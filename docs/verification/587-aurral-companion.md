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
