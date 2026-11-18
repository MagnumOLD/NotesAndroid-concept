package com.magnumold.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardInteractionOperationsTest {
    @Test
    fun taskAndPurchaseCardsToggleCompletion() {
        val task = Note(1, "", listOf(NoteBlock()), 100L, type = RecordType.TASK)
        val completedTask = CardInteractionOperations.applyQuickAction(task, 200L)!!
        assertEquals(RecordStatus.COMPLETED, completedTask.status)
        assertEquals(RecordStatus.WAITING, CardInteractionOperations.applyQuickAction(completedTask, 300L)!!.status)

        val purchase = task.copy(type = RecordType.PURCHASE)
        assertEquals(RecordStatus.COMPLETED, CardInteractionOperations.applyQuickAction(purchase, 200L)!!.status)

        val workout = task.copy(type = RecordType.WORKOUT)
        assertEquals(RecordStatus.COMPLETED, CardInteractionOperations.applyQuickAction(workout, 200L)!!.status)
    }

    @Test
    fun timerCardStartsPausesAndResumesFromRemainingTime() {
        val timer = Note(2, "", listOf(NoteBlock()), 100L, type = RecordType.TIMER, timerDurationSeconds = 60L)
        val running = CardInteractionOperations.applyQuickAction(timer, 1_000L)!!
        assertEquals(61_000L, running.timerEndsAtMillis)
        val paused = CardInteractionOperations.applyQuickAction(running, 21_000L)!!
        assertEquals(40L, paused.timerRemainingSeconds)
        assertEquals(RecordStatus.PAUSED, paused.status)
        val resumed = CardInteractionOperations.applyQuickAction(paused, 30_000L)!!
        assertEquals(70_000L, resumed.timerEndsAtMillis)
        assertNull(resumed.timerRemainingSeconds)
    }

    @Test
    fun unsupportedRecordTypesHaveNoQuickAction() {
        val note = Note(3, "", listOf(NoteBlock()), 100L)
        assertNull(CardInteractionOperations.applyQuickAction(note, 200L))
    }
}
