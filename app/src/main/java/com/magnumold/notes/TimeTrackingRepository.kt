package com.magnumold.notes

import android.content.SharedPreferences
import org.json.JSONObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Durable store for time tracking. Unlike debounced note autosave, timer/session
 * transitions are committed synchronously so a process kill cannot discard them.
 */
internal fun mergeTimeTrackingBackup(
    current: TimeTrackingState,
    imported: TimeTrackingState
): TimeTrackingState {
    val sessionsById = linkedMapOf<String, WorkSession>()
    current.sessions.forEach { sessionsById[it.id] = it }

    fun uniqueSessionId(original: String, used: Set<String>): String {
        var suffix = 1
        var candidate = "$original-imported-$suffix"
        while (candidate in used) {
            suffix++
            candidate = "$original-imported-$suffix"
        }
        return candidate
    }

    imported.sessions.forEach { session ->
        val previous = sessionsById[session.id]
        if (previous == null) {
            sessionsById[session.id] = session
        } else if (
            previous.taskId == session.taskId &&
            previous.parentTaskId == session.parentTaskId &&
            previous.startedAtMillis == session.startedAtMillis
        ) {
            // Same logical session from a newer backup: retain its latest saved revision.
            if (session.endedAtMillis > previous.endedAtMillis) {
                sessionsById[session.id] = session
            }
        } else {
            // Different work intervals must never overwrite one another just because
            // independently-created backups happen to contain the same UUID.
            val newId = uniqueSessionId(session.id, sessionsById.keys)
            sessionsById[newId] = session.copy(id = newId)
        }
    }

    val active = current.activeSessions.toMutableMap()
    val usedSessionIds = sessionsById.keys.toMutableSet().apply {
        addAll(active.values.map { it.id })
    }
    imported.activeSessions.forEach { (taskId, session) ->
        if (taskId !in active) {
            val safeSession = if (session.id in usedSessionIds) {
                val newId = uniqueSessionId(session.id, usedSessionIds)
                session.copy(id = newId)
            } else session
            active[taskId] = safeSession
            usedSessionIds += safeSession.id
        }
    }
    return TimeTrackingState(
        sessions = sessionsById.values.sortedBy { it.startedAtMillis },
        activeSessions = active,
        protectionTimer = current.protectionTimer ?: imported.protectionTimer,
        firedProtectionAlert = current.firedProtectionAlert,
        soundUri = imported.soundUri ?: current.soundUri,
        soundEnabled = imported.soundEnabled
    )
}

internal fun remapTimeTrackingTaskIds(
    state: TimeTrackingState,
    importedNoteIdMap: Map<Long, Long>
): TimeTrackingState {
    if (importedNoteIdMap.isEmpty()) return state
    fun mapped(id: Long): Long = importedNoteIdMap[id] ?: id
    fun mappedParent(id: Long?): Long? = id?.let(::mapped)
    return state.copy(
        sessions = state.sessions.map { session ->
            session.copy(
                taskId = mapped(session.taskId),
                parentTaskId = mappedParent(session.parentTaskId)
            )
        },
        activeSessions = state.activeSessions.values.associate { session ->
            val taskId = mapped(session.taskId)
            taskId to session.copy(
                taskId = taskId,
                parentTaskId = mappedParent(session.parentTaskId)
            )
        },
        protectionTimer = state.protectionTimer?.let { timer ->
            timer.copy(taskId = mapped(timer.taskId), parentTaskId = mappedParent(timer.parentTaskId))
        },
        firedProtectionAlert = state.firedProtectionAlert?.let { timer ->
            timer.copy(taskId = mapped(timer.taskId), parentTaskId = mappedParent(timer.parentTaskId))
        }
    )
}

/**
 * Applies only fields changed by a UI event to the latest persisted state.
 * This prevents a stale Compose snapshot from overwriting service-side timer
 * or session transitions that happened while the screen was open.
 */
internal fun mergeTimeTrackingUiChange(
    current: TimeTrackingState,
    previousUiState: TimeTrackingState,
    requestedUiState: TimeTrackingState
): TimeTrackingState {
    fun mergeSessions(
        latest: List<WorkSession>,
        previous: List<WorkSession>,
        requested: List<WorkSession>
    ): List<WorkSession> {
        val previousById = previous.associateBy { it.id }
        val requestedById = requested.associateBy { it.id }
        val changedIds = (previousById.keys + requestedById.keys)
            .filter { previousById[it] != requestedById[it] }
        if (changedIds.isEmpty()) return latest
        val result = latest.toMutableList()
        changedIds.forEach { id ->
            val replacement = requestedById[id]
            val index = result.indexOfFirst { it.id == id }
            when {
                replacement == null && index >= 0 -> result.removeAt(index)
                replacement != null && index >= 0 -> result[index] = replacement
                replacement != null -> result += replacement
            }
        }
        return result.sortedBy { it.startedAtMillis }
    }

    val activeSessions = current.activeSessions.toMutableMap()
    (previousUiState.activeSessions.keys + requestedUiState.activeSessions.keys)
        .filter { previousUiState.activeSessions[it] != requestedUiState.activeSessions[it] }
        .forEach { taskId ->
            val requested = requestedUiState.activeSessions[taskId]
            if (requested == null) activeSessions.remove(taskId)
            else activeSessions[taskId] = requested
        }

    return current.copy(
        sessions = mergeSessions(current.sessions, previousUiState.sessions, requestedUiState.sessions),
        activeSessions = activeSessions,
        protectionTimer = if (requestedUiState.protectionTimer != previousUiState.protectionTimer) {
            requestedUiState.protectionTimer
        } else current.protectionTimer,
        soundUri = if (requestedUiState.soundUri != previousUiState.soundUri) {
            requestedUiState.soundUri
        } else current.soundUri,
        soundEnabled = if (requestedUiState.soundEnabled != previousUiState.soundEnabled) {
            requestedUiState.soundEnabled
        } else current.soundEnabled,
        firedProtectionAlert = if (requestedUiState.firedProtectionAlert != previousUiState.firedProtectionAlert) {
            requestedUiState.firedProtectionAlert
        } else current.firedProtectionAlert
    )
}

internal class TimeTrackingRepository(
    private val preferences: SharedPreferences
) {
    private val _state = MutableStateFlow(loadStoredState())
    val state: StateFlow<TimeTrackingState> = _state.asStateFlow()

    fun load(): TimeTrackingState = synchronized(LOCK) { _state.value }

    fun reload() {
        synchronized(LOCK) {
            _state.value = loadStoredState()
        }
    }

    private fun loadStoredState(): TimeTrackingState {
        val raw = preferences.getString(KEY_STATE, null) ?: return TimeTrackingState()
        return try {
            parseTimeTrackingJson(JSONObject(raw))
        } catch (_: Exception) {
            preferences.preserveCorruptJson(KEY_STATE, raw, "{}")
            TimeTrackingState()
        }
    }

    fun save(state: TimeTrackingState) {
        synchronized(LOCK) {
            check(preferences.edit().putString(KEY_STATE, timeTrackingToJson(state).toString()).commit()) {
                "Не удалось сохранить учёт времени"
            }
            _state.value = state
        }
    }

    fun restoreBackup(backupState: TimeTrackingState) {
        save(backupState)
    }

    /** Rolls back an import only if no newer tracking update has replaced it. */
    fun restoreBackupIfCurrent(
        expectedCurrent: TimeTrackingState,
        restoreState: TimeTrackingState
    ): Boolean = synchronized(LOCK) {
        val latest = loadStoredState()
        _state.value = latest
        if (latest != expectedCurrent) return@synchronized false
        save(restoreState)
        true
    }

    fun update(transform: (TimeTrackingState) -> TimeTrackingState): TimeTrackingState =
        synchronized(LOCK) {
            // Each component may own a repository instance. Refresh from the
            // shared durable value before a read-modify-write so a service
            // cannot overwrite a timer created by the Activity a moment earlier.
            val latest = loadStoredState()
            _state.value = latest
            val updated = transform(latest)
            save(updated)
            updated
        }

    private companion object {
        const val KEY_STATE = "time_tracking_state_v1"
        val LOCK = Any()
    }
}
