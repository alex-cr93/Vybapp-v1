package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.MediaUtils
import com.example.core.PreferencesStore
import com.example.data.model.Post
import com.example.data.model.PostComment
import com.example.data.model.PublicUser
import com.example.data.model.SavedContact
import com.example.data.model.Story
import com.example.data.model.UiState
import com.example.data.repository.FeedRepository
import com.example.data.repository.UserRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

private const val TAG = "FeedViewModel"

class FeedViewModel(
    private val feedRepository: FeedRepository,
    private val userRepository: UserRepository,
    private val preferencesStore: PreferencesStore,
    private val currentUserId: String
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedHashtag = MutableStateFlow<String?>(null)
    val selectedHashtag: StateFlow<String?> = _selectedHashtag.asStateFlow()

    private val _uploadProgress = MutableStateFlow<Float?>(null)
    val uploadProgress: StateFlow<Float?> = _uploadProgress.asStateFlow()

    private val _uploadStageLabel = MutableStateFlow<String?>(null)
    val uploadStageLabel: StateFlow<String?> = _uploadStageLabel.asStateFlow()

    private val _publishError = MutableStateFlow<String?>(null)
    val publishError: StateFlow<String?> = _publishError.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _activeCommentPostId = MutableStateFlow<String?>(null)
    private val _feedRefreshTick = MutableStateFlow(0)

    private val _authorProfiles = MutableStateFlow<Map<String, PublicUser>>(emptyMap())
    val authorProfiles: StateFlow<Map<String, PublicUser>> = _authorProfiles.asStateFlow()
    private val authorObserverJobs = mutableMapOf<String, Job>()

    private val contactsState: StateFlow<UiState<List<SavedContact>>> = if (currentUserId.isBlank()) {
        MutableStateFlow(UiState.Success(emptyList()))
    } else {
        userRepository.observeContacts(currentUserId)
            .map<List<SavedContact>, UiState<List<SavedContact>>> { UiState.Success(it) }
            .catch { error ->
                val fsError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsError == null && error is CancellationException) throw error
                Log.w(TAG, "Error observing contacts for feed visibility", fsError ?: error)
                emit(UiState.Success(emptyList()))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)
    }

    val hasAddedContacts: StateFlow<Boolean> = contactsState
        .map { state -> (state as? UiState.Success)?.data?.isNotEmpty() == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)

    private val allowedAuthorIdsInternal: Flow<Set<String>?> = contactsState.map { state ->
        when (state) {
            is UiState.Loading -> null
            is UiState.Error -> setOf(currentUserId).filter { it.isNotBlank() }.toSet()
            is UiState.Success -> buildAllowedAuthorIds(currentUserId, state.data)
        }
    }

    val allowedAuthorIds: StateFlow<Set<String>> = allowedAuthorIdsInternal
        .map { it ?: setOf(currentUserId).filter { uid -> uid.isNotBlank() }.toSet() }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000L),
            setOf(currentUserId).filter { it.isNotBlank() }.toSet()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val storiesState: StateFlow<UiState<List<Story>>> = combine(
        allowedAuthorIdsInternal,
        _feedRefreshTick
    ) { allowedIds, _ -> allowedIds }
        .flatMapLatest { allowedIds ->
            if (allowedIds == null) {
                flowOf<UiState<List<Story>>>(UiState.Loading)
            } else {
                feedRepository.observeActiveStoriesForAuthors(allowedIds.toList(), currentUserId)
                    .map<List<Story>, UiState<List<Story>>> { stories ->
                        UiState.Success(filterVisibleStories(stories, allowedIds))
                    }
                    .catch { error ->
                        val fsError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (fsError == null && error is CancellationException) throw error
                        val root = fsError ?: error
                        Log.w(TAG, "Error observing stories", root)
                        emit(UiState.Error(root.message ?: "No se pudieron cargar las historias."))
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val rawPostsState: StateFlow<UiState<List<Post>>> = combine(
        allowedAuthorIdsInternal,
        _feedRefreshTick
    ) { allowedIds, _ -> allowedIds }
        .flatMapLatest { allowedIds ->
            if (allowedIds == null) {
                flowOf<UiState<List<Post>>>(UiState.Loading)
            } else {
                feedRepository.observePostsForAuthors(allowedIds.toList(), currentUserId)
                    .map<List<Post>, UiState<List<Post>>> { posts ->
                        UiState.Success(filterVisiblePosts(posts, allowedIds))
                    }
                    .catch { error ->
                        val fsError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (fsError == null && error is CancellationException) throw error
                        val root = fsError ?: error
                        Log.w(TAG, "Error observing posts", root)
                        emit(UiState.Error(root.message ?: "No se pudieron cargar las publicaciones."))
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val filteredPostsState: StateFlow<UiState<List<Post>>> = combine(
        rawPostsState,
        _searchQuery,
        _selectedHashtag
    ) { state, query, hashtag ->
        when (state) {
            is UiState.Success -> {
                val q = query.trim().lowercase()
                val tag = hashtag?.trim()?.removePrefix("#")?.lowercase()
                val filtered = state.data.filter { post ->
                    val matchesTag = tag.isNullOrBlank() || post.hashtags.any { it.lowercase() == tag }
                    val matchesQuery = q.isBlank() ||
                        post.text.lowercase().contains(q) ||
                        post.authorName.lowercase().contains(q) ||
                        post.authorUsername.lowercase().contains(q) ||
                        post.hashtags.any { "#$it".contains(q) || it.contains(q) }
                    matchesTag && matchesQuery
                }
                UiState.Success(filtered)
            }
            else -> state
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val bookmarkedPostIds: StateFlow<Set<String>> = preferencesStore.bookmarkedPostIds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptySet())

    private val extraSavedPosts = MutableStateFlow<Map<String, Post>>(emptyMap())

    val myPostsState: StateFlow<UiState<List<Post>>> = feedRepository.observePostsByAuthor(currentUserId)
        .map<List<Post>, UiState<List<Post>>> { UiState.Success(it) }
        .catch { error ->
            val fsError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsError == null && error is CancellationException) throw error
            emit(UiState.Error((fsError ?: error).message ?: "No se pudieron cargar tus publicaciones."))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val savedPostsState: StateFlow<UiState<List<Post>>> = combine(
        rawPostsState,
        myPostsState,
        bookmarkedPostIds,
        extraSavedPosts,
        allowedAuthorIds
    ) { rawState, myState, savedIds, extraMap, allowedIds ->
        val pool = buildMap<String, Post> {
            (rawState as? UiState.Success)?.data?.forEach { put(it.id, it) }
            (myState as? UiState.Success)?.data?.forEach { put(it.id, it) }
            putAll(extraMap)
        }
        val list = savedIds.mapNotNull { pool[it] }
            .let { filterVisiblePosts(it, allowedIds) }
            .sortedByDescending { it.createdAt?.seconds ?: 0L }
        when {
            savedIds.isEmpty() -> UiState.Success(emptyList())
            rawState is UiState.Loading && list.isEmpty() -> UiState.Loading
            else -> UiState.Success(list)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    @OptIn(ExperimentalCoroutinesApi::class)
    val commentsState: StateFlow<List<PostComment>> = _activeCommentPostId
        .flatMapLatest { postId ->
            if (postId.isNullOrBlank()) flowOf(emptyList())
            else feedRepository.observeComments(postId)
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    emit(emptyList())
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    init {
        viewModelScope.launch {
            userRepository.observePrivateProfile(currentUserId)
                .catch { error ->
                    if (error is CancellationException) throw error
                    emit(null)
                }
                .collect { priv ->
                    if (priv != null) {
                        val serverIds = priv.savedPostIds.filter { it.isNotBlank() }.toSet()
                        if (serverIds != bookmarkedPostIds.value) {
                            preferencesStore.setBookmarkedPostIds(serverIds)
                        }
                    }
                }
        }
        viewModelScope.launch {
            combine(bookmarkedPostIds, rawPostsState) { savedIds, rawState ->
                val loadedIds = (rawState as? UiState.Success)?.data?.mapTo(HashSet()) { it.id } ?: emptySet()
                savedIds.filter { it !in loadedIds && it !in extraSavedPosts.value }
            }.collect { missingIds ->
                if (missingIds.isNotEmpty()) {
                    val allowedNow = allowedAuthorIds.value
                    val fetched = filterVisiblePosts(feedRepository.getPostsByIds(missingIds), allowedNow)
                    if (fetched.isNotEmpty()) {
                        extraSavedPosts.update { curr -> curr + fetched.associateBy { it.id } }
                    }
                }
            }
        }
        viewModelScope.launch {
            rawPostsState.collect { state ->
                if (state is UiState.Success) {
                    val authorIds = state.data
                        .map { it.authorId }
                        .filter { it.isNotBlank() }
                        .toSet()
                    authorIds.forEach { ensureObservingAuthor(it) }
                }
            }
        }
    }

    fun ensureObservingAuthor(uid: String) {
        if (uid.isBlank() || authorObserverJobs[uid]?.isActive == true) return
        val job = viewModelScope.launch {
            try {
                userRepository.observePublicProfile(uid)
                    .catch { error ->
                        if (error is CancellationException) throw error
                        emit(null)
                    }
                    .collect { profile ->
                        if (profile != null) {
                            _authorProfiles.update { it + (uid to profile) }
                        }
                    }
            } finally {
                authorObserverJobs.remove(uid)
            }
        }
        authorObserverJobs[uid] = job
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observePostsForAuthor(authorId: String): Flow<List<Post>> =
        allowedAuthorIdsInternal.flatMapLatest { allowedIds ->
            when {
                allowedIds == null -> flowOf(emptyList())
                authorId !in allowedIds -> flowOf(emptyList())
                else -> feedRepository.observePostsByAuthor(authorId)
                    .map { posts -> filterVisiblePosts(posts, allowedIds) }
                    .catch { error ->
                        if (error is CancellationException) throw error
                        val cached = (rawPostsState.value as? UiState.Success)?.data
                            ?.filter { it.authorId == authorId && it.authorId in allowedIds }
                            .orEmpty()
                        emit(cached)
                    }
            }
        }

    override fun onCleared() {
        super.onCleared()
        authorObserverJobs.values.forEach { it.cancel() }
        authorObserverJobs.clear()
    }

    fun setSearchQuery(q: String) {
        _searchQuery.value = q
    }

    fun selectHashtag(tag: String?) {
        _selectedHashtag.value = if (_selectedHashtag.value == tag) null else tag
    }

    fun openComments(postId: String?) {
        _activeCommentPostId.value = postId
    }

    fun clearError() {
        _actionError.value = null
    }

    fun clearPublishError() {
        _publishError.value = null
    }

    fun refreshFeed() {
        _feedRefreshTick.update { it + 1 }
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    fun publishPost(
        context: Context,
        author: PublicUser,
        text: String,
        tagsRaw: String,
        mediaUri: Uri?,
        reportToSnackbar: Boolean = false,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _publishError.value = null
            val cleanText = text.trim()
            if (cleanText.isBlank() && mediaUri == null) {
                val msg = "Escribe algo o adjunta una foto o video para publicar."
                if (reportToSnackbar) _actionError.value = msg else _publishError.value = msg
                return@launch
            }
            _uploadProgress.value = 0.04f
            val mediaPayload = if (mediaUri != null) {
                _uploadStageLabel.value = "Procesando foto o video..."
                val prep = MediaUtils.prepareMediaPayload(context, mediaUri) { prepProgress ->
                    _uploadProgress.value = (0.04f + prepProgress * 0.46f).coerceIn(0.04f, 0.50f)
                }
                if (prep.isFailure) {
                    _uploadProgress.value = null
                    _uploadStageLabel.value = null
                    val err = prep.exceptionOrNull()?.localizedMessage ?: "No se pudo procesar el archivo."
                    if (reportToSnackbar) _actionError.value = err else _publishError.value = err
                    return@launch
                }
                prep.getOrNull()
            } else null

            _uploadStageLabel.value = if (mediaPayload != null) "Subiendo publicación..." else "Publicando..."
            _uploadProgress.value = 0.52f
            val tags = tagsRaw.split(",").map { it.trim() }.filter { it.isNotBlank() }
            val result = feedRepository.createPost(
                author = author,
                text = cleanText.ifBlank { "Compartiendo un momento" },
                explicitTags = tags,
                media = mediaPayload,
                onProgress = { uploadFraction ->
                    _uploadProgress.value = (0.52f + uploadFraction * 0.46f).coerceIn(0.52f, 0.99f)
                }
            )
            _uploadProgress.value = null
            _uploadStageLabel.value = null
            if (result.isSuccess) {
                _publishError.value = null
                _actionMessage.value = "Publicación compartida."
                onSuccess()
            } else {
                val err = result.exceptionOrNull()?.localizedMessage ?: "Error al publicar."
                if (reportToSnackbar) _actionError.value = err else _publishError.value = err
            }
        }
    }

    fun publishStory(
        context: Context,
        author: PublicUser,
        mediaUri: Uri,
        caption: String,
        locationLabel: String,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _publishError.value = null
            _uploadProgress.value = 0.04f
            _uploadStageLabel.value = "Procesando historia..."
            val prep = MediaUtils.prepareMediaPayload(context, mediaUri) { prepProgress ->
                _uploadProgress.value = (0.04f + prepProgress * 0.46f).coerceIn(0.04f, 0.50f)
            }
            if (prep.isFailure) {
                _uploadProgress.value = null
                _uploadStageLabel.value = null
                _publishError.value = prep.exceptionOrNull()?.localizedMessage ?: "No se pudo procesar el archivo."
                return@launch
            }
            val payload = prep.getOrThrow()
            _uploadStageLabel.value = "Subiendo historia..."
            val result = feedRepository.createStory(
                author = author,
                media = payload,
                caption = caption,
                locationLabel = locationLabel,
                onProgress = { p -> _uploadProgress.value = (0.52f + p * 0.46f).coerceIn(0.52f, 0.99f) }
            )
            _uploadProgress.value = null
            _uploadStageLabel.value = null
            if (result.isSuccess) {
                _publishError.value = null
                _actionMessage.value = "Historia publicada."
                onSuccess()
            } else {
                _publishError.value = result.exceptionOrNull()?.localizedMessage ?: "Error al subir historia."
            }
        }
    }

    fun markStoryViewed(story: Story) {
        if (currentUserId in story.viewerUids) return
        viewModelScope.launch {
            feedRepository.markStoryViewed(story.id)
        }
    }

    fun deleteStory(story: Story, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            val res = feedRepository.deleteStory(story.id)
            if (res.isSuccess) {
                _actionMessage.value = "Estado eliminado."
                onSuccess()
            } else {
                _actionError.value = res.exceptionOrNull()?.localizedMessage ?: "No se pudo eliminar el estado."
            }
        }
    }

    fun toggleLike(post: Post) {
        viewModelScope.launch {
            val res = feedRepository.toggleLike(post)
            if (res.isFailure) {
                _actionError.value = res.exceptionOrNull()?.localizedMessage ?: "Error al actualizar Me gusta."
            }
        }
    }

    fun sharePost(postId: String) {
        viewModelScope.launch {
            feedRepository.incrementShare(postId)
        }
    }

    fun toggleBookmark(postId: String) {
        viewModelScope.launch {
            val willSave = postId !in bookmarkedPostIds.value
            preferencesStore.toggleBookmark(postId)
            userRepository.toggleSavedPost(postId, save = willSave)
        }
    }

    fun addComment(postId: String, author: PublicUser, text: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            val res = feedRepository.addComment(postId, author, text)
            if (res.isSuccess) {
                onSuccess()
            } else {
                _actionError.value = res.exceptionOrNull()?.localizedMessage ?: "Error al comentar."
            }
        }
    }

    fun deleteComment(postId: String, comment: PostComment, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            val res = feedRepository.deleteComment(postId, comment.id)
            if (res.isSuccess) {
                _actionMessage.value = "Comentario eliminado."
                onSuccess()
            } else {
                _actionError.value = res.exceptionOrNull()?.localizedMessage ?: "No se pudo eliminar el comentario."
            }
        }
    }

    fun deletePost(post: Post, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            val res = feedRepository.deletePost(post)
            if (res.isSuccess) {
                _actionMessage.value = "Publicación eliminada."
                onSuccess()
            } else {
                _actionError.value = res.exceptionOrNull()?.localizedMessage ?: "No se pudo eliminar la publicación."
            }
        }
    }

    suspend fun loadFullMediaFile(context: Context, mediaId: String): File? {
        val result = feedRepository.downloadChunkedMedia(mediaId)
        val (mime, bytes) = result.getOrNull() ?: return null
        return MediaUtils.writeTempMediaFile(context, mediaId, mime, bytes)
    }

    companion object {
        fun buildAllowedAuthorIds(currentUserId: String, contacts: List<SavedContact>): Set<String> =
            buildSet {
                if (currentUserId.isNotBlank()) add(currentUserId)
                contacts.forEach { contact ->
                    if (contact.contactUid.isNotBlank()) add(contact.contactUid)
                }
            }

        fun filterVisiblePosts(posts: List<Post>, allowedAuthorIds: Set<String>): List<Post> =
            posts.filter { it.authorId.isNotBlank() && it.authorId in allowedAuthorIds }

        fun filterVisibleStories(stories: List<Story>, allowedAuthorIds: Set<String>): List<Story> =
            stories.filter { it.authorId.isNotBlank() && it.authorId in allowedAuthorIds }
    }
}
