package com.magnumold.notes

import org.json.JSONArray
import org.json.JSONObject

internal fun timeTrackingToJson(state: TimeTrackingState): JSONObject {
    val sessions = JSONArray()
    state.sessions.forEach { s ->
        sessions.put(JSONObject()
            .put("id", s.id).put("taskId", s.taskId)
            .put("parentTaskId", s.parentTaskId ?: JSONObject.NULL)
            .put("taskTitle", s.taskTitle).put("subtaskTitle", s.subtaskTitle ?: JSONObject.NULL)
            .put("startedAt", s.startedAtMillis).put("endedAt", s.endedAtMillis)
            .put("duration", s.durationMillis).put("endReason", s.endReason.name))
    }
    val active = JSONArray()
    state.activeSessions.values.forEach { s ->
        active.put(JSONObject()
            .put("id", s.id).put("taskId", s.taskId)
            .put("parentTaskId", s.parentTaskId ?: JSONObject.NULL)
            .put("taskTitle", s.taskTitle).put("subtaskTitle", s.subtaskTitle ?: JSONObject.NULL)
            .put("startedAt", s.startedAtMillis))
    }
    fun timerToJson(t: ProtectionTimer) = JSONObject()
        .put("taskId", t.taskId).put("parentTaskId", t.parentTaskId ?: JSONObject.NULL).put("taskTitle", t.taskTitle)
        .put("subtaskTitle", t.subtaskTitle ?: JSONObject.NULL).put("startedAt", t.startedAtMillis)
        .put("endsAt", t.endsAtMillis).put("endMode", t.endMode.name)
        .put("soundUri", t.soundUri ?: JSONObject.NULL).put("soundEnabled", t.soundEnabled)
    val timer = state.protectionTimer?.let(::timerToJson) ?: JSONObject.NULL
    val firedAlert = state.firedProtectionAlert?.let(::timerToJson) ?: JSONObject.NULL
    return JSONObject().put("sessions", sessions).put("activeSessions", active).put("protectionTimer", timer)
        .put("firedProtectionAlert", firedAlert)
        .put("pendingProtectionTaskPauseId", state.pendingProtectionTaskPauseId ?: JSONObject.NULL)
        .put("soundUri", state.soundUri ?: JSONObject.NULL).put("soundEnabled", state.soundEnabled)
}

internal fun parseTimeTrackingJson(json: JSONObject?): TimeTrackingState {
    if (json == null) return TimeTrackingState()
    fun arrayOrEmpty(key: String): JSONArray =
        if (!json.has(key) || json.isNull(key)) JSONArray() else json.getJSONArray(key)

    val sessionsArray = arrayOrEmpty("sessions")
    val sessions = (0 until sessionsArray.length()).map { index ->
        val o = sessionsArray.getJSONObject(index)
        WorkSession(
            id = o.getString("id"),
            taskId = o.getLong("taskId"),
            parentTaskId = if (o.isNull("parentTaskId")) null else o.getLong("parentTaskId"),
            taskTitle = o.getString("taskTitle"),
            subtaskTitle = if (o.isNull("subtaskTitle")) null else o.getString("subtaskTitle"),
            startedAtMillis = o.getLong("startedAt"),
            endedAtMillis = o.getLong("endedAt"),
            durationMillis = o.getLong("duration"),
            endReason = WorkSessionEndReason.valueOf(o.getString("endReason"))
        )
    }
    val savedSessionIds = sessions.mapTo(mutableSetOf()) { it.id }
    require(savedSessionIds.size == sessions.size) {
        "Duplicate saved time-tracking session ID"
    }
    val activeArray = arrayOrEmpty("activeSessions")
    val active = linkedMapOf<Long, ActiveWorkSession>()
    val usedSessionIds = savedSessionIds.toMutableSet()
    (0 until activeArray.length()).forEach { index ->
        val o = activeArray.getJSONObject(index)
        val item = ActiveWorkSession(
            id = o.getString("id"),
            taskId = o.getLong("taskId"),
            parentTaskId = if (o.isNull("parentTaskId")) null else o.getLong("parentTaskId"),
            taskTitle = o.getString("taskTitle"),
            subtaskTitle = if (o.isNull("subtaskTitle")) null else o.getString("subtaskTitle"),
            startedAtMillis = o.getLong("startedAt")
        )
        require(usedSessionIds.add(item.id)) {
            "Duplicate time-tracking session ID: ${item.id}"
        }
        require(active.put(item.taskId, item) == null) {
            "Duplicate active time-tracking task: ${item.taskId}"
        }
    }
    fun parseTimer(key: String): ProtectionTimer? {
        if (!json.has(key) || json.isNull(key)) return null
        val o = json.getJSONObject(key)
        return ProtectionTimer(
            taskId = o.getLong("taskId"), taskTitle = o.getString("taskTitle"),
            subtaskTitle = if (o.isNull("subtaskTitle")) null else o.getString("subtaskTitle"),
            startedAtMillis = o.getLong("startedAt"), endsAtMillis = o.getLong("endsAt"),
            endMode = ProtectionEndMode.valueOf(o.getString("endMode")),
            soundUri = if (o.isNull("soundUri")) null else o.getString("soundUri"),
            soundEnabled = o.optBoolean("soundEnabled", true),
            parentTaskId = if (o.isNull("parentTaskId")) null else o.getLong("parentTaskId")
        )
    }
    val timer = parseTimer("protectionTimer")
    val firedAlert = parseTimer("firedProtectionAlert")
    val soundUri = if (json.isNull("soundUri")) null else json.optString("soundUri").takeIf { it.isNotBlank() }
    val pendingPauseId = if (json.has("pendingProtectionTaskPauseId") && !json.isNull("pendingProtectionTaskPauseId")) {
        json.getLong("pendingProtectionTaskPauseId")
    } else null
    return TimeTrackingState(sessions, active, timer, soundUri, json.optBoolean("soundEnabled", true), firedAlert, pendingPauseId)
}
