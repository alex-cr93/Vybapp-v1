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

    if (msg.deletedForEveryone === true) return;

    const db = getFirestore(admin.app(), DATABASE_ID);
    const chatSnap = await db.collection("chats").doc(chatId).get();
    const chatData = chatSnap.exists ? chatSnap.data() || {} : {};
    const isGroup = chatData.chatType === "group";
    const title = isGroup && chatData.title ? `${senderName} · ${chatData.title}` : senderName;

    const msgType = msg.messageType || "text";
    let channelType = "chat";
    let body = (msg.text || "Tienes un mensaje nuevo").slice(0, 200);

    if (msgType === "buzz") {
      channelType = "buzz";
      body = `${senderName} te envió un bip`;
    } else if (msgType === "sinpe") {
      channelType = "sinpe";
    } else if (msgType === "media") {
      const mediaFallback = msg.mediaType === "video"
        ? "Video adjunto"
        : msg.mediaType === "audio"
          ? "Nota de voz"
          : "Foto adjunta";
      body = (msg.text || mediaFallback).slice(0, 200);
    } else if (msgType === "document") {
      body = (msg.documentName ? `Documento: ${msg.documentName}` : msg.text || "Documento adjunto").slice(0, 200);
    } else if (msgType === "location") {
      body = (msg.locationLabel ? `Ubicación: ${msg.locationLabel}` : "Ubicación compartida").slice(0, 200);
    } else if (msgType === "contact") {
      body = (msg.sharedContactName ? `Contacto: ${msg.sharedContactName}` : "Contacto compartido").slice(0, 200);
    } else if (msgType === "poll") {
      body = (msg.pollQuestion ? `Encuesta: ${msg.pollQuestion}` : "Nueva encuesta").slice(0, 200);
    } else if (msgType === "ai_summary") {
      body = (msg.aiSummaryTopic ? `Resumen: ${msg.aiSummaryTopic}` : "Resumen del chat").slice(0, 200);
    }

    const privateSnaps = await Promise.all(
      recipientUids.map((uid) => db.collection("users_private").doc(uid).get())
    );

    const tokenOwnerMap = new Map();
    for (const privSnap of privateSnaps) {
      if (!privSnap.exists) continue;
      const priv = privSnap.data() || {};
      const blocked = Array.isArray(priv.blockedUids) ? priv.blockedUids : [];
      if (blocked.includes(senderId)) continue;
      if (channelType === "sinpe" && priv.sinpeAlertsEnabled === false) continue;
      if (channelType !== "sinpe" && priv.soundEnabled === false) continue;
      if (typeof priv.fcmToken === "string" && priv.fcmToken.trim()) {
        const cleanToken = priv.fcmToken.trim();
        if (!tokenOwnerMap.has(cleanToken)) {
          tokenOwnerMap.set(cleanToken, privSnap.id);
        }
      }
    }

    const tokens = Array.from(tokenOwnerMap.keys());
    if (tokens.length === 0) return;

    // Data-only payload with high Android priority guarantees VybMessagingService.onMessageReceived()
    // executes even when the app is in background or killed state.
    const response = await admin.messaging().sendEachForMulticast({
      tokens,
      data: {
        eventId: `msg_${chatId}_${messageId}`,
        channelType,
        type: channelType,
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
      },
    });

    if (response.failureCount > 0) {
      const cleanupOps = [];
      response.responses.forEach((resp, idx) => {
        if (!resp.success) {
          const code = resp.error && resp.error.code ? resp.error.code : "unknown";
          const badToken = tokens[idx];
          const ownerUid = tokenOwnerMap.get(badToken);
          console.warn(`FCM delivery error (${code}) for uid=${ownerUid}`);
          if (
            ownerUid &&
            (code === "messaging/registration-token-not-registered" ||
              code === "messaging/invalid-registration-token")
          ) {
            cleanupOps.push(
              db.collection("users_private").doc(ownerUid).update({
                fcmToken: admin.firestore.FieldValue.delete(),
              }).catch(() => {})
            );
          }
        }
      });
      if (cleanupOps.length > 0) {
        await Promise.all(cleanupOps);
      }
    }
  }
);
