# Live Stream Details (#586)

Base: PR #585 / issue #583, source 0cc1e4e21. The owner requested a separate issue and a
Stream Details action in the existing Now Playing three-dot menu for Internet Radio.

## Shared behavior

Common UI builds station, current broadcast, audio and additional-metadata sections from the
current station and PlaybackStreamMetadata snapshot. Known station information makes the action
available before a server sends any tags. Missing fields are omitted. Station genre/description
and song genre/description remain separate; unrecognized useful tags remain visible under their
original field names. Binary cover-art comments are represented by a translated presence label,
not rendered as base64. URL credentials and query values are redacted using the existing shared
playback URL sanitizer; sensitive headers and HTTP transport noise are excluded.

The shared action catalog uses a translated Stream Details label for live radio. Its existing
Track Details action still serves library tracks. The existing dialog is reused with a translated
title/Close label and selectable, wrapping values inside its scrollable body. An open dialog
updates on a same-station metadata change, and closes when the station/media identity changes.
The compact audio-info setting controls the player label; it does not disable deliberate details
inspection. Fourteen new labels are present in all 17 maintained catalogs. No setting, export/sync
field or persistence schema changes.

## Native boundary accountability

The common implementation and regressions compiled for Android/JVM/iOS device/simulator before
native adapter changes. Existing Android/Desktop adapters already delegated parsing to
bassStreamProperties, but the native JNI method exposed only HTTP/ICY fields. The iOS BASS
adapter had no streamMetadata implementation and inherited the empty default.

- `native/bass-jni/src/naviamp_bass_jni.cpp`: the Android and Desktop JNI methods call the concrete
  BASS_ChannelGetTags C ABI for Ogg, APE and MP4 NUL-separated tag arrays, in addition to existing
  HTTP/ICY metadata. They only copy native strings into the existing shared contract.
- `platforms/ios/src/iosMain/kotlin/app/naviamp/ios/playback/IosBassPlayback.kt`: BASS_ChannelGetTags
  and C-pointer/NUL-string traversal cannot compile in common Kotlin. This adapter copies the
  native tag arrays/single ICY block, then delegates all parsing to bassStreamProperties.

No selection, UI, retry, scheduling or interpretation policy lives in either native adapter.
The new Desktop and iOS tests contain only native BASS initialization, temporary-file IO and
cleanup. Assertions belong to one common testkit contract. Its 0.1-second Vorbis sine fixture is
synthetic FFmpeg output and contains no recorded music. Both use the no-output device.

## Verification

Initial shared verification passes all 28 selected cases (model/actions/presentation and rendered
radio/track menu-dialog regressions), Android/JVM/iOS compilation and the Core-first architecture
gate. Source and all 16 translation catalogs pass the translation validator. Final native,
combined test-build and simulator results are recorded below after completion.

The implementation consumes available HTTP/ICY fields and codec-native text tag arrays supported
by BASS. It does not introduce a station status API, directory lookup or polling service; listener
counts/history/schedules that require external endpoints remain outside this issue. It does not
claim every station supplies rich metadata or that physical iOS playback was tested.

## Final results

- 28 selected shared UI/action/presentation/rendered tests pass with zero failures/errors/skips.
- The real Desktop BASS/JNI contract and the same contract through the iOS simulator BASS adapter
  each pass (one case each, zero skips), exposing title, artist, album and a custom presenter tag.
- Android JNI compiles for arm64-v8a, armeabi-v7a, x86 and x86_64. Common UI and iOS production
  adapter compilation pass for device and simulator; the architecture gate and diff checks pass.
- Fourteen new labels validate across all 17 catalogs. All eight native manifest/runner tests pass;
  the required native inventory now includes this case (18 total required cases).
- A rendered production-theme details body was inspected at
  `/private/tmp/naviamp-586-stream-details.png`: values wrap without horizontal clipping, the body
  scrolls to audio/custom fields, and artist/station changes clear the previous dialog context.
- One simulator launch attempt found the dedicated device shut down before process spawn; booting
  that device immediately before the focused invocation resolves the runner-state failure. This
  is not counted as a failed native metadata assertion or physical-device acceptance.

Final native/target log: `/private/tmp/naviamp-586-native-final.log`; shared and Desktop results:
`/private/tmp/naviamp-586-verified.log`; runner-manifest tests:
`/private/tmp/naviamp-586-native-manifest-tests.log`.
