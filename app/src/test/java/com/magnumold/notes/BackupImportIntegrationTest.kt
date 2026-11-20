package com.magnumold.notes

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModelStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Proxy
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupImportIntegrationTest {
    private fun preferences(name: String) =
        RuntimeEnvironment.getApplication()
            .getSharedPreferences(name, Context.MODE_PRIVATE)
            .also { it.edit().clear().commit() }

    @Test
    fun failureInSecondRecoveryStoreRollsBackRecordsTrackingAndRecoveryData() {
        val records = preferences("backup_atomic_records")
        val trackingBase = preferences("backup_atomic_tracking")
        val journal = preferences("backup_atomic_journal")
        val original = Note(
            id = 10L,
            title = "",
            blocks = listOf(NoteBlock("Original record")),
            updated = 10L
        )
        NotesRepository(records).flushSnapshot(
            PersistenceSnapshot(listOf(original), emptyList(), emptyList(), emptyList())
        )
        val previousTracking = TimeTrackingState(
            sessions = listOf(
                WorkSession(
                    id = "original-session",
                    taskId = 10L,
                    parentTaskId = null,
                    taskTitle = "Original task",
                    subtaskTitle = null,
                    startedAtMillis = 100L,
                    endedAtMillis = 200L,
                    durationMillis = 100L,
                    endReason = WorkSessionEndReason.STOP
                )
            )
        )
        trackingBase.edit()
            .putString("time_tracking_state_v1", timeTrackingToJson(previousTracking).toString())
            .putString("recovery_corrupt_timer_old", "old-timer-fragment")
            .commit()

        var failRecoveryWrite = true
        val tracking = Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)
        ) { _, method, args ->
            if (method.name == "edit") {
                val editor = trackingBase.edit()
                var writingRecovery = false
                Proxy.newProxyInstance(
                    SharedPreferences.Editor::class.java.classLoader,
                    arrayOf(SharedPreferences.Editor::class.java)
                ) { proxy, editorMethod, editorArgs ->
                    if (editorMethod.name == "putString" &&
                        (editorArgs?.getOrNull(0) as? String)?.startsWith("recovery_corrupt_") == true
                    ) {
                        writingRecovery = true
                    }
                    if (editorMethod.name == "commit" && writingRecovery && failRecoveryWrite) {
                        failRecoveryWrite = false
                        false
                    } else {
                        val result = editorMethod.invoke(editor, *(editorArgs ?: emptyArray()))
                        if (result is SharedPreferences.Editor) proxy else result
                    }
                }
            } else {
                method.invoke(trackingBase, *(args ?: emptyArray()))
            }
        } as SharedPreferences

        val store = ViewModelStore()
        val viewModel = NotesViewModel(
            NotesRepository(records),
            TimeTrackingRepository(tracking),
            RecoveryDataRepository(records, tracking),
            BackupImportTransactionJournal(records, tracking, journal)
        )
        store.put("atomic-backup-import-test", viewModel)
        try {
            val imported = Note(
                id = 20L,
                title = "",
                blocks = listOf(NoteBlock("Must be rolled back")),
                updated = 20L
            )
            val backup = createBackupJson(
                items = listOf(imported),
                collections = emptyList(),
                archived = emptyList(),
                trash = emptyList(),
                timeTracking = TimeTrackingState(
                    sessions = listOf(
                        WorkSession(
                            id = "imported-session",
                            taskId = 20L,
                            parentTaskId = null,
                            taskTitle = "Imported task",
                            subtaskTitle = null,
                            startedAtMillis = 300L,
                            endedAtMillis = 400L,
                            durationMillis = 100L,
                            endReason = WorkSessionEndReason.STOP
                        )
                    )
                ),
                recoveryEntries = listOf(
                    RecoveryEntry("records", "recovery_corrupt_notes_import", "new-record-fragment"),
                    RecoveryEntry("time_tracking", "recovery_corrupt_timer_import", "new-timer-fragment")
                )
            )

            var failed = false
            try {
                viewModel.importBackup(backup)
            } catch (_: IllegalStateException) {
                failed = true
            }

            assertTrue(failed)
            assertEquals(listOf(10L), NotesRepository(records).loadSnapshot().notes.map { it.id })
            assertEquals(previousTracking, TimeTrackingRepository(trackingBase).load())
            assertEquals("old-timer-fragment", trackingBase.getString("recovery_corrupt_timer_old", null))
            assertFalse(records.contains("recovery_corrupt_notes_import"))
            assertFalse(trackingBase.contains("recovery_corrupt_timer_import"))
            assertFalse(journal.contains("active_backup_import_v1"))
        } finally {
            store.clear()
            records.edit().clear().commit()
            trackingBase.edit().clear().commit()
            journal.edit().clear().commit()
        }
    }
}
