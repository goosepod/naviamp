# Naviamp Agent Development Rules

## Release Announcements

Whenever a new Naviamp release is pushed, create a GitHub Discussion in the **Announcements**
category that explains what is new, what changed, important fixes, and any upgrade or compatibility
notes. Feature branches and other unreleased work do not receive release announcements.

## Core Is the Product

These are hard architecture requirements, not preferences. Naviamp is one shared application with
thin Android, Desktop, and iOS hosts. A feature is not complete if its behavior must be implemented
again for another platform.

All agents must begin every implementation in common code. Do not prototype, repair, or temporarily
wire product behavior in a platform module with the intention of extracting it later. If a common
owner does not exist yet, create that owner first.

Common ownership includes:

- Product behavior, state, UI, actions, menus, navigation, validation, orchestration, scheduling,
  retry policy, lifecycle policy, feature capability decisions, and user-facing status policy.
- Provider protocol behavior, authentication/session renewal, response interpretation, provider
  model mapping, and provider-specific persistence mapping. Put these in the provider's
  `commonMain` code when they are not provider-neutral; do not put them in a host.
- Persistence schemas, repository behavior, serialization, migrations, cache policy, and storage
  coordination that can use shared SQLDelight or opaque storage contracts.
- Complete shared interfaces and default behavior needed for a new thin host to connect, browse,
  navigate, and render the application without reconstructing features.

## Mandatory Placement Test

Before creating **or modifying** production code in any Android, Desktop, or iOS source set, answer
this question:

> Which concrete operating-system API, native library ABI, or host lifecycle rule makes this code
> impossible to compile and behave correctly in common Kotlin?

If there is no specific answer, the code MUST live in `core`, shared storage, or provider
`commonMain`. “The current caller is Desktop,” “Android already has a controller,” “this is faster
to test here,” and “the shared interface does not exist yet” are not valid answers.

When uncertain, stop and place the behavior in common code. Ask the user only when two genuinely
different product behaviors are required; never assume a platform difference.

## What May Remain Platform-Specific

Platform production code is limited to narrow effects that directly touch a platform boundary, such
as:

- Android Activity/Service/MediaSession/notification/permission/URI APIs and Android Auto.
- Apple application/audio-session/CarPlay APIs and native framework integration.
- Desktop windowing, taskbar/Dock, native dialogs, JVM filesystem/process APIs, and packaging.
- BASS/native-library loading, JNI/cinterop/ABI calls, audio-device integration, and OS callbacks.
- Database-driver creation, OS secure-secret storage, platform HTTP/TLS engine initialization, and
  platform file or directory selection.

Even in these cases, the platform may only translate types, invoke the native effect, publish the
result through a shared contract, and manage the unavoidable native resource lifetime. Selection of
when to call it, scheduling, retries, state transitions, provider logic, persistence decisions, and
user-visible behavior stay common.

An `expect`/`actual` implementation must contain only the irreducible platform operation. Do not use
`actual` files as a place to hide product logic.

## Required Implementation Order

For every feature, bug fix, or migration:

1. Locate the existing common owner and all platform duplicates before editing.
2. Define or extend the complete common model, contract, controller, and UI behavior.
3. Add common tests for the behavior and failure cases.
4. Compile or test the common implementation for Android, JVM/Desktop, and iOS before adding host
   wiring.
5. Add only the smallest required platform effect or delegation.
6. Re-read every changed platform production file and move anything that does not directly touch
   the named platform boundary back into common code before committing.

Do not touch platform product files merely to keep a legacy graph compiling when that graph is
scheduled for deletion. Prefer deleting or isolating the obsolete graph, or document the compile
exception in the active migration plan.

## Platform-Diff Accountability

Before every commit that changes platform production code, inspect the platform diff separately.
The final handoff must list each changed Android/Desktop/iOS production file and the exact native
boundary that justifies the change. If that justification cannot be stated in one concrete sentence,
the change must be moved to common code.

Platform adapters should normally shrink or remain nearly mechanical. New platform controllers,
repositories, schedulers, state holders, feature-specific action factories, provider mappings, or
policy classes are presumed architecture violations unless the code directly wraps one of the
allowed native boundaries above.

If an architecture violation is discovered during review or by the user, stop the feature work and
extract it immediately. Do not continue on the basis that it can be cleaned up later.

## File Placement and Naming

Before creating or moving any source file under an Android, Desktop, or iOS module, verify that the file depends on a concrete platform-only API, operating-system lifecycle, native library, or host integration.

- If the code can compile and behave correctly in shared Kotlin, place it in the appropriate `core` module.
- Product behavior, UI, actions, navigation, validation, orchestration, persistence policy, and provider-neutral logic belong in Core by default.
- A platform directory is not justified merely because only one platform currently calls the code.
- SQLDelight repositories that consume shared queries and models belong in `core:storage`; a host-selected driver, native path, filesystem, credential store, or OS database API remains platform-specific.
- Platform files should be narrow adapters for concrete OS or native capabilities. Document the exact constraint that prevents each new platform file from being shared.
- If only part of a proposed file is platform-specific, split the portable owner into Core and inject the narrow native effect through a shared contract.
- Use neutral or `Naviamp` names in Core and matching `Android`, `Desktop`, or `Ios` prefixes only for genuine platform implementations.

Treat an unexplained Android/Desktop/iOS implementation difference as architecture debt, not as an accepted capability difference.

Generated sources, build scripts, packaging metadata, native resource manifests, and tests that
exercise a real platform adapter are exempt from common placement, but they must not contain product
behavior as a workaround.

## Migration Discipline on Unreleased Branches

Before adding a persistence migration, identify the latest migration that exists on `main` or the
applicable release baseline. When a branch has not been merged into `main` and none of its schema
changes have shipped in a release, keep the branch's final schema change consolidated into the next
single migration after that baseline. As the feature evolves, edit that unreleased migration and the
canonical schema together; do not preserve intermediate branch-development states as additional
production migrations.

Create a subsequent migration only after the preceding schema version has been merged into the
release line, shipped, or the user explicitly requires a staged migration sequence. Before making
that decision, inspect the target branch and release history rather than inferring it from the local
database's `user_version`.

Local development and test databases may drift through experimental branch schemas. Repair those
databases directly when needed: first inspect the exact database and affected objects, then make the
smallest targeted change to branch-owned tables or the schema version marker. Derived branch data
may be dropped and regenerated when safe. Never add compatibility migrations solely to preserve a
local test database's branch history, and never alter unrelated user data to avoid a clean reset.
