package com.magnumold.notes

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupImportTransactionJournalTest {
    private fun preferences(name: String) =
        RuntimeEnvironment.getApplication()
            .getSharedPreferences(name, Context.MODE_PRIVATE)
            .also { it.edit().clear().commit() }

    @Test
    fun failedImportRestoresAllManagedValues() {
        val records = preferences("import_journal_failure_records")
        val tracking = preferences("import_journal_failure_tracking")
        val journal = preferences("import_journal_failure_journal")
        records.edit().putString("notes", "old-notes")
            .putString("recovery_corrupt_notes_1", "old-recovery").commit()
        tracking.edit().putString("time_tracking_state_v1", "old-tracking")
            .putString("recovery_corrupt_timer_1", "old-timer-recovery").commit()
        val transaction = BackupImportTransactionJournal(records, tracking, journal)

        var failed = false
        try {
            transaction.execute {
                records.edit().putString("notes", "new-notes")
                    .putString("recovery_corrupt_notes_2", "new-recovery").commit()
                tracking.edit().putString("time_tracking_state_v1", "new-tracking")
                    .putString("recovery_corrupt_timer_2", "new-timer-recovery").commit()
                throw IllegalStateException("simulated import failure")
            }
        } catch (_: IllegalStateException) {
            failed = true
        }

        assertTrue(failed)
        assertEquals("old-notes", records.getString("notes", null))
        assertEquals("old-recovery", records.getString("recovery_corrupt_notes_1", null))
        assertFalse(records.contains("recovery_corrupt_notes_2"))
        assertEquals("old-tracking", tracking.getString("time_tracking_state_v1", null))
        assertEquals("old-timer-recovery", tracking.getString("recovery_corrupt_timer_1", null))
        assertFalse(tracking.contains("recovery_corrupt_timer_2"))
        assertFalse(journal.contains("active_backup_import_v1"))
    }

    @Test
    fun startupRecoveryRollsBackAnInterruptedImport() {
        val records = preferences("import_journal_crash_records")
        val tracking = preferences("import_journal_crash_tracking")
        val journal = preferences("import_journal_crash_journal")
        records.edit().putString("notes", "before-crash").commit()
        tracking.edit().putString("time_tracking_state_v1", "tracking-before-crash").commit()
        val firstProcess = BackupImportTransactionJournal(records, tracking, journal)

        // Simulate process death after the journal and some new values are durable.
        firstProcess.execute {
            records.edit().putString("notes", "partially-imported").commit()
            tracking.edit().putString("time_tracking_state_v1", "partially-imported").commit()
            // Leave a durable journal as if Android killed the process before commit.
            journal.edit().putString(
                "active_backup_import_v1",
                org.json.JSONObject()
                    .put("records", org.json.JSONObject().put("notes", "before-crash"))
                    .put("tracking", org.json.JSONObject().put("time_tracking_state_v1", "tracking-before-crash"))
                    .toString()
            ).commit()
        }

        BackupImportTransactionJournal(records, tracking, journal).recoverIfNeeded()

        assertEquals("before-crash", records.getString("notes", null))
        assertEquals("tracking-before-crash", tracking.getString("time_tracking_state_v1", null))
        assertFalse(journal.contains("active_backup_import_v1"))
    }
}
