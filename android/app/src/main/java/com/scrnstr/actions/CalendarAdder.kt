package com.scrnstr.actions

import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import android.util.Log
import android.widget.Toast
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

object CalendarAdder {

    private const val TAG = "CalendarAdder"

    suspend fun addEvent(context: Context, data: JsonObject) {
        withContext(Dispatchers.IO) {
            try {
                val title = data.get("title")?.asString ?: "Event"
                val dateStr = data.get("date")?.asString ?: ""
                val timeStr = data.get("time")?.asString ?: ""
                val location = data.get("location")?.asString ?: ""

                val startMillis = parseDateTime(dateStr, timeStr)
                val endMillis = startMillis + 2 * 60 * 60 * 1000 // default 2 hour duration

                val calendarId = getPrimaryCalendarId(context)

                val values = ContentValues().apply {
                    put(CalendarContract.Events.CALENDAR_ID, calendarId)
                    put(CalendarContract.Events.TITLE, title)
                    put(CalendarContract.Events.DTSTART, startMillis)
                    put(CalendarContract.Events.DTEND, endMillis)
                    put(CalendarContract.Events.EVENT_LOCATION, location)
                    put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                }

                context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                Log.d(TAG, "Event added: $title")

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Event added to calendar", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error adding event", e)
            }
        }
    }

    private fun getPrimaryCalendarId(context: Context): Long {
        val projection = arrayOf(CalendarContract.Calendars._ID)
        val selection = "${CalendarContract.Calendars.IS_PRIMARY} = 1"
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI, projection, selection, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getLong(0)
            }
        }
        // Fallback to first available calendar
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI, projection, null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getLong(0)
            }
        }
        return 1L
    }

    private fun parseDateTime(dateStr: String, timeStr: String): Long {
        val formats = listOf(
            "yyyy-MM-dd HH:mm", "MM/dd/yyyy HH:mm", "dd MMM yyyy HH:mm",
            "EEE dd MMM yyyy hh:mm a", "EEE dd MMM yyyy HH:mm",
            "dd MMM yyyy hh:mm a", "MMM dd, yyyy hh:mm a",
            "yyyy-MM-dd", "MM/dd/yyyy", "dd MMM yyyy", "EEE dd MMM yyyy"
        )
        val combined = "$dateStr $timeStr".trim()
        for (format in formats) {
            try {
                val sdf = SimpleDateFormat(format, Locale.US)
                val date = sdf.parse(combined)
                if (date != null) return date.time
            } catch (_: Exception) { }
        }
        // Fallback: tomorrow at noon
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, 1)
        cal.set(Calendar.HOUR_OF_DAY, 12)
        cal.set(Calendar.MINUTE, 0)
        return cal.timeInMillis
    }
}
