package com.magnumold.notes

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

internal data class KitchenTimerSnapshot(
    val timers: List<KitchenTimer>,
    val settings: KitchenTimerSettings
)

internal class KitchenTimerRepository(private val preferences: SharedPreferences) {
    @Synchronized
    fun load(): KitchenTimerSnapshot {
        val raw = preferences.getString(KEY_TIMERS, null)
        val timers = if (raw == null) {
            listOf(KitchenTimerOperations.create(nextId())).also(::saveTimers)
        } else {
            runCatching { parseTimers(JSONArray(raw)) }.getOrElse {
                preferences.preserveCorruptJson(KEY_TIMERS, raw, "[]")
                emptyList()
            }
        }
        val settings = runCatching {
            KitchenTimerSettings(
                alertDurationMode = preferences.getString(KEY_ALERT_MODE, null)
                    ?.let { TimerAlertDurationMode.valueOf(it) }
                    ?: TimerAlertDurationMode.THIRTY_SECONDS,
                customAlertDurationSeconds = preferences.getInt(KEY_CUSTOM_ALERT_SECONDS, 30),
                defaultSoundUri = preferences.getString(KEY_DEFAULT_SOUND_URI, null)
            ).normalized()
        }.getOrDefault(KitchenTimerSettings())
        return KitchenTimerSnapshot(timers, settings)
    }

    @Synchronized
    fun saveTimers(timers: List<KitchenTimer>) {
        require(timers.size <= KitchenTimerOperations.MAX_TIMERS)
        val safe = timers.map { it.normalized() }.distinctBy { it.id }
        val json = JSONArray()
        safe.forEach { timer ->
            json.put(JSONObject().apply {
                put("id", timer.id)
                put("title", timer.title)
                put("duration", timer.durationSeconds)
                put("remaining", timer.remainingSeconds ?: JSONObject.NULL)
                put("endsAt", timer.endsAtMillis ?: JSONObject.NULL)
                put("soundUri", timer.soundUri ?: JSONObject.NULL)
                put("status", timer.status.name)
            })
        }
        check(preferences.edit().putString(KEY_TIMERS, json.toString()).commit()) {
            "Не удалось сохранить таймеры"
        }
    }

    @Synchronized
    fun saveSettings(settings: KitchenTimerSettings) {
        val safe = settings.normalized()
        check(preferences.edit()
            .putString(KEY_ALERT_MODE, safe.alertDurationMode.name)
            .putInt(KEY_CUSTOM_ALERT_SECONDS, safe.customAlertDurationSeconds)
            .putString(KEY_DEFAULT_SOUND_URI, safe.defaultSoundUri)
            .commit()) { "Не удалось сохранить настройки таймеров" }
    }

    private fun parseTimers(array: JSONArray): List<KitchenTimer> =
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            runCatching {
                KitchenTimer(
                    id = item.getLong("id"),
                    title = item.optString("title"),
                    durationSeconds = item.optLong("duration", TimerOperations.DEFAULT_DURATION_SECONDS),
                    remainingSeconds = if (item.isNull("remaining")) null else item.optLong("remaining"),
                    endsAtMillis = if (item.isNull("endsAt")) null else item.optLong("endsAt"),
                    soundUri = if (item.isNull("soundUri")) null else item.optString("soundUri"),
                    status = runCatching {
                        KitchenTimerStatus.valueOf(item.optString("status", KitchenTimerStatus.READY.name))
                    }.getOrDefault(KitchenTimerStatus.READY)
                ).normalized()
            }.getOrNull()
        }.distinctBy { it.id }.take(KitchenTimerOperations.MAX_TIMERS)

    private fun nextId(): Long {
        val current = preferences.getLong(KEY_NEXT_ID, System.currentTimeMillis().coerceAtLeast(1L))
        preferences.edit().putLong(KEY_NEXT_ID, current + 1L).apply()
        return current
    }

    private companion object {
        const val KEY_TIMERS = "kitchen_timers_v1"
        const val KEY_ALERT_MODE = "kitchen_timer_alert_mode"
        const val KEY_CUSTOM_ALERT_SECONDS = "kitchen_timer_custom_alert_seconds"
        const val KEY_NEXT_ID = "kitchen_timer_next_id"
        const val KEY_DEFAULT_SOUND_URI = "kitchen_timer_default_sound_uri"
    }
}
