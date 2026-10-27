# SpiralCore manual device security test plan

Status: test plan only. No test below is claimed as run on a physical device.

## Device matrix
- Android 9 (API 28) device with secure PIN and strong biometric available.
- Android 9/10 device with secure PIN but no enrolled/available strong biometric.
- Android 11+ device with PIN and biometric; repeat with biometric enrollment unavailable if possible.
- At least one recent Android device with rotation and multi-window/task switching enabled.

## Scenarios and expected results
1. **Cold launch / process death:** force-stop the app, relaunch it, and use Android developer options to kill background processes. The app must always show the lock screen and must never restore an unlocked screen from saved state.
2. **Fast task switching:** unlock, quickly switch to another app and back repeatedly. When the activity reaches ON_STOP, the app must require authentication again; no library flash should appear during return.
3. **System authentication cancellation:** open biometric prompt, press Back/Cancel, and cancel the PIN credential activity. The library must stay locked; cancellation should not unlock or leave a stale success callback.
4. **Rotation / configuration change:** rotate while locked, while the biometric prompt is open, and immediately after successful auth. Activity recreation must start locked. Verify no stale callback unlocks the new Activity.
5. **Android 9/10 PIN-only path:** configure a secure screen lock but no strong biometric. The explicit PIN button must launch the system device-credential confirmation, accept a valid credential, and reject cancellation/invalid credentials.
6. **Android 11+ authenticator paths:** verify the combined biometric/device-credential prompt and PIN-only control path. Confirm no prompt builder exception occurs.
7. **Lock screen removal:** remove the device's secure lock and reopen the app. The app must refuse to unlock until a secure device credential is configured.
8. **Screen capture:** attempt screenshot and screen recording while the library is open. Confirm the system honors FLAG_SECURE on the tested OS/device.
9. **Deletion failure:** simulate a storage failure where feasible. The UI must show success only when Room delete completes; on failure it must display an error and not claim the item was deleted.
10. **Migration:** install a build with a v1 database containing duplicate URLs with different createdAt values, then install the migration build. The oldest id remains, its createdAt becomes the minimum, only one URL remains, and the unique index is present.
11. **Migration empty database:** create a v1 database with no link rows and verify upgrade to v2 succeeds without modifying unrelated categories.
12. **Competing writes:** trigger two simultaneous saves of the exact same normalized URL. Exactly one row is stored; one request succeeds and the other reports duplicate.
13. **External URL behavior:** open HTTP/HTTPS links and malformed/rejected input. Only validated web URLs are offered for opening; verify no custom schemes are stored.

## Evidence to record
Record device model, Android/API level, app commit SHA/build number, exact steps, expected/actual result, and sanitized logs. Never include saved personal URLs, PINs, or secrets in issue reports.

## Limits
This plan does not substitute for automated tests, independent penetration testing, or validation on every OEM's biometric implementation.
