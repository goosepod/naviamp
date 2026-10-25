# TV settings acceptance fixes (#332)

The living-room TV acceptance of candidate `a3776547` found a focus trap on Radio batch sliders and
an About page that did not expose the release changelog. The physical TV remains untouched while
testing is paused at the owner's request.

The shared Radio controls accept an explicit vertical-focus navigation policy, enabled by the shared
TV settings presentation. Up/Down move focus before Material Slider can treat them as value changes;
Left/Right retain the existing detents, values and accessibility semantics. Desktop's ordinary
settings retain their existing key behavior. Shared TV settings rows also handle vertical navigation
explicitly, including lazy-list focus scrolling.

TV About renders the same `NaviampAboutUi.changelog` entries used by other presentations. Each entry
is focusable and wraps completely rather than truncating after two lines, allowing D-pad navigation
to scroll through the list. Existing translated heading/empty labels are reused; English release
entries are unchanged. No new settings, string resources, schema or platform production code.

Synthetic shared UI tests exercise horizontal value changes, vertical focus exit without changing
values, and remote scrolling from version information through the final complete changelog entry.
Verification on the acceptance baseline: eight focused UI/policy tests passed with zero failures,
errors or skips. Android debug, iOS ARM64 and iOS Simulator ARM64 compilation and Core architecture
verification passed. The input tests exercise the actual Material Slider and shared TV list in a
synthetic Compose window, using no account data. The focused branch is also verified on main for PR
review; results are recorded in the PR.

Physical TV retest, other display
scales and final packaged acceptance remain pending. No release is authorized.
