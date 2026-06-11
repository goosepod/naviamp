package app.naviamp.desktop

import app.naviamp.domain.Album
import app.naviamp.domain.Artist
import app.naviamp.domain.Playlist
import app.naviamp.domain.Track
import app.naviamp.domain.cache.ProviderResponseService
import app.naviamp.domain.home.HomeContent
import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.PlaylistListApplication
import app.naviamp.domain.provider.addToPlaylistErrorMessage
import app.naviamp.domain.provider.addToPlaylistResolvingTracksStatus
import app.naviamp.domain.provider.addTracksToPlaylistApplication
import app.naviamp.domain.provider.PendingPlaybackAction
import app.naviamp.domain.provider.playlistDeleteApplication
import app.naviamp.domain.provider.playlistDeleteErrorMessage
import app.naviamp.domain.provider.playlistDeleteLoadingStatus
import app.naviamp.domain.provider.playlistDeleteApplicationUpdate
import app.naviamp.domain.provider.deletePlaylistAndRefresh
import app.naviamp.domain.provider.playlistDetailsErrorMessage
import app.naviamp.domain.provider.playlistDetailsLoadingStatus
import app.naviamp.domain.provider.PlaylistHomeProjection
import app.naviamp.domain.provider.playlistListApplication
import app.naviamp.domain.provider.playlistListErrorMessage
import app.naviamp.domain.provider.playlistListLoadingStatus
import app.naviamp.domain.provider.playlistPlaybackCompletionApplication
import app.naviamp.domain.provider.playlistPlaybackErrorMessage
import app.naviamp.domain.provider.playlistPlaybackPreparedApplication
import app.naviamp.domain.provider.playlistPlaybackStartApplication
import app.naviamp.domain.provider.playlistPlaybackStartPlan
import app.naviamp.domain.provider.playlistDetailPlaybackPreparedApplication
import app.naviamp.domain.provider.preparePlaylistDetailPlaybackApplication
import app.naviamp.domain.provider.preparePlaylistPlaybackApplication
import app.naviamp.domain.provider.playlistRenameApplication
import app.naviamp.domain.provider.playlistRenameErrorMessage
import app.naviamp.domain.provider.playlistRenameLoadingStatus
import app.naviamp.domain.provider.playlistRenameStateUpdate
import app.naviamp.domain.provider.queuePlaylistSaveErrorMessage
import app.naviamp.domain.provider.queuePlaylistSaveLoadingStatus
import app.naviamp.domain.provider.recentPlaylistIdsAfterPlayed
import app.naviamp.domain.provider.refreshPlaylistDetailsApplication
import app.naviamp.domain.provider.refreshPlaylistListState
import app.naviamp.domain.provider.renamePlaylistAndRefresh
import app.naviamp.domain.provider.saveQueueAsPlaylistApplication
import app.naviamp.domain.provider.preloadPlaylistTracksStateUpdate
import app.naviamp.domain.playback.PlaybackQueueManager
import app.naviamp.domain.playback.applyPlaybackQueueUpdate
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
                applyPlaylistListApplication(update.playlists)
                setPlaylistStatus(update.status)
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
        applyPlaylistListApplication(update.playlists)
        setPlaylistTracksById(update.playlistTracksById)
        update.selectionApplication?.let { selection ->
            setSelectedPlaylist(selection.playlist)
            setSelectedPlaylistTracks(selection.tracks)
            setSelectedPlaylistStatus(selection.status)
        }
    }

    fun openAddToPlaylist(target: AddToPlaylistTarget) {
        setAddToPlaylistTarget(target)
        setAddToPlaylistStatus(null)
        if (playlists().isEmpty()) refreshPlaylists()
    }

    fun openTrackAddToPlaylist(track: Track) {
        openAddToPlaylist(AddToPlaylistTarget.TrackTarget(track))
    }

    fun openAlbumAddToPlaylist(album: Album) {
        openAddToPlaylist(AddToPlaylistTarget.AlbumTarget(album))
    }

    fun openArtistAddToPlaylist(artist: Artist) {
        openAddToPlaylist(AddToPlaylistTarget.ArtistTarget(artist))
    }

    fun openPlaylistAddToPlaylist(playlist: Playlist) {
        openAddToPlaylist(AddToPlaylistTarget.PlaylistTarget(playlist))
    }

    fun addTargetToPlaylist(target: AddToPlaylistTarget, playlist: Playlist?, newPlaylistName: String? = null) {
        val activeProvider = provider() ?: return
        setAddToPlaylistStatus(addToPlaylistResolvingTracksStatus())
        scope.launch {
            try {
                val application = withContext(Dispatchers.IO) {
                    activeProvider.addTracksToPlaylistApplication(
                        playlistId = playlist?.id,
                        playlistName = playlist?.name,
                        newPlaylistName = newPlaylistName,
                        tracks = resolveAddToPlaylistTargetTracks(activeProvider, target),
                        currentHomeContent = homeContent(),
                        recentPlaylistIds = recentPlaylistIds(),
                        projection = PlaylistHomeProjection.RecentLimited,
                        providerResponseService = providerResponseService,
                    )
                }
                if (application.closeDialog) setAddToPlaylistTarget(null)
                setAddToPlaylistStatus(application.addToPlaylistStatus)
                application.connectionStatus?.let(setConnectionStatus)
                application.playlistListApplication?.let(::applyPlaylistListApplication)
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
                applyPlaybackQueueUpdate(
                    update = update,
                    setStatus = setConnectionStatus,
                    replaceQueue = playlistEngine::replaceQueue,
                )
            } catch (exception: Exception) {
                setConnectionStatus(exception.message ?: "Could not add to queue.")
            }
        }
    }

    fun addTrackToQueue(track: Track) {
        addTargetToQueue(AddToPlaylistTarget.TrackTarget(track))
    }

    fun addAlbumToQueue(album: Album) {
        addTargetToQueue(AddToPlaylistTarget.AlbumTarget(album))
    }

    fun addArtistToQueue(artist: Artist) {
        addTargetToQueue(AddToPlaylistTarget.ArtistTarget(artist))
    }

    fun addPlaylistToQueue(playlist: Playlist) {
        addTargetToQueue(AddToPlaylistTarget.PlaylistTarget(playlist))
    }

    fun addSelectedPlaylistToQueue() {
        selectedPlaylist()?.let(::addPlaylistToQueue)
    }

    fun openSelectedPlaylistAddToPlaylist() {
        selectedPlaylist()?.let(::openPlaylistAddToPlaylist)
    }

    fun requestSelectedPlaylistRename() {
        selectedPlaylist()?.let(setPlaylistPendingRename)
    }

    fun requestSelectedPlaylistDelete() {
        selectedPlaylist()?.let(setPlaylistPendingDelete)
    }

    fun playNext(track: Track) {
        val update = PlaybackQueueManager().playNextTracks(
            currentQueue = playlistEngine.queue,
            tracksToAdd = listOf(track),
        )
        applyPlaybackQueueUpdate(
            update = update,
            setStatus = setConnectionStatus,
            replaceQueue = playlistEngine::replaceQueue,
        )
    }

    fun saveQueueAsPlaylist(name: String) {
        val activeProvider = provider() ?: return
        val queueTracks = playlistEngine.queue.tracks
        setConnectionStatus(queuePlaylistSaveLoadingStatus())
        scope.launch {
            try {
                val application = withContext(Dispatchers.IO) {
                    activeProvider.saveQueueAsPlaylistApplication(
                        name = name,
                        tracks = queueTracks,
                        currentHomeContent = homeContent(),
                        recentPlaylistIds = recentPlaylistIds(),
                        projection = PlaylistHomeProjection.RecentLimited,
                        providerResponseService = providerResponseService,
                    )
                }
                setConnectionStatus(application.status)
                applyPlaylistListApplication(application.playlistListApplication)
            } catch (exception: Exception) {
                setConnectionStatus(queuePlaylistSaveErrorMessage(exception))
            }
        }
    }

    fun markPlaylistPlayed(
        playlist: Playlist,
        recentPlaylistIdsAfterPlayback: List<String> = recentPlaylistIdsAfterPlayed(recentPlaylistIds(), playlist.id, limit = 50),
    ) {
        val updatedRecentIds = recentPlaylistIdsAfterPlayback
        setRecentPlaylistIds(updatedRecentIds)
        settingsStore.saveRecentPlaylistIds(updatedRecentIds)
    }

    fun playPlaylist(playlist: Playlist, shuffle: Boolean = false) {
        val activeProvider = provider() ?: return
        val startPlan = playlistPlaybackStartPlan(playlist, shuffle, pendingPlaybackAction())
        val startApplication = playlistPlaybackStartApplication(startPlan)
        if (!startPlan.shouldStart) {
            setConnectionStatus(startApplication.status)
            return
        }
        setPendingPlaybackAction(startApplication.pendingPlaybackAction)
        setConnectionStatus(startApplication.status)
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
                val prepared = playlistPlaybackPreparedApplication(update)
                setPlaylistTracksById(prepared.playlistTracksById)
                val work = prepared.playbackWork
                if (work == null) {
                    setConnectionStatus(prepared.status)
                    return@launch
                }
                setConnectionStatus(prepared.status)
                setPendingPlaybackAction(
                    playlistPlaybackCompletionApplication(
                        pending = pendingPlaybackAction(),
                        completed = startPlan.action,
                    ).pendingPlaybackAction,
                )
                playTracks(
                    playlist = playlist,
                    activeProvider = activeProvider,
                    tracks = work.playbackTracks,
                    index = work.playbackIndex,
                    recentPlaylistIdsAfterPlayback = work.recentPlaylistIds,
                )
            } catch (exception: Exception) {
                setConnectionStatus(playlistPlaybackErrorMessage(exception, playlist))
            } finally {
                setPendingPlaybackAction(
                    playlistPlaybackCompletionApplication(
                        pending = pendingPlaybackAction(),
                        completed = startPlan.action,
                    ).pendingPlaybackAction,
                )
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
        val startApplication = playlistPlaybackStartApplication(startPlan)
        if (!startPlan.shouldStart) {
            return setSelectedPlaylistStatus(startApplication.status)
        }
        setPendingPlaybackAction(startApplication.pendingPlaybackAction)
        setSelectedPlaylistStatus(startApplication.status)
        scope.launch {
            try {
                val update = withContext(Dispatchers.IO) {
                    activeProvider.preparePlaylistDetailPlaybackApplication(
                        playlist = playlist,
                        shuffle = shuffle,
                        selectedPlaylistTracks = selectedPlaylistTracks(),
                        recentPlaylistIds = recentPlaylistIds(),
                        recentPlaylistLimit = 50,
                        currentPlaylistTracksById = playlistTracksById(),
                        providerResponseService = providerResponseService,
                        requestedIndex = index,
                    )
                }
                val prepared = playlistDetailPlaybackPreparedApplication(update)
                setPlaylistTracksById(prepared.playlistTracksById)
                prepared.loadedTracksToStore?.let { loadedTracks ->
                    setSelectedPlaylistTracks(loadedTracks)
                }
                val work = prepared.playbackWork
                if (work == null) {
                    setSelectedPlaylistStatus(prepared.status)
                    return@launch
                }
                playTracks(
                    playlist = playlist,
                    activeProvider = activeProvider,
                    tracks = work.playbackTracks,
                    index = work.playbackIndex,
                    recentPlaylistIdsAfterPlayback = work.recentPlaylistIds,
                )
                setSelectedPlaylistStatus(null)
            } catch (exception: Exception) {
                setSelectedPlaylistStatus(playlistPlaybackErrorMessage(exception, playlist))
            } finally {
                setPendingPlaybackAction(
                    playlistPlaybackCompletionApplication(
                        pending = pendingPlaybackAction(),
                        completed = startPlan.action,
                    ).pendingPlaybackAction,
                )
            }
        }
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
                val application = playlistRenameApplication(
                    update = update,
                    currentHomeContent = homeContent(),
                    recentPlaylistIds = recentPlaylistIds(),
                    projection = PlaylistHomeProjection.RecentLimited,
                )
                applyPlaylistListApplication(application.playlistListApplication)
                setPlaylistPendingRename(null)
                setPlaylistStatus(application.status)
                application.selectionApplication?.let { selection ->
                    setSelectedPlaylist(selection.selectedPlaylist)
                }
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
                val application = playlistDeleteApplication(
                    update = update,
                    currentHomeContent = homeContent(),
                    projection = PlaylistHomeProjection.RecentLimited,
                )
                applyPlaylistListApplication(application.playlistListApplication)
                application.selectionApplication?.let { selection ->
                    setSelectedPlaylist(selection.selectedPlaylist)
                    setSelectedPlaylistTracks(selection.selectedPlaylistTracks)
                    setAppRoute(DesktopAppRoute.Playlists)
                }
                setPlaylistTracksById(application.playlistTracksById)
                setRecentPlaylistIds(application.recentPlaylistIds)
                setPlaylistStatus(application.status)
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
        recentPlaylistIdsAfterPlayback: List<String>? = null,
    ) {
        markPlaylistPlayed(
            playlist = playlist,
            recentPlaylistIdsAfterPlayback = recentPlaylistIdsAfterPlayback
                ?: recentPlaylistIdsAfterPlayed(recentPlaylistIds(), playlist.id, limit = 50),
        )
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

    private fun applyPlaylistListApplication(application: PlaylistListApplication) {
        setPlaylists(application.playlists)
        setHomeContent(application.homeContent)
    }

    private fun applyPlaylistListApplication(refreshedPlaylists: List<Playlist>) {
        val application = playlistListApplication(
            playlists = refreshedPlaylists,
            currentHomeContent = homeContent(),
            recentPlaylistIds = recentPlaylistIds(),
            projection = PlaylistHomeProjection.RecentLimited,
        )
        applyPlaylistListApplication(application)
    }
}
