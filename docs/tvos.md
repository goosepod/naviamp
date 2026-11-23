# Apple TV (tvOS)

Apple TV support is tracked by [#590](https://github.com/goosepod/naviamp/issues/590). Starting
with v2.12.0 it is a beta available only by building from source: ordinary Android, Desktop and iOS
builds do not include tvOS targets, and no Apple TV download is published. Public material must not
claim an Apple TV download or App Store availability until the release gates below close.

## Architecture

Apple TV is a thin host of the shared runtime ([ADR 0001](architecture/0001-shared-runtime-thin-hosts.md)).
It uses the shared Television surface, Core runtime, providers, queue, connection setup (including
Aurral companions), navigation and settings. There is no tvOS controller, provider or persistence
policy.

| Module | Responsibility |
| --- | --- |
| `apps/tvos` | Xcode project `NaviampTV`, UIKit scene and window lifetime, recovery storage effects (UserDefaults, Keychain, CryptoKit), UIKit text entry, and `NaviampTvosApplication`, which wires native resources into the shared recoverable host. |
| `platforms/tvos` | Links the public tvOS BASS frameworks and supplies their inventory. |
| `platforms/apple` | Shared Apple BASS C ABI, callbacks and `AVAudioSession` effects used by iOS and tvOS. |
| `platforms/apple-media` | MediaPlayer remote commands, Now Playing and audio-session notifications. |
| `core/*/appleMain` | Shared Apple effects such as the raster presenter, SQLite driver, file storage and URL opening. |

### Storage and recovery

tvOS may purge the Caches directory between launches, so the library, artwork and audio databases
are rebuildable cache. Settings and connections live in a small encrypted recovery document in
UserDefaults, with its key in the Keychain. After a purge, Core restores the selected connection
and its Aurral companion, rebuilds derived data and does not autoplay. Keep UserDefaults content
small: tvOS warns at 512 KB and terminates at 1 MB.

When saved data cannot be opened, the shared recovery screen offers **Retry**, and on tvOS also
**Reset**, which erases this device's saved settings and connections. The server library is not
touched. iOS uses the same screen with Retry only, because its durable database has no reset effect.

## Playback

The app uses direct BASS 2.4.18.14 from Un4seen's public tvOS archive: BASS, BASSFLAC and
BASSOPUS ([inventory](../platforms/tvos/vendor/bass)). The public archive has no BASSmix, so
gapless playback, crossfade and the custom multichannel-to-stereo downmix are unavailable. Shared
engine capabilities hide those controls while preserving the saved preferences. Formats need the
BASS core decoders or FLAC/Opus; other add-ons available on iOS are not shipped.

## Building

Enable the targets with `-Pnaviamp.tvos.enabled=true`. The app host additionally needs
`-Pnaviamp.tvos.sceneHost=true` and the reviewed Compose Multiplatform tvOS artifacts described in
[`build-support/tvos-compose`](../build-support/tvos-compose/README.md). The Xcode build phase passes
both properties. Use exact target tasks (Debug simulator for iteration; optimized device Release only
for archives), and keep one consistent `KONAN_DATA_DIR` per checkout: mixing native homes produces
duplicate platform KLIB errors.

Device Release builds use the bundle ID `app.naviamp.ios` so Apple TV shares the iOS App Store
record. Debug and simulator builds use `app.naviamp.tvos`. `scripts/validate-ios-version.py` keeps
both Xcode projects on `VERSION` and `VERSION_CODE`. The deployment target is tvOS 18.5.

## Testing

- **Native BASS and shared Apple code:** `python3 scripts/run-tvos-bass-tests.py --output <new dir>
  --shared-foundation` runs Core and direct BASS tests on a fresh tvOS simulator. The
  `tvos-native.yml` workflow runs the same command.
- **Full scene:** `scripts/run-tvos-scene-tests.py` builds the app against installed Compose
  artifacts and drives setup, recovery, playback and system media with the remote against a local
  fixture server. See the Compose README for the command. `tvos-scene.yml` stays manual-only until
  the hosted Compose pin is approved ([hosted delivery](../build-support/tvos-compose/HOSTED.md)).
- **Live checks:** with `TEST_RUNNER_TV590_LIVE=1`, `testLiveSearchEditorRemoteActions` and
  `testLiveTogglePlayback` in `NaviampTVUITests` run against an already configured simulator.
  Compose focus is not visible to XCTest, so these follow the fixed TV layout.

Simulator acceptance is the owner-approved runtime environment; physical Apple TV testing is
optional and its results must not be inferred from the simulator.

### Animation cost

Screen protection dims and shifts the scene on idle. The shared raster owner moves unchanged native
layers instead of rebuilding them. In the tvOS 27.2 simulator, playing through a dim and a shift
used about 4% app CPU, the same as plain playback (previously 15.75% versus 5.97%), with the waveform
painted and moving. GPU intervals and parent-surface frames have not been re-measured since that
change, and physical hardware is unmeasured.

## Release gates

- Approve the hosted Compose artifact pin, then make the scene check required.
- Runtime acceptance at the tvOS 18.5 deployment target. Xcode 27.2 does not offer tvOS 18.x
  simulator runtimes for download; the lowest locally tested runtime is 26.5.
- Control Center Next/Previous on tvOS 26.5, and accessibility acceptance.
- GPU and parent-surface measurements for the screen-protection animation.
- App Store Connect platform setup, Apple validation, metadata and reviewer access.
- Release notes and store copy that state the BASSmix limitations above.
