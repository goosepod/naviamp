# Naviamp Connect Simplification — Design

Status: draft for owner review (2026-11-25)

## Intent

The owner paired a phone with an Android TV and found the flow unintuitive, complicated, and
unwieldy. Connecting two Naviamp devices must become a single, obvious action:

> The device being connected shows a 6-digit code **and** a QR code. On the other device, type the
> code or scan the QR. It connects. Done.

After connecting, the user can do either or both of:

1. **Control** — use one device to play on / control the other (phone → TV).
2. **Sign in** — copy the user's accounts and settings to the connected device so it is set up
   without typing a server address or password on a TV remote.

This work is also the foundation for seamless **playback handoff** (#348): listening on a phone,
coming home, opening Naviamp on the TV, and pressing one button on the phone to continue the same
queue at the same position on the TV. Every decision below must keep that path open.

User-facing copy and docs must describe Naviamp's own behavior only; do not reference other
products.

## Goals

- One concept, one entry point on every platform: **Connect a device**.
- Target always shows a 6-digit code and a QR together. A camera device can scan; any device
  (including a desktop with no camera) can type the code.
- Showing the code is the consent. No separate approval step on the target in the normal flow.
- Pair + sign-in happen in one flow, not two separately approved operations.
- Sign-in transfers **all** saved accounts and portable settings, as a **one-time copy** (owner
  decision). Re-sending later is a one-tap action.
- After first connection, no more codes: trusted devices are listed with live availability and are
  one tap to use, and reconnect automatically when reachable.
- Device management (rename, forget, send accounts again) on every platform, including TV.
- Every user-facing string translatable (including the existing hardcoded Connect strings).

## Non-goals

- Continuous account/settings sync between devices (owner chose one-time copy).
- A Naviamp cloud account, relay, or internet rendezvous. Connect remains LAN + manual address
  (Tailnet) only.
- The full handoff prepare/commit/reconcile protocol of #348. This design provides the presence,
  trust, account parity, and UI entry point that #348 builds on.
- iOS/tvOS Connect adapters (#590 and Apple items in `docs/android-tv-plan.md`). The shared design
  must not block them; native adapters come later.
- A web fallback page for the QR link when Naviamp is not installed.

## Anti-goals (failure even if it "works")

- Any step where the user must understand "pairing" vs "provisioning" vs "approval".
- A TV that asks for a password through the remote during phone-driven sign-in.
- Audio unexpectedly starting on a TV because a phone came home (presence ≠ playback).
- Product behavior placed in a host module (AGENTS.md "Core Is the Product").
- Weakening the current cryptography to make the UX simpler.

## Current state (summary of audit)

Security core is sound and is kept:

- DNS-SD `_naviamp-connect._tcp`, raw length-prefixed TCP, OS-assigned ephemeral port.
- The 6-digit code is the J-PAKE (RFC 8236) password; never sent over the wire. HKDF transcript
  root → AES-256-GCM channel; ECDSA P-256 identity proofs; fingerprint pinning; 5 attempts then
  30 s lockout; 5-minute code lifetime.
- Trust records + PAKE-root resumption credentials; heartbeat; newest controller wins.

UX problems being fixed:

- Pairing and provisioning are separate steps with separate approvals; automatic setup only for an
  *empty* TV with an *explicitly shown* code (`NaviampCoreConnectController` ~1296-1358).
- Trusted targets auto-start a background, non-consent code that forces an Approve step on the next
  new controller, and the TV row then reads "Stop pairing".
- Discovery is manual ("Find Naviamp devices"); targets only discoverable while advertising.
- Three taps plus code entry with no auto-submit.
- Phone Controllers page mixes own name, own target code, discovery, manual address and trusted
  aliases on one page; many strings hardcoded in `NaviampSettingsUi.kt:545-713`.
- Provisioning sends only the active account + interface/playback settings; other accounts, radio
  stations, visualizer, prefetch stay behind. Password prompt appears whenever the controller lacks a
  stored plaintext password, and after target validation failures.
- Output selection is in-memory only; nothing reconnects after restart.
- TV cannot rename/forget trusted devices. Manual port is ephemeral and missing from TV first-run.
- No QR support anywhere; iOS/tvOS have no Connect.

## Approaches considered

**A. Keep the security core, rebuild the experience around it (chosen).** Keep J-PAKE, identity,
trust, resumption, and transport. Add a QR variant of the same handshake, merge pair + sign-in,
widen the sign-in payload by reusing the settings sync document, and replace the UI. Lowest risk;
preserves a hardened, tested protocol. Older peers must update to use the new flow.

**B. New protocol (e.g. HTTPS/WebSocket + key-bound QR).** Enables a web fallback later, but discards
a working audited handshake and months of reconnect/edge-case hardening for no user-visible gain.
Rejected.

**C. UI-only polish.** Fastest, but omits QR and full-account sign-in, the two things the owner asked
for. Rejected.

## Design

### 1. Roles and vocabulary

- **This device** — the one in your hand.
- **Connect a device** — the single entry point. Opens the connect screen.
- **Let another device connect** — makes this device show its code + QR.
- **Your devices** — trusted devices.
- Code/protocol may keep `controller`/`target`; the UI never says "pair", "provision", "controller",
  or "target".

Which device shows the code is decided by convenience, not by role: a TV or desktop shows the code;
a phone usually types or scans. After connection, *who controls whom* and *who sends accounts to
whom* are chosen in the post-connect step (§4). Owner decisions:

- **Control:** phones and desktops can control any other Naviamp device (phone, desktop, TV). A TV is
  only ever controlled; it never controls another device (no TV controller UI).
- **Sign-in goes every direction**, independent of who controls whom: any connected device with
  accounts can sign in one without them, including a TV or desktop signing in a new phone. Because
  a TV never initiates, the device in hand can both **send** ("Sign in Living Room TV") and **pull**
  ("Sign in this phone from Living Room TV"), and every Connect peer, TV included, must be able to
  serve its accounts and settings when the connected user asks for them.

### 2. Showing the code (target side)

The "show code" screen always displays, together:

- the 6-digit code, large, monospaced, grouped `482 913`;
- a QR code encoding a connect link (§3);
- the device's friendly name ("Living Room TV");
- a countdown; on expiry both code and QR regenerate automatically without user action;
- a "Can't connect?" disclosure with the device's address and port for manual entry.

Showing the code is consent to be connected and set up by whoever enters it. No Approve prompt is
shown for a connection that used the currently displayed code or QR.

Where it appears:

- **TV first run:** the setup screen shows code + QR immediately (no "Show pairing code" button),
  with "Set up on this TV instead" for the existing manual server form.
- **Every platform:** Settings → Devices → This device → **Let another device connect**.
- **TV:** also reachable from a visible Home/Settings entry, e.g. "Connect your phone".

The code is shown only while that screen is open. Leaving the screen stops accepting new
connections. There is **no background code**: remove the automatic non-consent code generation on
trusted targets. Trusted reconnects use the resumption path, which must keep working without a code
and independently of whether a code is on screen (fixes "Stop pairing blocks resume").

### 3. QR connect link

- Custom scheme: `naviamp://connect?v=1#<payload>`. Secrets live in the fragment.
- Payload (compact, versioned): device ID, friendly name, host candidates (LAN IPv4/IPv6, plus
  hostname if known), port, identity fingerprint, and a one-time **QR secret** of ≥128 bits.
- The QR secret is used as the J-PAKE password for that attempt instead of the 6 digits; the same
  handshake, transcript binding, and key confirmation apply. The scanned fingerprint must match the
  identity proof, else abort.
- Code and QR are issued together as one offer, share its lifetime, and both become invalid when the
  offer is used, expires, or the screen closes. Single use.
- Scanning inside Naviamp is the primary path. The custom scheme also lets the phone's system camera
  open Naviamp directly. If Naviamp is not installed, nothing happens (accepted for now).

### 4. Connecting from the other device

**Connect a device** opens one screen with, top to bottom:

1. **Camera viewfinder** (devices with a camera; asks camera permission on first use, with a
   graceful "type the code instead" if denied or unavailable).
2. **Code entry**: six boxes; numeric keypad; submits automatically on the sixth digit.
3. **Nearby**: devices on this network currently showing a code, as tappable rows. Tapping one
   preselects the target so only the code is needed (or the QR scanned).
4. **Can't find it?** → enter address and port, then the code.

Code entry without selecting a device: the code alone does not identify a device, so the
controller resolves it by trying the discovered devices that are currently showing a code (normally
one). If several are showing codes, ask the user to pick from the list. Builder may refine the exact
resolution approach (e.g. include a short discriminator in DNS-SD TXT), but the user must never need
to pick a device first in the common single-device case.

On success both screens move on automatically. The connecting device shows the
**post-connect sheet**:

```
Living Room TV is connected
  [x] Sign it in with my accounts
        3 accounts · settings · radio stations   (Change…)
  [ Play on Living Room TV ]   [ Done ]
```

- The sheet offers the direction that makes sense: if the other device has no accounts, "Sign it in
  with my accounts" (pre-checked); if *this* device has none, "Sign in this device from
  Living Room TV" (pre-checked); if both have accounts, both options appear unchecked.
- Pulling from a peer is allowed only within a connection the user just made with that peer's
  displayed code/QR, or with a trusted peer; the serving device shows "Sent your accounts to
  JB's Pixel".
- "Change…" lets the user deselect individual accounts or settings groups.
- "Play on …" selects the device as the playback output (existing queue handoff applies).
- "Done" just leaves it trusted and available.

The device that showed the code displays "Connected to JB's Pixel", then "Signing in…" →
"Signed in", then continues to Home. No action is required on it.

### 5. Sign-in transfer (one-time copy)

- Payload reuses the shared settings sync document (`SettingsSyncDocument`): all `serverProfiles`
  and portable preferences (interface, playback, visualizer, internet radio stations, recent radio
  streams, prefetch). Device-local fields remain local, as today.
- Credentials are added to that payload over the encrypted channel: each profile's stored
  **long-lived** secret — password, or an OpenSubsonic/Navidrome API key — plus secret headers and
  the Aurral companion secret. **Session tokens are never copied** (Subsonic token/salt, Navidrome
  native session, Jellyfin access token bound to the sender's DeviceId): the receiving device logs in
  fresh with its own device identity, so signing out or revoking on one device never breaks the
  other and token refresh never races. (Designer's call.) Passwords are never logged or persisted
  outside the provider's normal secure credential storage.
- Accounts using local certificate files cannot be transferred; they are listed as
  "Set up on the device" and skipped, never silently dropped.
- If the sending device has no stored secret for an account, the sheet marks it
  "Needs password" and asks for it on the **sending** device (once, optional per account). The
  receiving TV never prompts.
- Receiving device validates each account (existing validation path), saves the ones that validate,
  and reports per-account results. Activates the sender's active account if it validated.
- Merge into a device that already has accounts: add new accounts; for the same provider + server +
  username, update the credential; never delete existing accounts. Settings are applied only if the
  "settings" group was selected (default on for an empty device, off for a configured one).
- Afterward the devices are independent. "Your devices → Send my accounts again" repeats the copy.
- Settings round-trip rules in AGENTS.md apply: the transfer uses the same normalization and defaults
  as file import.

### 6. Presence, discovery, and reconnect

- Every Connect-capable device advertises while Naviamp is running in the foreground, with a TXT
  flag indicating whether it is currently showing a code. Untrusted peers only list devices showing
  a code; trusted peers are listed whenever reachable.
- Discovery runs automatically while the playback output picker, the Connect screen, or Devices
  settings is visible (and as needed for trusted reconnect). No "Find" button.
- Fixed default port with fallback to an OS-assigned port if busy; the actual port is what the QR
  and "Can't connect?" show. (Builder picks the number and documents it.)
- Trusted devices learn each other's availability and live status ("Playing 'Song'") through a
  lightweight **presence/status** exchange that never takes the target's single control session.
  The authenticated control session is opened only when the user acts (Play on / Continue on /
  open the remote), so two trusted phones in one house never displace each other just by being
  home. This presence channel is also what #348 uses. (Designer's call.) Nothing in presence moves
  or starts playback. The last chosen output device is remembered locally
  (device-specific, not exported); at launch it is shown as the suggested device, but audio stays on
  this device until the user picks it.

### 7. Output picker and handoff entry point (for #348)

- The existing output picker lists This device plus every trusted device with live status
  (Available / Playing "Song" / Offline).
- Tapping an available device while something is playing shows **Continue on Living Room TV**; this
  uses the current queue handoff (queue, index, position, repeat, shuffle) today, and is the exact
  entry point #348's prepare/commit transfer replaces later. Name and place it once so #348 does not
  need a UI redesign.
- Account parity is a precondition for handoff. If the target lacks the account that the queue
  belongs to, offer "Sign in Living Room TV first" inline (runs §5 for that account) instead of the
  current source-mismatch dialog.
- "Continue here" initiated from the TV side requires the TV to act as a controller of the phone's
  session; that is in #348's scope. Do not foreclose it: the presence model and trust record already
  serve both directions.

### 8. Settings information architecture

Rename the settings category **Controllers → Devices** on all hosts:

- **This device**: name (editable), **Let another device connect**.
- **Connect a device** (on devices that can control).
- **Your devices**: each row → rename, Send my accounts again, Forget. Same on TV.

"Forget" removes local trust and resumption credentials. The other side's next resume fails and it
marks the device "Connect again" rather than retrying forever.

Remove from the default UI: per-row "Reconnect to …" buttons, "Pair securely", "Selected: …",
"Set up … with this connection", standalone approval rows. Manual address moves under
"Can't find it?".

### 9. Approval prompts

Only for the unexpected. A connection that used the displayed code/QR, or a trusted resume, never
prompts. With background codes removed, there is no remaining path for an untrusted device to
connect without the code; keep the approval UI only if the builder finds a remaining case, and then
it must name the requesting device.

### 10. Platforms

- Shared Core owns all of the above: state machine, offers, QR payload encode/decode, sign-in
  payload, merge policy, presence policy, UI, strings.
- Android phone: camera scanning adapter; custom-scheme intent handling. Prefer a scanning library
  that does not require Google Play Services.
- Android TV: shows code + QR; no camera; no discovery browsing needed for control (TV is a target);
  TV gains Your devices management.
- Desktop: shows code + QR; types codes (no camera scanning); registers `naviamp://` where packaging
  allows (optional).
- iOS/tvOS: not wired now. Shared code must compile for iOS; adapters (Bonjour + Local Network
  permission, Keychain, AVFoundation scanning) follow later.
- QR rendering is pure shared code (no camera, no native dependency).

### 11. Compatibility and migration

- Bump the Connect protocol version (current = min = 1 as of v2.12.0). Designer's call, not a hard
  cut: an updated device still resumes and controls an already-trusted v1 peer, so existing
  pairings keep working. New connections (code/QR), sign-in, presence, and the no-approval flow
  require both devices on v2; a v1 peer is shown as "Update Naviamp on <device>" for those actions.
  The legacy approval/provisioning paths are kept only for v1 peers and deleted when min becomes 2.
- Existing trust records remain valid (resumption unchanged).
- Any schema change follows AGENTS.md migration discipline (single consolidated migration on the
  unreleased branch).

### 12. Delivery stages

Each stage is its own GitHub issue, branch, and PR, and each issue is blocked by the previous one.
Stages are not separate releases: whichever stages are merged when a release is cut ship together
(e.g. stages 1 and 2 in v2.14.0 if both are ready). Therefore **every stage must leave a coherent
shipped experience on its own**: stage 1 alone keeps today's active-account-only setup behind the
new post-connect sheet's "Sign it in" option. Stage 1 makes no wire-incompatible change (the new
"showing a code" flag is an additive DNS-SD TXT key; consent and approval are target-local policy),
so it keeps protocol version 1. The version bumps once, with the first wire-incompatible stage, and
later stages add negotiated capabilities rather than further bumps.

1. **One flow + new screens**: Devices IA, show code screen (code only at first), connect screen with
   auto-submit + nearby list, no background codes, consent = shown code, translatable strings,
   automatic discovery.
2. **Full sign-in**: all accounts + credentials + portable settings via the sync document, merge
   policy, per-account results, send **and pull** in every direction (including TV → phone), "Send my
   accounts again", sender-side password prompt.
3. **QR**: QR offer + secret in handshake, shared QR rendering, Android scanning, `naviamp://` links.
4. **Presence and device management**: fixed port, background reconnect, remembered output, live
   status in output picker, TV rename/forget, "Continue on …" naming.
5. Follow-on: #348 handoff protocol; #590 / Apple Connect adapters.

## Acceptance (overall)

- Fresh TV → phone: open Naviamp on TV, scan the QR (or type the code) on the phone, TV is signed in
  with all accounts and settings and lands on Home with no TV input after launch. Measure taps: phone
  ≤ 2 actions after opening Connect a device.
- Desktop (no camera) connects to a TV by typing the code.
- Phone signs in a fresh Desktop by typing the Desktop's code.
- Fresh phone scans an already-configured TV's or Desktop's QR and pulls its accounts.
- Desktop controls a phone; phone controls another phone.
- Already-configured TV: connect, sign-in unchecked by default, TV accounts untouched.
- Restart both apps: trusted device shows as Available without any code; playback does not move on
  its own.
- Forget on either side; the other side shows "Connect again".
- Wrong code: clear error, attempt limits still enforced; expired code/QR regenerates on screen.
- All new strings in `strings.xml` and translations; shared UI and behavior tests in common code.
- Real-device acceptance on Android phone ↔ Android TV and Desktop ↔ Android TV.

## Left to the builder

- Exact QR payload encoding, length limits, and QR error-correction level.
- How a bare code is resolved to a device when several are showing codes.
- Fixed port number.
- Scanning library choice (within the Play-Services-free preference).
- Layout details of the screens within the shared design system.
