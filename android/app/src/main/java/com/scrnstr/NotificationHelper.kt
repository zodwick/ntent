package com.scrnstr

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.google.gson.JsonObject

class NotificationHelper(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

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
            .setContentTitle("ScrnStr")
            .setContentText("Analyzing screenshot...")
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setOngoing(true)
            .build()

        notificationManager.notify(Config.RESULT_NOTIFICATION_ID, notification)
    }

    fun showResult(category: String, data: JsonObject, screenshotUri: Uri) {
        val (title, text) = when (category) {
            "food_bill" -> {
                val total = data.get("total")?.asString ?: "?"
                val restaurant = data.get("restaurant")?.asString ?: "Unknown"
                "Food Bill: $total" to "$restaurant -- Tap to organize"
            }
            "event" -> {
                val eventTitle = data.get("title")?.asString ?: "Event"
                val date = data.get("date")?.asString ?: ""
                "Event: $eventTitle" to "$date -- Tap to add to calendar"
            }
            "tech_article" -> {
                val articleTitle = data.get("title")?.asString ?: "Article"
                "Tech Article" to "$articleTitle -- Tap to share with friends"
            }
            "movie" -> {
                val movieTitle = data.get("title")?.asString ?: "Movie"
                "Movie: $movieTitle" to "Tap to add to Letterboxd"
            }
            else -> "Screenshot Analyzed" to "Unknown category"
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

        val notification = NotificationCompat.Builder(context, Config.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(Config.RESULT_NOTIFICATION_ID, notification)
    }
}
