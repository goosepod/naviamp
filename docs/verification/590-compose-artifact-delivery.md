# Issue 590: private Compose artifact integrity checkpoint

The app must consume prebuilt, reviewed tvOS artifacts. Rebuilding AndroidX/Compose during an
ordinary app build is not acceptable. The separate producers retain their own compatible Kotlin
and Gradle versions; the app consumer remains Kotlin 2.4.21 / Gradle 9.9.1. Original Android,
Desktop and iOS resolution stays on the official coordinates.

## Implemented local gate

`scripts/tvos-compose-artifacts.py` seals and verifies the two owned probe repositories. It checks:

- Exactly 52 private publications: 48 UI/Foundation/animation/Material3/navigation publications,
  three resources publications and the separate prebuilt Compose Gradle plugin. Versions and
  `app.naviamp.tvosprobe` groups are explicit; original-platform leaf publications cannot enter.
- Actual Gradle module coordinates and tvOS device/simulator leaf attributes. Umbrella metadata
  can describe other targets; it is not evidence that those target leaves were published or used.
- Every module file reference, size and SHA-256 against the real artifact bytes.
- Every repository file, including Maven metadata, source JARs and checksum sidecars, against
  the sealed inventory. Missing, extra or changed files fail verification. Symlinks are rejected.
- A separately supplied manifest SHA-256 before trusting its inventory. A manifest downloaded
  beside arbitrary binaries does not establish their identity by itself.

The gate is read-only during verification. It never downloads/extracts artifacts, changes Gradle
resolution or publishes packages. It supplements the existing resolved-consumer graph check;
neither an inventory nor a passing fixture establishes full Naviamp target closure.

The actual corrected deployment probe repositories sealed successfully on November 15:
**1,476 files, 52 publications**, manifest SHA-256:

```text
3f6d453c0de05b643ac94d14bbb296ddee9429df5308806baa9d1e2e21864193
```

The immutable identity includes provenance describing the UI producer's official commit
`5f017375d926c73f1937408601b6cb28c12a8812`, reviewed tvOS port delta
`f29d2f99f3beafc60992c6a73dbbaa9f841c5d4c` → `6e266fe9e511c413e63d51011be1659c3696fee6`,
stable Material3 source overlay/formatter correction, producer Kotlin 2.3.20 / Gradle 9.5.0 and
the helper's corrected 18.5 deployment target. The resources producer identifies official commit
`ab304f2538657d9f7077bdb40797f5db618b4c84`, port delta
`d67175a3766c51caa673b220c74a4fb68a61fd31` → `9897904fab44c811affcd818b14b653bac0cf1ea`
and the separate Gradle plugin's Kotlin 2.2.0 / Gradle 9.5.0. These are documented local inputs,
not a claim that a clean hosted source-build service exists yet.

Example verification, using the identity above obtained from reviewed source:

```sh
python3 scripts/tvos-compose-artifacts.py verify \
  --ui-repository /absolute/owned/ui-repository \
  --resources-repository /absolute/owned/resources-repository \
  --manifest /absolute/owned/compose-artifact-manifest.json \
  --expected-sha256 3f6d453c0de05b643ac94d14bbb296ddee9429df5308806baa9d1e2e21864193
```

Sealing requires an explicit provenance JSON and a fresh output path. Do not automatically reseal
changed bytes to make verification pass: review the changed source/build/deployment inputs, rerun
the graph/native/rendering gates, then update the reviewed identity deliberately.

Seven regressions exercise complete inventory verification, missing publications, original-platform
leakage, wrong-target variants, external artifact references, changed bytes, and the separate trust
identity/extra-file rejection. The Windows test fixture uses an owned short build path because the
sandbox's long temporary prefix exceeded ordinary Windows path limits in the first attempt.

## Remaining delivery work

This checkpoint provides an integrity gate for already-built local inputs. It does **not** close
the reproducible producer/distribution gate. Before enabling the full host:

1. Automate clean source acquisition and exact reviewed patches/Material3 overlay with the
   producer commands and deployment init script recorded in the source/deployment reports.
2. Build with separate, bounded producer caches keyed by source patch, compiler, wrapper, SDK,
   architecture and deployment floor. Keep full producer builds out of normal app build graphs.
3. Preserve source/license notices and archive an immutable bundle, manifest, source-input hashes
   and producer logs. Use a trusted accepted-source writer; pull requests must not replace the
   shared artifact or cache. No public package/release was created at this checkpoint.
4. Pin the artifact identity in reviewed source, verify before repository use, and retain strict
   target-scoped mapping. Prove Android/JVM/iOS stay official in the complete app graph.
5. Rebuild/link the full tvOS app with matching Native caches. The original fixture's transitive
   Lifecycle 2.9.6 was not acceptance of the app's Lifecycle 2.11 graph. The separate follow-up
   below now covers 2.11 in that fixture, but full app closure and cold/warm measurements remain.

Local Mac evidence lives in `build/590-deployment-inputs/compose-artifact-{provenance,manifest}.json`.
The manifest records the exact binary inputs and is intentionally not checked in as a large
generated inventory. See the [deployment audit](590-tvos-deployment-audit.md) for every-object
minimum-OS inspection and visible renderer limits; 18.5 remains a compatibility candidate.

## Lifecycle 2.11 follow-up

A fresh owned copy of the renderer fixture added the app's actual
`org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.11.0` dependency after verifying
the artifact identity above. Strict resolution selected **official** Lifecycle 2.11 Native leaves,
including runtime/runtime-compose/common/viewmodel/savedstate on TV. No extra port or version
downgrade is needed for this dependency. The actual reports contain 95 iOS components with zero
private port artifacts and 96 TV components; every selected JetBrains Lifecycle component is 2.11.

Both TV frameworks and the original iOS simulator framework compiled/linked in 178.956 seconds
with the existing identity-compatible caches. The first fresh-directory attempt exposed a graph
reporter bug before compilation: it did not create its `build` output directory. The init script
now creates that directory; the successful repeat retains the failed initial log as evidence.

The rebuilt TV simulator fixture packages its `.cvr` resources through the actual Compose sync
task and visibly renders strings, Material3 slider, button and checked checkbox on **27.2**.
The retrieved **3840×2160** image was inspected; owned simulator cleanup returned 0/0.
Framework SHA-256: `86b4fead68974e19e501466c07036a74fcfb229036dfa76b505493213fd72a27`.
The 18.5 host declaration is still a candidate, not older-runtime acceptance.

Evidence: Mac `build/590-tvos-lifecycle-2_11-probe`,
`590-tvos-lifecycle-2_11{,-retry}.log`, `590-renderer-lifecycle-2_11-visible`; local
`build/590-feasibility/tvos-lifecycle-2_11{.png,-link-summary.json,-runtime-summary.json}`.
