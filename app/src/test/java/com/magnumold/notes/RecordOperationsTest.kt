package com.magnumold.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordOperationsTest {
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
}
