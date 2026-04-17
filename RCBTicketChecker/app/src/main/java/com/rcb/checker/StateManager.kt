package com.rcb.checker

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

class StateManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("rcb_state", Context.MODE_PRIVATE)

    var lastHash: String?
        get() = prefs.getString("last_hash", null)
        set(value) = prefs.edit().putString("last_hash", value).apply()

    var lastReportTime: Long
        get() = prefs.getLong("last_report_time", 0L)
        set(value) = prefs.edit().putLong("last_report_time", value).apply()

    var lastEvents: List<ApiHelper.Event>
        get() {
            val json = prefs.getString("last_events", "[]") ?: "[]"
            return try {
                val arr = JSONArray(json)
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    ApiHelper.Event(
                        name = o.optString("name"),
                        date = o.optString("date"),
                        status = o.optString("status"),
                        price = o.optString("price"),
                        venue = o.optString("venue"),
                        team1 = o.optString("team1"),
                        team2 = o.optString("team2")
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
        set(value) {
            val arr = JSONArray()
            value.forEach { e ->
                arr.put(JSONObject().apply {
                    put("name", e.name)
                    put("date", e.date)
                    put("status", e.status)
                    put("price", e.price)
                    put("venue", e.venue)
                    put("team1", e.team1)
                    put("team2", e.team2)
                })
            }
            prefs.edit().putString("last_events", arr.toString()).apply()
        }

    fun save(hash: String, events: List<ApiHelper.Event>) {
        lastHash = hash
        lastEvents = events
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        fun hash(body: String): String {
            return MessageDigest.getInstance("SHA-256")
                .digest(body.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }
    }
}
