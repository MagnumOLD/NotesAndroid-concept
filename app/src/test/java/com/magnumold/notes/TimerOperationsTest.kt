package com.magnumold.notes

import org.junit.Assert.assertEquals
import org.junit.Test

class TimerOperationsTest {
    @Test
    fun remainingSecondsRoundsUpAndNeverGoesNegative() {
        assertEquals(2L, TimerOperations.remainingSeconds(2_001L, 500L))
        assertEquals(1L, TimerOperations.remainingSeconds(1_001L, 500L))
        assertEquals(0L, TimerOperations.remainingSeconds(499L, 500L))
    }

    @Test
    fun remainingSecondsRoundsLargeTimestampsWithoutOverflow() {
        assertEquals(
            Long.MAX_VALUE / 1_000L + 1L,
            TimerOperations.remainingSeconds(Long.MAX_VALUE, 0L)
        )
    }

    @Test
    fun durationIsClampedToOneDay() {
        assertEquals(1L, TimerOperations.normalizeDuration(0L))
        assertEquals(86_400L, TimerOperations.normalizeDuration(90_000L))
    }

    @Test
    fun startAtUsesRemainingSeconds() {
        assertEquals(15_000L, TimerOperations.startAt(5_000L, 10L))
    }

    @Test
    fun formattingUsesHoursOnlyWhenNeeded() {
        assertEquals("04:09", TimerOperations.format(249L))
        assertEquals("1:01:01", TimerOperations.format(3_661L))
    }
}
