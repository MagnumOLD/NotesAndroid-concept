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
    var mode by mutableStateOf(initialMode)
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
        } else mode

    fun activeColors(systemDark: Boolean): CustomThemeColors =
        themeColorsForMode(colorSets, mode, systemDark)

    fun isDark(systemDark: Boolean): Boolean = when (mode) {
        "system" -> systemDark
        "dark" -> true
        "custom" -> noteTextColor(activeColors(systemDark).background) == Color.White
        else -> false
    }

    fun activeNoteColorOpacity(systemDark: Boolean): Float =
        when (activePaletteMode(systemDark)) {
            "light" -> noteColorOpacities.light
            "dark" -> noteColorOpacities.dark
            else -> noteColorOpacities.custom
        }

    fun changeMode(value: String) {
        if (value in listOf("system", "light", "dark", "custom")) mode = value
    }

    fun updateThemeColor(key: String, color: Color, systemDark: Boolean) {
        colorSets = updateThemeColorSets(colorSets, activePaletteMode(systemDark), key, color)
    }

    fun resetThemeColors(systemDark: Boolean) {
        val paletteMode = activePaletteMode(systemDark)
        val defaults = when (paletteMode) {
            "light" -> ThemeColorSets().light
            "dark" -> ThemeColorSets().dark
            else -> defaultCustomThemeColors
        }
        colorSets = when (paletteMode) {
            "light" -> colorSets.copy(light = defaults)
            "dark" -> colorSets.copy(dark = defaults)
            else -> colorSets.copy(custom = defaults)
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
            else -> noteColorOpacities.copy(custom = value)
        }
    }
}
