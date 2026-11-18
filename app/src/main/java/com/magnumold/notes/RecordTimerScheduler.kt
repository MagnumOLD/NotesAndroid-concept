package com.magnumold.notes

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build

internal object RecordTimerContract {
    const val ACTION_ALARM = "com.magnumold.notes.action.RECORD_TIMER_ALARM"
    const val EXTRA_RECORD_ID = "record_timer_id"
}

internal class RecordTimerScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun cancel(recordId: Long) {
        alarmManager.cancel(pendingIntent(recordId))
    }

    fun sync(note: Note) {
        val pendingIntent = pendingIntent(note.id)
        val endAt = note.timerEndsAtMillis
        if (note.type != RecordType.TIMER || endAt == null) {
            alarmManager.cancel(pendingIntent)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAt, pendingIntent)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAt, pendingIntent)
        }
    }

    fun scheduleAll(records: List<Note>) {
        records.filter { it.type == RecordType.TIMER && it.timerEndsAtMillis != null }
            .forEach(::sync)
    }

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
        val serviceIntent = Intent(context, ProtectionTimerAlertService::class.java)
            .setAction(RecordTimerContract.ACTION_ALARM)
            .putExtra(
                RecordTimerContract.EXTRA_RECORD_ID,
                intent.getLongExtra(RecordTimerContract.EXTRA_RECORD_ID, -1L)
            )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
