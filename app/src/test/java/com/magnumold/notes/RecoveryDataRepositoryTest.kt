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
class RecoveryDataRepositoryTest {
    private fun preferences(name: String) =
        RuntimeEnvironment.getApplication()
            .getSharedPreferences(name, Context.MODE_PRIVATE)
            .also { it.edit().clear().commit() }

    @Test
    fun exportCollectsQuarantinedFragmentsFromBothStores() {
        val records = preferences("recovery_export_records_test")
        val tracking = preferences("recovery_export_tracking_test")
        records.edit()
            .putString("recovery_corrupt_notes_100", "{broken-notes")
            .putString("ordinary_setting", "not recovery data")
            .commit()
        tracking.edit()
            .putString("recovery_corrupt_time_tracking_state_v1_200", "{broken-timer")
            .commit()

        val entries = RecoveryDataRepository(records, tracking).exportEntries()

        assertEquals(
            listOf(
                RecoveryEntry("records", "recovery_corrupt_notes_100", "{broken-notes"),
                RecoveryEntry("time_tracking", "recovery_corrupt_time_tracking_state_v1_200", "{broken-timer")
            ),
            entries
        )
    }

    @Test
    fun importingRecoveryFragmentsIsIdempotentAndDoesNotOverwriteConflicts() {
        val records = preferences("recovery_import_records_test")
        val tracking = preferences("recovery_import_tracking_test")
        records.edit().putString("recovery_corrupt_notes_100", "{local-copy").commit()
        val repository = RecoveryDataRepository(records, tracking)
        val entries = listOf(
            RecoveryEntry("records", "recovery_corrupt_notes_100", "{imported-copy"),
            RecoveryEntry("time_tracking", "recovery_corrupt_time_tracking_state_v1_200", "{timer-copy")
        )

        assertEquals(2, repository.importEntries(entries))
        assertEquals(0, repository.importEntries(entries))

        assertEquals("{local-copy", records.getString("recovery_corrupt_notes_100", null))
        assertTrue(records.all.keys.contains("recovery_corrupt_notes_100_import_1"))
        assertEquals("{imported-copy", records.getString("recovery_corrupt_notes_100_import_1", null))
        assertEquals("{timer-copy", tracking.getString("recovery_corrupt_time_tracking_state_v1_200", null))
        assertEquals(2, records.all.keys.count { it.startsWith("recovery_corrupt_") })
        assertEquals(1, tracking.all.keys.count { it.startsWith("recovery_corrupt_") })
    }

    @Test
    fun backupRoundTripPreservesQuarantinedFragments() {
        val entry = RecoveryEntry("records", "recovery_corrupt_notes_300", "{original-data")
        val encoded = createBackupJson(
            items = emptyList(),
            collections = emptyList(),
            archived = emptyList(),
            trash = emptyList(),
            recoveryEntries = listOf(entry)
        )

        val decoded = parseBackupJson(encoded)

        assertEquals(12, org.json.JSONObject(encoded).getInt("version"))
        assertEquals(listOf(entry), decoded.recoveryEntries)
    }
}
