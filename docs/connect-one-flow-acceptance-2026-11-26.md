# Connect one-flow acceptance (#713)

First physical pass: 2026-11-26, debug build `v2.12.0-v2test` from `feature/713-connect-one-flow`
(commit `38b984213`), Pixel 6a (Android, USB) and onn 4K Pro Streaming Device (Google TV, same
Wi-Fi). The phone's test app had no music account, so sign-in scenarios are still open.

| # | Scenario | Expected | Result |
| --- | --- | --- | --- |
| 1 | Fresh Android TV + phone: open Naviamp on the TV, type the code on the phone, choose **Done** | TV signs in and lands on Home; no input on the TV after launch | Not run — phone has no account; TV test data not reset |
| 2 | Desktop → TV by code | Desktop connects by typing the code; post-connect sheet appears | Not run |
| 3 | Phone → phone by code | Connects; both list each other under Your devices | Not run |
| 4 | Already-trusted TV + phone by code | Phone connects with the shown code; no approval prompt | **Pass** — no TV interaction; sheet showed Play on / Done (no sign-in offered: phone had no account) |
| 5 | Trusted resume with the code shown | Phone reconnects; a shown code is not consumed | **Pass** (via *Connect at an address*) — "Reconnected to onn 4K Pro Streaming Device" while the TV showed a code |
| 6 | Wrong code | Clear message; TV shows a new code | **Pass** (one attempt) — "That code didn't match…"; TV code changed. Five-failure pause not exercised |
| 7 | Leave the code on screen past 5 minutes | A new code appears automatically | Not run |
| 8 | Previous release on the phone, this build on the TV | Phone resumes and controls the TV | Not run |
| 9 | Signed-in TV, new phone connects | Sign-in is not preselected; **Done** changes nothing on the TV | Partial — Done kept TV unchanged; sign-in option absent because the phone had no account |
| 10 | **Play on …** with music playing on the phone | Queue continues on the TV at the current position | Not run |
| 11 | Local-network permission denied on the phone | Recovery panel appears in *Connect a device* | Not run |
| 12 | Spanish and one RTL language (Arabic) | Devices pages and TV setup are fully translated and laid out correctly | Not run |

## Findings

1. **Older Naviamp devices crowd *Nearby*.** Devices on earlier releases don't send the new
   "showing a code" flag, so they are listed as candidates. With four Naviamp devices on the
   network, typing a code asked "Choose the device that shows this code" instead of connecting.
   Suggested fix: when exactly one device explicitly advertises a code, select it even if legacy
   devices are present.
2. **The keyboard hides the result of typing a code.** After six digits the "Choose the device…"
   message and the *Nearby* list sit under the on-screen keyboard; nothing visible happens until the
   keyboard is closed.
3. **Reinstalling creates duplicate trusted entries.** A reinstalled test app restored old trust
   records from Android backup but got a new identity, so both devices list the other twice
   ("Pixel 6a (1)/(2)", "onn 4K Pro… (1)/(2)"). The stale entry cannot reconnect (its saved secret
   no longer decrypts) and reports "Could not reach that address" although the TV is reachable.
4. **Old manual-address wording.** That error still says "Check the Tailnet, port, and target
   pairing mode"; the manual-address status strings were not updated.
5. **No way back to a trusted TV from *Devices* without a queue.** Reconnect lives in the Now
   Playing device picker; with nothing playing, only *Connect at an address* or a new code works.
6. **Post-connect sheet repeats itself.** Title "… is connected" plus "Connected to …." below it.
7. Unrelated (reported during testing): the server sign-in form's "Connection name (optional)"
   label overflows its field on the Pixel 6a.

Verified along the way: TV *Devices* page and code row, phone *Devices* page layout, the TV closes
Settings when a phone connects, the phone's mini player mirrors the TV, and the TV kept the same
listener port across code changes, a connection, and a resume.
