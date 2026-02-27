package com.scrnstr

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val category = intent.getStringExtra("category") ?: return
        val dataJson = intent.getStringExtra("data") ?: return
        val screenshotUriStr = intent.getStringExtra("screenshotUri") ?: return

        val data = Gson().fromJson(dataJson, JsonObject::class.java)
        val screenshotUri = Uri.parse(screenshotUriStr)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ActionExecutor.execute(context, category, data, screenshotUri)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
