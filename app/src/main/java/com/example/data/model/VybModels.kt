package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class ThemeMode(val label: String) {
    SYSTEM("Sistema"),
    LIGHT("Claro"),
    AMOLED("AMOLED")
}

enum class AiModelChoice(
    val modelId: String,
    val fallbackModelId: String,
    val label: String,
    val subtitle: String
) {
    LITE(
        modelId = "gemini-3.5-flash-lite",
        fallbackModelId = "gemini-3.8-flash",
        label = "Ligero",
        subtitle = "Respuestas rápidas para conversaciones y notas"
    ),
    STANDARD(
        modelId = "gemini-3.8-flash",
        fallbackModelId = "gemini-3.5-flash-lite",
        label = "Estándar",
        subtitle = "Respuestas más completas para resúmenes y facturas"
    )
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

object VybConstants {
    val SINPE_BANKS = listOf(
        "BAC Credomatic",
        "BCR (Banco de Costa Rica)",
        "Banco Nacional",
        "Banco Popular",
        "Scotiabank",
        "Davivienda",
        "Promerica",
        "Lafise",
        "BCT",
        "Coopenae",
        "Mucap"
    )

    val QUICK_STATUSES = listOf(
        "Disponible",
        "En foco",
        "En camino",
        "En casa",
        "En clase",
        "Libre hoy"
    )

    const val ASSISTANT_CHAT_ID = "vyb_assistant_local"
    const val MAX_BUZZ_PER_DAY = 2L
    const val MAX_GROUP_LIVE_LOCATION_SHARERS = 15
}

enum class LiveLocationScope(
    val key: String,
    val label: String,
    val shortBadge: String,
    val description: String
) {
    ALL_CHATS(
        key = "all_chats",
        label = "Todos mis chats y grupos",
        shortBadge = "Todos los chats",
        description = "Puedes compartir tu ubicación en vivo directamente en chats individuales y grupos."
    ),
    CONTACTS_ONLY(
        key = "contacts_only",
        label = "Solo contactos guardados",
        shortBadge = "Solo contactos",
        description = "Permite compartir ubicación en vivo en chats directos con tus contactos o grupos con confirmación."
    ),
    ASK_EACH_TIME(
        key = "ask_each_time",
        label = "Preguntar cada vez",
        shortBadge = "Preguntar antes",
        description = "Muestra una confirmación de privacidad y duración antes de transmitir tu GPS en vivo."
    ),
    NOBODY(
        key = "nobody",
        label = "Desactivado (Nadie)",
        shortBadge = "Oculto",
        description = "Bloquea la transmisión de ubicación en tiempo real; solo tú puedes activarla cambiando este ajuste."
    );

    companion object {
        fun fromKey(raw: String?): LiveLocationScope =
            entries.firstOrNull { it.key == raw } ?: CONTACTS_ONLY
    }
}

enum class SinpeVisibility(
    val key: String,
    val label: String,
    val shortBadge: String,
    val description: String
) {
    CONTACTS_ONLY(
        key = "contacts_only",
        label = "Solo contactos aceptados",
        shortBadge = "Solo contactos",
        description = "Tu banco y número SINPE solo son visibles para contactos guardados o solicitudes aceptadas."
    ),
    EVERYONE(
        key = "everyone",
        label = "Todos en VYB",
        shortBadge = "Público",
        description = "Cualquier usuario que te busque en el directorio podrá ver tu banco y número SINPE."
    ),
    HIDDEN(
        key = "hidden",
        label = "Oculto",
        shortBadge = "Oculto",
        description = "Nadie verá tu banco ni tu teléfono SINPE en tu perfil o directorio; solo cuando envíes un cobro."
    );

    companion object {
        fun fromKey(raw: String?): SinpeVisibility =
            entries.firstOrNull { it.key == raw } ?: CONTACTS_ONLY
    }
}

@Keep
@IgnoreExtraProperties
data class PublicUser(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val username: String = "",
    val avatarUrl: String? = null,
    val quickStatus: String = "Disponible",
    val locationLabel: String? = null,
    val bio: String? = null,
    val sinpeBank: String = "",
    val sinpeVisibility: String = SinpeVisibility.CONTACTS_ONLY.key,
    val verified: Boolean = false,
    val postsCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PrivateUser(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String? = null,
    val phone: String? = null,
    val sinpeBank: String = "BAC Credomatic",
    val sinpeVisibility: String = SinpeVisibility.CONTACTS_ONLY.key,
    val blockedUids: List<String> = emptyList(),
    val blockedPhones: List<String> = emptyList(),
    val liveLocationScope: String = LiveLocationScope.CONTACTS_ONLY.key,
    val liveLocationDurationMin: Long = 60L,
    val liveLocationShowDistance: Boolean = true,
    val readReceipts: Boolean = true,
    val showOnlineStatus: Boolean = true,
    val soundEnabled: Boolean = true,
    val callAlertsEnabled: Boolean = true,
    val sinpeAlertsEnabled: Boolean = true,
    val fcmToken: String? = null,
    val hasPasswordAuth: Boolean? = null,
    val authEmail: String? = null,
    val buzzDailyDate: String? = null,
    val buzzDailyCount: Long = 0L,
    val savedPostIds: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SinpeProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val visibility: String = SinpeVisibility.CONTACTS_ONLY.key,
    val sinpeBank: String = "",
    val sinpePhone: String? = null,
    val approvedUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class DirectoryEntry(
    @DocumentId val id: String = "",
    val uid: String = "",
    val displayName: String = "",
    val username: String = "",
    val avatarUrl: String? = null,
    val sinpeBank: String = "",
    val sinpeVisibility: String = SinpeVisibility.CONTACTS_ONLY.key,
    val phone: String? = null,
    val entryType: String = "username",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SavedContact(
    @DocumentId val id: String = "",
    val contactUid: String = "",
    val displayName: String = "",
    val username: String = "",
    val avatarUrl: String? = null,
    val sinpeBank: String = "",
    val sinpePhone: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Story(
    @DocumentId val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorUsername: String = "",
    val authorAvatarUrl: String? = null,
    val authorVerified: Boolean = false,
    val mediaType: String = "image",
    val mediaId: String? = null,
    val mediaPreviewData: String? = null,
    val caption: String? = null,
    val locationLabel: String? = null,
    val viewerUids: List<String> = emptyList(),
    val active: Boolean = true,
    val expiresAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Post(
    @DocumentId val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorUsername: String = "",
    val authorAvatarUrl: String? = null,
    val authorVerified: Boolean = false,
    val text: String = "",
    val hashtags: List<String> = emptyList(),
    val mediaType: String = "none",
    val mediaId: String? = null,
    val mediaPreviewData: String? = null,
    val mediaAspectRatio: Double? = null,
    val likedBy: List<String> = emptyList(),
    val commentsCount: Long = 0L,
    val shareCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PostComment(
    @DocumentId val id: String = "",
    val postId: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorUsername: String = "",
    val authorAvatarUrl: String? = null,
    val text: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Chat(
    @DocumentId val id: String = "",
    val participantUids: List<String> = emptyList(),
    val participantNames: Map<String, String> = emptyMap(),
    val participantAvatars: Map<String, String> = emptyMap(),
    val chatType: String = "direct",
    val title: String? = null,
    val lastMessageText: String = "",
    val lastMessageSenderId: String? = null,
    val unreadCounts: Map<String, Long> = emptyMap(),
    val lastReadAt: Map<String, Timestamp> = emptyMap(),
    val buzzCounts: Map<String, Long> = emptyMap(),
    val hiddenForUids: List<String> = emptyList(),
    val clearedBeforeAt: Map<String, Timestamp> = emptyMap(),
    val liveLocationUids: List<String> = emptyList(),
    val createdBy: String = "",
    @ServerTimestamp val lastMessageAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class LiveLocationShare(
    @DocumentId val id: String = "",
    val userId: String = "",
    val chatId: String = "",
    val participantUids: List<String> = emptyList(),
    val displayName: String = "",
    val avatarUrl: String? = null,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracyMeters: Double? = null,
    val active: Boolean = true,
    val expiresAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val chatId: String = "",
    val participantUids: List<String> = emptyList(),
    val senderId: String = "",
    val senderName: String = "",
    val messageType: String = "text",
    val text: String = "",
    val mediaId: String? = null,
    val mediaType: String? = null,
    val mediaPreviewData: String? = null,
    val mediaAspectRatio: Double? = null,
    val audioDurationSec: Long? = null,
    val sinpeAmount: Long? = null,
    val sinpePerPerson: Long? = null,
    val sinpeReason: String? = null,
    val sinpeRecipientUid: String? = null,
    val sinpeRecipientName: String? = null,
    val sinpeRecipientPhone: String? = null,
    val sinpeRecipientBank: String? = null,
    val sinpeSplitCount: Long? = null,
    val sinpeStatus: String? = null,
    val sinpePaidByUids: List<String>? = null,
    val aiSummaryTopic: String? = null,
    val aiSummaryMain: String? = null,
    val aiSummaryDecisions: List<String>? = null,
    val aiSummaryTasks: List<String>? = null,
    val documentName: String? = null,
    val documentMimeType: String? = null,
    val documentSizeBytes: Long? = null,
    val locationLat: Double? = null,
    val locationLng: Double? = null,
    val locationLabel: String? = null,
    val sharedContactUid: String? = null,
    val sharedContactName: String? = null,
    val sharedContactUsername: String? = null,
    val sharedContactAvatarUrl: String? = null,
    val pollQuestion: String? = null,
    val pollOptions: List<String>? = null,
    val pollVotes: Map<String, List<String>>? = null,
    val editedAt: Timestamp? = null,
    val deletedForEveryone: Boolean? = null,
    val bombDurationSec: Long? = null,
    val bombTriggeredAt: Timestamp? = null,
    val reactions: Map<String, List<String>> = emptyMap(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChatRequest(
    @DocumentId val id: String = "",
    val fromUid: String = "",
    val fromName: String = "",
    val fromUsername: String = "",
    val fromAvatarUrl: String? = null,
    val fromSinpeBank: String? = null,
    val toUid: String = "",
    val toName: String = "",
    val toUsername: String = "",
    val toAvatarUrl: String? = null,
    val participantUids: List<String> = emptyList(),
    val status: String = "pending",
    val note: String? = null,
    val chatId: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CallSession(
    @DocumentId val id: String = "",
    val callerId: String = "",
    val callerName: String = "",
    val callerAvatar: String? = null,
    val calleeId: String = "",
    val calleeName: String = "",
    val calleeAvatar: String? = null,
    val participantUids: List<String> = emptyList(),
    val callType: String = "audio",
    val status: String = "ringing",
    val offerSdp: String? = null,
    val answerSdp: String? = null,
    val durationSeconds: Long = 0L,
    val groupCall: Boolean = false,
    val chatId: String? = null,
    val groupTitle: String? = null,
    val activeParticipantUids: List<String> = emptyList(),
    val declinedUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class StructuredChatSummary(
    val mainPoint: String,
    val decisions: List<String>,
    val pendingTasks: List<String>,
    val topic: String = ""
)

data class ToneVariants(
    val formal: String,
    val casual: String,
    val direct: String
)

data class ScannedReceipt(
    val merchant: String,
    val concept: String,
    val totalColones: Long,
    val suggestedPeople: Int
)

data class AssistantChatEntry(
    val id: String,
    val isUser: Boolean,
    val text: String,
    val timestampLabel: String,
    val isStreaming: Boolean = false
)

data class PrivateLocationInfo(
    val latitude: Double,
    val longitude: Double,
    val areaLabel: String,
    val suggestions: List<String>
)
