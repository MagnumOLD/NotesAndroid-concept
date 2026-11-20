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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NutritionRecordEditor(
    note: Note,
    products: List<NutritionProduct>,
    topBarColor: Color,
    addButtonColor: Color,
    onBack: () -> Unit,
    onChange: (Note) -> Unit,
    onProductsChange: (List<NutritionProduct>) -> Unit
) {
    var catalogVisible by remember { mutableStateOf(false) }
    var catalogSearch by remember { mutableStateOf("") }
    var pickerLineId by remember { mutableStateOf<String?>(null) }
    var pickerSearch by remember { mutableStateOf("") }
    var editingProduct by remember { mutableStateOf<NutritionProduct?>(null) }
    var productFormVisible by remember { mutableStateOf(false) }
    var pendingLineId by remember { mutableStateOf<String?>(null) }
    var returnToCatalog by remember { mutableStateOf(false) }
    var activeDragId by remember { mutableStateOf<String?>(null) }
    var dragPointerY by remember { mutableFloatStateOf(0f) }
    var dragGrabOffsetPx by remember { mutableFloatStateOf(0f) }
    var dragReleaseRequested by remember { mutableStateOf(false) }
    var reorderSession by remember { mutableStateOf<ReorderSession?>(null) }
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val legacyRecord = note.nutritionItems.isEmpty() && (
        note.nutritionGrams > 0 || note.nutritionCalories > 0 ||
        note.nutritionProtein > 0 || note.nutritionFat > 0 || note.nutritionCarbs > 0
    )
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
    BackHandler(enabled = catalogVisible) { catalogVisible = false }

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
                    IconButton(onClick = { if (catalogVisible) catalogVisible = false else onBack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Назад")
                    }
                    Text(
                        if (catalogVisible) "База продуктов" else "Приём пищи",
                        style = AppTypography.screenTitleStyle(),
                        color = noteTextColor(topBarColor),
                        modifier = Modifier.weight(1f)
                    )
                    if (!catalogVisible) {
                        TextButton(onClick = { catalogSearch = ""; catalogVisible = true }) {
                            Text("База продуктов", color = noteTextColor(topBarColor))
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (!legacyRecord || catalogVisible) {
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
                            } else {
                                pickerSearch = ""
                                pickerLineId = java.util.UUID.randomUUID().toString()
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
                                    Text(item.productName, style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.clickable { pickerSearch = ""; pickerLineId = item.id })
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

    pickerLineId?.let { lineId ->
        AlertDialog(
            onDismissRequest = { pickerLineId = null },
            title = { Text("Выбрать продукт") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                    OutlinedTextField(pickerSearch, { pickerSearch = it }, modifier = Modifier.fillMaxWidth(),
                        singleLine = true, label = { Text("Поиск") })
                    val matches = products.filter { it.name.contains(pickerSearch.trim(), ignoreCase = true) }
                    if (matches.isEmpty()) Text("Подходящих продуктов нет")
                    Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                        matches.forEach { product ->
                            TextButton(onClick = {
                                val item = NutritionCatalogOperations.createMealItem(product, 100.0, lineId)
                                val index = note.nutritionItems.indexOfFirst { it.id == lineId }
                                val items = if (index < 0) note.nutritionItems + item
                                    else note.nutritionItems.toMutableList().also { it[index] = item }
                                onChange(NutritionCatalogOperations.withMealItems(
                                    note.copy(titleText = note.titleText.ifBlank { "Приём пищи" }), items))
                                pickerLineId = null
                            }, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                                    Text(product.name)
                                    Text(NutritionOperations.formatCalories(product.caloriesPer100g) + " ккал / 100 г · Б " +
                                        NutritionOperations.formatAmount(product.proteinPer100g) + " · Ж " +
                                        NutritionOperations.formatAmount(product.fatPer100g) + " · У " +
                                        NutritionOperations.formatAmount(product.carbsPer100g),
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    TextButton(onClick = {
                        pendingLineId = lineId; pickerLineId = null; returnToCatalog = false
                        editingProduct = null; productFormVisible = true
                    }) { Text("Создать продукт в базе") }
                }
            },
            confirmButton = { TextButton(onClick = { pickerLineId = null }) { Text("Закрыть") } }
        )
    }

    if (productFormVisible) {
        NutritionProductFormDialog(
            product = editingProduct,
            onDismiss = {
                productFormVisible = false; editingProduct = null
                if (returnToCatalog) catalogVisible = true
                returnToCatalog = false
            },
            onSave = { saved ->
                val updated = if (products.any { it.id == saved.id }) products.map { if (it.id == saved.id) saved else it }
                    else products + saved
                onProductsChange(updated.sortedBy { it.name.lowercase() })
                pendingLineId?.let { lineId ->
                    val item = NutritionCatalogOperations.createMealItem(saved, 100.0, lineId)
                    val index = note.nutritionItems.indexOfFirst { it.id == lineId }
                    val mealItems = if (index < 0) note.nutritionItems + item
                        else note.nutritionItems.toMutableList().also { it[index] = item }
                    onChange(NutritionCatalogOperations.withMealItems(note, mealItems))
                }
                pendingLineId = null; productFormVisible = false; editingProduct = null
                if (returnToCatalog) catalogVisible = true
                returnToCatalog = false
            }
        )
    }
}

