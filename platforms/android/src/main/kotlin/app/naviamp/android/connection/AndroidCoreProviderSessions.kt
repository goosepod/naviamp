package app.naviamp.android

import app.naviamp.app.NaviampClock
import app.naviamp.android.playback.AndroidPlaybackTls
import app.naviamp.provider.navidrome.NavidromeCoreProviderSessionPort
import app.naviamp.provider.navidrome.navidromeProviderSessionOpener

/** Selects Android's native playback TLS initialization for the provider-common session owner. */
fun androidCoreProviderSessionPort(
    storage: AndroidStorageDependencies,
    clock: NaviampClock,
): NavidromeCoreProviderSessionPort = NavidromeCoreProviderSessionPort(
    mediaSources = storage,
    initialSource = storage.latestNavidromeSource(),
    sessionOpener = navidromeProviderSessionOpener(
        cacheMaintenanceRepository = storage,
        providerMediaSourceRepository = storage,
        applyTlsDefaults = { connection ->
            AndroidPlaybackTls.applyDefaults(connection.tlsSettings)
        },
        nowEpochMillis = clock::nowEpochMillis,
    ),
    applyTlsDefaults = { connection ->
        AndroidPlaybackTls.applyDefaults(connection.tlsSettings)
    },
)
