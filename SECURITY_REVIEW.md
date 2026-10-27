# SpiralCore security hardening review

## Scope and current automated evidence

The security work is split across PR #2 (URL validation and basic workflow hardening), PR #3 (Room migration data integrity, concurrency and UI lifecycle test coverage), PR #4 (SQLCipher API/build repair and cleanup retry behavior), and the final audit branch.

The CI run for PR #4 head `ef309eaf3bcd7434887027c2c968de9707f784e9` completed successfully on 2026-10-27:
- Debug and Release build plus JVM unit tests: passed.
- Gitleaks: passed; no leaks reported in the scanned PR commits.
- CodeQL Java/Kotlin analysis: passed.
- Gradle dependency resolution: passed.
- Dependency Review workflow job: success, but its review step was skipped because the repository dependency-graph SBOM endpoint was unavailable. This is not a successful dependency-change review.

These results validate that code at that SHA builds and static checks completed. They do not validate subsequent commits on `fix/security-final-audit-2026-10`, do not constitute a penetration test, and do not prove zero vulnerabilities.

## Implemented controls

- URL validator bounds length, rejects control characters, restricts schemes to HTTP/HTTPS, rejects credential-bearing authorities, validates ports and IPv6 literals, and avoids making network requests.
- SQLite unique indexes are the authoritative concurrency guard; repository handling distinguishes URL duplicates from unrelated constraints.
- Deletion success is reported only after the Room operation succeeds.
- The UI clears its unlocked state on stop and activity destruction; biometric prompt cancellation and callback generation checks prevent stale callbacks from unlocking a stopped/recreated activity.
- Android backup is disabled and XML backup/data-extraction rules exclude app databases, shared preferences and files.
- SQLCipher for Android 4.19.1 is wired to Room 2.6.1. A random 256-bit database key is wrapped with AES-GCM using Android Keystore.
- Plaintext-to-encrypted conversion uses a separate temporary destination and checks source integrity, table row counts, encrypted integrity and key acceptance before promoting the encrypted file. The original plaintext source remains until encrypted Room successfully opens.
- Plaintext cleanup failures are persisted as pending and retried on later launches; flash-storage forensic erasure cannot be guaranteed.

## Release blockers and environment limits

1. Re-run every CI gate on the latest final-audit branch head after the final authentication and manifest edits.
2. Run Android instrumentation tests on a supported Android emulator/device. The standard CI workflow currently runs JVM tests/build/security analysis but does not execute the full instrumentation suite. Do not claim the encryption migration instrumentation tests passed unless they are explicitly run.
3. Execute the documented real-device test matrix for Android 9/10 and Android 11+; verify biometric/PIN cancellation, task switching, recreation, process death and FLAG_SECURE behavior.
4. Test migration against a copy of representative real v1 data on physical devices. Compare rows, schema, timestamps and categories before/after, and verify recovery after interruption.
5. Confirm branch protection/rulesets in repository settings. The GitHub integration cannot apply the required branch-protection settings, and no ruleset was found through the accessible endpoint. Treat `main` protection as not configured until verified by an administrator.
6. Configure release signing with protected GitHub Environment secrets. Signing credentials must never be committed. Verify the final APK signature before publication.
7. Make an explicit backup and recovery policy: current backup exclusions intentionally prevent the encrypted database and local Keystore-wrapped key from transferring to another device; users can lose access if app data or the device Keystore is lost.
8. Run independent security review/penetration testing before public release.

## Assurance

No absolute “zero vulnerabilities” claim is made. Successful static analysis is useful evidence but is not proof of absence of vulnerabilities. See `docs/DEVICE_SECURITY_TEST_PLAN.md` and `docs/ENCRYPTION_THREAT_MODEL.md`.
