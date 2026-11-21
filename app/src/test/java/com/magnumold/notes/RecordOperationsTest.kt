package com.magnumold.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordOperationsTest {
    @Test
    fun externalTaskStatusUpdatePreservesPendingRecordEdits() {
        val child = Note(
            id = 22L,
            title = "",
            blocks = listOf(NoteBlock("unsaved text")),
            updated = 10L,
            type = RecordType.TASK,
            status = RecordStatus.IN_PROGRESS,
            deadlineResumeOverride = true
        )
        val parent = Note(
            id = 21L,
            title = "",
            blocks = listOf(NoteBlock("parent")),
            updated = 10L,
            type = RecordType.TASK,
            children = listOf(child)
        )
        val snapshot = PersistenceSnapshot(
            notes = listOf(parent),
            collections = emptyList(),
            archived = emptyList(),
            trash = emptyList()
        )

        val updated = RecordOperations.updateTaskStatus(snapshot, 22L, RecordStatus.PAUSED)
        val result = updated.notes.single().children.single()

        assertEquals("unsaved text", result.blocks.single().text)
        assertEquals(RecordStatus.PAUSED, result.status)
        assertEquals(false, result.deadlineResumeOverride)
    }

    @Test
    fun externalTimerRefreshUpdatesOnlyTimerStateAndPreservesPendingEdits() {
        val currentTimer = Note(
            id = 31L,
            title = "",
            blocks = listOf(NoteBlock("unsaved timer title")),
            updated = 10L,
            type = RecordType.TIMER,
            status = RecordStatus.IN_PROGRESS,
            timerDurationSeconds = 600L,
            timerEndsAtMillis = 20_000L,
            timerRemainingSeconds = null
        )
        val persistedTimer = currentTimer.copy(
            status = RecordStatus.COMPLETED,
            timerEndsAtMillis = null,
            timerRemainingSeconds = 0L,
            updated = 30L,
            blocks = listOf(NoteBlock("old persisted title")),
            timerDurationSeconds = 300L
        )
        val current = PersistenceSnapshot(
            notes = listOf(currentTimer),
            collections = emptyList(),
            archived = emptyList(),
            trash = emptyList()
        )
        val persisted = current.copy(notes = listOf(persistedTimer))

        val refreshed = RecordOperations.refreshTimerStates(current, persisted)
        val result = refreshed.notes.single()

        assertEquals("unsaved timer title", result.blocks.single().text)
        assertEquals(600L, result.timerDurationSeconds)
        assertEquals(RecordStatus.COMPLETED, result.status)
        assertEquals(null, result.timerEndsAtMillis)
        assertEquals(0L, result.timerRemainingSeconds)
        assertEquals(30L, result.updated)
    }

    @Test
    fun externalTaskStatusUpdateAlsoReachesArchivedAndTrashedRecords() {
        val archivedTask = Note(
            id = 51L, title = "", blocks = listOf(NoteBlock("archived")),
            updated = 10L, type = RecordType.TASK, status = RecordStatus.IN_PROGRESS
        )
        val trashedParent = Note(
            id = 52L, title = "", blocks = emptyList(), updated = 10L,
            type = RecordType.TASK,
            children = listOf(Note(
                id = 53L, title = "", blocks = listOf(NoteBlock("trashed child")),
                updated = 10L, type = RecordType.TASK, status = RecordStatus.IN_PROGRESS
            ))
        )
        val snapshot = PersistenceSnapshot(
            emptyList(), emptyList(),
            archived = listOf(vault(archivedTask)),
            trash = listOf(vault(trashedParent))
        )

        val result = RecordOperations.updateTaskStatus(snapshot, 51L, RecordStatus.COMPLETED)
        val afterChild = RecordOperations.updateTaskStatus(result, 53L, RecordStatus.PAUSED)

        assertEquals(RecordStatus.COMPLETED, afterChild.archived.single().note.status)
        assertEquals(RecordStatus.PAUSED, afterChild.trash.single().note.children.single().status)
        assertEquals(false, afterChild.trash.single().note.children.single().deadlineResumeOverride)
    }

    @Test
    fun externalTimerRefreshAlsoReachesArchivedAndTrashedRecords() {
        val currentArchived = Note(
            id = 61L, title = "", blocks = listOf(NoteBlock("keep archived edits")),
            updated = 10L, type = RecordType.TIMER, status = RecordStatus.IN_PROGRESS,
            timerDurationSeconds = 900L, timerEndsAtMillis = 50_000L
        )
        val currentTrashed = Note(
            id = 62L, title = "", blocks = listOf(NoteBlock("keep trashed edits")),
            updated = 10L, type = RecordType.TIMER, status = RecordStatus.IN_PROGRESS,
            timerDurationSeconds = 600L, timerEndsAtMillis = 40_000L
        )
        val persistedArchived = currentArchived.copy(
            status = RecordStatus.COMPLETED, timerEndsAtMillis = null,
            timerRemainingSeconds = 0L, updated = 30L,
            blocks = listOf(NoteBlock("stale archived text"))
        )
        val persistedTrashed = currentTrashed.copy(
            status = RecordStatus.PAUSED, timerEndsAtMillis = null,
            timerRemainingSeconds = 120L, updated = 40L,
            blocks = listOf(NoteBlock("stale trashed text"))
        )
        val current = PersistenceSnapshot(
            emptyList(), emptyList(),
            archived = listOf(vault(currentArchived)),
            trash = listOf(vault(currentTrashed))
        )
        val persisted = current.copy(
            archived = listOf(vault(persistedArchived)),
            trash = listOf(vault(persistedTrashed))
        )

        val result = RecordOperations.refreshTimerStates(current, persisted)

        assertEquals("keep archived edits", result.archived.single().note.blocks.single().text)
        assertEquals(900L, result.archived.single().note.timerDurationSeconds)
        assertEquals(RecordStatus.COMPLETED, result.archived.single().note.status)
        assertEquals(0L, result.archived.single().note.timerRemainingSeconds)
        assertEquals("keep trashed edits", result.trash.single().note.blocks.single().text)
        assertEquals(600L, result.trash.single().note.timerDurationSeconds)
        assertEquals(RecordStatus.PAUSED, result.trash.single().note.status)
        assertEquals(120L, result.trash.single().note.timerRemainingSeconds)
    }

    @Test
    fun externalCompletionOfNestedTaskUpdatesParentAndReopeningRestoresPreviousStatus() {
        fun child(id: Long, status: RecordStatus) = Note(
            id = id,
            title = "",
            blocks = listOf(NoteBlock("child $id")),
            updated = 10L,
            type = RecordType.TASK,
            status = status
        )
        val parent = Note(
            id = 70L,
            title = "",
            blocks = listOf(NoteBlock("parent")),
            updated = 10L,
            type = RecordType.TASK,
            status = RecordStatus.IN_PROGRESS,
            children = listOf(
                child(71L, RecordStatus.COMPLETED),
                child(72L, RecordStatus.WAITING)
            )
        )
        val snapshot = PersistenceSnapshot(
            notes = listOf(parent),
            collections = emptyList(),
            archived = emptyList(),
            trash = emptyList()
        )

        val completed = RecordOperations.updateTaskStatus(
            snapshot, 72L, RecordStatus.COMPLETED
        )
        val completedParent = completed.notes.single()
        assertEquals(RecordStatus.COMPLETED, completedParent.status)
        assertEquals(RecordStatus.IN_PROGRESS, completedParent.statusBeforeAutoCompletion)

        val reopened = RecordOperations.updateTaskStatus(
            completed, 71L, RecordStatus.PAUSED
        )
        val reopenedParent = reopened.notes.single()
        assertEquals(RecordStatus.IN_PROGRESS, reopenedParent.status)
        assertNull(reopenedParent.statusBeforeAutoCompletion)
        assertEquals(RecordStatus.PAUSED, reopenedParent.children.first().status)
    }

    private fun note(id: Long, text: String) = Note(
        id = id,
        title = text,
        blocks = listOf(NoteBlock(text = text)),
        updated = id
    )

    private fun vault(note: Note, movedAt: Long = 1L) =
        VaultNote(note = note, collectionId = null, position = 0, movedAt = movedAt)

    @Test
    fun importingBackupMergesArchiveAndTrashWithoutDuplicatingExistingEntries() {
        val existingArchive = vault(note(10L, "existing archive"))
        val existingTrash = vault(note(20L, "existing trash"))
        val importedArchive = vault(note(30L, "imported archive"))
        val importedTrash = vault(note(40L, "imported trash"))
        val current = PersistenceSnapshot(
            notes = emptyList(),
            collections = emptyList(),
            archived = listOf(existingArchive),
            trash = listOf(existingTrash)
        )
        val backup = BackupData(
            notes = emptyList(),
            collections = emptyList(),
            archived = listOf(importedArchive),
            trash = listOf(importedTrash)
        )

        val merged = RecordOperations.mergeBackup(current, backup)

        assertEquals(listOf(10L, 30L), merged.archived.map { it.note.id }.sorted())
        assertEquals(listOf(20L, 40L), merged.trash.map { it.note.id }.sorted())
        assertEquals(1, merged.archived.count { it.note.id == existingArchive.note.id })
        assertEquals(1, merged.trash.count { it.note.id == existingTrash.note.id })
    }

    @Test
    fun importingSameBackupTwiceIsIdempotentForVaultEntries() {
        val current = PersistenceSnapshot(
            notes = emptyList(),
            collections = emptyList(),
            archived = listOf(vault(note(10L, "old archive"))),
            trash = listOf(vault(note(20L, "old trash")))
        )
        val backup = BackupData(
            notes = emptyList(),
            collections = emptyList(),
            archived = listOf(vault(note(30L, "new archive"))),
            trash = listOf(vault(note(40L, "new trash")))
        )

        val once = RecordOperations.mergeBackup(current, backup)
        val twice = RecordOperations.mergeBackup(once, backup)

        assertEquals(once.archived.map { it.note.id }.toSet(), twice.archived.map { it.note.id }.toSet())
        assertEquals(once.trash.map { it.note.id }.toSet(), twice.trash.map { it.note.id }.toSet())
        assertTrue(twice.archived.size == 2 && twice.trash.size == 2)
    }
    @Test
    fun movingSelectedRecordsToVaultPreservesTheirVisibleOrder() {
        val snapshot = PersistenceSnapshot(
            notes = listOf(note(1L, "first"), note(2L, "second"), note(3L, "third")),
            collections = emptyList(),
            archived = emptyList(),
            trash = emptyList()
        )

        val archived = RecordOperations.moveActiveRecordsToVault(
            snapshot, collectionId = null, ids = setOf(1L, 2L), toTrash = false, now = 50L
        )
        assertEquals(listOf(1L, 2L), archived.archived.map { it.note.id })

        val trashed = RecordOperations.moveActiveRecordsToVault(
            snapshot, collectionId = null, ids = setOf(1L, 2L), toTrash = true, now = 50L
        )
        assertEquals(listOf(1L, 2L), trashed.trash.map { it.note.id })
    }

    @Test
    fun restoringArchivedRecordReturnsItToItsOriginalCollectionPosition() {
        val first = note(1L, "first")
        val restored = note(2L, "restored")
        val last = note(3L, "last")
        val entry = VaultNote(restored, collectionId = 10L, position = 1, movedAt = 100L)
        val snapshot = PersistenceSnapshot(
            notes = emptyList(),
            collections = listOf(NoteCollection(10L, "Work", listOf(first, last))),
            archived = listOf(entry),
            trash = emptyList()
        )

        val result = RecordOperations.restoreVaultEntry(snapshot, entry, fromArchive = true)

        assertEquals(listOf(1L, 2L, 3L), result.collections.single().notes.map { it.id })
        assertTrue(result.archived.isEmpty())
    }
    @Test
    fun restoringUsesLatestStoredVaultRecordInsteadOfStaleUiCopy() {
        val original = note(7L, "original")
        val latest = original.copy(title = "latest", blocks = listOf(NoteBlock("latest content")))
        val staleEntry = VaultNote(original, null, 0, 10L)
        val storedEntry = staleEntry.copy(note = latest, movedAt = 20L)
        val snapshot = PersistenceSnapshot(emptyList(), emptyList(), listOf(storedEntry), emptyList())

        val restored = RecordOperations.restoreVaultEntry(snapshot, staleEntry, fromArchive = true)

        assertEquals("latest", restored.notes.single().title)
        assertEquals("latest content", restored.notes.single().blocks.single().text)
    }

    @Test
    fun movingArchivedEntryUsesLatestStoredRecordInsteadOfStaleUiCopy() {
        val original = note(8L, "original")
        val latest = original.copy(title = "latest")
        val staleEntry = VaultNote(original, null, 0, 10L)
        val snapshot = PersistenceSnapshot(
            emptyList(), emptyList(), listOf(staleEntry.copy(note = latest)), emptyList()
        )

        val moved = RecordOperations.moveArchivedToTrash(snapshot, staleEntry, now = 50L)

        assertEquals("latest", moved.trash.single().note.title)
        assertEquals(50L, moved.trash.single().movedAt)
    }

    @Test
    fun restoringVaultEntriesOutOfOrderPreservesOriginalRootOrder() {
        val original = listOf(note(1L, "A"), note(2L, "B"), note(3L, "C"), note(4L, "D"), note(5L, "E"))
        val snapshot = PersistenceSnapshot(original, emptyList(), emptyList(), emptyList())
        val archived = RecordOperations.moveActiveRecordsToVault(
            snapshot, collectionId = null, ids = setOf(2L, 3L, 4L), toTrash = false, now = 50L
        )
        val c = archived.archived.first { it.note.id == 3L }
        val d = archived.archived.first { it.note.id == 4L }
        val b = archived.archived.first { it.note.id == 2L }

        val afterC = RecordOperations.restoreVaultEntry(archived, c, fromArchive = true)
        val afterD = RecordOperations.restoreVaultEntry(afterC, d, fromArchive = true)
        val afterB = RecordOperations.restoreVaultEntry(afterD, b, fromArchive = true)

        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), afterB.notes.map { it.id })
    }

    @Test
    fun restoringVaultEntriesOutOfOrderPreservesOriginalCollectionOrder() {
        val original = listOf(note(11L, "A"), note(12L, "B"), note(13L, "C"), note(14L, "D"), note(15L, "E"))
        val snapshot = PersistenceSnapshot(
            emptyList(), listOf(NoteCollection(90L, "Work", original)), emptyList(), emptyList()
        )
        val archived = RecordOperations.moveActiveRecordsToVault(
            snapshot, collectionId = 90L, ids = setOf(12L, 13L, 14L), toTrash = false, now = 50L
        )
        val c = archived.archived.first { it.note.id == 13L }
        val d = archived.archived.first { it.note.id == 14L }
        val b = archived.archived.first { it.note.id == 12L }

        val afterC = RecordOperations.restoreVaultEntry(archived, c, fromArchive = true)
        val afterD = RecordOperations.restoreVaultEntry(afterC, d, fromArchive = true)
        val afterB = RecordOperations.restoreVaultEntry(afterD, b, fromArchive = true)

        assertEquals(listOf(11L, 12L, 13L, 14L, 15L), afterB.collections.single().notes.map { it.id })
    }
    @Test
    fun backupMapsArchivedCollectionReferencesWhenCollectionsMergeByName() {
        val current = PersistenceSnapshot(
            emptyList(),
            listOf(NoteCollection(10L, "Work", listOf(note(1L, "first")))),
            emptyList(),
            emptyList()
        )
        val imported = BackupData(
            emptyList(),
            listOf(NoteCollection(20L, "work", listOf(note(2L, "second")))),
            listOf(VaultNote(note(3L, "third"), 20L, 2, 100L, previousRecordId = 2L)),
            emptyList()
        )

        val merged = RecordOperations.mergeBackup(current, imported)
        assertEquals(10L, merged.archived.single().collectionId)
        val restored = RecordOperations.restoreVaultEntry(merged, merged.archived.single(), fromArchive = true)
        assertEquals(listOf(1L, 2L, 3L), restored.collections.single().notes.map { it.id })
    }

    @Test
    fun backupMapsArchivedCollectionReferencesWhenCollectionIdCollides() {
        val current = PersistenceSnapshot(
            emptyList(),
            listOf(NoteCollection(20L, "Work", listOf(note(1L, "first")))),
            emptyList(),
            emptyList()
        )
        val imported = BackupData(
            emptyList(),
            listOf(NoteCollection(20L, "Home", listOf(note(2L, "second")))),
            listOf(VaultNote(note(3L, "third"), 20L, 1, 100L, previousRecordId = 2L)),
            emptyList()
        )

        val merged = RecordOperations.mergeBackup(current, imported)
        val importedCollection = merged.collections.first { it.name == "Home" }
        assertEquals(importedCollection.id, merged.archived.single().collectionId)
        val restored = RecordOperations.restoreVaultEntry(merged, merged.archived.single(), fromArchive = true)
        assertEquals(listOf(2L, 3L), restored.collections.first { it.name == "Home" }.notes.map { it.id })
    }
    @Test
    fun restoringEntriesAcrossArchiveAndTrashPreservesOriginalOrder() {
        val original = listOf(
            note(21L, "A"), note(22L, "B"), note(23L, "C"), note(24L, "D"), note(25L, "E")
        )
        val initial = PersistenceSnapshot(original, emptyList(), emptyList(), emptyList())
        val archived = RecordOperations.moveActiveRecordsToVault(
            initial, collectionId = null, ids = setOf(22L, 23L, 24L), toTrash = false, now = 50L
        )
        val c = archived.archived.first { it.note.id == 23L }
        val movedToTrash = RecordOperations.moveArchivedToTrash(archived, c, now = 60L)
        val b = movedToTrash.archived.first { it.note.id == 22L }
        val d = movedToTrash.archived.first { it.note.id == 24L }
        val trashedC = movedToTrash.trash.single()

        val afterB = RecordOperations.restoreVaultEntry(movedToTrash, b, fromArchive = true)
        val afterC = RecordOperations.restoreVaultEntry(afterB, trashedC, fromArchive = false)
        val afterD = RecordOperations.restoreVaultEntry(afterC, d, fromArchive = true)

        assertEquals(listOf(21L, 22L, 23L, 24L, 25L), afterD.notes.map { it.id })
    }
}
