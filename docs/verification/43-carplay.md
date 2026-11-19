# Issue 43 — Apple CarPlay

## Back from Up Next — November 19, 2026

Core distinguishes Up Next from the Queue entry on Home. Up Next sits above Now Playing, so
native Back removes only the queue page. A successful queue selection closes Up Next after
playback is ready. The Kotlin iOS session passes Core’s placement through its Swift ABI, and
the Swift CarPlay scene places Apple’s shared Now Playing template at the reported point.

On the iOS 26.5 CarPlay simulator, “Project Wildfire” was paused at 0:45 before opening Up
Next. Tapping Back from Queue returned directly to Now Playing at 0:45 with Play still visible.
The same app process survived, and a read-only database comparison confirmed the same ordered
six-track queue, current index and exact saved position (44.6893 seconds). The simulator was
left open on paused Now Playing. All checks stayed local.

On the same build, selecting “On The Run” from Up Next returned directly to CarPlay Now Playing
and started the selected track. Pausing from CarPlay left both CarPlay and the simulated iPhone
on “On The Run” at 0:16 with the Play button visible.

106 focused shared JVM tests and seven Swift adapter tests passed. Shared Android and iOS
compilation, the Core architecture check, and the signed simulator app build also passed.
Local evidence is under `build/issue-43/` in the `up-next-back-*` logs and checkpoints.

## Up Next crash fix — November 18, 2026

The simulator log identified an `NSInvalidArgumentException` from passing a
`CPTabBarTemplate` to `CPInterfaceController.popToTemplate`. The Swift adapter now uses
`popToRootTemplate` for the root transition and keeps targeted pops for detail templates.
Core continues to supply the desired queue and navigation path.

All six Swift adapter XCTest cases passed on Xcode 26.6 / iOS 26.5, including the Up Next
test with the fake stack enforcing the SDK's tab bar restriction. The app built with the
simulated CarPlay audio entitlement and was installed over the existing test data. On the
actual CarPlay display, Now Playing showed “KDR Assault” paused at 0:12. Tapping Up Next
opened Queue with “KDR Assault,” “Project Wildfire,” “On The Run,” and “A New Galaxy” in
order. The same app process survived, and the saved six-track queue and position matched
the pre-fix checkpoint. The simulator was left open on Queue.

Local evidence in `build/issue-43/`: `up-next-fix-build.log`, `up-next-fix-tests.log`,
and `up-next-fix-session.json`.

## Live Up Next Back and selection — November 18, 2026

On the fixed build, Back from the queue opened through Up Next returned to CarPlay Home.
Reopening Now Playing still showed “KDR Assault” paused at 0:12. Opening Up Next again and
selecting “Project Wildfire” started that song, and CarPlay opened Now Playing after playback
became ready. The iPhone showed “Project Wildfire” playing as well. Pausing from the iPhone
left both displays on “Project Wildfire” paused at 0:45. The simulator remained running on
CarPlay Now Playing. This was a local live simulator check.

## Focused live playback check — November 18, 2026

On the iOS 26.5 simulator, the phone and car initially showed “Tokyomatrix 3000.” Next on the
phone advanced both to “Smile (Make Your Body Move).” Next on the CarPlay screen advanced both
to “KDR Assault.” The earlier track mismatch did not recur in these two transitions.

Tapping the top-right Up Next button from CarPlay Now Playing then aborted Naviamp and returned
to the CarPlay launcher. The recorded native stack shows `CPInterfaceController.popToTemplate`
called by `NaviampCarPlayTemplateNavigation.reconcile`. A prior local crash report has the same
native stack. Home → Queue was usable and served as the alternate path for checking restoration.
The cause of the template exception needs investigation before CarPlay is ready to ship.

After restarting from that crash, CarPlay Home → Queue showed “KDR Assault,” “Project Wildfire,”
“On The Run,” and “A New Galaxy” in order. The track was paused at 0:12. An explicit CarPlay
disconnect/reconnect kept the same Naviamp process and restored the same visible queue and paused
position. After separately terminating the app and opening it from the CarPlay launcher, a new
Naviamp process showed the same queue order and Now Playing at 0:12 with the Play button visible.
A read-only check of the playback database found identical complete six-track queue order,
current index and saved position (11.6866 seconds) at the baseline, after reconnect and after
cold launch. All checks were local and the simulator was left open on paused Now Playing.

Local evidence in `build/issue-43/`: `carplay-local-before-reconnect.json`,
`carplay-local-after-reconnect.json`, `carplay-local-after-cold-start.json`, and
`carplay-local-up-next-crash.txt`.

## Xcode 26.6 simulator trial — November 18, 2026

Visual smoke check passed on the follow-up run. The owner opened and used CarPlay, and a direct
capture of the live CarPlay window showed Naviamp on Now Playing with “Wedding March for a Bullet,”
artist and album metadata, playback position, previous/play/next, Up Next, and Naviamp’s four
custom controls. This confirms the app can open and render its CarPlay UI with Xcode 26.6 and
iOS 26.5 on this Mac. The owner reported that the interface was responsive and accepted the lack
of a separate cover art tile in this simulator layout. A wider output window retained a compact
CarPlay viewport, so artwork placement at a genuinely wider layout remains unverified.
Exhaustive control, audible output and physical CarPlay acceptance remain separate checks.
The simulator was left running for the owner.

The owner reported that phone play/pause and Next controlled playback while CarPlay displayed
a different track. Direct inspection confirmed cover art in Naviamp’s phone player, no cover art
in the observed CarPlay Now Playing screen, and CarPlay’s Show Album Art in Now Playing setting
enabled. The cover and the earlier stale CarPlay item were not a verified match.

For the follow-up on “Balrog Boogie,” Naviamp and CarPlay displayed the same track. A local
debugger read Naviamp’s live `MPNowPlayingInfoCenter.nowPlayingInfo`: title “Balrog Boogie,”
artist “Diablo Swing Orchestra,” album “The Butcher’s Ballroom (2009),” and duration 233.082
seconds. The published `MPMediaItemArtwork` returned a valid 400 × 400 image matching the phone
cover. CarPlay displayed a similarly colored blurred background without a separate cover tile.
This confirms that Naviamp supplied the cover for that item and is consistent with CarPlay
using it as a background on this display. It does not resolve the earlier stale-track report.
The debugger detached and the simulator remained running. Local artifacts are
`build/issue-43/now-playing-ios-track-probe.log` and
`build/issue-43/now-playing-ios-published-cover.png`.

Installed Xcode 26.6 (17F113) from Apple Developer Downloads alongside Xcode 27.2. On this
macOS 27.0.1 host, Xcode 26.6 found the existing iOS 26.5 runtime (23F77) and built Naviamp
locally with ad hoc simulator signing. The simulated entitlement includes
`com.apple.developer.carplay-audio=true`. The installed executable matched the build and
`simctl launch` returned a Naviamp process ID after a simulator restart.

Simulator's I/O → External Displays → CarPlay opened the CarPlay window and exposed a display
whose screen type was CarPlay. The owner saw app icons in the CarPlay window during this trial,
confirming that the system launcher rendered. The automated inspection saw a black window at
one point, and its display capture timed out. After restarting the device and enabling CarPlay
again, the automated UI inspection timed out before the successful follow-up documented above.
The device was shut down after the initial trial and Xcode 27.2 remained the system default.

Local evidence: `build/issue-43/xcode26-carplay-build.log`,
`build/issue-43/xcode26-carplay-system.log` and `build/issue-43/xcode26-carplay-final.log`.

## Swift template adapter — November 18, 2026

Six focused XCTest cases passed on the dedicated iOS 26.5 simulator. They cover rejected root,
push and pop requests, Back from a detail or tab, Up Next handing navigation to Core, and stale
success/failure completions and observer events after reconnect with the same interface. The tests
use real CarPlay template objects and a controllable substitute for CPInterfaceController's stack.
The unsigned Naviamp Debug simulator app also built successfully. Results are in
`build/issue-43/template-adapter-tests.log` and `build/issue-43/template-adapter-xcode.log`.
Live CarPlay rendering, input, and actual system callback ordering remain pending.

The native scene delegate installs and removes a separate observer for each attachment and bridges
Core callbacks. The Swift navigation adapter serializes CPInterfaceController operations and
translates stack and Now Playing observer events. Core continues to own navigation and failure policy.

## Lifecycle local status — November 17, 2026

CarPlay lifecycle and playback resilience are implemented locally, alongside connection recovery,
saved-source selection, browse artwork, Downloads/offline browsing, Search, Library, playback
controls, Queue / Up Next and collection details. Phone and car scenes share one headless runtime;
reconnecting scenes observe its current queue/progress, and interruption recovery respects newer
phone/car playback intent. Scene release disposes presentation work without stopping the player.
Audio Search remains gated to iOS 27+.

Final validation passed **755 presentation JVM tests**, **364 shared native iOS presentation tests**,
**12 runtime/session ownership tests on each of JVM and native iOS**, and **two real iOS application
owner/session integration tests**: **1,145 executions**, with no failures/errors/skips. Shared
Android/JVM/iOS and iOS host compilation, the architecture guard and unsigned Xcode Debug simulator
build passed. All **91** feature source/build/resource/test hashes match the frozen validation
snapshot. This increment changes two native production adapters, justified in the table below.
Native execution used the installed iOS 26.5 runtime; visual/template, audible and physical CarPlay
acceptance remain pending. The dedicated test simulator is verified Shutdown. Nothing was committed,
pushed or sent to hosted CI.

## Lifecycle, reconnects and playback resilience — implementation and local validation

Core's existing lazy application owner remains the single runtime for phone and car scenes. Saved
connection restoration already starts from Core creation without mounting the phone's Compose UI.
Core now has an owned child runtime scope: closing it cancels startup restoration and background
work without cancelling the caller's application/window scope. The command router cancels admitted
work, rejects commands after closure, and treats coroutine cancellation as normal shutdown rather
than an asynchronous product failure. Command admission closes before native audio resources are
released. Existing Core fixtures now explicitly close their runtimes inside the test lifetime.
The provider-renewal owner remains idempotent across repeated surface acquisitions.

`NaviampPresentationSessionOwner` owns asynchronous presentation creation and consumption. A scene
closed before creation does not construct its controller; a controller returned after closure or
cancellation is disposed instead of adopted. Observer cancellation also disposes its presentation.
The common owner handles exactly-once disposal, failure/retry and final closed state. The iOS browse
ABI delegates to this owner, so scene release cancels only presentation work and never the shared
player/runtime. Old sessions reject input and cannot publish into later attachments.

The common external playback lifecycle coordinator now binds interruption resume to source/account
and playback-selection generations, media/queue occurrence, sleep-timer activity and the latest
playback intent. Intent is stamped at command admission before deferred execution, including an
explicit pause while already paused. Volume, repeat/shuffle and ordinary metadata updates do not
create a transport-resume veto. Output selection does. An interruption resumes only if it originally
paused local playing media, the same context is still paused, no new transport/source intent has
superseded it and the OS permits resume. Duplicate callbacks cannot rearm a changed track or resume
twice. Remote outputs and pending connections do not inherit local audio-session resume authority.
Output loss pauses local playing/paused audio (cancelling engine resume intent). Loading audio is
stopped on interruption/output loss even though ordinary Play/Pause is disabled, preventing that
pending load from starting later. Closing the coordinator rejects queued lifecycle callbacks.

Now Playing artwork lifetime is also common-owned. Source/account changes invalidate artwork even
when media IDs and URLs are identical; late bytes from a prior source or closed owner cannot replace
current artwork. Progress, metadata-only changes and transport-intent stamps do not reload unchanged
images, and unchanged artwork identity does not request fresh full playback snapshots on progress
updates (asserted across 100 updates in the common test). This is a work-avoidance regression
check, not a visible-window CPU or animation measurement. The shared publication planner republishes content/control state on source/output changes
without rebuilding native metadata for ordinary progress ticks. Native playback resource code now
only decodes bytes into UIImage/MPMediaItemArtwork and applies MediaPlayer effects. Seek, shuffle and
repeat command availability use Core capability facts.

New common cases cover asynchronous presentation ownership, phone-first/car-first construction,
headless saved-session restoration once per runtime, repeated car detach/reattach, current phone
queue/progress on reconnect, stale scene input, explicit phone pause/queued stop during interruption,
loading cancellation, output selection, normal resume, source-bound artwork, command cancellation
and shutdown during non-cooperative bootstrap work. The inherited provider session adapters retain
their pre-commit cancellation checks. Returning a value from a fake after cancellation is not proof
that real provider authentication/persistence has committed.

Native production accountability for this increment:

| File | Concrete native boundary |
| --- | --- |
| `apps/ios/src/iosMain/kotlin/app/naviamp/ios/NaviampIosVehicleBrowseSession.kt` | Objective-C/Swift callback observation is tied to its native scene lifetime, while the common presentation owner handles creation/disposal policy. |
| `platforms/ios/src/iosMain/kotlin/app/naviamp/ios/playback/IosNativePlaybackIntegration.kt` | AVAudioSession notifications, MediaPlayer registrations/availability and UIImage/MPMediaItemArtwork resources require Apple APIs; lifecycle and artwork decisions delegate to Core. |

The real iOS owner tests cover car-first acquisition, phone view-controller acquisition first,
immediate scene closure, twelve further scene cycles, release of the phone controller while the
runtime remains shared, closed-session callback suppression and final shutdown. These are native
factory/ABI/resource tests, not a visible CarPlay connection or an audible playback test. No strings,
settings/export/sync contract, database schema or migration changed in this increment.

Final local validation passed 755 presentation JVM tests, 364 selected native presentation tests,
12 common runtime/session ownership tests on each target, and two native application-owner tests.
There were no failures, errors or skips. Android and iOS common compilation, iOS host compilation,
`verifyCoreFirstArchitecture` and the unsigned Xcode Debug simulator build passed. All 91 frozen
source hashes match after validation; HEAD remains unchanged and the dedicated simulator is Shutdown.
Evidence is stored locally under `build/issue-43/`: `lifecycle-validation.log`, `lifecycle-xcode.log`,
`lifecycle-test-counts.json`, `lifecycle-final-summary.json`, `lifecycle-final-source-manifest.json`,
`lifecycle-final-state.json`, `lifecycle-simulator-state.json` and `lifecycle-whitespace.log`.

Visual/template and physical acceptance remain pending: actual car-first OS scene callbacks, phone/car concurrency and UI
synchronization, wired/wireless reconnection, locked-phone operation, interruption/route timing,
Siri/remote commands, audible playback, process termination/relaunch and large-catalog startup.
No older Xcode, additional runtime, developer-account change or physical phone setup was introduced.
All implementation and testing remain local under issue 43.

## Connection recovery and saved-source selection — implementation and local validation

Core now supplies a Connections entry on Home and a scene-owned saved-source picker. Recovery is
promoted ahead of music shortcuts when the selected source is disconnected, failed or known offline;
healthy connections follow the existing playback/browse shortcuts. A current saved source also gets
a direct Home Reconnect action during recovery, plus an explicit Reconnect row inside the picker.
Current accounts are marked and cannot be selected redundantly. Existing shared pagination exposes
every saved source within the host item budget. A disabled translated hint directs setup and
credential changes to the phone. No credential form, discovery, account creation or deletion is
exposed in the car. Rows show saved display names/usernames and never the stored server URL or raw
provider error. The labels and IDs are resolved in Core, independently of phone navigation.

`Connection.SelectSavedSource(id, reconnect)` and `SavedSourceSelected` provide a typed, reusable
in-place connection contract. The connection owner validates the actual saved inventory and rejects
blank/missing identities, concurrent connection attempts, remote Connect authority and reconnect
requests for a source that is no longer current. Selecting an already-current connected source is
idempotent; Reconnect explicitly validates its saved credentials again. The existing shared provider
session path validates the candidate while retaining the previous session and its data. Failed,
cancelled or timed-out attempts restore the previous connection and do not open the phone's account
error UI. Editor/form values and account-switcher state are preserved. No new provider protocol or
host-specific authentication behavior was added. The four inherited provider source/test files are
hash-verified unchanged from the accepted local Search snapshot.

A successful source change uses the existing common source-reset transaction and increments source
and account generations. It refreshes the common catalog and may restore the target's saved queue,
but the shared playback restoration contract now accepts `allowAutoPlay = false`. This applies to
both track and internet-radio restoration even when the saved play-on-launch preference is enabled.
The source picker neither navigates the phone Home nor reopens its saved Now Playing overlay.
Same-source recovery refreshes connection/catalog data without restoring or replacing the active
queue, resetting its progress, or releasing its downloaded-only source intent. Pending connection
queue-restoration jobs are cancelled by subsequent source transitions; cancelled restoration checks
its coroutine before applying a loaded plan.

The source picker has its own shared operation state and 30-second presentation deadline. Duplicate
selection is disabled immediately. Native row selection completes immediately for these connection
actions and never requests Now Playing. Failure permits another explicit attempt or source choice;
there is no new automatic retry loop. Back, navigation away, root presentation failure, source
identity removal/change, remote-authority takeover and scene detach cancel pending scene work.
Late results cannot replace a newer attempt's state. Existing provider session ports retain their
pre-commit cancellation checks; the common connection controller also checks cancellation before
publishing a returned session. A provider that ignores cancellation can delay its underlying I/O
unwind; the UI does not admit a second global connection while Core still reports one in progress.
The picker survives its own connecting/source transitions while stale media pages and artwork are
invalidated through the existing common generation rules.

`NaviampShellConnectionUi.offline` now projects the runtime connection phase explicitly. This avoids
parsing status text or mistaking the retained offline session's `connected = true` for online
availability. The runtime flag is unrelated to the user's Offline Mode preference. Two new English
keys (phone setup guidance and Offline), plus missing translations for four reused keys, are present
in all 17 catalogs. The final vehicle-label audit covers all 38 referenced keys. No settings export,
sync contract, schema or migration changed.

New common coverage includes 12 vehicle connection cases and five connection-owner cases. It checks
safe saved identity/current markers, absent/remote sources, single-flight operations, retry,
inventory/authority/source invalidation, late completion/deadline handling, direct offline recovery,
stale native selections, large paged inventories, Back/navigation-away/detach cancellation and real
Core composition. The real composition case covers track and internet-radio saved queues with
play-on-launch enabled, verifies no automatic start/phone navigation, and proves offline recovery
keeps the existing queue and progress.

Native production audit: **no Android, Desktop or iOS production files changed for this increment**.
The existing generic CarPlay page, row, enabled-state and callback ABI is sufficient. The native
application-owner test now opens the translated Connections page with an empty real application
inventory, verifies setup guidance/disabled rows and harmless invalid selection, and retains the
same application across phone/car scene acquisition and detach.

Final validation passed 736 presentation JVM tests, 320 shared native tests and one real iOS
application-owner/session test, with zero failures/errors/skips. Android/iOS common compilation,
iOS host compilation, the architecture guard and unsigned Xcode simulator build passed. Evidence
under `build/issue-43/`: `connections-validation-common.log`, `connections-validation.log`,
`connections-xcode.log`, `connections-final-summary.json`, `connections-final-source-manifest.json`,
`connections-translation-audit.json`, `connections-whitespace.log` and `connections-final-state.json`.
All 80 feature snapshot hashes match and HEAD is unchanged. The dedicated test simulator is verified
Shutdown. No commit, push or hosted CI was performed. Visual/template, audible and physical
acceptance remain pending: verify recovery discoverability, current-source markers, duplicate account
names, long/large source lists, touch/rotary/VoiceOver, slow/unreachable/authentication/TLS failures,
explicit retry, cancellation by Back and phone source changes, recovery during offline playback,
source changes with saved queues and play-on-launch enabled, car-first/phone-first launch, reconnect
and phone editor/account UI behavior. No older Xcode, additional runtime or physical phone setup was
introduced. All implementation, builds and tests stay local under issue 43.

## Browse artwork — implementation and local validation

Core now carries explicit media-kind artwork identities through Home/recent tracks and albums,
Playlists, internet Radio, Up Next, collection tracks, Library/favorites, provider Search previews
and result pages, and downloaded artists/albums/tracks. Control/category/paging rows do not gain
media artwork. Artist portraits and missing-portrait placeholders are circular; album/track and
collection images keep a square silhouette. The shared Compose artwork shape policy and vehicle
shape descriptors use the same `mediaArtworkIsCircular` policy. Identity is never inferred from
a title, URL or containing page. Downloaded artist rows have circular placeholders because saved
track metadata has no artist portrait URL.

The shared artwork source reuses `ImageCacheRepository.imageBytesForProvider`, provider stable
cache keys, `bytesForOwnedUrl` authentication and the initialized shared HTTP effect for public
images. Artist portrait URLs come from the provider's artist metadata. Downloaded rows use only
`cachedImageBytes`; no artist-metadata or image-network fetch is allowed from those rows. Without
a current provider handle, or without cached bytes, they retain placeholders. This adds no
transfer policy, download reconciliation, settings/schema change or phone navigation.

A scene-owned common request owner deduplicates image work and limits concurrent reads to three.
It retains at most 8 MiB of successful byte results, rejecting empty responses and individual
images larger than 2 MiB. Opaque identifiers prevent native row DTOs from carrying authenticated
URLs. Source/account generations and projected row lifetimes invalidate requests; page removal,
query replacement, detach and source changes complete callbacks safely and reject late bytes.
A five-second sibling deadline completes callbacks even when a provider ignores cancellation.
Failures retain placeholders for the active artwork identity. Rows and selection completion never
wait for artwork; image callbacks do not advance browse selection revisions or restart playback.

The native renderer uses ImageIO thumbnail decoding, UIKit aspect-fill/clipping and transparent
output, then calls `CPListItem.setImage`. It obtains dimensions from
[CPListItem.maximumImageSize](https://developer.apple.com/documentation/carplay/cplistitem/maximumimagesize)
and scale from the connected interface controller's car trait collection. Native Search results
use the same renderer. No UI strings or resource catalogs changed in this increment.

Native production accountability for this increment:

| File | Concrete native boundary |
| --- | --- |
| `apps/ios/iosApp/NaviampCarPlaySceneDelegate.swift` | CarPlay row images, UIKit raster drawing and ImageIO thumbnail decoding require Apple SDK APIs. |
| `apps/ios/src/iosMain/kotlin/app/naviamp/ios/NaviampIosVehicleBrowseSession.kt` | Objective-C/Swift row projection and Foundation NSData byte transport bridge common artwork results to CarPlay. |
| `apps/ios/src/iosMain/kotlin/app/naviamp/ios/NaviampIosApplication.kt` | The existing native application composition supplies its initialized image cache and HTTP effect to common Core artwork services. |

Thirteen new common artwork tests cover explicit kinds/shapes, opaque identities, deduplication,
cache eviction under queued and eager coroutine dispatch, concurrency, malformed byte-response policy, timeout/late completion, source
invalidation, authentication, provider-stable cache keys, cached-only offline access, provider
portrait lookup, browse revision stability, Search mapping, downloaded mapping and cancellation when paging hides rows. The native
application-owner test also verifies missing/closed-session artwork callbacks complete once.

A local simulator application executes the production Swift `renderArtwork` method body extracted
from the scene delegate. Seven pixel checks cover square and circular image alpha,
aspect-fill output, bounded 1x/2x dimensions, circular/square placeholders, invalid-image fallback
and invalid dimensions. The first command-line probe waited on UIKit screen initialization; it
was stopped and replaced by a proper UIApplication lifecycle probe. Only that application-backed
run is passing evidence. Probe sources, app and output are under `build/issue-43/` as
`ArtworkRendererProbe.swift`, `ArtworkRendererProbe.app` and `artwork-renderer-probe.log`.

Final local validation: 719 presentation JVM, 204 shared native, one real iOS owner and two shared
UI shape tests passed, with zero failures/errors/skips. All seven native renderer pixel checks
passed. Android/iOS common compilation, iOS host compilation, the architecture guard and unsigned
Xcode simulator build passed. Evidence under `build/issue-43/`: `artwork-final-common.log`,
`artwork-validation.log`, `artwork-ui-shape.log`, `artwork-xcode.log`,
`artwork-renderer-probe.log`, `artwork-final-summary.json`, `artwork-final-source-manifest.json`,
`artwork-whitespace.log` and `artwork-final-state.json`. All 72 frozen hashes match and the pixel
probe's renderer body matches production. Earlier logs ending in `before-paging` or `before-cache-fix`
are development checkpoints, not the final source acceptance. One pre-final Xcode build was
intentionally stopped to complete the eager-dispatch cache fix. The final build passed. The temporary
probe app is removed and the dedicated test simulator is verified Shutdown. No commit, push or
hosted CI was performed. Visual/native-template acceptance
remains pending: inspect artwork placement, row identity during scrolling/paging/search, image
quality at car display scales, slow/offline/authentication failures, source/account switching,
reconnects, contrast and VoiceOver. Pixel tests do not establish actual CarPlay rendering or a
visible real-window performance measurement.

## Downloads and offline browsing — implementation and local validation

Core now supplies a read-only Downloads browser with Artists, Albums and Tracks derived from saved
track metadata. Downloads is the fifth tab when the host's tab budget allows it; Home and Library
also expose it within smaller tab budgets. It is available for a stable selected source with a
local playback capability, including when no provider session exists or the connection reports an
offline error. Connect authority and Cast output do not expose local-download actions. Browse groups
contain only completed records with verified regular files and positive recorded sizes. Partial
albums contain only their available downloaded tracks, ordered using saved disc/track metadata,
and carry the translated Downloads status rather than claiming the full provider album.

`NaviampCoreDownloadStoragePort.availableDownloads` explicitly distinguishes a verified list from
an unverifiable host (`null`). The existing repository-backed storage service implements that
contract through each host's existing native regular-file predicate; Android/Desktop/iOS native
filesystem production code did not need changes. Browsing does not invoke transfers, policy/job
restoration or reconciliation, pruning, removals, or the phone's Downloads screen. The verified
scene snapshot is source/account scoped and reused for paging. Refresh rescans it; every playback
selection independently revalidates availability and source before dispatch. This prevents stale
rows, deleted files, late reads, source switches and duplicate/zero-byte metadata from starting
unavailable or another source's audio. Unverifiable storage has an explicit Unavailable state;
empty storage and failures remain distinct, with Refresh or Retry as appropriate.

Play, Shuffle and individual track actions use the common queue transaction and fresh engine
readiness. Local album queues retain the shared album playback-profile target. The explicit
`playDownloadedQueueSelection` effect enters a downloaded-only queue intent in the one shared
engine adapter. It overrides Prefer server only for that queue: current and prepared-next audio
resolve exclusively from verified downloaded assets. Audio cache/network fallback, network audio
prefetch and server transcoding recovery are not used for this intent. Missing files/codecs fail
rather than silently streaming. Up Next selection, previous/next, repeat, shuffle and queue updates
retain the source intent through `selectExistingQueueItem` and normal queue effects. The Now Playing
album button routes downloaded-only playback to the local album view, without requiring a provider
session. Reopening that album reuses the existing page instead of duplicating the native stack.

An ordinary new queue selection restores normal saved source policy. A successful explicit radio
refresh uses `applyProviderQueue` to change upcoming policy without restarting its retained seed;
failed/empty radio builds leave the downloaded-only queue unchanged. Saved playback preferences,
settings export/import and persistence schemas did not change. This is a runtime queue intent;
saved-session restoration continues to follow the existing restoration/preference policy rather
than introducing a new persisted setting. Tests verify requested file URIs and engine contracts;
they do not establish audible native output or full-application background network silence.

Native production audit for this increment: only
`apps/ios/iosApp/NaviampCarPlaySceneDelegate.swift` changed, mapping the shared Downloads identifier
to a UIKit/CarPlay tab image. Generic destination/page DTOs, callbacks and native stack effects were
already sufficient. The real iOS owner test additionally exercises a translated five-tab projection,
rejects invalid Downloads rows with no source, and verifies scene detach retains the application.

The Downloads label reuses the shared settings key. The final catalog audit also found and filled
pre-existing missing translations for shared Home, Playlists, connection/empty/loading status and
Previous/Next labels used by these browse pages. No new English UI keys were introduced. All shared
vehicle label keys are audited against every maintained catalog; source and resource hashes are
recorded with the final results below.

Final results: 1,148 domain JVM, 706 presentation JVM, 191 shared native and one iOS owner test
passed; common/host compilation, architecture guard and unsigned Xcode build passed. Evidence is
under `build/issue-43/`: `downloads-validation.log`, `downloads-xcode.log`,
`downloads-final-summary.json`, `downloads-final-source-manifest.json`,
`downloads-translation-audit.json`, `downloads-whitespace.log` and `downloads-final-state.json`.
All 68 frozen source/resource/build/test hashes match; all 31 vehicle label keys exist in all 17
catalogs. The dedicated test simulator is verified Shutdown. Visual/template and physical acceptance
remain pending: verify tab/fallback discoverability, downloaded groups and partial albums, large
catalog paging/refresh, real local-file playback with the server unreachable, missing-file/codec
errors, Up Next/repeat/shuffle transitions, radio transitions, source/account changes, reconnect,
accessibility and saved-session behavior. No transfer or download-management UI was added to CarPlay.

## Search — shared implementation and local validation

Search is a Home and Library entry when the host reports an eligible search-input capability,
a source is available, and the item budget is adequate. Apple's current
[CarPlay Developer Guide](https://developer.apple.com/download/files/CarPlay-Developer-Guide.pdf)
permits the Search template for audio apps beginning with **iOS 27**. The Swift host reports that OS
fact to Core and avoids creating the template on older systems. The app's deployment target is
unchanged. The installed iOS 26.5 runtime can exercise common search and the ABI/session contracts;
it does not establish native audio Search-template acceptance on iOS 27.

Core normalizes and debounces queries with the existing shared search policy. Artists, albums and
tracks use the selected provider's paged search APIs through `ApiCatalogService`, independently of
the phone's Search query, Library filters, recents cache and navigation. A bounded mixed preview
contains links to all three media categories; selecting one loads the full paged query results
through the existing Library browser, preserving offset/continuation tokens. Artist results open
releases, albums open their existing detail, and tracks use shared queue playback and engine readiness.
The keyboard Search action waits for the current query and promotes its results to a list page.
The existing three-detail-page policy reserves room for the root and system Now Playing.

Query-specific revisions reject old-query selections while retaining valid results across unrelated
Now Playing metadata changes. Query replacement/clear, source or authority changes, Back, root
failure and detach complete pending SDK callbacks exactly once before cancelling work. Late provider
results cannot clear a newer query. A sibling deadline completes the callback with a translated
failure even when the provider ignores coroutine cancellation; late completion cannot publish.
Empty queries avoid network calls; empty results, partial failure and timeout have shared translated
status rows that cannot be selected. Category links remain available for paged searching/retry.
CarPlay owns its system keyboard and vehicle input restrictions; no custom input or phone dialog
was introduced. Search is supplementary to the existing browse paths.

Native production placement audit:

- `apps/ios/iosApp/NaviampCarPlaySceneDelegate.swift`: maps common search pages to `CPSearchTemplate`,
  implements the SDK's text/result/keyboard callbacks and completions, binds native result-item
  identities to common query revisions, applies `CPListItem.isEnabled`, and reports OS availability.
  Native template references and SDK callback/resource lifetimes cannot live in common Kotlin.
- `apps/ios/src/iosMain/kotlin/app/naviamp/ios/NaviampIosVehicleBrowseSession.kt`: exports search page,
  row and completion DTOs across Objective-C and forwards search text, selection and submit calls.
- `apps/ios/src/iosMain/kotlin/app/naviamp/ios/NaviampIosApplication.kt`: extends the Swift ABI factory
  to forward the host's search capability fact to the shared scene factory. It adds no product policy.

The Search title reuses an already complete resource key; Search music and No matches reuse existing
keys completed across all **17** maintained catalogs. Provider implementations, settings, database
schemas and playback engines did not change in this increment. No artwork or microphone handling
was added.

**690 presentation JVM tests**, shared Android/iOS compilation and the architecture guard passed
before native source changes. The 11 new common tests cover capability gating, normalized/debounced
provider queries, blank input, replacement/clear and cancellation-resistant providers, timeout and
partial failure, exact callback completion, stale query taps, full-category pagination, preview
navigation/playback readiness, keyboard submit and item budgets. Evidence:
`build/issue-43/search-common.log` and `search-ios-host.log`.

Final local native validation passed **131 presentation native tests** (including all 11 search
cases) and **one real iOS application-owner/session integration test**, with no failures/errors/skips.
The native owner exercises the new search completion/selection/submit ABI and disconnected-source
capability gating. The unsigned Xcode Debug simulator app build passed. No new Swift diagnostics
were reported; the existing ICU simulator deployment-target and unused AppIntents metadata warnings
remain. No new simulator or runtime was installed.

Evidence:

- `build/issue-43/search-native.log`: shared native regressions and the real iOS owner/session test.
- `build/issue-43/search-xcode.log`: passing unsigned SDK/Swift/framework/app build.
- `build/issue-43/search-whitespace.log`: no findings with CRLF-aware checking.
- `build/issue-43/search-final-summary.json`: exact counts and compilation/build/compatibility status.
- `build/issue-43/search-final-source-manifest.json`: hashes for all 57 changed/untracked source,
  build, test and resource files, matching the frozen native-validation snapshot through the final
  app build.
- `build/issue-43/search-final-state.json`: confirms the dedicated test simulator is shut down.

Visual acceptance remains pending, including actual iOS 27 audio entitlement/template behavior,
keyboard/input restrictions, results, Back/submit transitions, VoiceOver, and
phone/car/source/disconnect concurrency. Native common tests on iOS 26.5 and an SDK build are not
substitutes for those checks. Nothing was committed, pushed or sent to hosted CI.

## Library browsing — implementation and local validation

Core now provides a scene-owned Library browser for Artists, Albums, Tracks and Favorites.
Library is the fourth tab when the runtime tab budget allows it; otherwise Home exposes a Library
entry. Favorite categories include only the source's supported artist/album/track capabilities.
Artists lead to paged primary releases, albums lead to the existing Play/Shuffle/track detail, and
track rows play the selected track through the existing Core queue transaction and engine-readiness
policy. Browsing does not mutate the phone's Library filters, search, detail, or navigation state.

`NaviampCoreVehicleLibraryAccess` reuses `ApiCatalogService`, the selected provider source, and shared
queue ownership. Catalog pages carry the provider's offset/continuation, fit the native item budget,
and expose Previous/Next rather than silently truncating. Visited pages can be revisited without
another fetch. Metadata mismatches, nonadvancing pages, repeated identities and contradictory total
counts fail visibly with explicit Retry. Source/account/connection/authority changes, Back, selection
supersession and scene detach cancel scoped work; late results and stale handlers cannot update or
play another source. Unsupported enumeration has a translated Unavailable state distinct from an
empty collection or a retryable failure. Remote Connect authority does not expose local-source
Library actions. Download-only browsing, artwork and full-library search are separate work.

A common `favoriteTracksPage` provider contract was added because the old convenience list has a
limit and cannot establish complete enumeration. Navidrome/OpenSubsonic uses the existing cached,
folder-filtered starred snapshot; Jellyfin uses server-filtered favorite Audio pages with the
existing folder/offset handling. Existing artist/album favorite page contracts are reused. Album
details now enumerate `albumTracksPage` to completion where available, retaining the existing detail
fallback for providers without that contract, and preserve shared album ordering and playback.

Core retains at most three detail pages, reserving space for the root and system Now Playing within
[Apple's five-template audio-app limit](https://developer.apple.com/download/files/CarPlay-Developer-Guide.pdf).
Older ancestors are removed when deeper navigation needs room. The native adapter reconstructs
Core's retained path one SDK push/pop at a time, preserving Back destinations after trimming and
avoiding treating intermediate programmatic pops as user Back input. Source selection, catalogs,
failure/retry decisions and the bounded navigation policy remain common.

Native production audit for this increment: only
`apps/ios/iosApp/NaviampCarPlaySceneDelegate.swift` changed. It maps the Library identifier to a
CarPlay tab image and executes `CPInterfaceController` stack effects over native template objects;
those SDK references/callbacks cannot live in common Kotlin. The existing Objective-C session
adapter already forwards generic destination/page identifiers and required no production changes.
The real iOS session-owner test additionally exercises the translated four-tab projection while
confirming a disconnected source does not invent catalog media and scene detach retains the runtime.

Library/Favorites/Unavailable reuse existing shared resource keys, completed in every maintained
translation; Artists/Albums/Tracks reuse already complete keys. All six labels were verified in all
17 catalogs. No settings or schema migrations were added, and no artist/album artwork was introduced.

The full shared JVM pass is **679 presentation tests**, **344 passed Navidrome tests plus one skipped
opt-in Aurral live-account test**, and **37 Jellyfin tests**. The skip is the existing environment-gated
`AurralCompanionLiveTest.realDiscoveryAccountRemainsSeparateFromThePrimaryLibraryAndPlayback`, not a
CarPlay/paging test. Android/JVM/iOS common compilation, including both provider implementations,
and the architecture guard passed before the Swift adapter was changed. Evidence is in
`build/issue-43/library-common.log` and `library-ios-host.log`.

Final native verification passed **120 shared presentation tests**, **20 Navidrome protocol tests**,
**25 Jellyfin provider tests**, and **one real iOS application-owner/session test**, with no failures,
errors or skips. The native owner test exercises both the three-tab disconnected state and the
four-tab translated Library projection. The unsigned Xcode Debug simulator app build passed with
no new Swift diagnostics; the existing ICU simulator deployment-target and unused AppIntents
metadata warnings remain.

Final evidence:

- `build/issue-43/library-native.log`: the native matrix above, on the existing dedicated simulator.
- `build/issue-43/library-xcode.log`: passing unsigned SDK/Swift/framework/app build.
- `build/issue-43/library-whitespace.log`: no findings with CRLF-aware whitespace checking.
- `build/issue-43/library-final-summary.json`: exact test totals, compile/architecture/build results,
  and the existing opt-in live-test skip.
- `build/issue-43/library-final-source-manifest.json`: HEAD/branch and SHA-256 hashes for all 55
  changed/untracked source, test, build and resource files. These match the frozen validation
  snapshot captured before native tests and remained unchanged through the final app build.
- `build/issue-43/library-final-state.json`: confirms the dedicated test simulator is shut down.

Visual/template/physical CarPlay acceptance remains pending: check the Library tab and Home fallback,
large-catalog paging, artist/release/album Back paths after ancestor trimming, system Now Playing and
Up Next transitions, unsupported/empty/error presentation, VoiceOver, hardware input, and
source/phone/car synchronization and disconnect/reconnect during loads. Compilation and native
tests do not establish those SDK/UI results. Nothing was committed, pushed or sent to hosted CI.

## Current album from Now Playing — local implementation and verification

The Album–Artist line on Now Playing now opens the current album's scene-owned detail page with
Play, Shuffle and ordered track selection. The album need not exist in Home's recents. Core derives
the target from current playback metadata and accepts only the current shared revision; idle/live
media, missing album identity/title, unavailable source, connecting/restoring state, Connect remote
authority, and an unavailable root cannot open an album. Progress ticks do not invalidate the input.

Shared collection navigation reuses an already loaded matching page, removes pages above it, and
avoids duplicate loads. A different collection replaces the old path rather than growing an unbounded
stack. Loading alone changes neither phone navigation nor playback. Existing shared collection
loading, retry, timeout, source invalidation, Back/detach cancellation, and audio readiness apply.
No user-facing strings, settings, artwork or additional provider protocols were introduced.

Seven new common tests cover current albums outside recents, capability exclusions, stale taps,
loaded-page reuse and queue return, failure/timeout retry, late-load cancellation, and a composed
Core/provider/playback integration with album ordering and engine readiness. All 666 presentation
JVM tests and Android/iOS common compilation passed before native wiring was changed. Final iOS
validation passed 107 shared native tests and the real application-owner/session integration test.

Native production diff audit for this increment:

- `apps/ios/iosApp/NaviampCarPlaySceneDelegate.swift`: sets the CarPlay SDK's
  `isAlbumArtistButtonEnabled` from common state, forwards the `CPNowPlayingTemplateObserver`
  Album–Artist callback, and resets the native flag on scene release.
- `apps/ios/src/iosMain/kotlin/app/naviamp/ios/NaviampIosVehicleBrowseSession.kt`: exports the shared
  capability flag through Objective-C and forwards the native revision-bound method to Core.

This follows [Apple's Album–Artist observer guidance](https://developer.apple.com/documentation/carplay/cpnowplayingtemplateobserver/nowplayingtemplatealbumartistbuttontapped(_:)).
Native adapters contain no target selection, loading, capability or retry policy.

Evidence: `build/issue-43/album-context-common.log`, `album-context-ios-host.log`,
`album-context-native.log`, and `album-context-xcode.log` record passing local compilation/tests and
the unsigned Debug simulator app build. The existing ICU simulator deployment-target and unused
AppIntents metadata warnings remain; no new Swift compile diagnostics were reported. The CRLF-aware
`album-context-whitespace.log` is empty. `album-context-final-summary.json` records test totals;
`album-context-final-source-manifest.json` supersedes the controls checkpoint with SHA-256 hashes
for the latest changed source/test/resource/build files. Sources remained frozen during builds.

Visual acceptance remains pending: verify the native Album–Artist affordance, album presentation
from system/phone/car Now Playing, Back/Up Next navigation, and disconnect/reconnect or a source
change while the album loads. Compiling and running native tests does not establish those results.
The dedicated simulator shutdown is recorded separately in `album-context-final-state.json`.
Nothing was committed, pushed or sent to hosted CI.

## Playback controls — local implementation and verification

Core owns button availability, ordering, active state, revision-bound input, in-flight serialization,
30-second acknowledgement timeout, translated failure alerts, explicit retry, source/track/occurrence
invalidation, and scene cancellation. It uses the existing external playback action order:
**Start Radio, favorite, shuffle, repeat**, omitting unsupported controls. Idle placeholders and live
streams expose none of these library controls. Connect authority hides Start Radio while retaining
supported remote favorite/shuffle/repeat actions. Progress and volume ticks do not invalidate buttons.
Scene disconnect cancels only scene work and clears CarPlay controls; it retains the application/player.

Favorite and Radio return explicit common mutation acknowledgements. A delayed favorite response
cannot replace a newer current track, write metadata across a source change, or publish after
cancellation. Radio failure, empty expansion, and a busy build report rejection; a successful refresh
preserves the current playing/paused seed, position and history through the existing transaction.
Shuffle and repeat await shared state acknowledgement, including asynchronous Connect responses.
Repeat cycles Off → Queue → Track → Off. Failures surface in a dismissible CarPlay alert with shared
translated copy; the host does not display provider diagnostics or automatically retry presentations.
All labels reuse existing keys verified in all **17** maintained catalogs. No settings were added.

Native production placement audit for this increment:

- `apps/ios/src/iosMain/kotlin/app/naviamp/ios/NaviampIosVehicleBrowseSession.kt`: projects shared
  controls/alerts to the Objective-C ABI and translates Swift callbacks and session lifetimes.
- `apps/ios/iosApp/NaviampCarPlaySceneDelegate.swift`: instantiates `CPNowPlayingImageButton`,
  `CPNowPlayingShuffleButton`, `CPNowPlayingRepeatButton` and `CPAlertTemplate`, translates SDK
  callbacks, and serializes unavoidable asynchronous native template effects. System repeat/shuffle
  selected states continue to come from the existing MediaPlayer publication; no additional player
  or remote-command registration was introduced.

Common code and tests passed JVM, Android and iOS compilation before these native changes were made.
Final source files were frozen during validation. Evidence:

- `build/issue-43/controls-common.log`: full presentation JVM tests, Android/iOS shared compilation,
  and `verifyCoreFirstArchitecture` passed.
- `build/issue-43/controls-ios-host.log`: iOS device host compilation passed.
- `build/issue-43/controls-native.log`: 100 shared native tests (7 playback controls, 7 browse,
  15 queue/details, 26 external bridge, 42 Now Playing media, 3 output effects) plus the real native
  application-owner test passed on the existing dedicated simulator.
- `build/issue-43/controls-xcode.log`: unsigned Debug simulator build passed. Existing ICU simulator
  deployment-target and unused AppIntents metadata warnings remain; no Swift compile warnings/errors.
- `build/issue-43/controls-whitespace.log`: no whitespace findings with CRLF-aware checking.
- `build/issue-43/controls-final-state.json`: confirms the dedicated test simulator is shut down.
- `build/issue-43/controls-final-summary.json` and `controls-final-source-manifest.json`: machine-readable
  results and SHA-256 hashes for all changed/untracked source, test, resource and build files.

Pending visual/device acceptance: confirm actual button ordering and icons, favorite selection,
system shuffle/repeat mode appearance, VoiceOver labels/state, failure-alert dismissal, control input
while a request is pending, phone/car synchronization, live/unsupported control omission, and
source/track changes or disconnect/reconnect during a radio build. These cannot be established by
compilation or native unit tests. No iPhone is available; older Xcode installation remains declined.

## Initial checkpoint

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
6. The original sequence gated expansion on minimal lifecycle/browse-to-play acceptance. On
   November 16 the owner explicitly authorized Queue / Up Next followed by playlist/album details
   while visual acceptance remains pending. Continue shared implementation and local verification;
   do not treat that authorization as native rendering, physical acceptance or release approval.

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

## Resumed local acceptance — November 16, 2026

The owner is now at the Mac, but no physical iPhone is available. The existing dedicated
`Naviamp Issue 43` iOS 26.5 simulator and installed `app.naviamp.ios` app were retained.
The installed toolchain identifies itself as Xcode 27.2, build 27B52. Device Hub exposes a disabled
**Device > CarPlay Simulator** action for that simulated iPhone. CoreSimulator enumerates a
720×480 CarPlay screen, but that screen registration does not establish connected template or
browse-to-play acceptance.

Apple's [Xcode 27.2 release notes](https://developer.apple.com/documentation/xcode-release-notes/xcode-27_2-release-notes)
explicitly list iOS Simulator CarPlay as unsupported and require a physical USB-connected device
for Device Hub CarPlay (179494052 / FB23101450). The
[current CarPlay Developer Guide](https://developer.apple.com/download/files/CarPlay-Developer-Guide.pdf)
also describes Device Hub's USB-connected iPhone workflow. This establishes a documented toolchain
limitation behind the earlier environment blocker; it is not resolved by the owner being present.

Visual CarPlay acceptance remains open. A separate older Xcode installation and compatible iOS
runtime are a candidate simulator-only route, subject to setup and actual runtime verification.
The owner explicitly chose to leave visual CarPlay testing pending rather than install an older
toolchain. The current Xcode installation remains available for concurrent tvOS work. No developer-account
settings, provisioning profiles, entitlement approvals or production code changed during this check.
No build or regression suite was rerun for this documentation-only finding.

## Queue / Up Next and collection details — November 16, 2026

Owner-authorized local work continues on `codex/43-carplay-queue-details` while the visual gate
above remains open. Do not push or run hosted CI for this iteration.

### Shared behavior

- Home gains Queue when a non-live queue exists; the system Now Playing Up Next action uses the
  same scene controller. Queue retains the existing shared current-first ordering and history.
  Each repeated track occurrence retains its own selection identity. Selecting the active playing
  item reveals Now Playing without restarting it; a paused current item resumes through the
  shared transport command. Other rows use the existing queue-selection policy.
- Album and playlist rows open scene-owned details instead of starting playback. Details offer
  Play, Shuffle and individual tracks. Album disc/track order and playlist order/repeated tracks
  are preserved; all rows remain reachable through bounded pages. The detail load does not change
  the phone's album/playlist page or navigation.
- `NaviampCoreVehicleCollectionAccess` uses the existing offline-capable provider source and queue
  transaction, including collection playback profiles and output routing. It rejects stale source
  snapshots. Collection playback does not silently replace the local queue while Naviamp Connect
  has remote playback authority; that unsupported action fails safely.
- Core owns loading, retry, timeouts, cancellation, source/account invalidation, revision checks,
  native page dismissal and the readiness-to-Now-Playing navigation request. Native presentation
  errors do not create an automatic retry loop. Repeated progress observations do not rebuild the
  browse projection or request another native push.
- Existing Queue, Play, Shuffle and Retry resource keys supply the added labels. Thirteen maintained
  catalogs lacked the existing Play key; this slice fills those entries and uses an existing Retry
  key present in every catalog. All four keys now exist in all 17 catalogs. There are no new
  resource keys, settings, persistence schemas or migrations.

### Initial and paused verification for this slice

- Initial shared implementation compiled for JVM, Android and iOS ARM64. The full presentation
  JVM suite passed **647 tests**, with no failures/errors/skips, including 20 vehicle cases.
  The common architecture guard passed in that run. Logs: `queue-details-common-compile.log`,
  `queue-details-common-tests.log`; summary: `queue-details-common-summary.json`.
- Subsequent boundary review moved root-presentation failure policy into Core and added a
  regression (the latest source contains 14 new Queue/details cases). The existing queue command
  mapper was consolidated, and the missing Play translations were filled. These final refinements
  were pending at the pause; final results are recorded in the resumed verification below.
- Translation validation passed for all 13 changed catalogs. Queue, Play, Shuffle and Retry keys
  are present in all 17 catalogs. The Swift delegate passed syntax parsing, which does not establish
  framework/SDK type checking or native template behavior.
- The final run completed the updated shared UI JVM compilation and JAR, then was cancelled with
  exit 130 at the owner's requested pause boundary. It had reached presentation JVM compilation.
  Log: `queue-details-final-checks.log`. The final full JVM suite, final Android/iOS compilation,
  affected native tests, expanded application-owner ABI test, architecture recheck and Xcode build
  were **pending at the pause**, not passing. The earlier native run was also interrupted before tests.
  The resumed verification below supersedes that pending status for automated checks.
- Native scene rendering/input, actual CarPlay Back/Up Next navigation, audible playback and
  physical lifecycle acceptance remain unverified.

### Native production-file boundary audit for Queue/details

| File | Concrete native boundary |
| --- | --- |
| `apps/ios/iosApp/NaviampCarPlaySceneDelegate.swift` | CarPlay scene/Now Playing/interface-controller callbacks and asynchronous CPTemplate push/pop operations require Apple's SDK; the adapter renders Core state, retains native templates and reports completion or removed identities. |
| `apps/ios/src/iosMain/kotlin/app/naviamp/ios/NaviampIosApplication.kt` | The Swift/Objective-C factory ABI now accepts the expanded native projection callback while still delegating session creation to Core. |
| `apps/ios/src/iosMain/kotlin/app/naviamp/ios/NaviampIosVehicleBrowseSession.kt` | Objective-C-compatible DTOs and callback signatures translate Core's page/navigation model and forward native lifetime events to the shared controller. |

No Android or Desktop production file changed in this slice. No native provider calls, collection
ordering, queue-selection policy, readiness timeouts, retry policy or user-facing copy were added.
The initial native root-presentation failure gate was moved into Core during boundary review;
reconnecting creates a fresh scene controller while preserving the application runtime.


### Paused checkpoint — November 16, 2026

The owner requested pausing after compilation and shutting down this work's simulators. The
running UI JVM compile completed, remaining queued verification was stopped, and the dedicated
`Naviamp Issue 43` simulator (`B121CD2B-C346-4DF6-91FC-72B6E5E96B8D`) was verified **Shutdown**.
No other simulator was targeted; the separately active tvOS work was preserved. No Xcode build
was started for this slice. No commit, push, PR update or hosted CI was performed.

Resume from this local worktree/branch, retaining all pre-existing CarPlay changes. Re-run the
full presentation JVM suite and common Android/iOS compile checks, then affected native vehicle,
external-playback and output-routing regressions plus the iOS application-owner test. Boot and
reuse the retained dedicated simulator when needed. Finally perform Xcode's unsigned Debug
simulator build to type-check the expanded Swift/Kotlin ABI and CarPlay SDK calls. Visual testing
remains explicitly pending under the owner's earlier decision; do not install another Xcode or
change Apple developer-account settings without a new instruction.

The verification source snapshot is `build/issue-43/queue-details-source-manifest.json` (37 local
source/build/resource files). The local native test init script at
`/private/tmp/naviamp43-simulator.init.gradle` pins presentation tests to the retained simulator
with `standalone=false`; app tests use `-Pnaviamp.ios.testDevice=<simulator-id>`. Test commands and
full output are retained in the local logs above. These are unfinished local changes, not PR or
release readiness evidence.

## Resumed verification — November 16, 2026

The owner resumed local work. The source matched the paused checkpoint before verification began.
The full 648-case presentation suite and common Android/iOS compilation passed. Native shared
regressions passed, but the real application-owner integration exposed a cold-start defect: the
shared Now Playing presenter always publishes an empty-identity placeholder, and the external
bridge was projecting that placeholder as a queued media item.

The correction is in the common external playback bridge. An empty media identity now produces
an idle external snapshot with no current item or queue while preserving supported idle volume
control and the saved browse catalog. The existing native empty-queue assertion was retained;
new portable regressions cover both the projection and a real cold Core runtime.

The first retry picked up mixed compilation inputs when the volume-preservation refinement was
edited during an active compile. Bytecode inspection confirmed the earlier bridge body; a frozen
source rebuild of the affected module resolved that stale output. No test assertion was relaxed.

Current results after the correction:

- Full presentation JVM suite: **650 tests**, zero failures/errors/skips.
- Common Android/iOS ARM64 compilation and architecture guard: passed.
- Affected shared iOS Simulator suite: **51 tests**, zero failures/errors/skips.
- iOS device-target host compilation: passed.
- Real application-owner native integration: **one test passed**, retaining the no-phantom-queue
  assertion, Foundation main-run-loop/translation checks, scene-session close and runtime reuse checks.
- Unsigned Xcode Debug simulator build: passed. The first build exposed an observer-conformance
  isolation warning; the native CarPlay observer conformance is now explicitly `@MainActor`, and
  the final build has no CarPlay conformance warning. No deployment-target or account changes were made.

Evidence: `queue-details-placeholder-common-final.log`, `queue-details-final-jvm-summary.json`,
`queue-details-native-final.log`; the original native failure is retained as
`queue-details-native-owner-failure.xml`. Visual/native-template and physical CarPlay acceptance
remain pending; nothing has been committed, pushed or submitted to hosted CI.


### Final local evidence and remaining gates

- Common fix/full suite and cross-platform compile: `queue-details-placeholder-common-final.log`.
- Full JVM result: `queue-details-final-jvm-summary.json` — 650 cases, zero failures/errors/skips.
- Native tests and device-target host compile: `queue-details-native-final.log`.
- Native result: `queue-details-final-native-summary.json` — 51 shared cases plus one native owner case,
  zero failures/errors/skips. The 51 cases include 15 new Queue/details regressions, seven existing
  vehicle cases, 26 external-playback bridge cases and three output-routing cases.
- Final Swift/Kotlin/CarPlay SDK build: `queue-details-xcode-final.log` — `BUILD SUCCEEDED`.
- Final aggregate test result: `queue-details-final-summary.json`.
- Final source snapshot: `queue-details-final-source-manifest.json` — 38 local source/build/resource files.

The source and native boundary diff were reviewed after the final change. The inherited
CRLF files trigger the default Git trailing-whitespace check; a CRLF-aware check is clean
(`queue-details-diff-check.log`). Existing vendor warnings remain, including the ICU data object's
simulator deployment-version warning, which is also present in the earlier `vehicle-xcode-final.log`.
The native execution target was iOS 26.5. Earlier OS runtime acceptance remains unverified.

Only the retained `Naviamp Issue 43` simulator was booted for these checks. It was verified
**Shutdown** afterward. No other simulator was targeted, no additional test device was created,
and no commit, push, PR update or hosted CI was performed.

This completes local automated verification for the Queue/details slice. Actual CPTemplate scene
connection/rendering, touch/rotary input, Back/Up Next navigation, audible playback, car-first OS
callbacks, source switching in the connected car UI, locked-phone/Siri/routing/interruption behavior,
entitlement approval and physical head-unit acceptance remain open under issue 43. The owner left
visual testing pending; no older Xcode was installed or Apple developer-account configuration changed.
