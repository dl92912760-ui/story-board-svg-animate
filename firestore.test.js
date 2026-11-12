const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function nowTimestamp() {
  return new Date(Date.now() - 1000);
}

function validHistoryPayload(userId) {
  const ts = nowTimestamp();
  return {
    userId,
    title: "Fall of Constantinople",
    prompt: "Animated documentary of 1453 Constantinople",
    era: "1453 CE • Byzantine Empire",
    summary: "A visual chronicle of the final siege of Constantinople.",
    generationType: "video",
    backgroundTheme: "dark",
    visualStyle: "Engraved Vector",
    aspectRatio: "16:9",
    totalDurationSeconds: 18,
    scenes: [
      {
        sceneNumber: 1,
        title: "The Golden Horn",
        narration: "Ships glide silently beneath the fortified sea walls.",
        durationSeconds: 6,
        cameraMotion: "zoom_in",
        svgCode: "<svg viewBox='0 0 800 450' xmlns='http://www.w3.org/2000/svg'><rect width='800' height='450' fill='#0E1015'/></svg>",
      },
    ],
    favorite: false,
    createdAt: ts,
    updatedAt: ts,
  };
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

test("Unauthenticated user cannot list or read histories", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("histories").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("histories").doc("hist_1").get()
  );
});

test("Authenticated owner can create, list, update favorite, and delete their own history", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("histories")
    .doc("hist_1");

  await assertSucceeds(docRef.set(validHistoryPayload(ALICE_UID)));
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("histories").get()
  );
  await assertSucceeds(
    docRef.update({ favorite: true, updatedAt: nowTimestamp() })
  );
  await assertSucceeds(docRef.delete());
});

test("Authenticated user cannot read, write, or delete another user's histories", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context
      .firestore()
      .collection("users")
      .doc(BOB_UID)
      .collection("histories")
      .doc("bob_hist")
      .set(validHistoryPayload(BOB_UID));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("histories").get()
  );
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("histories").doc("bob_hist").get()
  );
  await assertFails(
    aliceDb
      .collection("users")
      .doc(BOB_UID)
      .collection("histories")
      .doc("bob_hist")
      .set(validHistoryPayload(ALICE_UID))
  );
});

test("Rejects shadow fields, empty scenes, and immutable field mutation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("histories")
    .doc("hist_shadow");

  const withShadow = { ...validHistoryPayload(ALICE_UID), isAdmin: true };
  await assertFails(docRef.set(withShadow));

  const emptyScenes = { ...validHistoryPayload(ALICE_UID), scenes: [] };
  await assertFails(docRef.set(emptyScenes));

  await assertSucceeds(docRef.set(validHistoryPayload(ALICE_UID)));
  await assertFails(
    docRef.update({ userId: BOB_UID, updatedAt: nowTimestamp() })
  );
  await assertFails(
    docRef.update({ prompt: "hijacked prompt", updatedAt: nowTimestamp() })
  );
});
