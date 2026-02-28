package com.scrnstr

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ClassificationResult(
    val category: String,
    val data: JsonObject,
    val suggestedAction: String
)

class GeminiClassifier(private val context: Context) {

    companion object {
        private const val TAG = "GeminiClassifier"
        private const val PROMPT = """Analyze this screenshot and classify it into one of these categories:
- food_bill: A restaurant bill or food receipt. Extract: { "total": "amount", "restaurant": "name", "date": "date" }
- event: An event poster, ticket, or invitation. Extract: { "title": "event name", "date": "date", "time": "time", "location": "venue" }
- tech_article: A tech article, blog post, or news. Extract: { "title": "article title", "summary": "brief summary", "url_if_visible": "url or empty" }
- movie: A movie poster, review, or recommendation. Extract: { "title": "movie name", "year": "year" }
- coupon_code: A promo code, discount code, or voucher. Extract: { "code": "the code", "platform": "store or service name", "description": "what the coupon is for" }
- contact: A business card, contact info, or phone number. Extract: { "name": "person name", "phone": "phone number", "email": "email address", "company": "company name" }
- wifi_password: WiFi credentials or network info. Extract: { "ssid": "network name", "password": "the password", "security": "WPA2 or WPA3 or Open" }
- address: An address, location, or place. Extract: { "address": "street address", "place_name": "name of place", "city": "city name" }
- reminder: A deadline, todo, or reminder. Extract: { "title": "what to remember", "date": "date", "time": "time" }
- travel: A flight, hotel booking, or boarding pass. Extract: { "title": "trip description", "date": "date", "time": "time", "location": "destination", "booking_ref": "reference number" }
- unknown: Does not fit any category. Return empty data.

Respond ONLY with valid JSON in this exact format:
{ "category": "category_name", "data": { ... }, "suggested_action": "brief description of action" }"""
    }

    private val gson = Gson()

    private val model = GenerativeModel(
        modelName = "gemini-2.0-flash",
        apiKey = Config.GEMINI_API_KEY
    )

    suspend fun classify(imageUri: Uri): ClassificationResult? = withContext(Dispatchers.IO) {
        try {
            val bitmap = context.contentResolver.openInputStream(imageUri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            } ?: return@withContext null

            val response = model.generateContent(
                content {
                    image(bitmap)
                    text(PROMPT)
                }
            )

            val text = response.text ?: return@withContext null
            Log.d(TAG, "Gemini response: $text")

            val cleaned = text.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val json = gson.fromJson(cleaned, JsonObject::class.java)
            ClassificationResult(
                category = json.get("category").asString,
                data = json.getAsJsonObject("data") ?: JsonObject(),
                suggestedAction = json.get("suggested_action")?.asString ?: ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "Classification error", e)
            null
        }
    }
}
