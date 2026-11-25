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
import kotlinx.coroutines.tasks.await
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
        val data = remoteMessage.data
        val notif = remoteMessage.notification
        val channelType = data["channelType"] ?: data["type"] ?: "chat"
        val targetChatId = data["chatId"]?.takeIf { it.isNotBlank() }
        val openRequests = data["openRequests"] == "true" || channelType == "request"
        val openCalls = data["openCalls"] == "true" || channelType == "call"

        val title = data["title"]?.takeIf { it.isNotBlank() }
            ?: notif?.title?.takeIf { it.isNotBlank() }
            ?: when (channelType) {
                "call" -> "Llamada entrante VYB"
                "sinpe" -> "Cobro SINPE Móvil"
                "request" -> "Nueva solicitud de chat"
                "buzz" -> "Nuevo bip en VYB"
                else -> "Nuevo mensaje en VYB"
            }

        val body = data["body"]?.takeIf { it.isNotBlank() }
            ?: notif?.body?.takeIf { it.isNotBlank() }
            ?: when (channelType) {
                "buzz" -> "Te envió un bip"
                else -> "Tienes nueva actividad en tu cuenta VYB."
            }

        val eventKey = data["eventId"]?.takeIf { it.isNotBlank() }
            ?: "fcm_${remoteMessage.messageId ?: "${channelType}_${targetChatId.orEmpty()}_${body.hashCode()}"}"

        val appContext = applicationContext
        serviceScope.launch {
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
            val token = FirebaseMessaging.getInstance().token.await()
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
