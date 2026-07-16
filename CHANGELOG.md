# Changelog

Release changes are grouped into user-facing Features, Bug Fixes, and deployment or infrastructure-related System Settings. Internal task-tracking notes are intentionally not included.

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
