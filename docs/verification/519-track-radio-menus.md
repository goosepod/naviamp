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
