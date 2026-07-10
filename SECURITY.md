# Security Policy

Naviamp connects to personal music servers and may handle server URLs, usernames, passwords, tokens, playback URLs, and local cache data. Please do not report sensitive security issues in public issues or pull requests.

## Reporting a Vulnerability

If you believe you have found a security issue, contact the project maintainer privately through GitHub.

Please include:

- A short description of the issue.
- Steps to reproduce it, if safe to share.
- The affected platform or build.
- Any relevant logs with secrets, tokens, server URLs, and personal data removed.

## Sensitive Information

Do not include any of the following in public reports:

- Server credentials.
- Authentication tokens.
- Private server URLs.
- Signing keys, keystores, or passwords.
- Logs that contain personal library data unless you have reviewed and sanitized them.

## Supported Versions

Security fixes are handled on the current development line. Older builds may not receive separate patch releases unless the issue is severe and practical to backport.
