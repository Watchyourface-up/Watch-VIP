# SpiralCore security hardening review

## Scope
This source-level review covers URL validation, local storage behavior, authentication flow, declared dependencies, and CI configuration. It is not a formal penetration test and cannot prove that all vulnerabilities are closed.

## Implemented on the hardening branch
- Bound maximum accepted URL length and reject control characters.
- Validate port syntax and range, reject credential-bearing authorities, and apply conservative syntax checks to IPv6-like input.
- Added regression tests for unsupported schemes, credentials, malformed ports, control characters, oversized inputs, host/scheme case normalization, and malformed IPv6.
- Treat SQLite unique indexes as the authoritative concurrency guard and avoid mapping unrelated storage constraints to duplicate-link errors.
- On Android 9/10, allow the explicit device-credential button to open the system credential screen even if strong biometrics are unavailable.
- Added dependency graph resolution, weekly Dependabot updates, Gitleaks CLI secret scanning, and security reporting guidance.

## Remaining release gates
1. Application-layer at-rest encryption is not implemented. Choose a threat model, SQLCipher or equivalent solution, key management, migration/rollback, and recovery strategy before claiming forensic resistance.
2. The existing Room migration 1-to-2 deletes duplicate URL rows before creating a unique index. Preserve user data by design: add migration tests and decide how duplicate links should be reconciled before shipping a migration that removes records.
3. Add Room instrumentation tests for migration 1-to-2 and concurrent insert conflicts.
4. Test biometric/credential flows on physical Android 9/10 and Android 11+ devices, including cancel, background, process death, and no-biometric configurations.
5. Configure release signing using repository/environment secrets; do not commit key files or passwords.
6. Configure repository rules: require pull requests and passing checks before merge. Confirm the main branch protection/ruleset from repository settings.
7. Perform an independent security review and verify the signed APK before public distribution.

## CI scope
The workflow currently runs Android unit tests, Debug/Release builds, Gitleaks secret scanning, and dependency graph resolution. CodeQL and GitHub Dependency Review are not currently configured in this workflow.

## Assurance statement
No absolute "zero vulnerabilities" claim is made. A passing build or static analysis run is not equivalent to a full penetration test.
