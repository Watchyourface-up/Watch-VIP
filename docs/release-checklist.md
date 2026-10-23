# Release checklist

- [ ] Run npm install, typecheck, unit tests, lint, schema validation and secret scan.
- [ ] Build a fresh Android development/release binary with SQLCipher enabled.
- [ ] Verify PRAGMA cipher_version is non-empty in the native binary.
- [ ] Verify a database created with a key cannot be opened with a different key.
- [ ] Verify the database is not plaintext by inspecting a copied test database header and running SQLCipher-specific integrity checks.
- [ ] Test first launch, lock/unlock, cancel, biometric unavailable, temporary lockout, key unavailable, and biometric enrollment changes.
- [ ] Test app-switcher redaction and Android FLAG_SECURE behavior on a real device.
- [ ] Build and test iOS on macOS/Xcode; do not claim iOS screenshot blocking.
- [ ] Verify links never appear in logs, crash reports, or network requests.
- [ ] Review dependency advisories and the generated native project before release.
- [ ] Do not publish until native security checks pass.
