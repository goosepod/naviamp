# Windows usability audit — 2026-09-05

## Result

The packaged Windows app completed the browsing and playback scenarios below without an observed crash or blocked navigation. This is an agent-led usability inspection and functional smoke test, not a study with representative users or an accessibility certification.

Three visible copy problems were fixed in shared UI resources, in English and Spanish. The search loading message was also moved out of hardcoded Kotlin into those resources. Layout and accessibility findings remain open below.

## Environment and coverage

- Real app: `apps/desktop/build/compose/binaries/main/app/Naviamp/Naviamp.exe`, using the existing saved Navidrome connection and library.
- Real-window observations: 306 × 673 including window chrome, and maximized 2560 × 1392. The Computer Use API rejects resize drags outside the current window bounds; intermediate real-window widths were not successfully set. Do not confuse the automated viewport checks with live-server tests at those sizes.
- Automated shared Compose UI: 198 tests, zero failures/errors/skips. Library scenarios cover 288 × 640, 1280 × 720, 1920 × 1080, 3840 × 2160, and 4K at double density with increased font scale. New search scenarios cover 300 × 640, 1280 × 720, and 1920 × 1080.
- Shared JVM, Android, and iOS arm64 compilation passed. This does not validate native iOS packaging or runtime behavior.
- Playback was paused after testing. No existing playlists, stations, favorites, or downloaded files were deleted. Favorite Artists sorting was restored to Last radio played and its detail layout to List. The rebuilt app was left open on Home.

## Hands-on scenarios

| Area | Actions and observed result |
| --- | --- |
| Home | Scroll through recent radio, Favorite Artists, recommendations, and mix builders. Sorting controls are absent on Home. Both narrow and maximized layouts render. |
| Favorite Artists | Open detail; select Name; verify alphabetical reorder; switch List → Grid; open an artist. Settings reflects the selection. Restore Last radio played. Compact sort menu fits beside List/Grid at narrow width. |
| Artist | Open Big Bad Voodoo Daddy; load biography and top tracks; scroll tracks beneath the pinned header. |
| Library / album | Switch Artists → Albums; observe indexing progress finish; open Air's 10 000 Hz Legend. Track list and overflow actions remain reachable at narrow width. Inspect the same album maximized. |
| Global search | Search Charlotte; observe artist results. Tab reaches Clear and Enter clears the query. New automated checks additionally cover all three result types, album selection, loading, no-results, clearing, and focus restoration at three widths. |
| Playlists | Open 90's Hip Hop smart playlist; inspect read-only explanation and overflow actions. Escape dismisses the menu. Existing playlist content was not edited. |
| Internet radio | Load station list; open New station. Required fields and disabled empty Save are visible. Escape dismisses the dialog. No station was submitted. |
| Downloads | Inspect existing Californication download, storage usage, and expandable offline dashboard. |
| Playback | Resume Surge, observe progress advance, seek to approximately 2:10, skip to Common Era, and pause. Track title, waveform, and artwork update. This is functional visual evidence, not an audio-quality measurement. |
| Lyrics / track details | Open instrumental lyrics state; open Track details from overflow; confirm narrow dialog content and Close action fit. Escape dismisses it. |
| Settings | Navigate root, Home Screen / Favorite Artists, Playback, and Equalizer. Scrolling and back navigation work. |
| Genre ontology / songs | Open Genre Mix Builder; select Ambient branch; confirm Ambient + Tribal Ambient selection; Browse songs returns AFX/Air tracks and a Load more songs action. Random mix playback and further pagination were not exercised in this pass. |

## Fixed

1. **Misleading global-search scope:** “Search tracks” became “Search music”; the screen returns artists, albums, and songs.
2. **Download-count grammar:** “1 files” became the count-independent label “Files: 1”.
3. **Platform-specific offline status on Windows:** removed Android Auto from the shared ready message; it now describes offline playback and browsing downloads.
4. **Search loading localization:** “Searching...” now uses English/Spanish resources.

All production edits are in `core/ui` common code/resources. No platform production files were modified for this audit.

The Windows package rebuilt successfully and was relaunched. The updated Search music placeholder, Files: 1 label, and platform-neutral offline-ready message were verified again in the real app.

## Open findings

Follow-up implementation and validation: [shared player workspace](player-workspace.md). The findings below describe the original audit state; the follow-up addresses row width, equalizer labels, reading/navigation contrast, and the missing Windows accessibility runtime module.

| Priority | Finding and reproduction | Impact / suggested follow-up |
| --- | --- | --- |
| Medium | Maximize to 2560 × 1392; inspect an album track list or Home Favorite Artists in List mode. Titles remain far left while durations, menus, and hearts sit near the far right. | Excessive eye and pointer travel makes row associations harder. Introduce a shared maximum readable width or a deliberate wide-screen layout, then test action proximity rather than only containment. |
| Medium | At 306 × 673, Settings → Playback → Equalizer. Frequency labels crowd together; high-frequency labels reach/clamp at the right edge. | Labels are hard to distinguish. Use compact units and/or responsive label placement, with screenshot/text-layout assertions at the minimum window width. |
| Needs investigation | Windows UI Automation exposes only the outer window/pane and title-bar controls during the tested screens. | The tool cannot identify app controls by accessible names. Keyboard Clear works and Compose semantics tests pass, but neither proves Windows screen-reader compatibility. Verify with a Windows screen reader and investigate the Compose/JVM accessibility bridge before claiming accessibility support. |
| Low | Background artwork can make muted metadata, icons, and secondary actions harder to read; particularly visible with the bright Common Era cover. | Measure contrast across representative covers and themes. Consider stronger surfaces or adaptive scrims for secondary text; this pass did not perform a numeric contrast audit. |

## Limits and repeatable checks

This pass did not test credentials or permission dialogs, destructive operations, network loss/retry, a fresh installation, remote playlist writes, every mix-builder variant, every setting, long-duration playback, multiple monitors, or a real screen reader. Ordinary screenshots cannot rule out a split-second artwork flash; the shared artwork-retention regression test passed.

Validation command:

```powershell
.\gradlew.bat :core:ui:jvmTest :core:ui:compileDebugKotlinAndroid :core:ui:compileKotlinIosArm64 --console=plain
```

Local artifacts (ignored build output):

- `build/windows-usability-validation.log`
- `core/ui/build/reports/tests/jvmTest/index.html`
- `core/ui/build/reports/library-display-sizes/`
- `build/windows-usability-package.log`

The tests are repeatable; the live observations depend on the saved account's current library. Further UX work should prioritize the wide-row layout and narrow equalizer labels, then a dedicated Windows accessibility check.
