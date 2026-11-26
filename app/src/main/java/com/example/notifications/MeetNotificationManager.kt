package com.example.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.automation.AutoJoinPhase
import com.example.automation.AutoJoinSession
import com.example.scheduler.StopAutoJoinReceiver

class MeetNotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_STATUS = "auto_join_status"
        const val CHANNEL_CONNECTED = "meeting_connected"
        const val NOTIFICATION_ID_STATUS = 1001
        const val NOTIFICATION_ID_CONNECTED = 1002
    }

    init {
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val statusChannel = NotificationChannel(
                CHANNEL_STATUS,
                context.getString(R.string.channel_status_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.channel_status_desc)
                setShowBadge(false)
            }

            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val connectedChannel = NotificationChannel(
                CHANNEL_CONNECTED,
                context.getString(R.string.channel_connected_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.channel_connected_desc)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 280, 140, 360)
                setSound(soundUri, audioAttrs)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            manager.createNotificationChannel(statusChannel)
            manager.createNotificationChannel(connectedChannel)
        }
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun buildOngoingStatusNotification(session: AutoJoinSession): Notification {
        val openAppIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getBroadcast(
            context,
            1,
            Intent(context, StopAutoJoinReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusLabel = when (session.phase) {
            AutoJoinPhase.LAUNCHING_MEET -> "Opening Google Meet..."
            AutoJoinPhase.LOOKING_FOR_JOIN_BUTTON -> "Preparing to tap Join..."
            AutoJoinPhase.WAITING_FOR_ADMISSION -> "Waiting for host to let you in..."
            AutoJoinPhase.RETRYING -> "Retrying auto-join (Attempt #${session.attemptCount})"
            AutoJoinPhase.CONNECTED -> "Connected to meeting"
            AutoJoinPhase.STOPPED, AutoJoinPhase.IDLE -> "Auto-join idle"
        }

        val detailText = buildString {
            append(statusLabel)
            if (session.lastActionMessage.isNotBlank()) {
                append(" • ")
                append(session.lastActionMessage)
            }
        }

        return NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(android.R.drawable.ic_menu_today)
            .setContentTitle("${session.title} (${session.meetingCode})")
            .setContentText(statusLabel)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detailText))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                context.getString(R.string.action_stop_auto_join),
                stopIntent
            )
            .build()
    }

    fun updateOngoingStatus(session: AutoJoinSession) {
        if (!hasNotificationPermission()) return
        try {
            NotificationManagerCompat.from(context).notify(
                NOTIFICATION_ID_STATUS,
                buildOngoingStatusNotification(session)
            )
        } catch (_: SecurityException) {
            // Permission revoked concurrently
        }
    }

    fun cancelOngoingStatus() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_STATUS)
    }

    fun notifyConnectionEstablished(
        title: String,
        meetingCode: String,
        playSound: Boolean,
        vibrate: Boolean,
        isTest: Boolean = false
    ) {
        if (!hasNotificationPermission()) return

        val openAppIntent = PendingIntent.getActivity(
            context,
            2,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notifTitle = if (isTest) {
            "Test Alert: Connected to $title"
        } else {
            "Connected to $title!"
        }
        val notifBody = if (isTest) {
            "This is how MeetPilot alerts you when someone accepts you into $meetingCode."
        } else {
            "You were admitted into Google Meet ($meetingCode). Auto-join has completed."
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_CONNECTED)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle(notifTitle)
            .setContentText(notifBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notifBody))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent)

        if (!playSound) {
            builder.setSilent(true)
        }

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_CONNECTED, builder.build())
        } catch (_: SecurityException) {
            return
        }

        if (vibrate) {
            triggerHapticPulse()
        }
    }

    private fun triggerHapticPulse() {
        val pattern = longArrayOf(0, 260, 120, 340)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, -1)
            }
        }
    }
}
