package com.example.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.MediaUtils
import com.example.data.model.AiModelChoice
import com.example.data.model.AssistantChatEntry
import com.example.data.model.ChatMessage
import com.example.data.model.ScannedReceipt
import com.example.data.model.StructuredChatSummary
import com.example.data.model.ToneVariants
import com.example.data.repository.AiRepository
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

private const val TAG = "AssistantViewModel"

data class AssistantUiState(
    val entries: List<AssistantChatEntry> = listOf(
        AssistantChatEntry(
            id = "welcome",
            isUser = false,
            text = "Elige una herramienta o escríbeme directo abajo.",
            timestampLabel = "Ahora"
        )
    ),
    val isStreaming: Boolean = false,
    val isToolLoading: Boolean = false,
    val activeModelNotice: String? = null,
    val lastFailedPrompt: String? = null,
    val lastFailedImageUri: Uri? = null,
    val summaryResult: StructuredChatSummary? = null,
    val toneVariants: ToneVariants? = null,
    val scannedReceipt: ScannedReceipt? = null,
    val errorMessage: String? = null
)

class AssistantViewModel(
    private val aiRepository: AiRepository = AiRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(AssistantUiState())
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    private val history = mutableListOf<Content>()
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("America/Costa_Rica")
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearTools() {
        _uiState.update {
            it.copy(summaryResult = null, toneVariants = null, scannedReceipt = null, errorMessage = null)
        }
    }

    fun retryLastMessage(context: Context, modelChoice: AiModelChoice, privateAreaLabel: String?) {
        val prompt = _uiState.value.lastFailedPrompt ?: return
        val imageUri = _uiState.value.lastFailedImageUri
        _uiState.update { it.copy(lastFailedPrompt = null, lastFailedImageUri = null, errorMessage = null) }
        sendChatMessage(
            context = context,
            modelChoice = modelChoice,
            prompt = prompt,
            imageUri = imageUri,
            privateAreaLabel = privateAreaLabel
        )
    }

    fun sendChatMessage(
        context: Context,
        modelChoice: AiModelChoice,
        prompt: String,
        imageUri: Uri? = null,
        privateAreaLabel: String? = null
    ) {
        if (_uiState.value.isStreaming) return
        val clean = prompt.trim()
        if (clean.isBlank() && imageUri == null) return

        viewModelScope.launch {
            val bitmap: Bitmap? = if (imageUri != null) {
                MediaUtils.decodeBitmapFromUri(context, imageUri, maxDim = 768)
            } else null

            val userEntry = AssistantChatEntry(
                id = UUID.randomUUID().toString(),
                isUser = true,
                text = if (bitmap != null && clean.isBlank()) "Analiza esta imagen" else clean,
                timestampLabel = timeFormat.format(Date())
            )
            val botId = UUID.randomUUID().toString()
            val botPlaceholder = AssistantChatEntry(
                id = botId,
                isUser = false,
                text = "",
                timestampLabel = timeFormat.format(Date()),
                isStreaming = true
            )

            _uiState.update {
                it.copy(
                    entries = it.entries + userEntry + botPlaceholder,
                    isStreaming = true,
                    errorMessage = null,
                    lastFailedPrompt = null,
                    lastFailedImageUri = null
                )
            }

            val effectivePrompt = clean.ifBlank {
                "Analiza esta imagen con claridad. Si es una factura o comprobante SINPE Móvil, extrae comercio o remitente, detalle y monto total en colones (₡) y sugiere cómo dividir la cuenta."
            }
            var finalBotText = ""

            try {
                aiRepository.streamAssistantResponse(
                    modelChoice = modelChoice,
                    history = history.toList(),
                    prompt = effectivePrompt,
                    attachedImage = bitmap,
                    privateAreaLabel = privateAreaLabel
                ).collect { chunk ->
                    finalBotText = chunk.accumulatedText
                    val notice = if (chunk.usedFallback) {
                        "Respondiendo con respaldo automático"
                    } else null
                    _uiState.update { state ->
                        state.copy(
                            activeModelNotice = notice,
                            entries = state.entries.map {
                                if (it.id == botId) it.copy(text = chunk.accumulatedText) else it
                            }
                        )
                    }
                }

                if (finalBotText.isBlank()) {
                    throw IllegalStateException("El asistente no generó texto. Intenta nuevamente.")
                }

                history.add(content("user") { text(effectivePrompt) })
                history.add(content("model") { text(finalBotText) })
                if (history.size > 16) {
                    repeat(history.size - 16) { history.removeAt(0) }
                }

                _uiState.update { state ->
                    state.copy(
                        isStreaming = false,
                        entries = state.entries.map {
                            if (it.id == botId) it.copy(isStreaming = false) else it
                        }
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to receive assistant response", e)
                val errorText = e.localizedMessage?.takeIf { it.isNotBlank() }
                    ?: "No se pudo obtener respuesta en este momento. Verifica tu conexión."
                _uiState.update { state ->
                    state.copy(
                        isStreaming = false,
                        errorMessage = errorText,
                        lastFailedPrompt = clean,
                        lastFailedImageUri = imageUri,
                        entries = state.entries.mapNotNull { entry ->
                            when {
                                entry.id == botId && entry.text.isBlank() -> entry.copy(
                                    text = "No pude responder en este intento ($errorText). Toca «Reintentar» arriba para volver a enviarlo.",
                                    isStreaming = false
                                )
                                entry.id == botId -> entry.copy(isStreaming = false)
                                else -> entry
                            }
                        }
                    )
                }
            }
        }
    }

    fun summarizeMessages(modelChoice: AiModelChoice, messages: List<ChatMessage>) {
        val readable = messages.filter { it.deletedForEveryone != true && it.text.isNotBlank() }
        if (readable.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "No hay mensajes suficientes en este chat para resumir.") }
            return
        }
        val transcript = readable.takeLast(60).joinToString("\n") { msg ->
            "${msg.senderName}: ${msg.text}"
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isToolLoading = true, summaryResult = null, errorMessage = null) }
            val res = aiRepository.summarizeConversation(modelChoice, transcript)
            if (res.isSuccess) {
                _uiState.update { it.copy(isToolLoading = false, summaryResult = res.getOrNull()) }
            } else {
                _uiState.update {
                    it.copy(
                        isToolLoading = false,
                        errorMessage = res.exceptionOrNull()?.localizedMessage ?: "No se pudo resumir el chat."
                    )
                }
            }
        }
    }

    fun generateToneVariants(modelChoice: AiModelChoice, draft: String) {
        if (draft.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Escribe un borrador primero para transformarlo.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isToolLoading = true, toneVariants = null, errorMessage = null) }
            val res = aiRepository.rewriteTones(modelChoice, draft.trim())
            if (res.isSuccess) {
                _uiState.update { it.copy(isToolLoading = false, toneVariants = res.getOrNull()) }
            } else {
                _uiState.update {
                    it.copy(
                        isToolLoading = false,
                        errorMessage = res.exceptionOrNull()?.localizedMessage ?: "No se pudieron generar los tonos."
                    )
                }
            }
        }
    }

    fun scanReceipt(context: Context, modelChoice: AiModelChoice, imageUri: Uri, peopleCount: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isToolLoading = true, scannedReceipt = null, errorMessage = null) }
            val bitmap = MediaUtils.decodeBitmapFromUri(context, imageUri, maxDim = 1024)
            if (bitmap == null) {
                _uiState.update { it.copy(isToolLoading = false, errorMessage = "No se pudo leer la imagen de la factura.") }
                return@launch
            }
            val res = aiRepository.scanReceiptForSinpe(modelChoice, bitmap, peopleCount)
            if (res.isSuccess) {
                _uiState.update { it.copy(isToolLoading = false, scannedReceipt = res.getOrNull()) }
            } else {
                _uiState.update {
                    it.copy(
                        isToolLoading = false,
                        errorMessage = res.exceptionOrNull()?.localizedMessage ?: "No se pudo escanear la factura."
                    )
                }
            }
        }
    }
}
