package app.naviamp.desktop

import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Track
import app.naviamp.domain.cache.CacheMaintenanceRepository
import app.naviamp.domain.cache.ProviderMediaSourceConnection
import app.naviamp.domain.cache.ProviderMediaSourceRepository
import app.naviamp.domain.source.ConnectionHeaderDefinition
import app.naviamp.domain.source.ConnectionSecondaryUrl
import app.naviamp.domain.settings.PlaybackSessionSettings
import app.naviamp.domain.settings.RestoredPlaybackSession
import app.naviamp.domain.settings.restoredTrackSession
import app.naviamp.domain.source.ProviderConnectionLifecycleRequest
import app.naviamp.domain.source.ProviderConnectionSession
import app.naviamp.domain.source.openProviderConnectionSession
import app.naviamp.provider.navidrome.NavidromeConnection
import app.naviamp.provider.navidrome.NavidromeConnectionLoginRequest
import app.naviamp.provider.navidrome.NavidromeProvider
import app.naviamp.provider.navidrome.NavidromeTls
import app.naviamp.provider.navidrome.NavidromeTlsSettings
import app.naviamp.provider.navidrome.prepareNavidromeConnection
import app.naviamp.provider.navidrome.resolvedDisplayName

typealias DesktopConnectionSession = ProviderConnectionSession<NavidromeConnection, NavidromeProvider>

sealed interface DesktopRestoredPlaybackSession {
    data class InternetRadio(
        val station: InternetRadioStation,
        val track: Track,
    ) : DesktopRestoredPlaybackSession

    data class TrackQueue(
        val session: RestoredPlaybackSession,
        val coverArtUrl: String?,
    ) : DesktopRestoredPlaybackSession
}

suspend fun openDesktopConnectionSession(
    serverUrl: String,
    username: String,
    password: String,
    displayName: String,
    tlsSettings: NavidromeTlsSettings,
    secondaryUrls: List<ConnectionSecondaryUrl>,
    customHeaders: List<ConnectionHeaderDefinition>,
    savedConnectionForLogin: NavidromeConnection?,
    cacheMaintenanceRepository: CacheMaintenanceRepository<*>,
    providerMediaSourceRepository: ProviderMediaSourceRepository,
    clearProviderData: Boolean,
): DesktopConnectionSession {
    return openProviderConnectionSession(
        request = ProviderConnectionLifecycleRequest(
            connection = NavidromeConnectionLoginRequest(
                baseUrl = serverUrl,
                secondaryUrls = secondaryUrls,
                username = username,
                password = password,
                displayName = displayName,
                tlsSettings = tlsSettings,
                customHeaders = customHeaders,
                savedConnectionForLogin = savedConnectionForLogin,
                nativeAuthRequired = true,
            ),
            prepareConnection = { request -> prepareNavidromeConnection(request) },
            preparedConnection = { prepared -> prepared.connection },
            provider = { connection -> NavidromeProvider(connection) },
            mediaSourceConnection = { connection -> connection.toProviderMediaSourceConnection() },
            applyTlsDefaults = { connection -> NavidromeTls.applyJvmDefaults(connection.tlsSettings) },
            smartPlaylistAuthWarning = { prepared -> prepared.nativeAuthErrorMessage },
            clearProviderData = clearProviderData,
        ),
        cacheMaintenanceRepository = cacheMaintenanceRepository,
        providerMediaSourceRepository = providerMediaSourceRepository,
    )
}

private fun NavidromeConnection.toProviderMediaSourceConnection(): ProviderMediaSourceConnection =
    ProviderMediaSourceConnection(
        displayName = resolvedDisplayName(),
        baseUrl = baseUrl,
        username = username,
        token = token,
        salt = salt,
        nativeToken = nativeToken,
        tlsSettings = tlsSettings,
        secondaryUrls = secondaryUrls,
        customHeaders = customHeaders,
    )

fun restoredDesktopPlaybackSession(
    savedPlaybackSession: PlaybackSessionSettings?,
    provider: NavidromeProvider,
): DesktopRestoredPlaybackSession? {
    val session = savedPlaybackSession ?: return null
    val internetRadioStation = session.internetRadioStation?.toStation()
    val restoredTrackSession = session.restoredTrackSession()
    val currentTrack = restoredTrackSession?.currentTrack ?: session.currentTrack()
    if (internetRadioStation != null && currentTrack != null) {
        return DesktopRestoredPlaybackSession.InternetRadio(
            station = internetRadioStation,
            track = currentTrack,
        )
    }
    return restoredTrackSession?.let {
        DesktopRestoredPlaybackSession.TrackQueue(
            session = it,
            coverArtUrl = it.currentTrack.coverArtId?.let(provider::coverArtUrl),
        )
    }
}
