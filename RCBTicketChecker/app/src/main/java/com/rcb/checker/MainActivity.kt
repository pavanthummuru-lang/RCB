package com.rcb.checker

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.rcb.checker.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var config: ConfigManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        config = ConfigManager(this)

        // Load saved config into UI
        binding.etApiUrl.setText(config.apiUrl)
        binding.etCheckInterval.setText((config.checkIntervalMs / 1000).toString())
        binding.etReportInterval.setText((config.reportIntervalMs / 60000).toString())
        binding.etWatchKeyword.setText(config.watchKeyword)

        requestNotificationPermission()
        requestIgnoreBatteryOptimizations()

        binding.btnStartStop.setOnClickListener {
            if (TicketCheckerService.isRunning) {
                // Stop service + cancel watchdog
                WatchdogReceiver.cancelWatchdog(this)
                stopService(Intent(this, TicketCheckerService::class.java))
                Toast.makeText(this, "Checker stopped", Toast.LENGTH_SHORT).show()
            } else {
                saveConfig()
                // Clear old state so fresh start
                StateManager(this).clear()
                startForegroundService(Intent(this, TicketCheckerService::class.java))
                Toast.makeText(this, "Checker started! Running in background.", Toast.LENGTH_LONG).show()
            }
            updateUI()
        }

        binding.btnStopAlarm.setOnClickListener {
            val intent = Intent(this, TicketCheckerService::class.java).apply {
                action = TicketCheckerService.ACTION_STOP_ALARM
            }
            startService(intent)
            Toast.makeText(this, "Alarm stopped", Toast.LENGTH_SHORT).show()
        }

        binding.btnBuyTickets.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shop.royalchallengers.com/")))
        }

        binding.btnSaveConfig.setOnClickListener {
            saveConfig()
            Toast.makeText(this, "Config saved!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun saveConfig() {
        val url = binding.etApiUrl.text.toString().trim()
        val interval = binding.etCheckInterval.text.toString().toLongOrNull() ?: 60
        val report = binding.etReportInterval.text.toString().toLongOrNull() ?: 30
        val keyword = binding.etWatchKeyword.text.toString().trim()

        config.apiUrl = if (url.isNotEmpty()) url else ConfigManager.DEFAULT_API_URL
        config.checkIntervalMs = interval * 1000L
        config.reportIntervalMs = report * 60_000L
        config.watchKeyword = if (keyword.isNotEmpty()) keyword else "Gujarat"
    }

    private fun updateUI() {
        if (TicketCheckerService.isRunning) {
            binding.statusIndicator.setBackgroundColor(getColor(R.color.green))
            binding.tvStatus.text = "MONITORING ACTIVE"
            binding.tvSubStatus.text = "Running in background — you can close this app"
            binding.btnStartStop.text = "STOP CHECKER"
            binding.btnStartStop.setBackgroundColor(getColor(R.color.red))
            binding.configLayout.isEnabled = false
            binding.configLayout.alpha = 0.5f
        } else {
            binding.statusIndicator.setBackgroundColor(getColor(R.color.red))
            binding.tvStatus.text = "NOT MONITORING"
            binding.tvSubStatus.text = "Configure below then tap Start"
            binding.btnStartStop.text = "START CHECKER"
            binding.btnStartStop.setBackgroundColor(getColor(R.color.green))
            binding.configLayout.isEnabled = true
            binding.configLayout.alpha = 1.0f
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    100
                )
            }
        }
    }

    private fun requestIgnoreBatteryOptimizations() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            try {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                )
            } catch (e: Exception) {
                // Some devices don't support this intent
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
    }
}
