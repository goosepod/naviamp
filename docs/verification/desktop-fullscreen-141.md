# Desktop fullscreen — issue #141

Fullscreen is application-window placement, independent of the Full player / Split player layout.
Its shared model, restoration state, keyboard policy and controls live in Core UI. Desktop only
translates Compose Desktop window state and executes window operations. No playback, queue,
navigation or portable settings change is involved. Fullscreen starts off on every launch;
windowed geometry remains the device-local geometry saved by the host, even when quitting fullscreen.

## Controls

- Enter/Exit fullscreen icon in shared bottom navigation, including the player navigation.
- F11 toggles fullscreen. On macOS Control–Command–F also toggles it.
- Escape dismisses a focused menu/dialog first. With no overlay, Escape exits fullscreen.
- Native window controls report back into the same shared state.
- Exiting restores the previous windowed geometry and placement, including maximized placement.
  A saved position on a removed monitor falls back to platform-default placement.

## Automated checks

Shared tests cover restoration over repeated transitions, windowed resize, native-control state
publication, failed native effects, Escape precedence, exact shortcut modifiers and removed monitors.
Rendered tests exercise the accessible button labels, F11, Control–Command–F, menu dismissal,
held-key behavior and absence of the control on hosts without a window effect.

```sh
./gradlew :core:ui:jvmTest :apps:desktop:desktopTest \
  :core:presentation:compileDebugKotlinAndroid \
  :core:presentation:compileKotlinIosArm64 \
  :core:presentation:compileKotlinIosSimulatorArm64
```

## Visible-window animation probe

The existing probe accepts `NAVIAMP_PROBE_FULLSCREEN=true` to measure static, marquee, waveform,
and combined content in windowed, fullscreen and restored placements. Each phase uses a ten-second
CPU sample after settling, records parent frames, and checks nonblank moving pixels and unchanged
siblings. Window size, scale and display refresh facts are logged for each placement; compare like
placements under the same display and power conditions. This is a synthetic rendering check, not
proof of real application acceptance.

```sh
NAVIAMP_PROBE_INTEGRATED=true NAVIAMP_PROBE_FULLSCREEN=true NAVIAMP_PROBE_VERIFY=true \
  ./gradlew :core:ui:playerAnimationProbe
```

## Native acceptance matrix

Before marking all desktop platforms complete, verify macOS, Windows and Linux in visible sessions:

- Native and shared entry/exit; F11; macOS Control–Command–F; Escape with menus and dialogs.
- Playback/queue/position and route preservation; both player layouts; phone-width desktop window.
- Mouse hover/click targets, waveform seek, keyboard focus and accessibility labels.
- Resize before and after fullscreen, repeated entry/exit, minimize/restore and quit/relaunch.
- Multiple displays, differing scale factors and removal of the original monitor.
- Static, marquee, waveform and combined animation CPU, parent frames and compositor/GPU cost.
  Confirm that content visibly moves in fullscreen and after restoration.

Windows/Linux and multiple-monitor physical checks remain pending until those sessions are available.

## macOS results (2026-10-01)

Verified on Apple M1, AC power, two LG QHD displays at 2560×1440 / 75 Hz / 1× scale,
using JetBrains Runtime 21.0.11 and the staged application.

- Shared icon, F11, Control–Command–F and the native green button enter/exit fullscreen.
  A menu consumes the first Escape; the second exits fullscreen. Settings and the paused
  track remain selected, and accessible Enter/Exit labels follow native completion.
- The packaged application has fully visible bottom controls in fullscreen and after restore.
  A temporary probe initially used a magenta footer marker; production UI contains no such marker.
  The fixture now uses a white marker to avoid confusing it with application artwork.
- `:apps:desktop:desktopFullscreenProbe` passes three floating/fullscreen/restore cycles:
  exact native bounds and shared geometry restore, and Compose content matches client bounds.
  With `NAVIAMP_PROBE_MAXIMIZED=true`, three maximized/fullscreen/restore cycles also pass.
- AppKit updates decoration insets after AWT's last reshape. The native completion adapter
  refreshes the macOS peer's insets at unchanged bounds before layout. On restore, the
  28-pixel title-bar inset returns and bottom controls fit. A maximized restore must exit
  the fullscreen Space before executing native maximization.
- Shared UI JVM tests and Desktop adapter tests pass. Shared presentation compiles for JVM,
  Android, iOS ARM64 and iOS simulator ARM64.

Synthetic visible animation measurements (process CPU, percent of one CPU core):

| State | Windowed | Fullscreen | Restored |
| --- | ---: | ---: | ---: |
| Static | 0.421 | 0.341 | 1.246 |
| Marquee | 0.782 | 0.292 | 0.286 |
| Waveform | 0.303 | 0.268 | 0.315 |
| Combined | 0.306 | 0.284 | 0.303 |

All measured parent-surface frame counts are zero. Text and waveform pixel movement checks pass,
unchanged siblings remain unchanged, popup text remains visible across 12 samples, and waveform
pointer input passes. Windowed/restored size is 1000×740; fullscreen is 2560×1440. These placements
have different areas and should not be interpreted as a controlled comparison between areas.

Concurrent WindowServer samples range from 21.8–51.6% across the shared desktop. They do not
isolate this application's compositor/GPU cost. Real application animation budgets, minimized/
restored animation, mixed-scale displays and Windows/Linux acceptance remain pending; the synthetic
probe alone does not close the performance acceptance criteria.

```sh
./gradlew :apps:desktop:desktopFullscreenProbe
NAVIAMP_PROBE_MAXIMIZED=true ./gradlew :apps:desktop:desktopFullscreenProbe
```

## Windows checkpoint: October 2, 2026

Windows 11, Ryzen 7 5800H, AMD Radeon Graphics, 2560 x 1440 at 60 Hz, scale 1.0.
The packaged OpenGL development app reproduced a fullscreen exit from maximized
placement restoring a floating window. Compose's native Maximized setter does not
exit fullscreen; changing only WindowState after native exit allowed the exit
reshape to overwrite the requested maximization. The adapter now executes both
native placement operations together before publishing the requested state.

The visible native probe passes three floating and three maximized entry/restore
cycles with exact restored bounds, matching Compose/client bounds, and physically
visible bottom-edge controls. Maximized bounds restore to (-8, -8, 2576, 1408),
with a 2560 x 1369 client area; floating bounds restore to (48, 48, 1000, 740).
Six shared controller regressions and all five Desktop tests pass. Shared UI
compiles for JVM and Android; iOS ARM64 and simulator ARM64 klib cross-compilation tasks also pass (native app/link/runtime testing still requires macOS).
The Desktop restoration test injects monitor facts and uses absolute coordinates,
so it verifies the adapter without requiring a physical display in headless Linux CI.

The strengthened probe also passes fullscreen minimize, native activation and restore during each floating and maximized cycle, waiting for real on-screen geometry rather than the minimized flag alone. These checks resolve the reproduced maximization failure. Real-app lifecycle,
overlay/animation, accessibility and mixed-scale/multi-monitor acceptance remains
pending; this checkpoint alone does not complete #141.

## Platform diff accountability

- `apps/desktop/src/desktopMain/kotlin/app/naviamp/desktop/app/DesktopWindowEffect.kt` translates
  Compose Desktop/AWT window geometry, monitor coordinates and native placement, and manages
  AppKit fullscreen callbacks plus the macOS AWT peer inset refresh. These JVM/macOS APIs cannot
  compile in common Kotlin. The refresh uses JDK internal exported APIs, so a future runtime
  upgrade must rerun the native probe.
- `apps/desktop/src/desktopMain/kotlin/app/naviamp/desktop/app/Main.kt` wires the native WindowState,
  owns native listener lifetime and writes shared windowed snapshots to the device-local geometry store.
- `apps/desktop/src/desktopMain/kotlin/app/naviamp/desktop/app/DesktopNaviampCoreHost.kt` passes the
  optional shared controller from the existing AWT host into Core; no product behavior is added.

Fullscreen selection, saved restoration state, shortcut/Escape policy, monitor fallback policy and
all fullscreen UI live in common code. No Android or iOS production files change.

A repeated floating restore also exposed a deferred native exit overwriting restored
geometry. Executing the AWT exit before publishing Compose geometry fixed it; three
cycles now preserve exact bounds and the visible footer after minimize/restore.
