package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Chat
import com.example.data.model.Post
import com.example.data.model.PostComment
import com.example.data.model.PublicUser
import com.example.data.model.SavedContact
import com.example.data.model.SinpeVisibility
import com.example.data.model.UiState
import com.example.ui.components.EmptyStateView
import com.example.ui.components.PostCommentsModalSheet
import com.example.ui.components.UserStatusBadge
import com.example.ui.components.VybAvatar
import com.example.ui.components.VybPostCard
import com.example.ui.components.VybPrimaryGradientButton
import com.example.ui.components.ZoomableMediaLightbox
import com.example.ui.components.formatCostaRicaPhone
import com.example.ui.components.sharePostExternally
import com.example.ui.theme.LocalVybColors
import com.example.ui.theme.VybElectricBlue
import com.example.ui.theme.VybMonoTabularStyle
import com.example.ui.viewmodel.ChatViewModel
import com.example.ui.viewmodel.FeedViewModel

data class GroupMemberSelection(
    val uid: String,
    val displayName: String,
    val username: String = "",
    val avatarUrl: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactProfileSheet(
    contactUid: String,
    fallbackName: String,
    fallbackUsername: String? = null,
    fallbackAvatar: String? = null,
    currentUser: PublicUser,
    chatViewModel: ChatViewModel,
    feedViewModel: FeedViewModel,
    isBlocked: Boolean = false,
    onToggleBlock: (uid: String, displayName: String, currentlyBlocked: Boolean) -> Unit = { _, _, _ -> },
    onDismiss: () -> Unit,
    onOpenDirectChat: (uid: String, name: String, avatar: String?) -> Unit,
    onStartCall: (uid: String, name: String, avatar: String?, isVideo: Boolean) -> Unit
) {
    val context = LocalContext.current
    val vybColors = LocalVybColors.current
    val peerProfiles by chatViewModel.peerProfiles.collectAsStateWithLifecycle()
    val peerSinpeProfiles by chatViewModel.peerSinpeProfiles.collectAsStateWithLifecycle()
    val bookmarks by feedViewModel.bookmarkedPostIds.collectAsStateWithLifecycle()
    val comments by feedViewModel.commentsState.collectAsStateWithLifecycle()
    val allowedAuthorIds by feedViewModel.allowedAuthorIds.collectAsStateWithLifecycle()
    val canViewContactPosts = contactUid in allowedAuthorIds

    val postsFlow = remember(contactUid) { feedViewModel.observePostsForAuthor(contactUid) }
    val contactPosts by postsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    LaunchedEffect(contactUid) {
        chatViewModel.ensureObservingPublicProfile(contactUid)
        feedViewModel.ensureObservingAuthor(contactUid)
    }

    val liveProfile = if (contactUid == currentUser.userId) currentUser else peerProfiles[contactUid]
    val liveSinpe = peerSinpeProfiles[contactUid]
    val displayName = liveProfile?.displayName?.takeIf { it.isNotBlank() } ?: fallbackName.ifBlank { "Usuario VYB" }
    val username = liveProfile?.username?.takeIf { it.isNotBlank() } ?: fallbackUsername.orEmpty()
    val avatarUrl = liveProfile?.avatarUrl?.takeIf { it.isNotBlank() } ?: fallbackAvatar
    val quickStatus = liveProfile?.quickStatus?.takeIf { it.isNotBlank() }
    val bio = liveProfile?.bio?.takeIf { it.isNotBlank() }
    val locationLabel = liveProfile?.locationLabel?.takeIf { it.isNotBlank() }
    val sinpeVis = SinpeVisibility.fromKey(liveSinpe?.visibility ?: liveProfile?.sinpeVisibility)
    val visibleSinpeBank = liveSinpe?.sinpeBank?.takeIf { it.isNotBlank() }
        ?: liveProfile?.sinpeBank?.takeIf { it.isNotBlank() && sinpeVis == SinpeVisibility.EVERYONE }
    val visibleSinpePhone = formatCostaRicaPhone(liveSinpe?.sinpePhone)

    var activeCommentsPost by remember { mutableStateOf<Post?>(null) }
    var pendingDeletePost by remember { mutableStateOf<Post?>(null) }
    var pendingDeleteComment by remember { mutableStateOf<Pair<String, PostComment>?>(null) }
    var lightboxMedia by remember { mutableStateOf<Triple<String, String?, String?>?>(null) }
    var showBlockConfirmDialog by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .testTag("contact_profile_sheet"),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        VybAvatar(
                            avatarUrl = avatarUrl,
                            displayName = displayName,
                            size = 82.dp,
                            showGradientRing = true,
                            isVerified = liveProfile?.verified == true
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            if (liveProfile?.verified == true) {
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Filled.Verified,
                                    contentDescription = "Verificado",
                                    tint = vybColors.linkAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        if (username.isNotBlank()) {
                            Text(
                                text = "@$username",
                                style = MaterialTheme.typography.bodyMedium.merge(VybMonoTabularStyle),
                                color = vybColors.secondaryText
                            )
                        }
                        if (quickStatus != null) {
                            Spacer(Modifier.height(8.dp))
                            UserStatusBadge(
                                status = quickStatus,
                                modifier = Modifier.testTag("contact_profile_status")
                            )
                        }
                        if (bio != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = bio,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (locationLabel != null) {
                            Spacer(Modifier.height(6.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.LocationOn,
                                    contentDescription = null,
                                    tint = vybColors.secondaryText,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = locationLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = vybColors.secondaryText
                                )
                            }
                        }
                    }
                }

                // Compact SINPE card respecting privacy rules
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = vybColors.nestedSurface,
                        border = BorderStroke(0.75.dp, vybColors.hairlineBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PhoneAndroid,
                                contentDescription = null,
                                tint = vybColors.sinpeEmerald,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "SINPE Móvil",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = vybColors.secondaryText
                                )
                                Text(
                                    text = when {
                                        visibleSinpeBank != null && visibleSinpePhone != null ->
                                            "$visibleSinpeBank · $visibleSinpePhone"
                                        visibleSinpeBank != null ->
                                            visibleSinpeBank
                                        sinpeVis == SinpeVisibility.HIDDEN ->
                                            "SINPE privado"
                                        else ->
                                            "SINPE visible al aceptar contacto"
                                    },
                                    style = MaterialTheme.typography.bodySmall.merge(VybMonoTabularStyle),
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (visibleSinpeBank != null) vybColors.sinpeEmerald else vybColors.secondaryText
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = vybColors.sinpeEmerald.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = sinpeVis.shortBadge,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = vybColors.sinpeEmerald,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }

                if (contactUid != currentUser.userId) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    onDismiss()
                                    onOpenDirectChat(contactUid, displayName, avatarUrl)
                                },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = VybElectricBlue),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("contact_profile_message_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Chat,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Mensaje", color = Color.White)
                            }
                            OutlinedButton(
                                onClick = {
                                    onDismiss()
                                    onStartCall(contactUid, displayName, avatarUrl, false)
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("contact_profile_voice_call_button")
                            ) {
                                Icon(Icons.Filled.Call, contentDescription = "Llamada de voz", modifier = Modifier.size(18.dp))
                            }
                            OutlinedButton(
                                onClick = {
                                    onDismiss()
                                    onStartCall(contactUid, displayName, avatarUrl, true)
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("contact_profile_video_call_button")
                            ) {
                                Icon(Icons.Filled.Videocam, contentDescription = "Videollamada", modifier = Modifier.size(18.dp))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                if (isBlocked) {
                                    onToggleBlock(contactUid, displayName, true)
                                } else {
                                    showBlockConfirmDialog = true
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(
                                1.dp,
                                if (isBlocked) vybColors.sinpeEmerald.copy(alpha = 0.6f)
                                else MaterialTheme.colorScheme.error.copy(alpha = 0.55f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("contact_profile_block_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Block,
                                contentDescription = null,
                                tint = if (isBlocked) vybColors.sinpeEmerald else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (isBlocked) "Desbloquear contacto" else "Bloquear contacto",
                                color = if (isBlocked) vybColors.sinpeEmerald else MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                item {
                    HorizontalDivider(thickness = 0.75.dp, color = vybColors.hairlineBorder)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Publicaciones (${contactPosts.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (contactPosts.isEmpty()) {
                    item {
                        EmptyStateView(
                            title = if (canViewContactPosts) "Sin publicaciones recientes" else "Publicaciones solo para contactos",
                            subtitle = if (canViewContactPosts) {
                                "$displayName aún no ha compartido publicaciones visibles."
                            } else {
                                "Agrega a $displayName como contacto en VYB para ver sus publicaciones e historias."
                            }
                        )
                    }
                } else {
                    items(contactPosts, key = { it.id }) { post ->
                        VybPostCard(
                            post = post,
                            currentUserId = currentUser.userId,
                            authorAvatarUrlOverride = avatarUrl,
                            authorStatus = quickStatus,
                            isBookmarked = post.id in bookmarks,
                            onLike = { feedViewModel.toggleLike(post) },
                            onComment = {
                                activeCommentsPost = post
                                feedViewModel.openComments(post.id)
                            },
                            onShare = {
                                sharePostExternally(context, post) {
                                    feedViewModel.sharePost(post.id)
                                }
                            },
                            onBookmark = { feedViewModel.toggleBookmark(post.id) },
                            onDeletePost = { pendingDeletePost = post },
                            onTagClick = { },
                            onMediaClick = {
                                lightboxMedia = Triple(post.mediaType, post.mediaPreviewData, post.mediaId)
                            },
                            outerPadding = PaddingValues(0.dp)
                        )
                    }
                }
            }
        }
    }

    if (showBlockConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showBlockConfirmDialog = false },
            title = { Text("Bloquear a $displayName") },
            text = { Text("Esta persona aparecerá en Configuración > Contactos Bloqueados y podrás desbloquearla cuando quieras.") },
            confirmButton = {
                Button(
                    onClick = {
                        showBlockConfirmDialog = false
                        onToggleBlock(contactUid, displayName, false)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Bloquear", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockConfirmDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    activeCommentsPost?.let { post ->
        PostCommentsModalSheet(
            post = post,
            comments = comments,
            currentUserId = currentUser.userId,
            onDismiss = {
                activeCommentsPost = null
                feedViewModel.openComments(null)
            },
            onSendComment = { text, onSent ->
                feedViewModel.addComment(post.id, currentUser, text, onSent)
            },
            onRequestDeleteComment = { comment ->
                pendingDeleteComment = post.id to comment
            }
        )
    }

    lightboxMedia?.let { (type, preview, mediaId) ->
        ZoomableMediaLightbox(
            mediaType = type,
            previewDataUri = preview,
            mediaId = mediaId,
            loadFullMediaFile = { id -> feedViewModel.loadFullMediaFile(context, id) },
            onDismiss = { lightboxMedia = null }
        )
    }

    pendingDeletePost?.let { postToDelete ->
        AlertDialog(
            onDismissRequest = { pendingDeletePost = null },
            title = { Text("Eliminar publicación") },
            text = { Text("¿Deseas eliminar esta publicación por completo?") },
            confirmButton = {
                Button(
                    onClick = {
                        pendingDeletePost = null
                        feedViewModel.deletePost(postToDelete)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Eliminar", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeletePost = null }) { Text("Cancelar") }
            }
        )
    }

    pendingDeleteComment?.let { (postId, comment) ->
        AlertDialog(
            onDismissRequest = { pendingDeleteComment = null },
            title = { Text("Eliminar comentario") },
            text = { Text("¿Deseas eliminar este comentario?") },
            confirmButton = {
                Button(
                    onClick = {
                        pendingDeleteComment = null
                        feedViewModel.deleteComment(postId, comment)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Eliminar", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteComment = null }) { Text("Cancelar") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ContactsAndGroupSheet(
    chatViewModel: ChatViewModel,
    currentUser: PublicUser,
    contacts: List<SavedContact>,
    onOpenContactProfile: (uid: String, name: String, username: String?, avatar: String?) -> Unit = { _, _, _, _ -> },
    onDismiss: () -> Unit
) {
    val vybColors = LocalVybColors.current
    val lookupState by chatViewModel.lookupResult.collectAsStateWithLifecycle()
    val peerSinpeProfiles by chatViewModel.peerSinpeProfiles.collectAsStateWithLifecycle()

    var activeTab by remember { mutableIntStateOf(0) } // 0 = Directorio, 1 = Crear Grupo
    var searchInput by remember { mutableStateOf("") }
    var requestNote by remember { mutableStateOf("") }
    var groupTitle by remember { mutableStateOf("") }
    var groupUserQuery by remember { mutableStateOf("") }
    var selectedMembers by remember { mutableStateOf<Map<String, GroupMemberSelection>>(emptyMap()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TabRow(selectedTabIndex = activeTab) {
                    Tab(
                        selected = activeTab == 0,
                        onClick = { activeTab = 0 },
                        text = { Text("Directorio y Contactos") }
                    )
                    Tab(
                        selected = activeTab == 1,
                        onClick = { activeTab = 1 },
                        text = { Text("Crear Grupo") },
                        modifier = Modifier.testTag("tab_create_group")
                    )
                }

                if (activeTab == 0) {
                    OutlinedTextField(
                        value = searchInput,
                        onValueChange = { searchInput = it },
                        label = { Text("Buscar por @usuario o teléfono (+506)") },
                        placeholder = { Text("Ej. @ana_cr o 88887777") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("directory_search_input"),
                        shape = RoundedCornerShape(16.dp)
                    )
                    VybPrimaryGradientButton(
                        text = "Buscar en directorio",
                        enabled = searchInput.isNotBlank(),
                        onClick = { chatViewModel.lookupUserExact(searchInput) },
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "directory_search_button"
                    )

                    when (val l = lookupState) {
                        UiState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                        is UiState.Error -> Text(l.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        is UiState.Success -> {
                            val found = l.data
                            if (found == null) {
                                Text(
                                    "No encontramos ninguna cuenta exacta con ese dato.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = vybColors.secondaryText
                                )
                            } else {
                                Card(
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(containerColor = vybColors.nestedSurface),
                                    border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable {
                                                    onOpenContactProfile(found.uid, found.displayName, found.username, found.avatarUrl)
                                                }
                                        ) {
                                            VybAvatar(avatarUrl = found.avatarUrl, displayName = found.displayName, size = 44.dp)
                                            Spacer(Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(found.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                                val dirVisibility = SinpeVisibility.fromKey(found.sinpeVisibility)
                                                val dirSinpeLabel = if (found.sinpeBank.isNotBlank()) {
                                                    found.sinpeBank
                                                } else if (dirVisibility == SinpeVisibility.HIDDEN) {
                                                    "SINPE privado"
                                                } else {
                                                    "SINPE protegido (solo contactos)"
                                                }
                                                Text("@${found.username} · $dirSinpeLabel", style = MaterialTheme.typography.bodySmall, color = vybColors.secondaryText)
                                            }
                                            if (found.uid != currentUser.userId) {
                                                IconButton(
                                                    onClick = { chatViewModel.saveContact(found) },
                                                    modifier = Modifier.size(48.dp)
                                                ) {
                                                    Icon(Icons.Filled.PersonAdd, contentDescription = "Guardar contacto", tint = vybColors.linkAccent)
                                                }
                                            }
                                        }
                                        if (found.uid != currentUser.userId) {
                                            OutlinedTextField(
                                                value = requestNote,
                                                onValueChange = { requestNote = it.take(180) },
                                                label = { Text("Nota de solicitud (opcional)") },
                                                placeholder = { Text("Ej. ¡Hola! Soy de VYB") },
                                                singleLine = true,
                                                shape = RoundedCornerShape(14.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                OutlinedButton(
                                                    onClick = {
                                                        chatViewModel.sendChatRequest(
                                                            currentUser = currentUser,
                                                            target = found,
                                                            note = requestNote,
                                                            onSent = { onDismiss() }
                                                        )
                                                    },
                                                    shape = RoundedCornerShape(14.dp),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .heightIn(min = 48.dp)
                                                        .testTag("send_chat_request_button")
                                                ) {
                                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(Modifier.width(6.dp))
                                                    Text("Enviar solicitud")
                                                }
                                                Button(
                                                    onClick = {
                                                        chatViewModel.startDirectChat(
                                                            currentUser = currentUser,
                                                            peerUid = found.uid,
                                                            peerName = found.displayName,
                                                            peerAvatar = found.avatarUrl
                                                        )
                                                        onDismiss()
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = vybColors.sinpeEmerald),
                                                    shape = RoundedCornerShape(14.dp),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .heightIn(min = 48.dp)
                                                        .testTag("start_direct_chat_button")
                                                ) {
                                                    Icon(Icons.Filled.AddComment, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                                                    Spacer(Modifier.width(6.dp))
                                                    Text("Chatear ahora", color = Color.White)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        null -> {}
                    }

                    HorizontalDivider(thickness = 0.75.dp, color = vybColors.hairlineBorder)
                    Text("Mis contactos guardados", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                    if (contacts.isEmpty()) {
                        Text(
                            "Aún no tienes contactos guardados. Búscalos arriba por @usuario o +506.",
                            style = MaterialTheme.typography.bodySmall,
                            color = vybColors.secondaryText,
                            modifier = Modifier.padding(bottom = 24.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 220.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(contacts, key = { it.contactUid }) { c ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable {
                                            chatViewModel.startDirectChat(
                                                currentUser = currentUser,
                                                peerUid = c.contactUid,
                                                peerName = c.displayName,
                                                peerAvatar = c.avatarUrl
                                            )
                                            onDismiss()
                                        }
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    VybAvatar(
                                        avatarUrl = c.avatarUrl,
                                        displayName = c.displayName,
                                        size = 40.dp,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .clickable {
                                                onOpenContactProfile(c.contactUid, c.displayName, c.username, c.avatarUrl)
                                            }
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(c.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        val liveSinpe = peerSinpeProfiles[c.contactUid]
                                        val contactBank = liveSinpe?.sinpeBank?.takeIf { it.isNotBlank() }
                                            ?: c.sinpeBank.takeIf { it.isNotBlank() }
                                        val contactPhone = liveSinpe?.sinpePhone?.takeIf { it.isNotBlank() }
                                            ?: c.sinpePhone?.takeIf { it.isNotBlank() }
                                        val subLabel = buildString {
                                            append("@${c.username}")
                                            if (contactBank != null) {
                                                append(" · ")
                                                append(contactBank)
                                                if (contactPhone != null) {
                                                    append(" · ")
                                                    append(contactPhone)
                                                }
                                            } else {
                                                append(" · SINPE protegido")
                                            }
                                        }
                                        Text(subLabel, style = MaterialTheme.typography.labelSmall, color = vybColors.secondaryText)
                                    }
                                    IconButton(
                                        onClick = { chatViewModel.removeContact(c.contactUid) },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Eliminar contacto")
                                    }
                                }
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = groupTitle,
                        onValueChange = { groupTitle = it.take(60) },
                        label = { Text("Nombre del grupo") },
                        placeholder = { Text("Ej. Viaje a Guanacaste") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("group_title_input"),
                        shape = RoundedCornerShape(16.dp)
                    )

                    if (selectedMembers.isNotEmpty()) {
                        Text(
                            text = "Integrantes seleccionados (${selectedMembers.size})",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            selectedMembers.values.forEach { member ->
                                InputChip(
                                    selected = true,
                                    onClick = { selectedMembers = selectedMembers - member.uid },
                                    label = { Text(member.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    trailingIcon = {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = "Quitar integrante",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                )
                            }
                        }
                    }

                    // Inline user search for adding any VYB user beyond saved contacts
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = groupUserQuery,
                            onValueChange = { groupUserQuery = it },
                            label = { Text("Agregar por @usuario o +506") },
                            placeholder = { Text("@usuario") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("group_user_search_input")
                        )
                        IconButton(
                            onClick = { chatViewModel.lookupUserExact(groupUserQuery) },
                            enabled = groupUserQuery.isNotBlank(),
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("group_user_search_button")
                        ) {
                            Icon(Icons.Filled.Search, contentDescription = "Buscar usuario para el grupo")
                        }
                    }

                    when (val l = lookupState) {
                        UiState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                        is UiState.Error -> Text(l.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        is UiState.Success -> {
                            val found = l.data
                            if (found == null) {
                                Text(
                                    "No encontramos ninguna cuenta con ese dato.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = vybColors.secondaryText
                                )
                            } else if (found.uid != currentUser.userId) {
                                val alreadySelected = found.uid in selectedMembers
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = vybColors.nestedSurface,
                                    border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        VybAvatar(avatarUrl = found.avatarUrl, displayName = found.displayName, size = 38.dp)
                                        Spacer(Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(found.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                            Text("@${found.username}", style = MaterialTheme.typography.labelSmall, color = vybColors.secondaryText)
                                        }
                                        TextButton(
                                            onClick = {
                                                selectedMembers = if (alreadySelected) {
                                                    selectedMembers - found.uid
                                                } else {
                                                    selectedMembers + (
                                                        found.uid to GroupMemberSelection(
                                                            uid = found.uid,
                                                            displayName = found.displayName,
                                                            username = found.username,
                                                            avatarUrl = found.avatarUrl
                                                        )
                                                    )
                                                }
                                            },
                                            modifier = Modifier
                                                .heightIn(min = 48.dp)
                                                .testTag("group_lookup_toggle_button")
                                        ) {
                                            Text(if (alreadySelected) "Quitar" else "Agregar")
                                        }
                                    }
                                }
                            }
                        }
                        null -> {}
                    }

                    Text("Tus contactos guardados (puedes elegir varios):", style = MaterialTheme.typography.labelLarge)
                    if (contacts.isEmpty()) {
                        Text(
                            "No tienes contactos guardados todavía; puedes buscar personas arriba por @usuario.",
                            style = MaterialTheme.typography.bodySmall,
                            color = vybColors.secondaryText
                        )
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                            items(contacts, key = { it.contactUid }) { c ->
                                val checked = c.contactUid in selectedMembers
                                val toggleMember = {
                                    selectedMembers = if (checked) {
                                        selectedMembers - c.contactUid
                                    } else {
                                        selectedMembers + (
                                            c.contactUid to GroupMemberSelection(
                                                uid = c.contactUid,
                                                displayName = c.displayName,
                                                username = c.username,
                                                avatarUrl = c.avatarUrl
                                            )
                                        )
                                    }
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .clickable { toggleMember() }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = { toggleMember() }
                                    )
                                    VybAvatar(avatarUrl = c.avatarUrl, displayName = c.displayName, size = 36.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(c.displayName, style = MaterialTheme.typography.bodyMedium)
                                        Text("@${c.username}", style = MaterialTheme.typography.labelSmall, color = vybColors.secondaryText)
                                    }
                                }
                            }
                        }
                    }

                    VybPrimaryGradientButton(
                        text = if (selectedMembers.isEmpty()) {
                            "Crear chat grupal"
                        } else {
                            "Crear chat grupal (${selectedMembers.size + 1} integrantes)"
                        },
                        enabled = groupTitle.isNotBlank() && selectedMembers.isNotEmpty(),
                        onClick = {
                            val members = selectedMembers.values.map {
                                Triple(it.uid, it.displayName, it.avatarUrl)
                            }
                            chatViewModel.createGroupChatWithMembers(currentUser, groupTitle, members)
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        testTag = "create_group_submit_button"
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GroupInfoMembersSheet(
    chat: Chat,
    currentUser: PublicUser,
    chatViewModel: ChatViewModel,
    onOpenMemberProfile: (uid: String, name: String, username: String?, avatar: String?) -> Unit,
    onGroupExited: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val vybColors = LocalVybColors.current
    val peerProfiles by chatViewModel.peerProfiles.collectAsStateWithLifecycle()
    val contacts by chatViewModel.contactsState.collectAsStateWithLifecycle()
    val lookupState by chatViewModel.lookupResult.collectAsStateWithLifecycle()

    var searchInput by remember { mutableStateOf("") }
    var pendingMembers by remember { mutableStateOf<Map<String, GroupMemberSelection>>(emptyMap()) }
    var showLeaveGroupConfirm by remember { mutableStateOf(false) }
    var showDeleteGroupConfirm by remember { mutableStateOf(false) }
    val existingUids = remember(chat.participantUids) { chat.participantUids.toSet() }
    val availableContacts = remember(contacts, existingUids) {
        contacts.filter { it.contactUid !in existingUids }
    }

    LaunchedEffect(chat.participantUids) {
        chat.participantUids.filter { it != currentUser.userId }.forEach { uid ->
            chatViewModel.ensureObservingPublicProfile(uid)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .testTag("group_info_members_sheet"),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    VybAvatar(
                        avatarUrl = null,
                        displayName = chat.title ?: "Grupo VYB",
                        size = 52.dp,
                        showGradientRing = true
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = chat.title?.takeIf { it.isNotBlank() } ?: "Grupo VYB",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${chat.participantUids.size} participantes",
                            style = MaterialTheme.typography.bodySmall,
                            color = vybColors.secondaryText
                        )
                    }
                }

                HorizontalDivider(thickness = 0.75.dp, color = vybColors.hairlineBorder)
                Text("Integrantes actuales", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    chat.participantUids.forEach { uid ->
                        val isMe = uid == currentUser.userId
                        val profile = if (isMe) currentUser else peerProfiles[uid]
                        val name = profile?.displayName?.takeIf { it.isNotBlank() }
                            ?: chat.participantNames[uid]
                            ?: "Usuario VYB"
                        val avatar = profile?.avatarUrl?.takeIf { it.isNotBlank() }
                            ?: chat.participantAvatars[uid]
                        val status = profile?.quickStatus

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .then(
                                    if (!isMe) {
                                        Modifier
                                            .clickable {
                                                onDismiss()
                                                onOpenMemberProfile(uid, name, profile?.username, avatar)
                                            }
                                            .testTag("group_member_row_$uid")
                                    } else Modifier
                                )
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            VybAvatar(
                                avatarUrl = avatar,
                                displayName = name,
                                size = 40.dp,
                                isVerified = profile?.verified == true
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isMe) "$name (Tú)" else name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                profile?.username?.takeIf { it.isNotBlank() }?.let { uname ->
                                    Text(
                                        text = "@$uname",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = vybColors.secondaryText
                                    )
                                }
                            }
                            if (!status.isNullOrBlank()) {
                                UserStatusBadge(status = status)
                            }
                            if (!isMe) {
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = "Ver perfil de $name",
                                    tint = vybColors.secondaryText,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(thickness = 0.75.dp, color = vybColors.hairlineBorder)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.GroupAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Agregar participantes", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }

                if (pendingMembers.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        pendingMembers.values.forEach { member ->
                            InputChip(
                                selected = true,
                                onClick = { pendingMembers = pendingMembers - member.uid },
                                label = { Text(member.displayName) },
                                trailingIcon = {
                                    Icon(Icons.Filled.Close, contentDescription = "Quitar", modifier = Modifier.size(16.dp))
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchInput,
                        onValueChange = { searchInput = it },
                        label = { Text("Buscar por @usuario o +506") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("group_add_search_input")
                    )
                    IconButton(
                        onClick = { chatViewModel.lookupUserExact(searchInput) },
                        enabled = searchInput.isNotBlank(),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("group_add_search_button")
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = "Buscar para agregar al grupo")
                    }
                }

                when (val l = lookupState) {
                    UiState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    is UiState.Error -> Text(l.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    is UiState.Success -> {
                        val found = l.data
                        if (found != null && found.uid !in existingUids) {
                            val selected = found.uid in pendingMembers
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = vybColors.nestedSurface,
                                border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    VybAvatar(avatarUrl = found.avatarUrl, displayName = found.displayName, size = 36.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(found.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        Text("@${found.username}", style = MaterialTheme.typography.labelSmall, color = vybColors.secondaryText)
                                    }
                                    TextButton(
                                        onClick = {
                                            pendingMembers = if (selected) {
                                                pendingMembers - found.uid
                                            } else {
                                                pendingMembers + (
                                                    found.uid to GroupMemberSelection(
                                                        uid = found.uid,
                                                        displayName = found.displayName,
                                                        username = found.username,
                                                        avatarUrl = found.avatarUrl
                                                    )
                                                )
                                            }
                                        },
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) {
                                        Text(if (selected) "Quitar" else "Agregar")
                                    }
                                }
                            }
                        } else if (found != null && found.uid in existingUids) {
                            Text(
                                "${found.displayName} ya es integrante de este grupo.",
                                style = MaterialTheme.typography.bodySmall,
                                color = vybColors.secondaryText
                            )
                        }
                    }
                    null -> {}
                }

                if (availableContacts.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        availableContacts.forEach { c ->
                            val checked = c.contactUid in pendingMembers
                            val toggle = {
                                pendingMembers = if (checked) {
                                    pendingMembers - c.contactUid
                                } else {
                                    pendingMembers + (
                                        c.contactUid to GroupMemberSelection(
                                            uid = c.contactUid,
                                            displayName = c.displayName,
                                            username = c.username,
                                            avatarUrl = c.avatarUrl
                                        )
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .clickable { toggle() },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = checked, onCheckedChange = { toggle() })
                                VybAvatar(avatarUrl = c.avatarUrl, displayName = c.displayName, size = 34.dp)
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(c.displayName, style = MaterialTheme.typography.bodyMedium)
                                    Text("@${c.username}", style = MaterialTheme.typography.labelSmall, color = vybColors.secondaryText)
                                }
                            }
                        }
                    }
                }

                if (pendingMembers.isNotEmpty()) {
                    VybPrimaryGradientButton(
                        text = "Agregar al grupo (${pendingMembers.size})",
                        onClick = {
                            val list = pendingMembers.values.map { Triple(it.uid, it.displayName, it.avatarUrl) }
                            chatViewModel.addGroupParticipants(chat.id, list) {
                                pendingMembers = emptyMap()
                                chatViewModel.clearLookup()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "group_add_members_confirm_button"
                    )
                }

                HorizontalDivider(thickness = 0.75.dp, color = vybColors.hairlineBorder)
                Text("Gestión del grupo", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                OutlinedButton(
                    onClick = { showLeaveGroupConfirm = true },
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("group_leave_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Salir del grupo",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (chat.createdBy == currentUser.userId) {
                    Button(
                        onClick = { showDeleteGroupConfirm = true },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("group_delete_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteForever,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Eliminar grupo para todos", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showLeaveGroupConfirm) {
        AlertDialog(
            onDismissRequest = { showLeaveGroupConfirm = false },
            title = { Text("Salir del grupo") },
            text = { Text("¿Deseas salir de \"${chat.title ?: "Grupo VYB"}\"? Ya no recibirás mensajes ni alertas de este grupo.") },
            confirmButton = {
                Button(
                    onClick = {
                        showLeaveGroupConfirm = false
                        chatViewModel.leaveGroup(chat.id, currentUser.displayName) {
                            onDismiss()
                            onGroupExited()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Salir", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveGroupConfirm = false }) { Text("Cancelar") }
            }
        )
    }

    if (showDeleteGroupConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteGroupConfirm = false },
            title = { Text("Eliminar grupo") },
            text = { Text("Se eliminará el grupo \"${chat.title ?: "Grupo VYB"}\" junto con todos sus mensajes para todos los integrantes. Esta acción no se puede deshacer.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteGroupConfirm = false
                        chatViewModel.deleteGroup(chat.id) {
                            onDismiss()
                            onGroupExited()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Eliminar grupo", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteGroupConfirm = false }) { Text("Cancelar") }
            }
        )
    }
}
