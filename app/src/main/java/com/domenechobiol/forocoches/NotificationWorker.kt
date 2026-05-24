package com.domenechobiol.forocoches

import android.content.Context
import android.webkit.CookieManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class NotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val cookie = CookieManager.getInstance().getCookie("https://forocoches.com")
            ?: return Result.success()
        if (cookie.isBlank()) return Result.success()

        val notifRepo = NotificationRepository(applicationContext)
        val fetcher = NotificationFetcher()

        return try {
            checkMainNotifications(cookie, fetcher, notifRepo)
            checkFavoriteUsers(cookie, fetcher, notifRepo)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private suspend fun checkMainNotifications(
        cookie: String,
        fetcher: NotificationFetcher,
        repo: NotificationRepository
    ) {
        val html = fetcher.fetchMainPage(cookie)

        // Debug: log HTML snippet to verify patterns (remove when patterns confirmed)
        android.util.Log.d("FC_NOTIF", "html_length=${html.length}")
        val pmIdx = html.indexOf("private.php")
        if (pmIdx >= 0) android.util.Log.d("FC_NOTIF", "pm_context: ${html.substring(pmIdx, minOf(html.length, pmIdx + 300))}")

        val pmCount = fetcher.parsePmCount(html)
        val notifCount = fetcher.parseNotifCount(html)

        val lastPm = repo.getLastPmCount()
        if (lastPm >= 0 && pmCount > lastPm) {
            val diff = pmCount - lastPm
            NotificationHelper.show(
                applicationContext,
                NotificationHelper.ID_PM,
                "FC+ Mensajes Privados",
                "Tienes $diff nuevo${if (diff == 1) "" else "s"} mensaje${if (diff == 1) "" else "s"} privado${if (diff == 1) "" else "s"}"
            )
        }
        repo.setLastPmCount(pmCount)

        val lastNotif = repo.getLastNotifCount()
        if (lastNotif >= 0 && notifCount > lastNotif) {
            val diff = notifCount - lastNotif
            NotificationHelper.show(
                applicationContext,
                NotificationHelper.ID_NOTIF,
                "FC+ Notificaciones",
                "Tienes $diff nueva${if (diff == 1) "" else "s"} notificación${if (diff == 1) "" else "es"}"
            )
        }
        repo.setLastNotifCount(notifCount)
    }

    private suspend fun checkFavoriteUsers(
        cookie: String,
        fetcher: NotificationFetcher,
        repo: NotificationRepository
    ) {
        val favorites = repo.getFavoriteUsers()
        favorites.entries.forEachIndexed { index, (username, userId) ->
            if (userId.isBlank()) return@forEachIndexed
            val html = fetcher.fetchUserThreadsPage(cookie, userId)
            val latestThreadId = fetcher.parseLatestThreadId(html) ?: return@forEachIndexed
            val lastSeen = repo.getLastSeenThreadId(username)
            if (lastSeen != null && latestThreadId != lastSeen) {
                NotificationHelper.show(
                    applicationContext,
                    NotificationHelper.ID_FAVORITE_BASE + index,
                    "FC+ Nuevo hilo",
                    "@$username ha subido un hilo nuevo"
                )
            }
            if (lastSeen == null || latestThreadId != lastSeen) {
                repo.setLastSeenThreadId(username, latestThreadId)
            }
        }
    }
}
