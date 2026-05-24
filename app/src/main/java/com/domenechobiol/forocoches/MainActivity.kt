package com.domenechobiol.forocoches

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GestureDetectorCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var repo: IgnoreListRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview)
        configureWebView()
        setupSwipeNavigation()
        webView.loadUrl("https://forocoches.com/foro/")
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
        webView.webViewClient = ForocochesWebViewClient(this, repo)
        val notifRepo = NotificationRepository(this)
        webView.addJavascriptInterface(SettingsBridge(repo, notifRepo, webView), "Android")
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

    private fun setupSwipeNavigation() {
        val gestureDetector = GestureDetectorCompat(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                e1 ?: return false
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y
                if (abs(diffX) < abs(diffY) * 1.5f) return false
                if (abs(diffX) < 80f || abs(velocityX) < 200f) return false
                return if (diffX > 0) {
                    if (webView.canGoBack()) { webView.goBack(); true } else false
                } else {
                    if (webView.canGoForward()) { webView.goForward(); true } else false
                }
            }
        })
        webView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false
        }
    }

    private fun startNotificationPolling() {
        lifecycleScope.launch {
            delay(5_000) // espera inicial para que el WebView cargue y guarde cookies
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
                                "Tienes $diff nuevo${if (diff == 1) "" else "s"} mensaje${if (diff == 1) "" else "s"} privado${if (diff == 1) "" else "s"}"
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
                                "Tienes $diff nueva${if (diff == 1) "" else "s"} notificación${if (diff == 1) "" else "es"}"
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

    @Deprecated("Needed for API < 33")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }
}
