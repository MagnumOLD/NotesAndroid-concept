package com.magnumold.notes

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CardPreviewSettingsStoreTest {
    private fun store(): CardPreviewSettingsStore {
        val preferences = RuntimeEnvironment.getApplication()
            .getSharedPreferences("card_preview_test", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        return CardPreviewSettingsStore(preferences)
    }

    @Test
    fun defaultPreviewsAreEnabledForDataModules() {
        assertEquals(
            setOf(AppModule.TASKS, AppModule.TIMERS, AppModule.NUTRITION, AppModule.WORKOUTS, AppModule.PURCHASES),
            store().load()
        )
    }

    @Test
    fun selectedPreviewModulesPersist() {
        val store = store()
        val selected = setOf(AppModule.TASKS, AppModule.NUTRITION)
        store.save(selected)

        assertEquals(selected, store.load())
    }

    @Test
    fun invalidAndNonDataModulesAreIgnored() {
        val preferences = RuntimeEnvironment.getApplication()
            .getSharedPreferences("card_preview_invalid_test", Context.MODE_PRIVATE)
        preferences.edit().putStringSet("card_preview_modules", setOf("ALL", "NOT_A_MODULE", "PURCHASES")).commit()

        assertEquals(setOf(AppModule.PURCHASES), CardPreviewSettingsStore(preferences).load())
    }
}
