package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.Chat
import com.example.data.model.PublicUser
import com.example.ui.components.VybAvatar
import com.example.ui.theme.LocalVybColors
import com.example.ui.viewmodel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListContextMenuSheet(
    chat: Chat,
    chatTitle: String,
    peerAvatar: String?,
    currentUser: PublicUser,
    remainingBuzzes: Int,
    onDismiss: () -> Unit,
    onOpenChat: () -> Unit,
    onSendBuzz: () -> Unit,
    onRequestBlock: () -> Unit,
    onRequestDeleteDirectChat: () -> Unit,
    onRequestLeaveGroup: () -> Unit,
    onRequestDeleteGroup: () -> Unit
) {
    val vybColors = LocalVybColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isGroup = chat.chatType == "group"
    val isGroupCreator = isGroup && (chat.createdBy.isBlank() || chat.createdBy == currentUser.userId)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .testTag("chat_context_menu_sheet"),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                VybAvatar(avatarUrl = peerAvatar, displayName = chatTitle, size = 44.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = chatTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isGroup) {
                            "Grupo · ${chat.participantUids.size} participantes"
                        } else {
                            "Acciones rápidas de conversación"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = vybColors.secondaryText
                    )
                }
            }
            HorizontalDivider(thickness = 0.75.dp, color = vybColors.hairlineBorder)

            ChatContextActionItem(
                icon = Icons.AutoMirrored.Filled.Chat,
                title = "Abrir chat / enviar mensaje",
                subtitle = "Entrar directamente a la conversación",
                tint = MaterialTheme.colorScheme.primary,
                testTag = "chat_ctx_open",
                onClick = {
                    onDismiss()
                    onOpenChat()
                }
            )

            ChatContextActionItem(
                icon = Icons.Filled.Bolt,
                title = "Enviar un Bip directamente",
                subtitle = if (remainingBuzzes > 0) {
                    "Disponibles hoy: $remainingBuzzes/2"
                } else {
                    "Ya usaste tus 2 bips diarios"
                },
                tint = if (remainingBuzzes > 0) Color(0xFF00D2FF) else vybColors.secondaryText,
                enabled = remainingBuzzes > 0,
                testTag = "chat_ctx_buzz",
                onClick = {
                    onDismiss()
                    onSendBuzz()
                }
            )

            ChatContextActionItem(
                icon = Icons.Filled.Block,
                title = "Bloquear contacto o número",
                subtitle = if (isGroup) {
                    "Elige un integrante o ingresa @usuario / +506"
                } else {
                    "Bloquea a este contacto o cualquier número +506"
                },
                tint = MaterialTheme.colorScheme.error,
                testTag = "chat_ctx_block",
                onClick = {
                    onDismiss()
                    onRequestBlock()
                }
            )

            HorizontalDivider(thickness = 0.75.dp, color = vybColors.hairlineBorder)

            if (isGroup) {
                ChatContextActionItem(
                    icon = Icons.AutoMirrored.Filled.ExitToApp,
                    title = "Salir del grupo",
                    subtitle = "Dejarás de recibir mensajes y ubicación del grupo",
                    tint = MaterialTheme.colorScheme.error,
                    testTag = "chat_ctx_leave_group",
                    onClick = {
                        onDismiss()
                        onRequestLeaveGroup()
                    }
                )
                if (isGroupCreator) {
                    ChatContextActionItem(
                        icon = Icons.Filled.DeleteForever,
                        title = "Eliminar grupo",
                        subtitle = "Borra el grupo para todos los integrantes",
                        tint = MaterialTheme.colorScheme.error,
                        testTag = "chat_ctx_delete_group",
                        onClick = {
                            onDismiss()
                            onRequestDeleteGroup()
                        }
                    )
                }
            } else {
                ChatContextActionItem(
                    icon = Icons.Filled.DeleteOutline,
                    title = "Eliminar conversación",
                    subtitle = "Borra este historial de tu cuenta",
                    tint = MaterialTheme.colorScheme.error,
                    testTag = "chat_ctx_delete",
                    onClick = {
                        onDismiss()
                        onRequestDeleteDirectChat()
                    }
                )
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun ChatContextActionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color,
    testTag: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val vybColors = LocalVybColors.current
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        color = vybColors.nestedSurface,
        border = BorderStroke(0.75.dp, vybColors.hairlineBorder),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else vybColors.secondaryText
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = vybColors.secondaryText
                )
            }
        }
    }
}

@Composable
fun ChatListBlockContactDialog(
    chat: Chat,
    currentUid: String,
    peerProfiles: Map<String, PublicUser>,
    blockedUids: List<String>,
    onBlockUser: (uid: String, displayName: String) -> Unit,
    onUnblockUser: (uid: String) -> Unit,
    onBlockByIdentifier: (query: String, onResult: (Boolean, String) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    val vybColors = LocalVybColors.current
    val otherUids = remember(chat, currentUid) {
        chat.participantUids.filter { it != currentUid }
    }
    val directPeerUid = if (chat.chatType == "direct") {
        ChatViewModel.resolvePeerUid(chat, currentUid)
    } else null

    var identifierInput by remember { mutableStateOf("") }
    var feedbackMsg by remember { mutableStateOf<String?>(null) }
    var isBusy by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bloquear contacto o número") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (directPeerUid != null) {
                    val peerName = peerProfiles[directPeerUid]?.displayName
                        ?: chat.participantNames[directPeerUid]
                        ?: "Contacto VYB"
                    val isBlocked = directPeerUid in blockedUids
                    OutlinedButton(
                        onClick = {
                            if (isBlocked) {
                                onUnblockUser(directPeerUid)
                            } else {
                                onBlockUser(directPeerUid, peerName)
                            }
                            onDismiss()
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("chat_block_direct_peer_button")
                    ) {
                        Icon(
                            imageVector = if (isBlocked) Icons.Filled.CheckCircle else Icons.Filled.Block,
                            contentDescription = null,
                            tint = if (isBlocked) vybColors.sinpeEmerald else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (isBlocked) "Desbloquear a $peerName" else "Bloquear a $peerName",
                            color = if (isBlocked) vybColors.sinpeEmerald else MaterialTheme.colorScheme.error
                        )
                    }
                } else if (otherUids.isNotEmpty()) {
                    Text(
                        text = "Participantes del grupo:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    otherUids.forEach { uid ->
                        val name = peerProfiles[uid]?.displayName ?: chat.participantNames[uid] ?: "Usuario VYB"
                        val isBlocked = uid in blockedUids
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = vybColors.nestedSurface,
                            border = BorderStroke(0.75.dp, vybColors.hairlineBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                TextButton(
                                    onClick = {
                                        if (isBlocked) onUnblockUser(uid) else onBlockUser(uid, name)
                                    },
                                    modifier = Modifier.testTag("chat_block_group_member_$uid")
                                ) {
                                    Text(
                                        text = if (isBlocked) "Desbloquear" else "Bloquear",
                                        color = if (isBlocked) vybColors.sinpeEmerald else MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(thickness = 0.75.dp, color = vybColors.hairlineBorder)
                Text(
                    text = "También puedes bloquear por @usuario o teléfono +506:",
                    style = MaterialTheme.typography.bodySmall,
                    color = vybColors.secondaryText
                )
                OutlinedTextField(
                    value = identifierInput,
                    onValueChange = {
                        identifierInput = it
                        feedbackMsg = null
                    },
                    label = { Text("@usuario o +506 8888 8888") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("chat_ctx_block_identifier_input")
                )
                feedbackMsg?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.labelSmall,
                        color = vybColors.secondaryText
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = identifierInput.trim().isNotEmpty() && !isBusy,
                onClick = {
                    isBusy = true
                    onBlockByIdentifier(identifierInput.trim()) { ok, msg ->
                        isBusy = false
                        feedbackMsg = msg
                        if (ok) {
                            onDismiss()
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.testTag("chat_ctx_block_identifier_submit")
            ) {
                Text("Bloquear número / @", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}
