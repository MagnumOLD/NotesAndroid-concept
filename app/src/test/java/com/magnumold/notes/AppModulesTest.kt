package com.magnumold.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppModulesTest {
    @Test
    fun defaultConfigurationEnablesEveryFirstPartyModule() {
        val configuration = ModuleConfiguration().normalized()
        assertEquals(6, configuration.enabled.size)
        assertEquals(6, configuration.order.size)
        assertTrue(configuration.enabled.contains(AppModule.NOTES))
        assertTrue(configuration.enabled.contains(AppModule.PURCHASES))
    }

    @Test
    fun disablingModuleDoesNotRemoveItFromOrderOrOtherModules() {
        val current = ModuleConfiguration()
        val updated = current.copy(enabled = current.enabled - AppModule.WORKOUTS).normalized()
        assertTrue(AppModule.WORKOUTS !in updated.enabled)
        assertTrue(AppModule.WORKOUTS in updated.order)
        assertEquals(current.enabled.size - 1, updated.enabled.size)
    }

    @Test
    fun normalizationRestoresMissingModulesAndRemovesDuplicates() {
        val broken = ModuleConfiguration(
            enabled = setOf(AppModule.NOTES, AppModule.ALL),
            order = listOf(AppModule.TASKS, AppModule.TASKS, AppModule.ALL)
        ).normalized()
        assertEquals(6, broken.order.size)
        assertEquals(1, broken.order.count { it == AppModule.TASKS })
        assertTrue(AppModule.ALL !in broken.order)
        assertTrue(AppModule.ALL !in broken.enabled)
    }

    @Test
    fun eachFunctionalModuleMapsToItsOwnRecordType() {
        assertEquals(RecordType.NOTE, AppModule.NOTES.recordType)
        assertEquals(RecordType.TASK, AppModule.TASKS.recordType)
        assertEquals(RecordType.TIMER, AppModule.TIMERS.recordType)
        assertEquals(RecordType.NUTRITION, AppModule.NUTRITION.recordType)
        assertEquals(RecordType.WORKOUT, AppModule.WORKOUTS.recordType)
        assertEquals(RecordType.PURCHASE, AppModule.PURCHASES.recordType)
    }
}
