package com.magnumold.notes

internal object RecordOperations {
    private fun countNotes(notes: List<Note>): Int =
        notes.sumOf { 1 + countNotes(it.children) }

    fun recordCount(snapshot: PersistenceSnapshot): Int =
        countNotes(snapshot.notes) +
            snapshot.collections.sumOf { countNotes(it.notes) } +
            snapshot.archived.sumOf { 1 + countNotes(it.note.children) } +
            snapshot.trash.sumOf { 1 + countNotes(it.note.children) }

    data class BackupMergeResult(
        val snapshot: PersistenceSnapshot,
        val importedNoteIdMap: Map<Long, Long>
    )

    fun mergeBackup(current: PersistenceSnapshot, imported: BackupData): PersistenceSnapshot =
        mergeBackupWithIdMap(current, imported).snapshot

    fun mergeBackupWithIdMap(
        current: PersistenceSnapshot,
        imported: BackupData,
        currentTimeTracking: TimeTrackingState = TimeTrackingState()
    ): BackupMergeResult {
        val usedNoteIds = mutableSetOf<Long>()
        fun collectNotes(notes: List<Note>) {
            notes.forEach { note ->
                usedNoteIds += note.id
                collectNotes(note.children)
            }
        }
        fun collectVault(entries: List<VaultNote>) = entries.forEach { collectNotes(listOf(it.note)) }
        collectNotes(current.notes)
        current.collections.forEach { collectNotes(it.notes) }
        collectVault(current.archived)
        collectVault(current.trash)
        // Time-tracking records can outlive deleted tasks. Reserve those IDs too,
        // otherwise an imported task could inherit an unrelated orphaned history.
        currentTimeTracking.sessions.forEach { session ->
            usedNoteIds += session.taskId
            session.parentTaskId?.let { usedNoteIds += it }
        }
        currentTimeTracking.activeSessions.values.forEach { session ->
            usedNoteIds += session.taskId
            session.parentTaskId?.let { usedNoteIds += it }
        }
        currentTimeTracking.protectionTimer?.let { timer ->
            usedNoteIds += timer.taskId
            timer.parentTaskId?.let { usedNoteIds += it }
        }
        val existingNoteIds = usedNoteIds.toSet()

        val importedNoteIdMap = linkedMapOf<Long, Long>()
        val collectionMerge = mergeImportedCollectionsWithIdMap(
            current.collections,
            imported.collections,
            usedNoteIds,
            importedNoteIdMap
        )
        fun remapVaultCollection(entry: VaultNote): VaultNote = entry.copy(
            collectionId = entry.collectionId?.let { collectionMerge.importedIdMap[it] ?: it }
        )
        val merged = current.copy(
            notes = mergeImportedNotes(current.notes, imported.notes, usedNoteIds, importedNoteIdMap),
            collections = collectionMerge.collections,
            archived = mergeImportedVault(
                current.archived,
                imported.archived.map(::remapVaultCollection),
                usedNoteIds,
                importedNoteIdMap
            ),
            trash = mergeImportedVault(
                current.trash,
                imported.trash.map(::remapVaultCollection),
                usedNoteIds,
                importedNoteIdMap
            )
        )

        // Vault neighbor links are record references too. Once every imported
        // record has been assigned its final ID, remap links on imported vault
        // entries without touching unrelated entries already in local storage.
        val importedVaultRootIds = (
            imported.archived.map { it.note.id } + imported.trash.map { it.note.id }
        ).mapTo(mutableSetOf()) { importedNoteIdMap[it] ?: it }
        val preexistingVaultRootIds = (current.archived + current.trash).mapTo(mutableSetOf()) { it.note.id }
        fun remapImportedVaultLinks(entries: List<VaultNote>): List<VaultNote> =
            entries.map { entry ->
                if (entry.note.id !in importedVaultRootIds || entry.note.id in preexistingVaultRootIds) entry
                else entry.copy(
                    previousRecordId = entry.previousRecordId?.let { importedNoteIdMap[it] ?: it },
                    nextRecordId = entry.nextRecordId?.let { importedNoteIdMap[it] ?: it }
                )
            }
        val mergedWithRemappedVaultLinks = merged.copy(
            archived = remapImportedVaultLinks(merged.archived),
            trash = remapImportedVaultLinks(merged.trash)
        )

        // Time-tracking history may outlive a deleted task. If such an orphan
        // ID collides with a local record, give the imported reference its own
        // reserved ID rather than accidentally attaching history to that record.
        val tracking = imported.timeTracking
        if (tracking != null) {
            val referencedIds = buildSet {
                tracking.sessions.forEach { session ->
                    add(session.taskId)
                    session.parentTaskId?.let(::add)
                }
                tracking.activeSessions.values.forEach { session ->
                    add(session.taskId)
                    session.parentTaskId?.let(::add)
                }
                tracking.protectionTimer?.let { timer ->
                    add(timer.taskId)
                    timer.parentTaskId?.let(::add)
                }
            }
            referencedIds.forEach { id ->
                if (id in existingNoteIds && id !in importedNoteIdMap) {
                    var candidate = System.currentTimeMillis().coerceAtLeast(0L)
                    while (!usedNoteIds.add(candidate)) {
                        candidate = if (candidate == Long.MAX_VALUE) Long.MIN_VALUE else candidate + 1L
                    }
                    importedNoteIdMap[id] = candidate
                }
            }
        }
        return BackupMergeResult(mergedWithRemappedVaultLinks, importedNoteIdMap)
    }

    fun updateTaskStatus(
        snapshot: PersistenceSnapshot,
        taskId: Long,
        status: RecordStatus
    ): PersistenceSnapshot {
        var found = false
        fun updateTree(notes: List<Note>): List<Note> = notes.map { note ->
            val children = updateTree(note.children)
            // Keep parent completion in sync when an external status change
            // completes or reopens one of its nested records.
            val withUpdatedChildren = if (children != note.children) {
                withUpdatedRecordChildren(note, children)
            } else note
            if (note.id == taskId && note.type == RecordType.TASK) {
                found = true
                withUpdatedChildren.copy(
                    status = status,
                    deadlineResumeOverride = status == RecordStatus.IN_PROGRESS,
                    children = children
                )
            } else withUpdatedChildren
        }
        val notes = updateTree(snapshot.notes)
        val collections = snapshot.collections.map { collection ->
            collection.copy(notes = updateTree(collection.notes))
        }
        fun updateVault(entries: List<VaultNote>): List<VaultNote> =
            entries.map { entry ->
                val updated = updateTree(listOf(entry.note)).single()
                if (updated == entry.note) entry else entry.copy(note = updated)
            }
        val archived = updateVault(snapshot.archived)
        val trash = updateVault(snapshot.trash)
        return if (!found) snapshot else snapshot.copy(
            notes = notes,
            collections = collections,
            archived = archived,
            trash = trash
        )
    }

    /**
     * Reconcile only service-owned countdown fields. The ViewModel may contain
     * newer unsaved edits, so replacing whole records from SharedPreferences
     * here could discard user input made during the persistence debounce.
     */
    fun refreshTimerStates(
        current: PersistenceSnapshot,
        persisted: PersistenceSnapshot
    ): PersistenceSnapshot {
        val timerStates = mutableMapOf<Long, Note>()
        fun collect(notes: List<Note>) {
            notes.forEach { note ->
                if (note.type == RecordType.TIMER) timerStates[note.id] = note
                collect(note.children)
            }
        }
        collect(persisted.notes)
        persisted.collections.forEach { collect(it.notes) }
        persisted.archived.forEach { collect(listOf(it.note)) }
        persisted.trash.forEach { collect(listOf(it.note)) }

        fun updateTree(notes: List<Note>): List<Note> = notes.map { note ->
            val children = updateTree(note.children)
            val timer = timerStates[note.id]?.takeIf { note.type == RecordType.TIMER }
            when {
                timer != null -> note.copy(
                    status = timer.status,
                    timerEndsAtMillis = timer.timerEndsAtMillis,
                    timerRemainingSeconds = timer.timerRemainingSeconds,
                    updated = timer.updated,
                    children = children
                )
                children != note.children -> note.copy(children = children)
                else -> note
            }
        }
        fun updateVault(entries: List<VaultNote>): List<VaultNote> =
            entries.map { entry ->
                val updated = updateTree(listOf(entry.note)).single()
                if (updated == entry.note) entry else entry.copy(note = updated)
            }
        return current.copy(
            notes = updateTree(current.notes),
            collections = current.collections.map { collection ->
                collection.copy(notes = updateTree(collection.notes))
            },
            archived = updateVault(current.archived),
            trash = updateVault(current.trash)
        )
    }

    fun restoreVaultEntry(
        snapshot: PersistenceSnapshot,
        entry: VaultNote,
        fromArchive: Boolean
    ): PersistenceSnapshot {
        val source = if (fromArchive) snapshot.archived else snapshot.trash
        val storedEntry = source.firstOrNull { it.note.id == entry.note.id } ?: return snapshot
        val neighborVault = snapshot.archived + snapshot.trash
        val withoutEntry = if (fromArchive) snapshot.copy(
            archived = snapshot.archived.filterNot { it.note.id == entry.note.id }
        ) else snapshot.copy(
            trash = snapshot.trash.filterNot { it.note.id == entry.note.id }
        )
        val collectionIndex = storedEntry.collectionId?.let { id ->
            withoutEntry.collections.indexOfFirst { it.id == id }
        } ?: -1
        return if (collectionIndex >= 0) {
            val collections = withoutEntry.collections.toMutableList()
            val target = collections[collectionIndex]
            val notes = insertAtOriginalPosition(target.notes, storedEntry, neighborVault)
            collections[collectionIndex] = target.copy(notes = notes)
            withoutEntry.copy(collections = collections)
        } else {
            withoutEntry.copy(notes = insertAtOriginalPosition(withoutEntry.notes, storedEntry, neighborVault))
        }
    }

    internal fun createVaultEntries(
        notes: List<Note>,
        collectionId: Long?,
        ids: Set<Long>,
        now: Long
    ): List<VaultNote> = notes.mapIndexedNotNull { index, note ->
        if (note.id !in ids) return@mapIndexedNotNull null
        VaultNote(
            note = note,
            collectionId = collectionId,
            position = index,
            movedAt = now,
            previousRecordId = notes.getOrNull(index - 1)?.id,
            nextRecordId = notes.getOrNull(index + 1)?.id
        )
    }

    private fun insertAtOriginalPosition(
        activeNotes: List<Note>,
        entry: VaultNote,
        sourceVault: List<VaultNote>
    ): List<Note> {
        fun nearestActiveNeighbor(startId: Long?, forward: Boolean): Int? {
            var candidateId = startId
            val visited = mutableSetOf(entry.note.id)
            while (candidateId != null && visited.add(candidateId)) {
                val activeIndex = activeNotes.indexOfFirst { it.id == candidateId }
                if (activeIndex >= 0) return activeIndex
                val vaulted = sourceVault.firstOrNull { it.note.id == candidateId } ?: return null
                candidateId = if (forward) vaulted.nextRecordId else vaulted.previousRecordId
            }
            return null
        }

        val nextIndex = nearestActiveNeighbor(entry.nextRecordId, forward = true)
        val previousIndex = nearestActiveNeighbor(entry.previousRecordId, forward = false)
        val insertionIndex = nextIndex ?: previousIndex?.plus(1)
            ?: entry.position.coerceIn(0, activeNotes.size)
        return activeNotes.toMutableList().apply {
            add(insertionIndex.coerceIn(0, size), entry.note)
        }
    }

    fun moveArchivedToTrash(
        snapshot: PersistenceSnapshot,
        entry: VaultNote,
        now: Long = System.currentTimeMillis()
    ): PersistenceSnapshot {
        val storedEntry = snapshot.archived.firstOrNull { it.note.id == entry.note.id } ?: return snapshot
        return snapshot.copy(
            archived = snapshot.archived.filterNot { it.note.id == entry.note.id },
            trash = listOf(storedEntry.copy(movedAt = now)) + snapshot.trash
        )
    }

    fun purgeExpiredTrash(
        snapshot: PersistenceSnapshot,
        retentionDays: Int,
        now: Long = System.currentTimeMillis()
    ): PersistenceSnapshot {
        if (retentionDays <= 0 || snapshot.trash.isEmpty()) return snapshot
        val retentionMillis = retentionDays.toLong() * TRASH_RETENTION_DAY_MILLIS
        val cutoff = if (now < Long.MIN_VALUE + retentionMillis) Long.MIN_VALUE
            else now - retentionMillis
        val retained = snapshot.trash.filter { it.movedAt >= cutoff }
        return if (retained.size == snapshot.trash.size) snapshot else snapshot.copy(trash = retained)
    }

    private const val TRASH_RETENTION_DAY_MILLIS = 86_400_000L

    fun deleteFromTrash(snapshot: PersistenceSnapshot, entry: VaultNote): PersistenceSnapshot =
        snapshot.copy(trash = snapshot.trash.filterNot { it.note.id == entry.note.id })

    fun moveActiveRecordsToVault(
        snapshot: PersistenceSnapshot,
        collectionId: Long?,
        ids: Set<Long>,
        toTrash: Boolean,
        now: Long = System.currentTimeMillis()
    ): PersistenceSnapshot {
        if (ids.isEmpty()) return snapshot
        val active = if (collectionId == null) snapshot.notes
            else snapshot.collections.firstOrNull { it.id == collectionId }?.notes ?: return snapshot
        val moving = active.filter { it.id in ids }
        if (moving.isEmpty()) return snapshot
        val entries = createVaultEntries(active, collectionId, ids, now)
        val remaining = active.filterNot { it.id in ids }
        val updated = if (collectionId == null) snapshot.copy(notes = remaining)
        else snapshot.copy(collections = snapshot.collections.map { collection ->
            if (collection.id == collectionId) collection.copy(notes = remaining) else collection
        })
        return if (toTrash) updated.copy(trash = entries + updated.trash)
        else updated.copy(archived = entries + updated.archived)
    }
}
