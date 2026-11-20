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

    @Test
    fun taskSummaryShowsCompletionAndMissedCounts() {
        val today = 400L
        val records = listOf(
            Note(1, "", listOf(NoteBlock()), 1L, type = RecordType.TASK,
                status = RecordStatus.COMPLETED, scheduledAt = today * 86_400_000L),
            Note(2, "", listOf(NoteBlock()), 1L, type = RecordType.TASK,
                status = RecordStatus.WAITING, scheduledAt = today * 86_400_000L),
            Note(3, "", listOf(NoteBlock()), 1L, type = RecordType.TASK,
                status = RecordStatus.MISSED, scheduledAt = today * 86_400_000L)
        )

        assertEquals("Задач: 3 · Выполнено: 1 · Просрочено: 1", ModuleSummaryOperations.summary("Задачи", records, today))
    }

    @Test
    fun taskSummaryUsesTheSuppliedInstantForDeadlineStatus() {
        val previousZone = java.util.TimeZone.getDefault()
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"))
            val day = 600L
            val dayStart = day * 86_400_000L
            val task = Note(
                1, "", listOf(NoteBlock()), dayStart,
                type = RecordType.TASK,
                status = RecordStatus.WAITING,
                dateMode = RecordDateMode.DEADLINE,
                dateAt = dayStart,
                scheduledAt = dayStart
            )

            val beforeDeadlineDayEnds = ModuleSummaryOperations.summary(
                "Задачи", listOf(task), day, dayStart + 23L * 60L * 60L * 1_000L
            )
            val afterDeadlineDayEnds = ModuleSummaryOperations.summary(
                "Задачи", listOf(task), day, dayStart + 86_400_000L
            )

            assertEquals("Задач: 1 · Выполнено: 0 · Просрочено: 0", beforeDeadlineDayEnds)
            assertEquals("Задач: 1 · Выполнено: 0 · Просрочено: 1", afterDeadlineDayEnds)
        } finally {
            java.util.TimeZone.setDefault(previousZone)
        }
    }

    @Test
    fun timerSummaryCountsOnlyTimersThatAreActuallyRunning() {
        val today = 500L
        val now = today * 86_400_000L + 12_000L
        val records = listOf(
            Note(1, "", listOf(NoteBlock()), 1L, type = RecordType.TIMER,
                status = RecordStatus.IN_PROGRESS, timerEndsAtMillis = now + 30_000L,
                scheduledAt = today * 86_400_000L),
            Note(2, "", listOf(NoteBlock()), 1L, type = RecordType.TIMER,
                status = RecordStatus.IN_PROGRESS, timerEndsAtMillis = now - 1_000L,
                scheduledAt = today * 86_400_000L),
            Note(3, "", listOf(NoteBlock()), 1L, type = RecordType.TIMER,
                status = RecordStatus.PAUSED, timerEndsAtMillis = now + 60_000L,
                scheduledAt = today * 86_400_000L)
        )

        assertEquals("Таймеров: 3 · Активно: 1",
            ModuleSummaryOperations.summary("Таймеры", records, today, now))
    }

    @Test
    fun purchaseSummaryFormatsTotalsBeyondLongRangeWithoutSaturation() {
        val today = 700L
        val record = Note(
            1, "", listOf(NoteBlock()), 1L,
            type = RecordType.PURCHASE,
            status = RecordStatus.WAITING,
            scheduledAt = today * 86_400_000L,
            purchaseQuantity = 1_000_000_000_000.0,
            purchaseUnitPrice = 10_000_000.0
        )

        assertEquals(
            "В списке: 1 · Куплено: 0 · Остаток: 10000000000000000000",
            ModuleSummaryOperations.summary("Покупки", listOf(record), today)
        )
    }

    @Test
    fun nutritionSummaryIgnoresNegativeAndNonFiniteImportedValues() {
        val day = 800L
        val entry = Note(
            1, "", listOf(NoteBlock()), 1L,
            type = RecordType.NUTRITION,
            scheduledAt = day * 86_400_000L,
            nutritionCalories = 250.0,
            nutritionProtein = -10.0,
            nutritionFat = Double.NaN,
            nutritionCarbs = Double.POSITIVE_INFINITY
        )
        assertEquals(
            "Сегодня: 250 ккал · Б 0 г · Ж 0 г · У 0 г",
            ModuleSummaryOperations.summary("КБЖУ", listOf(entry), day)
        )
    }

    @Test
    fun purchaseSummaryIgnoresNegativeAndNonFiniteImportedAmounts() {
        val day = 801L
        val entry = Note(
            1, "", listOf(NoteBlock()), 1L,
            type = RecordType.PURCHASE,
            status = RecordStatus.WAITING,
            scheduledAt = day * 86_400_000L,
            purchaseQuantity = Double.NaN,
            purchaseUnitPrice = -50.0
        )
        assertEquals(
            "В списке: 1 · Куплено: 0 · Остаток: 0",
            ModuleSummaryOperations.summary("Покупки", listOf(entry), day)
        )
    }

    @Test
    fun purchaseSummaryAvoidsBinaryFloatingPointArtifactsAcrossEntries() {
        val day = 802L
        val records = listOf(
            Note(1, "", emptyList(), 1L, type = RecordType.PURCHASE,
                status = RecordStatus.WAITING, scheduledAt = day * 86_400_000L,
                purchaseQuantity = 3.0, purchaseUnitPrice = 0.1),
            Note(2, "", emptyList(), 1L, type = RecordType.PURCHASE,
                status = RecordStatus.WAITING, scheduledAt = day * 86_400_000L,
                purchaseQuantity = 3.0, purchaseUnitPrice = 0.2)
        )
        assertEquals(
            "В списке: 2 · Куплено: 0 · Остаток: 0.9",
            ModuleSummaryOperations.summary("Покупки", records, day)
        )
    }
}
