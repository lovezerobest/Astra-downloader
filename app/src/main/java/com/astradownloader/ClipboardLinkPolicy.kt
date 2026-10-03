package com.astradownloader

/** Rules for automatically dispatching a clipboard link from background listeners. */
object ClipboardLinkPolicy {
    @JvmStatic
    fun isDownloadLink(text: String): Boolean {
        val resolved = LinkResolver.resolve(text) ?: return false
        if (text.contains("download", ignoreCase = true)) return true
        return resolved is ResolvedLink.Share && resolved.platform in setOf(
            "QUARK", "UC", "PAN123", "BAIDU"
        )
    }
}
