package com.rcb.checker

import android.content.Context
import android.content.SharedPreferences

class ConfigManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("rcb_config", Context.MODE_PRIVATE)

    var apiUrl: String
        get() = prefs.getString("api_url", DEFAULT_API_URL) ?: DEFAULT_API_URL
        set(v) = prefs.edit().putString("api_url", v).apply()

    var checkIntervalMs: Long
        get() = prefs.getLong("check_interval_ms", 60_000L)
        set(v) = prefs.edit().putLong("check_interval_ms", v).apply()

    var reportIntervalMs: Long
        get() = prefs.getLong("report_interval_ms", 30 * 60 * 1000L)
        set(v) = prefs.edit().putLong("report_interval_ms", v).apply()

    var watchKeyword: String
        get() = prefs.getString("watch_keyword", "Gujarat") ?: "Gujarat"
        set(v) = prefs.edit().putString("watch_keyword", v).apply()

    companion object {
        const val DEFAULT_API_URL = "https://rcbscaleapi.ticketgenie.in/ticket/eventlist/O"
    }
}
