package com.magnumold.notes


import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material.icons.outlined.Redo
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.withFrameNanos
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import kotlinx.coroutines.launch

import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.waitForUpOrCancellation



import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.roundToInt

private val colorPickerPresets = listOf(
    Color(0xFF40C8E0), Color(0xFFFF9F0A), Color(0xFFFFD60A), Color(0xFF30D158),
    Color(0xFFFF375F), Color(0xFFBF5AF2), Color(0xFF007AFF), Color(0xFF5E5CE6),
    Color(0xFF8E8E93), Color(0xFFA2845E), Color(0xFFFF6961), Color(0xFF64D2FF)
)


@Composable
internal fun SettingsScreen(
    themeMode: String,
    activeColorMode: String,
    onThemeModeChange: (String) -> Unit,
    themeColors: CustomThemeColors,
    onThemeColorChange: (String, Color) -> Unit,
    onResetThemeColors: () -> Unit,
    noteColors: List<Color>,
    noteColorOpacity: Float,
    timelineDateColors: TimelineDateColors,
    showTimelineDateHeaders: Boolean,
    onShowTimelineDateHeadersChange: (Boolean) -> Unit,
    onTimelineDateColorChange: (String, Color) -> Unit,
    onResetTimelineDateColors: () -> Unit,
    onNoteColorsChange: (List<Color>) -> Unit,
    onNoteColorOpacityChange: (Float) -> Unit,
    onExport: () -> Unit,    onImport: () -> Unit,
    onOpenArchive: () -> Unit,
    onOpenTrash: () -> Unit,
    trashRetentionDays: Int,
    interactiveCardsEnabled: Boolean = false,
    onInteractiveCardsEnabledChange: (Boolean) -> Unit = {},
    cardPreviewFields: Set<String> = CardPreviewField.defaults,
    onCardPreviewFieldsChange: (Set<String>) -> Unit = {},
    collectionVisibleModules: Set<AppModule> = ModuleConfiguration().enabled,
    onCollectionVisibilityChange: (AppModule, Boolean) -> Unit = { _, _ -> },
    homeCollectionVisible: Boolean = true,
    onHomeCollectionVisibilityChange: (Boolean) -> Unit = {},
    homeButtonLayout: HomeButtonLayout = HomeButtonLayout.SEPARATE,
    onHomeButtonLayoutChange: (HomeButtonLayout) -> Unit = {},
    homeAddRecordType: RecordType = RecordType.NOTE,
    enabledModules: Set<AppModule> = ModuleConfiguration().enabled,
    onHomeAddRecordTypeChange: (RecordType) -> Unit = {},
    onTrashRetentionDaysChange: (Int) -> Unit,
    onBack: () -> Unit
) {
    var colorEditorIndex by remember { mutableStateOf<Int?>(null) }
    var customColorEditorKey by remember { mutableStateOf<String?>(null) }
    var dateColorEditorKey by remember { mutableStateOf<String?>(null) }
    var personalizationVisible by remember { mutableStateOf(false) }
    var homePageVisible by remember { mutableStateOf(false) }
    var cardBehaviorVisible by remember { mutableStateOf(false) }
    var storageVisible by remember { mutableStateOf(false) }
    var moduleCollectionsVisible by remember { mutableStateOf(false) }
    var retentionMenuExpanded by remember { mutableStateOf(false) }

    AppBackHandler(enabled = colorEditorIndex != null || customColorEditorKey != null || dateColorEditorKey != null || personalizationVisible || homePageVisible || cardBehaviorVisible || storageVisible || moduleCollectionsVisible) {
        if (colorEditorIndex != null || customColorEditorKey != null || dateColorEditorKey != null) {
            colorEditorIndex = null
            customColorEditorKey = null
            dateColorEditorKey = null
        } else if (personalizationVisible) {
            personalizationVisible = false
        } else if (homePageVisible) {
            homePageVisible = false
        } else if (cardBehaviorVisible) {
            cardBehaviorVisible = false
        } else if (storageVisible) {
            storageVisible = false
        } else {
            moduleCollectionsVisible = false
        }
    }

    if (dateColorEditorKey != null) {
        val key = dateColorEditorKey!!
        val title = if (key == "background") "Фон плашки даты" else "Цвет текста даты"
        val currentColor = if (key == "background") timelineDateColors.background else timelineDateColors.text
        val defaults = if (activeColorMode == "dark") TimelineDateColorSets().dark else TimelineDateColorSets().light
        val resetColor = if (key == "background") defaults.background else defaults.text
        ThemeColorEditorScreen(
            title = title,
            color = currentColor,
            resetColor = resetColor,
            topBarColor = themeColors.topBar,
            onBack = { dateColorEditorKey = null },
            onColorChange = { onTimelineDateColorChange(key, it) },
            onReset = { onTimelineDateColorChange(key, resetColor) }
        )
        return
    }

    if (customColorEditorKey != null) {
        val key = customColorEditorKey!!
        val title = when (key) {
            "background" -> "Фон приложения"
            "topBar" -> "Панель приложения"
            "statusBar" -> "Строка состояния"
            "selection" -> "Выделенные заметки"
            "addButton" -> "Кнопки добавления"
            else -> "Текст выбранной подборки"
        }
        ThemeColorEditorScreen(
            title = title,
            color = customThemeColor(themeColors, key),
            resetColor = customThemeColor(if (activeColorMode == "dark") ThemeColorSets().dark else ThemeColorSets().light, key),
            topBarColor = themeColors.topBar,
            onBack = { customColorEditorKey = null },
            onColorChange = { onThemeColorChange(key, it) },
            onReset = { onThemeColorChange(key, customThemeColor(if (activeColorMode == "dark") ThemeColorSets().dark else ThemeColorSets().light, key)) }
        )
        return
    }

    if (colorEditorIndex != null) {
        NoteColorEditorScreen(
            index = colorEditorIndex!!,
            color = noteColors[colorEditorIndex!!],
            topBarColor = themeColors.topBar,
            onBack = { colorEditorIndex = null },
            onColorChange = { updated ->
                onNoteColorsChange(noteColors.toMutableList().apply { this[colorEditorIndex!!] = updated })
            }
        )
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppPageTopBar(
                backgroundColor = themeColors.topBar,
                title = {
                    Text(
                        when {
                            personalizationVisible -> "Персонализация"
                            homePageVisible -> "Главная страница"
                            cardBehaviorVisible -> "Поведение карточек"
                            storageVisible -> "Хранение"
                            moduleCollectionsVisible -> "Подборки по модулям"
                            else -> "Настройки"
                        },
                        style = AppTypography.screenTitleStyle()
                    )
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            if (homePageVisible) {
                ListItem(
                    headlineContent = { Text("Расположение кнопок", style = AppTypography.emphasisBodyStyle()) },
                    supportingContent = { Text("Выберите расположение кнопок модулей и добавления записи") },
                    modifier = Modifier.padding(horizontal = AppSpacing.settingsListItemOuterInset)
                )
                listOf(
                    HomeButtonLayout.SEPARATE to "Раздельно (по умолчанию)",
                    HomeButtonLayout.VERTICAL to "Вертикально — отдельные кнопки",
                    HomeButtonLayout.VERTICAL_PANEL to "Вертикальная панель"
                ).forEach { (layout, label) ->
                    ListItem(
                        headlineContent = { Text(label) },
                        trailingContent = {
                            RadioButton(
                                selected = homeButtonLayout == layout,
                                onClick = { onHomeButtonLayoutChange(layout) }
                            )
                        },
                        modifier = Modifier.clickable { onHomeButtonLayoutChange(layout) }
                            .padding(horizontal = AppSpacing.settingsListItemOuterInset)
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f))
                ListItem(
                    headlineContent = { Text("Что создавать кнопкой «+»", style = AppTypography.emphasisBodyStyle()) },
                    supportingContent = { Text("Выберите тип новой записи на главной странице") },
                    modifier = Modifier.padding(horizontal = AppSpacing.settingsListItemOuterInset)
                )
                AppModule.entries.filter { it != AppModule.ALL }.forEach { module ->
                    val recordType = module.recordType ?: return@forEach
                    val selected = homeAddRecordType == recordType
                    val moduleDisabled = module !in enabledModules
                    ListItem(
                        headlineContent = { Text(module.title) },
                        supportingContent = if (selected && moduleDisabled) {
                            { Text("Модуль отключён. При нажатии «+» приложение предложит включить его или выбрать другой тип.") }
                        } else {
                            null
                        },
                        trailingContent = {
                            RadioButton(
                                selected = selected,
                                onClick = { onHomeAddRecordTypeChange(recordType) }
                            )
                        },
                        modifier = Modifier.clickable { onHomeAddRecordTypeChange(recordType) }
                            .padding(horizontal = AppSpacing.settingsListItemOuterInset)
                    )
                }
            } else if (personalizationVisible) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f))
                ListItem(headlineContent = { Text("Внешний вид", style = AppTypography.emphasisBodyStyle()) }, modifier = Modifier.padding(horizontal = AppSpacing.settingsListItemOuterInset))
                ListItem(
                    headlineContent = { Text("Системная тема") },
                    supportingContent = { Text("Следовать настройкам устройства") },
                    trailingContent = { RadioButton(selected = themeMode == "system", onClick = { onThemeModeChange("system") }) },
                    modifier = Modifier.clickable { onThemeModeChange("system") }.padding(horizontal = AppSpacing.settingsListItemOuterInset)
                )
                ListItem(
                    headlineContent = { Text("Светлая тема") },
                    trailingContent = { RadioButton(selected = themeMode == "light", onClick = { onThemeModeChange("light") }) },
                    modifier = Modifier.clickable { onThemeModeChange("light") }.padding(horizontal = AppSpacing.settingsListItemOuterInset)
                )
                ListItem(
                    headlineContent = { Text("Тёмная тема") },
                    trailingContent = { RadioButton(selected = themeMode == "dark", onClick = { onThemeModeChange("dark") }) },
                    modifier = Modifier.clickable { onThemeModeChange("dark") }.padding(horizontal = AppSpacing.settingsListItemOuterInset)
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f))
                ListItem(headlineContent = { Text("Оформление дат", style = AppTypography.emphasisBodyStyle()) }, modifier = Modifier.padding(horizontal = AppSpacing.settingsListItemOuterInset))
                ListItem(
                    headlineContent = { Text("Заголовки дат") },
                    supportingContent = {
                        Text(if (showTimelineDateHeaders) "Заметки сгруппированы по датам" else "Единый список с ручной сортировкой")
                    },
                    trailingContent = {
                        Switch(checked = showTimelineDateHeaders, onCheckedChange = onShowTimelineDateHeadersChange)
                    },
                    modifier = Modifier.padding(horizontal = AppSpacing.settingsListItemOuterInset)
                )
                listOf(
                    Triple("background", "Фон плашки", timelineDateColors.background),
                    Triple("text", "Цвет текста", timelineDateColors.text)
                ).forEach { (key, label, color) ->
                    ListItem(
                        headlineContent = { Text(label) },
                        leadingContent = { Box(Modifier.size(30.dp).background(color, RoundedCornerShape(7.dp))) },
                        trailingContent = { SettingsChevron() },
                        modifier = Modifier.clickable { dateColorEditorKey = key }
                            .padding(horizontal = AppSpacing.settingsListItemOuterInset)
                    )
                }
                TextButton(onClick = onResetTimelineDateColors, modifier = Modifier.padding(start = AppSpacing.screenHorizontal)) {
                    Text("Сбросить оформление дат")
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f))
                ListItem(headlineContent = { Text("Настройка цветов темы", style = AppTypography.emphasisBodyStyle()) }, modifier = Modifier.padding(horizontal = AppSpacing.settingsListItemOuterInset))
                val themeRows = listOf(
                    Triple("topBar", "Панель приложения", themeColors.topBar),
                    Triple("statusBar", "Строка состояния", themeColors.statusBar),
                    Triple("selection", "Цвет выделенных карточек", themeColors.selection),
                    Triple("addButton", "Кнопки добавления", themeColors.addButton),
                    Triple("collectionText", "Текст выбранной подборки", themeColors.collectionText)
                )
                themeRows.forEach { (key, label, color) ->
                    ListItem(
                        headlineContent = { Text(label) },
                        leadingContent = { Box(Modifier.size(30.dp).background(color, RoundedCornerShape(7.dp))) },
                        trailingContent = { SettingsChevron() },
                        modifier = Modifier.clickable { customColorEditorKey = key }.padding(horizontal = AppSpacing.settingsListItemOuterInset)
                    )
                }
                TextButton(onClick = onResetThemeColors, modifier = Modifier.padding(start = AppSpacing.screenHorizontal)) {
                    Text("Сбросить цвета темы")
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f))
                ListItem(headlineContent = { Text("Цвета заметок", style = AppTypography.emphasisBodyStyle()) }, modifier = Modifier.padding(horizontal = AppSpacing.settingsListItemOuterInset))
                noteColors.forEachIndexed { index, color ->
                    ListItem(
                        headlineContent = { Text(listOf("Бирюзовый", "Оранжевый", "Жёлтый", "Зелёный", "Розовый", "Фиолетовый")[index]) },
                        leadingContent = { Box(Modifier.size(30.dp).background(color.copy(alpha = noteColorOpacity), RoundedCornerShape(7.dp))) },
                        trailingContent = { SettingsChevron() },
                        modifier = Modifier.clickable { colorEditorIndex = index }.padding(horizontal = AppSpacing.settingsListItemOuterInset)
                    )
                }
                CompactOpacitySlider(value = noteColorOpacity, onValueChange = onNoteColorOpacityChange)
                TextButton(onClick = { onNoteColorsChange(defaultNoteColors) }, modifier = Modifier.padding(start = AppSpacing.screenHorizontal)) {
                    Text("Вернуть стандартные цвета")
                }
            } else if (cardBehaviorVisible) {
                ListItem(headlineContent = { Text("Интерактивность", style = AppTypography.emphasisBodyStyle()) }, modifier = Modifier.padding(horizontal = AppSpacing.settingsListItemOuterInset))
                ListItem(
                    headlineContent = { Text("Интерактивные карточки") },
                    supportingContent = { Text("Быстро отмечать задачи, покупки и тренировки, запускать и ставить на паузу таймеры прямо в списке") },
                    trailingContent = { Switch(checked = interactiveCardsEnabled, onCheckedChange = onInteractiveCardsEnabledChange) },
                    modifier = Modifier.padding(horizontal = AppSpacing.settingsListItemOuterInset)
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f))
                ListItem(
                    headlineContent = { Text("Состав данных на карточках", style = AppTypography.emphasisBodyStyle()) },
                    supportingContent = { Text("Включайте и отключайте отдельные поля для каждого модуля") },
                    modifier = Modifier.padding(horizontal = AppSpacing.settingsListItemOuterInset)
                )
                listOf(AppModule.TASKS, AppModule.TIMERS, AppModule.NUTRITION, AppModule.WORKOUTS, AppModule.PURCHASES).forEach { module ->
                    ListItem(headlineContent = { Text(module.title, style = AppTypography.emphasisBodyStyle()) },
                        modifier = Modifier.padding(horizontal = AppSpacing.settingsListItemOuterInset))
                    CardPreviewField.forModule(module).forEach { field ->
                        ListItem(
                            headlineContent = { Text(field.title) },
                            trailingContent = {
                                Switch(checked = field.key in cardPreviewFields, onCheckedChange = { checked ->
                                    onCardPreviewFieldsChange(if (checked) cardPreviewFields + field.key else cardPreviewFields - field.key)
                                })
                            },
                            modifier = Modifier.padding(start = AppSpacing.xl, end = AppSpacing.settingsListItemOuterInset)
                        )
                    }
                }
            } else if (storageVisible) {
                ListItem(
                    headlineContent = { Text("Архив") },
                    supportingContent = { Text("Отложенные записи") },
                    leadingContent = { AppIcon(NoteIcon.Archive, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onOpenArchive).padding(horizontal = AppSpacing.settingsListItemOuterInset)
                )
                ListItem(
                    headlineContent = { Text("Корзина") },
                    supportingContent = { Text("Удалённые записи") },
                    leadingContent = { AppIcon(NoteIcon.Trash, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onOpenTrash).padding(horizontal = AppSpacing.settingsListItemOuterInset)
                )
                Box {
                    ListItem(
                        headlineContent = { Text("Хранить в корзине") },
                        supportingContent = {
                            Text(if (trashRetentionDays <= 0) "Без автоматического удаления" else "$trashRetentionDays дней, затем удаление навсегда")
                        },
                        trailingContent = { SettingsChevron() },
                        modifier = Modifier.clickable { retentionMenuExpanded = true }.padding(horizontal = AppSpacing.settingsListItemOuterInset)
                    )
                    DropdownMenu(expanded = retentionMenuExpanded, onDismissRequest = { retentionMenuExpanded = false }) {
                        listOf(7, 30, 90, 365, 0).forEach { days ->
                            DropdownMenuItem(
                                text = { Text(if (days == 0) "Не удалять автоматически" else "$days дней") },
                                onClick = {
                                    onTrashRetentionDaysChange(days)
                                    retentionMenuExpanded = false
                                },
                                trailingIcon = { if (trashRetentionDays == days) Icon(Icons.Outlined.CheckCircle, contentDescription = null) }
                            )
                        }
                    }
                }
            } else if (moduleCollectionsVisible) {
                ListItem(
                    headlineContent = { Text("Отображение подборок", style = AppTypography.emphasisBodyStyle()) },
                    supportingContent = { Text("Выберите, в каких модулях показывать подборки. Скрытие не удаляет подборки и записи внутри них.") },
                    modifier = Modifier.padding(horizontal = AppSpacing.settingsListItemOuterInset)
                )
                ListItem(
                    headlineContent = { Text("Главная") },
                    supportingContent = { Text("Показывать подборки на главной странице") },
                    trailingContent = {
                        Switch(
                            checked = homeCollectionVisible,
                            onCheckedChange = onHomeCollectionVisibilityChange
                        )
                    },
                    modifier = Modifier.padding(horizontal = AppSpacing.settingsListItemOuterInset)
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f))
                AppModule.entries.filter { it != AppModule.ALL }.forEach { module ->
                    ListItem(
                        headlineContent = { Text(module.title) },
                        supportingContent = { Text("Показывать подборки в этом модуле") },
                        trailingContent = {
                            Switch(checked = module in collectionVisibleModules, onCheckedChange = { onCollectionVisibilityChange(module, it) })
                        },
                        modifier = Modifier.padding(horizontal = AppSpacing.settingsListItemOuterInset)
                    )
                }
            } else {
                SettingsNavigationItem(
                    title = "Главная страница",
                    description = "Расположение кнопок и действие кнопки «+»",
                    icon = NoteIcon.Home,
                    showChevron = true,
                    onClick = { homePageVisible = true }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f))
                SettingsNavigationItem(
                    title = "Персонализация",
                    description = "Темы и цвета интерфейса и заметок",
                    icon = NoteIcon.Palette,
                    showChevron = true,
                    onClick = { personalizationVisible = true }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f))
                SettingsNavigationItem(
                    title = "Поведение карточек",
                    description = "Интерактивность и состав данных на карточках",
                    icon = NoteIcon.Modules,
                    showChevron = true,
                    onClick = { cardBehaviorVisible = true }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f))
                SettingsNavigationItem(
                    title = "Подборки по модулям",
                    description = "Настройка отображения подборок в каждом модуле",
                    icon = NoteIcon.Folder,
                    showChevron = true,
                    onClick = { moduleCollectionsVisible = true }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f))
                SettingsNavigationItem(
                    title = "Хранение",
                    description = "Архив, корзина и срок хранения удалённых записей",
                    icon = NoteIcon.Archive,
                    showChevron = true,
                    onClick = { storageVisible = true }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f))
                ListItem(headlineContent = { Text("Данные", style = AppTypography.emphasisBodyStyle()) }, modifier = Modifier.padding(horizontal = AppSpacing.settingsListItemOuterInset))
                SettingsNavigationItem(
                    title = "Экспорт заметок",
                    description = "Записи, архив, корзина и данные восстановления в JSON-файл",
                    icon = NoteIcon.Upload,
                    onClick = onExport
                )
                SettingsNavigationItem(
                    title = "Импорт заметок",
                    description = "Добавить заметки из резервной копии",
                    icon = NoteIcon.Download,
                    onClick = onImport
                )
            }
        }
    }
}


@Composable
private fun SettingsNavigationItem(
    title: String,
    description: String,
    icon: NoteIcon,
    showChevron: Boolean = false,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        leadingContent = {
            Box(
                modifier = Modifier.fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                AppIcon(icon, contentDescription = null)
            }
        },
        trailingContent = if (showChevron) {
            { SettingsChevron() }
        } else {
            null
        },
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = AppSpacing.settingsListItemOuterInset)
    )
}

@Composable
private fun SettingsChevron() {
    Box(
        modifier = Modifier.size(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun CompactOpacitySlider(value: Float, onValueChange: (Float) -> Unit) {
    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val activeTrack = MaterialTheme.colorScheme.primary
    val inactiveTrack = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
    val updateAt: (Float, Float) -> Unit = { x, width ->
        if (width > 0f) latestOnValueChange((x / width).coerceIn(0f, 1f))
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.screenHorizontal, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Прозрачность", style = AppTypography.sectionLabelStyle(), color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(AppSpacing.sm))
        Canvas(
            modifier = Modifier.weight(1f).height(28.dp)
                .pointerInput(Unit) {
                    detectTapGestures { point -> updateAt(point.x, size.width.toFloat()) }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { point -> updateAt(point.x, size.width.toFloat()) },
                        onDrag = { change, _ ->
                            change.consume()
                            updateAt(change.position.x, size.width.toFloat())
                        }
                    )
                }
        ) {
            val inset = 4.dp.toPx()
            val start = inset
            val end = (size.width - inset).coerceAtLeast(start)
            val centerY = size.height / 2f
            val thumbX = start + (end - start) * value.coerceIn(0f, 1f)
            drawLine(
                color = inactiveTrack,
                start = Offset(start, centerY),
                end = Offset(end, centerY),
                strokeWidth = 2.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            drawLine(
                color = activeTrack,
                start = Offset(start, centerY),
                end = Offset(thumbX, centerY),
                strokeWidth = 2.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            drawCircle(activeTrack, radius = 4.dp.toPx(), center = Offset(thumbX, centerY))
        }
        Spacer(Modifier.width(AppSpacing.xs))
        Text(
            "${(value.coerceIn(0f, 1f) * 100f).roundToInt()}%",
            style = AppTypography.metadataStyle(),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeColorEditorScreen(
    title: String, color: Color, resetColor: Color, topBarColor: Color, onBack: () -> Unit,
    onColorChange: (Color) -> Unit, onReset: () -> Unit
) {
    var hex by remember(title) {
        mutableStateOf(String.format("%02X%02X%02X", (color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt()))
    }
    val parsedColor = remember(hex) {
        try { if (hex.length == 6) Color(android.graphics.Color.parseColor("#$hex")) else null }
        catch (_: Exception) { null }
    }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
        AppPageTopBar(
            backgroundColor = topBarColor,
            title = { Text(title, style = AppTypography.screenTitleStyle()) }
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).appPageContentInsets(),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                Box(Modifier.size(52.dp).background(parsedColor ?: color, RoundedCornerShape(10.dp)))
                Column {
                    Text(title, style = AppTypography.sectionHeadingStyle())
                    Text("Предпросмотр цвета", color = MaterialTheme.colorScheme.onSurfaceVariant, style = AppTypography.helperStyle())
                }
            }
            Text("Палитра", style = AppTypography.emphasisBodyStyle())
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                colorPickerPresets.chunked(6).forEach { rowColors ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        rowColors.forEach { preset ->
                            val isCurrent = parsedColor == preset
                            Box(Modifier.size(42.dp).background(preset, RoundedCornerShape(9.dp))
                                .then(if (isCurrent) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(9.dp)) else Modifier)
                                .clickable {
                                    hex = String.format("%02X%02X%02X", (preset.red * 255).toInt(), (preset.green * 255).toInt(), (preset.blue * 255).toInt())
                                    onColorChange(preset)
                                })
                        }
                    }
                }
            }
            Text("HEX-код", style = AppTypography.emphasisBodyStyle())
            OutlinedTextField(value = hex,
                onValueChange = { hex = it.filter { c -> c in "0123456789abcdefABCDEF" }.take(6).uppercase() },
                modifier = Modifier.fillMaxWidth(), singleLine = true, prefix = { Text("#") }, placeholder = { Text("RRGGBB") })
            Button(onClick = { parsedColor?.let(onColorChange) }, enabled = parsedColor != null, modifier = Modifier.fillMaxWidth()) {
                Text("Применить")
            }
            TextButton(onClick = {
                hex = colorToHex(resetColor).takeLast(6)
                onReset()
            }, modifier = Modifier.fillMaxWidth()) { Text("Вернуть стандартный цвет") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteColorEditorScreen(
    index: Int,
    color: Color,
    topBarColor: Color,
    onBack: () -> Unit,
    onColorChange: (Color) -> Unit
) {
    var hex by remember(index) {
        mutableStateOf(String.format("%02X%02X%02X", (color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt()))
    }
    val parsedColor = remember(hex) {
        try { if (hex.length == 6) Color(android.graphics.Color.parseColor("#$hex")) else null }
        catch (_: Exception) { null }
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppPageTopBar(
                backgroundColor = topBarColor,
                title = { Text("Изменение цвета", style = AppTypography.screenTitleStyle()) }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).appPageContentInsets(),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                Box(Modifier.size(52.dp).background(parsedColor ?: color, RoundedCornerShape(10.dp)))
                Column {
                    Text(listOf("Бирюзовый", "Оранжевый", "Жёлтый", "Зелёный", "Розовый", "Фиолетовый")[index], style = AppTypography.sectionHeadingStyle())
                    Text("Предпросмотр цвета", color = MaterialTheme.colorScheme.onSurfaceVariant, style = AppTypography.helperStyle())
                }
            }
            Text("Палитра", style = AppTypography.emphasisBodyStyle())
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                colorPickerPresets.chunked(6).forEach { rowColors ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        rowColors.forEach { preset ->
                            val isCurrent = parsedColor == preset
                            Box(Modifier.size(42.dp).background(preset, RoundedCornerShape(9.dp))
                                .then(if (isCurrent) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(9.dp)) else Modifier)
                                .clickable {
                                    hex = String.format("%02X%02X%02X", (preset.red * 255).toInt(), (preset.green * 255).toInt(), (preset.blue * 255).toInt())
                                    onColorChange(preset)
                                })
                        }
                    }
                }
            }
            Text("HEX-код", style = AppTypography.emphasisBodyStyle())
            OutlinedTextField(value = hex,
                onValueChange = { value -> hex = value.filter { it in "0123456789abcdefABCDEF" }.take(6).uppercase() },
                modifier = Modifier.fillMaxWidth(), singleLine = true, prefix = { Text("#") }, placeholder = { Text("RRGGBB") })
            Button(onClick = { parsedColor?.let(onColorChange) }, enabled = parsedColor != null, modifier = Modifier.fillMaxWidth()) {
                Text("Применить")
            }
        }
    }
}

