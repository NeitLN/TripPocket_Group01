# Firestore rules tests (A5)

Checks `../firestore.rules` with two accounts in the Firestore emulator (proposal 6.3):
an account reads, lists, writes and deletes only `users/{its uid}/trips/*`; another account,
a signed-out client and every other path are refused.

Needs Node 20+ and Java 11+ (the emulator is a Java program; Android Studio's JBR works).

```sh
cd firestore-tests
npm ci
# Git Bash, if java is not on PATH:
export PATH="/d/Android Studio/jbr/bin:$PATH"
npm test
```

The project id `demo-trippocket` is a demo id: nothing talks to the real Firebase project.
`firebase.json` at the repo root points at `firestore.rules`; the console copy must stay in sync
(docs/RUNBOOK.md, Firebase step 5).
