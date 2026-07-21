package app.naviamp.desktop

import app.naviamp.domain.app.NaviampRoute

import app.naviamp.domain.Album
import app.naviamp.domain.Artist
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Track
import app.naviamp.ui.SharedMediaItemKind
import app.naviamp.ui.SharedDetailActionSources
import app.naviamp.ui.SharedInternetRadioActionSources
import app.naviamp.ui.SharedPlaylistActionSources
import app.naviamp.ui.ResolvedMediaItemActionHandlers
import app.naviamp.ui.ResolvedPlaylistDetailActionHandlers
import app.naviamp.ui.ResolvedTrackRowActionHandlers
import app.naviamp.ui.dispatchResolvedPlaylistDetailAction
import app.naviamp.ui.handleResolvedMediaItemAction
import app.naviamp.ui.handleResolvedTrackRowAction
import app.naviamp.ui.playlistDetailActionDispatchStatus
import app.naviamp.ui.NaviampInternetRadioActions
import app.naviamp.ui.NaviampAlbumDetailActions
import app.naviamp.ui.NaviampArtistDetailActions
import app.naviamp.ui.NaviampPlaylistDetailActions
import app.naviamp.ui.NaviampMediaActions
import app.naviamp.ui.NaviampDownloadsActions
import app.naviamp.ui.DownloadedTrackActionHandlers
import app.naviamp.ui.handleDownloadedTrackAction
import app.naviamp.ui.StationRowAction

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
        handleResolvedTrackRowAction(
            request = request,
            tracks = actionSources.albumDetail?.tracks.orEmpty(),
            handlers = ResolvedTrackRowActionHandlers(
                onSelect = { index, _ -> appActions.playAlbumDetails(index = index) },
                onPlayNext = playlistsController::playNext,
                onStartRadio = { _, track -> appActions.playTrackRadio(track) },
                onPlayTrackRadioNext = appActions::playTrackRadioNext,
                onAddTrackRadioToQueue = appActions::addTrackRadioToQueue,
                onDownload = { _, track -> appActions.downloadTrack(track) },
                onAddToQueue = { _, track -> playlistsController.addTrackToQueue(track) },
                onAddToPlaylist = { _, track, _ -> playlistsController.openTrackAddToPlaylist(track) },
                onToggleFavorite = appActions::toggleTrackFavorite,
                onGoToAlbum = appActions::openTrackAlbumDetails,
                onGoToArtist = { track, artistId, artistName ->
                    appActions.openTrackArtistDetails(track, artistId, artistName, NaviampRoute.AlbumDetail)
                },
            ),
        )
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
        handleResolvedTrackRowAction(
            request = request,
            tracks = actionSources.artistPopularTracks,
            handlers = ResolvedTrackRowActionHandlers(
                onSelect = { _, track -> appActions.playSelectedPopularTrack(track) },
                onPlayNext = playlistsController::playNext,
                onStartRadio = { _, track -> appActions.playPopularTracksRadio(listOf(track)) },
                onPlayTrackRadioNext = appActions::playTrackRadioNext,
                onAddTrackRadioToQueue = appActions::addTrackRadioToQueue,
                onAddToQueue = { _, track -> playlistsController.addTrackToQueue(track) },
                onToggleFavorite = appActions::toggleTrackFavorite,
                onGoToAlbum = appActions::openTrackAlbumDetails,
                onGoToArtist = appActions::openTrackArtistDetails,
            ),
        )
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
        handleResolvedMediaItemAction(
            request = request,
            item = actionSources.album(request.item.id),
            handlers = ResolvedMediaItemActionHandlers(
                onSelect = appActions::openAlbumDetails,
                onStartRadio = appActions::playAlbumRadio,
                onDownload = { album, _ -> appActions.downloadAlbum(album) },
                onAddToQueue = playlistsController::addAlbumToQueue,
                onAddToPlaylist = { album, _ -> playlistsController.openAlbumAddToPlaylist(album) },
                onToggleFavorite = appActions::toggleAlbumFavorite,
            ),
        )
    },
)

internal fun desktopPlaylistDetailActions(
    actionSources: SharedPlaylistActionSources,
    appActions: DesktopAppActions,
    playlistsController: DesktopPlaylistsController,
    onBack: () -> Unit,
): NaviampPlaylistDetailActions = NaviampPlaylistDetailActions(
    onBack = onBack,
    onPlaylistAction = { request ->
        val result = dispatchResolvedPlaylistDetailAction(
            request = request,
            playlist = actionSources.playlist(request.playlist.id),
            handlers = ResolvedPlaylistDetailActionHandlers(
                onPlay = { _, shuffle -> appActions.playPlaylistDetails(shuffle = shuffle) },
                onAddToQueue = { playlistsController.addSelectedPlaylistToQueue() },
                onDownload = { playlist, value ->
                    if (value == app.naviamp.ui.KeepDownloadedActionValue) {
                        appActions.toggleKeepDownloadedPlaylist(playlist)
                    } else {
                        appActions.downloadSelectedPlaylist()
                    }
                },
                onAddToPlaylist = { source, choice ->
                    val target = actionSources.playlist(choice.id)
                    if (target == null) {
                        playlistsController.updateSelectedPlaylistStatus("Playlist not found.")
                    } else {
                        playlistsController.addTargetToPlaylist(
                            AddToPlaylistTarget.PlaylistTarget(source),
                            playlist = target,
                        )
                    }
                },
                onCreatePlaylistAndAdd = { source, name ->
                    playlistsController.addTargetToPlaylist(
                        AddToPlaylistTarget.PlaylistTarget(source),
                        playlist = null,
                        newPlaylistName = name,
                    )
                },
                onCopy = { _, name, deduplicate ->
                    val tracks = actionSources.selectedPlaylistTracks
                        .let { if (deduplicate) it.distinctBy { track -> track.id } else it }
                    playlistsController.saveTracksAsPlaylist(name, tracks, "playlist")
                },
                onRename = playlistsController::renamePlaylist,
                onDelete = playlistsController::deletePlaylist,
            ),
        )
        playlistDetailActionDispatchStatus(result)?.let(playlistsController::updateSelectedPlaylistStatus)
    },
    onTrackAction = { request ->
        handleResolvedTrackRowAction(
            request = request,
            tracks = actionSources.selectedPlaylistTracks,
            handlers = ResolvedTrackRowActionHandlers(
                onSelect = { index, _ -> appActions.playPlaylistDetails(index = index) },
                onPlayNext = playlistsController::playNext,
                onStartRadio = { _, track -> appActions.playTrackRadio(track) },
                onPlayTrackRadioNext = appActions::playTrackRadioNext,
                onAddTrackRadioToQueue = appActions::addTrackRadioToQueue,
                onDownload = { _, track -> appActions.downloadTrack(track) },
                onAddToQueue = { _, track -> playlistsController.addTrackToQueue(track) },
                onAddToPlaylist = { _, track, _ -> playlistsController.openTrackAddToPlaylist(track) },
                onToggleFavorite = appActions::toggleTrackFavorite,
                onGoToAlbum = appActions::openTrackAlbumDetails,
                onGoToArtist = { track, artistId, artistName ->
                    appActions.openTrackArtistDetails(track, artistId, artistName)
                },
            ),
        )
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
            handleResolvedMediaItemAction(
                request,
                playlistActionSources.playlist(request.item.id),
                ResolvedMediaItemActionHandlers(
                    onSelect = appActions::openPlaylistDetails,
                    onPlay = appActions::playPlaylist,
                    onDownload = { playlist, value ->
                        if (value == app.naviamp.ui.KeepDownloadedActionValue) {
                            appActions.toggleKeepDownloadedPlaylist(playlist)
                        } else {
                            appActions.downloadPlaylist(playlist)
                        }
                    },
                    onAddToQueue = playlistsController::addPlaylistToQueue,
                    onAddToPlaylist = { playlist, _ -> playlistsController.openPlaylistAddToPlaylist(playlist) },
                    onRename = { playlist, _ -> playlistsController.requestPlaylistRename(playlist) },
                    onDelete = playlistsController::requestPlaylistDelete,
                ),
            )
        }
        if (request.kind == SharedMediaItemKind.Artist) {
            handleResolvedMediaItemAction(
                request,
                artists.firstOrNull { it.id.value == request.item.id },
                ResolvedMediaItemActionHandlers(
                    onSelect = appActions::openArtistDetails,
                    onStartRadio = appActions::playArtistRadio,
                    onFindSimilar = appActions::findSimilarArtists,
                    onAddToQueue = playlistsController::addArtistToQueue,
                    onAddToPlaylist = { artist, _ -> playlistsController.openArtistAddToPlaylist(artist) },
                    onToggleFavorite = appActions::toggleArtistFavorite,
                ),
            )
        }
        if (request.kind == SharedMediaItemKind.Album) {
            handleResolvedMediaItemAction(
                request,
                albums.firstOrNull { it.id.value == request.item.id },
                ResolvedMediaItemActionHandlers(
                    onSelect = appActions::openAlbumDetails,
                    onStartRadio = appActions::playAlbumRadio,
                    onDownload = { album, _ -> appActions.downloadAlbum(album) },
                    onAddToQueue = playlistsController::addAlbumToQueue,
                    onAddToPlaylist = { album, _ -> playlistsController.openAlbumAddToPlaylist(album) },
                    onToggleFavorite = appActions::toggleAlbumFavorite,
                ),
            )
        }
    },
    onTrackAction = { request ->
        handleResolvedTrackRowAction(
            request,
            tracks,
            ResolvedTrackRowActionHandlers(
                onSelect = { index, _ -> appActions.playSearchTrack(index) },
                onPlayNext = playlistsController::playNext,
                onStartRadio = { index, _ -> appActions.playSearchTrackRadio(index) },
                onPlayTrackRadioNext = appActions::playTrackRadioNext,
                onAddTrackRadioToQueue = appActions::addTrackRadioToQueue,
                onDownload = { index, _ -> appActions.downloadSearchTrack(index) },
                onAddToQueue = { index, _ -> appActions.addSearchTrackToQueue(index) },
                onAddToPlaylist = { index, _, _ -> appActions.openSearchTrackAddToPlaylist(index) },
                onToggleFavorite = appActions::toggleTrackFavorite,
                onGoToAlbum = appActions::openTrackAlbumDetails,
                onGoToArtist = { track, artistId, artistName ->
                    appActions.openTrackArtistDetails(track, artistId, artistName)
                },
            ),
        )
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
            handleDownloadedTrackAction(
                request = request,
                handlers = DownloadedTrackActionHandlers(
                    onSelect = { appActions.playDownloadedTrack(downloads, index) },
                    onAddToPlaylist = { _, _ -> playlistsController.openTrackAddToPlaylist(download.track) },
                    onCreatePlaylistAndAdd = { _, name ->
                        playlistsController.saveTracksAsPlaylist(name, listOf(download.track), "track")
                    },
                    onRemove = { appActions.removeDownloadedTrack(download) },
                ),
            )
        }
    },
    onCancelJob = appActions::cancelDownloadJob,
    onRetryJob = appActions::retryDownloadJob,
    onRefresh = appActions::refreshDownloads,
    onToggleKeepFavoritesDownloaded = appActions::toggleKeepDownloadedFavorites,
    onDeleteAll = appActions::deleteAllDownloads,
)
