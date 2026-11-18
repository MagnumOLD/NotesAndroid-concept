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
    fun damagedRecordJsonIsPreservedBeforeLaterSnapshotSave() {
        val prefs = preferences("corrupt_records_recovery_test")
        val damaged = "{not-valid-json"
        prefs.edit().putString("notes", damaged).commit()

        val repository = NotesRepository(prefs)
        val snapshot = repository.loadSnapshot()
        assertTrue(snapshot.notes.isEmpty())

        val recoveryKey = prefs.all.keys.single { it.startsWith("recovery_corrupt_notes_") }
        assertEquals(damaged, prefs.getString(recoveryKey, null))

        repository.saveSnapshot(snapshot)
        assertEquals(damaged, prefs.getString(recoveryKey, null))
        assertEquals("[]", prefs.getString("notes", null))
    }

    @Test
    fun damagedTimeTrackingJsonIsPreservedBeforeValidStateCanBeSaved() {
        val prefs = preferences("corrupt_time_recovery_test")
        val damaged = "[broken-time-tracking-json"
        prefs.edit().putString("time_tracking_state_v1", damaged).commit()

        val repository = TimeTrackingRepository(prefs)
        assertEquals(TimeTrackingState(), repository.load())

        val recoveryKey = prefs.all.keys.single {
            it.startsWith("recovery_corrupt_time_tracking_state_v1_")
        }
        assertEquals(damaged, prefs.getString(recoveryKey, null))

        repository.save(TimeTrackingState())
        assertEquals(damaged, prefs.getString(recoveryKey, null))
    }
}
