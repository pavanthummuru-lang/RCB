package com.rcb.checker

import android.app.Service
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

        // IST timezone stop date: Apr 25, 2026 00:00
        private val STOP_TIMESTAMP: Long by lazy {
            Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata")).apply {
                set(2026, Calendar.APRIL, 25, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }

        const val REPORT_INTERVAL_MS = 30 * 60 * 1000L // 30 minutes
        const val ALARM_INTERVAL_MS = 5000L             // 5 seconds

        fun stopAlarm() {
            alarmJob?.cancel()
            alarmJob = null
            Log.d(TAG, "Alarm stopped by user")
        }

        const val ACTION_STOP_ALARM = "com.rcb.checker.STOP_ALARM"
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var mainJob: Job? = null
    private lateinit var wakeLock: PowerManager.WakeLock

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RCBChecker::Lock")
        wakeLock.acquire(12 * 60 * 60 * 1000L) // 12 hours, renewed by START_STICKY
        Log.d(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_ALARM) {
            stopAlarm()
            return START_STICKY
        }

        NotificationHelper.createChannels(this)
        startForeground(
            NotificationHelper.SERVICE_NOTIFICATION_ID,
            NotificationHelper.buildServiceNotification(this, "RCB Checker Active", "Monitoring tickets every ~60 seconds")
        )

        if (mainJob?.isActive != true) {
            startMainLoop()
        }

        return START_STICKY // Restart automatically if killed
    }

    private fun startMainLoop() {
        mainJob = serviceScope.launch {
            val state = StateManager(applicationContext)
            var errorCount = 0

            while (isActive) {
                // Check stop date
                if (System.currentTimeMillis() >= STOP_TIMESTAMP) {
                    NotificationHelper.showAlertNotification(
                        applicationContext,
                        "RCB Checker Stopped",
                        "Apr 24 has passed. Auto-stopped."
                    )
                    stopSelf()
                    break
                }

                try {
                    val result = ApiHelper.fetch()

                    if (result != null) {
                        val newHash = StateManager.hash(result.rawBody)
                        val oldHash = state.lastHash

                        Log.d(TAG, "Fetched ${result.events.size} events. Hash: ${newHash.take(8)}")

                        if (oldHash == null) {
                            // First run
                            state.save(newHash, result.events)
                            state.lastReportTime = System.currentTimeMillis()
                            NotificationHelper.showStartNotification(applicationContext, result.events)
                            NotificationHelper.updateServiceNotification(
                                applicationContext,
                                "RCB Checker Active",
                                "${result.events.size} match(es) monitored"
                            )
                            Log.d(TAG, "First run complete")
                        } else if (oldHash != newHash) {
                            Log.d(TAG, "CHANGE DETECTED!")
                            handleChange(state.lastEvents, result.events, state)
                            state.save(newHash, result.events)
                        }

                        // 30-min report
                        if (System.currentTimeMillis() - state.lastReportTime >= REPORT_INTERVAL_MS) {
                            NotificationHelper.showReportNotification(applicationContext, result.events)
                            state.lastReportTime = System.currentTimeMillis()
                            Log.d(TAG, "30-min report sent")
                        }

                        errorCount = 0
                    } else {
                        throw Exception("Fetch returned null")
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

                // Random 45-90 second interval
                val sleepMs = ((45..90).random() * 1000).toLong()
                Log.d(TAG, "Next check in ${sleepMs / 1000}s")
                delay(sleepMs)
            }
        }
    }

    private fun handleChange(
        oldEvents: List<ApiHelper.Event>,
        newEvents: List<ApiHelper.Event>,
        state: StateManager
    ) {
        val oldNames = oldEvents.map { it.name }

        // New matches
        val added = newEvents.filter { it.name !in oldNames }

        // Status changes
        val changes = newEvents.mapNotNull { ne ->
            oldEvents.find { oe -> oe.name == ne.name && oe.status != ne.status }
                ?.let { oe ->
                    Triple(ne, oe.status, ne.status)
                }
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
                Log.d(TAG, "New match alarm started: $title")
            }

            changes.isNotEmpty() -> {
                val title = "RCB Ticket Status Changed!"
                val msg = changes.joinToString("\n\n") { (ne, old, new) ->
                    "${ne.name}\n$old  →  $new\nDate: ${ne.date}"
                }
                NotificationHelper.showAlertNotification(applicationContext, title, msg)

                // Alarm only if tickets opened (not sold out anymore)
                val ticketsOpened = changes.any { (_, _, newStatus) -> newStatus != "SOLD OUT" }
                if (ticketsOpened) {
                    startAlarmLoop(title, msg)
                    Log.d(TAG, "Ticket open alarm started!")
                }
            }

            else -> {
                // Generic change
                val msg = newEvents.joinToString("\n") { "• ${it.name} | ${it.status}" }
                NotificationHelper.showAlertNotification(applicationContext, "RCB Tickets Changed!", msg)
                Log.d(TAG, "Generic change notified")
            }
        }
    }

    private fun startAlarmLoop(title: String, message: String) {
        alarmJob?.cancel()
        alarmJob = serviceScope.launch {
            var count = 0
            while (isActive) {
                count++
                Log.d(TAG, "ALARM #$count")
                NotificationHelper.showAlarmNotification(applicationContext, title, message, count)
                delay(ALARM_INTERVAL_MS)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        serviceScope.cancel()
        if (::wakeLock.isInitialized && wakeLock.isHeld) wakeLock.release()
        Log.d(TAG, "Service destroyed")
    }
}
