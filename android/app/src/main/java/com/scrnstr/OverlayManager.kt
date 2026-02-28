package com.scrnstr

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class OverlayManager(private val context: Context) {

    companion object {
        private const val TAG = "OverlayManager"
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var currentView: View? = null
    private var autoDismissRunnable: Runnable? = null
    private var countdownAnimator: ObjectAnimator? = null
    private var pulseAnimator: ObjectAnimator? = null

    fun canShowOverlay(): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun showLoading() {
        handler.post {
            dismissInternal(animate = false)

            val view = LayoutInflater.from(context).inflate(R.layout.overlay_loading, null)
            val params = createLayoutParams()

            // Pulse animation on the green dot
            val pulse = view.findViewById<View>(R.id.loadingPulse)
            pulseAnimator = ObjectAnimator.ofFloat(pulse, "alpha", 1f, 0.2f).apply {
                duration = 800
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = LinearInterpolator()
                start()
            }

            windowManager.addView(view, params)
            currentView = view

            // Slide in animation
            val slideIn = AnimationUtils.loadAnimation(context, R.anim.overlay_slide_in)
            view.startAnimation(slideIn)

            Log.d(TAG, "Loading overlay shown")
        }
    }

    fun showResult(
        category: String,
        data: JsonObject,
        screenshotUri: Uri,
        actionExecutor: suspend () -> Unit
    ) {
        handler.post {
            dismissInternal(animate = false)

            val view = LayoutInflater.from(context).inflate(R.layout.overlay_result, null)
            val params = createLayoutParams()

            // Set content
            val (title, subtitle, actionLabel, accentColor) = getDisplayInfo(category, data)
            view.findViewById<TextView>(R.id.resultTitle).text = title
            view.findViewById<TextView>(R.id.resultSubtitle).text = subtitle
            view.findViewById<TextView>(R.id.actionButton).text = actionLabel
            view.findViewById<View>(R.id.accentBar).setBackgroundColor(accentColor)

            // Load thumbnail
            loadThumbnail(screenshotUri)?.let { bmp ->
                view.findViewById<ImageView>(R.id.resultThumbnail).setImageBitmap(bmp)
            }

            // Action button tap
            view.findViewById<TextView>(R.id.actionButton).setOnClickListener {
                Log.d(TAG, "Action button clicked!")
                dismissInternal(animate = true)
                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        actionExecutor()
                    } catch (e: Exception) {
                        Log.e(TAG, "Action failed", e)
                    }
                }
            }

            // Swipe-to-dismiss
            setupSwipeToDismiss(view)

            windowManager.addView(view, params)
            currentView = view

            // Slide in
            val slideIn = AnimationUtils.loadAnimation(context, R.anim.overlay_slide_in)
            view.startAnimation(slideIn)

            // Auto-dismiss countdown
            startAutoDismiss(view)

            Log.d(TAG, "Result overlay shown: $category")
        }
    }

    fun dismiss(animate: Boolean = true) {
        handler.post { dismissInternal(animate) }
    }

    fun cleanup() {
        dismissInternal(animate = false)
    }

    private fun dismissInternal(animate: Boolean) {
        cancelAutoDismiss()
        pulseAnimator?.cancel()
        pulseAnimator = null

        val view = currentView ?: return
        currentView = null

        if (animate) {
            view.animate()
                .translationY(-view.height.toFloat())
                .alpha(0f)
                .setDuration(250)
                .withEndAction { removeView(view) }
                .start()
        } else {
            removeView(view)
        }
    }

    private fun removeView(view: View) {
        try {
            windowManager.removeView(view)
        } catch (e: Exception) {
            Log.w(TAG, "View already removed", e)
        }
    }

    private fun createLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 100
        }
    }

    private fun setupSwipeToDismiss(view: View) {
        val actionButton = view.findViewById<View>(R.id.actionButton)
        var startRawY = 0f
        var isDragging = false

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startRawY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaY = event.rawY - startRawY
                    if (!isDragging && kotlin.math.abs(deltaY) > 20) {
                        isDragging = true
                    }
                    if (isDragging) {
                        view.translationY = deltaY
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isDragging) {
                        val deltaY = event.rawY - startRawY
                        Log.d(TAG, "Swipe ended, deltaY=$deltaY")
                        if (kotlin.math.abs(deltaY) > 80) {
                            dismissInternal(animate = true)
                        } else {
                            view.animate().translationY(0f).setDuration(150).start()
                        }
                    } else {
                        if (isTouchInsideView(event, actionButton)) {
                            Log.d(TAG, "Tap on action button detected")
                            actionButton.performClick()
                        } else {
                            Log.d(TAG, "Tap outside button, dismissing")
                            dismissInternal(animate = true)
                        }
                    }
                    isDragging = false
                    true
                }
                else -> true
            }
        }
    }

    private fun isTouchInsideView(event: MotionEvent, view: View): Boolean {
        val loc = IntArray(2)
        view.getLocationOnScreen(loc)
        val x = event.rawX
        val y = event.rawY
        return x >= loc[0] && x <= loc[0] + view.width &&
                y >= loc[1] && y <= loc[1] + view.height
    }

    private fun startAutoDismiss(view: View) {
        val progressBar = view.findViewById<ProgressBar>(R.id.autoDismissProgress)
        countdownAnimator = ObjectAnimator.ofInt(progressBar, "progress", 1000, 0).apply {
            duration = Config.OVERLAY_AUTO_DISMISS_MS
            interpolator = LinearInterpolator()
            start()
        }

        autoDismissRunnable = Runnable { dismiss(animate = true) }
        handler.postDelayed(autoDismissRunnable!!, Config.OVERLAY_AUTO_DISMISS_MS)
    }

    private fun cancelAutoDismiss() {
        autoDismissRunnable?.let { handler.removeCallbacks(it) }
        autoDismissRunnable = null
        countdownAnimator?.cancel()
        countdownAnimator = null
    }

    private data class DisplayInfo(
        val title: String,
        val subtitle: String,
        val actionLabel: String,
        val accentColor: Int
    )

    private fun getDisplayInfo(category: String, data: JsonObject): DisplayInfo {
        return when (category) {
            "food_bill" -> DisplayInfo(
                title = "FOOD BILL: ${data.get("total")?.asString ?: "?"}",
                subtitle = data.get("restaurant")?.asString ?: "Unknown",
                actionLabel = "ORGANIZE →",
                accentColor = 0xFF00FF41.toInt()
            )
            "event" -> DisplayInfo(
                title = "EVENT: ${data.get("title")?.asString ?: "Event"}",
                subtitle = data.get("date")?.asString ?: "",
                actionLabel = "ADD TO CALENDAR →",
                accentColor = 0xFF00FF41.toInt()
            )
            "tech_article" -> DisplayInfo(
                title = "TECH ARTICLE",
                subtitle = data.get("title")?.asString ?: "Article",
                actionLabel = "SHARE →",
                accentColor = 0xFFFFB000.toInt()
            )
            "movie" -> DisplayInfo(
                title = "MOVIE: ${data.get("title")?.asString ?: "Movie"}",
                subtitle = "Add to Letterboxd watchlist",
                actionLabel = "ADD TO WATCHLIST →",
                accentColor = 0xFFFFB000.toInt()
            )
            "coupon_code" -> DisplayInfo(
                title = "COUPON: ${data.get("code")?.asString ?: "Code"}",
                subtitle = data.get("platform")?.asString ?: "",
                actionLabel = "COPY →",
                accentColor = 0xFFFF4081.toInt()
            )
            "contact" -> DisplayInfo(
                title = "CONTACT: ${data.get("name")?.asString ?: "Contact"}",
                subtitle = data.get("company")?.asString ?: "",
                actionLabel = "SAVE →",
                accentColor = 0xFF448AFF.toInt()
            )
            "wifi_password" -> DisplayInfo(
                title = "WIFI: ${data.get("ssid")?.asString ?: "Network"}",
                subtitle = "Tap to connect",
                actionLabel = "CONNECT →",
                accentColor = 0xFF448AFF.toInt()
            )
            "address" -> DisplayInfo(
                title = "ADDRESS: ${data.get("place_name")?.asString ?: "Location"}",
                subtitle = data.get("city")?.asString ?: "",
                actionLabel = "OPEN IN MAPS →",
                accentColor = 0xFFFF6D00.toInt()
            )
            "reminder" -> DisplayInfo(
                title = "REMINDER: ${data.get("title")?.asString ?: "Reminder"}",
                subtitle = data.get("time")?.asString ?: "",
                actionLabel = "SET ALARM →",
                accentColor = 0xFFE040FB.toInt()
            )
            "travel" -> DisplayInfo(
                title = "TRAVEL: ${data.get("title")?.asString ?: "Trip"}",
                subtitle = data.get("date")?.asString ?: "",
                actionLabel = "ADD TO CALENDAR →",
                accentColor = 0xFF00FF41.toInt()
            )
            else -> DisplayInfo(
                title = "SCREENSHOT ANALYZED",
                subtitle = category,
                actionLabel = "DISMISS →",
                accentColor = 0xFF00FF41.toInt()
            )
        }
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
            Bitmap.createScaledBitmap(original, 128, 128, true)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load thumbnail", e)
            null
        }
    }
}
