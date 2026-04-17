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

    companion object {
        private const val NOTIFICATION_PERMISSION_CODE = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestNotificationPermission()
        requestIgnoreBatteryOptimizations()

        binding.btnStartStop.setOnClickListener {
            if (TicketCheckerService.isRunning) {
                stopService(Intent(this, TicketCheckerService::class.java))
                Toast.makeText(this, "Checker stopped", Toast.LENGTH_SHORT).show()
            } else {
                startForegroundService(Intent(this, TicketCheckerService::class.java))
                Toast.makeText(this, "Checker started!", Toast.LENGTH_SHORT).show()
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
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun updateUI() {
        if (TicketCheckerService.isRunning) {
            binding.statusIndicator.setBackgroundColor(getColor(R.color.green))
            binding.tvStatus.text = "MONITORING ACTIVE"
            binding.tvSubStatus.text = "Checking every ~60 seconds\nAuto-stops after Apr 24, 2026"
            binding.btnStartStop.text = "STOP CHECKER"
            binding.btnStartStop.setBackgroundColor(getColor(R.color.red))
        } else {
            binding.statusIndicator.setBackgroundColor(getColor(R.color.red))
            binding.tvStatus.text = "NOT MONITORING"
            binding.tvSubStatus.text = "Tap Start to begin"
            binding.btnStartStop.text = "START CHECKER"
            binding.btnStartStop.setBackgroundColor(getColor(R.color.green))
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
                    NOTIFICATION_PERMISSION_CODE
                )
            }
        }
    }

    private fun requestIgnoreBatteryOptimizations() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }
    }
}
