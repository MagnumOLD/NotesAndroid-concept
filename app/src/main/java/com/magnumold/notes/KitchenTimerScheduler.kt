package com.magnumold.notes

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

internal object KitchenTimerContract {
    const val ACTION_ALARM = "com.magnumold.notes.action.KITCHEN_TIMER_ALARM"
    const val ACTION_START_ALERT = "com.magnumold.notes.action.KITCHEN_TIMER_START_ALERT"
    const val ACTION_STOP_ALERT = "com.magnumold.notes.action.KITCHEN_TIMER_STOP_ALERT"
    const val EXTRA_TIMER_ID = "kitchen_timer_id"
    const val EXTRA_TITLE = "kitchen_timer_title"
    const val EXTRA_SOUND_URI = "kitchen_timer_sound_uri"
    const val EXTRA_ALERT_DURATION = "kitchen_timer_alert_duration"
    const val SERVICE_NOTIFICATION_ID = 7440
    const val ALERT_NOTIFICATION_ID = 7441
}

internal class KitchenTimerScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun sync(timer: KitchenTimer) {
        val pendingIntent = pendingIntent(timer.id)
        val triggerAt = when {
            timer.status == KitchenTimerStatus.RUNNING -> timer.endsAtMillis
            timer.status == KitchenTimerStatus.FINISHED && timer.alertStartedAtMillis == null ->
                System.currentTimeMillis() + 1_000L
            else -> null
        }
        if (triggerAt == null) {
            alarmManager.cancel(pendingIntent)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    fun scheduleAll(timers: List<KitchenTimer>) {
        timers.forEach(::sync)
    }

    fun cancel(timerId: Long) {
        alarmManager.cancel(pendingIntent(timerId))
        context.getSystemService(NotificationManager::class.java)
            .cancel(kitchenTimerNotificationTag(timerId), KitchenTimerContract.ALERT_NOTIFICATION_ID)
        val stop = Intent(context, KitchenTimerAlertService::class.java)
            .setAction(KitchenTimerContract.ACTION_STOP_ALERT)
            .putExtra(KitchenTimerContract.EXTRA_TIMER_ID, timerId)
        context.startService(stop)
    }

    private fun pendingIntent(timerId: Long): PendingIntent {
        val intent = Intent(context, KitchenTimerReceiver::class.java)
            .setAction(KitchenTimerContract.ACTION_ALARM)
            .setData(Uri.parse("notesandroid://kitchen-timer/$timerId"))
            .putExtra(KitchenTimerContract.EXTRA_TIMER_ID, timerId)
        val requestCode = (timerId xor (timerId ushr 32)).toInt()
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

internal fun kitchenTimerNotificationTag(timerId: Long) = "kitchen-timer-alert:$timerId"

internal class KitchenTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != KitchenTimerContract.ACTION_ALARM) return
        val timerId = intent.getLongExtra(KitchenTimerContract.EXTRA_TIMER_ID, -1L)
        if (timerId <= 0L) return
        val repository = KitchenTimerRepository(
            context.getSharedPreferences("kitchen_timers", Context.MODE_PRIVATE)
        )
        val snapshot = repository.load()
        val current = snapshot.timers.firstOrNull { it.id == timerId } ?: return
        val now = System.currentTimeMillis()
        val reconciled = KitchenTimerOperations.reconcile(current, now)
        if (reconciled.status == KitchenTimerStatus.RUNNING) {
            KitchenTimerScheduler(context).sync(reconciled)
            return
        }
        if (reconciled.status != KitchenTimerStatus.FINISHED || reconciled.alertStartedAtMillis != null) return

        val fired = reconciled.copy(alertStartedAtMillis = now)
        repository.saveTimers(snapshot.timers.map { if (it.id == timerId) fired else it })
        val sound = fired.soundUri ?: snapshot.settings.defaultSoundUri
        val serviceIntent = Intent(context, KitchenTimerAlertService::class.java)
            .setAction(KitchenTimerContract.ACTION_START_ALERT)
            .putExtra(KitchenTimerContract.EXTRA_TIMER_ID, timerId)
            .putExtra(KitchenTimerContract.EXTRA_TITLE, fired.title)
            .putExtra(KitchenTimerContract.EXTRA_SOUND_URI, sound)
            .putExtra(KitchenTimerContract.EXTRA_ALERT_DURATION, snapshot.settings.alertDurationSeconds())
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(serviceIntent)
            else context.startService(serviceIntent)
        } catch (_: RuntimeException) {
            postKitchenTimerFallbackNotification(context, fired, sound)
        }
    }
}

internal class KitchenTimerBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val repository = KitchenTimerRepository(
            context.getSharedPreferences("kitchen_timers", Context.MODE_PRIVATE)
        )
        KitchenTimerScheduler(context).scheduleAll(repository.load().timers)
    }
}

private fun postKitchenTimerFallbackNotification(context: Context, timer: KitchenTimer, sound: String?) {
    val manager = context.getSystemService(NotificationManager::class.java)
    val soundUri = sound?.let(Uri::parse) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
    val channelId = "kitchen_timer_fallback_${soundUri?.toString()?.hashCode() ?: 0}"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager.getNotificationChannel(channelId) == null) {
        manager.createNotificationChannel(
            NotificationChannel(channelId, "Сигналы таймеров", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(soundUri, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
                enableVibration(true)
            }
        )
    }
    val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(context, channelId)
    else {
        @Suppress("DEPRECATION")
        Notification.Builder(context)
    }
    builder.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
        .setContentTitle("Время вышло")
        .setContentText(timer.title.ifBlank { "Таймер завершён" })
        .setCategory(Notification.CATEGORY_ALARM)
        .setPriority(Notification.PRIORITY_MAX)
        .setAutoCancel(true)
        .setTimeoutAfter(60_000L)
    manager.notify(kitchenTimerNotificationTag(timer.id), KitchenTimerContract.ALERT_NOTIFICATION_ID, builder.build())
}

internal class KitchenTimerAlertService : Service() {
    private data class ActiveAlert(val ringtone: Ringtone?, val stopAction: Runnable)
    private val handler = Handler(Looper.getMainLooper())
    private val activeAlerts = linkedMapOf<Long, ActiveAlert>()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureForeground()
        when (intent?.action) {
            KitchenTimerContract.ACTION_START_ALERT -> startAlert(intent)
            KitchenTimerContract.ACTION_STOP_ALERT -> {
                val id = intent.getLongExtra(KitchenTimerContract.EXTRA_TIMER_ID, -1L)
                if (id > 0L) stopAlert(id)
            }
        }
        return START_NOT_STICKY
    }

    private fun startAlert(intent: Intent) {
        val id = intent.getLongExtra(KitchenTimerContract.EXTRA_TIMER_ID, -1L)
        if (id <= 0L || activeAlerts.containsKey(id)) return
        val uri = intent.getStringExtra(KitchenTimerContract.EXTRA_SOUND_URI)?.let(Uri::parse)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val ringtone = runCatching { RingtoneManager.getRingtone(this, uri) }.getOrNull()
        ringtone?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                it.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) it.isLooping = true
            runCatching { it.play() }
        }
        val durationSeconds = intent.getIntExtra(KitchenTimerContract.EXTRA_ALERT_DURATION, 30).coerceIn(1, 3600)
        val stopAction = Runnable { stopAlert(id) }
        activeAlerts[id] = ActiveAlert(ringtone, stopAction)
        handler.postDelayed(stopAction, durationSeconds * 1_000L)
        postAlertNotification(id, intent.getStringExtra(KitchenTimerContract.EXTRA_TITLE).orEmpty())
        updateForegroundNotification()
    }

    private fun stopAlert(id: Long) {
        val active = activeAlerts.remove(id) ?: return
        handler.removeCallbacks(active.stopAction)
        runCatching { active.ringtone?.stop() }
        getSystemService(NotificationManager::class.java)
            .cancel(kitchenTimerNotificationTag(id), KitchenTimerContract.ALERT_NOTIFICATION_ID)
        if (activeAlerts.isEmpty()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE)
            else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            stopSelf()
        } else updateForegroundNotification()
    }

    private fun ensureForeground() {
        createChannels()
        startForeground(KitchenTimerContract.SERVICE_NOTIFICATION_ID, buildServiceNotification())
    }

    private fun updateForegroundNotification() {
        getSystemService(NotificationManager::class.java)
            .notify(KitchenTimerContract.SERVICE_NOTIFICATION_ID, buildServiceNotification())
    }

    private fun buildServiceNotification(): Notification {
        val builder = notificationBuilder(SERVICE_CHANNEL_ID)
        return builder.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Таймеры")
            .setContentText(if (activeAlerts.isEmpty()) "Сигнал таймера" else "Звучит сигнал: ${activeAlerts.size}")
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    private fun postAlertNotification(id: Long, title: String) {
        val stopIntent = Intent(this, KitchenTimerAlertService::class.java)
            .setAction(KitchenTimerContract.ACTION_STOP_ALERT)
            .putExtra(KitchenTimerContract.EXTRA_TIMER_ID, id)
        val stopPending = PendingIntent.getService(
            this, (id xor (id ushr 32)).toInt(), stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openPending = PendingIntent.getActivity(
            this, 7442,
            Intent(this, MainActivity::class.java)
                .setData(Uri.parse("notesandroid://kitchen-timer-alert/$id"))
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = notificationBuilder(ALERT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Время вышло")
            .setContentText(title.ifBlank { "Таймер завершён" })
            .setContentIntent(openPending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Отключить сигнал", stopPending)
            .setCategory(Notification.CATEGORY_ALARM)
            .setAutoCancel(false)
            .setOngoing(true)
            .build()
        getSystemService(NotificationManager::class.java)
            .notify(kitchenTimerNotificationTag(id), KitchenTimerContract.ALERT_NOTIFICATION_ID, notification)
    }

    private fun notificationBuilder(channel: String): Notification.Builder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, channel)
        else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(SERVICE_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(SERVICE_CHANNEL_ID, "Работа таймеров", NotificationManager.IMPORTANCE_LOW)
            )
        }
        if (manager.getNotificationChannel(ALERT_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(ALERT_CHANNEL_ID, "Окончание таймеров", NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(null, null)
                    enableVibration(true)
                }
            )
        }
    }

    override fun onDestroy() {
        activeAlerts.values.forEach {
            handler.removeCallbacks(it.stopAction)
            runCatching { it.ringtone?.stop() }
        }
        activeAlerts.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private companion object {
        const val SERVICE_CHANNEL_ID = "kitchen_timer_service"
        const val ALERT_CHANNEL_ID = "kitchen_timer_alerts"
    }
}
