package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.RegistrationStep
import com.example.data.model.RegistrationUiModel
import com.example.data.model.SinpeVisibility
import com.example.data.model.UsernameAvailability
import com.example.data.model.VybConstants
import com.example.data.repository.UserRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RegistrationViewModel(
    private val userRepository: UserRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegistrationUiModel())
    val uiState: StateFlow<RegistrationUiModel> = _uiState.asStateFlow()

    private var seeded = false
    private var availabilityJob: Job? = null

    fun seedInitialAccount(
        displayName: String,
        email: String?,
        phone: String?,
        avatarUrl: String?
    ) {
        if (seeded) return
        seeded = true
        val suggestedUsername = UserRepository.normalizeUsername(displayName).take(18)
        val phone8 = phone?.filter { it.isDigit() }?.takeLast(8) ?: ""
        _uiState.update {
            it.copy(
                step = RegistrationStep.IDENTITY,
                displayName = displayName.take(60),
                username = suggestedUsername,
                phoneDigits = phone8,
                email = email,
                avatarUrl = avatarUrl,
                sinpeBank = VybConstants.SINPE_BANKS.first(),
                quickStatus = VybConstants.QUICK_STATUSES.first()
            )
        }
        if (suggestedUsername.length >= 3) {
            scheduleUsernameCheck(suggestedUsername)
        }
    }

    fun updateDisplayName(raw: String) {
        _uiState.update { it.copy(displayName = raw.take(60), errorMessage = null) }
    }

    fun updateUsername(raw: String) {
        val clean = UserRepository.normalizeUsername(raw).take(30)
        val immediateStatus = when {
            clean.isEmpty() -> UsernameAvailability.IDLE
            clean.length < 3 -> UsernameAvailability.INVALID
            else -> UsernameAvailability.CHECKING
        }
        _uiState.update {
            it.copy(
                username = clean,
                usernameAvailability = immediateStatus,
                errorMessage = null
            )
        }
        if (clean.length >= 3) {
            scheduleUsernameCheck(clean)
        } else {
            availabilityJob?.cancel()
        }
    }

    private fun scheduleUsernameCheck(cleanUsername: String) {
        availabilityJob?.cancel()
        availabilityJob = viewModelScope.launch {
            delay(320L)
            val available = userRepository.checkUsernameAvailable(cleanUsername, currentUserId)
            _uiState.update { state ->
                if (state.username == cleanUsername) {
                    state.copy(
                        usernameAvailability = if (available) {
                            UsernameAvailability.AVAILABLE
                        } else {
                            UsernameAvailability.TAKEN
                        }
                    )
                } else {
                    state
                }
            }
        }
    }

    fun updateQuickStatus(status: String) {
        _uiState.update { it.copy(quickStatus = status.take(40), errorMessage = null) }
    }

    fun updateSinpeBank(bank: String) {
        _uiState.update { it.copy(sinpeBank = bank.take(60), errorMessage = null) }
    }

    fun updateSinpeVisibility(visibility: String) {
        _uiState.update { it.copy(sinpeVisibility = SinpeVisibility.fromKey(visibility).key, errorMessage = null) }
    }

    fun updatePhoneDigits(raw: String) {
        val digits = raw.filter { it.isDigit() }.take(8)
        _uiState.update { it.copy(phoneDigits = digits, errorMessage = null) }
    }

    fun updateLocationLabel(raw: String) {
        _uiState.update { it.copy(locationLabel = raw.take(80), errorMessage = null) }
    }

    fun updateBio(raw: String) {
        _uiState.update { it.copy(bio = raw.take(240), errorMessage = null) }
    }

    fun updatePassword(raw: String) {
        _uiState.update { it.copy(password = raw.take(64), errorMessage = null) }
    }

    fun updateConfirmPassword(raw: String) {
        _uiState.update { it.copy(confirmPassword = raw.take(64), errorMessage = null) }
    }

    fun advanceToSinpeStep() {
        val state = _uiState.value
        if (!state.isIdentityStepValid) {
            _uiState.update {
                it.copy(
                    errorMessage = when {
                        state.displayName.trim().isEmpty() -> "Ingresa tu nombre visible."
                        state.cleanUsername.length < 3 -> "El @usuario debe tener al menos 3 caracteres."
                        state.usernameAvailability == UsernameAvailability.TAKEN -> "Ese @usuario ya está en uso."
                        else -> "Revisa los datos de identidad antes de continuar."
                    }
                )
            }
            return
        }
        _uiState.update { it.copy(step = RegistrationStep.SINPE_SETUP, errorMessage = null) }
    }

    fun backToIdentityStep() {
        _uiState.update { it.copy(step = RegistrationStep.IDENTITY, errorMessage = null) }
    }

    fun advanceToPasswordStep() {
        val state = _uiState.value
        if (!state.isSinpeStepValid) {
            _uiState.update {
                it.copy(errorMessage = "El teléfono SINPE debe tener 8 dígitos de Costa Rica (+506).")
            }
            return
        }
        _uiState.update { it.copy(step = RegistrationStep.PASSWORD_SETUP, errorMessage = null) }
    }

    fun backToSinpeStep() {
        _uiState.update { it.copy(step = RegistrationStep.SINPE_SETUP, errorMessage = null) }
    }

    fun submitRegistration(isVerified: Boolean) {
        val state = _uiState.value
        if (!state.isIdentityStepValid) {
            _uiState.update { it.copy(step = RegistrationStep.IDENTITY) }
            return
        }
        if (!state.isSinpeStepValid) {
            _uiState.update {
                it.copy(
                    step = RegistrationStep.SINPE_SETUP,
                    errorMessage = "El teléfono SINPE debe tener 8 dígitos de Costa Rica (+506)."
                )
            }
            return
        }
        if (state.password.length < 8) {
            _uiState.update {
                it.copy(errorMessage = "Define una contraseña de al menos 8 caracteres para continuar.")
            }
            return
        }
        if (state.password != state.confirmPassword) {
            _uiState.update {
                it.copy(errorMessage = "Las contraseñas no coinciden.")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            val linkResult = userRepository.linkPasswordCredential(
                password = state.password,
                contactEmail = state.email
            )
            val authEmail = linkResult.getOrElse { err ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = err.localizedMessage ?: "No se pudo guardar la contraseña."
                    )
                }
                return@launch
            }

            val result = userRepository.saveFullProfile(
                displayName = state.displayName,
                rawUsername = state.cleanUsername,
                sinpeBank = state.sinpeBank,
                rawPhone = state.normalizedPhone,
                email = state.email,
                quickStatus = state.quickStatus,
                locationLabel = state.locationLabel.takeIf { it.isNotBlank() },
                bio = state.bio.takeIf { it.isNotBlank() },
                avatarUrl = state.avatarUrl,
                isVerified = isVerified,
                hasPasswordAuth = true,
                authEmail = authEmail,
                sinpeVisibility = state.sinpeVisibility
            )
            if (result.isSuccess) {
                _uiState.update { it.copy(isSaving = false) }
            } else {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = result.exceptionOrNull()?.localizedMessage
                            ?: "No se pudo completar el registro en la nube."
                    )
                }
            }
        }
    }
}
