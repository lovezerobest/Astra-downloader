package com.astradownloader

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.astradownloader.network.BaiduConstants
import com.astradownloader.network.C139Constants
import com.astradownloader.network.Pan123Constants
import com.astradownloader.network.UCConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 通用网盘 WebView 登录页。
 *
 * 通过 Intent extra "platform" 指定平台：
 *  - UC / BAIDU / C139：登录后从 Cookie 提取登录态；
 *  - PAN123：123 云盘把登录态（Bearer JWT）存在网页 localStorage 的 authorToken 键，
 *            登录后从 WebView localStorage 读取并保存。
 */
class WebLoginActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as AstraApp
        val platform = intent.getStringExtra("platform") ?: "UC"

        val isTokenBased = platform == "PAN123"
        val loginUrl: String
        val cookieDomain: String
        when (platform) {
            "BAIDU" -> { loginUrl = BaiduConstants.LOGIN_URL; cookieDomain = BaiduConstants.COOKIE_DOMAIN }
            "C139" -> { loginUrl = C139Constants.LOGIN_URL; cookieDomain = C139Constants.COOKIE_DOMAIN }
            "PAN123" -> { loginUrl = Pan123Constants.WEB_LOGIN_URL; cookieDomain = "" }
            else -> { loginUrl = UCConstants.LOGIN_URL; cookieDomain = UCConstants.COOKIE_DOMAIN }
        }

        val progressBar = ProgressBar(this)
        val statusText = TextView(this).apply {
            text = if (isTokenBased) "请在网页中登录 123 云盘（登录后点下方保存）" else "请在网页中登录 $platform"
        }

        val webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            setBackgroundColor(Color.BLACK)
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    progressBar.visibility = View.VISIBLE
                }
                override fun onPageFinished(view: WebView?, url: String?) {
                    progressBar.visibility = View.GONE
                }
            }
            loadUrl(loginUrl)
        }

        val saveBtn = Button(this).apply {
            text = "保存并登录"
            setOnClickListener {
                if (isTokenBased) {
                    // 从网页 localStorage 读取 authorToken
                    webView.evaluateJavascript(
                        "localStorage.getItem('${Pan123Constants.LOCAL_STORAGE_TOKEN_KEY}')"
                    ) { raw ->
                        val token = raw?.trim('"')?.takeIf { it.isNotBlank() && it != "null" }
                        if (token == null) {
                            Toast.makeText(this@WebLoginActivity, "未获取到登录凭证，请先完成登录", Toast.LENGTH_SHORT).show()
                            return@evaluateJavascript
                        }
                        CoroutineScope(Dispatchers.IO).launch {
                            val ok = app.pan123AccountRepo.saveToken(token)
                            withContext(Dispatchers.Main) {
                                if (ok) { Toast.makeText(this@WebLoginActivity, "登录成功", Toast.LENGTH_SHORT).show(); finish() }
                                else Toast.makeText(this@WebLoginActivity, "登录凭证无效，请重新登录", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } else {
                    val cookie = CookieManager.getInstance().getCookie(cookieDomain)
                    if (cookie.isNullOrBlank()) {
                        Toast.makeText(this@WebLoginActivity, "未获取到登录凭证，请先完成登录", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    CoroutineScope(Dispatchers.IO).launch {
                        val ok = saveCookie(app, platform, cookie)
                        withContext(Dispatchers.Main) {
                            if (ok) { Toast.makeText(this@WebLoginActivity, "登录成功", Toast.LENGTH_SHORT).show(); finish() }
                            else Toast.makeText(this@WebLoginActivity, "登录凭证无效，请重新登录", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(progressBar)
            addView(statusText)
            addView(webView, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(saveBtn)
        }
        setContentView(root)
    }

    private suspend fun saveCookie(app: AstraApp, platform: String, cookie: String): Boolean =
        when (platform) {
            "BAIDU" -> app.baiduAccountRepo.saveBaiduAccount(cookie)
            "C139" -> app.c139AccountRepo.saveC139Account(cookie)
            else -> app.ucAccountRepo.saveUCAccount(cookie)
        }
}