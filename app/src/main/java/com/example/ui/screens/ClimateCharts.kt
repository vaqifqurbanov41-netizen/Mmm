package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.ClimateExtremeType
import com.example.domain.ClimateHistorySummary
import com.example.domain.ClimateHistoryUtils
import com.example.i18n.LocalStrings
import com.example.ui.components.AqroCard
import com.example.ui.components.formatNumber
import com.example.ui.theme.DangerRed
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.LocalAqroColors
import com.example.ui.theme.PhosphorusAmber
import com.example.ui.theme.PotassiumPlum
import com.example.ui.theme.WarningAmber
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun TemperatureAnalyticsCard(
    summary: ClimateHistorySummary,
    useYearly: Boolean,
) {
    val strings = LocalStrings.current
    val extra = LocalAqroColors.current
    val labels = if (useYearly && summary.yearlySummaries.size >= 2) {
        summary.yearlySummaries.map { it.year }
    } else {
        summary.monthlyChronological.map { it.yearMonth }
    }
    val maxSeries = if (useYearly && summary.yearlySummaries.size >= 2) {
        summary.yearlySummaries.map { it.maxTempC }
    } else {
        summary.monthlyChronological.map { it.avgTempMax }
    }
    val meanSeries = if (useYearly && summary.yearlySummaries.size >= 2) {
        summary.yearlySummaries.map { it.avgTempC }
    } else {
        summary.monthlyChronological.map { it.avgTempMean }
    }
    val minSeries = if (useYearly && summary.yearlySummaries.size >= 2) {
        summary.yearlySummaries.map { it.minTempC }
    } else {
        summary.monthlyChronological.map { it.avgTempMin }
    }

    AqroCard(modifier = Modifier.testTag("climate_chart_temperature")) {
        ClimateSectionHeader(
            icon = Icons.Filled.Thermostat,
            title = strings.climateSectionTemp,
            tint = Color(0xFFEA580C),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        ) {
            MetricMiniBox(
                label = strings.climateAvgTempLabel,
                value = "${formatNumber(summary.avgTempC, 1)}°C",
                sub = "${formatNumber(summary.minTempC, 1)}° / ${formatNumber(summary.maxTempC, 1)}°",
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            val deltaSign = if (summary.tempTrendDeltaC >= 0) "+" else ""
            MetricMiniBox(
                label = strings.climateTrendDeltaLabel,
                value = "$deltaSign${formatNumber(summary.tempTrendDeltaC, 2)}°C",
                sub = "${formatNumber(summary.avgTempMinC, 1)}° .. ${formatNumber(summary.avgTempMaxC, 1)}°",
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        if (meanSeries.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            ClimateLineChart(
                labels = labels,
                seriesList = listOf(
                    ChartSeries("Maks", maxSeries, Color(0xFFEF4444)),
                    ChartSeries("Orta", meanSeries, Color(0xFFF59E0B)),
                    ChartSeries("Min", minSeries, Color(0xFF3B82F6)),
                ),
                unit = "°C",
                referenceZero = true,
                mutedColor = extra.muted,
            )
        }
    }
}

@Composable
fun PrecipHumidityAnalyticsCard(
    summary: ClimateHistorySummary,
    useYearly: Boolean,
) {
    val strings = LocalStrings.current
    val extra = LocalAqroColors.current
    val labels = if (useYearly && summary.yearlySummaries.size >= 2) {
        summary.yearlySummaries.map { it.year }
    } else {
        summary.monthlyChronological.map { it.yearMonth }
    }
    val precipBars = if (useYearly && summary.yearlySummaries.size >= 2) {
        summary.yearlySummaries.map { it.totalPrecipMm }
    } else {
        summary.monthlyChronological.map { it.totalPrecipMm }
    }
    val humidityLine = if (useYearly && summary.yearlySummaries.size >= 2) {
        summary.yearlySummaries.map { it.avgHumidityPct }
    } else {
        summary.monthlyChronological.map { it.avgHumidityPct }
    }

    AqroCard(modifier = Modifier.testTag("climate_chart_precip_humidity")) {
        ClimateSectionHeader(
            icon = Icons.Filled.WaterDrop,
            title = strings.climateSectionPrecipHumidity,
            tint = Color(0xFF0284C7),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        ) {
            MetricMiniBox(
                label = strings.climateTotalPrecipLabel,
                value = "${summary.totalPrecipMm.roundToInt()} mm",
                sub = "${strings.climateRainSnowLabel}: ${summary.totalRainMm.roundToInt()}mm / ${formatNumber(summary.totalSnowCm, 1)}cm",
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            MetricMiniBox(
                label = strings.climateHumidityAvgLabel,
                value = "${summary.avgHumidityPct.roundToInt()}%",
                sub = "${strings.climateMaxDailyRainLabel}: ${formatNumber(summary.maxDailyPrecipMm, 1)} mm",
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        if (precipBars.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            BarPlusLineChart(
                labels = labels,
                bars = precipBars,
                barLabel = "Yağıntı (mm)",
                barColor = Color(0xFF0284C7),
                line = humidityLine,
                lineLabel = "Rütubət (%)",
                lineColor = Color(0xFF0D9488),
                lineFixedMax = 100.0,
                mutedColor = extra.muted,
            )
        }
    }
}

@Composable
fun WindRegimeAnalyticsCard(
    summary: ClimateHistorySummary,
    useYearly: Boolean,
) {
    val strings = LocalStrings.current
    val extra = LocalAqroColors.current
    val labels = if (useYearly && summary.yearlySummaries.size >= 2) {
        summary.yearlySummaries.map { it.year }
    } else {
        summary.monthlyChronological.map { it.yearMonth }
    }
    val windAvg = if (useYearly && summary.yearlySummaries.size >= 2) {
        summary.yearlySummaries.map { it.avgWindMaxKmh }
    } else {
        summary.monthlyChronological.map { it.avgWindMaxKmh }
    }
    val gustMax = if (useYearly && summary.yearlySummaries.size >= 2) {
        summary.yearlySummaries.map { it.maxGustKmh }
    } else {
        summary.monthlyChronological.map { it.maxGustKmh }
    }

    AqroCard(modifier = Modifier.testTag("climate_chart_wind")) {
        ClimateSectionHeader(
            icon = Icons.Filled.Air,
            title = strings.climateSectionWind,
            tint = Color(0xFF0F766E),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        ) {
            MetricMiniBox(
                label = strings.climateWindAvgGustLabel,
                value = "${formatNumber(summary.avgWindMaxKmh, 1)} / ${summary.maxGustKmh.roundToInt()} ${strings.weatherWindUnit}",
                sub = "Max: ${summary.maxWindKmh.roundToInt()} ${strings.weatherWindUnit}",
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = extra.pale,
                border = BorderStroke(1.dp, extra.line),
                modifier = Modifier.fillMaxHeight(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    WindCompassBadge(directionDeg = summary.dominantWindDeg)
                    Column {
                        Text(strings.climateWindDirLabel, style = MaterialTheme.typography.labelSmall, color = extra.muted)
                        Text(
                            text = "${ClimateHistoryUtils.windCompassShort(summary.dominantWindDeg)} (${summary.dominantWindDeg.roundToInt()}°)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
        if (windAvg.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            ClimateLineChart(
                labels = labels,
                seriesList = listOf(
                    ChartSeries("Qasırğa (${strings.weatherWindUnit})", gustMax, PotassiumPlum),
                    ChartSeries("Orta (${strings.weatherWindUnit})", windAvg, Color(0xFF0D9488)),
                ),
                unit = " ${strings.weatherWindUnit}",
                referenceZero = false,
                mutedColor = extra.muted,
            )
        }
    }
}

@Composable
fun SolarCloudAnalyticsCard(
    summary: ClimateHistorySummary,
    useYearly: Boolean,
) {
    val strings = LocalStrings.current
    val extra = LocalAqroColors.current
    val labels = if (useYearly && summary.yearlySummaries.size >= 2) {
        summary.yearlySummaries.map { it.year }
    } else {
        summary.monthlyChronological.map { it.yearMonth }
    }
    val radiation = if (useYearly && summary.yearlySummaries.size >= 2) {
        summary.yearlySummaries.map { it.avgRadiationMjM2 }
    } else {
        summary.monthlyChronological.map { it.avgRadiationMjM2 }
    }
    val cloudCover = if (useYearly && summary.yearlySummaries.size >= 2) {
        summary.yearlySummaries.map { it.avgCloudCoverPct }
    } else {
        summary.monthlyChronological.map { it.avgCloudCoverPct }
    }

    AqroCard(modifier = Modifier.testTag("climate_chart_solar_cloud")) {
        ClimateSectionHeader(
            icon = Icons.Filled.WbSunny,
            title = strings.climateSectionSolarCloud,
            tint = PhosphorusAmber,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        ) {
            MetricMiniBox(
                label = strings.climateSolarAvgLabel,
                value = "${formatNumber(summary.avgRadiationMjM2, 1)} MJ/m²",
                sub = "${summary.daysCount} d",
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            MetricMiniBox(
                label = strings.climateSunshineCloudLabel,
                value = "${summary.totalSunshineHours.roundToInt()} h / ${summary.avgCloudCoverPct.roundToInt()}%",
                sub = "Buludluq: ${summary.avgCloudCoverPct.roundToInt()}%",
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        if (radiation.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            BarPlusLineChart(
                labels = labels,
                bars = radiation,
                barLabel = "Radiasiya (MJ/m²)",
                barColor = PhosphorusAmber,
                line = cloudCover,
                lineLabel = "Buludluq (%)",
                lineColor = Color(0xFF64748B),
                lineFixedMax = 100.0,
                mutedColor = extra.muted,
            )
        }
    }
}

@Composable
fun PressureEt0AnalyticsCard(
    summary: ClimateHistorySummary,
    useYearly: Boolean,
) {
    val strings = LocalStrings.current
    val extra = LocalAqroColors.current
    val labels = if (useYearly && summary.yearlySummaries.size >= 2) {
        summary.yearlySummaries.map { it.year }
    } else {
        summary.monthlyChronological.map { it.yearMonth }
    }
    val et0Bars = if (useYearly && summary.yearlySummaries.size >= 2) {
        summary.yearlySummaries.map { it.totalEt0Mm }
    } else {
        summary.monthlyChronological.map { it.totalEt0Mm }
    }
    val pressureLine = if (useYearly && summary.yearlySummaries.size >= 2) {
        summary.yearlySummaries.map { it.avgPressureHpa }
    } else {
        summary.monthlyChronological.map { it.avgPressureHpa }
    }

    AqroCard(modifier = Modifier.testTag("climate_chart_pressure_et0")) {
        ClimateSectionHeader(
            icon = Icons.Filled.Compress,
            title = strings.climateSectionPressureEt0,
            tint = Color(0xFF6366F1),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        ) {
            MetricMiniBox(
                label = strings.climatePressureAvgLabel,
                value = "${formatNumber(summary.avgPressureHpa, 1)} hPa",
                sub = "${summary.minPressureHpa.roundToInt()}–${summary.maxPressureHpa.roundToInt()} hPa",
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            val wbSign = if (summary.waterBalanceMm >= 0) "+" else ""
            MetricMiniBox(
                label = strings.climateEt0Label,
                value = "${summary.totalEt0Mm.roundToInt()} mm",
                sub = "${strings.climateWaterBalanceLabel}: $wbSign${summary.waterBalanceMm.roundToInt()} mm",
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        if (et0Bars.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            BarPlusLineChart(
                labels = labels,
                bars = et0Bars,
                barLabel = "ET0 (mm)",
                barColor = EmeraldPrimary,
                line = pressureLine,
                lineLabel = "Təzyiq (hPa)",
                lineColor = Color(0xFF6366F1),
                lineFixedMax = null,
                mutedColor = extra.muted,
            )
        }
    }
}

@Composable
fun ExtremeWeatherAnalyticsCard(summary: ClimateHistorySummary) {
    val strings = LocalStrings.current
    val extra = LocalAqroColors.current

    val categories = listOf(
        Triple(strings.climateExtremeFrostLabel, summary.frostDays, Color(0xFF2563EB)),
        Triple(strings.climateExtremeHeatLabel, summary.hotDays, Color(0xFFDC2626)),
        Triple(strings.climateExtremeRainLabel, summary.heavyRainDays, Color(0xFF0284C7)),
        Triple(strings.climateExtremeWindLabel, summary.highWindDays, PotassiumPlum),
        Triple(strings.climateExtremeStormLabel, summary.stormHailDays, WarningAmber),
    )
    val maxCount = categories.maxOfOrNull { it.second }.coerceAtLeastOrOne()

    AqroCard(modifier = Modifier.testTag("climate_chart_extremes")) {
        ClimateSectionHeader(
            icon = Icons.Filled.Warning,
            title = strings.climateSectionExtremes,
            tint = DangerRed,
        )
        Spacer(modifier = Modifier.height(10.dp))

        // Horizontal Extreme Events Frequency Bar Chart
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            categories.forEach { (label, count, color) ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Text("$count d", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(extra.pale),
                    ) {
                        val fraction = (count.toFloat() / maxCount.toFloat()).coerceIn(0f, 1f)
                        if (fraction > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction.coerceAtLeast(0.03f))
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(color),
                            )
                        }
                    }
                }
            }
        }

        if (summary.extremeEvents.isEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(strings.climateNoExtremesLabel, style = MaterialTheme.typography.bodySmall, color = extra.muted)
        } else {
            Spacer(modifier = Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                summary.extremeEvents.take(6).forEach { ev ->
                    val (evLabel, evColor) = when (ev.type) {
                        ClimateExtremeType.STORM_HAIL -> strings.climateExtremeStormLabel to WarningAmber
                        ClimateExtremeType.HEAVY_RAIN -> strings.climateExtremeRainLabel to Color(0xFF0284C7)
                        ClimateExtremeType.SEVERE_WIND -> strings.climateExtremeWindLabel to PotassiumPlum
                        ClimateExtremeType.HEATWAVE -> strings.climateExtremeHeatLabel to DangerRed
                        ClimateExtremeType.FROST -> strings.climateExtremeFrostLabel to Color(0xFF2563EB)
                    }
                    val metricStr = when (ev.type) {
                        ClimateExtremeType.STORM_HAIL, ClimateExtremeType.HEAVY_RAIN -> "${formatNumber(ev.metricValue, 1)} mm"
                        ClimateExtremeType.SEVERE_WIND -> "${ev.metricValue.roundToInt()} ${strings.weatherWindUnit}"
                        ClimateExtremeType.HEATWAVE, ClimateExtremeType.FROST -> "${formatNumber(ev.metricValue, 1)}°C"
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = evColor.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, evColor.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        ) {
                            Text("${ev.date}  •  $evLabel", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                            Text(metricStr, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = evColor)
                        }
                    }
                }
            }
        }
    }
}

private fun Int?.coerceAtLeastOrOne(): Int = (this ?: 1).coerceAtLeast(1)

private data class ChartSeries(
    val name: String,
    val values: List<Double>,
    val color: Color,
)

@Composable
private fun ClimateLineChart(
    labels: List<String>,
    seriesList: List<ChartSeries>,
    unit: String,
    referenceZero: Boolean,
    mutedColor: Color,
) {
    val allVals = seriesList.flatMap { it.values }
    if (allVals.isEmpty()) return
    val rawMin = allVals.minOrNull() ?: 0.0
    val rawMax = allVals.maxOrNull() ?: 1.0
    val span = (rawMax - rawMin).coerceAtLeast(1.0)
    val minVal = rawMin - span * 0.08
    val maxVal = rawMax + span * 0.08
    val range = (maxVal - minVal).coerceAtLeast(1e-3)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            seriesList.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(s.color))
                    Text(s.name, style = MaterialTheme.typography.labelSmall, color = mutedColor)
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Text("${formatNumber(rawMin, 1)}..${formatNumber(rawMax, 1)}$unit", style = MaterialTheme.typography.labelSmall, color = mutedColor)
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(mutedColor.copy(alpha = 0.06f))
                .padding(horizontal = 8.dp, vertical = 10.dp),
        ) {
            val w = size.width
            val h = size.height
            val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
            // Horizontal gridlines
            for (g in 0..2) {
                val y = h * (g / 2f)
                drawLine(
                    color = mutedColor.copy(alpha = 0.22f),
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 1.5f,
                )
            }
            // Zero degree reference line
            if (referenceZero && 0.0 in minVal..maxVal) {
                val zy = (h - ((0.0 - minVal) / range) * h).toFloat()
                drawLine(
                    color = Color(0xFF3B82F6).copy(alpha = 0.65f),
                    start = Offset(0f, zy),
                    end = Offset(w, zy),
                    strokeWidth = 2f,
                    pathEffect = dash,
                )
            }

            seriesList.forEach { series ->
                if (series.values.size == 1) {
                    val y = (h - ((series.values.first() - minVal) / range) * h).toFloat()
                    drawCircle(color = series.color, radius = 6f, center = Offset(w / 2f, y))
                } else if (series.values.size >= 2) {
                    val path = Path()
                    val stepX = w / (series.values.size - 1).coerceAtLeast(1)
                    series.values.forEachIndexed { i, v ->
                        val x = i * stepX
                        val y = (h - ((v - minVal) / range) * h).toFloat().coerceIn(0f, h)
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(
                        path = path,
                        color = series.color,
                        style = Stroke(width = 3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }
            }
        }

        if (labels.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(labels.first(), style = MaterialTheme.typography.labelSmall, color = mutedColor)
                if (labels.size >= 3) {
                    Text(labels[labels.size / 2], style = MaterialTheme.typography.labelSmall, color = mutedColor)
                }
                Text(labels.last(), style = MaterialTheme.typography.labelSmall, color = mutedColor)
            }
        }
    }
}

@Composable
private fun BarPlusLineChart(
    labels: List<String>,
    bars: List<Double>,
    barLabel: String,
    barColor: Color,
    line: List<Double>,
    lineLabel: String,
    lineColor: Color,
    lineFixedMax: Double?,
    mutedColor: Color,
) {
    if (bars.isEmpty()) return
    val maxBar = (bars.maxOrNull() ?: 1.0).coerceAtLeast(1.0)
    val lMin = if (lineFixedMax != null) 0.0 else (line.minOrNull() ?: 0.0) - 2.0
    val lMax = lineFixedMax ?: ((line.maxOrNull() ?: 1.0) + 2.0)
    val lRange = (lMax - lMin).coerceAtLeast(1.0)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(barColor))
                Text(barLabel, style = MaterialTheme.typography.labelSmall, color = mutedColor)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(lineColor))
                Text(lineLabel, style = MaterialTheme.typography.labelSmall, color = mutedColor)
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(126.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(mutedColor.copy(alpha = 0.06f))
                .padding(horizontal = 8.dp, vertical = 10.dp),
        ) {
            val w = size.width
            val h = size.height
            val n = bars.size.coerceAtLeast(1)
            val slotW = w / n.toFloat()
            val barW = (slotW * 0.62f).coerceIn(2f, 22f)

            bars.forEachIndexed { idx, v ->
                val bh = ((v / maxBar) * h).toFloat().coerceIn(0f, h)
                val x = idx * slotW + (slotW - barW) / 2f
                drawRoundRect(
                    color = barColor.copy(alpha = 0.55f),
                    topLeft = Offset(x, h - bh),
                    size = Size(barW, bh),
                    cornerRadius = CornerRadius(3f, 3f),
                )
            }

            if (line.size >= 2) {
                val path = Path()
                line.forEachIndexed { idx, v ->
                    val x = idx * slotW + slotW / 2f
                    val y = (h - ((v - lMin) / lRange) * h).toFloat().coerceIn(0f, h)
                    if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(
                    path = path,
                    color = lineColor,
                    style = Stroke(width = 3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }

        if (labels.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(labels.first(), style = MaterialTheme.typography.labelSmall, color = mutedColor)
                if (labels.size >= 3) {
                    Text(labels[labels.size / 2], style = MaterialTheme.typography.labelSmall, color = mutedColor)
                }
                Text(labels.last(), style = MaterialTheme.typography.labelSmall, color = mutedColor)
            }
        }
    }
}

@Composable
private fun WindCompassBadge(directionDeg: Double) {
    val extra = LocalAqroColors.current
    Canvas(modifier = Modifier.size(34.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = size.minDimension / 2f - 2f
        drawCircle(color = extra.line, radius = r, style = Stroke(width = 2f))
        val rad = Math.toRadians(directionDeg - 90.0)
        val tipX = cx + (cos(rad) * (r * 0.78f)).toFloat()
        val tipY = cy + (sin(rad) * (r * 0.78f)).toFloat()
        drawLine(
            color = Color(0xFF0F766E),
            start = Offset(cx, cy),
            end = Offset(tipX, tipY),
            strokeWidth = 3.5f,
            cap = StrokeCap.Round,
        )
        drawCircle(color = Color(0xFF0F766E), radius = 3.5f, center = Offset(cx, cy))
    }
}

@Composable
private fun ClimateSectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    tint: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MetricMiniBox(
    label: String,
    value: String,
    sub: String,
    modifier: Modifier = Modifier,
) {
    val extra = LocalAqroColors.current
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = extra.pale,
        border = BorderStroke(1.dp, extra.line),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = extra.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = extra.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
