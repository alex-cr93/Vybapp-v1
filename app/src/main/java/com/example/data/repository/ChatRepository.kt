package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.core.EncodedDocumentPayload
import com.example.core.EncodedMediaPayload
import com.example.core.OperationType
import com.example.core.formatVoiceDuration
import com.example.core.handleFirestoreError
import com.example.data.model.Chat
import com.example.data.model.ChatMessage
import com.example.data.model.ChatRequest
import com.example.data.model.DirectoryEntry
import com.example.data.model.LiveLocationShare
import com.example.data.model.PublicUser
import com.example.data.model.SavedContact
import com.example.data.model.SinpeProfile
import com.example.data.model.SinpeVisibility
import com.example.data.model.StructuredChatSummary
import com.example.data.model.VybConstants
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class ChatRepository(
    private val db: FirebaseFirestore,
    private val feedRepository: FeedRepository,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        FeedRepository(context)
    )

    private fun safeIdPart(raw: String): String =
        raw.replace(Regex("[^a-zA-Z0-9_-]"), "").take(20).ifBlank { "vyb" }

    fun deterministicDirectChatId(uid1: String, uid2: String): String {
        val sorted = listOf(uid1, uid2).sorted()
        return "dm_${safeIdPart(sorted[0])}_${safeIdPart(sorted[1])}"
    }

    fun deterministicRequestId(uid1: String, uid2: String): String {
        val sorted = listOf(uid1, uid2).sorted()
        return "req_${safeIdPart(sorted[0])}_${safeIdPart(sorted[1])}"
    }

    fun observeChats(uid: String): Flow<List<Chat>> =
        db.collection("chats")
            .whereArrayContains("participantUids", uid)
            .snapshots()
            .map { snap ->
                snap.toObjects(Chat::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .filter { uid !in it.hiddenForUids }
                    .sortedByDescending { it.lastMessageAt ?: it.updatedAt ?: Timestamp(0, 0) }
            }

    fun observeChatRequests(uid: String): Flow<List<ChatRequest>> =
        db.collection("chat_requests")
            .whereArrayContains("participantUids", uid)
            .snapshots()
            .map { snap ->
                snap.toObjects(ChatRequest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
            }

    fun observeMessages(chatId: String, uid: String): Flow<List<ChatMessage>> =
        combine(
            db.collection("chats").document(chatId).snapshots(),
            db.collection("chats").document(chatId).collection("messages")
                .whereArrayContains("participantUids", uid)
                .snapshots()
        ) { chatSnap, msgsSnap ->
            if (!chatSnap.exists()) return@combine emptyList()
            val chat = chatSnap.toObject(Chat::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            if (chat != null && uid in chat.hiddenForUids) return@combine emptyList()
            val watermark = chat?.clearedBeforeAt?.get(uid)
            msgsSnap.documents
                .mapNotNull { doc ->
                    val msg = doc.toObject(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        ?: return@mapNotNull null
                    if (watermark == null) {
                        msg
                    } else if (doc.metadata.hasPendingWrites()) {
                        msg
                    } else {
                        val created = msg.createdAt
                        val isAfter = created != null && (
                            created.seconds > watermark.seconds ||
                                (created.seconds == watermark.seconds && created.nanoseconds > watermark.nanoseconds)
                            )
                        if (isAfter) msg else null
                    }
                }
                .sortedBy { it.createdAt ?: Timestamp(0, 0) }
        }

    suspend fun sendChatRequest(
        currentUser: PublicUser,
        target: DirectoryEntry,
        note: String? = null
    ): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sin sesión"))
        if (target.uid == uid) {
            return Result.failure(IllegalStateException("No puedes enviarte una solicitud a ti mismo."))
        }
        val reqId = deterministicRequestId(uid, target.uid)
        val docRef = db.collection("chat_requests").document(reqId)
        return try {
            val snap = docRef.get().await()
            if (snap.exists()) {
                val existingStatus = snap.getString("status")
                val existingFrom = snap.getString("fromUid")
                if (existingStatus == "pending") {
                    val msg = if (existingFrom == uid) {
                        "Ya enviaste una solicitud pendiente a @${target.username}."
                    } else {
                        "@${target.username} ya te envió una solicitud. Acéptala en la pestaña Solicitudes."
                    }
                    return Result.failure(IllegalStateException(msg))
                }
                // Remove old rejected/accepted record so a fresh pending invitation can be created cleanly
                docRef.delete().await()
            }

            val sortedParticipants = listOf(uid, target.uid).sorted()
            val exposePublicSinpe = currentUser.sinpeVisibility == SinpeVisibility.EVERYONE.key
            val payload = mapOf<String, Any?>(
                "fromUid" to uid,
                "fromName" to currentUser.displayName.trim().ifBlank { "Usuario VYB" }.take(60),
                "fromUsername" to currentUser.username.trim().ifBlank { "usuario" }.take(30),
                "fromAvatarUrl" to currentUser.avatarUrl?.takeIf { it.isNotBlank() && it.length <= 200000 },
                "fromSinpeBank" to if (exposePublicSinpe) {
                    currentUser.sinpeBank.trim().take(60).takeIf { it.isNotBlank() }
                } else {
                    null
                },
                "toUid" to target.uid,
                "toName" to target.displayName.trim().ifBlank { "Usuario VYB" }.take(60),
                "toUsername" to target.username.trim().ifBlank { "usuario" }.take(30),
                "toAvatarUrl" to target.avatarUrl?.takeIf { it.isNotBlank() && it.length <= 200000 },
                "participantUids" to sortedParticipants,
                "status" to "pending",
                "note" to note?.trim()?.take(200)?.takeIf { it.isNotBlank() },
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            docRef.set(payload).await()
            Result.success(reqId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "chat_requests/$reqId")
            Result.failure(e)
        }
    }

    suspend fun acceptChatRequest(request: ChatRequest, currentUser: PublicUser): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sin sesión"))
        if (request.toUid != uid) {
            return Result.failure(IllegalStateException("Solo el destinatario puede aceptar esta solicitud."))
        }
        return try {
            val chat = getOrCreateDirectChatModel(
                currentUser = currentUser,
                peerUid = request.fromUid,
                peerName = request.fromName,
                peerAvatarUrl = request.fromAvatarUrl
            ).getOrThrow()

            db.collection("chat_requests").document(request.id).update(
                mapOf(
                    "status" to "accepted",
                    "chatId" to chat.id,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            // Grant requester access to acceptor's /sinpe_profiles/{uid} when visibility allows contacts
            try {
                val sinpeRef = db.collection("sinpe_profiles").document(uid)
                val sinpeSnap = sinpeRef.get().await()
                if (sinpeSnap.exists()) {
                    sinpeRef.update(
                        mapOf(
                            "approvedUids" to FieldValue.arrayUnion(request.fromUid),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                } else {
                    val privSnap = db.collection("users_private").document(uid).get().await()
                    val visibility = SinpeVisibility.fromKey(
                        privSnap.getString("sinpeVisibility") ?: currentUser.sinpeVisibility
                    ).key
                    val bank = (
                        privSnap.getString("sinpeBank")?.takeIf { it.isNotBlank() }
                            ?: currentUser.sinpeBank.takeIf { it.isNotBlank() }
                            ?: "BAC Credomatic"
                        ).take(60)
                    val phone = UserRepository.normalizeCrPhone(privSnap.getString("phone"))
                        ?.let { UserRepository.formatCrPhone(it) }
                    val createSinpe = mapOf<String, Any?>(
                        "userId" to uid,
                        "visibility" to visibility,
                        "sinpeBank" to bank,
                        "sinpePhone" to phone,
                        "approvedUids" to listOf(request.fromUid),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ).filterValues { it != null }
                    sinpeRef.set(createSinpe).await()
                }
            } catch (_: Exception) {
            }

            // Best-effort: save requester to local user's contacts without exposing protected bank data
            try {
                val peerSinpe = try {
                    db.collection("sinpe_profiles").document(request.fromUid).get().await()
                        .toObject(SinpeProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } catch (_: Exception) {
                    null
                }
                val peerBank = peerSinpe?.sinpeBank?.takeIf { it.isNotBlank() }
                    ?: request.fromSinpeBank?.takeIf { it.isNotBlank() }
                val peerPhone = peerSinpe?.sinpePhone?.takeIf { it.isNotBlank() }
                val contactPayload = mapOf<String, Any?>(
                    "contactUid" to request.fromUid,
                    "displayName" to request.fromName.take(60),
                    "username" to request.fromUsername.take(30),
                    "avatarUrl" to request.fromAvatarUrl?.takeIf { it.isNotBlank() && it.length <= 200000 },
                    "sinpeBank" to peerBank?.take(60),
                    "sinpePhone" to peerPhone?.take(20),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                db.collection("users").document(uid).collection("contacts").document(request.fromUid)
                    .set(contactPayload).await()
            } catch (_: Exception) {
            }

            Result.success(chat.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "chat_requests/${request.id}")
            Result.failure(e)
        }
    }

    suspend fun rejectChatRequest(requestId: String): Result<Unit> {
        auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sin sesión"))
        return try {
            db.collection("chat_requests").document(requestId).update(
                mapOf(
                    "status" to "rejected",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "chat_requests/$requestId")
            Result.failure(e)
        }
    }

    suspend fun deleteChatRequest(requestId: String): Result<Unit> {
        auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sin sesión"))
        return try {
            db.collection("chat_requests").document(requestId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "chat_requests/$requestId")
            Result.failure(e)
        }
    }

    suspend fun deleteChat(chatId: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sin sesión"))
        val chatRef = db.collection("chats").document(chatId)
        return try {
            val allParticipantsDeleted = db.runTransaction { tx ->
                val snap = tx.get(chatRef)
                if (!snap.exists()) return@runTransaction false

                val participants = (snap.get("participantUids") as? List<*>)
                    ?.mapNotNull { it as? String }
                    .orEmpty()
                val existingHidden = (snap.get("hiddenForUids") as? List<*>)
                    ?.mapNotNull { it as? String }
                    .orEmpty()
                val updatedHidden = (existingHidden + uid).distinct()

                val rawCleared = (snap.get("clearedBeforeAt") as? Map<*, *>) ?: emptyMap<String, Any>()
                val updatedCleared = rawCleared.entries
                    .mapNotNull { (k, v) ->
                        val key = k as? String ?: return@mapNotNull null
                        val value = v ?: return@mapNotNull null
                        key to value
                    }
                    .toMap()
                    .toMutableMap()
                updatedCleared[uid] = FieldValue.serverTimestamp()

                val rawCounts = (snap.get("unreadCounts") as? Map<*, *>) ?: emptyMap<String, Any>()
                val updatedUnreads = rawCounts.entries
                    .mapNotNull { (k, v) ->
                        val key = k as? String ?: return@mapNotNull null
                        val count = (v as? Number)?.toLong() ?: 0L
                        key to count
                    }
                    .toMap()
                    .toMutableMap()
                updatedUnreads[uid] = 0L

                tx.update(
                    chatRef,
                    mapOf(
                        "hiddenForUids" to updatedHidden,
                        "clearedBeforeAt" to updatedCleared,
                        "unreadCounts" to updatedUnreads,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )

                participants.isNotEmpty() && participants.all { it in updatedHidden }
            }.await()

            if (allParticipantsDeleted) {
                val msgSnaps = chatRef.collection("messages")
                    .whereArrayContains("participantUids", uid)
                    .get()
                    .await()

                val ownedMediaIds = msgSnaps.documents.mapNotNull { doc ->
                    val senderId = doc.getString("senderId")
                    val mediaId = doc.getString("mediaId")?.takeIf { it.isNotBlank() }
                    if (senderId == uid && mediaId != null) mediaId else null
                }.distinct()

                ownedMediaIds.forEach { medId ->
                    runCatching {
                        val metaSnap = db.collection("vybmedia_files").document(medId).get().await()
                        val totalChunks = (metaSnap.getLong("totalChunks") ?: 0L).toInt()
                        for (idx in 0 until totalChunks) {
                            db.collection("vybmedia_files").document(medId)
                                .collection("chunks").document("chunk_$idx").delete().await()
                        }
                        db.collection("vybmedia_files").document(medId).delete().await()
                    }
                }

                msgSnaps.documents.chunked(400).forEach { chunk ->
                    val batch = db.batch()
                    chunk.forEach { doc -> batch.delete(doc.reference) }
                    batch.commit().await()
                }

                chatRef.delete().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "chats/$chatId")
            Result.failure(e)
        }
    }

    suspend fun getOrCreateDirectChat(
        currentUser: PublicUser,
        peerUid: String,
        peerName: String,
        peerAvatarUrl: String?
    ): Result<String> = getOrCreateDirectChatModel(currentUser, peerUid, peerName, peerAvatarUrl).map { it.id }

    suspend fun getOrCreateDirectChatModel(
        currentUser: PublicUser,
        peerUid: String,
        peerName: String,
        peerAvatarUrl: String?
    ): Result<Chat> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sin sesión"))
        if (peerUid == uid) {
            return Result.failure(IllegalStateException("No puedes iniciar un chat contigo mismo."))
        }
        val sortedIds = listOf(uid, peerUid).sorted()
        val deterministicChatId = deterministicDirectChatId(uid, peerUid)
        return try {
            val docRef = db.collection("chats").document(deterministicChatId)
            val snap = docRef.get().await()
            if (snap.exists()) {
                val existing = snap.toObject(Chat::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: Chat(
                        id = deterministicChatId,
                        participantUids = sortedIds,
                        participantNames = mapOf(uid to currentUser.displayName, peerUid to peerName),
                        chatType = "direct",
                        createdBy = uid
                    )
                if (uid in existing.hiddenForUids) {
                    val newHidden = existing.hiddenForUids.filter { it != uid }
                    docRef.update(
                        mapOf(
                            "hiddenForUids" to newHidden,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                    return Result.success(existing.copy(hiddenForUids = newHidden))
                }
                return Result.success(existing)
            }

            val names = mapOf(
                uid to currentUser.displayName.trim().ifBlank { "Usuario VYB" }.take(60),
                peerUid to peerName.trim().ifBlank { "Contacto VYB" }.take(60)
            )
            val avatars = buildMap {
                currentUser.avatarUrl?.takeIf { it.isNotBlank() && it.length <= 200000 }?.let { put(uid, it) }
                peerAvatarUrl?.takeIf { it.isNotBlank() && it.length <= 200000 }?.let { put(peerUid, it) }
            }
            val initialUnreads = mapOf(uid to 0L, peerUid to 0L)
            val chatPayload = mapOf<String, Any?>(
                "participantUids" to sortedIds,
                "participantNames" to names,
                "participantAvatars" to avatars,
                "chatType" to "direct",
                "lastMessageText" to "Conversación iniciada",
                "unreadCounts" to initialUnreads,
                "createdBy" to uid,
                "lastMessageAt" to FieldValue.serverTimestamp(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            docRef.set(chatPayload).await()

            Result.success(
                Chat(
                    id = deterministicChatId,
                    participantUids = sortedIds,
                    participantNames = names,
                    participantAvatars = avatars,
                    chatType = "direct",
                    lastMessageText = "Conversación iniciada",
                    unreadCounts = initialUnreads,
                    createdBy = uid
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "chats/$deterministicChatId")
            Result.failure(e)
        }
    }

    suspend fun createGroupChat(
        currentUser: PublicUser,
        title: String,
        peers: List<Triple<String, String, String?>>
    ): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sin sesión"))
        return try {
            val allUids = (listOf(uid) + peers.map { it.first }).distinct().take(50)
            require(allUids.size >= 2) { "Selecciona al menos un participante." }
            val names = buildMap {
                put(uid, currentUser.displayName.trim().ifBlank { "Usuario VYB" }.take(60))
                peers.forEach { put(it.first, it.second.trim().ifBlank { "Miembro" }.take(60)) }
            }
            val avatars = buildMap {
                currentUser.avatarUrl?.takeIf { it.isNotBlank() && it.length <= 200000 }?.let { put(uid, it) }
                peers.forEach { (id, _, av) ->
                    av?.takeIf { it.isNotBlank() && it.length <= 200000 }?.let { put(id, it) }
                }
            }
            val unreads = allUids.associateWith { 0L }
            val docRef = db.collection("chats").document()
            val payload = mapOf<String, Any?>(
                "participantUids" to allUids,
                "participantNames" to names,
                "participantAvatars" to avatars,
                "chatType" to "group",
                "title" to title.trim().ifBlank { "Grupo VYB" }.take(80),
                "lastMessageText" to "Grupo creado",
                "unreadCounts" to unreads,
                "createdBy" to uid,
                "lastMessageAt" to FieldValue.serverTimestamp(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "chats")
            Result.failure(e)
        }
    }

    suspend fun addGroupParticipants(
        chatId: String,
        peers: List<Triple<String, String, String?>>
    ): Result<Unit> {
        auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sin sesión"))
        if (peers.isEmpty()) return Result.success(Unit)
        val chatRef = db.collection("chats").document(chatId)
        return try {
            db.runTransaction { tx ->
                val snap = tx.get(chatRef)
                if (!snap.exists()) throw IllegalStateException("El grupo ya no existe.")
                val existingUids = (snap.get("participantUids") as? List<*>)
                    ?.mapNotNull { it as? String }
                    .orEmpty()
                val mergedUids = (existingUids + peers.map { it.first }).distinct().take(50)

                val existingNames = (snap.get("participantNames") as? Map<*, *>)
                    ?.entries
                    ?.mapNotNull { (k, v) ->
                        val key = k as? String ?: return@mapNotNull null
                        val value = v as? String ?: return@mapNotNull null
                        key to value
                    }
                    ?.toMap()
                    ?.toMutableMap()
                    ?: mutableMapOf()
                peers.forEach { (peerUid, peerName, _) ->
                    if (peerUid in mergedUids) {
                        existingNames[peerUid] = peerName.trim().ifBlank { "Miembro" }.take(60)
                    }
                }

                val existingAvatars = (snap.get("participantAvatars") as? Map<*, *>)
                    ?.entries
                    ?.mapNotNull { (k, v) ->
                        val key = k as? String ?: return@mapNotNull null
                        val value = v as? String ?: return@mapNotNull null
                        key to value
                    }
                    ?.toMap()
                    ?.toMutableMap()
                    ?: mutableMapOf()
                peers.forEach { (peerUid, _, avatar) ->
                    if (peerUid in mergedUids && !avatar.isNullOrBlank() && avatar.length <= 200000) {
                        existingAvatars[peerUid] = avatar
                    }
                }

                val existingUnreads = (snap.get("unreadCounts") as? Map<*, *>)
                    ?.entries
                    ?.mapNotNull { (k, v) ->
                        val key = k as? String ?: return@mapNotNull null
                        val count = (v as? Number)?.toLong() ?: 0L
                        key to count
                    }
                    ?.toMap()
                    ?.toMutableMap()
                    ?: mutableMapOf()
                mergedUids.forEach { participant ->
                    if (!existingUnreads.containsKey(participant)) {
                        existingUnreads[participant] = 0L
                    }
                }

                tx.update(
                    chatRef,
                    mapOf(
                        "participantUids" to mergedUids,
                        "participantNames" to existingNames,
                        "participantAvatars" to existingAvatars,
                        "unreadCounts" to existingUnreads,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                null
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "chats/$chatId")
            Result.failure(e)
        }
    }

    fun observeChatLiveLocations(chatId: String): Flow<List<LiveLocationShare>> =
        db.collection("chats").document(chatId).collection("live_locations")
            .snapshots()
            .map { snap ->
                val nowMs = System.currentTimeMillis()
                snap.toObjects(LiveLocationShare::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .filter { share ->
                        share.active &&
                            share.userId.isNotBlank() &&
                            (share.expiresAt == null || share.expiresAt.toDate().time > nowMs - 15_000L)
                    }
                    .sortedBy { it.displayName.lowercase() }
            }

    suspend fun startLiveLocationShare(
        chat: Chat,
        sender: PublicUser,
        latitude: Double,
        longitude: Double,
        accuracyMeters: Double?,
        durationMin: Int
    ): Result<Timestamp> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sin sesión"))
        val safeLat = latitude.coerceIn(-90.0, 90.0)
        val safeLng = longitude.coerceIn(-180.0, 180.0)
        val safeDurationMin = durationMin.coerceIn(5, 480)
        val expiresAt = Timestamp(Date(System.currentTimeMillis() + safeDurationMin * 60_000L))
        val chatRef = db.collection("chats").document(chat.id)
        val liveRef = chatRef.collection("live_locations").document(uid)

        return try {
            db.runTransaction { tx ->
                val chatSnap = tx.get(chatRef)
                if (!chatSnap.exists()) {
                    throw IllegalStateException("La conversación ya no existe.")
                }
                val participants = (chatSnap.get("participantUids") as? List<*>)
                    ?.mapNotNull { it as? String }
                    ?.takeIf { it.isNotEmpty() }
                    ?: chat.participantUids
                if (uid !in participants) {
                    throw IllegalStateException("No eres participante de esta conversación.")
                }
                val existingLive = (chatSnap.get("liveLocationUids") as? List<*>)
                    ?.mapNotNull { it as? String }
                    ?.distinct()
                    .orEmpty()

                if (uid !in existingLive && existingLive.size >= VybConstants.MAX_GROUP_LIVE_LOCATION_SHARERS) {
                    throw IllegalStateException(
                        "Este grupo ya alcanzó el límite de ${VybConstants.MAX_GROUP_LIVE_LOCATION_SHARERS} personas compartiendo ubicación en tiempo real."
                    )
                }
                val updatedLive = (existingLive + uid).distinct()
                tx.update(
                    chatRef,
                    mapOf(
                        "liveLocationUids" to updatedLive,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )

                val payload = mapOf<String, Any?>(
                    "userId" to uid,
                    "chatId" to chat.id,
                    "participantUids" to participants,
                    "displayName" to sender.displayName.trim().ifBlank { "Usuario VYB" }.take(60),
                    "avatarUrl" to sender.avatarUrl?.takeIf { it.isNotBlank() && it.length <= 200000 },
                    "latitude" to safeLat,
                    "longitude" to safeLng,
                    "accuracyMeters" to accuracyMeters?.coerceIn(0.0, 50000.0),
                    "active" to true,
                    "expiresAt" to expiresAt,
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                tx.set(liveRef, payload)
                null
            }.await()
            Result.success(expiresAt)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "chats/${chat.id}/live_locations/$uid")
            Result.failure(e)
        }
    }

    suspend fun updateLiveLocationCoordinates(
        chat: Chat,
        sender: PublicUser,
        latitude: Double,
        longitude: Double,
        accuracyMeters: Double?,
        expiresAt: Timestamp
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sin sesión"))
        val safeLat = latitude.coerceIn(-90.0, 90.0)
        val safeLng = longitude.coerceIn(-180.0, 180.0)
        val liveRef = db.collection("chats").document(chat.id)
            .collection("live_locations").document(uid)
        return try {
            val payload = mapOf<String, Any?>(
                "userId" to uid,
                "chatId" to chat.id,
                "participantUids" to chat.participantUids.ifEmpty { listOf(uid) },
                "displayName" to sender.displayName.trim().ifBlank { "Usuario VYB" }.take(60),
                "avatarUrl" to sender.avatarUrl?.takeIf { it.isNotBlank() && it.length <= 200000 },
                "latitude" to safeLat,
                "longitude" to safeLng,
                "accuracyMeters" to accuracyMeters?.coerceIn(0.0, 50000.0),
                "active" to true,
                "expiresAt" to expiresAt,
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            liveRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "chats/${chat.id}/live_locations/$uid")
            Result.failure(e)
        }
    }

    suspend fun stopLiveLocationShare(chatId: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sin sesión"))
        val chatRef = db.collection("chats").document(chatId)
        val liveRef = chatRef.collection("live_locations").document(uid)
        return try {
            db.runTransaction { tx ->
                val chatSnap = tx.get(chatRef)
                if (chatSnap.exists()) {
                    val existingLive = (chatSnap.get("liveLocationUids") as? List<*>)
                        ?.mapNotNull { it as? String }
                        .orEmpty()
                    if (uid in existingLive) {
                        tx.update(
                            chatRef,
                            mapOf(
                                "liveLocationUids" to existingLive.filterNot { it == uid },
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                }
                tx.delete(liveRef)
                null
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "chats/$chatId/live_locations/$uid")
            Result.failure(e)
        }
    }

    suspend fun leaveGroup(chatId: String, actorName: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sin sesión"))
        val chatRef = db.collection("chats").document(chatId)
        val liveRef = chatRef.collection("live_locations").document(uid)
        return try {
            val outcome = db.runTransaction { tx ->
                val snap = tx.get(chatRef)
                if (!snap.exists()) return@runTransaction "missing"
                val chatType = snap.getString("chatType") ?: "direct"
                if (chatType != "group") {
                    throw IllegalStateException("Solo puedes salir de chats grupales.")
                }
                val createdBy = snap.getString("createdBy") ?: ""
                val existingUids = (snap.get("participantUids") as? List<*>)
                    ?.mapNotNull { it as? String }
                    .orEmpty()
                val remainingUids = existingUids.filterNot { it == uid }
                if (remainingUids.isEmpty()) {
                    return@runTransaction if (createdBy == uid) "delete_group" else "hide_group"
                }

                val updatedNames = ((snap.get("participantNames") as? Map<*, *>) ?: emptyMap<String, Any>())
                    .entries
                    .mapNotNull { (k, v) ->
                        val key = k as? String ?: return@mapNotNull null
                        val value = v as? String ?: return@mapNotNull null
                        if (key == uid) null else key to value
                    }
                    .toMap()

                val updatedAvatars = ((snap.get("participantAvatars") as? Map<*, *>) ?: emptyMap<String, Any>())
                    .entries
                    .mapNotNull { (k, v) ->
                        val key = k as? String ?: return@mapNotNull null
                        val value = v as? String ?: return@mapNotNull null
                        if (key == uid) null else key to value
                    }
                    .toMap()

                val updatedUnreads = ((snap.get("unreadCounts") as? Map<*, *>) ?: emptyMap<String, Any>())
                    .entries
                    .mapNotNull { (k, v) ->
                        val key = k as? String ?: return@mapNotNull null
                        val value = (v as? Number)?.toLong() ?: 0L
                        if (key == uid) null else key to value
                    }
                    .toMap()

                val existingLive = (snap.get("liveLocationUids") as? List<*>)
                    ?.mapNotNull { it as? String }
                    .orEmpty()
                    .filterNot { it == uid }

                val cleanActor = actorName.trim().ifBlank { "Un integrante" }.take(40)
                val updates = mutableMapOf<String, Any>(
                    "participantUids" to remainingUids,
                    "participantNames" to updatedNames,
                    "participantAvatars" to updatedAvatars,
                    "unreadCounts" to updatedUnreads,
                    "liveLocationUids" to existingLive,
                    "lastMessageText" to "$cleanActor salió del grupo",
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                tx.update(chatRef, updates)
                tx.delete(liveRef)
                "left"
            }.await()

            when (outcome) {
                "delete_group" -> return deleteGroup(chatId)
                "hide_group" -> return deleteChat(chatId)
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "chats/$chatId")
            Result.failure(e)
        }
    }

    suspend fun deleteGroup(chatId: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sin sesión"))
        val chatRef = db.collection("chats").document(chatId)
        return try {
            val snap = chatRef.get().await()
            if (!snap.exists()) return Result.success(Unit)
            val createdBy = snap.getString("createdBy") ?: ""
            val chatType = snap.getString("chatType") ?: "direct"
            require(chatType == "group") { "Esta acción solo aplica a grupos." }
            require(createdBy == uid) { "Solo la persona creadora puede eliminar el grupo para todos." }

            val liveSnaps = runCatching { chatRef.collection("live_locations").get().await() }.getOrNull()
            liveSnaps?.documents?.chunked(400)?.forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }

            val msgSnaps = chatRef.collection("messages")
                .whereArrayContains("participantUids", uid)
                .get()
                .await()

            val ownedMediaIds = msgSnaps.documents.mapNotNull { doc ->
                val senderId = doc.getString("senderId")
                val mediaId = doc.getString("mediaId")?.takeIf { it.isNotBlank() }
                if (senderId == uid && mediaId != null) mediaId else null
            }.distinct()

            ownedMediaIds.forEach { medId ->
                runCatching {
                    val metaSnap = db.collection("vybmedia_files").document(medId).get().await()
                    val totalChunks = (metaSnap.getLong("totalChunks") ?: 0L).toInt()
                    for (idx in 0 until totalChunks) {
                        db.collection("vybmedia_files").document(medId)
                            .collection("chunks").document("chunk_$idx").delete().await()
                    }
                    db.collection("vybmedia_files").document(medId).delete().await()
                }
            }

            msgSnaps.documents.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { doc -> batch.delete(doc.reference) }
                batch.commit().await()
            }

            chatRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "chats/$chatId")
            Result.failure(e)
        }
    }

    private fun isTimestampBefore(earlier: Timestamp?, later: Timestamp?): Boolean {
        if (later == null) return false
        if (earlier == null) return true
        return earlier.seconds < later.seconds ||
            (earlier.seconds == later.seconds && earlier.nanoseconds < later.nanoseconds)
    }

    suspend fun acknowledgeChatDelivered(
        chatId: String,
        messageIds: List<String> = emptyList()
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        if (chatId.isBlank()) return Result.success(Unit)
        val chatRef = db.collection("chats").document(chatId)
        return try {
            db.runTransaction { tx ->
                val snap = tx.get(chatRef)
                if (!snap.exists()) return@runTransaction null
                val participants = (snap.get("participantUids") as? List<*>)
                    ?.mapNotNull { it as? String }
                    ?: emptyList()
                if (uid !in participants) return@runTransaction null
                val lastSender = snap.getString("lastMessageSenderId")
                val lastMsgAt = snap.getTimestamp("lastMessageAt")
                val rawDeliveredAt = (snap.get("lastDeliveredAt") as? Map<*, *>) ?: emptyMap<String, Any>()
                val currentDeliveredTs = rawDeliveredAt[uid] as? Timestamp
                if (lastSender != uid && isTimestampBefore(currentDeliveredTs, lastMsgAt)) {
                    val updatedDeliveredAt = rawDeliveredAt.entries
                        .mapNotNull { (k, v) ->
                            val key = k as? String ?: return@mapNotNull null
                            val value = v ?: return@mapNotNull null
                            key to value
                        }
                        .toMap()
                        .toMutableMap()
                    updatedDeliveredAt[uid] = FieldValue.serverTimestamp()
                    tx.update(
                        chatRef,
                        mapOf(
                            "lastDeliveredAt" to updatedDeliveredAt,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                null
            }.await()

            val targetMsgIds = if (messageIds.isNotEmpty()) {
                messageIds.filter { it.isNotBlank() }.distinct().take(30)
            } else {
                val recentSnaps = chatRef.collection("messages")
                    .whereArrayContains("participantUids", uid)
                    .limit(15)
                    .get()
                    .await()
                recentSnaps.documents.mapNotNull { doc ->
                    val senderId = doc.getString("senderId")
                    val deleted = doc.getBoolean("deletedForEveryone") == true
                    val delivered = (doc.get("deliveredTo") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                    if (!deleted && senderId != uid && uid !in delivered) doc.id else null
                }
            }

            targetMsgIds.forEach { msgId ->
                runCatching {
                    chatRef.collection("messages").document(msgId).update(
                        mapOf(
                            "deliveredTo" to FieldValue.arrayUnion(uid),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "chats/$chatId")
            Result.failure(e)
        }
    }

    suspend fun acknowledgeMessagesRead(
        chatId: String,
        messageIds: List<String>,
        sendReadReceipt: Boolean
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        val cleanIds = messageIds.filter { it.isNotBlank() }.distinct().take(30)
        if (chatId.isBlank() || cleanIds.isEmpty()) return Result.success(Unit)
        val msgsCol = db.collection("chats").document(chatId).collection("messages")
        return try {
            cleanIds.forEach { msgId ->
                val updates = mutableMapOf<String, Any>(
                    "deliveredTo" to FieldValue.arrayUnion(uid),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (sendReadReceipt) {
                    updates["readBy"] = FieldValue.arrayUnion(uid)
                }
                runCatching {
                    msgsCol.document(msgId).update(updates).await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun markChatRead(chat: Chat, sendReadReceipt: Boolean = true): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sin sesión"))
        val hasUnread = (chat.unreadCounts[uid] ?: 0L) > 0L
        val lastMsgAt = chat.lastMessageAt
        val myLastDelivered = chat.lastDeliveredAt[uid]
        val myLastRead = chat.lastReadAt[uid]
        val needsDeliveryTimestamp = chat.lastMessageSenderId != uid && isTimestampBefore(myLastDelivered, lastMsgAt)
        val needsReadTimestamp = sendReadReceipt &&
            chat.lastMessageSenderId != uid &&
            isTimestampBefore(myLastRead, lastMsgAt)

        if (!hasUnread && !needsReadTimestamp && !needsDeliveryTimestamp) return Result.success(Unit)
        val chatRef = db.collection("chats").document(chat.id)
        return try {
            db.runTransaction { tx ->
                val snap = tx.get(chatRef)
                if (!snap.exists()) return@runTransaction null
                val rawCounts = (snap.get("unreadCounts") as? Map<*, *>) ?: emptyMap<String, Any>()
                val updatedCounts = rawCounts.entries
                    .mapNotNull { (k, v) ->
                        val key = k as? String ?: return@mapNotNull null
                        val num = (v as? Number)?.toLong() ?: 0L
                        key to num
                    }
                    .toMap()
                    .toMutableMap()
                updatedCounts[uid] = 0L

                val rawDeliveredAt = (snap.get("lastDeliveredAt") as? Map<*, *>) ?: emptyMap<String, Any>()
                val updatedDeliveredAt = rawDeliveredAt.entries
                    .mapNotNull { (k, v) ->
                        val key = k as? String ?: return@mapNotNull null
                        val value = v ?: return@mapNotNull null
                        key to value
                    }
                    .toMap()
                    .toMutableMap()
                updatedDeliveredAt[uid] = FieldValue.serverTimestamp()

                val updates = mutableMapOf<String, Any>(
                    "unreadCounts" to updatedCounts,
                    "lastDeliveredAt" to updatedDeliveredAt,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (sendReadReceipt) {
                    val rawReadAt = (snap.get("lastReadAt") as? Map<*, *>) ?: emptyMap<String, Any>()
                    val updatedReadAt = rawReadAt.entries
                        .mapNotNull { (k, v) ->
                            val key = k as? String ?: return@mapNotNull null
                            val value = v ?: return@mapNotNull null
                            key to value
                        }
                        .toMap()
                        .toMutableMap()
                    updatedReadAt[uid] = FieldValue.serverTimestamp()
                    updates["lastReadAt"] = updatedReadAt
                }

                tx.update(chatRef, updates)
                null
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "chats/${chat.id}")
            Result.failure(e)
        }
    }

    suspend fun sendTextMessage(
        chat: Chat,
        sender: PublicUser,
        text: String,
        bombDurationSec: Int? = null
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sin sesión"))
        return try {
            val clean = text.trim().take(4000)
            require(clean.isNotBlank()) { "El mensaje no puede estar vacío." }
            val safeBomb = bombDurationSec?.takeIf { it > 0 }?.coerceIn(1, 3600)
            val msgData = buildMap<String, Any?> {
                put("chatId", chat.id)
                put("participantUids", chat.participantUids)
                put("senderId", uid)
                put("senderName", sender.displayName.trim().ifBlank { "Usuario VYB" }.take(60))
                put("messageType", "text")
                put("text", clean)
                if (safeBomb != null) put("bombDurationSec", safeBomb.toLong())
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }
            val preview = if (safeBomb != null) "💣 Mensaje temporal (${safeBomb}s)" else clean.take(300)
            commitMessageAndUpdateChat(chat.id, uid, preview, msgData)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "chats/${chat.id}/messages")
            Result.failure(e)
        }
    }

    suspend fun sendMediaMessage(
        chat: Chat,
        sender: PublicUser,
        payload: EncodedMediaPayload,
        caption: String?,
        bombDurationSec: Int? = null
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sin sesión"))
        return try {
            val mediaId = feedRepository.uploadChunkedMedia(
                payload = payload,
                scope = "chat",
                allowedUids = chat.participantUids
            ).getOrThrow()
            val label = caption?.trim()?.takeIf { it.isNotBlank() }
                ?: if (payload.isVideo) "Video adjunto" else "Foto adjunta"
            val safeBomb = bombDurationSec?.takeIf { it > 0 }?.coerceIn(1, 3600)
            val msgData = mapOf<String, Any?>(
                "chatId" to chat.id,
                "participantUids" to chat.participantUids,
                "senderId" to uid,
                "senderName" to sender.displayName.trim().ifBlank { "Usuario VYB" }.take(60),
                "messageType" to "media",
                "text" to label.take(4000),
                "mediaId" to mediaId,
                "mediaType" to if (payload.isVideo) "video" else "image",
                "mediaPreviewData" to payload.previewDataUri?.takeIf { it.length <= 300000 },
                "mediaAspectRatio" to payload.aspectRatio,
                "bombDurationSec" to safeBomb?.toLong(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            val preview = if (safeBomb != null) "💣 $label (${safeBomb}s)" else label.take(300)
            commitMessageAndUpdateChat(chat.id, uid, preview, msgData)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "chats/${chat.id}/messages")
            Result.failure(e)
        }
    }

    suspend fun sendAudioMessage(
        chat: Chat,
        sender: PublicUser,
        payload: EncodedMediaPayload,
        durationSec: Int,
        bombDurationSec: Int? = null
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sin sesión"))
        return try {
            val safeDuration = durationSec.coerceIn(1, 300)
            val mediaId = feedRepository.uploadChunkedMedia(
                payload = payload,
                scope = "chat",
                allowedUids = chat.participantUids
            ).getOrThrow()

            val formatted = formatVoiceDuration(safeDuration)
            val label = "Nota de voz ($formatted)"
            val safeBomb = bombDurationSec?.takeIf { it > 0 }?.coerceIn(1, 3600)
            val msgData = mapOf<String, Any?>(
                "chatId" to chat.id,
                "participantUids" to chat.participantUids,
                "senderId" to uid,
                "senderName" to sender.displayName.trim().ifBlank { "Usuario VYB" }.take(60),
                "messageType" to "media",
                "text" to label,
                "mediaId" to mediaId,
                "mediaType" to "audio",
                "audioDurationSec" to safeDuration.toLong(),
                "bombDurationSec" to safeBomb?.toLong(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            val preview = if (safeBomb != null) "💣 🎤 $label (${safeBomb}s)" else "🎤 $label"
            commitMessageAndUpdateChat(chat.id, uid, preview, msgData)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "chats/${chat.id}/messages")
            Result.failure(e)
        }
    }

    suspend fun sendDocumentMessage(
        chat: Chat,
        sender: PublicUser,
        payload: EncodedDocumentPayload
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sin sesión"))
        return try {
            val mediaPayload = EncodedMediaPayload(
                mimeType = payload.mimeType,
                previewDataUri = null,
                fullBytes = payload.fullBytes,
                aspectRatio = 1.0,
                isVideo = false,
                isAudio = false
            )
            val mediaId = feedRepository.uploadChunkedMedia(
                payload = mediaPayload,
                scope = "chat",
                allowedUids = chat.participantUids
            ).getOrThrow()

            val cleanName = payload.fileName.trim().ifBlank { "Documento_VYB" }.take(200)
            val cleanMime = payload.mimeType.trim().ifBlank { "application/octet-stream" }.take(100)
            val msgData = mapOf<String, Any?>(
                "chatId" to chat.id,
                "participantUids" to chat.participantUids,
                "senderId" to uid,
                "senderName" to sender.displayName.trim().ifBlank { "Usuario VYB" }.take(60),
                "messageType" to "document",
                "text" to cleanName,
                "mediaId" to mediaId,
                "documentName" to cleanName,
                "documentMimeType" to cleanMime,
                "documentSizeBytes" to payload.sizeBytes.coerceAtLeast(1L),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            commitMessageAndUpdateChat(chat.id, uid, "📄 $cleanName".take(300), msgData)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "chats/${chat.id}/messages")
            Result.failure(e)
        }
    }

    suspend fun sendLocationMessage(
        chat: Chat,
        sender: PublicUser,
        latitude: Double,
        longitude: Double,
        label: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sin sesión"))
        return try {
            val safeLat = latitude.coerceIn(-90.0, 90.0)
            val safeLng = longitude.coerceIn(-180.0, 180.0)
            val cleanLabel = label.trim().ifBlank { "Ubicación actual" }.take(140)
            val msgData = mapOf<String, Any?>(
                "chatId" to chat.id,
                "participantUids" to chat.participantUids,
                "senderId" to uid,
                "senderName" to sender.displayName.trim().ifBlank { "Usuario VYB" }.take(60),
                "messageType" to "location",
                "text" to "Ubicación: $cleanLabel",
                "locationLat" to safeLat,
                "locationLng" to safeLng,
                "locationLabel" to cleanLabel,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            commitMessageAndUpdateChat(chat.id, uid, "📍 $cleanLabel".take(300), msgData)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "chats/${chat.id}/messages")
            Result.failure(e)
        }
    }

    suspend fun sendContactMessage(
        chat: Chat,
        sender: PublicUser,
        contact: SavedContact
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sin sesión"))
        return try {
            val contactUid = contact.contactUid.trim().take(128)
            require(contactUid.isNotBlank()) { "El contacto no tiene identificador válido." }
            val contactName = contact.displayName.trim().ifBlank { "Contacto VYB" }.take(60)
            val contactUsername = contact.username.trim().ifBlank { "usuario" }.take(30)
            val contactAvatar = contact.avatarUrl?.takeIf { it.isNotBlank() && it.length <= 200000 }
            val msgData = mapOf<String, Any?>(
                "chatId" to chat.id,
                "participantUids" to chat.participantUids,
                "senderId" to uid,
                "senderName" to sender.displayName.trim().ifBlank { "Usuario VYB" }.take(60),
                "messageType" to "contact",
                "text" to "Contacto compartido: $contactName (@$contactUsername)",
                "sharedContactUid" to contactUid,
                "sharedContactName" to contactName,
                "sharedContactUsername" to contactUsername,
                "sharedContactAvatarUrl" to contactAvatar,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            commitMessageAndUpdateChat(chat.id, uid, "👤 Contacto: $contactName".take(300), msgData)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "chats/${chat.id}/messages")
            Result.failure(e)
        }
    }

    suspend fun sendPollMessage(
        chat: Chat,
        sender: PublicUser,
        question: String,
        options: List<String>
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sin sesión"))
        return try {
            val cleanQuestion = question.trim().take(240)
            require(cleanQuestion.isNotBlank()) { "Escribe una pregunta para la encuesta." }
            val cleanOptions = options.map { it.trim().take(80) }.filter { it.isNotBlank() }.distinct().take(6)
            require(cleanOptions.size >= 2) { "Agrega al menos 2 opciones válidas." }
            val msgData = mapOf<String, Any?>(
                "chatId" to chat.id,
                "participantUids" to chat.participantUids,
                "senderId" to uid,
                "senderName" to sender.displayName.trim().ifBlank { "Usuario VYB" }.take(60),
                "messageType" to "poll",
                "text" to "Encuesta: $cleanQuestion",
                "pollQuestion" to cleanQuestion,
                "pollOptions" to cleanOptions,
                "pollVotes" to emptyMap<String, List<String>>(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            commitMessageAndUpdateChat(chat.id, uid, "📊 $cleanQuestion".take(300), msgData)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "chats/${chat.id}/messages")
            Result.failure(e)
        }
    }

    suspend fun votePollOption(
        chatId: String,
        messageId: String,
        optionIndex: Int
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sin sesión"))
        if (optionIndex !in 0..5) {
            return Result.failure(IllegalArgumentException("Opción de encuesta no válida."))
        }
        val msgRef = db.collection("chats").document(chatId).collection("messages").document(messageId)
        return try {
            db.runTransaction { tx ->
                val snap = tx.get(msgRef)
                if (!snap.exists() || snap.getBoolean("deletedForEveryone") == true) {
                    return@runTransaction null
                }
                val optionsList = (snap.get("pollOptions") as? List<*>) ?: emptyList<Any>()
                if (optionIndex >= optionsList.size) {
                    throw IllegalArgumentException("Opción fuera de rango.")
                }

                val rawVotes = (snap.get("pollVotes") as? Map<*, *>) ?: emptyMap<String, Any>()
                val nextVotes = mutableMapOf<String, MutableList<String>>()
                rawVotes.forEach { (k, v) ->
                    val key = k as? String ?: return@forEach
                    val voters = (v as? List<*>)?.mapNotNull { it as? String }?.toMutableList()
                        ?: mutableListOf()
                    if (voters.isNotEmpty()) {
                        nextVotes[key] = voters
                    }
                }

                val targetKey = optionIndex.toString()
                val alreadyVotedThisOption = nextVotes[targetKey]?.contains(uid) == true

                // Single choice: remove prior vote from any other option first
                val keysToRemove = mutableListOf<String>()
                nextVotes.forEach { (key, voters) ->
                    if (key != targetKey && voters.remove(uid) && voters.isEmpty()) {
                        keysToRemove.add(key)
                    }
                }
                keysToRemove.forEach { nextVotes.remove(it) }

                if (alreadyVotedThisOption) {
                    val list = nextVotes[targetKey]
                    if (list != null) {
                        list.remove(uid)
                        if (list.isEmpty()) nextVotes.remove(targetKey)
                    }
                } else {
                    val list = nextVotes.getOrPut(targetKey) { mutableListOf() }
                    if (uid !in list) list.add(uid)
                }

                val immutableVotes = nextVotes.mapValues { it.value.toList() }
                tx.update(
                    msgRef,
                    mapOf(
                        "pollVotes" to immutableVotes,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                null
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "chats/$chatId/messages/$messageId")
            Result.failure(e)
        }
    }

    suspend fun editTextMessage(chatId: String, message: ChatMessage, newText: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sin sesión"))
        return try {
            val clean = newText.trim().take(4000)
            require(clean.isNotBlank()) { "El mensaje editado no puede quedar vacío." }
            require(message.senderId == uid) { "Solo puedes editar tus propios mensajes." }
            require(message.messageType == "text" && message.deletedForEveryone != true && message.bombDurationSec == null) {
                "Este mensaje no se puede editar."
            }
            val createdSec = message.createdAt?.seconds ?: (System.currentTimeMillis() / 1000L)
            val nowSec = System.currentTimeMillis() / 1000L
            require(nowSec - createdSec <= 3600L) { "El límite de 1 hora para editar este mensaje ya venció." }

            val chatRef = db.collection("chats").document(chatId)
            val msgRef = chatRef.collection("messages").document(message.id)
            msgRef.update(
                mapOf(
                    "text" to clean,
                    "editedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            try {
                val chatSnap = chatRef.get().await()
                val lastSender = chatSnap.getString("lastMessageSenderId")
                val lastTs = chatSnap.getTimestamp("lastMessageAt")
                val isLikelyLast = lastSender == uid && (
                    lastTs == null || message.createdAt == null ||
                        kotlin.math.abs(lastTs.seconds - message.createdAt.seconds) <= 2L
                    )
                if (isLikelyLast) {
                    chatRef.update(
                        mapOf(
                            "lastMessageText" to clean.take(300),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            } catch (_: Exception) {
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "chats/$chatId/messages/${message.id}")
            Result.failure(e)
        }
    }

    suspend fun deleteMessageForEveryone(chatId: String, message: ChatMessage): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sin sesión"))
        return try {
            require(message.senderId == uid) { "Solo puedes eliminar tus propios mensajes para todos." }
            val chatRef = db.collection("chats").document(chatId)
            val msgRef = chatRef.collection("messages").document(message.id)
            msgRef.update(
                mapOf(
                    "deletedForEveryone" to true,
                    "text" to "",
                    "mediaId" to FieldValue.delete(),
                    "mediaType" to FieldValue.delete(),
                    "mediaPreviewData" to FieldValue.delete(),
                    "mediaAspectRatio" to FieldValue.delete(),
                    "audioDurationSec" to FieldValue.delete(),
                    "documentName" to FieldValue.delete(),
                    "documentMimeType" to FieldValue.delete(),
                    "documentSizeBytes" to FieldValue.delete(),
                    "locationLat" to FieldValue.delete(),
                    "locationLng" to FieldValue.delete(),
                    "locationLabel" to FieldValue.delete(),
                    "sharedContactUid" to FieldValue.delete(),
                    "sharedContactName" to FieldValue.delete(),
                    "sharedContactUsername" to FieldValue.delete(),
                    "sharedContactAvatarUrl" to FieldValue.delete(),
                    "pollQuestion" to FieldValue.delete(),
                    "pollOptions" to FieldValue.delete(),
                    "pollVotes" to FieldValue.delete(),
                    "aiSummaryTopic" to FieldValue.delete(),
                    "aiSummaryMain" to FieldValue.delete(),
                    "aiSummaryDecisions" to FieldValue.delete(),
                    "aiSummaryTasks" to FieldValue.delete(),
                    "bombDurationSec" to FieldValue.delete(),
                    "bombTriggeredAt" to FieldValue.delete(),
                    "editedAt" to FieldValue.delete(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            try {
                val chatSnap = chatRef.get().await()
                val lastSender = chatSnap.getString("lastMessageSenderId")
                val lastTs = chatSnap.getTimestamp("lastMessageAt")
                val isLikelyLast = lastSender == uid && (
                    lastTs == null || message.createdAt == null ||
                        kotlin.math.abs(lastTs.seconds - message.createdAt.seconds) <= 2L
                    )
                if (isLikelyLast) {
                    chatRef.update(
                        mapOf(
                            "lastMessageText" to "Mensaje eliminado",
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            } catch (_: Exception) {
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "chats/$chatId/messages/${message.id}")
            Result.failure(e)
        }
    }

    suspend fun triggerBombMessage(chatId: String, messageId: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sin sesión"))
        val msgRef = db.collection("chats").document(chatId).collection("messages").document(messageId)
        return try {
            db.runTransaction { tx ->
                val snap = tx.get(msgRef)
                if (!snap.exists()) return@runTransaction null
                val senderId = snap.getString("senderId")
                val bombDur = snap.getLong("bombDurationSec")
                val alreadyTriggered = snap.get("bombTriggeredAt") != null
                val deleted = snap.getBoolean("deletedForEveryone") == true
                if (senderId != null && senderId != uid && bombDur != null && !alreadyTriggered && !deleted) {
                    tx.update(
                        msgRef,
                        mapOf(
                            "bombTriggeredAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                null
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "chats/$chatId/messages/$messageId")
            Result.failure(e)
        }
    }

    suspend fun explodeBombMessage(chatId: String, message: ChatMessage): Result<Unit> {
        auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sin sesión"))
        val chatRef = db.collection("chats").document(chatId)
        val msgRef = chatRef.collection("messages").document(message.id)
        return try {
            msgRef.delete().await()
            try {
                val chatSnap = chatRef.get().await()
                val lastSender = chatSnap.getString("lastMessageSenderId")
                val lastTs = chatSnap.getTimestamp("lastMessageAt")
                val isLikelyLast = lastSender == message.senderId && (
                    lastTs == null || message.createdAt == null ||
                        kotlin.math.abs(lastTs.seconds - message.createdAt.seconds) <= 2L
                    )
                if (isLikelyLast) {
                    chatRef.update(
                        mapOf(
                            "lastMessageText" to "💣 Mensaje autodestruido",
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            } catch (_: Exception) {
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "chats/$chatId/messages/${message.id}")
            Result.failure(e)
        }
    }

    suspend fun sendSinpeCardMessage(
        chat: Chat,
        sender: PublicUser,
        senderPhone: String?,
        totalAmount: Long,
        splitCount: Int,
        reason: String,
        senderBank: String? = null
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sin sesión"))
        return try {
            val safeTotal = totalAmount.coerceAtLeast(1L)
            val safeSplit = splitCount.coerceIn(1, 50)
            val perPerson = ((safeTotal + safeSplit - 1) / safeSplit).coerceAtLeast(1L)
            val cleanReason = reason.trim().ifBlank { "Cobro SINPE Móvil" }.take(200)
            val summaryText = "Cobro SINPE Móvil: ₡$safeTotal por $cleanReason"
            val resolvedBank = senderBank?.trim()?.takeIf { it.isNotBlank() }
                ?: sender.sinpeBank.trim().takeIf { it.isNotBlank() }
                ?: "BAC Credomatic"

            val msgData = mapOf<String, Any?>(
                "chatId" to chat.id,
                "participantUids" to chat.participantUids,
                "senderId" to uid,
                "senderName" to sender.displayName.trim().ifBlank { "Usuario VYB" }.take(60),
                "messageType" to "sinpe",
                "text" to summaryText,
                "sinpeAmount" to safeTotal,
                "sinpePerPerson" to perPerson,
                "sinpeReason" to cleanReason,
                "sinpeRecipientUid" to uid,
                "sinpeRecipientName" to sender.displayName.trim().ifBlank { "Usuario VYB" }.take(60),
                "sinpeRecipientPhone" to (UserRepository.formatCrPhone(senderPhone) ?: "No configurado").take(20),
                "sinpeRecipientBank" to resolvedBank.take(60),
                "sinpeSplitCount" to safeSplit.toLong(),
                "sinpeStatus" to "pending",
                "sinpePaidByUids" to emptyList<String>(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            commitMessageAndUpdateChat(chat.id, uid, summaryText.take(300), msgData)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "chats/${chat.id}/messages")
            Result.failure(e)
        }
    }

    suspend fun authorizeSinpePayment(chatId: String, message: ChatMessage): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sin sesión"))
        val msgRef = db.collection("chats").document(chatId)
            .collection("messages").document(message.id)
        return try {
            db.runTransaction { tx ->
                val snap = tx.get(msgRef)
                if (!snap.exists()) {
                    throw IllegalStateException("El cobro SINPE ya no existe.")
                }
                val currentStatus = snap.getString("sinpeStatus") ?: "pending"
                if (currentStatus == "completed") {
                    return@runTransaction null
                }
                val existingPaid = (snap.get("sinpePaidByUids") as? List<*>)
                    ?.mapNotNull { it as? String }
                    .orEmpty()
                val updatedPaid = (existingPaid + uid).distinct()
                val expectedPayers = ((snap.getLong("sinpeSplitCount") ?: message.sinpeSplitCount ?: 1L).toInt())
                    .coerceAtLeast(1)
                val recipientUid = snap.getString("sinpeRecipientUid") ?: message.sinpeRecipientUid
                val participants = (snap.get("participantUids") as? List<*>)
                    ?.mapNotNull { it as? String }
                    ?: message.participantUids
                val isRecipient = recipientUid == uid
                val newStatus = if (
                    isRecipient ||
                    updatedPaid.size >= expectedPayers ||
                    participants.size <= 2
                ) {
                    "completed"
                } else {
                    "pending"
                }
                tx.update(
                    msgRef,
                    mapOf(
                        "sinpeStatus" to newStatus,
                        "sinpePaidByUids" to updatedPaid,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                null
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "chats/$chatId/messages/${message.id}")
            Result.failure(e)
        }
    }

    suspend fun shareAiSummaryMessage(
        chat: Chat,
        sender: PublicUser,
        summary: StructuredChatSummary
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sin sesión"))
        return try {
            val cleanTopic = summary.topic.trim().take(100)
            val summaryLabel = if (cleanTopic.isNotBlank()) {
                "Resumen del chat · $cleanTopic"
            } else {
                "Resumen del chat"
            }
            val msgData = mapOf<String, Any?>(
                "chatId" to chat.id,
                "participantUids" to chat.participantUids,
                "senderId" to uid,
                "senderName" to sender.displayName.trim().ifBlank { "Usuario VYB" }.take(60),
                "messageType" to "ai_summary",
                "text" to summaryLabel.take(300),
                "aiSummaryTopic" to cleanTopic.takeIf { it.isNotBlank() },
                "aiSummaryMain" to summary.mainPoint.take(1500),
                "aiSummaryDecisions" to summary.decisions.take(30),
                "aiSummaryTasks" to summary.pendingTasks.take(30),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            commitMessageAndUpdateChat(chat.id, uid, summaryLabel.take(300), msgData)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "chats/${chat.id}/messages")
            Result.failure(e)
        }
    }

    suspend fun sendBuzzMessage(
        chat: Chat,
        sender: PublicUser
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sin sesión"))
        val chatRef = db.collection("chats").document(chat.id)
        val msgRef = chatRef.collection("messages").document()
        val privRef = db.collection("users_private").document(uid)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("America/Costa_Rica")
        }.format(Date())
        return try {
            db.runTransaction { tx ->
                val chatSnap = tx.get(chatRef)
                if (!chatSnap.exists()) {
                    throw IllegalStateException("La conversación ya no existe o fue eliminada.")
                }
                val privSnap = tx.get(privRef)
                if (!privSnap.exists()) {
                    throw IllegalStateException("Completa tu perfil antes de enviar un bip.")
                }

                val liveParticipants = (chatSnap.get("participantUids") as? List<*>)
                    ?.mapNotNull { it as? String }
                    ?.takeIf { it.isNotEmpty() }
                    ?: chat.participantUids.ifEmpty { listOf(uid) }

                val oldDate = privSnap.getString("buzzDailyDate") ?: ""
                val oldCount = if (oldDate == today) (privSnap.getLong("buzzDailyCount") ?: 0L) else 0L
                if (oldCount >= 2L) {
                    throw IllegalStateException("Ya usaste tus 2 bips disponibles de hoy.")
                }
                val nextCount = oldCount + 1L

                val rawCounts = (chatSnap.get("unreadCounts") as? Map<*, *>) ?: emptyMap<String, Any>()
                val updatedCounts = liveParticipants.associateWith { participant ->
                    if (participant == uid) 0L
                    else ((rawCounts[participant] as? Number)?.toLong() ?: 0L) + 1L
                }

                val rawReadAt = (chatSnap.get("lastReadAt") as? Map<*, *>) ?: emptyMap<String, Any>()
                val updatedReadAt = rawReadAt.entries
                    .mapNotNull { (k, v) ->
                        val key = k as? String ?: return@mapNotNull null
                        val value = v ?: return@mapNotNull null
                        key to value
                    }
                    .toMap()
                    .toMutableMap()
                updatedReadAt[uid] = FieldValue.serverTimestamp()

                val rawDeliveredAt = (chatSnap.get("lastDeliveredAt") as? Map<*, *>) ?: emptyMap<String, Any>()
                val updatedDeliveredAt = rawDeliveredAt.entries
                    .mapNotNull { (k, v) ->
                        val key = k as? String ?: return@mapNotNull null
                        val value = v ?: return@mapNotNull null
                        key to value
                    }
                    .toMap()
                    .toMutableMap()
                updatedDeliveredAt[uid] = FieldValue.serverTimestamp()

                val msgData = mapOf(
                    "chatId" to chat.id,
                    "participantUids" to liveParticipants,
                    "senderId" to uid,
                    "senderName" to sender.displayName.trim().ifBlank { "Usuario VYB" }.take(60),
                    "messageType" to "buzz",
                    "text" to "Bip",
                    "deliveredTo" to listOf(uid),
                    "readBy" to listOf(uid),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                tx.update(
                    privRef,
                    mapOf(
                        "buzzDailyDate" to today,
                        "buzzDailyCount" to nextCount,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                tx.set(msgRef, msgData)
                tx.update(
                    chatRef,
                    mapOf(
                        "lastMessageText" to "Bip",
                        "lastMessageSenderId" to uid,
                        "lastMessageAt" to FieldValue.serverTimestamp(),
                        "unreadCounts" to updatedCounts,
                        "lastDeliveredAt" to updatedDeliveredAt,
                        "lastReadAt" to updatedReadAt,
                        "hiddenForUids" to emptyList<String>(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                null
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "chats/${chat.id}")
            Result.failure(e)
        }
    }

    private suspend fun commitMessageAndUpdateChat(
        chatId: String,
        senderUid: String,
        lastPreview: String,
        msgData: Map<String, Any?>
    ) {
        val chatRef = db.collection("chats").document(chatId)
        val msgRef = chatRef.collection("messages").document()
        val baseCleanMsg = msgData.mapNotNull { (k, v) -> v?.let { k to it } }.toMap().toMutableMap()

        db.runTransaction { tx ->
            val chatSnap = tx.get(chatRef)
            if (!chatSnap.exists()) {
                throw IllegalStateException("La conversación ya no existe o fue eliminada.")
            }
            val liveParticipants = (chatSnap.get("participantUids") as? List<*>)
                ?.mapNotNull { it as? String }
                ?.takeIf { it.isNotEmpty() }
                ?: (baseCleanMsg["participantUids"] as? List<*>)?.mapNotNull { it as? String }
                ?: listOf(senderUid)

            baseCleanMsg["participantUids"] = liveParticipants
            baseCleanMsg["deliveredTo"] = listOf(senderUid)
            baseCleanMsg["readBy"] = listOf(senderUid)

            val rawCounts = (chatSnap.get("unreadCounts") as? Map<*, *>) ?: emptyMap<String, Any>()
            val updatedCounts = liveParticipants.associateWith { participant ->
                if (participant == senderUid) 0L
                else ((rawCounts[participant] as? Number)?.toLong() ?: 0L) + 1L
            }

            val rawReadAt = (chatSnap.get("lastReadAt") as? Map<*, *>) ?: emptyMap<String, Any>()
            val updatedReadAt = rawReadAt.entries
                .mapNotNull { (k, v) ->
                    val key = k as? String ?: return@mapNotNull null
                    val value = v ?: return@mapNotNull null
                    key to value
                }
                .toMap()
                .toMutableMap()
            updatedReadAt[senderUid] = FieldValue.serverTimestamp()

            val rawDeliveredAt = (chatSnap.get("lastDeliveredAt") as? Map<*, *>) ?: emptyMap<String, Any>()
            val updatedDeliveredAt = rawDeliveredAt.entries
                .mapNotNull { (k, v) ->
                    val key = k as? String ?: return@mapNotNull null
                    val value = v ?: return@mapNotNull null
                    key to value
                }
                .toMap()
                .toMutableMap()
            updatedDeliveredAt[senderUid] = FieldValue.serverTimestamp()

            tx.set(msgRef, baseCleanMsg)
            tx.update(
                chatRef,
                mapOf(
                    "lastMessageText" to lastPreview.take(300),
                    "lastMessageSenderId" to senderUid,
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "unreadCounts" to updatedCounts,
                    "lastDeliveredAt" to updatedDeliveredAt,
                    "lastReadAt" to updatedReadAt,
                    "hiddenForUids" to emptyList<String>(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            null
        }.await()
    }
}
