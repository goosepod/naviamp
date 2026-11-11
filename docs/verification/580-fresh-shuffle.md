# Fresh shuffle orders (#580)

The reported original/shuffled/original alternation is the existing on/off contract in
`PlaybackQueue.toggleUpcomingShuffle`. Enabling already used Kotlin's default random shuffle;
there is no fixed seed or cached permutation. Exact repeated shuffled orders were possible by
chance, as was selecting the unchanged order. The owner confirmed that on/off should remain and
that enabling should produce fresh orders, with small-queue limitations accepted.

The common queue mutation owner now retains one `UpcomingShufflePolicy` across toggles. Each
enable draws a full random permutation, rejecting the original order and immediately previous
shuffled order. Eight unsuccessful draws use a bounded swap fallback. When fewer than three
distinguishable permutations exist, repetition is allowed rather than losing tracks or looping.
This is session behavior, not a new setting or a persisted schema change.

Tests cover adversarial random sources that repeatedly return the unchanged order or the same
permutation, duplicate occurrences, empty/singleton/identical/two-track inputs, and 30 seeded
100-track shuffles with broad variation. Queue-manager and application-coordinator regressions
exercise enabling, restoring and re-enabling, including pinned current/history/Play Next tracks.
No platform production files or UI copy change. Native and Connect on/off entry points retain
their shared toggle and selected-state contracts.

## Validation

Baseline `PlaybackQueueTest` passed before the change. The final common-domain checks pass:
1,130 JVM tests and 1,130 Android unit tests, no failures or skips; Android compilation, both
common iOS target compilations, and `verifyCoreFirstArchitecture` also pass. All 15 application
coordinator tests and the focused playback controller, native bridge, and Connect presentation
tests pass, including idempotent set-shuffle and fresh off/on behavior. Reproduction:

```powershell
.\gradlew.bat :core:domain:jvmTest :core:domain:testDebugUnitTest `
  :core:domain:compileKotlinIosArm64 :core:domain:compileKotlinIosSimulatorArm64 `
  verifyCoreFirstArchitecture --console=plain
.\gradlew.bat :core:app:jvmTest --tests '*NaviampPlaybackQueueCoordinatorTest' --console=plain
.\gradlew.bat :core:presentation:jvmTest --tests '*NaviampCorePlaybackControllerTest' `
  --tests '*NaviampCoreExternalPlaybackBridgeTest' `
  --tests '*NaviampCoreConnectRemoteNowPlayingTest' --console=plain
```

These are shared behavior tests and compilation checks; they do not claim touch-driven or audible
native playback verification. Continuous animation behavior is unchanged.
