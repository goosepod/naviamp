# Changelog

Release changes are grouped into user-facing Features, Bug Fixes, and deployment or infrastructure-related System Settings. Internal task-tracking notes are intentionally not included.

## v2.0.0-beta.2

This second beta focuses on playback efficiency, accurate shared Now Playing behavior, platform packaging, and issues found during Android, Desktop, iOS, Jellyfin, Bandcamp, and Subsonic acceptance testing.

### Features

- Added an in-player lyrics timing selector for plain, line-synchronized, and word-synchronized lyrics, kept in sync with the shared display preference.
- Improved downloaded-track quality labels so saved originals and transcodes show their actual format and bitrate when available.

### Bug Fixes

- Fixed queue completion so repeat-track, automatic next-track navigation, and Sonic Autoplay continuation follow the shared finished-track policy without duplicate navigation.
- Fixed playback-source reporting when a provider stream falls back to a downloaded file, including the quality shown by native media controls and Stats for Nerds.
- Reduced active-playback CPU and battery use by limiting speculative audio prefetch work and isolating the high-frequency playback clock from structural application state.
- Reduced Now Playing frame work by localizing progress observation, limiting waveform overdraw, and replacing the hidden recomposing slider with exact tap, drag, and accessibility seek handling.
- Removed Android's unused notification permission prompt while retaining the foreground media-playback service behavior required by the operating system.

### System Settings

- Added the complete semantic version to Android, ZIP, MSI, EXE, DMG, DEB, and RPM release filenames so prerelease upgrades can be identified and installed correctly.
- Gave Windows installers monotonically increasing package versions based on the shared build code and preserved prerelease ordering for Linux packages.
- Added explicit Linux secure-credential runtime dependencies for DEB and RPM packages.
- Staged every platform's release outputs in a consistent artifact directory before GitHub release publication.

### Beta Notes and Known Limitations

- Feature development remains frozen for the 2.0 beta line. Only release blockers, regressions, acceptance coverage, diagnostics, packaging, documentation, and narrowly required compatibility fixes are accepted.
- Back up important playlists and settings before testing this beta.
- Android, macOS, Windows, and Linux are the supported beta targets. The iOS artifact remains an unsigned preview that testers must sign themselves; release signing, TestFlight, and physical-iPhone acceptance remain open before iOS can be treated as a supported RC target.
- Bandcamp's Subsonic beta currently serves collection audio as MP3 256 kbps and does not reliably apply playlist track reordering; Naviamp blocks unsupported reorder mutations.
- Windows and macOS packages are not yet distributed with trusted publisher signing, so their operating systems may display security warnings.

## v2.0.0-beta.1

This first beta begins the Naviamp 2.0 feature freeze. It combines the shared cross-platform product with the final discovery, playback-continuity, Home customization, media-detail, and Desktop keyboard work completed after Alpha 4.

### Features

- Added Sonic Autoplay replenishment for ordinary queues, so playback can continue after manually queued tracks when the active provider supports sonic similarity.
- Added album information to the shared album-detail page, with independent artist/album information visibility settings and lightweight provider-text formatting.
- Added Navibeat Mixes as a dedicated Home section while keeping generated plugin playlists out of the normal Playlists view.
- Added configurable Home section order and independent List, Grid, or Carousel layouts, plus dedicated collection pages and Desktop carousel controls.
- Added customizable Desktop global shortcuts for playback, volume, and bringing Naviamp forward, with focused-window Space always controlling Play/Pause outside text entry.
- Replaced the removed direct Musixmatch client with the documented, keyless LRCMUse API for plain, line-synchronized, and word-synchronized online lyrics.

### Bug Fixes

- Fixed Sonic Autoplay stopping at the end of manually assembled queues even when sonic similarity was available.
- Fixed artist popular-track radio so the first popular track starts the queue and the remaining popular tracks are interspersed without duplication.
- Added shared busy feedback for longer-running radio and collection actions.
- Fixed album and artist artwork expansion letterboxing, narrow artist-album grids, album-detail metadata duplication, artist navigation, and formatted information rendering.
- Fixed Stats for Nerds so the last playback request receives the same credential redaction as recent API calls.
- Fixed Home section ordering persistence and preserved the correct scroll position when opening and returning from dedicated section pages.
- Fixed Settings contrast and focused-window Space handling after navigating or interacting with other controls.

### System Settings

- Kept queue continuation, Home composition, media details, online-lyrics selection, keyboard command routing, settings persistence, and user-facing status policy in shared Core code.
- Extended the versioning script to accept an explicit prerelease SemVer while continuing to advance the cross-platform build number.
- Updated tag release-note extraction so Alpha, Beta, and Release Candidate limitations are included consistently.

### Beta Notes and Known Limitations

- Feature development is frozen for the 2.0 beta line. Only release blockers, regressions, acceptance coverage, diagnostics, packaging, documentation, and narrowly required compatibility fixes are accepted.
- Back up important playlists and settings before testing this beta.
- Android, macOS, Windows, and Linux are the supported beta targets. The iOS artifact remains an unsigned preview that testers must sign themselves; release signing, TestFlight, and physical-iPhone acceptance remain open before iOS can be treated as a supported RC target.
- Windows, Linux, multi-provider edge cases, Android download/offline interactions, accessibility, migration, and the final performance matrix still require stabilization acceptance before RC1.
- Windows and macOS packages are not yet distributed with trusted publisher signing, so their operating systems may display security warnings.
- LRCMUse aggregates results from upstream lyric sources. Availability, attribution, and lyric rights remain source-dependent; Naviamp does not claim ownership of returned lyrics.

## v2.0.0-alpha.4

This alpha expands Naviamp beyond Navidrome with shared Subsonic, Jellyfin, and Bandcamp connections across desktop, Android, and iOS.

### Features

- Added an explicit provider selector for Navidrome, generic Subsonic/OpenSubsonic, Jellyfin, and Bandcamp, including editable saved-provider types and provider-specific connection guidance.
- Added native Jellyfin authentication, music-library selection, browsing, search, direct and transcoded playback, instant mixes, favorites, recently played reporting, lyrics, playlists, downloads, and offline playback.
- Added generic Subsonic/OpenSubsonic capability negotiation while preserving Navidrome-only behavior behind its provider profile.
- Added Bandcamp collection access through its Subsonic beta, including automatic server-address entry, collection-folder selection, search, playback, downloads, and supported playlist operations.
- Added shared multi-provider session routing and source-scoped restoration so saved connections, queues, artwork, downloads, and offline playback resolve against the correct provider.

### Bug Fixes

- Fixed Jellyfin transcoded playback, waveform generation, scrubbing, authenticated downloads, media-quality presentation, and downloaded-file fallback.
- Fixed queue-to-playlist saves so every queued track is included and playlist updates always leave the saving state.
- Prevented unsupported Bandcamp playlist reordering from sending unreliable mutations, and reduced the explanation to one visible message.
- Reset connection-page scrolling when opening a new provider and made provider connection failures remain prominent and actionable.
- Fixed Android system Back handling so playlist, Now Playing, and nested Settings navigation stays inside Naviamp instead of returning to another application.
- Fixed offline cold restoration when the saved provider is not the first configured source, including downloaded artwork and playback without a reachable server.
- Preserved playlist-dialog input state across responsive layout changes.

### System Settings

- Added a dedicated shared Jellyfin provider module and kept provider protocol behavior in common code for all three hosts.
- Changed the Subsonic request client name to `Naviamp` while retaining versioned user-agent diagnostics.
- Added shared provider, routing, authentication, playlist, download, artwork, playback, restoration, and system-Back regression coverage.

### Alpha Notes and Known Limitations

- Back up important playlists and settings before testing this alpha release.
- Bandcamp's current beta serves collection audio as MP3 256 kbps and does not reliably apply playlist track reordering; Naviamp blocks reorder mutations for that provider.
- Provider features are shown only where the current capability profile supports them. Jellyfin does not expose Navidrome smart playlists or sonic-analysis features, and its favorite state is separate from Naviamp's star/rating action.
- Generic Subsonic compatibility still needs broader testing against additional non-Navidrome server implementations and legacy authentication configurations.
- Windows, Linux, physical-iPhone, large-library, and long-running multi-provider acceptance remain limited; the detailed remaining matrix is recorded in `docs/provider-expansion-discovery.md`.
- Windows and macOS builds are not yet distributed with trusted publisher signing, so their operating systems may display security warnings.
- The iOS IPA is unsigned and requires testers to sign it with their own Apple identity before sideloading.

## v2.0.0-alpha.3

This alpha adds word-by-word lyrics and gives users independent control over the lyric timing Naviamp downloads and displays.

### Features

- Added word-by-word karaoke lyrics with progressive active-word highlighting, synchronized line fallback, manual offsets, seeking, and cached offline reuse.
- Added separate download and display timing preferences for plain, line-synced, and word-synced lyrics.
- Added a Text, Lines, and Words selector directly on the lyrics screen, including selected, available, and unavailable states for the current track.
- Added timing-aware online lyric lookup and caching that preserves richer results while allowing a less detailed display mode.

### Bug Fixes

- Made connection failures automatically scroll into view and display in a prominent error card instead of appearing as easy-to-miss status text.
- Prevented display-timing choices from discarding richer cached lyric timing when switching between plain, line-synced, and word-synced views.
- Kept the Settings layout readable when long selected values are shown on narrow screens.

### System Settings

- Extended the shared lyric sidecar cache and settings schema with backward-compatible timing metadata and display preferences.
- Added shared tests for lyric timing selection, cache projection, persistence, seeking behavior, and connection-error state propagation.

### Alpha Notes and Known Limitations

- Back up important playlists and settings before testing this alpha release.
- Word-by-word availability depends on the selected provider and the timing data available for each track.
- Online lyric services may rely on unofficial endpoints that can change or become unavailable without notice; server, embedded, cached, and alternate online fallbacks remain available.
- Windows and macOS builds are not yet distributed with trusted publisher signing, so their operating systems may display security warnings.
- The iOS IPA is unsigned and requires testers to sign it with their own Apple identity before sideloading.
- Windows, Linux, and physical-iPhone release testing remains limited.

## v2.0.0-alpha.2

This alpha focuses on tightening Naviamp's native audio packaging and correcting mobile settings capability presentation.

### Features

- Added BASS, decoder, OpenSSL, and Naviamp licensing notices to the shared About screen and source distribution.

### Bug Fixes

- Hidden the software volume-bar setting on Android and iOS, where Naviamp intentionally uses system volume controls instead of an in-app volume bar.
- Removed unused BASS FX and BASSloud components while retaining Core BASS equalization and ReplayGain behavior.
- Removed the BASS_TTA decoder because its LGPL terms are incompatible with Naviamp's current static iOS packaging.
- Aligned Android, Desktop, and iOS BASS decoder inventories and diagnostics so packaged components match the plugins each host actually registers.

### System Settings

- Added a GPLv3 section 7 linking exception for BASS and expanded the repository's third-party licensing disclosures.
- Updated the vendored BASSWEBM, BASS_SSL, and BASSOPUS libraries from verified upstream packages.
- Added build-time package checks that reject missing or unexpected native BASS components across supported platforms.

### Alpha Notes and Known Limitations

- Back up important playlists and settings before testing this alpha release.
- Windows and macOS builds are not yet distributed with trusted publisher signing, so their operating systems may display security warnings.
- The iOS IPA is unsigned and requires testers to sign it with their own Apple identity before sideloading.
- Android Auto remains available for testing; report any first-track advancement or reconnection problems with the surrounding playback details.
- Windows, Linux, and physical-iPhone release testing remains limited.

## v2.0.0-alpha.1

Naviamp 2.0 is a major cross-platform rebuild. Android, macOS, Windows, Linux, and iOS now share the same application core, interface, playback behavior, provider integration, storage model, and settings wherever the operating systems allow it.

This is the first public alpha. It is intended for testing and may still contain platform-specific bugs or incomplete packaging.

### Features

- Added the first Naviamp application for iPhone and iPad, including streaming, downloaded and offline playback, background audio, Control Center integration, secure credential storage, settings synchronization, lyrics, waveforms, and visualizers.
- Unified Android, Desktop, and iOS around one shared application core and interface, keeping browsing, search, playlists, downloads, settings, provider behavior, and playback policy consistent across platforms.
- Added native BASS playback across Android, Desktop, and iOS with shared gapless, crossfade, ReplayGain, sample-rate conversion, and sample-rate matching behavior.
- Added the final Naviamp 2.0 icon across the applications, installers, notifications, About screen, repository, and website.
- Added downloadable unsigned iOS builds for advanced users who can sign and sideload applications with their own Apple identity.
- Added automatic detection and safe migration support for Navidrome's upcoming canonical identifier transition using the announced `topSongsByArtistId` OpenSubsonic extension.
- Added shared Metal visualizers on iOS and macOS with safe fallbacks when native rendering is unavailable.
- Added playback sample-rate and effective downloaded-quality information to diagnostics.
- Added support for embedded Opus metadata.
- Retained Android Auto browsing, search, queue, and playback integration through the new shared application architecture.

### Bug Fixes

- Significantly reduced Android background playback CPU, network, and battery usage by bounding rolling playback prefetch and sidecar work.
- Improved queue restoration and playback-session persistence across application restarts and host lifecycle changes.
- Applied transition, fade, and sample-rate setting changes to active queues more reliably.
- Prevented stale prepared tracks from surviving queue, transition, or playback-source changes.
- Isolated playback and persisted state when switching between configured servers.
- Fixed downloaded playback, storage-location selection, converted-download tracking, and server-versus-downloaded fallback behavior.
- Improved playlist, favorite, and Smart Playlist keep-downloaded reconciliation, watched download jobs, and playlist download selection.
- Preserved Android MediaSession and notification state across track transitions.
- Hardened cache and download cleanup so Naviamp deletes only files it owns and preserves unrelated files when clearing storage or changing locations.
- Fixed Navidrome snapshot identifier collisions while preserving existing downloaded audio and durable media ownership.
- Improved rotated native-token recovery and targeted Smart Playlist reauthentication.
- Improved visualizer resource disposal, shared waveform analysis, cover-art fallback behavior, artist biography scrolling, and similar-artist resolution.

### System Settings

- Release automation can produce an Android APK and Google Play AAB, a macOS application and DMG, Windows standalone packages and installers, Linux standalone packages plus DEB and RPM installers, and an unsigned iOS IPA.
- Android credentials remain protected by Android Keystore, iOS credentials use Apple Keychain, and Desktop credentials use macOS Keychain, Windows DPAPI, or Linux Secret Service.
- Navidrome identifier migration is versioned and atomic across saved queues, downloads, playback history, artwork ownership, and other stored media references. Existing downloaded files remain in place.
- Native cache and download deletion now requires a matching database ownership record, an approved Naviamp storage location, and a verified regular file before ownership is removed.

### Alpha Notes and Known Limitations

- Back up important playlists and settings before testing this alpha release.
- Windows and macOS builds are not yet distributed with trusted publisher signing, so their operating systems may display security warnings.
- The iOS IPA is unsigned and cannot be installed directly. Testers must sign it using their own Apple identity and sideloading workflow.
- TestFlight distribution is not available yet.
- iOS has primarily been tested in Apple's Simulator; physical-device behavior still needs broader testing.
- Android Auto is included for testing, but its final physical-vehicle acceptance pass is still pending.
- CarPlay is not included in this release.
- Windows and Linux still need final real-device packaging and playback smoke tests.
- Linux secure credential storage requires `secret-tool` and an available Secret Service implementation such as GNOME Keyring or KDE Wallet.
- Additional performance and long-running playback testing is still underway across all platforms.

## v1.5.0

### Features

- Added customizable app backgrounds with the default Aurora gradient in light or dark emphasis, an adjustable Album Blur treatment, and a user-selected Single Color.
- Added structured multi-artist credits with separate artist-page navigation from Now Playing and shared track rows on desktop and Android, including exact-name fallback for legacy combined Navidrome credits.
- Expanded Library Radio to queue Navidrome's full bounded 500-song random set while preserving Radio DJ tuning across the returned tracks.
- Changed artist-page header playback to Play and Shuffle the full catalog in its displayed album order while keeping Popular Tracks playback in its own section.

### Bug Fixes

- Refined compact Now Playing spacing, moved volume below the transport controls, and hid volume at the minimum desktop height so the remaining controls stay comfortably separated.
- Made single- and multi-artist lines use consistent spacing and the configured artist-name marquee behavior.
- Prevented Aurora backgrounds and cover art from flashing through empty fallback states between tracks by retaining the current visuals until preloaded replacements are ready.
- Prevented first-track waveforms from being lost when startup navigation cancels the original shared analysis request.

## v1.4.0

### Features

- Added Now Playing queue polish with track durations and a denser desktop side-panel layout.
- Added desktop hover tooltips for icon-only controls with a Settings > Experience toggle.
- Moved playback reporting to the OpenSubsonic `reportPlayback` flow and let the server own scrobble timing.
- Added playback state heartbeats and final stopped reports so Navidrome can update Now Playing and scrobble completed plays.
- Added Android parity for playback-report transitions across regular playback and service-owned playback.

### Bug Fixes

- Fixed desktop and Android playback transitions so a final stopped report is sent before moving to another track.
- Removed the legacy `scrobble.view` reporting path and obsolete client-side played/scrobble timing setting.

## v1.3.0

### Features

- Added release-aware artist discographies with album, EP, single, live, compilation, remix, and soundtrack sections plus explicit-content indicators.
- Added configurable album list or artwork-grid layouts, release-type grouping, and sorting by year or title across desktop and Android.
- Moved connected catalog browsing to shared paged Navidrome APIs while preserving fast artist search and A-Z navigation with a lightweight local index.
- Added visible download jobs with progress, cancellation, retry, completed-file quality details, and configurable download swipe actions.
- Added configurable download and audio-cache locations, including Android storage selection for devices with SD cards.
- Added keep-downloaded playlists, Smart Playlists, and favorites with automatic missing-track reconciliation.
- Added a playback preference to choose downloaded files first or prefer the server with downloaded-file fallback.

### Bug Fixes

- Fixed download transcoding so selected codec and bitrate settings are honored and accurately displayed.
- Fixed completed download activity, downloaded-file size totals, album artwork, refresh behavior, and externally removed-file detection.
- Fixed waveform generation that could remain unfinished for an entire track.
- Fixed Android storage-location crashes and missing keep-downloaded tables in databases created by development builds.
- Fixed the Sonic track overflow menu crash and restored transparent bottom navigation backgrounds.
- Standardized page headings, action alignment, search-field spacing, and compact search styling across desktop and Android.

### System Settings

- Replaced the mirrored album and track catalog with bounded cross-platform paging and retained only local data with durable offline value.
- Added a database migration that clears legacy catalog mirrors and oversized artwork blobs, then reclaims freed SQLite pages.
- Bounded persistent artwork caching to appropriately sized 512 px browsing images and 1024 px Now Playing images.
- Added cross-platform coverage for paging, release sections, download jobs, keep-downloaded policies, playback source selection, migrations, and settings persistence.

## v1.2.0

### Features

- Added dedicated standard playlist editing with drag reordering, track removal, undo, save and cancel controls, and configurable editing-only swipe actions.
- Added Smart Playlist editing with single- and multi-library targeting, preserved rule grouping, and refreshed results after updates.
- Made album, artist, and playlist action rows adapt to the available width while keeping additional actions in an overflow menu.

### Bug Fixes

- Fixed expired Navidrome authentication while creating or updating Smart Playlists by refreshing rotated native tokens and retrying after reauthentication.
- Prevented automatic track changes and restored playback sessions from opening Now Playing or interrupting in-progress Smart Playlist edits.
- Fixed playlist detail layouts, compact-screen scrolling, drag auto-scrolling, dragged-item layering, action contrast, and Smart Playlist-specific controls.
- Fixed incomplete waveform analysis and invalid cached waveforms that produced sparse or misleading progress displays.
- Fixed Android notification artwork updates occurring from a background thread.
- Fixed Library navigation behaving like artist-detail Back after browsing through Similar Artists.

### System Settings

- Upgraded Compose Material 3 to 1.9.0 through the shared version catalog and validated Android API 36 compatibility.
- Added automated coverage for playlist editing, Smart Playlist library scoping and authentication, waveform validation, responsive action rows, and settings persistence.

## v1.1.1

### Features

- Added Android Auto voice search for library tracks, albums, and artists.

### Bug Fixes

- Fixed Android Auto crossfades, gapless transitions, and tracks restarting after prepared playback transitions.
- Made Android Auto manual skips start promptly and prevented stale browse or search results from replacing newer selections.
- Fixed queue removals and reordering so an obsolete prepared track cannot play on Android or desktop.
- Protected saved Android credentials with Keystore-backed encryption and excluded credential-bearing data from backup and device transfer.
- Restricted exported Android playback commands to trusted Naviamp controls while preserving Android Auto and notification controls.

### System Settings

- Added automated coverage for prepared-next invalidation, Android Auto selection cancellation, playback-command authorization, credential protection, and backup rules.
- Replaced the unbounded Android playback wake lock with a renewable bounded lease and cleaned up obsolete Android SDK checks.

## v1.1.0

### Features

- Added configurable left and right swipe actions for Library, Queue, Related, and Sonic track lists across desktop and Android.
- Added a visible Play Next priority queue that preserves insertion order ahead of the regular queue and remains stable while shuffling.
- Added pull-to-refresh and an overflow Refresh action to Home.
- Improved album and artist detail actions for narrow and wide layouts.

### Bug Fixes

- Fixed duplicate queue occurrences causing Play Next loops and duplicate-row crashes on Android.
- Fixed restored Android queues and waveforms so they work before playback is resumed or skipped.
- Fixed incomplete or stale waveform data and improved waveform contrast across player backgrounds.
- Fixed seeking backward while a desktop crossfade is active.
- Improved Now Playing gradients so light and secondary album-art colors remain visible.
- Fixed Popular Tracks swipe behavior and track metadata navigation.

### System Settings

- Added automated coverage for swipe gestures, queue occurrences, player colors, waveform validation, and Android playback-session restoration.
- Updated release automation to publish only Features and Bug Fixes while retaining deployment and infrastructure changes in this changelog.

## v1.0.0

- First full public release of Naviamp.
- Presents Naviamp as a polished music player for Navidrome and OpenSubsonic-compatible servers across desktop and Android.
- Highlights Sonic Analysis, Smart Playlists, internet radio, lyrics, waveforms, visualizers, and customizable Now Playing behavior.
- Cleans up the public repository for open-source use with a focused README, contributing guide, code of conduct, security policy, issue templates, and GPLv3 licensing.
- Moves internal planning notes out of the tracked repository while keeping user-facing project information in top-level files.

## v0.19.0

- Added Now Playing display customization for album year, bitrate info, volume bar, and long title, artist, and album scrolling.
- Added opt-in Start Playing on Start behavior to resume playback automatically from the previous session.
- Added configurable audio output quality with Sample Rate Converter and Sample Rate Matching settings.
- Added strict sample-rate and crossfade safety prompts so incompatible playback settings are handled explicitly.
- Improved waveform rendering dynamics and added a scroll affordance to the Add to Playlist dialog.

## v0.18.0

- Added first-pass update checking on startup and every 24 hours, with a default-on Experience setting and a prompt linking to the newest GitHub release.
- Reworked Back To, Up Next, and Sonic queue menus to replace Track Details with Go to Artist and Go to Album, and made the Now Playing album name open album details.
- Added click/tap-to-enlarge artist images and album artwork on detail pages while preserving each source image's original aspect ratio.
- Refined compact Now Playing layouts with stable small typography, tighter spacing, and vertically balanced favorites, rating, bitrate, and volume controls.
- Fixed Settings scrolling in small windows and moved portable Windows SQLite data to writable local app storage with migration from the previous roaming path.

## v0.17.0

- Added audio output device selection for desktop playback.
- Improved artist and popular-track behavior across shared app surfaces.
- Continued Android and desktop parity work for shared playback, provider, and settings behavior.
