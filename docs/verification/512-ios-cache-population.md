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

The `.konan` location is supplied through Kotlin's documented `konan.data.dir` property in the
isolated Gradle user home's `gradle.properties`, so Xcode's later Gradle invocations inherit it too.
That machine-specific file is recreated before restore and is not archived. Gradle
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

The first hosted PR run passes both-target compilation, common tests and fresh native execution,
then rejects the production framework link because it sees platform KLIBs from both the private
compiler directory and the default `~/.konan`. Only the initial command supplied `-Pkonan.data.dir`;
Xcode invoked Gradle again without it. Persisting the same property in the isolated Gradle home
fixes the cross-invocation path mismatch; keep the original failed log and rerun full Verify. Do not
enable duplicate-KLIB tolerance or mix compiler directories to silence the failure.

The corrected local suite passes 84 helper tests. A real Gradle 8.14.5 probe, using a temporary
home whose path contains spaces, reports the identical native directory both with the initial
explicit `-P` argument and through an independent `/bin/sh` invocation without that argument.

## Fresh hosted native invocation

PR #555 is accepted on main at `22a6feb6e`. Its automatic population run can compile and save
without native execution, but inspection of the first manual benchmark finds a separate setup
defect: `replay-ios-native-tls.py` requires an earlier native report. A new hosted benchmark has
compiled the binary but has no such report. The affected benchmark is cancelled before claiming
any native or warm-cache result; the automatic population run remains active.

The explicit `--fresh` mode executes the existing prebuilt runner on a new owned simulator.
That runner inventories the binary, checks the exact required source manifest, executes every
case, validates all events and rejects any old output directory. The replay wrapper additionally
requires the new XML, expected inventory and executed inventory to match. Subsequent suites must
match the first complete inventory. The existing default replay mode still requires and compares
its earlier successful suite; the change does not weaken the three-suite verification gate.

References: [Kotlin native compilation guidance](https://kotlinlang.org/docs/native-improving-compilation-time.html),
[Gradle setup and external caching](https://github.com/gradle/actions/blob/main/docs/setup-gradle.md),
[GitHub cache scope](https://docs.github.com/en/actions/reference/workflows-and-actions/dependency-caching).

## Accepted hosted cold/warm measurement — November 10

Follow-up #556 merges at `d281f2b0de3db72e0b3538a1a2eca4ac737c17db` after its exact-head full
matrix passes. Matched [benchmark 42858817970](https://github.com/goosepod/naviamp/actions/runs/42858817970)
then completes on two fresh hosted runners at that same source. Both compatibility identities are
ARM64, Kotlin 2.3.21, Xcode 26.6 build 17F113, simulator/device SDK 23F81a, runner image
macos26/20261102.0473.1 and the same Gradle wrapper digest.

| Measurement | Cold stage | Warm stage |
| --- | ---: | ---: |
| Complete benchmark job, excluding queue | 20m32s | 12m11s |
| Gradle compile/common-test workload | 977.827s | 331.104s |
| Native fixture/simulator provisioning and execution | 150.389s | 243.706s |
| Cache restore action | skipped restoration; 7s setup | 94s |
| Gradle/native archive saves | 38s + 17s | no save |
| Task outcomes | 153 executed; no cache hits | 105 executed; 39 from cache; 9 up to date |

Complete-job time falls by **8m21s (40.7%)** in this one matched pair, including the measured
save/restore cost. The Gradle stage falls by 66.1%; native provisioning is slower in the warm
sample and is included in the net result. Queue wait is excluded from both job durations. This is
not a repeated performance guarantee or a measured percentage for the entire production Verify
workflow: Xcode app/archive and Keychain work are not part of this benchmark workload.

The cold stage deliberately restores nothing. The warm stage requires and restores the exact
two cold-stage archives whose generation suffix is
`d281f2b0de3db72e0b3538a1a2eca4ac737c17db-42858817970`, with compatibility prefix
`naviamp-ios-v1-28efd75b2926e87235709b65`. Compressed archive sizes are **1,250,993,482 bytes**
for Gradle and **415,629,552 bytes** for Kotlin/Native (1.67 GB total). The recorded uncompressed
directories are approximately 2.37/2.40 GB Gradle and 2.18 GB native. No cross-toolchain fallback
or restored test-result directory is used.

Both stages execute the identical **2,512 common test cases across 342 reports**, with zero
failures/errors/skips, and a fresh **17-case native TLS/BASS suite**, also zero failures/errors/skips.
Every common test task with real reports executes instead of returning from cache/up-to-date;
the empty application-host test task remains explicitly skipped. Native executions carry fresh
inventories/results and their own verified executable hashes. The independently linked native
binaries have different hashes; no binary-identical comparison is claimed.

The complete hosted artifacts, exact restore records, compatibility JSON, task counts, native
execution records and Actions cache inventory establish the cache acceptance for #512. The
independent fixture reliability issue #454 remains open. Private audited copies are retained in
`/private/tmp/naviamp-cache-cold-20261110`, `/private/tmp/naviamp-cache-warm-20261110` and
`/private/tmp/naviamp-cache-inventory-20261110.json`.
