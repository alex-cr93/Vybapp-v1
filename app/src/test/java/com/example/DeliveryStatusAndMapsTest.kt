package com.example

import com.example.core.MapsConfigHelper
import com.example.data.model.Chat
import com.example.data.model.ChatMessage
import com.example.ui.viewmodel.ChatViewModel
import com.example.ui.viewmodel.MessageDeliveryStatus
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeliveryStatusAndMapsTest {

    private val alice = "uid_alice"
    private val bob = "uid_bob"
    private val carol = "uid_carol"

    @Test
    fun `maps config rejects placeholders and accepts real API keys`() {
        assertFalse(MapsConfigHelper.isUsableApiKey(null))
        assertFalse(MapsConfigHelper.isUsableApiKey(""))
        assertFalse(MapsConfigHelper.isUsableApiKey("   "))
        assertFalse(MapsConfigHelper.isUsableApiKey("MY_MAPS_API_KEY"))
        assertFalse(MapsConfigHelper.isUsableApiKey("MAPS_API_KEY_DEFAULT_VALUE"))
        assertFalse(MapsConfigHelper.isUsableApiKey("\${MAPS_API_KEY}"))
        assertTrue(MapsConfigHelper.isUsableApiKey("AIzaSyRealMapsKeyExample12345"))
    }

    @Test
    fun `direct message transitions from SENT to DELIVERED to READ`() {
        val t100 = Timestamp(100L, 0)
        val t110 = Timestamp(110L, 0)
        val t120 = Timestamp(120L, 0)

        val baseChat = Chat(
            id = "dm_1",
            participantUids = listOf(alice, bob),
            chatType = "direct",
            lastMessageSenderId = alice,
            lastMessageAt = t100,
            unreadCounts = mapOf(alice to 0L, bob to 1L),
            lastDeliveredAt = mapOf(alice to t100),
            lastReadAt = mapOf(alice to t100)
        )
        val sentMsg = ChatMessage(
            id = "m1",
            chatId = "dm_1",
            participantUids = listOf(alice, bob),
            senderId = alice,
            text = "Hola",
            deliveredTo = listOf(alice),
            readBy = listOf(alice),
            createdAt = t100
        )

        // Recipient offline -> single check (SENT)
        assertEquals(
            MessageDeliveryStatus.SENT,
            ChatViewModel.resolveMessageDeliveryStatus(baseChat, sentMsg, alice, readReceiptsEnabled = true)
        )
        assertEquals(
            MessageDeliveryStatus.SENT,
            ChatViewModel.resolveChatLastMessageStatus(baseChat, alice, readReceiptsEnabled = true)
        )

        // Recipient receives ACK -> double check gray (DELIVERED)
        val deliveredChat = baseChat.copy(lastDeliveredAt = mapOf(alice to t100, bob to t110))
        val deliveredMsg = sentMsg.copy(deliveredTo = listOf(alice, bob))
        assertEquals(
            MessageDeliveryStatus.DELIVERED,
            ChatViewModel.resolveMessageDeliveryStatus(deliveredChat, deliveredMsg, alice, readReceiptsEnabled = true)
        )
        assertEquals(
            MessageDeliveryStatus.DELIVERED,
            ChatViewModel.resolveChatLastMessageStatus(deliveredChat, alice, readReceiptsEnabled = true)
        )

        // Recipient opens chat & reads -> double check blue (READ)
        val readChat = deliveredChat.copy(
            unreadCounts = mapOf(alice to 0L, bob to 0L),
            lastReadAt = mapOf(alice to t100, bob to t120)
        )
        val readMsg = deliveredMsg.copy(readBy = listOf(alice, bob))
        assertEquals(
            MessageDeliveryStatus.READ,
            ChatViewModel.resolveMessageDeliveryStatus(readChat, readMsg, alice, readReceiptsEnabled = true)
        )
        assertEquals(
            MessageDeliveryStatus.READ,
            ChatViewModel.resolveChatLastMessageStatus(readChat, alice, readReceiptsEnabled = true)
        )

        // With readReceiptsEnabled = false, status caps at DELIVERED (never blue)
        assertEquals(
            MessageDeliveryStatus.DELIVERED,
            ChatViewModel.resolveMessageDeliveryStatus(readChat, readMsg, alice, readReceiptsEnabled = false)
        )
    }

    @Test
    fun `group message turns DELIVERED when at least one member ACKs and newer message stays SENT if peer goes offline`() {
        val t100 = Timestamp(100L, 0)
        val t150 = Timestamp(150L, 0)
        val t200 = Timestamp(200L, 0)

        val groupChat = Chat(
            id = "grp_1",
            participantUids = listOf(alice, bob, carol),
            chatType = "group",
            lastMessageSenderId = alice,
            lastMessageAt = t200,
            unreadCounts = mapOf(alice to 0L, bob to 1L, carol to 2L),
            lastDeliveredAt = mapOf(alice to t200, bob to t150),
            lastReadAt = mapOf(alice to t200)
        )

        val msg1DeliveredByBob = ChatMessage(
            id = "m1",
            chatId = "grp_1",
            senderId = alice,
            deliveredTo = listOf(alice, bob),
            readBy = listOf(alice),
            createdAt = t100
        )
        val msg2SentAfterBobWentOffline = ChatMessage(
            id = "m2",
            chatId = "grp_1",
            senderId = alice,
            deliveredTo = listOf(alice),
            readBy = listOf(alice),
            createdAt = t200
        )

        assertEquals(
            MessageDeliveryStatus.DELIVERED,
            ChatViewModel.resolveMessageDeliveryStatus(groupChat, msg1DeliveredByBob, alice, readReceiptsEnabled = true)
        )
        assertEquals(
            MessageDeliveryStatus.SENT,
            ChatViewModel.resolveMessageDeliveryStatus(groupChat, msg2SentAfterBobWentOffline, alice, readReceiptsEnabled = true)
        )
        assertEquals(
            MessageDeliveryStatus.SENT,
            ChatViewModel.resolveChatLastMessageStatus(groupChat, alice, readReceiptsEnabled = true)
        )
    }
}
