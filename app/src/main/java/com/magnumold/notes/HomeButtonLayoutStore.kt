package com.magnumold.notes

import android.content.SharedPreferences

internal enum class HomeButtonLayout {
    SEPARATE,
    VERTICAL,
    VERTICAL_PANEL
}

internal class HomeButtonLayoutStore(private val preferences: SharedPreferences) {
    fun load(): HomeButtonLayout {
        val stored = preferences.getString(KEY_LAYOUT, null) ?: return HomeButtonLayout.VERTICAL_PANEL
        return when (stored) {
            // Migrate the former horizontal shared panel to the vertical panel.
            "SHARED_PANEL" -> HomeButtonLayout.VERTICAL_PANEL
            // The old horizontal pair is intentionally retired.
            "SIDE_BY_SIDE" -> HomeButtonLayout.SEPARATE
            else -> runCatching { HomeButtonLayout.valueOf(stored) }
                .getOrDefault(HomeButtonLayout.VERTICAL_PANEL)
        }
    }

    fun save(layout: HomeButtonLayout) {
        preferences.edit().putString(KEY_LAYOUT, layout.name).apply()
    }

    private companion object {
        const val KEY_LAYOUT = "home_button_layout"
    }
}
