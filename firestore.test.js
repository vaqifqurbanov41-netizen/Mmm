const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const DATABASE_ID = "ai-studio-android-aqromslh-892d166e-8482-4e99-87aa-793ae6666b78";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function tsNow() {
  return new Date(Date.now() - 1000);
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

function getDb(context) {
  return context.firestore({ databaseId: DATABASE_ID });
}

test("Unauthenticated user cannot read or write fields", async () => {
  const unauthDb = getDb(testEnv.unauthenticatedContext());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("fields").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("fields").doc("f1").set({
      userId: ALICE_UID,
      name: "North",
      crop: "Wheat",
      sizeHa: 2.5,
      widthM: 100,
      lengthM: 250,
      location: "Barda",
      coords: [],
      createdAt: tsNow(),
      updatedAt: tsNow(),
    })
  );
});

test("Owner can create, list, and delete their field", async () => {
  const aliceDb = getDb(testEnv.authenticatedContext(ALICE_UID));
  const fieldRef = aliceDb.collection("users").doc(ALICE_UID).collection("fields").doc("field_1");
  await assertSucceeds(
    fieldRef.set({
      userId: ALICE_UID,
      name: "North Plot",
      crop: "Wheat",
      sizeHa: 4.5,
      widthM: 150,
      lengthM: 300,
      location: "Barda",
      coords: ["40.67,46.36", "40.68,46.36", "40.68,46.37"],
      createdAt: tsNow(),
      updatedAt: tsNow(),
    })
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("fields").get());
  await assertSucceeds(fieldRef.delete());
});

test("Cross-user read and write are denied", async () => {
  const aliceDb = getDb(testEnv.authenticatedContext(ALICE_UID));
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("equipment").doc("eq_1").set({
      userId: ALICE_UID,
      name: "Tractor",
      model: "MTZ-82",
      plateCode: "90-AA-001",
      inspectionDate: "2026-12-01",
      insuranceDate: "2026-12-15",
      createdAt: tsNow(),
      updatedAt: tsNow(),
    })
  );

  const bobDb = getDb(testEnv.authenticatedContext(BOB_UID));
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("equipment").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("equipment").doc("eq_2").set({
      userId: BOB_UID,
      name: "Hijack",
      model: "X",
      plateCode: "00",
      inspectionDate: "",
      insuranceDate: "",
      createdAt: tsNow(),
      updatedAt: tsNow(),
    })
  );
});

test("Shadow fields and invalid payloads are rejected", async () => {
  const aliceDb = getDb(testEnv.authenticatedContext(ALICE_UID));
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("finance").doc("fin_1").set({
      userId: ALICE_UID,
      type: "income",
      date: "2026-11-14",
      fieldName: "North",
      amount: 100.0,
      category: "Sales",
      isVerified: true,
      createdAt: tsNow(),
      updatedAt: tsNow(),
    })
  );

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("finance").doc("fin_2").set({
      userId: ALICE_UID,
      type: "invalid_type",
      date: "2026-11-14",
      fieldName: "North",
      amount: -10,
      category: "Sales",
      createdAt: tsNow(),
      updatedAt: tsNow(),
    })
  );
});

test("Owner can update profile while preserving userId and createdAt", async () => {
  const aliceDb = getDb(testEnv.authenticatedContext(ALICE_UID));
  const profileRef = aliceDb.collection("users").doc(ALICE_UID);
  await assertSucceeds(
    profileRef.set({
      userId: ALICE_UID,
      fullName: "Ali Mammadov",
      phone: "+994501234567",
      place: "Barda",
      createdAt: tsNow(),
      updatedAt: tsNow(),
    })
  );
  await assertSucceeds(
    profileRef.update({
      fullName: "Ali Mammadov Updated",
      updatedAt: tsNow(),
    })
  );
});

test("Only verified admin vaqifqurbanov494@gmail.com can write adminEntries while signed-in users can read", async () => {
  const adminUid = "admin_vaqif_1";
  const adminDb = getDb(
    testEnv.authenticatedContext(adminUid, {
      email: "vaqifqurbanov494@gmail.com",
      email_verified: true,
    })
  );
  const aliceDb = getDb(
    testEnv.authenticatedContext(ALICE_UID, {
      email: "farmer@example.com",
      email_verified: true,
    })
  );
  const unauthDb = getDb(testEnv.unauthenticatedContext());

  const adminDoc = adminDb.collection("adminEntries").doc("entry_1");
  await assertSucceeds(
    adminDoc.set({
      userId: adminUid,
      createdByEmail: "vaqifqurbanov494@gmail.com",
      category: "notice",
      region: "Aran",
      title: "Pambıq suvarma xəbərdarlığı",
      details: "Bu həftə axşam saatlarında suvarma aparın.",
      contactOrUrl: "",
      createdAt: tsNow(),
      updatedAt: tsNow(),
    })
  );

  // Regular signed-in user can read, cannot create or delete
  await assertSucceeds(aliceDb.collection("adminEntries").get());
  await assertFails(
    aliceDb.collection("adminEntries").doc("entry_2").set({
      userId: ALICE_UID,
      createdByEmail: "vaqifqurbanov494@gmail.com",
      category: "notice",
      region: "Aran",
      title: "Unauthorized",
      details: "Should fail",
      contactOrUrl: "",
      createdAt: tsNow(),
      updatedAt: tsNow(),
    })
  );
  await assertFails(aliceDb.collection("adminEntries").doc("entry_1").delete());
  await assertFails(unauthDb.collection("adminEntries").get());
  await assertSucceeds(adminDoc.delete());
});

test("Shared exams allow code-based get, owner-scoped list, and consented attempt telemetry only", async () => {
  const aliceDb = getDb(testEnv.authenticatedContext(ALICE_UID));
  const bobDb = getDb(testEnv.authenticatedContext(BOB_UID));
  const charlieDb = getDb(testEnv.authenticatedContext("charlie_789"));
  const unauthDb = getDb(testEnv.unauthenticatedContext());

  const examId = "TB4K9M2A";
  const examRef = aliceDb.collection("sharedExams").doc(examId);

  await assertSucceeds(
    examRef.set({
      ownerId: ALICE_UID,
      ownerName: "Əli",
      title: "İnformatika Sınaq",
      sourceBankTitle: "İnformatika",
      questionCount: 2,
      durationMinutes: 15,
      shuffleOptions: true,
      questions: [
        { qNum: 1, text: "Sual 1?", options: ["A", "B", "C"], correct: "A" },
        { qNum: 2, text: "Sual 2?", options: ["X", "Y", "Z"], correct: "Y" },
      ],
      createdAt: tsNow(),
      updatedAt: tsNow(),
    })
  );

  // Blanket listing of all sharedExams is denied; owner-scoped listing succeeds
  await assertFails(bobDb.collection("sharedExams").get());
  await assertSucceeds(aliceDb.collection("sharedExams").where("ownerId", "==", ALICE_UID).get());

  // Participant with exact code can get the exam
  await assertSucceeds(bobDb.collection("sharedExams").doc(examId).get());
  await assertFails(unauthDb.collection("sharedExams").doc(examId).get());

  // Submitting attempt without consent (shareConsentGranted = false) is rejected
  const bobAttemptRef = bobDb.collection("sharedExams").doc(examId).collection("attempts").doc("att_bob_1");
  await assertFails(
    bobAttemptRef.set({
      examId,
      examOwnerId: ALICE_UID,
      participantId: BOB_UID,
      participantName: "Babək",
      shareConsentGranted: false,
      totalQuestions: 2,
      answeredCount: 2,
      correctCount: 1,
      wrongCount: 1,
      unansweredCount: 0,
      totalElapsedSeconds: 45,
      durationLimitSeconds: 900,
      timedOut: false,
      wrongDetails: [
        { questionNumber: 2, questionText: "Sual 2?", selectedAnswer: "X", correctAnswer: "Y", timeSpentSeconds: 30 },
      ],
      questionTimings: [
        { questionNumber: 1, questionText: "Sual 1?", status: "CORRECT", timeSpentSeconds: 15 },
        { questionNumber: 2, questionText: "Sual 2?", status: "WRONG", timeSpentSeconds: 30 },
      ],
      createdAt: tsNow(),
      updatedAt: tsNow(),
    })
  );

  // Submitting attempt WITH explicit consent succeeds
  await assertSucceeds(
    bobAttemptRef.set({
      examId,
      examOwnerId: ALICE_UID,
      participantId: BOB_UID,
      participantName: "Babək",
      shareConsentGranted: true,
      totalQuestions: 2,
      answeredCount: 2,
      correctCount: 1,
      wrongCount: 1,
      unansweredCount: 0,
      totalElapsedSeconds: 45,
      durationLimitSeconds: 900,
      timedOut: false,
      wrongDetails: [
        { questionNumber: 2, questionText: "Sual 2?", selectedAnswer: "X", correctAnswer: "Y", timeSpentSeconds: 30 },
      ],
      questionTimings: [
        { questionNumber: 1, questionText: "Sual 1?", status: "CORRECT", timeSpentSeconds: 15 },
        { questionNumber: 2, questionText: "Sual 2?", status: "WRONG", timeSpentSeconds: 30 },
      ],
      createdAt: tsNow(),
      updatedAt: tsNow(),
    })
  );

  // Sharer (Alice) can list consented attempts with owner+consent filter
  await assertSucceeds(
    aliceDb
      .collection("sharedExams")
      .doc(examId)
      .collection("attempts")
      .where("examOwnerId", "==", ALICE_UID)
      .where("shareConsentGranted", "==", true)
      .get()
  );

  // Unrelated user (Charlie) cannot read Bob's attempt
  await assertFails(charlieDb.collection("sharedExams").doc(examId).collection("attempts").doc("att_bob_1").get());
  await assertFails(
    charlieDb
      .collection("sharedExams")
      .doc(examId)
      .collection("attempts")
      .where("examOwnerId", "==", ALICE_UID)
      .where("shareConsentGranted", "==", true)
      .get()
  );

  // Participant can revoke/delete their shared attempt
  await assertSucceeds(bobAttemptRef.delete());
  await assertSucceeds(examRef.delete());
});


