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
        assertEquals(
            Long.MAX_VALUE / 1_000L + 1L,
            TimerOperations.remainingSeconds(Long.MAX_VALUE, Long.MIN_VALUE)
        )
    }

    @Test
    fun expiredNegativeEndTimestampNeverWrapsIntoPositiveDuration() {
        assertEquals(0L, TimerOperations.remainingSeconds(Long.MIN_VALUE, 1L))
        assertEquals(0L, TimerOperations.remainingSeconds(Long.MIN_VALUE, Long.MAX_VALUE))
    }

    @Test
    fun durationIsClampedToOneDay() {
        assertEquals(1L, TimerOperations.normalizeDuration(0L))
        assertEquals(86_400L, TimerOperations.normalizeDuration(90_000L))
    }

    @Test
    fun completedOrUnsetTimerRestartsAtFullDuration() {
        assertEquals(300L, TimerOperations.durationToStart(0L, 300L))
        assertEquals(300L, TimerOperations.durationToStart(null, 300L))
    }

    @Test
    fun pausedTimerResumesRemainingTimeWithoutExceedingConfiguredDuration() {
        assertEquals(45L, TimerOperations.durationToStart(45L, 300L))
        assertEquals(300L, TimerOperations.durationToStart(500L, 300L))
    }

    @Test
    fun startAtUsesRemainingSeconds() {
        assertEquals(15_000L, TimerOperations.startAt(5_000L, 10L))
    }


    @Test
    fun startAtSaturatesInsteadOfWrappingAtTimestampLimit() {
        assertEquals(Long.MAX_VALUE, TimerOperations.startAt(Long.MAX_VALUE - 500L, 1L))
        assertEquals(Long.MIN_VALUE + 1_000L, TimerOperations.startAt(Long.MIN_VALUE, 1L))
    }

    @Test
    fun formattingUsesHoursOnlyWhenNeeded() {
        assertEquals("04:09", TimerOperations.format(249L))
        assertEquals("1:01:01", TimerOperations.format(3_661L))
    }

    @Test
    fun simultaneousRecordTimersHaveDistinctStableNotificationTags() {
        val first = recordTimerNotificationTag(101L)
        val second = recordTimerNotificationTag(202L)

        assertEquals("record-timer:101", first)
        assertEquals("record-timer:101", recordTimerNotificationTag(101L))
        org.junit.Assert.assertNotEquals(first, second)
        assertEquals("record-timer:-202", recordTimerNotificationTag(-202L))
    }

}
