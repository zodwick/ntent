package com.scrnstr

import android.Manifest
import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.animation.AnimationUtils
import android.view.animation.LinearInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.motion.widget.MotionLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.JsonObject
import com.scrnstr.data.InterceptHistory
import com.scrnstr.data.InterceptRecord
import com.scrnstr.ui.RecentInterceptAdapter
import com.scrnstr.ui.TerminalTextView
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var motionLayout: MotionLayout
    private lateinit var toggleButton: TextView
    private lateinit var statusText: TerminalTextView
    private lateinit var statusCircle: View
    private lateinit var glowBackground: View
    private lateinit var idleCursorIcon: ImageView
    private lateinit var radarSweep: ImageView
    private lateinit var scanline: View
    private lateinit var recentRecyclerView: RecyclerView
    private lateinit var noInterceptsText: TextView
    private lateinit var dotStorage: View
    private lateinit var dotCalendar: View
    private lateinit var dotNotify: View
    private lateinit var dotOverlay: View

    // Quick action views
    private lateinit var quickActionInput: EditText
    private lateinit var quickActionResultCard: View
    private lateinit var quickActionAccentBar: View
    private lateinit var quickActionResultTitle: TextView
    private lateinit var quickActionResultSubtitle: TextView
    private lateinit var quickActionButton: TextView

    private lateinit var classifier: GeminiClassifier
    private lateinit var interceptHistory: InterceptHistory
    private lateinit var adapter: RecentInterceptAdapter
    private var isMonitoring = false
    private var scanlineAnimator: ObjectAnimator? = null

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
        private const val OVERLAY_PERMISSION_REQUEST_CODE = 101
    }

    private var pendingStartAfterOverlay = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        interceptHistory = InterceptHistory(this)
        classifier = GeminiClassifier(this)
        bindViews()
        setupCategoryCards()
        setupRecyclerView()
        setupToggleButton()
        setupTextInput()
        setupScanlineAnimation()
        updatePermissionDots()
        applyIdleState()
    }

    override fun onResume() {
        super.onResume()
        refreshInterceptFeed()
        updatePermissionDots()
        updateCategoryDots()

        // Check if returning from overlay permission settings
        if (pendingStartAfterOverlay) {
            pendingStartAfterOverlay = false
            if (hasPermissions()) {
                startMonitoring()
            }
        }
    }

    private fun bindViews() {
        motionLayout = findViewById(R.id.motionLayout)
        toggleButton = findViewById(R.id.toggleButton)
        statusText = findViewById(R.id.statusText)
        statusCircle = findViewById(R.id.statusCircle)
        glowBackground = findViewById(R.id.glowBackground)
        idleCursorIcon = findViewById(R.id.idleCursorIcon)
        radarSweep = findViewById(R.id.radarSweep)
        scanline = findViewById(R.id.scanline)
        recentRecyclerView = findViewById(R.id.recentRecyclerView)
        noInterceptsText = findViewById(R.id.noInterceptsText)
        dotStorage = findViewById(R.id.dotStorage)
        dotCalendar = findViewById(R.id.dotCalendar)
        dotNotify = findViewById(R.id.dotNotify)
        dotOverlay = findViewById(R.id.dotOverlay)

        quickActionInput = findViewById(R.id.quickActionInput)
        quickActionResultCard = findViewById(R.id.quickActionResultCard)
        quickActionAccentBar = findViewById(R.id.quickActionAccentBar)
        quickActionResultTitle = findViewById(R.id.quickActionResultTitle)
        quickActionResultSubtitle = findViewById(R.id.quickActionResultSubtitle)
        quickActionButton = findViewById(R.id.quickActionButton)
    }

    private fun setupCategoryCards() {
        val cardBills = findViewById<View>(R.id.cardBills)
        val cardEvents = findViewById<View>(R.id.cardEvents)
        val cardArticles = findViewById<View>(R.id.cardArticles)
        val cardFilms = findViewById<View>(R.id.cardFilms)

        setupCard(cardBills, R.drawable.ic_category_bills, getString(R.string.category_bills))
        setupCard(cardEvents, R.drawable.ic_category_events, getString(R.string.category_events))
        setupCard(cardArticles, R.drawable.ic_category_articles, getString(R.string.category_articles))
        setupCard(cardFilms, R.drawable.ic_category_films, getString(R.string.category_films))
    }

    private fun setupCard(card: View, iconRes: Int, label: String) {
        card.findViewById<ImageView>(R.id.categoryIcon).setImageResource(iconRes)
        card.findViewById<TextView>(R.id.categoryLabel).text = label
    }

    private fun updateCategoryDots() {
        val now = System.currentTimeMillis()
        val recentThreshold = 5 * 60 * 1000L // 5 minutes

        val cardMap = mapOf(
            "food_bill" to findViewById<View>(R.id.cardBills),
            "event" to findViewById<View>(R.id.cardEvents),
            "tech_article" to findViewById<View>(R.id.cardArticles),
            "movie" to findViewById<View>(R.id.cardFilms)
        )

        cardMap.forEach { (category, card) ->
            val lastTime = interceptHistory.getLastCategoryTimestamp(category)
            val dot = card.findViewById<View>(R.id.categoryDot)
            dot.visibility = if (lastTime != null && now - lastTime < recentThreshold) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = RecentInterceptAdapter()
        recentRecyclerView.layoutManager = LinearLayoutManager(this)
        recentRecyclerView.adapter = adapter
    }

    private fun refreshInterceptFeed() {
        val intercepts = interceptHistory.getIntercepts()
        adapter.updateItems(intercepts)
        noInterceptsText.visibility = if (intercepts.isEmpty()) View.VISIBLE else View.GONE
        recentRecyclerView.visibility = if (intercepts.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun setupToggleButton() {
        toggleButton.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    val press = AnimationUtils.loadAnimation(this, R.anim.button_press)
                    v.startAnimation(press)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val release = AnimationUtils.loadAnimation(this, R.anim.button_release)
                    v.startAnimation(release)
                    if (event.action == MotionEvent.ACTION_UP) {
                        v.performClick()
                    }
                }
            }
            true
        }

        toggleButton.setOnClickListener {
            if (isMonitoring) {
                stopMonitoring()
            } else {
                if (!hasPermissions()) {
                    requestPermissions()
                } else if (!hasOverlayPermission()) {
                    requestOverlayPermission()
                } else {
                    startMonitoring()
                }
            }
        }
    }

    private fun setupScanlineAnimation() {
        scanline.post {
            val parentHeight = (scanline.parent as View).height.toFloat()
            scanlineAnimator = ObjectAnimator.ofFloat(scanline, "translationY", 0f, parentHeight).apply {
                duration = 8000
                repeatCount = ObjectAnimator.INFINITE
                interpolator = LinearInterpolator()
                start()
            }
        }
    }

    private fun applyIdleState() {
        statusCircle.setBackgroundResource(R.drawable.bg_status_circle_idle)
        glowBackground.alpha = 0f
        idleCursorIcon.visibility = View.VISIBLE
        radarSweep.visibility = View.GONE
        statusText.stopDotAnimation()
        statusText.text = getString(R.string.status_dormant)
        statusText.setTextColor(ContextCompat.getColor(this, R.color.text_muted))

        val idlePulse = AnimationUtils.loadAnimation(this, R.anim.pulse_idle)
        idleCursorIcon.startAnimation(idlePulse)
    }

    private fun applyActiveState() {
        statusCircle.setBackgroundResource(R.drawable.bg_status_circle_active)
        glowBackground.alpha = 0.6f
        idleCursorIcon.clearAnimation()
        idleCursorIcon.visibility = View.GONE
        radarSweep.visibility = View.VISIBLE

        // Start radar AVD
        val avd = radarSweep.drawable
        if (avd is AnimatedVectorDrawable) {
            avd.start()
        }

        // Active pulse on glow
        val activePulse = AnimationUtils.loadAnimation(this, R.anim.pulse_active)
        glowBackground.startAnimation(activePulse)

        statusText.setTextColor(ContextCompat.getColor(this, R.color.terminal_green))
        statusText.startDotAnimation("SCANNING")
    }

    private fun hasOverlayPermission(): Boolean {
        return Settings.canDrawOverlays(this)
    }

    private fun updatePermissionDots() {
        updateDot(dotStorage, hasStoragePermission())
        updateDot(dotCalendar, hasCalendarPermission())
        updateDot(dotNotify, hasNotifyPermission())
        updateDot(dotOverlay, hasOverlayPermission())
    }

    private fun updateDot(dot: View, granted: Boolean) {
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(
                ContextCompat.getColor(
                    this@MainActivity,
                    if (granted) R.color.terminal_green else R.color.terminal_red
                )
            )
        }
        dot.background = drawable
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun hasCalendarPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasNotifyPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun hasPermissions(): Boolean {
        return getRequiredPermissions().all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun getRequiredPermissions(): List<String> {
        val permissions = mutableListOf(
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        return permissions
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            getRequiredPermissions().toTypedArray(),
            PERMISSION_REQUEST_CODE
        )
    }

    private fun requestOverlayPermission() {
        pendingStartAfterOverlay = true
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
        Toast.makeText(this, "Grant overlay permission to show floating results", Toast.LENGTH_LONG).show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            updatePermissionDots()
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                if (!hasOverlayPermission()) {
                    requestOverlayPermission()
                } else {
                    startMonitoring()
                }
            } else {
                Toast.makeText(this, "Permissions required to monitor screenshots", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startMonitoring() {
        val intent = Intent(this, ScreenshotObserverService::class.java)
        ContextCompat.startForegroundService(this, intent)
        isMonitoring = true

        // Update toggle button
        toggleButton.text = getString(R.string.toggle_deactivate)
        toggleButton.setBackgroundResource(R.drawable.bg_toggle_active)
        toggleButton.setTextColor(ContextCompat.getColor(this, R.color.surface_black))

        // MotionLayout transition to active
        motionLayout.transitionToEnd()
        applyActiveState()
    }

    private fun stopMonitoring() {
        val intent = Intent(this, ScreenshotObserverService::class.java)
        stopService(intent)
        isMonitoring = false

        // Update toggle button
        toggleButton.text = getString(R.string.toggle_activate)
        toggleButton.setBackgroundResource(R.drawable.bg_toggle_idle)
        toggleButton.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))

        // MotionLayout transition to idle
        motionLayout.transitionToStart()
        applyIdleState()
    }

    private fun setupTextInput() {
        quickActionInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val input = quickActionInput.text.toString().trim()
                if (input.isNotEmpty()) {
                    runTextClassification(input)
                }
                true
            } else {
                false
            }
        }
    }

    private fun runTextClassification(input: String) {
        // Hide keyboard
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(quickActionInput.windowToken, 0)

        // Loading state
        quickActionInput.isEnabled = false
        quickActionInput.hint = "analyzing..."
        quickActionResultCard.visibility = View.GONE

        lifecycleScope.launch {
            val result = classifier.classifyText(input)
            if (result != null) {
                val (title, subtitle, actionLabel, accentColor) = getDisplayInfo(result.category, result.data)

                quickActionResultCard.visibility = View.VISIBLE
                quickActionResultTitle.text = title
                quickActionResultSubtitle.text = subtitle
                quickActionButton.text = actionLabel
                quickActionAccentBar.setBackgroundColor(accentColor)

                quickActionButton.setOnClickListener {
                    lifecycleScope.launch {
                        try {
                            ActionExecutor.execute(this@MainActivity, result.category, result.data, Uri.EMPTY)
                        } catch (e: Exception) {
                            Toast.makeText(this@MainActivity, "Action failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                // Log to intercept history
                interceptHistory.addIntercept(
                    InterceptRecord(
                        category = result.category,
                        title = title,
                        thumbnailUri = "",
                        timestamp = System.currentTimeMillis()
                    )
                )
                refreshInterceptFeed()
                updateCategoryDots()
            } else {
                Toast.makeText(this@MainActivity, "Could not classify text", Toast.LENGTH_SHORT).show()
            }

            // Re-enable input
            quickActionInput.isEnabled = true
            quickActionInput.hint = "type anything... movie, event, reminder"
        }
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
                title = "ANALYZED",
                subtitle = category,
                actionLabel = "DISMISS →",
                accentColor = 0xFF00FF41.toInt()
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scanlineAnimator?.cancel()
    }
}
