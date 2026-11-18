package com.magnumold.notes

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
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
}
