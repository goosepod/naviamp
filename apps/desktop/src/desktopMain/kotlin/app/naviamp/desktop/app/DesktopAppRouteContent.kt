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
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Playlist
import app.naviamp.domain.Track
import app.naviamp.domain.cache.LibrarySnapshot
import app.naviamp.domain.cache.StorageCacheStats
import app.naviamp.domain.playback.AudioOutputDevicePlaybackEngine
import app.naviamp.domain.home.HomeContent
import app.naviamp.domain.playback.EqualizerPlaybackEngine
import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.domain.popular.SimilarArtistMatch
import app.naviamp.domain.provider.MediaSearchResults
import app.naviamp.domain.settings.ConnectionFormMusicFolder
import app.naviamp.domain.source.SavedMediaSource
import app.naviamp.domain.sonichome.SonicHomeDiscoveryRows
import app.naviamp.ui.AlbumMixBuilderContent
import app.naviamp.ui.ArtistMixBuilderContent
import app.naviamp.ui.GenreMixBuilderContent
import app.naviamp.ui.NaviampAboutUi
import app.naviamp.ui.SharedAlbumMixBuilderUi
import app.naviamp.ui.SharedArtistMixBuilderUi
import app.naviamp.ui.SharedGenreMixBuilderUi
import app.naviamp.ui.SharedGenreMixItemUi
import app.naviamp.ui.SharedHome
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
import app.naviamp.ui.SaveQueueAsPlaylistDialog
import app.naviamp.ui.StationRowAction
import app.naviamp.ui.SonicMixBuilderContent
import app.naviamp.ui.SonicPathBuilderContent
import app.naviamp.ui.toSharedHomeUi

@Composable
fun ColumnScope.DesktopAppRouteContent(
    appColors: DesktopAppColors,
    appRoute: DesktopAppRoute,
    connectionStatus: String?,
    about: NaviampAboutUi,
    homeStatus: String?,
    homeContent: HomeContent,
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
    selectedAlbum: Album?,
    selectedAlbumDetails: AlbumDetails?,
    selectedAlbumStatus: String?,
    albumDetailBackRoute: DesktopAppRoute,
    selectedArtist: Artist?,
    selectedArtistDetails: ArtistDetails?,
    selectedArtistPopularTracks: List<Track>,
    selectedArtistSimilarArtists: List<SimilarArtistMatch>,
    selectedArtistStatus: String?,
    selectedArtistPopularTracksStatus: String?,
    selectedArtistSimilarArtistsStatus: String?,
    artistDetailBackRoute: DesktopAppRoute,
    playlists: List<Playlist>,
    playlistTracksById: Map<String, List<Track>>,
    recentPlaylistIds: List<String>,
    playlistSortMode: DesktopPlaylistSortMode,
    playlistStatus: String?,
    onPlaylistSortModeChanged: (DesktopPlaylistSortMode) -> Unit,
    onPlaylistRenameRequested: (Playlist) -> Unit,
    onPlaylistDeleteRequested: (Playlist) -> Unit,
    selectedPlaylist: Playlist?,
    selectedPlaylistTracks: List<Track>,
    selectedPlaylistStatus: String?,
    librarySnapshot: LibrarySnapshot,
    libraryQuery: String,
    libraryTab: DesktopLibraryTab,
    libraryStatus: String?,
    isLibrarySyncing: Boolean,
    libraryListState: LazyListState,
    onLibraryQueryChanged: (String) -> Unit,
    searchQuery: String,
    searchResults: MediaSearchResults,
    searchStatus: String?,
    isSearching: Boolean,
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
    internetRadioStations: List<InternetRadioStation>,
    internetRadioStatus: String?,
    onSaveInternetRadioStation: (InternetRadioStation) -> Unit,
    onDeleteInternetRadioStation: (InternetRadioStation) -> Unit,
    connectedSourceId: String?,
    downloadRefreshToken: Int,
    downloadStatus: String?,
    cacheSettings: CacheSettings,
    cacheStats: StorageCacheStats,
    settingsSyncDirectoryPath: String?,
    settingsSyncAutoExportEnabled: Boolean,
    settingsSyncStatus: String?,
    downloadedTracks: (sourceId: String) -> List<DownloadedTrack>,
    connectionForm: DesktopConnectionFormStateHolder,
    availableMusicFolders: List<ConnectionFormMusicFolder>,
    musicFoldersStatus: String?,
    savedMediaSources: List<SavedMediaSource>,
    isConnecting: Boolean,
    playbackSettings: PlaybackSettings,
    playbackEngine: PlaybackEngine,
    supportsSonicSimilarity: Boolean,
    onConnect: () -> Unit,
    onNewConnection: () -> Unit,
    onEditConnection: (SavedMediaSource) -> Unit,
    onConnectSavedConnection: (SavedMediaSource) -> Unit,
    onDeleteConnection: (SavedMediaSource) -> Unit,
    onCancelConnectionForm: () -> Unit,
    onSettingsSyncDirectoryChanged: (String?) -> Unit,
    onSettingsSyncDirectorySelectedForImport: (String) -> Unit,
    onSettingsSyncAutoExportChanged: (Boolean) -> Unit,
    onSettingsSyncExport: () -> Unit,
    onSettingsSyncImport: () -> Unit,
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
        playlistTracksById = playlistTracksById,
        sonicDiscoveryRows = sonicHomeDiscoveryRows,
        canFavoriteAlbums = true,
        showSonicPathBuilder = playbackSettings.sonicSimilarityEnabled && supportsSonicSimilarity,
        showSonicMixBuilder = playbackSettings.sonicSimilarityEnabled && supportsSonicSimilarity,
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
            SharedMediaItemAction.ToggleFavorite -> (selectedAlbumDetails?.album ?: selectedAlbum)?.let {
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
                    selectedPlaylistTracks.distinctBy { track -> track.id }
                } else {
                    selectedPlaylistTracks
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
            SharedTrackGroupAction.Play -> appActions.playPopularTracks(selectedArtistPopularTracks)
            SharedTrackGroupAction.StartRadio -> appActions.playPopularTracksRadio(selectedArtistPopularTracks)
            SharedTrackGroupAction.AddToQueue -> appActions.addPopularTracksToQueue(selectedArtistPopularTracks)
        }
    }

    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .then(
                if (
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
                DesktopAppRoute.Home -> SharedHome(
                    colors = appColors,
                    home = sharedHome,
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
                                SharedTrackRowAction.StartRadio -> appActions.playTrackRadio(track)
                                SharedTrackRowAction.PlayTrackRadioNext -> appActions.playTrackRadioNext(track)
                                SharedTrackRowAction.AddTrackRadioToQueue -> appActions.addTrackRadioToQueue(track)
                                SharedTrackRowAction.Download -> appActions.downloadTrack(track)
                                SharedTrackRowAction.AddToQueue -> playlistsController.addTrackToQueue(track)
                                SharedTrackRowAction.AddToPlaylist -> playlistsController.openTrackAddToPlaylist(track)
                                SharedTrackRowAction.CreatePlaylistAndAdd -> Unit
                            }
                        }
                    },
                )
                DesktopAppRoute.AlbumDetail -> DesktopAlbumDetailPanel(
                    appColors = appColors,
                    album = selectedAlbum,
                    albumDetails = selectedAlbumDetails,
                    status = selectedAlbumStatus,
                    coverArtUrl = (
                        selectedAlbumDetails?.album?.coverArtId ?: selectedAlbum?.coverArtId
                        )?.let(coverArtUrl),
                    popularTrackIds = selectedArtistPopularTracks.map { it.id.value }.toSet(),
                    onBack = { onRouteSelected(albumDetailBackRoute) },
                    onAlbumAction = { request -> handleSelectedAlbumMediaAction(request.action) },
                    onTrackAction = { request ->
                        val index = selectedAlbumDetails?.tracks?.indexOfFirst { track -> track.id.value == request.track.id } ?: -1
                        val track = selectedAlbumDetails?.tracks?.getOrNull(index)
                        if (track != null) {
                            when (request.action) {
                                SharedTrackRowAction.Select -> appActions.playAlbumDetails(index = index)
                                SharedTrackRowAction.StartRadio -> appActions.playTrackRadio(track)
                                SharedTrackRowAction.PlayTrackRadioNext -> appActions.playTrackRadioNext(track)
                                SharedTrackRowAction.AddTrackRadioToQueue -> appActions.addTrackRadioToQueue(track)
                                SharedTrackRowAction.Download -> appActions.downloadTrack(track)
                                SharedTrackRowAction.AddToQueue -> playlistsController.addTrackToQueue(track)
                                SharedTrackRowAction.AddToPlaylist -> playlistsController.openTrackAddToPlaylist(track)
                                SharedTrackRowAction.CreatePlaylistAndAdd -> Unit
                            }
                        }
                    },
                    onArtistSelected = { track ->
                        appActions.openTrackArtistDetails(track, backRouteOverride = DesktopAppRoute.AlbumDetail)
                    },
                )
                DesktopAppRoute.ArtistDetail -> DesktopArtistDetailPanel(
                    appColors = appColors,
                    artist = selectedArtist,
                    artistDetails = selectedArtistDetails,
                    popularTracks = selectedArtistPopularTracks,
                    similarArtists = selectedArtistSimilarArtists,
                    status = selectedArtistStatus,
                    popularTracksStatus = selectedArtistPopularTracksStatus,
                    similarArtistsStatus = selectedArtistSimilarArtistsStatus,
                    coverArtUrl = coverArtUrl,
                    onBack = appActions::closeArtistDetails,
                    onSimilarArtistSelected = appActions::openArtistDetails,
                    onSimilarArtistExternalSelected = appActions::openExternalArtistUrl,
                    onArtistAction = { request ->
                        (selectedArtistDetails?.artist ?: selectedArtist)
                            ?.let { artist -> handleArtistMediaAction(request.action, artist) }
                    },
                    onPopularTracksAction = ::handlePopularTracksGroupAction,
                    onPopularTrackAction = { request ->
                        selectedArtistPopularTracks
                            .firstOrNull { track -> track.id.value == request.track.id }
                            ?.let { track ->
                                when (request.action) {
                                    SharedTrackRowAction.Select -> appActions.playSelectedPopularTrack(track)
                                    SharedTrackRowAction.StartRadio -> appActions.playPopularTracksRadio(listOf(track))
                                    SharedTrackRowAction.PlayTrackRadioNext -> appActions.playTrackRadioNext(track)
                                    SharedTrackRowAction.AddTrackRadioToQueue -> appActions.addTrackRadioToQueue(track)
                                    SharedTrackRowAction.AddToQueue -> playlistsController.addTrackToQueue(track)
                                    SharedTrackRowAction.Download,
                                    SharedTrackRowAction.AddToPlaylist,
                                    SharedTrackRowAction.CreatePlaylistAndAdd,
                                    -> Unit
                                }
                            }
                    },
                    onAlbumAction = { request ->
                        selectedArtistDetails?.albums
                            ?.firstOrNull { album -> album.id.value == request.item.id }
                            ?.let { album -> handleAlbumMediaAction(request.action, album) }
                    },
                )
                DesktopAppRoute.Playlists -> DesktopPlaylistsPanel(
                    appColors = appColors,
                    playlists = playlists,
                    playlistTracks = { playlist -> playlistTracksById[playlist.id].orEmpty() },
                    recentPlaylistIds = recentPlaylistIds,
                    sortMode = playlistSortMode,
                    status = playlistStatus ?: connectionStatus,
                    coverArtUrl = coverArtUrl,
                    onSortModeChanged = onPlaylistSortModeChanged,
                    onPlaylistAction = { request ->
                        playlists
                            .firstOrNull { playlist -> playlist.id == request.item.id }
                            ?.let { playlist -> handlePlaylistMediaAction(request.action, playlist, request.shuffle) }
                    },
                    onRefreshPlaylists = { playlistsController.refreshPlaylists(useCache = false) },
                    onSmartPlaylistSave = smartPlaylistsController::saveSmartPlaylist,
                    onSmartPlaylistUpdate = smartPlaylistsController::updateSmartPlaylist,
                    onSmartPlaylistLoad = smartPlaylistsController::loadSmartPlaylistDefinition,
                )
                DesktopAppRoute.PlaylistDetail -> DesktopPlaylistDetailPanel(
                    appColors = appColors,
                    playlist = selectedPlaylist,
                    tracks = selectedPlaylistTracks,
                    status = selectedPlaylistStatus ?: playlistStatus,
                    playlistCoverArtUrl = selectedPlaylist?.coverArtId?.let(coverArtUrl),
                    coverArtUrl = coverArtUrl,
                    onBack = { onRouteSelected(DesktopAppRoute.Playlists) },
                    onPlaylistAction = { request -> handleSelectedPlaylistMediaAction(request) },
                    onTrackAction = { request ->
                        val index = selectedPlaylistTracks.indexOfFirst { track -> track.id.value == request.track.id }
                        val track = selectedPlaylistTracks.getOrNull(index)
                        if (track != null) {
                            when (request.action) {
                                SharedTrackRowAction.Select -> appActions.playPlaylistDetails(index = index)
                                SharedTrackRowAction.StartRadio -> appActions.playTrackRadio(track)
                                SharedTrackRowAction.PlayTrackRadioNext -> appActions.playTrackRadioNext(track)
                                SharedTrackRowAction.AddTrackRadioToQueue -> appActions.addTrackRadioToQueue(track)
                                SharedTrackRowAction.Download -> appActions.downloadTrack(track)
                                SharedTrackRowAction.AddToQueue -> playlistsController.addTrackToQueue(track)
                                SharedTrackRowAction.AddToPlaylist -> playlistsController.openTrackAddToPlaylist(track)
                                SharedTrackRowAction.CreatePlaylistAndAdd -> Unit
                            }
                        }
                    },
                )
                DesktopAppRoute.Library -> {
                    DesktopLibraryListLoadMoreEffect(
                        selectedTab = libraryTab,
                        snapshot = librarySnapshot,
                        listState = libraryListState,
                        onLoadMore = libraryController::loadMoreLibraryRows,
                    )
                    DesktopLibraryPanel(
                        appColors = appColors,
                        snapshot = librarySnapshot,
                        query = libraryQuery,
                        selectedTab = libraryTab,
                        status = libraryStatus ?: connectionStatus,
                        isSyncing = isLibrarySyncing,
                        listState = libraryListState,
                        coverArtUrl = coverArtUrl,
                        onQueryChanged = onLibraryQueryChanged,
                        onTabSelected = libraryController::selectLibraryTab,
                        onLoadMore = libraryController::loadMoreLibraryRows,
                        onJumpToLetter = libraryController::jumpLibraryToLetter,
                        onMediaItemAction = { request ->
                            librarySnapshot.artists
                                .firstOrNull { artist -> artist.id.value == request.item.id }
                                ?.let { artist -> handleArtistMediaAction(request.action, artist) }
                                ?: librarySnapshot.albums
                                    .firstOrNull { album -> album.id.value == request.item.id }
                                    ?.let { album -> handleAlbumMediaAction(request.action, album) }
                        },
                        onRefreshLibrary = { libraryController.startLibrarySync(force = true) },
                    )
                }
                DesktopAppRoute.Search -> DesktopSearchPanel(
                    appColors = appColors,
                    query = searchQuery,
                    results = searchResults,
                    status = searchStatus,
                    isSearching = isSearching,
                    coverArtUrl = coverArtUrl,
                    onQueryChanged = searchController::updateQuery,
                    onClearSearch = searchController::clearSearch,
                    onMediaItemAction = { request ->
                        searchResults.artists
                            .firstOrNull { artist -> artist.id.value == request.item.id }
                            ?.let { artist -> handleArtistMediaAction(request.action, artist) }
                            ?: searchResults.albums
                                .firstOrNull { album -> album.id.value == request.item.id }
                                ?.let { album -> handleAlbumMediaAction(request.action, album) }
                    },
                    onTrackAction = { request ->
                        val index = searchResults.tracks.indexOfFirst { track -> track.id.value == request.track.id }
                        val track = searchResults.tracks.getOrNull(index)
                        if (track != null) {
                            when (request.action) {
                                SharedTrackRowAction.Select -> appActions.playSearchTrack(index)
                                SharedTrackRowAction.StartRadio -> appActions.playSearchTrackRadio(index)
                                SharedTrackRowAction.PlayTrackRadioNext -> appActions.playTrackRadioNext(track)
                                SharedTrackRowAction.AddTrackRadioToQueue -> appActions.addTrackRadioToQueue(track)
                                SharedTrackRowAction.Download -> appActions.downloadSearchTrack(index)
                                SharedTrackRowAction.AddToQueue -> appActions.addSearchTrackToQueue(index)
                                SharedTrackRowAction.AddToPlaylist -> appActions.openSearchTrackAddToPlaylist(index)
                                SharedTrackRowAction.CreatePlaylistAndAdd -> Unit
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
                        stations = internetRadioStations,
                        status = internetRadioStatus ?: connectionStatus,
                        onStationAction = { request ->
                            internetRadioStations.firstOrNull { station -> station.id == request.station.id }?.let { station ->
                                when (request.action) {
                                    StationRowAction.Select -> internetRadioController.playStation(station)
                                    StationRowAction.Edit -> Unit
                                    StationRowAction.Delete -> onDeleteInternetRadioStation(station)
                                }
                            }
                        },
                        onSaveStation = onSaveInternetRadioStation,
                        onRefreshStations = internetRadioController::refreshStations,
                    )
                }
                DesktopAppRoute.Downloads -> DesktopDownloadsRoute(
                    appColors = appColors,
                    connectedSourceId = connectedSourceId,
                    downloadRefreshToken = downloadRefreshToken,
                    downloadCount = cacheStats.downloadCount,
                    downloadBytes = cacheStats.downloadBytes,
                    maxDownloadBytes = cacheSettings.maxDownloadBytes,
                    audioCacheCount = cacheStats.audioCount,
                    audioCacheBytes = cacheStats.audioBytes,
                    maxAudioCacheBytes = cacheSettings.maxAudioCacheBytes,
                    status = downloadStatus ?: connectionStatus,
                    coverArtUrl = coverArtUrl,
                    downloadedTracks = downloadedTracks,
                    onPlayDownloadedTrack = appActions::playDownloadedTrack,
                    onRemoveDownloadedTrack = appActions::removeDownloadedTrack,
                    onAddDownloadedTrackToPlaylist = { download ->
                        playlistsController.openTrackAddToPlaylist(download.track)
                    },
                )
                DesktopAppRoute.Settings -> DesktopSettingsPanel(
                    appColors = appColors,
                    serverUrl = connectionForm.serverUrl,
                    connectionName = connectionForm.connectionName,
                    username = connectionForm.username,
                    password = connectionForm.password,
                    insecureSkipTlsVerification = connectionForm.insecureSkipTlsVerification,
                    customCertificatePath = connectionForm.customCertificatePath,
                    clientCertificateKeyStorePath = connectionForm.clientCertificateKeyStorePath,
                    clientCertificateKeyStorePassword = connectionForm.clientCertificateKeyStorePassword,
                    secondaryUrls = connectionForm.secondaryUrls,
                    customHeaders = connectionForm.customHeaders,
                    selectedMusicFolderIds = connectionForm.selectedMusicFolderIds,
                    availableMusicFolders = availableMusicFolders,
                    musicFoldersStatus = musicFoldersStatus,
                    savedConnections = savedMediaSources,
                    currentSourceId = connectedSourceId,
                    hasSavedConnection = connectionForm.savedConnectionForLogin != null,
                    isConnectionFormOpen = connectionForm.isOpen,
                    isConnecting = isConnecting,
                    connectionStatus = connectionStatus,
                    playbackSettings = playbackSettings,
                    cacheSettings = cacheSettings,
                    cacheStats = cacheStats,
                    settingsSyncDirectoryPath = settingsSyncDirectoryPath,
                    settingsSyncAutoExportEnabled = settingsSyncAutoExportEnabled,
                    settingsSyncStatus = settingsSyncStatus,
                    about = about,
                    supportsReplayGain = playbackEngine.supportsReplayGain,
                    supportsGapless = playbackEngine.supportsGapless,
                    supportsCrossfade = playbackEngine.supportsCrossfade,
                    supportsEqualizer = (playbackEngine as? EqualizerPlaybackEngine)?.supportsEqualizer == true,
                    supportsAudioOutputDeviceSelection =
                        (playbackEngine as? AudioOutputDevicePlaybackEngine)?.supportsAudioOutputDeviceSelection == true,
                    audioOutputDevices =
                        (playbackEngine as? AudioOutputDevicePlaybackEngine)?.outputDevices().orEmpty(),
                    supportsSonicSimilarity = supportsSonicSimilarity,
                    onServerUrlChanged = connectionForm::updateServerUrl,
                    onConnectionNameChanged = { connectionForm.connectionName = it },
                    onUsernameChanged = connectionForm::updateUsername,
                    onPasswordChanged = { connectionForm.password = it },
                    onInsecureSkipTlsVerificationChanged = {
                        connectionForm.insecureSkipTlsVerification = it
                    },
                    onCustomCertificatePathChanged = {
                        connectionForm.customCertificatePath = it
                    },
                    onClientCertificateKeyStorePathChanged = {
                        connectionForm.clientCertificateKeyStorePath = it
                    },
                    onClientCertificateKeyStorePasswordChanged = {
                        connectionForm.clientCertificateKeyStorePassword = it
                    },
                    onSecondaryUrlsChanged = {
                        connectionForm.secondaryUrls = it
                    },
                    onCustomHeadersChanged = {
                        connectionForm.customHeaders = it
                    },
                    onSelectedMusicFolderIdsChanged = {
                        connectionForm.selectedMusicFolderIds = it
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
