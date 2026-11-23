package com.magnumold.notes

import android.content.Context
import androidx.compose.ui.graphics.Color
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ThemeSettingsStoreTest {
    @Test
    fun unchangedLegacyDateColorsMigrateButCustomizedColorsArePreserved() {
        val preferences = RuntimeEnvironment.getApplication()
            .getSharedPreferences("theme_settings_date_migration_test", Context.MODE_PRIVATE)
        val light = JSONObject()
            .put("background", "#FFF5F5F5")
            .put("text", "#FFFF0000")
        val dark = JSONObject()
            .put("background", "#FF191919")
            .put("text", "#FF00FF00")
        preferences.edit().clear()
            .putString("timeline_date_colors_v1", JSONObject()
                .put("light", light)
                .put("dark", dark)
                .toString())
            .commit()

        val migrated = ThemeSettingsStore(preferences).loadTimelineDateColorSets()

        assertEquals(Color.White, migrated.light.background)
        assertEquals(Color.Red, migrated.light.text)
        assertEquals(Color.Black, migrated.dark.background)
        assertEquals(Color.Green, migrated.dark.text)
    }
}
