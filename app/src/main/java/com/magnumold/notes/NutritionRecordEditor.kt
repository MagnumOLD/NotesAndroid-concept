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
internal fun NutritionRecordEditor(
    note: Note,
    topBarColor: Color,
    onBack: () -> Unit,
    onChange: (Note) -> Unit
) {
    @Composable
    fun NumericField(label: String, value: Double, update: (Double) -> Unit) {
        var input by remember(note.id, label) { mutableStateOf(if (value == 0.0) "" else value.toString()) }
        OutlinedTextField(
            value = input,
            onValueChange = { raw ->
                if (raw.length <= 10 && raw.all { it.isDigit() || it == '.' || it == ',' }) {
                    input = raw
                    update(raw.replace(',', '.').toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(label) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
    }

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
                    Text("КБЖУ", style = AppTypography.screenTitleStyle(), color = noteTextColor(topBarColor))
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
                label = { Text("Продукт или приём пищи") },
                placeholder = { Text("Например, овсянка") }
            )
            NumericField("Масса, г", note.nutritionGrams) { onChange(note.copy(nutritionGrams = it)) }
            NumericField("Калории, ккал", note.nutritionCalories) { onChange(note.copy(nutritionCalories = it)) }
            NumericField("Белки, г", note.nutritionProtein) { onChange(note.copy(nutritionProtein = it)) }
            NumericField("Жиры, г", note.nutritionFat) { onChange(note.copy(nutritionFat = it)) }
            NumericField("Углеводы, г", note.nutritionCarbs) { onChange(note.copy(nutritionCarbs = it)) }
            HorizontalDivider()
            Text(
                "КБЖУ этой записи",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "${NutritionOperations.formatCalories(note.nutritionCalories)} ккал  •  Б ${note.nutritionProtein} г  •  Ж ${note.nutritionFat} г  •  У ${note.nutritionCarbs} г",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
