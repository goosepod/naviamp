package app.naviamp.android

import app.naviamp.android.playback.AndroidPlaybackTls
import app.naviamp.app.NaviampClock
import app.naviamp.domain.provider.ProviderIdJellyfin
import app.naviamp.presentation.NaviampCoreProviderSessionPort
import app.naviamp.presentation.NaviampCoreProviderSessionRoute
import app.naviamp.provider.jellyfin.JellyfinCoreProviderSessionPort
import app.naviamp.provider.jellyfin.JellyfinSessionService
import app.naviamp.provider.jellyfin.JellyfinSessionServiceFactory
import app.naviamp.provider.jellyfin.KtorJellyfinHttpClient
import app.naviamp.provider.jellyfin.jellyfinClientIdentity
import app.naviamp.provider.jellyfin.jellyfinProviderSessionOpener
import app.naviamp.provider.navidrome.NavidromeCoreProviderSessionPort
import app.naviamp.provider.navidrome.createDefaultNavidromeKtorClient
import app.naviamp.provider.navidrome.navidromeProviderSessionOpener
import app.naviamp.provider.navidrome.subsonicFamilyProviderSessionRouter

/** Selects Android's native playback TLS initialization for the provider-common session owner. */
fun androidCoreProviderSessionPort(
    storage: AndroidStorageDependencies,
    clock: NaviampClock,
): NaviampCoreProviderSessionPort {
    val jellyfinServices = androidJellyfinSessionServices()
    return subsonicFamilyProviderSessionRouter(
        NavidromeCoreProviderSessionPort(
            mediaSources = storage,
            initialSource = storage.latestMediaSource(),
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
        ),
        additionalRoutes = listOf(
            NaviampCoreProviderSessionRoute(
                providerIds = setOf(ProviderIdJellyfin),
                sessionPort = JellyfinCoreProviderSessionPort(
                    mediaSources = storage,
                    initialSource = storage.latestMediaSource(),
                    sessionOpener = jellyfinProviderSessionOpener(
                        sessionServices = jellyfinServices,
                        cacheMaintenanceRepository = storage,
                        providerMediaSourceRepository = storage,
                        nowEpochMillis = clock::nowEpochMillis,
                    ),
                    sessionServices = jellyfinServices,
                    deviceId = "naviamp-android",
                ),
            ),
        ),
    )
}

private fun androidJellyfinSessionServices(): JellyfinSessionServiceFactory =
    JellyfinSessionServiceFactory { tlsSettings ->
        JellyfinSessionService(
            httpClient = KtorJellyfinHttpClient(createDefaultNavidromeKtorClient(tlsSettings)),
            identity = jellyfinClientIdentity(
                deviceId = "naviamp-android",
                deviceName = "Android",
            ),
        )
    }
