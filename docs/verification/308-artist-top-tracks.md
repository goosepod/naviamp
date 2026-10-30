# Artist Top Tracks controls (#308)

Settings → Experience → Artist details includes “Show Top Tracks on artist pages” and “Maximum Top Tracks”.
Visibility defaults to on and the maximum defaults to 10. Choices are 5, 10, 15, 20, 25,
30, 35, 40, 45, and 50. The maximum control is disabled while the section is hidden and
retains its saved value. Fewer tracks can appear when fewer popular tracks match the library.

Both settings are owned by shared Core, persisted locally, and included in settings export,
import, and sync. Older settings retain the defaults; imported limits are clamped to 5–50
and rounded to the nearest five. Every maintained language catalog contains the new labels.

Turning the section off skips its discovery request and cancels pending work. Changing the
maximum updates the open artist page without reloading its discography. Late results are
discarded after hiding, changing artist, or resetting the source. Raising the maximum expands
an older, smaller popular-track cache. Album popular-track markers and Radio retain their
existing cache behavior. No platform production files or database migrations are required.

## Automated checks

On 2026-10-17, 58 focused JVM tests passed: 34 domain, 20 presentation, and four
rendered UI tests. Shared Android, iOS ARM64, and iOS Simulator ARM64 compilation
and `verifyCoreFirstArchitecture` passed in the same run. XML checks verified the five
new keys and count placeholder in all 17 catalogs. The native iOS application still
requires a macOS runner; shared compilation is not a native integration test.

- Domain: supported values, normalization, older and invalid imports, export/import/sync
  round trips, cache expansion and reuse of a short complete response.
- Presentation: local persistence, live visibility and maximum changes, skipped hidden
  discovery, cancellation, and rejection of stale artist/source results.
- Rendered shared UI: all slider steps, disabled-state retention, section visibility,
  and common mapper limits.
- Shared Android, JVM, and both iOS ARM64 target compilation; Core architecture verification.

## Manual acceptance

1. Open Settings → Experience → Artist details. Confirm the default is on with a maximum of 10.
2. Visit an artist with many matched Top Tracks. Try every maximum, including 50.
   The displayed count must never exceed the chosen maximum. A smaller count is valid
   when the library or discovery service has fewer matches.
3. Turn Top Tracks off. Confirm the whole section disappears and other artist content remains.
   Turn it back on and confirm the previous maximum is retained.
4. Change the setting with an artist already loaded, return to that page, and verify it updates.
   Switch artists or servers during a slow lookup; the previous results must not reappear.
5. Restart the app, export/import settings, and sync to another platform. Confirm both values
   survive. Check the controls with keyboard/accessibility navigation and on television.

Manual acceptance and full platform CI remain required before release. This work does not
create a release, tag, or announcement.
