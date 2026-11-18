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

        val updated = HomeTimelineOperations.reorder(snapshot, setOf(3), afterId = null)
        val ordered = HomeTimelineOperations.orderedVisibleRecords(updated.notes)

        assertEquals(listOf(3L, 1L, 2L), ordered.map { it.id })
        assertTrue(ordered.take(2).all { it.pinned })
        assertFalse(ordered.last().pinned)
    }

    @Test
    fun dateRangeFiltersCoverTodayTomorrowWeekMonthAndHistory() {
        val today = 100L
        val records = listOf(
            note(1, created = (today - 1) * 86_400_000L),
            note(2, created = today * 86_400_000L),
            note(3, created = (today + 1) * 86_400_000L),
            note(4, created = (today + 6) * 86_400_000L),
            note(5, created = (today + 7) * 86_400_000L),
            note(6, created = (today + 29) * 86_400_000L),
            note(7, created = (today + 30) * 86_400_000L)
        )

        assertEquals(listOf(2L), HomeTimelineOperations.filterByRange(records, HomeTimelineRange.TODAY, today).map { it.id })
        assertEquals(listOf(3L), HomeTimelineOperations.filterByRange(records, HomeTimelineRange.TOMORROW, today).map { it.id })
        assertEquals(listOf(2L, 3L, 4L), HomeTimelineOperations.filterByRange(records, HomeTimelineRange.WEEK, today).map { it.id })
        assertEquals(listOf(2L, 3L, 4L, 5L, 6L), HomeTimelineOperations.filterByRange(records, HomeTimelineRange.MONTH, today).map { it.id })
        assertEquals(listOf(1L), HomeTimelineOperations.filterByRange(records, HomeTimelineRange.HISTORY, today).map { it.id })
    }

    @Test
    fun createdTimestampUsesDeviceLocalCalendarDay() {
        val utc = java.util.TimeZone.getTimeZone("UTC")
        val helsinki = java.util.TimeZone.getTimeZone("Europe/Helsinki")
        val timestamp = java.util.Calendar.getInstance(utc).apply {
            clear()
            set(2026, java.util.Calendar.JANUARY, 1, 22, 30, 0)
        }.timeInMillis
        val expectedDay = java.util.Calendar.getInstance(utc).apply {
            clear()
            set(2026, java.util.Calendar.JANUARY, 2, 0, 0, 0)
        }.timeInMillis / 86_400_000L

        assertEquals(expectedDay, timelineDayKey(timestamp, isDateOnly = false, timeZone = helsinki))
    }

    @Test
    fun scheduledDateUsesDatePickerUtcCalendarDay() {
        val utc = java.util.TimeZone.getTimeZone("UTC")
        val helsinki = java.util.TimeZone.getTimeZone("Europe/Helsinki")
        val datePickerMillis = java.util.Calendar.getInstance(utc).apply {
            clear()
            set(2026, java.util.Calendar.JANUARY, 2, 0, 0, 0)
        }.timeInMillis
        val expectedDay = datePickerMillis / 86_400_000L

        assertEquals(expectedDay, timelineDayKey(datePickerMillis, isDateOnly = true, timeZone = helsinki))
    }

    @Test
    fun dateRangeFiltersUseTheSameCalendarDayKeyAsTimelineSorting() {
        val today = currentTimelineDayKey()
        val scheduled = note(1, created = 0L).copy(scheduledAt = today * 86_400_000L)
        val previous = note(2, created = 0L).copy(scheduledAt = (today - 1) * 86_400_000L)
        val next = note(3, created = 0L).copy(scheduledAt = (today + 1) * 86_400_000L)

        assertEquals(listOf(1L), HomeTimelineOperations.filterByRange(listOf(scheduled, previous, next), HomeTimelineRange.TODAY, today).map { it.id })
        assertEquals(listOf(3L), HomeTimelineOperations.filterByRange(listOf(scheduled, previous, next), HomeTimelineRange.TOMORROW, today).map { it.id })
    }

    @Test
    fun favoritesRangeReturnsOnlyFavoriteRecords() {
        val records = listOf(
            note(1).copy(favorite = true),
            note(2),
            note(3).copy(favorite = true)
        )
        assertEquals(
            listOf(1L, 3L),
            HomeTimelineOperations.filterByRange(records, HomeTimelineRange.FAVORITES, 100L).map { it.id }
        )
    }


    @Test
    fun favoritesAreGroupedByModuleAndOrderedByDateInsideEachGroup() {
        val records = listOf(
            note(1, created = 5 * 86_400_000L).copy(type = RecordType.TASK, favorite = true),
            note(2, created = 8 * 86_400_000L).copy(type = RecordType.NOTE, favorite = true),
            note(3, created = 9 * 86_400_000L).copy(type = RecordType.TASK, favorite = true),
            note(4, created = 10 * 86_400_000L).copy(type = RecordType.NOTE),
            note(5, created = 7 * 86_400_000L).copy(type = RecordType.PURCHASE, favorite = true)
        )

        val ordered = HomeTimelineOperations.orderedFavoritesByModule(records)

        assertEquals(listOf(2L, 1L, 3L, 5L), ordered.map { it.id })
        assertEquals("Заметки", HomeTimelineOperations.moduleSectionTitle(RecordType.NOTE))
        assertEquals("Задачи", HomeTimelineOperations.moduleSectionTitle(RecordType.TASK))
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
