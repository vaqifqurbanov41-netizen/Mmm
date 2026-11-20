package com.example.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.daysUntilIsoDate
import com.example.data.model.EquipmentItem
import com.example.i18n.AppStrings
import com.example.domain.LocationSourceKind
import com.example.domain.WeatherHazardAlert
import com.example.domain.WeatherHazardKind
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

object EquipmentReminderManager {

    private const val CHANNEL_ID = "aqro_equipment_reminders_v2"
    private const val WEATHER_CHANNEL_ID = "aqro_weather_hazards_v1"
    private const val PREFS_NAME = "aqro_equipment_notif_prefs"
    private const val KEY_ALARM_CODES = "scheduled_alarm_codes"
    private const val KEY_NOTIFIED_PREFIX = "notified_"
    private const val KEY_WEATHER_NOTIFIED_PREFIX = "weather_notified_"
    private const val TEST_NOTIFICATION_ID = 991001

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun ensureNotificationChannel(
        context: Context,
        channelName: String,
        channelId: String = CHANNEL_ID,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val audioAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val channel = NotificationChannel(
            channelId,
            channelName,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            enableVibration(true)
            vibrationPattern = longArrayOf(0L, 250L, 120L, 250L)
            if (soundUri != null) {
                setSound(soundUri, audioAttrs)
            }
        }
        manager.createNotificationChannel(channel)
    }

    fun playNotificationChime(context: Context) {
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: return
            RingtoneManager.getRingtone(context.applicationContext, uri)?.play()
        }
    }

    @SuppressLint("MissingPermission")
    fun postNotification(
        context: Context,
        title: String,
        body: String,
        notificationId: Int,
        channelName: String,
        channelId: String = CHANNEL_ID,
    ) {
        ensureNotificationChannel(context, channelName, channelId)
        if (!hasNotificationPermission(context)) return

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .setVibrate(longArrayOf(0L, 250L, 120L, 250L))
        if (soundUri != null) {
            builder.setSound(soundUri)
        }

        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        }
    }

    fun sendTestReminderNotification(context: Context, strings: AppStrings) {
        ensureNotificationChannel(context, strings.eqNotifChannelName)
        postNotification(
            context = context,
            title = strings.eqNotifDueTitle,
            body = strings.eqTestNotifBody,
            notificationId = TEST_NOTIFICATION_ID,
            channelName = strings.eqNotifChannelName,
        )
        playNotificationChime(context)
    }

    fun sourceKindLabel(kind: LocationSourceKind, strings: AppStrings): String = when (kind) {
        LocationSourceKind.FIELD -> strings.notifSourceField
        LocationSourceKind.USER_GPS -> strings.notifSourceGps
        LocationSourceKind.REGISTERED_REGION -> strings.notifSourceRegion
    }

    fun weatherHazardSummary(
        alert: WeatherHazardAlert,
        strings: AppStrings,
    ): Pair<String, String> = when (alert.kind) {
        WeatherHazardKind.FROST -> strings.weatherHazardFrostTitle to strings.weatherHazardFrostAdvice
        WeatherHazardKind.HEAT_STRESS -> strings.weatherHazardHeatTitle to strings.weatherHazardHeatAdvice
        WeatherHazardKind.HEAVY_RAIN_LEACHING -> strings.weatherHazardRainTitle to strings.weatherHazardRainAdvice
        WeatherHazardKind.HIGH_WIND_SPRAY -> strings.weatherHazardWindTitle to strings.weatherHazardWindAdvice
    }

    fun syncWeatherHazards(
        context: Context,
        alerts: List<WeatherHazardAlert>,
        strings: AppStrings,
    ) {
        if (alerts.isEmpty()) return
        ensureNotificationChannel(context, strings.weatherHazardNotifChannel, WEATHER_CHANNEL_ID)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var playedChime = false

        alerts.forEach { alert ->
            val dedupKey = "${KEY_WEATHER_NOTIFIED_PREFIX}${alert.id}"
            if (prefs.getBoolean(dedupKey, false)) return@forEach

            val (eventTitle, advice) = weatherHazardSummary(alert, strings)
            val srcLabel = sourceKindLabel(alert.sourceKind, strings)
            val body = String.format(
                Locale.getDefault(),
                strings.weatherHazardNotifBodyFormat,
                alert.locationLabel,
                srcLabel,
                alert.date,
                alert.metricText,
                advice,
            )
            val notifId = 810_000 + (abs(alert.id.hashCode()) % 150_000)
            postNotification(
                context = context,
                title = "${strings.weatherHazardNotifTitle}: $eventTitle",
                body = body,
                notificationId = notifId,
                channelName = strings.weatherHazardNotifChannel,
                channelId = WEATHER_CHANNEL_ID,
            )
            if (!playedChime) {
                playNotificationChime(context)
                playedChime = true
            }
            prefs.edit().putBoolean(dedupKey, true).apply()
        }
    }

    fun syncEquipmentReminders(
        context: Context,
        items: List<EquipmentItem>,
        warningDays: Int,
        strings: AppStrings,
    ) {
        val leadDays = warningDays.coerceAtLeast(1)
        ensureNotificationChannel(context, strings.eqNotifChannelName)

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

        // Cancel previous alarms cleanly before re-registering active slots
        val prevCodes = prefs.getStringSet(KEY_ALARM_CODES, emptySet()).orEmpty()
        prevCodes.forEach { rawCode ->
            val code = rawCode.toIntOrNull() ?: return@forEach
            val cancelIntent = Intent(context, EquipmentReminderReceiver::class.java)
            val pi = PendingIntent.getBroadcast(
                context,
                code,
                cancelIntent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
            if (pi != null && alarmManager != null) {
                runCatching { alarmManager.cancel(pi) }
                pi.cancel()
            }
        }

        val activeCodes = mutableSetOf<String>()
        var playedChimeThisSync = false

        items.forEach { item ->
            val dates = listOf(
                Triple("inspection", strings.eqNotifInspectionLabel, item.inspectionDate),
                Triple("insurance", strings.eqNotifInsuranceLabel, item.insuranceDate),
            )
            dates.forEach { (kind, typeLabel, dateStr) ->
                if (dateStr.isBlank()) return@forEach
                val daysLeft = daysUntilIsoDate(dateStr) ?: return@forEach
                val body = if (daysLeft < 0) {
                    String.format(
                        Locale.getDefault(),
                        strings.eqNotifOverdueBodyFormat,
                        item.name,
                        typeLabel,
                        dateStr,
                    )
                } else {
                    String.format(
                        Locale.getDefault(),
                        strings.eqNotifSoonBodyFormat,
                        item.name,
                        typeLabel,
                        daysLeft,
                        dateStr,
                    )
                }
                val requestCode = stableRequestCode(item.id, kind)

                if (daysLeft <= leadDays) {
                    val dedupKey = "${KEY_NOTIFIED_PREFIX}${item.id}_${kind}_${dateStr}_$leadDays"
                    if (!prefs.getBoolean(dedupKey, false)) {
                        postNotification(
                            context = context,
                            title = strings.eqNotifDueTitle,
                            body = body,
                            notificationId = requestCode,
                            channelName = strings.eqNotifChannelName,
                        )
                        if (!playedChimeThisSync) {
                            playNotificationChime(context)
                            playedChimeThisSync = true
                        }
                        prefs.edit().putBoolean(dedupKey, true).apply()
                    }
                } else {
                    val triggerAtMillis = computeTriggerTimeMillis(dateStr, leadDays) ?: return@forEach
                    if (triggerAtMillis > System.currentTimeMillis() && alarmManager != null) {
                        val alarmBody = String.format(
                            Locale.getDefault(),
                            strings.eqNotifSoonBodyFormat,
                            item.name,
                            typeLabel,
                            leadDays.toLong(),
                            dateStr,
                        )
                        val intent = Intent(context, EquipmentReminderReceiver::class.java).apply {
                            putExtra(EquipmentReminderReceiver.EXTRA_TITLE, strings.eqNotifDueTitle)
                            putExtra(EquipmentReminderReceiver.EXTRA_BODY, alarmBody)
                            putExtra(EquipmentReminderReceiver.EXTRA_NOTIFICATION_ID, requestCode)
                            putExtra(EquipmentReminderReceiver.EXTRA_CHANNEL_NAME, strings.eqNotifChannelName)
                        }
                        val pi = PendingIntent.getBroadcast(
                            context,
                            requestCode,
                            intent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        )
                        runCatching {
                            alarmManager.setAndAllowWhileIdle(
                                AlarmManager.RTC_WAKEUP,
                                triggerAtMillis,
                                pi,
                            )
                        }
                        activeCodes.add(requestCode.toString())
                    }
                }
            }
        }

        prefs.edit().putStringSet(KEY_ALARM_CODES, activeCodes).apply()
    }

    fun computeTriggerTimeMillis(dateStr: String, leadDays: Int): Long? {
        return try {
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
            val parsed = fmt.parse(dateStr.trim()) ?: return null
            val cal = Calendar.getInstance().apply {
                time = parsed
                add(Calendar.DAY_OF_YEAR, -leadDays.coerceAtLeast(1))
                set(Calendar.HOUR_OF_DAY, 9)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            cal.timeInMillis
        } catch (_: Exception) {
            null
        }
    }

    private fun stableRequestCode(itemId: String, kind: String): Int {
        val rawHash = "$itemId:$kind".hashCode()
        return 10_000 + (abs(rawHash) % 800_000)
    }
}
