package com.astradownloader

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.util.Log

class ClipboardReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i("AstraClipboard", "receiver entry action=${intent.action} fromUid=$sentFromUid")
        if (intent.action != "com.astradownloader.CLIP_DETECTED") return
        // Shell-context broadcasts may report -1 on Android 14+ even though the
        // receiver is protected by android.permission.DUMP in the manifest.
        if (Build.VERSION.SDK_INT >= 34 && sentFromUid != Process.SHELL_UID && sentFromUid != -1) return
        if (!SettingsRepository.autoDetectClipboard) return
        val text = intent.getStringExtra("text")?.trim() ?: return
        if (text.length > 4096 || !ClipboardLinkPolicy.isDownloadLink(text)) return
        Log.i("AstraClipboard", "background download link received")
        (context.applicationContext as AstraApp).coordinator.onLinkDetected(
            text, intent.getLongExtra("timestamp", 0L)
        )
    }
}
