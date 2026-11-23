package com.magnumold.notes

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build

internal fun recordTimerNotificationTag(recordId: Long): String = "record-timer:$recordId"

internal object RecordTimerContract {
    const val ACTION_ALARM = "com.magnumold.notes.action.RECORD_TIMER_ALARM"
    const val EXTRA_RECORD_ID = "record_timer_id"
    const val NOTIFICATION_ID = 7433
}

internal class RecordTimerScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun cancel(recordId: Long) {
        alarmManager.cancel(pendingIntent(recordId))
        notificationManager().cancel(recordTimerNotificationTag(recordId), RecordTimerContract.NOTIFICATION_ID)
        notificationManager().cancel(kitchenTimerNotificationTag(recordId), KitchenTimerContract.ALERT_NOTIFICATION_ID)
        runCatching {
            context.startService(
                Intent(context, KitchenTimerAlertService::class.java)
                    .setAction(KitchenTimerContract.ACTION_STOP_ALERT)
                    .putExtra(KitchenTimerContract.EXTRA_TIMER_ID, recordId)
            )
        }
    }

    fun sync(note: Note) {
        val pending = pendingIntent(note.id)
        val triggerAt = when {
            note.type != RecordType.TIMER -> null
            note.timerEndsAtMillis != null -> note.timerEndsAtMillis
            note.status == RecordStatus.COMPLETED && note.timerAlertStartedAtMillis != null ->
                System.currentTimeMillis() + 1_000L
            else -> null
        }
        if (triggerAt == null) {
            alarmManager.cancel(pending)
            if (note.status != RecordStatus.COMPLETED) {
                notificationManager().cancel(recordTimerNotificationTag(note.id), RecordTimerContract.NOTIFICATION_ID)
            }
            return
        }
        notificationManager().cancel(recordTimerNotificationTag(note.id), RecordTimerContract.NOTIFICATION_ID)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    fun scheduleAll(records: List<Note>) {
        records.filter {
            it.type == RecordType.TIMER &&
                (it.timerEndsAtMillis != null || it.timerAlertStartedAtMillis != null)
        }
            .forEach(::sync)
    }

    private fun notificationManager() = context.getSystemService(NotificationManager::class.java)

    private fun pendingIntent(recordId: Long): PendingIntent {
        val intent = Intent(context, RecordTimerReceiver::class.java)
            .setAction(RecordTimerContract.ACTION_ALARM)
            .setData(Uri.parse("notesandroid://record-timer/$recordId"))
            .putExtra(RecordTimerContract.EXTRA_RECORD_ID, recordId)
        val requestCode = (recordId xor (recordId ushr 32)).toInt()
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

internal class RecordTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != RecordTimerContract.ACTION_ALARM) return
        val recordId = intent.getLongExtra(RecordTimerContract.EXTRA_RECORD_ID, -1L)
        if (recordId <= 0L) return

        val repository = NotesRepository(
            context.getSharedPreferences(NotesRepository.PREFERENCES_NAME, Context.MODE_PRIVATE)
        )
        val snapshot = repository.loadSnapshot()
        val current = HomeTimelineOperations.records(snapshot).firstOrNull {
            it.id == recordId && it.type == RecordType.TIMER
        } ?: return
        val endAt = current.timerEndsAtMillis
        if (endAt == null) {
            if (current.status == RecordStatus.COMPLETED && current.timerAlertStartedAtMillis != null) {
                val restoreIntent = Intent(context, KitchenTimerAlertService::class.java)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(restoreIntent)
                    else context.startService(restoreIntent)
                } catch (_: RuntimeException) {
                    // The durable alert marker remains available for the next app/service restore.
                }
            }
            return
        }
        val now = System.currentTimeMillis()
        if (now < endAt) {
            RecordTimerScheduler(context).sync(current)
            return
        }

        val completed = current.copy(
            timerEndsAtMillis = null,
            timerRemainingSeconds = 0L,
            timerAlertStartedAtMillis = now,
            status = RecordStatus.COMPLETED,
            updated = now
        )
        repository.flushSnapshot(HomeTimelineOperations.update(snapshot, completed))
        repository.markExternalTimerStateUpdate()
        context.sendBroadcast(
            Intent(KitchenTimerContract.ACTION_STATE_CHANGED).setPackage(context.packageName)
        )

        val settings = repository.loadSnapshot().timerSettings
        val serviceIntent = Intent(context, KitchenTimerAlertService::class.java)
            .setAction(KitchenTimerContract.ACTION_START_ALERT)
            .putExtra(KitchenTimerContract.EXTRA_TIMER_ID, recordId)
            .putExtra(KitchenTimerContract.EXTRA_TITLE, current.titleText)
            .putExtra(
                KitchenTimerContract.EXTRA_SOUND_URI,
                current.timerSoundUri ?: settings.defaultSoundUri
            )
            .putExtra(KitchenTimerContract.EXTRA_ALERT_DURATION, settings.alertDurationSeconds())
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(serviceIntent)
            else context.startService(serviceIntent)
        } catch (_: RuntimeException) {
            postKitchenTimerFallbackNotification(
                context,
                completed.toKitchenTimer(),
                current.timerSoundUri ?: settings.defaultSoundUri,
                settings.alertDurationSeconds()
            )
            // The fallback notification owns its timeout; clear the durable
            // service-alert marker so it cannot be replayed indefinitely.
            val latest = repository.loadSnapshot()
            val latestRecord = HomeTimelineOperations.records(latest).firstOrNull { it.id == recordId }
            if (latestRecord?.timerAlertStartedAtMillis != null) {
                repository.flushSnapshot(
                    HomeTimelineOperations.update(latest, latestRecord.copy(timerAlertStartedAtMillis = null))
                )
                repository.markExternalTimerStateUpdate()
            }
        }
    }
}
