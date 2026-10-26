# SpiralCore security hardening review

## Scope
This source-level review covers link validation, local storage behavior, authentication flow, declared dependencies, and CI configuration. It is not a formal penetration test and cannot prove that all vulnerabilities are closed.

## Changes to implement
- Bound maximum accepted URL length and reject control characters.
- Validate port syntax and range, reject unbracketed IPv6-like authorities, and apply conservative syntax checks to bracketed IPv6 literals.
- Add regression tests for unsupported schemes, credentials, malformed ports, control characters, oversized inputs, host/scheme case normalization, and malformed IPv6.
- Treat SQLite unique indexes as the authoritative concurrency guard and avoid mapping unrelated storage constraints to duplicate-link errors.
- On Android 9/10, allow the explicit device-credential button to open the system credential screen even if strong biometrics are unavailable.
- Add CodeQL, dependency review on pull requests, dependency graph validation, Dependabot updates, and security reporting guidance.

## Remaining release gates
1. Application-layer at-rest encryption is not implemented. Choose a threat model, SQLCipher or equivalent solution, key management, migration/rollback, and recovery strategy before claiming forensic resistance.
2. Add Room instrumentation tests for migration 1-to-2 and concurrent insert conflicts.
3. Test biometric/credential flows on physical Android 9/10 and Android 11+ devices, including cancel, background, process death, and no-biometric configurations.
4. Configure release signing using repository/environment secrets; do not commit key files or passwords.
5. Check CodeQL, dependency-review, and dependency jobs after GitHub executes them. Automated tools have coverage limitations and can generate false positives.
6. Configure repository rules: the main branch is currently unprotected. Require pull requests and passing checks before merge.
7. Perform an independent security review and verify the signed APK before public distribution.

## Assurance statement
No absolute "zero vulnerabilities" claim is made. A passing build or static analysis run is not equivalent to a full penetration test.
