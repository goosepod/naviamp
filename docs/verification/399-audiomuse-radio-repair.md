# Navidrome Radio/Autoplay backend repair — October 29, 2026

Tracking: #399, found during physical-phone acceptance #397. Release-note category: operational repair; no Naviamp product change.

## Cause and repair

The signed Naviamp candidate's Sonic Radio and queue-end Autoplay timed out on both 5G/Tailscale and direct Wi-Fi. Its diagnostic advertised Sonic support but timed out on both raw similarity endpoints. Ordinary authentication, browsing and audio playback worked.

Navidrome's own logs confirm that AudioMuse plugin **11.0.0** was calling its manifest's obsolete private-IP default. The plugin's stored configuration was empty, so that default remained effective. The running AudioMuse AI **3.7.0** Flask service is reachable on the same Docker network through its service name. From Navidrome's container, the obsolete address times out; `http://audiomuse-ai-flask:8000/` returns HTTP 200 in about 0.06 seconds. Replaying the same similarity request against that service returns **50 results in about 0.29 seconds**.

The repair sets the existing plugin's explicit `apiUrl` override to `http://audiomuse-ai-flask:8000`. This uses the persistent service alias rather than a container/private-host IP. The exact old configuration and timestamp are saved in an owner-only backup next to the Navidrome data, and a guarded transaction changes only that plugin row's configuration/timestamp. Only Navidrome is restarted to reload the setting. Its API returns HTTP 200 and the persisted override is re-read successfully. No media, account, token, library, plugin binary, Compose stack, or other plugin setting is changed.

The app's ten-second budget, explicit Sonic/Instant Mix choice, no-fallback policy, and error handling are unchanged. Raising a client deadline would hide the unreachable backend rather than repair it.

## Prevention and recovery

For this stack, Navidrome and the AudioMuse Flask container must share the Docker network and the plugin's **AudioMuse-AI API URL** must use the reachable service alias and internal port 8000. The host-published port differs and is not needed between these containers. Confirm connectivity from Navidrome's network before changing the URL. The supported admin UI exposes this override in the installed AudioMuse plugin's settings; the plugin package's default is not deployment configuration.

Keep any existing API token, server selector and other options intact. No token is required or changed for this deployment. Do not edit the plugin package or silently switch Sonic to another recommendation method. Preserve the original setting before repair and reload only the affected service/plugin. The protected backup allows restoring the exact prior configuration if necessary; it is deliberately not committed.

## Validation

The actual signed build-60 phone app, with unchanged production code, is reconnected to the live Navidrome source after its previous offline tests. A one-song catalog queue reaches its natural end after a native seek; Autoplay obtains a new track, starts it with advancing progress, and displays additional tracks in Up Next. Explicit **Start track radio** also generates a populated Sonic queue while playback advances. This separates live-provider capability from the earlier offline-download context, which correctly cannot generate server Radio until reconnected.

The built-in diagnostic returns **20 Sonic matches and 20 regular-similarity matches** on direct Wi-Fi, using the same account/seed. The regular result is checked after scrolling the diagnostic, not inferred from the first visible Sonic result.

The phone then reports NR/NR_ADVANCED (5G), with an active Tailscale VPN whose underlying transport is cellular. Sonic queue-end Autoplay again generates and plays a different track with advancing progress. Temporarily selecting Instant Mix also produces successful queue-end Autoplay and explicit Radio with a populated queue on that route. The original Sonic choice is restored and Wi-Fi is re-enabled afterward. This is an observed 5G run, not a separate LTE test.

Post-restart Navidrome logs show 44 similarity backend calls, all using the stable service alias, with **zero context-deadline/context-canceled failures** in that captured interval. The persistent plugin setting is independently re-read. The later signed candidate adds the separately tested shared TLS accessibility fix (#398); no phone/protocol workaround is needed for Radio.

The phone retains its original account and offline file. Test queue/history changes are part of acceptance and are not represented as unchanged user history. The cellular probe restores Wi-Fi and the original Sonic selection. Temporary probe-package removal and final paused-owner-track restoration are checked with the combined candidate (#401). The subsequent store-changelog correction (#401) changes release wording only.

Raw container logs, library labels, request URLs and native UI captures remain in ignored local acceptance evidence. No credentials or raw server logs are uploaded. There is no APK behavior patch for this incident and no platform production source change. The repair addresses this configured server; it does not prove another deployment's similarity backend is healthy.
