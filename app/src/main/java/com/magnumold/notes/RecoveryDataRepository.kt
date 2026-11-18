package com.magnumold.notes

import android.content.SharedPreferences

internal data class RecoveryEntry(
    val storage: String,
    val key: String,
    val raw: String
)

/**
 * Keeps quarantined source JSON in its original preferences store and carries
 * those fragments through normal backup export/import without activating them.
 */
internal class RecoveryDataRepository(
    private val recordPreferences: SharedPreferences,
    private val timeTrackingPreferences: SharedPreferences
) {
    fun exportEntries(): List<RecoveryEntry> =
        (collect(STORAGE_RECORDS, recordPreferences) +
            collect(STORAGE_TIME_TRACKING, timeTrackingPreferences))
            .sortedWith(compareBy<RecoveryEntry> { it.storage }.thenBy { it.key })

    fun importEntries(entries: List<RecoveryEntry>): Int {
        var importedCount = 0
        entries.forEach { entry ->
            require(entry.key.startsWith(RECOVERY_KEY_PREFIX)) {
                "Invalid recovery entry key"
            }
            val preferences = preferencesFor(entry.storage)
            var candidate = entry.key
            var suffix = 1
            while (preferences.contains(candidate)) {
                if (preferences.all[candidate] == entry.raw) return@forEach
                candidate = "${entry.key}_import_${suffix++}"
            }
            check(preferences.edit().putString(candidate, entry.raw).commit()) {
                "Не удалось импортировать фрагмент восстановления: ${entry.key}"
            }
            importedCount++
        }
        return importedCount
    }

    private fun collect(storage: String, preferences: SharedPreferences): List<RecoveryEntry> =
        preferences.all.mapNotNull { (key, value) ->
            if (key.startsWith(RECOVERY_KEY_PREFIX) && value is String) {
                RecoveryEntry(storage, key, value)
            } else null
        }

    private fun preferencesFor(storage: String): SharedPreferences = when (storage) {
        STORAGE_RECORDS -> recordPreferences
        STORAGE_TIME_TRACKING -> timeTrackingPreferences
        else -> error("Unknown recovery storage: $storage")
    }

    companion object {
        const val STORAGE_RECORDS = "records"
        const val STORAGE_TIME_TRACKING = "time_tracking"
        const val RECOVERY_KEY_PREFIX = "recovery_corrupt_"
    }
}
