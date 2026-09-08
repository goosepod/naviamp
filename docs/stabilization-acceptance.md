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
| Playlist membership | Shared coordinator, bounded reads, occurrence removal, permissions, partial failures, stale-response reconciliation; disposable-playlist Pixel pass. | Both hosts loaded membership and canceled without saving. macOS narrow creation dialog rendered correctly. Native originating-action keyboard focus and live failure cases remain unverified. |
| Favorite Artists / Home | Common sorting, source-scoped radio activity, settings persistence/sync, shared Home controls; Pixel restart and Windows sorting checks. | iPhone sorting survived restart and was restored; both hosts rendered Home and Favorite Artists. |
| Player / Aurora | Common workspace/navigation, waveform and normalized portable settings; shared UI and live Windows checks recorded. | iPhone playback/pause and portrait/landscape rendered correctly. macOS full/split/narrow layouts and queue passed. Aurora has automated coverage; exhaustive native control acceptance remains open. |
| Storage / architecture / translations | September 8 review passed architecture and migration verification plus 1,459 JVM tests. Migration 24 follows main's 23. New keys have English/Spanish parity. | Architecture, migration, aggregate coverage and Android debug/release unit gates passed. Local desktop schema drift repaired directly; no production migration added. Nine Spanish keys missing on main predate this branch. |
| iOS native | Earlier documents establish shared compilation, not a final native-host pass. | 1,462 Kotlin simulator tests and device compilation passed; signed Keychain XCTest passed. Production simulator app build, strict signature verification, install and iPhone interaction pass completed; see details below. |
| macOS desktop | Existing common UI and Windows evidence do not establish this host. | 1,680 JVM/Compose tests and 42 native Desktop/app-host tests passed; package verification/staging passed. Packaged-app interaction and warm restart passed; see details below. |
| Provider interoperability | Fixtures for Navidrome/Jellyfin/legacy/Bandcamp; live Navidrome protocol and Pixel genre/download/playlist checks recorded. | Basic legacy Subsonic Demo interaction passed on iOS; Navidrome LAN browsing passed on macOS. Live Jellyfin/Bandcamp browsing, artwork, streaming/native decode and disposable playlist checks passed on Pixel; adverse-network acceptance remains open. Bandcamp download compatibility correction also passed live verification below. |
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

Remaining acceptance: Windows/Linux native CI; current Android emulator/device coverage; large
real Appears On catalog and touch scrolling; native keyboard/focus and screen readers; live
network-failure scenarios; end-to-end artwork/catalog latency. The branch
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
