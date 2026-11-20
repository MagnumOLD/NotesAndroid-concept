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
class TimeTrackingRepositoryImportTransactionTest {
    private fun preferences(name: String) =
        RuntimeEnvironment.getApplication()
            .getSharedPreferences(name, Context.MODE_PRIVATE)
            .also { it.edit().clear().commit() }

    @Test
    fun failedMultiStoreCommitRestoresDurableTrackingState() {
        val preferences = preferences("tracking_import_transaction_rollback")
        val repository = TimeTrackingRepository(preferences)
        val previous = TimeTrackingState(soundEnabled = true)
        val imported = TimeTrackingState(soundEnabled = false)
        repository.save(previous)

        var failed = false
        try {
            repository.withBackupImportTransaction(
                prepare = { current -> mergeTimeTrackingBackup(current, imported) },
                commit = { _, merged ->
                    assertEquals(imported, merged)
                    throw IllegalStateException("Simulated later-store failure")
                }
            )
        } catch (_: IllegalStateException) {
            failed = true
        }

        assertTrue(failed)
        assertEquals(previous, repository.load())
        assertEquals(previous, TimeTrackingRepository(preferences).load())
    }

    @Test
    fun successfulMultiStoreCommitKeepsMergedTrackingState() {
        val preferences = preferences("tracking_import_transaction_success")
        val repository = TimeTrackingRepository(preferences)
        val previous = TimeTrackingState(soundEnabled = true)
        val imported = TimeTrackingState(soundEnabled = false)
        repository.save(previous)

        val result = repository.withBackupImportTransaction(
            prepare = { current -> mergeTimeTrackingBackup(current, imported) },
            commit = { _, merged -> merged?.soundEnabled }
        )

        assertEquals(false, result)
        assertEquals(false, repository.load().soundEnabled)
        assertEquals(false, TimeTrackingRepository(preferences).load().soundEnabled)
    }
    @Test
    fun repositoryInstancesReadTheLatestSharedPersistedState() {
        val preferences = preferences("tracking_shared_repository_reads")
        val first = TimeTrackingRepository(preferences)
        val second = TimeTrackingRepository(preferences)
        val latest = TimeTrackingState(soundEnabled = false)

        first.save(latest)

        assertEquals(latest, second.load())
    }

}
