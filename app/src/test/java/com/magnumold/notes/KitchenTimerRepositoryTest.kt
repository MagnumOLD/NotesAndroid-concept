package com.magnumold.notes

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KitchenTimerRepositoryTest {
    @Test fun firstLoadCreatesAndPersistsOneDefaultTimer() {
        val preferences = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("kitchen-timers-test-default", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        val repository = KitchenTimerRepository(preferences)

        val first = repository.load()
        val second = repository.load()

        assertEquals(1, first.timers.size)
        assertEquals(first.timers, second.timers)
    }

    @Test fun timersAndSettingsSurviveRepositoryRecreation() {
        val preferences = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("kitchen-timers-test-persist", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        val repository = KitchenTimerRepository(preferences)
        val timers = listOf(
            KitchenTimer(id = 101L, title = "Гречка", durationSeconds = 1200L),
            KitchenTimer(id = 102L, title = "Рыба", durationSeconds = 720L, status = KitchenTimerStatus.PAUSED, remainingSeconds = 400L)
        )
        val settings = KitchenTimerSettings(
            alertDurationMode = TimerAlertDurationMode.CUSTOM,
            customAlertDurationSeconds = 90,
            defaultSoundUri = "content://settings/system/alarm_alert"
        )
        repository.saveTimers(timers)
        repository.saveSettings(settings)

        val restored = KitchenTimerRepository(preferences).load()

        assertEquals(timers, restored.timers)
        assertEquals(settings, restored.settings)
        assertNotNull(restored.timers.firstOrNull { it.title == "Рыба" })
    }
}
