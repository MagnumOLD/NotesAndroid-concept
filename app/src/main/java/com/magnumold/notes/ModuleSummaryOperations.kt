package com.magnumold.notes

internal object ModuleSummaryOperations {
    fun summary(title: String, records: List<Note>, todayDay: Long, nowMillis: Long = System.currentTimeMillis()): String? {
        val today = records.filter { timelineDayKey(it) == todayDay }
        return when (title) {
            "Задачи" -> {
                val entries = today.filter { it.type == RecordType.TASK }
                if (entries.isEmpty()) "На сегодня задач нет"
                else {
                    val completed = entries.count { it.recordStatus(nowMillis) == RecordStatus.COMPLETED }
                    val missed = entries.count { it.recordStatus(nowMillis) == RecordStatus.MISSED }
                    "Задач: ${entries.size} · Выполнено: $completed · Просрочено: $missed"
                }
            }
            "КБЖУ" -> {
                val entries = today.filter { it.type == RecordType.NUTRITION }
                if (entries.isEmpty()) "Сегодня пока нет записей о питании"
                else {
                    val calories = NutritionOperations.sumAmounts(entries.map { it.nutritionCalories })
                    val protein = NutritionOperations.sumAmounts(entries.map { it.nutritionProtein })
                    val fat = NutritionOperations.sumAmounts(entries.map { it.nutritionFat })
                    val carbs = NutritionOperations.sumAmounts(entries.map { it.nutritionCarbs })
                    "Сегодня: ${NutritionOperations.formatCalories(calories)} ккал · Б ${NutritionOperations.formatAmount(protein)} г · Ж ${NutritionOperations.formatAmount(fat)} г · У ${NutritionOperations.formatAmount(carbs)} г"
                }
            }
            "Покупки" -> {
                val entries = today.filter { it.type == RecordType.PURCHASE }
                if (entries.isEmpty()) "На сегодня покупок нет"
                else {
                    val bought = entries.count { it.status == RecordStatus.COMPLETED }
                    val planned = PurchaseOperations.sumTotalCost(
                        entries.filter { it.status != RecordStatus.COMPLETED }
                    )
                    "В списке: ${entries.size - bought} · Куплено: $bought · Остаток: ${PurchaseOperations.formatAmount(planned)}"
                }
            }
            "Тренировки" -> {
                val entries = today.filter { it.type == RecordType.WORKOUT }
                if (entries.isEmpty()) "На сегодня тренировок нет"
                else {
                    val volume = WorkoutOperations.sumVolume(entries)
                    "Упражнений: ${entries.size} · Общий объём: ${WorkoutOperations.formatVolume(volume)} кг"
                }
            }
            "Таймеры" -> {
                val entries = today.filter { it.type == RecordType.TIMER }
                if (entries.isEmpty()) "Таймеров на сегодня нет"
                else {
                    val running = entries.count { it.status == RecordStatus.IN_PROGRESS && (it.timerEndsAtMillis ?: 0L) > nowMillis }
                    "Таймеров: ${entries.size} · Активно: $running"
                }
            }
            else -> null
        }
    }
}
