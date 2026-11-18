package com.magnumold.notes

import java.util.Locale

internal object ModuleSummaryOperations {
    fun summary(title: String, records: List<Note>, todayDay: Long): String? {
        val today = records.filter { timelineDayKey(it) == todayDay }
        fun number(value: Double): String =
            if (value % 1.0 == 0.0) value.toLong().toString()
            else "%.1f".format(Locale.getDefault(), value)

        return when (title) {
            "Задачи" -> {
                val entries = today.filter { it.type == RecordType.TASK }
                if (entries.isEmpty()) "На сегодня задач нет"
                else {
                    val completed = entries.count { it.recordStatus() == RecordStatus.COMPLETED }
                    val missed = entries.count { it.recordStatus() == RecordStatus.MISSED }
                    "Задач: ${entries.size} · Выполнено: $completed · Просрочено: $missed"
                }
            }
            "КБЖУ" -> {
                val entries = today.filter { it.type == RecordType.NUTRITION }
                if (entries.isEmpty()) "Сегодня пока нет записей о питании"
                else "Сегодня: ${number(entries.sumOf { it.nutritionCalories })} ккал · Б ${number(entries.sumOf { it.nutritionProtein })} г · Ж ${number(entries.sumOf { it.nutritionFat })} г · У ${number(entries.sumOf { it.nutritionCarbs })} г"
            }
            "Покупки" -> {
                val entries = today.filter { it.type == RecordType.PURCHASE }
                if (entries.isEmpty()) "На сегодня покупок нет"
                else {
                    val bought = entries.count { it.status == RecordStatus.COMPLETED }
                    val planned = entries.filter { it.status != RecordStatus.COMPLETED }
                        .sumOf { it.purchaseQuantity * it.purchaseUnitPrice }
                    "В списке: ${entries.size - bought} · Куплено: $bought · Остаток: ${number(planned)}"
                }
            }
            "Тренировки" -> {
                val entries = today.filter { it.type == RecordType.WORKOUT }
                if (entries.isEmpty()) "На сегодня тренировок нет"
                else {
                    val volume = entries.sumOf {
                        WorkoutOperations.totalVolume(it.workoutSets, it.workoutReps, it.workoutWeightKg)
                    }
                    "Упражнений: ${entries.size} · Общий объём: ${number(volume)} кг"
                }
            }
            "Таймеры" -> {
                val entries = today.filter { it.type == RecordType.TIMER }
                if (entries.isEmpty()) "Таймеров на сегодня нет"
                else {
                    val running = entries.count { it.timerEndsAtMillis != null }
                    "Таймеров: ${entries.size} · Активно: $running"
                }
            }
            else -> null
        }
    }
}
