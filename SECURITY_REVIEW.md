# SpiralCore security hardening review

## PR #2 scope
PR #2 adds bounded URL validation and regression tests, URL authority/port/credential checks, a conservative IPv6 syntax check, an Android 9/10 device-credential fallback, Gitleaks scanning, and Gradle dependency graph resolution. The follow-up branch adds migration/data-integrity tests, stronger edge-case URL tests, safer deletion feedback, CodeQL and Dependency Review.

## Release gates
- Application-layer database encryption is intentionally not implemented. See `docs/ENCRYPTION_THREAT_MODEL.md`; implementation must wait for owner approval.
- Migration 1-to-2 retains the oldest row ID per URL and sets its creation timestamp to the earliest timestamp in the duplicate group. Instrumentation tests cover duplicate and empty link tables.
- Concurrent duplicate insertions are tested using Room's unique URL index and the repository result.
- Lifecycle state handling clears the unlocked UI at ON_STOP and ON_DESTROY. Real-device validation is still required for fast task switching, system credential cancellation, rotation, process death, and Android 9/10 PIN behavior.
- Configure main branch protection/rulesets to require pull requests, reviews, and all required checks before merge. Documentation only; no repository setting was changed by this branch.
- Configure release signing using protected repository/environment secrets (keystore bytes, store password, key alias, key password). Never commit signing materials. Documentation only; signing was not added.
- Complete an independent security review and verify the signed APK before public distribution.

## CI scope
CI runs unit/build tasks, Gitleaks, Gradle dependency resolution, CodeQL Java/Kotlin analysis, and Dependency Review for pull requests. Passing CI is not equivalent to a penetration test.

## Assurance
No claim of zero vulnerabilities is made. No physical-device testing is claimed; see `docs/DEVICE_SECURITY_TEST_PLAN.md`.
