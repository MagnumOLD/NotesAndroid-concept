package com.magnumold.notes

import java.math.BigDecimal
import java.util.Locale

internal object PurchaseOperations {
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
}
