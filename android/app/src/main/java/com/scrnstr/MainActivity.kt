package com.scrnstr

import android.Manifest
import android.animation.ObjectAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.animation.AnimationUtils
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.motion.widget.MotionLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.scrnstr.data.InterceptHistory
import com.scrnstr.ui.RecentInterceptAdapter
import com.scrnstr.ui.TerminalTextView

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

    private lateinit var interceptHistory: InterceptHistory
    private lateinit var adapter: RecentInterceptAdapter
    private var isMonitoring = false
    private var scanlineAnimator: ObjectAnimator? = null

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        interceptHistory = InterceptHistory(this)
        bindViews()
        setupCategoryCards()
        setupRecyclerView()
        setupToggleButton()
        setupScanlineAnimation()
        updatePermissionDots()
        applyIdleState()
    }

    override fun onResume() {
        super.onResume()
        refreshInterceptFeed()
        updatePermissionDots()
        updateCategoryDots()
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
                if (hasPermissions()) {
                    startMonitoring()
                } else {
                    requestPermissions()
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

    private fun updatePermissionDots() {
        updateDot(dotStorage, hasStoragePermission())
        updateDot(dotCalendar, hasCalendarPermission())
        updateDot(dotNotify, hasNotifyPermission())
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

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            updatePermissionDots()
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startMonitoring()
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

    override fun onDestroy() {
        super.onDestroy()
        scanlineAnimator?.cancel()
    }
}
