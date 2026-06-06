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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.naviamp.desktop.settings.CacheSettings
import app.naviamp.desktop.settings.PlaybackSettings
import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.Genre
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Playlist
import app.naviamp.domain.Track
import app.naviamp.domain.cache.LibrarySnapshot
import app.naviamp.domain.cache.StorageCacheStats
import app.naviamp.domain.home.HomeContent
import app.naviamp.domain.home.HomeStationLibrary
import app.naviamp.domain.home.HomeStationRandomAlbum
import app.naviamp.domain.home.parseHomeDecadeStationId
import app.naviamp.domain.home.parseHomeGenreStationId
import app.naviamp.domain.playback.EqualizerPlaybackEngine
import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.domain.popular.SimilarArtistMatch
import app.naviamp.domain.provider.MediaSearchResults
import app.naviamp.domain.source.SavedMediaSource
import app.naviamp.ui.AlbumMixBuilderContent
import app.naviamp.ui.ArtistMixBuilderContent
import app.naviamp.ui.GenreMixBuilderContent
import app.naviamp.ui.SharedAlbumMixBuilderUi
import app.naviamp.ui.SharedArtistMixBuilderUi
import app.naviamp.ui.SharedGenreMixBuilderUi
import app.naviamp.ui.SharedGenreMixItemUi
import app.naviamp.ui.SharedHome
import app.naviamp.ui.SharedHomeStationUi
import app.naviamp.ui.SharedMediaItemUi
import app.naviamp.ui.SharedMixBuilderUi
import app.naviamp.ui.toSharedHomeUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun ColumnScope.DesktopAppRouteContent(
    appColors: DesktopAppColors,
    appRoute: DesktopAppRoute,
    connectionStatus: String?,
    homeStatus: String?,
    homeContent: HomeContent,
    coverArtUrl: (String?) -> String?,
    appActions: DesktopAppActions,
    playlistsController: DesktopPlaylistsController,
    internetRadioController: DesktopInternetRadioController,
    libraryController: DesktopLibraryController,
    searchController: DesktopSearchController,
    smartPlaylistsController: DesktopSmartPlaylistsController,
    coroutineScope: CoroutineScope,
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
    onLibraryTabSelected: (DesktopLibraryTab) -> Unit,
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
    internetRadioStations: List<InternetRadioStation>,
    internetRadioStatus: String?,
    onSaveInternetRadioStation: (InternetRadioStation) -> Unit,
    onDeleteInternetRadioStation: (InternetRadioStation) -> Unit,
    connectedSourceId: String?,
    downloadRefreshToken: Int,
    downloadStatus: String?,
    cacheSettings: CacheSettings,
    cacheStats: StorageCacheStats,
    downloadedTracks: (sourceId: String) -> List<DownloadedTrack>,
    connectionForm: DesktopConnectionFormStateHolder,
    savedMediaSources: List<SavedMediaSource>,
    isConnecting: Boolean,
    playbackSettings: PlaybackSettings,
    playbackEngine: PlaybackEngine,
    onConnect: () -> Unit,
    onNewConnection: () -> Unit,
    onEditConnection: (SavedMediaSource) -> Unit,
    onConnectSavedConnection: (SavedMediaSource) -> Unit,
    onDeleteConnection: (SavedMediaSource) -> Unit,
    onCancelConnectionForm: () -> Unit,
    onPlaybackSettingsChanged: (PlaybackSettings) -> Unit,
    onPlaybackSettingsChangedAndRedownload: (PlaybackSettings) -> Unit,
    onCacheSettingsChanged: (CacheSettings) -> Unit,
    onOpenStatsForNerds: () -> Unit,
    onClearCache: () -> Unit,
    onClearLibrary: () -> Unit,
    onRefreshLibrary: () -> Unit,
    onResetDatabase: () -> Unit,
) {
    val contentScrollState = rememberScrollState()
    val sharedHome = homeContent.toSharedHomeUi(
        coverArtUrl = coverArtUrl,
        playlistTracksById = playlistTracksById,
        canFavoriteAlbums = true,
    )
    val homeAlbums = (
        homeContent.recentlyAddedAlbums +
            homeContent.mixAlbums +
            homeContent.recentAlbums +
            homeContent.frequentAlbums +
            homeContent.randomAlbums +
            homeContent.genreSpotlightAlbums +
            homeContent.decadeAlbums
        ).distinctBy { it.id }
    val homePlaylists = (homeContent.playlists + playlists).distinctBy { it.id }
    val homeInternetRadioStations = (
        homeContent.recentInternetRadioStations +
            homeContent.radioStations +
            internetRadioStations
        ).distinctBy { it.id }

    fun openHomeAlbum(item: SharedMediaItemUi) {
        homeAlbums.firstOrNull { it.id.value == item.id }?.let(appActions::openAlbumDetails)
    }

    fun openHomePlaylist(item: SharedMediaItemUi) {
        homePlaylists.firstOrNull { it.id == item.id }?.let(appActions::openPlaylistDetails)
    }

    fun playHomeRecentRadio(item: SharedMediaItemUi) {
        homeContent.recentRadioStreams.firstOrNull { it.id == item.id }?.let(appActions::playRecentRadio)
    }

    fun playHomeInternetRadio(item: SharedMediaItemUi) {
        homeInternetRadioStations.firstOrNull { it.id == item.id }?.let(internetRadioController::playStation)
    }

    fun playHomeStation(station: SharedHomeStationUi) {
        when (station.id) {
            HomeStationLibrary -> appActions.playLibraryRadio()
            HomeStationRandomAlbum -> appActions.playRandomAlbumRadio()
            else -> {
                parseHomeGenreStationId(station.id)?.let { genre ->
                    appActions.playGenreRadio(Genre(genre))
                    return
                }
                parseHomeDecadeStationId(station.id)?.let { decade ->
                    appActions.playDecadeRadio(decade.fromYear, decade.toYear)
                }
            }
        }
    }

    fun openMixBuilder(builder: SharedMixBuilderUi) {
        when (builder.id) {
            "artist" -> onOpenArtistMixBuilder()
            "album" -> onOpenAlbumMixBuilder()
            "genre" -> onRouteSelected(DesktopAppRoute.GenreMix)
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
                    onAlbumSelected = ::openHomeAlbum,
                    onAlbumFavoriteToggled = { item ->
                        homeAlbums.firstOrNull { it.id.value == item.id }?.let(appActions::toggleAlbumFavorite)
                    },
                    onMixAlbumSelected = ::openHomeAlbum,
                    onPlaylistSelected = ::openHomePlaylist,
                    onRecentRadioSelected = ::playHomeRecentRadio,
                    onInternetRadioStationSelected = ::playHomeInternetRadio,
                    onMixBuilderSelected = ::openMixBuilder,
                    onHomeStationSelected = ::playHomeStation,
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
                    onPlayAlbum = { appActions.playAlbumDetails() },
                    onShuffleAlbum = { appActions.playAlbumDetails(shuffle = true) },
                    onAlbumRadio = {
                        selectedAlbumDetails?.album?.let(appActions::playAlbumRadio)
                            ?: selectedAlbum?.let(appActions::playAlbumRadio)
                    },
                    onDownloadAlbum = {
                        selectedAlbumDetails?.let { appActions.downloadTracks(it.album.title, it.tracks) }
                            ?: selectedAlbum?.let(appActions::downloadAlbum)
                    },
                    onAddAlbumToQueue = {
                        selectedAlbumDetails?.album?.let {
                            playlistsController.addTargetToQueue(AddToPlaylistTarget.AlbumTarget(it))
                        } ?: selectedAlbum?.let {
                            playlistsController.addTargetToQueue(AddToPlaylistTarget.AlbumTarget(it))
                        }
                    },
                    onPlayTrack = { index -> appActions.playAlbumDetails(index = index) },
                    onTrackRadio = appActions::playTrackRadio,
                    onDownloadTrack = appActions::downloadTrack,
                    onAddTrackToQueue = { track ->
                        playlistsController.addTargetToQueue(AddToPlaylistTarget.TrackTarget(track))
                    },
                    onAddAlbumToPlaylist = {
                        selectedAlbumDetails?.album?.let {
                            playlistsController.openAddToPlaylist(AddToPlaylistTarget.AlbumTarget(it))
                        } ?: selectedAlbum?.let {
                            playlistsController.openAddToPlaylist(AddToPlaylistTarget.AlbumTarget(it))
                        }
                    },
                    onAlbumFavoriteToggle = appActions::toggleAlbumFavorite,
                    onAddTrackToPlaylist = { track ->
                        playlistsController.openAddToPlaylist(AddToPlaylistTarget.TrackTarget(track))
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
                    onArtistRadio = appActions::playArtistRadio,
                    onFindSimilarArtists = appActions::findSimilarArtists,
                    onSimilarArtistSelected = appActions::openArtistDetails,
                    onSimilarArtistExternalSelected = appActions::openExternalArtistUrl,
                    onPopularTracksPlay = appActions::playPopularTracks,
                    onPopularTracksRadio = appActions::playPopularTracksRadio,
                    onPopularTracksAddToQueue = appActions::addPopularTracksToQueue,
                    onPopularTrackSelected = { track ->
                        val index = selectedArtistPopularTracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
                        appActions.playPopularTracks(selectedArtistPopularTracks, index)
                    },
                    onPopularTrackAddToQueue = { track ->
                        playlistsController.addTargetToQueue(AddToPlaylistTarget.TrackTarget(track))
                    },
                    onAddArtistToQueue = { artist ->
                        playlistsController.addTargetToQueue(AddToPlaylistTarget.ArtistTarget(artist))
                    },
                    onAddArtistToPlaylist = { artist ->
                        playlistsController.openAddToPlaylist(AddToPlaylistTarget.ArtistTarget(artist))
                    },
                    onArtistFavoriteToggle = appActions::toggleArtistFavorite,
                    onAlbumSelected = appActions::openAlbumDetails,
                    onAlbumRadioSelected = appActions::playAlbumRadio,
                    onAlbumDownloadSelected = appActions::downloadAlbum,
                    onAlbumAddToQueue = { album ->
                        playlistsController.addTargetToQueue(AddToPlaylistTarget.AlbumTarget(album))
                    },
                    onAlbumAddToPlaylist = { album ->
                        playlistsController.openAddToPlaylist(AddToPlaylistTarget.AlbumTarget(album))
                    },
                    onAlbumFavoriteToggle = appActions::toggleAlbumFavorite,
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
                    onPlaylistSelected = appActions::openPlaylistDetails,
                    onPlayPlaylist = appActions::playPlaylist,
                    onRenamePlaylist = onPlaylistRenameRequested,
                    onDeletePlaylist = onPlaylistDeleteRequested,
                    onDownloadPlaylist = appActions::downloadPlaylist,
                    onAddPlaylistToQueue = { playlist ->
                        playlistsController.addTargetToQueue(AddToPlaylistTarget.PlaylistTarget(playlist))
                    },
                    onAddPlaylistToPlaylist = { playlist ->
                        playlistsController.openAddToPlaylist(AddToPlaylistTarget.PlaylistTarget(playlist))
                    },
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
                    onPlayPlaylist = { appActions.playPlaylistDetails() },
                    onShufflePlaylist = { appActions.playPlaylistDetails(shuffle = true) },
                    onRenamePlaylist = { selectedPlaylist?.let(onPlaylistRenameRequested) },
                    onDeletePlaylist = { selectedPlaylist?.let(onPlaylistDeleteRequested) },
                    onDownloadPlaylist = {
                        selectedPlaylist?.let { appActions.downloadTracks(it.name, selectedPlaylistTracks) }
                    },
                    onAddPlaylistToQueue = {
                        selectedPlaylist?.let {
                            playlistsController.addTargetToQueue(AddToPlaylistTarget.PlaylistTarget(it))
                        }
                    },
                    onAddPlaylistToPlaylist = {
                        selectedPlaylist?.let {
                            playlistsController.openAddToPlaylist(AddToPlaylistTarget.PlaylistTarget(it))
                        }
                    },
                    onPlayTrack = { index -> appActions.playPlaylistDetails(index = index) },
                    onTrackRadio = appActions::playTrackRadio,
                    onDownloadTrack = appActions::downloadTrack,
                    onAddTrackToQueue = { track ->
                        playlistsController.addTargetToQueue(AddToPlaylistTarget.TrackTarget(track))
                    },
                    onAddTrackToPlaylist = { track ->
                        playlistsController.openAddToPlaylist(AddToPlaylistTarget.TrackTarget(track))
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
                        onTabSelected = { tab ->
                            onLibraryTabSelected(tab)
                            libraryController.refreshLibrarySnapshot()
                            coroutineScope.launch {
                                libraryListState.scrollToItem(0)
                            }
                        },
                        onLoadMore = libraryController::loadMoreLibraryRows,
                        onJumpToLetter = libraryController::jumpLibraryToLetter,
                        onArtistSelected = appActions::openArtistDetails,
                        onArtistRadioSelected = appActions::playArtistRadio,
                        onArtistAddToQueue = { artist ->
                            playlistsController.addTargetToQueue(AddToPlaylistTarget.ArtistTarget(artist))
                        },
                        onArtistAddToPlaylist = { artist ->
                            playlistsController.openAddToPlaylist(AddToPlaylistTarget.ArtistTarget(artist))
                        },
                        onArtistFavoriteToggle = appActions::toggleArtistFavorite,
                        onAlbumSelected = appActions::openAlbumDetails,
                        onAlbumRadioSelected = appActions::playAlbumRadio,
                        onAlbumDownloadSelected = appActions::downloadAlbum,
                        onAlbumAddToQueue = { album ->
                            playlistsController.addTargetToQueue(AddToPlaylistTarget.AlbumTarget(album))
                        },
                        onAlbumAddToPlaylist = { album ->
                            playlistsController.openAddToPlaylist(AddToPlaylistTarget.AlbumTarget(album))
                        },
                        onAlbumFavoriteToggle = appActions::toggleAlbumFavorite,
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
                    onArtistSelected = appActions::openArtistDetails,
                    onArtistRadioSelected = appActions::playArtistRadio,
                    onArtistAddToQueue = { artist ->
                        playlistsController.addTargetToQueue(AddToPlaylistTarget.ArtistTarget(artist))
                    },
                    onArtistAddToPlaylist = { artist ->
                        playlistsController.openAddToPlaylist(AddToPlaylistTarget.ArtistTarget(artist))
                    },
                    onArtistFavoriteToggle = appActions::toggleArtistFavorite,
                    onAlbumSelected = appActions::openAlbumDetails,
                    onAlbumRadioSelected = appActions::playAlbumRadio,
                    onAlbumDownloadSelected = appActions::downloadAlbum,
                    onAlbumAddToQueue = { album ->
                        playlistsController.addTargetToQueue(AddToPlaylistTarget.AlbumTarget(album))
                    },
                    onAlbumAddToPlaylist = { album ->
                        playlistsController.openAddToPlaylist(AddToPlaylistTarget.AlbumTarget(album))
                    },
                    onAlbumFavoriteToggle = appActions::toggleAlbumFavorite,
                    onTrackSelected = appActions::playSearchTrack,
                    onTrackRadioSelected = { index ->
                        searchResults.tracks.getOrNull(index)?.let(appActions::playTrackRadio)
                    },
                    onTrackDownloadSelected = { index ->
                        searchResults.tracks.getOrNull(index)?.let(appActions::downloadTrack)
                    },
                    onTrackAddToQueue = { index ->
                        searchResults.tracks.getOrNull(index)?.let {
                            playlistsController.addTargetToQueue(AddToPlaylistTarget.TrackTarget(it))
                        }
                    },
                    onTrackAddToPlaylist = { index ->
                        searchResults.tracks.getOrNull(index)?.let {
                            playlistsController.openAddToPlaylist(AddToPlaylistTarget.TrackTarget(it))
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
                DesktopAppRoute.InternetRadio -> Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    DesktopInternetRadioPanel(
                        appColors = appColors,
                        stations = internetRadioStations,
                        status = internetRadioStatus ?: connectionStatus,
                        onPlayStation = internetRadioController::playStation,
                        onSaveStation = onSaveInternetRadioStation,
                        onDeleteStation = onDeleteInternetRadioStation,
                    )
                }
                DesktopAppRoute.Downloads -> DesktopDownloadsRoute(
                    appColors = appColors,
                    connectedSourceId = connectedSourceId,
                    downloadRefreshToken = downloadRefreshToken,
                    downloadCount = cacheStats.downloadCount,
                    downloadBytes = cacheStats.downloadBytes,
                    maxDownloadBytes = cacheSettings.maxDownloadBytes,
                    status = downloadStatus ?: connectionStatus,
                    coverArtUrl = coverArtUrl,
                    downloadedTracks = downloadedTracks,
                    onPlayDownloadedTrack = appActions::playDownloadedTrack,
                    onRemoveDownloadedTrack = appActions::removeDownloadedTrack,
                    onAddDownloadedTrackToPlaylist = { download ->
                        playlistsController.openAddToPlaylist(AddToPlaylistTarget.TrackTarget(download.track))
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
                    savedConnections = savedMediaSources,
                    currentSourceId = connectedSourceId,
                    hasSavedConnection = connectionForm.savedConnectionForLogin != null,
                    isConnectionFormOpen = connectionForm.isOpen,
                    isConnecting = isConnecting,
                    connectionStatus = connectionStatus,
                    playbackSettings = playbackSettings,
                    cacheSettings = cacheSettings,
                    cacheStats = cacheStats,
                    supportsReplayGain = playbackEngine.supportsReplayGain,
                    supportsGapless = playbackEngine.supportsGapless,
                    supportsCrossfade = playbackEngine.supportsCrossfade,
                    supportsEqualizer = (playbackEngine as? EqualizerPlaybackEngine)?.supportsEqualizer == true,
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
                    onConnect = onConnect,
                    onNewConnection = onNewConnection,
                    onEditConnection = onEditConnection,
                    onConnectSavedConnection = onConnectSavedConnection,
                    onDeleteConnection = onDeleteConnection,
                    onCancelConnectionForm = onCancelConnectionForm,
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
}
