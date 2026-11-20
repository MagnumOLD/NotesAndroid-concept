package com.magnumold.notes

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

internal object PurchaseOperations {
    fun normalizeAmount(value: Double): Double =
        if (value.isFinite()) value.coerceAtLeast(0.0) else 0.0

    private fun decimalAmount(value: Double): BigDecimal =
        BigDecimal.valueOf(normalizeAmount(value))

    fun totalCost(quantity: Double, unitPrice: Double): BigDecimal =
        decimalAmount(quantity).multiply(decimalAmount(unitPrice))

    fun sumTotalCost(records: Iterable<Note>): BigDecimal =
        records.fold(BigDecimal.ZERO) { sum, record ->
            sum.add(totalCost(record.purchaseQuantity, record.purchaseUnitPrice))
        }

    /**
     * Keeps the existing one-decimal preview for fractional amounts while
     * preserving large integral totals that do not fit in a signed Long.
     */
    fun formatAmount(value: Double, locale: Locale = Locale.getDefault()): String {
        if (!value.isFinite()) return "—"
        return formatAmount(BigDecimal.valueOf(value), locale)
    }

    /** Formats an exact aggregate without introducing Double rounding errors. */
    fun formatAmount(value: BigDecimal, locale: Locale = Locale.getDefault()): String {
        val normalized = value.max(BigDecimal.ZERO).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros()
        return NumberFormat.getNumberInstance(locale).apply {
            isGroupingUsed = false
            minimumFractionDigits = 0
            maximumFractionDigits = 1
        }.format(normalized)
    }

    /**
     * Formats the editor total with exact decimal multiplication. Imported
     * finite values can still overflow the Double range; keep that result out
     * of the UI instead of rendering a misleading infinite value.
     */
    fun formatTotalCost(
        quantity: Double,
        unitPrice: Double,
        locale: Locale = Locale.getDefault()
    ): String {
        val total = totalCost(quantity, unitPrice).setScale(2, RoundingMode.HALF_UP)
        if (total > BigDecimal.valueOf(Double.MAX_VALUE)) return "—"
        return NumberFormat.getNumberInstance(locale).apply {
            isGroupingUsed = false
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }.format(total)
    }
}
