package com.astradownloader

import android.util.Log
import com.astradownloader.network.ShareLinkParser

/**
 * 识别结果：一条可下载的直链，或一个待解析的网盘分享链接。
 */
sealed class ResolvedLink {
    /** 通用直链，可直接分片下载 */
    data class Direct(val url: String, val fileName: String? = null) : ResolvedLink()

    /** 网盘分享链接（需要先走对应平台解析取直链） */
    data class Share(val text: String, val platform: String, val shareId: String, val pwd: String?) : ResolvedLink()
}

/**
 * 链接识别器：判断片段里是否含有可解析/可下载的链接，以及其类型。
 * 直链识别采取宽松策略：任何 http(s) URL 都尝试作为直链，不限于常见文件后缀。
 */
object LinkResolver {
    /** 明显只是网页、不适合当直链下载的域名前缀（黑名单，可扩展） */
    private val PAGE_ONLY_HOSTS = listOf(
        "www.baidu.com", "m.baidu.com", "www.google.", "github.com", "gist.github.com",
        "www.youtube.com", "www.bilibili.com", "www.v2ex.com"
    )

    fun resolve(text: String): ResolvedLink? {
        val trimmed = text.trim()
        Log.d("LinkResolve", "resolve input len=${trimmed.length}")
        if (trimmed.isEmpty()) { Log.d("LinkResolve", "empty"); return null }

        val parsed = runCatching { ShareLinkParser.parse(trimmed) }
            .onFailure { Log.e("LinkResolve", "parse exception", it) }
            .getOrNull()
        if (parsed != null) {
            Log.d("LinkResolve", "share platform=${parsed.platform}")
            return ResolvedLink.Share(
                text = trimmed,
                platform = parsed.platform.name,
                shareId = parsed.shareId,
                pwd = parsed.pwd,
            )
        }
        Log.d("LinkResolve", "not a share link")

        val url = extractUrl(trimmed)
        if (url == null) { Log.d("LinkResolve", "no url extracted"); return null }
        Log.d("LinkResolve", "URL extracted")
        if (isLikelyDownloadable(url)) {
            val fn = guessFileName(url)
            Log.d("LinkResolve", "direct URL recognized")
            return ResolvedLink.Direct(url = url, fileName = fn)
        }
        Log.d("LinkResolve", "host blacklisted")
        return null
    }

    private fun extractUrl(text: String): String? {
        val m = Regex("""https?://[^\s，。；、<>"']+""").find(text) ?: return null
        return m.value.trimEnd('.', ',', ';', ')', ']', '}', '"', '\'')
    }

    /** 黑名单外的主机都认为可尝试下载；无主机名则不算。 */
    private fun isLikelyDownloadable(url: String): Boolean {
        val host = Regex("""https?://([^/]+)""").find(url)?.groupValues?.getOrNull(1)?.lowercase() ?: return false
        return PAGE_ONLY_HOSTS.none { host.startsWith(it) }
    }

    /** 从 URL 末段猜测文件名；无有效末段返回 null（下载时再兜底）。 */
    private fun guessFileName(url: String): String? {
        val path = url.substringAfter("://").substringBefore('?').substringBefore('#')
        val last = path.substringAfterLast('/')
        // 排除末尾就是域名（无路径）、空名、纯目录（以 / 结尾已自然排除）
        if (last.isBlank() || last == path) return null
        // 去掉可能的查询残留 / 点号结尾
        val cleaned = last.trimEnd('.', '/')
        return cleaned.ifBlank { null }
    }
}