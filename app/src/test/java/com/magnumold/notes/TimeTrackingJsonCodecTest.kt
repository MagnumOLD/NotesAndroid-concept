package com.magnumold.notes

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.json.JSONObject

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TimeTrackingJsonCodecTest {
    @Test fun updatesFromSeparateRepositoryInstancesReadTheLatestPersistedState() {
        val preferences = RuntimeEnvironment.getApplication().getSharedPreferences(
            "time_tracking_repository_concurrency_${System.nanoTime()}",
            Context.MODE_PRIVATE
        )
        val first = TimeTrackingRepository(preferences)
        val second = TimeTrackingRepository(preferences)
        second.load() // Keep a stale in-memory snapshot in the second instance.

        val timer = ProtectionTimer(
            taskId = 11L,
            taskTitle = "Parent",
            subtaskTitle = "Child",
            startedAtMillis = 100L,
            endsAtMillis = 200L,
            endMode = ProtectionEndMode.STOP_AND_PAUSE
        )
        first.update { it.copy(protectionTimer = timer) }
        second.update { it.copy(soundEnabled = false) }

        assertEquals(timer, second.load().protectionTimer)
        assertEquals(false, second.load().soundEnabled)
    }

    @Test fun trackingStateRoundTripsSessionsActiveStopwatchesAndTimer() {
        val session = WorkSession(
            id = "session-1",
            taskId = 2L,
            parentTaskId = 1L,
            taskTitle = "Parent",
            subtaskTitle = "Subtask",
            startedAtMillis = 100L,
            endedAtMillis = 500L,
            durationMillis = 400L,
            endReason = WorkSessionEndReason.PAUSE
        )
        val active = ActiveWorkSession(
            id = "session-2",
            taskId = 3L,
            parentTaskId = 1L,
            taskTitle = "Parent",
            subtaskTitle = "Another subtask",
            startedAtMillis = 600L
        )
        val timer = ProtectionTimer(
            taskId = 2L,
            taskTitle = "Parent",
            subtaskTitle = "Subtask",
            startedAtMillis = 700L,
            endsAtMillis = 70_700L,
            endMode = ProtectionEndMode.STOP_AND_PAUSE,
            soundUri = "content://alarm/tone",
            soundEnabled = false,
            parentTaskId = 1L
        )
        val original = TimeTrackingState(
            sessions = listOf(session),
            activeSessions = mapOf(3L to active),
            protectionTimer = timer,
            firedProtectionAlert = timer.copy(startedAtMillis = 800L, endsAtMillis = 900L),
            soundUri = "content://alarm/tone",
            soundEnabled = false,
            pendingProtectionTaskPauseId = 2L
        )
        val restored = parseTimeTrackingJson(timeTrackingToJson(original))
        assertEquals(original, restored)
    }

    @Test fun importingTrackingBackupMergesHistoryWithoutReplacingLocalSessions() {
        val local = WorkSession("local", 1L, null, "Local", null, 10L, 20L, 10L, WorkSessionEndReason.PAUSE)
        val duplicate = local.copy(endedAtMillis = 25L, durationMillis = 15L)
        val imported = WorkSession("remote", 2L, null, "Remote", null, 30L, 50L, 20L, WorkSessionEndReason.STOP)
        val localActive = ActiveWorkSession("active-local", 3L, null, "Local active", null, 60L)
        val remoteActive = ActiveWorkSession("active-remote", 3L, null, "Remote active", null, 70L)
        val additionalActive = ActiveWorkSession("active-new", 4L, null, "New active", null, 80L)
        val localTimer = ProtectionTimer(3L, "Local timer", null, 100L, 200L, ProtectionEndMode.SIGNAL_ONLY)
        val importedTimer = ProtectionTimer(4L, "Imported timer", null, 110L, 210L, ProtectionEndMode.STOP_AND_PAUSE)
        val current = TimeTrackingState(listOf(local), mapOf(3L to localActive), localTimer, "local-tone", true)
        val backup = TimeTrackingState(listOf(duplicate, imported), mapOf(3L to remoteActive, 4L to additionalActive), importedTimer, "backup-tone", false)

        val merged = mergeTimeTrackingBackup(current, backup)

        assertEquals(listOf(duplicate, imported), merged.sessions)
        assertEquals(mapOf(3L to localActive, 4L to additionalActive), merged.activeSessions)
        assertEquals(localTimer, merged.protectionTimer)
        assertEquals("backup-tone", merged.soundUri)
        assertEquals(false, merged.soundEnabled)
    }


    @Test fun collidingSessionIdsFromDifferentTasksArePreservedDuringBackupMerge() {
        val local = WorkSession(
            "same-id", 1L, null, "Local", null, 10L, 20L, 10L, WorkSessionEndReason.STOP
        )
        val imported = WorkSession(
            "same-id", 2L, null, "Imported", null, 30L, 50L, 20L, WorkSessionEndReason.STOP
        )
        val merged = mergeTimeTrackingBackup(
            TimeTrackingState(sessions = listOf(local)),
            TimeTrackingState(sessions = listOf(imported))
        )

        assertEquals(2, merged.sessions.size)
        assertEquals(setOf(1L, 2L), merged.sessions.map { it.taskId }.toSet())
        assertEquals(2, merged.sessions.map { it.id }.toSet().size)
    }

    @Test fun importedActiveSessionIdCollisionIsRekeyedInsteadOfOverwritingHistory() {
        val saved = WorkSession(
            "shared-id", 1L, null, "Saved", null, 10L, 20L, 10L, WorkSessionEndReason.STOP
        )
        val importedActive = ActiveWorkSession("shared-id", 2L, null, "Active", null, 30L)
        val merged = mergeTimeTrackingBackup(
            TimeTrackingState(sessions = listOf(saved)),
            TimeTrackingState(activeSessions = mapOf(2L to importedActive))
        )

        assertEquals(1, merged.sessions.size)
        assertEquals(2L, merged.activeSessions.keys.single())
        assertNotEquals("shared-id", merged.activeSessions.getValue(2L).id)
        assertEquals(2, (merged.sessions.map { it.id } + merged.activeSessions.values.map { it.id }).toSet().size)
    }

    @Test
    fun orphanedImportedTrackingIdsCannotAttachToUnrelatedLocalRecords() {
        val localTask = Note(55L, "", listOf(NoteBlock("Local task")), 55L, type = RecordType.TASK)
        val importedTracking = TimeTrackingState(
            sessions = listOf(
                WorkSession("orphan", 55L, null, "Deleted imported task", null,
                    100L, 200L, 100L, WorkSessionEndReason.STOP)
            )
        )
        val merged = RecordOperations.mergeBackupWithIdMap(
            PersistenceSnapshot(
                notes = listOf(localTask),
                collections = emptyList(),
                archived = emptyList(),
                trash = emptyList()
            ),
            BackupData(
                notes = emptyList(),
                collections = emptyList(),
                archived = emptyList(),
                trash = emptyList(),
                timeTracking = importedTracking
            )
        )
        val remapped = remapTimeTrackingTaskIds(importedTracking, merged.importedNoteIdMap)

        assertNotEquals(55L, remapped.sessions.single().taskId)
        assertEquals(55L, merged.snapshot.notes.single().id)
    }

    @Test
    fun importedTrackingReferencesFollowRekeyedNestedTaskIds() {
        val currentChild = Note(
            id = 20L,
            title = "",
            blocks = listOf(NoteBlock("Local task")),
            updated = 20L,
            type = RecordType.TASK
        )
        val currentParent = Note(
            id = 10L,
            title = "",
            blocks = listOf(NoteBlock("Local parent")),
            updated = 10L,
            type = RecordType.TASK,
            children = listOf(currentChild)
        )
        val importedChild = Note(
            id = 20L,
            title = "",
            blocks = listOf(NoteBlock("Imported subtask")),
            updated = 40L,
            type = RecordType.TASK
        )
        val importedParent = Note(
            id = 30L,
            title = "",
            blocks = listOf(NoteBlock("Imported parent")),
            updated = 30L,
            type = RecordType.TASK,
            children = listOf(importedChild)
        )
        val backupTracking = TimeTrackingState(
            sessions = listOf(
                WorkSession("imported-session", 20L, 30L, "Imported parent", "Imported subtask",
                    100L, 200L, 100L, WorkSessionEndReason.STOP)
            ),
            activeSessions = mapOf(
                20L to ActiveWorkSession("active-imported", 20L, 30L, "Imported parent", "Imported subtask", 300L)
            ),
            protectionTimer = ProtectionTimer(
                20L, "Imported parent", "Imported subtask", 400L, 500L,
                ProtectionEndMode.SIGNAL_ONLY, parentTaskId = 30L
            )
        )
        val merged = RecordOperations.mergeBackupWithIdMap(
            PersistenceSnapshot(
                notes = listOf(currentParent),
                collections = emptyList(),
                archived = emptyList(),
                trash = emptyList()
            ),
            BackupData(
                notes = listOf(importedParent),
                collections = emptyList(),
                archived = emptyList(),
                trash = emptyList(),
                timeTracking = backupTracking
            )
        )
        val importedSubtaskId = merged.snapshot.notes.last().children.single().id
        val remapped = remapTimeTrackingTaskIds(backupTracking, merged.importedNoteIdMap)

        assertNotEquals(20L, importedSubtaskId)
        assertEquals(importedSubtaskId, remapped.sessions.single().taskId)
        assertEquals(30L, remapped.sessions.single().parentTaskId)
        assertEquals(importedSubtaskId, remapped.activeSessions.keys.single())
        assertEquals(importedSubtaskId, remapped.activeSessions.values.single().taskId)
        assertEquals(importedSubtaskId, remapped.protectionTimer?.taskId)
        assertEquals(30L, remapped.protectionTimer?.parentTaskId)
    }

    @Test fun sharedBackupKeepsPerSubtaskTrackingPreference() {
        val task = Note(
            id = 10L,
            title = "",
            blocks = listOf(NoteBlock("Subtask")),
            updated = 1L,
            type = RecordType.TASK,
            timeTrackingEnabled = true
        )
        val json = createBackupJson(listOf(task), emptyList(), emptyList(), emptyList())
        val restored = parseBackupJson(json)
        assertEquals(true, restored.notes.single().timeTrackingEnabled)
    }

    @Test fun malformedSessionIsNotSilentlyDropped() {
        val malformed = JSONObject("""{"sessions":[{"id":"broken"}]}""")
        try {
            parseTimeTrackingJson(malformed)
            fail("Malformed sessions must invalidate the complete state")
        } catch (_: Exception) {
            // Expected: repository will quarantine the original JSON.
        }
    }

    @Test fun malformedActiveSessionAndTimerAreNotSilentlyDropped() {
        listOf(
            JSONObject("""{"activeSessions":[{"id":"broken"}]}"""),
            JSONObject("""{"protectionTimer":{"taskId":1}}""")
        ).forEach { malformed ->
            try {
                parseTimeTrackingJson(malformed)
                fail("Malformed tracking state must be rejected")
            } catch (_: Exception) {
                // Expected.
            }
        }
    }


    @Test fun activeSessionsRequireNonBlankIdsAndTaskTitles() {
        listOf(
            JSONObject("""{"activeSessions":[{"id":"","taskId":1,"taskTitle":"Task","startedAt":10}]}"""),
            JSONObject("""{"activeSessions":[{"id":"active","taskId":1,"taskTitle":"","startedAt":10}]}""")
        ).forEach { malformed ->
            try {
                parseTimeTrackingJson(malformed)
                fail("Active sessions require a stable ID and task title")
            } catch (_: IllegalArgumentException) {
                // Expected: malformed active sessions are quarantined as one state.
            }
        }
    }

    @Test fun duplicateSavedSessionIdsInvalidateTrackingState() {
        val session = WorkSession(
            "duplicate", 1L, null, "Task", null, 10L, 20L, 10L, WorkSessionEndReason.STOP
        )
        val json = timeTrackingToJson(TimeTrackingState(sessions = listOf(session, session.copy(taskId = 2L))))
        try {
            parseTimeTrackingJson(json)
            fail("Duplicate saved session IDs must invalidate the tracking state")
        } catch (_: IllegalArgumentException) {
            // Duplicate IDs would make edits and deletion ambiguous.
        }
    }

    @Test fun activeSessionIdsMustBeUniqueAcrossSavedAndActiveHistory() {
        val saved = WorkSession(
            "shared", 1L, null, "Task", null, 10L, 20L, 10L, WorkSessionEndReason.STOP
        )
        val active = ActiveWorkSession("shared", 2L, null, "Another task", null, 30L)
        val json = timeTrackingToJson(
            TimeTrackingState(sessions = listOf(saved), activeSessions = mapOf(2L to active))
        )
        try {
            parseTimeTrackingJson(json)
            fail("An active session must not reuse a saved session ID")
        } catch (_: IllegalArgumentException) {
            // A session ID identifies one logical interval across its lifecycle.
        }
    }

    @Test fun missingTrackingObjectIsNotTreatedAsAnImportedEmptyHistory() {
        val backup = parseBackupJson(
            """{"format":"notes_backup","version":6,"notes":[],"collections":[]}"""
        )
        assertNull(backup.timeTracking)
    }
}
