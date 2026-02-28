package com.scrnstr.actions

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.wifi.WifiNetworkSuggestion
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object WifiConnector {

    private const val TAG = "WifiConnector"

    suspend fun connect(context: Context, data: JsonObject) {
        val ssid = data.get("ssid")?.asString ?: return
        val password = data.get("password")?.asString ?: ""
        val security = data.get("security")?.asString?.uppercase() ?: "WPA2"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            withContext(Dispatchers.IO) {
                try {
                    val builder = WifiNetworkSuggestion.Builder()
                        .setSsid(ssid)

                    when {
                        security.contains("WPA3") -> builder.setWpa3Passphrase(password)
                        security.contains("OPEN") || password.isBlank() -> { /* open network */ }
                        else -> builder.setWpa2Passphrase(password)
                    }

                    val suggestion = builder.build()
                    val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
                    val status = wifiManager.addNetworkSuggestions(listOf(suggestion))

                    val msg = if (status == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) {
                        "WiFi suggestion added for $ssid"
                    } else {
                        "WiFi suggestion failed (code $status)"
                    }
                    Log.d(TAG, msg)

                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error adding WiFi suggestion", e)
                }
            }
        } else {
            // Fallback: copy password to clipboard
            withContext(Dispatchers.Main) {
                try {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("WiFi Password", password))
                    Toast.makeText(context, "WiFi password copied for $ssid", Toast.LENGTH_SHORT).show()
                    Log.d(TAG, "Copied WiFi password for $ssid (API < 29 fallback)")
                } catch (e: Exception) {
                    Log.e(TAG, "Error copying WiFi password", e)
                }
            }
        }
    }
}
