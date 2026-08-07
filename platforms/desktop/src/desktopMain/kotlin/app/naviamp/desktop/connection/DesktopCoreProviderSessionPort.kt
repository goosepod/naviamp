package app.naviamp.desktop

import app.naviamp.domain.cache.CacheMaintenanceRepository
import app.naviamp.domain.cache.ProviderMediaSourceRepository
import app.naviamp.domain.provider.ProviderIdJellyfin
import app.naviamp.presentation.NaviampCoreProviderSessionPort
import app.naviamp.presentation.NaviampCoreProviderSessionRoute
import app.naviamp.provider.jellyfin.JellyfinCoreProviderSessionPort
import app.naviamp.provider.jellyfin.JellyfinSessionService
import app.naviamp.provider.jellyfin.JellyfinSessionServiceFactory
import app.naviamp.provider.jellyfin.KtorJellyfinHttpClient
import app.naviamp.provider.jellyfin.jellyfinClientIdentity
import app.naviamp.provider.jellyfin.jellyfinProviderSessionOpener
import app.naviamp.provider.navidrome.NavidromeConnection
import app.naviamp.provider.navidrome.NavidromeCoreProviderSessionPort
import app.naviamp.provider.navidrome.NavidromeProviderConnectionSession
import app.naviamp.provider.navidrome.NavidromeProviderSessionOpener
import app.naviamp.provider.navidrome.NavidromeTls
import app.naviamp.provider.navidrome.createDefaultNavidromeKtorClient
import app.naviamp.provider.navidrome.navidromeProviderSessionOpener
import app.naviamp.provider.navidrome.subsonicFamilyProviderSessionRouter
import app.naviamp.storage.StorageMediaSourceStore

typealias DesktopNavidromeSession = NavidromeProviderConnectionSession
typealias DesktopNavidromeSessionOpener = NavidromeProviderSessionOpener
typealias DesktopCoreProviderSessionPort = NavidromeCoreProviderSessionPort

/** Selects Desktop's JVM TLS initialization for the provider-common Core session owner. */
fun desktopCoreProviderSessionPort(
    storage: StorageMediaSourceStore,
    cacheMaintenanceRepository: CacheMaintenanceRepository<*>,
    nowEpochMillis: () -> Long = DesktopSystemClock::nowEpochMillis,
): NaviampCoreProviderSessionPort {
    val jellyfinServices = desktopJellyfinSessionServices()
    return subsonicFamilyProviderSessionRouter(
        NavidromeCoreProviderSessionPort(
            mediaSources = storage,
            initialSource = storage.latestMediaSource(),
            sessionOpener = desktopNavidromeSessionOpener(
                cacheMaintenanceRepository = cacheMaintenanceRepository,
                providerMediaSourceRepository = storage,
                nowEpochMillis = nowEpochMillis,
            ),
            applyTlsDefaults = ::applyDesktopNavidromeTlsDefaults,
        ),
        additionalRoutes = listOf(
            NaviampCoreProviderSessionRoute(
                providerIds = setOf(ProviderIdJellyfin),
                sessionPort = JellyfinCoreProviderSessionPort(
                    mediaSources = storage,
                    initialSource = storage.latestMediaSource(),
                    sessionOpener = jellyfinProviderSessionOpener(
                        sessionServices = jellyfinServices,
                        cacheMaintenanceRepository = cacheMaintenanceRepository,
                        providerMediaSourceRepository = storage,
                        nowEpochMillis = nowEpochMillis,
                    ),
                    sessionServices = jellyfinServices,
                    deviceId = "naviamp-desktop",
                ),
            ),
        ),
    )
}

private fun desktopJellyfinSessionServices(): JellyfinSessionServiceFactory =
    JellyfinSessionServiceFactory { tlsSettings ->
        JellyfinSessionService(
            httpClient = KtorJellyfinHttpClient(createDefaultNavidromeKtorClient(tlsSettings)),
            identity = jellyfinClientIdentity(
                deviceId = "naviamp-desktop",
                deviceName = System.getProperty("os.name").ifBlank { "Desktop" },
            ),
        )
    }

fun desktopNavidromeSessionOpener(
    cacheMaintenanceRepository: CacheMaintenanceRepository<*>,
    providerMediaSourceRepository: ProviderMediaSourceRepository,
    nowEpochMillis: () -> Long = DesktopSystemClock::nowEpochMillis,
): DesktopNavidromeSessionOpener = navidromeProviderSessionOpener(
    cacheMaintenanceRepository = cacheMaintenanceRepository,
    providerMediaSourceRepository = providerMediaSourceRepository,
    applyTlsDefaults = ::applyDesktopNavidromeTlsDefaults,
    nowEpochMillis = nowEpochMillis,
)

private fun applyDesktopNavidromeTlsDefaults(connection: NavidromeConnection) {
    NavidromeTls.applyJvmDefaults(connection.tlsSettings)
}
