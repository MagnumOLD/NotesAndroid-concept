package com.magnumold.notes

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun NotesApp(
    activity: MainActivity,
    recordsViewModel: NotesViewModel,
    timeTrackingRepository: TimeTrackingRepository,
    recoveryDataRepository: RecoveryDataRepository,
    savedThemeMode: String,
    initialThemeColorSets: ThemeColorSets
) {
            val snapshot by recordsViewModel.snapshot.collectAsStateWithLifecycle()
            val moduleSettingsStore = remember(activity) {
                ModuleSettingsStore(activity.getSharedPreferences("notes_modules", Context.MODE_PRIVATE))
            }
            val cardInteractionStore = remember(activity) {
                CardInteractionSettingsStore(activity.getSharedPreferences("notes_card_behavior", Context.MODE_PRIVATE))
            }
            val cardPreviewStore = remember(activity) {
                CardPreviewSettingsStore(activity.getSharedPreferences("notes_card_previews", Context.MODE_PRIVATE))
            }
            var cardPreviewFields by remember(cardPreviewStore) { androidx.compose.runtime.mutableStateOf(cardPreviewStore.load()) }
            val collectionVisibilityStore = remember(activity) {
                CollectionVisibilitySettingsStore(activity.getSharedPreferences("notes_collection_visibility", Context.MODE_PRIVATE))
            }
            val homeButtonLayoutStore = remember(activity) {
                HomeButtonLayoutStore(activity.getSharedPreferences("notes_home_buttons", Context.MODE_PRIVATE))
            }
            var homeButtonLayout by remember(homeButtonLayoutStore) {
                androidx.compose.runtime.mutableStateOf(homeButtonLayoutStore.load())
            }
            val homeAddRecordTypeStore = remember(activity) {
                HomeAddRecordTypeStore(activity.getSharedPreferences("notes_home_add", Context.MODE_PRIVATE))
            }
            var homeAddRecordType by remember(homeAddRecordTypeStore) {
                androidx.compose.runtime.mutableStateOf(homeAddRecordTypeStore.load())
            }
            var collectionVisibleModules by remember(collectionVisibilityStore) {
                androidx.compose.runtime.mutableStateOf(collectionVisibilityStore.load())
            }
            var homeCollectionVisible by remember(collectionVisibilityStore) {
                androidx.compose.runtime.mutableStateOf(collectionVisibilityStore.loadHomeVisible())
            }
            var interactiveCardsEnabled by remember(cardInteractionStore) {
                androidx.compose.runtime.mutableStateOf(cardInteractionStore.load())
            }
            val trashRetentionStore = remember(activity) {
                TrashRetentionStore(activity.getSharedPreferences("notes_storage", Context.MODE_PRIVATE))
            }
            var trashRetentionDays by remember(trashRetentionStore) {
                androidx.compose.runtime.mutableIntStateOf(trashRetentionStore.loadDays())
            }
            var moduleConfiguration by remember(moduleSettingsStore) {
                androidx.compose.runtime.mutableStateOf(moduleSettingsStore.load())
            }
            val themeSettings = remember(activity) {
                ThemeSettingsStore(activity.getSharedPreferences("notes_settings", Context.MODE_PRIVATE))
            }
            val homeTimelineDisplayStore = remember(activity) {
                HomeTimelineDisplayStore(activity.getSharedPreferences("notes_home_timeline", Context.MODE_PRIVATE))
            }
            var showTimelineDateHeaders by remember(homeTimelineDisplayStore) {
                androidx.compose.runtime.mutableStateOf(homeTimelineDisplayStore.loadShowDateHeaders())
            }
            var dateHeaderTextSize by remember(homeTimelineDisplayStore) {
                androidx.compose.runtime.mutableIntStateOf(homeTimelineDisplayStore.loadDateHeaderTextSize())
            }
            var manualHomeOrderIds by remember(homeTimelineDisplayStore) {
                androidx.compose.runtime.mutableStateOf(homeTimelineDisplayStore.loadManualOrder())
            }
            val backupFileStore = remember(activity, timeTrackingRepository, recoveryDataRepository) {
                BackupFileStore(
                    contentResolver = activity.contentResolver,
                    timeTrackingStateProvider = { timeTrackingRepository.load() },
                    recoveryDataProvider = { recoveryDataRepository.exportEntries() }
                )
            }
            val timeTrackingState by timeTrackingRepository.state.collectAsStateWithLifecycle()
            val protectionTimerScheduler = remember(activity) { ProtectionTimerScheduler(activity) }
            val recordTimerScheduler = remember(activity) { RecordTimerScheduler(activity) }
            val kitchenTimerScheduler = remember(activity) { KitchenTimerScheduler(activity) }
            val kitchenTimerRepository = remember(activity) {
                KitchenTimerRepository(activity.getSharedPreferences("kitchen_timers", Context.MODE_PRIVATE))
            }
            var kitchenTimers by remember(kitchenTimerRepository) {
                androidx.compose.runtime.mutableStateOf(kitchenTimerRepository.load().timers)
            }
            var kitchenTimerSettings by remember(kitchenTimerRepository) {
                androidx.compose.runtime.mutableStateOf(kitchenTimerRepository.load().settings)
            }
            LaunchedEffect(kitchenTimers) {
                kitchenTimerRepository.saveTimers(kitchenTimers)
                kitchenTimerScheduler.scheduleAll(kitchenTimers)
            }
            LaunchedEffect(kitchenTimerSettings) {
                kitchenTimerRepository.saveSettings(kitchenTimerSettings)
            }
            val activeTimerRecords = remember(snapshot.notes, snapshot.collections) {
                HomeTimelineOperations.records(snapshot).filter {
                    it.type == RecordType.TIMER && it.timerEndsAtMillis != null
                }
            }
            LaunchedEffect(activeTimerRecords.map { it.id to it.timerEndsAtMillis }) {
                activeTimerRecords.forEach(recordTimerScheduler::sync)
            }
            LaunchedEffect(timeTrackingState.protectionTimer) {
                val timer = timeTrackingState.protectionTimer
                if (timer == null) protectionTimerScheduler.cancel()
                else protectionTimerScheduler.schedule(timer)
            }
            fun closeActiveSessionsForRemoved(records: List<Note>) {
                if (records.isEmpty()) return
                timeTrackingRepository.update { current ->
                    syncWorkSessionsForTaskChanges(
                        oldRoots = records,
                        newRoots = emptyList(),
                        currentState = current,
                        nowMillis = System.currentTimeMillis()
                    )
                }
            }
            val systemDark = isSystemInDarkTheme()
            val themeState = remember(themeSettings, savedThemeMode, initialThemeColorSets) {
                AppThemeState(
                    initialMode = savedThemeMode,
                    initialColorSets = initialThemeColorSets,
                    initialNoteColors = themeSettings.loadNoteColors(),
                    initialNoteColorOpacities = themeSettings.loadNoteColorOpacities(),
                    initialTimelineDateColorSets = themeSettings.loadTimelineDateColorSets()
                )
            }
            val themeMode = themeState.mode
            val themeColorSets = themeState.colorSets
            val activeThemeColors = themeState.activeColors(systemDark)
            val dark = themeState.isDark(systemDark)
            val noteColors = themeState.noteColors
            val activeNoteColorOpacity = themeState.activeNoteColorOpacity(systemDark)
            val activeTimelineDateColors = themeState.activeTimelineDateColors(systemDark)
            val displayedNoteColors = remember(noteColors, activeNoteColorOpacity) {
                noteColors.map { it.copy(alpha = activeNoteColorOpacity) }
            }
            val notes = snapshot.notes
            val collections = snapshot.collections
            val navigation = remember { AppNavigationState() }
            val activeCollectionId = navigation.activeCollectionId
            val screenStateHolder = rememberSaveableStateHolder()
            val notesListStateKey = when {
                activeCollectionId != null ->
                    "notes_list:${navigation.activeModule.name}:collection:$activeCollectionId"
                navigation.activeModule == AppModule.ALL -> "notes_list:home"
                else -> "notes_list:module:${navigation.activeModule.name}"
            }
            val selectedId = navigation.selectedId
            val homeTimelineMode = navigation.activeModule == AppModule.ALL && activeCollectionId == null
            val sourceNotes = when {
                activeCollectionId != null -> collections.firstOrNull { it.id == activeCollectionId }?.notes.orEmpty()
                homeTimelineMode -> HomeTimelineOperations.records(snapshot)
                else -> notes.toList()
            }
            val enabledSourceNotes = ModuleVisibilityOperations.visibleRecords(sourceNotes, moduleConfiguration.enabled)
            val activeNotes = enabledSourceNotes.filter {
                navigation.activeModule.recordType == null || it.type == navigation.activeModule.recordType
            }
            val allHomeRecords = remember(snapshot.notes, snapshot.collections) {
                HomeTimelineOperations.records(snapshot)
            }
            LaunchedEffect(showTimelineDateHeaders) {
                homeTimelineDisplayStore.saveShowDateHeaders(showTimelineDateHeaders)
            }
            LaunchedEffect(dateHeaderTextSize) {
                homeTimelineDisplayStore.saveDateHeaderTextSize(dateHeaderTextSize)
            }
            LaunchedEffect(manualHomeOrderIds) {
                manualHomeOrderIds?.let(homeTimelineDisplayStore::saveManualOrder)
            }
            LaunchedEffect(showTimelineDateHeaders, allHomeRecords.map { it.id }, manualHomeOrderIds) {
                val currentOrder = manualHomeOrderIds
                if (!showTimelineDateHeaders && currentOrder != null) {
                    val validIds = allHomeRecords.map { it.id }.toSet()
                    val retained = currentOrder.filter { it in validIds }.distinct()
                    val retainedSet = retained.toSet()
                    val newIds = HomeTimelineOperations.orderedRecordsByDate(allHomeRecords)
                        .map { it.id }.filterNot { it in retainedSet }
                    val reconciled = newIds + retained
                    if (reconciled != currentOrder) manualHomeOrderIds = reconciled
                }
            }
            val autoFocusNewNote = navigation.autoFocusNewNote
            val search = navigation.search
            val searchVisible = navigation.searchVisible
            val settingsVisible = navigation.settingsVisible
            val vaultScreen = navigation.vaultScreen
            val archived = snapshot.archived
            val trash = snapshot.trash
            LaunchedEffect(trash, trashRetentionDays) {
                recordsViewModel.purgeExpiredTrash(trashRetentionDays)
            }

            val backupActions = rememberBackupActions(
                activity = activity,
                snapshot = snapshot,
                recordsViewModel = recordsViewModel,
                backupFileStore = backupFileStore
            )

            AppBackHandler(enabled = navigation.hasTopLevelBackAction) {
                navigation.handleTopLevelBack()
            }

            AppThemeEffects(
                activity = activity,
                themeMode = themeMode,
                dark = dark,
                themeColors = activeThemeColors,
                themeColorSets = themeColorSets,
                noteColors = noteColors,
                noteColorOpacities = themeState.noteColorOpacities,
                timelineDateColorSets = themeState.timelineDateColorSets,
                themeSettings = themeSettings
            )

            NotesTheme(dark, activeThemeColors) {
                val selected = activeNotes.firstOrNull { it.id == selectedId }
                if (navigation.modulesVisible && navigation.moduleManagementVisible && selected == null) {
                    ModuleManagementScreen(
                        configuration = moduleConfiguration,
                        onConfigurationChange = { updated ->
                            moduleConfiguration = updated.normalized()
                            moduleSettingsStore.save(moduleConfiguration)
                            if (navigation.activeModule != AppModule.ALL && navigation.activeModule !in moduleConfiguration.enabled) {
                                navigation.returnToHomeKeepingModuleScreens()
                            }
                        },
                        themeColors = activeThemeColors
                    )
                } else if (navigation.modulesVisible && selected == null) {
                    ModulesScreen(
                        configuration = moduleConfiguration,
                        onConfigurationChange = { updated ->
                            moduleConfiguration = updated.normalized()
                            moduleSettingsStore.save(moduleConfiguration)
                            if (navigation.activeModule != AppModule.ALL && navigation.activeModule !in moduleConfiguration.enabled) {
                                navigation.selectModule(AppModule.ALL)
                            }
                        },
                        onOpenModule = { module -> navigation.selectModule(module) },
                        onOpenManagement = { navigation.openModuleManagement() },
                        onBack = { navigation.closeModules() },
                        themeColors = activeThemeColors
                    )
                } else if (settingsVisible && selected == null && vaultScreen != null) {
                    VaultScreen(
                        title = if (navigation.vaultScreen == "archive") "Архив" else "Корзина",
                        entries = if (navigation.vaultScreen == "archive") archived else trash,
                        isTrash = navigation.vaultScreen == "trash",
                        onBack = { navigation.closeVault() },
                        onRestore = { entry ->
                            recordsViewModel.restoreVaultEntry(entry, fromArchive = navigation.vaultScreen == "archive")
                            recordsViewModel.flush()
                            val restored = HomeTimelineOperations.records(recordsViewModel.snapshot.value)
                                .firstOrNull { it.id == entry.note.id }
                            if (restored != null) recordTimerScheduler.sync(restored)
                            else recordTimerScheduler.cancel(entry.note.id)
                        },
                        onMoveToTrash = { entry ->
                            recordTimerScheduler.cancel(entry.note.id)
                            recordsViewModel.moveArchivedToTrash(entry)
                        },
                        onDeleteForever = { entry ->
                            recordTimerScheduler.cancel(entry.note.id)
                            recordsViewModel.deleteFromTrash(entry)
                        },
                        onEmptyTrash = {
                            snapshot.trash.forEach { recordTimerScheduler.cancel(it.note.id) }
                            recordsViewModel.emptyTrash()
                        },
                        themeColors = activeThemeColors
                    )
                } else if (settingsVisible && selected == null) {
                    SettingsScreen(
                        themeMode = themeMode,
                        activeColorMode = if (themeMode == "system") if (dark) "dark" else "light" else themeMode,
                        onThemeModeChange = { themeState.changeMode(it) },
                        themeColors = activeThemeColors,
                        onThemeColorChange = { key, color -> themeState.updateThemeColor(key, color, systemDark) },
                        onResetThemeColors = { themeState.resetThemeColors(systemDark) },
                        noteColors = noteColors,
                        noteColorOpacity = activeNoteColorOpacity,
                        timelineDateColors = activeTimelineDateColors,
                        onTimelineDateColorChange = { key, color -> themeState.updateTimelineDateColor(key, color, systemDark) },
                        onResetTimelineDateColors = { themeState.resetTimelineDateColors(systemDark) },
                        showTimelineDateHeaders = showTimelineDateHeaders,
                        onShowTimelineDateHeadersChange = { enabled ->
                            if (!enabled && manualHomeOrderIds == null) {
                                manualHomeOrderIds = HomeTimelineOperations.orderedRecordsByDate(allHomeRecords).map { it.id }
                            }
                            showTimelineDateHeaders = enabled
                        },
                        dateHeaderTextSize = dateHeaderTextSize,
                        onDateHeaderTextSizeChange = { dateHeaderTextSize = it },
                        onNoteColorsChange = { themeState.replaceNoteColors(it) },
                        onNoteColorOpacityChange = { opacity -> themeState.setNoteColorOpacity(opacity, systemDark) },
                        onExport = backupActions.exportBackup,
                        onImport = backupActions.importBackup,
                        onOpenArchive = { navigation.openVault("archive") },
                        onOpenTrash = { navigation.openVault("trash") },
                        trashRetentionDays = trashRetentionDays,
                        interactiveCardsEnabled = interactiveCardsEnabled,
                        cardPreviewFields = cardPreviewFields,
                        onCardPreviewFieldsChange = { fields -> cardPreviewFields = fields; cardPreviewStore.save(fields) },
                        collectionVisibleModules = collectionVisibleModules,
                        onCollectionVisibilityChange = { module, visible ->
                            collectionVisibleModules = if (visible) collectionVisibleModules + module else collectionVisibleModules - module
                            collectionVisibilityStore.save(collectionVisibleModules)
                            if (!visible && navigation.activeModule == module && navigation.activeCollectionId != null) {
                                navigation.selectCollection(null)
                            }
                        },
                        homeCollectionVisible = homeCollectionVisible,
                        onHomeCollectionVisibilityChange = { visible ->
                            homeCollectionVisible = visible
                            collectionVisibilityStore.saveHomeVisible(visible)
                        },
                        homeButtonLayout = homeButtonLayout,
                        onHomeButtonLayoutChange = { layout ->
                            homeButtonLayout = layout
                            homeButtonLayoutStore.save(layout)
                        },
                        homeAddRecordType = homeAddRecordType,
                        enabledModules = moduleConfiguration.enabled,
                        onHomeAddRecordTypeChange = { type ->
                            homeAddRecordType = type
                            homeAddRecordTypeStore.save(type)
                        },
                        onInteractiveCardsEnabledChange = { enabled ->
                            interactiveCardsEnabled = enabled
                            cardInteractionStore.save(enabled)
                        },
                        onTrashRetentionDaysChange = { days ->
                            trashRetentionDays = days
                            trashRetentionStore.saveDays(days)
                        },
                        onBack = { navigation.closeSettings() }
                    )
                } else if (
                    navigation.activeModule == AppModule.TIMERS &&
                    activeCollectionId == null &&
                    selected == null
                ) {
                    KitchenTimersScreen(
                        timers = kitchenTimers,
                        settings = kitchenTimerSettings,
                        topBarColor = activeThemeColors.topBar,
                        onTimersChange = { updated ->
                            kitchenTimers.filter { old ->
                                old.alertStartedAtMillis != null &&
                                    updated.none { it.id == old.id && it.status == KitchenTimerStatus.FINISHED }
                            }.forEach { kitchenTimerScheduler.cancel(it.id) }
                            kitchenTimers = updated
                        },
                        onSettingsChange = { kitchenTimerSettings = it },
                        onRefresh = {
                            val refreshed = kitchenTimerRepository.load()
                            kitchenTimers = refreshed.timers
                            kitchenTimerSettings = refreshed.settings
                            kitchenTimerScheduler.scheduleAll(refreshed.timers)
                        }
                    )
                } else if (selected == null) {
                    screenStateHolder.SaveableStateProvider(notesListStateKey) {
                    NotesHome(
                        notes = activeNotes, search = search, searchVisible = searchVisible,
                        noteColors = displayedNoteColors,
                        themeColors = activeThemeColors,
                        timelineDateColors = activeTimelineDateColors,
                        showTimelineDateHeaders = showTimelineDateHeaders,
                        dateHeaderTextSize = dateHeaderTextSize,
                        manualHomeOrderIds = manualHomeOrderIds,
                        collections = collections, activeCollectionId = activeCollectionId,
                        onSelectCollection = { navigation.selectCollection(it) },
                        onCreateCollection = { name ->
                            val id = newRecordId()
                            recordsViewModel.createCollection(id, name)
                            navigation.selectCollection(id)
                        },
                        onRenameCollection = { id, name ->
                            recordsViewModel.renameCollection(id, name)
                        },
                        onDeleteCollection = { id ->
                            if (recordsViewModel.deleteCollection(id) && navigation.activeCollectionId == id) {
                                navigation.selectCollection(null)
                            }
                        },
                        onMoveCollection = { id, delta ->
                            recordsViewModel.moveCollection(id, delta)
                        },
                        onTransferSelected = { ids, targetId ->
                            if (targetId != activeCollectionId) {
                                recordsViewModel.transferRecords(activeCollectionId, targetId, ids)
                            }
                        },
                        onSearch = { navigation.updateSearch(it) },
                        onToggleSearch = { navigation.toggleSearch() },
                        dark = dark,
                        onOpenSettings = { navigation.openSettings() },
                        onOpen = { navigation.openRecord(it) },
                        onArchiveSelected = { ids ->
                            val selectedRecords = activeNotes.filter { it.id in ids }
                            closeActiveSessionsForRemoved(selectedRecords)
                            selectedRecords.filter { it.type == RecordType.TIMER }
                                .forEach { recordTimerScheduler.cancel(it.id) }
                            if (homeTimelineMode) recordsViewModel.moveHomeRecordsToVault(ids, toTrash = false)
                            else recordsViewModel.moveActiveRecordsToVault(activeCollectionId, ids, toTrash = false)
                        },
                        onDeleteSelected = { ids ->
                            val selectedRecords = activeNotes.filter { it.id in ids }
                            closeActiveSessionsForRemoved(selectedRecords)
                            selectedRecords.filter { it.type == RecordType.TIMER }
                                .forEach { recordTimerScheduler.cancel(it.id) }
                            if (homeTimelineMode) recordsViewModel.moveHomeRecordsToVault(ids, toTrash = true)
                            else recordsViewModel.moveActiveRecordsToVault(activeCollectionId, ids, toTrash = true)
                        },
                        onMoveSelected = { ids, afterId, freeOrderMode ->
                            if (homeTimelineMode && freeOrderMode) {
                                val currentVisibleOrder = HomeTimelineOperations.orderedVisibleRecordsManually(
                                    activeNotes, manualHomeOrderIds
                                )
                                val reorderedVisible = NoteReorderModel.moveSelectedInCurrentOrder(
                                    currentVisibleOrder, ids, afterId
                                )
                                val visibleIds = activeNotes.filter { it.showOnHome }.map { it.id }.toSet()
                                val allIds = allHomeRecords.map { it.id }.toSet()
                                val fullOrder = (
                                    manualHomeOrderIds.orEmpty() +
                                        HomeTimelineOperations.orderedRecordsByDate(allHomeRecords).map { it.id }
                                    ).distinct().filter { it in allIds }
                                val reorderedIds = reorderedVisible.map { it.id }
                                val reorderedIterator = reorderedIds.iterator()
                                manualHomeOrderIds = fullOrder.map { id ->
                                    if (id in visibleIds && reorderedIterator.hasNext()) reorderedIterator.next() else id
                                }
                            } else if (homeTimelineMode) {
                                recordsViewModel.reorderHomeRecords(ids, afterId)
                            } else {
                                recordsViewModel.reorderActiveRecords(activeCollectionId, ids, afterId)
                            }
                        },
                        onTogglePinned = { ids ->
                            if (homeTimelineMode) recordsViewModel.toggleHomeRecordsPinned(ids)
                            else recordsViewModel.toggleActiveRecordsPinned(activeCollectionId, ids)
                        },
                        onToggleFavorite = { ids -> recordsViewModel.toggleRecordsFavorite(ids) },
                        onSetShowOnHome = { ids, show -> recordsViewModel.setRecordsShowOnHome(ids, show) },
                        onChangeNoteColor = { noteId, colorIndex ->
                            if (homeTimelineMode) recordsViewModel.changeHomeRecordColor(noteId, colorIndex)
                            else recordsViewModel.changeActiveRecordColor(activeCollectionId, noteId, colorIndex)
                        },
                        screenTitle = navigation.activeModule.title,
                        onOpenModules = { navigation.openModules() },
                        timelineMode = homeTimelineMode,
                        interactiveCards = interactiveCardsEnabled,
                        cardPreviewFields = cardPreviewFields,
                        showCollections = if (navigation.activeModule == AppModule.ALL) {
                            homeCollectionVisible
                        } else {
                            navigation.activeModule in collectionVisibleModules
                        },
                        collectionsAvailable = navigation.activeModule == AppModule.ALL ||
                            navigation.activeModule in collectionVisibleModules,
                        enabledModules = moduleConfiguration.enabledInOrder(),
                        homeButtonLayout = homeButtonLayout,
                        homeAddRecordType = homeAddRecordType,
                        isMainHome = navigation.activeModule == AppModule.ALL,
                        onQuickAction = { record ->
                            val now = System.currentTimeMillis()
                            val changed = CardInteractionOperations.applyQuickAction(record, now) ?: return@NotesHome
                            if (record.type == RecordType.TASK) {
                                timeTrackingRepository.update { current ->
                                    syncWorkSessionsForTaskChanges(
                                        oldRoots = listOf(record),
                                        newRoots = listOf(changed),
                                        currentState = current,
                                        nowMillis = now
                                    )
                                }
                            }
                            val updated = changed.copy(updated = now)
                            if (homeTimelineMode) recordsViewModel.updateHomeRecord(updated)
                            else recordsViewModel.updateActiveRecord(activeCollectionId, updated)
                            if (record.type == RecordType.TIMER) recordTimerScheduler.sync(updated)
                        },
                        onCreate = { requestedType ->
                            val type = navigation.activeModule.recordType ?: requestedType
                            val now = System.currentTimeMillis()
                            val id = newRecordId()
                            recordsViewModel.createActiveRecord(activeCollectionId, Note(id, "", listOf(NoteBlock()), now, created = now, type = type))
                            navigation.openNewRecord(id)
                        }
                    )
                    }
                } else if (selected.type == RecordType.WORKOUT) {
                    WorkoutRecordEditor(
                        note = selected,
                        topBarColor = activeThemeColors.topBar,
                        onBack = {
                            if (!selected.hasContent()) {
                                if (homeTimelineMode) recordsViewModel.removeHomeRecord(selected.id)
                                else recordsViewModel.removeActiveRecord(activeCollectionId, selected.id)
                            }
                            navigation.closeEditor()
                        },
                        onChange = { changed ->
                            val updated = changed.copy(updated = System.currentTimeMillis())
                            if (homeTimelineMode) recordsViewModel.updateHomeRecord(updated)
                            else recordsViewModel.updateActiveRecord(activeCollectionId, updated)
                        }
                    )
                } else if (selected.type == RecordType.PURCHASE) {
                    PurchaseRecordEditor(
                        note = selected,
                        topBarColor = activeThemeColors.topBar,
                        onBack = {
                            if (!selected.hasContent()) {
                                if (homeTimelineMode) recordsViewModel.removeHomeRecord(selected.id)
                                else recordsViewModel.removeActiveRecord(activeCollectionId, selected.id)
                            }
                            navigation.closeEditor()
                        },
                        onChange = { changed ->
                            val updated = changed.copy(updated = System.currentTimeMillis())
                            if (homeTimelineMode) recordsViewModel.updateHomeRecord(updated)
                            else recordsViewModel.updateActiveRecord(activeCollectionId, updated)
                        }
                    )
                } else if (selected.type == RecordType.NUTRITION) {
                    NutritionRecordEditor(
                        note = selected,
                        products = snapshot.nutritionProducts,
                        onProductsChange = recordsViewModel::updateNutritionProducts,
                        topBarColor = activeThemeColors.topBar,
                        addButtonColor = activeThemeColors.addButton,
                        onBack = {
                            if (!selected.hasContent()) {
                                if (homeTimelineMode) recordsViewModel.removeHomeRecord(selected.id)
                                else recordsViewModel.removeActiveRecord(activeCollectionId, selected.id)
                            }
                            navigation.closeEditor()
                        },
                        onSaved = { navigation.closeEditor() },
                        onChange = { changed ->
                            val updated = changed.copy(updated = System.currentTimeMillis())
                            if (homeTimelineMode) recordsViewModel.updateHomeRecord(updated)
                            else recordsViewModel.updateActiveRecord(activeCollectionId, updated)
                        }
                    )
                } else if (selected.type == RecordType.TIMER) {
                    TimerRecordEditor(
                        note = selected,
                        topBarColor = activeThemeColors.topBar,
                        onBack = {
                            if (!selected.hasContent()) {
                                if (homeTimelineMode) recordsViewModel.removeHomeRecord(selected.id)
                                else recordsViewModel.removeActiveRecord(activeCollectionId, selected.id)
                            }
                            navigation.closeEditor()
                        },
                        onChange = { changed ->
                            val updated = changed.copy(updated = System.currentTimeMillis())
                            if (homeTimelineMode) recordsViewModel.updateHomeRecord(updated)
                            else recordsViewModel.updateActiveRecord(activeCollectionId, updated)
                            recordTimerScheduler.sync(updated)
                        }
                    )
                } else {
                    NoteEditor(
                        note = selected,
                        dark = dark,
                        topBarColor = activeThemeColors.topBar,
                        addButtonColor = activeThemeColors.addButton,
                        selectionColor = activeThemeColors.selection,
                        autoFocus = autoFocusNewNote,
                        timeTrackingState = timeTrackingState,
                        onTimeTrackingStateChange = { updated ->
                            timeTrackingRepository.update { current ->
                                mergeTimeTrackingUiChange(current, timeTrackingState, updated)
                            }
                        },
                        onBack = {
                            if (!selected.hasContent()) {
                                if (homeTimelineMode) recordsViewModel.removeHomeRecord(selected.id)
                                else recordsViewModel.removeActiveRecord(activeCollectionId, selected.id)
                            }
                            navigation.closeEditor()
                        },
                        onChange = { changed ->
                            val previous = activeNotes.firstOrNull { it.id == changed.id } ?: selected
                            val now = System.currentTimeMillis()
                            timeTrackingRepository.update { current ->
                                syncWorkSessionsForTaskChanges(
                                    oldRoots = listOf(previous),
                                    newRoots = listOf(changed),
                                    currentState = current,
                                    nowMillis = now
                                )
                            }
                            val index = activeNotes.indexOfFirst { it.id == changed.id }
                            if (index >= 0) {
                                val updated = changed.copy(updated = now)
                                if (homeTimelineMode) recordsViewModel.updateHomeRecord(updated)
                                else recordsViewModel.updateActiveRecord(activeCollectionId, updated)
                            }
                        },
                        onDelete = { promoteChildren ->
                            closeActiveSessionsForRemoved(listOf(selected))
                            if (homeTimelineMode) recordsViewModel.moveHomeRecordsToVault(setOf(selected.id), toTrash = true)
                            else recordsViewModel.moveActiveRecordsToVault(activeCollectionId, setOf(selected.id), toTrash = true)
                            navigation.closeEditor()
                        },
                        onShare = { activity.shareNote(selected) }
                    )
                }
            }
}
