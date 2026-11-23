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
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager

internal object KitchenTimerContract {
    const val ACTION_ALARM = "com.magnumold.notes.action.KITCHEN_TIMER_ALARM"
    const val ACTION_START_ALERT = "com.magnumold.notes.action.KITCHEN_TIMER_START_ALERT"
    const val ACTION_STOP_ALERT = "com.magnumold.notes.action.KITCHEN_TIMER_STOP_ALERT"
    const val ACTION_STATE_CHANGED = "com.magnumold.notes.action.KITCHEN_TIMER_STATE_CHANGED"
    const val EXTRA_TIMER_ID = "kitchen_timer_id"
    const val EXTRA_TITLE = "kitchen_timer_title"
    const val EXTRA_SOUND_URI = "kitchen_timer_sound_uri"
    const val EXTRA_ALERT_DURATION = "kitchen_timer_alert_duration"
    const val SERVICE_NOTIFICATION_ID = 7440
    const val ALERT_NOTIFICATION_ID = 7441
}

internal fun kitchenTimerNotificationTag(timerId: Long) = "kitchen-timer-alert:$timerId"

internal class KitchenTimerReceiver : BroadcastReceiver() {
    /**
     * Compatibility bridge for alarms registered by versions that stored
     * kitchen timers separately. It immediately hands the event to the
     * canonical record scheduler; no legacy timer data is read or written.
     */
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != KitchenTimerContract.ACTION_ALARM) return
        val timerId = intent.getLongExtra(KitchenTimerContract.EXTRA_TIMER_ID, -1L)
        if (timerId <= 0L) return
        val preferences = context.getSharedPreferences(NotesRepository.PREFERENCES_NAME, Context.MODE_PRIVATE)
        val mappedId = runCatching {
            preferences.getString(NotesRepository.KEY_KITCHEN_TIMER_LEGACY_ID_MAP, null)
                ?.let { org.json.JSONObject(it) }
                ?.optLong(timerId.toString(), 0L)
        }.getOrNull()?.takeIf { it > 0L } ?: return
        val repository = NotesRepository(preferences)
        val record = HomeTimelineOperations.records(repository.loadSnapshot())
            .firstOrNull { it.id == mappedId && it.type == RecordType.TIMER } ?: return
        RecordTimerScheduler(context).sync(record)
    }
}

internal fun postKitchenTimerFallbackNotification(context: Context, timer: KitchenTimer, sound: String?, durationSeconds: Int) {
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
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        builder.setTimeoutAfter(durationSeconds.coerceIn(1, 3600) * 1_000L)
    }
    manager.notify(kitchenTimerNotificationTag(timer.id), KitchenTimerContract.ALERT_NOTIFICATION_ID, builder.build())
}

internal class KitchenTimerAlertService : Service() {
    private data class ActiveAlert(val player: MediaPlayer?, val stopAction: Runnable)
    private val handler = Handler(Looper.getMainLooper())
    private val activeAlerts = linkedMapOf<Long, ActiveAlert>()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureForeground()
        when (intent?.action) {
            KitchenTimerContract.ACTION_START_ALERT -> {
                restoreActiveAlerts(finishWhenEmpty = false)
                intent?.let(::startAlert)
            }
            KitchenTimerContract.ACTION_STOP_ALERT -> intent?.let { stopIntent ->
                val id = stopIntent.getLongExtra(KitchenTimerContract.EXTRA_TIMER_ID, -1L)
                if (id > 0L) stopAlert(id)
                else finishIfIdle()
            }
            null -> restoreActiveAlerts()
            else -> finishIfIdle()
        }
        return START_STICKY
    }

    private fun restoreActiveAlerts(finishWhenEmpty: Boolean = true) {
        val repository = NotesRepository(
            getSharedPreferences(NotesRepository.PREFERENCES_NAME, Context.MODE_PRIVATE)
        )
        val records = HomeTimelineOperations.records(repository.loadSnapshot())
            .filter { it.type == RecordType.TIMER && it.timerAlertStartedAtMillis != null }
        val settings = repository.loadSnapshot().timerSettings
        val now = System.currentTimeMillis()
        records.filter { it.status == RecordStatus.COMPLETED }.forEach { record ->
            val startedAt = record.timerAlertStartedAtMillis ?: return@forEach
            val elapsed = (now - startedAt).coerceAtLeast(0L) / 1_000L
            val remaining = settings.alertDurationSeconds() - elapsed
            if (remaining > 0L) {
                startAlert(
                    Intent().putExtra(KitchenTimerContract.EXTRA_TIMER_ID, record.id)
                        .putExtra(KitchenTimerContract.EXTRA_TITLE, record.titleText)
                        .putExtra(
                            KitchenTimerContract.EXTRA_SOUND_URI,
                            record.timerSoundUri ?: settings.defaultSoundUri
                        )
                        .putExtra(KitchenTimerContract.EXTRA_ALERT_DURATION, remaining.toInt())
                )
            } else {
                clearPersistedAlert(record.id)
                getSystemService(NotificationManager::class.java)
                    .cancel(kitchenTimerNotificationTag(record.id), KitchenTimerContract.ALERT_NOTIFICATION_ID)
            }
        }
        if (activeAlerts.isEmpty() && finishWhenEmpty) finishIfIdle()
    }

    private fun clearPersistedAlert(recordId: Long) {
        val repository = NotesRepository(
            getSharedPreferences(NotesRepository.PREFERENCES_NAME, Context.MODE_PRIVATE)
        )
        val snapshot = repository.loadSnapshot()
        val record = HomeTimelineOperations.records(snapshot).firstOrNull {
            it.id == recordId && it.type == RecordType.TIMER && it.timerAlertStartedAtMillis != null
        } ?: return
        repository.flushSnapshot(
            HomeTimelineOperations.update(snapshot, record.copy(timerAlertStartedAtMillis = null))
        )
        repository.markExternalTimerStateUpdate()
        sendBroadcast(Intent(KitchenTimerContract.ACTION_STATE_CHANGED).setPackage(packageName))
    }

    private fun startAlert(intent: Intent) {
        val id = intent.getLongExtra(KitchenTimerContract.EXTRA_TIMER_ID, -1L)
        if (id <= 0L) {
            finishIfIdle()
            return
        }
        if (activeAlerts.containsKey(id)) return
        val uri = intent.getStringExtra(KitchenTimerContract.EXTRA_SOUND_URI)?.let(Uri::parse)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val player = uri?.let { selectedUri ->
            val candidate = MediaPlayer()
            val prepared = runCatching {
                candidate.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                candidate.setWakeMode(this@KitchenTimerAlertService, PowerManager.PARTIAL_WAKE_LOCK)
                candidate.setDataSource(this@KitchenTimerAlertService, selectedUri)
                candidate.isLooping = true
                candidate.prepare()
                candidate.start()
            }.isSuccess
            if (prepared) candidate else {
                runCatching { candidate.release() }
                null
            }
        }
        val durationSeconds = intent.getIntExtra(KitchenTimerContract.EXTRA_ALERT_DURATION, 30).coerceIn(1, 3600)
        val stopAction = Runnable { stopAlert(id) }
        activeAlerts[id] = ActiveAlert(player, stopAction)
        handler.postDelayed(stopAction, durationSeconds * 1_000L)
        postAlertNotification(id, intent.getStringExtra(KitchenTimerContract.EXTRA_TITLE).orEmpty())
        updateForegroundNotification()
    }

    private fun stopAlert(id: Long) {
        // Acknowledgement is durable: a service restart must not replay a
        // signal the user has already stopped (or whose selected duration ended).
        clearPersistedAlert(id)
        val active = activeAlerts.remove(id)
        if (active == null) {
            getSystemService(NotificationManager::class.java)
                .cancel(kitchenTimerNotificationTag(id), KitchenTimerContract.ALERT_NOTIFICATION_ID)
            finishIfIdle()
            return
        }
        handler.removeCallbacks(active.stopAction)
        runCatching { active.player?.stop() }
        runCatching { active.player?.release() }
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

    private fun finishIfIdle() {
        if (activeAlerts.isNotEmpty()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE)
        else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
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
            runCatching { it.player?.stop() }
            runCatching { it.player?.release() }
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
