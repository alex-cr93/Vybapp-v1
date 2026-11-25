package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.BitmapMemoryCache
import com.example.data.model.Post
import com.example.data.model.PostComment
import com.example.ui.theme.LocalVybColors
import com.example.ui.theme.VybElectricBlue
import com.example.ui.theme.VybLikeGradientBrush
import com.example.ui.theme.VybMonoTabularStyle
import com.example.ui.theme.VybPink
import com.example.ui.theme.VybSkyBlue
import com.example.ui.theme.VybViolet
import kotlinx.coroutines.launch

fun sharePostExternally(context: Context, post: Post, onShared: () -> Unit = {}) {
    onShared()
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(
            Intent.EXTRA_TEXT,
            "${post.authorName} (@${post.authorUsername}) en VYB:\n${post.text}"
        )
    }
    runCatching {
        context.startActivity(Intent.createChooser(shareIntent, "Compartir publicación"))
    }.onFailure {
        Toast.makeText(
            context,
            "No hay una aplicación disponible para compartir.",
            Toast.LENGTH_SHORT
        ).show()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VybPostCard(
    post: Post,
    currentUserId: String,
    authorAvatarUrlOverride: String? = null,
    authorStatus: String? = null,
    isBookmarked: Boolean,
    onLike: () -> Unit,
    onComment: () -> Unit,
    onShare: () -> Unit,
    onBookmark: () -> Unit,
    onDeletePost: () -> Unit,
    onTagClick: (String) -> Unit,
    onMediaClick: () -> Unit,
    onAuthorClick: (() -> Unit)? = null,
    outerPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
    modifier: Modifier = Modifier
) {
    val vybColors = LocalVybColors.current
    val isLiked = currentUserId in post.likedBy

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = vybColors.cardElevationDp),
        border = BorderStroke(0.75.dp, vybColors.hairlineBorder),
        modifier = modifier
            .fillMaxWidth()
            .padding(outerPadding)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (onAuthorClick != null) {
                                Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onAuthorClick() }
                            } else Modifier
                        )
                ) {
                    VybAvatar(
                        avatarUrl = authorAvatarUrlOverride ?: post.authorAvatarUrl,
                        displayName = post.authorName,
                        size = 44.dp,
                        isVerified = post.authorVerified
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = post.authorName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (!authorStatus.isNullOrBlank()) {
                                UserStatusBadge(
                                    status = authorStatus,
                                    modifier = Modifier.testTag("post_author_status_${post.id}")
                                )
                            }
                        }
                        Text(
                            text = "@${post.authorUsername} · ${formatRelativeTime(post.createdAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = vybColors.secondaryText
                        )
                    }
                }
                if (post.authorId == currentUserId) {
                    IconButton(
                        onClick = onDeletePost,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("post_delete_button_${post.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = "Eliminar publicación",
                            tint = vybColors.secondaryText
                        )
                    }
                }
                IconButton(
                    onClick = onBookmark,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("post_bookmark_button_${post.id}")
                ) {
                    Icon(
                        imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        contentDescription = "Guardar publicación",
                        tint = if (isBookmarked) vybColors.linkAccent else vybColors.secondaryText
                    )
                }
            }

            if (post.text.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(text = post.text, style = MaterialTheme.typography.bodyLarge)
            }

            if (post.hashtags.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    post.hashtags.forEach { tag ->
                        Text(
                            text = "#$tag",
                            style = MaterialTheme.typography.labelMedium,
                            color = vybColors.linkAccent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onTagClick(tag) }
                                .padding(horizontal = 4.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (post.mediaType != "none") {
                Spacer(Modifier.height(12.dp))
                val ratio = (post.mediaAspectRatio ?: 1.33).toFloat().coerceIn(0.6f, 2.2f)
                val cachedBmp = remember(post.mediaPreviewData) {
                    BitmapMemoryCache.getCached(post.mediaPreviewData, maxDim = 720)
                }
                val previewBmp by produceState(initialValue = cachedBmp, key1 = post.mediaPreviewData) {
                    value = BitmapMemoryCache.decodeDataUriCached(post.mediaPreviewData, maxDim = 720)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(ratio)
                        .clip(RoundedCornerShape(16.dp))
                        .background(vybColors.nestedSurface)
                        .clickable { onMediaClick() },
                    contentAlignment = Alignment.Center
                ) {
                    val currentPreviewBmp = previewBmp
                    if (currentPreviewBmp != null) {
                        Image(
                            bitmap = currentPreviewBmp,
                            contentDescription = "Multimedia de la publicación",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    if (post.mediaType == "video") {
                        Icon(
                            imageVector = Icons.Filled.PlayCircleFilled,
                            contentDescription = "Reproducir video",
                            tint = Color.White,
                            modifier = Modifier.size(56.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(thickness = 0.75.dp, color = vybColors.hairlineBorder)
            Spacer(Modifier.height(10.dp))

            val subtleLikeBrush = remember(vybColors.isDark) {
                Brush.linearGradient(
                    colors = listOf(
                        VybPink.copy(alpha = if (vybColors.isDark) 0.16f else 0.10f),
                        VybViolet.copy(alpha = if (vybColors.isDark) 0.14f else 0.08f)
                    )
                )
            }
            val commentPillBrush = remember(vybColors.isDark) {
                Brush.linearGradient(
                    colors = listOf(
                        VybElectricBlue.copy(alpha = if (vybColors.isDark) 0.22f else 0.11f),
                        VybViolet.copy(alpha = if (vybColors.isDark) 0.16f else 0.08f)
                    )
                )
            }
            val sharePillBrush = remember(vybColors.isDark) {
                Brush.linearGradient(
                    colors = listOf(
                        VybSkyBlue.copy(alpha = if (vybColors.isDark) 0.20f else 0.11f),
                        VybElectricBlue.copy(alpha = if (vybColors.isDark) 0.16f else 0.08f)
                    )
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PostActionPill(
                    icon = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    countText = "${post.likedBy.size}",
                    contentDescription = if (isLiked) "Quitar me gusta" else "Me gusta",
                    backgroundBrush = if (isLiked) VybLikeGradientBrush else subtleLikeBrush,
                    borderColor = if (isLiked) Color.White.copy(alpha = 0.30f) else VybPink.copy(alpha = 0.32f),
                    contentTint = if (isLiked) Color.White else VybPink,
                    emphasisPop = isLiked,
                    onClick = onLike,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("post_like_pill_${post.id}")
                )

                PostActionPill(
                    icon = Icons.Filled.ChatBubbleOutline,
                    countText = "${post.commentsCount}",
                    contentDescription = "Comentarios",
                    backgroundBrush = commentPillBrush,
                    borderColor = VybElectricBlue.copy(alpha = 0.34f),
                    contentTint = if (vybColors.isDark) VybSkyBlue else VybElectricBlue,
                    emphasisPop = false,
                    onClick = onComment,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("post_comment_pill_${post.id}")
                )

                PostActionPill(
                    icon = Icons.Filled.Share,
                    countText = "${post.shareCount}",
                    contentDescription = "Compartir",
                    backgroundBrush = sharePillBrush,
                    borderColor = VybSkyBlue.copy(alpha = 0.34f),
                    contentTint = if (vybColors.isDark) Color(0xFFC4B5FD) else VybViolet,
                    emphasisPop = false,
                    onClick = onShare,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("post_share_pill_${post.id}")
                )
            }
        }
    }
}

@Composable
private fun PostActionPill(
    icon: ImageVector,
    countText: String,
    contentDescription: String,
    backgroundBrush: Brush,
    borderColor: Color,
    contentTint: Color,
    emphasisPop: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val scaleAnim = remember { Animatable(1f) }
    val animatedTint by animateColorAsState(
        targetValue = contentTint,
        animationSpec = tween(220),
        label = "post_pill_tint"
    )
    val animatedBorder by animateColorAsState(
        targetValue = borderColor,
        animationSpec = tween(220),
        label = "post_pill_border"
    )

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color.Transparent,
        border = BorderStroke(0.9.dp, animatedBorder),
        modifier = modifier
            .heightIn(min = 48.dp)
            .graphicsLayer {
                val s = scaleAnim.value
                scaleX = s
                scaleY = s
            }
            .clip(RoundedCornerShape(999.dp))
            .clickable {
                scope.launch {
                    scaleAnim.snapTo(0.88f)
                    if (emphasisPop) {
                        scaleAnim.animateTo(
                            targetValue = 1.06f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                    }
                    scaleAnim.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                }
                onClick()
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(backgroundBrush)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = animatedTint,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = countText,
                    style = MaterialTheme.typography.labelLarge.merge(VybMonoTabularStyle),
                    color = animatedTint,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostCommentsModalSheet(
    post: Post,
    comments: List<PostComment>,
    currentUserId: String,
    onDismiss: () -> Unit,
    onSendComment: (text: String, onSuccess: () -> Unit) -> Unit,
    onRequestDeleteComment: (PostComment) -> Unit
) {
    val vybColors = LocalVybColors.current
    var newComment by remember { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("Comentarios en vivo", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(10.dp))
                if (comments.isEmpty()) {
                    Text(
                        "Sé la primera persona en comentar.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = vybColors.secondaryText,
                        modifier = Modifier.padding(vertical = 20.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().height(260.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            items = comments,
                            key = { it.id },
                            contentType = { "comment_item" }
                        ) { comment ->
                            val canDeleteComment = comment.authorId == currentUserId || post.authorId == currentUserId
                            Row(verticalAlignment = Alignment.Top) {
                                VybAvatar(
                                    avatarUrl = comment.authorAvatarUrl,
                                    displayName = comment.authorName,
                                    size = 34.dp
                                )
                                Spacer(Modifier.width(10.dp))
                                Surface(
                                    color = vybColors.nestedSurface,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    "${comment.authorName} @${comment.authorUsername}",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    formatRelativeTime(comment.createdAt),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = vybColors.secondaryText
                                                )
                                            }
                                            if (canDeleteComment) {
                                                IconButton(
                                                    onClick = { onRequestDeleteComment(comment) },
                                                    modifier = Modifier
                                                        .size(48.dp)
                                                        .testTag("comment_delete_button_${comment.id}")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.DeleteOutline,
                                                        contentDescription = "Eliminar comentario",
                                                        tint = vybColors.secondaryText,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Text(comment.text, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    OutlinedTextField(
                        value = newComment,
                        onValueChange = { newComment = it.take(500) },
                        placeholder = { Text("Escribe un comentario...") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            onSendComment(newComment) { newComment = "" }
                        },
                        enabled = newComment.isNotBlank(),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar comentario")
                    }
                }
            }
        }
    }
}
