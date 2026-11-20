# TLS, range, seek and transport recovery (#590)

This checkpoint stays local on `codex/590-apple-system-media`. It extends the existing shared
Android/iOS mTLS contract to the real tvOS provider/HTTP/BASS path. It does not add a second TV
transport implementation, relax certificate verification, install trust roots globally, or use
real accounts. The test settings mute output; an advancing native clock is not audibility proof.

## Common ownership and regression

`core:app` owns finite provider-backed random-access reads and their bounded block cache. New
common tests cover contradictory one-byte probe lengths, truncated/oversized transfer rejection,
partial-block discard, fresh-source recovery, backward reads, cached-data revocation and
cancellation remaining distinct from transport failure.

The probe-length test first fails on the previous implementation: a `bytes=0-0` response declaring
zero bytes still creates a random-access source. The common owner now accepts a declared probe
length only when it is one byte (or absent). Other responses retain the existing sequential
streaming fallback. Range metadata/body consistency follows
[HTTP partial-response semantics](https://www.rfc-editor.org/rfc/rfc9110.html#section-15.3.7).
The provider's `headOnly` argument means stop after its bounded GET prefix; it does not send wire
HEAD. No change to the HTTP method, retry policy, cache budget or TLS policy is made here.

The real TV contract then finds a second common defect: a range read fails, but BASS leaves its
decoder-format error behind. The shared engine classifies that stale native code as UnsupportedFormat,
so the adapter requests a transcode and the faulty Original path can appear recovered. A new Domain
test reproduces this before the fix. The engine now preserves a known random-access transport failure
with no codec-failure reason; genuine native format failures retain their UnsupportedFormat reason
and existing transcode recovery. No retry is added or removed. The known transport failure no
longer enters a recovery branch intended for a different failure class.

`core:testkit` owns the integration assertions and engine/observer lifetime. The same contract:

- Rejects missing/untrusted client identities, wrong PKCS#12 passwords, untrusted server roots
  and hostname mismatches, with valid mTLS and private-CA positive controls.
- Browses and reads artwork through the real provider, then starts uncached native playback,
  pauses, seeks to 90 seconds and resumes. It checks authenticated client identities and a real
  upstream range beyond 2 MB, with a bounded transfer instead of a complete-file preload.
- Injects unavailable (503), truncated and inconsistent `Content-Range` responses. Each must
  surface an error rather than Playing or Finished. Each restored endpoint is explicitly replayed
  through a fresh playback lease, seeks forward to 90 seconds and backward to 5 seconds, and
  resumes an advancing clock. This is explicit retry recovery, not a new automatic retry policy.
- Requires every injected fault counter to be observed and all mutual-TLS HTTP requests to retain
  the expected authenticated certificate identity.

The JVM test fixture owns generated, short-lived certificate chains and loopback-only listeners.
Its mode-control endpoint requires this run's private stop token. Body faults spare the one-byte
GET probe so tests exercise ranged file callbacks rather than only capability negotiation.
Fixture regressions verify the good probe, deliberately bad response, exact healthy range after
restoration, fault counters and rejection of another run's control token.

## Thin native test effect and execution

`platforms/tvos/src/tvosTransportTest/.../TvosMutualTlsPlaybackIntegrationTest.kt` supplies only
Foundation private-file IO, native callback delivery/main-run-loop pumping and the existing TV
BASS factory. The event-loop lifetime/result owner stays in common testkit. It contains no
provider, range, retry or recovery behavior. No new platform production source is changed.

The extra native case is opt-in with `naviamp.tvos.transportTests=true` and requires the full
reviewed scene graph. Normal direct-BASS test inventory remains four cases; the transport binary
must contain exactly those four plus the shared mTLS case. Testkit TV targets are enabled only
with the existing scene flag. Original iOS/Android/JVM targets retain their normal coordinates.

That five-case inventory describes this checkpoint's retained evidence. The subsequent
[audio recovery checkpoint](590-tvos-audio-recovery.md) expands the current opt-in runner to eight
cases and additionally requires interruption/route recovery markers. Running the reproduction
command below against current source therefore executes that expanded gate.

`scripts/run-tvos-transport-tests.py` verifies the installed Compose inventory, generates an
isolated current-source consumer, checks the complete target graph and compiles both TV shared
targets. It builds the simulator test binary and standalone JVM fixture before allocating or
booting a simulator. It checks the public BASS inventory and absence of BASSmix, provisions a
fresh fixture, verifies readiness before and after boot, and executes the prebuilt native binary.
The prebuilt runner retains strict inventory, duplicate/failure/skip checks and binary identity;
its iOS defaults remain unchanged. TV additionally requires all three recovery completion markers.

The runner saves source hashes, graph, build/fixture/native logs, validated native JUnit/HTML,
timings, authenticated fixture counters and cleanup outcomes. Counter capture verifies this run's
bootstrap identity and permits only the documented telemetry fields. The complete snapshot records every source input. Generated
certificate bootstrap files are private local test material, not settings exports. Signal/timeout
cleanup terminates only captured owned compiler trees, stops the fixture and shuts down/deletes
the newly allocated simulator. Earlier failures/interrupted builds remain separate evidence.

Reproduction with the already reviewed local bundle:

```sh
export ANDROID_HOME=/absolute/Android/sdk
export GRADLE_USER_HOME=/absolute/dedicated/app-gradle
export KONAN_DATA_DIR=/absolute/compatible/app-native
python3 scripts/run-tvos-transport-tests.py --artifacts /absolute/verified/compose-artifacts \
  --expected-manifest-sha256 970fd6056b5169f65d6477ed365556f0a0dc02791b3d39e2c6f102ab4912d746 \
  --runtime com.apple.CoreSimulator.SimRuntime.tvOS-27-2 --build-timeout 7200 \
  --output build/tvos-transport-new
```

Use an ARM64 Mac, JDK 21 and the installed runtime. Every evidence/consumer destination must be
new. The producer is not invoked by this command. `--build-timeout` permits 60–7200 seconds;
changing a budget does not waive source, inventory, fixture, native-result or cleanup checks.

## Evidence and limits

Ignored local evidence is under `build/590-transport-1117`. Before TV wiring, the common change
passes 301 app, 9 TLS-testkit and 16 shared-testkit JVM cases, with no failures/errors/skips, and
compiles the common app/contract for Android and iOS Simulator. `common-before.xml` preserves
the expected initial regression failure; `common-originals.log` records the passing original-target
checks. The fixture's refined probe/body fault coverage passes again in `fixture-final.log`.

The first TV graph attempt fails on early source-set initialization, before allocating a device;
explicit hierarchy initialization corrects it. The second build is deliberately interrupted before
simulator allocation to correct the fixture's GET-probe treatment. Neither is a passing native
result. Their summaries/logs remain in `tvos` and `tvos-v2`.

The final 71-case TV tooling suite and eight existing iOS native-runner regressions pass, including
mandatory recovery evidence and preservation of the original iOS inventory requirement. The
initial sandboxed process-inspection attempt is not a pass; it is repeated with native `ps` access.

The first completed native attempt (`tvos-v3`) passes the existing BASS cases, real TLS controls,
initial seek, unavailable-server recovery and truncated-body recovery, then fails on the range/codec
classification defect. Fixture/shutdown/delete all return zero. The failed native log/execution
record and the common reproduction (`classification-before.xml`) are retained. After the common
fix, all 1,505 JVM cases pass (Domain 1,179; App 301; TLS testkit 9; shared testkit 16), with Android
and iOS compilation recorded in `classification-originals.log`.

The final `tvos-v4/summary.json` passes on tvOS Simulator 27.2: exactly five native cases, zero
failures/errors/skips, graph 20.966 seconds, build 194.065 seconds and native execution 22.907 seconds.
Both TV device and simulator common sources compile; the executable links the public BASS inventory
without BASSmix. All three recovery markers execute. `transport-state.json` records three 503
responses, fourteen truncated bodies and fourteen inconsistent ranges, with all mutual-TLS requests
authenticated as `CN=Naviamp Test Client`. Real ranged requests include `2752512-2883583` and later
blocks, and the initial transfer bound is asserted before fault/recovery traffic. Total traffic across
the entire suite is not an initial-playback preload measurement. Fixture stop, simulator shutdown
and deletion return zero with no cleanup errors; the device is `448EE04A-E485-4BC0-B25D-651D1DF85592`.

The original iOS native suite is rebuilt from the same immutable consumer and passes all nineteen
required cases on iOS Simulator 26.5, with zero failures/errors/skips and all three recovery markers.
`ios-original/summary.json` records the 622.836-second build, 101.329-second fresh-device boot and
successful fixture/shutdown/delete cleanup. Its native execution record, inventory, JUnit/HTML and
logs remain alongside the TV evidence. This confirms the extended shared contract and classification
fix through the existing iOS host as well as TV; no iOS production wiring changes are needed.

`checkpoint-summary.json` checks that the eight watched native-test/production/runner inputs still
match the compiled snapshot and records the 1,505 passing JVM cases. `final-resource-audit.json`
independently verifies all ten owned delivery/hosted-preparation/transport simulators are absent,
all three completed transport fixtures have stopped, and no checkpoint runner/fixture process
remains. Unrelated simulators and processes are untouched.

The local hosted workflow drafts now require this transport suite after the full app scene and
before producer candidate upload. The independent consumer uses the same approved manifest/runtime
and disposable app caches. Separate always-run cleanup covers each owned simulator. Evidence uploads
exclude the generated consumer and private fixture bootstrap. Workflow structural regressions and
`actionlint` pass. No hosted workflow is dispatched and the artifact trust pin remains pending.

This transport checkpoint does not close
GUI certificate selection/settings/reset recovery, interruption/route/accessibility, older-runtime,
hosted artifact promotion/CI, signing/distribution or release gates. Physical TV/HDMI/Siri Remote
checks remain unverified optional follow-up under the owner's simulator acceptance decision.
