package com.scrnstr.actions

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.google.gson.JsonObject
import com.scrnstr.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

object LetterboxdAction {

    private const val TAG = "LetterboxdAction"
    private val client = OkHttpClient()

    suspend fun addToWatchlist(context: Context, data: JsonObject) {
        withContext(Dispatchers.IO) {
            try {
                val title = data.get("title")?.asString ?: "Unknown Movie"
                val year = data.get("year")?.asString ?: ""

                val body = JsonObject().apply {
                    addProperty("movie", title)
                    addProperty("year", year)
                }

                val request = Request.Builder()
                    .url("${Config.SERVER_URL}/letterboxd")
                    .post(body.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                Log.d(TAG, "Letterboxd response: ${response.code}")

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Added to Letterboxd watchlist", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error adding to Letterboxd", e)
            }
        }
    }
}
