package com.magnumold.notes

import android.content.SharedPreferences

internal enum class HomeButtonLayout {
    SEPARATE,
    VERTICAL,
    VERTICAL_PANEL
}

internal enum class HomeButtonSize(val scale: Float) {
    SMALL(0.82f),
    NORMAL(1.0f),
    LARGE(1.18f)
}

internal class HomeButtonLayoutStore(private val preferences: SharedPreferences) {
    fun load(): HomeButtonLayout {
        val stored = preferences.getString(KEY_LAYOUT, null) ?: return HomeButtonLayout.SEPARATE
        return when (stored) {
            // Migrate the former horizontal shared panel to the vertical panel.
            "SHARED_PANEL" -> HomeButtonLayout.VERTICAL_PANEL
            // The old horizontal pair is intentionally retired.
            "SIDE_BY_SIDE" -> HomeButtonLayout.SEPARATE
            else -> runCatching { HomeButtonLayout.valueOf(stored) }
                .getOrDefault(HomeButtonLayout.SEPARATE)
        }
    }

    fun save(layout: HomeButtonLayout) {
        preferences.edit().putString(KEY_LAYOUT, layout.name).apply()
    }

    fun loadSize(): HomeButtonSize {
        val stored = preferences.getString(KEY_SIZE, null) ?: return HomeButtonSize.NORMAL
        return runCatching { HomeButtonSize.valueOf(stored) }
            .getOrDefault(HomeButtonSize.NORMAL)
    }

    fun saveSize(size: HomeButtonSize) {
        preferences.edit().putString(KEY_SIZE, size.name).apply()
    }

    private companion object {
        const val KEY_LAYOUT = "home_button_layout"
        const val KEY_SIZE = "home_button_size"
    }
}
