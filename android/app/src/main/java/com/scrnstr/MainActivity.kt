package com.scrnstr

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    private lateinit var toggleButton: MaterialButton
    private lateinit var statusText: TextView
    private var isMonitoring = false

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        toggleButton = findViewById(R.id.toggleButton)
        statusText = findViewById(R.id.statusText)

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

    private fun hasPermissions(): Boolean {
        val permissions = getRequiredPermissions()
        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun getRequiredPermissions(): List<String> {
        val permissions = mutableListOf(
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR
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
        toggleButton.text = getString(R.string.stop_monitoring)
        statusText.text = getString(R.string.status_monitoring)
    }

    private fun stopMonitoring() {
        val intent = Intent(this, ScreenshotObserverService::class.java)
        stopService(intent)
        isMonitoring = false
        toggleButton.text = getString(R.string.start_monitoring)
        statusText.text = getString(R.string.status_idle)
    }
}
