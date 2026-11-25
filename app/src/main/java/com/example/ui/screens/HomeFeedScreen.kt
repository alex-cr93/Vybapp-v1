package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import android.widget.Toast
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.ui.graphics.asImageBitmap
import com.example.core.ResolvedMediaInfo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.core.BitmapMemoryCache
import com.example.core.MediaUtils
import com.example.data.model.Post
import com.example.data.model.PublicUser
import com.example.data.model.Story
import com.example.data.model.UiState
import com.example.ui.components.EmptyStateView
import com.example.ui.components.UserStatusBadge
import com.example.ui.components.VybAvatar
import com.example.ui.components.VybLogoBadge
import com.example.ui.components.VybPrimaryGradientButton
import com.example.ui.components.ZoomableMediaLightbox
import com.example.ui.components.formatRelativeTime
import com.example.ui.components.PostCommentsModalSheet
import com.example.ui.components.VybPostCard
import com.example.ui.components.sharePostExternally
import com.example.ui.theme.LocalVybColors
import com.example.ui.theme.VybElectricBlue
import com.example.ui.theme.VybGradientBrush
import com.example.ui.theme.VybLikeGradientBrush
import com.example.ui.theme.VybMonoTabularStyle
import com.example.ui.theme.VybPink
import com.example.ui.theme.VybSkyBlue
import com.example.ui.theme.VybViolet
import com.example.ui.viewmodel.FeedViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeFeedScreen(
    feedViewModel: FeedViewModel,
    currentUser: PublicUser,
    privateAreaLabel: String?,
    widthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
    onReplyToStoryAuthor: (authorId: String, authorName: String, authorAvatar: String?, replyText: String) -> Unit
) {
    val context = LocalContext.current
    val vybColors = LocalVybColors.current
    val isExpanded = widthSizeClass == WindowWidthSizeClass.Expanded

    val storiesState by feedViewModel.storiesState.collectAsStateWithLifecycle()
    val postsState by feedViewModel.filteredPostsState.collectAsStateWithLifecycle()
    val searchQuery by feedViewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedHashtag by feedViewModel.selectedHashtag.collectAsStateWithLifecycle()
    val bookmarks by feedViewModel.bookmarkedPostIds.collectAsStateWithLifecycle()
    val uploadProgress by feedViewModel.uploadProgress.collectAsStateWithLifecycle()
    val uploadStageLabel by feedViewModel.uploadStageLabel.collectAsStateWithLifecycle()
    val publishError by feedViewModel.publishError.collectAsStateWithLifecycle()
    val comments by feedViewModel.commentsState.collectAsStateWithLifecycle()
    val authorProfiles by feedViewModel.authorProfiles.collectAsStateWithLifecycle()
    val hasAddedContacts by feedViewModel.hasAddedContacts.collectAsStateWithLifecycle()

    var showSearchBar by remember { mutableStateOf(false) }
    var quickPostText by remember { mutableStateOf("") }
    var showCreatePostModal by remember { mutableStateOf(false) }
    var showCreateStoryModal by remember { mutableStateOf(false) }
    var activeStoryIndex by remember { mutableStateOf<Int?>(null) }
    var activeCommentsPost by remember { mutableStateOf<Post?>(null) }
    var pendingDeletePost by remember { mutableStateOf<Post?>(null) }
    var pendingDeleteComment by remember { mutableStateOf<Pair<String, com.example.data.model.PostComment>?>(null) }
    var lightboxMedia by remember { mutableStateOf<Triple<String, String?, String?>?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Sticky compact header
        Surface(
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .widthIn(max = if (isExpanded) 1040.dp else 720.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        VybLogoBadge(size = 38.dp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "VYB",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Conecta · Comparte · Vibra",
                                style = MaterialTheme.typography.bodySmall,
                                color = vybColors.secondaryText
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = { showSearchBar = !showSearchBar },
                            modifier = Modifier.size(48.dp).testTag("feed_search_toggle")
                        ) {
                            Icon(
                                imageVector = if (showSearchBar) Icons.Filled.Close else Icons.Filled.Search,
                                contentDescription = "Buscar publicaciones"
                            )
                        }
                        IconButton(
                            onClick = { showCreatePostModal = true },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(VybGradientBrush)
                                .testTag("feed_create_post_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "Crear publicación",
                                tint = Color.White
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = showSearchBar || !selectedHashtag.isNullOrBlank()) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = feedViewModel::setSearchQuery,
                            placeholder = { Text("Filtrar por texto, autor o #etiqueta") },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            trailingIcon = {
                                if (searchQuery.isNotBlank()) {
                                    IconButton(
                                        onClick = { feedViewModel.setSearchQuery("") },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(Icons.Filled.Close, contentDescription = "Limpiar")
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().testTag("feed_search_input")
                        )
                        selectedHashtag?.let { tag ->
                            Spacer(Modifier.height(6.dp))
                            AssistChip(
                                onClick = { feedViewModel.selectHashtag(null) },
                                label = { Text("#$tag  ✕") }
                            )
                        }
                    }
                }
                HorizontalDivider(thickness = 0.75.dp, color = vybColors.hairlineBorder)
            }
            }
        }

        if (uploadProgress != null) {
            LinearProgressIndicator(
                progress = { uploadProgress ?: 0f },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            val activeStories = (storiesState as? UiState.Success)?.data ?: emptyList()

            LazyColumn(
                modifier = Modifier
                    .widthIn(max = if (isExpanded) 1040.dp else 640.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(bottom = 104.dp)
            ) {
                // Horizontal Stories Bar
                item(key = "stories_bar", contentType = "stories_bar") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item(key = "add_story_tile", contentType = "add_story") {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width(72.dp)
                                    .clickable { showCreateStoryModal = true }
                                    .testTag("add_story_button")
                            ) {
                                Box(modifier = Modifier.size(64.dp)) {
                                    VybAvatar(
                                        avatarUrl = currentUser.avatarUrl,
                                        displayName = currentUser.displayName,
                                        size = 64.dp,
                                        showGradientRing = true
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .align(Alignment.BottomEnd)
                                            .clip(CircleShape)
                                            .background(VybGradientBrush)
                                            .border(2.dp, MaterialTheme.colorScheme.background, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Filled.Add,
                                            contentDescription = "Subir historia",
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "Tu historia",
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        itemsIndexed(
                            items = activeStories,
                            key = { _, s -> s.id },
                            contentType = { _, _ -> "story_item" }
                        ) { idx, story ->
                            val isUnseen = currentUser.userId !in story.viewerUids
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width(72.dp)
                                    .clickable {
                                        feedViewModel.markStoryViewed(story)
                                        activeStoryIndex = idx
                                    }
                            ) {
                                VybAvatar(
                                    avatarUrl = story.authorAvatarUrl,
                                    displayName = story.authorName,
                                    size = 64.dp,
                                    showGradientRing = isUnseen
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = story.authorName.substringBefore(" "),
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    HorizontalDivider(thickness = 0.75.dp, color = vybColors.hairlineBorder)
                }

                // Quick Wall Composer
                item(key = "quick_composer", contentType = "quick_composer") {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = vybColors.cardElevationDp),
                            border = BorderStroke(0.75.dp, vybColors.hairlineBorder),
                            modifier = Modifier
                                .widthIn(max = 720.dp)
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                VybAvatar(
                                    avatarUrl = currentUser.avatarUrl,
                                    displayName = currentUser.displayName,
                                    size = 40.dp
                                )
                                Spacer(Modifier.width(10.dp))
                                OutlinedTextField(
                                    value = quickPostText,
                                    onValueChange = { quickPostText = it },
                                    placeholder = { Text("¿Qué estás creando o compartiendo hoy?") },
                                    maxLines = 3,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("quick_post_input")
                                )
                                Spacer(Modifier.width(8.dp))
                                val canSendQuick = quickPostText.isNotBlank() && uploadProgress == null
                                IconButton(
                                    onClick = {
                                        feedViewModel.publishPost(
                                            context = context,
                                            author = currentUser,
                                            text = quickPostText,
                                            tagsRaw = "",
                                            mediaUri = null,
                                            reportToSnackbar = true,
                                            onSuccess = { quickPostText = "" }
                                        )
                                    },
                                    enabled = canSendQuick,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .graphicsLayer { alpha = if (canSendQuick) 1f else 0.45f }
                                        .clip(CircleShape)
                                        .background(
                                            if (canSendQuick) VybGradientBrush
                                            else androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.surfaceVariant)
                                        )
                                        .testTag("quick_post_send")
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Publicar",
                                        tint = if (canSendQuick) Color.White else vybColors.secondaryText
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = { showCreatePostModal = true },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Icon(Icons.Filled.PermMedia, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Foto, video y etiquetas")
                                }
                            }
                        }
                        }
                    }
                }

                // Feed Posts
                when (val state = postsState) {
                    is UiState.Loading -> {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                    is UiState.Error -> {
                        item {
                            EmptyStateView(
                                title = "No se pudo cargar el muro",
                                subtitle = state.message,
                                actionLabel = "Reintentar",
                                onAction = { feedViewModel.refreshFeed() }
                            )
                        }
                    }
                    is UiState.Success -> {
                        if (state.data.isEmpty()) {
                            val hasFilter = searchQuery.isNotBlank() || !selectedHashtag.isNullOrBlank()
                            item {
                                EmptyStateView(
                                    title = when {
                                        hasFilter -> "Sin resultados en tu red"
                                        !hasAddedContacts -> "Solo visible para tus contactos"
                                        else -> "Tu muro está listo para vibrar"
                                    },
                                    subtitle = when {
                                        hasFilter -> "Ninguna publicación tuya o de tus contactos agregados coincide con la búsqueda."
                                        !hasAddedContacts -> "Aún no tienes contactos agregados ni publicaciones propias. Agrega contactos en VYB para ver sus publicaciones e historias, o comparte la primera."
                                        else -> "Aún no hay publicaciones tuyas ni de tus contactos agregados. Comparte la primera idea, foto o video."
                                    },
                                    actionLabel = if (hasFilter) null else "Crear publicación",
                                    onAction = if (hasFilter) null else {
                                        { showCreatePostModal = true }
                                    }
                                )
                            }
                        } else {
                            val sharePostAction: (Post) -> Unit = { targetPost ->
                                sharePostExternally(context, targetPost) {
                                    feedViewModel.sharePost(targetPost.id)
                                }
                            }

                            if (isExpanded) {
                                items(
                                    items = state.data.chunked(2),
                                    key = { row -> row.first().id },
                                    contentType = { "post_grid_row" }
                                ) { rowPosts ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 6.dp)
                                            .animateItem(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        rowPosts.forEach { post ->
                                            val liveAuthor = authorProfiles[post.authorId]
                                            val authorStatus = liveAuthor?.quickStatus
                                                ?: if (post.authorId == currentUser.userId) currentUser.quickStatus else null
                                            val authorAvatarOverride = liveAuthor?.avatarUrl?.takeIf { it.isNotBlank() }
                                            VybPostCard(
                                                post = post,
                                                currentUserId = currentUser.userId,
                                                authorAvatarUrlOverride = authorAvatarOverride,
                                                authorStatus = authorStatus,
                                                isBookmarked = post.id in bookmarks,
                                                onLike = { feedViewModel.toggleLike(post) },
                                                onComment = {
                                                    activeCommentsPost = post
                                                    feedViewModel.openComments(post.id)
                                                },
                                                onShare = { sharePostAction(post) },
                                                onBookmark = { feedViewModel.toggleBookmark(post.id) },
                                                onDeletePost = { pendingDeletePost = post },
                                                onTagClick = { tag -> feedViewModel.selectHashtag(tag) },
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
                                    items = state.data,
                                    key = { it.id },
                                    contentType = { "post_card" }
                                ) { post ->
                                    val liveAuthor = authorProfiles[post.authorId]
                                    val authorStatus = liveAuthor?.quickStatus
                                        ?: if (post.authorId == currentUser.userId) currentUser.quickStatus else null
                                    val authorAvatarOverride = liveAuthor?.avatarUrl?.takeIf { it.isNotBlank() }
                                    VybPostCard(
                                        post = post,
                                        currentUserId = currentUser.userId,
                                        authorAvatarUrlOverride = authorAvatarOverride,
                                        authorStatus = authorStatus,
                                        isBookmarked = post.id in bookmarks,
                                        onLike = { feedViewModel.toggleLike(post) },
                                        onComment = {
                                            activeCommentsPost = post
                                            feedViewModel.openComments(post.id)
                                        },
                                        onShare = { sharePostAction(post) },
                                        onBookmark = { feedViewModel.toggleBookmark(post.id) },
                                        onDeletePost = { pendingDeletePost = post },
                                        onTagClick = { tag -> feedViewModel.selectHashtag(tag) },
                                        onMediaClick = {
                                            lightboxMedia = Triple(post.mediaType, post.mediaPreviewData, post.mediaId)
                                        },
                                        modifier = Modifier.animateItem()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreatePostModal) {
        CreatePostModal(
            uploadProgress = uploadProgress,
            uploadStageLabel = uploadStageLabel,
            publishError = publishError,
            onClearError = feedViewModel::clearPublishError,
            onDismiss = {
                feedViewModel.clearPublishError()
                showCreatePostModal = false
            },
            onPublish = { text, tags, uri ->
                feedViewModel.publishPost(context, currentUser, text, tags, uri) {
                    showCreatePostModal = false
                }
            }
        )
    }

    if (showCreateStoryModal) {
        CreateStoryModal(
            defaultLocation = privateAreaLabel ?: currentUser.locationLabel.orEmpty(),
            uploadProgress = uploadProgress,
            uploadStageLabel = uploadStageLabel,
            publishError = publishError,
            onClearError = feedViewModel::clearPublishError,
            onDismiss = {
                feedViewModel.clearPublishError()
                showCreateStoryModal = false
            },
            onPublish = { uri, caption, loc ->
                feedViewModel.publishStory(context, currentUser, uri, caption, loc) {
                    showCreateStoryModal = false
                }
            }
        )
    }

    val storyList = (storiesState as? UiState.Success)?.data ?: emptyList()
    activeStoryIndex?.let { index ->
        if (index in storyList.indices) {
            StoryViewerDialog(
                stories = storyList,
                startIndex = index,
                currentUserId = currentUser.userId,
                onViewed = { feedViewModel.markStoryViewed(it) },
                loadFullMediaFile = { id -> feedViewModel.loadFullMediaFile(context, id) },
                onDeleteStory = { story ->
                    feedViewModel.deleteStory(story) {
                        if (storyList.size <= 1) {
                            activeStoryIndex = null
                        } else {
                            activeStoryIndex = index.coerceAtMost(storyList.size - 2)
                        }
                    }
                },
                onReply = { story, text ->
                    activeStoryIndex = null
                    onReplyToStoryAuthor(story.authorId, story.authorName, story.authorAvatarUrl, text)
                },
                onDismiss = { activeStoryIndex = null }
            )
        }
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
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_post_button")
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
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_comment_button")
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


@Composable
private fun SelectedMediaPreviewCard(
    uri: Uri,
    isBusy: Boolean,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    val vybColors = LocalVybColors.current
    val mediaInfo by produceState<ResolvedMediaInfo?>(initialValue = null, key1 = uri) {
        value = MediaUtils.inspectMediaUri(context, uri)
    }
    val previewBmp by produceState<android.graphics.Bitmap?>(initialValue = null, key1 = uri) {
        value = MediaUtils.loadComposerPreviewBitmap(context, uri)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = vybColors.nestedSurface),
        border = BorderStroke(0.75.dp, vybColors.hairlineBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                val bmp = previewBmp
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Vista previa seleccionada",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = if (mediaInfo?.isVideo == true) Icons.Filled.Videocam else Icons.Filled.PermMedia,
                        contentDescription = null,
                        tint = vybColors.linkAccent
                    )
                }
                if (mediaInfo?.isVideo == true) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayCircleFilled,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mediaInfo?.displayName ?: "Archivo listo",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val info = mediaInfo
                val subtitle = buildString {
                    append(if (info?.isVideo == true) "Video (se optimizará a MP4 HD)" else "Imagen")
                    if (info != null && info.sizeBytes > 0L) {
                        append(" · ")
                        append(MediaUtils.formatByteSize(info.sizeBytes))
                    }
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = vybColors.secondaryText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!isBusy) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Quitar archivo",
                        tint = vybColors.secondaryText
                    )
                }
            }
        }
    }
}

@Composable
private fun InlineModalErrorBanner(
    error: String?,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(visible = !error.isNullOrBlank()) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = error.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Cerrar error",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CreatePostModal(
    uploadProgress: Float?,
    uploadStageLabel: String?,
    publishError: String?,
    onClearError: () -> Unit,
    onDismiss: () -> Unit,
    onPublish: (text: String, tagsRaw: String, mediaUri: Uri?) -> Unit
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    var showCameraChoice by remember { mutableStateOf(false) }
    var pendingCaptureVideo by remember { mutableStateOf(false) }

    val mediaPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onClearError()
            selectedUri = uri
        }
    }

    val documentPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onClearError()
            selectedUri = uri
        }
    }

    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok ->
        if (ok) {
            onClearError()
            selectedUri = tempCameraUri
        }
    }

    val captureVideo = rememberLauncherForActivityResult(
        ActivityResultContracts.CaptureVideo()
    ) { ok ->
        if (ok) {
            onClearError()
            selectedUri = tempCameraUri
        }
    }

    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            if (pendingCaptureVideo) {
                val uri = MediaUtils.createTempCameraVideoUri(context)
                tempCameraUri = uri
                captureVideo.launch(uri)
            } else {
                val uri = MediaUtils.createTempCameraUri(context)
                tempCameraUri = uri
                takePicture.launch(uri)
            }
        } else {
            Toast.makeText(context, "Activa el permiso de cámara para capturar.", Toast.LENGTH_SHORT).show()
        }
    }

    Dialog(onDismissRequest = { if (uploadProgress == null) onDismiss() }) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Text("Nueva publicación", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        onClearError()
                        text = it.take(2000)
                    },
                    label = { Text("¿Qué quieres compartir?") },
                    minLines = 3,
                    maxLines = 6,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("Etiquetas (separadas por comas)") },
                    placeholder = { Text("diseño, café, tecnología") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = {
                            mediaPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            )
                        },
                        enabled = uploadProgress == null,
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("post_picker_gallery_button")
                    ) {
                        Icon(Icons.Filled.PermMedia, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Galería", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = { documentPicker.launch(arrayOf("image/*", "video/*")) },
                        enabled = uploadProgress == null,
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("post_picker_files_button")
                    ) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Archivos", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = { showCameraChoice = true },
                        enabled = uploadProgress == null,
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("post_picker_camera_button")
                    ) {
                        Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Cámara", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                if (selectedUri != null) {
                    Spacer(Modifier.height(12.dp))
                    SelectedMediaPreviewCard(
                        uri = selectedUri!!,
                        isBusy = uploadProgress != null,
                        onRemove = {
                            onClearError()
                            selectedUri = null
                        }
                    )
                }

                InlineModalErrorBanner(error = publishError, onDismiss = onClearError)

                if (uploadProgress != null) {
                    Spacer(Modifier.height(12.dp))
                    if (!uploadStageLabel.isNullOrBlank()) {
                        Text(
                            text = uploadStageLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    LinearProgressIndicator(progress = { uploadProgress }, modifier = Modifier.fillMaxWidth())
                }

                Spacer(Modifier.height(16.dp))
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = uploadProgress == null,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) { Text("Cancelar") }
                    Spacer(Modifier.width(8.dp))
                    VybPrimaryGradientButton(
                        text = "Publicar",
                        enabled = (text.isNotBlank() || selectedUri != null) && uploadProgress == null,
                        isLoading = uploadProgress != null,
                        onClick = { onPublish(text, tags, selectedUri) },
                        modifier = Modifier
                            .width(136.dp)
                            .testTag("post_publish_button")
                    )
                }
            }
        }
    }

    if (showCameraChoice) {
        AlertDialog(
            onDismissRequest = { showCameraChoice = false },
            title = { Text("Capturar con cámara") },
            text = { Text("Elige si deseas tomar una fotografía o grabar un video.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCameraChoice = false
                        pendingCaptureVideo = false
                        cameraPermission.launch(android.Manifest.permission.CAMERA)
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Tomar foto")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCameraChoice = false
                        pendingCaptureVideo = true
                        cameraPermission.launch(android.Manifest.permission.CAMERA)
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Filled.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Grabar video")
                }
            }
        )
    }
}

@Composable
private fun CreateStoryModal(
    defaultLocation: String,
    uploadProgress: Float?,
    uploadStageLabel: String?,
    publishError: String?,
    onClearError: () -> Unit,
    onDismiss: () -> Unit,
    onPublish: (uri: Uri, caption: String, location: String) -> Unit
) {
    val context = LocalContext.current
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var caption by remember { mutableStateOf("") }
    var location by remember { mutableStateOf(defaultLocation) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    val galleryPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onClearError()
            selectedUri = uri
        }
    }

    val documentPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onClearError()
            selectedUri = uri
        }
    }

    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok ->
        if (ok) {
            onClearError()
            selectedUri = tempCameraUri
        }
    }

    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = MediaUtils.createTempCameraUri(context)
            tempCameraUri = uri
            takePicture.launch(uri)
        } else {
            Toast.makeText(context, "Activa el permiso de cámara para capturar.", Toast.LENGTH_SHORT).show()
        }
    }

    Dialog(onDismissRequest = { if (uploadProgress == null) onDismiss() }) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Text("Crear historia (24 h)", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            galleryPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            )
                        },
                        enabled = uploadProgress == null,
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Filled.PermMedia, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Galería", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = { documentPicker.launch(arrayOf("image/*", "video/*")) },
                        enabled = uploadProgress == null,
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Archivos", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = { cameraPermission.launch(android.Manifest.permission.CAMERA) },
                        enabled = uploadProgress == null,
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Cámara", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (selectedUri != null) {
                    Spacer(Modifier.height(12.dp))
                    SelectedMediaPreviewCard(
                        uri = selectedUri!!,
                        isBusy = uploadProgress != null,
                        onRemove = {
                            onClearError()
                            selectedUri = null
                        }
                    )
                }
                InlineModalErrorBanner(error = publishError, onDismiss = onClearError)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it.take(300) },
                    label = { Text("Texto o descripción") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it.take(100) },
                    label = { Text("Ubicación (opcional)") },
                    leadingIcon = { Icon(Icons.Filled.LocationOn, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                if (uploadProgress != null) {
                    Spacer(Modifier.height(12.dp))
                    if (!uploadStageLabel.isNullOrBlank()) {
                        Text(
                            text = uploadStageLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    LinearProgressIndicator(progress = { uploadProgress }, modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = uploadProgress == null,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) { Text("Cancelar") }
                    Spacer(Modifier.width(8.dp))
                    VybPrimaryGradientButton(
                        text = "Subir historia",
                        enabled = selectedUri != null && uploadProgress == null,
                        isLoading = uploadProgress != null,
                        onClick = { selectedUri?.let { onPublish(it, caption, location) } },
                        modifier = Modifier.width(150.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StoryViewerDialog(
    stories: List<Story>,
    startIndex: Int,
    currentUserId: String,
    onViewed: (Story) -> Unit,
    loadFullMediaFile: suspend (String) -> File?,
    onDeleteStory: (Story) -> Unit,
    onReply: (Story, String) -> Unit,
    onDismiss: () -> Unit
) {
    if (stories.isEmpty()) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    var currentIndex by remember { mutableIntStateOf(startIndex.coerceIn(0, stories.lastIndex)) }
    LaunchedEffect(stories.size) {
        if (stories.isEmpty()) {
            onDismiss()
        } else if (currentIndex > stories.lastIndex) {
            currentIndex = stories.lastIndex
        }
    }
    var isPaused by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var replyText by remember { mutableStateOf("") }
    val story = stories.getOrNull(currentIndex) ?: stories.last()
    var videoFile by remember { mutableStateOf<File?>(null) }
    var isVideoReady by remember(story.id) { mutableStateOf(story.mediaType != "video") }
    var storyStepIncrement by remember(story.id) { mutableStateOf(0.01f) }
    val context = LocalContext.current

    LaunchedEffect(currentIndex, story.id) {
        progress = 0f
        videoFile = null
        isVideoReady = story.mediaType != "video"
        storyStepIncrement = 0.01f
        onViewed(story)
        if (story.mediaType == "video" && !story.mediaId.isNullOrBlank()) {
            val loaded = loadFullMediaFile(story.mediaId)
            videoFile = loaded
            if (loaded == null) {
                isVideoReady = true
            }
        } else if (story.mediaType == "video") {
            isVideoReady = true
        }
        while (progress < 1f) {
            delay(50L)
            if (isVideoReady && !isPaused && !showDeleteConfirm && replyText.isEmpty()) {
                progress += storyStepIncrement
            }
        }
        if (currentIndex < stories.lastIndex) {
            currentIndex++
        } else {
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(currentIndex) {
                    detectTapGestures(
                        onLongPress = { isPaused = true },
                        onPress = {
                            tryAwaitRelease()
                            isPaused = false
                        },
                        onTap = { offset ->
                            if (offset.x < size.width * 0.3f && currentIndex > 0) {
                                currentIndex--
                            } else if (offset.x > size.width * 0.7f) {
                                if (currentIndex < stories.lastIndex) currentIndex++ else onDismiss()
                            }
                        }
                    )
                }
        ) {
            if (story.mediaType == "video") {
                val f = videoFile
                if (f != null) {
                    val exo = remember(f) {
                        ExoPlayer.Builder(context).build().apply {
                            setMediaItem(MediaItem.fromUri(f.toURI().toString()))
                            prepare()
                            playWhenReady = true
                        }
                    }
                    DisposableEffect(exo) {
                        val listener = object : Player.Listener {
                            override fun onPlaybackStateChanged(playbackState: Int) {
                                if (playbackState == Player.STATE_READY) {
                                    val durMs = exo.duration.takeIf { it > 0L }?.coerceIn(5000L, 15000L) ?: 5000L
                                    storyStepIncrement = (50f / durMs.toFloat()).coerceIn(0.0033f, 0.01f)
                                    isVideoReady = true
                                }
                            }
                        }
                        exo.addListener(listener)
                        onDispose {
                            exo.removeListener(listener)
                            exo.release()
                        }
                    }
                    AndroidView(
                        factory = { ctx -> PlayerView(ctx).apply { player = exo; useController = false } },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            } else {
                val cachedStoryBmp = remember(story.mediaPreviewData) {
                    BitmapMemoryCache.getCached(story.mediaPreviewData, maxDim = 1080)
                }
                val bmp by produceState(initialValue = cachedStoryBmp, key1 = story.mediaPreviewData) {
                    value = BitmapMemoryCache.decodeDataUriCached(story.mediaPreviewData, maxDim = 1080)
                }
                val currentBmp = bmp
                if (currentBmp != null) {
                    Image(
                        bitmap = currentBmp,
                        contentDescription = "Historia de ${story.authorName}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Progress bars & author header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = 18.dp, start = 14.dp, end = 14.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    stories.forEachIndexed { idx, _ ->
                        val p = when {
                            idx < currentIndex -> 1f
                            idx == currentIndex -> progress
                            else -> 0f
                        }
                        LinearProgressIndicator(
                            progress = { p },
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.weight(1f).height(3.dp).clip(CircleShape)
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        VybAvatar(
                            avatarUrl = story.authorAvatarUrl,
                            displayName = story.authorName,
                            size = 38.dp,
                            isVerified = story.authorVerified
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = story.authorName,
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = formatRelativeTime(story.createdAt),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                                if (!story.locationLabel.isNullOrBlank()) {
                                    Text(
                                        text = " · ${story.locationLabel}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Visibility,
                            contentDescription = "Vistas",
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "${story.viewerUids.size}",
                            style = MaterialTheme.typography.labelMedium.merge(VybMonoTabularStyle),
                            color = Color.White
                        )
                        if (story.authorId == currentUserId) {
                            Spacer(Modifier.width(4.dp))
                            IconButton(
                                onClick = { showDeleteConfirm = true },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("story_delete_button")
                            ) {
                                Icon(
                                    Icons.Filled.DeleteOutline,
                                    contentDescription = "Eliminar estado",
                                    tint = Color.White
                                )
                            }
                        }
                        Spacer(Modifier.width(4.dp))
                        IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "Cerrar", tint = Color.White)
                        }
                    }
                }
            }

            // Caption + Direct Reply Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(16.dp)
            ) {
                if (!story.caption.isNullOrBlank()) {
                    Text(
                        text = story.caption,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White
                    )
                    Spacer(Modifier.height(10.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = replyText,
                        onValueChange = { replyText = it },
                        placeholder = { Text("Responder por mensaje directo...", color = Color.White.copy(0.7f)) },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (replyText.isNotBlank()) onReply(story, replyText)
                        },
                        enabled = replyText.isNotBlank(),
                        modifier = Modifier.size(48.dp).background(VybGradientBrush, CircleShape)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Enviar respuesta",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Eliminar estado") },
            text = { Text("¿Deseas eliminar este estado? Dejará de ser visible para tus contactos.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteStory(story)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("story_confirm_delete_button")
                ) {
                    Text("Eliminar", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancelar") }
            }
        )
    }
}
