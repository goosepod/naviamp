# Explicit pause intent during restart-on-seek (#346)

The combined v2.10.0 Windows native run at 84dc5d60 failed
`DesktopMutualTlsIntegrationTest.uncachedPlaybackUsesThePrivateServerAuthorityWithoutAClientIdentity`
in [run 40386367739](https://github.com/goosepod/naviamp/actions/runs/40386367739), job 121798786422.
Retained HTML reports `Waiting for Paused`, states
`[Loading, Playing, Paused, Playing, Loading, Playing, Playing]`, successful authenticated
bytes/ranges, native Playing and no BASS error. Authentication and initial streaming succeeded;
this is not classified as an authentication/setup failure.

The shared playback adapter inferred pause preservation from the latest reported state and
paused a restarted request only once. A late Playing report could undo explicit pause in Core
before seek, or after the one-shot startup pause. A new common regression injects that ordering;
it fails on the unchanged implementation at the first Paused assertion (actual Playing).

The adapter now records explicit pause intent independently, uses it when restarting on seek,
and reasserts native pause for late Playing reports until explicit resume or a new play clears it.
Stop clears it, and recovery starts preserve the current intent. The completed-request guard,
provider request generation, native playback cadence, timeouts and test assertions are unchanged.
All policy is in common code; no platform production source, setting or schema changed.

The regression covers late native Playing before/after paused seek, retained seek position,
explicit resume, and a new explicit play after pausing. All 73 adapter/controller cases and all five native Mac TLS cases passed after the fix;
Android debug, iOS arm64 and iOS simulator arm64 compilation and architecture checks passed. Full updated-source Windows
native and combined release verification remain required; a passing synthetic regression alone
does not claim that every native race or the separate TV queue-end failure is resolved.
