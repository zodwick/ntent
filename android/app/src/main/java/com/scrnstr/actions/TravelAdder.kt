package com.scrnstr.actions

import android.content.Context
import android.util.Log
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object TravelAdder {

    private const val TAG = "TravelAdder"

    suspend fun add(context: Context, data: JsonObject) {
        withContext(Dispatchers.IO) {
            try {
                val title = data.get("title")?.asString ?: "Trip"
                val bookingRef = data.get("booking_ref")?.asString ?: ""

                val enrichedData = JsonObject().apply {
                    // Copy all existing fields
                    for ((key, value) in data.entrySet()) {
                        add(key, value)
                    }
                    // Enrich title with booking reference
                    val enrichedTitle = if (bookingRef.isNotBlank()) {
                        "$title (Ref: $bookingRef)"
                    } else {
                        title
                    }
                    addProperty("title", enrichedTitle)
                }

                Log.d(TAG, "Adding travel event: ${enrichedData.get("title")?.asString}")
                CalendarAdder.addEvent(context, enrichedData)
            } catch (e: Exception) {
                Log.e(TAG, "Error adding travel event", e)
            }
        }
    }
}
