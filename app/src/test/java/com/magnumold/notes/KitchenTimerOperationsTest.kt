package com.magnumold.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KitchenTimerOperationsTest {
    @Test fun startPauseAndResumeAreIndependentTimestampTransitions() {
        val timer = KitchenTimer(id = 1L, durationSeconds = 120L)
        val started = KitchenTimerOperations.start(timer, 10_000L)
        assertEquals(130_000L, started.endsAtMillis)
        val paused = KitchenTimerOperations.pause(started, 40_000L)
        assertEquals(90L, paused.remainingSeconds)
        assertNull(paused.endsAtMillis)
        assertEquals(KitchenTimerStatus.PAUSED, paused.status)
        val resumed = KitchenTimerOperations.start(paused, 50_000L)
        assertEquals(140_000L, resumed.endsAtMillis)
        assertEquals(KitchenTimerStatus.RUNNING, resumed.status)
    }

    @Test fun runningTimerFinishesWhenItsDeadlineHasPassed() {
        val timer = KitchenTimer(id = 2L, durationSeconds = 60L)
        val started = KitchenTimerOperations.start(timer, 1_000L)
        val finished = KitchenTimerOperations.reconcile(started, 61_000L)
        assertEquals(KitchenTimerStatus.FINISHED, finished.status)
        assertEquals(0L, KitchenTimerOperations.remainingSeconds(finished, 61_000L))
        assertNull(finished.endsAtMillis)
    }

    @Test fun resetAndDurationUpdatePreserveExpectedState() {
        val timer = KitchenTimer(id = 3L, durationSeconds = 90L)
        val paused = KitchenTimerOperations.pause(KitchenTimerOperations.start(timer, 0L), 20_000L)
        val changed = KitchenTimerOperations.updateDuration(paused, 180L)
        assertEquals(180L, changed.durationSeconds)
        assertEquals(KitchenTimerStatus.PAUSED, changed.status)
        assertEquals(180L, changed.remainingSeconds)
        assertEquals(KitchenTimerStatus.READY, KitchenTimerOperations.reset(changed).status)
    }

    @Test fun kitchenTimersConvertToCanonicalTimerRecords() {
        val ready = KitchenTimer(
            id = 1_700_000_000_000L,
            title = "Гречка",
            durationSeconds = 900L,
            colorIndex = 3
        ).toRecord()
        assertEquals(1_700_000_000_000L, ready.id)
        assertEquals(RecordType.TIMER, ready.type)
        assertEquals("Гречка", ready.titleText)
        assertEquals(3, ready.colorIndex)
        assertEquals(RecordStatus.WAITING, ready.status)
        assertEquals(900L, ready.timerDurationSeconds)

        val paused = KitchenTimer(
            id = 1_700_000_000_001L,
            durationSeconds = 900L,
            remainingSeconds = 420L,
            status = KitchenTimerStatus.PAUSED
        ).toRecord()
        assertEquals(RecordStatus.PAUSED, paused.status)
        assertEquals(420L, paused.timerRemainingSeconds)

        val running = KitchenTimer(
            id = 1_700_000_000_002L,
            durationSeconds = 900L,
            endsAtMillis = 1_700_000_000_900L,
            status = KitchenTimerStatus.RUNNING
        ).toRecord()
        assertEquals(RecordStatus.IN_PROGRESS, running.status)
        assertEquals(1_700_000_000_900L, running.timerEndsAtMillis)
    }

    @Test fun alertDurationHasOnlyTheApprovedPresetsAndCustomValue() {
        assertEquals(10, KitchenTimerSettings(TimerAlertDurationMode.TEN_SECONDS).alertDurationSeconds())
        assertEquals(30, KitchenTimerSettings(TimerAlertDurationMode.THIRTY_SECONDS).alertDurationSeconds())
        assertEquals(60, KitchenTimerSettings(TimerAlertDurationMode.ONE_MINUTE).alertDurationSeconds())
        assertEquals(45, KitchenTimerSettings(TimerAlertDurationMode.CUSTOM, 45).alertDurationSeconds())
        assertTrue(KitchenTimerSettings(TimerAlertDurationMode.CUSTOM, 99_999).alertDurationSeconds() <= 3600)
    }

    @Test fun multipleTimersKeepSeparateDeadlines() {
        val first = KitchenTimerOperations.start(KitchenTimer(id = 1L, durationSeconds = 120L), 1_000L)
        val second = KitchenTimerOperations.start(KitchenTimer(id = 2L, durationSeconds = 45L), 5_000L)
        assertEquals(121_000L, first.endsAtMillis)
        assertEquals(50_000L, second.endsAtMillis)
        assertEquals(76L, KitchenTimerOperations.remainingSeconds(first, 45_000L))
        assertEquals(5L, KitchenTimerOperations.remainingSeconds(second, 45_000L))
    }
}
