package com.magnumold.notes

internal object NutritionOperations {
    /** Calories are displayed as whole kcal; use Long to avoid wrapping above Int.MAX_VALUE. */
    fun formatCalories(value: Double): String =
        if (value.isFinite()) value.toLong().toString() else "—"
}
