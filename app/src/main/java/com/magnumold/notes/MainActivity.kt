package com.magnumold.notes

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.res.Configuration
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider

class MainActivity : ComponentActivity() {
    private lateinit var recordsViewModel: NotesViewModel
    private lateinit var timeTrackingRepository: TimeTrackingRepository
    private lateinit var recoveryDataRepository: RecoveryDataRepository
    private var lastExternalTaskStatusUpdate: Long = 0L
    private var lastExternalTimerStateUpdate: Long = 0L
    private val taskStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (::recordsViewModel.isInitialized &&
                intent?.action == ProtectionTimerContract.ACTION_TASK_STATUS_CHANGED
            ) {
                val taskId = intent.getLongExtra(ProtectionTimerContract.EXTRA_CHANGED_TASK_ID, -1L)
                val status = intent.getStringExtra(ProtectionTimerContract.EXTRA_CHANGED_TASK_STATUS)
                    ?.let { runCatching { RecordStatus.valueOf(it) }.getOrNull() }
                if (taskId > 0L && status != null) {
                    recordsViewModel.applyExternalTaskStatus(taskId, status)
                    lastExternalTaskStatusUpdate = getPreferences(Context.MODE_PRIVATE)
                        .getLong(ExternalRecordUpdateKeys.TASK_STATUS_AT, lastExternalTaskStatusUpdate)
                }
                if (intent.getBooleanExtra(ProtectionTimerContract.EXTRA_TIMER_STATE_CHANGED, false)) {
                    recordsViewModel.refreshTimerStatesFromRepository()
                    lastExternalTimerStateUpdate = getPreferences(Context.MODE_PRIVATE)
                        .getLong(ExternalRecordUpdateKeys.TIMER_STATE_AT, lastExternalTimerStateUpdate)
                }
                if (::timeTrackingRepository.isInitialized) timeTrackingRepository.reload()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(ProtectionTimerContract.ACTION_TASK_STATUS_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(taskStatusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(taskStatusReceiver, filter)
        }
    }

    override fun onResume() {
        super.onResume()
        if (::recordsViewModel.isInitialized) {
            // The ViewModel is already the source of truth for in-app edits.
            // Re-reading preferences on every resume can replace a newer in-memory
            // snapshot with stale data while the debounced write is still pending.
            // Refresh only when the background service explicitly marks an update.
            val recordPreferences = getSharedPreferences(NotesRepository.PREFERENCES_NAME, Context.MODE_PRIVATE)
            val marker = recordPreferences.getLong(
                ExternalRecordUpdateKeys.TASK_STATUS_AT, lastExternalTaskStatusUpdate
            )
            if (marker > lastExternalTaskStatusUpdate) {
                val taskId = recordPreferences.getLong(ExternalRecordUpdateKeys.TASK_STATUS_ID, -1L)
                val status = recordPreferences.getString(ExternalRecordUpdateKeys.TASK_STATUS_VALUE, null)
                    ?.let { runCatching { RecordStatus.valueOf(it) }.getOrNull() }
                if (taskId > 0L && status != null) recordsViewModel.applyExternalTaskStatus(taskId, status)
                lastExternalTaskStatusUpdate = marker
            }
            val timerMarker = recordPreferences.getLong(
                ExternalRecordUpdateKeys.TIMER_STATE_AT, lastExternalTimerStateUpdate
            )
            if (timerMarker > lastExternalTimerStateUpdate) {
                recordsViewModel.refreshTimerStatesFromRepository()
                lastExternalTimerStateUpdate = timerMarker
            }
            RecordTimerScheduler(this).scheduleAll(
                HomeTimelineOperations.records(recordsViewModel.snapshot.value)
            )
        }
        if (::timeTrackingRepository.isInitialized) {
            timeTrackingRepository.reload()
            val currentTracking = timeTrackingRepository.load()
            val records = HomeTimelineOperations.records(recordsViewModel.snapshot.value)
            val reconciledTracking = syncWorkSessionsForTaskChanges(
                oldRoots = records,
                newRoots = records,
                currentState = currentTracking,
                nowMillis = System.currentTimeMillis()
            )
            if (reconciledTracking != currentTracking) timeTrackingRepository.save(reconciledTracking)
            timeTrackingRepository.load().protectionTimer?.let {
                ProtectionTimerScheduler(this).schedule(it)
            }
        }
    }

    override fun onStop() {
        try {
            if (::recordsViewModel.isInitialized) {
                try {
                    recordsViewModel.flush()
                } catch (error: Exception) {
                    // A storage failure must not abort Android's lifecycle callback.
                    android.util.Log.e("NotesAndroid", "Unable to flush records during onStop", error)
                }
            }
        } finally {
            try {
                unregisterReceiver(taskStatusReceiver)
            } finally {
                super.onStop()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST)
        }
        val preferences = getPreferences(Context.MODE_PRIVATE)
        val systemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val savedThemeMode = preferences.getString("theme_mode", null)
            ?.takeIf { it in listOf("system", "light", "dark", "custom") }
            ?: if (preferences.contains("dark_v2")) {
                if (preferences.getBoolean("dark_v2", systemDark)) "dark" else "light"
            } else "system"
        val themeSettings = ThemeSettingsStore(getSharedPreferences("notes_settings", Context.MODE_PRIVATE))
        val initialThemeColorSets = themeSettings.loadThemeColorSets()
        val initialThemeColors = themeColorsForMode(initialThemeColorSets, savedThemeMode, systemDark)
        val initialDark = when (savedThemeMode) {
            "system" -> systemDark
            "dark" -> true
            "custom" -> noteTextColor(initialThemeColors.background) == androidx.compose.ui.graphics.Color.White
            else -> false
        }
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(
            if (savedThemeMode == "custom") initialThemeColors.background.toArgbInt()
            else if (initialDark) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        ))
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val recordPreferences = getPreferences(Context.MODE_PRIVATE)
        val timeTrackingPreferences = getSharedPreferences("time_tracking", Context.MODE_PRIVATE)
        val repository = NotesRepository(recordPreferences)
        timeTrackingRepository = TimeTrackingRepository(timeTrackingPreferences)
        recoveryDataRepository = RecoveryDataRepository(recordPreferences, timeTrackingPreferences)
        lastExternalTaskStatusUpdate = recordPreferences.getLong(ExternalRecordUpdateKeys.TASK_STATUS_AT, 0L)
        lastExternalTimerStateUpdate = recordPreferences.getLong(ExternalRecordUpdateKeys.TIMER_STATE_AT, 0L)
        recordsViewModel = ViewModelProvider(
            this,
            NotesViewModelFactory(repository, timeTrackingRepository, recoveryDataRepository)
        )[NotesViewModel::class.java]
        setContent {
            NotesApp(
                activity = this@MainActivity,
                recordsViewModel = recordsViewModel,
                timeTrackingRepository = timeTrackingRepository,
                recoveryDataRepository = recoveryDataRepository,
                savedThemeMode = savedThemeMode,
                initialThemeColorSets = initialThemeColorSets
            )
        }
    }

    internal fun shareNote(note: Note) {
        val text = note.blocks.joinToString("\n\n") { (if (it.isChecklist) (if (it.checked) "☑ " else "☐ ") else "") + it.text }
        val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
        startActivity(Intent.createChooser(intent, "Поделиться заметкой"))
    }

    private companion object {
        const val NOTIFICATION_PERMISSION_REQUEST = 7433
    }
}
