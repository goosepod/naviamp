package app.naviamp.desktop

import app.naviamp.domain.app.NaviampRoute

import app.naviamp.domain.Album
import app.naviamp.domain.Artist
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Track
import app.naviamp.ui.SharedMediaItemActionRequest
import app.naviamp.ui.SharedMediaItemKind
import app.naviamp.ui.SharedTrackRowActionRequest
import app.naviamp.ui.SharedDetailActionSources
import app.naviamp.ui.SharedInternetRadioActionSources
import app.naviamp.ui.SharedPlaylistActionSources
import app.naviamp.ui.NaviampInternetRadioActions
import app.naviamp.ui.NaviampAlbumDetailActions
import app.naviamp.ui.NaviampArtistDetailActions
import app.naviamp.ui.NaviampPlaylistDetailActions
import app.naviamp.ui.NaviampMediaActions
import app.naviamp.ui.NaviampDownloadsActions
import app.naviamp.ui.DownloadedTrackAction
import app.naviamp.ui.StationRowAction
import app.naviamp.ui.SharedMediaItemAction
import app.naviamp.ui.SharedTrackRowAction

internal fun desktopInternetRadioActions(
    actionSources: SharedInternetRadioActionSources,
    onRefresh: () -> Unit,
    onPlayStation: (InternetRadioStation) -> Unit,
    onSaveStation: (InternetRadioStation) -> Unit,
    onDeleteStation: (InternetRadioStation) -> Unit,
): NaviampInternetRadioActions = NaviampInternetRadioActions(
    onRefresh = onRefresh,
    onStationAction = { request ->
        actionSources.station(request.station.id)?.let { station ->
            when (request.action) {
                StationRowAction.Select -> onPlayStation(station)
                StationRowAction.Edit -> Unit
                StationRowAction.Delete -> onDeleteStation(station)
            }
        }
    },
    onSaveStation = { edit -> onSaveStation(actionSources.station(edit)) },
)

internal fun desktopAlbumDetailActions(
    actionSources: SharedDetailActionSources,
    appActions: DesktopAppActions,
    playlistsController: DesktopPlaylistsController,
    onBack: () -> Unit,
): NaviampAlbumDetailActions = NaviampAlbumDetailActions(
    onBack = onBack,
    onPlay = { _, shuffle -> appActions.playAlbumDetails(shuffle = shuffle) },
    onRadio = { appActions.playCurrentAlbumRadio() },
    onDownload = { appActions.downloadCurrentAlbum() },
    onAddToQueue = { appActions.addCurrentAlbumToQueue() },
    onAddToPlaylist = { _, _ -> appActions.openCurrentAlbumAddToPlaylist() },
    onFavoriteToggled = { item ->
        actionSources.album(item.id)?.let(appActions::toggleAlbumFavorite)
    },
    onTrackAction = { request ->
        actionSources.albumTrack(request.track.id)?.let { (index, track) ->
            when (request.action) {
                SharedTrackRowAction.Select -> appActions.playAlbumDetails(index = index)
                SharedTrackRowAction.PlayNext -> playlistsController.playNext(track)
                SharedTrackRowAction.StartRadio -> appActions.playTrackRadio(track)
                SharedTrackRowAction.PlayTrackRadioNext -> appActions.playTrackRadioNext(track)
                SharedTrackRowAction.AddTrackRadioToQueue -> appActions.addTrackRadioToQueue(track)
                SharedTrackRowAction.Download -> appActions.downloadTrack(track)
                SharedTrackRowAction.AddToQueue -> playlistsController.addTrackToQueue(track)
                SharedTrackRowAction.AddToPlaylist -> playlistsController.openTrackAddToPlaylist(track)
                SharedTrackRowAction.CreatePlaylistAndAdd -> Unit
                SharedTrackRowAction.ToggleFavorite -> appActions.toggleTrackFavorite(track)
                SharedTrackRowAction.GoToAlbum -> appActions.openTrackAlbumDetails(track)
                SharedTrackRowAction.GoToArtist -> appActions.openTrackArtistDetails(
                    track,
                    artistId = request.artistId,
                    artistName = request.artistName,
                    backRouteOverride = NaviampRoute.AlbumDetail,
                )
            }
        }
    },
)

internal fun desktopArtistDetailActions(
    actionSources: SharedDetailActionSources,
    appActions: DesktopAppActions,
    playlistsController: DesktopPlaylistsController,
): NaviampArtistDetailActions = NaviampArtistDetailActions(
    onBack = appActions::closeArtistDetails,
    onRadio = { details ->
        actionSources.artist(details.artist.id)?.let(appActions::playArtistRadio)
    },
    onPlay = { details ->
        appActions.playArtistCatalog(actionSources.artistAlbums(details.albums.map { it.id }), false)
    },
    onShuffle = { details ->
        appActions.playArtistCatalog(actionSources.artistAlbums(details.albums.map { it.id }), true)
    },
    onAddToQueue = { details ->
        actionSources.artist(details.artist.id)?.let(playlistsController::addArtistToQueue)
    },
    onAddToPlaylist = { details, _ ->
        actionSources.artist(details.artist.id)?.let(playlistsController::openArtistAddToPlaylist)
    },
    onFavoriteToggled = { item ->
        actionSources.artist(item.id)?.let(appActions::toggleArtistFavorite)
    },
    onPopularPlay = { appActions.playPopularTracks(actionSources.artistPopularTracks) },
    onPopularRadio = { appActions.playPopularTracksRadio(actionSources.artistPopularTracks) },
    onPopularAddToQueue = { appActions.addPopularTracksToQueue(actionSources.artistPopularTracks) },
    onTrackAction = { request ->
        actionSources.popularTrack(request.track.id)?.let { track ->
            when (request.action) {
                SharedTrackRowAction.Select -> appActions.playSelectedPopularTrack(track)
                SharedTrackRowAction.PlayNext -> playlistsController.playNext(track)
                SharedTrackRowAction.StartRadio -> appActions.playPopularTracksRadio(listOf(track))
                SharedTrackRowAction.PlayTrackRadioNext -> appActions.playTrackRadioNext(track)
                SharedTrackRowAction.AddTrackRadioToQueue -> appActions.addTrackRadioToQueue(track)
                SharedTrackRowAction.AddToQueue -> playlistsController.addTrackToQueue(track)
                SharedTrackRowAction.Download,
                SharedTrackRowAction.AddToPlaylist,
                SharedTrackRowAction.CreatePlaylistAndAdd,
                -> Unit
                SharedTrackRowAction.ToggleFavorite -> appActions.toggleTrackFavorite(track)
                SharedTrackRowAction.GoToAlbum -> appActions.openTrackAlbumDetails(track)
                SharedTrackRowAction.GoToArtist -> appActions.openTrackArtistDetails(
                    track,
                    artistId = request.artistId,
                    artistName = request.artistName,
                )
            }
        }
    },
    onFindSimilar = { details ->
        actionSources.artist(details.artist.id)?.let(appActions::findSimilarArtists)
    },
    onSimilarArtistSelected = { item ->
        val (localArtist, externalUrl) = actionSources.similarArtist(item)
        when {
            localArtist != null -> appActions.openArtistDetails(localArtist)
            externalUrl != null -> appActions.openExternalArtistUrl(externalUrl)
        }
    },
    onAlbumAction = { request ->
        actionSources.album(request.item.id)?.let { album ->
            when (request.action) {
                SharedMediaItemAction.Select -> appActions.openAlbumDetails(album)
                SharedMediaItemAction.StartRadio -> appActions.playAlbumRadio(album)
                SharedMediaItemAction.Download -> appActions.downloadAlbum(album)
                SharedMediaItemAction.AddToQueue -> playlistsController.addAlbumToQueue(album)
                SharedMediaItemAction.AddToPlaylist -> playlistsController.openAlbumAddToPlaylist(album)
                SharedMediaItemAction.ToggleFavorite -> appActions.toggleAlbumFavorite(album)
                SharedMediaItemAction.Play,
                SharedMediaItemAction.Shuffle,
                SharedMediaItemAction.FindSimilar,
                SharedMediaItemAction.CreatePlaylistAndAdd,
                SharedMediaItemAction.CopyPlaylist,
                SharedMediaItemAction.CopyPlaylistDeduplicated,
                SharedMediaItemAction.Rename,
                SharedMediaItemAction.EditSmartPlaylist,
                SharedMediaItemAction.Delete,
                SharedMediaItemAction.EditStation,
                SharedMediaItemAction.DeleteStation,
                -> Unit
            }
        }
    },
)

internal fun desktopPlaylistDetailActions(
    actionSources: SharedPlaylistActionSources,
    appActions: DesktopAppActions,
    playlistsController: DesktopPlaylistsController,
    onBack: () -> Unit,
): NaviampPlaylistDetailActions = NaviampPlaylistDetailActions(
    onBack = onBack,
    onMediaItemAction = { request ->
        if (request.textValue == app.naviamp.ui.KeepDownloadedActionValue) {
            actionSources.selectedPlaylist?.let(appActions::toggleKeepDownloadedPlaylist)
        } else {
            when (request.action) {
                SharedMediaItemAction.Play -> appActions.playPlaylistDetails()
                SharedMediaItemAction.Shuffle -> appActions.playPlaylistDetails(shuffle = true)
                SharedMediaItemAction.Rename -> playlistsController.requestSelectedPlaylistRename()
                SharedMediaItemAction.Delete -> playlistsController.requestSelectedPlaylistDelete()
                SharedMediaItemAction.Download -> appActions.downloadSelectedPlaylist()
                SharedMediaItemAction.AddToQueue -> playlistsController.addSelectedPlaylistToQueue()
                SharedMediaItemAction.AddToPlaylist -> playlistsController.openSelectedPlaylistAddToPlaylist()
                SharedMediaItemAction.CreatePlaylistAndAdd,
                SharedMediaItemAction.CopyPlaylist,
                SharedMediaItemAction.CopyPlaylistDeduplicated,
                -> request.playlistName?.let { name ->
                    val tracks = if (request.action == SharedMediaItemAction.CopyPlaylistDeduplicated) {
                        actionSources.selectedPlaylistTracks.distinctBy { track -> track.id }
                    } else {
                        actionSources.selectedPlaylistTracks
                    }
                    playlistsController.saveTracksAsPlaylist(name = name, tracks = tracks, label = "playlist")
                }
                SharedMediaItemAction.Select,
                SharedMediaItemAction.StartRadio,
                SharedMediaItemAction.FindSimilar,
                SharedMediaItemAction.ToggleFavorite,
                SharedMediaItemAction.EditSmartPlaylist,
                SharedMediaItemAction.EditStation,
                SharedMediaItemAction.DeleteStation,
                -> Unit
            }
        }
    },
    onTrackAction = { request ->
        actionSources.selectedTrack(request.track.id)?.let { (index, track) ->
            when (request.action) {
                SharedTrackRowAction.Select -> appActions.playPlaylistDetails(index = index)
                SharedTrackRowAction.PlayNext -> playlistsController.playNext(track)
                SharedTrackRowAction.StartRadio -> appActions.playTrackRadio(track)
                SharedTrackRowAction.PlayTrackRadioNext -> appActions.playTrackRadioNext(track)
                SharedTrackRowAction.AddTrackRadioToQueue -> appActions.addTrackRadioToQueue(track)
                SharedTrackRowAction.Download -> appActions.downloadTrack(track)
                SharedTrackRowAction.AddToQueue -> playlistsController.addTrackToQueue(track)
                SharedTrackRowAction.AddToPlaylist -> playlistsController.openTrackAddToPlaylist(track)
                SharedTrackRowAction.CreatePlaylistAndAdd -> Unit
                SharedTrackRowAction.ToggleFavorite -> appActions.toggleTrackFavorite(track)
                SharedTrackRowAction.GoToAlbum -> appActions.openTrackAlbumDetails(track)
                SharedTrackRowAction.GoToArtist -> appActions.openTrackArtistDetails(
                    track,
                    artistId = request.artistId,
                    artistName = request.artistName,
                )
            }
        }
    },
    onUpdateStandardPlaylist = { item, rows ->
        val playlist = actionSources.playlist(item.id)
        val tracks = actionSources.selectedTracks(rows)
        if (playlist != null && tracks != null) {
            playlistsController.updateStandardPlaylistTracks(playlist, tracks)
        }
    },
)

internal fun desktopMediaActions(
    playlistActionSources: SharedPlaylistActionSources,
    artists: List<Artist>,
    albums: List<Album>,
    tracks: List<Track>,
    appActions: DesktopAppActions,
    playlistsController: DesktopPlaylistsController,
): NaviampMediaActions = NaviampMediaActions(
    onAlbumSelected = { item -> appActions.openHomeAlbum(item.id) },
    onAlbumFavoriteToggled = { item -> appActions.toggleHomeAlbumFavorite(item.id) },
    onMixAlbumSelected = { item -> appActions.playHomeMixAlbum(item.id) },
    onPlaylistSelected = { item -> appActions.openHomePlaylist(item.id) },
    onMediaItemAction = { request ->
        if (request.kind == SharedMediaItemKind.Playlist) {
            playlistActionSources.playlist(request.item.id)?.let { playlist ->
                if (request.textValue == app.naviamp.ui.KeepDownloadedActionValue) {
                    appActions.toggleKeepDownloadedPlaylist(playlist)
                } else {
                    when (request.action) {
                        SharedMediaItemAction.Select -> appActions.openPlaylistDetails(playlist)
                        SharedMediaItemAction.Play -> appActions.playPlaylist(playlist, request.shuffle)
                        SharedMediaItemAction.Shuffle -> appActions.playPlaylist(playlist, shuffle = true)
                        SharedMediaItemAction.Download -> appActions.downloadPlaylist(playlist)
                        SharedMediaItemAction.AddToQueue -> playlistsController.addPlaylistToQueue(playlist)
                        SharedMediaItemAction.AddToPlaylist ->
                            playlistsController.openPlaylistAddToPlaylist(playlist)
                        SharedMediaItemAction.Rename -> playlistsController.requestPlaylistRename(playlist)
                        SharedMediaItemAction.Delete -> playlistsController.requestPlaylistDelete(playlist)
                        SharedMediaItemAction.StartRadio,
                        SharedMediaItemAction.FindSimilar,
                        SharedMediaItemAction.ToggleFavorite,
                        SharedMediaItemAction.CreatePlaylistAndAdd,
                        SharedMediaItemAction.CopyPlaylist,
                        SharedMediaItemAction.CopyPlaylistDeduplicated,
                        SharedMediaItemAction.EditSmartPlaylist,
                        SharedMediaItemAction.EditStation,
                        SharedMediaItemAction.DeleteStation,
                        -> Unit
                    }
                }
            }
        }
        if (request.kind == SharedMediaItemKind.Artist) {
            artists.firstOrNull { artist -> artist.id.value == request.item.id }?.let { artist ->
                when (request.action) {
                    SharedMediaItemAction.Select -> appActions.openArtistDetails(artist)
                    SharedMediaItemAction.StartRadio -> appActions.playArtistRadio(artist)
                    SharedMediaItemAction.FindSimilar -> appActions.findSimilarArtists(artist)
                    SharedMediaItemAction.AddToQueue -> playlistsController.addArtistToQueue(artist)
                    SharedMediaItemAction.AddToPlaylist -> playlistsController.openArtistAddToPlaylist(artist)
                    SharedMediaItemAction.ToggleFavorite -> appActions.toggleArtistFavorite(artist)
                    SharedMediaItemAction.Play,
                    SharedMediaItemAction.Shuffle,
                    SharedMediaItemAction.Download,
                    SharedMediaItemAction.CreatePlaylistAndAdd,
                    SharedMediaItemAction.CopyPlaylist,
                    SharedMediaItemAction.CopyPlaylistDeduplicated,
                    SharedMediaItemAction.Rename,
                    SharedMediaItemAction.EditSmartPlaylist,
                    SharedMediaItemAction.Delete,
                    SharedMediaItemAction.EditStation,
                    SharedMediaItemAction.DeleteStation,
                    -> Unit
                }
            }
        }
        if (request.kind == SharedMediaItemKind.Album) {
            albums.firstOrNull { album -> album.id.value == request.item.id }?.let { album ->
                when (request.action) {
                    SharedMediaItemAction.Select -> appActions.openAlbumDetails(album)
                    SharedMediaItemAction.StartRadio -> appActions.playAlbumRadio(album)
                    SharedMediaItemAction.Download -> appActions.downloadAlbum(album)
                    SharedMediaItemAction.AddToQueue -> playlistsController.addAlbumToQueue(album)
                    SharedMediaItemAction.AddToPlaylist -> playlistsController.openAlbumAddToPlaylist(album)
                    SharedMediaItemAction.ToggleFavorite -> appActions.toggleAlbumFavorite(album)
                    SharedMediaItemAction.Play,
                    SharedMediaItemAction.Shuffle,
                    SharedMediaItemAction.FindSimilar,
                    SharedMediaItemAction.CreatePlaylistAndAdd,
                    SharedMediaItemAction.CopyPlaylist,
                    SharedMediaItemAction.CopyPlaylistDeduplicated,
                    SharedMediaItemAction.Rename,
                    SharedMediaItemAction.EditSmartPlaylist,
                    SharedMediaItemAction.Delete,
                    SharedMediaItemAction.EditStation,
                    SharedMediaItemAction.DeleteStation,
                    -> Unit
                }
            }
        }
    },
    onTrackAction = { request ->
        val index = tracks.indexOfFirst { track -> track.id.value == request.track.id }
        tracks.getOrNull(index)?.let { track ->
            when (request.action) {
                SharedTrackRowAction.Select -> appActions.playSearchTrack(index)
                SharedTrackRowAction.PlayNext -> playlistsController.playNext(track)
                SharedTrackRowAction.StartRadio -> appActions.playSearchTrackRadio(index)
                SharedTrackRowAction.PlayTrackRadioNext -> appActions.playTrackRadioNext(track)
                SharedTrackRowAction.AddTrackRadioToQueue -> appActions.addTrackRadioToQueue(track)
                SharedTrackRowAction.Download -> appActions.downloadSearchTrack(index)
                SharedTrackRowAction.AddToQueue -> appActions.addSearchTrackToQueue(index)
                SharedTrackRowAction.AddToPlaylist -> appActions.openSearchTrackAddToPlaylist(index)
                SharedTrackRowAction.CreatePlaylistAndAdd -> Unit
                SharedTrackRowAction.ToggleFavorite -> appActions.toggleTrackFavorite(track)
                SharedTrackRowAction.GoToAlbum -> appActions.openTrackAlbumDetails(track)
                SharedTrackRowAction.GoToArtist -> appActions.openTrackArtistDetails(
                    track,
                    artistId = request.artistId,
                    artistName = request.artistName,
                )
            }
        }
    },
)

internal fun desktopDownloadsActions(
    downloads: List<DownloadedTrack>,
    appActions: DesktopAppActions,
    playlistsController: DesktopPlaylistsController,
): NaviampDownloadsActions = NaviampDownloadsActions(
    onTrackAction = { request ->
        val index = downloads.indexOfFirst { download -> download.path.toString() == request.download.id }
        downloads.getOrNull(index)?.let { download ->
            when (request.action) {
                DownloadedTrackAction.Select -> appActions.playDownloadedTrack(downloads, index)
                DownloadedTrackAction.AddToPlaylist ->
                    playlistsController.openTrackAddToPlaylist(download.track)
                DownloadedTrackAction.Remove -> appActions.removeDownloadedTrack(download)
                DownloadedTrackAction.CreatePlaylistAndAdd -> Unit
            }
        }
    },
    onCancelJob = appActions::cancelDownloadJob,
    onRetryJob = appActions::retryDownloadJob,
    onRefresh = appActions::refreshDownloads,
    onToggleKeepFavoritesDownloaded = appActions::toggleKeepDownloadedFavorites,
    onDeleteAll = appActions::deleteAllDownloads,
)
