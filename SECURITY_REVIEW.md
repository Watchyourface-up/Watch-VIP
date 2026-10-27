# SpiralCore security hardening review

## Scope and current automated evidence

The security work is split across PR #2 (URL validation and basic workflow hardening), PR #3 (Room migration data integrity, concurrency and UI lifecycle test coverage), PR #4 (SQLCipher API/build repair and cleanup retry behavior), and the final audit branch.

The CI run for PR #4 head `ef309eaf3bcd7434887027c2c968de9707f784e9` completed successfully on 2026-10-27:
- Debug and Release build plus JVM unit tests: passed.
- Gitleaks: passed; no leaks reported in the scanned PR commits.
- CodeQL Java/Kotlin analysis: passed.
- Gradle dependency resolution: passed.
- Dependency Review workflow job: success, but its review step was skipped because the repository dependency-graph SBOM endpoint was unavailable. This is not a successful dependency-change review.

Final-audit head `0e9af6cfda5085a2bc572aad8eb024578ce708d1` adds a stronger migration integrity check; its CI run is in progress. Do not treat the earlier passing run at `24a27de39427066ad933a89a23ca4420cc5d85a0` as validation of the current head.

## Implemented controls

- URL validator bounds length, rejects control characters, restricts schemes to HTTP/HTTPS, rejects credential-bearing authorities, validates ports and IPv6 literals, and avoids making network requests.
- SQLite unique indexes are the authoritative concurrency guard; repository handling distinguishes URL duplicates from unrelated constraints.
- Deletion success is reported only after the Room operation succeeds.
- The UI clears its unlocked state on stop and activity destruction; biometric prompt cancellation and callback generation checks prevent stale callbacks from unlocking a stopped/recreated activity.
- Android backup is disabled and XML backup/data-extraction rules exclude app databases, shared preferences and files.
- SQLCipher for Android 4.19.1 is wired to Room 2.6.1. A random 256-bit database key is wrapped with AES-GCM using Android Keystore.
- Plaintext-to-encrypted conversion uses a separate temporary destination. It checks source integrity, compares schema metadata and typed row values between source and encrypted output, checks encrypted integrity/key acceptance, and preserves the original until encrypted Room opens successfully.
- Plaintext cleanup failures are persisted as pending and retried on later launches; flash-storage forensic erasure cannot be guaranteed.

## Release blockers and environment limits

1. Await and inspect all CI gates on the current final-audit head `0e9af6cfda5085a2bc572aad8eb024578ce708d1`.
2. Run Android instrumentation tests on a supported Android emulator/device. The standard CI workflow currently runs JVM tests/build/security analysis but does not execute the full instrumentation suite. Do not claim the encryption migration instrumentation tests passed unless they are explicitly run.
3. Execute the documented real-device test matrix for Android 9/10 and Android 11+; verify biometric/PIN cancellation, task switching, recreation, process death and FLAG_SECURE behavior.
4. Test migration against a copy of representative real v1 data on physical devices, checking rows, schema, timestamps and categories before/after, plus recovery after interruption.
5. Confirm branch protection/rulesets in repository settings. The GitHub integration cannot access the branch-protection API (403), so treat `main` protection as unverified and likely not configured until checked by an administrator.
6. Configure release signing with protected GitHub Environment secrets. Signing credentials must never be committed. Verify the final APK signature before publication.
7. Make an explicit backup and recovery policy: current backup exclusions intentionally prevent the encrypted database and local Keystore-wrapped key from transferring to another device; users can lose access if app data or the device Keystore is lost.
8. Run independent security review/penetration testing before public release.

## Assurance

No absolute “zero vulnerabilities” claim is made. Successful static analysis is useful evidence but is not proof of absence of vulnerabilities. See `docs/DEVICE_SECURITY_TEST_PLAN.md` and `docs/ENCRYPTION_THREAT_MODEL.md`.
