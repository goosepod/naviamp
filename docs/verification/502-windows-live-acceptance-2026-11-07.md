# Aurral 2.13 Windows live acceptance — November 7

Issue: #502. Baseline: accepted main `10e6f4e7e`. This follow-up changes only the
provider's JVM live-test connection setup and this report; application behavior is unchanged.

## Configured CA preserved

The existing Windows test connection uses a custom CA with certificate verification enabled.
The native preview probe already accepts `NAVIAMP_AURRAL_TEST_CA`, but the provider live-test
helper ignored it. The helper now passes that path to the existing shared TLS policy. The
native/Subsonic connection-parity case reuses that helper instead of duplicating its settings.
No OS trust store, account configuration, stored credential, or production TLS behavior changes.

The configured account is `test`. Its protected connection was read without modifying the
running review profile. Credentials remain private; no address or credential is included here.
Mutation opt-ins and administrator credentials are absent from the test processes. The earlier
one-play approval remains consumed and was not replayed.

## Fourteen live read-only cases pass

The explicitly selected fourteen `AurralLiveTest` methods execute in 21.521 seconds with zero
failures, errors, or skips. The feedback mutation case is excluded. These are configured live
results, not unconfigured early returns:

- Bootstrap reports Aurral **2.13.0**, API **1**, and eleven capabilities.
- Catalog/recommendation, artist/edition/release metadata, ownership and native/Subsonic identity
  reads succeed. Two fresh user sessions validate existing recording ownership without submission.
- Initial and nonzero audio ranges each return **HTTP 206 / 1,024 bytes**.
- Artwork, native sample bytes, lyrics and playlist reads succeed. The current account has no
  playlists; the lyrics probe returns 46 lines.
- Original-file metadata is readable with audio caching disabled: embedded title/date and FLAC
  44.1 kHz / 16-bit properties are present.
- Both Instant Mix and fallback Radio return **20 tracks / 20 artists** with Sonic unavailable.
  Another artist's Library identity and stream validate; the reported seed is retained.
- Charts contain 100 songs, 25 albums, 25 artists, nine genres and 100 previews. The permission
  probe confirms Flow is absent and chart saving is Forbidden before any write.

The first launch failed during Gradle task selection because PowerShell split a test-filter
argument. It executed no cases. Correcting the launcher produced the fourteen passing cases
above; no application change or relaxed assertion was involved.

## Native preview remains blocked by the Windows output environment

The single selected `DesktopAurralWindowsPreviewTest` case fails in 5.493 seconds at
`setAudioOutputDevice(null)`: **BASS_SetDevice failed: driver error**. It does not reach playback.
BASS reports only an uninitialized Default alias. Windows audio service is running, but present
AudioEndpoint inventory exposes only an AMD microphone and no playback endpoint.

This is not passing native-clock or audible acceptance. No no-sound device is substituted, and
no playback deadline or assertion is changed. A real playback output is required before rerunning
the existing muted clock/pause/resume/cleanup case. With the configured test URL, user, password
and CA supplied privately, select only that case:

```text
./gradlew :platforms:desktop:desktopTest --rerun --tests app.naviamp.desktop.playback.bass.DesktopAurralWindowsPreviewTest.liveChartPreviewReachesNativePlayingThroughAuthenticatedByteTransport
```

## Remaining gates

- Connect a Windows playback endpoint, then complete the native preview clock/pause/resume and
  callback-cleanup assertions. Muted native results still do not establish audible quality.
- Positive chart save requires suitable Flow access and an approved small chart. Positive
  playlist/cover editing requires an approved disposable target. No acquisition, feedback,
  playlist edit, account change, administrator fallback or listen was sent by this batch.
- The broader application restart, physical input and spoken accessibility gates in the #506
  acceptance record remain separate from API/transport evidence.
- Required CI must pass on the final test-harness PR. Keep #502 open while these gates remain.

Private evidence: `build/aurral-502-windows/` and the acceptance checkout's provider/Desktop XML
reports. No local profile data, build artifact, endpoint or credential is committed.
