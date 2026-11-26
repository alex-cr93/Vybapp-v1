package com.example.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MarkChatUnread
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.core.BitmapMemoryCache
import com.example.core.LocationHelper
import com.example.core.MediaUtils
import com.example.core.VoicePlaybackManager
import com.example.core.VoiceRecorderSession
import com.example.core.formatVoiceDuration
import com.example.reactions.ReactionBadgesView
import com.example.reactions.ReactionCatalog
import com.example.reactions.ReactionFirestoreHelper
import com.example.reactions.ReactionFloatingBarController
import com.example.data.model.AiModelChoice
import com.example.data.model.CallSession
import com.example.data.model.Chat
import com.example.data.model.ChatMessage
import com.example.data.model.ChatRequest
import com.example.data.model.PrivateUser
import com.example.data.model.PublicUser
import com.example.data.model.SavedContact
import com.example.data.model.SinpeProfile
import com.example.data.model.SinpeVisibility
import com.example.data.model.StructuredChatSummary
import com.example.data.model.UiState
import com.example.ui.components.EmptyStateView
import com.example.ui.components.SinpeCard
import com.example.ui.components.UserStatusBadge
import com.example.ui.components.VybAvatar
import com.example.ui.components.VybBombIndicatorRow
import com.example.ui.components.VybBombMessageContainer
import com.example.ui.components.VybBombVisualStage
import com.example.ui.components.VybLogoBadge
import com.example.ui.components.VybPrimaryGradientButton
import com.example.ui.components.ZoomableMediaLightbox
import com.example.ui.components.formatMessageTime
import com.example.ui.components.formatRelativeTime
import com.example.ui.components.resolveBombUiRemainingSeconds
import com.example.ui.components.resolveBombVisualStage
import com.example.ui.theme.LocalVybColors
import com.example.ui.theme.VybElectricBlue
import com.example.ui.theme.VybGradientBrush
import com.example.ui.theme.VybMonoTabularStyle
import com.example.ui.theme.VybPink
import com.example.ui.theme.VybSenderBubbleBrush
import com.example.ui.theme.VybViolet
import com.example.ui.viewmodel.AssistantViewModel
import com.example.ui.viewmodel.ChatFilter
import com.example.ui.viewmodel.ChatViewModel
import com.example.ui.viewmodel.FeedViewModel
import com.example.ui.viewmodel.MessageDeliveryStatus
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen(
    chatViewModel: ChatViewModel,
    feedViewModel: FeedViewModel,
    assistantViewModel: AssistantViewModel,
    aiModelChoice: AiModelChoice,
    onSelectModelChoice: (AiModelChoice) -> Unit,
    privateAreaLabel: String?,
    currentUser: PublicUser,
    privateUser: PrivateUser?,
    blockedUids: List<String> = privateUser?.blockedUids ?: emptyList(),
    onBlockUser: (uid: String, displayName: String) -> Unit = { _, _ -> },
    onUnblockUser: (uid: String) -> Unit = {},
    onBlockByIdentifier: (query: String, onResult: (Boolean, String) -> Unit) -> Unit = { _, _ -> },
    loadFullMediaFile: suspend (String) -> File?,
    onOpenAssistant: (tab: AssistantToolTab, prompt: String?, draft: String, chat: Chat?, messages: List<ChatMessage>) -> Unit,
    onStartCall: (calleeUid: String, calleeName: String, calleeAvatar: String?, isVideo: Boolean) -> Unit,
    activeGroupCallByChatId: Map<String, CallSession> = emptyMap(),
    onStartGroupCall: (chat: Chat, isVideo: Boolean) -> Unit = { _, _ -> },
    onJoinGroupCall: (CallSession) -> Unit = {},
    widthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact
) {
    val vybColors = LocalVybColors.current
    val selectedChat by chatViewModel.selectedChat.collectAsStateWithLifecycle()
    val messagesState by chatViewModel.messagesState.collectAsStateWithLifecycle()
    var inspectedContact by remember { mutableStateOf<GroupMemberSelection?>(null) }

    val readReceiptsEnabled = privateUser?.readReceipts != false
    val isExpanded = widthSizeClass == WindowWidthSizeClass.Expanded

    val openContactProfile: (String, String, String?, String?) -> Unit = { uid, name, username, avatar ->
        if (uid.isNotBlank() && uid != currentUser.userId) {
            inspectedContact = GroupMemberSelection(
                uid = uid,
                displayName = name,
                username = username.orEmpty(),
                avatarUrl = avatar
            )
        }
    }

    if (isExpanded) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .width(380.dp)
                    .fillMaxHeight()
            ) {
                ChatListView(
                    chatViewModel = chatViewModel,
                    currentUser = currentUser,
                    privateUser = privateUser,
                    readReceiptsEnabled = readReceiptsEnabled,
                    selectedChatId = selectedChat?.id,
                    blockedUids = blockedUids,
                    onBlockUser = onBlockUser,
                    onUnblockUser = onUnblockUser,
                    onBlockByIdentifier = onBlockByIdentifier,
                    onOpenContactProfile = openContactProfile,
                    isMediumScreen = false
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(0.75.dp)
                    .background(vybColors.hairlineBorder)
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(vybColors.chatCanvas),
                contentAlignment = Alignment.Center
            ) {
                val chat = selectedChat
                if (chat != null) {
                    BackHandler { chatViewModel.openChat(null) }
                    ChatRoomScreen(
                        chat = chat,
                        messagesState = messagesState,
                        currentUser = currentUser,
                        privateUser = privateUser,
                        chatViewModel = chatViewModel,
                        assistantViewModel = assistantViewModel,
                        aiModelChoice = aiModelChoice,
                        onSelectModelChoice = onSelectModelChoice,
                        privateAreaLabel = privateAreaLabel,
                        loadFullMediaFile = loadFullMediaFile,
                        onBack = { chatViewModel.openChat(null) },
                        onStartCall = onStartCall,
                        activeGroupCall = activeGroupCallByChatId[chat.id],
                        onStartGroupCall = onStartGroupCall,
                        onJoinGroupCall = onJoinGroupCall,
                        onOpenContactProfile = openContactProfile,
                        showBackButton = false
                    )
                } else {
                    EmptyStateView(
                        title = "Selecciona un chat",
                        subtitle = "Elige una conversación de la lista o busca contactos para empezar a vibrar.",
                        modifier = Modifier.padding(24.dp)
                    )
                }
            }
        }
    } else {
        AnimatedContent(
            targetState = selectedChat != null,
            transitionSpec = {
                if (targetState) {
                    (fadeIn(tween(140)) + slideInHorizontally(tween(160)) { it / 10 })
                        .togetherWith(fadeOut(tween(110)))
                } else {
                    fadeIn(tween(140))
                        .togetherWith(fadeOut(tween(110)))
                }
            },
            label = "chats_list_room_transition"
        ) { inRoom ->
            val chat = selectedChat
            if (inRoom && chat != null) {
                BackHandler { chatViewModel.openChat(null) }
                ChatRoomScreen(
                    chat = chat,
                    messagesState = messagesState,
                    currentUser = currentUser,
                    privateUser = privateUser,
                    chatViewModel = chatViewModel,
                    assistantViewModel = assistantViewModel,
                    aiModelChoice = aiModelChoice,
                    onSelectModelChoice = onSelectModelChoice,
                    privateAreaLabel = privateAreaLabel,
                    loadFullMediaFile = loadFullMediaFile,
                    onBack = { chatViewModel.openChat(null) },
                    onStartCall = onStartCall,
                    activeGroupCall = activeGroupCallByChatId[chat.id],
                    onStartGroupCall = onStartGroupCall,
                    onJoinGroupCall = onJoinGroupCall,
                    onOpenContactProfile = openContactProfile,
                    showBackButton = true
                )
            } else {
                ChatListView(
                    chatViewModel = chatViewModel,
                    currentUser = currentUser,
                    privateUser = privateUser,
                    readReceiptsEnabled = readReceiptsEnabled,
                    selectedChatId = null,
                    blockedUids = blockedUids,
                    onBlockUser = onBlockUser,
                    onUnblockUser = onUnblockUser,
                    onBlockByIdentifier = onBlockByIdentifier,
                    onOpenContactProfile = openContactProfile,
                    isMediumScreen = widthSizeClass == WindowWidthSizeClass.Medium
                )
            }
        }
    }

    inspectedContact?.let { target ->
        ContactProfileSheet(
            contactUid = target.uid,
            fallbackName = target.displayName,
            fallbackUsername = target.username.takeIf { it.isNotBlank() },
            fallbackAvatar = target.avatarUrl,
            currentUser = currentUser,
            chatViewModel = chatViewModel,
            feedViewModel = feedViewModel,
            isBlocked = target.uid in blockedUids,
            onToggleBlock = { uid, name, currentlyBlocked ->
                if (currentlyBlocked) {
                    onUnblockUser(uid)
                } else {
                    onBlockUser(uid, name)
                }
            },
            onDismiss = { inspectedContact = null },
            onOpenDirectChat = { uid, name, avatar ->
                chatViewModel.startDirectChat(
                    currentUser = currentUser,
                    peerUid = uid,
                    peerName = name,
                    peerAvatar = avatar
                )
            },
            onStartCall = onStartCall
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatListView(
    chatViewModel: ChatViewModel,
    currentUser: PublicUser,
    privateUser: PrivateUser? = null,
    readReceiptsEnabled: Boolean,
    selectedChatId: String? = null,
    blockedUids: List<String> = emptyList(),
    onBlockUser: (uid: String, displayName: String) -> Unit = { _, _ -> },
    onUnblockUser: (uid: String) -> Unit = {},
    onBlockByIdentifier: (query: String, onResult: (Boolean, String) -> Unit) -> Unit = { _, _ -> },
    onOpenContactProfile: (uid: String, name: String, username: String?, avatar: String?) -> Unit = { _, _, _, _ -> },
    isMediumScreen: Boolean = false
) {
    val vybColors = LocalVybColors.current
    val haptic = LocalHapticFeedback.current
    val chatsState by chatViewModel.filteredChatsState.collectAsStateWithLifecycle()
    val requestsState by chatViewModel.chatRequestsState.collectAsStateWithLifecycle()
    val incomingPending by chatViewModel.incomingPendingRequests.collectAsStateWithLifecycle()
    val busyRequestIds by chatViewModel.busyRequestIds.collectAsStateWithLifecycle()
    val searchQuery by chatViewModel.chatSearch.collectAsStateWithLifecycle()
    val chatFilter by chatViewModel.chatFilter.collectAsStateWithLifecycle()
    val contacts by chatViewModel.contactsState.collectAsStateWithLifecycle()
    val peerProfiles by chatViewModel.peerProfiles.collectAsStateWithLifecycle()

    var showContactsSheet by remember { mutableStateOf(false) }
    var contextMenuChat by remember { mutableStateOf<Chat?>(null) }
    var blockTargetChat by remember { mutableStateOf<Chat?>(null) }
    var deleteDirectConfirmChat by remember { mutableStateOf<Chat?>(null) }
    var leaveGroupConfirmChat by remember { mutableStateOf<Chat?>(null) }
    var deleteGroupConfirmChat by remember { mutableStateOf<Chat?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .then(if (isMediumScreen) Modifier.widthIn(max = 720.dp) else Modifier)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Chats",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = { showContactsSheet = true },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("chats_contacts_button")
                        ) {
                            Icon(Icons.Filled.Contacts, contentDescription = "Contactos, solicitudes y grupos")
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { chatViewModel.setChatSearch(it) },
                        placeholder = { Text("Buscar conversaciones o solicitudes...") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("chats_search_input")
                    )
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ChatFilter.entries) { f ->
                            val chipLabel = if (f == ChatFilter.REQUESTS && incomingPending.isNotEmpty()) {
                                "${f.label} (${incomingPending.size})"
                            } else {
                                f.label
                            }
                            FilterChip(
                                selected = chatFilter == f,
                                onClick = { chatViewModel.setChatFilter(f) },
                                label = { Text(chipLabel, style = MaterialTheme.typography.labelMedium) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("chat_filter_${f.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        }
        HorizontalDivider(thickness = 0.75.dp, color = vybColors.hairlineBorder)

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxHeight()
                .then(if (isMediumScreen) Modifier.widthIn(max = 720.dp) else Modifier)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 14.dp,
                bottom = 112.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (chatFilter == ChatFilter.REQUESTS) {
                when (val reqState = requestsState) {
                    UiState.Loading -> {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                    is UiState.Error -> {
                        item {
                            EmptyStateView(
                                title = "No se pudieron cargar las solicitudes",
                                subtitle = reqState.message
                            )
                        }
                    }
                    is UiState.Success -> {
                        val q = searchQuery.trim().lowercase()
                        val filteredReqs = reqState.data.filter { req ->
                            q.isBlank() ||
                                req.fromName.lowercase().contains(q) ||
                                req.fromUsername.lowercase().contains(q) ||
                                req.toName.lowercase().contains(q) ||
                                req.toUsername.lowercase().contains(q)
                        }
                        val received = filteredReqs.filter { it.toUid == currentUser.userId }
                        val sent = filteredReqs.filter { it.fromUid == currentUser.userId }

                        if (filteredReqs.isEmpty()) {
                            item {
                                EmptyStateView(
                                    title = "No tienes solicitudes pendientes",
                                    subtitle = "Busca amigos por @usuario o teléfono +506 para enviarles una solicitud de chat.",
                                    actionLabel = "Buscar contactos",
                                    onAction = { showContactsSheet = true }
                                )
                            }
                        } else {
                            if (received.isNotEmpty()) {
                                item(key = "header_received_requests") {
                                    Text(
                                        text = "Recibidas (${received.size})",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = vybColors.secondaryText,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                                items(received, key = { "in_${it.id}" }) { req ->
                                    ChatRequestCard(
                                        request = req,
                                        currentUserId = currentUser.userId,
                                        isBusy = req.id in busyRequestIds,
                                        onAccept = { chatViewModel.acceptRequest(req, currentUser) },
                                        onReject = { chatViewModel.rejectRequest(req) },
                                        onDelete = { chatViewModel.deleteRequest(req) },
                                        onOpenChat = { chatId -> chatViewModel.openChat(chatId, readReceiptsEnabled) },
                                        modifier = Modifier.animateItem()
                                    )
                                }
                            }
                            if (sent.isNotEmpty()) {
                                item(key = "header_sent_requests") {
                                    Text(
                                        text = "Enviadas (${sent.size})",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = vybColors.secondaryText,
                                        modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 2.dp)
                                    )
                                }
                                items(sent, key = { "out_${it.id}" }) { req ->
                                    ChatRequestCard(
                                        request = req,
                                        currentUserId = currentUser.userId,
                                        isBusy = req.id in busyRequestIds,
                                        onAccept = {},
                                        onReject = {},
                                        onDelete = { chatViewModel.deleteRequest(req) },
                                        onOpenChat = { chatId -> chatViewModel.openChat(chatId, readReceiptsEnabled) },
                                        modifier = Modifier.animateItem()
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Quick incoming requests preview banner
                if (incomingPending.isNotEmpty()) {
                    item(key = "incoming_requests_banner") {
                        val firstReq = incomingPending.first()
                        Card(
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = vybColors.cardElevationDp),
                            border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("incoming_requests_banner")
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Filled.MarkChatUnread,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = if (incomingPending.size == 1) "1 solicitud de chat" else "${incomingPending.size} solicitudes de chat",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    TextButton(
                                        onClick = { chatViewModel.setChatFilter(ChatFilter.REQUESTS) },
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) {
                                        Text("Ver todas")
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    VybAvatar(
                                        avatarUrl = firstReq.fromAvatarUrl,
                                        displayName = firstReq.fromName,
                                        size = 40.dp
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(firstReq.fromName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            "@${firstReq.fromUsername} quiere chatear contigo",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = vybColors.secondaryText,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = { chatViewModel.acceptRequest(firstReq, currentUser) },
                                        enabled = firstReq.id !in busyRequestIds,
                                        colors = ButtonDefaults.buttonColors(containerColor = vybColors.sinpeEmerald),
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .testTag("banner_accept_request")
                                    ) {
                                        Text("Aceptar", color = Color.White, style = MaterialTheme.typography.labelLarge)
                                    }
                                    OutlinedButton(
                                        onClick = { chatViewModel.rejectRequest(firstReq) },
                                        enabled = firstReq.id !in busyRequestIds,
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .testTag("banner_reject_request")
                                    ) {
                                        Text("Rechazar")
                                    }
                                    IconButton(
                                        onClick = { chatViewModel.deleteRequest(firstReq) },
                                        enabled = firstReq.id !in busyRequestIds,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("banner_delete_request")
                                    ) {
                                        Icon(
                                            Icons.Filled.DeleteOutline,
                                            contentDescription = "Eliminar solicitud",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                when (val state = chatsState) {
                    UiState.Loading -> {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                    is UiState.Error -> {
                        item {
                            EmptyStateView(
                                title = "Error al cargar chats",
                                subtitle = state.message
                            )
                        }
                    }
                    is UiState.Success -> {
                        if (state.data.isEmpty()) {
                            item {
                                EmptyStateView(
                                    title = "Aún no tienes chats activos",
                                    subtitle = "Busca a tus amigos por @usuario o número +506 para chatear al instante o cobrar por SINPE.",
                                    actionLabel = "Buscar contactos",
                                    onAction = { showContactsSheet = true }
                                )
                            }
                        } else {
                            items(state.data, key = { it.id }) { chat ->
                                val peerUid = if (chat.chatType == "direct") {
                                    ChatViewModel.resolvePeerUid(chat, currentUser.userId)
                                } else {
                                    null
                                }
                                val livePeer = peerUid?.let { peerProfiles[it] }
                                val displayTitle = livePeer?.displayName?.takeIf { it.isNotBlank() }
                                    ?: ChatViewModel.resolveChatDisplayTitle(chat, currentUser.userId)
                                val peerAvatar = livePeer?.avatarUrl?.takeIf { it.isNotBlank() }
                                    ?: ChatViewModel.resolvePeerAvatar(chat, currentUser.userId)
                                val unread = chat.unreadCounts[currentUser.userId] ?: 0L
                                val visibleLastMessage = ChatViewModel.resolveVisibleLastMessage(chat, currentUser.userId)
                                val isLastFromMe = chat.lastMessageSenderId == currentUser.userId &&
                                    visibleLastMessage != "Conversación vacía" &&
                                    chat.lastMessageText.isNotBlank()
                                val lastMsgStatus = if (isLastFromMe) {
                                    ChatViewModel.resolveChatLastMessageStatus(
                                        chat = chat,
                                        currentUid = currentUser.userId,
                                        readReceiptsEnabled = readReceiptsEnabled
                                    )
                                } else {
                                    MessageDeliveryStatus.SENT
                                }
                                val listCheckColor by animateColorAsState(
                                    targetValue = when (lastMsgStatus) {
                                        MessageDeliveryStatus.READ -> Color(0xFF38BDF8)
                                        MessageDeliveryStatus.DELIVERED -> vybColors.secondaryText.copy(alpha = 0.78f)
                                        MessageDeliveryStatus.SENT -> vybColors.secondaryText.copy(alpha = 0.58f)
                                    },
                                    animationSpec = tween(220),
                                    label = "chat_list_read_check"
                                )
                                val isSelected = selectedChatId != null && chat.id == selectedChatId

                                Card(
                                    shape = RoundedCornerShape(22.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) vybColors.nestedSurface else MaterialTheme.colorScheme.surface
                                    ),
                                    elevation = CardDefaults.cardElevation(
                                        defaultElevation = if (isSelected) 0.dp else vybColors.cardElevationDp
                                    ),
                                    border = BorderStroke(
                                        if (isSelected) 1.5.dp else 0.75.dp,
                                        if (isSelected) MaterialTheme.colorScheme.primary else vybColors.hairlineBorder
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(22.dp))
                                        .combinedClickable(
                                            onClick = { chatViewModel.openChat(chat.id, readReceiptsEnabled) },
                                            onLongClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                contextMenuChat = chat
                                            }
                                        )
                                        .testTag("chat_item_${chat.id}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(modifier = Modifier.clip(CircleShape)) {
                                            VybAvatar(
                                                avatarUrl = peerAvatar,
                                                displayName = displayTitle,
                                                size = 48.dp,
                                                showGradientRing = unread > 0L,
                                                isVerified = livePeer?.verified == true
                                            )
                                        }
                                        Spacer(Modifier.width(14.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    modifier = Modifier.weight(1f),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        text = displayTitle,
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.SemiBold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f, fill = false)
                                                    )
                                                    livePeer?.quickStatus?.takeIf { it.isNotBlank() }?.let { peerStatus ->
                                                        UserStatusBadge(
                                                            status = peerStatus,
                                                            modifier = Modifier.testTag("chat_peer_status_${chat.id}")
                                                        )
                                                    }
                                                }
                                                Spacer(Modifier.width(8.dp))
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    if (isLastFromMe) {
                                                        Icon(
                                                            imageVector = if (lastMsgStatus == MessageDeliveryStatus.SENT) {
                                                                Icons.Filled.Done
                                                            } else {
                                                                Icons.Filled.DoneAll
                                                            },
                                                            contentDescription = when (lastMsgStatus) {
                                                                MessageDeliveryStatus.READ -> "Leído"
                                                                MessageDeliveryStatus.DELIVERED -> "Entregado"
                                                                MessageDeliveryStatus.SENT -> "Enviado"
                                                            },
                                                            tint = listCheckColor,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                    }
                                                    Text(
                                                        text = formatRelativeTime(chat.lastMessageAt),
                                                        style = MaterialTheme.typography.labelSmall.merge(VybMonoTabularStyle),
                                                        color = vybColors.secondaryText
                                                    )
                                                }
                                            }
                                            Spacer(Modifier.height(4.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = visibleLastMessage.ifBlank { "Sin mensajes todavía" },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = if (unread > 0L) MaterialTheme.colorScheme.onSurface else vybColors.secondaryText,
                                                    fontWeight = if (unread > 0L) FontWeight.SemiBold else FontWeight.Normal,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                if (unread > 0L) {
                                                    Spacer(Modifier.width(8.dp))
                                                    Box(
                                                        modifier = Modifier
                                                            .size(22.dp)
                                                            .clip(CircleShape)
                                                            .background(VybGradientBrush),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = unread.coerceAtMost(99L).toString(),
                                                            style = MaterialTheme.typography.labelSmall.merge(VybMonoTabularStyle),
                                                            color = Color.White
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
                }
            }
        }
        }
    }

    if (showContactsSheet) {
        ContactsAndGroupSheet(
            chatViewModel = chatViewModel,
            currentUser = currentUser,
            contacts = contacts,
            onOpenContactProfile = onOpenContactProfile,
            onDismiss = {
                chatViewModel.clearLookup()
                showContactsSheet = false
            }
        )
    }

    contextMenuChat?.let { targetChat ->
        val ctxPeerUid = if (targetChat.chatType == "direct") {
            ChatViewModel.resolvePeerUid(targetChat, currentUser.userId)
        } else null
        val ctxLivePeer = ctxPeerUid?.let { peerProfiles[it] }
        val ctxTitle = ctxLivePeer?.displayName?.takeIf { it.isNotBlank() }
            ?: ChatViewModel.resolveChatDisplayTitle(targetChat, currentUser.userId)
        val ctxAvatar = ctxLivePeer?.avatarUrl?.takeIf { it.isNotBlank() }
            ?: ChatViewModel.resolvePeerAvatar(targetChat, currentUser.userId)
        val optimisticBuzzPair by chatViewModel.optimisticBuzzDayAndCount.collectAsStateWithLifecycle()
        val todayKey = ChatViewModel.currentDateKey()
        val optimisticUsed = optimisticBuzzPair?.takeIf { it.first == todayKey }?.second ?: 0
        val remainingBuzzes = ChatViewModel.remainingDailyBuzzCount(
            privateUser = privateUser,
            messages = emptyList(),
            currentUid = currentUser.userId,
            optimisticUsedToday = optimisticUsed
        )

        ChatListContextMenuSheet(
            chat = targetChat,
            chatTitle = ctxTitle,
            peerAvatar = ctxAvatar,
            currentUser = currentUser,
            remainingBuzzes = remainingBuzzes,
            onDismiss = { contextMenuChat = null },
            onOpenChat = { chatViewModel.openChat(targetChat.id, readReceiptsEnabled) },
            onSendBuzz = {
                chatViewModel.sendBuzz(
                    chat = targetChat,
                    sender = currentUser,
                    currentPrivateUser = privateUser
                )
            },
            onRequestBlock = { blockTargetChat = targetChat },
            onRequestDeleteDirectChat = { deleteDirectConfirmChat = targetChat },
            onRequestLeaveGroup = { leaveGroupConfirmChat = targetChat },
            onRequestDeleteGroup = { deleteGroupConfirmChat = targetChat }
        )
    }

    blockTargetChat?.let { targetChat ->
        ChatListBlockContactDialog(
            chat = targetChat,
            currentUid = currentUser.userId,
            peerProfiles = peerProfiles,
            blockedUids = blockedUids,
            onBlockUser = onBlockUser,
            onUnblockUser = onUnblockUser,
            onBlockByIdentifier = onBlockByIdentifier,
            onDismiss = { blockTargetChat = null }
        )
    }

    deleteDirectConfirmChat?.let { targetChat ->
        AlertDialog(
            onDismissRequest = { deleteDirectConfirmChat = null },
            title = { Text("Eliminar conversación") },
            text = { Text("Este chat y todo su historial se borrarán de tu cuenta.") },
            confirmButton = {
                Button(
                    onClick = {
                        deleteDirectConfirmChat = null
                        chatViewModel.deleteChat(targetChat.id)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Eliminar", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteDirectConfirmChat = null }) { Text("Cancelar") }
            }
        )
    }

    leaveGroupConfirmChat?.let { targetChat ->
        AlertDialog(
            onDismissRequest = { leaveGroupConfirmChat = null },
            title = { Text("Salir del grupo") },
            text = { Text("Dejarás de recibir mensajes y ubicación en vivo de este grupo.") },
            confirmButton = {
                Button(
                    onClick = {
                        leaveGroupConfirmChat = null
                        chatViewModel.leaveGroup(targetChat.id, currentUser.displayName)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Salir", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { leaveGroupConfirmChat = null }) { Text("Cancelar") }
            }
        )
    }

    deleteGroupConfirmChat?.let { targetChat ->
        AlertDialog(
            onDismissRequest = { deleteGroupConfirmChat = null },
            title = { Text("Eliminar grupo") },
            text = { Text("El grupo se eliminará permanentemente para todos sus integrantes.") },
            confirmButton = {
                Button(
                    onClick = {
                        deleteGroupConfirmChat = null
                        chatViewModel.deleteGroup(targetChat.id)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Eliminar grupo", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteGroupConfirmChat = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun ChatRequestCard(
    request: ChatRequest,
    currentUserId: String,
    isBusy: Boolean,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onDelete: () -> Unit,
    onOpenChat: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val vybColors = LocalVybColors.current
    val isIncoming = request.toUid == currentUserId
    val peerName = if (isIncoming) request.fromName else request.toName
    val peerUsername = if (isIncoming) request.fromUsername else request.toUsername
    val peerAvatar = if (isIncoming) request.fromAvatarUrl else request.toAvatarUrl

    val statusText = when (request.status) {
        "accepted" -> "Aceptada"
        "rejected" -> "Rechazada"
        else -> if (isIncoming) "Pendiente" else "Enviada"
    }
    val statusColor = when (request.status) {
        "accepted" -> vybColors.sinpeEmerald
        "rejected" -> MaterialTheme.colorScheme.error
        else -> vybColors.linkAccent
    }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = vybColors.cardElevationDp),
        border = BorderStroke(0.75.dp, vybColors.hairlineBorder),
        modifier = modifier
            .fillMaxWidth()
            .testTag("chat_request_card_${request.id}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                VybAvatar(avatarUrl = peerAvatar, displayName = peerName, size = 46.dp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = peerName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "@$peerUsername · ${formatRelativeTime(request.updatedAt ?: request.createdAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = vybColors.secondaryText
                    )
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = statusColor.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            request.note?.takeIf { it.isNotBlank() }?.let { note ->
                Text(
                    text = "“$note”",
                    style = MaterialTheme.typography.bodySmall,
                    color = vybColors.secondaryText
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isIncoming && request.status == "pending") {
                    Button(
                        onClick = onAccept,
                        enabled = !isBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = vybColors.sinpeEmerald),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("accept_request_${request.id}")
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                        Spacer(Modifier.width(4.dp))
                        Text("Aceptar", color = Color.White)
                    }
                    OutlinedButton(
                        onClick = onReject,
                        enabled = !isBusy,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("reject_request_${request.id}")
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Rechazar")
                    }
                } else if (request.status == "accepted" && !request.chatId.isNullOrBlank()) {
                    Button(
                        onClick = { onOpenChat(request.chatId) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Text("Abrir chat")
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }

                OutlinedButton(
                    onClick = onDelete,
                    enabled = !isBusy,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("delete_request_${request.id}")
                ) {
                    Icon(
                        Icons.Filled.DeleteOutline,
                        contentDescription = "Eliminar solicitud",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

private enum class VoiceDraftStage {
    IDLE,
    RECORDING,
    PREVIEW
}

private enum class SpotlightMode {
    REACTION,
    MENU,
    MENU_WITH_REACTIONS
}

private enum class RecentMediaFilter(val label: String) {
    ALL("Todos"),
    PHOTOS("Fotos"),
    VIDEOS("Videos")
}

private data class InChatAiLaunchState(
    val mode: InChatAiMode,
    val prompt: String? = null,
    val draft: String = ""
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ChatRoomScreen(
    chat: Chat,
    messagesState: UiState<List<ChatMessage>>,
    currentUser: PublicUser,
    privateUser: PrivateUser?,
    chatViewModel: ChatViewModel,
    assistantViewModel: AssistantViewModel,
    aiModelChoice: AiModelChoice,
    onSelectModelChoice: (AiModelChoice) -> Unit,
    privateAreaLabel: String?,
    loadFullMediaFile: suspend (String) -> File?,
    onBack: () -> Unit,
    onStartCall: (calleeUid: String, calleeName: String, calleeAvatar: String?, isVideo: Boolean) -> Unit,
    activeGroupCall: CallSession? = null,
    onStartGroupCall: (chat: Chat, isVideo: Boolean) -> Unit = { _, _ -> },
    onJoinGroupCall: (CallSession) -> Unit = {},
    onOpenContactProfile: (uid: String, name: String, username: String?, avatar: String?) -> Unit = { _, _, _, _ -> },
    showBackButton: Boolean = true
) {
    val context = LocalContext.current
    val rootView = LocalView.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val vybColors = LocalVybColors.current
    val reactionBarController = remember(context) { ReactionFloatingBarController(context) }
    val reactionFirestoreHelper = remember(context) { ReactionFirestoreHelper(context) }
    val peerProfiles by chatViewModel.peerProfiles.collectAsStateWithLifecycle()
    val peerSinpeProfiles by chatViewModel.peerSinpeProfiles.collectAsStateWithLifecycle()
    val savedContacts by chatViewModel.contactsState.collectAsStateWithLifecycle()
    val activeLiveLocations by chatViewModel.activeLiveLocations.collectAsStateWithLifecycle()
    val isStartingLiveLocation by chatViewModel.isStartingLiveLocation.collectAsStateWithLifecycle()
    val effectiveSenderSinpeBank = privateUser?.sinpeBank?.takeIf { it.isNotBlank() }
        ?: currentUser.sinpeBank.ifBlank { "BAC Credomatic" }
    val peerUid = ChatViewModel.resolvePeerUid(chat, currentUser.userId)
    val livePeer = peerUid?.let { peerProfiles[it] }
    val title = livePeer?.displayName?.takeIf { it.isNotBlank() }
        ?: ChatViewModel.resolveChatDisplayTitle(chat, currentUser.userId)
    val peerAvatar = livePeer?.avatarUrl?.takeIf { it.isNotBlank() }
        ?: ChatViewModel.resolvePeerAvatar(chat, currentUser.userId)

    var messageDraft by remember { mutableStateOf("") }
    var selectedBombSec by remember { mutableStateOf<Int?>(null) }
    var showBombTimerPicker by remember { mutableStateOf(false) }
    var showCustomBombDialog by remember { mutableStateOf(false) }
    var editingMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var confirmDeleteMsg by remember { mutableStateOf<ChatMessage?>(null) }
    var inChatAiState by remember { mutableStateOf<InChatAiLaunchState?>(null) }
    val savedPrivateSummaries = remember(currentUser.userId) {
        mutableStateMapOf<String, StructuredChatSummary>()
    }

    var spotlightTarget by remember { mutableStateOf<Pair<ChatMessage, SpotlightMode>?>(null) }
    var spotlightAnchorBounds by remember { mutableStateOf<Rect?>(null) }
    var roomRootTopInWindow by remember { mutableFloatStateOf(0f) }

    var showSinpeModal by remember { mutableStateOf(false) }
    var showAttachmentSheet by remember { mutableStateOf(false) }
    var showLiveLocationMapSheet by remember { mutableStateOf(false) }
    var pendingLiveDurationMin by remember { mutableStateOf<Int?>(null) }
    var pendingStaticLocationShare by remember { mutableStateOf(false) }
    var showContactShareDialog by remember { mutableStateOf(false) }
    var showPollCreateDialog by remember { mutableStateOf(false) }
    var showChatOptionsMenu by remember { mutableStateOf(false) }
    var showDeleteChatDialog by remember { mutableStateOf(false) }
    var showGroupMembersSheet by remember { mutableStateOf(false) }
    var activeLightboxMsgId by remember { mutableStateOf<String?>(null) }
    var pendingMediaUri by remember { mutableStateOf<Uri?>(null) }
    var pendingMediaCaption by remember { mutableStateOf("") }
    var pendingMediaBombSec by remember { mutableStateOf<Int?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var isSendingMediaDraft by remember { mutableStateOf(false) }
    var isSendingAttachmentAction by remember { mutableStateOf(false) }
    var downloadingDocIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    val readReceiptsEnabled = privateUser?.readReceipts != false
    val messages = (messagesState as? UiState.Success)?.data ?: emptyList()
    val optimisticBuzzPair by chatViewModel.optimisticBuzzDayAndCount.collectAsStateWithLifecycle()
    val todayKey = ChatViewModel.currentDateKey()
    val optimisticUsedToday = optimisticBuzzPair?.takeIf { it.first == todayKey }?.second ?: 0
    val remainingBuzzes = ChatViewModel.remainingDailyBuzzCount(
        privateUser = privateUser,
        messages = messages,
        currentUid = currentUser.userId,
        optimisticUsedToday = optimisticUsedToday
    )
    val roomShakeX = remember { Animatable(0f) }
    val roomShakeY = remember { Animatable(0f) }
    val roomShakeRot = remember { Animatable(0f) }
    val buzzFlashAlpha = remember { Animatable(0f) }
    var buzzHydratedForChatId by remember(chat.id) { mutableStateOf<String?>(null) }
    var lastTriggeredBuzzId by remember(chat.id) { mutableStateOf<String?>(null) }

    var voiceStage by remember { mutableStateOf(VoiceDraftStage.IDLE) }
    var recordingSeconds by remember { mutableIntStateOf(0) }
    var previewAudioFile by remember { mutableStateOf<File?>(null) }
    var previewDurationSec by remember { mutableIntStateOf(0) }
    val recorderSession = remember(context) { VoiceRecorderSession(context.applicationContext) }
    val playbackManager = remember { VoicePlaybackManager() }
    var activePlaybackKey by remember { mutableStateOf<String?>(null) }
    var playbackProgress by remember { mutableFloatStateOf(0f) }
    var playbackPositionSec by remember { mutableIntStateOf(0) }
    var downloadingAudioIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val isSendingAudio by chatViewModel.isSendingAudio.collectAsStateWithLifecycle()

    val canDeleteChat = currentUser.userId in chat.participantUids

    fun startRecordingSession() {
        playbackManager.stop()
        activePlaybackKey = null
        if (recorderSession.start().isSuccess) {
            recordingSeconds = 0
            voiceStage = VoiceDraftStage.RECORDING
        } else {
            Toast.makeText(context, "No se pudo iniciar la grabación de audio.", Toast.LENGTH_SHORT).show()
        }
    }

    fun finishRecordingToPreview() {
        val result = recorderSession.stopAndFinalize()
        if (result != null) {
            previewAudioFile = result.first
            previewDurationSec = result.second
            voiceStage = VoiceDraftStage.PREVIEW
        } else {
            voiceStage = VoiceDraftStage.IDLE
            Toast.makeText(context, "La grabación fue muy corta o no se guardó.", Toast.LENGTH_SHORT).show()
        }
    }

    fun toggleAudioPlayback(key: String, file: File) {
        if (activePlaybackKey == key) {
            playbackManager.stop()
            activePlaybackKey = null
            playbackProgress = 0f
            playbackPositionSec = 0
        } else {
            playbackProgress = 0f
            playbackPositionSec = 0
            playbackManager.play(
                key = key,
                file = file,
                onCompleted = {
                    activePlaybackKey = null
                    playbackProgress = 0f
                    playbackPositionSec = 0
                },
                onError = { msg ->
                    activePlaybackKey = null
                    playbackProgress = 0f
                    playbackPositionSec = 0
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            )
            if (playbackManager.activeKey == key) {
                activePlaybackKey = key
            }
        }
    }

    fun toggleMessageReaction(msg: ChatMessage, reactionKey: String) {
        reactionFirestoreHelper.toggleReaction(
            chat.id,
            msg.id,
            currentUser.userId,
            reactionKey,
            msg.reactions,
            object : ReactionFirestoreHelper.ReactionCallback {
                override fun onSuccess(added: Boolean) {}
                override fun onError(errorMessage: String) {
                    Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    fun shareCurrentLocation() {
        if (isSendingAttachmentAction) return
        scope.launch {
            isSendingAttachmentAction = true
            try {
                val res = LocationHelper(context).fetchPrivateLocation()
                if (res.isSuccess) {
                    val info = res.getOrThrow()
                    chatViewModel.sendLocationAttachment(
                        chat = chat,
                        sender = currentUser,
                        latitude = info.latitude,
                        longitude = info.longitude,
                        label = info.areaLabel
                    )
                    showAttachmentSheet = false
                } else {
                    Toast.makeText(
                        context,
                        res.exceptionOrNull()?.localizedMessage ?: "No se pudo obtener tu ubicación.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } finally {
                isSendingAttachmentAction = false
            }
        }
    }

    DisposableEffect(chat.id) {
        onDispose {
            reactionBarController.dismiss()
            recorderSession.cancel()
            playbackManager.stop()
            runCatching { previewAudioFile?.delete() }
        }
    }

    BackHandler(enabled = spotlightTarget != null) {
        reactionBarController.dismiss()
        spotlightTarget = null
    }

    LaunchedEffect(voiceStage) {
        if (voiceStage == VoiceDraftStage.RECORDING) {
            while (voiceStage == VoiceDraftStage.RECORDING) {
                delay(1000L)
                recordingSeconds += 1
                if (recordingSeconds >= 120) {
                    finishRecordingToPreview()
                    break
                }
            }
        }
    }

    LaunchedEffect(activePlaybackKey) {
        val key = activePlaybackKey ?: return@LaunchedEffect
        while (activePlaybackKey == key) {
            if (playbackManager.isPlaying()) {
                val dur = playbackManager.durationMs().coerceAtLeast(1)
                val pos = playbackManager.currentPositionMs().coerceAtLeast(0)
                playbackProgress = (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
                playbackPositionSec = pos / 1000
            } else if (playbackManager.activeKey == null) {
                activePlaybackKey = null
                playbackProgress = 0f
                playbackPositionSec = 0
                break
            }
            delay(120L)
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startRecordingSession()
        } else {
            Toast.makeText(
                context,
                "Necesitamos permiso de micrófono para grabar notas de voz.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val mediaPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            pendingMediaUri = uri
            pendingMediaCaption = ""
            pendingMediaBombSec = selectedBombSec
            selectedBombSec = null
            showBombTimerPicker = false
            showAttachmentSheet = false
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { captured ->
        val camUri = pendingCameraUri
        pendingCameraUri = null
        if (captured && camUri != null) {
            pendingMediaUri = camUri
            pendingMediaCaption = ""
            pendingMediaBombSec = selectedBombSec
            selectedBombSec = null
            showBombTimerPicker = false
            showAttachmentSheet = false
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val outUri = MediaUtils.createTempCameraUri(context)
            pendingCameraUri = outUri
            cameraLauncher.launch(outUri)
        } else {
            Toast.makeText(
                context,
                "Necesitamos permiso de cámara para tomar fotos.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val documentPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            isSendingAttachmentAction = true
            chatViewModel.sendDocumentAttachment(
                context = context,
                chat = chat,
                sender = currentUser,
                uri = uri,
                onResult = { success ->
                    isSendingAttachmentAction = false
                    if (success) {
                        showAttachmentSheet = false
                    }
                }
            )
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        val liveMin = pendingLiveDurationMin
        val doStatic = pendingStaticLocationShare
        pendingLiveDurationMin = null
        pendingStaticLocationShare = false
        if (granted) {
            if (liveMin != null) {
                chatViewModel.startLiveLocationSharing(
                    context = context,
                    chat = chat,
                    sender = currentUser,
                    durationMin = liveMin
                )
            } else if (doStatic) {
                shareCurrentLocation()
            }
        } else {
            Toast.makeText(
                context,
                "Necesitamos permiso de ubicación para compartir tu punto en el mapa.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun requestLocationPermissionAndExecute(liveDurationMin: Int?) {
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (fineGranted || coarseGranted) {
            if (liveDurationMin != null) {
                chatViewModel.startLiveLocationSharing(
                    context = context,
                    chat = chat,
                    sender = currentUser,
                    durationMin = liveDurationMin
                )
            } else {
                shareCurrentLocation()
            }
        } else {
            pendingLiveDurationMin = liveDurationMin
            pendingStaticLocationShare = liveDurationMin == null
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    val listState = rememberLazyListState()

    suspend fun playBuzzShake() = coroutineScope {
        launch {
            buzzFlashAlpha.snapTo(0.85f)
            buzzFlashAlpha.animateTo(0f, tween(480, easing = FastOutSlowInEasing))
        }
        val impulses = listOf(
            Triple(22f, -7f, 1.4f),
            Triple(-22f, 8f, -1.4f),
            Triple(18f, -6f, 1.1f),
            Triple(-16f, 5f, -1.0f),
            Triple(12f, -4f, 0.7f),
            Triple(-9f, 3f, -0.5f),
            Triple(5f, -2f, 0.25f),
            Triple(0f, 0f, 0f)
        )
        impulses.forEach { (tx, ty, rot) ->
            launch { roomShakeX.animateTo(tx, tween(28)) }
            launch { roomShakeRot.animateTo(rot, tween(28)) }
            roomShakeY.animateTo(ty, tween(28))
        }
    }

    fun requestSendBuzz() {
        showAttachmentSheet = false
        showBombTimerPicker = false
        if (remainingBuzzes <= 0) {
            Toast.makeText(
                context,
                "Ya usaste tus 2 bips disponibles de hoy.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        chatViewModel.sendBuzz(
            chat = chat,
            sender = currentUser,
            currentPrivateUser = privateUser
        )
    }

    var initialScrolledChatId by remember { mutableStateOf<String?>(null) }
    var prevMsgCount by remember(chat.id) { mutableIntStateOf(0) }
    LaunchedEffect(chat.id, messages.size) {
        if (messages.isNotEmpty()) {
            if (initialScrolledChatId != chat.id) {
                initialScrolledChatId = chat.id
                prevMsgCount = messages.size
                listState.scrollToItem(messages.lastIndex)
            } else if (messages.size > prevMsgCount) {
                prevMsgCount = messages.size
                listState.animateScrollToItem(messages.lastIndex)
            } else {
                prevMsgCount = messages.size
            }
        }
    }

    // Trigger Messenger Buzz vibration + damped shake only for newly sent/arriving buzzes
    LaunchedEffect(messagesState, messages, chat.id) {
        if (messagesState !is UiState.Success) return@LaunchedEffect
        val newestBuzz = messages.lastOrNull {
            it.messageType == "buzz" && it.deletedForEveryone != true
        }
        if (buzzHydratedForChatId != chat.id) {
            buzzHydratedForChatId = chat.id
            lastTriggeredBuzzId = newestBuzz?.id
            return@LaunchedEffect
        }
        if (newestBuzz != null && newestBuzz.id != lastTriggeredBuzzId) {
            lastTriggeredBuzzId = newestBuzz.id
            triggerMessengerBuzzVibration(context)
            playBuzzShake()
        }
    }

    LaunchedEffect(
        chat.id,
        messages,
        chat.unreadCounts[currentUser.userId],
        readReceiptsEnabled
    ) {
        val unread = chat.unreadCounts[currentUser.userId] ?: 0L
        val lastFromPeer = messages.lastOrNull()?.let { it.senderId != currentUser.userId } == true
        val needsReceipt = readReceiptsEnabled && !chat.lastReadAt.containsKey(currentUser.userId)
        if (unread > 0L || lastFromPeer || needsReceipt) {
            chatViewModel.markChatRead(chat, sendReadReceipt = readReceiptsEnabled)
        }
        chatViewModel.acknowledgeMessagesInOpenChat(
            chat = chat,
            messages = messages,
            readReceiptsEnabled = readReceiptsEnabled
        )
    }

    // Recipient automatically confirms reading armed bomb messages once rendered in the open chat
    LaunchedEffect(messages, chat.id, currentUser.userId) {
        messages.forEach { msg ->
            if (msg.bombDurationSec != null &&
                msg.bombTriggeredAt == null &&
                msg.deletedForEveryone != true &&
                msg.senderId != currentUser.userId
            ) {
                chatViewModel.triggerBombMessage(chat.id, msg.id)
            }
        }
    }

    LaunchedEffect(voiceStage) {
        if (voiceStage != VoiceDraftStage.IDLE) {
            showAttachmentSheet = false
            showBombTimerPicker = false
            pendingMediaUri = null
        }
    }

    val isSpotlightActive = spotlightTarget != null
    val spotlightProgress by animateFloatAsState(
        targetValue = if (isSpotlightActive) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
        label = "spotlight_progress"
    )
    val contentBlurRadius = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && spotlightProgress > 0.02f) {
        (18f * spotlightProgress).dp
    } else {
        0.dp
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                roomRootTopInWindow = coords.positionInWindow().y
            }
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(if (contentBlurRadius > 0.dp) Modifier.blur(contentBlurRadius) else Modifier)
            .graphicsLayer {
                translationX = roomShakeX.value
                translationY = roomShakeY.value
                rotationZ = roomShakeRot.value
            }
            .navigationBarsPadding()
            .imePadding()
    ) {
        // Top bar
        Surface(color = MaterialTheme.colorScheme.background) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showBackButton) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("chat_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver a chats")
                    }
                } else {
                    Spacer(Modifier.width(8.dp))
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            if (chat.chatType == "group") {
                                showGroupMembersSheet = true
                            } else if (peerUid != null) {
                                onOpenContactProfile(peerUid, title, livePeer?.username, peerAvatar)
                            }
                        }
                        .padding(vertical = 4.dp, horizontal = 4.dp)
                        .testTag("chat_room_header_profile"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    VybAvatar(avatarUrl = peerAvatar, displayName = title, size = 40.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (chat.chatType == "group") {
                                "${chat.participantUids.size} participantes · Toca para ver"
                            } else {
                                livePeer?.quickStatus?.takeIf { it.isNotBlank() }?.let { "Chat directo · $it" }
                                    ?: "Chat directo · Ver perfil"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = vybColors.secondaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (chat.chatType == "direct" && peerUid != null) {
                    IconButton(
                        onClick = { onStartCall(peerUid, title, peerAvatar, false) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Filled.Call, contentDescription = "Llamada de voz")
                    }
                    IconButton(
                        onClick = { onStartCall(peerUid, title, peerAvatar, true) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Filled.Videocam, contentDescription = "Videollamada")
                    }
                } else if (chat.chatType == "group") {
                    IconButton(
                        onClick = { onStartGroupCall(chat, false) },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("group_voice_call_button")
                    ) {
                        Icon(Icons.Filled.Call, contentDescription = "Llamada grupal de voz")
                    }
                    IconButton(
                        onClick = { onStartGroupCall(chat, true) },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("group_video_call_button")
                    ) {
                        Icon(Icons.Filled.Videocam, contentDescription = "Videollamada grupal")
                    }
                }
                Box {
                    IconButton(
                        onClick = { showChatOptionsMenu = true },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("chat_options_menu_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "Herramientas y opciones del chat",
                            tint = vybColors.secondaryText
                        )
                    }
                    DropdownMenu(
                        expanded = showChatOptionsMenu,
                        onDismissRequest = { showChatOptionsMenu = false },
                        modifier = Modifier.testTag("chat_options_dropdown")
                    ) {
                        DropdownMenuItem(
                            text = { Text("Ubicación en tiempo real") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.LocationOn,
                                    contentDescription = null,
                                    tint = vybColors.sinpeEmerald
                                )
                            },
                            onClick = {
                                showChatOptionsMenu = false
                                showLiveLocationMapSheet = true
                            },
                            modifier = Modifier.testTag("chat_menu_live_location")
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "⚡ Bip ($remainingBuzzes/2 hoy)",
                                    color = if (remainingBuzzes > 0) MaterialTheme.colorScheme.onSurface else vybColors.secondaryText
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Bolt,
                                    contentDescription = null,
                                    tint = if (remainingBuzzes > 0) Color(0xFF00D2FF) else vybColors.secondaryText
                                )
                            },
                            enabled = remainingBuzzes > 0,
                            onClick = {
                                showChatOptionsMenu = false
                                requestSendBuzz()
                            },
                            modifier = Modifier.testTag("chat_menu_buzz")
                        )
                        DropdownMenuItem(
                            text = { Text("Cobro SINPE") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.PhoneAndroid,
                                    contentDescription = null,
                                    tint = vybColors.sinpeEmerald
                                )
                            },
                            onClick = {
                                showChatOptionsMenu = false
                                showSinpeModal = true
                            },
                            modifier = Modifier.testTag("chat_menu_sinpe")
                        )
                        DropdownMenuItem(
                            text = { Text("Escanear factura") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                                    contentDescription = null,
                                    tint = vybColors.sinpeEmerald
                                )
                            },
                            onClick = {
                                showChatOptionsMenu = false
                                assistantViewModel.clearTools()
                                inChatAiState = InChatAiLaunchState(mode = InChatAiMode.RECEIPT)
                            },
                            modifier = Modifier.testTag("chat_menu_receipt")
                        )
                        DropdownMenuItem(
                            text = { Text("Tono IA") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Tune,
                                    contentDescription = null,
                                    tint = vybColors.linkAccent
                                )
                            },
                            onClick = {
                                showChatOptionsMenu = false
                                assistantViewModel.clearTools()
                                inChatAiState = InChatAiLaunchState(
                                    mode = InChatAiMode.TONE,
                                    draft = messageDraft
                                )
                            },
                            modifier = Modifier.testTag("chat_menu_tone")
                        )
                        DropdownMenuItem(
                            text = { Text("Resumen IA") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Summarize,
                                    contentDescription = null,
                                    tint = vybColors.linkAccent
                                )
                            },
                            onClick = {
                                showChatOptionsMenu = false
                                assistantViewModel.clearTools()
                                inChatAiState = InChatAiLaunchState(mode = InChatAiMode.SUMMARY)
                            },
                            modifier = Modifier.testTag("chat_menu_summary")
                        )
                        if (canDeleteChat) {
                            HorizontalDivider(
                                thickness = 0.75.dp,
                                color = vybColors.hairlineBorder,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Eliminar conversación",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.DeleteOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    showChatOptionsMenu = false
                                    showDeleteChatDialog = true
                                },
                                modifier = Modifier.testTag("chat_delete_menu_item")
                            )
                        }
                    }
                }
            }
        }
        HorizontalDivider(thickness = 0.75.dp, color = vybColors.hairlineBorder)

        // Active joinable group call banner
        AnimatedVisibility(
            visible = chat.chatType == "group" && activeGroupCall != null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            val activeSession = activeGroupCall
            if (activeSession != null) {
                val activeCount = activeSession.activeParticipantUids.size.coerceAtLeast(1)
                Surface(
                    color = vybColors.sinpeEmerald.copy(alpha = if (vybColors.isDark) 0.22f else 0.14f),
                    border = BorderStroke(0.75.dp, vybColors.sinpeEmerald.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("group_active_call_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(vybColors.sinpeEmerald)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (activeSession.callType == "video") "Videollamada grupal en curso" else "Llamada grupal en curso",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = vybColors.sinpeEmerald
                            )
                            Text(
                                text = "$activeCount ${if (activeCount == 1) "persona" else "personas"} en sala · Iniciada por ${activeSession.callerName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = vybColors.secondaryText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Button(
                            onClick = { onJoinGroupCall(activeSession) },
                            colors = ButtonDefaults.buttonColors(containerColor = vybColors.sinpeEmerald),
                            shape = RoundedCornerShape(999.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("group_join_call_button")
                        ) {
                            Text("Unirse", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = activeLiveLocations.isNotEmpty(),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            LiveLocationActiveBanner(
                activeShares = activeLiveLocations,
                currentUid = currentUser.userId,
                isGroup = chat.chatType == "group",
                onOpenMap = { showLiveLocationMapSheet = true }
            )
        }

        // Messages list over Midnight Navy (dark) or Ice Blue (light) backdrop
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(vybColors.chatCanvas)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val topAlpha = if (vybColors.isDark) 0.16f else 0.09f
                val bottomAlpha = if (vybColors.isDark) 0.11f else 0.06f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(VybElectricBlue.copy(alpha = topAlpha), Color.Transparent),
                        center = Offset(size.width * 0.18f, size.height * 0.12f),
                        radius = size.maxDimension * 0.58f
                    ),
                    radius = size.maxDimension * 0.58f,
                    center = Offset(size.width * 0.18f, size.height * 0.12f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(VybViolet.copy(alpha = bottomAlpha), Color.Transparent),
                        center = Offset(size.width * 0.88f, size.height * 0.82f),
                        radius = size.maxDimension * 0.52f
                    ),
                    radius = size.maxDimension * 0.52f,
                    center = Offset(size.width * 0.88f, size.height * 0.82f)
                )
            }

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 780.dp)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.Top
            ) {
            if (messagesState is UiState.Loading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else if (messagesState is UiState.Error) {
                item {
                    EmptyStateView(
                        title = "No se pudieron cargar los mensajes",
                        subtitle = messagesState.message
                    )
                }
            } else if (messages.isEmpty()) {
                item {
                    EmptyStateView(
                        title = "Conversación lista",
                        subtitle = "Envía tu primer mensaje, activa el modo bomba en texto o fotos, usa /cobrar [monto] o menciona @vyb."
                    )
                }
            }

            itemsIndexed(messages, key = { _, msg -> msg.id }) { index, msg ->
                val isMine = msg.senderId == currentUser.userId
                val groupMeta = remember(
                    messages.size,
                    msg.id,
                    msg.createdAt?.seconds,
                    messages.getOrNull(index - 1)?.id,
                    messages.getOrNull(index - 1)?.senderId,
                    messages.getOrNull(index - 1)?.createdAt?.seconds,
                    messages.getOrNull(index + 1)?.id,
                    messages.getOrNull(index + 1)?.senderId,
                    messages.getOrNull(index + 1)?.createdAt?.seconds,
                    todayKey
                ) {
                    ChatViewModel.computeMessageGroupingMeta(messages, index)
                }

                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (groupMeta.showDateDivider) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    top = if (index == 0) 2.dp else 12.dp,
                                    bottom = 8.dp
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = vybColors.subtleSurface.copy(alpha = 0.88f),
                                border = BorderStroke(0.75.dp, vybColors.hairlineBorder),
                                modifier = Modifier.testTag("chat_date_divider_${groupMeta.dayKey}")
                            ) {
                                Text(
                                    text = groupMeta.dateLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = vybColors.secondaryText,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                        }
                    } else if (groupMeta.topSpacingDp > 0) {
                        Spacer(Modifier.height(groupMeta.topSpacingDp.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
                    ) {
                        if (msg.deletedForEveryone == true) {
                            DeletedMessageTombBubble(
                                isMine = isMine,
                                createdAt = msg.createdAt,
                                modifier = Modifier.testTag("msg_deleted_${msg.id}")
                            )
                        } else {
                            var bubbleBoundsInWindow by remember(msg.id) { mutableStateOf<Rect?>(null) }
                            val isHiddenUnderSpotlight = spotlightTarget?.first?.id == msg.id
                            val openReactionSpotlight: () -> Unit = {
                                bubbleBoundsInWindow?.let { rect ->
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    spotlightAnchorBounds = rect
                                    spotlightTarget = msg to SpotlightMode.REACTION
                                }
                            }
                            val openMenuSpotlight: () -> Unit = {
                                bubbleBoundsInWindow?.let { rect ->
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    spotlightAnchorBounds = rect
                                    spotlightTarget = msg to SpotlightMode.MENU_WITH_REACTIONS
                                }
                            }
                            val hasActiveReactions = remember(msg.reactions, currentUser.userId) {
                                ReactionCatalog.getOrderedActiveEntries(
                                    msg.reactions,
                                    currentUser.userId
                                ).isNotEmpty()
                            }

                            Column(
                                horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
                                modifier = Modifier.alpha(if (isHiddenUnderSpotlight) 0f else 1f)
                            ) {
                                Box(
                                    modifier = Modifier.onGloballyPositioned { coords ->
                                        val pos = coords.positionInWindow()
                                        bubbleBoundsInWindow = Rect(
                                            pos.x.roundToInt(),
                                            pos.y.roundToInt(),
                                            (pos.x + coords.size.width).roundToInt(),
                                            (pos.y + coords.size.height).roundToInt()
                                        )
                                    }
                                ) {
                                    VybBombMessageContainer(
                                        chatId = chat.id,
                                        message = msg,
                                        isMine = isMine,
                                        onVaporizeStarted = {
                                            chatViewModel.explodeBombMessage(chat.id, msg, delayMs = 1450L)
                                        },
                                        onTriggeredExpired = {
                                            chatViewModel.explodeBombMessage(chat.id, msg)
                                        },
                                        onDoubleTap = openReactionSpotlight,
                                        onLongPress = openMenuSpotlight
                                    ) { bombStage, remainingBombSecFloat, _ ->
                                        ChatMessageBubbleBody(
                                            msg = msg,
                                            chat = chat,
                                            isMine = isMine,
                                            currentUser = currentUser,
                                            groupMeta = groupMeta,
                                            bombStage = bombStage,
                                            remainingBombSecFloat = remainingBombSecFloat,
                                            readReceiptsEnabled = readReceiptsEnabled,
                                            activePlaybackKey = activePlaybackKey,
                                            playbackPositionSec = playbackPositionSec,
                                            playbackProgress = playbackProgress,
                                            downloadingAudioIds = downloadingAudioIds,
                                            downloadingDocIds = downloadingDocIds,
                                            isSpotlightPreview = false,
                                            onToggleAudioPlay = { audioMsg ->
                                                val mediaId = audioMsg.mediaId
                                                if (mediaId.isNullOrBlank()) {
                                                    Toast.makeText(context, "Audio no disponible.", Toast.LENGTH_SHORT).show()
                                                } else if (activePlaybackKey == audioMsg.id) {
                                                    playbackManager.stop()
                                                    activePlaybackKey = null
                                                    playbackProgress = 0f
                                                    playbackPositionSec = 0
                                                } else {
                                                    val cachedFile = File(context.cacheDir, "vyb_media_${mediaId}.m4a")
                                                    if (cachedFile.exists() && cachedFile.length() > 0L) {
                                                        toggleAudioPlayback(audioMsg.id, cachedFile)
                                                    } else {
                                                        scope.launch {
                                                            downloadingAudioIds = downloadingAudioIds + mediaId
                                                            val downloaded = loadFullMediaFile(mediaId)
                                                            downloadingAudioIds = downloadingAudioIds - mediaId
                                                            if (downloaded != null) {
                                                                toggleAudioPlayback(audioMsg.id, downloaded)
                                                            } else {
                                                                Toast.makeText(context, "No se pudo descargar la nota de voz.", Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    }
                                                }
                                            },
                                            onOpenMediaLightbox = { activeLightboxMsgId = it.id },
                                            onDoubleTapBubble = openReactionSpotlight,
                                            onLongPressBubble = openMenuSpotlight,
                                            onAuthorizeSinpe = { chatViewModel.authorizeSinpePayment(chat.id, it) },
                                            onOpenDocument = { docMsg ->
                                                chatViewModel.openDocumentAttachment(
                                                    context = context,
                                                    message = docMsg,
                                                    loadFullMediaFile = loadFullMediaFile,
                                                    onStatus = { loading ->
                                                        downloadingDocIds = if (loading) {
                                                            downloadingDocIds + (docMsg.mediaId ?: docMsg.id)
                                                        } else {
                                                            downloadingDocIds - (docMsg.mediaId ?: docMsg.id)
                                                        }
                                                    }
                                                )
                                            },
                                            onOpenLocation = { lat, lng, label ->
                                                val encoded = Uri.encode(label)
                                                val geoUri = Uri.parse("geo:$lat,$lng?q=$lat,$lng($encoded)")
                                                val mapIntent = Intent(Intent.ACTION_VIEW, geoUri)
                                                try {
                                                    context.startActivity(mapIntent)
                                                } catch (_: Exception) {
                                                    Toast.makeText(context, "No hay aplicación de mapas instalada.", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            onOpenSharedContactChat = { uid, name, avatar ->
                                                chatViewModel.startDirectChat(
                                                    currentUser = currentUser,
                                                    peerUid = uid,
                                                    peerName = name,
                                                    peerAvatar = avatar
                                                )
                                            },
                                            onVotePoll = { optionIdx ->
                                                chatViewModel.votePollOption(chat.id, msg.id, optionIdx)
                                            }
                                        )
                                    }
                                }
                                if (hasActiveReactions) {
                                    AndroidView(
                                        factory = { ctx -> ReactionBadgesView(ctx) },
                                        update = { badgesView ->
                                            badgesView.bindReactions(
                                                msg.reactions,
                                                currentUser.userId,
                                                isMine,
                                                vybColors.isDark
                                            ) { clickedReaction ->
                                                toggleMessageReaction(msg, clickedReaction.key)
                                            }
                                        },
                                        modifier = Modifier
                                            .padding(horizontal = 8.dp)
                                            .offset(y = (-5).dp)
                                            .widthIn(max = 300.dp)
                                            .testTag("msg_reactions_${msg.id}")
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

        // Inline edit banner when editing an existing message
        AnimatedVisibility(
            visible = editingMessage != null && voiceStage == VoiceDraftStage.IDLE,
            enter = fadeIn(tween(150)) + expandVertically(),
            exit = fadeOut(tween(120)) + shrinkVertically()
        ) {
            editingMessage?.let { target ->
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            modifier = Modifier
                                .widthIn(max = 780.dp)
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = null,
                                tint = vybColors.linkAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Editando mensaje · te quedan ${ChatViewModel.remainingEditMinutes(target)} min",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = vybColors.linkAccent,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = target.text,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = vybColors.secondaryText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = {
                                    editingMessage = null
                                    messageDraft = ""
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Cancelar edición")
                            }
                        }
                    }
                }
            }
        }

        // Pre-send image/video preview with inline Bomb Timer selector
        AnimatedVisibility(
            visible = pendingMediaUri != null && editingMessage == null && voiceStage == VoiceDraftStage.IDLE,
            enter = fadeIn(tween(160)) + slideInVertically { it / 3 } + expandVertically(
                spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMedium)
            ),
            exit = fadeOut(tween(120)) + shrinkVertically()
        ) {
            pendingMediaUri?.let { mediaUri ->
                val isVideoDraft = remember(mediaUri) {
                    context.contentResolver.getType(mediaUri)?.startsWith("video/") == true
                }
                PendingMediaDraftBar(
                    uri = mediaUri,
                    isVideo = isVideoDraft,
                    caption = pendingMediaCaption,
                    onCaptionChange = { pendingMediaCaption = it.take(400) },
                    selectedBombSec = pendingMediaBombSec,
                    onSelectBombSec = { pendingMediaBombSec = it },
                    onOpenCustomBomb = { showCustomBombDialog = true },
                    isSending = isSendingMediaDraft,
                    onCancel = {
                        pendingMediaUri = null
                        pendingMediaCaption = ""
                        pendingMediaBombSec = null
                    },
                    onSend = {
                        if (!isSendingMediaDraft) {
                            isSendingMediaDraft = true
                            val bomb = pendingMediaBombSec
                            val captionText = pendingMediaCaption.trim().takeIf { it.isNotBlank() }
                            chatViewModel.sendMediaAttachment(
                                context = context,
                                chat = chat,
                                sender = currentUser,
                                uri = mediaUri,
                                caption = captionText,
                                bombDurationSec = bomb,
                                onResult = { success ->
                                    isSendingMediaDraft = false
                                    if (success) {
                                        pendingMediaUri = null
                                        pendingMediaCaption = ""
                                        pendingMediaBombSec = null
                                    }
                                }
                            )
                        }
                    }
                )
            }
        }

        // Compact floating Bomb Timer panel directly above the composer text input
        AnimatedVisibility(
            visible = showBombTimerPicker && pendingMediaUri == null && editingMessage == null && voiceStage == VoiceDraftStage.IDLE,
            enter = fadeIn(tween(160)) + slideInVertically { it / 3 } + expandVertically(
                spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMedium)
            ),
            exit = fadeOut(tween(120)) + shrinkVertically()
        ) {
            BombTimerSelectorBar(
                selectedSeconds = selectedBombSec,
                onSelectSeconds = { chosen ->
                    selectedBombSec = chosen
                    showBombTimerPicker = false
                },
                onOpenCustomPicker = { showCustomBombDialog = true },
                onClose = { showBombTimerPicker = false }
            )
        }

        HorizontalDivider(thickness = 0.75.dp, color = vybColors.hairlineBorder)

        // Input row or voice recorder bar with fluid AnimatedContent transition
        Surface(color = vybColors.subtleSurface) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
            AnimatedContent(
                targetState = voiceStage,
                transitionSpec = {
                    (fadeIn(tween(180)) + slideInVertically { it / 3 }) togetherWith
                        (fadeOut(tween(130)) + slideOutVertically { it / 3 })
                },
                modifier = Modifier
                    .widthIn(max = 780.dp)
                    .fillMaxWidth(),
                label = "voice_stage_transition"
            ) { stage ->
            when (stage) {
                VoiceDraftStage.IDLE -> {
                    val plusRotation by animateFloatAsState(
                        targetValue = if (showAttachmentSheet) 45f else 0f,
                        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium),
                        label = "plus_rotation"
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                showBombTimerPicker = false
                                showAttachmentSheet = true
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("chat_tools_plus_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = if (showAttachmentSheet) "Cerrar menú adjunto" else "Adjuntar archivos y opciones",
                                tint = if (showAttachmentSheet) vybColors.linkAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.rotate(plusRotation)
                            )
                        }
                        if (editingMessage == null && pendingMediaUri == null) {
                            val isBombArmed = selectedBombSec != null
                            val bombBg = when {
                                isBombArmed -> Color(0xFF4F46E5).copy(alpha = if (vybColors.isDark) 0.28f else 0.14f)
                                showBombTimerPicker -> VybElectricBlue.copy(alpha = 0.12f)
                                else -> Color.Transparent
                            }
                            val bombStrokeColor = when {
                                isBombArmed -> if (vybColors.isDark) Color(0xFF93C5FD) else Color(0xFF3730A3)
                                showBombTimerPicker -> vybColors.linkAccent
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            val bombSparkColor = when {
                                isBombArmed -> Color(0xFFF97316)
                                showBombTimerPicker -> Color(0xFFF97316)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Box(
                                modifier = Modifier
                                    .padding(end = 4.dp)
                                    .size(48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = bombBg,
                                    border = when {
                                        isBombArmed -> BorderStroke(
                                            1.dp,
                                            Color(0xFF4F46E5).copy(alpha = 0.65f)
                                        )
                                        showBombTimerPicker -> BorderStroke(
                                            0.75.dp,
                                            VybElectricBlue.copy(alpha = 0.45f)
                                        )
                                        else -> null
                                    },
                                    modifier = Modifier
                                        .size(44.dp)
                                        .minimumInteractiveComponentSize()
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable {
                                            showAttachmentSheet = false
                                            showBombTimerPicker = !showBombTimerPicker
                                        }
                                        .testTag("chat_bomb_toggle_button"),
                                    content = {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            MinimalBombIcon(
                                                bodyStrokeColor = bombStrokeColor,
                                                sparkColor = bombSparkColor,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                )
                                selectedBombSec?.let { secs ->
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = Color(0xFF4F46E5),
                                        border = BorderStroke(1.dp, vybColors.subtleSurface),
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .offset(x = 2.dp, y = (-1).dp)
                                            .testTag("chat_bomb_active_badge")
                                    ) {
                                        Text(
                                            text = "${secs}s",
                                            style = MaterialTheme.typography.labelSmall.merge(VybMonoTabularStyle).copy(
                                                fontSize = 9.sp,
                                                lineHeight = 11.sp
                                            ),
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }
                        OutlinedTextField(
                            value = messageDraft,
                            onValueChange = {
                                messageDraft = it
                                if (showAttachmentSheet) showAttachmentSheet = false
                            },
                            placeholder = {
                                Text(
                                    text = when {
                                        editingMessage != null -> "Edita tu mensaje..."
                                        selectedBombSec != null -> "Mensaje bomba (${selectedBombSec}s)..."
                                        else -> "Escribe un mensaje, /cobrar o @vyb..."
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("chat_message_input"),
                            shape = RoundedCornerShape(22.dp),
                            maxLines = 4
                        )
                        Spacer(Modifier.width(8.dp))
                        AnimatedContent(
                            targetState = messageDraft.isNotBlank() || editingMessage != null,
                            transitionSpec = {
                                fadeIn(tween(150)) togetherWith fadeOut(tween(120))
                            },
                            label = "send_or_mic"
                        ) { hasText ->
                            if (hasText) {
                                IconButton(
                                    onClick = {
                                        showAttachmentSheet = false
                                        showBombTimerPicker = false
                                        val txt = messageDraft
                                        val editTarget = editingMessage
                                        if (editTarget != null) {
                                            if (txt.isNotBlank()) {
                                                chatViewModel.editMessage(chat.id, editTarget, txt) {
                                                    editingMessage = null
                                                    messageDraft = ""
                                                }
                                            }
                                        } else {
                                            val bomb = selectedBombSec
                                            selectedBombSec = null
                                            messageDraft = ""
                                            chatViewModel.sendMessageOrCommand(
                                                chat = chat,
                                                sender = currentUser,
                                                senderPhone = privateUser?.phone,
                                                rawText = txt,
                                                bombDurationSec = bomb,
                                                senderBank = effectiveSenderSinpeBank,
                                                onMentionAssistant = { prompt ->
                                                    assistantViewModel.clearTools()
                                                    inChatAiState = InChatAiLaunchState(
                                                        mode = InChatAiMode.QUICK_CHAT,
                                                        prompt = prompt
                                                    )
                                                }
                                            )
                                        }
                                    },
                                    enabled = messageDraft.isNotBlank(),
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(VybGradientBrush)
                                        .testTag("chat_send_button")
                                ) {
                                    Icon(
                                        imageVector = if (editingMessage != null) Icons.Filled.Check else Icons.AutoMirrored.Filled.Send,
                                        contentDescription = if (editingMessage != null) "Guardar edición" else "Enviar mensaje",
                                        tint = Color.White
                                    )
                                }
                            } else {
                                IconButton(
                                    onClick = {
                                        showAttachmentSheet = false
                                        val granted = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.RECORD_AUDIO
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (granted) {
                                            startRecordingSession()
                                        } else {
                                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(VybGradientBrush)
                                        .testTag("chat_mic_button")
                                ) {
                                    Icon(
                                        Icons.Filled.Mic,
                                        contentDescription = "Grabar nota de voz",
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    }
                }

                VoiceDraftStage.RECORDING -> {
                    val recPulse = rememberInfiniteTransition(label = "rec_dot_pulse")
                    val dotScale by recPulse.animateFloat(
                        initialValue = 0.85f,
                        targetValue = 1.3f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(550),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "rec_dot_scale"
                    )
                    val dotAlpha by recPulse.animateFloat(
                        initialValue = 0.55f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(550),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "rec_dot_alpha"
                    )
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = vybColors.subtleSurface,
                        border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .scale(dotScale)
                                    .alpha(dotAlpha)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.error)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Grabando nota de voz...",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${formatVoiceDuration(recordingSeconds)} / 2:00",
                                    style = MaterialTheme.typography.labelSmall.merge(VybMonoTabularStyle),
                                    color = vybColors.secondaryText
                                )
                            }
                            IconButton(
                                onClick = {
                                    recorderSession.cancel()
                                    voiceStage = VoiceDraftStage.IDLE
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("voice_cancel_button")
                            ) {
                                Icon(
                                    Icons.Filled.DeleteOutline,
                                    contentDescription = "Cancelar grabación",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                            IconButton(
                                onClick = { finishRecordingToPreview() },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.error)
                                    .testTag("voice_stop_button")
                            ) {
                                Icon(
                                    Icons.Filled.Stop,
                                    contentDescription = "Detener y previsualizar",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }

                VoiceDraftStage.PREVIEW -> {
                    val previewFile = previewAudioFile
                    val isPreviewPlaying = activePlaybackKey == "__preview__"
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = vybColors.subtleSurface,
                        border = BorderStroke(0.75.dp, VybElectricBlue.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    if (previewFile != null) {
                                        toggleAudioPlayback("__preview__", previewFile)
                                    }
                                },
                                enabled = previewFile != null && !isSendingAudio,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("voice_preview_play_button")
                            ) {
                                Icon(
                                    imageVector = if (isPreviewPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (isPreviewPlaying) "Pausar vista previa" else "Escuchar vista previa",
                                    tint = VybElectricBlue
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (selectedBombSec != null) "Vista previa · Bomba ${selectedBombSec}s" else "Vista previa",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = if (isPreviewPlaying) {
                                            "${formatVoiceDuration(playbackPositionSec)} / ${formatVoiceDuration(previewDurationSec)}"
                                        } else {
                                            formatVoiceDuration(previewDurationSec)
                                        },
                                        style = MaterialTheme.typography.labelSmall.merge(VybMonoTabularStyle),
                                        color = vybColors.secondaryText
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { if (isPreviewPlaying) playbackProgress else 0f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(CircleShape)
                                )
                            }
                            IconButton(
                                onClick = {
                                    if (isPreviewPlaying) {
                                        playbackManager.stop()
                                        activePlaybackKey = null
                                    }
                                    runCatching { previewAudioFile?.delete() }
                                    previewAudioFile = null
                                    previewDurationSec = 0
                                    voiceStage = VoiceDraftStage.IDLE
                                },
                                enabled = !isSendingAudio,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("voice_discard_button")
                            ) {
                                Icon(
                                    Icons.Filled.DeleteOutline,
                                    contentDescription = "Descartar audio",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                            IconButton(
                                onClick = {
                                    if (previewFile != null && !isSendingAudio) {
                                        if (isPreviewPlaying) {
                                            playbackManager.stop()
                                            activePlaybackKey = null
                                        }
                                        val bomb = selectedBombSec
                                        chatViewModel.sendVoiceMessage(
                                            chat = chat,
                                            sender = currentUser,
                                            audioFile = previewFile,
                                            durationSec = previewDurationSec,
                                            bombDurationSec = bomb,
                                            onSent = {
                                                selectedBombSec = null
                                                previewAudioFile = null
                                                previewDurationSec = 0
                                                voiceStage = VoiceDraftStage.IDLE
                                            }
                                        )
                                    }
                                },
                                enabled = previewFile != null && !isSendingAudio,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(VybGradientBrush)
                                    .testTag("voice_send_button")
                            ) {
                                if (isSendingAudio) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(20.dp)
                                    )
                                } else {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Enviar nota de voz",
                                        tint = Color.White
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
    }

    // iOS / WhatsApp-style Spotlight Overlay for Reaction & Options
    val activeSpotlight = spotlightTarget
    val rawBounds = spotlightAnchorBounds
    if (activeSpotlight != null && rawBounds != null) {
        val targetMsg = activeSpotlight.first
        val mode = activeSpotlight.second
        val isMineTarget = targetMsg.senderId == currentUser.userId
        val myActiveReactionKey = remember(targetMsg.reactions, currentUser.userId) {
            ReactionCatalog.getOrderedActiveEntries(targetMsg.reactions, currentUser.userId)
                .firstOrNull { it.isSelectedByCurrentUser }
                ?.reaction?.key
        }
        val spotlightBubbleScale by animateFloatAsState(
            targetValue = 1.03f,
            animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow),
            label = "spotlight_bubble_scale"
        )
        val dismissSpotlight: () -> Unit = {
            reactionBarController.dismiss()
            spotlightTarget = null
        }
        BackHandler(enabled = true) {
            dismissSpotlight()
        }
        val showReactionsBar = mode == SpotlightMode.REACTION || mode == SpotlightMode.MENU_WITH_REACTIONS
        val showMenuCard = mode == SpotlightMode.MENU || mode == SpotlightMode.MENU_WITH_REACTIONS

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    (if (vybColors.isDark) Color(0xB8030712) else Color(0x8A0F172A))
                        .copy(alpha = (if (vybColors.isDark) 0.72f else 0.54f) * spotlightProgress)
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = dismissSpotlight
                )
                .testTag("chat_spotlight_overlay")
        ) {
            val topReservePx = with(density) { (if (showReactionsBar) 148.dp else 36.dp).roundToPx() }
            val bottomReservePx = with(density) { (if (showMenuCard) 220.dp else 88.dp).roundToPx() }
            val containerHeightPx = with(density) { maxHeight.roundToPx() }
            val rawRelTopPx = (rawBounds.top - roomRootTopInWindow).roundToInt()
            val bubbleHeightPx = rawBounds.height().coerceAtLeast(with(density) { 48.dp.roundToPx() })
            val maxTopPx = (containerHeightPx - bubbleHeightPx - bottomReservePx).coerceAtLeast(topReservePx)
            val clampedTopPx = rawRelTopPx.coerceIn(topReservePx, maxTopPx)

            LaunchedEffect(targetMsg.id, mode, clampedTopPx) {
                if (showReactionsBar) {
                    val liftedTopInWindow = roomRootTopInWindow.roundToInt() + clampedTopPx
                    val liftedBounds = Rect(
                        rawBounds.left,
                        liftedTopInWindow,
                        rawBounds.right,
                        liftedTopInWindow + bubbleHeightPx
                    )
                    val cornerInsetPx = with(density) { 22.dp.roundToPx() }
                    val badgeDropPx = with(density) { 8.dp.roundToPx() }
                    val halfBadgePx = with(density) { 16.dp.roundToPx() }
                    val destCenterX = if (isMineTarget) {
                        rawBounds.right - cornerInsetPx
                    } else {
                        rawBounds.left + cornerInsetPx
                    }
                    val destCenterY = rawBounds.bottom + badgeDropPx
                    val flightDestBounds = Rect(
                        destCenterX - halfBadgePx,
                        destCenterY - halfBadgePx,
                        destCenterX + halfBadgePx,
                        destCenterY + halfBadgePx
                    )
                    delay(45L)
                    reactionBarController.showAboveAnchor(
                        rootView,
                        liftedBounds,
                        vybColors.isDark,
                        myActiveReactionKey,
                        flightDestBounds,
                        false,
                        { selectedReaction ->
                            toggleMessageReaction(targetMsg, selectedReaction.key)
                            spotlightTarget = null
                        },
                        {
                            if (spotlightTarget?.first?.id == targetMsg.id &&
                                (spotlightTarget?.second == SpotlightMode.REACTION ||
                                    spotlightTarget?.second == SpotlightMode.MENU_WITH_REACTIONS)
                            ) {
                                spotlightTarget = null
                            }
                        }
                    )
                } else {
                    reactionBarController.dismiss()
                }
            }

            Column(
                horizontalAlignment = if (isMineTarget) Alignment.End else Alignment.Start,
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(0, clampedTopPx) }
                    .padding(horizontal = 14.dp)
            ) {
                val spotlightRemSecFloat = targetMsg.bombDurationSec?.let { dur ->
                    resolveBombUiRemainingSeconds(
                        isTriggered = targetMsg.bombTriggeredAt != null,
                        triggeredAtSec = targetMsg.bombTriggeredAt?.seconds,
                        durationSec = dur
                    )
                } ?: 0f
                val spotlightBombStage = resolveBombVisualStage(
                    isTriggered = targetMsg.bombTriggeredAt != null,
                    remainingSec = spotlightRemSecFloat
                )
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = spotlightBubbleScale
                            scaleY = spotlightBubbleScale
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        )
                ) {
                    VybBombMessageContainer(
                        chatId = chat.id,
                        message = targetMsg,
                        isMine = isMineTarget,
                        isSpotlightPreview = true,
                        onVaporizeStarted = {},
                        onTriggeredExpired = {},
                        onDoubleTap = {},
                        onLongPress = {}
                    ) { _, _, _ ->
                        ChatMessageBubbleBody(
                            msg = targetMsg,
                            chat = chat,
                            isMine = isMineTarget,
                            currentUser = currentUser,
                            groupMeta = ChatViewModel.MessageGroupingMeta(
                                showDateDivider = false,
                                dateLabel = "",
                                dayKey = "",
                                isGroupedWithPrevious = false,
                                isGroupedWithNext = false,
                                topSpacingDp = 0
                            ),
                            bombStage = spotlightBombStage,
                            remainingBombSecFloat = spotlightRemSecFloat,
                            readReceiptsEnabled = readReceiptsEnabled,
                            activePlaybackKey = null,
                            playbackPositionSec = 0,
                            playbackProgress = 0f,
                            downloadingAudioIds = emptySet(),
                            downloadingDocIds = emptySet(),
                            isSpotlightPreview = true,
                            onToggleAudioPlay = {},
                            onOpenMediaLightbox = {},
                            onDoubleTapBubble = {},
                            onLongPressBubble = {},
                            onAuthorizeSinpe = {},
                            onOpenDocument = {},
                            onOpenLocation = { _, _, _ -> },
                            onOpenSharedContactChat = { _, _, _ -> },
                            onVotePoll = {}
                        )
                    }
                }

                if (showMenuCard) {
                    Spacer(Modifier.height(10.dp))
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(tween(150)) + scaleIn(initialScale = 0.92f),
                        exit = fadeOut(tween(110)) + scaleOut(targetScale = 0.92f)
                    ) {
                        SpotlightQuickActionsCard(
                            message = targetMsg,
                            canEdit = ChatViewModel.canEditMessage(targetMsg, currentUser.userId),
                            remainingEditMinutes = ChatViewModel.remainingEditMinutes(targetMsg),
                            canDeleteForEveryone = targetMsg.senderId == currentUser.userId && targetMsg.deletedForEveryone != true,
                            onCopy = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("Mensaje VYB", targetMsg.text))
                                Toast.makeText(context, "Mensaje copiado", Toast.LENGTH_SHORT).show()
                                spotlightTarget = null
                            },
                            onEdit = {
                                editingMessage = targetMsg
                                messageDraft = targetMsg.text
                                spotlightTarget = null
                            },
                            onRequestDeleteForEveryone = {
                                confirmDeleteMsg = targetMsg
                                spotlightTarget = null
                            }
                        )
                    }
                }
            }
        }
    }

        // High-voltage electric blue flash overlay during Bip shockwave
        if (buzzFlashAlpha.value > 0.01f) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val a = buzzFlashAlpha.value
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF00D2FF).copy(alpha = 0.14f * a),
                            Color(0xFF2563EB).copy(alpha = 0.24f * a),
                            Color(0xFF00D2FF).copy(alpha = 0.36f * a)
                        ),
                        center = Offset(size.width * 0.5f, size.height * 0.45f),
                        radius = size.maxDimension * 0.72f
                    )
                )
                drawRect(
                    color = Color(0xFF00D2FF).copy(alpha = 0.65f * a),
                    style = Stroke(width = 4.dp.toPx())
                )
            }
        }

        if (showLiveLocationMapSheet) {
            LiveLocationChatMapSheet(
                chat = chat,
                chatTitle = title,
                currentUid = currentUser.userId,
                privateUser = privateUser,
                savedContacts = savedContacts,
                activeShares = activeLiveLocations,
                isStarting = isStartingLiveLocation,
                onStartSharing = { durationMin ->
                    requestLocationPermissionAndExecute(durationMin)
                },
                onStopSharing = {
                    chatViewModel.stopLiveLocationSharing(chat.id)
                },
                onSendStaticLocation = {
                    showLiveLocationMapSheet = false
                    requestLocationPermissionAndExecute(null)
                },
                onDismiss = { showLiveLocationMapSheet = false }
            )
        }
    }

    // Expandable WhatsApp-style bottom attachment sheet ('+' button)
    if (showAttachmentSheet) {
        AttachmentOptionsSheet(
            recentMessages = messages,
            isBusy = isSendingAttachmentAction,
            onDismiss = { showAttachmentSheet = false },
            onPickPhotos = {
                mediaPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onPickVideos = {
                mediaPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                )
            },
            onOpenSystemGallery = {
                mediaPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                )
            },
            onOpenCamera = {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) {
                    val outUri = MediaUtils.createTempCameraUri(context)
                    pendingCameraUri = outUri
                    cameraLauncher.launch(outUri)
                } else {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            },
            onPickDocument = {
                documentPicker.launch(arrayOf("*/*"))
            },
            onShareLocation = {
                showAttachmentSheet = false
                showLiveLocationMapSheet = true
            },
            onPickContact = {
                showAttachmentSheet = false
                showContactShareDialog = true
            },
            onCreatePoll = {
                showAttachmentSheet = false
                showPollCreateDialog = true
            },
            onSelectRecentMedia = { recentMsg ->
                showAttachmentSheet = false
                activeLightboxMsgId = recentMsg.id
            }
        )
    }

    if (showContactShareDialog) {
        ContactShareDialog(
            contacts = savedContacts,
            peerSinpeProfiles = peerSinpeProfiles,
            onSelectContact = { contact ->
                showContactShareDialog = false
                chatViewModel.sendContactAttachment(chat, currentUser, contact)
            },
            onDismiss = { showContactShareDialog = false }
        )
    }

    if (showPollCreateDialog) {
        PollCreateDialog(
            onCreatePoll = { question, options ->
                chatViewModel.sendPoll(
                    chat = chat,
                    sender = currentUser,
                    question = question,
                    options = options,
                    onSent = { showPollCreateDialog = false }
                )
            },
            onDismiss = { showPollCreateDialog = false }
        )
    }

    confirmDeleteMsg?.let { targetMsg ->
        AlertDialog(
            onDismissRequest = { confirmDeleteMsg = null },
            title = { Text("Eliminar para ambos") },
            text = {
                Text("¿Deseas eliminar este mensaje para ambos usuarios? Se borrará el contenido y ninguno podrá leerlo.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        chatViewModel.deleteMessageForEveryone(chat.id, targetMsg)
                        confirmDeleteMsg = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Eliminar para todos", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteMsg = null }) { Text("Cancelar") }
            }
        )
    }

    if (showCustomBombDialog) {
        CustomBombSecondsDialog(
            initialSeconds = (if (pendingMediaUri != null) pendingMediaBombSec else selectedBombSec) ?: 20,
            onConfirm = { secs ->
                if (pendingMediaUri != null) {
                    pendingMediaBombSec = secs
                } else {
                    selectedBombSec = secs
                }
                showCustomBombDialog = false
            },
            onDismiss = { showCustomBombDialog = false }
        )
    }

    // Compact in-chat AI bottom sheet (zero redirection, zero tab strip)
    inChatAiState?.let { launchState ->
        InChatAiSheet(
            mode = launchState.mode,
            assistantViewModel = assistantViewModel,
            modelChoice = aiModelChoice,
            onSelectModelChoice = onSelectModelChoice,
            messages = messages,
            initialDraft = launchState.draft,
            initialPrompt = launchState.prompt,
            privateAreaLabel = privateAreaLabel,
            savedSummary = savedPrivateSummaries[chat.id],
            onSaveLocalSummary = { summary ->
                savedPrivateSummaries[chat.id] = summary
            },
            onShareSummary = { summary ->
                savedPrivateSummaries[chat.id] = summary
                chatViewModel.shareAiSummary(chat, currentUser, summary)
            },
            onPutToneInDraft = { text ->
                messageDraft = text
            },
            onSendTextToChat = { text ->
                val bomb = selectedBombSec
                selectedBombSec = null
                chatViewModel.sendMessageOrCommand(
                    chat = chat,
                    sender = currentUser,
                    senderPhone = privateUser?.phone,
                    rawText = text,
                    bombDurationSec = bomb,
                    senderBank = effectiveSenderSinpeBank,
                    onMentionAssistant = {}
                )
            },
            onSendSinpeFromReceipt = { totalAmount, splitCount, reason ->
                chatViewModel.sendSinpeCard(
                    chat = chat,
                    sender = currentUser,
                    senderPhone = privateUser?.phone,
                    totalAmount = totalAmount,
                    splitCount = splitCount,
                    reason = reason,
                    senderBank = effectiveSenderSinpeBank
                )
            },
            onDismiss = {
                assistantViewModel.clearTools()
                inChatAiState = null
            }
        )
    }

    if (showDeleteChatDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteChatDialog = false },
            title = { Text("Eliminar conversación") },
            text = { Text("Este chat y todo su historial se borrarán por completo de tu cuenta. No volverán a aparecer mensajes anteriores.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteChatDialog = false
                        chatViewModel.deleteChat(chat.id)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Eliminar", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteChatDialog = false }) { Text("Cancelar") }
            }
        )
    }

    if (showSinpeModal) {
        SinpeCreateDialog(
            defaultPeopleCount = if (chat.chatType == "group") chat.participantUids.size.coerceAtLeast(1) else 1,
            onConfirm = { amount, splitCount, reason ->
                showSinpeModal = false
                chatViewModel.sendSinpeCard(
                    chat = chat,
                    sender = currentUser,
                    senderPhone = privateUser?.phone,
                    totalAmount = amount,
                    splitCount = splitCount,
                    reason = reason,
                    senderBank = effectiveSenderSinpeBank
                )
            },
            onDismiss = { showSinpeModal = false }
        )
    }

    val activeLightboxMsg = activeLightboxMsgId?.let { id ->
        messages.firstOrNull { it.id == id && it.deletedForEveryone != true }
    }
    LaunchedEffect(activeLightboxMsgId, activeLightboxMsg == null) {
        if (activeLightboxMsgId != null && activeLightboxMsg == null) {
            activeLightboxMsgId = null
        }
    }
    activeLightboxMsg?.let { lbMsg ->
        val lbBombRemaining by produceState<Long?>(
            initialValue = lbMsg.bombDurationSec?.let { dur ->
                val trig = lbMsg.bombTriggeredAt
                if (trig == null) dur else (dur - ((System.currentTimeMillis() / 1000L) - trig.seconds).coerceAtLeast(0L)).coerceAtLeast(0L)
            },
            key1 = lbMsg.id,
            key2 = lbMsg.bombTriggeredAt?.seconds,
            key3 = lbMsg.bombDurationSec
        ) {
            val dur = lbMsg.bombDurationSec ?: run {
                value = null
                return@produceState
            }
            val trig = lbMsg.bombTriggeredAt
            if (trig == null) {
                value = dur
                return@produceState
            }
            while (true) {
                val diff = (System.currentTimeMillis() / 1000L) - trig.seconds
                val elapsed = if (diff in -15L..2L) 0L else diff.coerceAtLeast(0L)
                val rem = (dur - elapsed).coerceAtLeast(0L)
                value = rem
                if (rem <= 0L) break
                delay(1000L)
            }
        }
        ZoomableMediaLightbox(
            mediaType = lbMsg.mediaType ?: "image",
            previewDataUri = lbMsg.mediaPreviewData,
            mediaId = lbMsg.mediaId,
            loadFullMediaFile = { id -> loadFullMediaFile(id) },
            bombRemainingSec = lbBombRemaining,
            onDismiss = { activeLightboxMsgId = null }
        )
    }

    if (showGroupMembersSheet && chat.chatType == "group") {
        GroupInfoMembersSheet(
            chat = chat,
            currentUser = currentUser,
            chatViewModel = chatViewModel,
            onOpenMemberProfile = onOpenContactProfile,
            onGroupExited = {
                showGroupMembersSheet = false
                onBack()
            },
            onDismiss = {
                chatViewModel.clearLookup()
                showGroupMembersSheet = false
            }
        )
    }
}

@Composable
private fun DeletedMessageTombBubble(
    isMine: Boolean,
    createdAt: com.google.firebase.Timestamp?,
    modifier: Modifier = Modifier
) {
    val vybColors = LocalVybColors.current
    Surface(
        shape = RoundedCornerShape(
            topStart = 18.dp,
            topEnd = 18.dp,
            bottomStart = if (isMine) 18.dp else 6.dp,
            bottomEnd = if (isMine) 6.dp else 18.dp
        ),
        color = vybColors.nestedSurface.copy(alpha = 0.85f),
        border = BorderStroke(0.75.dp, vybColors.hairlineBorder),
        modifier = modifier.widthIn(max = 280.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Block,
                contentDescription = null,
                tint = vybColors.secondaryText,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = "Mensaje eliminado",
                style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
                color = vybColors.secondaryText
            )
            Text(
                text = formatMessageTime(createdAt),
                style = MaterialTheme.typography.labelSmall.merge(VybMonoTabularStyle),
                color = vybColors.secondaryText.copy(alpha = 0.78f)
            )
        }
    }
}

// Self-destructing message container and effects live in com.example.ui.components.VybBombMessageEffects.kt

@Composable
private fun MinimalBombIcon(
    bodyStrokeColor: Color,
    sparkColor: Color,
    modifier: Modifier = Modifier.size(22.dp)
) {
    Canvas(modifier = modifier) {
        val s = size.minDimension / 24f

        // Sphere: <circle cx="10.5" cy="13.5" r="7.5" stroke="currentColor" stroke-width="2" />
        drawCircle(
            color = bodyStrokeColor,
            radius = 7.5f * s,
            center = Offset(10.5f * s, 13.5f * s),
            style = Stroke(width = 2f * s)
        )

        // Inner highlight: M7.5 11.2 C8.1 10.1 9.2 9.4 10.5 9.4
        val highlight = Path().apply {
            moveTo(7.5f * s, 11.2f * s)
            cubicTo(8.1f * s, 10.1f * s, 9.2f * s, 9.4f * s, 10.5f * s, 9.4f * s)
        }
        drawPath(
            path = highlight,
            color = bodyStrokeColor,
            style = Stroke(width = 1.8f * s, cap = StrokeCap.Round)
        )

        // Fuse neck collar: M14.2 6.8 L15.8 5.2 L17.8 7.2 L16.2 8.8
        val neck = Path().apply {
            moveTo(14.2f * s, 6.8f * s)
            lineTo(15.8f * s, 5.2f * s)
            lineTo(17.8f * s, 7.2f * s)
            lineTo(16.2f * s, 8.8f * s)
        }
        drawPath(
            path = neck,
            color = bodyStrokeColor,
            style = Stroke(width = 2f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Curved fuse: M16.8 6.2 C17.8 5.2 18.2 3.8 19.5 3.5
        val fuse = Path().apply {
            moveTo(16.8f * s, 6.2f * s)
            cubicTo(17.8f * s, 5.2f * s, 18.2f * s, 3.8f * s, 19.5f * s, 3.5f * s)
        }
        drawPath(
            path = fuse,
            color = bodyStrokeColor,
            style = Stroke(width = 2f * s, cap = StrokeCap.Round)
        )

        // Spark cross: M20.5 1.5 V4.5 M19 3 H22
        drawLine(
            color = sparkColor,
            start = Offset(20.5f * s, 1.5f * s),
            end = Offset(20.5f * s, 4.5f * s),
            strokeWidth = 2f * s,
            cap = StrokeCap.Round
        )
        drawLine(
            color = sparkColor,
            start = Offset(19f * s, 3f * s),
            end = Offset(22f * s, 3f * s),
            strokeWidth = 2f * s,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun BombTimerSelectorBar(
    selectedSeconds: Int?,
    onSelectSeconds: (Int?) -> Unit,
    onOpenCustomPicker: () -> Unit,
    onClose: () -> Unit
) {
    val vybColors = LocalVybColors.current
    val presets = listOf(5, 15, 30, 60)
    val isCustomActive = selectedSeconds != null && selectedSeconds !in presets
    val indigoAccent = Color(0xFF4F46E5)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(vybColors.chatCanvas)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = vybColors.incomingBubbleBg,
            border = BorderStroke(
                width = 1.dp,
                color = if (selectedSeconds != null) {
                    VybElectricBlue.copy(alpha = 0.72f)
                } else {
                    vybColors.incomingBubbleBorder
                }
            ),
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
            modifier = Modifier
                .widthIn(max = 756.dp)
                .fillMaxWidth()
                .testTag("bomb_floating_panel")
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        MinimalBombIcon(
                            bodyStrokeColor = if (selectedSeconds != null) {
                                if (vybColors.isDark) Color(0xFF93C5FD) else Color(0xFF3730A3)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            sparkColor = if (selectedSeconds != null) {
                                Color(0xFFF97316)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Modo bomba",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = if (selectedSeconds != null) {
                                indigoAccent.copy(alpha = if (vybColors.isDark) 0.28f else 0.14f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        ) {
                            Text(
                                text = if (selectedSeconds != null) "Al leer · ${selectedSeconds}s" else "Sin temporizador",
                                style = MaterialTheme.typography.labelSmall.merge(VybMonoTabularStyle),
                                fontWeight = FontWeight.SemiBold,
                                color = if (selectedSeconds != null) {
                                    if (vybColors.isDark) Color(0xFF93C5FD) else indigoAccent
                                } else {
                                    vybColors.secondaryText
                                },
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Cerrar menú bomba",
                                tint = vybColors.secondaryText,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bomb_timer_options_row"),
                    contentPadding = PaddingValues(horizontal = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item {
                        BombTimerPill(
                            label = "OFF",
                            selected = selectedSeconds == null,
                            isAccent = false,
                            onClick = { onSelectSeconds(null) },
                            testTag = "bomb_timer_off"
                        )
                    }
                    items(presets) { secs ->
                        BombTimerPill(
                            label = "${secs}s",
                            selected = selectedSeconds == secs,
                            isAccent = true,
                            onClick = { onSelectSeconds(if (selectedSeconds == secs) null else secs) },
                            testTag = "bomb_timer_${secs}s"
                        )
                    }
                    item {
                        BombTimerPill(
                            label = if (isCustomActive) "${selectedSeconds}s ✎" else "Más",
                            selected = isCustomActive,
                            isAccent = true,
                            onClick = onOpenCustomPicker,
                            testTag = "bomb_timer_custom"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BombTimerPill(
    label: String,
    selected: Boolean,
    isAccent: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val vybColors = LocalVybColors.current
    val bgColor = when {
        selected && isAccent -> Color(0xFF4F46E5)
        selected -> VybElectricBlue.copy(alpha = 0.18f)
        else -> vybColors.nestedSurface
    }
    val textColor = when {
        selected && isAccent -> Color.White
        selected -> vybColors.linkAccent
        else -> vybColors.secondaryText
    }
    val borderColor = when {
        selected && isAccent -> Color(0xFFF97316)
        selected -> MaterialTheme.colorScheme.primary
        else -> vybColors.hairlineBorder
    }

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = bgColor,
        border = BorderStroke(0.9.dp, borderColor),
        modifier = Modifier
            .widthIn(min = 48.dp)
            .heightIn(min = 42.dp)
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.merge(VybMonoTabularStyle),
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = textColor,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun PendingMediaDraftBar(
    uri: Uri,
    isVideo: Boolean,
    caption: String,
    onCaptionChange: (String) -> Unit,
    selectedBombSec: Int?,
    onSelectBombSec: (Int?) -> Unit,
    onOpenCustomBomb: () -> Unit,
    isSending: Boolean,
    onCancel: () -> Unit,
    onSend: () -> Unit
) {
    val vybColors = LocalVybColors.current
    val presets = listOf(5, 15, 30, 60)
    val isCustomActive = selectedBombSec != null && selectedBombSec !in presets

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(vybColors.chatCanvas)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = vybColors.incomingBubbleBg,
            border = BorderStroke(
                width = 1.dp,
                color = if (selectedBombSec != null) {
                    Color(0xFF4F46E5).copy(alpha = 0.78f)
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                }
            ),
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
            modifier = Modifier
                .widthIn(max = 756.dp)
                .fillMaxWidth()
                .testTag("media_draft_preview_card")
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isVideo) "Vista previa de video" else "Vista previa de imagen",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = vybColors.linkAccent
                    )
                    IconButton(
                        onClick = onCancel,
                        enabled = !isSending,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("media_draft_cancel_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Cancelar adjunto",
                            tint = vybColors.secondaryText
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = uri,
                            contentDescription = "Vista previa antes de enviar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (isVideo) {
                            Icon(
                                imageVector = Icons.Filled.PlayCircleFilled,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = caption,
                        onValueChange = onCaptionChange,
                        placeholder = { Text("Comentario opcional...") },
                        singleLine = true,
                        enabled = !isSending,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("media_draft_caption_input")
                    )

                    IconButton(
                        onClick = onSend,
                        enabled = !isSending,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(VybGradientBrush)
                            .testTag("media_draft_send_button")
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Enviar multimedia",
                                tint = Color.White
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MinimalBombIcon(
                        bodyStrokeColor = if (selectedBombSec != null) {
                            if (vybColors.isDark) Color(0xFF93C5FD) else Color(0xFF3730A3)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        sparkColor = if (selectedBombSec != null) {
                            Color(0xFFF97316)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (selectedBombSec != null) {
                            "Se eliminará en ${selectedBombSec}s al abrirse"
                        } else {
                            "Activa modo bomba para auto-eliminar:"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selectedBombSec != null) {
                            if (vybColors.isDark) Color(0xFF93C5FD) else Color(0xFF4F46E5)
                        } else {
                            vybColors.secondaryText
                        },
                        fontWeight = if (selectedBombSec != null) FontWeight.Bold else FontWeight.Medium
                    )
                }

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item {
                        BombTimerPill(
                            label = "OFF",
                            selected = selectedBombSec == null,
                            isAccent = false,
                            onClick = { if (!isSending) onSelectBombSec(null) },
                            testTag = "media_draft_bomb_off"
                        )
                    }
                    items(presets) { secs ->
                        BombTimerPill(
                            label = "${secs}s",
                            selected = selectedBombSec == secs,
                            isAccent = true,
                            onClick = {
                                if (!isSending) {
                                    onSelectBombSec(if (selectedBombSec == secs) null else secs)
                                }
                            },
                            testTag = "media_draft_bomb_${secs}s"
                        )
                    }
                    item {
                        BombTimerPill(
                            label = if (isCustomActive) "${selectedBombSec}s ✎" else "Más",
                            selected = isCustomActive,
                            isAccent = true,
                            onClick = { if (!isSending) onOpenCustomBomb() },
                            testTag = "media_draft_bomb_custom"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BuzzMessageCard(
    senderName: String,
    isMine: Boolean,
    createdAt: com.google.firebase.Timestamp?,
    modifier: Modifier = Modifier
) {
    val vybColors = LocalVybColors.current
    val pulse = rememberInfiniteTransition(label = "bip_card_electric")
    val boltScale by pulse.animateFloat(
        initialValue = 0.90f,
        targetValue = 1.22f,
        animationSpec = infiniteRepeatable(
            animation = tween(380, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bip_bolt_scale"
    )
    val boltTilt by pulse.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(260),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bip_bolt_tilt"
    )
    val ringAlpha by pulse.animateFloat(
        initialValue = 0.85f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(720),
            repeatMode = RepeatMode.Restart
        ),
        label = "bip_ring_alpha"
    )
    val ringScale by pulse.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.48f,
        animationSpec = infiniteRepeatable(
            animation = tween(720),
            repeatMode = RepeatMode.Restart
        ),
        label = "bip_ring_scale"
    )
    val electricCyan = Color(0xFF00D2FF)
    val electricBlue = Color(0xFF2563EB)
    val titleColor = if (vybColors.isDark) Color(0xFF38BDF8) else Color(0xFF1D4ED8)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = electricBlue.copy(alpha = if (vybColors.isDark) 0.20f else 0.12f),
        border = BorderStroke(
            width = 1.35.dp,
            brush = Brush.linearGradient(listOf(electricBlue, electricCyan))
        ),
        modifier = modifier
            .widthIn(max = 280.dp)
            .testTag("bip_message_card")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier.size(38.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = ringScale
                            scaleY = ringScale
                            alpha = ringAlpha
                        }
                ) {
                    drawCircle(
                        color = electricCyan,
                        style = Stroke(width = 1.8.dp.toPx())
                    )
                }
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .graphicsLayer {
                            scaleX = boltScale
                            scaleY = boltScale
                            rotationZ = boltTilt
                        }
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(electricBlue, electricCyan)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Bolt,
                        contentDescription = "Bip",
                        tint = Color.White,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
            Column {
                Text(
                    text = if (isMine) "Enviaste un bip" else "$senderName te envió un bip",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = titleColor
                )
                Text(
                    text = formatMessageTime(createdAt),
                    style = MaterialTheme.typography.labelSmall.merge(VybMonoTabularStyle),
                    color = titleColor.copy(alpha = 0.80f)
                )
            }
        }
    }
}

private fun triggerMessengerBuzzVibration(context: Context) {
    runCatching {
        val pattern = longArrayOf(0L, 90L, 60L, 140L)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            val vibrator = vm?.defaultVibrator
            if (vibrator?.hasVibrator() == true) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            }
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator?.hasVibrator() == true) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            }
        }
    }
}

@Composable
private fun CustomBombSecondsDialog(
    initialSeconds: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var secondsInput by remember { mutableStateOf(initialSeconds.coerceIn(1, 3600).toString()) }
    val parsed = secondsInput.toIntOrNull()
    val valid = parsed != null && parsed in 1..3600

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MinimalBombIcon(
                    bodyStrokeColor = MaterialTheme.colorScheme.onSurface,
                    sparkColor = Color(0xFFF97316),
                    modifier = Modifier.size(22.dp)
                )
                Text("Temporizador bomba")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Elige cuántos segundos durará el mensaje visible después de que la otra persona confirme su lectura (1 a 3600 s).",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = secondsInput,
                    onValueChange = { secondsInput = it.filter { ch -> ch.isDigit() }.take(4) },
                    label = { Text("Segundos") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { parsed?.let { onConfirm(it.coerceIn(1, 3600)) } },
                enabled = valid,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C))
            ) {
                Text("Activar", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatMessageBubbleBody(
    msg: ChatMessage,
    chat: Chat,
    isMine: Boolean,
    currentUser: PublicUser,
    groupMeta: ChatViewModel.MessageGroupingMeta,
    bombStage: VybBombVisualStage,
    remainingBombSecFloat: Float,
    readReceiptsEnabled: Boolean,
    activePlaybackKey: String?,
    playbackPositionSec: Int,
    playbackProgress: Float,
    downloadingAudioIds: Set<String>,
    downloadingDocIds: Set<String>,
    isSpotlightPreview: Boolean,
    onToggleAudioPlay: (ChatMessage) -> Unit,
    onOpenMediaLightbox: (ChatMessage) -> Unit,
    onDoubleTapBubble: () -> Unit,
    onLongPressBubble: () -> Unit,
    onAuthorizeSinpe: (ChatMessage) -> Unit,
    onOpenDocument: (ChatMessage) -> Unit,
    onOpenLocation: (Double, Double, String) -> Unit,
    onOpenSharedContactChat: (String, String, String?) -> Unit,
    onVotePoll: (Int) -> Unit
) {
    val vybColors = LocalVybColors.current
    when (msg.messageType) {
        "bomb_destroyed_ghost" -> Unit
        "buzz" -> {
            BuzzMessageCard(
                senderName = msg.senderName,
                isMine = isMine,
                createdAt = msg.createdAt,
                modifier = if (isSpotlightPreview) Modifier else Modifier.testTag("msg_buzz_${msg.id}")
            )
        }
        "sinpe" -> {
            SinpeCard(
                message = msg,
                currentUserId = currentUser.userId,
                onAuthorizePayment = { if (!isSpotlightPreview) onAuthorizeSinpe(msg) }
            )
        }
        "ai_summary" -> {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = vybColors.cardElevationDp),
                border = BorderStroke(0.75.dp, vybColors.linkAccent.copy(alpha = 0.42f)),
                modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val summaryTopic = ChatViewModel.resolveAiSummaryHeaderTopic(
                        aiSummaryTopic = msg.aiSummaryTopic,
                        chat = chat,
                        currentUid = currentUser.userId
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = vybColors.linkAccent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Resumen del chat · $summaryTopic",
                            style = MaterialTheme.typography.labelLarge,
                            color = vybColors.linkAccent
                        )
                    }
                    Text(msg.aiSummaryMain ?: msg.text, style = MaterialTheme.typography.bodyMedium)
                    msg.aiSummaryDecisions?.takeIf { it.isNotEmpty() }?.let { decs ->
                        Text("Decisiones:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        decs.forEach { d -> Text("• $d", style = MaterialTheme.typography.bodySmall) }
                    }
                    msg.aiSummaryTasks?.takeIf { it.isNotEmpty() }?.let { tasks ->
                        Text("Pendientes:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        tasks.forEach { t -> Text("• $t", style = MaterialTheme.typography.bodySmall) }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = formatMessageTime(msg.createdAt),
                            style = MaterialTheme.typography.labelSmall.merge(VybMonoTabularStyle),
                            color = vybColors.secondaryText
                        )
                    }
                }
            }
        }
        else -> {
            val isBombArmed = msg.bombDurationSec != null
            val useDarkGlassForeground = isMine || isBombArmed

            val bubbleInnerContent: @Composable () -> Unit = {
                Column {
                    if (!isMine && chat.chatType == "group" && !groupMeta.isGroupedWithPrevious) {
                        Text(
                            text = msg.senderName,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isBombArmed) Color(0xFF67E8F9) else vybColors.linkAccent,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(2.dp))
                    }
                    when {
                        msg.messageType == "document" -> {
                            val docKey = msg.mediaId ?: msg.id
                            DocumentBubbleContent(
                                fileName = msg.documentName ?: msg.text.ifBlank { "Documento VYB" },
                                sizeBytes = msg.documentSizeBytes ?: 0L,
                                isMine = useDarkGlassForeground,
                                isLoading = docKey in downloadingDocIds,
                                onOpen = { if (!isSpotlightPreview) onOpenDocument(msg) }
                            )
                        }
                        msg.messageType == "location" -> {
                            val lat = msg.locationLat ?: 9.9281
                            val lng = msg.locationLng ?: -84.0907
                            val area = msg.locationLabel?.takeIf { it.isNotBlank() } ?: "Ubicación compartida"
                            LocationBubbleContent(
                                latitude = lat,
                                longitude = lng,
                                label = area,
                                isMine = useDarkGlassForeground,
                                onOpenMap = { if (!isSpotlightPreview) onOpenLocation(lat, lng, area) }
                            )
                        }
                        msg.messageType == "contact" -> {
                            val contactUid = msg.sharedContactUid.orEmpty()
                            val contactName = msg.sharedContactName ?: "Contacto VYB"
                            val contactUsername = msg.sharedContactUsername ?: "usuario"
                            ContactBubbleContent(
                                name = contactName,
                                username = contactUsername,
                                avatarUrl = msg.sharedContactAvatarUrl,
                                isMine = useDarkGlassForeground,
                                canOpenChat = contactUid.isNotBlank() && contactUid != currentUser.userId,
                                onOpenChat = {
                                    if (!isSpotlightPreview && contactUid.isNotBlank()) {
                                        onOpenSharedContactChat(contactUid, contactName, msg.sharedContactAvatarUrl)
                                    }
                                }
                            )
                        }
                        msg.messageType == "poll" -> {
                            PollBubbleContent(
                                question = msg.pollQuestion ?: msg.text,
                                options = msg.pollOptions.orEmpty(),
                                votes = msg.pollVotes.orEmpty(),
                                currentUid = currentUser.userId,
                                isMine = useDarkGlassForeground,
                                onVote = { idx -> if (!isSpotlightPreview) onVotePoll(idx) }
                            )
                        }
                        msg.messageType == "media" && msg.mediaType == "audio" -> {
                            val isThisPlaying = activePlaybackKey == msg.id
                            val isLoadingAudio = msg.mediaId in downloadingAudioIds
                            val totalSecs = (msg.audioDurationSec ?: 1L).toInt().coerceAtLeast(1)
                            AudioMessageContent(
                                isMine = useDarkGlassForeground,
                                isPlaying = isThisPlaying,
                                isLoading = isLoadingAudio,
                                progress = if (isThisPlaying) playbackProgress else 0f,
                                durationLabel = if (isThisPlaying) {
                                    "${formatVoiceDuration(playbackPositionSec)} / ${formatVoiceDuration(totalSecs)}"
                                } else {
                                    formatVoiceDuration(totalSecs)
                                },
                                onTogglePlay = { if (!isSpotlightPreview) onToggleAudioPlay(msg) }
                            )
                        }
                        else -> {
                            if (!msg.mediaPreviewData.isNullOrBlank()) {
                                val previewData = msg.mediaPreviewData
                                val cachedBmp = remember(previewData) {
                                    BitmapMemoryCache.getCached(previewData, maxDim = 640)
                                }
                                val previewBmp by produceState(initialValue = cachedBmp, key1 = previewData) {
                                    value = BitmapMemoryCache.decodeDataUriCached(previewData, maxDim = 640)
                                }
                                val currentBmp = previewBmp
                                if (currentBmp != null) {
                                    Image(
                                        bitmap = currentBmp,
                                        contentDescription = "Archivo adjunto",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(170.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .then(
                                                if (isSpotlightPreview) Modifier
                                                else Modifier.combinedClickable(
                                                    onClick = { onOpenMediaLightbox(msg) },
                                                    onDoubleClick = onDoubleTapBubble,
                                                    onLongClick = onLongPressBubble
                                                )
                                            )
                                    )
                                    Spacer(Modifier.height(6.dp))
                                }
                            }
                            if (msg.text.isNotBlank()) {
                                Text(
                                    text = msg.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (useDarkGlassForeground) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(if (isBombArmed) 6.dp else 5.dp))
                    Row(
                        modifier = if (isBombArmed) Modifier.fillMaxWidth() else Modifier,
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = if (isBombArmed) Arrangement.SpaceBetween else Arrangement.spacedBy(5.dp)
                    ) {
                        if (msg.bombDurationSec != null) {
                            VybBombIndicatorRow(
                                isTriggered = msg.bombTriggeredAt != null,
                                durationSec = msg.bombDurationSec,
                                remainingSecFloat = remainingBombSecFloat,
                                stage = bombStage
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (msg.editedAt != null) {
                                Text(
                                    text = "Editado ·",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontStyle = FontStyle.Italic,
                                    color = if (useDarkGlassForeground) Color(0xFFCBD5E1).copy(alpha = 0.85f) else vybColors.secondaryText
                                )
                            }
                            Text(
                                text = formatMessageTime(msg.createdAt),
                                style = MaterialTheme.typography.labelSmall.merge(VybMonoTabularStyle),
                                color = if (useDarkGlassForeground) {
                                    if (isBombArmed) Color(0xFFCBD5E1).copy(alpha = 0.86f)
                                    else Color.White.copy(alpha = 0.78f)
                                } else {
                                    vybColors.secondaryText
                                }
                            )
                            if (isMine) {
                                val deliveryStatus = ChatViewModel.resolveMessageDeliveryStatus(
                                    chat = chat,
                                    message = msg,
                                    currentUid = currentUser.userId,
                                    readReceiptsEnabled = readReceiptsEnabled
                                )
                                val checkTint by animateColorAsState(
                                    targetValue = when (deliveryStatus) {
                                        MessageDeliveryStatus.READ ->
                                            if (isBombArmed) Color(0xFF67E8F9) else Color(0xFF38BDF8)
                                        MessageDeliveryStatus.DELIVERED ->
                                            Color.White.copy(alpha = 0.74f)
                                        MessageDeliveryStatus.SENT ->
                                            Color.White.copy(alpha = 0.56f)
                                    },
                                    animationSpec = tween(220),
                                    label = "msg_read_tint"
                                )
                                Icon(
                                    imageVector = if (deliveryStatus == MessageDeliveryStatus.SENT) {
                                        Icons.Filled.Done
                                    } else {
                                        Icons.Filled.DoneAll
                                    },
                                    contentDescription = when (deliveryStatus) {
                                        MessageDeliveryStatus.READ -> "Leído"
                                        MessageDeliveryStatus.DELIVERED -> "Entregado"
                                        MessageDeliveryStatus.SENT -> "Enviado"
                                    },
                                    tint = checkTint,
                                    modifier = Modifier
                                        .size(14.dp)
                                        .then(if (isSpotlightPreview) Modifier else Modifier.testTag("msg_read_check_${msg.id}"))
                                )
                            }
                        }
                    }
                }
            }

            if (isBombArmed) {
                Box(modifier = Modifier.widthIn(min = 190.dp, max = 270.dp)) {
                    bubbleInnerContent()
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(
                        topStart = if (!isMine && groupMeta.isGroupedWithPrevious) 8.dp else 20.dp,
                        topEnd = if (isMine && groupMeta.isGroupedWithPrevious) 8.dp else 20.dp,
                        bottomStart = when {
                            isMine -> 20.dp
                            groupMeta.isGroupedWithNext -> 8.dp
                            else -> 6.dp
                        },
                        bottomEnd = when {
                            !isMine -> 20.dp
                            groupMeta.isGroupedWithNext -> 8.dp
                            else -> 6.dp
                        }
                    ),
                    color = if (isMine) Color.Transparent else vybColors.incomingBubbleBg,
                    border = when {
                        isMine -> BorderStroke(0.75.dp, vybColors.senderBubbleBorder)
                        else -> BorderStroke(
                            if (vybColors.isDark) 0.75.dp else 1.dp,
                            vybColors.incomingBubbleBorder
                        )
                    },
                    tonalElevation = if (isMine) 0.dp else 2.dp,
                    shadowElevation = if (isMine || vybColors.isDark) 0.dp else 2.dp,
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .animateContentSize(
                            spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
                        )
                ) {
                    Box(
                        modifier = if (isMine) Modifier.background(VybSenderBubbleBrush).padding(13.dp)
                        else Modifier.padding(13.dp)
                    ) {
                        bubbleInnerContent()
                    }
                }
            }
        }
    }
}

@Composable
private fun DocumentBubbleContent(
    fileName: String,
    sizeBytes: Long,
    isMine: Boolean,
    isLoading: Boolean,
    onOpen: () -> Unit
) {
    val vybColors = LocalVybColors.current
    val fg = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface
    val sub = if (isMine) Color.White.copy(alpha = 0.82f) else vybColors.secondaryText
    val pillBg = if (isMine) Color.White.copy(alpha = 0.16f) else vybColors.nestedSurface
    val iconTint = if (isMine) Color.White else vybColors.linkAccent

    Row(
        modifier = Modifier
            .widthIn(min = 190.dp, max = 270.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(pillBg)
            .clickable(enabled = !isLoading, onClick = onOpen)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(if (isMine) Color.White.copy(alpha = 0.18f) else vybColors.linkAccent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = iconTint,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Description,
                    contentDescription = "Documento adjunto",
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = fileName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = fg,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${if (sizeBytes > 0L) MediaUtils.formatByteSize(sizeBytes) + " · " else ""}Tocar para abrir",
                style = MaterialTheme.typography.labelSmall,
                color = sub
            )
        }
    }
}

@Composable
private fun LocationBubbleContent(
    latitude: Double,
    longitude: Double,
    label: String,
    isMine: Boolean,
    onOpenMap: () -> Unit
) {
    val vybColors = LocalVybColors.current
    val fg = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface
    val sub = if (isMine) Color.White.copy(alpha = 0.82f) else vybColors.secondaryText
    val pinBg = if (isMine) Color.White.copy(alpha = 0.18f) else vybColors.sinpeEmerald.copy(alpha = 0.16f)
    val pinTint = if (isMine) Color.White else vybColors.sinpeEmerald

    Column(
        modifier = Modifier
            .widthIn(min = 200.dp, max = 268.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (isMine) Color.White.copy(alpha = 0.14f) else vybColors.nestedSurface)
            .clickable(onClick = onOpenMap)
            .padding(11.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(pinBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = "Ubicación",
                    tint = pinTint,
                    modifier = Modifier.size(21.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = fg,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = String.format(Locale.US, "%.4f, %.4f", latitude, longitude),
                    style = MaterialTheme.typography.labelSmall.merge(VybMonoTabularStyle),
                    color = sub
                )
            }
        }
        Text(
            text = "Ver ubicación en mapa →",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (isMine) Color(0xFFBAE6FD) else vybColors.linkAccent
        )
    }
}

@Composable
private fun ContactBubbleContent(
    name: String,
    username: String,
    avatarUrl: String?,
    isMine: Boolean,
    canOpenChat: Boolean,
    onOpenChat: () -> Unit
) {
    val vybColors = LocalVybColors.current
    val fg = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface
    val sub = if (isMine) Color.White.copy(alpha = 0.82f) else vybColors.secondaryText

    Column(
        modifier = Modifier
            .widthIn(min = 200.dp, max = 268.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (isMine) Color.White.copy(alpha = 0.14f) else vybColors.nestedSurface)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            VybAvatar(avatarUrl = avatarUrl, displayName = name, size = 40.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = fg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "@$username",
                    style = MaterialTheme.typography.labelSmall,
                    color = sub,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (canOpenChat) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isMine) Color.White.copy(alpha = 0.22f) else vybColors.linkAccent.copy(alpha = 0.14f),
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onOpenChat)
            ) {
                Text(
                    text = "Enviar mensaje",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isMine) Color.White else vybColors.linkAccent,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun PollBubbleContent(
    question: String,
    options: List<String>,
    votes: Map<String, List<String>>,
    currentUid: String,
    isMine: Boolean,
    onVote: (Int) -> Unit
) {
    val vybColors = LocalVybColors.current
    val fg = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface
    val sub = if (isMine) Color.White.copy(alpha = 0.8f) else vybColors.secondaryText
    val totalVotes = votes.values.sumOf { it.size }.coerceAtLeast(0)

    Column(
        modifier = Modifier.widthIn(min = 215.dp, max = 272.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Poll,
                contentDescription = null,
                tint = if (isMine) Color(0xFFBAE6FD) else vybColors.linkAccent,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "Encuesta",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (isMine) Color(0xFFBAE6FD) else vybColors.linkAccent
            )
        }
        Text(
            text = question,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = fg
        )
        options.forEachIndexed { idx, optionLabel ->
            val optionVoters = votes[idx.toString()].orEmpty()
            val votedByMe = currentUid in optionVoters
            val pct = if (totalVotes > 0) optionVoters.size.toFloat() / totalVotes.toFloat() else 0f
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isMine) Color.White.copy(alpha = if (votedByMe) 0.24f else 0.13f)
                else if (votedByMe) vybColors.linkAccent.copy(alpha = 0.15f) else vybColors.nestedSurface,
                border = BorderStroke(
                    width = if (votedByMe) 1.15.dp else 0.75.dp,
                    color = when {
                        votedByMe && isMine -> Color(0xFF38BDF8)
                        votedByMe -> vybColors.linkAccent
                        isMine -> Color.White.copy(alpha = 0.26f)
                        else -> vybColors.hairlineBorder
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onVote(idx) }
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (votedByMe) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "Tu voto",
                                    tint = if (isMine) Color(0xFF38BDF8) else vybColors.linkAccent,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            Text(
                                text = optionLabel,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (votedByMe) FontWeight.Bold else FontWeight.Medium,
                                color = fg
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "${optionVoters.size}",
                            style = MaterialTheme.typography.labelSmall.merge(VybMonoTabularStyle),
                            color = sub
                        )
                    }
                    Spacer(Modifier.height(5.dp))
                    LinearProgressIndicator(
                        progress = { pct },
                        color = if (isMine) Color(0xFF38BDF8) else vybColors.linkAccent,
                        trackColor = if (isMine) Color.White.copy(alpha = 0.18f) else vybColors.hairlineBorder,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(CircleShape)
                    )
                }
            }
        }
        Text(
            text = if (totalVotes == 1) "1 voto" else "$totalVotes votos",
            style = MaterialTheme.typography.labelSmall,
            color = sub
        )
    }
}

@Composable
private fun SpotlightQuickActionsCard(
    message: ChatMessage,
    canEdit: Boolean,
    remainingEditMinutes: Int,
    canDeleteForEveryone: Boolean,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onRequestDeleteForEveryone: () -> Unit
) {
    val vybColors = LocalVybColors.current
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = if (vybColors.isDark) Color(0xF0111C36) else Color(0xFAFFFFFF),
        border = BorderStroke(
            1.dp,
            if (vybColors.isDark) Color(0x736488EA) else Color(0x552563EB)
        ),
        tonalElevation = 8.dp,
        shadowElevation = 10.dp,
        modifier = Modifier
            .widthIn(min = 230.dp, max = 300.dp)
            .testTag("message_floating_menu")
    ) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            if (canEdit) {
                FloatingMenuActionRow(
                    icon = Icons.Filled.Edit,
                    title = "Editar mensaje",
                    subtitle = "Te quedan $remainingEditMinutes min para editar",
                    tint = VybElectricBlue,
                    testTag = "menu_action_edit",
                    onClick = onEdit
                )
            }
            if (message.text.isNotBlank() && message.deletedForEveryone != true) {
                FloatingMenuActionRow(
                    icon = Icons.Filled.ContentCopy,
                    title = "Copiar texto",
                    subtitle = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    testTag = "menu_action_copy",
                    onClick = onCopy
                )
            }
            if (canDeleteForEveryone) {
                FloatingMenuActionRow(
                    icon = Icons.Filled.DeleteForever,
                    title = "Eliminar para ambos",
                    subtitle = "Nadie podrá leer el contenido original",
                    tint = MaterialTheme.colorScheme.error,
                    testTag = "menu_action_delete_both",
                    onClick = onRequestDeleteForEveryone
                )
            }
            if (!canEdit && !canDeleteForEveryone && message.text.isBlank()) {
                Text(
                    text = "No hay acciones disponibles para este mensaje.",
                    style = MaterialTheme.typography.bodySmall,
                    color = vybColors.secondaryText,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun FloatingMenuActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    tint: Color,
    testTag: String,
    onClick: () -> Unit
) {
    val vybColors = LocalVybColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(21.dp)
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (tint == MaterialTheme.colorScheme.error) tint else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = vybColors.secondaryText
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttachmentOptionsSheet(
    recentMessages: List<ChatMessage>,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onPickPhotos: () -> Unit,
    onPickVideos: () -> Unit,
    onOpenSystemGallery: () -> Unit,
    onOpenCamera: () -> Unit,
    onPickDocument: () -> Unit,
    onShareLocation: () -> Unit,
    onPickContact: () -> Unit,
    onCreatePoll: () -> Unit,
    onSelectRecentMedia: (ChatMessage) -> Unit
) {
    val vybColors = LocalVybColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var mediaFilter by remember { mutableStateOf(RecentMediaFilter.ALL) }

    val recentMedia = remember(recentMessages, mediaFilter) {
        recentMessages
            .asReversed()
            .filter { msg ->
                msg.deletedForEveryone != true &&
                    msg.messageType == "media" &&
                    (msg.mediaType == "image" || msg.mediaType == "video") &&
                    when (mediaFilter) {
                        RecentMediaFilter.ALL -> true
                        RecentMediaFilter.PHOTOS -> msg.mediaType == "image"
                        RecentMediaFilter.VIDEOS -> msg.mediaType == "video"
                    }
            }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("chat_attachment_sheet")
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isBusy) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(CircleShape)
                )
            }

            // 7 separate WhatsApp-style attachment buttons across 2 rows
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                AttachmentTileButton(
                    icon = Icons.Filled.PhotoLibrary,
                    label = "Fotos",
                    tint = VybElectricBlue,
                    testTag = "attach_photos",
                    onClick = onPickPhotos,
                    modifier = Modifier.weight(1f)
                )
                AttachmentTileButton(
                    icon = Icons.Filled.VideoLibrary,
                    label = "Videos",
                    tint = VybViolet,
                    testTag = "attach_videos",
                    onClick = onPickVideos,
                    modifier = Modifier.weight(1f)
                )
                AttachmentTileButton(
                    icon = Icons.Filled.CameraAlt,
                    label = "Cámara",
                    tint = VybPink,
                    testTag = "attach_camera",
                    onClick = onOpenCamera,
                    modifier = Modifier.weight(1f)
                )
                AttachmentTileButton(
                    icon = Icons.Filled.Description,
                    label = "Documentos",
                    tint = Color(0xFF6366F1),
                    testTag = "attach_document",
                    onClick = onPickDocument,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                AttachmentTileButton(
                    icon = Icons.Filled.LocationOn,
                    label = "Ubicación",
                    tint = vybColors.sinpeEmerald,
                    testTag = "attach_location",
                    onClick = onShareLocation,
                    modifier = Modifier.weight(1f)
                )
                AttachmentTileButton(
                    icon = Icons.Filled.Contacts,
                    label = "Contacto",
                    tint = Color(0xFF0EA5E9),
                    testTag = "attach_contact",
                    onClick = onPickContact,
                    modifier = Modifier.weight(1f)
                )
                AttachmentTileButton(
                    icon = Icons.Filled.Poll,
                    label = "Encuestas",
                    tint = Color(0xFFF59E0B),
                    testTag = "attach_poll",
                    onClick = onCreatePoll,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.weight(1f))
            }

            HorizontalDivider(thickness = 0.75.dp, color = vybColors.hairlineBorder)

            // Expandable recent chat media & full gallery launcher
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Recientes en este chat",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                TextButton(
                    onClick = onOpenSystemGallery,
                    modifier = Modifier.testTag("attach_full_gallery_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.PermMedia,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text("Galería completa")
                }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(RecentMediaFilter.entries) { f ->
                    FilterChip(
                        selected = mediaFilter == f,
                        onClick = { mediaFilter = f },
                        label = { Text(f.label, style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }

            if (recentMedia.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = vybColors.nestedSurface,
                    border = BorderStroke(0.75.dp, vybColors.hairlineBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable(onClick = onOpenSystemGallery)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PermMedia,
                            contentDescription = null,
                            tint = vybColors.linkAccent,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = "Sin fotos o videos recientes en el chat",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Toca aquí para abrir la galería completa del dispositivo",
                            style = MaterialTheme.typography.bodySmall,
                            color = vybColors.secondaryText,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 104.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 190.dp, max = 460.dp)
                        .padding(bottom = 20.dp)
                        .testTag("attach_recent_media_grid")
                ) {
                    gridItems(recentMedia, key = { it.id }) { mediaMsg ->
                        val preview = mediaMsg.mediaPreviewData
                        val cachedBmp = remember(preview) {
                            preview?.let { BitmapMemoryCache.getCached(it, maxDim = 360) }
                        }
                        val thumbBmp by produceState(initialValue = cachedBmp, key1 = preview) {
                            value = preview?.let { BitmapMemoryCache.decodeDataUriCached(it, maxDim = 360) }
                        }
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { onSelectRecentMedia(mediaMsg) }
                                .testTag("recent_media_tile_${mediaMsg.id}"),
                            contentAlignment = Alignment.Center
                        ) {
                            val bmp = thumbBmp
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp,
                                    contentDescription = mediaMsg.text.ifBlank { "Archivo multimedia reciente" },
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = if (mediaMsg.mediaType == "video") Icons.Filled.PlayCircleFilled else Icons.Filled.PhotoLibrary,
                                    contentDescription = null,
                                    tint = VybElectricBlue,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            if (mediaMsg.mediaType == "video") {
                                Surface(
                                    shape = CircleShape,
                                    color = Color.Black.copy(alpha = 0.55f),
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PlayArrow,
                                        contentDescription = "Video",
                                        tint = Color.White,
                                        modifier = Modifier
                                            .padding(4.dp)
                                            .size(16.dp)
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
}

@Composable
private fun AttachmentTileButton(
    icon: ImageVector,
    label: String,
    tint: Color,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val vybColors = LocalVybColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = tint.copy(alpha = if (vybColors.isDark) 0.2f else 0.12f),
            border = BorderStroke(0.9.dp, tint.copy(alpha = 0.45f)),
            modifier = Modifier.size(54.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = tint,
                    modifier = Modifier.size(25.dp)
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ContactShareDialog(
    contacts: List<SavedContact>,
    peerSinpeProfiles: Map<String, SinpeProfile?> = emptyMap(),
    onSelectContact: (SavedContact) -> Unit,
    onDismiss: () -> Unit
) {
    val vybColors = LocalVybColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Compartir contacto") },
        text = {
            if (contacts.isEmpty()) {
                Text(
                    text = "Aún no tienes contactos guardados. Búscalos primero desde el ícono de Contactos en la pantalla principal de Chats.",
                    style = MaterialTheme.typography.bodySmall,
                    color = vybColors.secondaryText
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(contacts, key = { it.contactUid }) { contact ->
                        val resolvedBank = peerSinpeProfiles[contact.contactUid]?.sinpeBank?.takeIf { it.isNotBlank() }
                            ?: contact.sinpeBank.takeIf { it.isNotBlank() }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onSelectContact(contact) }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            VybAvatar(
                                avatarUrl = contact.avatarUrl,
                                displayName = contact.displayName,
                                size = 40.dp
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = contact.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (resolvedBank != null) {
                                        "@${contact.username} · $resolvedBank"
                                    } else {
                                        "@${contact.username} · SINPE protegido"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = vybColors.secondaryText
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}

@Composable
private fun PollCreateDialog(
    onCreatePoll: (question: String, options: List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var question by remember { mutableStateOf("") }
    var options by remember { mutableStateOf(listOf("", "")) }
    val validOptions = options.map { it.trim() }.filter { it.isNotBlank() }.distinct()
    val isValid = question.trim().isNotBlank() && validOptions.size >= 2

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nueva encuesta") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it.take(240) },
                    label = { Text("Pregunta") },
                    placeholder = { Text("Ej. ¿A qué hora nos vemos?") },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("poll_question_input")
                )
                options.forEachIndexed { idx, opt ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = opt,
                            onValueChange = { value ->
                                options = options.toMutableList().also { it[idx] = value.take(80) }
                            },
                            label = { Text("Opción ${idx + 1}") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        )
                        if (options.size > 2) {
                            IconButton(
                                onClick = {
                                    options = options.toMutableList().also { it.removeAt(idx) }
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Quitar opción")
                            }
                        }
                    }
                }
                if (options.size < 6) {
                    TextButton(
                        onClick = { options = options + "" },
                        modifier = Modifier.align(Alignment.Start)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Agregar opción")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreatePoll(question, validOptions) },
                enabled = isValid,
                colors = ButtonDefaults.buttonColors(containerColor = VybElectricBlue),
                modifier = Modifier.testTag("poll_send_button")
            ) {
                Text("Publicar encuesta", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun AudioMessageContent(
    isMine: Boolean,
    isPlaying: Boolean,
    isLoading: Boolean,
    progress: Float,
    durationLabel: String,
    onTogglePlay: () -> Unit
) {
    val vybColors = LocalVybColors.current
    val contentTint = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface
    val subTint = if (isMine) Color.White.copy(alpha = 0.8f) else vybColors.secondaryText
    val pillColor = if (isMine) Color.White.copy(alpha = 0.22f) else vybColors.linkAccent.copy(alpha = 0.14f)
    val iconTint = if (isMine) Color.White else vybColors.linkAccent

    Row(
        modifier = Modifier.widthIn(min = 190.dp, max = 250.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onTogglePlay,
            enabled = !isLoading,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(pillColor)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = iconTint,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pausar nota de voz" else "Reproducir nota de voz",
                    tint = iconTint
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    Icons.Filled.GraphicEq,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "Nota de voz",
                    style = MaterialTheme.typography.labelLarge,
                    color = contentTint,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(5.dp))
            LinearProgressIndicator(
                progress = { progress },
                color = iconTint,
                trackColor = if (isMine) Color.White.copy(alpha = 0.28f) else vybColors.hairlineBorder,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = durationLabel,
                style = MaterialTheme.typography.labelSmall.merge(VybMonoTabularStyle),
                color = subTint
            )
        }
    }
}

@Composable
private fun SinpeCreateDialog(
    defaultPeopleCount: Int,
    onConfirm: (amount: Long, splitCount: Int, reason: String) -> Unit,
    onDismiss: () -> Unit
) {
    val vybColors = LocalVybColors.current
    var amountText by remember { mutableStateOf("") }
    var splitText by remember { mutableStateOf(defaultPeopleCount.toString()) }
    var reason by remember { mutableStateOf("") }

    val amount = amountText.toLongOrNull() ?: 0L
    val split = (splitText.toIntOrNull() ?: 1).coerceIn(1, 30)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo cobro SINPE Móvil") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() }.take(9) },
                    label = { Text("Monto total en colones (₡)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = splitText,
                    onValueChange = { splitText = it.filter { c -> c.isDigit() }.take(2) },
                    label = { Text("Dividir entre personas") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Motivo o detalle") },
                    placeholder = { Text("Ej. Cena, café, entradas...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(amount, split, reason.ifBlank { "Cobro SINPE Móvil" })
                },
                enabled = amount > 0L,
                colors = ButtonDefaults.buttonColors(containerColor = vybColors.sinpeEmerald)
            ) {
                Text("Enviar tarjeta SINPE", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}


