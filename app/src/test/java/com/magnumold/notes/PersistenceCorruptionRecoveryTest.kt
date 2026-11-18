package com.magnumold.notes

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModelStore
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Proxy
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PersistenceCorruptionRecoveryTest {
    private fun preferences(name: String) =
        RuntimeEnvironment.getApplication()
            .getSharedPreferences(name, Context.MODE_PRIVATE)
            .also { it.edit().clear().commit() }

    @Test
    fun damagedRecordJsonIsPreservedAndQuarantinedAtomically() {
        val prefs = preferences("corrupt_records_recovery_test")
        val damaged = "{not-valid-json"
        prefs.edit().putString("notes", damaged).commit()

        val repository = NotesRepository(prefs)
        val snapshot = repository.loadSnapshot()
        assertTrue(snapshot.notes.isEmpty())

        val recoveryKey = prefs.all.keys.single { it.startsWith("recovery_corrupt_notes_") }
        assertEquals(damaged, prefs.getString(recoveryKey, null))
        assertEquals("[]", prefs.getString("notes", null))

        repository.loadSnapshot()
        assertEquals(1, prefs.all.keys.count { it.startsWith("recovery_corrupt_notes_") })

        repository.saveSnapshot(snapshot)
        assertEquals(damaged, prefs.getString(recoveryKey, null))
    }

    @Test
    fun damagedCollectionsArchiveAndTrashArePreservedAndQuarantined() {
        val prefs = preferences("corrupt_vault_recovery_test")
        prefs.edit()
            .putString("collections", "{bad")
            .putString("archive", "{bad")
            .putString("trash", "{bad")
            .commit()

        NotesRepository(prefs).loadSnapshot()

        listOf("collections", "archive", "trash").forEach { key ->
            assertEquals("[]", prefs.getString(key, null))
            val recoveryKey = prefs.all.keys.single { it.startsWith("recovery_corrupt_${key}_") }
            assertEquals("{bad", prefs.getString(recoveryKey, null))
        }
    }

    @Test
    fun damagedTimeTrackingJsonIsPreservedAndQuarantinedAtomically() {
        val prefs = preferences("corrupt_time_recovery_test")
        val damaged = "[broken-time-tracking-json"
        prefs.edit().putString("time_tracking_state_v1", damaged).commit()

        val repository = TimeTrackingRepository(prefs)
        assertEquals(TimeTrackingState(), repository.load())

        val recoveryKey = prefs.all.keys.single {
            it.startsWith("recovery_corrupt_time_tracking_state_v1_")
        }
        assertEquals(damaged, prefs.getString(recoveryKey, null))
        assertEquals("{}", prefs.getString("time_tracking_state_v1", null))

        repository.reload()
        assertEquals(1, prefs.all.keys.count {
            it.startsWith("recovery_corrupt_time_tracking_state_v1_")
        })

        repository.save(TimeTrackingState())
        assertEquals(damaged, prefs.getString(recoveryKey, null))
    }

    @Test
    fun malformedVaultEntryIsQuarantinedInsteadOfSilentlyDropped() {
        val prefs = preferences("corrupt_vault_entry_recovery_test")
        val damaged = """[{"note":null}]"""
        prefs.edit().putString("archive", damaged).commit()

        val snapshot = NotesRepository(prefs).loadSnapshot()

        assertTrue(snapshot.archived.isEmpty())
        assertEquals("[]", prefs.getString("archive", null))
        val recoveryKey = prefs.all.keys.single { it.startsWith("recovery_corrupt_archive_") }
        assertEquals(damaged, prefs.getString(recoveryKey, null))
    }

    @Test
    fun malformedTimeTrackingSessionQuarantinesTheWholeHistory() {
        val prefs = preferences("corrupt_tracking_entry_recovery_test")
        val damaged = """{"sessions":[{"id":"broken"}]}"""
        prefs.edit().putString("time_tracking_state_v1", damaged).commit()

        val repository = TimeTrackingRepository(prefs)

        assertEquals(TimeTrackingState(), repository.load())
        assertEquals("{}", prefs.getString("time_tracking_state_v1", null))
        val recoveryKey = prefs.all.keys.single {
            it.startsWith("recovery_corrupt_time_tracking_state_v1_")
        }
        assertEquals(damaged, prefs.getString(recoveryKey, null))
    }

    @Test
    fun backupImportFlushesMergedRecordsBeforeReturning() {
        val records = preferences("backup_import_flush_records_test")
        val tracking = preferences("backup_import_flush_tracking_test")
        val store = ViewModelStore()
        val viewModel = NotesViewModel(
            NotesRepository(records),
            TimeTrackingRepository(tracking),
            RecoveryDataRepository(records, tracking)
        )
        store.put("backup-import-test", viewModel)
        try {
            val note = Note(
                id = 420L,
                title = "",
                blocks = listOf(NoteBlock("Imported immediately")),
                updated = 420L
            )
            val json = createBackupJson(
                items = listOf(note),
                collections = emptyList(),
                archived = emptyList(),
                trash = emptyList()
            )

            val result = viewModel.importBackup(json)

            assertEquals(1, result.importedRecordCount)
            assertEquals(420L, NotesRepository(records).loadSnapshot().notes.single().id)
        } finally {
            store.clear()
            records.edit().clear().commit()
            tracking.edit().clear().commit()
        }
    }

    @Test
    fun explicitFlushPersistsLatestRecordSnapshot() {
        val prefs = preferences("explicit_snapshot_flush_test")
        val repository = NotesRepository(prefs)
        val note = Note(
            id = 42L,
            title = "",
            blocks = listOf(NoteBlock("flush this record")),
            updated = 100L
        )
        val snapshot = PersistenceSnapshot(
            notes = listOf(note),
            collections = emptyList(),
            archived = emptyList(),
            trash = emptyList()
        )

        repository.flushSnapshot(snapshot)

        assertEquals(42L, NotesRepository(prefs).loadSnapshot().notes.single().id)
    }

    @Test
    fun failedExplicitFlushDoesNotDisableLaterAutosave() = runBlocking {
        val base = preferences("failed_flush_autosave_recovery_test")
        var failNextCommit = true
        val wrapped = Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)
        ) { _, method, args ->
            if (method.name == "edit") {
                val editor = base.edit()
                Proxy.newProxyInstance(
                    SharedPreferences.Editor::class.java.classLoader,
                    arrayOf(SharedPreferences.Editor::class.java)
                ) { proxy, editorMethod, editorArgs ->
                    if (editorMethod.name == "commit" && failNextCommit) {
                        failNextCommit = false
                        false
                    } else {
                        val result = editorMethod.invoke(editor, *(editorArgs ?: emptyArray()))
                        if (result is SharedPreferences.Editor) proxy else result
                    }
                }
            } else {
                method.invoke(base, *(args ?: emptyArray()))
            }
        } as SharedPreferences
        val tracking = preferences("failed_flush_autosave_tracking_test")
        val store = ViewModelStore()
        val viewModel = NotesViewModel(
            NotesRepository(wrapped),
            TimeTrackingRepository(tracking),
            RecoveryDataRepository(wrapped, tracking)
        )
        store.put("failed-flush-autosave-test", viewModel)
        try {
            try {
                viewModel.flush()
                fail("The first synchronous commit should fail")
            } catch (_: IllegalStateException) {
                // The observer must still be restarted in the finally block.
            }

            viewModel.createActiveRecord(
                null,
                Note(901L, "", listOf(NoteBlock("Autosaved after failed flush")), 901L)
            )
            delay(500L)

            assertEquals(
                901L,
                NotesRepository(base).loadSnapshot().notes.single().id
            )
        } finally {
            store.clear()
            base.edit().clear().commit()
            tracking.edit().clear().commit()
        }
    }

}
