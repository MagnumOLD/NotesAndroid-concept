package com.magnumold.notes

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

internal object NutritionOperations {
    /** Invalid or negative imported values must never corrupt daily totals. */
    fun normalizeAmount(value: Double): Double =
        if (value.isFinite()) value.coerceAtLeast(0.0) else 0.0

    fun decimalAmount(value: Double): BigDecimal =
        BigDecimal.valueOf(normalizeAmount(value))

    fun sumAmounts(values: Iterable<Double>): BigDecimal =
        values.fold(BigDecimal.ZERO) { total, value -> total.add(decimalAmount(value)) }

    fun formatAmount(value: BigDecimal, locale: Locale = Locale.getDefault()): String {
        val rounded = value.max(BigDecimal.ZERO)
            .setScale(1, RoundingMode.HALF_UP)
            .stripTrailingZeros()
        return NumberFormat.getNumberInstance(locale).apply {
            isGroupingUsed = false
            minimumFractionDigits = 0
            maximumFractionDigits = 1
        }.format(rounded)
    }

    /** Calories are displayed as whole kcal; use Long to avoid wrapping above Int.MAX_VALUE. */
    fun formatCalories(value: Double): String =
        if (value.isFinite()) value.toLong().toString() else "—"
}
