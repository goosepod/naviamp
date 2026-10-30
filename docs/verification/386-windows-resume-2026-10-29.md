# Resumed Windows candidate verification — October 29, 2026

Tracking: #386; follows [the earlier Windows record](386-windows-release-stabilization.md).

The owner requested reviewing and pulling today's changes, then resuming Windows tests. The owner
also confirmed the updated Android Auto application works well in the actual truck. That report is
recorded in [the physical Android acceptance record](android-signed-release-extended-2026-10-29.md).

## Integrated source and review

GitHub `main` remains `9783770a`; today's work is on unmerged issue branches. The isolated branch
`test/386-windows-oct29` starts from the earlier stabilization source `505dd7e8`, pulls
`chore/397-android-release-acceptance` with a fast-forward, and combines the remaining acceptance
and fix branches. Main, the original source branches and the user's initial checkout are unchanged.
Combined test source: `724103492bf4afe30a580e8d5854a99052840d07`, v2.10.0/build 60.

| Pulled branch/head | Reviewed change |
| --- | --- |
| `chore/397-android-release-acceptance` / `36c49fb5` | Connection editing/import shares player visibility; artwork/media rows retain spoken identity; Android optimized/signed upgrade, audio focus, offline and physical-device evidence. Includes #388/#389 and the later song-status-icon correction. |
| `chore/386-macos-acceptance` / `c181f99c` | Mac/iOS simulator, live provider and Android ARM acceptance evidence, including explicit limitations and later phone follow-up. |
| `fix/398-tls-checkbox-label` / `7690dabc` | The visible TLS label and checkbox form one named toggle target; two activations produce exactly two changes. Native phone acceptance is recorded. |
| `fix/399-audiomuse-backend` / `f0ff3ffd` | Operational evidence: AudioMuse's obsolete private-IP default was replaced by the reachable service alias. Phone Sonic Radio and natural-end Autoplay passed afterward. No Naviamp product patch or Windows/TV root-cause proof is inferred. |
| `docs/401-store-changelog` / `ad34a5f6` | Microsoft Store, Mac App Store and iOS App Store distribution lead all three release-note surfaces. |

The three-store branch was based on older main and conflicted with the complete stabilization
notes. Resolution puts the three Stores first and Aurral second while retaining every other frozen
entry, including imported connections, Home defaults and the paused/far-seek fix. The changelog
regression asserts both Store platform mappings and Aurral. The UI changes are common; no
Android/Desktop/iOS production file is added or modified by this integration.

No new setting, schema migration, feature, version bump, release tag, publication or server change
is introduced. The real-truck confirmation does not close the separate physical-TV queue case,
strict animation/GPU budget, or unperformed interruption/receiver checks.

## Windows run

The four loopback byte-range regressions pass. The full shared/provider/Desktop native gate,
architecture check, shared Android UI/presentation compilation, distributable validation and
release ZIP rebuild pass in 11m50s against `72410349`. Logs are retained in ignored
`build/windows-oct29/full-suite.log`; XML aggregate counts are in `full-summary.json` and the
complete native XML was retained in `full-native-xml` before the real-output targeted run.

| Suite | Reported cases | Failures/errors | Skips |
| --- | ---: | ---: | ---: |
| Core domain | 1,071 | 0 | 0 |
| Core app | 287 | 0 | 0 |
| Core storage | 64 | 0 | 0 |
| Core UI | 618 | 0 | 0 |
| Core presentation | 531 | 0 | 0 |
| Core testkit | 6 | 0 | 0 |
| Navidrome | 252 | 0 | 0 |
| Jellyfin | 35 | 0 | 0 |
| Desktop platform | 65 | 0 | 3 |
| Desktop host | 8 | 0 | 0 |
| Total | 2,937 | 0 | 3 |

Two skips require Linux MPRIS. The third preview/native-clock case separately passes with the real
default Windows output device and zero stream volume in 6.848 seconds. The targeted Gradle run
succeeds in 1m34s (`hardware-preview.log`). Optional live entry points without real credentials or
their fixtures do not establish a live-server pass. The four local HTTP range tests pass as well.

The new media-label and named TLS-control regressions pass, as does the earlier navigation case.
Both complete changelog render traversals pass. Their start/end captures were visually inspected:
all three Stores lead, Aurral follows, and the remaining sections remain present and scrollable.

## Remaining editor-entry defect found in the visible app

The packaged `72410349` app launches in a separate muted fixture-only profile. The split player
and a single bottom navigation bar render correctly. Opening Settings → Source → Connections →
Edit hides the split player and retains one bar, but displays the root Settings list instead of
the pending edit form. Selecting Source again reveals the form. This matches the extra Source
step in the earlier Mac report and is not considered complete native editor-entry acceptance.

The existing shared smoke regression initially checked only player absence and bar count. It was
extended to require `connection-name` immediately after Edit and its absence after cancellation.
That assertion fails on the combined baseline: the form's semantics node is absent.

Fix `319f6913` on `fix/388-windows-editor-entry` makes the common Settings owner select Source when
`isConnectionFormOpen` becomes true, including on remount after hiding the split player. It is a
one-shot shared effect, with no platform condition, new UI copy, persistent setting, TLS change or
continuous-animation change. The extended smoke regression now passes.

The follow-up gate succeeds in 4m20s (`editor-fix.log`): all 618 UI and 531 presentation cases pass
with zero failures/errors/skips; architecture verification, shared Android UI/presentation
compilation, distributable validation and release packaging also pass. No unchanged domain/provider
or native suite is reclassified as freshly rerun after this UI-only correction.

## Final native Windows checks and artifact

The rebuilt release executable directly opens the Edit connection form after clicking the saved
fixture's pencil, without a second Source selection. The split player hides and there is one bottom
navigation bar. Back cancels the form, restores the split player and returns to Connections. A
second entry repeats the correct behavior. Advanced fields visibly include one unchecked
`Skip TLS certificate verification` control. Inspection is read-only; no TLS, credential, server or
OS security setting is changed. The named toggle and activation counts are established by the
rendered regression, not a claim of a Windows spoken-screen-reader pass. Windows UIA still exposes
the native window shell rather than the complete Compose tree.

About visibly reports v2.10.0 / Build 60. Its actual Changelog shows Microsoft Store on Windows,
Mac App Store on macOS and App Store on iOS together first, followed by Aurral and certificate
support. Muted ordinary fixture playback advances from 0:00 through 0:24 while navigating About.
It pauses at 0:30, seeks to 5:24 while remaining paused, then resumes and advances to 5:33.

One attempted Source click was rejected after detected user input; the window was refreshed before
continuing. After inspecting lower fields, an attempted Back location hit the already-selected
Password tab; no authentication value changed. The form was scrolled back to its visible Back
button before the accepted cancellation. These input attempts are not additional product failures
or accepted interaction checks.

Final tested product fix: `319f6913`, tree `1f48cc18d1ae87c31e513154c1ba7cfaeae7aa48`.
Windows ZIP: `apps/desktop/build/compose/distributions/Naviamp-v2.10.0-windows-x64-release.zip`.
SHA-256: `44C5A61F5DD2903B64D76FAC19AE5ECE6F65115A7F10BD67457A7A0E8D7D8EC1`.
It supersedes the baseline integration ZIP whose SHA-256 was
`430476EB298A3EBB42172971F4AB54064921FBD5A513F9EAE87A1F03A58CC9CE`.
Subsequent documentation-only edits do not change the packaged source.

Only fixture/control data under ignored `build/windows-oct29` was used. The owned app and Python
fixture are closed. Normal settings, database and window-state hashes match the pre-run values.
The user's initial main checkout and unrelated research document remain untouched.

## Remaining acceptance scope

This resumed batch establishes functional Windows behavior for the combined fixes. It does not
close the existing strict animation CPU/GPU budget or Windows spoken accessibility gate. No new
performance pass is claimed from functional screenshots. The original physical-TV eleven-track
queue-end case remains separate from the successful phone/server Radio repair and truck report.
Hosted verification of the final combined source, immutable release-tag reconciliation and store
publication holds remain separate release gates. No canonical merge, tag, release, mirror or store
submission is performed by this test run.
