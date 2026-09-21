# Independent font sizes: follow-up verification

Issue: https://github.com/goosepod/naviamp/issues/25

## Changes

- Artist and album descriptions share their 14sp/20sp reading typography and expansion control.
  Expansion follows the collapsed text's actual layout overflow, including descriptions shorter
  than 260 characters with four or more lines. Resizing and changing text size update the control.
- Regular and television settings share font-size resource mappings.
- The same rendered UI regression suite runs on JVM/Desktop and Android instrumentation.

## Automated results

Windows JVM: 14 settings sync tests and 12 targeted UI/text tests passed. Shared Kotlin metadata
compilation and the Android debug app/test APK builds passed. Native iOS execution was not performed
on this Windows host.

Physical Pixel 10a, Android 17/API 37, 1080 x 2424: all five tests in
`NaviampFontSizeRenderingTest` passed:

1. Every General/Now Playing combination at injected system font scales 1.0 and 2.0, checking
   rendered text density and unchanged physical density.
2. Real mini-player, queue, and lyrics components respond to the player preference independently.
3. Actual settings controls change and independently reset both preferences at 2.0 font scale.
4. A short four-line description expands fully, collapses, and resets for another item.
5. Overflow responds to width and font-size changes without using a character-count threshold.

Settings tests also cover all nine combinations through shared export/normalization, older data
defaults, and rejection of unknown enum values consistent with other interface preferences.

The initial instrumented run could not initialize the older transitive Espresso input injector on
Android 17. The test-only Espresso 3.7.0 dependency resolves this compatibility problem; its release
notes document replacement of reflective `InputManager.getInstance` access:
https://developer.android.com/jetpack/androidx/releases/test#espresso-3.7.0

Reproduce:

```text
gradlew :core:domain:jvmTest --tests app.naviamp.domain.settings.SettingsSyncDocumentTest
gradlew :core:ui:jvmTest --tests app.naviamp.ui.NaviampFontSizeRenderingTest --tests app.naviamp.ui.NaviampFontSizeTest --tests app.naviamp.ui.ProviderDescriptionTest --tests app.naviamp.ui.ProviderRichTextTest
gradlew :core:ui:assembleDebugAndroidTest
adb install -r core/ui/build/outputs/apk/androidTest/debug/ui-debug-androidTest.apk
adb shell am instrument -w -r -e class app.naviamp.ui.NaviampFontSizeRenderingTest app.naviamp.ui.test/androidx.test.runner.AndroidJUnitRunner
```

## Live application observations

The existing phone app had a different signing key. A separate `app.naviamp.android.fontreview`
debug installation preserved the existing app and data; the user logged into the review build.

- Settings page renders and wraps the player section title at Large.
- Changing General from Standard to Large leaves the mini-player's text bounds unchanged.
- Changing Now Playing to Small reduces mini-player text while General remains Large.
- A live Big Bad Voodoo Daddy biography renders with readable Standard typography and expands;
  its collapsed layout and expansion control also work at Large.
- The sampled Save My Soul album supplied no description, so live album-description verification
  remains incomplete. Album and artist screens now delegate to the same tested component.
- Both preferences were returned to Standard and playback paused after testing.

## Separate renderer finding

The full-player native title and waveform appeared at the top of the window at both Small and
Standard. Regular Compose text remained correctly positioned. This prevents claiming complete
full-player visual acceptance.

`AndroidRasterPresenter` was introduced in commit `6467b03f` (PR #106) and is unchanged by this
feature. It applies geometry, layer, and visibility transactions directly to a SurfaceView's own
surface control. Android documents this control as effectively read-only: applications should
parent their own child surfaces beneath it, since SurfaceView can override its geometry.
This is a strong explanation for the observed placement failure, pending a renderer fix and retest:
https://developer.android.com/reference/android/view/SurfaceView#getSurfaceControl()

No animation implementation or platform production code was changed in this font-size follow-up.
Any renderer fix must retain the performance measurement and lifecycle gates in AGENTS.md.

Local logs and screenshots are under `build/font-size-review/` (not committed).
