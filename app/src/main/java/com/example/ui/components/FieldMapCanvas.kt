package com.example.ui.components

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.GeoUtils
import com.example.domain.LatLon
import com.example.i18n.LocalStrings
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt

internal object MapTileLoader {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val cache = object : LruCache<String, ImageBitmap>(140) {}
    private val inFlight = mutableSetOf<String>()

    fun getCached(key: String): ImageBitmap? = cache.get(key)

    suspend fun fetchTile(
        z: Int,
        x: Int,
        y: Int,
        satellite: Boolean,
    ): ImageBitmap? {
        val key = "${if (satellite) "sat" else "osm"}_${z}_${x}_${y}"
        cache.get(key)?.let { return it }
        synchronized(inFlight) {
            if (inFlight.contains(key)) return null
            inFlight.add(key)
        }
        return try {
            withContext(Dispatchers.IO) {
                val url = if (satellite) {
                    "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$z/$y/$x"
                } else {
                    "https://tile.openstreetmap.org/$z/$x/$y.png"
                }
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "AqroMeslehetAndroid/1.0")
                    .build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext null
                    val bytes = resp.body?.bytes() ?: return@withContext null
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext null
                    val img = bmp.asImageBitmap()
                    cache.put(key, img)
                    img
                }
            }
        } catch (_: Exception) {
            null
        } finally {
            synchronized(inFlight) {
                inFlight.remove(key)
            }
        }
    }
}

@Composable
fun FieldMapCanvas(
    center: LatLon,
    zoom: Int,
    isSatellite: Boolean,
    points: List<LatLon>,
    gpsLocation: LatLon?,
    onCenterChanged: (LatLon) -> Unit,
    onZoomChanged: (Int) -> Unit,
    onAddPoint: (LatLon) -> Unit,
    onMovePoint: (Int, LatLon) -> Unit,
    onToggleLayer: () -> Unit,
    onLocateMe: () -> Unit,
    modifier: Modifier = Modifier,
    isReadOnly: Boolean = false,
    extraPolygons: List<List<LatLon>> = emptyList(),
    mapHeightDp: Int = 310,
    isFullscreen: Boolean = false,
    onToggleFullscreen: (() -> Unit)? = null,
) {
    val strings = LocalStrings.current
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var tileTick by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val textMeasurer: TextMeasurer = rememberTextMeasurer()

    var pinchScale by remember { mutableFloatStateOf(1f) }
    var localCenter by remember(center) { mutableStateOf(center) }
    var localZoom by remember(zoom) { mutableIntStateOf(zoom.coerceIn(5, 19)) }

    val latestPoints by rememberUpdatedState(points)
    val latestExtraPolygons by rememberUpdatedState(extraPolygons)
    val latestOnCenterChanged by rememberUpdatedState(onCenterChanged)
    val latestOnZoomChanged by rememberUpdatedState(onZoomChanged)
    val latestOnAddPoint by rememberUpdatedState(onAddPoint)
    val latestOnMovePoint by rememberUpdatedState(onMovePoint)

    val (centerWorldX, centerWorldY) = remember(localCenter, localZoom) {
        GeoUtils.latLonToWorldPx(localCenter.lat, localCenter.lon, localZoom)
    }

    // Trigger tile fetches around visible viewport
    LaunchedEffect(localCenter, localZoom, isSatellite, canvasSize) {
        if (canvasSize.width == 0 || canvasSize.height == 0) return@LaunchedEffect
        val maxTile = (1 shl localZoom)
        val halfW = canvasSize.width / (2.0 * pinchScale.coerceAtLeast(0.5f))
        val halfH = canvasSize.height / (2.0 * pinchScale.coerceAtLeast(0.5f))
        val minTx = floor((centerWorldX - halfW) / 256.0).toInt()
        val maxTx = floor((centerWorldX + halfW) / 256.0).toInt()
        val minTy = floor((centerWorldY - halfH) / 256.0).toInt().coerceIn(0, maxTile - 1)
        val maxTy = floor((centerWorldY + halfH) / 256.0).toInt().coerceIn(0, maxTile - 1)

        for (tx in minTx..maxTx) {
            val wrappedX = ((tx % maxTile) + maxTile) % maxTile
            for (ty in minTy..maxTy) {
                val key = "${if (isSatellite) "sat" else "osm"}_${localZoom}_${wrappedX}_${ty}"
                if (MapTileLoader.getCached(key) == null) {
                    scope.launch {
                        val loaded = MapTileLoader.fetchTile(localZoom, wrappedX, ty, isSatellite)
                        if (loaded != null) {
                            tileTick++
                        }
                    }
                }
            }
        }
    }

    val allPolygonPoints = remember(points, extraPolygons) {
        if (extraPolygons.isEmpty()) points else points + extraPolygons.flatten()
    }

    val containerShape = RoundedCornerShape(if (isFullscreen) 0.dp else 18.dp)
    val baseSizeModifier = if (isFullscreen) {
        modifier.fillMaxSize()
    } else {
        modifier.fillMaxWidth().height(mapHeightDp.dp)
    }
    Box(
        modifier = baseSizeModifier
            .clip(containerShape)
            .background(if (isSatellite) Color(0xFF162219) else Color(0xFFE6EEF4)),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = it }
                .pointerInput(isReadOnly) {
                    val touchSlop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val pts = latestPoints
                        var draggingIdx = -1
                        if (!isReadOnly) {
                            val (cwX, cwY) = GeoUtils.latLonToWorldPx(localCenter.lat, localCenter.lon, localZoom)
                            pts.forEachIndexed { idx, pt ->
                                val (px, py) = GeoUtils.latLonToWorldPx(pt.lat, pt.lon, localZoom)
                                val sx = ((px - cwX) * pinchScale + size.width / 2.0).toFloat()
                                val sy = ((py - cwY) * pinchScale + size.height / 2.0).toFloat()
                                if (hypot(sx - down.position.x, sy - down.position.y) <= 44f) {
                                    draggingIdx = idx
                                }
                            }
                        }

                        var moved = false
                        var multiTouchUsed = false
                        var totalPanDistance = 0f

                        do {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break

                            if (pressed.size >= 2) {
                                multiTouchUsed = true
                                draggingIdx = -1
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()
                                val centroid = event.calculateCentroid(useCurrent = true)

                                if (zoomChange != 1f || panChange != Offset.Zero) {
                                    moved = true
                                    val oldScale = pinchScale
                                    var nextScale = (oldScale * zoomChange).coerceIn(0.45f, 2.25f)
                                    var nextZoom = localZoom

                                    val (cwX, cwY) = GeoUtils.latLonToWorldPx(localCenter.lat, localCenter.lon, nextZoom)
                                    val halfW = size.width / 2.0
                                    val halfH = size.height / 2.0

                                    // Keep geographic coordinate under pinch centroid anchored while zooming and panning
                                    if (centroid != Offset.Unspecified) {
                                        val anchorWx = cwX + (centroid.x - panChange.x - halfW) / oldScale
                                        val anchorWy = cwY + (centroid.y - panChange.y - halfH) / oldScale
                                        val shiftedCwX = anchorWx - (centroid.x - halfW) / nextScale
                                        val shiftedCwY = anchorWy - (centroid.y - halfH) / nextScale
                                        localCenter = GeoUtils.worldPxToLatLon(shiftedCwX, shiftedCwY, nextZoom)
                                    } else {
                                        localCenter = GeoUtils.worldPxToLatLon(
                                            cwX - panChange.x / oldScale,
                                            cwY - panChange.y / oldScale,
                                            nextZoom,
                                        )
                                    }

                                    while (nextScale >= 1.65f && nextZoom < 19) {
                                        nextZoom += 1
                                        nextScale /= 2f
                                    }
                                    while (nextScale <= 0.62f && nextZoom > 5) {
                                        nextZoom -= 1
                                        nextScale *= 2f
                                    }

                                    pinchScale = nextScale
                                    if (nextZoom != localZoom) {
                                        localZoom = nextZoom
                                        latestOnZoomChanged(nextZoom)
                                    }
                                    latestOnCenterChanged(localCenter)
                                }
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            } else if (!multiTouchUsed) {
                                val panChange = event.calculatePan()
                                totalPanDistance += hypot(panChange.x, panChange.y)
                                if (totalPanDistance > touchSlop) {
                                    moved = true
                                }
                                if (moved && panChange != Offset.Zero) {
                                    val change = pressed.first()
                                    if (draggingIdx in latestPoints.indices && !isReadOnly) {
                                        val (cwX, cwY) = GeoUtils.latLonToWorldPx(localCenter.lat, localCenter.lon, localZoom)
                                        val wx = cwX + (change.position.x - size.width / 2.0) / pinchScale
                                        val wy = cwY + (change.position.y - size.height / 2.0) / pinchScale
                                        latestOnMovePoint(draggingIdx, GeoUtils.worldPxToLatLon(wx, wy, localZoom))
                                    } else {
                                        val (cwX, cwY) = GeoUtils.latLonToWorldPx(localCenter.lat, localCenter.lon, localZoom)
                                        val newCenter = GeoUtils.worldPxToLatLon(
                                            cwX - panChange.x / pinchScale,
                                            cwY - panChange.y / pinchScale,
                                            localZoom,
                                        )
                                        localCenter = newCenter
                                        latestOnCenterChanged(newCenter)
                                    }
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            }
                        } while (true)

                        // Settle fractional pinch scale cleanly into integer tile zoom
                        if (pinchScale >= 1.35f && localZoom < 19) {
                            localZoom += 1
                            latestOnZoomChanged(localZoom)
                        } else if (pinchScale <= 0.72f && localZoom > 5) {
                            localZoom -= 1
                            latestOnZoomChanged(localZoom)
                        }
                        pinchScale = 1f

                        if (!moved && !multiTouchUsed && !isReadOnly) {
                            val (cwX, cwY) = GeoUtils.latLonToWorldPx(localCenter.lat, localCenter.lon, localZoom)
                            val wx = cwX + (down.position.x - size.width / 2.0)
                            val wy = cwY + (down.position.y - size.height / 2.0)
                            val tapped = GeoUtils.worldPxToLatLon(wx, wy, localZoom)
                            latestOnAddPoint(tapped)
                        }
                    }
                }
                .testTag("field_map_canvas"),
        ) {
            val unusedTick = tileTick
            val halfW = size.width / 2.0
            val halfH = size.height / 2.0
            val maxTile = (1 shl localZoom)
            val scale = pinchScale.toDouble()
            val tileSizePx = (256.0 * scale)

            val minTx = floor((centerWorldX - halfW / scale) / 256.0).toInt()
            val maxTx = floor((centerWorldX + halfW / scale) / 256.0).toInt()
            val minTy = floor((centerWorldY - halfH / scale) / 256.0).toInt().coerceIn(0, maxTile - 1)
            val maxTy = floor((centerWorldY + halfH / scale) / 256.0).toInt().coerceIn(0, maxTile - 1)

            // Draw map tiles scaled smoothly during pinch
            for (tx in minTx..maxTx) {
                val wrappedX = ((tx % maxTile) + maxTile) % maxTile
                for (ty in minTy..maxTy) {
                    val left = ((tx * 256.0 - centerWorldX) * scale + halfW).roundToInt()
                    val top = ((ty * 256.0 - centerWorldY) * scale + halfH).roundToInt()
                    val right = (((tx + 1) * 256.0 - centerWorldX) * scale + halfW).roundToInt()
                    val bottom = (((ty + 1) * 256.0 - centerWorldY) * scale + halfH).roundToInt()
                    val dstW = (right - left + 1).coerceAtLeast(1)
                    val dstH = (bottom - top + 1).coerceAtLeast(1)

                    val key = "${if (isSatellite) "sat" else "osm"}_${localZoom}_${wrappedX}_${ty}"
                    val tileBitmap = MapTileLoader.getCached(key)
                    if (tileBitmap != null) {
                        drawImage(
                            image = tileBitmap,
                            dstOffset = IntOffset(left, top),
                            dstSize = IntSize(dstW, dstH),
                        )
                    } else {
                        drawRect(
                            color = if (isSatellite) Color(0xFF1E2D22) else Color(0xFFDCE5EC),
                            topLeft = Offset(left.toFloat(), top.toFloat()),
                            size = androidx.compose.ui.geometry.Size(tileSizePx.toFloat() + 1f, tileSizePx.toFloat() + 1f),
                        )
                        drawRect(
                            color = Color.White.copy(alpha = 0.08f),
                            topLeft = Offset(left.toFloat(), top.toFloat()),
                            size = androidx.compose.ui.geometry.Size(tileSizePx.toFloat() + 1f, tileSizePx.toFloat() + 1f),
                            style = Stroke(width = 1f),
                        )
                    }
                }
            }

            // Draw secondary/background KMZ polygons if provided
            latestExtraPolygons.forEach { polyPts ->
                if (polyPts.size >= 2) {
                    val screenExtra = polyPts.map { pt ->
                        val (wx, wy) = GeoUtils.latLonToWorldPx(pt.lat, pt.lon, localZoom)
                        Offset(
                            x = ((wx - centerWorldX) * scale + halfW).toFloat(),
                            y = ((wy - centerWorldY) * scale + halfH).toFloat(),
                        )
                    }
                    val path = Path().apply {
                        moveTo(screenExtra.first().x, screenExtra.first().y)
                        screenExtra.drop(1).forEach { lineTo(it.x, it.y) }
                        if (screenExtra.size >= 3) close()
                    }
                    if (screenExtra.size >= 3) {
                        drawPath(path = path, color = Color(0xFF38BDF8).copy(alpha = 0.22f))
                    }
                    drawPath(path = path, color = Color(0xFF38BDF8), style = Stroke(width = 4f))
                }
            }

            // Convert primary points to screen offsets
            val screenPoints = latestPoints.map { pt ->
                val (wx, wy) = GeoUtils.latLonToWorldPx(pt.lat, pt.lon, localZoom)
                Offset(
                    x = ((wx - centerWorldX) * scale + halfW).toFloat(),
                    y = ((wy - centerWorldY) * scale + halfH).toFloat(),
                )
            }

            // Draw primary Polygon fill + border
            if (screenPoints.size >= 2) {
                val path = Path().apply {
                    moveTo(screenPoints.first().x, screenPoints.first().y)
                    screenPoints.drop(1).forEach { lineTo(it.x, it.y) }
                    if (screenPoints.size >= 3) close()
                }
                if (screenPoints.size >= 3) {
                    drawPath(
                        path = path,
                        color = EmeraldBright.copy(alpha = 0.28f),
                    )
                }
                drawPath(
                    path = path,
                    color = EmeraldBright,
                    style = Stroke(width = 6f),
                )
            }

            // Draw GPS Marker
            gpsLocation?.let { gps ->
                val (gx, gy) = GeoUtils.latLonToWorldPx(gps.lat, gps.lon, localZoom)
                val gOff = Offset(
                    ((gx - centerWorldX) * scale + halfW).toFloat(),
                    ((gy - centerWorldY) * scale + halfH).toFloat(),
                )
                drawCircle(
                    color = Color(0xFF2196F3).copy(alpha = 0.25f),
                    radius = 36f,
                    center = gOff,
                )
                drawCircle(
                    color = Color.White,
                    radius = 16f,
                    center = gOff,
                )
                drawCircle(
                    color = Color(0xFF1976D2),
                    radius = 11f,
                    center = gOff,
                )
            }

            // Draw numbered vertices
            screenPoints.forEachIndexed { idx, offset ->
                drawCircle(
                    color = Color.White,
                    radius = 22f,
                    center = offset,
                )
                drawCircle(
                    color = EmeraldPrimary,
                    radius = 17f,
                    center = offset,
                )
                val label = (idx + 1).toString()
                val layoutResult = textMeasurer.measure(
                    text = label,
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                drawText(
                    textLayoutResult = layoutResult,
                    topLeft = Offset(
                        x = offset.x - layoutResult.size.width / 2f,
                        y = offset.y - layoutResult.size.height / 2f,
                    ),
                )
            }
        }

        // Top-left Fullscreen Expand/Collapse toggle
        if (onToggleFullscreen != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                shadowElevation = 3.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onToggleFullscreen)
                    .testTag("map_fullscreen_toggle"),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                        contentDescription = if (isFullscreen) strings.btnExitFullscreenMap else strings.btnFullscreenMap,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = if (isFullscreen) strings.btnExitFullscreenMap else strings.btnFullscreenMap,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        // Top-right Layer Toggle & Locate Me controls
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                shadowElevation = 3.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .clickable(onClick = onToggleLayer)
                    .testTag("map_layer_toggle"),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Layers,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = if (isSatellite) strings.btnSatelliteLayer else strings.btnStreetLayer,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            if (!isReadOnly) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary,
                    shadowElevation = 3.dp,
                    modifier = Modifier
                        .clickable(onClick = onLocateMe)
                        .testTag("map_locate_button"),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MyLocation,
                            contentDescription = strings.btnLocateMe,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = strings.btnLocateMe,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                }
            }
        }

        // Bottom-right Fit-Bounds & Zoom +/- controls
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(10.dp),
        ) {
            if (allPolygonPoints.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    shadowElevation = 3.dp,
                ) {
                    IconButton(
                        onClick = {
                            val (fitCenter, fitZoom) = GeoUtils.computeBoundsCenterAndZoom(allPolygonPoints)
                            pinchScale = 1f
                            localCenter = fitCenter
                            localZoom = fitZoom
                            onCenterChanged(fitCenter)
                            onZoomChanged(fitZoom)
                        },
                        modifier = Modifier.size(40.dp).testTag("map_fit_bounds"),
                    ) {
                        Icon(Icons.Filled.CenterFocusStrong, contentDescription = strings.btnFitBounds)
                    }
                }
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shadowElevation = 3.dp,
            ) {
                IconButton(
                    onClick = {
                        val next = (localZoom + 1).coerceAtMost(19)
                        pinchScale = 1f
                        localZoom = next
                        onZoomChanged(next)
                    },
                    modifier = Modifier.size(40.dp).testTag("map_zoom_in"),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = strings.zoomIn)
                }
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shadowElevation = 3.dp,
            ) {
                IconButton(
                    onClick = {
                        val next = (localZoom - 1).coerceAtLeast(5)
                        pinchScale = 1f
                        localZoom = next
                        onZoomChanged(next)
                    },
                    modifier = Modifier.size(40.dp).testTag("map_zoom_out"),
                ) {
                    Icon(Icons.Filled.Remove, contentDescription = strings.zoomOut)
                }
            }
        }
    }
}
