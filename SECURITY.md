# Security notes

- Do not store passwords, access tokens, recovery codes, or authentication secrets in links or descriptions.
- Sensitive URL detection is heuristic and may have false positives and false negatives.
- The SQLCipher adapter checks `PRAGMA cipher_version` and refuses to proceed if the native build does not expose SQLCipher.
- Database keys must never be logged or committed. An initialization marker prevents silently generating a replacement key after a previously initialized key goes missing. Key access failure must not trigger automatic database deletion.
- The app requests screen-capture protection through Expo ScreenCapture and enables iOS app-switcher protection where available. Verify on physical devices; do not promise that all screen recording or screenshots are blocked on every OS version.
- The configurable auto-lock timeout is immediate by default; delayed options are available only when the app reports screen privacy protection enabled. This is still a UI/session control, not protection against a compromised or rooted device.
- Delayed auto-lock options are available only when the screen privacy API reports success; the app defaults to locking immediately on background.
- Report issues without posting private links or live credentials. Rotate any exposed credential.
