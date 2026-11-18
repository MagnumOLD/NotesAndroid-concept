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
    (current.sessions + imported.sessions).forEach { session ->
        val previous = sessionsById[session.id]
        if (previous == null || session.endedAtMillis > previous.endedAtMillis) {
            sessionsById[session.id] = session
        }
    }
    val active = current.activeSessions.toMutableMap()
    imported.activeSessions.forEach { (taskId, session) -> active.putIfAbsent(taskId, session) }
    return TimeTrackingState(
        sessions = sessionsById.values.sortedBy { it.startedAtMillis },
        activeSessions = active,
        protectionTimer = current.protectionTimer ?: imported.protectionTimer,
        soundUri = imported.soundUri ?: current.soundUri,
        soundEnabled = imported.soundEnabled
    )
}

internal class TimeTrackingRepository(
    private val preferences: SharedPreferences
) {
    private val _state = MutableStateFlow(loadStoredState())
    val state: StateFlow<TimeTrackingState> = _state.asStateFlow()

    @Synchronized
    fun load(): TimeTrackingState = _state.value

    @Synchronized
    fun reload() {
        _state.value = loadStoredState()
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

    @Synchronized
    fun save(state: TimeTrackingState) {
        check(preferences.edit().putString(KEY_STATE, timeTrackingToJson(state).toString()).commit()) {
            "Не удалось сохранить учёт времени"
        }
        _state.value = state
    }

    @Synchronized
    fun restoreBackup(backupState: TimeTrackingState) {
        save(backupState)
    }

    @Synchronized
    fun update(transform: (TimeTrackingState) -> TimeTrackingState): TimeTrackingState {
        val updated = transform(load())
        save(updated)
        return updated
    }

    private companion object {
        const val KEY_STATE = "time_tracking_state_v1"
    }
}
