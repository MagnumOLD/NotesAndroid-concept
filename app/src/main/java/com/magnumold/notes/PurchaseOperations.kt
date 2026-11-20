package com.magnumold.notes

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

internal object PurchaseOperations {
    fun normalizeAmount(value: Double): Double =
        if (value.isFinite()) value.coerceAtLeast(0.0) else 0.0

    /**
     * Keeps the existing one-decimal preview for fractional amounts while
     * preserving large integral totals that do not fit in a signed Long.
     */
    fun formatAmount(value: Double, locale: Locale = Locale.getDefault()): String {
        if (!value.isFinite()) return "—"
        val decimal = BigDecimal.valueOf(value).stripTrailingZeros()
        return if (decimal.scale() <= 0) decimal.toPlainString()
        else "%.1f".format(locale, value)
    }

    /**
     * Formats the editor total with exact decimal multiplication. Imported
     * finite values can still overflow when multiplied as Doubles; keep that
     * result out of the UI instead of rendering the literal "Infinity".
     */
    fun formatTotalCost(
        quantity: Double,
        unitPrice: Double,
        locale: Locale = Locale.getDefault()
    ): String {
        val total = BigDecimal.valueOf(normalizeAmount(quantity))
            .multiply(BigDecimal.valueOf(normalizeAmount(unitPrice)))
            .setScale(2, RoundingMode.HALF_UP)
        if (total > BigDecimal.valueOf(Double.MAX_VALUE)) return "—"
        return NumberFormat.getNumberInstance(locale).apply {
            isGroupingUsed = false
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }.format(total)
    }
}
