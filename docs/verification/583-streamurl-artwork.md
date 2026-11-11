# Internet Radio StreamUrl artwork (#583)

The owner tested Radio Paradise Main Mix in the combined Mac build of #579, #581 and #582.
The original native BASS row wraps a complete ICY block in `StreamTitle=`; the common parser
previously extracted only the title, discarding its per-song `StreamUrl`. The artwork mapper
also did not recognize that key.

The common parser now retains quoted ICY fields from both raw and native-wrapped blocks,
including empty values, so a changed/cleared URL follows the current snapshot. The shared UI
uses an HTTP(S) StreamUrl with an image filename after explicit artwork properties and before
library-search/station fallbacks. Query strings, fragments and case variants are accepted;
station webpages, unsafe schemes and malformed URLs are rejected. Extensionless image
endpoints remain unrecognized because StreamUrl can also be an ordinary station webpage;
there is no speculative image download or station-specific polling API.

Existing provider song-search artwork remains available. The owner subsequently observed
Radio Paradise covers in the original build: that observation alone does not establish ICY
StreamUrl handling, because the fallback can find a cover for a later announced song.

The macOS BASS library independently returned this live decode-only metadata on November 11:

```
StreamTitle='Massive Attack - Unfinished Sympathy';StreamUrl='http://img.radioparadise.com/covers/l/6974.jpg';
```

The probe uses BASS's no-output device and a decode-only stream, releases all native resources,
and does not touch the installed application or its data. Raw evidence is retained locally in
`/private/tmp/naviamp-583-native-metadata.json`. This is native metadata evidence, not audible
playback or a painted installed-app artwork acceptance claim.

Shared regressions cover raw/JNI/icy-title wrappers, reordered fields, quotes and delimiters,
empty/missing covers, latest-song changes, explicit artwork precedence, safe URL recognition
and effective Now Playing artwork. All production changes are in common domain/UI code;
Android, Desktop and iOS adapters remain unchanged. No strings, settings or schemas change.

## Homepage edits (owner-requested scope addition)

The owner found that editing the Radio Paradise station to add a homepage never persisted and
explicitly requested including it here rather than creating another issue. The provider sent
`homePageUrl`, which is the response field spelling, instead of the request parameter
`homepageUrl`. OpenSubsonic's create/update documentation and Navidrome's
[`server/subsonic/radio.go`](https://github.com/navidrome/navidrome/blob/master/server/subsonic/radio.go)
confirm the differing spelling. The shared provider now uses `homepageUrl` on writes and retains
`homePageUrl` on reads. Updates send an explicit empty value to clear a saved homepage.
A server-contract regression edits, replaces and clears the homepage and reloads station data.

## Radio stream quality (owner-requested scope addition)

The owner explicitly requested stream quality during Internet Radio playback, controlled by the
existing track bitrate/audio-info setting, with no new issue. Common code maps the active stream's
Content-Type, ICY bitrate and Icecast audio-info headers to the existing codec/bitrate presentation.
It never substitutes configured library-transcoding preferences or infers quality from a stream URL.
Missing/invalid values remain absent. The existing showAudioInfo setting controls normal, compact,
filled-artwork and TV presentations; live radio still has no track-rating controls. No setting,
export/sync field, or translated UI label is added. Shared metadata regressions and rendered player
checks cover the enabled/disabled setting and the different layouts.

## Consolidated SomaFM logos

At the owner's request, the work originally tracked in #584 is included in #583 rather than a
separate PR. See [the catalog and logo verification](583-somafm-logos.md).

## Owner feedback on the preceding combined Mac build

Source `5348bde1d3a3661ef361133d04e375f0d09cda5e` combines #579, #581 and #582.
The owner reports shuffle works as intended and Recently Played Radio no longer shows duplicates
in their testing. SomaFM logos partly returned, prompting the channel-catalog follow-up here.
Radio Paradise's library-matched song cover appeared and the next non-library song had no cover.
These are owner-reported observations of the preceding build, not manual acceptance of this fix.

## Final local verification

The consolidated source passes 357 selected JVM cases (7 common-domain metadata/quality,
17 UI/artwork/rendered-layout, 333 provider), with zero failures, errors or skips. Common UI
and provider compilation passes for Android, JVM, iOS arm64 and iOS simulator arm64. The
Core-first architecture gate and `git diff --check` pass. Final log:
`/private/tmp/naviamp-583-final-verification.log`. Rendered checks cover audio-info on/off in
filled, compact, wide and TV players; no native application acceptance is inferred from them.
