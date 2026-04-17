package com.rcb.checker

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object ApiHelper {

    data class Event(
        val name: String,
        val date: String,
        val status: String,
        val price: String,
        val venue: String,
        val team1: String,
        val team2: String
    )

    data class FetchResult(
        val events: List<Event>,
        val rawBody: String
    )

    fun fetch(apiUrl: String = "https://rcbscaleapi.ticketgenie.in/ticket/eventlist/O"): FetchResult? {
        return try {
            val url = URL(apiUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 15000
                setRequestProperty("accept", "application/json, text/plain, */*")
                setRequestProperty("origin", "https://shop.royalchallengers.com")
                setRequestProperty("referer", "https://shop.royalchallengers.com/")
                setRequestProperty(
                    "user-agent",
                    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
                )
            }

            if (connection.responseCode != 200) {
                connection.disconnect()
                return null
            }

            val body = connection.inputStream.bufferedReader().readText()
            connection.disconnect()

            val json = JSONObject(body)
            val resultArray: JSONArray = json.optJSONArray("result") ?: JSONArray()

            val events = (0 until resultArray.length()).map { i ->
                val e = resultArray.getJSONObject(i)
                Event(
                    name = e.optString("event_Name", ""),
                    date = e.optString("event_Display_Date", ""),
                    status = e.optString("event_Button_Text", ""),
                    price = e.optString("event_Price_Range", "N/A"),
                    venue = e.optString("venue_Name", ""),
                    team1 = e.optString("team_1", ""),
                    team2 = e.optString("team_2", "")
                )
            }

            FetchResult(events, body)
        } catch (e: Exception) {
            null
        }
    }
}
