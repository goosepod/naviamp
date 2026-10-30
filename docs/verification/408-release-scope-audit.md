# Selected release scope audit — October 30, 2026

Tracking: #408; omitted common bug fix #357 / PR #358.

The tested overnight cohort and final phone/performance records are accepted through #409 and
#410. Main `13dc942ac` and release `2a6ed29a3` have identical source trees. Both full matrices
for their candidate `b42d01957` pass, including the strengthened native preview clock test.
Existing release tags and publication/store holds remain unchanged.

A final audit of open v2.10.0 milestone PRs found one missing implementation: #358's refresh of
Audio Cache and Downloads totals. Its common fix `abefd0318` previously passed a complete hosted
matrix. Its two new regression files and refresh owner were absent from the combined source.
This follow-up merges that original branch and retains both required media-item/cache imports.
The English About/CHANGELOG/draft-release bug-fix entry is added. No translation, persisted
setting, schema, native adapter, version or signing policy changes.

## Older milestone work already represented

These older heads are not literal ancestors of `b42d01957`. Their changed test/owner paths exist
in current Core, and every added regression method declaration remains present at its original
test path. The sole absent older file is the deliberately superseded v2.10.1 draft release body.
The complete current-source hosted matrix executes the retained coverage. This check avoids
assuming a non-ancestor branch means missing behavior or reintroducing an older whole branch.

| PR | Behavior represented by retained Core coverage |
| --- | --- |
| #347 | Explicit pause across late callbacks and restart-on-seek. |
| #345 | Smart-playlist rule/group/sort movement and focus retention. |
| #336 | TV slider navigation and complete focusable changelog. |
| #335 | Completed-request callback latching during pending Autoplay. |
| #334 | Validated native-login capabilities and stale-source rejection. |
| #319 | Library labels, compact smart-playlist examples and window scrolling. |
| #317 | Sample-sync progress and removal of obsolete preview metadata. |
| #315 | Rule explanations and library-scoped previews. |
| #313 | Independent artist playlist/appearance List/Grid settings. |
| #311 | Artist preferences grouped under Experience with aligned controls. |
| #309 | Top Tracks visibility and maximum. |
| #307 | Authoritative provider Library search matches. |
| #305 | Stable search controls, explicit refresh and browse restoration. |
| #303 | Owned playlists containing an artist. |
| #300 | Readable rule/sort/reference summaries. |

This is not a sum of unique executions: stacked branches repeat earlier methods. It does not
independently close those PRs or claim unperformed physical acceptance.

## Omitted storage fix validation

Production changes remain in existing common `NaviampCore` and `NaviampCoreApp` owners.
Settings reads the same diagnostics storage facts as Stats for Nerds immediately and every two
seconds, retains valid totals through unavailable/failed reads, and stops on leaving Settings.
Preferences, storage-location selections and sync/export models are preserved. Download byte
projection is updated consistently with diagnostic totals.

Current-source local verification passes 622 UI and 534 presentation cases with no failures,
errors or skips, including populated/changing/empty totals, failure/recovery, cancellation and
actual Settings rendering. Architecture verification and Android/JVM plus both iOS ARM64
compilations pass. About/Changelog renders the storage correction, scrolls all 31 entries and
passes TV keyboard focus coverage. The storage-entry capture is visually inspected.

Android CPU measurements remain a source-pinned `b42d01957` baseline preceding this Settings-only
refresh, not final performance acceptance. Android #414 and current Mac/Windows gates remain
open. A fresh ordinary Mac release/observer bundle must be rebuilt and rehashed for this
follow-up before visible measurements; the owner made only the phone available. No new phone
installation or profile change is needed for this common-only fix's local validation.

This follow-up must pass protected main/release checks before merging; earlier green matrices
do not replace final-source verification. No release, tag, mirror or store submission is performed.
