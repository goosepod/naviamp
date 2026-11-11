# Recent Radio station identity (#416)

## Confirmed cause

`NaviampRecentRadioStreamController.remember(stream, tracks)` previously generated a unique
`:session:<timestamp>` ID for every successful queue, with a collision suffix for repeated starts
at the same time. History deduplicated only exact IDs. Replaying a persisted queue used generic
playback without updating its recency. These are shared defects; no host implements this policy.

## Identity and reconciliation

- Source/account-scoped media source ID, Radio kind/mode, stable seed IDs and full multi-seed
  membership define a station together with normalized batch size, DJ tuning and similarity
  selection. Labels, cover art, resulting track count, random queue order and start time do not.
- Creation reuses the existing station ID and updates its retained queue/recency. Replay uses the
  saved queue and identity/options, updating recency after the playback effect accepts it. A common
  coroutine mutex serializes read/modify/save, and cancelled starts cannot persist phantom history.
- Existing duplicates reconcile newest-first, retaining the newest ID/recency and borrowing an
  older nonempty queue or covers only when absent. Reconciliation precedes the history limit and
  is idempotent. The settings value store durably reconciles older JSON on load as well as save.
- Legacy records with unknown generation options remain an unknown variant rather than being
  guessed equivalent to a known configuration. Historical tuning variants that were never stored
  cannot be distinguished; matching old source/mode/seed definitions consolidate into one legacy
  station, retaining its newest queue. Unknown-source records remain unscoped and are never
  assigned to an arbitrary account. A seed-only record may be upgraded after successful
  generation within its own scope. Missing seeds retain opaque identities rather than being
  merged by name. Old composite mix IDs cannot be safely split on colons; only identical old
  definitions consolidate. Composite stations without a saved queue report the existing incomplete
  status instead of silently rebuilding only the first seed.
- Additive history fields default to empty seeds/unknown options when absent. Export/import and
  sync retain them, normalize options, and reconcile equivalent history. Provider-ID migration
  transforms entity seeds, preserving genre names. No SQL schema or migration changes are needed.

## Verification

Common regressions cover equivalent/distinct seeds and settings, identical names/counts,
source/account isolation, complete mix membership, missing and ambiguous legacy seeds,
reconciliation, retained queues/covers, concurrent starts, cancelled starts, failed writes,
serialization/reopening, sync round trips, and repeated replay through Core.

`NaviampRecentRadioPresentationTest` renders the shared Home section after repeated replay
and checks that only one recent station remains. `verifyNaviampRecentRadioPlayback` is a reusable
common contract driven by the Desktop BASS/JNI test, using synthetic WAV over loopback and a
temporary file-backed settings store. It requires a moving playback clock for generation and
replays before/after Core reconstruction. Volume is zero; this is native playback verification,
not an audible listening test or a manual installed-app session.
The moving-clock probe explicitly skips a headless no-output device (`outputDevice=0`); that skip
is not native playback evidence.

The final common run passes 1,130 domain tests on each of JVM and Android, 10 Radio controller tests
on each, and 61 focused Core/presentation/serialized-store/rendered-list tests. Android compilation,
both common iOS target compilations, shared testkit JVM compilation and architecture verification
pass. Testkit compilation also passes for Android and both iOS targets. The real-output Windows
native probe passes in 16.864 seconds (one test, zero skips), retaining `library:session:2` through
six generation/replay actions and two Core instances. Recency advances from 101 to 106; native
progress moves on every action and resets after Core reconstruction. Settings use real temporary
files, the server binds loopback only, and the fixtures are removed after the test.

The common verification run succeeds in 2m35s; the native/testkit run succeeds in 2m30s. Initial
native build attempts exposed cached JNI paths for the removed JDK 21.0.12.101 installation.
Refreshing only the generated BASS/OpenGL CMake `JAVA_*` entries and selecting installed Temurin
21.0.13.9 resolves that toolchain failure; no production build scripts change.

```powershell
.\gradlew.bat :core:domain:jvmTest :core:domain:testDebugUnitTest `
  :core:app:jvmTest --tests '*NaviampRecentRadioStreamControllerTest' `
  :core:app:testDebugUnitTest --tests '*NaviampRecentRadioStreamControllerTest' `
  :core:presentation:jvmTest --tests '*NaviampCoreTest' `
  --tests '*NaviampCoreNowPlayingMediaControllerTest' --tests '*NaviampCoreSettingsValueStoreTest' `
  --tests '*NaviampRecentRadioPresentationTest' :core:presentation:compileDebugKotlinAndroid `
  :core:presentation:compileKotlinIosArm64 :core:presentation:compileKotlinIosSimulatorArm64 `
  :core:testkit:compileKotlinJvm verifyCoreFirstArchitecture --console=plain
$env:JAVA_HOME = 'C:/Program Files/Eclipse Adoptium/jdk-21.0.13.9-hotspot'
$env:CMAKE_EXE = 'C:/Users/ursasmar/AppData/Local/Android/Sdk/cmake/3.22.1/bin/cmake.exe'
.\gradlew.bat '-Pnaviamp.bass.platform=windows-x64' :platforms:desktop:desktopTest `
  --tests '*DesktopRecentRadioPlaybackTest' :core:testkit:compileDebugKotlinAndroid `
  :core:testkit:compileKotlinIosArm64 :core:testkit:compileKotlinIosSimulatorArm64 --console=plain
```

No Android, Desktop or iOS production files change. The Desktop test contains only native BASS,
loopback HTTP and filesystem fixtures; the station behavior contract lives in shared testkit.
