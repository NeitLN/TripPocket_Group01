// A5 (proposal 6.3, docs/ROADMAP.md): firestore.rules with two accounts, in the Firestore emulator.
// A signed-in account reads and writes only users/{its uid}/trips/*; another account, a signed-out
// client and every other path are refused. Run: npm test (needs Java 11+ for the emulator).
import { after, before, beforeEach, describe, test } from 'node:test';
import { readFileSync } from 'node:fs';
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import {
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  setDoc,
} from 'firebase/firestore';

const A = 'uid-demo1';
const B = 'uid-demo2';

// The shape of a backup document (TripSnapshotCodec), cut down: the rules only look at the path.
const trip = (tripId, ownerUid) => ({
  tripId,
  ownerUid,
  name: 'Đà Lạt Getaway',
  destination: 'Đà Lạt',
  startDate: 20376,
  endDate: 20379,
  localRevision: 1,
  updatedAt: 1000,
  members: [],
  activities: [],
  checklist: [],
  places: [],
  expenses: [],
});

let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-trippocket',
    firestore: { rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8') },
  });
});

beforeEach(async () => {
  await env.clearFirestore();
  // One backed-up trip per account, written past the rules.
  await env.withSecurityRulesDisabled(async (admin) => {
    const db = admin.firestore();
    await setDoc(doc(db, `users/${A}/trips/trip-a`), trip('trip-a', A));
    await setDoc(doc(db, `users/${B}/trips/trip-b`), trip('trip-b', B));
  });
});

after(async () => {
  await env?.cleanup();
});

const asA = () => env.authenticatedContext(A).firestore();
const signedOut = () => env.unauthenticatedContext().firestore();

describe('an account and its own trips', () => {
  test('backs up (A3): creates, overwrites and deletes its trip documents', async () => {
    const db = asA();
    await assertSucceeds(setDoc(doc(db, `users/${A}/trips/trip-new`), trip('trip-new', A)));
    await assertSucceeds(setDoc(doc(db, `users/${A}/trips/trip-a`), { ...trip('trip-a', A), localRevision: 2 }));
    await assertSucceeds(deleteDoc(doc(db, `users/${A}/trips/trip-a`)));
  });

  test('restores (A4): reads one trip and lists all of its trips', async () => {
    const db = asA();
    await assertSucceeds(getDoc(doc(db, `users/${A}/trips/trip-a`)));
    const all = await assertSucceeds(getDocs(collection(db, `users/${A}/trips`)));
    if (all.size !== 1) throw new Error(`expected 1 trip, got ${all.size}`);
  });
});

describe("another account's trips", () => {
  test('cannot be read or listed', async () => {
    const db = asA();
    await assertFails(getDoc(doc(db, `users/${B}/trips/trip-b`)));
    await assertFails(getDocs(collection(db, `users/${B}/trips`)));
  });

  test('cannot be created, overwritten or deleted', async () => {
    const db = asA();
    await assertFails(setDoc(doc(db, `users/${B}/trips/trip-planted`), trip('trip-planted', A)));
    await assertFails(setDoc(doc(db, `users/${B}/trips/trip-b`), trip('trip-b', A)));
    await assertFails(deleteDoc(doc(db, `users/${B}/trips/trip-b`)));
  });

  test('stay untouched after the refused writes', async () => {
    const db = asA();
    await assertFails(deleteDoc(doc(db, `users/${B}/trips/trip-b`)));
    await env.withSecurityRulesDisabled(async (admin) => {
      const snap = await getDoc(doc(admin.firestore(), `users/${B}/trips/trip-b`));
      if (!snap.exists() || snap.data().ownerUid !== B) throw new Error("B's trip changed");
    });
  });
});

describe('a signed-out client', () => {
  test('can neither read nor write any trip', async () => {
    const db = signedOut();
    await assertFails(getDoc(doc(db, `users/${A}/trips/trip-a`)));
    await assertFails(getDocs(collection(db, `users/${A}/trips`)));
    await assertFails(setDoc(doc(db, `users/${A}/trips/trip-x`), trip('trip-x', A)));
  });
});

describe('every other path', () => {
  test('is refused, even to the account itself', async () => {
    const db = asA();
    await assertFails(getDoc(doc(db, `users/${A}`)));
    await assertFails(setDoc(doc(db, `users/${A}`), { name: 'A' }));
    await assertFails(getDocs(collection(db, 'users')));
    await assertFails(setDoc(doc(db, `users/${A}/notes/n1`), { text: 'x' }));
    await assertFails(setDoc(doc(db, 'trips/trip-a'), trip('trip-a', A)));
  });
});
