package com.magnumold.notes

internal enum class KitchenTimerStatus { READY, RUNNING, PAUSED, FINISHED }

internal data class KitchenTimer(
    val id: Long,
    val title: String = "",
    val durationSeconds: Long = TimerOperations.DEFAULT_DURATION_SECONDS,
    val remainingSeconds: Long? = null,
    val endsAtMillis: Long? = null,
    val soundUri: String? = null,
    val status: KitchenTimerStatus = KitchenTimerStatus.READY,
    val alertStartedAtMillis: Long? = null,
    val colorIndex: Int = 0
) {
    fun normalized(): KitchenTimer {
        val duration = TimerOperations.normalizeDuration(durationSeconds)
        return copy(
            title = title.trim(),
            durationSeconds = duration,
            remainingSeconds = remainingSeconds?.coerceIn(0L, duration),
            endsAtMillis = endsAtMillis?.takeIf { status == KitchenTimerStatus.RUNNING },
            soundUri = soundUri?.takeIf { it.isNotBlank() },
            alertStartedAtMillis = alertStartedAtMillis?.takeIf { status == KitchenTimerStatus.FINISHED },
            colorIndex = colorIndex.coerceIn(0, 6)
        )
    }
}

internal fun KitchenTimer.toRecord(existing: Note? = null): Note {
    val recordStatus = when (status) {
        KitchenTimerStatus.READY -> RecordStatus.WAITING
        KitchenTimerStatus.RUNNING -> RecordStatus.IN_PROGRESS
        KitchenTimerStatus.PAUSED -> RecordStatus.PAUSED
        KitchenTimerStatus.FINISHED -> RecordStatus.COMPLETED
    }
    val timestamp = endsAtMillis ?: alertStartedAtMillis ?: System.currentTimeMillis()
    return Note(
        id = id,
        title = "",
        blocks = existing?.blocks ?: listOf(NoteBlock()),
        updated = timestamp,
        created = existing?.created ?: System.currentTimeMillis(),
        type = RecordType.TIMER,
        colorIndex = colorIndex,
        pinned = existing?.pinned ?: false,
        status = recordStatus,
        titleEnabled = true,
        titleText = title,
        showOnHome = existing?.showOnHome ?: true,
        favorite = existing?.favorite ?: false,
        homeOrder = existing?.homeOrder ?: existing?.created ?: System.currentTimeMillis(),
        timerDurationSeconds = durationSeconds,
        timerEndsAtMillis = endsAtMillis,
        timerRemainingSeconds = remainingSeconds,
        timerSoundUri = soundUri,
        timerAlertStartedAtMillis = alertStartedAtMillis
    )
}

internal fun Note.toKitchenTimer(): KitchenTimer {
    require(type == RecordType.TIMER)
    val timerStatus = when {
        status == RecordStatus.IN_PROGRESS && timerEndsAtMillis != null -> KitchenTimerStatus.RUNNING
        status == RecordStatus.IN_PROGRESS || status == RecordStatus.PAUSED -> KitchenTimerStatus.PAUSED
        status == RecordStatus.COMPLETED -> KitchenTimerStatus.FINISHED
        else -> KitchenTimerStatus.READY
    }
    return KitchenTimer(
        id = id,
        title = titleText,
        durationSeconds = timerDurationSeconds,
        remainingSeconds = if (timerStatus == KitchenTimerStatus.PAUSED) {
            timerRemainingSeconds ?: timerDurationSeconds
        } else timerRemainingSeconds,
        endsAtMillis = timerEndsAtMillis,
        soundUri = timerSoundUri,
        status = timerStatus,
        alertStartedAtMillis = timerAlertStartedAtMillis,
        colorIndex = colorIndex
    ).normalized()
}

internal enum class TimerAlertDurationMode { TEN_SECONDS, THIRTY_SECONDS, ONE_MINUTE, CUSTOM }

internal data class KitchenTimerSettings(
    val alertDurationMode: TimerAlertDurationMode = TimerAlertDurationMode.THIRTY_SECONDS,
    val customAlertDurationSeconds: Int = 30,
    val defaultSoundUri: String? = null
) {
    fun normalized() = copy(
        customAlertDurationSeconds = customAlertDurationSeconds.coerceIn(1, 3600),
        defaultSoundUri = defaultSoundUri?.takeIf { it.isNotBlank() }
    )

    fun alertDurationSeconds(): Int = when (alertDurationMode) {
        TimerAlertDurationMode.TEN_SECONDS -> 10
        TimerAlertDurationMode.THIRTY_SECONDS -> 30
        TimerAlertDurationMode.ONE_MINUTE -> 60
        TimerAlertDurationMode.CUSTOM -> normalized().customAlertDurationSeconds
    }
}

internal object KitchenTimerOperations {
    fun create(id: Long, title: String = "") = KitchenTimer(id = id, title = title)

    // KitchenTimer is a presentation/legacy adapter only. All state transitions
    // are performed by RecordTimerOperations on the canonical Note record.
    fun start(timer: KitchenTimer, nowMillis: Long): KitchenTimer =
        RecordTimerOperations.start(timer.toRecord(), nowMillis).toKitchenTimer()

    fun pause(timer: KitchenTimer, nowMillis: Long): KitchenTimer =
        RecordTimerOperations.pause(timer.toRecord(), nowMillis).toKitchenTimer()

    fun reset(timer: KitchenTimer): KitchenTimer =
        RecordTimerOperations.reset(timer.toRecord()).toKitchenTimer()

    fun updateDuration(timer: KitchenTimer, durationSeconds: Long): KitchenTimer =
        RecordTimerOperations.updateDuration(timer.toRecord(), durationSeconds).toKitchenTimer()

    fun remainingSeconds(timer: KitchenTimer, nowMillis: Long): Long =
        RecordTimerOperations.remainingSeconds(timer.toRecord(), nowMillis)

    fun reconcile(timer: KitchenTimer, nowMillis: Long): KitchenTimer =
        RecordTimerOperations.reconcile(timer.toRecord(), nowMillis).toKitchenTimer()

    fun format(seconds: Long): String = TimerOperations.format(seconds)
}
