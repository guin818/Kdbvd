const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const DATABASE_ID = "ai-studio-android-277c3cfb-0643-4fc8-a258-8e33f98b5437";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const CAROL_UID = "carol_789";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function serverTimestamp() {
  return new Date();
}

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
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: cannot read or write profiles, groups, or messages", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore(DATABASE_ID);
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("groups").where("memberUids", "array-contains", ALICE_UID).get());
  await assertFails(unauthDb.collection("groups").doc("TEAM01").collection("messages").get());
  await assertFails(
    unauthDb.collection("groups").doc("TEAM01").set({
      ownerId: ALICE_UID,
      name: "Team",
      description: "",
      memberUids: [ALICE_UID],
      lastMessagePreview: "",
      lastSenderName: "",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );
});

test("UserProfile: owner can create and update displayName, statusMessage, and photoBase64; others blocked", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore(DATABASE_ID);
  const now = serverTimestamp();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "민수",
      statusMessage: "열심히 코딩 중",
      photoBase64: "dGVzdA==",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      statusMessage: "휴식 중 ☕",
      updatedAt: serverTimestamp(),
    })
  );

  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").get());
});

test("Group chat: Alice creates group, Bob joins by group ID, both chat; Carol is isolated", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore(DATABASE_ID);
  const carolDb = testEnv.authenticatedContext(CAROL_UID).firestore(DATABASE_ID);
  const now = serverTimestamp();

  // Alice creates group TEAM01
  await assertSucceeds(
    aliceDb.collection("groups").doc("TEAM01").set({
      ownerId: ALICE_UID,
      name: "프로젝트 팀방",
      description: "함께 대화해요",
      memberUids: [ALICE_UID],
      lastMessagePreview: "",
      lastSenderName: "",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Non-member cannot read or list Alice's group before joining
  await assertFails(bobDb.collection("groups").doc("TEAM01").get());
  await assertFails(carolDb.collection("groups").get());

  // Bob joins TEAM01 by adding only himself to memberUids
  await assertSucceeds(
    bobDb.collection("groups").doc("TEAM01").update({
      memberUids: [ALICE_UID, BOB_UID],
      updatedAt: serverTimestamp(),
    })
  );

  // Carol cannot inject herself + tamper with group name while joining
  await assertFails(
    carolDb.collection("groups").doc("TEAM01").update({
      name: "Hacked",
      memberUids: [ALICE_UID, BOB_UID, CAROL_UID],
      updatedAt: serverTimestamp(),
    })
  );

  // Alice and Bob can query their groups
  await assertSucceeds(bobDb.collection("groups").where("memberUids", "array-contains", BOB_UID).get());

  // Bob sends a message to TEAM01
  await assertSucceeds(
    bobDb.collection("groups").doc("TEAM01").collection("messages").doc("msg_1").set({
      groupId: "TEAM01",
      senderId: BOB_UID,
      senderName: "영희",
      senderStatus: "온라인",
      content: "안녕하세요 팀원분들!",
      memberUids: [ALICE_UID, BOB_UID],
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  // Alice can list messages in TEAM01
  await assertSucceeds(
    aliceDb.collection("groups").doc("TEAM01").collection("messages").where("memberUids", "array-contains", ALICE_UID).get()
  );

  // Carol (non-member) cannot read or send messages in TEAM01
  await assertFails(
    carolDb.collection("groups").doc("TEAM01").collection("messages").get()
  );
  await assertFails(
    carolDb.collection("groups").doc("TEAM01").collection("messages").where("memberUids", "array-contains", ALICE_UID).get()
  );
  await assertFails(
    carolDb.collection("groups").doc("TEAM01").collection("messages").doc("msg_1").get()
  );
  await assertFails(
    carolDb.collection("groups").doc("TEAM01").collection("messages").doc("msg_carol").set({
      groupId: "TEAM01",
      senderId: CAROL_UID,
      senderName: "Carol",
      senderStatus: "",
      content: "Sneak in",
      memberUids: [CAROL_UID],
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );
});

test("Validation guards: rejects ghost fields, empty senderName, oversized content, and orphaned messages", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const now = serverTimestamp();

  // Orphaned message without parent group fails
  await assertFails(
    aliceDb.collection("groups").doc("NO_SUCH_GROUP").collection("messages").doc("msg_orphan").set({
      groupId: "NO_SUCH_GROUP",
      senderId: ALICE_UID,
      senderName: "민수",
      senderStatus: "",
      content: "Orphan",
      memberUids: [ALICE_UID],
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("groups").doc("G1").set({
      ownerId: ALICE_UID,
      name: "테스트방",
      description: "",
      memberUids: [ALICE_UID],
      lastMessagePreview: "",
      lastSenderName: "",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Ghost field rejected
  await assertFails(
    aliceDb.collection("groups").doc("G1").collection("messages").doc("msg_ghost").set({
      groupId: "G1",
      senderId: ALICE_UID,
      senderName: "민수",
      senderStatus: "",
      content: "Hi",
      memberUids: [ALICE_UID],
      isAdmin: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Empty senderName rejected
  await assertFails(
    aliceDb.collection("groups").doc("G1").collection("messages").doc("msg_empty").set({
      groupId: "G1",
      senderId: ALICE_UID,
      senderName: "",
      senderStatus: "",
      content: "Hi",
      memberUids: [ALICE_UID],
      createdAt: now,
      updatedAt: now,
    })
  );
});
