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
internal fun PurchaseRecordEditor(
    note: Note,
    topBarColor: Color,
    onBack: () -> Unit,
    onChange: (Note) -> Unit
) {
    @Composable
    fun AmountField(label: String, value: Double, update: (Double) -> Unit) {
        var input by remember(note.id, label) {
            mutableStateOf(DecimalInputOperations.format(value))
        }
        OutlinedTextField(
            value = input,
            onValueChange = { raw ->
                if (DecimalInputOperations.isValid(raw, maxLength = 12)) {
                    input = raw
                    update(PurchaseOperations.normalizeAmount(DecimalInputOperations.parse(raw)))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(label) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
    }
    val purchased = note.status == RecordStatus.COMPLETED
    val total = PurchaseOperations.formatTotalCost(note.purchaseQuantity, note.purchaseUnitPrice)
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
                    Text("Покупки", style = AppTypography.screenTitleStyle(), color = noteTextColor(topBarColor))
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
                label = { Text("Что купить") },
                placeholder = { Text("Название товара") }
            )
            AmountField("Количество", note.purchaseQuantity) { onChange(note.copy(purchaseQuantity = it)) }
            AmountField("Цена за единицу", note.purchaseUnitPrice) { onChange(note.copy(purchaseUnitPrice = it)) }
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Итого", style = MaterialTheme.typography.titleMedium)
                    Text(total, style = MaterialTheme.typography.headlineSmall)
                }
                Text("Куплено", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = purchased,
                    onCheckedChange = { checked ->
                        onChange(note.copy(status = if (checked) RecordStatus.COMPLETED else RecordStatus.WAITING))
                    }
                )
            }
        }
    }
}
