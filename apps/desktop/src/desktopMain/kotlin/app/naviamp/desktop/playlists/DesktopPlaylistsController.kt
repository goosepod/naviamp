package app.naviamp.desktop

import app.naviamp.domain.Playlist
import app.naviamp.domain.Track
import app.naviamp.domain.cache.ProviderResponseService
import app.naviamp.domain.home.HomeContent
import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.addToPlaylistErrorMessage
import app.naviamp.domain.provider.addToPlaylistResolvingTracksStatus
import app.naviamp.domain.provider.addTracksToPlaylistStateUpdate
import app.naviamp.domain.provider.clearPendingPlaybackAction
import app.naviamp.domain.provider.PendingPlaybackAction
import app.naviamp.domain.provider.playlistDeleteErrorMessage
import app.naviamp.domain.provider.playlistDeleteLoadingStatus
import app.naviamp.domain.provider.playlistDeleteApplicationUpdate
import app.naviamp.domain.provider.deletePlaylistAndRefresh
import app.naviamp.domain.provider.playlistDetailsErrorMessage
import app.naviamp.domain.provider.playlistDetailsLoadingStatus
import app.naviamp.domain.provider.playlistListErrorMessage
import app.naviamp.domain.provider.playlistListLoadingStatus
import app.naviamp.domain.provider.playlistPlaybackErrorMessage
import app.naviamp.domain.provider.playlistPlaybackStartPlan
import app.naviamp.domain.provider.preparePlaylistPlaybackApplication
import app.naviamp.domain.provider.playlistRenameErrorMessage
import app.naviamp.domain.provider.playlistRenameLoadingStatus
import app.naviamp.domain.provider.playlistRenameStateUpdate
import app.naviamp.domain.provider.queuePlaylistSaveErrorMessage
import app.naviamp.domain.provider.queuePlaylistSaveLoadingStatus
import app.naviamp.domain.provider.queuePlaylistSaveStateUpdate
import app.naviamp.domain.provider.recentPlaylistIdsAfterPlayed
import app.naviamp.domain.provider.refreshPlaylistDetailsApplication
import app.naviamp.domain.provider.refreshPlaylistListState
import app.naviamp.domain.provider.renamePlaylistAndRefresh
import app.naviamp.domain.provider.saveQueueAsPlaylistAndRefresh
import app.naviamp.domain.provider.selectedPlaylistPlaybackApplicationUpdate
import app.naviamp.domain.provider.preloadPlaylistTracksStateUpdate
import app.naviamp.domain.provider.withPlaylists
import app.naviamp.domain.playback.PlaybackQueueManager
import app.naviamp.domain.settings.PlaybackSettings
import app.naviamp.desktop.playback.PlaylistCallbacks
import app.naviamp.desktop.playback.DesktopPlaylistEngine
import app.naviamp.desktop.settings.DesktopSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DesktopPlaylistsController(
    private val scope: CoroutineScope,
    private val settingsStore: DesktopSettingsStore,
    private val playbackEngine: PlaybackEngine,
    private val playlistEngine: DesktopPlaylistEngine,
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
    private val setAppRoute: (DesktopAppRoute) -> Unit,
    private val stopRadioContinuation: () -> Unit,
    private val clearShuffleSnapshot: () -> Unit,
    private val setOpenPlayerOnTrackStart: (Boolean) -> Unit,
) {
    fun refreshPlaylists(useCache: Boolean = true) {
        val activeProvider = provider() ?: return
        setPlaylistStatus(playlistListLoadingStatus())
        scope.launch {
            try {
                val update = withContext(Dispatchers.IO) {
                    activeProvider.refreshPlaylistListState(
                        providerResponseService = providerResponseService,
                        useCache = useCache,
                        playlistTracksById = playlistTracksById(),
                    )
                }
                setPlaylists(update.playlists)
                setPlaylistStatus(update.status)
                refreshHomePlaylists(update.playlists)
                val preloadUpdate = withContext(Dispatchers.IO) {
                    activeProvider.preloadPlaylistTracksStateUpdate(
                        playlists = update.playlistsToPreload,
                        currentPlaylistTracksById = playlistTracksById(),
                        providerResponseService = providerResponseService,
                        useCache = useCache,
                    )
                }
                setPlaylistTracksById(preloadUpdate.playlistTracksById)
            } catch (exception: Exception) {
                setPlaylistStatus(playlistListErrorMessage(exception))
            }
        }
    }

    suspend fun refreshPlaylistDetailsFromServer(
        activeProvider: MediaProvider,
        playlist: Playlist,
        showLoadingStatus: Boolean,
    ) {
        if (showLoadingStatus) setSelectedPlaylistStatus(playlistDetailsLoadingStatus(playlist))
        val update = withContext(Dispatchers.IO) {
            activeProvider.refreshPlaylistDetailsApplication(
                playlist = playlist,
                currentSelectedPlaylist = selectedPlaylist(),
                currentSelectedPlaylistTracks = selectedPlaylistTracks(),
                currentPlaylistTracksById = playlistTracksById(),
                providerResponseService = providerResponseService,
            )
        }
        setPlaylists(update.playlists)
        refreshHomePlaylists(update.playlists)
        setPlaylistTracksById(update.playlistTracksById)
        if (update.selectedPlaylistChanged) {
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
        setAddToPlaylistStatus(addToPlaylistResolvingTracksStatus())
        scope.launch {
            try {
                val update = withContext(Dispatchers.IO) {
                    activeProvider.addTracksToPlaylistStateUpdate(
                        playlistId = playlist?.id,
                        playlistName = playlist?.name,
                        newPlaylistName = newPlaylistName,
                        tracks = resolveAddToPlaylistTargetTracks(activeProvider, target),
                        providerResponseService = providerResponseService,
                    )
                }
                if (update.closeDialog) setAddToPlaylistTarget(null)
                setAddToPlaylistStatus(update.addToPlaylistStatus)
                update.connectionStatus?.let(setConnectionStatus)
                update.playlists?.also(setPlaylists)?.let(::refreshHomePlaylists)
            } catch (exception: Exception) {
                setAddToPlaylistStatus(addToPlaylistErrorMessage(exception, "tracks"))
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
                val update = PlaybackQueueManager().appendTracks(
                    currentQueue = playlistEngine.queue,
                    tracksToAdd = tracksToAdd,
                )
                setConnectionStatus(update.status)
                if (!update.tracksChanged) {
                    return@launch
                }
                playlistEngine.replaceQueue(update.queue)
            } catch (exception: Exception) {
                setConnectionStatus(exception.message ?: "Could not add to queue.")
            }
        }
    }

    fun saveQueueAsPlaylist(name: String) {
        val activeProvider = provider() ?: return
        val queueTracks = playlistEngine.queue.tracks
        setConnectionStatus(queuePlaylistSaveLoadingStatus())
        scope.launch {
            try {
                val refresh = withContext(Dispatchers.IO) {
                    activeProvider.saveQueueAsPlaylistAndRefresh(
                        name = name,
                        tracks = queueTracks,
                        providerResponseService = providerResponseService,
                    )
                }
                val update = queuePlaylistSaveStateUpdate(refresh)
                setConnectionStatus(update.status)
                setPlaylists(update.playlists)
                refreshHomePlaylists(update.playlists)
            } catch (exception: Exception) {
                setConnectionStatus(queuePlaylistSaveErrorMessage(exception))
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
        val startPlan = playlistPlaybackStartPlan(playlist, shuffle, pendingPlaybackAction())
        if (!startPlan.shouldStart) {
            setConnectionStatus(startPlan.status)
            return
        }
        setPendingPlaybackAction(startPlan.action)
        setConnectionStatus(startPlan.status)
        scope.launch {
            try {
                val update = withContext(Dispatchers.IO) {
                    activeProvider.preparePlaylistPlaybackApplication(
                        playlist = playlist,
                        shuffle = shuffle,
                        selectedPlaylist = null,
                        selectedPlaylistTracks = emptyList(),
                        recentPlaylistIds = recentPlaylistIds(),
                        recentPlaylistLimit = 50,
                        currentPlaylistTracksById = playlistTracksById(),
                        providerResponseService = providerResponseService,
                        emptyStatus = "${playlist.name} did not return any tracks.",
                    )
                }
                setPlaylistTracksById(update.playlistTracksById)
                if (update.firstTrack == null) {
                    setConnectionStatus(update.status)
                    return@launch
                }
                setConnectionStatus(update.status)
                setPendingPlaybackAction(clearPendingPlaybackAction(pendingPlaybackAction(), startPlan.action))
                playTracks(playlist, activeProvider, update.playbackTracks, index = 0)
            } catch (exception: Exception) {
                setConnectionStatus(playlistPlaybackErrorMessage(exception, playlist))
            } finally {
                setPendingPlaybackAction(clearPendingPlaybackAction(pendingPlaybackAction(), startPlan.action))
            }
        }
    }

    fun openPlaylistDetails(playlist: Playlist) {
        val activeProvider = provider() ?: return
        setSelectedPlaylist(playlist)
        setSelectedPlaylistTracks(emptyList())
        setSelectedPlaylistStatus(playlistDetailsLoadingStatus(playlist))
        setAppRoute(DesktopAppRoute.PlaylistDetail)
        scope.launch {
            try {
                refreshPlaylistDetailsFromServer(activeProvider, playlist, showLoadingStatus = false)
            } catch (exception: Exception) {
                setSelectedPlaylistStatus(playlistDetailsErrorMessage(exception))
            }
        }
    }

    fun playPlaylistDetails(index: Int = 0, shuffle: Boolean = false) {
        val activeProvider = provider() ?: return
        val playlist = selectedPlaylist() ?: return
        val startPlan = playlistPlaybackStartPlan(playlist, shuffle, pendingPlaybackAction())
        if (!startPlan.shouldStart) {
            return setSelectedPlaylistStatus(startPlan.status)
        }
        val update = selectedPlaylistPlaybackApplicationUpdate(playlist, selectedPlaylistTracks(), shuffle, recentPlaylistIds(), 50, index)
        if (update.firstTrack == null) {
            return setSelectedPlaylistStatus(update.status)
        }
        setPendingPlaybackAction(startPlan.action)
        setSelectedPlaylistStatus(startPlan.status)
        playTracks(playlist, activeProvider, update.playbackTracks, update.playbackIndex)
        setSelectedPlaylistStatus(null)
        setPendingPlaybackAction(clearPendingPlaybackAction(pendingPlaybackAction(), startPlan.action))
    }

    fun renamePlaylist(playlist: Playlist, name: String) {
        val activeProvider = provider() ?: return
        setPlaylistStatus(playlistRenameLoadingStatus(playlist))
        scope.launch {
            try {
                val refresh = withContext(Dispatchers.IO) {
                    activeProvider.renamePlaylistAndRefresh(
                        playlist = playlist,
                        name = name,
                        providerResponseService = providerResponseService,
                    )
                }
                val update = playlistRenameStateUpdate(selectedPlaylist(), refresh, playlist.id)
                setPlaylists(update.playlists)
                refreshHomePlaylists(update.playlists)
                setPlaylistPendingRename(null)
                setPlaylistStatus(update.status)
                setSelectedPlaylist(update.selectedPlaylist)
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
                val refresh = withContext(Dispatchers.IO) {
                    activeProvider.deletePlaylistAndRefresh(
                        playlist = playlist,
                        providerResponseService = providerResponseService,
                    )
                }
                setPlaylistPendingDelete(null)
                val update = playlistDeleteApplicationUpdate(
                    refresh = refresh,
                    currentSelectedPlaylist = selectedPlaylist(),
                    currentSelectedPlaylistTracks = selectedPlaylistTracks(),
                    currentPlaylistTracksById = playlistTracksById(),
                    currentRecentPlaylistIds = recentPlaylistIds(),
                    deletedPlaylistId = playlist.id,
                )
                setPlaylists(update.playlists)
                refreshHomePlaylists(update.playlists)
                setSelectedPlaylist(update.selectedPlaylist)
                setSelectedPlaylistTracks(update.selectedPlaylistTracks)
                if (update.deletedSelectedPlaylist) {
                    setAppRoute(DesktopAppRoute.Playlists)
                }
                setPlaylistTracksById(update.playlistTracksById)
                setRecentPlaylistIds(update.recentPlaylistIds)
                setPlaylistStatus(update.status)
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
        setHomeContent(homeContent().withPlaylists(refreshedPlaylists, recentPlaylistIds()))
    }
}
