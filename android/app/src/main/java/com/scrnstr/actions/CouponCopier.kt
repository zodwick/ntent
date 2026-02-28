package com.scrnstr.actions

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.widget.Toast
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CouponCopier {

    private const val TAG = "CouponCopier"

    suspend fun copy(context: Context, data: JsonObject) {
        val code = data.get("code")?.asString ?: return
        val platform = data.get("platform")?.asString ?: ""
        val label = if (platform.isNotEmpty()) "Coupon ($platform)" else "Coupon Code"

        withContext(Dispatchers.Main) {
            try {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText(label, code))
                Toast.makeText(context, "Coupon copied: $code", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "Copied coupon: $code")
            } catch (e: Exception) {
                Log.e(TAG, "Error copying coupon", e)
            }
        }
    }
}
