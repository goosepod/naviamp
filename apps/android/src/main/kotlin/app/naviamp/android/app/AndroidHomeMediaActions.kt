package app.naviamp.android

import app.naviamp.domain.ArtistId
import app.naviamp.ui.KeepDownloadedActionValue
import app.naviamp.ui.NaviampHomeActions
import app.naviamp.ui.NaviampMediaActions
import app.naviamp.ui.SharedMediaItemAction
import app.naviamp.ui.SharedMediaItemKind
import app.naviamp.ui.SharedTrackRowAction
import kotlinx.coroutines.CoroutineScope

internal fun androidHomeActions(
    state: AndroidAppState,
    refreshHome: () -> Unit,
    mediaController: AndroidMediaAppController,
    navigationController: AndroidNavigationController,
    shellMediaController: AndroidShellMediaController,
    trackActionController: AndroidTrackActionController,
    sonicHomeDiscoveryController: AndroidSonicHomeDiscoveryController,
): NaviampHomeActions = NaviampHomeActions(
    onRefresh = refreshHome,
    onRecentRadioSelected = shellMediaController::handleShellRecentRadioSelected,
    onInternetRadioStationSelected = { item ->
        state.homeState.radioStations.firstOrNull { station -> station.id == item.id }
            ?.let(shellMediaController::handleRadioStationSelected)
            ?: run { state.status = "Station not found." }
    },
    onMixBuilderSelected = navigationController::handleMixBuilderSelected,
    onStationSelected = shellMediaController::handleShellHomeStationSelected,
    onSonicDiscoveryTrackAction = { request ->
        val track = sonicHomeDiscoveryController.trackFor(request)
        when (request.action) {
            SharedTrackRowAction.ToggleFavorite -> track?.let(mediaController::toggleTrackFavorite)
            SharedTrackRowAction.GoToAlbum -> track?.let(shellMediaController::handleTrackGoToAlbum)
            SharedTrackRowAction.GoToArtist -> track?.let { selectedTrack ->
                shellMediaController.handleTrackGoToArtist(
                    selectedTrack,
                    request.artistId,
                    request.artistName,
                )
            }
            else -> sonicHomeDiscoveryController.handleAction(request)
        }
    },
    onRecentlyPlayedTrackAction = { request ->
        if (request.action == SharedTrackRowAction.Select) {
            shellMediaController.handleShellTrackSelected(request.track)
        } else {
            trackActionController.handleTrackAction(request)
        }
    },
)

internal fun androidMediaActions(
    scope: CoroutineScope,
    state: AndroidAppState,
    mediaController: AndroidMediaAppController,
    shellMediaController: AndroidShellMediaController,
    artistActionController: AndroidArtistActionController,
    trackActionController: AndroidTrackActionController,
    playlistActionController: AndroidPlaylistActionController,
    downloadActionController: AndroidDownloadActionController,
): NaviampMediaActions = NaviampMediaActions(
    onTrackSelected = shellMediaController::handleShellTrackSelected,
    onAlbumSelected = shellMediaController::handleShellAlbumSelected,
    onAlbumFavoriteToggled = { item ->
        toggleAndroidAlbumFavorite(scope, state, item, state.sharedControllers.providerActions)
    },
    onMixAlbumSelected = shellMediaController::handleMixAlbumSelected,
    onTrackAction = trackActionController::handleTrackAction,
    onArtistSelected = { selectedArtist ->
        mediaController.openArtistDetails(ArtistId(selectedArtist.id), selectedArtist.title)
    },
    onArtistFavoriteToggled = { item ->
        toggleAndroidArtistFavorite(scope, state, item, state.sharedControllers.providerActions)
    },
    onPlaylistSelected = { selectedPlaylist ->
        state.homeState.playlists.firstOrNull { it.id == selectedPlaylist.id }
            ?.let(playlistActionController::openPlaylistDetails)
            ?: run { state.status = "Playlist not found." }
    },
    onMediaItemAction = { request ->
        when (request.kind) {
            SharedMediaItemKind.Album -> when (request.action) {
                SharedMediaItemAction.Select -> shellMediaController.handleShellAlbumSelected(request.item)
                SharedMediaItemAction.StartRadio -> artistActionController.handleArtistAlbumRadio(request.item)
                SharedMediaItemAction.AddToQueue -> artistActionController.loadArtistAlbumTracks(request.item) {
                    mediaController.appendTracksToQueue(it, "album tracks")
                }
                SharedMediaItemAction.Download -> artistActionController.loadArtistAlbumTracks(request.item) {
                    downloadActionController.downloadTracks(it, request.item.title)
                }
                SharedMediaItemAction.AddToPlaylist -> artistActionController.loadArtistAlbumTracks(request.item) {
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
            SharedMediaItemKind.Artist -> when (request.action) {
                SharedMediaItemAction.Select ->
                    mediaController.openArtistDetails(ArtistId(request.item.id), request.item.title)
                SharedMediaItemAction.ToggleFavorite ->
                    toggleAndroidArtistFavorite(scope, state, request.item, state.sharedControllers.providerActions)
                else -> Unit
            }
            SharedMediaItemKind.Playlist -> {
                val playlist = state.homeState.playlists.firstOrNull { it.id == request.item.id }
                if (playlist == null) {
                    state.status = "Playlist not found."
                } else {
                    when (request.action) {
                        SharedMediaItemAction.Select -> playlistActionController.openPlaylistDetails(playlist)
                        SharedMediaItemAction.Play -> playlistActionController.playPlaylist(playlist, false)
                        SharedMediaItemAction.Shuffle -> playlistActionController.playPlaylist(playlist, true)
                        SharedMediaItemAction.AddToQueue -> playlistActionController.addPlaylistToQueue(playlist)
                        SharedMediaItemAction.Download -> {
                            if (request.textValue == KeepDownloadedActionValue) {
                                downloadActionController.toggleKeepDownloadedPlaylist(playlist)
                            } else {
                                downloadActionController.downloadPlaylist(playlist)
                            }
                        }
                        SharedMediaItemAction.AddToPlaylist ->
                            playlistActionController.addPlaylistToPlaylist(playlist, request.playlistChoice, null)
                        SharedMediaItemAction.CreatePlaylistAndAdd,
                        SharedMediaItemAction.CopyPlaylist,
                        -> playlistActionController.addPlaylistToPlaylist(playlist, null, request.playlistName)
                        SharedMediaItemAction.CopyPlaylistDeduplicated -> {
                            val tracks = if (state.selectedPlaylist?.id == playlist.id) {
                                state.selectedPlaylistTracks.distinctBy { track -> track.id }
                            } else {
                                emptyList()
                            }
                            if (tracks.isNotEmpty()) {
                                playlistActionController.addTracksToPlaylist(
                                    tracks,
                                    null,
                                    request.playlistName,
                                    playlist.name,
                                )
                            } else {
                                state.status = "Open the playlist before copying a deduplicated version."
                            }
                        }
                        SharedMediaItemAction.Rename ->
                            request.textValue?.let { name -> playlistActionController.renamePlaylist(playlist, name) }
                        SharedMediaItemAction.Delete -> playlistActionController.deletePlaylist(playlist)
                        else -> Unit
                    }
                }
            }
            SharedMediaItemKind.Unknown,
            SharedMediaItemKind.RadioStation,
            SharedMediaItemKind.MixBuilder,
            -> Unit
        }
    },
)
