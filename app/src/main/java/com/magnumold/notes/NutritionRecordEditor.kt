package com.magnumold.notes

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class NutritionDraftLine(
    val id: String = java.util.UUID.randomUUID().toString(),
    val query: String = "",
    val grams: String = "100",
    val selectedProductId: Long? = null
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NutritionRecordEditor(
    note: Note,
    products: List<NutritionProduct>,
    topBarColor: Color,
    addButtonColor: Color,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onChange: (Note) -> Unit,
    onProductsChange: (List<NutritionProduct>) -> Unit
) {
    val legacyRecord = note.nutritionItems.isEmpty() && (
        note.nutritionGrams > 0 || note.nutritionCalories > 0 ||
        note.nutritionProtein > 0 || note.nutritionFat > 0 || note.nutritionCarbs > 0
    )
    var catalogVisible by remember(note.id) { mutableStateOf(false) }
    var catalogSearch by remember(note.id) { mutableStateOf("") }
    var addingProducts by remember(note.id) { mutableStateOf(note.nutritionItems.isEmpty() && !legacyRecord) }
    var draftLines by remember(note.id) { mutableStateOf(listOf(NutritionDraftLine())) }
    var focusDraftId by remember(note.id) { mutableStateOf(draftLines.firstOrNull()?.id) }
    val keyboardController = LocalSoftwareKeyboardController.current
    var editingProduct by remember(note.id) { mutableStateOf<NutritionProduct?>(null) }
    var productFormVisible by remember(note.id) { mutableStateOf(false) }
    var pendingDraftId by remember(note.id) { mutableStateOf<String?>(null) }
    var returnToCatalog by remember(note.id) { mutableStateOf(false) }
    var activeDragId by remember(note.id) { mutableStateOf<String?>(null) }
    var dragPointerY by remember(note.id) { mutableFloatStateOf(0f) }
    var dragGrabOffsetPx by remember(note.id) { mutableFloatStateOf(0f) }
    var dragReleaseRequested by remember(note.id) { mutableStateOf(false) }
    var reorderSession by remember(note.id) { mutableStateOf<ReorderSession?>(null) }
    val listState = remember(note.id) { LazyListState() }
    val density = LocalDensity.current
    val orderedItems = remember(note.nutritionItems, reorderSession?.keys) {
        val byId = note.nutritionItems.associateBy { it.id }
        reorderSession?.keys?.mapNotNull(byId::get) ?: note.nutritionItems
    }
    fun finishDrag() {
        val session = reorderSession
        if (session != null) {
            val byId = note.nutritionItems.associateBy { it.id }
            val reordered = session.keys.mapNotNull(byId::get)
            if (reordered.size == note.nutritionItems.size) {
                onChange(NutritionCatalogOperations.withMealItems(note, reordered))
            }
        }
        activeDragId = null
        reorderSession = null
        dragPointerY = 0f
        dragGrabOffsetPx = 0f
        dragReleaseRequested = false
    }
    AdjacentReorderEffect(
        isDragging = activeDragId != null,
        orderedKeys = orderedItems.map { it.id },
        reorderSession = reorderSession,
        pointerY = dragPointerY,
        grabOffsetPx = dragGrabOffsetPx,
        releaseRequested = dragReleaseRequested,
        listState = listState,
        density = density,
        onSessionChange = { reorderSession = it },
        onFinishDrag = { finishDrag() }
    )
    BackHandler(enabled = addingProducts || catalogVisible) {
        if (catalogVisible) catalogVisible = false else onBack()
    }

    AppPageScaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(color = topBarColor, contentColor = noteTextColor(topBarColor)) {
                Row(
                    Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)
                        .height(AppPageLayout.toolbarHeight)
                        .padding(start = AppSpacing.screenHorizontal, end = AppSpacing.toolbarControlInset),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        when { catalogVisible -> "База продуктов"; addingProducts -> "Добавить продукты"; else -> "КБЖУ" },
                        style = AppTypography.screenTitleStyle(),
                        color = noteTextColor(topBarColor),
                        modifier = Modifier.weight(1f)
                    )
                    if (!catalogVisible && !addingProducts) {
                        TextButton(onClick = { catalogSearch = ""; catalogVisible = true }) {
                            Text("База продуктов", color = noteTextColor(topBarColor))
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if ((!legacyRecord || catalogVisible || addingProducts) && !legacyRecord) {
                Box(
                    Modifier.offset(y = PrimaryAddButtonDimensions.verticalOffset)
                        .size(PrimaryAddButtonDimensions.diameter)
                        .shadow(6.dp, CircleShape).clip(CircleShape)
                        .background(addButtonColor)
                        .clickable {
                            if (catalogVisible) {
                                editingProduct = null
                                returnToCatalog = true
                                productFormVisible = true
                            } else if (addingProducts) {
                                val line = NutritionDraftLine()
                                draftLines = draftLines + line
                                focusDraftId = line.id
                            } else {
                                val line = NutritionDraftLine()
                                draftLines = listOf(line)
                                addingProducts = true
                                focusDraftId = line.id
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("+", fontSize = 34.sp, fontWeight = FontWeight.Light, color = noteTextColor(addButtonColor))
                }
            }
        }
    ) { padding ->
        when {
            catalogVisible -> {
                Column(Modifier.fillMaxSize().padding(padding)) {
                    OutlinedTextField(
                        value = catalogSearch,
                        onValueChange = { catalogSearch = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.screenHorizontal, vertical = AppSpacing.sm),
                        singleLine = true,
                        label = { Text("Поиск продуктов") }
                    )
                    val filtered = products.filter { it.name.contains(catalogSearch.trim(), ignoreCase = true) }
                        .sortedBy { it.name.lowercase() }
                    if (filtered.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(if (products.isEmpty()) "База пока пуста. Нажми +, чтобы добавить продукт." else "Ничего не найдено",
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                            itemsIndexed(filtered, key = { _, p -> p.id }) { _, product ->
                                RecordCardSurface(background = MaterialTheme.colorScheme.background) {
                                    Row(
                                        Modifier.fillMaxWidth().heightIn(min = CardDimensions.contentHeight)
                                            .padding(horizontal = AppSpacing.screenHorizontal, vertical = AppSpacing.md),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                                            Text(product.name, style = MaterialTheme.typography.titleMedium)
                                            Text(
                                                NutritionOperations.formatCalories(product.caloriesPer100g) + " ккал / 100 г · Б " +
                                                    NutritionOperations.formatAmount(product.proteinPer100g) + " · Ж " +
                                                    NutritionOperations.formatAmount(product.fatPer100g) + " · У " +
                                                    NutritionOperations.formatAmount(product.carbsPer100g),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(onClick = { editingProduct = product; returnToCatalog = true; productFormVisible = true }) {
                                            Icon(Icons.Outlined.Edit, contentDescription = "Изменить продукт")
                                        }
                                        IconButton(onClick = { onProductsChange(products.filterNot { it.id == product.id }) }) {
                                            Icon(Icons.Outlined.Delete, contentDescription = "Удалить продукт")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            addingProducts -> {
                Column(
                    Modifier.fillMaxSize().padding(padding)
                        .padding(horizontal = AppSpacing.screenHorizontal, vertical = AppSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
                ) {
                    Text(
                        "Добавляй продукты в одну запись. Для каждого укажи количество в граммах.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                        contentPadding = PaddingValues(bottom = AppSpacing.md)
                    ) {
                        itemsIndexed(draftLines, key = { _, line -> line.id }) { _, line ->
                            val focusRequester = remember(line.id) { FocusRequester() }
                            LaunchedEffect(focusDraftId) {
                                if (focusDraftId == line.id) {
                                    kotlinx.coroutines.delay(100)
                                    focusRequester.requestFocus()
                                    keyboardController?.show()
                                }
                            }
                            val selected = products.firstOrNull { it.id == line.selectedProductId }
                            val matches = NutritionCatalogOperations.search(products, line.query)
                                .filter { line.selectedProductId == null || it.id != line.selectedProductId }
                                .take(6)
                            RecordCardSurface(background = MaterialTheme.colorScheme.background) {
                                Column(
                                    Modifier.fillMaxWidth().padding(AppSpacing.md),
                                    verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = line.query,
                                            onValueChange = { value ->
                                                draftLines = draftLines.map {
                                                    if (it.id == line.id) it.copy(query = value, selectedProductId = null) else it
                                                }
                                            },
                                            modifier = Modifier.weight(1f).focusRequester(focusRequester),
                                            singleLine = true,
                                            label = { Text("Продукт") },
                                            placeholder = { Text("Название продукта") }
                                        )
                                        OutlinedTextField(
                                            value = line.grams,
                                            onValueChange = { value ->
                                                if (DecimalInputOperations.isValid(value, maxLength = 10)) {
                                                    draftLines = draftLines.map { if (it.id == line.id) it.copy(grams = value) else it }
                                                }
                                            },
                                            modifier = Modifier.width(104.dp),
                                            singleLine = true,
                                            label = { Text("Граммы") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                                        )
                                    }
                                    if (selected != null) {
                                        Text(
                                            "Выбрано: " + selected.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    } else if (line.query.isNotBlank() && matches.isNotEmpty()) {
                                        matches.forEach { product ->
                                            TextButton(
                                                onClick = {
                                                    draftLines = draftLines.map {
                                                        if (it.id == line.id) it.copy(query = product.name, selectedProductId = product.id) else it
                                                    }
                                                    focusDraftId = null
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                                                    Text(product.name, style = MaterialTheme.typography.titleSmall)
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
                                    if (line.query.isNotBlank() && selected == null) {
                                        TextButton(onClick = {
                                            pendingDraftId = line.id
                                            editingProduct = null
                                            returnToCatalog = false
                                            productFormVisible = true
                                        }) { Text("Добавить новый продукт в базу") }
                                    }
                                    if (draftLines.size > 1) {
                                        TextButton(
                                            onClick = { draftLines = draftLines.filterNot { it.id == line.id } },
                                            modifier = Modifier.align(Alignment.End)
                                        ) { Text("Убрать строку") }
                                    }
                                }
                            }
                        }
                    }
                    TextButton(onClick = {
                        catalogSearch = ""
                        catalogVisible = true
                    }) { Text("Открыть базу продуктов") }
                    Button(
                        onClick = {
                            val additions = draftLines.mapNotNull { line ->
                                val product = products.firstOrNull { it.id == line.selectedProductId } ?: return@mapNotNull null
                                val grams = DecimalInputOperations.parse(line.grams)
                                if (grams <= 0.0) return@mapNotNull null
                                NutritionCatalogOperations.createMealItem(product, grams)
                            }
                            if (additions.isNotEmpty()) {
                                onChange(NutritionCatalogOperations.withMealItems(note, note.nutritionItems + additions))
                                addingProducts = false
                                draftLines = listOf(NutritionDraftLine())
                                focusDraftId = null
                                onSaved()
                            }
                        },
                        enabled = draftLines.any { line ->
                            line.selectedProductId != null && DecimalInputOperations.parse(line.grams) > 0.0
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Добавить в запись") }
                }
            }
            legacyRecord -> LegacyNutritionRecordEditor(note, padding, onChange)
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(
                        start = AppSpacing.screenHorizontal, end = AppSpacing.screenHorizontal,
                        top = AppSpacing.md, bottom = 104.dp
                    )
                ) {
                    item(key = "meal_reorder_anchor") { Spacer(Modifier.height(1.dp)) }
                    item(key = "meal_title") {
                        Text("Продукты в приёме пищи", style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = AppSpacing.sm))
                    }
                    if (orderedItems.isEmpty()) {
                        item(key = "meal_empty") {
                            Text("Нажми +, выбери продукт и укажи массу. Этой же кнопкой добавляй следующие продукты.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = AppSpacing.md))
                        }
                    }
                    itemsIndexed(orderedItems, key = { _, item -> item.id }) { _, item ->
                        val itemIndex = note.nutritionItems.indexOfFirst { it.id == item.id }
                        RecordCardSurface(background = MaterialTheme.colorScheme.background,
                            modifier = Modifier.animateItemPlacement(tween(NOTE_REORDER_ANIMATION_DURATION_MILLIS))) {
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = CardDimensions.contentHeight)
                                    .padding(start = AppSpacing.screenHorizontal, end = AppSpacing.xs,
                                        top = AppSpacing.sm, bottom = AppSpacing.sm),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                                    Text(item.productName, style = MaterialTheme.typography.titleMedium)
                                    var grams by remember(item.id, item.grams) { mutableStateOf(DecimalInputOperations.format(item.grams)) }
                                    OutlinedTextField(
                                        value = grams,
                                        onValueChange = { raw ->
                                            if (DecimalInputOperations.isValid(raw, maxLength = 10)) {
                                                grams = raw
                                                val amount = DecimalInputOperations.parse(raw).coerceAtLeast(0.0)
                                                onChange(NutritionCatalogOperations.withMealItems(note, note.nutritionItems.map {
                                                    if (it.id == item.id) NutritionCatalogOperations.updateGrams(it, amount) else it
                                                }))
                                            }
                                        },
                                        modifier = Modifier.widthIn(min = 100.dp, max = 130.dp),
                                        singleLine = true, label = { Text("Граммы") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                                    )
                                    Text(
                                        NutritionOperations.formatCalories(item.calories) + " ккал · Б " +
                                            NutritionOperations.formatAmount(item.protein) + " г · Ж " +
                                            NutritionOperations.formatAmount(item.fat) + " г · У " +
                                            NutritionOperations.formatAmount(item.carbs) + " г",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    IconButton(onClick = {
                                        onChange(NutritionCatalogOperations.withMealItems(note, note.nutritionItems.filterNot { it.id == item.id }))
                                    }) { Icon(Icons.Outlined.Delete, contentDescription = "Удалить продукт") }
                                    Box(
                                        Modifier.size(CardDimensions.handleTouchTargetSize)
                                            .pointerInput(item.id, itemIndex) {
                                                detectDragGestures(
                                                    onDragStart = { start ->
                                                        val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == item.id }
                                                            ?: return@detectDragGestures
                                                        val handleTop = with(density) { CardDimensions.handleHitTargetTopOffset.toPx() }
                                                        val pointer = info.offset + handleTop + start.y
                                                        activeDragId = item.id
                                                        reorderSession = AdjacentReorderEngine.begin(orderedItems.map { it.id }, item.id)
                                                        dragPointerY = pointer
                                                        dragGrabOffsetPx = pointer - info.offset
                                                        dragReleaseRequested = false
                                                    },
                                                    onDrag = { change, amount ->
                                                        if (activeDragId == item.id) { dragPointerY += amount.y; change.consume() }
                                                    },
                                                    onDragEnd = { if (activeDragId == item.id) dragReleaseRequested = true },
                                                    onDragCancel = {
                                                        if (activeDragId == item.id) {
                                                            activeDragId = null; reorderSession = null
                                                            dragPointerY = 0f; dragGrabOffsetPx = 0f; dragReleaseRequested = false
                                                        }
                                                    }
                                                )
                                            },
                                        contentAlignment = Alignment.Center
                                    ) { DragHandleIcon(color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                }
                            }
                        }
                    }
                    item(key = "meal_total") {
                        Column(Modifier.fillMaxWidth().padding(top = AppSpacing.lg),
                            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                            HorizontalDivider()
                            Text("Итого за приём пищи", style = MaterialTheme.typography.titleMedium)
                            Text(NutritionOperations.formatCalories(note.nutritionCalories) + " ккал",
                                style = MaterialTheme.typography.headlineSmall)
                            Text("Б " + NutritionOperations.formatAmount(note.nutritionProtein) + " г · Ж " +
                                NutritionOperations.formatAmount(note.nutritionFat) + " г · У " +
                                NutritionOperations.formatAmount(note.nutritionCarbs) + " г",
                                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    if (productFormVisible) {
        NutritionProductFormDialog(
            product = editingProduct,
            onDismiss = {
                productFormVisible = false; editingProduct = null
                pendingDraftId = null
                if (returnToCatalog) catalogVisible = true
                returnToCatalog = false
            },
            onSave = { saved ->
                val updated = if (products.any { it.id == saved.id }) products.map { if (it.id == saved.id) saved else it }
                    else products + saved
                onProductsChange(updated.sortedBy { it.name.lowercase() })
                pendingDraftId?.let { draftId ->
                    draftLines = draftLines.map {
                        if (it.id == draftId) it.copy(query = saved.name, selectedProductId = saved.id) else it
                    }
                }
                pendingDraftId = null; productFormVisible = false; editingProduct = null
                if (returnToCatalog) catalogVisible = true
                returnToCatalog = false
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

