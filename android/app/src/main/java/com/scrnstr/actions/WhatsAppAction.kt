package com.scrnstr.actions

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.google.gson.Gson
import com.scrnstr.Config
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

object WhatsAppAction {

    private const val TAG = "WhatsAppAction"
    private val client = OkHttpClient()
    private val gson = Gson()

    suspend fun share(context: Context, data: JsonObject) {
        withContext(Dispatchers.IO) {
            try {
                val title = data.get("title")?.asString ?: "Article"
                val summary = data.get("summary")?.asString ?: ""

                val body = JsonObject().apply {
                    addProperty("message", "Check out this article: $title - $summary")
                    add("contacts", gson.toJsonTree(Config.WHATSAPP_CONTACTS))
                }

                val request = Request.Builder()
                    .url("${Config.SERVER_URL}/whatsapp")
                    .post(body.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                Log.d(TAG, "WhatsApp share response: ${response.code}")

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Shared with friends", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sharing via WhatsApp", e)
            }
        }
    }
}
