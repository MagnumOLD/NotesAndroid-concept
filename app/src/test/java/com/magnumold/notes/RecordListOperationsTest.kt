package com.magnumold.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class RecordListOperationsTest {
    private fun note(id: Long, pinned: Boolean = false) = Note(
        id = id,
        title = "",
        blocks = listOf(NoteBlock("record $id")),
        updated = 1_800_000_000_000L,
        created = 1_800_000_000_000L,
        pinned = pinned,
        homeOrder = id
    )

    @Test
    fun moduleReorderUsesTheSameOrderInTheUnifiedHomeTimeline() {
        val snapshot = PersistenceSnapshot(
            notes = listOf(note(1), note(2), note(3)),
            collections = emptyList(),
            archived = emptyList(),
            trash = emptyList()
        )

        val reordered = RecordListOperations.reorder(
            snapshot,
            collectionId = null,
            ids = setOf(3L),
            afterId = 1L
        )

        assertEquals(listOf(1L, 3L, 2L), reordered.notes.map { it.id })
        assertEquals(
            listOf(1L, 3L, 2L),
            HomeTimelineOperations.orderedVisibleRecords(reordered.notes).map { it.id }
        )
    }

    @Test
    fun moduleReorderRejectsSelectedDropAnchorButKeepsUnknownAnchorFallback() {
        val original = listOf(note(1), note(2), note(3))
        assertEquals(listOf(1L, 2L, 3L), NoteReorderModel.moveSelected(original, setOf(3L), afterId = 99L).map { it.id })
        assertEquals(original, NoteReorderModel.moveSelected(original, setOf(3L), afterId = 3L))
    }

    @Test
    fun moduleReorderCannotCrossPinnedPartition() {
        val original = listOf(note(1, pinned = true), note(2))
        assertEquals(original, NoteReorderModel.moveSelected(original, setOf(2L), afterId = 1L))
    }

    @Test
    fun pinningWithNoMatchingSelectionDoesNotReorderRecords() {
        val snapshot = PersistenceSnapshot(
            notes = listOf(note(1), note(2, pinned = true), note(3)),
            collections = emptyList(),
            archived = emptyList(),
            trash = emptyList()
        )

        assertSame(
            snapshot,
            RecordListOperations.togglePinned(snapshot, collectionId = null, ids = emptySet())
        )
        assertSame(
            snapshot,
            RecordListOperations.togglePinned(snapshot, collectionId = null, ids = setOf(999L))
        )
    }
}
