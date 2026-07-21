package app.naviamp.android

import app.naviamp.domain.ArtistId
import app.naviamp.ui.KeepDownloadedActionValue
import app.naviamp.ui.NaviampAlbumDetailActions
import app.naviamp.ui.NaviampArtistDetailActions
import app.naviamp.ui.NaviampPlaylistDetailActions
import app.naviamp.ui.ResolvedArtistAlbumActionHandlers
import app.naviamp.ui.ResolvedArtistDetailActionHandlers
import app.naviamp.ui.ResolvedAlbumDetailActionHandlers
import app.naviamp.ui.ResolvedPlaylistDetailActionHandlers
import app.naviamp.ui.SharedArtistDetailUi
import app.naviamp.ui.albumDetailActionDispatchStatus
import app.naviamp.ui.artistAlbumActionDispatchStatus
import app.naviamp.ui.artistDetailActionDispatchStatus
import app.naviamp.ui.dispatchResolvedAlbumDetailAction
import app.naviamp.ui.dispatchResolvedArtistAlbumAction
import app.naviamp.ui.dispatchResolvedArtistDetailAction
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
    onArtistAction = { request ->
        val artist = state.artistDetail?.artist?.takeIf { it.id.value == request.artist.id }
        val result = dispatchResolvedArtistDetailAction(
            request = request,
            artist = artist,
            handlers = ResolvedArtistDetailActionHandlers(
                onPlayCatalog = { _, albums, shuffle ->
                    val detail = SharedArtistDetailUi(request.artist, albums)
                    if (shuffle) {
                        artistActionController.handleShellArtistShuffle(detail)
                    } else {
                        artistActionController.handleShellArtistPlay(detail)
                    }
                },
                onStartRadio = {
                    artistActionController.handleShellArtistRadio(SharedArtistDetailUi(request.artist, emptyList()))
                },
                onAddToQueue = {
                    artistActionController.loadArtistTracks {
                        mediaController.appendTracksToQueue(it, "artist tracks")
                    }
                },
                onAddToPlaylist = { _, choice ->
                    artistActionController.loadArtistTracks {
                        playlistActionController.addTracksToPlaylist(it, choice, null, "artist")
                    }
                },
                onCreatePlaylistAndAdd = { _, name ->
                    artistActionController.loadArtistTracks {
                        playlistActionController.addTracksToPlaylist(it, null, name, "artist")
                    }
                },
                onToggleFavorite = {
                    toggleAndroidArtistFavorite(
                        scope,
                        state,
                        request.artist,
                        state.sharedControllers.providerActions,
                    )
                },
                onPlayPopular = {
                    artistActionController.handleArtistPopularPlay(
                        SharedArtistDetailUi(request.artist, emptyList()),
                    )
                },
                onStartPopularRadio = {
                    artistActionController.handleShellArtistPopularRadio(
                        SharedArtistDetailUi(request.artist, emptyList()),
                    )
                },
                onAddPopularToQueue = {
                    artistActionController.handleArtistPopularAddToQueue(
                        SharedArtistDetailUi(request.artist, emptyList()),
                    )
                },
                onFindSimilar = {
                    artistActionController.findSimilarArtists(ArtistId(request.artist.id), request.artist.title)
                },
                onSelectSimilar = { _, similar -> artistActionController.handleSimilarArtistSelected(similar) },
                onOpenSimilarExternal = { _, url -> artistActionController.openExternalArtistUrl(url) },
            ),
        )
        artistDetailActionDispatchStatus(result)?.let { state.status = it }
    },
    onAlbumAction = { request ->
        val album = state.artistDetail?.albums?.firstOrNull { it.id.value == request.album.id }
        val result = dispatchResolvedArtistAlbumAction(
            request = request,
            album = album,
            handlers = ResolvedArtistAlbumActionHandlers(
                onSelect = { shellMediaController.handleShellAlbumSelected(request.album) },
                onStartRadio = { artistActionController.handleArtistAlbumRadio(request.album) },
                onAddToQueue = {
                    artistActionController.loadArtistAlbumTracks(request.album) {
                        mediaController.appendTracksToQueue(it, "album tracks")
                    }
                },
                onDownload = {
                    artistActionController.loadArtistAlbumTracks(request.album) {
                        downloadActionController.downloadTracks(it, request.album.title)
                    }
                },
                onAddToPlaylist = { _, choice ->
                    artistActionController.loadArtistAlbumTracks(request.album) {
                        playlistActionController.addTracksToPlaylist(
                            it,
                            choice,
                            null,
                            request.album.title,
                        )
                    }
                },
                onCreatePlaylistAndAdd = { _, name ->
                    artistActionController.loadArtistAlbumTracks(request.album) {
                        playlistActionController.addTracksToPlaylist(it, null, name, request.album.title)
                    }
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
        artistAlbumActionDispatchStatus(result)?.let { state.status = it }
    },
    onPopularTrackAction = trackActionController::handleTrackAction,
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
