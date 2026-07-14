# Temporary Branch Notes: Android Auto Crossfade Follow-up

> [!WARNING]
> This file is a working checklist for `fix/android-auto-crossfade`.
> Resolve or transfer every remaining item, then delete this file before merging the branch into `main`.

## Completed

- [x] Fix Android Auto playback transitions and service-owned prepared-next playback.
- [x] Cancel stale Android Auto browse and search selections when a newer selection or transport command takes precedence.
- [x] Add unit coverage for selection cancellation and prepared-next planning.
- [x] Verify domain tests, Android unit tests, and Android debug compilation.

## Outstanding issues

### Android Auto voice-search manifest entry

Status: Not started. Priority: High.

Current behavior:

- `:apps:android:lintDebug` fails with `MissingIntentFilterForMediaSearch`.
- The manifest does not register `android.media.action.MEDIA_PLAY_FROM_SEARCH`, even though the media-session callback handles `onPlayFromSearch`.
- The full lint result currently contains 1 error, 36 warnings, and 9 hints.

Desired behavior:

- Register the Android media-search action on the appropriate exported Android Auto component.
- Confirm voice search reaches Naviamp through Android Auto and Android Automotive OS.
- Keep the existing trusted media-browser caller validation intact.

Acceptance criteria:

- [ ] `android.media.action.MEDIA_PLAY_FROM_SEARCH` is declared on the correct activity or service.
- [ ] An Android Auto voice-search request reaches the existing search command path.
- [ ] `:apps:android:lintDebug` and the repository `check` task pass.

### Protect persisted credentials and backup data

Status: Not started. Priority: High.

Current behavior:

- Android stores the Navidrome password and client-certificate password in ordinary private `SharedPreferences`.
- The shared storage database persists native tokens, client-certificate passwords, and custom header values as plain text.
- Android backup is enabled, but the manifest does not declare backup exclusion rules for credential-bearing preferences or database fields.

Desired behavior:

- Protect credential material with an Android Keystore-backed design.
- Exclude credentials and authentication tokens from cloud backup and device-transfer backup unless a reviewed encrypted migration explicitly supports them.
- Preserve non-secret settings backup and settings-sync behavior.
- Migrate existing installations without silently losing usable connections.

Acceptance criteria:

- [ ] Passwords, native tokens, certificate passwords, and secret custom-header values are not stored as recoverable plain text.
- [ ] Android backup rules explicitly exclude credential-bearing data.
- [ ] Existing saved connections migrate safely or clearly require reauthentication.
- [ ] Settings export and sync continue to omit credentials.
- [ ] Tests cover storage, migration, backup-rule presence, and reconnect behavior.

References:

- <https://developer.android.com/privacy-and-security/keystore>
- <https://developer.android.com/privacy-and-security/risks/backup-best-practices>
- <https://developer.android.com/identity/data/autobackup>

### Invalidate prepared-next engine state after queue edits

Status: Not started. Priority: Medium.

Current behavior:

- Queue mutations clear the domain controller's `preparedNextIndex`.
- `QueueAwarePlaybackEngine` can prepare a next request but cannot cancel or replace already prepared native engine state explicitly.
- Removing, moving, replacing, or clearing the upcoming track after BASS preparation may leave the old prepared source attached until the next playback transition.

Desired behavior:

- Add an explicit prepared-next invalidation operation at the playback-engine boundary.
- Invoke it whenever a queue mutation changes the identity or order of the next track.
- Keep append-only radio refills from discarding a still-valid prepared next track.

Acceptance criteria:

- [ ] `QueueAwarePlaybackEngine` exposes a clear prepared-next operation implemented by Android and desktop BASS engines.
- [ ] Removing, moving, replacing, or clearing the next track frees stale native prepared state.
- [ ] Queue changes made while asynchronous preparation is running cannot attach a stale track afterward.
- [ ] Automated tests cover queue mutation before preparation, during preparation, and after preparation.
- [ ] Device testing confirms the removed or reordered track does not crossfade unexpectedly.

### Harden the exported media-browser service command surface

Status: Not started. Priority: Medium.

Current behavior:

- `AndroidPlaybackForegroundService` is exported for Android Auto browsing.
- The same exported service accepts explicit `START`, `STOP`, `PLAY_PAUSE`, `PREVIOUS`, `NEXT`, `FAVORITE`, and related action intents in `onStartCommand`.
- Trusted-caller validation protects `onGetRoot`, but it does not authenticate explicit service-start intents.
- Android lint reports `ExportedService` as a warning.

Desired behavior:

- Keep the Android Auto media-browser endpoint available to trusted hosts.
- Prevent unrelated installed applications from invoking Naviamp playback and metadata actions directly.
- Prefer separating the exported browser facade from an internal playback-command service, or use another reviewed capability-based design.

Acceptance criteria:

- [ ] External untrusted applications cannot send Naviamp playback commands through explicit service intents.
- [ ] Android Auto and Android Automotive OS can still browse, search, and control playback.
- [ ] Notification `PendingIntent` controls continue to work.
- [ ] Tests cover trusted browsing and rejection of unauthorized command entry points.

## Lower-priority cleanup found by lint

- [ ] Review the no-timeout playback wake lock and document why its foreground-service lifecycle is sufficient, or add a safe renewal strategy.
- [ ] Replace obsolete SDK-version checks now that the minimum Android version is API 26.
- [ ] Review the custom trust manager used only for the explicit skip-TLS-verification option and suppress the warning narrowly if the behavior remains intentional.
- [ ] Fix or intentionally suppress the exported-service warning after command-surface hardening.
- [ ] Clean up the remaining Compose modifier, primitive state, icon, resource, KTX, and dependency-catalog findings.

## Before merging

- [ ] Fix the Android Auto media-search lint error on this branch.
- [ ] Resolve the security and prepared-next items, or transfer them into the repository's durable issue tracker.
- [ ] Run the full `check` task and Android lint successfully.
- [ ] Delete this temporary branch note.
