const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const admin = require("firebase-admin");
const { getFirestore } = require("firebase-admin/firestore");

admin.initializeApp();

const DATABASE_ID = "ai-studio-android-vyb-b1f7dbb7-9d46-4609-8c96-2ec46e3c990e";

exports.sendChatMessagePush = onDocumentCreated(
  {
    document: "chats/{chatId}/messages/{messageId}",
    database: DATABASE_ID,
    region: "us-central1",
  },
  async (event) => {
    const snap = event.data;
    if (!snap) return;

    const msg = snap.data() || {};
    const chatId = event.params.chatId;
    const messageId = event.params.messageId;
    const senderId = msg.senderId || "";
    const senderName = (msg.senderName || "Usuario VYB").slice(0, 60);
    const participants = Array.isArray(msg.participantUids) ? msg.participantUids : [];
    const recipientUids = participants.filter((uid) => typeof uid === "string" && uid && uid !== senderId);
    if (recipientUids.length === 0) return;

    const db = getFirestore(admin.app(), DATABASE_ID);
    const chatSnap = await db.collection("chats").doc(chatId).get();
    const chatData = chatSnap.exists ? chatSnap.data() || {} : {};
    const isGroup = chatData.chatType === "group";
    const title = isGroup && chatData.title ? `${senderName} · ${chatData.title}` : senderName;

    const msgType = msg.messageType || "text";
    let channelType = "chat";
    let channelId = "vyb_chat_channel";
    let body = (msg.text || "Tienes un mensaje nuevo").slice(0, 200);

    if (msgType === "buzz") {
      channelType = "buzz";
      channelId = "vyb_buzz_channel";
      body = `${senderName} te envió un bip`;
    } else if (msgType === "sinpe") {
      channelType = "sinpe";
      channelId = "vyb_sinpe_channel";
    } else if (msgType === "media") {
      body = msg.text || (msg.mediaType === "video" ? "Video adjunto" : "Foto adjunta");
    }

    const privateSnaps = await Promise.all(
      recipientUids.map((uid) => db.collection("users_private").doc(uid).get())
    );

    const tokens = [];
    for (const privSnap of privateSnaps) {
      if (!privSnap.exists) continue;
      const priv = privSnap.data() || {};
      const blocked = Array.isArray(priv.blockedUids) ? priv.blockedUids : [];
      if (blocked.includes(senderId)) continue;
      if (channelType === "sinpe" && priv.sinpeAlertsEnabled === false) continue;
      if (channelType !== "sinpe" && priv.soundEnabled === false) continue;
      if (typeof priv.fcmToken === "string" && priv.fcmToken.trim()) {
        tokens.push(priv.fcmToken.trim());
      }
    }

    if (tokens.length === 0) return;

    await admin.messaging().sendEachForMulticast({
      tokens,
      data: {
        eventId: `msg_${chatId}_${messageId}`,
        channelType,
        chatId,
        messageId,
        senderId,
        senderName,
        title,
        body,
      },
      android: {
        priority: "high",
        ttl: 3600 * 1000,
        notification: {
          channelId,
          title,
          body,
          sound: "default",
          defaultSound: true,
          defaultVibrateTimings: true,
          notificationPriority: "PRIORITY_MAX",
        },
      },
    });
  }
);
