package com.magnumold.notes

import org.junit.Assert.assertEquals
import org.junit.Test

class ModuleSummaryOperationsTest {
    @Test
    fun nutritionSummaryTotalsOnlyTodayEntries() {
        val today = 200L
        fun entry(id: Long, day: Long, kcal: Double, protein: Double) =
            Note(id, "", listOf(NoteBlock()), day * 86_400_000L,
                type = RecordType.NUTRITION, titleText = "Еда",
                nutritionCalories = kcal, nutritionProtein = protein,
                scheduledAt = day * 86_400_000L)
        val records = listOf(entry(1, today, 300.0, 20.0), entry(2, today, 150.0, 10.0), entry(3, today - 1, 900.0, 50.0))

        assertEquals(
            "Сегодня: 450 ккал · Б 30 г · Ж 0 г · У 0 г",
            ModuleSummaryOperations.summary("КБЖУ", records, today)
        )
    }

    @Test
    fun purchaseSummarySeparatesBoughtAndRemaining() {
        val today = 300L
        val records = listOf(
            Note(1, "", listOf(NoteBlock()), 1L, type = RecordType.PURCHASE,
                titleText = "Хлеб", status = RecordStatus.WAITING, scheduledAt = today * 86_400_000L,
                purchaseQuantity = 2.0, purchaseUnitPrice = 1.5),
            Note(2, "", listOf(NoteBlock()), 1L, type = RecordType.PURCHASE,
                titleText = "Молоко", status = RecordStatus.COMPLETED, scheduledAt = today * 86_400_000L)
        )

        assertEquals("В списке: 1 · Куплено: 1 · Остаток: 3", ModuleSummaryOperations.summary("Покупки", records, today))
    }

    @Test
    fun emptyModuleSummaryIsUseful() {
        assertEquals("Сегодня пока нет записей о питании", ModuleSummaryOperations.summary("КБЖУ", emptyList(), 1L))
    }
}
