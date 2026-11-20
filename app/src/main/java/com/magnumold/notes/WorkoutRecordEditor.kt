package com.magnumold.notes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
internal fun WorkoutRecordEditor(
    note: Note,
    topBarColor: Color,
    onBack: () -> Unit,
    onChange: (Note) -> Unit
) {
    BackHandler { onBack() }
    @Composable
    fun IntegerField(label: String, value: Int, minimum: Int, maximum: Int, update: (Int) -> Unit) {
        var input by remember(note.id, label) { mutableStateOf(value.toString()) }
        OutlinedTextField(
            value = input,
            onValueChange = { raw ->
                if (raw.length <= 4 && raw.all(Char::isDigit)) {
                    raw.toIntOrNull()?.let { parsed ->
                        val normalized = parsed.coerceIn(minimum, maximum)
                        input = normalized.toString()
                        update(normalized)
                    } ?: run { input = raw }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(label) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
    }
    var weightInput by remember(note.id) {
        mutableStateOf(DecimalInputOperations.format(note.workoutWeightKg))
    }
    val volume = WorkoutOperations.totalVolumeDecimal(note.workoutSets, note.workoutReps, note.workoutWeightKg)
    AppPageScaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(color = topBarColor, contentColor = noteTextColor(topBarColor)) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(horizontal = AppSpacing.screenHorizontal, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Тренировка", style = AppTypography.screenTitleStyle(), color = noteTextColor(topBarColor))
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
                .padding(horizontal = AppSpacing.screenHorizontal, vertical = AppSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
            OutlinedTextField(
                value = note.titleText,
                onValueChange = { onChange(note.copy(titleText = it)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Упражнение") },
                placeholder = { Text("Например, подтягивания") }
            )
            IntegerField("Подходы", note.workoutSets, 1, 100) {
                onChange(note.copy(workoutSets = it))
            }
            IntegerField("Повторения", note.workoutReps, 1, 1_000) {
                onChange(note.copy(workoutReps = it))
            }
            OutlinedTextField(
                value = weightInput,
                onValueChange = { raw ->
                    if (DecimalInputOperations.isValid(raw, maxLength = 10)) {
                        weightInput = raw
                        onChange(note.copy(workoutWeightKg = WorkoutOperations.normalizeWeight(
                            DecimalInputOperations.parse(raw)
                        )))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Вес, кг (если нужен)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            OutlinedTextField(
                value = note.descriptionText,
                onValueChange = { onChange(note.copy(descriptionText = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Заметка к упражнению") }
            )
            HorizontalDivider()
            Text("Общий объём", style = MaterialTheme.typography.titleMedium)
            Text("${WorkoutOperations.formatVolume(volume)} кг", style = MaterialTheme.typography.headlineSmall)
            Text(
                "${note.workoutSets} подхода × ${note.workoutReps} повторений",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
