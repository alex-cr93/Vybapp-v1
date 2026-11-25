package com.example.core

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.R
import com.example.data.model.CallSession
import com.example.data.model.Chat
import com.example.data.model.ChatRequest
import com.example.data.model.PrivateUser
import com.example.ui.viewmodel.ChatViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

class VybBackgroundNotificationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!NotificationHelper.hasNotificationPermission(applicationContext)) {
            return Result.success()
        }
        val uid = Firebase.auth.currentUser?.uid ?: return Result.success()
        val prefs = PreferencesStore(applicationContext)

        return try {
            val dbId = applicationContext.getString(R.string.firestore_database_id)
            val db = FirebaseFirestore.getInstance(dbId)

            val privateSnap = db.collection("users_private").document(uid).get().await()
            val privateUser = privateSnap.toObject(
                PrivateUser::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            val soundEnabled = privateUser?.soundEnabled != false
            val callAlertsEnabled = privateUser?.callAlertsEnabled != false
            val sinpeAlertsEnabled = privateUser?.sinpeAlertsEnabled != false

            // 1. Check unread messages & SINPE cards
            val chatSnaps = db.collection("chats")
                .whereArrayContains("participantUids", uid)
                .limit(25)
                .get()
                .await()

            val chatRepo = com.example.data.repository.ChatRepository(applicationContext)
            chatSnaps.documents.forEach { doc ->
                val chat = doc.toObject(Chat::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: return@forEach
                if (uid in chat.hiddenForUids) return@forEach
                val unread = chat.unreadCounts[uid] ?: 0L
                val tsMillis = chat.lastMessageAt?.let { it.seconds * 1000L + it.nanoseconds / 1_000_000L } ?: 0L
                if (unread > 0L && chat.lastMessageSenderId != uid && tsMillis > 0L) {
                    runCatching { chatRepo.acknowledgeChatDelivered(chat.id) }
                    val isSinpe = chat.lastMessageText.contains("SINPE", ignoreCase = true) ||
                        chat.lastMessageText.contains("₡")
                    val isBuzz = ChatViewModel.isBuzzPreviewText(chat.lastMessageText)
                    val allowed = if (isSinpe) sinpeAlertsEnabled else soundEnabled
                    if (allowed) {
                        val eventKey = "msg_${chat.id}_$tsMillis"
                        if (prefs.markEventNotifiedIfNew(eventKey)) {
                            val title = ChatViewModel.resolveChatDisplayTitle(chat, uid)
                            val body = when {
                                isBuzz -> "$title te envió un bip"
                                else -> chat.lastMessageText.ifBlank { "Tienes un mensaje nuevo" }
                            }
                            NotificationHelper.showNotification(
                                context = applicationContext,
                                channelType = when {
                                    isSinpe -> "sinpe"
                                    isBuzz -> "buzz"
                                    else -> "chat"
                                },
                                title = title,
                                body = body,
                                notificationId = eventKey.hashCode(),
                                targetChatId = chat.id
                            )
                        }
                    }
                }
            }

            // 2. Check incoming pending chat requests
            if (soundEnabled) {
                val reqSnaps = db.collection("chat_requests")
                    .whereArrayContains("participantUids", uid)
                    .limit(20)
                    .get()
                    .await()
                reqSnaps.documents.forEach { doc ->
                    val req = doc.toObject(ChatRequest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        ?: return@forEach
                    if (req.toUid == uid && req.status == "pending") {
                        val eventKey = "req_${req.id}"
                        if (prefs.markEventNotifiedIfNew(eventKey)) {
                            NotificationHelper.showNotification(
                                context = applicationContext,
                                channelType = "request",
                                title = "Nueva solicitud de chat",
                                body = "${req.fromName} (@${req.fromUsername}) quiere chatear contigo",
                                notificationId = req.id.hashCode(),
                                openRequestsTab = true
                            )
                        }
                    }
                }
            }

            // 3. Check incoming ringing P2P calls
            if (callAlertsEnabled) {
                val callSnaps = db.collection("calls")
                    .whereArrayContains("participantUids", uid)
                    .limit(10)
                    .get()
                    .await()
                callSnaps.documents.forEach { doc ->
                    val call = doc.toObject(CallSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        ?: return@forEach
                    if (call.calleeId == uid && call.status == "ringing") {
                        val eventKey = "call_${call.id}"
                        if (prefs.markEventNotifiedIfNew(eventKey)) {
                            NotificationHelper.showNotification(
                                context = applicationContext,
                                channelType = "call",
                                title = "Llamada entrante de ${call.callerName}",
                                body = if (call.callType == "video") "Videollamada VYB" else "Llamada de voz VYB",
                                notificationId = call.id.hashCode(),
                                openCallsTab = true
                            )
                        }
                    }
                }
            }

            prefs.setLastBgSyncEpochMs(System.currentTimeMillis())
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
