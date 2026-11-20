package com.example.ui.components

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.i18n.LocalStrings
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

fun hasAnyLocationPermission(context: Context): Boolean {
    val fine = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    return fine || coarse
}

fun isDeviceLocationEnabled(context: Context): Boolean {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        lm.isLocationEnabled
    } else {
        @Suppress("DEPRECATION")
        lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }
}

@Composable
fun rememberGpsLocateAction(
    onFixSuccess: (Double, Double, Float) -> Unit,
    onStatusMessage: (String?) -> Unit,
    onLoadingChange: (Boolean) -> Unit = {},
): () -> Unit {
    val context = LocalContext.current
    val strings = LocalStrings.current
    val currentOnSuccess by rememberUpdatedState(onFixSuccess)
    val currentOnStatus by rememberUpdatedState(onStatusMessage)
    val currentOnLoading by rememberUpdatedState(onLoadingChange)
    var awaitingSettingsReturn by remember { mutableStateOf(false) }

    val startFixFetch: () -> Unit = {
        currentOnLoading(true)
        currentOnStatus(null)
        fetchRobustGpsFix(
            context = context,
            onSuccess = { lat, lon, acc ->
                currentOnLoading(false)
                currentOnStatus(null)
                currentOnSuccess(lat, lon, acc)
            },
            onFailure = {
                currentOnLoading(false)
                currentOnStatus(
                    if (!isDeviceLocationEnabled(context)) strings.gpsTurnOnRequired
                    else strings.gpsUnavailable
                )
            },
        )
    }

    val deviceLocationSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        awaitingSettingsReturn = false
        if (hasAnyLocationPermission(context) && isDeviceLocationEnabled(context)) {
            startFixFetch()
        } else {
            currentOnLoading(false)
            currentOnStatus(strings.gpsTurnOnRequired)
        }
    }

    val gpsResolutionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK || isDeviceLocationEnabled(context)) {
            startFixFetch()
        } else {
            currentOnLoading(false)
            currentOnStatus(strings.gpsTurnOnRequired)
        }
    }

    val ensureGpsSettingsAndLocate: () -> Unit = {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000L)
            .setMinUpdateIntervalMillis(1_500L)
            .build()
        val settingsReq = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
            .setAlwaysShow(true)
            .build()

        currentOnStatus(strings.gpsTurningOn)
        LocationServices.getSettingsClient(context)
            .checkLocationSettings(settingsReq)
            .addOnSuccessListener {
                if (isDeviceLocationEnabled(context)) {
                    startFixFetch()
                } else {
                    awaitingSettingsReturn = true
                    runCatching {
                        deviceLocationSettingsLauncher.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    }.onFailure {
                        currentOnStatus(strings.gpsTurnOnRequired)
                    }
                }
            }
            .addOnFailureListener { ex ->
                if (ex is ResolvableApiException) {
                    runCatching {
                        val req = IntentSenderRequest.Builder(ex.resolution).build()
                        gpsResolutionLauncher.launch(req)
                    }.onFailure {
                        runCatching {
                            deviceLocationSettingsLauncher.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                        }.onFailure {
                            currentOnStatus(strings.gpsTurnOnRequired)
                        }
                    }
                } else if (!isDeviceLocationEnabled(context)) {
                    runCatching {
                        deviceLocationSettingsLauncher.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    }.onFailure {
                        currentOnStatus(strings.gpsTurnOnRequired)
                    }
                } else {
                    // Location provider is enabled even though Play Services checkSettings errored; fetch directly.
                    startFixFetch()
                }
            }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            hasAnyLocationPermission(context)
        if (granted) {
            ensureGpsSettingsAndLocate()
        } else {
            currentOnLoading(false)
            currentOnStatus(strings.gpsPermissionDenied)
        }
    }

    return remember(context) {
        {
            if (hasAnyLocationPermission(context)) {
                ensureGpsSettingsAndLocate()
            } else {
                currentOnStatus(strings.gpsPermissionNeeded)
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    )
                )
            }
        }
    }
}

@SuppressLint("MissingPermission")
fun fetchRobustGpsFix(
    context: Context,
    onSuccess: (Double, Double, Float) -> Unit,
    onFailure: () -> Unit,
) {
    if (!hasAnyLocationPermission(context)) {
        onFailure()
        return
    }
    val client = LocationServices.getFusedLocationProviderClient(context)
    val cts = CancellationTokenSource()
    var delivered = false
    fun completeSuccess(loc: Location) {
        if (delivered) return
        delivered = true
        onSuccess(loc.latitude, loc.longitude, if (loc.hasAccuracy()) loc.accuracy else 25f)
    }

    fun tryPlatformLocationManager() {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (lm == null) {
            if (!delivered) {
                delivered = true
                onFailure()
            }
            return
        }
        val bestLast = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        ).mapNotNull { provider ->
            runCatching { lm.getLastKnownLocation(provider) }.getOrNull()
        }.maxByOrNull { it.time }

        if (bestLast != null) {
            completeSuccess(bestLast)
            return
        }

        val activeProvider = when {
            runCatching { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false) ->
                LocationManager.GPS_PROVIDER
            runCatching { lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false) ->
                LocationManager.NETWORK_PROVIDER
            else -> null
        }

        if (activeProvider == null) {
            if (!delivered) {
                delivered = true
                onFailure()
            }
            return
        }

        val handler = Handler(Looper.getMainLooper())
        lateinit var listener: LocationListener
        val timeoutRunnable = Runnable {
            runCatching { lm.removeUpdates(listener) }
            if (!delivered) {
                delivered = true
                onFailure()
            }
        }
        listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                handler.removeCallbacks(timeoutRunnable)
                runCatching { lm.removeUpdates(this) }
                completeSuccess(location)
            }

            @Deprecated("Deprecated in Android API")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }

        val registered = runCatching {
            lm.requestLocationUpdates(activeProvider, 0L, 0f, listener, Looper.getMainLooper())
            true
        }.getOrDefault(false)

        if (registered) {
            handler.postDelayed(timeoutRunnable, 6_500L)
        } else if (!delivered) {
            delivered = true
            onFailure()
        }
    }

    client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
        .addOnSuccessListener { highLoc ->
            if (highLoc != null) {
                completeSuccess(highLoc)
            } else {
                client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, CancellationTokenSource().token)
                    .addOnSuccessListener { balLoc ->
                        if (balLoc != null) {
                            completeSuccess(balLoc)
                        } else {
                            client.lastLocation
                                .addOnSuccessListener { last ->
                                    if (last != null) completeSuccess(last)
                                    else tryPlatformLocationManager()
                                }
                                .addOnFailureListener { tryPlatformLocationManager() }
                        }
                    }
                    .addOnFailureListener { tryPlatformLocationManager() }
            }
        }
        .addOnFailureListener {
            client.lastLocation
                .addOnSuccessListener { last ->
                    if (last != null) completeSuccess(last)
                    else tryPlatformLocationManager()
                }
                .addOnFailureListener { tryPlatformLocationManager() }
        }
}
