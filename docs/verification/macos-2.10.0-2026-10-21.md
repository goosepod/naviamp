# macOS v2.10.0 acceptance checkpoint — October 21, 2026

The owner accepts the Mac scenarios available to them, with all reported passing. This record
distinguishes that acceptance, agent observations, automated scenarios, and unavailable checks.

## Candidate and environment

- Source: `a0e8f7b36a2949bb84f2a48cdd5108f6e9dd0035`.
- Git tree: `a8c86ab32f5fd7e722458c3c1627711bef95eec3`.
- About and bundle metadata: **v2.10.0, build 60**.
- macOS 27.0.1, build 26A434; Apple Silicon; JetBrains Runtime 21.0.11; Metal.
- Packaged with `:apps:desktop:createReleaseDistributable`, using the normal Naviamp profile.
  No production source was changed for these checks.
- App, relative to the test worktree:
  `apps/desktop/build/compose/binaries/main-release/app/Naviamp.app`.
- Local archive:
  `build/manual-acceptance-macos-a0e8f7b36/Naviamp-v2.10.0-macos-arm64-manual.zip`.
- Archive SHA-256:
  `adbf4a509dc47a031c71cb6700218bfe943c8784bc271c9db8b96b7ad3175531`.

The installed `/Applications` copy reports v2.9.0/build 57. The candidate was opened separately
against the existing profile; it did not replace that installed bundle. This establishes existing-profile
retention, not a DMG installation/upgrade result. Settings and a consistent database snapshot were
backed up before first launch, and again before the agent's checks.

The same source passed the complete required matrices for [the repair PR](https://github.com/goosepod/naviamp/actions/runs/39643363551),
[the feature PR](https://github.com/goosepod/naviamp/actions/runs/39651034275),
[main preparation](https://github.com/goosepod/naviamp/actions/runs/39651031219), and
[release preparation](https://github.com/goosepod/naviamp/actions/runs/39651033285).

## Owner feedback

The owner reported: “Smart playlists and autoplay seem to be working fine on the mac.”
The owner subsequently reported that everything they could test was passing. This records owner
acceptance of the available Mac scenarios on this candidate. The follow-up does not enumerate
individual cases; unavailable and cross-device scenarios retain their unverified status. Release
publication remains a separate task.

## Observed in the packaged Mac application

| Scenario | Result and scope |
| --- | --- |
| About | Showed v2.10.0 Build 60. |
| In-app changelog | Certificate-protected connections led Features, followed by artist playlists. Improvements, fixes, upgrade notes and limitations rendered readably; the end was scrolled into view. |
| Artist search | `Rufus` returned `RÜFÜS DU SOL`, its Carlita and Kaz James collaboration credits, and Rufus Thomas. Portraits and placeholders were circular. |
| Album search | `Rufus` returned artist-matched albums including Lately, Pressure and Solace, retaining matches whose displayed title did not contain the query. Covers were square. |
| Song search | `Rufus` returned songs by the accented artist, including Alive and All I've Got, without an extra literal-title filter. |
| Query controls | Search and view controls stayed in place. Typing during album loading retained the entered query. No-match feedback and clearing back to browse data were observed. |
| Long results | The `all` song query continued through deep scrolling with matching results and no refresh panel. This was a visible scrolling check, not an HTTP pagination trace. Deterministic paging tests are listed below. |
| Tab/detail return | The artist and album `Rufus` queries survived tab changes. Opening an artist, then a matching playlist, returned to the same artist; returning again restored Library's artist query and matches. |
| Explicit refresh | Refresh with an album query active showed indexing feedback below the stationary controls. Clearing restored refreshed album browsing. Entering Albums initially also performed its browse-index load; that is distinct from typing a query. |
| Artist playlists | Existing regular and smart playlists appeared once, in name order, below the discography. Both kinds opened correctly. Generated smart-playlist tracks were read-only with the rules-edit action available. No existing playlist was edited. |
| Artist sections | Top Tracks loaded with ten rows. The main discography used its existing grid; playlist and appearance-album sections used lists, and individual appearance tracks remained rows. Artist artwork was circular and album artwork square. |
| Restart | After native-menu Quit and reopening, Home painted and the paused track and 0:54 position were retained. This does not establish minimize/restore or resize acceptance. |

Screenshots and accessibility states were inspected during the session. These visible observations
do not constitute an animation CPU/GPU budget measurement.

## Profile retention and isolation

Comparing the pre-agent snapshot with the live profile after the visible checks:

- All four source entries remained; the profile contains Navidrome, Jellyfin and Bandcamp sources.
- All 40 queue entries and the persisted current index/position were unchanged.
- All four download records were unchanged, and all four referenced files were present.
- Portable interface, playback and cache settings were unchanged.
- Database `user_version` remained 29.

The user's settings auto-export was enabled. Preference-mutation and failure-case work was therefore
prepared in an isolated profile with sync disabled, separate media paths, no inherited Connect pairing
identity, and no automatic keep-downloaded work. That test app did not start because the computer-use
connection failed before launch. No interactive preference/export/import pass is claimed for it.
The normal candidate process remained running. Private profile copies stay in ignored local build
output and are not included in the app archive or committed to GitHub.

## Automated scenarios executed locally on this Mac

All **494 JVM test cases reported success**, with zero failures, errors or skipped cases:

| Suite | Cases |
| --- | ---: |
| Shared domain: artist membership/navigation, popular matching, settings defaults/round trips, Radio rules/service, Library paging/jumps | 92 |
| Shared presentation: catalog/search, settings persistence/sync, playlist browsing | 76 |
| Shared presentation: artist details and playback controllers | 54 |
| Shared rendered UI: artist playlists/layouts/Top Tracks/appearances, Library search, Radio controls | 27 |
| Jellyfin provider protocol fixtures | 23 |
| Navidrome provider JVM suite | 217 |
| Desktop native mTLS integration/contracts | 5 |

The Navidrome suite includes six `NavidromeMutualTlsTest` cases. Together with the five Desktop
cases, these executed **11 real certificate/TLS or native-playback test cases**. Optional live-server
test entry points in the provider suite were not configured; their presence in the XML totals is
not evidence of a production-server test.

The real TLS/native tests used disposable loopback servers, generated identities and media,
production certificate private-copy/TLS adapters, the shared playback adapter, and Mac BASS.
They verified:

- Imported private certificate copies still work after selected originals are deleted and clients
  are recreated; browsing and artwork authenticate with the expected client identity.
- Missing/untrusted client identities, a wrong PKCS12 password, an untrusted server CA and a hostname
  mismatch fail closed. A valid positive control follows rejection checks.
- Private-server-CA-only connections work without a client identity.
- Real database close/reopen preserves the connection with production credential encryption and a
  disposable test key.
- Uncached WAV playback starts before the complete download; caching/prefetch are disabled. Pause,
  seeking to 90 seconds beyond buffered data, resume, authenticated finite ranges and stop within
  three seconds pass through the native playback path.

Other targeted tests cover all Top Tracks steps, hidden-state retention, independent List/Grid
choices and appearance rows, older export defaults and imported normalization, settings persistence
and sync mappings, artist ownership/secondary credits/partial failure, cancelled or stale lookups,
query-specific paging and clearing, explicit Radio methods and failure without substitution,
independent Radio/Autoplay sizes, regular Radio replenishment and queue-end Autoplay behavior.
Jellyfin ownership/mapping checks use protocol fixtures, not its saved live account.

Local evidence is under `build/manual-acceptance-macos-a0e8f7b36/`: `validation.json`,
`profile-retention.json`, copied XML in `test-results/`, and the three test logs. Commands are in
[testing.md](../testing.md); the selected test classes and individual methods are retained in
`validation.json`. Native file selection is injected in the contract tests, so they do not establish
acceptance of the visible macOS file chooser.

## Agent-unverified checks and tooling blocker

Computer use stopped working with `Sky Computer Use native pipe startup failed`. App discovery
failed as well, including after resetting the automation session. This is a tooling blocker, not
evidence that Naviamp crashed. Earlier select-all, AX value replacement, minimize and resize
attempts did not provide reliable verification; they are not marked passed or diagnosed as product
defects.

The following cases were not verified interactively by the agent. The later owner acceptance above
covers the scenarios available to them; it does not identify which individual cases below were
exercised. Do not infer failures or require repetition of accepted checks:

- Native window minimize/restore and interactive resize; narrow-window and translated-label checks.
- Physical keyboard selection/focus, accessibility and other owned-dialog lifecycle checks.
- Interactive Artist details switches, every Top Tracks maximum, all four section-layout
  combinations, restart, native settings export/import, and sync with another device.
- Live Radio method/diagnostic/top-up scenarios distinct from the owner's Autoplay feedback.
- Visible certificate import/password/file-copy/restart workflow; compressed formats/transcoding;
  source/track changes and stop during slow authenticated startup.
- Live Jellyfin ownership/refresh/failure cases, server-side playlist mutations, and the remaining
  platform/device checks in the shared checklist.

No release, draft, tag, announcement, milestone closure, merge into main, or mirror was performed.
Performance and the existing native accessibility follow-ups remain deferred. Owner acceptance of
available Mac scenarios is recorded above; unavailable checks and release publication remain separate.
