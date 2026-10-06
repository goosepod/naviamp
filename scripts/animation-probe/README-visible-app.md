# Visible packaged-app observer

`ApplicationRenderProbe.java` is an opt-in test agent for the real packaged JVM application.
It wraps existing Skiko render delegates to count frames without changing their behavior, records
one-core process CPU and JVM compilation time over ten seconds, and captures before/after images
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
