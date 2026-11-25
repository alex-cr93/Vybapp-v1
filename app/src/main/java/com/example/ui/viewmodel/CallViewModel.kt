package com.example.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.WebRtcCallManager
import com.example.data.model.CallSession
import com.example.data.model.Chat
import com.example.data.model.PublicUser
import com.example.data.model.UiState
import com.example.data.repository.CallRepository
import com.example.data.repository.UserRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

private const val TAG = "CallViewModel"

enum class CallHistoryFilter(val label: String) {
    ALL("Todas"),
    MISSED("Perdidas")
}

data class ActiveCallUiState(
    val session: CallSession? = null,
    val isIncoming: Boolean = false,
    val isConnected: Boolean = false,
    val isMuted: Boolean = false,
    val isVideoEnabled: Boolean = true,
    val isSpeakerOn: Boolean = false,
    val isFrontCamera: Boolean = true,
    val durationSeconds: Long = 0L,
    val errorMessage: String? = null
)

class CallViewModel(
    private val callRepository: CallRepository,
    private val userRepository: UserRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _activeCall = MutableStateFlow(ActiveCallUiState())
    val activeCall: StateFlow<ActiveCallUiState> = _activeCall.asStateFlow()

    private val _peerProfiles = MutableStateFlow<Map<String, PublicUser>>(emptyMap())
    val peerProfiles: StateFlow<Map<String, PublicUser>> = _peerProfiles.asStateFlow()
    private val peerObserverJobs = mutableMapOf<String, Job>()

    private val _remoteVideoTracks = MutableStateFlow<Map<String, VideoTrack>>(emptyMap())
    val remoteVideoTracks: StateFlow<Map<String, VideoTrack>> = _remoteVideoTracks.asStateFlow()

    private var webRtcManager: WebRtcCallManager? = null
    private var callDocJob: Job? = null
    private var signalJob: Job? = null
    private var iceJob: Job? = null
    private var remoteTracksJob: Job? = null
    private var timerJob: Job? = null
    private var ringingTimeoutJob: Job? = null
    private val processedCandidateIds = mutableSetOf<String>()
    private val processedSignalKeys = mutableSetOf<String>()
    private val connectedMeshPeers = mutableSetOf<String>()
    private val dismissedIncomingGroupCallIds = mutableSetOf<String>()
    private var answerApplied = false

    private val _historyRetryTrigger = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val callHistoryState: StateFlow<UiState<List<CallSession>>> =
        _historyRetryTrigger
            .flatMapLatest {
                callRepository.observeCalls(currentUserId)
                    .map<List<CallSession>, UiState<List<CallSession>>> { UiState.Success(it) }
                    .catch { error ->
                        val fsError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (fsError == null && error is CancellationException) throw error
                        val root = fsError ?: error
                        Log.w(TAG, "Error observing call history", root)
                        emit(UiState.Error(formatCallHistoryError(root)))
                    }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val activeGroupCallByChatId: StateFlow<Map<String, CallSession>> = callHistoryState
        .map { state ->
            if (state is UiState.Success) {
                state.data
                    .filter { call ->
                        call.groupCall &&
                            !call.chatId.isNullOrBlank() &&
                            call.status in listOf("ringing", "active")
                    }
                    .groupBy { it.chatId!! }
                    .mapValues { (_, list) -> list.first() }
            } else {
                emptyMap()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyMap())

    fun retryCallHistory() {
        _historyRetryTrigger.value = _historyRetryTrigger.value + 1
    }

    init {
        viewModelScope.launch {
            callHistoryState.collect { state ->
                if (state is UiState.Success) {
                    val peerIds = state.data
                        .flatMap { call ->
                            if (call.groupCall) {
                                call.participantUids
                            } else {
                                listOf(if (call.callerId == currentUserId) call.calleeId else call.callerId)
                            }
                        }
                        .filter { it.isNotBlank() && it != currentUserId }
                        .toSet()

                    peerIds.forEach { peerUid ->
                        if (peerObserverJobs[peerUid]?.isActive != true) {
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
                    }

                    if (_activeCall.value.session == null) {
                        val incoming = state.data.firstOrNull { call ->
                            if (call.groupCall) {
                                call.status == "ringing" &&
                                    call.callerId != currentUserId &&
                                    currentUserId !in call.declinedUids &&
                                    currentUserId !in call.activeParticipantUids &&
                                    call.id !in dismissedIncomingGroupCallIds
                            } else {
                                call.calleeId == currentUserId && call.status == "ringing"
                            }
                        }
                        if (incoming != null) {
                            _activeCall.value = ActiveCallUiState(
                                session = incoming,
                                isIncoming = true,
                                isVideoEnabled = incoming.callType == "video",
                                isSpeakerOn = incoming.callType == "video"
                            )
                            if (incoming.groupCall) {
                                observeGroupCallSignaling(incoming.id, incoming.participantUids)
                            } else {
                                observeDirectCallSignaling(incoming.id)
                            }
                        }
                    }
                }
            }
        }
    }

    fun startOutgoingCall(
        context: Context,
        caller: PublicUser,
        calleeUid: String,
        calleeName: String,
        calleeAvatar: String?,
        isVideo: Boolean
    ) {
        if (_activeCall.value.session != null) return
        answerApplied = false
        processedCandidateIds.clear()
        processedSignalKeys.clear()
        connectedMeshPeers.clear()

        val tempParticipants = listOf(currentUserId, calleeUid)
        var createdCallId: String? = null
        val pendingLocalIce = mutableListOf<org.webrtc.IceCandidate>()

        val manager = WebRtcCallManager(
            context = context,
            onLocalIceCandidate = { _, candidate ->
                val cid = createdCallId
                if (cid != null) {
                    viewModelScope.launch {
                        callRepository.pushIceCandidate(
                            cid,
                            tempParticipants,
                            candidate.sdpMid ?: "",
                            candidate.sdpMLineIndex,
                            candidate.sdp
                        )
                    }
                } else {
                    pendingLocalIce.add(candidate)
                }
            },
            onConnectionStateChanged = { _, state ->
                if (state == org.webrtc.PeerConnection.PeerConnectionState.CONNECTED) {
                    _activeCall.update { it.copy(isConnected = true) }
                    startTimerIfNeeded()
                }
            }
        )
        manager.initialize(enableVideo = isVideo)
        webRtcManager = manager
        observeManagerRemoteTracks(manager)

        _activeCall.value = ActiveCallUiState(
            session = CallSession(
                callerId = currentUserId,
                callerName = caller.displayName,
                callerAvatar = caller.avatarUrl,
                calleeId = calleeUid,
                calleeName = calleeName,
                calleeAvatar = calleeAvatar,
                participantUids = tempParticipants,
                callType = if (isVideo) "video" else "audio",
                status = "ringing"
            ),
            isIncoming = false,
            isVideoEnabled = isVideo,
            isSpeakerOn = isVideo
        )

        manager.createOffer { offerSdp ->
            viewModelScope.launch {
                val res = callRepository.createOutgoingCall(
                    caller = caller,
                    calleeUid = calleeUid,
                    calleeName = calleeName,
                    calleeAvatar = calleeAvatar,
                    callType = if (isVideo) "video" else "audio",
                    offerSdp = offerSdp
                )
                if (res.isSuccess) {
                    val callId = res.getOrThrow()
                    createdCallId = callId
                    pendingLocalIce.forEach { candidate ->
                        callRepository.pushIceCandidate(
                            callId,
                            tempParticipants,
                            candidate.sdpMid ?: "",
                            candidate.sdpMLineIndex,
                            candidate.sdp
                        )
                    }
                    pendingLocalIce.clear()
                    observeDirectCallSignaling(callId)
                    startRingingTimeout(callId, isGroup = false)
                } else {
                    _activeCall.update {
                        it.copy(errorMessage = res.exceptionOrNull()?.localizedMessage ?: "Error al llamar.")
                    }
                    cleanupWebRtc()
                }
            }
        }
    }

    fun startOutgoingGroupCall(
        context: Context,
        caller: PublicUser,
        chat: Chat,
        isVideo: Boolean
    ) {
        if (_activeCall.value.session != null) return
        val existingActive = activeGroupCallByChatId.value[chat.id]
        if (existingActive != null) {
            joinGroupCall(context, existingActive)
            return
        }

        val participants = (listOf(currentUserId) + chat.participantUids).distinct().take(20)
        if (participants.size < 2) return
        val title = chat.title?.takeIf { it.isNotBlank() } ?: "Grupo VYB"

        answerApplied = false
        processedCandidateIds.clear()
        processedSignalKeys.clear()
        connectedMeshPeers.clear()

        var createdCallId: String? = null
        val pendingLocalIce = mutableListOf<Pair<String, org.webrtc.IceCandidate>>()

        val manager = WebRtcCallManager(
            context = context,
            onLocalIceCandidate = { peerUid, candidate ->
                val cid = createdCallId
                if (cid != null) {
                    viewModelScope.launch {
                        callRepository.pushIceCandidate(
                            callId = cid,
                            participantUids = participants,
                            sdpMid = candidate.sdpMid ?: "",
                            sdpMLineIndex = candidate.sdpMLineIndex,
                            sdpCandidate = candidate.sdp,
                            targetUid = peerUid
                        )
                    }
                } else {
                    pendingLocalIce.add(peerUid to candidate)
                }
            },
            onConnectionStateChanged = { _, state ->
                if (state == org.webrtc.PeerConnection.PeerConnectionState.CONNECTED) {
                    _activeCall.update { it.copy(isConnected = true) }
                    startTimerIfNeeded()
                }
            }
        )
        manager.initialize(enableVideo = isVideo)
        webRtcManager = manager
        observeManagerRemoteTracks(manager)

        _activeCall.value = ActiveCallUiState(
            session = CallSession(
                callerId = currentUserId,
                callerName = caller.displayName,
                callerAvatar = caller.avatarUrl,
                calleeId = chat.id,
                calleeName = title,
                participantUids = participants,
                callType = if (isVideo) "video" else "audio",
                status = "ringing",
                groupCall = true,
                chatId = chat.id,
                groupTitle = title,
                activeParticipantUids = listOf(currentUserId)
            ),
            isIncoming = false,
            isVideoEnabled = isVideo,
            isSpeakerOn = isVideo
        )

        viewModelScope.launch {
            val res = callRepository.createGroupCall(
                caller = caller,
                chatId = chat.id,
                groupTitle = title,
                participantUids = participants,
                callType = if (isVideo) "video" else "audio"
            )
            if (res.isSuccess) {
                val callId = res.getOrThrow()
                createdCallId = callId
                pendingLocalIce.forEach { (peerUid, cand) ->
                    callRepository.pushIceCandidate(
                        callId = callId,
                        participantUids = participants,
                        sdpMid = cand.sdpMid ?: "",
                        sdpMLineIndex = cand.sdpMLineIndex,
                        sdpCandidate = cand.sdp,
                        targetUid = peerUid
                    )
                }
                pendingLocalIce.clear()
                observeGroupCallSignaling(callId, participants)
                startRingingTimeout(callId, isGroup = true)
            } else {
                _activeCall.update {
                    it.copy(errorMessage = res.exceptionOrNull()?.localizedMessage ?: "Error al iniciar llamada grupal.")
                }
                cleanupWebRtc()
            }
        }
    }

    fun joinGroupCall(context: Context, session: CallSession) {
        val callId = session.id
        if (callId.isBlank()) return
        val isVideo = session.callType == "video"
        answerApplied = false
        processedCandidateIds.clear()
        processedSignalKeys.clear()
        connectedMeshPeers.clear()
        dismissedIncomingGroupCallIds.remove(callId)

        val manager = WebRtcCallManager(
            context = context,
            onLocalIceCandidate = { peerUid, candidate ->
                viewModelScope.launch {
                    callRepository.pushIceCandidate(
                        callId = callId,
                        participantUids = session.participantUids,
                        sdpMid = candidate.sdpMid ?: "",
                        sdpMLineIndex = candidate.sdpMLineIndex,
                        sdpCandidate = candidate.sdp,
                        targetUid = peerUid
                    )
                }
            },
            onConnectionStateChanged = { _, state ->
                if (state == org.webrtc.PeerConnection.PeerConnectionState.CONNECTED) {
                    _activeCall.update { it.copy(isConnected = true) }
                    startTimerIfNeeded()
                }
            }
        )
        manager.initialize(enableVideo = isVideo)
        webRtcManager = manager
        observeManagerRemoteTracks(manager)

        _activeCall.value = ActiveCallUiState(
            session = session.copy(
                activeParticipantUids = (session.activeParticipantUids + currentUserId).distinct()
            ),
            isIncoming = false,
            isConnected = session.activeParticipantUids.any { it != currentUserId },
            isVideoEnabled = isVideo,
            isSpeakerOn = isVideo
        )
        if (session.activeParticipantUids.any { it != currentUserId }) {
            startTimerIfNeeded()
        }

        viewModelScope.launch {
            callRepository.joinGroupCall(callId)
        }
        observeGroupCallSignaling(callId, session.participantUids)
    }

    fun acceptIncomingCall(context: Context) {
        val session = _activeCall.value.session ?: return
        if (session.groupCall) {
            joinGroupCall(context, session)
            return
        }
        val isVideo = session.callType == "video"
        answerApplied = false
        processedCandidateIds.clear()
        processedSignalKeys.clear()
        connectedMeshPeers.clear()

        val manager = WebRtcCallManager(
            context = context,
            onLocalIceCandidate = { _, candidate ->
                viewModelScope.launch {
                    callRepository.pushIceCandidate(
                        session.id,
                        session.participantUids,
                        candidate.sdpMid ?: "",
                        candidate.sdpMLineIndex,
                        candidate.sdp
                    )
                }
            },
            onConnectionStateChanged = { _, state ->
                if (state == org.webrtc.PeerConnection.PeerConnectionState.CONNECTED) {
                    _activeCall.update { it.copy(isConnected = true) }
                    startTimerIfNeeded()
                }
            }
        )
        manager.initialize(enableVideo = isVideo)
        webRtcManager = manager
        observeManagerRemoteTracks(manager)

        _activeCall.update {
            it.copy(isIncoming = false, isVideoEnabled = isVideo, isSpeakerOn = isVideo)
        }

        val offerSdp = session.offerSdp
        if (!offerSdp.isNullOrBlank()) {
            manager.handleRemoteOfferAndCreateAnswer(offerSdp) { answerSdp ->
                viewModelScope.launch {
                    callRepository.answerCall(session.id, answerSdp)
                }
            }
        }
        observeDirectCallSignaling(session.id)
    }

    fun rejectOrEndCall() {
        val state = _activeCall.value
        val session = state.session
        val duration = state.durationSeconds
        val callId = session?.id

        if (session?.groupCall == true && !callId.isNullOrBlank()) {
            if (state.isIncoming && session.status == "ringing") {
                dismissedIncomingGroupCallIds.add(callId)
                cleanupWebRtc()
                _activeCall.value = ActiveCallUiState()
                viewModelScope.launch {
                    callRepository.declineGroupCall(callId)
                }
            } else {
                cleanupWebRtc()
                _activeCall.value = ActiveCallUiState()
                viewModelScope.launch {
                    callRepository.leaveOrEndGroupCall(callId, duration)
                }
            }
            return
        }

        val finalStatus = when {
            session == null -> "ended"
            state.isIncoming && session.status == "ringing" -> "rejected"
            !state.isIncoming && session.status == "ringing" -> "missed"
            else -> "ended"
        }

        cleanupWebRtc()
        _activeCall.value = ActiveCallUiState()

        if (!callId.isNullOrBlank()) {
            viewModelScope.launch {
                callRepository.updateCallStatus(callId, finalStatus, duration)
            }
        }
    }

    fun toggleMute() {
        val next = !_activeCall.value.isMuted
        webRtcManager?.setMuted(next)
        _activeCall.update { it.copy(isMuted = next) }
    }

    fun toggleVideo() {
        val next = !_activeCall.value.isVideoEnabled
        webRtcManager?.setVideoEnabled(next)
        _activeCall.update { it.copy(isVideoEnabled = next) }
    }

    fun toggleSpeaker() {
        val next = !_activeCall.value.isSpeakerOn
        webRtcManager?.setSpeakerphone(next)
        _activeCall.update { it.copy(isSpeakerOn = next) }
    }

    fun switchCamera() {
        webRtcManager?.switchCamera { isFront ->
            _activeCall.update { it.copy(isFrontCamera = isFront) }
        }
    }

    fun attachRenderers(local: SurfaceViewRenderer?, remote: SurfaceViewRenderer?) {
        local?.let { webRtcManager?.attachLocalRenderer(it) }
        remote?.let { webRtcManager?.attachRemoteRenderer(it) }
    }

    fun attachLocalRenderer(local: SurfaceViewRenderer?) {
        local?.let { webRtcManager?.attachLocalRenderer(it) }
    }

    fun attachRemoteRendererForPeer(peerUid: String, renderer: SurfaceViewRenderer) {
        webRtcManager?.attachRemoteRenderer(renderer, peerUid)
    }

    fun detachRemoteRendererForPeer(peerUid: String, renderer: SurfaceViewRenderer) {
        webRtcManager?.detachRemoteRenderer(renderer, peerUid)
    }

    fun getEglContext() = webRtcManager?.eglBase?.eglBaseContext

    private fun observeManagerRemoteTracks(manager: WebRtcCallManager) {
        remoteTracksJob?.cancel()
        remoteTracksJob = viewModelScope.launch {
            manager.remoteVideoTracks.collect { tracks ->
                _remoteVideoTracks.value = tracks
            }
        }
    }

    private fun observeDirectCallSignaling(callId: String) {
        callDocJob?.cancel()
        signalJob?.cancel()
        iceJob?.cancel()

        callDocJob = viewModelScope.launch {
            callRepository.observeCallDocument(callId)
                .catch { error ->
                    if (error is CancellationException) throw error
                }
                .collect { doc ->
                    if (doc == null) return@collect
                    _activeCall.update { it.copy(session = doc) }

                    if (doc.status == "active") {
                        ringingTimeoutJob?.cancel()
                        ringingTimeoutJob = null
                        val ans = doc.answerSdp
                        if (!answerApplied && doc.callerId == currentUserId && !ans.isNullOrBlank()) {
                            answerApplied = true
                            webRtcManager?.setRemoteAnswer(ans)
                        }
                    } else if (doc.status in listOf("ended", "rejected", "missed")) {
                        cleanupWebRtc()
                        _activeCall.value = ActiveCallUiState()
                    }
                }
        }

        iceJob = viewModelScope.launch {
            callRepository.observeIceCandidates(callId, currentUserId)
                .catch { error ->
                    if (error is CancellationException) throw error
                }
                .collect { candidates ->
                    candidates.forEach { cand ->
                        if (cand.senderId != currentUserId && processedCandidateIds.add(cand.id)) {
                            webRtcManager?.addRemoteIceCandidate(
                                cand.sdpMid,
                                cand.sdpMLineIndex,
                                cand.sdpCandidate
                            )
                        }
                    }
                }
        }
    }

    private fun observeGroupCallSignaling(callId: String, fallbackParticipants: List<String>) {
        callDocJob?.cancel()
        signalJob?.cancel()
        iceJob?.cancel()

        callDocJob = viewModelScope.launch {
            callRepository.observeCallDocument(callId)
                .catch { error ->
                    if (error is CancellationException) throw error
                }
                .collect { doc ->
                    if (doc == null) return@collect
                    val currentState = _activeCall.value
                    _activeCall.update { it.copy(session = doc) }

                    if (doc.status in listOf("ended", "rejected", "missed")) {
                        cleanupWebRtc()
                        _activeCall.value = ActiveCallUiState()
                        return@collect
                    }

                    if (doc.activeParticipantUids.size >= 2 || doc.status == "active") {
                        ringingTimeoutJob?.cancel()
                        ringingTimeoutJob = null
                    }

                    // If we are joined in this group call, reconcile WebRTC mesh peers
                    if (!currentState.isIncoming && currentUserId in doc.activeParticipantUids) {
                        val activeRemotePeers = doc.activeParticipantUids
                            .filter { it != currentUserId }
                            .toSet()

                        if (activeRemotePeers.isNotEmpty() && !currentState.isConnected) {
                            _activeCall.update { it.copy(isConnected = true) }
                            startTimerIfNeeded()
                        }

                        // Remove disconnected peers
                        val departed = connectedMeshPeers.filter { it !in activeRemotePeers }
                        departed.forEach { goneUid ->
                            connectedMeshPeers.remove(goneUid)
                            webRtcManager?.removePeer(goneUid)
                        }

                        // Deterministic glare-free mesh: lower UID creates offer to higher UID
                        val meshParticipants = doc.participantUids.ifEmpty { fallbackParticipants }
                        activeRemotePeers.forEach { peerUid ->
                            if (connectedMeshPeers.add(peerUid) && currentUserId < peerUid) {
                                webRtcManager?.createOffer(peerKey = peerUid) { offerSdp ->
                                    viewModelScope.launch {
                                        callRepository.upsertPeerSignal(
                                            callId = callId,
                                            participantUids = meshParticipants,
                                            toUid = peerUid,
                                            type = "offer",
                                            sdp = offerSdp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
        }

        signalJob = viewModelScope.launch {
            callRepository.observePeerSignals(callId, currentUserId)
                .catch { error ->
                    if (error is CancellationException) throw error
                }
                .collect { signals ->
                    val currentState = _activeCall.value
                    if (currentState.isIncoming) return@collect
                    val participants = currentState.session?.participantUids.orEmpty()
                        .ifEmpty { fallbackParticipants }

                    signals.forEach { sig ->
                        if (sig.toUid != currentUserId || sig.fromUid == currentUserId) return@forEach
                        val signalVersionKey = "${sig.id}_${sig.type}_${sig.sdp.hashCode()}"
                        if (!processedSignalKeys.add(signalVersionKey)) return@forEach

                        connectedMeshPeers.add(sig.fromUid)
                        when (sig.type) {
                            "offer" -> {
                                webRtcManager?.handleRemoteOfferAndCreateAnswer(
                                    offerSdp = sig.sdp,
                                    peerKey = sig.fromUid
                                ) { answerSdp ->
                                    viewModelScope.launch {
                                        callRepository.upsertPeerSignal(
                                            callId = callId,
                                            participantUids = participants,
                                            toUid = sig.fromUid,
                                            type = "answer",
                                            sdp = answerSdp
                                        )
                                    }
                                }
                            }
                            "answer" -> {
                                webRtcManager?.setRemoteAnswer(sig.sdp, peerKey = sig.fromUid)
                            }
                        }
                    }
                }
        }

        iceJob = viewModelScope.launch {
            callRepository.observeIceCandidates(callId, currentUserId)
                .catch { error ->
                    if (error is CancellationException) throw error
                }
                .collect { candidates ->
                    candidates.forEach { cand ->
                        val forUs = cand.senderId != currentUserId &&
                            (cand.targetUid == null || cand.targetUid == currentUserId)
                        if (forUs && processedCandidateIds.add(cand.id)) {
                            webRtcManager?.addRemoteIceCandidate(
                                sdpMid = cand.sdpMid,
                                sdpMLineIndex = cand.sdpMLineIndex,
                                sdpCandidate = cand.sdpCandidate,
                                peerKey = cand.senderId
                            )
                        }
                    }
                }
        }
    }

    private fun startRingingTimeout(callId: String, isGroup: Boolean) {
        ringingTimeoutJob?.cancel()
        ringingTimeoutJob = viewModelScope.launch {
            delay(45_000L)
            val current = _activeCall.value
            if (current.session?.id == callId && !current.isConnected && current.session.status == "ringing") {
                cleanupWebRtc()
                _activeCall.value = ActiveCallUiState()
                if (isGroup) {
                    callRepository.leaveOrEndGroupCall(callId, 0L)
                } else {
                    callRepository.updateCallStatus(callId, "missed", 0L)
                }
            }
        }
    }

    private fun startTimerIfNeeded() {
        if (timerJob != null) return
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                _activeCall.update { it.copy(durationSeconds = it.durationSeconds + 1L) }
            }
        }
    }

    private fun cleanupWebRtc() {
        ringingTimeoutJob?.cancel()
        ringingTimeoutJob = null
        callDocJob?.cancel()
        callDocJob = null
        signalJob?.cancel()
        signalJob = null
        iceJob?.cancel()
        iceJob = null
        remoteTracksJob?.cancel()
        remoteTracksJob = null
        timerJob?.cancel()
        timerJob = null
        connectedMeshPeers.clear()
        processedSignalKeys.clear()
        _remoteVideoTracks.value = emptyMap()
        webRtcManager?.release()
        webRtcManager = null
    }

    override fun onCleared() {
        super.onCleared()
        peerObserverJobs.values.forEach { it.cancel() }
        peerObserverJobs.clear()
        cleanupWebRtc()
    }

    companion object {
        fun filterCalls(calls: List<CallSession>, filter: CallHistoryFilter): List<CallSession> =
            when (filter) {
                CallHistoryFilter.ALL -> calls
                CallHistoryFilter.MISSED -> calls.filter { it.status == "missed" }
            }

        fun resolvePeerAvatar(livePeer: PublicUser?, fallbackAvatar: String?): String? =
            if (livePeer != null) {
                livePeer.avatarUrl?.takeIf { it.isNotBlank() }
            } else {
                fallbackAvatar?.takeIf { it.isNotBlank() }
            }

        fun formatCallHistoryError(error: Throwable): String {
            val raw = error.localizedMessage ?: error.message ?: ""
            return when {
                (error is FirebaseFirestoreException &&
                    error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) ||
                    raw.contains("PERMISSION_DENIED", ignoreCase = true) ||
                    raw.contains("insufficient permissions", ignoreCase = true) ->
                    "No se pudo verificar el permiso de tu historial de llamadas. Revisa tu sesión e intenta de nuevo."
                (error is FirebaseFirestoreException &&
                    error.code == FirebaseFirestoreException.Code.UNAVAILABLE) ||
                    raw.contains("UNAVAILABLE", ignoreCase = true) ||
                    raw.contains("network", ignoreCase = true) ->
                    "Sin conexión estable para cargar tus llamadas. Revisa tu internet y reintenta."
                error is IllegalStateException && raw.isNotBlank() -> raw
                else -> "No pudimos cargar tu historial de llamadas en este momento. Intenta nuevamente."
            }
        }
    }
}
