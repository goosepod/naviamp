# Track Radio menu entries

Sven's report #519 identifies two shared UI omissions: album track rows disabled Radio explicitly,
and the current-track overflow action catalog omitted it despite an existing Core handler.

Album mapping now carries provider-supported Radio eligibility on each track through initial load,
metadata/popularity enrichment and settings remapping. Live streams and discovery previews are
ineligible. The current-track overflow uses the existing Now Playing capability, which already
accounts for provider/source/output restrictions, and dispatches the existing current-track action.
Both menus reuse the maintained translated Start Radio label. Product changes are common code;
there are no platform production changes or new settings.

Local validation on November 6:

- 9 action-catalog tests and 6 rendered album tests pass, including opening the actual overflow
  menu, selecting Start Radio and asserting the selected track and dispatched action.
- 19 common detail-controller tests pass, including capability preservation through enrichment
  and settings remapping with both supported and unsupported providers.
- 33 existing Now Playing media tests and 7 remote-command routing tests pass, retaining failed,
  empty, repeated and stale-result radio behavior and remote routing.
- Total: 74 focused tests, zero failures/errors/skips.
- Shared Android, JVM and both iOS targets compile; the common-first architecture check passes.

These are local UI/controller and compile checks, not a new live-server or audible Radio test.
The branch still requires final-base CI and owner acceptance before issue closure.

## Follow-up review on accepted main

Reviewed PR #528 at `ccd186fc9` against accepted main `62851e1c3`. The accepted main tree
matches the candidate that passed Verify run `42306716280`. The PR's own final-base Verify run
`42311690332` was cancelled, so that does not establish final PR acceptance.

Added a rendered current-track overflow regression that opens Track actions, selects Start Radio,
checks the dispatched current-track action and menu dismissal, then removes eligibility and
verifies Radio is absent when reopening the menu. This closes the UI-wiring coverage gap between
the existing action-catalog test and the actual Now Playing menu.

Fresh local tests: 9 action-catalog, 6 rendered album, 1 rendered current-track menu, 19 detail
controller, 34 Now Playing media-controller and 7 remote-command routing tests pass, totaling 76
with zero failures/errors/skips. Logs are retained under ignored `build/sven-review/` in this
worktree. No production or platform production files change in this follow-up. Live-provider
generation and final full CI remain acceptance work; no issue closure or merge is claimed.

## Windows native menu checks, November 7

Built the Windows distributable and isolated Android debug APK from production checkpoint
`6189184ae`, using JDK 21 and the installed CMake/MSVC toolchain. The successful combined build
executes 269 tasks in 4m44s. Initial local setup failures were a missing CMake environment path
and an unquoted PowerShell Gradle property; neither was a product test failure.

`DesktopTrackRadioAppFixtureKt` prepares only a new profile and disposable loopback credentials
through shared storage/settings APIs. The normal application performs its own credential-protection
migration on first open. Both test processes used separate APPDATA/LOCALAPPDATA directories and
volume zero; existing installed/review windows were not selected. The Android package is
`app.naviamp.android.sven519`, installed separately from existing phone applications.

Actual Windows UI observations using the packaged application:

- The album track overflow exposes Start Radio. Clicking it against the real Navidrome 0.64.2
  FLAC library starts the selected seed and reports no additional tracks. That disposable server
  has external similarity services disabled; this is the empty-result case, not successful
  generation from a recommendation catalog.
- A separate controlled Subsonic HTTP fixture supplies three 90-second WAV tracks and explicit
  `getSimilarSongs` responses. Its contract probe verifies the requested count and exclusion of
  the seed. This is a protocol fixture, not a second real Navidrome server.
- Clicking Start Radio in the album track menu starts the selected seed. The current-track
  overflow also exposes Start Radio; selecting it preserves the playing seed's position
  (observed at 24 seconds before selection and continuing past 31 seconds afterward).
- Up Next visibly contains the two other distinct tracks. Playback subsequently advances to
  the third track and its clock moves normally. The menus dismiss and no empty-result notice
  appears in the positive case. Playback is muted; audible output is not claimed.

Both Windows launches initially show a blank client surface until maximize/restore. The functional
checks above occur after that resize; they do not establish clean-start presentation or resolve
that separate rendering observation. No platform production code changes are made here.

Reproduction uses `scripts/android-tv-fixture.py --port 18081 --tracks 3 --albums 1 --track-seconds 90`
and the opt-in Desktop fixture entry point, with a JavaExec task using the `desktopTest` classpath
and two arguments: a **new** profile directory and `http://127.0.0.1:18081`. Launch the distributable
with APPDATA set to that directory's `Roaming` child and LOCALAPPDATA to its `Local` child. The
owned fixture source uses username/password `fixture` only. Never point this setup at an existing
profile. Raw local build/setup logs and the HTTP contract result are under `build/sven-review/`.

The connected Pixel is charging at 72%, but its display is locked. A fresh owned test profile is
prepared; native Android menu input remains pending an unlock. TV checks remain deferred by the
owner. Final-base CI follows the accepted iOS fixture correction in #531.

## Android native menu checks after owner unlock, November 7

The owner unlocked the connected Pixel 6a (Android 17/API 37). Launched only the isolated
`app.naviamp.android.sven519` debug application; existing installed app profiles remain untouched.
The initial connection screen did not recognize the prepared database, so the connection was
created through the normal UI using only disposable loopback fixture credentials. A separately
owned ADB reverse for port 18081 connects the same three-track protocol fixture used on Windows.
No TV or personal media server was contacted.

- The first album track's overflow exposes **Start Radio**. Selecting it dismisses the menu,
  opens Now Playing with the selected seed and starts native playback. Its clock advances from
  0:00 to 0:18.
- The current track's **Track actions** overflow also exposes **Start Radio**. Selecting it
  dismisses the menu and retains the currently playing seed and elapsed position (0:41), rather
  than restarting at zero.
- Playback automatically advances from the seed to fixture track 2. Its clock reaches 0:26.
  The shared Now Playing queue shows track 3 under **UP NEXT** and the original seed under
  **BACK TO**, establishing distinct generated queue entries and actual native advancement.
- Playback is paused at 0:26 after inspection. These are UI/native-clock checks against a
  controlled Subsonic protocol fixture, not an audible-quality assertion or a live external
  recommendation-service test. The prior real-Navidrome empty-result control remains separate.

The installed APK is the previously built production checkpoint `6189184ae`; later changes to
this branch only add test evidence and integrate accepted main. Its menu implementation is
unchanged. Required final-base CI is recorded on PR #528. No platform production file changes.
