# Linux cached raster compositor (#116)

## Status

Linux production previously installed no raster presenter unless
`NAVIAMP_RASTER_FORCE_SKIA=true` was set. Scrolling metadata and smooth waveform progress therefore
fell back to inline Compose animation and repainted the complete application surface. This was
inconsistent with the behavior and documentation merged in PR #106.

The issue branch now installs an X11 presenter by default. Shared Kotlin still owns cached pixels,
linear motion timelines, clipping geometry, visibility, interaction, and accessibility. The native
adapter uploads premultiplied ARGB pixels to cached X11 pixmaps, applies the shared rounded clip, and
moves or reveals child windows. Its X Shape input region is empty, so the shared Compose surface
continues to receive pointer input. No Compose or Skia rendering occurs for an animation frame.

Transparent child windows require an active X11 compositing manager. The presenter now checks the
standard `_NET_WM_CM_Sn` selection on the parent visual's screen before creating any native window.
When no compositor owns that selection, native presentation declines cleanly and shared Compose
content remains visible. CI starts `xcompmgr` explicitly instead of relying on an accidental Xvfb
configuration. Fully clipped reveal layers are unmapped rather than forcing a one-pixel X11 window.

Final release acceptance remains open until the packaged application is measured in a visible,
accelerated Linux desktop session. The current workstation is a virtual machine whose visible X11
session reports Mesa llvmpipe and `Accelerated: no`; its CPU results prove the redraw boundary and
provide a software-rendering upper bound, but they do not measure representative GPU/compositor
cost.

## Reproduction before the fix

The integrated 1000 x 740 real-window probe ran under Xvfb on Ubuntu 26.04.1, JDK 21.0.12. Xvfb
could not create a Skiko GL context and used `SOFTWARE_FAST`. Each case used five seconds of warm-up
and ten seconds of measurement.

| State | Process CPU | Parent frames / 10 s |
| --- | ---: | ---: |
| Static | 0.10% | 0 |
| Marquee | 44.90% | 647 |
| Waveform | 38.70% | 643 |
| Combined | 37.70% | 646 |

The existing forced isolated Skia presenter reduced the redraw scope to zero parent frames, but
still measured 27.50% marquee, 10.20% waveform, and 24.70% combined CPU under the same software
renderer. It was a useful structural diagnostic, not an acceptable production solution.

## X11 presenter measurements

The final probe verifies changed pixels in the expected animated region, unchanged sibling pixels,
nonblank cached text, zero parent frames, popup stability, and pointer delivery to the shared
waveform. It runs against the combined local tree containing PR #113 followed by PR #115.

| Environment | Static | Marquee | Waveform | Combined | Parent frames |
| --- | ---: | ---: | ---: | ---: | ---: |
| Visible XFCE/X11, Mesa llvmpipe, three trials | 0.20–0.70% | 0.90–1.10% | 0.40–0.60% | 1.20–1.50% | 0 in every state |
| Xvfb `SOFTWARE_FAST` | 0.50% | 1.10% | 0.40% | 1.40% | 0 in every state |

All three visible trials verified marquee, waveform, and combined pixel changes while recording zero
sibling changes and zero parent frames. Each also passed 12 popup transitions and a Robot-generated
waveform click reached the shared handler. The CI-equivalent Xvfb trial passed the same assertions.
These checks rule out a blank, frozen, or input-blocking native surface as the explanation for the
low CPU result.

## Reproduce

Run three visible trials on an X11 desktop with a fixed window size, display scale, refresh rate,
and power state:

```sh
scripts/animation-probe/measure-linux.sh
```

Set `NAVIAMP_PROBE_TRIALS=1` for a quick diagnostic. Results and the GL renderer description are
written under `build/linux-animation-probe`. CI uses Xvfb and enables `NAVIAMP_PROBE_VERIFY=true`,
so missing motion, parent redraws, sibling changes, blank pixels, popup loss, or intercepted waveform
input fail the Linux job rather than merely appearing in a log.

## Remaining packaged-app acceptance

Before closing #116 and shipping v2.7.0:

- run at least three cold and warmed trials in the packaged Linux application on accelerated
  graphics, recording static, marquee, waveform, and combined process CPU plus compositor/GPU cost;
- visually confirm placement, rectangular and rounded clipping, resize behavior, menus, tooltips,
  seeking, and track replacement;
- verify transparent rendering with compositing enabled, safe fallback with compositing disabled,
  and compositor detection under XWayland;
- verify paused, hidden/minimized, restored, and idle states stop continuous work;
- inspect the shared accessibility tree with a Linux accessibility inspector;
- rerun the complete Linux and cross-platform matrix on the final integrated tree.
