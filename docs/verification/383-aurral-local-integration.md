# Aurral discovery expansion: local integration

Owner instruction, November 5: carry out the existing-stack integration, Recommendations,
Search, Trending, genre blocking and final acceptance as substantial local batches. Run
builds and tests locally; do not dispatch GitHub for each change. Existing issue scopes
#383/#474/#475/#476/#477/#478/#502/#503/#504/#505/#506 remain authoritative.

## Baseline

The local acquisition stack includes artist #488, release #489, native queue #498,
waiting-for-release #503 and durable requests #478. Accepted main `cafcee8cd` was merged
without conflicts. The baseline focused command passed locally; some unchanged test tasks
were up to date. It is not a fresh execution of every native assertion.

GitHub evidence inspected before this batch: exact artist/release heads passed Verify;
native queue #498 failed `IosMutualTlsPlaybackIntegrationTest.realMutualTlsAuthenticatesAndSeeksThroughNormalPlayback`
(1 of 17 native iOS methods). Android and all Desktop native jobs passed. Preserve that
failure; its cause is not established by another passing execution.

## Delivery and acceptance

- Keep complete portable behavior, UI, navigation, policy and protocol in common code.
- Reuse shared artist/release/request/preview owners and validated Library identities.
- Use bounded reads, retained last usable data and source/account generations.
- Acquisition, genre blocking and playlist saving remain explicit actions. Never replay an
  uncertain mutation; reconcile by authoritative reads.
- No live acquisition target has been selected for this batch. Do not repeat the earlier
  one-shot Airbag request. Test an explicit new target only with target-specific authorization.
- Physical touch/TV remote, spoken screen-reader and audible live output are distinct from
  rendered semantics, muted native fixtures, transport bytes and emulator execution.
- No live-test environment variables are currently configured. Only an Android emulator is
  attached; no physical Android device or booted Apple simulator was found during inventory.
- Final exact-source native iOS, packaged Desktop and Android acceptance, current-source
  rendering, and live/device evidence remain required. Record pass/fail/not run precisely.

## New local work

#474 adds full paginated Recommendations, For you/All, rank/name/first-seen sorting,
seed/genre and validated new/direct/further filters, exclusive For you groups, explanations,
loading/building/failure/partial counts and Back retention. Older metadata does not invent
first-seen or discovery depth. Reads never request an administrator rebuild. All new copy
is defined in English and all 16 maintained translations; no portable setting is added.

Verification commands and results will be recorded after the local batch completes.

## November 5 local implementation and acceptance

Local checkpoints: Recommendations `18411e248`, Search `f0754b41b`, Trending
`a2a25eb91`, genre blocking `e82332a11`. The acceptance branch is
`test/506-aurral-local-acceptance` in the existing `aurral-478` worktree. None of
these new increments has been pushed or dispatched to GitHub.

Recommendations retain the compact Home rail and read the complete collection in bounded
100-row pages, at most ten pages per pass, with explicit further loading for larger sets.
For you uses the server rank, seed explanations, initial-cohort exclusion and validated
depth/first-seen fields; groups do not duplicate artists. Older metadata does not invent
new/direct status. Search is explicitly submitted, source scoped, paginated and retained
through artist/release/Back; album navigation validates release metadata and artist credits.
Unresolved IDs remain informational with an Aurral catalog fallback.

Trending preserves nullable sections, ranks, movement, history, region and update time.
A chart key alone never establishes a preview or Library identity. Only the exact known
native preview route plus a positive Deezer ID enables the isolated preview engine.
Unmatched chart entries use explicit catalog selection. Saving a songs chart is deliberate,
requires the current user's Flow permission, reconciles existing saves before POST, and
blocks uncertain repeats. Saving can cause Aurral to acquire playlist tracks; it is not
proof of playable files.

Genre support is established by a safe GET probe. Older servers hide the action. Only
server-approved genre values are offered for blocking, and the shared comparison contract
matches the released server's case/whitespace/hyphen/underscore equivalence. The server owns
normalization, recommendation filtering and storage. Confirmed changes refresh Home and
Recommendations; uncertain mutations require read reconciliation. No Library filtering,
portable preference, new schema or platform production code is added by these four increments.

### Preserved findings

- An early local run overlapped incremental edits and failed compilation; it is not acceptance
  evidence. Later runs use fixed source snapshots.
- The first new chart fixture compared a read-status enum with an operation-status enum and
  failed test compilation. Corrected the assertion without changing production behavior.
- Four shared controller regressions exposed retained completed coroutine jobs cancelling a
  later caller. The owner now clears only its own completed job in `finally`; the regression
  batch passes without widening deadlines or weakening assertions.
- A real isolated packaged Mac app connected using the dedicated ordinary test account and
  displayed discovery and Library content. Opening full Recommendations then remained at
  `0 of ?` despite healthy HTTP reads. Production provider decorators are recreated per lookup;
  reference equality incorrectly discarded valid results. A new recreated-wrapper regression
  reproduces this failure before the correction. The fix uses the existing shared provider
  identity contract. The failed window and failing regression remain evidence, not a pass.
- The same walkthrough exposed the inappropriate artist-list label `All releases`. The new
  collection uses translated `All`; chart epoch timestamps are interpreted as readable UTC
  timestamps. Focused action labels now retain visible foreground/background contrast.
- Computer use initially timed out opening the separate review app, later exposed its real
  accessibility tree and screenshot, and subsequently had intermittent capture/action failures.
  These tooling failures do not establish an application crash or a successful walkthrough.

### Local results so far

The initial complete matrix at `e82332a11` passes: 375 tasks, 124 executed, 251 up to date,
16m16s. It covers Android debug packaging, verified Desktop packaging, both iOS host/common
compilation graphs, native iOS test-binary linking, SQLDelight migration verification and the
architecture guard. Its deterministic JVM reports contain 186 cases with no failures/errors/
skips: domain 18, SQL storage 5, shared controllers/navigation 59, rendered UI 26 and provider/
transport 78. This result predates the recreated-wrapper walkthrough correction and does not
validate that subsequent source. The first focused wrapper regression deliberately fails.

Nine new rendered captures cover Recommendations, charts and genre management at 360, 1100
and 1280 pixels wide. Inspected narrow chart and wide genre captures; controls, explanatory
copy and the focused unblock action remain readable. These are rendered layouts and semantics,
not spoken screen-reader or physical input acceptance.

### Configured read-only comparison

Fresh direct HTTP reads through an encrypted SSH tunnel confirm Aurral 2.13.0, API v1 and
11 features. The ordinary test account has album-add permission and lacks Flow access; no
administrator or permission change was attempted. All 456 recommendations load over five
pages with 456 distinct IDs and one run ID, 450 direct and six second-hop records, all with
first-seen metadata. Charts return 100 songs, 25 albums, 25 artists and nine genre charts;
the native MP3 preview path matches the production transport contract. The user's blocklist
is initially empty. Request activity has 312 rows (processing/completed/failed/pending);
Naviamp intentionally bounds its displayed/read history and does not claim it is a private
list of only local submissions.

These direct HTTP results are separate from production-client, actual-window, audible,
physical-device and mutation acceptance. Passwords, private addresses, tokens and raw
configuration are excluded from this record. Production-client execution against the final
candidate and the final native/output results are recorded below when complete.

## Final local source and results

Code checkpoint `85716735b9fe071845d6b13f88fa2c0a34e17640` fixes recreated-provider
identity and the associated labels/timestamps. The failing wrapper regression now passes;
all 32 discovery-controller cases pass, including stale-source rejection. No assertions,
timeouts or playback cadence were weakened.

The updated full local matrix passes: **374 tasks, 101 executed, 273 up to date, 9m13s**.
It builds the Android debug APK and verified Desktop app, compiles both iOS hosts and
common dependency graphs, links the real native iOS test binary, verifies the single
unreleased migration and passes `verifyCoreFirstArchitecture`.

Deterministic JVM coverage is **187 cases**, all passing with no errors/skips: domain 18,
SQL storage 5, controllers/navigation 60, rendered UI/resource behavior 26, provider and
transport 78. The standalone live run subsequently replaces the provider task's JUnit
report; do not add the same provider cases twice or interpret replaced reports as failures.

A separate configured execution runs **nine active read-only production-client methods**,
all passing with no skips. The feedback mutation method is excluded by explicit method
selection, rather than counted as an early-return pass. These cover bootstrap, catalog
paging/search, artist/release/edition/track reads, validated owned-artist mapping, real cover
images, MP3 preview bytes, initial/nonzero authenticated ranges, Trending and a genre chart,
and the user's blocklist. No acquisition, feedback, monitoring, playlist-save, genre or
server-configuration mutation was submitted.

Fresh native execution passes **all 17 iOS methods** against an owned fixture/simulator,
including the mTLS playback case that failed on earlier #498 CI, plus **both real Desktop
preview contracts**. The Desktop fixtures are muted and synthetic; they verify queue,
index, paused position and saved-session isolation. A passing execution does not identify
or close the intermittent #454/#497 causes. Existing Windows-first-frame #493 and other
unavailable platform findings remain open.

Local logs:

- `/private/tmp/aurral-506-wrapper-before.log` — reproduced real-window identity failure.
- `/private/tmp/aurral-506-final-local-matrix.log` — final deterministic/build matrix.
- `/private/tmp/aurral-506-native-and-live.log` — configured live and native execution.
- `build/aurral-local-native-diagnostics` — bounded native observation artifacts.
- `build/aurral-local-acceptance` — sanitized result summary and candidate manifest.
- `/private/tmp/aurral-browser-captures` — inspected narrow/wide/TV component renders.

The debug APK is `apps/android/build/outputs/apk/debug/android-debug.apk`, 52,679,516 bytes,
SHA-256 `1a5b55d1d4b3a91957508b872bf0f247c5abe5e529b6264670d7ca0b6a604e19`.
Repository version/build remain v2.10.0/60; these are local development artifacts for the
selected future scope, not a new release. No version, tag, release or announcement was made.

## Open acceptance gates and handoff

The earlier, pre-redesign isolated Mac review app was at
`/private/tmp/aurral-acceptance-review-r2/Naviamp Aurral Acceptance r2.app` with a fresh
private test profile. The UI revision below supersedes it with r3. The installed application and its profile were preserved. The computer-use
service timed out repeatedly, including a final call that ignored its requested timeout for
approximately 75 minutes. Further native UI automation was stopped. **The corrected full-route
walkthrough is not accepted** merely because the wrapper regression and builds now pass.

Still open: actual Recommendations/Search/chart-to-artist/release/Back walkthrough after the
correction, audible live previews with a nonempty main queue, physical touch/TV remote and spoken
screen-reader checks, a positive live unreleased/waiting example, source/account restart/reconnect
checks in the real app, and the remaining upgraded-server lyrics/cover/playlist/listen-reporting
matrix. Initial AX information and rendered semantics do not establish spoken accessibility.

The owner subsequently approved one new Radiohead recording from a different single and a
temporary rock block/unblock on the dedicated account. Both guarded mutation tests pass:
exactly one recording POST returns HTTP 202/Queued with one job; one genre block and one unblock
restore the complete original blocklist. The earlier Airbag acquisition was not repeated.
The new approved recording must not be submitted again. The account lacks Flow
permission, so a positive live playlist-save path is unavailable without a separately scoped
account/permission decision; no permission was changed.

Keep #383/#474/#475/#476/#477/#478/#502/#503/#504/#505/#506 and dependent drafts open where their
acceptance criteria remain unmet. This local batch neither updates GitHub issues/PRs nor replaces
protected exact-source checks. No new hosted runner was dispatched and no branch was pushed.

All new product, UI, protocol, paging, normalization and lifecycle behavior is common. This batch
adds no platform production change. The six existing #478 repository-injection adapters and their
concrete OS/database lifetime boundaries remain audited in
[478-aurral-acquisition-tracking.md](478-aurral-acquisition-tracking.md#platform-diff-audit).


## Approved live acquisition and genre acceptance

The specifically approved “A Punchup at a Wedding (edit)” from Radiohead’s single
“A Punchup at a Wedding” was absent before one guarded request. Its atomic attempt marker
was written before POST. HTTP 202 returned Queued with one job. The genre acceptance used
one block POST and one unblock DELETE for rock, checking and restoring the original empty
blocklist. Both configured methods pass; neither action was replayed.

A separate read-only Desktop test reconstructs that accepted operation in the actual shared
SQL store, closes/reopens the database, and invokes the production shared acquisition tracker
and provider. Observed phases are Processing → Processing → Available over three bounded reads.
The tracker requires the exact requested recording in validated ownership and a canonical/
Subsonic Library album before Available. Server history independently reports completion.
The reopened acceptance database contains no login credentials.

The same test streams 26,836,983 bytes through the normal Library provider and executes the
real Core/BASS/JNI engine against those acquired media bytes. Muted play, pause, seek to one
second and resume pass, with native progress reaching about two seconds. This is genuine
acquired-media/native playback, distinct from audible output, an app UI restart, or a qualifying
reported listen. The approved recording remains in the server Library.

The first new acceptance-test compilation omitted the explicit nullable range argument; it
was corrected without changing production code. The final configured test passes with one
case, no failures/errors/skips. Logs are `/private/tmp/aurral-506-approved-mutations.log` and
`/private/tmp/aurral-506-acquisition-reconciliation-2.log`. One-shot markers and correlation files
remain private local artifacts. The provider genre test executes the actual common protocol;
the Desktop test exercises JVM filesystem/SQLite-driver and BASS/JNI boundaries while delegating
reconciliation policy to common code. Neither adds platform production behavior.

## Owner-directed UI revision

The owner rejected the initial navigation/presentation and requested a dedicated Aurral hub,
with separate section destinations and proper graphical controls instead of text navigation.
All UI work remains within the existing Aurral tickets. The preceding implementation and
acceptance results preserve backend evidence; they do not accept the rejected UI or validate
the revised presentation. No new ticket is needed or created for this revision.

The revision replaces Aurral's Home rails and section links with one branded entry card.
Its dedicated hub contains a featured Recommendations card and responsive Search, Trending,
Requests and Genres navigation cards, with icons, descriptions, chevrons and visible keyboard
focus. Each section occupies its own destination. Requests is a full shared page above the
underlying browser/artist destination, rather than a modal. System Back dismisses it first.

Recommendations uses tabs, compact filter menus and responsive artist cards. Search has an
explicit filled submit control, artist/album tabs, a separate external action and result cards.
Trending uses category tabs, card surfaces and outlined preview controls; chart artists without
a verified identity still go through explicit catalog selection. Genres uses labeled switches,
with uncertain mutations disabled. Separator aliases are deduplicated using Aurral's existing
normalization contract. Artist artwork stays circular and album artwork square.

Hub navigation does not fetch the complete recommendation collection. Entering a section starts
at its own top, and Back restores the hub viewport. All routing, state, controls and layouts are
common code; this revision changes no Android/Desktop/iOS production file. Six new descriptions
are present in English and all 16 maintained translations. Existing generic-provider rail tests
continue exercising that presentation; new Aurral tests assert the single Home entry, card
navigation and separate destinations. Directional artist-focus coverage follows the reachable
control path rather than depending on the previous text-button widths.

Initial rendering tests caught a real subcomposition focus problem: requesting Back focus before
the responsive header existed did not focus its button. Focus is now requested from within the
header's composition. An offscreen hub-card test was corrected to scroll the lazy container to
its target before selecting it. No live mutations were repeated during UI verification.

The first broad packaging attempt used Homebrew OpenJDK and was stopped by Compose's JDK vendor
check. The final batch uses the already-installed JetBrains JDK 21.0.11 and keeps that check
enabled. It includes discovery/domain and navigation/UI regression tests, Android debug packaging,
Desktop app staging, both iOS common/host compile graphs, migration verification and the Core-first
architecture check. Final results and review artifacts are recorded below.

Final shared routing review also found that TV's shell-level Back handler could otherwise consume
Back before a browser destination. Browser and Requests now register their own shared handlers;
TV browser pages use the same directional focus policy as the existing artist page. A rendered
TV hub test checks Back focus, D-pad activation of the Recommendations card, and the scoped Close
command from system Back. The Requests route test verifies it replaces the hub/artist content,
creates no dialog, and consumes system Back through the shared dispatcher.

The responsive header also consumes TV sidebar entry generations and reports focus handoff,
so re-entering a browser or Requests destination restores Back focus. The TV hub regression
checks re-entry after activating a card, in addition to directional activation and system Back.

The TV-sized cases run only in the local JVM Compose test renderer with simulated D-pad events.
No physical TV was connected to, installed onto or tested during this revision. These checks do
not accept physical remote navigation, TV display output or TV playback. The owner explicitly
confirmed this distinction during the local verification run.


### Revised UI verification result

The frozen-source matrix passes: **349 tasks, 66 executed, 283 up to date, 8m33s**.
The selected local regression suite contains **111 cases**, all passing with no errors or skips:
31 domain cases, 49 controller/navigation cases and 31 rendered UI/resource cases. The sidebar
re-entry regression failed against the preceding compiled UI, whose browser signature did not
accept entry generations, and passes after the fix is compiled. The full Android debug APK,
Desktop distributable and both iOS common/host graphs build successfully. Migration verification
and `verifyCoreFirstArchitecture` pass. This is compile/render verification for the UI revision;
it does not claim a new physical-device or audible native acceptance run.

The isolated review app is now
`/private/tmp/aurral-acceptance-review-r3/Naviamp Aurral Acceptance r3.app`, with a separate private
test profile and bundle identifier. Its ad-hoc signature verifies and its process launches.
The earlier task-owned r2 process was stopped after checking its exact executable path. The
installed application and owner profile were preserved. The owned native acceptance simulator
and TLS fixture were stopped; the SSH tunnel remains available for the isolated review app.

Fixture screenshots are in `/private/tmp/aurral-redesign-captures`, including the hub,
recommendations, charts and genres at 360/1100/1280 pixels, Search at 360 pixels, and Requests.
The final build log is `/private/tmp/aurral-ui-redesign-verified-source.log`. The sanitized local
candidate manifest and separate UI test summary are under `build/aurral-local-acceptance`.
The original backend-checkpoint evidence remains distinct from this UI suite; its 187 cases
are not added to or substituted for the 111 selected regression cases.

The actual-window walkthrough and the other manual/live gates listed above remain open.
Fixture images, accessibility semantics, a running process and a valid signature do not accept
that walkthrough. No new GitHub ticket was opened, branch pushed, PR updated or hosted runner
dispatched for this revision. All new/changed production files are common; the platform
production diff for this revision is empty.

## Follow-up review cleanup

Owner feedback after r3 requested regional date/time display, smaller Aurral typography consistent
with the Library and the existing font preference, compact release tracks, stable genre-row order,
mouse/phone Back parity, and capability-correct player ratings. This remains the same local batch
of existing Aurral tickets; no new ticket or GitHub update is required.

Common UI now parses complete instants before native formatting in the device locale and time
zone. Calendar-only release dates explicitly use UTC for formatting so their day cannot shift;
year-only/month-only precision is retained. Invalid timestamps are omitted rather than printed as
raw protocol data. Android delegates its hour-cycle preference to the OS; JVM and Foundation use
their native localized date/time styles. The unexplained raw rank array is removed, the useful
days-at-number-one summary remains, and zero chart movement no longer adds a redundant line.

Aurral body text uses the same 13sp baseline as Library rows. Existing `NaviampFontSizeScope` and
system accessibility font scaling continue to apply; there is no second setting or density reset.
Release tracks reuse shared `TrackRow` with every Library mutation/menu/swipe capability disabled.
Only explicit, scoped preview/request controls remain. Genre rows retain their original order
through block/unblock, refresh and temporarily omitted provider tags; unavailable actions remain
disabled pending current provider context.

Shared Back target selection now serves both the phone's existing native delegation and mouse
Back presses. A held mouse button cannot pop multiple pages; full and docked player states respect
the visible destination, and dialogs/menus consume Back before content behind them. This adds no
platform navigation implementation. Heart and stars follow independent capabilities; stale rating
metadata cannot render stars when track ratings are unsupported, and the missing controls reserve
no empty rating space.

A fresh authenticated, read-only `/api/discover/trending` comparison returned 100 songs for US
with update epoch 1793896169854. Its first nine ranks exactly match the owner’s web screenshot:
Choosin’ Texas, Boston, Been By Now, Be Her, BbY WOW, Nicole Kidman, Dracula (with JENNIE),
Patient Zero and stupid song. The web page features #1 separately and starts its top-song list at
#2; Naviamp includes #1 in its full list. No alternative ranking or provider query was introduced.
Playback was left unchanged after the owner identified low volume; the new review app reuses the
current isolated profile rather than reseeding the earlier zero-volume fixture.

### Native boundary audit for the cleanup

| Production file | Concrete native operation |
| --- | --- |
| `core/ui/src/androidMain/kotlin/app/naviamp/ui/AndroidDateTimeFormatting.kt` | Android Context and `android.text.format.DateFormat` supply regional formatting and the user's 12/24-hour preference. |
| `core/ui/src/jvmMain/kotlin/app/naviamp/ui/DesktopDateTimeFormatting.kt` | JDK `DateFormat`, `Locale` and `TimeZone` execute the shared formatting request against JVM system defaults. |
| `core/ui/src/iosMain/kotlin/app/naviamp/ui/IosDateTimeFormatting.kt` | Foundation `NSDateFormatter`, `NSDate` and `NSTimeZone` execute native regional formatting. |

All parsing, precision, timezone-selection policy, navigation, capability gating, typography and
stable-order behavior is common. No host production file is changed. Tests use local renderers
and synthetic mouse/phone-dispatch events; no physical TV or phone is accessed.

### Cleanup verification result

The final selected regression suite passes **154 cases** with no failures/errors/skips: 32 domain,
68 rendered UI/resource cases and 54 controller/full-shell cases. Regional checks cover US/Pacific
and German/Berlin date/time display, a local-day rollover, invalid instants and retained partial
release precision. The typography check compares Aurral body text with a real Library row and
verifies growth under the existing Small/Large preference. Mouse checks cover one press edge,
held input across recomposition, ignored non-Back buttons, modal priority and the same scoped
command through mouse input and the phone-style dispatcher. Rating controls follow independent
capabilities even with stale cached metadata. Initial viewport reports are excluded from the
full-shell test's navigation count.

The complete local build matrix passes: **349 tasks, 57 executed, 292 up to date, 3m02s** on the
final rerun. Android debug packaging, Desktop distributable verification, both iOS common/host
compile graphs, migration verification and the Core-first architecture check pass. The Desktop
packaging follow-up passes **67 tasks, 22 executed, 46s** and includes `jdk.localedata`, with a
mandatory distributable check. A disposable copy of the packaged VM/module image produces the
expected US/Pacific and German/Berlin formats; only a matching JDK command launcher is restored
in that disposable copy because jpackage strips command tools. This tests the packaged locale
data rather than relying on full-JDK test success. The build-script change is packaging metadata,
not platform product behavior.

Logs:

- `/private/tmp/aurral-polish-verified-matrix.log`
- `/private/tmp/aurral-polish-desktop-locales.log`
- `/private/tmp/aurral-polish-packaged-locales.log`

The updated fixture renders are in `/private/tmp/aurral-polish-captures`. The running review app
is `/private/tmp/aurral-acceptance-review-r4/Naviamp Aurral Acceptance r4.app`; it reuses the private
r3 review profile so volume, queue and connections are preserved, with a private rollback copy.
Its signature verifies and only the r4 review process is running. The installed application was
not targeted. No physical TV/phone, new GitHub ticket, push, PR update or hosted check was used.
The manual and release gates above remain distinct from these local results.

## Track and Home label alignment follow-up

The owner’s next live Mac review explicitly confirms working mouse Back, suitable font sizing,
and working requests. Two remaining visual findings are handled in the existing UI work:
track titles were above their controls, and Recently Added album/artist labels were too far apart.

A rendered regression reproduces the first defect before the fix: a single-line title’s vertical
center is seven pixels above its preview control. Shared `TrackRow` was still composing a blank
subtitle, which reserved a second text line. It now omits blank subtitles while retaining real
subtitles. Discovery release rows also reserve the same preview-control slot when a track has no
preview, keeping titles in one column. Home collection cards explicitly use compact title and
subtitle line heights rather than inheriting the larger Material body line height.

The alignment checks cover phone/desktop widths, Small/Large font preferences, track numbers,
preview controls, mixed preview availability, and compact non-overlapping album/artist labels.
Only common UI production files change; no new setting, provider operation or native adapter is
introduced. Owner feedback is evidence for those explicitly confirmed Mac behaviors, not a new
physical TV/phone or full native/audio acceptance claim.

The completed alignment batch passes **177 selected UI regression cases** with no failures,
errors or skips. All four phone/desktop and Small/Large track-layout combinations align the
single-line title with the preview and track-number centers and retain one title column when
previews are missing. Home card labels remain compact, single-line and non-overlapping at both
font settings. Captures are in `/private/tmp/aurral-alignment-captures`.

Shared UI compilation for JVM, Android and both iOS targets, Desktop packaging and the architecture
check pass: **104 tasks, 32 executed, 5m45s**. The refreshed Android debug APK also passes:
**226 tasks, 20 executed, 1m47s**. Logs are `/private/tmp/aurral-alignment-matrix.log` and
`/private/tmp/aurral-alignment-android.log`; the pre-fix failure is preserved in
`/private/tmp/aurral-alignment-before.log`.

The running review app is now
`/private/tmp/aurral-acceptance-review-r5/Naviamp Aurral Acceptance r5.app`. Its signature verifies,
the prior r4 review process is closed, and its existing isolated profile, queue, volume and
connections are preserved. Packaged locale data remains present. The installed application and
physical devices were not targeted; no GitHub issue, push or hosted check was added.
