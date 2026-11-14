# Issue 43 — Apple CarPlay

## Current checkpoint

Started from accepted main `e2da3aaf5ce8acbd008a9ef3ccf595474e7ed253` on
`codex/43-carplay`. This is an implementation checkpoint, not a claim that CarPlay is available.
PR #605 is a separate dependency: integrate its accepted main commit after it merges, rather than
merging its unreleased feature branch. Keep the original PR #605 checkout unchanged.

The first shared implementation is `NaviampApplicationRuntimeOwner` in `core:app`. It lazily owns
one runtime across scene acquisitions, retains it for reconnects, rejects reentrant construction,
and makes shutdown final and idempotent. Creation failure can be retried without disposing a runtime
that never existed. Calls must be confined to the application lifecycle dispatcher. Native wiring
has deliberately not been added yet: compile this owner for Android, JVM and iOS first.

## Existing owners and first integration gap

- `NaviampCoreExternalPlaybackBridge` owns shared browse snapshots, selection and transport dispatch.
- `CatalogPlaybackIntent` owns catalog playback intents. `QueueBrowsePolicy` owns bounded,
  current-item-first queue ordering. Reuse these instead of implementing a CarPlay-only catalog.
- `IosNativePlaybackIntegration` already translates shared playback into MediaPlayer Now Playing,
  remote commands and AVAudioSession callbacks. CarPlay must reuse that registration.
- `NaviampRootView.Coordinator` currently constructs `NaviampIosApplication` and closes it when the
  phone coordinator is released. A car-first launch cannot depend on this view existing; phone and
  car scenes must acquire the same application-owned instance. Scene disconnect must release only
  scene presentation resources, not the player, repositories or remote-command registration.
- `Info.plist` currently has no CarPlay scene configuration. The Xcode app has an iOS 17.2 deployment
  target and no CarPlay entitlement configuration. Developer-account approval is unverified; the
  absence of a repository entitlement file does not establish account approval status.

## Implementation sequence

1. Test and compile shared runtime ownership on Android, JVM and both iOS targets.
2. Extend the shared external browse/presentation contract where needed for an initial bounded
   list, source-bound item identity, loading/error states and command completion. All new status
   copy belongs in English and every maintained translation. Avoid phone dialogs in car flows.
3. Wire the existing iOS application through one application-owned runtime reference. Add only
   scene connection/disconnection translation and CarPlay template presentation in native code.
4. Register the audio CarPlay scene and verified signing entitlement configuration. Show a minimal
   playlist/recent-item browse-to-play flow and synchronize the system Now Playing template with
   the existing MediaPlayer integration before expanding the catalog.
5. Prove car-first launch, phone-first launch, reconnect, phone-view disposal, process restart and
   interruption recovery against the same runtime. Re-run source-switch and stale-selection cases.
6. Expand feature coverage only after the minimal lifecycle and browse-to-play acceptance passes.

## Action and capability decisions

| Area | First proof | Completion gate |
| --- | --- | --- |
| Library and playlists | Existing shared browse rows and playback intents | Broader catalog loading, pagination and deterministic completion/error handling |
| Radio | Existing station choices and play intent | Live-stream failures, reconnect and transport capability mapping |
| Search | Reuse shared policy; label actual scope accurately | Full provider search must not be claimed from the bridge's current in-memory filtering |
| Queue | Shared current-item-first bounded queue | Native template limits, selecting active/other items and source-switch invalidation |
| Favorites and related tracks | Existing shared command availability | Only expose supported safe actions; no unsupported template controls or editor flows |
| Downloads/offline | Retain existing shared playback ownership | Download-only browsing and unavailable media status without phone-oriented dialogs |
| Artwork | Existing provider-aware byte loader/cache | Authenticated images, cancellation, stale source results and native shape rendering |
| Connection/source changes | Shared connection/runtime state | Car-visible recovery and safe source selection; setup/credential entry stays on phone |

These are acceptance boundaries, not separate implementations of provider behavior in Swift.

## Apple capability and acceptance gates

Use Apple's CarPlay audio entitlement, `com.apple.developer.carplay-audio`, with the supported
template scene APIs. The app's iOS 17.2 minimum does not require the deprecated pre-iOS-14 playable
content entitlement. Request approval and the entitlement addendum through the developer-account
owner, then verify the App ID, provisioning profile and signed app entitlement. Do not equate an
unsigned build or a plist entry with approved distribution. No developer-account change or request
has been submitted by this work.

CarPlay audio presentation uses the scene's `CPInterfaceController` and Apple templates. It must
not render the Compose phone UI into the CarPlay window. Native files must name their exact UIKit,
CarPlay, MediaPlayer or AVAudioSession boundary; lifecycle and product decisions remain shared.

Simulator acceptance must include actual template browse-to-play, queue/Now Playing updates,
remote commands, detachment/reattachment, source switching, offline/error handling and authenticated
artwork. Physical iPhone/head-unit acceptance is separately required for locked-phone operation,
Siri, wired/wireless reconnects as supported, audio interruptions/ducking, routing and background
process recovery. Simulator results cannot establish those physical behaviors. Apple entitlement
approval, app-review requirements and physical acceptance remain release blockers.

## Sources checked November 13, 2026

- [Apple CarPlay developer resources](https://developer.apple.com/carplay/)
- [Requesting CarPlay entitlements](https://developer.apple.com/documentation/carplay/requesting-carplay-entitlements)
- [Audio entitlement](https://developer.apple.com/documentation/bundleresources/entitlements/com.apple.developer.carplay-audio)
- [Displaying content in CarPlay](https://developer.apple.com/documentation/carplay/displaying-content-in-carplay)
- [Using the CarPlay simulator](https://developer.apple.com/documentation/carplay/using-the-carplay-simulator)

## Verification

Shared runtime-owner tests cover repeated scene acquisition, final shutdown, shutdown before first
use, retry after failed construction, reentrancy and disposal failure. All six passed on Windows;
shared Android compilation and architecture checks also passed:

```powershell
.\gradlew.bat :core:app:jvmTest --tests 'app.naviamp.app.NaviampApplicationRuntimeOwnerTest' :core:app:compileDebugKotlinAndroid verifyCoreFirstArchitecture --max-workers=2 '-Pkotlin.native.enableKlibsCrossCompilation=false' --console=plain
```

Local log: `build/issue-43/runtime-owner-tests.log`. iOS compilation of this new owner remains
pending before host wiring. Native scene integration, CarPlay simulator, signing and
physical-head-unit checks have not run at this checkpoint. Native factories remain responsible
for cleaning up their own partial construction if they throw before returning a runtime.
