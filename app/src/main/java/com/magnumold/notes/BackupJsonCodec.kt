package com.magnumold.notes

import org.json.JSONArray
import org.json.JSONObject

internal data class BackupImportResult(
    val importedRecordCount: Int,
    val importedRecoveryCount: Int
)

internal data class BackupData(
    val notes: List<Note>,
    val collections: List<NoteCollection>,
    val archived: List<VaultNote>,
    val trash: List<VaultNote>,
    val timeTracking: TimeTrackingState? = null,
    val recoveryEntries: List<RecoveryEntry> = emptyList()
)
private fun noteToJson(note: Note): JSONObject {
    val blocks = JSONArray()
    note.blocks.forEach { block ->
        blocks.put(JSONObject().put("id", block.id).put("text", block.text)
            .put("checked", block.checked).put("checklist", block.isChecklist))
    }
    return JSONObject()
        .put("id", note.id).put("title", note.title).put("updated", note.updated)
        .put("created", note.created).put("colorIndex", note.colorIndex).put("pinned", note.pinned)
        .put("type", note.type.name).put("status", note.status.name).put("timeTrackingEnabled", note.timeTrackingEnabled)
        .put("scheduledAt", note.scheduledAt ?: JSONObject.NULL).put("showOnHome", note.showOnHome)
        .put("favorite", note.favorite).put("homeOrder", note.homeOrder)
        .put("timerDurationSeconds", note.timerDurationSeconds)
        .put("timerEndsAtMillis", note.timerEndsAtMillis ?: JSONObject.NULL)
        .put("timerRemainingSeconds", note.timerRemainingSeconds ?: JSONObject.NULL)
        .put("nutritionGrams", note.nutritionGrams).put("nutritionCalories", note.nutritionCalories)
        .put("nutritionProtein", note.nutritionProtein).put("nutritionFat", note.nutritionFat)
        .put("nutritionCarbs", note.nutritionCarbs)
        .put("purchaseQuantity", note.purchaseQuantity).put("purchaseUnitPrice", note.purchaseUnitPrice)
        .put("workoutSets", note.workoutSets).put("workoutReps", note.workoutReps)
        .put("workoutWeightKg", note.workoutWeightKg)
        .put("dateMode", note.dateMode?.name ?: JSONObject.NULL)
        .put("dateAt", note.dateAt ?: JSONObject.NULL)
        .put("titleEnabled", note.titleEnabled).put("titleText", note.titleText)
        .put("descriptionEnabled", note.descriptionEnabled).put("descriptionText", note.descriptionText)
        .put("deadlineResumeOverride", note.deadlineResumeOverride)
        .put("statusBeforeAutoCompletion", note.statusBeforeAutoCompletion?.name ?: JSONObject.NULL)
        .put("blocks", blocks).put("children", notesToJson(note.children))
}
internal fun notesToJson(items: List<Note>): JSONArray {
    val array = JSONArray()
    items.filter { it.hasContent() }.forEach { array.put(noteToJson(it)) }
    return array
}
internal fun vaultToJson(items: List<VaultNote>): JSONArray {
    val array = JSONArray()
    items.forEach { entry ->
        array.put(JSONObject().put("collectionId", entry.collectionId ?: JSONObject.NULL)
            .put("position", entry.position).put("movedAt", entry.movedAt)
            .put("previousRecordId", entry.previousRecordId ?: JSONObject.NULL)
            .put("nextRecordId", entry.nextRecordId ?: JSONObject.NULL)
            .put("note", noteToJson(entry.note)))
    }
    return array
}
private fun JSONObject.optionalArray(key: String): JSONArray =
    if (!has(key) || isNull(key)) JSONArray() else getJSONArray(key)

private fun parseNoteObject(obj: JSONObject, index: Int): Note {
    val blocksJson = obj.optionalArray("blocks")
    var blocks = (0 until blocksJson.length()).map { blockIndex ->
        val block = blocksJson.getJSONObject(blockIndex)
        NoteBlock(block.optString("text"), block.optBoolean("checked"), block.optBoolean("checklist"),
            block.optString("id").ifBlank { java.util.UUID.randomUUID().toString() })
    }.ifEmpty { listOf(NoteBlock()) }
    val oldTitle = obj.optString("title").trim()
    if (oldTitle.isNotEmpty() && blocks.firstOrNull()?.text?.trim() != oldTitle) {
        blocks = listOf(NoteBlock(oldTitle)) + blocks
    }
    val storedType = obj.optString("type", "NOTE")
    // Legacy UNIVERSAL records are migrated to the new TASK container model.
    val type = if (storedType == "UNIVERSAL") RecordType.TASK
        else runCatching { RecordType.valueOf(storedType) }.getOrDefault(RecordType.NOTE)
    val status = runCatching { RecordStatus.valueOf(obj.optString("status", "WAITING")) }.getOrDefault(RecordStatus.WAITING)
    val dateMode = runCatching { RecordDateMode.valueOf(obj.optString("dateMode")) }.getOrNull()
    val children = parseNotesArray(obj.optionalArray("children"))
    val updated = obj.optLong("updated", System.currentTimeMillis())
    val created = obj.optLong("created",
        obj.optLong("id", updated).takeIf { it > 1_000_000_000_000L } ?: updated)
    return Note(
        id = obj.optLong("id", index.toLong() + 1L), title = "", blocks = blocks, updated = updated,
        colorIndex = obj.optInt("colorIndex", 0).coerceIn(0, 6), pinned = obj.optBoolean("pinned", false),
        created = created, type = type, status = status,
        timeTrackingEnabled = obj.optBoolean("timeTrackingEnabled", false),
        scheduledAt = if (obj.isNull("scheduledAt")) null else obj.optLong("scheduledAt"),
        showOnHome = obj.optBoolean("showOnHome", true), favorite = obj.optBoolean("favorite", false),
        homeOrder = obj.optLong("homeOrder", created),
        timerDurationSeconds = obj.optLong("timerDurationSeconds", 300L).coerceIn(1L, 86_400L),
        timerEndsAtMillis = if (obj.isNull("timerEndsAtMillis")) null else obj.optLong("timerEndsAtMillis"),
        timerRemainingSeconds = if (obj.isNull("timerRemainingSeconds")) null else obj.optLong("timerRemainingSeconds").coerceIn(0L, 86_400L),
        nutritionGrams = obj.optDouble("nutritionGrams", 0.0).coerceAtLeast(0.0),
        nutritionCalories = obj.optDouble("nutritionCalories", 0.0).coerceAtLeast(0.0),
        nutritionProtein = obj.optDouble("nutritionProtein", 0.0).coerceAtLeast(0.0),
        nutritionFat = obj.optDouble("nutritionFat", 0.0).coerceAtLeast(0.0),
        nutritionCarbs = obj.optDouble("nutritionCarbs", 0.0).coerceAtLeast(0.0),
        purchaseQuantity = obj.optDouble("purchaseQuantity", 1.0).coerceAtLeast(0.0),
        purchaseUnitPrice = obj.optDouble("purchaseUnitPrice", 0.0).coerceAtLeast(0.0),
        workoutSets = obj.optInt("workoutSets", 3).coerceIn(1, 100),
        workoutReps = obj.optInt("workoutReps", 10).coerceIn(1, 1000),
        workoutWeightKg = obj.optDouble("workoutWeightKg", 0.0).coerceAtLeast(0.0),
        dateMode = dateMode,
        dateAt = if (obj.isNull("dateAt")) null else obj.optLong("dateAt"),
        titleEnabled = obj.optBoolean("titleEnabled", false), titleText = obj.optString("titleText"),
        descriptionEnabled = obj.optBoolean("descriptionEnabled", false),
        descriptionText = obj.optString("descriptionText"),
        deadlineResumeOverride = obj.optBoolean("deadlineResumeOverride", false), children = children,
        statusBeforeAutoCompletion = obj.optString("statusBeforeAutoCompletion").takeIf { it.isNotBlank() && it != "null" }
            ?.let { runCatching { RecordStatus.valueOf(it) }.getOrNull() }
    )
}
internal fun parseNotesArray(array: JSONArray): List<Note> =
    (0 until array.length()).map { index -> parseNoteObject(array.getJSONObject(index), index) }
        .filter { it.hasContent() }
internal fun parseVaultArray(array: JSONArray?): List<VaultNote> {
    if (array == null) return emptyList()
    return (0 until array.length()).map { index ->
        val obj = array.getJSONObject(index)
        val noteObj = obj.getJSONObject("note")
        val note = parseNoteObject(noteObj, index)
        require(note.hasContent()) { "Archive entry $index has no content" }
        val collectionId = if (obj.isNull("collectionId")) null else obj.optLong("collectionId")
        VaultNote(
            note = note,
            collectionId = collectionId,
            position = obj.optInt("position", 0),
            movedAt = obj.optLong("movedAt", System.currentTimeMillis()),
            previousRecordId = if (obj.isNull("previousRecordId")) null else obj.optLong("previousRecordId"),
            nextRecordId = if (obj.isNull("nextRecordId")) null else obj.optLong("nextRecordId")
        )
    }
}
internal fun collectionsToJson(items: List<NoteCollection>): JSONArray {
    val array = JSONArray()
    items.forEach { item ->
        array.put(JSONObject().put("id", item.id).put("name", item.name)
            .put("notes", notesToJson(item.notes)))
    }
    return array
}

internal fun parseCollectionsArray(array: JSONArray): List<NoteCollection> =
    (0 until array.length()).map { index ->
        val obj = array.getJSONObject(index)
        NoteCollection(
            obj.optLong("id", System.currentTimeMillis() + index),
            obj.optString("name").ifBlank { "Подборка ${index + 1}" },
            parseNotesArray(obj.optionalArray("notes"))
        )
    }

internal fun createBackupJson(
    items: List<Note>,
    collections: List<NoteCollection>,
    archived: List<VaultNote>,
    trash: List<VaultNote>,
    timeTracking: TimeTrackingState = TimeTrackingState(),
    recoveryEntries: List<RecoveryEntry> = emptyList()
): String {
    val collectionArray = JSONArray()
    collections.forEach { collection ->
        collectionArray.put(JSONObject().put("id", collection.id).put("name", collection.name).put("notes", notesToJson(collection.notes)))
    }
    val recoveryArray = JSONArray()
    recoveryEntries.forEach { entry ->
        recoveryArray.put(
            JSONObject()
                .put("storage", entry.storage)
                .put("key", entry.key)
                .put("raw", entry.raw)
        )
    }
    return JSONObject().put("format", "notes_backup").put("version", 12).put("exportedAt", System.currentTimeMillis())
        .put("notes", notesToJson(items)).put("collections", collectionArray)
        .put("archive", vaultToJson(archived)).put("trash", vaultToJson(trash))
        .put("timeTracking", timeTrackingToJson(timeTracking))
        .put("recoveryData", recoveryArray)
        .toString(2)
}
internal fun parseBackupJson(raw: String): BackupData {
    val root = JSONObject(raw)
    require(root.optString("format") == "notes_backup") { "Это не резервная копия блокнота" }
    require(root.optInt("version") in 1..12) { "Неподдерживаемая версия файла" }
    val notes = parseNotesArray(root.getJSONArray("notes"))
    val collections = if (!root.has("collections") || root.isNull("collections")) emptyList()
        else parseCollectionsArray(root.getJSONArray("collections"))
    val archived = if (!root.has("archive") || root.isNull("archive")) emptyList()
        else parseVaultArray(root.getJSONArray("archive"))
    val trash = if (!root.has("trash") || root.isNull("trash")) emptyList()
        else parseVaultArray(root.getJSONArray("trash"))
    val timeTracking = if (!root.has("timeTracking") || root.isNull("timeTracking")) null
        else parseTimeTrackingJson(root.getJSONObject("timeTracking"))
    val recoveryEntries = if (!root.has("recoveryData") || root.isNull("recoveryData")) emptyList()
        else {
            val array = root.getJSONArray("recoveryData")
            (0 until array.length()).map { index ->
                val entry = array.getJSONObject(index)
                RecoveryEntry(
                    storage = entry.getString("storage"),
                    key = entry.getString("key"),
                    raw = entry.getString("raw")
                ).also {
                    require(it.storage == RecoveryDataRepository.STORAGE_RECORDS ||
                        it.storage == RecoveryDataRepository.STORAGE_TIME_TRACKING)
                    require(it.key.startsWith(RecoveryDataRepository.RECOVERY_KEY_PREFIX))
                }
            }
        }
    return BackupData(notes, collections, archived, trash, timeTracking, recoveryEntries)
}

private fun Note.sameRecordContent(other: Note): Boolean =
    // Content equality is used to make repeated imports idempotent across devices.
    // Creation time and task scheduling/state are part of a record's identity:
    // two tasks with the same text but different deadlines must remain separate.
    created == other.created &&
        type == other.type &&
        blocks.map { Triple(it.text, it.checked, it.isChecklist) } ==
            other.blocks.map { Triple(it.text, it.checked, it.isChecklist) } &&
        titleText == other.titleText && descriptionText == other.descriptionText &&
        status == other.status && dateMode == other.dateMode && dateAt == other.dateAt &&
        scheduledAt == other.scheduledAt && deadlineResumeOverride == other.deadlineResumeOverride &&
        timeTrackingEnabled == other.timeTrackingEnabled &&
        timerDurationSeconds == other.timerDurationSeconds &&
        timerEndsAtMillis == other.timerEndsAtMillis &&
        timerRemainingSeconds == other.timerRemainingSeconds &&
        nutritionGrams == other.nutritionGrams && nutritionCalories == other.nutritionCalories &&
        nutritionProtein == other.nutritionProtein && nutritionFat == other.nutritionFat && nutritionCarbs == other.nutritionCarbs &&
        purchaseQuantity == other.purchaseQuantity && purchaseUnitPrice == other.purchaseUnitPrice &&
        workoutSets == other.workoutSets && workoutReps == other.workoutReps && workoutWeightKg == other.workoutWeightKg

internal fun mergeImportedNotes(existing: List<Note>, imported: List<Note>): List<Note> {
    val result = existing.toMutableList()
    val usedIds = mutableSetOf<Long>()

    fun collectIds(notes: List<Note>) {
        notes.forEach { note ->
            usedIds += note.id
            collectIds(note.children)
        }
    }
    collectIds(existing)

    fun nextUniqueId(): Long {
        var candidate = System.currentTimeMillis().coerceAtLeast(0L)
        while (!usedIds.add(candidate)) {
            candidate = if (candidate == Long.MAX_VALUE) Long.MIN_VALUE else candidate + 1L
        }
        return candidate
    }

    fun copyWithUniqueTreeIds(note: Note): Note {
        val safeId = if (usedIds.add(note.id)) note.id else nextUniqueId()
        val idChanged = safeId != note.id
        return note.copy(
            id = safeId,
            blocks = if (idChanged) note.blocks.map {
                it.copy(id = java.util.UUID.randomUUID().toString())
            } else note.blocks,
            children = note.children.map(::copyWithUniqueTreeIds)
        )
    }

    imported.forEach { incoming ->
        val sameIndex = result.indexOfFirst { it.sameRecordContent(incoming) }
        if (sameIndex >= 0) {
            val current = result[sameIndex]
            result[sameIndex] = current.copy(
                children = mergeImportedNotes(current.children, incoming.children)
            )
            return@forEach
        }
        result.add(copyWithUniqueTreeIds(incoming))
    }
    return result
}

internal fun mergeImportedVault(existing: List<VaultNote>, imported: List<VaultNote>): List<VaultNote> {
    val result = existing.toMutableList()
    imported.forEach { incoming ->
        val sameIndex = result.indexOfFirst { it.note.sameRecordContent(incoming.note) }
        if (sameIndex >= 0) {
            val current = result[sameIndex]
            result[sameIndex] = current.copy(
                note = current.note.copy(
                    children = mergeImportedNotes(current.note.children, incoming.note.children)
                )
            )
            return@forEach
        }
        val collision = result.any { it.note.id == incoming.note.id }
        val safeNote = if (!collision) incoming.note else {
            var candidate = System.currentTimeMillis()
            while (result.any { it.note.id == candidate }) candidate++
            incoming.note.copy(id = candidate, blocks = incoming.note.blocks.map { it.copy(id = java.util.UUID.randomUUID().toString()) })
        }
        result.add(incoming.copy(note = safeNote))
    }
    return result
}

internal data class ImportedCollectionsMerge(
    val collections: List<NoteCollection>,
    val importedIdMap: Map<Long, Long>
)

internal fun mergeImportedCollectionsWithIdMap(
    existing: List<NoteCollection>,
    imported: List<NoteCollection>
): ImportedCollectionsMerge {
    val result = existing.toMutableList()
    val idMap = linkedMapOf<Long, Long>()
    imported.forEach { incoming ->
        val sameName = result.indexOfFirst { it.name.equals(incoming.name, true) }
        if (sameName >= 0) {
            val target = result[sameName]
            result[sameName] = target.copy(notes = mergeImportedNotes(target.notes, incoming.notes))
            idMap[incoming.id] = target.id
        } else {
            var id = incoming.id
            while (result.any { it.id == id }) id++
            result.add(incoming.copy(id = id))
            idMap[incoming.id] = id
        }
    }
    return ImportedCollectionsMerge(result, idMap)
}

internal fun mergeImportedCollections(existing: List<NoteCollection>, imported: List<NoteCollection>): List<NoteCollection> =
    mergeImportedCollectionsWithIdMap(existing, imported).collections
