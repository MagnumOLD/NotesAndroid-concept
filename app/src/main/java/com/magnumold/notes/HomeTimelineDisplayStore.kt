package com.magnumold.notes

import android.content.SharedPreferences
import org.json.JSONArray

/**
 * Stores home timeline presentation independently from record data.
 * The date-grouped order remains in each record's homeOrder field; this store
 * owns only the optional global manual order and the date-header visibility.
 */
internal class HomeTimelineDisplayStore(private val preferences: SharedPreferences) {
    fun loadShowDateHeaders(): Boolean = preferences.getBoolean(KEY_SHOW_DATE_HEADERS, true)

    fun loadDateHeaderTextSize(): Int =
        preferences.getInt(KEY_DATE_HEADER_TEXT_SIZE, DEFAULT_DATE_HEADER_TEXT_SIZE)
            .takeIf { it == 12 || it == 14 } ?: DEFAULT_DATE_HEADER_TEXT_SIZE

    fun saveDateHeaderTextSize(sizeSp: Int) {
        preferences.edit()
            .putInt(KEY_DATE_HEADER_TEXT_SIZE, if (sizeSp == 12) 12 else DEFAULT_DATE_HEADER_TEXT_SIZE)
            .apply()
    }

    fun saveShowDateHeaders(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_SHOW_DATE_HEADERS, enabled).apply()
    }

    /**
     * Null means the user has never entered manual mode. An empty list is a
     * valid, initialized order for an empty notebook.
     */
    fun loadManualOrder(): List<Long>? {
        if (!preferences.contains(KEY_MANUAL_ORDER)) return null
        return try {
            val array = JSONArray(preferences.getString(KEY_MANUAL_ORDER, "[]") ?: "[]")
            (0 until array.length()).mapNotNull { index ->
                runCatching { array.getLong(index) }.getOrNull()
            }
        } catch (_: Exception) {
            null
        }
    }

    fun saveManualOrder(ids: List<Long>) {
        val array = JSONArray()
        ids.distinct().forEach { array.put(it) }
        preferences.edit().putString(KEY_MANUAL_ORDER, array.toString()).apply()
    }

    private companion object {
        const val KEY_SHOW_DATE_HEADERS = "show_date_headers"
        const val KEY_DATE_HEADER_TEXT_SIZE = "date_header_text_size"
        const val DEFAULT_DATE_HEADER_TEXT_SIZE = 14
        const val KEY_MANUAL_ORDER = "manual_order_ids"
    }
}
