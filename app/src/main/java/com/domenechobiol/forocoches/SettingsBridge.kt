package com.domenechobiol.forocoches

import android.webkit.JavascriptInterface

class SettingsBridge(private val repo: IgnoreListRepository) {

    @JavascriptInterface
    fun getHideMode(): String = repo.getHideMode()

    @JavascriptInterface
    fun setHideMode(mode: String) {
        if (mode == "complete" || mode == "message") repo.setHideMode(mode)
    }

    @JavascriptInterface
    fun getIgnoredUsersJson(): String {
        val users = repo.getIgnoredUsers()
        if (users.isEmpty()) return "[]"
        return "[" + users.joinToString(",") {
            "\"${it.replace("\\", "\\\\").replace("\"", "\\\"")}\""
        } + "]"
    }

    @JavascriptInterface
    fun removeIgnoredUser(username: String) {
        val users = repo.getIgnoredUsers().toMutableList()
        users.remove(username)
        repo.setIgnoredUsers(users)
    }

    @JavascriptInterface
    fun getLastUpdatedMs(): Long = repo.getLastUpdated()
}
