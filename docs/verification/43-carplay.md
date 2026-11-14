# Issue 43 — Apple CarPlay

## Current checkpoint

Started from accepted main `e2da3aaf5ce8acbd008a9ef3ccf595474e7ed253` on
`codex/43-carplay`. This is an implementation checkpoint, not a claim that CarPlay is available.
PR #605 merged as accepted main commit `57c26661247facd5d3ddf43319b7f119d6683c22` and was integrated
through merge `a9bc29da71762151ded3a3e00fe5365587f40904`. The original PR #605 checkout was not changed.

The first shared implementation is `NaviampApplicationRuntimeOwner` in `core:app`. It lazily owns
one runtime across scene acquisitions, retains it for reconnects, rejects reentrant construction,
and makes shutdown final and idempotent. Creation failure can be retried without disposing a runtime
that never existed. Calls must be confined to the application lifecycle dispatcher. This owner passed
JVM and native iOS tests and both Android/iOS compilation before native scene wiring was added.

## Existing owners and first integration gap

- `NaviampCoreExternalPlaybackBridge` owns shared browse snapshots, selection and transport dispatch.
- `CatalogPlaybackIntent` owns catalog playback intents. `QueueBrowsePolicy` owns bounded,
  current-item-first queue ordering. Reuse these instead of implementing a CarPlay-only catalog.
- `IosNativePlaybackIntegration` already translates shared playback into MediaPlayer Now Playing,
  remote commands and AVAudioSession callbacks. CarPlay must reuse that registration.
- At the starting baseline, `NaviampRootView.Coordinator` constructed `NaviampIosApplication` and closed it when the
  phone coordinator is released. A car-first launch cannot depend on this view existing; phone and
  car scenes must acquire the same application-owned instance. Scene disconnect must release only
  scene presentation resources, not the player, repositories or remote-command registration.
- At the starting baseline, `Info.plist` had no CarPlay scene configuration. The Xcode app has an iOS 17.2 deployment
  target; the new scene and entitlement registration is described below. Developer-account approval is unverified; the
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

Local log: `build/issue-43/runtime-owner-tests.log`. Native factories remain responsible for cleaning
up their own partial construction if they throw before returning a runtime.

## Phone and CarPlay scene wiring checkpoint

The UIApplication delegate now holds a single native application factory backed by the common
runtime owner. Both the SwiftUI phone coordinator and CarPlay scene delegate acquire that factory's
application. Phone-controller disposal no longer closes playback. Each phone scene gets a separate
UIKit controller and raster-layer reference, while sharing Core state, playback and repositories.
Only weak phone-controller references are retained for native document presentation; CarPlay does
not create a phone controller. CarPlay disconnect releases its own interface/controller references.
Application termination disposes the shared owner and native resources.

At that checkpoint, the initial CarPlay adapter installed Apple's shared Now Playing template, using the application's
existing MediaPlayer integration rather than registering a second player or remote-command center.
The scene manifest, Xcode source lists and audio entitlement build setting are wired. This remains
an initial Now Playing/lifecycle surface: browsing, car-visible empty/connection/error presentation,
search, artwork acceptance and the full browse-to-play flow still need implementation/acceptance.
No CarPlay template was presented in a connected CarPlay simulator or physical head unit in this
checkpoint; compiling the delegate is not evidence that its connection callback was exercised.

Provider renewal was also detached from phone-view lifetime. Shared Core owns one maintenance job;
mounted views may await it, and cancelling a view's waiter leaves the runtime's job alive. Core
shutdown cancels that job. Native Android and iOS startup now delegate to the same common operation,
removing Android's duplicate host-owned scheduling condition. No renewal timing/retry policy was
added to either host.

### Verification results

- Six common runtime-owner tests passed natively on iOS Simulator; iOS ARM64 compilation passed.
- A native application-owner integration test passed against a disposable application-support
  directory: repeated scene acquisitions return the same real application, phone controllers are
  distinct, releasing them retains the runtime, and final shutdown rejects reacquisition.
- Shared connection/lifecycle tests passed on JVM and iOS Simulator (48 native cases). The added
  regression proves one renewal job survives scene-waiter cancellation and stops at runtime close.
- The full shared presentation JVM suite passed 627 cases, with no failures/errors/skips.
- Shared presentation compiled for Android and iOS ARM64. The final iOS application compiled for
  both simulator and device targets. Android application compilation and architecture checks passed.
- Xcode's complete Debug simulator build succeeded, including both Swift scene adapters and the
  generated Kotlin/Native framework. The built app installed and launched on a dedicated iPhone 17 /
  iOS 26.5 simulator, and its first-run phone connection UI rendered normally.

The native integration test initially exposed missing BASS/SQLite executable link settings and an
uncreated fixture directory; both were corrected in test configuration/setup. Test audio frameworks
are staged using the existing platform staging task. Final native startup and Xcode builds passed
after the accepted PR #605 merge. Tests used a dedicated simulator and disposable data, not the
owner's iOS profile. Apple account/provisioning approval, signed CarPlay-template presentation,
car-first/phone-first OS scene callbacks, process-kill recovery and physical head-unit acceptance
remain open. No Apple developer-account settings were changed.

Evidence under `build/issue-43/`: `runtime-owner-ios.log`, `runtime-renewal-tests.log`,
`runtime-renewal-ios.log`, `presentation-full-tests.log`, `presentation-test-summary.json`,
`native-owner-final.log`, `android-startup-compile.log`, `xcode-scene-build.log` and
`phone-launch-settled.png`. The Mac's matching worktree retains simulator/test XML and DerivedData.

### Native production-file boundary audit

| File | Concrete native boundary |
| --- | --- |
| `apps/ios/iosApp/NaviampApp.swift` | SwiftUI's UIApplication-delegate adaptor installs the process-lifecycle callback receiver. |
| `apps/ios/iosApp/NaviampApplicationDelegate.swift` | UIApplication termination and the native filesystem/Keychain resource factory connect to common ownership. |
| `apps/ios/iosApp/NaviampRootView.swift` | UIViewControllerRepresentable creates and releases per-phone UIKit controllers without closing Core. |
| `apps/ios/iosApp/NaviampCarPlaySceneDelegate.swift` | CPTemplateApplicationSceneDelegate and CPInterfaceController translate CarPlay connection/disconnection and present the system template. |
| `apps/ios/src/iosMain/kotlin/app/naviamp/ios/NaviampIosApplicationOwner.kt` | The Swift/Objective-C ABI exposes a concrete factory for the native SQL/audio application while delegating ownership policy to common code. |
| `apps/ios/src/iosMain/kotlin/app/naviamp/ios/NaviampIosApplication.kt` | UIKit controllers, CALayer references, native document presentation, SQL driver and audio-resource lifetimes require the iOS host. |
| `apps/android/src/main/kotlin/app/naviamp/android/AndroidNaviampApplicationRuntime.kt` | Android Context and playback-service bridge installation remain native; renewal startup now mechanically delegates to common Core. |

Xcode/Gradle, scene-manifest and entitlement changes are build/native registration metadata. The
new iOS test exercises the actual native application factory. No Desktop production file changed.

## Home, Playlists and Radio implementation — November 14, 2026

The scene now renders a native tab bar with Home, Playlists and Radio lists, replacing the initial
Now-Playing-only root. The common `NaviampVehicleBrowseController` obtains its rows and playback
commands from the same `NaviampCoreExternalPlaybackBridge` used by Android Auto. Home contains
recent tracks and recent albums. Playlists and Radio use the existing shared saved catalogs;
album/playlist selection starts collection playback, as on Android Auto. Collection detail pages,
full-library search, vehicle artwork, Up Next integration and Downloads remain follow-on work.

The common owner controls paging, runtime item/tab budgets, translated labels, empty/loading/error
presentation, source-bound revisions, cancellation and selection completion. Pages include shared
Previous/Next actions instead of silently losing rows beyond the native item budget. Playback
progress and visualizer observations do not rebuild the browse projection. Existing maintained
resource strings supply every label; no setting, persistence schema or translation entry was added.

Core now records selection-start generations and authoritative engine-ready/error observations.
A rejected/no-op collection request cannot succeed just because an old track remains playing.
The scene completes each native row handler once and pushes Apple's shared Now Playing template
only after the accepted selection reports ready audio. Source changes, scene detach, supersession,
failure and a shared 30-second readiness timeout complete pending handlers safely. Scene detach
retains the application runtime and any audio that has already started.

### Validation and remaining interactive gate

- Full shared presentation JVM suite: **634 tests**, zero failures/errors/skips. Android host
  compilation and `verifyCoreFirstArchitecture` passed in the same run.
- Affected JVM regressions passed again after the final status refinement: vehicle browsing,
  existing external-playback bridge and shared output routing.
- Native iOS regressions cover all three browse destinations through the real Core catalog,
  command routing, queue transactions and engine-observer readiness. This uses a controlled
  provider and playback-effect fixture, not a live Navidrome server or an audible native engine.
- Native iOS affected regressions: **35 tests**, zero failures/errors/skips (seven vehicle-browse,
  25 external-bridge and three output-routing tests). The real iOS application-owner test also
  passed with the new native session: packaged translations and three ABI tab projections arrive
  through Foundation's main run loop, invalid selection completes once, and repeated scene-session
  close retains the application. Its first compile exposed a missing Foundation extension import;
  the import was added and the test passed.
- iOS device compilation and Xcode simulator app build passed. The installed app launches and
  displays the normal first-run phone connection form on the dedicated simulator.
- The simulator build's `Naviamp.app-Simulated.xcent` contains the CarPlay audio entitlement and
  application identifier. The regular simulator codesign entitlement dictionary is empty; it must
  not be confused with the simulator-embedded entitlement payload or distribution approval.
- Interactive CarPlay acceptance is **not passed**. This Mac's Xcode 27 installation provides
  `DeviceHub.app`; no `Simulator.app` or standalone CarPlay Simulator was found in the inspected
  Xcode locations. Enabling external screen 3 through `simctl io ... screenConfig` and launching
  `com.apple.CarPlayApp` produced a black surface, without a connected application template scene.
  A black surface is not evidence that our template rendered. A request to activate a suitable
  CarPlay-capable simulator is pending with the owner. No Mac accessibility permission or Apple
  developer-account configuration was changed.

Logs and captures: `build/issue-43/vehicle-common-tests.log`, `vehicle-regression-tests.log`,
`vehicle-regression-summary.json`, `vehicle-final-common.log`, `vehicle-common-ios.log`,
`vehicle-core-integration-ios.log`, `vehicle-core-final-ios.log`, `vehicle-final-native.log`,
`vehicle-session-native.log`, `vehicle-xcode-build.log`, `vehicle-xcode-final.log`,
`vehicle-phone-settled.png`, and `carplay-after-install.png`. The black CarPlay capture records the
environment limitation only. Native test XML and DerivedData remain in the matching Mac worktree.

### Native production-file audit for this slice

| File | Concrete native boundary |
| --- | --- |
| `apps/ios/iosApp/NaviampCarPlaySceneDelegate.swift` | CarPlay scene callbacks, CPListTemplate/CPTabBarTemplate/CPInterfaceController and UIKit symbols render common state and translate native handler lifetimes. |
| `apps/ios/src/iosMain/kotlin/app/naviamp/ios/NaviampIosApplication.kt` | The Swift/Objective-C factory ABI binds a scene callback to the existing native application's main-dispatcher scope. |
| `apps/ios/src/iosMain/kotlin/app/naviamp/ios/NaviampIosVehicleBrowseSession.kt` | Objective-C-compatible DTOs and callback signatures translate Kotlin common models and release the native scene's observation lifetime. |

No Android or Desktop production file changes in this slice. Product behavior remains in common
Kotlin; Swift has no provider, catalog-loading, source-selection, paging or playback-readiness policy.
The public-source interface comparison and design rationale are in
[`43-carplay-interface-research.md`](43-carplay-interface-research.md).
