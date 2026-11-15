# Windows upgrade comparison, November 14, 2026

Refs #590 / draft PR #606. Production code was unchanged during this experiment. The two
disposable full applications use the final shared source with the original dependency graph
(Compose 1.11.0 / Kotlin 2.4.10) and upgraded graph (Compose 1.12.2 / Kotlin 2.4.21).
Packaging logs are `build/590-feasibility/{original,upgraded}-graph-final-shared-windows-package.log`.
After testing, all 275 original and 280 upgraded inventoried production files matched their
SHA-256 inventories. Only disposable launcher configuration and added observer code differed.

## Conditions and method

Ryzen 7 5800H / AMD Radeon Graphics, 2560×1440 display at 60 Hz, scale 1.0, unchanged
My Custom Plan 1. Matched maximized parent surfaces were 2560×1369, OpenGL, with the same
JDK 21.0.13 runtime and JVM memory/GC options. Power policy was not changed. An identical
loopback fixture supplied two silent 600-second WAVs, artwork, short metadata and threefold
long metadata; volume remained zero. Each app had its own synthetic profile. No real profile
or TV/phone state was changed.

Computer Use established each state and independently checked visible painting/motion before
and after measurements. `ApplicationRenderProbe` counted parent/owned frames and process CPU
over ten seconds, without its own capture API. HotSpot compiler counters bracketed the sample;
compiler elapsed time is not process CPU and was never subtracted. Separate eight-sample GPU
intervals also recorded process CPU and whole-desktop DWM counters. No build/profiling ran
during accepted CPU intervals. Early short-settle/transition intervals remain in the record.

### Observation overhead

JavaScript reset did not release the installed Computer Use helper. A paused upgraded sample
used 4.374%, then 4.531% on repeat with essentially no compiler activity and zero parent frames.
Thread CPU brackets localized most recorded Java-thread cost to AWT-Windows (359.38 ms).
A separate target-filtered ETW trace recorded 158 incoming accessibility requests, all attributed
to the live task-owned Computer Use helper, with zero lost events. Releasing that verified helper
produced zero events/losses; the unchanged app then used 0.469% CPU with moving text.

Only the exact task-owned helper processes were closed, after checking PID, creation time and
parent relationships. New observation sessions were released again before subsequent V5 timing.
The product's accessibility remained enabled. V2–V4 samples are diagnostic, including earlier
geometry changes and observation-connected readings; they are not the matched acceptance table.
An initial V5 baseline static verification exposed the Windows maximize-hover layout popup and
was rejected for visibility. The unobscured repeat is reported below. This does not prove every
earlier high reading had the same cause.

## Results

CPU is percent of one core. All listed settled samples recorded **zero parent frames** with an
attached observer. The upgraded hidden tooltip surface also recorded zero frames.

| Full-app state | Original CPU | Upgraded CPU |
| --- | ---: | ---: |
| Static, short identity, paused | 1.562% | 0.156% |
| Long identity, paused, moving text | 1.250% | 0.469% |
| Progress, short identity | 4.062%, 2.812% | 4.218% settled repeat |
| Combined, text and progress | 4.062%, 3.437% | 5.936%, 3.437% |
| Playing minimized | not repeated cleanly | 1.094% |

The upgraded progress transition sample used 17.498%, 42 parent frames and a 707.417 ms compiler
bracket after changing tracks with only five seconds of settling. It is retained as loading/
transition evidence, not a settled animation result. Its following interval had zero parent
frames and a 248.994 ms compiler bracket. Original progress brackets were 141.059/95.119 ms;
combined brackets were 48.728/84.221 ms original and 144.357/194.459 ms upgraded. These are
observations, not a numerical CPU decomposition. The earlier upgraded V4 progress repeat was
also high and remains diagnostic rather than being silently replaced.

| Separate GPU interval | Original app CPU | Upgraded app CPU | Original app GPU sum | Upgraded app GPU sum |
| --- | ---: | ---: | ---: | ---: |
| Static | 0.606% | 0.597% | 0 | 0 |
| Marquee | 0.596% | 1.492% | 0 | 0 |
| Progress | 2.972% | 3.313% | 0.04093 | 0.04219 |
| Combined | 3.187% | 3.278% | 0.03923 | 0.05904 |
| Upgraded minimized | — | 3.119% | — | 0 |

Whole-desktop DWM CPU was 3.606/2.853% static, 6.649/6.251% marquee, 7.223/7.986% progress,
and 7.418/6.272% combined (original/upgraded). DWM engine sums were respectively 0/0,
1.9835/6.4837, 2.1803/2.1375 and 2.2569/5.9432. These engine sums are not percentages of whole
GPU capacity or energy. Higher upgraded marquee/combined desktop GPU readings remain recorded;
this experiment does not isolate per-surface GPU energy or establish their cause. No fixed GPU
clock was imposed, and whole-desktop counters must not be presented as app-attributed GPU work.

## Visible behavior and disposition

Both apps painted artwork, player controls, metadata and Home without requiring corrective input.
Title/artist visibly moved within their viewport while album text ellipsized. Progress advanced;
pause held the elapsed position while text continued moving. The upgraded paused long position
held at 2:32, with different text offsets before/after. The final original long position held at
2:44 with text movement. The short upgraded control progressed through 1:35, then held at 1:54;
the original short control progressed from 1:09 to 2:18. Minimized playback restored with an
advanced position and correctly painted/moving content. Normal-to-maximized resize temporarily
showed stale raster placement, then corrected on an untouched subsequent capture in both graphs.
This is not a claim of zero transient resize artifacts. Space and pointer playback actions worked.

The native accessibility tree exposed the window chrome, not the shared Compose content. A Tab
input was exercised but supplies no complete focus-traversal or spoken-screen-reader acceptance.
Existing shared focus/semantics tests and CI remain relevant; native reader coverage is still a
release-matrix requirement.

The measured upgrade does not introduce a sustained parent-redraw stream. Static/marquee CPU
is cheap in the upgraded observer intervals, and the separate playback GPU-interval CPU is close
to the original. The retained process outliers and differing whole-desktop GPU readings prevent
a universal low-CPU/compositor-budget sign-off or a speed-improvement claim. Continue Core/native
preparation under #590; keep final performance acceptance open before enabling/shipping the tvOS
host or merging the preparatory PR. Do not raise budgets, slow animations, reduce playback update
frequency or disable accessibility to close that gate.

## Evidence, reproduction and cleanup

Raw V2–V5 observer results, compiler counters, GPU JSON, target-only ETL, payload inventories and
restoration JSON remain in ignored `build/590-final-windows`. `v5-*` names identify the clean
sequence, subject to the explicit transition/visibility exclusions above. The ETW capture ran
separately from CPU acceptance. Its existing C# source compiled with installed Visual Studio
Roslyn, .NET 10.0.15 runtime assemblies and pinned TraceEvent 4.0.0; SDK `dotnet build` was
unavailable, and the initial unfiltered native-DLL reference attempt failed before correction.

The committed observer README and `PerformanceFixtureLauncher.java` document isolated launcher
preparation. Both Java sources compile with the installed JDK 21. The fixture's new
`--metadata-repeat` option leaves default behavior unchanged and was exercised in the actual
two-app run. The normal app matrix was not rerun for test-fixture/documentation-only changes.

Both owned app process families exited. Six backed-up fixture-profile files per app and both
launcher configurations were restored byte-identically; generated extra cache files were not
represented as a byte-identical whole-profile restore. The owned port-18087 fixture and final
observation helper were stopped. The user's desktop was released. No platform production source,
schema, Android minimum, release metadata or OS accessibility/power setting changed.
