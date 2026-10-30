# Visible packaged-app observer

`ApplicationRenderProbe.java` is an opt-in test agent for the real packaged JVM application.
It wraps existing Skiko render delegates to count frames without changing their behavior, records
one-core process CPU and available JVM compilation time over ten seconds, and captures before/after images
outside the CPU interval. It does not choose tracks, change playback cadence, or create UI.

Compile with a JDK, creating a manifest containing `Premain-Class: ApplicationRenderProbe`:

```sh
javac -d build/render-observer scripts/animation-probe/ApplicationRenderProbe.java
printf 'Premain-Class: ApplicationRenderProbe\n' > build/render-observer/manifest.mf
jar cfm build/render-observer/probe.jar build/render-observer/manifest.mf -C build/render-observer ApplicationRenderProbe.class
swiftc -module-cache-path /private/tmp/naviamp-swift-cache scripts/animation-probe/mac-visible-window-guard.swift -o build/render-observer/mac-window-guard
```

Use an isolated staged app and development profile, not the user's installed app or production
profile. Add these JVM options to the copied app's launcher configuration, using absolute paths:

```text
-javaagent:/absolute/path/probe.jar=/absolute/path/output
-Dnaviamp.probe.macWindowGuard=/absolute/path/mac-window-guard
```

For a trimmed runtime without `java.instrument`/`java.management`, compile the same class
into a test JAR and use its launcher entry point in a **disposable copy** of the app:

- Preserve the original launcher configuration and hashes of the production JARs/native runtime.
- Add the observer JAR to the copied configuration's `app.classpath` entries.
- Replace only `app.mainclass` with `ApplicationRenderProbe`.
- Add `-Dnaviamp.probe.directory=/absolute/path/output`; omit `-javaagent`.

The wrapper starts the observer and invokes the unchanged application's original main method.
CPU uses `ProcessHandle` from `java.base`; compilation time is explicitly `unavailable` when
the runtime has no management module. Missing process CPU is an error, never a zero reading.
Do not ship this configuration. Also launch the original release configuration separately to
verify normal startup; distinguish that run from the instrumented configuration in the record.

When Computer Use supplies the physical observations, add `-Dnaviamp.probe.capture=false`.
The agent then records CPU/frames without using its own screen-capture API. Its result correctly
reports `physical_captures=false/false`; record the independent visible motion observations
separately and do not count that flag as automated pixel acceptance.

The guard uses macOS CGWindow ownership and native placement rather than cached AWT placement.
It rejects any foreign window overlapping the client rectangle, including the automation pointer
annotation. Actual AWT decoration insets exclude the title bar. Screenshots are restricted to owned,
onscreen client rectangles, with a nonblank color check. Screen-recording access must already be
available to the test process. Without the macOS guard, the older AWT capture fallback remains;
that fallback's active-window check does not establish unobscured content.

Write a unique alphanumeric/hyphen/underscore name to `output/phase` to request each measurement.
Wait for its `.txt` result before changing state. Physical capture flags must both be true for visible
acceptance. Verify actual pixel movement and unchanged sibling regions independently; the observer
alone does not certify motion or smoothness. Minimized/hidden samples should fail physical capture.
Retain all samples, including compilation outliers and rejected captures. Record display size, scale,
refresh, power, fixture and exact app commit. GPU traces must run separately from CPU samples.

`android-tv-fixture.py --mixed-metadata --tracks 2 --albums 2` supplies a short identity for static
and progress-only states and long identities for marquee and combined states. Keep the fixture
loopback-only and playback muted. Exercise menus, diagnostics, pause, minimize/restore, resize,
clipping, input and accessibility, and apply the budgets in `docs/performance/desktop-popup-animation-136.md`.
A low CPU measurement or an attached render delegate alone is insufficient acceptance evidence.

## Windows compiler attribution with a trimmed runtime

The observer records each surface's scale. If its `compilation_ms` is unavailable because the
packaged runtime omits `java.management`, bracket a phase with the installed JDK's HotSpot counters:

```powershell
scripts/animation-probe/measure-windows-application-phase.ps1 `
  -ApplicationProcessId <child-JVM-PID> -ObserverDirectory <absolute-observer-output> `
  -Phase <unique-phase-name> -JdkDirectory <installed-JDK-directory>
```

This does not control the UI. Establish and visibly verify the state first. The helper reads
compiler counters before requesting the observer interval and after its result exists, avoiding
diagnostic attachment during the CPU sample. It verifies the result PID, refuses an existing
phase result and times out if the observer does not respond. Missing counters fail explicitly.
The `*-jit.json` reports aggregate compiler elapsed time across that bracket, not process CPU or
an exact partition of the observer interval. Keep high samples even when compilation occurs.

Native WPR is under `Windows/Sysnative` from a 32-bit shell and `Windows/System32` from a
64-bit shell. A missing PATH command does not establish that WPR is unavailable. If Windows
rejects kernel profiling with `0xc5585011`, an administrator must run the capture. Keep the
disposable Naviamp application unelevated, visible and at the measured size, then use:

```powershell
scripts/animation-probe/capture-windows-native-cpu.ps1 -ApplicationProcessId <child-JVM-PID>
```

The helper requires an administrator shell, validates a disposable Naviamp build in this checkout,
records for twelve seconds, and saves under ignored `build/windows-native-cpu`. It creates a unique
WPR instance and only stops/cancels that instance. It does not elevate the app, change OS policy,
or control the UI. The owner successfully ran its administrator capture on October 29 (30 seconds).
Future captures also save wall-clock interval brackets in `recording-window.json`, separately from
WPR setup/rundown; this metadata addition is syntax-checked, not re-recorded. Keep raw ETL data local;
restrict analysis to the recorded target PID.

### Analyze a saved native CPU capture

`native-cpu-analysis/` uses Microsoft's TraceEvent 4.0.0 package. With a .NET 10 SDK, run:

```powershell
dotnet run --project scripts/animation-probe/native-cpu-analysis -- `
  <trace.etl> <target-PID> <start-seconds> <end-seconds> build/native-symbols
```

Times are relative to the ETL session start; select them from capture brackets when available.
The optional final argument enables matching Windows PDB downloads from Microsoft's public symbol
server. Without it, module/thread counts still work, but function-specific ETW classifications
cannot be interpreted. The tool creates a derived ETLX beside the local trace. It reports only
samples belonging to the specified PID, missing-stack counts, time bins, exclusive leaf modules,
and overlapping inclusive stack groups. Its top stacks contain local addresses; keep outputs local.
Use the saved target identity/start time to exclude PID reuse before interpreting results.

The October 29 analyzer source was compiled with the installed Visual Studio Roslyn compiler,
.NET 10.0.14 runtime assemblies and the exact NuGet DLLs, then exercised against the owner's ETL;
this machine has no .NET SDK, so the SDK project/restore command was not executed here.
TraceEvent documentation: https://github.com/microsoft/perfview/blob/main/documentation/TraceEvent/TraceEventLibrary.md

Zero lost events does not guarantee complete JVM stacks. JIT frames lack Java method mapping in
this capture. ETW stack-walking cost is visible in the target itself; never treat an instrumented
CPU total as a clean acceptance measurement or subtract inclusive overlapping groups to manufacture
a passing baseline. See the October 29 verification report for the actual counts and limits.

## Windows native diagnostic control

`windows-native-sampler/` contains the October 29 investigation helper and a plain Swing control.
Neither is linked to or packaged with Naviamp. Use only disposable processes with verified PID/TID
identities. The sampler rejects a thread belonging to another process, limits collection to 30
seconds, and immediately resumes the target before allocation, symbol lookup or stack unwinding.
It copies a bounded stack region while suspended. Do not use it during acceptance measurements.

Build with the installed Visual Studio 2022 tools and Windows SDK:

```powershell
cmake -S scripts/animation-probe/windows-native-sampler -B build/native-sampler/out `
  -G 'Visual Studio 17 2022' -A x64
cmake --build build/native-sampler/out --config Release
# Read the disposable JVM's thread IDs from jcmd <pid> Thread.print -e first.
build/native-sampler/out/Release/sampler.exe <pid> <native-thread-id> 15 build/native-samples.txt
javac -d build/native-sampler scripts/animation-probe/windows-native-sampler/AwtIdleControl.java
java -cp build/native-sampler AwtIdleControl
```

These are wall-clock instruction snapshots, not ETW on-CPU samples. A high count at a wait syscall
does not mean it consumed that fraction of CPU. DbgHelp export names may identify the closest export
rather than the internal function. JVM-generated frames have no supported native unwind metadata
here: ignore every frame after the first unresolved/JIT address. Reads beyond the copied stack
region may see live target memory; this prototype is not a general-purpose consistent stack walker.
Use the native DLL portion only as a lead for a proper CPU trace, not proof of causation. The helper
was compiled and used locally before testing stopped; relocation of its unchanged source was not
rebuilt afterward, in accordance with the owner's stop instruction.

Keep the control and application foreground, unobscured, equally sized, and under the same display,
power and input conditions. Without those conditions, a low control reading cannot establish an
application or runtime regression. Record window/capture approval failures rather than bypassing
them. A plain Java control's CPU cost does not waive Naviamp's application performance budget.
