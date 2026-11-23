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

    fun loadDateHeaderTextSize(): Int {
        val stored = preferences.getInt(KEY_DATE_HEADER_TEXT_SIZE, DEFAULT_DATE_HEADER_TEXT_SIZE)
        if (preferences.getBoolean(KEY_DATE_HEADER_TEXT_SIZE_USER_SET, false)) {
            return stored.takeIf { it == 12 || it == 14 } ?: DEFAULT_DATE_HEADER_TEXT_SIZE
        }
        // Older versions wrote 14sp on every launch, even when the user never
        // changed the setting. Treat that unmarked value as the former default.
        if (stored == 14) {
            preferences.edit().putInt(KEY_DATE_HEADER_TEXT_SIZE, DEFAULT_DATE_HEADER_TEXT_SIZE).apply()
            return DEFAULT_DATE_HEADER_TEXT_SIZE
        }
        return stored.takeIf { it == 12 } ?: DEFAULT_DATE_HEADER_TEXT_SIZE
    }

    fun saveDateHeaderTextSize(sizeSp: Int) {
        preferences.edit()
            .putInt(KEY_DATE_HEADER_TEXT_SIZE, if (sizeSp == 14) 14 else 12)
            .putBoolean(KEY_DATE_HEADER_TEXT_SIZE_USER_SET, true)
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
        const val KEY_DATE_HEADER_TEXT_SIZE_USER_SET = "date_header_text_size_user_set"
        const val DEFAULT_DATE_HEADER_TEXT_SIZE = 12
        const val KEY_MANUAL_ORDER = "manual_order_ids"
    }
}
