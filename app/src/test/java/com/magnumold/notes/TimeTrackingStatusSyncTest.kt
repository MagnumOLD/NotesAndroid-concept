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

    @Test fun protectionTimerActionResolvesTheTaskCurrentParentAfterItMoves() {
        val child = task(11, "Child", RecordStatus.PAUSED)
        val currentTree = listOf(
            task(10, "Old parent", RecordStatus.WAITING),
            task(20, "Current parent", RecordStatus.IN_PROGRESS, listOf(child))
        )

        val context = findTaskTrackingContext(currentTree, 11L)

        assertEquals(11L, context?.task?.id)
        assertEquals(20L, context?.parent?.id)
        assertEquals("Current parent", context?.parent?.trackingDisplayTitle())
        assertEquals("Child", context?.task?.trackingDisplayTitle())
    }

    @Test fun protectionTimerActionDoesNotTreatMovedRootTaskAsSubtask() {
        val context = findTaskTrackingContext(
            listOf(task(11, "Child", RecordStatus.PAUSED)),
            11L
        )

        assertEquals(11L, context?.task?.id)
        assertEquals(null, context?.parent)
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

    @Test fun expiredOldProtectionAlarmCannotConsumeReplacementTimerOrStopItsSession() {
        val oldTimer = ProtectionTimer(11L, "Parent", "Child", 100L, 200L, ProtectionEndMode.STOP_AND_PAUSE)
        val replacement = oldTimer.copy(taskId = 22L, startedAtMillis = 300L, endsAtMillis = 900L)
        val active = TimeTrackingEngine.start(
            state = TimeTrackingState(protectionTimer = replacement),
            taskId = 22L,
            parentTaskId = 20L,
            taskTitle = "Other parent",
            subtaskTitle = "Other child",
            nowMillis = 350L,
            sessionId = "replacement-active"
        )

        val result = completeProtectionTimerIfCurrent(active, oldTimer, 500L)

        assertEquals(replacement, result.protectionTimer)
        assertTrue(result.activeSessions.containsKey(22L))
        assertTrue(result.sessions.isEmpty())
    }

    @Test fun expiredStopAndPauseProtectionTimerClosesOnlyItsOwnSession() {
        val timer = ProtectionTimer(11L, "Parent", "Child", 100L, 200L, ProtectionEndMode.STOP_AND_PAUSE)
        val active = TimeTrackingEngine.start(
            state = TimeTrackingState(protectionTimer = timer),
            taskId = 11L,
            parentTaskId = 10L,
            taskTitle = "Parent",
            subtaskTitle = "Child",
            nowMillis = 100L,
            sessionId = "active-child"
        )

        val result = completeProtectionTimerIfCurrent(active, timer, 250L)

        assertEquals(null, result.protectionTimer)
        assertTrue(result.activeSessions.isEmpty())
        assertEquals(WorkSessionEndReason.AUTOMATIC_PAUSE, result.sessions.single().endReason)
        assertEquals(150L, result.sessions.single().durationMillis)
        assertEquals(timer.taskId, result.pendingProtectionTaskPauseId)
    }

    @Test fun expiredSignalOnlyProtectionTimerDoesNotStopWorkSession() {
        val timer = ProtectionTimer(11L, "Parent", "Child", 100L, 200L, ProtectionEndMode.SIGNAL_ONLY)
        val active = TimeTrackingEngine.start(
            state = TimeTrackingState(protectionTimer = timer),
            taskId = 11L,
            parentTaskId = 10L,
            taskTitle = "Parent",
            subtaskTitle = "Child",
            nowMillis = 100L,
            sessionId = "active-child"
        )

        val result = completeProtectionTimerIfCurrent(active, timer, 250L)

        assertEquals(null, result.protectionTimer)
        assertTrue(result.activeSessions.containsKey(11L))
        assertTrue(result.sessions.isEmpty())
        assertEquals(null, result.pendingProtectionTaskPauseId)
    }

    @Test fun staleProtectionTimerActionClearsOnlyItsOwnTimer() {
        val timer = ProtectionTimer(11L, "Parent", "Child", 100L, 200L, ProtectionEndMode.STOP_AND_PAUSE)
        val otherTimer = timer.copy(taskId = 22L)
        val state = TimeTrackingState(protectionTimer = timer)

        val cleared = clearProtectionTimerForTask(state, 11L)
        val untouched = clearProtectionTimerForTask(state, 22L)

        assertEquals(null, cleared.protectionTimer)
        assertEquals(timer, untouched.protectionTimer)
    }

    @Test fun protectionNotificationActionMustMatchTheAlertThatIsCurrentlyRinging() {
        val oldAlert = ProtectionTimer(11L, "Parent", "Child", 100L, 200L, ProtectionEndMode.STOP_AND_PAUSE)
        val newAlert = oldAlert.copy(startedAtMillis = 300L, endsAtMillis = 400L)

        assertTrue(isProtectionTimerActionCurrent(newAlert, null, 11L, 300L, 400L, newAlert))
        assertFalse(isProtectionTimerActionCurrent(newAlert, null, 11L, 100L, 200L, newAlert))
        assertFalse(isProtectionTimerActionCurrent(newAlert, null, 22L, 300L, 400L, newAlert))
        assertFalse(isProtectionTimerActionCurrent(newAlert, null, 11L, 300L, 400L, oldAlert))
        val newerPendingTimer = newAlert.copy(taskId = 22L, startedAtMillis = 500L, endsAtMillis = 900L)
        assertFalse(isProtectionTimerActionCurrent(newAlert, newerPendingTimer, 11L, 300L, 400L, newAlert))
    }

    @Test fun firedAlertActionRequiresDurableIdentityAfterProcessRestart() {
        val fired = ProtectionTimer(11L, "Parent", "Child", 100L, 200L, ProtectionEndMode.STOP_AND_PAUSE)
        assertTrue(isProtectionTimerActionCurrent(null, null, 11L, 100L, 200L, fired))
        assertFalse(isProtectionTimerActionCurrent(null, null, 11L, 100L, 200L, null))
        assertFalse(isProtectionTimerActionCurrent(null, null, 22L, 100L, 200L, fired))
        assertFalse(isProtectionTimerActionCurrent(null, null, 11L, 100L, 201L, fired))
    }

    @Test fun staleProtectionActionCannotConsumeANewerTimerForTheSameTask() {
        val newerTimer = ProtectionTimer(11L, "Parent", "Child", 300L, 900L, ProtectionEndMode.STOP_AND_PAUSE)

        assertFalse(isProtectionTimerActionCurrent(null, newerTimer, 11L, 100L, 200L, newerTimer))
        assertFalse(isProtectionTimerActionCurrent(null, newerTimer, 11L, 300L, 900L, null))
        assertFalse(isProtectionTimerActionCurrent(null, newerTimer, 11L, 300L, 900L, newerTimer))
    }

    @Test fun completedProtectionActionSurvivesServiceProcessRestart() {
        val fired = ProtectionTimer(11L, "Parent", "Child", 100L, 200L, ProtectionEndMode.STOP_AND_PAUSE)
        assertTrue(isProtectionTimerActionCurrent(null, null, 11L, 100L, 200L, fired))
        assertFalse(isProtectionTimerActionCurrent(null, null, 11L, 100L, 200L))
    }

    @Test fun staleActionForAnotherTaskIsRejectedWhileAnotherTimerIsPending() {
        val otherTimer = ProtectionTimer(22L, "Other parent", "Other child", 300L, 900L, ProtectionEndMode.STOP_AND_PAUSE)

        assertFalse(isProtectionTimerActionCurrent(null, otherTimer, 11L, 100L, 200L))
        assertFalse(isProtectionTimerActionCurrent(null, otherTimer, 22L, 300L, 900L))
    }

    @Test fun staleStopTrackingActionPreservesAnotherTasksProtectionTimer() {
        val timer = ProtectionTimer(22L, "Other parent", "Other child", 100L, 900L, ProtectionEndMode.SIGNAL_ONLY)
        val active = TimeTrackingEngine.start(
            state = TimeTrackingState(protectionTimer = timer),
            taskId = 11L,
            parentTaskId = 10L,
            taskTitle = "Parent",
            subtaskTitle = "Child",
            nowMillis = 200L,
            sessionId = "active-child"
        )

        val stopped = stopWorkAfterProtectionTimer(active, 11L, 500L)

        assertEquals(300L, stopped.sessions.single().durationMillis)
        assertEquals(WorkSessionEndReason.STOP, stopped.sessions.single().endReason)
        assertTrue(stopped.activeSessions.isEmpty())
        assertEquals(timer, stopped.protectionTimer)
    }

    @Test fun stopTrackingActionClearsItsOwnProtectionTimer() {
        val timer = ProtectionTimer(11L, "Parent", "Child", 100L, 900L, ProtectionEndMode.SIGNAL_ONLY)
        val stopped = stopWorkAfterProtectionTimer(
            TimeTrackingState(protectionTimer = timer),
            11L,
            500L
        )
        assertEquals(null, stopped.protectionTimer)
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

    @Test fun deadlineExpiryStopsAnActiveSubtaskWhenTrackingStateIsReconciled() {
        val previousZone = java.util.TimeZone.getDefault()
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"))
            val deadline = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
                clear()
                set(2026, java.util.Calendar.JANUARY, 2, 0, 0, 0)
            }.timeInMillis
            val beforeDeadline = deadline + 23L * 60L * 60L * 1_000L
            val afterDeadline = deadline + 24L * 60L * 60L * 1_000L + 60L * 1_000L
            val child = task(11, "Child", RecordStatus.IN_PROGRESS).copy(
                dateMode = RecordDateMode.DEADLINE,
                dateAt = deadline
            )
            val parent = task(10, "Parent", RecordStatus.WAITING, listOf(child))
            val active = syncWorkSessionsForTaskChanges(
                emptyList(), listOf(parent), TimeTrackingState(), beforeDeadline
            )
            assertTrue(active.activeSessions.containsKey(11L))

            // Reconcile the same task tree after its local deadline has passed.
            val reconciled = syncWorkSessionsForTaskChanges(
                listOf(parent), listOf(parent), active, afterDeadline
            )

            assertFalse(reconciled.activeSessions.containsKey(11L))
            assertEquals(WorkSessionEndReason.STOP, reconciled.sessions.single().endReason)
            assertEquals(deadline + 24L * 60L * 60L * 1_000L, reconciled.sessions.single().endedAtMillis)
        } finally {
            java.util.TimeZone.setDefault(previousZone)
        }
    }


    @Test fun deadlineTrackingStopsAtTheCorrectLocalBoundaryAcrossSpringDst() {
        val zone = java.util.TimeZone.getTimeZone("Europe/Vienna")
        val deadline = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(2026, java.util.Calendar.MARCH, 29, 0, 0, 0)
        }.timeInMillis

        val localStart = java.util.Calendar.getInstance(zone).apply {
            clear()
            set(2026, java.util.Calendar.MARCH, 29, 0, 0, 0)
        }.timeInMillis
        val boundary = deadlineEndExclusiveMillis(deadline, zone)
        val expected = java.util.Calendar.getInstance(zone).apply {
            clear()
            set(2026, java.util.Calendar.MARCH, 30, 0, 0, 0)
        }.timeInMillis

        assertEquals(expected, boundary)
        assertEquals(23L * 60L * 60L * 1_000L, boundary - localStart)
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

    @Test fun bootReconciliationStopsDeadlineExpiredSessionBeforeAppLaunch() {
        val previousZone = java.util.TimeZone.getDefault()
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"))
            val deadline = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
                clear()
                set(2026, java.util.Calendar.JANUARY, 2, 0, 0, 0)
            }.timeInMillis
            val sessionStart = deadline + 12L * 60L * 60L * 1_000L
            val bootTime = deadline + 24L * 60L * 60L * 1_000L + 5L * 60L * 1_000L
            val now = bootTime + 30L * 60L * 1_000L
            val child = task(11, "Child", RecordStatus.IN_PROGRESS).copy(
                dateMode = RecordDateMode.DEADLINE,
                dateAt = deadline
            )
            val parent = task(10, "Parent", RecordStatus.WAITING, listOf(child))
            val active = ActiveWorkSession(
                id = "after-boot",
                taskId = 11L,
                parentTaskId = 10L,
                taskTitle = "Parent",
                subtaskTitle = "Child",
                startedAtMillis = sessionStart
            )
            val initial = TimeTrackingState(activeSessions = mapOf(11L to active))

            val reconciled = reconcileBootTimeTrackingState(listOf(parent), initial, now)

            assertFalse(reconciled.activeSessions.containsKey(11L))
            assertEquals(WorkSessionEndReason.STOP, reconciled.sessions.single().endReason)
            assertEquals(deadline + 24L * 60L * 60L * 1_000L, reconciled.sessions.single().endedAtMillis)
            assertEquals(12L * 60L * 60L * 1_000L, reconciled.sessions.single().durationMillis)
        } finally {
            java.util.TimeZone.setDefault(previousZone)
        }
    }



    @Test fun bootRestoresPersistedFiredProtectionAlert() {
        val fired = ProtectionTimer(
            11L, "Parent", "Child", 100L, 200L, ProtectionEndMode.SIGNAL_ONLY
        )
        assertTrue(shouldRestoreProtectionAlertOnBoot(TimeTrackingState(firedProtectionAlert = fired)))
    }

    @Test fun bootDoesNotRestoreAlertWhenNoneIsPersisted() {
        assertFalse(shouldRestoreProtectionAlertOnBoot(TimeTrackingState()))
    }

    @Test fun uiSoundChangePreservesProtectionTimerCreatedByServiceAfterUiSnapshot() {
        val previousUi = TimeTrackingState(soundEnabled = true)
        val timer = ProtectionTimer(
            11L, "Parent", "Child", 100L, 900L, ProtectionEndMode.SIGNAL_ONLY
        )
        val latest = previousUi.copy(protectionTimer = timer)
        val requested = previousUi.copy(soundEnabled = false)

        val merged = mergeTimeTrackingUiChange(latest, previousUi, requested)

        assertEquals(timer, merged.protectionTimer)
        assertFalse(merged.soundEnabled)
    }

    @Test fun addingManualSessionFromUiPreservesConcurrentActiveSession() {
        val previousUi = TimeTrackingState()
        val manual = WorkSession(
            id = "manual", taskId = 11L, parentTaskId = 10L,
            taskTitle = "Parent", subtaskTitle = "Child",
            startedAtMillis = 100L, endedAtMillis = 200L,
            durationMillis = 100L, endReason = WorkSessionEndReason.STOP
        )
        val requested = previousUi.copy(sessions = listOf(manual))
        val active = ActiveWorkSession(
            id = "active", taskId = 22L, parentTaskId = 20L,
            taskTitle = "Other parent", subtaskTitle = "Other child",
            startedAtMillis = 300L
        )
        val latest = previousUi.copy(activeSessions = mapOf(22L to active))

        val merged = mergeTimeTrackingUiChange(latest, previousUi, requested)

        assertEquals(listOf(manual), merged.sessions)
        assertEquals(active, merged.activeSessions[22L])
    }

    @Test fun staleUiTimerStartCannotReplaceTimerCreatedConcurrently() {
        val previousUi = TimeTrackingState()
        val concurrentTimer = ProtectionTimer(
            22L, "Other task", null, 300L, 900L, ProtectionEndMode.SIGNAL_ONLY
        )
        val requestedTimer = ProtectionTimer(
            11L, "Task", null, 400L, 1_000L, ProtectionEndMode.STOP_AND_PAUSE
        )
        val latest = previousUi.copy(protectionTimer = concurrentTimer)
        val requested = previousUi.copy(protectionTimer = requestedTimer)

        val merged = mergeTimeTrackingUiChange(latest, previousUi, requested)

        assertEquals(concurrentTimer, merged.protectionTimer)
    }

    @Test fun staleUiTimerStartCannotClearAlertThatFiredConcurrently() {
        val previousUi = TimeTrackingState()
        val fired = ProtectionTimer(
            22L, "Other task", null, 100L, 200L, ProtectionEndMode.SIGNAL_ONLY
        )
        val requestedTimer = ProtectionTimer(
            11L, "Task", null, 300L, 900L, ProtectionEndMode.STOP_AND_PAUSE
        )
        val latest = previousUi.copy(firedProtectionAlert = fired)
        val requested = previousUi.copy(
            protectionTimer = requestedTimer,
            firedProtectionAlert = null
        )

        val merged = mergeTimeTrackingUiChange(latest, previousUi, requested)

        assertEquals(null, merged.protectionTimer)
        assertEquals(fired, merged.firedProtectionAlert)
    }
    @Test fun inexactAlarmFallbackIsUsedOnlyWhenExactAlarmAccessIsUnavailable() {
        assertTrue(shouldUseProtectionAlarmNotificationFallback(31, false))
        assertFalse(shouldUseProtectionAlarmNotificationFallback(31, true))
        assertFalse(shouldUseProtectionAlarmNotificationFallback(30, false))
    }

    @Test fun inexactAlarmResolutionReschedulesTimerThatHasNotExpired() {
        val timer = ProtectionTimer(
            11L, "Parent", "Child", 100L, 200L, ProtectionEndMode.SIGNAL_ONLY
        )
        val state = TimeTrackingState(protectionTimer = timer)

        val resolution = resolveProtectionAlarm(state, 150L)

        assertEquals(state, resolution.state)
        assertEquals(timer, resolution.rescheduleTimer)
        assertEquals(null, resolution.expiredTimer)
    }

    @Test fun inexactAlarmResolutionConsumesExpiredTimerOnlyOnce() {
        val timer = ProtectionTimer(
            11L, "Parent", "Child", 100L, 200L, ProtectionEndMode.SIGNAL_ONLY
        )
        val state = TimeTrackingState(protectionTimer = timer)

        val resolution = resolveProtectionAlarm(state, 250L)

        assertEquals(null, resolution.state.protectionTimer)
        assertEquals(timer, resolution.state.firedProtectionAlert)
        assertEquals(timer, resolution.expiredTimer)
        assertEquals(null, resolution.rescheduleTimer)
    }

    @Test fun inexactAlarmResolutionKeepsAlreadyFiredAlertWithoutReconsumingTimer() {
        val fired = ProtectionTimer(
            11L, "Parent", "Child", 100L, 200L, ProtectionEndMode.SIGNAL_ONLY
        )
        val state = TimeTrackingState(firedProtectionAlert = fired)

        val resolution = resolveProtectionAlarm(state, 500L)

        assertEquals(state, resolution.state)
        assertEquals(null, resolution.expiredTimer)
        assertEquals(null, resolution.rescheduleTimer)
    }

}
