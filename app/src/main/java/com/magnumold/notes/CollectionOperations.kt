package com.magnumold.notes

/**
 * Pure snapshot transformations for collection operations.
 * These functions keep list mutation rules out of the Compose coordinator.
 */
internal object CollectionOperations {
    fun create(snapshot: PersistenceSnapshot, id: Long, name: String): PersistenceSnapshot {
        val normalizedName = name.trim()
        if (normalizedName.isEmpty() || snapshot.collections.any {
                it.id == id || it.name.equals(normalizedName, ignoreCase = true)
            }
        ) return snapshot
        val updated = snapshot.copy(collections = snapshot.collections + NoteCollection(id, normalizedName))
        return updated.takeIf { it.hasValidIdentifiers() } ?: snapshot
    }

    fun rename(snapshot: PersistenceSnapshot, id: Long, name: String): PersistenceSnapshot {
        val normalizedName = name.trim()
        val current = snapshot.collections.firstOrNull { it.id == id } ?: return snapshot
        if (normalizedName.isEmpty() || snapshot.collections.any {
                it.id != id && it.name.equals(normalizedName, ignoreCase = true)
            }
        ) return snapshot
        if (current.name == normalizedName) return snapshot
        return snapshot.copy(collections = snapshot.collections.map { collection ->
            if (collection.id == id) collection.copy(name = normalizedName) else collection
        })
    }

    fun delete(snapshot: PersistenceSnapshot, id: Long): PersistenceSnapshot {
        val collection = snapshot.collections.firstOrNull { it.id == id } ?: return snapshot
        val updated = snapshot.copy(
            notes = collection.notes + snapshot.notes,
            collections = snapshot.collections.filterNot { it.id == id }
        )
        return updated.takeIf { it.hasValidIdentifiers() } ?: snapshot
    }

    fun move(snapshot: PersistenceSnapshot, id: Long, delta: Int): PersistenceSnapshot {
        val from = snapshot.collections.indexOfFirst { it.id == id }
        if (from < 0 || snapshot.collections.isEmpty()) return snapshot
        val to = (from + delta).coerceIn(0, snapshot.collections.lastIndex)
        if (from == to) return snapshot
        return snapshot.copy(collections = snapshot.collections.toMutableList().apply {
            add(to, removeAt(from))
        })
    }

    fun transfer(
        snapshot: PersistenceSnapshot,
        sourceId: Long?,
        targetId: Long?,
        ids: Set<Long>
    ): PersistenceSnapshot {
        if (sourceId == targetId || ids.isEmpty()) return snapshot
        // Validate the destination before removing anything from the source.
        // A stale collection ID must never make a transfer silently lose records.
        if (targetId != null && snapshot.collections.none { it.id == targetId }) return snapshot
        val source = RecordListOperations.activeNotes(snapshot, sourceId)
        val moving = source.filter { it.id in ids }
        if (moving.isEmpty()) return snapshot
        val remaining = source.filterNot { it.id in ids }
        val withoutMoving = RecordListOperations.replace(snapshot, sourceId, remaining)
        val updated = if (targetId == null) {
            withoutMoving.copy(notes = moving + withoutMoving.notes)
        } else {
            withoutMoving.copy(collections = withoutMoving.collections.map { collection ->
                if (collection.id == targetId) collection.copy(notes = moving + collection.notes) else collection
            })
        }
        return updated.takeIf { it.hasValidIdentifiers() } ?: snapshot
    }
}
