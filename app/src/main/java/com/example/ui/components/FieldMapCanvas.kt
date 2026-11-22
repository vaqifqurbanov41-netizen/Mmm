package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
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
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt

internal object MapTileMath {
    const val MIN_ZOOM = 4
    const val MAX_ZOOM = 21
    const val MAX_SATELLITE_NATIVE_ZOOM = 20
    const val MAX_ESRI_FALLBACK_ZOOM = 17
    const val MAX_OSM_NATIVE_ZOOM = 19

    data class AncestorCrop(
        val ancestorZ: Int,
        val ancestorX: Int,
        val ancestorY: Int,
        val srcOffsetX: Int,
        val srcOffsetY: Int,
        val srcWidth: Int,
        val srcHeight: Int,
    )

    fun computeAncestorCrop(
        targetZ: Int,
        targetX: Int,
        targetY: Int,
        ancestorZ: Int,
        tileWidth: Int = 256,
        tileHeight: Int = 256,
    ): AncestorCrop {
        val shift = (targetZ - ancestorZ).coerceAtLeast(0)
        val subCount = 1 shl shift
        val ancestorX = targetX shr shift
        val ancestorY = targetY shr shift
        val localX = targetX and (subCount - 1)
        val localY = targetY and (subCount - 1)
        val left = (localX * tileWidth) / subCount
        val right = ((localX + 1) * tileWidth) / subCount
        val top = (localY * tileHeight) / subCount
        val bottom = ((localY + 1) * tileHeight) / subCount
        return AncestorCrop(
            ancestorZ = ancestorZ,
            ancestorX = ancestorX,
            ancestorY = ancestorY,
            srcOffsetX = left,
            srcOffsetY = top,
            srcWidth = (right - left).coerceAtLeast(1),
            srcHeight = (bottom - top).coerceAtLeast(1),
        )
    }

    /**
     * Detects uniform grey "Map data not yet available" placeholder tiles (such as Esri's HTTP 200
     * blank error graphic) by sampling a grid of pixels and rejecting near-monochrome grey plates.
     */
    fun isLikelyPlaceholderTilePixels(argbSamples: IntArray): Boolean {
        if (argbSamples.isEmpty()) return true
        var greyPlateSamples = 0
        var chromaticSamples = 0
        var minLum = 255
        var maxLum = 0
        for (px in argbSamples) {
            val r = (px shr 16) and 0xFF
            val g = (px shr 8) and 0xFF
            val b = px and 0xFF
            val maxChannel = maxOf(r, g, b)
            val minChannel = minOf(r, g, b)
            val chroma = maxChannel - minChannel
            val lum = (r + g + b) / 3
            if (lum < minLum) minLum = lum
            if (lum > maxLum) maxLum = lum
            if (chroma > 14) {
                chromaticSamples++
            }
            if (chroma <= 6 && lum in 188..236) {
                greyPlateSamples++
            }
        }
        val ratioGreyPlate = greyPlateSamples.toDouble() / argbSamples.size.toDouble()
        // Real satellite imagery almost always has earth color chroma and wide luminance variance.
        return chromaticSamples == 0 && (ratioGreyPlate >= 0.72 || (maxLum - minLum) <= 10)
    }
}

internal data class ResolvedMapTile(
    val bitmap: ImageBitmap,
    val srcOffset: IntOffset,
    val srcSize: IntSize,
)

internal object MapTileLoader {
    private const val NEGATIVE_TTL_MS = 45_000L

    @Volatile
    private var client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var cacheInitialized = false

    private val cache = object : LruCache<String, ImageBitmap>(260) {}
    private val inFlight = mutableSetOf<String>()
    private val failedUntil = ConcurrentHashMap<String, Long>()

    fun initCache(context: Context) {
        if (cacheInitialized) return
        synchronized(this) {
            if (cacheInitialized) return
            runCatching {
                val cacheDir = File(context.applicationContext.cacheDir, "map_tile_cache_v2")
                client = OkHttpClient.Builder()
                    .cache(Cache(cacheDir, 24L * 1024L * 1024L))
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(12, TimeUnit.SECONDS)
                    .build()
                cacheInitialized = true
            }
        }
    }

    private fun tileKey(z: Int, x: Int, y: Int, satellite: Boolean): String =
        "${if (satellite) "gsat" else "osm"}_${z}_${x}_${y}"

    fun getCached(key: String): ImageBitmap? = cache.get(key)

    fun resolveBestTile(
        z: Int,
        x: Int,
        y: Int,
        satellite: Boolean,
    ): ResolvedMapTile? {
        cache.get(tileKey(z, x, y, satellite))?.let { exact ->
            return ResolvedMapTile(
                bitmap = exact,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(exact.width, exact.height),
            )
        }
        val minAncestor = (z - 6).coerceAtLeast(MapTileMath.MIN_ZOOM)
        for (pz in (z - 1) downTo minAncestor) {
            val shift = z - pz
            val px = x shr shift
            val py = y shr shift
            cache.get(tileKey(pz, px, py, satellite))?.let { ancestorBmp ->
                val crop = MapTileMath.computeAncestorCrop(
                    targetZ = z,
                    targetX = x,
                    targetY = y,
                    ancestorZ = pz,
                    tileWidth = ancestorBmp.width,
                    tileHeight = ancestorBmp.height,
                )
                return ResolvedMapTile(
                    bitmap = ancestorBmp,
                    srcOffset = IntOffset(crop.srcOffsetX, crop.srcOffsetY),
                    srcSize = IntSize(crop.srcWidth, crop.srcHeight),
                )
            }
        }
        return null
    }

    private fun isPlaceholderBitmap(bmp: Bitmap): Boolean {
        val w = bmp.width
        val h = bmp.height
        if (w < 16 || h < 16) return true
        val samples = IntArray(64)
        var idx = 0
        for (gy in 0 until 8) {
            val py = ((gy + 0.5) * h / 8.0).toInt().coerceIn(0, h - 1)
            for (gx in 0 until 8) {
                val px = ((gx + 0.5) * w / 8.0).toInt().coerceIn(0, w - 1)
                samples[idx++] = bmp.getPixel(px, py)
            }
        }
        return MapTileMath.isLikelyPlaceholderTilePixels(samples)
    }

    private fun downloadTileBitmap(
        url: String,
        referer: String?,
        userAgent: String,
        rejectPlaceholder: Boolean,
    ): ImageBitmap? {
        val reqBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "image/webp,image/png,image/jpeg,image/*;q=0.8")
        if (referer != null) {
            reqBuilder.header("Referer", referer)
        }
        return client.newCall(reqBuilder.build()).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            val contentType = resp.header("Content-Type").orEmpty()
            if (contentType.isNotEmpty() && !contentType.startsWith("image/", ignoreCase = true)) {
                return@use null
            }
            val bytes = resp.body?.bytes() ?: return@use null
            if (bytes.size < 128) return@use null
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@use null
            if (rejectPlaceholder && isPlaceholderBitmap(bmp)) {
                bmp.recycle()
                return@use null
            }
            bmp.asImageBitmap()
        }
    }

    suspend fun fetchTile(
        z: Int,
        x: Int,
        y: Int,
        satellite: Boolean,
    ): ImageBitmap? {
        val nativeCap = if (satellite) MapTileMath.MAX_SATELLITE_NATIVE_ZOOM else MapTileMath.MAX_OSM_NATIVE_ZOOM
        if (z > nativeCap || z < MapTileMath.MIN_ZOOM) return null
        val key = tileKey(z, x, y, satellite)
        cache.get(key)?.let { return it }

        val now = System.currentTimeMillis()
        val blockedUntil = failedUntil[key]
        if (blockedUntil != null && now < blockedUntil) return null

        synchronized(inFlight) {
            if (inFlight.contains(key)) return null
            inFlight.add(key)
        }
        return try {
            withContext(Dispatchers.IO) {
                var decoded: ImageBitmap? = null
                if (satellite) {
                    val shard = (abs(x) + abs(y)) and 3
                    val googleSatUrl = "https://mt$shard.google.com/vt/lyrs=s&hl=az&x=$x&y=$y&z=$z"
                    decoded = downloadTileBitmap(
                        url = googleSatUrl,
                        referer = "https://maps.google.com/",
                        userAgent = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36",
                        rejectPlaceholder = true,
                    )
                    if (decoded == null && z <= MapTileMath.MAX_ESRI_FALLBACK_ZOOM) {
                        val esriUrl = "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$z/$y/$x"
                        decoded = downloadTileBitmap(
                            url = esriUrl,
                            referer = null,
                            userAgent = "AqroMeslehetAndroid/1.0",
                            rejectPlaceholder = true,
                        )
                    }
                } else {
                    val osmUrl = "https://tile.openstreetmap.org/$z/$x/$y.png"
                    decoded = downloadTileBitmap(
                        url = osmUrl,
                        referer = "https://www.openstreetmap.org/",
                        userAgent = "AqroMeslehetAndroid/1.0 (https://aqromeslehet.az)",
                        rejectPlaceholder = false,
                    )
                }

                if (decoded != null) {
                    failedUntil.remove(key)
                    cache.put(key, decoded)
                } else {
                    failedUntil[key] = System.currentTimeMillis() + NEGATIVE_TTL_MS
                }
                decoded
            }
        } catch (_: Exception) {
            failedUntil[key] = System.currentTimeMillis() + NEGATIVE_TTL_MS
            null
        } finally {
            synchronized(inFlight) {
                inFlight.remove(key)
            }
        }
    }

    /**
     * Fetches the requested tile and, if exact zoom is missing or deep (>16), seamlessly warms up
     * parent zoom tiles so high-zoom overzooming always keeps crisp underlying imagery visible.
     */
    suspend fun fetchTileWithAncestors(
        z: Int,
        x: Int,
        y: Int,
        satellite: Boolean,
        onTileLoaded: () -> Unit,
    ) {
        val exactLoaded = fetchTile(z, x, y, satellite)
        if (exactLoaded != null) {
            onTileLoaded()
            return
        }
        val minAncestor = (z - 4).coerceAtLeast(MapTileMath.MIN_ZOOM)
        for (pz in (z - 1) downTo minAncestor) {
            val shift = z - pz
            val px = x shr shift
            val py = y shr shift
            val parentKey = tileKey(pz, px, py, satellite)
            if (cache.get(parentKey) != null) {
                onTileLoaded()
                return
            }
            val loadedParent = fetchTile(pz, px, py, satellite)
            if (loadedParent != null) {
                onTileLoaded()
                return
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
    val context = LocalContext.current
    val strings = LocalStrings.current
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var tileTick by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val textMeasurer: TextMeasurer = rememberTextMeasurer()

    var pinchScale by remember { mutableFloatStateOf(1f) }
    var localCenter by remember(center) { mutableStateOf(center) }
    var localZoom by remember(zoom) { mutableIntStateOf(zoom.coerceIn(MapTileMath.MIN_ZOOM, MapTileMath.MAX_ZOOM)) }

    val latestPoints by rememberUpdatedState(points)
    val latestExtraPolygons by rememberUpdatedState(extraPolygons)
    val latestOnCenterChanged by rememberUpdatedState(onCenterChanged)
    val latestOnZoomChanged by rememberUpdatedState(onZoomChanged)
    val latestOnAddPoint by rememberUpdatedState(onAddPoint)
    val latestOnMovePoint by rememberUpdatedState(onMovePoint)

    LaunchedEffect(context) {
        MapTileLoader.initCache(context)
    }

    val (centerWorldX, centerWorldY) = remember(localCenter, localZoom) {
        GeoUtils.latLonToWorldPx(localCenter.lat, localCenter.lon, localZoom)
    }

    // Trigger tile fetches around visible viewport (including ancestor overzoom fallback)
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
                val best = MapTileLoader.resolveBestTile(localZoom, wrappedX, ty, isSatellite)
                val exactCached = best != null && best.srcSize.width == best.bitmap.width
                if (!exactCached) {
                    scope.launch {
                        MapTileLoader.fetchTileWithAncestors(localZoom, wrappedX, ty, isSatellite) {
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

                                    while (nextScale >= 1.65f && nextZoom < MapTileMath.MAX_ZOOM) {
                                        nextZoom += 1
                                        nextScale /= 2f
                                    }
                                    while (nextScale <= 0.62f && nextZoom > MapTileMath.MIN_ZOOM) {
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
                        if (pinchScale >= 1.35f && localZoom < MapTileMath.MAX_ZOOM) {
                            localZoom += 1
                            latestOnZoomChanged(localZoom)
                        } else if (pinchScale <= 0.72f && localZoom > MapTileMath.MIN_ZOOM) {
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

            // Draw exact or cropped ancestor map tiles smoothly during pan/zoom/overzoom
            for (tx in minTx..maxTx) {
                val wrappedX = ((tx % maxTile) + maxTile) % maxTile
                for (ty in minTy..maxTy) {
                    val left = ((tx * 256.0 - centerWorldX) * scale + halfW).roundToInt()
                    val top = ((ty * 256.0 - centerWorldY) * scale + halfH).roundToInt()
                    val right = (((tx + 1) * 256.0 - centerWorldX) * scale + halfW).roundToInt()
                    val bottom = (((ty + 1) * 256.0 - centerWorldY) * scale + halfH).roundToInt()
                    val dstW = (right - left + 1).coerceAtLeast(1)
                    val dstH = (bottom - top + 1).coerceAtLeast(1)

                    val resolved = MapTileLoader.resolveBestTile(localZoom, wrappedX, ty, isSatellite)
                    if (resolved != null) {
                        drawImage(
                            image = resolved.bitmap,
                            srcOffset = resolved.srcOffset,
                            srcSize = resolved.srcSize,
                            dstOffset = IntOffset(left, top),
                            dstSize = IntSize(dstW, dstH),
                            filterQuality = FilterQuality.Medium,
                        )
                    } else {
                        drawRect(
                            color = if (isSatellite) Color(0xFF1E2D22) else Color(0xFFDCE5EC),
                            topLeft = Offset(left.toFloat(), top.toFloat()),
                            size = androidx.compose.ui.geometry.Size(tileSizePx.toFloat() + 1f, tileSizePx.toFloat() + 1f),
                        )
                        drawRect(
                            color = Color.White.copy(alpha = 0.06f),
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
                        val next = (localZoom + 1).coerceAtMost(MapTileMath.MAX_ZOOM)
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
                        val next = (localZoom - 1).coerceAtLeast(MapTileMath.MIN_ZOOM)
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
