package com.example.ui.viewmodel

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.LocationHelper
import com.example.core.MediaUtils
import com.example.data.model.Chat
import com.example.data.model.ChatMessage
import com.example.data.model.ChatRequest
import com.example.data.model.DirectoryEntry
import com.example.data.model.LiveLocationShare
import com.example.data.model.PrivateUser
import com.example.data.model.PublicUser
import com.example.data.model.SavedContact
import com.example.data.model.SinpeProfile
import com.example.data.model.StructuredChatSummary
import com.example.data.model.UiState
import com.example.data.model.VybConstants
import com.example.data.repository.ChatRepository
import com.example.data.repository.UserRepository
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "ChatViewModel"

enum class ChatFilter(val label: String) {
    ALL("Todos"),
    DIRECT("Directos"),
    GROUP("Grupos"),
    REQUESTS("Solicitudes")
}

class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _chatSearch = MutableStateFlow("")
    val chatSearch: StateFlow<String> = _chatSearch.asStateFlow()

    private val _chatFilter = MutableStateFlow(ChatFilter.ALL)
    val chatFilter: StateFlow<ChatFilter> = _chatFilter.asStateFlow()

    private val _selectedChatId = MutableStateFlow<String?>(null)
    val selectedChatId: StateFlow<String?> = _selectedChatId.asStateFlow()

    private val _lookupResult = MutableStateFlow<UiState<DirectoryEntry?>?>(null)
    val lookupResult: StateFlow<UiState<DirectoryEntry?>?> = _lookupResult.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _busyRequestIds = MutableStateFlow<Set<String>>(emptySet())
    val busyRequestIds: StateFlow<Set<String>> = _busyRequestIds.asStateFlow()

    private val _isSendingAudio = MutableStateFlow(false)
    val isSendingAudio: StateFlow<Boolean> = _isSendingAudio.asStateFlow()

    private val _sinpeConfettiTrigger = MutableStateFlow(0)
    val sinpeConfettiTrigger: StateFlow<Int> = _sinpeConfettiTrigger.asStateFlow()

    private val _optimisticBuzzDayAndCount = MutableStateFlow<Pair<String, Int>?>(null)
    val optimisticBuzzDayAndCount: StateFlow<Pair<String, Int>?> = _optimisticBuzzDayAndCount.asStateFlow()

    private val _peerProfiles = MutableStateFlow<Map<String, PublicUser>>(emptyMap())
    val peerProfiles: StateFlow<Map<String, PublicUser>> = _peerProfiles.asStateFlow()
    private val peerObserverJobs = mutableMapOf<String, Job>()

    private val _peerSinpeProfiles = MutableStateFlow<Map<String, SinpeProfile?>>(emptyMap())
    val peerSinpeProfiles: StateFlow<Map<String, SinpeProfile?>> = _peerSinpeProfiles.asStateFlow()
    private val peerSinpeObserverJobs = mutableMapOf<String, Job>()
    private val handledAcceptedRequestIds = mutableSetOf<String>()

    private val triggeringBombIds = mutableSetOf<String>()
    private val explodingBombIds = mutableSetOf<String>()
    private val lastLiveBombsByChat = mutableMapOf<String, Map<String, ChatMessage>>()
    private val _explodedBombTombstones = MutableStateFlow<Map<String, List<ChatMessage>>>(emptyMap())
    private val cachedMessagesByChat = ConcurrentHashMap<String, List<ChatMessage>>()
    private val liveLocationTrackerJobs = mutableMapOf<String, Job>()

    private val _isStartingLiveLocation = MutableStateFlow(false)
    val isStartingLiveLocation: StateFlow<Boolean> = _isStartingLiveLocation.asStateFlow()

    val rawChatsState: StateFlow<UiState<List<Chat>>> = chatRepository.observeChats(currentUserId)
        .map<List<Chat>, UiState<List<Chat>>> { UiState.Success(it) }
        .catch { error ->
            val fsError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsError == null && error is CancellationException) throw error
            val root = fsError ?: error
            Log.w(TAG, "Error observing chats", root)
            emit(UiState.Error(formatFirestoreMessage(root, "No se pudieron cargar los chats.")))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val chatRequestsState: StateFlow<UiState<List<ChatRequest>>> = chatRepository.observeChatRequests(currentUserId)
        .map<List<ChatRequest>, UiState<List<ChatRequest>>> { UiState.Success(it) }
        .catch { error ->
            val fsError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsError == null && error is CancellationException) throw error
            val root = fsError ?: error
            Log.w(TAG, "Error observing chat requests", root)
            emit(UiState.Error(formatFirestoreMessage(root, "No se pudieron cargar las solicitudes.")))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val incomingPendingRequests: StateFlow<List<ChatRequest>> = chatRequestsState
        .map { state ->
            if (state is UiState.Success) {
                state.data.filter { it.toUid == currentUserId && it.status == "pending" }
            } else emptyList()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val pendingIncomingCount: StateFlow<Int> = incomingPendingRequests
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0)

    val filteredChatsState: StateFlow<UiState<List<Chat>>> = combine(
        rawChatsState,
        _chatSearch,
        _chatFilter
    ) { state, query, filter ->
        when (state) {
            is UiState.Success -> {
                val q = query.trim().lowercase()
                val filtered = state.data.filter { chat ->
                    val matchesType = when (filter) {
                        ChatFilter.ALL -> true
                        ChatFilter.DIRECT -> chat.chatType == "direct"
                        ChatFilter.GROUP -> chat.chatType == "group"
                        ChatFilter.REQUESTS -> false
                    }
                    val displayTitle = resolveChatDisplayTitle(chat, currentUserId).lowercase()
                    val matchesQuery = q.isBlank() ||
                        displayTitle.contains(q) ||
                        chat.lastMessageText.lowercase().contains(q)
                    matchesType && matchesQuery
                }
                UiState.Success(filtered)
            }
            else -> state
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val totalUnreadCount: StateFlow<Long> = rawChatsState
        .map { state ->
            if (state is UiState.Success) {
                state.data.sumOf { it.unreadCounts[currentUserId] ?: 0L }
            } else 0L
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0L)

    val contactsState: StateFlow<List<SavedContact>> = userRepository.observeContacts(currentUserId)
        .catch { error ->
            val fsError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsError == null && error is CancellationException) throw error
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val selectedChat: StateFlow<Chat?> = combine(rawChatsState, _selectedChatId) { state, chatId ->
        if (state is UiState.Success && chatId != null) {
            state.data.firstOrNull { it.id == chatId }
        } else null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val messagesState: StateFlow<UiState<List<ChatMessage>>> = _selectedChatId
        .flatMapLatest { chatId ->
            if (chatId.isNullOrBlank()) {
                flowOf(UiState.Success(emptyList()))
            } else {
                combine(
                    chatRepository.observeMessages(chatId, currentUserId),
                    _explodedBombTombstones.map { it[chatId].orEmpty() }
                ) { liveMessages, tombstones ->
                    val currentLiveById = liveMessages.associateBy { it.id }
                    val prevLiveBombs = lastLiveBombsByChat[chatId].orEmpty()
                    val vanishedTriggered = prevLiveBombs.values.filter { prev ->
                        prev.id !in currentLiveById &&
                            prev.bombDurationSec != null &&
                            prev.bombTriggeredAt != null &&
                            prev.deletedForEveryone != true
                    }
                    lastLiveBombsByChat[chatId] = currentLiveById.filterValues { it.bombDurationSec != null }
                    if (vanishedTriggered.isNotEmpty()) {
                        vanishedTriggered.forEach { vanished ->
                            markBombExplodedTombstone(chatId, vanished)
                        }
                    }
                    val mergedTombstones = (_explodedBombTombstones.value[chatId].orEmpty() + tombstones)
                        .distinctBy { it.id }
                    val mergedList = mergeMessagesWithBombTombstones(liveMessages, mergedTombstones)
                    cachedMessagesByChat[chatId] = mergedList
                    UiState.Success(mergedList) as UiState<List<ChatMessage>>
                }
                    .onStart {
                        cachedMessagesByChat[chatId]?.let { cached ->
                            emit(UiState.Success(cached))
                        }
                    }
                    .catch { error ->
                        val fsError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (fsError == null && error is CancellationException) throw error
                        val root = fsError ?: error
                        val cached = cachedMessagesByChat[chatId]
                        if (cached != null) {
                            emit(UiState.Success(cached))
                        } else {
                            emit(UiState.Error(formatFirestoreMessage(root, "Error al cargar mensajes.")))
                        }
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val activeLiveLocations: StateFlow<List<LiveLocationShare>> = _selectedChatId
        .flatMapLatest { chatId ->
            if (chatId.isNullOrBlank()) {
                flowOf(emptyList())
            } else {
                chatRepository.observeChatLiveLocations(chatId)
                    .catch { error ->
                        if (error is CancellationException) throw error
                        emit(emptyList())
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    init {
        viewModelScope.launch {
            rawChatsState.collect { state ->
                if (state is UiState.Success) {
                    val peerIds = state.data
                        .flatMap { it.participantUids }
                        .filter { it.isNotBlank() && it != currentUserId }
                        .toSet()

                    peerIds.forEach { peerUid ->
                        ensureObservingPublicProfile(peerUid)
                    }
                }
            }
        }
        viewModelScope.launch {
            combine(rawChatsState, contactsState) { state, contacts ->
                val chatPeers = if (state is UiState.Success) {
                    state.data
                        .filter { it.chatType == "direct" }
                        .mapNotNull { resolvePeerUid(it, currentUserId) }
                } else {
                    emptyList()
                }
                (chatPeers + contacts.map { it.contactUid })
                    .filter { it.isNotBlank() && it != currentUserId }
                    .toSet()
            }.collect { peerIds ->
                peerIds.forEach { peerUid ->
                    if (peerSinpeObserverJobs[peerUid]?.isActive != true) {
                        val job = viewModelScope.launch {
                            try {
                                userRepository.observeSinpeProfile(peerUid)
                                    .catch { error ->
                                        if (error is CancellationException) throw error
                                        emit(null)
                                    }
                                    .collect { sinpe ->
                                        _peerSinpeProfiles.update { it + (peerUid to sinpe) }
                                    }
                            } finally {
                                peerSinpeObserverJobs.remove(peerUid)
                            }
                        }
                        peerSinpeObserverJobs[peerUid] = job
                    }
                }
            }
        }
        viewModelScope.launch {
            chatRequestsState.collect { state ->
                if (state is UiState.Success) {
                    state.data
                        .filter { it.fromUid == currentUserId && it.status == "accepted" }
                        .forEach { req ->
                            if (handledAcceptedRequestIds.add(req.id)) {
                                viewModelScope.launch {
                                    userRepository.authorizePeerForSinpe(req.toUid)
                                }
                            }
                        }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        peerObserverJobs.values.forEach { it.cancel() }
        peerObserverJobs.clear()
        peerSinpeObserverJobs.values.forEach { it.cancel() }
        peerSinpeObserverJobs.clear()
        liveLocationTrackerJobs.values.forEach { it.cancel() }
        liveLocationTrackerJobs.clear()
    }

    fun setChatSearch(q: String) {
        _chatSearch.value = q
    }

    fun setChatFilter(filter: ChatFilter) {
        _chatFilter.value = filter
    }

    fun openChat(chatId: String?, sendReadReceipt: Boolean = true) {
        _selectedChatId.value = chatId
        if (chatId != null) {
            val chat = (rawChatsState.value as? UiState.Success)?.data?.firstOrNull { it.id == chatId }
            if (chat != null) {
                viewModelScope.launch { chatRepository.markChatRead(chat, sendReadReceipt) }
            }
        }
    }

    fun markChatRead(chat: Chat, sendReadReceipt: Boolean = true) {
        viewModelScope.launch { chatRepository.markChatRead(chat, sendReadReceipt) }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    fun lookupUserExact(query: String) {
        viewModelScope.launch {
            _lookupResult.value = UiState.Loading
            val res = userRepository.lookupDirectoryExact(query)
            if (res.isSuccess) {
                _lookupResult.value = UiState.Success(res.getOrNull())
            } else {
                _lookupResult.value = UiState.Error(
                    res.exceptionOrNull()?.localizedMessage ?: "No se pudo buscar el contacto."
                )
            }
        }
    }

    fun clearLookup() {
        _lookupResult.value = null
    }

    fun saveContact(entry: DirectoryEntry) {
        viewModelScope.launch {
            val res = userRepository.saveContact(entry)
            if (res.isSuccess) {
                _actionMessage.value = "@${entry.username} guardado en tus contactos."
            } else {
                _errorMessage.value = res.exceptionOrNull()?.localizedMessage ?: "No se pudo guardar."
            }
        }
    }

    fun removeContact(contactUid: String) {
        viewModelScope.launch {
            val res = userRepository.removeContact(contactUid)
            if (res.isFailure) {
                _errorMessage.value = res.exceptionOrNull()?.localizedMessage ?: "No se pudo eliminar."
            }
        }
    }

    fun sendChatRequest(
        currentUser: PublicUser,
        target: DirectoryEntry,
        note: String? = null,
        onSent: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val res = chatRepository.sendChatRequest(currentUser, target, note)
            if (res.isSuccess) {
                _actionMessage.value = "Solicitud enviada a @${target.username}."
                onSent()
            } else {
                _errorMessage.value = res.exceptionOrNull()?.localizedMessage ?: "No se pudo enviar la solicitud."
            }
        }
    }

    fun acceptRequest(request: ChatRequest, currentUser: PublicUser, openAfterAccept: Boolean = true) {
        viewModelScope.launch {
            _busyRequestIds.value = _busyRequestIds.value + request.id
            val res = chatRepository.acceptChatRequest(request, currentUser)
            _busyRequestIds.value = _busyRequestIds.value - request.id
            if (res.isSuccess) {
                val chatId = res.getOrThrow()
                _actionMessage.value = "Solicitud de ${request.fromName} aceptada."
                if (openAfterAccept) {
                    _chatFilter.value = ChatFilter.ALL
                    _selectedChatId.value = chatId
                }
            } else {
                _errorMessage.value = res.exceptionOrNull()?.localizedMessage ?: "No se pudo aceptar la solicitud."
            }
        }
    }

    fun rejectRequest(request: ChatRequest) {
        viewModelScope.launch {
            _busyRequestIds.value = _busyRequestIds.value + request.id
            val res = chatRepository.rejectChatRequest(request.id)
            _busyRequestIds.value = _busyRequestIds.value - request.id
            if (res.isSuccess) {
                _actionMessage.value = "Solicitud rechazada."
            } else {
                _errorMessage.value = res.exceptionOrNull()?.localizedMessage ?: "No se pudo rechazar la solicitud."
            }
        }
    }

    fun deleteRequest(request: ChatRequest) {
        viewModelScope.launch {
            _busyRequestIds.value = _busyRequestIds.value + request.id
            val res = chatRepository.deleteChatRequest(request.id)
            _busyRequestIds.value = _busyRequestIds.value - request.id
            if (res.isSuccess) {
                _actionMessage.value = "Solicitud eliminada."
            } else {
                _errorMessage.value = res.exceptionOrNull()?.localizedMessage ?: "No se pudo eliminar la solicitud."
            }
        }
    }

    fun deleteChat(chatId: String) {
        viewModelScope.launch {
            val res = chatRepository.deleteChat(chatId)
            if (res.isSuccess) {
                lastLiveBombsByChat.remove(chatId)
                cachedMessagesByChat.remove(chatId)
                _explodedBombTombstones.update { it - chatId }
                if (_selectedChatId.value == chatId) {
                    _selectedChatId.value = null
                }
                _actionMessage.value = "Conversación eliminada."
            } else {
                _errorMessage.value = res.exceptionOrNull()?.localizedMessage ?: "No se pudo eliminar el chat."
            }
        }
    }

    fun startDirectChat(
        currentUser: PublicUser,
        peerUid: String,
        peerName: String,
        peerAvatar: String?,
        initialMessage: String? = null,
        onOpened: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            val res = chatRepository.getOrCreateDirectChatModel(currentUser, peerUid, peerName, peerAvatar)
            if (res.isSuccess) {
                val realChat = res.getOrThrow()
                _chatFilter.value = ChatFilter.ALL
                _selectedChatId.value = realChat.id
                if (!initialMessage.isNullOrBlank()) {
                    val sendRes = chatRepository.sendTextMessage(realChat, currentUser, initialMessage)
                    if (sendRes.isFailure) {
                        _errorMessage.value = sendRes.exceptionOrNull()?.localizedMessage ?: "No se pudo enviar el mensaje inicial."
                    }
                }
                onOpened(realChat.id)
            } else {
                _errorMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Error al abrir chat."
            }
        }
    }

    fun ensureObservingPublicProfile(peerUid: String) {
        if (peerUid.isBlank() || peerUid == currentUserId) return
        if (peerObserverJobs[peerUid]?.isActive == true) return
        val job = viewModelScope.launch {
            try {
                userRepository.observePublicProfile(peerUid)
                    .catch { error ->
                        if (error is CancellationException) throw error
                        emit(null)
                    }
                    .collect { profile ->
                        if (profile != null) {
                            _peerProfiles.update { it + (peerUid to profile) }
                        }
                    }
            } finally {
                peerObserverJobs.remove(peerUid)
            }
        }
        peerObserverJobs[peerUid] = job
    }

    fun createGroupChat(
        currentUser: PublicUser,
        title: String,
        contacts: List<SavedContact>,
        onCreated: (String) -> Unit = {}
    ) {
        createGroupChatWithMembers(
            currentUser = currentUser,
            title = title,
            members = contacts.map { Triple(it.contactUid, it.displayName, it.avatarUrl) },
            onCreated = onCreated
        )
    }

    fun createGroupChatWithMembers(
        currentUser: PublicUser,
        title: String,
        members: List<Triple<String, String, String?>>,
        onCreated: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            val res = chatRepository.createGroupChat(currentUser, title, members)
            if (res.isSuccess) {
                val id = res.getOrThrow()
                _chatFilter.value = ChatFilter.ALL
                _selectedChatId.value = id
                onCreated(id)
            } else {
                _errorMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Error al crear grupo."
            }
        }
    }

    fun addGroupParticipants(
        chatId: String,
        members: List<Triple<String, String, String?>>,
        onDone: () -> Unit = {}
    ) {
        if (members.isEmpty()) return
        viewModelScope.launch {
            val res = chatRepository.addGroupParticipants(chatId, members)
            if (res.isSuccess) {
                _actionMessage.value = if (members.size == 1) {
                    "${members.first().second} se unió al grupo."
                } else {
                    "Se agregaron ${members.size} integrantes al grupo."
                }
                onDone()
            } else {
                _errorMessage.value = res.exceptionOrNull()?.localizedMessage ?: "No se pudieron agregar integrantes."
            }
        }
    }

    fun leaveGroup(chatId: String, actorName: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            liveLocationTrackerJobs.remove(chatId)?.cancel()
            val res = chatRepository.leaveGroup(chatId, actorName)
            if (res.isSuccess) {
                lastLiveBombsByChat.remove(chatId)
                cachedMessagesByChat.remove(chatId)
                _explodedBombTombstones.update { it - chatId }
                if (_selectedChatId.value == chatId) {
                    _selectedChatId.value = null
                }
                _actionMessage.value = "Saliste del grupo."
                onDone()
            } else {
                _errorMessage.value = res.exceptionOrNull()?.localizedMessage ?: "No se pudo salir del grupo."
            }
        }
    }

    fun deleteGroup(chatId: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            liveLocationTrackerJobs.remove(chatId)?.cancel()
            val res = chatRepository.deleteGroup(chatId)
            if (res.isSuccess) {
                lastLiveBombsByChat.remove(chatId)
                cachedMessagesByChat.remove(chatId)
                _explodedBombTombstones.update { it - chatId }
                if (_selectedChatId.value == chatId) {
                    _selectedChatId.value = null
                }
                _actionMessage.value = "Grupo eliminado permanentemente."
                onDone()
            } else {
                _errorMessage.value = res.exceptionOrNull()?.localizedMessage ?: "No se pudo eliminar el grupo."
            }
        }
    }

    fun startLiveLocationSharing(
        context: Context,
        chat: Chat,
        sender: PublicUser,
        durationMin: Int
    ) {
        viewModelScope.launch {
            _isStartingLiveLocation.value = true
            try {
                val helper = LocationHelper(context)
                val locResult = helper.fetchPrivateLocation()
                if (locResult.isFailure) {
                    _errorMessage.value = locResult.exceptionOrNull()?.localizedMessage
                        ?: "Activa el GPS para compartir tu ubicación en tiempo real."
                    return@launch
                }
                val info = locResult.getOrThrow()
                val startRes = chatRepository.startLiveLocationShare(
                    chat = chat,
                    sender = sender,
                    latitude = info.latitude,
                    longitude = info.longitude,
                    accuracyMeters = 15.0,
                    durationMin = durationMin
                )
                if (startRes.isFailure) {
                    _errorMessage.value = startRes.exceptionOrNull()?.localizedMessage
                        ?: "No se pudo iniciar la ubicación en tiempo real."
                    return@launch
                }
                val expiresAt = startRes.getOrThrow()
                _actionMessage.value = "Compartiendo ubicación en vivo (${durationMin} min)."

                liveLocationTrackerJobs.remove(chat.id)?.cancel()
                val trackerJob = viewModelScope.launch {
                    try {
                        helper.observeLiveLocationUpdates(intervalMs = 5000L)
                            .catch { /* keep quiet if GPS pauses temporarily */ }
                            .collect { location ->
                                val nowMs = System.currentTimeMillis()
                                if (nowMs >= expiresAt.toDate().time) {
                                    chatRepository.stopLiveLocationShare(chat.id)
                                    liveLocationTrackerJobs.remove(chat.id)?.cancel()
                                    return@collect
                                }
                                chatRepository.updateLiveLocationCoordinates(
                                    chat = chat,
                                    sender = sender,
                                    latitude = location.latitude,
                                    longitude = location.longitude,
                                    accuracyMeters = if (location.hasAccuracy()) location.accuracy.toDouble() else null,
                                    expiresAt = expiresAt
                                )
                            }
                    } finally {
                        liveLocationTrackerJobs.remove(chat.id)
                    }
                }
                liveLocationTrackerJobs[chat.id] = trackerJob
            } finally {
                _isStartingLiveLocation.value = false
            }
        }
    }

    fun stopLiveLocationSharing(chatId: String) {
        liveLocationTrackerJobs.remove(chatId)?.cancel()
        viewModelScope.launch {
            val res = chatRepository.stopLiveLocationShare(chatId)
            if (res.isSuccess) {
                _actionMessage.value = "Dejaste de compartir tu ubicación en vivo."
            } else {
                _errorMessage.value = res.exceptionOrNull()?.localizedMessage ?: "No se pudo detener la ubicación en vivo."
            }
        }
    }

    fun sendMessageOrCommand(
        chat: Chat,
        sender: PublicUser,
        senderPhone: String?,
        rawText: String,
        bombDurationSec: Int? = null,
        senderBank: String? = null,
        onMentionAssistant: (String) -> Unit
    ) {
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) return

        if (trimmed.startsWith("@asistente", ignoreCase = true) ||
            trimmed.startsWith("@vyb", ignoreCase = true)
        ) {
            val prompt = trimmed.substringAfter(" ", "").ifBlank { "Resume este chat por favor." }
            onMentionAssistant(prompt)
            return
        }

        val sinpeRegex = Regex("^/cobrar\\s+(\\d+)(?:\\s+por\\s+(.+))?$", RegexOption.IGNORE_CASE)
        val match = sinpeRegex.matchEntire(trimmed)
        if (match != null) {
            val amount = match.groupValues[1].toLongOrNull() ?: 0L
            val reason = match.groupValues.getOrNull(2)?.ifBlank { "Cobro SINPE Móvil" } ?: "Cobro SINPE Móvil"
            if (amount > 0L) {
                sendSinpeCard(chat, sender, senderPhone, amount, 1, reason, senderBank = senderBank)
                return
            }
        }

        viewModelScope.launch {
            val res = chatRepository.sendTextMessage(chat, sender, trimmed, bombDurationSec)
            if (res.isFailure) {
                _errorMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Error al enviar mensaje."
            }
        }
    }

    fun sendMediaAttachment(
        context: Context,
        chat: Chat,
        sender: PublicUser,
        uri: Uri,
        caption: String? = null,
        bombDurationSec: Int? = null,
        onResult: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            val prep = MediaUtils.prepareMediaPayload(context, uri)
            if (prep.isFailure) {
                _errorMessage.value = prep.exceptionOrNull()?.localizedMessage ?: "No se pudo adjuntar."
                onResult(false)
                return@launch
            }
            val res = chatRepository.sendMediaMessage(chat, sender, prep.getOrThrow(), caption, bombDurationSec)
            if (res.isFailure) {
                _errorMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Error al enviar multimedia."
                onResult(false)
            } else {
                onResult(true)
            }
        }
    }

    fun sendVoiceMessage(
        chat: Chat,
        sender: PublicUser,
        audioFile: File,
        durationSec: Int,
        bombDurationSec: Int? = null,
        onSent: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isSendingAudio.value = true
            try {
                val prep = MediaUtils.prepareAudioPayload(audioFile)
                if (prep.isFailure) {
                    _errorMessage.value = prep.exceptionOrNull()?.localizedMessage ?: "No se pudo procesar la nota de voz."
                    return@launch
                }
                val res = chatRepository.sendAudioMessage(chat, sender, prep.getOrThrow(), durationSec, bombDurationSec)
                if (res.isSuccess) {
                    runCatching { audioFile.delete() }
                    onSent()
                } else {
                    _errorMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Error al enviar nota de voz."
                }
            } finally {
                _isSendingAudio.value = false
            }
        }
    }

    fun sendDocumentAttachment(
        context: Context,
        chat: Chat,
        sender: PublicUser,
        uri: Uri,
        onResult: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            val prep = MediaUtils.prepareDocumentPayload(context, uri)
            if (prep.isFailure) {
                _errorMessage.value = prep.exceptionOrNull()?.localizedMessage ?: "No se pudo leer el documento."
                onResult(false)
                return@launch
            }
            val res = chatRepository.sendDocumentMessage(chat, sender, prep.getOrThrow())
            if (res.isFailure) {
                _errorMessage.value = res.exceptionOrNull()?.localizedMessage ?: "No se pudo enviar el documento."
                onResult(false)
            } else {
                onResult(true)
            }
        }
    }

    fun sendLocationAttachment(
        chat: Chat,
        sender: PublicUser,
        latitude: Double,
        longitude: Double,
        label: String
    ) {
        viewModelScope.launch {
            val res = chatRepository.sendLocationMessage(chat, sender, latitude, longitude, label)
            if (res.isFailure) {
                _errorMessage.value = res.exceptionOrNull()?.localizedMessage ?: "No se pudo compartir la ubicación."
            }
        }
    }

    fun sendContactAttachment(
        chat: Chat,
        sender: PublicUser,
        contact: SavedContact
    ) {
        viewModelScope.launch {
            val res = chatRepository.sendContactMessage(chat, sender, contact)
            if (res.isFailure) {
                _errorMessage.value = res.exceptionOrNull()?.localizedMessage ?: "No se pudo compartir el contacto."
            }
        }
    }

    fun sendPoll(
        chat: Chat,
        sender: PublicUser,
        question: String,
        options: List<String>,
        onSent: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val res = chatRepository.sendPollMessage(chat, sender, question, options)
            if (res.isSuccess) {
                onSent()
            } else {
                _errorMessage.value = res.exceptionOrNull()?.localizedMessage ?: "No se pudo crear la encuesta."
            }
        }
    }

    fun votePollOption(chatId: String, messageId: String, optionIndex: Int) {
        viewModelScope.launch {
            val res = chatRepository.votePollOption(chatId, messageId, optionIndex)
            if (res.isFailure) {
                _errorMessage.value = res.exceptionOrNull()?.localizedMessage ?: "No se pudo registrar tu voto."
            }
        }
    }

    fun openDocumentAttachment(
        context: Context,
        message: ChatMessage,
        loadFullMediaFile: suspend (String) -> File?,
        onStatus: (Boolean) -> Unit = {}
    ) {
        val mediaId = message.mediaId
        if (mediaId.isNullOrBlank()) {
            _errorMessage.value = "El documento no está disponible."
            return
        }
        viewModelScope.launch {
            onStatus(true)
            try {
                val downloaded = loadFullMediaFile(mediaId)
                if (downloaded == null || !downloaded.exists()) {
                    _errorMessage.value = "No se pudo descargar el documento."
                    return@launch
                }
                val bytes = withContext(Dispatchers.IO) { downloaded.readBytes() }
                val mime = message.documentMimeType ?: "application/octet-stream"
                val shareable = MediaUtils.writeShareableDocumentFile(
                    context = context,
                    mediaId = mediaId,
                    mimeType = mime,
                    bytes = bytes,
                    preferredName = message.documentName
                )
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    shareable
                )
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mime)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(intent)
                } catch (_: ActivityNotFoundException) {
                    _errorMessage.value = "No tienes una aplicación instalada para abrir este archivo (${message.documentName ?: "documento"})."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "No se pudo abrir el documento."
            } finally {
                onStatus(false)
            }
        }
    }

    fun editMessage(
        chatId: String,
        message: ChatMessage,
        newText: String,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val res = chatRepository.editTextMessage(chatId, message, newText)
            if (res.isSuccess) {
                _actionMessage.value = "Mensaje editado."
                onDone()
            } else {
                _errorMessage.value = res.exceptionOrNull()?.localizedMessage ?: "No se pudo editar el mensaje."
            }
        }
    }

    fun deleteMessageForEveryone(chatId: String, message: ChatMessage) {
        viewModelScope.launch {
            val res = chatRepository.deleteMessageForEveryone(chatId, message)
            if (res.isSuccess) {
                _actionMessage.value = "Mensaje eliminado para ambos."
            } else {
                _errorMessage.value = res.exceptionOrNull()?.localizedMessage ?: "No se pudo eliminar el mensaje."
            }
        }
    }

    fun triggerBombMessage(chatId: String, messageId: String) {
        if (!triggeringBombIds.add(messageId)) return
        viewModelScope.launch {
            val res = chatRepository.triggerBombMessage(chatId, messageId)
            if (res.isFailure) {
                triggeringBombIds.remove(messageId)
            }
        }
    }

    fun markBombExplodedTombstone(chatId: String, message: ChatMessage) {
        if (chatId.isBlank() || message.id.isBlank()) return
        val sanitized = ChatMessage(
            id = message.id,
            chatId = chatId,
            participantUids = message.participantUids,
            senderId = message.senderId,
            senderName = message.senderName,
            messageType = "bomb_destroyed_ghost",
            text = "",
            bombDurationSec = message.bombDurationSec,
            bombTriggeredAt = message.bombTriggeredAt,
            createdAt = message.createdAt ?: Timestamp.now(),
            updatedAt = Timestamp.now()
        )
        _explodedBombTombstones.update { current ->
            val list = current[chatId].orEmpty()
            if (list.any { it.id == sanitized.id }) current
            else current + (chatId to (list + sanitized))
        }
    }

    fun explodeBombMessage(chatId: String, message: ChatMessage, delayMs: Long = 0L) {
        markBombExplodedTombstone(chatId, message)
        if (!explodingBombIds.add(message.id)) return
        viewModelScope.launch {
            if (delayMs > 0L) {
                delay(delayMs)
            }
            val res = chatRepository.explodeBombMessage(chatId, message)
            if (res.isFailure) {
                explodingBombIds.remove(message.id)
            }
        }
    }

    fun sendSinpeCard(
        chat: Chat,
        sender: PublicUser,
        senderPhone: String?,
        totalAmount: Long,
        splitCount: Int,
        reason: String,
        senderBank: String? = null
    ) {
        viewModelScope.launch {
            val res = chatRepository.sendSinpeCardMessage(
                chat = chat,
                sender = sender,
                senderPhone = senderPhone,
                totalAmount = totalAmount,
                splitCount = splitCount,
                reason = reason,
                senderBank = senderBank
            )
            if (res.isFailure) {
                _errorMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Error al generar cobro SINPE."
            }
        }
    }

    fun authorizeSinpePayment(chatId: String, message: ChatMessage) {
        viewModelScope.launch {
            val res = chatRepository.authorizeSinpePayment(chatId, message)
            if (res.isSuccess) {
                _sinpeConfettiTrigger.value = _sinpeConfettiTrigger.value + 1
            } else {
                _errorMessage.value = res.exceptionOrNull()?.localizedMessage ?: "No se pudo autorizar el pago."
            }
        }
    }

    fun sendBuzz(
        chat: Chat,
        sender: PublicUser,
        currentPrivateUser: PrivateUser? = null,
        onSent: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val today = currentDateKey()
            val currentMessages = (messagesState.value as? UiState.Success)?.data ?: emptyList()
            val priorOptimistic = _optimisticBuzzDayAndCount.value
                ?.takeIf { it.first == today }?.second ?: 0
            val usedBeforeSend = usedDailyBuzzCount(
                privateUser = currentPrivateUser,
                messages = currentMessages,
                currentUid = currentUserId,
                optimisticUsedToday = priorOptimistic
            )
            val res = chatRepository.sendBuzzMessage(chat, sender)
            if (res.isSuccess) {
                val nextUsed = (usedBeforeSend + 1).coerceAtMost(MAX_BUZZ_PER_DAY.toInt())
                _optimisticBuzzDayAndCount.value = today to nextUsed
                onSent()
            } else {
                val msg = res.exceptionOrNull()?.localizedMessage ?: "No se pudo enviar el bip."
                if (msg.contains("2 bips", ignoreCase = true) || msg.contains("2 zumbidos", ignoreCase = true)) {
                    _optimisticBuzzDayAndCount.value = today to MAX_BUZZ_PER_DAY.toInt()
                }
                _errorMessage.value = msg
            }
        }
    }

    fun shareAiSummary(chat: Chat, sender: PublicUser, summary: StructuredChatSummary) {
        viewModelScope.launch {
            val res = chatRepository.shareAiSummaryMessage(chat, sender, summary)
            if (res.isFailure) {
                _errorMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Error al compartir resumen."
            }
        }
    }

    private fun formatFirestoreMessage(error: Throwable, fallback: String): String {
        val raw = error.localizedMessage ?: error.message ?: ""
        return when {
            raw.contains("PERMISSION_DENIED", ignoreCase = true) ->
                "Sin permisos para abrir esta sección. Inicia sesión de nuevo o revisa tu conexión."
            raw.contains("UNAVAILABLE", ignoreCase = true) || raw.contains("network", ignoreCase = true) ->
                "Sin conexión estable. Reintentando automáticamente..."
            raw.isNotBlank() -> raw
            else -> fallback
        }
    }

    data class MessageGroupingMeta(
        val showDateDivider: Boolean,
        val dateLabel: String,
        val dayKey: String,
        val isGroupedWithPrevious: Boolean,
        val isGroupedWithNext: Boolean,
        val topSpacingDp: Int
    )

    companion object {
        const val MAX_BUZZ_PER_DAY = 2L

        val costaRicaTimeZone: TimeZone = TimeZone.getTimeZone("America/Costa_Rica")

        fun currentDateKey(nowMillis: Long = System.currentTimeMillis()): String =
            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply {
                timeZone = costaRicaTimeZone
            }.format(java.util.Date(nowMillis))

        fun countBuzzesSentTodayByUser(
            messages: List<ChatMessage>,
            currentUid: String,
            nowMillis: Long = System.currentTimeMillis()
        ): Int {
            if (currentUid.isBlank()) return 0
            val today = currentDateKey(nowMillis)
            return messages.count { msg ->
                msg.messageType == "buzz" &&
                    msg.senderId == currentUid &&
                    msg.deletedForEveryone != true &&
                    (msg.createdAt == null || currentDateKey(msg.createdAt.toDate().time) == today)
            }
        }

        fun usedDailyBuzzCount(
            privateUser: PrivateUser?,
            messages: List<ChatMessage> = emptyList(),
            currentUid: String = "",
            optimisticUsedToday: Int = 0,
            nowMillis: Long = System.currentTimeMillis()
        ): Int {
            val today = currentDateKey(nowMillis)
            val fromPrivate = if (privateUser?.buzzDailyDate == today) {
                privateUser.buzzDailyCount.toInt().coerceAtLeast(0)
            } else {
                0
            }
            val fromMessages = countBuzzesSentTodayByUser(messages, currentUid, nowMillis)
            return maxOf(fromPrivate, fromMessages, optimisticUsedToday.coerceAtLeast(0))
                .coerceIn(0, MAX_BUZZ_PER_DAY.toInt())
        }

        fun remainingDailyBuzzCount(privateUser: PrivateUser?): Int {
            val today = currentDateKey()
            val used = if (privateUser?.buzzDailyDate == today) privateUser.buzzDailyCount else 0L
            return (MAX_BUZZ_PER_DAY - used).coerceIn(0L, MAX_BUZZ_PER_DAY).toInt()
        }

        fun remainingDailyBuzzCount(
            privateUser: PrivateUser?,
            messages: List<ChatMessage>,
            currentUid: String,
            optimisticUsedToday: Int = 0,
            nowMillis: Long = System.currentTimeMillis()
        ): Int {
            val used = usedDailyBuzzCount(
                privateUser = privateUser,
                messages = messages,
                currentUid = currentUid,
                optimisticUsedToday = optimisticUsedToday,
                nowMillis = nowMillis
            )
            return (MAX_BUZZ_PER_DAY.toInt() - used).coerceIn(0, MAX_BUZZ_PER_DAY.toInt())
        }

        fun computeMessageGroupingMeta(
            messages: List<ChatMessage>,
            index: Int,
            nowMillis: Long = System.currentTimeMillis()
        ): MessageGroupingMeta {
            val current = messages[index]
            val prev = messages.getOrNull(index - 1)
            val next = messages.getOrNull(index + 1)

            val currentDayKey = com.example.ui.components.chatMessageDayKey(current.createdAt, nowMillis)
            val prevDayKey = prev?.let { com.example.ui.components.chatMessageDayKey(it.createdAt, nowMillis) }
            val nextDayKey = next?.let { com.example.ui.components.chatMessageDayKey(it.createdAt, nowMillis) }

            val showDateDivider = prev == null || prevDayKey != currentDayKey
            val isGroupedWithPrevious = prev != null && !showDateDivider && prev.senderId == current.senderId
            val isGroupedWithNext = next != null && nextDayKey == currentDayKey && next.senderId == current.senderId
            val topSpacingDp = when {
                showDateDivider -> 0
                isGroupedWithPrevious -> 4
                else -> 12
            }
            return MessageGroupingMeta(
                showDateDivider = showDateDivider,
                dateLabel = com.example.ui.components.formatChatDateDivider(current.createdAt, nowMillis),
                dayKey = currentDayKey,
                isGroupedWithPrevious = isGroupedWithPrevious,
                isGroupedWithNext = isGroupedWithNext,
                topSpacingDp = topSpacingDp
            )
        }

        fun isBuzzPreviewText(rawText: String?): Boolean {
            val clean = rawText?.trim().orEmpty()
            return clean.equals("Bip", ignoreCase = true) ||
                clean == "⚡ ¡Bip!" ||
                clean.equals("Zumbido", ignoreCase = true) ||
                clean == "⚡ ¡Zumbido!"
        }

        fun resolveVisibleLastMessage(chat: Chat, currentUid: String): String {
            val watermark = chat.clearedBeforeAt[currentUid]
            val lastAt = chat.lastMessageAt
            if (watermark != null && lastAt != null) {
                val isBeforeOrEqual = lastAt.seconds < watermark.seconds ||
                    (lastAt.seconds == watermark.seconds && lastAt.nanoseconds <= watermark.nanoseconds)
                if (isBeforeOrEqual) return "Conversación vacía"
            }
            val raw = chat.lastMessageText
            return when {
                isBuzzPreviewText(raw) ->
                    if (chat.lastMessageSenderId == currentUid) "Enviaste un bip" else "Te envió un bip"
                else -> raw
            }
        }

        fun resolveChatDisplayTitle(chat: Chat, currentUid: String): String {
            if (chat.chatType == "group" && !chat.title.isNullOrBlank()) return chat.title
            val peerUid = chat.participantUids.firstOrNull { it != currentUid }
            return peerUid?.let { chat.participantNames[it] } ?: chat.title ?: "Chat VYB"
        }

        fun resolveAiSummaryHeaderTopic(
            aiSummaryTopic: String?,
            chat: Chat,
            currentUid: String
        ): String {
            val cleanTopic = aiSummaryTopic?.trim()?.takeIf { it.isNotBlank() }
            return cleanTopic ?: resolveChatDisplayTitle(chat, currentUid)
        }

        fun resolvePeerUid(chat: Chat, currentUid: String): String? =
            chat.participantUids.firstOrNull { it != currentUid }

        fun resolvePeerAvatar(chat: Chat, currentUid: String): String? {
            val peerUid = resolvePeerUid(chat, currentUid) ?: return null
            return chat.participantAvatars[peerUid]
        }

        fun isMessageReadByPeer(
            chat: Chat,
            messageCreatedAt: com.google.firebase.Timestamp?,
            currentUid: String,
            readReceiptsEnabled: Boolean
        ): Boolean {
            if (!readReceiptsEnabled) return false
            val msgTs = messageCreatedAt ?: return false
            val otherParticipants = chat.participantUids.filter { it != currentUid }
            if (otherParticipants.isEmpty()) return false
            return otherParticipants.any { peerUid ->
                val peerUnread = chat.unreadCounts[peerUid] ?: 0L
                val peerRead = chat.lastReadAt[peerUid]
                if (peerRead != null) {
                    val isReadAfterMsg = peerRead.seconds > msgTs.seconds ||
                        (peerRead.seconds == msgTs.seconds && peerRead.nanoseconds >= msgTs.nanoseconds)
                    isReadAfterMsg && peerUnread == 0L
                } else {
                    false
                }
            }
        }

        const val EDIT_WINDOW_SECONDS = 3600L

        fun canEditMessage(
            message: ChatMessage,
            currentUid: String,
            nowSeconds: Long = System.currentTimeMillis() / 1000L
        ): Boolean {
            if (message.senderId != currentUid) return false
            if (message.messageType != "text") return false
            if (message.deletedForEveryone == true) return false
            if (message.bombDurationSec != null) return false
            val createdSec = message.createdAt?.seconds ?: return true
            val elapsed = (nowSeconds - createdSec).coerceAtLeast(0L)
            return elapsed <= EDIT_WINDOW_SECONDS
        }

        fun remainingEditMinutes(
            message: ChatMessage,
            nowSeconds: Long = System.currentTimeMillis() / 1000L
        ): Int {
            val createdSec = message.createdAt?.seconds ?: return 60
            val remainingSec = (EDIT_WINDOW_SECONDS - (nowSeconds - createdSec).coerceAtLeast(0L)).coerceAtLeast(0L)
            return ((remainingSec + 59L) / 60L).toInt().coerceIn(0, 60)
        }

        fun mergeMessagesWithBombTombstones(
            liveMessages: List<ChatMessage>,
            tombstones: List<ChatMessage>
        ): List<ChatMessage> {
            if (tombstones.isEmpty()) return liveMessages
            val liveIds = liveMessages.mapTo(HashSet()) { it.id }
            val pendingTombstones = tombstones.filter { it.id !in liveIds }
            if (pendingTombstones.isEmpty()) return liveMessages
            return (liveMessages + pendingTombstones).sortedBy { it.createdAt ?: Timestamp(0, 0) }
        }
    }
}
