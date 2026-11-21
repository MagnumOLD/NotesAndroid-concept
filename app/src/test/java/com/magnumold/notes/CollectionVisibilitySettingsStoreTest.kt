package com.magnumold.notes

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CollectionVisibilitySettingsStoreTest {
    private fun store(name: String = "collection_visibility_test"): CollectionVisibilitySettingsStore {
        val preferences = RuntimeEnvironment.getApplication().getSharedPreferences(name, Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        return CollectionVisibilitySettingsStore(preferences)
    }

    @Test
    fun allModulesShowCollectionsByDefault() {
        assertEquals(ModuleConfiguration().enabled, store().load())
    }

    @Test
    fun visibilitySelectionPersistsAndExcludesTheAllModulesPseudoModule() {
        val store = store()
        val selected = setOf(AppModule.NOTES, AppModule.NUTRITION, AppModule.PURCHASES)
        store.save(selected + AppModule.ALL)
        assertEquals(selected, store.load())
    }

    @Test
    fun homeCollectionsAreHiddenByDefaultForNewInstallations() {
        assertFalse(store().loadHomeVisible())
    }

    @Test
    fun homeVisibilityPersistsIndependentlyFromModuleVisibility() {
        val store = store()
        val selectedModules = setOf(AppModule.NOTES, AppModule.NUTRITION)
        store.save(selectedModules)
        store.saveHomeVisible(false)

        val reloadedStore = CollectionVisibilitySettingsStore(
            RuntimeEnvironment.getApplication()
                .getSharedPreferences("collection_visibility_test", Context.MODE_PRIVATE)
        )
        assertFalse(reloadedStore.loadHomeVisible())
        assertEquals(selectedModules, reloadedStore.load())
    }

    @Test
    fun homeVisibilityCanBeEnabledAgain() {
        val store = store()
        store.saveHomeVisible(false)
        store.saveHomeVisible(true)
        assertTrue(store.loadHomeVisible())
    }

    @Test
    fun unknownModuleNamesAreIgnored() {
        val preferences = RuntimeEnvironment.getApplication()
            .getSharedPreferences("collection_visibility_invalid_test", Context.MODE_PRIVATE)
        preferences.edit().putStringSet("collection_visible_modules", setOf("UNKNOWN", "NOTES")).commit()
        assertEquals(setOf(AppModule.NOTES), CollectionVisibilitySettingsStore(preferences).load())
    }
}
