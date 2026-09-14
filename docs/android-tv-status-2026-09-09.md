# Android TV restart status — 2026-09-09

> Later work on this branch is recorded in [the TV plan](android-tv-plan.md), including localization,
> screen protection, recovery, launcher packaging, and the native lifecycle pass. For repeatable
> emulator recovery checks, see [the local fixture procedure](android-tv-lifecycle-fixture.md).
> The sections below retain the earlier post-merge audit and its validation baseline.
> For the subsequent physical Android 14 TV findings and September 10 wake-lock recovery work,
> use [Android TV follow-up](ANDROID_TV_FOLLOW_UP.md). The historical "Next development work"
> below is not the current backlog; use the TV plan's preview checklist and that follow-up together.

## Branch and merge

The working branch is `feature/android-tv` (not `jfeature/android-tv`). It started at
`ca62a933`, with 12 commits not yet on `origin/feature/android-tv`. The merge incorporates
`main` / `origin/main` at `50c8a935`, the completed v2.4.0 release acceptance baseline.
Before the merge, the branches had 54 TV-side and 31 main-side commits since their merge base.
No remote push or release is part of this restart.

The merge resolves 20 conflicted files and retains the TV shell and Connect implementation
alongside main's library catalogs, playlist membership, Home changes, and player workspace.
Specific integration decisions:

- Keep cached complete artist browsing, with the newer independent Artists/Albums/Songs load state
  and stale-source rejection. The subsequent TV Library implementation is recorded below.
- Keep immediate radio-seed playback while retaining main's successful-radio artist activity
  tracking and cancellation handling.
- Keep Connect output selection and remote progress in the new player workspace.
- Share Home section definitions and Aurora settings with TV. Preserve main's newer standard
  Home settings interaction and localized favorite-artist section title.
- Keep the released `24.sqm` unchanged. The unshipped Connect password-column addition moves to
  `25.sqm`, producing schema version 26. Tests cover upgrades from the released version 25 and
  older schemas, and canonical column order matches migrated databases. No development database
  was modified.

## Development status

The following is based on the checked-in September 4 acceptance record, not a fresh device run.

| Area | Status |
| --- | --- |
| M0: TV host and emulator foundation | Complete |
| M1: dedicated TV navigation, setup, Home/collections, Library, Search, Playlists, details, Settings, Internet Radio | Complete in recorded emulator acceptance |
| TV playback, queue controls, artwork, waveform, line-synced lyrics | Implemented; emulator acceptance recorded |
| Connect pairing, trust, encrypted control, handoff, reconnect, source-mismatch recovery | Implemented and tested for the phone/Desktop-to-TV-emulator preview topology |
| Android/JVM Connect protocol security gate | Internal v1 review completed September 4 |
| Physical Google TV and direct-LAN acceptance | Outstanding |
| General Connect availability and Apple adapters | Outstanding |

The authoritative preview exit checklist remains [android-tv-plan.md](android-tv-plan.md#android-tv-preview-release-gates).
The broader topology and fresh-device setup work remains in
[naviamp-connect-product-plan.md](naviamp-connect-product-plan.md).

## TV Library implementation after the merge

The TV Library now presents the shared Artists, Albums, and Songs catalogs. Each view uses its
existing Core query, loading, pagination, refresh, and A–Z jump actions. Artists and albums render
in the TV grid; songs reuse the TV track row and its playback, Play After Current Group, Add to
Queue, and Start Radio actions. Internet Radio and Playlists remain reachable from the toolbar.

D-pad navigation explicitly connects the selectors, tools, search, A–Z rail, and content. Back
returns to the active selector. Each view retains its viewport and stable focused item identity
across detail navigation, and a source change creates fresh viewport state. Appending a page does
not replay an earlier grid focus request. New copy, including the reused track action panel, has
English and Spanish resources. All production changes are in `core/ui/commonMain`; no platform
production files or persisted settings were changed.

Automated coverage exercises selector navigation, detail return after catalog reordering, song
actions, per-view queries, empty-search keyboard focus, delayed and stale letter jumps, pagination focus, and
720p/1080p/native-4K/double-density-4K layouts. These shared Compose checks do not replace remote,
IME, or physical Google TV acceptance.

## Next development work

1. Finish pairing diagnostics and permission recovery, OLED burn-in behavior, and direct shared
   Compose coverage for the remaining TV screens and navigation paths.
2. Validate sustained gapless/crossfade, ReplayGain, provider reporting, process/network recovery,
   and target-independent playback on representative physical Google TV hardware.
3. Complete 720p/native-4K focus, waveform/repeat-icon, contrast, accessibility, HDMI/downmix,
   CEC, MediaSession/audio focus, and sleep/wake acceptance.
4. Finish TV banner/icon assets and distribution packaging. Word-level karaoke and broader
   Connect/Apple availability remain later work.

Much of the older TV/Connect copy is still hardcoded in shared Kotlin. Before preview release,
that existing localization debt needs a resource/translation pass under the current AGENTS rules.

## Validation and environment

After the TV Library implementation, `:core:ui:jvmTest` passes 306 tests and
`:core:presentation:jvmTest` passes 340 tests (646 total, no failures/errors/skips). Android debug
assembly, Desktop compilation, iOS Simulator ARM64 compilation, and `verifyCoreFirstArchitecture`
all pass again. Eleven new tests cover the Library state and shared Compose behavior. Screenshots
are generated under `core/ui/build/reports/television-library/`; 720p and double-density 4K renders
were also visually inspected. No physical-device acceptance was performed for this change.

The following broader results record the preceding main merge:

The shared domain, app/Connect runtime, UI, storage, Navidrome, and Jellyfin JVM suites pass.
Android debug assembly (`:apps:android:assembleDebug`), Desktop host compilation and its platform
suite (`:apps:desktop:compileKotlinDesktop :platforms:desktop:desktopTest`), and iOS Simulator ARM64
host compilation (`:apps:ios:compileKotlinIosSimulatorArm64`) pass.

The final presentation and storage JVM suites, `:core:storage:verifySqlDelightMigration`, and
`verifyCoreFirstArchitecture` also pass. After correcting the canonical schema ordering, Android,
Desktop, and iOS Simulator builds were repeated successfully.

| Tested module | Tests passed |
| --- | ---: |
| `core/domain` | 888 |
| `core/app` | 191 |
| `core/presentation` | 340 |
| `core/ui` | 295 |
| `core/storage` | 49 |
| `providers/navidrome` | 157 |
| `providers/jellyfin` | 25 |
| `platforms/desktop` | 42 |
| **Total** | **1,987** |

No failures, errors, or skipped tests in these suites.

`adb devices -l` returned no connected devices, so this restart does not claim fresh emulator or
physical-device acceptance.

Before launching a device last used on the old TV branch, inspect its local database. That branch
also used schema version 25, but its migration 24 added the Connect password rather than main's
new tables. Such an unreleased development database may need a targeted repair of branch-owned
schema objects/version state. Do not add a production compatibility migration for that development
history or reset unrelated user data.

## Platform-diff accountability

No Android, Desktop, or iOS production source file is changed relative to the pre-merge TV head.
The platform-side changes inherited from main are build scripts and platform tests, which are
exempt from common-code placement. Merge-specific behavior and UI changes remain in common code.
The existing TV host adapters and their native boundaries are described in the
[September 4 branch review](android-tv-connect-branch-review-2026-09-04.md#shared-first-ownership).
