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
