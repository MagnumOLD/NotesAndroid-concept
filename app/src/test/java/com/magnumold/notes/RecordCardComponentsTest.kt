package com.magnumold.notes

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordCardComponentsTest {
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
}
