# tvOS remote, text entry and app recovery (#590)

This extends the [bounded storage/scene checkpoint](590-tvos-recovery-storage.md). All work stays
under #590 and draft PR 606. Android's runtime minimum remains API 26; the TV deployment candidate
remains 18.5. Normal builds still exclude the private Compose TV scene.

## Accepted CI baseline

Commit `c8aa7a8b9c63106c900f780c10a8fbecccb0eadb` passes the complete
[Verify matrix](https://github.com/goosepod/naviamp/actions/runs/43765830193),
[headless public-BASS TV lane](https://github.com/goosepod/naviamp/actions/runs/43765830109) and
[Flatpak package lane](https://github.com/goosepod/naviamp/actions/runs/43765830037).
Verify includes Windows/Linux/macOS native checks, original iOS and Android API 26/30/35.
The optional iOS fixture diagnostic was intentionally skipped. No failing baseline CI job needed repair.

## Shared remote fixes

The real scene test exposed two defects before the recovery sequence could be accepted:

1. Navigation consumed Down before Compose's default traversal could switch from touch mode to
   keyboard mode. The top navigation explicitly allows focus, while system-defined content cards
   can reject focus in touch mode. Down/Select therefore left focus on Home after restoration or
   native text entry. The shared `TelevisionNavigationButton` now requests keyboard input mode
   before handing focus to content. It does this for Down and activation; pointer interaction
   remains available. No Compose artifact or native responder workaround is introduced.
2. The port already translates Siri Remote Menu into Compose Back, but the scene had no connection
   between that key and Naviamp's shared back dispatcher. Shared TV back navigation now bubbles
   Back/Escape/Menu after focused controls to the deepest registered handler. One held press
   invokes one handler; repeats and the release cannot close another page. The standard surface
   keeps its existing host back integration.

Both changes live in `core:ui/commonMain`. Two rendered regressions cover touch-mode Down/Select
entry, and two cover nested Back handlers, a held press, release, focused-control precedence and
root behavior without a handler. Android and both original iOS targets compile the common code
before TV integration. There are **no changed platform production files** in this follow-up:
Xcode target/scheme metadata and native UI tests are test/build integration, while runtime policy
stays shared. No settings, schema, animation timing, sampling or renderer policy changed.

## App-level acceptance harness

`apps/tvos/NaviampTVUITests/TvosRemoteRecoveryTests.swift` drives the actual app entry point with
`XCUIRemote` and the native system keyboard. It does not seed SQL, preferences or Core state and
does not add test-only production routes. The separate `NaviampTVSceneTests` scheme keeps ordinary
native storage tests independent of the full scene fixture.

The test exercises provider Left/Right/Select, real URL/username/password entry, Next/Done,
keyboard dismissal with Menu and reopening with Select, scrolling/focus, Advanced-to-Aurral
checkbox traversal, companion entry, connection, Home-to-discovery navigation and nested Back.
It scrolls to and checks the hittable primary-library mix title, then returns to the companion
entry and opens the authenticated recommendation. Both providers are captured visibly.
Screenshots and accessibility trees are retained at each major stage and on failure.

`scripts/tvos-scene-fixture.py` binds only to 127.0.0.1:18790. Primary and companion use deliberately
different synthetic credentials and base paths. The main server verifies salted Subsonic password
tokens; the companion verifies its own password and bearer session. Swapped/missing credentials
are rejected. Five fixture regressions check these authentication gates, protected cover/image
requests, telemetry redaction and rejection of cache controls without an explicit owned device.
The fixture serves valid existing test artwork, so the runner
does not need to excuse unauthenticated cover fallbacks caused by an incomplete test response.
It never forwards traffic. No user account or production server is involved.

`scripts/run-tvos-scene-tests.py` requires a configured, reviewed consumer checkout and an explicit
installed tvOS runtime. It verifies the artifact manifest, creates a new owned simulator and builds
the UI-test scheme once with the existing two-worker/4 GiB-heap native build phase. One XCTest
session then runs these stages, explicitly terminating/relaunching the actual app between them:

1. Fresh main/Aurral setup entirely through remote/text UI, followed by visible library/discovery.
2. A new app process with the original cache intact; both accounts must authenticate again.
3. Terminated app, removal of **only** that owned container's `Library/Caches/Naviamp`, then a
   new app process. The encrypted preference record must remain byte-identical across deletion,
   have authenticated-cipher framing and contain neither synthetic username nor password in
   plaintext. The app must rebuild its database, authenticate both accounts and render both providers.

The runner clears route telemetry before each stage and rejects authentication failures. The
test calls a separate host fixture control after asserting the app has terminated; this probes
the actual container before each launch and removes only the owned cache for the purge stage.
Expected pre-launch database states are absent/present/absent, with the recovery record present
in both later stages. The final host probe verifies a rebuilt physical database before XCTest
can clean up its app installation. No production app exposes these fixture controls.

The runner requires exactly one non-skipped passing XCTest case covering all three stages,
verifies the reported device matches the newly created simulator, exports attachments, records
source hashes and cleans up its own simulator/helper in `finally`. Resolved container/cache paths
are validated against that simulator before deletion. Earlier requests cannot satisfy a later
stage. It does not alter pre-existing simulators or the Mac user's checkout.

From an already configured consumer checkout, with JDK/Android SDK and isolated Gradle/Konan cache
environment variables set as in the [artifact report](590-compose-artifact-delivery.md):

```sh
python3 scripts/run-tvos-scene-tests.py \
  --output /absolute/new/owned/evidence \
  --runtime com.apple.CoreSimulator.SimRuntime.tvOS-27-2 \
  --ui-repository /absolute/reviewed/ui-repository \
  --resources-repository /absolute/reviewed/resources-repository \
  --artifact-manifest /absolute/reviewed/compose-artifact-manifest.json \
  --expected-manifest-sha256 3f6d453c0de05b643ac94d14bbb296ddee9429df5308806baa9d1e2e21864193
```

This still depends on the separately reviewed private port delivery described in the artifact
report; it is not a fresh public-checkout build recipe. The runner never builds Compose sources.

## Evidence and limits

The final complete run is owned `build/590-scene-remote-acceptance-1115-v6`. All three stages pass
in one 93.275-second XCTest case, with zero failures/errors/skips. The build-for-testing step takes
31.577 seconds with warm dependency/compiler caches; the full test command takes 105.066 seconds.
These are test/build timings, not cold-machine or app runtime-performance claims.

| Verification | Result |
| --- | --- |
| Full shared JVM UI suite | 784 pass, zero failures/errors/skips |
| New shared focus/back regressions | All four pass, included above |
| Android common UI compilation and Core-first architecture check | Pass |
| Original iOS device/simulator common UI compilation | Both pass |
| Physical-tvOS target UI/host compilation | Pass; this is compilation, not physical runtime acceptance |
| Strict fixture authentication/artwork/ownership tests | Five pass |
| Actual remote/text/app recovery case on tvOS 27.2 | Pass, including all three stages and visible main/Aurral content |
| Pre-launch database state: create/relaunch/purged | Absent / present / absent |
| Pre-launch recovery document: create/relaunch/purged | Absent / present / present |
| Per-stage requests: create/relaunch/purged | 43 / 31 / 44; zero rejected authentication requests |
| Removed cache projection | Database, WAL and SHM only; 2,734-byte encrypted preference record preserved byte-identically |
| Final physical database and recovery record | Both present, checked before XCTest cleanup |
| Owned simulator shutdown/deletion | Both zero; pre-existing user simulator untouched |
| Tested source/artwork identities | All 17 LF-normalized source/raw artwork hashes match the working snapshot |
| TV framework export header | Unchanged at 15,641 bytes |

Windows evidence is `build/590-feasibility/tvos-scene-remote-{summary,telemetry,attachments}.json`,
`tvos-purged-{main-library,aurral}.png` (both visually inspected),
`tvos-remote-{common-v2,final-ui}.log` and the retained fixture-test output. Native compile evidence
is `build/590-remote-recovery-1115-v1/ios-remote-compile.log` and
`build/590-scene-remote-acceptance-1115-v1/device-compile.log`. The Mac user checkout is unchanged.

Diagnostic failures and attachments remain in owned `build/590-remote-recovery-1115-v1` and earlier
`590-scene-remote-acceptance-1115-v*` outputs; they are not counted as final passes.
The first setup probe incorrectly expected an exact artist label on Home; Home exposes a generated
mix instead. Discovery recommendations are in a lazy list and must be reached by remote scrolling.
Those selector corrections are distinct from the reproduced navigation and Back defects.
The earlier multi-session harness also assumed a stable app-container path. Xcode can delay app
installation until `launch()` and relocate its container across separate test sessions. The final
harness keeps the complete recovery flow inside one session, probes between actual app processes,
and validates the rebuilt database before test-manager cleanup. A passing UI stage with a failed
harness postcondition is not reported as complete acceptance.

A separate diagnostic held Menu for 1.2 seconds while expecting a one-page Back action. That
expectation is incorrect: [Apple defines held Back/Menu as a system Home gesture](https://support.apple.com/en-gb/102337).
The diagnostic checked the app hierarchy rather than its foreground/background state, so it does
not establish whether the system gesture succeeded. System Home exit/return remains **unaccepted**.
The reviewed native hosting controller also suppresses superclass press forwarding; include that
boundary in the lifecycle/port review rather than adding app navigation policy to Swift. The final
regression uses normal short Back. Shared repeated-key protection is not proof of system long-press behavior.

These tests do not establish physical Siri Remote/HDMI audibility, VoiceOver, older-runtime
compatibility, background/interruption handling or system media commands. Remote playback buttons,
authenticated range/TLS streaming, native lifecycle/media integration, document/certificate selection,
discovery/pairing and signing/distribution remain explicit host acceptance work under #590.
Component tests continue to cover certificate relocation, settings normalization/import rollback,
capacity/corruption/missing-key behavior and queue exclusion. This app sequence uses synthetic
password accounts over loopback HTTP and an initially empty playback queue; it must not be described
as GUI acceptance of every certificate transport or crash-durability scenario.

Original-platform animation/performance, resize and native accessibility gates also remain open.
PR 606 stays draft; passing the local scene sequence does not close issue 590.

## API references

- [Apple XCUIRemote](https://developer.apple.com/documentation/xcuiautomation/xcuiremote)
- [Apple system-keyboard typing](https://developer.apple.com/documentation/xcuiautomation/xcuielement/typetext(_:))
- [Apple focus interaction](https://developer.apple.com/documentation/uikit/adding-user-focusable-elements-to-a-tvos-app)
