package com.magnumold.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeTrackingEngineTest {
    @Test fun parallelSessionsAreCountedOnceInParentDurationButIndividuallyPerTask() {
        var state = TimeTrackingState()
        state = TimeTrackingEngine.start(state, 1, 100, "Parent", "A", 0, "a")
        state = TimeTrackingEngine.start(state, 2, 100, "Parent", "B", 0, "b")
        state = TimeTrackingEngine.finish(state, 1, 3_600_000, WorkSessionEndReason.STOP)
        state = TimeTrackingEngine.finish(state, 2, 3_600_000, WorkSessionEndReason.STOP)

        assertEquals(3_600_000L, TimeTrackingEngine.taskDurationMillis(state, 1, 3_600_000))
        assertEquals(3_600_000L, TimeTrackingEngine.taskDurationMillis(state, 2, 3_600_000))
        assertEquals(3_600_000L, TimeTrackingEngine.parentDurationMillis(state, 100, 3_600_000))
    }

    @Test fun parentDurationMergesPartiallyOverlappingIntervals() {
        val first = session("a", 1, 100, 0, 60)
        val second = session("b", 2, 100, 30, 90)
        val state = TimeTrackingState(sessions = listOf(first, second))

        assertEquals(90L, TimeTrackingEngine.parentDurationMillis(state, 100, 100))
    }

    @Test fun parentDurationAddsSeparateIntervalsWithoutCountingTheGap() {
        val first = session("a", 1, 100, 0, 30)
        val second = session("b", 2, 100, 50, 80)
        val state = TimeTrackingState(sessions = listOf(first, second))

        assertEquals(60L, TimeTrackingEngine.parentDurationMillis(state, 100, 100))
    }

    @Test fun parentDurationMergesSavedAndActiveIntervals() {
        val saved = session("saved", 1, 100, 0, 60)
        val active = ActiveWorkSession("active", 2, 100, "Parent", "B", 30)
        val state = TimeTrackingState(sessions = listOf(saved), activeSessions = mapOf(2L to active))

        assertEquals(90L, TimeTrackingEngine.parentDurationMillis(state, 100, 90))
    }

    @Test fun parentDurationIgnoresSessionsFromOtherParents() {
        val included = session("included", 1, 100, 0, 40)
        val excluded = session("excluded", 2, 200, 0, 90)
        val state = TimeTrackingState(sessions = listOf(included, excluded))

        assertEquals(40L, TimeTrackingEngine.parentDurationMillis(state, 100, 100))
    }


    @Test fun startingSessionRejectsAnIdAlreadyUsedBySavedOrActiveHistory() {
        val saved = session("taken", 1L, null, 0L, 10L)
        val withSaved = TimeTrackingState(sessions = listOf(saved))
        try {
            TimeTrackingEngine.start(withSaved, 2L, null, "Other", null, 20L, "taken")
            throw AssertionError("A saved session ID must not be reused")
        } catch (_: IllegalArgumentException) {
            // Duplicate IDs would make future persistence parsing reject the entire state.
        }

        val active = TimeTrackingEngine.start(TimeTrackingState(), 3L, null, "Active", null, 30L, "active-id")
        try {
            TimeTrackingEngine.start(active, 4L, null, "Other", null, 40L, "active-id")
            throw AssertionError("An active session ID must not be reused")
        } catch (_: IllegalArgumentException) {
            // Session IDs are unique across active and completed intervals.
        }
    }

    @Test fun pausingClosesSessionAndNextStartCreatesAnother() {
        var state = TimeTrackingEngine.start(TimeTrackingState(), 1, null, "Task", null, 100, "first")
        state = TimeTrackingEngine.finish(state, 1, 500, WorkSessionEndReason.PAUSE)
        state = TimeTrackingEngine.start(state, 1, null, "Task", null, 900, "second")
        assertEquals(1, state.sessions.size)
        assertEquals(900L, state.activeSessions[1L]?.startedAtMillis)
    }

    @Test fun historyGroupsIntervalsWithGapsUpToTenMinutesWithoutCountingBreaks() {
        val first = session("first", 7, 2, 0, 5 * 60_000L)
        val second = session("second", 7, 2, 15 * 60_000L, 20 * 60_000L)
        val third = session("third", 7, 2, 31 * 60_000L, 36 * 60_000L)

        val groups = TimeTrackingEngine.groupSessionsForDisplay(listOf(first, second, third))

        assertEquals(listOf(1, 2), groups.map { it.size }.sorted())
        val merged = groups.single { it.size == 2 }
        assertEquals(10 * 60_000L, merged.sumOf { it.durationMillis })
        assertEquals(0L, merged.first().startedAtMillis)
        assertEquals(20 * 60_000L, merged.last().endedAtMillis)
    }

    @Test fun taskTitleSnapshotSurvivesIndependentOfTaskRecord() {
        var state = TimeTrackingEngine.start(TimeTrackingState(), 7, null, "Original title", "Child", 10, "s")
        state = TimeTrackingEngine.finish(state, 7, 20, WorkSessionEndReason.TASK_COMPLETED)
        assertEquals("Original title", state.sessions.single().taskTitle)
        assertEquals("Child", state.sessions.single().subtaskTitle)
    }

    @Test fun sessionCanBeEditedAddedAndDeleted() {
        val session = session("s", 1, null, 10, 30)
        var state = TimeTrackingEngine.addManualSession(TimeTrackingState(), session)
        state = TimeTrackingEngine.replaceSession(state, session.copy(endedAtMillis = 50, durationMillis = 40))
        assertEquals(40L, state.sessions.single().durationMillis)
        state = TimeTrackingEngine.deleteSession(state, "s")
        assertTrue(state.sessions.isEmpty())
    }

    @Test fun periodStatisticsSumParallelSessionsWithoutDeduplication() {
        var state = TimeTrackingState()
        state = TimeTrackingEngine.start(state, 1, 9, "Parent", "One", 0, "one")
        state = TimeTrackingEngine.start(state, 2, 9, "Parent", "Two", 0, "two")
        state = TimeTrackingEngine.finish(state, 1, 3_600_000, WorkSessionEndReason.STOP)
        state = TimeTrackingEngine.finish(state, 2, 1_800_000, WorkSessionEndReason.STOP)
        val stats = TimeTrackingEngine.statisticsBetween(state, 0, 7_200_000, 7_200_000)
        assertEquals(5_400_000L, stats.totalMillis)
        assertEquals(3_600_000L, stats.taskTotalsMillis[1L])
        assertEquals(1_800_000L, stats.taskTotalsMillis[2L])
        assertEquals(2, stats.sessionCount)
    }


    @Test fun veryLargeDurationsSaturateInsteadOfWrappingNegative() {
        val first = session("first", 7L, 9L, Long.MIN_VALUE, -1L)
        val second = session("second", 7L, 9L, 0L, Long.MAX_VALUE)
        val state = TimeTrackingState(sessions = listOf(first, second))

        assertEquals(Long.MAX_VALUE, TimeTrackingEngine.taskDurationMillis(state, 7L, Long.MAX_VALUE))
        assertEquals(Long.MAX_VALUE, TimeTrackingEngine.parentDurationMillis(state, 9L, Long.MAX_VALUE))
        val stats = TimeTrackingEngine.statisticsBetween(state, Long.MIN_VALUE, Long.MAX_VALUE, Long.MAX_VALUE)
        assertEquals(Long.MAX_VALUE, stats.totalMillis)
        assertEquals(Long.MAX_VALUE, stats.taskTotalsMillis[7L])
    }

    @Test fun finishingSessionSaturatesDurationAcrossLongTimestampRange() {
        val active = TimeTrackingEngine.start(
            TimeTrackingState(), 7L, null, "Task", null, Long.MIN_VALUE, "extreme"
        )

        val finished = TimeTrackingEngine.finish(
            active, 7L, Long.MAX_VALUE, WorkSessionEndReason.STOP
        )

        assertEquals(Long.MIN_VALUE, finished.sessions.single().startedAtMillis)
        assertEquals(Long.MAX_VALUE, finished.sessions.single().endedAtMillis)
        assertEquals(Long.MAX_VALUE, finished.sessions.single().durationMillis)
    }

    @Test fun displayGroupingDoesNotMergeWhenGapSubtractionWouldOverflow() {
        val first = session("first", 7L, null, Long.MIN_VALUE, Long.MIN_VALUE)
        val second = session("second", 7L, null, Long.MAX_VALUE - 1L, Long.MAX_VALUE)

        val groups = TimeTrackingEngine.groupSessionsForDisplay(listOf(first, second))

        assertEquals(2, groups.size)
        assertEquals(listOf("second", "first"), groups.map { it.single().id })
    }

    @Test fun dailyTotalsClipsLongSessionsToTheSelectedAnalyticsRange() {
        val calendar = java.util.Calendar.getInstance().apply {
            clear()
            set(2026, java.util.Calendar.NOVEMBER, 18, 0, 0, 0)
        }
        val start = calendar.timeInMillis
        calendar.add(java.util.Calendar.DAY_OF_MONTH, 1)
        val end = calendar.timeInMillis
        val session = WorkSession(
            id = "long",
            taskId = 7L,
            parentTaskId = null,
            taskTitle = "Task",
            subtaskTitle = null,
            startedAtMillis = Long.MIN_VALUE,
            endedAtMillis = Long.MAX_VALUE,
            durationMillis = Long.MAX_VALUE,
            endReason = WorkSessionEndReason.STOP
        )

        val totals = TimeTrackingEngine.dailyTotals(
            TimeTrackingState(sessions = listOf(session)),
            nowMillis = end,
            startInclusive = start,
            endExclusive = end
        )
        val key = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            .format(java.util.Date(start))

        assertEquals(setOf(key), totals.keys)
        assertEquals(end - start, totals[key])
    }

    @Test fun protectionTimerIsIndependentAndOnlyOneCanRun() {
        val timer = ProtectionTimerEngine.start(null, 1, "Task", "Child", 0, 60_000, ProtectionEndMode.SIGNAL_ONLY, null, false)
        assertEquals(30_000L, ProtectionTimerEngine.remainingMillis(timer, 30_000))
        assertTrue(ProtectionTimerEngine.isFinished(timer, 60_000))
    }

    @Test fun protectionTimerRemainingTimeDoesNotOverflowForExtremeTimestamps() {
        val expired = ProtectionTimer(
            taskId = 1, taskTitle = "Task", subtaskTitle = null,
            startedAtMillis = Long.MIN_VALUE, endsAtMillis = -1L,
            endMode = ProtectionEndMode.SIGNAL_ONLY
        )
        val longRunning = expired.copy(endsAtMillis = Long.MAX_VALUE)

        assertEquals(0L, ProtectionTimerEngine.remainingMillis(expired, 1L))
        assertEquals(Long.MAX_VALUE, ProtectionTimerEngine.remainingMillis(longRunning, Long.MIN_VALUE))
    }

    @Test fun protectionTimerRejectsEndTimestampOverflow() {
        try {
            ProtectionTimerEngine.start(
                null, 1, "Task", null, Long.MAX_VALUE - 10L, 60_000L,
                ProtectionEndMode.SIGNAL_ONLY, null, false
            )
            throw AssertionError("Expected an invalid end timestamp to be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected: an invalid timestamp must not wrap into the past.
        }
    }

    private fun session(
        id: String,
        taskId: Long,
        parentTaskId: Long?,
        start: Long,
        end: Long
    ) = WorkSession(
        id = id,
        taskId = taskId,
        parentTaskId = parentTaskId,
        taskTitle = "Parent",
        subtaskTitle = "Child",
        startedAtMillis = start,
        endedAtMillis = end,
        durationMillis = end - start,
        endReason = WorkSessionEndReason.STOP
    )
}
