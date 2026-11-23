package com.magnumold.notes

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Single persistence boundary for local records.
 * Preference names and JSON keys are preserved for upgrade compatibility.
 */
internal class NotesRepository(private val preferences: SharedPreferences) {
    companion object {
        // Activity.getPreferences() uses the Activity's local class name.
        // Services and receivers must open this exact same file.
        const val PREFERENCES_NAME = "MainActivity"
        private const val KEY_KITCHEN_TIMER_MIGRATION = "kitchen_timers_migrated_v1"
        const val KEY_KITCHEN_TIMER_LEGACY_ID_MAP = "kitchen_timer_legacy_id_map"
    }
    fun loadSnapshot() = PersistenceSnapshot(
        notes = loadNotes(),
        collections = loadCollections(),
        archived = loadVault("archive"),
        trash = loadVault("trash"),
        nutritionProducts = loadJsonArray("nutrition_products", emptyList()) { NutritionCatalogOperations.parseProducts(it) },
        timerSettings = loadTimerSettings()
    )

    @Synchronized
    fun saveSnapshot(snapshot: PersistenceSnapshot) {
        snapshotEditor(snapshot).apply()
    }

    /**
     * One-time, crash-safe migration from the legacy kitchen-timer store.
     * The records and migration marker are committed in the same preferences
     * transaction; legacy timer JSON is removed only after that commit succeeds.
     */
    @Synchronized
    fun migrateLegacyKitchenTimers(legacyPreferences: SharedPreferences) {
        if (preferences.getBoolean(KEY_KITCHEN_TIMER_MIGRATION, false)) {
            // Retry cleanup if the process was interrupted after the main-store commit.
            legacyPreferences.edit()
                .remove("kitchen_timers_v1")
                .remove("kitchen_timer_next_id")
                .remove("kitchen_timer_alert_mode")
                .remove("kitchen_timer_custom_alert_seconds")
                .remove("kitchen_timer_default_sound_uri")
                .commit()
            return
        }

        val legacyRaw = legacyPreferences.getString("kitchen_timers_v1", null)
        val current = loadSnapshot().copy(
            timerSettings = KitchenTimerRepository(legacyPreferences).loadSettings()
        )
        val legacyIdMap = linkedMapOf<Long, Long>()
        // An empty legacy store means there is nothing to migrate.
        // Never synthesize a timer during first launch or after the last timer was deleted.
        val migrated = if (legacyRaw.isNullOrBlank()) current else {
            val legacy = KitchenTimerRepository(legacyPreferences).load().timers
            val usedIds = mutableSetOf<Long>()
            fun collect(notes: List<Note>) {
                notes.forEach { note -> usedIds += note.id; collect(note.children) }
            }
            collect(current.notes)
            current.collections.forEach { collect(it.notes) }
            (current.archived + current.trash).forEach { collect(listOf(it.note)) }
            val converted = legacy.map { timer ->
                val safeId = if (timer.id > 0L && usedIds.add(timer.id)) timer.id
                    else newRecordId().also { usedIds.add(it) }
                legacyIdMap[timer.id] = safeId
                timer.copy(id = safeId).toRecord().copy(
                    created = timer.id,
                    homeOrder = timer.id,
                    updated = timer.endsAtMillis ?: timer.alertStartedAtMillis ?: timer.id
                )
            }
            current.copy(notes = converted + current.notes)
        }

        val legacyIdMapJson = JSONObject().apply {
            legacyIdMap.forEach { (legacyId, recordId) -> put(legacyId.toString(), recordId) }
        }.toString()
        check(snapshotEditor(migrated)
            .putString(KEY_KITCHEN_TIMER_LEGACY_ID_MAP, legacyIdMapJson)
            .putBoolean(KEY_KITCHEN_TIMER_MIGRATION, true)
            .commit()) { "Не удалось перенести таймеры в основное хранилище" }

        // Timer settings were committed with the migrated records above.
        // Remove the legacy copy only after that transaction succeeds.
        legacyPreferences.edit()
            .remove("kitchen_timers_v1")
            .remove("kitchen_timer_next_id")
            .remove("kitchen_timer_alert_mode")
            .remove("kitchen_timer_custom_alert_seconds")
            .remove("kitchen_timer_default_sound_uri")
            .commit()
    }

    @Synchronized
    fun flushSnapshot(snapshot: PersistenceSnapshot) {
        check(snapshotEditor(snapshot).commit()) {
            "Не удалось сохранить записи"
        }
    }

    private fun snapshotEditor(snapshot: PersistenceSnapshot): SharedPreferences.Editor =
        preferences.edit()
            .putString("notes", notesToJson(snapshot.notes).toString())
            .putString("collections", collectionsToJson(snapshot.collections).toString())
            .putString("archive", vaultToJson(snapshot.archived).toString())
            .putString("trash", vaultToJson(snapshot.trash).toString())
            .putString("nutrition_products", NutritionCatalogOperations.productsToJson(snapshot.nutritionProducts).toString())
            .putString("timer_alert_mode", snapshot.timerSettings.alertDurationMode.name)
            .putInt("timer_custom_alert_seconds", snapshot.timerSettings.customAlertDurationSeconds)
            .putString("timer_default_sound_uri", snapshot.timerSettings.defaultSoundUri)

    private fun loadTimerSettings(): KitchenTimerSettings = runCatching {
        KitchenTimerSettings(
            alertDurationMode = preferences.getString("timer_alert_mode", null)
                ?.let { TimerAlertDurationMode.valueOf(it) }
                ?: TimerAlertDurationMode.THIRTY_SECONDS,
            customAlertDurationSeconds = preferences.getInt("timer_custom_alert_seconds", 30),
            defaultSoundUri = preferences.getString("timer_default_sound_uri", null)
        ).normalized()
    }.getOrDefault(KitchenTimerSettings())

    fun findTask(taskId: Long): Note? {
        val snapshot = loadSnapshot()
        fun find(items: List<Note>): Note? {
            items.forEach { note ->
                if (note.id == taskId) return note
                find(note.children)?.let { return it }
            }
            return null
        }
        return find(snapshot.notes)
            ?: find(snapshot.collections.flatMap { it.notes })
            ?: find(snapshot.archived.map { it.note })
            ?: find(snapshot.trash.map { it.note })
    }

    fun updateTaskStatus(taskId: Long, status: RecordStatus): Boolean {
        val current = loadSnapshot()
        var found = false
        fun updateTree(items: List<Note>): List<Note> = items.map { note ->
            val updatedChildren = updateTree(note.children)
            val withUpdatedChildren = if (updatedChildren != note.children) {
                withUpdatedRecordChildren(note, updatedChildren)
            } else note
            if (note.id == taskId && note.type == RecordType.TASK) {
                found = true
                withUpdatedChildren.copy(
                    status = status,
                    deadlineResumeOverride = status == RecordStatus.IN_PROGRESS,
                    children = updatedChildren
                )
            } else withUpdatedChildren
        }
        val updatedNotes = updateTree(current.notes)
        val updatedCollections = current.collections.map { collection ->
            collection.copy(notes = updateTree(collection.notes))
        }
        fun updateVault(entries: List<VaultNote>): List<VaultNote> =
            entries.map { entry ->
                val updated = updateTree(listOf(entry.note)).single()
                if (updated == entry.note) entry else entry.copy(note = updated)
            }
        val updatedArchive = updateVault(current.archived)
        val updatedTrash = updateVault(current.trash)
        if (found) {
            flushSnapshot(current.copy(
                notes = updatedNotes,
                collections = updatedCollections,
                archived = updatedArchive,
                trash = updatedTrash
            ))
            preferences.edit()
                .putLong(ExternalRecordUpdateKeys.TASK_STATUS_AT, System.currentTimeMillis())
                .putLong(ExternalRecordUpdateKeys.TASK_STATUS_ID, taskId)
                .putString(ExternalRecordUpdateKeys.TASK_STATUS_VALUE, status.name)
                .apply()
        }
        return found
    }

    private fun <T> loadJsonArray(
        key: String,
        emptyValue: T,
        parse: (JSONArray) -> T
    ): T {
        val raw = preferences.getString(key, "[]") ?: "[]"
        return try {
            parse(JSONArray(raw))
        } catch (_: Exception) {
            preferences.preserveCorruptJson(key, raw, "[]")
            emptyValue
        }
    }

    private fun loadNotes(): List<Note> =
        loadJsonArray("notes", emptyList(), ::parseNotesArray)

    private fun loadCollections(): List<NoteCollection> =
        loadJsonArray("collections", emptyList(), ::parseCollectionsArray)

    private fun loadVault(key: String): List<VaultNote> =
        loadJsonArray(key, emptyList(), ::parseVaultArray)

    fun markExternalTimerStateUpdate() {
        preferences.edit()
            .putLong(ExternalRecordUpdateKeys.TIMER_STATE_AT, System.currentTimeMillis())
            .apply()
    }
}

internal object ExternalRecordUpdateKeys {
    const val TASK_STATUS_AT = "external_task_status_update"
    const val TASK_STATUS_ID = "external_task_status_update_id"
    const val TASK_STATUS_VALUE = "external_task_status_update_value"
    const val TIMER_STATE_AT = "external_timer_state_update"
}

/**
 * Keep the exact unreadable value before allowing a caller to continue with an
 * empty fallback. This prevents the next normal save from destroying the only
 * copy of the user's damaged data.
 */
internal fun SharedPreferences.preserveCorruptJson(sourceKey: String, raw: String, replacementValue: String) {
    val prefix = "recovery_corrupt_${sourceKey}_"
    val timestamp = System.currentTimeMillis()
    var recoveryKey = prefix + timestamp
    var suffix = 1
    while (contains(recoveryKey)) {
        recoveryKey = prefix + timestamp + "_" + suffix++
    }
    check(edit().putString(recoveryKey, raw).putString(sourceKey, replacementValue).commit()) {
        "Не удалось сохранить повреждённые данные для восстановления: $sourceKey"
    }
}
