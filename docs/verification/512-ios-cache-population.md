# Trusted iOS compiler caches and hosted measurements

Tracked by [#512](https://github.com/goosepod/naviamp/issues/512).

The November 9 inventory is still empty: Verify restores Gradle state read-only and no
default-branch writer populates it. The earlier local 260.330s → 45.434s task-cache comparison
is not a hosted cold/warm result and does not measure dependency or native compiler downloads.

## Writer and consumers

`iOS cache population and diagnostics` is a separate, non-gating workflow. Relevant pushes to
`main` populate caches once; manually dispatching it on the default branch offers:

- `populate`: restore compatible state, compile both Apple targets and run common tests, then save.
- `benchmark`: deliberately skip restoration on one fresh hosted runner, compile and execute the
  native boundary suite, save both archives, then repeat the same source/workload on another fresh
  hosted runner. The warm stage requires the exact two archives from its cold stage.
- `native`: restore read-only, link only the native boundary suite and fixture, and execute it on a
  new owned simulator. It avoids unrelated common test links and does not save caches. It is a
  diagnostic, never a replacement for full Verify.

Only the default branch may run this workflow. Pull-request Verify uses a restore-only composite
action and cannot save. GitHub's branch cache scope prevents fork/PR entries from replacing the
default-branch archives. Existing full cross-platform PR/release gates remain unchanged.

Gradle dependencies, wrapper distributions and task-output cache are archived separately from
Kotlin/Native's data directory. Both archive prefixes require the same machine architecture,
Kotlin version, Gradle wrapper, Xcode build and simulator/device SDK builds. There is no broader
fallback that crosses those compatibility boundaries. Each archive has an immutable source/run
suffix; later compatible consumers may use the newest matching archive. Project build directories,
test reports, configuration cache and credential settings are not archive paths. The Gradle
setup action explicitly delegates to this external cache, avoiding competing cache providers.

The `.konan` location is supplied through Kotlin's documented `konan.data.dir` property. Gradle
task-output caching is enabled explicitly for this iOS stage. The init script disables both result
caching and up-to-date skipping for every Gradle test task; compiled artifacts may be reused, test
bodies still execute. The real TLS/BASS suite also runs directly through the existing prebuilt
runner, validates its current binary hash and complete test inventory, and provisions fresh native
services. Xcode Keychain tests and the rest of Verify continue to run normally. Older public-source
recovery retains the prior read-only setup when that source predates these helpers.

## Evidence and acceptance

Each stage retains `build/ci-cache`: compatibility identity, exact restored keys, source SHA,
Gradle wall time, executed/cache-hit/up-to-date task counts, uncompressed cache file counts/bytes,
Gradle log/profile, and fresh native execution evidence. GitHub's step durations measure actual
restore/save overhead; the repository Actions cache inventory supplies compressed archive sizes.
Compare complete cold/warm jobs as well as compilation. Do not hide the previously measured
roughly 70-second save cost by reporting only compilation.

The implementation must first pass workflow validation, cache-key isolation tests, live test-policy
checks and full Verify. After merging the reviewed workflow, dispatch `benchmark` on `main`, retain
both hosted artifacts and cache inventory, and report the measured net benefit (or lack of one).
Until those runs complete, no hosted speed improvement or cache acceptance is claimed. Compiler
upgrades, beta native incremental compilation, and paid runner provisioning are outside this change.

November 9 local validation: actionlint 1.7.12 accepts both workflows; all 83 verification helper
tests pass outside the sandbox (three loopback fixture tests cannot bind inside it). Real Xcode
27.2/27B52 and SDK 24B8118 fingerprinting succeeds. Two consecutive cached JVM invocations each
execute all six `BassOutputStartupRecoveryTest` cases, with fresh XML timestamps and no failures;
the second keeps five inputs up to date while the test task executes. Inspection of Kotlin 2.3.21's
actual plugin hierarchy confirms `KotlinNativeSimulatorTest → KotlinNativeTest → KotlinTest →
AbstractTestTask`, so the same policy covers native Gradle test tasks. These checks establish the
policy and wiring locally; they do not replace hosted cache write/restore measurements.

References: [Kotlin native compilation guidance](https://kotlinlang.org/docs/native-improving-compilation-time.html),
[Gradle setup and external caching](https://github.com/gradle/actions/blob/main/docs/setup-gradle.md),
[GitHub cache scope](https://docs.github.com/en/actions/reference/workflows-and-actions/dependency-caching).
