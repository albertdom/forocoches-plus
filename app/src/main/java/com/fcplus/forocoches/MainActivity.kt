package com.fcplus.forocoches

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.MotionEvent
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var repo: IgnoreListRepository

    private var touchDownX = 0f
    private var touchDownY = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        swipeRefresh = findViewById(R.id.swipe_refresh)
        webView = findViewById(R.id.webview)
        configureWebView()
        configureSwipeRefresh()
        val startUrl = TrustedOrigins.trustedUrlOrDefault(intent.getStringExtra("url"))
        webView.loadUrl(startUrl)
        fetchIgnoreListIfNeeded()
        requestNotificationPermission()
        requestBatteryExemption()
        startNotificationPolling()
        if (NotificationRepository(this).isInstantEnabled()) NotificationService.start(this)
    }

    private fun configureWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            userAgentString = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }
        repo = IgnoreListRepository(this)
        val notifRepo = NotificationRepository(this)
        val keywordRepo = KeywordRepository(this)
        webView.webViewClient = ForocochesWebViewClient(
            this, repo, keywordRepo,
            onPageLoad = { swipeRefresh.isRefreshing = false }
        )
        webView.addJavascriptInterface(SettingsBridge(repo, notifRepo, keywordRepo, webView), "Android")
    }

    private fun configureSwipeRefresh() {
        swipeRefresh.setColorSchemeColors(android.graphics.Color.parseColor("#00e5cc"))
        swipeRefresh.setOnChildScrollUpCallback { _, _ -> webView.canScrollVertically(-1) }
        swipeRefresh.setOnRefreshListener { webView.reload() }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = ev.x
                touchDownY = ev.y
            }
            MotionEvent.ACTION_UP -> {
                val diffX = ev.x - touchDownX
                val diffY = ev.y - touchDownY
                if (abs(diffX) > abs(diffY) * 2f && abs(diffX) > 100f) {
                    if (diffX > 0 && webView.canGoBack()) { webView.goBack(); return true }
                    if (diffX < 0 && webView.canGoForward()) { webView.goForward(); return true }
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun fetchIgnoreListIfNeeded() {
        if (repo.getLastUpdated() != 0L) return
        lifecycleScope.launch {
            delay(3_000)
            val cookie = CookieManager.getInstance().getCookie("https://forocoches.com")
                ?: return@launch
            if (cookie.isBlank()) return@launch
            try {
                val users = IgnoreListFetcher().fetch(cookie)
                if (users.isNotEmpty()) repo.setIgnoredUsers(users)
            } catch (_: Exception) { }
        }
    }

    private fun startNotificationPolling() {
        lifecycleScope.launch {
            delay(5_000)
            while (true) {
                val cookie = CookieManager.getInstance().getCookie("https://forocoches.com")
                if (!cookie.isNullOrBlank()) {
                    try {
                        NotificationChecker.check(this@MainActivity, cookie)
                    } catch (_: Exception) { }
                }
                delay(60_000)
            }
        }
    }

    /**
     * Pide eximir la app de la optimización de batería. Sin esto, Samsung/Xiaomi/etc.
     * "duermen" la app y matan el NotificationWorker de fondo. Solo pregunta si aún no
     * está exenta.
     */
    private fun requestBatteryExemption() {
        val pm = getSystemService(PowerManager::class.java) ?: return
        if (pm.isIgnoringBatteryOptimizations(packageName)) return
        try {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
            )
        } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (_: Exception) { }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }
    }

    override fun onPause() {
        super.onPause()
        // Persiste las cookies de sesión a disco para que el NotificationWorker en
        // background no haga el fetch deslogueado.
        CookieManager.getInstance().flush()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        val url = TrustedOrigins.trustedUrlOrDefault(intent.getStringExtra("url"))
        webView.loadUrl(url)
    }

    @Deprecated("Needed for API < 33")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }
}
