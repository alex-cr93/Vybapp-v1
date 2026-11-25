package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.data.model.CallSession
import com.example.data.model.PublicUser
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

data class IceCandidatePayload(
    val id: String,
    val senderId: String,
    val targetUid: String? = null,
    val sdpMid: String,
    val sdpMLineIndex: Int,
    val sdpCandidate: String
)

data class CallSignalPayload(
    val id: String,
    val fromUid: String,
    val toUid: String,
    val type: String,
    val sdp: String
)

class CallRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    fun observeCalls(uid: String): Flow<List<CallSession>> = flow {
        val activeUid = auth.currentUser?.uid?.takeIf { it.isNotBlank() && it == uid }
            ?: throw IllegalStateException("Tu sesión aún se está verificando. Toca Reintentar.")
        emitAll(
            db.collection("calls")
                .whereArrayContains("participantUids", activeUid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(CallSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .filter { it.groupCall || it.callerId == activeUid || it.calleeId == activeUid }
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    val exceptionToLog = fsError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == activeUid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, "calls")
                    }
                    throw error
                }
        )
    }

    fun observeCallDocument(callId: String): Flow<CallSession?> =
        db.collection("calls").document(callId)
            .snapshots()
            .map { snap -> snap.toObject(CallSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }

    fun observePeerSignals(callId: String, uid: String): Flow<List<CallSignalPayload>> =
        db.collection("calls").document(callId).collection("signals")
            .whereArrayContains("participantUids", uid)
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    val fromUid = data["fromUid"] as? String ?: return@mapNotNull null
                    val toUid = data["toUid"] as? String ?: return@mapNotNull null
                    val type = data["type"] as? String ?: return@mapNotNull null
                    val sdp = data["sdp"] as? String ?: return@mapNotNull null
                    CallSignalPayload(
                        id = doc.id,
                        fromUid = fromUid,
                        toUid = toUid,
                        type = type,
                        sdp = sdp
                    )
                }
            }

    fun observeIceCandidates(callId: String, uid: String): Flow<List<IceCandidatePayload>> =
        db.collection("calls").document(callId).collection("iceCandidates")
            .whereArrayContains("participantUids", uid)
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    IceCandidatePayload(
                        id = doc.id,
                        senderId = data["senderId"] as? String ?: return@mapNotNull null,
                        targetUid = data["targetUid"] as? String,
                        sdpMid = data["sdpMid"] as? String ?: "",
                        sdpMLineIndex = (data["sdpMLineIndex"] as? Number)?.toInt() ?: 0,
                        sdpCandidate = data["sdpCandidate"] as? String ?: return@mapNotNull null
                    )
                }
            }

    suspend fun createOutgoingCall(
        caller: PublicUser,
        calleeUid: String,
        calleeName: String,
        calleeAvatar: String?,
        callType: String,
        offerSdp: String
    ): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sin sesión"))
        return try {
            val docRef = db.collection("calls").document()
            val payload = mapOf<String, Any?>(
                "callerId" to uid,
                "callerName" to caller.displayName,
                "callerAvatar" to caller.avatarUrl,
                "calleeId" to calleeUid,
                "calleeName" to calleeName,
                "calleeAvatar" to calleeAvatar,
                "participantUids" to listOf(uid, calleeUid),
                "callType" to callType,
                "status" to "ringing",
                "offerSdp" to offerSdp.take(35000),
                "durationSeconds" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "calls")
            Result.failure(e)
        }
    }

    suspend fun createGroupCall(
        caller: PublicUser,
        chatId: String,
        groupTitle: String,
        participantUids: List<String>,
        callType: String
    ): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sin sesión"))
        val cleanParticipants = (listOf(uid) + participantUids).distinct().take(20)
        if (cleanParticipants.size < 2) {
            return Result.failure(IllegalStateException("El grupo necesita al menos 2 participantes para llamar."))
        }
        val safeTitle = groupTitle.trim().ifBlank { "Grupo VYB" }.take(60)
        val safeChatId = chatId.replace(Regex("[^a-zA-Z0-9_-]"), "").take(128).ifBlank { "group" }
        return try {
            val docRef = db.collection("calls").document()
            val payload = mapOf<String, Any?>(
                "callerId" to uid,
                "callerName" to caller.displayName.trim().ifBlank { "Usuario VYB" }.take(60),
                "callerAvatar" to caller.avatarUrl?.takeIf { it.isNotBlank() && it.length <= 200000 },
                "calleeId" to safeChatId,
                "calleeName" to safeTitle,
                "participantUids" to cleanParticipants,
                "callType" to callType,
                "status" to "ringing",
                "durationSeconds" to 0L,
                "groupCall" to true,
                "chatId" to safeChatId,
                "groupTitle" to groupTitle.trim().ifBlank { "Grupo VYB" }.take(80),
                "activeParticipantUids" to listOf(uid),
                "declinedUids" to emptyList<String>(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "calls")
            Result.failure(e)
        }
    }

    suspend fun joinGroupCall(callId: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sin sesión"))
        val callRef = db.collection("calls").document(callId)
        return try {
            db.runTransaction { tx ->
                val snap = tx.get(callRef)
                if (!snap.exists()) return@runTransaction null
                val status = snap.getString("status") ?: return@runTransaction null
                if (status in listOf("ended", "rejected", "missed")) return@runTransaction null
                val active = (snap.get("activeParticipantUids") as? List<*>)
                    ?.mapNotNull { it as? String }
                    .orEmpty()
                val declined = (snap.get("declinedUids") as? List<*>)
                    ?.mapNotNull { it as? String }
                    .orEmpty()
                val nextActive = (active + uid).distinct().take(20)
                val nextDeclined = declined.filterNot { it == uid }
                val nextStatus = if (nextActive.size >= 2 || status == "active") "active" else status
                tx.update(
                    callRef,
                    mapOf(
                        "status" to nextStatus,
                        "activeParticipantUids" to nextActive,
                        "declinedUids" to nextDeclined,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                null
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "calls/$callId")
            Result.failure(e)
        }
    }

    suspend fun declineGroupCall(callId: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sin sesión"))
        val callRef = db.collection("calls").document(callId)
        return try {
            db.runTransaction { tx ->
                val snap = tx.get(callRef)
                if (!snap.exists()) return@runTransaction null
                val status = snap.getString("status") ?: return@runTransaction null
                if (status in listOf("ended", "rejected", "missed")) return@runTransaction null
                val active = (snap.get("activeParticipantUids") as? List<*>)
                    ?.mapNotNull { it as? String }
                    .orEmpty()
                    .filterNot { it == uid }
                val declined = (snap.get("declinedUids") as? List<*>)
                    ?.mapNotNull { it as? String }
                    .orEmpty()
                val nextDeclined = (declined + uid).distinct().take(20)
                tx.update(
                    callRef,
                    mapOf(
                        "activeParticipantUids" to active,
                        "declinedUids" to nextDeclined,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                null
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "calls/$callId")
            Result.failure(e)
        }
    }

    suspend fun leaveOrEndGroupCall(callId: String, durationSeconds: Long): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sin sesión"))
        val callRef = db.collection("calls").document(callId)
        return try {
            db.runTransaction { tx ->
                val snap = tx.get(callRef)
                if (!snap.exists()) return@runTransaction null
                val status = snap.getString("status") ?: return@runTransaction null
                if (status in listOf("ended", "rejected", "missed")) return@runTransaction null
                val active = (snap.get("activeParticipantUids") as? List<*>)
                    ?.mapNotNull { it as? String }
                    .orEmpty()
                    .filterNot { it == uid }
                val shouldEnd = active.isEmpty() || (status == "ringing" && snap.getString("callerId") == uid)
                val updates = mutableMapOf<String, Any>(
                    "activeParticipantUids" to if (shouldEnd) emptyList<String>() else active,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (shouldEnd) {
                    updates["status"] = if (status == "ringing" && active.isEmpty()) "missed" else "ended"
                    updates["durationSeconds"] = durationSeconds.coerceAtLeast(0L)
                }
                tx.update(callRef, updates)
                null
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "calls/$callId")
            Result.failure(e)
        }
    }

    suspend fun upsertPeerSignal(
        callId: String,
        participantUids: List<String>,
        toUid: String,
        type: String,
        sdp: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sin sesión"))
        val safeFrom = uid.replace(Regex("[^a-zA-Z0-9_-]"), "").take(50)
        val safeTo = toUid.replace(Regex("[^a-zA-Z0-9_-]"), "").take(50)
        val signalId = "${safeFrom}__${safeTo}"
        val signalRef = db.collection("calls").document(callId).collection("signals").document(signalId)
        return try {
            val snap = signalRef.get().await()
            if (snap.exists()) {
                signalRef.update(
                    mapOf(
                        "type" to type,
                        "sdp" to sdp.take(35000),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                signalRef.set(
                    mapOf(
                        "participantUids" to participantUids.take(20),
                        "fromUid" to uid,
                        "toUid" to toUid,
                        "type" to type,
                        "sdp" to sdp.take(35000),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "calls/$callId/signals/$signalId")
            Result.failure(e)
        }
    }

    suspend fun answerCall(callId: String, answerSdp: String): Result<Unit> {
        return try {
            db.collection("calls").document(callId).update(
                mapOf(
                    "status" to "active",
                    "answerSdp" to answerSdp.take(35000),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "calls/$callId")
            Result.failure(e)
        }
    }

    suspend fun updateCallStatus(callId: String, status: String, durationSeconds: Long): Result<Unit> {
        return try {
            db.collection("calls").document(callId).update(
                mapOf(
                    "status" to status,
                    "durationSeconds" to durationSeconds.coerceAtLeast(0L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "calls/$callId")
            Result.failure(e)
        }
    }

    suspend fun pushIceCandidate(
        callId: String,
        participantUids: List<String>,
        sdpMid: String,
        sdpMLineIndex: Int,
        sdpCandidate: String,
        targetUid: String? = null
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sin sesión"))
        return try {
            val data = mapOf<String, Any?>(
                "participantUids" to participantUids.take(20),
                "senderId" to uid,
                "targetUid" to targetUid?.takeIf { it.isNotBlank() && it in participantUids },
                "sdpMid" to sdpMid.take(64),
                "sdpMLineIndex" to sdpMLineIndex,
                "sdpCandidate" to sdpCandidate.take(2000),
                "createdAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            db.collection("calls").document(callId)
                .collection("iceCandidates").document().set(data).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "calls/$callId/iceCandidates")
            Result.failure(e)
        }
    }
}
