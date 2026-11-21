package com.magnumold.notes

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * Owns the mutable appearance state used by the root app composition.
 * Persistence remains in ThemeSettingsStore; this class contains only state
 * transitions and derived values for the active theme.
 */
@Stable
internal class AppThemeState(
    initialMode: String,
    initialColorSets: ThemeColorSets,
    initialNoteColors: List<Color>,
    initialNoteColorOpacities: NoteColorOpacities
) {
    var mode by mutableStateOf(initialMode.takeIf { it in listOf("system", "light", "dark") } ?: "system")
        private set

    var colorSets by mutableStateOf(initialColorSets)
        private set

    var noteColors by mutableStateOf(initialNoteColors)
        private set

    var noteColorOpacities by mutableStateOf(initialNoteColorOpacities)
        private set

    private fun activePaletteMode(systemDark: Boolean): String =
        if (mode == "system") {
            if (systemDark) "dark" else "light"
        } else if (mode == "dark") "dark" else "light"

    fun activeColors(systemDark: Boolean): CustomThemeColors =
        themeColorsForMode(colorSets, mode, systemDark)

    fun isDark(systemDark: Boolean): Boolean = when (mode) {
        "system" -> systemDark
        "dark" -> true
        else -> false
    }

    fun activeNoteColorOpacity(systemDark: Boolean): Float =
        when (activePaletteMode(systemDark)) {
            "light" -> noteColorOpacities.light
            "dark" -> noteColorOpacities.dark
            else -> noteColorOpacities.light
        }

    fun changeMode(value: String) {
        if (value in listOf("system", "light", "dark")) mode = value
    }

    fun updateThemeColor(key: String, color: Color, systemDark: Boolean) {
        colorSets = updateThemeColorSets(colorSets, activePaletteMode(systemDark), key, color)
    }

    fun resetThemeColors(systemDark: Boolean) {
        val paletteMode = activePaletteMode(systemDark)
        val defaults = if (paletteMode == "dark") ThemeColorSets().dark else ThemeColorSets().light
        colorSets = if (paletteMode == "dark") {
            colorSets.copy(dark = defaults)
        } else {
            colorSets.copy(light = defaults)
        }
    }

    fun replaceNoteColors(colors: List<Color>) {
        noteColors = colors
    }

    fun setNoteColorOpacity(opacity: Float, systemDark: Boolean) {
        val value = opacity.coerceIn(0f, 1f)
        noteColorOpacities = when (activePaletteMode(systemDark)) {
            "light" -> noteColorOpacities.copy(light = value)
            "dark" -> noteColorOpacities.copy(dark = value)
            else -> noteColorOpacities.copy(light = value)
        }
    }
}
