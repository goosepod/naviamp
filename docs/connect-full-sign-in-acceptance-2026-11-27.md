# Connect Full Sign-in Acceptance (#714) — 2026-11-27

Devices: Pixel 6a (phone) and onn 4K Pro (Android TV), both on the `v2test` build of
`feature/714-connect-full-sign-in`, test Navidrome server, same Wi-Fi network.

## Results

| Scenario | Result |
|---|---|
| Phone (signed in) → TV with app data cleared: connect with the TV's code, sheet shows sign-in ticked and "Choose accounts and settings" lists the account as ready; Done | Pass: the TV lands on Home signed in as the test account with no TV input; the phone says "onn 4K Pro Streaming Device is signed in." |
| Phone with app data cleared pulls from the signed-in TV: TV shows its code from Settings → Devices; phone enters it | Pass: the sheet offers "Sign this device in with accounts from onn 4K Pro Streaming Device" (ticked); Done signs the phone in and it lands on Home without typing a password |
| Secrets in logs | Pass: the trace lines contain only session and transfer ids |

Shared tests cover the rest of the issue's acceptance: three accounts (password, API key,
Jellyfin) with settings and the active account, merge into a configured device (credential
updated, library choice and name kept, nothing deleted), a rejected account with per-account
results, a token-only account given a password on the sending device, certificate accounts
reported, Your devices → Send my accounts, and previous-release peers keeping single-account
sign-in.

## Notes

- A phone with no accounts opens on the sign-in form; Connect a device is reached through
  Settings → Devices. A first-run shortcut belongs with the QR entry point (#715).
- Not exercised on devices: Desktop, multiple real accounts, a Jellyfin server, and a
  previous-release peer.
