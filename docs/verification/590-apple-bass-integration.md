# Shared Apple and direct tvOS BASS integration (#590)

November 15, 2026. Issue branch `codex/590-tvos-feasibility`, draft PR #606. This is a native
integration milestone, not full Naviamp tvOS host or release acceptance. Android minimum remains
API 26. [Windows measurements](590-windows-performance-2026-11-14.md) and the
[implementation plan](../tvos-implementation-plan.md) retain the outstanding performance,
renderer-production, deployment, storage and physical-device gates.

## Ownership and target graph

`core:domain` owns playback, queue transitions, feature capabilities, settings, internet defaults,
audible-position compensation, weighted waveform analysis, equalizer plans and preview routing.
The new Native-only `platforms:apple` module implements only BASS C calls and the Apple
AVAudioSession/Foundation/StableRef boundary. It has no Compose dependency or second controller.

Its base interop uses `bass.h` only, moved byte-identically from the iOS vendor directory.
The existing iOS module now binds only `bassmix.h`, depending on the shared base ABI. Its small
specialization retains the existing iOS mixer version, channel/matrix operations and mixer-aware
seek/position calls. All eleven existing iOS frameworks remain linked/embedded; the Swift host's
inventory was not reduced. Base imports in the two native test files follow their new owner.

`platforms:tvos` has only the public distribution facts and Core-engine factory. It inherits
the common unsupported-mixer defaults. Core derives gapless/crossfade/custom-downmix availability;
portable saved preferences are preserved. Public BASS, FLAC and Opus XCFrameworks are vendored
byte-for-byte with an 18-file SHA-256 inventory. No iOS binary is relabeled, and no BASSmix symbol
is required. The vendor directory documents the archive/header/license provenance.

Ordinary builds include only the new iOS-capable Apple module. Both tvOS targets and the tvOS
module require `-Pnaviamp.tvos.feasibility=true`. Normal Android/Desktop/iOS resolution does not
load private Compose port artifacts or build AndroidX sources. This milestone needs only the
official Core dependencies and public native binaries, so it can precede full UI-host enablement.

## Shared preview correction

The native isolation test exposed a real shared routing omission: preview playback always sent
non-random-access sources through `BASS_StreamCreateURL`, including `file:` URLs. Native BASS
rejected those with unsupported-protocol error 48. `BassPreviewPlayback` now chooses the existing
file effect after native URL translation; authenticated random access retains priority, and remote
URLs still use the network effect. A failed file open reports its original failure without a
network retry. Three common regressions cover those cases and exact resource cleanup.

Core tests/compilation on Android, JVM and both iOS architectures preceded the native rerun.
There is no tvOS-specific preview policy, new setting or UI copy.

The first control fixture also passed a raw path where Core requires a URL; that test-input error
was corrected using Foundation's encoded file URL. A later three-second fixture could reach
natural completion before the combined preview/pause sequence finished. Retained state traces
show a position of 2.960s before pause and a passing trace with almost no remaining margin.
The final fixture lasts eight seconds and seeks to six seconds before natural completion.
The application's polling, recovery and animation frequencies were not changed.

## Reproduction and CI

On a Mac with the normal JDK 21, Android configuration SDK and Xcode/tvOS runtime installed:

```sh
./gradlew --console=plain --max-workers=2 \
  '-Dorg.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8' \
  -Pnaviamp.tvos.feasibility=true \
  :platforms:tvos:compileKotlinTvosArm64 :platforms:tvos:verifyTvosBassInventory
python3 scripts/run-tvos-bass-tests.py --output build/tvos-native-fresh
```

Use a fresh output directory. Supply the same `KONAN_DATA_DIR`/`konan.data.dir` to every invocation;
the runner accepts repeatable `--gradle-arg=...` options. Do not reuse a different compiler's
producer cache. The runner creates its own compatible Apple TV simulator, executes all Domain
tests plus the exact three-case native inventory, rejects stale XML/failures/skips, records source
and binary hashes, audits dynamic libraries/undefined symbols for BASSmix, and shuts down/deletes
only its owned simulator. Files, test logs and XML remain evidence. Test audio is muted.

`.github/workflows/tvos-native.yml` adds bounded device compilation and simulator verification.
It is separate from the original-app Verify/release acceptance matrix. Its native cache identity
includes CPU, Kotlin, wrapper/catalog/GC policy, Xcode, both tvOS SDK builds, cinterop headers and
vendored binary hashes. There is no fallback across those identities, no iOS cache reuse, and only
successful default-branch runs can save caches. PR restores are read-only. This job builds exact
native targets with two workers/4 GiB; it does not rebuild the Compose producer or link a Release
application archive on every edit. The original iOS cache writer now also watches Apple headers.
On an image without a usable tvOS runtime, the bounded setup step uses Apple's documented
[`xcodebuild -downloadPlatform` command](https://developer.apple.com/documentation/xcode/downloading-and-installing-additional-xcode-components)
for the installed simulator SDK version; it does not silently skip native execution.

## Platform production diff audit

- `platforms/apple/src/appleMain/kotlin/app/naviamp/apple/playback/AppleBassPlayback.kt`:
  BASS C ABI, pinned buffers, native callback/StableRef and plugin lifetimes, AVAudioSession,
  Foundation URL translation and NSLock operations cannot compile in common Kotlin.
- `platforms/ios/src/iosMain/kotlin/app/naviamp/ios/playback/IosBassPlayback.kt`:
  the remaining BASSmix ABI calls require the iOS mixer binary; factory/aliases delegate to the
  shared Apple adapter. Its former base adapter was extracted rather than copied for tvOS.
- `platforms/tvos/src/tvosMain/kotlin/app/naviamp/tvos/playback/TvosBassPlayback.kt`:
  selects the three actually bundled tvOS native frameworks and wires the shared Core engine.

Build scripts, cinterop declarations, unchanged vendor binaries and native integration tests are
build/ABI metadata or adapter verification. No Android/Desktop/app-iOS production file changes
belong to this extraction. The preceding shared position/network extraction's five platform files
and two JNI files have their separate audit in the [upgrade checkpoint](590-supported-upgrade-checkpoint.md).
The architecture task now inspects iOS, shared Apple and tvOS production sources too.

## Completed validation

| Check | Result |
| --- | --- |
| Domain JVM / Android host | 1,176 each, zero failures/errors/skips |
| Domain iOS simulator / tvOS simulator | 1,176 each, zero failures/errors/skips |
| Required iOS native inventory | 18, zero failures/errors/skips, including real mutual TLS and mixer/downmix |
| tvOS native inventory | All three cases pass: WAV/FLAC/Opus decode/seek, EQ, waveform/network values, FILEPROCS, Core controls and independent preview |
| Base adapter compilation | iOS device/simulator and tvOS device/simulator pass |
| Original iOS app | Debug simulator framework link and device compile pass with tvOS disabled (1m27s) |
| Original Windows/Android hosts | Desktop compile, rebuilt Android Debug APK and expanded architecture check pass (10s) |
| Native integrity | All 18 public tvOS files match; base header unchanged; no BASSmix dynamic dependency/undefined symbol |
| Helpers | Six feasibility cases; four cache-helper cases on Mac; Python syntax and actionlint 1.7.12 pass |

Final routing reuses the existing `isPlaybackLocalFileUrl` policy. Its last Windows repeat ran
all 1,176 JVM cases and the six focused Android preview cases; the earlier Android full run had
1,176 passes. Both Native Domain targets then ran all 1,176 cases again with the final source.

The final tvOS invocation took **109.698s** with its existing dependencies, including common
recompilation/test links and fresh execution. The preceding native-fixture iteration took
41.829s. Neither is a cold build or Release benchmark. Final native binary SHA-256:
`9507509edd098d526ea9197b3514c349c4ab4e2cb123e3cc619333afd27c06fb`.
Final iOS native execution took **4.504s**, binary SHA-256:
`03005faaba9c4e220e9ff84c4d4f5197f509868950735048cc14b8da5afa0743`.
All owned simulator shutdown/delete operations returned 0; existing user simulators were left alone.

The local Mac received source files from the Windows workspace; raw hashes therefore reflect
those files' line endings. A separate staged-source check verified all five recorded inputs match
the tested bytes before Git's text normalization, and that their LF-normalized content exactly
matches the canonical index. Vendor XCFramework inputs and the relocated upstream header are
explicitly `-text`; the header and all 18 vendored files were checked byte-for-byte in the index.
Fresh CI will execute the canonical checkout. The runner records both raw and LF source hashes.

Evidence: local `build/590-feasibility/apple-preview-shared-url-{windows,ios,tvos}-summary.json`,
the earlier `apple-preview-final-*-summary.json`, `apple-preview-common-windows.log`,
`apple-preview-shared-url-policy-windows.log`, `apple-base-final-original-windows.log`; Mac checkout
`build/590-apple-native/build/{apple-preview-shared-url-policy-tvos,apple-preview-shared-url-ios-execution}` and
`build-apple-base-{interop-corrected,native-test-link,original-ios-host}.log`. Failed fixture-input,
preview-routing and short-fixture attempts remain in their separate evidence directories.
The unchanged iOS-cache test's Unix path-spelling assertion fails on Windows; the same four
tests pass on their intended Mac host. It was not counted as a Windows pass or hidden by a skip.

Verify run **888** fully passed at preceding source
`5a57042511fdc5a4679a3c13d3f09a173433137f` (all Android levels/Desktop/shared/iOS/evidence).
That is the position/network prerequisite, not CI proof for this subsequent Apple extraction.
Fresh full Verify and the new tvOS workflow must validate the committed extraction independently.

## Remaining acceptance

The full shared Naviamp UIKit/Compose television host is not enabled. Before enabling it, finish
the stable producer's deployment/artifact pipeline, bounded shared tvOS storage policy and common
runtime/provider/UI target closure. Native authenticated tvOS range/TLS streams, queue/preparation,
interruption/route changes, Now Playing, remote/focus/VoiceOver, signing and physical HDMI output
remain explicit tests. iOS mutual TLS passing is not tvOS mutual TLS evidence.

The public native tests use tvOS 27.2 only. They do not select a supported app minimum or prove
physical audibility. Windows real-output driver initialization and final animation/compositor
budget acceptance remain open; the recorded measurements are not a universal performance sign-off.
No new issues, store claims or release artifacts were created for this milestone.
