package com.astradownloader

import android.content.Context
import com.astraisland.client.IslandClient
import com.astraisland.protocol.ActivityBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * 星河岛接入层：
 * - 持有 IslandClient 并维护连接（断线自动重连）
 * - 识别可解析链接后投送「流体云卡片」（带 下载 / 停止 按钮）
 * - 下载过程中实时更新进度卡片
 * - 暴露连接状态供 UI 观察
 */
class IslandBridge(
    context: Context,
    private val actionListener: (activityId: String, action: String) -> Unit
) {
    lateinit var island: IslandClient
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var reconnectJob: Job? = null
    private var reconnectAttempts = 0

    /** 连接状态，供 UI 观察 */
    private val _connectionState = MutableStateFlow(IslandClient.State.NOT_INSTALLED)
    val connectionState: StateFlow<IslandClient.State> = _connectionState.asStateFlow()

    /** 完整状态文本（含协议版本） */
    private val _statusText = MutableStateFlow("未初始化")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    /** 断线期间待恢复的活跃内容（重连后自动重投） */
    private val pendingContents = ConcurrentHashMap.newKeySet<String>()

    init {
        island = IslandClient(context) { activityId, action ->
            actionListener(activityId, action)
        }
        island.onReadyChanged = { ready ->
            updateStatus(ready)
            if (ready) {
                reconnectAttempts = 0
                reconnectJob?.cancel()
                _connectionState.value = IslandClient.State.READY
                reattachPendingContents()
            }
        }
    }

    private fun updateStatus(ready: Boolean) {
        val s = island.state
        _connectionState.value = s
        _statusText.value = buildString {
            when (s) {
                IslandClient.State.NOT_INSTALLED -> append("未发现星流宿主")
                IslandClient.State.WAITING -> append("等待星河岛（模块未启用或系统界面未注入）")
                IslandClient.State.REJECTED -> append("被岛拒绝（包名/UID 不符）")
                IslandClient.State.READY -> append("已连接 · 通信 v${island.islandProtocolVersion}")
            }
            if (!ready && s != IslandClient.State.READY) {
                append(" · 尝试重连中")
            }
        }
    }

    fun connect() {
        if (island.state == IslandClient.State.READY) return
        island.connect()
        // 防抖：有时 READY 后 state 已就绪；若仍非 READY 且连接可重试，安排自动重连
        scheduleReconnectIfNeeded()
    }

    private fun scheduleReconnectIfNeeded() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(3000)
            if (island.state != IslandClient.State.READY &&
                island.state != IslandClient.State.NOT_INSTALLED) {
                reconnectAttempts++
                reconnectProgress()
                island.connect()
                scheduleReconnectIfNeeded()
            }
        }
    }

    private fun reconnectProgress() {
        _statusText.value = when (island.state) {
            IslandClient.State.WAITING -> "等待星河岛（第${reconnectAttempts}次重连）..."
            else -> "重连中（尝试 ${reconnectAttempts}）..."
        }
    }

    fun isReady(): Boolean = island.state == IslandClient.State.READY

    val protocolVersion: Int get() = island.islandProtocolVersion

    /** 标记一个内容 id 需要在重连后恢复 */
    fun trackContent(id: String) {
        pendingContents.add(id)
        if (island.state == IslandClient.State.READY) {
            // 若已就绪无需恢复（直接投递）
        }
    }

    private fun reattachPendingContents() {
        // 占位：真实恢复逻辑由上层在 onReadyChanged(true) 时用 listMine 对账后重投
        pendingContents.clear()
    }

    fun showLinkCard(
        cardId: String,
        title: String,
        subtitle: String,
        downloadEnabled: Boolean = true
    ): Int = island.start(ActivityBundle.encodeActivity(
        id = cardId,
        kind = "LIVE_UPDATE",
        compactLeading = ActivityBundle.encodeSlot("icon",
            icon = ActivityBundle.encodeIcon("builtin", builtin = "DOWNLOAD")),
        compactTrailing = ActivityBundle.encodeSlot("ring", fraction = 0f),
        expanded = ActivityBundle.encodeExpanded(
            template = "PROGRESS",
            title = title,
            subtitle = subtitle,
            progress = ActivityBundle.encodeProgress(fraction = 0f, startLabel = "等待开始"),
            actions = arrayListOf(
                ActivityBundle.encodeAction("download", "下载", primary = downloadEnabled),
                ActivityBundle.encodeAction("stop", "停止", destructive = true),
            ),
        ),
        alertOnStart = true,
        hideWhenSourceForeground = false,
    ))

    fun updateDownloadProgress(
        cardId: String,
        downloaded: Long,
        total: Long
    ): Int {
        val fraction = if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f
        val percent = if (total > 0) ((downloaded * 100) / total).toInt() else -1
        return island.start(ActivityBundle.encodeActivity(
            id = cardId,
            kind = "LIVE_UPDATE",
            compactLeading = ActivityBundle.encodeSlot("icon",
                icon = ActivityBundle.encodeIcon("builtin", builtin = "DOWNLOAD"), breathing = true),
            compactTrailing = ActivityBundle.encodeSlot("ring", fraction = fraction),
            expanded = ActivityBundle.encodeExpanded(
                template = "PROGRESS",
                title = "正在下载",
                subtitle = if (percent >= 0) "进度 ${percent}%" else "准备中",
                progress = ActivityBundle.encodeProgress(
                    fraction = fraction,
                    startLabel = if (percent >= 0) "$percent%" else "...",
                    marker = ActivityBundle.encodeIcon("builtin", builtin = "DOWNLOAD"),
                ),
                actions = arrayListOf(
                    ActivityBundle.encodeAction("stop", "停止", destructive = true),
                ),
            ),
            hideWhenSourceForeground = false,
        ))
    }

    fun finishDownload(cardId: String, success: Boolean, text: String): Int =
        island.end(cardId, ActivityBundle.encodeOutro(success = success, text = text))

    fun end(cardId: String): Int = island.end(cardId)
}