# Radio methods and similarity diagnostics

Issue: https://github.com/goosepod/naviamp/issues/273

## Shared behavior

Settings → Experience → Radio is always visible. Sonic Similarity and Instant Mix are mutually
exclusive highlighted-row choices with descriptions. Sonic is disabled when the server does not advertise support.
A saved Sonic selection remains explicit on an unsupported server: requests report unsupported
rather than silently selecting Instant Mix. TV playback settings use the same common Radio controls.

Radio Autoplay uses the selected method, with its own independent batch size. Both sizes request
10–100 tracks in steps of ten; fewer usable tracks are valid. Sonic never calls Instant Mix as
fallback or top-up. Short and partial Sonic results remain usable; partial failures retain visible
diagnostics. Instant Mix uses the server’s similar-song recommendations, which may also be backed
by plugins. Selected Navidrome Instant Mix does not substitute local artist/album candidates for
empty endpoint results.

The existing serialized preference and Autoplay keys remain compatible with local storage,
export/import, and sync. Stored explicit choices survive. Unconfigured settings and older exports
with the original false default use Instant Mix; they no longer automatically opt into Sonic.
There is no database migration.

## Acceptance checks

1. Connect to a server without Sonic support, including the demo server. Radio stays visible.
   Sonic is disabled with guidance; Instant Mix and Radio Autoplay remain available.
2. On a supported server, select each method. Selection is exclusive. Start track Radio and finish
   a queue with Radio Autoplay enabled. Verify only the selected endpoint is requested.
3. Test short Sonic results with healthy Instant Mix. Keep only Sonic tracks. Test duplicate-heavy
   responses, queued tracks, and partial failures. No cross-method top-up is permitted.
4. Test empty, unsupported, failed, and timed-out Sonic. Radio does not expand with another method.
   Autoplay remains finished when no tracks are available and shows a notice. Refreshing Radio on
   the current track retains its session; starting another seed already selects that seed. Feedback
   must not claim that the previous queue was kept.
5. During either method’s Autoplay request, change the method, size, queue, source, or disable
   Autoplay. Late results must not append or restart playback.
6. Change batch sizes independently, restart, export/import and sync. Verify both methods and
   Autoplay survive, plus defaults for old documents and normalized imported sizes.
7. Open Test similarity. Both raw endpoints are tested without changing playback. Verify seed and
   match titles/artists, long-list scrolling, timeout, safe protocol codes, and no-match guidance.
   Source changes discard stale tests.
8. Check admin details with admin/non-admin accounts, missing native credentials, disabled plugins,
   successful empty inventory, and unavailable APIs. No credentials, paths, configuration values,
   or raw plugin error text may appear.
9. Check keyboard/selected-row accessibility, TV focus, narrow-window clipping and source changes.
   Disabled Sonic must not be activatable through pointer or keyboard.

## Automated checks

Common tests cover selected endpoints, filtering, ranking, short/partial results, safe errors,
deadlines, cancellation, and portable settings. Presentation tests cover method selection, feedback,
and stale-result rejection. Rendered UI tests cover always-visible Radio, unavailable Sonic,
exclusive choices, capability loss, independent sliders, and the diagnostic popup. Provider tests
verify request paths and that empty Instant Mix never triggers local candidate substitution.

## Optional read-only server probe

Set the disposable test server credentials in environment variables:
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

## Historical live evidence (before issue #273)

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

## Self-hosted AudioMuse connectivity

An advertised Sonic extension does not prove its backend is reachable. If both similarity methods
time out while browsing/playback work, inspect Navidrome's plugin log and test the configured
AudioMuse API URL from Navidrome's own container/network. An empty plugin configuration can leave
an obsolete address from the plugin manifest active. Use a stable reachable service name and the
correct internal port, preserve other settings and a rollback copy, then verify both diagnostics,
explicit Radio and queue-end Autoplay. Do not hide the problem by increasing the app timeout or
silently selecting another method. See the [physical-phone repair evidence](../verification/399-audiomuse-radio-repair.md).
