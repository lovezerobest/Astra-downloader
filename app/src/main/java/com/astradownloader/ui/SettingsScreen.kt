package com.astradownloader.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astradownloader.RootClipboardMonitor
import com.astradownloader.SettingsRepository

private val THREAD_OPTIONS = listOf(1, 2, 4, 8, 16, 24, 32, 48, 64)

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var monitorStatus by remember { mutableStateOf(if (SettingsRepository.rootClipboardEnabled) "已启用（需 Root）" else "需要 Root 授权") }
    var threads by remember { mutableStateOf(SettingsRepository.threads) }
    var autoDl by remember { mutableStateOf(SettingsRepository.autoDownloadAfterResolve) }
    var showThreadDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Miuix.Background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("设置", fontSize = 24.sp, color = Miuix.OnSurface)

        Text("下载", fontSize = 15.sp, color = Miuix.TextSecondary)
        SettingRow("并发下载线程数", sub = "$threads 线程", onClick = { showThreadDialog = true })

        Text("识别", fontSize = 15.sp, color = Miuix.TextSecondary)
        SettingRow("启动后台复制监听（Root）", sub = monitorStatus, onClick = {
            monitorStatus = "正在申请 Root 并启动..."
            RootClipboardMonitor.start(context) { monitorStatus = it }
        })
        SettingRow("解析成功后自动下载", sub = "关闭则需点卡片「下载」", trailing = SwitchRow(autoDl) {
            autoDl = it
            SettingsRepository.autoDownloadAfterResolve = it
        })

        Text("关于", fontSize = 15.sp, color = Miuix.TextSecondary)
        SettingRow("版本", sub = "1.0.1", onClick = { showAboutDialog = true })
    }

    if (showThreadDialog) {
        var selected by remember(threads) { mutableStateOf(threads) }
        AlertDialog(
            onDismissRequest = { showThreadDialog = false },
            title = { Text("并发下载线程数", fontSize = 18.sp) },
            text = {
                Column {
                    THREAD_OPTIONS.forEach { option ->
                        Text(
                            text = "$option 线程",
                            fontSize = 15.sp,
                            color = if (option == selected) Miuix.BrandBlue else Miuix.OnSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected = option
                                    threads = option
                                    SettingsRepository.threads = option
                                    showThreadDialog = false
                                }
                                .padding(vertical = 10.dp)
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showThreadDialog = false }) { Text("取消") } }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("关于", fontSize = 18.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("作者：sihatianyi&GPT", color = Miuix.OnSurface)
                    Text("致谢", color = Miuix.OnSurface)
                    Text(
                        "https://github.com/CYQawa/YunX",
                        color = Miuix.BrandBlue,
                        modifier = Modifier.clickable {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/CYQawa/YunX")))
                        }
                    )
                    Text(
                        "https://github.com/MuYuanXing/AstraIsland-Developers",
                        color = Miuix.BrandBlue,
                        modifier = Modifier.clickable {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/MuYuanXing/AstraIsland-Developers")))
                        }
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showAboutDialog = false }) { Text("关闭") } }
        )
    }
}

@Composable
private fun SettingRow(label: String, sub: String? = null, trailing: (@Composable () -> Unit)? = null, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Miuix.Surface, RoundedCornerShape(14.dp))
            .let { if (onClick != null) it.clickable { onClick() } else it }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 15.sp, color = Miuix.OnSurface)
            if (sub != null) {
                Spacer(Modifier.height(2.dp))
                Text(sub, fontSize = 12.sp, color = Miuix.TextSecondary)
            }
        }
        if (trailing != null) trailing()
    }
}

@Composable
private fun SwitchRow(checked: Boolean, onChecked: (Boolean) -> Unit): @Composable () -> Unit = {
    Switch(
        checked = checked,
        onCheckedChange = onChecked,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Miuix.BrandBlue,
            checkedTrackColor = Miuix.BrandBlue.copy(alpha = 0.5f)
        )
    )
}
