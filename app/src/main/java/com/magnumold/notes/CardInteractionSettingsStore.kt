package com.magnumold.notes

import android.content.SharedPreferences

internal class CardInteractionSettingsStore(private val preferences: SharedPreferences) {
    fun load(): Boolean = preferences.getBoolean(KEY_ENABLED, false)
    fun save(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    private companion object {
        const val KEY_ENABLED = "interactive_cards_enabled"
    }
}
