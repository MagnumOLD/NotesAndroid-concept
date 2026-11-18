package com.magnumold.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ModuleRecordBackupTest {
    @Test
    fun moduleSpecificFieldsSurviveBackupRoundTrip() {
        val now = 1_800_000_000_000L
        val records = listOf(
            Note(1, "", listOf(NoteBlock()), now, created = now, type = RecordType.NUTRITION,
                titleText = "Овсянка", nutritionGrams = 80.0, nutritionCalories = 296.0,
                nutritionProtein = 10.0, nutritionFat = 6.0, nutritionCarbs = 54.0),
            Note(2, "", listOf(NoteBlock()), now, created = now, type = RecordType.PURCHASE,
                titleText = "Молоко", purchaseQuantity = 2.0, purchaseUnitPrice = 1.75),
            Note(3, "", listOf(NoteBlock()), now, created = now, type = RecordType.WORKOUT,
                titleText = "Подтягивания", workoutSets = 4, workoutReps = 8, workoutWeightKg = 5.0)
        )

        val decoded = parseBackupJson(
            createBackupJson(records, emptyList(), emptyList(), emptyList())
        ).notes

        assertEquals(80.0, decoded[0].nutritionGrams, 0.0)
        assertEquals(296.0, decoded[0].nutritionCalories, 0.0)
        assertEquals(2.0, decoded[1].purchaseQuantity, 0.0)
        assertEquals(1.75, decoded[1].purchaseUnitPrice, 0.0)
        assertEquals(4, decoded[2].workoutSets)
        assertEquals(8, decoded[2].workoutReps)
        assertEquals(5.0, decoded[2].workoutWeightKg, 0.0)
    }

    @Test
    fun importingDifferentShoppingQuantitiesDoesNotMergeThem() {
        val base = Note(10, "", listOf(NoteBlock()), 100L, type = RecordType.PURCHASE,
            titleText = "Кофе", purchaseQuantity = 1.0, purchaseUnitPrice = 5.0)
        val otherQuantity = base.copy(id = 11, purchaseQuantity = 2.0)

        val merged = mergeImportedNotes(listOf(base), listOf(otherQuantity))

        assertEquals(2, merged.size)
    }

    @Test
    fun moduleCardTitlesHaveUsefulFallbacks() {
        val base = Note(1, "", listOf(NoteBlock()), 100L)
        assertEquals("Таймер", recordCardPrimaryText(base.copy(type = RecordType.TIMER)))
        assertEquals("Запись КБЖУ", recordCardPrimaryText(base.copy(type = RecordType.NUTRITION)))
        assertEquals("Тренировка", recordCardPrimaryText(base.copy(type = RecordType.WORKOUT)))
        assertNotEquals("Пустая заметка", recordCardPrimaryText(base.copy(type = RecordType.WORKOUT, titleText = "Приседания")))
    }
}
