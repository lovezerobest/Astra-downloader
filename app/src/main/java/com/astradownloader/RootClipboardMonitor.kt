package com.astradownloader

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.TimeUnit

/** Starts the privileged clipboard listener only after the user enables Root access. */
object RootClipboardMonitor {
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var starting = false

    fun start(context: Context, result: (String) -> Unit = {}) {
        if (starting) return
        starting = true
        val apk = context.applicationInfo.sourceDir
        Thread {
            val message = try {
                val probe = ProcessBuilder("su", "-c", "id -u")
                    .redirectErrorStream(true).start()
                if (!probe.waitFor(12, TimeUnit.SECONDS)) {
                    probe.destroyForcibly()
                    "Root 授权超时，请在 Root 管理器中允许 AstraDownloader"
                } else if (probe.exitValue() != 0 || probe.inputStream.bufferedReader().readText().trim() != "0") {
                    "未获得 Root 权限，请在 Root 管理器中授权 AstraDownloader"
                } else {
                    // APK install path comes from PackageManager, not user text.
                    val shellCommand = "CLASSPATH='$apk' app_process /system/bin com.astradownloader.ClipboardShellBridge"
                    val command = "exec su 2000 -c \"$shellCommand\""
                    val daemon = ProcessBuilder("su", "-c", command)
                        .redirectErrorStream(true).start()
                    if (daemon.waitFor(2, TimeUnit.SECONDS) && daemon.exitValue() != 0) {
                        "后台监听启动失败：${daemon.inputStream.bufferedReader().readText().take(180)}"
                    } else {
                        SettingsRepository.rootClipboardEnabled = true
                        "后台监听已启动（Root）"
                    }
                }
            } catch (e: Exception) {
                "后台监听启动失败：${e.message ?: "未知错误"}"
            }
            starting = false
            handler.post { result(message) }
        }.start()
    }
}
