package com.magnumold.notes

/**
 * Operations for the unified home timeline. Records remain in their original
 * notebook/collection; only their home presentation order is shared.
 */
internal object HomeTimelineOperations {
    fun records(snapshot: PersistenceSnapshot): List<Note> =
        snapshot.notes + snapshot.collections.flatMap { it.notes }

    fun orderedVisibleRecords(notes: List<Note>): List<Note> =
        notes.filter { it.showOnHome }.sortedWith(
            compareByDescending<Note> { (it.scheduledAt ?: it.created) / 86_400_000L }
                .thenByDescending { it.pinned }
                .thenByDescending { it.homeOrder }
        )

    fun update(snapshot: PersistenceSnapshot, note: Note): PersistenceSnapshot {
        if (snapshot.notes.any { it.id == note.id }) {
            return snapshot.copy(notes = snapshot.notes.map { if (it.id == note.id) note else it })
        }
        val collections = snapshot.collections.map { collection ->
            collection.copy(notes = collection.notes.map { if (it.id == note.id) note else it })
        }
        return if (collections == snapshot.collections) snapshot
        else snapshot.copy(collections = collections)
    }

    fun remove(snapshot: PersistenceSnapshot, id: Long): PersistenceSnapshot {
        val root = snapshot.notes.filterNot { it.id == id }
        val collections = snapshot.collections.map { collection ->
            collection.copy(notes = collection.notes.filterNot { it.id == id })
        }
        return if (root == snapshot.notes && collections == snapshot.collections) snapshot
        else snapshot.copy(notes = root, collections = collections)
    }

    fun changeColor(snapshot: PersistenceSnapshot, id: Long, colorIndex: Int): PersistenceSnapshot =
        updateById(snapshot, id) { it.copy(colorIndex = colorIndex) }

    fun togglePinned(snapshot: PersistenceSnapshot, ids: Set<Long>): PersistenceSnapshot {
        if (ids.isEmpty()) return snapshot
        val selected = records(snapshot).filter { it.id in ids }
        if (selected.isEmpty()) return snapshot
        val allPinned = selected.all { it.pinned }
        return mapAll(snapshot) { note ->
            if (note.id in ids) note.copy(pinned = !allPinned) else note
        }
    }

    fun reorder(snapshot: PersistenceSnapshot, ids: Set<Long>, afterId: Long?): PersistenceSnapshot {
        if (ids.isEmpty()) return snapshot
        val ordered = orderedVisibleRecords(records(snapshot))
        val moving = ordered.filter { it.id in ids }
        if (moving.isEmpty()) return snapshot
        val movingDay = dayKey(moving.first())
        if (moving.any { dayKey(it) != movingDay } || moving.map { it.pinned }.distinct().size != 1) {
            return snapshot
        }

        val dayNotes = ordered.filter { dayKey(it) == movingDay }
        val remaining = dayNotes.filterNot { it.id in ids }
        val movingPinned = moving.first().pinned
        val pinnedCount = remaining.count { it.pinned }
        val anchorIndex = afterId?.let { id -> remaining.indexOfFirst { it.id == id } } ?: -1
        val targetIndex = if (anchorIndex >= 0) anchorIndex + 1 else if (movingPinned) 0 else pinnedCount
        val insertAt = if (movingPinned) targetIndex.coerceIn(0, pinnedCount)
            else targetIndex.coerceIn(pinnedCount, remaining.size)
        val reorderedDay = remaining.toMutableList().apply { addAll(insertAt, moving) }

        var dayInserted = false
        val reordered = buildList {
            ordered.forEach { note ->
                if (dayKey(note) == movingDay) {
                    if (!dayInserted) {
                        addAll(reorderedDay)
                        dayInserted = true
                    }
                } else add(note)
            }
        }
        if (reordered == ordered) return snapshot

        val orderBase = System.currentTimeMillis()
        val orderById = reordered.mapIndexed { index, note ->
            note.id to (orderBase + reordered.size - index)
        }.toMap()
        return mapAll(snapshot) { note ->
            orderById[note.id]?.let { note.copy(homeOrder = it) } ?: note
        }
    }

    private fun dayKey(note: Note): Long =
        (note.scheduledAt ?: note.created) / 86_400_000L

    fun moveToVault(
        snapshot: PersistenceSnapshot,
        ids: Set<Long>,
        toTrash: Boolean,
        now: Long = System.currentTimeMillis()
    ): PersistenceSnapshot {
        if (ids.isEmpty()) return snapshot
        val entries = buildList {
            snapshot.notes.forEachIndexed { index, note ->
                if (note.id in ids) add(VaultNote(note, null, index, now))
            }
            snapshot.collections.forEach { collection ->
                collection.notes.forEachIndexed { index, note ->
                    if (note.id in ids) add(VaultNote(note, collection.id, index, now))
                }
            }
        }
        if (entries.isEmpty()) return snapshot
        val remainingRoot = snapshot.notes.filterNot { it.id in ids }
        val remainingCollections = snapshot.collections.map { collection ->
            collection.copy(notes = collection.notes.filterNot { it.id in ids })
        }
        val updated = snapshot.copy(notes = remainingRoot, collections = remainingCollections)
        return if (toTrash) updated.copy(trash = entries.asReversed() + updated.trash)
        else updated.copy(archived = entries.asReversed() + updated.archived)
    }

    private fun updateById(
        snapshot: PersistenceSnapshot,
        id: Long,
        transform: (Note) -> Note
    ): PersistenceSnapshot = mapAll(snapshot) { if (it.id == id) transform(it) else it }

    private fun mapAll(
        snapshot: PersistenceSnapshot,
        transform: (Note) -> Note
    ): PersistenceSnapshot = snapshot.copy(
        notes = snapshot.notes.map(transform),
        collections = snapshot.collections.map { collection ->
            collection.copy(notes = collection.notes.map(transform))
        }
    )
}
