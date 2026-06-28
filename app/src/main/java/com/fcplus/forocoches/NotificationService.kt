package com.fcplus.forocoches

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.webkit.CookieManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Servicio en primer plano para notificaciones "casi instantáneas".
 *
 * Sondea cada N segundos (configurable) mientras corre, incluso con la app cerrada
 * (no sobrevive a "forzar detención"). Muestra una notificación permanente de baja
 * prioridad porque Android lo exige. Es OPCIONAL: solo corre si el usuario activa el
 * toggle. Sin él, sigue el WorkManager de 15 min.
 */
class NotificationService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var job: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(SERVICE_NOTIF_ID, buildOngoing())
        if (job?.isActive != true) {
            job = scope.launch { loop() }
        }
        return START_STICKY
    }

    private suspend fun loop() {
        val repo = NotificationRepository(applicationContext)
        while (scope.isActive) {
            val cookie = CookieManager.getInstance().getCookie("https://forocoches.com")
            if (!cookie.isNullOrBlank()) {
                try {
                    NotificationChecker.check(applicationContext, cookie)
                } catch (e: Exception) {
                    android.util.Log.w("FC_NOTIF", "service check error: ${e.message}")
                }
            }
            delay(repo.getIntervalSec().coerceAtLeast(15) * 1000L)
        }
    }

    private fun buildOngoing(): Notification {
        ensureChannel()
        val tap = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, SERVICE_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("Forocoches+")
            .setContentText("Comprobando mensajes")
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(tap)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                SERVICE_CHANNEL, "Servicio en segundo plano",
                NotificationManager.IMPORTANCE_MIN
            )
            ch.description = "Mantiene FC+ comprobando mensajes con la app cerrada"
            ch.setShowBadge(false)
            ch.setSound(null, null)
            ch.enableVibration(false)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
        }
    }

    override fun onDestroy() {
        job?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val SERVICE_CHANNEL = "fc_service2"
        const val SERVICE_NOTIF_ID = 2001

        fun start(context: Context) {
            val intent = Intent(context, NotificationService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                android.util.Log.w("FC_NOTIF", "no se pudo arrancar el servicio: ${e.message}")
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, NotificationService::class.java))
        }
    }
}
