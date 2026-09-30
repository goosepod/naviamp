# Artist and album downloads for v2.9.0

**Issue:** [#150](https://github.com/goosepod/naviamp/issues/150)
**Related larger initiative:** [#27](https://github.com/goosepod/naviamp/issues/27)

## Product decisions

- Keep the existing download byte store and shared transfer service. Extend the shared subscription,
  ownership, planning, and job contracts around them.
- Offer a one-time album download separately from **Keep downloaded**. Keep downloaded follows
  membership changes; a one-time download never silently becomes a subscription.
- An artist subscription defaults to a bounded, explicit selection. Offer the full discography only
  after showing its scope and requiring an explicit choice. Do not silently truncate a provider
  result and treat it as complete.
- Retain the existing all-favorite-tracks and playlist subscriptions. Favorite artists and albums
  are opt-in; favoriting alone does not initiate a download.
- Keep one physical copy of each source/track/quality. Represent each reason to retain it separately.
  Removing one reason deletes the file only when no other subscription or manual retention needs it
  and the user selected removal.

## Implementation sequence

1. **Inventory and migration baseline.** Inspect `main`'s latest SQLDelight migration and the
   existing `downloaded_audio`, keep-downloaded, settings, and file-store behavior. Record the
   current upgrade baseline before changing schema. Add a migration fixture populated with existing
   downloads, playlists, favorite tracks, and overlapping memberships.
2. **Shared ownership model.** Add source-scoped download intents and collection membership in Core
   and shared storage. Existing `downloaded_audio` rows remain the canonical byte inventory. Backfill
   a conservative manual-retention intent for existing downloaded tracks whose provenance cannot be
   proven. Existing keep-downloaded policies retain their saved membership. Make backfill idempotent.
3. **Shared collection resolution and planning.** Enumerate favorite albums and artists through
   provider-neutral Core contracts, with provider-specific interpretation in provider `commonMain`.
   Detect incomplete enumeration. Resolve album/artist track IDs, deduplicate physical transfers,
   estimate new bytes, enforce the storage budget, and show large-artist scope before transfer.
4. **Shared job recovery.** Persist request identity, source, quality, items, outcomes, and retry
   information. On startup, reconcile records with complete files and restart incomplete tracks
   safely. Claim byte-range resume only after the provider and byte-store contracts can verify it.
5. **Shared UI and controls.** Add per-album, per-artist, and deliberate per-track actions, clear
   subscription state, progress, failure, retry, and removal choices. Keep all new copy in every
   maintained string resource. Add portable settings to export/import/sync only if the design needs
   new settings; verify old-export defaults and normalization.
6. **Verification.** Test overlap, membership changes, source isolation, failures, cancellation,
   restart, metered networks, storage limits, and unsupported providers in common code. Compile
   Android, JVM/Desktop, and iOS common targets before any host wiring. Add only native lifecycle
   or file effects that cannot live in common Kotlin, then inspect every platform diff separately.

The SQLDelight all-history migration verifier currently reports a pre-existing column-order
discrepancy for `media_source.authentication_mode`. Resolve or document that verifier baseline
separately; do not treat a passing targeted download migration test as a substitute for the full
release verification gate.

## Existing-download upgrade gate

- Upgrade an actual previous-release database fixture with downloaded tracks at multiple qualities,
  an album, a playlist, favorites, and overlapping memberships. Verify the same physical paths,
  byte counts, source IDs, track metadata, and offline playback after migration, with no transfer or
  deletion called during schema upgrade.
- Preserve files when old data cannot establish whether a person downloaded them manually. A
  conservative manual-retention record is safer than removing a user's music during reconciliation.
- Interrupt and rerun migration or startup reconciliation; verify idempotence and no duplicate
  collection membership or physical bytes. Verify a missing file is reported and repaired only for
  its own track. A failed migration must leave the old inventory usable or fail without modifying
  physical files.
- Test downgrade/rollback from the release candidate where supported. An older app must not erase
  existing `downloaded_audio` rows or files merely because it cannot read the new intent tables.
  Document any database-version incompatibility and recovery path before tagging the release.
- Check offline cold launch after upgrade and storage-path changes on Android, Desktop, and iOS.
  Do not require users to redownload valid existing audio.

## Release boundary

Issue #150 is complete when favorite artist and album subscriptions are bounded, observable,
recoverable, and safe for existing downloads. The broader normal-library offline navigation and
search experience remains #27 unless it is explicitly brought into this milestone with its own
acceptance and verification.

## Implementation checkpoint

The shared implementation is on `feature/150-artist-album-downloads`:

- Migration `27.sqm` backfills a conservative manual-retention record for every existing downloaded
  track without changing its audio row or file path. Multiple qualities share one retention reason.
- New one-time downloads add manual retention after the track completes. Automatic subscriptions
  do not acquire manual retention, including on retry.
- Reconciliation preserves a managed track when manual retention or another policy still requires it.
  Explicit removal releases retention only after the stored download is gone.
- JVM tests cover migration, source isolation, overlap, and retry classification. Artist and album
  favorite catalog enumeration tests cover complete pages, explicit bounds, unsupported providers,
  and Navidrome/Jellyfin mapping.
- Complete album-track and primary artist-album pages now feed a shared preview planner. It
  deduplicates overlapping tracks, counts existing downloads, estimates new bytes when metadata
  permits, and rejects catalogs over 200 artist albums or 2,000 tracks before transfer. Provider
  fixtures verify the paged member queries.
- Shared album and artist detail actions now show subscription state. A bounded preview counts
  tracks, existing downloads, and estimated new bytes before confirmation; confirmation persists
  the policy and starts reconciliation. Stopping a subscription asks whether to keep its files or
  remove files with no other retention reason; upgraded legacy downloads stay protected.
  Storage and controller tests cover policy recreation, preview gating, source isolation, and
  oversized artist rejection. A fixture derived from the v2.8.0 release schema now verifies
  paths, byte counts, album metadata, overlapping policy membership, and idempotent startup.
  Shared Android, JVM, and iOS simulator builds pass.
- The same unreleased migration now stores job requests and item outcomes. Shared Core recovers an
  interrupted manual job with its saved quality, while policy reconciliation recreates interrupted
  subscription transfers. Focused storage and controller tests cover serialization and restart.
  A connected startup now reconciles saved subscriptions; offline restoration only loads local
  downloads and waits for a connection before requesting catalog pages.
  Android, Desktop, and iOS simulator hosts compile through the shared repository contract.
- Artist subscriptions default to favorite albums only, with an explicit all-albums choice. The
  chooser uses the shared highlighted settings row rather than radio buttons. The chosen scope is
  stored with the shared collection policy; older saved policies default to the full catalog.
  The branch-only `27.sqm` migration remains consolidated against `main`'s schema baseline.

## Connected Android upgrade verification

On September 30, 2026, the separate `app.naviamp.android.v2test` install on a Pixel 10a was
upgraded in place from a v2.8.0 database with nine FLAC downloads and a saved playlist policy.
The test install's database had an intermediate, unreleased v28 schema from this feature branch,
so its schema marker was restored to v27 from a backup before installing the consolidated v28
migration. Only the separate test app's database was repaired; its downloaded files were retained.

- After upgrade, all nine original track rows retained their source, quality, path, and byte count.
  SHA-256 hashes of their physical files matched the pre-upgrade manifest after both album and
  artist transfers. Migration backfilled nine manual-retention records. No original file was
  redownloaded or deleted.
- The **Save My Soul** album subscription downloaded eleven tracks. The artist chooser then showed
  zero favorite albums for **Big Bad Voodoo Daddy**; selecting all albums previewed seven albums,
  73 tracks, eleven already saved, and about 1.4 GB of new storage. With the test app's temporary
  5 GB download budget, the artist subscription transferred the remaining 62 tracks. The final
  inventory had 82 download rows and 82 physical files, with 73 artist memberships and eleven
  album memberships. The completed job journal was empty.
- Online cold launch retained the saved subscriptions and 82-file inventory. Offline cold launch
  retained the inventory, and a newly downloaded artist track played with no default network.
  Connectivity was restored afterward.

The local Android unit, Desktop, and iOS simulator test and compilation matrix passed, along with
the architecture check, Android packaging checks, and debug bundle build. The release gate still
needs the pull request's CI matrix.
A v2.8.0 binary cannot be expected to read the new v28 database;
recovery from a rollback requires restoring the pre-upgrade database backup while preserving the
audio files. Do not downgrade the only copy of a user's database to test this.

## Desktop and iOS physical-file upgrade verification

On September 30, 2026, the v2.8.0 release SQL fixture was installed in an isolated Desktop data
profile and a newly created iPhone 17 Pro simulator. Each fixture held three real audio files
(two FLAC files and one MP3), including two qualities for one track, a saved playlist policy,
favorite-track policy, and overlapping memberships. The media source used deliberately invalid
test credentials; these checks did not exercise server reconnection or playback.

- The packaged Desktop app and built iOS app each cold-launched against schema v27 and migrated it
  to v28. Both retained all three download rows and exact physical paths, sizes, and SHA-256 hashes.
  Each backfilled two manual-retention records and retained three collection memberships.
- A second cold launch on each platform preserved the same database and files. The iOS app also
  visibly rendered after the second launch; its connection error was expected from the invalid
  fixture credentials. The existing simulator with unrelated Naviamp data was left untouched.
- The fixture and verification used the release SQL at
  `core/storage/src/jvmTest/resources/v2.8.0-download-upgrade.sql`, generated short valid FLAC/MP3
  files, and compared a pre-launch path/size/SHA-256 manifest to the migrated inventory.
