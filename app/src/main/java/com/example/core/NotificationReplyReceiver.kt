package com.example.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.example.R
import com.example.data.model.Chat
import com.example.data.model.PublicUser
import com.example.data.repository.ChatRepository
import com.example.data.repository.FeedRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class NotificationReplyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != NotificationHelper.ACTION_DIRECT_REPLY) return
        val replyText = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(NotificationHelper.KEY_TEXT_REPLY)
            ?.toString()
            ?.trim()
            ?.take(4000)
            ?: return

        val chatId = intent.getStringExtra(NotificationHelper.EXTRA_TARGET_CHAT_ID)
            ?.takeIf { it.isNotBlank() }
            ?: return
        val notificationId = intent.getIntExtra(NotificationHelper.EXTRA_NOTIFICATION_ID, chatId.hashCode())
        val chatTitle = intent.getStringExtra(NotificationHelper.EXTRA_CHAT_TITLE).orEmpty().ifBlank { "Chat VYB" }
        val originalBody = intent.getStringExtra(NotificationHelper.EXTRA_ORIGINAL_BODY)

        if (replyText.isBlank()) return

        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val uid = Firebase.auth.currentUser?.uid
                if (uid.isNullOrBlank()) {
                    NotificationHelper.showRepliedConfirmation(
                        context = appContext,
                        notificationId = notificationId,
                        chatId = chatId,
                        chatTitle = chatTitle,
                        originalBody = originalBody,
                        replyText = replyText,
                        failedReason = "Inicia sesión en VYB para responder."
                    )
                    return@launch
                }

                val dbId = appContext.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(dbId)
                val chatSnap = db.collection("chats").document(chatId).get().await()
                val chat = chatSnap.toObject(Chat::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                val userSnap = db.collection("users_public").document(uid).get().await()
                val sender = userSnap.toObject(PublicUser::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: PublicUser(userId = uid, displayName = "Usuario VYB", username = "usuario")

                if (chat == null || uid !in chat.participantUids) {
                    NotificationHelper.showRepliedConfirmation(
                        context = appContext,
                        notificationId = notificationId,
                        chatId = chatId,
                        chatTitle = chatTitle,
                        originalBody = originalBody,
                        replyText = replyText,
                        failedReason = "Esta conversación ya no está disponible."
                    )
                    return@launch
                }

                val chatRepo = ChatRepository(db, FeedRepository(db))
                val res = chatRepo.sendTextMessage(chat = chat, sender = sender, text = replyText)
                if (res.isSuccess) {
                    NotificationHelper.showRepliedConfirmation(
                        context = appContext,
                        notificationId = notificationId,
                        chatId = chatId,
                        chatTitle = chatTitle,
                        originalBody = originalBody,
                        replyText = replyText
                    )
                } else {
                    NotificationHelper.showRepliedConfirmation(
                        context = appContext,
                        notificationId = notificationId,
                        chatId = chatId,
                        chatTitle = chatTitle,
                        originalBody = originalBody,
                        replyText = replyText,
                        failedReason = "No se pudo enviar la respuesta. Reintenta en la app."
                    )
                }
            } catch (_: Exception) {
                NotificationHelper.showRepliedConfirmation(
                    context = appContext,
                    notificationId = notificationId,
                    chatId = chatId,
                    chatTitle = chatTitle,
                    originalBody = originalBody,
                    replyText = replyText,
                    failedReason = "Sin conexión para enviar tu respuesta."
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}
