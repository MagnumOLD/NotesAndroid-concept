package com.magnumold.notes

import java.math.BigDecimal
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Shared non-negative decimal input rules for nutrition, purchases and workout
 * measurements. A trailing separator remains editable without being persisted
 * as an invalid floating-point value.
 */
internal object DecimalInputOperations {
    fun isValid(raw: String, maxLength: Int): Boolean {
        if (raw.length > maxLength) return false
        if (raw.any { !it.isDigit() && it != '.' && it != ',' }) return false
        return raw.count { it == '.' || it == ',' } <= 1
    }

    fun parse(raw: String): Double {
        if (raw.isBlank() || raw == "." || raw == ",") return 0.0
        val normalized = raw.replace(',', '.').removeSuffix(".")
            .let { if (it.startsWith(".")) "0$it" else it }
        return normalized.toDoubleOrNull()
            ?.takeIf { it.isFinite() && it >= 0.0 }
            ?: 0.0
    }

    fun format(value: Double, locale: Locale = Locale.getDefault()): String {
        if (!value.isFinite() || value <= 0.0) return ""
        val separator = DecimalFormatSymbols.getInstance(locale).decimalSeparator
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
            .replace('.', separator)
    }
}
