package com.magnumold.notes

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NotesHome(
    notes: List<Note>, search: String, searchVisible: Boolean, noteColors: List<Color>,
    themeColors: CustomThemeColors, onSearch: (String) -> Unit,
    onToggleSearch: () -> Unit, dark: Boolean, onOpenSettings: () -> Unit,
    onOpen: (Long) -> Unit, onCreate: (RecordType) -> Unit,
    onChangeNoteColor: (Long, Int) -> Unit, onDeleteSelected: (Set<Long>) -> Unit, onArchiveSelected: (Set<Long>) -> Unit,
    onTogglePinned: (Set<Long>) -> Unit,
    onToggleFavorite: (Set<Long>) -> Unit = {},
    onSetShowOnHome: (Set<Long>, Boolean) -> Unit = { _, _ -> },
    onMoveSelected: (Set<Long>, Long?) -> Unit,
    collections: List<NoteCollection>, activeCollectionId: Long?,
    onSelectCollection: (Long?) -> Unit, onCreateCollection: (String) -> Unit,
    onRenameCollection: (Long, String) -> Unit, onDeleteCollection: (Long) -> Unit,
    onMoveCollection: (Long, Int) -> Unit, onTransferSelected: (Set<Long>, Long?) -> Unit,
    screenTitle: String = "Заметки", onOpenModules: () -> Unit = {}, timelineMode: Boolean = false,
    interactiveCards: Boolean = false, onQuickAction: (Note) -> Unit = {},
    cardPreviewFields: Set<String> = CardPreviewField.defaults,
     showCollections: Boolean = true,
    collectionsAvailable: Boolean = true,
    enabledModules: List<AppModule> = AppModule.entries.filter { it != AppModule.ALL },
    homeButtonLayout: HomeButtonLayout = HomeButtonLayout.SEPARATE,
    homeAddRecordType: RecordType = RecordType.NOTE,
    isMainHome: Boolean = timelineMode
) {
    var selectedNoteIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showMoveMenu by remember { mutableStateOf(false) }
    var showSelectionActionsMenu by remember { mutableStateOf(false) }
    var collectionMenuId by remember { mutableStateOf<Long?>(null) }
    var showNewCollectionDialog by remember { mutableStateOf(false) }
    var newCollectionName by remember { mutableStateOf("") }
    var renameCollectionId by remember { mutableStateOf<Long?>(null) }
    var renameCollectionName by remember { mutableStateOf("") }
    var deleteCollectionId by remember { mutableStateOf<Long?>(null) }
    var createMenuExpanded by remember { mutableStateOf(false) }
    var showDisabledAddModuleDialog by remember { mutableStateOf(false) }
    var draggingIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var dragBaseRows by remember { mutableStateOf<Map<String, DragRow>>(emptyMap()) }
    var reorderSession by remember { mutableStateOf<ReorderSession?>(null) }
    var dragPointerY by remember { mutableFloatStateOf(0f) }
    var dragGrabOffsetPx by remember { mutableFloatStateOf(0f) }
    var dragReleaseRequested by remember { mutableStateOf(false) }
    val measuredNoteCardHeights = remember { mutableStateMapOf<Long, Int>() }
    var timelineRange by remember { mutableStateOf(HomeTimelineRange.ALL) }

    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val orderedNotes = if (timelineMode) {
        HomeTimelineOperations.orderedVisibleRecords(notes)
    } else notes.sortedByDescending { it.pinned }
    val todayDay = currentTimelineDayKey()
    val moduleSummary = ModuleSummaryOperations.summary(screenTitle, notes, todayDay)
    val rangeNotes = if (timelineMode && timelineRange == HomeTimelineRange.FAVORITES) {
        notes.filter { it.favorite }
    } else if (timelineMode) {
        HomeTimelineOperations.filterByRange(orderedNotes, timelineRange, todayDay)
    } else orderedNotes
    val displayNotes = if (timelineMode && timelineRange == HomeTimelineRange.FAVORITES) {
        HomeTimelineOperations.orderedFavoritesByModule(rangeNotes)
    } else rangeNotes
    val filtered = displayNotes.filter { it.matchesSearch(search) }
    val selectionMode = selectedNoteIds.isNotEmpty()
    val activeButtonLayout = if (isMainHome) homeButtonLayout else HomeButtonLayout.SEPARATE
    val homeBarColor = themeColors.topBar
    val homeContentColor = noteTextColor(homeBarColor)
    val selectedNoteBackground = themeColors.selection
    val isDragging = draggingIds.isNotEmpty()
    val dragRows = reorderSession?.keys?.mapNotNull { dragBaseRows[it] }.orEmpty()
    val visibleDragRows = if (isDragging && search.isBlank()) dragRows
        else filtered.map { DragRow(listOf(it), it.id.toString()) }

    fun finishDrag() {
        val ids = draggingIds
        if (ids.isNotEmpty()) {
            onMoveSelected(ids, NoteReorderModel.afterId(dragRows, ids))
        }
        draggingIds = emptySet()
        reorderSession = null
        dragBaseRows = emptyMap()
        dragPointerY = 0f
        dragGrabOffsetPx = 0f
        dragReleaseRequested = false
    }

    fun startDrag(note: Note, rootY: Float, grabOffsetInRow: Float) {
        val ids = if (note.id in selectedNoteIds && selectedNoteIds.size > 1) {
            selectedNoteIds
        } else {
            setOf(note.id)
        }
        val rows = NoteReorderModel.createDragRows(rangeNotes, ids, note.id) ?: return

        dragBaseRows = rows.associateBy { it.key }
        reorderSession = AdjacentReorderEngine.begin(rows.map { it.key }, note.id.toString())
        draggingIds = ids
        dragPointerY = rootY
        dragGrabOffsetPx = grabOffsetInRow
        dragReleaseRequested = false
    }

    // Keep gesture tracking on the stable viewport, not on a row that LazyColumn repositions.
    val latestIsDraggingForPointer = rememberUpdatedState(isDragging)
    val latestSearchBlankForPointer = rememberUpdatedState(search.isBlank())
    val latestRowsForPointer = rememberUpdatedState(visibleDragRows)
    val latestStartDrag = rememberUpdatedState<(Note, Float, Float) -> Unit> { note, pointerY, grabOffset ->
        startDrag(note, pointerY, grabOffset)
    }

    AdjacentReorderEffect(
        isDragging = isDragging,
        orderedKeys = dragRows.map { it.key },
        reorderSession = reorderSession,
        pointerY = dragPointerY,
        grabOffsetPx = dragGrabOffsetPx,
        releaseRequested = dragReleaseRequested,
        listState = listState,
        density = density,
        canSwap = { movingKey, neighbourKey ->
            NoteReorderModel.canSwapRows(
                moving = dragBaseRows[movingKey]?.notes.orEmpty(),
                neighbour = dragBaseRows[neighbourKey]?.notes.orEmpty(),
                timelineMode = timelineMode
            )
        },
        onSessionChange = { reorderSession = it },
        onFinishDrag = { finishDrag() }
    )

    AppPageScaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(color = homeBarColor, contentColor = homeContentColor) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .height(AppPageLayout.toolbarHeight)
                        .padding(start = AppSpacing.screenHorizontal, end = AppSpacing.toolbarControlInset),
                    verticalAlignment = Alignment.CenterVertically                ) {
                    if (selectionMode) {
                        Text("${selectedNoteIds.size} выбрано", style = AppTypography.screenTitleStyle())
                        Spacer(Modifier.weight(1f))
                        val selectedRecords = notes.filter { it.id in selectedNoteIds }
                        val allSelectedPinned = selectedRecords.isNotEmpty() && selectedRecords.all { it.pinned }
                        val allSelectedFavorite = selectedRecords.isNotEmpty() && selectedRecords.all { it.favorite }
                        val allSelectedShownOnHome = selectedRecords.isNotEmpty() && selectedRecords.all { it.showOnHome }
                        Box {
                            IconButton(onClick = { showSelectionActionsMenu = true }) {
                                AppIcon(NoteIcon.More, contentDescription = "Дополнительные действия")
                            }
                            DropdownMenu(
                                expanded = showSelectionActionsMenu,
                                onDismissRequest = { showSelectionActionsMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(if (allSelectedPinned) "Открепить" else "Закрепить") },
                                    onClick = {
                                        onTogglePinned(selectedNoteIds)
                                        showSelectionActionsMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (allSelectedFavorite) "Убрать из избранного" else "В избранное") },
                                    onClick = {
                                        onToggleFavorite(selectedNoteIds)
                                        if (allSelectedFavorite) selectedNoteIds = emptySet()
                                        showSelectionActionsMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (allSelectedShownOnHome) "Скрыть с главной" else "Показывать на главной") },
                                    onClick = {
                                        val show = !allSelectedShownOnHome
                                        onSetShowOnHome(selectedNoteIds, show)
                                        if (!show) selectedNoteIds = emptySet()
                                        showSelectionActionsMenu = false
                                    }
                                )
                            }
                        }
                        IconButton(onClick = { onArchiveSelected(selectedNoteIds); selectedNoteIds = emptySet() }) { AppIcon(NoteIcon.Archive, contentDescription = "В архив") }
                        IconButton(onClick = { showMoveMenu = true }, enabled = collectionsAvailable) { AppIcon(NoteIcon.Folder, contentDescription = "Переместить заметки") }
                        DropdownMenu(expanded = showMoveMenu, onDismissRequest = { showMoveMenu = false }) {
                            if (activeCollectionId != null) DropdownMenuItem(text = { Text("В основной блокнот") }, onClick = {
                                onTransferSelected(selectedNoteIds, null); selectedNoteIds = emptySet(); showMoveMenu = false
                            })
                            collections.filter { it.id != activeCollectionId }.forEach { collection ->
                                DropdownMenuItem(text = { Text(collection.name) }, onClick = {
                                    onTransferSelected(selectedNoteIds, collection.id); selectedNoteIds = emptySet(); showMoveMenu = false
                                })
                            }
                            if (collections.isEmpty() || (collections.size == 1 && activeCollectionId != null)) {
                                DropdownMenuItem(text = { Text("Нет других подборок") }, enabled = false, onClick = {})
                            }
                        }
                        IconButton(onClick = { selectedNoteIds = emptySet() }) {
                            AppIcon(NoteIcon.Close, contentDescription = "Отменить выделение")
                        }
                        IconButton(onClick = { showDeleteConfirmation = true }) { AppIcon(NoteIcon.Trash, contentDescription = "Удалить выбранные заметки") }
                    } else {
                        Text(screenTitle, style = AppTypography.screenTitleStyle())
                        Spacer(Modifier.weight(1f))
                        if (activeButtonLayout == HomeButtonLayout.SEPARATE) {
                            IconButton(onClick = onOpenModules) {
                                AppIcon(NoteIcon.Modules, contentDescription = "Модули")
                            }
                        }
                        IconButton(onClick = onToggleSearch) {
                            AppIcon(if (searchVisible) NoteIcon.Close else NoteIcon.Search, contentDescription = "Поиск")
                        }
                        IconButton(onClick = onOpenSettings) {
                            AppIcon(NoteIcon.More, contentDescription = "Настройки")
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (!selectionMode && !isDragging) {
                val buttonDiameter = when (activeButtonLayout) {
                    HomeButtonLayout.SEPARATE -> PrimaryAddButtonDimensions.diameter
                    HomeButtonLayout.VERTICAL_PANEL -> 52.dp
                    else -> 56.dp
                }
                val modulesDiameter = buttonDiameter
                val addDiameter = buttonDiameter
                val buttonGap = 8.dp
                val selectedAddModule = AppModule.entries.firstOrNull { it.recordType == homeAddRecordType }
                val addAction: () -> Unit = {
                    if (isMainHome) {
                        if (selectedAddModule == null || selectedAddModule !in enabledModules) {
                            showDisabledAddModuleDialog = true
                        } else {
                            onCreate(homeAddRecordType)
                        }
                    } else {
                        enabledModules.firstOrNull()?.recordType?.let(onCreate) ?: onOpenModules()
                    }
                }
                val addLongAction: () -> Unit = {
                    if (enabledModules.isEmpty()) onOpenModules()
                    else createMenuExpanded = true
                }

                Column(
                    modifier = Modifier.offset(y = PrimaryAddButtonDimensions.verticalOffset),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(buttonGap)
                ) {
                    if (activeButtonLayout == HomeButtonLayout.VERTICAL) {
                        Box(
                            modifier = Modifier.size(modulesDiameter)
                                .shadow(3.dp, androidx.compose.foundation.shape.CircleShape)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .combinedClickable(
                                    role = Role.Button,
                                    onClick = onOpenModules,
                                    onLongClick = {}
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            AppIcon(NoteIcon.Modules, contentDescription = "Модули")
                        }
                    } else if (activeButtonLayout == HomeButtonLayout.VERTICAL_PANEL) {
                        Column(
                            modifier = Modifier
                                .width(56.dp)
                                .shadow(4.dp, androidx.compose.foundation.shape.CircleShape)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier.size(modulesDiameter)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(MaterialTheme.colorScheme.surface)
                                    .combinedClickable(
                                        role = Role.Button,
                                        onClick = onOpenModules,
                                        onLongClick = {}
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                AppIcon(NoteIcon.Modules, contentDescription = "Модули")
                            }
                            Box(
                                modifier = Modifier.size(addDiameter)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(themeColors.addButton)
                                    .combinedClickable(
                                        role = Role.Button,
                                        onClick = addAction,
                                        onLongClick = addLongAction
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "+",
                                    fontSize = 34.sp,
                                    fontWeight = FontWeight.Light,
                                    color = noteTextColor(themeColors.addButton)
                                )
                            }
                        }
                    }

                    if (activeButtonLayout != HomeButtonLayout.VERTICAL_PANEL) {
                        Box(
                            modifier = Modifier
                                .size(addDiameter)
                                .shadow(6.dp, androidx.compose.foundation.shape.CircleShape)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(themeColors.addButton)
                                .combinedClickable(
                                    role = Role.Button,
                                    onClick = addAction,
                                    onLongClick = addLongAction
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "+",
                                fontSize = 34.sp,
                                fontWeight = FontWeight.Light,
                                color = noteTextColor(themeColors.addButton)
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = createMenuExpanded,
                        onDismissRequest = { createMenuExpanded = false }
                    ) {
                        enabledModules.forEach { module ->
                            module.recordType?.let { recordType ->
                                DropdownMenuItem(text = { Text(module.title) }, onClick = {
                                    createMenuExpanded = false
                                    onCreate(recordType)
                                })
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (showDisabledAddModuleDialog) {
            AlertDialog(
                onDismissRequest = { showDisabledAddModuleDialog = false },
                title = { Text("Модуль отключён") },
                text = {
                    Text("Выбранный тип записи недоступен, потому что соответствующий модуль отключён. Включите модуль или выберите другой тип в персонализации.")
                },
                confirmButton = {
                    TextButton(onClick = {
                        showDisabledAddModuleDialog = false
                        onOpenModules()
                    }) {
                        Text("Открыть модули")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDisabledAddModuleDialog = false }) {
                        Text("Отмена")
                    }
                }
            )
        }
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (showCollections) {
            Row(Modifier.fillMaxWidth().height(AppCollectionRowDimensions.height).padding(start = AppSpacing.screenHorizontal, end = AppSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).fillMaxHeight().horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.lg)) {
                    Text("Все",
                        color = if (activeCollectionId == null) themeColors.collectionText else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = AppTypography.collectionLabelStyle(selected = activeCollectionId == null),
                        modifier = Modifier.pointerInput(activeCollectionId) { detectTapGestures(onTap = { onSelectCollection(null); selectedNoteIds = emptySet() }) })
                    collections.forEach { collection ->
                        Box {
                            Text(collection.name,
                                color = if (activeCollectionId == collection.id) themeColors.collectionText else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = AppTypography.collectionLabelStyle(selected = activeCollectionId == collection.id),
                                maxLines = 1,
                                modifier = Modifier.pointerInput(collection.id, activeCollectionId) {
                                    detectTapGestures(onTap = { onSelectCollection(collection.id); selectedNoteIds = emptySet() },
                                        onLongPress = { collectionMenuId = collection.id })
                                })
                            DropdownMenu(expanded = collectionMenuId == collection.id, onDismissRequest = { collectionMenuId = null }) {
                                DropdownMenuItem(text = { Text("Переименовать") }, onClick = {
                                    renameCollectionId = collection.id; renameCollectionName = collection.name; collectionMenuId = null
                                })
                                DropdownMenuItem(text = { Text("Переместить влево") }, enabled = collections.indexOfFirst { it.id == collection.id } > 0, onClick = {
                                    onMoveCollection(collection.id, -1); collectionMenuId = null
                                })
                                DropdownMenuItem(text = { Text("Переместить вправо") }, enabled = collections.indexOfFirst { it.id == collection.id } < collections.lastIndex, onClick = {
                                    onMoveCollection(collection.id, 1); collectionMenuId = null
                                })
                                DropdownMenuItem(text = { Text("Удалить подборку") }, onClick = {
                                    deleteCollectionId = collection.id; collectionMenuId = null
                                })
                            }
                        }
                    }
                }
                IconButton(onClick = { newCollectionName = ""; showNewCollectionDialog = true }) {
                    AppIcon(NoteIcon.Add, contentDescription = "Создать подборку", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            }
            if (timelineMode) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .height(AppCollectionRowDimensions.height)
                        .padding(horizontal = AppSpacing.screenHorizontal)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.lg),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val ranges = listOf(
                        HomeTimelineRange.ALL to "Лента",
                        HomeTimelineRange.TODAY to "Сегодня",
                        HomeTimelineRange.TOMORROW to "Завтра",
                        HomeTimelineRange.WEEK to "Неделя",
                        HomeTimelineRange.MONTH to "Месяц",
                        HomeTimelineRange.FAVORITES to "Избранное"
                    )
                    ranges.forEach { (range, label) ->
                        Text(
                            text = label,
                            color = if (timelineRange == range) {
                                themeColors.collectionText
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            style = AppTypography.collectionLabelStyle(selected = timelineRange == range),
                            modifier = Modifier.pointerInput(range, timelineRange) {
                                detectTapGestures(onTap = {
                                    timelineRange = range
                                    selectedNoteIds = emptySet()
                                })
                            }
                        )
                    }
                }
            }
            if (searchVisible) OutlinedTextField(value = search, onValueChange = onSearch,
                modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.screenHorizontal, vertical = AppSpacing.sm),
                placeholder = { Text("Поиск по заметкам") }, singleLine = true)

            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (timelineMode) {
                            HomeTimelineOperations.emptyStateMessage(
                                timelineRange,
                                searching = search.isNotBlank()
                            )
                        } else if (search.isNotBlank()) "Ничего не найдено" else "Пока нет записей"
                    )
                }
            } else {
                Box(
                    Modifier.fillMaxSize()
                        .pointerInput(selectionMode) {
                            if (selectionMode) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                    // Handle touch target: 40dp wide with a 28dp right inset.
                                    val handleZone = 72.dp.toPx()
                                    if (down.position.x < size.width - handleZone) {
                                        var totalX = 0f
                                        var totalY = 0f
                                        var finished = false
                                        val clearDistance = 64.dp.toPx()
                                        while (!finished) {
                                            val event = awaitPointerEvent(PointerEventPass.Initial)
                                            val change = event.changes.firstOrNull() ?: break
                                            if (!change.pressed) {
                                                finished = true
                                            } else {
                                                val delta = change.position - change.previousPosition
                                                totalX += delta.x
                                                totalY += delta.y
                                                val absX = kotlin.math.abs(totalX)
                                                val absY = kotlin.math.abs(totalY)
                                                val touchSlop = viewConfiguration.touchSlop
                                                if (absY > touchSlop && absY > absX) {
                                                    finished = true
                                                } else if (absX >= clearDistance && absX > absY * 1.25f) {
                                                    selectedNoteIds = emptySet()
                                                    change.consume()
                                                    finished = true
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(
                                    requireUnconsumed = false,
                                    pass = PointerEventPass.Initial
                                )
                                if (latestIsDraggingForPointer.value ||
                                    !latestSearchBlankForPointer.value
                                ) return@awaitEachGesture

                                // Hit-test the handle in viewport coordinates. This listener
                                // remains attached while the keyed row changes its list position.
                                val handleRight = size.width - CardDimensions.handleHitTargetRightInset.toPx()
                                val handleLeft = handleRight - CardDimensions.handleTouchTargetSize.toPx()
                                if (down.position.x < handleLeft ||
                                    down.position.x > handleRight
                                ) return@awaitEachGesture

                                val item = listState.layoutInfo.visibleItemsInfo.firstOrNull {
                                    down.position.y >= it.offset &&
                                        down.position.y < it.offset + it.size
                                } ?: return@awaitEachGesture
                                val handleTop = item.offset + CardDimensions.handleHitTargetTopOffset.toPx()
                                val handleBottom = handleTop + CardDimensions.handleTouchTargetSize.toPx()
                                if (down.position.y < handleTop || down.position.y > handleBottom) {
                                    return@awaitEachGesture
                                }
                                val key = item.key as? String ?: return@awaitEachGesture
                                val row = latestRowsForPointer.value.firstOrNull { it.key == key }
                                    ?: return@awaitEachGesture
                                val anchorNote = row.notes.firstOrNull()
                                    ?: return@awaitEachGesture

                                // Observe movement in the Initial pass so the LazyColumn's
                                // scroll detector cannot steal a drag that began on the handle.
                                // The down event stays unconsumed; vertical intent claims it at slop.
                                var totalX = 0f
                                var totalY = 0f
                                var dragStartY = Float.NaN
                                while (dragStartY.isNaN()) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                        ?: break
                                    if (!change.pressed) break

                                    val delta = change.position - change.previousPosition
                                    totalX += delta.x
                                    totalY += delta.y
                                    val absX = kotlin.math.abs(totalX)
                                    val absY = kotlin.math.abs(totalY)
                                    val touchSlop = viewConfiguration.touchSlop

                                    if (absY > touchSlop && absY > absX) {
                                        change.consume()
                                        dragStartY = change.position.y
                                    } else if (absX > touchSlop && absX >= absY) {
                                        return@awaitEachGesture
                                    }
                                }
                                if (dragStartY.isNaN()) return@awaitEachGesture
                                if (latestIsDraggingForPointer.value) return@awaitEachGesture
                                latestStartDrag.value.invoke(
                                    anchorNote,
                                    dragStartY,
                                    dragStartY - item.offset
                                )

                                var released = false
                                try {
                                    var tracking = true
                                    while (tracking) {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        val change = event.changes.firstOrNull { it.id == down.id }
                                            ?: break
                                        if (!change.pressed) {
                                            released = true
                                            dragReleaseRequested = true
                                            tracking = false
                                        } else {
                                            dragPointerY = change.position.y
                                            change.consume()
                                        }
                                    }
                                } finally {
                                    // Cancellation is not finger-up. Discard the transient session
                                    // instead of committing a partial reorder accidentally.
                                    if (!released && draggingIds.isNotEmpty()) {
                                        draggingIds = emptySet()
                                        reorderSession = null
                                        dragBaseRows = emptyMap()
                                        dragPointerY = 0f
                                        dragGrabOffsetPx = 0f
                                        dragReleaseRequested = false
                                    }
                                }
                            }
                        }
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 88.dp)
                    ) {
                        // A permanent keyed anchor keeps LazyColumn's first-visible
                        // item identity stable while the first two note rows exchange.
                        // Its 1dp height is visually negligible but prevents the list
                        // from following a note that is being displaced from index 0.
                        item(key = "notes_reorder_viewport_anchor") {
                            Spacer(Modifier.height(1.dp))
                        }
                        if (moduleSummary != null) {
                            item(key = "module_summary") {
                                Column(
                                    modifier = Modifier.fillMaxWidth()
                                        .padding(horizontal = AppSpacing.screenHorizontal, vertical = AppSpacing.md)
                                ) {
                                    Text(
                                        moduleSummary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        itemsIndexed(visibleDragRows, key = { _, row -> row.key }) { index, dragRow ->
                            // Keep placement animation attached to every keyed row,
                            // including index 0. Toggling it off at the first position
                            // breaks continuity when a row crosses the top boundary.
                            val rowPlacementModifier = Modifier.animateItemPlacement(
                                animationSpec = tween(durationMillis = NOTE_REORDER_ANIMATION_DURATION_MILLIS)
                            )
                            val anchorNoteId = dragRow.key.toLongOrNull()
                            val anchorNote = dragRow.notes.firstOrNull { it.id == anchorNoteId } ?: dragRow.notes.first()
                            val rowDay = timelineDayKey(anchorNote)
                            val previousNote = visibleDragRows.getOrNull(index - 1)?.notes?.firstOrNull()
                            val previousDay = previousNote?.let { timelineDayKey(it) }
                            val isFavoritesView = timelineRange == HomeTimelineRange.FAVORITES
                            val startsNewSection = if (isFavoritesView) {
                                index == 0 || previousNote?.type != anchorNote.type
                            } else {
                                index == 0 || rowDay != previousDay
                            }
                            Column(Modifier.fillMaxWidth()) {
                                if (timelineMode && !isDragging && startsNewSection) {
                                    Text(
                                        text = if (isFavoritesView) {
                                            HomeTimelineOperations.moduleSectionTitle(anchorNote.type)
                                        } else homeTimelineDayLabel(rowDay, todayDay),
                                        modifier = Modifier.fillMaxWidth().padding(
                                            start = AppSpacing.screenHorizontal,
                                            end = AppSpacing.screenHorizontal,
                                            top = if (index == 0) AppSpacing.sm else AppSpacing.lg,
                                            bottom = AppSpacing.xs
                                        ),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Box(
                                    Modifier.fillMaxWidth().then(rowPlacementModifier)
                                ) {
                                if (dragRow.notes.size > 1) {
                                    DraggedNotesGroup(
                                        notes = dragRow.notes,
                                        dark = dark,
                                        noteColors = noteColors,
                                        customSelectionColor = selectedNoteBackground,
                                        anchorNoteId = anchorNote.id,
                                        rowHeight = with(density) {
                                            (measuredNoteCardHeights[anchorNote.id] ?: CardDimensions.rowHeight.roundToPx()).toDp()
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else {
                                    val note = dragRow.notes.first()
                                    val isSelected = note.id in selectedNoteIds
                                    NoteRow(
                                        note = note, dark = dark, noteColors = noteColors,
                                        customSelectionColor = selectedNoteBackground, pinned = note.pinned, selected = isSelected,
                                        onClick = {
                                            if (selectionMode) selectedNoteIds = if (isSelected) selectedNoteIds - note.id else selectedNoteIds + note.id
                                            else onOpen(note.id)
                                        },
                                        onLongPress = { selectedNoteIds = selectedNoteIds + note.id },
                                        colorSwipeEnabled = !selectionMode && !isDragging,
                                        onChangeColor = { next -> onChangeNoteColor(note.id, next) },
                                        interactiveCards = interactiveCards,
                                        onQuickAction = { onQuickAction(note) },
                                        showSecondaryPreview = CardPreviewField.forModule(AppModule.entries.firstOrNull { it.recordType == note.type } ?: AppModule.ALL).any { it.key in cardPreviewFields },
                                        previewFields = cardPreviewFields,
                                        onMeasuredHeight = { heightPx ->
                                            if (measuredNoteCardHeights[note.id] != heightPx) {
                                                measuredNoteCardHeights[note.id] = heightPx
                                            }
                                        }
                                    )
                                }

                                // This handle layer belongs to the stable keyed row, not to
                                // NoteRow. It therefore survives the NoteRow -> group placeholder
                                // replacement that happens as soon as dragging starts.
                                if (search.isBlank()) {
                                    Box(
                                        Modifier.align(Alignment.TopEnd)
                                            .padding(top = CardDimensions.handleHitTargetTopOffset, end = CardDimensions.handleHitTargetRightInset)
                                            // Match the top control slot in the right-side card rail.
                                            .size(CardDimensions.handleTouchTargetSize)
                                            .zIndex(2f)

                                    )
                                }
                                }
                            }
                        }
                    }

                }
            }
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(onDismissRequest = { showDeleteConfirmation = false }, title = { Text("Удалить заметки?") },
            text = { Text("Будет удалено заметок: ${selectedNoteIds.size}.") },
            confirmButton = { TextButton(onClick = { onDeleteSelected(selectedNoteIds); selectedNoteIds = emptySet(); showDeleteConfirmation = false }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { showDeleteConfirmation = false }) { Text("Отмена") } })
    }
    if (showNewCollectionDialog) {
        AlertDialog(onDismissRequest = { showNewCollectionDialog = false }, title = { Text("Новая подборка") },
            text = { OutlinedTextField(value = newCollectionName, onValueChange = { newCollectionName = it }, singleLine = true, placeholder = { Text("Название") }) },
            confirmButton = { TextButton(enabled = newCollectionName.isNotBlank(), onClick = {
                val name = newCollectionName.trim()
                if (name.isNotEmpty() && collections.none { it.name.equals(name, true) }) onCreateCollection(name)
                showNewCollectionDialog = false
            }) { Text("Создать") } },
            dismissButton = { TextButton(onClick = { showNewCollectionDialog = false }) { Text("Отмена") } })
    }
    if (renameCollectionId != null) {
        AlertDialog(onDismissRequest = { renameCollectionId = null }, title = { Text("Переименовать подборку") },
            text = { OutlinedTextField(value = renameCollectionName, onValueChange = { renameCollectionName = it }, singleLine = true) },
            confirmButton = { TextButton(enabled = renameCollectionName.isNotBlank(), onClick = {
                val id = renameCollectionId; val name = renameCollectionName.trim()
                if (id != null && name.isNotEmpty() && collections.none { it.id != id && it.name.equals(name, true) }) onRenameCollection(id, name)
                renameCollectionId = null
            }) { Text("Сохранить") } },
            dismissButton = { TextButton(onClick = { renameCollectionId = null }) { Text("Отмена") } })
    }
    if (deleteCollectionId != null) {
        AlertDialog(onDismissRequest = { deleteCollectionId = null }, title = { Text("Удалить подборку?") },
            text = { Text("Её заметки будут возвращены в основной блокнот.") },
            confirmButton = { TextButton(onClick = { deleteCollectionId?.let(onDeleteCollection); deleteCollectionId = null }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { deleteCollectionId = null }) { Text("Отмена") } })
    }
}



private fun homeTimelineDayLabel(day: Long, todayDay: Long): String {
    return when (day) {
        todayDay -> "Сегодня"
        todayDay + 1 -> "Завтра"
        todayDay - 1 -> "Вчера"
        else -> java.text.SimpleDateFormat("EEEE, d MMMM", java.util.Locale.getDefault()).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(java.util.Date(day * 86_400_000L))
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
    }
}
