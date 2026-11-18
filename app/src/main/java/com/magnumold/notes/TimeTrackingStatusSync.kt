package com.magnumold.notes

private data class TaskTreeEntry(
    val note: Note,
    val parentId: Long?,
    val parentTitle: String?
)

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
    fun title(note: Note): String =
        note.titleText.takeIf { note.titleEnabled && it.isNotBlank() }
            ?: note.blocks.toPlainText().lineSequence().firstOrNull()?.trim().orEmpty().ifBlank { "Задача" }

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
    if (state.protectionTimer?.taskId?.let { it in removedIds } == true) {
        state = state.copy(protectionTimer = null)
    }

    newEntries.forEach { (id, entry) ->
        val note = entry.note
        if (note.type != RecordType.TASK) {
            if (id in state.activeSessions) {
                state = TimeTrackingEngine.finish(state, id, nowMillis, WorkSessionEndReason.STOP)
            }
            if (state.protectionTimer?.taskId == id) {
                state = state.copy(protectionTimer = null)
            }
            return@forEach
        }
        val old = oldEntries[id]?.note
        // Resolve deadline-derived states at the same instant as this sync.
        // A task can become MISSED without its persisted raw status changing.
        val oldStatus = old?.recordStatus(nowMillis)
        val newStatus = note.recordStatus(nowMillis)
        if (newStatus == RecordStatus.COMPLETED && state.protectionTimer?.taskId == id) {
            state = state.copy(protectionTimer = null)
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
            state = TimeTrackingEngine.finish(state, id, nowMillis, reason)
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
 * Resumes a stopwatch after the protection timer action. Only subtasks own
 * work sessions; the old per-record opt-in flag is intentionally ignored.
 */
internal fun resumeWorkAfterProtectionTimer(
    state: TimeTrackingState,
    taskId: Long,
    parentTaskId: Long?,
    taskTitle: String,
    subtaskTitle: String?,
    nowMillis: Long
): TimeTrackingState {
    val clean = state.copy(protectionTimer = null)
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
