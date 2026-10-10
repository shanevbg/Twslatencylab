package com.twslatencylab

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class RunRecord(
    val atMs: Long,
    val netMs: Double,
    val headsetMs: Double,
    val speakerMs: Double,
    val trials: Int,
    val loMs: Double,
    val hiMs: Double,
    val route: String
) {
    fun whenLabel(): String =
        WHEN.format(Instant.ofEpochMilli(atMs).atZone(ZoneId.systemDefault()))

    fun toJson(): JSONObject = JSONObject()
        .put("atMs", atMs)
        .put("netMs", netMs)
        .put("headsetMs", headsetMs)
        .put("speakerMs", speakerMs)
        .put("trials", trials)
        .put("loMs", loMs)
        .put("hiMs", hiMs)
        .put("route", route)

    companion object {
        private val WHEN: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, h:mm a")

        fun fromJson(o: JSONObject) = RunRecord(
            atMs = o.getLong("atMs"),
            netMs = o.getDouble("netMs"),
            headsetMs = o.getDouble("headsetMs"),
            speakerMs = o.getDouble("speakerMs"),
            trials = o.getInt("trials"),
            loMs = o.getDouble("loMs"),
            hiMs = o.getDouble("hiMs"),
            route = o.optString("route")
        )
    }
}

class RunStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("runs", Context.MODE_PRIVATE)

    fun load(): List<RunRecord> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { RunRecord.fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    fun add(record: RunRecord): List<RunRecord> {
        val next = listOf(record) + load().take(39)
        val arr = JSONArray()
        next.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY, arr.toString()).apply()
        return next
    }

    companion object {
        private const val KEY = "history"
    }
}
