package com.domenechobiol.forocoches

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class ForocochesApp : Application() {
    override fun onCreate() {
        super.onCreate()

        NotificationHelper.createChannel(this)

        try {
            val ignoreRequest = PeriodicWorkRequestBuilder<IgnoreListWorker>(30, TimeUnit.MINUTES).build()
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "ignore-list-refresh",
                ExistingPeriodicWorkPolicy.KEEP,
                ignoreRequest
            )

            val notifRequest = PeriodicWorkRequestBuilder<NotificationWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "notification-poll",
                ExistingPeriodicWorkPolicy.KEEP,
                notifRequest
            )
        } catch (_: IllegalStateException) {
            // WorkManager not initialized (e.g., in tests)
        }
    }
}
