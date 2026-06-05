package app.naviamp.android

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.naviamp.android.playback.AndroidBassLoadReport
import app.naviamp.android.playback.AndroidPlaybackEngine
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Playlist
import app.naviamp.domain.Track
import app.naviamp.domain.playback.nextRepeatMode
import app.naviamp.domain.provider.allKnownTracks
import app.naviamp.domain.queue.RepeatMode
import app.naviamp.domain.settings.ConnectionFormState
import app.naviamp.domain.settings.PlaybackSettings
import app.naviamp.domain.settings.VisualizerSettings
import app.naviamp.domain.settings.streamQualityForNetwork
import app.naviamp.domain.smartplaylist.SmartPlaylistDefinition
import app.naviamp.ui.SharedTrackRowUi
import app.naviamp.ui.NaviampDiagnosticsUi
import app.naviamp.ui.NaviampDownloadedTrackUi
import app.naviamp.ui.NaviampLibrarySyncStatusUi
import app.naviamp.ui.NaviampNowPlayingItemUi
import app.naviamp.ui.NaviampPlaylistChoiceUi
import app.naviamp.ui.NaviampSharedAppShell
import app.naviamp.ui.NaviampVisualizer
import app.naviamp.ui.NowPlayingUi
import app.naviamp.ui.SharedAlbumDetailUi
import app.naviamp.ui.SharedArtistDetailUi
import app.naviamp.ui.SharedHomeStationUi
import app.naviamp.ui.SharedHomeUi
import app.naviamp.ui.SharedMediaItemUi
import app.naviamp.ui.SharedPlaylistDetailUi
import app.naviamp.ui.SharedPlaylistSortMode
import app.naviamp.ui.SharedRoute
import app.naviamp.ui.SharedSearchResultsUi
import app.naviamp.ui.SharedSimilarArtistUi
import app.naviamp.ui.toNaviampRoute

data class AndroidAppShellUiState(
    val modifier: Modifier,
    val status: String,
    val serverVersion: String?,
    val connected: Boolean,
    val editingConnection: Boolean,
    val restoringConnection: Boolean,
    val connectionForm: ConnectionFormState,
    val playbackSettings: PlaybackSettings,
    val diagnostics: NaviampDiagnosticsUi,
    val supportsReplayGain: Boolean,
    val supportsGapless: Boolean,
    val supportsCrossfade: Boolean,
    val showMobileNetworkQuality: Boolean,
    val selectedVisualizer: NaviampVisualizer,
    val visualizerBandsProvider: () -> List<Float>,
    val query: String,
    val home: SharedHomeUi,
    val searchResults: SharedSearchResultsUi,
    val libraryArtists: List<SharedMediaItemUi>,
    val libraryQuery: String,
    val librarySyncStatus: NaviampLibrarySyncStatusUi,
    val downloads: List<NaviampDownloadedTrackUi>,
    val downloadBytes: Long,
    val maxDownloadBytes: Long,
    val downloadStatus: String?,
    val playlistItems: List<SharedMediaItemUi>,
    val recentPlaylistIds: List<String>,
    val playlistSortMode: SharedPlaylistSortMode,
    val playlistChoices: List<NaviampPlaylistChoiceUi>,
    val playlistActionStatus: String?,
    val radioStations: List<InternetRadioStation>,
    val albumDetail: SharedAlbumDetailUi?,
    val artistDetail: SharedArtistDetailUi?,
    val playlistDetail: SharedPlaylistDetailUi?,
    val nowPlaying: NowPlayingUi?,
    val nowPlayingOpen: Boolean,
    val selectedRoute: SharedRoute,
)

data class AndroidAppShellActions(
    val onRouteSelected: (SharedRoute) -> Unit,
    val onConnectionFormChanged: (ConnectionFormState) -> Unit,
    val onConnect: () -> Unit,
    val onEditConnection: () -> Unit,
    val onCancelEditConnection: () -> Unit,
    val onPlaybackSettingsChanged: (PlaybackSettings) -> Unit,
    val onPlaybackSettingsChangedAndRedownload: (PlaybackSettings) -> Unit,
    val onClearCache: () -> Unit,
    val onClearLibrary: () -> Unit,
    val onResetDatabase: () -> Unit,
    val onQueryChanged: (String) -> Unit,
    val onSearch: () -> Unit,
    val onClearSearch: () -> Unit,
    val onLibraryQueryChanged: (String) -> Unit,
    val onRefreshLibrary: () -> Unit,
    val onTrackSelected: (SharedTrackRowUi) -> Unit,
    val onDownloadedTrackSelected: (NaviampDownloadedTrackUi) -> Unit,
    val onDownloadedTrackAddToPlaylist: (NaviampDownloadedTrackUi, NaviampPlaylistChoiceUi?) -> Unit,
    val onDownloadedTrackCreatePlaylistAndAdd: (NaviampDownloadedTrackUi, String) -> Unit,
    val onRemoveDownload: (NaviampDownloadedTrackUi) -> Unit,
    val onAlbumSelected: (SharedMediaItemUi) -> Unit,
    val onMixAlbumSelected: (SharedMediaItemUi) -> Unit,
    val onAlbumPlay: (SharedAlbumDetailUi, Boolean) -> Unit,
    val onAlbumTrackSelected: (SharedTrackRowUi) -> Unit,
    val onAlbumRadio: (SharedAlbumDetailUi) -> Unit,
    val onAlbumAddToQueue: (SharedAlbumDetailUi) -> Unit,
    val onAlbumDownload: (SharedAlbumDetailUi) -> Unit,
    val onAlbumAddToPlaylist: (SharedAlbumDetailUi, NaviampPlaylistChoiceUi?) -> Unit,
    val onAlbumCreatePlaylistAndAdd: (SharedAlbumDetailUi, String) -> Unit,
    val onAlbumTrackDownload: (SharedTrackRowUi) -> Unit,
    val onAlbumTrackAddToPlaylist: (SharedTrackRowUi, NaviampPlaylistChoiceUi?) -> Unit,
    val onAlbumTrackCreatePlaylistAndAdd: (SharedTrackRowUi, String) -> Unit,
    val onArtistRadio: (SharedArtistDetailUi) -> Unit,
    val onArtistShuffle: (SharedArtistDetailUi) -> Unit,
    val onArtistAddToQueue: (SharedArtistDetailUi) -> Unit,
    val onArtistAddToPlaylist: (SharedArtistDetailUi, NaviampPlaylistChoiceUi?) -> Unit,
    val onArtistCreatePlaylistAndAdd: (SharedArtistDetailUi, String) -> Unit,
    val onArtistPopularPlay: (SharedArtistDetailUi) -> Unit,
    val onArtistPopularRadio: (SharedArtistDetailUi) -> Unit,
    val onArtistPopularTrackSelected: (SharedTrackRowUi) -> Unit,
    val onArtistPopularAddToQueue: (SharedArtistDetailUi) -> Unit,
    val onArtistPopularTrackAddToQueue: (SharedTrackRowUi) -> Unit,
    val onArtistPopularTrackDownload: (SharedTrackRowUi) -> Unit,
    val onArtistPopularTrackAddToPlaylist: (SharedTrackRowUi, NaviampPlaylistChoiceUi?) -> Unit,
    val onArtistPopularTrackCreatePlaylistAndAdd: (SharedTrackRowUi, String) -> Unit,
    val onFindSimilarArtists: (SharedArtistDetailUi) -> Unit,
    val onSimilarArtistSelected: (SharedSimilarArtistUi) -> Unit,
    val onSimilarArtistExternalSelected: (String) -> Unit,
    val onArtistSelected: (SharedMediaItemUi) -> Unit,
    val onArtistAlbumRadio: (SharedMediaItemUi) -> Unit,
    val onArtistAlbumDownload: (SharedMediaItemUi) -> Unit,
    val onArtistAlbumAddToQueue: (SharedMediaItemUi) -> Unit,
    val onArtistAlbumAddToPlaylist: (SharedMediaItemUi, NaviampPlaylistChoiceUi?) -> Unit,
    val onArtistAlbumCreatePlaylistAndAdd: (SharedMediaItemUi, String) -> Unit,
    val onPlaylistSelected: (SharedMediaItemUi) -> Unit,
    val onPlaylistSortModeChanged: (SharedPlaylistSortMode) -> Unit,
    val onPlaylistPlay: (SharedMediaItemUi, Boolean) -> Unit,
    val onPlaylistItemAddToQueue: (SharedMediaItemUi) -> Unit,
    val onPlaylistItemAddToPlaylist: (SharedMediaItemUi, NaviampPlaylistChoiceUi?) -> Unit,
    val onPlaylistItemCreatePlaylistAndAdd: (SharedMediaItemUi, String) -> Unit,
    val onPlaylistAddToQueue: (SharedPlaylistDetailUi) -> Unit,
    val onPlaylistDownload: (SharedMediaItemUi) -> Unit,
    val onPlaylistAddToPlaylist: (SharedPlaylistDetailUi, NaviampPlaylistChoiceUi?) -> Unit,
    val onPlaylistCreatePlaylistAndAdd: (SharedPlaylistDetailUi, String) -> Unit,
    val onPlaylistRename: (SharedMediaItemUi, String) -> Unit,
    val onPlaylistDelete: (SharedMediaItemUi) -> Unit,
    val onSmartPlaylistSave: suspend (SmartPlaylistDefinition) -> Unit,
    val onSmartPlaylistUpdate: suspend (SharedMediaItemUi, SmartPlaylistDefinition) -> Unit,
    val onSmartPlaylistLoad: suspend (SharedMediaItemUi) -> SmartPlaylistDefinition,
    val onPlaylistBack: () -> Unit,
    val onPlaylistTrackSelected: (SharedTrackRowUi) -> Unit,
    val onTrackAddToQueue: (SharedTrackRowUi) -> Unit,
    val onRecentRadioSelected: (SharedMediaItemUi) -> Unit,
    val onRadioStationSelected: (InternetRadioStation) -> Unit,
    val onRadioStationSave: (InternetRadioStation) -> Unit,
    val onRadioStationDelete: (InternetRadioStation) -> Unit,
    val onHomeStationSelected: (SharedHomeStationUi) -> Unit,
    val onOpenNowPlaying: () -> Unit,
    val onCloseNowPlaying: () -> Unit,
    val onPause: () -> Unit,
    val onResume: () -> Unit,
    val onStop: () -> Unit,
    val onPrevious: () -> Unit,
    val onNext: () -> Unit,
    val onSeek: (Double) -> Unit,
    val onVolumeChanged: (Int) -> Unit,
    val onToggleShuffle: () -> Unit,
    val onCycleRepeatMode: () -> Unit,
    val onToggleLyrics: () -> Unit,
    val onToggleVisualizer: () -> Unit,
    val onVisualizerSelected: (NaviampVisualizer) -> Unit,
    val onTrackRadio: () -> Unit,
    val onAddToPlaylist: (NaviampPlaylistChoiceUi?) -> Unit,
    val onCreatePlaylistAndAdd: (String) -> Unit,
    val onDownloadTrack: () -> Unit,
    val onGoToAlbum: () -> Unit,
    val onGoToArtist: () -> Unit,
    val onQueueItemRadio: (NaviampNowPlayingItemUi) -> Unit,
    val onQueueItemAddToPlaylist: (NaviampNowPlayingItemUi, NaviampPlaylistChoiceUi?) -> Unit,
    val onQueueItemCreatePlaylistAndAdd: (NaviampNowPlayingItemUi, String) -> Unit,
    val onQueueItemDownload: (NaviampNowPlayingItemUi) -> Unit,
    val onToggleFavorite: () -> Unit,
    val onRatingSelected: (Int?) -> Unit,
)

@Composable
fun AndroidAppShellContent(
    state: AndroidAppShellUiState,
    actions: AndroidAppShellActions,
) {
    NaviampSharedAppShell(
        modifier = state.modifier,
        status = state.status,
        serverVersion = state.serverVersion,
        connected = state.connected,
        editingConnection = state.editingConnection,
        restoringConnection = state.restoringConnection,
        connectionForm = state.connectionForm,
        playbackSettings = state.playbackSettings,
        diagnostics = state.diagnostics,
        supportsReplayGain = state.supportsReplayGain,
        supportsGapless = state.supportsGapless,
        supportsCrossfade = state.supportsCrossfade,
        showMobileNetworkQuality = state.showMobileNetworkQuality,
        selectedVisualizer = state.selectedVisualizer,
        visualizerBandsProvider = state.visualizerBandsProvider,
        query = state.query,
        home = state.home,
        searchResults = state.searchResults,
        libraryArtists = state.libraryArtists,
        libraryQuery = state.libraryQuery,
        librarySyncStatus = state.librarySyncStatus,
        downloads = state.downloads,
        downloadBytes = state.downloadBytes,
        maxDownloadBytes = state.maxDownloadBytes,
        downloadStatus = state.downloadStatus,
        playlistItems = state.playlistItems,
        recentPlaylistIds = state.recentPlaylistIds,
        playlistSortMode = state.playlistSortMode,
        playlistChoices = state.playlistChoices,
        playlistActionStatus = state.playlistActionStatus,
        radioStations = state.radioStations,
        albumDetail = state.albumDetail,
        artistDetail = state.artistDetail,
        playlistDetail = state.playlistDetail,
        nowPlaying = state.nowPlaying,
        nowPlayingOpen = state.nowPlayingOpen,
        selectedRoute = state.selectedRoute,
        onRouteSelected = actions.onRouteSelected,
        onConnectionFormChanged = actions.onConnectionFormChanged,
        onConnect = actions.onConnect,
        onEditConnection = actions.onEditConnection,
        onCancelEditConnection = actions.onCancelEditConnection,
        onPlaybackSettingsChanged = actions.onPlaybackSettingsChanged,
        onPlaybackSettingsChangedAndRedownload = actions.onPlaybackSettingsChangedAndRedownload,
        onClearCache = actions.onClearCache,
        onClearLibrary = actions.onClearLibrary,
        onResetDatabase = actions.onResetDatabase,
        onQueryChanged = actions.onQueryChanged,
        onSearch = actions.onSearch,
        onClearSearch = actions.onClearSearch,
        onLibraryQueryChanged = actions.onLibraryQueryChanged,
        onRefreshLibrary = actions.onRefreshLibrary,
        onTrackSelected = actions.onTrackSelected,
        onDownloadedTrackSelected = actions.onDownloadedTrackSelected,
        onDownloadedTrackAddToPlaylist = actions.onDownloadedTrackAddToPlaylist,
        onDownloadedTrackCreatePlaylistAndAdd = actions.onDownloadedTrackCreatePlaylistAndAdd,
        onRemoveDownload = actions.onRemoveDownload,
        onAlbumSelected = actions.onAlbumSelected,
        onMixAlbumSelected = actions.onMixAlbumSelected,
        onAlbumPlay = actions.onAlbumPlay,
        onAlbumTrackSelected = actions.onAlbumTrackSelected,
        onAlbumRadio = actions.onAlbumRadio,
        onAlbumAddToQueue = actions.onAlbumAddToQueue,
        onAlbumDownload = actions.onAlbumDownload,
        onAlbumAddToPlaylist = actions.onAlbumAddToPlaylist,
        onAlbumCreatePlaylistAndAdd = actions.onAlbumCreatePlaylistAndAdd,
        onAlbumTrackDownload = actions.onAlbumTrackDownload,
        onAlbumTrackAddToPlaylist = actions.onAlbumTrackAddToPlaylist,
        onAlbumTrackCreatePlaylistAndAdd = actions.onAlbumTrackCreatePlaylistAndAdd,
        onArtistRadio = actions.onArtistRadio,
        onArtistShuffle = actions.onArtistShuffle,
        onArtistAddToQueue = actions.onArtistAddToQueue,
        onArtistAddToPlaylist = actions.onArtistAddToPlaylist,
        onArtistCreatePlaylistAndAdd = actions.onArtistCreatePlaylistAndAdd,
        onArtistPopularPlay = actions.onArtistPopularPlay,
        onArtistPopularRadio = actions.onArtistPopularRadio,
        onArtistPopularTrackSelected = actions.onArtistPopularTrackSelected,
        onArtistPopularAddToQueue = actions.onArtistPopularAddToQueue,
        onArtistPopularTrackAddToQueue = actions.onArtistPopularTrackAddToQueue,
        onArtistPopularTrackDownload = actions.onArtistPopularTrackDownload,
        onArtistPopularTrackAddToPlaylist = actions.onArtistPopularTrackAddToPlaylist,
        onArtistPopularTrackCreatePlaylistAndAdd = actions.onArtistPopularTrackCreatePlaylistAndAdd,
        onFindSimilarArtists = actions.onFindSimilarArtists,
        onSimilarArtistSelected = actions.onSimilarArtistSelected,
        onSimilarArtistExternalSelected = actions.onSimilarArtistExternalSelected,
        onArtistSelected = actions.onArtistSelected,
        onArtistAlbumRadio = actions.onArtistAlbumRadio,
        onArtistAlbumDownload = actions.onArtistAlbumDownload,
        onArtistAlbumAddToQueue = actions.onArtistAlbumAddToQueue,
        onArtistAlbumAddToPlaylist = actions.onArtistAlbumAddToPlaylist,
        onArtistAlbumCreatePlaylistAndAdd = actions.onArtistAlbumCreatePlaylistAndAdd,
        onPlaylistSelected = actions.onPlaylistSelected,
        onPlaylistSortModeChanged = actions.onPlaylistSortModeChanged,
        onPlaylistPlay = actions.onPlaylistPlay,
        onPlaylistItemAddToQueue = actions.onPlaylistItemAddToQueue,
        onPlaylistItemAddToPlaylist = actions.onPlaylistItemAddToPlaylist,
        onPlaylistItemCreatePlaylistAndAdd = actions.onPlaylistItemCreatePlaylistAndAdd,
        onPlaylistAddToQueue = actions.onPlaylistAddToQueue,
        onPlaylistDownload = actions.onPlaylistDownload,
        onPlaylistAddToPlaylist = actions.onPlaylistAddToPlaylist,
        onPlaylistCreatePlaylistAndAdd = actions.onPlaylistCreatePlaylistAndAdd,
        onPlaylistRename = actions.onPlaylistRename,
        onPlaylistDelete = actions.onPlaylistDelete,
        onSmartPlaylistSave = actions.onSmartPlaylistSave,
        onSmartPlaylistUpdate = actions.onSmartPlaylistUpdate,
        onSmartPlaylistLoad = actions.onSmartPlaylistLoad,
        onPlaylistBack = actions.onPlaylistBack,
        onPlaylistTrackSelected = actions.onPlaylistTrackSelected,
        onTrackAddToQueue = actions.onTrackAddToQueue,
        onRecentRadioSelected = actions.onRecentRadioSelected,
        onRadioStationSelected = actions.onRadioStationSelected,
        onRadioStationSave = actions.onRadioStationSave,
        onRadioStationDelete = actions.onRadioStationDelete,
        onHomeStationSelected = actions.onHomeStationSelected,
        onOpenNowPlaying = actions.onOpenNowPlaying,
        onCloseNowPlaying = actions.onCloseNowPlaying,
        onPause = actions.onPause,
        onResume = actions.onResume,
        onStop = actions.onStop,
        onPrevious = actions.onPrevious,
        onNext = actions.onNext,
        onSeek = actions.onSeek,
        onVolumeChanged = actions.onVolumeChanged,
        onToggleShuffle = actions.onToggleShuffle,
        onCycleRepeatMode = actions.onCycleRepeatMode,
        onToggleLyrics = actions.onToggleLyrics,
        onToggleVisualizer = actions.onToggleVisualizer,
        onVisualizerSelected = actions.onVisualizerSelected,
        onTrackRadio = actions.onTrackRadio,
        onAddToPlaylist = actions.onAddToPlaylist,
        onCreatePlaylistAndAdd = actions.onCreatePlaylistAndAdd,
        onDownloadTrack = actions.onDownloadTrack,
        onGoToAlbum = actions.onGoToAlbum,
        onGoToArtist = actions.onGoToArtist,
        onQueueItemRadio = actions.onQueueItemRadio,
        onQueueItemAddToPlaylist = actions.onQueueItemAddToPlaylist,
        onQueueItemCreatePlaylistAndAdd = actions.onQueueItemCreatePlaylistAndAdd,
        onQueueItemDownload = actions.onQueueItemDownload,
        onToggleFavorite = actions.onToggleFavorite,
        onRatingSelected = actions.onRatingSelected,
    )
}

@Composable
fun rememberAndroidAppShellUiState(
    state: AndroidAppState,
    modifier: Modifier,
    context: Context,
    bassLoadReport: AndroidBassLoadReport,
    playbackEngine: AndroidPlaybackEngine,
): AndroidAppShellUiState =
    with(state) {
        val activeQueueForUi = playbackQueue.tracks.ifEmpty { allKnownTracks(searchResults, albumDetail) }
        val streamQualityForUi = playbackSettings.streamQualityForNetwork(context.isActiveNetworkMobileData())
        val diagnostics = rememberAndroidDiagnostics(
            selectedRoute = selectedRoute,
            storageStats = storageStats,
            provider = provider,
            validation = validation,
            activeSourceId = activeSourceId,
            bassLoadReport = bassLoadReport,
            playbackEngine = playbackEngine,
            playbackState = playbackState,
            playbackProgress = playbackProgress,
            playbackQueue = playbackQueue,
            playbackSettings = playbackSettings,
            streamQuality = streamQualityForUi,
            nowPlaying = nowPlaying,
            nowPlayingStation = nowPlayingStation,
            nowPlayingStreamMetadata = nowPlayingStreamMetadata,
            nowPlayingOpen = nowPlayingOpen,
            visualizerVisible = visualizerVisible,
            activeTlsSettings = activeTlsSettings,
        )
        val shellModels = rememberAndroidShellModels(
            connectionName = connectionName,
            serverUrl = serverUrl,
            username = username,
            password = password,
            skipTlsVerification = skipTlsVerification,
            customCertificatePath = customCertificatePath,
            clientCertificatePath = clientCertificatePath,
            clientCertificatePassword = clientCertificatePassword,
            provider = provider,
            homeState = homeState,
            playlistTracksById = playlistTracksById,
            searchResults = searchResults,
            libraryStatus = libraryStatus,
            isLibrarySyncing = isLibrarySyncing,
            downloadedTracks = downloadedTracks,
            selectedPlaylistTracks = selectedPlaylistTracks,
            selectedPlaylist = selectedPlaylist,
            albumDetail = albumDetail,
            artistDetail = artistDetail,
            artistPopularTracksByArtistId = artistPopularTracksByArtistId,
            artistPopularTracksStatusByArtistId = artistPopularTracksStatusByArtistId,
            artistSimilarArtistsByArtistId = artistSimilarArtistsByArtistId,
            artistSimilarArtistsStatusByArtistId = artistSimilarArtistsStatusByArtistId,
        )
        val nowPlayingUi = androidNowPlayingUi(
            nowPlaying = nowPlaying,
            nowPlayingStation = nowPlayingStation,
            nowPlayingStreamMetadata = nowPlayingStreamMetadata,
            playbackEngine = playbackEngine,
            playbackState = playbackState,
            playbackProgress = playbackProgress,
            visualizerVisible = visualizerVisible,
            volumePercent = volumePercent,
            knownTracks = activeQueueForUi,
            repeatMode = repeatMode,
            shuffledUpNextSnapshot = shuffledUpNextSnapshot,
            waveformByTrackId = waveformByTrackId,
            audioTagsByTrackId = audioTagsByTrackId,
            lyricsByTrackId = lyricsByTrackId,
            lyricsStatusByTrackId = lyricsStatusByTrackId,
            lyricsVisible = lyricsVisible,
            nowPlayingOpen = nowPlayingOpen,
            streamQuality = streamQualityForUi,
            provider = provider,
            playlistChoices = shellModels.playlistChoices,
            playlistActionStatus = playlistActionStatus,
            relatedTracks = relatedTracks,
            radioTrackArtworkByKey = radioTrackArtworkByKey,
            radioStations = homeState.radioStations,
        )

        AndroidAppShellUiState(
            modifier = modifier,
            status = status,
            serverVersion = validation?.serverVersion,
            connected = provider != null,
            editingConnection = editingConnection,
            restoringConnection = restoringConnection,
            connectionForm = shellModels.connectionForm,
            playbackSettings = playbackSettings,
            diagnostics = diagnostics,
            supportsReplayGain = playbackEngine.supportsReplayGain,
            supportsGapless = playbackEngine.supportsGapless,
            supportsCrossfade = playbackEngine.supportsCrossfade,
            showMobileNetworkQuality = true,
            selectedVisualizer = selectedVisualizer,
            visualizerBandsProvider = { visualizerFrame?.bands.orEmpty() },
            query = query,
            home = shellModels.home,
            searchResults = shellModels.searchResults,
            libraryArtists = shellModels.libraryArtists,
            libraryQuery = libraryQuery,
            librarySyncStatus = shellModels.librarySyncStatus,
            downloads = shellModels.downloads,
            downloadBytes = storageStats.downloadBytes,
            maxDownloadBytes = AndroidMaxDownloadBytes,
            downloadStatus = downloadStatus,
            playlistItems = shellModels.playlistItems,
            recentPlaylistIds = recentPlaylistIds,
            playlistSortMode = playlistSortMode,
            playlistChoices = shellModels.playlistChoices,
            playlistActionStatus = playlistActionStatus,
            radioStations = homeState.radioStations,
            albumDetail = shellModels.albumDetail,
            artistDetail = shellModels.artistDetail,
            playlistDetail = shellModels.playlistDetail,
            nowPlaying = nowPlayingUi,
            nowPlayingOpen = nowPlayingOpen,
            selectedRoute = selectedRoute,
        )
    }

fun androidAppShellActions(
    state: AndroidAppState,
    playbackEngine: AndroidPlaybackEngine,
    settingsStore: AndroidSettingsStore,
    handleConnectionFormChanged: (ConnectionFormState) -> Unit,
    connectToNavidrome: () -> Unit,
    handlePlaybackSettingsChanged: (PlaybackSettings) -> Unit,
    handlePlaybackSettingsChangedAndRedownload: (PlaybackSettings) -> Unit,
    handleClearCache: () -> Unit,
    handleClearLibrary: () -> Unit,
    handleResetDatabase: () -> Unit,
    handleSearch: () -> Unit,
    startAndroidLibrarySync: (Boolean) -> Unit,
    handleShellTrackSelected: (SharedTrackRowUi) -> Unit,
    handleDownloadedTrackSelected: (NaviampDownloadedTrackUi) -> Unit,
    handleDownloadedTrackAddToPlaylist: (NaviampDownloadedTrackUi, NaviampPlaylistChoiceUi?) -> Unit,
    handleDownloadedTrackCreatePlaylistAndAdd: (NaviampDownloadedTrackUi, String) -> Unit,
    removeDownload: (NaviampDownloadedTrackUi) -> Unit,
    handleShellAlbumSelected: (SharedMediaItemUi) -> Unit,
    handleMixAlbumSelected: (SharedMediaItemUi) -> Unit,
    handleShellAlbumPlay: (Boolean) -> Unit,
    handleShellAlbumTrackSelected: (SharedTrackRowUi) -> Unit,
    handleShellAlbumRadio: () -> Unit,
    appendTracksToQueue: (List<Track>, String) -> Unit,
    downloadTracks: (List<Track>, String) -> Unit,
    addTracksToPlaylist: (List<Track>, NaviampPlaylistChoiceUi?, String?, String) -> Unit,
    handleAlbumTrackDownload: (SharedTrackRowUi) -> Unit,
    handleAlbumTrackAddToPlaylist: (SharedTrackRowUi, NaviampPlaylistChoiceUi?) -> Unit,
    handleAlbumTrackCreatePlaylistAndAdd: (SharedTrackRowUi, String) -> Unit,
    handleShellArtistRadio: (SharedArtistDetailUi) -> Unit,
    handleShellArtistShuffle: () -> Unit,
    loadArtistTracks: ((List<Track>) -> Unit) -> Unit,
    handleArtistPopularPlay: (SharedArtistDetailUi) -> Unit,
    handleShellArtistPopularRadio: (SharedArtistDetailUi) -> Unit,
    handleArtistPopularTrackSelected: (SharedTrackRowUi) -> Unit,
    handleArtistPopularAddToQueue: (SharedArtistDetailUi) -> Unit,
    handleTrackAddToQueue: (SharedTrackRowUi) -> Unit,
    handleTrackDownload: (SharedTrackRowUi) -> Unit,
    handleTrackAddToPlaylist: (SharedTrackRowUi, NaviampPlaylistChoiceUi?) -> Unit,
    handleTrackCreatePlaylistAndAdd: (SharedTrackRowUi, String) -> Unit,
    findSimilarArtists: (app.naviamp.domain.ArtistId, String) -> Unit,
    handleSimilarArtistSelected: (SharedSimilarArtistUi) -> Unit,
    openExternalArtistUrl: (String) -> Unit,
    openArtistDetails: (app.naviamp.domain.ArtistId, String) -> Unit,
    handleArtistAlbumRadio: (SharedMediaItemUi) -> Unit,
    loadArtistAlbumTracks: (SharedMediaItemUi, (List<Track>) -> Unit) -> Unit,
    openPlaylistDetails: (Playlist) -> Unit,
    playPlaylist: (Playlist, Boolean) -> Unit,
    downloadPlaylist: (Playlist) -> Unit,
    addPlaylistToQueue: (Playlist) -> Unit,
    addPlaylistToPlaylist: (Playlist, NaviampPlaylistChoiceUi?, String?) -> Unit,
    renamePlaylist: (Playlist, String) -> Unit,
    deletePlaylist: (Playlist) -> Unit,
    saveSmartPlaylist: suspend (SmartPlaylistDefinition) -> Unit,
    updateSmartPlaylist: suspend (Playlist, SmartPlaylistDefinition) -> Unit,
    loadSmartPlaylist: suspend (Playlist) -> SmartPlaylistDefinition,
    closeActivePlaylist: () -> Unit,
    handlePlaylistTrackSelected: (SharedTrackRowUi) -> Unit,
    handleRecentRadioSelected: (SharedMediaItemUi) -> Unit,
    handleRadioStationSelected: (InternetRadioStation) -> Unit,
    saveInternetRadioStation: (InternetRadioStation) -> Unit,
    deleteInternetRadioStation: (InternetRadioStation) -> Unit,
    handleShellHomeStationSelected: (SharedHomeStationUi) -> Unit,
    closeActiveDetail: () -> Unit,
    handleShellResume: () -> Unit,
    playAdjacentTrack: (Int) -> Unit,
    performSeek: (Double) -> Unit,
    handleShellToggleShuffle: () -> Unit,
    loadLyrics: (Track) -> Unit,
    handleShellTrackRadio: () -> Unit,
    handleNowPlayingAddToPlaylist: (NaviampPlaylistChoiceUi?) -> Unit,
    handleNowPlayingCreatePlaylistAndAdd: (String) -> Unit,
    downloadTrack: (Track) -> Unit,
    handleShellGoToAlbum: () -> Unit,
    handleShellGoToArtist: () -> Unit,
    handleShellQueueItemRadio: (NaviampNowPlayingItemUi) -> Unit,
    findKnownTrack: (String) -> Track?,
    addTrackToPlaylist: (Track, NaviampPlaylistChoiceUi?, String?) -> Unit,
    toggleCurrentFavorite: () -> Unit,
    handleShellRatingSelected: (Int?) -> Unit,
): AndroidAppShellActions =
    with(state) {
        AndroidAppShellActions(
            onRouteSelected = { route ->
                navigationState = navigationState.copy(route = route.toNaviampRoute())
                contentState = contentState.clearDetails()
                nowPlayingOpen = false
            },
            onConnectionFormChanged = handleConnectionFormChanged,
            onConnect = { connectToNavidrome() },
            onEditConnection = { editingConnection = true },
            onCancelEditConnection = { editingConnection = false },
            onPlaybackSettingsChanged = handlePlaybackSettingsChanged,
            onPlaybackSettingsChangedAndRedownload = handlePlaybackSettingsChangedAndRedownload,
            onClearCache = handleClearCache,
            onClearLibrary = handleClearLibrary,
            onResetDatabase = handleResetDatabase,
            onQueryChanged = { contentState = contentState.copy(searchQuery = it) },
            onSearch = handleSearch,
            onClearSearch = {
                contentState = contentState.copy(
                    searchQuery = "",
                    searchResults = app.naviamp.domain.provider.MediaSearchResults(),
                )
                tracks = emptyList()
                status = ""
            },
            onLibraryQueryChanged = { libraryQuery = it },
            onRefreshLibrary = { startAndroidLibrarySync(true) },
            onTrackSelected = handleShellTrackSelected,
            onDownloadedTrackSelected = handleDownloadedTrackSelected,
            onDownloadedTrackAddToPlaylist = handleDownloadedTrackAddToPlaylist,
            onDownloadedTrackCreatePlaylistAndAdd = handleDownloadedTrackCreatePlaylistAndAdd,
            onRemoveDownload = removeDownload,
            onAlbumSelected = handleShellAlbumSelected,
            onMixAlbumSelected = handleMixAlbumSelected,
            onAlbumPlay = { _, shuffle -> handleShellAlbumPlay(shuffle) },
            onAlbumTrackSelected = handleShellAlbumTrackSelected,
            onAlbumRadio = { handleShellAlbumRadio() },
            onAlbumAddToQueue = { appendTracksToQueue(albumDetail?.tracks.orEmpty(), "album tracks") },
            onAlbumDownload = { downloadTracks(albumDetail?.tracks.orEmpty(), "album") },
            onAlbumAddToPlaylist = { _, playlist ->
                addTracksToPlaylist(albumDetail?.tracks.orEmpty(), playlist, null, "album")
            },
            onAlbumCreatePlaylistAndAdd = { _, name ->
                addTracksToPlaylist(albumDetail?.tracks.orEmpty(), null, name, "album")
            },
            onAlbumTrackDownload = handleAlbumTrackDownload,
            onAlbumTrackAddToPlaylist = handleAlbumTrackAddToPlaylist,
            onAlbumTrackCreatePlaylistAndAdd = handleAlbumTrackCreatePlaylistAndAdd,
            onArtistRadio = handleShellArtistRadio,
            onArtistShuffle = { handleShellArtistShuffle() },
            onArtistAddToQueue = { loadArtistTracks { appendTracksToQueue(it, "artist tracks") } },
            onArtistAddToPlaylist = { _, playlist -> loadArtistTracks { addTracksToPlaylist(it, playlist, null, "artist") } },
            onArtistCreatePlaylistAndAdd = { _, name -> loadArtistTracks { addTracksToPlaylist(it, null, name, "artist") } },
            onArtistPopularPlay = handleArtistPopularPlay,
            onArtistPopularRadio = handleShellArtistPopularRadio,
            onArtistPopularTrackSelected = handleArtistPopularTrackSelected,
            onArtistPopularAddToQueue = handleArtistPopularAddToQueue,
            onArtistPopularTrackAddToQueue = handleTrackAddToQueue,
            onArtistPopularTrackDownload = handleTrackDownload,
            onArtistPopularTrackAddToPlaylist = handleTrackAddToPlaylist,
            onArtistPopularTrackCreatePlaylistAndAdd = handleTrackCreatePlaylistAndAdd,
            onFindSimilarArtists = { detail ->
                findSimilarArtists(app.naviamp.domain.ArtistId(detail.artist.id), detail.artist.title)
            },
            onSimilarArtistSelected = handleSimilarArtistSelected,
            onSimilarArtistExternalSelected = openExternalArtistUrl,
            onArtistSelected = { selectedArtist ->
                openArtistDetails(app.naviamp.domain.ArtistId(selectedArtist.id), selectedArtist.title)
            },
            onArtistAlbumRadio = handleArtistAlbumRadio,
            onArtistAlbumDownload = { selectedAlbum -> loadArtistAlbumTracks(selectedAlbum) { downloadTracks(it, selectedAlbum.title) } },
            onArtistAlbumAddToQueue = { selectedAlbum -> loadArtistAlbumTracks(selectedAlbum) { appendTracksToQueue(it, "album tracks") } },
            onArtistAlbumAddToPlaylist = { selectedAlbum, playlist ->
                loadArtistAlbumTracks(selectedAlbum) { addTracksToPlaylist(it, playlist, null, selectedAlbum.title) }
            },
            onArtistAlbumCreatePlaylistAndAdd = { selectedAlbum, name ->
                loadArtistAlbumTracks(selectedAlbum) { addTracksToPlaylist(it, null, name, selectedAlbum.title) }
            },
            onPlaylistSelected = { selectedPlaylist ->
                homeState.playlists.firstOrNull { it.id == selectedPlaylist.id }?.let(openPlaylistDetails)
                    ?: run { status = "Playlist not found." }
            },
            onPlaylistSortModeChanged = { playlistSortMode = it },
            onPlaylistPlay = { selectedPlaylist, shuffle ->
                homeState.playlists.firstOrNull { it.id == selectedPlaylist.id }?.let { playPlaylist(it, shuffle) }
                    ?: run { status = "Playlist not found." }
            },
            onPlaylistItemAddToQueue = { selectedPlaylist ->
                homeState.playlists.firstOrNull { it.id == selectedPlaylist.id }?.let(addPlaylistToQueue)
                    ?: run { status = "Playlist not found." }
            },
            onPlaylistItemAddToPlaylist = { selectedPlaylist, playlist ->
                homeState.playlists.firstOrNull { it.id == selectedPlaylist.id }?.let { addPlaylistToPlaylist(it, playlist, null) }
                    ?: run { status = "Playlist not found." }
            },
            onPlaylistItemCreatePlaylistAndAdd = { selectedPlaylist, name ->
                homeState.playlists.firstOrNull { it.id == selectedPlaylist.id }?.let { addPlaylistToPlaylist(it, null, name) }
                    ?: run { status = "Playlist not found." }
            },
            onPlaylistAddToQueue = { appendTracksToQueue(selectedPlaylistTracks, "playlist tracks") },
            onPlaylistDownload = { selectedPlaylist ->
                homeState.playlists.firstOrNull { it.id == selectedPlaylist.id }?.let(downloadPlaylist)
                    ?: run { status = "Playlist not found." }
            },
            onPlaylistAddToPlaylist = { _, playlist ->
                selectedPlaylist?.let { addPlaylistToPlaylist(it, playlist, null) }
                    ?: run { status = "Playlist not found." }
            },
            onPlaylistCreatePlaylistAndAdd = { _, name ->
                selectedPlaylist?.let { addPlaylistToPlaylist(it, null, name) }
                    ?: run { status = "Playlist not found." }
            },
            onPlaylistRename = { selectedPlaylist, name ->
                homeState.playlists.firstOrNull { it.id == selectedPlaylist.id }?.let { renamePlaylist(it, name) }
                    ?: run { status = "Playlist not found." }
            },
            onPlaylistDelete = { selectedPlaylist ->
                homeState.playlists.firstOrNull { it.id == selectedPlaylist.id }?.let(deletePlaylist)
                    ?: run { status = "Playlist not found." }
            },
            onSmartPlaylistSave = { definition -> saveSmartPlaylist(definition) },
            onSmartPlaylistUpdate = { playlist, definition ->
                homeState.playlists.firstOrNull { it.id == playlist.id }?.let { updateSmartPlaylist(it, definition) }
                    ?: run { status = "Playlist not found." }
            },
            onSmartPlaylistLoad = { playlist ->
                homeState.playlists.firstOrNull { it.id == playlist.id }?.let { loadSmartPlaylist(it) }
                    ?: throw IllegalArgumentException("Playlist not found.")
            },
            onPlaylistBack = { closeActivePlaylist() },
            onPlaylistTrackSelected = handlePlaylistTrackSelected,
            onTrackAddToQueue = handleTrackAddToQueue,
            onRecentRadioSelected = handleRecentRadioSelected,
            onRadioStationSelected = handleRadioStationSelected,
            onRadioStationSave = saveInternetRadioStation,
            onRadioStationDelete = deleteInternetRadioStation,
            onHomeStationSelected = handleShellHomeStationSelected,
            onOpenNowPlaying = { nowPlayingOpen = true },
            onCloseNowPlaying = {
                if (nowPlayingOpen) {
                    nowPlayingOpen = false
                } else {
                    closeActiveDetail()
                }
            },
            onPause = playbackEngine::pause,
            onResume = handleShellResume,
            onStop = playbackEngine::stop,
            onPrevious = { playAdjacentTrack(-1) },
            onNext = { playAdjacentTrack(1) },
            onSeek = performSeek,
            onVolumeChanged = { percent ->
                volumePercent = percent
                playbackEngine.setVolume(percent)
            },
            onToggleShuffle = handleShellToggleShuffle,
            onCycleRepeatMode = {
                repeatMode = nextRepeatMode(repeatMode)
            },
            onToggleLyrics = {
                lyricsVisible = !lyricsVisible
                if (lyricsVisible) {
                    nowPlaying?.let(loadLyrics)
                }
            },
            onToggleVisualizer = { visualizerRequestedVisible = !visualizerRequestedVisible },
            onVisualizerSelected = { visualizer ->
                selectedVisualizer = visualizer
                settingsStore.saveVisualizerSettings(VisualizerSettings(selectedVisualizer = visualizer.name))
            },
            onTrackRadio = handleShellTrackRadio,
            onAddToPlaylist = handleNowPlayingAddToPlaylist,
            onCreatePlaylistAndAdd = handleNowPlayingCreatePlaylistAndAdd,
            onDownloadTrack = { nowPlaying?.let(downloadTrack) },
            onGoToAlbum = handleShellGoToAlbum,
            onGoToArtist = handleShellGoToArtist,
            onQueueItemRadio = handleShellQueueItemRadio,
            onQueueItemAddToPlaylist = { item, playlist ->
                findKnownTrack(item.id)?.let { addTrackToPlaylist(it, playlist, null) }
            },
            onQueueItemCreatePlaylistAndAdd = { item, name ->
                findKnownTrack(item.id)?.let { addTrackToPlaylist(it, null, name) }
            },
            onQueueItemDownload = { item -> findKnownTrack(item.id)?.let(downloadTrack) },
            onToggleFavorite = { toggleCurrentFavorite() },
            onRatingSelected = handleShellRatingSelected,
        )
    }
