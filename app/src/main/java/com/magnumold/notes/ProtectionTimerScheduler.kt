package com.magnumold.notes

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock

internal object ProtectionTimerContract {
    const val ACTION_ALARM = "com.magnumold.notes.action.PROTECTION_TIMER_ALARM"
    const val ACTION_STOP_TRACKING = "com.magnumold.notes.action.STOP_TRACKING"
    const val ACTION_CONTINUE_WORK = "com.magnumold.notes.action.CONTINUE_WORK"
    const val ACTION_TASK_STATUS_CHANGED = "com.magnumold.notes.action.TASK_STATUS_CHANGED"
    const val EXTRA_TASK_ID = "task_id"
    const val EXTRA_PARENT_TASK_ID = "parent_task_id"
    const val EXTRA_TASK_TITLE = "task_title"
    const val EXTRA_SUBTASK_TITLE = "subtask_title"
    const val EXTRA_END_MODE = "end_mode"
    const val EXTRA_TIMER_STARTED_AT = "timer_started_at"
    const val EXTRA_TIMER_ENDS_AT = "timer_ends_at"
    const val EXTRA_CHANGED_TASK_ID = "changed_task_id"
    const val EXTRA_CHANGED_TASK_STATUS = "changed_task_status"
    const val EXTRA_TIMER_STATE_CHANGED = "timer_state_changed"
    const val REQUEST_CODE = 7431
}

internal class ProtectionTimerScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun schedule(timer: ProtectionTimer) {
        val pendingIntent = alarmPendingIntent()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timer.endsAtMillis, pendingIntent)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timer.endsAtMillis, pendingIntent)
        }
    }

    fun cancel() {
        alarmManager.cancel(alarmPendingIntent())
    }

    private fun alarmPendingIntent(): PendingIntent {
        val intent = Intent(context, ProtectionTimerReceiver::class.java)
            .setAction(ProtectionTimerContract.ACTION_ALARM)
        return PendingIntent.getBroadcast(
            context,
            ProtectionTimerContract.REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

internal data class ProtectionAlarmResolution(
    val state: TimeTrackingState,
    val expiredTimer: ProtectionTimer? = null,
    val rescheduleTimer: ProtectionTimer? = null
)

internal fun shouldUseProtectionAlarmNotificationFallback(
    sdkInt: Int,
    canScheduleExactAlarms: Boolean
): Boolean = sdkInt >= Build.VERSION_CODES.S && !canScheduleExactAlarms

internal fun resolveProtectionAlarm(
    persisted: TimeTrackingState,
    nowMillis: Long
): ProtectionAlarmResolution {
    if (persisted.firedProtectionAlert != null) {
        return ProtectionAlarmResolution(persisted)
    }
    val timer = persisted.protectionTimer ?: return ProtectionAlarmResolution(persisted)
    if (nowMillis < timer.endsAtMillis) {
        return ProtectionAlarmResolution(persisted, rescheduleTimer = timer)
    }
    return ProtectionAlarmResolution(
        state = completeProtectionTimerIfCurrent(persisted, timer, nowMillis),
        expiredTimer = timer
    )
}

internal class ProtectionTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        // An inexact alarm is not exempt from Android 12+ background FGS-start
        // restrictions. When exact-alarm access is denied, complete the timer
        // and post an audible notification instead of trying to start a service.
        if (shouldUseProtectionAlarmNotificationFallback(
                Build.VERSION.SDK_INT,
                context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
            )
        ) {
            deliverInexactAlarm(context)
            return
        }
        val serviceIntent = Intent(context, ProtectionTimerAlertService::class.java)
            .setAction(ProtectionTimerContract.ACTION_ALARM)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (_: android.app.ForegroundServiceStartNotAllowedException) {
            // The platform can still reject an FGS start for reasons other than
            // exact-alarm access. Preserve the alarm as a user-visible notification.
            deliverInexactAlarm(context)
        }
    }

    private fun deliverInexactAlarm(context: Context) {
        val trackingRepository = TimeTrackingRepository(
            context.getSharedPreferences("time_tracking", Context.MODE_PRIVATE)
        )
        val now = System.currentTimeMillis()
        var resolution = ProtectionAlarmResolution(TimeTrackingState())
        val state = trackingRepository.update { persisted ->
            resolution = resolveProtectionAlarm(persisted, now)
            resolution.state
        }
        resolution.rescheduleTimer?.let {
            ProtectionTimerScheduler(context).schedule(it)
            return
        }
        val alert = state.firedProtectionAlert ?: return
        if (resolution.expiredTimer?.endMode == ProtectionEndMode.STOP_AND_PAUSE) {
            val updated = NotesRepository(
                context.getSharedPreferences(NotesRepository.PREFERENCES_NAME, Context.MODE_PRIVATE)
            ).updateTaskStatus(alert.taskId, RecordStatus.PAUSED)
            if (updated) {
                context.sendBroadcast(
                    Intent(ProtectionTimerContract.ACTION_TASK_STATUS_CHANGED)
                        .setPackage(context.packageName)
                        .putExtra(ProtectionTimerContract.EXTRA_CHANGED_TASK_ID, alert.taskId)
                        .putExtra(ProtectionTimerContract.EXTRA_CHANGED_TASK_STATUS, RecordStatus.PAUSED.name)
                )
            }
        }
        postFallbackNotification(context, alert)
    }

    private fun postFallbackNotification(context: Context, alert: ProtectionTimer) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val soundUri = if (alert.soundEnabled) {
            alert.soundUri?.let(Uri::parse)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        } else null
        val channelId = "protection_timer_fallback_${if (soundUri == null) "silent" else soundUri.toString().hashCode()}"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            manager.getNotificationChannel(channelId) == null
        ) {
            manager.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    if (soundUri == null) "Сигнал таймера (без звука)" else "Сигнал таймера",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Резервное уведомление об окончании таймера защиты"
                    enableVibration(true)
                    setSound(
                        soundUri,
                        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
                    )
                }
            )
        }
        val openApp = PendingIntent.getActivity(
            context,
            alert.taskId.hashCode(),
            Intent(context, MainActivity::class.java)
                .setData(Uri.parse("notesandroid://protection-timer-fallback/${alert.taskId}/${alert.startedAtMillis}"))
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, channelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
        builder
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Время вышло")
            .setContentText(alert.subtaskTitle ?: alert.taskTitle)
            .setContentIntent(openApp)
            .setCategory(Notification.CATEGORY_ALARM)
            .setPriority(Notification.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(false)
            .setShowWhen(false)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O && soundUri != null) {
            @Suppress("DEPRECATION")
            builder.setSound(soundUri)
        }
        manager.notify(FALLBACK_NOTIFICATION_ID, builder.build())
    }

    private companion object {
        const val FALLBACK_NOTIFICATION_ID = 7432
    }
}

internal class ProtectionTimerBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val repository = TimeTrackingRepository(
            context.getSharedPreferences("time_tracking", Context.MODE_PRIVATE)
        )
        val now = System.currentTimeMillis()
        val bootTime = now - SystemClock.elapsedRealtime()
        val notesRepository = NotesRepository(
            context.getSharedPreferences(NotesRepository.PREFERENCES_NAME, Context.MODE_PRIVATE)
        )
        val records = HomeTimelineOperations.records(notesRepository.loadSnapshot())
        // Reconcile against the latest persisted state while holding the repository
        // transaction lock. A timer/service update must not be overwritten by a
        // stale snapshot captured before boot reconciliation began.
        val state = repository.update { persisted ->
            // Apply deadline-derived task states before splitting sessions at boot.
            // This closes an expired task at its real deadline boundary instead of
            // counting powered-off time up to the next device boot.
            var reconciled = reconcileBootTimeTrackingState(records, persisted, now)
            // Remaining sessions resume from bootTime; their active intervals are
            // split so the reboot itself does not merge the before/after segments.
            reconciled.activeSessions.values.toList().forEach { active ->
                if (active.startedAtMillis < bootTime) {
                    reconciled = TimeTrackingEngine.finish(
                        reconciled, active.taskId, bootTime, WorkSessionEndReason.STOP
                    )
                    reconciled = TimeTrackingEngine.start(
                        state = reconciled,
                        taskId = active.taskId,
                        parentTaskId = active.parentTaskId,
                        taskTitle = active.taskTitle,
                        subtaskTitle = active.subtaskTitle,
                        nowMillis = bootTime
                    )
                }
            }
            reconciled
        }
        if (shouldRestoreProtectionAlertOnBoot(state)) {
            // The alert identity survives reboot, but its foreground service does
            // not. Re-deliver it through AlarmManager: Android 15 restricts
            // starting mediaPlayback foreground services directly from BOOT_COMPLETED.
            state.firedProtectionAlert?.let { alert ->
                ProtectionTimerScheduler(context).schedule(alert)
            }
        } else {
            state.protectionTimer?.let { timer ->
                // Scheduling even an already-expired alarm lets AlarmManager deliver it
                // through the same receiver path as a normal expiry after reboot.
                ProtectionTimerScheduler(context).schedule(timer)
            }
        }
        RecordTimerScheduler(context).scheduleAll(records)
    }
}
