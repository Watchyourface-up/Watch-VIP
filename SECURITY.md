# Security notes

- Do not store passwords, access tokens, recovery codes, or authentication secrets in links or descriptions.
- Sensitive URL detection is heuristic and may have false positives and false negatives.
- The SQLCipher adapter checks `PRAGMA cipher_version` and refuses to proceed if the native build does not expose SQLCipher.
- Database keys must never be logged or committed. Key access failure must not trigger automatic database deletion.
- iOS does not provide a universal way to block all screenshots. Android screenshot protection requires native secure-window integration.
- Report issues without posting private links or live credentials. Rotate any exposed credential.
