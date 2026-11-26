package com.example.data.repository

import android.graphics.Bitmap
import android.util.Log
import com.example.data.model.AiModelChoice
import com.example.data.model.ScannedReceipt
import com.example.data.model.StructuredChatSummary
import com.example.data.model.ToneVariants
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "AiRepository"
private const val MAX_HISTORY_TURNS = 8
private const val PRIMARY_FIRST_CHUNK_TIMEOUT_MS = 7_500L
private const val STREAM_MAX_TIMEOUT_MS = 25_000L
private const val TOOL_TIMEOUT_MS = 22_000L

data class AssistantStreamChunk(
    val accumulatedText: String,
    val usedFallback: Boolean
)

class AiRepository {

    private val chatModels = ConcurrentHashMap<String, GenerativeModel>()

    private val systemPromptContent by lazy {
        content {
            text(
                "Eres Asistente VYB, el asistente integrado en VYB. " +
                    "Responde siempre en español neutral, claro y cercano usando 'tú' (nunca 'vos'). " +
                    "Sé directo, práctico y conciso, y si conoces la zona privada aproximada del usuario, " +
                    "úsala de forma discreta solo cuando aporte valor a una recomendación local."
            )
        }
    }

    private fun getOrCreateChatModel(modelId: String): GenerativeModel =
        chatModels.getOrPut(modelId) {
            val isLite = modelId == AiModelChoice.LITE.modelId
            Firebase.ai.generativeModel(
                modelName = modelId,
                generationConfig = generationConfig {
                    temperature = 0.65f
                    maxOutputTokens = if (isLite) 520 else 850
                },
                systemInstruction = systemPromptContent
            )
        }

    fun streamAssistantResponse(
        modelChoice: AiModelChoice,
        history: List<Content>,
        prompt: String,
        attachedImage: Bitmap?,
        privateAreaLabel: String?
    ): Flow<AssistantStreamChunk> = flow {
        val contextPrefix = if (!privateAreaLabel.isNullOrBlank()) {
            "[Zona local: $privateAreaLabel] "
        } else ""

        val userContent = content("user") {
            if (attachedImage != null) {
                image(attachedImage)
            }
            text(contextPrefix + prompt)
        }

        val recentHistory = if (history.size > MAX_HISTORY_TURNS) {
            history.takeLast(MAX_HISTORY_TURNS)
        } else {
            history
        }

        val candidates = listOf(modelChoice.modelId, modelChoice.fallbackModelId).distinct()
        var lastError: Throwable? = null

        for ((index, modelId) in candidates.withIndex()) {
            val isFallback = index > 0
            val accumulated = StringBuilder()
            try {
                val model = getOrCreateChatModel(modelId)
                val chat = model.startChat(history = recentHistory)

                withTimeout(STREAM_MAX_TIMEOUT_MS) {
                    coroutineScope {
                        val firstChunkWatchdog = if (!isFallback && candidates.size > 1) {
                            launch {
                                delay(PRIMARY_FIRST_CHUNK_TIMEOUT_MS)
                                if (accumulated.isEmpty()) {
                                    throw FirstChunkTimeoutException()
                                }
                            }
                        } else {
                            null
                        }

                        try {
                            chat.sendMessageStream(userContent).collect { chunk ->
                                val piece = chunk.text.orEmpty()
                                if (piece.isNotEmpty()) {
                                    firstChunkWatchdog?.cancel()
                                    accumulated.append(piece)
                                    emit(
                                        AssistantStreamChunk(
                                            accumulatedText = accumulated.toString(),
                                            usedFallback = isFallback
                                        )
                                    )
                                }
                            }

                            firstChunkWatchdog?.cancel()
                            if (accumulated.isBlank()) {
                                val fallbackUnary = chat.sendMessage(userContent).text.orEmpty()
                                if (fallbackUnary.isNotBlank()) {
                                    accumulated.append(fallbackUnary)
                                    emit(
                                        AssistantStreamChunk(
                                            accumulatedText = accumulated.toString(),
                                            usedFallback = isFallback
                                        )
                                    )
                                }
                            }
                        } finally {
                            firstChunkWatchdog?.cancel()
                        }
                    }
                }

                if (accumulated.isNotBlank()) {
                    return@flow
                }
                lastError = IllegalStateException("El asistente no generó texto en este intento.")
            } catch (e: CancellationException) {
                if (e is FirstChunkTimeoutException || e is TimeoutCancellationException) {
                    Log.w(TAG, "Assistant timed out on candidate index=$index")
                    lastError = IllegalStateException("La respuesta tardó más de lo esperado.")
                } else {
                    throw e
                }
            } catch (e: Exception) {
                Log.w(TAG, "Assistant failed on candidate index=$index: ${e.message}", e)
                lastError = e
            }
        }

        throw IllegalStateException(formatAiError(lastError))
    }

    private fun formatAiError(error: Throwable?): String {
        val raw = error?.localizedMessage ?: error?.message ?: ""
        Log.e(TAG, "Formatting AI error (${error?.javaClass?.simpleName}): $raw", error)
        return when {
            raw.contains("404", ignoreCase = true) || raw.contains("not found", ignoreCase = true) ->
                "El asistente no respondió ahora. Inténtalo de nuevo en unos segundos."
            raw.contains("AppCheck", ignoreCase = true) ||
                raw.contains("app check", ignoreCase = true) ||
                raw.contains("App Check", ignoreCase = true) ||
                raw.contains("attestation", ignoreCase = true) ||
                raw.contains("403", ignoreCase = true) ->
                "No se pudo verificar la seguridad de la solicitud (App Check). Verifica el token de depuración o compila en Release."
            raw.contains("429", ignoreCase = true) || raw.contains("quota", ignoreCase = true) || raw.contains("resource exhausted", ignoreCase = true) ->
                "El asistente está recibiendo muchas consultas en este momento. Intenta en unos segundos."
            raw.contains("Unable to resolve host", ignoreCase = true) ||
                raw.contains("network", ignoreCase = true) ||
                raw.contains("timeout", ignoreCase = true) ||
                raw.contains("tardó", ignoreCase = true) ->
                "La conexión está lenta o inestable. Revisa tu red e intenta otra vez."
            raw.contains("gemini", ignoreCase = true) || raw.contains("flash", ignoreCase = true) ->
                "No se pudo completar la respuesta en este momento. Inténtalo de nuevo."
            raw.isNotBlank() -> raw
            else -> "No se pudo obtener respuesta del asistente. Inténtalo de nuevo."
        }
    }

    private fun parseAiJsonObject(rawText: String): JSONObject {
        val trimmed = rawText.trim()
        val unwrapped = if (trimmed.startsWith("```")) {
            trimmed
                .removePrefix("```json")
                .removePrefix("```JSON")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
        } else {
            trimmed
        }
        return JSONObject(unwrapped)
    }

    private suspend fun <T> withModelFallback(
        modelChoice: AiModelChoice,
        block: suspend (modelId: String) -> T
    ): Result<T> {
        val candidates = listOf(modelChoice.modelId, modelChoice.fallbackModelId).distinct()
        var lastError: Throwable? = null
        for ((index, modelId) in candidates.withIndex()) {
            try {
                val res = withTimeout(TOOL_TIMEOUT_MS) { block(modelId) }
                return Result.success(res)
            } catch (e: CancellationException) {
                if (e is TimeoutCancellationException) {
                    Log.w(TAG, "AI tool timed out on candidate index=$index")
                    lastError = IllegalStateException("La solicitud tardó más de lo esperado.")
                } else {
                    throw e
                }
            } catch (e: Exception) {
                Log.w(TAG, "AI tool failed on candidate index=$index: ${e.message}", e)
                lastError = e
            }
        }
        return Result.failure(IllegalStateException(formatAiError(lastError)))
    }

    suspend fun summarizeConversation(
        modelChoice: AiModelChoice,
        transcript: String
    ): Result<StructuredChatSummary> {
        val schema = Schema.obj(
            properties = mapOf(
                "temaPrincipal" to Schema.string("Tema principal breve de la conversación en máximo 6 palabras"),
                "loPrincipal" to Schema.string("Resumen breve de lo principal tratado en el chat"),
                "decisionesTomadas" to Schema.array(
                    items = Schema.string(),
                    description = "Lista corta de decisiones concretas acordadas"
                ),
                "tareasPendientes" to Schema.array(
                    items = Schema.string(),
                    description = "Tareas pendientes incluyendo el nombre del responsable cuando exista"
                )
            )
        )
        return withModelFallback(modelChoice) { modelId ->
            val model = Firebase.ai.generativeModel(
                modelName = modelId,
                generationConfig = generationConfig {
                    temperature = 0.35f
                    maxOutputTokens = 550
                    responseMimeType = "application/json"
                    responseSchema = schema
                }
            )
            val response = model.generateContent(
                "Resume de forma breve y clara esta conversación en español neutral (tú), extrayendo el tema principal (máximo 6 palabras), lo principal, decisiones y pendientes:\n\n$transcript"
            )
            val raw = response.text ?: throw IllegalStateException("No se recibió contenido para resumir.")
            val json = parseAiJsonObject(raw)
            val topic = json.optString("temaPrincipal", "").trim().take(80)
            val main = json.optString("loPrincipal", "Sin resumen disponible.")
            val decisionsArr = json.optJSONArray("decisionesTomadas")
            val decisions = if (decisionsArr != null) {
                (0 until decisionsArr.length()).map { decisionsArr.getString(it) }
            } else emptyList()
            val tasksArr = json.optJSONArray("tareasPendientes")
            val tasks = if (tasksArr != null) {
                (0 until tasksArr.length()).map { tasksArr.getString(it) }
            } else emptyList()

            StructuredChatSummary(
                mainPoint = main,
                decisions = decisions,
                pendingTasks = tasks,
                topic = topic
            )
        }
    }

    suspend fun rewriteTones(
        modelChoice: AiModelChoice,
        draft: String
    ): Result<ToneVariants> {
        val schema = Schema.obj(
            properties = mapOf(
                "formal" to Schema.string("Versión formal y cordial en español neutral"),
                "casual" to Schema.string("Versión casual, amable y cercana usando tú"),
                "directo" to Schema.string("Versión breve, clara y directa al punto")
            )
        )
        return withModelFallback(modelChoice) { modelId ->
            val model = Firebase.ai.generativeModel(
                modelName = modelId,
                generationConfig = generationConfig {
                    temperature = 0.45f
                    maxOutputTokens = 450
                    responseMimeType = "application/json"
                    responseSchema = schema
                }
            )
            val response = model.generateContent(
                "Reescribe este mensaje en 3 variantes naturales en español neutral:\n\n\"$draft\""
            )
            val raw = response.text ?: throw IllegalStateException("No se generaron opciones de tono.")
            val json = parseAiJsonObject(raw)
            ToneVariants(
                formal = json.getString("formal"),
                casual = json.getString("casual"),
                direct = json.getString("directo")
            )
        }
    }

    suspend fun scanReceiptForSinpe(
        modelChoice: AiModelChoice,
        receiptImage: Bitmap,
        peopleCount: Int
    ): Result<ScannedReceipt> {
        val schema = Schema.obj(
            properties = mapOf(
                "comercio" to Schema.string("Nombre del comercio o establecimiento"),
                "concepto" to Schema.string("Resumen corto del consumo o factura"),
                "totalColones" to Schema.integer("Monto total en colones costarricenses (entero)"),
                "personasSugeridas" to Schema.integer("Número sugerido de personas para dividir")
            )
        )
        return withModelFallback(modelChoice) { modelId ->
            val model = Firebase.ai.generativeModel(
                modelName = modelId,
                generationConfig = generationConfig {
                    temperature = 0.25f
                    maxOutputTokens = 320
                    responseMimeType = "application/json"
                    responseSchema = schema
                }
            )
            val response = model.generateContent(
                content {
                    image(receiptImage)
                    text(
                        "Lee este recibo y extrae comercio, concepto breve y monto total entero en colones (₡). " +
                            "Usa el total final si incluye impuesto o servicio. Personas: $peopleCount."
                    )
                }
            )
            val raw = response.text ?: throw IllegalStateException("No se pudo interpretar la imagen del recibo.")
            val json = parseAiJsonObject(raw)
            ScannedReceipt(
                merchant = json.optString("comercio", "Factura").ifBlank { "Factura" },
                concept = json.optString("concepto", "Cuenta compartida").ifBlank { "Cuenta compartida" },
                totalColones = json.optLong("totalColones", 0L).coerceAtLeast(1L),
                suggestedPeople = peopleCount.coerceIn(1, 50)
            )
        }
    }

    private class FirstChunkTimeoutException : CancellationException("First chunk timeout")
}
