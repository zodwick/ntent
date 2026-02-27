package com.scrnstr.actions

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BillOrganizer {

    private const val TAG = "BillOrganizer"

    suspend fun organize(context: Context, screenshotUri: Uri) {
        withContext(Dispatchers.IO) {
            try {
                val dateFolder = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
                val fileName = "bill_${System.currentTimeMillis()}.png"
                val relativePath = "${Environment.DIRECTORY_PICTURES}/Bills/$dateFolder"

                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
                }

                val destUri = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
                )

                if (destUri != null) {
                    context.contentResolver.openInputStream(screenshotUri)?.use { input ->
                        context.contentResolver.openOutputStream(destUri)?.use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.d(TAG, "Bill saved to $relativePath/$fileName")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Bill organized", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.e(TAG, "Failed to create destination URI")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error organizing bill", e)
            }
        }
    }
}
