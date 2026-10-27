# Security policy

## Reporting a vulnerability

Do not publish exploit details in a public issue. Use GitHub's private vulnerability reporting feature for this repository where available, or contact the repository owner privately through GitHub.

Include the affected version/commit, Android version/device model, reproducible steps, impact, and sanitized logs. Never include personal URLs, tokens, PINs, or other secrets.

## Scope and limitations

SpiralCore stores user-provided URLs locally. Link validation is not an SSRF or DNS-rebinding defense if network fetching is added in the future.

The current database is not encrypted at the application layer. Device encryption, disabled backup, and FLAG_SECURE reduce exposure but do not guarantee protection on a rooted, compromised, unlocked, or instrumented device.

Security changes should include regression tests and pass CI before merging.
