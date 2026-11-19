package com.magnumold.notes

private data class TaskTreeEntry(
    val note: Note,
    val parentId: Long?,
    val parentTitle: String?
)

internal data class TaskTrackingContext(
    val task: Note,
    val parent: Note?
)

internal fun findTaskTrackingContext(
    roots: List<Note>,
    taskId: Long
): TaskTrackingContext? {
    fun find(note: Note, parent: Note?): TaskTrackingContext? {
        if (note.id == taskId) return TaskTrackingContext(note, parent)
        note.children.forEach { child ->
            find(child, note)?.let { return it }
        }
        return null
    }
    roots.forEach { root -> find(root, null)?.let { return it } }
    return null
}

internal fun Note.trackingDisplayTitle(): String =
    titleText.takeIf { titleEnabled && it.isNotBlank() }
        ?: blocks.toPlainText().lineSequence().firstOrNull()?.trim().orEmpty().ifBlank { "Задача" }

/** Returns the first instant after the selected local deadline day (DST-safe). */
internal fun deadlineEndExclusiveMillis(
    dateAt: Long,
    timeZone: java.util.TimeZone = java.util.TimeZone.getDefault()
): Long {
    val utcDate = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = dateAt
    }
    return java.util.Calendar.getInstance(timeZone).apply {
        clear()
        set(
            utcDate.get(java.util.Calendar.YEAR),
            utcDate.get(java.util.Calendar.MONTH),
            utcDate.get(java.util.Calendar.DAY_OF_MONTH),
            0, 0, 0
        )
        add(java.util.Calendar.DAY_OF_MONTH, 1)
    }.timeInMillis
}

/**
 * Applies the approved status-to-stopwatch transitions to a changed note tree.
 * A task with children is a parent and never owns a stopwatch.
 */
internal fun syncWorkSessionsForTaskChanges(
    oldRoots: List<Note>,
    newRoots: List<Note>,
    currentState: TimeTrackingState,
    nowMillis: Long
): TimeTrackingState {
    fun title(note: Note): String = note.trackingDisplayTitle()

    fun flatten(roots: List<Note>): Map<Long, TaskTreeEntry> {
        val result = linkedMapOf<Long, TaskTreeEntry>()
        fun visit(note: Note, parentId: Long?, parentTitle: String?) {
            result[note.id] = TaskTreeEntry(note, parentId, parentTitle)
            note.children.forEach { child -> visit(child, note.id, title(note)) }
        }
        roots.forEach { visit(it, null, null) }
        return result
    }

    val oldEntries = flatten(oldRoots)
    val newEntries = flatten(newRoots)
    var state = currentState

    // Deleting a task closes any live session but keeps its title snapshot in history.
    val removedIds = oldEntries.keys - newEntries.keys
    removedIds.forEach { removedId ->
        state = TimeTrackingEngine.finish(state, removedId, nowMillis, WorkSessionEndReason.STOP)
    }
    if (state.protectionTimer?.taskId?.let { it in removedIds } == true ||
        state.firedProtectionAlert?.taskId?.let { it in removedIds } == true
    ) {
        state = state.copy(
            protectionTimer = state.protectionTimer?.takeUnless { it.taskId in removedIds },
            firedProtectionAlert = state.firedProtectionAlert?.takeUnless { it.taskId in removedIds }
        )
    }

    newEntries.forEach { (id, entry) ->
        val note = entry.note
        if (note.type != RecordType.TASK) {
            if (id in state.activeSessions) {
                state = TimeTrackingEngine.finish(state, id, nowMillis, WorkSessionEndReason.STOP)
            }
            if (state.protectionTimer?.taskId == id || state.firedProtectionAlert?.taskId == id) {
                state = state.copy(
                    protectionTimer = state.protectionTimer?.takeUnless { it.taskId == id },
                    firedProtectionAlert = state.firedProtectionAlert?.takeUnless { it.taskId == id }
                )
            }
            return@forEach
        }
        val old = oldEntries[id]?.note
        // Resolve deadline-derived states at the same instant as this sync.
        // A task can become MISSED without its persisted raw status changing.
        val oldStatus = old?.recordStatus(nowMillis)
        val newStatus = note.recordStatus(nowMillis)
        if (newStatus == RecordStatus.COMPLETED &&
            (state.protectionTimer?.taskId == id || state.firedProtectionAlert?.taskId == id)
        ) {
            state = state.copy(
                protectionTimer = state.protectionTimer?.takeUnless { it.taskId == id },
                firedProtectionAlert = state.firedProtectionAlert?.takeUnless { it.taskId == id }
            )
        }

        val previousParentId = oldEntries[id]?.parentId
        val parentChanged = previousParentId != entry.parentId
        val ownTitle = title(note)
        state.protectionTimer?.takeIf { it.taskId == id && it.parentTaskId != entry.parentId }?.let { timer ->
            state = state.copy(
                protectionTimer = timer.copy(
                    parentTaskId = entry.parentId,
                    taskTitle = if (entry.parentId == null) ownTitle else entry.parentTitle ?: "Задача",
                    subtaskTitle = if (entry.parentId == null) null else ownTitle
                )
            )
        }
        if (id in state.activeSessions &&
            (entry.parentId == null || parentChanged || newStatus != RecordStatus.IN_PROGRESS)
        ) {
            val reason = when (newStatus) {
                RecordStatus.COMPLETED -> WorkSessionEndReason.TASK_COMPLETED
                RecordStatus.PAUSED -> WorkSessionEndReason.PAUSE
                else -> WorkSessionEndReason.STOP
            }
            // If a date-only deadline expired while the app/device was inactive,
            // close the stopwatch at the actual local-day boundary, not at the
            // later time when reconciliation happened (which would count idle time).
            val endedAt = if (
                newStatus == RecordStatus.MISSED &&
                note.dateMode == RecordDateMode.DEADLINE &&
                note.dateAt != null
            ) deadlineEndExclusiveMillis(note.dateAt)
            else nowMillis
            state = TimeTrackingEngine.finish(state, id, endedAt, reason)
        }

        val shouldStart = entry.parentId != null && newStatus == RecordStatus.IN_PROGRESS &&
            (old == null || oldStatus != RecordStatus.IN_PROGRESS || parentChanged)
        if (shouldStart && id !in state.activeSessions) {
            val parentId = entry.parentId
            state = TimeTrackingEngine.start(
                state = state,
                taskId = id,
                parentTaskId = parentId,
                taskTitle = if (parentId == null) ownTitle else entry.parentTitle ?: "Задача",
                subtaskTitle = if (parentId == null) null else ownTitle,
                nowMillis = nowMillis
            )
        }
    }
    return state
}

/**
 * Reconciles sessions after the device has booted. Boot-time splitting is done
 * by the receiver first; this pass then applies task changes that happened
 * while the device was powered off (notably date-only deadline expiry).
 */
internal fun reconcileBootTimeTrackingState(
    records: List<Note>,
    state: TimeTrackingState,
    nowMillis: Long
): TimeTrackingState = syncWorkSessionsForTaskChanges(records, records, state, nowMillis)

/**
 * Resumes a stopwatch after the protection timer action. Only subtasks own
 * work sessions; the old per-record opt-in flag is intentionally ignored.
 */
/** Clears only the matching protection timer when its task no longer exists. */
internal fun clearProtectionTimerForTask(
    state: TimeTrackingState,
    taskId: Long
): TimeTrackingState =
    state.copy(
        protectionTimer = state.protectionTimer?.takeUnless { it.taskId == taskId },
        firedProtectionAlert = state.firedProtectionAlert?.takeUnless { it.taskId == taskId }
    )

/**
 * Rejects stale notification actions when a newer protection alert/timer has
 * replaced the one the notification was created for. A missing persisted timer
 * is expected after the alarm has fired and the alert is being shown.
 */
internal fun isProtectionTimerActionCurrent(
    activeAlert: ProtectionTimer?,
    persistedTimer: ProtectionTimer?,
    taskId: Long,
    startedAtMillis: Long?,
    endsAtMillis: Long?,
    persistedFiredAlert: ProtectionTimer? = null
): Boolean {
    fun matches(timer: ProtectionTimer): Boolean =
        timer.taskId == taskId &&
            startedAtMillis != null &&
            endsAtMillis != null &&
            timer.startedAtMillis == startedAtMillis &&
            timer.endsAtMillis == endsAtMillis

    if (persistedTimer != null) return false
    if (activeAlert != null) return activeAlert == persistedFiredAlert && matches(activeAlert)
    return persistedFiredAlert?.let(::matches) ?: false
}

/** Completes an expired protection timer only if it is still the exact timer that fired. */
internal fun completeProtectionTimerIfCurrent(
    state: TimeTrackingState,
    expectedTimer: ProtectionTimer,
    nowMillis: Long
): TimeTrackingState {
    if (state.protectionTimer != expectedTimer) return state
    return when (expectedTimer.endMode) {
        ProtectionEndMode.STOP_AND_PAUSE -> TimeTrackingEngine.finish(
            state,
            expectedTimer.taskId,
            nowMillis,
            WorkSessionEndReason.AUTOMATIC_PAUSE
        ).copy(protectionTimer = null, firedProtectionAlert = expectedTimer)
        ProtectionEndMode.SIGNAL_ONLY -> state.copy(protectionTimer = null, firedProtectionAlert = expectedTimer)
    }
}

/** Stops the requested task and clears a protection timer only when it belongs to that task. */
internal fun stopWorkAfterProtectionTimer(
    state: TimeTrackingState,
    taskId: Long,
    nowMillis: Long
): TimeTrackingState = clearProtectionTimerForTask(
    TimeTrackingEngine.finish(state, taskId, nowMillis, WorkSessionEndReason.STOP),
    taskId
).copy(firedProtectionAlert = null)

internal fun resumeWorkAfterProtectionTimer(
    state: TimeTrackingState,
    taskId: Long,
    parentTaskId: Long?,
    taskTitle: String,
    subtaskTitle: String?,
    nowMillis: Long
): TimeTrackingState {
    val clean = state.copy(protectionTimer = null, firedProtectionAlert = null)
    if (parentTaskId == null || taskId in clean.activeSessions) return clean
    return TimeTrackingEngine.start(
        state = clean,
        taskId = taskId,
        parentTaskId = parentTaskId,
        taskTitle = taskTitle,
        subtaskTitle = subtaskTitle,
        nowMillis = nowMillis
    )
}
