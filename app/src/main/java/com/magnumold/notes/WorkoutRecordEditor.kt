package com.magnumold.notes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
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
    @Composable
    fun IntegerField(label: String, value: Int, update: (Int) -> Unit) {
        var input by remember(note.id, label) { mutableStateOf(value.toString()) }
        OutlinedTextField(
            value = input,
            onValueChange = { raw ->
                if (raw.length <= 4 && raw.all(Char::isDigit)) {
                    input = raw
                    raw.toIntOrNull()?.let(update)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(label) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
    }
    var weightInput by remember(note.id) {
        mutableStateOf(if (note.workoutWeightKg == 0.0) "" else note.workoutWeightKg.toString())
    }
    val volume = WorkoutOperations.totalVolume(note.workoutSets, note.workoutReps, note.workoutWeightKg)
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
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Назад")
                    }
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
            IntegerField("Подходы", note.workoutSets) {
                onChange(note.copy(workoutSets = WorkoutOperations.normalizeSets(it)))
            }
            IntegerField("Повторения", note.workoutReps) {
                onChange(note.copy(workoutReps = WorkoutOperations.normalizeReps(it)))
            }
            OutlinedTextField(
                value = weightInput,
                onValueChange = { raw ->
                    if (raw.length <= 10 && raw.all { it.isDigit() || it == '.' || it == ',' }) {
                        weightInput = raw
                        onChange(note.copy(workoutWeightKg = WorkoutOperations.normalizeWeight(
                            raw.replace(',', '.').toDoubleOrNull() ?: 0.0
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
            Text("%.1f кг".format(java.util.Locale.getDefault(), volume), style = MaterialTheme.typography.headlineSmall)
            Text(
                "${note.workoutSets} подхода × ${note.workoutReps} повторений",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
