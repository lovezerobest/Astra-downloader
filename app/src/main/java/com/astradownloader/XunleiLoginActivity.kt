package com.astradownloader

import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 迅雷网盘登录页（短信验证码登录）。
 *
 * 流程：输入手机号 → 发送验证码（sendSms）→ 输入 6 位验证码 → 登录（loginWithSms，
 * 内部完成 captcha/init → signin/token 换取 access/refresh token 并落库）。
 */
class XunleiLoginActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as AstraApp
        val scope = CoroutineScope(Dispatchers.IO)

        var creditKey = ""
        var smsToken = ""

        val statusText = TextView(this).apply { text = "输入手机号，获取验证码后登录迅雷网盘" }

        val mobileInput = EditText(this).apply {
            hint = "手机号"
            inputType = android.text.InputType.TYPE_CLASS_PHONE
        }
        val codeInput = EditText(this).apply {
            hint = "短信验证码"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }

        val sendBtn = Button(this).apply {
            text = "发送验证码"
            setOnClickListener {
                val mobile = mobileInput.text.toString().trim()
                if (mobile.length < 11) {
                    Toast.makeText(this@XunleiLoginActivity, "请输入正确的手机号", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                statusText.text = "正在发送验证码..."
                scope.launch {
                    val step = runCatching { app.xunleiAccountRepo.sendSms(mobile) }.getOrNull()
                    withContext(Dispatchers.Main) {
                        if (step != null) {
                            creditKey = step.smsCreditKey
                            smsToken = step.smsToken
                            statusText.text = if (step.needSms) "验证码已发送，请查收短信" else (step.message.ifBlank { "验证码已发送" })
                        } else {
                            statusText.text = "验证码发送失败，请重试"
                        }
                    }
                }
            }
        }

        val loginBtn = Button(this).apply {
            text = "登录"
            setOnClickListener {
                val mobile = mobileInput.text.toString().trim()
                val code = codeInput.text.toString().trim()
                if (mobile.length < 11 || code.length < 4) {
                    Toast.makeText(this@XunleiLoginActivity, "请填写手机号与验证码", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                statusText.text = "正在登录..."
                scope.launch {
                    val ok = runCatching {
                        app.xunleiAccountRepo.loginWithSms(mobile, code, creditKey, smsToken)
                    }.getOrDefault(false)
                    withContext(Dispatchers.Main) {
                        if (ok) {
                            Toast.makeText(this@XunleiLoginActivity, "迅雷登录成功", Toast.LENGTH_SHORT).show()
                            finish()
                        } else {
                            statusText.text = "登录失败：验证码错误或已过期"
                        }
                    }
                }
            }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(48, 120, 48, 48)
            addView(statusText)
            addView(mobileInput, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(sendBtn)
            addView(codeInput, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(loginBtn)
        }
        setContentView(root)
    }
}