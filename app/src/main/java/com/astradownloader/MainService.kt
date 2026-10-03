package com.astradownloader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

/** Foreground clipboard listener; Android may restrict background clipboard reads. */
class MainService : Service() {
    private val channelId = "astra_main"
    private val notificationId = 1
    private val handler = Handler(Looper.getMainLooper())
    private var clipboardManager: ClipboardManager? = null
    private var pendingRead: Runnable? = null
    private var lastText: String? = null
    private var lastTimestamp: Long = 0L

    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        if (!SettingsRepository.autoDetectClipboard) return@OnPrimaryClipChangedListener
        pendingRead?.let(handler::removeCallbacks)
        val read = Runnable { processCurrentClip() }
        pendingRead = read
        handler.postDelayed(read, 300)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(notificationId, buildNotification())
        startClipboardMonitor()
    }

    private fun startClipboardMonitor() {
        runCatching {
            clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboardManager?.addPrimaryClipChangedListener(clipboardListener)
            processCurrentClip()
        }
    }

    private fun processCurrentClip() {
        if (!SettingsRepository.autoDetectClipboard) return
        val clip = runCatching { clipboardManager?.primaryClip }.getOrNull() ?: return
        if (clip.itemCount <= 0) return
        val text = runCatching { clip.getItemAt(0).coerceToText(this).toString().trim() }
            .getOrNull().orEmpty()
        if (text.isEmpty()) return
        val timestamp = clip.description?.timestamp ?: 0L
        if (text == lastText && timestamp == lastTimestamp) return
        lastText = text
        lastTimestamp = timestamp
        if (!ClipboardLinkPolicy.isDownloadLink(text)) return

        val app = application as? AstraApp ?: return
        app.coordinator.connect()
        app.coordinator.onLinkDetected(text, timestamp)
    }

    override fun onDestroy() {
        pendingRead?.let(handler::removeCallbacks)
        clipboardManager?.removePrimaryClipChangedListener(clipboardListener)
        clipboardManager = null
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        createChannel()
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, channelId)
            .setContentTitle("星流下载器")
            .setContentText("监听剪贴板中，复制下载链接自动识别")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "下载监听", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
