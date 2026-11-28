# Naviamp Connect Stage 2 — Full Sign-in (#714)

Design: `docs/superpowers/specs/2026-11-25-naviamp-connect-simplification-design.md` §5 and §12.
Branch: `feature/714-connect-full-sign-in` (from `main` at `0e1fc13ba`).

## Goal

**Sign in** copies every saved account with its long-lived credential, plus portable settings, from
one connected device to another, in either direction, as a one-time copy. Today's sign-in sends only
the active account (stage 1).

## Constraints found in the code (drive the design)

1. **Older peers reject unknown wire values.** A new `NaviampConnectCapability` value or command
   `@SerialName` fails to decode on a v1 peer and closes the session. New behavior is therefore
   advertised with additive `Welcome` booleans (the `companionProvisioningSupported` pattern), and
   new commands are sent only to a target that advertised them. Protocol version stays 1.
2. **The target never learns the controller's abilities** (Hello capabilities are empty). Pull is
   therefore always a controller request that the target answers.
3. **The wire profile cannot express API-key accounts** (`password` required, no mode field).
4. **Validating an account always activates it** (`provisionConnect` → `connect(Form)`), and saving a
   second account with an existing `cacheNamespace` throws. Merging N accounts needs a
   validate-and-save path that does not switch the active source and targets the existing row.
5. **Secrets read back as null when undecryptable**, and some rows hold only session tokens
   (Subsonic token/salt, Navidrome JWT, Jellyfin DeviceId token). Only a password or an API key is
   portable; anything else is "Needs password".
6. **Certificate-file accounts are device-local** (files copied into app storage).
7. **Timeouts are sized for one account** (target 45 s validation, controller 60 s wait).
8. **Settings sync and Connect disagree on portable fields.** The transfer uses the sync document's
   normalization and keeps the union of both device-local lists on the receiver.

## Wire additions (core:domain, additive, protocol v1)

- `NaviampConnectWelcome.accountTransferSupported: Boolean = false` — the target can receive and
  serve a full transfer.
- `NaviampConnectAccountTransfer` (redacting `toString`):
  - `document: SettingsSyncDocument` — portable profiles and preferences, certificate paths removed;
  - `credentials: List<NaviampConnectAccountCredential>` — per profile id: `kind` (password | apiKey),
    `secret`, secret header values, Aurral companion password and header values;
  - `activeProfileId: String?`, `includeSettings: Boolean`.
- Commands (controller → target), sent only when `accountTransferSupported`:
  - `offer_account_transfer(transfer, transferId)` — send direction;
  - `request_account_transfer(transferId, includeSettings)` — pull direction.
- Messages (target → controller):
  - `account_transfer_result(transferId, results: List<AccountResult>)` — per profile: Added,
    Updated, NeedsPassword, SetUpOnDevice, Failed(code);
  - `account_transfer_payload(transferId, transfer)` — the pull answer.
- Both commands are non-idempotent: add to `isNaviampConnectIdempotent`, `requiredCapability`
  (maps to `ConnectionProvisioning`) and the `rememberCompleted` strip list.
- A target without `accountTransferSupported` keeps stage 1's single-account offer.

## Core owners

- **`NaviampCoreAccountTransferExport`** (core/presentation): builds a transfer from the local
  saved sources (revealed secrets via the settings sync snapshot) and preferences. Marks accounts
  NeedsPassword (no password/API key) and SetUpOnDevice (certificate files). Accepts passwords the
  user typed on the sending device for NeedsPassword accounts.
- **`NaviampCoreAccountTransferImport`** (core/presentation): applies a received transfer:
  1. For each profile with a credential, validate and save **without activating**, matched by
     provider + server + username (existing row id passed so the credential is updated, never a
     duplicate); never delete.
  2. Activate the sender's active profile if it validated.
  3. Apply settings only when `includeSettings`, through the sync document's normalization, keeping
     the receiver's device-local fields.
  4. Return per-account results. No prompts on the receiver.
- **Provider session port**: new `validateAndSave(form, existingSourceId)` that validates and
  upserts without changing the active source. Implemented in each provider's `commonMain`
  (Subsonic family incl. Aurral wrapper, Jellyfin) and delegated by the router.
- **`NaviampCoreConnectController`**: send, pull and serve flows; per-transfer timeout scaled by
  account count (45 s per account + 15 s); one transfer at a time; trace events without secrets.

## UI (core:ui, all strings translatable)

- **Post-connect sheet** picks the direction:
  - this device has accounts and the other has none → "Sign in <device>" (ticked);
  - this device has none and the other has → "Sign in this device from <device>" (ticked);
  - both have accounts → "Sign in <device>" unticked.
  - "Change…" lists accounts (checkbox each, status: Needs password with an inline password field,
    Set up on the device) and a Settings checkbox (on for an empty receiver, off otherwise).
- **Results**: after the transfer the sheet lists each account's result; the serving device shows
  "Sent N accounts to <device>".
- **Your devices → Send my accounts again** and **Get accounts from this device** (connects with
  the trusted device, then sends or pulls).

## Tasks (test-first; common code before hosts)

1. Wire types + codec tests (round trip, redaction, unknown-key tolerance, older-peer gating).
2. Export: per-provider secret selection (password, API key from `token` when mode is apiKey,
   secret headers, companion), NeedsPassword, SetUpOnDevice, typed-password substitution.
3. Provider `validateAndSave` in Subsonic-family and Jellyfin ports + router; storage merge by
   server key; no activation.
4. Import: multi-account merge, collisions, partial failure, activation, settings policy, results.
5. Controller send flow (sheet → offer → results), fallback to stage 1 for older targets.
6. Controller pull flow and target serve flow.
7. Post-connect sheet direction, Change… list, results; Your devices actions; strings in all
   catalogs.
8. Peer tests with real crypto: phone (3 accounts) → fresh TV; fresh phone pulls from configured
   TV and Desktop; configured receiver keeps its accounts; no secrets in traces.
9. Settings export/import round-trip for any changed settings; docs (protocol notes, acceptance).
10. Device acceptance (phone ↔ TV, desktop ↔ TV) per the issue checklist.

## Open decisions (designer's call unless the owner objects)

- Activating the sender's active account on a receiver that already had accounts: done only when
  the user ticked sign-in (default off for a configured receiver).
- Device-local settings: union of the sync document's and Connect's lists stays local.
