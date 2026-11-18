package com.magnumold.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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
    fun importedRecordCountIncludesNestedTasksInEveryStorageArea() {
        fun record(id: Long, children: List<Note> = emptyList()) = Note(
            id = id,
            title = "",
            blocks = listOf(NoteBlock("Record $id")),
            updated = id,
            children = children
        )
        val snapshot = PersistenceSnapshot(
            notes = listOf(record(1, listOf(record(2)))),
            collections = listOf(NoteCollection(10, "Work", listOf(record(3, listOf(record(4)))))),
            archived = listOf(VaultNote(record(5, listOf(record(6))), null, 0, 100)),
            trash = listOf(VaultNote(record(7, listOf(record(8))), null, 0, 200))
        )

        assertEquals(8, RecordOperations.recordCount(snapshot))
    }

    @Test
    fun malformedVaultEntryRejectsBackupInsteadOfSilentlyDroppingIt() {
        val malformed = """{"format":"notes_backup","version":11,"notes":[],"collections":[],"archive":[{"note":null}]}"""
        try {
            parseBackupJson(malformed)
            fail("Malformed archive entries must reject the backup")
        } catch (_: Exception) {
            // Expected: callers must not import a partially decoded backup.
        }
    }

    @Test
    fun backupWithDuplicateNestedRecordIdsIsRejectedBeforeImport() {
        val malformed = """{"format":"notes_backup","version":12,"notes":[{"id":1,"blocks":[{"text":"Parent"}],"children":[{"id":1,"blocks":[{"text":"Child"}]}]}],"collections":[],"archive":[],"trash":[]}"""
        try {
            parseBackupJson(malformed)
            fail("Duplicate IDs make tracking references ambiguous and must reject the backup")
        } catch (_: IllegalArgumentException) {
            // Expected: a malformed backup must not be partially imported.
        }
    }


    @Test
    fun backupNormalizesNonFiniteAndOutOfRangeModuleNumbers() {
        val raw = """{"format":"notes_backup","version":12,"notes":[
            {"id":1,"type":"NUTRITION","titleText":"Food","nutritionCalories":"Infinity","nutritionProtein":-4,"nutritionFat":99999999999,"blocks":[]},
            {"id":2,"type":"PURCHASE","titleText":"Item","purchaseQuantity":"Infinity","purchaseUnitPrice":-8,"blocks":[]},
            {"id":3,"type":"WORKOUT","titleText":"Exercise","workoutWeightKg":"Infinity","blocks":[]}
        ],"collections":[],"archive":[],"trash":[]}"""

        val notes = parseBackupJson(raw).notes
        assertEquals(0.0, notes[0].nutritionCalories, 0.0)
        assertEquals(0.0, notes[0].nutritionProtein, 0.0)
        assertEquals(9_999_999_999.0, notes[0].nutritionFat, 0.0)
        assertEquals(1.0, notes[1].purchaseQuantity, 0.0)
        assertEquals(0.0, notes[1].purchaseUnitPrice, 0.0)
        assertEquals(0.0, notes[2].workoutWeightKg, 0.0)
        assertTrue(notes.all { values ->
            listOf(values.nutritionCalories, values.nutritionProtein, values.nutritionFat,
                values.purchaseQuantity, values.purchaseUnitPrice, values.workoutWeightKg)
                .all { it.isFinite() }
        })
    }

    @Test
    fun backupRejectsRecordIdCollisionsAcrossCollectionsAndVault() {
        val malformedBackups = listOf(
            """{"format":"notes_backup","version":12,"notes":[{"id":42,"blocks":[{"text":"Root"}]}],"collections":[{"id":7,"name":"Work","notes":[{"id":42,"blocks":[{"text":"Collection copy"}]}]}],"archive":[],"trash":[]}""",
            """{"format":"notes_backup","version":12,"notes":[{"id":42,"blocks":[{"text":"Root"}]}],"collections":[],"archive":[{"note":{"id":42,"blocks":[{"text":"Archived copy"}]}}],"trash":[]}""",
            """{"format":"notes_backup","version":12,"notes":[],"collections":[{"id":7,"name":"Work","notes":[{"id":42,"blocks":[{"text":"Collection copy"}]}]}],"archive":[],"trash":[{"note":{"id":42,"blocks":[{"text":"Trash copy"}]}}]}"""
        )

        malformedBackups.forEach { malformed ->
            try {
                parseBackupJson(malformed)
                fail("Record IDs must be unique across active, collection, archive and trash storage")
            } catch (_: IllegalArgumentException) {
                // Expected: the importer must reject ambiguous tracking references.
            }
        }
    }

    @Test
    fun backupWithDuplicateCollectionIdsIsRejected() {
        val malformed = """{"format":"notes_backup","version":12,"notes":[],"collections":[{"id":7,"name":"A","notes":[]},{"id":7,"name":"B","notes":[]}],"archive":[],"trash":[]}"""
        try {
            parseBackupJson(malformed)
            fail("Duplicate collection IDs must reject the backup")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    @Test
    fun importingNestedRecordsRekeysIdsThatCollideAnywhereInTheTree() {
        val existing = Note(
            id = 1L,
            title = "",
            blocks = listOf(NoteBlock("Existing parent")),
            updated = 1L,
            children = listOf(
                Note(2L, "", listOf(NoteBlock("Existing child")), 2L)
            )
        )
        val imported = Note(
            id = 3L,
            title = "",
            blocks = listOf(NoteBlock("Imported parent")),
            updated = 3L,
            children = listOf(
                Note(
                    2L,
                    "",
                    listOf(NoteBlock("Imported child")),
                    4L,
                    children = listOf(Note(3L, "", listOf(NoteBlock("Grandchild")), 5L))
                )
            )
        )

        val merged = mergeImportedNotes(listOf(existing), listOf(imported))
        val ids = mutableListOf<Long>()
        fun collect(notes: List<Note>) {
            notes.forEach { note ->
                ids += note.id
                collect(note.children)
            }
        }
        collect(merged)

        assertEquals(5, ids.size)
        assertEquals(5, ids.toSet().size)
        assertEquals("Imported child", merged.last().children.single().blocks.single().text)
        assertEquals("Grandchild", merged.last().children.single().children.single().blocks.single().text)
        assertNotEquals(2L, merged.last().children.single().id)
        assertNotEquals(3L, merged.last().children.single().children.single().id)
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
    fun importingTaskDoesNotReuseIdFromOrphanedLocalTimeTracking() {
        val currentTracking = TimeTrackingState(
            activeSessions = mapOf(
                77L to ActiveWorkSession(
                    id = "local-active",
                    taskId = 77L,
                    parentTaskId = null,
                    taskTitle = "Previously deleted task",
                    subtaskTitle = null,
                    startedAtMillis = 100L
                )
            )
        )
        val importedTask = Note(
            id = 77L,
            title = "",
            blocks = listOf(NoteBlock("Imported task")),
            updated = 200L,
            type = RecordType.TASK
        )
        val importedTracking = TimeTrackingState(
            sessions = listOf(
                WorkSession(
                    id = "imported-session",
                    taskId = 77L,
                    parentTaskId = null,
                    taskTitle = "Imported task",
                    subtaskTitle = null,
                    startedAtMillis = 200L,
                    endedAtMillis = 300L,
                    durationMillis = 100L,
                    endReason = WorkSessionEndReason.STOP
                )
            )
        )
        val imported = BackupData(
            notes = listOf(importedTask),
            collections = emptyList(),
            archived = emptyList(),
            trash = emptyList(),
            timeTracking = importedTracking
        )

        val merged = RecordOperations.mergeBackupWithIdMap(
            PersistenceSnapshot(emptyList(), emptyList(), emptyList(), emptyList()),
            imported,
            currentTracking
        )
        val importedRecord = merged.snapshot.notes.single()
        val remappedTracking = remapTimeTrackingTaskIds(
            importedTracking,
            merged.importedNoteIdMap
        )

        assertNotEquals(77L, importedRecord.id)
        assertEquals(importedRecord.id, remappedTracking.sessions.single().taskId)
        assertEquals(77L, currentTracking.activeSessions.keys.single())
        assertEquals("Previously deleted task", currentTracking.activeSessions[77L]?.taskTitle)
    }

    @Test
    fun importedVaultNeighborLinksFollowRekeyedActiveRecordIds() {
        val currentRecord = Note(
            id = 200L,
            title = "",
            blocks = listOf(NoteBlock("Local record")),
            updated = 100L
        )
        val importedNeighbor = Note(
            id = 200L,
            title = "",
            blocks = listOf(NoteBlock("Imported neighbor")),
            updated = 200L
        )
        val importedArchived = Note(
            id = 300L,
            title = "",
            blocks = listOf(NoteBlock("Archived record")),
            updated = 300L
        )
        val imported = BackupData(
            notes = listOf(importedNeighbor),
            collections = emptyList(),
            archived = listOf(
                VaultNote(
                    note = importedArchived,
                    collectionId = null,
                    position = 1,
                    movedAt = 400L,
                    previousRecordId = 200L
                )
            ),
            trash = emptyList()
        )

        val merged = RecordOperations.mergeBackupWithIdMap(
            PersistenceSnapshot(
                notes = listOf(currentRecord),
                collections = emptyList(),
                archived = emptyList(),
                trash = emptyList()
            ),
            imported
        )

        val importedNeighborId = merged.importedNoteIdMap.getValue(200L)
        assertNotEquals(currentRecord.id, importedNeighborId)
        assertEquals(importedNeighborId, merged.snapshot.archived.single().previousRecordId)
    }

    @Test
    fun repeatedVaultImportDoesNotRewriteExistingNeighborLinks() {
        val localNeighbor = Note(200L, "", listOf(NoteBlock("Local")), 100L)
        val archivedNote = Note(300L, "", listOf(NoteBlock("Archived")), 300L)
        val current = PersistenceSnapshot(
            notes = listOf(localNeighbor),
            collections = emptyList(),
            archived = listOf(
                VaultNote(
                    note = archivedNote,
                    collectionId = null,
                    position = 0,
                    movedAt = 400L,
                    previousRecordId = 200L
                )
            ),
            trash = emptyList()
        )
        val imported = BackupData(
            notes = listOf(Note(200L, "", listOf(NoteBlock("Imported")), 200L)),
            collections = emptyList(),
            archived = listOf(
                VaultNote(
                    note = archivedNote.copy(id = 400L),
                    collectionId = null,
                    position = 0,
                    movedAt = 500L,
                    previousRecordId = 200L
                )
            ),
            trash = emptyList()
        )

        val merged = RecordOperations.mergeBackupWithIdMap(current, imported)

        assertEquals(1, merged.snapshot.archived.size)
        assertEquals(200L, merged.snapshot.archived.single().previousRecordId)
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
