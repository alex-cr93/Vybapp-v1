package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.QrCodeEncoder
import com.example.data.model.Post
import com.example.data.model.PostComment
import com.example.data.model.PrivateUser
import com.example.data.model.PublicUser
import com.example.data.model.SinpeVisibility
import com.example.data.model.UiState
import com.example.data.model.VybConstants
import com.example.ui.components.CostaRicaPhoneVisualTransformation
import com.example.ui.components.EmptyStateView
import com.example.ui.components.PostCommentsModalSheet
import com.example.ui.components.VybAvatar
import com.example.ui.components.VybLogoBadge
import com.example.ui.components.VybPostCard
import com.example.ui.components.VybPrimaryGradientButton
import com.example.ui.components.ZoomableMediaLightbox
import com.example.ui.components.formatCostaRicaPhone
import com.example.ui.components.sharePostExternally
import com.example.ui.theme.LocalVybColors
import com.example.ui.theme.VybGradientBrush
import com.example.ui.theme.VybMonoTabularStyle
import com.example.ui.viewmodel.FeedViewModel
import com.example.ui.viewmodel.ProfileViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    profileViewModel: ProfileViewModel,
    feedViewModel: FeedViewModel,
    publicUser: PublicUser,
    privateUser: PrivateUser?,
    isAccountVerified: Boolean,
    fcmToken: String? = null,
    widthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val vybColors = LocalVybColors.current
    val isExpanded = widthSizeClass == WindowWidthSizeClass.Expanded

    val themeMode by profileViewModel.themeMode.collectAsStateWithLifecycle()
    val aiModel by profileViewModel.aiModelChoice.collectAsStateWithLifecycle()
    val privateLocationEnabled by profileViewModel.privateLocationEnabled.collectAsStateWithLifecycle()
    val privateLocation by profileViewModel.privateLocation.collectAsStateWithLifecycle()
    val blockedProfiles by profileViewModel.blockedProfiles.collectAsStateWithLifecycle()
    val blockedPhones by profileViewModel.blockedPhones.collectAsStateWithLifecycle()
    val isSaving by profileViewModel.isSaving.collectAsStateWithLifecycle()

    val myPostsState by feedViewModel.myPostsState.collectAsStateWithLifecycle()
    val savedPostsState by feedViewModel.savedPostsState.collectAsStateWithLifecycle()
    val bookmarks by feedViewModel.bookmarkedPostIds.collectAsStateWithLifecycle()
    val comments by feedViewModel.commentsState.collectAsStateWithLifecycle()
    val authorProfiles by feedViewModel.authorProfiles.collectAsStateWithLifecycle()

    var settingsPage by remember { mutableStateOf<ProfileSettingsPage?>(null) }
    var selectedContentTab by remember { mutableIntStateOf(0) } // 0 = Publicaciones, 1 = Guardados
    var showEditModal by remember { mutableStateOf(false) }
    var showPasswordModal by remember { mutableStateOf(false) }
    var showSinpeQrModal by remember { mutableStateOf(false) }
    var activeCommentsPost by remember { mutableStateOf<Post?>(null) }
    var lightboxMedia by remember { mutableStateOf<Triple<String, String?, String?>?>(null) }
    var pendingDeletePost by remember { mutableStateOf<Post?>(null) }
    var pendingDeleteComment by remember { mutableStateOf<Pair<String, PostComment>?>(null) }

    val effectiveSinpeBank = remember(privateUser?.sinpeBank, publicUser.sinpeBank) {
        privateUser?.sinpeBank?.takeIf { it.isNotBlank() }
            ?: publicUser.sinpeBank.ifBlank { "BAC Credomatic" }
    }
    val effectiveSinpeVisibility = remember(privateUser?.sinpeVisibility, publicUser.sinpeVisibility) {
        SinpeVisibility.fromKey(privateUser?.sinpeVisibility ?: publicUser.sinpeVisibility)
    }
    val formattedSinpePhone = remember(privateUser?.phone) {
        formatCostaRicaPhone(privateUser?.phone)
    }

    val avatarPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            profileViewModel.uploadAvatar(context, uri, publicUser, privateUser, isAccountVerified)
        }
    }

    val openCommentsForPost: (Post) -> Unit = { post ->
        activeCommentsPost = post
        feedViewModel.openComments(post.id)
    }

    val sharePostAction: (Post) -> Unit = { post ->
        sharePostExternally(context, post) {
            feedViewModel.sharePost(post.id)
        }
    }

    AnimatedContent(
        targetState = settingsPage != null,
        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(150)) },
        label = "profile_vs_settings_nav"
    ) { inSettings ->
        if (inSettings) {
            ProfileSettingsNavigator(
                currentPage = settingsPage ?: ProfileSettingsPage.LIST,
                onSelectPage = { settingsPage = it },
                onCloseSettings = { settingsPage = null },
                publicUser = publicUser,
                privateUser = privateUser,
                formattedSinpePhone = formattedSinpePhone,
                effectiveSinpeVisibility = effectiveSinpeVisibility,
                themeMode = themeMode,
                onSelectThemeMode = { profileViewModel.setThemeMode(it) },
                privateLocationEnabled = privateLocationEnabled,
                privateLocation = privateLocation,
                onEnablePrivateLocation = { profileViewModel.refreshPrivateLocation(context) },
                onDisablePrivateLocation = { profileViewModel.disablePrivateLocation() },
                aiModel = aiModel,
                onSelectAiModel = { profileViewModel.setAiModelChoice(it) },
                onUpdatePrivacySettings = { r, s, snd, c, sinpe ->
                    profileViewModel.updatePrivacySettings(r, s, snd, c, sinpe)
                },
                onUpdateLiveLocationPrivacy = { scope, durationMin, showDistance ->
                    profileViewModel.updateLiveLocationPrivacy(scope, durationMin, showDistance)
                },
                onUpdateSinpeVisibility = { visKey ->
                    profileViewModel.updateSinpeVisibility(visKey)
                },
                blockedProfiles = blockedProfiles,
                blockedPhones = blockedPhones,
                onUnblockUser = { profileViewModel.unblockUser(it) },
                onUnblockPhone = { profileViewModel.unblockPhone(it) },
                onBlockByIdentifier = { query, cb -> profileViewModel.blockByIdentifier(query, cb) },
                onOpenPasswordModal = { showPasswordModal = true },
                onSignOut = onSignOut,
                isExpanded = isExpanded
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                LazyColumn(
                    modifier = Modifier
                        .widthIn(max = if (isExpanded) 1040.dp else 640.dp)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Clean Content-Focused Header with Top-Right Three-Dots Menu (⋮)
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Perfil",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Tu identidad, publicaciones y guardados",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = vybColors.secondaryText
                                )
                            }
                            IconButton(
                                onClick = { settingsPage = ProfileSettingsPage.LIST },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .testTag("profile_settings_menu_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.MoreVert,
                                    contentDescription = "Configuración"
                                )
                            }
                        }
                    }

                    // Profile Identity Card
                    item {
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = vybColors.cardElevationDp),
                            border = BorderStroke(0.75.dp, vybColors.hairlineBorder)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(contentAlignment = Alignment.BottomEnd) {
                                    VybAvatar(
                                        avatarUrl = publicUser.avatarUrl,
                                        displayName = publicUser.displayName,
                                        size = 92.dp,
                                        showGradientRing = true,
                                        isVerified = publicUser.verified
                                    )
                                    IconButton(
                                        onClick = {
                                            avatarPicker.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(VybGradientBrush)
                                            .testTag("profile_change_avatar_button")
                                    ) {
                                        Icon(
                                            Icons.Filled.CameraAlt,
                                            contentDescription = "Cambiar foto de perfil",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                Spacer(Modifier.height(12.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = publicUser.displayName,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (publicUser.verified) {
                                        Spacer(Modifier.width(6.dp))
                                        Icon(
                                            Icons.Filled.Verified,
                                            contentDescription = "Verificado",
                                            tint = vybColors.linkAccent,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = buildString {
                                        append("@")
                                        append(publicUser.username)
                                        if (!formattedSinpePhone.isNullOrBlank()) {
                                            append(" · ")
                                            append(formattedSinpePhone)
                                        }
                                    },
                                    style = MaterialTheme.typography.bodyMedium.merge(VybMonoTabularStyle),
                                    color = vybColors.secondaryText,
                                    textAlign = TextAlign.Center
                                )
                                if (!publicUser.bio.isNullOrBlank()) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = publicUser.bio,
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = TextAlign.Center
                                    )
                                }

                                Spacer(Modifier.height(14.dp))
                                LazyRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("profile_status_carousel"),
                                    contentPadding = PaddingValues(horizontal = 2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    items(VybConstants.QUICK_STATUSES) { st ->
                                        FilterChip(
                                            selected = publicUser.quickStatus == st,
                                            onClick = { profileViewModel.updateQuickStatus(st) },
                                            label = {
                                                Text(
                                                    text = st,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
                                        )
                                    }
                                }

                                Spacer(Modifier.height(14.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = vybColors.nestedSurface,
                                        border = BorderStroke(0.75.dp, vybColors.hairlineBorder),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                val copyPhone = formattedSinpePhone ?: privateUser?.phone.orEmpty()
                                                if (copyPhone.isNotBlank()) {
                                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                    cm.setPrimaryClip(ClipData.newPlainText("Teléfono SINPE", copyPhone))
                                                    Toast.makeText(context, "Teléfono SINPE copiado", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(
                                                        context,
                                                        "Configura tu número SINPE en Editar perfil.",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                    showEditModal = true
                                                }
                                            }
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Filled.PhoneAndroid, contentDescription = null, tint = vybColors.sinpeEmerald, modifier = Modifier.size(15.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("SINPE Móvil", style = MaterialTheme.typography.labelSmall, color = vybColors.secondaryText)
                                                Spacer(Modifier.weight(1f))
                                                Icon(
                                                    imageVector = if (formattedSinpePhone != null) Icons.Filled.ContentCopy else Icons.Filled.Edit,
                                                    contentDescription = if (formattedSinpePhone != null) "Copiar teléfono" else "Configurar teléfono SINPE",
                                                    modifier = Modifier.size(14.dp),
                                                    tint = vybColors.secondaryText
                                                )
                                            }
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                text = formattedSinpePhone ?: "Sin configurar",
                                                style = MaterialTheme.typography.bodySmall.merge(VybMonoTabularStyle),
                                                fontWeight = FontWeight.Bold
                                            )
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = effectiveSinpeBank,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = vybColors.sinpeEmerald,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )
                                                Surface(
                                                    shape = RoundedCornerShape(999.dp),
                                                    color = vybColors.sinpeEmerald.copy(alpha = 0.14f)
                                                ) {
                                                    Text(
                                                        text = effectiveSinpeVisibility.shortBadge,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = vybColors.sinpeEmerald,
                                                        maxLines = 1,
                                                        modifier = Modifier
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                            .testTag("profile_sinpe_privacy_badge")
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = vybColors.nestedSurface,
                                        border = BorderStroke(0.75.dp, vybColors.hairlineBorder),
                                        modifier = Modifier.width(105.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text("Publicaciones", style = MaterialTheme.typography.labelSmall, color = vybColors.secondaryText)
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                text = publicUser.postsCount.toString(),
                                                style = MaterialTheme.typography.titleLarge.merge(VybMonoTabularStyle),
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.height(14.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = { showEditModal = true },
                                        shape = RoundedCornerShape(14.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                        modifier = Modifier
                                            .weight(1.25f)
                                            .heightIn(min = 52.dp)
                                            .testTag("profile_edit_button")
                                    ) {
                                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "Editar perfil y datos SINPE",
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = { showSinpeQrModal = true },
                                        shape = RoundedCornerShape(14.dp),
                                        border = BorderStroke(1.dp, vybColors.sinpeEmerald.copy(alpha = 0.65f)),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                        modifier = Modifier
                                            .weight(0.95f)
                                            .heightIn(min = 52.dp)
                                            .testTag("profile_sinpe_qr_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.QrCode2,
                                            contentDescription = null,
                                            tint = vybColors.sinpeEmerald,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "Mi QR SINPE",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = vybColors.sinpeEmerald,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Content Tabs: Publicaciones / Guardados
                    item {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(0.75.dp, vybColors.hairlineBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TabRow(
                                selectedTabIndex = selectedContentTab,
                                containerColor = Color.Transparent,
                                contentColor = MaterialTheme.colorScheme.primary
                            ) {
                                Tab(
                                    selected = selectedContentTab == 0,
                                    onClick = { selectedContentTab = 0 },
                                    modifier = Modifier.testTag("profile_tab_posts"),
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(Icons.Filled.DynamicFeed, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Text("Publicaciones", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                )
                                Tab(
                                    selected = selectedContentTab == 1,
                                    onClick = { selectedContentTab = 1 },
                                    modifier = Modifier.testTag("profile_tab_saved"),
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(Icons.Filled.BookmarkBorder, contentDescription = null, modifier = Modifier.size(16.dp))
                                            val savedCount = bookmarks.size
                                            Text(
                                                if (savedCount > 0) "Guardados ($savedCount)" else "Guardados",
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }

                    val activePostsState = if (selectedContentTab == 0) myPostsState else savedPostsState
                    when (val pState = activePostsState) {
                        UiState.Loading -> {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                        }
                        is UiState.Error -> {
                            item {
                                EmptyStateView(
                                    title = "No se pudo cargar esta sección",
                                    subtitle = pState.message
                                )
                            }
                        }
                        is UiState.Success -> {
                            if (pState.data.isEmpty()) {
                                item {
                                    if (selectedContentTab == 0) {
                                        EmptyStateView(
                                            title = "Aún no tienes publicaciones",
                                            subtitle = "Comparte fotos, momentos o ideas desde Inicio para verlas aquí.",
                                            modifier = Modifier.testTag("profile_empty_posts")
                                        )
                                    } else {
                                        EmptyStateView(
                                            title = "Aún no tienes publicaciones guardadas",
                                            subtitle = "Toca el ícono de marcador en cualquier publicación para guardarla en tu cuenta privada.",
                                            modifier = Modifier.testTag("profile_empty_saved")
                                        )
                                    }
                                }
                            } else if (isExpanded) {
                                val rows = pState.data.chunked(2)
                                items(
                                    items = rows,
                                    key = { pair -> pair.joinToString("_") { it.id } }
                                ) { rowPosts ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        rowPosts.forEach { post ->
                                            val liveAuthor = authorProfiles[post.authorId]
                                            val authorStatus = liveAuthor?.quickStatus
                                                ?: if (post.authorId == publicUser.userId) publicUser.quickStatus else null
                                            val authorAvatarOverride = liveAuthor?.avatarUrl?.takeIf { it.isNotBlank() }
                                                ?: if (post.authorId == publicUser.userId) publicUser.avatarUrl?.takeIf { it.isNotBlank() } else null
                                            VybPostCard(
                                                post = post,
                                                currentUserId = publicUser.userId,
                                                authorAvatarUrlOverride = authorAvatarOverride,
                                                authorStatus = authorStatus,
                                                isBookmarked = post.id in bookmarks,
                                                onLike = { feedViewModel.toggleLike(post) },
                                                onComment = { openCommentsForPost(post) },
                                                onShare = { sharePostAction(post) },
                                                onBookmark = { feedViewModel.toggleBookmark(post.id) },
                                                onDeletePost = { pendingDeletePost = post },
                                                onTagClick = { tag ->
                                                    Toast.makeText(context, "#$tag", Toast.LENGTH_SHORT).show()
                                                },
                                                onMediaClick = {
                                                    lightboxMedia = Triple(post.mediaType, post.mediaPreviewData, post.mediaId)
                                                },
                                                outerPadding = PaddingValues(0.dp),
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        if (rowPosts.size == 1) {
                                            Spacer(Modifier.weight(1f))
                                        }
                                    }
                                }
                            } else {
                                items(
                                    items = pState.data,
                                    key = { it.id },
                                    contentType = { "profile_post_card" }
                                ) { post ->
                                    val liveAuthor = authorProfiles[post.authorId]
                                    val authorStatus = liveAuthor?.quickStatus
                                        ?: if (post.authorId == publicUser.userId) publicUser.quickStatus else null
                                    val authorAvatarOverride = liveAuthor?.avatarUrl?.takeIf { it.isNotBlank() }
                                        ?: if (post.authorId == publicUser.userId) publicUser.avatarUrl?.takeIf { it.isNotBlank() } else null
                                    VybPostCard(
                                        post = post,
                                        currentUserId = publicUser.userId,
                                        authorAvatarUrlOverride = authorAvatarOverride,
                                        authorStatus = authorStatus,
                                        isBookmarked = post.id in bookmarks,
                                        onLike = { feedViewModel.toggleLike(post) },
                                        onComment = { openCommentsForPost(post) },
                                        onShare = { sharePostAction(post) },
                                        onBookmark = { feedViewModel.toggleBookmark(post.id) },
                                        onDeletePost = { pendingDeletePost = post },
                                        onTagClick = { tag ->
                                            Toast.makeText(context, "#$tag", Toast.LENGTH_SHORT).show()
                                        },
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
            }
        }
    }

    if (showEditModal) {
        EditProfileDialog(
            publicUser = publicUser,
            privateUser = privateUser,
            initialSinpeBank = effectiveSinpeBank,
            initialSinpeVisibility = effectiveSinpeVisibility.key,
            isSaving = isSaving,
            onSave = { name, username, bank, phone, loc, bio, visibility ->
                profileViewModel.saveProfile(
                    displayName = name,
                    username = username,
                    sinpeBank = bank,
                    phone = phone,
                    email = privateUser?.email,
                    quickStatus = publicUser.quickStatus,
                    locationLabel = loc,
                    bio = bio,
                    avatarUrl = publicUser.avatarUrl,
                    isVerified = isAccountVerified,
                    sinpeVisibility = visibility,
                    onSuccess = { showEditModal = false }
                )
            },
            onDismiss = { showEditModal = false }
        )
    }

    if (showPasswordModal) {
        UpdatePasswordDialog(
            username = publicUser.username,
            isSaving = isSaving,
            onConfirm = { newPass ->
                profileViewModel.updateAccountPassword(
                    newPassword = newPass,
                    currentPublic = publicUser,
                    currentPrivate = privateUser,
                    onSuccess = { showPasswordModal = false }
                )
            },
            onDismiss = { showPasswordModal = false }
        )
    }

    if (showSinpeQrModal) {
        SinpeQrDialog(
            displayName = publicUser.displayName,
            formattedPhone = formattedSinpePhone,
            sinpeBank = effectiveSinpeBank,
            onCopyPhone = { phoneToCopy ->
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Teléfono SINPE", phoneToCopy))
                Toast.makeText(context, "Teléfono SINPE copiado", Toast.LENGTH_SHORT).show()
            },
            onConfigureSinpe = {
                showSinpeQrModal = false
                showEditModal = true
            },
            onDismiss = { showSinpeQrModal = false }
        )
    }

    activeCommentsPost?.let { post ->
        PostCommentsModalSheet(
            post = post,
            comments = comments,
            currentUserId = publicUser.userId,
            onDismiss = {
                activeCommentsPost = null
                feedViewModel.openComments(null)
            },
            onSendComment = { text, onSent ->
                feedViewModel.addComment(post.id, publicUser, text, onSent)
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
            text = { Text("¿Deseas eliminar esta publicación por completo? Sus comentarios también se borrarán.") },
            confirmButton = {
                Button(
                    onClick = {
                        pendingDeletePost = null
                        if (activeCommentsPost?.id == postToDelete.id) {
                            activeCommentsPost = null
                            feedViewModel.openComments(null)
                        }
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

    pendingDeleteComment?.let { (postId, commentToDelete) ->
        AlertDialog(
            onDismissRequest = { pendingDeleteComment = null },
            title = { Text("Eliminar comentario") },
            text = { Text("¿Deseas eliminar este comentario?") },
            confirmButton = {
                Button(
                    onClick = {
                        pendingDeleteComment = null
                        feedViewModel.deleteComment(postId, commentToDelete)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditProfileDialog(
    publicUser: PublicUser,
    privateUser: PrivateUser?,
    initialSinpeBank: String,
    initialSinpeVisibility: String,
    isSaving: Boolean,
    onSave: (name: String, username: String, bank: String, phone: String?, loc: String?, bio: String?, visibility: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(publicUser.displayName) }
    var username by remember { mutableStateOf(publicUser.username) }
    var bank by remember { mutableStateOf(initialSinpeBank) }
    var sinpeVisibility by remember { mutableStateOf(initialSinpeVisibility) }
    var phone by remember {
        mutableStateOf(com.example.data.repository.UserRepository.normalizeCrPhone(privateUser?.phone) ?: "")
    }
    var loc by remember { mutableStateOf(publicUser.locationLabel ?: "") }
    var bio by remember { mutableStateOf(publicUser.bio ?: "") }
    var bankMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar perfil") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    label = { Text("Nombre visible") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { raw ->
                        username = raw.lowercase().replace(Regex("[^a-z0-9_]"), "").take(30)
                    },
                    label = { Text("@usuario") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                val phoneValid = phone.isEmpty() || phone.length == 8
                val phoneMask = remember { CostaRicaPhoneVisualTransformation() }
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it.filter { c -> c.isDigit() }.take(8) },
                    label = { Text("Teléfono SINPE (+506)") },
                    prefix = { Text("+506 ", style = VybMonoTabularStyle) },
                    placeholder = { Text("8888-8888", maxLines = 1, softWrap = false) },
                    visualTransformation = phoneMask,
                    isError = !phoneValid,
                    supportingText = {
                        Text(
                            text = if (!phoneValid) {
                                "Debe tener exactamente 8 dígitos (formato XXXX-XXXX)"
                            } else {
                                "Formato Costa Rica: XXXX-XXXX (8 dígitos)"
                            },
                            color = if (!phoneValid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    textStyle = VybMonoTabularStyle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_profile_sinpe_phone_input")
                )
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = bank,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Banco SINPE Móvil") },
                        leadingIcon = { Icon(Icons.Filled.AccountBalance, contentDescription = null) },
                        trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { bankMenuExpanded = true }
                    )
                    DropdownMenu(expanded = bankMenuExpanded, onDismissRequest = { bankMenuExpanded = false }) {
                        VybConstants.SINPE_BANKS.forEach { b ->
                            DropdownMenuItem(
                                text = { Text(b) },
                                onClick = {
                                    bank = b
                                    bankMenuExpanded = false
                                }
                            )
                        }
                    }
                }
                Text(
                    text = "Visibilidad de datos SINPE",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SinpeVisibility.entries.forEach { vis ->
                        FilterChip(
                            selected = sinpeVisibility == vis.key,
                            onClick = { sinpeVisibility = vis.key },
                            label = { Text(vis.shortBadge) }
                        )
                    }
                }
                OutlinedTextField(
                    value = loc,
                    onValueChange = { loc = it },
                    label = { Text("Ciudad o zona") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it.take(200) },
                    label = { Text("Biografía") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            val isPhoneValid = phone.isEmpty() || phone.length == 8
            Button(
                onClick = { onSave(name, username, bank, phone, loc, bio, sinpeVisibility) },
                enabled = !isSaving && name.isNotBlank() && username.isNotBlank() && isPhoneValid,
                modifier = Modifier.testTag("edit_profile_save_button")
            ) {
                Text(if (isSaving) "Guardando..." else "Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun UpdatePasswordDialog(
    username: String,
    isSaving: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val vybColors = LocalVybColors.current
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showNew by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }

    val longEnough = newPassword.length >= 8
    val matches = confirmPassword.isNotEmpty() && newPassword == confirmPassword
    val valid = longEnough && matches

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Contraseña de acceso") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Úsala junto con @$username, tu teléfono (+506) o tu correo para iniciar sesión en otros dispositivos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = vybColors.secondaryText
                )
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it.take(64) },
                    label = { Text("Nueva contraseña") },
                    leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { showNew = !showNew }) {
                            Icon(
                                imageVector = if (showNew) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (showNew) "Ocultar" else "Mostrar"
                            )
                        }
                    },
                    visualTransformation = if (showNew) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    supportingText = {
                        Text(
                            text = if (longEnough) "Longitud segura lista" else "Mínimo 8 caracteres (${newPassword.length}/8)",
                            color = if (longEnough) vybColors.sinpeEmerald else vybColors.secondaryText
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_new_password_input")
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it.take(64) },
                    label = { Text("Confirmar contraseña") },
                    leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { showConfirm = !showConfirm }) {
                            Icon(
                                imageVector = if (showConfirm) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (showConfirm) "Ocultar" else "Mostrar"
                            )
                        }
                    },
                    visualTransformation = if (showConfirm) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = confirmPassword.isNotEmpty() && !matches,
                    supportingText = {
                        val txt = when {
                            confirmPassword.isEmpty() -> "Repite tu nueva contraseña"
                            matches -> "Las contraseñas coinciden"
                            else -> "Las contraseñas no coinciden"
                        }
                        val col = when {
                            matches -> vybColors.sinpeEmerald
                            confirmPassword.isNotEmpty() -> MaterialTheme.colorScheme.error
                            else -> vybColors.secondaryText
                        }
                        Text(txt, color = col)
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_confirm_password_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(newPassword) },
                enabled = valid && !isSaving,
                modifier = Modifier.testTag("profile_save_password_button")
            ) {
                Text(if (isSaving) "Guardando..." else "Guardar clave")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun SinpeQrDialog(
    displayName: String,
    formattedPhone: String?,
    sinpeBank: String,
    onCopyPhone: (String) -> Unit,
    onConfigureSinpe: () -> Unit,
    onDismiss: () -> Unit
) {
    val vybColors = LocalVybColors.current
    val qrPayload = remember(displayName, formattedPhone, sinpeBank) {
        formattedPhone?.let { phone ->
            "SINPE Móvil\nTeléfono: $phone\nNombre: $displayName\nBanco: $sinpeBank"
        }
    }
    val qrMatrix = remember(qrPayload) {
        qrPayload?.let { payload -> runCatching { QrCodeEncoder.encode(payload) }.getOrNull() }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(0.9.dp, vybColors.hairlineBorder),
            tonalElevation = 6.dp,
            shadowElevation = 10.dp,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 380.dp)
                .testTag("profile_sinpe_qr_modal")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        VybLogoBadge(size = 34.dp)
                        Column {
                            Text(
                                text = "Mi QR SINPE",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Cobro rápido en persona",
                                style = MaterialTheme.typography.labelSmall,
                                color = vybColors.secondaryText
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("profile_sinpe_qr_close")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Cerrar QR SINPE"
                        )
                    }
                }

                if (qrMatrix != null && !formattedPhone.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White,
                        border = BorderStroke(1.5.dp, vybColors.sinpeEmerald.copy(alpha = 0.5f)),
                        shadowElevation = 2.dp
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(16.dp)
                                .size(216.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("profile_sinpe_qr_canvas")
                            ) {
                                val quietModules = 4
                                val totalModules = qrMatrix.size + quietModules * 2
                                val moduleSize = size.minDimension / totalModules.toFloat()
                                val darkColor = Color(0xFF0F172A)

                                for (y in 0 until qrMatrix.size) {
                                    for (x in 0 until qrMatrix.size) {
                                        if (qrMatrix.get(x, y)) {
                                            drawRect(
                                                color = darkColor,
                                                topLeft = Offset(
                                                    x = (x + quietModules) * moduleSize,
                                                    y = (y + quietModules) * moduleSize
                                                ),
                                                size = Size(moduleSize + 0.5f, moduleSize + 0.5f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = vybColors.sinpeContainer,
                        border = BorderStroke(0.75.dp, vybColors.sinpeEmerald.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = vybColors.sinpeOnContainer,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = formattedPhone,
                                style = MaterialTheme.typography.titleMedium.merge(VybMonoTabularStyle),
                                fontWeight = FontWeight.Bold,
                                color = vybColors.sinpeEmerald
                            )
                            Text(
                                text = sinpeBank,
                                style = MaterialTheme.typography.labelMedium,
                                color = vybColors.sinpeOnContainer.copy(alpha = 0.85f)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onCopyPhone(formattedPhone) },
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("profile_sinpe_qr_copy")
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Copiar número",
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Button(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("profile_sinpe_qr_done")
                        ) {
                            Text(
                                text = "Cerrar",
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = vybColors.nestedSurface,
                        border = BorderStroke(0.75.dp, vybColors.hairlineBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.QrCode2,
                                contentDescription = null,
                                tint = vybColors.secondaryText,
                                modifier = Modifier.size(44.dp)
                            )
                            Text(
                                text = "Aún no tienes un número SINPE configurado",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Agrega tu número +506 en tu perfil para generar tu código QR de cobro rápido.",
                                style = MaterialTheme.typography.bodySmall,
                                color = vybColors.secondaryText,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    VybPrimaryGradientButton(
                        text = "Configurar en Editar perfil",
                        onClick = onConfigureSinpe,
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "profile_sinpe_qr_configure"
                    )
                }
            }
        }
    }
}
