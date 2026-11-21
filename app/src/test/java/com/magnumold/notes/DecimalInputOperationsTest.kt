package com.magnumold.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class DecimalInputOperationsTest {
    @Test
    fun acceptsDigitsAndSingleLocalizedDecimalSeparator() {
        assertTrue(DecimalInputOperations.isValid("125", 10))
        assertTrue(DecimalInputOperations.isValid("12.5", 10))
        assertTrue(DecimalInputOperations.isValid("12,5", 10))
        assertTrue(DecimalInputOperations.isValid(".5", 10))
        assertTrue(DecimalInputOperations.isValid("1,", 10))
        assertTrue(DecimalInputOperations.isValid("", 10))
    }

    @Test
    fun rejectsMultipleSeparatorsSignsAndUnexpectedCharacters() {
        assertFalse(DecimalInputOperations.isValid("1.2,3", 10))
        assertFalse(DecimalInputOperations.isValid("1,,2", 10))
        assertFalse(DecimalInputOperations.isValid("-1", 10))
        assertFalse(DecimalInputOperations.isValid("1e3", 10))
        assertFalse(DecimalInputOperations.isValid("12x", 10))
        assertFalse(DecimalInputOperations.isValid("123456", 5))
    }

    @Test
    fun parsesDotAndCommaAndKeepsTrailingSeparatorEditable() {
        assertEquals(12.5, DecimalInputOperations.parse("12.5"), 0.0)
        assertEquals(12.5, DecimalInputOperations.parse("12,5"), 0.0)
        assertEquals(12.0, DecimalInputOperations.parse("12,"), 0.0)
        assertEquals(0.5, DecimalInputOperations.parse(",5"), 0.0)
        assertEquals(0.0, DecimalInputOperations.parse("."), 0.0)
        assertEquals(0.0, DecimalInputOperations.parse(""), 0.0)
    }

    @Test
    fun formattingUsesLocaleSeparatorWithoutGroupingOrUnwantedZeros() {
        assertEquals("12.5", DecimalInputOperations.format(12.5, Locale.US))
        assertEquals("12,5", DecimalInputOperations.format(12.5, Locale.GERMANY))
        assertEquals("12", DecimalInputOperations.format(12.0, Locale.US))
        assertEquals("", DecimalInputOperations.format(0.0, Locale.US))
        assertEquals("", DecimalInputOperations.format(Double.NaN, Locale.US))
    }
}
