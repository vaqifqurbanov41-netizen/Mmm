package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.i18n.LocalStrings
import com.example.ui.components.AqroCard
import com.example.ui.components.DangerBannerBox
import com.example.ui.components.ScreenTopBar
import com.example.ui.components.formatNumber
import com.example.ui.theme.LocalAqroColors
import com.example.ui.viewmodel.WeatherUiState

@Composable
fun WeatherScreen(
    weatherState: WeatherUiState,
    onRefreshWeather: (Double, Double) -> Unit,
    onBack: () -> Unit,
) {
    val strings = LocalStrings.current
    val extra = LocalAqroColors.current
    val context = LocalContext.current

    var latText by remember(weatherState.lat) { mutableStateOf(formatNumber(weatherState.lat, 4)) }
    var lonText by remember(weatherState.lon) { mutableStateOf(formatNumber(weatherState.lon, 4)) }
    var gpsMsg by remember { mutableStateOf<String?>(null) }

    val locateAndFetch = {
        fetchSingleGpsFix(
            context = context,
            onSuccess = { lat, lon, _ ->
                latText = formatNumber(lat, 4)
                lonText = formatNumber(lon, 4)
                gpsMsg = null
                onRefreshWeather(lat, lon)
            },
            onFailure = {
                gpsMsg = strings.gpsUnavailable
            },
        )
    }

    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) locateAndFetch()
        else gpsMsg = strings.gpsPermissionDenied
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            ScreenTopBar(title = strings.weatherTitle, onBack = onBack)
        }

        item {
            AqroCard {
                Text(strings.weatherCoordsTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = latText,
                        onValueChange = { latText = it },
                        label = { Text(strings.latLabel) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = lonText,
                        onValueChange = { lonText = it },
                        label = { Text(strings.lonLabel) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_FINE_LOCATION,
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) locateAndFetch()
                            else permLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        },
                        modifier = Modifier.weight(1f).testTag("weather_gps_btn"),
                    ) {
                        Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(strings.btnGpsWeather, maxLines = 1)
                    }
                    Button(
                        onClick = {
                            val lat = latText.replace(",", ".").toDoubleOrNull() ?: weatherState.lat
                            val lon = lonText.replace(",", ".").toDoubleOrNull() ?: weatherState.lon
                            onRefreshWeather(lat, lon)
                        },
                        enabled = !weatherState.isLoading,
                        modifier = Modifier.weight(1f).testTag("weather_refresh_btn"),
                    ) {
                        if (weatherState.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(strings.btnRefreshWeather, maxLines = 1)
                        }
                    }
                }
                if (gpsMsg != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    DangerBannerBox(text = gpsMsg!!)
                }
            }
        }

        if (weatherState.error != null) {
            item {
                DangerBannerBox(text = "${strings.weatherError}\n(${weatherState.error})")
            }
        }

        val current = weatherState.response?.current
        if (current != null) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    WeatherStatTile(
                        icon = Icons.Filled.Thermostat,
                        label = strings.weatherCurrentTemp,
                        value = "${formatNumber(current.temperature2m, 1)} °C",
                        modifier = Modifier.weight(1f),
                    )
                    WeatherStatTile(
                        icon = Icons.Filled.WaterDrop,
                        label = strings.weatherHumidity,
                        value = "${current.humidity2m}%",
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    WeatherStatTile(
                        icon = Icons.Filled.WbSunny,
                        label = strings.weatherPrecip,
                        value = "${formatNumber(current.precipitation, 1)} mm",
                        modifier = Modifier.weight(1f),
                    )
                    WeatherStatTile(
                        icon = Icons.Filled.Air,
                        label = strings.weatherWind,
                        value = "${formatNumber(current.windSpeed10m, 1)} ${strings.weatherWindUnit}",
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item {
                val unsafeSpray = current.windSpeed10m > 18.0 || current.precipitation > 0.3 || weatherState.hasAnyHazard
                if (unsafeSpray) {
                    val reasons = buildList {
                        add(strings.weatherSprayAvoid)
                        if (weatherState.hasFrostRisk) add(strings.frostWarning)
                        if (weatherState.hasHighWind) add(strings.windWarning)
                        if (weatherState.hasHeavyRain) add(strings.rainWarning)
                    }.joinToString("\n• ")
                    DangerBannerBox(text = reasons)
                } else {
                    AqroCard(containerColor = extra.pale) {
                        Text(
                            text = strings.weatherSprayGood,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        } else if (!weatherState.isLoading && weatherState.error == null) {
            item {
                AqroCard {
                    Text(strings.weatherPromptLoad, color = extra.muted)
                }
            }
        }

        val daily = weatherState.response?.daily
        if (daily != null && daily.time.isNotEmpty()) {
            item {
                Text(
                    text = strings.weatherForecastTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            val count = minOf(5, daily.time.size)
            items(count) { idx ->
                val date = daily.time.getOrElse(idx) { "" }
                val tMin = daily.tempMin.getOrElse(idx) { 0.0 }
                val tMax = daily.tempMax.getOrElse(idx) { 0.0 }
                val rain = daily.precipitationSum.getOrElse(idx) { 0.0 }
                AqroCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(date, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            text = "${strings.weatherMinLabel}: ${formatNumber(tMin, 0)}°  |  ${strings.weatherMaxLabel}: ${formatNumber(tMax, 0)}°  |  ${strings.weatherRainLabel}: ${formatNumber(rain, 1)} mm",
                            style = MaterialTheme.typography.bodySmall,
                            color = extra.muted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherStatTile(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val extra = LocalAqroColors.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, extra.line),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = extra.muted)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
        }
    }
}
