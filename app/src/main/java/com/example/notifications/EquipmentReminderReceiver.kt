package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.i18n.StringsAz

class EquipmentReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(EXTRA_TITLE) ?: StringsAz.eqNotifDueTitle
        val body = intent.getStringExtra(EXTRA_BODY).orEmpty()
        if (body.isBlank()) return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 2001)
        val channelName = intent.getStringExtra(EXTRA_CHANNEL_NAME) ?: StringsAz.eqNotifChannelName

        EquipmentReminderManager.ensureNotificationChannel(context, channelName)
        EquipmentReminderManager.postNotification(
            context = context,
            title = title,
            body = body,
            notificationId = notificationId,
            channelName = channelName,
        )
        EquipmentReminderManager.playNotificationChime(context)
    }

    companion object {
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_BODY = "extra_body"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val EXTRA_CHANNEL_NAME = "extra_channel_name"
    }
}
