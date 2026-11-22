package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.data.model.FarmField
import com.example.domain.ClimateHistorySummary
import com.example.domain.ClimateHistoryUtils
import com.example.domain.GeoUtils
import com.example.domain.LatLon
import com.example.i18n.LocalStrings
import com.example.ui.components.AqroCard
import com.example.ui.components.MapTileLoader
import com.example.ui.components.MapTileMath
import com.example.ui.components.TabPillsRow
import com.example.ui.components.formatNumber
import com.example.ui.theme.LocalAqroColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

enum class ClimateMapLayer {
    TEMPERATURE,
    PRECIP_HUMIDITY,
    WIND,
    SOLAR_CLOUD,
    PRESSURE_ET0,
}

private data class ClimateTimeFrame(
    val label: String,
    val tempC: Double,
    val minTempC: Double,
    val maxTempC: Double,
    val precipMm: Double,
    val humidityPct: Double,
    val windKmh: Double,
    val gustKmh: Double,
    val windDeg: Double,
    val radiationMjM2: Double,
    val cloudPct: Double,
    val pressureHpa: Double,
    val et0Mm: Double,
)

@Composable
fun ClimateTimeMapCard(
    summary: ClimateHistorySummary,
    centerLat: Double,
    centerLon: Double,
    selectedField: FarmField?,
    useYearly: Boolean,
    onToggleYearly: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val strings = LocalStrings.current
    val extra = LocalAqroColors.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(context) {
        MapTileLoader.initCache(context)
    }

    val frames = remember(summary, useYearly) {
        if (useYearly && summary.yearlySummaries.size >= 2) {
            summary.yearlySummaries.map { y ->
                ClimateTimeFrame(
                    label = y.year,
                    tempC = y.avgTempC,
                    minTempC = y.minTempC,
                    maxTempC = y.maxTempC,
                    precipMm = y.totalPrecipMm,
                    humidityPct = y.avgHumidityPct,
                    windKmh = y.avgWindMaxKmh,
                    gustKmh = y.maxGustKmh,
                    windDeg = summary.dominantWindDeg,
                    radiationMjM2 = y.avgRadiationMjM2,
                    cloudPct = y.avgCloudCoverPct,
                    pressureHpa = y.avgPressureHpa,
                    et0Mm = y.totalEt0Mm,
                )
            }
        } else {
            summary.monthlyChronological.map { m ->
                ClimateTimeFrame(
                    label = m.yearMonth,
                    tempC = m.avgTempMean,
                    minTempC = m.minTemp,
                    maxTempC = m.maxTemp,
                    precipMm = m.totalPrecipMm,
                    humidityPct = m.avgHumidityPct,
                    windKmh = m.avgWindMaxKmh,
                    gustKmh = m.maxGustKmh,
                    windDeg = m.dominantWindDeg,
                    radiationMjM2 = m.avgRadiationMjM2,
                    cloudPct = m.avgCloudCoverPct,
                    pressureHpa = m.avgPressureHpa,
                    et0Mm = m.totalEt0Mm,
                )
            }
        }
    }

    if (frames.isEmpty()) return

    var activeLayer by remember { mutableStateOf(ClimateMapLayer.TEMPERATURE) }
    var frameIndex by remember(frames) { mutableIntStateOf(frames.lastIndex) }
    var isPlaying by remember { mutableStateOf(false) }
    var isSatellite by remember { mutableStateOf(true) }

    val fieldPoly = remember(selectedField) {
        selectedField?.coords?.mapNotNull { GeoUtils.decodeCoord(it) }.orEmpty()
    }
    var mapCenter by remember(centerLat, centerLon, selectedField) {
        mutableStateOf(
            if (fieldPoly.isNotEmpty()) {
                LatLon(fieldPoly.map { it.lat }.average(), fieldPoly.map { it.lon }.average())
            } else {
                LatLon(centerLat, centerLon)
            }
        )
    }
    var mapZoom by remember(centerLat, centerLon, selectedField) {
        mutableIntStateOf(if (fieldPoly.size >= 3) 14 else 9)
    }
    var pinchScale by remember { mutableFloatStateOf(1f) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var tileTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(isPlaying, frames.size) {
        if (!isPlaying || frames.size <= 1) return@LaunchedEffect
        while (isPlaying) {
            delay(650L)
            frameIndex = if (frameIndex >= frames.lastIndex) 0 else frameIndex + 1
        }
    }

    val safeIdx = frameIndex.coerceIn(0, frames.lastIndex)
    val frame = frames[safeIdx]
    val layerColor = remember(activeLayer, frame, summary) {
        computeClimateTint(activeLayer, frame, summary)
    }
    val (worldCx, worldCy) = remember(mapCenter, mapZoom) {
        GeoUtils.latLonToWorldPx(mapCenter.lat, mapCenter.lon, mapZoom)
    }

    LaunchedEffect(mapCenter, mapZoom, isSatellite, canvasSize) {
        if (canvasSize.width == 0 || canvasSize.height == 0) return@LaunchedEffect
        val maxTile = (1 shl mapZoom)
        val halfW = canvasSize.width / 2.0
        val halfH = canvasSize.height / 2.0
        val minTx = floor((worldCx - halfW) / 256.0).toInt()
        val maxTx = floor((worldCx + halfW) / 256.0).toInt()
        val minTy = floor((worldCy - halfH) / 256.0).toInt().coerceIn(0, maxTile - 1)
        val maxTy = floor((worldCy + halfH) / 256.0).toInt().coerceIn(0, maxTile - 1)
        for (tx in minTx..maxTx) {
            val wx = ((tx % maxTile) + maxTile) % maxTile
            for (ty in minTy..maxTy) {
                val best = MapTileLoader.resolveBestTile(mapZoom, wx, ty, isSatellite)
                val exactCached = best != null && best.srcSize.width == best.bitmap.width
                if (!exactCached) {
                    scope.launch {
                        MapTileLoader.fetchTileWithAncestors(mapZoom, wx, ty, isSatellite) {
                            tileTick++
                        }
                    }
                }
            }
        }
    }

    AqroCard(modifier = Modifier.testTag("climate_time_map_card")) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.Map, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column {
                    Text(strings.climateMapTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(strings.climateMapSub, style = MaterialTheme.typography.labelSmall, color = extra.muted)
                }
            }
        }

        if (summary.yearlySummaries.size >= 2) {
            Spacer(modifier = Modifier.height(8.dp))
            TabPillsRow(
                tabs = listOf(strings.climateTimelineMonthly, strings.climateTimelineYearly),
                selectedIndex = if (useYearly) 1 else 0,
                onSelect = { idx ->
                    isPlaying = false
                    onToggleYearly(idx == 1)
                },
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            val layerChips = listOf(
                ClimateMapLayer.TEMPERATURE to strings.climateLayerTemp,
                ClimateMapLayer.PRECIP_HUMIDITY to strings.climateLayerPrecipHumidity,
                ClimateMapLayer.WIND to strings.climateLayerWind,
                ClimateMapLayer.SOLAR_CLOUD to strings.climateLayerSolarCloud,
                ClimateMapLayer.PRESSURE_ET0 to strings.climateLayerPressureEt0,
            )
            layerChips.forEach { (layer, label) ->
                FilterChip(
                    selected = activeLayer == layer,
                    onClick = { activeLayer = layer },
                    label = { Text(label, maxLines = 1) },
                    modifier = Modifier.testTag("climate_map_layer_${layer.name.lowercase()}"),
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (isSatellite) Color(0xFF15221B) else Color(0xFFDCE5EC)),
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            var multiTouchUsed = false
                            do {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.isEmpty()) break
                                if (pressed.size >= 2) {
                                    multiTouchUsed = true
                                    val zoomChange = event.calculateZoom()
                                    val panChange = event.calculatePan()
                                    val centroid = event.calculateCentroid(useCurrent = true)
                                    val oldScale = pinchScale
                                    var nextScale = (oldScale * zoomChange).coerceIn(0.45f, 2.25f)
                                    var nextZoom = mapZoom
                                    val (cwX, cwY) = GeoUtils.latLonToWorldPx(mapCenter.lat, mapCenter.lon, nextZoom)
                                    val hw = size.width / 2.0
                                    val hh = size.height / 2.0
                                    if (centroid != Offset.Unspecified) {
                                        val ax = cwX + (centroid.x - panChange.x - hw) / oldScale
                                        val ay = cwY + (centroid.y - panChange.y - hh) / oldScale
                                        mapCenter = GeoUtils.worldPxToLatLon(
                                            ax - (centroid.x - hw) / nextScale,
                                            ay - (centroid.y - hh) / nextScale,
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
                                    mapZoom = nextZoom
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                } else if (!multiTouchUsed) {
                                    val panChange = event.calculatePan()
                                    if (panChange != Offset.Zero) {
                                        val (cwX, cwY) = GeoUtils.latLonToWorldPx(mapCenter.lat, mapCenter.lon, mapZoom)
                                        mapCenter = GeoUtils.worldPxToLatLon(
                                            cwX - panChange.x / pinchScale,
                                            cwY - panChange.y / pinchScale,
                                            mapZoom,
                                        )
                                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                                    }
                                }
                            } while (true)
                            pinchScale = 1f
                        }
                    }
                    .testTag("climate_time_map_canvas"),
            ) {
                val unusedTick = tileTick
                val hw = size.width / 2.0
                val hh = size.height / 2.0
                val maxTile = (1 shl mapZoom)
                val scale = pinchScale.toDouble()
                val tileSizePx = (256.0 * scale)

                val minTx = floor((worldCx - hw / scale) / 256.0).toInt()
                val maxTx = floor((worldCx + hw / scale) / 256.0).toInt()
                val minTy = floor((worldCy - hh / scale) / 256.0).toInt().coerceIn(0, maxTile - 1)
                val maxTy = floor((worldCy + hh / scale) / 256.0).toInt().coerceIn(0, maxTile - 1)

                for (tx in minTx..maxTx) {
                    val wx = ((tx % maxTile) + maxTile) % maxTile
                    for (ty in minTy..maxTy) {
                        val left = ((tx * 256.0 - worldCx) * scale + hw).roundToInt()
                        val top = ((ty * 256.0 - worldCy) * scale + hh).roundToInt()
                        val right = (((tx + 1) * 256.0 - worldCx) * scale + hw).roundToInt()
                        val bottom = (((ty + 1) * 256.0 - worldCy) * scale + hh).roundToInt()
                        val resolved = MapTileLoader.resolveBestTile(mapZoom, wx, ty, isSatellite)
                        if (resolved != null) {
                            drawImage(
                                image = resolved.bitmap,
                                srcOffset = resolved.srcOffset,
                                srcSize = resolved.srcSize,
                                dstOffset = IntOffset(left, top),
                                dstSize = IntSize((right - left + 1).coerceAtLeast(1), (bottom - top + 1).coerceAtLeast(1)),
                                filterQuality = FilterQuality.Medium,
                            )
                        } else {
                            drawRect(
                                color = if (isSatellite) Color(0xFF1D2D24) else Color(0xFFD5E1EA),
                                topLeft = Offset(left.toFloat(), top.toFloat()),
                                size = androidx.compose.ui.geometry.Size(tileSizePx.toFloat() + 1f, tileSizePx.toFloat() + 1f),
                            )
                        }
                    }
                }

                // Dynamic Spatial Climate Gradient Overlay across the region
                val (anchorWx, anchorWy) = GeoUtils.latLonToWorldPx(centerLat, centerLon, mapZoom)
                val anchorScreen = Offset(
                    x = ((anchorWx - worldCx) * scale + hw).toFloat(),
                    y = ((anchorWy - worldCy) * scale + hh).toFloat(),
                )
                drawRect(color = layerColor.copy(alpha = 0.22f))
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            layerColor.copy(alpha = 0.52f),
                            layerColor.copy(alpha = 0.24f),
                            Color.Transparent,
                        ),
                        center = anchorScreen,
                        radius = size.minDimension * 0.65f,
                    ),
                    radius = size.minDimension * 0.65f,
                    center = anchorScreen,
                )

                // Surrounding Azerbaijan regional climate nodes
                ClimateHistoryUtils.regionPresets.forEach { preset ->
                    val (pwX, pwY) = GeoUtils.latLonToWorldPx(preset.lat, preset.lon, mapZoom)
                    val px = ((pwX - worldCx) * scale + hw).toFloat()
                    val py = ((pwY - worldCy) * scale + hh).toFloat()
                    if (px in -40f..(size.width + 40f) && py in -40f..(size.height + 40f)) {
                        drawCircle(color = layerColor.copy(alpha = 0.85f), radius = 11f, center = Offset(px, py))
                        drawCircle(color = Color.White, radius = 11f, center = Offset(px, py), style = Stroke(width = 2.5f))
                    }
                }

                // Field polygon highlighted in climate-driven color
                if (fieldPoly.size >= 2) {
                    val polyScreen = fieldPoly.map { pt ->
                        val (wx, wy) = GeoUtils.latLonToWorldPx(pt.lat, pt.lon, mapZoom)
                        Offset(
                            x = ((wx - worldCx) * scale + hw).toFloat(),
                            y = ((wy - worldCy) * scale + hh).toFloat(),
                        )
                    }
                    val path = Path().apply {
                        moveTo(polyScreen.first().x, polyScreen.first().y)
                        polyScreen.drop(1).forEach { lineTo(it.x, it.y) }
                        if (polyScreen.size >= 3) close()
                    }
                    if (polyScreen.size >= 3) {
                        drawPath(path = path, color = layerColor.copy(alpha = 0.45f))
                    }
                    drawPath(path = path, color = Color.White, style = Stroke(width = 6f))
                    drawPath(path = path, color = layerColor, style = Stroke(width = 3.5f))
                } else {
                    drawCircle(color = Color.White, radius = 14f, center = anchorScreen)
                    drawCircle(color = layerColor, radius = 10f, center = anchorScreen)
                }

                // Wind vector field overlay when WIND layer is selected
                if (activeLayer == ClimateMapLayer.WIND) {
                    val rad = Math.toRadians(frame.windDeg - 90.0)
                    val len = (18f + (frame.windKmh.toFloat() * 0.7f)).coerceIn(18f, 44f)
                    val dx = (cos(rad) * len).toFloat()
                    val dy = (sin(rad) * len).toFloat()
                    val gridCols = listOf(0.25f, 0.5f, 0.75f)
                    val gridRows = listOf(0.35f, 0.65f)
                    gridRows.forEach { ry ->
                        gridCols.forEach { rx ->
                            val start = Offset(size.width * rx - dx / 2f, size.height * ry - dy / 2f)
                            val end = Offset(start.x + dx, start.y + dy)
                            drawLine(
                                color = Color.White.copy(alpha = 0.88f),
                                start = start,
                                end = end,
                                strokeWidth = 3.5f,
                                cap = StrokeCap.Round,
                            )
                            drawCircle(color = layerColor, radius = 5f, center = end)
                        }
                    }
                }
            }

            // Layer Switcher Button (Top-Right)
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clickable { isSatellite = !isSatellite },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Icon(Icons.Filled.Layers, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                    Text(
                        text = if (isSatellite) strings.btnSatelliteLayer else strings.btnStreetLayer,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // Live Climate HUD Overlay (Bottom-Left)
            val hudSummary = formatMapHudSummary(activeLayer, frame, summary, strings.weatherWindUnit, strings.climateBaselineDeltaLabel)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                border = BorderStroke(1.dp, layerColor.copy(alpha = 0.7f)),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .testTag("climate_map_hud"),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                ) {
                    Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(layerColor))
                    Column {
                        Text(
                            text = "${frame.label}  •  ${hudSummary.first}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = hudSummary.second,
                            style = MaterialTheme.typography.labelSmall,
                            color = extra.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        // Timeline Slider + Backward/Play/Forward Controls
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            IconButton(
                onClick = {
                    isPlaying = false
                    frameIndex = (safeIdx - 1).coerceAtLeast(0)
                },
                enabled = safeIdx > 0,
                modifier = Modifier.size(44.dp).testTag("climate_slider_prev_btn"),
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Back in time")
            }
            IconButton(
                onClick = { isPlaying = !isPlaying },
                modifier = Modifier.size(44.dp).testTag("climate_slider_play_btn"),
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Slider(
                value = safeIdx.toFloat(),
                onValueChange = {
                    isPlaying = false
                    frameIndex = it.roundToInt().coerceIn(0, frames.lastIndex)
                },
                valueRange = 0f..frames.lastIndex.coerceAtLeast(1).toFloat(),
                enabled = frames.size > 1,
                modifier = Modifier.weight(1f).testTag("climate_timeline_slider"),
            )
            IconButton(
                onClick = {
                    isPlaying = false
                    frameIndex = (safeIdx + 1).coerceAtMost(frames.lastIndex)
                },
                enabled = safeIdx < frames.lastIndex,
                modifier = Modifier.size(44.dp).testTag("climate_slider_next_btn"),
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Forward in time")
            }
        }
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        ) {
            Text(frames.first().label, style = MaterialTheme.typography.labelSmall, color = extra.muted)
            Text(
                text = frame.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("climate_map_active_period"),
            )
            Text(frames.last().label, style = MaterialTheme.typography.labelSmall, color = extra.muted)
        }
    }
}

private fun computeClimateTint(
    layer: ClimateMapLayer,
    frame: ClimateTimeFrame,
    summary: ClimateHistorySummary,
): Color = when (layer) {
    ClimateMapLayer.TEMPERATURE -> when {
        frame.tempC <= 2.0 -> Color(0xFF2563EB)
        frame.tempC <= 12.0 -> Color(0xFF06B6D4)
        frame.tempC <= 21.0 -> Color(0xFF16A34A)
        frame.tempC <= 28.0 -> Color(0xFFF59E0B)
        else -> Color(0xFFDC2626)
    }
    ClimateMapLayer.PRECIP_HUMIDITY -> when {
        frame.precipMm >= 80.0 || frame.humidityPct >= 78.0 -> Color(0xFF1D4ED8)
        frame.precipMm >= 30.0 -> Color(0xFF0284C7)
        frame.humidityPct >= 52.0 -> Color(0xFF0D9488)
        else -> Color(0xFFD97706)
    }
    ClimateMapLayer.WIND -> when {
        frame.gustKmh >= 55.0 || frame.windKmh >= 30.0 -> Color(0xFF9333EA)
        frame.windKmh >= 18.0 -> Color(0xFFF59E0B)
        else -> Color(0xFF10B981)
    }
    ClimateMapLayer.SOLAR_CLOUD -> when {
        frame.cloudPct >= 65.0 -> Color(0xFF64748B)
        frame.radiationMjM2 >= 18.0 -> Color(0xFFF59E0B)
        else -> Color(0xFF0EA5E9)
    }
    ClimateMapLayer.PRESSURE_ET0 -> {
        val et0High = frame.et0Mm >= (summary.totalEt0Mm / summary.monthlyChronological.size.coerceAtLeast(1)) * 1.15
        if (et0High) Color(0xFFEA580C) else Color(0xFF6366F1)
    }
}

private fun formatMapHudSummary(
    layer: ClimateMapLayer,
    frame: ClimateTimeFrame,
    summary: ClimateHistorySummary,
    windUnit: String,
    baselineLabel: String,
): Pair<String, String> = when (layer) {
    ClimateMapLayer.TEMPERATURE -> {
        val diff = frame.tempC - summary.avgTempC
        val sign = if (diff >= 0) "+" else ""
        "${formatNumber(frame.tempC, 1)}°C (${sign}${formatNumber(diff, 1)}°C $baselineLabel)" to
            "Min ${formatNumber(frame.minTempC, 1)}° / Maks ${formatNumber(frame.maxTempC, 1)}°"
    }
    ClimateMapLayer.PRECIP_HUMIDITY ->
        "${frame.precipMm.roundToInt()} mm • ${frame.humidityPct.roundToInt()}%" to
            "Yağıntı: ${frame.precipMm.roundToInt()} mm | Rütubət: ${frame.humidityPct.roundToInt()}%"
    ClimateMapLayer.WIND ->
        "${formatNumber(frame.windKmh, 1)} $windUnit • ${ClimateHistoryUtils.windCompassShort(frame.windDeg)}" to
            "Maks qasırğa: ${frame.gustKmh.roundToInt()} $windUnit (${frame.windDeg.roundToInt()}°)"
    ClimateMapLayer.SOLAR_CLOUD ->
        "${formatNumber(frame.radiationMjM2, 1)} MJ/m² • ${frame.cloudPct.roundToInt()}%" to
            "Buludluq: ${frame.cloudPct.roundToInt()}%"
    ClimateMapLayer.PRESSURE_ET0 ->
        "${formatNumber(frame.pressureHpa, 1)} hPa • ET0: ${frame.et0Mm.roundToInt()} mm" to
            "Atmosfer təzyiqi və buxarlanma"
}
