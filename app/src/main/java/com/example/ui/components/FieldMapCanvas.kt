package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

data class SavedFieldOverlay(
    val id: String,
    val name: String,
    val crop: String,
    val areaHa: Double,
    val points: List<LatLon>,
)

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

    data class TileRange(
        val minTx: Int,
        val maxTx: Int,
        val minTy: Int,
        val maxTy: Int,
    )

    data class ScaleBarSpec(
        val label: String,
        val widthPx: Float,
    )

    fun normalizeBearingDeg(deg: Float): Float {
        var r = ((deg % 360f) + 540f) % 360f - 180f
        if (r == -180f) r = 180f
        return r
    }

    fun worldToScreen(
        wx: Double,
        wy: Double,
        centerWorldX: Double,
        centerWorldY: Double,
        scale: Double,
        bearingDeg: Float,
        halfW: Double,
        halfH: Double,
    ): Pair<Float, Float> {
        val dx = (wx - centerWorldX) * scale
        val dy = (wy - centerWorldY) * scale
        val rad = bearingDeg.toDouble() * PI / 180.0
        val c = cos(rad)
        val s = sin(rad)
        val sx = halfW + dx * c - dy * s
        val sy = halfH + dx * s + dy * c
        return sx.toFloat() to sy.toFloat()
    }

    fun screenToWorld(
        sx: Float,
        sy: Float,
        centerWorldX: Double,
        centerWorldY: Double,
        scale: Double,
        bearingDeg: Float,
        halfW: Double,
        halfH: Double,
    ): Pair<Double, Double> {
        val dsx = sx.toDouble() - halfW
        val dsy = sy.toDouble() - halfH
        val rad = bearingDeg.toDouble() * PI / 180.0
        val c = cos(rad)
        val s = sin(rad)
        val wx = centerWorldX + (dsx * c + dsy * s) / scale
        val wy = centerWorldY + (-dsx * s + dsy * c) / scale
        return wx to wy
    }

    fun panDeltaToWorldDelta(
        panX: Float,
        panY: Float,
        scale: Double,
        bearingDeg: Float,
    ): Pair<Double, Double> {
        val rad = bearingDeg.toDouble() * PI / 180.0
        val c = cos(rad)
        val s = sin(rad)
        val dwx = (-panX.toDouble() * c - panY.toDouble() * s) / scale
        val dwy = (panX.toDouble() * s - panY.toDouble() * c) / scale
        return dwx to dwy
    }

    fun computeRotatedTileRange(
        centerWorldX: Double,
        centerWorldY: Double,
        halfW: Double,
        halfH: Double,
        scale: Double,
        maxTile: Int,
        bearingDeg: Float,
    ): TileRange {
        val (rx, ry) = if (abs(bearingDeg) < 0.05f) {
            (halfW / scale) to (halfH / scale)
        } else {
            val diag = hypot(halfW, halfH) / scale
            diag to diag
        }
        val minTx = floor((centerWorldX - rx) / 256.0).toInt()
        val maxTx = floor((centerWorldX + rx) / 256.0).toInt()
        val minTy = floor((centerWorldY - ry) / 256.0).toInt().coerceIn(0, maxTile - 1)
        val maxTy = floor((centerWorldY + ry) / 256.0).toInt().coerceIn(0, maxTile - 1)
        return TileRange(minTx, maxTx, minTy, maxTy)
    }

    fun computeScaleBar(
        latDeg: Double,
        zoom: Int,
        pinchScale: Float,
        maxBarPx: Float,
    ): ScaleBarSpec {
        val safeMaxPx = maxBarPx.coerceAtLeast(36f)
        val metersPerPx =
            (156543.03392 * cos(latDeg.coerceIn(-85.0, 85.0) * PI / 180.0)) /
                ((1 shl zoom).toDouble() * pinchScale.toDouble().coerceAtLeast(0.25))
        val targetMaxMeters = safeMaxPx * metersPerPx
        val steps = listOf(5, 10, 20, 50, 100, 200, 500, 1000, 2000, 5000, 10000, 20000, 50000, 100000)
        val chosen = steps.lastOrNull { it <= targetMaxMeters } ?: steps.first()
        val widthPx = (chosen / metersPerPx).toFloat().coerceIn(28f, safeMaxPx)
        val label = if (chosen >= 1000) "${chosen / 1000} km" else "$chosen m"
        return ScaleBarSpec(label = label, widthPx = widthPx)
    }

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

private val MapDockDarkBg = Color(0xD9151B18)
private val MapDockBorder = Color.White.copy(alpha = 0.16f)

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
    savedOverlays: List<SavedFieldOverlay> = emptyList(),
    mapHeightDp: Int = 310,
    isFullscreen: Boolean = false,
    isDrawEnabled: Boolean = true,
    measurementSummary: String? = null,
    onToggleFullscreen: (() -> Unit)? = null,
    bottomOverlay: (@Composable () -> Unit)? = null,
) {
    val context = LocalContext.current
    val strings = LocalStrings.current
    val density = LocalDensity.current
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var tileTick by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val textMeasurer: TextMeasurer = rememberTextMeasurer()

    var pinchScale by remember { mutableFloatStateOf(1f) }
    val bearingAnim = remember { Animatable(0f) }
    var localCenter by remember(center) { mutableStateOf(center) }
    var localZoom by remember(zoom) { mutableIntStateOf(zoom.coerceIn(MapTileMath.MIN_ZOOM, MapTileMath.MAX_ZOOM)) }

    val latestPoints by rememberUpdatedState(points)
    val latestExtraPolygons by rememberUpdatedState(extraPolygons)
    val latestSavedOverlays by rememberUpdatedState(savedOverlays)
    val latestIsDrawEnabled by rememberUpdatedState(isDrawEnabled)
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
    val currentBearing = bearingAnim.value

    // Trigger tile fetches around visible rotated viewport
    LaunchedEffect(localCenter, localZoom, currentBearing, isSatellite, canvasSize) {
        if (canvasSize.width == 0 || canvasSize.height == 0) return@LaunchedEffect
        val maxTile = (1 shl localZoom)
        val range = MapTileMath.computeRotatedTileRange(
            centerWorldX = centerWorldX,
            centerWorldY = centerWorldY,
            halfW = canvasSize.width / 2.0,
            halfH = canvasSize.height / 2.0,
            scale = pinchScale.toDouble().coerceAtLeast(0.5),
            maxTile = maxTile,
            bearingDeg = currentBearing,
        )

        for (tx in range.minTx..range.maxTx) {
            val wrappedX = ((tx % maxTile) + maxTile) % maxTile
            for (ty in range.minTy..range.maxTy) {
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

    val allFitPoints = remember(points, extraPolygons, savedOverlays) {
        when {
            points.isNotEmpty() -> points
            extraPolygons.isNotEmpty() -> extraPolygons.flatten()
            else -> savedOverlays.flatMap { it.points }
        }
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
                        val halfW = size.width / 2.0
                        val halfH = size.height / 2.0
                        var draggingIdx = -1
                        if (!isReadOnly && latestIsDrawEnabled) {
                            val (cwX, cwY) = GeoUtils.latLonToWorldPx(localCenter.lat, localCenter.lon, localZoom)
                            pts.forEachIndexed { idx, pt ->
                                val (px, py) = GeoUtils.latLonToWorldPx(pt.lat, pt.lon, localZoom)
                                val (sx, sy) = MapTileMath.worldToScreen(
                                    wx = px,
                                    wy = py,
                                    centerWorldX = cwX,
                                    centerWorldY = cwY,
                                    scale = pinchScale.toDouble(),
                                    bearingDeg = bearingAnim.value,
                                    halfW = halfW,
                                    halfH = halfH,
                                )
                                if (hypot(sx - down.position.x, sy - down.position.y) <= 46f) {
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
                                val rotationChange = event.calculateRotation()
                                val centroid = event.calculateCentroid(useCurrent = true)

                                if (zoomChange != 1f || panChange != Offset.Zero || abs(rotationChange) > 0.01f) {
                                    moved = true
                                    val oldScale = pinchScale
                                    val oldBearing = bearingAnim.value
                                    val oldZoom = localZoom
                                    val (oldCwX, oldCwY) = GeoUtils.latLonToWorldPx(
                                        localCenter.lat,
                                        localCenter.lon,
                                        oldZoom,
                                    )

                                    val anchorLatLon = if (centroid != Offset.Unspecified) {
                                        val (anchorWx, anchorWy) = MapTileMath.screenToWorld(
                                            sx = centroid.x - panChange.x,
                                            sy = centroid.y - panChange.y,
                                            centerWorldX = oldCwX,
                                            centerWorldY = oldCwY,
                                            scale = oldScale.toDouble(),
                                            bearingDeg = oldBearing,
                                            halfW = halfW,
                                            halfH = halfH,
                                        )
                                        GeoUtils.worldPxToLatLon(anchorWx, anchorWy, oldZoom)
                                    } else {
                                        null
                                    }

                                    var nextScale = (oldScale * zoomChange).coerceIn(0.45f, 2.25f)
                                    var nextZoom = oldZoom
                                    while (nextScale >= 1.65f && nextZoom < MapTileMath.MAX_ZOOM) {
                                        nextZoom += 1
                                        nextScale /= 2f
                                    }
                                    while (nextScale <= 0.62f && nextZoom > MapTileMath.MIN_ZOOM) {
                                        nextZoom -= 1
                                        nextScale *= 2f
                                    }

                                    val nextBearing = MapTileMath.normalizeBearingDeg(oldBearing + rotationChange)
                                    scope.launch { bearingAnim.snapTo(nextBearing) }

                                    if (anchorLatLon != null && centroid != Offset.Unspecified) {
                                        val (newAnchorWx, newAnchorWy) = GeoUtils.latLonToWorldPx(
                                            anchorLatLon.lat,
                                            anchorLatLon.lon,
                                            nextZoom,
                                        )
                                        val (offsetWx, offsetWy) = MapTileMath.screenToWorld(
                                            sx = centroid.x,
                                            sy = centroid.y,
                                            centerWorldX = 0.0,
                                            centerWorldY = 0.0,
                                            scale = nextScale.toDouble(),
                                            bearingDeg = nextBearing,
                                            halfW = halfW,
                                            halfH = halfH,
                                        )
                                        localCenter = GeoUtils.worldPxToLatLon(
                                            newAnchorWx - offsetWx,
                                            newAnchorWy - offsetWy,
                                            nextZoom,
                                        )
                                    } else {
                                        val (dwx, dwy) = MapTileMath.panDeltaToWorldDelta(
                                            panX = panChange.x,
                                            panY = panChange.y,
                                            scale = oldScale.toDouble(),
                                            bearingDeg = nextBearing,
                                        )
                                        val (curX, curY) = GeoUtils.latLonToWorldPx(
                                            localCenter.lat,
                                            localCenter.lon,
                                            nextZoom,
                                        )
                                        localCenter = GeoUtils.worldPxToLatLon(curX + dwx, curY + dwy, nextZoom)
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
                                    val curBearing = bearingAnim.value
                                    if (draggingIdx in latestPoints.indices && !isReadOnly && latestIsDrawEnabled) {
                                        val (cwX, cwY) = GeoUtils.latLonToWorldPx(
                                            localCenter.lat,
                                            localCenter.lon,
                                            localZoom,
                                        )
                                        val (wx, wy) = MapTileMath.screenToWorld(
                                            sx = change.position.x,
                                            sy = change.position.y,
                                            centerWorldX = cwX,
                                            centerWorldY = cwY,
                                            scale = pinchScale.toDouble(),
                                            bearingDeg = curBearing,
                                            halfW = halfW,
                                            halfH = halfH,
                                        )
                                        latestOnMovePoint(draggingIdx, GeoUtils.worldPxToLatLon(wx, wy, localZoom))
                                    } else {
                                        val (cwX, cwY) = GeoUtils.latLonToWorldPx(
                                            localCenter.lat,
                                            localCenter.lon,
                                            localZoom,
                                        )
                                        val (dwx, dwy) = MapTileMath.panDeltaToWorldDelta(
                                            panX = panChange.x,
                                            panY = panChange.y,
                                            scale = pinchScale.toDouble(),
                                            bearingDeg = curBearing,
                                        )
                                        val newCenter = GeoUtils.worldPxToLatLon(cwX + dwx, cwY + dwy, localZoom)
                                        localCenter = newCenter
                                        latestOnCenterChanged(newCenter)
                                    }
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            }
                        } while (true)

                        // Snap fractional pinch scale cleanly into integer tile zoom
                        if (pinchScale >= 1.35f && localZoom < MapTileMath.MAX_ZOOM) {
                            localZoom += 1
                            latestOnZoomChanged(localZoom)
                        } else if (pinchScale <= 0.72f && localZoom > MapTileMath.MIN_ZOOM) {
                            localZoom -= 1
                            latestOnZoomChanged(localZoom)
                        }
                        pinchScale = 1f

                        if (!moved && !multiTouchUsed && !isReadOnly && latestIsDrawEnabled) {
                            val (cwX, cwY) = GeoUtils.latLonToWorldPx(localCenter.lat, localCenter.lon, localZoom)
                            val (wx, wy) = MapTileMath.screenToWorld(
                                sx = down.position.x,
                                sy = down.position.y,
                                centerWorldX = cwX,
                                centerWorldY = cwY,
                                scale = 1.0,
                                bearingDeg = bearingAnim.value,
                                halfW = size.width / 2.0,
                                halfH = size.height / 2.0,
                            )
                            latestOnAddPoint(GeoUtils.worldPxToLatLon(wx, wy, localZoom))
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
            val range = MapTileMath.computeRotatedTileRange(
                centerWorldX = centerWorldX,
                centerWorldY = centerWorldY,
                halfW = halfW,
                halfH = halfH,
                scale = scale,
                maxTile = maxTile,
                bearingDeg = currentBearing,
            )

            // Draw rotated satellite or street map tiles
            withTransform({
                rotate(degrees = currentBearing, pivot = Offset(halfW.toFloat(), halfH.toFloat()))
            }) {
                for (tx in range.minTx..range.maxTx) {
                    val wrappedX = ((tx % maxTile) + maxTile) % maxTile
                    for (ty in range.minTy..range.maxTy) {
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
            }

            fun toScreen(pt: LatLon): Offset {
                val (wx, wy) = GeoUtils.latLonToWorldPx(pt.lat, pt.lon, localZoom)
                val (sx, sy) = MapTileMath.worldToScreen(
                    wx = wx,
                    wy = wy,
                    centerWorldX = centerWorldX,
                    centerWorldY = centerWorldY,
                    scale = scale,
                    bearingDeg = currentBearing,
                    halfW = halfW,
                    halfH = halfH,
                )
                return Offset(sx, sy)
            }

            // Draw Saved Farm Fields Overlays (with centroid label like reference screenshot)
            latestSavedOverlays.forEach { saved ->
                if (saved.points.size >= 3) {
                    val pts = saved.points.map(::toScreen)
                    val path = Path().apply {
                        moveTo(pts.first().x, pts.first().y)
                        pts.drop(1).forEach { lineTo(it.x, it.y) }
                        close()
                    }
                    drawPath(path = path, color = Color(0xFF22C55E).copy(alpha = 0.34f))
                    drawPath(path = path, color = Color(0xFF14532D).copy(alpha = 0.7f), style = Stroke(width = 6f))
                    drawPath(path = path, color = Color(0xFF4ADE80), style = Stroke(width = 3.2f))

                    if (localZoom >= 12) {
                        val cx = pts.map { it.x }.average().toFloat()
                        val cy = pts.map { it.y }.average().toFloat()
                        if (cx in -200f..(size.width + 200f) && cy in -200f..(size.height + 200f)) {
                            val titleLine = if (saved.crop.isNotBlank()) "${saved.name} (${saved.crop})" else saved.name
                            val areaLine = "${formatNumber(saved.areaHa, 2)} ha"
                            drawHaloText(
                                textMeasurer = textMeasurer,
                                text = "$titleLine\n$areaLine",
                                center = Offset(cx, cy),
                                fontSizeSp = 12f,
                            )
                        }
                    }
                }
            }

            // Draw secondary/background KMZ polygons if provided
            latestExtraPolygons.forEach { polyPts ->
                if (polyPts.size >= 2) {
                    val screenExtra = polyPts.map(::toScreen)
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

            // Convert primary active drawing points to screen offsets
            val screenPoints = latestPoints.map(::toScreen)

            if (screenPoints.size >= 2) {
                val path = Path().apply {
                    moveTo(screenPoints.first().x, screenPoints.first().y)
                    screenPoints.drop(1).forEach { lineTo(it.x, it.y) }
                    if (screenPoints.size >= 3) close()
                }
                if (screenPoints.size >= 3) {
                    drawPath(path = path, color = EmeraldBright.copy(alpha = 0.30f))
                }
                drawPath(path = path, color = Color.Black.copy(alpha = 0.55f), style = Stroke(width = 8.5f))
                drawPath(path = path, color = EmeraldBright, style = Stroke(width = 5f))

                // Segment length labels along edges when zoomed in enough
                val edgeCount = if (screenPoints.size >= 3) screenPoints.size else screenPoints.size - 1
                for (i in 0 until edgeCount) {
                    val nextIdx = (i + 1) % screenPoints.size
                    val aScreen = screenPoints[i]
                    val bScreen = screenPoints[nextIdx]
                    if (hypot(bScreen.x - aScreen.x, bScreen.y - aScreen.y) >= 58f) {
                        val meters = GeoUtils.haversineDistanceM(latestPoints[i], latestPoints[nextIdx])
                        if (meters >= 1.0) {
                            val mid = Offset((aScreen.x + bScreen.x) / 2f, (aScreen.y + bScreen.y) / 2f)
                            drawHaloText(
                                textMeasurer = textMeasurer,
                                text = "${meters.roundToInt()} m",
                                center = mid,
                                fontSizeSp = 11f,
                            )
                        }
                    }
                }
            }

            // Draw GPS Location Blue Marker
            gpsLocation?.let { gps ->
                val gOff = toScreen(gps)
                drawCircle(color = Color(0xFF2196F3).copy(alpha = 0.28f), radius = 36f, center = gOff)
                drawCircle(color = Color.White, radius = 16f, center = gOff)
                drawCircle(color = Color(0xFF1976D2), radius = 11f, center = gOff)
            }

            // Draw numbered corner vertices
            screenPoints.forEachIndexed { idx, offset ->
                drawCircle(color = Color.Black.copy(alpha = 0.45f), radius = 24f, center = offset)
                drawCircle(color = Color.White, radius = 21f, center = offset)
                drawCircle(color = EmeraldPrimary, radius = 16.5f, center = offset)
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

        // Collision-free structured HUD overlay respecting top/bottom system bars
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isFullscreen) {
                        Modifier.statusBarsPadding().navigationBarsPadding()
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = 10.dp, vertical = 10.dp),
        ) {
            // 1. Top Overlay Row — Compass | Centered Live Metrics | Layer / Fullscreen controls
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // Rotating Compass / North Reset button
                    Surface(
                        shape = CircleShape,
                        color = MapDockDarkBg,
                        border = BorderStroke(1.dp, MapDockBorder),
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable {
                                scope.launch {
                                    bearingAnim.animateTo(0f, tween(durationMillis = 280))
                                }
                            }
                            .testTag("map_compass_reset"),
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            CompassNeedleCanvas(bearingDeg = currentBearing)
                        }
                    }

                    if (!isFullscreen && onToggleFullscreen != null) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MapDockDarkBg,
                            border = BorderStroke(1.dp, MapDockBorder),
                            shadowElevation = 4.dp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable(onClick = onToggleFullscreen)
                                .testTag("map_fullscreen_toggle"),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Fullscreen,
                                    contentDescription = strings.btnFullscreenMap,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = strings.btnFullscreenMap,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }

                if (!measurementSummary.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MapDockDarkBg,
                        border = BorderStroke(1.dp, MapDockBorder),
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 6.dp),
                    ) {
                        Text(
                            text = measurementSummary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 9.dp)
                                .testTag("map_measurement_hud"),
                        )
                    }
                }

                if (isFullscreen) {
                    if (onToggleFullscreen != null) {
                        Surface(
                            shape = CircleShape,
                            color = MapDockDarkBg,
                            border = BorderStroke(1.dp, MapDockBorder),
                            shadowElevation = 4.dp,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .clickable(onClick = onToggleFullscreen)
                                .testTag("fullscreen_exit_btn"),
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    imageVector = Icons.Filled.FullscreenExit,
                                    contentDescription = strings.btnExitFullscreenMap,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                } else {
                    // Compact inline top-right controls (Layers + Locate)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MapDockDarkBg,
                            border = BorderStroke(1.dp, MapDockBorder),
                            shadowElevation = 4.dp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable(onClick = onToggleLayer)
                                .testTag("map_layer_toggle"),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Layers,
                                    contentDescription = null,
                                    tint = Color(0xFF86EFAC),
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = if (isSatellite) strings.btnSatelliteLayer else strings.btnStreetLayer,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        if (!isReadOnly) {
                            Surface(
                                shape = CircleShape,
                                color = EmeraldPrimary,
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
                                shadowElevation = 4.dp,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .clickable(onClick = onLocateMe)
                                    .testTag("map_locate_button"),
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Icon(
                                        imageVector = Icons.Filled.MyLocation,
                                        contentDescription = strings.btnLocateMe,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. Middle viewport area — Right Vertical Control Dock + Scale Bar
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                // Bottom-left bearing degree chip + live metric Scale Bar
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(bottom = 4.dp),
                ) {
                    if (abs(currentBearing) >= 1f) {
                        val displayDeg = ((currentBearing.roundToInt() % 360) + 360) % 360
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MapDockDarkBg,
                            border = BorderStroke(1.dp, MapDockBorder),
                        ) {
                            Text(
                                text = "${displayDeg}°",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }

                    val maxScaleBarPx = with(density) { 104.dp.toPx() }
                    val scaleSpec = MapTileMath.computeScaleBar(
                        latDeg = localCenter.lat,
                        zoom = localZoom,
                        pinchScale = pinchScale,
                        maxBarPx = maxScaleBarPx,
                    )
                    MapScaleBar(
                        label = scaleSpec.label,
                        barWidthDp = with(density) { scaleSpec.widthPx.toDp() },
                    )
                }

                // Right Vertical Tool Dock (clean capsule layout like reference screenshot)
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 2.dp),
                ) {
                    if (isFullscreen) {
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = MapDockDarkBg,
                            border = BorderStroke(1.dp, MapDockBorder),
                            shadowElevation = 4.dp,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(
                                    onClick = onToggleLayer,
                                    modifier = Modifier.size(44.dp).testTag("map_layer_toggle"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Layers,
                                        contentDescription = if (isSatellite) strings.btnSatelliteLayer else strings.btnStreetLayer,
                                        tint = Color.White,
                                    )
                                }
                                if (!isReadOnly) {
                                    HorizontalDivider(
                                        color = Color.White.copy(alpha = 0.14f),
                                        modifier = Modifier.width(26.dp),
                                    )
                                    IconButton(
                                        onClick = onLocateMe,
                                        modifier = Modifier.size(44.dp).testTag("map_locate_button"),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.MyLocation,
                                            contentDescription = strings.btnLocateMe,
                                            tint = Color(0xFF86EFAC),
                                        )
                                    }
                                }
                                if (allFitPoints.isNotEmpty()) {
                                    HorizontalDivider(
                                        color = Color.White.copy(alpha = 0.14f),
                                        modifier = Modifier.width(26.dp),
                                    )
                                    IconButton(
                                        onClick = {
                                            val (fitCenter, fitZoom) = GeoUtils.computeBoundsCenterAndZoom(allFitPoints)
                                            pinchScale = 1f
                                            localCenter = fitCenter
                                            localZoom = fitZoom
                                            onCenterChanged(fitCenter)
                                            onZoomChanged(fitZoom)
                                        },
                                        modifier = Modifier.size(44.dp).testTag("map_fit_bounds"),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.CenterFocusStrong,
                                            contentDescription = strings.btnFitBounds,
                                            tint = Color.White,
                                        )
                                    }
                                }
                            }
                        }
                    } else if (allFitPoints.isNotEmpty()) {
                        Surface(
                            shape = CircleShape,
                            color = MapDockDarkBg,
                            border = BorderStroke(1.dp, MapDockBorder),
                            shadowElevation = 4.dp,
                        ) {
                            IconButton(
                                onClick = {
                                    val (fitCenter, fitZoom) = GeoUtils.computeBoundsCenterAndZoom(allFitPoints)
                                    pinchScale = 1f
                                    localCenter = fitCenter
                                    localZoom = fitZoom
                                    onCenterChanged(fitCenter)
                                    onZoomChanged(fitZoom)
                                },
                                modifier = Modifier.size(40.dp).testTag("map_fit_bounds"),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CenterFocusStrong,
                                    contentDescription = strings.btnFitBounds,
                                    tint = Color.White,
                                )
                            }
                        }
                    }

                    // Zoom + / - stacked pill
                    val zoomBtnSize = if (isFullscreen) 44.dp else 40.dp
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = MapDockDarkBg,
                        border = BorderStroke(1.dp, MapDockBorder),
                        shadowElevation = 4.dp,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = {
                                    val next = (localZoom + 1).coerceAtMost(MapTileMath.MAX_ZOOM)
                                    pinchScale = 1f
                                    localZoom = next
                                    onZoomChanged(next)
                                },
                                modifier = Modifier.size(zoomBtnSize).testTag("map_zoom_in"),
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = strings.zoomIn, tint = Color.White)
                            }
                            HorizontalDivider(
                                color = Color.White.copy(alpha = 0.14f),
                                modifier = Modifier.width(24.dp),
                            )
                            IconButton(
                                onClick = {
                                    val next = (localZoom - 1).coerceAtLeast(MapTileMath.MIN_ZOOM)
                                    pinchScale = 1f
                                    localZoom = next
                                    onZoomChanged(next)
                                },
                                modifier = Modifier.size(zoomBtnSize).testTag("map_zoom_out"),
                            ) {
                                Icon(Icons.Filled.Remove, contentDescription = strings.zoomOut, tint = Color.White)
                            }
                        }
                    }
                }
            }

            // 3. Bottom functional action slot (strictly below Middle viewport so zero overlaps!)
            if (bottomOverlay != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    bottomOverlay()
                }
            }
        }
    }
}

@Composable
private fun CompassNeedleCanvas(bearingDeg: Float) {
    Canvas(modifier = Modifier.size(24.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        withTransform({
            rotate(degrees = bearingDeg, pivot = Offset(cx, cy))
        }) {
            val northPath = Path().apply {
                moveTo(cx, cy - size.height * 0.40f)
                lineTo(cx - size.width * 0.16f, cy)
                lineTo(cx + size.width * 0.16f, cy)
                close()
            }
            drawPath(northPath, color = Color(0xFFEF4444))

            val southPath = Path().apply {
                moveTo(cx, cy + size.height * 0.40f)
                lineTo(cx - size.width * 0.16f, cy)
                lineTo(cx + size.width * 0.16f, cy)
                close()
            }
            drawPath(southPath, color = Color.White.copy(alpha = 0.85f))
            drawCircle(color = Color(0xFF1E293B), radius = 2.2f, center = Offset(cx, cy))
        }
    }
}

@Composable
private fun MapScaleBar(
    label: String,
    barWidthDp: androidx.compose.ui.unit.Dp,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = Color.Black.copy(alpha = 0.46f),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Canvas(
            modifier = Modifier
                .width(barWidthDp)
                .height(8.dp),
        ) {
            val w = size.width
            val h = size.height
            // Dark halo
            drawLine(
                color = Color.Black.copy(alpha = 0.72f),
                start = Offset(0f, h - 2f),
                end = Offset(w, h - 2f),
                strokeWidth = 5f,
            )
            drawLine(
                color = Color.Black.copy(alpha = 0.72f),
                start = Offset(1.5f, 0f),
                end = Offset(1.5f, h),
                strokeWidth = 4.5f,
            )
            drawLine(
                color = Color.Black.copy(alpha = 0.72f),
                start = Offset(w - 1.5f, 0f),
                end = Offset(w - 1.5f, h),
                strokeWidth = 4.5f,
            )
            // White foreground ticks
            drawLine(
                color = Color.White,
                start = Offset(0f, h - 2f),
                end = Offset(w, h - 2f),
                strokeWidth = 2.4f,
            )
            drawLine(
                color = Color.White,
                start = Offset(1.5f, 1f),
                end = Offset(1.5f, h),
                strokeWidth = 2.2f,
            )
            drawLine(
                color = Color.White,
                start = Offset(w - 1.5f, 1f),
                end = Offset(w - 1.5f, h),
                strokeWidth = 2.2f,
            )
        }
    }
}

private fun DrawScope.drawHaloText(
    textMeasurer: TextMeasurer,
    text: String,
    center: Offset,
    fontSizeSp: Float,
) {
    val haloLayout = textMeasurer.measure(
        text = text,
        style = TextStyle(
            color = Color.Black.copy(alpha = 0.90f),
            fontSize = fontSizeSp.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        ),
    )
    val mainLayout = textMeasurer.measure(
        text = text,
        style = TextStyle(
            color = Color.White,
            fontSize = fontSizeSp.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        ),
    )
    val baseTopLeft = Offset(
        x = center.x - mainLayout.size.width / 2f,
        y = center.y - mainLayout.size.height / 2f,
    )
    val haloOffsets = listOf(
        Offset(-1.6f, 0f),
        Offset(1.6f, 0f),
        Offset(0f, -1.6f),
        Offset(0f, 1.6f),
    )
    haloOffsets.forEach { d ->
        drawText(textLayoutResult = haloLayout, topLeft = baseTopLeft + d)
    }
    drawText(textLayoutResult = mainLayout, topLeft = baseTopLeft)
}
