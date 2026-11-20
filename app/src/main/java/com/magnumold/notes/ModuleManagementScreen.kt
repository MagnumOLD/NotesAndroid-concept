package com.magnumold.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun ModuleManagementScreen(
    configuration: ModuleConfiguration,
    onConfigurationChange: (ModuleConfiguration) -> Unit,
    themeColors: CustomThemeColors
) {
    val orderedModules = configuration.normalized().order

    AppPageScaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppPageTopBar(
                backgroundColor = themeColors.topBar,
                title = { Text("Управление модулями", style = AppTypography.screenTitleStyle()) }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(
                start = AppSpacing.screenHorizontal,
                end = AppSpacing.screenHorizontal,
                top = AppSpacing.md,
                bottom = 28.dp
            )
        ) {
            item(key = "module_management_description") {
                Text(
                    "Выберите, какие модули должны быть доступны. Отключение не удаляет созданные записи.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = AppSpacing.md)
                )
            }
            items(orderedModules, key = { it.name }) { module ->
                val enabled = module in configuration.enabled
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f).padding(vertical = 12.dp)
                        ) {
                            Text(
                                module.title,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                module.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = enabled,
                            onCheckedChange = { checked ->
                                onConfigurationChange(
                                    configuration.copy(
                                        enabled = if (checked) configuration.enabled + module
                                        else configuration.enabled - module
                                    )
                                )
                            }
                        )
                    }
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.42f),
                        thickness = 0.7.dp
                    )
                }
            }
        }
    }
}
