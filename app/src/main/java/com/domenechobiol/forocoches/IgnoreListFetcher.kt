package com.domenechobiol.forocoches

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class IgnoreListFetcher {

    companion object {
        private const val IGNORE_LIST_URL = "https://forocoches.com/foro/profile.php?do=ignorelist"
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
    }

    suspend fun fetch(cookieHeader: String): List<String> = withContext(Dispatchers.IO) {
        val conn = URL(IGNORE_LIST_URL).openConnection() as HttpURLConnection
        conn.setRequestProperty("Cookie", cookieHeader)
        conn.setRequestProperty("User-Agent", USER_AGENT)
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        val html = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        parseIgnoreList(html)
    }

    fun parseIgnoreList(html: String): List<String> {
        val usernames = mutableListOf<String>()
        val divRegex = Regex("""<div[^>]+id="user\d+"[^>]*>([\s\S]*?)</div>""", RegexOption.IGNORE_CASE)
        val aRegex = Regex("""href="member\.php\?u=\d+"[^>]*>([^<]+)</a>""", RegexOption.IGNORE_CASE)
        for (divMatch in divRegex.findAll(html)) {
            val aMatch = aRegex.find(divMatch.value)
            if (aMatch != null) usernames.add(aMatch.groupValues[1].trim())
        }
        return usernames
    }
}
