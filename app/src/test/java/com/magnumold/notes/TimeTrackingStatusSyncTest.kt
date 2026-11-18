package com.magnumold.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeTrackingStatusSyncTest {
    private fun task(
        id: Long,
        text: String,
        status: RecordStatus,
        children: List<Note> = emptyList(),
        trackingEnabled: Boolean = true
    ) = Note(
        id, "", listOf(NoteBlock(text)), 1L,
        type = RecordType.TASK, status = status,
        timeTrackingEnabled = trackingEnabled, children = children
    )

    @Test fun startingParentDoesNotStartItsOwnStopwatch() {
        val parent = task(10, "Parent", RecordStatus.IN_PROGRESS)
        val state = syncWorkSessionsForTaskChanges(emptyList(), listOf(parent), TimeTrackingState(), 100L)
        assertTrue(state.activeSessions.isEmpty())
    }

    @Test fun startingSubtaskStartsOnlyItsOwnSessionAndSnapshotsBothNames() {
        val before = task(10, "Parent", RecordStatus.IN_PROGRESS, listOf(task(11, "Child", RecordStatus.WAITING)))
        val after = before.copy(children = listOf(task(11, "Child", RecordStatus.IN_PROGRESS)))
        val state = syncWorkSessionsForTaskChanges(listOf(before), listOf(after), TimeTrackingState(), 100L)
        assertEquals(setOf(11L), state.activeSessions.keys)
        assertEquals(10L, state.activeSessions[11L]?.parentTaskId)
        assertEquals("Parent", state.activeSessions[11L]?.taskTitle)
        assertEquals("Child", state.activeSessions[11L]?.subtaskTitle)
    }

    @Test fun everySubtaskIsTrackedRegardlessOfLegacyOptInValue() {
        val parent = task(
            10, "Parent", RecordStatus.IN_PROGRESS,
            listOf(task(11, "Child", RecordStatus.IN_PROGRESS, trackingEnabled = false))
        )
        val state = syncWorkSessionsForTaskChanges(emptyList(), listOf(parent), TimeTrackingState(), 100L)
        assertEquals(setOf(11L), state.activeSessions.keys)
    }

    @Test fun movingActiveSubtaskToAnotherParentClosesAndRestartsItsSession() {
        val child = task(11, "Child", RecordStatus.IN_PROGRESS)
        val before = listOf(
            task(10, "First parent", RecordStatus.IN_PROGRESS, listOf(child)),
            task(20, "Second parent", RecordStatus.WAITING)
        )
        val active = syncWorkSessionsForTaskChanges(
            emptyList(), before, TimeTrackingState(), 100L
        )
        val after = listOf(
            task(10, "First parent", RecordStatus.IN_PROGRESS),
            task(20, "Second parent", RecordStatus.WAITING, listOf(child))
        )

        val updated = syncWorkSessionsForTaskChanges(before, after, active, 600L)

        assertEquals(WorkSessionEndReason.STOP, updated.sessions.single().endReason)
        assertEquals(500L, updated.sessions.single().durationMillis)
        assertEquals(20L, updated.activeSessions[11L]?.parentTaskId)
        assertEquals(600L, updated.activeSessions[11L]?.startedAtMillis)
        assertTrue(updated.activeSessions[11L]?.id != active.activeSessions[11L]?.id)
    }

    @Test fun protectionTimerFollowsSubtaskWhenParentChanges() {
        val child = task(11, "Child", RecordStatus.PAUSED)
        val before = listOf(
            task(10, "First parent", RecordStatus.IN_PROGRESS, listOf(child)),
            task(20, "Second parent", RecordStatus.WAITING)
        )
        val timer = ProtectionTimer(
            taskId = 11L,
            taskTitle = "First parent",
            subtaskTitle = "Child",
            startedAtMillis = 100L,
            endsAtMillis = 900L,
            endMode = ProtectionEndMode.STOP_AND_PAUSE,
            parentTaskId = 10L
        )
        val after = listOf(
            task(10, "First parent", RecordStatus.IN_PROGRESS),
            task(20, "Second parent", RecordStatus.WAITING, listOf(child))
        )

        val updated = syncWorkSessionsForTaskChanges(
            before, after, TimeTrackingState(protectionTimer = timer), 500L
        )

        assertEquals(20L, updated.protectionTimer?.parentTaskId)
        assertEquals("Second parent", updated.protectionTimer?.taskTitle)
        assertEquals("Child", updated.protectionTimer?.subtaskTitle)
    }

    @Test fun protectionTimerContinueResumesSubtaskWithoutLegacyOptInFlag() {
        val timer = ProtectionTimer(11L, "Parent", "Child", 100L, 200L, ProtectionEndMode.STOP_AND_PAUSE, parentTaskId = 10L)
        val initial = TimeTrackingState(
            activeSessions = mapOf(11L to ActiveWorkSession("old", 11L, 10L, "Parent", "Child", 100L)),
            protectionTimer = timer
        )
        val paused = TimeTrackingEngine.finish(initial, 11L, 200L, WorkSessionEndReason.AUTOMATIC_PAUSE)

        val resumed = resumeWorkAfterProtectionTimer(
            state = paused,
            taskId = 11L,
            parentTaskId = 10L,
            taskTitle = "Parent",
            subtaskTitle = "Child",
            nowMillis = 300L
        )

        assertEquals(200L, resumed.sessions.single().endedAtMillis)
        assertEquals(300L, resumed.activeSessions[11L]?.startedAtMillis)
        assertEquals(10L, resumed.activeSessions[11L]?.parentTaskId)
        assertTrue(resumed.activeSessions[11L]?.id != "old")
        assertEquals(null, resumed.protectionTimer)
    }

    @Test fun protectionTimerContinueDoesNotStartStopwatchForParentTask() {
        val timer = ProtectionTimer(10L, "Parent", null, 100L, 200L, ProtectionEndMode.STOP_AND_PAUSE)
        val resumed = resumeWorkAfterProtectionTimer(
            state = TimeTrackingState(protectionTimer = timer),
            taskId = 10L,
            parentTaskId = null,
            taskTitle = "Parent",
            subtaskTitle = null,
            nowMillis = 300L
        )
        assertTrue(resumed.activeSessions.isEmpty())
        assertEquals(null, resumed.protectionTimer)
    }

    @Test fun pausingSubtaskSavesSessionWithPauseReason() {
        val before = task(10, "Parent", RecordStatus.WAITING, listOf(task(11, "Child", RecordStatus.IN_PROGRESS)))
        val active = syncWorkSessionsForTaskChanges(emptyList(), listOf(before), TimeTrackingState(), 100L)
        val after = before.copy(children = listOf(task(11, "Child", RecordStatus.PAUSED)))
        val state = syncWorkSessionsForTaskChanges(listOf(before), listOf(after), active, 600L)
        assertFalse(state.activeSessions.containsKey(11L))
        assertEquals(WorkSessionEndReason.PAUSE, state.sessions.single().endReason)
        assertEquals(500L, state.sessions.single().durationMillis)
    }
}
