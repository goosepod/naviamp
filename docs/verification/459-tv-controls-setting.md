# TV player controls visibility

Tracking: issue #459, contributor PR #463, v2.11.0.

The common TV player may keep controls visible instead of starting its inactivity timeout.
The setting defaults off. Existing focus/animation behavior remains in the common TV UI; no
Android, Desktop or iOS production files changed. The contributor supplied physical TCL Google TV
14 / D-pad acceptance for both setting states in the PR.

The shared settings document carries the preference through export/import and sync. Common tests
cover enabled/disabled round trips, normalization and older-export defaults; the existing value-store
regression now also verifies that true survives recreation of the settings catalog.

Updated from accepted main `1b00ec12285b283f0b3d0c79b72d870ae9d284e8`. Fourteen catalog conflicts
were append-only. Every XML entry from both parents was compared and retained; full Spanish and
Portuguese catalogs match all 1,206 source keys/types, and both TV labels exist in all 17 catalogs.
The original Weblate translation commit remains an ancestor through main.

Current-main verification: 20 settings-document, 8 shared value-store, 15 TV player focus and one
catalog regression passed (44 tests, no skips/failures). Earlier combined contributor verification
also compiled the shared behavior for Android, JVM and both iOS targets. Required hosted CI gates
the final merge against current main.
