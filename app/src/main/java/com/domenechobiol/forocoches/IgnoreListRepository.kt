package com.domenechobiol.forocoches

import android.content.Context

class IgnoreListRepository(context: Context) {

    private val prefs = context.getSharedPreferences("fc_filtro", Context.MODE_PRIVATE)

    fun getIgnoredUsers(): List<String> =
        prefs.getStringSet("ignored_users", emptySet())?.toList() ?: emptyList()

    fun setIgnoredUsers(users: List<String>) {
        prefs.edit()
            .putStringSet("ignored_users", users.toSet())
            .putLong("last_updated", System.currentTimeMillis())
            .apply()
    }

    fun getLastUpdated(): Long = prefs.getLong("last_updated", 0L)
}
