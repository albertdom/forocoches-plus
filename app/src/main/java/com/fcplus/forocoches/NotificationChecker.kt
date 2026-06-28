package com.fcplus.forocoches

import android.content.Context
import androidx.core.app.NotificationManagerCompat

/**
 * Lógica única de chequeo de notificaciones, compartida por el worker de fondo
 * ([NotificationWorker]) y el sondeo en primer plano ([MainActivity]).
 *
 * Compara los contadores actuales con los últimos guardados y, si suben, lanza la push:
 *  - MP: con remitente + preview del mensaje (estilo WhatsApp).
 *  - Notificaciones (citas + menciones): contador combinado.
 */
object NotificationChecker {

    suspend fun check(context: Context, cookie: String) {
        val fetcher = NotificationFetcher()
        val repo = NotificationRepository(context)
        val counts = fetcher.parseCounts(fetcher.fetchMainPage(cookie))

        // Mantiene el badge del icono en sync: si ya no hay nada sin leer (leído desde
        // el navegador, p.ej.), cancela la notificación para que el numerito desaparezca.
        val nm = NotificationManagerCompat.from(context)
        if (counts.pm == 0) nm.cancel(NotificationHelper.ID_PM)
        if (counts.notifTotal == 0) nm.cancel(NotificationHelper.ID_NOTIF)

        val lastPm = repo.getLastPmCount()
        if (lastPm in 0 until counts.pm) {
            val preview = runCatching { fetcher.fetchLatestPm(cookie) }.getOrNull()
            if (preview != null) {
                // Estilo chat (WhatsApp): remitente + mensaje.
                NotificationHelper.showPm(
                    context,
                    sender = preview.sender,
                    message = (preview.snippet.ifBlank { preview.subject }).take(200),
                    url = preview.url,
                    badgeCount = counts.pm
                )
            } else {
                val diff = counts.pm - lastPm
                NotificationHelper.show(
                    context, NotificationHelper.ID_PM, "FC+ Mensajes Privados",
                    "Tienes $diff mensaje${if (diff == 1) "" else "s"} privado${if (diff == 1) "" else "s"} nuevo${if (diff == 1) "" else "s"}",
                    counts.pm, "https://forocoches.com/foro/private.php"
                )
            }
        }
        repo.setLastPmCount(counts.pm)

        val lastNotif = repo.getLastNotifCount()
        if (lastNotif in 0 until counts.notifTotal) {
            val diff = counts.notifTotal - lastNotif
            NotificationHelper.show(
                context,
                NotificationHelper.ID_NOTIF,
                "FC+ Notificaciones",
                "Tienes $diff nueva${if (diff == 1) "" else "s"} notificación${if (diff == 1) "" else "es"} (citas/menciones)",
                counts.notifTotal
            )
        }
        repo.setLastNotifCount(counts.notifTotal)
    }
}
