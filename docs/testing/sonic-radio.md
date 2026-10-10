# Sonic Radio and similarity diagnostics

Issue: https://github.com/goosepod/naviamp/issues/250

## Manual checks

1. Connect to a Navidrome server and play a library track. Open Settings → Experience →
   Related Tracks → Test similarity. Confirm the displayed test track is the playing track,
   both endpoint outcomes are reported separately, and playback and the queue remain unchanged.
2. With a working Sonic backend and Sonic Similarity enabled, start Radio. Confirm additional
   tracks arrive without a fallback notice. Disable Sonic Similarity and repeat: regular Radio
   should still work and the preference should remain disabled.
3. On a disposable server, enable a Sonic plugin but configure its backend to an unreachable
   address. Test similarity should show advertised support, a Sonic failure, and the actual
   regular endpoint result. Start Radio with Sonic enabled: it should recover through the
   existing provider fallback and show a dismissible fallback notice.
4. Test a slow Sonic request and a response containing only the seed. Radio should recover
   after the ten-second Sonic deadline or the unusable response. Cancelling playback or
   changing the source must not apply an old queue/result to the new source.
5. Without a Sonic plugin, Related Tracks must still expose Test similarity on Navidrome.
   Missing support must be distinct from an advertised but failing backend. A regular endpoint
   with no matches must be shown as empty even if provider Radio can find local album/artist
   candidates.
6. When neither Radio path produces additional tracks, confirm the explicit no-additional-tracks
   notice appears and the current queue is kept. Close the notice and confirm it disappears.
7. Switch servers while a test is running, including switching back to the original server.
   Old results must be discarded and a new test must be available immediately.

## Automated checks

The common RadioService tests cover success, empty/seed-only results, exceptions, deadlines,
transport timeouts, cancellation, and independent diagnostic endpoints. Provider tests cover raw
response parsing, capability refresh, safe protocol codes, and avoiding local Radio fallback in
endpoint diagnostics. Controller tests cover overlapping requests, cancellation, source changes,
and queue preservation. Compose tests cover endpoint labels/results, narrow and wide layouts,
and access when Sonic is not advertised.

An opt-in JVM integration test uses an explicitly configured disposable server. Set
`NAVIAMP_SIMILARITY_TEST_URL`, `NAVIAMP_SIMILARITY_TEST_USER`,
`NAVIAMP_SIMILARITY_TEST_PASSWORD`, and `NAVIAMP_SIMILARITY_TEST_SUPPORT`
(`Missing` or `Advertised`), then run:

```shell
./gradlew :providers:navidrome:cleanJvmTest :providers:navidrome:jvmTest --tests '*NavidromeSimilarityLiveTest'
```

This integration test expects an unavailable Sonic backend and a library where regular Radio
can return candidates. It does not change the server. Restore any test plugin or configuration
changes separately. Without the URL environment variable, the live test does not contact a server.

## Recorded live evidence

On the authorized `navidrome-test` container, Navidrome 0.64.2-SNAPSHOT (PR 6227), the plugin-disabled
baseline reported missing support, Sonic HTTP 404, and 20 regular endpoint matches. Radio added
50 tracks. With the official AudioMuse v10 plugin enabled and its backend configured to an
unreachable loopback address, support was advertised, Sonic returned API error 0, regular similarity
returned 20 matches, and Radio added 50 tracks with a recorded failure fallback.

Only `navidrome-test` was recreated. The temporary plugin was disabled and removed afterward;
the restored container environment and image were checked against the original baseline.
Successful Sonic playback remains a manual verification case; common/provider tests cover its
selection and response parsing. Native iOS runtime verification requires a macOS host.
