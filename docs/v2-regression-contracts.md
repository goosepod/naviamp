# Naviamp 2.0 Migration Regression Contracts

These invariants protect behavior while Android and Desktop application ownership moves into the shared Naviamp runtime. A migration commit that changes an owner must keep the relevant contracts green and add a test when it introduces a new boundary.

- **Recorded:** 2026-07-16
- **Tracker:** [Naviamp 2.0 Cross-Platform Plan](v2-cross-platform-plan.md)
- **Baseline:** [Naviamp 2.0 Platform Baseline](v2-platform-baseline.md)

## Playback Session and Queue Restoration

Required invariants:

- Saved queue order, current occurrence, explicit Play Next boundary, and playback position survive restoration.
- Duplicate track IDs do not cause restoration to select the wrong occurrence.
- Invalid saved indices fail safely without replacing a live queue or Now Playing state.
- Track restoration updates both application state and the queue controller.
- Internet radio restoration clears track playback state and synchronizes an empty track queue.
- Restored track playback starts automatically only when Start Playing on Launch is enabled; otherwise sidecars may prepare without starting audio.
- Missing sessions and sessions without a usable playback target do not fabricate playback state.

Coverage:

- `core/domain/.../settings/PlaybackSessionMappingTest.kt`
- `core/domain/.../queue/PlaybackQueueTest.kt`
- `core/domain/.../playback/PlaybackQueueControllerTest.kt`
- `apps/android/.../AndroidPlaybackSessionControllerTest.kt`
- `core/app/.../NaviampPlaybackSessionControllerTest.kt`
- `core/storage/.../StoragePlaybackSessionStoreTest.kt`

## Playback Transitions and Service Lifetime

Required invariants:

- Prepared-next playback selects the correct queue occurrence and reason.
- Gapless and crossfade preparation respect engine capability and user settings.
- Normal completion retains an already-playing prepared source until automatic queue promotion adopts it; the incoming track must not restart at 0:00.
- Prepared-source identity survives a provider-stream-to-local-cache URL change for the same media item.
- A track is not prepared twice for the same queue position.
- Short tracks and near-end progress do not leave Now Playing behind the audio engine.
- Android service-owned playback remains authoritative when the Activity is recreated.
- Shared transition planning cannot depend on an Activity, Desktop window, or iOS view-controller lifetime.

Coverage:

- `core/domain/.../playback/PlaybackTransitionsTest.kt`
- `core/domain/.../playback/PreparedNextPlaybackServiceTest.kt`
- `core/domain/.../playback/PreparedBassPlaybackPlannerTest.kt`
- `core/domain/.../playback/PlaybackTrackStartEffectsTest.kt`
- `apps/android/.../playback/AndroidServicePlaybackRuntimeControllerTest.kt`
- `core/domain/.../playback/PlaybackProgressTest.kt`
- `core/domain/.../playback/PlaybackProgressEffectsTest.kt`

Android service reconnection needs a new contract test in the same slice that introduces the shared runtime/session bridge. The current code does not yet expose that bridge as a unit-testable interface.

## Provider Actions and Connection Restoration

Required invariants:

- Pending provider actions replay in stored order and successful actions are removed.
- A failed action remains available for retry without dropping later durable state.
- Replayed favorites, ratings, and playback reports preserve their intended values.
- Restoring a connection does not erase the existing playback session unless the user is starting a genuinely new connection.
- Source-scoped state is never restored into another server source.
- Provider capability gates remain authoritative for optional OpenSubsonic behavior.
- A provider-declared remote-ID transition runs at most once per source and target identity version.
- Remote-ID migration is one Core storage transaction: durable audio/download paths, sidecars, queues, history, keep-downloaded ownership, and pending actions either all move together or none do.
- Reproducible library/provider-response/artwork caches are invalidated instead of partially rewriting opaque provider payloads.
- Identifier migration never receives a native file-deletion capability and cannot remove cached or downloaded bytes.
- Rejected Navidrome native JWTs are cleared and persisted without clearing the independent Subsonic connection; only native smart-playlist work requests password reauthentication.

Coverage:

- `core/domain/.../provider/PendingProviderActionsTest.kt`
- `core/domain/.../source/ProviderConnectionLifecycleTest.kt`
- `core/domain/.../source/MediaSourceConnectionUpdatesTest.kt`
- `providers/navidrome/.../NavidromeProviderTest.kt`
- `providers/navidrome/.../NavidromeCanonicalIdTest.kt`
- `providers/navidrome/.../NavidromeCoreProviderSessionPortTest.kt`
- `core/storage/.../StorageProviderIdentityMigrationStoreTest.kt`
- Android and Desktop session-boundary tests listed above

The first shared connection coordinator must use the existing domain lifecycle functions instead of duplicating platform connection rules.

## Downloads, Cache, and Offline State

Required invariants:

- Download jobs preserve independent progress, failure, cancellation, and retry state.
- Retry includes only unfinished tracks.
- Keep-downloaded ownership does not remove manual downloads or media still required by another policy.
- Downloaded audio is preferred or used as fallback according to the saved playback policy.
- Cache eviction respects budgets and does not treat registered downloads as disposable cache entries.
- Platform storage locations affect paths and permissions, not product-level ownership rules.
- Native deletion fails closed unless the stored path identifies a regular, hash-named Naviamp audio file that is a direct child of the configured cache/download directory. A rejected path retains its ownership row.

Coverage:

- `core/domain/.../cache/DownloadJobsTest.kt`
- `core/domain/.../cache/DownloadPlansTest.kt`
- `core/domain/.../cache/KeepDownloadedCollectionsTest.kt`
- `core/domain/.../cache/AudioCacheEvictionTest.kt`
- `core/domain/.../playback/PlaybackAudioSourceResolverTest.kt`
- `core/presentation/.../NaviampCoreDownloadsControllerTest.kt`
- `platforms/desktop/.../cache/DesktopStorageRepositoriesTest.kt`

Native SQLDelight driver and migration tests are required when the storage factory is extracted and when the iOS driver is added. `core:storage` currently has no direct test source set.

## Settings and Credentials

Required invariants:

- Shared settings normalize invalid values identically on every platform.
- Settings synchronization round trips without silently dropping supported fields.
- Playback settings are reduced only when the active playback engine lacks a capability.
- Connection passwords, secret headers, and client-certificate passwords never enter general settings exports or unprotected platform persistence.
- Android continues using its Keystore-backed credential protector.
- Desktop credentials move out of the general settings JSON before the shared secret-store migration is complete.
- iOS credentials use Keychain and never `NSUserDefaults`.

Coverage:

- `core/domain/.../settings/PlaybackSettingsTest.kt`
- `core/domain/.../settings/SettingsSyncDocumentTest.kt`
- `core/domain/.../settings/SettingsSyncMappingTest.kt`
- `core/domain/.../settings/SettingsSyncCoordinatorTest.kt`
- `apps/android/.../security/AndroidCredentialProtectorTest.kt`
- `apps/android/.../security/AndroidBackupRulesTest.kt`
- `platforms/desktop/.../settings/DesktopCoreSettingsStoreTest.kt`

Each new shared settings or secret-store interface must have contract tests that run against a fake implementation. Each platform adapter must then prove persistence and protection behavior separately.

## Navigation, Application State, and Capabilities

Required invariants:

- Last stable content routes restore without reopening transient detail/player routes incorrectly.
- Application effects clear or retain state according to the shared domain decision, not platform-specific UI lifetime.
- Unknown platform capabilities fail closed.
- Experimental capabilities can be exercised during development but do not count as v2 release-ready.
- Shared UI checks capabilities rather than an operating-system name.

Coverage:

- `core/domain/.../app/NaviampNavigationRestorationTest.kt`
- `core/domain/.../app/NaviampContentStateTest.kt`
- `core/domain/.../app/AppStateEffectsTest.kt`
- `core/domain/.../app/PlatformCapabilitiesTest.kt`

## Extraction Gate

Before moving a behavior slice into the shared runtime:

1. Identify its invariants in this document.
2. Run the existing tests named for that boundary.
3. Add a failing characterization test for any behavior not yet represented.
4. Move the smallest coherent owner behind a shared contract.
5. Run shared tests plus both Android and Desktop boundary tests.
6. Update the v2 checklist and ownership table in the same commit.

Milestone 0 establishes these contracts. It does not claim that every future adapter test already exists; adapter tests are added when the corresponding interface becomes concrete.
