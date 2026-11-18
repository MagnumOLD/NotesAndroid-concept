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
    private fun store(name: String = "card_preview_test"): CardPreviewSettingsStore {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences(name, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        return CardPreviewSettingsStore(prefs)
    }
    @Test fun defaultsEnableAllPreviewFields() {
        assertEquals(CardPreviewField.defaults, store().load())
    }
    @Test fun individualFieldsPersist() {
        val selected = setOf(CardPreviewField.NUTRITION_CALORIES.key, CardPreviewField.NUTRITION_PROTEIN.key, CardPreviewField.TASK_STATUS.key)
        val store = store()
        store.save(selected)
        assertEquals(selected, store.load())
    }
    @Test fun unknownKeysAreIgnored() {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("card_preview_invalid_test", Context.MODE_PRIVATE)
        prefs.edit().putStringSet("card_preview_fields", setOf("invalid", CardPreviewField.PURCHASE_STATUS.key)).commit()
        assertEquals(setOf(CardPreviewField.PURCHASE_STATUS.key), CardPreviewSettingsStore(prefs).load())
    }
}
