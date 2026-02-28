package com.scrnstr

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import com.google.gson.JsonObject
import com.scrnstr.data.InterceptHistory
import com.scrnstr.data.InterceptRecord

class NotificationHelper(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val interceptHistory = InterceptHistory(context)

    init {
        createChannel()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            Config.NOTIFICATION_CHANNEL_ID,
            Config.NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Screenshot analysis results and actions"
        }
        notificationManager.createNotificationChannel(channel)
    }

    fun showAnalyzing() {
        val notification = NotificationCompat.Builder(context, Config.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("SCRNSTR")
            .setContentText("Analyzing screenshot...")
            .setSmallIcon(R.drawable.ic_notif_scrnstr)
            .setColor(0xFF00FF41.toInt())
            .setOngoing(true)
            .setProgress(0, 0, true)
            .build()

        notificationManager.notify(Config.RESULT_NOTIFICATION_ID, notification)
    }

    fun showResult(category: String, data: JsonObject, screenshotUri: Uri) {
        val (title, text) = when (category) {
            "food_bill" -> {
                val total = data.get("total")?.asString ?: "?"
                val restaurant = data.get("restaurant")?.asString ?: "Unknown"
                "Food Bill: $total" to "$restaurant — Tap to organize"
            }
            "event" -> {
                val eventTitle = data.get("title")?.asString ?: "Event"
                val date = data.get("date")?.asString ?: ""
                "Event: $eventTitle" to "$date — Tap to add to calendar"
            }
            "tech_article" -> {
                val articleTitle = data.get("title")?.asString ?: "Article"
                "Tech Article" to "$articleTitle — Tap to share"
            }
            "movie" -> {
                val movieTitle = data.get("title")?.asString ?: "Movie"
                "Movie: $movieTitle" to "Tap to add to Letterboxd"
            }
            "coupon_code" -> {
                val code = data.get("code")?.asString ?: "Code"
                val platform = data.get("platform")?.asString ?: ""
                "Coupon: $code" to "${if (platform.isNotEmpty()) "$platform — " else ""}Tap to copy"
            }
            "contact" -> {
                val name = data.get("name")?.asString ?: "Contact"
                val company = data.get("company")?.asString ?: ""
                "Contact: $name" to "${if (company.isNotEmpty()) "$company — " else ""}Tap to save"
            }
            "wifi_password" -> {
                val ssid = data.get("ssid")?.asString ?: "Network"
                "WiFi: $ssid" to "Tap to connect"
            }
            "address" -> {
                val placeName = data.get("place_name")?.asString ?: ""
                val city = data.get("city")?.asString ?: ""
                val label = if (placeName.isNotEmpty()) placeName else "Location"
                "Address: $label" to "${if (city.isNotEmpty()) "$city — " else ""}Tap to open in Maps"
            }
            "reminder" -> {
                val reminderTitle = data.get("title")?.asString ?: "Reminder"
                val time = data.get("time")?.asString ?: ""
                "Reminder: $reminderTitle" to "${if (time.isNotEmpty()) "$time — " else ""}Tap to set alarm"
            }
            "travel" -> {
                val travelTitle = data.get("title")?.asString ?: "Trip"
                val date = data.get("date")?.asString ?: ""
                "Travel: $travelTitle" to "${if (date.isNotEmpty()) "$date — " else ""}Tap to add to calendar"
            }
            else -> "Screenshot Analyzed" to "Unknown category"
        }

        // Save to intercept history
        val displayTitle = data.get("title")?.asString
            ?: data.get("restaurant")?.asString
            ?: data.get("name")?.asString
            ?: data.get("code")?.asString
            ?: data.get("ssid")?.asString
            ?: data.get("place_name")?.asString
            ?: category
        interceptHistory.addIntercept(
            InterceptRecord(
                category = category,
                title = displayTitle,
                thumbnailUri = screenshotUri.toString(),
                timestamp = System.currentTimeMillis()
            )
        )

        val accentColor = when (category) {
            "food_bill", "event", "travel" -> 0xFF00FF41.toInt()   // green
            "tech_article", "movie" -> 0xFFFFB000.toInt()          // amber
            "coupon_code" -> 0xFFFF4081.toInt()                    // pink
            "contact", "wifi_password" -> 0xFF448AFF.toInt()       // blue
            "address" -> 0xFFFF6D00.toInt()                        // orange
            "reminder" -> 0xFFE040FB.toInt()                       // purple
            else -> 0xFF00FF41.toInt()
        }

        val actionIntent = Intent(context, ActionReceiver::class.java).apply {
            putExtra("category", category)
            putExtra("data", data.toString())
            putExtra("screenshotUri", screenshotUri.toString())
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            System.currentTimeMillis().toInt(),
            actionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val thumbnail = loadThumbnail(screenshotUri)

        val builder = NotificationCompat.Builder(context, Config.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notif_scrnstr)
            .setColor(accentColor)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        if (thumbnail != null) {
            builder.setLargeIcon(thumbnail)
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(thumbnail)
                    .bigLargeIcon(null as Bitmap?)
            )
        }

        notificationManager.notify(Config.RESULT_NOTIFICATION_ID, builder.build())
    }

    private fun loadThumbnail(uri: Uri): Bitmap? {
        return try {
            val original = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source)
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
            Bitmap.createScaledBitmap(original, 256, 256, true)
        } catch (e: Exception) {
            null
        }
    }
}
