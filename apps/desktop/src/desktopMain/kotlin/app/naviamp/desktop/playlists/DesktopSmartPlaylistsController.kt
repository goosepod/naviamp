package app.naviamp.desktop

import app.naviamp.domain.Playlist
import app.naviamp.domain.Track
import app.naviamp.domain.cache.ProviderMediaSourceConnection
import app.naviamp.domain.cache.ProviderMediaSourceRepository
import app.naviamp.domain.cache.ProviderResponseCacheRepository
import app.naviamp.domain.cache.ProviderResponseService
import app.naviamp.domain.home.HomeContent
import app.naviamp.domain.provider.PlaylistHomeProjection
import app.naviamp.domain.provider.playlistListApplication
import app.naviamp.domain.provider.saveSmartPlaylistAndRefresh
import app.naviamp.domain.provider.smartPlaylistLoadErrorMessage
import app.naviamp.domain.provider.smartPlaylistLoadingRulesStatus
import app.naviamp.domain.provider.smartPlaylistSaveErrorMessage
import app.naviamp.domain.provider.smartPlaylistSaveStateUpdate
import app.naviamp.domain.provider.smartPlaylistSavingStatus
import app.naviamp.domain.provider.smartPlaylistUpdateErrorMessage
import app.naviamp.domain.provider.smartPlaylistUpdateStateUpdate
import app.naviamp.domain.provider.smartPlaylistUpdatingStatus
import app.naviamp.domain.provider.updateSmartPlaylistAndRefresh
import app.naviamp.domain.smartplaylist.SmartPlaylistDefinition
import app.naviamp.provider.navidrome.NavidromeConnection
import app.naviamp.provider.navidrome.NavidromeProvider
import app.naviamp.provider.navidrome.resolvedDisplayName
import app.naviamp.provider.navidrome.withNativeTokenFromPassword
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DesktopSmartPlaylistsController(
    private val providerMediaSourceRepository: ProviderMediaSourceRepository,
    providerResponseCacheRepository: ProviderResponseCacheRepository,
    private val provider: () -> NavidromeProvider?,
    private val setProvider: (NavidromeProvider) -> Unit,
    private val password: () -> String,
    private val clearPassword: () -> Unit,
    private val savedConnectionForLogin: () -> NavidromeConnection?,
    private val setSavedConnectionForLogin: (NavidromeConnection) -> Unit,
    private val setConnectedSourceId: (String) -> Unit,
    private val incrementMediaSourcesRevision: () -> Unit,
    private val incrementStatsForNerdsRefreshTick: () -> Unit,
    private val setPlaylists: (List<Playlist>) -> Unit,
    private val recentPlaylistIds: () -> List<String>,
    private val homeContent: () -> HomeContent,
    private val setHomeContent: (HomeContent) -> Unit,
    private val playlistTracksById: () -> Map<String, List<Track>>,
    private val setPlaylistTracksById: (Map<String, List<Track>>) -> Unit,
    private val selectedPlaylist: () -> Playlist?,
    private val setSelectedPlaylist: (Playlist?) -> Unit,
    private val setSelectedPlaylistTracks: (List<Track>) -> Unit,
    private val setSelectedPlaylistStatus: (String?) -> Unit,
    private val setPlaylistStatus: (String?) -> Unit,
    private val setConnectionStatus: (String) -> Unit,
) {
    private val providerResponseService = ProviderResponseService(providerResponseCacheRepository)

    suspend fun saveSmartPlaylist(definition: SmartPlaylistDefinition) {
        var activeProvider = provider()
            ?: throw IllegalStateException("Connect to Navidrome before saving smart playlists.")
        setPlaylistStatus(smartPlaylistSavingStatus(definition))
        try {
            val savedConnection = savedConnectionForLogin()
            if (savedConnection?.nativeToken.isNullOrBlank() && password().isNotBlank()) {
                val refreshedConnection = withContext(Dispatchers.IO) {
                    savedConnection!!.withNativeTokenFromPassword(password(), required = true)
                }
                val refreshedProvider = NavidromeProvider(refreshedConnection)
                setProvider(refreshedProvider)
                val mediaSource = providerMediaSourceRepository.upsertProviderMediaSource(
                    connection = refreshedConnection.toProviderMediaSourceConnection(),
                    cacheNamespace = refreshedProvider.cacheNamespace,
                    providerId = refreshedProvider.id.value,
                )
                setConnectedSourceId(mediaSource.id)
                setSavedConnectionForLogin(refreshedConnection)
                incrementMediaSourcesRevision()
                clearPassword()
                activeProvider = refreshedProvider
                setConnectionStatus("Smart playlist authentication refreshed.")
            }
            val refresh = withContext(Dispatchers.IO) {
                saveSmartPlaylistAndRefresh(activeProvider, definition, providerResponseService)
            }
            val update = smartPlaylistSaveStateUpdate(refresh, playlistTracksById())
            setPlaylistTracksById(update.playlistTracksById)
            applyPlaylistListApplication(update.playlists)
            setPlaylistStatus(update.status)
        } catch (error: Exception) {
            val message = smartPlaylistSaveErrorMessage(error)
            setPlaylistStatus(message)
            if (message != error.message) throw IllegalStateException(message)
            throw error
        } finally {
            incrementStatsForNerdsRefreshTick()
        }
    }

    suspend fun updateSmartPlaylist(playlist: Playlist, definition: SmartPlaylistDefinition) {
        val activeProvider = provider()
            ?: throw IllegalStateException("Connect to Navidrome before updating smart playlists.")
        setPlaylistStatus(smartPlaylistUpdatingStatus(definition))
        try {
            val refresh = withContext(Dispatchers.IO) {
                updateSmartPlaylistAndRefresh(activeProvider, playlist, definition, providerResponseService)
            }
            val update = smartPlaylistUpdateStateUpdate(
                refresh = refresh,
                currentSelectedPlaylist = selectedPlaylist(),
                currentPlaylistTracksById = playlistTracksById(),
            )
            setPlaylistTracksById(update.playlistTracksById)
            applyPlaylistListApplication(update.playlists)
            update.selectionApplication?.let { selection ->
                setSelectedPlaylist(selection.playlist)
                setSelectedPlaylistTracks(selection.tracks)
                setSelectedPlaylistStatus(selection.status)
            }
            setPlaylistStatus(update.status)
        } catch (error: Exception) {
            setPlaylistStatus(smartPlaylistUpdateErrorMessage(error))
            throw error
        } finally {
            incrementStatsForNerdsRefreshTick()
        }
    }

    suspend fun loadSmartPlaylistDefinition(playlist: Playlist): SmartPlaylistDefinition {
        val activeProvider = provider()
            ?: throw IllegalStateException("Connect to Navidrome before editing smart playlists.")
        setPlaylistStatus(smartPlaylistLoadingRulesStatus(playlist))
        return try {
            withContext(Dispatchers.IO) { activeProvider.smartPlaylistDefinition(playlist.id) }
                .also { setPlaylistStatus(null) }
        } catch (error: Exception) {
            setPlaylistStatus(smartPlaylistLoadErrorMessage(error))
            throw error
        } finally {
            incrementStatsForNerdsRefreshTick()
        }
    }

    private fun applyPlaylistListApplication(refreshedPlaylists: List<Playlist>) {
        val application = playlistListApplication(
            playlists = refreshedPlaylists,
            currentHomeContent = homeContent(),
            recentPlaylistIds = recentPlaylistIds(),
            projection = PlaylistHomeProjection.RecentLimited,
        )
        setPlaylists(application.playlists)
        setHomeContent(application.homeContent)
    }
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
    )
