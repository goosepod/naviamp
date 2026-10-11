# Sonic Radio and similarity diagnostics

Issue: https://github.com/goosepod/naviamp/issues/250

## Manual checks

1. Connect to a Navidrome server and play a library track. Open Settings → Experience →
   Related Tracks → Test similarity. Confirm the displayed test track is the playing track,
   the popup shows both endpoint outcomes separately, with artist and title for the seed and
   returned matches. Scroll through a longer match list, close the popup, and confirm playback
   and the queue remain unchanged.
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
8. With a Navidrome native admin session, confirm Server plugin details shows global plugin
   enablement, external-service enablement, configured metadata agents, and the installed plugin
   names, IDs, versions, and enabled states. A server-reported plugin error must show only generic
   guidance. No configuration, credentials, local paths, or raw plugin error text should appear.
9. Disable the plugin system on a disposable server. The popup must say it is disabled and cannot
   list installed plugins, rather than claim an empty inventory. A successful empty inventory on
   an enabled server must remain distinct. An unavailable API must not imply missing plugins.
10. Try a non-admin account or a source without native login credentials. Both similarity endpoints
    must still be tested. Only the extra plugin details should report the access/login requirement.
11. Enable Sonic Autoplay and let a queue finish with the Sonic backend failing, timing out, or
    returning only queued tracks. Regular Radio should supply unique unqueued continuation tracks
    and playback should resume. Usable Sonic matches must take priority without a regular request.
    A saved enabled setting must also fall back when Sonic support is no longer advertised.
12. During the Autoplay fallback, replace the queue, switch sources, or disable Sonic Autoplay.
    Late results must not append tracks or restart playback. If both paths have no usable results,
    playback remains finished and no queue modification occurs.

## Automated checks

Track Radio and Sonic Autoplay share `SimilarityService` in domain common code. Requests contain
seed IDs, excluded track IDs, per-seed counts, and the Sonic preference. Results retain scored
matches in ordered seed slots, selected endpoint, and typed fallback/failure diagnostics. Radio
applies tuning after the request; Autoplay applies recent-seed ranking, deduplication, and append
limits. A fatal regular error can be explicitly propagated by Radio without exposing its exception
in diagnostic metadata; Autoplay keeps its finished queue when no continuation is available.
Both the Sonic and regular batches have one ten-second deadline. Raw endpoint diagnostics bypass
this playback service so they can still show the actual results of each endpoint.

The common RadioService tests cover success, empty/seed-only results, exceptions, deadlines,
transport timeouts, cancellation, and independent diagnostic endpoints. Provider tests cover raw
response parsing, capability refresh, safe protocol codes, and avoiding local Radio fallback in
endpoint diagnostics. Controller tests cover overlapping requests, cancellation, source changes,
and queue preservation. Compose tests cover endpoint labels/results, narrow and wide layouts,
and access when Sonic is not advertised. Admin tests cover native JWT refresh, permission/session
failures, disabled versus empty inventory, safe metadata mapping, independent failure and deadlines,
and cancellation. Presentation integration tests verify expanded Radio history persistence/replay
and reject a seed-only result as a successful saved session.
Sonic Autoplay tests cover regular fallback for missing support, errors, deadlines, empty/queued-only
matches, deduplication, cancellation, and stale queue/source/setting results. The Sonic batch and
regular batch each have a ten-second deadline; usable Sonic matches collected before a later seed
times out are retained, and regular Radio is requested only when no usable Sonic tracks remain.

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

To opt into the separate read-only admin integration check, also set
`NAVIAMP_SIMILARITY_TEST_ADMIN=true` and `NAVIAMP_SIMILARITY_TEST_PLUGINS` to the expected global
plugin enablement (`true` or `false`). Run the `inspectNativeAdminApiWithoutChangingServer`
method in `NavidromeSimilarityLiveTest`. The enabled fixture expects AudioMuse v10 to be installed.
Native authentication is obtained using the supplied password; the test never changes the server.

## Recorded live evidence

On the authorized `navidrome-test` container, Navidrome 0.64.2-SNAPSHOT (PR 6227), the plugin-disabled
baseline reported missing support, Sonic HTTP 404, and 20 regular endpoint matches. Radio added
50 tracks. With the official AudioMuse v10 plugin enabled and its backend configured to an
unreachable loopback address, support was advertised, Sonic returned API error 0, regular similarity
returned 20 matches, and Radio added 50 tracks with a recorded failure fallback.

Only `navidrome-test` was recreated. The temporary plugin was disabled and removed afterward;
the restored container environment and image were checked against the original baseline.
The native admin check also returned `pluginsEnabled=false` with unknown inventory at baseline,
then `pluginsEnabled=true` with the installed `audiomuseai` plugin and version `10.0.0` when enabled.
Both similarity endpoints remained available to the diagnostic test. After removing the fixture,
the complete environment mapping matched the original Compose service plus image defaults,
the image matched the baseline, and the installed plugin inventory was empty.
Successful Sonic playback remains a manual verification case; common/provider tests cover its
selection and response parsing. Native iOS runtime verification requires a macOS host.
