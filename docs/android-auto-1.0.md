# Android Auto 1.0 Checklist

Branch: `codex/android-auto-1-0`

Status: planning

## Goal

Finish Android Auto as the last 1.0 release gate. Naviamp should be discoverable and usable from Android Auto without depending on the phone UI staying alive, and the release should be validated in Desktop Head Unit plus at least one real vehicle when available.

## Current State

- [x] Android Auto descriptor exists at `apps/android/src/main/res/xml/automotive_app_desc.xml`.
- [x] `AndroidPlaybackForegroundService` is declared as a `MediaBrowserService`.
- [x] Auto browse is split into `AndroidAutoBrowseController`.
- [x] Auto media-id/search/custom-action dispatch is split into `AndroidAutoCommandController`.
- [x] Service-owned playback runtime exists in `AndroidServicePlaybackRuntimeController`.
- [x] Media-session hydration/restoration exists in `AndroidPlaybackServiceSessionController`.
- [x] Phone-app handoff exists through `AndroidAutoAppController` and `MainActivity` intent extras.
- [x] Auto can browse library, downloads, playlists, internet radio, and Library Radio.
- [x] Auto can play browsed tracks, downloads, radio, playlists, and saved sessions after cold service start.
- [x] Auto progress, duration, seek, and metadata updates are wired.
- [x] Disconnect/noisy-audio pause handling exists for the standard Android route-disconnect signal.

## 1.0 Release Criteria

- [ ] Android Auto discovers Naviamp in DHU from a freshly installed debug build.
- [ ] Android Auto discovers Naviamp in a real vehicle from a freshly installed build.
- [ ] Browse root is fast, stable, and never empty when a saved connection exists.
- [ ] Browse root degrades gracefully when no connection is configured or provider restore fails.
- [ ] Now Playing / Resume item resumes the last saved queue or radio session from a swiped-away app.
- [ ] Browsed library track playback works from cold service start.
- [ ] Browsed downloaded-track playback works from cold service start without network.
- [ ] Browsed playlist playback works from cold service start.
- [ ] Browsed internet-radio playback works from cold service start.
- [ ] Library Radio works from cold service start.
- [ ] Assistant voice requests reach Naviamp and either play a matching result or fail with a clear log/status.
- [ ] Seek, play/pause, previous, next, and stop commands work from DHU and real vehicle controls.
- [ ] Projection disconnect pauses playback reliably enough that audio does not continue unexpectedly on phone speakers.
- [ ] Foreground notification and Android Auto session stay in sync across phone UI open/close, service-only playback, and app process cold start.
- [ ] `.\gradlew.bat --configure-on-demand :apps:android:assembleDebug` passes.
- [ ] `.\gradlew.bat --configure-on-demand :apps:android:assembleRelease` passes.
- [ ] Debug build installs and runs on the test phone.

## Implementation Checklist

### Phase 1: Baseline Audit

- [ ] Re-run a clean Android debug build and install to the test phone.
- [ ] Capture DHU startup logs filtered to `NaviampAutoCommand`.
- [ ] Capture current browse tree IDs from root through tracks, downloads, playlists, radio, and Library Radio.
- [ ] Document any current DHU failures in this file before changing behavior.
- [ ] Confirm whether real-car testing is available now or must remain a release-candidate manual gate.

### Phase 2: Service Ownership

- [ ] Audit where Auto playback still depends on retained phone-app callbacks.
- [ ] Move any remaining Auto-only playback path that can run service-side into `AndroidPlaybackForegroundService` or service-owned controllers.
- [ ] Ensure phone UI opening is an optional presentation side effect, not required for playback.
- [ ] Verify service-owned playback can restore provider, storage, playback session, queue, and radio state without a composed `NaviampAndroidApp`.
- [ ] Add focused tests or debug-only assertions for media-id classification where practical.

### Phase 3: Browse and Media IDs

- [ ] Review `AndroidAutoBrowseController` for stable IDs, labels, playable/browsable flags, and browse limits.
- [ ] Make empty/error states explicit for disconnected, restoring, no library index, no downloads, no playlists, and no stations.
- [ ] Ensure media IDs are documented and round-trip through `AndroidAutoCommandController`.
- [ ] Verify playlists and stations do not require phone UI state to resolve after cold start.
- [ ] Keep browse result construction on shared storage/provider ports where possible.

### Phase 4: Voice Search

- [ ] Define supported Assistant phrases for 1.0.
- [ ] Verify DHU voice/search dispatch logs `Auto requested search=...`.
- [ ] Map voice search queries to artist radio, library radio, station, playlist, or track playback with a deterministic priority.
- [ ] Add clear logging for no-match, provider-unavailable, and connection-restore failures.
- [ ] Test sample phrases:
  - [ ] `Hey Google, play Green Day radio in Naviamp`
  - [ ] `Hey Google, play Camel Fat radio in Naviamp`
  - [ ] `Hey Google, play Electronica radio in Naviamp`
  - [ ] `Hey Google, play downloaded music in Naviamp`

### Phase 5: Media Session and Controls

- [ ] Verify metadata does not flicker on progress-only ticks.
- [ ] Verify album art is shown when available and placeholder behavior is acceptable when unavailable.
- [ ] Verify duration and position are correct for original streams, transcoded streams, downloads, and radio.
- [ ] Verify seek ignores the known bogus zero-position command after playback has advanced.
- [ ] Verify previous/next command behavior for queue, playlist, radio, and single-track playback.
- [ ] Verify stop/pause behavior when the service owns playback and the phone UI is closed.

### Phase 6: Disconnect and Lifecycle

- [ ] Test DHU disconnect while playing queue audio.
- [ ] Test DHU disconnect while playing internet radio.
- [ ] Test USB/Bluetooth route disconnect on the real phone.
- [ ] Add fallback handling if the standard noisy-audio broadcast is insufficient.
- [ ] Verify swiping away the phone UI does not release service-owned playback unexpectedly.
- [ ] Verify app process kill and relaunch preserves enough session state for Auto resume.

### Phase 7: Build and Device Validation

- [ ] `.\gradlew.bat --configure-on-demand :apps:android:assembleDebug`
- [ ] `.\gradlew.bat --configure-on-demand :apps:android:installDebug`
- [ ] `.\gradlew.bat --configure-on-demand :apps:android:assembleRelease`
- [ ] Install on test phone and connect to Navidrome.
- [ ] Start Android Auto DHU and verify the full checklist above.
- [ ] Test in a real vehicle.
- [ ] Record tested device, Android version, vehicle/head-unit model, and result.

## Desktop Head Unit Flow

- [ ] Install or update the Android debug build on the test phone.
- [ ] Start the Android Auto head unit server on the phone from Android Auto developer settings.
- [ ] Run:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" forward tcp:5277 tcp:5277
desktop-head-unit.exe
```

- [ ] Capture logs:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" logcat -s NaviampAutoCommand
```

## Progress Log

- Created this Android Auto 1.0 checklist on branch `codex/android-auto-1-0`.
