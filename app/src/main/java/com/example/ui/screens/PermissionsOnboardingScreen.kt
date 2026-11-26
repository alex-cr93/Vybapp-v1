package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.example.core.PreferencesStore
import com.example.core.VybPermissionGroup
import com.example.core.VybPermissionsHelper
import com.example.core.VybPermissionsSnapshot
import com.example.data.model.ThemeMode
import com.example.ui.components.VybLogoBadge
import com.example.ui.components.VybPrimaryGradientButton
import com.example.ui.theme.LocalVybColors
import com.example.ui.theme.VYBTheme
import com.example.ui.theme.VybElectricBlue
import com.example.ui.theme.VybGradientBrush
import kotlinx.coroutines.launch

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun PermissionsOnboardingScreen(
    snapshot: VybPermissionsSnapshot,
    preferencesStore: PreferencesStore,
    onRefreshPermissions: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val scope = rememberCoroutineScope()

    var activeSequentialGroup by remember { mutableStateOf<VybPermissionGroup?>(null) }
    var isSequentialFlow by remember { mutableStateOf(false) }
    var permanentlyDeniedGroupDialog by remember { mutableStateOf<VybPermissionGroup?>(null) }
    var showBatteryGuideCard by remember { mutableStateOf(false) }

    lateinit var launchGroupRequest: (VybPermissionGroup, Boolean) -> Unit

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val finishedGroup = activeSequentialGroup
        activeSequentialGroup = null
        val continueChain = isSequentialFlow
        onRefreshPermissions()

        if (finishedGroup != null) {
            val missingAfter = VybPermissionsHelper.missingPermissionsForGroup(context, finishedGroup)
            if (missingAfter.isNotEmpty()) {
                val blocked = activity != null && missingAfter.any { perm ->
                    grants[perm] != true && !ActivityCompat.shouldShowRequestPermissionRationale(activity, perm)
                }
                if (blocked) {
                    isSequentialFlow = false
                    permanentlyDeniedGroupDialog = finishedGroup
                    return@rememberLauncherForActivityResult
                }
            }
        }

        if (continueChain) {
            val next = VybPermissionsHelper.nextMissingGroup(context)
            if (next != null && next != finishedGroup) {
                launchGroupRequest(next, true)
            } else {
                isSequentialFlow = false
            }
        }
    }

    launchGroupRequest = { group, sequential ->
        if (group == VybPermissionGroup.BATTERY) {
            isSequentialFlow = false
            showBatteryGuideCard = true
            VybPermissionsHelper.openBatteryOptimizationSettings(context)
        } else {
            val missing = VybPermissionsHelper.missingPermissionsForGroup(context, group)
            if (missing.isEmpty()) {
                onRefreshPermissions()
                if (sequential) {
                    val next = VybPermissionsHelper.nextMissingGroup(context)
                    if (next != null && next != group) {
                        launchGroupRequest(next, true)
                    } else {
                        isSequentialFlow = false
                    }
                }
            } else if (snapshot.isGroupPermanentlyDenied(group)) {
                isSequentialFlow = false
                permanentlyDeniedGroupDialog = group
            } else {
                activeSequentialGroup = group
                isSequentialFlow = sequential
                scope.launch {
                    preferencesStore.recordRequestedPermissions(missing.toList())
                }
                permissionLauncher.launch(missing)
            }
        }
    }

    VYBTheme(themeMode = ThemeMode.AMOLED) {
        val vybColors = LocalVybColors.current
        val progress = (snapshot.grantedStepsCount.toFloat() / snapshot.totalStepsCount.toFloat()).coerceIn(0f, 1f)

        Surface(
            color = Color.Black,
            modifier = modifier
                .fillMaxSize()
                .testTag("permissions_onboarding_screen")
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxSize()
                ) {
                    // Header
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            VybLogoBadge(size = 44.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Permisos esenciales para VYB",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Activa estos permisos para usar chats, llamadas y alertas en tiempo real.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = vybColors.secondaryText
                                )
                            }
                            Surface(
                                shape = CircleShape,
                                color = VybElectricBlue.copy(alpha = 0.16f),
                                border = BorderStroke(0.75.dp, VybElectricBlue.copy(alpha = 0.4f))
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Security,
                                    contentDescription = "Seguridad de permisos",
                                    tint = VybElectricBlue,
                                    modifier = Modifier
                                        .padding(10.dp)
                                        .size(20.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Progreso de activación: ${snapshot.grantedStepsCount} de ${snapshot.totalStepsCount}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (snapshot.allRequiredGranted) vybColors.sinpeEmerald else Color.White
                            )
                            Text(
                                text = "${(progress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = VybElectricBlue
                            )
                        }
                        LinearProgressIndicator(
                            progress = { progress },
                            color = if (snapshot.allRequiredGranted) vybColors.sinpeEmerald else VybElectricBlue,
                            trackColor = Color(0xFF141822),
                            strokeCap = StrokeCap.Round,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(999.dp))
                        )
                    }

                    HorizontalDivider(thickness = 0.75.dp, color = vybColors.hairlineBorder)

                    // Permission Cards List
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        VybPermissionGroup.entries.forEach { group ->
                            val satisfied = snapshot.isGroupSatisfied(group)
                            val permanentlyDenied = snapshot.isGroupPermanentlyDenied(group)
                            PermissionStepCard(
                                group = group,
                                icon = iconForGroup(group),
                                isSatisfied = satisfied,
                                isPermanentlyDenied = permanentlyDenied,
                                onActionClick = { launchGroupRequest(group, false) }
                            )
                        }

                        if (showBatteryGuideCard || (snapshot.allRuntimeGranted && snapshot.batteryRequired && !snapshot.batteryUnrestricted)) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFF0A1220),
                                border = BorderStroke(0.75.dp, VybElectricBlue.copy(alpha = 0.45f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Settings,
                                        contentDescription = null,
                                        tint = VybElectricBlue,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "En Ajustes de Android, busca VYB y selecciona Batería > Sin restricciones. Al volver a la app se validará automáticamente.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFD6E4FF)
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(thickness = 0.75.dp, color = vybColors.hairlineBorder)

                    // Sticky Footer CTA
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black)
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val nextMissing = remember(snapshot) { VybPermissionsHelper.nextMissingGroup(context) }
                        val ctaLabel = when {
                            snapshot.allRequiredGranted -> "Entrar a VYB"
                            nextMissing == VybPermissionGroup.BATTERY -> "Configurar batería y continuar"
                            else -> "Conceder permisos y continuar"
                        }
                        VybPrimaryGradientButton(
                            text = ctaLabel,
                            onClick = {
                                val next = VybPermissionsHelper.nextMissingGroup(context)
                                if (next == null) {
                                    onRefreshPermissions()
                                } else {
                                    launchGroupRequest(next, true)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "permissions_continue_button"
                        )
                        TextButton(
                            onClick = onSignOut,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("permissions_signout_button")
                        ) {
                            Text(
                                text = "Cerrar sesión",
                                color = vybColors.secondaryText
                            )
                        }
                    }
                }
            }
        }

        permanentlyDeniedGroupDialog?.let { blockedGroup ->
            AlertDialog(
                onDismissRequest = { permanentlyDeniedGroupDialog = null },
                containerColor = Color(0xFF0C0F16),
                titleContentColor = Color.White,
                textContentColor = Color(0xFFCBD5E1),
                title = { Text("Permiso requerido en Ajustes") },
                text = {
                    Text(
                        "El permiso de \"${blockedGroup.title}\" fue bloqueado en Android. " +
                            "Abre Ajustes de VYB > Permisos para activarlo y poder continuar a tus chats."
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            permanentlyDeniedGroupDialog = null
                            VybPermissionsHelper.openAppDetailsSettings(context)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = VybElectricBlue),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("permissions_open_settings_button")
                    ) {
                        Text("Ir a Ajustes", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { permanentlyDeniedGroupDialog = null },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Cerrar")
                    }
                }
            )
        }
    }
}

private fun iconForGroup(group: VybPermissionGroup): ImageVector = when (group) {
    VybPermissionGroup.CONTACTS -> Icons.Filled.Contacts
    VybPermissionGroup.CALLS -> Icons.Filled.Call
    VybPermissionGroup.NOTIFICATIONS -> Icons.Filled.NotificationsActive
    VybPermissionGroup.LOCATION -> Icons.Filled.LocationOn
    VybPermissionGroup.BATTERY -> Icons.Filled.BatteryChargingFull
}

@Composable
private fun PermissionStepCard(
    group: VybPermissionGroup,
    icon: ImageVector,
    isSatisfied: Boolean,
    isPermanentlyDenied: Boolean,
    onActionClick: () -> Unit
) {
    val vybColors = LocalVybColors.current
    val borderColor by animateColorAsState(
        targetValue = when {
            isSatisfied -> vybColors.sinpeEmerald.copy(alpha = 0.55f)
            isPermanentlyDenied -> MaterialTheme.colorScheme.error.copy(alpha = 0.65f)
            else -> Color(0xFF1E2433)
        },
        label = "perm_card_border"
    )
    val badgeBg = when {
        isSatisfied -> vybColors.sinpeEmerald.copy(alpha = 0.16f)
        isPermanentlyDenied -> MaterialTheme.colorScheme.error.copy(alpha = 0.16f)
        else -> VybElectricBlue.copy(alpha = 0.14f)
    }
    val badgeFg = when {
        isSatisfied -> vybColors.sinpeEmerald
        isPermanentlyDenied -> MaterialTheme.colorScheme.error
        else -> VybElectricBlue
    }
    val statusText = when {
        isSatisfied -> "Activo"
        isPermanentlyDenied -> "En Ajustes"
        group == VybPermissionGroup.BATTERY -> "Ajuste"
        else -> "Pendiente"
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF090B10),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = !isSatisfied, onClick = onActionClick)
            .testTag("permission_card_${group.testTagSuffix}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (isSatisfied) vybColors.sinpeEmerald.copy(alpha = 0.18f) else Color(0xFF121722)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = group.title,
                    tint = if (isSatisfied) vybColors.sinpeEmerald else Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = group.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = vybColors.secondaryText
                )
            }
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = badgeBg,
                border = BorderStroke(0.75.dp, badgeFg.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isSatisfied) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Concedido",
                            tint = badgeFg,
                            modifier = Modifier.size(14.dp)
                        )
                    } else if (isPermanentlyDenied) {
                        Icon(
                            imageVector = Icons.Filled.WarningAmber,
                            contentDescription = "Bloqueado",
                            tint = badgeFg,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = badgeFg
                    )
                }
            }
        }
    }
}
