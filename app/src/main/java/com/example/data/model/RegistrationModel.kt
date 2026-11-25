package com.example.data.model

import com.example.data.repository.UserRepository

enum class RegistrationStep(
    val stepNumber: Int,
    val totalSteps: Int,
    val title: String,
    val subtitle: String
) {
    CREDENTIAL(
        stepNumber = 1,
        totalSteps = 4,
        title = "Acceso a VYB",
        subtitle = "Inicia sesión o regístrate con Google o SMS (+506)."
    ),
    IDENTITY(
        stepNumber = 2,
        totalSteps = 4,
        title = "Tu identidad VYB",
        subtitle = "Elige tu nombre visible y un @usuario único."
    ),
    SINPE_SETUP(
        stepNumber = 3,
        totalSteps = 4,
        title = "SINPE Móvil y perfil",
        subtitle = "Configura tu banco para cobros y detalles finales."
    ),
    PASSWORD_SETUP(
        stepNumber = 4,
        totalSteps = 4,
        title = "Tu contraseña de acceso",
        subtitle = "Define una contraseña para entrar en otros dispositivos sin SMS."
    )
}

enum class UsernameAvailability {
    IDLE,
    CHECKING,
    AVAILABLE,
    TAKEN,
    INVALID
}

data class RegistrationUiModel(
    val step: RegistrationStep = RegistrationStep.IDENTITY,
    val displayName: String = "",
    val username: String = "",
    val usernameAvailability: UsernameAvailability = UsernameAvailability.IDLE,
    val sinpeBank: String = VybConstants.SINPE_BANKS.first(),
    val sinpeVisibility: String = SinpeVisibility.CONTACTS_ONLY.key,
    val phoneDigits: String = "",
    val email: String? = null,
    val quickStatus: String = VybConstants.QUICK_STATUSES.first(),
    val bio: String = "",
    val locationLabel: String = "",
    val avatarUrl: String? = null,
    val password: String = "",
    val confirmPassword: String = "",
    val isSaving: Boolean = false,
    val errorMessage: String? = null
) {
    val cleanUsername: String
        get() = UserRepository.normalizeUsername(username)

    val normalizedPhone: String?
        get() = UserRepository.normalizeCrPhone(phoneDigits)

    val isIdentityStepValid: Boolean
        get() = displayName.trim().isNotEmpty() &&
            cleanUsername.length in 3..30 &&
            usernameAvailability != UsernameAvailability.TAKEN &&
            usernameAvailability != UsernameAvailability.INVALID

    val isSinpeStepValid: Boolean
        get() = sinpeBank.isNotBlank() &&
            (phoneDigits.isBlank() || normalizedPhone != null)

    val isPasswordStepValid: Boolean
        get() = password.length >= 8 && password == confirmPassword
}
