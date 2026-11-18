package com.magnumold.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeTimelineOperationsTest {
    private fun note(id: Long, order: Long = id, created: Long = 86_400_000L) =
        Note(
            id = id,
            title = "",
            blocks = listOf(NoteBlock("record $id")),
            updated = created,
            created = created,
            homeOrder = order
        )

    @Test
    fun homeTimelineIncludesRootAndCollectionRecords() {
        val snapshot = PersistenceSnapshot(
            notes = listOf(note(1)),
            collections = listOf(NoteCollection(10, "Work", listOf(note(2)))),
            archived = emptyList(),
            trash = emptyList()
        )

        assertEquals(listOf(1L, 2L), HomeTimelineOperations.records(snapshot).map { it.id })
    }

    @Test
    fun editingCollectionRecordUpdatesItsOriginalCollection() {
        val original = note(2)
        val snapshot = PersistenceSnapshot(
            notes = listOf(note(1)),
            collections = listOf(NoteCollection(10, "Work", listOf(original))),
            archived = emptyList(),
            trash = emptyList()
        )

        val updated = HomeTimelineOperations.update(snapshot, original.copy(titleText = "Changed"))

        assertEquals("Changed", updated.collections.single().notes.single().titleText)
        assertEquals(1L, updated.notes.single().id)
    }

    @Test
    fun deletingCollectionRecordDoesNotDeleteItsCollectionOrOtherRecords() {
        val snapshot = PersistenceSnapshot(
            notes = listOf(note(1)),
            collections = listOf(NoteCollection(10, "Work", listOf(note(2), note(3)))),
            archived = emptyList(),
            trash = emptyList()
        )

        val updated = HomeTimelineOperations.remove(snapshot, 2)

        assertEquals(listOf(1L), updated.notes.map { it.id })
        assertEquals(listOf(3L), updated.collections.single().notes.map { it.id })
    }

    @Test
    fun movingCollectionRecordToArchivePreservesItsOrigin() {
        val snapshot = PersistenceSnapshot(
            notes = listOf(note(1)),
            collections = listOf(NoteCollection(10, "Work", listOf(note(2), note(3)))),
            archived = emptyList(),
            trash = emptyList()
        )

        val updated = HomeTimelineOperations.moveToVault(snapshot, setOf(2), toTrash = false)

        assertEquals(listOf(1L), updated.notes.map { it.id })
        assertEquals(listOf(3L), updated.collections.single().notes.map { it.id })
        assertEquals(10L, updated.archived.single().collectionId)
        assertEquals(0, updated.archived.single().position)
    }

    @Test
    fun hiddenRecordsRemainStoredAndCanBeUpdated() {
        val hidden = note(2).copy(showOnHome = false)
        val snapshot = PersistenceSnapshot(
            notes = listOf(note(1), hidden),
            collections = emptyList(),
            archived = emptyList(),
            trash = emptyList()
        )

        val updated = HomeTimelineOperations.update(snapshot, hidden.copy(titleText = "Hidden"))

        assertFalse(updated.notes.first { it.id == 2L }.showOnHome)
        assertEquals("Hidden", updated.notes.first { it.id == 2L }.titleText)
    }

    @Test
    fun homeTimelineReorderUpdatesOrderAcrossRootAndCollections() {
        val snapshot = PersistenceSnapshot(
            notes = listOf(note(1, order = 30)),
            collections = listOf(NoteCollection(10, "Work", listOf(note(2, order = 20), note(3, order = 10)))),
            archived = emptyList(),
            trash = emptyList()
        )

        val updated = HomeTimelineOperations.reorder(snapshot, setOf(3), afterId = 1)
        val ordered = HomeTimelineOperations.records(updated)
            .sortedByDescending { it.homeOrder }
            .map { it.id }

        assertEquals(listOf(1L, 3L, 2L), ordered)
        assertTrue(updated.collections.single().notes.any { it.id == 3L })
    }

    @Test
    fun homeTimelineReorderCannotMoveRecordAcrossScheduledDays() {
        val snapshot = PersistenceSnapshot(
            notes = listOf(
                note(1, order = 30, created = 3 * 86_400_000L),
                note(2, order = 20, created = 2 * 86_400_000L),
                note(3, order = 10, created = 2 * 86_400_000L)
            ),
            collections = emptyList(),
            archived = emptyList(),
            trash = emptyList()
        )

        val updated = HomeTimelineOperations.reorder(snapshot, setOf(2), afterId = null)
        val ordered = HomeTimelineOperations.orderedVisibleRecords(updated.notes)

        assertEquals(listOf(1L, 2L, 3L), ordered.map { it.id })
    }

    @Test
    fun homeTimelineKeepsPinnedRecordsInTheirOwnReorderPartition() {
        val snapshot = PersistenceSnapshot(
            notes = listOf(
                note(1, order = 30).copy(pinned = true),
                note(2, order = 20),
                note(3, order = 10).copy(pinned = true)
            ),
            collections = emptyList(),
            archived = emptyList(),
            trash = emptyList()
        )

        val updated = HomeTimelineOperations.reorder(snapshot, setOf(3), afterId = 1)
        val ordered = HomeTimelineOperations.orderedVisibleRecords(updated.notes)

        assertEquals(listOf(3L, 1L, 2L), ordered.map { it.id })
        assertTrue(ordered.take(2).all { it.pinned })
        assertFalse(ordered.last().pinned)
    }

    @Test
    fun homeTimelineSortsByScheduledDayAndHidesExcludedRecords() {
        val yesterday = note(1, order = 100, created = 86_400_000L)
        val today = note(2, order = 10, created = 2 * 86_400_000L)
            .copy(scheduledAt = 3 * 86_400_000L)
        val hidden = note(3, order = 200, created = 3 * 86_400_000L)
            .copy(showOnHome = false)
        val sameDayLater = note(4, order = 20, created = 3 * 86_400_000L)

        val ordered = HomeTimelineOperations.orderedVisibleRecords(
            listOf(yesterday, today, hidden, sameDayLater)
        )

        assertEquals(listOf(4L, 2L, 1L), ordered.map { it.id })
    }

    @Test
    fun unknownRecordUpdateIsNoOp() {
        val snapshot = PersistenceSnapshot(listOf(note(1)), emptyList(), emptyList(), emptyList())

        assertEquals(snapshot, HomeTimelineOperations.update(snapshot, note(99)))
        assertNull(HomeTimelineOperations.records(snapshot).firstOrNull { it.id == 99L })
    }
}
