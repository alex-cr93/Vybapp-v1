package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.RegistrationStep
import com.example.data.model.SinpeVisibility
import com.example.data.model.ThemeMode
import com.example.data.model.UsernameAvailability
import com.example.data.model.VybConstants
import kotlinx.coroutines.delay
import com.example.ui.components.CostaRicaPhoneVisualTransformation
import com.example.ui.components.ThemeSelectorRow
import com.example.ui.components.VybAvatar
import com.example.ui.components.VybGoogleCircleButton
import com.example.ui.components.VybLogoBadge
import com.example.ui.components.VybPrimaryGradientButton
import com.example.ui.components.formatCostaRicaPhone
import com.example.ui.theme.LocalVybColors
import com.example.ui.theme.VybElectricBlue
import com.example.ui.theme.VybGradientBrush
import com.example.ui.theme.VybMonoTabularStyle
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.RegistrationViewModel

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun RegistrationStepHeader(
    step: RegistrationStep,
    modifier: Modifier = Modifier
) {
    val vybColors = LocalVybColors.current
    val progress by animateFloatAsState(
        targetValue = step.stepNumber.toFloat() / step.totalSteps.toFloat(),
        label = "registration_step_progress"
    )

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = vybColors.cardElevationDp,
        border = BorderStroke(0.75.dp, vybColors.hairlineBorder),
        modifier = modifier
            .fillMaxWidth()
            .testTag("registration_step_header")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = step.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Paso ${step.stepNumber} de ${step.totalSteps}",
                    style = MaterialTheme.typography.labelMedium.merge(VybMonoTabularStyle),
                    color = vybColors.linkAccent,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = step.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = vybColors.secondaryText
            )
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { progress },
                color = VybElectricBlue,
                trackColor = vybColors.hairlineBorder,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf(
                    RegistrationStep.CREDENTIAL to "Acceso",
                    RegistrationStep.IDENTITY to "Perfil",
                    RegistrationStep.SINPE_SETUP to "SINPE",
                    RegistrationStep.PASSWORD_SETUP to "Clave"
                ).forEach { (itemStep, label) ->
                    val completed = itemStep.stepNumber < step.stepNumber
                    val active = itemStep == step
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        completed || active -> VybGradientBrush
                                        else -> androidx.compose.ui.graphics.SolidColor(vybColors.hairlineBorder)
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (completed) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp)
                                )
                            } else {
                                Text(
                                    text = "${itemStep.stepNumber}",
                                    style = MaterialTheme.typography.labelSmall.merge(VybMonoTabularStyle),
                                    color = if (active) Color.White else vybColors.secondaryText,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (active || completed) MaterialTheme.colorScheme.onSurface else vybColors.secondaryText,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun AuthScreen(
    authViewModel: AuthViewModel,
    themeMode: ThemeMode,
    onSelectTheme: (ThemeMode) -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val uiState by authViewModel.uiState.collectAsStateWithLifecycle()
    val vybColors = LocalVybColors.current
    val scrollState = rememberScrollState()
    val passwordSectionRequester = remember { BringIntoViewRequester() }
    val signInButtonRequester = remember { BringIntoViewRequester() }
    val imeVisible = WindowInsets.isImeVisible

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Iniciar sesión, 1 = Crear cuenta
    var passwordFocused by remember { mutableStateOf(false) }
    var showLoginPassword by remember { mutableStateOf(false) }
    var showSmsFallback by remember { mutableStateOf(false) }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }
    var resetIdentifier by remember { mutableStateOf("") }

    LaunchedEffect(passwordFocused, imeVisible, selectedTab) {
        if (selectedTab == 0 && passwordFocused) {
            delay(140L)
            signInButtonRequester.bringIntoView()
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp, vertical = 20.dp)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ThemeSelectorRow(
                    currentMode = themeMode,
                    onSelect = onSelectTheme
                )

                Spacer(Modifier.height(28.dp))

                VybLogoBadge(size = 76.dp)
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "VYB",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Conecta · Comparte · Vibra",
                    style = MaterialTheme.typography.bodyMedium,
                    color = vybColors.secondaryText
                )

            Spacer(Modifier.height(24.dp))

            // Tabs: Iniciar sesión | Crear cuenta
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = vybColors.nestedSurface,
                border = BorderStroke(0.75.dp, vybColors.hairlineBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("Iniciar sesión", "Crear cuenta").forEachIndexed { idx, label ->
                        val active = selectedTab == idx
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (active) MaterialTheme.colorScheme.surface else Color.Transparent,
                            shadowElevation = if (active && !vybColors.isDark) 1.dp else 0.dp,
                            border = if (active && vybColors.isDark) {
                                BorderStroke(0.75.dp, vybColors.hairlineBorder)
                            } else {
                                null
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    selectedTab = idx
                                    authViewModel.clearMessages()
                                }
                                .testTag("auth_tab_$idx")
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                    color = if (active) MaterialTheme.colorScheme.onSurface else vybColors.secondaryText
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = selectedTab == 1) {
                Column {
                    Spacer(Modifier.height(14.dp))
                    RegistrationStepHeader(step = RegistrationStep.CREDENTIAL)
                }
            }

            Spacer(Modifier.height(18.dp))

            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = vybColors.cardElevationDp),
                border = BorderStroke(0.75.dp, vybColors.hairlineBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = if (selectedTab == 0) "Bienvenido de nuevo" else "Crea tu cuenta VYB",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (selectedTab == 0)
                            "Entra con tu teléfono (+506), @usuario o correo y tu contraseña, sin esperar códigos SMS."
                        else
                            "Elige Google para acceso rápido o regístrate con tu número SMS (+506); luego configurarás tu perfil, SINPE y clave.",
                        style = MaterialTheme.typography.bodySmall,
                        color = vybColors.secondaryText
                    )

                    Spacer(Modifier.height(18.dp))

                    if (selectedTab == 0) {
                        OutlinedTextField(
                            value = uiState.identifierInput,
                            onValueChange = authViewModel::updateIdentifierInput,
                            label = {
                                Text(
                                    text = "Teléfono (+506), @usuario o correo",
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            placeholder = {
                                Text(
                                    text = "Teléfono, @usuario o correo",
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Filled.Person, contentDescription = "Identificador de cuenta")
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("login_identifier_input")
                        )

                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = uiState.passwordInput,
                            onValueChange = authViewModel::updatePasswordInput,
                            label = {
                                Text(
                                    text = "Contraseña",
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            supportingText = {
                                Text(
                                    text = "Mínimo 8 caracteres",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = vybColors.secondaryText
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Filled.Lock, contentDescription = "Contraseña")
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = { showLoginPassword = !showLoginPassword }
                                ) {
                                    Icon(
                                        imageVector = if (showLoginPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = if (showLoginPassword) "Ocultar contraseña" else "Mostrar contraseña"
                                    )
                                }
                            },
                            visualTransformation = if (showLoginPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { authViewModel.signInWithPassword(context) }
                            ),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .bringIntoViewRequester(passwordSectionRequester)
                                .onFocusChanged { focusState ->
                                    passwordFocused = focusState.isFocused
                                }
                                .testTag("login_password_input")
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = {
                                    resetIdentifier = uiState.identifierInput.trim()
                                    authViewModel.clearMessages()
                                    showForgotPasswordDialog = true
                                },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("forgot_password_button")
                            ) {
                                Text("¿Olvidaste tu contraseña?")
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        VybPrimaryGradientButton(
                            text = "Iniciar sesión",
                            onClick = { authViewModel.signInWithPassword(context) },
                            enabled = uiState.identifierInput.isNotBlank() && uiState.passwordInput.length >= 8,
                            isLoading = uiState.isLoading && uiState.smsVerificationId == null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .bringIntoViewRequester(signInButtonRequester),
                            testTag = "password_sign_in_button"
                        )

                        Spacer(Modifier.height(18.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = vybColors.hairlineBorder
                            )
                            Text(
                                text = "  o entra rápido con  ",
                                style = MaterialTheme.typography.labelMedium,
                                color = vybColors.secondaryText
                            )
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = vybColors.hairlineBorder
                            )
                        }
                        Spacer(Modifier.height(14.dp))

                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            VybGoogleCircleButton(
                                onClick = { activity?.let { authViewModel.signInWithGoogle(it) } },
                                enabled = !uiState.isLoading,
                                isLoading = uiState.isLoading && uiState.smsVerificationId == null,
                                contentDescription = "Continuar con Google",
                                testTag = "google_sign_in_button"
                            )
                        }

                        Spacer(Modifier.height(8.dp))
                        TextButton(
                            onClick = { showSmsFallback = !showSmsFallback },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("toggle_sms_login_button")
                        ) {
                            Icon(Icons.Filled.Sms, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (showSmsFallback || uiState.smsVerificationId != null) {
                                    "Ocultar acceso por código SMS"
                                } else {
                                    "¿Prefieres entrar con código SMS?"
                                }
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                imageVector = if (showSmsFallback || uiState.smsVerificationId != null) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        // Create account tab: Centered Google circle button
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            VybGoogleCircleButton(
                                onClick = { activity?.let { authViewModel.signInWithGoogle(it) } },
                                enabled = !uiState.isLoading,
                                isLoading = uiState.isLoading && uiState.smsVerificationId == null,
                                contentDescription = "Crear cuenta con Google",
                                testTag = "google_sign_in_button"
                            )
                        }

                        Spacer(Modifier.height(16.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = vybColors.hairlineBorder
                            )
                            Text(
                                text = "  o por SMS (+506)  ",
                                style = MaterialTheme.typography.labelMedium,
                                color = vybColors.secondaryText
                            )
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = vybColors.hairlineBorder
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                    }

                    AnimatedVisibility(visible = selectedTab == 1 || showSmsFallback || uiState.smsVerificationId != null) {
                        Column(modifier = Modifier.padding(top = 4.dp)) {
                            if (uiState.smsVerificationId == null) {
                                val phoneMask = remember { CostaRicaPhoneVisualTransformation() }
                                val phoneIncomplete = uiState.phoneDigits.isNotEmpty() && uiState.phoneDigits.length < 8
                                OutlinedTextField(
                                    value = uiState.phoneDigits,
                                    onValueChange = authViewModel::updatePhoneDigits,
                                    label = { Text("Teléfono Costa Rica (+506)") },
                                    placeholder = {
                                        Text(
                                            text = "8888-8888",
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    prefix = {
                                        Text(
                                            "+506 ",
                                            style = VybMonoTabularStyle,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Filled.PhoneAndroid, contentDescription = "Teléfono")
                                    },
                                    visualTransformation = phoneMask,
                                    isError = phoneIncomplete,
                                    supportingText = {
                                        Text(
                                            text = if (phoneIncomplete) {
                                                "Debe tener exactamente 8 dígitos (XXXX-XXXX)"
                                            } else {
                                                "Formato Costa Rica: XXXX-XXXX (8 dígitos)"
                                            },
                                            color = if (phoneIncomplete) MaterialTheme.colorScheme.error else vybColors.secondaryText
                                        )
                                    },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    textStyle = VybMonoTabularStyle,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("phone_input")
                                )

                                Spacer(Modifier.height(12.dp))
                                OutlinedButton(
                                    onClick = { activity?.let { authViewModel.sendPhoneOtp(it) } },
                                    enabled = !uiState.isLoading && uiState.phoneDigits.length == 8,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("send_sms_button")
                                ) {
                                    Icon(Icons.Filled.Sms, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Enviar código SMS de 6 dígitos")
                                }
                            } else {
                                OutlinedTextField(
                                    value = uiState.otpCode,
                                    onValueChange = authViewModel::updateOtpCode,
                                    label = { Text("Código OTP de 6 dígitos") },
                                    placeholder = { Text("123456") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    textStyle = VybMonoTabularStyle,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("otp_input")
                                )

                                Spacer(Modifier.height(12.dp))
                                Button(
                                    onClick = authViewModel::verifyOtpCode,
                                    enabled = !uiState.isLoading && uiState.otpCode.length == 6,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("verify_otp_button")
                                ) {
                                    Text("Verificar código")
                                }

                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    TextButton(
                                        onClick = authViewModel::resetPhoneStep,
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) {
                                        Text("Cambiar número")
                                    }
                                    TextButton(
                                        enabled = uiState.resendSecondsLeft == 0 && !uiState.isLoading,
                                        onClick = { activity?.let { authViewModel.sendPhoneOtp(it) } },
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) {
                                        val label = if (uiState.resendSecondsLeft > 0) {
                                            "Reenviar (${uiState.resendSecondsLeft}s)"
                                        } else {
                                            "Reenviar SMS"
                                        }
                                        Text(label, style = VybMonoTabularStyle)
                                    }
                                }
                            }
                        }
                    }

                    uiState.infoMessage?.let { msg ->
                        Spacer(Modifier.height(12.dp))
                        Surface(
                            color = vybColors.sinpeContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = vybColors.sinpeEmerald,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = msg,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = vybColors.sinpeOnContainer
                                )
                            }
                        }
                    }

                    uiState.errorMessage?.let { err ->
                        Spacer(Modifier.height(12.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = err,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Text(
                text = "Tu cuenta y tus chats están protegidos.",
                style = MaterialTheme.typography.bodySmall,
                color = vybColors.secondaryText,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
        }
    }
    }

    if (showForgotPasswordDialog) {
        AlertDialog(
            onDismissRequest = { if (!uiState.isLoading) showForgotPasswordDialog = false },
            title = { Text("Recuperar contraseña") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Ingresa tu correo electrónico, @usuario o teléfono (+506) y te enviaremos un enlace para restablecer tu contraseña.",
                        style = MaterialTheme.typography.bodySmall,
                        color = vybColors.secondaryText
                    )
                    OutlinedTextField(
                        value = resetIdentifier,
                        onValueChange = { resetIdentifier = it },
                        label = { Text("Teléfono, @usuario o correo") },
                        placeholder = {
                            Text(
                                text = "correo@ejemplo.com o @usuario",
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("forgot_password_identifier_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        authViewModel.sendPasswordReset(context, resetIdentifier) {
                            showForgotPasswordDialog = false
                        }
                    },
                    enabled = resetIdentifier.isNotBlank() && !uiState.isLoading,
                    modifier = Modifier.testTag("forgot_password_send_button")
                ) {
                    Text(if (uiState.isLoading) "Enviando..." else "Enviar enlace")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showForgotPasswordDialog = false },
                    enabled = !uiState.isLoading
                ) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingProfileScreen(
    registrationViewModel: RegistrationViewModel,
    initialDisplayName: String,
    initialEmail: String?,
    initialPhone: String?,
    initialAvatarUrl: String?,
    isAccountVerified: Boolean,
    onSignOut: () -> Unit
) {
    val vybColors = LocalVybColors.current
    val uiState by registrationViewModel.uiState.collectAsStateWithLifecycle()
    var bankMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(initialDisplayName, initialEmail, initialPhone, initialAvatarUrl) {
        registrationViewModel.seedInitialAccount(
            displayName = initialDisplayName,
            email = initialEmail,
            phone = initialPhone,
            avatarUrl = initialAvatarUrl
        )
    }

    BackHandler(
        enabled = uiState.step == RegistrationStep.SINPE_SETUP ||
            uiState.step == RegistrationStep.PASSWORD_SETUP
    ) {
        when (uiState.step) {
            RegistrationStep.PASSWORD_SETUP -> registrationViewModel.backToSinpeStep()
            RegistrationStep.SINPE_SETUP -> registrationViewModel.backToIdentityStep()
            else -> Unit
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .imePadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            VybLogoBadge(size = 56.dp)
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Completa tu registro VYB",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(14.dp))

            RegistrationStepHeader(step = uiState.step)
            Spacer(Modifier.height(16.dp))

            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = vybColors.cardElevationDp),
                border = BorderStroke(0.75.dp, vybColors.hairlineBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                AnimatedContent(
                    targetState = uiState.step,
                    label = "registration_step_content"
                ) { currentStep ->
                    when (currentStep) {
                        RegistrationStep.CREDENTIAL,
                        RegistrationStep.IDENTITY -> {
                            Column(modifier = Modifier.padding(20.dp)) {
                                // Live VYB identity preview
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = vybColors.nestedSurface,
                                    border = BorderStroke(0.75.dp, vybColors.hairlineBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        VybAvatar(
                                            avatarUrl = uiState.avatarUrl,
                                            displayName = uiState.displayName.ifBlank { "VYB" },
                                            size = 48.dp,
                                            showGradientRing = true,
                                            isVerified = isAccountVerified
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = uiState.displayName.ifBlank { "Tu nombre en VYB" },
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "@${uiState.cleanUsername.ifBlank { "usuario" }} · ${uiState.quickStatus}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = vybColors.secondaryText,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.height(16.dp))
                                OutlinedTextField(
                                    value = uiState.displayName,
                                    onValueChange = registrationViewModel::updateDisplayName,
                                    label = { Text("Nombre visible") },
                                    leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("onboarding_name_input")
                                )

                                Spacer(Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = uiState.username,
                                    onValueChange = registrationViewModel::updateUsername,
                                    label = { Text("Usuario único") },
                                    prefix = { Text("@", fontWeight = FontWeight.Bold) },
                                    leadingIcon = { Icon(Icons.Filled.AlternateEmail, contentDescription = null) },
                                    trailingIcon = {
                                        when (uiState.usernameAvailability) {
                                            UsernameAvailability.CHECKING -> CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp
                                            )
                                            UsernameAvailability.AVAILABLE -> Icon(
                                                Icons.Filled.CheckCircle,
                                                contentDescription = "Disponible",
                                                tint = vybColors.sinpeEmerald
                                            )
                                            UsernameAvailability.TAKEN -> Icon(
                                                Icons.Filled.ErrorOutline,
                                                contentDescription = "En uso",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                            else -> Unit
                                        }
                                    },
                                    supportingText = {
                                        val supportMsg = when (uiState.usernameAvailability) {
                                            UsernameAvailability.CHECKING -> "Comprobando disponibilidad en VYB..."
                                            UsernameAvailability.AVAILABLE -> "@${uiState.cleanUsername} está disponible"
                                            UsernameAvailability.TAKEN -> "Ese @usuario ya está ocupado. Elige otro."
                                            UsernameAvailability.INVALID,
                                            UsernameAvailability.IDLE -> "Mínimo 3 caracteres (letras, números o _)"
                                        }
                                        val supportColor = when (uiState.usernameAvailability) {
                                            UsernameAvailability.AVAILABLE -> vybColors.sinpeEmerald
                                            UsernameAvailability.TAKEN -> MaterialTheme.colorScheme.error
                                            else -> vybColors.secondaryText
                                        }
                                        Text(supportMsg, color = supportColor)
                                    },
                                    isError = uiState.usernameAvailability == UsernameAvailability.TAKEN,
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("onboarding_username_input")
                                )

                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = "Estado inicial",
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Spacer(Modifier.height(6.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    VybConstants.QUICK_STATUSES.forEach { status ->
                                        FilterChip(
                                            selected = uiState.quickStatus == status,
                                            onClick = { registrationViewModel.updateQuickStatus(status) },
                                            label = { Text(status) }
                                        )
                                    }
                                }

                                Spacer(Modifier.height(18.dp))
                                VybPrimaryGradientButton(
                                    text = "Continuar a SINPE",
                                    enabled = uiState.isIdentityStepValid,
                                    onClick = registrationViewModel::advanceToSinpeStep,
                                    modifier = Modifier.fillMaxWidth(),
                                    testTag = "onboarding_next_button"
                                )
                            }
                        }

                        RegistrationStep.SINPE_SETUP -> {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedTextField(
                                        value = uiState.sinpeBank,
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Banco SINPE Móvil") },
                                        leadingIcon = { Icon(Icons.Filled.AccountBalance, contentDescription = null) },
                                        trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = "Elegir banco") },
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { bankMenuExpanded = true }
                                    )
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .clickable { bankMenuExpanded = true }
                                    )
                                    DropdownMenu(
                                        expanded = bankMenuExpanded,
                                        onDismissRequest = { bankMenuExpanded = false }
                                    ) {
                                        VybConstants.SINPE_BANKS.forEach { bank ->
                                            DropdownMenuItem(
                                                text = { Text(bank) },
                                                onClick = {
                                                    registrationViewModel.updateSinpeBank(bank)
                                                    bankMenuExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.height(12.dp))
                                val onboardingPhoneMask = remember { CostaRicaPhoneVisualTransformation() }
                                val onboardingPhoneError = uiState.phoneDigits.isNotEmpty() && uiState.phoneDigits.length < 8
                                OutlinedTextField(
                                    value = uiState.phoneDigits,
                                    onValueChange = registrationViewModel::updatePhoneDigits,
                                    label = { Text("Teléfono SINPE Móvil (+506, opcional)") },
                                    placeholder = {
                                        Text(
                                            text = "8888-8888",
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    prefix = { Text("+506 ", style = VybMonoTabularStyle) },
                                    leadingIcon = { Icon(Icons.Filled.PhoneAndroid, contentDescription = null) },
                                    visualTransformation = onboardingPhoneMask,
                                    isError = onboardingPhoneError,
                                    supportingText = {
                                        Text(
                                            text = if (onboardingPhoneError) {
                                                "Debe tener exactamente 8 dígitos (XXXX-XXXX)"
                                            } else {
                                                "Formato Costa Rica: XXXX-XXXX (8 dígitos)"
                                            },
                                            color = if (onboardingPhoneError) MaterialTheme.colorScheme.error else vybColors.secondaryText
                                        )
                                    },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    textStyle = VybMonoTabularStyle,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("onboarding_phone_input")
                                )

                                Spacer(Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = uiState.locationLabel,
                                    onValueChange = registrationViewModel::updateLocationLabel,
                                    label = { Text("Ciudad o zona (opcional)") },
                                    placeholder = { Text("Ej. San José, Heredia...") },
                                    leadingIcon = { Icon(Icons.Filled.LocationOn, contentDescription = null) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("onboarding_location_input")
                                )

                                Spacer(Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = uiState.bio,
                                    onValueChange = registrationViewModel::updateBio,
                                    label = { Text("Presentación breve (opcional)") },
                                    placeholder = { Text("Cuéntale a tu red qué estás creando...") },
                                    minLines = 2,
                                    maxLines = 3,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("onboarding_bio_input")
                                )

                                Spacer(Modifier.height(14.dp))
                                Text(
                                    text = "¿Quién puede ver tu banco y número SINPE?",
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Spacer(Modifier.height(4.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    SinpeVisibility.entries.forEach { vis ->
                                        FilterChip(
                                            selected = uiState.sinpeVisibility == vis.key,
                                            onClick = { registrationViewModel.updateSinpeVisibility(vis.key) },
                                            label = { Text(vis.shortBadge) },
                                            modifier = Modifier.testTag("onboarding_sinpe_vis_${vis.key}")
                                        )
                                    }
                                }
                                Text(
                                    text = SinpeVisibility.fromKey(uiState.sinpeVisibility).description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = vybColors.secondaryText
                                )

                                Spacer(Modifier.height(14.dp))
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = vybColors.sinpeContainer,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = "Resumen de tu registro",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = vybColors.sinpeOnContainer,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = "${uiState.displayName} (@${uiState.cleanUsername}) · ${uiState.sinpeBank}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = vybColors.sinpeOnContainer
                                        )
                                        if (uiState.normalizedPhone != null) {
                                            Text(
                                                text = "SINPE Móvil: ${formatCostaRicaPhone(uiState.normalizedPhone)}",
                                                style = MaterialTheme.typography.bodySmall.merge(VybMonoTabularStyle),
                                                color = vybColors.sinpeOnContainer
                                            )
                                        }
                                        Text(
                                            text = "Privacidad SINPE: ${SinpeVisibility.fromKey(uiState.sinpeVisibility).label}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = vybColors.sinpeOnContainer
                                        )
                                    }
                                }

                                Spacer(Modifier.height(18.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = registrationViewModel::backToIdentityStep,
                                        enabled = !uiState.isSaving,
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier
                                            .height(50.dp)
                                            .testTag("onboarding_back_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Atrás",
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text("Atrás")
                                    }
                                    VybPrimaryGradientButton(
                                        text = "Continuar a clave",
                                        enabled = uiState.isIdentityStepValid && uiState.isSinpeStepValid,
                                        onClick = registrationViewModel::advanceToPasswordStep,
                                        modifier = Modifier.weight(1f),
                                        testTag = "onboarding_to_password_button"
                                    )
                                }
                            }
                        }

                        RegistrationStep.PASSWORD_SETUP -> {
                            var showPass by remember { mutableStateOf(false) }
                            var showConfirm by remember { mutableStateOf(false) }
                            val passLongEnough = uiState.password.length >= 8
                            val passesMatch = uiState.confirmPassword.isNotEmpty() && uiState.password == uiState.confirmPassword

                            Column(modifier = Modifier.padding(20.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = vybColors.nestedSurface,
                                    border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Security,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = "Define una contraseña obligatoria. Con ella podrás iniciar sesión en cualquier dispositivo usando tu teléfono (+506), @${uiState.cleanUsername} o correo, sin depender de SMS.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = vybColors.secondaryText
                                        )
                                    }
                                }

                                Spacer(Modifier.height(14.dp))
                                OutlinedTextField(
                                    value = uiState.password,
                                    onValueChange = registrationViewModel::updatePassword,
                                    label = { Text("Contraseña de acceso") },
                                    placeholder = {
                                        Text(
                                            text = "Mínimo 8 caracteres",
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                                    trailingIcon = {
                                        IconButton(onClick = { showPass = !showPass }) {
                                            Icon(
                                                imageVector = if (showPass) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                                contentDescription = if (showPass) "Ocultar contraseña" else "Mostrar contraseña"
                                            )
                                        }
                                    },
                                    visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Password,
                                        imeAction = ImeAction.Next
                                    ),
                                    supportingText = {
                                        Text(
                                            text = if (passLongEnough) {
                                                "Longitud segura lista"
                                            } else {
                                                "Mínimo 8 caracteres (${uiState.password.length}/8)"
                                            },
                                            color = if (passLongEnough) vybColors.sinpeEmerald else vybColors.secondaryText
                                        )
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("onboarding_password_input")
                                )

                                Spacer(Modifier.height(10.dp))
                                OutlinedTextField(
                                    value = uiState.confirmPassword,
                                    onValueChange = registrationViewModel::updateConfirmPassword,
                                    label = { Text("Confirma tu contraseña") },
                                    leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                                    trailingIcon = {
                                        IconButton(onClick = { showConfirm = !showConfirm }) {
                                            Icon(
                                                imageVector = if (showConfirm) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                                contentDescription = if (showConfirm) "Ocultar confirmación" else "Mostrar confirmación"
                                            )
                                        }
                                    },
                                    visualTransformation = if (showConfirm) VisualTransformation.None else PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Password,
                                        imeAction = ImeAction.Done
                                    ),
                                    isError = uiState.confirmPassword.isNotEmpty() && !passesMatch,
                                    supportingText = {
                                        val msg = when {
                                            uiState.confirmPassword.isEmpty() -> "Repite tu contraseña para confirmarla"
                                            passesMatch -> "Las contraseñas coinciden"
                                            else -> "Las contraseñas aún no coinciden"
                                        }
                                        val col = when {
                                            passesMatch -> vybColors.sinpeEmerald
                                            uiState.confirmPassword.isNotEmpty() -> MaterialTheme.colorScheme.error
                                            else -> vybColors.secondaryText
                                        }
                                        Text(msg, color = col)
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("onboarding_confirm_password_input")
                                )

                                Spacer(Modifier.height(12.dp))
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = vybColors.sinpeContainer,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = "Accesos que quedarán activos:",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = vybColors.sinpeOnContainer,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(Modifier.height(3.dp))
                                        Text(
                                            text = "@${uiState.cleanUsername}" +
                                                (formatCostaRicaPhone(uiState.normalizedPhone)?.let { " · $it" } ?: "") +
                                                (uiState.email?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = vybColors.sinpeOnContainer
                                        )
                                    }
                                }

                                Spacer(Modifier.height(18.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = registrationViewModel::backToSinpeStep,
                                        enabled = !uiState.isSaving,
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier
                                            .height(50.dp)
                                            .testTag("onboarding_password_back_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Atrás",
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text("Atrás")
                                    }
                                    VybPrimaryGradientButton(
                                        text = "Entrar a VYB",
                                        enabled = uiState.isIdentityStepValid &&
                                            uiState.isSinpeStepValid &&
                                            uiState.isPasswordStepValid,
                                        isLoading = uiState.isSaving,
                                        onClick = { registrationViewModel.submitRegistration(isAccountVerified) },
                                        modifier = Modifier.weight(1f),
                                        testTag = "complete_onboarding_button"
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (!uiState.errorMessage.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = uiState.errorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            TextButton(
                onClick = onSignOut,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Cerrar sesión")
            }
        }
    }
}

