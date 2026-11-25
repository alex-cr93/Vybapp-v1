const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_vyb_123";
const BOB_UID = "bob_vyb_456";
const CHARLIE_UID = "charlie_vyb_789";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

const pastDate = () => new Date(Date.now() - 2000);
const futureDate = (ms = 86400000) => new Date(Date.now() + ms);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) await testEnv.cleanup();
});

beforeEach(async () => {
  if (testEnv) await testEnv.clearFirestore();
});

test("Unauthenticated users are denied everywhere", async () => {
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection("posts").limit(10).get());
  await assertFails(unauth.collection("users_public").doc(ALICE_UID).get());
  await assertFails(unauth.collection("chats").where("participantUids", "array-contains", ALICE_UID).get());
  await assertFails(unauth.collection("calls").where("participantUids", "array-contains", ALICE_UID).get());
});

test("Public & private user profiles enforce PII isolation and ownership", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastDate();

  await assertSucceeds(
    aliceDb.collection("users_public").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alicia",
      username: "alicia_cr",
      quickStatus: "Disponible",
      sinpeBank: "BAC Credomatic",
      verified: true,
      postsCount: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users_private").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: "alicia@example.com",
      phone: "+50688887777",
      blockedUids: [],
      readReceipts: true,
      showOnlineStatus: true,
      soundEnabled: true,
      callAlertsEnabled: true,
      sinpeAlertsEnabled: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Bob can read Alicia's public doc by ID, cannot list users_public, cannot read/write Alicia's private PII
  await assertSucceeds(bobDb.collection("users_public").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users_public").get());
  await assertFails(bobDb.collection("users_private").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users_public").doc(ALICE_UID).update({ displayName: "Hack", updatedAt: pastDate() }));
});

test("Directory lookup supports exact get but rejects list scraping", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastDate();

  await assertSucceeds(
    aliceDb.collection("directory_lookup").doc("user_alicia_cr").set({
      uid: ALICE_UID,
      displayName: "Alicia",
      username: "alicia_cr",
      sinpeBank: "BAC Credomatic",
      entryType: "username",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(bobDb.collection("directory_lookup").doc("user_alicia_cr").get());
  await assertFails(bobDb.collection("directory_lookup").get());
});

test("Posts, likes, shares, and comments require added contact relationship and match Kotlin queries", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const ts = pastDate();

  await assertSucceeds(
    aliceDb.collection("posts").doc("post_1").set({
      authorId: ALICE_UID,
      authorName: "Alicia",
      authorUsername: "alicia_cr",
      authorVerified: true,
      text: "Hola #vyb",
      hashtags: ["vyb"],
      mediaType: "none",
      likedBy: [],
      commentsCount: 0,
      shareCount: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Author Alicia can always read and query her own posts via whereEqualTo("authorId", ALICE_UID)
  await assertSucceeds(aliceDb.collection("posts").doc("post_1").get());
  await assertSucceeds(aliceDb.collection("posts").where("authorId", "==", ALICE_UID).limit(30).get());

  // Before Bob adds Alicia as a contact, Bob CANNOT read, list, like, or comment on post_1
  await assertFails(bobDb.collection("posts").doc("post_1").get());
  await assertFails(bobDb.collection("posts").limit(30).get());
  await assertFails(bobDb.collection("posts").where("authorId", "==", ALICE_UID).limit(30).get());
  await assertFails(bobDb.collection("posts").where("authorId", "in", [BOB_UID, ALICE_UID]).limit(30).get());
  await assertFails(bobDb.collection("posts").doc("post_1").update({ likedBy: [BOB_UID], updatedAt: pastDate() }));
  await assertFails(
    bobDb.collection("posts").doc("post_1").collection("comments").doc("c_unauth").set({
      postId: "post_1",
      authorId: BOB_UID,
      authorName: "Bob",
      authorUsername: "bob_cr",
      text: "No debería poder",
      createdAt: pastDate(),
    })
  );

  // Bob adds Alicia to /users/{BOB_UID}/contacts/{ALICE_UID}
  await assertSucceeds(
    bobDb.collection("users").doc(BOB_UID).collection("contacts").doc(ALICE_UID).set({
      contactUid: ALICE_UID,
      displayName: "Alicia",
      username: "alicia_cr",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Per-author whereEqualTo("authorId", ...) queries with limit(30) match Kotlin FeedRepository
  await assertSucceeds(bobDb.collection("posts").where("authorId", "==", BOB_UID).limit(30).get());
  await assertSucceeds(bobDb.collection("posts").where("authorId", "==", ALICE_UID).limit(30).get());
  await assertSucceeds(bobDb.collection("posts").doc("post_1").get());
  // Bob can like post_1
  await assertSucceeds(bobDb.collection("posts").doc("post_1").update({ likedBy: [BOB_UID], updatedAt: pastDate() }));
  // Bob cannot edit Alicia's post text
  await assertFails(bobDb.collection("posts").doc("post_1").update({ text: "Edited", updatedAt: pastDate() }));
  // Bob can add and list comments
  await assertSucceeds(
    bobDb.collection("posts").doc("post_1").collection("comments").doc("c_1").set({
      postId: "post_1",
      authorId: BOB_UID,
      authorName: "Bob",
      authorUsername: "bob_cr",
      text: "Genial!",
      createdAt: pastDate(),
    })
  );
  await assertSucceeds(
    bobDb.collection("posts").doc("post_1").collection("comments").where("postId", "==", "post_1").get()
  );

  // Non-contact Charlie is still strictly denied on posts and comments
  await assertFails(charlieDb.collection("posts").doc("post_1").get());
  await assertFails(charlieDb.collection("posts").doc("post_1").collection("comments").doc("c_1").get());
  await assertFails(charlieDb.collection("posts").doc("post_1").collection("comments").where("postId", "==", "post_1").get());
});

test("Stories enforce contacts-only visibility, author-scoped query, and view-only updates", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const ts = pastDate();

  await assertSucceeds(
    aliceDb.collection("stories").doc("story_1").set({
      authorId: ALICE_UID,
      authorName: "Alicia",
      authorUsername: "alicia_cr",
      authorVerified: true,
      mediaType: "image",
      caption: "Café en San José",
      viewerUids: [],
      active: true,
      expiresAt: futureDate(),
      createdAt: ts,
    })
  );

  // Author can query her own stories; non-contacts Bob & Charlie are denied
  await assertSucceeds(aliceDb.collection("stories").where("authorId", "==", ALICE_UID).limit(60).get());
  await assertFails(bobDb.collection("stories").doc("story_1").get());
  await assertFails(bobDb.collection("stories").where("authorId", "==", ALICE_UID).limit(60).get());
  await assertFails(bobDb.collection("stories").where("authorId", "in", [BOB_UID, ALICE_UID]).limit(60).get());
  await assertFails(bobDb.collection("stories").doc("story_1").update({ viewerUids: [BOB_UID] }));
  await assertFails(charlieDb.collection("stories").doc("story_1").get());

  // Bob adds Alicia as contact -> can list, read, and mark viewed
  await assertSucceeds(
    bobDb.collection("users").doc(BOB_UID).collection("contacts").doc(ALICE_UID).set({
      contactUid: ALICE_UID,
      displayName: "Alicia",
      username: "alicia_cr",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(bobDb.collection("stories").where("authorId", "==", BOB_UID).limit(60).get());
  await assertSucceeds(bobDb.collection("stories").where("authorId", "==", ALICE_UID).limit(60).get());
  await assertSucceeds(bobDb.collection("stories").doc("story_1").update({ viewerUids: [BOB_UID] }));
  await assertFails(bobDb.collection("stories").doc("story_1").update({ caption: "Hack" }));
  await assertFails(bobDb.collection("stories").doc("story_1").delete());
  await assertSucceeds(aliceDb.collection("stories").doc("story_1").delete());
});

test("Chats & SINPE cards enforce participant isolation, missing-doc get, delete, and terminal payment lock", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const ts = pastDate();

  // Deterministic missing chat check succeeds for signed-in users before first message creation
  await assertSucceeds(aliceDb.collection("chats").doc("dm_alice_bob").get());

  await assertSucceeds(
    aliceDb.collection("chats").doc("chat_ab").set({
      participantUids: [ALICE_UID, BOB_UID],
      participantNames: { [ALICE_UID]: "Alicia", [BOB_UID]: "Bob" },
      chatType: "direct",
      lastMessageText: "Cobro SINPE",
      unreadCounts: { [ALICE_UID]: 0, [BOB_UID]: 1 },
      createdBy: ALICE_UID,
      lastMessageAt: ts,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("chats").doc("chat_ab").collection("messages").doc("msg_1").set({
      chatId: "chat_ab",
      participantUids: [ALICE_UID, BOB_UID],
      senderId: ALICE_UID,
      senderName: "Alicia",
      messageType: "sinpe",
      text: "Cobro SINPE Móvil: ₡5000 por Almuerzo",
      sinpeAmount: 5000,
      sinpePerPerson: 2500,
      sinpeReason: "Almuerzo",
      sinpeRecipientUid: ALICE_UID,
      sinpeRecipientName: "Alicia",
      sinpeRecipientPhone: "+50688887777",
      sinpeRecipientBank: "BAC Credomatic",
      sinpeSplitCount: 2,
      sinpeStatus: "pending",
      sinpePaidByUids: [],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Bob marks chat as read with lastReadAt timestamp; Charlie cannot update it
  await assertSucceeds(
    bobDb.collection("chats").doc("chat_ab").update({
      unreadCounts: { [ALICE_UID]: 0, [BOB_UID]: 0 },
      lastReadAt: { [ALICE_UID]: ts, [BOB_UID]: pastDate() },
      updatedAt: pastDate(),
    })
  );
  await assertFails(
    charlieDb.collection("chats").doc("chat_ab").update({
      unreadCounts: { [ALICE_UID]: 0, [BOB_UID]: 0 },
      lastReadAt: { [CHARLIE_UID]: pastDate() },
      updatedAt: pastDate(),
    })
  );

  // Charlie cannot read chat, specific message, or run unfiltered message list
  await assertFails(charlieDb.collection("chats").doc("chat_ab").get());
  await assertFails(charlieDb.collection("chats").doc("chat_ab").collection("messages").doc("msg_1").get());
  await assertFails(charlieDb.collection("chats").doc("chat_ab").collection("messages").get());

  // Bob completes SINPE payment; subsequent mutation after 'completed' is locked
  await assertSucceeds(
    bobDb.collection("chats").doc("chat_ab").collection("messages").doc("msg_1").update({
      sinpeStatus: "completed",
      sinpePaidByUids: [BOB_UID],
      updatedAt: pastDate(),
    })
  );
  await assertFails(
    bobDb.collection("chats").doc("chat_ab").collection("messages").doc("msg_1").update({
      sinpeStatus: "pending",
      updatedAt: pastDate(),
    })
  );

  // Audio voice note message creation by participant succeeds; invalid duration or non-participant fails
  await assertSucceeds(
    bobDb.collection("chats").doc("chat_ab").collection("messages").doc("msg_audio").set({
      chatId: "chat_ab",
      participantUids: [ALICE_UID, BOB_UID],
      senderId: BOB_UID,
      senderName: "Bob",
      messageType: "media",
      text: "Nota de voz (0:12)",
      mediaId: "med_audio_123",
      mediaType: "audio",
      audioDurationSec: 12,
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );
  await assertFails(
    bobDb.collection("chats").doc("chat_ab").collection("messages").doc("msg_audio_bad").set({
      chatId: "chat_ab",
      participantUids: [ALICE_UID, BOB_UID],
      senderId: BOB_UID,
      senderName: "Bob",
      messageType: "media",
      text: "Nota de voz",
      mediaId: "med_audio_124",
      mediaType: "audio",
      audioDurationSec: 999,
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );
  await assertFails(
    charlieDb.collection("chats").doc("chat_ab").collection("messages").doc("msg_audio_charlie").set({
      chatId: "chat_ab",
      participantUids: [ALICE_UID, BOB_UID, CHARLIE_UID],
      senderId: CHARLIE_UID,
      senderName: "Charlie",
      messageType: "media",
      text: "Nota de voz",
      mediaId: "med_audio_125",
      mediaType: "audio",
      audioDurationSec: 5,
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );

  // Non-participant Charlie cannot delete direct chat; participant Bob can delete direct chat
  await assertFails(charlieDb.collection("chats").doc("chat_ab").delete());
  await assertSucceeds(bobDb.collection("chats").doc("chat_ab").delete());
});

test("Chat requests support send, accept, reject, delete, and prevent spoofing", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const ts = pastDate();

  // Unauthenticated access denied
  await assertFails(unauthDb.collection("chat_requests").where("participantUids", "array-contains", ALICE_UID).get());

  // Check missing deterministic request succeeds before creating
  await assertSucceeds(aliceDb.collection("chat_requests").doc("req_alice_bob").get());

  // Spoofing sender fails
  await assertFails(
    charlieDb.collection("chat_requests").doc("req_fake").set({
      fromUid: ALICE_UID,
      fromName: "Alicia",
      fromUsername: "alicia_cr",
      toUid: BOB_UID,
      toName: "Bob",
      toUsername: "bob_cr",
      participantUids: [ALICE_UID, BOB_UID],
      status: "pending",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Alice sends chat request to Bob
  await assertSucceeds(
    aliceDb.collection("chat_requests").doc("req_alice_bob").set({
      fromUid: ALICE_UID,
      fromName: "Alicia",
      fromUsername: "alicia_cr",
      fromSinpeBank: "BAC Credomatic",
      toUid: BOB_UID,
      toName: "Bob",
      toUsername: "bob_cr",
      participantUids: [ALICE_UID, BOB_UID],
      status: "pending",
      note: "Hola Bob, conectemos por VYB",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Both Alice and Bob can list participant requests; Charlie cannot list or read
  await assertSucceeds(bobDb.collection("chat_requests").where("participantUids", "array-contains", BOB_UID).get());
  await assertFails(charlieDb.collection("chat_requests").doc("req_alice_bob").get());
  await assertFails(charlieDb.collection("chat_requests").get());

  // Sender Alice cannot self-accept her own outgoing request
  await assertFails(
    aliceDb.collection("chat_requests").doc("req_alice_bob").update({
      status: "accepted",
      updatedAt: pastDate(),
    })
  );

  // Recipient Bob accepts the request
  await assertSucceeds(
    bobDb.collection("chat_requests").doc("req_alice_bob").update({
      status: "accepted",
      chatId: "dm_alice_bob",
      updatedAt: pastDate(),
    })
  );

  // Terminal state lock: cannot flip accepted -> rejected
  await assertFails(
    bobDb.collection("chat_requests").doc("req_alice_bob").update({
      status: "rejected",
      updatedAt: pastDate(),
    })
  );

  // Either participant can delete; Charlie cannot delete
  await assertFails(charlieDb.collection("chat_requests").doc("req_alice_bob").delete());
  await assertSucceeds(aliceDb.collection("chat_requests").doc("req_alice_bob").delete());

  // Re-creating a request after delete and rejecting it works
  await assertSucceeds(
    aliceDb.collection("chat_requests").doc("req_alice_bob").set({
      fromUid: ALICE_UID,
      fromName: "Alicia",
      fromUsername: "alicia_cr",
      toUid: BOB_UID,
      toName: "Bob",
      toUsername: "bob_cr",
      participantUids: [ALICE_UID, BOB_UID],
      status: "pending",
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );
  await assertSucceeds(
    bobDb.collection("chat_requests").doc("req_alice_bob").update({
      status: "rejected",
      updatedAt: pastDate(),
    })
  );
  await assertSucceeds(bobDb.collection("chat_requests").doc("req_alice_bob").delete());
});

test("Calls enforce participant access and terminal state locking", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const ts = pastDate();

  await assertSucceeds(
    aliceDb.collection("calls").doc("call_1").set({
      callerId: ALICE_UID,
      callerName: "Alicia",
      calleeId: BOB_UID,
      calleeName: "Bob",
      participantUids: [ALICE_UID, BOB_UID],
      callType: "video",
      status: "ringing",
      offerSdp: "v=0...",
      durationSeconds: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertFails(charlieDb.collection("calls").doc("call_1").get());
  await assertSucceeds(bobDb.collection("calls").doc("call_1").update({ status: "ended", durationSeconds: 42, updatedAt: pastDate() }));
  await assertFails(bobDb.collection("calls").doc("call_1").update({ status: "active", updatedAt: pastDate() }));
});

test("Messages enforce 1h edit window, delete-for-everyone scrubbing, and bomb message trigger & cleanup", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const recentTs = pastDate();
  const expiredTs = new Date(Date.now() - 65 * 60 * 1000);

  await assertSucceeds(
    aliceDb.collection("chats").doc("chat_bomb").set({
      participantUids: [ALICE_UID, BOB_UID],
      participantNames: { [ALICE_UID]: "Alicia", [BOB_UID]: "Bob" },
      chatType: "direct",
      lastMessageText: "Hola",
      unreadCounts: { [ALICE_UID]: 0, [BOB_UID]: 1 },
      createdBy: ALICE_UID,
      lastMessageAt: recentTs,
      createdAt: recentTs,
      updatedAt: recentTs,
    })
  );

  // Recent message can be edited by author, not by recipient
  await assertSucceeds(
    aliceDb.collection("chats").doc("chat_bomb").collection("messages").doc("msg_recent").set({
      chatId: "chat_bomb",
      participantUids: [ALICE_UID, BOB_UID],
      senderId: ALICE_UID,
      senderName: "Alicia",
      messageType: "text",
      text: "Mensaje original",
      createdAt: recentTs,
      updatedAt: recentTs,
    })
  );
  await assertFails(
    bobDb.collection("chats").doc("chat_bomb").collection("messages").doc("msg_recent").update({
      text: "Hack de Bob",
      editedAt: pastDate(),
      updatedAt: pastDate(),
    })
  );
  await assertSucceeds(
    aliceDb.collection("chats").doc("chat_bomb").collection("messages").doc("msg_recent").update({
      text: "Mensaje editado a tiempo",
      editedAt: pastDate(),
      updatedAt: pastDate(),
    })
  );

  // Old message (> 1h) cannot be edited
  await assertSucceeds(
    aliceDb.collection("chats").doc("chat_bomb").collection("messages").doc("msg_old").set({
      chatId: "chat_bomb",
      participantUids: [ALICE_UID, BOB_UID],
      senderId: ALICE_UID,
      senderName: "Alicia",
      messageType: "text",
      text: "Viejo",
      createdAt: expiredTs,
      updatedAt: expiredTs,
    })
  );
  await assertFails(
    aliceDb.collection("chats").doc("chat_bomb").collection("messages").doc("msg_old").update({
      text: "Intento fuera de tiempo",
      editedAt: pastDate(),
      updatedAt: pastDate(),
    })
  );

  // Author deletes message for both users, scrubbing original text/media
  await assertSucceeds(
    aliceDb.collection("chats").doc("chat_bomb").collection("messages").doc("msg_media_del").set({
      chatId: "chat_bomb",
      participantUids: [ALICE_UID, BOB_UID],
      senderId: ALICE_UID,
      senderName: "Alicia",
      messageType: "media",
      text: "Foto privada",
      mediaId: "med_priv",
      mediaType: "image",
      createdAt: recentTs,
      updatedAt: recentTs,
    })
  );
  await assertFails(
    bobDb.collection("chats").doc("chat_bomb").collection("messages").doc("msg_media_del").update({
      deletedForEveryone: true,
      text: "",
      updatedAt: pastDate(),
    })
  );
  await assertSucceeds(
    aliceDb.collection("chats").doc("chat_bomb").collection("messages").doc("msg_media_del").set({
      chatId: "chat_bomb",
      participantUids: [ALICE_UID, BOB_UID],
      senderId: ALICE_UID,
      senderName: "Alicia",
      messageType: "media",
      deletedForEveryone: true,
      text: "",
      createdAt: recentTs,
      updatedAt: pastDate(),
    })
  );

  // Bomb message: recipient confirms read -> triggers once -> participant can delete after explosion
  await assertSucceeds(
    aliceDb.collection("chats").doc("chat_bomb").collection("messages").doc("msg_bomb").set({
      chatId: "chat_bomb",
      participantUids: [ALICE_UID, BOB_UID],
      senderId: ALICE_UID,
      senderName: "Alicia",
      messageType: "text",
      text: "Secreto bomba",
      bombDurationSec: 10,
      createdAt: recentTs,
      updatedAt: recentTs,
    })
  );
  // Sender cannot self-trigger recipient read countdown
  await assertFails(
    aliceDb.collection("chats").doc("chat_bomb").collection("messages").doc("msg_bomb").update({
      bombTriggeredAt: pastDate(),
      updatedAt: pastDate(),
    })
  );
  // Charlie (non-participant) cannot trigger
  await assertFails(
    charlieDb.collection("chats").doc("chat_bomb").collection("messages").doc("msg_bomb").update({
      bombTriggeredAt: pastDate(),
      updatedAt: pastDate(),
    })
  );
  // Bob triggers bomb countdown once
  await assertSucceeds(
    bobDb.collection("chats").doc("chat_bomb").collection("messages").doc("msg_bomb").update({
      bombTriggeredAt: pastDate(),
      updatedAt: pastDate(),
    })
  );
  // Re-triggering bomb fails
  await assertFails(
    bobDb.collection("chats").doc("chat_bomb").collection("messages").doc("msg_bomb").update({
      bombTriggeredAt: pastDate(),
      updatedAt: pastDate(),
    })
  );
  // Participant can delete triggered bomb message on explosion
  await assertSucceeds(
    bobDb.collection("chats").doc("chat_bomb").collection("messages").doc("msg_bomb").delete()
  );
});

test("10. Owner can sync fcmToken in users_private while others are denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users_private").doc(ALICE_UID).set({
      userId: ALICE_UID,
      readReceipts: true,
      showOnlineStatus: true,
      soundEnabled: true,
      callAlertsEnabled: true,
      sinpeAlertsEnabled: true,
      createdAt: pastDate(),
      updatedAt: pastDate(),
    });
  });

  await assertSucceeds(
    aliceDb.collection("users_private").doc(ALICE_UID).update({
      fcmToken: "fcm_token_sample_abc123",
      updatedAt: pastDate(),
    })
  );

  await assertFails(
    bobDb.collection("users_private").doc(ALICE_UID).update({
      fcmToken: "hijacked_token",
      updatedAt: pastDate(),
    })
  );
});

test("11. Hashed auth_aliases allow exact SHA-256 get for login resolution while rejecting scraping and spoofing", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastDate();
  const validHash = "a".repeat(64);

  // Non-SHA256 doc IDs or unauthenticated writes fail
  await assertFails(
    aliceDb.collection("auth_aliases").doc("user_alicia").set({
      uid: ALICE_UID,
      authEmail: "alicia@example.com",
      entryType: "username",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertFails(
    unauthDb.collection("auth_aliases").doc(validHash).set({
      uid: ALICE_UID,
      authEmail: "alicia@example.com",
      entryType: "username",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Alice writes her own SHA-256 alias
  await assertSucceeds(
    aliceDb.collection("auth_aliases").doc(validHash).set({
      uid: ALICE_UID,
      authEmail: "alicia@example.com",
      entryType: "username",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Exact single-doc get works for cross-device login resolution; list scraping is strictly blocked
  await assertSucceeds(unauthDb.collection("auth_aliases").doc(validHash).get());
  await assertFails(unauthDb.collection("auth_aliases").get());
  await assertFails(bobDb.collection("auth_aliases").get());

  // Bob cannot overwrite or delete Alice's alias
  await assertFails(
    bobDb.collection("auth_aliases").doc(validHash).update({
      authEmail: "evil@example.com",
      entryType: "username",
      updatedAt: pastDate(),
    })
  );
  await assertFails(bobDb.collection("auth_aliases").doc(validHash).delete());

  // Alice can record hasPasswordAuth and authEmail in her private user profile
  await assertSucceeds(
    aliceDb.collection("users_private").doc(ALICE_UID).set({
      userId: ALICE_UID,
      hasPasswordAuth: true,
      authEmail: "alicia@example.com",
      readReceipts: true,
      showOnlineStatus: true,
      soundEnabled: true,
      callAlertsEnabled: true,
      sinpeAlertsEnabled: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );
});

test("12. Chat buzzCounts enforce 2-buzz cap per participant and allow buzz messages", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastDate();

  await assertSucceeds(
    aliceDb.collection("chats").doc("chat_buzz").set({
      participantUids: [ALICE_UID, BOB_UID],
      participantNames: { [ALICE_UID]: "Alicia", [BOB_UID]: "Bob" },
      chatType: "direct",
      lastMessageText: "Chat listo",
      unreadCounts: { [ALICE_UID]: 0, [BOB_UID]: 0 },
      createdBy: ALICE_UID,
      lastMessageAt: ts,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Alice sends buzz #1 and #2
  await assertSucceeds(
    aliceDb.collection("chats").doc("chat_buzz").update({
      buzzCounts: { [ALICE_UID]: 1 },
      lastMessageText: "⚡ ¡Zumbido!",
      lastMessageSenderId: ALICE_UID,
      lastMessageAt: pastDate(),
      unreadCounts: { [ALICE_UID]: 0, [BOB_UID]: 1 },
      updatedAt: pastDate(),
    })
  );
  await assertSucceeds(
    aliceDb.collection("chats").doc("chat_buzz").collection("messages").doc("msg_buzz_1").set({
      chatId: "chat_buzz",
      participantUids: [ALICE_UID, BOB_UID],
      senderId: ALICE_UID,
      senderName: "Alicia",
      messageType: "buzz",
      text: "⚡ ¡Zumbido!",
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );
  await assertSucceeds(
    aliceDb.collection("chats").doc("chat_buzz").update({
      buzzCounts: { [ALICE_UID]: 2 },
      lastMessageText: "⚡ ¡Zumbido!",
      lastMessageSenderId: ALICE_UID,
      lastMessageAt: pastDate(),
      unreadCounts: { [ALICE_UID]: 0, [BOB_UID]: 2 },
      updatedAt: pastDate(),
    })
  );

  // Alice cannot exceed 2 buzzes or reset her own counter
  await assertFails(
    aliceDb.collection("chats").doc("chat_buzz").update({
      buzzCounts: { [ALICE_UID]: 3 },
      updatedAt: pastDate(),
    })
  );
  await assertFails(
    aliceDb.collection("chats").doc("chat_buzz").update({
      buzzCounts: { [ALICE_UID]: 0 },
      updatedAt: pastDate(),
    })
  );

  // Bob cannot tamper with Alice's buzz count, only increment his own
  await assertFails(
    bobDb.collection("chats").doc("chat_buzz").update({
      buzzCounts: { [ALICE_UID]: 1, [BOB_UID]: 1 },
      updatedAt: pastDate(),
    })
  );
  await assertSucceeds(
    bobDb.collection("chats").doc("chat_buzz").update({
      buzzCounts: { [ALICE_UID]: 2, [BOB_UID]: 1 },
      updatedAt: pastDate(),
    })
  );
});

test("13. Global daily buzz limit (2/day on users_private) enforces same-day cap and resets next day", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = pastDate();

  await assertSucceeds(
    aliceDb.collection("users_private").doc(ALICE_UID).set({
      userId: ALICE_UID,
      readReceipts: true,
      showOnlineStatus: true,
      soundEnabled: true,
      callAlertsEnabled: true,
      sinpeAlertsEnabled: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Day 1: buzz 1 and 2 succeed; buzz 3 is denied
  await assertSucceeds(
    aliceDb.collection("users_private").doc(ALICE_UID).update({
      buzzDailyDate: "2026-11-19",
      buzzDailyCount: 1,
      updatedAt: pastDate(),
    })
  );
  await assertSucceeds(
    aliceDb.collection("users_private").doc(ALICE_UID).update({
      buzzDailyDate: "2026-11-19",
      buzzDailyCount: 2,
      updatedAt: pastDate(),
    })
  );
  await assertFails(
    aliceDb.collection("users_private").doc(ALICE_UID).update({
      buzzDailyDate: "2026-11-19",
      buzzDailyCount: 3,
      updatedAt: pastDate(),
    })
  );
  // Same day cannot reset back to 0 or 1
  await assertFails(
    aliceDb.collection("users_private").doc(ALICE_UID).update({
      buzzDailyDate: "2026-11-19",
      buzzDailyCount: 1,
      updatedAt: pastDate(),
    })
  );

  // Day 2: new calendar date resets count cleanly to 1
  await assertSucceeds(
    aliceDb.collection("users_private").doc(ALICE_UID).update({
      buzzDailyDate: "2026-11-20",
      buzzDailyCount: 1,
      updatedAt: pastDate(),
    })
  );
});

test("14. Post & comment deletion allow comment author and post author while rejecting outsiders", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const ts = pastDate();

  await assertSucceeds(
    bobDb.collection("users").doc(BOB_UID).collection("contacts").doc(ALICE_UID).set({
      contactUid: ALICE_UID,
      displayName: "Alicia",
      username: "alicia_cr",
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    charlieDb.collection("users").doc(CHARLIE_UID).collection("contacts").doc(ALICE_UID).set({
      contactUid: ALICE_UID,
      displayName: "Alicia",
      username: "alicia_cr",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("posts").doc("post_del").set({
      authorId: ALICE_UID,
      authorName: "Alicia",
      authorUsername: "alicia_cr",
      authorVerified: true,
      text: "Mi publicación",
      hashtags: [],
      mediaType: "none",
      likedBy: [],
      commentsCount: 2,
      shareCount: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    bobDb.collection("posts").doc("post_del").collection("comments").doc("c_bob").set({
      postId: "post_del",
      authorId: BOB_UID,
      authorName: "Bob",
      authorUsername: "bob_cr",
      text: "Comentario de Bob",
      createdAt: pastDate(),
    })
  );
  await assertSucceeds(
    charlieDb.collection("posts").doc("post_del").collection("comments").doc("c_charlie").set({
      postId: "post_del",
      authorId: CHARLIE_UID,
      authorName: "Charlie",
      authorUsername: "charlie_cr",
      text: "Comentario de Charlie",
      createdAt: pastDate(),
    })
  );

  // Charlie cannot delete Bob's comment or Alice's post
  await assertFails(charlieDb.collection("posts").doc("post_del").collection("comments").doc("c_bob").delete());
  await assertFails(charlieDb.collection("posts").doc("post_del").delete());

  // Bob can delete his own comment and decrement commentsCount by 1
  await assertSucceeds(bobDb.collection("posts").doc("post_del").collection("comments").doc("c_bob").delete());
  await assertSucceeds(
    bobDb.collection("posts").doc("post_del").update({
      commentsCount: 1,
      updatedAt: pastDate(),
    })
  );

  // Alice (post owner) can also delete Charlie's comment and decrement commentsCount by 1, then delete her post
  await assertSucceeds(aliceDb.collection("posts").doc("post_del").collection("comments").doc("c_charlie").delete());
  await assertSucceeds(
    aliceDb.collection("posts").doc("post_del").update({
      commentsCount: 0,
      updatedAt: pastDate(),
    })
  );
  await assertSucceeds(aliceDb.collection("posts").doc("post_del").delete());
});

test("15. Per-user chat deletion hides for deleter and purges all messages when all participants delete", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const ts = pastDate();

  await assertSucceeds(
    aliceDb.collection("chats").doc("chat_wipe").set({
      participantUids: [ALICE_UID, BOB_UID],
      participantNames: { [ALICE_UID]: "Alicia", [BOB_UID]: "Bob" },
      chatType: "direct",
      lastMessageText: "Hola",
      unreadCounts: { [ALICE_UID]: 0, [BOB_UID]: 0 },
      createdBy: ALICE_UID,
      lastMessageAt: ts,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("chats").doc("chat_wipe").collection("messages").doc("m_alice").set({
      chatId: "chat_wipe",
      participantUids: [ALICE_UID, BOB_UID],
      senderId: ALICE_UID,
      senderName: "Alicia",
      messageType: "text",
      text: "Mensaje de Alicia",
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    bobDb.collection("chats").doc("chat_wipe").collection("messages").doc("m_bob").set({
      chatId: "chat_wipe",
      participantUids: [ALICE_UID, BOB_UID],
      senderId: BOB_UID,
      senderName: "Bob",
      messageType: "text",
      text: "Mensaje de Bob",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Alice hides/clears the chat for herself; Bob cannot yet delete Alice's message
  await assertSucceeds(
    aliceDb.collection("chats").doc("chat_wipe").update({
      hiddenForUids: [ALICE_UID],
      clearedBeforeAt: { [ALICE_UID]: pastDate() },
      unreadCounts: { [ALICE_UID]: 0, [BOB_UID]: 0 },
      updatedAt: pastDate(),
    })
  );
  await assertFails(
    bobDb.collection("chats").doc("chat_wipe").collection("messages").doc("m_alice").delete()
  );

  // Bob also deletes the chat -> all participants are in hiddenForUids -> Bob can purge every message & the chat doc
  await assertSucceeds(
    bobDb.collection("chats").doc("chat_wipe").update({
      hiddenForUids: [ALICE_UID, BOB_UID],
      clearedBeforeAt: { [ALICE_UID]: ts, [BOB_UID]: pastDate() },
      unreadCounts: { [ALICE_UID]: 0, [BOB_UID]: 0 },
      updatedAt: pastDate(),
    })
  );
  await assertFails(
    charlieDb.collection("chats").doc("chat_wipe").collection("messages").doc("m_alice").delete()
  );
  await assertSucceeds(
    bobDb.collection("chats").doc("chat_wipe").collection("messages").doc("m_alice").delete()
  );
  await assertSucceeds(
    bobDb.collection("chats").doc("chat_wipe").collection("messages").doc("m_bob").delete()
  );
  await assertSucceeds(
    bobDb.collection("chats").doc("chat_wipe").delete()
  );
});

test("16. Chat message custom reactions allow valid 7-key participant toggles and reject spoofing or invalid keys", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const ts = pastDate();

  await assertSucceeds(
    aliceDb.collection("chats").doc("chat_react").set({
      participantUids: [ALICE_UID, BOB_UID],
      participantNames: { [ALICE_UID]: "Alicia", [BOB_UID]: "Bob" },
      chatType: "direct",
      lastMessageText: "Hola pura vida",
      unreadCounts: { [ALICE_UID]: 0, [BOB_UID]: 1 },
      createdBy: ALICE_UID,
      lastMessageAt: ts,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("chats").doc("chat_react").collection("messages").doc("msg_r1").set({
      chatId: "chat_react",
      participantUids: [ALICE_UID, BOB_UID],
      senderId: ALICE_UID,
      senderName: "Alicia",
      messageType: "text",
      text: "Vamos por un café",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Bob adds "pura_vida" using dot-notation field update (mirroring FieldValue.arrayUnion)
  await assertSucceeds(
    bobDb.collection("chats").doc("chat_react").collection("messages").doc("msg_r1").update({
      "reactions.pura_vida": [BOB_UID],
      updatedAt: pastDate(),
    })
  );

  // Alice also adds "pura_vida" first, then switches atomically to "que_chiva" (2 affected keys)
  await assertSucceeds(
    aliceDb.collection("chats").doc("chat_react").collection("messages").doc("msg_r1").update({
      "reactions.pura_vida": [BOB_UID, ALICE_UID],
      updatedAt: pastDate(),
    })
  );
  await assertSucceeds(
    aliceDb.collection("chats").doc("chat_react").collection("messages").doc("msg_r1").update({
      "reactions.pura_vida": [BOB_UID],
      "reactions.que_chiva": [ALICE_UID],
      updatedAt: pastDate(),
    })
  );

  // Bob removes his own "pura_vida" vote while leaving map valid
  await assertSucceeds(
    bobDb.collection("chats").doc("chat_react").collection("messages").doc("msg_r1").update({
      "reactions.pura_vida": [],
      updatedAt: pastDate(),
    })
  );

  // Participant can also toggle the 7th custom reaction "que_rajado"
  await assertSucceeds(
    bobDb.collection("chats").doc("chat_react").collection("messages").doc("msg_r1").update({
      "reactions.que_rajado": [BOB_UID],
      updatedAt: pastDate(),
    })
  );

  // Reject unapproved reaction key
  await assertFails(
    bobDb.collection("chats").doc("chat_react").collection("messages").doc("msg_r1").update({
      "reactions.desconocida": [BOB_UID],
      updatedAt: pastDate(),
    })
  );

  // Reject removing or spoofing another user's UID
  await assertFails(
    bobDb.collection("chats").doc("chat_react").collection("messages").doc("msg_r1").update({
      "reactions.que_chiva": [],
      updatedAt: pastDate(),
    })
  );
  await assertFails(
    bobDb.collection("chats").doc("chat_react").collection("messages").doc("msg_r1").update({
      "reactions.que_chicha": [ALICE_UID],
      updatedAt: pastDate(),
    })
  );

  // Reject outsider reacting
  await assertFails(
    charlieDb.collection("chats").doc("chat_react").collection("messages").doc("msg_r1").update({
      "reactions.de_una": [CHARLIE_UID],
      updatedAt: pastDate(),
    })
  );

  // Deleted messages cannot receive new reactions
  await assertSucceeds(
    aliceDb.collection("chats").doc("chat_react").collection("messages").doc("msg_r1").update({
      deletedForEveryone: true,
      text: "",
      updatedAt: pastDate(),
    })
  );
  await assertFails(
    bobDb.collection("chats").doc("chat_react").collection("messages").doc("msg_r1").update({
      "reactions.buena_vibra": [BOB_UID],
      updatedAt: pastDate(),
    })
  );
});

test("17. Document, location, contact, and live poll messages enforce participant isolation and valid schemas", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const ts = pastDate();

  await assertSucceeds(
    aliceDb.collection("chats").doc("chat_rich").set({
      participantUids: [ALICE_UID, BOB_UID],
      participantNames: { [ALICE_UID]: "Alicia", [BOB_UID]: "Bob" },
      chatType: "direct",
      lastMessageText: "Adjunto",
      unreadCounts: { [ALICE_UID]: 0, [BOB_UID]: 0 },
      createdBy: ALICE_UID,
      lastMessageAt: ts,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("chats").doc("chat_rich").collection("messages").doc("msg_doc").set({
      chatId: "chat_rich",
      participantUids: [ALICE_UID, BOB_UID],
      senderId: ALICE_UID,
      senderName: "Alicia",
      messageType: "document",
      text: "Factura_CR.pdf",
      mediaId: "med_pdf_1",
      documentName: "Factura_CR.pdf",
      documentMimeType: "application/pdf",
      documentSizeBytes: 24500,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("chats").doc("chat_rich").collection("messages").doc("msg_loc").set({
      chatId: "chat_rich",
      participantUids: [ALICE_UID, BOB_UID],
      senderId: ALICE_UID,
      senderName: "Alicia",
      messageType: "location",
      text: "San José, Costa Rica",
      locationLat: 9.9281,
      locationLng: -84.0907,
      locationLabel: "San José, Costa Rica",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    bobDb.collection("chats").doc("chat_rich").collection("messages").doc("msg_contact").set({
      chatId: "chat_rich",
      participantUids: [ALICE_UID, BOB_UID],
      senderId: BOB_UID,
      senderName: "Bob",
      messageType: "contact",
      text: "Alicia (@alicia_cr)",
      sharedContactUid: ALICE_UID,
      sharedContactName: "Alicia",
      sharedContactUsername: "alicia_cr",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("chats").doc("chat_rich").collection("messages").doc("msg_poll").set({
      chatId: "chat_rich",
      participantUids: [ALICE_UID, BOB_UID],
      senderId: ALICE_UID,
      senderName: "Alicia",
      messageType: "poll",
      text: "¿Dónde comemos?",
      pollQuestion: "¿Dónde comemos?",
      pollOptions: ["Soda típica", "Café", "Pizza"],
      pollVotes: {},
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Bob votes option 0, then switches atomically to option 1
  await assertSucceeds(
    bobDb.collection("chats").doc("chat_rich").collection("messages").doc("msg_poll").update({
      "pollVotes.0": [BOB_UID],
      updatedAt: pastDate(),
    })
  );
  await assertSucceeds(
    bobDb.collection("chats").doc("chat_rich").collection("messages").doc("msg_poll").update({
      "pollVotes.0": [],
      "pollVotes.1": [BOB_UID],
      updatedAt: pastDate(),
    })
  );

  // Outsider Charlie cannot vote on poll or create attachments in chat_rich
  await assertFails(
    charlieDb.collection("chats").doc("chat_rich").collection("messages").doc("msg_poll").update({
      "pollVotes.1": [BOB_UID, CHARLIE_UID],
      updatedAt: pastDate(),
    })
  );
  await assertFails(
    charlieDb.collection("chats").doc("chat_rich").collection("messages").doc("msg_loc2").set({
      chatId: "chat_rich",
      participantUids: [ALICE_UID, BOB_UID, CHARLIE_UID],
      senderId: CHARLIE_UID,
      senderName: "Charlie",
      messageType: "location",
      text: "Hack",
      locationLat: 9.9,
      locationLng: -84.0,
      createdAt: ts,
      updatedAt: ts,
    })
  );
});

test("18. SINPE privacy protects bank/phone via /sinpe_profiles and aiSummaryTopic works on summary messages", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const ts = pastDate();

  // Public profile & directory lookup succeed WITHOUT sinpeBank when sinpeVisibility is contacts_only
  await assertSucceeds(
    aliceDb.collection("users_public").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alicia",
      username: "alicia_cr",
      quickStatus: "Disponible",
      sinpeVisibility: "contacts_only",
      verified: true,
      postsCount: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    aliceDb.collection("directory_lookup").doc("user_alicia_cr").set({
      uid: ALICE_UID,
      displayName: "Alicia",
      username: "alicia_cr",
      sinpeVisibility: "contacts_only",
      entryType: "username",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Alice creates her access-controlled SINPE profile (contacts_only, initially approvedUids empty)
  await assertSucceeds(
    aliceDb.collection("sinpe_profiles").doc(ALICE_UID).set({
      userId: ALICE_UID,
      visibility: "contacts_only",
      sinpeBank: "BAC Credomatic",
      sinpePhone: "+50688887777",
      approvedUids: [],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Alice can read her own sinpe_profile; Bob & Charlie cannot yet read it, and list scraping is always denied
  await assertSucceeds(aliceDb.collection("sinpe_profiles").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("sinpe_profiles").doc(ALICE_UID).get());
  await assertFails(charlieDb.collection("sinpe_profiles").doc(ALICE_UID).get());
  await assertFails(charlieDb.collection("sinpe_profiles").get());

  // Bob cannot add himself to Alice's approvedUids
  await assertFails(
    bobDb.collection("sinpe_profiles").doc(ALICE_UID).update({
      approvedUids: [BOB_UID],
      updatedAt: pastDate(),
    })
  );

  // Alice accepts Bob -> adds BOB_UID to approvedUids -> Bob can read, stranger Charlie still denied
  await assertSucceeds(
    aliceDb.collection("sinpe_profiles").doc(ALICE_UID).update({
      approvedUids: [BOB_UID],
      updatedAt: pastDate(),
    })
  );
  await assertSucceeds(bobDb.collection("sinpe_profiles").doc(ALICE_UID).get());
  await assertFails(charlieDb.collection("sinpe_profiles").doc(ALICE_UID).get());

  // Alice switches visibility to hidden -> even Bob in approvedUids is denied
  await assertSucceeds(
    aliceDb.collection("sinpe_profiles").doc(ALICE_UID).update({
      visibility: "hidden",
      updatedAt: pastDate(),
    })
  );
  await assertFails(bobDb.collection("sinpe_profiles").doc(ALICE_UID).get());
  await assertFails(charlieDb.collection("sinpe_profiles").doc(ALICE_UID).get());

  // Alice switches visibility to everyone -> Charlie can now read
  await assertSucceeds(
    aliceDb.collection("sinpe_profiles").doc(ALICE_UID).update({
      visibility: "everyone",
      updatedAt: pastDate(),
    })
  );
  await assertSucceeds(charlieDb.collection("sinpe_profiles").doc(ALICE_UID).get());

  // ai_summary message supports aiSummaryTopic and clears it on deleteForEveryone
  await assertSucceeds(
    aliceDb.collection("chats").doc("chat_sum").set({
      participantUids: [ALICE_UID, BOB_UID],
      participantNames: { [ALICE_UID]: "Alicia", [BOB_UID]: "Bob" },
      chatType: "direct",
      lastMessageText: "Resumen del chat",
      unreadCounts: { [ALICE_UID]: 0, [BOB_UID]: 1 },
      createdBy: ALICE_UID,
      lastMessageAt: ts,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    aliceDb.collection("chats").doc("chat_sum").collection("messages").doc("msg_sum").set({
      chatId: "chat_sum",
      participantUids: [ALICE_UID, BOB_UID],
      senderId: ALICE_UID,
      senderName: "Alicia",
      messageType: "ai_summary",
      text: "Resumen del chat · Cena del viernes",
      aiSummaryTopic: "Cena del viernes",
      aiSummaryMain: "Coordinación de cena y SINPE.",
      aiSummaryDecisions: ["Viernes 8pm"],
      aiSummaryTasks: ["Reservar mesa"],
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    aliceDb.collection("chats").doc("chat_sum").collection("messages").doc("msg_sum").set({
      chatId: "chat_sum",
      participantUids: [ALICE_UID, BOB_UID],
      senderId: ALICE_UID,
      senderName: "Alicia",
      messageType: "ai_summary",
      deletedForEveryone: true,
      text: "",
      createdAt: ts,
      updatedAt: pastDate(),
    })
  );
});

test("19. Private savedPostIds syncs for owner only and multi-member group chats allow adding participants", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const ts = pastDate();

  await assertSucceeds(
    aliceDb.collection("users_private").doc(ALICE_UID).set({
      userId: ALICE_UID,
      readReceipts: true,
      showOnlineStatus: true,
      soundEnabled: true,
      callAlertsEnabled: true,
      sinpeAlertsEnabled: true,
      savedPostIds: ["post_1", "post_2"],
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    aliceDb.collection("users_private").doc(ALICE_UID).update({
      savedPostIds: ["post_1", "post_2", "post_3"],
      updatedAt: pastDate(),
    })
  );
  await assertFails(
    bobDb.collection("users_private").doc(ALICE_UID).update({
      savedPostIds: [],
      updatedAt: pastDate(),
    })
  );

  // Create group with 3+ members and add a 4th member later
  await assertSucceeds(
    aliceDb.collection("chats").doc("chat_grp_multi").set({
      participantUids: [ALICE_UID, BOB_UID, CHARLIE_UID],
      participantNames: { [ALICE_UID]: "Alicia", [BOB_UID]: "Bob", [CHARLIE_UID]: "Charlie" },
      participantAvatars: {},
      chatType: "group",
      title: "Paseo CR",
      lastMessageText: "Bienvenidos",
      unreadCounts: { [ALICE_UID]: 0, [BOB_UID]: 0, [CHARLIE_UID]: 0 },
      createdBy: ALICE_UID,
      lastMessageAt: ts,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    bobDb.collection("chats").doc("chat_grp_multi").update({
      participantUids: [ALICE_UID, BOB_UID, CHARLIE_UID, "user_diana"],
      participantNames: {
        [ALICE_UID]: "Alicia",
        [BOB_UID]: "Bob",
        [CHARLIE_UID]: "Charlie",
        user_diana: "Diana",
      },
      unreadCounts: { [ALICE_UID]: 0, [BOB_UID]: 0, [CHARLIE_UID]: 0, user_diana: 0 },
      updatedAt: pastDate(),
    })
  );
});

test("20. Group calls support multi-participant signaling, targeted ICE, join/decline, and outsider denial", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const daveDb = testEnv.authenticatedContext("user_dave").firestore();
  const ts = pastDate();

  await assertSucceeds(
    aliceDb.collection("calls").doc("grp_call_1").set({
      callerId: ALICE_UID,
      callerName: "Alicia",
      calleeId: "chat_grp_multi",
      calleeName: "Paseo CR",
      participantUids: [ALICE_UID, BOB_UID, CHARLIE_UID],
      callType: "video",
      status: "ringing",
      durationSeconds: 0,
      groupCall: true,
      chatId: "chat_grp_multi",
      groupTitle: "Paseo CR",
      activeParticipantUids: [ALICE_UID],
      declinedUids: [],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Outsider cannot read or signal on group call
  await assertFails(daveDb.collection("calls").doc("grp_call_1").get());
  await assertFails(
    daveDb.collection("calls").doc("grp_call_1").collection("signals").doc("sig_bad").set({
      participantUids: [ALICE_UID, BOB_UID, CHARLIE_UID, "user_dave"],
      fromUid: "user_dave",
      toUid: ALICE_UID,
      type: "offer",
      sdp: "v=0",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Bob joins active group call, Charlie declines
  await assertSucceeds(
    bobDb.collection("calls").doc("grp_call_1").update({
      status: "active",
      activeParticipantUids: [ALICE_UID, BOB_UID],
      updatedAt: pastDate(),
    })
  );
  await assertSucceeds(
    charlieDb.collection("calls").doc("grp_call_1").update({
      declinedUids: [CHARLIE_UID],
      updatedAt: pastDate(),
    })
  );

  // Pairwise signal & targeted ICE between Alice and Bob succeed
  await assertSucceeds(
    aliceDb.collection("calls").doc("grp_call_1").collection("signals").doc(`${ALICE_UID}__${BOB_UID}`).set({
      participantUids: [ALICE_UID, BOB_UID, CHARLIE_UID],
      fromUid: ALICE_UID,
      toUid: BOB_UID,
      type: "offer",
      sdp: "v=0\r\na=group",
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    aliceDb.collection("calls").doc("grp_call_1").collection("iceCandidates").doc("ice_grp_1").set({
      participantUids: [ALICE_UID, BOB_UID, CHARLIE_UID],
      senderId: ALICE_UID,
      targetUid: BOB_UID,
      sdpMid: "0",
      sdpMLineIndex: 0,
      sdpCandidate: "candidate:1 1 UDP 2122252543 192.168.1.2 50000 typ host",
      createdAt: ts,
    })
  );

  // Ending group call locks further status updates
  await assertSucceeds(
    aliceDb.collection("calls").doc("grp_call_1").update({
      status: "ended",
      activeParticipantUids: [],
      durationSeconds: 42,
      updatedAt: pastDate(),
    })
  );
  await assertFails(
    bobDb.collection("calls").doc("grp_call_1").update({
      status: "active",
      activeParticipantUids: [BOB_UID],
      updatedAt: pastDate(),
    })
  );
});

test("21. Real-time live location sharing enforces 15-user group cap, participant isolation, and PrivateUser privacy fields", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const outsiderDb = testEnv.authenticatedContext("user_outsider").firestore();
  const ts = pastDate();

  // Owner updates blockedPhones & live location privacy controls in users_private; outsider cannot
  await assertSucceeds(
    aliceDb.collection("users_private").doc(ALICE_UID).set({
      userId: ALICE_UID,
      blockedUids: [CHARLIE_UID],
      blockedPhones: ["88990011"],
      liveLocationScope: "contacts_only",
      liveLocationDurationMin: 60,
      liveLocationShowDistance: true,
      readReceipts: true,
      showOnlineStatus: true,
      soundEnabled: true,
      callAlertsEnabled: true,
      sinpeAlertsEnabled: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertFails(
    aliceDb.collection("users_private").doc(ALICE_UID).update({
      liveLocationScope: "invalid_scope",
      updatedAt: pastDate(),
    })
  );
  await assertFails(
    bobDb.collection("users_private").doc(ALICE_UID).update({
      blockedPhones: [],
      updatedAt: pastDate(),
    })
  );

  // Create a group chat with 16 participants
  const members16 = [ALICE_UID, BOB_UID, CHARLIE_UID];
  const names16 = { [ALICE_UID]: "Alicia", [BOB_UID]: "Bob", [CHARLIE_UID]: "Charlie" };
  const unreads16 = { [ALICE_UID]: 0, [BOB_UID]: 0, [CHARLIE_UID]: 0 };
  for (let i = 4; i <= 16; i++) {
    const uid = `member_${i}`;
    members16.push(uid);
    names16[uid] = `Miembro ${i}`;
    unreads16[uid] = 0;
  }

  await assertSucceeds(
    aliceDb.collection("chats").doc("grp_loc_16").set({
      participantUids: members16,
      participantNames: names16,
      participantAvatars: {},
      chatType: "group",
      title: "Ruta Volcán Poás",
      lastMessageText: "Grupo listo",
      unreadCounts: unreads16,
      liveLocationUids: [],
      createdBy: ALICE_UID,
      lastMessageAt: ts,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // 15 participants can share location sequentially
  const currentLive = [];
  for (let i = 0; i < 15; i++) {
    const uid = members16[i];
    const userDb = testEnv.authenticatedContext(uid).firestore();
    currentLive.push(uid);
    await assertSucceeds(
      userDb.collection("chats").doc("grp_loc_16").update({
        liveLocationUids: [...currentLive],
        updatedAt: pastDate(),
      })
    );
    await assertSucceeds(
      userDb.collection("chats").doc("grp_loc_16").collection("live_locations").doc(uid).set({
        userId: uid,
        chatId: "grp_loc_16",
        participantUids: members16,
        displayName: names16[uid],
        latitude: 10.197 + i * 0.001,
        longitude: -84.230 - i * 0.001,
        accuracyMeters: 12,
        active: true,
        expiresAt: futureDate(60 * 60 * 1000),
        updatedAt: pastDate(),
      })
    );
  }

  // 16th participant exceeds the 15-user cap and MUST be rejected on both chat array and subcollection
  const user16Uid = members16[15];
  const user16Db = testEnv.authenticatedContext(user16Uid).firestore();
  await assertFails(
    user16Db.collection("chats").doc("grp_loc_16").update({
      liveLocationUids: [...currentLive, user16Uid],
      updatedAt: pastDate(),
    })
  );
  await assertFails(
    user16Db.collection("chats").doc("grp_loc_16").collection("live_locations").doc(user16Uid).set({
      userId: user16Uid,
      chatId: "grp_loc_16",
      participantUids: members16,
      displayName: "Miembro 16",
      latitude: 10.22,
      longitude: -84.25,
      accuracyMeters: 10,
      active: true,
      expiresAt: futureDate(60 * 60 * 1000),
      updatedAt: pastDate(),
    })
  );

  // Participants can read/list live_locations; outsider is denied; Bob cannot spoof Alice's location
  await assertSucceeds(bobDb.collection("chats").doc("grp_loc_16").collection("live_locations").get());
  await assertFails(outsiderDb.collection("chats").doc("grp_loc_16").collection("live_locations").get());
  await assertFails(
    bobDb.collection("chats").doc("grp_loc_16").collection("live_locations").doc(ALICE_UID).set({
      userId: ALICE_UID,
      chatId: "grp_loc_16",
      participantUids: members16,
      displayName: "Alicia",
      latitude: 9.9,
      longitude: -84.1,
      accuracyMeters: 15,
      active: true,
      expiresAt: futureDate(60 * 60 * 1000),
      updatedAt: pastDate(),
    })
  );

  // If Alice stops sharing (14 active), user16 can now join and reaches 15
  const afterAliceLeft = currentLive.filter((u) => u !== ALICE_UID);
  await assertSucceeds(
    aliceDb.collection("chats").doc("grp_loc_16").update({
      liveLocationUids: afterAliceLeft,
      updatedAt: pastDate(),
    })
  );
  await assertSucceeds(
    aliceDb.collection("chats").doc("grp_loc_16").collection("live_locations").doc(ALICE_UID).delete()
  );
  await assertSucceeds(
    user16Db.collection("chats").doc("grp_loc_16").update({
      liveLocationUids: [...afterAliceLeft, user16Uid],
      updatedAt: pastDate(),
    })
  );
  await assertSucceeds(
    user16Db.collection("chats").doc("grp_loc_16").collection("live_locations").doc(user16Uid).set({
      userId: user16Uid,
      chatId: "grp_loc_16",
      participantUids: members16,
      displayName: "Miembro 16",
      latitude: 10.22,
      longitude: -84.25,
      accuracyMeters: 10,
      active: true,
      expiresAt: futureDate(60 * 60 * 1000),
      updatedAt: pastDate(),
    })
  );
});

test("22. Group leave down to 1 participant and creator-only full group deletion", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const ts = pastDate();

  await assertSucceeds(
    aliceDb.collection("chats").doc("grp_manage").set({
      participantUids: [ALICE_UID, BOB_UID, CHARLIE_UID],
      participantNames: { [ALICE_UID]: "Alicia", [BOB_UID]: "Bob", [CHARLIE_UID]: "Charlie" },
      participantAvatars: {},
      chatType: "group",
      title: "Equipo VYB",
      lastMessageText: "Hola equipo",
      unreadCounts: { [ALICE_UID]: 0, [BOB_UID]: 0, [CHARLIE_UID]: 0 },
      createdBy: ALICE_UID,
      lastMessageAt: ts,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    bobDb.collection("chats").doc("grp_manage").collection("messages").doc("m_bob_1").set({
      chatId: "grp_manage",
      participantUids: [ALICE_UID, BOB_UID, CHARLIE_UID],
      senderId: BOB_UID,
      senderName: "Bob",
      messageType: "text",
      text: "Mensaje de Bob",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Non-creator Bob cannot delete group chat directly
  await assertFails(bobDb.collection("chats").doc("grp_manage").delete());

  // Charlie leaves group cleanly (3 -> 2), then Bob leaves group (2 -> 1)
  await assertSucceeds(
    charlieDb.collection("chats").doc("grp_manage").update({
      participantUids: [ALICE_UID, BOB_UID],
      participantNames: { [ALICE_UID]: "Alicia", [BOB_UID]: "Bob" },
      participantAvatars: {},
      unreadCounts: { [ALICE_UID]: 0, [BOB_UID]: 0 },
      updatedAt: pastDate(),
    })
  );
  await assertSucceeds(
    bobDb.collection("chats").doc("grp_manage").update({
      participantUids: [ALICE_UID],
      participantNames: { [ALICE_UID]: "Alicia" },
      participantAvatars: {},
      unreadCounts: { [ALICE_UID]: 0 },
      updatedAt: pastDate(),
    })
  );

  // Creator Alice can delete all messages (including Bob's) and delete the group document
  await assertSucceeds(
    aliceDb.collection("chats").doc("grp_manage").collection("messages").doc("m_bob_1").delete()
  );
  await assertSucceeds(aliceDb.collection("chats").doc("grp_manage").delete());
});

test("23. Removing a contact immediately revokes access to their posts and stories", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastDate();

  await assertSucceeds(
    aliceDb.collection("posts").doc("post_priv").set({
      authorId: ALICE_UID,
      authorName: "Alicia",
      authorUsername: "alicia_cr",
      authorVerified: true,
      text: "Solo para mis contactos",
      hashtags: ["privado"],
      mediaType: "none",
      likedBy: [],
      commentsCount: 0,
      shareCount: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("stories").doc("story_priv").set({
      authorId: ALICE_UID,
      authorName: "Alicia",
      authorUsername: "alicia_cr",
      authorVerified: true,
      mediaType: "image",
      caption: "Historia privada",
      viewerUids: [],
      active: true,
      expiresAt: futureDate(),
      createdAt: ts,
    })
  );

  // Bob adds Alicia -> can read post and story via per-author equality queries
  await assertSucceeds(
    bobDb.collection("users").doc(BOB_UID).collection("contacts").doc(ALICE_UID).set({
      contactUid: ALICE_UID,
      displayName: "Alicia",
      username: "alicia_cr",
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(bobDb.collection("posts").where("authorId", "==", BOB_UID).limit(30).get());
  await assertSucceeds(bobDb.collection("posts").where("authorId", "==", ALICE_UID).limit(30).get());
  await assertSucceeds(bobDb.collection("stories").where("authorId", "==", BOB_UID).limit(60).get());
  await assertSucceeds(bobDb.collection("stories").where("authorId", "==", ALICE_UID).limit(60).get());

  // Bob removes Alicia -> all access to Alicia's posts and stories is revoked immediately
  await assertSucceeds(
    bobDb.collection("users").doc(BOB_UID).collection("contacts").doc(ALICE_UID).delete()
  );
  await assertFails(bobDb.collection("posts").doc("post_priv").get());
  await assertFails(bobDb.collection("posts").where("authorId", "==", ALICE_UID).limit(30).get());
  await assertFails(bobDb.collection("stories").doc("story_priv").get());
  await assertFails(bobDb.collection("stories").where("authorId", "==", ALICE_UID).limit(60).get());
  // Bob can still query his own posts & stories cleanly
  await assertSucceeds(bobDb.collection("posts").where("authorId", "==", BOB_UID).limit(30).get());
  await assertSucceeds(bobDb.collection("stories").where("authorId", "==", BOB_UID).limit(60).get());
});

test("24. Chunked media uploads (vybmedia_files) enforce owner writes, scope reads, and missing-doc get safety", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastDate();

  // Missing media doc returns cleanly for signed-in users, denied for unauthenticated users
  await assertSucceeds(bobDb.collection("vybmedia_files").doc("med_missing_001").get());
  await assertFails(unauthDb.collection("vybmedia_files").doc("med_missing_001").get());

  // Alice uploads a public post/story media metadata + chunk
  await assertSucceeds(
    aliceDb.collection("vybmedia_files").doc("med_post_123").set({
      ownerId: ALICE_UID,
      mimeType: "video/mp4",
      totalChunks: 1,
      totalBytes: 1024,
      scope: "public",
      allowedUids: [ALICE_UID],
      createdAt: ts,
    })
  );
  await assertSucceeds(
    aliceDb.collection("vybmedia_files").doc("med_post_123").collection("chunks").doc("chunk_0").set({
      ownerId: ALICE_UID,
      scope: "public",
      allowedUids: [ALICE_UID],
      chunkIndex: 0,
      dataBase64: "AAAA",
      createdAt: ts,
    })
  );

  await assertSucceeds(bobDb.collection("vybmedia_files").doc("med_post_123").get());
  await assertSucceeds(bobDb.collection("vybmedia_files").doc("med_post_123").collection("chunks").doc("chunk_0").get());
  await assertFails(bobDb.collection("vybmedia_files").doc("med_post_123").delete());
});

test("25. Chat and message delivery/read ACKs (lastDeliveredAt, deliveredTo, readBy) enforce self-only updates", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const ts = pastDate();

  await assertSucceeds(
    aliceDb.collection("chats").doc("dm_ack_1").set({
      participantUids: [ALICE_UID, BOB_UID],
      participantNames: { [ALICE_UID]: "Alicia", [BOB_UID]: "Bob" },
      chatType: "direct",
      lastMessageText: "Hola Bob",
      lastMessageSenderId: ALICE_UID,
      unreadCounts: { [ALICE_UID]: 0, [BOB_UID]: 1 },
      lastDeliveredAt: { [ALICE_UID]: ts },
      lastReadAt: { [ALICE_UID]: ts },
      createdBy: ALICE_UID,
      lastMessageAt: ts,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Sender initializes deliveredTo & readBy with self UID only
  await assertSucceeds(
    aliceDb.collection("chats").doc("dm_ack_1").collection("messages").doc("msg_ack_1").set({
      chatId: "dm_ack_1",
      participantUids: [ALICE_UID, BOB_UID],
      senderId: ALICE_UID,
      senderName: "Alicia",
      messageType: "text",
      text: "Hola Bob",
      deliveredTo: [ALICE_UID],
      readBy: [ALICE_UID],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Alice cannot pre-mark Bob as delivered on message creation
  await assertFails(
    aliceDb.collection("chats").doc("dm_ack_1").collection("messages").doc("msg_spoof").set({
      chatId: "dm_ack_1",
      participantUids: [ALICE_UID, BOB_UID],
      senderId: ALICE_UID,
      senderName: "Alicia",
      messageType: "text",
      text: "Spoof",
      deliveredTo: [ALICE_UID, BOB_UID],
      readBy: [ALICE_UID],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Bob acknowledges delivery on chat & message
  const ackTs = pastDate();
  await assertSucceeds(
    bobDb.collection("chats").doc("dm_ack_1").update({
      lastDeliveredAt: { [ALICE_UID]: ts, [BOB_UID]: ackTs },
      updatedAt: ackTs,
    })
  );
  await assertSucceeds(
    bobDb.collection("chats").doc("dm_ack_1").collection("messages").doc("msg_ack_1").update({
      deliveredTo: [ALICE_UID, BOB_UID],
      updatedAt: ackTs,
    })
  );

  // Bob marks message read
  const readTs = pastDate();
  await assertSucceeds(
    bobDb.collection("chats").doc("dm_ack_1").collection("messages").doc("msg_ack_1").update({
      deliveredTo: [ALICE_UID, BOB_UID],
      readBy: [ALICE_UID, BOB_UID],
      updatedAt: readTs,
    })
  );

  // Bob cannot spoof Charlie into deliveredTo or overwrite Alice's lastDeliveredAt
  await assertFails(
    bobDb.collection("chats").doc("dm_ack_1").collection("messages").doc("msg_ack_1").update({
      deliveredTo: [ALICE_UID, BOB_UID, CHARLIE_UID],
      updatedAt: pastDate(),
    })
  );
  await assertFails(
    bobDb.collection("chats").doc("dm_ack_1").update({
      lastDeliveredAt: { [ALICE_UID]: pastDate(), [BOB_UID]: ackTs },
      updatedAt: pastDate(),
    })
  );
  await assertFails(
    charlieDb.collection("chats").doc("dm_ack_1").collection("messages").doc("msg_ack_1").update({
      deliveredTo: [ALICE_UID, BOB_UID, CHARLIE_UID],
      updatedAt: pastDate(),
    })
  );
});

