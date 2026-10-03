package com.astradownloader

import android.Manifest
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.astradownloader.ui.AstraTheme
import com.astradownloader.ui.CloudDriveScreen
import com.astradownloader.ui.DownloadScreen
import com.astradownloader.ui.HomeScreen
import com.astradownloader.ui.Miuix
import com.astradownloader.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    private var clipboardManager: ClipboardManager? = null
    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        if (SettingsRepository.autoDetectClipboard) checkClipboard()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermission()
        val owner = application as AstraApp
        setContent {
            AstraTheme {
                var tab by remember { mutableStateOf(0) }
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Miuix.Background,
                    bottomBar = {
                        NavigationBar(containerColor = Miuix.Surface) {
                            NavigationBarItem(
                                selected = tab == 0,
                                onClick = { tab = 0 },
                                icon = { Icon(Icons.Filled.Home, contentDescription = "首页") },
                                label = { Text("首页", fontSize = 11.sp) }
                            )
                            NavigationBarItem(
                                selected = tab == 1,
                                onClick = { tab = 1 },
                                icon = { Icon(Icons.Filled.Download, contentDescription = "下载") },
                                label = { Text("下载", fontSize = 11.sp) }
                            )
                            NavigationBarItem(
                                selected = tab == 2,
                                onClick = { tab = 2 },
                                icon = { Icon(Icons.Filled.Cloud, contentDescription = "网盘") },
                                label = { Text("网盘", fontSize = 11.sp) }
                            )
                            NavigationBarItem(
                                selected = tab == 3,
                                onClick = { tab = 3 },
                                icon = { Icon(Icons.Filled.Settings, contentDescription = "设置") },
                                label = { Text("设置", fontSize = 11.sp) }
                            )
                        }
                    }
                ) { innerPadding ->
                    when (tab) {
                        0 -> HomeScreen(
                            app = owner,
                            onParseLink = { text -> parseAndResolve(owner, text) },
                            onReadClipboard = ::readLatestClipboardText,
                            modifier = Modifier.padding(innerPadding)
                        )
                        1 -> DownloadScreen(
                            coordinator = owner.coordinator,
                            modifier = Modifier.padding(innerPadding)
                        )
                        2 -> CloudDriveScreen(app = owner, modifier = Modifier.padding(innerPadding))
                        else -> SettingsScreen(modifier = Modifier.padding(innerPadding))
                    }
                }
            }
        }
    }

    private fun parseAndResolve(owner: AstraApp, text: String): Boolean {
        if (LinkResolver.resolve(text) == null) return false
        owner.coordinator.onLinkDetected(text)
        return true
    }

    private fun readLatestClipboardText(): String? = runCatching {
        val clip = getSystemService(ClipboardManager::class.java)?.primaryClip ?: return null
        if (clip.itemCount <= 0) return null
        clip.getItemAt(0).coerceToText(this)?.toString()
    }.getOrNull()

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }
    }

    private fun checkClipboard() {
        val cm = clipboardManager ?: getSystemService(ClipboardManager::class.java) ?: return
        val clip = runCatching { cm.primaryClip }.getOrNull() ?: return
        if (clip.itemCount <= 0) return
        val text = runCatching { clip.getItemAt(0).coerceToText(this).toString().trim() }.getOrNull().orEmpty()
        if (text.isNotBlank() && ClipboardLinkPolicy.isDownloadLink(text)) {
            (application as AstraApp).coordinator.onLinkDetected(text, clip.description?.timestamp ?: 0L)
        }
    }

    override fun onResume() {
        super.onResume()
        clipboardManager = getSystemService(ClipboardManager::class.java)
        clipboardManager?.addPrimaryClipChangedListener(clipboardListener)
        if (SettingsRepository.autoDetectClipboard) checkClipboard()
    }

    override fun onPause() {
        clipboardManager?.removePrimaryClipChangedListener(clipboardListener)
        clipboardManager = null
        super.onPause()
    }
}
