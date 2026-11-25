package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.BuildConfig
import com.example.core.LiveLocationCluster
import com.example.core.LiveLocationClusterer
import com.example.core.LocationHelper
import com.example.core.MapsConfigHelper
import com.example.data.model.Chat
import com.example.data.model.LiveLocationScope
import com.example.data.model.LiveLocationShare
import com.example.data.model.PrivateUser
import com.example.data.model.SavedContact
import com.example.data.model.VybConstants
import com.example.ui.components.VybAvatar
import com.example.ui.theme.LocalVybColors
import com.example.ui.theme.VybGradientBrush
import com.example.ui.theme.VybMonoTabularStyle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun LiveLocationActiveBanner(
    activeShares: List<LiveLocationShare>,
    currentUid: String,
    isGroup: Boolean,
    onOpenMap: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (activeShares.isEmpty()) return
    val vybColors = LocalVybColors.current
    val amISharing = activeShares.any { it.userId == currentUid }
    val countLabel = if (isGroup) {
        "${activeShares.size}/${VybConstants.MAX_GROUP_LIVE_LOCATION_SHARERS} compartiendo ubicación en vivo"
    } else if (amISharing && activeShares.size == 1) {
        "Estás compartiendo tu ubicación en vivo"
    } else {
        "${activeShares.first().displayName} comparte su ubicación en vivo"
    }

    Surface(
        onClick = onOpenMap,
        color = vybColors.sinpeEmerald.copy(alpha = 0.12f),
        border = BorderStroke(0.75.dp, vybColors.sinpeEmerald.copy(alpha = 0.35f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("chat_live_location_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(vybColors.sinpeEmerald.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.GpsFixed,
                    contentDescription = "Ubicación en vivo",
                    tint = vybColors.sinpeEmerald,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = countLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = vybColors.sinpeEmerald,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Toca para ver el mapa en tiempo real y distancias",
                    style = MaterialTheme.typography.labelSmall,
                    color = vybColors.secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = vybColors.sinpeEmerald
            ) {
                Text(
                    text = "Ver mapa",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LiveLocationChatMapSheet(
    chat: Chat,
    chatTitle: String,
    currentUid: String,
    privateUser: PrivateUser?,
    savedContacts: List<SavedContact>,
    activeShares: List<LiveLocationShare>,
    isStarting: Boolean,
    onStartSharing: (durationMin: Int) -> Unit,
    onStopSharing: () -> Unit,
    onSendStaticLocation: () -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }

    val context = LocalContext.current
    val vybColors = LocalVybColors.current
    val scope = rememberCoroutineScope()
    val hasValidMapsKey = remember(context) { MapsConfigHelper.hasValidMapsApiKey(context) }

    val privacyScope = LiveLocationScope.fromKey(privateUser?.liveLocationScope)
    val defaultDuration = (privateUser?.liveLocationDurationMin ?: 60L).toInt().coerceIn(5, 480)
    val showDistance = privateUser?.liveLocationShowDistance ?: true

    var selectedDurationMin by remember(defaultDuration) { mutableIntStateOf(defaultDuration) }
    var focusedShare by remember { mutableStateOf<LiveLocationShare?>(null) }
    var showPrivacyConfirmDialog by remember { mutableStateOf(false) }

    val amISharing = activeShares.any { it.userId == currentUid }
    val myShare = activeShares.firstOrNull { it.userId == currentUid }
    val groupAtCapacity = !amISharing &&
        chat.chatType == "group" &&
        activeShares.size >= VybConstants.MAX_GROUP_LIVE_LOCATION_SHARERS

    val isContactsOnlyAllowed = remember(chat, savedContacts, currentUid) {
        val contactUids = savedContacts.map { it.contactUid }.toSet()
        val others = chat.participantUids.filter { it != currentUid }
        others.isNotEmpty() && others.all { it in contactUids }
    }

    val initialCenter = remember(activeShares) {
        val first = activeShares.firstOrNull()
        if (first != null) LatLng(first.latitude, first.longitude)
        else LatLng(9.9281, -84.0907) // San José, Costa Rica default
    }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(initialCenter, if (activeShares.size <= 1) 14.5f else 13f)
    }

    LaunchedEffect(activeShares.size, hasValidMapsKey) {
        if (!hasValidMapsKey) return@LaunchedEffect
        runCatching {
            if (activeShares.size == 1) {
                val only = activeShares.first()
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(LatLng(only.latitude, only.longitude), 15f)
                )
            } else if (activeShares.isNotEmpty() && focusedShare == null) {
                val avgLat = activeShares.sumOf { it.latitude } / activeShares.size
                val avgLng = activeShares.sumOf { it.longitude } / activeShares.size
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(LatLng(avgLat, avgLng), 13.5f)
                )
            }
        }
    }

    val clusters = remember(activeShares, cameraPositionState.position.zoom) {
        LiveLocationClusterer.clusterLiveLocations(
            shares = activeShares,
            zoomLevel = cameraPositionState.position.zoom
        )
    }

    fun tryStartWithPrivacyRules() {
        when (privacyScope) {
            LiveLocationScope.NOBODY -> Unit // Button disabled with privacy message
            LiveLocationScope.ASK_EACH_TIME -> showPrivacyConfirmDialog = true
            LiveLocationScope.CONTACTS_ONLY -> {
                if (isContactsOnlyAllowed) {
                    onStartSharing(selectedDurationMin)
                } else {
                    showPrivacyConfirmDialog = true
                }
            }
            LiveLocationScope.ALL_CHATS -> onStartSharing(selectedDurationMin)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 22.dp)
                .testTag("live_location_map_sheet"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = vybColors.cardElevationDp,
            border = BorderStroke(0.75.dp, vybColors.hairlineBorder)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(VybGradientBrush),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.GpsFixed,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Ubicación en tiempo real",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (chat.chatType == "group") {
                                "$chatTitle · ${activeShares.size}/${VybConstants.MAX_GROUP_LIVE_LOCATION_SHARERS} activos"
                            } else {
                                "$chatTitle · ${privacyScope.shortBadge}"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = vybColors.secondaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("live_location_close_button")
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Cerrar mapa")
                    }
                }
                HorizontalDivider(thickness = 0.75.dp, color = vybColors.hairlineBorder)

                // Map or Graceful Fallback Viewport
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(vybColors.subtleSurface)
                ) {
                    if (hasValidMapsKey) {
                        GoogleMap(
                            modifier = Modifier.fillMaxSize(),
                            cameraPositionState = cameraPositionState,
                            uiSettings = MapUiSettings(
                                zoomControlsEnabled = true,
                                myLocationButtonEnabled = false,
                                compassEnabled = true
                            )
                        ) {
                            clusters.forEach { cluster ->
                                val pos = LatLng(cluster.latitude, cluster.longitude)
                                if (cluster.isCluster) {
                                    MarkerComposable(
                                        keys = arrayOf<Any>(cluster.id, cluster.members.size),
                                        state = MarkerState(position = pos),
                                        onClick = {
                                            val nextZoom = (cameraPositionState.position.zoom + 2.2f).coerceAtMost(19f)
                                            scope.launch {
                                                runCatching {
                                                    cameraPositionState.animate(
                                                        CameraUpdateFactory.newLatLngZoom(pos, nextZoom)
                                                    )
                                                }
                                            }
                                            true
                                        }
                                    ) {
                                        ClusterAvatarMarkerBadge(cluster = cluster)
                                    }
                                } else {
                                    val member = cluster.primaryMember
                                    MarkerComposable(
                                        keys = arrayOf<Any>(member.userId, member.latitude, member.longitude, member.avatarUrl ?: ""),
                                        state = MarkerState(position = pos),
                                        onClick = {
                                            focusedShare = member
                                            scope.launch {
                                                runCatching {
                                                    cameraPositionState.animate(
                                                        CameraUpdateFactory.newLatLngZoom(pos, 16f)
                                                    )
                                                }
                                            }
                                            true
                                        }
                                    ) {
                                        UserAvatarMapMarker(
                                            share = member,
                                            isSelf = member.userId == currentUid
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // Graceful interactive fallback when MAPS_API_KEY is not configured yet
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(0.75.dp, vybColors.hairlineBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.LocationOn,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Configura MAPS_API_KEY en el panel Secrets (y en GitHub Secrets si compilas el APK vía CI) para renderizar el mapa interactivo de Google Maps.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = vybColors.secondaryText
                                    )
                                }
                            }
                            if (clusters.isNotEmpty()) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Agrupación y proximidad en tiempo real (${clusters.size})",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    clusters.forEach { cluster ->
                                        Surface(
                                            shape = RoundedCornerShape(14.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            border = BorderStroke(0.75.dp, vybColors.hairlineBorder),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                if (cluster.isCluster) {
                                                    ClusterAvatarMarkerBadge(cluster = cluster)
                                                } else {
                                                    UserAvatarMapMarker(
                                                        share = cluster.primaryMember,
                                                        isSelf = cluster.primaryMember.userId == currentUid
                                                    )
                                                }
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = if (cluster.isCluster) {
                                                            "${cluster.members.size} usuarios cercanos: ${cluster.members.joinToString { it.displayName.substringBefore(' ') }}"
                                                        } else {
                                                            cluster.primaryMember.displayName
                                                        },
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.SemiBold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = String.format(
                                                            Locale.US,
                                                            "%.4f, %.4f",
                                                            cluster.latitude,
                                                            cluster.longitude
                                                        ),
                                                        style = MaterialTheme.typography.labelSmall.merge(VybMonoTabularStyle),
                                                        color = vybColors.secondaryText
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Active participants horizontal pill strip over bottom of map
                    if (activeShares.isNotEmpty()) {
                        LazyRow(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .padding(bottom = 10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(activeShares, key = { it.userId }) { share ->
                                val distLabel = if (showDistance && myShare != null && share.userId != currentUid) {
                                    LocationHelper.formatDistanceLabel(
                                        LocationHelper.distanceMetersBetween(
                                            myShare.latitude,
                                            myShare.longitude,
                                            share.latitude,
                                            share.longitude
                                        )
                                    )
                                } else null
                                val isFocused = focusedShare?.userId == share.userId
                                Surface(
                                    onClick = {
                                        focusedShare = share
                                        scope.launch {
                                            cameraPositionState.animate(
                                                CameraUpdateFactory.newLatLngZoom(
                                                    LatLng(share.latitude, share.longitude),
                                                    16f
                                                )
                                            )
                                        }
                                    },
                                    shape = RoundedCornerShape(999.dp),
                                    color = if (isFocused) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
                                    },
                                    border = BorderStroke(
                                        1.dp,
                                        if (share.userId == currentUid) vybColors.sinpeEmerald else vybColors.hairlineBorder
                                    ),
                                    shadowElevation = 2.dp
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        VybAvatar(
                                            avatarUrl = share.avatarUrl,
                                            displayName = share.displayName,
                                            size = 24.dp
                                        )
                                        Column {
                                            Text(
                                                text = if (share.userId == currentUid) "Tú" else share.displayName.substringBefore(" "),
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1
                                            )
                                            if (distLabel != null) {
                                                Text(
                                                    text = distLabel,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = vybColors.sinpeEmerald,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(thickness = 0.75.dp, color = vybColors.hairlineBorder)

                // Footer Controls
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (privacyScope == LiveLocationScope.NOBODY && !amISharing) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Tu privacidad de ubicación está en 'Desactivado (Nadie)'. Cámbiala en Perfil ⋮ > Configuración > Privacidad y Alertas.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    } else if (groupAtCapacity) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Filled.Group, contentDescription = null)
                                Text(
                                    text = "Este grupo alcanzó el máximo de ${VybConstants.MAX_GROUP_LIVE_LOCATION_SHARERS} participantes compartiendo en vivo.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    } else if (!amISharing) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(15 to "15 min", 60 to "1 hora", 480 to "8 horas").forEach { (mins, label) ->
                                FilterChip(
                                    selected = selectedDurationMin == mins,
                                    onClick = { selectedDurationMin = mins },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (amISharing) {
                            Button(
                                onClick = onStopSharing,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("live_location_stop_button")
                            ) {
                                Icon(Icons.Filled.StopCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Dejar de compartir", color = Color.White)
                            }
                        } else {
                            Button(
                                enabled = !isStarting && privacyScope != LiveLocationScope.NOBODY && !groupAtCapacity,
                                onClick = { tryStartWithPrivacyRules() },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1.3f)
                                    .heightIn(min = 48.dp)
                                    .testTag("live_location_start_button")
                            ) {
                                if (isStarting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                                Spacer(Modifier.width(6.dp))
                                Text("Compartir en vivo")
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                onSendStaticLocation()
                                onDismiss()
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("live_location_send_point_button")
                        ) {
                            Text("Enviar punto actual", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }

    if (showPrivacyConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyConfirmDialog = false },
            title = { Text("Confirmar ubicación en vivo") },
            text = {
                Text(
                    if (privacyScope == LiveLocationScope.CONTACTS_ONLY && !isContactsOnlyAllowed) {
                        "Tu privacidad está configurada para 'Solo contactos guardados' y hay personas en este chat que aún no están en tu agenda. ¿Deseas compartir tu ubicación en tiempo real durante $selectedDurationMin min?"
                    } else {
                        "¿Deseas compartir tu ubicación GPS en tiempo real en \"$chatTitle\" durante $selectedDurationMin min?"
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPrivacyConfirmDialog = false
                        onStartSharing(selectedDurationMin)
                    }
                ) {
                    Text("Sí, compartir")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPrivacyConfirmDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun UserAvatarMapMarker(
    share: LiveLocationShare,
    isSelf: Boolean
) {
    val vybColors = LocalVybColors.current
    val ringColor = if (isSelf) vybColors.sinpeEmerald else MaterialTheme.colorScheme.primary
    val avatarBitmap = remember(share.avatarUrl) {
        LiveLocationClusterer.decodeOptimizedMarkerAvatar(share.avatarUrl, targetSizePx = 88)
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(3.dp, ringColor, CircleShape)
                .padding(3.dp),
            contentAlignment = Alignment.Center
        ) {
            if (avatarBitmap != null) {
                Image(
                    bitmap = avatarBitmap,
                    contentDescription = share.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(VybGradientBrush),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = share.displayName.take(1).uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            border = BorderStroke(0.75.dp, ringColor)
        ) {
            Text(
                text = if (isSelf) "Tú" else share.displayName.substringBefore(" ").take(12),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun ClusterAvatarMarkerBadge(
    cluster: LiveLocationCluster
) {
    val primary = cluster.primaryMember
    val avatarBitmap = remember(primary.avatarUrl) {
        LiveLocationClusterer.decodeOptimizedMarkerAvatar(primary.avatarUrl, targetSizePx = 76)
    }
    Box(
        contentAlignment = Alignment.TopEnd,
        modifier = Modifier.size(52.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .border(2.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                .padding(3.dp),
            contentAlignment = Alignment.Center
        ) {
            if (avatarBitmap != null) {
                Image(
                    bitmap = avatarBitmap,
                    contentDescription = "${cluster.members.size} usuarios cercanos",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            } else {
                Text(
                    text = "${cluster.members.size}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.surface)
        ) {
            Text(
                text = "+${cluster.members.size}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
            )
        }
    }
}
