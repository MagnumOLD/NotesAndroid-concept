package com.magnumold.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.json.JSONObject

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TimeTrackingJsonCodecTest {
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
            soundUri = "content://alarm/tone",
            soundEnabled = false
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

    @Test fun missingTrackingObjectIsNotTreatedAsAnImportedEmptyHistory() {
        val backup = parseBackupJson(
            """{"format":"notes_backup","version":6,"notes":[],"collections":[]}"""
        )
        assertNull(backup.timeTracking)
    }
}
