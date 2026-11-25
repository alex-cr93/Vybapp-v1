package com.example.core

import android.content.Context
import android.media.AudioManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "WebRtcCallManager"
private const val DEFAULT_PEER_KEY = "default_peer"

class WebRtcCallManager(
    private val context: Context,
    private val onLocalIceCandidate: (peerKey: String, candidate: IceCandidate) -> Unit,
    private val onConnectionStateChanged: (peerKey: String, state: PeerConnection.PeerConnectionState) -> Unit
) {
    val eglBase: EglBase = EglBase.create()
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var factory: PeerConnectionFactory? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var localVideoSource: VideoSource? = null
    private var localAudioSource: AudioSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var localAudioTrack: AudioTrack? = null

    private var localRenderer: SurfaceViewRenderer? = null

    private val peerConnections = ConcurrentHashMap<String, PeerConnection>()
    private val remoteRenderers = ConcurrentHashMap<String, SurfaceViewRenderer>()
    private val _remoteVideoTracks = MutableStateFlow<Map<String, VideoTrack>>(emptyMap())
    val remoteVideoTracks: StateFlow<Map<String, VideoTrack>> = _remoteVideoTracks.asStateFlow()

    private val iceLock = Any()
    private val peerHasRemoteDesc = ConcurrentHashMap<String, Boolean>()
    private val peerPendingIce = ConcurrentHashMap<String, MutableList<IceCandidate>>()

    private var isInitialized = false
    private var rtcConfig: PeerConnection.RTCConfiguration? = null

    fun initialize(enableVideo: Boolean) {
        if (isInitialized) return
        isInitialized = true
        synchronized(iceLock) {
            peerHasRemoteDesc.clear()
            peerPendingIce.clear()
        }

        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
        )

        val encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)

        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()

        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
        )
        rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        val audioConstraints = MediaConstraints()
        localAudioSource = factory?.createAudioSource(audioConstraints)
        localAudioTrack = factory?.createAudioTrack("vyb_audio_track", localAudioSource)

        if (enableVideo) {
            startLocalVideoTrack()
        }
        setSpeakerphone(enableVideo)
    }

    private fun ensurePeerConnection(peerKey: String): PeerConnection? {
        peerConnections[peerKey]?.let { return it }
        val f = factory ?: return null
        val cfg = rtcConfig ?: return null
        val pc = f.createPeerConnection(
            cfg,
            object : PeerConnection.Observer {
                override fun onIceCandidate(candidate: IceCandidate?) {
                    candidate?.let { onLocalIceCandidate(peerKey, it) }
                }

                override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                    newState?.let { onConnectionStateChanged(peerKey, it) }
                }

                override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
                    val track = receiver?.track()
                    if (track is VideoTrack) {
                        _remoteVideoTracks.update { it + (peerKey to track) }
                        remoteRenderers[peerKey]?.let { renderer ->
                            runCatching { track.addSink(renderer) }
                        }
                    }
                }

                override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
                override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {}
                override fun onIceConnectionReceivingChange(p0: Boolean) {}
                override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
                override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
                override fun onAddStream(p0: MediaStream?) {}
                override fun onRemoveStream(p0: MediaStream?) {}
                override fun onDataChannel(p0: DataChannel?) {}
                override fun onRenegotiationNeeded() {}
            }
        ) ?: return null

        localAudioTrack?.let { pc.addTrack(it, listOf("vyb_stream")) }
        localVideoTrack?.let { pc.addTrack(it, listOf("vyb_stream")) }
        peerConnections[peerKey] = pc
        return pc
    }

    fun removePeer(peerKey: String) {
        val track = _remoteVideoTracks.value[peerKey]
        remoteRenderers.remove(peerKey)?.let { renderer ->
            runCatching { track?.removeSink(renderer) }
        }
        _remoteVideoTracks.update { it - peerKey }
        synchronized(iceLock) {
            peerHasRemoteDesc.remove(peerKey)
            peerPendingIce.remove(peerKey)
        }
        peerConnections.remove(peerKey)?.let { pc ->
            runCatching {
                pc.close()
                pc.dispose()
            }
        }
    }

    private fun startLocalVideoTrack() {
        val f = factory ?: return
        if (localVideoTrack != null) return
        val capturer = createCameraCapturer() ?: return
        videoCapturer = capturer
        surfaceTextureHelper = SurfaceTextureHelper.create("VybCaptureThread", eglBase.eglBaseContext)
        localVideoSource = f.createVideoSource(capturer.isScreencast)
        capturer.initialize(surfaceTextureHelper, context.applicationContext, localVideoSource?.capturerObserver)
        try {
            capturer.startCapture(720, 1280, 30)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start camera capture: ${e.message}")
        }
        localVideoTrack = f.createVideoTrack("vyb_video_track", localVideoSource)
        localVideoTrack?.let { track ->
            peerConnections.values.forEach { pc ->
                runCatching { pc.addTrack(track, listOf("vyb_stream")) }
            }
            localRenderer?.let { runCatching { track.addSink(it) } }
        }
    }

    private fun createCameraCapturer(): CameraVideoCapturer? {
        val enumerator = Camera2Enumerator(context.applicationContext)
        val devices = enumerator.deviceNames
        for (name in devices) {
            if (enumerator.isFrontFacing(name)) {
                enumerator.createCapturer(name, null)?.let { return it }
            }
        }
        for (name in devices) {
            if (!enumerator.isFrontFacing(name)) {
                enumerator.createCapturer(name, null)?.let { return it }
            }
        }
        return null
    }

    fun attachLocalRenderer(renderer: SurfaceViewRenderer) {
        if (localRenderer === renderer) return
        localRenderer?.let { prev -> runCatching { localVideoTrack?.removeSink(prev) } }
        localRenderer = renderer
        localVideoTrack?.let { track -> runCatching { track.addSink(renderer) } }
    }

    fun attachRemoteRenderer(renderer: SurfaceViewRenderer, peerKey: String = DEFAULT_PEER_KEY) {
        val previous = remoteRenderers[peerKey]
        val track = _remoteVideoTracks.value[peerKey]
        if (previous === renderer) {
            track?.let { runCatching { it.addSink(renderer) } }
            return
        }
        if (previous != null) {
            runCatching { track?.removeSink(previous) }
        }
        remoteRenderers[peerKey] = renderer
        track?.let { runCatching { it.addSink(renderer) } }
    }

    fun detachRemoteRenderer(renderer: SurfaceViewRenderer, peerKey: String = DEFAULT_PEER_KEY) {
        val current = remoteRenderers[peerKey]
        if (current === renderer) {
            remoteRenderers.remove(peerKey)
        }
        _remoteVideoTracks.value[peerKey]?.let { track ->
            runCatching { track.removeSink(renderer) }
        }
    }

    fun createOffer(peerKey: String = DEFAULT_PEER_KEY, onOfferCreated: (String) -> Unit) {
        val pc = ensurePeerConnection(peerKey) ?: return
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }
        pc.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                desc ?: return
                pc.setLocalDescription(SimpleSdpObserver(), desc)
                onOfferCreated(desc.description)
            }
        }, constraints)
    }

    fun handleRemoteOfferAndCreateAnswer(
        offerSdp: String,
        peerKey: String = DEFAULT_PEER_KEY,
        onAnswerCreated: (String) -> Unit
    ) {
        val pc = ensurePeerConnection(peerKey) ?: return
        val remoteDesc = SessionDescription(SessionDescription.Type.OFFER, offerSdp)
        pc.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                flushPendingRemoteIceCandidates(peerKey, pc)
                val constraints = MediaConstraints().apply {
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
                }
                pc.createAnswer(object : SimpleSdpObserver() {
                    override fun onCreateSuccess(desc: SessionDescription?) {
                        desc ?: return
                        pc.setLocalDescription(SimpleSdpObserver(), desc)
                        onAnswerCreated(desc.description)
                    }
                }, constraints)
            }
        }, remoteDesc)
    }

    fun setRemoteAnswer(answerSdp: String, peerKey: String = DEFAULT_PEER_KEY) {
        val pc = peerConnections[peerKey] ?: return
        val remoteDesc = SessionDescription(SessionDescription.Type.ANSWER, answerSdp)
        pc.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                flushPendingRemoteIceCandidates(peerKey, pc)
            }
        }, remoteDesc)
    }

    fun addRemoteIceCandidate(
        sdpMid: String,
        sdpMLineIndex: Int,
        sdpCandidate: String,
        peerKey: String = DEFAULT_PEER_KEY
    ) {
        val candidate = IceCandidate(sdpMid, sdpMLineIndex, sdpCandidate)
        val pc = peerConnections[peerKey]
        val shouldQueue = synchronized(iceLock) {
            if (peerHasRemoteDesc[peerKey] != true || pc == null) {
                peerPendingIce.getOrPut(peerKey) { mutableListOf() }.add(candidate)
                true
            } else {
                false
            }
        }
        if (!shouldQueue) {
            pc?.addIceCandidate(candidate)
        }
    }

    private fun flushPendingRemoteIceCandidates(peerKey: String, pc: PeerConnection) {
        val queued = synchronized(iceLock) {
            peerHasRemoteDesc[peerKey] = true
            val list = peerPendingIce[peerKey]
            val copy = list?.toList().orEmpty()
            list?.clear()
            copy
        }
        queued.forEach { candidate ->
            pc.addIceCandidate(candidate)
        }
    }

    fun setMuted(muted: Boolean) {
        localAudioTrack?.setEnabled(!muted)
    }

    fun setVideoEnabled(enabled: Boolean) {
        if (enabled && localVideoTrack == null) {
            startLocalVideoTrack()
        } else {
            localVideoTrack?.setEnabled(enabled)
        }
    }

    fun switchCamera(onDone: (Boolean) -> Unit = {}) {
        videoCapturer?.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
            override fun onCameraSwitchDone(isFrontCamera: Boolean) {
                onDone(isFrontCamera)
            }

            override fun onCameraSwitchError(errorDescription: String?) {
                Log.w(TAG, "Switch camera error: $errorDescription")
            }
        })
    }

    fun setSpeakerphone(enabled: Boolean) {
        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = enabled
        } catch (e: Exception) {
            Log.w(TAG, "Speakerphone toggle failed: ${e.message}")
        }
    }

    fun release() {
        try {
            localRenderer?.let { r -> runCatching { localVideoTrack?.removeSink(r) } }
            remoteRenderers.forEach { (key, renderer) ->
                _remoteVideoTracks.value[key]?.let { track -> runCatching { track.removeSink(renderer) } }
            }
            localRenderer = null
            remoteRenderers.clear()
            _remoteVideoTracks.value = emptyMap()
            videoCapturer?.stopCapture()
            videoCapturer?.dispose()
            videoCapturer = null
            surfaceTextureHelper?.dispose()
            surfaceTextureHelper = null
            localVideoTrack?.dispose()
            localVideoTrack = null
            localAudioTrack?.dispose()
            localAudioTrack = null
            localVideoSource?.dispose()
            localVideoSource = null
            localAudioSource?.dispose()
            localAudioSource = null
            peerConnections.values.forEach { pc ->
                runCatching {
                    pc.close()
                    pc.dispose()
                }
            }
            peerConnections.clear()
            factory?.dispose()
            factory = null
            audioManager.mode = AudioManager.MODE_NORMAL
            eglBase.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing WebRTC resources: ${e.message}")
        } finally {
            synchronized(iceLock) {
                peerHasRemoteDesc.clear()
                peerPendingIce.clear()
            }
            isInitialized = false
        }
    }

    private open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(p0: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(p0: String?) {
            Log.w(TAG, "SDP create failure: $p0")
        }
        override fun onSetFailure(p0: String?) {
            Log.w(TAG, "SDP set failure: $p0")
        }
    }
}
