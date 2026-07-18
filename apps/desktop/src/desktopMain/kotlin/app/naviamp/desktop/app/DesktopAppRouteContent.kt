package app.naviamp.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.naviamp.desktop.settings.CacheSettings
import app.naviamp.desktop.settings.PlaybackSettings
import app.naviamp.domain.Album
import app.naviamp.domain.Artist
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Playlist
import app.naviamp.domain.Track
import app.naviamp.domain.cache.DownloadJob
import app.naviamp.domain.cache.KeepDownloadedCollectionPolicy
import app.naviamp.domain.cache.StorageCacheStats
import app.naviamp.domain.playback.AudioOutputDevicePlaybackEngine
import app.naviamp.domain.home.HomeContent
import app.naviamp.domain.playback.EqualizerPlaybackEngine
import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.domain.settings.ConnectionFormState
import app.naviamp.domain.settings.InterfaceSettings
import app.naviamp.domain.sonichome.SonicHomeDiscoveryRows
import app.naviamp.ui.AlbumMixBuilderContent
import app.naviamp.ui.ArtistMixBuilderContent
import app.naviamp.ui.GenreMixBuilderContent
import app.naviamp.ui.NaviampAboutUi
import app.naviamp.ui.NaviampAlbumDetailScreenUi
import app.naviamp.ui.NaviampArtistDetailScreenUi
import app.naviamp.ui.NaviampSavedConnectionUi
import app.naviamp.ui.NaviampLibraryScreenUi
import app.naviamp.ui.NaviampInternetRadioScreenUi
import app.naviamp.ui.NaviampPlaylistDetailScreenUi
import app.naviamp.ui.NaviampPlaylistsScreenUi
import app.naviamp.ui.NaviampSearchScreenUi
import app.naviamp.ui.NaviampShellCapabilitiesUi
import app.naviamp.ui.NaviampShellConnectionUi
import app.naviamp.ui.SharedAlbumMixBuilderUi
import app.naviamp.ui.SharedArtistMixBuilderUi
import app.naviamp.ui.SharedGenreMixBuilderUi
import app.naviamp.ui.SharedGenreMixItemUi
import app.naviamp.ui.SharedHomeRoute
import app.naviamp.ui.SharedHomeDiscoveryTrackActionRequest
import app.naviamp.ui.SharedMediaItemAction
import app.naviamp.ui.SharedMediaItemActionRequest
import app.naviamp.ui.SharedMediaItemUi
import app.naviamp.ui.SharedMixBuilderUi
import app.naviamp.ui.SharedSonicMixBiasUi
import app.naviamp.ui.SharedSonicMixBuilderUi
import app.naviamp.ui.SharedSonicPathBuilderUi
import app.naviamp.ui.SharedTrackGroupAction
import app.naviamp.ui.SharedTrackGroupActionRequest
import app.naviamp.ui.SharedTrackRowAction
import app.naviamp.ui.SharedTrackRowUi
import app.naviamp.ui.SharedPlaylistSortMode
import app.naviamp.ui.SaveQueueAsPlaylistDialog
import app.naviamp.ui.StationRowAction
import app.naviamp.ui.SonicMixBuilderContent
import app.naviamp.ui.SonicPathBuilderContent
import app.naviamp.ui.toSharedHomeUi
import app.naviamp.ui.toConnectionSettingsUi
import app.naviamp.ui.toPlaybackSettingsUi
import app.naviamp.ui.toCacheSettingsUi

@Composable
fun ColumnScope.DesktopAppRouteContent(
    appColors: DesktopAppColors,
    appRoute: DesktopAppRoute,
    connection: NaviampShellConnectionUi,
    capabilities: NaviampShellCapabilitiesUi,
    about: NaviampAboutUi,
    homeStatus: String?,
    homeContent: HomeContent,
    homeRefreshing: Boolean,
    onRefreshHome: () -> Unit,
    sonicHomeDiscoveryRows: SonicHomeDiscoveryRows,
    coverArtUrl: (String?) -> String?,
    appActions: DesktopAppActions,
    playlistsController: DesktopPlaylistsController,
    internetRadioController: DesktopInternetRadioController,
    libraryController: DesktopLibraryController,
    searchController: DesktopSearchController,
    smartPlaylistsController: DesktopSmartPlaylistsController,
    onRouteSelected: (DesktopAppRoute) -> Unit,
    onOpenArtistMixBuilder: () -> Unit,
    onOpenAlbumMixBuilder: () -> Unit,
    albumDetail: NaviampAlbumDetailScreenUi,
    albumDetailBackRoute: DesktopAppRoute,
    artistDetail: NaviampArtistDetailScreenUi,
    detailActionSources: DesktopDetailActionSources,
    artistDetailBackRoute: DesktopAppRoute,
    playlists: NaviampPlaylistsScreenUi,
    playlistDetail: NaviampPlaylistDetailScreenUi,
    playlistActionSources: DesktopPlaylistActionSources,
    onPlaylistSortModeChanged: (SharedPlaylistSortMode) -> Unit,
    onPlaylistRenameRequested: (Playlist) -> Unit,
    onPlaylistDeleteRequested: (Playlist) -> Unit,
    library: NaviampLibraryScreenUi,
    libraryTab: DesktopLibraryTab,
    libraryListState: LazyListState,
    onLibraryQueryChanged: (String) -> Unit,
    search: NaviampSearchScreenUi,
    artistMixBuilder: SharedArtistMixBuilderUi,
    onArtistMixQueryChanged: (String) -> Unit,
    onArtistMixSearch: () -> Unit,
    onArtistMixArtistSelected: (SharedMediaItemUi) -> Unit,
    onArtistMixArtistRemoved: (SharedMediaItemUi) -> Unit,
    onArtistMixReset: () -> Unit,
    onArtistMixPlay: () -> Unit,
    albumMixBuilder: SharedAlbumMixBuilderUi,
    onAlbumMixQueryChanged: (String) -> Unit,
    onAlbumMixSearch: () -> Unit,
    onAlbumMixAlbumSelected: (SharedMediaItemUi) -> Unit,
    onAlbumMixAlbumRemoved: (SharedMediaItemUi) -> Unit,
    onAlbumMixReset: () -> Unit,
    onAlbumMixPlay: () -> Unit,
    genreMixBuilder: SharedGenreMixBuilderUi,
    onGenreMixQueryChanged: (String) -> Unit,
    onGenreMixSearch: () -> Unit,
    onGenreMixGenreSelected: (SharedGenreMixItemUi) -> Unit,
    onGenreMixGenreRemoved: (SharedGenreMixItemUi) -> Unit,
    onGenreMixReset: () -> Unit,
    onGenreMixPlay: () -> Unit,
    sonicPathBuilder: SharedSonicPathBuilderUi,
    onSonicPathStartQueryChanged: (String) -> Unit,
    onSonicPathEndQueryChanged: (String) -> Unit,
    onSonicPathStartSearch: () -> Unit,
    onSonicPathEndSearch: () -> Unit,
    onSonicPathStartTrackSelected: (SharedTrackRowUi) -> Unit,
    onSonicPathEndTrackSelected: (SharedTrackRowUi) -> Unit,
    onSonicPathStartTrackCleared: () -> Unit,
    onSonicPathEndTrackCleared: () -> Unit,
    onSonicPathCountChanged: (Int) -> Unit,
    onSonicPathBuild: () -> Unit,
    onSonicPathReset: () -> Unit,
    onSonicPathPlay: () -> Unit,
    onSonicPathAddToQueue: () -> Unit,
    onSonicPathSaveAsPlaylist: (String) -> Unit,
    sonicMixBuilder: SharedSonicMixBuilderUi,
    onSonicMixQueryChanged: (String) -> Unit,
    onSonicMixSearch: () -> Unit,
    onSonicMixTrackSelected: (SharedTrackRowUi) -> Unit,
    onSonicMixTrackRemoved: (SharedTrackRowUi) -> Unit,
    onSonicMixTargetLengthChanged: (Int) -> Unit,
    onSonicMixBiasChanged: (SharedSonicMixBiasUi) -> Unit,
    onSonicMixBuild: () -> Unit,
    onSonicMixReset: () -> Unit,
    onSonicMixPlay: () -> Unit,
    onSonicMixAddToQueue: () -> Unit,
    onSonicMixSaveAsPlaylist: (String) -> Unit,
    internetRadio: NaviampInternetRadioScreenUi,
    internetRadioActionSources: DesktopInternetRadioActionSources,
    onSaveInternetRadioStation: (InternetRadioStation) -> Unit,
    onDeleteInternetRadioStation: (InternetRadioStation) -> Unit,
    connectedSourceId: String?,
    downloadRefreshToken: Int,
    downloadStatus: String?,
    downloadJobs: List<DownloadJob>,
    keepDownloadedPolicies: List<KeepDownloadedCollectionPolicy>,
    cacheSettings: CacheSettings,
    cacheStats: StorageCacheStats,
    settingsSyncDirectoryPath: String?,
    settingsSyncAutoExportEnabled: Boolean,
    settingsSyncStatus: String?,
    downloadedTracks: (sourceId: String) -> List<DownloadedTrack>,
    interfaceSettings: InterfaceSettings,
    playbackSettings: PlaybackSettings,
    playbackEngine: PlaybackEngine,
    onConnectionFormChanged: (ConnectionFormState) -> Unit,
    onConnect: () -> Unit,
    onNewConnection: () -> Unit,
    onEditConnection: (NaviampSavedConnectionUi) -> Unit,
    onConnectSavedConnection: (NaviampSavedConnectionUi) -> Unit,
    onDeleteConnection: (NaviampSavedConnectionUi) -> Unit,
    onCancelConnectionForm: () -> Unit,
    onSettingsSyncDirectoryChanged: (String?) -> Unit,
    onSettingsSyncDirectorySelectedForImport: (String) -> Unit,
    onSettingsSyncAutoExportChanged: (Boolean) -> Unit,
    onSettingsSyncExport: () -> Unit,
    onSettingsSyncImport: () -> Unit,
    onInterfaceSettingsChanged: (InterfaceSettings) -> Unit,
    onPlaybackSettingsChanged: (PlaybackSettings) -> Unit,
    onPlaybackSettingsChangedAndRedownload: (PlaybackSettings) -> Unit,
    onCacheSettingsChanged: (CacheSettings) -> Unit,
    onOpenStatsForNerds: () -> Unit,
    onClearCache: () -> Unit,
    onClearLibrary: () -> Unit,
    onRefreshLibrary: () -> Unit,
    onResetDatabase: () -> Unit,
    onSonicHomeDiscoveryTrackAction: (SharedHomeDiscoveryTrackActionRequest) -> Unit,
) {
    var saveSonicPathDialogOpen by remember { mutableStateOf(false) }
    var saveSonicMixDialogOpen by remember { mutableStateOf(false) }
    val contentScrollState = rememberScrollState()
    val sharedHome = homeContent.toSharedHomeUi(
        coverArtUrl = coverArtUrl,
        playlistTracksById = playlistActionSources.playlistTracksById,
        sonicDiscoveryRows = sonicHomeDiscoveryRows,
        canFavoriteAlbums = true,
        showSonicPathBuilder = playbackSettings.sonicSimilarityEnabled && capabilities.sonicSimilarity,
        showSonicMixBuilder = playbackSettings.sonicSimilarityEnabled && capabilities.sonicSimilarity,
    )
    fun openMixBuilder(builder: SharedMixBuilderUi) {
        when (builder.id) {
            "artist" -> onOpenArtistMixBuilder()
            "album" -> onOpenAlbumMixBuilder()
            "genre" -> onRouteSelected(DesktopAppRoute.GenreMix)
            "sonic-path" -> onRouteSelected(DesktopAppRoute.SonicPath)
            "sonic-mix" -> onRouteSelected(DesktopAppRoute.SonicMix)
        }
    }
    fun handleArtistMediaAction(
        requestAction: SharedMediaItemAction,
        artist: Artist,
    ) {
        when (requestAction) {
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
    fun handleAlbumMediaAction(
        requestAction: SharedMediaItemAction,
        album: Album,
    ) {
        when (requestAction) {
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
    fun handleSelectedAlbumMediaAction(requestAction: SharedMediaItemAction) {
        when (requestAction) {
            SharedMediaItemAction.Play -> appActions.playAlbumDetails()
            SharedMediaItemAction.Shuffle -> appActions.playAlbumDetails(shuffle = true)
            SharedMediaItemAction.StartRadio -> appActions.playCurrentAlbumRadio()
            SharedMediaItemAction.Download -> appActions.downloadCurrentAlbum()
            SharedMediaItemAction.AddToQueue -> appActions.addCurrentAlbumToQueue()
            SharedMediaItemAction.AddToPlaylist -> appActions.openCurrentAlbumAddToPlaylist()
            SharedMediaItemAction.ToggleFavorite -> (
                detailActionSources.albumDetail?.album ?: detailActionSources.selectedAlbum
                )?.let {
                appActions.toggleAlbumFavorite(it)
            }
            SharedMediaItemAction.Select,
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
    fun handlePlaylistMediaAction(
        requestAction: SharedMediaItemAction,
        playlist: Playlist,
        shuffle: Boolean = false,
    ) {
        when (requestAction) {
            SharedMediaItemAction.Select -> appActions.openPlaylistDetails(playlist)
            SharedMediaItemAction.Play -> appActions.playPlaylist(playlist, shuffle)
            SharedMediaItemAction.Shuffle -> appActions.playPlaylist(playlist, shuffle = true)
            SharedMediaItemAction.Download -> appActions.downloadPlaylist(playlist)
            SharedMediaItemAction.AddToQueue -> playlistsController.addPlaylistToQueue(playlist)
            SharedMediaItemAction.AddToPlaylist -> playlistsController.openPlaylistAddToPlaylist(playlist)
            SharedMediaItemAction.Rename -> onPlaylistRenameRequested(playlist)
            SharedMediaItemAction.Delete -> onPlaylistDeleteRequested(playlist)
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
    fun handleSelectedPlaylistMediaAction(request: SharedMediaItemActionRequest) {
        if (request.textValue == app.naviamp.ui.KeepDownloadedActionValue) {
            playlistActionSources.selectedPlaylist?.let(appActions::toggleKeepDownloadedPlaylist)
            return
        }
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
                    playlistActionSources.selectedPlaylistTracks.distinctBy { track -> track.id }
                } else {
                    playlistActionSources.selectedPlaylistTracks
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
    fun handlePopularTracksGroupAction(request: SharedTrackGroupActionRequest) {
        if (request.tracks.isEmpty()) return
        when (request.action) {
            SharedTrackGroupAction.Play -> appActions.playPopularTracks(detailActionSources.artistPopularTracks)
            SharedTrackGroupAction.StartRadio -> appActions.playPopularTracksRadio(detailActionSources.artistPopularTracks)
            SharedTrackGroupAction.AddToQueue -> appActions.addPopularTracksToQueue(detailActionSources.artistPopularTracks)
        }
    }

    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .then(
                if (
                    appRoute == DesktopAppRoute.Home ||
                        appRoute == DesktopAppRoute.Library ||
                        appRoute == DesktopAppRoute.ArtistMix ||
                        appRoute == DesktopAppRoute.AlbumMix ||
                        appRoute == DesktopAppRoute.GenreMix ||
                        appRoute == DesktopAppRoute.SonicPath ||
                        appRoute == DesktopAppRoute.SonicMix ||
                        appRoute == DesktopAppRoute.Settings ||
                        appRoute == DesktopAppRoute.AlbumDetail ||
                        appRoute == DesktopAppRoute.ArtistDetail
                ) {
                    Modifier
                } else {
                    Modifier.verticalScroll(contentScrollState)
                },
            ),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            when (appRoute) {
                DesktopAppRoute.Player -> Unit
                DesktopAppRoute.Home -> SharedHomeRoute(
                    colors = appColors,
                    home = sharedHome,
                    isRefreshing = homeRefreshing,
                    onRefresh = onRefreshHome,
                    onAlbumSelected = { item -> appActions.openHomeAlbum(item.id) },
                    onAlbumFavoriteToggled = { item -> appActions.toggleHomeAlbumFavorite(item.id) },
                    onMixAlbumSelected = { item -> appActions.playHomeMixAlbum(item.id) },
                    onPlaylistSelected = { item -> appActions.openHomePlaylist(item.id) },
                    onRecentRadioSelected = { item -> appActions.playHomeRecentRadio(item.id) },
                    onInternetRadioStationSelected = { item -> appActions.playHomeInternetRadio(item.id) },
                    onMixBuilderSelected = ::openMixBuilder,
                    onHomeStationSelected = { station -> appActions.playHomeStation(station.id) },
                    onSonicDiscoveryTrackAction = onSonicHomeDiscoveryTrackAction,
                    onRecentlyPlayedTrackAction = { request ->
                        val tracks = homeContent.recentlyPlayedTracks
                        val index = tracks.indexOfFirst { track -> track.id.value == request.track.id }
                        val track = tracks.getOrNull(index)
                        if (track != null) {
                            when (request.action) {
                                SharedTrackRowAction.Select -> appActions.playPopularTracks(tracks, index)
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
                )
                DesktopAppRoute.AlbumDetail -> DesktopAlbumDetailPanel(
                    appColors = appColors,
                    screen = albumDetail,
                    onBack = { onRouteSelected(albumDetailBackRoute) },
                    onAlbumAction = { request -> handleSelectedAlbumMediaAction(request.action) },
                    onTrackAction = { request ->
                        detailActionSources.albumTrack(request.track.id)?.let { (index, track) ->
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
                    onArtistSelected = { request ->
                        detailActionSources.albumTrack(request.track.id)?.second?.let { track ->
                            appActions.openTrackArtistDetails(
                                track,
                                artistId = request.artistId,
                                artistName = request.artistName,
                                backRouteOverride = DesktopAppRoute.AlbumDetail,
                            )
                        }
                    },
                )
                DesktopAppRoute.ArtistDetail -> DesktopArtistDetailPanel(
                    appColors = appColors,
                    screen = artistDetail,
                    albumCollectionLayout = interfaceSettings.albumCollectionLayout,
                    albumSortOrder = interfaceSettings.albumSortOrder,
                    groupAlbumsByReleaseType = interfaceSettings.groupAlbumsByReleaseType,
                    onBack = appActions::closeArtistDetails,
                    onSimilarArtistSelected = { item ->
                        val (localArtist, externalUrl) = detailActionSources.similarArtist(item)
                        when {
                            localArtist != null -> appActions.openArtistDetails(localArtist)
                            externalUrl != null -> appActions.openExternalArtistUrl(externalUrl)
                        }
                    },
                    onArtistAction = { request ->
                        detailActionSources.artist(request.item.id)
                            ?.let { artist -> handleArtistMediaAction(request.action, artist) }
                    },
                    onArtistCatalogPlay = { albums, shuffle ->
                        appActions.playArtistCatalog(
                            detailActionSources.artistAlbums(albums.map { it.id }),
                            shuffle,
                        )
                    },
                    onPopularTracksAction = ::handlePopularTracksGroupAction,
                    onPopularTrackAction = { request ->
                        detailActionSources.popularTrack(request.track.id)
                            ?.let { track ->
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
                    onAlbumAction = { request ->
                        detailActionSources.album(request.item.id)
                            ?.let { album -> handleAlbumMediaAction(request.action, album) }
                    },
                )
                DesktopAppRoute.Playlists -> DesktopPlaylistsPanel(
                    appColors = appColors,
                    screen = playlists.copy(status = playlists.status ?: connection.status.pageStatusOrNull()),
                    onSortModeChanged = onPlaylistSortModeChanged,
                    onPlaylistAction = { request ->
                        playlistActionSources.playlist(request.item.id)
                            ?.let { playlist ->
                                if (request.textValue == app.naviamp.ui.KeepDownloadedActionValue) {
                                    appActions.toggleKeepDownloadedPlaylist(playlist)
                                } else {
                                    handlePlaylistMediaAction(request.action, playlist, request.shuffle)
                                }
                            }
                    },
                    onRefreshPlaylists = { playlistsController.refreshPlaylists(useCache = false) },
                    onSmartPlaylistSave = smartPlaylistsController::saveSmartPlaylist,
                    onSmartPlaylistUpdate = { item, definition ->
                        playlistActionSources.playlist(item.id)?.let { playlist ->
                            smartPlaylistsController.updateSmartPlaylist(playlist, definition)
                        }
                    },
                    onSmartPlaylistSaveWithPassword = smartPlaylistsController::saveSmartPlaylistWithPassword,
                    onSmartPlaylistUpdateWithPassword = { item, definition, password ->
                        playlistActionSources.playlist(item.id)?.let { playlist ->
                            smartPlaylistsController.updateSmartPlaylistWithPassword(playlist, definition, password)
                        }
                    },
                    onSmartPlaylistLoad = { item ->
                        playlistActionSources.playlist(item.id)
                            ?.let { smartPlaylistsController.loadSmartPlaylistDefinition(it) }
                            ?: error("Playlist ${item.title} is no longer available.")
                    },
                    availableLibraries = connection.availableMusicFolders,
                    selectedConnectionLibraryIds = connection.form.selectedMusicFolderIds,
                )
                DesktopAppRoute.PlaylistDetail -> DesktopPlaylistDetailPanel(
                    appColors = appColors,
                    screen = playlistDetail.copy(status = playlistDetail.status ?: playlists.status),
                    onBack = { onRouteSelected(DesktopAppRoute.Playlists) },
                    onPlaylistAction = { request -> handleSelectedPlaylistMediaAction(request) },
                    onTrackAction = { request ->
                        playlistActionSources.selectedTrack(request.track.id)?.let { (index, track) ->
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
                    onUpdateStandardPlaylist = { rows ->
                        val playlist = playlistActionSources.selectedPlaylist
                        val tracks = playlistActionSources.selectedTracks(rows)
                        if (playlist != null && tracks != null) {
                            playlistsController.updateStandardPlaylistTracks(playlist, tracks)
                        }
                    },
                    onSmartPlaylistUpdate = { definition ->
                        playlistActionSources.selectedPlaylist?.let { playlist ->
                            smartPlaylistsController.updateSmartPlaylist(playlist, definition)
                        }
                    },
                    onSmartPlaylistUpdateWithPassword = { definition, password ->
                        playlistActionSources.selectedPlaylist?.let { playlist ->
                            smartPlaylistsController.updateSmartPlaylistWithPassword(playlist, definition, password)
                        }
                    },
                    onSmartPlaylistLoad = {
                        playlistActionSources.selectedPlaylist
                            ?.let { smartPlaylistsController.loadSmartPlaylistDefinition(it) }
                            ?: error("The selected playlist is no longer available.")
                    },
                    availableLibraries = connection.availableMusicFolders,
                    selectedConnectionLibraryIds = connection.form.selectedMusicFolderIds,
                )
                DesktopAppRoute.Library -> {
                    DesktopLibraryPanel(
                        appColors = appColors,
                        library = library.copy(
                            syncStatus = library.syncStatus.copy(
                                message = library.syncStatus.message ?: connection.status.pageStatusOrNull(),
                            ),
                        ),
                        listState = libraryListState,
                        onQueryChanged = onLibraryQueryChanged,
                        onJumpToLetter = libraryController::jumpLibraryToLetter,
                        onMediaItemAction = { request ->
                            resolveDesktopMediaItemAction(
                                request = request,
                                artists = libraryController.snapshot.artists,
                                onArtistAction = { action, artist ->
                                    handleArtistMediaAction(action.action, artist)
                                },
                            )
                        },
                        onRefreshLibrary = libraryController::refreshArtistIndex,
                    )
                }
                DesktopAppRoute.Search -> DesktopSearchPanel(
                    appColors = appColors,
                    search = search,
                    onQueryChanged = searchController::updateQuery,
                    onClearSearch = searchController::clearSearch,
                    onMediaItemAction = { request ->
                        resolveDesktopMediaItemAction(
                            request = request,
                            artists = searchController.results.artists,
                            albums = searchController.results.albums,
                            onArtistAction = { action, artist ->
                                handleArtistMediaAction(action.action, artist)
                            },
                            onAlbumAction = { action, album ->
                                handleAlbumMediaAction(action.action, album)
                            },
                        )
                    },
                    onTrackAction = { request ->
                        resolveDesktopTrackAction(
                            request = request,
                            tracks = searchController.results.tracks,
                        ) { action, index, track ->
                            when (action.action) {
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
                                    artistId = action.artistId,
                                    artistName = action.artistName,
                                )
                            }
                        }
                    },
                )
                DesktopAppRoute.ArtistMix -> Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(contentScrollState),
                    ) {
                        ArtistMixBuilderContent(
                            colors = appColors,
                            builder = artistMixBuilder,
                            onQueryChanged = onArtistMixQueryChanged,
                            onSearch = onArtistMixSearch,
                            onArtistSelected = onArtistMixArtistSelected,
                            onArtistRemoved = onArtistMixArtistRemoved,
                            onReset = onArtistMixReset,
                            onPlayMix = onArtistMixPlay,
                            showPlayMixButton = false,
                        )
                    }
                    if (artistMixBuilder.selectedArtists.isNotEmpty()) {
                        Button(
                            onClick = onArtistMixPlay,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = appColors.accent,
                                contentColor = appColors.onAccent,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Play Mix")
                        }
                    }
                }
                DesktopAppRoute.AlbumMix -> Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(contentScrollState),
                    ) {
                        AlbumMixBuilderContent(
                            colors = appColors,
                            builder = albumMixBuilder,
                            onQueryChanged = onAlbumMixQueryChanged,
                            onSearch = onAlbumMixSearch,
                            onAlbumSelected = onAlbumMixAlbumSelected,
                            onAlbumRemoved = onAlbumMixAlbumRemoved,
                            onReset = onAlbumMixReset,
                            onPlayMix = onAlbumMixPlay,
                            showPlayMixButton = false,
                        )
                    }
                    if (albumMixBuilder.selectedAlbums.isNotEmpty()) {
                        Button(
                            onClick = onAlbumMixPlay,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = appColors.accent,
                                contentColor = appColors.onAccent,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Play Mix")
                        }
                    }
                }
                DesktopAppRoute.GenreMix -> Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(contentScrollState),
                    ) {
                        GenreMixBuilderContent(
                            colors = appColors,
                            builder = genreMixBuilder,
                            onQueryChanged = onGenreMixQueryChanged,
                            onSearch = onGenreMixSearch,
                            onGenreSelected = onGenreMixGenreSelected,
                            onGenreRemoved = onGenreMixGenreRemoved,
                            onReset = onGenreMixReset,
                            onPlayMix = onGenreMixPlay,
                            showPlayMixButton = false,
                        )
                    }
                    if (genreMixBuilder.selectedGenres.isNotEmpty()) {
                        Button(
                            onClick = onGenreMixPlay,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = appColors.accent,
                                contentColor = appColors.onAccent,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Play Mix")
                        }
                    }
                }
                DesktopAppRoute.SonicPath -> Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(contentScrollState),
                    ) {
                        SonicPathBuilderContent(
                            colors = appColors,
                            builder = sonicPathBuilder,
                            onStartQueryChanged = onSonicPathStartQueryChanged,
                            onEndQueryChanged = onSonicPathEndQueryChanged,
                            onStartSearch = onSonicPathStartSearch,
                            onEndSearch = onSonicPathEndSearch,
                            onStartTrackSelected = onSonicPathStartTrackSelected,
                            onEndTrackSelected = onSonicPathEndTrackSelected,
                            onStartTrackCleared = onSonicPathStartTrackCleared,
                            onEndTrackCleared = onSonicPathEndTrackCleared,
                            onCountChanged = onSonicPathCountChanged,
                            onBuildPath = onSonicPathBuild,
                            onReset = onSonicPathReset,
                            onPlayPath = onSonicPathPlay,
                            onAddPathToQueue = onSonicPathAddToQueue,
                            showPathActions = false,
                        )
                    }
                    if (sonicPathBuilder.hasPath) {
                        androidx.compose.foundation.layout.Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Button(
                                onClick = onSonicPathPlay,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = appColors.accent,
                                    contentColor = appColors.onAccent,
                                ),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Play Path")
                            }
                            Button(
                                onClick = onSonicPathAddToQueue,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Add to Queue")
                            }
                            Button(
                                onClick = { saveSonicPathDialogOpen = true },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Save")
                            }
                        }
                    }
                }
                DesktopAppRoute.SonicMix -> Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(contentScrollState),
                    ) {
                        SonicMixBuilderContent(
                            colors = appColors,
                            builder = sonicMixBuilder,
                            onQueryChanged = onSonicMixQueryChanged,
                            onSearch = onSonicMixSearch,
                            onTrackSelected = onSonicMixTrackSelected,
                            onTrackRemoved = onSonicMixTrackRemoved,
                            onTargetLengthChanged = onSonicMixTargetLengthChanged,
                            onBiasChanged = onSonicMixBiasChanged,
                            onBuildMix = onSonicMixBuild,
                            onReset = onSonicMixReset,
                            onPlayMix = onSonicMixPlay,
                            onAddMixToQueue = onSonicMixAddToQueue,
                            showMixActions = false,
                        )
                    }
                    if (sonicMixBuilder.hasMix) {
                        androidx.compose.foundation.layout.Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Button(
                                onClick = onSonicMixPlay,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = appColors.accent,
                                    contentColor = appColors.onAccent,
                                ),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Play Mix")
                            }
                            Button(
                                onClick = onSonicMixAddToQueue,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Add to Queue")
                            }
                            Button(
                                onClick = { saveSonicMixDialogOpen = true },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Save")
                            }
                        }
                    }
                }
                DesktopAppRoute.InternetRadio -> Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    DesktopInternetRadioPanel(
                        appColors = appColors,
                        screen = internetRadio.copy(
                            status = internetRadio.status ?: connection.status.pageStatusOrNull(),
                        ),
                        onStationAction = { request ->
                            internetRadioActionSources.station(request.station.id)?.let { station ->
                                when (request.action) {
                                    StationRowAction.Select -> internetRadioController.playStation(station)
                                    StationRowAction.Edit -> Unit
                                    StationRowAction.Delete -> onDeleteInternetRadioStation(station)
                                }
                            }
                        },
                        onSaveStation = { edit ->
                            onSaveInternetRadioStation(internetRadioActionSources.station(edit))
                        },
                        onRefreshStations = internetRadioController::refreshStations,
                    )
                }
                DesktopAppRoute.Downloads -> DesktopDownloadsRoute(
                    appColors = appColors,
                    source = DesktopDownloadsSourceState(
                        connectedSourceId = connectedSourceId,
                        refreshToken = downloadRefreshToken,
                        downloadCount = cacheStats.downloadCount,
                        maxDownloadBytes = cacheSettings.maxDownloadBytes,
                        offlineDashboard = app.naviamp.ui.NaviampOfflineDashboardUi(
                            audioCacheCount = cacheStats.audioCount,
                            audioCacheBytes = cacheStats.audioBytes,
                            maxAudioCacheBytes = cacheSettings.maxAudioCacheBytes,
                        ),
                        status = downloadStatus,
                        jobs = downloadJobs,
                        keepFavoritesDownloaded = keepDownloadedPolicies.any {
                            it.kind == app.naviamp.domain.cache.KeepDownloadedCollectionKind.Favorites
                        },
                    ),
                    coverArtUrl = coverArtUrl,
                    downloadedTracks = downloadedTracks,
                    onPlayDownloadedTrack = appActions::playDownloadedTrack,
                    onRemoveDownloadedTrack = appActions::removeDownloadedTrack,
                    onCancelDownloadJob = appActions::cancelDownloadJob,
                    onRetryDownloadJob = appActions::retryDownloadJob,
                    onRefreshDownloads = appActions::refreshDownloads,
                    onToggleKeepFavoritesDownloaded = appActions::toggleKeepDownloadedFavorites,
                    onDeleteAllDownloads = appActions::deleteAllDownloads,
                    onAddDownloadedTrackToPlaylist = { download ->
                        playlistsController.openTrackAddToPlaylist(download.track)
                    },
                )
                DesktopAppRoute.Settings -> DesktopSettingsPanel(
                    appColors = appColors,
                    connectionSettings = connection.toConnectionSettingsUi(capabilities),
                    currentSourceId = connectedSourceId,
                    interfaceSettings = interfaceSettings,
                    playback = playbackSettings.toPlaybackSettingsUi(
                        capabilities = capabilities,
                        audioOutputDeviceSelectionAvailable =
                            (playbackEngine as? AudioOutputDevicePlaybackEngine)?.supportsAudioOutputDeviceSelection == true,
                        audioOutputDevices =
                            (playbackEngine as? AudioOutputDevicePlaybackEngine)?.outputDevices().orEmpty(),
                        downloadBytes = cacheStats.downloadBytes,
                    ),
                    cache = cacheSettings.toCacheSettingsUi(cacheStats, capabilities),
                    settingsSyncDirectoryPath = settingsSyncDirectoryPath,
                    settingsSyncAutoExportEnabled = settingsSyncAutoExportEnabled,
                    settingsSyncStatus = settingsSyncStatus,
                    about = about,
                    onServerUrlChanged = { onConnectionFormChanged(connection.form.copy(serverUrl = it)) },
                    onConnectionNameChanged = { onConnectionFormChanged(connection.form.copy(displayName = it)) },
                    onUsernameChanged = { onConnectionFormChanged(connection.form.copy(username = it)) },
                    onPasswordChanged = { onConnectionFormChanged(connection.form.copy(password = it)) },
                    onInsecureSkipTlsVerificationChanged = {
                        onConnectionFormChanged(connection.form.copy(skipTlsVerification = it))
                    },
                    onCustomCertificatePathChanged = {
                        onConnectionFormChanged(connection.form.copy(customCertificatePath = it))
                    },
                    onClientCertificateKeyStorePathChanged = {
                        onConnectionFormChanged(connection.form.copy(clientCertificatePath = it))
                    },
                    onClientCertificateKeyStorePasswordChanged = {
                        onConnectionFormChanged(connection.form.copy(clientCertificatePassword = it))
                    },
                    onSecondaryUrlsChanged = { onConnectionFormChanged(connection.form.copy(secondaryUrls = it)) },
                    onCustomHeadersChanged = { onConnectionFormChanged(connection.form.copy(customHeaders = it)) },
                    onSelectedMusicFolderIdsChanged = {
                        onConnectionFormChanged(connection.form.copy(selectedMusicFolderIds = it))
                    },
                    onConnect = onConnect,
                    onNewConnection = onNewConnection,
                    onEditConnection = onEditConnection,
                    onConnectSavedConnection = onConnectSavedConnection,
                    onDeleteConnection = onDeleteConnection,
                    onCancelConnectionForm = onCancelConnectionForm,
                    onSettingsSyncDirectoryChanged = onSettingsSyncDirectoryChanged,
                    onSettingsSyncDirectorySelectedForImport = onSettingsSyncDirectorySelectedForImport,
                    onSettingsSyncAutoExportChanged = onSettingsSyncAutoExportChanged,
                    onSettingsSyncExport = onSettingsSyncExport,
                    onSettingsSyncImport = onSettingsSyncImport,
                    onInterfaceSettingsChanged = onInterfaceSettingsChanged,
                    onPlaybackSettingsChanged = onPlaybackSettingsChanged,
                    onPlaybackSettingsChangedAndRedownload = onPlaybackSettingsChangedAndRedownload,
                    onCacheSettingsChanged = onCacheSettingsChanged,
                    onOpenStatsForNerds = onOpenStatsForNerds,
                    onClearCache = onClearCache,
                    onClearLibrary = onClearLibrary,
                    onRefreshLibrary = onRefreshLibrary,
                    onResetDatabase = onResetDatabase,
                )
            }
        }
    }
    if (saveSonicPathDialogOpen) {
        SaveQueueAsPlaylistDialog(
            colors = appColors,
            status = null,
            title = "Save path as playlist",
            description = "Save this Sonic Path in order as a server playlist.",
            onDismissRequest = { saveSonicPathDialogOpen = false },
            onSave = { name ->
                onSonicPathSaveAsPlaylist(name)
                saveSonicPathDialogOpen = false
            },
        )
    }
    if (saveSonicMixDialogOpen) {
        SaveQueueAsPlaylistDialog(
            colors = appColors,
            status = null,
            title = "Save mix as playlist",
            description = "Save this Sonic Mix in order as a server playlist.",
            onDismissRequest = { saveSonicMixDialogOpen = false },
            onSave = { name ->
                onSonicMixSaveAsPlaylist(name)
                saveSonicMixDialogOpen = false
            },
        )
    }
}

private fun String?.pageStatusOrNull(): String? =
    this?.takeUnless { status ->
        status.startsWith("Connected to Navidrome", ignoreCase = true) ||
            status.startsWith("Connected to ", ignoreCase = true)
    }
