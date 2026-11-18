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

    fun startAt(nowMillis: Long, remainingSeconds: Long): Long =
        nowMillis + normalizeDuration(remainingSeconds) * 1_000L

    fun format(seconds: Long): String {
        val safe = seconds.coerceAtLeast(0L)
        val hours = safe / 3_600L
        val minutes = (safe % 3_600L) / 60L
        val remainder = safe % 60L
        return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, remainder)
        else "%02d:%02d".format(minutes, remainder)
    }
}
