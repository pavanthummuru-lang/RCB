package com.rcb.checker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import androidx.core.app.NotificationCompat

object NotificationHelper {

    const val SERVICE_NOTIFICATION_ID = 1001
    private const val ALERT_NOTIFICATION_ID = 1002
    private const val ALARM_NOTIFICATION_ID = 1003
    private const val REPORT_NOTIFICATION_ID = 1004

    private const val CHANNEL_SERVICE = "rcb_service"
    private const val CHANNEL_ALERT = "rcb_alert"
    private const val CHANNEL_ALARM = "rcb_alarm"
    private const val CHANNEL_REPORT = "rcb_report"

    private const val BUY_URL = "https://shop.royalchallengers.com/"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

        // Service channel (silent, persistent)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SERVICE, "Checker Service", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Background service notification"
                setShowBadge(false)
            }
        )

        // Alert channel (high importance, sound+vibrate)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERT, "Ticket Alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Ticket change alerts"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
                enableLights(true)
                lightColor = android.graphics.Color.RED
                setBypassDnd(true)
            }
        )

        // Alarm channel (MAX importance, bypass DND, alarm sound)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALARM, "URGENT Ticket Alarm", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Urgent repeating alarm for new tickets"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 300, 1000, 300, 1000)
                enableLights(true)
                lightColor = android.graphics.Color.RED
                setBypassDnd(true)
                setSound(
                    alarmSound,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
        )

        // Report channel (default, no sound)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_REPORT, "Status Reports", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "30-minute status reports"
            }
        )
    }

    private fun buyIntent(context: Context): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(BUY_URL))
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun buildServiceNotification(context: Context, title: String, content: String): Notification {
        return NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(content)
            .setOngoing(true)
            .setContentIntent(openAppIntent(context))
            .build()
    }

    fun updateServiceNotification(context: Context, title: String, content: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(SERVICE_NOTIFICATION_ID, buildServiceNotification(context, title, content))
    }

    fun showStartNotification(context: Context, events: List<ApiHelper.Event>) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val content = "Monitoring ${events.size} match(es). Will alert on any change."
        nm.notify(
            REPORT_NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_REPORT)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("RCB Checker Started")
                .setContentText(content)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(events.joinToString("\n") { "• ${it.name} | ${it.status}" })
                )
                .setContentIntent(openAppIntent(context))
                .setAutoCancel(true)
                .build()
        )
    }

    fun showAlertNotification(context: Context, title: String, message: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(
            ALERT_NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_ALERT)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
                .addAction(android.R.drawable.ic_input_add, "BUY TICKETS", buyIntent(context))
                .setContentIntent(buyIntent(context))
                .setAutoCancel(false)
                .build()
        )
    }

    fun showAlarmNotification(context: Context, title: String, message: String, count: Int) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(
            ALARM_NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_ALARM)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("[$count] $title")
                .setContentText("TAP TO BUY TICKETS NOW!")
                .setStyle(NotificationCompat.BigTextStyle().bigText("$message\n\nTAP TO BUY TICKETS NOW!"))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setVibrate(longArrayOf(0, 1000, 300, 1000, 300, 1000))
                .addAction(android.R.drawable.ic_input_add, "BUY NOW", buyIntent(context))
                .setContentIntent(buyIntent(context))
                .setAutoCancel(false)
                .setOnlyAlertOnce(false) // alert EVERY time
                .build()
        )
    }

    fun showReportNotification(context: Context, events: List<ApiHelper.Event>) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(
            REPORT_NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_REPORT)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("RCB Ticket Report")
                .setContentText("${events.size} match(es) — Checker running fine")
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(events.joinToString("\n") { "• ${it.name}\n  ${it.date} | ${it.status}" })
                )
                .setContentIntent(openAppIntent(context))
                .setAutoCancel(true)
                .build()
        )
    }
}
