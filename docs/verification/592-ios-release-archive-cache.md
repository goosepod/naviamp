# iOS cold Release compilation measurements (#592)

Issue: https://github.com/goosepod/naviamp/issues/592

## Scope and preserved behavior

Measure the actual unsigned ARM64 Release archive, including its optimized framework link.
Keep the existing 6 GiB heap and ARM64, unsigned-signature, version/build, privacy-manifest and
ZIP validations. The initial export experiment holds the compiler fixed. The owner subsequently
authorized a separate compatible compiler/toolchain experiment. No experimental Release binary
caches or reduced optimization.
No platform production source changes are part of this investigation.

The owner released v2.11.2 while this work was in progress. Its tag was merged into the local
#592 branch. The four shared background/inset implementations already match the hotfix exactly;
the merge also brings version/build 2.11.2/63, release notes and regression tests. Existing Android
Auto work remains present. The release tag is unchanged.

## Initial local cold archive

Frozen source: `ca562b4cfcc18113e0b682763b23f3b674ddb589`, before the v2.11.2 integration.
Java 21.0.13.0.3, Kotlin 2.3.21, Gradle 8.14.5, Xcode 27.2 (27B52), ARM64,
device/simulator SDK build 24B8118. Dedicated initially empty Gradle and Kotlin/Native homes.

| Measurement | Seconds |
| --- | ---: |
| Whole archive command | 1775.650 |
| Xcode archive phase | 1772.813 |
| `:apps:ios:linkReleaseFrameworkIosArm64` | 1420.245 |
| Output validation | 0.177 |
| IPA packaging | 2.487 |

The optimized link executed and consumed approximately 80% of the command time. The archive
and all existing validators passed. This is a local baseline, not a hosted speedup claim. The
validated cold IPA was retained outside generated build outputs before cleaning those outputs.
The dependency-cache warm experiment used the same frozen source and clean generated outputs.
It was intentionally stopped after the framework had been observed recompiling, when the owner
selected cold compilation work. Its interrupted output is not a successful warm benchmark.

## Framework cache limitation

The installed Kotlin Gradle plugin marks `KotlinNativeLink` cacheable, but that annotation alone
does not determine actual eligibility. Its binary task configurator adds a `cacheIf` predicate
which returns false for `CompilerOutputKind.FRAMEWORK`. Both Debug and Release framework tasks
therefore remain ineligible for Gradle task-output caching. Do not override that predicate or claim
that warming device KLIBs avoids optimized framework linking.

Reproduce against the installed `kotlin-gradle-plugin-2.3.21-gradle813.jar`:

```shell
javap -p -c -classpath "$KOTLIN_PLUGIN_JAR" \
  'org.jetbrains.kotlin.gradle.targets.native.KotilnNativeConfigureBinariesSideEffectKt$createLinkTask$linkTask$1$2'
```

The `isSatisfiedBy` implementation compares the task's output kind with FRAMEWORK, returning
false for frameworks. During the warm archive, an independent thread sample also observed the
Kotlin/Native devirtualization optimizer running again. Sampling identifies active work; it is not
a replacement for the completed task timings.

Kotlin's [compilation-time guidance](https://kotlinlang.org/docs/native-improving-compilation-time.html)
describes experimental Release binary caches as a different mechanism with runtime optimization
tradeoffs. They are not enabled here.

## Reproducible measurement tooling

`scripts/ios-release-archive.py archive` invokes the real packaging script. A supported Gradle
task-completion BuildService records outcome and duration for every completed task. Dedicated
user-home init scripts apply to Xcode's nested invocation, including the existing policy that
test bodies always execute. Measurements require clean committed source and check it again after
the archive. Missing validation phases, a failed task, or absent optimized-link evidence cannot
be accepted as a successful benchmark.

The optional `--profile-phases` argument passes the compiler's supported `-Xprofile-phases` flag
through a dedicated Gradle user-home property into the nested native framework task. It records
backend phase timings without changing optimization or heap. Logs remain part of the report.

## Owner-selected cold compilation experiment

On November 13 the owner selected reducing cold compilation itself, rather than reusing a
previously built IPA. No prebuilt-package reuse is implemented. Existing trusted cache policy,
workflow permissions and release job configuration remain unchanged.

The original framework exports all of `core:storage`, even though Swift only implements the
three-method `StorageCredentialProtector` contract. Its generated header has 9,849 lines,
336 interface declarations and 79 protocol declarations. Those counts describe the original
export surface, not independent app capabilities.

The rejected prototype moved the identical interface, retaining its package and methods, into a
shared `core:storage-api` module. Storage exposed that dependency through `api`, preserving the
Kotlin contract. The framework exported only `storage-api`; storage remained an implementation
dependency. Swift source and native Keychain behavior were unchanged.

Kotlin's [binary export documentation](https://kotlinlang.org/docs/multiplatform/multiplatform-build-native-binaries.html#export-dependencies-to-binaries)
explains that exporting a dependency retains its API and limits dead-code elimination, increasing
compile time and binary size. This motivated measuring the narrower surface. The completed
experiment below rejected it. Release optimization remained enabled. No platform production
Kotlin/Swift files changed; build metadata controlled the Objective-C framework export boundary.

The prototype's generated header had 202 lines, 19 interface declarations and four protocol
declarations. Its application initializer and all three credential methods retained their Swift
names and signatures; storage/query implementation types were absent.

Acceptance required shared contract compilation for Android/JVM/device/simulator, credential
regressions, a validated fresh Release IPA with the unchanged Swift host, API/binary inspection,
and matched phase-profiled archives. These checks ran locally before the prototype was rejected.

Local shared verification passed all four compilation targets, the architecture guard and nine
`StorageMediaSourceStoreTest` cases, with zero failures/errors/skips. The first fresh candidate
archive failed during SQLDelight dependency resolution because Maven's hostname could not be
resolved. It never reached the optimized link and is excluded from performance results.

For the controlled cold-compiler experiment, seed both isolated homes with the same already
downloaded `modules-2`, wrapper and Kotlin/Native distribution/dependencies. Do not transfer
Gradle task-output caches or project outputs. `--cold-compile` disables Gradle output caching for
the nested Xcode invocation, so compilation still executes from fresh source outputs. This measures
cold compilation with downloaded tools/dependencies; it is not an empty-download-cache benchmark.
Run the two archives sequentially on the same host to avoid simultaneous compiler contention.

The local comparison helper accepts only that single build-file export difference and matching
compiler/SDK/heap/profiling controls. It rejects cached optimized links, cached task outputs,
failed archives and unrelated source changes. This produces local evidence, not a hosted result.

### Completed export experiment: rejected

Both profiled cold-compilation archives passed, with no task outputs restored. Control source
`7d31c5ebe242a763de55ad0821df56dc8fb821af`; narrowed source
`bfc5b4aef48e99d5e9537835fab5fe47e241477c`. The only source difference is the reviewed
framework export configuration. Dependencies were already downloaded; source outputs were fresh.

| Measurement | Full export | Narrow export |
| --- | ---: | ---: |
| Whole archive, seconds | 1531.002 | 2005.678 |
| Release framework link, seconds | 1264.184 | 1797.168 |
| LLVM module optimization, seconds | 153.753 | 611.433 |
| LLVM LTO, seconds | 364.179 | 459.979 |
| Static-initializer optimization, seconds | 125.756 | 177.426 |
| Static framework bytes | 223444608 | 213990064 |

The narrower export was 42.2% slower in the measured link and 31.0% slower overall. Smaller
headers/binaries did not translate into faster compilation. The production candidate therefore
restores the original storage contract location and full export, removing the extra shared module.
Keep the frozen snapshots and evidence; do not ship this as a build-speed improvement. Detailed
phase profiling has overhead; these matched profiled runs are not absolute production-build claims.

### Authorized toolchain probe

The owner selected testing a compatible compiler/toolchain upgrade under #592 on November 13.
The initial isolated probe uses Kotlin 2.4.21, R8 9.1.57, the existing AGP 8.13.2 and Gradle 8.14.5.
R8 9.1.57 is a published stable artifact meeting Android's Kotlin 2.4 minimum requirement.
The old compiler's `KonanConfig.getDefaultGC()` was inspected and returns
`PARALLEL_MARK_CONCURRENT_SWEEP`. Pin `kotlin.native.binary.gc=pmcs` in the probe to preserve
that policy: Kotlin 2.4 changes the default to CMS. The heap remains 6 GiB.

The probe passed shared credential-contract compilation for JVM, Android and both iOS targets,
shared UI JVM compilation, architecture verification and credential-store regressions. A tiny
optimized ARM64 framework also built, prefetching the new LLVM dependencies outside product
measurements. Clean product outputs again before measuring an actual Release archive.
The first upgrade archive retains the frozen narrow-export experiment for compiler investigation;
it is not the final production configuration. A full-export upgrade measurement and quieter
confirmation remain necessary before accepting a cold-build improvement.

Use Kotlin's supported file build reports for confirmation runs; verbose `-Xprofile-phases` emits
hundreds of thousands of lines and is reserved for phase investigation. Both runtime policy and
optimization must match, and Android release shrinking/native/runtime checks must pass before
adopting a cross-platform toolchain change.

References: [Kotlin 2.4 native changes](https://kotlinlang.org/docs/whatsnew24.html#kotlin-native),
[Android Kotlin/R8 compatibility](https://developer.android.com/build/kotlin-support),
[Kotlin build reports](https://kotlinlang.org/docs/gradle-compilation-and-caches.html#build-reports).
KT-84412's public issue metadata lists 2.3.20-RC as a fix version, so 2.3.21 already contains it;
do not cite that regression as a missing fix addressed by this upgrade.

### Full-export compiler trials

The full-export 2.4.21 trial spent longer than the old control in the optimized link and was
intentionally interrupted. It has no validated archive and is excluded from completed timing
comparisons. The public [KT-85168](https://youtrack.jetbrains.com/issue/KT-85168) metadata places
the static-initializer IR refactor in 2.4.20-Beta1. This suggests testing the preceding stable
2.4.10 compiler; it does not establish the cause of the observed slowdown.

The full-export 2.4.10 trial passed compilation of shared storage for JVM, Android, iOS device and
simulator, shared UI JVM compilation, nine credential-store tests and architecture verification.
Its actual cold Release archive passed all existing validators and compiled the unchanged Swift
host. The archive took 764.419 seconds, including 624.838 seconds for the optimized framework
link. No task outputs were restored. The original 9,849-line export surface remains intact.

The matching 2.3.21 control completed with identical PMCS, 6 GiB heap, R8 9.1.57, SDK,
toolchain paths, fresh source outputs and quiet file reports. Frozen source
`cf08475259d0ff43d85893fbb5fa035b9750b555` and candidate
`1ea72bdad1de1865d5d1426e6c73e6f59678e459` differ only in the Kotlin version catalog entry.
Both archives passed every packaging validator. The strict comparison helper accepted the pair.

| Measurement | Kotlin 2.3.21 | Kotlin 2.4.10 | Reduction |
| --- | ---: | ---: | ---: |
| Whole archive, seconds | 1006.342 | 764.419 | 24.0% |
| Optimized framework link, seconds | 866.882 | 624.838 | 27.9% |

This is one sequential local pair with downloaded dependencies, not a hosted whole-job result.
Normal host background activity was not controlled. The older verbose-profiled export trials
must not be substituted for this quiet control to inflate the compiler improvement.

The same candidate passed 2,947 shared UI, storage, domain, presentation, Navidrome and Jellyfin
JVM tests on Windows, with zero failures, errors or skipped tests, plus the architecture guard.
Local tooling verification passed all 117 script tests. Windows desktop native tests reported
80 cases: 73 passed and seven existing platform/environment skips (two Linux MPRIS tests, one
optional authenticated server test, and four requiring real audio output).

Android release APK/AAB, JNI preservation, native symbols and optimization checks passed, as did
all seven Debug and seven Release unit tests. The mapping confirms R8 9.1.57, and release DEX
size is 12,387,048 bytes against the existing 20 MB limit. AGP's bundled lint analyzer reported
incompatible Kotlin metadata despite a successful task exit; that run is not accepted as valid
lint coverage. A separate full Android release lint run passed with lint 9.5.1, whose published
analyzer contains Kotlin 2.4.20-dev-6724-for-lint. Pin that stable lint release through Google's
[documented version override](https://googlesamples.github.io/android-custom-lint-rules/usage/newer-lint.md.html)
without disabling checks or changing AGP. Its report confirms lint 9.5.1, zero errors and zero
metadata compatibility errors. The 17 warnings include a new compile-SDK update advisory; SDK 36
remains unchanged.

From a clean checkout using one consistent Kotlin/Native installation, all 2,579 shared iOS
simulator tests passed with zero failures, errors or skips. Device compilation also passed. The
fresh native replay executed all 18 playback/TLS cases with zero failures, errors or skips and
cleaned its owned simulator and fixture resources. No assertions or test selection changed.
An earlier local test attempt relocated the native installation while retaining generated
references to its old path; duplicate platform libraries caused compilation to fail before tests.
The clean-checkout rerun resolved that setup error without relaxing library validation.

### Final hosted confirmation

At final readiness, push the completed branch once and open its #592-linked PR for full Verify.
Keep an ancestor checkpoint with the same code, R8, lint, GC and measurement tools but Kotlin
2.3.21; the candidate commit changes only the Kotlin version to 2.4.10. Dispatch the existing
`ios-cache.yml` workflow on the candidate with mode `cold-compiler-benchmark` and that checkpoint
as `baseline_sha`. Both fresh hosted jobs build validated Release archives without restoring or
saving compiler caches. The collector checks the source delta, runner image and hardware,
workload flags and executed native compilation before comparing complete job durations.
Whole-job timing includes setup, dependency downloads, validation and uploads; queue wait is
excluded. Do not declare hosted improvement or close #592 before this evidence and full Verify
pass. No experimental cache or production release workflow changes are required.
