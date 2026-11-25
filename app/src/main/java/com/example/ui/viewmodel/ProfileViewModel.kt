package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.LocationHelper
import com.example.core.MediaUtils
import com.example.core.PreferencesStore
import com.example.data.model.AiModelChoice
import com.example.data.model.LiveLocationScope
import com.example.data.model.PrivateLocationInfo
import com.example.data.model.PrivateUser
import com.example.data.model.PublicUser
import com.example.data.model.SinpeVisibility
import com.example.data.model.ThemeMode
import com.example.data.model.UiState
import com.example.data.repository.UserRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "ProfileViewModel"

class ProfileViewModel(
    private val userRepository: UserRepository,
    private val preferencesStore: PreferencesStore,
    private val currentUserId: String
) : ViewModel() {

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _privateLocation = MutableStateFlow<PrivateLocationInfo?>(null)
    val privateLocation: StateFlow<PrivateLocationInfo?> = _privateLocation.asStateFlow()

    private val _blockedProfiles = MutableStateFlow<List<PublicUser>>(emptyList())
    val blockedProfiles: StateFlow<List<PublicUser>> = _blockedProfiles.asStateFlow()

    private val _retryTrigger = MutableStateFlow(0)
    private val _optimisticPhonePatch = MutableStateFlow<Pair<Boolean, String?>?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val publicProfileState: StateFlow<UiState<PublicUser?>> =
        _retryTrigger
            .flatMapLatest {
                userRepository.observePublicProfile(currentUserId)
                    .map<PublicUser?, UiState<PublicUser?>> { UiState.Success(it) }
                    .catch { error ->
                        val fsError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (fsError == null && error is CancellationException) throw error
                        val root = fsError ?: error
                        Log.w(TAG, "Error observing public profile", root)
                        emit(UiState.Error(root.message ?: "Error al cargar el perfil."))
                    }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    fun retryProfileLoad() {
        _retryTrigger.value = _retryTrigger.value + 1
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val privateProfileState: StateFlow<PrivateUser?> =
        combine(
            _retryTrigger.flatMapLatest {
                userRepository.observePrivateProfile(currentUserId)
                    .catch { error ->
                        val fsError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (fsError == null && error is CancellationException) throw error
                        emit(null)
                    }
            },
            _optimisticPhonePatch
        ) { remotePrivate, phonePatch ->
            if (phonePatch != null && phonePatch.first) {
                (remotePrivate ?: PrivateUser(userId = currentUserId)).copy(phone = phonePatch.second)
            } else {
                remotePrivate
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val themeMode: StateFlow<ThemeMode> = preferencesStore.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), ThemeMode.SYSTEM)

    val aiModelChoice: StateFlow<AiModelChoice> = preferencesStore.aiModel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), AiModelChoice.LITE)

    val privateLocationEnabled: StateFlow<Boolean> = preferencesStore.privateLocationEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)

    val blockedPhones: StateFlow<List<String>> = privateProfileState
        .map { it?.blockedPhones.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    init {
        viewModelScope.launch {
            privateProfileState.collect { priv ->
                val uids = priv?.blockedUids ?: emptyList()
                if (uids.isEmpty()) {
                    _blockedProfiles.value = emptyList()
                } else {
                    _blockedProfiles.value = uids.map { uid ->
                        userRepository.getPublicUser(uid)
                            ?: PublicUser(
                                id = uid,
                                userId = uid,
                                displayName = "Usuario VYB",
                                username = uid.take(10)
                            )
                    }
                }
            }
        }
    }

    fun clearMessage() {
        _actionMessage.value = null
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferencesStore.setThemeMode(mode) }
    }

    fun setAiModelChoice(choice: AiModelChoice) {
        viewModelScope.launch { preferencesStore.setAiModel(choice) }
    }

    fun refreshPrivateLocation(context: Context) {
        viewModelScope.launch {
            preferencesStore.setPrivateLocationEnabled(true)
            val res = LocationHelper(context).fetchPrivateLocation()
            if (res.isSuccess) {
                _privateLocation.value = res.getOrNull()
            } else {
                _actionMessage.value = res.exceptionOrNull()?.localizedMessage
                    ?: "No se pudo obtener la ubicación privada."
            }
        }
    }

    fun disablePrivateLocation() {
        viewModelScope.launch {
            preferencesStore.setPrivateLocationEnabled(false)
            _privateLocation.value = null
        }
    }

    fun saveProfile(
        displayName: String,
        username: String,
        sinpeBank: String,
        phone: String?,
        email: String?,
        quickStatus: String,
        locationLabel: String?,
        bio: String?,
        avatarUrl: String?,
        isVerified: Boolean,
        sinpeVisibility: String? = null,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            val res = userRepository.saveFullProfile(
                displayName = displayName,
                rawUsername = username,
                sinpeBank = sinpeBank,
                rawPhone = phone,
                email = email,
                quickStatus = quickStatus,
                locationLabel = locationLabel,
                bio = bio,
                avatarUrl = avatarUrl,
                isVerified = isVerified,
                sinpeVisibility = sinpeVisibility
            )
            _isSaving.value = false
            if (res.isSuccess) {
                val formattedSavedPhone = UserRepository.formatCrPhone(phone)
                _optimisticPhonePatch.value = true to formattedSavedPhone
                _actionMessage.value = "Perfil actualizado correctamente."
                onSuccess()
            } else {
                _actionMessage.value = res.exceptionOrNull()?.localizedMessage ?: "No se pudo guardar el perfil."
            }
        }
    }

    fun uploadAvatar(context: Context, uri: Uri, currentPublic: PublicUser, currentPrivate: PrivateUser?, isVerified: Boolean) {
        viewModelScope.launch {
            _isSaving.value = true
            val dataUri = MediaUtils.compressAvatarDataUri(context, uri)
            if (dataUri == null) {
                _isSaving.value = false
                _actionMessage.value = "No se pudo procesar la foto de perfil."
                return@launch
            }
            val resolvedBank = currentPrivate?.sinpeBank?.takeIf { it.isNotBlank() }
                ?: currentPublic.sinpeBank.takeIf { it.isNotBlank() }
                ?: "BAC Credomatic"
            val resolvedVisibility = currentPrivate?.sinpeVisibility
                ?: currentPublic.sinpeVisibility
            val res = userRepository.saveFullProfile(
                displayName = currentPublic.displayName,
                rawUsername = currentPublic.username,
                sinpeBank = resolvedBank,
                rawPhone = currentPrivate?.phone,
                email = currentPrivate?.email,
                quickStatus = currentPublic.quickStatus,
                locationLabel = currentPublic.locationLabel,
                bio = currentPublic.bio,
                avatarUrl = dataUri,
                isVerified = isVerified,
                sinpeVisibility = resolvedVisibility
            )
            _isSaving.value = false
            if (res.isFailure) {
                _actionMessage.value = res.exceptionOrNull()?.localizedMessage ?: "No se pudo subir la foto."
            }
        }
    }

    fun updateQuickStatus(status: String) {
        viewModelScope.launch {
            userRepository.updateQuickStatus(status)
        }
    }

    fun updateSinpeVisibility(visibility: String) {
        viewModelScope.launch {
            val res = userRepository.updateSinpeVisibility(visibility)
            if (res.isSuccess) {
                val label = SinpeVisibility.fromKey(visibility).label
                _actionMessage.value = "Privacidad SINPE: $label."
            } else {
                _actionMessage.value = res.exceptionOrNull()?.localizedMessage
                    ?: "No se pudo actualizar la privacidad SINPE."
            }
        }
    }

    fun updatePrivacySettings(
        readReceipts: Boolean,
        showOnlineStatus: Boolean,
        soundEnabled: Boolean,
        callAlertsEnabled: Boolean,
        sinpeAlertsEnabled: Boolean
    ) {
        viewModelScope.launch {
            userRepository.updatePrivacySettings(
                readReceipts = readReceipts,
                showOnlineStatus = showOnlineStatus,
                soundEnabled = soundEnabled,
                callAlertsEnabled = callAlertsEnabled,
                sinpeAlertsEnabled = sinpeAlertsEnabled
            )
        }
    }

    fun updateLiveLocationPrivacy(
        scope: LiveLocationScope,
        durationMin: Int,
        showDistance: Boolean
    ) {
        viewModelScope.launch {
            val res = userRepository.updateLiveLocationPrivacy(
                scope = scope,
                durationMin = durationMin,
                showDistance = showDistance
            )
            if (res.isFailure) {
                _actionMessage.value = res.exceptionOrNull()?.localizedMessage
                    ?: "No se pudo guardar la privacidad de ubicación."
            }
        }
    }

    fun unblockUser(uid: String) {
        viewModelScope.launch {
            val res = userRepository.toggleBlockUser(uid, block = false)
            if (res.isSuccess) {
                _actionMessage.value = "Contacto desbloqueado."
            } else {
                _actionMessage.value = res.exceptionOrNull()?.localizedMessage ?: "No se pudo desbloquear."
            }
        }
    }

    fun blockUser(uid: String, displayName: String? = null) {
        viewModelScope.launch {
            val res = userRepository.toggleBlockUser(uid, block = true)
            if (res.isSuccess) {
                _actionMessage.value = if (!displayName.isNullOrBlank()) {
                    "$displayName bloqueado."
                } else {
                    "Contacto bloqueado."
                }
            } else {
                _actionMessage.value = res.exceptionOrNull()?.localizedMessage ?: "No se pudo bloquear."
            }
        }
    }

    fun unblockPhone(phone: String) {
        viewModelScope.launch {
            val res = userRepository.toggleBlockPhone(phone, block = false)
            if (res.isSuccess) {
                _actionMessage.value = "Número desbloqueado."
            } else {
                _actionMessage.value = res.exceptionOrNull()?.localizedMessage ?: "No se pudo desbloquear el número."
            }
        }
    }

    fun blockByIdentifier(
        query: String,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            val res = userRepository.blockContactOrIdentifier(query)
            if (res.isSuccess) {
                val msg = res.getOrThrow()
                _actionMessage.value = msg
                onResult(true, msg)
            } else {
                val err = res.exceptionOrNull()?.localizedMessage ?: "No se pudo bloquear."
                _actionMessage.value = err
                onResult(false, err)
            }
        }
    }

    fun updateAccountPassword(
        newPassword: String,
        currentPublic: PublicUser,
        currentPrivate: PrivateUser?,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            val res = userRepository.updatePasswordAndSyncAliases(
                newPassword = newPassword,
                username = currentPublic.username,
                phone = currentPrivate?.phone,
                contactEmail = currentPrivate?.email,
                existingAuthEmail = currentPrivate?.authEmail
            )
            _isSaving.value = false
            if (res.isSuccess) {
                _actionMessage.value = "Contraseña guardada. Ya puedes usarla en cualquier dispositivo."
                onSuccess()
            } else {
                _actionMessage.value = res.exceptionOrNull()?.localizedMessage
                    ?: "No se pudo actualizar la contraseña."
            }
        }
    }
}
