package app.naviamp.android

import app.naviamp.domain.Playlist
import app.naviamp.domain.Track
import app.naviamp.domain.app.NaviampRoute
import app.naviamp.domain.provider.clearPendingPlaybackAction
import app.naviamp.domain.provider.normalizedPlaylistName
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
import app.naviamp.domain.provider.recentPlaylistIdsAfterPlayed
import app.naviamp.domain.provider.refreshPlaylistDetails
import app.naviamp.domain.provider.renamedSelectedPlaylist
import app.naviamp.domain.provider.saveSmartPlaylistAndRefresh
import app.naviamp.domain.provider.selectedPlaylistTracksForPlayback
import app.naviamp.domain.provider.shouldStartPlaybackAction
import app.naviamp.domain.provider.smartPlaylistSaveErrorMessage
import app.naviamp.domain.provider.smartPlaylistSavedStatus
import app.naviamp.domain.provider.smartPlaylistSavingStatus
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
) {
    with(state) {
        if (showLoadingStatus) status = "Loading ${playlist.name}..."
        val refresh = withContext(Dispatchers.IO) { activeProvider.refreshPlaylistDetails(playlist) }
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
) {
    val activeProvider = state.provider ?: return
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
                    runCatching { activeProvider.playlistTracks(playlist.id) }
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
) {
    val activeProvider = state.provider ?: return
    val requestedName = normalizedPlaylistName(name)
    scope.launch {
        with(state) {
            status = playlistRenameLoadingStatus(playlist)
            runCatching {
                activeProvider.renamePlaylist(playlist.id, requestedName)
                activeProvider.playlists(limit = 500)
            }.onSuccess { playlists ->
                homeState = homeState.copy(playlists = playlists)
                selectedPlaylist?.let { current ->
                    if (current.id == playlist.id) {
                        contentState = contentState.copy(
                            selectedPlaylist = renamedSelectedPlaylist(
                                current = current,
                                playlistId = playlist.id,
                                requestedName = requestedName,
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
) {
    val activeProvider = state.provider ?: return
    scope.launch {
        with(state) {
            status = playlistDeleteLoadingStatus(playlist)
            runCatching {
                activeProvider.deletePlaylist(playlist.id)
                activeProvider.playlists(limit = 500)
            }.onSuccess { playlists ->
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
) {
    scope.launch {
        playlistsNeedingTrackPreload(playlists, state.playlistTracksById).forEach { playlist ->
            runCatching { activeProvider.playlistTracks(playlist.id) }
                .onSuccess { tracks ->
                    state.playlistTracksById = state.playlistTracksById + (playlist.id to tracks)
                }
        }
    }
}

fun refreshAndroidPlaylists(
    scope: CoroutineScope,
    state: AndroidAppState,
) {
    val activeProvider = state.provider ?: return
    scope.launch {
        runCatching { activeProvider.playlists(limit = 500) }
            .onSuccess { playlists ->
                state.homeState = state.homeState.copy(playlists = playlists)
                preloadAndroidPlaylistTracks(scope, state, activeProvider, playlists)
            }
    }
}

suspend fun saveAndroidSmartPlaylist(
    scope: CoroutineScope,
    state: AndroidAppState,
    definition: SmartPlaylistDefinition,
) {
    val activeProvider = state.provider
        ?: throw IllegalStateException("Connect to Navidrome before saving smart playlists.")
    state.status = smartPlaylistSavingStatus(definition)
    try {
        val refresh = saveSmartPlaylistAndRefresh(activeProvider, definition)
        state.playlistTracksById = state.playlistTracksById + (refresh.displayPlaylist.id to refresh.tracks)
        state.homeState = state.homeState.copy(playlists = refresh.playlists)
        preloadAndroidPlaylistTracks(scope, state, activeProvider, refresh.playlists)
        state.status = smartPlaylistSavedStatus(refresh.displayPlaylist, refresh.tracks.size)
    } catch (error: Exception) {
        state.status = smartPlaylistSaveErrorMessage(error)
        throw error
    }
}
