# Smart-playlist movement (#212)

Shared draft actions move top-level conditions, conditions within their current ALL/ANY group,
groups, and ordered sorts by stable row identity. Boundary moves and stale IDs are no-ops.
No move crosses a logical container. Sort identities are editor-only and are not serialized.
Values, operators, group matching, selected libraries and limit policy remain intact.

Shared keyed Compose rows expose up/down buttons, boundary enabled states, localized position
and polite live-region semantics. Activation retains focus on that row, switching to its other
movement button if the original direction becomes unavailable at a boundary. No dragging is needed.
All three new messages are defined in every maintained resource catalog. No new setting, schema,
platform production implementation or provider write operation is introduced.

Local verification on October 26, macOS 27.0.1 / JBR 21.0.11:

- All 76 smart-playlist domain cases passed, including three movement regressions.
- All 53 smart-playlist rendered UI cases passed, including three movement regressions.
- A final three-case movement run also passed after matching the app's dark Material theme.
- Domain and UI compiled for Android debug, iOS arm64 and iOS simulator arm64; architecture checks passed.
- New checks cover edited values after movement, group containment, stable identity, stale/boundary
  operations, serialized sort precedence/reopen, keyboard/D-pad activation, disabled boundaries,
  focus retention and position semantics at 340-pixel width. The narrow rendering was inspected.

Reproduction: `:core:domain:jvmTest --tests '*SmartPlaylist*'` and
`:core:ui:jvmTest --tests '*SmartPlaylist*'`, with `:core:ui:compileDebugKotlinAndroid`,
`:core:ui:compileKotlinIosArm64`, `:core:ui:compileKotlinIosSimulatorArm64` and
`verifyCoreFirstArchitecture`. Native cross-compilation was enabled.
Local logs/capture remain under `build/verification-212/` in the feature worktree.

Packaged physical touch/remote and spoken screen-reader acceptance remain unverified. The living-room
TV remains paused at the owner's request, and Mac computer control still fails before app launch.
Rendered semantics and synthetic D-pad tests are not claims of physical-device or spoken acceptance.
