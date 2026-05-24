package com.domenechobiol.forocoches

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
        val startUrl = intent.getStringExtra("url") ?: "https://forocoches.com/foro/"
        webView.loadUrl(startUrl)
        fetchIgnoreListIfNeeded()
        requestNotificationPermission()
        startNotificationPolling()
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
        webView.webViewClient = ForocochesWebViewClient(this, repo, keywordRepo) {
            swipeRefresh.isRefreshing = false
        }
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
                        val notifRepo = NotificationRepository(this@MainActivity)
                        val fetcher = NotificationFetcher()
                        val html = fetcher.fetchMainPage(cookie)
                        val (pmCount, notifCount) = fetcher.parseAllCounts(html)

                        val lastPm = notifRepo.getLastPmCount()
                        if (lastPm >= 0 && pmCount > lastPm) {
                            val diff = pmCount - lastPm
                            NotificationHelper.show(
                                this@MainActivity,
                                NotificationHelper.ID_PM,
                                "FC+ Mensajes Privados",
                                "Tienes $diff nuevo${if (diff == 1) "" else "s"} mensaje${if (diff == 1) "" else "s"} privado${if (diff == 1) "" else "s"}",
                                pmCount,
                                "https://forocoches.com/foro/private.php"
                            )
                        }
                        notifRepo.setLastPmCount(pmCount)

                        val lastNotif = notifRepo.getLastNotifCount()
                        if (lastNotif >= 0 && notifCount > lastNotif) {
                            val diff = notifCount - lastNotif
                            NotificationHelper.show(
                                this@MainActivity,
                                NotificationHelper.ID_NOTIF,
                                "FC+ Notificaciones",
                                "Tienes $diff nueva${if (diff == 1) "" else "s"} notificación${if (diff == 1) "" else "es"}",
                                notifCount
                            )
                        }
                        notifRepo.setLastNotifCount(notifCount)
                    } catch (_: Exception) { }
                }
                delay(60_000)
            }
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

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        val url = intent.getStringExtra("url") ?: return
        webView.loadUrl(url)
    }

    @Deprecated("Needed for API < 33")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }
}
