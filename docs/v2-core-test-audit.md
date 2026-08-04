# V2 Core Test Audit

This audit records whether Naviamp Core has platform-neutral proof for the behavior that Android, Desktop, and iOS must consume. It is evidence for the [Core Test Contract](v2-core-first-platform-audit.md#core-test-contract), not permission to retain parallel host product implementations.

## Audit Result

| Required proof | Status | Evidence | Remaining work |
| --- | --- | --- | --- |
| Required-action completeness | Complete | `NaviampCoreRequiredActionContractTest` invokes all 118 immediate action callbacks and all seven suspending callbacks through the one composed action graph. The strict router tests prove every command has exactly one owner. | Host adapter tests must use the same graph while Android and Desktop are mounted on Core. |
| Capability behavior | Complete for Core | `NaviampCoreRequiredActionContractTest` proves OS availability only adds the four explicit picker callbacks and cannot remove normal product actions. Existing capability-policy tests cover visible/enabled/experimental presentation. | Correct any inaccurate host capability declarations during host conversion. |
| State mapping | Complete for the current Core graph | Focused `core:ui` mapper tests cover domain-to-UI mapping, while `NaviampCoreTest` proves restored navigation, connection, playback, and product state converge into one host-neutral shell state. | Add mapper coverage with each new product model; hosts must not introduce competing presentation state. |
| Lifecycle and restoration | Complete at the host-neutral boundary | `NaviampApplicationRuntimeTest`, `NaviampApplicationServicesTest`, navigation tests, connection tests, and playback-session tests cover once-only restoration, lifecycle forwarding, queue/current-target restoration, reattachment, and shutdown policy. The restored `NaviampCore` composition test proves those inputs rehydrate the complete shared state graph. | Physical Android lifecycle/service survival remains a platform acceptance item, not missing Core ownership. |
| Feature results and failures | Complete for the current controller catalog | Focused common controller tests cover success, empty/stale inputs, provider failures, retry/cancellation, status publication, and restoration for connection, content/detail, playlists, radio/mixes, Downloads/offline, settings, and Now Playing/playback. | Preserve this result matrix as features are added; device-only native failures remain host adapter tests. |
| Reusable platform-adapter contracts | Foundation complete | `core:testkit` supplies reusable playback-execution and application-session contract harnesses, with JVM and iOS tests proving the suites themselves. It is a separate KMP test-support module so every host can consume identical expectations. | Android and Desktop must apply these suites to their real adapters during Core mounting; the initial iOS adapters must do the same. Add focused reusable suites when new common ports gain multiple host implementations. |

## Rules Established by the Audit

- A visible product action is required and typed. Only an unavailable operating-system mechanism may be optional, and its absence must be represented by a declared capability.
- Restoration enters Core as host-neutral navigation, connection, playback, and product state. A host may restore native resources, but it may not reconstruct a separate product graph.
- Feature controllers publish common results and failures. Hosts execute native effects and return their results through narrow ports.
- Reusable adapter contracts live in `core:testkit`; host-specific test suites consume them rather than restating or weakening the Core contract.
- Passing this audit does not close Milestone 4. Android and Desktop still need to mount the complete Core, delete superseded product wiring, and prove their real adapters against the reusable suites.
