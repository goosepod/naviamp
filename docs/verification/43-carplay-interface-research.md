# CarPlay interface research for issue 43

Research date: November 14, 2026. These findings come from public source code and
developer-published descriptions, not hands-on testing of the other apps. Recommendations
below are Naviamp design decisions, not claims that the compared apps meet our acceptance gates.

## Other Subsonic/OpenSubsonic clients

| Client | Verified interface pattern | Useful idea for Naviamp |
| --- | --- | --- |
| Amperfy | Its root tab order is Home, Library, Cached, Playlists, bounded by Apple's runtime tab limit. Library includes artists, albums, genres, favorites, recents and radio, with continue-playback and account-switch actions. Now Playing exposes Up Next and album navigation, with repeat/shuffle controls. | Separate quick listening from exhaustive browsing; retain access to the current queue and existing playback. |
| flo | Its root is Library, Playlists, Radio, Downloads. Library offers Albums, Artists, Songs and Liked Songs. Album and playlist detail lists place Play All and Shuffle before individual tracks. Now Playing exposes Up Next, shuffle/repeat and a conditional favorite control. | Give radio and playlists direct entry points, and let people choose between playing a collection and choosing a track. |
| Nautiline | Developer release notes document a favorite-track page, queue access, next-track information, tappable album navigation and artist information in CarPlay. Notes also describe fixes for excessive offline refresh and a CarPlay memory leak. | Make favorites and playback context easy to reach; include offline state changes and repeated attachment in verification. Release notes alone do not establish its complete tab layout. |

Primary sources:

- [Amperfy root templates](https://github.com/BLeeEZ/amperfy/blob/master/Amperfy/CarPlay/CarPlaySceneDelegate.swift),
  [Library](https://github.com/BLeeEZ/amperfy/blob/master/Amperfy/CarPlay/CarPlayLibraryTabExtension.swift),
  [Now Playing](https://github.com/BLeeEZ/amperfy/blob/master/Amperfy/CarPlay/CarPlayNowPlayingExtension.swift).
- [flo browse coordinator](https://github.com/kepelet/flo/blob/ca890aa9aee4b0491be1219f50a5eb97684ef7a8/flo/CarPlay/CarPlayCoordinator.swift),
  [Now Playing](https://github.com/kepelet/flo/blob/ca890aa9aee4b0491be1219f50a5eb97684ef7a8/flo/CarPlay/CarPlayNowPlayingManager.swift).
- [Nautiline developer description and release history](https://apps.apple.com/us/app/nautiline-opensubsonic-client/id6755918140).

Substreamer's developer listing confirms CarPlay browsing/playback, but does not establish a
detailed tab arrangement. play:Sub's public product page describes its general library and caching
features without enough CarPlay-specific detail to use as a layout reference. Neither is evidence
for a particular tab layout here.

- [Substreamer developer listing](https://apps.apple.com/gb/app/substreamer/id1012991665)
- [play:Sub product page](https://michaelsapps.dk/playsubapp/)

## What Naviamp already does on Android Auto

Inspected `NaviampCoreExternalPlaybackBridge.kt` and `AndroidNaviampPlaybackService.kt` at the
issue-43 checkpoint `97977caee`.

- Core supplies nonempty root categories in this order: Playlists, Recently Played, Radio,
  Recent Albums, Queue, Now Playing. Android translates the rows into native MediaBrowser items.
- Collection rows currently start collection playback; the bridge does not expose album/playlist
  track-detail navigation. Recent tracks and stations are direct play selections.
- Queue starts with the active item, followed by upcoming items and then earlier history. Android
  translates transport, queue selection, favorite, radio, shuffle and repeat into Core actions.
- Catalog changes invalidate the root and child categories. Android supports page options.
- Search filters the loaded shared browse catalog; it is not a full provider-library search.
- Root category labels/subtitles currently contain hardcoded English. Reuse the common resource
  system when extending this contract, so both vehicle hosts can receive translated labels.

The catalog and commands are useful shared behavior. Android's platform browser tree does not
establish a tab layout that CarPlay should copy verbatim.

## Proposed Naviamp interface

Use native system list/tab templates backed by common presentation state. Start with three useful
entry points, constrained by runtime capabilities:

| Entry | Content | Scope |
| --- | --- | --- |
| Home | Recently played tracks and recent albums; playback/queue access when available | Initial browse-to-play proof using the existing shared catalog |
| Playlists | Saved playlists | Initial proof starts playback; follow with detail pages exposing Play, Shuffle and tracks |
| Radio | Internet radio and shared library-radio stations | Initial proof where supported by the selected source |
| Library | Artists, Albums, Tracks and Favorites | Follow-on shared loading/detail navigation; do not label recents as the full library |
| Downloads | Available offline collections/tracks, with honest unavailable states | Follow-on shared offline catalog, not a Swift-only cache implementation |

Library and Downloads are target destinations, not a promise of five simultaneous tabs. Prioritize
tabs against the native limit and provide shared list navigation for remaining destinations. Keep
Now Playing reachable through the system affordance and Up Next; it need not consume a browse tab.
Preserve the shared current-first queue policy for the first proof; revisit history presentation
explicitly rather than silently replacing Android behavior with an upcoming-only queue.

Album/playlist detail should offer Play and Shuffle above tracks. Favorites and artist/album
navigation should appear only when Core supports the action. Native code renders templates and
forwards selection; Core owns destination choice, capability filtering, identity, item budgets,
loading, failure, cancellation and readiness. Authenticate/load artwork through existing shared
owners and preserve circular artists versus square albums.

## Apple constraints and acceptance consequences

Apple recommends quick, low-interaction tasks, system templates, locked-phone operation and
in-car error reporting. Do not require a phone dialog during car browsing. Connecting a scene
does not itself authorize starting playback. Complete a selection and present Now Playing when
audio is ready; artwork/metadata loading must not hold up ready audio.

Read list and tab limits at runtime rather than assuming a fixed count. Native code publishes
those facts to the shared owner. Any bounded list needs a useful continuation or category
strategy where the available catalog exceeds the budget; silent truncation is not full-library
browsing. Exercise hardware input, large catalogs, empty states, slow/offline servers, errors,
source changes and stale callbacks, repeated reconnects and phone/car concurrent scenes.

- [Apple CarPlay design guidance](https://developer.apple.com/design/human-interface-guidelines/carplay)
- [Runtime list item limit](https://developer.apple.com/documentation/carplay/cplisttemplate/maximumitemcount)
- [Runtime tab limit](https://developer.apple.com/documentation/carplay/cptabbartemplate/maximumtabcount)

This research changes the intended browse presentation; it does not establish native template,
entitlement or physical head-unit acceptance. Those gates remain in `43-carplay.md`.
