package com.astradownloader

import android.accessibilityservice.AccessibilityService
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/** Background clipboard listener. Android requires the user to enable this accessibility service. */
class ClipboardAccessibilityService : AccessibilityService() {
    private val tag = "A11yClipboard"
    private lateinit var clipboard: ClipboardManager
    private val handler = Handler(Looper.getMainLooper())
    private var lastText: String? = null
    private var lastTimestamp: Long = 0L
    private var pendingRead: Runnable? = null

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        pendingRead?.let(handler::removeCallbacks)
        val read = Runnable { checkClipboard() }
        pendingRead = read
        handler.postDelayed(read, 300)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(tag, "onServiceConnected")
        clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        runCatching { clipboard.addPrimaryClipChangedListener(clipListener) }
        checkClipboard()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED ||
            event?.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED
        ) {
            checkClipboard()
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        pendingRead?.let(handler::removeCallbacks)
        runCatching { clipboard.removePrimaryClipChangedListener(clipListener) }
        super.onDestroy()
    }

    private fun checkClipboard() {
        if (!SettingsRepository.autoDetectClipboard) return
        val clip = runCatching { clipboard.primaryClip }.getOrNull() ?: return
        if (clip.itemCount <= 0) return
        val text = runCatching { clip.getItemAt(0).coerceToText(this).toString().trim() }.getOrNull().orEmpty()
        if (text.isBlank()) return
        val timestamp = clip.description?.timestamp ?: 0L
        if (text == lastText && timestamp == lastTimestamp) return
        lastText = text
        lastTimestamp = timestamp
        if (!ClipboardLinkPolicy.isDownloadLink(text)) return

        Log.d(tag, "download clipboard link detected")
        val app = applicationContext as AstraApp
        app.coordinator.connect()
        app.coordinator.onLinkDetected(text, timestamp)
    }
}
