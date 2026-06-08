package app.naviamp.android

import app.naviamp.domain.Playlist
import app.naviamp.domain.Track
import app.naviamp.domain.app.NaviampRoute
import app.naviamp.domain.cache.ProviderResponseCacheRepository
import app.naviamp.domain.cache.ProviderResponseService
import app.naviamp.domain.provider.clearPendingPlaybackAction
import app.naviamp.domain.provider.playlistDeleteErrorMessage
import app.naviamp.domain.provider.playlistDeleteLoadingStatus
import app.naviamp.domain.provider.playlistDeleteApplicationUpdate
import app.naviamp.domain.provider.playlistDetailsStateUpdate
import app.naviamp.domain.provider.playlistListRefresh
import app.naviamp.domain.provider.playlistPlaybackStartPlan
import app.naviamp.domain.provider.preparePlaylistPlayback
import app.naviamp.domain.provider.playlistRenameErrorMessage
import app.naviamp.domain.provider.playlistRenameLoadingStatus
import app.naviamp.domain.provider.playlistRenameStateUpdate
import app.naviamp.domain.provider.queuePlaylistSaveErrorMessage
import app.naviamp.domain.provider.queuePlaylistSaveLoadingStatus
import app.naviamp.domain.provider.queuePlaylistSaveStateUpdate
import app.naviamp.domain.provider.recentPlaylistIdsAfterPlayed
import app.naviamp.domain.provider.refreshPlaylistDetails
import app.naviamp.domain.provider.refreshPlaylistsAndPlanPreload
import app.naviamp.domain.provider.renamePlaylistAndRefresh
import app.naviamp.domain.provider.saveQueueAsPlaylistAndRefresh
import app.naviamp.domain.provider.saveSmartPlaylistAndRefresh
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
    val startPlan = playlistPlaybackStartPlan(playlist, shuffle, state.pendingPlaybackAction)
    with(state) {
        if (!startPlan.shouldStart) {
            playlistActionStatus = startPlan.status
            status = startPlan.status
            return
        }
        pendingPlaybackAction = startPlan.action
        playlistActionStatus = startPlan.status
        status = startPlan.status
    }
    scope.launch {
        with(state) {
            try {
                val preparation = runCatching {
                    activeProvider.preparePlaylistPlayback(
                        playlist = playlist,
                        shuffle = shuffle,
                        selectedPlaylist = selectedPlaylist,
                        selectedPlaylistTracks = selectedPlaylistTracks,
                        recentPlaylistIds = recentPlaylistIds,
                        recentPlaylistLimit = 20,
                        providerResponseService = providerResponseService,
                    )
                }.onSuccess {
                    if (it.shouldStoreLoadedTracks) {
                        playlistTracksById = playlistTracksById + (playlist.id to it.loadedTracks)
                        contentState = contentState.showPlaylist(playlist, it.loadedTracks)
                        tracks = it.loadedTracks
                    }
                }.getOrElse { error ->
                    status = error.message ?: "Could not play ${playlist.name}."
                    return@launch
                }
                val readyPlan = preparation.readyPlan
                readyPlan.firstTrack?.let { firstTrack ->
                    recentPlaylistIds = readyPlan.recentPlaylistIds
                    playlistActionStatus = null
                    playTrack(firstTrack, readyPlan.tracks)
                } ?: run {
                    playlistActionStatus = null
                    status = readyPlan.emptyStatus
                }
            } finally {
                pendingPlaybackAction = clearPendingPlaybackAction(pendingPlaybackAction, startPlan.action)
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
                val update = playlistRenameStateUpdate(selectedPlaylist, refresh, playlist.id)
                homeState = homeState.copy(playlists = update.playlists)
                if (update.selectedPlaylistChanged) {
                    contentState = contentState.copy(selectedPlaylist = update.selectedPlaylist)
                }
                status = update.status
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
                val update = playlistDeleteApplicationUpdate(
                    refresh = refresh,
                    currentSelectedPlaylist = selectedPlaylist,
                    currentSelectedPlaylistTracks = selectedPlaylistTracks,
                    currentPlaylistTracksById = playlistTracksById,
                    currentRecentPlaylistIds = recentPlaylistIds,
                    deletedPlaylistId = playlist.id,
                )
                homeState = homeState.copy(playlists = update.playlists)
                if (update.deletedSelectedPlaylist) {
                    contentState = contentState.copy(
                        selectedPlaylist = update.selectedPlaylist,
                        selectedPlaylistTracks = update.selectedPlaylistTracks,
                    )
                }
                playlistTracksById = update.playlistTracksById
                recentPlaylistIds = update.recentPlaylistIds
                status = update.status
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
    state.playlistActionStatus = queuePlaylistSaveLoadingStatus()
    scope.launch {
        with(state) {
            runCatching {
                activeProvider.saveQueueAsPlaylistAndRefresh(
                    name = name,
                    tracks = queueTracks,
                    providerResponseService = providerResponseService,
                )
            }.onSuccess { refresh ->
                val update = queuePlaylistSaveStateUpdate(refresh)
                homeState = homeState.copy(playlists = update.playlists)
                playlistActionStatus = null
                status = update.status
            }.onFailure { error ->
                playlistActionStatus = queuePlaylistSaveErrorMessage(error)
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
