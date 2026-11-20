package com.magnumold.notes

/**
 * Operations for the unified home timeline. Records remain in their original
 * notebook/collection; only their home presentation order is shared.
 */
internal enum class HomeTimelineRange {
    ALL, TODAY, TOMORROW, WEEK, MONTH, HISTORY, FAVORITES
}

internal object HomeTimelineOperations {
    fun filterByRange(notes: List<Note>, range: HomeTimelineRange, todayDay: Long): List<Note> {
        val monthBounds = if (range == HomeTimelineRange.MONTH) {
            val utc = java.util.TimeZone.getTimeZone("UTC")
            val calendar = java.util.Calendar.getInstance(utc).apply {
                timeInMillis = todayDay * MILLIS_PER_DAY
                set(java.util.Calendar.DAY_OF_MONTH, 1)
            }
            val monthStart = calendar.timeInMillis / MILLIS_PER_DAY
            calendar.add(java.util.Calendar.MONTH, 1)
            monthStart until (calendar.timeInMillis / MILLIS_PER_DAY)
        } else 0L until 0L

        return notes.filter { note ->
            val day = timelineDayKey(note)
            when (range) {
                HomeTimelineRange.ALL -> true
                HomeTimelineRange.TODAY -> day == todayDay
                HomeTimelineRange.TOMORROW -> day == todayDay + 1
                HomeTimelineRange.WEEK -> day in todayDay..(todayDay + 6)
                // "Месяц" covers the complete current calendar month,
                // including earlier days, not just the remaining days.
                HomeTimelineRange.MONTH -> day in monthBounds
                HomeTimelineRange.HISTORY -> day < todayDay
                HomeTimelineRange.FAVORITES -> note.favorite
            }
        }
    }

    fun records(snapshot: PersistenceSnapshot): List<Note> =
        snapshot.notes + snapshot.collections.flatMap { it.notes }

    fun orderedVisibleRecords(notes: List<Note>): List<Note> =
        notes.filter { it.showOnHome }.sortedWith(
            compareByDescending<Note> { timelineDayKey(it) }
                .thenByDescending { it.pinned }
                .thenByDescending { it.homeOrder }
        )

    fun orderedFavoritesByModule(notes: List<Note>): List<Note> =
        notes.filter { it.favorite }.sortedWith(
            compareBy<Note> { it.type.ordinal }
                .thenByDescending { timelineDayKey(it) }
                .thenByDescending { it.pinned }
                .thenByDescending { it.homeOrder }
        )

    fun moduleSectionTitle(type: RecordType): String =
        AppModule.entries.firstOrNull { it.recordType == type }?.title ?: "Другое"

    fun emptyStateMessage(range: HomeTimelineRange, searching: Boolean): String {
        if (searching) return "Ничего не найдено"
        return when (range) {
            HomeTimelineRange.ALL -> "Пока нет записей"
            HomeTimelineRange.TODAY -> "На сегодня записей нет"
            HomeTimelineRange.TOMORROW -> "На завтра записей нет"
            HomeTimelineRange.WEEK -> "На эту неделю записей нет"
            HomeTimelineRange.MONTH -> "В этом месяце записей нет"
            HomeTimelineRange.HISTORY -> "История пока пуста"
            HomeTimelineRange.FAVORITES -> "В избранном пока пусто"
        }
    }

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

    fun toggleFavorite(snapshot: PersistenceSnapshot, ids: Set<Long>): PersistenceSnapshot {
        if (ids.isEmpty()) return snapshot
        val selected = records(snapshot).filter { it.id in ids }
        if (selected.isEmpty()) return snapshot
        val allFavorite = selected.all { it.favorite }
        return mapAll(snapshot) { note ->
            if (note.id in ids) note.copy(favorite = !allFavorite) else note
        }
    }

    fun setShowOnHome(
        snapshot: PersistenceSnapshot,
        ids: Set<Long>,
        show: Boolean
    ): PersistenceSnapshot {
        if (ids.isEmpty()) return snapshot
        val existingIds = records(snapshot).asSequence().map { it.id }.toSet()
        if (ids.none { it in existingIds }) return snapshot
        return mapAll(snapshot) { note ->
            if (note.id in ids) note.copy(showOnHome = show) else note
        }
    }

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

    private fun dayKey(note: Note): Long = timelineDayKey(note)

    private const val MILLIS_PER_DAY = 86_400_000L

    fun moveToVault(
        snapshot: PersistenceSnapshot,
        ids: Set<Long>,
        toTrash: Boolean,
        now: Long = System.currentTimeMillis()
    ): PersistenceSnapshot {
        if (ids.isEmpty()) return snapshot
        val entries = buildList {
            addAll(RecordOperations.createVaultEntries(snapshot.notes, null, ids, now))
            snapshot.collections.forEach { collection ->
                addAll(RecordOperations.createVaultEntries(collection.notes, collection.id, ids, now))
            }
        }
        if (entries.isEmpty()) return snapshot
        val remainingRoot = snapshot.notes.filterNot { it.id in ids }
        val remainingCollections = snapshot.collections.map { collection ->
            collection.copy(notes = collection.notes.filterNot { it.id in ids })
        }
        val updated = snapshot.copy(notes = remainingRoot, collections = remainingCollections)
        return if (toTrash) updated.copy(trash = entries + updated.trash)
        else updated.copy(archived = entries + updated.archived)
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


/**
 * Converts record timestamps into a stable calendar-day key.
 * Scheduled dates are stored as DatePicker UTC-midnight values; creation
 * timestamps are real instants and must be interpreted in the device timezone.
 */
internal fun timelineDayKey(note: Note): Long =
    timelineDayKey(note.scheduledAt ?: note.created, isDateOnly = note.scheduledAt != null)

internal fun currentTimelineDayKey(now: Long = System.currentTimeMillis()): Long =
    timelineDayKey(now, isDateOnly = false)

internal fun timelineDayKey(
    timestamp: Long,
    isDateOnly: Boolean,
    timeZone: java.util.TimeZone = java.util.TimeZone.getDefault()
): Long {
    val sourceZone = if (isDateOnly) java.util.TimeZone.getTimeZone("UTC") else timeZone
    val sourceCalendar = java.util.Calendar.getInstance(sourceZone).apply {
        timeInMillis = timestamp
    }
    val utcCalendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(
            sourceCalendar.get(java.util.Calendar.YEAR),
            sourceCalendar.get(java.util.Calendar.MONTH),
            sourceCalendar.get(java.util.Calendar.DAY_OF_MONTH),
            0, 0, 0
        )
    }
    return utcCalendar.timeInMillis / 86_400_000L
}
