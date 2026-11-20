package com.magnumold.notes

import android.content.SharedPreferences
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag

internal enum class AppModule(
    val title: String,
    val subtitle: String,
    val recordType: RecordType?,
    val symbol: String
) {
    ALL("Главная", "Общая лента объектов", null, "⌂"),
    NOTES("Заметки", "Текст, списки и быстрые записи", RecordType.NOTE, "✎"),
    TASKS("Задачи", "Планы, сроки и выполнение", RecordType.TASK, "✓"),
    TIMERS("Таймеры", "Обратный отсчёт и интервалы", RecordType.TIMER, "◷"),
    NUTRITION("КБЖУ", "Учёт калорий и макронутриентов", RecordType.NUTRITION, "◉"),
    WORKOUTS("Тренировки", "Планы и история занятий", RecordType.WORKOUT, "⌁"),
    PURCHASES("Покупки", "Списки и приобретённые товары", RecordType.PURCHASE, "▤")
}

internal data class ModuleConfiguration(
    val enabled: Set<AppModule> = AppModule.entries.filter { it != AppModule.ALL }.toSet(),
    val order: List<AppModule> = AppModule.entries.filter { it != AppModule.ALL }
) {
    fun normalized(): ModuleConfiguration {
        val available = AppModule.entries.filter { it != AppModule.ALL }
        val safeOrder = (order.filter { it in available } + available.filterNot { it in order }).distinct()
        val safeEnabled = enabled.intersect(available.toSet())
        return copy(enabled = safeEnabled, order = safeOrder)
    }

    fun enabledInOrder(): List<AppModule> {
        val safe = normalized()
        return safe.order.filter { it in safe.enabled }
    }
}

internal data class ModuleDragRow(
    val key: String,
    val modules: List<AppModule>
) {
    val isGroup: Boolean get() = modules.size > 1
    val anchor: AppModule get() = modules.first()
}

internal object ModuleReorderModel {
    fun createRows(
        order: List<AppModule>,
        selected: Set<AppModule>,
        anchor: AppModule
    ): List<ModuleDragRow>? {
        if (anchor !in order) return null
        val movingModules = order.filter { it in selected }
        if (anchor !in movingModules) return null
        if (movingModules.size <= 1) return order.map { ModuleDragRow(it.name, listOf(it)) }

        val movingSet = movingModules.toSet()
        val insertionIndex = order.takeWhile { it != anchor }.count { it !in movingSet }
        val remaining = order.filterNot { it in movingSet }.toMutableList()
        remaining.addAll(insertionIndex.coerceIn(0, remaining.size), movingModules)

        val groupKey = "module-group-" + movingModules.joinToString("-") { it.name }
        return remaining.mapIndexed { index, module ->
            when {
                index == insertionIndex -> ModuleDragRow(groupKey, movingModules)
                module in movingSet -> ModuleDragRow(groupKey, movingModules)
                else -> ModuleDragRow(module.name, listOf(module))
            }
        }.distinctBy { it.key }
    }

    fun flatten(rows: List<ModuleDragRow>): List<AppModule> = rows.flatMap { it.modules }
}

internal class ModuleSettingsStore(private val preferences: SharedPreferences) {
    fun load(): ModuleConfiguration {
        // A damaged preference must not accidentally disable every module.
        // An intentionally empty set remains valid and is preserved.
        val enabledNames = runCatching { preferences.getStringSet(KEY_ENABLED, null) }.getOrNull()
        val orderNames = runCatching { preferences.getString(KEY_ORDER, null) }.getOrNull()
        val parsedEnabled = enabledNames?.mapNotNull {
            runCatching { AppModule.valueOf(it) }.getOrNull()
        }?.toSet()
        val enabled = when {
            enabledNames == null -> ModuleConfiguration().enabled
            enabledNames.isNotEmpty() && parsedEnabled.isNullOrEmpty() -> ModuleConfiguration().enabled
            else -> parsedEnabled.orEmpty()
        }
        val order = orderNames?.split(",")?.mapNotNull {
            runCatching { AppModule.valueOf(it) }.getOrNull()
        } ?: ModuleConfiguration().order
        return ModuleConfiguration(enabled, order).normalized()
    }

    fun save(configuration: ModuleConfiguration) {
        val safe = configuration.normalized()
        preferences.edit()
            .putStringSet(KEY_ENABLED, safe.enabled.map { it.name }.toSet())
            .putString(KEY_ORDER, safe.order.joinToString(",") { it.name })
            .apply()
    }

    private companion object {
        const val KEY_ENABLED = "concept_modules_enabled"
        const val KEY_ORDER = "concept_modules_order"
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ModulesScreen(
    configuration: ModuleConfiguration,
    onConfigurationChange: (ModuleConfiguration) -> Unit,
    onOpenModule: (AppModule) -> Unit,
    onOpenManagement: () -> Unit,
    onBack: () -> Unit,
    themeColors: CustomThemeColors
) {
    val ordered = configuration.normalized().order
    val listState = rememberLazyListState()
    val density = androidx.compose.ui.platform.LocalDensity.current

    var selectedModules by remember { mutableStateOf<Set<AppModule>>(emptySet()) }
    var draggingModules by remember { mutableStateOf<Set<AppModule>>(emptySet()) }
    var dragBaseRows by remember { mutableStateOf<Map<String, ModuleDragRow>>(emptyMap()) }
    var reorderSession by remember { mutableStateOf<ReorderSession?>(null) }
    var dragPointerY by remember { mutableFloatStateOf(0f) }
    var dragGrabOffsetPx by remember { mutableFloatStateOf(0f) }
    var dragReleaseRequested by remember { mutableStateOf(false) }

    val isDragging = draggingModules.isNotEmpty()
    val dragRows = reorderSession?.keys?.mapNotNull { dragBaseRows[it] }.orEmpty()

    fun finishDrag() {
        if (draggingModules.isNotEmpty()) {
            onConfigurationChange(configuration.copy(order = ModuleReorderModel.flatten(dragRows)))
        }
        draggingModules = emptySet()
        reorderSession = null
        dragBaseRows = emptyMap()
        dragPointerY = 0f
        dragGrabOffsetPx = 0f
        dragReleaseRequested = false
    }

    fun startDrag(module: AppModule, pointerY: Float, grabOffset: Float) {
        val movingSelection = if (module in selectedModules && selectedModules.size > 1) {
            selectedModules
        } else {
            setOf(module)
        }
        val rows = ModuleReorderModel.createRows(ordered, movingSelection, module) ?: return
        dragBaseRows = rows.associateBy { it.key }
        reorderSession = AdjacentReorderEngine.begin(rows.map { it.key }, rows.firstOrNull { module in it.modules }?.key ?: return)
        draggingModules = movingSelection
        dragPointerY = pointerY
        dragGrabOffsetPx = grabOffset
        dragReleaseRequested = false
    }

    AdjacentReorderEffect(
        isDragging = isDragging,
        orderedKeys = dragRows.map { it.key },
        reorderSession = reorderSession,
        pointerY = dragPointerY,
        grabOffsetPx = dragGrabOffsetPx,
        releaseRequested = dragReleaseRequested,
        listState = listState,
        density = density,
        onSessionChange = { reorderSession = it },
        onFinishDrag = { finishDrag() }
    )

    AppPageScaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(color = themeColors.topBar, contentColor = noteTextColor(themeColors.topBar)) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .height(AppPageLayout.toolbarHeight)
                        .padding(horizontal = AppSpacing.screenHorizontal),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (selectedModules.isEmpty()) "Модули" else "${selectedModules.size} выбрано",
                        style = AppTypography.screenTitleStyle()
                    )
                    Spacer(Modifier.weight(1f))
                    if (selectedModules.isNotEmpty()) {
                        IconButton(onClick = { selectedModules = emptySet() }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Снять выделение")
                        }
                    } else {
                        IconButton(onClick = onOpenManagement) {
                            Icon(Icons.Outlined.Tune, contentDescription = "Управление модулями")
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = AppSpacing.md,
                    bottom = 28.dp
                )
            ) {
                item(key = "modules_reorder_viewport_anchor") {
                    Spacer(Modifier.height(1.dp))
                }
                item(key = "modules_description") {
                    Text(
                        "Меняйте порядок модулей перетаскиванием. Доступность настраивается отдельно.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            horizontal = AppSpacing.screenHorizontal,
                            bottom = AppSpacing.md
                        )
                    )
                }
                val rows = if (isDragging) dragRows else ordered.map { ModuleDragRow(it.name, listOf(it)) }
                items(rows, key = { it.key }) { row ->
                    val module = row.anchor
                    val enabled = module in configuration.enabled
                    val selected = row.isGroup || module in selectedModules
                    val baseBackground = MaterialTheme.colorScheme.background
                    val dark = baseBackground.luminance() < 0.5f
                    val cardBackground = mainCardBackground(
                        color = baseBackground,
                        selected = selected,
                        isBaseColor = true,
                        dark = dark,
                        selectionColor = themeColors.selection
                    )
                    val cardTextColor = mainCardTextColor(baseBackground, isBaseColor = true)

                    RecordCardSurface(
                        background = cardBackground,
                        modifier = Modifier.testTag("module-card-${row.key}")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .heightIn(min = CardDimensions.contentHeight),
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(
                                modifier = Modifier.weight(1f)
                                    .combinedClickable(
                                        enabled = !row.isGroup,
                                        onClick = {
                                            if (selectedModules.isNotEmpty()) {
                                                selectedModules = if (module in selectedModules) selectedModules - module else selectedModules + module
                                            } else if (enabled && !isDragging) {
                                                onOpenModule(module)
                                            }
                                        },
                                        onLongClick = {
                                            if (!isDragging) selectedModules = selectedModules + module
                                        }
                                    )
                                    .padding(
                                        start = AppSpacing.screenHorizontal,
                                        top = AppSpacing.md,
                                        bottom = AppSpacing.md
                                    )
                            ) {
                                MainCardHeader(
                                    text = if (row.isGroup) "Группа" else module.title,
                                    textColor = cardTextColor,
                                    showDragHandle = false
                                )
                                Text(
                                    if (row.isGroup) "${row.modules.size} модулей" else module.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = cardTextColor.copy(alpha = 0.76f),
                                    modifier = Modifier.padding(top = AppSpacing.xs)
                                )
                            }

                            Box(
                                modifier = Modifier.padding(
                                    top = CardDimensions.sideRailTopInset,
                                    end = CardDimensions.handleHitTargetRightInset
                                )
                            ) {
                                ModuleDragHandle(
                                    itemKey = row.key,
                                    listState = listState,
                                    enabled = !isDragging,
                                    onStart = { pointerY, grabOffset ->
                                        startDrag(module, pointerY, grabOffset)
                                    },
                                    onPointer = { pointerY -> dragPointerY = pointerY },
                                    onRelease = { dragReleaseRequested = true },
                                    onCancel = {
                                        draggingModules = emptySet()
                                        reorderSession = null
                                        dragBaseRows = emptyMap()
                                        dragPointerY = 0f
                                        dragGrabOffsetPx = 0f
                                        dragReleaseRequested = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModuleDragHandle(
    itemKey: String,
    listState: LazyListState,
    enabled: Boolean,
    onStart: (pointerY: Float, grabOffset: Float) -> Unit,
    onPointer: (Float) -> Unit,
    onRelease: () -> Unit,
    onCancel: () -> Unit
) {
    val latestEnabled by rememberUpdatedState(enabled)
    val latestOnStart by rememberUpdatedState(onStart)
    val latestOnPointer by rememberUpdatedState(onPointer)
    val latestOnRelease by rememberUpdatedState(onRelease)
    val latestOnCancel by rememberUpdatedState(onCancel)

    Box(
        modifier = Modifier.size(40.dp).pointerInput(itemKey) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (!latestEnabled) return@awaitEachGesture
                var totalX = 0f
                var totalY = 0f
                var dragStartY = Float.NaN
                while (dragStartY.isNaN()) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    val delta = change.position - change.previousPosition
                    totalX += delta.x
                    totalY += delta.y
                    val slop = viewConfiguration.touchSlop
                    if (kotlin.math.abs(totalY) > slop && kotlin.math.abs(totalY) > kotlin.math.abs(totalX)) {
                        change.consume()
                        dragStartY = change.position.y
                    } else if (kotlin.math.abs(totalX) > slop && kotlin.math.abs(totalX) >= kotlin.math.abs(totalY)) {
                        return@awaitEachGesture
                    }
                }
                if (dragStartY.isNaN()) return@awaitEachGesture

                val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == itemKey }
                    ?: return@awaitEachGesture
                val handleTopInRow = (item.size - size.height) / 2f
                val rootY = item.offset + handleTopInRow + dragStartY
                latestOnStart(rootY, rootY - item.offset)

                var released = false
                try {
                    var tracking = true
                    while (tracking) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            released = true
                            latestOnRelease()
                            tracking = false
                        } else {
                            val currentItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == itemKey }
                            val currentTop = currentItem?.offset ?: item.offset
                            latestOnPointer(currentTop + handleTopInRow + change.position.y)
                            change.consume()
                        }
                    }
                } finally {
                    if (!released) latestOnCancel()
                }
            }
        },
        contentAlignment = Alignment.Center
    ) {
        DragHandleIcon()
    }
}
