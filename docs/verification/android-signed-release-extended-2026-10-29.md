# Signed Android upgrade and extended physical-phone checks — October 29, 2026

Tracking: #397. This follows the isolated [physical acceptance report](android-2.10.0-2026-10-29.md), which remains the record of the earlier 59 active checks and Android Auto runs. The owner subsequently authorized an actual signed in-place upgrade and extended live/offline/background tests. These later tests intentionally exercise the normal application.

## Actual signed artifact and upgrade

Physical Pixel 10a, Android 17/API 37. Production application code matches `c58f0b3aeb5dad7c17ace37b8c444f2a418f737f`; the acceptance branch adds only native test tooling and documentation. `:apps:android:stageReleaseArtifacts` succeeds. The real release is R8-minified/resource-shrunk and non-debuggable, package `app.naviamp.android`, versionCode 60/versionName v2.10.0.

APK SHA-256: `d82c40254c37eb1f8ff1e472fc0b79c21b3be56af06e7bb7b5b34448cb769f1b`.

Actual APK signer SHA-256: `84ce75e58fe0a75a045bbf830f16c547b92368c83fd118ca87b874d029623c43`, identical to the original installed APK. Android accepts `install -r --enable-rollback 0` over build 59/versionName v2.10.1. Android compares version codes: this is build 59 → 60 despite the lower display version string. No uninstall, data clear, debug re-signing or credential extraction is used.

Immediately after installation, the OS rollback service reports an **available** `app.naviamp.android 60 -> 59 [0]` rollback, where policy 0 restores application data. This verifies the rollback was enabled; accepting an install flag alone would not. The rollback is not exercised or presented as a portable complete backup. Android's own [rollback implementation](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/rollback/README.md) describes its APK/user-data snapshots and availability limits.

Before/after UI captures verify the existing saved Navidrome account and selected source, retained paused track and one existing approximately 5 MB OPUS offline file. Visible playback and download settings match exactly. The retained credentials successfully reconnect and real playback progress advances. This is a visible-state comparison, not an exhaustive private-database or full queue-order audit. Later playback/source tests deliberately change the active queue; do not interpret the initial preservation check as a promise that all test playback history is unchanged.

## Live providers and network scope

The owner supplies an Aurral test account and explicitly authorizes disabling TLS verification for that test profile. The owner also requires Tailscale for cellular server access. Initial state: Wi-Fi on, mobile data on, airplane mode off, Tailscale disconnected. The existing Tailscale account/configuration is enabled, without changing its exit-node, DNS, membership or login settings. The radio reports NR/NR_ADVANCED (5G); a separate LTE run is not established.

- Navidrome reconnects with retained credentials over 5G/Tailscale; actual playback progress advances.
- Temporary Aurral profile connects over 5G/Tailscale with verification skipped only for that profile. Recommendations and trending artists load; artist samples/releases load.
- One discovery sample plays. Native AudioFlinger records the release's own track advancing by 235,200 frames at 48 kHz during its approximately five-second run, then stopping when the modal closes. A Pause icon alone is not used as playback proof.
- One bounded single-track request returns **Request queued**. A later artist-specific library search does not find the requested artist; fulfillment of that server download is not established. Same-title tracks by a different artist are explicitly excluded as proof. An already-owned library track plays as OPUS/128 kbps with advancing progress.
- A real transient Android audio-focus request from the separate foreground test UID pauses the release's MediaSession; abandoning focus resumes moving progress. This is an OS focus interruption, not a real carrier call.
- Aurral does not advertise Sonic Similarity; the app reports that explicitly. Instant Mix on the single-track test artist reports no additional tracks and preserves the current playback. Neither result is counted as successful radio generation.
- The original Sonic Similarity preference is restored on Navidrome. A Downloads-launched queue end reports unavailable Sonic support; a separately selected live catalog song instead reaches **Radio Autoplay: Request timed out** over 5G/Tailscale. On direct Wi-Fi with Tailscale disconnected, Start track radio also times out. The built-in same-seed diagnostic reports advertised Sonic support but times out on both Sonic and regular similarity endpoints. This unresolved integration failure is tracked in #399; successful radio generation/Autoplay queue extension is **not** accepted. Plugin-admin details require a password login unavailable to this test; the saved credential is not extracted or replaced.

## Background, offline, cleanup and remaining scope

The locked/background capture spans **610.84 seconds** with 21 native samples. All 21 show an active release-owned AudioFlinger track. Its server frame counter increases at every sample, totaling **29,319,360 frames**, averaging 47,998.6 frames/second for the 48 kHz output. The track is temporarily set to repeat. This proves continuing native audio delivery across repeats; it is not a subjective listening check or a new-stream fetch test at every sample.

At 300.2 seconds Wi-Fi is re-enabled; at 315.1 seconds the OS reports validated Wi-Fi. Native audio continues across the transition. The initial cellular capture explicitly contains an active `VPN:com.tailscale.ipn` network. After the Wi-Fi return, the VPN is absent. Tailscale's own log records an orderly `StopVPNWorker`/WantRunning=false transition at that time; its process remains alive, and no Tailscale configuration is edited. The cause of that stop is not inferred from the network snapshot alone.

At 480.3 seconds Android enters forced deep idle, with `mState=IDLE`. Samples 16–19 confirm deep idle while audio frames continue. At 610.3 seconds forced idle is exited and at 610.4 seconds simulated battery state is reset. The display reports Dozing in 19 of 21 samples and briefly Awake at the battery-state changes; do not describe this as an uninterrupted display-off interval. This is a bounded forced-Doze functional check while physically USB-connected, not natural overnight/unplugged battery endurance or CPU/GPU budget acceptance.

After owner unlock, moving progress is verified and Repeat off is restored. The final probe uses Android's MediaSessionManager (scoped shell permission, filtered to the release package) to assert the real session is PAUSED during both transient and permanent focus requests. Transient focus abandonment resumes progress automatically; permanent abandonment leaves the app paused until an explicit Play, which then advances. Both assertions pass.

A new single Aurral test download completes as a 13 MB MP3/320 kbps. Wi-Fi and active-subscription cellular data are disabled, then the OS is allowed to settle to **no default network** before force-stop/cold launch. Tailscale may retain a virtual VPN agent with no underlying networks; that is not a usable connection. The new MP3 plays after process recreation, seeks to the middle, pauses, seeks while paused and resumes with moving progress. The owner's original 5 MB OPUS/128 kbps file separately passes no-default-network cold launch, playback and midpoint seek after the upgrade. The active subscription uses `mobile_data3`; the legacy aggregate `mobile_data` value alone does not prove data is enabled/disabled on this phone.

Only the newly downloaded Aurral file is removed, through its scoped row menu. The Aurral profile's deletion confirmation explicitly names the test profile, and final connection UI shows only the original current Navidrome account. Its original OPUS file still appears with the same format, quality and approximate size. Navidrome reconnects with retained credentials. One post-offline Home wait exceeds 30 seconds; a later capture shows the original recent-radio Home content without editing the account. This delayed recovery is retained as an observed result, not classified as a navigation-driver failure.

Final OS checks match the initial network settings: Wi-Fi on, mobile data and active subscription data on, airplane mode off. Wi-Fi is the default network; no active Tailscale VPN remains. Forced idle is false and battery simulation is reset. The normal app retains its original UID/appId 10470. Its actual installed APK SHA-256 matches the verified signed artifact, and the available build-60 → 59 rollback is still present.

The original source and track are left paused at their initial displayed 2:23 position, with Repeat off. The current queue/history was exercised during acceptance and is not claimed to be identical to the original full queue. Both disposable `.v2test`/`.v2test.test` packages are removed, leaving only the signed normal Naviamp installation. The temporary Aurral profile and its phone download are removed; the single server-side request was accepted as queued, but its eventual state/fulfillment is unverified and it is not silently canceled. No global TLS trust policy, Android Auto security preference, pairing, Bluetooth configuration or server configuration is changed.

**Disposition:** signed in-place upgrade/visible preservation, retained authentication, live browsing/sample/request acceptance, real compressed/lossless playback, new and original cold-start offline files, locked/forced-idle audio, network restoration, and transient/permanent audio focus pass within the stated scope. Radio generation and Autoplay queue extension remain unresolved (#399). The shared TLS checkbox accessibility label remains open (#398). These findings prevent describing the whole release acceptance as green.

No Bluetooth audio device is available. Real Bluetooth route changes, real carrier-call interruptions, separate LTE operation, store-delivered installation and the owner's truck connection remain outside the completed scope. The owner will check the truck later (#395). No release/tag/merge/store publication occurs.

## Native probe and reproduction

`AndroidReleaseAcceptanceInstrumentedTest` is explicitly gated by `releaseAcceptance=true`; ordinary test runs skip it. It uses public Android Activity/accessibility/audio-focus/screen-lock APIs to operate the separately installed real signed app, without calling its R8-obfuscated internals or reading its private storage. Captures must belong to the selected application. Its only external UI target is the existing Tailscale app. Test-side native APIs justify the Android source placement; no Android/Desktop/iOS production file is changed.

Build the isolated debug host and instrumentation with `:apps:android:assembleDebug :apps:android:assembleDebugAndroidTest`, verify their `.v2test` package IDs, then install only those test packages. Place authorized steps in a private JSON file, for example `[{"op":"launch"},{"op":"expect","label":"Home"},{"op":"snapshot","name":"home"}]`. Run:

```sh
python3 scripts/android-release-acceptance-probe.py \
  --serial DEVICE_SERIAL --steps-file /private/tmp/authorized-steps.json \
  --evidence-dir build/private-phone-evidence --name home-check
```

The runner does not install, update, clear or uninstall the normal app. Its output directory and input scripts can contain private account/library labels; keep them out of GitHub. Private local evidence for this run lives in `build/phone-release-acceptance-2026-10-29` in the acceptance worktree. Setup probes that assumed the wrong navigation state, used a TV repeat label, or redundantly selected Play after seeking an ended track are retained as test-driver failures; they are not counted as product regressions or accepted scenarios. Final observations must come from the corrected probes and actual native/window evidence.

The TLS checkbox's visible label is an unassociated accessibility sibling in the shared connection UI; this is tracked separately in #398. The native probe selects its adjacent checkbox without changing TLS policy globally.
