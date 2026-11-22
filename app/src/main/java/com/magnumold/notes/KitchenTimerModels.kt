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
    val alertStartedAtMillis: Long? = null
) {
    fun normalized(): KitchenTimer {
        val duration = TimerOperations.normalizeDuration(durationSeconds)
        return copy(
            title = title.trim(),
            durationSeconds = duration,
            remainingSeconds = remainingSeconds?.coerceIn(0L, duration),
            endsAtMillis = endsAtMillis?.takeIf { status == KitchenTimerStatus.RUNNING },
            soundUri = soundUri?.takeIf { it.isNotBlank() },
            alertStartedAtMillis = alertStartedAtMillis?.takeIf { status == KitchenTimerStatus.FINISHED }
        )
    }
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

    fun start(timer: KitchenTimer, nowMillis: Long): KitchenTimer {
        val remaining = when (timer.status) {
            KitchenTimerStatus.PAUSED -> timer.remainingSeconds ?: timer.durationSeconds
            KitchenTimerStatus.RUNNING -> TimerOperations.remainingSeconds(timer.endsAtMillis ?: nowMillis, nowMillis)
            KitchenTimerStatus.READY, KitchenTimerStatus.FINISHED -> timer.durationSeconds
        }.coerceAtLeast(1L)
        return timer.copy(
            remainingSeconds = null,
            endsAtMillis = TimerOperations.startAt(nowMillis, remaining),
            status = KitchenTimerStatus.RUNNING,
            alertStartedAtMillis = null
        )
    }

    fun pause(timer: KitchenTimer, nowMillis: Long): KitchenTimer {
        if (timer.status != KitchenTimerStatus.RUNNING) return timer
        val remaining = TimerOperations.remainingSeconds(timer.endsAtMillis ?: nowMillis, nowMillis)
        return timer.copy(
            remainingSeconds = remaining,
            endsAtMillis = null,
            status = if (remaining == 0L) KitchenTimerStatus.FINISHED else KitchenTimerStatus.PAUSED
        )
    }

    fun reset(timer: KitchenTimer): KitchenTimer = timer.copy(
        remainingSeconds = null,
        endsAtMillis = null,
        status = KitchenTimerStatus.READY,
        alertStartedAtMillis = null
    )

    fun updateDuration(timer: KitchenTimer, durationSeconds: Long): KitchenTimer {
        val duration = TimerOperations.normalizeDuration(durationSeconds)
        val now = System.currentTimeMillis()
        return timer.copy(
            durationSeconds = duration,
            remainingSeconds = if (timer.status == KitchenTimerStatus.PAUSED) duration else null,
            endsAtMillis = if (timer.status == KitchenTimerStatus.RUNNING) {
                TimerOperations.startAt(now, duration)
            } else null,
            status = if (timer.status == KitchenTimerStatus.FINISHED) KitchenTimerStatus.READY else timer.status,
            alertStartedAtMillis = if (timer.status == KitchenTimerStatus.FINISHED) null else timer.alertStartedAtMillis
        )
    }

    fun remainingSeconds(timer: KitchenTimer, nowMillis: Long): Long = when (timer.status) {
        KitchenTimerStatus.RUNNING -> TimerOperations.remainingSeconds(timer.endsAtMillis ?: nowMillis, nowMillis)
        KitchenTimerStatus.PAUSED -> timer.remainingSeconds ?: 0L
        KitchenTimerStatus.READY -> timer.durationSeconds
        KitchenTimerStatus.FINISHED -> 0L
    }

    fun reconcile(timer: KitchenTimer, nowMillis: Long): KitchenTimer {
        if (timer.status != KitchenTimerStatus.RUNNING) return timer
        if (remainingSeconds(timer, nowMillis) > 0L) return timer
        return timer.copy(
            remainingSeconds = 0L,
            endsAtMillis = null,
            status = KitchenTimerStatus.FINISHED
        )
    }

    fun format(seconds: Long): String = TimerOperations.format(seconds)
}
