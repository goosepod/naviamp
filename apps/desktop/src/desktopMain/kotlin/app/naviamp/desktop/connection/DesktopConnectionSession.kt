package app.naviamp.desktop

import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Track
import app.naviamp.domain.cache.CacheMaintenanceRepository
import app.naviamp.domain.cache.ProviderMediaSourceConnection
import app.naviamp.domain.cache.ProviderMediaSourceRepository
import app.naviamp.domain.provider.ConnectionValidation
import app.naviamp.domain.settings.PlaybackSessionSettings
import app.naviamp.domain.settings.RestoredPlaybackSession
import app.naviamp.domain.settings.restoredTrackSession
import app.naviamp.provider.navidrome.NavidromeConnection
import app.naviamp.provider.navidrome.NavidromeConnectionLoginRequest
import app.naviamp.provider.navidrome.NavidromeProvider
import app.naviamp.provider.navidrome.NavidromeTls
import app.naviamp.provider.navidrome.NavidromeTlsSettings
import app.naviamp.provider.navidrome.prepareNavidromeConnection
import app.naviamp.provider.navidrome.resolvedDisplayName

data class DesktopConnectionSession(
    val connection: NavidromeConnection,
    val provider: NavidromeProvider,
    val sourceId: String,
    val validation: ConnectionValidation,
    val smartPlaylistAuthWarning: String?,
)

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
    savedConnectionForLogin: NavidromeConnection?,
    cacheMaintenanceRepository: CacheMaintenanceRepository<*>,
    providerMediaSourceRepository: ProviderMediaSourceRepository,
    clearProviderData: Boolean,
): DesktopConnectionSession {
    val preparedConnection = prepareNavidromeConnection(
        NavidromeConnectionLoginRequest(
            baseUrl = serverUrl,
            username = username,
            password = password,
            displayName = displayName,
            tlsSettings = tlsSettings,
            savedConnectionForLogin = savedConnectionForLogin,
            nativeAuthRequired = true,
        ),
    )
    val connection = preparedConnection.connection
    NavidromeTls.applyJvmDefaults(connection.tlsSettings)
    val provider = NavidromeProvider(connection)
    val validation = provider.validateConnection()
    if (clearProviderData) {
        cacheMaintenanceRepository.clearProviderData()
    }
    val sourceId = providerMediaSourceRepository.upsertProviderMediaSource(
        connection = ProviderMediaSourceConnection(
            displayName = connection.resolvedDisplayName(),
            baseUrl = connection.baseUrl,
            username = connection.username,
            token = connection.token,
            salt = connection.salt,
            nativeToken = connection.nativeToken,
            tlsSettings = connection.tlsSettings,
        ),
        cacheNamespace = provider.cacheNamespace,
        providerId = provider.id.value,
    ).id
    return DesktopConnectionSession(
        connection = connection,
        provider = provider,
        sourceId = sourceId,
        validation = validation,
        smartPlaylistAuthWarning = preparedConnection.nativeAuthErrorMessage,
    )
}

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
