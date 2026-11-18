package com.magnumold.notes

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutOperationsTest {
    @Test
    fun setAndRepCountsAreClamped() {
        assertEquals(1, WorkoutOperations.normalizeSets(0))
        assertEquals(100, WorkoutOperations.normalizeSets(500))
        assertEquals(1, WorkoutOperations.normalizeReps(-2))
        assertEquals(1000, WorkoutOperations.normalizeReps(2000))
    }

    @Test
    fun weightRejectsInvalidAndCapsExtremeValues() {
        assertEquals(0.0, WorkoutOperations.normalizeWeight(Double.NaN), 0.0)
        assertEquals(1000.0, WorkoutOperations.normalizeWeight(2000.0), 0.0)
    }

    @Test
    fun totalVolumeMultipliesSetsRepsAndWeight() {
        assertEquals(600.0, WorkoutOperations.totalVolume(4, 10, 15.0), 0.0)
    }
}
