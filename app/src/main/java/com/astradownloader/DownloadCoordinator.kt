package com.astradownloader

import android.util.Log


import android.os.Handler
import android.os.Looper
import com.astradownloader.network.BaiduConstants
import com.astradownloader.network.C139Constants
import com.astradownloader.network.HttpClients
import com.astradownloader.network.Pan123Constants
import com.astradownloader.network.QuarkConstants
import com.astradownloader.network.UCConstants
import com.astradownloader.network.XunleiConstants
import com.astradownloader.repository.Pan123ResolveRepository
import com.astradownloader.repository.XunleiResolveRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 下载协调器：把 星河岛卡片 与 网盘解析&下载引擎 串联。
 * - 识别到链接 -> 显示流体云卡片（下载 / 停止按钮）
 * - 网盘分享：复制时异步预解析（校验登录、取直链），点「下载」时复用结果并开始下载
 * - 通用直链：直接下载
 * - 点「停止」 -> 取消 / 暂停对应任务
 */
class DownloadCoordinator(private val app: AstraApp) {

    private val TAG = "Coordinator"
    val bridge: IslandBridge
    private val manager: DownloadManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val cardSeq = AtomicLong(0)

    private val mainHandler = Handler(Looper.getMainLooper())
    private val cardToTask = ConcurrentHashMap<String, Long>()
    private val pendingDirect = ConcurrentHashMap<String, String>()
    private val pendingShare = ConcurrentHashMap<String, String>()
    private val pendingResolves = ConcurrentHashMap<String, Deferred<Result<ResolvedDownload>>>()

    /** 岛未就绪时暂存的待投递链接（去重），READY 后自动重放。 */
    private val pendingDetections = ConcurrentHashMap.newKeySet<String>()

    @Volatile
    var lastDiag: String = ""
    private var lastDetectedText: String? = null
    private var lastDetectedAt: Long = 0L
    private var lastDetectedClipTimestamp: Long = 0L
    @Volatile private var shareTextBeingDownloaded: String? = null
    private val downloadingCards = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /** 解析得到的可下载项 */
    data class ResolvedDownload(val url: String, val fileName: String, val headers: Map<String, String>)

    /** 暴露给下载管理 UI 的任务项 */
    data class DownloadItem(
        val id: Long,
        val fileName: String,
        val url: String,
        val state: DownloadState,
        val total: Long,
        val downloaded: Long,
        val speed: Long
    )

    private val _items = MutableStateFlow<List<DownloadItem>>(emptyList())
    val items: StateFlow<List<DownloadItem>> = _items.asStateFlow()
    private val itemById = ConcurrentHashMap<Long, DownloadItem>()

    init {
        val saveBase = File(app.getExternalFilesDir(null) ?: app.filesDir, "downloads").apply { mkdirs() }
        manager = DownloadManager(
            downloader = ChunkDownloader { HttpClients.downloadClient() },
            saveDir = saveBase,
            threadCountProvider = { SettingsRepository.threads.coerceIn(1, 64) },
            listener = object : DownloadListener {
                override fun onProgress(taskId: Long, downloaded: Long, total: Long, stats: DownloadStats) {
                    val cardId = cardToTask.entries.firstOrNull { it.value == taskId }?.key
                    if (cardId != null) {
                        bridge.updateDownloadProgress(cardId, downloaded, total)
                    }
                    itemById[taskId]?.let { cur ->
                        updateItemAt(taskId, cur.copy(downloaded = downloaded, total = if (total > 0) total else cur.total, speed = stats.speed))
                    }
                    Log.d(TAG, "onProgress task=$taskId d=$downloaded t=$total -> island")
                    if (cardId != null) {
                        val cid = cardId
                        mainHandler.post {
                            val r = bridge.updateDownloadProgress(cid, downloaded, total)
                            Log.d(TAG, "island progress update r=$r")
                        }
                    }
                }

                override fun onStateChanged(taskId: Long, state: DownloadState) {
                    val cardId = cardToTask.entries.firstOrNull { it.value == taskId }?.key ?: return
                    when (state) {
                        DownloadState.COMPLETED -> {
                            val t = itemById[taskId]?.total ?: -1L
                            val fin = t
                            mainHandler.post {
                                if (fin > 0) bridge.updateDownloadProgress(cardId, fin, fin)
                                bridge.finishDownload(cardId, true, "下载完成")
                            }
                            Log.d(TAG, "completed: total=$t, end island")
                        }
                        DownloadState.FAILED -> bridge.finishDownload(cardId, false, "下载失败")
                        DownloadState.PAUSED -> bridge.finishDownload(cardId, false, "已停止")
                        else -> {}
                    }
                    itemById[taskId]?.let { cur ->
                        updateItemAt(taskId, cur.copy(state = state, speed = 0L))
                    }
                }
            }
        )
        bridge = IslandBridge(app) { activityId, action ->
            handleCardAction(activityId, action)
        }
        // 岛就绪后自动投递之前缓存的链接
        scope.launch {
            bridge.connectionState.collect {
                if (it == com.astraisland.client.IslandClient.State.READY) {
                    dispatchQueued()
                }
            }
        }
    }

    fun connect() = bridge.connect()
    fun isReady(): Boolean = bridge.isReady()

    /** 诊断：投递一个直链卡片并自动开始下载，验证岛进度同步。 */
    fun testAutoDownload() {
        val url = "https://speed.hetzner.de/100MB.bin"
        val cardId = "dltest-${cardSeq.incrementAndGet()}"
        val r = bridge.showLinkCard(cardId, "100MB.bin", "测试直链下载", true)
        lastDiag = "投递测试卡片 结果码=$r"
        Log.d(TAG, "testAutoDownload showLinkCard r=$r, ready=${bridge.isReady()}")
        scope.launch {
            val fn = "100MB.bin"
            startDownload(cardId, url, fn, emptyMap())
        }
    }

    /** 主页直链下载入口：直接入队下载，进度展示在「下载」页。 */
    fun downloadDirectUrl(url: String, headers: Map<String, String> = emptyMap()) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return
        val fileName = trimmed.substringAfterLast('/').substringBefore('?').ifBlank { "download" }
        val taskId = manager.enqueue(trimmed, fileName, headers)
        upsertItem(DownloadItem(taskId, fileName, trimmed, DownloadState.DOWNLOADING, 0, 0, 0))
    }

    /** 入口：外部给一段文本/链接，识别并投递卡片 */
    fun onLinkDetected(text: String, clipTimestamp: Long = 0L) {
        Log.d(TAG, "onLinkDetected called")
        // 同一次复制事件在服务监听与前台页面重复触发时，只投递一次
        val now = System.currentTimeMillis()
        if (text == lastDetectedText && ((clipTimestamp > 0 && clipTimestamp == lastDetectedClipTimestamp) || now - lastDetectedAt < 5000)) {
            Log.d(TAG, "onLinkDetected dedupe: same clipboard event or within 5s")
            return
        }
        lastDetectedText = text
        lastDetectedAt = now
        lastDetectedClipTimestamp = clipTimestamp

        lastDiag = "识别: ${text.take(40)}"
        val resolved = LinkResolver.resolve(text)
        if (resolved == null) {
            lastDiag = "未识别为可下载/可解析链接"
            Log.d(TAG, "onLinkDetected: resolve=null")
            return
        }
        if (resolved is ResolvedLink.Share && resolved.text == shareTextBeingDownloaded) {
            Log.d(TAG, "onLinkDetected: share already being downloaded, skip")
            return
        }
        bridge.connect()
        pendingDetections.add(text)
        if (bridge.isReady()) {
            dispatchQueued()
        } else {
            lastDiag = "已识别链接，等待星河岛连接后投递..."
        }
    }

    private fun dispatchQueued() {
        if (!bridge.isReady()) return
        val queued = pendingDetections.toList()
        pendingDetections.clear()
        queued.forEach { handleDetected(it) }
    }

    private fun handleDetected(text: String) {
        Log.d(TAG, "handleDetected ready=${bridge.isReady()}")
        val resolved = LinkResolver.resolve(text) ?: return
        when (resolved) {
            is ResolvedLink.Share -> {
                val cardId = "share-${cardSeq.incrementAndGet()}"
                val pwdText = resolved.pwd?.let { "· 已带提取码" } ?: ""
                val r = bridge.showLinkCard(
                    cardId = cardId,
                    title = "网盘分享链接",
                    subtitle = "${platformName(resolved.platform)} $pwdText · 解析中".trim(),
                    downloadEnabled = true,
                )
                pendingShare[cardId] = resolved.text
                lastDiag = "投递网盘卡片 结果码=$r · 解析中"
                // 复制时预解析分享链接；不自动下载，点击下载时复用结果。
                val task = scope.async { resolveSharePlatform(resolved.platform, resolved.text) }
                pendingResolves[cardId] = task
                scope.launch {
                    val result = task.await()
                    if (pendingShare[cardId] != resolved.text) return@launch
                    result.onSuccess { dl ->
                        if (SettingsRepository.autoDownloadAfterResolve) {
                            // 解析成功且开启了「自动下载」：直接开始下载
                            lastDiag = "已解析，自动开始下载: ${dl.fileName}"
                            if (downloadingCards.add(cardId)) {
                                startDownload(cardId, dl.url, dl.fileName, dl.headers)
                            }
                        } else {
                            bridge.showLinkCard(cardId, dl.fileName, "${platformName(resolved.platform)} · 已解析，点击下载")
                            lastDiag = "已解析: ${dl.fileName}"
                        }
                    }.onFailure { error ->
                        bridge.showLinkCard(cardId, "网盘分享链接", "解析失败：${error.message ?: "未知错误"} · 点击下载重试")
                        lastDiag = "解析失败: ${error.message ?: "未知错误"}"
                        pendingResolves.remove(cardId, task)
                    }
                }
            }
            is ResolvedLink.Direct -> {
                val cardId = "dl-${cardSeq.incrementAndGet()}"
                val title = resolved.fileName ?: "下载链接"
                val r = bridge.showLinkCard(
                    cardId = cardId,
                    title = title,
                    subtitle = "点击下载开始",
                    downloadEnabled = true,
                )
                pendingDirect[cardId] = resolved.url
                lastDiag = "投递直链卡片 结果码=$r"
            }
        }
    }

    private fun handleCardAction(cardId: String, action: String) {
        Log.d(TAG, "卡片 $cardId 收到动作 $action")
        when (action) {
            "download" -> {
                // 同一卡片已在下/已触发下载则忽略重复点击，避免两个任务
                if (!downloadingCards.add(cardId)) {
                    Log.d(TAG, "download: card=$cardId already downloading, skip")
                    return
                }
                pendingDirect.remove(cardId)?.let { url ->
                    startDirectDownload(cardId, url)
                } ?: pendingShare.remove(cardId)?.let { link ->
                    startShareResolve(cardId, link)
                }
            }
            "stop" -> {
                val taskId = cardToTask.remove(cardId)
                if (taskId != null) manager.cancel(taskId)
                pendingDirect.remove(cardId)
                pendingShare.remove(cardId)
                pendingResolves.remove(cardId)?.cancel()
                downloadingCards.remove(cardId)
                // 停止后自动收起岛卡片
                mainHandler.post { bridge.end(cardId) }
                Log.d(TAG, "stop: end island card=$cardId")
            }
        }
    }

    private fun startDirectDownload(cardId: String, url: String) {
        val fileName = url.substringAfterLast('/').substringBefore('?').ifBlank { "download" }
        startDownload(cardId, url, fileName, emptyMap())
    }

    private fun startDownload(cardId: String, url: String, fileName: String, headers: Map<String, String>) {
        val taskId = manager.enqueue(url, fileName, headers)
        cardToTask[cardId] = taskId
        bridge.updateDownloadProgress(cardId, 0, -1)
        upsertItem(DownloadItem(taskId, fileName, url, DownloadState.DOWNLOADING, 0, 0, 0))
    }

    private fun upsertItem(item: DownloadItem) {
        itemById[item.id] = item
        _items.value = itemById.values.sortedByDescending { it.id }
    }

    private fun updateItemAt(id: Long, item: DownloadItem) {
        itemById[id] = item
        _items.value = itemById.values.sortedByDescending { it.id }
    }

    fun pauseItem(id: Long) {
        itemById[id]?.let { if (it.state == DownloadState.DOWNLOADING) manager.pause(id) }
    }

    fun resumeItem(id: Long) {
        itemById[id]?.let {
            if (it.state == DownloadState.PAUSED || it.state == DownloadState.FAILED) manager.start(id)
        }
    }

    fun removeItem(id: Long) {
        if (itemById.remove(id) != null) manager.cancel(id)
        _items.value = itemById.values.sortedByDescending { it.id }
        cardToTask.entries.removeAll { it.value == id }
    }

    fun openItem(id: Long) {
        val item = itemById[id] ?: return
        if (item.state != DownloadState.COMPLETED) return
        val file = File(app.getExternalFilesDir(null) ?: app.filesDir, "downloads/${item.fileName}")
        if (!file.exists()) return
        runCatching {
            val uri = androidx.core.content.FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "*/*")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            app.startActivity(intent)
        }
    }

    /** 网盘分享解析：登录校验 → 会话 → 首个文件直链 → 下载 */
    private fun startShareResolve(cardId: String, link: String) {
        bridge.updateDownloadProgress(cardId, 0, -1)
        scope.launch {
            val platform = (LinkResolver.resolve(link) as? ResolvedLink.Share)?.platform
            // 预解析尚未结束时等待同一请求；若之前失败（如未登录）则允许重试。
            val cached = pendingResolves.remove(cardId)?.await()
            val result = if (cached == null || cached.isFailure) resolveSharePlatform(platform, link) else cached
            if (!downloadingCards.contains(cardId)) return@launch
            result.onSuccess { dl ->
                startDownload(cardId, dl.url, dl.fileName, dl.headers)
            }.onFailure { e ->
                downloadingCards.remove(cardId)
                bridge.finishDownload(cardId, false, e.message ?: "解析失败")
            }
        }
    }

    private suspend fun resolveSharePlatform(platform: String?, link: String): Result<ResolvedDownload> {
        val parsed = LinkResolver.resolve(link) as? ResolvedLink.Share ?: return Result.failure(IllegalStateException("无效链接"))
        return when (platform) {
            "QUARK" -> resolveQuark(parsed.text, parsed.pwd)
            "UC" -> resolveUC(parsed.text, parsed.pwd)
            "BAIDU" -> resolveBaidu(parsed.text, parsed.pwd)
            "C139" -> resolveC139(parsed.text, parsed.pwd)
            "PAN123" -> resolvePan123(parsed.text, parsed.pwd)
            "XUNLEI" -> resolveXunlei(parsed.text, parsed.pwd)
            else -> Result.failure(IllegalStateException("不支持的网盘平台：$platform"))
        }
    }

    private suspend fun resolveQuark(link: String, pwd: String?): Result<ResolvedDownload> {
        val cookie = app.quarkAccountRepo.getAccount()?.cookie
        if (cookie.isNullOrBlank()) return Result.failure(IllegalStateException("夸克未登录：请先在应用内登录"))
        return runCatching {
            val session = app.quarkResolveRepo.createSession(link, pwd, cookie).getOrThrow()
            val files = app.quarkResolveRepo.listFiles(session, "0", cookie).getOrThrow()
            val first = pickFile(files)
            val dl = app.quarkResolveRepo.getShareDownloadLink(session, first, cookie).getOrThrow()
            ResolvedDownload(dl.downloadUrl, dl.filename, mapOf(
                "Cookie" to cookie,
                "User-Agent" to QuarkConstants.API_USER_AGENT,
                "Referer" to QuarkConstants.DOWNLOAD_REFERER,
            ))
        }
    }

    private suspend fun resolveUC(link: String, pwd: String?): Result<ResolvedDownload> {
        val cookie = app.ucAccountRepo.getAccount()?.cookie
        if (cookie.isNullOrBlank()) return Result.failure(IllegalStateException("UC网盘未登录：请先在应用内登录"))
        return runCatching {
            val session = app.ucResolveRepo.createSession(link, pwd, cookie).getOrThrow()
            val files = app.ucResolveRepo.listFiles(session, "0", cookie).getOrThrow()
            val first = pickFile(files)
            val dl = app.ucResolveRepo.getShareDownloadLink(session, first, cookie).getOrThrow()
            ResolvedDownload(dl.downloadUrl, dl.filename, mapOf(
                "Cookie" to cookie,
                "User-Agent" to UCConstants.CLOUD_UA,
                "Referer" to UCConstants.DOWNLOAD_REFERER,
            ))
        }
    }

    private suspend fun resolveBaidu(link: String, pwd: String?): Result<ResolvedDownload> {
        val cookie = app.baiduAccountRepo.getAccount()?.cookie
        if (cookie.isNullOrBlank()) return Result.failure(IllegalStateException("百度网盘未登录：请先在应用内登录"))
        return runCatching {
            val session = app.baiduResolveRepo.createSession(link, pwd, cookie).getOrThrow()
            val files = app.baiduResolveRepo.listFiles(session, "0", cookie).getOrThrow()
            val first = pickFile(files)
            val dl = app.baiduResolveRepo.getShareDownloadLink(session, first, cookie).getOrThrow()
            ResolvedDownload(dl.downloadUrl, dl.filename, mapOf(
                "Cookie" to cookie,
                "User-Agent" to BaiduConstants.UA_NETDISK,
            ))
        }
    }

    private suspend fun resolveC139(link: String, pwd: String?): Result<ResolvedDownload> {
        val account = app.c139AccountRepo.getAccount()
        if (account == null || account.cookie.isBlank()) return Result.failure(IllegalStateException("139网盘未登录：请先在应用内登录"))
        val cookie = account.cookie
        return runCatching {
            val session = app.c139ResolveRepo.createSession(link, pwd, cookie).getOrThrow()
            val files = app.c139ResolveRepo.listFiles(session, "0", cookie).getOrThrow()
            val first = pickFile(files)
            val dl = app.c139ResolveRepo.getShareDownloadLink(session, first, cookie).getOrThrow()
            val headers = mutableMapOf<String, String>(
                "User-Agent" to C139Constants.PC_UA,
                "Referer" to "https://yun.139.com/",
            )
            if (account.authorization.isNotBlank()) {
                headers["Authorization"] = account.authorization
            }
            ResolvedDownload(dl.downloadUrl, dl.filename, headers)
        }
    }

    private suspend fun resolvePan123(link: String, pwd: String?): Result<ResolvedDownload> {
        val token = app.pan123AccountRepo.getAccount()?.accessToken
        if (token.isNullOrBlank()) return Result.failure(IllegalStateException("123云盘未登录：请先在应用内登录"))
        return runCatching {
            // 123 的 createSession 需要 token 而不是 cookie
            val session = app.pan123ResolveRepo.createSession(link, pwd, "").getOrThrow()
            val files = app.pan123ResolveRepo.listFiles(session, "0", "").getOrThrow()
            val first = pickFile(files)
            val dl = app.pan123ResolveRepo.getShareDownloadLink(session, first, token).getOrThrow()
            ResolvedDownload(dl.downloadUrl, dl.filename, mapOf(
                "Referer" to Pan123Constants.DOWNLOAD_REFERER,
            ))
        }
    }

    private suspend fun resolveXunlei(link: String, pwd: String?): Result<ResolvedDownload> {
        val account = app.xunleiAccountRepo.getAccount()
        if (account == null || account.accessToken.isBlank()) return Result.failure(IllegalStateException("迅雷网盘未登录：请先在应用内登录"))
        return runCatching {
            val session = app.xunleiResolveRepo.createSession(link, pwd, account.accessToken).getOrThrow()
            val files = app.xunleiResolveRepo.listFiles(session, "0", "").getOrThrow()
            val first = pickFile(files)
            val dl = app.xunleiResolveRepo.getShareDownloadLink(session, first, account.accessToken).getOrThrow()
            ResolvedDownload(dl.downloadUrl, dl.filename, mapOf(
                "User-Agent" to XunleiConstants.APP_UA,
            ))
        }
    }

    private fun pickFile(files: List<com.astradownloader.network.model.ShareFile>):
        com.astradownloader.network.model.ShareFile =
        files.firstOrNull { !it.isdir }
            ?: files.firstOrNull()
            ?: throw IllegalStateException("分享内容为空")

    private fun platformName(platform: String): String = when (platform) {
        "QUARK" -> "夸克"
        "UC" -> "UC"
        "XUNLEI" -> "迅雷"
        "BAIDU" -> "百度"
        "C139" -> "139和彩云"
        "PAN123" -> "123云盘"
        else -> platform
    }
}