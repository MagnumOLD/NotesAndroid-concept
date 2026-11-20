package com.magnumold.notes

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.util.Locale

class PurchaseOperationsTest {
    @Test
    fun largeIntegralPurchaseTotalsDoNotClampToLongMaxValue() {
        assertEquals(
            "1000000000000000000000000",
            PurchaseOperations.formatAmount(1.0e24, Locale.US)
        )
    }

    @Test
    fun fractionalPurchaseAmountsKeepOneDecimalPlace() {
        assertEquals("12.6", PurchaseOperations.formatAmount(12.56, Locale.US))
        assertEquals("12", PurchaseOperations.formatAmount(12.0, Locale.US))
    }

    @Test
    fun nonFinitePurchaseAmountsUsePlaceholder() {
        assertEquals("—", PurchaseOperations.formatAmount(Double.NaN, Locale.US))
        assertEquals("—", PurchaseOperations.formatAmount(Double.POSITIVE_INFINITY, Locale.US))
    }

    @Test
    fun invalidAndNegativePurchaseAmountsAreNormalizedToZero() {
        assertEquals(0.0, PurchaseOperations.normalizeAmount(Double.NaN), 0.0)
        assertEquals(0.0, PurchaseOperations.normalizeAmount(Double.NEGATIVE_INFINITY), 0.0)
        assertEquals(0.0, PurchaseOperations.normalizeAmount(-4.5), 0.0)
        assertEquals(2.75, PurchaseOperations.normalizeAmount(2.75), 0.0)
    }

    @Test
    fun purchaseTotalUsesExactDecimalMultiplicationAndTwoFractionDigits() {
        assertEquals("31.25", PurchaseOperations.formatTotalCost(2.5, 12.5, Locale.US))
        assertEquals("31,25", PurchaseOperations.formatTotalCost(2.5, 12.5, Locale.GERMANY))
    }

    @Test
    fun purchaseTotalDoesNotRenderInfinityWhenImportedValuesOverflowDoubleRange() {
        assertEquals("—", PurchaseOperations.formatTotalCost(Double.MAX_VALUE, 2.0, Locale.US))
    }

    @Test
    fun invalidPurchaseTotalInputsAreSafelyNormalized() {
        assertEquals("0.00", PurchaseOperations.formatTotalCost(Double.NaN, -4.0, Locale.US))
    }

    @Test
    fun purchaseTotalsAreSummedAsExactDecimalsRatherThanBinaryFloatingPoint() {
        val records = listOf(
            Note(1, "", emptyList(), 1L, type = RecordType.PURCHASE, purchaseQuantity = 3.0, purchaseUnitPrice = 0.1),
            Note(2, "", emptyList(), 1L, type = RecordType.PURCHASE, purchaseQuantity = 3.0, purchaseUnitPrice = 0.2)
        )
        assertEquals(0, PurchaseOperations.sumTotalCost(records).compareTo(BigDecimal("0.9")))
        assertEquals("0.9", PurchaseOperations.formatAmount(PurchaseOperations.sumTotalCost(records), Locale.US))
    }

    @Test
    fun exactPurchaseSummaryFormattingSupportsHugeAggregates() {
        val total = BigDecimal("123456789012345678901234567890.45")
        assertEquals(
            "123456789012345678901234567890.5",
            PurchaseOperations.formatAmount(total, Locale.US)
        )
    }
}
