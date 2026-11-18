package com.magnumold.notes

import android.content.SharedPreferences

internal enum class CardPreviewField(val module: AppModule, val key: String, val title: String) {
    TASK_STATUS(AppModule.TASKS, "task_status", "Статус"),
    TASK_PROGRESS(AppModule.TASKS, "task_progress", "Прогресс подзадач"),
    TASK_DATE(AppModule.TASKS, "task_date", "Дата или срок"),
    TIMER_REMAINING(AppModule.TIMERS, "timer_remaining", "Оставшееся время"),
    TIMER_STATUS(AppModule.TIMERS, "timer_status", "Состояние"),
    TIMER_DURATION(AppModule.TIMERS, "timer_duration", "Исходная длительность"),
    NUTRITION_CALORIES(AppModule.NUTRITION, "nutrition_calories", "Калории"),
    NUTRITION_PROTEIN(AppModule.NUTRITION, "nutrition_protein", "Белки"),
    NUTRITION_FAT(AppModule.NUTRITION, "nutrition_fat", "Жиры"),
    NUTRITION_CARBS(AppModule.NUTRITION, "nutrition_carbs", "Углеводы"),
    NUTRITION_GRAMS(AppModule.NUTRITION, "nutrition_grams", "Масса"),
    PURCHASE_QUANTITY(AppModule.PURCHASES, "purchase_quantity", "Количество"),
    PURCHASE_PRICE(AppModule.PURCHASES, "purchase_price", "Цена и сумма"),
    PURCHASE_STATUS(AppModule.PURCHASES, "purchase_status", "Статус покупки"),
    WORKOUT_SETS(AppModule.WORKOUTS, "workout_sets", "Подходы и повторы"),
    WORKOUT_WEIGHT(AppModule.WORKOUTS, "workout_weight", "Вес"),
    WORKOUT_STATUS(AppModule.WORKOUTS, "workout_status", "Статус упражнения");

    companion object {
        fun forModule(module: AppModule): List<CardPreviewField> = entries.filter { it.module == module }
        val defaults: Set<String> = entries.map { it.key }.toSet()
    }
}

internal class CardPreviewSettingsStore(private val preferences: SharedPreferences) {
    fun load(): Set<String> {
        val stored = preferences.getStringSet(KEY_FIELDS, null) ?: return CardPreviewField.defaults
        return stored.intersect(CardPreviewField.defaults)
    }
    fun save(fields: Set<String>) {
        preferences.edit().putStringSet(KEY_FIELDS, fields.intersect(CardPreviewField.defaults)).apply()
    }
    private companion object { const val KEY_FIELDS = "card_preview_fields" }
}
