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

    /**
     * Plans every destination before writing, then commits each backing store
     * as a batch. If a later store fails, keys written to earlier stores are
     * removed again so a failed backup import does not leave half its recovery
     * fragments behind.
     */
    fun importEntries(entries: List<RecoveryEntry>): Int {
        entries.forEach { entry ->
            require(entry.key.startsWith(RECOVERY_KEY_PREFIX)) {
                "Invalid recovery entry key"
            }
            preferencesFor(entry.storage)
        }

        val planned = linkedMapOf(
            STORAGE_RECORDS to linkedMapOf<String, String>(),
            STORAGE_TIME_TRACKING to linkedMapOf<String, String>()
        )
        entries.forEach { entry ->
            val preferences = preferencesFor(entry.storage)
            val destination = planned.getValue(entry.storage)
            var candidate = entry.key
            var suffix = 1
            while (preferences.contains(candidate) || destination.containsKey(candidate)) {
                if (preferences.all[candidate] == entry.raw || destination[candidate] == entry.raw) {
                    return@forEach
                }
                candidate = "${entry.key}_import_${suffix++}"
            }
            destination[candidate] = entry.raw
        }

        val attempted = mutableListOf<Pair<SharedPreferences, Set<String>>>()
        var importedCount = 0
        try {
            planned.forEach { (storage, values) ->
                if (values.isEmpty()) return@forEach
                val preferences = preferencesFor(storage)
                attempted += preferences to values.keys.toSet()
                val editor = preferences.edit()
                values.forEach { (key, raw) -> editor.putString(key, raw) }
                check(editor.commit()) {
                    "Не удалось импортировать фрагменты восстановления: $storage"
                }
                importedCount += values.size
            }
        } catch (failure: Exception) {
            attempted.asReversed().forEach { (preferences, keys) ->
                runCatching {
                    val editor = preferences.edit()
                    keys.forEach(editor::remove)
                    check(editor.commit()) { "Не удалось отменить импорт фрагментов восстановления" }
                }.exceptionOrNull()?.let(failure::addSuppressed)
            }
            throw failure
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
