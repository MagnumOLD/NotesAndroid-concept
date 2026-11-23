package com.magnumold.notes

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
internal fun TimerRecordEditor(
    note: Note,
    topBarColor: Color,
    onBack: () -> Unit,
    onChange: (Note) -> Unit
) {
    AppBackHandler { onBack() }
    val context = LocalContext.current
    val exactAlarmsAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    val notificationsAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val latestNote by rememberUpdatedState(note)
    val latestOnChange by rememberUpdatedState(onChange)
    var now by remember(note.id) { mutableLongStateOf(System.currentTimeMillis()) }
    var durationInput by remember(note.id) { mutableStateOf(note.timerDurationSeconds.toString()) }
    val running = note.timerEndsAtMillis != null
    val remaining = RecordTimerOperations.remainingSeconds(note, now)

    LaunchedEffect(note.timerEndsAtMillis) {
        val end = note.timerEndsAtMillis ?: return@LaunchedEffect
        while (true) {
            now = System.currentTimeMillis()
            if (TimerOperations.remainingSeconds(end, now) <= 0L) {
                latestOnChange(RecordTimerOperations.reconcile(latestNote, System.currentTimeMillis()))
                break
            }
            delay(1_000L)
        }
    }

    AppPageScaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(color = topBarColor, contentColor = noteTextColor(topBarColor)) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.statusBars)
                        .padding(horizontal = AppSpacing.screenHorizontal, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Таймер", style = AppTypography.screenTitleStyle(), color = noteTextColor(topBarColor))
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
                .padding(horizontal = AppSpacing.screenHorizontal, vertical = AppSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppSpacing.lg)
        ) {
            OutlinedTextField(
                value = note.titleText,
                onValueChange = { onChange(note.copy(titleText = it)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Название") },
                placeholder = { Text("Например, перерыв") }
            )
            Spacer(Modifier.weight(1f))
            Text(
                TimerOperations.format(remaining),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!exactAlarmsAllowed) {
                Text(
                    "Без доступа к точным будильникам сигнал может прийти с задержкой.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                TextButton(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            context.startActivity(
                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                    .setData(Uri.parse("package:${context.packageName}"))
                            )
                        }
                    }
                ) { Text("Настроить точный сигнал") }
            }
            if (!notificationsAllowed) {
                Text(
                    "Уведомления отключены. Включите их, чтобы видеть окончание таймера.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                TextButton(
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        )
                    }
                ) { Text("Настроить уведомления") }
            }
            if (!running) {
                OutlinedTextField(
                    value = durationInput,
                    onValueChange = { value ->
                        if (value.length <= 5 && value.all { it.isDigit() }) durationInput = value
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Длительность, секунд") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            } else {
                Text("Таймер запущен", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val duration = durationInput.toLongOrNull()?.let { TimerOperations.normalizeDuration(it) }
                            ?: note.timerDurationSeconds
                        val start = System.currentTimeMillis()
                        onChange(RecordTimerOperations.start(
                            note.copy(timerDurationSeconds = duration),
                            start
                        ))
                        now = start
                    },
                    enabled = !running && (remaining > 0L || durationInput.toLongOrNull() != null)
                ) { Text(if (remaining == 0L) "Запустить заново" else "Старт") }
                if (running) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onChange(RecordTimerOperations.pause(note, System.currentTimeMillis()))
                        }
                    ) { Text("Пауза") }
                } else {
                    TextButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onChange(RecordTimerOperations.reset(note))
                            durationInput = note.timerDurationSeconds.toString()
                        }
                    ) { Text("Сбросить") }
                }
            }
            Spacer(Modifier.weight(1f))
        }
    }
}
