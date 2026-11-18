package com.magnumold.notes

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun NotesApp(
    activity: MainActivity,
    recordsViewModel: NotesViewModel,
    timeTrackingRepository: TimeTrackingRepository,
    savedThemeMode: String,
    initialThemeColorSets: ThemeColorSets
) {
            val snapshot by recordsViewModel.snapshot.collectAsStateWithLifecycle()
            val moduleSettingsStore = remember(activity) {
                ModuleSettingsStore(activity.getSharedPreferences("notes_modules", Context.MODE_PRIVATE))
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
            val backupFileStore = remember(activity, timeTrackingRepository) {
                BackupFileStore(activity.contentResolver) { timeTrackingRepository.load() }
            }
            val timeTrackingState by timeTrackingRepository.state.collectAsStateWithLifecycle()
            val protectionTimerScheduler = remember(activity) { ProtectionTimerScheduler(activity) }
            LaunchedEffect(timeTrackingState.protectionTimer) {
                val timer = timeTrackingState.protectionTimer
                if (timer == null) protectionTimerScheduler.cancel()
                else protectionTimerScheduler.schedule(timer)
            }
            fun closeActiveSessionsForRemoved(records: List<Note>) {
                if (records.isEmpty()) return
                val updated = syncWorkSessionsForTaskChanges(
                    oldRoots = records,
                    newRoots = emptyList(),
                    currentState = timeTrackingRepository.load(),
                    nowMillis = System.currentTimeMillis()
                )
                if (updated != timeTrackingRepository.load()) timeTrackingRepository.save(updated)
            }
            val themeState = remember(themeSettings, savedThemeMode, initialThemeColorSets) {
                AppThemeState(
                    initialMode = savedThemeMode,
                    initialColorSets = initialThemeColorSets,
                    initialNoteColors = themeSettings.loadNoteColors(),
                    initialNoteColorOpacities = themeSettings.loadNoteColorOpacities()
                )
            }
            val themeMode = themeState.mode
            val themeColorSets = themeState.colorSets
            val activeThemeColors = themeState.activeColors
            val dark = themeState.isDark
            val noteColors = themeState.noteColors
            val activeNoteColorOpacity = themeState.activeNoteColorOpacity
            val displayedNoteColors = remember(noteColors, activeNoteColorOpacity) {
                noteColors.map { it.copy(alpha = activeNoteColorOpacity) }
            }
            val notes = snapshot.notes
            val collections = snapshot.collections
            val navigation = remember { AppNavigationState() }
            val activeCollectionId = navigation.activeCollectionId
            val selectedId = navigation.selectedId
            val homeTimelineMode = navigation.activeModule == AppModule.ALL && activeCollectionId == null
            val sourceNotes = when {
                activeCollectionId != null -> collections.firstOrNull { it.id == activeCollectionId }?.notes.orEmpty()
                homeTimelineMode -> HomeTimelineOperations.records(snapshot)
                else -> notes.toList()
            }
            val activeNotes = sourceNotes.filter { navigation.activeModule.recordType == null || it.type == navigation.activeModule.recordType }
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

            BackHandler(enabled = navigation.modulesVisible) { navigation.closeModules() }
            BackHandler(enabled = settingsVisible && vaultScreen != null) { navigation.closeVault() }
            BackHandler(enabled = settingsVisible && navigation.vaultScreen == null) { navigation.closeSettings() }

            AppThemeEffects(
                activity = activity,
                themeMode = themeMode,
                dark = dark,
                themeColors = activeThemeColors,
                themeColorSets = themeColorSets,
                noteColors = noteColors,
                noteColorOpacities = themeState.noteColorOpacities,
                themeSettings = themeSettings
            )

            NotesTheme(dark, activeThemeColors, themeMode == "custom") {
                val selected = activeNotes.firstOrNull { it.id == selectedId }
                if (navigation.modulesVisible && selected == null) {
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
                        onBack = { navigation.closeModules() },
                        themeColors = activeThemeColors
                    )
                } else if (settingsVisible && selected == null && vaultScreen != null) {
                    VaultScreen(
                        title = if (navigation.vaultScreen == "archive") "Архив" else "Корзина",
                        entries = if (navigation.vaultScreen == "archive") archived else trash,
                        isTrash = navigation.vaultScreen == "trash",
                        onBack = { navigation.closeVault() },
                        onRestore = { entry -> recordsViewModel.restoreVaultEntry(entry, fromArchive = navigation.vaultScreen == "archive") },
                        onMoveToTrash = { entry -> recordsViewModel.moveArchivedToTrash(entry) },
                        onDeleteForever = { entry -> recordsViewModel.deleteFromTrash(entry) },
                        onEmptyTrash = { recordsViewModel.emptyTrash() },
                        themeColors = activeThemeColors
                    )
                } else if (settingsVisible && selected == null) {
                    SettingsScreen(
                        themeMode = themeMode,
                        onThemeModeChange = { themeState.changeMode(it) },
                        themeColors = activeThemeColors,
                        onThemeColorChange = { key, color -> themeState.updateThemeColor(key, color) },
                        onResetThemeColors = { themeState.resetThemeColors() },
                        noteColors = noteColors,
                        noteColorOpacity = activeNoteColorOpacity,
                        onNoteColorsChange = { themeState.replaceNoteColors(it) },
                        onNoteColorOpacityChange = { opacity -> themeState.setNoteColorOpacity(opacity) },
                        onExport = backupActions.exportBackup,
                        onImport = backupActions.importBackup,
                        onOpenArchive = { navigation.openVault("archive") },
                        onOpenTrash = { navigation.openVault("trash") },
                        trashRetentionDays = trashRetentionDays,
                        onTrashRetentionDaysChange = { days ->
                            trashRetentionDays = days
                            trashRetentionStore.saveDays(days)
                        },
                        onBack = { navigation.closeSettings() }
                    )
                } else if (selected == null) {
                    NotesHome(
                        notes = activeNotes, search = search, searchVisible = searchVisible,
                        noteColors = displayedNoteColors,
                        themeColors = activeThemeColors,
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
                            closeActiveSessionsForRemoved(activeNotes.filter { it.id in ids })
                            if (homeTimelineMode) recordsViewModel.moveHomeRecordsToVault(ids, toTrash = false)
                            else recordsViewModel.moveActiveRecordsToVault(activeCollectionId, ids, toTrash = false)
                        },
                        onDeleteSelected = { ids ->
                            closeActiveSessionsForRemoved(activeNotes.filter { it.id in ids })
                            if (homeTimelineMode) recordsViewModel.moveHomeRecordsToVault(ids, toTrash = true)
                            else recordsViewModel.moveActiveRecordsToVault(activeCollectionId, ids, toTrash = true)
                        },
                        onMoveSelected = { ids, afterId ->
                            if (homeTimelineMode) recordsViewModel.reorderHomeRecords(ids, afterId)
                            else recordsViewModel.reorderActiveRecords(activeCollectionId, ids, afterId)
                        },
                        onTogglePinned = { ids ->
                            if (homeTimelineMode) recordsViewModel.toggleHomeRecordsPinned(ids)
                            else recordsViewModel.toggleActiveRecordsPinned(activeCollectionId, ids)
                        },
                        onChangeNoteColor = { noteId, colorIndex ->
                            if (homeTimelineMode) recordsViewModel.changeHomeRecordColor(noteId, colorIndex)
                            else recordsViewModel.changeActiveRecordColor(activeCollectionId, noteId, colorIndex)
                        },
                        screenTitle = navigation.activeModule.title,
                        onOpenModules = { navigation.openModules() },
                        timelineMode = homeTimelineMode,
                        onCreate = { requestedType ->
                            val type = navigation.activeModule.recordType ?: requestedType
                            val now = System.currentTimeMillis()
                            val id = newRecordId()
                            recordsViewModel.createActiveRecord(activeCollectionId, Note(id, "", listOf(NoteBlock()), now, created = now, type = type))
                            navigation.openNewRecord(id)
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
                            timeTrackingRepository.save(updated)
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
                            val updatedTimeState = syncWorkSessionsForTaskChanges(
                                oldRoots = listOf(previous),
                                newRoots = listOf(changed),
                                currentState = timeTrackingState,
                                nowMillis = now
                            )
                            if (updatedTimeState != timeTrackingState) {
                                timeTrackingRepository.save(updatedTimeState)
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
