package com.scrnstr

import android.app.Service
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ScreenshotObserverService : Service() {

    companion object {
        private const val TAG = "ScreenshotObserver"
        private const val DEBOUNCE_MS = 2000L
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var contentObserver: ContentObserver
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var classifier: GeminiClassifier
    private var lastProcessedTime = 0L

    override fun onCreate() {
        super.onCreate()
        notificationHelper = NotificationHelper(this)
        classifier = GeminiClassifier(this)
        setupContentObserver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, Config.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("ScrnStr")
            .setContentText("Monitoring screenshots...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()

        startForeground(Config.SERVICE_NOTIFICATION_ID, notification)

        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            contentObserver
        )

        Log.d(TAG, "Screenshot observer started")
        return START_STICKY
    }

    private fun setupContentObserver() {
        contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                if (uri == null) return

                val now = System.currentTimeMillis()
                if (now - lastProcessedTime < DEBOUNCE_MS) return

                val path = getPathFromUri(uri)
                if (path == null || !path.contains("Screenshot", ignoreCase = true)) return

                lastProcessedTime = now
                Log.d(TAG, "Screenshot detected: $path")
                processScreenshot(uri)
            }
        }
    }

    private fun getPathFromUri(uri: Uri): String? {
        val projection = arrayOf(MediaStore.Images.Media.DATA)
        return try {
            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA))
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting path from URI", e)
            null
        }
    }

    private fun processScreenshot(uri: Uri) {
        serviceScope.launch {
            try {
                notificationHelper.showAnalyzing()

                val result = classifier.classify(uri)
                if (result != null) {
                    notificationHelper.showResult(result.category, result.data, uri)
                } else {
                    Log.w(TAG, "Classification returned null")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing screenshot", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        contentResolver.unregisterContentObserver(contentObserver)
        serviceScope.cancel()
        Log.d(TAG, "Screenshot observer stopped")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
