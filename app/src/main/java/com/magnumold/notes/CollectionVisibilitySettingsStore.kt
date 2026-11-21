package com.magnumold.notes

import android.content.SharedPreferences

internal class CollectionVisibilitySettingsStore(private val preferences: SharedPreferences) {
    fun load(): Set<AppModule> {
        val stored = preferences.getStringSet(KEY_VISIBLE, null) ?: return DEFAULT_VISIBLE
        return stored.mapNotNull { name -> runCatching { AppModule.valueOf(name) }.getOrNull() }
            .filter { it != AppModule.ALL }.toSet()
    }

    fun save(visibleModules: Set<AppModule>) {
        preferences.edit()
            .putStringSet(KEY_VISIBLE, visibleModules.filter { it != AppModule.ALL }.map { it.name }.toSet())
            .apply()
    }

    fun loadHomeVisible(): Boolean = preferences.getBoolean(KEY_HOME_VISIBLE, DEFAULT_HOME_VISIBLE)

    fun saveHomeVisible(visible: Boolean) {
        preferences.edit().putBoolean(KEY_HOME_VISIBLE, visible).apply()
    }

    private companion object {
        const val KEY_VISIBLE = "collection_visible_modules"
        const val KEY_HOME_VISIBLE = "collection_home_visible"
        const val DEFAULT_HOME_VISIBLE = false
        val DEFAULT_VISIBLE = AppModule.entries.filter { it != AppModule.ALL }.toSet()
    }
}
