# iOS cold Release compilation measurements (#592)

Issue: https://github.com/goosepod/naviamp/issues/592

## Scope and preserved behavior

Measure the actual unsigned ARM64 Release archive, including its optimized framework link.
Keep the existing 6 GiB heap and ARM64, unsigned-signature, version/build, privacy-manifest and
ZIP validations. No compiler upgrade, experimental Release binary caches or reduced optimization.
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

The candidate moves the identical interface, retaining its package and methods, into the shared
`core:storage-api` module. Storage exposes that dependency through `api`, so Kotlin consumers
continue using the same contract. The iOS framework exports only `storage-api`; storage remains
an implementation dependency. Swift source and native Keychain behavior are unchanged.

Kotlin's [binary export documentation](https://kotlinlang.org/docs/multiplatform/multiplatform-build-native-binaries.html#export-dependencies-to-binaries)
explains that exporting a dependency retains its API and limits dead-code elimination, increasing
compile time and binary size. Narrowing this surface is a measured candidate, not yet a claimed
speedup. Release optimization remains enabled. No platform production Kotlin/Swift files change;
the iOS build metadata controls the Objective-C framework export boundary.

Before accepting this candidate, compile the shared contract for Android/JVM/device/simulator,
run existing credential-persistence regressions, build and validate a fresh Release IPA, confirm
the unchanged Swift host compiles, inspect the generated API and binary size, and run matched
phase-profiled baseline/candidate archives. Keep iterations local until ready for final CI.

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
