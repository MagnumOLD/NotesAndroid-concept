package com.magnumold.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
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
    @Test
    fun moduleSpecificValuesAloneKeepAnOtherwiseEmptyRecord() {
        val base = Note(20, "", listOf(NoteBlock()), 100L)
        assertEquals(true, base.copy(type = RecordType.PURCHASE, purchaseUnitPrice = 2.5).hasContent())
        assertEquals(true, base.copy(type = RecordType.WORKOUT, workoutWeightKg = 10.0).hasContent())
    }

    @Test
    fun moduleCardsShowUsefulSecondaryDetails() {
        val base = Note(21, "", listOf(NoteBlock()), 100L)
        val nutrition = base.copy(type = RecordType.NUTRITION, nutritionCalories = 250.0, nutritionProtein = 20.0)
        val purchase = base.copy(type = RecordType.PURCHASE, titleText = "Хлеб", purchaseQuantity = 2.0, purchaseUnitPrice = 1.5)
        val workout = base.copy(type = RecordType.WORKOUT, workoutSets = 3, workoutReps = 8, workoutWeightKg = 20.0)

        assertEquals("250 ккал · Б 20 · Ж 0 · У 0", recordCardSecondaryText(nutrition))
        assertEquals("2 × 1.5 = 3 · В списке", recordCardSecondaryText(purchase))
        assertEquals("3 × 8 повторений · 20 кг", recordCardSecondaryText(workout))
    }

    @Test
    fun taskCardsShowStatusDeadlineAndSubtaskProgress() {
        val task = Note(
            id = 30L,
            title = "",
            blocks = listOf(NoteBlock()),
            updated = 100L,
            type = RecordType.TASK,
            status = RecordStatus.IN_PROGRESS,
            dateMode = RecordDateMode.DEADLINE,
            dateAt = java.util.Calendar.getInstance().apply {
                clear()
                set(2026, java.util.Calendar.NOVEMBER, 20)
            }.timeInMillis,
            children = listOf(
                Note(31L, "", listOf(NoteBlock("Done")), 100L, type = RecordType.TASK, status = RecordStatus.COMPLETED),
                Note(32L, "", listOf(NoteBlock("Next")), 100L, type = RecordType.TASK)
            )
        )

        assertEquals("Выполняется · 1/2 · до 20.11", recordCardSecondaryText(task))
    }


    @Test
    fun vaultNeighborLinksSurviveBackupRoundTrip() {
        val records = listOf(
            Note(101L, "A", listOf(NoteBlock("A")), 101L),
            Note(102L, "B", listOf(NoteBlock("B")), 102L),
            Note(103L, "C", listOf(NoteBlock("C")), 103L)
        )
        val snapshot = PersistenceSnapshot(
            notes = listOf(records.first()),
            collections = emptyList(),
            archived = listOf(
                VaultNote(records[1], null, 1, 200L, previousRecordId = 101L, nextRecordId = 103L)
            ),
            trash = emptyList()
        )
        val decoded = parseBackupJson(createBackupJson(
            snapshot.notes, snapshot.collections, snapshot.archived, snapshot.trash
        ))

        assertEquals(101L, decoded.archived.single().previousRecordId)
        assertEquals(103L, decoded.archived.single().nextRecordId)
    }

    @Test
    fun importingTasksWithDifferentDeadlinesOrStatusesDoesNotMergeThem() {
        val created = 1_800_000_000_000L
        val waiting = Note(
            id = 100L,
            title = "",
            blocks = listOf(NoteBlock("Prepare report")),
            updated = created,
            created = created,
            type = RecordType.TASK,
            status = RecordStatus.WAITING,
            dateMode = RecordDateMode.DEADLINE,
            dateAt = created + 86_400_000L
        )
        val laterDeadline = waiting.copy(id = 101L, dateAt = created + 2 * 86_400_000L)
        val inProgress = waiting.copy(id = 102L, status = RecordStatus.IN_PROGRESS)

        val merged = mergeImportedNotes(emptyList(), listOf(waiting, laterDeadline, inProgress))

        assertEquals(3, merged.size)
        assertEquals(setOf(waiting.dateAt, laterDeadline.dateAt), merged.take(2).map { it.dateAt }.toSet())
        assertEquals(RecordStatus.IN_PROGRESS, merged.last().status)
    }

    @Test
    fun identicalBackupRecordsRemainIdempotentAcrossRepeatedImports() {
        val created = 1_800_000_000_000L
        val original = Note(
            id = 200L,
            title = "",
            blocks = listOf(NoteBlock("Buy milk", isChecklist = true)),
            updated = created,
            created = created,
            type = RecordType.TASK,
            dateMode = RecordDateMode.DATE,
            dateAt = created + 86_400_000L
        )

        val once = mergeImportedNotes(listOf(original), listOf(original.copy(
            blocks = original.blocks.map { it.copy(id = "different-block-id") }
        )))

        assertEquals(1, once.size)
    }

    @Test
    fun taskSchedulingStatusAndChecklistStateSurviveBackupRoundTrip() {
        val created = 1_800_000_000_000L
        val task = Note(
            id = 300L,
            title = "",
            blocks = listOf(
                NoteBlock("Prepare", checked = true, isChecklist = true, id = "checked"),
                NoteBlock("Review", checked = false, isChecklist = true, id = "unchecked")
            ),
            updated = created + 1_000L,
            created = created,
            type = RecordType.TASK,
            status = RecordStatus.IN_PROGRESS,
            dateMode = RecordDateMode.DEADLINE,
            dateAt = created + 3 * 86_400_000L,
            scheduledAt = created + 2 * 86_400_000L,
            deadlineResumeOverride = true,
            timeTrackingEnabled = true
        )

        val restored = parseBackupJson(
            createBackupJson(listOf(task), emptyList(), emptyList(), emptyList())
        ).notes.single()

        assertEquals(task.created, restored.created)
        assertEquals(task.status, restored.status)
        assertEquals(task.dateMode, restored.dateMode)
        assertEquals(task.dateAt, restored.dateAt)
        assertEquals(task.scheduledAt, restored.scheduledAt)
        assertEquals(task.deadlineResumeOverride, restored.deadlineResumeOverride)
        assertEquals(task.timeTrackingEnabled, restored.timeTrackingEnabled)
        assertEquals(task.blocks.map { Triple(it.text, it.checked, it.isChecklist) },
            restored.blocks.map { Triple(it.text, it.checked, it.isChecklist) })
    }
}
