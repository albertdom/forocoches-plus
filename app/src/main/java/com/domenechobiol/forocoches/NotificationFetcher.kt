package com.domenechobiol.forocoches

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class NotificationFetcher {

    companion object {
        private const val BASE_URL = "https://forocoches.com/foro"
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

        // Patrones para PM count: badge-count span cerca de private.php, o formato "(N nuevos)"
        private val PM_BADGE = Regex("""href="private\.php"[\s\S]{0,200}?class="[^"]*(?:badge|count)[^"]*"[^>]*>(\d+)""", RegexOption.IGNORE_CASE)
        private val PM_PARENS = Regex("""href="private\.php"[\s\S]{0,200}?\((\d+)\s*nuevo""", RegexOption.IGNORE_CASE)

        // Patrón para notificaciones generales (menciones, citas, respuestas)
        private val NOTIF_BADGE = Regex("""class="[^"]*notification-count[^"]*"[^>]*>(\d+)""", RegexOption.IGNORE_CASE)
        private val NOTIF_BULLET = Regex("""class="[^"]*notif[^"]*badge[^"]*"[^>]*>(\d+)""", RegexOption.IGNORE_CASE)

        // Thread más reciente de un usuario
        private val THREAD_ID = Regex("""showthread\.php\?t=(\d+)""")
    }

    suspend fun fetchMainPage(cookie: String): String = withContext(Dispatchers.IO) {
        get("$BASE_URL/", cookie)
    }

    suspend fun fetchUserThreadsPage(cookie: String, userId: String): String = withContext(Dispatchers.IO) {
        get("$BASE_URL/search.php?do=finduser&userid=$userId&contenttype=vBForum_Thread&showposts=0", cookie)
    }

    fun parsePmCount(html: String): Int {
        PM_BADGE.find(html)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        PM_PARENS.find(html)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        return 0
    }

    fun parseNotifCount(html: String): Int {
        NOTIF_BADGE.find(html)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        NOTIF_BULLET.find(html)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        return 0
    }

    fun parseLatestThreadId(html: String): String? =
        THREAD_ID.find(html)?.groupValues?.get(1)

    private fun get(url: String, cookie: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.setRequestProperty("Cookie", cookie)
        conn.setRequestProperty("User-Agent", USER_AGENT)
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        val code = conn.responseCode
        if (code !in 200..299) throw java.io.IOException("HTTP $code for $url")
        return try {
            conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
    }
}
