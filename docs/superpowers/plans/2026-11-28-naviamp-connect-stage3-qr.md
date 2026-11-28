# Naviamp Connect Stage 3: QR Connect (#715)

Design: `docs/superpowers/specs/2026-11-25-naviamp-connect-simplification-design.md` §3–4.

## Link

`naviamp://connect?v=1#n=<name>&h=<host,host>&p=<port>&f=<fingerprint>&o=<offer id>&s=<secret>`

- Fragment fields are percent-encoded. `s` is 16 random bytes (128 bits), base64url without padding.
- `o` is the offer's pairing session ID, so a stale QR is recognised before any J-PAKE attempt.
- The identity fingerprint identifies the device. A separate device ID would be unused, so it is
  left out to keep the symbol small. Error correction level M.
- Parsing in `core:domain` (`NaviampConnectLink`) validates every field and redacts the secret.

## Handshake

- The code and the QR secret are issued together. They share the offer's lifetime and pairing
  session ID, and both are replaced when the offer is used, expires, or the screen closes.
- `NaviampConnectHello.pairingOfferId` is additive. When it is present, the target uses the QR
  secret instead of the 6 digits as the J-PAKE password. If the ID is not the current offer, the
  target sends a plaintext `PairingExpired` error before closing, so the scanner can say the QR is
  out of date.
- The controller requires `offer.identity.identityFingerprint == f` and `offer.pairingSessionId == o`.
  Otherwise it aborts, as it does for a mismatched advertised fingerprint.
- Only new targets show QR codes, so older peers never see the field.

## Hosts

- `NaviampConnectTransportListener.localAddresses()` defaults to empty. The JVM/Android TCP listener
  lists non-loopback interface addresses.
- The scanner tries addresses of a discovered target with the same fingerprint first, then the QR
  hosts.

## UI (shared)

- `NaviampQrCode` encoder (byte mode, versions 1–40, all masks) and a `NaviampQrCodeImage` drawn once
  on a Canvas. It appears in the show-code panel, the TV first run, and TV settings.
- Connect a device: a "Scan QR code" button above code entry. It opens a viewfinder supplied by the host
  through `LocalNaviampQrScanner`. It asks for camera permission on first use. If permission is denied or
  there is no camera, the user is told to type the code instead.
- `naviamp://connect` links (from the system camera) go through `NaviampCoreCommand.Connect.OpenLink`.
  They open Devices settings with Connect a device and start the same flow.
- After a scan, the flow continues into the post-connect sheet.

## Android (platform boundary only)

- A camera viewfinder using zxing-android-embedded (no Play Services); the CAMERA permission request;
  `uses-feature camera required=false`.
- An intent filter for `naviamp://connect`; MainActivity forwards the URI string.

## Desktop

- Shows the QR; it has no scanner. Scheme registration depends on packaging and is not done here.

## Tests

- Domain: link round trip, tampered/invalid fields, redaction.
- Runtime and peer (real crypto): scan connects, wrong secret rejected, stale offer reports
  expired and the code still works, fingerprint mismatch aborts.
- UI: encoder decoded by ZXing in jvmTest, scan button with permission denied shows the fallback.
