package com.astradownloader

import android.app.Application
import android.util.Log
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import android.os.Handler
import android.os.Looper
import com.astradownloader.db.AppDatabase
import com.astradownloader.network.BaiduApi
import com.astradownloader.network.C139Api
import com.astradownloader.network.HttpClients
import com.astradownloader.network.Pan123Api
import com.astradownloader.network.QuarkApi
import com.astradownloader.network.UCApi
import com.astradownloader.network.XunleiApi
import com.astradownloader.repository.BaiduAccountRepository
import com.astradownloader.repository.BaiduResolveRepository
import com.astradownloader.repository.C139AccountRepository
import com.astradownloader.repository.C139ResolveRepository
import com.astradownloader.repository.Pan123AccountRepository
import com.astradownloader.repository.Pan123ResolveRepository
import com.astradownloader.repository.QuarkAccountRepository
import com.astradownloader.repository.QuarkResolveRepository
import com.astradownloader.repository.UCAccountRepository
import com.astradownloader.repository.UCResolveRepository
import com.astradownloader.repository.XunleiAccountRepository
import com.astradownloader.repository.XunleiResolveRepository

class AstraApp : Application() {
    lateinit var coordinator: DownloadCoordinator
        private set

    lateinit var db: AppDatabase
        private set

    // ---- API 实例 ----
    lateinit var quarkApi: QuarkApi; private set
    lateinit var ucApi: UCApi; private set
    lateinit var xunleiApi: XunleiApi; private set
    lateinit var baiduApi: BaiduApi; private set
    lateinit var c139Api: C139Api; private set
    lateinit var pan123Api: Pan123Api; private set

    // ---- 账号仓库 ----
    lateinit var quarkAccountRepo: QuarkAccountRepository; private set
    lateinit var ucAccountRepo: UCAccountRepository; private set
    lateinit var xunleiAccountRepo: XunleiAccountRepository; private set
    lateinit var baiduAccountRepo: BaiduAccountRepository; private set
    lateinit var c139AccountRepo: C139AccountRepository; private set
    lateinit var pan123AccountRepo: Pan123AccountRepository; private set

    // ---- 解析仓库 ----
    lateinit var quarkResolveRepo: QuarkResolveRepository; private set
    lateinit var ucResolveRepo: UCResolveRepository; private set
    lateinit var xunleiResolveRepo: XunleiResolveRepository; private set
    lateinit var baiduResolveRepo: BaiduResolveRepository; private set
    lateinit var c139ResolveRepo: C139ResolveRepository; private set
    lateinit var pan123ResolveRepo: Pan123ResolveRepository; private set

    override fun onCreate() {
        super.onCreate()
        AppHolder.init(this)

        db = AppDatabase.get(this)

        // 初始化各平台 API 与仓库
        quarkApi = QuarkApi { HttpClients.apiClient() }
        ucApi = UCApi { HttpClients.apiClient() }
        xunleiApi = XunleiApi { HttpClients.apiClient() }
        baiduApi = BaiduApi { HttpClients.apiClient() }
        c139Api = C139Api { HttpClients.apiClient() }
        pan123Api = Pan123Api { HttpClients.apiClient() }

        quarkAccountRepo = QuarkAccountRepository(db.quarkAccountDao(), quarkApi)
        ucAccountRepo = UCAccountRepository(db.ucAccountDao(), ucApi)
        xunleiAccountRepo = XunleiAccountRepository(db.xunleiAccountDao(), xunleiApi)
        baiduAccountRepo = BaiduAccountRepository(db.baiduAccountDao(), baiduApi)
        c139AccountRepo = C139AccountRepository(db.c139AccountDao())
        pan123AccountRepo = Pan123AccountRepository(db.pan123AccountDao(), pan123Api)

        quarkResolveRepo = QuarkResolveRepository(quarkApi)
        ucResolveRepo = UCResolveRepository(ucApi)
        xunleiResolveRepo = XunleiResolveRepository(
            api = xunleiApi,
            accountProvider = { runCatching { xunleiAccountRepo.getAccount()?.accessToken }.getOrNull() },
            deviceIdProvider = { runCatching { xunleiAccountRepo.getAccount()?.deviceId }.getOrNull() },
            captchaProvider = { runCatching { xunleiAccountRepo.getAccount()?.captchaToken }.getOrNull() },
            refreshProvider = {
                runCatching {
                    val acc = xunleiAccountRepo.getAccount() ?: return@runCatching null
                    val newTokens = xunleiApi.refreshToken(acc.refreshToken, acc.deviceId) ?: return@runCatching null
                    xunleiAccountRepo.updateTokens(newTokens.first, newTokens.second)
                    newTokens.first to newTokens.second
                }.getOrNull()
            },
        )
        baiduResolveRepo = BaiduResolveRepository(baiduApi)
        c139ResolveRepo = C139ResolveRepository(c139Api)
        pan123ResolveRepo = Pan123ResolveRepository(
            api = pan123Api,
            tokenProvider = { runCatching { pan123AccountRepo.getAccount()?.accessToken }.getOrNull() },
        )

        coordinator = DownloadCoordinator(this)
        coordinator.connect()

        startMainService()
        // 后台剪贴板监听依赖特权身份；Root 已授权时自动接管，失败只记录不打断启动。
        RootClipboardMonitor.start(this) { Log.i("AstraClipboard", "root monitor: $it") }
    }

    /** 启动常驻前台服务：后台监听剪贴板，复制链接自动投递岛卡片。 */
    private fun startMainService() {
        runCatching {
            val intent = Intent(this, MainService::class.java)
            ContextCompat.startForegroundService(this, intent)
        }
    }

}