package com.magnumold.notes

import org.junit.Assert.assertEquals
import org.junit.Test

class TrashRetentionTest {
    private fun note(id: Long) = Note(
        id = id,
        title = "",
        blocks = listOf(NoteBlock("record $id")),
        updated = 1L,
        created = 1L
    )

    @Test
    fun expiredTrashEntriesArePurgedAndRecentEntriesRemain() {
        val day = 86_400_000L
        val now = 100L * day
        val snapshot = PersistenceSnapshot(
            notes = emptyList(),
            collections = emptyList(),
            archived = emptyList(),
            trash = listOf(
                VaultNote(note(1), null, 0, now - 31L * day),
                VaultNote(note(2), null, 1, now - 30L * day),
                VaultNote(note(3), null, 2, now - 2L * day)
            )
        )

        val result = RecordOperations.purgeExpiredTrash(snapshot, retentionDays = 30, now = now)

        assertEquals(listOf(2L, 3L), result.trash.map { it.note.id })
        assertEquals(emptyList<Note>(), result.notes)
    }

    @Test
    fun zeroRetentionKeepsTrashIndefinitely() {
        val snapshot = PersistenceSnapshot(
            emptyList(), emptyList(), emptyList(),
            listOf(VaultNote(note(1), null, 0, 1L))
        )

        assertEquals(snapshot, RecordOperations.purgeExpiredTrash(snapshot, retentionDays = 0, now = Long.MAX_VALUE))
    }

    @Test
    fun movingArchivedEntryToTrashRestartsItsRetentionPeriod() {
        val archived = VaultNote(note(1), null, 0, 1L)
        val snapshot = PersistenceSnapshot(emptyList(), emptyList(), listOf(archived), emptyList())
        val now = 5_000_000L

        val result = RecordOperations.moveArchivedToTrash(snapshot, archived, now)

        assertEquals(now, result.trash.single().movedAt)
        assertEquals(emptyList<VaultNote>(), result.archived)
    }
}
