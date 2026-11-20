package com.magnumold.notes

import android.content.SharedPreferences

internal enum class HomeButtonLayout {
    SEPARATE,
    SIDE_BY_SIDE,
    SHARED_PANEL
}

internal class HomeButtonLayoutStore(private val preferences: SharedPreferences) {
    fun load(): HomeButtonLayout {
        val stored = preferences.getString(KEY_LAYOUT, null) ?: return HomeButtonLayout.SEPARATE
        return runCatching { HomeButtonLayout.valueOf(stored) }.getOrDefault(HomeButtonLayout.SEPARATE)
    }

    fun save(layout: HomeButtonLayout) {
        preferences.edit().putString(KEY_LAYOUT, layout.name).apply()
    }

    private companion object {
        const val KEY_LAYOUT = "home_button_layout"
    }
}
