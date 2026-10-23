# DevVault release checklist

## CI and source checks
- [x] GitHub Actions pass: TypeScript typecheck, Jest tests, ESLint, SQL schema syntax validation, and heuristic secret scan. See the green CI run for commit `bc70a0399652640beb1973e0498ab7696570077b`.
- [ ] Generate and commit `package-lock.json`, then use `npm ci` for reproducible installs.
- [ ] Review dependency audit findings, including development dependencies; do not run automatic breaking upgrades blindly.

## Native database encryption
- [ ] Build a fresh Android binary with SQLCipher actually compiled in.
- [ ] Verify `PRAGMA cipher_version` is non-empty in the native binary.
- [ ] Verify a database created with a key cannot be opened with a different key.
- [ ] Verify the database file is not plaintext and run SQLCipher integrity checks.
- [ ] Test a missing/corrupt SecureStore key and database-open failure; ensure the app does not silently replace keys or delete the database.
- [ ] Consider moving key application into native startup/database configuration if the Expo SQLite open lifecycle cannot guarantee the key is applied before any database page access.

## Authentication and privacy
- [ ] Test first launch, unlock, cancellation, unavailable biometrics, temporary lockout, device passcode fallback, key unavailable, and biometric enrollment changes on real Android/iOS devices.
- [ ] Confirm app-switcher redaction on both platforms; Android screenshot protection needs native `FLAG_SECURE` integration.
- [ ] Verify links, descriptions, and keys never appear in logs, crash reports, backups, or network requests.
- [ ] Test the app’s behavior when backgrounded and when returning before/after the configured timeout.

## Platforms and release
- [ ] Build and test iOS on macOS/Xcode.
- [ ] Verify screen-reader accessibility, RTL and LTR rendering, small screens, keyboard handling, and long URLs.
- [ ] Review dependency advisories and generated native project.
- [ ] Do not publish until native SQLCipher/authentication checks pass.
