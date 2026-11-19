package com.magnumold.notes

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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

internal class ProtectionTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val serviceIntent = Intent(context, ProtectionTimerAlertService::class.java)
            .setAction(ProtectionTimerContract.ACTION_ALARM)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
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
        state.protectionTimer?.let { timer ->
            // Scheduling even an already-expired alarm lets AlarmManager deliver it
            // through the same receiver path as a normal expiry after reboot.
            ProtectionTimerScheduler(context).schedule(timer)
        }
        RecordTimerScheduler(context).scheduleAll(records)
    }
}
