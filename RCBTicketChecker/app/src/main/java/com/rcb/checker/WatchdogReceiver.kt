package com.rcb.checker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

// Watchdog — restarts the service if Android kills it
class WatchdogReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("RCBWatchdog", "Watchdog fired — restarting service")
        val serviceIntent = Intent(context, TicketCheckerService::class.java)
        context.startForegroundService(serviceIntent)
        // Schedule next watchdog
        scheduleWatchdog(context)
    }

    companion object {
        private const val WATCHDOG_INTERVAL_MS = 60_000L // Check every 60 seconds
        private const val REQUEST_CODE = 8888

        fun scheduleWatchdog(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, WatchdogReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + WATCHDOG_INTERVAL_MS,
                    pendingIntent
                )
            } catch (e: Exception) {
                // Fallback for restricted devices
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + WATCHDOG_INTERVAL_MS,
                    pendingIntent
                )
            }
            Log.d("RCBWatchdog", "Watchdog scheduled in ${WATCHDOG_INTERVAL_MS / 1000}s")
        }

        fun cancelWatchdog(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, WatchdogReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context, REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
    }
}
