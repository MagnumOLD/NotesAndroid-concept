package com.magnumold.notes

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.util.Locale

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

    @Test
    fun volumeCalculationUsesExactDecimalWeight() {
        assertEquals(
            BigDecimal("0.9"),
            WorkoutOperations.totalVolumeDecimal(3, 1, 0.3)
        )
    }

    @Test
    fun dailyWorkoutVolumesAreSummedExactly() {
        val entries = listOf(
            Note(1, "", emptyList(), 1L, type = RecordType.WORKOUT, workoutSets = 3, workoutReps = 1, workoutWeightKg = 0.1),
            Note(2, "", emptyList(), 1L, type = RecordType.WORKOUT, workoutSets = 3, workoutReps = 1, workoutWeightKg = 0.2)
        )
        assertEquals(BigDecimal("0.9"), WorkoutOperations.sumVolume(entries))
        assertEquals("0.9", WorkoutOperations.formatVolume(WorkoutOperations.sumVolume(entries), Locale.US))
        assertEquals("0,9", WorkoutOperations.formatVolume(WorkoutOperations.sumVolume(entries), Locale.GERMANY))
    }

    @Test
    fun invalidAndNegativeWeightsContributeZeroVolume() {
        assertEquals(
            BigDecimal("0.0"),
            WorkoutOperations.totalVolumeDecimal(3, 10, Double.NaN).setScale(1)
        )
        assertEquals(
            BigDecimal("0.0"),
            WorkoutOperations.totalVolumeDecimal(3, 10, -2.0).setScale(1)
        )
    }
}
