package com.magnumold.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking

/**
 * Owns the complete record state and its persistence lifecycle.
 * UI state (navigation, selection, dialogs and theme) remains in Compose.
 */
internal class NotesViewModel(
    private val repository: NotesRepository,
    private val timeTrackingRepository: TimeTrackingRepository,
    private val recoveryDataRepository: RecoveryDataRepository
) : ViewModel() {
    private val _snapshot = MutableStateFlow(repository.loadSnapshot())
    val snapshot: StateFlow<PersistenceSnapshot> = _snapshot.asStateFlow()

    private var persistenceJob: kotlinx.coroutines.Job? = null

    init {
        startPersistenceObserver()
    }

    private fun startPersistenceObserver() {
        persistenceJob = viewModelScope.launch(Dispatchers.IO) {
            _snapshot
                .debounce(PERSISTENCE_DEBOUNCE_MS)
                .distinctUntilChanged()
                .collectLatest { value ->
                    repository.saveSnapshot(value)
                }
        }
    }

    /**
     * Finish any in-flight debounced write before committing the newest
     * snapshot, so an older queued write cannot overwrite the lifecycle flush.
     */
    fun flush() {
        val pendingJob = persistenceJob
        persistenceJob = null
        runBlocking {
            pendingJob?.cancelAndJoin()
        }
        try {
            repository.flushSnapshot(_snapshot.value)
        } finally {
            // A failed synchronous commit must not permanently disable
            // autosave for every edit made later in this ViewModel's lifetime.
            startPersistenceObserver()
        }
    }

    fun refreshFromRepository() {
        _snapshot.value = repository.loadSnapshot()
    }

    private fun updateSnapshot(transform: (PersistenceSnapshot) -> PersistenceSnapshot) {
        _snapshot.update(transform)
    }

    fun createCollection(id: Long, name: String) {
        updateSnapshot { CollectionOperations.create(it, id, name) }
    }

    fun renameCollection(id: Long, name: String) {
        updateSnapshot { CollectionOperations.rename(it, id, name) }
    }

    fun deleteCollection(id: Long): Boolean {
        if (_snapshot.value.collections.none { it.id == id }) return false
        updateSnapshot { CollectionOperations.delete(it, id) }
        return true
    }

    fun moveCollection(id: Long, delta: Int) {
        updateSnapshot { CollectionOperations.move(it, id, delta) }
    }

    fun transferRecords(sourceId: Long?, targetId: Long?, ids: Set<Long>) {
        updateSnapshot { CollectionOperations.transfer(it, sourceId, targetId, ids) }
    }

    fun updateHomeRecord(note: Note) {
        updateSnapshot { HomeTimelineOperations.update(it, note) }
    }

    fun removeHomeRecord(noteId: Long) {
        updateSnapshot { HomeTimelineOperations.remove(it, noteId) }
    }

    fun reorderHomeRecords(ids: Set<Long>, afterId: Long?) {
        updateSnapshot { HomeTimelineOperations.reorder(it, ids, afterId) }
    }

    fun toggleHomeRecordsPinned(ids: Set<Long>) {
        updateSnapshot { HomeTimelineOperations.togglePinned(it, ids) }
    }

    fun changeHomeRecordColor(noteId: Long, colorIndex: Int) {
        updateSnapshot { HomeTimelineOperations.changeColor(it, noteId, colorIndex) }
    }

    fun moveHomeRecordsToVault(ids: Set<Long>, toTrash: Boolean) {
        updateSnapshot { HomeTimelineOperations.moveToVault(it, ids, toTrash) }
    }

    fun createActiveRecord(collectionId: Long?, note: Note) {
        updateSnapshot { RecordListOperations.create(it, collectionId, note) }
    }

    fun updateActiveRecord(collectionId: Long?, note: Note) {
        updateSnapshot { RecordListOperations.update(it, collectionId, note) }
    }

    fun removeActiveRecord(collectionId: Long?, noteId: Long) {
        updateSnapshot { RecordListOperations.remove(it, collectionId, noteId) }
    }

    fun reorderActiveRecords(collectionId: Long?, ids: Set<Long>, afterId: Long?) {
        updateSnapshot { RecordListOperations.reorder(it, collectionId, ids, afterId) }
    }

    fun toggleActiveRecordsPinned(collectionId: Long?, ids: Set<Long>) {
        updateSnapshot { RecordListOperations.togglePinned(it, collectionId, ids) }
    }

    fun changeActiveRecordColor(collectionId: Long?, noteId: Long, colorIndex: Int) {
        updateSnapshot { RecordListOperations.changeColor(it, collectionId, noteId, colorIndex) }
    }

    fun importBackup(json: String): BackupImportResult {
        val imported = parseBackupJson(json)
        val before = _snapshot.value
        val currentTimeTracking = timeTrackingRepository.load()
        val merge = RecordOperations.mergeBackupWithIdMap(before, imported, currentTimeTracking)
        val mergedTimeTracking = imported.timeTracking?.let { backup ->
            val remappedBackup = remapTimeTrackingTaskIds(backup, merge.importedNoteIdMap)
            mergeTimeTrackingBackup(currentTimeTracking, remappedBackup)
        }
        val importedCount = RecordOperations.recordCount(merge.snapshot) - RecordOperations.recordCount(before)

        // Persist the merged records and tracking references as one logical
        // operation. If the record snapshot cannot be committed, roll back the
        // tracking state so it cannot point at records that were never saved.
        if (mergedTimeTracking != null) timeTrackingRepository.save(mergedTimeTracking)
        updateSnapshot { merge.snapshot }
        try {
            flush()
        } catch (failure: Exception) {
            _snapshot.value = before
            runCatching { repository.flushSnapshot(before) }
                .exceptionOrNull()?.let(failure::addSuppressed)
            if (mergedTimeTracking != null) {
                runCatching { timeTrackingRepository.restoreBackup(currentTimeTracking) }
                    .exceptionOrNull()?.let(failure::addSuppressed)
            }
            throw failure
        }

        // Recovery payloads are imported only after the main data commit succeeds.
        val importedRecoveryCount = recoveryDataRepository.importEntries(imported.recoveryEntries)
        return BackupImportResult(importedCount, importedRecoveryCount)
    }

    fun restoreVaultEntry(entry: VaultNote, fromArchive: Boolean) {
        updateSnapshot { RecordOperations.restoreVaultEntry(it, entry, fromArchive) }
    }

    fun moveArchivedToTrash(entry: VaultNote) {
        updateSnapshot { RecordOperations.moveArchivedToTrash(it, entry) }
    }

    fun purgeExpiredTrash(retentionDays: Int, now: Long = System.currentTimeMillis()) {
        updateSnapshot { RecordOperations.purgeExpiredTrash(it, retentionDays, now) }
    }

    fun deleteFromTrash(entry: VaultNote) {
        updateSnapshot { RecordOperations.deleteFromTrash(it, entry) }
    }

    fun emptyTrash() {
        updateSnapshot { it.copy(trash = emptyList()) }
    }

    fun moveActiveRecordsToVault(collectionId: Long?, ids: Set<Long>, toTrash: Boolean) {
        updateSnapshot { RecordOperations.moveActiveRecordsToVault(it, collectionId, ids, toTrash) }
    }

    private companion object {
        const val PERSISTENCE_DEBOUNCE_MS = 300L
    }
}

internal class NotesViewModelFactory(
    private val repository: NotesRepository,
    private val timeTrackingRepository: TimeTrackingRepository,
    private val recoveryDataRepository: RecoveryDataRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(NotesViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        return NotesViewModel(repository, timeTrackingRepository, recoveryDataRepository) as T
    }
}
