# Connect QR Acceptance (#715) — 2026-11-28

Devices: Pixel 6a (phone) and onn 4K Pro (Android TV), both running the `v2test` build of
`feature/715-connect-qr` on the same Wi-Fi network, with the test Navidrome server.

The phone's camera cannot be pointed at the TV from the workstation. Instead, the TV's screen was
captured and its QR decoded with ZXing (an independent decoder). The decoded link was delivered to the
phone as an Android `VIEW` intent, the same route the system camera uses. This checks that the
displayed symbol is readable and that `naviamp://connect` works end to end. The in-app viewfinder is
covered by shared UI tests with a host scanner fake. The live camera preview still needs a hands-on
scan (see Notes).

## Results

| Scenario | Result |
|---|---|
| TV with app data cleared: first-run screen | Pass: shows the QR next to the code and says "scan this QR code or enter the code" |
| Decode the QR from a TV screenshot | Pass: the `naviamp://connect?v=1#…` link carries the TV name, LAN addresses, port, fingerprint and offer ID |
| Open that link on the phone (system-camera route) | Pass: the phone connects with nothing typed and the post-connect sheet opens; Done signs the TV in; the TV lands on Home as the test account |
| Reopen the used link | Pass: "Can't reach …", because the TV app had restarted with a new port. The status appears in an app-wide dialog, since the user is not on the Connect screen |
| QR whose offer was replaced (TV panel closed and reopened, same port) | Pass: "That QR code was replaced…"; the TV keeps showing its current code and QR |
| QR with an altered secret | Pass: "Couldn't verify … from its QR code…"; the TV issues a new code and QR, and the new QR connects |
| Scan QR code with no camera permission | Pass: the system prompt appears on first use |
| Camera permission denied | Pass: "Camera access is off. Type the code instead…"; typing the TV's code connects |
| Manifest | Pass: every camera feature is `required="false"` (TVs stay eligible); the library's unused capture activity is removed |

## Notes

- **Found and fixed (predates #715):** connecting as a controller, including a background trusted
  reconnect, stopped discovery even while Connect a device was open. Discovery now keeps running while
  that screen is open.
- **Not fixed here:** on Android, a running discovery does not notice when a device it has already
  resolved starts showing a code. Legacy `NsdManager.resolveService` resolves once, so the device appears
  in Nearby only after Connect a device is reopened. Re-resolving belongs with presence and discovery in
  #716. A scan is unaffected, because the QR carries the device's addresses.
- **Still needs a hands-on check:** scanning with the in-app viewfinder and with the phone's system
  camera app.
- **Not covered by these device tests:** Desktop showing the QR, and a fresh phone with no accounts
  scanning a configured TV. Shared tests cover the scan flow on every platform. A fresh phone still
  reaches Connect a device through Settings → Devices.
