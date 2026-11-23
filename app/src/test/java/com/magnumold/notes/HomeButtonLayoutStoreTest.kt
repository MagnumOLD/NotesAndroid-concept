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
class HomeButtonLayoutStoreTest {
    private fun preferences() = RuntimeEnvironment.getApplication()
        .getSharedPreferences("home_button_layout_test", Context.MODE_PRIVATE)
        .apply { edit().clear().commit() }

    @Test
    fun defaultsToVerticalPanelLayout() {
        assertEquals(HomeButtonLayout.VERTICAL_PANEL, HomeButtonLayoutStore(preferences()).load())
    }

    @Test
    fun selectedLayoutPersistsAfterStoreRecreation() {
        val prefs = preferences()
        HomeButtonLayoutStore(prefs).save(HomeButtonLayout.VERTICAL)
        assertEquals(HomeButtonLayout.VERTICAL, HomeButtonLayoutStore(prefs).load())
        HomeButtonLayoutStore(prefs).save(HomeButtonLayout.VERTICAL_PANEL)
        assertEquals(HomeButtonLayout.VERTICAL_PANEL, HomeButtonLayoutStore(prefs).load())
    }

    @Test
    fun formerHorizontalLayoutsMigrateToSupportedVerticalChoices() {
        val prefs = preferences()
        prefs.edit().putString("home_button_layout", "SHARED_PANEL").commit()
        assertEquals(HomeButtonLayout.VERTICAL_PANEL, HomeButtonLayoutStore(prefs).load())
        prefs.edit().putString("home_button_layout", "SIDE_BY_SIDE").commit()
        assertEquals(HomeButtonLayout.SEPARATE, HomeButtonLayoutStore(prefs).load())
    }

    @Test
    fun unknownStoredValueFallsBackToCurrentLayout() {
        val prefs = preferences()
        prefs.edit().putString("home_button_layout", "UNKNOWN").commit()
        assertEquals(HomeButtonLayout.VERTICAL_PANEL, HomeButtonLayoutStore(prefs).load())
    }
}
