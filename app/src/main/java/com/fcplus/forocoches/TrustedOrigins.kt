package com.fcplus.forocoches

import java.net.URI
import java.util.Locale

object TrustedOrigins {
    const val DEFAULT_URL = "https://forocoches.com/foro/"

    private val trustedHosts = setOf("forocoches.com", "www.forocoches.com")

    fun trustedUrlOrDefault(rawUrl: String?): String {
        val trimmed = rawUrl?.trim().orEmpty()
        return if (isTrustedForocochesUrl(trimmed)) trimmed else DEFAULT_URL
    }

    fun isTrustedForocochesUrl(rawUrl: String?): Boolean {
        val uri = parse(rawUrl) ?: return false
        val scheme = uri.scheme?.lowercase(Locale.US) ?: return false
        val host = uri.host?.lowercase(Locale.US) ?: return false
        return scheme == "https" && host in trustedHosts
    }

    fun isHttpOrHttps(rawUrl: String?): Boolean {
        val scheme = parse(rawUrl)?.scheme?.lowercase(Locale.US) ?: return false
        return scheme == "http" || scheme == "https"
    }

    private fun parse(rawUrl: String?): URI? {
        val trimmed = rawUrl?.trim()
        if (trimmed.isNullOrEmpty()) return null
        return try {
            URI(trimmed)
        } catch (_: Exception) {
            null
        }
    }
}
