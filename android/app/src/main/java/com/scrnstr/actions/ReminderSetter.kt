package com.scrnstr.actions

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.util.Log
import android.widget.Toast
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ReminderSetter {

    private const val TAG = "ReminderSetter"

    suspend fun setReminder(context: Context, data: JsonObject) {
        withContext(Dispatchers.Main) {
            try {
                val title = data.get("title")?.asString ?: "Reminder"
                val timeStr = data.get("time")?.asString ?: ""

                val (hour, minute) = parseTime(timeStr)

                val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                    putExtra(AlarmClock.EXTRA_MESSAGE, title)
                    putExtra(AlarmClock.EXTRA_HOUR, hour)
                    putExtra(AlarmClock.EXTRA_MINUTES, minute)
                    putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                Log.d(TAG, "Alarm set: $title at $hour:${minute.toString().padStart(2, '0')}")
                Toast.makeText(context, "Alarm set: $title", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e(TAG, "Error setting reminder", e)
            }
        }
    }

    private fun parseTime(timeStr: String): Pair<Int, Int> {
        if (timeStr.isBlank()) return 9 to 0

        // Try HH:mm or H:mm
        val colonMatch = Regex("""(\d{1,2}):(\d{2})""").find(timeStr)
        if (colonMatch != null) {
            var hour = colonMatch.groupValues[1].toInt()
            val minute = colonMatch.groupValues[2].toInt()
            // Handle AM/PM
            val lower = timeStr.lowercase()
            if (lower.contains("pm") && hour < 12) hour += 12
            if (lower.contains("am") && hour == 12) hour = 0
            return hour.coerceIn(0, 23) to minute.coerceIn(0, 59)
        }

        return 9 to 0
    }
}
