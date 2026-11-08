# Aurral acquisition tracking (#478)

Local feature branch `feature/478-aurral-acquisition-tracking`, based on the locally
verified #503 waiting-for-release implementation. This work has not been pushed.

## Shared behavior and contracts

Core owns acquisition intent, recovery, status reconciliation, polling policy and UI.
Provider common code owns the Aurral wire contract. Shared SQLDelight storage owns
persistence; Android/Desktop/iOS only inject the shared repository from their existing
native database graphs.

Before an acquisition POST, persist an uncertain intent. Lost responses, cancellation,
restart and offline refresh must never cause automatic replay. Saved requests live outside
the replayable provider-action queue. Source ID plus account/server identity scope the
records. Music-folder filters do not fork an account's acquisition history. Passwords,
headers, native sessions and raw error bodies are never stored in acquisition payloads.

Aurral remains responsible for release dates and acquisition scheduling. Waiting requests
retain the server's bounded display date, and never schedule a future POST. Accepted,
queued, searching, in progress, downloading, importing, partial, finished, blocked, failed, cancelled,
unknown and playable states remain distinct. Completed history means the request finished;
it does not establish that files were imported or playback is possible.

A Recent requests surface is available from Discovery home and artist/release pages.
It retains local records and the last usable server history after refresh failure, with
an explicit stale-information message. It offers refresh, return to the scoped artist,
and a trusted connection-origin link to Aurral's Requests page. Review/retry/cancel remain
in Aurral; this change adds no unverified mutating endpoint or automatic retry action.
A request with a saved or uncertain intent cannot be resubmitted. The existing acquisition
button permits another submission after a definite rejection or a local persistence failure
that prevented the first POST.

Read-only reconciliation uses exact job IDs, request groups or manager-qualified album
IDs; ambiguous multiple history matches and matching names never establish ownership.
Aurral canonical IDs and Lidarr album IDs remain separate namespaces. Only the existing
canonical resolver plus actual Subsonic album validation can produce a Library album.
Recording requests additionally require the requested recording in the validated owned
recording set. Persisted state never carries a playable Library ID across restarts.

Automatic refresh runs only while the shared Home discovery artist/request surface is
visible and its lifecycle is started. Source/page changes and hidden/stopped lifecycle
cancel in-flight reads. Refresh serializes with submission, reads at most 200 history rows
and reconciles at most 20 local records per pass, oldest checked first. The request list
shows the most recent 200 local records; exact persisted intents still protect older
requests against replay. Active work polls every 30 seconds, bounded to eight reads per
visible session; failures back off to 60/120 seconds and stop after three failures.
Unsupported/forbidden/sign-in/password-required responses stop polling immediately.
Waiting dates, terminal server history and unrelated Flow/discovery activity never start idle polling. Reopening a surface,
reconnecting or manually refreshing resumes reconciliation through reads.

## Pinned provider evidence

Reviewed Aurral v2.13.0 source commit `3f01fa586f363f416f2e40c234a95550b87d74f7`:

- `backend/routes/requests.ts` and `backend/services/aurralHistoryService.ts`: authenticated
  user-visible requests/history; `completed` album request history is not file availability.
- `backend/routes/library/handlers/downloads.ts`: Lidarr numeric IDs for
  `GET /api/library/downloads/status?albumIds=...`.
- `GET /api/library/albums/aurral/:canonicalId/status`: requires an explicitly Aurral-owned
  canonical identity, and response owner/canonical ID agreement.
- Native bootstrap feature `activity` gates both acquisition status and request history;
  native user-session authentication and configured proxy headers remain in provider code.
- Canonical ownership and the validated Subsonic bridge reuse #477's contract.

No live acquisition, monitoring, feedback, scrobble or download was initiated for this work.

## Schema

The inspected accepted baseline (`origin/main`, `c5e119f2`) ends at `28.sqm`.
The single unmerged `29.sqm` adds `discovery_acquisition`, scoped to `media_source`
with cascade deletion. Its canonical table definition lives in `NaviampStorage.sq`.
Cache clearing preserves acquisition intent. Deleting a saved source removes its records.
Corrupt payloads do not break list rendering, but exact corrupt intent lookup fails closed
rather than allowing a repeat submission. No real user database was reset or repaired.

## Verification

Focused deterministic tests: **153 passed, zero failures/errors/skips**.

| Coverage | Tests |
| --- | ---: |
| Common acquisition policy: persisted intent before POST, crash/cancellation/restart, concurrent duplicates, account isolation, delayed import, exact recording ownership, qualified/ambiguous history, offline reads, bounded batch rotation and polling/backoff | 15 |
| SQL storage: file reopen, typed IDs/date round trip, cache preservation/source cascade, corrupt intent, recent-list eviction without losing replay protection, accepted-baseline migration | 5 |
| Common discovery and Home controllers: existing navigation/waiting behavior plus durable restart, cancelled polling, manual refresh after cancellation and stale-account status/open races | 37 |
| Aurral provider and HTTP errors: existing contract plus manager routes, authentication, feature gates, unknown/wrong ownership, qualified history and folder-independent account identity | 71 |
| Shared UI: existing artist/release/waiting behavior, request-list loading/staleness/source callbacks, empty/waiting/remote-completion states and keyboard/D-pad navigation | 23 |
| Real BASS/native preview integration, muted fixture playback | 2 |

Captured shared request fixtures at 360×760, 1100×800 and 1280×720. Visually inspected
360×760 and 1280×720 captures; controls and stale/completion copy fit without clipping.
The original artist-page D-pad path (Back → sample → Refresh → sample/play) still passes;
Recent requests lives in the artist header so it does not intercept that path.
All 12 new UI strings are present in English and all 16 maintained translations.

Final local matrix: **BUILD SUCCESSFUL in 4m 11s** (373 tasks; 101 executed, 272 up to date).
Android debug APK and packaged Desktop test app built successfully. Both iOS device and
simulator host source sets, shared testkit and their common dependencies compiled. SQLDelight
migration verification and `verifyCoreFirstArchitecture` passed.

Artifacts relative to this worktree:

- `apps/android/build/outputs/apk/debug/android-debug.apk` (49,399,375 bytes), SHA-256
  `7383292594d648ea679a22c5771fcbfbe51685337518d925a34f9e2d9afee378`.
- `build/local-test/Naviamp.app` (staged local Desktop application; not installed into a user profile).

The final local log is `/private/tmp/478-final-local.log`; request captures are under
`/private/tmp/478-request-captures`. These local files supplement this durable test record.

Live acquisition-through-playback, audible listening, physical touch/TV and screen-reader
acceptance remain separate from deterministic local coverage (#506). No issue is closed,
no remote branch is pushed, and no GitHub runner is dispatched by this local verification.

Reproduce the local matrix from this worktree with JDK 21, Android SDK and the Apple toolchain:

```sh
./gradlew -Pcompose.desktop.packaging.checkJdkVendor=false \
  :core:domain:jvmTest --tests '*DiscoveryAcquisitionTrackerTest' \
  :core:storage:jvmTest --tests '*StorageDiscoveryAcquisitionStoreTest' \
  :providers:navidrome:jvmTest --tests 'app.naviamp.provider.aurral.*' --tests '*NavidromeHttpErrorResponseTest' \
  :core:presentation:jvmTest --tests '*NaviampCoreDiscoveryControllerTest' --tests '*NaviampCoreHomeControllerTest' \
  :core:ui:jvmTest --tests '*NaviampAurralDiscoveryUiTest' --tests '*NaviampDiscoveryArtistPageTest' \
    --tests '*NaviampDiscoveryReleasePageTest' --tests '*NaviampDiscoveryWaitingForReleaseUiTest' \
    --tests '*NaviampDiscoveryRequestsUiTest' \
  :apps:android:assembleDebug :apps:desktop:stageLocalTestApp \
  :apps:ios:compileKotlinIosArm64 :apps:ios:compileKotlinIosSimulatorArm64 \
  :core:testkit:compileKotlinIosArm64 :core:testkit:compileKotlinIosSimulatorArm64 \
  :platforms:desktop:desktopTest --tests '*DesktopDiscoveryPreviewCoreIntegrationTest*' \
    --tests '*DesktopPreviewPlaybackIntegrationTest*' \
  :core:storage:verifyCommonMainNaviampStorageDatabaseMigration verifyCoreFirstArchitecture \
  --continue --console=plain --max-workers=4
```

Shared presentation, storage and Aurral provider Android/JVM/iOS compilation preceded host
wiring; the final host builds compile those shared dependencies again as needed.

## Platform diff audit

All six platform production changes are one-line repository delegation/injection; they add no
state, provider logic, retry, lifecycle policy, schema or UI behavior.

| File | Concrete existing platform boundary |
| --- | --- |
| `platforms/android/src/main/kotlin/app/naviamp/android/storage/AndroidStorage.kt` | Android `Context` and the native SQLite driver own the database resource; delegate to the shared SQL store. |
| `platforms/android/src/main/kotlin/app/naviamp/android/AndroidStorageDependencies.kt` | The Android `Context` storage lifetime wrapper forwards the same shared repository. |
| `apps/android/src/main/kotlin/app/naviamp/android/AndroidNaviampCoreCatalog.kt` | The Android application factory injects the repository from its `Context`-owned database graph. |
| `platforms/desktop/src/desktopMain/kotlin/app/naviamp/desktop/cache/DesktopStorageRepositories.kt` | The JVM filesystem/database-driver factory exposes its shared SQL repository. |
| `apps/desktop/src/desktopMain/kotlin/app/naviamp/desktop/app/DesktopComposition.kt` | The Desktop host injects the repository from its JVM filesystem/native database resource graph. |
| `apps/ios/src/iosMain/kotlin/app/naviamp/ios/NaviampIosApplication.kt` | The Apple host injects the repository from its native SQLite-driver/application database lifetime. |
