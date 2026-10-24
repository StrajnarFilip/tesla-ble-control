package io.github.teslablecontrol.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import io.github.teslablecontrol.MainActivity
import io.github.teslablecontrol.R

object Notifications {
    const val CHANNEL_CONNECTION = "connection"
    const val LINK_NOTIFICATION_ID = 1

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_CONNECTION,
            context.getString(R.string.notification_channel_connection),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notification_channel_connection_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * The background connection's ongoing notification. It is private on the lock screen, and Unlock
     * is only offered where Android can require the phone to be unlocked first (Android 12+).
     */
    fun linkNotification(context: Context, title: String, text: String, showLockActions: Boolean): Notification {
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = Notification.Builder(context, CHANNEL_CONNECTION)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(
                Notification.Builder(context, CHANNEL_CONNECTION)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(context.getString(R.string.app_name))
                    .setContentText(context.getString(R.string.notification_public_text))
                    .build(),
            )
        if (showLockActions) {
            builder.addAction(action(context, VehicleLinkService.ACTION_LOCK, R.string.action_lock, authenticationRequired = false))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.addAction(action(context, VehicleLinkService.ACTION_UNLOCK, R.string.action_unlock, authenticationRequired = true))
            }
        }
        builder.addAction(action(context, VehicleLinkService.ACTION_STOP, R.string.action_stop_connection, authenticationRequired = false))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        return builder.build()
    }

    private fun action(context: Context, action: String, label: Int, authenticationRequired: Boolean): Notification.Action {
        val intent = PendingIntent.getForegroundService(
            context, action.hashCode(), Intent(context, VehicleLinkService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = Notification.Action.Builder(null, context.getString(label), intent)
        if (authenticationRequired && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) builder.setAuthenticationRequired(true)
        return builder.build()
    }
}
