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

The first shared foundation is in progress on `feature/150-artist-album-downloads`:

- Migration `27.sqm` backfills a conservative manual-retention record for every existing downloaded
  track without changing its audio row or file path. Multiple qualities share one retention reason.
- New one-time downloads add manual retention after the track completes. Automatic subscriptions
  do not acquire manual retention, including on retry.
- Reconciliation preserves a managed track when manual retention or another policy still requires it.
  Explicit removal releases retention only after the stored download is gone.
- JVM tests cover migration, source isolation, overlap, and retry classification. Artist and album
  favorite catalog enumeration tests now cover complete pages, explicit bounds, unsupported
  providers, and Navidrome/Jellyfin mapping. Subscription selection, persisted jobs,
  UI, cross-platform compilation, and an actual previous-release database fixture remain
  outstanding.
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
  Android, Desktop, and iOS simulator hosts compile through the shared repository contract.
  Physical-file cold-launch checks and complete release verification remain outstanding.
