package com.magnumold.notes

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordStatusRulesTest {
    @Test
    fun overdueDeadlineMarksWaitingTaskMissed() {
        assertEquals(RecordStatus.MISSED, RecordStatusRules.applyDeadline(
            RecordStatus.WAITING, RecordDateMode.DEADLINE, 100L, 86_400_100L
        ))
    }

    @Test
    fun ordinaryDateDoesNotChangeTaskStatus() {
        assertEquals(RecordStatus.WAITING, RecordStatusRules.applyDeadline(
            RecordStatus.WAITING, RecordDateMode.DATE, 100L, 101L
        ))
    }

    @Test
    fun completedAndCancelledTasksDoNotBecomeMissed() {
        for (status in listOf(RecordStatus.COMPLETED, RecordStatus.CANCELLED)) {
            assertEquals(status, RecordStatusRules.applyDeadline(
                status, RecordDateMode.DEADLINE, 100L, 101L
            ))
        }
    }

    @Test
    fun movingDeadlineDoesNotAutomaticallyResumeMissedTask() {
        assertEquals(RecordStatus.MISSED, RecordStatusRules.applyDeadline(
            RecordStatus.MISSED, RecordDateMode.DEADLINE, 10_000L, 1L
        ))
    }

    @Test
    fun deadlineBecomesMissedAtEndOfSelectedDay() {
        assertEquals(RecordStatus.MISSED, RecordStatusRules.applyDeadline(
            RecordStatus.WAITING, RecordDateMode.DEADLINE, 500L, 86_400_500L
        ))
    }

    @Test
    fun futureDeadlineKeepsTaskWaiting() {
        assertEquals(RecordStatus.WAITING, RecordStatusRules.applyDeadline(
            RecordStatus.WAITING, RecordDateMode.DEADLINE, 86_400_500L, 86_400_499L
        ))
    }

    private fun localTime(
        zoneId: String,
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int
    ): Long = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone(zoneId)).apply {
        clear()
        set(year, month - 1, day, hour, minute, 0)
    }.timeInMillis

    private fun datePickerDay(year: Int, month: Int, day: Int): Long =
        java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(year, month - 1, day, 0, 0, 0)
        }.timeInMillis

    @Test
    fun deadlineRemainsActiveUntilTheEndOfTheSelectedLocalDay() {
        val zone = java.util.TimeZone.getTimeZone("Europe/Vienna")
        val deadline = datePickerDay(2026, 1, 2)

        assertEquals(RecordStatus.WAITING, RecordStatusRules.applyDeadline(
            RecordStatus.WAITING, RecordDateMode.DEADLINE, deadline,
            localTime("Europe/Vienna", 2026, 1, 2, 23, 30), zone
        ))
        assertEquals(RecordStatus.MISSED, RecordStatusRules.applyDeadline(
            RecordStatus.WAITING, RecordDateMode.DEADLINE, deadline,
            localTime("Europe/Vienna", 2026, 1, 3, 0, 1), zone
        ))
    }

    @Test
    fun deadlineUsesLocalCalendarDayAcrossSpringDstTransition() {
        val zone = java.util.TimeZone.getTimeZone("Europe/Vienna")
        val deadline = datePickerDay(2026, 3, 29)

        assertEquals(RecordStatus.WAITING, RecordStatusRules.applyDeadline(
            RecordStatus.WAITING, RecordDateMode.DEADLINE, deadline,
            localTime("Europe/Vienna", 2026, 3, 29, 23, 30), zone
        ))
        assertEquals(RecordStatus.MISSED, RecordStatusRules.applyDeadline(
            RecordStatus.WAITING, RecordDateMode.DEADLINE, deadline,
            localTime("Europe/Vienna", 2026, 3, 30, 0, 30), zone
        ))
    }

    @Test
    fun deadlineUsesLocalCalendarDayAcrossAutumnDstTransition() {
        val zone = java.util.TimeZone.getTimeZone("Europe/Vienna")
        val deadline = datePickerDay(2026, 10, 25)

        assertEquals(RecordStatus.WAITING, RecordStatusRules.applyDeadline(
            RecordStatus.WAITING, RecordDateMode.DEADLINE, deadline,
            localTime("Europe/Vienna", 2026, 10, 25, 23, 30), zone
        ))
        assertEquals(RecordStatus.MISSED, RecordStatusRules.applyDeadline(
            RecordStatus.WAITING, RecordDateMode.DEADLINE, deadline,
            localTime("Europe/Vienna", 2026, 10, 26, 0, 30), zone
        ))
    }

}
