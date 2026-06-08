package app.naviamp.android

import app.naviamp.domain.Playlist
import app.naviamp.domain.Track
import app.naviamp.domain.app.NaviampRoute
import app.naviamp.domain.cache.ProviderResponseCacheRepository
import app.naviamp.domain.cache.ProviderResponseService
import app.naviamp.domain.provider.clearPendingPlaybackAction
import app.naviamp.domain.provider.playlistDeleteErrorMessage
import app.naviamp.domain.provider.playlistDeleteLoadingStatus
import app.naviamp.domain.provider.playlistDeleteStateUpdate
import app.naviamp.domain.provider.playlistDeletedStatus
import app.naviamp.domain.provider.playlistDetailsStateUpdate
import app.naviamp.domain.provider.playlistPlaybackAction
import app.naviamp.domain.provider.playlistPlaybackTracks
import app.naviamp.domain.provider.playlistListRefresh
import app.naviamp.domain.provider.playlistRenameErrorMessage
import app.naviamp.domain.provider.playlistRenameLoadingStatus
import app.naviamp.domain.provider.playlistRenamedStatus
import app.naviamp.domain.provider.recentPlaylistIdsAfterPlayed
import app.naviamp.domain.provider.refreshPlaylistDetails
import app.naviamp.domain.provider.refreshPlaylistsAndPlanPreload
import app.naviamp.domain.provider.renamedSelectedPlaylist
import app.naviamp.domain.provider.renamePlaylistAndRefresh
import app.naviamp.domain.provider.saveQueueAsPlaylistAndRefresh
import app.naviamp.domain.provider.saveSmartPlaylistAndRefresh
import app.naviamp.domain.provider.selectedPlaylistTracksForPlayback
import app.naviamp.domain.provider.shouldStartPlaybackAction
import app.naviamp.domain.provider.smartPlaylistSaveErrorMessage
import app.naviamp.domain.provider.smartPlaylistSavedStatus
import app.naviamp.domain.provider.smartPlaylistSavingStatus
import app.naviamp.domain.provider.smartPlaylistUpdateErrorMessage
import app.naviamp.domain.provider.smartPlaylistUpdatedStatus
import app.naviamp.domain.provider.smartPlaylistUpdatingStatus
import app.naviamp.domain.provider.deletePlaylistAndRefresh
import app.naviamp.domain.provider.loadPlaylistTracksForPreload
import app.naviamp.domain.provider.updateSmartPlaylistAndRefresh
import app.naviamp.domain.smartplaylist.SmartPlaylistDefinition
import app.naviamp.provider.navidrome.NavidromeProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

suspend fun refreshAndroidPlaylistDetailsFromServer(
    state: AndroidAppState,
    activeProvider: NavidromeProvider,
    playlist: Playlist,
    showLoadingStatus: Boolean,
    providerResponseCacheRepository: ProviderResponseCacheRepository? = null,
) {
    with(state) {
        if (showLoadingStatus) status = "Loading ${playlist.name}..."
        val providerResponseService = providerResponseCacheRepository?.let { ProviderResponseService(it) }
        val refresh = withContext(Dispatchers.IO) {
            activeProvider.refreshPlaylistDetails(
                playlist = playlist,
                providerResponseService = providerResponseService,
            )
        }
        val update = playlistDetailsStateUpdate(
            currentSelectedPlaylist = selectedPlaylist,
            currentSelectedPlaylistTracks = selectedPlaylistTracks,
            currentPlaylistTracksById = playlistTracksById,
            refresh = refresh,
            requestedPlaylistId = playlist.id,
        )
        homeState = homeState.copy(playlists = update.playlists)
        playlistTracksById = update.playlistTracksById
        if (selectedPlaylist?.id == playlist.id) {
            contentState = contentState.showPlaylist(
                playlist = requireNotNull(update.selectedPlaylist),
                tracks = update.selectedPlaylistTracks,
            )
            tracks = update.selectedPlaylistTracks
            if (showLoadingStatus) status = "Connected."
        }
    }
}

fun openAndroidPlaylistDetails(
    scope: CoroutineScope,
    state: AndroidAppState,
    playlist: Playlist,
    providerResponseCacheRepository: ProviderResponseCacheRepository? = null,
) {
    val activeProvider = state.provider ?: return
    with(state) {
        contentState = contentState.showPlaylist(playlist)
        navigationState = navigationState.copy(route = NaviampRoute.Playlists)
        nowPlayingOpen = false
        recentPlaylistIds = recentPlaylistIdsAfterPlayed(recentPlaylistIds, playlist.id, limit = 20)
    }
    scope.launch {
        with(state) {
            status = "Loading ${playlist.name}..."
            runCatching {
                refreshAndroidPlaylistDetailsFromServer(
                    state = state,
                    activeProvider = activeProvider,
                    playlist = playlist,
                    showLoadingStatus = false,
                    providerResponseCacheRepository = providerResponseCacheRepository,
                )
            }.onSuccess {
                status = "Connected."
            }.onFailure { error ->
                contentState = contentState.showPlaylist(playlist)
                status = error.message ?: "Playlist failed to load."
            }
        }
    }
}

fun playAndroidPlaylist(
    scope: CoroutineScope,
    state: AndroidAppState,
    playlist: Playlist,
    shuffle: Boolean,
    playTrack: (Track, List<Track>) -> Unit,
    providerResponseCacheRepository: ProviderResponseCacheRepository? = null,
) {
    val activeProvider = state.provider ?: return
    val providerResponseService = providerResponseCacheRepository?.let { ProviderResponseService(it) }
    val playbackAction = playlistPlaybackAction(playlist, shuffle)
    with(state) {
        if (!shouldStartPlaybackAction(pendingPlaybackAction)) {
            playlistActionStatus = pendingPlaybackAction?.status
            status = pendingPlaybackAction?.status ?: status
            return
        }
        pendingPlaybackAction = playbackAction
        playlistActionStatus = playbackAction.status
        status = playbackAction.status
    }
    scope.launch {
        with(state) {
            try {
                val loadedTracks = if (selectedPlaylist?.id == playlist.id && selectedPlaylistTracks.isNotEmpty()) {
                    emptyList()
                } else {
                    runCatching {
                        providerResponseService?.playlistTracks(activeProvider, playlist.id)
                            ?: activeProvider.playlistTracks(playlist.id)
                    }
                        .onSuccess {
                            playlistTracksById = playlistTracksById + (playlist.id to it)
                            contentState = contentState.showPlaylist(playlist, it)
                            tracks = it
                        }
                        .getOrElse { error ->
                            status = error.message ?: "Could not play ${playlist.name}."
                            return@launch
                        }
                }
                val playlistTracks = selectedPlaylistTracksForPlayback(
                    selectedPlaylist = selectedPlaylist,
                    selectedPlaylistTracks = selectedPlaylistTracks,
                    playlist = playlist,
                    loadedTracks = loadedTracks,
                )
                val queue = playlistPlaybackTracks(playlistTracks, shuffle)
                queue.firstOrNull()?.let { firstTrack ->
                    recentPlaylistIds = recentPlaylistIdsAfterPlayed(recentPlaylistIds, playlist.id, limit = 20)
                    playlistActionStatus = null
                    playTrack(firstTrack, queue)
                } ?: run {
                    playlistActionStatus = null
                    status = "Playlist is empty."
                }
            } finally {
                pendingPlaybackAction = clearPendingPlaybackAction(pendingPlaybackAction, playbackAction)
            }
        }
    }
}

fun renameAndroidPlaylist(
    scope: CoroutineScope,
    state: AndroidAppState,
    playlist: Playlist,
    name: String,
    providerResponseCacheRepository: ProviderResponseCacheRepository? = null,
) {
    val activeProvider = state.provider ?: return
    val providerResponseService = providerResponseCacheRepository?.let { ProviderResponseService(it) }
    scope.launch {
        with(state) {
            status = playlistRenameLoadingStatus(playlist)
            runCatching {
                activeProvider.renamePlaylistAndRefresh(
                    playlist = playlist,
                    name = name,
                    providerResponseService = providerResponseService,
                )
            }.onSuccess { refresh ->
                val playlists = refresh.playlists
                homeState = homeState.copy(playlists = playlists)
                selectedPlaylist?.let { current ->
                    if (current.id == playlist.id) {
                        contentState = contentState.copy(
                            selectedPlaylist = renamedSelectedPlaylist(
                                current = current,
                                playlistId = playlist.id,
                                requestedName = refresh.requestedName,
                                refreshedPlaylists = playlists,
                            ),
                        )
                    }
                }
                status = playlistRenamedStatus()
            }.onFailure { error ->
                status = playlistRenameErrorMessage(error)
            }
        }
    }
}

fun deleteAndroidPlaylist(
    scope: CoroutineScope,
    state: AndroidAppState,
    playlist: Playlist,
    providerResponseCacheRepository: ProviderResponseCacheRepository? = null,
) {
    val activeProvider = state.provider ?: return
    val providerResponseService = providerResponseCacheRepository?.let { ProviderResponseService(it) }
    scope.launch {
        with(state) {
            status = playlistDeleteLoadingStatus(playlist)
            runCatching {
                activeProvider.deletePlaylistAndRefresh(
                    playlist = playlist,
                    providerResponseService = providerResponseService,
                )
            }.onSuccess { refresh ->
                val playlists = refresh.playlists
                homeState = homeState.copy(playlists = playlists)
                val update = playlistDeleteStateUpdate(
                    currentSelectedPlaylist = selectedPlaylist,
                    currentSelectedPlaylistTracks = selectedPlaylistTracks,
                    currentPlaylistTracksById = playlistTracksById,
                    currentRecentPlaylistIds = recentPlaylistIds,
                    deletedPlaylistId = playlist.id,
                )
                if (update.deletedSelectedPlaylist) {
                    contentState = contentState.copy(
                        selectedPlaylist = update.selectedPlaylist,
                        selectedPlaylistTracks = update.selectedPlaylistTracks,
                    )
                }
                playlistTracksById = update.playlistTracksById
                recentPlaylistIds = update.recentPlaylistIds
                status = playlistDeletedStatus()
            }.onFailure { error ->
                status = playlistDeleteErrorMessage(error)
            }
        }
    }
}

fun preloadAndroidPlaylistTracks(
    scope: CoroutineScope,
    state: AndroidAppState,
    activeProvider: NavidromeProvider,
    playlists: List<Playlist>,
    providerResponseCacheRepository: ProviderResponseCacheRepository? = null,
) {
    val providerResponseService = providerResponseCacheRepository?.let { ProviderResponseService(it) }
    scope.launch {
        playlistListRefresh(
            playlists = playlists,
            playlistTracksById = state.playlistTracksById,
        )
            .playlistsToPreload
            .forEach { playlist ->
                runCatching {
                    activeProvider.loadPlaylistTracksForPreload(
                        playlist = playlist,
                        providerResponseService = providerResponseService,
                    )
                }
                    .onSuccess { tracks ->
                        state.playlistTracksById = state.playlistTracksById + (playlist.id to tracks)
                    }
                }
    }
}

fun refreshAndroidPlaylists(
    scope: CoroutineScope,
    state: AndroidAppState,
    providerResponseCacheRepository: ProviderResponseCacheRepository? = null,
) {
    val activeProvider = state.provider ?: return
    val providerResponseService = providerResponseCacheRepository?.let { ProviderResponseService(it) }
    scope.launch {
        runCatching {
            activeProvider.refreshPlaylistsAndPlanPreload(
                providerResponseService = providerResponseService,
                playlistTracksById = state.playlistTracksById,
            )
        }.onSuccess { refresh ->
                state.homeState = state.homeState.copy(playlists = refresh.playlists)
                preloadAndroidPlaylistTracks(
                    scope,
                    state,
                    activeProvider,
                    refresh.playlists,
                    providerResponseCacheRepository,
                )
            }
    }
}

fun saveQueueAsPlaylistFromState(
    scope: CoroutineScope,
    state: AndroidAppState,
    name: String,
    providerResponseCacheRepository: ProviderResponseCacheRepository? = null,
) {
    val activeProvider = state.provider ?: return
    val providerResponseService = providerResponseCacheRepository?.let { ProviderResponseService(it) }
    val queueTracks = state.playbackQueue.tracks
    state.playlistActionStatus = "Saving queue as playlist..."
    scope.launch {
        with(state) {
            runCatching {
                activeProvider.saveQueueAsPlaylistAndRefresh(
                    name = name,
                    tracks = queueTracks,
                    providerResponseService = providerResponseService,
                )
            }.onSuccess { refresh ->
                homeState = homeState.copy(playlists = refresh.playlists)
                playlistActionStatus = null
                val result = refresh.result
                status = "Saved ${result.playlist.name} with ${result.trackCount} tracks."
            }.onFailure { error ->
                playlistActionStatus = error.message ?: "Could not save queue as playlist."
                status = playlistActionStatus.orEmpty()
            }
        }
    }
}

suspend fun saveAndroidSmartPlaylist(
    scope: CoroutineScope,
    state: AndroidAppState,
    definition: SmartPlaylistDefinition,
    providerResponseCacheRepository: ProviderResponseCacheRepository? = null,
) {
    val activeProvider = state.provider
        ?: throw IllegalStateException("Connect to Navidrome before saving smart playlists.")
    state.status = smartPlaylistSavingStatus(definition)
    try {
        val refresh = saveSmartPlaylistAndRefresh(activeProvider, definition)
        providerResponseCacheRepository
            ?.let { ProviderResponseService(it) }
            ?.invalidatePlaylistResponses(activeProvider, refresh.displayPlaylist.id)
        state.playlistTracksById = state.playlistTracksById + (refresh.displayPlaylist.id to refresh.tracks)
        state.homeState = state.homeState.copy(playlists = refresh.playlists)
        preloadAndroidPlaylistTracks(scope, state, activeProvider, refresh.playlists, providerResponseCacheRepository = null)
        state.status = smartPlaylistSavedStatus(refresh.displayPlaylist, refresh.tracks.size)
    } catch (error: Exception) {
        state.status = smartPlaylistSaveErrorMessage(error)
        throw error
    }
}

suspend fun updateAndroidSmartPlaylist(
    scope: CoroutineScope,
    state: AndroidAppState,
    playlist: Playlist,
    definition: SmartPlaylistDefinition,
    providerResponseCacheRepository: ProviderResponseCacheRepository? = null,
) {
    val activeProvider = state.provider
        ?: throw IllegalStateException("Connect to Navidrome before updating smart playlists.")
    state.status = smartPlaylistUpdatingStatus(definition)
    try {
        val refresh = updateSmartPlaylistAndRefresh(activeProvider, playlist, definition)
        providerResponseCacheRepository
            ?.let { ProviderResponseService(it) }
            ?.invalidatePlaylistResponses(activeProvider, refresh.displayPlaylist.id)
        state.playlistTracksById = state.playlistTracksById + (refresh.displayPlaylist.id to refresh.tracks)
        state.homeState = state.homeState.copy(playlists = refresh.playlists)
        if (state.selectedPlaylist?.id == refresh.displayPlaylist.id) {
            state.contentState = state.contentState.showPlaylist(refresh.displayPlaylist, refresh.tracks)
            state.tracks = refresh.tracks
        }
        preloadAndroidPlaylistTracks(scope, state, activeProvider, refresh.playlists, providerResponseCacheRepository = null)
        state.status = smartPlaylistUpdatedStatus(refresh.displayPlaylist, refresh.tracks.size)
    } catch (error: Exception) {
        state.status = smartPlaylistUpdateErrorMessage(error)
        throw error
    }
}

suspend fun loadAndroidSmartPlaylistDefinition(
    state: AndroidAppState,
    playlist: Playlist,
): SmartPlaylistDefinition {
    val activeProvider = state.provider
        ?: throw IllegalStateException("Connect to Navidrome before editing smart playlists.")
    return withContext(Dispatchers.IO) {
        activeProvider.smartPlaylistDefinition(playlist.id)
    }
}
