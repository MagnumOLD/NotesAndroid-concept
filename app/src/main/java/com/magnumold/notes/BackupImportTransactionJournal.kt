package com.magnumold.notes

import android.content.SharedPreferences
import org.json.JSONObject

/**
 * Durable write-ahead journal for imports spanning the records and tracking
 * preference files. A process death before journal removal is recovered by
 * restoring the exact pre-import values on the next app launch.
 */
internal class BackupImportTransactionJournal(
    private val recordPreferences: SharedPreferences,
    private val trackingPreferences: SharedPreferences,
    private val journalPreferences: SharedPreferences
) {
    fun <T> execute(block: () -> T): T = synchronized(LOCK) {
        val before = capture()
        check(journalPreferences.edit().putString(JOURNAL_KEY, before.toString()).commit()) {
            "Не удалось подготовить безопасный импорт резервной копии"
        }
        try {
            val result = block()
            check(journalPreferences.edit().remove(JOURNAL_KEY).commit()) {
                "Не удалось завершить транзакцию импорта"
            }
            result
        } catch (failure: Throwable) {
            runCatching { restore(before) }
                .exceptionOrNull()?.let(failure::addSuppressed)
            if (runCatching { clearJournal() }.isFailure) {
                failure.addSuppressed(IllegalStateException("Не удалось очистить журнал импорта"))
            }
            throw failure
        }
    }

    /** Must run before repositories load state or background services start. */
    fun recoverIfNeeded() = synchronized(LOCK) {
        val raw = journalPreferences.getString(JOURNAL_KEY, null) ?: return@synchronized
        val journal = JSONObject(raw)
        restore(journal)
        clearJournal()
    }

    private fun capture(): JSONObject {
        val records = JSONObject()
        val tracking = JSONObject()
        val recordKeys = recordPreferences.all.keys.filter(::isRecordDataKey).toSet() +
            RECORD_DATA_KEYS.filter(recordPreferences::contains)
        val trackingKeys = trackingPreferences.all.keys.filter(::isRecoveryKey).toSet() +
            listOf(TRACKING_STATE_KEY).filter(trackingPreferences::contains)
        recordKeys.forEach { key ->
            records.put(key, recordPreferences.getString(key, null) ?: JSONObject.NULL)
        }
        trackingKeys.forEach { key ->
            tracking.put(key, trackingPreferences.getString(key, null) ?: JSONObject.NULL)
        }
        return JSONObject().put("records", records).put("tracking", tracking)
    }

    private fun restore(journal: JSONObject) {
        restoreStore(recordPreferences, journal.getJSONObject("records"), ::isRecordDataKey, RECORD_DATA_KEYS)
        restoreStore(
            trackingPreferences,
            journal.getJSONObject("tracking"),
            { it == TRACKING_STATE_KEY || isRecoveryKey(it) },
            listOf(TRACKING_STATE_KEY)
        )
    }

    private fun restoreStore(
        preferences: SharedPreferences,
        saved: JSONObject,
        isManagedKey: (String) -> Boolean,
        fixedKeys: List<String>
    ) {
        val editor = preferences.edit()
        (preferences.all.keys.filter(isManagedKey) + fixedKeys).toSet().forEach(editor::remove)
        saved.keys().forEach { key ->
            val value = saved.get(key)
            if (value != JSONObject.NULL) editor.putString(key, value as String)
        }
        check(editor.commit()) { "Не удалось восстановить данные после прерванного импорта" }
    }

    private fun clearJournal() {
        check(journalPreferences.edit().remove(JOURNAL_KEY).commit()) {
            "Не удалось очистить журнал импорта"
        }
    }

    private fun isRecordDataKey(key: String): Boolean =
        key in RECORD_DATA_KEYS || isRecoveryKey(key)

    private fun isRecoveryKey(key: String): Boolean =
        key.startsWith(RecoveryDataRepository.RECOVERY_KEY_PREFIX)

    private companion object {
        const val JOURNAL_KEY = "active_backup_import_v1"
        const val TRACKING_STATE_KEY = "time_tracking_state_v1"
        val RECORD_DATA_KEYS = listOf("notes", "collections", "archive", "trash")
        val LOCK = Any()
    }
}
