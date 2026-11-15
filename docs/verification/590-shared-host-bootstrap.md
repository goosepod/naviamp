# Shared host bootstrap for #590

The iOS host previously constructed provider routers, artwork fetch/cache policy, repositories,
downloads, playback/sidecars and settings callbacks itself. Repeating that graph in a TV host
would duplicate product behavior. `core:bootstrap` now owns this complete assembly in
`NaviampStoredApplication`. Its direction is presentation/storage/providers → native ports;
presentation does not depend on providers, avoiding a dependency cycle. Android/JVM/iOS targets
are present, with TV targets under the existing feasibility flag. Original platforms retain
official Compose dependencies. Producers remain outside application builds.

The bootstrap accepts the database and credential effect, opaque settings/document effects,
audio byte/file/URL effects, playback/BASS/tag adapters, native capability facts and host identity.
It owns provider routing, TLS selection, repository composition, cache settings state, artwork
fallback, downloads, sidecars and Core construction. Hosts retain native database/audio lifetimes.
It exposes the shared environment/Core and artwork loader; a TV Swift host must expose only its
UIKit controller and lifecycle entry points, not this complete Kotlin graph.

`core:storage` now owns `StoragePlaybackAudioAssets`, `StorageAudioDirectory`, container-relocation
mapping and the known-file ownership check. Download/cache selection preserves source, track,
quality, size and file name; only file-URI conversion and actual file operations cross the native
boundary. Unknown/non-child paths cannot pass the shared deletion guard. No schema, migration,
setting, export format or supported Android/iOS minimum changes.

## Placement audit

Changed platform production files:

- `apps/ios/src/iosMain/kotlin/app/naviamp/ios/NaviampIosApplication.kt`: UIKit controller/CALayer
  mounting, NSUserDefaults/document effects and native database/BASS/media-session resource lifetime;
  all provider/cache/playback assembly delegates to the common bootstrap.
- `platforms/ios/src/iosMain/kotlin/app/naviamp/ios/storage/IosAudioStorage.kt`: Foundation/POSIX
  atomic byte IO, native regular-file queries/deletion and NSURL file-URI conversion; portable audio
  lookup, relocation and ownership checks move to Core.

The iOS build now depends on `core:bootstrap` rather than directly on both providers. Its public
constructor and existing storage export remain unchanged. The bootstrap declares SQLite linkage
for native executables; the first new native test link exposed missing system SQLite symbols and
failed until that explicit `-lsqlite3` boundary was added. No animation cadence, renderer setting,
capability budget or native assertion was relaxed.

## Verification and remaining work

JVM and Android host tests execute the complete assembly with real SQLite and strict unused-native
effects. They verify settings normalization/recreation, mixer-free UI capability propagation,
successful artwork cache reuse and failed fetch handling. Storage common tests cover relocated
file URI/metadata, relative/current paths, missing files and guarded deletion; existing storage
suites remain in place.

| Check | Result |
| --- | --- |
| JVM bootstrap / storage | 2 / 80 passing cases |
| Android host bootstrap / storage | 2 / 8 passing cases |
| Original Android Debug APK / Desktop compilation | Pass; minimum API remains 26 |
| Original iOS bootstrap and app device / simulator compilation/link | Pass |
| iOS 26.5 bootstrap / storage | 2 / 12 passes; zero failures/errors/skips |
| Rebuilt iOS app startup | Installed/launched; inspected connection setup painting |
| Original iOS Keychain test | 1 pass; zero failures |
| tvOS bootstrap device / simulator compilation | Both pass in the diagnostic private-port graph |
| tvOS 27.2 bootstrap / storage / direct public BASS | 2 / 11 / 3 passes; zero failures/errors/skips |
| Native simulator cleanup | All owned shutdown/delete operations return zero |

The bootstrap graph has 146 selected iOS simulator modules with **zero** private artifacts;
each TV graph has 147 modules including sixteen reviewed private-port artifacts. Official
Lifecycle 2.11 remains selected. This run uses the previous sealed 52-publication/1,476-file
repository identity `3f6d453c0de05b643ac94d14bbb296ddee9429df5308806baa9d1e2e21864193`,
not the separately reconstructed candidate. Compiling the common bootstrap does not enable the
normal application TV host or establish older-runtime support.

Common Android/JVM/iOS compilation preceded host delegation; native runtime checks then exercised
the final shared owner and rebuilt host. The exact source hashes from both Apple
reports match the Windows workspace after LF normalization. Evidence: Windows
`build/590-feasibility/bootstrap-{common-windows,original-targets}.log`,
`bootstrap-{apple,tvos}-summary.json`, `bootstrap-tvos-variants.json`, and inspected
`bootstrap-ios-startup.png`; owned Mac roots `build/590-bootstrap-common-1115-v3` and
`build/590-bootstrap-tvos-1115-v2`. The first TV test harness attempt incorrectly configured an
included settings-plugin build; its isolated script was bounded to the Naviamp root before the
passing repeat. No product guard or test assertion was removed to obtain that pass.

This is host preparation, not a completed tvOS application. Remaining gates include bounded
authoritative settings/connections and Keychain secret references with purge recovery; permanent
shared Apple UI effects and reviewed artifact delivery; the thin TV scene/remote/system host;
authenticated native streaming and physical HDMI/remote/lifecycle acceptance. The Windows visualizer,
resize and accessibility limitations remain in the November 15 report. Keep #590 open and PR606 draft.
