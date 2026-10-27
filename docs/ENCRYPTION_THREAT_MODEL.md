# SpiralCore database encryption: threat model and proposal

## Status
Proposal only. No encryption code, dependency, key generation, or database migration is implemented by this document. Stop here pending explicit owner approval.

## Assets and threats
The database contains user-saved URLs, titles, categories, and timestamps. Relevant threats are offline extraction of the app's database from a copied device image, a rooted or forensic-accessible device, debugging/instrumentation, and backup or transfer mistakes. An attacker controlling an unlocked device or the running app may still read data; encrypting SQLite does not protect against a compromised process while the database is open. URLs copied/shared by the user are outside database-at-rest protection.

## Candidate design
Prefer SQLCipher-compatible Room integration (or another actively maintained SQLite-at-rest encryption solution) only after confirming compatibility with the current Room/SQLite driver versions, Android ABI support, licensing, maintenance cadence, and release artifact size. Do not implement ad hoc page/file encryption. The database passphrase should be random, high entropy, and never hard-coded or derived directly from a user PIN.

## Key management
Generate a random database key once and protect its wrapping key with Android Keystore. Prefer hardware-backed protection when available while handling devices where it is not. Keep key material out of source control, logs, analytics, clipboard, saved instance state, and ordinary preferences. A Keystore alias is not the secret itself. Define behavior for lock-screen removal, biometric enrollment changes, key invalidation, device migration, and app reinstall before implementation. Because a local-only vault has no server recovery, loss of the wrapping key may mean unrecoverable data; this must be disclosed to users.

## Migration from plaintext database
1. Ship only after backup/restore and rollback behavior are specified and tested.
2. Create a new encrypted database beside the existing plaintext database; never overwrite the source in place.
3. In a transaction/validated migration flow, copy categories and links, verify row counts and content integrity, and validate indexes/foreign keys.
4. Close both databases, persist a durable migration marker, then atomically promote the encrypted database where filesystem constraints permit.
5. Keep a recoverable source until the encrypted copy is validated and the app has safely reopened it. Define secure cleanup and crash-recovery semantics; deletion cannot guarantee forensic erasure on flash storage.
6. On interruption, reopen either the verified old database or verified new database, never a partial destination. Do not delete plaintext data before successful verification.
7. Add tests for empty DB, large DB, duplicate legacy URLs, corrupt source, low storage, process death at each phase, key invalidation, and rollback.

## Approval gate
Before coding, approve the chosen library/integration, key lifecycle and recovery policy, minimum supported Android behavior, migration/cutover strategy, and acceptance tests. Until then, existing SQLite remains unencrypted at the application layer; do not claim forensic resistance.
