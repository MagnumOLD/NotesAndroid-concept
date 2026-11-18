package com.magnumold.notes

import android.content.SharedPreferences

/**
 * Controls how long deleted records remain recoverable in the trash.
 * A value of zero disables automatic expiration.
 */
internal class TrashRetentionStore(private val preferences: SharedPreferences) {
    fun loadDays(): Int = preferences.getInt(KEY_DAYS, DEFAULT_DAYS).coerceIn(0, MAX_DAYS)

    fun saveDays(days: Int) {
        preferences.edit().putInt(KEY_DAYS, days.coerceIn(0, MAX_DAYS)).apply()
    }

    private companion object {
        const val KEY_DAYS = "trash_retention_days"
        const val DEFAULT_DAYS = 30
        const val MAX_DAYS = 365
    }
}
