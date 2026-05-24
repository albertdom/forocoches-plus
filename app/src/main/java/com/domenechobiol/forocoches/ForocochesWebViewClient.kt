package com.domenechobiol.forocoches

import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient

class ForocochesWebViewClient(
    private val context: Context,
    private val repo: IgnoreListRepository,
    private val keywordRepo: KeywordRepository,
    private val onPageLoad: (() -> Unit)? = null
) : WebViewClient() {

    private val contentJs: String by lazy {
        context.assets.open("content.js").bufferedReader().readText()
    }

    private val adblockCss: String by lazy {
        context.assets.open("adblock.css").bufferedReader().readText()
    }

    private val settingsPanelJs: String by lazy {
        context.assets.open("settings-panel.js").bufferedReader().readText()
    }

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? {
        if (AdBlocker.shouldBlock(request.url.toString())) {
            return WebResourceResponse("text/plain", "utf-8", "".byteInputStream())
        }
        return null
    }

    override fun onPageFinished(view: WebView, url: String) {
        injectCss(view, adblockCss)
        view.evaluateJavascript(contentJs, null)
        view.evaluateJavascript(settingsPanelJs, null)
        onPageLoad?.invoke()
    }

    private fun injectCss(view: WebView, css: String) {
        val escaped = css
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "")
        view.evaluateJavascript(
            """(function(){var s=document.createElement('style');s.textContent="$escaped";document.head&&document.head.appendChild(s);})();""",
            null
        )
    }

}
