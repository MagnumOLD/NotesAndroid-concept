package com.magnumold.notes

import android.content.SharedPreferences
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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
}

internal class ModuleSettingsStore(private val preferences: SharedPreferences) {
    fun load(): ModuleConfiguration {
        val enabledNames = preferences.getStringSet(KEY_ENABLED, null)
        val orderNames = preferences.getString(KEY_ORDER, null)
        val enabled = enabledNames?.mapNotNull { runCatching { AppModule.valueOf(it) }.getOrNull() }
            ?.toSet() ?: ModuleConfiguration().enabled
        val order = orderNames?.split(",")?.mapNotNull { runCatching { AppModule.valueOf(it) }.getOrNull() }
            ?: ModuleConfiguration().order
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

@Composable
internal fun ModulesScreen(
    configuration: ModuleConfiguration,
    onConfigurationChange: (ModuleConfiguration) -> Unit,
    onOpenModule: (AppModule) -> Unit,
    onBack: () -> Unit,
    themeColors: CustomThemeColors
) {
    val ordered = configuration.normalized().order
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
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Назад")
                    }
                    Text("Модули", style = AppTypography.screenTitleStyle())
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Outlined.Tune, contentDescription = null)
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(
                start = AppSpacing.screenHorizontal,
                end = AppSpacing.screenHorizontal,
                top = AppSpacing.md,
                bottom = 28.dp
            ),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            item {
                Text(
                    "Выберите модуль. Порядок и доступность можно менять — отключение не удаляет данные.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = AppSpacing.sm)
                )
            }
            items(ordered, key = { it.name }) { module ->
                val enabled = module in configuration.enabled
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.medium,
                    tonalElevation = 1.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp).padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f).clickable(enabled = enabled) { onOpenModule(module) }
                                .padding(vertical = 4.dp)
                        ) {
                            Text(module.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                            Text(module.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row {
                                IconButton(
                                    onClick = {
                                        val index = ordered.indexOf(module)
                                        if (index > 0) {
                                            val moved = ordered.toMutableList()
                                            val item = moved.removeAt(index)
                                            moved.add(index - 1, item)
                                            onConfigurationChange(configuration.copy(order = moved))
                                        }
                                    },
                                    enabled = ordered.indexOf(module) > 0
                                ) { Icon(Icons.Outlined.ArrowUpward, contentDescription = "Переместить выше") }
                                IconButton(
                                    onClick = {
                                        val index = ordered.indexOf(module)
                                        if (index < ordered.lastIndex) {
                                            val moved = ordered.toMutableList()
                                            val item = moved.removeAt(index)
                                            moved.add(index + 1, item)
                                            onConfigurationChange(configuration.copy(order = moved))
                                        }
                                    },
                                    enabled = ordered.indexOf(module) < ordered.lastIndex
                                ) { Icon(Icons.Outlined.ArrowDownward, contentDescription = "Переместить ниже") }
                            }
                            Switch(
                                checked = enabled,
                                onCheckedChange = { checked ->
                                    onConfigurationChange(
                                        configuration.copy(enabled = if (checked) configuration.enabled + module else configuration.enabled - module)
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
