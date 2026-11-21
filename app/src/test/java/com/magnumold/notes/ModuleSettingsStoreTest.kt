package com.magnumold.notes

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ModuleSettingsStoreTest {
    private fun store(name: String): Pair<ModuleSettingsStore, android.content.SharedPreferences> {
        val preferences = RuntimeEnvironment.getApplication()
            .getSharedPreferences(name, Context.MODE_PRIVATE)
            .also { it.edit().clear().commit() }
        return ModuleSettingsStore(preferences) to preferences
    }

    @Test
    fun customEnabledModulesAndOrderSurviveRestart() {
        val (first, preferences) = store("module_settings_round_trip")
        val configuration = ModuleConfiguration(
            enabled = setOf(AppModule.PURCHASES, AppModule.TASKS),
            order = listOf(AppModule.PURCHASES, AppModule.WORKOUTS, AppModule.TASKS)
        ).normalized()
        first.save(configuration)
        assertEquals(configuration, ModuleSettingsStore(preferences).load())
    }

    @Test
    fun unknownPersistedModuleNamesFallBackToSafeDefaults() {
        val (moduleStore, preferences) = store("module_settings_unknown_names")
        preferences.edit()
            .putStringSet("concept_modules_enabled", setOf("FUTURE_UNKNOWN_MODULE"))
            .putString("concept_modules_order", "FUTURE_UNKNOWN_MODULE")
            .commit()
        val loaded = moduleStore.load()
        assertEquals(ModuleConfiguration().enabled, loaded.enabled)
        assertEquals(ModuleConfiguration().order, loaded.order)
    }

    @Test
    fun intentionallyEmptyEnabledSetIsNotReplacedWithDefaults() {
        val (moduleStore, _) = store("module_settings_empty_enabled")
        moduleStore.save(ModuleConfiguration(enabled = emptySet()))
        assertTrue(moduleStore.load().enabled.isEmpty())
        assertEquals(6, moduleStore.load().order.size)
    }

    @Test
    fun preferencesWithWrongStoredTypesRecoverToDefaults() {
        val (moduleStore, preferences) = store("module_settings_wrong_types")
        preferences.edit()
            .putString("concept_modules_enabled", "not-a-set")
            .putStringSet("concept_modules_order", setOf("not-a-string"))
            .commit()
        val loaded = moduleStore.load()
        assertEquals(ModuleConfiguration().enabled, loaded.enabled)
        assertEquals(ModuleConfiguration().order, loaded.order)
    }
}
