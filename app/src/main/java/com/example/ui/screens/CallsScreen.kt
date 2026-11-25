package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.CallSession
import com.example.data.model.Chat
import com.example.data.model.PublicUser
import com.example.data.model.UiState
import com.example.ui.components.EmptyStateView
import com.example.ui.components.VybAvatar
import com.example.ui.components.formatRelativeTime
import com.example.ui.theme.LocalVybColors
import com.example.ui.theme.VybElectricBlue
import com.example.ui.theme.VybGradientBrush
import com.example.ui.theme.VybMonoTabularStyle
import androidx.compose.ui.text.style.TextOverflow
import com.example.ui.viewmodel.ActiveCallUiState
import com.example.ui.viewmodel.CallHistoryFilter
import com.example.ui.viewmodel.CallViewModel
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import kotlinx.coroutines.launch
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import java.util.Locale

private val MissedCallSoftRed = Color(0xFFF87171)

@Composable
fun CallsScreen(
    callViewModel: CallViewModel,
    currentUser: PublicUser,
    widthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
    activeChats: List<Chat> = emptyList(),
    onRequestCallWithPermissions: (calleeUid: String, calleeName: String, calleeAvatar: String?, isVideo: Boolean) -> Unit,
    onRequestGroupCallWithPermissions: (chat: Chat, isVideo: Boolean) -> Unit = { _, _ -> },
    onJoinGroupCallWithPermissions: (CallSession) -> Unit = {},
    onGoToChats: () -> Unit
) {
    val vybColors = LocalVybColors.current
    val isExpanded = widthSizeClass == WindowWidthSizeClass.Expanded
    val historyState by callViewModel.callHistoryState.collectAsStateWithLifecycle()
    val peerProfiles by callViewModel.peerProfiles.collectAsStateWithLifecycle()
    var selectedFilter by remember { mutableStateOf(CallHistoryFilter.ALL) }

    val allCalls = (historyState as? UiState.Success)?.data ?: emptyList()
    val missedCount = remember(allCalls) { allCalls.count { it.status == "missed" } }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .widthIn(max = if (isExpanded) 1040.dp else 720.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "Llamadas",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Llamadas de voz y video",
                    style = MaterialTheme.typography.bodySmall,
                    color = vybColors.secondaryText
                )
                Spacer(Modifier.height(10.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(CallHistoryFilter.entries) { filter ->
                        val isSelected = selectedFilter == filter
                        val pillLabel = if (filter == CallHistoryFilter.MISSED && missedCount > 0) {
                            "${filter.label} ($missedCount)"
                        } else {
                            filter.label
                        }
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedFilter = filter },
                            leadingIcon = if (filter == CallHistoryFilter.MISSED && missedCount > 0) {
                                {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(vybColors.missedAccent)
                                    )
                                }
                            } else null,
                            label = {
                                Text(
                                    text = pillLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            },
                            colors = if (filter == CallHistoryFilter.MISSED) {
                                FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = vybColors.missedAccent.copy(alpha = 0.14f),
                                    selectedLabelColor = vybColors.missedAccent
                                )
                            } else {
                                FilterChipDefaults.filterChipColors()
                            },
                            modifier = Modifier.testTag(
                                if (filter == CallHistoryFilter.ALL) "calls_filter_all" else "calls_filter_missed"
                            )
                        )
                    }
                }
            }
            }
        }
        HorizontalDivider(thickness = 0.75.dp, color = vybColors.hairlineBorder)

        when (val state = historyState) {
            UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is UiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyStateView(
                        title = "No se pudo cargar el historial",
                        subtitle = state.message,
                        actionLabel = "Reintentar",
                        onAction = { callViewModel.retryCallHistory() },
                        actionTestTag = "calls_retry_button"
                    )
                }
            }
            is UiState.Success -> {
                val filteredCalls = remember(state.data, selectedFilter) {
                    CallViewModel.filterCalls(state.data, selectedFilter)
                }
                if (state.data.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyStateView(
                            title = "Sin llamadas todavía",
                            subtitle = "Inicia una llamada de voz o video desde cualquiera de tus chats.",
                            actionLabel = "Ir a Chats",
                            onAction = onGoToChats
                        )
                    }
                } else if (filteredCalls.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyStateView(
                            title = "Sin llamadas perdidas",
                            subtitle = "No tienes llamadas perdidas registradas en tu historial."
                        )
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        LazyColumn(
                            modifier = Modifier
                                .widthIn(max = if (isExpanded) 1040.dp else 720.dp)
                                .fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 12.dp,
                                bottom = 110.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (isExpanded) {
                                items(filteredCalls.chunked(2), key = { it.first().id }) { rowCalls ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        rowCalls.forEach { call ->
                                            val peerUid = if (call.callerId == currentUser.userId) call.calleeId else call.callerId
                                            val resolvedGroupChat = if (call.groupCall) {
                                                activeChats.firstOrNull { it.id == call.chatId }
                                                    ?: Chat(
                                                        id = call.chatId ?: call.calleeId,
                                                        title = call.groupTitle ?: call.calleeName,
                                                        participantUids = call.participantUids,
                                                        chatType = "group"
                                                    )
                                            } else null
                                            CallHistoryCard(
                                                call = call,
                                                currentUid = currentUser.userId,
                                                livePeer = if (call.groupCall) null else peerProfiles[peerUid],
                                                onCallAudio = { uid, peerName, peerAvatar ->
                                                    if (resolvedGroupChat != null) {
                                                        onRequestGroupCallWithPermissions(resolvedGroupChat, false)
                                                    } else {
                                                        onRequestCallWithPermissions(uid, peerName, peerAvatar, false)
                                                    }
                                                },
                                                onCallVideo = { uid, peerName, peerAvatar ->
                                                    if (resolvedGroupChat != null) {
                                                        onRequestGroupCallWithPermissions(resolvedGroupChat, true)
                                                    } else {
                                                        onRequestCallWithPermissions(uid, peerName, peerAvatar, true)
                                                    }
                                                },
                                                onJoinGroupCall = { onJoinGroupCallWithPermissions(call) },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        if (rowCalls.size == 1) {
                                            Spacer(Modifier.weight(1f))
                                        }
                                    }
                                }
                            } else {
                                items(filteredCalls, key = { it.id }) { call ->
                                    val peerUid = if (call.callerId == currentUser.userId) call.calleeId else call.callerId
                                    val resolvedGroupChat = if (call.groupCall) {
                                        activeChats.firstOrNull { it.id == call.chatId }
                                            ?: Chat(
                                                id = call.chatId ?: call.calleeId,
                                                title = call.groupTitle ?: call.calleeName,
                                                participantUids = call.participantUids,
                                                chatType = "group"
                                            )
                                    } else null
                                    CallHistoryCard(
                                        call = call,
                                        currentUid = currentUser.userId,
                                        livePeer = if (call.groupCall) null else peerProfiles[peerUid],
                                        onCallAudio = { uid, peerName, peerAvatar ->
                                            if (resolvedGroupChat != null) {
                                                onRequestGroupCallWithPermissions(resolvedGroupChat, false)
                                            } else {
                                                onRequestCallWithPermissions(uid, peerName, peerAvatar, false)
                                            }
                                        },
                                        onCallVideo = { uid, peerName, peerAvatar ->
                                            if (resolvedGroupChat != null) {
                                                onRequestGroupCallWithPermissions(resolvedGroupChat, true)
                                            } else {
                                                onRequestCallWithPermissions(uid, peerName, peerAvatar, true)
                                            }
                                        },
                                        onJoinGroupCall = { onJoinGroupCallWithPermissions(call) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CallHistoryCard(
    call: CallSession,
    currentUid: String,
    livePeer: PublicUser?,
    onCallAudio: (String, String, String?) -> Unit,
    onCallVideo: (String, String, String?) -> Unit,
    onJoinGroupCall: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val vybColors = LocalVybColors.current
    val isOutgoing = call.callerId == currentUid
    val peerUid = if (isOutgoing) call.calleeId else call.callerId
    val fallbackName = if (call.groupCall) {
        call.groupTitle?.takeIf { it.isNotBlank() } ?: call.calleeName
    } else if (isOutgoing) {
        call.calleeName
    } else {
        call.callerName
    }
    val fallbackAvatar = if (call.groupCall) null else if (isOutgoing) call.calleeAvatar else call.callerAvatar
    val peerName = livePeer?.displayName?.takeIf { it.isNotBlank() } ?: fallbackName
    val peerAvatar = CallViewModel.resolvePeerAvatar(livePeer, fallbackAvatar)
    val isMissed = call.status == "missed"
    val isMissedOrRejected = isMissed || call.status == "rejected"
    val isJoinableGroup = call.groupCall && call.status in listOf("ringing", "active")

    val directionIcon = when {
        isMissedOrRejected -> Icons.AutoMirrored.Filled.CallMissed
        isOutgoing -> Icons.AutoMirrored.Filled.CallMade
        else -> Icons.AutoMirrored.Filled.CallReceived
    }

    val statusText = when (call.status) {
        "ringing" -> "Sonando..."
        "active" -> "En curso"
        "missed" -> "Perdida"
        "rejected" -> "Rechazada"
        else -> {
            if (call.durationSeconds > 0L) {
                String.format(
                    Locale.getDefault(),
                    "%02d:%02d",
                    call.durationSeconds / 60L,
                    call.durationSeconds % 60L
                )
            } else {
                "Finalizada"
            }
        }
    }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = vybColors.cardElevationDp),
        border = BorderStroke(0.75.dp, vybColors.hairlineBorder),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                VybAvatar(
                    avatarUrl = peerAvatar,
                    displayName = peerName,
                    size = 48.dp,
                    isVerified = livePeer?.verified == true
                )
                if (call.groupCall) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surface),
                        modifier = Modifier.size(20.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Group,
                                contentDescription = "Llamada grupal",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = peerName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = directionIcon,
                        contentDescription = null,
                        tint = if (isMissedOrRejected) vybColors.missedAccent else vybColors.sinpeEmerald,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    val prefixText = if (call.groupCall) {
                        "Grupo (${call.participantUids.size}) · ${if (call.callType == "video") "Video" else "Audio"} · "
                    } else {
                        "${if (call.callType == "video") "Video" else "Audio"} · "
                    }
                    Text(
                        text = prefixText,
                        style = MaterialTheme.typography.bodySmall.merge(VybMonoTabularStyle),
                        color = vybColors.secondaryText
                    )
                    if (isMissed) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = vybColors.missedAccent.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.labelSmall.merge(VybMonoTabularStyle),
                                color = vybColors.missedAccent,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .padding(horizontal = 6.dp, vertical = 1.dp)
                                    .testTag("call_missed_label_${call.id}")
                            )
                        }
                    } else {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall.merge(VybMonoTabularStyle),
                            color = if (call.status == "rejected") vybColors.missedAccent else vybColors.secondaryText
                        )
                    }
                    Text(
                        text = " · ${formatRelativeTime(call.createdAt)}",
                        style = MaterialTheme.typography.bodySmall.merge(VybMonoTabularStyle),
                        color = vybColors.secondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (isJoinableGroup) {
                Button(
                    onClick = onJoinGroupCall,
                    colors = ButtonDefaults.buttonColors(containerColor = vybColors.sinpeEmerald),
                    shape = RoundedCornerShape(999.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("call_history_join_${call.id}")
                ) {
                    Text("Unirse", color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            } else {
                IconButton(
                    onClick = { onCallAudio(peerUid, peerName, peerAvatar) },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Filled.Call, contentDescription = "Llamar por voz", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(
                    onClick = { onCallVideo(peerUid, peerName, peerAvatar) },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Filled.Videocam, contentDescription = "Llamar por video", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun CallModal(
    activeCall: ActiveCallUiState,
    currentUserId: String,
    callViewModel: CallViewModel
) {
    val session = activeCall.session ?: return
    val context = LocalContext.current
    val peerProfiles by callViewModel.peerProfiles.collectAsStateWithLifecycle()
    val remoteVideoTracks by callViewModel.remoteVideoTracks.collectAsStateWithLifecycle()
    val isVideoCall = session.callType == "video"
    val isGroup = session.groupCall
    val isOutgoing = session.callerId == currentUserId
    val peerUid = if (isOutgoing) session.calleeId else session.callerId
    val livePeer = if (isGroup) null else peerProfiles[peerUid]
    val peerName = if (isGroup) {
        session.groupTitle?.takeIf { it.isNotBlank() } ?: session.calleeName
    } else {
        livePeer?.displayName?.takeIf { it.isNotBlank() }
            ?: if (isOutgoing) session.calleeName else session.callerName
    }
    val fallbackCallAvatar = if (isGroup) null else if (isOutgoing) session.calleeAvatar else session.callerAvatar
    val peerAvatar = CallViewModel.resolvePeerAvatar(livePeer, fallbackCallAvatar)

    var pendingAccept by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grantedMap ->
        val audioOk = grantedMap[Manifest.permission.RECORD_AUDIO] == true
        val videoOk = !isVideoCall || grantedMap[Manifest.permission.CAMERA] == true
        if (pendingAccept && audioOk && videoOk) {
            pendingAccept = false
            callViewModel.acceptIncomingCall(context)
        } else {
            pendingAccept = false
        }
    }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val dialogView = LocalView.current
        SideEffect {
            val window = (dialogView.parent as? DialogWindowProvider)?.window
            if (window != null) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF070A14))
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            if (isGroup && !activeCall.isIncoming) {
                val eglContext = callViewModel.getEglContext()
                val otherPeers = remember(session.participantUids, session.activeParticipantUids, currentUserId) {
                    val activeSet = session.activeParticipantUids.toSet()
                    (session.participantUids - currentUserId)
                        .distinct()
                        .sortedByDescending { it in activeSet }
                }
                val activeCount = remember(session.activeParticipantUids, currentUserId) {
                    (session.activeParticipantUids + currentUserId).distinct().size
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 148.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color.White.copy(alpha = 0.12f),
                        modifier = Modifier.testTag("group_call_status_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.Group, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Text(
                                text = "$peerName · $activeCount en sala · " + String.format(
                                    Locale.getDefault(),
                                    "%02d:%02d",
                                    activeCall.durationSeconds / 60L,
                                    activeCall.durationSeconds % 60L
                                ),
                                style = MaterialTheme.typography.labelLarge.merge(VybMonoTabularStyle),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(if (otherPeers.size <= 1) 1 else 2),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .testTag("group_call_participants_grid"),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        gridItems(otherPeers, key = { it }) { memberUid ->
                            val memberProfile = peerProfiles[memberUid]
                            val memberName = memberProfile?.displayName?.takeIf { it.isNotBlank() } ?: "Integrante"
                            val memberAvatar = memberProfile?.avatarUrl
                            val isMemberActive = memberUid in session.activeParticipantUids
                            val isMemberDeclined = memberUid in session.declinedUids
                            val hasVideoTrack = isVideoCall && activeCall.isVideoEnabled &&
                                remoteVideoTracks[memberUid] != null && eglContext != null

                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Color(0xFF111827),
                                border = BorderStroke(
                                    1.dp,
                                    if (isMemberActive) Color(0xFF10B981).copy(alpha = 0.55f) else Color.White.copy(alpha = 0.12f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(if (otherPeers.size <= 2) 0.9f else 0.82f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (hasVideoTrack && eglContext != null) {
                                        var peerRenderer by remember(memberUid) { mutableStateOf<SurfaceViewRenderer?>(null) }
                                        AndroidView(
                                            factory = { ctx ->
                                                SurfaceViewRenderer(ctx).apply {
                                                    init(eglContext, null)
                                                    setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                                                    setEnableHardwareScaler(true)
                                                    peerRenderer = this
                                                    callViewModel.attachRemoteRendererForPeer(memberUid, this)
                                                }
                                            },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        DisposableEffect(memberUid) {
                                            onDispose {
                                                peerRenderer?.let { renderer ->
                                                    callViewModel.detachRemoteRendererForPeer(memberUid, renderer)
                                                    runCatching { renderer.release() }
                                                }
                                            }
                                        }
                                    } else {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center,
                                            modifier = Modifier.padding(12.dp)
                                        ) {
                                            VybAvatar(
                                                avatarUrl = memberAvatar,
                                                displayName = memberName,
                                                size = 64.dp,
                                                showGradientRing = isMemberActive
                                            )
                                            Spacer(Modifier.height(10.dp))
                                            Text(
                                                text = memberName,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = when {
                                                    isMemberActive -> "En llamada"
                                                    isMemberDeclined -> "Rechazada"
                                                    else -> "Invitado..."
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isMemberActive) Color(0xFF34D399) else Color.White.copy(alpha = 0.65f)
                                            )
                                        }
                                    }

                                    if (hasVideoTrack) {
                                        Surface(
                                            shape = RoundedCornerShape(999.dp),
                                            color = Color.Black.copy(alpha = 0.6f),
                                            modifier = Modifier
                                                .align(Alignment.BottomStart)
                                                .padding(10.dp)
                                        ) {
                                            Text(
                                                text = memberName,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (isVideoCall && activeCall.isVideoEnabled && eglContext != null) {
                    var localView by remember { mutableStateOf<SurfaceViewRenderer?>(null) }
                    AndroidView(
                        factory = { ctx ->
                            SurfaceViewRenderer(ctx).apply {
                                init(eglContext, null)
                                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                                setEnableHardwareScaler(true)
                                setZOrderMediaOverlay(true)
                                localView = this
                                callViewModel.attachLocalRenderer(this)
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                            .size(width = 96.dp, height = 136.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .graphicsLayer {
                                scaleX = if (activeCall.isFrontCamera) -1f else 1f
                            }
                    )
                    DisposableEffect(Unit) {
                        onDispose {
                            runCatching { localView?.release() }
                        }
                    }
                }
            } else {
                // 1:1 Video renderers when call is active video
                if (isVideoCall && activeCall.isVideoEnabled && !activeCall.isIncoming) {
                    val eglContext = callViewModel.getEglContext()
                    if (eglContext != null) {
                        var localView by remember { mutableStateOf<SurfaceViewRenderer?>(null) }
                        var remoteView by remember { mutableStateOf<SurfaceViewRenderer?>(null) }

                        AndroidView(
                            factory = { ctx ->
                                SurfaceViewRenderer(ctx).apply {
                                    init(eglContext, null)
                                    setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                                    setEnableHardwareScaler(true)
                                    remoteView = this
                                    callViewModel.attachRenderers(localView, this)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        AndroidView(
                            factory = { ctx ->
                                SurfaceViewRenderer(ctx).apply {
                                    init(eglContext, null)
                                    setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                                    setEnableHardwareScaler(true)
                                    setZOrderMediaOverlay(true)
                                    localView = this
                                    callViewModel.attachRenderers(this, remoteView)
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp)
                                .size(width = 112.dp, height = 160.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .graphicsLayer {
                                    scaleX = if (activeCall.isFrontCamera) -1f else 1f
                                }
                        )

                        DisposableEffect(Unit) {
                            onDispose {
                                try {
                                    localView?.release()
                                    remoteView?.release()
                                } catch (_: Exception) {
                                }
                            }
                        }
                    }
                }

                // Center peer card when audio or ringing
                if (!isVideoCall || activeCall.isIncoming || !activeCall.isConnected) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val pulseTransition = rememberInfiniteTransition(label = "call_ring_pulse")
                        val ringScale by pulseTransition.animateFloat(
                            initialValue = 1f,
                            targetValue = if (!activeCall.isConnected) 1.16f else 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(900),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "ring_scale"
                        )
                        val ringAlpha by pulseTransition.animateFloat(
                            initialValue = 0.45f,
                            targetValue = 0.1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(900),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "ring_alpha"
                        )
                        Box(contentAlignment = Alignment.Center) {
                            if (!activeCall.isConnected) {
                                Box(
                                    modifier = Modifier
                                        .size(126.dp)
                                        .graphicsLayer {
                                            scaleX = ringScale
                                            scaleY = ringScale
                                            alpha = ringAlpha
                                        }
                                        .clip(CircleShape)
                                        .background(VybGradientBrush)
                                )
                            }
                            VybAvatar(
                                avatarUrl = peerAvatar,
                                displayName = peerName,
                                size = 110.dp,
                                showGradientRing = true
                            )
                        }
                        Spacer(Modifier.height(20.dp))
                        Text(
                            text = peerName,
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        val statusLabel = when {
                            activeCall.isIncoming && isGroup ->
                                "Llamada grupal ${if (isVideoCall) "de video" else "de voz"} · ${session.callerName}"
                            activeCall.isIncoming ->
                                "Llamada ${if (isVideoCall) "de video" else "de voz"} entrante..."
                            activeCall.isConnected -> String.format(
                                Locale.getDefault(),
                                "%02d:%02d",
                                activeCall.durationSeconds / 60L,
                                activeCall.durationSeconds % 60L
                            )
                            else -> "Llamando..."
                        }
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.titleMedium.merge(VybMonoTabularStyle),
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        activeCall.errorMessage?.let { err ->
                            Spacer(Modifier.height(10.dp))
                            Text(err, style = MaterialTheme.typography.bodySmall, color = Color(0xFFFCA5A5))
                        }
                    }
                } else {
                    // Connected video timer pill
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color.Black.copy(alpha = 0.55f),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "$peerName · " + String.format(
                                Locale.getDefault(),
                                "%02d:%02d",
                                activeCall.durationSeconds / 60L,
                                activeCall.durationSeconds % 60L
                            ),
                            style = MaterialTheme.typography.labelLarge.merge(VybMonoTabularStyle),
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            // Elevated floating call dock comfortably above bottom gesture bar
            Surface(
                shape = RoundedCornerShape(34.dp),
                color = Color(0xCC0F172A),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                tonalElevation = 8.dp,
                shadowElevation = 10.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = 460.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 44.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (activeCall.isIncoming) {
                        IconButton(
                            onClick = { callViewModel.rejectOrEndCall() },
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEF4444))
                                .testTag("call_reject_button")
                        ) {
                            Icon(Icons.Filled.CallEnd, contentDescription = "Rechazar llamada", tint = Color.White)
                        }

                        IconButton(
                            onClick = {
                                val needed = buildList {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                                        add(Manifest.permission.RECORD_AUDIO)
                                    }
                                    if (isVideoCall && ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                                        add(Manifest.permission.CAMERA)
                                    }
                                }
                                if (needed.isEmpty()) {
                                    callViewModel.acceptIncomingCall(context)
                                } else {
                                    pendingAccept = true
                                    permissionLauncher.launch(needed.toTypedArray())
                                }
                            },
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981))
                                .testTag("call_accept_button")
                        ) {
                            Icon(Icons.Filled.Call, contentDescription = "Contestar llamada", tint = Color.White)
                        }
                    } else {
                        CallControlCircle(
                            icon = if (activeCall.isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                            label = "Silenciar",
                            active = activeCall.isMuted,
                            onClick = { callViewModel.toggleMute() }
                        )
                        if (isVideoCall) {
                            CallControlCircle(
                                icon = if (activeCall.isVideoEnabled) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
                                label = "Cámara",
                                active = !activeCall.isVideoEnabled,
                                onClick = { callViewModel.toggleVideo() }
                            )
                            CallControlCircle(
                                icon = Icons.Filled.Cameraswitch,
                                label = "Rotar",
                                active = false,
                                onClick = { callViewModel.switchCamera() }
                            )
                        }
                        CallControlCircle(
                            icon = Icons.AutoMirrored.Filled.VolumeUp,
                            label = "Altavoz",
                            active = activeCall.isSpeakerOn,
                            onClick = { callViewModel.toggleSpeaker() }
                        )
                        IconButton(
                            onClick = { callViewModel.rejectOrEndCall() },
                            modifier = Modifier
                                .size(60.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEF4444))
                                .testTag("call_hangup_button")
                        ) {
                            Icon(Icons.Filled.CallEnd, contentDescription = "Colgar", tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CallControlCircle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = {
                scope.launch {
                    scale.snapTo(0.86f)
                    scale.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    )
                }
                onClick()
            },
            modifier = Modifier
                .size(52.dp)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                }
                .clip(CircleShape)
                .background(if (active) Color.White else Color.White.copy(alpha = 0.18f))
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (active) Color.Black else Color.White
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.88f))
    }
}
