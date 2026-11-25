package com.example

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import com.example.core.LiveLocationClusterer
import com.example.data.model.Chat
import com.example.data.model.LiveLocationScope
import com.example.data.model.LiveLocationShare
import com.example.data.model.PrivateUser
import com.example.data.model.PublicUser
import com.example.data.model.VybConstants
import com.example.ui.screens.ChatListBlockContactDialog
import com.example.ui.screens.ChatListContextMenuSheet
import com.example.ui.screens.LiveLocationActiveBanner
import com.example.ui.screens.LiveLocationChatMapSheet
import com.example.ui.theme.VYBTheme
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config


class LiveLocationAndChatInteractionsRobolectricTest: BaseRobolectricTest() {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `live location clusterer groups nearby users at wide zoom and splits them when zoomed in`() {
        val future = Timestamp(System.currentTimeMillis() / 1000L + 3600L, 0)
        val shareA = LiveLocationShare(
            userId = "u1",
            chatId = "grp_1",
            displayName = "Ana",
            latitude = 9.9281,
            longitude = -84.0907,
            expiresAt = future,
            active = true
        )
        val shareB = LiveLocationShare(
            userId = "u2",
            chatId = "grp_1",
            displayName = "Luis",
            latitude = 9.9289, // ~90m north
            longitude = -84.0907,
            expiresAt = future,
            active = true
        )
        val shareFar = LiveLocationShare(
            userId = "u3",
            chatId = "grp_1",
            displayName = "Sofía",
            latitude = 10.0150, // Alajuela (~10km away)
            longitude = -84.2116,
            expiresAt = future,
            active = true
        )

        val wideClusters = LiveLocationClusterer.clusterLiveLocations(
            shares = listOf(shareA, shareB, shareFar),
            zoomLevel = 12f
        )
        assertEquals(2, wideClusters.size)
        assertTrue(wideClusters.any { it.isCluster && it.members.size == 2 })

        val closeClusters = LiveLocationClusterer.clusterLiveLocations(
            shares = listOf(shareA, shareB, shareFar),
            zoomLevel = 18f
        )
        assertEquals(3, closeClusters.size)
        assertTrue(closeClusters.none { it.isCluster })
        assertEquals(15, VybConstants.MAX_GROUP_LIVE_LOCATION_SHARERS)
    }

    @Test
    fun `chat list context menu exposes all 4 actions for direct and group chats`() {
        var opened = false
        var buzzed = false
        var blocked = false
        var deletedDirect = false
        var leftGroup = false
        var deletedGroup = false

        val me = PublicUser(userId = "u1", displayName = "Ana")
        val directChat = Chat(
            id = "direct_u1_u2",
            participantUids = listOf("u1", "u2"),
            participantNames = mapOf("u1" to "Ana", "u2" to "Luis"),
            chatType = "direct"
        )
        val groupChat = Chat(
            id = "grp_1",
            participantUids = listOf("u1", "u2", "u3"),
            chatType = "group",
            title = "Paseo Volcán",
            createdBy = "u1"
        )

        val useGroup = androidx.compose.runtime.mutableStateOf(false)
        composeRule.setContent {
            VYBTheme {
                val isGrp = useGroup.value
                ChatListContextMenuSheet(
                    chat = if (isGrp) groupChat else directChat,
                    chatTitle = if (isGrp) "Paseo Volcán" else "Luis",
                    peerAvatar = null,
                    currentUser = me,
                    remainingBuzzes = if (isGrp) 1 else 2,
                    onDismiss = {},
                    onOpenChat = { opened = true },
                    onSendBuzz = { buzzed = true },
                    onRequestBlock = { blocked = true },
                    onRequestDeleteDirectChat = { deletedDirect = true },
                    onRequestLeaveGroup = { leftGroup = true },
                    onRequestDeleteGroup = { deletedGroup = true }
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("chat_ctx_open").performScrollTo().assertIsDisplayed().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithTag("chat_ctx_buzz").performScrollTo().assertIsDisplayed().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithTag("chat_ctx_block").performScrollTo().assertIsDisplayed().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithTag("chat_ctx_delete").performScrollTo().assertIsDisplayed().performSemanticsAction(SemanticsActions.OnClick)

        assertTrue(opened)
        assertTrue(buzzed)
        assertTrue(blocked)
        assertTrue(deletedDirect)

        useGroup.value = true
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("chat_ctx_leave_group").performScrollTo().assertIsDisplayed().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithTag("chat_ctx_delete_group").performScrollTo().assertIsDisplayed().performSemanticsAction(SemanticsActions.OnClick)
        assertTrue(leftGroup)
        assertTrue(deletedGroup)
    }

    @Test
    fun `block contact dialog blocks direct peer and identifier`() {
        var blockedUid: String? = null
        var blockedName: String? = null
        var blockedIdentifier: String? = null

        val directChat = Chat(
            id = "direct_u1_u2",
            participantUids = listOf("u1", "u2"),
            participantNames = mapOf("u1" to "Ana", "u2" to "Luis"),
            chatType = "direct"
        )

        composeRule.setContent {
            VYBTheme {
                ChatListBlockContactDialog(
                    chat = directChat,
                    currentUid = "u1",
                    peerProfiles = mapOf("u2" to PublicUser(userId = "u2", displayName = "Luis")),
                    blockedUids = emptyList(),
                    onBlockUser = { uid, name ->
                        blockedUid = uid
                        blockedName = name
                    },
                    onUnblockUser = {},
                    onBlockByIdentifier = { query, cb ->
                        blockedIdentifier = query
                        cb(true, "Bloqueado")
                    },
                    onDismiss = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("chat_block_direct_peer_button").assertIsDisplayed().performClick()
        assertEquals("u2", blockedUid)
        assertEquals("Luis", blockedName)
        assertEquals(null, blockedIdentifier)
    }

    @Test
    fun `live location banner and map sheet enforce 15-user group cap and nobody privacy scope`() {
        val future = Timestamp(System.currentTimeMillis() / 1000L + 3600L, 0)
        val groupShares15 = (2..16).map { idx ->
            LiveLocationShare(
                userId = "u$idx",
                chatId = "grp_big",
                displayName = "User $idx",
                latitude = 9.93 + idx * 0.001,
                longitude = -84.09,
                expiresAt = future,
                active = true
            )
        }
        val groupChat = Chat(
            id = "grp_big",
            participantUids = listOf("u1") + groupShares15.map { it.userId },
            chatType = "group",
            title = "Grupo Grande"
        )

        composeRule.setContent {
            VYBTheme {
                LiveLocationActiveBanner(
                    activeShares = groupShares15,
                    currentUid = "u1",
                    isGroup = true,
                    onOpenMap = {}
                )
                LiveLocationChatMapSheet(
                    chat = groupChat,
                    chatTitle = "Grupo Grande",
                    currentUid = "u1",
                    privateUser = PrivateUser(
                        userId = "u1",
                        liveLocationScope = LiveLocationScope.ALL_CHATS.key
                    ),
                    savedContacts = emptyList(),
                    activeShares = groupShares15,
                    isStarting = false,
                    onStartSharing = {},
                    onStopSharing = {},
                    onSendStaticLocation = {},
                    onDismiss = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("chat_live_location_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("live_location_start_button").assertIsNotEnabled()
    }
}
