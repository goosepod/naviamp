package app.naviamp.desktop

import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Playlist
import app.naviamp.domain.Track
import app.naviamp.domain.cache.ProviderResponseService
import app.naviamp.domain.home.HomeContent
import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.addToPlaylistMutationUpdate
import app.naviamp.domain.provider.clearPendingPlaybackAction
import app.naviamp.domain.provider.homePlaylists
import app.naviamp.domain.provider.normalizedPlaylistName
import app.naviamp.domain.provider.PendingPlaybackAction
import app.naviamp.domain.provider.playlistDeleteErrorMessage
import app.naviamp.domain.provider.playlistDeleteLoadingStatus
import app.naviamp.domain.provider.playlistDeleteStateUpdate
import app.naviamp.domain.provider.playlistDeletedStatus
import app.naviamp.domain.provider.playlistDetailsStateUpdate
import app.naviamp.domain.provider.playlistPlaybackAction
import app.naviamp.domain.provider.playlistPlaybackTracks
import app.naviamp.domain.provider.playlistRenameErrorMessage
import app.naviamp.domain.provider.playlistRenameLoadingStatus
import app.naviamp.domain.provider.playlistRenamedStatus
import app.naviamp.domain.provider.playlistsNeedingTrackPreload
import app.naviamp.domain.provider.queueAppendPlan
import app.naviamp.domain.provider.recentPlaylistIdsAfterPlayed
import app.naviamp.domain.provider.refreshPlaylistDetails
import app.naviamp.domain.provider.renamedSelectedPlaylist
import app.naviamp.domain.provider.shouldStartPlaybackAction
import app.naviamp.domain.settings.PlaybackSettings
import app.naviamp.domain.settings.RecentRadioStream
import app.naviamp.desktop.playback.PlaylistCallbacks
import app.naviamp.desktop.playback.PlaylistEngine
import app.naviamp.desktop.settings.DesktopSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DesktopPlaylistsController(
    private val scope: CoroutineScope,
    private val settingsStore: DesktopSettingsStore,
    private val playbackEngine: PlaybackEngine,
    private val playlistEngine: PlaylistEngine,
    private val providerResponseService: ProviderResponseService,
    private val provider: () -> MediaProvider?,
    private val playbackSettings: () -> PlaybackSettings,
    private val playlistCallbacks: () -> PlaylistCallbacks,
    private val playlists: () -> List<Playlist>,
    private val setPlaylists: (List<Playlist>) -> Unit,
    private val recentPlaylistIds: () -> List<String>,
    private val setRecentPlaylistIds: (List<String>) -> Unit,
    private val homeContent: () -> HomeContent,
    private val setHomeContent: (HomeContent) -> Unit,
    private val recentRadioStreams: () -> List<RecentRadioStream>,
    private val recentInternetRadioStations: () -> List<InternetRadioStation>,
    private val playlistTracksById: () -> Map<String, List<Track>>,
    private val setPlaylistTracksById: (Map<String, List<Track>>) -> Unit,
    private val selectedPlaylist: () -> Playlist?,
    private val setSelectedPlaylist: (Playlist?) -> Unit,
    private val selectedPlaylistTracks: () -> List<Track>,
    private val setSelectedPlaylistTracks: (List<Track>) -> Unit,
    private val pendingPlaybackAction: () -> PendingPlaybackAction?,
    private val setPendingPlaybackAction: (PendingPlaybackAction?) -> Unit,
    private val setSelectedPlaylistStatus: (String?) -> Unit,
    private val setPlaylistStatus: (String?) -> Unit,
    private val setAddToPlaylistTarget: (AddToPlaylistTarget?) -> Unit,
    private val setAddToPlaylistStatus: (String?) -> Unit,
    private val setPlaylistPendingRename: (Playlist?) -> Unit,
    private val setPlaylistPendingDelete: (Playlist?) -> Unit,
    private val setConnectionStatus: (String?) -> Unit,
    private val setAppRoute: (AppRoute) -> Unit,
    private val stopRadioContinuation: () -> Unit,
    private val clearShuffleSnapshot: () -> Unit,
    private val setOpenPlayerOnTrackStart: (Boolean) -> Unit,
) {
    fun refreshPlaylists(useCache: Boolean = true) {
        val activeProvider = provider() ?: return
        setPlaylistStatus("Loading playlists...")
        scope.launch {
            try {
                val refreshedPlaylists = withContext(Dispatchers.IO) {
                    if (useCache) {
                        providerResponseService.playlists(activeProvider, limit = 500)
                    } else {
                        activeProvider.playlists(limit = 500)
                    }
                }
                setPlaylists(refreshedPlaylists)
                setPlaylistStatus(null)
                refreshHomePlaylists(refreshedPlaylists)
                playlistsNeedingTrackPreload(refreshedPlaylists, playlistTracksById()).forEach { playlist ->
                    runCatching {
                        withContext(Dispatchers.IO) {
                            if (useCache) {
                                providerResponseService.playlistTracks(activeProvider, playlist.id)
                            } else {
                                activeProvider.playlistTracks(playlist.id)
                            }
                        }
                    }.onSuccess { tracks ->
                        setPlaylistTracksById(playlistTracksById() + (playlist.id to tracks))
                    }
                }
            } catch (exception: Exception) {
                setPlaylistStatus(exception.message ?: "Could not load playlists.")
            }
        }
    }

    suspend fun refreshPlaylistDetailsFromServer(
        activeProvider: MediaProvider,
        playlist: Playlist,
        showLoadingStatus: Boolean,
    ) {
        if (showLoadingStatus) setSelectedPlaylistStatus("Loading ${playlist.name}...")
        val refresh = withContext(Dispatchers.IO) {
            activeProvider.refreshPlaylistDetails(
                playlist = playlist,
                providerResponseService = providerResponseService,
            )
        }
        val update = playlistDetailsStateUpdate(
            currentSelectedPlaylist = selectedPlaylist(),
            currentSelectedPlaylistTracks = selectedPlaylistTracks(),
            currentPlaylistTracksById = playlistTracksById(),
            refresh = refresh,
            requestedPlaylistId = playlist.id,
        )
        setPlaylists(update.playlists)
        refreshHomePlaylists(update.playlists)
        setPlaylistTracksById(update.playlistTracksById)
        if (selectedPlaylist()?.id == playlist.id) {
            setSelectedPlaylist(update.selectedPlaylist)
            setSelectedPlaylistTracks(update.selectedPlaylistTracks)
            setSelectedPlaylistStatus(null)
        }
    }

    fun openAddToPlaylist(target: AddToPlaylistTarget) {
        setAddToPlaylistTarget(target)
        setAddToPlaylistStatus(null)
        if (playlists().isEmpty()) refreshPlaylists()
    }

    fun addTargetToPlaylist(target: AddToPlaylistTarget, playlist: Playlist?, newPlaylistName: String? = null) {
        val activeProvider = provider() ?: return
        setAddToPlaylistStatus("Loading tracks...")
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    addTargetTracksToPlaylist(activeProvider, target, playlist, newPlaylistName)
                }
                val update = addToPlaylistMutationUpdate(result, playlist?.name)
                if (update.closeDialog) setAddToPlaylistTarget(null)
                setAddToPlaylistStatus(update.addToPlaylistStatus)
                update.connectionStatus?.let(setConnectionStatus)
                if (update.refreshPlaylists) {
                    playlist?.let {
                        providerResponseService.invalidatePlaylistResponses(activeProvider, it.id)
                    } ?: providerResponseService.invalidatePlaylists(activeProvider)
                    refreshPlaylists()
                }
            } catch (exception: Exception) {
                setAddToPlaylistStatus(exception.message ?: "Could not add to playlist.")
            }
        }
    }

    fun addTargetToQueue(target: AddToPlaylistTarget) {
        val activeProvider = provider() ?: return
        setConnectionStatus("Loading tracks...")
        scope.launch {
            try {
                val tracksToAdd = withContext(Dispatchers.IO) {
                    resolveAddToPlaylistTargetTracks(activeProvider, target)
                }
                val plan = queueAppendPlan(tracksToAdd)
                setConnectionStatus(plan.status)
                if (plan.tracks.isEmpty()) {
                    return@launch
                }
                playlistEngine.appendTracks(plan.tracks)
            } catch (exception: Exception) {
                setConnectionStatus(exception.message ?: "Could not add to queue.")
            }
        }
    }

    fun markPlaylistPlayed(playlist: Playlist) {
        val updatedRecentIds = recentPlaylistIdsAfterPlayed(recentPlaylistIds(), playlist.id, limit = 50)
        setRecentPlaylistIds(updatedRecentIds)
        settingsStore.saveRecentPlaylistIds(updatedRecentIds)
    }

    fun playPlaylist(playlist: Playlist, shuffle: Boolean = false) {
        val activeProvider = provider() ?: return
        val playbackAction = playlistPlaybackAction(playlist, shuffle)
        if (!shouldStartPlaybackAction(pendingPlaybackAction())) {
            pendingPlaybackAction()?.status?.let(setConnectionStatus)
            return
        }
        setPendingPlaybackAction(playbackAction)
        setConnectionStatus(playbackAction.status)
        scope.launch {
            try {
                val loadedTracks = withContext(Dispatchers.IO) {
                    providerResponseService.playlistTracks(activeProvider, playlist.id)
                }
                setPlaylistTracksById(playlistTracksById() + (playlist.id to loadedTracks))
                val tracks = playlistPlaybackTracks(loadedTracks, shuffle)
                if (tracks.isEmpty()) {
                    setConnectionStatus("${playlist.name} did not return any tracks.")
                    return@launch
                }
                setConnectionStatus(null)
                setPendingPlaybackAction(clearPendingPlaybackAction(pendingPlaybackAction(), playbackAction))
                playTracks(playlist, activeProvider, tracks, index = 0)
            } catch (exception: Exception) {
                setConnectionStatus(exception.message ?: "Could not play ${playlist.name}.")
            } finally {
                setPendingPlaybackAction(clearPendingPlaybackAction(pendingPlaybackAction(), playbackAction))
            }
        }
    }

    fun openPlaylistDetails(playlist: Playlist) {
        val activeProvider = provider() ?: return
        setSelectedPlaylist(playlist)
        setSelectedPlaylistTracks(emptyList())
        setSelectedPlaylistStatus("Loading ${playlist.name}...")
        setAppRoute(AppRoute.PlaylistDetail)
        scope.launch {
            try {
                refreshPlaylistDetailsFromServer(activeProvider, playlist, showLoadingStatus = false)
            } catch (exception: Exception) {
                setSelectedPlaylistStatus(exception.message ?: "Could not load ${playlist.name}.")
            }
        }
    }

    fun playPlaylistDetails(index: Int = 0, shuffle: Boolean = false) {
        val activeProvider = provider() ?: return
        val playlist = selectedPlaylist() ?: return
        val playbackAction = playlistPlaybackAction(playlist, shuffle)
        if (!shouldStartPlaybackAction(pendingPlaybackAction())) {
            pendingPlaybackAction()?.status?.let(setSelectedPlaylistStatus)
            return
        }
        val tracks = playlistPlaybackTracks(selectedPlaylistTracks(), shuffle)
        if (tracks.isEmpty()) return
        setPendingPlaybackAction(playbackAction)
        setSelectedPlaylistStatus(playbackAction.status)
        playTracks(
            playlist = playlist,
            activeProvider = activeProvider,
            tracks = tracks,
            index = index.coerceIn(tracks.indices),
        )
        setSelectedPlaylistStatus(null)
        setPendingPlaybackAction(clearPendingPlaybackAction(pendingPlaybackAction(), playbackAction))
    }

    fun renamePlaylist(playlist: Playlist, name: String) {
        val activeProvider = provider() ?: return
        val requestedName = normalizedPlaylistName(name)
        setPlaylistStatus(playlistRenameLoadingStatus(playlist))
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    activeProvider.renamePlaylist(playlist.id, requestedName)
                }
                providerResponseService.invalidatePlaylistResponses(activeProvider, playlist.id)
                setPlaylistPendingRename(null)
                setPlaylistStatus(playlistRenamedStatus())
                setSelectedPlaylist(
                    renamedSelectedPlaylist(
                        current = selectedPlaylist(),
                        playlistId = playlist.id,
                        requestedName = requestedName,
                        refreshedPlaylists = emptyList(),
                    ),
                )
                refreshPlaylists()
            } catch (exception: Exception) {
                setPlaylistStatus(playlistRenameErrorMessage(exception))
            }
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        val activeProvider = provider() ?: return
        setPlaylistStatus(playlistDeleteLoadingStatus(playlist))
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    activeProvider.deletePlaylist(playlist.id)
                }
                providerResponseService.invalidatePlaylistResponses(activeProvider, playlist.id)
                setPlaylistPendingDelete(null)
                val update = playlistDeleteStateUpdate(
                    currentSelectedPlaylist = selectedPlaylist(),
                    currentSelectedPlaylistTracks = selectedPlaylistTracks(),
                    currentPlaylistTracksById = playlistTracksById(),
                    currentRecentPlaylistIds = recentPlaylistIds(),
                    deletedPlaylistId = playlist.id,
                )
                setSelectedPlaylist(update.selectedPlaylist)
                setSelectedPlaylistTracks(update.selectedPlaylistTracks)
                if (update.deletedSelectedPlaylist) {
                    setAppRoute(AppRoute.Playlists)
                }
                setPlaylistTracksById(update.playlistTracksById)
                setRecentPlaylistIds(update.recentPlaylistIds)
                setPlaylistStatus(playlistDeletedStatus())
                refreshPlaylists()
            } catch (exception: Exception) {
                setPlaylistStatus(playlistDeleteErrorMessage(exception))
            }
        }
    }

    private fun playTracks(
        playlist: Playlist,
        activeProvider: MediaProvider,
        tracks: List<Track>,
        index: Int,
    ) {
        markPlaylistPlayed(playlist)
        stopRadioContinuation()
        clearShuffleSnapshot()
        setOpenPlayerOnTrackStart(true)
        playlistEngine.playFrom(
            scope = scope,
            provider = activeProvider,
            tracks = tracks,
            index = index,
            quality = playbackSettings().streamQuality(playbackEngine),
            replayGainMode = playbackSettings().replayGainMode,
            callbacks = playlistCallbacks(),
        )
    }

    private fun refreshHomePlaylists(refreshedPlaylists: List<Playlist>) {
        setHomeContent(
            homeContent().copy(
                playlists = homePlaylists(refreshedPlaylists, recentPlaylistIds()),
                recentRadioStreams = recentRadioStreams(),
                recentInternetRadioStations = recentInternetRadioStations(),
            ),
        )
    }
}
