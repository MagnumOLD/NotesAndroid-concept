package com.magnumold.notes

import android.app.Activity
import android.app.AlarmManager
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
internal fun KitchenTimersScreen(
    timers: List<KitchenTimer>,
    settings: KitchenTimerSettings,
    topBarColor: androidx.compose.ui.graphics.Color,
    onTimersChange: (List<KitchenTimer>) -> Unit,
    onSettingsChange: (KitchenTimerSettings) -> Unit,
    onRefresh: () -> Unit
) {
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var editingTimerId by remember { mutableStateOf<Long?>(null) }
    var settingsVisible by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) onRefresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(250L)
        }
    }

    val editingTimer = timers.firstOrNull { it.id == editingTimerId }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppPageTopBar(
                backgroundColor = topBarColor,
                title = { Text("Таймеры", style = AppTypography.screenTitleStyle()) },
                actions = {
                    IconButton(onClick = { settingsVisible = true }) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Настройки таймеров")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).appPageHorizontalInsets(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                top = AppSpacing.md,
                bottom = AppSpacing.xl
            ),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
            items(timers, key = { it.id }) { timer ->
                KitchenTimerCard(
                    timer = timer,
                    nowMillis = nowMillis,
                    onStart = {
                        onTimersChange(timers.map {
                            if (it.id == timer.id) KitchenTimerOperations.start(it, nowMillis) else it
                        })
                    },
                    onPause = {
                        onTimersChange(timers.map {
                            if (it.id == timer.id) KitchenTimerOperations.pause(it, nowMillis) else it
                        })
                    },
                    onReset = {
                        onTimersChange(timers.map {
                            if (it.id == timer.id) KitchenTimerOperations.reset(it) else it
                        })
                    },
                    onEdit = { editingTimerId = timer.id },
                    onDelete = { onTimersChange(timers.filterNot { it.id == timer.id }) }
                )
            }
            item {
                OutlinedButton(
                    onClick = {
                        val nextId = maxOf(
                            System.currentTimeMillis().coerceAtLeast(1L),
                            (timers.maxOfOrNull { it.id } ?: 0L) + 1L
                        )
                        onTimersChange(timers + KitchenTimerOperations.create(nextId))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Spacer(Modifier.width(AppSpacing.sm))
                    Text("Добавить таймер")
                }
            }
        }
    }

    if (editingTimer != null) {
        KitchenTimerEditDialog(
            timer = editingTimer,
            defaultSoundUri = settings.defaultSoundUri,
            onDismiss = { editingTimerId = null },
            onSave = { updated ->
                onTimersChange(timers.map { if (it.id == updated.id) updated else it })
                editingTimerId = null
            }
        )
    }
    if (settingsVisible) {
        KitchenTimerSettingsDialog(
            settings = settings,
            onDismiss = { settingsVisible = false },
            onSave = {
                onSettingsChange(it)
                settingsVisible = false
            }
        )
    }
}

@Composable
private fun KitchenTimerCard(
    timer: KitchenTimer,
    nowMillis: Long,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onReset: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val remaining = KitchenTimerOperations.remainingSeconds(timer, nowMillis)
    val statusText = when (timer.status) {
        KitchenTimerStatus.READY -> "Готов к запуску"
        KitchenTimerStatus.RUNNING -> "Работает"
        KitchenTimerStatus.PAUSED -> "На паузе"
        KitchenTimerStatus.FINISHED -> "Время вышло"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(AppSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    if (timer.title.isNotBlank()) {
                        Text(timer.title, style = AppTypography.sectionHeadingStyle(), fontWeight = FontWeight.Medium)
                    }
                    Text(
                        statusText,
                        style = AppTypography.helperStyle(),
                        color = if (timer.status == KitchenTimerStatus.FINISHED) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Outlined.Edit, contentDescription = "Настроить таймер")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Удалить таймер")
                }
            }
            Text(
                KitchenTimerOperations.format(remaining),
                style = MaterialTheme.typography.displaySmall,
                color = if (timer.status == KitchenTimerStatus.FINISHED) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (timer.status) {
                    KitchenTimerStatus.RUNNING -> Button(onClick = onPause, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Pause, contentDescription = null)
                        Spacer(Modifier.width(AppSpacing.xs))
                        Text("Пауза")
                    }
                    KitchenTimerStatus.FINISHED -> Button(onClick = onReset, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.RestartAlt, contentDescription = null)
                        Spacer(Modifier.width(AppSpacing.xs))
                        Text("Сбросить")
                    }
                    else -> Button(onClick = onStart, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(AppSpacing.xs))
                        Text(if (timer.status == KitchenTimerStatus.PAUSED) "Продолжить" else "Запустить")
                    }
                }
                if (timer.status != KitchenTimerStatus.FINISHED) {
                    IconButton(onClick = onReset) {
                        Icon(Icons.Outlined.RestartAlt, contentDescription = "Сбросить")
                    }
                }
            }
        }
    }
}

@Composable
private fun KitchenTimerEditDialog(
    timer: KitchenTimer,
    defaultSoundUri: String?,
    onDismiss: () -> Unit,
    onSave: (KitchenTimer) -> Unit
) {
    var title by remember(timer.id) { mutableStateOf(timer.title) }
    var hours by remember(timer.id) { mutableStateOf((timer.durationSeconds / 3600L).toString()) }
    var minutes by remember(timer.id) { mutableStateOf(((timer.durationSeconds % 3600L) / 60L).toString()) }
    var seconds by remember(timer.id) { mutableStateOf((timer.durationSeconds % 60L).toString()) }
    var soundUri by remember(timer.id) { mutableStateOf(timer.soundUri) }
    val context = LocalContext.current
    val soundPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            soundUri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)?.toString()
        }
    }
    val duration = (hours.toLongOrNull() ?: 0L) * 3600L +
        (minutes.toLongOrNull() ?: 0L) * 60L + (seconds.toLongOrNull() ?: 0L)
    val valid = duration in 1L..TimerOperations.MAX_DURATION_SECONDS

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Настройка таймера") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Название (необязательно)") }
                )
                Text("Время", style = AppTypography.emphasisBodyStyle())
                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                    TimerTimeField("Часы", hours, { hours = it }, Modifier.weight(1f))
                    TimerTimeField("Мин", minutes, { minutes = it }, Modifier.weight(1f))
                    TimerTimeField("Сек", seconds, { seconds = it }, Modifier.weight(1f))
                }
                OutlinedButton(
                    onClick = {
                        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Звук таймера")
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                            soundUri?.let { putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(it)) }
                        }
                        soundPicker.launch(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.VolumeUp, contentDescription = null)
                    Spacer(Modifier.width(AppSpacing.sm))
                    Text(if (soundUri == null) "Выбрать звук" else "Изменить звук")
                }
                val previewUri = soundUri ?: defaultSoundUri
                if (previewUri != null) {
                    TextButton(onClick = {
                        runCatching {
                            RingtoneManager.getRingtone(context, Uri.parse(previewUri))?.play()
                        }
                    }) { Text("Прослушать звук") }
                }
                if (soundUri != null) {
                    TextButton(onClick = { soundUri = null }) { Text("Использовать общий звук") }
                }
                if (!valid) {
                    Text("Укажите время от 1 секунды до 24 часов.", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val now = System.currentTimeMillis()
                    val changed = timer.copy(
                        title = title.trim(),
                        durationSeconds = duration,
                        remainingSeconds = if (timer.status == KitchenTimerStatus.PAUSED) duration else null,
                        endsAtMillis = if (timer.status == KitchenTimerStatus.RUNNING) TimerOperations.startAt(now, duration) else null,
                        status = if (timer.status == KitchenTimerStatus.FINISHED) KitchenTimerStatus.READY else timer.status,
                        soundUri = soundUri,
                        alertStartedAtMillis = if (timer.status == KitchenTimerStatus.FINISHED) null else timer.alertStartedAtMillis
                    )
                    onSave(changed.normalized())
                },
                enabled = valid
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun TimerTimeField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> if (input.length <= 3 && input.all(Char::isDigit)) onValueChange(input) },
        modifier = modifier,
        singleLine = true,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

@Composable
private fun KitchenTimerSettingsDialog(
    settings: KitchenTimerSettings,
    onDismiss: () -> Unit,
    onSave: (KitchenTimerSettings) -> Unit
) {
    var mode by remember { mutableStateOf(settings.alertDurationMode) }
    var customMinutes by remember { mutableStateOf((settings.customAlertDurationSeconds / 60).coerceAtLeast(1).toString()) }
    var defaultSoundUri by remember { mutableStateOf(settings.defaultSoundUri) }
    val context = LocalContext.current
    val soundPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            defaultSoundUri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)?.toString()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Настройки сигналов") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                val alarmManager = context.getSystemService(AlarmManager::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                    Text(
                        "Для более точного срабатывания разрешите приложению точные будильники.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = AppTypography.helperStyle()
                    )
                    TextButton(onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                .setData(Uri.parse("package:${context.packageName}"))
                        )
                    }) { Text("Разрешить точные сигналы") }
                }
                listOf(
                    TimerAlertDurationMode.TEN_SECONDS to "10 секунд",
                    TimerAlertDurationMode.THIRTY_SECONDS to "30 секунд",
                    TimerAlertDurationMode.ONE_MINUTE to "1 минута",
                    TimerAlertDurationMode.CUSTOM to "Вручную"
                ).forEach { (value, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = mode == value, onClick = { mode = value })
                        Text(label)
                    }
                }
                if (mode == TimerAlertDurationMode.CUSTOM) {
                    OutlinedTextField(
                        value = customMinutes,
                        onValueChange = { if (it.length <= 3 && it.all(Char::isDigit)) customMinutes = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Продолжительность, минут") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
                OutlinedButton(
                    onClick = {
                        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Общий звук таймера")
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                            defaultSoundUri?.let { putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(it)) }
                        }
                        soundPicker.launch(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.VolumeUp, contentDescription = null)
                    Spacer(Modifier.width(AppSpacing.sm))
                    Text(if (defaultSoundUri == null) "Выбрать общий звук" else "Изменить общий звук")
                }
                if (defaultSoundUri != null) {
                    TextButton(onClick = {
                        runCatching {
                            RingtoneManager.getRingtone(context, Uri.parse(defaultSoundUri))?.play()
                        }
                    }) { Text("Прослушать общий звук") }
                    TextButton(onClick = { defaultSoundUri = null }) { Text("Системный звук по умолчанию") }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val minutes = customMinutes.toIntOrNull() ?: 1
                    onSave(KitchenTimerSettings(mode, minutes.coerceIn(1, 60) * 60, defaultSoundUri))
                },
                enabled = mode != TimerAlertDurationMode.CUSTOM || (customMinutes.toIntOrNull() ?: 0) in 1..60
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
