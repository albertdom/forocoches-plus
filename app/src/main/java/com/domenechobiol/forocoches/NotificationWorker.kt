package com.domenechobiol.forocoches

import android.content.Context
import android.webkit.CookieManager
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class NotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val cookie = CookieManager.getInstance().getCookie("https://forocoches.com")
        android.util.Log.d("FC_NOTIF", "cookie=${if (cookie == null) "NULL" else if (cookie.isBlank()) "BLANK" else "OK(${cookie.length})"}")
        if (cookie == null || cookie.isBlank()) return Result.success()

        val notifRepo = NotificationRepository(applicationContext)
        val fetcher = NotificationFetcher()

        return try {
            checkMainNotifications(cookie, fetcher, notifRepo)
            checkFavoriteUsers(cookie, fetcher, notifRepo)
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("FC_NOTIF", "worker exception: ${e.message}", e)
            Result.retry()
        }
    }

    private suspend fun checkMainNotifications(
        cookie: String,
        fetcher: NotificationFetcher,
        repo: NotificationRepository
    ) {
        val html = fetcher.fetchMainPage(cookie)

        val pmCount = fetcher.parsePmCount(html)
        val notifCount = fetcher.parseNotifCount(html)
        val lastPm = repo.getLastPmCount()
        val lastNotif = repo.getLastNotifCount()
        android.util.Log.d("FC_NOTIF", "pmCount=$pmCount lastPm=$lastPm notifCount=$notifCount lastNotif=$lastNotif")

        val spanMatches = Regex("""<span class="user-notifications">(\d+)</span>""", RegexOption.IGNORE_CASE).findAll(html).map { it.groupValues[1] }.toList()
        android.util.Log.d("FC_NOTIF", "user-notifications spans found: $spanMatches")

        val pmIdx = html.indexOf("private.php")
        if (pmIdx >= 0) android.util.Log.d("FC_NOTIF", "pm_html: ${html.substring(pmIdx, minOf(html.length, pmIdx + 600))}")

        android.util.Log.d("FC_NOTIF", "notificationsEnabled=${NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()}")

        if (lastPm >= 0 && pmCount > lastPm) {
            val diff = pmCount - lastPm
            NotificationHelper.show(
                applicationContext,
                NotificationHelper.ID_PM,
                "FC+ Mensajes Privados",
                "Tienes $diff nuevo${if (diff == 1) "" else "s"} mensaje${if (diff == 1) "" else "s"} privado${if (diff == 1) "" else "s"}",
                pmCount,
                "https://forocoches.com/foro/private.php"
            )
        }
        repo.setLastPmCount(pmCount)

        if (lastNotif >= 0 && notifCount > lastNotif) {
            val diff = notifCount - lastNotif
            NotificationHelper.show(
                applicationContext,
                NotificationHelper.ID_NOTIF,
                "FC+ Notificaciones",
                "Tienes $diff nueva${if (diff == 1) "" else "s"} notificación${if (diff == 1) "" else "es"}",
                notifCount
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
                    "@$username ha subido un hilo nuevo",
                    1
                )
            }
            if (lastSeen == null || latestThreadId != lastSeen) {
                repo.setLastSeenThreadId(username, latestThreadId)
            }
        }
    }
}
