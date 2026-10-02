package com.llgl.app.pet

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.llgl.app.MainActivity
import com.llgl.app.R

/** The persistent notification that keeps the overlay service alive, with pause/stop actions. */
object PetNotification {
    const val CHANNEL_ID = "pet"
    const val ID = 1

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    fun build(context: Context, petName: String, paused: Boolean): Notification {
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), flags)
        val toggleAction = if (paused) PetService.ACTION_RESUME else PetService.ACTION_PAUSE
        val toggle = PendingIntent.getService(context, 1, PetService.intent(context, toggleAction), flags)
        val stop = PendingIntent.getService(context, 2, PetService.intent(context, PetService.ACTION_STOP), flags)
        val title = context.getString(if (paused) R.string.pet_stopped else R.string.pet_running, petName)
        val toggleLabel = context.getString(if (paused) R.string.notification_resume else R.string.notification_pause)

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_pet)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.notification_text))
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, toggleLabel, toggle)
            .addAction(0, context.getString(R.string.notification_stop), stop)
            .build()
    }
}
