package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.core.BitmapMemoryCache
import com.example.core.MediaUtils
import com.example.data.model.ChatMessage
import com.example.data.model.ThemeMode
import com.example.data.repository.UserRepository
import com.example.ui.theme.LocalVybColors
import com.example.ui.theme.VybElectricBlue
import com.example.ui.theme.VybGradientBrush
import com.example.ui.theme.VybMagenta
import com.example.ui.theme.VybMonoTabularStyle
import com.example.ui.theme.VybSkyBlue
import com.example.ui.theme.VybViolet
import com.google.firebase.Timestamp
import kotlinx.coroutines.delay
import java.io.File
import java.text.NumberFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

val COSTA_RICA_TIME_ZONE: TimeZone = TimeZone.getTimeZone("America/Costa_Rica")

fun formatCrPhoneMask(digits: String): String {
    val clean = digits.filter { it.isDigit() }.take(8)
    return if (clean.length <= 4) clean else "${clean.substring(0, 4)}-${clean.substring(4)}"
}

class CostaRicaPhoneVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text.filter { it.isDigit() }.take(8)
        val formatted = formatCrPhoneMask(raw)
        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val clamped = offset.coerceIn(0, raw.length)
                return if (clamped <= 4) clamped else clamped + 1
            }

            override fun transformedToOriginal(offset: Int): Int {
                val clamped = offset.coerceIn(0, formatted.length)
                return if (clamped <= 4) clamped else (clamped - 1).coerceIn(0, raw.length)
            }
        }
        return TransformedText(AnnotatedString(formatted), offsetMapping)
    }
}

@Composable
fun UserStatusBadge(
    status: String,
    modifier: Modifier = Modifier
) {
    val clean = status.trim()
    if (clean.isBlank()) return
    val vybColors = LocalVybColors.current
    val accent = when (clean.lowercase()) {
        "disponible" -> vybColors.sinpeEmerald
        "en foco" -> VybViolet
        "en camino" -> VybSkyBlue
        "en casa" -> Color(0xFF14B8A6)
        "en clase" -> VybElectricBlue
        "libre hoy" -> VybMagenta
        else -> VybElectricBlue
    }
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = accent.copy(alpha = if (vybColors.isDark) 0.16f else 0.11f),
        border = BorderStroke(0.65.dp, accent.copy(alpha = 0.38f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Text(
                text = clean,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = accent,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun VybAvatar(
    avatarUrl: String?,
    displayName: String,
    size: Dp = 44.dp,
    showGradientRing: Boolean = false,
    isVerified: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val vybColors = LocalVybColors.current
    val cachedBitmap = remember(avatarUrl) {
        BitmapMemoryCache.getCached(avatarUrl, maxDim = 256)
    }
    val decodedBitmap by produceState(initialValue = cachedBitmap, key1 = avatarUrl) {
        value = cachedBitmap
        value = if (avatarUrl != null && avatarUrl.startsWith("data:image")) {
            BitmapMemoryCache.decodeDataUriCached(avatarUrl, maxDim = 256)
        } else {
            null
        }
    }

    Box(modifier = modifier.size(size)) {
        val outerMod = if (showGradientRing) {
            Modifier
                .fillMaxSize()
                .border(2.dp, VybGradientBrush, CircleShape)
                .padding(3.dp)
        } else {
            Modifier
                .fillMaxSize()
                .border(1.dp, vybColors.hairlineBorder, CircleShape)
        }

        val currentDecodedBitmap = if (avatarUrl != null && avatarUrl.startsWith("data:image")) {
            decodedBitmap ?: cachedBitmap
        } else {
            null
        }
        val isNetworkOrUri = !avatarUrl.isNullOrBlank() &&
            (avatarUrl.startsWith("http") || avatarUrl.startsWith("content://") || avatarUrl.startsWith("file://"))

        Box(
            modifier = outerMod
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (currentDecodedBitmap != null) {
                Image(
                    bitmap = currentDecodedBitmap,
                    contentDescription = "Avatar de $displayName",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                val initials = displayName.trim()
                    .split(" ")
                    .filter { it.isNotBlank() }
                    .take(2)
                    .joinToString("") { it.first().uppercaseChar().toString() }
                    .ifBlank { "V" }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(VybGradientBrush),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initials,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (isNetworkOrUri) {
                    val request = remember(avatarUrl, context) {
                        coil.request.ImageRequest.Builder(context)
                            .data(avatarUrl)
                            .crossfade(true)
                            .build()
                    }
                    AsyncImage(
                        model = request,
                        contentDescription = "Avatar de $displayName",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        if (isVerified) {
            Icon(
                imageVector = Icons.Filled.Verified,
                contentDescription = "Cuenta verificada",
                tint = VybSkyBlue,
                modifier = Modifier
                    .size(16.dp)
                    .align(Alignment.BottomEnd)
                    .background(MaterialTheme.colorScheme.background, CircleShape)
            )
        }
    }
}

@Composable
fun VybPrimaryGradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    testTag: String? = null
) {
    val baseMod = if (testTag != null) modifier.testTag(testTag) else modifier
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        contentPadding = PaddingValues(0.dp),
        shape = RoundedCornerShape(16.dp),
        modifier = baseMod.height(50.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = if (enabled) VybGradientBrush
                    else androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(16.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (enabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun VybGoogleCircleButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    contentDescription: String = "Continuar con Google",
    modifier: Modifier = Modifier,
    testTag: String? = null
) {
    val vybColors = LocalVybColors.current
    val baseMod = if (testTag != null) modifier.testTag(testTag) else modifier
    Surface(
        onClick = onClick,
        enabled = enabled && !isLoading,
        shape = CircleShape,
        color = Color.White,
        shadowElevation = if (vybColors.isDark) 4.dp else 3.dp,
        border = BorderStroke(
            width = 1.dp,
            color = if (vybColors.isDark) Color.White.copy(alpha = 0.24f) else vybColors.hairlineBorder
        ),
        modifier = baseMod
            .minimumInteractiveComponentSize()
            .size(54.dp)
            .semantics {
                this.contentDescription = contentDescription
                this.role = Role.Button
            }
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = VybElectricBlue,
                    strokeWidth = 2.2.dp
                )
            } else {
                Canvas(modifier = Modifier.size(24.dp)) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val outerRadius = size.minDimension * 0.45f
                    val strokeWidth = size.minDimension * 0.19f
                    val midRadius = outerRadius - strokeWidth / 2f
                    val arcTopLeft = Offset(cx - midRadius, cy - midRadius)
                    val arcSize = Size(midRadius * 2f, midRadius * 2f)
                    val ringStroke = Stroke(width = strokeWidth, cap = StrokeCap.Butt)

                    val outerCirclePath = Path().apply {
                        addOval(
                            Rect(
                                offset = Offset(cx - outerRadius, cy - outerRadius),
                                size = Size(outerRadius * 2f, outerRadius * 2f)
                            )
                        )
                    }

                    clipPath(outerCirclePath) {
                        // Top segment (Red)
                        drawArc(
                            color = Color(0xFFEA4335),
                            startAngle = -148f,
                            sweepAngle = 108f,
                            useCenter = false,
                            topLeft = arcTopLeft,
                            size = arcSize,
                            style = ringStroke
                        )
                        // Left segment (Yellow)
                        drawArc(
                            color = Color(0xFFFBBC05),
                            startAngle = 146f,
                            sweepAngle = 68f,
                            useCenter = false,
                            topLeft = arcTopLeft,
                            size = arcSize,
                            style = ringStroke
                        )
                        // Bottom segment (Green)
                        drawArc(
                            color = Color(0xFF34A853),
                            startAngle = 40f,
                            sweepAngle = 108f,
                            useCenter = false,
                            topLeft = arcTopLeft,
                            size = arcSize,
                            style = ringStroke
                        )
                        // Lower-right arc (Blue)
                        drawArc(
                            color = Color(0xFF4285F4),
                            startAngle = -2f,
                            sweepAngle = 44f,
                            useCenter = false,
                            topLeft = arcTopLeft,
                            size = arcSize,
                            style = ringStroke
                        )
                        // Crossbar (Blue)
                        drawRect(
                            color = Color(0xFF4285F4),
                            topLeft = Offset(cx - strokeWidth * 0.04f, cy - strokeWidth / 2f),
                            size = Size(outerRadius + strokeWidth * 0.04f, strokeWidth)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OfflineIndicator(isOnline: Boolean) {
    AnimatedVisibility(visible = !isOnline) {
        Surface(
            color = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.WifiOff,
                    contentDescription = "Sin conexión",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Sin conexión a internet — los cambios se sincronizarán al reconectar",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
fun EmptyStateView(
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    actionTestTag: String? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            VybLogoEmblem(size = 72.dp)
            Spacer(Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = LocalVybColors.current.secondaryText,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(20.dp))
                VybPrimaryGradientButton(
                    text = actionLabel,
                    onClick = onAction,
                    testTag = actionTestTag,
                    modifier = Modifier.width(220.dp)
                )
            }
        }
    }
}

@Composable
fun ThemeSelectorRow(
    currentMode: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val vybColors = LocalVybColors.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ThemeMode.entries.forEach { mode ->
            val selected = mode == currentMode
            val icon = when (mode) {
                ThemeMode.SYSTEM -> Icons.Filled.SettingsBrightness
                ThemeMode.LIGHT -> Icons.Filled.LightMode
                ThemeMode.AMOLED -> Icons.Filled.DarkMode
            }
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else vybColors.nestedSurface,
                border = BorderStroke(
                    width = if (selected) 1.5.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else vybColors.hairlineBorder
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onSelect(mode) }
                    .testTag("theme_option_${mode.name.lowercase()}")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = mode.label,
                        modifier = Modifier.size(16.dp),
                        tint = if (selected) MaterialTheme.colorScheme.primary else vybColors.secondaryText
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = mode.label,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        softWrap = false,
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun SinpeCard(
    message: ChatMessage,
    currentUserId: String,
    onAuthorizePayment: () -> Unit
) {
    val context = LocalContext.current
    val vybColors = LocalVybColors.current
    val isCompleted = message.sinpeStatus == "completed"
    val paidByMe = currentUserId in (message.sinpePaidByUids ?: emptyList())
    val isRecipient = message.sinpeRecipientUid == currentUserId

    val total = message.sinpeAmount ?: 0L
    val perPerson = message.sinpePerPerson ?: total
    val splitCount = (message.sinpeSplitCount ?: 1L).toInt()
    val formattedPhone = remember(message.sinpeRecipientPhone) {
        formatCostaRicaPhone(message.sinpeRecipientPhone) ?: "No configurado"
    }
    val formattedExactAmount = remember(perPerson) { formatColones(perPerson) }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCompleted) vybColors.sinpeContainer else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = vybColors.cardElevationDp),
        border = BorderStroke(1.dp, vybColors.sinpeEmerald.copy(alpha = 0.7f)),
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .testTag("sinpe_card_${message.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.PhoneAndroid,
                        contentDescription = "SINPE Móvil",
                        tint = vybColors.sinpeEmerald,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "SINPE MÓVIL",
                        style = MaterialTheme.typography.labelLarge,
                        color = vybColors.sinpeEmerald,
                        fontWeight = FontWeight.Bold
                    )
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (isCompleted) {
                        vybColors.sinpeEmerald.copy(alpha = 0.16f)
                    } else {
                        Color(0xFFF59E0B).copy(alpha = 0.14f)
                    },
                    border = BorderStroke(
                        0.75.dp,
                        if (isCompleted) {
                            vybColors.sinpeEmerald.copy(alpha = 0.45f)
                        } else {
                            Color(0xFFF59E0B).copy(alpha = 0.45f)
                        }
                    ),
                    modifier = Modifier.testTag("sinpe_status_badge_${message.id}")
                ) {
                    Text(
                        text = if (isCompleted) "✓ Completado" else "Pendiente",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isCompleted) {
                            vybColors.sinpeEmerald
                        } else if (vybColors.isDark) {
                            Color(0xFFFBBF24)
                        } else {
                            Color(0xFFB45309)
                        },
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = message.sinpeReason ?: "Cobro compartido",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(6.dp))

            Text(
                text = formattedExactAmount,
                style = MaterialTheme.typography.headlineMedium.merge(VybMonoTabularStyle),
                color = vybColors.sinpeEmerald,
                fontWeight = FontWeight.Bold
            )
            if (splitCount > 1) {
                Text(
                    text = "Cuota por persona ($splitCount pers.) · Total ${formatColones(total)}",
                    style = MaterialTheme.typography.bodySmall.merge(VybMonoTabularStyle),
                    color = vybColors.secondaryText
                )
            }

            Spacer(Modifier.height(10.dp))
            Surface(
                color = vybColors.nestedSurface,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(0.75.dp, vybColors.hairlineBorder)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                    Text(
                        text = "Destinatario: ${message.sinpeRecipientName ?: message.senderName}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = formattedPhone,
                            style = MaterialTheme.typography.bodySmall.merge(VybMonoTabularStyle)
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Filled.AccountBalance,
                            contentDescription = "Banco",
                            modifier = Modifier.size(13.dp),
                            tint = vybColors.secondaryText
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = message.sinpeRecipientBank ?: "Banco",
                            style = MaterialTheme.typography.bodySmall,
                            color = vybColors.secondaryText
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            if (isCompleted) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Pago completado vía SINPE Móvil",
                        style = MaterialTheme.typography.labelLarge,
                        color = vybColors.sinpeOnContainer,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = formatMessageTime(message.createdAt),
                        style = MaterialTheme.typography.labelSmall.merge(VybMonoTabularStyle),
                        color = vybColors.secondaryText
                    )
                }
            } else {
                if (splitCount > 1 && paidByMe && !isRecipient) {
                    Text(
                        text = "Tu cuota fue registrada · pendiente del resto del grupo.",
                        style = MaterialTheme.typography.labelSmall,
                        color = vybColors.sinpeEmerald,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                var copiedNumber by remember(message.id) { mutableStateOf(false) }
                LaunchedEffect(copiedNumber) {
                    if (copiedNumber) {
                        delay(2000L)
                        copiedNumber = false
                    }
                }
                val hasCopyablePhone = formattedPhone != "No configurado" && formattedPhone.isNotBlank()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            if (hasCopyablePhone) {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("Número SINPE Móvil", formattedPhone))
                                copiedNumber = true
                                Toast.makeText(
                                    context,
                                    "Número SINPE copiado: $formattedPhone",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    "El destinatario aún no configuró su número SINPE.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            width = 0.9.dp,
                            color = if (copiedNumber) vybColors.sinpeEmerald else vybColors.hairlineBorder
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("sinpe_copy_data_button")
                    ) {
                        Icon(
                            imageVector = if (copiedNumber) Icons.Filled.Check else Icons.Filled.ContentCopy,
                            contentDescription = null,
                            tint = if (copiedNumber) vybColors.sinpeEmerald else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = if (copiedNumber) "¡Copiado!" else "Copiar número",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (copiedNumber) vybColors.sinpeEmerald else MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    val buttonEnabled = isRecipient || !paidByMe
                    Button(
                        onClick = onAuthorizePayment,
                        enabled = buttonEnabled,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = vybColors.sinpeEmerald,
                            contentColor = Color.White,
                            disabledContainerColor = vybColors.sinpeEmerald.copy(alpha = 0.25f),
                            disabledContentColor = vybColors.sinpeEmerald
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("sinpe_confirm_payment_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = if (buttonEnabled) Color.White else vybColors.sinpeEmerald
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = if (paidByMe && !isRecipient) "Ya pagaste" else "Marcar pagado",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (buttonEnabled) Color.White else vybColors.sinpeEmerald,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = formatMessageTime(message.createdAt),
                        style = MaterialTheme.typography.labelSmall.merge(VybMonoTabularStyle),
                        color = vybColors.secondaryText
                    )
                }
            }
        }
    }
}

@Composable
fun ConfettiOverlay(triggerCount: Int) {
    if (triggerCount <= 0) return
    var show by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    val particles = remember(triggerCount) {
        List(42) {
            val angle = Random.nextDouble(0.0, Math.PI * 2)
            val speed = Random.nextFloat() * 0.45f + 0.2f
            Triple(
                angle,
                speed,
                listOf(VybSkyBlue, VybElectricBlue, VybViolet, VybMagenta, Color(0xFF10B981)).random()
            )
        }
    }

    LaunchedEffect(triggerCount) {
        show = true
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = 1500, easing = LinearEasing))
        show = false
    }

    if (show) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val p = progress.value
            val origin = Offset(size.width / 2f, size.height * 0.45f)
            particles.forEach { (angle, speed, color) ->
                val dist = p * size.minDimension * speed
                val x = origin.x + (cos(angle) * dist).toFloat()
                val y = origin.y + (sin(angle) * dist).toFloat() + (p * p * 260f)
                drawCircle(
                    color = color.copy(alpha = (1f - p).coerceIn(0f, 1f)),
                    radius = 9f * (1f - p * 0.4f),
                    center = Offset(x, y)
                )
            }
        }
    }
}

@Composable
fun ZoomableMediaLightbox(
    mediaType: String,
    previewDataUri: String?,
    mediaId: String?,
    loadFullMediaFile: suspend (String) -> File?,
    bombRemainingSec: Long? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var loadedFile by remember { mutableStateOf<File?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(mediaId) {
        if (!mediaId.isNullOrBlank()) {
            isLoading = true
            loadedFile = loadFullMediaFile(mediaId)
            isLoading = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
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
                .background(Color.Black)
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            if (mediaType == "video") {
                val file = loadedFile
                if (file != null) {
                    val exoPlayer = remember(file) {
                        ExoPlayer.Builder(context).build().apply {
                            setMediaItem(MediaItem.fromUri(file.toURI().toString()))
                            prepare()
                            playWhenReady = true
                        }
                    }
                    DisposableEffect(exoPlayer) {
                        onDispose { exoPlayer.release() }
                    }
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = true
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    CircularProgressIndicator(color = Color.White)
                }
            } else {
                var scale by remember { mutableFloatStateOf(1f) }
                var offset by remember { mutableStateOf(Offset.Zero) }

                val previewBmp = remember(previewDataUri) {
                    MediaUtils.decodeBase64DataUri(previewDataUri)?.let { bytes ->
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    }
                }
                val fullBmp = remember(loadedFile) {
                    loadedFile?.let { file ->
                        BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
                    }
                }

                val bmp = fullBmp ?: previewBmp
                if (bmp != null) {
                    Image(
                        bitmap = bmp,
                        contentDescription = "Vista ampliada",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    val newScale = (scale * zoom).coerceIn(1f, 4f)
                                    scale = newScale
                                    offset = if (newScale > 1f) offset + pan else Offset.Zero
                                }
                            }
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            )
                    )
                } else if (isLoading) {
                    CircularProgressIndicator(color = Color.White)
                }
            }

            if (bombRemainingSec != null) {
                val isUrgent = bombRemainingSec <= 5L
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (isUrgent) Color(0xFFEF4444).copy(alpha = 0.88f) else Color(0xFFEA580C).copy(alpha = 0.88f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LocalFireDepartment,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "💣 Se elimina en ${bombRemainingSec}s",
                            style = MaterialTheme.typography.labelLarge.merge(VybMonoTabularStyle),
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(48.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Cerrar visor",
                    tint = Color.White
                )
            }
        }
    }
}

fun formatCostaRicaPhone(raw: String?): String? = UserRepository.formatCrPhone(raw)

fun formatColones(amount: Long): String {
    val nf = NumberFormat.getNumberInstance(Locale("es", "CR"))
    return "₡${nf.format(amount)}"
}

fun formatRelativeTime(timestamp: Timestamp?): String {
    if (timestamp == null) return "Ahora"
    val diffSec = ((System.currentTimeMillis() - timestamp.toDate().time) / 1000L).coerceAtLeast(0L)
    return when {
        diffSec < 60 -> "Ahora"
        diffSec < 3600 -> "Hace ${diffSec / 60} min"
        diffSec < 86400 -> "Hace ${diffSec / 3600} h"
        else -> "Hace ${diffSec / 86400} d"
    }
}

fun formatMessageTime(
    timestamp: Timestamp?,
    nowMillis: Long = System.currentTimeMillis()
): String {
    val date = timestamp?.toDate() ?: Date(nowMillis)
    val cal = Calendar.getInstance(COSTA_RICA_TIME_ZONE).apply { time = date }
    val hour24 = cal.get(Calendar.HOUR_OF_DAY)
    val hour12 = when (val h = hour24 % 12) {
        0 -> 12
        else -> h
    }
    val minute = cal.get(Calendar.MINUTE)
    val amPm = if (hour24 < 12) "a. m." else "p. m."
    return String.format(Locale.US, "%02d:%02d %s", hour12, minute, amPm)
}

fun chatMessageDayKey(
    timestamp: Timestamp?,
    nowMillis: Long = System.currentTimeMillis()
): String {
    val date = timestamp?.toDate() ?: Date(nowMillis)
    val cal = Calendar.getInstance(COSTA_RICA_TIME_ZONE).apply { time = date }
    val year = cal.get(Calendar.YEAR)
    val month = cal.get(Calendar.MONTH) + 1
    val day = cal.get(Calendar.DAY_OF_MONTH)
    return String.format(Locale.US, "%04d-%02d-%02d", year, month, day)
}

private val SPANISH_MONTH_NAMES = listOf(
    "enero", "febrero", "marzo", "abril", "mayo", "junio",
    "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"
)

fun formatChatDateDivider(
    timestamp: Timestamp?,
    nowMillis: Long = System.currentTimeMillis()
): String {
    val targetDate = timestamp?.toDate() ?: Date(nowMillis)
    val msgCal = Calendar.getInstance(COSTA_RICA_TIME_ZONE).apply { time = targetDate }
    val todayCal = Calendar.getInstance(COSTA_RICA_TIME_ZONE).apply { timeInMillis = nowMillis }
    val yesterdayCal = Calendar.getInstance(COSTA_RICA_TIME_ZONE).apply {
        timeInMillis = nowMillis
        add(Calendar.DAY_OF_YEAR, -1)
    }

    val msgYear = msgCal.get(Calendar.YEAR)
    val msgDayOfYear = msgCal.get(Calendar.DAY_OF_YEAR)

    return when {
        msgYear == todayCal.get(Calendar.YEAR) && msgDayOfYear == todayCal.get(Calendar.DAY_OF_YEAR) -> "Hoy"
        msgYear == yesterdayCal.get(Calendar.YEAR) && msgDayOfYear == yesterdayCal.get(Calendar.DAY_OF_YEAR) -> "Ayer"
        msgYear == todayCal.get(Calendar.YEAR) -> {
            val day = msgCal.get(Calendar.DAY_OF_MONTH)
            val month = SPANISH_MONTH_NAMES[msgCal.get(Calendar.MONTH).coerceIn(0, 11)]
            "$day de $month"
        }
        else -> {
            val day = msgCal.get(Calendar.DAY_OF_MONTH)
            val month = SPANISH_MONTH_NAMES[msgCal.get(Calendar.MONTH).coerceIn(0, 11)]
            "$day de $month de $msgYear"
        }
    }
}
