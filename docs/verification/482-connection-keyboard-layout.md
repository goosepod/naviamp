# Connection screen keyboard layout

Tracking: #482, selected for v2.11.0.

The owner's Pixel 10a screenshot showed the connection form shifted behind the status bar, the
navigation row displaced upward, and unused space above the open keyboard. Read-only window
inspection confirmed `sim={adjust=pan forwardNavigation}` while the Android host applied both
safe-drawing and IME padding. Evidence: `/private/tmp/naviamp-phone-connection.png`.

The shared `naviampContentInsets` policy now owns safe-drawing and IME inset consumption, called by
Core for both standard and TV shells. Android and iOS hosts delegate instead of maintaining their
own copies. Android's manifest declares `adjustResize`, allowing the shared inset-aware scroll
layout to respond to keyboard bounds without Android panning the entire window.

References: https://developer.android.com/develop/ui/compose/system/setup-e2e and
https://developer.android.com/develop/ui/compose/system/insets-ui.

The rendered viewport regression checks a fixed top inset, keyboard opening and height changes,
navigation/keyboard overlap consumption, and restoration after keyboard dismissal. This test and
all four connection-form focus tests passed. The common presentation compiled for Android, JVM,
iOS ARM64 and iOS Simulator ARM64 before the host changes were applied. Architecture verification
passed. Log: `/private/tmp/naviamp-482-common.log`.

Platform diff review:

- `apps/android/src/main/AndroidManifest.xml`: declares Android's native Activity keyboard window mode.
- `apps/android/src/main/kotlin/app/naviamp/android/MainActivity.kt`: removes duplicated inset policy
  from the Android Activity composition boundary; shared Core now applies it.
- `apps/ios/src/iosMain/kotlin/app/naviamp/ios/NaviampIosApplication.kt`: removes the same duplicated
  policy from the UIKit Compose-view-controller boundary; shared Core now applies it.

No settings, schema, or user-facing strings changed. Existing owner phone and emulator data must
be preserved; no reset or uninstall is part of acceptance. Physical keyboard/focus acceptance is
separate from the deterministic shared inset test.

Host validation passed: Android debug assembly, both iOS host compiles, and architecture verification
(`/private/tmp/naviamp-482-host.log`). APK signature verification passed and the packaged manifest
contained `windowSoftInputMode=0x10` (adjustResize). The update installed successfully on the connected
Pixel 10a using an explicit serial and replacement install, preserving persisted app data. The
release package and existing emulator were untouched. The owner subsequently confirmed on the
physical phone: “The new keyboard onscreen issue is fixed.” No data reset was performed.

These local checks and the phone APK include the artwork preview changes from #480/#481. The
connection fix is also submitted as an independent patch against main; its hosted checks validate
that isolated integration tree.
