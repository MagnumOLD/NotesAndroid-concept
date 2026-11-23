package com.magnumold.notes

import android.content.SharedPreferences

/**
 * Stores the record type created by the main home-screen add button.
 * A missing or invalid preference always preserves the standard Note default.
 */
internal class HomeAddRecordTypeStore(private val preferences: SharedPreferences) {
    fun load(): RecordType {
        val stored = runCatching { preferences.getString(KEY_RECORD_TYPE, null) }.getOrNull()
            ?: return RecordType.NOTE
        return runCatching { RecordType.valueOf(stored) }.getOrDefault(RecordType.NOTE)
    }

    fun save(recordType: RecordType) {
        preferences.edit().putString(KEY_RECORD_TYPE, recordType.name).apply()
    }

    private companion object {
        const val KEY_RECORD_TYPE = "home_add_record_type"
    }
}
