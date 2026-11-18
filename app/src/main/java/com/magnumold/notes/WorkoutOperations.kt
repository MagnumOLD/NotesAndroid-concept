package com.magnumold.notes

internal object WorkoutOperations {
    fun normalizeSets(value: Int): Int = value.coerceIn(1, 100)
    fun normalizeReps(value: Int): Int = value.coerceIn(1, 1_000)
    fun normalizeWeight(value: Double): Double =
        if (value.isFinite()) value.coerceIn(0.0, 1_000.0) else 0.0

    fun totalVolume(sets: Int, reps: Int, weightKg: Double): Double =
        normalizeSets(sets) * normalizeReps(reps) * normalizeWeight(weightKg)
}
