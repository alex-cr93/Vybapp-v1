package com.example.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.core.MediaUtils
import com.example.data.model.AiModelChoice
import com.example.data.model.Chat
import com.example.data.model.ChatMessage
import com.example.data.model.StructuredChatSummary
import com.example.ui.components.VybLogoBadge
import com.example.ui.components.VybPrimaryGradientButton
import com.example.ui.components.formatColones
import com.example.ui.theme.LocalVybColors
import com.example.ui.theme.VybElectricBlue
import com.example.ui.theme.VybGradientBrush
import com.example.ui.theme.VybIndigo
import com.example.ui.theme.VybMonoTabularStyle
import com.example.ui.theme.VybSkyBlue
import com.example.ui.theme.VybViolet
import com.example.ui.viewmodel.AssistantViewModel
import kotlinx.coroutines.launch

enum class AssistantToolTab(val label: String) {
    CHAT("Asistente"),
    SUMMARY("Resumen"),
    TONE("Selector de Tono"),
    RECEIPT("Escáner SINPE")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssistantModal(
    assistantViewModel: AssistantViewModel,
    modelChoice: AiModelChoice,
    initialTab: AssistantToolTab = AssistantToolTab.CHAT,
    initialPrompt: String? = null,
    initialDraft: String = "",
    activeChat: Chat? = null,
    activeChatMessages: List<ChatMessage> = emptyList(),
    privateAreaLabel: String? = null,
    onShareSummaryToChat: (StructuredChatSummary) -> Unit = {},
    onInsertToneIntoChat: (String) -> Unit = {},
    onSendSinpeFromReceipt: (totalAmount: Long, splitCount: Int, reason: String) -> Unit = { _, _, _ -> },
    onSelectModelChoice: (AiModelChoice) -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val vybColors = LocalVybColors.current
    val uiState by assistantViewModel.uiState.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf(initialTab) }
    var chatInput by remember { mutableStateOf("") }
    var chatImageUri by remember { mutableStateOf<Uri?>(null) }
    var toneDraft by remember { mutableStateOf(initialDraft) }
    var receiptUri by remember { mutableStateOf<Uri?>(null) }
    var receiptPeopleCount by remember { mutableIntStateOf(2) }
    var customTotalOverride by remember { mutableStateOf("") }

    var showAttachMenu by remember { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var cameraTargetIsReceipt by remember { mutableStateOf(false) }

    val chatImagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            chatImageUri = uri
        }
    }

    val receiptPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            receiptUri = uri
            assistantViewModel.scanReceipt(context, modelChoice, uri, receiptPeopleCount)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val captured = pendingCameraUri
        pendingCameraUri = null
        if (success && captured != null) {
            if (cameraTargetIsReceipt) {
                receiptUri = captured
                assistantViewModel.scanReceipt(context, modelChoice, captured, receiptPeopleCount)
            } else {
                chatImageUri = captured
            }
        }
    }

    fun launchCameraNow() {
        val tempUri = runCatching { MediaUtils.createTempCameraUri(context) }.getOrNull()
        if (tempUri == null) {
            Toast.makeText(context, "No se pudo abrir la cámara.", Toast.LENGTH_SHORT).show()
            return
        }
        pendingCameraUri = tempUri
        cameraLauncher.launch(tempUri)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCameraNow()
        } else {
            Toast.makeText(context, "Permiso de cámara requerido para tomar foto.", Toast.LENGTH_SHORT).show()
        }
    }

    fun requestCameraCapture(forReceiptTab: Boolean) {
        cameraTargetIsReceipt = forReceiptTab
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            launchCameraNow()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(initialPrompt) {
        if (!initialPrompt.isNullOrBlank()) {
            assistantViewModel.sendChatMessage(
                context = context,
                modelChoice = modelChoice,
                prompt = initialPrompt,
                privateAreaLabel = privateAreaLabel
            )
        }
    }

    LaunchedEffect(uiState.scannedReceipt) {
        uiState.scannedReceipt?.let {
            customTotalOverride = it.totalColones.toString()
            receiptPeopleCount = it.suggestedPeople.coerceIn(1, 20)
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

        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                VybElectricBlue.copy(alpha = if (vybColors.isDark) 0.14f else 0.06f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.85f, size.height * 0.08f),
                            radius = size.maxDimension * 0.55f
                        ),
                        radius = size.maxDimension * 0.55f,
                        center = Offset(size.width * 0.85f, size.height * 0.08f)
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                VybViolet.copy(alpha = if (vybColors.isDark) 0.12f else 0.05f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.12f, size.height * 0.72f),
                            radius = size.maxDimension * 0.48f
                        ),
                        radius = size.maxDimension * 0.48f,
                        center = Offset(size.width * 0.12f, size.height * 0.72f)
                    )
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                // Clean, distraction-free header (no model pills or tab strip)
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = if (vybColors.isDark) 0.92f else 0.98f),
                    tonalElevation = 2.dp
                ) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    Column(modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (currentTab != AssistantToolTab.CHAT) {
                                    IconButton(
                                        onClick = { currentTab = AssistantToolTab.CHAT },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("assistant_back_to_chat")
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Volver al chat"
                                        )
                                    }
                                } else {
                                    Spacer(Modifier.width(4.dp))
                                }
                                VybLogoBadge(size = 42.dp)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = when (currentTab) {
                                            AssistantToolTab.CHAT -> "Asistente VYB"
                                            AssistantToolTab.SUMMARY -> "Resumen de chat"
                                            AssistantToolTab.TONE -> "Selector de tono"
                                            AssistantToolTab.RECEIPT -> "Escáner de facturas"
                                        },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .clip(CircleShape)
                                                .background(vybColors.sinpeEmerald)
                                        )
                                        Text(
                                            text = if (currentTab == AssistantToolTab.CHAT) {
                                                "Tu ayuda inteligente en chats"
                                            } else {
                                                "Herramienta del Asistente VYB"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = vybColors.secondaryText
                                        )
                                    }
                                }
                            }
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("assistant_close_button")
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Cerrar asistente")
                            }
                        }

                        if (!privateAreaLabel.isNullOrBlank()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.LocationOn,
                                    contentDescription = "Zona privada",
                                    tint = VybElectricBlue,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "Tu zona: $privateAreaLabel",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = vybColors.secondaryText
                                )
                            }
                        }
                    }
                    }
                }
                HorizontalDivider(thickness = 0.75.dp, color = vybColors.hairlineBorder)

                AnimatedVisibility(
                    visible = uiState.activeModelNotice != null,
                    enter = fadeIn(tween(180)),
                    exit = fadeOut(tween(120))
                ) {
                    uiState.activeModelNotice?.let { notice ->
                        Surface(
                            color = vybColors.subtleSurface,
                            border = BorderStroke(0.75.dp, vybColors.hairlineBorder),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .testTag("assistant_model_notice")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    tint = VybElectricBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = notice,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = vybColors.secondaryText
                                )
                            }
                        }
                    }
                }

                uiState.errorMessage?.let { err ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .testTag("assistant_error_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = err,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            if (uiState.lastFailedPrompt != null) {
                                TextButton(
                                    onClick = {
                                        assistantViewModel.retryLastMessage(
                                            context = context,
                                            modelChoice = modelChoice,
                                            privateAreaLabel = privateAreaLabel
                                        )
                                    },
                                    modifier = Modifier.testTag("assistant_retry_button")
                                ) {
                                    Text("Reintentar")
                                }
                            }
                            TextButton(onClick = { assistantViewModel.clearError() }) {
                                Text("Cerrar")
                            }
                        }
                    }
                }

                AnimatedContent(
                    targetState = currentTab,
                    transitionSpec = {
                        (fadeIn(tween(200)) + slideInHorizontally(tween(220)) { width ->
                            if (targetState == AssistantToolTab.CHAT) -width / 5 else width / 5
                        }) togetherWith (fadeOut(tween(140)) + slideOutHorizontally(tween(180)) { width ->
                            if (targetState == AssistantToolTab.CHAT) width / 5 else -width / 5
                        })
                    },
                    label = "assistant_view_transition",
                    modifier = Modifier.weight(1f)
                ) { tab ->
                    when (tab) {
                        AssistantToolTab.CHAT -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.TopCenter
                            ) {
                            Column(
                                modifier = Modifier
                                    .widthIn(max = 720.dp)
                                    .fillMaxSize()
                            ) {
                                val listState = rememberLazyListState()
                                LaunchedEffect(uiState.entries.size, uiState.entries.lastOrNull()?.text) {
                                    if (uiState.entries.isNotEmpty()) {
                                        listState.animateScrollToItem(uiState.entries.lastIndex)
                                    }
                                }

                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                    contentPadding = PaddingValues(
                                        start = 16.dp,
                                        end = 16.dp,
                                        top = 14.dp,
                                        bottom = 14.dp
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(uiState.entries, key = { it.id }) { entry ->
                                        if (entry.id == "welcome" && !entry.isUser) {
                                            AssistantHeroWelcomeCard(
                                                greetingText = entry.text,
                                                onOpenSummary = { currentTab = AssistantToolTab.SUMMARY },
                                                onOpenTone = { currentTab = AssistantToolTab.TONE },
                                                onOpenReceipt = { currentTab = AssistantToolTab.RECEIPT },
                                                onSelectQuickPrompt = { prompt -> chatInput = prompt }
                                            )
                                        } else {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .animateItem(),
                                                horizontalArrangement = if (entry.isUser) Arrangement.End else Arrangement.Start
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(
                                                        topStart = 20.dp,
                                                        topEnd = 20.dp,
                                                        bottomStart = if (entry.isUser) 20.dp else 6.dp,
                                                        bottomEnd = if (entry.isUser) 6.dp else 20.dp
                                                    ),
                                                    color = if (entry.isUser) Color.Transparent else MaterialTheme.colorScheme.surface,
                                                    border = if (entry.isUser) {
                                                        BorderStroke(0.75.dp, vybColors.senderBubbleBorder)
                                                    } else {
                                                        BorderStroke(0.9.dp, vybColors.incomingBubbleBorder)
                                                    },
                                                    tonalElevation = if (entry.isUser) 0.dp else 2.dp,
                                                    modifier = Modifier
                                                        .widthIn(max = 320.dp)
                                                        .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow))
                                                ) {
                                                    Box(
                                                        modifier = if (entry.isUser) {
                                                            Modifier
                                                                .background(VybGradientBrush)
                                                                .padding(14.dp)
                                                        } else {
                                                            Modifier.padding(14.dp)
                                                        }
                                                    ) {
                                                        Column {
                                                            if (entry.text.isBlank() && entry.isStreaming) {
                                                                Row(
                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                                ) {
                                                                    CircularProgressIndicator(
                                                                        modifier = Modifier.size(16.dp),
                                                                        strokeWidth = 2.dp
                                                                    )
                                                                    Text(
                                                                        text = "Pensando...",
                                                                        style = MaterialTheme.typography.bodySmall,
                                                                        color = vybColors.secondaryText
                                                                    )
                                                                }
                                                            } else {
                                                                Text(
                                                                    text = entry.text,
                                                                    style = MaterialTheme.typography.bodyMedium,
                                                                    color = if (entry.isUser) Color.White else MaterialTheme.colorScheme.onSurface
                                                                )
                                                            }
                                                            Spacer(Modifier.height(4.dp))
                                                            Text(
                                                                text = entry.timestampLabel,
                                                                style = MaterialTheme.typography.labelSmall.merge(VybMonoTabularStyle),
                                                                color = if (entry.isUser) Color.White.copy(alpha = 0.78f) else vybColors.secondaryText
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                if (chatImageUri != null) {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = vybColors.nestedSurface,
                                        border = BorderStroke(0.75.dp, vybColors.hairlineBorder),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            AsyncImage(
                                                model = chatImageUri,
                                                contentDescription = "Imagen adjunta",
                                                modifier = Modifier
                                                    .size(52.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                            )
                                            Spacer(Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    "Foto o factura lista",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    "Envíala aquí o escanéala como SINPE",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = vybColors.secondaryText,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Spacer(Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(999.dp),
                                                color = vybColors.sinpeEmerald.copy(alpha = 0.14f),
                                                border = BorderStroke(0.75.dp, vybColors.sinpeEmerald.copy(alpha = 0.45f)),
                                                modifier = Modifier
                                                    .minimumInteractiveComponentSize()
                                                    .heightIn(min = 38.dp)
                                                    .clip(RoundedCornerShape(999.dp))
                                                    .clickable {
                                                        val uri = chatImageUri
                                                        if (uri != null) {
                                                            receiptUri = uri
                                                            chatImageUri = null
                                                            currentTab = AssistantToolTab.RECEIPT
                                                            assistantViewModel.scanReceipt(
                                                                context,
                                                                modelChoice,
                                                                uri,
                                                                receiptPeopleCount
                                                            )
                                                        }
                                                    }
                                                    .testTag("assistant_quick_scan_sinpe")
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                                                        contentDescription = null,
                                                        tint = vybColors.sinpeEmerald,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Text(
                                                        text = "Escanear SINPE",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = vybColors.sinpeEmerald,
                                                        maxLines = 1,
                                                        softWrap = false
                                                    )
                                                }
                                            }
                                            IconButton(
                                                onClick = { chatImageUri = null },
                                                modifier = Modifier.size(48.dp)
                                            ) {
                                                Icon(Icons.Filled.Close, contentDescription = "Quitar imagen")
                                            }
                                        }
                                    }
                                }

                                val submitChat: () -> Unit = {
                                    if (!uiState.isStreaming && (chatInput.isNotBlank() || chatImageUri != null)) {
                                        val txt = chatInput
                                        val uri = chatImageUri
                                        chatInput = ""
                                        chatImageUri = null
                                        assistantViewModel.sendChatMessage(
                                            context = context,
                                            modelChoice = modelChoice,
                                            prompt = txt,
                                            imageUri = uri,
                                            privateAreaLabel = privateAreaLabel
                                        )
                                    }
                                }

                                // Elevated floating composer bar well clear of system navigation bar
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 14.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(26.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(1.dp, vybColors.incomingBubbleBorder.copy(alpha = 0.85f)),
                                        tonalElevation = 4.dp,
                                        shadowElevation = 4.dp,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box {
                                                IconButton(
                                                    onClick = { showAttachMenu = true },
                                                    modifier = Modifier
                                                        .size(48.dp)
                                                        .testTag("assistant_attach_image")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.AddAPhoto,
                                                        contentDescription = "Tomar o subir foto de factura SINPE",
                                                        tint = VybElectricBlue
                                                    )
                                                }
                                                DropdownMenu(
                                                    expanded = showAttachMenu,
                                                    onDismissRequest = { showAttachMenu = false }
                                                ) {
                                                    DropdownMenuItem(
                                                        leadingIcon = {
                                                            Icon(
                                                                imageVector = Icons.Filled.CameraAlt,
                                                                contentDescription = null,
                                                                tint = VybElectricBlue
                                                            )
                                                        },
                                                        text = { Text("Tomar foto de factura") },
                                                        onClick = {
                                                            showAttachMenu = false
                                                            requestCameraCapture(forReceiptTab = false)
                                                        },
                                                        modifier = Modifier.testTag("assistant_camera_option")
                                                    )
                                                    DropdownMenuItem(
                                                        leadingIcon = {
                                                            Icon(
                                                                imageVector = Icons.Filled.AddPhotoAlternate,
                                                                contentDescription = null,
                                                                tint = vybColors.sinpeEmerald
                                                            )
                                                        },
                                                        text = { Text("Subir imagen o factura") },
                                                        onClick = {
                                                            showAttachMenu = false
                                                            chatImagePicker.launch(
                                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                            )
                                                        },
                                                        modifier = Modifier.testTag("assistant_gallery_option")
                                                    )
                                                }
                                            }
                                            OutlinedTextField(
                                                value = chatInput,
                                                onValueChange = { chatInput = it },
                                                placeholder = {
                                                    Text(
                                                        "Escribe tu consulta aquí...",
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                },
                                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                                keyboardActions = KeyboardActions(onSend = { submitChat() }),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = Color.Transparent,
                                                    unfocusedBorderColor = Color.Transparent
                                                ),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("assistant_chat_input"),
                                                maxLines = 3
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            val canSend = !uiState.isStreaming && (chatInput.isNotBlank() || chatImageUri != null)
                                            IconButton(
                                                onClick = submitChat,
                                                enabled = canSend,
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .graphicsLayer { alpha = if (canSend) 1f else 0.42f }
                                                    .clip(CircleShape)
                                                    .background(VybGradientBrush)
                                                    .testTag("assistant_send_button")
                                            ) {
                                                Icon(
                                                    Icons.AutoMirrored.Filled.Send,
                                                    contentDescription = "Enviar consulta",
                                                    tint = Color.White
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            }
                        }

                        AssistantToolTab.SUMMARY -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                contentAlignment = Alignment.TopCenter
                            ) {
                            Column(
                                modifier = Modifier
                                    .widthIn(max = 640.dp)
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Summarize, contentDescription = null, tint = vybColors.linkAccent)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Resumen del chat",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = if (activeChat != null) {
                                        "Obtén los puntos clave, acuerdos y pendientes de esta conversación en segundos."
                                    } else {
                                        "Abre primero un chat o grupo para resumir su conversación."
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = vybColors.secondaryText
                                )

                                VybPrimaryGradientButton(
                                    text = "Resumir conversación ahora",
                                    enabled = activeChat != null && activeChatMessages.isNotEmpty(),
                                    isLoading = uiState.isToolLoading,
                                    onClick = {
                                        assistantViewModel.summarizeMessages(modelChoice, activeChatMessages)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    testTag = "assistant_generate_summary_button"
                                )

                                uiState.summaryResult?.let { summary ->
                                    Card(
                                        shape = RoundedCornerShape(20.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        elevation = CardDefaults.cardElevation(defaultElevation = vybColors.cardElevationDp),
                                        border = BorderStroke(0.75.dp, vybColors.hairlineBorder)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            if (summary.topic.isNotBlank()) {
                                                Text(
                                                    text = "Resumen del chat · ${summary.topic}",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    color = vybColors.linkAccent,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Text(
                                                "Punto principal",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = vybColors.linkAccent
                                            )
                                            Text(summary.mainPoint, style = MaterialTheme.typography.bodyMedium)

                                            if (summary.decisions.isNotEmpty()) {
                                                Text(
                                                    "Decisiones",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = vybColors.linkAccent
                                                )
                                                summary.decisions.forEach { d ->
                                                    Text("• $d", style = MaterialTheme.typography.bodySmall)
                                                }
                                            }

                                            if (summary.pendingTasks.isNotEmpty()) {
                                                Text(
                                                    "Pendientes",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = vybColors.linkAccent
                                                )
                                                summary.pendingTasks.forEach { t ->
                                                    Text("• $t", style = MaterialTheme.typography.bodySmall)
                                                }
                                            }

                                            HorizontalDivider(thickness = 0.75.dp, color = vybColors.hairlineBorder)
                                            Text(
                                                "¿Cómo deseas usar este resumen?",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = vybColors.secondaryText
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                OutlinedButton(
                                                    onClick = {
                                                        Toast.makeText(
                                                            context,
                                                            "Resumen guardado solo para ti.",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(48.dp)
                                                ) {
                                                    Text(
                                                        text = "Solo para mí",
                                                        maxLines = 1,
                                                        softWrap = false,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                if (activeChat != null) {
                                                    Button(
                                                        onClick = {
                                                            onShareSummaryToChat(summary)
                                                            onDismiss()
                                                        },
                                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .height(48.dp)
                                                            .testTag("assistant_share_summary_button")
                                                    ) {
                                                        Text(
                                                            text = "Compartir aquí",
                                                            maxLines = 1,
                                                            softWrap = false,
                                                            overflow = TextOverflow.Ellipsis
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

                        AssistantToolTab.TONE -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                contentAlignment = Alignment.TopCenter
                            ) {
                            Column(
                                modifier = Modifier
                                    .widthIn(max = 640.dp)
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Tune, contentDescription = null, tint = vybColors.linkAccent)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Ajusta el tono de tu mensaje",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    "Escribe tu idea y elige entre tres versiones listas para enviar.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = vybColors.secondaryText
                                )
                                OutlinedTextField(
                                    value = toneDraft,
                                    onValueChange = { toneDraft = it },
                                    label = { Text("Borrador de mensaje") },
                                    placeholder = { Text("Ej. No puedo ir hoy, lo vemos mañana...") },
                                    minLines = 3,
                                    maxLines = 5,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("assistant_tone_input"),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                VybPrimaryGradientButton(
                                    text = "Crear 3 opciones",
                                    isLoading = uiState.isToolLoading,
                                    enabled = toneDraft.isNotBlank(),
                                    onClick = { assistantViewModel.generateToneVariants(modelChoice, toneDraft) },
                                    modifier = Modifier.fillMaxWidth(),
                                    testTag = "assistant_generate_tones_button"
                                )

                                uiState.toneVariants?.let { tones ->
                                    val options = listOf(
                                        "Formal" to tones.formal,
                                        "Cercano" to tones.casual,
                                        "Directo" to tones.direct
                                    )
                                    options.forEach { (label, contentText) ->
                                        Card(
                                            shape = RoundedCornerShape(18.dp),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                            elevation = CardDefaults.cardElevation(defaultElevation = vybColors.cardElevationDp),
                                            border = BorderStroke(0.75.dp, vybColors.hairlineBorder)
                                        ) {
                                            Column(modifier = Modifier.padding(14.dp)) {
                                                Text(
                                                    text = label,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = vybColors.linkAccent,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Spacer(Modifier.height(6.dp))
                                                Text(contentText, style = MaterialTheme.typography.bodyMedium)
                                                Spacer(Modifier.height(10.dp))
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    OutlinedButton(
                                                        onClick = {
                                                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                            cm.setPrimaryClip(ClipData.newPlainText("VYB Tono", contentText))
                                                            Toast.makeText(context, "Texto copiado", Toast.LENGTH_SHORT).show()
                                                        },
                                                        modifier = Modifier.height(48.dp)
                                                    ) {
                                                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                                        Spacer(Modifier.width(6.dp))
                                                        Text("Copiar")
                                                    }
                                                    if (activeChat != null) {
                                                        Button(
                                                            onClick = {
                                                                onInsertToneIntoChat(contentText)
                                                                onDismiss()
                                                            },
                                                            modifier = Modifier
                                                                .weight(1f)
                                                                .height(48.dp)
                                                        ) {
                                                            Text("Usar en chat")
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

                        AssistantToolTab.RECEIPT -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                contentAlignment = Alignment.TopCenter
                            ) {
                            Column(
                                modifier = Modifier
                                    .widthIn(max = 640.dp)
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, tint = vybColors.sinpeEmerald)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Escáner de facturas SINPE",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    "Sube la foto de una factura para leer el total en colones (₡), dividir la cuenta y cobrar por SINPE Móvil.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = vybColors.secondaryText
                                )

                                Text("Dividir entre personas:", style = MaterialTheme.typography.labelLarge)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(1, 2, 3, 4, 5, 6).forEach { count ->
                                        FilterChip(
                                            selected = receiptPeopleCount == count,
                                            onClick = { receiptPeopleCount = count },
                                            label = { Text("$count ${if (count == 1) "persona" else "personas"}") }
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { requestCameraCapture(forReceiptTab = true) },
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(50.dp)
                                            .testTag("assistant_camera_receipt_button"),
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Tomar foto", maxLines = 1, softWrap = false)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            receiptPicker.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(50.dp)
                                            .testTag("assistant_pick_receipt_button"),
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            if (receiptUri == null) "Subir foto" else "Otra foto",
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }

                                if (receiptUri != null) {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = vybColors.subtleSurface,
                                        border = BorderStroke(0.75.dp, vybColors.hairlineBorder),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            AsyncImage(
                                                model = receiptUri,
                                                contentDescription = "Factura SINPE adjunta",
                                                modifier = Modifier
                                                    .size(56.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "Factura adjunta",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = "Usa 'Tomar foto' u 'Otra foto' si deseas cambiarla.",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = vybColors.secondaryText
                                                )
                                            }
                                        }
                                    }
                                }

                                if (uiState.isToolLoading) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                                        Text("Leyendo factura...", style = MaterialTheme.typography.bodyMedium)
                                    }
                                }

                                uiState.scannedReceipt?.let { receipt ->
                                    val totalColones = customTotalOverride.toLongOrNull() ?: receipt.totalColones
                                    val perPerson = (totalColones / receiptPeopleCount.coerceAtLeast(1)).coerceAtLeast(1L)

                                    Card(
                                        shape = RoundedCornerShape(20.dp),
                                        colors = CardDefaults.cardColors(containerColor = vybColors.sinpeContainer),
                                        border = BorderStroke(1.5.dp, vybColors.sinpeEmerald)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Filled.CheckCircle,
                                                    contentDescription = null,
                                                    tint = vybColors.sinpeEmerald
                                                )
                                                Spacer(Modifier.width(6.dp))
                                                Text(
                                                    receipt.merchant,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Text(
                                                "Detalle: ${receipt.concept}",
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            OutlinedTextField(
                                                value = customTotalOverride,
                                                onValueChange = { customTotalOverride = it.filter { c -> c.isDigit() } },
                                                label = { Text("Monto total en colones (₡)") },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            Text(
                                                text = "Por persona: ${formatColones(perPerson)}",
                                                style = MaterialTheme.typography.titleMedium.merge(VybMonoTabularStyle),
                                                color = vybColors.sinpeEmerald,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (activeChat != null) {
                                                Button(
                                                    onClick = {
                                                        onSendSinpeFromReceipt(
                                                            totalColones,
                                                            receiptPeopleCount,
                                                            "${receipt.merchant} · ${receipt.concept}"
                                                        )
                                                        onDismiss()
                                                    },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = vybColors.sinpeEmerald,
                                                        contentColor = Color.White
                                                    ),
                                                    shape = RoundedCornerShape(14.dp),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(48.dp)
                                                        .testTag("assistant_send_sinpe_from_receipt")
                                                ) {
                                                    Icon(Icons.Filled.PhoneAndroid, contentDescription = null, modifier = Modifier.size(18.dp))
                                                    Spacer(Modifier.width(8.dp))
                                                    Text("Enviar cobro SINPE al chat")
                                                }
                                            } else {
                                                Text(
                                                    "Abre un chat para enviar este cobro SINPE.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = vybColors.secondaryText
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

@Composable
private fun AssistantHeroWelcomeCard(
    greetingText: String,
    onOpenSummary: () -> Unit,
    onOpenTone: () -> Unit,
    onOpenReceipt: () -> Unit,
    onSelectQuickPrompt: (String) -> Unit
) {
    val vybColors = LocalVybColors.current

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, vybColors.incomingBubbleBorder),
        tonalElevation = 3.dp,
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("assistant_welcome_hero_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(VybGradientBrush),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "¿En qué te ayudo en tus chats?",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = greetingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = vybColors.secondaryText
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistantCapabilityItem(
                    icon = Icons.Filled.Summarize,
                    title = "Resumir conversaciones",
                    subtitle = "Saca los acuerdos, puntos clave y tareas de cualquier chat.",
                    accentColor = VybElectricBlue,
                    onClick = onOpenSummary,
                    testTag = "assistant_card_summary"
                )
                AssistantCapabilityItem(
                    icon = Icons.Filled.Tune,
                    title = "Mejorar el tono de tus mensajes",
                    subtitle = "Cambia un borrador a estilo formal, cercano o directo.",
                    accentColor = VybViolet,
                    onClick = onOpenTone,
                    testTag = "assistant_card_tone"
                )
                AssistantCapabilityItem(
                    icon = Icons.AutoMirrored.Filled.ReceiptLong,
                    title = "Escanear facturas para SINPE",
                    subtitle = "Lee el monto de un recibo y divide la cuenta entre amigos.",
                    accentColor = vybColors.sinpeEmerald,
                    onClick = onOpenReceipt,
                    testTag = "assistant_card_receipt"
                )
                AssistantCapabilityItem(
                    icon = Icons.Filled.AutoAwesome,
                    title = "Ideas y respuestas al instante",
                    subtitle = "Redacta invitaciones, responde dudas o analiza una foto.",
                    accentColor = VybSkyBlue,
                    onClick = { onSelectQuickPrompt("Ayúdame a redactar un mensaje amable para coordinar una salida hoy") },
                    testTag = "assistant_card_quick_prompt"
                )
            }
        }
    }
}

@Composable
private fun AssistantCapabilityItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color,
    onClick: () -> Unit,
    testTag: String
) {
    val vybColors = LocalVybColors.current
    val scope = rememberCoroutineScope()
    val pressScale = remember { Animatable(1f) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = accentColor.copy(alpha = if (vybColors.isDark) 0.14f else 0.08f),
        border = BorderStroke(0.75.dp, accentColor.copy(alpha = 0.30f)),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = pressScale.value
                scaleY = pressScale.value
            }
            .clip(RoundedCornerShape(16.dp))
            .clickable {
                scope.launch {
                    pressScale.snapTo(0.96f)
                    pressScale.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    )
                }
                onClick()
            }
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accentColor.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = vybColors.secondaryText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = vybColors.secondaryText,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
