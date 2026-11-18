package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.data.model.FarmField
import com.example.domain.ClimateHistorySummary
import com.example.domain.ClimateHistoryUtils
import com.example.i18n.LocalStrings
import com.example.ui.components.AqroCard
import com.example.ui.components.DangerBannerBox
import com.example.ui.components.formatNumber
import com.example.ui.theme.LocalAqroColors
import com.example.ui.viewmodel.ClimateHistoryUiState
import java.io.File
import java.nio.charset.StandardCharsets
import kotlin.math.roundToInt

fun LazyListScope.climateHistorySectionItems(
    fields: List<FarmField>,
    climateState: ClimateHistoryUiState,
    defaultLat: Double,
    defaultLon: Double,
    selectedField: FarmField?,
    onSelectField: (FarmField?) -> Unit,
    useYearlyAggregation: Boolean,
    onToggleYearly: (Boolean) -> Unit,
    onLoadClimateHistory: (Double, Double, String, String, String) -> Unit,
    monthlyTitle: String,
    windUnit: String,
) {
    item {
        ClimateHistoryControlsCard(
            fields = fields,
            climateState = climateState,
            defaultLat = defaultLat,
            defaultLon = defaultLon,
            onSelectField = onSelectField,
            onLoadClimateHistory = onLoadClimateHistory,
        )
    }

    val summary = climateState.summary
    if (summary == null && !climateState.isLoading) {
        item {
            AqroCard {
                Text(LocalStrings.current.climateEmptyPrompt, style = MaterialTheme.typography.bodySmall, color = LocalAqroColors.current.muted)
            }
        }
    } else if (summary != null && summary.daysCount > 0) {
        item {
            ClimateSummaryOverviewCard(
                climateState = climateState,
                summary = summary,
            )
        }
        item {
            ClimateTimeMapCard(
                summary = summary,
                centerLat = climateState.lat,
                centerLon = climateState.lon,
                selectedField = selectedField ?: fields.firstOrNull { it.name == climateState.locationLabel },
                useYearly = useYearlyAggregation,
                onToggleYearly = onToggleYearly,
            )
        }
        item {
            TemperatureAnalyticsCard(
                summary = summary,
                useYearly = useYearlyAggregation,
            )
        }
        item {
            PrecipHumidityAnalyticsCard(
                summary = summary,
                useYearly = useYearlyAggregation,
            )
        }
        item {
            WindRegimeAnalyticsCard(
                summary = summary,
                useYearly = useYearlyAggregation,
            )
        }
        item {
            SolarCloudAnalyticsCard(
                summary = summary,
                useYearly = useYearlyAggregation,
            )
        }
        item {
            PressureEt0AnalyticsCard(
                summary = summary,
                useYearly = useYearlyAggregation,
            )
        }
        item {
            ExtremeWeatherAnalyticsCard(summary = summary)
        }
        climateMonthlyItems(
            climateState = climateState,
            title = monthlyTitle,
            windUnit = windUnit,
        )
    }
}

@Composable
fun ClimateHistoryControlsCard(
    fields: List<FarmField>,
    climateState: ClimateHistoryUiState,
    defaultLat: Double,
    defaultLon: Double,
    onSelectField: (FarmField?) -> Unit = {},
    onLoadClimateHistory: (Double, Double, String, String, String) -> Unit,
) {
    val strings = LocalStrings.current
    val extra = LocalAqroColors.current
    val context = LocalContext.current

    var selectedFieldId by remember { mutableStateOf<String?>(null) }
    var selectedRegionId by remember { mutableStateOf("ganja") }
    var locationLabel by remember(climateState.locationLabel) { mutableStateOf(climateState.locationLabel) }
    var latText by remember(climateState.lat) { mutableStateOf(formatNumber(climateState.lat, 4)) }
    var lonText by remember(climateState.lon) { mutableStateOf(formatNumber(climateState.lon, 4)) }

    val initialRange = remember { ClimateHistoryUtils.defaultDateRange(24) }
    var startDate by remember { mutableStateOf(initialRange.first) }
    var endDate by remember { mutableStateOf(initialRange.second) }
    var selectedPresetMonths by remember { mutableIntStateOf(24) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val gpsLocate = {
        fetchSingleGpsFix(
            context = context,
            onSuccess = { lat, lon, _ ->
                latText = formatNumber(lat, 4)
                lonText = formatNumber(lon, 4)
                selectedFieldId = null
                onSelectField(null)
                selectedRegionId = ""
                locationLabel = "GPS (${formatNumber(lat, 2)}, ${formatNumber(lon, 2)})"
                validationError = null
            },
            onFailure = { validationError = strings.gpsUnavailable },
        )
    }

    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) gpsLocate() else validationError = strings.gpsPermissionDenied
    }

    AqroCard {
        Text(strings.climateLocationTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        if (fields.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(strings.climateMyFieldsLabel, style = MaterialTheme.typography.labelMedium, color = extra.muted)
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                fields.forEach { field ->
                    val isSel = selectedFieldId == field.id
                    FilterChip(
                        selected = isSel,
                        onClick = {
                            selectedFieldId = field.id
                            onSelectField(field)
                            selectedRegionId = ""
                            val center = ClimateHistoryUtils.fieldCentroidOrMatch(field, defaultLat, defaultLon)
                            latText = formatNumber(center.lat, 4)
                            lonText = formatNumber(center.lon, 4)
                            locationLabel = field.name
                            validationError = null
                        },
                        label = { Text(field.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.testTag("climate_field_chip_${field.id}"),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(strings.climateRegionsLabel, style = MaterialTheme.typography.labelMedium, color = extra.muted)
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            ClimateHistoryUtils.regionPresets.forEach { preset ->
                FilterChip(
                    selected = selectedFieldId == null && selectedRegionId == preset.id,
                    onClick = {
                        selectedFieldId = null
                        onSelectField(null)
                        selectedRegionId = preset.id
                        latText = formatNumber(preset.lat, 4)
                        lonText = formatNumber(preset.lon, 4)
                        locationLabel = preset.nameAz
                        validationError = null
                    },
                    label = { Text(preset.nameAz, maxLines = 1) },
                    modifier = Modifier.testTag("climate_region_chip_${preset.id}"),
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        ) {
            OutlinedTextField(
                value = latText,
                onValueChange = { latText = it; validationError = null },
                label = { Text(strings.latLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            OutlinedTextField(
                value = lonText,
                onValueChange = { lonText = it; validationError = null },
                label = { Text(strings.lonLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text(strings.climateRangeLabel, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        val presets = listOf(
            3 to strings.climatePreset3m,
            6 to strings.climatePreset6m,
            12 to strings.climatePreset1y,
            24 to strings.climatePreset2y,
            60 to strings.climatePreset5y,
            120 to strings.climatePreset10y,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            presets.forEach { (months, label) ->
                FilterChip(
                    selected = selectedPresetMonths == months,
                    onClick = {
                        selectedPresetMonths = months
                        val range = ClimateHistoryUtils.defaultDateRange(months)
                        startDate = range.first
                        endDate = range.second
                        validationError = null
                    },
                    label = { Text(label, maxLines = 1) },
                    modifier = Modifier.testTag("climate_preset_${months}m"),
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        ) {
            OutlinedTextField(
                value = startDate,
                onValueChange = { startDate = it; selectedPresetMonths = 0; validationError = null },
                label = { Text(strings.climateStartDateLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                singleLine = true,
                modifier = Modifier.weight(1f).fillMaxHeight().testTag("climate_start_date_input"),
            )
            OutlinedTextField(
                value = endDate,
                onValueChange = { endDate = it; selectedPresetMonths = 0; validationError = null },
                label = { Text(strings.climateEndDateLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                singleLine = true,
                modifier = Modifier.weight(1f).fillMaxHeight().testTag("climate_end_date_input"),
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        ) {
            OutlinedButton(
                onClick = {
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) gpsLocate() else permLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                },
                modifier = Modifier.weight(0.8f).fillMaxHeight().heightIn(min = 48.dp),
            ) {
                Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.size(4.dp))
                Text("GPS", maxLines = 1)
            }
            Button(
                onClick = {
                    val lat = latText.replace(",", ".").trim().toDoubleOrNull()
                    val lon = lonText.replace(",", ".").trim().toDoubleOrNull()
                    val validRange = ClimateHistoryUtils.validateDateRange(startDate, endDate)
                    if (lat == null || lon == null || kotlin.math.abs(lat) > 90.0 || kotlin.math.abs(lon) > 180.0) {
                        validationError = strings.invalidInput
                    } else if (validRange == null) {
                        validationError = strings.climateInvalidRangeError
                    } else {
                        validationError = null
                        startDate = validRange.first
                        endDate = validRange.second
                        onLoadClimateHistory(lat, lon, validRange.first, validRange.second, locationLabel)
                    }
                },
                enabled = !climateState.isLoading,
                modifier = Modifier.weight(1.4f).fillMaxHeight().heightIn(min = 48.dp).testTag("climate_load_btn"),
            ) {
                if (climateState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(strings.climateLoadBtn, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        if (validationError != null) {
            Spacer(modifier = Modifier.height(8.dp))
            DangerBannerBox(text = validationError!!)
        }
        if (climateState.error != null) {
            Spacer(modifier = Modifier.height(8.dp))
            DangerBannerBox(text = "${strings.weatherError}\n(${climateState.error})")
        }
    }
}

@Composable
private fun ClimateSummaryOverviewCard(
    climateState: ClimateHistoryUiState,
    summary: ClimateHistorySummary,
) {
    val strings = LocalStrings.current
    val extra = LocalAqroColors.current
    val context = LocalContext.current
    var statusMsg by remember { mutableStateOf<String?>(null) }

    val saveCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                val csv = ClimateHistoryUtils.buildClimateCsv(
                    locationLabel = climateState.locationLabel,
                    lat = climateState.lat,
                    lon = climateState.lon,
                    summary = summary,
                )
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(csv.toByteArray(StandardCharsets.UTF_8))
                }
                statusMsg = strings.climateSavedCsvSuccess
            }
        }
    }

    AqroCard(containerColor = extra.pale) {
        Text(
            text = "${climateState.locationLabel} (${summary.startDate} — ${summary.endDate}, ${summary.daysCount} gün)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        ) {
            ClimateStatTile(
                icon = Icons.Filled.Thermostat,
                label = strings.climateAvgTempLabel,
                value = "${formatNumber(summary.avgTempC, 1)} °C",
                sub = "${formatNumber(summary.minTempC, 1)}° / ${formatNumber(summary.maxTempC, 1)}°",
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            ClimateStatTile(
                icon = Icons.Filled.WaterDrop,
                label = strings.climateTotalPrecipLabel,
                value = "${summary.totalPrecipMm.roundToInt()} mm",
                sub = "${summary.rainyDays} d • RH ${summary.avgHumidityPct.roundToInt()}%",
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        ) {
            ClimateStatTile(
                icon = Icons.Filled.WbSunny,
                label = strings.climateEt0Label,
                value = "${summary.totalEt0Mm.roundToInt()} mm",
                sub = "${if (summary.waterBalanceMm >= 0) "+" else ""}${summary.waterBalanceMm.roundToInt()} mm",
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            ClimateStatTile(
                icon = Icons.Filled.Warning,
                label = strings.climateExtremeDaysLabel,
                value = "${summary.frostDays} / ${summary.hotDays}",
                sub = "max ${summary.maxGustKmh.roundToInt()} ${strings.weatherWindUnit}",
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        ) {
            Button(
                onClick = {
                    val safeName = climateState.locationLabel.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                        .trim('_').ifBlank { "climate" }
                    saveCsvLauncher.launch("aqro_${safeName}_${summary.startDate}_${summary.endDate}.csv")
                },
                modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp).testTag("climate_download_csv_btn"),
            ) {
                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.size(4.dp))
                Text(strings.climateDownloadCsvBtn, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton(
                onClick = {
                    runCatching {
                        val csv = ClimateHistoryUtils.buildClimateCsv(
                            locationLabel = climateState.locationLabel,
                            lat = climateState.lat,
                            lon = climateState.lon,
                            summary = summary,
                        )
                        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
                        val file = File(dir, "climate_${summary.startDate}_${summary.endDate}.csv")
                        file.writeText(csv, StandardCharsets.UTF_8)
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, strings.climateShareCsvBtn))
                    }
                },
                modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp).testTag("climate_share_csv_btn"),
            ) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.size(4.dp))
                Text(strings.climateShareCsvBtn, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        if (statusMsg != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(statusMsg!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}

fun LazyListScope.climateMonthlyItems(
    climateState: ClimateHistoryUiState,
    title: String,
    windUnit: String,
) {
    val summary = climateState.summary ?: return
    if (summary.monthlyBreakdown.isEmpty()) return
    item {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
    items(summary.monthlyBreakdown.take(24), key = { "climate_month_${it.yearMonth}" }) { m ->
        AqroCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(m.yearMonth, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    text = "${formatNumber(m.avgTempMean, 1)}°C (${formatNumber(m.minTemp, 0)}°..${formatNumber(m.maxTemp, 0)}°)",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${m.totalPrecipMm.roundToInt()} mm • RH ${m.avgHumidityPct.roundToInt()}% • ${formatNumber(m.avgWindMaxKmh, 0)} $windUnit • ${formatNumber(m.avgPressureHpa, 0)} hPa • ET0: ${m.totalEt0Mm.roundToInt()} mm",
                style = MaterialTheme.typography.labelSmall,
                color = LocalAqroColors.current.muted,
            )
        }
    }
}

@Composable
private fun ClimateStatTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    sub: String,
    modifier: Modifier = Modifier,
) {
    val extra = LocalAqroColors.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, extra.line),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, color = extra.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = extra.muted, maxLines = 1)
        }
    }
}
