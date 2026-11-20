package com.magnumold.notes

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.lang.reflect.Proxy
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
    fun invalidRecoveryPayloadDoesNotPartiallyImportEarlierEntries() {
        val records = preferences("recovery_invalid_records_test")
        val tracking = preferences("recovery_invalid_tracking_test")
        val repository = RecoveryDataRepository(records, tracking)
        val entries = listOf(
            RecoveryEntry("records", "recovery_corrupt_notes_400", "{valid-first"),
            RecoveryEntry("unknown", "recovery_corrupt_unknown_500", "{invalid-second")
        )

        var rejected = false
        try {
            repository.importEntries(entries)
        } catch (_: IllegalStateException) {
            rejected = true
        }

        assertTrue(rejected)
        assertTrue(!records.contains("recovery_corrupt_notes_400"))
        assertTrue(!tracking.contains("recovery_corrupt_unknown_500"))
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
    @Test
    fun duplicateEntriesInOnePayloadAreWrittenOnlyOnce() {
        val records = preferences("recovery_duplicate_payload_records_test")
        val tracking = preferences("recovery_duplicate_payload_tracking_test")
        val entry = RecoveryEntry("records", "recovery_corrupt_notes_600", "{same-fragment")

        val imported = RecoveryDataRepository(records, tracking).importEntries(listOf(entry, entry))

        assertEquals(1, imported)
        assertEquals("{same-fragment", records.getString(entry.key, null))
        assertEquals(1, records.all.keys.count { it.startsWith("recovery_corrupt_") })
    }

    @Test
    fun failedSecondStoreCommitRollsBackEarlierStoreWrites() {
        val records = preferences("recovery_rollback_records_test")
        val trackingBase = preferences("recovery_rollback_tracking_test")
        var failCommit = true
        val tracking = Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)
        ) { _, method, args ->
            if (method.name == "edit") {
                val editor = trackingBase.edit()
                Proxy.newProxyInstance(
                    SharedPreferences.Editor::class.java.classLoader,
                    arrayOf(SharedPreferences.Editor::class.java)
                ) { proxy, editorMethod, editorArgs ->
                    if (editorMethod.name == "commit" && failCommit) {
                        failCommit = false
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
        val repository = RecoveryDataRepository(records, tracking)

        var failed = false
        try {
            repository.importEntries(
                listOf(
                    RecoveryEntry("records", "recovery_corrupt_notes_700", "{record-fragment"),
                    RecoveryEntry("time_tracking", "recovery_corrupt_timer_800", "{timer-fragment")
                )
            )
        } catch (_: IllegalStateException) {
            failed = true
        }

        assertTrue(failed)
        assertTrue(records.all.keys.none { it.startsWith("recovery_corrupt_") })
        assertTrue(trackingBase.all.keys.none { it.startsWith("recovery_corrupt_") })
    }

}
