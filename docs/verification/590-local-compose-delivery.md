# Local Compose delivery and scene automation (#590)

All work remains local on `codex/590-apple-system-media`. Nothing in this checkpoint uploads
artifacts, dispatches CI, changes normal app settings, or establishes a trusted hosted writer.
The existing simulator-only runtime acceptance decision remains in force.

## Delivery contract

- `prepare-tvos-compose-sources.py` retains the reviewed source-bundle and complete patched-tree
  gates. The bundle identity remains `08e488b70395517ae5140f20f154874405fa5bc3b8051665321a4c090df0ce04`.
- `build-tvos-compose.py` verifies source trees, working files, absence of untracked producer inputs
  and the source-bundle's settings-plugin bytes. Its explicit local publication tasks build the
  48 UI and four resources/plugin publications. It retains stage logs, commands, toolchain/SDK
  identity, script/wrapper hashes and a newly sealed manifest. New outputs are candidates, not
  automatically accepted replacements of an existing manifest.
- `tvos-compose-delivery.py pack` checks the separately trusted manifest against both repositories
  before packaging. Entry order, timestamps and permissions are fixed; the same sealed inputs
  produce identical ZIP bytes. It publishes the output through an atomic no-replace hard link.
- `install` checks the independently supplied bundle hash before extraction. Count/size limits,
  duplicate/path/type checks and a private staging directory precede full publication/file
  verification. A failed installation does not leave a usable destination. Existing destinations
  are rejected. Neither archive names nor adjacent checksum files grant trust.
- `prepare-tvos-scene-consumer.py` verifies installed repositories before generating a new consumer.
  It copies tracked files and untracked source/tooling inputs from the current working tree, preserves executable
  modes, rejects symlink inputs, and records source hashes and the generated settings hash. The
  settings template selects the reviewed private coordinates only in this isolated checkout.
- `run-tvos-scene-tests.py --artifacts ...` uses that generator. Both new and legacy prepared
  consumers now pass the complete UI/presentation graph gate before Xcode: TV device/simulator,
  official Lifecycle and original iOS with no private artifacts. It retains the graph report,
  native test evidence, authentication/cache telemetry, attachments and owned-device cleanup.
  Builds use a fresh single-use JVM. The producer and scene entry points share a workspace lease
  to serialize their heavy phases on a constrained Mac.

The consumer plugin now parses local repositories as file URIs for both metadata reads and
existence probes. This preserves escaped spaces, non-ASCII characters and `#` in relocated paths.
Its new regression first failed on the remaining existence-probe path; both paths are corrected.
The pinned producer settings-plugin snapshot is separate and has not been silently regenerated.

Reproduction commands and prerequisites are in
[the source/delivery README](../../build-support/tvos-compose/README.md).

## Checked local evidence

The existing independently reconstructed candidate is used as the first delivered artifact set:

- Manifest: `16218e7b494df690dcfddb81d247a19dd3b86d08f03b124428fbe5c7eec16d97`.
- Bundle: `9a83a0c546373f36e86af211c5718f6ce3a7d3d39c57aacff61f9c9864225a44`.
- Two independent packaging invocations produce that same bundle identity. Installation verifies
  all 52 publications / 1,580 files after relocation. This is binary-delivery reproducibility,
  not a claim that independent producer builds emit identical KLIBs/JARs/Maven timestamps.
- The initial 39 tvOS Python tooling cases pass, including six delivery/snapshot cases. The final
  settings-hash assertion also passes in the focused six-case suite.
- All 26 consumer-plugin tests pass, with no failures/errors/skips.
- The generated consumer's fresh graph check passes: UI iOS 130 modules/zero private, TV 131/16;
  presentation iOS 132/zero private, TV 133/16. Both TV target graphs are checked independently.
  Original iOS remains official and Lifecycle remains 2.11.0.

Evidence is under ignored `build/590-delivery-1116`, including the two bundles, installed
repositories, snapshot inventory, graph output, plugin test logs and app-run evidence. The
accepted source of the delivered binaries remains the separately reviewed candidate above.

## Long-running validation and limits

The first concurrent producer/app attempt caused excessive pressure on this 16 GiB Mac. Its
producer logs are preserved as `producer-evidence-interrupted-memory` in the prepared source
root. The original scene attempt was stopped during native compilation after 1,252.294 seconds;
its owned simulator shutdown/delete both returned zero. Neither interrupted attempt is a pass.
The lease and single-use-JVM changes prevent that overlap in subsequent runs.

The serial scene attempt reuses the generated consumer and completed outputs, with the corrected
consumer-plugin URI handling recorded in `build-tooling-overlay.json`. The generated settings
are also an intentional difference from the source checkout. It repeats integrity and full graph
verification before rebuilding. Native compiler traces and the initial snapshot remain retained;
this is not a matched build-performance benchmark.

The serial app build reached its 1,800-second budget before the framework binary completed.
Shared app/UI/presentation/storage/bootstrap KLIBs and the small Swift header were generated;
this is not a completed framework link or runtime pass. Simulator shutdown/delete returned zero.
Xcode left the single-use Gradle wrapper/daemon running after its own termination; those two
identified processes were stopped explicitly. The runner now handles a timeout by stopping only
single-use Gradle trees belonging to a generated, tool-owned consumer. A real OS regression
confirms cleanup of a parent and detached-session child while preserving an unrelated process.
The producer similarly cleans up its captured subprocess tree on cancellation/timeout.

The scene runner has an explicit 60–7,200-second build budget option; the default remains 1,800.
A longer cold-link window does not increase the 4 GiB heap or worker limit. The completed source
producer exposed a resources working-directory error before compilation. Resources belongs to
`resources/components`, while the plugin belongs to `resources/gradle-plugins`; both are now
explicit and stage evidence records `cwd`. Guarded resume revalidates the source/toolchain inputs,
completed commands and the 49 completed UI/plugin publications, retains their file hashes, and
checks those bytes unchanged after the three resources publications. Earlier failed logs remain.

## Completed source-to-scene checkpoint

The local source producer completes all 52 publications / 1,580 files. UI takes 2,936.808 seconds,
the standalone plugin 131.950 seconds and resources 506.986 seconds. UI used a fresh identity-keyed
Native cache and warm Gradle dependencies; the resumed resources stage reused the completed,
revalidated UI/plugin outputs. These are not fully cold-host or matched performance benchmarks.

- New manifest: `970fd6056b5169f65d6477ed365556f0a0dc02791b3d39e2c6f102ab4912d746`.
- New bundle: `23e84573f98eb9e24f04b6180cd8dacd556f8bd1756e0d080e9155673051ff00`.
- Two packaging invocations produce identical bytes. The installer verifies the complete inventory
  after relocation. The prior candidate identity is retained separately rather than overwritten.
- Both newly produced UIKit helper archives pass all 27 ARM64 objects each: platforms 3/8,
  zero deployment declarations above 18.5.
- The new `--artifacts` scene path creates another fresh consumer directly from the working tree.
  Its integrity and full graph gates pass. Original iOS has zero private artifacts; both TV targets
  retain the 16 reviewed private modules and official Lifecycle 2.11.
- The app's artifact-specific Native home is seeded only with installed compiler/dependency tools,
  with compiler JAR hashes verified and download/object-cache directories removed. The seed and
  omitted paths are recorded in `app-native-seed.json`. Old private binary caches are not used.
- Xcode build-for-testing passes in 396.199 seconds at the retained two-worker/4 GiB limit. The
  actual framework passes all 3,960 ARM64 objects, platform 8, zero violations above 18.5. The Swift
  export header remains 15,641 bytes. Earlier slow/interrupted attempts remain evidence; this is
  neither a controlled speedup nor a build-cost signoff.
- On tvOS Simulator 27.2, the complete remote/keyboard/main+Aurral/relaunch/cache-purge/foreground
  and background media case passes: one test, zero failures/errors/skips/expected failures, native
  case 195.226 seconds (Xcode command 238.868 seconds). Stage requests are 43/31/85 with zero
  authentication rejections. The 2,734-byte encrypted record survives the targeted cache deletion
  byte-identically and the database is rebuilt. The fixture is silent PCM; no audibility is claimed.
- Final screenshots are visually inspected: recovered discovery, circular artist artwork and the
  returned paused 0:23 clock are visible. All 619 audited runtime source files match the current
  working tree byte for byte. Simulator shutdown/delete both return zero.
- Final local suites pass 41 Python tooling cases and 26 plugin cases. Process cleanup includes a
  detached-session child and preserves an unrelated process. CLI help and invalid build budgets
  are checked before resource allocation. Source snapshots include new untracked source owners
  and exclude unrelated worktrees.

Final evidence: `build/590-prepared-compose-remote/producer-evidence{,-resume-1,-resume-2}` and
`build/590-delivery-1116/scene-source`, including the runner identity, complete snapshot,
telemetry, deployment audit, runtime-source audit and attachments. The sources remain pinned to
bundle `08e488…ce04`; neither the producer snapshot nor normal app settings were silently changed.
The local source-build/package/install/generated-consumer/scene path is accepted for this
checkpoint. This does not promote a hosted writer or establish release readiness.

Hosted trusted artifact production/distribution, hosted full-scene checks, older-runtime proof,
signing/distribution and the existing product/runtime acceptance gates remain open under #590.
No public support or release-readiness claim follows from packaging or graph checks alone.

## Cancellation and cleanup follow-up

The resumed local review found two failure paths to close before hosted scene execution: cancellation
could leave Xcode's compiler descendants alive, and a fixture shutdown timeout could skip simulator
deletion. Scene commands now capture their process trees and stop them on timeout or cancellation,
including detached children and the generated consumer's single-use Gradle wrapper. If process-tree
inspection fails, cleanup still attempts the known command process directly and records failures.
Unrelated builds and shared Gradle daemons are not selected.

Fixture cleanup uses a bounded terminate/wait/kill/wait sequence. Simulator inspection, shutdown
and deletion have budgets; a fixture or shutdown failure cannot skip the subsequent deletion attempt.
The runner writes cleanup evidence before collecting source hashes and marks a scene successful
only after both the test case and owned-resource cleanup succeed.

- All 47 tvOS Python cases pass, including real command-timeout/detached-child cleanup and tests for
  keyboard cancellation, fixture timeout, simulator inspection/shutdown failures and preservation of
  unrelated processes/devices. Evidence: `build/590-delivery-1116/cleanup-followup-tests.log`.
- The initial sandboxed attempt cannot inspect processes or bind loopback fixture sockets; it is
  not a passing run. The complete suite above is rerun with the required local OS access.
- A live fault probe creates one unbooted simulator and a fixture that ignores SIGTERM. Cleanup
  forcibly stops that fixture (exit -9) after its 10-second wait, reports fixture/shutdown/delete
  all zero, and confirms the device no longer exists. Evidence:
  `build/590-delivery-1116/scene-cleanup-fault.json`. No existing devices are selected or booted.
- Both producer and scene CLI entry points now translate SIGTERM into stack unwinding so their
  owned-resource `finally` blocks can run. A real OS test terminates a controller while its command
  is active, verifies the owned command stops, and observes exit 143. The handler is restored after
  the guarded scope. All 49 cases pass in
  `build/590-delivery-1116/cleanup-followup-signals-tests.log`.
- Failed-scene results/attachments are now collected before propagating the test failure. A
  collection error is recorded separately and cannot mask the original case failure or skip owned
  resource cleanup. The final 51-case suite passes in
  `build/590-delivery-1116/cleanup-followup-final-tests.log`, including both failure paths.

The fresh-consumer follow-up builds in 619.058 seconds and passes the full graph and all 3,960
framework object deployment checks. Its scene fails after relaunch at the main-library label's
visibility assertion (case 82.851 seconds). The failure screenshot shows Aurral still focused;
the fixed two-Down assumption did not reach the library row, leaving its label obscured by the
bottom player. Fixture/shutdown/delete all return zero. The failed xcresult and 64 exported
attachments remain in `build/590-delivery-1116/scene-cleanup-followup`.

The native XCTest now allows at most two further Down inputs while waiting for that actual label
to become hittable. It still uses the real remote and fails if the card remains unreachable.
No product focus policy is changed. The rerun uses a new simulator and retains the unchanged app
framework, rebuilding the Swift test in 42.785 seconds. The three test/runner/ownership overlays,
their before/after hashes and original bytes are in `scene-cleanup-focus-overlay`; the original
consumer inventory is retained. `scene-cleanup-focus-fixed/runtime-source-audit.json` confirms all
1,472 audited application/platform inputs match the current checkout, and runner/ownership hashes
match their overlaid consumer copies. Full graph and 3,960-object deployment gates pass again.
These runs are not matched cold/warm build-performance measurements.

The rerun passes on tvOS Simulator 27.2: one case, zero failures/skips/expected failures, native
case 196.063 seconds (Xcode command 236.282 seconds). Stage requests remain 43/31/85 with no
authentication rejection. The 2,734-byte encrypted recovery record survives targeted database,
WAL and SHM removal; the database is rebuilt. Screenshots are visually checked: the relaunched
library card is focused with its label visible, the recovered Aurral artist is circular, and
background controls return to a paused 0:25 clock. The PCM fixture remains silent.

Fixture/shutdown/delete all return zero with no cleanup errors. The final OS inventory confirms
all six owned device IDs from these delivery runs are absent; no idle compiler remains to close
in the dedicated profile. Evidence: `scene-cleanup-focus-fixed` and
`cleanup-followup-final-resources.json` under `build/590-delivery-1116`. Nothing is pushed and no
CI is dispatched. This local reliability checkpoint is complete; the hosted and product gates
listed above remain open.

This follow-up changes local verification tooling only. The existing platform production diff is
`platforms/apple-media/src/appleMain/kotlin/app/naviamp/apple/media/ApplePlaybackSurface.kt`:
it maps the common album title to Apple's `MPMediaItemPropertyAlbumTitle` at the MediaPlayer API
boundary. No portable product behavior is added to a host.
