package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.core.NotificationHelper
import com.example.data.model.AiModelChoice
import com.example.data.model.LiveLocationScope
import com.example.data.model.PrivateLocationInfo
import com.example.data.model.PrivateUser
import com.example.data.model.PublicUser
import com.example.data.model.SinpeVisibility
import com.example.data.model.ThemeMode
import com.example.data.model.VybConstants
import com.example.data.repository.UserRepository
import com.example.ui.components.ThemeSelectorRow
import com.example.ui.components.VybAvatar
import com.example.ui.theme.LocalVybColors
import com.google.firebase.Firebase
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.auth
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.filled.ShareLocation
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField

enum class ProfileSettingsPage(val title: String) {
    LIST("Configuración"),
    APPEARANCE("Apariencia"),
    ASSISTANT("Asistente VYB"),
    PRIVACY_ALERTS("Privacidad y Alertas"),
    SINPE_PRIVACY("Privacidad de datos SINPE"),
    BLOCKED("Contactos Bloqueados"),
    ACCOUNT("Cuenta y Sesión")
}

@Composable
fun ProfileSettingsNavigator(
    currentPage: ProfileSettingsPage,
    onSelectPage: (ProfileSettingsPage) -> Unit,
    onCloseSettings: () -> Unit,
    publicUser: PublicUser,
    privateUser: PrivateUser?,
    formattedSinpePhone: String?,
    effectiveSinpeVisibility: SinpeVisibility,
    themeMode: ThemeMode,
    onSelectThemeMode: (ThemeMode) -> Unit,
    privateLocationEnabled: Boolean,
    privateLocation: PrivateLocationInfo?,
    onEnablePrivateLocation: () -> Unit,
    onDisablePrivateLocation: () -> Unit,
    aiModel: AiModelChoice,
    onSelectAiModel: (AiModelChoice) -> Unit,
    onUpdatePrivacySettings: (
        readReceipts: Boolean,
        showOnline: Boolean,
        soundEnabled: Boolean,
        callAlerts: Boolean,
        sinpeAlerts: Boolean
    ) -> Unit,
    onUpdateLiveLocationPrivacy: (
        scope: LiveLocationScope,
        durationMin: Int,
        showDistance: Boolean
    ) -> Unit,
    onUpdateSinpeVisibility: (String) -> Unit,
    blockedProfiles: List<PublicUser>,
    blockedPhones: List<String>,
    onUnblockUser: (String) -> Unit,
    onUnblockPhone: (String) -> Unit,
    onBlockByIdentifier: (String, (Boolean, String) -> Unit) -> Unit,
    onOpenPasswordModal: () -> Unit,
    onSignOut: () -> Unit,
    isExpanded: Boolean
) {
    val vybColors = LocalVybColors.current

    BackHandler {
        if (currentPage == ProfileSettingsPage.LIST) {
            onCloseSettings()
        } else {
            onSelectPage(ProfileSettingsPage.LIST)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Settings Top App Bar
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Row(
                    modifier = Modifier
                        .widthIn(max = if (isExpanded) 860.dp else 640.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (currentPage == ProfileSettingsPage.LIST) {
                                onCloseSettings()
                            } else {
                                onSelectPage(ProfileSettingsPage.LIST)
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = currentPage.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        HorizontalDivider(thickness = 0.75.dp, color = vybColors.hairlineBorder)

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            AnimatedContent(
                targetState = currentPage,
                transitionSpec = {
                    val forward = targetState != ProfileSettingsPage.LIST && initialState == ProfileSettingsPage.LIST
                    if (forward) {
                        (slideInHorizontally(tween(200)) { it / 4 } + fadeIn(tween(200))) togetherWith
                            (slideOutHorizontally(tween(170)) { -it / 6 } + fadeOut(tween(150)))
                    } else {
                        (slideInHorizontally(tween(200)) { -it / 4 } + fadeIn(tween(200))) togetherWith
                            (slideOutHorizontally(tween(170)) { it / 6 } + fadeOut(tween(150)))
                    }
                },
                label = "profile_settings_submenu_nav"
            ) { page ->
                LazyColumn(
                    modifier = Modifier
                        .widthIn(max = if (isExpanded) 860.dp else 640.dp)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    when (page) {
                        ProfileSettingsPage.LIST -> {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(22.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    tonalElevation = vybColors.cardElevationDp,
                                    shadowElevation = vybColors.cardElevationDp,
                                    border = BorderStroke(0.75.dp, vybColors.hairlineBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        SettingsMenuRow(
                                            icon = Icons.Filled.Palette,
                                            iconTint = MaterialTheme.colorScheme.primary,
                                            title = "Apariencia",
                                            subtitle = themeMode.label,
                                            testTag = "settings_row_appearance",
                                            onClick = { onSelectPage(ProfileSettingsPage.APPEARANCE) }
                                        )
                                        SettingsRowDivider()
                                        SettingsMenuRow(
                                            icon = Icons.Filled.AutoAwesome,
                                            iconTint = Color(0xFF7C3AED),
                                            title = "Asistente VYB",
                                            subtitle = aiModel.label,
                                            testTag = "settings_row_assistant",
                                            onClick = { onSelectPage(ProfileSettingsPage.ASSISTANT) }
                                        )
                                        SettingsRowDivider()
                                        SettingsMenuRow(
                                            icon = Icons.Filled.Notifications,
                                            iconTint = vybColors.linkAccent,
                                            title = "Privacidad y Alertas",
                                            subtitle = "Ubicación privada, en vivo y notificaciones",
                                            testTag = "settings_row_privacy_alerts",
                                            onClick = { onSelectPage(ProfileSettingsPage.PRIVACY_ALERTS) }
                                        )
                                        SettingsRowDivider()
                                        SettingsMenuRow(
                                            icon = Icons.Filled.PhoneAndroid,
                                            iconTint = vybColors.sinpeEmerald,
                                            title = "Privacidad de datos SINPE",
                                            subtitle = effectiveSinpeVisibility.label,
                                            testTag = "settings_row_sinpe_privacy",
                                            onClick = { onSelectPage(ProfileSettingsPage.SINPE_PRIVACY) }
                                        )
                                        SettingsRowDivider()
                                        val totalBlockedCount = blockedProfiles.size + blockedPhones.size
                                        SettingsMenuRow(
                                            icon = Icons.Filled.Block,
                                            iconTint = MaterialTheme.colorScheme.error,
                                            title = "Contactos Bloqueados",
                                            subtitle = if (totalBlockedCount == 0) {
                                                "Ninguno bloqueado"
                                            } else {
                                                "$totalBlockedCount bloqueados"
                                            },
                                            testTag = "settings_row_blocked",
                                            onClick = { onSelectPage(ProfileSettingsPage.BLOCKED) }
                                        )
                                        SettingsRowDivider()
                                        SettingsMenuRow(
                                            icon = Icons.AutoMirrored.Filled.ExitToApp,
                                            iconTint = vybColors.secondaryText,
                                            title = "Cuenta y Sesión",
                                            subtitle = "Contraseña de acceso y cerrar sesión",
                                            testTag = "settings_row_account",
                                            onClick = { onSelectPage(ProfileSettingsPage.ACCOUNT) }
                                        )
                                    }
                                }
                            }
                        }

                        ProfileSettingsPage.APPEARANCE -> {
                            item {
                                SettingsSubmenuCard(
                                    title = "Apariencia",
                                    icon = Icons.Filled.Palette
                                ) {
                                    ThemeSelectorRow(
                                        currentMode = themeMode,
                                        onSelect = onSelectThemeMode
                                    )
                                }
                            }
                        }

                        ProfileSettingsPage.ASSISTANT -> {
                            item {
                                SettingsSubmenuCard(
                                    title = "Asistente VYB",
                                    icon = Icons.Filled.AutoAwesome
                                ) {
                                    AiModelChoice.entries.forEach { choice ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable { onSelectAiModel(choice) }
                                                .padding(vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(
                                                selected = aiModel == choice,
                                                onClick = { onSelectAiModel(choice) }
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(choice.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                                Text(choice.subtitle, style = MaterialTheme.typography.bodySmall, color = vybColors.secondaryText)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        ProfileSettingsPage.PRIVACY_ALERTS -> {
                            item {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    PrivacyAndAlertsSubmenuContent(
                                        privateUser = privateUser,
                                        onUpdatePrivacySettings = onUpdatePrivacySettings
                                    )
                                    LiveLocationPrivacySubmenuContent(
                                        privateUser = privateUser,
                                        onUpdateLiveLocationPrivacy = onUpdateLiveLocationPrivacy
                                    )
                                    PrivateLocationSubmenuContent(
                                        privateLocationEnabled = privateLocationEnabled,
                                        privateLocation = privateLocation,
                                        onEnablePrivateLocation = onEnablePrivateLocation,
                                        onDisablePrivateLocation = onDisablePrivateLocation
                                    )
                                }
                            }
                        }

                        ProfileSettingsPage.SINPE_PRIVACY -> {
                            item {
                                SinpePrivacySubmenuContent(
                                    effectiveSinpeVisibility = effectiveSinpeVisibility,
                                    onUpdateSinpeVisibility = onUpdateSinpeVisibility
                                )
                            }
                        }

                        ProfileSettingsPage.BLOCKED -> {
                            item {
                                BlockedContactsSubmenuContent(
                                    blockedProfiles = blockedProfiles,
                                    blockedPhones = blockedPhones,
                                    onUnblockUser = onUnblockUser,
                                    onUnblockPhone = onUnblockPhone,
                                    onBlockByIdentifier = onBlockByIdentifier
                                )
                            }
                        }

                        ProfileSettingsPage.ACCOUNT -> {
                            item {
                                AccountAndSessionSubmenuContent(
                                    publicUser = publicUser,
                                    privateUser = privateUser,
                                    formattedSinpePhone = formattedSinpePhone,
                                    onOpenPasswordModal = onOpenPasswordModal,
                                    onSignOut = onSignOut
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsMenuRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit
) {
    val vybColors = LocalVybColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = vybColors.secondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Abrir $title",
            tint = vybColors.secondaryText,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun SettingsRowDivider() {
    val vybColors = LocalVybColors.current
    HorizontalDivider(
        thickness = 0.75.dp,
        color = vybColors.hairlineBorder,
        modifier = Modifier.padding(start = 68.dp, end = 16.dp)
    )
}

@Composable
private fun PrivateLocationSubmenuContent(
    privateLocationEnabled: Boolean,
    privateLocation: PrivateLocationInfo?,
    onEnablePrivateLocation: () -> Unit,
    onDisablePrivateLocation: () -> Unit
) {
    val context = LocalContext.current
    val vybColors = LocalVybColors.current
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { map ->
        val granted = map[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            map[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            onEnablePrivateLocation()
        } else {
            Toast.makeText(context, "Permiso de ubicación denegado.", Toast.LENGTH_SHORT).show()
        }
    }

    SettingsSubmenuCard(
        title = "Ubicación Privada",
        icon = Icons.Filled.LocationOn
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Mostrar mi zona en el mapa", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Solo tú ves tu ubicación; nunca se comparte con otros.",
                    style = MaterialTheme.typography.bodySmall,
                    color = vybColors.secondaryText
                )
            }
            Switch(
                checked = privateLocationEnabled,
                onCheckedChange = { checked ->
                    if (checked) {
                        val fineOk = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        val coarseOk = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        if (fineOk || coarseOk) {
                            onEnablePrivateLocation()
                        } else {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    } else {
                        onDisablePrivateLocation()
                    }
                },
                modifier = Modifier.testTag("profile_private_location_switch")
            )
        }
        if (privateLocationEnabled) {
            Spacer(Modifier.height(10.dp))
            VybMapsPanel(locationInfo = privateLocation)
        }
    }
}

@Composable
private fun PrivacyAndAlertsSubmenuContent(
    privateUser: PrivateUser?,
    onUpdatePrivacySettings: (Boolean, Boolean, Boolean, Boolean, Boolean) -> Unit
) {
    val context = LocalContext.current
    val vybColors = LocalVybColors.current
    val readReceipts = privateUser?.readReceipts ?: true
    val showOnline = privateUser?.showOnlineStatus ?: true
    val soundEnabled = privateUser?.soundEnabled ?: true
    val callAlerts = privateUser?.callAlertsEnabled ?: true
    val sinpeAlerts = privateUser?.sinpeAlertsEnabled ?: true

    val lifecycleOwner = LocalLifecycleOwner.current
    var hasNotifPerm by remember { mutableStateOf(NotificationHelper.hasNotificationPermission(context)) }
    var isIgnoringBatteryOpt by remember { mutableStateOf(NotificationHelper.isIgnoringBatteryOptimizations(context)) }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotifPerm = NotificationHelper.hasNotificationPermission(context)
                isIgnoringBatteryOpt = NotificationHelper.isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val pushPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifPerm = granted || NotificationHelper.hasNotificationPermission(context)
    }

    SettingsSubmenuCard(
        title = "Privacidad y Alertas",
        icon = Icons.Filled.Notifications
    ) {
        if (!hasNotifPerm) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = vybColors.nestedSurface,
                border = BorderStroke(0.75.dp, vybColors.hairlineBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_push_status_card")
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Notificaciones push",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                text = "Sin permiso",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    Text(
                        text = "Actívalas para enterarte al instante cuando recibas mensajes, llamadas o cobros.",
                        style = MaterialTheme.typography.bodySmall,
                        color = vybColors.secondaryText
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        OutlinedButton(
                            onClick = {
                                pushPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("profile_enable_push_button")
                        ) {
                            Text("Permitir notificaciones push")
                        }
                    }
                }
            }
        }
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = vybColors.nestedSurface,
            border = BorderStroke(0.75.dp, vybColors.hairlineBorder),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("profile_battery_opt_card")
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Segundo plano y batería",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (isIgnoringBatteryOpt) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        }
                    ) {
                        Text(
                            text = if (isIgnoringBatteryOpt) "Sin restricciones" else "Optimizada",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isIgnoringBatteryOpt) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                Text(
                    text = if (isIgnoringBatteryOpt) {
                        "VYB puede recibir notificaciones de mensajes normales aunque la app esté cerrada."
                    } else {
                        "Permite batería sin restricciones para que los mensajes normales lleguen al instante con la app cerrada."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = vybColors.secondaryText
                )
                if (!isIgnoringBatteryOpt) {
                    OutlinedButton(
                        onClick = { NotificationHelper.openBatteryOptimizationSettings(context) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("profile_battery_opt_button")
                    ) {
                        Text("Excluir de ahorro de batería")
                    }
                }
            }
        }
        SettingSwitchRow(
            label = "Confirmaciones de lectura",
            checked = readReceipts,
            testTag = "privacy_switch_read_receipts"
        ) { v ->
            onUpdatePrivacySettings(v, showOnline, soundEnabled, callAlerts, sinpeAlerts)
        }
        SettingSwitchRow(
            label = "Mostrar estado en línea",
            checked = showOnline,
            testTag = "privacy_switch_online_status"
        ) { v ->
            onUpdatePrivacySettings(readReceipts, v, soundEnabled, callAlerts, sinpeAlerts)
        }
        SettingSwitchRow(
            label = "Notificaciones y sonido de mensajes",
            checked = soundEnabled,
            testTag = "privacy_switch_msg_sound"
        ) { v ->
            if (v && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pushPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            onUpdatePrivacySettings(readReceipts, showOnline, v, callAlerts, sinpeAlerts)
        }
        SettingSwitchRow(
            label = "Alertas de llamadas",
            checked = callAlerts,
            testTag = "privacy_switch_call_alerts"
        ) { v ->
            if (v && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pushPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            onUpdatePrivacySettings(readReceipts, showOnline, soundEnabled, v, sinpeAlerts)
        }
        SettingSwitchRow(
            label = "Alertas de cobros SINPE",
            checked = sinpeAlerts,
            testTag = "privacy_switch_sinpe_alerts"
        ) { v ->
            if (v && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pushPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            onUpdatePrivacySettings(readReceipts, showOnline, soundEnabled, callAlerts, v)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LiveLocationPrivacySubmenuContent(
    privateUser: PrivateUser?,
    onUpdateLiveLocationPrivacy: (LiveLocationScope, Int, Boolean) -> Unit
) {
    val vybColors = LocalVybColors.current
    val currentScope = LiveLocationScope.fromKey(privateUser?.liveLocationScope)
    val currentDuration = (privateUser?.liveLocationDurationMin ?: 60L).toInt().coerceIn(5, 480)
    val showDistance = privateUser?.liveLocationShowDistance ?: true

    SettingsSubmenuCard(
        title = "Ubicación en tiempo real (Chats y Grupos)",
        icon = Icons.Filled.ShareLocation
    ) {
        Text(
            text = "Controla quién y durante cuánto tiempo puede ver tu ubicación en vivo dentro de Google Maps. Máximo ${VybConstants.MAX_GROUP_LIVE_LOCATION_SHARERS} personas activas por grupo.",
            style = MaterialTheme.typography.bodySmall,
            color = vybColors.secondaryText
        )
        LiveLocationScope.entries.forEach { scope ->
            val selected = currentScope == scope
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (selected) vybColors.sinpeEmerald.copy(alpha = 0.12f) else vybColors.nestedSurface,
                border = BorderStroke(
                    if (selected) 1.dp else 0.75.dp,
                    if (selected) vybColors.sinpeEmerald else vybColors.hairlineBorder
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable {
                        onUpdateLiveLocationPrivacy(scope, currentDuration, showDistance)
                    }
                    .testTag("live_loc_scope_${scope.key}")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selected,
                        onClick = {
                            onUpdateLiveLocationPrivacy(scope, currentDuration, showDistance)
                        }
                    )
                    Spacer(Modifier.width(6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = scope.label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
                        )
                        Text(
                            text = scope.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = vybColors.secondaryText
                        )
                    }
                }
            }
        }

        Text(
            text = "Duración predeterminada al compartir",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(15 to "15 min", 60 to "1 hora", 480 to "8 horas").forEach { (mins, label) ->
                FilterChip(
                    selected = currentDuration == mins,
                    onClick = {
                        onUpdateLiveLocationPrivacy(currentScope, mins, showDistance)
                    },
                    label = { Text(label) },
                    modifier = Modifier.testTag("live_loc_dur_$mins")
                )
            }
        }

        SettingSwitchRow(
            label = "Mostrar distancia y cercanía entre participantes",
            checked = showDistance,
            testTag = "live_loc_distance_switch"
        ) { checked ->
            onUpdateLiveLocationPrivacy(currentScope, currentDuration, checked)
        }
    }
}

@Composable
private fun BlockedContactsSubmenuContent(
    blockedProfiles: List<PublicUser>,
    blockedPhones: List<String>,
    onUnblockUser: (String) -> Unit,
    onUnblockPhone: (String) -> Unit,
    onBlockByIdentifier: (String, (Boolean, String) -> Unit) -> Unit
) {
    val vybColors = LocalVybColors.current
    var query by remember { mutableStateOf("") }
    var isBusy by remember { mutableStateOf(false) }

    SettingsSubmenuCard(
        title = "Contactos Bloqueados",
        icon = Icons.Filled.Block
    ) {
        Text(
            text = "Los bloqueos hechos desde chats o perfiles se sincronizan aquí. Puedes bloquear un @usuario o un número de 8 dígitos (+506).",
            style = MaterialTheme.typography.bodySmall,
            color = vybColors.secondaryText
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("@usuario o 8888-7777") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("blocked_add_input")
            )
            Button(
                enabled = query.trim().isNotBlank() && !isBusy,
                onClick = {
                    isBusy = true
                    onBlockByIdentifier(query.trim()) { ok, _ ->
                        isBusy = false
                        if (ok) query = ""
                    }
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .heightIn(min = 52.dp)
                    .testTag("blocked_add_submit")
            ) {
                Text("Bloquear")
            }
        }

        if (blockedProfiles.isEmpty() && blockedPhones.isEmpty()) {
            Text(
                "No tienes contactos ni números bloqueados actualmente.",
                style = MaterialTheme.typography.bodySmall,
                color = vybColors.secondaryText
            )
        } else {
            blockedProfiles.forEach { blocked ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    VybAvatar(avatarUrl = blocked.avatarUrl, displayName = blocked.displayName, size = 36.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(blocked.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("@${blocked.username}", style = MaterialTheme.typography.labelSmall, color = vybColors.secondaryText)
                    }
                    TextButton(
                        onClick = { onUnblockUser(blocked.userId) },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("unblock_user_${blocked.userId}")
                    ) {
                        Text("Desbloquear")
                    }
                }
            }
            blockedPhones.forEach { digits ->
                val formatted = UserRepository.formatCrPhone(digits) ?: "+$digits"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PhoneAndroid,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(formatted, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("Número bloqueado", style = MaterialTheme.typography.labelSmall, color = vybColors.secondaryText)
                    }
                    TextButton(
                        onClick = { onUnblockPhone(digits) },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("unblock_phone_$digits")
                    ) {
                        Text("Desbloquear")
                    }
                }
            }
        }
    }
}

@Composable
private fun SinpePrivacySubmenuContent(
    effectiveSinpeVisibility: SinpeVisibility,
    onUpdateSinpeVisibility: (String) -> Unit
) {
    val vybColors = LocalVybColors.current
    SettingsSubmenuCard(
        title = "Privacidad de datos SINPE",
        icon = Icons.Filled.Lock
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("profile_sinpe_privacy_section"),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Decide quién puede ver tu número SINPE y tu banco desde el directorio o tus publicaciones.",
                style = MaterialTheme.typography.bodySmall,
                color = vybColors.secondaryText
            )
            SinpeVisibility.entries.forEach { vis ->
                val selected = effectiveSinpeVisibility == vis
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (selected) {
                        vybColors.sinpeEmerald.copy(alpha = 0.12f)
                    } else {
                        vybColors.nestedSurface
                    },
                    border = BorderStroke(
                        if (selected) 1.dp else 0.75.dp,
                        if (selected) vybColors.sinpeEmerald else vybColors.hairlineBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onUpdateSinpeVisibility(vis.key) }
                        .testTag("profile_sinpe_vis_${vis.key}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = { onUpdateSinpeVisibility(vis.key) }
                        )
                        Spacer(Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = vis.label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
                            )
                            Text(
                                text = vis.description,
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

@Composable
private fun AccountAndSessionSubmenuContent(
    publicUser: PublicUser,
    privateUser: PrivateUser?,
    formattedSinpePhone: String?,
    onOpenPasswordModal: () -> Unit,
    onSignOut: () -> Unit
) {
    val vybColors = LocalVybColors.current
    val hasPasswordAuth = remember(privateUser?.hasPasswordAuth) {
        privateUser?.hasPasswordAuth == true ||
            runCatching {
                Firebase.auth.currentUser?.providerData?.any {
                    it.providerId == EmailAuthProvider.PROVIDER_ID
                } == true
            }.getOrDefault(false)
    }
    SettingsSubmenuCard(
        title = "Cuenta y Sesión",
        icon = Icons.AutoMirrored.Filled.ExitToApp
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = vybColors.nestedSurface,
            border = BorderStroke(
                0.75.dp,
                if (hasPasswordAuth) vybColors.sinpeEmerald.copy(alpha = 0.45f) else vybColors.hairlineBorder
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Acceso en otros dispositivos",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (hasPasswordAuth) {
                            vybColors.sinpeEmerald.copy(alpha = 0.14f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    ) {
                        Text(
                            text = if (hasPasswordAuth) "Contraseña activa" else "Sin contraseña",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (hasPasswordAuth) vybColors.sinpeEmerald else vybColors.secondaryText,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                Text(
                    text = if (hasPasswordAuth) {
                        val phoneLabel = formattedSinpePhone ?: "teléfono +506"
                        "Puedes iniciar sesión con tu @${publicUser.username}, $phoneLabel o correo usando tu contraseña."
                    } else {
                        "Define una contraseña para entrar en otro teléfono sin depender de códigos SMS o correo."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = vybColors.secondaryText
                )
                OutlinedButton(
                    onClick = onOpenPasswordModal,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("profile_password_button")
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (hasPasswordAuth) "Cambiar contraseña de acceso" else "Definir contraseña de acceso")
                }
            }
        }

        OutlinedButton(
            onClick = onSignOut,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("profile_signout_button")
        ) {
            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Cerrar sesión en este dispositivo")
        }
    }
}

@Composable
private fun SettingsSubmenuCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val vybColors = LocalVybColors.current
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = vybColors.cardElevationDp),
        border = BorderStroke(0.75.dp, vybColors.hairlineBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            HorizontalDivider(thickness = 0.75.dp, color = vybColors.hairlineBorder)
            content()
        }
    }
}

@Composable
private fun SettingSwitchRow(
    label: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
