package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config


class ExampleRobolectricTest: BaseRobolectricTest() {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()



    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("VYB", appName)
    }

    @Test
    fun `main activity launches and renders auth screen cleanly`() {
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("VYB").assertIsDisplayed()
        composeTestRule.onNodeWithText("Conecta · Comparte · Vibra").assertIsDisplayed()
        composeTestRule.onNodeWithTag("auth_tab_0").assertIsDisplayed()
        composeTestRule.onNodeWithTag("auth_tab_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("login_identifier_input").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Teléfono, @usuario o correo", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("login_password_input").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Mínimo 8 caracteres", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("forgot_password_button").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Recuperar contraseña").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancelar").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("¿Prefieres entrar con código SMS?").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("password_sign_in_button").performScrollTo().assertIsDisplayed().assertIsNotEnabled()
        composeTestRule.onNodeWithTag("login_identifier_input").performTextInput("ana_cr")
        composeTestRule.onNodeWithTag("login_password_input").performTextInput("clave1234")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("password_sign_in_button").performScrollTo().assertIsEnabled()
        composeTestRule.onNodeWithTag("google_sign_in_button").performScrollTo().assertIsDisplayed().assertIsEnabled()
        composeTestRule.onNodeWithTag("google_sign_in_button")
            .let { _ ->
                org.junit.Assert.assertEquals(
                    0,
                    composeTestRule.onAllNodes(androidx.compose.ui.test.hasText("Continuar con Google")).fetchSemanticsNodes().size
                )
            }
        composeTestRule.onNode(androidx.compose.ui.test.hasContentDescription("Continuar con Google")).assertIsDisplayed()

        // Switch theme modes without crash
        composeTestRule.onNodeWithTag("theme_option_amoled").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("theme_option_light").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // Switch between login and signup tabs
        composeTestRule.onNodeWithTag("auth_tab_1").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Crea tu cuenta VYB").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("registration_step_header").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("google_sign_in_button").performScrollTo().assertIsDisplayed().assertIsEnabled()
        org.junit.Assert.assertEquals(
            0,
            composeTestRule.onAllNodes(androidx.compose.ui.test.hasText("Crear cuenta con Google")).fetchSemanticsNodes().size
        )
        composeTestRule.onNode(androidx.compose.ui.test.hasContentDescription("Crear cuenta con Google")).assertIsDisplayed()

        // Enter 8-digit Costa Rica phone number enables SMS button
        composeTestRule.onNodeWithTag("send_sms_button").performScrollTo().assertIsNotEnabled()
        composeTestRule.onNodeWithTag("phone_input").performScrollTo().performTextInput("88887777")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("send_sms_button").performScrollTo().assertIsEnabled()
    }

    @Test
    fun `firestore named database config and user profile models are valid`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbId = context.getString(R.string.firestore_database_id)
        org.junit.Assert.assertTrue(dbId.isNotBlank())
        org.junit.Assert.assertNotEquals("(default)", dbId)

        val publicProfile = com.example.data.model.PublicUser()
        assertEquals("Disponible", publicProfile.quickStatus)
        assertEquals("", publicProfile.sinpeBank)
        assertEquals("contacts_only", publicProfile.sinpeVisibility)
        assertEquals(0L, publicProfile.postsCount)
        assertEquals(false, publicProfile.verified)

        val privateProfile = com.example.data.model.PrivateUser()
        assertEquals("BAC Credomatic", privateProfile.sinpeBank)
        assertEquals("contacts_only", privateProfile.sinpeVisibility)
        assertEquals(true, privateProfile.readReceipts)
        assertEquals(true, privateProfile.showOnlineStatus)
        assertEquals(com.example.data.model.SinpeVisibility.CONTACTS_ONLY, com.example.data.model.SinpeVisibility.fromKey(null))
        assertEquals(com.example.data.model.SinpeVisibility.CONTACTS_ONLY, com.example.data.model.SinpeVisibility.fromKey("unknown"))
        assertEquals(com.example.data.model.SinpeVisibility.HIDDEN, com.example.data.model.SinpeVisibility.fromKey("hidden"))
        assertEquals(com.example.data.model.SinpeVisibility.EVERYONE, com.example.data.model.SinpeVisibility.fromKey("everyone"))

        assertEquals(
            "alicia_cr",
            com.example.data.repository.UserRepository.normalizeUsername("@Alicia_CR!!")
        )
        assertEquals(
            "88887777",
            com.example.data.repository.UserRepository.normalizeCrPhone("+506 8888-7777")
        )
    }

    @Test
    fun `registration model ai flash config chat request and bitmap cache invariants are valid`() {
        val invalidIdentity = com.example.data.model.RegistrationUiModel(
            displayName = "Ana",
            username = "ab"
        )
        org.junit.Assert.assertFalse(invalidIdentity.isIdentityStepValid)

        val validIdentity = com.example.data.model.RegistrationUiModel(
            displayName = "Ana María",
            username = "ana_cr",
            usernameAvailability = com.example.data.model.UsernameAvailability.AVAILABLE,
            sinpeBank = "BAC Credomatic",
            phoneDigits = "88889999"
        )
        org.junit.Assert.assertTrue(validIdentity.isIdentityStepValid)
        org.junit.Assert.assertTrue(validIdentity.isSinpeStepValid)
        org.junit.Assert.assertFalse(validIdentity.isPasswordStepValid)
        val validWithPassword = validIdentity.copy(
            password = "MiClaveSegura1",
            confirmPassword = "MiClaveSegura1"
        )
        org.junit.Assert.assertTrue(validWithPassword.isPasswordStepValid)
        assertEquals("88889999", validIdentity.normalizedPhone)
        assertEquals(3, com.example.data.model.RegistrationStep.SINPE_SETUP.stepNumber)
        assertEquals(4, com.example.data.model.RegistrationStep.PASSWORD_SETUP.stepNumber)
        assertEquals(4, com.example.data.model.RegistrationStep.PASSWORD_SETUP.totalSteps)

        val hash1 = com.example.data.repository.UserRepository.sha256Hex("username:ana_cr")
        val hash2 = com.example.data.repository.UserRepository.sha256Hex("username:ana_cr")
        assertEquals(64, hash1.length)
        assertEquals(hash1, hash2)

        val standard = com.example.data.model.AiModelChoice.STANDARD
        assertEquals("Estándar", standard.label)
        assertEquals("gemini-3.8-flash", standard.modelId)
        assertEquals("gemini-3.5-flash-lite", standard.fallbackModelId)

        val lite = com.example.data.model.AiModelChoice.LITE
        assertEquals("Ligero", lite.label)
        assertEquals("gemini-3.5-flash-lite", lite.modelId)
        assertEquals("gemini-3.8-flash", lite.fallbackModelId)

        // User-facing labels and descriptions must never expose internal engine names
        com.example.data.model.AiModelChoice.entries.forEach { choice ->
            val combined = "${choice.label} ${choice.subtitle}"
            org.junit.Assert.assertFalse(combined.contains("Gemini", ignoreCase = true))
            org.junit.Assert.assertFalse(combined.contains("Flash", ignoreCase = true))
            org.junit.Assert.assertFalse(combined.contains("3.8"))
            org.junit.Assert.assertFalse(combined.contains("3.5"))
        }

        // Read receipt logic checks
        val msgTs = com.google.firebase.Timestamp(1_700_000_100L, 0)
        val earlierRead = com.google.firebase.Timestamp(1_700_000_050L, 0)
        val laterRead = com.google.firebase.Timestamp(1_700_000_200L, 0)
        val unreadChat = com.example.data.model.Chat(
            id = "dm_u1_u2",
            participantUids = listOf("u1", "u2"),
            unreadCounts = mapOf("u1" to 0L, "u2" to 1L),
            lastReadAt = mapOf("u2" to earlierRead)
        )
        org.junit.Assert.assertFalse(
            com.example.ui.viewmodel.ChatViewModel.isMessageReadByPeer(
                chat = unreadChat,
                messageCreatedAt = msgTs,
                currentUid = "u1",
                readReceiptsEnabled = true
            )
        )
        val readChat = unreadChat.copy(
            unreadCounts = mapOf("u1" to 0L, "u2" to 0L),
            lastReadAt = mapOf("u2" to laterRead)
        )
        org.junit.Assert.assertTrue(
            com.example.ui.viewmodel.ChatViewModel.isMessageReadByPeer(
                chat = readChat,
                messageCreatedAt = msgTs,
                currentUid = "u1",
                readReceiptsEnabled = true
            )
        )
        org.junit.Assert.assertFalse(
            com.example.ui.viewmodel.ChatViewModel.isMessageReadByPeer(
                chat = readChat,
                messageCreatedAt = msgTs,
                currentUid = "u1",
                readReceiptsEnabled = false
            )
        )

        // Notification permission helper works without crashing
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.example.core.NotificationHelper.hasNotificationPermission(context)

        assertEquals("0:05", com.example.core.formatVoiceDuration(5))
        assertEquals("1:12", com.example.core.formatVoiceDuration(72))

        val voiceMsg = com.example.data.model.ChatMessage(
            chatId = "dm_1",
            messageType = "media",
            mediaType = "audio",
            mediaId = "med_123",
            audioDurationSec = 14L
        )
        assertEquals("audio", voiceMsg.mediaType)
        assertEquals(14L, voiceMsg.audioDurationSec)

        val req = com.example.data.model.ChatRequest(
            fromUid = "u1",
            fromName = "Ana",
            fromUsername = "ana_cr",
            toUid = "u2",
            toName = "Luis",
            toUsername = "luis_cr",
            participantUids = listOf("u1", "u2")
        )
        assertEquals("pending", req.status)
        assertEquals(2, req.participantUids.size)

        org.junit.Assert.assertNull(com.example.core.BitmapMemoryCache.getCached(null))
        org.junit.Assert.assertNull(com.example.core.BitmapMemoryCache.getCached("https://example.com/a.jpg"))
    }

    @Test
    fun `chat message edit window bomb fields and in-chat ai mode labels are valid`() {
        val nowSec = 1_700_005_000L
        val recentOwnText = com.example.data.model.ChatMessage(
            id = "m1",
            chatId = "dm_1",
            senderId = "u1",
            messageType = "text",
            text = "Hola",
            createdAt = com.google.firebase.Timestamp(nowSec - 300L, 0)
        )
        org.junit.Assert.assertTrue(
            com.example.ui.viewmodel.ChatViewModel.canEditMessage(recentOwnText, "u1", nowSec)
        )
        assertEquals(
            55,
            com.example.ui.viewmodel.ChatViewModel.remainingEditMinutes(recentOwnText, nowSec)
        )

        val expiredOwnText = recentOwnText.copy(
            createdAt = com.google.firebase.Timestamp(nowSec - 3660L, 0)
        )
        org.junit.Assert.assertFalse(
            com.example.ui.viewmodel.ChatViewModel.canEditMessage(expiredOwnText, "u1", nowSec)
        )

        org.junit.Assert.assertFalse(
            com.example.ui.viewmodel.ChatViewModel.canEditMessage(recentOwnText, "u2", nowSec)
        )

        val deletedOwnText = recentOwnText.copy(deletedForEveryone = true, text = "")
        org.junit.Assert.assertFalse(
            com.example.ui.viewmodel.ChatViewModel.canEditMessage(deletedOwnText, "u1", nowSec)
        )

        val bombOwnText = recentOwnText.copy(
            bombDurationSec = 15L,
            bombTriggeredAt = com.google.firebase.Timestamp(nowSec - 2L, 0)
        )
        org.junit.Assert.assertFalse(
            com.example.ui.viewmodel.ChatViewModel.canEditMessage(bombOwnText, "u1", nowSec)
        )
        assertEquals(15L, bombOwnText.bombDurationSec)
        org.junit.Assert.assertNotNull(bombOwnText.bombTriggeredAt)

        val mediaOwn = recentOwnText.copy(messageType = "media")
        org.junit.Assert.assertFalse(
            com.example.ui.viewmodel.ChatViewModel.canEditMessage(mediaOwn, "u1", nowSec)
        )

        val sinpeOwn = recentOwnText.copy(messageType = "sinpe_card")
        org.junit.Assert.assertFalse(
            com.example.ui.viewmodel.ChatViewModel.canEditMessage(sinpeOwn, "u1", nowSec)
        )

        val editedOwn = recentOwnText.copy(editedAt = com.google.firebase.Timestamp(nowSec - 60L, 0))
        org.junit.Assert.assertNotNull(editedOwn.editedAt)

        com.example.ui.screens.InChatAiMode.entries.forEach { mode ->
            val combined = "${mode.title} ${mode.subtitle}"
            org.junit.Assert.assertFalse(combined.contains("Gemini", ignoreCase = true))
            org.junit.Assert.assertFalse(combined.contains("Flash", ignoreCase = true))
            org.junit.Assert.assertFalse(combined.contains("3.8"))
            org.junit.Assert.assertFalse(combined.contains("3.5"))
        }

        // Buzz 2-per-day global cap, daily reset, cleared chat watermark, and bomb media checks
        assertEquals(2L, com.example.data.model.VybConstants.MAX_BUZZ_PER_DAY)
        assertEquals(2L, com.example.ui.viewmodel.ChatViewModel.MAX_BUZZ_PER_DAY)
        val todayKey = com.example.ui.viewmodel.ChatViewModel.currentDateKey()
        val freshPrivate = com.example.data.model.PrivateUser(userId = "u1")
        assertEquals(2, com.example.ui.viewmodel.ChatViewModel.remainingDailyBuzzCount(freshPrivate))
        val oneUsedToday = freshPrivate.copy(buzzDailyDate = todayKey, buzzDailyCount = 1L)
        assertEquals(1, com.example.ui.viewmodel.ChatViewModel.remainingDailyBuzzCount(oneUsedToday))
        val maxedToday = freshPrivate.copy(buzzDailyDate = todayKey, buzzDailyCount = 2L)
        assertEquals(0, com.example.ui.viewmodel.ChatViewModel.remainingDailyBuzzCount(maxedToday))
        val yesterdayMaxed = freshPrivate.copy(buzzDailyDate = "2020-01-01", buzzDailyCount = 2L)
        assertEquals(2, com.example.ui.viewmodel.ChatViewModel.remainingDailyBuzzCount(yesterdayMaxed))

        val clearedTs = com.google.firebase.Timestamp(1_700_000_500L, 0)
        val earlierMsgTs = com.google.firebase.Timestamp(1_700_000_400L, 0)
        val laterMsgTs = com.google.firebase.Timestamp(1_700_000_600L, 0)
        val clearedChat = com.example.data.model.Chat(
            id = "dm_u1_u2",
            participantUids = listOf("u1", "u2"),
            lastMessageSenderId = "u2",
            lastMessageText = "Mensaje viejo",
            lastMessageAt = earlierMsgTs,
            clearedBeforeAt = mapOf("u1" to clearedTs)
        )
        assertEquals(
            "Conversación vacía",
            com.example.ui.viewmodel.ChatViewModel.resolveVisibleLastMessage(clearedChat, "u1")
        )
        assertEquals(
            "Mensaje viejo",
            com.example.ui.viewmodel.ChatViewModel.resolveVisibleLastMessage(clearedChat, "u2")
        )
        val reopenedWithLegacyBuzz = clearedChat.copy(
            lastMessageSenderId = "u1",
            lastMessageText = "Zumbido",
            lastMessageAt = laterMsgTs
        )
        assertEquals(
            "Enviaste un bip",
            com.example.ui.viewmodel.ChatViewModel.resolveVisibleLastMessage(reopenedWithLegacyBuzz, "u1")
        )
        assertEquals(
            "Te envió un bip",
            com.example.ui.viewmodel.ChatViewModel.resolveVisibleLastMessage(reopenedWithLegacyBuzz, "u2")
        )
        val reopenedWithBip = clearedChat.copy(
            lastMessageSenderId = "u1",
            lastMessageText = "Bip",
            lastMessageAt = laterMsgTs
        )
        assertEquals(
            "Enviaste un bip",
            com.example.ui.viewmodel.ChatViewModel.resolveVisibleLastMessage(reopenedWithBip, "u1")
        )
        assertEquals(
            "Te envió un bip",
            com.example.ui.viewmodel.ChatViewModel.resolveVisibleLastMessage(reopenedWithBip, "u2")
        )
        org.junit.Assert.assertTrue(com.example.ui.viewmodel.ChatViewModel.isBuzzPreviewText("Bip"))
        org.junit.Assert.assertTrue(com.example.ui.viewmodel.ChatViewModel.isBuzzPreviewText("⚡ ¡Bip!"))
        org.junit.Assert.assertTrue(com.example.ui.viewmodel.ChatViewModel.isBuzzPreviewText("Zumbido"))
        org.junit.Assert.assertTrue(com.example.ui.viewmodel.ChatViewModel.isBuzzPreviewText("⚡ ¡Zumbido!"))
        org.junit.Assert.assertFalse(com.example.ui.viewmodel.ChatViewModel.isBuzzPreviewText("Hola pura vida"))

        val bombImageMsg = com.example.data.model.ChatMessage(
            id = "img_bomb_1",
            chatId = "dm_1",
            senderId = "u1",
            messageType = "media",
            mediaType = "image",
            bombDurationSec = 10L
        )
        assertEquals(10L, bombImageMsg.bombDurationSec)
        assertEquals("image", bombImageMsg.mediaType)
    }

    @Test
    fun `qr encoder formats cr phones theme labels and missed call filters are valid`() {
        assertEquals("AMOLED", com.example.data.model.ThemeMode.AMOLED.label)
        assertEquals("Sistema", com.example.data.model.ThemeMode.SYSTEM.label)
        assertEquals("Claro", com.example.data.model.ThemeMode.LIGHT.label)

        assertEquals(
            "+506 6352-9566",
            com.example.data.repository.UserRepository.formatCrPhone("63529566")
        )
        assertEquals(
            "+506 6352-9566",
            com.example.data.repository.UserRepository.formatCrPhone("+50663529566")
        )
        assertEquals(
            "+506 6352-9566",
            com.example.data.repository.UserRepository.formatCrPhone("506 6352 9566")
        )
        assertEquals(
            "+506 6352-9566",
            com.example.ui.components.formatCostaRicaPhone("+506 6352-9566")
        )
        org.junit.Assert.assertNull(com.example.data.repository.UserRepository.formatCrPhone("   "))

        val qrPayload = "SINPE Móvil\nTeléfono: +506 6352-9566\nNombre: Ana Sofía\nBanco: BAC Credomatic"
        val matrix = com.example.core.QrCodeEncoder.encode(qrPayload)
        org.junit.Assert.assertTrue(matrix.size >= 21)
        org.junit.Assert.assertEquals(0, (matrix.size - 17) % 4)
        org.junit.Assert.assertEquals(matrix.size * matrix.size, matrix.modules.size)
        // Finder pattern invariants at top-left (0..6, 0..6) and dark module at (8, size - 8)
        org.junit.Assert.assertTrue(matrix.get(0, 0))
        org.junit.Assert.assertTrue(matrix.get(3, 3))
        org.junit.Assert.assertFalse(matrix.get(1, 1))
        org.junit.Assert.assertTrue(matrix.get(matrix.size - 1, 0))
        org.junit.Assert.assertTrue(matrix.get(0, matrix.size - 1))
        org.junit.Assert.assertTrue(matrix.get(8, matrix.size - 8))

        val calls = listOf(
            com.example.data.model.CallSession(id = "c1", status = "ended"),
            com.example.data.model.CallSession(id = "c2", status = "missed"),
            com.example.data.model.CallSession(id = "c3", status = "rejected"),
            com.example.data.model.CallSession(id = "c4", status = "missed")
        )
        assertEquals(4, com.example.ui.viewmodel.CallViewModel.filterCalls(calls, com.example.ui.viewmodel.CallHistoryFilter.ALL).size)
        val missedOnly = com.example.ui.viewmodel.CallViewModel.filterCalls(calls, com.example.ui.viewmodel.CallHistoryFilter.MISSED)
        assertEquals(listOf("c2", "c4"), missedOnly.map { it.id })

        // Live peer profile always takes precedence over stale call history snapshot
        assertEquals(
            "https://cdn.example.com/new.jpg",
            com.example.ui.viewmodel.CallViewModel.resolvePeerAvatar(
                livePeer = com.example.data.model.PublicUser(userId = "u2", avatarUrl = "https://cdn.example.com/new.jpg"),
                fallbackAvatar = "https://cdn.example.com/old.jpg"
            )
        )
        org.junit.Assert.assertNull(
            com.example.ui.viewmodel.CallViewModel.resolvePeerAvatar(
                livePeer = com.example.data.model.PublicUser(userId = "u2", avatarUrl = ""),
                fallbackAvatar = "https://cdn.example.com/old.jpg"
            )
        )
        assertEquals(
            "https://cdn.example.com/old.jpg",
            com.example.ui.viewmodel.CallViewModel.resolvePeerAvatar(
                livePeer = null,
                fallbackAvatar = "https://cdn.example.com/old.jpg"
            )
        )
    }

    @Test
    fun `direct chat timestamps date dividers grouping and daily buzz sync are accurate`() {
        val baseCal = java.util.Calendar.getInstance(com.example.ui.components.COSTA_RICA_TIME_ZONE).apply {
            set(2026, java.util.Calendar.NOVEMBER, 20, 15, 45, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val nowMillis = baseCal.timeInMillis

        val morningCal = (baseCal.clone() as java.util.Calendar).apply {
            set(java.util.Calendar.HOUR_OF_DAY, 9)
            set(java.util.Calendar.MINUTE, 5)
        }
        val noonCal = (baseCal.clone() as java.util.Calendar).apply {
            set(java.util.Calendar.HOUR_OF_DAY, 12)
            set(java.util.Calendar.MINUTE, 0)
        }
        val midnightCal = (baseCal.clone() as java.util.Calendar).apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 15)
        }
        val yesterdayCal = (baseCal.clone() as java.util.Calendar).apply {
            add(java.util.Calendar.DAY_OF_YEAR, -1)
            set(java.util.Calendar.HOUR_OF_DAY, 15)
            set(java.util.Calendar.MINUTE, 45)
        }
        val olderSameYearCal = (baseCal.clone() as java.util.Calendar).apply {
            set(2026, java.util.Calendar.NOVEMBER, 14, 18, 20, 0)
        }
        val prevYearCal = (baseCal.clone() as java.util.Calendar).apply {
            set(2025, java.util.Calendar.DECEMBER, 25, 20, 0, 0)
        }

        assertEquals(
            "09:05 a. m.",
            com.example.ui.components.formatMessageTime(
                com.google.firebase.Timestamp(java.util.Date(morningCal.timeInMillis)),
                nowMillis
            )
        )
        assertEquals(
            "12:00 p. m.",
            com.example.ui.components.formatMessageTime(
                com.google.firebase.Timestamp(java.util.Date(noonCal.timeInMillis)),
                nowMillis
            )
        )
        assertEquals(
            "03:45 p. m.",
            com.example.ui.components.formatMessageTime(
                com.google.firebase.Timestamp(java.util.Date(nowMillis)),
                nowMillis
            )
        )
        assertEquals(
            "12:15 a. m.",
            com.example.ui.components.formatMessageTime(
                com.google.firebase.Timestamp(java.util.Date(midnightCal.timeInMillis)),
                nowMillis
            )
        )

        assertEquals(
            "Hoy",
            com.example.ui.components.formatChatDateDivider(
                com.google.firebase.Timestamp(java.util.Date(morningCal.timeInMillis)),
                nowMillis
            )
        )
        assertEquals(
            "Ayer",
            com.example.ui.components.formatChatDateDivider(
                com.google.firebase.Timestamp(java.util.Date(yesterdayCal.timeInMillis)),
                nowMillis
            )
        )
        assertEquals(
            "14 de noviembre",
            com.example.ui.components.formatChatDateDivider(
                com.google.firebase.Timestamp(java.util.Date(olderSameYearCal.timeInMillis)),
                nowMillis
            )
        )
        assertEquals(
            "25 de diciembre de 2025",
            com.example.ui.components.formatChatDateDivider(
                com.google.firebase.Timestamp(java.util.Date(prevYearCal.timeInMillis)),
                nowMillis
            )
        )

        val m1 = com.example.data.model.ChatMessage(
            id = "m1",
            senderId = "u1",
            messageType = "text",
            text = "Hola",
            createdAt = com.google.firebase.Timestamp(java.util.Date(yesterdayCal.timeInMillis))
        )
        val m2 = com.example.data.model.ChatMessage(
            id = "m2",
            senderId = "u1",
            messageType = "text",
            text = "Hoy temprano",
            createdAt = com.google.firebase.Timestamp(java.util.Date(morningCal.timeInMillis))
        )
        val m3 = com.example.data.model.ChatMessage(
            id = "m3",
            senderId = "u1",
            messageType = "buzz",
            text = "Zumbido",
            createdAt = com.google.firebase.Timestamp(java.util.Date(noonCal.timeInMillis))
        )
        val m4 = com.example.data.model.ChatMessage(
            id = "m4",
            senderId = "u2",
            messageType = "text",
            text = "Listo!",
            createdAt = com.google.firebase.Timestamp(java.util.Date(nowMillis))
        )
        val timeline = listOf(m1, m2, m3, m4)

        val meta0 = com.example.ui.viewmodel.ChatViewModel.computeMessageGroupingMeta(timeline, 0, nowMillis)
        assertEquals(true, meta0.showDateDivider)
        assertEquals("Ayer", meta0.dateLabel)
        assertEquals(false, meta0.isGroupedWithPrevious)

        val meta1 = com.example.ui.viewmodel.ChatViewModel.computeMessageGroupingMeta(timeline, 1, nowMillis)
        assertEquals(true, meta1.showDateDivider)
        assertEquals("Hoy", meta1.dateLabel)
        assertEquals(false, meta1.isGroupedWithPrevious)
        assertEquals(true, meta1.isGroupedWithNext)

        val meta2 = com.example.ui.viewmodel.ChatViewModel.computeMessageGroupingMeta(timeline, 2, nowMillis)
        assertEquals(false, meta2.showDateDivider)
        assertEquals(true, meta2.isGroupedWithPrevious)
        assertEquals(4, meta2.topSpacingDp)

        val meta3 = com.example.ui.viewmodel.ChatViewModel.computeMessageGroupingMeta(timeline, 3, nowMillis)
        assertEquals(false, meta3.showDateDivider)
        assertEquals(false, meta3.isGroupedWithPrevious)
        assertEquals(12, meta3.topSpacingDp)

        // Synchronized daily buzz count reconciles privateUser, real messages today, and optimistic state
        val stalePrivate = com.example.data.model.PrivateUser(userId = "u1", buzzDailyDate = "2026-11-20", buzzDailyCount = 0L)
        assertEquals(
            1,
            com.example.ui.viewmodel.ChatViewModel.remainingDailyBuzzCount(
                privateUser = stalePrivate,
                messages = timeline,
                currentUid = "u1",
                optimisticUsedToday = 0,
                nowMillis = nowMillis
            )
        )
        assertEquals(
            0,
            com.example.ui.viewmodel.ChatViewModel.remainingDailyBuzzCount(
                privateUser = stalePrivate,
                messages = timeline,
                currentUid = "u1",
                optimisticUsedToday = 2,
                nowMillis = nowMillis
            )
        )
    }

    @Test
    fun `costa rica phone mask friendly call errors quick statuses and timezone lock are valid`() {
        // 1. Costa Rica phone mask XXXX-XXXX
        assertEquals("8888", com.example.ui.components.formatCrPhoneMask("8888"))
        assertEquals("8888-7", com.example.ui.components.formatCrPhoneMask("88887"))
        assertEquals("8888-7777", com.example.ui.components.formatCrPhoneMask("88887777"))
        val transformed = com.example.ui.components.CostaRicaPhoneVisualTransformation()
            .filter(androidx.compose.ui.text.AnnotatedString("63529566"))
        assertEquals("6352-9566", transformed.text.text)
        assertEquals(0, transformed.offsetMapping.originalToTransformed(0))
        assertEquals(4, transformed.offsetMapping.originalToTransformed(4))
        assertEquals(6, transformed.offsetMapping.originalToTransformed(5))
        assertEquals(9, transformed.offsetMapping.originalToTransformed(8))
        assertEquals(8, transformed.offsetMapping.transformedToOriginal(9))

        // 2. CallViewModel friendly error mapping hides raw Firebase PERMISSION_DENIED errors
        val permError = com.example.ui.viewmodel.CallViewModel.formatCallHistoryError(
            IllegalStateException("PERMISSION_DENIED: Missing or insufficient permissions.")
        )
        org.junit.Assert.assertFalse(permError.contains("PERMISSION_DENIED"))
        org.junit.Assert.assertFalse(permError.contains("Missing or insufficient"))
        org.junit.Assert.assertTrue(permError.contains("permiso", ignoreCase = true))

        // 3. Profile quick statuses include "En casa"
        org.junit.Assert.assertTrue(
            com.example.data.model.VybConstants.QUICK_STATUSES.contains("En casa")
        )

        // 4. Timestamp formatting stays locked to America/Costa_Rica even if JVM default TZ is UTC
        val previousTz = java.util.TimeZone.getDefault()
        try {
            val crCal = java.util.Calendar.getInstance(com.example.ui.components.COSTA_RICA_TIME_ZONE).apply {
                set(2026, java.util.Calendar.NOVEMBER, 20, 9, 5, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            val crTs = com.google.firebase.Timestamp(java.util.Date(crCal.timeInMillis))
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"))
            assertEquals("09:05 a. m.", com.example.ui.components.formatMessageTime(crTs, crCal.timeInMillis))
            assertEquals("2026-11-20", com.example.ui.viewmodel.ChatViewModel.currentDateKey(crCal.timeInMillis))
        } finally {
            java.util.TimeZone.setDefault(previousTz)
        }
    }

    @Test
    fun `java custom reactions catalog and badge view render all 7 reactions cleanly`() {
        val all = com.example.reactions.ReactionCatalog.getAll()
        assertEquals(7, all.size)
        assertEquals(
            listOf(
                "pura_vida" to "Pura vida",
                "buena_vibra" to "Buena vibra",
                "que_rajado" to "¡Qué rajado!",
                "que_chiva" to "¡Qué chiva!",
                "de_una" to "¡De una!",
                "que_aguevado" to "¡Qué agüevado!",
                "que_chicha" to "¡Qué chicha!"
            ),
            all.map { it.key to it.label }
        )
        assertEquals(R.drawable.pura_vida, com.example.reactions.ReactionCatalog.findByKey("pura_vida")?.drawableResId)
        assertEquals(R.drawable.buena_vibra, com.example.reactions.ReactionCatalog.findByKey("buena_vibra")?.drawableResId)
        assertEquals(R.drawable.que_rajado, com.example.reactions.ReactionCatalog.findByKey("que_rajado")?.drawableResId)
        assertEquals(R.drawable.que_chiva, com.example.reactions.ReactionCatalog.findByKey("que_chiva")?.drawableResId)
        assertEquals(R.drawable.de_una, com.example.reactions.ReactionCatalog.findByKey("de_una")?.drawableResId)
        assertEquals(R.drawable.que_aguevado, com.example.reactions.ReactionCatalog.findByKey("que_aguevado")?.drawableResId)
        assertEquals(R.drawable.que_chicha, com.example.reactions.ReactionCatalog.findByKey("que_chicha")?.drawableResId)
        org.junit.Assert.assertTrue(com.example.reactions.ReactionCatalog.isValidKey("que_rajado"))
        org.junit.Assert.assertFalse(com.example.reactions.ReactionCatalog.isValidKey("desconocida"))
        org.junit.Assert.assertNull(com.example.reactions.ReactionCatalog.findByKey("desconocida"))

        val reactionsMap = mapOf(
            "que_aguevado" to listOf("u1"),
            "pura_vida" to listOf("u1", "u2"),
            "que_rajado" to listOf("u2"),
            "que_chicha" to emptyList()
        )
        val active = com.example.reactions.ReactionCatalog.getOrderedActiveEntries(reactionsMap, "u1")
        assertEquals(3, active.size)
        assertEquals("pura_vida", active[0].reaction.key)
        assertEquals(2, active[0].count)
        org.junit.Assert.assertTrue(active[0].isSelectedByCurrentUser)
        assertEquals("que_rajado", active[1].reaction.key)
        assertEquals(1, active[1].count)
        assertEquals("que_aguevado", active[2].reaction.key)
        assertEquals(1, active[2].count)

        val resolvedStyles = all.map { com.example.reactions.ReactionAnimatorHelper.resolveStyleForKey(it.key) }.toSet()
        assertEquals(7, resolvedStyles.size)
        assertEquals(
            com.example.reactions.ReactionAnimatorHelper.MotionStyle.VOLCANO_ERUPT,
            com.example.reactions.ReactionAnimatorHelper.resolveStyleForKey("que_rajado")
        )

        val context = ApplicationProvider.getApplicationContext<Context>()
        val badgesView = com.example.reactions.ReactionBadgesView(context)
        var clickedKey: String? = null
        badgesView.bindReactions(reactionsMap, "u1", true, true) { reaction ->
            clickedKey = reaction.key
        }
        assertEquals(android.view.View.VISIBLE, badgesView.visibility)
        assertEquals(3, badgesView.childCount)
        val firstBadgeChild = badgesView.getChildAt(0)
        for (i in 0 until badgesView.childCount) {
            val child = badgesView.getChildAt(i)
            org.junit.Assert.assertTrue(child is android.widget.ImageView)
            org.junit.Assert.assertFalse(child is android.view.ViewGroup)
            org.junit.Assert.assertNull(child.background)
            org.junit.Assert.assertTrue(com.example.reactions.ReactionAnimatorHelper.hasActiveAnimation(child))
        }
        assertEquals(
            com.example.reactions.ReactionAnimatorHelper.MotionStyle.HEARTBEAT_PULSE,
            com.example.reactions.ReactionAnimatorHelper.getActiveStyle(firstBadgeChild)
        )
        // Also verify light-theme overload and click callback
        badgesView.bindReactions(reactionsMap, "u1", true, false) { reaction ->
            clickedKey = reaction.key
        }
        val widthSpec = android.view.View.MeasureSpec.makeMeasureSpec(600, android.view.View.MeasureSpec.AT_MOST)
        val heightSpec = android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
        badgesView.measure(widthSpec, heightSpec)
        badgesView.layout(0, 0, badgesView.measuredWidth, badgesView.measuredHeight)
        org.junit.Assert.assertTrue(badgesView.measuredWidth > 0)
        org.junit.Assert.assertTrue(badgesView.measuredHeight > 0)
        badgesView.getChildAt(2).performClick()
        assertEquals("que_aguevado", clickedKey)

        // All 7 1.2s Pulso VYB signature micro-animations run safely via ViewOverlay without altering ViewGroup hierarchy
        all.forEach { reaction ->
            com.example.reactions.ReactionAnimatorHelper.playSignatureMicroAnimation(firstBadgeChild, reaction.key)
        }
        com.example.reactions.ReactionAnimatorHelper.playHoverSpark(firstBadgeChild, "que_rajado")
        assertEquals(3, badgesView.childCount)

        // Clearing reactions detaches animations cleanly
        badgesView.bindReactions(emptyMap(), "u1", true, false, null)
        assertEquals(android.view.View.GONE, badgesView.visibility)
        assertEquals(0, badgesView.childCount)

        // Verify Pulso VYB 7-reaction Gaussian scrubbing & floating tooltip across all 7 slots
        var selectedFromDrag: String? = null
        composeTestRule.runOnUiThread {
            val controller = com.example.reactions.ReactionFloatingBarController(composeTestRule.activity)
            controller.showAboveAnchor(
                composeTestRule.activity.window.decorView,
                android.graphics.Rect(80, 340, 420, 420),
                true,
                "pura_vida",
                { reaction -> selectedFromDrag = reaction.key },
                null
            )
            org.junit.Assert.assertTrue(controller.isShowing)
            val popupRoot = controller.popupRootForTest!!
            val wSpec = android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.AT_MOST)
            val hSpec = android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
            popupRoot.measure(wSpec, hSpec)
            popupRoot.layout(0, 0, popupRoot.measuredWidth, popupRoot.measuredHeight)

            val bar = popupRoot.findViewWithTag<android.view.ViewGroup>(
                com.example.reactions.ReactionFloatingBarController.TAG_BAR_CONTAINER
            )
            assertEquals(7, bar.childCount)
            val label = popupRoot.findViewWithTag<android.widget.TextView>(
                com.example.reactions.ReactionFloatingBarController.TAG_FLOATING_LABEL
            )
            val barH = bar.height.toFloat() * 0.5f
            val slot0X = (bar.getChildAt(0).left + bar.getChildAt(0).right) * 0.5f
            val slot2X = (bar.getChildAt(2).left + bar.getChildAt(2).right) * 0.5f
            val slot6X = (bar.getChildAt(6).left + bar.getChildAt(6).right) * 0.5f

            val now = android.os.SystemClock.uptimeMillis()
            val down = android.view.MotionEvent.obtain(now, now, android.view.MotionEvent.ACTION_DOWN, slot0X, barH, 0)
            bar.dispatchTouchEvent(down)
            assertEquals(android.view.View.VISIBLE, label.visibility)
            assertEquals("Pura vida", label.text.toString())

            val move2 = android.view.MotionEvent.obtain(now, now + 20, android.view.MotionEvent.ACTION_MOVE, slot2X, barH, 0)
            bar.dispatchTouchEvent(move2)
            assertEquals("¡Qué rajado!", label.text.toString())
            // Gaussian lift elevates & scales the active icon
            org.junit.Assert.assertTrue(bar.getChildAt(2).scaleX > 1.0f)
            org.junit.Assert.assertTrue(bar.getChildAt(2).translationY < 0f)

            val move6 = android.view.MotionEvent.obtain(now, now + 40, android.view.MotionEvent.ACTION_MOVE, slot6X, barH, 0)
            bar.dispatchTouchEvent(move6)
            assertEquals("¡Qué chicha!", label.text.toString())

            val up = android.view.MotionEvent.obtain(now, now + 60, android.view.MotionEvent.ACTION_UP, slot6X, barH, 0)
            bar.dispatchTouchEvent(up)

            down.recycle()
            move2.recycle()
            move6.recycle()
            up.recycle()
            org.junit.Assert.assertFalse(controller.isShowing)
        }
        assertEquals("que_chicha", selectedFromDrag)

        // ChatMessage defaults and new attachment/poll fields
        val msg = com.example.data.model.ChatMessage(
            id = "m_rx",
            chatId = "dm_1",
            messageType = "poll",
            text = "Hola",
            documentName = "factura.pdf",
            documentSizeBytes = 2048L,
            locationLabel = "San José, CR",
            locationLat = 9.9281,
            locationLng = -84.0907,
            sharedContactName = "Sofía",
            pollQuestion = "¿Café hoy?",
            pollOptions = listOf("Sí", "Luego"),
            pollVotes = mapOf("0" to listOf("u1"))
        )
        org.junit.Assert.assertTrue(msg.reactions.isEmpty())
        assertEquals("factura.pdf", msg.documentName)
        assertEquals(2, msg.pollOptions?.size)
        assertEquals("2 KB", com.example.core.MediaUtils.formatByteSize(2048L))
        assertEquals("1.5 MB", com.example.core.MediaUtils.formatByteSize(1_572_864L))
        assertEquals("pdf", com.example.core.MediaUtils.resolveExtension("application/pdf", "factura.pdf"))

        // AI summary topic resolution uses AI topic first, then peer/group title, never senderName
        val summaryModel = com.example.data.model.StructuredChatSummary(
            topic = "Almuerzo del viernes",
            mainPoint = "Se acordó almorzar a las 12.",
            decisions = listOf("Ir a soda"),
            pendingTasks = listOf("Enviar SINPE")
        )
        assertEquals("Almuerzo del viernes", summaryModel.topic)
        val directChat = com.example.data.model.Chat(
            id = "dm_alex_lourdes",
            participantUids = listOf("alex_uid", "lourdes_uid"),
            participantNames = mapOf("alex_uid" to "AlexanderR", "lourdes_uid" to "LourdesMitum"),
            chatType = "direct"
        )
        assertEquals(
            "Almuerzo del viernes",
            com.example.ui.viewmodel.ChatViewModel.resolveAiSummaryHeaderTopic(
                aiSummaryTopic = "Almuerzo del viernes",
                chat = directChat,
                currentUid = "alex_uid"
            )
        )
        assertEquals(
            "LourdesMitum",
            com.example.ui.viewmodel.ChatViewModel.resolveAiSummaryHeaderTopic(
                aiSummaryTopic = null,
                chat = directChat,
                currentUid = "alex_uid"
            )
        )
    }

    @Test
    fun `vyb 3 stage bomb mechanics perimeter geometry and tombstone merging are accurate`() {
        // 1. Stage resolution across armed -> stage 1 -> stage 2 (last 2s) -> stage 3 (0s)
        assertEquals(
            com.example.ui.components.VybBombVisualStage.STAGE_1_ACTIVE,
            com.example.ui.components.resolveBombVisualStage(isTriggered = false, remainingSec = 0.5f)
        )
        assertEquals(
            com.example.ui.components.VybBombVisualStage.STAGE_1_ACTIVE,
            com.example.ui.components.resolveBombVisualStage(isTriggered = true, remainingSec = 2.05f)
        )
        assertEquals(
            com.example.ui.components.VybBombVisualStage.STAGE_2_CRITICAL,
            com.example.ui.components.resolveBombVisualStage(isTriggered = true, remainingSec = 2.0f)
        )
        assertEquals(
            com.example.ui.components.VybBombVisualStage.STAGE_2_CRITICAL,
            com.example.ui.components.resolveBombVisualStage(isTriggered = true, remainingSec = 0.1f)
        )
        assertEquals(
            com.example.ui.components.VybBombVisualStage.STAGE_3_VAPORIZE,
            com.example.ui.components.resolveBombVisualStage(isTriggered = true, remainingSec = 0f)
        )

        // 2. Remaining seconds calculation respects unarmed, skew grace, and elapsed bounds
        assertEquals(
            5f,
            com.example.ui.components.resolveBombUiRemainingSeconds(
                isTriggered = false,
                triggeredAtSec = null,
                durationSec = 5L,
                nowMs = 1_700_000_000_000L
            ),
            0.01f
        )
        assertEquals(
            5f,
            com.example.ui.components.resolveBombUiRemainingSeconds(
                isTriggered = true,
                triggeredAtSec = 1_700_000_000L,
                durationSec = 5L,
                nowMs = 1_700_000_001_000L // within 1.8s skew grace
            ),
            0.01f
        )
        assertEquals(
            1.5f,
            com.example.ui.components.resolveBombUiRemainingSeconds(
                isTriggered = true,
                triggeredAtSec = 1_700_000_000L,
                durationSec = 5L,
                nowMs = 1_700_000_003_500L
            ),
            0.05f
        )

        // 3. Rounded-rectangle perimeter fuse tip stays on boundary across [0..1]
        val w = 240f
        val h = 76f
        val r = 21f
        listOf(0f, 0.15f, 0.32f, 0.5f, 0.78f, 1f).forEach { p ->
            val pt = com.example.ui.components.computeRoundedRectPerimeterPoint(w, h, r, p)
            org.junit.Assert.assertFalse(pt.x.isNaN())
            org.junit.Assert.assertFalse(pt.y.isNaN())
            org.junit.Assert.assertTrue(pt.x in -0.01f..(w + 0.01f))
            org.junit.Assert.assertTrue(pt.y in -0.01f..(h + 0.01f))
        }

        // 4. ChatViewModel merges destroyed bomb ghost tombstones without duplicating live IDs
        val ts1 = com.google.firebase.Timestamp(1000L, 0)
        val ts2 = com.google.firebase.Timestamp(1005L, 0)
        val ts3 = com.google.firebase.Timestamp(1010L, 0)
        val m1 = com.example.data.model.ChatMessage(id = "m1", text = "Primero", createdAt = ts1)
        val m3 = com.example.data.model.ChatMessage(id = "m3", text = "Tercero", createdAt = ts3)
        val ghost2 = com.example.data.model.ChatMessage(
            id = "m2",
            messageType = "bomb_destroyed_ghost",
            text = "",
            createdAt = ts2
        )
        val duplicateGhost3 = com.example.data.model.ChatMessage(
            id = "m3",
            messageType = "bomb_destroyed_ghost",
            text = "",
            createdAt = ts3
        )
        val merged = com.example.ui.viewmodel.ChatViewModel.mergeMessagesWithBombTombstones(
            liveMessages = listOf(m1, m3),
            tombstones = listOf(ghost2, duplicateGhost3)
        )
        assertEquals(listOf("m1", "m2", "m3"), merged.map { it.id })
        assertEquals("bomb_destroyed_ghost", merged[1].messageType)
        assertEquals("text", merged[2].messageType)
    }
}
