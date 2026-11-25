package com.example.ui.viewmodel

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.repository.UserRepository
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

private const val TAG = "AuthViewModel"

data class AuthUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val identifierInput: String = "",
    val passwordInput: String = "",
    val phoneDigits: String = "",
    val smsVerificationId: String? = null,
    val otpCode: String = "",
    val resendSecondsLeft: Int = 0
)

class AuthViewModel(
    private val auth: FirebaseAuth = Firebase.auth
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private var forceResendingToken: PhoneAuthProvider.ForceResendingToken? = null

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, infoMessage = null) }
    }

    fun updateIdentifierInput(raw: String) {
        _uiState.update { it.copy(identifierInput = raw.take(120), errorMessage = null) }
    }

    fun updatePasswordInput(raw: String) {
        _uiState.update { it.copy(passwordInput = raw.take(64), errorMessage = null) }
    }

    fun updatePhoneDigits(raw: String) {
        val digits = raw.filter { it.isDigit() }.take(8)
        _uiState.update { it.copy(phoneDigits = digits, errorMessage = null) }
    }

    fun updateOtpCode(raw: String) {
        val digits = raw.filter { it.isDigit() }.take(6)
        _uiState.update { it.copy(otpCode = digits, errorMessage = null) }
    }

    fun signInWithPassword(context: Context) {
        val identifier = _uiState.value.identifierInput.trim()
        val password = _uiState.value.passwordInput
        if (identifier.isBlank()) {
            _uiState.update {
                it.copy(errorMessage = "Ingresa tu teléfono (+506), @usuario o correo.")
            }
            return
        }
        if (password.length < 8) {
            _uiState.update {
                it.copy(errorMessage = "Ingresa tu contraseña definida (mínimo 8 caracteres).")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
            try {
                val repo = UserRepository(context.applicationContext)
                val lookupResult = repo.resolveAuthEmailForIdentifier(identifier)
                val resolvedEmail = lookupResult.getOrNull()
                if (resolvedEmail.isNullOrBlank()) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "No encontramos una cuenta registrada con ese identificador y contraseña."
                        )
                    }
                    return@launch
                }

                auth.signInWithEmailAndPassword(resolvedEmail, password).await()
                _uiState.update { it.copy(isLoading = false, passwordInput = "") }
            } catch (e: FirebaseAuthInvalidCredentialsException) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Usuario o contraseña incorrectos.")
                }
            } catch (e: FirebaseAuthInvalidUserException) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "No existe una cuenta activa con esas credenciales.")
                }
            } catch (e: FirebaseAuthException) {
                val msg = if (e.errorCode == "ERROR_OPERATION_NOT_ALLOWED") {
                    "Esta opción de acceso aún no está disponible. Prueba con Google o con SMS."
                } else {
                    e.localizedMessage ?: "No se pudo iniciar sesión con contraseña."
                }
                _uiState.update { it.copy(isLoading = false, errorMessage = msg) }
            } catch (e: Exception) {
                Log.e(TAG, "Password sign-in failed", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.localizedMessage ?: "No se pudo iniciar sesión."
                    )
                }
            }
        }
    }

    fun sendPasswordReset(
        context: Context,
        rawIdentifier: String,
        onSent: () -> Unit = {}
    ) {
        val identifier = rawIdentifier.trim()
        if (identifier.isBlank()) {
            _uiState.update {
                it.copy(errorMessage = "Ingresa tu correo electrónico, @usuario o teléfono (+506).")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
            try {
                val repo = UserRepository(context.applicationContext)
                val resolvedEmail = repo.resolveAuthEmailForIdentifier(identifier).getOrNull()
                if (resolvedEmail.isNullOrBlank()) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "No encontramos un correo asociado a esa cuenta."
                        )
                    }
                    return@launch
                }
                if (resolvedEmail.endsWith("@vyb.cr", ignoreCase = true)) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Esta cuenta se registró sin correo real. Usa la opción de código SMS (+506) para entrar."
                        )
                    }
                    return@launch
                }

                auth.sendPasswordResetEmail(resolvedEmail).await()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        infoMessage = "Enviamos un enlace para restablecer tu contraseña a $resolvedEmail. Revisa tu bandeja o spam."
                    )
                }
                onSent()
            } catch (e: FirebaseAuthInvalidUserException) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "No existe una cuenta registrada con ese correo."
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Password reset failed", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.localizedMessage ?: "No se pudo enviar el correo de recuperación."
                    )
                }
            }
        }
    }

    fun attemptAutoSignIn(context: Context) {
        if (auth.currentUser != null) return
        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (_: Exception) {
            return
        }
        val credentialManager = CredentialManager.create(context)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        viewModelScope.launch {
            try {
                val cred = credentialManager.getCredential(context, request).credential
                if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                    auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                }
            } catch (_: Exception) {
                // Silent auto-sign-in is optional; interactive fallback button remains visible
            }
        }
    }

    fun signInWithGoogle(activity: Activity) {
        val clientId = try {
            activity.getString(R.string.default_web_client_id)
        } catch (_: Exception) {
            _uiState.update {
                it.copy(errorMessage = "Falta la configuración de Google Sign-In en el proyecto.")
            }
            return
        }

        val credentialManager = CredentialManager.create(activity)
        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
            try {
                val result = credentialManager.getCredential(activity, request)
                val cred = result.credential
                if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                    auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                    _uiState.update { it.copy(isLoading = false) }
                } else {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Tipo de credencial inesperado.")
                    }
                }
            } catch (e: GetCredentialCancellationException) {
                Log.w(TAG, "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
                _uiState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                Log.e(TAG, "Google Sign-In failed", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.localizedMessage ?: "No se pudo iniciar sesión con Google."
                    )
                }
            }
        }
    }

    fun sendPhoneOtp(activity: Activity) {
        val cleanDigits = UserRepository.normalizeCrPhone(_uiState.value.phoneDigits)
        if (cleanDigits == null) {
            _uiState.update {
                it.copy(errorMessage = "Ingresa un número válido de Costa Rica (+506) de 8 dígitos.")
            }
            return
        }

        val fullPhone = "+506$cleanDigits"
        _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }

        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                viewModelScope.launch {
                    try {
                        auth.signInWithCredential(credential).await()
                        _uiState.update { it.copy(isLoading = false) }
                    } catch (e: Exception) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = e.localizedMessage ?: "Error al verificar el teléfono."
                            )
                        }
                    }
                }
            }

            override fun onVerificationFailed(e: FirebaseException) {
                Log.e(TAG, "Phone verification failed", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.localizedMessage
                            ?: "No se pudo enviar el SMS en este momento. Puedes probar con Google."
                    )
                }
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                forceResendingToken = token
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        smsVerificationId = verificationId,
                        infoMessage = "Enviamos un código de 6 dígitos al $fullPhone."
                    )
                }
                startResendCountdown()
            }
        }

        val builder = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(fullPhone)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
        forceResendingToken?.let { builder.setForceResendingToken(it) }
        PhoneAuthProvider.verifyPhoneNumber(builder.build())
    }

    fun verifyOtpCode() {
        val verId = _uiState.value.smsVerificationId
        val code = _uiState.value.otpCode
        if (verId.isNullOrBlank() || code.length != 6) {
            _uiState.update { it.copy(errorMessage = "Ingresa el código OTP de 6 dígitos.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val credential = PhoneAuthProvider.getCredential(verId, code)
                auth.signInWithCredential(credential).await()
                _uiState.update { it.copy(isLoading = false, smsVerificationId = null, otpCode = "") }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.localizedMessage ?: "Código incorrecto o expirado."
                    )
                }
            }
        }
    }

    fun resetPhoneStep() {
        _uiState.update { it.copy(smsVerificationId = null, otpCode = "", errorMessage = null) }
    }

    private fun startResendCountdown() {
        viewModelScope.launch {
            for (s in 45 downTo 0) {
                _uiState.update { it.copy(resendSecondsLeft = s) }
                if (s > 0) delay(1000L)
            }
        }
    }

    fun signOut(context: Context, onComplete: () -> Unit = {}) {
        val credentialManager = CredentialManager.create(context)
        viewModelScope.launch {
            withContext(NonCancellable) {
                try {
                    credentialManager.clearCredentialState(ClearCredentialStateRequest())
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to clear credential state: ${e.message}")
                } finally {
                    auth.signOut()
                    onComplete()
                }
            }
        }
    }
}
