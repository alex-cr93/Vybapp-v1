package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.data.model.DirectoryEntry
import com.example.data.model.LiveLocationScope
import com.example.data.model.PrivateUser
import com.example.data.model.PublicUser
import com.example.data.model.SavedContact
import com.example.data.model.SinpeProfile
import com.example.data.model.SinpeVisibility
import com.google.firebase.Firebase
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class UserRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Debes iniciar sesión para acceder a VYB.")
    }

    fun observePublicProfile(uid: String): Flow<PublicUser?> = flow {
        val activeUid = requireUserId()
        require(uid.isNotBlank()) { "El ID de usuario es obligatorio." }
        val path = "users_public/$uid"
        emitAll(
            db.collection("users_public").document(uid)
                .snapshots()
                .map { snap -> snap.toObject(PublicUser::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<com.google.firebase.firestore.FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == activeUid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observePrivateProfile(uid: String): Flow<PrivateUser?> = flow {
        val activeUid = requireUserId()
        require(uid.isNotBlank()) { "El ID de usuario es obligatorio." }
        val path = "users_private/$uid"
        emitAll(
            db.collection("users_private").document(uid)
                .snapshots()
                .map { snap -> snap.toObject(PrivateUser::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<com.google.firebase.firestore.FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == activeUid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeContacts(uid: String): Flow<List<SavedContact>> = flow {
        val activeUid = requireUserId()
        require(uid.isNotBlank()) { "El ID de usuario es obligatorio." }
        val path = "users/$uid/contacts"
        emitAll(
            db.collection("users").document(uid).collection("contacts")
                .snapshots()
                .map { snap ->
                    snap.toObjects(SavedContact::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.displayName.lowercase() }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<com.google.firebase.firestore.FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == activeUid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun getPublicUser(uid: String): PublicUser? {
        if (uid.isBlank()) return null
        return try {
            requireUserId()
            db.collection("users_public").document(uid).get().await()
                .toObject(PublicUser::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "users_public/$uid")
            null
        }
    }

    suspend fun getPrivateUser(uid: String): PrivateUser? {
        if (uid.isBlank()) return null
        return try {
            requireUserId()
            db.collection("users_private").document(uid).get().await()
                .toObject(PrivateUser::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "users_private/$uid")
            null
        }
    }

    suspend fun checkUsernameAvailable(username: String, currentUid: String): Boolean {
        val clean = normalizeUsername(username)
        if (clean.length < 3) return false
        return try {
            requireUserId()
            val doc = db.collection("directory_lookup").document("user_$clean").get().await()
            !doc.exists() || doc.getString("uid") == currentUid
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "directory_lookup/user_$clean")
            true
        }
    }

    suspend fun getSinpeProfile(uid: String): SinpeProfile? {
        if (uid.isBlank()) return null
        return try {
            requireUserId()
            db.collection("sinpe_profiles").document(uid).get().await()
                .toObject(SinpeProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    fun observeSinpeProfile(uid: String): Flow<SinpeProfile?> = flow {
        requireUserId()
        require(uid.isNotBlank()) { "El ID de usuario es obligatorio." }
        emitAll(
            db.collection("sinpe_profiles").document(uid)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(SinpeProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else {
                        null
                    }
                }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }
                        .filterIsInstance<com.google.firebase.firestore.FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    emit(null)
                }
        )
    }

    suspend fun saveFullProfile(
        displayName: String,
        rawUsername: String,
        sinpeBank: String,
        rawPhone: String?,
        email: String?,
        quickStatus: String,
        locationLabel: String?,
        bio: String?,
        avatarUrl: String?,
        isVerified: Boolean,
        hasPasswordAuth: Boolean? = null,
        authEmail: String? = null,
        sinpeVisibility: String? = null
    ): Result<Unit> {
        val cleanUser = normalizeUsername(rawUsername)
        val trimmedPhoneInput = rawPhone?.trim().orEmpty()
        val cleanPhone = normalizeCrPhone(rawPhone)
        val cleanStatus = quickStatus.trim().ifBlank { "Disponible" }.take(40)
        val cleanBank = sinpeBank.trim().ifBlank { "BAC Credomatic" }.take(60)
        val cleanEmail = email?.trim()?.lowercase()?.take(120)?.takeIf { it.isNotBlank() }
        var publicPath = "users_public/unauthenticated"
        return try {
            val uid = requireUserId()
            publicPath = "users_public/$uid"
            require(displayName.trim().isNotEmpty()) { "El nombre visible es obligatorio." }
            require(cleanUser.length in 3..30) { "El @usuario debe tener entre 3 y 30 caracteres (letras, números o _)." }
            require(trimmedPhoneInput.isEmpty() || cleanPhone != null) {
                "El número SINPE Móvil debe tener exactamente 8 dígitos (formato XXXX-XXXX)."
            }

            val existingPublic = db.collection("users_public").document(uid).get().await()
            val existingPrivate = db.collection("users_private").document(uid).get().await()
            val existingSinpe = try {
                db.collection("sinpe_profiles").document(uid).get().await()
            } catch (_: Exception) {
                null
            }

            val cleanVisibility = SinpeVisibility.fromKey(
                sinpeVisibility
                    ?: existingPrivate.getString("sinpeVisibility")
                    ?: existingSinpe?.getString("visibility")
                    ?: existingPublic.getString("sinpeVisibility")
            ).key
            val exposePublicSinpe = cleanVisibility == SinpeVisibility.EVERYONE.key

            val oldUsername = existingPublic.getString("username")
            val oldPhone = existingPrivate.getString("phone")?.let { normalizeCrPhone(it) }
            val oldEmail = existingPrivate.getString("email")?.trim()?.lowercase()
            val effectiveAuthEmail = (authEmail ?: existingPrivate.getString("authEmail"))
                ?.trim()?.lowercase()?.take(160)?.takeIf { it.isNotBlank() }

            val usernameDoc = db.collection("directory_lookup").document("user_$cleanUser").get().await()
            if (usernameDoc.exists() && usernameDoc.getString("uid") != uid) {
                return Result.failure(IllegalStateException("Ese @usuario ya está en uso."))
            }

            val safeAvatarUrl = avatarUrl?.takeIf { it.isNotBlank() && it.length <= 200_000 }
            if (existingPublic.exists()) {
                val publicUpdates = mutableMapOf<String, Any>(
                    "displayName" to displayName.trim().take(60),
                    "username" to cleanUser,
                    "quickStatus" to cleanStatus,
                    "sinpeVisibility" to cleanVisibility,
                    "verified" to isVerified,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                safeAvatarUrl?.let { publicUpdates["avatarUrl"] = it }
                locationLabel?.trim()?.take(80)?.takeIf { it.isNotBlank() }?.let {
                    publicUpdates["locationLabel"] = it
                }
                bio?.trim()?.take(240)?.takeIf { it.isNotBlank() }?.let {
                    publicUpdates["bio"] = it
                }
                if (exposePublicSinpe) {
                    publicUpdates["sinpeBank"] = cleanBank
                } else if (existingPublic.contains("sinpeBank")) {
                    publicUpdates["sinpeBank"] = FieldValue.delete()
                }
                db.collection("users_public").document(uid).update(publicUpdates).await()
            } else {
                val publicCreate = mapOf<String, Any?>(
                    "userId" to uid,
                    "displayName" to displayName.trim().take(60),
                    "username" to cleanUser,
                    "avatarUrl" to safeAvatarUrl,
                    "quickStatus" to cleanStatus,
                    "locationLabel" to locationLabel?.trim()?.take(80)?.takeIf { it.isNotBlank() },
                    "bio" to bio?.trim()?.take(240)?.takeIf { it.isNotBlank() },
                    "sinpeBank" to if (exposePublicSinpe) cleanBank else null,
                    "sinpeVisibility" to cleanVisibility,
                    "verified" to isVerified,
                    "postsCount" to 0L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                db.collection("users_public").document(uid).set(publicCreate).await()
            }

            val formattedPhone = cleanPhone?.let { formatCrPhone(it) }
            if (existingPrivate.exists()) {
                val privUpdates = mutableMapOf<String, Any>(
                    "sinpeBank" to cleanBank,
                    "sinpeVisibility" to cleanVisibility,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (cleanEmail != null) privUpdates["email"] = cleanEmail
                if (formattedPhone != null) {
                    privUpdates["phone"] = formattedPhone
                } else if (trimmedPhoneInput.isEmpty() && oldPhone != null) {
                    privUpdates["phone"] = FieldValue.delete()
                }
                if (hasPasswordAuth != null) privUpdates["hasPasswordAuth"] = hasPasswordAuth
                if (effectiveAuthEmail != null) privUpdates["authEmail"] = effectiveAuthEmail
                db.collection("users_private").document(uid).update(privUpdates).await()
            } else {
                val privCreate = mapOf<String, Any?>(
                    "userId" to uid,
                    "email" to cleanEmail,
                    "phone" to formattedPhone,
                    "sinpeBank" to cleanBank,
                    "sinpeVisibility" to cleanVisibility,
                    "blockedUids" to emptyList<String>(),
                    "blockedPhones" to emptyList<String>(),
                    "liveLocationScope" to LiveLocationScope.CONTACTS_ONLY.key,
                    "liveLocationDurationMin" to 60L,
                    "liveLocationShowDistance" to true,
                    "hasPasswordAuth" to hasPasswordAuth,
                    "authEmail" to effectiveAuthEmail,
                    "readReceipts" to true,
                    "showOnlineStatus" to true,
                    "soundEnabled" to true,
                    "callAlertsEnabled" to true,
                    "sinpeAlertsEnabled" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                db.collection("users_private").document(uid).set(privCreate).await()
            }

            upsertSinpeProfile(
                uid = uid,
                visibility = cleanVisibility,
                sinpeBank = cleanBank,
                formattedPhone = formattedPhone,
                existingSinpe = existingSinpe
            )

            // Update directory_lookup for username
            if (!oldUsername.isNullOrBlank() && oldUsername != cleanUser) {
                try {
                    db.collection("directory_lookup").document("user_$oldUsername").delete().await()
                } catch (_: Exception) {}
            }
            upsertDirectoryKey(
                key = "user_$cleanUser",
                uid = uid,
                displayName = displayName.trim().take(60),
                username = cleanUser,
                avatarUrl = avatarUrl,
                sinpeBank = cleanBank,
                sinpeVisibility = cleanVisibility,
                phone = formattedPhone,
                entryType = "username"
            )

            // Update directory_lookup for +506 phone
            if (!oldPhone.isNullOrBlank() && oldPhone != cleanPhone) {
                try {
                    db.collection("directory_lookup").document("phone_$oldPhone").delete().await()
                } catch (_: Exception) {}
            }
            if (cleanPhone != null) {
                upsertDirectoryKey(
                    key = "phone_$cleanPhone",
                    uid = uid,
                    displayName = displayName.trim().take(60),
                    username = cleanUser,
                    avatarUrl = avatarUrl,
                    sinpeBank = cleanBank,
                    sinpeVisibility = cleanVisibility,
                    phone = formattedPhone,
                    entryType = "phone"
                )
            }

            // Sync SHA-256 auth_aliases for cross-device login if authEmail is set
            if (!effectiveAuthEmail.isNullOrBlank()) {
                syncAuthAliases(
                    uid = uid,
                    authEmail = effectiveAuthEmail,
                    username = cleanUser,
                    oldUsername = oldUsername,
                    phone8 = cleanPhone,
                    oldPhone8 = oldPhone,
                    contactEmail = cleanEmail,
                    oldContactEmail = oldEmail
                )
            }

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, publicPath)
            Result.failure(e)
        }
    }

    private suspend fun upsertSinpeProfile(
        uid: String,
        visibility: String,
        sinpeBank: String,
        formattedPhone: String?,
        existingSinpe: DocumentSnapshot? = null
    ) {
        val sinpeRef = db.collection("sinpe_profiles").document(uid)
        val snap = existingSinpe ?: sinpeRef.get().await()
        if (snap.exists()) {
            val updates = mutableMapOf<String, Any>(
                "visibility" to visibility,
                "sinpeBank" to sinpeBank,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!formattedPhone.isNullOrBlank()) {
                updates["sinpePhone"] = formattedPhone
            } else if (snap.contains("sinpePhone")) {
                updates["sinpePhone"] = FieldValue.delete()
            }
            sinpeRef.update(updates).await()
        } else {
            val create = mapOf<String, Any?>(
                "userId" to uid,
                "visibility" to visibility,
                "sinpeBank" to sinpeBank,
                "sinpePhone" to formattedPhone?.takeIf { it.isNotBlank() },
                "approvedUids" to emptyList<String>(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            sinpeRef.set(create).await()
        }
    }

    suspend fun updateSinpeVisibility(visibility: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sin sesión"))
        val cleanVisibility = SinpeVisibility.fromKey(visibility).key
        val exposePublic = cleanVisibility == SinpeVisibility.EVERYONE.key
        return try {
            val pubSnap = db.collection("users_public").document(uid).get().await()
            val privSnap = db.collection("users_private").document(uid).get().await()
            val sinpeSnap = try {
                db.collection("sinpe_profiles").document(uid).get().await()
            } catch (_: Exception) {
                null
            }

            val resolvedBank = (
                privSnap.getString("sinpeBank")?.takeIf { it.isNotBlank() }
                    ?: sinpeSnap?.getString("sinpeBank")?.takeIf { it.isNotBlank() }
                    ?: pubSnap.getString("sinpeBank")?.takeIf { it.isNotBlank() }
                    ?: "BAC Credomatic"
                ).take(60)
            val rawPhone = privSnap.getString("phone") ?: sinpeSnap?.getString("sinpePhone")
            val formattedPhone = normalizeCrPhone(rawPhone)?.let { formatCrPhone(it) }

            if (privSnap.exists()) {
                db.collection("users_private").document(uid).update(
                    mapOf(
                        "sinpeVisibility" to cleanVisibility,
                        "sinpeBank" to resolvedBank,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            if (pubSnap.exists()) {
                val pubUpdates = mutableMapOf<String, Any>(
                    "sinpeVisibility" to cleanVisibility,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (exposePublic) {
                    pubUpdates["sinpeBank"] = resolvedBank
                } else if (pubSnap.contains("sinpeBank")) {
                    pubUpdates["sinpeBank"] = FieldValue.delete()
                }
                db.collection("users_public").document(uid).update(pubUpdates).await()
            }

            upsertSinpeProfile(
                uid = uid,
                visibility = cleanVisibility,
                sinpeBank = resolvedBank,
                formattedPhone = formattedPhone,
                existingSinpe = sinpeSnap
            )

            val username = pubSnap.getString("username")?.trim().orEmpty()
            val displayName = pubSnap.getString("displayName")?.trim().orEmpty().ifBlank { "Usuario VYB" }
            val avatarUrl = pubSnap.getString("avatarUrl")
            if (username.length >= 3) {
                upsertDirectoryKey(
                    key = "user_$username",
                    uid = uid,
                    displayName = displayName,
                    username = username,
                    avatarUrl = avatarUrl,
                    sinpeBank = resolvedBank,
                    sinpeVisibility = cleanVisibility,
                    phone = formattedPhone,
                    entryType = "username"
                )
            }
            val phone8 = normalizeCrPhone(rawPhone)
            if (phone8 != null && username.length >= 3) {
                upsertDirectoryKey(
                    key = "phone_$phone8",
                    uid = uid,
                    displayName = displayName,
                    username = username,
                    avatarUrl = avatarUrl,
                    sinpeBank = resolvedBank,
                    sinpeVisibility = cleanVisibility,
                    phone = formattedPhone,
                    entryType = "phone"
                )
            }

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "sinpe_profiles/$uid")
            Result.failure(e)
        }
    }

    suspend fun authorizePeerForSinpe(peerUid: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sin sesión"))
        if (peerUid.isBlank() || peerUid == uid) return Result.success(Unit)
        return try {
            val sinpeRef = db.collection("sinpe_profiles").document(uid)
            val snap = sinpeRef.get().await()
            if (snap.exists()) {
                sinpeRef.update(
                    mapOf(
                        "approvedUids" to FieldValue.arrayUnion(peerUid),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                val privSnap = db.collection("users_private").document(uid).get().await()
                val pubSnap = db.collection("users_public").document(uid).get().await()
                val visibility = SinpeVisibility.fromKey(
                    privSnap.getString("sinpeVisibility") ?: pubSnap.getString("sinpeVisibility")
                ).key
                val bank = (
                    privSnap.getString("sinpeBank")?.takeIf { it.isNotBlank() }
                        ?: pubSnap.getString("sinpeBank")?.takeIf { it.isNotBlank() }
                        ?: "BAC Credomatic"
                    ).take(60)
                val phone = normalizeCrPhone(privSnap.getString("phone"))?.let { formatCrPhone(it) }
                val create = mapOf<String, Any?>(
                    "userId" to uid,
                    "visibility" to visibility,
                    "sinpeBank" to bank,
                    "sinpePhone" to phone,
                    "approvedUids" to listOf(peerUid),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                sinpeRef.set(create).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Result.success(Unit)
        }
    }

    suspend fun revokePeerForSinpe(peerUid: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sin sesión"))
        if (peerUid.isBlank() || peerUid == uid) return Result.success(Unit)
        return try {
            val sinpeRef = db.collection("sinpe_profiles").document(uid)
            val snap = sinpeRef.get().await()
            if (snap.exists()) {
                sinpeRef.update(
                    mapOf(
                        "approvedUids" to FieldValue.arrayRemove(peerUid),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Result.success(Unit)
        }
    }

    suspend fun toggleSavedPost(postId: String, save: Boolean): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sin sesión"))
        if (postId.isBlank()) return Result.success(Unit)
        val privRef = db.collection("users_private").document(uid)
        return try {
            db.runTransaction { tx ->
                val snap = tx.get(privRef)
                if (!snap.exists()) return@runTransaction null
                val existing = (snap.get("savedPostIds") as? List<*>)
                    ?.mapNotNull { it as? String }
                    ?.filter { it.isNotBlank() }
                    .orEmpty()
                val updated = if (save) {
                    (listOf(postId) + existing.filterNot { it == postId }).take(500)
                } else {
                    existing.filterNot { it == postId }
                }
                tx.update(
                    privRef,
                    mapOf(
                        "savedPostIds" to updated,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                null
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users_private/$uid")
            Result.failure(e)
        }
    }

    suspend fun linkPasswordCredential(
        password: String,
        contactEmail: String? = null,
        existingAuthEmail: String? = null
    ): Result<String> {
        return try {
            val user = auth.currentUser
                ?: return Result.failure(IllegalStateException("Debes iniciar sesión primero para definir tu contraseña."))
            require(password.length >= 8) { "La contraseña debe tener al menos 8 caracteres." }

            val resolvedAuthEmail = (
                existingAuthEmail?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
                    ?: user.email?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
                    ?: contactEmail?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
                    ?: "u_${user.uid.lowercase().take(24)}@vyb.cr"
                ).take(160)

            val hasEmailProvider = user.providerData.any { it.providerId == EmailAuthProvider.PROVIDER_ID }
            if (hasEmailProvider) {
                user.updatePassword(password).await()
            } else {
                val credential = EmailAuthProvider.getCredential(resolvedAuthEmail, password)
                user.linkWithCredential(credential).await()
            }
            Result.success(resolvedAuthEmail)
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseAuthException) {
            val friendly = when (e.errorCode) {
                "ERROR_OPERATION_NOT_ALLOWED" ->
                    "Por ahora no pudimos guardar tu contraseña. Intenta de nuevo en unos momentos."
                "ERROR_REQUIRES_RECENT_LOGIN" ->
                    "Por seguridad, cierra sesión e inicia nuevamente con Google o SMS para actualizar tu contraseña."
                "ERROR_WEAK_PASSWORD" ->
                    "Elige una contraseña más segura de al menos 8 caracteres."
                "ERROR_PROVIDER_ALREADY_LINKED" -> {
                    return try {
                        auth.currentUser?.updatePassword(password)?.await()
                        val fallbackEmail = existingAuthEmail ?: auth.currentUser?.email ?: "u_${auth.currentUser?.uid?.lowercase()?.take(24)}@vyb.cr"
                        Result.success(fallbackEmail)
                    } catch (inner: Exception) {
                        Result.failure(IllegalStateException(inner.localizedMessage ?: "No se pudo actualizar la contraseña."))
                    }
                }
                else -> e.localizedMessage ?: "No se pudo configurar la contraseña."
            }
            Result.failure(IllegalStateException(friendly))
        } catch (e: Exception) {
            Result.failure(IllegalStateException(e.localizedMessage ?: "No se pudo configurar la contraseña."))
        }
    }

    suspend fun updatePasswordAndSyncAliases(
        newPassword: String,
        username: String,
        phone: String?,
        contactEmail: String?,
        existingAuthEmail: String?
    ): Result<String> {
        val linkResult = linkPasswordCredential(
            password = newPassword,
            contactEmail = contactEmail,
            existingAuthEmail = existingAuthEmail
        )
        val authEmail = linkResult.getOrElse { return Result.failure(it) }
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sin sesión"))
        return try {
            val privRef = db.collection("users_private").document(uid)
            val snap = privRef.get().await()
            if (snap.exists()) {
                privRef.update(
                    mapOf(
                        "hasPasswordAuth" to true,
                        "authEmail" to authEmail,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            syncAuthAliases(
                uid = uid,
                authEmail = authEmail,
                username = normalizeUsername(username),
                oldUsername = null,
                phone8 = normalizeCrPhone(phone),
                oldPhone8 = null,
                contactEmail = contactEmail?.trim()?.lowercase()?.takeIf { it.isNotBlank() },
                oldContactEmail = null
            )
            Result.success(authEmail)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "auth_aliases")
            Result.failure(e)
        }
    }

    suspend fun resolveAuthEmailForIdentifier(rawIdentifier: String): Result<String?> {
        val trimmed = rawIdentifier.trim()
        if (trimmed.isBlank()) return Result.success(null)
        return try {
            val isEmail = !trimmed.startsWith("@") && trimmed.contains("@") && trimmed.contains(".")
            val phone8 = if (!trimmed.startsWith("@") && !trimmed.any { it.isLetter() }) {
                normalizeCrPhone(trimmed)
            } else {
                null
            }
            val rawAliasKey = when {
                phone8 != null -> "phone:$phone8"
                isEmail -> "email:${trimmed.lowercase()}"
                else -> {
                    val userClean = normalizeUsername(trimmed)
                    if (userClean.length < 3) return Result.success(null)
                    "username:$userClean"
                }
            }
            val hash = sha256Hex(rawAliasKey)
            val snap = db.collection("auth_aliases").document(hash).get().await()
            val mapped = snap.getString("authEmail")?.trim()?.takeIf { it.isNotBlank() }
            if (mapped != null) {
                Result.success(mapped)
            } else if (isEmail) {
                Result.success(trimmed.lowercase())
            } else {
                Result.success(null)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "auth_aliases")
            Result.failure(e)
        }
    }

    private suspend fun syncAuthAliases(
        uid: String,
        authEmail: String,
        username: String,
        oldUsername: String?,
        phone8: String?,
        oldPhone8: String?,
        contactEmail: String?,
        oldContactEmail: String?
    ) {
        if (!oldUsername.isNullOrBlank() && oldUsername != username) {
            try {
                db.collection("auth_aliases").document(sha256Hex("username:$oldUsername")).delete().await()
            } catch (_: Exception) {}
        }
        if (username.length >= 3) {
            upsertAuthAlias(sha256Hex("username:$username"), uid, authEmail, "username")
        }

        if (!oldPhone8.isNullOrBlank() && oldPhone8 != phone8) {
            try {
                db.collection("auth_aliases").document(sha256Hex("phone:$oldPhone8")).delete().await()
            } catch (_: Exception) {}
        }
        if (!phone8.isNullOrBlank()) {
            upsertAuthAlias(sha256Hex("phone:$phone8"), uid, authEmail, "phone")
        }

        if (!oldContactEmail.isNullOrBlank() && oldContactEmail != contactEmail) {
            try {
                db.collection("auth_aliases").document(sha256Hex("email:$oldContactEmail")).delete().await()
            } catch (_: Exception) {}
        }
        val cleanContact = contactEmail?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
        if (!cleanContact.isNullOrBlank()) {
            upsertAuthAlias(sha256Hex("email:$cleanContact"), uid, authEmail, "email")
        }
        if (!authEmail.equals(cleanContact, ignoreCase = true) && !authEmail.endsWith("@vyb.cr")) {
            upsertAuthAlias(sha256Hex("email:${authEmail.lowercase()}"), uid, authEmail, "email")
        }
    }

    private suspend fun upsertAuthAlias(
        aliasHash: String,
        uid: String,
        authEmail: String,
        entryType: String
    ) {
        val docRef = db.collection("auth_aliases").document(aliasHash)
        val snap = docRef.get().await()
        if (snap.exists() && snap.getString("uid") == uid) {
            docRef.update(
                mapOf(
                    "authEmail" to authEmail,
                    "entryType" to entryType,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } else if (!snap.exists()) {
            docRef.set(
                mapOf(
                    "uid" to uid,
                    "authEmail" to authEmail,
                    "entryType" to entryType,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
    }

    private suspend fun upsertDirectoryKey(
        key: String,
        uid: String,
        displayName: String,
        username: String,
        avatarUrl: String?,
        sinpeBank: String,
        sinpeVisibility: String,
        phone: String?,
        entryType: String
    ) {
        val exposePublic = sinpeVisibility == SinpeVisibility.EVERYONE.key
        val docRef = db.collection("directory_lookup").document(key)
        val snap = docRef.get().await()
        if (snap.exists() && snap.getString("uid") == uid) {
            val updates = mutableMapOf<String, Any>(
                "displayName" to displayName,
                "username" to username,
                "sinpeVisibility" to sinpeVisibility,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            avatarUrl?.takeIf { it.isNotBlank() }?.let { updates["avatarUrl"] = it }
            if (exposePublic) {
                updates["sinpeBank"] = sinpeBank
                if (!phone.isNullOrBlank()) {
                    updates["phone"] = phone
                } else if (snap.contains("phone")) {
                    updates["phone"] = FieldValue.delete()
                }
            } else {
                if (snap.contains("sinpeBank")) updates["sinpeBank"] = FieldValue.delete()
                if (snap.contains("phone")) updates["phone"] = FieldValue.delete()
            }
            docRef.update(updates).await()
        } else if (!snap.exists()) {
            val create = mapOf<String, Any?>(
                "uid" to uid,
                "displayName" to displayName,
                "username" to username,
                "avatarUrl" to avatarUrl?.takeIf { it.isNotBlank() },
                "sinpeBank" to if (exposePublic) sinpeBank else null,
                "sinpeVisibility" to sinpeVisibility,
                "phone" to if (exposePublic) phone else null,
                "entryType" to entryType,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            docRef.set(create).await()
        }
    }

    suspend fun updateQuickStatus(status: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sin sesión"))
        return try {
            db.collection("users_public").document(uid)
                .update(mapOf("quickStatus" to status, "updatedAt" to FieldValue.serverTimestamp()))
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users_public/$uid")
            Result.failure(e)
        }
    }

    suspend fun updatePrivacySettings(
        readReceipts: Boolean,
        showOnlineStatus: Boolean,
        soundEnabled: Boolean,
        callAlertsEnabled: Boolean,
        sinpeAlertsEnabled: Boolean
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sin sesión"))
        return try {
            db.collection("users_private").document(uid).update(
                mapOf(
                    "readReceipts" to readReceipts,
                    "showOnlineStatus" to showOnlineStatus,
                    "soundEnabled" to soundEnabled,
                    "callAlertsEnabled" to callAlertsEnabled,
                    "sinpeAlertsEnabled" to sinpeAlertsEnabled,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users_private/$uid")
            Result.failure(e)
        }
    }

    suspend fun saveFcmToken(token: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sin sesión"))
        val clean = token.trim().take(512)
        if (clean.isBlank()) return Result.success(Unit)
        return try {
            val docRef = db.collection("users_private").document(uid)
            val snap = docRef.get().await()
            if (snap.exists() && snap.getString("fcmToken") != clean) {
                docRef.update(
                    mapOf(
                        "fcmToken" to clean,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users_private/$uid")
            Result.failure(e)
        }
    }

    suspend fun updateLiveLocationPrivacy(
        scope: LiveLocationScope,
        durationMin: Int,
        showDistance: Boolean
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sin sesión"))
        val safeDuration = durationMin.coerceIn(5, 480).toLong()
        return try {
            db.collection("users_private").document(uid).update(
                mapOf(
                    "liveLocationScope" to scope.key,
                    "liveLocationDurationMin" to safeDuration,
                    "liveLocationShowDistance" to showDistance,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users_private/$uid")
            Result.failure(e)
        }
    }

    suspend fun toggleBlockUser(targetUid: String, block: Boolean): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sin sesión"))
        if (targetUid.isBlank() || targetUid == uid) {
            return Result.failure(IllegalArgumentException("Usuario inválido."))
        }
        return try {
            val op = if (block) FieldValue.arrayUnion(targetUid) else FieldValue.arrayRemove(targetUid)
            db.collection("users_private").document(uid).update(
                mapOf("blockedUids" to op, "updatedAt" to FieldValue.serverTimestamp())
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users_private/$uid")
            Result.failure(e)
        }
    }

    suspend fun toggleBlockPhone(rawPhone: String, block: Boolean): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sin sesión"))
        val digits = normalizeBlockedPhoneDigits(rawPhone)
            ?: return Result.failure(IllegalArgumentException("Ingresa un número válido de 8 dígitos (CR) o internacional."))
        val privRef = db.collection("users_private").document(uid)
        return try {
            db.runTransaction { tx ->
                val snap = tx.get(privRef)
                if (!snap.exists()) {
                    throw IllegalStateException("Completa tu perfil antes de gestionar bloqueos.")
                }
                val existing = (snap.get("blockedPhones") as? List<*>)
                    ?.mapNotNull { it as? String }
                    .orEmpty()
                val updated = if (block) {
                    (existing + digits).distinct().take(200)
                } else {
                    existing.filterNot { it == digits }
                }
                tx.update(
                    privRef,
                    mapOf(
                        "blockedPhones" to updated,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                null
            }.await()
            Result.success(formatCrPhone(digits) ?: "+$digits")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users_private/$uid")
            Result.failure(e)
        }
    }

    suspend fun blockContactOrIdentifier(rawQuery: String): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sin sesión"))
        val trimmed = rawQuery.trim()
        if (trimmed.isBlank()) {
            return Result.failure(IllegalArgumentException("Escribe un @usuario o un número de teléfono."))
        }

        val entry = lookupDirectoryExact(trimmed).getOrNull()
        if (entry != null) {
            if (entry.uid == uid) {
                return Result.failure(IllegalArgumentException("No puedes bloquearte a ti mismo."))
            }
            val blockRes = toggleBlockUser(entry.uid, block = true)
            return blockRes.map { "${entry.displayName} (@${entry.username}) bloqueado." }
        }

        val phoneDigits = normalizeBlockedPhoneDigits(trimmed)
        if (phoneDigits != null) {
            val formatted = formatCrPhone(phoneDigits) ?: "+$phoneDigits"
            return toggleBlockPhone(phoneDigits, block = true).map {
                "Número $formatted bloqueado."
            }
        }

        return Result.failure(
            IllegalArgumentException("No encontramos a @$trimmed. Para bloquear un número sin cuenta, ingresa los 8 dígitos.")
        )
    }

    suspend fun lookupDirectoryExact(query: String): Result<DirectoryEntry?> {
        return try {
            requireUserId()
            val trimmed = query.trim()
            if (trimmed.isBlank()) return Result.success(null)

            val phoneDigits = normalizeCrPhone(trimmed)
            val key = if (phoneDigits != null) {
                "phone_$phoneDigits"
            } else {
                val userClean = normalizeUsername(trimmed)
                if (userClean.length < 3) return Result.success(null)
                "user_$userClean"
            }

            val snap = db.collection("directory_lookup").document(key).get().await()
            val entry = snap.toObject(DirectoryEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            Result.success(entry)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "directory_lookup")
            Result.failure(e)
        }
    }

    suspend fun saveContact(entry: DirectoryEntry): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sin sesión"))
        val path = "users/$uid/contacts/${entry.uid}"
        return try {
            authorizePeerForSinpe(entry.uid)
            val peerSinpe = getSinpeProfile(entry.uid)
            val resolvedBank = peerSinpe?.sinpeBank?.takeIf { it.isNotBlank() }
                ?: entry.sinpeBank.takeIf { it.isNotBlank() }
            val resolvedPhone = peerSinpe?.sinpePhone?.takeIf { it.isNotBlank() }
                ?: entry.phone?.takeIf { it.isNotBlank() }
            val safeContactName = entry.displayName.trim().ifBlank { "Contacto VYB" }.take(60)
            val safeContactUsername = entry.username.trim().ifBlank { "vyb_user" }
                .let { if (it.length < 3) it.padEnd(3, '_') else it }
                .take(30)
            val safeContactAvatar = entry.avatarUrl?.takeIf { it.isNotBlank() && it.length <= 200_000 }
            val payload = mapOf<String, Any?>(
                "contactUid" to entry.uid,
                "displayName" to safeContactName,
                "username" to safeContactUsername,
                "avatarUrl" to safeContactAvatar,
                "sinpeBank" to resolvedBank?.trim()?.take(60),
                "sinpePhone" to resolvedPhone?.trim()?.take(20),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            db.collection("users").document(uid).collection("contacts").document(entry.uid)
                .set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun removeContact(contactUid: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sin sesión"))
        val path = "users/$uid/contacts/$contactUid"
        return try {
            db.collection("users").document(uid).collection("contacts").document(contactUid).delete().await()
            revokePeerForSinpe(contactUid)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    companion object {
        fun normalizeUsername(raw: String): String =
            raw.trim().removePrefix("@").lowercase().replace(Regex("[^a-z0-9_]"), "")

        fun normalizeCrPhone(raw: String?): String? {
            if (raw.isNullOrBlank()) return null
            val digits = raw.filter { it.isDigit() }
            val eight = when {
                digits.length == 11 && digits.startsWith("506") -> digits.substring(3)
                digits.length == 8 -> digits
                else -> return null
            }
            return if (eight.length == 8) eight else null
        }

        fun normalizeBlockedPhoneDigits(raw: String?): String? {
            if (raw.isNullOrBlank()) return null
            val cr = normalizeCrPhone(raw)
            if (cr != null) return cr
            val digits = raw.filter { it.isDigit() }
            return if (digits.length in 8..15) digits else null
        }

        fun formatCrPhone(raw: String?): String? {
            if (raw.isNullOrBlank()) return null
            val eight = normalizeCrPhone(raw) ?: return raw.trim().takeIf { it.isNotBlank() }
            return "+506 ${eight.substring(0, 4)}-${eight.substring(4, 8)}"
        }

        fun sha256Hex(raw: String): String {
            val bytes = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
