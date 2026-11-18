package com.magnumold.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    var now by remember(note.id) { mutableLongStateOf(System.currentTimeMillis()) }
    var durationInput by remember(note.id) { mutableStateOf(note.timerDurationSeconds.toString()) }
    val running = note.timerEndsAtMillis != null
    val remaining = if (running) TimerOperations.remainingSeconds(note.timerEndsAtMillis!!, now)
    else note.timerRemainingSeconds ?: note.timerDurationSeconds

    LaunchedEffect(note.timerEndsAtMillis) {
        val end = note.timerEndsAtMillis ?: return@LaunchedEffect
        while (true) {
            now = System.currentTimeMillis()
            if (TimerOperations.remainingSeconds(end, now) <= 0L) {
                onChange(note.copy(timerEndsAtMillis = null, timerRemainingSeconds = 0L, status = RecordStatus.COMPLETED))
                break
            }
            delay(250L)
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
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Назад")
                    }
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
                        val left = if (note.timerRemainingSeconds == null) duration
                            else note.timerRemainingSeconds.coerceIn(1L, duration)
                        val start = System.currentTimeMillis()
                        onChange(note.copy(
                            timerDurationSeconds = duration,
                            timerRemainingSeconds = null,
                            timerEndsAtMillis = TimerOperations.startAt(start, left),
                            status = RecordStatus.IN_PROGRESS
                        ))
                        now = start
                    },
                    enabled = !running && (remaining > 0L || durationInput.toLongOrNull() != null)
                ) { Text(if (remaining == 0L) "Запустить заново" else "Старт") }
                if (running) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val left = TimerOperations.remainingSeconds(note.timerEndsAtMillis!!, System.currentTimeMillis())
                            onChange(note.copy(timerEndsAtMillis = null, timerRemainingSeconds = left, status = RecordStatus.PAUSED))
                        }
                    ) { Text("Пауза") }
                } else {
                    TextButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onChange(note.copy(timerEndsAtMillis = null, timerRemainingSeconds = null, status = RecordStatus.WAITING))
                            durationInput = note.timerDurationSeconds.toString()
                        }
                    ) { Text("Сбросить") }
                }
            }
            Spacer(Modifier.weight(1f))
        }
    }
}
