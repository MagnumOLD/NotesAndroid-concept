package com.magnumold.notes

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotesRepositoryTest {
    @Test
    fun legacyKitchenTimersMigrateIntoMainRecordsWithSettingsAndWithoutDuplicates() {
        val context = RuntimeEnvironment.getApplication()
        val recordPreferences = context.getSharedPreferences(
            "notes_repository_kitchen_migration_test", Context.MODE_PRIVATE
        )
        val legacyPreferences = context.getSharedPreferences(
            "kitchen_timers_migration_test", Context.MODE_PRIVATE
        )
        recordPreferences.edit().clear().commit()
        legacyPreferences.edit().clear().commit()

        val legacyTimer = KitchenTimer(
            id = 1_700_000_000_123L,
            title = "Гречка",
            durationSeconds = 900L,
            remainingSeconds = 420L,
            soundUri = "content://alarm/test",
            status = KitchenTimerStatus.PAUSED,
            colorIndex = 3
        )
        val runningTimer = KitchenTimer(
            id = legacyTimer.id + 1L,
            title = "Чай",
            durationSeconds = 120L,
            endsAtMillis = 1_700_000_060_123L,
            soundUri = null,
            status = KitchenTimerStatus.RUNNING
        )
        val finishedTimer = KitchenTimer(
            id = legacyTimer.id + 2L,
            title = "Кофе",
            durationSeconds = 60L,
            remainingSeconds = 0L,
            status = KitchenTimerStatus.FINISHED,
            alertStartedAtMillis = 1_700_000_000_500L
        )
        val legacyRepository = KitchenTimerRepository(legacyPreferences)
        legacyRepository.saveTimers(listOf(legacyTimer, runningTimer, finishedTimer))
        val settings = KitchenTimerSettings(
            TimerAlertDurationMode.TEN_SECONDS,
            customAlertDurationSeconds = 47,
            defaultSoundUri = "content://alarm/default"
        )
        legacyRepository.saveSettings(settings)

        val repository = NotesRepository(recordPreferences)
        repository.migrateLegacyKitchenTimers(legacyPreferences)

        val migratedTimers = HomeTimelineOperations.records(repository.loadSnapshot())
            .filter { it.type == RecordType.TIMER }
        val migrated = migratedTimers.first { it.titleText == "Гречка" }
        assertEquals(legacyTimer.id, migrated.id)
        assertEquals(legacyTimer.id, migrated.created)
        assertEquals("Гречка", migrated.titleText)
        assertEquals(RecordStatus.PAUSED, migrated.status)
        assertEquals(420L, migrated.timerRemainingSeconds)
        assertEquals("content://alarm/test", migrated.timerSoundUri)
        assertEquals(3, migrated.colorIndex)
        assertEquals(settings, repository.loadSnapshot().timerSettings)
        assertTrue(repository.loadSnapshot().hasUniqueRecordIds())
        val migratedRunning = migratedTimers.first { it.titleText == "Чай" }
        assertEquals(RecordStatus.IN_PROGRESS, migratedRunning.status)
        assertEquals(runningTimer.endsAtMillis, migratedRunning.timerEndsAtMillis)
        val migratedFinished = migratedTimers.first { it.titleText == "Кофе" }
        assertEquals(RecordStatus.COMPLETED, migratedFinished.status)
        assertEquals(finishedTimer.alertStartedAtMillis, migratedFinished.timerAlertStartedAtMillis)
        assertTrue(!legacyPreferences.contains("kitchen_timers_v1"))

        repository.migrateLegacyKitchenTimers(legacyPreferences)
        assertEquals(
            3,
            HomeTimelineOperations.records(repository.loadSnapshot()).count { it.type == RecordType.TIMER }
        )
    }

    @Test
    fun legacyAlarmIdMapFollowsTimerWhenItsIdCollidesDuringMigration() {
        val context = RuntimeEnvironment.getApplication()
        val recordPreferences = context.getSharedPreferences(
            "notes_repository_timer_id_collision_test", Context.MODE_PRIVATE
        )
        val legacyPreferences = context.getSharedPreferences(
            "kitchen_timers_id_collision_test", Context.MODE_PRIVATE
        )
        recordPreferences.edit().clear().commit()
        legacyPreferences.edit().clear().commit()

        val legacyId = 1_700_000_000_456L
        val repository = NotesRepository(recordPreferences)
        repository.flushSnapshot(PersistenceSnapshot(
            notes = listOf(Note(
                id = legacyId,
                title = "",
                blocks = listOf(NoteBlock("Existing record")),
                updated = legacyId
            )),
            collections = emptyList(),
            archived = emptyList(),
            trash = emptyList()
        ))
        KitchenTimerRepository(legacyPreferences).saveTimers(listOf(
            KitchenTimer(id = legacyId, title = "Старый таймер", durationSeconds = 90L)
        ))

        repository.migrateLegacyKitchenTimers(legacyPreferences)

        val migrated = HomeTimelineOperations.records(repository.loadSnapshot())
            .single { it.type == RecordType.TIMER }
        assertTrue(migrated.id != legacyId)
        assertEquals(legacyId, migrated.created)
        val mapping = org.json.JSONObject(
            recordPreferences.getString(NotesRepository.KEY_KITCHEN_TIMER_LEGACY_ID_MAP, "{}") ?: "{}"
        )
        assertEquals(migrated.id, mapping.getLong(legacyId.toString()))
        assertEquals("Existing record", repository.loadSnapshot().notes
            .single { it.id == legacyId }.blocks.single().text)
    }

    @Test
    fun firstLaunchWithoutLegacyTimersKeepsTimerListEmptyAndDoesNotRecreateDeletedTimer() {
        val context = RuntimeEnvironment.getApplication()
        val recordPreferences = context.getSharedPreferences(
            "notes_repository_empty_timer_migration_test", Context.MODE_PRIVATE
        )
        val legacyPreferences = context.getSharedPreferences(
            "kitchen_timers_empty_migration_test", Context.MODE_PRIVATE
        )
        recordPreferences.edit().clear().commit()
        legacyPreferences.edit().clear().commit()

        val repository = NotesRepository(recordPreferences)
        repository.migrateLegacyKitchenTimers(legacyPreferences)

        assertTrue(HomeTimelineOperations.records(repository.loadSnapshot())
            .none { it.type == RecordType.TIMER })
        assertTrue(recordPreferences.getBoolean("kitchen_timers_migrated_v1", false))

        repository.migrateLegacyKitchenTimers(legacyPreferences)
        assertTrue(HomeTimelineOperations.records(repository.loadSnapshot())
            .none { it.type == RecordType.TIMER })

        // A timer explicitly created by the user can be deleted permanently.
        val manuallyCreated = Note(
            id = 88L,
            title = "",
            blocks = listOf(NoteBlock()),
            updated = 88L,
            created = 88L,
            type = RecordType.TIMER,
            timerDurationSeconds = 240L
        )
        repository.flushSnapshot(repository.loadSnapshot().copy(notes = listOf(manuallyCreated)))
        repository.flushSnapshot(repository.loadSnapshot().copy(notes = emptyList()))
        repository.migrateLegacyKitchenTimers(legacyPreferences)
        assertTrue(HomeTimelineOperations.records(repository.loadSnapshot())
            .none { it.type == RecordType.TIMER })
    }

    @Test
    fun migrationWithoutLegacyTimersPreservesExistingCanonicalTimers() {
        val context = RuntimeEnvironment.getApplication()
        val recordPreferences = context.getSharedPreferences(
            "notes_repository_existing_timer_migration_test", Context.MODE_PRIVATE
        )
        val legacyPreferences = context.getSharedPreferences(
            "kitchen_timers_existing_timer_migration_test", Context.MODE_PRIVATE
        )
        recordPreferences.edit().clear().commit()
        legacyPreferences.edit().clear().commit()

        val existing = Note(
            id = 77L,
            title = "",
            blocks = listOf(NoteBlock()),
            updated = 77L,
            created = 77L,
            type = RecordType.TIMER,
            timerDurationSeconds = 180L
        )
        val repository = NotesRepository(recordPreferences)
        repository.flushSnapshot(PersistenceSnapshot(
            notes = listOf(existing),
            collections = emptyList(),
            archived = emptyList(),
            trash = emptyList()
        ))

        repository.migrateLegacyKitchenTimers(legacyPreferences)

        val timers = HomeTimelineOperations.records(repository.loadSnapshot())
            .filter { it.type == RecordType.TIMER }
        assertEquals(1, timers.size)
        assertEquals(77L, timers.single().id)
        assertEquals(180L, timers.single().timerDurationSeconds)
    }

    @Test
    fun findTaskResolvesNestedTasksAcrossActiveArchiveAndTrash() {
        val preferences = RuntimeEnvironment.getApplication().getSharedPreferences(
            "notes_repository_find_task_test", Context.MODE_PRIVATE
        )
        preferences.edit().clear().commit()
        val repository = NotesRepository(preferences)
        val archivedChild = Note(3L, "", listOf(NoteBlock("Archived child")), 3L, type = RecordType.TASK)
        val trashedChild = Note(5L, "", listOf(NoteBlock("Trashed child")), 5L, type = RecordType.TASK)
        repository.flushSnapshot(PersistenceSnapshot(
            notes = listOf(Note(1L, "", listOf(NoteBlock("Active")), 1L)),
            collections = listOf(NoteCollection(10L, "Work", listOf(
                Note(2L, "", listOf(NoteBlock("Parent")), 2L, children = listOf(
                    Note(4L, "", listOf(NoteBlock("Collection child")), 4L)
                ))
            ))),
            archived = listOf(VaultNote(
                Note(6L, "", listOf(NoteBlock("Archived parent")), 6L, children = listOf(archivedChild)),
                null, 0, 10L
            )),
            trash = listOf(VaultNote(
                Note(7L, "", listOf(NoteBlock("Trashed parent")), 7L, children = listOf(trashedChild)),
                null, 0, 20L
            ))
        ))

        assertEquals("Active", repository.findTask(1L)?.blocks?.single()?.text)
        assertEquals("Collection child", repository.findTask(4L)?.blocks?.single()?.text)
        assertEquals("Archived child", repository.findTask(3L)?.blocks?.single()?.text)
        assertEquals("Trashed child", repository.findTask(5L)?.blocks?.single()?.text)
        assertEquals(null, repository.findTask(999L))
    }

    @Test
    fun externalTaskStatusUpdatesNestedTasksInArchiveAndTrash() {
        val preferences = RuntimeEnvironment.getApplication().getSharedPreferences(
            "notes_repository_external_status_test",
            Context.MODE_PRIVATE
        )
        preferences.edit().clear().commit()
        val repository = NotesRepository(preferences)

        fun task(id: Long, text: String, children: List<Note> = emptyList()) = Note(
            id = id,
            title = "",
            blocks = listOf(NoteBlock(text)),
            updated = id,
            type = RecordType.TASK,
            status = RecordStatus.WAITING,
            children = children
        )
        val archivedParent = task(10L, "Archived parent", listOf(task(11L, "Archived child")))
        val trashedParent = task(20L, "Trashed parent", listOf(task(21L, "Trashed child")))
        repository.flushSnapshot(
            PersistenceSnapshot(
                notes = emptyList(),
                collections = emptyList(),
                archived = listOf(VaultNote(archivedParent, null, 0, 100L)),
                trash = listOf(VaultNote(trashedParent, null, 0, 200L))
            )
        )

        assertTrue(repository.updateTaskStatus(11L, RecordStatus.COMPLETED))
        assertTrue(repository.updateTaskStatus(21L, RecordStatus.COMPLETED))

        val restored = repository.loadSnapshot()
        assertEquals(RecordStatus.COMPLETED, restored.archived.single().note.status)
        assertEquals(RecordStatus.COMPLETED, restored.archived.single().note.children.single().status)
        assertEquals(RecordStatus.COMPLETED, restored.trash.single().note.status)
        assertEquals(RecordStatus.COMPLETED, restored.trash.single().note.children.single().status)
        assertEquals(RecordStatus.WAITING, restored.archived.single().note.statusBeforeAutoCompletion)
        assertEquals(RecordStatus.WAITING, restored.trash.single().note.statusBeforeAutoCompletion)
    }
}
