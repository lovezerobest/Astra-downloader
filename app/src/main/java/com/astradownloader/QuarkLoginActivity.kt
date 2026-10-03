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
import com.astradownloader.network.QuarkConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 夸克网盘登录页（WebView 网页登录）。
 * 用户在网页完成登录后点「保存并登录」，从 WebView Cookie 提取登录态并落库。
 */
class QuarkLoginActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as AstraApp

        val progressBar = ProgressBar(this)
        val statusText = TextView(this).apply { text = "请在网页中登录夸克网盘" }

        val webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = QuarkConstants.USER_AGENT
            setBackgroundColor(Color.BLACK)
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    progressBar.visibility = View.VISIBLE
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    progressBar.visibility = View.GONE
                }
            }
            loadUrl(QuarkConstants.LOGIN_URL)
        }

        val saveBtn = Button(this).apply {
            text = "保存并登录"
            setOnClickListener {
                val cookie = CookieManager.getInstance().getCookie(QuarkConstants.COOKIE_DOMAIN)
                if (cookie.isNullOrBlank()) {
                    Toast.makeText(this@QuarkLoginActivity, "未获取到登录凭证，请先完成登录", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                CoroutineScope(Dispatchers.IO).launch {
                    val ok = app.quarkAccountRepo.saveQuarkAccount(cookie)
                    withContext(Dispatchers.Main) {
                        if (ok) {
                            Toast.makeText(this@QuarkLoginActivity, "夸克登录成功", Toast.LENGTH_SHORT).show()
                            finish()
                        } else {
                            Toast.makeText(this@QuarkLoginActivity, "登录凭证无效，请重新登录", Toast.LENGTH_SHORT).show()
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
}