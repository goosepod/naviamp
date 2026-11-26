# Connect one-flow acceptance (#713)

First physical pass: 2026-11-26, debug build `v2.12.0-v2test` from `feature/713-connect-one-flow`
(commit `38b984213`), Pixel 6a (Android, USB) and onn 4K Pro Streaming Device (Google TV, same
Wi-Fi). The phone's test app had no music account, so sign-in scenarios are still open.

| # | Scenario | Expected | Result |
| --- | --- | --- | --- |
| 1 | Fresh Android TV + phone: open Naviamp on the TV, type the code on the phone, choose **Done** | TV signs in and lands on Home; no input on the TV after launch | **Pass** (second pass) — code shown at launch; sign-in pre-ticked; TV on Home with the library ~5 s after Done |
| 2 | Desktop → TV by code | Desktop connects by typing the code; post-connect sheet appears | Not run |
| 3 | Phone → phone by code | Connects; both list each other under Your devices | Not run |
| 4 | Already-trusted TV + phone by code | Phone connects with the shown code; no approval prompt | **Pass** — no TV interaction; sheet showed Play on / Done (no sign-in offered: phone had no account) |
| 5 | Trusted resume with the code shown | Phone reconnects; a shown code is not consumed | **Pass** (via *Connect at an address*) — "Reconnected to onn 4K Pro Streaming Device" while the TV showed a code |
| 6 | Wrong code | Clear message; TV shows a new code | **Pass** (one attempt) — "That code didn't match…"; TV code changed. Five-failure pause not exercised |
| 7 | Leave the code on screen past 5 minutes | A new code appears automatically | Not run |
| 8 | Previous release on the phone, this build on the TV | Phone resumes and controls the TV | Not run |
| 9 | Signed-in TV, new phone connects | Sign-in is not preselected; **Done** changes nothing on the TV | **Pass** (second pass) — sign-in unticked; TV source unchanged after Done |
| 10 | **Play on …** with music playing on the phone | Queue continues on the TV at the current position | **Pass** (second pass) — TV continued "Check Yourself" at ~2:1x; phone became the remote (position in step). Phone audio silence not confirmed by ear (phone volume muted) |
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

## Retest after fixes (2026-11-26, commit after `ec74bc4e2`)

Same devices, rebuilt and reinstalled.

- Finding 1 — **fixed**: with three older Naviamp devices on the network, typing the TV's code alone
  connected to the onn TV (the one device announcing a code).
- Finding 2 — **fixed**: the keyboard closes after the sixth digit.
- Finding 3 — **fixed**: the stale trusted entry now says "Connect onn 4K Pro Streaming Device again:
  show a new code on it and enter it here."; Connect trust is excluded from Android backup so a
  reinstall no longer restores dead entries (existing stale entries remain until forgotten).
- Finding 4 — **fixed** in all languages: manual-address messages no longer mention Tailnet, targets
  or pairing mode.
- Finding 5 — **fixed**: *Your devices* → device → **Connect** reconnected to the TV with nothing
  playing.
- Finding 6 — **fixed**: the post-connect sheet shows only "… is connected", **Play on …** and
  **Done**.
- Finding 7 (sign-in form label) — unrelated to #713; not changed here.

## Second pass with a signed-in phone (2026-11-26)

Both test apps were reset (data cleared); the phone was signed in to the test Navidrome account by
the owner. Scenarios 1, 9 and 10 pass (see the table). New small findings:

8. The TV first-run status line still says "Ready for a **controller** on this local network."
9. On the TV first-run screen, "**Or** connect to your server directly from this TV." appears above
   the phone/computer option it is the alternative to.
10. After closing *Connect a device*, the phone keeps showing "Searching this local network…" although
    discovery has stopped.
