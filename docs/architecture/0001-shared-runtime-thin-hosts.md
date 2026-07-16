# ADR 0001: Shared Runtime and Thin Platform Hosts

- **Status:** Accepted
- **Date:** 2026-07-16
- **Target:** Naviamp 2.0.0

## Context

Naviamp already shares domain rules, provider behavior, storage schemas, UI models, and much of its Compose UI. Android and Desktop nevertheless construct separate application state, controllers, actions, and lifecycle effects. The previous iOS scaffold followed the same pattern by starting a third platform runtime, leaving playback and several product flows incomplete.

Maintaining three application implementations would make feature parity a recurring manual task. It would also place iOS-specific concerns into product logic and make Android's service-owned playback lifecycle difficult to reason about.

Naviamp needs one product implementation while preserving genuine operating-system differences, existing Android background playback, Desktop packaging, and the final requirement that all platforms use BASS.

## Decision

Naviamp 2.0 will use one shared application runtime and shared Compose application entry point, hosted by thin Android, Desktop, and iOS applications.

The shared runtime owns:

- application and session state;
- navigation state and restoration;
- provider connection coordination;
- media, playlist, radio, search, download, and settings actions;
- queue and playback policy;
- shared lifecycle decisions and user-facing errors;
- construction of shared UI state and actions.

Each platform host owns only operating-system integration and constructs implementations of narrow shared contracts. Examples include playback devices, media sessions and remote commands, secure secrets, database drivers, filesystem locations, connectivity, notifications, permissions, file selection, application lifecycle, window management, and distribution/update integration.

The composition boundary will use explicit dependency construction initially. A dependency-injection framework will be introduced only if concrete lifecycle or graph-management needs justify it.

Android playback may continue to be owned by a foreground service. The Activity and service must communicate through a shared session/command boundary so the shared product runtime does not require Activity lifetime.

iOS may first implement `PlaybackEngine` with AVPlayer to prove streaming, background audio, interruptions, route changes, Control Center, and Now Playing integration. That implementation is temporary. BASS is the required normal iOS playback engine for Naviamp 2.0.0.

## Consequences

### Positive

- Product behavior is implemented once and is shared by default.
- iOS becomes a platform-adapter project rather than a separate application rewrite.
- Android and Desktop divergence becomes visible through explicit contracts.
- Shared runtime tests can verify behavior without launching an operating system UI.
- Platform capabilities can be surfaced honestly and consistently.
- BASS-specific advanced playback remains available behind existing playback capability interfaces.

### Costs and risks

- Existing Android and Desktop composition roots must be decomposed incrementally.
- Android's service lifecycle requires a deliberate session boundary; forcing it into a UI-owned runtime would cause regressions.
- Desktop settings and credential storage need separation and security hardening.
- Storage construction and some persistence behavior currently live in platform applications.
- Apple targets will expose JVM assumptions in otherwise shared modules.
- A long-lived migration branch requires disciplined checklist updates and frequent pushes.

## Rejected Alternatives

### Maintain three independent applications

Rejected because navigation, controllers, settings behavior, and feature wiring would drift across platforms.

### Merge and continue the old iOS scaffold

Rejected because it is incomplete, substantially diverged from current `main`, and establishes a separate iOS runtime rather than the chosen shared-runtime boundary. It may be inspected for isolated build or bridge lessons.

### Rewrite the existing applications at once

Rejected because Android service playback and mature Desktop behavior need continuous regression protection. Migration will be performed in behavior slices.

### Use AVPlayer as the final iOS engine

Rejected as the final target because Naviamp requires BASS performance and feature parity. AVPlayer remains useful as a temporary integration proof of concept.

### Move platform APIs into common code through operating-system checks

Rejected because it hides platform coupling, makes common tests weaker, and does not produce replaceable platform services.

## Validation

This decision is successfully implemented when:

- Android and Desktop launch the same shared application runtime;
- iOS launches that runtime through a minimal SwiftUI/UIKit shell;
- platform hosts contain operating-system integration rather than duplicate product controllers;
- shared runtime behavior is covered by platform-independent tests;
- Android service-owned playback survives Activity recreation;
- BASS is the normal playback engine on Android, Desktop, and iOS;
- all platform differences are explicit capabilities or documented unsupported behavior.
