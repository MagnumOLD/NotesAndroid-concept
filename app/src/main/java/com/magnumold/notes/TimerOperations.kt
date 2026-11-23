package com.magnumold.notes

/** Pure countdown rules shared by the timer editor and unit tests. */
internal object TimerOperations {
    const val MIN_DURATION_SECONDS = 1L
    const val MAX_DURATION_SECONDS = 86_400L
    const val DEFAULT_DURATION_SECONDS = 300L

    fun normalizeDuration(seconds: Long): Long =
        seconds.coerceIn(MIN_DURATION_SECONDS, MAX_DURATION_SECONDS)

    /** A completed timer starts from its full configured duration again. */
    fun durationToStart(remainingSeconds: Long?, durationSeconds: Long): Long {
        val duration = normalizeDuration(durationSeconds)
        return remainingSeconds?.takeIf { it > 0L }?.coerceAtMost(duration) ?: duration
    }

    fun remainingSeconds(endsAtMillis: Long, nowMillis: Long): Long {
        if (endsAtMillis <= nowMillis) return 0L

        // Saturate subtraction when timestamps span the signed Long range.
        // This also avoids turning an already-expired negative end timestamp
        // into a huge positive duration through arithmetic underflow.
        val remainingMillis = if (
            nowMillis < 0L && endsAtMillis > Long.MAX_VALUE + nowMillis
        ) Long.MAX_VALUE else endsAtMillis - nowMillis

        // Divide first: adding 999 before division overflows for very large
        // imported/restored end timestamps near Long.MAX_VALUE.
        return remainingMillis / 1_000L + if (remainingMillis % 1_000L == 0L) 0L else 1L
    }

    fun startAt(nowMillis: Long, remainingSeconds: Long): Long {
        val durationMillis = normalizeDuration(remainingSeconds) * 1_000L
        return if (nowMillis > Long.MAX_VALUE - durationMillis) Long.MAX_VALUE
        else nowMillis + durationMillis
    }

    fun format(seconds: Long): String {
        val safe = seconds.coerceAtLeast(0L)
        val hours = safe / 3_600L
        val minutes = (safe % 3_600L) / 60L
        val remainder = safe % 60L
        return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, remainder)
        else "%02d:%02d".format(minutes, remainder)
    }
}

/** Canonical state transitions for every persisted countdown timer record. */
internal object RecordTimerOperations {
    fun remainingSeconds(note: Note, nowMillis: Long = System.currentTimeMillis()): Long = when {
        note.timerEndsAtMillis != null ->
            TimerOperations.remainingSeconds(note.timerEndsAtMillis, nowMillis)
        note.status == RecordStatus.PAUSED || note.status == RecordStatus.IN_PROGRESS ->
            (note.timerRemainingSeconds ?: note.timerDurationSeconds).coerceAtLeast(0L)
        note.status == RecordStatus.COMPLETED -> 0L
        else -> TimerOperations.normalizeDuration(note.timerDurationSeconds)
    }

    fun start(note: Note, nowMillis: Long = System.currentTimeMillis()): Note {
        require(note.type == RecordType.TIMER)
        val duration = TimerOperations.normalizeDuration(note.timerDurationSeconds)
        val remaining = when {
            note.timerEndsAtMillis != null ->
                TimerOperations.remainingSeconds(note.timerEndsAtMillis, nowMillis).takeIf { it > 0L } ?: duration
            note.status == RecordStatus.PAUSED || note.status == RecordStatus.IN_PROGRESS ->
                note.timerRemainingSeconds?.takeIf { it > 0L } ?: duration
            else -> duration
        }
        return note.copy(
            timerDurationSeconds = duration,
            timerEndsAtMillis = TimerOperations.startAt(nowMillis, remaining),
            timerRemainingSeconds = null,
            timerAlertStartedAtMillis = null,
            status = RecordStatus.IN_PROGRESS,
            updated = nowMillis
        )
    }

    fun pause(note: Note, nowMillis: Long = System.currentTimeMillis()): Note {
        require(note.type == RecordType.TIMER)
        val endAt = note.timerEndsAtMillis ?: return note
        val remaining = TimerOperations.remainingSeconds(endAt, nowMillis)
        return note.copy(
            timerEndsAtMillis = null,
            timerRemainingSeconds = remaining,
            timerAlertStartedAtMillis = if (remaining == 0L) nowMillis else null,
            status = if (remaining == 0L) RecordStatus.COMPLETED else RecordStatus.PAUSED,
            updated = nowMillis
        )
    }

    fun reset(note: Note, nowMillis: Long = System.currentTimeMillis()): Note {
        require(note.type == RecordType.TIMER)
        return note.copy(
            timerEndsAtMillis = null,
            timerRemainingSeconds = null,
            timerAlertStartedAtMillis = null,
            status = RecordStatus.WAITING,
            updated = nowMillis
        )
    }

    fun updateDuration(
        note: Note,
        durationSeconds: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): Note {
        require(note.type == RecordType.TIMER)
        val duration = TimerOperations.normalizeDuration(durationSeconds)
        val running = note.timerEndsAtMillis != null
        return note.copy(
            timerDurationSeconds = duration,
            timerRemainingSeconds = if (!running && (note.status == RecordStatus.PAUSED || note.status == RecordStatus.IN_PROGRESS)) duration else null,
            timerEndsAtMillis = if (running) TimerOperations.startAt(nowMillis, duration) else null,
            timerAlertStartedAtMillis = if (note.status == RecordStatus.COMPLETED) null
                else note.timerAlertStartedAtMillis,
            status = when {
                note.status == RecordStatus.COMPLETED -> RecordStatus.WAITING
                !running && note.status == RecordStatus.IN_PROGRESS -> RecordStatus.PAUSED
                else -> note.status
            },
            updated = nowMillis
        )
    }

    fun reconcile(note: Note, nowMillis: Long = System.currentTimeMillis()): Note {
        require(note.type == RecordType.TIMER)
        val endAt = note.timerEndsAtMillis ?: return note
        if (TimerOperations.remainingSeconds(endAt, nowMillis) > 0L) return note
        return note.copy(
            timerEndsAtMillis = null,
            timerRemainingSeconds = 0L,
            timerAlertStartedAtMillis = note.timerAlertStartedAtMillis ?: nowMillis,
            status = RecordStatus.COMPLETED,
            updated = nowMillis
        )
    }
}
