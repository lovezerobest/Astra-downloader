package com.astradownloader

import android.util.Log

/** 日志脱敏：URL 中的路径段进行遮盖，避免敏感 token/key 泄露到日志。 */
object LogRedactor {
    private const val TAG = "YunX-DL"

    fun url(url: String): String = runCatching {
        val i = url.indexOf('?')
        if (i in 0 until url.length - 1) {
            // 只保留 host + 前 40 字符的查询参数
            val query = url.substring(i + 1)
            val trimmed = if (query.length > 40) query.take(20) + "…(len=${query.length})" else query
            url.substring(0, i + 1) + trimmed
        } else url
    }.getOrDefault(url)

    fun cookie(cookie: String): String =
        if (cookie.length <= 8) "***" else cookie.take(4) + "…(len=${cookie.length})"
}