# Artist details settings subsection (#310)

Under Settings → Experience, open **Artist details**. The subsection contains artist
information, Top Tracks visibility and its maximum, and playlist visibility. The existing
values, defaults, persistence, settings export/import and sync behavior are unchanged.

The Top Tracks title, value, slider and help text now use the same horizontal gutters as
the other settings controls. No platform production files or new settings are required.

## Manual checks

1. Confirm the three artist-page switches and the maximum are absent from the Experience
   overview and present under Artist details. Album information stays on the overview.
2. Change each switch and maximum. Go Back, reopen the subsection, restart the app, and
   check the saved values and corresponding artist-page content.
3. Disable Top Tracks: its maximum remains visible but disabled and keeps its selected value.
   Enable it again and try 5 through 50 in steps of five.
4. At narrow and wide window sizes, check the slider, title, selected value, help text and
   switch rows share the same content edges and do not run into the outer gutters.
5. Check keyboard navigation, Back, and a long-label translation. Repeat on available platforms.

Rendered common tests exercise the subsection navigation, retained preferences, every maximum,
disabled control, and geometry at 320 and 960 dp. Shared Android/JVM/iOS compilation and the
Core architecture check cover the shared implementation. Manual acceptance remains pending.

On 2026-10-17, all nine focused rendered tests passed. Shared Android, iOS ARM64 and
iOS Simulator ARM64 compilation and `verifyCoreFirstArchitecture` passed. All 17 language
catalogs contain the new title and subtitle exactly once. No platform production files changed.
