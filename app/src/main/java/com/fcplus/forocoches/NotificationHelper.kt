package com.fcplus.forocoches

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat

object NotificationHelper {

    private const val CHANNEL_ID = "fc_notifications"
    private const val CHANNEL_NAME = "FC+ Notificaciones"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT)
            channel.description = "Notificaciones de Forocoches Plus"
            context.getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }

    fun show(context: Context, id: Int, title: String, text: String, badgeCount: Int = 0, url: String = "") {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (url.isNotEmpty()) putExtra("url", url)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setNumber(badgeCount)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val nm = NotificationManagerCompat.from(context)
        notifyIfAllowed(context, nm, id, notification)
    }

    /**
     * Notificación de MP estilo chat (WhatsApp): remitente como "persona" con avatar
     * (círculo con su inicial), el mensaje como burbuja. Usa [NotificationCompat.MessagingStyle].
     */
    fun showPm(context: Context, sender: String, message: String, url: String, badgeCount: Int) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (url.isNotEmpty()) putExtra("url", url)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, ID_PM, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val them = Person.Builder()
            .setName(sender.ifBlank { "Forocoches" })
            .setIcon(IconCompat.createWithBitmap(initialAvatar(sender)))
            .build()
        val me = Person.Builder().setName("Tú").build()
        val style = NotificationCompat.MessagingStyle(me)
            // En 1:1, Android a veces NO muestra el nombre del remitente (lo da por
            // implícito). Ponerlo como título de la conversación garantiza que se vea.
            .setConversationTitle(sender.ifBlank { "Mensaje privado" })
            .addMessage(message.ifBlank { "(mensaje)" }, System.currentTimeMillis(), them)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setNumber(badgeCount)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val nm = NotificationManagerCompat.from(context)
        notifyIfAllowed(context, nm, ID_PM, notification)
    }

    @SuppressLint("MissingPermission")
    private fun notifyIfAllowed(
        context: Context,
        nm: NotificationManagerCompat,
        id: Int,
        notification: Notification
    ) {
        if (!canPostNotifications(context) || !nm.areNotificationsEnabled()) return
        try {
            nm.notify(id, notification)
        } catch (_: SecurityException) {
        }
    }

    private fun canPostNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** Avatar circular con la inicial del remitente (FC no da la URL del avatar en la bandeja). */
    private fun initialAvatar(name: String): Bitmap {
        val size = 128
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.parseColor("#00E5CC")
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.color = Color.BLACK
        paint.textSize = size * 0.5f
        paint.textAlign = Paint.Align.CENTER
        val initial = name.trim().firstOrNull()?.uppercase() ?: "?"
        val fm = paint.fontMetrics
        canvas.drawText(initial, size / 2f, size / 2f - (fm.ascent + fm.descent) / 2f, paint)
        return bmp
    }

    const val ID_PM = 1001
    const val ID_NOTIF = 1002
}
