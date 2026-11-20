# Reviewed tvOS producer source inputs (#590)

`sources.zip` is a source-only snapshot: complete binary Git patches, per-path hashes, upstream
licenses and the 16-file Naviamp settings-plugin source input. It contains no compiled Compose
artifacts. The separately reviewed SHA-256 is pinned in
[`prepare-tvos-compose-sources.py`](../../scripts/prepare-tvos-compose-sources.py):

```text
3eb79a0aab2314002db1694a7783aabfd67a5c6050ec048c8b1a25703db583cf
```

| Producer | Official base commit | Complete patched tree | Changed paths |
| --- | --- | --- | --- |
| UI | `5f017375d926c73f1937408601b6cb28c12a8812` | `1df1346d489a469031026a02c41ccc48553e8514` | 696 |
| Resources | `ab304f2538657d9f7077bdb40797f5db618b4c84` | `aa9b89dcd867364ab066cfe89894a93645160276` | 18 |

The UI input incorporates the reviewed port delta
`f29d2f99f3beafc60992c6a73dbbaa9f841c5d4c` → `6e266fe9e511c413e63d51011be1659c3696fee6`,
stable Material3 1.9.0 overlay and documented compile/format corrections. Resources incorporates
`d67175a3766c51caa673b220c74a4fb68a61fd31` → `9897904fab44c811affcd818b14b653bac0cf1ea`,
explicit local probe inputs and the Gradle 9 non-null extension-type bound.

The tvOS scene mediator consumes hardware Return and Siri Remote Select when a text field is
focused and opens UIKit’s editor after key release. Device Hub sends a hardware Return path that
previously reached Compose’s Next or Done handler before the system keyboard could open.
The mediator synthesizes held-button repeats only for remote presses (`UIPress.key == null`).
Physical keyboard presses retain UIKit's event stream so a missing release cannot leave a Compose
repeat job inserting characters into Search.

Official repositories: [Compose core](https://github.com/JetBrains/compose-multiplatform-core),
[Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform). Port sources:
[sajidalidev/compose-multiplatform-core](https://github.com/sajidalidev/compose-multiplatform-core),
[sajidalidev/compose-tvos](https://github.com/sajidalidev/compose-tvos).

From Naviamp's root, with Python 3.11+ and Git:

```sh
python3 scripts/prepare-tvos-compose-sources.py --destination build/tvos-compose-sources
```

The command verifies the bundle before network access, fetches exact official commits into new
directories, applies patches without whitespace rewriting, and checks complete Git trees,
changed-path inventories and file hashes. It reconstructs `ui`, `resources` and `settings-plugin`
and writes `source-replay.json`. It never runs Gradle, publishes artifacts, selects Naviamp
dependencies, modifies an existing checkout or deletes previous evidence.

Optional `--ui-reference /absolute/local/git/repository` and `--resources-reference ...` avoid
network acquisition. These share read-only Git objects; keep the reference repositories available
until the prepared checkout is retired. Both local-reference replay and fresh acquisition from
the two official HTTPS repositories passed against the actual bundle. The latter uses shallow
repositories with no shared object references; both complete patched trees match the pin.

The prepared resources library takes explicit Gradle inputs:

```text
-Pnaviamp.tvos.probePluginBuild=/absolute/prepared/settings-plugin
-Pnaviamp.tvos.probeRepository=/absolute/owned/ui-repository
```

The plugin input is a pinned producer snapshot, not an automatically synchronized copy of current
Naviamp build logic. Review/regenerate deliberately when changing it. Preserve upstream notices
and compare source/API/runtime evidence before changing the pin. See
the producer scripts for toolchains, bounded build settings and binary identities, and
[hosted delivery](HOSTED.md) for the trusted CI path. This is neither an
official JetBrains release nor a completed hosted artifact service.

## Local producer and scene delivery

The following commands stay local. An ARM64 Mac, Xcode with tvOS/iOS runtimes, Android SDK and
JDK 21 are required for the build/scene stages. Set `JAVA_HOME`, `PATH` and `ANDROID_HOME` as usual.
Python 3.11+ is required. Use new source, repository, bundle, installation and evidence directories.

```sh
python3 scripts/prepare-tvos-compose-sources.py --destination build/tvos-sources
python3 scripts/build-tvos-compose.py --sources build/tvos-sources \
  --gradle-home build/tvos-producer-gradle --native-home build/tvos-producer-native
```

The producer validates the patched Git trees, working files and pinned settings-plugin bytes before
building. It runs the source-graph, exact-publication and native deployment-floor probes, then seals
new UI/resources repositories. UI uses two workers/6 GiB, resources two workers/4 GiB, with Gradle
build/configuration caches disabled. Producer and scene entry points share a workspace build lease
so their heavy phases cannot overlap. Native caches are keyed by source bundle, producer scripts,
wrapper pins, JDK, architecture, device/simulator SDKs and deployment floor. Logs, commands,
identity and the new manifest are in `build/tvos-sources/producer-evidence`. Failed output is
retained and never automatically accepted or overwritten. No app build invokes the producers.

Review the new manifest/source/deployment evidence before using its SHA-256 as `MANIFEST_SHA`:

```sh
python3 scripts/tvos-compose-delivery.py pack \
  --ui-repository build/tvos-sources/ui-repository \
  --resources-repository build/tvos-sources/resources-repository \
  --manifest build/tvos-sources/producer-evidence/manifest.json \
  --manifest-sha256 "$MANIFEST_SHA" --output build/tvos-compose.zip
python3 scripts/tvos-compose-delivery.py install --bundle build/tvos-compose.zip \
  --bundle-sha256 "$BUNDLE_SHA" --manifest-sha256 "$MANIFEST_SHA" \
  --destination build/tvos-artifacts
python3 scripts/run-tvos-scene-tests.py --artifacts build/tvos-artifacts \
  --expected-manifest-sha256 "$MANIFEST_SHA" \
  --runtime com.apple.CoreSimulator.SimRuntime.tvOS-27-2 --build-timeout 7200 --output build/tvos-scene
```

`pack` prints `BUNDLE_SHA`. Deliver both identities through the separately reviewed source/record,
not an untrusted archive sidecar. The installer checks bundle identity before extraction, enforces
path/type/count/size limits, then verifies every publication and file against the trusted manifest.
The archive has deterministic ordering, timestamps and permissions; packaging the same sealed
inputs yields identical bytes. Independent producer builds are **not** claimed bit-identical.

`--artifacts` creates an isolated current-working-tree snapshot in the evidence directory, including
local source edits, untracked files in source/tooling roots and explicit new tooling, and generates settings using the installed repository
URIs. It records every input hash and the generated settings hash. Normal checkout settings stay
unchanged. Before Xcode runs, the consumer graph gate checks both TV targets and original iOS,
including official Lifecycle and absence of private iOS artifacts. A disposable simulator runs the
existing complete remote/account/cache-recovery/background-playback case and is removed afterward.
No real accounts, existing simulator profiles, uploads, package publication or CI dispatch are used.

Trusted hosted writing, automatic artifact distribution and hosted scene execution remain separate
release gates. This local pipeline is the reproducible command path for validating them first.

A fully cold app framework link can exceed the default 30-minute build budget on a busy
16 GiB host. `--build-timeout` can explicitly allow up to two hours without raising the
4 GiB app heap or worker limit. Retain elapsed times and failures; this does not waive build-cost acceptance.

An early resources-stage setup failure can be resumed explicitly with
`--resume-from /absolute/producer-evidence/summary.json`. This only accepts completed UI and
plugin stages with identical recorded commands and unchanged source, wrapper, SDK, JDK,
architecture, deployment and publication inputs. The 49 completed publications are revalidated,
their per-file hashes are retained and checked unchanged after building the three missing resources
publications. Resume writes a new evidence directory and links the predecessor summary by SHA-256;
it does not overwrite the earlier logs or automatically accept partial resources output.
Resources tasks run from `resources/components`; the plugin has its own `resources/gradle-plugins`
build. Stage evidence records the working directory as well as arguments.

The scene build creates its owned device before Xcode but defers boot until the framework passes
its deployment audit. It handles an already-shut-down device during cleanup and terminates
captured Xcode/compiler process trees and single-use Gradle processes belonging to its generated
snapshot on timeout or cancellation. Fixture shutdown has a bounded wait and forced-stop fallback.
Simulator shutdown and deletion are attempted independently even if fixture cleanup or simulator
inspection fails. Cleanup outcomes and errors are retained before source-evidence collection;
a scene cannot pass until its fixture and owned simulator cleanup succeed.

Run the portable inventory/delivery and native ownership regressions locally with:

```sh
python3 -m unittest discover -s scripts/tests -p 'test_verification_tvos_*.py' -v
```

The process tests need POSIX process inspection and the fixture tests need loopback sockets;
run them in a local execution context that permits those effects.

## Hosted delivery and scene CI preparation

The local drafts for an accepted-main producer, separate provenance signer and read-only scene
consumer are documented in [hosted delivery](HOSTED.md).
Every action is commit-pinned. A producer emits an unapproved candidate; only a separately reviewed
`trusted-artifact.json` accepts its immutable run/artifact IDs and archive/bundle/manifest hashes.
PR consumers read that policy from their base commit and require cryptographic provenance
verification before repository installation. There is no shared binary/cache writer in a PR job.

The initial trust record deliberately remains `pending-hosted-production`. This command will fail
before network or installation until a real hosted candidate has passed qualification and promotion:

```sh
python3 scripts/tvos-hosted-compose.py fetch --pin build-support/tvos-compose/trusted-artifact.json \
  --destination build/tvos-hosted-artifacts --evidence build/tvos-hosted-fetch
```

The workflows have not been dispatched or pushed by this preparation. See the checkpoint for the
bootstrap order, first hosted/fork acceptance, 90-day artifact renewal and remaining release gates.
