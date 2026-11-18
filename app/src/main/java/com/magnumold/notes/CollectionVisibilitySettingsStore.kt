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

    private companion object {
        const val KEY_VISIBLE = "collection_visible_modules"
        val DEFAULT_VISIBLE = AppModule.entries.filter { it != AppModule.ALL }.toSet()
    }
}
