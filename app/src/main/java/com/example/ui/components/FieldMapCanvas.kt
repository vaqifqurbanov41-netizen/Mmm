package com.example.ui.components

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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

private object MapTileLoader {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val cache = object : LruCache<String, ImageBitmap>(120) {}
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
) {
    val strings = LocalStrings.current
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var tileTick by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val textMeasurer: TextMeasurer = rememberTextMeasurer()

    val (centerWorldX, centerWorldY) = remember(center, zoom) {
        GeoUtils.latLonToWorldPx(center.lat, center.lon, zoom)
    }

    // Trigger tile fetches around visible viewport
    LaunchedEffect(center, zoom, isSatellite, canvasSize) {
        if (canvasSize.width == 0 || canvasSize.height == 0) return@LaunchedEffect
        val maxTile = (1 shl zoom)
        val halfW = canvasSize.width / 2.0
        val halfH = canvasSize.height / 2.0
        val minTx = floor((centerWorldX - halfW) / 256.0).toInt()
        val maxTx = floor((centerWorldX + halfW) / 256.0).toInt()
        val minTy = floor((centerWorldY - halfH) / 256.0).toInt().coerceIn(0, maxTile - 1)
        val maxTy = floor((centerWorldY + halfH) / 256.0).toInt().coerceIn(0, maxTile - 1)

        for (tx in minTx..maxTx) {
            val wrappedX = ((tx % maxTile) + maxTile) % maxTile
            for (ty in minTy..maxTy) {
                val key = "${if (isSatellite) "sat" else "osm"}_${zoom}_${wrappedX}_${ty}"
                if (MapTileLoader.getCached(key) == null) {
                    scope.launch {
                        val loaded = MapTileLoader.fetchTile(zoom, wrappedX, ty, isSatellite)
                        if (loaded != null) {
                            tileTick++
                        }
                    }
                }
            }
        }
    }

    var draggingPointIndex by remember { mutableIntStateOf(-1) }
    var currentCenter by remember { mutableStateOf(center) }
    currentCenter = center

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(310.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (isSatellite) Color(0xFF162219) else Color(0xFFE6EEF4)),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = it }
                .pointerInput(zoom, center, points) {
                    detectTapGestures { offset ->
                        val (cwX, cwY) = GeoUtils.latLonToWorldPx(currentCenter.lat, currentCenter.lon, zoom)
                        val wx = cwX + (offset.x - size.width / 2.0)
                        val wy = cwY + (offset.y - size.height / 2.0)
                        val tapped = GeoUtils.worldPxToLatLon(wx, wy, zoom)
                        onAddPoint(tapped)
                    }
                }
                .pointerInput(zoom, points) {
                    detectDragGestures(
                        onDragStart = { startOffset ->
                            val (cwX, cwY) = GeoUtils.latLonToWorldPx(currentCenter.lat, currentCenter.lon, zoom)
                            var found = -1
                            points.forEachIndexed { idx, pt ->
                                val (px, py) = GeoUtils.latLonToWorldPx(pt.lat, pt.lon, zoom)
                                val sx = (px - cwX + size.width / 2.0).toFloat()
                                val sy = (py - cwY + size.height / 2.0).toFloat()
                                if (hypot(sx - startOffset.x, sy - startOffset.y) <= 44f) {
                                    found = idx
                                }
                            }
                            draggingPointIndex = found
                        },
                        onDragEnd = { draggingPointIndex = -1 },
                        onDragCancel = { draggingPointIndex = -1 },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            if (draggingPointIndex in points.indices) {
                                val (cwX, cwY) = GeoUtils.latLonToWorldPx(currentCenter.lat, currentCenter.lon, zoom)
                                val wx = cwX + (change.position.x - size.width / 2.0)
                                val wy = cwY + (change.position.y - size.height / 2.0)
                                onMovePoint(draggingPointIndex, GeoUtils.worldPxToLatLon(wx, wy, zoom))
                            } else {
                                val (cwX, cwY) = GeoUtils.latLonToWorldPx(currentCenter.lat, currentCenter.lon, zoom)
                                val newCenter = GeoUtils.worldPxToLatLon(
                                    cwX - dragAmount.x,
                                    cwY - dragAmount.y,
                                    zoom,
                                )
                                currentCenter = newCenter
                                onCenterChanged(newCenter)
                            }
                        }
                    )
                }
                .testTag("field_map_canvas"),
        ) {
            val unusedTick = tileTick // trigger redraw when tiles finish loading
            val halfW = size.width / 2.0
            val halfH = size.height / 2.0
            val maxTile = (1 shl zoom)

            val minTx = floor((centerWorldX - halfW) / 256.0).toInt()
            val maxTx = floor((centerWorldX + halfW) / 256.0).toInt()
            val minTy = floor((centerWorldY - halfH) / 256.0).toInt().coerceIn(0, maxTile - 1)
            val maxTy = floor((centerWorldY + halfH) / 256.0).toInt().coerceIn(0, maxTile - 1)

            // Draw map tiles
            for (tx in minTx..maxTx) {
                val wrappedX = ((tx % maxTile) + maxTile) % maxTile
                for (ty in minTy..maxTy) {
                    val left = (tx * 256.0 - centerWorldX + halfW).roundToInt()
                    val top = (ty * 256.0 - centerWorldY + halfH).roundToInt()
                    val key = "${if (isSatellite) "sat" else "osm"}_${zoom}_${wrappedX}_${ty}"
                    val tileBitmap = MapTileLoader.getCached(key)
                    if (tileBitmap != null) {
                        drawImage(
                            image = tileBitmap,
                            dstOffset = IntOffset(left, top),
                            dstSize = IntSize(257, 257),
                        )
                    } else {
                        drawRect(
                            color = if (isSatellite) Color(0xFF1E2D22) else Color(0xFFDCE5EC),
                            topLeft = Offset(left.toFloat(), top.toFloat()),
                            size = androidx.compose.ui.geometry.Size(256f, 256f),
                        )
                        drawRect(
                            color = Color.White.copy(alpha = 0.08f),
                            topLeft = Offset(left.toFloat(), top.toFloat()),
                            size = androidx.compose.ui.geometry.Size(256f, 256f),
                            style = Stroke(width = 1f),
                        )
                    }
                }
            }

            // Convert points to screen offsets
            val screenPoints = points.map { pt ->
                val (wx, wy) = GeoUtils.latLonToWorldPx(pt.lat, pt.lon, zoom)
                Offset(
                    x = (wx - centerWorldX + halfW).toFloat(),
                    y = (wy - centerWorldY + halfH).toFloat(),
                )
            }

            // Draw Polygon fill + border
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
                val (gx, gy) = GeoUtils.latLonToWorldPx(gps.lat, gps.lon, zoom)
                val gOff = Offset(
                    (gx - centerWorldX + halfW).toFloat(),
                    (gy - centerWorldY + halfH).toFloat(),
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

        // Bottom-right Zoom +/- controls
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(10.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shadowElevation = 3.dp,
            ) {
                IconButton(
                    onClick = { onZoomChanged((zoom + 1).coerceAtMost(19)) },
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
                    onClick = { onZoomChanged((zoom - 1).coerceAtLeast(5)) },
                    modifier = Modifier.size(40.dp).testTag("map_zoom_out"),
                ) {
                    Icon(Icons.Filled.Remove, contentDescription = strings.zoomOut)
                }
            }
        }
    }
}
