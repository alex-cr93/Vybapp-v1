package com.example.core

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
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

data class NotificationNavTarget(
    val targetChatId: String? = null,
    val openRequestsTab: Boolean = false,
    val openCallsTab: Boolean = false
)

object ActiveChatTracker {
    @Volatile
    var isAppForeground: Boolean = false

    @Volatile
    var activeChatId: String? = null
}

object NotificationHelper {
    const val CHANNEL_CHAT = "vyb_chat_channel"
    const val CHANNEL_BUZZ = "vyb_buzz_channel"
    const val CHANNEL_CALL = "vyb_call_channel"
    const val CHANNEL_SINPE = "vyb_sinpe_channel"
    const val CHANNEL_REQUEST = "vyb_request_channel"

    const val EXTRA_TARGET_CHAT_ID = "vyb_extra_target_chat_id"
    const val EXTRA_OPEN_REQUESTS = "vyb_extra_open_requests"
    const val EXTRA_OPEN_CALLS = "vyb_extra_open_calls"
    const val EXTRA_NOTIFICATION_ID = "vyb_extra_notification_id"
    const val EXTRA_CHAT_TITLE = "vyb_extra_chat_title"
    const val EXTRA_ORIGINAL_BODY = "vyb_extra_original_body"
    const val ACTION_DIRECT_REPLY = "com.example.ACTION_NOTIFICATION_REPLY"
    const val KEY_TEXT_REPLY = "vyb_key_text_reply"

    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun openBatteryOptimizationSettings(context: Context) {
        val batteryIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val fallbackIntent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            context.startActivity(batteryIntent)
        }.onFailure {
            runCatching { context.startActivity(fallbackIntent) }
        }
    }

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val defaultSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val msgAudioAttrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            nm.createNotificationChannels(
                listOf(
                    NotificationChannel(
                        CHANNEL_CHAT,
                        "Mensajes de chat",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Notificaciones push de mensajes directos y grupales"
                        enableLights(true)
                        enableVibration(true)
                        vibrationPattern = longArrayOf(0L, 180L, 90L, 180L)
                        setShowBadge(true)
                        setSound(defaultSound, msgAudioAttrs)
                        lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                    },
                    NotificationChannel(
                        CHANNEL_BUZZ,
                        "Alertas Bip",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Alertas instantáneas tipo Bip en VYB"
                        enableLights(true)
                        enableVibration(true)
                        vibrationPattern = longArrayOf(0L, 260L, 80L, 260L, 80L, 320L)
                        setShowBadge(true)
                        setSound(defaultSound, msgAudioAttrs)
                        lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                    },
                    NotificationChannel(
                        CHANNEL_CALL,
                        "Llamadas entrantes",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Alertas de llamadas de voz y videollamadas VYB"
                        enableLights(true)
                        enableVibration(true)
                        vibrationPattern = longArrayOf(0L, 500L, 300L, 500L)
                        setShowBadge(true)
                        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    },
                    NotificationChannel(
                        CHANNEL_SINPE,
                        "Alertas SINPE Móvil",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Cobros y confirmaciones SINPE en tus chats"
                        enableLights(true)
                        enableVibration(true)
                        vibrationPattern = longArrayOf(0L, 220L, 100L, 220L)
                        setShowBadge(true)
                        setSound(defaultSound, msgAudioAttrs)
                        lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                    },
                    NotificationChannel(
                        CHANNEL_REQUEST,
                        "Solicitudes de chat",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Nuevas solicitudes de conexión en VYB"
                        enableLights(true)
                        enableVibration(true)
                        vibrationPattern = longArrayOf(0L, 180L, 90L, 180L)
                        setShowBadge(true)
                        setSound(defaultSound, msgAudioAttrs)
                        lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                    }
                )
            )
        }
    }

    fun showNotification(
        context: Context,
        channelType: String,
        title: String,
        body: String,
        notificationId: Int,
        targetChatId: String? = null,
        openRequestsTab: Boolean = false,
        openCallsTab: Boolean = false
    ) {
        if (!hasNotificationPermission(context)) return

        ensureChannels(context)
        val channelId = when (channelType) {
            "call" -> CHANNEL_CALL
            "sinpe" -> CHANNEL_SINPE
            "request" -> CHANNEL_REQUEST
            "buzz" -> CHANNEL_BUZZ
            else -> CHANNEL_CHAT
        }
        val category = when (channelType) {
            "call" -> NotificationCompat.CATEGORY_CALL
            else -> NotificationCompat.CATEGORY_MESSAGE
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (!targetChatId.isNullOrBlank()) {
                putExtra(EXTRA_TARGET_CHAT_ID, targetChatId)
            }
            if (openRequestsTab) {
                putExtra(EXTRA_OPEN_REQUESTS, true)
            }
            if (openCallsTab) {
                putExtra(EXTRA_OPEN_CALLS, true)
            }
        }
        val requestCode = "${channelType}_${targetChatId.orEmpty()}_$notificationId".hashCode()
        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val defaultSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_vyb_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setCategory(category)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setSound(defaultSound)
            .setVibrate(
                if (channelType == "buzz") longArrayOf(0L, 260L, 80L, 260L, 80L, 320L)
                else longArrayOf(0L, 180L, 90L, 180L)
            )
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setOnlyAlertOnce(false)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val supportsQuickReply = !targetChatId.isNullOrBlank() &&
            (channelType == "chat" || channelType == "sinpe" || channelType == "buzz")

        if (supportsQuickReply) {
            val chatId = targetChatId!!
            val mePerson = Person.Builder().setName("Tú").build()
            val peerPerson = Person.Builder().setName(title.ifBlank { "Contacto VYB" }).build()
            val messagingStyle = NotificationCompat.MessagingStyle(mePerson)
                .setConversationTitle(title)
                .addMessage(body, System.currentTimeMillis(), peerPerson)
            builder.setStyle(messagingStyle)

            val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
                .setLabel("Responder...")
                .build()

            val replyIntent = Intent(context, NotificationReplyReceiver::class.java).apply {
                action = ACTION_DIRECT_REPLY
                putExtra(EXTRA_TARGET_CHAT_ID, chatId)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                putExtra(EXTRA_CHAT_TITLE, title)
                putExtra(EXTRA_ORIGINAL_BODY, body)
            }
            val replyFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val replyPendingIntent = PendingIntent.getBroadcast(
                context,
                ("reply_${chatId}_$notificationId").hashCode(),
                replyIntent,
                replyFlags
            )

            val replyAction = NotificationCompat.Action.Builder(
                R.drawable.ic_vyb_notification,
                "Responder",
                replyPendingIntent
            )
                .addRemoteInput(remoteInput)
                .setAllowGeneratedReplies(true)
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
                .build()

            builder.addAction(replyAction)
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(body))
        }

        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        }
    }

    fun showRepliedConfirmation(
        context: Context,
        notificationId: Int,
        chatId: String,
        chatTitle: String,
        originalBody: String?,
        replyText: String,
        failedReason: String? = null
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TARGET_CHAT_ID, chatId)
        }
        val contentPi = PendingIntent.getActivity(
            context,
            ("open_${chatId}_$notificationId").hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val mePerson = Person.Builder().setName("Tú").build()
        val peerPerson = Person.Builder().setName(chatTitle.ifBlank { "Contacto VYB" }).build()
        val now = System.currentTimeMillis()
        val style = NotificationCompat.MessagingStyle(mePerson)
            .setConversationTitle(chatTitle)
        if (!originalBody.isNullOrBlank()) {
            style.addMessage(originalBody, now - 1000L, peerPerson)
        }
        if (failedReason == null) {
            style.addMessage(replyText, now, mePerson)
        } else {
            style.addMessage(failedReason, now, mePerson)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_CHAT)
            .setSmallIcon(R.drawable.ic_vyb_notification)
            .setContentTitle(chatTitle)
            .setContentText(failedReason ?: "Tú: $replyText")
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(contentPi)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }
    }

    fun extractNavTarget(intent: Intent?): NotificationNavTarget? {
        if (intent == null) return null
        val chatId = intent.getStringExtra(EXTRA_TARGET_CHAT_ID)?.takeIf { it.isNotBlank() }
        val openRequests = intent.getBooleanExtra(EXTRA_OPEN_REQUESTS, false)
        val openCalls = intent.getBooleanExtra(EXTRA_OPEN_CALLS, false)
        if (chatId == null && !openRequests && !openCalls) return null
        return NotificationNavTarget(
            targetChatId = chatId,
            openRequestsTab = openRequests,
            openCallsTab = openCalls
        )
    }
}
