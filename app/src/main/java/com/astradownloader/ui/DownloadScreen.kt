package com.astradownloader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astradownloader.DownloadCoordinator
import com.astradownloader.DownloadCoordinator.DownloadItem
import com.astradownloader.DownloadState

/** 下载管理页（MIUI 风格列表）。 */
@Composable
fun DownloadScreen(coordinator: DownloadCoordinator, modifier: Modifier = Modifier) {
    val items by coordinator.items.collectAsState()

    Column(modifier = modifier.fillMaxSize().background(Miuix.Background)) {
        Text(
            text = "下载管理",
            fontSize = 22.sp,
            color = Miuix.OnSurface,
            modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp)
        )
        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无下载任务", color = Miuix.TextSecondary, fontSize = 15.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items, key = { it.id }) { item -> DownloadRow(coordinator, item) }
            }
        }
    }
}

@Composable
private fun DownloadRow(coordinator: DownloadCoordinator, item: DownloadItem) {
    val progress = if (item.total > 0) (item.downloaded.toFloat() / item.total).coerceIn(0f, 1f) else 0f
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Miuix.Surface)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = item.fileName,
                fontSize = 15.sp,
                color = Miuix.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.size(8.dp))
            StateBadge(item.state)
        }

        Spacer(Modifier.height(8.dp))

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = if (item.state == DownloadState.FAILED) Miuix.Danger else Miuix.BrandBlue,
            trackColor = Miuix.Divider,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = progressText(item) + speedText(item),
            fontSize = 12.sp,
            color = Miuix.TextSecondary
        )

        val canPauseOrResume = item.state == DownloadState.DOWNLOADING ||
            item.state == DownloadState.PAUSED || item.state == DownloadState.FAILED
        if (canPauseOrResume || item.state == DownloadState.COMPLETED) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (item.state == DownloadState.DOWNLOADING) {
                    SmallButton("暂停", Miuix.Warning) { coordinator.pauseItem(item.id) }
                } else if (item.state == DownloadState.PAUSED || item.state == DownloadState.FAILED) {
                    SmallButton("继续", Miuix.BrandBlue) { coordinator.resumeItem(item.id) }
                }
                SmallButton("删除", Miuix.Danger) { coordinator.removeItem(item.id) }
                if (item.state == DownloadState.COMPLETED) {
                    SmallButton("打开", Miuix.BrandBlue) { coordinator.openItem(item.id) }
                }
            }
        }
    }
}

@Composable
private fun SmallButton(label: String, textColor: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = textColor,
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.height(30.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
    ) {
        Text(label, fontSize = 13.sp)
    }
}

@Composable
private fun StateBadge(state: DownloadState) {
    val (label, color) = when (state) {
        DownloadState.DOWNLOADING -> "下载中" to Miuix.BrandBlue
        DownloadState.PAUSED -> "已暂停" to Miuix.Warning
        DownloadState.COMPLETED -> "已完成" to Miuix.Success
        DownloadState.FAILED -> "失败" to Miuix.Danger
        DownloadState.PENDING -> "等待" to Miuix.TextSecondary
    }
    Text(
        text = label,
        fontSize = 11.sp,
        color = color,
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

private fun progressText(item: DownloadItem): String {
    if (item.total <= 0) return "已下载 ${formatSize(item.downloaded)}"
    val pct = ((item.downloaded.toDouble() / item.total) * 100).toInt().coerceIn(0, 100)
    return "$pct% · ${formatSize(item.downloaded)}/${formatSize(item.total)}"
}

private fun speedText(item: DownloadItem): String {
    return if (item.speed > 0 && item.state == DownloadState.DOWNLOADING)
        " · ${formatSpeed(item.speed)}" else ""
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var v = bytes.toDouble()
    var i = 0
    while (v >= 1024 && i < units.size - 1) { v /= 1024; i++ }
    return String.format("%.1f%s", v, units[i])
}

private fun formatSpeed(bytesPerSec: Long): String {
    if (bytesPerSec <= 0) return ""
    val units = arrayOf("B/s", "KB/s", "MB/s", "GB/s")
    var v = bytesPerSec.toDouble()
    var i = 0
    while (v >= 1024 && i < units.size - 1) { v /= 1024; i++ }
    return String.format("%.1f%s", v, units[i])
}