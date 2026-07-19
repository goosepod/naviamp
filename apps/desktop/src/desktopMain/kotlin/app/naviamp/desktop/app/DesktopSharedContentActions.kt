package app.naviamp.desktop

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Track
import app.naviamp.domain.popular.SimilarArtistMatch
import app.naviamp.ui.SharedMediaItemActionRequest
import app.naviamp.ui.SharedMediaItemKind
import app.naviamp.ui.SharedTrackRowActionRequest
import app.naviamp.ui.SharedSimilarArtistUi
import app.naviamp.ui.SharedTrackRowUi
import app.naviamp.ui.NaviampInternetRadioStationEditUi
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
import app.naviamp.ui.toInternetRadioStation

data class DesktopDetailActionSources(
    val selectedAlbum: Album? = null,
    val albumDetail: AlbumDetails? = null,
    val selectedArtist: Artist? = null,
    val artistDetail: ArtistDetails? = null,
    val artistPopularTracks: List<Track> = emptyList(),
    val artistSimilarArtists: List<SimilarArtistMatch> = emptyList(),
) {
    fun album(id: String): Album? =
        albumDetail?.album?.takeIf { it.id.value == id }
            ?: selectedAlbum?.takeIf { it.id.value == id }
            ?: artistDetail?.albums?.firstOrNull { it.id.value == id }

    fun artist(id: String): Artist? =
        artistDetail?.artist?.takeIf { it.id.value == id }
            ?: selectedArtist?.takeIf { it.id.value == id }

    fun albumTrack(id: String): Pair<Int, Track>? {
        val tracks = albumDetail?.tracks.orEmpty()
        val index = tracks.indexOfFirst { it.id.value == id }
        return tracks.getOrNull(index)?.let { index to it }
    }

    fun popularTrack(id: String): Track? =
        artistPopularTracks.firstOrNull { it.id.value == id }

    fun artistAlbums(ids: List<String>): List<Album> =
        ids.mapNotNull { id -> artistDetail?.albums?.firstOrNull { it.id.value == id } }

    fun similarArtist(item: SharedSimilarArtistUi): Pair<Artist?, String?> {
        val match = artistSimilarArtists.firstOrNull { candidate ->
            candidate.candidate.sourceArtistId == item.id || candidate.matchedArtist?.id?.value == item.localArtistId
        }
        return match?.matchedArtist to (match?.candidate?.externalUrl ?: item.externalUrl)
    }
}

data class DesktopPlaylistActionSources(
    val playlists: List<app.naviamp.domain.Playlist> = emptyList(),
    val playlistTracksById: Map<String, List<Track>> = emptyMap(),
    val selectedPlaylist: app.naviamp.domain.Playlist? = null,
    val selectedPlaylistTracks: List<Track> = emptyList(),
) {
    fun playlist(id: String): app.naviamp.domain.Playlist? =
        playlists.firstOrNull { it.id == id }
            ?: selectedPlaylist?.takeIf { it.id == id }

    fun selectedTrack(id: String): Pair<Int, Track>? {
        val index = selectedPlaylistTracks.indexOfFirst { it.id.value == id }
        return selectedPlaylistTracks.getOrNull(index)?.let { index to it }
    }

    fun selectedTracks(rows: List<SharedTrackRowUi>): List<Track>? {
        val tracksById = selectedPlaylistTracks.associateBy { it.id.value }
        val resolved = rows.mapNotNull { tracksById[it.id] }
        return resolved.takeIf { it.size == rows.size }
    }
}

data class DesktopInternetRadioActionSources(
    val stations: List<InternetRadioStation> = emptyList(),
) {
    fun station(id: String): InternetRadioStation? =
        stations.firstOrNull { it.id == id }

    fun station(edit: NaviampInternetRadioStationEditUi): InternetRadioStation =
        edit.toInternetRadioStation()
}

internal fun desktopInternetRadioActions(
    actionSources: DesktopInternetRadioActionSources,
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
    actionSources: DesktopDetailActionSources,
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
                    backRouteOverride = DesktopAppRoute.AlbumDetail,
                )
            }
        }
    },
)

internal fun desktopArtistDetailActions(
    actionSources: DesktopDetailActionSources,
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
    actionSources: DesktopPlaylistActionSources,
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
    playlistActionSources: DesktopPlaylistActionSources,
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

internal fun resolveDesktopMediaItemAction(
    request: SharedMediaItemActionRequest,
    artists: List<Artist> = emptyList(),
    albums: List<Album> = emptyList(),
    onArtistAction: (SharedMediaItemActionRequest, Artist) -> Unit,
    onAlbumAction: (SharedMediaItemActionRequest, Album) -> Unit = { _, _ -> },
) {
    when (request.kind) {
        SharedMediaItemKind.Artist -> artists
            .firstOrNull { artist -> artist.id.value == request.item.id }
            ?.let { artist -> onArtistAction(request, artist) }
        SharedMediaItemKind.Album -> albums
            .firstOrNull { album -> album.id.value == request.item.id }
            ?.let { album -> onAlbumAction(request, album) }
        else -> Unit
    }
}

internal fun resolveDesktopTrackAction(
    request: SharedTrackRowActionRequest,
    tracks: List<Track>,
    onTrackAction: (SharedTrackRowActionRequest, Int, Track) -> Unit,
) {
    val index = tracks.indexOfFirst { track -> track.id.value == request.track.id }
    tracks.getOrNull(index)?.let { track -> onTrackAction(request, index, track) }
}
