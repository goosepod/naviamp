# Smart-playlist sample sync (#316)

The owner reported that Sync library took a long time without useful progress, then still showed
missing library membership. A read-only inspection of the active Windows cache found 24,943 indexed
tracks: 24,929 had library membership and 14 older rows did not. The completed sync had updated
current records but had no reconciliation step for old records. Even a small number of unknown
memberships correctly prevents a reliable library-scoped preview.

The builder now has a dedicated track-metadata sync. Where the provider can list tracks directly,
it skips artist/album enumeration, genre discovery and scan-state work. Other providers enumerate
albums and their tracks without refreshing artists or genres. No audio is downloaded. Metadata and
database work run on the shared background dispatcher so the editor can continue painting and
accepting input.

The editor immediately shows sync activity, then processed track counts and a determinate bar
when the provider supplies a total. Unknown totals are shown as counts, not an invented percentage.
Fallback album discovery has its own status. Stop sync cancels the editor-owned job and leaves the
draft open; errors remain visible and the action can be retried. Progress returns through the common
command graph and works identically for every host. There is no continuous animation.

Only a complete successful enumeration reconciles the source’s track index. Entries that were not
returned are removed from that derived index, with their derived artist-credit rows. Other accounts,
downloads, audio-cache files, listening history and playlist definitions are not cleared. Interrupted,
failed, truncated and source-changed runs do not prune; successfully refreshed pages may remain in
the cache. Tracks actually returned without membership remain present, and still produce an honest
preview limitation rather than silently bypassing the library filter.

No schema change or migration is needed: the SQL changes add queries to the existing index. All
production changes live in common domain, storage, presentation and UI. No host production file or
new setting is involved. Five new messages are included in all 17 maintained catalogs.

## Manual retest

1. Open the same saved playlist and click Sync library. Expect an immediate status, then track
   counts and a bar if a total is supplied. The editor should remain responsive.
2. Let it complete. The preview reruns automatically. Old cache entries absent from the complete
   response should no longer block membership filtering; all returned tracks still need known library
   membership. Verify samples respect the selected library and playlist conditions.
3. Run it again and choose Stop sync partway through. Keep the dialog open; confirm the draft’s
   name/rules/limit are unchanged and another sync can start. Stop does not save or close the playlist.
4. Disconnect during a sync and retry after restoring the connection. The failure should be readable
   and previous index entries must not be cleared. Changing source while syncing must prevent stale
   completion/pruning. Check ordinary Library refresh still behaves independently.
5. Check a narrow window and keyboard access to Stop sync; long status text must wrap readably.

Verification results and the exact Windows package/hash are recorded in issue #280 and PR linked
from #316. The owner’s running app and live database were not modified during diagnosis. This fix
requires a new test build; the preceding 33d2c32a build does not contain it. No release is authorized.

## Automated verification

112 focused JVM tests passed with zero failures, errors or skips: seven library-sync/domain tests,
64 presentation/action/catalog/playlist tests, three shared SQLite repository tests and 38
smart-playlist UI tests. The storage regression shows that a stale membership-free row can be
pruned after complete enumeration and the selected-library preview then succeeds, while another
source and downloaded audio remain present. Domain failures, cancellation, truncated paging and
album fallback are covered; presentation checks source changes and background dispatch; rendered
UI checks real counts, Stop sync, retained draft values, errors and retry.

The changed common modules compiled for Android, JVM, iOS ARM64 and iOS Simulator ARM64, and
`verifyCoreFirstArchitecture` passed. Windows packaging reuses unchanged, hash-verified CI native
DLLs because the local C++ toolchain is unavailable; only the four native configure/build tasks
are excluded and tests remain enabled. Current-source CI and owner acceptance remain separate.

The passing logs and XML reports accompany the Windows test package. This update does not claim
new physical Android or macOS acceptance, and does not change the recorded scope of earlier owner
approvals. No user database was directly repaired to make the regression appear to pass.
