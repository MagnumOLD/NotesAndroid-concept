package com.magnumold.notes

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppThemeStateTest {
    private fun state(
        mode: String = "system",
        colors: ThemeColorSets = ThemeColorSets()
    ) = AppThemeState(
        initialMode = mode,
        initialColorSets = colors,
        initialNoteColors = defaultNoteColors,
        initialNoteColorOpacities = NoteColorOpacities(light = 0.3f, dark = 0.7f, custom = 0.9f)
    )

    @Test
    fun systemModeFollowsTheDeviceAndUsesTheMatchingColorPalette() {
        val state = state()

        assertFalse(state.isDark(systemDark = false))
        assertEquals(ThemeColorSets().light, state.activeColors(systemDark = false))
        assertTrue(state.isDark(systemDark = true))
        assertEquals(ThemeColorSets().dark, state.activeColors(systemDark = true))
    }

    @Test
    fun systemModeUsesSeparateLightAndDarkNoteColorOpacity() {
        val state = state()

        assertEquals(0.3f, state.activeNoteColorOpacity(systemDark = false), 0f)
        assertEquals(0.7f, state.activeNoteColorOpacity(systemDark = true), 0f)
    }

    @Test
    fun systemPaletteEditsDoNotOverwriteTheOtherPalette() {
        val state = state()
        state.updateThemeColor("topBar", Color.Red, systemDark = true)

        assertEquals(Color.Red, state.activeColors(systemDark = true).topBar)
        assertEquals(ThemeColorSets().light.topBar, state.activeColors(systemDark = false).topBar)
    }

    @Test
    fun legacyCustomModeFallsBackToSystemAndCannotBeSelectedAgain() {
        val state = state(mode = "custom")

        assertEquals("system", state.mode)
        state.changeMode("custom")
        assertEquals("system", state.mode)
    }

    @Test
    fun systemModeCanBeSelectedAfterManualTheme() {
        val state = state(mode = "dark")
        state.changeMode("system")

        assertEquals("system", state.mode)
        assertTrue(state.isDark(systemDark = true))
        assertFalse(state.isDark(systemDark = false))
    }
}
