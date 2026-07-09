package app.naviamp.desktop

import app.naviamp.domain.Playlist
import app.naviamp.domain.cache.ProviderMediaSourceConnection
import app.naviamp.domain.cache.ProviderMediaSourceRepository
import app.naviamp.domain.cache.ProviderResponseCacheRepository
import app.naviamp.domain.cache.ProviderResponseService
import app.naviamp.domain.provider.loadSmartPlaylistDefinition
import app.naviamp.domain.provider.saveSmartPlaylistStateUpdate
import app.naviamp.domain.provider.smartPlaylistLoadErrorMessage
import app.naviamp.domain.provider.smartPlaylistLoadingRulesStatus
import app.naviamp.domain.provider.smartPlaylistSaveErrorMessage
import app.naviamp.domain.provider.smartPlaylistSavingStatus
import app.naviamp.domain.provider.smartPlaylistUpdateErrorMessage
import app.naviamp.domain.provider.smartPlaylistUpdatingStatus
import app.naviamp.domain.provider.updateSmartPlaylistStateUpdate
import app.naviamp.domain.smartplaylist.SmartPlaylistDefinition
import app.naviamp.provider.navidrome.NavidromeConnection
import app.naviamp.provider.navidrome.NavidromeProvider
import app.naviamp.provider.navidrome.resolvedDisplayName
import app.naviamp.provider.navidrome.withNativeTokenFromPassword
import app.naviamp.desktop.settings.DesktopSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DesktopSmartPlaylistsController(
    private val providerMediaSourceRepository: ProviderMediaSourceRepository,
    providerResponseCacheRepository: ProviderResponseCacheRepository,
    private val settingsStore: DesktopSettingsStore,
    private val provider: () -> NavidromeProvider?,
    private val setProvider: (NavidromeProvider) -> Unit,
    private val password: () -> String,
    private val clearPassword: () -> Unit,
    private val savedConnectionForLogin: () -> NavidromeConnection?,
    private val setSavedConnectionForLogin: (NavidromeConnection) -> Unit,
    private val setConnectedSourceId: (String) -> Unit,
    private val incrementMediaSourcesRevision: () -> Unit,
    private val incrementStatsForNerdsRefreshTick: () -> Unit,
    private val playlistsController: DesktopPlaylistsController,
    private val setConnectionStatus: (String) -> Unit,
) {
    private val providerResponseService = ProviderResponseService(providerResponseCacheRepository)

    suspend fun saveSmartPlaylist(definition: SmartPlaylistDefinition) {
        saveSmartPlaylist(definition, passwordOverride = null)
    }

    suspend fun saveSmartPlaylistWithPassword(definition: SmartPlaylistDefinition, password: String) {
        saveSmartPlaylist(definition, passwordOverride = password)
    }

    private suspend fun saveSmartPlaylist(definition: SmartPlaylistDefinition, passwordOverride: String?) {
        val activeProvider = smartPlaylistProvider(passwordOverride)
        playlistsController.updateStatus(smartPlaylistSavingStatus(definition))
        try {
            val update = withContext(Dispatchers.IO) {
                saveSmartPlaylistStateUpdate(
                    provider = activeProvider,
                    definition = definition,
                    currentPlaylistTracksById = playlistsController.playlistTracksById,
                    providerResponseService = providerResponseService,
                )
            }
            playlistsController.updatePlaylistTracksById(update.playlistTracksById)
            playlistsController.applyRefreshedPlaylists(update.playlists)
            playlistsController.updateStatus(update.status)
        } catch (error: Exception) {
            val message = smartPlaylistSaveErrorMessage(error)
            playlistsController.updateStatus(message)
            if (message != error.message) throw IllegalStateException(message)
            throw error
        } finally {
            incrementStatsForNerdsRefreshTick()
        }
    }

    suspend fun updateSmartPlaylist(playlist: Playlist, definition: SmartPlaylistDefinition) {
        updateSmartPlaylist(playlist, definition, passwordOverride = null)
    }

    suspend fun updateSmartPlaylistWithPassword(
        playlist: Playlist,
        definition: SmartPlaylistDefinition,
        password: String,
    ) {
        updateSmartPlaylist(playlist, definition, passwordOverride = password)
    }

    private suspend fun updateSmartPlaylist(
        playlist: Playlist,
        definition: SmartPlaylistDefinition,
        passwordOverride: String?,
    ) {
        val activeProvider = smartPlaylistProvider(passwordOverride)
        playlistsController.updateStatus(smartPlaylistUpdatingStatus(definition))
        try {
            val update = withContext(Dispatchers.IO) {
                updateSmartPlaylistStateUpdate(
                    provider = activeProvider,
                    playlist = playlist,
                    definition = definition,
                    currentSelectedPlaylist = playlistsController.selectedPlaylist,
                    currentPlaylistTracksById = playlistsController.playlistTracksById,
                    providerResponseService = providerResponseService,
                )
            }
            playlistsController.updatePlaylistTracksById(update.playlistTracksById)
            playlistsController.applyRefreshedPlaylists(update.playlists)
            update.selectionApplication?.let { selection ->
                playlistsController.applySelectedPlaylistDetails(selection.playlist, selection.tracks, selection.status)
            }
            playlistsController.updateStatus(update.status)
        } catch (error: Exception) {
            val message = smartPlaylistUpdateErrorMessage(error)
            playlistsController.updateStatus(message)
            if (message != error.message) throw IllegalStateException(message)
            throw error
        } finally {
            incrementStatsForNerdsRefreshTick()
        }
    }

    suspend fun loadSmartPlaylistDefinition(playlist: Playlist): SmartPlaylistDefinition {
        val activeProvider = provider()
            ?: throw IllegalStateException("Connect to Navidrome before editing smart playlists.")
        playlistsController.updateStatus(smartPlaylistLoadingRulesStatus(playlist))
        return try {
            withContext(Dispatchers.IO) { activeProvider.loadSmartPlaylistDefinition(playlist) }
                .also { playlistsController.updateStatus(null) }
        } catch (error: Exception) {
            playlistsController.updateStatus(smartPlaylistLoadErrorMessage(error))
            throw error
        } finally {
            incrementStatsForNerdsRefreshTick()
        }
    }

    private suspend fun smartPlaylistProvider(passwordOverride: String?): NavidromeProvider {
        val activeProvider = provider()
            ?: throw IllegalStateException("Connect to Navidrome before saving smart playlists.")
        val savedConnection = savedConnectionForLogin()
        if (savedConnection?.nativeToken?.isNotBlank() == true) return activeProvider
        val passwordToUse = passwordOverride?.takeIf { it.isNotBlank() } ?: password().takeIf { it.isNotBlank() }
            ?: return activeProvider
        val connection = savedConnection
            ?: throw IllegalStateException("Reconnect to Navidrome with your password before saving smart playlists.")
        val refreshedConnection = withContext(Dispatchers.IO) {
            connection.withNativeTokenFromPassword(passwordToUse, required = true)
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
        settingsStore.saveConnection(refreshedConnection)
        incrementMediaSourcesRevision()
        clearPassword()
        setConnectionStatus("Smart playlist authentication refreshed.")
        return refreshedProvider
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
        secondaryUrls = secondaryUrls,
        customHeaders = customHeaders,
        selectedMusicFolderIds = selectedMusicFolderIds,
    )
