package com.alimjangbot.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LogEntry(
    val timestamp: Long,
    val status: Status,
    val message: String
) {
    enum class Status { SUCCESS, FAILURE, SKIPPED }

    fun formattedTime(): String =
        SimpleDateFormat("MM/dd HH:mm", Locale.KOREA).format(Date(timestamp))

    fun statusEmoji(): String = when (status) {
        Status.SUCCESS -> "✅"
        Status.FAILURE -> "❌"
        Status.SKIPPED -> "⏭️"
    }
}

/**
 * 전송 이력을 SharedPreferences에 JSON 배열로 저장.
 * 최근 50건만 유지.
 */
class SendLogRepository private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("alimjang_logs", Context.MODE_PRIVATE)

    companion object {
        @Volatile
        private var INSTANCE: SendLogRepository? = null

        fun getInstance(context: Context): SendLogRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: SendLogRepository(context.applicationContext).also { INSTANCE = it }
            }

        private const val KEY_LOGS = "logs"
        private const val MAX_LOGS = 50
    }

    fun addLog(status: LogEntry.Status, message: String) {
        val entry = LogEntry(System.currentTimeMillis(), status, message)
        val logs = getLogs().toMutableList()
        logs.add(0, entry)          // 최신이 앞으로
        if (logs.size > MAX_LOGS) logs.removeAt(logs.lastIndex)
        saveLogs(logs)
    }

    fun getLogs(): List<LogEntry> {
        val json = prefs.getString(KEY_LOGS, "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                LogEntry(
                    timestamp = obj.getLong("ts"),
                    status    = LogEntry.Status.valueOf(obj.getString("status")),
                    message   = obj.getString("msg")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun clearLogs() = prefs.edit { putString(KEY_LOGS, "[]") }

    private fun saveLogs(logs: List<LogEntry>) {
        val arr = JSONArray()
        logs.forEach { entry ->
            arr.put(JSONObject().apply {
                put("ts",     entry.timestamp)
                put("status", entry.status.name)
                put("msg",    entry.message)
            })
        }
        prefs.edit { putString(KEY_LOGS, arr.toString()) }
    }
}
