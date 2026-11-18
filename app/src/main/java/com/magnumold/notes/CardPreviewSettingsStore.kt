package com.magnumold.notes

import android.content.SharedPreferences

internal class CardPreviewSettingsStore(private val preferences: SharedPreferences) {
    fun load(): Set<AppModule> {
        val stored = preferences.getStringSet(KEY_MODULES, null) ?: return defaultModules
        return stored.mapNotNull { value ->
            runCatching { AppModule.valueOf(value) }.getOrNull()
        }.filter { it.recordType != null }.toSet()
    }

    fun save(modules: Set<AppModule>) {
        preferences.edit()
            .putStringSet(KEY_MODULES, modules.filter { it.recordType != null }.map { it.name }.toSet())
            .apply()
    }

    private companion object {
        const val KEY_MODULES = "card_preview_modules"
        val defaultModules = AppModule.entries.filter { it.recordType != null && it != AppModule.NOTES }.toSet()
    }
}
