package com.example.data.repository

import android.content.Context
import android.util.Base64
import com.example.R
import com.example.core.EncodedMediaPayload
import com.example.core.MediaUtils
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.data.model.Post
import com.example.data.model.PostComment
import com.example.data.model.PublicUser
import com.example.data.model.Story
import com.google.firebase.Timestamp
import com.google.firebase.Firebase
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.util.Date
import java.util.UUID

class FeedRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("Debes iniciar sesión.")

    fun observeActiveStoriesForAuthors(
        authorIds: List<String>,
        currentUserId: String = auth.currentUser?.uid.orEmpty()
    ): Flow<List<Story>> {
        val activeUid = auth.currentUser?.uid
        if (activeUid.isNullOrBlank()) return flowOf(emptyList())
        val effectiveUid = currentUserId.ifBlank { activeUid }
        val cleanIds = authorIds.filter { it.isNotBlank() }.distinct()
        if (cleanIds.isEmpty()) return flowOf(emptyList())
        val authorFlows = cleanIds.map { authorId ->
            val baseFlow = db.collection("stories")
                .whereEqualTo("authorId", authorId)
                .limit(60)
                .snapshots()
                .map { snapshot ->
                    val now = Timestamp.now()
                    snapshot.toObjects(Story::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .filter { story -> story.active && (story.expiresAt == null || story.expiresAt > now) }
                }
            if (authorId == effectiveUid) {
                baseFlow
            } else {
                baseFlow.catch { err ->
                    val fsError = generateSequence(err) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsError == null && err is CancellationException) throw err
                    emit(emptyList())
                }
            }
        }
        return combine(authorFlows) { batches ->
            batches.flatMap { it }
                .distinctBy { it.id }
                .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
        }
    }

    fun observePostsForAuthors(
        authorIds: List<String>,
        currentUserId: String = auth.currentUser?.uid.orEmpty()
    ): Flow<List<Post>> {
        val activeUid = auth.currentUser?.uid
        if (activeUid.isNullOrBlank()) return flowOf(emptyList())
        val effectiveUid = currentUserId.ifBlank { activeUid }
        val cleanIds = authorIds.filter { it.isNotBlank() }.distinct()
        if (cleanIds.isEmpty()) return flowOf(emptyList())
        val authorFlows = cleanIds.map { authorId ->
            val baseFlow = db.collection("posts")
                .whereEqualTo("authorId", authorId)
                .limit(30)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(Post::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
            if (authorId == effectiveUid) {
                baseFlow
            } else {
                baseFlow.catch { err ->
                    val fsError = generateSequence(err) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsError == null && err is CancellationException) throw err
                    emit(emptyList())
                }
            }
        }
        return combine(authorFlows) { batches ->
            batches.flatMap { it }
                .distinctBy { it.id }
                .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
        }
    }

    fun observePostsByAuthor(authorId: String): Flow<List<Post>> {
        if (authorId.isBlank() || auth.currentUser?.uid.isNullOrBlank()) return flowOf(emptyList())
        return db.collection("posts")
            .whereEqualTo("authorId", authorId)
            .limit(50)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(Post::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
    }

    suspend fun getPostsByIds(postIds: List<String>): List<Post> {
        if (auth.currentUser?.uid.isNullOrBlank()) return emptyList()
        val cleanIds = postIds.filter { it.isNotBlank() }.distinct().take(50)
        if (cleanIds.isEmpty()) return emptyList()
        return cleanIds.mapNotNull { postId ->
            runCatching {
                val snap = db.collection("posts").document(postId).get().await()
                if (snap.exists()) {
                    snap.toObject(Post::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else {
                    null
                }
            }.getOrNull()
        }
    }

    fun observeComments(postId: String): Flow<List<PostComment>> =
        db.collection("posts").document(postId).collection("comments")
            .whereEqualTo("postId", postId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(PostComment::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.createdAt ?: Timestamp(0, 0) }
            }

    suspend fun uploadChunkedMedia(
        payload: EncodedMediaPayload,
        scope: String,
        allowedUids: List<String>,
        onProgress: (Float) -> Unit = {}
    ): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sin sesión"))
        val mediaId = "med_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        return try {
            val bytes = payload.fullBytes
            val chunkSize = MediaUtils.CHUNK_SIZE_BYTES
            val totalChunks = ((bytes.size + chunkSize - 1) / chunkSize).coerceAtLeast(1)

            val meta = mapOf(
                "ownerId" to uid,
                "mimeType" to payload.mimeType.take(80).ifBlank { "application/octet-stream" },
                "totalChunks" to totalChunks,
                "totalBytes" to bytes.size,
                "scope" to scope,
                "allowedUids" to allowedUids.take(50),
                "createdAt" to FieldValue.serverTimestamp()
            )
            db.collection("vybmedia_files").document(mediaId).set(meta).await()

            for (i in 0 until totalChunks) {
                val start = i * chunkSize
                val end = minOf(start + chunkSize, bytes.size)
                val slice = bytes.copyOfRange(start, end)
                val b64 = Base64.encodeToString(slice, Base64.NO_WRAP)
                val chunkDoc = mapOf(
                    "ownerId" to uid,
                    "scope" to scope,
                    "allowedUids" to allowedUids.take(50),
                    "chunkIndex" to i,
                    "dataBase64" to b64,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                db.collection("vybmedia_files").document(mediaId)
                    .collection("chunks").document("chunk_$i").set(chunkDoc).await()
                onProgress((i + 1).toFloat() / totalChunks.toFloat())
            }
            Result.success(mediaId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "vybmedia_files/$mediaId")
            Result.failure(e)
        }
    }

    suspend fun downloadChunkedMedia(mediaId: String): Result<Pair<String, ByteArray>> {
        return try {
            val metaSnap = db.collection("vybmedia_files").document(mediaId).get().await()
            if (!metaSnap.exists()) {
                return Result.failure(IllegalStateException("Archivo multimedia no encontrado."))
            }
            val mimeType = metaSnap.getString("mimeType") ?: "image/jpeg"
            val totalChunks = (metaSnap.getLong("totalChunks") ?: 0L).toInt()
            val out = ByteArrayOutputStream()
            for (i in 0 until totalChunks) {
                val chunkSnap = db.collection("vybmedia_files").document(mediaId)
                    .collection("chunks").document("chunk_$i").get().await()
                val b64 = chunkSnap.getString("dataBase64") ?: continue
                out.write(Base64.decode(b64, Base64.DEFAULT))
            }
            Result.success(mimeType to out.toByteArray())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "vybmedia_files/$mediaId")
            Result.failure(e)
        }
    }

    suspend fun createStory(
        author: PublicUser,
        media: EncodedMediaPayload,
        caption: String?,
        locationLabel: String?,
        onProgress: (Float) -> Unit
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sin sesión"))
        return try {
            val mediaId = uploadChunkedMedia(media, scope = "public", allowedUids = listOf(uid), onProgress = onProgress)
                .getOrThrow()
            val expiresAt = Timestamp(Date(System.currentTimeMillis() + 24L * 60L * 60L * 1000L))
            val safeAuthorName = author.displayName.trim().ifBlank { "Usuario VYB" }.take(60)
            val safeAuthorUsername = author.username.trim().ifBlank { "vyb_user" }
                .let { if (it.length < 3) it.padEnd(3, '_') else it }
                .take(30)
            val safeAvatar = author.avatarUrl?.takeIf { it.isNotBlank() && it.length <= 200_000 }
            val storyData = mapOf<String, Any?>(
                "authorId" to uid,
                "authorName" to safeAuthorName,
                "authorUsername" to safeAuthorUsername,
                "authorAvatarUrl" to safeAvatar,
                "authorVerified" to author.verified,
                "mediaType" to if (media.isVideo) "video" else "image",
                "mediaId" to mediaId,
                "mediaPreviewData" to media.previewDataUri,
                "caption" to caption?.trim()?.take(300)?.takeIf { it.isNotBlank() },
                "locationLabel" to locationLabel?.trim()?.take(100)?.takeIf { it.isNotBlank() },
                "viewerUids" to emptyList<String>(),
                "active" to true,
                "expiresAt" to expiresAt,
                "createdAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            db.collection("stories").document().set(storyData).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "stories")
            Result.failure(e)
        }
    }

    suspend fun markStoryViewed(storyId: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sin sesión"))
        return try {
            db.collection("stories").document(storyId)
                .update(mapOf("viewerUids" to FieldValue.arrayUnion(uid)))
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "stories/$storyId")
            Result.failure(e)
        }
    }

    suspend fun deleteStory(storyId: String): Result<Unit> {
        auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sin sesión"))
        return try {
            require(storyId.isNotBlank()) { "El identificador del estado no es válido." }
            db.collection("stories").document(storyId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "stories/$storyId")
            Result.failure(e)
        }
    }

    suspend fun createPost(
        author: PublicUser,
        text: String,
        explicitTags: List<String>,
        media: EncodedMediaPayload?,
        onProgress: (Float) -> Unit = {}
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sin sesión"))
        return try {
            val inlineTags = Regex("#([\\p{L}0-9_]+)").findAll(text).map { it.groupValues[1].lowercase() }.toList()
            val mergedTags = (explicitTags.map { it.trim().removePrefix("#").lowercase() } + inlineTags)
                .filter { it.isNotBlank() }
                .distinct()
                .take(15)

            var mediaId: String? = null
            if (media != null) {
                mediaId = uploadChunkedMedia(
                    payload = media,
                    scope = "public",
                    allowedUids = listOf(uid),
                    onProgress = onProgress
                ).getOrThrow()
            }

            val safeAuthorName = author.displayName.trim().ifBlank { "Usuario VYB" }.take(60)
            val safeAuthorUsername = author.username.trim().ifBlank { "vyb_user" }
                .let { if (it.length < 3) it.padEnd(3, '_') else it }
                .take(30)
            val safeAvatar = author.avatarUrl?.takeIf { it.isNotBlank() && it.length <= 200_000 }
            val postData = mapOf<String, Any?>(
                "authorId" to uid,
                "authorName" to safeAuthorName,
                "authorUsername" to safeAuthorUsername,
                "authorAvatarUrl" to safeAvatar,
                "authorVerified" to author.verified,
                "text" to text.trim().take(2000),
                "hashtags" to mergedTags,
                "mediaType" to when {
                    media == null -> "none"
                    media.isVideo -> "video"
                    else -> "image"
                },
                "mediaId" to mediaId,
                "mediaPreviewData" to media?.previewDataUri?.takeIf { it.isNotBlank() && it.length <= 300_000 },
                "mediaAspectRatio" to media?.aspectRatio?.coerceIn(0.25, 4.5),
                "likedBy" to emptyList<String>(),
                "commentsCount" to 0L,
                "shareCount" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            db.collection("posts").document().set(postData).await()
            runCatching {
                db.collection("users_public").document(uid).update(
                    mapOf(
                        "postsCount" to FieldValue.increment(1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "posts")
            Result.failure(e)
        }
    }

    suspend fun toggleLike(post: Post): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sin sesión"))
        return try {
            val alreadyLiked = uid in post.likedBy
            val op = if (alreadyLiked) FieldValue.arrayRemove(uid) else FieldValue.arrayUnion(uid)
            db.collection("posts").document(post.id).update(
                mapOf(
                    "likedBy" to op,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "posts/${post.id}")
            Result.failure(e)
        }
    }

    suspend fun incrementShare(postId: String): Result<Unit> {
        return try {
            requireUserId()
            db.collection("posts").document(postId).update(
                mapOf(
                    "shareCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "posts/$postId")
            Result.failure(e)
        }
    }

    suspend fun addComment(postId: String, author: PublicUser, text: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sin sesión"))
        return try {
            val cleanText = text.trim().take(500)
            require(cleanText.isNotBlank()) { "El comentario no puede estar vacío." }
            val safeAuthorName = author.displayName.trim().ifBlank { "Usuario VYB" }.take(60)
            val safeAuthorUsername = author.username.trim().ifBlank { "vyb_user" }
                .let { if (it.length < 3) it.padEnd(3, '_') else it }
                .take(30)
            val safeAvatar = author.avatarUrl?.takeIf { it.isNotBlank() && it.length <= 200_000 }
            val commentData = mapOf<String, Any?>(
                "postId" to postId,
                "authorId" to uid,
                "authorName" to safeAuthorName,
                "authorUsername" to safeAuthorUsername,
                "authorAvatarUrl" to safeAvatar,
                "text" to cleanText,
                "createdAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            db.collection("posts").document(postId).collection("comments").document().set(commentData).await()
            db.collection("posts").document(postId).update(
                mapOf(
                    "commentsCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "posts/$postId/comments")
            Result.failure(e)
        }
    }

    suspend fun deleteComment(postId: String, commentId: String): Result<Unit> {
        requireUserId()
        return try {
            db.collection("posts").document(postId).collection("comments").document(commentId).delete().await()
            val postRef = db.collection("posts").document(postId)
            db.runTransaction { tx ->
                val postSnap = tx.get(postRef)
                if (postSnap.exists()) {
                    val count = postSnap.getLong("commentsCount") ?: 0L
                    if (count > 0L) {
                        tx.update(
                            postRef,
                            mapOf(
                                "commentsCount" to count - 1L,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                }
                null
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "posts/$postId/comments/$commentId")
            Result.failure(e)
        }
    }

    suspend fun deletePost(post: Post): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sin sesión"))
        if (post.authorId != uid) {
            return Result.failure(IllegalStateException("Solo el autor puede eliminar esta publicación."))
        }
        return try {
            // Delete child comments (allowed for parent post author by rules)
            runCatching {
                val commentSnaps = db.collection("posts").document(post.id)
                    .collection("comments")
                    .whereEqualTo("postId", post.id)
                    .get()
                    .await()
                commentSnaps.documents.forEach { doc ->
                    doc.reference.delete().await()
                }
            }

            // Delete owned chunked media if attached
            post.mediaId?.takeIf { it.isNotBlank() }?.let { medId ->
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

            db.collection("posts").document(post.id).delete().await()

            val publicRef = db.collection("users_public").document(uid)
            runCatching {
                db.runTransaction { tx ->
                    val snap = tx.get(publicRef)
                    if (snap.exists()) {
                        val current = snap.getLong("postsCount") ?: 0L
                        if (current > 0L) {
                            tx.update(
                                publicRef,
                                mapOf(
                                    "postsCount" to current - 1L,
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            )
                        }
                    }
                    null
                }.await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "posts/${post.id}")
            Result.failure(e)
        }
    }
}
