package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AiModelChoice
import com.example.data.model.ChatMessage
import com.example.data.model.StructuredChatSummary
import com.example.ui.components.VybPrimaryGradientButton
import com.example.ui.components.formatColones
import com.example.ui.theme.LocalVybColors
import com.example.ui.theme.VybElectricBlue
import com.example.ui.theme.VybGradientBrush
import com.example.ui.theme.VybMonoTabularStyle
import com.example.ui.viewmodel.AssistantViewModel

enum class InChatAiMode(
    val title: String,
    val subtitle: String
) {
    SUMMARY(
        title = "Resumen de este chat",
        subtitle = "Extrae puntos clave, decisiones y pendientes sin salir de la conversación"
    ),
    TONE(
        title = "Ajuste de tono",
        subtitle = "Transforma tu borrador en 3 estilos listos para enviar"
    ),
    RECEIPT(
        title = "Escáner de factura",
        subtitle = "Lee el monto total en colones y divide la cuenta por SINPE"
    ),
    QUICK_CHAT(
        title = "Asistente en este chat",
        subtitle = "Consulta rápida con inserción directa en tu conversación"
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun InChatAiSheet(
    mode: InChatAiMode,
    assistantViewModel: AssistantViewModel,
    modelChoice: AiModelChoice,
    onSelectModelChoice: (AiModelChoice) -> Unit,
    messages: List<ChatMessage>,
    initialDraft: String = "",
    initialPrompt: String? = null,
    privateAreaLabel: String? = null,
    savedSummary: StructuredChatSummary? = null,
    onSaveLocalSummary: (StructuredChatSummary) -> Unit = {},
    onShareSummary: (StructuredChatSummary) -> Unit,
    onPutToneInDraft: (String) -> Unit,
    onSendTextToChat: (String) -> Unit,
    onSendSinpeFromReceipt: (totalAmount: Long, splitCount: Int, reason: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val vybColors = LocalVybColors.current
    val uiState by assistantViewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var toneDraft by remember(initialDraft, mode) { mutableStateOf(initialDraft) }
    var receiptUri by remember(mode) { mutableStateOf<Uri?>(null) }
    var receiptPeopleCount by remember(mode) { mutableIntStateOf(2) }
    var customTotalOverride by remember(mode) { mutableStateOf("") }
    var quickPromptInput by remember(initialPrompt, mode) { mutableStateOf(initialPrompt.orEmpty()) }

    val receiptPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        receiptUri = uri
        if (uri != null) {
            assistantViewModel.scanReceipt(context, modelChoice, uri, receiptPeopleCount)
        }
    }

    LaunchedEffect(mode, initialPrompt) {
        if (mode == InChatAiMode.QUICK_CHAT && !initialPrompt.isNullOrBlank()) {
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

    val headerIcon = when (mode) {
        InChatAiMode.SUMMARY -> Icons.Filled.Summarize
        InChatAiMode.TONE -> Icons.Filled.Tune
        InChatAiMode.RECEIPT -> Icons.AutoMirrored.Filled.ReceiptLong
        InChatAiMode.QUICK_CHAT -> Icons.Filled.AutoAwesome
    }
    val headerAccent = if (mode == InChatAiMode.RECEIPT) vybColors.sinpeEmerald else VybElectricBlue

    val summarizableMessages = remember(messages) {
        messages.filter { it.deletedForEveryone != true && it.text.isNotBlank() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null
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
                    .padding(horizontal = 20.dp, vertical = 14.dp)
                    .heightIn(max = 620.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
            // Subtle top handle
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(vybColors.hairlineBorder)
            )

            // Focused tool header (zero tab strip, zero unrelated capsules)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(headerAccent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = headerIcon,
                        contentDescription = null,
                        tint = headerAccent,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = mode.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = mode.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = vybColors.secondaryText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("inchat_ai_close_button")
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Cerrar herramienta")
                }
            }

            // Compact speed mode switch (user-friendly labels only)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Modo:",
                    style = MaterialTheme.typography.labelMedium,
                    color = vybColors.secondaryText
                )
                AiModelChoice.entries.forEach { choice ->
                    val selected = modelChoice == choice
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else vybColors.nestedSurface,
                        border = BorderStroke(
                            0.75.dp,
                            if (selected) MaterialTheme.colorScheme.primary else vybColors.hairlineBorder
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(999.dp))
                            .clickable { onSelectModelChoice(choice) }
                            .testTag("inchat_ai_mode_${choice.name.lowercase()}")
                    ) {
                        Text(
                            text = choice.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) MaterialTheme.colorScheme.primary else vybColors.secondaryText,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(visible = uiState.errorMessage != null, enter = fadeIn(), exit = fadeOut()) {
                uiState.errorMessage?.let { err ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = err,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { assistantViewModel.clearError() }) {
                                Text("Cerrar")
                            }
                        }
                    }
                }
            }

            HorizontalDivider(thickness = 0.75.dp, color = vybColors.hairlineBorder)

            when (mode) {
                InChatAiMode.SUMMARY -> {
                    VybPrimaryGradientButton(
                        text = "Resumir conversación ahora",
                        enabled = summarizableMessages.isNotEmpty(),
                        isLoading = uiState.isToolLoading,
                        onClick = { assistantViewModel.summarizeMessages(modelChoice, summarizableMessages) },
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "inchat_summary_generate_button"
                    )
                    if (summarizableMessages.isEmpty()) {
                        Text(
                            text = "Envía al menos un mensaje de texto o nota en este chat para resumirlo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = vybColors.secondaryText
                        )
                    }

                    val activeSummary = uiState.summaryResult ?: savedSummary
                    activeSummary?.let { summary ->
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = vybColors.nestedSurface),
                            border = BorderStroke(0.75.dp, vybColors.hairlineBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (summary.topic.isNotBlank()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.AutoAwesome,
                                            contentDescription = null,
                                            tint = vybColors.linkAccent,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Resumen del chat · ${summary.topic}",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = vybColors.linkAccent,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Text(
                                    text = "Punto principal",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = vybColors.linkAccent,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(summary.mainPoint, style = MaterialTheme.typography.bodyMedium)

                                if (summary.decisions.isNotEmpty()) {
                                    Text(
                                        text = "Decisiones",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = vybColors.linkAccent,
                                        fontWeight = FontWeight.Bold
                                    )
                                    summary.decisions.forEach { d ->
                                        Text("• $d", style = MaterialTheme.typography.bodySmall)
                                    }
                                }

                                if (summary.pendingTasks.isNotEmpty()) {
                                    Text(
                                        text = "Pendientes",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = vybColors.linkAccent,
                                        fontWeight = FontWeight.Bold
                                    )
                                    summary.pendingTasks.forEach { t ->
                                        Text("• $t", style = MaterialTheme.typography.bodySmall)
                                    }
                                }

                                HorizontalDivider(thickness = 0.75.dp, color = vybColors.hairlineBorder)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            onSaveLocalSummary(summary)
                                            Toast.makeText(
                                                context,
                                                "Resumen guardado solo para tu vista.",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            onDismiss()
                                        },
                                        shape = RoundedCornerShape(14.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
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
                                    Button(
                                        onClick = {
                                            onShareSummary(summary)
                                            onDismiss()
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = VybElectricBlue,
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(14.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("inchat_summary_share_button")
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

                InChatAiMode.TONE -> {
                    OutlinedTextField(
                        value = toneDraft,
                        onValueChange = { toneDraft = it },
                        label = { Text("Tu borrador") },
                        placeholder = { Text("Ej. No puedo llegar hoy, lo vemos mañana...") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("inchat_tone_input"),
                        shape = RoundedCornerShape(16.dp)
                    )
                    VybPrimaryGradientButton(
                        text = "Generar 3 estilos",
                        isLoading = uiState.isToolLoading,
                        enabled = toneDraft.isNotBlank(),
                        onClick = { assistantViewModel.generateToneVariants(modelChoice, toneDraft) },
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "inchat_tone_generate_button"
                    )

                    uiState.toneVariants?.let { tones ->
                        val options = listOf(
                            "Formal" to tones.formal,
                            "Casual y cercano" to tones.casual,
                            "Directo" to tones.direct
                        )
                        options.forEach { (label, contentText) ->
                            Card(
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = vybColors.nestedSurface),
                                border = BorderStroke(0.75.dp, vybColors.hairlineBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = vybColors.linkAccent,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(contentText, style = MaterialTheme.typography.bodyMedium)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                cm.setPrimaryClip(ClipData.newPlainText("VYB Tono", contentText))
                                                Toast.makeText(context, "Texto copiado", Toast.LENGTH_SHORT).show()
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp),
                                            modifier = Modifier.height(48.dp)
                                        ) {
                                            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Copiar", maxLines = 1, softWrap = false)
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                onPutToneInDraft(contentText)
                                                onDismiss()
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                        ) {
                                            Text("Al borrador", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                                        }
                                        Button(
                                            onClick = {
                                                onSendTextToChat(contentText)
                                                onDismiss()
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = VybElectricBlue,
                                                contentColor = Color.White
                                            ),
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp),
                                            modifier = Modifier.height(48.dp)
                                        ) {
                                            Text("Enviar", maxLines = 1, softWrap = false)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                InChatAiMode.RECEIPT -> {
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

                    OutlinedButton(
                        onClick = {
                            receiptPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("inchat_receipt_pick_button")
                    ) {
                        Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (receiptUri == null) "Seleccionar foto de factura" else "Elegir otra imagen",
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    if (uiState.isToolLoading) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("Leyendo montos de la factura...", style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    uiState.scannedReceipt?.let { receipt ->
                        val totalColones = customTotalOverride.toLongOrNull() ?: receipt.totalColones
                        val perPerson = ((totalColones + receiptPeopleCount.coerceAtLeast(1) - 1) / receiptPeopleCount.coerceAtLeast(1)).coerceAtLeast(1L)

                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = vybColors.sinpeContainer),
                            border = BorderStroke(1.25.dp, vybColors.sinpeEmerald),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = vybColors.sinpeEmerald)
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = receipt.merchant,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text("Concepto: ${receipt.concept}", style = MaterialTheme.typography.bodySmall)
                                OutlinedTextField(
                                    value = customTotalOverride,
                                    onValueChange = { customTotalOverride = it.filter { c -> c.isDigit() } },
                                    label = { Text("Monto total (₡)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    text = "Cuota por persona: ${formatColones(perPerson)}",
                                    style = MaterialTheme.typography.titleMedium.merge(VybMonoTabularStyle),
                                    color = vybColors.sinpeEmerald,
                                    fontWeight = FontWeight.Bold
                                )
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
                                        .testTag("inchat_receipt_send_sinpe_button")
                                ) {
                                    Icon(Icons.Filled.PhoneAndroid, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Enviar cobro SINPE aquí", maxLines = 1, softWrap = false)
                                }
                            }
                        }
                    }
                }

                InChatAiMode.QUICK_CHAT -> {
                    val lastBotEntry = uiState.entries.lastOrNull { !it.isUser && it.id != "welcome" }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = quickPromptInput,
                            onValueChange = { quickPromptInput = it },
                            label = { Text("Tu consulta @vyb") },
                            placeholder = { Text("Ej. Redacta una invitación amable...") },
                            maxLines = 3,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                if (!uiState.isStreaming && quickPromptInput.isNotBlank()) {
                                    assistantViewModel.sendChatMessage(
                                        context = context,
                                        modelChoice = modelChoice,
                                        prompt = quickPromptInput,
                                        privateAreaLabel = privateAreaLabel
                                    )
                                }
                            },
                            enabled = !uiState.isStreaming && quickPromptInput.isNotBlank(),
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(VybGradientBrush)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Consultar", tint = Color.White)
                        }
                    }

                    lastBotEntry?.let { entry ->
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = vybColors.nestedSurface),
                            border = BorderStroke(0.75.dp, vybColors.hairlineBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow))
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (entry.isStreaming && entry.text.isBlank()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                        Text("Pensando...", style = MaterialTheme.typography.bodySmall, color = vybColors.secondaryText)
                                    }
                                } else {
                                    Text(entry.text, style = MaterialTheme.typography.bodyMedium)
                                    if (!entry.isStreaming && entry.text.isNotBlank()) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = {
                                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                    cm.setPrimaryClip(ClipData.newPlainText("Asistente VYB", entry.text))
                                                    Toast.makeText(context, "Respuesta copiada", Toast.LENGTH_SHORT).show()
                                                },
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(48.dp)
                                            ) {
                                                Text("Copiar", maxLines = 1, softWrap = false)
                                            }
                                            Button(
                                                onClick = {
                                                    onSendTextToChat(entry.text)
                                                    onDismiss()
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = VybElectricBlue,
                                                    contentColor = Color.White
                                                ),
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(48.dp)
                                            ) {
                                                Text("Enviar aquí", maxLines = 1, softWrap = false)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
        }
    }
}
