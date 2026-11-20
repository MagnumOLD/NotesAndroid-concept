package com.magnumold.notes

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordCardComponentsTest {
    @Test
    fun primaryCardTextUsesModuleSpecificTitles() {
        fun record(type: RecordType, title: String = "") = Note(
            id = type.ordinal.toLong() + 1L,
            title = "",
            blocks = listOf(NoteBlock("")),
            updated = 1L,
            type = type,
            titleText = title
        )

        assertEquals("Таймер", recordCardPrimaryText(record(RecordType.TIMER)))
        assertEquals("Запись КБЖУ", recordCardPrimaryText(record(RecordType.NUTRITION)))
        assertEquals("Тренировка", recordCardPrimaryText(record(RecordType.WORKOUT)))
        assertEquals("Молоко", recordCardPrimaryText(record(RecordType.PURCHASE, "Молоко")))
        assertEquals("Позвонить", recordCardPrimaryText(record(RecordType.TASK, "Позвонить")))
    }

    @Test
    fun purchaseCardPreviewPreservesLargeCalculatedTotal() {
        val previousLocale = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.US)
            val purchase = Note(
                id = 2L,
                title = "",
                blocks = emptyList(),
                updated = 1L,
                type = RecordType.PURCHASE,
                purchaseQuantity = 1.0e12,
                purchaseUnitPrice = 1.0e12
            )

            val preview = recordCardSecondaryText(purchase).orEmpty()

            org.junit.Assert.assertTrue(preview.contains("= 1000000000000000000000000"))
        } finally {
            java.util.Locale.setDefault(previousLocale)
        }
    }

    @Test
    fun dateOnlyTaskPreviewDoesNotShiftInTimeZonesWestOfUtc() {
        val previousZone = java.util.TimeZone.getDefault()
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/Los_Angeles"))
            val date = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
                clear()
                set(2026, java.util.Calendar.JANUARY, 2, 0, 0, 0)
            }.timeInMillis
            val task = Note(
                id = 1L,
                title = "",
                blocks = emptyList(),
                updated = date,
                type = RecordType.TASK,
                dateMode = RecordDateMode.DEADLINE,
                dateAt = date
            )

            assertEquals(true, recordCardSecondaryText(task).orEmpty().endsWith("до 02.01"))
        } finally {
            java.util.TimeZone.setDefault(previousZone)
        }
    }

    @Test
    fun purchaseCardPreviewUsesExactDecimalMultiplication() {
        val previousLocale = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.US)
            val purchase = Note(
                id = 22L,
                title = "",
                blocks = emptyList(),
                updated = 1L,
                type = RecordType.PURCHASE,
                purchaseQuantity = 3.0,
                purchaseUnitPrice = 0.1
            )
            assertEquals("3 × 0.1 = 0.3 · В списке", recordCardSecondaryText(purchase))
        } finally {
            java.util.Locale.setDefault(previousLocale)
        }
    }

    @Test
    fun nutritionCardPreviewUsesWholeKcalLikeTheEditorAndDailySummary() {
        val previousLocale = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.US)
            val nutrition = Note(
                id = 23L,
                title = "",
                blocks = emptyList(),
                updated = 1L,
                type = RecordType.NUTRITION,
                nutritionCalories = 125.9,
                nutritionProtein = 12.5
            )
            assertEquals("126 ккал · Б 12.5", recordCardSecondaryText(nutrition))
        } finally {
            java.util.Locale.setDefault(previousLocale)
        }
    }

}
