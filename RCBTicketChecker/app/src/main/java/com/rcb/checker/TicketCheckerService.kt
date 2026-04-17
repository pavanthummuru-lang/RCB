package com.rcb.checker

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.*
import java.util.Calendar
import java.util.TimeZone

class TicketCheckerService : Service() {

    companion object {
        var isRunning = false
        private var alarmJob: Job? = null
        const val TAG = "RCBChecker"
        const val ACTION_STOP_ALARM = "com.rcb.checker.STOP_ALARM"
        const val ACTION_STOP_SERVICE = "com.rcb.checker.STOP_SERVICE"

        fun stopAlarm() {
            alarmJob?.cancel()
            alarmJob = null
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var mainJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true

        // Acquire wake lock — prevents CPU from sleeping
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RCBChecker::WakeLock")
        wakeLock?.acquire(24 * 60 * 60 * 1000L) // 24 hours
        Log.d(TAG, "Service created, wake lock acquired")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        when (intent?.action) {
            ACTION_STOP_ALARM -> {
                stopAlarm()
                return START_STICKY
            }
            ACTION_STOP_SERVICE -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }

        NotificationHelper.createChannels(this)

        // Start foreground immediately — prevents Android from killing us
        startForeground(
            NotificationHelper.SERVICE_NOTIFICATION_ID,
            NotificationHelper.buildServiceNotification(
                this,
                "RCB Checker Active",
                "Monitoring tickets — tap to open"
            )
        )

        if (mainJob?.isActive != true) {
            startMainLoop()
        }

        // Schedule watchdog alarm — restarts service if Android kills it
        WatchdogReceiver.scheduleWatchdog(this)

        return START_STICKY // Android restarts this service automatically if killed
    }

    private fun startMainLoop() {
        mainJob = serviceScope.launch {
            val config = ConfigManager(applicationContext)
            val state = StateManager(applicationContext)
            var errorCount = 0

            Log.d(TAG, "Main loop started")

            while (isActive) {

                // Check stop date
                val stopCal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata")).apply {
                    set(2026, Calendar.APRIL, 25, 0, 0, 0)
                }
                if (System.currentTimeMillis() >= stopCal.timeInMillis) {
                    NotificationHelper.showAlertNotification(
                        applicationContext,
                        "RCB Checker Stopped",
                        "Apr 24 has passed. Checker auto-stopped."
                    )
                    stopSelf()
                    break
                }

                try {
                    val apiUrl = config.apiUrl
                    val result = ApiHelper.fetch(apiUrl)

                    if (result != null) {
                        val newHash = StateManager.hash(result.rawBody)
                        val oldHash = state.lastHash

                        Log.d(TAG, "Fetched ${result.events.size} events")

                        if (oldHash == null) {
                            state.save(newHash, result.events)
                            state.lastReportTime = System.currentTimeMillis()
                            NotificationHelper.showStartNotification(applicationContext, result.events)
                            NotificationHelper.updateServiceNotification(
                                applicationContext,
                                "RCB Checker Active",
                                "${result.events.size} match(es) monitored — running in background"
                            )
                        } else if (oldHash != newHash) {
                            handleChange(state.lastEvents, result.events)
                            state.save(newHash, result.events)
                        }

                        // 30-min report
                        val reportInterval = config.reportIntervalMs
                        if (System.currentTimeMillis() - state.lastReportTime >= reportInterval) {
                            NotificationHelper.showReportNotification(applicationContext, result.events)
                            state.lastReportTime = System.currentTimeMillis()
                        }

                        errorCount = 0
                    } else {
                        throw Exception("API returned null")
                    }

                } catch (e: CancellationException) {
                    break
                } catch (e: Exception) {
                    errorCount++
                    Log.e(TAG, "Error #$errorCount: ${e.message}")
                    val retryWait = minOf(60_000L * errorCount, 300_000L)
                    delay(retryWait)
                    continue
                }

                val interval = config.checkIntervalMs
                Log.d(TAG, "Next check in ${interval / 1000}s")
                delay(interval)
            }
        }
    }

    private fun handleChange(oldEvents: List<ApiHelper.Event>, newEvents: List<ApiHelper.Event>) {
        val oldNames = oldEvents.map { it.name }
        val added = newEvents.filter { it.name !in oldNames }
        val changes = newEvents.mapNotNull { ne ->
            oldEvents.find { oe -> oe.name == ne.name && oe.status != ne.status }
                ?.let { oe -> Triple(ne, oe.status, ne.status) }
        }

        when {
            added.isNotEmpty() -> {
                val isGT = added.any { "Gujarat" in it.name }
                val title = if (isGT) "RCB vs GT LISTED! BUY NOW!" else "New RCB Match Added!"
                val msg = added.joinToString("\n\n") {
                    "${it.name}\nDate: ${it.date}\nStatus: ${it.status}\nPrice: ${it.price}"
                }
                NotificationHelper.showAlertNotification(applicationContext, title, msg)
                startAlarmLoop(title, msg)
            }
            changes.isNotEmpty() -> {
                val title = "RCB Ticket Status Changed!"
                val msg = changes.joinToString("\n\n") { (ne, old, new) ->
                    "${ne.name}\n$old  →  $new\nDate: ${ne.date}"
                }
                NotificationHelper.showAlertNotification(applicationContext, title, msg)
                val ticketsOpened = changes.any { (_, _, newStatus) -> newStatus != "SOLD OUT" }
                if (ticketsOpened) startAlarmLoop(title, msg)
            }
            else -> {
                val msg = newEvents.joinToString("\n") { "• ${it.name} | ${it.status}" }
                NotificationHelper.showAlertNotification(applicationContext, "RCB Tickets Changed!", msg)
            }
        }
    }

    private fun startAlarmLoop(title: String, message: String) {
        alarmJob?.cancel()
        alarmJob = serviceScope.launch {
            var count = 0
            while (isActive) {
                count++
                NotificationHelper.showAlarmNotification(applicationContext, title, message, count)
                delay(5000L)
            }
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // When user swipes app away — reschedule watchdog to restart us
        WatchdogReceiver.scheduleWatchdog(this)
        Log.d(TAG, "App removed from recents — watchdog scheduled")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        serviceScope.cancel()
        wakeLock?.takeIf { it.isHeld }?.release()
        // Reschedule watchdog to restart service
        WatchdogReceiver.scheduleWatchdog(this)
        Log.d(TAG, "Service destroyed — watchdog will restart it")
    }
}
