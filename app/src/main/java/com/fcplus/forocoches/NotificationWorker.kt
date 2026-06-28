package com.fcplus.forocoches

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
        if (cookie.isNullOrBlank()) return Result.success()

        return try {
            NotificationChecker.check(applicationContext, cookie)
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("FC_NOTIF", "worker exception: ${e.message}", e)
            Result.retry()
        }
    }
}
