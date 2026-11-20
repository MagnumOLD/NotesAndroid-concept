package com.magnumold.notes

internal object NutritionOperations {
    /** Invalid or negative imported values must never corrupt daily totals. */
    fun normalizeAmount(value: Double): Double =
        if (value.isFinite()) value.coerceAtLeast(0.0) else 0.0

    /** Calories are displayed as whole kcal; use Long to avoid wrapping above Int.MAX_VALUE. */
    fun formatCalories(value: Double): String =
        if (value.isFinite()) value.toLong().toString() else "—"
}
