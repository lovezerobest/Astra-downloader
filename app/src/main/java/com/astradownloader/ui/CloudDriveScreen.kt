package com.astradownloader.ui

import android.content.Intent
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astradownloader.AstraApp
import com.astradownloader.QuarkLoginActivity
import com.astradownloader.WebLoginActivity
import com.astradownloader.XunleiLoginActivity

@Composable
fun CloudDriveScreen(app: AstraApp, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Miuix.Background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("网盘", fontSize = 24.sp, color = Miuix.OnSurface)
        Text("登录网盘", fontSize = 17.sp, color = Miuix.OnSurface)
        LoginButton("夸克网盘") { launch(app, QuarkLoginActivity::class.java) }
        LoginButton("UC 网盘") { launch(app, WebLoginActivity::class.java, "UC") }
        LoginButton("百度网盘") { launch(app, WebLoginActivity::class.java, "BAIDU") }
        LoginButton("139和彩云") { launch(app, WebLoginActivity::class.java, "C139") }
        LoginButton("123 云盘") { launch(app, WebLoginActivity::class.java, "PAN123") }
        LoginButton("迅雷网盘") { launch(app, XunleiLoginActivity::class.java) }
    }
}

private fun launch(app: AstraApp, activity: Class<*>, platform: String? = null) {
    val intent = Intent(app, activity).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (platform != null) intent.putExtra("platform", platform)
    app.startActivity(intent)
}

@Composable
private fun LoginButton(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Miuix.Surface, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 15.sp, color = Miuix.OnSurface)
            Spacer(Modifier.height(2.dp))
            Text("点击登录", fontSize = 12.sp, color = Miuix.TextSecondary)
        }
        Text("›", fontSize = 20.sp, color = Miuix.TextSecondary)
    }
}
