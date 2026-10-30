package com.meshmobile

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Keeps FreeCall Offline running in the background, so nearby phones can
 * reach you even when the app is not open on screen.
 */
class MeshService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIF_ID, serviceNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            } else {
                startForeground(NOTIF_ID, serviceNotification())
            }
        } catch (e: Exception) {
            // A permission is still missing. The app starts the service again once permissions are granted.
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nearby = MeshHub.restore(this)
        if (nearby != null) {
            nearby.onIncoming = { title, text -> notifyIncoming(title, text) }
            nearby.start()
        } else {
            stopSelf()
        }
        return START_STICKY
    }

    private fun serviceNotification() = NotificationCompat.Builder(this, CH_SERVICE)
        .setSmallIcon(android.R.drawable.ic_menu_call)
        .setContentTitle("FreeCall Offline")
        .setContentText("Ready for nearby calls and messages")
        .setOngoing(true)
        .setSilent(true)
        .build()

    private fun notifyIncoming(title: String, text: String) {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(this, CH_MSG)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try {
            NotificationManagerCompat.from(this).notify((System.currentTimeMillis() % 100000).toInt(), n)
        } catch (e: SecurityException) {
            // Notification permission not granted; nothing to show.
        }
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_SERVICE, "Background connection", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_MSG, "Messages and calls", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    companion object {
        private const val NOTIF_ID = 1001
        private const val CH_SERVICE = "freecall_service"
        private const val CH_MSG = "freecall_messages"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, MeshService::class.java))
        }
    }
}
