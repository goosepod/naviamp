# Reviewed tvOS producer source inputs (#590)

`sources.zip` is a source-only snapshot: complete binary Git patches, per-path hashes, upstream
licenses and the 16-file Naviamp settings-plugin source input. It contains no compiled Compose
artifacts. The separately reviewed SHA-256 is pinned in
[`prepare-tvos-compose-sources.py`](../../scripts/prepare-tvos-compose-sources.py):

```text
08e488b70395517ae5140f20f154874405fa5bc3b8051665321a4c090df0ce04
```

| Producer | Official base commit | Complete patched tree | Changed paths |
| --- | --- | --- | --- |
| UI | `5f017375d926c73f1937408601b6cb28c12a8812` | `9e294881e3e1d86b0979a5bbd357576c09d92519` | 696 |
| Resources | `ab304f2538657d9f7077bdb40797f5db618b4c84` | `aa9b89dcd867364ab066cfe89894a93645160276` | 18 |

The UI input incorporates the reviewed port delta
`f29d2f99f3beafc60992c6a73dbbaa9f841c5d4c` → `6e266fe9e511c413e63d51011be1659c3696fee6`,
stable Material3 1.9.0 overlay and documented compile/format corrections. Resources incorporates
`d67175a3766c51caa673b220c74a4fb68a61fd31` → `9897904fab44c811affcd818b14b653bac0cf1ea`,
explicit local probe inputs and the Gradle 9 non-null extension-type bound.

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
[artifact delivery](../../docs/verification/590-compose-artifact-delivery.md) for toolchains,
bounded build settings, binary identities, validation and remaining gates. This is neither an
official JetBrains release nor a completed hosted artifact service.
