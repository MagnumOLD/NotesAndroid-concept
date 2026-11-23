package com.magnumold.notes

import android.app.Activity
import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.style.TextOverflow
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
    addButtonColor: androidx.compose.ui.graphics.Color,
    noteColors: List<androidx.compose.ui.graphics.Color>,
    dark: Boolean,
    onTimersChange: (List<KitchenTimer>) -> Unit,
    onSettingsChange: (KitchenTimerSettings) -> Unit,
    onRefresh: () -> Unit,
    initialEditingTimerId: Long? = null,
    onInitialEditConsumed: () -> Unit = {},
    onEditorExit: () -> Unit = {}
) {
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var editingTimer by remember { mutableStateOf<KitchenTimer?>(null) }
    var editingIsNew by remember { mutableStateOf(false) }
    var settingsVisible by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) onRefresh()
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == KitchenTimerContract.ACTION_STATE_CHANGED) onRefresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        val filter = IntentFilter(KitchenTimerContract.ACTION_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter)
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    LaunchedEffect(Unit) {
        onRefresh()
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    LaunchedEffect(initialEditingTimerId) {
        val requestedId = initialEditingTimerId ?: return@LaunchedEffect
        editingTimer = timers.firstOrNull { it.id == requestedId }
        editingIsNew = false
        onInitialEditConsumed()
    }

    fun closeEditor() {
        editingTimer = null
        editingIsNew = false
        onEditorExit()
    }

    if (editingTimer != null) {
        KitchenTimerEditScreen(
            timer = editingTimer!!,
            defaultSoundUri = settings.defaultSoundUri,
            topBarColor = topBarColor,
            onBack = ::closeEditor,
            onSave = { updated ->
                val next = if (editingIsNew) timers + updated
                    else timers.map { if (it.id == updated.id) updated else it }
                onTimersChange(next)
                closeEditor()
            }
        )
        return
    }

    if (settingsVisible) {
        KitchenTimerSettingsScreen(
            settings = settings,
            topBarColor = topBarColor,
            onBack = { settingsVisible = false },
            onSave = {
                onSettingsChange(it)
                settingsVisible = false
            }
        )
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppPageTopBar(
                backgroundColor = topBarColor,
                title = { Text("Таймеры", style = AppTypography.screenTitleStyle()) },
                actions = {
                    IconButton(onClick = { settingsVisible = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "Настройки таймеров")
                    }
                }
            )
        },
        floatingActionButton = {
            PrimaryAddButton(
                color = addButtonColor,
                onClick = {
                    val nextId = newRecordId()
                    val created = KitchenTimerOperations.create(nextId)
                    editingTimer = created
                    editingIsNew = true
                },
                onLongClick = {},
                modifier = Modifier
                    .size(PrimaryAddButtonDimensions.diameter)
                    .offset(y = PrimaryAddButtonDimensions.verticalOffset)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                top = AppSpacing.md,
                bottom = 88.dp
            ),
        ) {
            items(timers, key = { it.id }) { timer ->
                KitchenTimerCard(
                    timer = timer,
                    noteColors = noteColors,
                    dark = dark,
                    onChangeColor = { colorIndex ->
                        onTimersChange(timers.map {
                            if (it.id == timer.id) it.copy(colorIndex = colorIndex) else it
                        })
                    },
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
                    onEdit = { editingTimer = timer; editingIsNew = false },
                    onDelete = { onTimersChange(timers.filterNot { it.id == timer.id }) }
                )
            }

        }
    }

}

@Composable
private fun KitchenTimerCard(
    timer: KitchenTimer,
    noteColors: List<androidx.compose.ui.graphics.Color>,
    dark: Boolean,
    onChangeColor: (Int) -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onReset: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    NoteRow(
        note = timer.toRecord(),
        dark = dark,
        noteColors = noteColors,
        pinned = timer.pinned,
        selected = false,
        onClick = onEdit,
        onLongPress = {},
        colorSwipeEnabled = true,
        onChangeColor = onChangeColor,
        interactiveCards = true,
        onQuickAction = if (timer.status == KitchenTimerStatus.RUNNING) onPause else onStart,
        onResetAction = onReset,
        onDeleteRequest = onDelete,
        showTimerDragHandle = false
    )
}

@Composable
private fun KitchenTimerEditScreen(
    timer: KitchenTimer,
    defaultSoundUri: String?,
    topBarColor: androidx.compose.ui.graphics.Color,
    onBack: () -> Unit,
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

    BackHandler(onBack = onBack)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppPageTopBar(
                backgroundColor = topBarColor,
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Outlined.ArrowBack, contentDescription = "Назад")
                        }
                        Text("Настройка таймера", style = AppTypography.screenTitleStyle())
                    }
                }
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = AppSpacing.screenHorizontal, vertical = AppSpacing.md)
            ) {
                Button(
                    onClick = {
                        val now = System.currentTimeMillis()
                        val durationChanged = duration != timer.durationSeconds
                        val changed = timer.copy(
                            title = title.trim(),
                            durationSeconds = duration,
                            remainingSeconds = if (timer.status == KitchenTimerStatus.PAUSED) {
                                if (durationChanged) duration else timer.remainingSeconds
                            } else null,
                            endsAtMillis = if (timer.status == KitchenTimerStatus.RUNNING) {
                                if (durationChanged) TimerOperations.startAt(now, duration) else timer.endsAtMillis
                            } else null,
                            status = if (timer.status == KitchenTimerStatus.FINISHED && durationChanged) {
                                KitchenTimerStatus.READY
                            } else timer.status,
                            soundUri = soundUri,
                            alertStartedAtMillis = if (timer.status == KitchenTimerStatus.FINISHED && durationChanged) {
                                null
                            } else timer.alertStartedAtMillis
                        )
                        onSave(changed.normalized())
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = valid
                ) { Text("Сохранить") }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize()
                .padding(padding)
                .appPageHorizontalInsets()
                .verticalScroll(rememberScrollState())
                .padding(vertical = AppSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Название (необязательно)") }
            )
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                Text("Время", style = AppTypography.emphasisBodyStyle())
                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                    TimerTimeField("Часы", hours, { hours = it }, Modifier.weight(1f))
                    TimerTimeField("Мин", minutes, { minutes = it }, Modifier.weight(1f))
                    TimerTimeField("Сек", seconds, { seconds = it }, Modifier.weight(1f))
                }
                if (!valid) {
                    Text("Укажите время от 1 секунды до 24 часов.", color = MaterialTheme.colorScheme.error)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                Text("Звук", style = AppTypography.emphasisBodyStyle())
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
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)?.toString()
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
            }
        }
    }
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
private fun KitchenTimerSettingsScreen(
    settings: KitchenTimerSettings,
    topBarColor: androidx.compose.ui.graphics.Color,
    onBack: () -> Unit,
    onSave: (KitchenTimerSettings) -> Unit
) {
    var mode by remember { mutableStateOf(settings.alertDurationMode) }
    var customMinutes by remember { mutableStateOf((settings.customAlertDurationSeconds / 60).toString()) }
    var customSeconds by remember { mutableStateOf((settings.customAlertDurationSeconds % 60).toString()) }
    var defaultSoundUri by remember { mutableStateOf(settings.defaultSoundUri) }
    val context = LocalContext.current
    val soundPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            defaultSoundUri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)?.toString()
        }
    }
    val alarmManager = context.getSystemService(AlarmManager::class.java)
    val exactAlarmsAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
    val customDuration = (customMinutes.toIntOrNull() ?: 0) * 60 +
        (customSeconds.toIntOrNull() ?: 0)
    val validCustomDuration = customDuration in 1..3600

    BackHandler(onBack = onBack)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppPageTopBar(
                backgroundColor = topBarColor,
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Outlined.ArrowBack, contentDescription = "Назад")
                        }
                        Text("Настройки таймеров", style = AppTypography.screenTitleStyle())
                    }
                }
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = AppSpacing.screenHorizontal, vertical = AppSpacing.md)
            ) {
                Button(
                    onClick = {
                        onSave(KitchenTimerSettings(mode, customDuration.coerceIn(1, 3600), defaultSoundUri))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = mode != TimerAlertDurationMode.CUSTOM || validCustomDuration
                ) { Text("Сохранить") }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize()
                .padding(padding)
                .appPageContentInsets()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.lg)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                Text("Длительность сигнала", style = AppTypography.emphasisBodyStyle())
                listOf(
                    TimerAlertDurationMode.TEN_SECONDS to "10 секунд",
                    TimerAlertDurationMode.THIRTY_SECONDS to "30 секунд",
                    TimerAlertDurationMode.ONE_MINUTE to "1 минута",
                    TimerAlertDurationMode.CUSTOM to "Вручную"
                ).forEach { (value, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = mode == value, onClick = { mode = value })
                        Text(label)
                    }
                }
                if (mode == TimerAlertDurationMode.CUSTOM) {
                    Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                        OutlinedTextField(
                            value = customMinutes,
                            onValueChange = { if (it.length <= 2 && it.all(Char::isDigit)) customMinutes = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            label = { Text("Минуты") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        OutlinedTextField(
                            value = customSeconds,
                            onValueChange = { if (it.length <= 2 && it.all(Char::isDigit)) customSeconds = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            label = { Text("Секунды") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                    if (!validCustomDuration) {
                        Text(
                            "Укажите длительность от 1 секунды до 60 минут.",
                            color = MaterialTheme.colorScheme.error,
                            style = AppTypography.helperStyle()
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f))

            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                Text("Общий звук", style = AppTypography.emphasisBodyStyle())
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
                    Text(if (defaultSoundUri == null) "Выбрать звук" else "Изменить звук")
                }
                val previewUri = defaultSoundUri
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)?.toString()
                if (previewUri != null) {
                    TextButton(onClick = {
                        runCatching {
                            RingtoneManager.getRingtone(context, Uri.parse(previewUri))?.play()
                        }
                    }) { Text("Прослушать звук") }
                }
                if (defaultSoundUri != null) {
                    TextButton(onClick = { defaultSoundUri = null }) {
                        Text("Системный звук по умолчанию")
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f))

            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                Text("Точность срабатывания", style = AppTypography.emphasisBodyStyle())
                if (exactAlarmsAllowed) {
                    Text(
                        "Разрешение на точные сигналы включено.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = AppTypography.helperStyle()
                    )
                } else {
                    Text(
                        "Для более точного срабатывания разрешите приложению точные будильники.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = AppTypography.helperStyle()
                    )
                    OutlinedButton(
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                    .setData(Uri.parse("package:" + context.packageName))
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Разрешить точные сигналы") }
                }
            }
            Spacer(Modifier.height(AppSpacing.md))
        }
    }
}
