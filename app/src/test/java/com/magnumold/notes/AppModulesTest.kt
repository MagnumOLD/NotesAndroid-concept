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
    @Test
    fun enabledModulesFollowConfiguredOrderAndExcludeDisabledModules() {
        val configuration = ModuleConfiguration(
            enabled = setOf(AppModule.PURCHASES, AppModule.TASKS),
            order = listOf(AppModule.PURCHASES, AppModule.NOTES, AppModule.TASKS)
        )
        assertEquals(listOf(AppModule.PURCHASES, AppModule.TASKS), configuration.enabledInOrder())
    }

    @Test
    fun disablingEveryModuleLeavesNoQuickCreateTarget() {
        assertTrue(ModuleConfiguration(enabled = emptySet()).enabledInOrder().isEmpty())
    }
    @Test
    fun disabledModuleRecordsAreHiddenWithoutDeletingTheirSourceData() {
        val records = listOf(
            Note(1L, "", listOf(NoteBlock("note")), 1L, type = RecordType.NOTE),
            Note(2L, "", listOf(NoteBlock("task")), 2L, type = RecordType.TASK),
            Note(3L, "", listOf(NoteBlock("purchase")), 3L, type = RecordType.PURCHASE)
        )
        val enabled = setOf(AppModule.NOTES, AppModule.TASKS)

        val visible = ModuleVisibilityOperations.visibleRecords(records, enabled)

        assertEquals(listOf(1L, 2L), visible.map { it.id })
        assertEquals(3, records.size)
    }

    @Test
    fun reenablingModuleMakesItsExistingRecordsVisibleAgain() {
        val records = listOf(
            Note(4L, "", listOf(NoteBlock("timer")), 4L, type = RecordType.TIMER),
            Note(5L, "", listOf(NoteBlock("nutrition")), 5L, type = RecordType.NUTRITION)
        )

        assertTrue(ModuleVisibilityOperations.visibleRecords(records, setOf(AppModule.TIMERS)).none { it.id == 5L })
        assertEquals(
            listOf(4L, 5L),
            ModuleVisibilityOperations.visibleRecords(records, setOf(AppModule.TIMERS, AppModule.NUTRITION)).map { it.id }
        )
    }

    @Test
    fun moduleReorderCreatesOneTemporaryGroupAtTheAnchorPosition() {
        val order = listOf(
            AppModule.NOTES, AppModule.TASKS, AppModule.TIMERS,
            AppModule.NUTRITION, AppModule.WORKOUTS, AppModule.PURCHASES
        )
        val rows = ModuleReorderModel.createRows(
            order,
            setOf(AppModule.TASKS, AppModule.NUTRITION),
            AppModule.NUTRITION
        )!!

        assertEquals(
            listOf("NOTES", "module-group-TASKS-NUTRITION", "TIMERS", "WORKOUTS", "PURCHASES"),
            rows.map { it.key }
        )
        assertEquals(listOf(AppModule.TASKS, AppModule.NUTRITION), rows[1].modules)
        assertEquals(order, ModuleReorderModel.flatten(rows))
    }

    @Test
    fun moduleReorderKeepsSelectedModulesInOriginalRelativeOrder() {
        val order = listOf(AppModule.NOTES, AppModule.TASKS, AppModule.TIMERS, AppModule.PURCHASES)
        val rows = ModuleReorderModel.createRows(
            order,
            setOf(AppModule.NOTES, AppModule.TIMERS),
            AppModule.TIMERS
        )!!

        assertEquals(
            listOf(AppModule.TASKS, AppModule.NOTES, AppModule.TIMERS, AppModule.PURCHASES),
            ModuleReorderModel.flatten(rows)
        )
        assertEquals(1, rows.count { it.isGroup })
    }

    @Test
    fun moduleReorderUsesSingleRowsForSingleSelection() {
        val order = listOf(AppModule.NOTES, AppModule.TASKS, AppModule.TIMERS)
        val rows = ModuleReorderModel.createRows(order, setOf(AppModule.TASKS), AppModule.TASKS)!!

        assertEquals(order, ModuleReorderModel.flatten(rows))
        assertTrue(rows.none { it.isGroup })
    }
}
