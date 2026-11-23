package com.magnumold.notes

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager

internal class ProtectionTimerAlertService : Service() {
    private var ringtone: Ringtone? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var protectionAlertActive = false
    private var activeProtectionAlert: ProtectionTimer? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ProtectionTimerContract.ACTION_ALARM -> handleAlarm(startId)
            ProtectionTimerContract.ACTION_STOP_TRACKING -> handleStopTracking(intent, startId)
            ProtectionTimerContract.ACTION_CONTINUE_WORK -> handleContinueWork(intent, startId)
            else -> stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private fun handleAlarm(startId: Int) {
        // Duplicate alarm deliveries must not dismiss an alert already ringing.
        if (protectionAlertActive) return
        createNotificationChannel()
        if (!protectionAlertActive) {
            startForeground(NOTIFICATION_ID, buildNotification(null, null))
        }
        val repository = timeRepository()
        val storedState = repository.load()
        val pendingPauseTaskId = applyPendingProtectionTaskPause(repository, notesRepository())
        if (pendingPauseTaskId != null) sendTaskStatusChanged(pendingPauseTaskId, RecordStatus.PAUSED)
        val timer = storedState.protectionTimer
        if (timer == null) {
            // A process may be recreated while the alert notification is still
            // active. Restore its identity and foreground presentation instead
            // of treating the already-consumed alarm as an empty timer.
            val firedAlert = storedState.firedProtectionAlert
            if (firedAlert != null) {
                protectionAlertActive = true
                activeProtectionAlert = firedAlert
                if (firedAlert.soundEnabled) {
                    acquireWakeLock()
                    playAlarmSound(firedAlert.soundUri)
                }
                startForeground(NOTIFICATION_ID, buildNotification(firedAlert, actionPendingIntent(firedAlert)))
            } else {
                finishService(startId)
            }
            return
        }
        val now = System.currentTimeMillis()
        if (now < timer.endsAtMillis) {
            ProtectionTimerScheduler(this).schedule(timer)
            finishService(startId)
            return
        }

        var timerConsumed = false
        val updatedState = repository.update { state ->
            if (state.protectionTimer != timer) state
            else {
                timerConsumed = true
                completeProtectionTimerIfCurrent(state, timer, now)
            }
        }
        if (!timerConsumed) {
            // The timer was replaced or cancelled after this alarm was
            // delivered. Do not pause its task or show an obsolete alert.
            updatedState.protectionTimer?.let { ProtectionTimerScheduler(this).schedule(it) }
            finishService(startId)
            return
        }

        val pausedTaskId = applyPendingProtectionTaskPause(repository, notesRepository())
        if (pausedTaskId != null) sendTaskStatusChanged(pausedTaskId, RecordStatus.PAUSED)
        else sendTaskStatusChanged()

        protectionAlertActive = true
        activeProtectionAlert = timer
        if (timer.soundEnabled) {
            acquireWakeLock()
            playAlarmSound(timer.soundUri)
        }
        startForeground(NOTIFICATION_ID, buildNotification(timer, actionPendingIntent(timer)))
    }


    private fun handleStopTracking(intent: Intent, startId: Int) {
        val taskId = intent.getLongExtra(ProtectionTimerContract.EXTRA_TASK_ID, -1L)
        if (taskId <= 0L) {
            if (!protectionAlertActive) finishService(startId)
            return
        }
        var actionApplied = false
        timeRepository().update { state ->
            if (!isProtectionTimerActionCurrent(
                    activeProtectionAlert,
                    state.protectionTimer,
                    taskId,
                    intent.getLongExtraOrNull(ProtectionTimerContract.EXTRA_TIMER_STARTED_AT),
                    intent.getLongExtraOrNull(ProtectionTimerContract.EXTRA_TIMER_ENDS_AT),
                    state.firedProtectionAlert
                )
            ) state
            else {
                actionApplied = true
                stopWorkAfterProtectionTimer(state, taskId, System.currentTimeMillis())
            }
        }
        if (!actionApplied) {
            if (!protectionAlertActive) finishService(startId)
            return
        }
        sendTaskStatusChanged()
        stopAlertAndService(startId)
    }

    private fun handleContinueWork(intent: Intent, startId: Int) {
        val taskId = intent.getLongExtra(ProtectionTimerContract.EXTRA_TASK_ID, -1L)
        if (taskId <= 0L) {
            if (!protectionAlertActive) finishService(startId)
            return
        }
        val notes = notesRepository()
        // Resolve task context without mutating it. The notification identity
        // must be claimed atomically in the tracking repository before changing
        // the task status; otherwise a stale tap can race a timer replacement
        // and pause/resume a task even though its tracking action is rejected.
        val context = findTaskTrackingContext(notes.loadSnapshot().taskTrackingRoots(), taskId)
        var actionApplied = false
        timeRepository().update { state ->
            if (!isProtectionTimerActionCurrent(
                    activeProtectionAlert,
                    state.protectionTimer,
                    taskId,
                    intent.getLongExtraOrNull(ProtectionTimerContract.EXTRA_TIMER_STARTED_AT),
                    intent.getLongExtraOrNull(ProtectionTimerContract.EXTRA_TIMER_ENDS_AT),
                    state.firedProtectionAlert
                )
            ) state
            else {
                actionApplied = true
                if (context == null) {
                    clearProtectionTimerForTask(state, taskId)
                } else {
                    val parent = context.parent
                    val subtaskTitle = context.task.trackingDisplayTitle().takeIf { parent != null }
                    val taskTitle = (parent ?: context.task).trackingDisplayTitle()
                    resumeWorkAfterProtectionTimer(
                        state = state,
                        taskId = taskId,
                        parentTaskId = parent?.id,
                        taskTitle = taskTitle,
                        subtaskTitle = subtaskTitle,
                        nowMillis = System.currentTimeMillis()
                    )
                }
            }
        }
        if (!actionApplied) {
            if (!protectionAlertActive) finishService(startId)
            return
        }
        val taskUpdated = notes.updateTaskStatus(taskId, RecordStatus.IN_PROGRESS)
        if (taskUpdated) sendTaskStatusChanged(taskId, RecordStatus.IN_PROGRESS)
        else sendTaskStatusChanged()
        stopAlertAndService(startId)
    }

    private fun Intent.getLongExtraOrNull(name: String): Long? =
        if (hasExtra(name)) getLongExtra(name, 0L) else null

    private fun buildNotification(
        timer: ProtectionTimer?,
        actionIntent: PendingIntent?
    ): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        builder
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(if (timer == null) "Таймер защиты" else "Время вышло")
            .setContentText(
                if (timer?.subtaskTitle != null) "${timer.taskTitle} — ${timer.subtaskTitle}"
                else timer?.taskTitle ?: "Обработка сигнала"
            )
            .setCategory(Notification.CATEGORY_ALARM)
            .setPriority(Notification.PRIORITY_MAX)
            .setOngoing(true)
            .setShowWhen(false)

        if (timer != null && actionIntent != null) {
            val label = if (timer.endMode == ProtectionEndMode.SIGNAL_ONLY) {
                "Остановить учёт"
            } else {
                "Продолжить работу"
            }
            builder.addAction(android.R.drawable.ic_media_pause, label, actionIntent)
        }
        return builder.build()
    }

    private fun actionPendingIntent(timer: ProtectionTimer): PendingIntent {
        val action = if (timer.endMode == ProtectionEndMode.SIGNAL_ONLY) {
            ProtectionTimerContract.ACTION_STOP_TRACKING
        } else {
            ProtectionTimerContract.ACTION_CONTINUE_WORK
        }
        val intent = Intent(this, ProtectionTimerAlertService::class.java)
            .setAction(action)
            // PendingIntent identity must include the timer instance, not only
            // taskId: old and new alerts for one task must not share an action.
            .setData(Uri.parse("notesandroid://protection-timer-action/${timer.taskId}/${timer.startedAtMillis}/${timer.endsAtMillis}"))
            .putExtra(ProtectionTimerContract.EXTRA_TASK_ID, timer.taskId)
            .putExtra(ProtectionTimerContract.EXTRA_TASK_TITLE, timer.taskTitle)
            .putExtra(ProtectionTimerContract.EXTRA_SUBTASK_TITLE, timer.subtaskTitle)
            .putExtra(ProtectionTimerContract.EXTRA_END_MODE, timer.endMode.name)
            .putExtra(ProtectionTimerContract.EXTRA_TIMER_STARTED_AT, timer.startedAtMillis)
            .putExtra(ProtectionTimerContract.EXTRA_TIMER_ENDS_AT, timer.endsAtMillis)
        timer.parentTaskId?.let {
            intent.putExtra(ProtectionTimerContract.EXTRA_PARENT_TASK_ID, it)
        }
        val requestCode = timer.taskId.hashCode() + if (action == ProtectionTimerContract.ACTION_STOP_TRACKING) 10 else 20
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun playAlarmSound(uriString: String?) {
        stopRingtone()
        val uri = uriString?.let(Uri::parse) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        ringtone = RingtoneManager.getRingtone(this, uri)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) ringtone?.isLooping = true
        ringtone?.play()
    }

    private fun stopRingtone() {
        ringtone?.stop()
        ringtone = null
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(PowerManager::class.java)
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "NotesAndroid:ProtectionTimer"
        ).apply {
            setReferenceCounted(false)
            acquire(10L * 60L * 1_000L)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun recoverInterruptedBackupImport() = recoverBackupImportIfNeeded(this)

    private fun timeRepository(): TimeTrackingRepository {
        recoverInterruptedBackupImport()
        return TimeTrackingRepository(getSharedPreferences("time_tracking", MODE_PRIVATE))
    }

    private fun notesRepository(): NotesRepository {
        recoverInterruptedBackupImport()
        return NotesRepository(getSharedPreferences(NotesRepository.PREFERENCES_NAME, MODE_PRIVATE))
    }

    private fun sendTaskStatusChanged(taskId: Long? = null, status: RecordStatus? = null) {
        val intent = Intent(ProtectionTimerContract.ACTION_TASK_STATUS_CHANGED).setPackage(packageName)
        if (taskId != null && status != null) {
            intent.putExtra(ProtectionTimerContract.EXTRA_CHANGED_TASK_ID, taskId)
            intent.putExtra(ProtectionTimerContract.EXTRA_CHANGED_TASK_STATUS, status.name)
        }
        sendBroadcast(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "Таймер защиты",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Сигнал об окончании таймера защиты"
                        setSound(null, null)
                        enableVibration(true)
                    }
                )
            }
        }
    }

    private fun stopAlertAndService(startId: Int) {
        protectionAlertActive = false
        activeProtectionAlert = null
        stopRingtone()
        releaseWakeLock()
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        finishService(startId)
    }

    private fun finishService(startId: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelfResult(startId)
    }

    override fun onDestroy() {
        stopRingtone()
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private companion object {
        const val CHANNEL_ID = "protection_timer_alerts"
        const val NOTIFICATION_ID = 7432
    }
}
