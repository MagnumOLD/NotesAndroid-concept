package com.magnumold.notes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp

@Composable
internal fun NutritionRecordEditor(
    note: Note,
    products: List<NutritionProduct>,
    topBarColor: Color,
    onBack: () -> Unit,
    onChange: (Note) -> Unit,
    onProductsChange: (List<NutritionProduct>) -> Unit
) {
    var pickerLineId by remember { mutableStateOf<String?>(null) }
    var pickerSearch by remember { mutableStateOf("") }
    var catalogVisible by remember { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<NutritionProduct?>(null) }
    var productFormVisible by remember { mutableStateOf(false) }
    val legacy = note.nutritionItems.isEmpty() && (
        note.nutritionGrams > 0 || note.nutritionCalories > 0 ||
        note.nutritionProtein > 0 || note.nutritionFat > 0 || note.nutritionCarbs > 0
    )

    AppPageScaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(color = topBarColor, contentColor = noteTextColor(topBarColor)) {
                Row(
                    Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)
                        .padding(horizontal = AppSpacing.screenHorizontal, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Назад")
                    }
                    Text(
                        "Приём пищи",
                        style = AppTypography.screenTitleStyle(),
                        color = noteTextColor(topBarColor),
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { catalogVisible = true }) {
                        Text("База продуктов", color = noteTextColor(topBarColor))
                    }
                }
            }
        }
    ) { padding ->
        if (legacy) {
            LegacyNutritionRecordEditor(note, padding, onChange)
        } else {
            Column(
                Modifier.fillMaxSize().padding(padding)
                    .padding(horizontal = AppSpacing.screenHorizontal, vertical = AppSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
            ) {
                Text("Продукты в приёме пищи", style = MaterialTheme.typography.titleMedium)
                if (note.nutritionItems.isEmpty()) {
                    Text(
                        "Добавь продукты из своей базы. Для каждого укажи массу в граммах — итог КБЖУ посчитается автоматически.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                note.nutritionItems.forEach { item ->
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = { pickerSearch = ""; pickerLineId = item.id },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(item.productName, style = MaterialTheme.typography.titleMedium)
                            }
                            IconButton(onClick = {
                                onChange(NutritionCatalogOperations.withMealItems(
                                    note, note.nutritionItems.filterNot { it.id == item.id }
                                ))
                            }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Удалить продукт из приёма пищи")
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            var grams by remember(item.id) {
                                mutableStateOf(DecimalInputOperations.format(item.grams))
                            }
                            OutlinedTextField(
                                value = grams,
                                onValueChange = { raw ->
                                    if (DecimalInputOperations.isValid(raw, maxLength = 10)) {
                                        grams = raw
                                        val amount = DecimalInputOperations.parse(raw).coerceAtLeast(0.0)
                                        onChange(NutritionCatalogOperations.withMealItems(
                                            note, note.nutritionItems.map {
                                                if (it.id == item.id) NutritionCatalogOperations.updateGrams(it, amount) else it
                                            }
                                        ))
                                    }
                                },
                                modifier = Modifier.widthIn(min = 110.dp, max = 145.dp),
                                singleLine = true,
                                label = { Text("Граммы") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                            )
                            Text(
                                NutritionOperations.formatCalories(item.calories) + " ккал · Б " +
                                    NutritionOperations.formatAmount(item.protein) + " · Ж " +
                                    NutritionOperations.formatAmount(item.fat) + " · У " +
                                    NutritionOperations.formatAmount(item.carbs),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
                OutlinedButton(
                    onClick = { pickerSearch = ""; pickerLineId = java.util.UUID.randomUUID().toString() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Добавить продукт")
                }
                HorizontalDivider()
                Text("Итого за приём пищи", style = MaterialTheme.typography.titleMedium)
                Text(
                    NutritionOperations.formatCalories(note.nutritionCalories) + " ккал",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    "Б " + NutritionOperations.formatAmount(note.nutritionProtein) + " г · Ж " +
                        NutritionOperations.formatAmount(note.nutritionFat) + " г · У " +
                        NutritionOperations.formatAmount(note.nutritionCarbs) + " г",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    pickerLineId?.let { lineId ->
        AlertDialog(
            onDismissRequest = { pickerLineId = null },
            title = { Text("Выбрать продукт") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        pickerSearch, { pickerSearch = it },
                        modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Поиск") }
                    )
                    val matches = NutritionCatalogOperations.search(products, pickerSearch)
                    if (matches.isEmpty()) Text("Подходящих продуктов нет")
                    Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                        matches.forEach { product ->
                            TextButton(
                                onClick = {
                                    val item = NutritionCatalogOperations.createMealItem(product, 100.0, lineId)
                                    val index = note.nutritionItems.indexOfFirst { it.id == lineId }
                                    val items = if (index < 0) note.nutritionItems + item
                                        else note.nutritionItems.toMutableList().also { it[index] = item }
                                    onChange(NutritionCatalogOperations.withMealItems(
                                        note.copy(titleText = note.titleText.ifBlank { "Приём пищи" }), items
                                    ))
                                    pickerLineId = null
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                                    Text(product.name)
                                    Text(
                                        NutritionOperations.formatCalories(product.caloriesPer100g) + " ккал / 100 г · Б " +
                                            NutritionOperations.formatAmount(product.proteinPer100g) + " · Ж " +
                                            NutritionOperations.formatAmount(product.fatPer100g) + " · У " +
                                            NutritionOperations.formatAmount(product.carbsPer100g),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    TextButton(onClick = {
                        pickerLineId = null
                        editingProduct = null
                        productFormVisible = true
                    }) { Text("Создать продукт в базе") }
                }
            },
            confirmButton = { TextButton(onClick = { pickerLineId = null }) { Text("Закрыть") } }
        )
    }

    if (catalogVisible) {
        AlertDialog(
            onDismissRequest = { catalogVisible = false },
            title = { Text("База продуктов") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (products.isEmpty()) Text("База пока пуста")
                    Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                        products.sortedBy { it.name.lowercase() }.forEach { product ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(product.name)
                                    Text(
                                        NutritionOperations.formatCalories(product.caloriesPer100g) + " ккал · Б " +
                                            NutritionOperations.formatAmount(product.proteinPer100g) + " · Ж " +
                                            NutritionOperations.formatAmount(product.fatPer100g) + " · У " +
                                            NutritionOperations.formatAmount(product.carbsPer100g) + " / 100 г",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { editingProduct = product; productFormVisible = true }) {
                                    Icon(Icons.Outlined.Edit, contentDescription = "Изменить")
                                }
                                IconButton(onClick = {
                                    onProductsChange(products.filterNot { it.id == product.id })
                                }) {
                                    Icon(Icons.Outlined.Delete, contentDescription = "Удалить")
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                    OutlinedButton(
                        onClick = { editingProduct = null; productFormVisible = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null)
                        Text("Новый продукт")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { catalogVisible = false }) { Text("Готово") } }
        )
    }

    if (productFormVisible) {
        NutritionProductFormDialog(
            product = editingProduct,
            onDismiss = { productFormVisible = false; editingProduct = null },
            onSave = { saved ->
                val updated = if (products.any { it.id == saved.id }) {
                    products.map { if (it.id == saved.id) saved else it }
                } else products + saved
                onProductsChange(updated.sortedBy { it.name.lowercase() })
                productFormVisible = false
                editingProduct = null
            }
        )
    }
}

@Composable
private fun NutritionProductFormDialog(
    product: NutritionProduct?,
    onDismiss: () -> Unit,
    onSave: (NutritionProduct) -> Unit
) {
    var name by remember(product?.id) { mutableStateOf(product?.name.orEmpty()) }
    var kcal by remember(product?.id) { mutableStateOf(product?.caloriesPer100g?.let(DecimalInputOperations::format).orEmpty()) }
    var protein by remember(product?.id) { mutableStateOf(product?.proteinPer100g?.let(DecimalInputOperations::format).orEmpty()) }
    var fat by remember(product?.id) { mutableStateOf(product?.fatPer100g?.let(DecimalInputOperations::format).orEmpty()) }
    var carbs by remember(product?.id) { mutableStateOf(product?.carbsPer100g?.let(DecimalInputOperations::format).orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (product == null) "Новый продукт" else "Изменить продукт") },
        text = {
            Column(
                Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(name, { name = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Название") })
                OutlinedTextField(kcal, { if (DecimalInputOperations.isValid(it, maxLength = 12)) kcal = it },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Калории / 100 г") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(protein, { if (DecimalInputOperations.isValid(it, maxLength = 12)) protein = it },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Белки / 100 г") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(fat, { if (DecimalInputOperations.isValid(it, maxLength = 12)) fat = it },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Жиры / 100 г") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(carbs, { if (DecimalInputOperations.isValid(it, maxLength = 12)) carbs = it },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Углеводы / 100 г") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Text("Пищевая ценность указывается на 100 граммов.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && listOf(kcal, protein, fat, carbs).all { it.isNotBlank() },
                onClick = {
                    onSave(NutritionProduct(
                        product?.id ?: newRecordId(), name.trim(),
                        DecimalInputOperations.parse(kcal).coerceAtLeast(0.0),
                        DecimalInputOperations.parse(protein).coerceAtLeast(0.0),
                        DecimalInputOperations.parse(fat).coerceAtLeast(0.0),
                        DecimalInputOperations.parse(carbs).coerceAtLeast(0.0)
                    ))
                }
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun LegacyNutritionRecordEditor(note: Note, padding: PaddingValues, onChange: (Note) -> Unit) {
    @Composable
    fun NumericField(label: String, value: Double, update: (Double) -> Unit) {
        var input by remember(note.id, label) { mutableStateOf(DecimalInputOperations.format(value)) }
        OutlinedTextField(
            input,
            { raw ->
                if (DecimalInputOperations.isValid(raw, maxLength = 10)) {
                    input = raw
                    update(NutritionOperations.normalizeAmount(DecimalInputOperations.parse(raw)))
                }
            },
            modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text(label) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
    }
    Column(
        Modifier.fillMaxSize().padding(padding)
            .padding(horizontal = AppSpacing.screenHorizontal, vertical = AppSpacing.lg)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
    ) {
        Text("Старая запись питания. Её данные сохранены; новые приёмы пищи создаются через базу продуктов.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(note.titleText, { onChange(note.copy(titleText = it)) },
            modifier = Modifier.fillMaxWidth(), label = { Text("Название") })
        NumericField("Масса, г", note.nutritionGrams) { onChange(note.copy(nutritionGrams = it)) }
        NumericField("Калории, ккал", note.nutritionCalories) { onChange(note.copy(nutritionCalories = it)) }
        NumericField("Белки, г", note.nutritionProtein) { onChange(note.copy(nutritionProtein = it)) }
        NumericField("Жиры, г", note.nutritionFat) { onChange(note.copy(nutritionFat = it)) }
        NumericField("Углеводы, г", note.nutritionCarbs) { onChange(note.copy(nutritionCarbs = it)) }
    }
}
