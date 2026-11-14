# Issue 587 — Aurral companion integration

## Product contract

A saved primary connection may enable an independent Aurral account in its connection settings.
Standalone Aurral remains available. The Aurral Home entry uses the existing shared Home section
settings: it can be hidden and reordered in regular and television layouts. An entry without an
explicit saved position defaults to the top, including existing customized Home orders. These
preferences round-trip through settings export/import and sync. Hiding Home does not disable
contextual actions. The primary provider owns library IDs, queue, playback URLs,
reporting, downloads, Radio, capabilities and cache namespace. The existing dedicated Aurral hub
owns discovery, recommendations, catalog search, trending, monitoring and acquisition requests.

A companion-only edit does not reconnect the primary, clear playback, restore a saved queue or
create another primary account. Disabling the attachment retains its protected configuration for
later use. Authentication errors and unsupported/unavailable Aurral responses stay in discovery.
Passwords and proxy/client-certificate secrets use the existing host credential protector and never
enter portable settings. Imported profiles retain local companion secrets only for the same endpoint
and username. Account and primary-source changes invalidate discovery reads and pending actions.

Artist details offer **Find in Aurral**. Internet Radio Now Playing offers the same action when an
announced title is available. These actions perform read-only catalog searches. Radio metadata is
search context, not a trusted artist or recording identity. The user selects a catalog artist and
then explicitly selects monitoring, a sample track or a release recording before any mutation.
Aurral's published catalog protocol supports artist/album search, so a radio title of the form
`Artist - Recording` seeds the artist search; the full announcement remains comparison context.
The search is editable and does not claim that the first name match is the intended artist.
Opening discovery preserves a docked side-by-side player; a full-screen player yields to the search.
Radio context uses the page text color. Connection guidance is shown during setup rather than
repeated on discovery pages. Album editions are collapsed by default below the track list in the
same shared page for standalone and companion Aurral. Contextual searches preserve the current
account’s feed and hub capabilities; an account reset clears that feed and binds the new account
before its refreshed capabilities are published.

Aurral acquisition status describes the companion's account. It is not proof of availability in
the primary library. Companion discovery never forwards Aurral library artist/album IDs into the
primary provider. Automatic cross-provider library matching is deliberately absent until it can
be backed by verified identity contracts.

## Common ownership and persistence

- Domain: attachment configuration, normalization, form validation and portable export/import.
- Shared storage: protected attachment payload on the primary source. Equivalent library scopes
  of one server account receive the same attachment configuration.
- Provider common code: composition of the primary provider with Aurral native discovery and
  independent preview transport. No primary protocol request is used to authenticate discovery.
- Core presentation/UI: configuration, certificate-picker target, action routing, stale-result
  checks, status, navigation and contextual entry points.
- Existing OS certificate pickers and credential protectors execute unchanged through their shared
  contracts. No Android, Desktop or iOS production file changes are required.

Accepted main baseline: `e2da3aaf5ce8acbd008a9ef3ccf595474e7ed253`; its latest SQLDelight migration is
`29.sqm` (schema 30). The single #587 migration is `30.sqm` (schema 31). Historical migration fixtures
must remove the new column before assigning an old schema version; production migrations must not
be changed to accommodate a fixture constructed from the current canonical schema.

## Local verification

The full feature matrix passed before the owner’s presentation refinements:

```text
jvmTest verifyCoreFirstArchitecture
:core:storage:verifyCommonMainNaviampStorageDatabaseMigration
:apps:desktop:stageLocalTestApp
:apps:android:compileDebugKotlin :apps:android:testDebugUnitTest
iosSimulatorArm64Test -x :platforms:ios:iosSimulatorArm64Test
:apps:ios:compileKotlinIosArm64
```

The exclusion is the pre-existing platform-host simulator suite; shared iOS domain, presentation,
provider, storage, UI and Core application suites ran. The iOS device application compiled.

Shared regression coverage includes primary playback/queue isolation, same-account credential
retention across sync and library scopes, protected payloads, migration from schema 30, independent
companion authentication/TLS/sample transport, invalid endpoint guards, stale reads after account
changes, certificate-picker targeting, unchanged-form reconnects, and read-only contextual search.

The opt-in `AurralCompanionLiveTest` also passed against the dedicated acceptance account. It logged
in and loaded real recommendations, catalog search and artist details, and checked that synthetic
primary playback URLs remained unchanged. It performed no monitoring or acquisition mutation.
This does not constitute a real audio-device playback or end-to-end acquisition acceptance test.

The connection form was rendered and visually checked at 850×1100; its capture is
`/tmp/naviamp-587-qa/companion-connection.png` (fake account details). All 16 maintained translations
passed the catalog validation alongside the English source. No host production adapters changed.

Final JVM results across the initial matrix and focused refinement runs: domain 1,147; storage 75;
UI 759; presentation 622; Core app 298;
Navidrome/Aurral 343 (one opt-in live case skipped in the regular run); Jellyfin 36. All failures and
errors are zero. The opt-in live case passed separately as described above.

Home tests cover default visibility for older exports, portable preference round trips, shared
mapping, immediate settings updates, hidden/unavailable entries, rendered standalone and companion
ordering, and TV remote traversal in both directions. The existing Home settings list was rendered
and visually checked with Aurral as a draggable entry.

The final presentation refinements were rechecked with the full domain/UI/presentation JVM suites,
Android debug compilation and unit tests, shared UI and presentation iOS simulator suites, and the
staged Desktop app. Final iOS device application compilation passed. Shared UI has 199 passing
native cases and presentation has 610; all have zero failures and errors.

Build the physical-device candidate with `:apps:android:assembleDebug`; the APK is written to
`apps/android/build/outputs/apk/debug/android-debug.apk`.

The staged macOS app was launched from `build/local-test/Naviamp.app` using the development data
profile. The owner tested attachment to an existing Navidrome connection and Internet Radio catalog
search, then reviewed the corrected Home placement, split-player navigation, text contrast, collapsed
editions and hub sections. The visible hub was also checked for Recommendations, Search, Trending,
Recent requests and Genres. The updated test app remains open.

Physical Android-device acceptance and Windows manual testing remain pending with the owner.
The debug APK uses the existing `app.naviamp.android.v2test` package and **Naviamp v2 Test** label.

## Pixel-to-TV companion provisioning correction (2026-11-13)

Tested the correction on branch `codex/587-companion-pairing`, based on PR #605 head
`308428defc35af5677261c787d7add32a39d5ab4`. The original provisioning form and wire profile
omitted the saved companion. Shared provisioning now exports/imports its enabled state, endpoint,
credentials, TLS verification flag and custom headers through the encrypted setup offer. An
already-matching primary saves the companion without reconnecting or replacing playback state.
Older targets must advertise companion support before receiving a compound offer, preventing
silent partial setup. Legacy primary-only offers retain an existing attachment. Device-local
certificate files still require direct setup on the target. No platform production files changed.

Focused Windows JVM verification passed:

```text
.\gradlew.bat :core:domain:jvmTest :core:presentation:jvmTest :providers:navidrome:jvmTest verifyCoreFirstArchitecture --max-workers=2 -Pkotlin.native.enableKlibsCrossCompilation=false -Pnaviamp.bass.platform=windows-x64
.\gradlew.bat :apps:android:assembleDebug :apps:android:testDebugUnitTest --max-workers=2 -Pkotlin.native.enableKlibsCrossCompilation=false -Pnaviamp.bass.platform=windows-x64
```

JVM totals: domain 1,148; presentation 626; Navidrome/Aurral 344 (one opt-in live test skipped).
Android app unit tests: seven. All failures/errors are zero. Shared iOS simulator tests also passed
on `naviamp-mac` using `--no-daemon :core:domain:iosSimulatorArm64Test
:core:presentation:iosSimulatorArm64Test :providers:navidrome:iosSimulatorArm64Test --max-workers=2`:
domain 1,148; presentation 614; provider 314; zero failures/errors/skips. This focused fix validation
does not replace the broader feature matrix or Windows manual UI acceptance described above.

Installed the same fixed debug APK on the physical Pixel 6a and onn 4K Pro Streaming Device.
APK SHA-256: `5A55DEC01A947A0831F67B611FF38F75E8C9F854E28F2E0348E12111571DD0B3`.
Used isolated synthetic primary/Aurral accounts and local fixtures; no acquisition mutations.

- Paired the Pixel and TV with a code, then sent the Pixel's connection using the setup action and
  approved it on the TV. Both the primary account and Aurral companion appeared on TV Home.
  The phone reported secure setup completion. The companion fixture verified its password and
  required secret custom header, then accepted authenticated discovery requests.
- Stopped the Pixel app and restarted the TV. The companion persisted and independently returned
  an Aurral catalog search result on the TV; the phone process remained stopped.
- Changed only the Pixel companion credentials and repeated setup against the same primary.
  The TV authenticated as the updated companion account. Database comparison showed exactly one
  primary source, with only `aurral_companion` changed; its two queue rows were byte-for-byte equal.
  Playback remained paused at 45.48 seconds. Only the expected now-playing presentation flag and
  state timestamp changed, with no playback position or queue replacement.
- Restored both original test-app profiles from pre-test backups. Every file in the backed-up
  database, preference and file directories matched its original SHA-256 (Pixel: 13 files; TV: nine).
  Removed the fixture port reverses, stopped the fixtures and returned both devices to Home.

Local artifacts are under `build/pr605-pairing-fixed/`: `tv-fixed-both-accounts.png`,
`pixel-fixed-first-success.png`, `tv-fixed-independent-results.png`,
`tv-fixed-repeat-success.png`, `repeat-transfer-result.json`, `restoration-result.json`,
`companion-requests.jsonl` and `ios-tests.log`. Windows build logs are in
`build/pr605-pairing/fix-shared-tests.log` and `fix-android-build.log`. Device archives contain
private profile data and remain local; they must not be committed or uploaded.

## Windows acceptance and remaining draft gate (2026-11-13)

Tested PR head `5b8439b86ae30cda89a2a2c87e6f1e5cec22dd25` in the newly packaged Windows x64
application. A disposable profile under `build/pr605-windows/profile` used only synthetic loopback
primary/Aurral accounts and silent media. The installed application and owner profile were untouched.

The following command passed after setting `CMAKE_EXE` to the installed SDK executable
`C:/Users/ursasmar/AppData/Local/Android/Sdk/cmake/3.22.1/bin/cmake.exe` (the first packaging attempt
could not find CMake on PATH):

```powershell
.\gradlew.bat :apps:desktop:stageLocalTestApp :platforms:desktop:desktopTest :core:storage:jvmTest :core:ui:jvmTest :core:app:jvmTest verifyCoreFirstArchitecture --max-workers=2 '-Pkotlin.native.enableKlibsCrossCompilation=false' '-Pnaviamp.bass.platform=windows-x64' --console=plain
```

Regular suites: Desktop adapter 72 cases (three skipped), storage 75, shared UI 759 and Core app 298;
zero failures/errors. Packaging and architecture checks passed. Build log:
`build/pr605-pairing-fixed/windows-acceptance-build.log`; summary:
`build/pr605-windows/test-summary.json`. The hosted Windows native check also passed on this head.

Visible Windows checks passed:

- Started a two-track primary queue, observed the playback clock advance, then paused it.
- Attached Aurral through the connection editor with an independent password and a custom header
  marked secret. The fixture accepted authentication and authenticated discovery reads.
- Compared SQLite snapshots before/after Save: one primary source, only `aurral_companion` changed;
  both queue rows and the complete playback-session state remained equal, at 13.19 seconds paused.
  The stored companion used the Desktop protected credential prefix and contained no plaintext
  fixture password.
- Opened the companion hub, recommendations and catalog search beside the docked primary player.
  Submitted search with Enter, saw the result and circular artist placeholders, and navigated Back.
- Stopped Aurral: discovery retained its result and displayed the retry guidance. Primary playback
  resumed and its clock advanced. Restarted Aurral and refreshed successfully.
- Closed and relaunched the Windows app: Aurral independently authenticated again, the Home entry
  remained present, and the primary queue returned paused at the same position.
- Hid and restored the Aurral Home entry through Experience settings, confirming both results.
  Closed the disposable application and stopped both fixtures afterward.

**Keep PR #605 in draft:** rechecking the earlier directional-focus reproducer still fails on this
head. In `NaviampConnectionUi.kt`, Down from the primary form's collapsed Show Advanced action
requests the Connect/Save focus requester directly, skipping the Aurral companion checkbox. This
is a shared remote/keyboard setup defect, separate from the corrected pairing transfer. The regular
UI suite does not cover this transition and its passing result does not resolve this failure.

The isolated regression renders `NaviampConnectionForm` at 850x1100, requests focus on
`ConnectionAdvancedActionTestTag`, sends `Key.DirectionDown`, and asserts that Aurral companion is
focused. The assertion fails. It was injected from an ignored build directory through a temporary
Gradle init script; no production or tracked test sources were changed. Recheck artifacts:
`build/pr605-windows/reproducer/Pr605CompanionRemoteReviewTest.kt`, `reproducer.init.gradle`,
`remote-focus-recheck.log` and `remote-focus-result.xml`. Fix this transition, add permanent shared
regression coverage and retest the remote path before removing draft status. At the final hosted
check snapshot, shared/Android, API 30 emulator and iOS simulator checks were still running; none
had reported failure. This acceptance does not claim screen-reader speech or audible output.
