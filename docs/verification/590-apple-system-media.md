# Shared Apple system-media integration (#590)

This extends the [foreground transport checkpoint](590-tvos-system-input.md) under issue 590,
on `codex/590-apple-system-media`, based on draft PR 606's `1736dabd653b8c5bb6d260aa5fe20399f3f6b306`.
Apple's tvOS Simulator remains the owner's acceptance environment. Physical
Apple TV testing is optional; HDMI, hardware audibility and physical remote behavior remain
explicitly unverified.

## Common ownership and native boundary

`NaviampExternalPlaybackSurfaceSession` in `core:presentation` owns the complete media-session
lifetime, capability validation, queued command dispatch, metadata publication decisions,
artwork loading/cancellation and stale-result rejection. It reuses the existing external-playback
bridge, publication planner and interruption policy. Ordinary clock updates do not republish
native metadata; pause, seek, semantic content and artwork changes do. No playback update cadence
or animation behavior changes.

OS callbacks enqueue intents on the owning common scope and validate capabilities again before
execution. Invalid/nonfinite/out-of-bounds seek positions are rejected. Closing invalidates
retained callbacks immediately. A canceled provider request cannot decode/publish stale artwork,
even if the provider ignores cancellation. Replaced/removed artwork clears immediately; missing,
empty or invalid artwork does not create a retry loop. Existing translated external-playback
labels supply the native Favorite command title; there is no new UI copy or setting.

Core's interruption policy pauses an active track, resumes only when the OS permits it, and
cancels pending resume after output loss or an explicit external transport command. An already-paused
player is not automatically started. Synthetic notification tests exercise native translation;
they do not establish handling of a real incoming call or physical route loss.

`NaviampRecoverableHostController` registers host effects before attachment can fail or call back.
It drains them in reverse order before disposing Core and then the native audio/database effects.
Partial startup, cancellation, reload and throwing cleanup cannot leave a second registered
session. A failed critical media attachment releases the application and requires reload;
dismissing an error cannot leave unavailable system controls advertised. Callbacks from an old
attempt cannot tear down its replacement. Recovery data is preserved.

`NaviampStoredApplication.attachPlaybackSurface` assembles the session with the common provider
artwork loader. The TV host supplies only its Apple effect and native resource lifetime. Original
iOS retains the existing `IosNativePlaybackIntegration` constructor and delegates to the same
common owner and Apple effect.

Changed native production files and their exact placement justification:

- `platforms/apple-media/src/appleMain/kotlin/app/naviamp/apple/media/ApplePlaybackSurface.kt`
  invokes MediaPlayer command registration/Now Playing, AVAudioSession notifications and UIKit
  image decoding; these Apple SDK APIs cannot compile in common Kotlin. It translates values and
  retains/releases native handles only, without provider logic, coroutines or retry policy.
- `platforms/ios/src/iosMain/kotlin/app/naviamp/ios/playback/IosNativePlaybackIntegration.kt`
  preserves the existing iOS entry point while constructing the native UIImage/MediaPlayer
  adapter and delegating all session behavior to Core; roughly 280 lines of former host behavior
  are replaced by delegation.
- `apps/tvos/src/tvosMain/kotlin/app/naviamp/tvos/NaviampTvosApplication.kt` mounts the native
  MediaPlayer effect alongside its existing UIKit/SQLite/BASS resource boundary. Native capability
  facts are published only through successfully attached shared startup.
- `platforms/apple/src/appleMain/kotlin/app/naviamp/apple/playback/AppleBassPlayback.kt`
  configures the native BASS iOS-session flag before the existing AVAudioSession category setup;
  only the native library/Apple SDK boundary belongs here.

The separate `platforms:apple-media` module keeps UIKit/MediaPlayer and the shared UI graph out of
the headless public-BASS foundation. Original iOS targets are always present; TV targets require
the scene-host flag. The TV Info.plist declares audio background mode, matching its existing
Playback-category AVAudioSession. Public direct BASS/FLAC/Opus and original iOS's add-on inventory
are unchanged. No new persistence schema, migration, setting or export format is introduced.
Android's minimum remains API 26 (Android 8).

Apple documents manual Now Playing publication through the shared command center for engines
that do not use AVPlayer. This integration therefore does not require replacing direct BASS
with AVPlayer or creating a dummy AVPlayer session.

## Defects reproduced and fixed

Actual native tests exposed BASS replacing the requested Playback-category session with its default
`MixWithOthers` option. The first TV probe reported session options `1` and BASS session flags
`129`, failing the nonmixing assertion. Apple requires a nonmixing audio session for Now Playing
eligibility ([Apple WWDC22](https://developer.apple.com/videos/play/wwdc2022/110338/)). The Apple
BASS adapter now sets `BASS_IOS_SESSION_DISABLE` before configuring the
session through AVAudioSession. Per BASS's API contract this disables its category management,
while retaining activation handling ([BASS session configuration](https://www.un4seen.com/doc/bass/BASS_CONFIG_IOS_SESSION.html)).
Actual iOS initialization and TV start/resume tests now
report Playback with options `0`; the TV probe reports BASS flags `16`.

The app test also exposed a progress rollback after resume. All three native bindings use
`BASS_ChannelGetPosition(BASS_POS_BYTE)`, which already returns a direct output's playback cursor
([BASS position API](https://www.un4seen.com/doc/bass/BASS_ChannelGetPosition.html)).
The common read policy incorrectly subtracted buffered output again while playing, but used the
raw cursor when paused. It now uses the direct native cursor unchanged, retaining mixer-history
selection and invalid-value validation. Four focused assertions failed before the fix; all eleven
read-policy cases pass after it. The new real-output TV regression verifies a populated buffer,
moving native cursor, matching shared cursor, stable paused cursor and no rollback after resume.
The independent buffer query for startup-stall detection remains; update cadence is unchanged.

The native UIKit invalid-image regression reproduced a Kotlin NullPointerException from its
failable constructor. The adapter now uses nullable `UIImage.imageWithData`; empty and malformed
bytes safely return null.

## Verification

The full JVM domain/presentation/bootstrap suites pass **1,178 + 644 + 11 = 1,833 tests**, with no
failures, errors or skips. They include fifteen new media-session cases and eight total recoverable-host
cases. Android common compilation and the architecture guard pass; the guard now explicitly
includes the new Apple adapter directory.

Two actual-SDK component tests execute on each of iOS Simulator 26.5 and tvOS Simulator 27.2,
with zero failures/errors/skips. They verify real Now Playing dictionary/artwork publication,
native command capability flags and translated title, valid/empty/invalid UIKit decoding,
idempotent clearing, AVAudioSession notification translation and observer removal. Both owned
simulators shut down and delete successfully. The invalid-image regression remains in these tests.

Four real native TV BASS cases and the new original-iOS real initialization case pass without
failures/errors/skips. The iOS case is added to the required prebuilt-native inventory (now nineteen
cases), with a matching manifest regression, so hosted replay cannot silently omit it.

Windows's full native binding class was attempted against real output: ten cases fail during
`BASS_Init` with driver error `3`, before the clock assertions; two pass. Endpoint inspection
confirms no connected playback endpoint (the previous BenQ and Pixel Buds entries are `Unknown`;
only the microphone is `OK`). A separate no-sound run passes ten native cases and explicitly skips
the two that require real output, including the new clock regression. Those skips do not constitute
real-output acceptance. The earlier BenQ success in the Windows performance report remains valid
for its earlier source; the new real Windows clock regression remains unverified.

Original iOS device/simulator and TV device/test-source compilation pass. The seven strict
fixture authentication/audio/range/ownership regressions also pass.
The focused verification-tool suite passes all thirty-nine tests on macOS, including actual
POSIX process-group cleanup. Windows passes thirty-eight and explicitly skips that POSIX-only
case. Local Git replay requires the normal Windows credential/permission context; its initial
sandbox clone errors disappear when rerun there, without changing the replay implementation.

The original iOS framework is rebuilt and linked against official artifacts, then its Xcode app
is built, installed and launched on a newly owned iOS 26.5 simulator. It remains running after
startup; the connection setup screenshot is visually inspected. Shutdown/delete both succeed.
Final TV device compilation passes. A feasibility-only headless BASS task graph contains no
Core UI, presentation or Apple media tasks. The new TV simulator framework passes the complete
deployment audit: **3,960 ARM64 objects, platform 8, zero violations above 18.5**. This metadata
audit does not establish compatibility on an older runtime. The ten original-iOS source
identities and the additional 619-file TV runtime source audit are retained for comparison.
All ten original-iOS identities match this checkout. The final incremental device compile takes
71.136 seconds, original-iOS compile/framework link 130.239 seconds, and Xcode iOS build 42.181
seconds. The TV Swift header remains 15,641 bytes, exporting only `core:host-api`. These checks use
two workers / 4 GiB and the separate verified Native caches; they do not rebuild Compose producers.

Native component evidence: Mac `build/590-media-sdk-tests-v3/{summary.json,ios.log,tvos.log}`;
the preceding failure is retained separately in `build/590-media-sdk-tests-v1`. Shared evidence:
Windows `build/590-feasibility/tvos-media-clock-final-common.log`,
`tvos-media-architecture.log` and `tvos-media-fixture-tests-v2.log`.
Native session/clock evidence is Mac `build/590-media-audio-session-probe-v3` (four TV cases)
and `build/590-media-ios-session-v1` (one iOS case plus both original app compilations).
Windows native attempts are `tvos-media-windows-native-v2.log` and
`tvos-media-windows-native-nosound.log` under `build/590-feasibility`; tooling evidence is
Mac `build/590-media-tooling-mac-v3.log` and Windows `tvos-media-tooling-final-v2.log` plus
`tvos-media-source-tooling.log`.
Original-host evidence is Mac `build/590-media-original-ios-v3`, with local
`apple-native-media-ios-{summary.json,startup.png}`; deployment evidence is
`build/590-media-simulator-deployment-v2` on the Mac. These are bounded validation timings,
not comparative performance measurements.

## App-level acceptance and remaining gates

The complete TV scene case additionally tests Home during active BASS playback, background system
Play/Pause, and background resume/pause without foregrounding the app. This cannot reach the
Compose foreground media-key fallback because PineBoard owns foreground input. The test retains
main/Aurral native-keyboard setup, process relaunch, encrypted cache-purge recovery, Home/return
and held foreground media-button checks. The silent 180-second WAV establishes decoder/transport
and clock behavior, not audibility.

The final app case passes on a newly owned tvOS 27.2 simulator: **one test, zero failures,
errors, skips or expected failures**. All three create/relaunch/purged stages authenticate both
accounts independently without rejected authentication; the encrypted recovery bytes remain
unchanged through cache purge and the final probe proves the database was rebuilt. Shutdown
and delete both return zero. All **25 scoped source identities and 619 runtime source identities**
match this Windows checkout. The pinned port manifest remains
`3f6d453c0de05b643ac94d14bbb296ddee9429df5308806baa9d1e2e21864193`
(52 publications / 1,476 files); no Compose producer or artifact changes were needed.

The retained native OS log explains the test adjustments. A Now Playing banner can consume a
TV/Home press to reopen the player, so the post-playback Home test uses Apple's documented held
Menu gesture. From a paused Home screen, Play/Pause did not deliver Play to the app; opening
Control Center's Now Playing tab did. The accepted case verifies its native Play-to-Pause control
transition and that Naviamp remains backgrounded during resumed audio, then verifies the advanced
app clock stays paused after returning. No product-only test entry point or synthesized media
callback bypasses the system controls.

Listening mode deliberately hides app transport buttons. Playback assertions therefore use the
accessible clock rather than sending an unrelated directional input to reveal them. On return,
the test waits for the clock to reflect background progress before checking pause. A bounded
pause assertion requires three continuous seconds of stability; continued playback of the
180-second fixture changes its integer percentage every 1.8 seconds and cannot satisfy it.
No media command is retried to turn a failure into acceptance.

App evidence is Mac `build/590-media-background-1115-v15-clock-contract`, including summary,
telemetry, screenshots, accessibility trees and `system-media.log`. Xcode build-for-testing takes
35.038 seconds and test execution 202.766 seconds, with two Gradle workers / 4 GiB. Native Control
Center and the returned 0:23 paused app screenshot are visually inspected. The app test reports
one runtime warning from the pre-existing synchronous `AVAudioSession.setActive` call: profile
activation latency and evaluate an availability-safe asynchronous native effect before performance
signoff. No activation failure or measured responsiveness result is inferred from that warning.

Broader transport commands, actual interruption/route behavior, simulator
accessibility, authenticated TLS/range/seek recovery, certificate/settings/error/reset GUI flows,
discovery/pairing, older-runtime compatibility, trusted port delivery/hosted scene CI and
signing/distribution remain under #590. Existing original-platform performance/restore/native
accessibility gates remain open. No physical-device acceptance gate is added. PR 606 stays draft.

Next work stays within #590: verify native seek/previous/next and actual OS interruption/route
transitions with the same shared owner; extend authenticated TLS/range and GUI error/reset
acceptance; then address discovery/pairing and trusted hosted scene builds. Preserve bounded
Native builds, the small Swift export, headless/UI graph isolation and Android 8 support.

## API references

- [Apple: supporting remote interactions in tvOS](https://developer.apple.com/documentation/AVFoundation/supporting-remote-interactions-in-tvos)
- [Apple: configuring an app for media playback](https://developer.apple.com/documentation/avfoundation/configuring-your-app-for-media-playback)
- [Apple: MPNowPlayingInfoCenter](https://developer.apple.com/documentation/mediaplayer/mpnowplayinginfocenter)
- [Apple: handling external player events](https://developer.apple.com/documentation/mediaplayer/handling-external-player-events-notifications)
- [Apple WWDC22: manual metadata publication and remote interactions](https://developer.apple.com/videos/play/wwdc2022/110338/)
- [BASS: native audio-session management](https://www.un4seen.com/doc/bass/BASS_CONFIG_IOS_SESSION.html)
- [BASS: playback/decoding position distinction](https://www.un4seen.com/doc/bass/BASS_ChannelGetPosition.html)
- [BASS: buffered output query](https://www.un4seen.com/doc/bass/BASS_ChannelGetData.html)
- [Apple: Home and held Menu gestures](https://support.apple.com/en-ca/guide/tv/atvbe55a4b4e/27/tvos/27)
- [Apple: music controls in Control Center](https://support.apple.com/en-gb/guide/tv/atvbef12564e/tvos)
