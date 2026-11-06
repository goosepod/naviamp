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
