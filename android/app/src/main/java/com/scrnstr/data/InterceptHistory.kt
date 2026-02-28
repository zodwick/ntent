package com.scrnstr.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class InterceptRecord(
    val category: String,
    val title: String,
    val thumbnailUri: String,
    val timestamp: Long
)

class InterceptHistory(context: Context) {

    companion object {
        private const val PREFS_NAME = "scrnstr_intercepts"
        private const val KEY_INTERCEPTS = "recent_intercepts"
        private const val MAX_ITEMS = 3
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun addIntercept(record: InterceptRecord) {
        val list = getIntercepts().toMutableList()
        list.add(0, record)
        if (list.size > MAX_ITEMS) {
            list.subList(MAX_ITEMS, list.size).clear()
        }
        prefs.edit().putString(KEY_INTERCEPTS, gson.toJson(list)).apply()
    }

    fun getIntercepts(): List<InterceptRecord> {
        val json = prefs.getString(KEY_INTERCEPTS, null) ?: return emptyList()
        val type = object : TypeToken<List<InterceptRecord>>() {}.type
        return try {
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getLastCategoryTimestamp(category: String): Long? {
        return getIntercepts().firstOrNull { it.category == category }?.timestamp
    }
}
