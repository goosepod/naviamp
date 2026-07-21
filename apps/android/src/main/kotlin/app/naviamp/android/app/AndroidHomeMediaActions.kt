package app.naviamp.android

import app.naviamp.domain.ArtistId
import app.naviamp.ui.KeepDownloadedActionValue
import app.naviamp.ui.NaviampHomeActions
import app.naviamp.ui.NaviampMediaActions
import app.naviamp.ui.MediaItemActionDispatchResult
import app.naviamp.ui.ResolvedMediaItemActionHandlers
import app.naviamp.ui.SharedArtistDetailUi
import app.naviamp.ui.SharedMediaItemKind
import app.naviamp.ui.SharedTrackRowAction
import app.naviamp.ui.handleResolvedMediaItemAction
import app.naviamp.ui.mediaItemActionDispatchStatus
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
        val result = when (request.kind) {
            SharedMediaItemKind.Album -> handleResolvedMediaItemAction(
                request = request,
                item = request.item,
                handlers = ResolvedMediaItemActionHandlers(
                    onSelect = shellMediaController::handleShellAlbumSelected,
                    onPlay = null,
                    onStartRadio = artistActionController::handleArtistAlbumRadio,
                    onFindSimilar = null,
                    onAddToQueue = { album ->
                        artistActionController.loadArtistAlbumTracks(album) {
                            mediaController.appendTracksToQueue(it, "album tracks")
                        }
                    },
                    onDownload = { album, _ ->
                        artistActionController.loadArtistAlbumTracks(album) {
                            downloadActionController.downloadTracks(it, album.title)
                        }
                    },
                    onAddToPlaylist = { album, choice ->
                        artistActionController.loadArtistAlbumTracks(album) {
                            playlistActionController.addTracksToPlaylist(it, choice, null, album.title)
                        }
                    },
                    onCreatePlaylistAndAdd = { album, name ->
                        artistActionController.loadArtistAlbumTracks(album) {
                            playlistActionController.addTracksToPlaylist(it, null, name, album.title)
                        }
                    },
                    onCopy = null,
                    onToggleFavorite = { album ->
                        toggleAndroidAlbumFavorite(scope, state, album, state.sharedControllers.providerActions)
                    },
                    onRename = null,
                    onEditSmartPlaylist = null,
                    onDelete = null,
                    onEditStation = null,
                    onDeleteStation = null,
                ),
            )
            SharedMediaItemKind.Artist -> handleResolvedMediaItemAction(
                request = request,
                item = request.item,
                handlers = ResolvedMediaItemActionHandlers(
                    onSelect = { artist ->
                        mediaController.openArtistDetails(ArtistId(artist.id), artist.title)
                    },
                    onPlay = null,
                    onStartRadio = { artist ->
                        artistActionController.handleShellArtistRadio(SharedArtistDetailUi(artist, emptyList()))
                    },
                    onFindSimilar = { artist ->
                        artistActionController.findSimilarArtists(ArtistId(artist.id), artist.title)
                    },
                    onAddToQueue = null,
                    onDownload = null,
                    onAddToPlaylist = null,
                    onCreatePlaylistAndAdd = null,
                    onCopy = null,
                    onToggleFavorite = { artist ->
                        toggleAndroidArtistFavorite(scope, state, artist, state.sharedControllers.providerActions)
                    },
                    onRename = null,
                    onEditSmartPlaylist = null,
                    onDelete = null,
                    onEditStation = null,
                    onDeleteStation = null,
                ),
            )
            SharedMediaItemKind.Playlist -> handleResolvedMediaItemAction(
                request = request,
                item = state.homeState.playlists.firstOrNull { it.id == request.item.id },
                handlers = ResolvedMediaItemActionHandlers(
                    onSelect = playlistActionController::openPlaylistDetails,
                    onPlay = playlistActionController::playPlaylist,
                    onStartRadio = null,
                    onFindSimilar = null,
                    onAddToQueue = playlistActionController::addPlaylistToQueue,
                    onDownload = { playlist, value ->
                        if (value == KeepDownloadedActionValue) {
                            downloadActionController.toggleKeepDownloadedPlaylist(playlist)
                        } else {
                            downloadActionController.downloadPlaylist(playlist)
                        }
                    },
                    onAddToPlaylist = { playlist, choice ->
                        playlistActionController.addPlaylistToPlaylist(playlist, choice, null)
                    },
                    onCreatePlaylistAndAdd = { playlist, name ->
                        playlistActionController.addPlaylistToPlaylist(playlist, null, name)
                    },
                    onCopy = { playlist, name, deduplicate ->
                        if (!deduplicate) {
                            playlistActionController.addPlaylistToPlaylist(playlist, null, name)
                        } else {
                            val tracks = if (state.selectedPlaylist?.id == playlist.id) {
                                state.selectedPlaylistTracks.distinctBy { track -> track.id }
                            } else {
                                emptyList()
                            }
                            if (tracks.isEmpty()) {
                                state.status = "Open the playlist before copying a deduplicated version."
                            } else {
                                playlistActionController.addTracksToPlaylist(tracks, null, name, playlist.name)
                            }
                        }
                    },
                    onToggleFavorite = null,
                    onRename = playlistActionController::renamePlaylist,
                    onEditSmartPlaylist = null,
                    onDelete = playlistActionController::deletePlaylist,
                    onEditStation = null,
                    onDeleteStation = null,
                ),
            )
            SharedMediaItemKind.Unknown,
            SharedMediaItemKind.RadioStation,
            SharedMediaItemKind.MixBuilder,
            -> MediaItemActionDispatchResult.UnsupportedAction
        }
        mediaItemActionDispatchStatus(result)?.let { state.status = it }
    },
)
