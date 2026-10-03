package com.astradownloader.network

/** 网盘平台。 */
enum class SharePlatform { QUARK, UC, XUNLEI, BAIDU, C139, PAN123 }

data class ParsedShare(
    val shareId: String,
    val pwd: String?,
    val platform: SharePlatform
)

/** 从分享链接或整段分享文案中提取平台、分享 ID 和提取码。 */
object ShareLinkParser {
    private val urlRegex = Regex("""https?://[^\s]+""")
    private val quarkShareIdRegex = Regex("""pan\.quark\.cn/s/([A-Za-z0-9]+)""", RegexOption.IGNORE_CASE)
    private val ucShareIdRegex = Regex("""drive\.uc\.cn/s/([A-Za-z0-9]+)""", RegexOption.IGNORE_CASE)
    private val xunleiShareIdRegex = Regex("""pan\.xunlei\.com/s/([A-Za-z0-9_-]+)""", RegexOption.IGNORE_CASE)
    private val baiduShareIdRegex = Regex("""pan\.baidu\.com/s/(1[A-Za-z0-9_-]+)""", RegexOption.IGNORE_CASE)
    private val c139ShareIdRegex = Regex("""yun\.139\.com/shareweb/.*?/w/i/([A-Za-z0-9_-]+)""", RegexOption.IGNORE_CASE)
    private val pan123ShareIdRegex = Regex("""123(?:865|pan)\.(?:com|cn)/s/([A-Za-z0-9]+-[A-Za-z0-9]+)""", RegexOption.IGNORE_CASE)
    private val pan123ShareSubRegex = Regex("""share\.123pan\.cn/123pan/([A-Za-z0-9-]+)""", RegexOption.IGNORE_CASE)
    private val pan123SrrRegex = Regex("""api/srr\?sk=([A-Za-z0-9-]+)""", RegexOption.IGNORE_CASE)
    private val pwdInUrlRegex = Regex("""[?&]pwd=([A-Za-z0-9]+)""")
    private val pwdInTextRegex = Regex("""(?:提取码|访问码|密码)[：:]\s*([A-Za-z0-9]{4,8})""")

    fun parse(text: String): ParsedShare? {
        val url = urlRegex.find(text.trim())?.value
            ?.trimEnd('。', '，', ',', '；', ';', ')', ']', '}', '"', '\'')
            ?: return null

        fun password(): String? = pwdInUrlRegex.find(url)?.groupValues?.getOrNull(1)
            ?: pwdInTextRegex.find(text)?.groupValues?.getOrNull(1)

        quarkShareIdRegex.find(url)?.groupValues?.getOrNull(1)?.let {
            return ParsedShare(it, password(), SharePlatform.QUARK)
        }
        ucShareIdRegex.find(url)?.groupValues?.getOrNull(1)?.let {
            return ParsedShare(it, password(), SharePlatform.UC)
        }
        xunleiShareIdRegex.find(url)?.groupValues?.getOrNull(1)?.let {
            return ParsedShare(it, password(), SharePlatform.XUNLEI)
        }
        baiduShareIdRegex.find(url)?.groupValues?.getOrNull(1)?.let {
            return ParsedShare(it.removePrefix("1"), password(), SharePlatform.BAIDU)
        }
        c139ShareIdRegex.find(url)?.groupValues?.getOrNull(1)?.let {
            return ParsedShare(it, password(), SharePlatform.C139)
        }
        pan123ShareIdRegex.find(url)?.groupValues?.getOrNull(1)?.let {
            return ParsedShare(it, password(), SharePlatform.PAN123)
        }
        pan123ShareSubRegex.find(url)?.groupValues?.getOrNull(1)?.let {
            return ParsedShare(it, password(), SharePlatform.PAN123)
        }
        pan123SrrRegex.find(url)?.groupValues?.getOrNull(1)?.let {
            return ParsedShare(it, password(), SharePlatform.PAN123)
        }
        return null
    }
}
