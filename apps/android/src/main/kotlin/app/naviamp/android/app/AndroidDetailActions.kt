package app.naviamp.android

import app.naviamp.domain.ArtistId
import app.naviamp.ui.KeepDownloadedActionValue
import app.naviamp.ui.NaviampAlbumDetailActions
import app.naviamp.ui.NaviampArtistDetailActions
import app.naviamp.ui.NaviampPlaylistDetailActions
import app.naviamp.ui.ResolvedAlbumDetailActionHandlers
import app.naviamp.ui.ResolvedPlaylistDetailActionHandlers
import app.naviamp.ui.SharedMediaItemAction
import app.naviamp.ui.albumDetailActionDispatchStatus
import app.naviamp.ui.dispatchResolvedAlbumDetailAction
import app.naviamp.ui.dispatchResolvedPlaylistDetailAction
import app.naviamp.ui.playlistDetailActionDispatchStatus
import kotlinx.coroutines.CoroutineScope

internal fun androidDetailBackAction(navigationController: AndroidNavigationController): () -> Unit =
    navigationController::closeActiveDetail

internal fun androidAlbumDetailActions(
    scope: CoroutineScope,
    state: AndroidAppState,
    navigationController: AndroidNavigationController,
    mediaController: AndroidMediaAppController,
    shellMediaController: AndroidShellMediaController,
    trackActionController: AndroidTrackActionController,
    playlistActionController: AndroidPlaylistActionController,
    downloadActionController: AndroidDownloadActionController,
): NaviampAlbumDetailActions = NaviampAlbumDetailActions(
    onBack = androidDetailBackAction(navigationController),
    onAlbumAction = { request ->
        val album = state.albumDetail?.album?.takeIf { it.id.value == request.album.id }
        val result = dispatchResolvedAlbumDetailAction(
            request = request,
            album = album,
            handlers = ResolvedAlbumDetailActionHandlers(
                onPlay = { _, shuffle -> shellMediaController.handleShellAlbumPlay(shuffle) },
                onStartRadio = { shellMediaController.handleShellAlbumRadio() },
                onDownload = {
                    downloadActionController.downloadTracks(state.albumDetail?.tracks.orEmpty(), "album")
                },
                onAddToQueue = {
                    mediaController.appendTracksToQueue(state.albumDetail?.tracks.orEmpty(), "album tracks")
                },
                onAddToPlaylist = { _, choice ->
                    playlistActionController.addTracksToPlaylist(
                        state.albumDetail?.tracks.orEmpty(),
                        choice,
                        null,
                        "album",
                    )
                },
                onCreatePlaylistAndAdd = { _, name ->
                    playlistActionController.addTracksToPlaylist(
                        state.albumDetail?.tracks.orEmpty(),
                        null,
                        name,
                        "album",
                    )
                },
                onToggleFavorite = {
                    toggleAndroidAlbumFavorite(
                        scope,
                        state,
                        request.album,
                        state.sharedControllers.providerActions,
                    )
                },
            ),
        )
        albumDetailActionDispatchStatus(result)?.let { state.status = it }
    },
    onTrackAction = trackActionController::handleTrackAction,
)

internal fun androidArtistDetailActions(
    scope: CoroutineScope,
    state: AndroidAppState,
    navigationController: AndroidNavigationController,
    mediaController: AndroidMediaAppController,
    shellMediaController: AndroidShellMediaController,
    artistActionController: AndroidArtistActionController,
    trackActionController: AndroidTrackActionController,
    playlistActionController: AndroidPlaylistActionController,
    downloadActionController: AndroidDownloadActionController,
): NaviampArtistDetailActions = NaviampArtistDetailActions(
    onBack = androidDetailBackAction(navigationController),
    onRadio = artistActionController::handleShellArtistRadio,
    onPlay = artistActionController::handleShellArtistPlay,
    onShuffle = artistActionController::handleShellArtistShuffle,
    onAddToQueue = {
        artistActionController.loadArtistTracks { mediaController.appendTracksToQueue(it, "artist tracks") }
    },
    onAddToPlaylist = { _, playlist ->
        artistActionController.loadArtistTracks {
            playlistActionController.addTracksToPlaylist(it, playlist, null, "artist")
        }
    },
    onCreatePlaylistAndAdd = { _, name ->
        artistActionController.loadArtistTracks {
            playlistActionController.addTracksToPlaylist(it, null, name, "artist")
        }
    },
    onFavoriteToggled = { item ->
        toggleAndroidArtistFavorite(scope, state, item, state.sharedControllers.providerActions)
    },
    onPopularPlay = artistActionController::handleArtistPopularPlay,
    onPopularRadio = artistActionController::handleShellArtistPopularRadio,
    onPopularAddToQueue = artistActionController::handleArtistPopularAddToQueue,
    onPopularTrackSelected = artistActionController::handleArtistPopularTrackSelected,
    onTrackAction = trackActionController::handleTrackAction,
    onFindSimilar = { detail -> artistActionController.findSimilarArtists(ArtistId(detail.artist.id), detail.artist.title) },
    onSimilarArtistSelected = artistActionController::handleSimilarArtistSelected,
    onSimilarArtistExternalSelected = artistActionController::openExternalArtistUrl,
    onAlbumSelected = shellMediaController::handleShellAlbumSelected,
    onAlbumAction = { request ->
        when (request.action) {
            SharedMediaItemAction.Select -> shellMediaController.handleShellAlbumSelected(request.item)
            SharedMediaItemAction.StartRadio -> artistActionController.handleArtistAlbumRadio(request.item)
            SharedMediaItemAction.AddToQueue ->
                artistActionController.loadArtistAlbumTracks(request.item) {
                    mediaController.appendTracksToQueue(it, "album tracks")
                }
            SharedMediaItemAction.Download ->
                artistActionController.loadArtistAlbumTracks(request.item) {
                    downloadActionController.downloadTracks(it, request.item.title)
                }
            SharedMediaItemAction.AddToPlaylist ->
                artistActionController.loadArtistAlbumTracks(request.item) {
                    playlistActionController.addTracksToPlaylist(
                        it,
                        request.playlistChoice,
                        null,
                        request.item.title,
                    )
                }
            SharedMediaItemAction.CreatePlaylistAndAdd ->
                artistActionController.loadArtistAlbumTracks(request.item) {
                    playlistActionController.addTracksToPlaylist(it, null, request.playlistName, request.item.title)
                }
            SharedMediaItemAction.ToggleFavorite ->
                toggleAndroidAlbumFavorite(scope, state, request.item, state.sharedControllers.providerActions)
            else -> Unit
        }
    },
    onAlbumFavoriteToggled = { item ->
        toggleAndroidAlbumFavorite(scope, state, item, state.sharedControllers.providerActions)
    },
)

internal fun androidPlaylistDetailActions(
    state: AndroidAppState,
    mediaController: AndroidMediaAppController,
    navigationController: AndroidNavigationController,
    trackActionController: AndroidTrackActionController,
    playlistActionController: AndroidPlaylistActionController,
    downloadActionController: AndroidDownloadActionController,
): NaviampPlaylistDetailActions = NaviampPlaylistDetailActions(
    onBack = navigationController::closeActivePlaylist,
    onUpdateStandardPlaylist = { playlistItem, trackRows ->
        val playlist = state.homeState.playlists.firstOrNull { it.id == playlistItem.id }
            ?: throw IllegalArgumentException("Playlist not found.")
        val sourceTracks = state.playlistTracksById[playlist.id].orEmpty()
        val editedTracks = trackRows.map { row ->
            sourceTracks.firstOrNull { track -> track.id.value == row.id }
                ?: throw IllegalArgumentException("Track ${row.title} is no longer in the playlist.")
        }
        playlistActionController.updateStandardPlaylistTracks(playlist, editedTracks)
    },
    onPlaylistAction = { request ->
        val playlist = state.homeState.playlists.firstOrNull { it.id == request.playlist.id }
            ?: state.selectedPlaylist?.takeIf { it.id == request.playlist.id }
        val result = dispatchResolvedPlaylistDetailAction(
            request = request,
            playlist = playlist,
            handlers = ResolvedPlaylistDetailActionHandlers(
                onPlay = playlistActionController::playPlaylist,
                onAddToQueue = {
                    mediaController.appendTracksToQueue(state.selectedPlaylistTracks, "playlist tracks")
                },
                onDownload = { source, value ->
                    if (value == KeepDownloadedActionValue) {
                        downloadActionController.toggleKeepDownloadedPlaylist(source)
                    } else {
                        downloadActionController.downloadPlaylist(source)
                    }
                },
                onAddToPlaylist = { source, choice ->
                    playlistActionController.addPlaylistToPlaylist(source, choice, null)
                },
                onCreatePlaylistAndAdd = { source, name ->
                    playlistActionController.addPlaylistToPlaylist(source, null, name)
                },
                onCopy = { _, name, deduplicate ->
                    val tracks = if (deduplicate) {
                        state.selectedPlaylistTracks.distinctBy { it.id }
                    } else {
                        state.selectedPlaylistTracks
                    }
                    playlistActionController.addTracksToPlaylist(tracks, null, name, "playlist")
                },
                onRename = playlistActionController::renamePlaylist,
                onDelete = playlistActionController::deletePlaylist,
            ),
        )
        playlistDetailActionDispatchStatus(result)?.let { status ->
            state.status = status
        }
    },
    onTrackAction = trackActionController::handleTrackAction,
)
