# Changelog

Release changes are grouped into user-facing Features, Bug Fixes, and deployment or infrastructure-related System Settings. Internal task-tracking notes are intentionally not included.

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
