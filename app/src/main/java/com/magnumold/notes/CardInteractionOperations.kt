package com.magnumold.notes

internal object CardInteractionOperations {
    fun applyQuickAction(note: Note, nowMillis: Long): Note? = when (note.type) {
        RecordType.TASK, RecordType.PURCHASE, RecordType.WORKOUT -> note.copy(
            status = if (note.status == RecordStatus.COMPLETED) RecordStatus.WAITING else RecordStatus.COMPLETED,
            deadlineResumeOverride = false
        )
        RecordType.TIMER -> {
            val endAt = note.timerEndsAtMillis
            if (endAt != null) {
                note.copy(
                    timerEndsAtMillis = null,
                    timerRemainingSeconds = TimerOperations.remainingSeconds(endAt, nowMillis),
                    status = RecordStatus.PAUSED
                )
            } else {
                val remaining = note.timerRemainingSeconds?.takeIf { it > 0L }
                    ?: note.timerDurationSeconds
                note.copy(
                    timerEndsAtMillis = TimerOperations.startAt(nowMillis, remaining),
                    timerRemainingSeconds = null,
                    status = RecordStatus.IN_PROGRESS
                )
            }
        }
        else -> null
    }
}
