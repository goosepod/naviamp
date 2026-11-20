# tvOS renderer deployment and build inputs (#590)

November 15, 2026. This advances the pinned stable source-port experiment. It does not enable
the full Naviamp tvOS host or announce a supported minimum. Android minimum remains API 26;
the original iOS app minimum is unchanged. Native audio progress is recorded separately in
[the Apple/BASS report](590-apple-bass-integration.md).

## Findings and selected compatibility probe

The existing renderer host declared tvOS 17.0, but its source-produced UIKit helper archives
actually contained **27 objects per target, all declaring minimum 27.2**. The producer's Xcode
invocation omitted `TVOS_DEPLOYMENT_TARGET`, so the tvOS SDK supplied that default. Both device
and simulator archives fail the new 17.0 deployment audit for that exact reason. Successful
linking/rendering on 27.2 never established older-runtime compatibility.

An explicit 17.0 rebuild exposed a real availability warning: `CMPAccessibilityElement` calls
`accessibilityHitTest:withEvent:`, introduced in **tvOS 18.0**, without a tvOS availability guard.
Existing `UIFocusEnvironment` conformance warnings also remain in this port. Do not treat a
lower plist or a successful compile with warnings as complete remote/accessibility support.

Official Skiko **0.150.2** was audited directly from both cached Maven KLIBs. Each contains
**22 native archives / 3,932 ARM64 objects**, with the correct device/simulator platform.
Only two entries per target exceed the 17.0 probe: `libicu.icudtl_dat.o` appears in both
`libicu.a` and `libskunicode_icu.a`, tagged **18.5**. Those duplicate objects are byte-identical
within each target, have no undefined symbols and have zero executable text bytes; their
constant section is 6,296,800 bytes. This distinguishes data metadata from a demonstrated use
of a new OS function, but does not establish older compatibility. All vendor bytes were preserved.
All 22 archives on each target pass the complete 18.5 metadata audit.

The next UI compatibility probe therefore uses **18.5** as a conservative candidate floor,
covering both the actual accessibility API and vendor annotations. This is distinct from the
direct-BASS-only probe and remains provisional until an appropriate older runtime and physical
device can be exercised. It does not raise Android or iOS requirements.

Input KLIB SHA-256 values:

- `skiko-tvosArm64Main-0.150.2.klib`:
  `8f71870e65d0cab31b4665281bca630a898c6ca5c0a59659264bc88c3a2f5ba6`.
- `skiko-tvosSimulatorArm64Main-0.150.2.klib`:
  `073639daaf89c17dde8256d7e100baba5de2349f8960a9d946d7326d5e73a07a`.

## Reproducible producer change

`scripts/probe-compose-tvos-deployment.init.gradle` applies only to the reviewed external
`:compose:ui:ui-uikit` producer. It requires an explicit numeric
`naviamp.tvos.probeDeploymentTarget`, validates the two real Xcode invocations and adds the tvOS
deployment flag. It changes no iOS invocation and refuses an ordinary Naviamp project.

Both the native archive task and consuming Kotlin compilation declare the deployment value as
an input. The Kotlin task also tracks the actual static archive's bytes. A changed target
therefore rebuilds the KLIB's included native code; it cannot leave a stale 27.2 archive inside
an otherwise up-to-date KLIB. The 17.0 → 18.5 repeat verified that behavior.

Retain the pinned producer: Kotlin **2.3.20**, Gradle **9.5.0**, JDK 21, 6 GiB/two workers,
SDK 27.2, isolated Gradle/project/output directories and its own `KONAN_DATA_DIR`. Do not mix
that with the Kotlin 2.4.21 consumer cache. Add these options to the source-producer command in
[the upgrade checkpoint](590-supported-upgrade-checkpoint.md):

```text
--no-configure-on-demand --no-configuration-cache
--init-script /absolute/naviamp/scripts/probe-compose-tvos-deployment.init.gradle
-Pnaviamp.tvos.probeDeploymentTarget=18.5
:compose:ui:ui-uikit:compileKotlinTvosArm64
:compose:ui:ui-uikit:compileKotlinTvosSimulatorArm64
```

The helper-only 18.5 rebuild passed in **13s**, 51 tasks / five executed. Native archives and
their embedded KLIB copies match exactly; all 27 objects in each declare 18.5. The original
iOS device/simulator archives remained byte-identical:
`9eeba9fb1b4adbcf7e8c1c323f371859382628ec8b9c9390becdef0b18babcca` /
`803568ffb7440a37bacbe5f01ff54bc07548485bef7f6fe807c3adbdf43686cb`.
This is a targeted rebuild, not an optimized application benchmark.

The reviewed private repository was retained. A separate owned copy,
`build/590-compose-port-repository-18_5` on the Mac, received only UIKit publications through
the existing local guard. The complete graph verified **48 exact-version private publications**;
staging took **14s**, 89 tasks / 13 executed. Every changed repository file is under `ui-uikit`.
An initial missing-script path and an on-demand partial-graph attempt failed before accepted
staging. The guard correctly rejected the partial graph; both logs remain evidence. Always
configure the full graph for publication verification.

This remains local `app.naviamp.tvosprobe` experimentation, not a public Maven release.
Production delivery needs an immutable identity containing source overlays, producer/compiler/
plugin versions, SDKs, native target, deployment floor and content hashes. Normal app builds
consume validated artifacts; they must not rebuild AndroidX. Trusted automated artifact
production/distribution remains outstanding.

## Consumer and actual renderer

A separate `build/590-tvos-renderer-deployment-consumer` uses the new repository and fresh
Gradle/Native caches. It retains Kotlin **2.4.21**, Gradle **9.9.1**, Compose **1.12.2**,
Material3 **1.9.0**, PMCS, four GiB/two workers and the pinned prebuilt Compose Gradle plugin.
Both tvOS frameworks and the original iOS simulator framework compile/link. The first invocation
took **6m05s**, including fresh tools/dependencies and three Debug frameworks. The unchanged
warm repeat took **1.750s** with all three links up to date. These are not comparable full-app
Release benchmarks or a general speed claim.

The graph check verified **91 iOS components without private artifacts** and **96 tvOS components
with exact-version private Native leaves**. This fixture retains Compose's lifecycle transitives;
it does not establish the full Naviamp TV graph with the app's lifecycle 2.11.0 pins.
Each final tvOS framework contains **3,960 audited ARM64 objects**, all on the correct platform
and at or below minimum 18.5. This checks the consumed artifact, not just an unused producer output.

`scripts/probe-tvos-renderer-runtime.py` ran actual `syncComposeResourcesForIos`, linked a scene
host with minimum 18.5, signed/installed/launched it on **tvOS 27.2**, verified its process stayed
alive and captured a screenshot. Final linking had no newer-deployment-object warning. Gradle
copied the `.cvr` strings bundle. The **3840×2160** image was inspected: resource-backed title/
details, Material3 slider, button and checked checkbox are painted. This is a shared synthetic
fixture, not Naviamp's full application or remote/accessibility acceptance. Owned simulator
shutdown/delete both returned 0; the existing user simulator was untouched. Framework SHA-256:
`a7feb9039078c225e0ebe6e529e9b1eb7d7d7c4487940c8b5cd5875e63a39b54`.

```sh
python3 scripts/probe-tvos-renderer-runtime.py \
  --consumer /absolute/owned/renderer-consumer \
  --minimum-os 18.5 --output /absolute/owned/fresh-evidence
```

The probe deliberately leaves `screenshotInspected=false` until the saved image is inspected.
A passing process/resource probe alone is not visible-rendering acceptance.

## Tools, evidence and remaining limits

`scripts/audit-tvos-native-deployment.py` checks **every ARM64 object**, rejects missing/conflicting
metadata, distinguishes device (3) from simulator (8), normalizes patch versions, preserves input
hashes and retains raw output. Six new parser/runtime-version regressions and six existing
feasibility cases pass; the real baseline/final audits also ran. The init script's negative
ordinary-project test passed. No application production source changed in this deployment step.

```sh
python3 scripts/audit-tvos-native-deployment.py \
  --archive /absolute/libCMPUIKitUtils.a --platform 8 \
  --maximum-os 18.5 --output /absolute/fresh-audit
```

Mac evidence: `build/590-deployment-inputs`, `build/590-helper-deployment-*`,
`build/590-skiko-deployment-audit`, `build/590-renderer-deployment-{tvosArm64,tvosSimulatorArm64}`,
`build/590-renderer-18_5-visible-host`. Early inspection assumptions about empty `__text` headers
and filtering inspection copies were corrected: the final audit uses each original exact
22-archive manifest and tests zero executable bytes. Local inspected image:
`build/590-feasibility/tvos-renderer-deployment-18_5.png`.

The installed catalog rejected both requested **18.5 ARM64-only and universal runtimes** as
unavailable (exit 70). No older-runtime UI launch is claimed. Apple documents the interface in
[additional Xcode components](https://developer.apple.com/documentation/xcode/downloading-and-installing-additional-xcode-components).
Use an appropriate older runtime/device before selecting a supported app minimum; do not alter
vendor metadata to manufacture that evidence.

Remaining gates include the full shared application/provider/storage/UI closure, trusted artifact
delivery, physical remote/HDMI/accessibility, signing and Windows performance/output acceptance.
The latest Windows default-output initialization also failed **outside** the sandbox with BASS
error 3 and created no stream; a usable output endpoint is still needed.
