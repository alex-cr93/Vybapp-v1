package com.example.core

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.repository.ChatRepository
import com.example.data.repository.UserRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

private const val TAG = "VybMessagingService"

class VybMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        if (token.isBlank()) return
        val appContext = applicationContext
        serviceScope.launch {
            val prefs = PreferencesStore(appContext)
            prefs.setFcmToken(token)
            if (Firebase.auth.currentUser != null) {
                runCatching {
                    UserRepository(appContext).saveFcmToken(token)
                }.onFailure { err ->
                    Log.w(TAG, "Deferred FCM token sync: ${err.localizedMessage}")
                }
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        val appContext = applicationContext
        NotificationHelper.ensureChannels(appContext)

        val data = remoteMessage.data
        val notif = remoteMessage.notification
        val rawType = (data["channelType"] ?: data["type"] ?: "chat").trim().lowercase()
        val channelType = when (rawType) {
            "call", "sinpe", "request", "buzz" -> rawType
            else -> "chat"
        }
        val targetChatId = data["chatId"]?.takeIf { it.isNotBlank() }
        val messageId = data["messageId"]?.takeIf { it.isNotBlank() }
        val openRequests = data["openRequests"] == "true" || channelType == "request"
        val openCalls = data["openCalls"] == "true" || channelType == "call"

        val title = data["title"]?.takeIf { it.isNotBlank() }
            ?: notif?.title?.takeIf { it.isNotBlank() }
            ?: data["senderName"]?.takeIf { it.isNotBlank() }
            ?: when (channelType) {
                "call" -> "Llamada entrante VYB"
                "sinpe" -> "Cobro SINPE Móvil"
                "request" -> "Nueva solicitud de chat"
                "buzz" -> "Nuevo bip en VYB"
                else -> "Nuevo mensaje en VYB"
            }

        val body = data["body"]?.takeIf { it.isNotBlank() }
            ?: notif?.body?.takeIf { it.isNotBlank() }
            ?: data["text"]?.takeIf { it.isNotBlank() }
            ?: when (channelType) {
                "buzz" -> "Te envió un bip"
                else -> "Tienes un mensaje nuevo en VYB."
            }

        val senderId = data["senderId"]?.takeIf { it.isNotBlank() }
        val currentUid = Firebase.auth.currentUser?.uid
        if (senderId != null && senderId == currentUid) {
            return
        }

        val eventKey = data["eventId"]?.takeIf { it.isNotBlank() }
            ?: messageId?.let { "msg_${targetChatId.orEmpty()}_$it" }
            ?: "fcm_${remoteMessage.messageId ?: "${channelType}_${targetChatId.orEmpty()}_${remoteMessage.sentTime}_${body.hashCode()}"}"

        val isViewingTargetChatInForeground =
            ActiveChatTracker.isAppForeground &&
                targetChatId != null &&
                ActiveChatTracker.activeChatId == targetChatId

        runBlocking(Dispatchers.IO) {
            withTimeoutOrNull(8_000L) {
                if (!isViewingTargetChatInForeground) {
                    val shouldNotify = PreferencesStore(appContext).markEventNotifiedIfNew(eventKey)
                    if (shouldNotify) {
                        NotificationHelper.showNotification(
                            context = appContext,
                            channelType = channelType,
                            title = title,
                            body = body,
                            notificationId = eventKey.hashCode(),
                            targetChatId = targetChatId,
                            openRequestsTab = openRequests,
                            openCallsTab = openCalls
                        )
                    }
                }
                if (targetChatId != null &&
                    channelType in setOf("chat", "sinpe", "buzz") &&
                    currentUid != null
                ) {
                    runCatching {
                        ChatRepository(appContext).acknowledgeChatDelivered(
                            chatId = targetChatId,
                            messageIds = listOfNotNull(messageId)
                        )
                    }.onFailure { err ->
                        Log.w(TAG, "Push delivery ACK skipped: ${err.localizedMessage}")
                    }
                }
            }
        }
    }
}

object PushNotificationRegistrar {
    private const val PERIODIC_WORK_NAME = "vyb_bg_push_sync"
    private const val EXPEDITED_WORK_NAME = "vyb_bg_push_immediate"

    suspend fun syncFcmToken(
        context: Context,
        userRepository: UserRepository,
        preferencesStore: PreferencesStore
    ): String? {
        return runCatching {
            val messaging = FirebaseMessaging.getInstance()
            messaging.isAutoInitEnabled = true
            val token = messaging.token.await()
            if (token.isNotBlank()) {
                preferencesStore.setFcmToken(token)
                userRepository.saveFcmToken(token)
                token
            } else {
                null
            }
        }.onFailure { err ->
            Log.w(TAG, "FCM token unavailable yet (enable FCM in Firebase Console if needed): ${err.localizedMessage}")
        }.getOrNull()
    }

    fun scheduleBackgroundSync(context: Context) {
        runCatching {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<VybBackgroundNotificationWorker>(
                15,
                TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(
                    PERIODIC_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
        }
    }

    fun triggerExpeditedSync(context: Context) {
        runCatching {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<VybBackgroundNotificationWorker>()
                .setConstraints(constraints)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(
                    EXPEDITED_WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    request
                )
        }
    }
}
