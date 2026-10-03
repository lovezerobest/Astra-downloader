package com.astradownloader

import android.content.Context

/** 设置项集中管理（SharedPreferences）。 */
object SettingsRepository {
    private const val PREFS = "astra_settings"

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 并发下载线程数（1..32） */
    var threads: Int
        get() = prefs(AppHolder.app).getInt("download_threads", 16)
        set(value) { prefs(AppHolder.app).edit().putInt("download_threads", value).apply() }

    /** 复制链接自动识别并投递岛卡片 */
    var autoDetectClipboard: Boolean
        get() = prefs(AppHolder.app).getBoolean("auto_detect_clipboard", true)
        set(value) { prefs(AppHolder.app).edit().putBoolean("auto_detect_clipboard", value).apply() }

    var rootClipboardEnabled: Boolean
        get() = prefs(AppHolder.app).getBoolean("root_clipboard_enabled", false)
        set(value) { prefs(AppHolder.app).edit().putBoolean("root_clipboard_enabled", value).apply() }

    /** 是否忽略 SSL 证书校验（部分网盘/自签 CDN） */
    var ignoreSsl: Boolean
        get() = prefs(AppHolder.app).getBoolean("ignore_ssl", false)
        set(value) { prefs(AppHolder.app).edit().putBoolean("ignore_ssl", value).apply() }

    /** 复制链接解析成功后自动开始下载（默认关：需用户点「下载」） */
    var autoDownloadAfterResolve: Boolean
        get() = prefs(AppHolder.app).getBoolean("auto_download_after_resolve", false)
        set(value) { prefs(AppHolder.app).edit().putBoolean("auto_download_after_resolve", value).apply() }

    /** 岛卡片自动展开 */
    var alertOnStart: Boolean
        get() = prefs(AppHolder.app).getBoolean("alert_on_start", true)
        set(value) { prefs(AppHolder.app).edit().putBoolean("alert_on_start", value).apply() }
}

/** 持有 Application 引用，供无 Context 上下文访问设置。 */
object AppHolder {
    lateinit var app: AstraApp
        private set

    fun init(app: AstraApp) { this.app = app }
}