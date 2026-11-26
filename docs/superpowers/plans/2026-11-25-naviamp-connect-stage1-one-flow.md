# Naviamp Connect Stage 1: One Code-Entry Flow — Implementation Plan


**Goal:** Replace the multi-step Connect pairing with one flow: a device shows a 6-digit code while
its "Let another device connect" panel is open; another device types it, connects without any
approval prompt, and then chooses Sign in / Play on / Done in one sheet (issue #713).

**Architecture:** Keep the J-PAKE handshake, identity, trust, resumption and TCP transport
unchanged. Separate *presence* (listener + DNS-SD advertisement kept alive for trusted resume) from
the *code offer* (exists only while the code panel is visible). Remove all approval paths, turn the
controller's auto-provisioning into a post-connect sheet, add automatic discovery while the connect
panel is visible, and rebuild the shared Devices settings UI with translatable strings only. All
behavior lives in `core/domain`, `core/app`, `core/presentation`, `core/ui`; no host changes are
expected.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform (`runComposeUiTest` in `core/ui`
jvmTest), kotlinx.coroutines test, Bouncy Castle J-PAKE in jvm tests.

**Spec:** `docs/superpowers/specs/2026-11-25-naviamp-connect-simplification-design.md` (§1, §2, §4,
§8, §9, §11, §12 stage 1). Issue: https://github.com/goosepod/naviamp/issues/713

## Global Constraints

- AGENTS.md "Core Is the Product": no product behavior in `apps/` or `platforms/`. Expected
  platform production diff for this stage: **none**. Any platform file change needs a one-sentence
  native-boundary justification in the PR.
- Every new user-facing string goes in `core/ui/src/commonMain/composeResources/values/strings.xml`
  **and** all 17 locale catalogs (`values-ar … values-zh-rCN`); `values-es` and `values-pt-rBR`
  must stay complete (`NaviampTranslationResourcesTest`). Remove obsolete keys from every catalog.
- No hardcoded UI copy in Kotlin. `NaviampConnectSettingsUi` must not carry raw English status text.
- Protocol version stays **1** in this stage (no wire-incompatible change). Older peers: a v1
  target without the new TXT key is treated as pairable; a v1 controller may still resume to and
  pair with an updated target.
- Code lifetime 5 minutes (`pairingLifetimeMillis`), attempt limit 5 then 30 s lockout — unchanged.
- Control matrix: phones and desktops control (`NaviampCoreBidirectionalConnectCapabilities`);
  TV stays `NaviampCorePlaybackTargetConnectCapabilities`.
- Terminology in UI: "Devices", "This device", "Let another device connect", "Connect a device",
  "Your devices". Never "pair", "controller", "target", "provision" in new copy.
- Windows workspace: run Gradle as `.\gradlew.bat`; iOS compile is verified by CI (`ios` job), not
  locally.

## Review Focus

1. **Code typed before discovery has found the device** (slow mDNS): the sixth digit should wait up
   to 5 s for a device showing a code, then report "No device showing a code" — not fail instantly.
   Test in Task 4.
2. **A trusted device resumes while a code is on screen** (single accept loop): both the resume and
   a later code connection must work. Test in Task 2.
3. **Two devices enter the same displayed code at once:** exactly one connects; the other gets the
   existing authentication error, and the offer is consumed. Test in Task 3.
4. **An already-trusted phone re-enters a new code:** trust is replaced, not duplicated (one entry
   in Your devices). Test in Task 3.
5. **Local-network permission denied when the connect panel opens:** the recovery panel appears in
   the panel instead of an empty Nearby list. Test in Task 7.

---

### Task 1: Advertisement `pairable` flag in DNS-SD metadata

**Files:**
- Modify: `core/domain/src/commonMain/kotlin/app/naviamp/domain/connect/NaviampConnectProtocol.kt` (`NaviampConnectAdvertisement`, ~line 121)
- Modify: `core/domain/src/commonMain/kotlin/app/naviamp/domain/connect/NaviampConnectDiscoveryMetadata.kt`
- Test: `core/domain/src/commonTest/kotlin/app/naviamp/domain/connect/NaviampConnectDiscoveryMetadataTest.kt`

**Interfaces:**
- Produces: `NaviampConnectAdvertisement.pairable: Boolean = true` (serializable, default keeps old
  persisted trust endpoints decodable). TXT key `"code"`: `"1"` when pairable, `"0"` otherwise;
  absent → `true` (legacy v1 target).

- [ ] **Step 1: Write failing tests**

```kotlin
@Test fun pairableFlagRoundTripsThroughTxt() {
    val presence = sampleAdvertisement().copy(pairable = false)
    val txt = NaviampConnectDiscoveryMetadata.encode(presence)
    assertEquals("0", txt["code"])
    assertEquals(false, NaviampConnectDiscoveryMetadata.decode(txt, presence.port, presence.expiresAtEpochMillis)?.pairable)
    assertEquals("1", NaviampConnectDiscoveryMetadata.encode(presence.copy(pairable = true))["code"])
}
@Test fun legacyTxtWithoutCodeKeyIsPairable() {
    val txt = NaviampConnectDiscoveryMetadata.encode(sampleAdvertisement()) - "code"
    assertEquals(true, NaviampConnectDiscoveryMetadata.decode(txt, 1234, 10L)?.pairable)
}
```
(`sampleAdvertisement()` = the fixture already used in this test file; add one if absent.)

- [ ] **Step 2: Run, expect FAIL** — `.\gradlew.bat :core:domain:jvmTest --tests "app.naviamp.domain.connect.NaviampConnectDiscoveryMetadataTest"` → unresolved `pairable`.
- [ ] **Step 3: Implement** the field and the `code` TXT key in `encode`/`decode`.
- [ ] **Step 4: Run, expect PASS** (same command, plus `--tests "app.naviamp.domain.connect.*"`).
- [ ] **Step 5: Commit** — `git commit -m "Advertise whether a Connect device is showing a code (#713)"`

---

### Task 2: Presence listener without a code; code offer only while visible

**Files:**
- Modify: `core/presentation/src/commonMain/kotlin/app/naviamp/presentation/NaviampCoreConnectController.kt`
  (`startPairingMode` 562-649, `acceptPairingRequests` 651-740, `resumeTrustedController` 767-834,
  `finishPairing` 1280, `ensureTrustedTargetListener` 1583-1586, `stopPairingMode` 2057-2069,
  `resetPairingOfferKeepingListener` 2071-2086, `closeTargetResources` 2088-2104)
- Test: `core/presentation/src/commonTest/kotlin/app/naviamp/presentation/NaviampCoreConnectControllerTest.kt`
- Test: `core/presentation/src/commonTest/kotlin/app/naviamp/presentation/NaviampCoreConnectPeerTest.kt`

**Interfaces:**
- Consumes: `NaviampConnectAdvertisement.pairable` (Task 1).
- Produces (private to the controller, but later tasks rely on the behavior):
  - `presenceAdvertisement: NaviampConnectAdvertisement?` — registered with `pairable = false`
    whenever the device can be a target and has ≥1 trust record, or while the code is visible;
    renewed (new `expiresAtEpochMillis`) on expiry instead of tearing down the listener.
  - `codeVisible: Boolean` — true between `actions.onStartPairingMode()` and
    `actions.onStopPairingMode()`. While true, a `targetPairing` offer exists and the advertisement
    is re-registered with `pairable = true`; on offer expiry a **new code** is generated
    automatically. While false, `targetPairing` is `Disabled`, and no code is generated for
    trusted devices (delete the "background code" in `ensureTrustedTargetListener` and the
    `startPairingMode()` rotation at line 1280 — after a successful code connection the offer is
    consumed and `codeVisible` stays true but no new code is issued until the user asks again via
    `onStartPairingMode`).
  - `resumeTrustedController` validates against `presenceAdvertisement` (fingerprint + protocol
    range) instead of requiring `targetPairing` to be `Advertising`/`AwaitingApproval`.
  - `stopPairingMode()` hides the code but **keeps** the listener + presence when trust exists;
    it closes them only when there is no trust.
  - New UI field `NaviampConnectSettingsUi.codeVisible: Boolean` and
    `pairingCodeExpiresAtEpochMillis: Long?` (publish from the active offer).

- [ ] **Step 1: Replace/extend tests**
  - Rewrite `trustedTargetAdvertisesAutomatically` → `trustedTargetListensWithoutShowingACode`:
    after construction `assertNotNull(advertising.service)`, `assertEquals("0", advertising.service!!.textAttributes["code"])`,
    `assertNull(connect.pairingCode)`, `assertFalse(connect.codeVisible)`, listen count 1, listener
    still open after `advanceTimeBy(1_000L)` (lifetime 1 000 ms).
  - Rewrite `pairingLifetimeStopsAdvertisingAndListenerWithoutAnotherRequest` →
    `visibleCodeRegeneratesOnExpiry`: `newPairingCode` yields `"111111"` then `"222222"`;
    `onStartPairingMode()`, advance 1 000 ms → `assertEquals("222222", connect.pairingCode)`,
    `assertFalse(listener.closed)`; then `onStopPairingMode()` → `assertNull(connect.pairingCode)`,
    and with no trust `assertTrue(listener.closed)`.
  - Add `pairingRequestWithoutVisibleCodeIsClosed`: trusted target, code hidden; enqueue a plain
    `PairingHello` connection on a `QueuedListener` → connection closed, `connect.pairingPhase`
    not `AwaitingApproval`, listener still accepting (`acceptCount == 2`).
  - Add to `NaviampCoreConnectPeerTest`: `trustedResumeWorksWhileCodeHiddenAndWhileCodeVisible`
    (Review Focus 2): with `connectPeer(preTrusted = true)`, phone selects trusted mac → connected;
    stop controlling; mac `onStartPairingMode()`; phone selects trusted mac again → connected; mac
    `connect.pairingCode` still non-null.
- [ ] **Step 2: Run, expect FAIL** — `.\gradlew.bat :core:presentation:jvmTest --tests "app.naviamp.presentation.NaviampCoreConnectControllerTest" --tests "app.naviamp.presentation.NaviampCoreConnectPeerTest"`
- [ ] **Step 3: Implement** the presence/offer split as specified in Interfaces. Keep
  `preserveAdvertisement` semantics (no DNS goodbye during a live session) — re-register only to
  flip `pairable`.
- [ ] **Step 4: Run, expect PASS** — same command, then the whole module:
  `.\gradlew.bat :core:presentation:jvmTest`. Update other tests that relied on background codes
  (e.g. `pairingStopRenewalAndExpiryPreserveTheLiveSessionAndPlaybackUpdates`) to the new policy;
  do not weaken their session-preservation assertions.
- [ ] **Step 5: Commit** — `git commit -m "Keep trusted Connect presence without a background code (#713)"`

---

### Task 3: The displayed code is the consent — remove every approval

**Files:**
- Modify: `NaviampCoreConnectController.kt` (`acceptPairingRequests` AwaitingApproval branch
  706-721, `approveController` 836-863, `rejectController` 865-873, `startTargetSession`
  `offerProvisioning` 1347-1365 and `initialSetupAllowed` 1325, `approveProvisioning`/
  `rejectProvisioning` 1862-1933, `actions` 281-325)
- Delete: `core/app/src/commonMain/kotlin/app/naviamp/app/NaviampConnectInitialSetupAuthorization.kt` and its tests (grep `NaviampConnectInitialSetupAuthorization` under `core/app/src/commonTest`)
- Modify: `core/ui/src/commonMain/kotlin/app/naviamp/ui/NaviampConnectUi.kt` (remove fields/actions below)
- Test: `core/presentation/src/jvmTest/kotlin/app/naviamp/presentation/NaviampCoreConnectFreshSetupTest.kt`
- Test: `core/presentation/src/commonTest/kotlin/app/naviamp/presentation/NaviampCoreConnectSetupRecoveryTest.kt`

**Interfaces:**
- Produces: target auto-approves every pairing request that matches the visible offer (a request
  can only exist while a code is visible after Task 2). Target applies every
  `NaviampConnectOfferConnectionProvisioning` from an authenticated session without approval
  (both fresh code sessions and trusted resumes). `NaviampConnectWelcome.initialSetupAllowed` =
  `ConnectionProvisioning in supportedTargetCapabilities` (keeps v1 controllers auto-setting-up
  empty targets).
- Removed from `NaviampConnectSettingsActions`: `onApproveController`, `onRejectController`,
  `onApproveProvisioning`, `onRejectProvisioning`. Removed from `NaviampConnectSettingsUi`:
  `pendingControllerName`, `pendingProvisioningControllerName`, `pendingProvisioningConnectionName`.
  Removed enum value `NaviampConnectPairingUiPhase.AwaitingApproval`. Fix all call sites (UI files
  are rewritten in Tasks 7–8; delete their approval branches here so the build stays green).

- [ ] **Step 1: Tests**
  - In `NaviampCoreConnectFreshSetupTest` add `secondPhoneConnectsToAnAlreadyTrustedTargetWithoutApproval`
    using three peers built with `connectPeer(..., preTrusted = false, configure = <BouncyCastle as existing test>)`:
    phone A pairs with mac via code; mac `onStartPairingMode()` again; phone B enters the new code
    → `assertEquals("mac", phoneB.connectedTargetName)` with no mac action in between.
    (`ConnectTestNetwork.discovery()` must resolve all registered peers; extend it if it only keys
    one listener per id.)
  - Add `simultaneousCodeEntryConnectsExactlyOnePeer` (Review Focus 3): phones A and B submit the
    same code in the same `runCurrent()` → exactly one `connectedTargetName == "mac"`, the other's
    `statusMessage?.text` is the authentication-failed text from Task 6.
  - Add `reenteringACodeReplacesExistingTrust` (Review Focus 4): pair phone→mac twice via codes →
    `phone.connect.trustedDevices.size == 1`, `mac.connect.trustedDevices.size == 1`.
  - Rewrite `NaviampCoreConnectSetupRecoveryTest.rejectionDoesNotRequestCredentials…` as
    `configuredTargetAppliesTrustedSetupWithoutApproval`: same fixture minus the reject/approve
    calls; assert `destination.currentSourceId() == "fixture"` and `TvSetupCompletedSecurely`.
    Keep `validationFailureStillOffersCredentialRecovery…` minus `onApproveProvisioning()`.
- [ ] **Step 2: Run, expect FAIL** — `.\gradlew.bat :core:presentation:jvmTest --tests "*NaviampCoreConnectFreshSetupTest" --tests "*NaviampCoreConnectSetupRecoveryTest"`
- [ ] **Step 3: Implement** per Interfaces; delete `NaviampConnectInitialSetupAuthorization` and
  `codePairingOfferId` consent bookkeeping.
- [ ] **Step 4: Run, expect PASS** — `.\gradlew.bat :core:app:jvmTest :core:presentation:jvmTest :core:ui:jvmTest`
- [ ] **Step 5: Commit** — `git commit -m "Treat a displayed Connect code as consent and remove approvals (#713)"`

---

### Task 4: Connect panel — automatic discovery, single-device resolution, auto-submit

**Files:**
- Modify: `NaviampCoreConnectController.kt` (`refreshTargets` 930-946 → removed,
  `changePairingCode` 1159-1162, `submitPairingCode` 1164-1234, `selectTarget` 1110-1123, `publish`
  `discoveredTargets` 2217-2223)
- Modify: `core/ui/src/commonMain/kotlin/app/naviamp/ui/NaviampConnectUi.kt`
- Test: `NaviampCoreConnectControllerTest.kt`

**Interfaces:**
- Produces: action `onConnectScreenVisibilityChanged: (Boolean) -> Unit` (replaces
  `onRefreshTargets`). `true` starts discovery (if not running) and clears a previous
  selection/code; `false` stops discovery **unless** `pendingTrustedDeviceId != null` or a reconnect
  job is active, and resets `enteredCode`/`selectedTarget`.
- `NaviampConnectSettingsUi.discoveredTargets` lists only `advertisement.pairable` targets that are
  not this device. New field `connectScreenVisible: Boolean`.
- `changePairingCode(value)`: keep digits, max 6; on the 6th digit call `submitPairingCode()`.
- `submitPairingCode()` target resolution, in order: manual endpoint → explicitly selected target →
  the single pairable discovered target → if none yet, wait up to `CodeTargetWaitMillis = 5_000L`
  for one to appear (collect `discovery.state`), then `NaviampConnectStatusText.NoDeviceShowingACode`
  → if several and none selected, `NaviampConnectStatusText.ChooseTheDeviceShowingThisCode` and keep
  the digits. After a failed handshake clear `enteredCode`.

- [ ] **Step 1: Tests** (use `RecordingDiscoveryEffect` and a resolved-service helper like
  `discoveredTargetExpiresWithoutAnotherNativeCallback` does):
  - `openingConnectScreenStartsAndClosingStopsDiscovery`: `onConnectScreenVisibilityChanged(true)`
    → `startCount == 1`; `(false)` → discovery stopped.
  - `closingConnectScreenKeepsDiscoveryForAPendingTrustedReconnect`.
  - `sixthDigitSubmitsToTheOnlyDeviceShowingACode`: one pairable + one presence-only target
    discovered → `discoveredTargets.size == 1`; `onPairingCodeChanged("123456")` → transport
    `connect` attempted to the pairable target's address; phase `Handshaking`.
  - `codeTypedBeforeDiscoveryWaitsForTheDevice` (Review Focus 1): type 6 digits with nothing
    discovered; resolve a pairable target at +2 000 ms → connect attempted; with nothing by
    +5 000 ms → `statusMessage?.text == NoDeviceShowingACode`.
  - `multipleDevicesShowingCodesRequireAChoice`: two pairable targets → status
    `ChooseTheDeviceShowingThisCode`, no connect; `onTargetSelected(second)` → connect to second
    with the retained digits.
- [ ] **Step 2: Run, expect FAIL** — `.\gradlew.bat :core:presentation:jvmTest --tests "*NaviampCoreConnectControllerTest"`
- [ ] **Step 3: Implement** per Interfaces; add the two `NaviampConnectStatusText` entries (strings in Task 9).
- [ ] **Step 4: Run, expect PASS** — same command; update `NaviampCoreConnectFreshSetupTest` to use
  `onConnectScreenVisibilityChanged(true)` instead of `onRefreshTargets()`.
- [ ] **Step 5: Commit** — `git commit -m "Find nearby devices automatically and submit the sixth digit (#713)"`

---

### Task 5: Post-connect sheet replaces automatic and button-driven setup

**Files:**
- Modify: `NaviampCoreConnectController.kt` (`finishPairing` 1296-1299, `startControllerSession`
  1415-1419, `provisionTarget` 1748-1855, `publish`)
- Modify: `core/ui/src/commonMain/kotlin/app/naviamp/ui/NaviampConnectUi.kt`
- Test: `core/presentation/src/jvmTest/kotlin/app/naviamp/presentation/NaviampCoreConnectFreshSetupTest.kt`

**Interfaces:**
- Produces:
```kotlin
data class NaviampConnectPostConnectUi(
    val deviceName: String,
    val canSignIn: Boolean,          // peer has ConnectionProvisioning && this device has a current source
    val signInSelected: Boolean,     // default: canSignIn && !deviceHasAccounts
    val deviceHasAccounts: Boolean,  // Welcome snapshot.sourceIdentity != null
    val canPlayOn: Boolean,          // peer is a playback target
    val busy: Boolean,               // sign-in in progress
)
```
  `NaviampConnectSettingsUi.postConnect: NaviampConnectPostConnectUi?` — set only after a
  **code** connection on the controller side (never after a trusted resume); cleared on finish,
  on session end, or when sign-in completes/fails (failure keeps the existing credential prompt
  fields `needsProvisioningCredential` + `onSubmitProvisioningCredential`).
  Actions: `onPostConnectSignInChanged: (Boolean) -> Unit`, `onPostConnectPlayOnDevice: () -> Unit`,
  `onPostConnectDone: () -> Unit`.
  - Play on: if `signInSelected` run sign-in first; then keep the remote output selected and call
    `handoffLocalQueue()` when the local queue has a current item.
  - Done: if `signInSelected` run sign-in and then `stopControlling()` after its result; otherwise
    `stopControlling()` immediately. The device stays trusted; audio stays here.
  - Remove the automatic `provisionTarget()` after pairing and the `controllerInitialSetupAllowed`
    gate. `onProvisionTarget` remains for `NaviampConnectSourceMismatchDialog`.
  - Target side: on a code connection publish `statusMessage = ConnectedToDevice(controllerName)`.

- [ ] **Step 1: Tests** (Bouncy Castle peers as in the existing fresh-setup test):
  - Update `freshCodeSetupSurvivesSlowValidation…`: after the code, assert
    `phone.connect.postConnect?.signInSelected == true` and `destination.connects == 0`; call
    `onPostConnectDone()`; then existing slow-validation assertions; finally
    `phone.connect.connectedTargetName == null` and `phone.connect.trustedDevices.size == 1`.
  - `configuredDeviceDoesNotPreselectSignIn`: destination `configured = true` →
    `postConnect.deviceHasAccounts && !signInSelected`; `onPostConnectDone()` →
    `destination.connects == 0`, `source.exports == 0`.
  - `playOnDeviceSignsInAndKeepsTheRemoteOutput`: `onPostConnectPlayOnDevice()` →
    `destination.currentSourceId() == "fixture"`, `playbackDestinationStatus != Local`.
  - `trustedResumeShowsNoPostConnectSheet`.
- [ ] **Step 2: Run, expect FAIL** — `.\gradlew.bat :core:presentation:jvmTest --tests "*NaviampCoreConnectFreshSetupTest"`
- [ ] **Step 3: Implement** per Interfaces.
- [ ] **Step 4: Run, expect PASS** — `.\gradlew.bat :core:presentation:jvmTest`
- [ ] **Step 5: Commit** — `git commit -m "Choose sign-in or playback in one sheet after connecting (#713)"`

---

### Task 6: Typed status messages only — no raw English in Connect UI state

**Files:**
- Modify: `core/ui/src/commonMain/kotlin/app/naviamp/ui/NaviampConnectUi.kt` (remove
  `NaviampConnectSettingsUi.status`; `displayStatus()` uses only recovery / notice / `statusMessage`)
- Modify: `core/ui/src/commonMain/kotlin/app/naviamp/ui/NaviampConnectStatusText.kt`
- Modify: `NaviampCoreConnectController.kt` (every `status = "…"` without a `statusMessage`; the
  private `userMessage()` mappers 2373-2388; `remoteCommandFailureMessage` 2242-2251;
  `naviampCoreConnectQueueHandoffProblem` 2312-2323; `toUi()` detail 2368)
- Modify: `core/presentation/src/commonMain/kotlin/app/naviamp/presentation/NaviampCoreConnectProvisioning.kt:35,40`
- Test: existing tests that read `connect.status` (e.g. `incompletePairingClientTimesOut…`
  `status.contains("timed out")`) switch to `statusMessage?.text`.

**Interfaces:**
- Produces `NaviampConnectStatusText` entries (resource keys `connect_status_*`), at least:
  `ConnectedToDevice`, `ConnectedWithoutSecureReconnect`, `NoDeviceShowingACode`,
  `ChooseTheDeviceShowingThisCode`, `CouldNotCreateCode`, `IncompleteConnectionRequestClosed`,
  `IncompleteConnectionRequestTimedOut`, `ControllerDisconnected`, `DeviceDisconnected`,
  `AnotherDeviceTookControl`, `Reconnecting`, `PlaybackDeviceDisconnectedBeforeConfirming`,
  `QueueHandoffUnavailable`, `StartSomethingBeforeSendingQueue`, `ConnectSameSourceBeforeSendingQueue`,
  `ConnectBeforeSendingQueue`, `DeviceDidNotRespondToQueueTransfer`, `LocalNetworkPermissionRequired`,
  `DiscoveryFailed`, `DeviceIdentityChanged`, `IncompatibleVersion`, `CodeNotVerified`,
  `CodeExpired`, `TooManyAttempts`, `DeviceNoLongerAvailable`, `InvalidRequest`, `RequestFailed`.
  Keep the private diagnostic `status` string in the controller for `trace` only.
  Trusted-device `detail` becomes a typed value (`NaviampConnectTrustedDeviceUi.kind:
  NaviampConnectTrustedDeviceKind { Playback, Remote }`) rendered via resources.

- [ ] **Step 1: Test** — add `NaviampConnectStatusTextTest` in `core/ui/src/jvmTest`:
  `NaviampConnectStatusText.entries.forEach { assertTrue(englishKeys.contains(it.resource.key)) }`
  (read keys the same way `NaviampTranslationResourcesTest.resources("values")` does).
- [ ] **Step 2: Run, expect FAIL** — `.\gradlew.bat :core:ui:jvmTest --tests "*NaviampConnectStatusTextTest"` (and compile errors where `status` was read).
- [ ] **Step 3: Implement**; add English strings now (translations in Task 9).
- [ ] **Step 4: Run, expect PASS** — `.\gradlew.bat :core:ui:jvmTest :core:presentation:jvmTest`
- [ ] **Step 5: Commit** — `git commit -m "Localize every Connect status message (#713)"`

---

### Task 7: Shared Devices settings (phone and desktop)

**Files:**
- Modify: `core/ui/src/commonMain/kotlin/app/naviamp/ui/NaviampSettingsUi.kt` (enum line 246;
  section 545-713; lines 484, 522, 533, 2267)
- Modify: `core/ui/src/commonMain/kotlin/app/naviamp/ui/NaviampLanguage.kt` (add `Devices` to the
  `categories` map of `NaviampResourceLanguagePack`)
- Replace: `core/ui/src/commonMain/kotlin/app/naviamp/ui/NaviampConnectTargetSettings.kt` →
  `NaviampConnectDevicesPanels.kt` containing `NaviampConnectShowCodePanel` and
  `NaviampConnectEnterCodePanel`
- Create: `core/ui/src/commonMain/kotlin/app/naviamp/ui/NaviampConnectPostConnectDialog.kt`
- Modify: `core/ui/src/commonMain/kotlin/app/naviamp/ui/NaviampSharedUi.kt:448` (mount the dialog next to the mismatch dialog)
- Test: replace `core/ui/src/jvmTest/kotlin/app/naviamp/ui/NaviampConnectTargetSettingsTest.kt` with
  `NaviampConnectDevicesSettingsTest.kt`; create `NaviampConnectPostConnectDialogTest.kt`

**Interfaces:**
- Consumes: UI fields/actions from Tasks 2–6.
- Produces:
  - `NaviampSettingsCategory.Devices` (renamed from `Controllers`; resources `settings_devices_title`
    "Devices", `settings_devices_subtitle` "Connect and control your devices").
  - `@Composable internal fun NaviampConnectShowCodePanel(connect, colors, actions, modifier)`:
    `DisposableEffect(Unit) { actions.onStartPairingMode(); onDispose { actions.onStopPairingMode() } }`;
    shows device name, code via `formatNaviampConnectPairingCode` (tag `connect-code`), countdown from
    `pairingCodeExpiresAtEpochMillis` updated once per second only while composed, and a
    "Can't connect?" disclosure with `listeningPort` and address. Shows "Connected to %1$s" when
    `connectedControllerName != null` with a "Connect another device" button →
    `onStartPairingMode()`.
  - `@Composable internal fun NaviampConnectEnterCodePanel(connect, colors, actions, modifier)`:
    `DisposableEffect` → `onConnectScreenVisibilityChanged(true/false)`; one numeric code field
    (tag `connect-code-entry`, digits only, max 6, no submit button); Nearby rows from
    `discoveredTargets` (tag `connect-nearby-<instanceId>`, click → `onTargetSelected`);
    recovery panel when `connect.recovery != null`; "Can't find it?" disclosure → existing manual
    address field + `onManualEndpointSelected`.
  - Devices section order: This device (name field + save), "Let another device connect" (expands
    `NaviampConnectShowCodePanel`; only when `canAdvertise`), "Connect a device" (expands
    `NaviampConnectEnterCodePanel`; only when `canDiscover`), "Stop controlling %1$s" row when
    `connectedTargetName != null`, Your devices (per row: name, kind, rename field, Forget, and a
    "Connect at address" disclosure using `onManualTrustedEndpointSelected`). Removed: "Find Naviamp
    devices", "Pair with …", "Selected: …", "Pair securely", "Set up … with this connection",
    "Reconnect to …". Only one panel expanded at a time.
  - `@Composable internal fun NaviampConnectPostConnectDialog(postConnect, colors, actions)` using
    `NaviampAlertDialog`: title "%1$s is connected", sign-in checkbox (tag
    `connect-post-sign-in`, shown when `canSignIn`), "Play on %1$s" (when `canPlayOn`), "Done";
    buttons disabled while `busy`; credential prompt inline when `needsProvisioningCredential`.

- [ ] **Step 1: Tests** (`runComposeUiTest`, `NaviampColors.Dark`, recording `actions(calls)` like the old test):
  - `showCodePanelStartsOnEnterAndStopsOnLeave`: compose panel → `calls == ["start"]`; remove it
    from composition → `["start","stop"]`; with `pairingCode = "123456"` shows "123 456".
  - `enterCodePanelReportsVisibilityAndListsOnlyNearbyDevices`; `typingDigitsDelegatesToCore`
    (`performTextInput("123456")` → `onPairingCodeChanged` received "123456").
  - `permissionProblemShowsRecoveryInsteadOfEmptyNearby` (Review Focus 5): `recovery =
    NaviampConnectRecoveryUi(LocalNetworkPermission, canOpenSettings = true)` → recovery panel node
    exists; no "No devices" placeholder.
  - `devicesSectionHasNoLegacyPairingControls`: none of the removed labels exist.
  - Post-connect: `freshDevicePreselectsSignIn`, `doneAndPlayDelegate`, `busyDisablesButtons`.
- [ ] **Step 2: Run, expect FAIL** — `.\gradlew.bat :core:ui:jvmTest --tests "*NaviampConnectDevicesSettingsTest" --tests "*NaviampConnectPostConnectDialogTest"`
- [ ] **Step 3: Implement**; all copy via `stringResource` (English keys now, translations in Task 9).
- [ ] **Step 4: Run, expect PASS** — `.\gradlew.bat :core:ui:jvmTest`
- [ ] **Step 5: Commit** — `git commit -m "Rebuild Connect settings as Devices with one code flow (#713)"`

---

### Task 8: Television — show the code immediately, no approvals

**Files:**
- Modify: `core/ui/src/commonMain/kotlin/app/naviamp/ui/NaviampTelevisionSettings.kt` (category
  `Controllers` → `Devices`, lines 106, 415, 482-591, 1922)
- Modify: `core/ui/src/commonMain/kotlin/app/naviamp/ui/NaviampTelevisionAppShell.kt`
  (`TelevisionFirstRunConnectSetup` 909-976; setup description string usage near 837-880)
- Test: `core/ui/src/commonTest/kotlin/app/naviamp/ui/NaviampTelevisionSettingsPolicyTest.kt`;
  create `core/ui/src/jvmTest/kotlin/app/naviamp/ui/NaviampTelevisionConnectSetupTest.kt`

**Interfaces:**
- Consumes: `NaviampConnectShowCodePanel` behavior contract (Task 7) — TV uses its own TV-styled
  composable but the same `DisposableEffect` start/stop rule.
- Produces:
  - First run: code + device name shown as soon as the setup screen composes (no "Show pairing
    code" button); title "Set up from your phone or computer"; body "On your other device, open
    Naviamp → Settings → Devices → Connect a device, then enter this code."; leaving the screen
    stops the offer. Remove Approve/Reject rows.
  - TV Devices settings: one row "Let another device connect" that, when selected, shows the code
    in the row value and starts the offer; the offer stops when the settings page is left (row's
    `DisposableEffect` scoped to the page). Remove approve-controller, reject-controller,
    approve/reject provisioning rows. Trusted list unchanged (rename/forget come in #716).
  - `tv_direct_setup_description` new copy: "Or connect to your server directly from this TV."

- [ ] **Step 1: Tests**
  - `firstRunShowsCodeWithoutAButtonAndStopsOnLeave`: compose with `pairingCode = "123456"` →
    "123 456" displayed, no "Show pairing code" node, `calls == ["start"]`; dispose → `"stop"`.
  - `televisionDevicesHasNoApprovalRows`.
  - Policy test: `TelevisionSettingsCategory.Devices` replaces `Controllers`.
- [ ] **Step 2: Run, expect FAIL** — `.\gradlew.bat :core:ui:jvmTest --tests "*NaviampTelevisionConnectSetupTest" --tests "*NaviampTelevisionSettingsPolicyTest"`
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run, expect PASS** — `.\gradlew.bat :core:ui:jvmTest`
- [ ] **Step 5: Commit** — `git commit -m "Show the Connect code immediately on TV without approvals (#713)"`

---

### Task 9: Translations and obsolete strings

**Files:**
- Modify: `core/ui/src/commonMain/composeResources/values*/strings.xml` (all 18 catalogs)

- [ ] **Step 1:** List every key added in Tasks 4–8 (`git diff main -- core/ui/src/commonMain/composeResources/values/strings.xml`) and every key no longer referenced (`connect_*`, `tv_*` approval/pairing keys: e.g. `tv_approve`, `tv_reject_request`, `tv_pair`, `tv_find_naviamp_targets`, `tv_show_pairing_code`, `tv_stop_pairing`, `tv_pair_a_controller`, `tv_only_approve_if_you_started_pairing_on_this_device`, `tv_controller_asking_to_pair`, `tv_reject_server_setup`, `tv_set_up_server`, `connect_status_approve_*`). Confirm each removal with a repo-wide grep for `Res.string.<key>`.
- [ ] **Step 2:** Translate every new key into all 17 locales (natural, concise UI copy; keep `%1$s` arguments) and delete removed keys from every catalog.
- [ ] **Step 3: Verify** — `.\gradlew.bat :core:ui:jvmTest --tests "*NaviampTranslationResourcesTest" --tests "*NaviampLanguageCatalogTest"` → PASS; `python scripts/validate-translations.py core/ui/src/commonMain/composeResources/values-de/strings.xml` (repeat per locale) → no errors.
- [ ] **Step 4: Commit** — `git commit -m "Translate the simplified Connect flow (#713)"`

---

### Task 10: Docs, full verification, real-device acceptance

**Files:**
- Modify: `docs/naviamp-connect-protocol.md` (TXT `code` key; presence vs code offer; no approval;
  provisioning accepted from authenticated sessions; protocol version unchanged)
- Modify: `docs/android-tv-plan.md` (lines ~292, 296, 305, 308: replace outdated statements)
- Modify: `docs/superpowers/specs/2026-11-25-naviamp-connect-simplification-design.md` §11/§12:
  stage 1 keeps protocol version 1; the bump happens with the first wire-incompatible stage.
- Create: `docs/connect-one-flow-acceptance-<date>.md` (results of the checklist below)

- [ ] **Step 1: Full JVM/Android/Desktop build** — `.\gradlew.bat -Pkotlin.native.enableKlibsCrossCompilation=false verifyCoreFirstArchitecture verifySharedAndroidSourceGraph jvmTest :apps:android:testDebugUnitTest :apps:android:lintRelease desktopTest` → BUILD SUCCESSFUL.
- [ ] **Step 2: Platform diff check** — `git diff --stat main -- apps platforms` → empty (or each file justified in the PR body).
- [ ] **Step 3: Real-device acceptance** (record in the acceptance doc): fresh Android TV + phone
  (type code → Done → TV on Home, no TV input); Desktop → TV by code; phone → phone; already-trusted
  TV + second phone with no approval; trusted resume with code hidden and visible; wrong code ×5
  lockout message; code regenerates on screen after 5 min; v1 (previous release) phone resumes to
  the updated TV.
- [ ] **Step 4: Commit** — `git commit -m "Document the one-flow Connect behavior and acceptance (#713)"`
- [ ] **Step 5:** Squash local WIP if needed and open the PR linked to #713 (owner approval before push).
