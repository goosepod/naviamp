# Issue 590: shared application/provider target foundation

The opt-in `naviamp.tvos.enabled=true` graph now includes device and simulator targets for
`core:app`, `core:storage`, `providers:navidrome` and `providers:jellyfin`, in addition to Domain
and the direct native BASS adapter. Ordinary builds do not create these extra targets. No TV
controller, repository, authentication flow, schema or duplicated provider implementation was added.

Navidrome's two existing Apple effect files moved from `iosMain` to `appleMain`. The HTTP engine
dependency moved with them. Both Git blobs are identical before/after the move:

| File | Git blob | Required native boundary |
| --- | --- | --- |
| `NavidromeApplePlatform.kt` (formerly `NavidromeIosPlatform.kt`) | `aeb82c03971786fb9606f16adcc142b9efc9d9bb` | Ktor Darwin's NSURLSession configuration, Foundation authentication challenges and native credentials |
| `NavidromeAppleTlsMaterial.kt` | `94877c2e00b839ea23833a6d02ad2fb0c1545c50` | Apple Security/CoreFoundation certificate, identity and trust objects with native reference lifetimes |

These adapters cannot run in common Kotlin because those APIs exist at the Apple platform
boundary. Existing common parsing, challenge decisions, models and provider behavior remain their
owners. The move lets iOS and tvOS compile the same effects instead of copying them. No other
platform production source changed in this step; build scripts and the test runner provide wiring.

## Local validation, November 15

All four modules compiled for **iOS ARM64 device, iOS ARM64 simulator and tvOS ARM64 device**:
12 requested compilations, successful in 61 seconds with existing caches, 33 tasks (15 executed).
This is a warm integration check, not a cold build-performance measurement.

Their existing common suites then executed in a fresh owned tvOS **27.2** simulator, alongside
Domain and the direct native backend. The runner requires fresh XML, nonempty suite inventories,
no failed/errored/skipped cases, matching source hashes and successful simulator cleanup.

| Suite | Cases | Failures/errors/skips |
| --- | ---: | --- |
| Domain | 1,176 | 0 / 0 / 0 |
| Shared application | 275 | 0 / 0 / 0 |
| Shared storage location policy | 3 | 0 / 0 / 0 |
| Navidrome/Aurral provider | 312 | 0 / 0 / 0 |
| Jellyfin provider | 36 | 0 / 0 / 0 |
| Direct BASS native integration | 3 | 0 / 0 / 0 |
| **Total** | **1,805** | **0 / 0 / 0** |

The invocation took 143.835 seconds including new simulator test compilation/links and execution;
the native BASS binary remained `9507509edd098d526ea9197b3514c349c4ab4e2cb123e3cc619333afd27c06fb`.
No BASSmix dependency/symbol was linked. Owned simulator shutdown/delete both returned 0.

Windows JVM/Android host suites for the four modules also passed: **1,379 reported cases**, one
existing conditional Navidrome JVM skip, zero failures/errors. Shared Android source-graph and
Core-first architecture checks pass. Native Apple adapters compile through their new shared source
set for the original iOS targets. Fresh canonical-source CI remains required independently of the
local Mac overlay and preceding passing runs.

`scripts/run-tvos-bass-tests.py --shared-foundation` reproduces the owned-simulator suite set.
The bounded tvOS workflow now compiles the four device targets and enables this flag. Its ABI/SDK
cache policy remains separate from the UI producer; PRs restore read-only and do not write shared
compiler caches. The workflow runs for the newly covered module paths too.

Evidence: local `build/590-feasibility/tvos-foundation-{windows,native,compile}-summary.json` and
`tvos-foundation-windows.log`; Mac `build/590-shared-tvos-foundation/{compile.log,simulator}`.
The initial setup attempts failed before compiling (system Python lacked `tarfile`'s safe extraction
filter; sandboxed Windows Gradle could not lock its existing cache). The accepted runs used installed
Python 3.14 and the existing Windows credential/cache context. Failed attempts were not passes.

## Limits before host wiring

This proves portable behavior compiles and executes on tvOS. It does not yet provide TV filesystem
or secure-storage effects, bounded persistent settings/cache policy, Core presentation/UI closure,
a signed app or physical input/audio. The native driver follow-up below supplies the shared SQL
effect, without selecting a TV filesystem location. Provider tests mostly use the
existing mock HTTP effects: native tvOS authenticated TLS/range streaming still needs a real fixture.
Do not treat iOS TLS success as that test. Full-app UI resolution must also address Lifecycle 2.11
and use the [verified private artifact pipeline](590-compose-artifact-delivery.md).

## Shared native SQLite follow-up

`NativeStorageDriverFactory` now contains the original NativeSqliteDriver effect in `nativeMain`,
usable by both iOS and TV. Its SQLDelight Native/sqliter dependency, WAL, 10-second busy timeout,
foreign-key configuration and canonical `NaviampStorageSchema` remain the same. The original
`IosStorageDriverFactory` constructor/API remains as a ten-line delegation wrapper. No schema or
migration changed. The absolute POSIX path check moved into the common location model and has a
common regression for relative/traversal/Windows-style inputs; no filesystem location is chosen
by the model or driver.

The separate production-native audit is:

| Changed native production file | Concrete boundary |
| --- | --- |
| `core/storage/src/nativeMain/kotlin/app/naviamp/storage/NativeStorageDriverFactory.kt` | NativeSqliteDriver/sqliter configuration and native connection lifetime; unavailable to JVM/common Kotlin |
| `core/storage/src/iosMain/kotlin/app/naviamp/storage/IosStorageDriverFactory.kt` | Compatibility delegation into that native SQL driver, with no repository/persistence policy |

Three real Apple native adapter cases now verify canonical schema/version plus enabled foreign
keys, committed-row persistence across close/reopen in the selected directory, and schema creation
after deleting/recreating an **owned test directory** to simulate a cache purge. They execute on
both TV 27.2 and iOS 26.5. The TV suite set now reports **1,809** cases (storage: seven), zero
failures/errors/skips. iOS storage reports **eight** cases, including the original compatibility
wrapper test, zero failures/errors/skips. Both owned simulators cleaned up successfully. The three
device/simulator native storage compilations and JVM/Android common suites also pass.

The strict TV runner requires all three native SQL test names and records their source identities;
an empty or common-only storage result cannot satisfy this gate. Evidence: Mac
`build/590-native-storage-shared`, local `build/590-feasibility/native-storage-{ios,tvos}-summary.json`
and `native-storage-windows.log`. This is SQLite recreation evidence, **not** recovery of user
connections/secrets/settings after an OS purge.

Apple's [archived TV storage guidance](https://developer.apple.com/library/archive/documentation/General/Conceptual/AppleTV_PG/)
requires non-defaults local data to tolerate purging. Its current
[UserDefaults limit documentation](https://developer.apple.com/documentation/foundation/userdefaults/sizelimitexceededmessage)
warns at 512 KB and describes process termination at 1 MB. Before a real TV host, define the
authoritative bounded connection/settings snapshot and secure-secret effects in shared contracts;
keep derived SQL/index/artwork/audio data rebuildable. Do not copy iOS's large file layout/defaults
store, silently discard oversized imported preferences, or promise durable TV downloads.
