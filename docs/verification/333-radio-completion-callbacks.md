# Radio completion callback ordering (#333)

TV acceptance of candidate `a3776547` stopped at the end of an eleven-track Sonic queue with
Autoplay enabled and batch ten. An independent diagnostic returned twenty matches. That diagnostic
does not establish unique eligible continuation candidates or prove why the earlier request stopped.
Windows comparisons with other seeds and Autoplay twenty succeeded for Instant Mix and Sonic.

Investigation found a concrete shared adapter defect: after accepting Finished for one engine
execution, it still accepted late Playing/Loading, duplicate Finished, and teardown progress.
The shared playback controller cancels pending Autoplay when state leaves Finished. The new
synthetic engine regression reproduced `[Playing, Finished, Playing, Loading, Finished]` before
the fix. Seventy-two focused adapter/controller tests ran; only the new regression failed.

The adapter now latches completion per engine request. The latch uses a shared atomic boolean so
native end-sync and polling observe completion and duplicate completion competes atomically. Late
callbacks from that completed request are ignored. Starting a new play creates a new latch; explicit
Stop still invalidates the request and publishes Stopped. Provider/source, queue and settings guards
on pending Autoplay remain unchanged, as do candidate ranking, exclusions, timeouts and batch sizes.

The adapter/controller suite passes after the correction. Verification commands/results are recorded
with the pull request. No production platform files, settings, strings or migrations changed.

Final main-based verification of the atomic boolean passed all 72 adapter/controller cases with
zero failures/errors/skips, plus Android debug, iOS ARM64, iOS Simulator ARM64 compilation and
`verifyCoreFirstArchitecture`. The command ran `:core:presentation:jvmTest` with filters
`*NaviampCorePlaybackEngineAdapterTest` and `*NaviampCorePlaybackControllerTest`, together with
`:core:presentation:compileDebugKotlinAndroid`, `:core:presentation:compileKotlinIosArm64`,
`:core:presentation:compileKotlinIosSimulatorArm64` and architecture verification. Native KLIB
cross-compilation was enabled; no device was controlled.

This corrects the reproduced callback defect; it does not establish the original TV failure's root
cause. Physical TV retest remains pending because the owner paused device use. No TV commands,
installation or server playlist mutations are part of this fix. Proactive Radio top-up is not
implemented by the current queue-end Autoplay design and is not asserted as passing here.

## Current-source physical TV retest — November 7, 2026

Accepted main `ac1e407dea6ea5624f6b8ba2b03faacc84e49c3a` is retested on the owner's onn 4K Pro,
Android 14, armeabi-v7a. The TV is explicitly available again. An isolated debug host and its
instrumentation use `.tvverify20261107`; the existing `.v2test` installation/profile is untouched.
No production source or native adapter changes are needed for this retest.

The new opt-in `AndroidTvRadioContinuationInstrumentedTest` runs the actual shared application
and real Android BASS engine against a credential-free Sonic protocol fixture. Start
`scripts/android-tv-fixture.py --sonic --silent --tracks 60 --track-seconds 30`, reverse port
18080 only on the authorized device, and run this class with `-e tvLocalFixture true` in an
empty disposable installation. Without that explicit argument the hardware test is skipped.
The fixture's new Sonic behavior is opt-in; existing default responses are preserved.

The test starts seed-plus-ten Radio, advances to its last item, seeks to 23 seconds of the
30-second track, then waits for actual native completion rather than issuing Next at EOF.
Foreground completion extends 11 -> 21, starts index 11, preserves the original queue prefix
and contains only unique track IDs. Repeating with the launcher demonstrably foreground
extends 21 -> 31 and starts index 21 with the same retention/uniqueness checks. Native progress
advances at least one second on each appended current track. The complete test passes in
45.508 seconds, one executed case with no failure or skip. It pauses in `finally`; the isolated
host is subsequently force-stopped.

The fixture advertises Sonic v1 and returns twenty distinct, seed-excluding matches, with at
least ten eligible IDs after excluding the queue at both tested ends. These real HTTP protocol
checks and all eighteen existing media-fixture range/artwork tests pass. The ordinary Android
app, instrumentation and optimized benchmark builds plus architecture verification pass.

This is controlled-provider, real-TV native/lifecycle acceptance. It complements the earlier
live-library foreground/background results in `386-physical-tv-functional-2026-10-29.md`;
it does not claim those live-provider requests were repeated, subjective HDMI audio quality,
proactive top-up or proof of the exact original failure's cause. Hosted verification of the
new acceptance harness and its exact source tree is recorded on the linked PR.
