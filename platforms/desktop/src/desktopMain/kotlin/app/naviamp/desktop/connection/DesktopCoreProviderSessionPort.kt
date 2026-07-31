package app.naviamp.desktop

import app.naviamp.domain.cache.CacheMaintenanceRepository
import app.naviamp.domain.cache.ProviderMediaSourceRepository
import app.naviamp.storage.StorageMediaSourceStore
import app.naviamp.provider.navidrome.NavidromeConnection
import app.naviamp.provider.navidrome.NavidromeCoreProviderSessionPort
import app.naviamp.provider.navidrome.NavidromeProviderConnectionSession
import app.naviamp.provider.navidrome.NavidromeProviderSessionOpener
import app.naviamp.provider.navidrome.NavidromeTls
import app.naviamp.provider.navidrome.navidromeProviderSessionOpener

typealias DesktopNavidromeSession = NavidromeProviderConnectionSession
typealias DesktopNavidromeSessionOpener = NavidromeProviderSessionOpener
typealias DesktopCoreProviderSessionPort = NavidromeCoreProviderSessionPort

/** Selects Desktop's JVM TLS initialization for the provider-common Core session owner. */
fun desktopCoreProviderSessionPort(
    storage: StorageMediaSourceStore,
    cacheMaintenanceRepository: CacheMaintenanceRepository<*>,
    nowEpochMillis: () -> Long = DesktopSystemClock::nowEpochMillis,
): DesktopCoreProviderSessionPort = NavidromeCoreProviderSessionPort(
    mediaSources = storage,
    initialSource = storage.latestMediaSource(),
    sessionOpener = desktopNavidromeSessionOpener(
        cacheMaintenanceRepository = cacheMaintenanceRepository,
        providerMediaSourceRepository = storage,
        nowEpochMillis = nowEpochMillis,
    ),
    applyTlsDefaults = ::applyDesktopNavidromeTlsDefaults,
)

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
