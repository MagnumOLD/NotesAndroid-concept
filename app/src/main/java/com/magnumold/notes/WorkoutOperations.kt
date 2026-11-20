package com.magnumold.notes

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

internal object WorkoutOperations {
    fun normalizeSets(value: Int): Int = value.coerceIn(1, 100)
    fun normalizeReps(value: Int): Int = value.coerceIn(1, 1_000)
    fun normalizeWeight(value: Double): Double =
        if (value.isFinite()) value.coerceIn(0.0, 1_000.0) else 0.0

    fun totalVolumeDecimal(sets: Int, reps: Int, weightKg: Double): BigDecimal =
        BigDecimal.valueOf(normalizeSets(sets).toLong() * normalizeReps(reps).toLong())
            .multiply(BigDecimal.valueOf(normalizeWeight(weightKg)))

    fun totalVolume(sets: Int, reps: Int, weightKg: Double): Double =
        totalVolumeDecimal(sets, reps, weightKg).toDouble()

    fun sumVolume(records: Iterable<Note>): BigDecimal =
        records.fold(BigDecimal.ZERO) { total, note ->
            total.add(totalVolumeDecimal(note.workoutSets, note.workoutReps, note.workoutWeightKg))
        }

    fun formatVolume(value: BigDecimal, locale: Locale = Locale.getDefault()): String {
        val rounded = value.max(BigDecimal.ZERO).setScale(1, RoundingMode.HALF_UP)
        return NumberFormat.getNumberInstance(locale).apply {
            isGroupingUsed = false
            minimumFractionDigits = 1
            maximumFractionDigits = 1
        }.format(rounded)
    }
}
