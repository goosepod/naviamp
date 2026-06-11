package app.naviamp.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
    initialRecentPlaylistIds: List<String>,
    private val homeContent: () -> HomeContent,
    private val setHomeContent: (HomeContent) -> Unit,
    private val setConnectionStatus: (String?) -> Unit,
    private val setAppRoute: (DesktopAppRoute) -> Unit,
    private val stopRadioContinuation: () -> Unit,
    private val clearShuffleSnapshot: () -> Unit,
    private val setOpenPlayerOnTrackStart: (Boolean) -> Unit,
) {
    var playlists by mutableStateOf<List<Playlist>>(emptyList())
        private set
    var status by mutableStateOf<String?>(null)
        private set
    var pendingPlaybackAction by mutableStateOf<PendingPlaybackAction?>(null)
        private set
    var sortMode by mutableStateOf(DesktopPlaylistSortMode.Alphabetical)
        private set
    var recentPlaylistIds by mutableStateOf(initialRecentPlaylistIds)
        private set
    var selectedPlaylist by mutableStateOf<Playlist?>(null)
        private set
    var selectedPlaylistTracks by mutableStateOf<List<Track>>(emptyList())
        private set
    var playlistTracksById by mutableStateOf<Map<String, List<Track>>>(emptyMap())
        private set
    var selectedPlaylistStatus by mutableStateOf<String?>(null)
        private set
    var pendingRename by mutableStateOf<Playlist?>(null)
        private set
    var pendingDelete by mutableStateOf<Playlist?>(null)
        private set
    var addToPlaylistTarget by mutableStateOf<AddToPlaylistTarget?>(null)
        private set
    var addToPlaylistStatus by mutableStateOf<String?>(null)
        private set

    fun updateSortMode(mode: DesktopPlaylistSortMode) {
        sortMode = mode
    }

    fun dismissAddToPlaylist() {
        addToPlaylistTarget = null
        addToPlaylistStatus = null
    }

    fun dismissRename() {
        pendingRename = null
    }

    fun dismissDelete() {
        pendingDelete = null
    }

    fun updateStatus(status: String?) {
        this.status = status
    }

    fun updatePlaylistTracksById(tracksById: Map<String, List<Track>>) {
        playlistTracksById = tracksById
    }

    fun applySelectedPlaylistDetails(playlist: Playlist?, tracks: List<Track>, status: String?) {
        selectedPlaylist = playlist
        selectedPlaylistTracks = tracks
        selectedPlaylistStatus = status
    }

    fun applyRefreshedPlaylists(refreshedPlaylists: List<Playlist>) {
        applyPlaylistListApplication(refreshedPlaylists)
    }

    fun refreshPlaylists(useCache: Boolean = true) {
        val activeProvider = provider() ?: return
        status = playlistListLoadingStatus()
        scope.launch {
            try {
                val update = withContext(Dispatchers.IO) {
                    activeProvider.refreshPlaylistListState(
                        providerResponseService = providerResponseService,
                        useCache = useCache,
                        playlistTracksById = playlistTracksById,
                    )
                }
                applyPlaylistListApplication(update.playlists)
                status = update.status
                val preloadUpdate = withContext(Dispatchers.IO) {
                    activeProvider.preloadPlaylistTracksStateUpdate(
                        playlists = update.playlistsToPreload,
                        currentPlaylistTracksById = playlistTracksById,
                        providerResponseService = providerResponseService,
                        useCache = useCache,
                    )
                }
                playlistTracksById = preloadUpdate.playlistTracksById
            } catch (exception: Exception) {
                status = playlistListErrorMessage(exception)
            }
        }
    }

    suspend fun refreshPlaylistDetailsFromServer(
        activeProvider: MediaProvider,
        playlist: Playlist,
        showLoadingStatus: Boolean,
    ) {
        if (showLoadingStatus) selectedPlaylistStatus = playlistDetailsLoadingStatus(playlist)
        val update = withContext(Dispatchers.IO) {
            activeProvider.refreshPlaylistDetailsApplication(
                playlist = playlist,
                currentSelectedPlaylist = selectedPlaylist,
                currentSelectedPlaylistTracks = selectedPlaylistTracks,
                currentPlaylistTracksById = playlistTracksById,
                providerResponseService = providerResponseService,
            )
        }
        applyPlaylistListApplication(update.playlists)
        playlistTracksById = update.playlistTracksById
        update.selectionApplication?.let { selection ->
            selectedPlaylist = selection.playlist
            selectedPlaylistTracks = selection.tracks
            selectedPlaylistStatus = selection.status
        }
    }

    fun openAddToPlaylist(target: AddToPlaylistTarget) {
        addToPlaylistTarget = target
        addToPlaylistStatus = null
        if (playlists.isEmpty()) refreshPlaylists()
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
        addToPlaylistStatus = addToPlaylistResolvingTracksStatus()
        scope.launch {
            try {
                val application = withContext(Dispatchers.IO) {
                    activeProvider.addTracksToPlaylistApplication(
                        playlistId = playlist?.id,
                        playlistName = playlist?.name,
                        newPlaylistName = newPlaylistName,
                        tracks = resolveAddToPlaylistTargetTracks(activeProvider, target),
                        currentHomeContent = homeContent(),
                        recentPlaylistIds = recentPlaylistIds,
                        projection = PlaylistHomeProjection.RecentLimited,
                        providerResponseService = providerResponseService,
                    )
                }
                if (application.closeDialog) addToPlaylistTarget = null
                addToPlaylistStatus = application.addToPlaylistStatus
                application.connectionStatus?.let(setConnectionStatus)
                application.playlistListApplication?.let(::applyPlaylistListApplication)
            } catch (exception: Exception) {
                addToPlaylistStatus = addToPlaylistErrorMessage(exception, "tracks")
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
        selectedPlaylist?.let(::addPlaylistToQueue)
    }

    fun openSelectedPlaylistAddToPlaylist() {
        selectedPlaylist?.let(::openPlaylistAddToPlaylist)
    }

    fun requestSelectedPlaylistRename() {
        selectedPlaylist?.let { playlist -> pendingRename = playlist }
    }

    fun requestSelectedPlaylistDelete() {
        selectedPlaylist?.let { playlist -> pendingDelete = playlist }
    }

    fun requestPlaylistRename(playlist: Playlist) {
        pendingRename = playlist
    }

    fun requestPlaylistDelete(playlist: Playlist) {
        pendingDelete = playlist
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
                        recentPlaylistIds = recentPlaylistIds,
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
        recentPlaylistIdsAfterPlayback: List<String> = recentPlaylistIdsAfterPlayed(recentPlaylistIds, playlist.id, limit = 50),
    ) {
        val updatedRecentIds = recentPlaylistIdsAfterPlayback
        recentPlaylistIds = updatedRecentIds
        settingsStore.saveRecentPlaylistIds(updatedRecentIds)
    }

    fun playPlaylist(playlist: Playlist, shuffle: Boolean = false) {
        val activeProvider = provider() ?: return
        val startPlan = playlistPlaybackStartPlan(playlist, shuffle, pendingPlaybackAction)
        val startApplication = playlistPlaybackStartApplication(startPlan)
        if (!startPlan.shouldStart) {
            setConnectionStatus(startApplication.status)
            return
        }
        pendingPlaybackAction = startApplication.pendingPlaybackAction
        setConnectionStatus(startApplication.status)
        scope.launch {
            try {
                val update = withContext(Dispatchers.IO) {
                    activeProvider.preparePlaylistPlaybackApplication(
                        playlist = playlist,
                        shuffle = shuffle,
                        selectedPlaylist = null,
                        selectedPlaylistTracks = emptyList(),
                        recentPlaylistIds = recentPlaylistIds,
                        recentPlaylistLimit = 50,
                        currentPlaylistTracksById = playlistTracksById,
                        providerResponseService = providerResponseService,
                        emptyStatus = "${playlist.name} did not return any tracks.",
                    )
                }
                val prepared = playlistPlaybackPreparedApplication(update)
                playlistTracksById = prepared.playlistTracksById
                val work = prepared.playbackWork
                if (work == null) {
                    setConnectionStatus(prepared.status)
                    return@launch
                }
                setConnectionStatus(prepared.status)
                pendingPlaybackAction =
                    playlistPlaybackCompletionApplication(
                        pending = pendingPlaybackAction,
                        completed = startPlan.action,
                    ).pendingPlaybackAction
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
                pendingPlaybackAction =
                    playlistPlaybackCompletionApplication(
                        pending = pendingPlaybackAction,
                        completed = startPlan.action,
                    ).pendingPlaybackAction
            }
        }
    }

    fun openPlaylistDetails(playlist: Playlist) {
        val activeProvider = provider() ?: return
        selectedPlaylist = playlist
        selectedPlaylistTracks = emptyList()
        selectedPlaylistStatus = playlistDetailsLoadingStatus(playlist)
        setAppRoute(DesktopAppRoute.PlaylistDetail)
        scope.launch {
            try {
                refreshPlaylistDetailsFromServer(activeProvider, playlist, showLoadingStatus = false)
            } catch (exception: Exception) {
                selectedPlaylistStatus = playlistDetailsErrorMessage(exception)
            }
        }
    }

    fun playPlaylistDetails(index: Int = 0, shuffle: Boolean = false) {
        val activeProvider = provider() ?: return
        val playlist = selectedPlaylist ?: return
        val startPlan = playlistPlaybackStartPlan(playlist, shuffle, pendingPlaybackAction)
        val startApplication = playlistPlaybackStartApplication(startPlan)
        if (!startPlan.shouldStart) {
            selectedPlaylistStatus = startApplication.status
            return
        }
        pendingPlaybackAction = startApplication.pendingPlaybackAction
        selectedPlaylistStatus = startApplication.status
        scope.launch {
            try {
                val update = withContext(Dispatchers.IO) {
                    activeProvider.preparePlaylistDetailPlaybackApplication(
                        playlist = playlist,
                        shuffle = shuffle,
                        selectedPlaylistTracks = selectedPlaylistTracks,
                        recentPlaylistIds = recentPlaylistIds,
                        recentPlaylistLimit = 50,
                        currentPlaylistTracksById = playlistTracksById,
                        providerResponseService = providerResponseService,
                        requestedIndex = index,
                    )
                }
                val prepared = playlistDetailPlaybackPreparedApplication(update)
                playlistTracksById = prepared.playlistTracksById
                prepared.loadedTracksToStore?.let { loadedTracks ->
                    selectedPlaylistTracks = loadedTracks
                }
                val work = prepared.playbackWork
                if (work == null) {
                    selectedPlaylistStatus = prepared.status
                    return@launch
                }
                playTracks(
                    playlist = playlist,
                    activeProvider = activeProvider,
                    tracks = work.playbackTracks,
                    index = work.playbackIndex,
                    recentPlaylistIdsAfterPlayback = work.recentPlaylistIds,
                )
                selectedPlaylistStatus = null
            } catch (exception: Exception) {
                selectedPlaylistStatus = playlistPlaybackErrorMessage(exception, playlist)
            } finally {
                pendingPlaybackAction =
                    playlistPlaybackCompletionApplication(
                        pending = pendingPlaybackAction,
                        completed = startPlan.action,
                    ).pendingPlaybackAction
            }
        }
    }

    fun renamePlaylist(playlist: Playlist, name: String) {
        val activeProvider = provider() ?: return
        status = playlistRenameLoadingStatus(playlist)
        scope.launch {
            try {
                val refresh = withContext(Dispatchers.IO) {
                    activeProvider.renamePlaylistAndRefresh(
                        playlist = playlist,
                        name = name,
                        providerResponseService = providerResponseService,
                    )
                }
                val update = playlistRenameStateUpdate(selectedPlaylist, refresh, playlist.id)
                val application = playlistRenameApplication(
                    update = update,
                    currentHomeContent = homeContent(),
                    recentPlaylistIds = recentPlaylistIds,
                    projection = PlaylistHomeProjection.RecentLimited,
                )
                applyPlaylistListApplication(application.playlistListApplication)
                pendingRename = null
                status = application.status
                application.selectionApplication?.let { selection ->
                    selectedPlaylist = selection.selectedPlaylist
                }
            } catch (exception: Exception) {
                status = playlistRenameErrorMessage(exception)
            }
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        val activeProvider = provider() ?: return
        status = playlistDeleteLoadingStatus(playlist)
        scope.launch {
            try {
                val refresh = withContext(Dispatchers.IO) {
                    activeProvider.deletePlaylistAndRefresh(
                        playlist = playlist,
                        providerResponseService = providerResponseService,
                    )
                }
                pendingDelete = null
                val update = playlistDeleteApplicationUpdate(
                    refresh = refresh,
                    currentSelectedPlaylist = selectedPlaylist,
                    currentSelectedPlaylistTracks = selectedPlaylistTracks,
                    currentPlaylistTracksById = playlistTracksById,
                    currentRecentPlaylistIds = recentPlaylistIds,
                    deletedPlaylistId = playlist.id,
                )
                val application = playlistDeleteApplication(
                    update = update,
                    currentHomeContent = homeContent(),
                    projection = PlaylistHomeProjection.RecentLimited,
                )
                applyPlaylistListApplication(application.playlistListApplication)
                application.selectionApplication?.let { selection ->
                    selectedPlaylist = selection.selectedPlaylist
                    selectedPlaylistTracks = selection.selectedPlaylistTracks
                    setAppRoute(DesktopAppRoute.Playlists)
                }
                playlistTracksById = application.playlistTracksById
                recentPlaylistIds = application.recentPlaylistIds
                status = application.status
            } catch (exception: Exception) {
                status = playlistDeleteErrorMessage(exception)
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
                ?: recentPlaylistIdsAfterPlayed(recentPlaylistIds, playlist.id, limit = 50),
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
        playlists = application.playlists
        setHomeContent(application.homeContent)
    }

    private fun applyPlaylistListApplication(refreshedPlaylists: List<Playlist>) {
        val application = playlistListApplication(
            playlists = refreshedPlaylists,
            currentHomeContent = homeContent(),
            recentPlaylistIds = recentPlaylistIds,
            projection = PlaylistHomeProjection.RecentLimited,
        )
        applyPlaylistListApplication(application)
    }
}
