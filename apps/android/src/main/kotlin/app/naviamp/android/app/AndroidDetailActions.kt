package app.naviamp.android

import app.naviamp.domain.ArtistId
import app.naviamp.ui.KeepDownloadedActionValue
import app.naviamp.ui.NaviampAlbumDetailActions
import app.naviamp.ui.NaviampArtistDetailActions
import app.naviamp.ui.NaviampPlaylistDetailActions
import app.naviamp.ui.SharedMediaItemAction
import kotlinx.coroutines.CoroutineScope

internal fun androidAlbumDetailActions(
    scope: CoroutineScope,
    state: AndroidAppState,
    mediaController: AndroidMediaAppController,
    shellMediaController: AndroidShellMediaController,
    trackActionController: AndroidTrackActionController,
    playlistActionController: AndroidPlaylistActionController,
    downloadActionController: AndroidDownloadActionController,
): NaviampAlbumDetailActions = NaviampAlbumDetailActions(
    onBack = { state.nowPlayingOpen = false },
    onPlay = { _, shuffle -> shellMediaController.handleShellAlbumPlay(shuffle) },
    onRadio = { shellMediaController.handleShellAlbumRadio() },
    onDownload = { downloadActionController.downloadTracks(state.albumDetail?.tracks.orEmpty(), "album") },
    onAddToQueue = { mediaController.appendTracksToQueue(state.albumDetail?.tracks.orEmpty(), "album tracks") },
    onAddToPlaylist = { _, playlist ->
        playlistActionController.addTracksToPlaylist(state.albumDetail?.tracks.orEmpty(), playlist, null, "album")
    },
    onCreatePlaylistAndAdd = { _, name ->
        playlistActionController.addTracksToPlaylist(state.albumDetail?.tracks.orEmpty(), null, name, "album")
    },
    onFavoriteToggled = { item ->
        toggleAndroidAlbumFavorite(scope, state, item, state.sharedControllers.providerActions)
    },
    onTrackSelected = shellMediaController::handleShellAlbumTrackSelected,
    onTrackAction = trackActionController::handleTrackAction,
)

internal fun androidArtistDetailActions(
    scope: CoroutineScope,
    state: AndroidAppState,
    mediaController: AndroidMediaAppController,
    shellMediaController: AndroidShellMediaController,
    artistActionController: AndroidArtistActionController,
    trackActionController: AndroidTrackActionController,
    playlistActionController: AndroidPlaylistActionController,
    downloadActionController: AndroidDownloadActionController,
): NaviampArtistDetailActions = NaviampArtistDetailActions(
    onBack = { state.nowPlayingOpen = false },
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
    onPlay = { selectedPlaylist, shuffle ->
        state.homeState.playlists.firstOrNull { it.id == selectedPlaylist.id }
            ?.let { playlistActionController.playPlaylist(it, shuffle) }
            ?: run { state.status = "Playlist not found." }
    },
    onAddToQueue = { mediaController.appendTracksToQueue(state.selectedPlaylistTracks, "playlist tracks") },
    onAddToPlaylist = { _, playlist ->
        state.selectedPlaylist?.let { playlistActionController.addPlaylistToPlaylist(it, playlist, null) }
            ?: run { state.status = "Playlist not found." }
    },
    onCreatePlaylistAndAdd = { _, name ->
        state.selectedPlaylist?.let { playlistActionController.addPlaylistToPlaylist(it, null, name) }
            ?: run { state.status = "Playlist not found." }
    },
    onCopy = { _, name, deduplicate ->
        val tracks = if (deduplicate) state.selectedPlaylistTracks.distinctBy { it.id } else state.selectedPlaylistTracks
        playlistActionController.addTracksToPlaylist(tracks, null, name, "playlist")
    },
    onRename = { selectedPlaylist, name ->
        state.homeState.playlists.firstOrNull { it.id == selectedPlaylist.id }
            ?.let { playlistActionController.renamePlaylist(it, name) }
            ?: run { state.status = "Playlist not found." }
    },
    onDelete = { selectedPlaylist ->
        state.homeState.playlists.firstOrNull { it.id == selectedPlaylist.id }
            ?.let(playlistActionController::deletePlaylist)
            ?: run { state.status = "Playlist not found." }
    },
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
    onMediaItemAction = { request ->
        val playlist = state.homeState.playlists.firstOrNull { it.id == request.item.id }
        if (playlist == null) {
            state.status = "Playlist not found."
        } else if (request.action == SharedMediaItemAction.Download) {
            if (request.textValue == KeepDownloadedActionValue) {
                downloadActionController.toggleKeepDownloadedPlaylist(playlist)
            } else {
                downloadActionController.downloadPlaylist(playlist)
            }
        }
    },
    onTrackSelected = trackActionController::handlePlaylistTrackSelected,
    onTrackAction = trackActionController::handleTrackAction,
)
