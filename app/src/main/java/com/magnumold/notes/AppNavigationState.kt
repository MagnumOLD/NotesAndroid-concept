package com.magnumold.notes

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Owns transient navigation and view state for the app's top-level screens.
 * It deliberately contains no persistence or UI logic.
 */
@Stable
internal class AppNavigationState {
    var activeModule by mutableStateOf(AppModule.ALL)
        private set
    var modulesVisible by mutableStateOf(false)
        private set
    var returnToModulesOnBack by mutableStateOf(false)
        private set

    val canReturnToModules: Boolean
        get() = returnToModulesOnBack && activeModule != AppModule.ALL && !modulesVisible
    var moduleManagementVisible by mutableStateOf(false)
        private set
    var activeCollectionId by mutableStateOf<Long?>(null)
        private set
    var selectedId by mutableStateOf<Long?>(null)
        private set
    var autoFocusNewNote by mutableStateOf(false)
        private set
    var search by mutableStateOf("")
        private set
    var searchVisible by mutableStateOf(false)
        private set
    var settingsVisible by mutableStateOf(false)
        private set
    var vaultScreen by mutableStateOf<String?>(null)
        private set
    fun openModules() { modulesVisible = true }

    fun closeModules() {
        modulesVisible = false
        moduleManagementVisible = false
        returnToModulesOnBack = false
    }

    fun openModuleManagement() { moduleManagementVisible = true }

    fun closeModuleManagement() { moduleManagementVisible = false }

    fun returnToHomeKeepingModuleScreens() {
        activeModule = AppModule.ALL
        activeCollectionId = null
        selectedId = null
        autoFocusNewNote = false
        search = ""
        searchVisible = false
    }

    fun selectModule(module: AppModule) {
        returnToModulesOnBack = module != AppModule.ALL && modulesVisible
        activeModule = module
        activeCollectionId = null
        selectedId = null
        modulesVisible = false
        moduleManagementVisible = false
        search = ""
        searchVisible = false
    }

    fun returnFromModuleToModules() {
        if (!canReturnToModules) return
        activeModule = AppModule.ALL
        activeCollectionId = null
        selectedId = null
        autoFocusNewNote = false
        search = ""
        searchVisible = false
        modulesVisible = true
        moduleManagementVisible = false
        returnToModulesOnBack = false
    }

    fun selectCollection(id: Long?) {
        activeCollectionId = id
        selectedId = null
    }

    fun openRecord(id: Long) {
        autoFocusNewNote = false
        selectedId = id
    }

    fun openNewRecord(id: Long) {
        autoFocusNewNote = true
        selectedId = id
    }

    fun closeEditor() {
        selectedId = null
        autoFocusNewNote = false
    }

    fun updateSearch(query: String) {
        search = query
    }

    fun toggleSearch() {
        searchVisible = !searchVisible
        if (!searchVisible) search = ""
    }

    fun openSettings() {
        settingsVisible = true
    }

    fun closeSettings() {
        settingsVisible = false
    }

    fun openVault(screen: String) {
        require(screen == "archive" || screen == "trash")
        vaultScreen = screen
    }

    fun closeVault() {
        vaultScreen = null
    }
}
