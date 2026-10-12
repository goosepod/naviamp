# Testing Naviamp

Naviamp uses a layered test gate so shared product behavior is exercised once in Core while the
irreducible native boundaries are tested on their actual platform.

## Local verification

Run the complete non-device suite from the repository root:

```shell
make test
```

This verifies the Core-first architecture rules, SQLDelight migrations from the committed version-1
database, shared and provider behavior, JVM/Desktop behavior, Android debug and release unit tests,
Desktop BASS/JNI loading and decoding, Android native-library packaging, and a 60% aggregate JVM
line-coverage floor. It prints a combined test count from Gradle's XML reports when complete.

For a faster Desktop-oriented loop, use `make desktop-test`. To regenerate coverage independently,
use `make coverage`; its HTML report is written to `build/reports/kover/html/index.html`.

On an x86-64 Linux machine, `make linux-verify` runs the native Desktop tests with a null ALSA
device, builds and stages the Linux application image, and verifies that the packaged launcher stays
alive for a bounded Xvfb smoke window. It requires JDK 21 or newer, `xvfb-run`, OpenGL development
libraries, `libsecret-tools`, and an Android SDK containing API 36 because the root Gradle build also
configures the Android application. Set the SDK with `ANDROID_HOME`, `ANDROID_SDK_ROOT`, or an ignored
`local.properties` file. Diagnostics are written under `build/linux-test`.

Kover measures JVM and Android unit-test execution. Kotlin/Native, XCTest, Android instrumentation,
and native C/JNI execution are intentionally represented by explicit CI gates rather than being
misreported as JVM line coverage.

## Opt-in Connect cross-device instrumentation

`AndroidNaviampConnectCrossDeviceInstrumentedTest` (discovery) and
`AndroidNaviampConnectPairingCrossDeviceInstrumentedTest` (pairing/control) require a coordinated
target and controller. Ordinary `connectedDebugAndroidTest` runs skip these two scenarios when
`connectRole` is absent. An explicitly supplied invalid role still fails, so invocation errors cannot
silently pass. Single-device Connect/native tests continue to run normally.

Select one of those test classes explicitly and run it on both participating test devices, starting
the target first. Use `-e connectRole target` on the target and `-e connectRole controller` on the
controller, with each device's explicit `adb -s <serial>` and installed instrumentation component:

```shell
adb -s <target-serial> shell am instrument -w -r \
  -e class app.naviamp.android.AndroidNaviampConnectCrossDeviceInstrumentedTest \
  -e connectRole target <test-package>/androidx.test.runner.AndroidJUnitRunner
adb -s <controller-serial> shell am instrument -w -r \
  -e class app.naviamp.android.AndroidNaviampConnectCrossDeviceInstrumentedTest \
  -e connectRole controller <test-package>/androidx.test.runner.AndroidJUnitRunner
```

Run the commands in separate terminals while the target is listening. Both devices need the
network topology required by the selected fixture; skipping these tests in single-device CI does
not establish cross-device acceptance.

## Multi-disc album fixture checks

For a reproducible check with real library metadata, save JSON `getAlbum` responses as
`album-*.json` in an ignored directory such as `build/issue-187`. Do not include credentials in
the fixture files. Save `getCoverArt` bytes for the album and each `discTitles.coverArt` ID
alongside them as `art-<id>.bin`, replacing characters outside `A-Za-z0-9._-` in the ID with
underscores. On Windows, run:

```powershell
$env:NAVIAMP_DISC_FIXTURE_DIR = (Resolve-Path build/issue-187).Path
./gradlew.bat :providers:navidrome:jvmTest --tests '*NavidromeAlbumDiscFixtureTest' :core:ui:jvmTest --tests '*NaviampAlbumDiscUiTest'
```

The provider check verifies that no disc/track numbers or disc artwork IDs are lost. The UI check opens Compose
test windows at Desktop and phone sizes, scrolls to every disc heading, and saves PNGs beside
the fixtures. These optional fixture checks run only when `NAVIAMP_DISC_FIXTURE_DIR` is set;
the synthetic grouping, metadata round-trip, and rendered interaction tests run in ordinary CI.

## CI verification

`.github/workflows/verify.yml` runs on every branch push and pull request and is also called before
release packaging. Its required matrix contains:

- shared/provider and Android debug/release unit tests plus aggregate coverage;
- Desktop BASS/JNI integration tests on macOS, Windows, and Linux;
- an Android emulator test that exercises Android Keystore and packaged BASS decoding; and
- iOS Simulator Kotlin/Native tests, device-target compilation, a production Xcode app build,
  native BASS decoding, and a signed XCTest host that exercises Keychain storage, replacement,
  deletion, and missing-value behavior.

Release artifacts cannot start packaging unless this reusable verification workflow succeeds.

## High-risk regression coverage

- SQLDelight checks every migration from `core/storage/src/commonMain/sqldelight/app/naviamp/storage/1.db`.
- Critical stores cover library/search/history, waveform-cache eviction, lyrics and offsets,
  provider-response and sidecar caches, pending actions, and radio-DJ presets.
- Provider contracts cover Jellyfin library selection, pagination, authorization failures, malformed
  responses, and Bandcamp's supported Subsonic profile and playlist limitations.
- A responsive Compose image test renders representative phone and Desktop track lists and checks
  track-number/title alignment semantically and at the pixel-output boundary.

## Smart playlist live acceptance

The opt-in `NavidromeSmartPlaylistLiveTest` requires a disposable Navidrome server and
`NAVIAMP_PLAYLIST_TEST_URL`, `NAVIAMP_PLAYLIST_TEST_USER`, `NAVIAMP_PLAYLIST_TEST_PASSWORD`,
and `NAVIAMP_PLAYLIST_TEST_DISPOSABLE=true` in the environment. Keep credentials out of source
files and command arguments. Without a URL, the test makes no server requests.

Run `:providers:navidrome:cleanJvmTest :providers:navidrome:jvmTest --tests '*NavidromeSmartPlaylistLiveTest'`.
The test creates a uniquely named playlist, checks supported save/reload without introducing
a limit or changing effective sort directions, then assigns nested rules to that fixture.
Inspection must retain those server rules and leave matching tracks accessible. The fixture
is deleted in a `finally` block, and missing-playlist inspection is checked after deletion.
Existing playlists and server configuration are not modified. This provider check complements
the shared UI tests; it does not verify native UI interaction, accessibility, or audio playback.

## Dependency audit policy

Patch and compatible minor releases may be taken on a release branch after the full matrix passes.
Toolchain or framework upgrades with migration requirements get a dedicated branch. As of the
2026-08-12 audit, Activity Compose 1.13.0, AndroidX Media 1.7.1, ProfileInstaller 1.4.1,
coroutines/serialization 1.11.0, and JNA 5.19.1 were adopted. Compose Multiplatform 1.11 and Android
Gradle Plugin 9.2 are intentionally deferred:
Compose 1.11 changes native graphics interop APIs, while AGP 9.2 requires Gradle 9.4.1 and an AGP 9
Kotlin Multiplatform migration. Kotlin 2.3.21, Ktor 3.5.0, SQLDelight 2.3.2, and Kover 0.9.8 are
already current stable versions.

### Smart-playlist representative tracks on a fresh profile

The preview reads the synced local track index, rather than the current server playlist
result. In a fresh profile, use **Sync library** in the builder before checking
representative tracks. **Settings > Debugging > Refresh library index** also remains available. The explicit action indexes artists, albums, and tracks
in shared code; ordinary catalog refresh stays lightweight. Confirm that a matching
track appears with its artist, that changing the filter changes the samples, and that
the preview clearly identifies local examples rather than final server order.

Regression coverage lives in `NaviampCoreCatalogControllerTest`: full index refresh
feeds the actual preview port, track-fetch failures do not complete the sync, and a
source change cancels completion of the old request.

### Library sync from the smart-playlist builder

The Preview area exposes **Sync library** in both creation and editing. It syncs
metadata through the same shared full-index operation as Settings; it does not
download audio. The action is disabled while its request is running. Rules and
server saves remain usable independently. Completion recomputes examples from the
current rules. Failure shows a localized retry message; closing the builder cancels
its request.

Check a fresh profile: open the builder, choose Sync library, wait for completion,
and confirm matching examples appear. Change a rule while sync is running and
confirm the completed preview reflects that rule. Disconnect the server and check
the retry message, then reconnect and retry. Common rendered UI tests cover these
state transitions, duplicate clicks, and dismissal cancellation; shared catalog
tests cover metadata population and source changes.

### Smart-playlist picker pointer and keyboard acceptance

Open a field or operator picker with a normal mouse click and with a held press.
The menu must appear after release and remain available for selection. Select an
option, reopen the same still-focused picker, press Escape, and reopen it again.
Type a search query and select a matching option; searching or dismissing must not
change the selected rule. Repeat for sorting and limits.

`NaviampSmartPlaylistPickerUiTest` exercises the shared picker inside dialogs,
including owned popup windows. The still-focused reopening regression failed before the menu-anchor fix. Held
presses check that the popup opens only after release. Generic dialog tests also
check that content clicks stay inside the dialog and outside clicks dismiss it.

### Smart-playlist calendar acceptance

For a date field such as Date Added, choose before/after and verify the visible
YYYY-MM-DD guidance. Open Choose date, select a day, and confirm Use date; verify
the value and preview update. Reopen and check that the saved day is selected.
Cancel or press Escape after selecting another day and verify the operand stays
unchanged. Typed invalid or impossible dates must still show the existing rule
validation. Check an imported leap-day date and a date before 1970.

Switch the same field to in the last/not in the last: verify a positive day-count
hint appears and no calendar is offered. Run the calendar UI tests with the owned
popup dialog enabled, and check the real Windows app for nested-dialog dismissal,
readable calendar colors, scrolling on a small window, and keyboard operation.
The calendar-to-ISO conversion is covered by common domain tests for leap years,
UTC boundaries, invalid values, and the full supported 0001–9999 year range.

### Text-entry caret visibility

Focus a shared text field in both dark and light palettes. Confirm the caret is
readable against the field surface, then use Home, End, and arrow keys to move it
and insert text in the middle of an existing value. Check smart-playlist names,
numeric/date operands, searchable field/operator pickers, and multiline imports.
Password entry should keep masking; disabled inputs should remain non-editable.
`NaviampTextCaretUiTest` checks actual foreground-colored caret pixels, movement
with the insertion position, and insertion in dark, light, and error-state inputs.
The color policy uses the built-in Compose caret; it does not add a separate
animation or change blink timing, selection, or keyboard behavior. Owned dialog
content uses the shared owner-group activation fact for window focus so an input
receiving typing can display its caret. The focus regression checks active and
inactive groups, native geometry preservation, and fallback to native focus when
no group fact is available.
