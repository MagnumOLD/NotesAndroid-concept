package com.magnumold.notes

import android.content.SharedPreferences
import org.json.JSONArray

/**
 * Single persistence boundary for local records.
 * Preference names and JSON keys are preserved for upgrade compatibility.
 */
internal class NotesRepository(private val preferences: SharedPreferences) {
    companion object {
        // Activity.getPreferences() uses the Activity's local class name.
        // Services and receivers must open this exact same file.
        const val PREFERENCES_NAME = "MainActivity"
    }
    fun loadSnapshot() = PersistenceSnapshot(
        notes = loadNotes(),
        collections = loadCollections(),
        archived = loadVault("archive"),
        trash = loadVault("trash")
    )

    @Synchronized
    fun saveSnapshot(snapshot: PersistenceSnapshot) {
        snapshotEditor(snapshot).apply()
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
