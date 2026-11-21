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
class HomeAddRecordTypeStoreTest {
    private fun preferences() = RuntimeEnvironment.getApplication()
        .getSharedPreferences("home_add_record_type_test", Context.MODE_PRIVATE)
        .apply { edit().clear().commit() }

    @Test
    fun defaultsToNote() {
        assertEquals(RecordType.NOTE, HomeAddRecordTypeStore(preferences()).load())
    }

    @Test
    fun selectedRecordTypePersistsAfterStoreRecreation() {
        val prefs = preferences()
        RecordType.entries.forEach { type ->
            HomeAddRecordTypeStore(prefs).save(type)
            assertEquals(type, HomeAddRecordTypeStore(prefs).load())
        }
    }

    @Test
    fun unknownStoredValueFallsBackToNote() {
        val prefs = preferences()
        prefs.edit().putString("home_add_record_type", "UNKNOWN").commit()
        assertEquals(RecordType.NOTE, HomeAddRecordTypeStore(prefs).load())
    }

    @Test
    fun wrongStoredPreferenceTypeFallsBackToNote() {
        val prefs = preferences()
        prefs.edit().putInt("home_add_record_type", 42).commit()
        assertEquals(RecordType.NOTE, HomeAddRecordTypeStore(prefs).load())
    }
}
