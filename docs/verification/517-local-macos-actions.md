# Temporary local macOS Actions verification

Tracking #517. Hosted macOS ARM capacity cancelled the required #496/#508 jobs without assigning
runners. Local terminal tests remain useful but cannot satisfy checks bound to the GitHub Actions
application. Do not create synthetic success checks, weaken branch rules or merge around them.

Verify now accepts an optional `macos_runner_label` on an owner-triggered `workflow_dispatch`.
Blank input and all ordinary PR/reusable invocations retain hosted macOS. A nonblank selector must
be `naviamp-local-` plus 32 lowercase hexadecimal characters. The classification job validates it
before either macOS job can run, then publishes JSON labels requiring self-hosted/macOS/ARM64 and
the unique selector. Other platform jobs and every native/app/Keychain check remain unchanged.
All checkouts use the event SHA (or the existing explicit release recovery ref), rather than a
moving branch ref. Successful manual verification also records its exact tested source tree.
Existing release evidence-reuse policy is not weakened by this option.

Operator procedure:

1. Select the exact reviewed branch/head and generate a fresh UUID-hex selector for this dispatch.
2. Dispatch Verify at that branch with the selector. Verify the workflow run head matches the
   reviewed SHA and the macOS jobs request precisely those labels before connecting a runner.
3. Download the official macOS ARM64 Actions runner and verify its published SHA-256 checksum.
   Use a private temporary directory and repository-scoped ephemeral/JIT registration. Do not
   install a persistent service or share runner labels with ordinary workflows.
4. Run one ephemeral runner per queued macOS job, sequentially on the same Mac. The runner must
   have the validated unique selector, local JDK/Xcode/SDK access and a separate checkout/work
   directory. Keep registration material private and retain sanitized job/runner logs.
5. Require actual Actions job/check results and uploaded native/app/Keychain evidence. Remove any
   remaining owned runner registrations after completion/failure; never remove another runner.
6. The final merge must still meet current-main and exact-head branch protection. Source changes
   after dispatch require fresh verification. Local success does not resolve intermittent #454.

Local validation: five routing/failure tests and the complete 46 workflow-helper tests pass;
workflow YAML parses and `git diff --check` passes. The first unprivileged helper invocation could
not bind its loopback test sockets; the normal local test context passes. Full Actions verification
is pending. No platform production code changed.

The selection step also preserves reusable recovery of an older public source without the new
helper: only the unchanged hosted default is allowed in that case; manual dispatch fails closed
if its helper is absent. The initial real local Actions desktop job passed in 4m 14s, but that
run predates this recovery correction and is not final-head acceptance.


## Protected PR checks

A successful manual diagnostic run on the branch head was not accepted as PR #518's required run;
its head checks passed but the PR check rollup stayed empty. To satisfy that gate, use the actual
`pull_request` workflow and GitHub's merge snapshot. Before a new push/synchronize event, an owner
sets `NAVIAMP_LOCAL_MACOS_<PR number>` to a fresh selector and adds `verify:local-macos` to the PR.
The workflow permits that selector only when the head repository is the current repository and
the owner label is present. Forks and unselected PRs remain hosted. Missing/malformed selected
variables fail closed. Validate the actual PR association and exact head before connecting workers.
Remove the per-PR variable and opt-in label after the work is merged or abandoned.

The manual dispatch remains available for diagnostics, but its passing checks alone must not be
reported as satisfying a protected PR. Reusable older-release recovery inherits the caller's event
name: a dispatch with an explicit recovery source and no local selector retains the hosted default
when the helper is absent. That distinction is tested without weakening the local opt-in guard.
