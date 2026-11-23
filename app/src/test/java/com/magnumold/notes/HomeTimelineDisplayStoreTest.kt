package com.magnumold.notes

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeTimelineDisplayStoreTest {
    private fun store(name: String = "home_timeline_display_test"): HomeTimelineDisplayStore {
        val preferences = RuntimeEnvironment.getApplication().getSharedPreferences(name, Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        return HomeTimelineDisplayStore(preferences)
    }

    @Test
    fun dateHeadersAreEnabledByDefault() {
        assertTrue(store().loadShowDateHeaders())
    }

    @Test
    fun dateHeaderTextSizeDefaultsToTwelveAndPersistsTwelve() {
        val settings = store()
        assertEquals(12, settings.loadDateHeaderTextSize())

        settings.saveDateHeaderTextSize(12)
        val reloaded = HomeTimelineDisplayStore(
            RuntimeEnvironment.getApplication().getSharedPreferences("home_timeline_display_test", Context.MODE_PRIVATE)
        )
        assertEquals(12, reloaded.loadDateHeaderTextSize())
    }

    @Test
    fun legacyUnmarkedFourteenSpDefaultMigratesToTwelve() {
        val preferences = RuntimeEnvironment.getApplication()
            .getSharedPreferences("home_timeline_display_legacy_test", Context.MODE_PRIVATE)
        preferences.edit().clear().putInt("date_header_text_size", 14).commit()

        assertEquals(12, HomeTimelineDisplayStore(preferences).loadDateHeaderTextSize())
        assertEquals(12, preferences.getInt("date_header_text_size", -1))
    }

    @Test
    fun explicitlySelectedFourteenSpPersistsAcrossReload() {
        val preferences = RuntimeEnvironment.getApplication()
            .getSharedPreferences("home_timeline_display_explicit_test", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        HomeTimelineDisplayStore(preferences).saveDateHeaderTextSize(14)

        assertEquals(14, HomeTimelineDisplayStore(preferences).loadDateHeaderTextSize())
    }

    @Test
    fun unsupportedDateHeaderTextSizeFallsBackToTwelve() {
        val settings = store()
        settings.saveDateHeaderTextSize(18)
        assertEquals(12, settings.loadDateHeaderTextSize())
    }

    @Test
    fun headerVisibilityAndManualOrderPersistIndependently() {
        val settings = store()
        settings.saveShowDateHeaders(false)
        settings.saveManualOrder(listOf(8L, 3L, 5L))

        val reloaded = HomeTimelineDisplayStore(
            RuntimeEnvironment.getApplication().getSharedPreferences("home_timeline_display_test", Context.MODE_PRIVATE)
        )
        assertEquals(false, reloaded.loadShowDateHeaders())
        assertEquals(listOf(8L, 3L, 5L), reloaded.loadManualOrder())
    }

    @Test
    fun neverInitializedManualOrderIsDifferentFromAnEmptySavedOrder() {
        val settings = store()
        assertNull(settings.loadManualOrder())
        settings.saveManualOrder(emptyList())
        assertEquals(emptyList<Long>(), settings.loadManualOrder())
    }

    @Test
    fun duplicateManualIdsAreRemovedWhenSaving() {
        val settings = store()
        settings.saveManualOrder(listOf(1L, 2L, 1L))
        assertEquals(listOf(1L, 2L), settings.loadManualOrder())
    }
}
