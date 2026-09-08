# Library branch stabilization acceptance

Updated September 8, 2026. Branch: `feature/library-discovery-playlists`.
Implementation baseline: `c6b3c648`. The September 8 reporting fix and this acceptance record
accompany this checkpoint on that baseline; no release is implied.

This is the authoritative current acceptance record. The library plan, provider audit, and
Windows/workspace documents retain their dated implementation history. Their older unchecked
boxes, environment limitations, and references to uncommitted work are not current status.

## Scope and completion rule

Feature scope is frozen for stabilization: complete Library views and album indexing, expanded
discography, track playlist membership, Favorite Artists, the OpenSubsonic corrections, and the
shared player workspace/Aurora controls. Jellyfin Quick Connect and Television remain outside scope.
Only corrections, regression coverage, performance measurements, and acceptance documentation
belong in this pass. A shared compile is not native-host acceptance; synthetic fixtures are not
live-server performance evidence.

## Current acceptance matrix

| Area | Implementation and existing evidence | September 8 acceptance |
| --- | --- | --- |
| Reporting fallback | Common reporter and durable listen submission exist. Three new simulator regressions reproduce missing presence fallback after timeline failure. | Fixed in common code. All eight reporting tests pass in the full 123-test iOS app suite. |
| Library / album index | Common paging, stale-response guards, deterministic local ordering, atomic persisted snapshot, restart and failure regressions. Pixel and Windows interaction recorded. | iPhone navigation/search/restart and macOS 3,252-album indexing/restart/jump passed. Synthetic 2k/20k snapshot measurements recorded below; live end-to-end latency remains unmeasured. |
| Discography | Common classification and expansion; 125-album/track UI fixtures. Navidrome fallback depends on indexed credits. | Real large Appears On catalog remains unverified. |
| Playlist membership | Shared coordinator, bounded reads, occurrence removal, permissions, partial failures, stale-response reconciliation; disposable-playlist Pixel pass. | Both hosts loaded membership and canceled without saving. macOS narrow creation dialog rendered correctly. Native originating-action keyboard focus remains unverified. Interrupted membership saves passed on all three saved Pixel providers below. |
| Favorite Artists / Home | Common sorting, source-scoped radio activity, settings persistence/sync, shared Home controls; Pixel restart and Windows sorting checks. | iPhone sorting survived restart and was restored; both hosts rendered Home and Favorite Artists. |
| Player / Aurora | Common workspace/navigation, waveform and normalized portable settings; shared UI and live Windows checks recorded. | iPhone playback/pause and portrait/landscape rendered correctly. macOS full/split/narrow layouts and queue passed. Aurora has automated coverage; exhaustive native control acceptance remains open. |
| Storage / architecture / translations | September 8 review passed architecture and migration verification plus 1,459 JVM tests. Migration 24 follows main's 23. New keys have English/Spanish parity. | Architecture, migration, aggregate coverage and Android debug/release unit gates passed. Local desktop schema drift repaired directly; no production migration added. Nine Spanish keys missing on main predate this branch. |
| iOS native | Earlier documents establish shared compilation, not a final native-host pass. | 1,462 Kotlin simulator tests and device compilation passed; signed Keychain XCTest passed. Production simulator app build, strict signature verification, install and iPhone interaction pass completed; see details below. |
| macOS desktop | Existing common UI and Windows evidence do not establish this host. | 1,680 JVM/Compose tests and 42 native Desktop/app-host tests passed; package verification/staging passed. Packaged-app interaction and warm restart passed; see details below. |
| Provider interoperability | Fixtures for Navidrome/Jellyfin/legacy/Bandcamp; live Navidrome protocol and Pixel genre/download/playlist checks recorded. | Basic legacy Subsonic Demo interaction passed on iOS; Navidrome LAN browsing passed on macOS. Live Jellyfin/Bandcamp browsing, artwork, streaming/native decode and disposable playlist checks passed on Pixel; interrupted membership saves and retries passed on all three providers below; broader adverse-network acceptance remains open. Bandcamp download compatibility correction also passed live verification below. |
| Accessibility | Shared keyboard/semantics/contrast tests and Windows bridge packaging exist. | Screen-reader usability remains unverified. macOS native text entry could not be driven by automation; shared keyboard tests passed, but native keyboard acceptance remains open. |

## September 8 evidence

- `build/stabilization/ios-reporting-before.log`: eight reporting simulator tests; existing five
  passed and all three new fallback regressions failed before the fix.
- `build/stabilization/ios-suite.log`: 1,462 Kotlin simulator tests, zero failures/errors/skips.
  App 123; Domain 858; Presentation 257; UI 29; Storage 4; native iOS 12; Navidrome 155; Jellyfin 24.
  Production iOS/device and provider compilation, common Android/JVM compilation, and architecture
  verification passed. UI Kotlin tests do not substitute for native screen interaction.
- `build/stabilization/ios-keychain.log` and `ios-keychain.xcresult`: signed native XCTest passed
  (one test, zero failures/skips) on iPhone 17 Pro, iOS 26.5. It checks store/reveal, unique opaque
  references, absent/altered references, and deletion isolation. Xcode 26.6 / macOS 26.5.

Generated logs and native result bundles stay in ignored build output. The full Kotlin iOS command was:

```sh
./gradlew verifyCoreFirstArchitecture iosSimulatorArm64Test \
  :apps:ios:compileKotlinIosArm64 :apps:ios:compileKotlinIosSimulatorArm64 \
  :providers:jellyfin:compileKotlinIosArm64 :providers:navidrome:compileKotlinIosArm64 \
  :core:app:compileDebugKotlinAndroid :core:app:compileKotlinJvm --console=plain
```

Native Keychain command (the simulator destination is local to this machine):

```sh
xcodebuild test -quiet -project apps/ios/Naviamp.xcodeproj -scheme NaviampTests \
  -configuration Debug -destination id=A6BCDBC1-FE96-40AF-A48C-5FD19C745EDB \
  -derivedDataPath build/stabilization/ios-derived \
  -resultBundlePath build/stabilization/ios-keychain.xcresult
```

Production app used the same project, configuration, destination and derived-data path with
`xcodebuild build -scheme Naviamp`; `simctl install` installed it over the existing simulator app.

## macOS automated verification

`build/stabilization/macos-suite.log`: 1,680 JVM tests and 42 Desktop tests passed, with zero
failures/errors/skips (1,722 total). Architecture, SQLDelight migration verification, native BASS/JNI
and Metal builds, packaged native inventory verification, and local app staging passed. The run used
`-Pnaviamp.bass.platform=macos-arm64 -Pcompose.desktop.packaging.checkJdkVendor=false` with
`verifyCoreFirstArchitecture :core:storage:verifySqlDelightMigration jvmTest desktopTest
:apps:desktop:stageLocalTestApp`. The local package is `build/local-test/Naviamp.app`.

## iPhone interaction pass

Rebuilt production app installed in place on iPhone 17 Pro / iOS 26.5. The saved Subsonic Demo
connection remained usable. Verified Artists/Albums/Songs navigation; G album jump to the next
available group (Hanfplanet); album detail/Back position restoration; local Kosmonaut album search;
independent Robot song search; album-query restoration; song-menu membership loading and Cancel
without applying a server mutation; Favorite Artists navigation; artist biography/top tracks; Home
section settings; Date favorited selection surviving app termination/relaunch; and warm catalog
browsing after restart. Restored Name sorting and cleared both test queries.

Track selection started Diurnal Entropy, progress advanced, and Pause stopped playback. The
paused player, waveform and controls rendered in portrait and landscape. Restored upright portrait
and left playback paused. This is functional playback evidence, not audio-quality measurement.

A computer-use drag intended to scroll the artist page instead activated a track; wheel scrolling
also did not move that page. Do not count those attempts as touch-scroll or large-discography
acceptance. The demo library is small and cannot establish large-catalog or LAN-artwork latency.
No existing playlists, favorites, downloads or server settings were deliberately changed.

`ios-app-build.log` returned exit 0 alongside contradictory Xcode framework-copy diagnostics.
The built bundle passed `codesign --verify --deep --strict`; an incremental build with full
logging (`ios-app-confirm.log`) explicitly finished **BUILD SUCCEEDED** without those diagnostics.
The app then installed and ran successfully. Vendor frameworks were not modified.

## macOS packaged-app interaction and local database repair

Tested the staged Apple Silicon package against the saved NaviDoom LAN source. Its existing local
SQLite database reported version 25 but lacked all three tables introduced by this branch's
migration 24. Album browsing was empty. With the app closed, applied the canonical migration 24
statements only after checking the exact database, version, missing tables and existing parent
tables. Created `favorite_artist_activity`, `library_track_artist_credit`, `album_catalog_snapshot`
and the credit index. Existing rows and `user_version` were unchanged. This repairs development
schema drift, not a shipped upgrade; no additional production migration was created.
The targeted repair log is `build/stabilization/desktop-schema-repair.log`.

After reopening, indexing produced a persisted snapshot of 3,252 albums (1,193,221 JSON bytes).
Verified G jump, GIRL album detail, full player, split player with detail preserved, narrow stacked
player, and queue pane. At 347 × 740, album actions, membership picker, playlist creation form and
all ten equalizer frequency labels fit. Canceled pending membership choices and creation without
server writes. After quitting/relaunching, connection and warm album snapshot restored, including G
jump and covers. Restored Home, 1152 × 768 window, split preference, and the original paused track.

Native keyboard entry could not be exercised: computer-use typing, paste and individual-key
attempts produced no text, and the accessibility field was not settable. This is an automation
limitation, not evidence that native typing is broken. Shared Compose keyboard/focus tests passed;
manual native keyboard and screen-reader acceptance are still required.

## Catalog snapshot measurement

An ignored local JVM probe exercised the actual `AlbumLibraryIndex`, storage repository and
file-backed SQLDelight SQLite driver with synthetic 200-album provider pages. It indexed each
catalog once, then measured seven persisted reads and seven favorite updates. The probe and log
are under `build/stabilization/` (`probe-src/AlbumCatalogPerformanceProbe.kt`,
`catalog-probe.init.gradle`, `catalog-probe.log`).

| Albums | Cold indexing | Median snapshot read | Median favorite update | Maximum favorite update |
| --- | ---: | ---: | ---: | ---: |
| 2,000 | 473 ms | 31 ms | 30 ms | 97 ms |
| 20,000 | 1,084 ms | 35 ms | 84 ms | 101 ms |

This single local run excludes HTTP, artwork and rendered frame timings. The smaller case ran
first, so JIT warming affects comparisons. Whole-snapshot favorite updates remain an optimization
candidate; these measurements do not establish smooth UI frame times or live-server latency.
The real 3,252-album desktop run establishes functional indexing and restart, not a latency benchmark.

## Additional local release gates

`build/stabilization/local-release-gates.log` finished **BUILD SUCCESSFUL**. Architecture,
SQLDelight migration verification, aggregate Kover's 60% floor, Android debug/release unit tests
and Android debug BASS package verification passed:

```sh
./gradlew -Pnaviamp.bass.platform=macos-arm64 \
  -Pcompose.desktop.packaging.checkJdkVendor=false \
  verifyCoreFirstArchitecture :core:storage:verifySqlDelightMigration :koverVerify \
  :apps:android:testDebugUnitTest :apps:android:testReleaseUnitTest \
  :apps:android:verifyDebugBassNativePackage --console=plain
```

Final reports: JVM 1,680; Desktop 42; Android debug 1,460; Android release 1,460;
iOS simulator Kotlin 1,462; native Keychain XCTest 1. All have zero failures/errors/skips.
These are platform test executions, not distinct tests. Android unit/package checks do not
substitute for current Android emulator/device acceptance.

## Release gate

The complete reusable verification matrix remains required before release packaging, including
Android, Windows, Linux and native iOS coverage. This local iOS-then-macOS pass cannot substitute
for unavailable platforms. Release notes and an Announcements Discussion are created when the
release ships; feature-branch stabilization does not publish an announcement.

Remaining acceptance: Windows/Linux native CI; broader Android emulator coverage; large
real Appears On catalog and touch scrolling; native keyboard/focus and screen readers; live
network-failure scenarios beyond the playlist cases below; end-to-end artwork/catalog latency. The branch
is ready for continued acceptance, not a release-ready declaration.

The full verification workflow is on GitHub. The configured GitHub repository is public, and this
feature branch was absent there when checked on September 8. Normal `origin` is private Forgejo.
User decision: keep this branch off GitHub until release readiness. Do not publish it to trigger
CI. Remaining Windows/Linux verification must use local machines or private runners until then.

## Available acceptance environments

The user has no physical iOS device. Physical-device-only iOS checks (real audio routes,
interruptions and background/lock-screen behavior) are unavailable and must remain an explicit
validation limitation; simulator results do not close them.

A connected Pixel 10a running Android 17 is available for native Android acceptance. The largest
available live library is approximately 25,000 tracks on Navidrome (track count, not album count).
Use it for catalog scale and responsiveness checks. Smaller Jellyfin and Bandcamp servers are
available for compatibility testing once their connections are identified/configured; their size
does not prevent functional provider acceptance but cannot establish large-library performance.

### Connected Pixel native verification

On September 8, built `:apps:android:assembleDebug :apps:android:assembleDebugAndroidTest`,
updated both existing `.v2test` APKs with `adb install -r` (no data clear/uninstall), and ran only
`app.naviamp.android.AndroidNativeBoundaryInstrumentedTest` through AndroidJUnitRunner on the
Pixel 10a / Android 17. All three tests passed: Keystore encryption/reveal/tamper rejection,
packaged BASS decode/seek/read, and Core 5.1-to-stereo matrix application through native BASS.
Logs: `build/stabilization/android-device-build.log` and `android-pixel-native.log`.
This closes the current physical Android native boundary gate, not live UI/provider/lifecycle
acceptance. Live server tests were not enabled during this run.

### Live saved-provider acceptance on Pixel

Added `SavedProviderLiveAcceptanceInstrumentedTest`, explicitly gated by `liveSavedProviders=true`.
It restores credentials on-device through Android Keystore-backed storage and uses common provider
implementations. It prints stages/counts/error types, never credentials or authenticated URLs.
`livePlaylistWrites=true` enables disposable playlist creation, duplicate-occurrence removal,
addition and deletion in `finally`; it does not edit existing playlists. `liveCatalogScale=true`
enables complete Navidrome album enumeration using the actual common `AlbumLibraryIndex` with an
isolated in-memory snapshot repository. `liveAudioMode=stream` isolates streaming from downloads.

`android-live-streaming.log`: all three provider tests passed against the saved Navidrome, Jellyfin
and Bandcamp connections. Each passed connection restoration, album page/detail, album-title
search, artists/detail, playlist reads, sampled artwork, real audio retrieval, native BASS decoding
and seeking, and disposable playlist create/duplicate-removal/add/delete. Sample audio retrieval
was 248 ms Navidrome, 964 ms Jellyfin and 793 ms Bandcamp; these single samples are not comparative
benchmarks. No audible playback/UI lifecycle acceptance is implied by file decoding.

Navidrome's complete catalog contained 3,252 unique albums over 17 pages in 5,352 ms on the Pixel.
This measures network enumeration plus common sorting/progress work with an in-memory repository;
it does not measure persisted snapshot writes or rendered frame latency. Earlier download-mode
runs passed Navidrome and Jellyfin audio download/decode/seek.

Bandcamp's separate `download.view` request returned an error document despite reported download
permission. Its raw `stream.view` returned valid audio. The new common provider profile flag
`originalDownloadsUseStream` restores stream-based offline retrieval for Bandcamp only, retaining
account permission checks and offset stripping. Standard Navidrome/Subsonic still use `download`
for Original. This means the audio Bandcamp supplies through its streaming API, not a claim of
lossless purchased-master delivery. The new regression failed before the change; afterward all
154 Navidrome JVM tests and 156 iOS simulator provider tests passed, with Android and iOS-device
compilation. Logs: `bandcamp-download-red.log`, `bandcamp-fix-verification.log`.

During direct ADB UI checks, selecting Albums crashed with a missing `album_catalog_snapshot`
table in the existing v2 Test database. Inspection confirmed version 25, both other branch tables
present, and only `album_catalog_snapshot` missing. A local-only on-device helper created that one
table using its canonical migration-24 definition, leaving existing rows and version unchanged.
No production migration was added. The ignored helper was omitted from the final regular test APK.
Inspection/repair logs: `android-schema-inspect.log`, `android-schema-repair.log`. Android Studio mirroring was not enabled. The user
explicitly requested direct ADB control, which is used for all subsequent phone UI checks.

Final installed-build verification (`android-live-final.log`): all six tests passed: three live
providers plus three Keystore/BASS native tests. Bandcamp's corrected download path retrieved
1,971,871 bytes and decoded/seeks successfully. All three disposable playlists were deleted.
The repeat Navidrome album enumeration returned 3,252 albums in 5,164 ms. Android debug/release
provider unit suites each passed 154 tests; architecture, migration and native-package verification
passed (`android-final-build.log`).

Direct ADB UI checks after repair and in-place update passed Artists/Albums/Songs switching,
completed album indexing, G jump to G I R L, warm album browsing after app restart/reinstall,
native Galore album search, independent Amber song search, restored Galore query, album detail,
swipe to the last track, and Android Back restoring the filtered result. Cleared both temporary
queries. No audible playback was started in this UI pass; background/audio-route/interruption
acceptance remains open. Full Jellyfin/Bandcamp screen-by-screen UI acceptance and adverse-network
scenarios remain separate from the passing live provider/native-decode checks.

To repeat the live acceptance after building/installing the debug and Android-test APKs in place,
set `ANDROID_SERIAL` to the intended connected device and run:

```sh
adb -s "$ANDROID_SERIAL" shell am instrument -w -r \
  -e liveSavedProviders true -e livePlaylistWrites true -e liveCatalogScale true \
  -e class app.naviamp.android.SavedProviderLiveAcceptanceInstrumentedTest,app.naviamp.android.AndroidNativeBoundaryInstrumentedTest \
  app.naviamp.android.v2test.test/androidx.test.runner.AndroidJUnitRunner
```

The live tests choose the most recently connected saved source for each provider and require a
nonempty library. Omit `livePlaylistWrites` for read-only server checks. Playlist cleanup is bounded
and runs in a non-cancellable context so a cancelled test still attempts to delete its own playlist.
A failed remote cleanup must be resolved before calling a run clean. No secrets belong in runner
arguments or logs. These opt-in tests are not enabled by default in unattended device CI.

The final cancellation-safe harness was rebuilt and rerun: `android-live-cleanup-final.log`
finished `OK (6 tests)`, with all disposable playlist deletions confirmed. Production changes in
this checkpoint are confined to provider `commonMain`; no platform production files changed.

## Source-switch race acceptance

The user confirmed Android background playback, lock-screen controls, state synchronization and
network recovery worked as expected. This is user-verified evidence; the provider(s) covered were
not separately specified.

Direct ADB testing reproduced a source-isolation bug: Search showed the existing Navidrome Galore
results and artwork after Settings confirmed connection to JellyDoom. Four new deterministic
common tests failed before the fix: retained completed search, delayed search success/failure,
delayed playlist detail, and delayed album detail (`source-switch-red.log`).

The common connection lifecycle now synchronously resets catalog/search, playlist browsing and
media details when the source changes. Resets invalidate outstanding generations and clear both
UI state and shared media lookup records. Response guards compare provider ID, cache namespace
and selected libraries, not object identity: Core may recreate offline-capable provider decorators
on lookup. This also rejects late failures and avoids reintroducing old artwork/action targets.
Additional controlled tests cover switching away and back to the same provider, delayed artist
success/failure, album-index rows/artwork/snapshot isolation, and delayed playlist list outcomes.
Final verification passed (`source-switch-verification.log`): 1,689 JVM tests and 42 Desktop
tests, including 267 presentation JVM tests; 265 presentation iOS simulator tests; 265 presentation
Android debug and 265 release unit tests. All had zero failures/errors/skips. iOS device compilation,
Android app assembly and architecture verification passed. Aggregate coverage verification passed
separately (`source-switch-coverage.log`). The final race class contains eight tests, several looping
over success and failure outcomes; it is part of the shared presentation suite.

The fixed APK was installed in place on the Pixel. Timed direct-ADB checks passed:

- Navidrome album refresh showed **Indexing albums… 400 found**, then Jellyfin Connect was tapped
  2.123 seconds after Refresh. Search cleared; Library settled on Jellyfin's albums (starting with
  10 Years Parquet Recordings), and old Navidrome rows did not return.
- Jellyfin to Bandcamp cleared Search; Bandcamp's own album rows and its Test playlist (31 tracks)
  rendered without prior-provider content.
- A Navidrome Galore query was followed by Bandcamp Connect 1.085 seconds after text entry; Search
  was empty after connection. The reverse Bandcamp Veldt query to NaviDoom Connect took 1.176
  seconds after text entry, again leaving Search empty.
- Returning to NaviDoom restored its warm album catalog, starting with Cookie's Favorite Songs,
  with no stale Bandcamp rows or stuck indexing indicator. Returned to Home with no playback.

Phone timing alone cannot guarantee a particular HTTP response arrived after switching; the common
race tests explicitly hold responses until after the source changes and establish that ordering.
One preparatory Bandcamp-to-Navidrome attempt missed Connect because the list reset its scroll
position; it is not counted as a successful timed switch. The successful repeat used the visible
second connection. Evidence is in ignored `switch-nav-indexing.png`, `switch-nav-jelly-timing.json`,
`switch-nav-bandcamp-timing.json`, and `switch-bandcamp-nav-final-timing.json`.

No platform production files changed. No playlist edits or audible playback were started in this
pass. All source-switch production behavior and regression tests remain common.

## Interrupted playlist save acceptance

The shared track editor had a source-isolation defect: a delayed replacement success or failure
could publish into the connection selected afterward. The new regression failed before the fix
(`playlist-interruption-red.log`). Track replacement now captures the playlist browsing source
generation and stable provider identity, checks them after suspended work, and cancels stale
completion. Switching away and back also invalidates the operation. A switch during the initial
contents read prevents the write; an already-issued request may still commit on its original
server, but cannot publish success or failure into the new source. No platform production code
changed, and no new UI strings or settings were introduced.

Common regressions cover delayed replacement success/failure, switching away and back during the
initial read, and retry after a committed replacement loses its response. Membership tests cover
failure before commit and after commit with reconciliation unavailable, retry without duplicate
adds, and completion after switching while a write is pending. Playlist writes are not placed in
the durable offline action queue. Membership Retry rereads server contents; if the failed write
never committed, the user can select the desired membership again and Apply.

The opt-in Android saved-provider harness now accepts `livePlaylistInterruptions=true`. It runs the
real common membership coordinator with each saved provider, restricts discovery to one disposable
playlist, disables phone Wi-Fi and mobile data immediately before a real write, and restores the
original toggle states in cleanup. It separately discards a response after a real successful add
(test-injected response loss). Both cases check failed-save state, reconciliation, retry and exact
server contents. This is on-device controller/provider acceptance, not a visual UI automation pass.
Credentials stay in Android storage and no existing playlists are edited.

Final common/platform verification (`playlist-interruption-final.log`) passed: 1,694 JVM tests,
42 Desktop tests, 270 presentation iOS simulator tests, and 270 presentation tests in each Android
debug/release variant, all with zero failures/errors/skips. Aggregate coverage passed. Architecture,
iOS device compilation and Android app/test assembly passed in `playlist-interruption-platforms.log`.
Five new common tests were added; several exercise multiple failure outcomes.

The first live harness used a fixed one-second delay after network toggles. Diagnostic runs showed
writes could still succeed during Android's asynchronous network teardown, so these attempts are
not counted as offline acceptance. All their disposable playlists were deleted. The harness now
waits for `ConnectivityManager.activeNetwork` to become null before sending the offline write.

A subsequent harness run exposed a second test timing issue: Jellyfin session validation does not
prove a fresh request can reach the server after Wi-Fi is enabled. Recovery now waits for an actual
playlist read to succeed. The one disposable Jellyfin playlist left by that failed cleanup was
removed in a separate, narrowly filtered cleanup pass (`playlist-interruption-cleanup.log`):
Bandcamp 0, Jellyfin 1, Navidrome 0 deletions; `OK (3 tests)`. Cleanup selected only names with this
run's `Naviamp interruption ` prefix and numeric timestamps after 2026-09-08 17:20 UTC.

The corrected Pixel run passed all three providers (`playlist-interruption-phone-verified.log`,
`OK (3 tests)`). Each confirmed no active network before the real offline add, failed-save state
without false success, successful recovery and retry with exactly one added occurrence, and the
same reconciliation after a successful add whose response was deliberately discarded. Each
provider confirmed deletion of its disposable playlist. Wi-Fi and mobile data were checked as
restored, and the app was reopened on Home with Nothing Playing.

Reproduce this opt-in pass on the explicitly selected phone after installing the debug app and test
APKs in place:

```sh
adb -s 5A131JEA306253 shell am instrument -w -r \
  -e liveSavedProviders true -e livePlaylistInterruptions true \
  -e class app.naviamp.android.SavedProviderLiveAcceptanceInstrumentedTest \
  app.naviamp.android.v2test.test/androidx.test.runner.AndroidJUnitRunner
```

This pass does not establish atomic rollback for a server request already accepted, process-death
recovery during a multi-request replacement, or server permission/authentication failures. Precise
provider-switch ordering was tested with common deferred-response gates; live interruption tests
exercised the membership coordinator and real providers, without navigating the visual editor.
All changes remain local; nothing was pushed to GitHub or another remote.

## Process termination and download recovery

The current download job list and playlist editor draft are in-memory state. Process termination
loses pending intent; neither is a durable transaction queue. This pass verifies reopening from
server/persisted contents and explicit retry, not automatic resumption or rollback of an accepted
server write. A partially written Android audio file remains `.tmp` after force-stop until that
track is downloaded again; it is not registered as a completed download.

Two common tests were added: a cancelled audio transfer can be retried through the same shared
byte-store service without retaining a failed in-flight entry, and a fresh membership coordinator
reconstructs pre-commit or post-commit server state without replaying the old draft or adding a
duplicate. Both passed with the existing implementation (`process-death-common.log`). No production
code, settings, strings or schemas changed.

The opt-in saved-provider Android harness supports `liveProcessDeathPhase=prepare|recover` and
`liveProcessDeathCase=playlist-before|playlist-after|download-partial|download-complete`. Prepare
writes test-only metadata in private app storage and suspends at an explicit `PROCESS_READY`
checkpoint. A host ADB runner force-stops the target package, confirms its PID is gone, and starts
a separate instrumented recovery process. This is real process death; coroutine cleanup cannot
stand in for it. Test metadata contains only source/track/playlist identifiers, never credentials.

Playlist cases stop immediately before the add reaches the provider or after a real server add
returns but before the coordinator receives success. Recovery creates a fresh common membership
owner, checks authoritative contents, applies the desired membership if necessary, verifies exact
occurrences, and deletes its disposable playlist. Download cases use the real shared SQLDelight
audio store and Android file adapter, with an isolated test directory and tracks not previously
downloaded. They stop after at least 16 KiB of partial audio or after the completed file/database
record exists. Recovery checks row/file state, retries partial transfers, confirms existing complete
files require zero new network transfers, decodes audio with native BASS, then removes only its
own download rows/files and marker. Existing user playlists and downloads are preserved.

Physical Pixel results (`process-death-phone.log`): **12/12 force-stop/recovery cases passed**, four
per provider across NaviDoom/Navidrome, JellyDoom/Jellyfin and Bandcamp. Each prepare run was
intentionally killed at its checkpoint; each fresh recovery run finished `OK (1 test)`. Partial
transfers required exactly one new audio request; completed transfers required zero, and recovered
audio decoded successfully in every case. All six disposable playlists and all six test downloads
were removed. A final device check found no `acceptance-death-*` markers/directories, and Naviamp
was reopened. No network settings were changed during this pass.

Remaining limitations: job/draft persistence and automatic resumption are not implemented; a partial
temporary file can remain if the user never retries that track. These tests do not cover termination
between individual requests inside a multi-step playlist replacement or between the final audio
rename and database insertion. They exercise the shared controllers/provider/storage layers inside
real Android processes, rather than driving the visual editor and Downloads screen through touch.

Final verification passed (`process-death-verification.log`): 1,696 JVM tests, 42 Desktop tests,
1,130 domain/presentation iOS simulator tests, and 1,130 domain/presentation tests in each Android
debug/release variant, with zero failures/errors/skips. Architecture and aggregate coverage checks
passed. The phone test APK build passed separately (`process-death-build.log`). The app reopened
on Home. Changes consist only of common/native-boundary tests and this acceptance record; no
production platform files changed and nothing was published to a remote.

## Download failures while the process stays alive

Common regressions reproduced two source-isolation failures (`download-failure-red.log`): an old
failed job could retry its track IDs using the newly selected connection, and a late thrown transfer
failure could overwrite that connection's status. Download jobs now retain their originating source;
the screen filters jobs by source, rejects cross-source retry, and restores the original source's
retryable jobs when returning. The shared connection lifecycle clears download selection/snapshots
on source changes. Transfers already running retain their captured original provider/source.

A shared SQLDelight regression also reproduced loss of an existing file when replacement exceeded
the download quota (`download-quota-red.log`). The byte-store service now enforces a byte budget
while writing the temporary file, before the native adapter replaces the target. Storage calculates
the remaining budget including replacement credit and serializes download writes in its common
owner so concurrent jobs cannot each spend the same remaining quota. A failed replacement preserves
both original bytes and its database record. Existing quota messages are reused; no new settings,
strings, schemas or platform production behavior were introduced.

Regressions include immediate cancel/retry, source switching and returning, oversized/exact-limit
byte streams, and preservation of original bytes/database metadata after replacement failure.
The first common verification passed (`download-failure-common.log`); Kotlin reported an incremental
compiler cache failure and successfully fell back to full compilation. Android assembly similarly
recovered from that cache issue. These compiler diagnostics are not failed test assertions.

The opt-in phone harness uses `liveDownloadFailures=true` with the three saved providers. It uses
only previously undownloaded sample tracks and an isolated test directory, keeps a reference download
whose hash must remain unchanged, interrupts a real transfer after its first chunk, waits for a fresh provider request
to confirm network loss, reconnects and retries, cancels/retries immediately, forces zero-quota new
and replacement failures, and switches the common controller to another real saved provider while
an old-source transfer is held. Retry on the new source must not start a request; returning to the
original source must permit retry. Any fully buffered transfer that finishes despite network loss
is recorded separately and followed by a fresh offline request; it is not called a failed transfer.

The final common/platform run passed (`download-failure-verification.log`): 1,701 JVM tests,
42 Desktop tests, 860 domain and 274 presentation iOS simulator tests plus 4 storage simulator
tests; Android domain/presentation tests were 860/274 in both debug and release. All had zero
failures/errors/skips. iOS device compilation, architecture and coverage passed. The final storage
suite was rerun after extending the preservation test to throw after writing a partial chunk
(`download-storage-final.log`); it passed. This controlled partial-write failure supplements the
phone's buffered-transfer observations.

Live setup diagnostics are retained: initial network-registration waits timed out, and the first
cleanup did not wait long enough for the next provider's restore. The final harness waits for a
fresh provider request to fail after disabling data/Wi-Fi and for a real read to succeed after
restoring them. All timed-out passes removed their test files. One later Navidrome switch checkpoint
timed out; its isolated rerun passed (`download-failure-navidrome-final.log`). The checkpoint is now
bounded to 30 seconds with sanitized diagnostic state. This does not establish a root cause for
that intermittent timeout.

Combined live runs also observed DNS/reachability failures immediately after network handover.
The final harness checks sustained loss with a second failed server request after ten seconds,
and allows ten seconds of recovery settling followed by another successful read before starting
the next case. These waits are test setup, not new product retry policy. An earlier failed combined
run must not be mistaken for a passing acceptance result simply because isolated provider repeats
passed. All run logs remain in ignored `build/stabilization/`.

The final combined Pixel pass succeeded (`download-failure-settled-phone.log`, `OK (3 tests)`,
162.248 seconds): all four scenario groups completed for Bandcamp, Jellyfin and Navidrome. Each
verified reference bytes and database metadata remained unchanged, and each removed its test files.
All sampled held streams completed from buffered audio after loss; fresh offline downloads failed,
then succeeded after reconnection. True partial-write failure is established by the controlled
shared storage regression, not by claiming those buffered responses failed mid-stream.

The phone's Wi-Fi and mobile data toggles were verified restored to 1/1. No isolated
`acceptance-download-failures-*` directories remained, and the app was reopened. This checkpoint
changes common production owners only; the Android changes are opt-in tests of real credential,
network and file effects. Existing user downloads were not selected for mutation. No remote push
or GitHub publication was performed.

Remaining coverage includes truncated/empty successful server responses, physical disk I/O errors,
and larger unbuffered audio streams. The observed network handover failures remain recorded; the
successful final run establishes these scenarios after sustained loss and settled recovery, not
instant DNS readiness when Wi-Fi is switched on.
