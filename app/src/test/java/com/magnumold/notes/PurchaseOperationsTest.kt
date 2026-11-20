package com.magnumold.notes

import org.junit.Assert.assertEquals
import org.junit.Test
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
}
