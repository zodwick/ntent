package com.scrnstr.actions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AddressOpener {

    private const val TAG = "AddressOpener"

    suspend fun open(context: Context, data: JsonObject) {
        withContext(Dispatchers.Main) {
            try {
                val placeName = data.get("place_name")?.asString ?: ""
                val address = data.get("address")?.asString ?: ""
                val city = data.get("city")?.asString ?: ""

                val query = listOf(placeName, address, city)
                    .filter { it.isNotBlank() }
                    .joinToString(", ")

                if (query.isBlank()) {
                    Toast.makeText(context, "No address found", Toast.LENGTH_SHORT).show()
                    return@withContext
                }

                val encodedQuery = Uri.encode(query)
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$encodedQuery")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                Log.d(TAG, "Opened maps for: $query")
            } catch (e: Exception) {
                Log.e(TAG, "Error opening address", e)
            }
        }
    }
}
