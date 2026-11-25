package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.data.model.AiModelChoice
import com.example.data.model.CallSession
import com.example.data.model.Chat
import com.example.data.model.LiveLocationScope
import com.example.data.model.PrivateLocationInfo
import com.example.data.model.PrivateUser
import com.example.data.model.PublicUser
import com.example.data.model.SinpeVisibility
import com.example.data.model.ThemeMode
import com.example.ui.screens.GroupMemberSelection
import com.example.ui.screens.ProfileSettingsNavigator
import com.example.ui.screens.ProfileSettingsPage
import com.example.ui.theme.VYBTheme
import com.example.ui.viewmodel.CallHistoryFilter
import com.example.ui.viewmodel.CallViewModel
import com.example.ui.viewmodel.ChatViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner


class GroupAndProfileRedesignRobolectricTest: BaseRobolectricTest() {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `whatsapp style settings hub has 6 categories and integrates private plus live location inside privacy alerts`() {
        var closedCount = 0
        var selectedTheme = ThemeMode.LIGHT
        var selectedAiModel = AiModelChoice.LITE
        var lastPrivacyUpdate: List<Boolean>? = null
        var lastLivePrivacy: Triple<LiveLocationScope, Int, Boolean>? = null
        var lastSinpeVis: String? = null
        var unblockedUid: String? = null
        var unblockedPhone: String? = null
        var passwordOpened = false
        var signedOut = false

        val publicUser = PublicUser(
            userId = "u1",
            displayName = "Ana Sofía",
            username = "ana_cr",
            sinpeBank = "BAC Credomatic"
        )
        val privateUser = PrivateUser(
            userId = "u1",
            phone = "88887777",
            readReceipts = true,
            showOnlineStatus = true,
            soundEnabled = true,
            callAlertsEnabled = true,
            sinpeAlertsEnabled = false,
            liveLocationScope = LiveLocationScope.CONTACTS_ONLY.key,
            liveLocationDurationMin = 60L,
            liveLocationShowDistance = true
        )
        val blockedUser = PublicUser(
            userId = "u99",
            displayName = "Carlos Bloqueado",
            username = "carlos_b"
        )

        composeRule.setContent {
            var currentPage by remember { mutableStateOf(ProfileSettingsPage.LIST) }
            var themeMode by remember { mutableStateOf(selectedTheme) }
            var aiModel by remember { mutableStateOf(selectedAiModel) }
            var privUser by remember { mutableStateOf(privateUser) }
            var sinpeVis by remember { mutableStateOf(SinpeVisibility.CONTACTS_ONLY) }
            var blocked by remember { mutableStateOf(listOf(blockedUser)) }
            var blockedPhoneList by remember { mutableStateOf(listOf("87654321")) }

            VYBTheme(themeMode = themeMode) {
                ProfileSettingsNavigator(
                    currentPage = currentPage,
                    onSelectPage = { currentPage = it },
                    onCloseSettings = { closedCount++ },
                    publicUser = publicUser,
                    privateUser = privUser,
                    formattedSinpePhone = "+506 8888-7777",
                    effectiveSinpeVisibility = sinpeVis,
                    themeMode = themeMode,
                    onSelectThemeMode = {
                        themeMode = it
                        selectedTheme = it
                    },
                    privateLocationEnabled = false,
                    privateLocation = PrivateLocationInfo(9.93, -84.09, "San José", emptyList()),
                    onEnablePrivateLocation = {},
                    onDisablePrivateLocation = {},
                    aiModel = aiModel,
                    onSelectAiModel = {
                        aiModel = it
                        selectedAiModel = it
                    },
                    onUpdatePrivacySettings = { r, s, snd, c, sinpe ->
                        privUser = privUser.copy(
                            readReceipts = r,
                            showOnlineStatus = s,
                            soundEnabled = snd,
                            callAlertsEnabled = c,
                            sinpeAlertsEnabled = sinpe
                        )
                        lastPrivacyUpdate = listOf(r, s, snd, c, sinpe)
                    },
                    onUpdateLiveLocationPrivacy = { scope, durationMin, showDist ->
                        privUser = privUser.copy(
                            liveLocationScope = scope.key,
                            liveLocationDurationMin = durationMin.toLong(),
                            liveLocationShowDistance = showDist
                        )
                        lastLivePrivacy = Triple(scope, durationMin, showDist)
                    },
                    onUpdateSinpeVisibility = { visKey ->
                        sinpeVis = SinpeVisibility.fromKey(visKey)
                        lastSinpeVis = visKey
                    },
                    blockedProfiles = blocked,
                    blockedPhones = blockedPhoneList,
                    onUnblockUser = { uid ->
                        unblockedUid = uid
                        blocked = blocked.filterNot { it.userId == uid }
                    },
                    onUnblockPhone = { phone ->
                        unblockedPhone = phone
                        blockedPhoneList = blockedPhoneList.filterNot { it == phone }
                    },
                    onBlockByIdentifier = { _, cb -> cb(true, "Bloqueado") },
                    onOpenPasswordModal = { passwordOpened = true },
                    onSignOut = { signedOut = true },
                    isExpanded = false
                )
            }
        }
        composeRule.waitForIdle()

        // Verify all 6 category rows appear on the hub (Ubicacion Privada moved inside Privacidad y Alertas)
        assertEquals(6, ProfileSettingsPage.entries.size - 1)
        composeRule.onNodeWithTag("settings_row_appearance").assertIsDisplayed()
        composeRule.onAllNodesWithTag("settings_row_location").assertCountEquals(0)
        composeRule.onNodeWithTag("settings_row_assistant").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_row_privacy_alerts").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_row_sinpe_privacy").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("settings_row_blocked").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("settings_row_account").performScrollTo().assertIsDisplayed()

        // 1. Apariencia
        composeRule.onNodeWithTag("settings_row_appearance").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("theme_option_amoled").assertIsDisplayed().performClick()
        assertEquals(ThemeMode.AMOLED, selectedTheme)
        composeRule.onNodeWithTag("settings_back_button").performClick()
        composeRule.waitForIdle()

        // 2. Asistente VYB
        composeRule.onNodeWithTag("settings_row_assistant").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Estándar").assertIsDisplayed().performClick()
        assertEquals(AiModelChoice.STANDARD, selectedAiModel)
        composeRule.onNodeWithTag("settings_back_button").performClick()
        composeRule.waitForIdle()

        // 3. Privacidad y Alertas (5 alerts toggles + Ubicacion Privada + Live Location privacy)
        composeRule.onNodeWithTag("settings_row_privacy_alerts").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Confirmaciones de lectura").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Mostrar estado en línea").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Notificaciones y sonido de mensajes").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Alertas de llamadas").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Alertas de cobros SINPE").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("privacy_switch_sinpe_alerts").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(listOf(true, true, true, true, true), lastPrivacyUpdate)

        // Ubicación Privada is now nested inside Privacidad y Alertas
        composeRule.onNodeWithText("Mostrar mi zona en el mapa").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("profile_private_location_switch").performScrollTo().assertIsDisplayed().assertIsOff()

        // Granular live location privacy controls
        composeRule.onNodeWithTag("live_loc_scope_all_chats").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(Triple(LiveLocationScope.ALL_CHATS, 60, true), lastLivePrivacy)
        composeRule.onNodeWithTag("settings_back_button").performClick()
        composeRule.waitForIdle()

        // 4. Privacidad de datos SINPE
        composeRule.onNodeWithTag("settings_row_sinpe_privacy").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("profile_sinpe_privacy_section").assertIsDisplayed()
        composeRule.onNodeWithTag("profile_sinpe_vis_everyone").assertIsDisplayed().performClick()
        assertEquals("everyone", lastSinpeVis)
        composeRule.onNodeWithTag("settings_back_button").performClick()
        composeRule.waitForIdle()

        // 5. Contactos Bloqueados (users + phones)
        composeRule.onNodeWithTag("settings_row_blocked").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Carlos Bloqueado").assertIsDisplayed()
        composeRule.onNodeWithTag("unblock_user_u99").performClick()
        composeRule.waitForIdle()
        assertEquals("u99", unblockedUid)
        composeRule.onNodeWithTag("unblock_phone_87654321").performClick()
        composeRule.waitForIdle()
        assertEquals("87654321", unblockedPhone)
        composeRule.onNodeWithText("No tienes contactos ni números bloqueados actualmente.").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_back_button").performClick()
        composeRule.waitForIdle()

        // 6. Cuenta y Sesión
        composeRule.onNodeWithTag("settings_row_account").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("profile_password_button").assertIsDisplayed().performClick()
        assertTrue(passwordOpened)
        composeRule.onNodeWithTag("profile_signout_button").assertIsDisplayed().performClick()
        assertTrue(signedOut)

        // Back to hub then close settings
        composeRule.onNodeWithTag("settings_back_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_back_button").performClick()
        assertEquals(1, closedCount)

        // Confirm no removed controls leaked back into hub
        composeRule.onAllNodesWithTag("profile_sinpe_qr_button").assertCountEquals(0)
        assertFalse(lastPrivacyUpdate == emptyList<Boolean>())
    }

    @Test
    fun `multi participant group chat group call model and saved post ids work cleanly`() {
        val groupChat = Chat(
            id = "grp_team",
            participantUids = listOf("u1", "u2", "u3", "u4"),
            participantNames = mapOf(
                "u1" to "Ana",
                "u2" to "Luis",
                "u3" to "Sofía",
                "u4" to "Mateo"
            ),
            chatType = "group",
            title = "Paseo Arenal"
        )
        assertEquals(4, groupChat.participantUids.size)
        assertEquals("Paseo Arenal", ChatViewModel.resolveChatDisplayTitle(groupChat, "u1"))

        val selected = listOf(
            GroupMemberSelection("u2", "Luis", "luis_cr"),
            GroupMemberSelection("u3", "Sofía", "sofi_cr"),
            GroupMemberSelection("u4", "Mateo", "mateo_cr")
        )
        assertEquals(3, selected.size)

        val groupCall = CallSession(
            id = "call_grp_1",
            callerId = "u2",
            callerName = "Luis",
            calleeId = "grp_team",
            participantUids = groupChat.participantUids,
            activeParticipantUids = listOf("u2", "u3"),
            declinedUids = emptyList(),
            groupCall = true,
            chatId = "grp_team",
            groupTitle = "Paseo Arenal",
            callType = "video",
            status = "active"
        )
        val missedGroupCall = CallSession(
            id = "call_grp_2",
            callerId = "u3",
            callerName = "Sofía",
            calleeId = "grp_team",
            participantUids = groupChat.participantUids,
            groupCall = true,
            chatId = "grp_team",
            groupTitle = "Paseo Arenal",
            callType = "audio",
            status = "missed"
        )
        val allFiltered = CallViewModel.filterCalls(
            listOf(groupCall, missedGroupCall),
            CallHistoryFilter.ALL
        )
        assertEquals(2, allFiltered.size)
        val missedFiltered = CallViewModel.filterCalls(
            listOf(groupCall, missedGroupCall),
            CallHistoryFilter.MISSED
        )
        assertEquals(listOf("call_grp_2"), missedFiltered.map { it.id })
        assertTrue(groupCall.groupCall)
        assertEquals(2, groupCall.activeParticipantUids.size)

        val privWithSaved = PrivateUser(
            userId = "u1",
            savedPostIds = listOf("post_1", "post_2")
        )
        assertEquals(listOf("post_1", "post_2"), privWithSaved.savedPostIds)
    }
}
