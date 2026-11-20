package com.magnumold.notes

import org.junit.Assert.assertEquals
import org.junit.Test

class NutritionOperationsTest {
    @Test
    fun calorieSummaryDoesNotOverflowAtLargeValidValues() {
        assertEquals("9999999999", NutritionOperations.formatCalories(9_999_999_999.0))
    }

    @Test
    fun invalidAndNegativeAmountsAreNormalizedToZero() {
        assertEquals(0.0, NutritionOperations.normalizeAmount(Double.NaN), 0.0)
        assertEquals(0.0, NutritionOperations.normalizeAmount(Double.POSITIVE_INFINITY), 0.0)
        assertEquals(0.0, NutritionOperations.normalizeAmount(-12.5), 0.0)
    }

    @Test
    fun validFractionalAmountsArePreserved() {
        assertEquals(12.75, NutritionOperations.normalizeAmount(12.75), 0.0)
    }
}
