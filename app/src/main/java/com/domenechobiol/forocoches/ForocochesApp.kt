package com.domenechobiol.forocoches

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class ForocochesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            val request = PeriodicWorkRequestBuilder<IgnoreListWorker>(30, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "ignore-list-refresh",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        } catch (_: IllegalStateException) {
            // WorkManager not initialized (e.g., in tests)
        }
    }
}
