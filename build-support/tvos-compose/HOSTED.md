# Hosted Compose tvOS delivery

This is the hosted path for the reviewed Compose Multiplatform tvOS artifacts used by the Apple TV
app ([overview](../../docs/tvos.md)). `trusted-artifact.json` stays `pending-hosted-production`
until the bootstrap below promotes an approved producer run. Normal app builds resolve official
Compose coordinates and never use these artifacts.

## Accepted-source writer

`.github/workflows/tvos-compose-producer.yml` is manual-only and restricted to this repository's
`main`. Its preflight also requires the source SHA to equal the workflow SHA and an ARM64
GitHub-hosted runner. It accepts no arbitrary source ref, artifact URL, or cache input.

The read-only build job reconstructs the independently pinned source bundle, runs the local
producer into new repositories, seals all 52 publications, packages the deterministic delivery
bundle, verifies installation, audits both UIKit helper archives, and runs the full recovered app
scene with its consumer graph/framework deployment gates, followed by the shared TLS/range/seek
and transport recovery contract through real TV HTTP/BASS adapters. Source licenses, patches and notices
remain in the original source bundle retained with producer evidence. Actual toolchain/SDK/source
identities, logs, manifest, graph, xcresult, telemetry and cleanup are retained separately from the
one-file delivery artifact. No app consumer builds the Compose producers.

Producer and app Gradle/Native homes are separate disposable directories. There is no shared
Actions cache restore/save, partial cache fallback, or PR cache writer. Producer Native identity
checks remain in the existing build driver. Independent source builds are not claimed bit-identical;
packaging the same sealed repositories remains deterministic.

Only a qualified build uploads `tvos-compose-candidate-<run ID>-<attempt>`, with exactly `compose.zip`,
archiving explicitly enabled and overwrite disabled. A second, fresh Ubuntu job downloads that
exact artifact ID, rechecks the qualified bundle digest, and creates GitHub build provenance for
the bundle. Only this job receives `id-token: write` and `attestations: write`; source builds and
scene consumers do not. All third-party actions are pinned to their verified full commit SHAs.
The attestation action's required privileges and provenance behavior follow its
[official contract](https://github.com/actions/attest).

The final job emits `trusted-artifact.candidate.json`. Its status is always `candidate`, and it is
uploaded as review material. No job edits the accepted pin, publishes a package/release, pushes a
branch, or promotes itself.

## Reviewed consumer trust chain

`build-support/tvos-compose/trusted-artifact.json` is the sole hosted binary acceptance record.
An approved record must contain the canonical repository ID, producer workflow path and commit,
run ID/attempt, immutable artifact ID/name/archive digest, inner bundle digest, manifest digest,
and the reviewed source-bundle digest. Full SHAs and positive integer IDs are mandatory; unknown
schema fields, aliases such as `latest`, candidates and the initial pending record are rejected.

`scripts/tvos-hosted-compose.py fetch` performs these checks in order:

1. Validate the approved pin before any network access or repository selection.
2. Read the exact run and artifact metadata. Require a completed successful `workflow_dispatch`
   run of the named workflow on accepted `main`, matching repository/head-repository IDs, source
   SHA and attempt. Require the artifact's exact ID, name, run ownership, SHA-256 and bounded size;
   reject expired artifacts. Artifact/run metadata fields follow the
   [GitHub artifact API](https://docs.github.com/en/rest/actions/artifacts).
3. Download by numeric ID from `github.com`, with a 600-second/size budget. Check the actual outer
   archive digest and size; accept exactly one regular member named `compose.zip`. No arbitrary
   metadata URL, extraction path or adjacent checksum file selects an input.
4. Check the separately pinned bundle digest and require `gh attestation verify` to succeed.
   It pins the repository, signer workflow and commit, source commit/ref, GitHub OIDC issuer and
   SLSA provenance type, and denies self-hosted signers. CLI verification options come from the
   [official verifier documentation](https://cli.github.com/manual/gh_attestation_verify).
5. Pass the bundle and independently pinned manifest digest to the existing bounded installer.
   It verifies the complete 52-publication/file inventory before exposing a usable repository.

There is no CLI switch to bypass attestation or substitute local artifacts when hosted trust fails.
Missing/expired artifacts, incomplete or rerun attempts, signature rejection, changed bytes and
unapproved pins fail closed. Run metadata, artifact metadata, verifier output and the final outcome
are saved. Authentication is read-only, step-scoped `GH_TOKEN`; it never enters argv or evidence.
The consumer has `contents: read`, `actions: read`, and `attestations: read`, with no OIDC/write
permission. The explicit attestation read scope follows the
[repository attestation API](https://docs.github.com/en/rest/repos/attestations).

## Scene workflow and cleanup

`.github/workflows/tvos-scene.yml` is manual-only while the pin is pending, then covers relevant PR changes. It checks
out accepted policy from the PR's base SHA (accepted `main` for manual runs) separately from the
exact source under test. Fetch/install uses that policy's script and pin, so a PR cannot promote
its own candidate by editing either. Checkout credentials are not persisted. Authentication ends
before PR build/test execution; generated consumer settings remain isolated from the checkout.

The scene uses fresh app caches, records the actual available tvOS runtime, and retains the full
graph, native audit, recovery/database/authentication, remote/keyboard, foreground/background media
and xcresult evidence. Runtime selection does not establish acceptance on the candidate 18.5
minimum. The runner already captures failed-case evidence and performs cancellation/timeout
cleanup. A separate `always()` step retries only the recorded owned scene device, verifies its
Naviamp scene name/application, and saves `ci-cleanup.json`; it never shuts down all simulators.
The hosted jobs use disposable [GitHub-hosted runners](https://docs.github.com/en/actions/reference/runners/github-hosted-runners), not a personal/self-hosted machine. Job hard-kill
cannot guarantee a final step executes; local signal unwinding and ordinary CI cancellation remain
bounded cleanup paths, while a terminated hosted runner is retired by GitHub.

Both workflows also run the mandatory TLS/range/seek/recovery transport suite. It runs after the GUI scene with the same
manifest/runtime and app caches, but its own source snapshot, fixture and simulator. A second
always-run accepted-policy cleanup step covers that owned simulator. Native transport logs, JUnit,
source hashes and fixture counters are retained; private `fixture.bootstrap` and generated consumer
trees are excluded. The producer cannot upload a candidate unless both suites pass. 

The native runner requires eleven strict cases: real Core/surface-session playback with
AVAudioSession deactivation, interruption and route recovery phases, and remote-command recovery
phases with the native callback translation marker. Both workflows require `passed`,
`scope=tvos-simulator` and `os27SimulatorQualified` after the default native runner. This follows
the owner's Simulator-only runtime acceptance: the installed runtime does not deliver genuine audio
lifecycle contexts, consistent with Apple's documented Simulator limits.
The receipt keeps `os27ContextQualified=false` and genuine context delivery unverified. A separate
`--require-system-context` diagnostic retains that stronger proof without turning an unsupported
Simulator capability into a hosted prerequisite. No supported Simulator recovery phase is optional.
The trusted hosted pin and first hosted run remain pending.

## Bootstrap and subsequent promotion

These steps run after this tooling merges into `main`:

1. Accept the opt-in tooling/workflows through the issue's PR and normal required checks. The new
   hosted scene check intentionally cannot pass while its accepted base pin is pending. Do not
   make it a required branch check before the initial bootstrap promotion is accepted.
2. Dispatch the producer from that accepted `main` commit. Review its completed successful run,
   source/SDK/producer evidence, both helper audits, full scene and cleanup, immutable artifact ID
   and hashes, and cryptographically verified bundle provenance. A failed/skipped run is no candidate.
3. Use the producer's promotion record as a proposal. In a separate reviewed source change, set
   its status to `approved` and replace the pending trust record. The producer already qualifies
   the scene without depending on an approved hosted pin, avoiding recursive bootstrap dependence.
   During the initial pin PR, consumer CI still reads the pending base record and stays blocked;
   that initial qualification comes from the accepted producer's complete scene evidence.
4. After the pin is accepted, run the independent scene workflow against accepted source and a PR
   source. Review authenticated download/verification and full scene results before making this
   scene check required or calling hosted delivery accepted.

Later replacements also require source review. The old accepted pin stays in force for PRs until
the replacement is merged. Artifacts/evidence request 90-day retention; repository limits can
shorten availability. Plan a reviewed refresh before expiry. An expired/deleted artifact or a newer
attempt of the same producer run blocks consumption instead of selecting another binary. Rebuild
as a new candidate and review a new pin; do not re-upload mutable bytes to an old identity. Long-term
package/registry distribution is outside this initial Actions-artifact delivery path.
