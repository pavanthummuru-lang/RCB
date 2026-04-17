package com.rcb.checker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

// Auto-restarts checker after phone reboot
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            val serviceIntent = Intent(context, TicketCheckerService::class.java)
            context.startForegroundService(serviceIntent)
        }
    }
}
