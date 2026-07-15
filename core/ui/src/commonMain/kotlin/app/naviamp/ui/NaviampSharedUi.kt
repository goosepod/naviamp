package app.naviamp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.cache.DownloadJob
import app.naviamp.domain.settings.ConnectionFormHeader
import app.naviamp.domain.settings.ConnectionFormMusicFolder
import app.naviamp.domain.settings.ConnectionFormSecondaryUrl
import app.naviamp.domain.settings.InterfaceSettings
import app.naviamp.domain.settings.AlbumCollectionLayout
import app.naviamp.domain.settings.AlbumSortOrder
import app.naviamp.domain.settings.toggleSelectedMusicFolderId
import app.naviamp.domain.smartplaylist.SmartPlaylistDefinition

@Composable
expect fun PlatformCoverArt(
    url: String?,
    colors: NaviampColors,
    size: Dp,
    cornerRadius: Dp,
)

@Composable
expect fun PlatformExpandedMediaImage(
    url: String?,
    colors: NaviampColors,
    maxWidth: Dp,
    maxHeight: Dp,
)

@Composable
expect fun rememberPlatformCoverArtGradientColors(
    url: String?,
    colors: NaviampColors,
): List<Color>

@Composable
expect fun rememberPlatformCoverArtPlayerColors(
    url: String?,
    colors: NaviampColors,
): NaviampPlayerColors

@Composable
expect fun NaviampTooltip(
    text: String,
    colors: NaviampColors,
    content: @Composable () -> Unit,
)

@Composable
@NonRestartableComposable
fun NaviampSharedAppShell(
    modifier: Modifier = Modifier,
    status: String,
    serverVersion: String?,
    connected: Boolean,
    editingConnection: Boolean,
    restoringConnection: Boolean = false,
    connectionForm: ConnectionFormState,
    interfaceSettings: InterfaceSettings = InterfaceSettings(),
    playbackSettings: PlaybackSettings = PlaybackSettings(),
    cacheSettings: CacheSettings = CacheSettings(),
    diagnostics: NaviampDiagnosticsUi = NaviampDiagnosticsUi(),
    about: NaviampAboutUi = NaviampAboutUi(),
    savedConnections: List<NaviampSavedConnectionUi> = emptyList(),
    isConnectionFormOpen: Boolean = editingConnection,
    isConnecting: Boolean = restoringConnection,
    connectionStatus: String? = status,
    settingsSyncStatus: String? = null,
    availableMusicFolders: List<ConnectionFormMusicFolder> = emptyList(),
    musicFoldersStatus: String? = null,
    hasSavedConnection: Boolean = false,
    supportsReplayGain: Boolean = false,
    supportsGapless: Boolean = true,
    supportsCrossfade: Boolean = false,
    supportsEqualizer: Boolean = false,
    supportsSonicSimilarity: Boolean = false,
    showMobileNetworkQuality: Boolean = false,
    query: String,
    home: SharedHomeUi,
    homeRefreshing: Boolean = false,
    searchResults: SharedSearchResultsUi,
    artistMixBuilder: SharedArtistMixBuilderUi = SharedArtistMixBuilderUi(),
    albumMixBuilder: SharedAlbumMixBuilderUi = SharedAlbumMixBuilderUi(),
    genreMixBuilder: SharedGenreMixBuilderUi = SharedGenreMixBuilderUi(),
    sonicPathBuilder: SharedSonicPathBuilderUi = SharedSonicPathBuilderUi(),
    sonicMixBuilder: SharedSonicMixBuilderUi = SharedSonicMixBuilderUi(),
    libraryArtists: List<SharedMediaItemUi>,
    libraryQuery: String = "",
    librarySyncStatus: NaviampLibrarySyncStatusUi = NaviampLibrarySyncStatusUi(),
    downloads: List<NaviampDownloadedTrackUi> = emptyList(),
    downloadBytes: Long = 0L,
    maxDownloadBytes: Long = 0L,
    offlineDashboard: NaviampOfflineDashboardUi = NaviampOfflineDashboardUi(),
    downloadStatus: String? = null,
    downloadJobs: List<DownloadJob> = emptyList(),
    keepFavoritesDownloaded: Boolean = false,
    downloadLocations: List<NaviampStorageLocationUi> = emptyList(),
    audioCacheLocations: List<NaviampStorageLocationUi> = emptyList(),
    selectedDownloadLocationId: String? = null,
    selectedAudioCacheLocationId: String? = null,
    playlistItems: List<SharedMediaItemUi>,
    recentPlaylistIds: List<String> = emptyList(),
    playlistSortMode: SharedPlaylistSortMode = SharedPlaylistSortMode.Alphabetical,
    playlistChoices: List<NaviampPlaylistChoiceUi> = emptyList(),
    playlistActionStatus: String? = null,
    playlistRefreshing: Boolean = false,
    radioStations: List<InternetRadioStation>,
    radioRefreshing: Boolean = false,
    albumDetail: SharedAlbumDetailUi?,
    artistDetail: SharedArtistDetailUi?,
    playlistDetail: SharedPlaylistDetailUi? = null,
    nowPlaying: NowPlayingUi?,
    nowPlayingOpen: Boolean,
    visualizerBandsProvider: () -> List<Float> = { nowPlaying?.visualizerFrame?.bands.orEmpty() },
    selectedVisualizer: NaviampVisualizer = NaviampVisualizer.AudioSphere,
    selectedRoute: SharedRoute,
    onRouteSelected: (SharedRoute) -> Unit,
    onConnectionFormChanged: (ConnectionFormState) -> Unit,
    onConnect: () -> Unit,
    onEditConnection: () -> Unit,
    onNewConnection: () -> Unit = onEditConnection,
    onEditSavedConnection: (NaviampSavedConnectionUi) -> Unit = { onEditConnection() },
    onConnectSavedConnection: (NaviampSavedConnectionUi) -> Unit = {},
    onDeleteSavedConnection: (NaviampSavedConnectionUi) -> Unit = {},
    onImportSettingsSyncFile: (() -> Unit)? = null,
    onChooseSettingsSyncFolder: (() -> Unit)? = null,
    onImportSettingsSyncFolder: (() -> Unit)? = null,
    onExportSettingsSyncFolder: (() -> Unit)? = null,
    settingsSyncAutoExportEnabled: Boolean = false,
    onSettingsSyncAutoExportChanged: ((Boolean) -> Unit)? = null,
    onCancelEditConnection: () -> Unit,
    onInterfaceSettingsChanged: (InterfaceSettings) -> Unit = {},
    onPlaybackSettingsChanged: (PlaybackSettings) -> Unit = {},
    onPlaybackSettingsChangedAndRedownload: (PlaybackSettings) -> Unit = onPlaybackSettingsChanged,
    onCacheSettingsChanged: (CacheSettings) -> Unit = {},
    onQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onClearSearch: () -> Unit = {},
    onArtistMixQueryChanged: (String) -> Unit = {},
    onArtistMixSearch: () -> Unit = {},
    onArtistMixArtistSelected: (SharedMediaItemUi) -> Unit = {},
    onArtistMixArtistRemoved: (SharedMediaItemUi) -> Unit = {},
    onArtistMixReset: () -> Unit = {},
    onArtistMixPlay: () -> Unit = {},
    onAlbumMixQueryChanged: (String) -> Unit = {},
    onAlbumMixSearch: () -> Unit = {},
    onAlbumMixAlbumSelected: (SharedMediaItemUi) -> Unit = {},
    onAlbumMixAlbumRemoved: (SharedMediaItemUi) -> Unit = {},
    onAlbumMixReset: () -> Unit = {},
    onAlbumMixPlay: () -> Unit = {},
    onGenreMixQueryChanged: (String) -> Unit = {},
    onGenreMixSearch: () -> Unit = {},
    onGenreMixGenreSelected: (SharedGenreMixItemUi) -> Unit = {},
    onGenreMixGenreRemoved: (SharedGenreMixItemUi) -> Unit = {},
    onGenreMixReset: () -> Unit = {},
    onGenreMixPlay: () -> Unit = {},
    onSonicPathStartQueryChanged: (String) -> Unit = {},
    onSonicPathEndQueryChanged: (String) -> Unit = {},
    onSonicPathStartSearch: () -> Unit = {},
    onSonicPathEndSearch: () -> Unit = {},
    onSonicPathStartTrackSelected: (SharedTrackRowUi) -> Unit = {},
    onSonicPathEndTrackSelected: (SharedTrackRowUi) -> Unit = {},
    onSonicPathStartTrackCleared: () -> Unit = {},
    onSonicPathEndTrackCleared: () -> Unit = {},
    onSonicPathCountChanged: (Int) -> Unit = {},
    onSonicPathBuild: () -> Unit = {},
    onSonicPathReset: () -> Unit = {},
    onSonicPathPlay: () -> Unit = {},
    onSonicPathAddToQueue: () -> Unit = {},
    onSonicPathSaveAsPlaylist: (String) -> Unit = {},
    onSonicMixQueryChanged: (String) -> Unit = {},
    onSonicMixSearch: () -> Unit = {},
    onSonicMixTrackSelected: (SharedTrackRowUi) -> Unit = {},
    onSonicMixTrackRemoved: (SharedTrackRowUi) -> Unit = {},
    onSonicMixTargetLengthChanged: (Int) -> Unit = {},
    onSonicMixBiasChanged: (SharedSonicMixBiasUi) -> Unit = {},
    onSonicMixBuild: () -> Unit = {},
    onSonicMixReset: () -> Unit = {},
    onSonicMixPlay: () -> Unit = {},
    onSonicMixAddToQueue: () -> Unit = {},
    onSonicMixSaveAsPlaylist: (String) -> Unit = {},
    onLibraryQueryChanged: (String) -> Unit = {},
    onRefreshHome: () -> Unit = {},
    onRefreshLibrary: () -> Unit = {},
    onLoadMoreLibrary: () -> Unit = {},
    onRefreshPlaylists: () -> Unit = {},
    onRefreshRadioStations: () -> Unit = {},
    onTrackSelected: (SharedTrackRowUi) -> Unit,
    onDownloadedTrackAction: (DownloadedTrackActionRequest) -> Unit = {},
    onCancelDownloadJob: (String) -> Unit = {},
    onRetryDownloadJob: (String) -> Unit = {},
    onRefreshDownloads: () -> Unit = {},
    onToggleKeepFavoritesDownloaded: () -> Unit = {},
    onDeleteAllDownloads: () -> Unit = {},
    onDownloadLocationChanged: (NaviampStorageLocationUi) -> Unit = {},
    onAudioCacheLocationChanged: (NaviampStorageLocationUi) -> Unit = {},
    onAlbumSelected: (SharedMediaItemUi) -> Unit,
    onAlbumFavoriteToggled: (SharedMediaItemUi) -> Unit = {},
    onMixAlbumSelected: (SharedMediaItemUi) -> Unit = onAlbumSelected,
    onAlbumPlay: (SharedAlbumDetailUi, Boolean) -> Unit = { _, _ -> },
    onAlbumRadio: (SharedAlbumDetailUi) -> Unit = {},
    onAlbumDownload: (SharedAlbumDetailUi) -> Unit = {},
    onAlbumAddToQueue: (SharedAlbumDetailUi) -> Unit = {},
    onAlbumAddToPlaylist: (SharedAlbumDetailUi, NaviampPlaylistChoiceUi?) -> Unit = { _, _ -> },
    onAlbumCreatePlaylistAndAdd: (SharedAlbumDetailUi, String) -> Unit = { _, _ -> },
    onAlbumTrackDownload: (SharedTrackRowUi) -> Unit = {},
    onAlbumTrackAddToPlaylist: (SharedTrackRowUi, NaviampPlaylistChoiceUi?) -> Unit = { _, _ -> },
    onAlbumTrackCreatePlaylistAndAdd: (SharedTrackRowUi, String) -> Unit = { _, _ -> },
    onArtistSelected: (SharedMediaItemUi) -> Unit,
    onArtistFavoriteToggled: (SharedMediaItemUi) -> Unit = {},
    onArtistRadio: (SharedArtistDetailUi) -> Unit = {},
    onArtistShuffle: (SharedArtistDetailUi) -> Unit = {},
    onArtistAddToQueue: (SharedArtistDetailUi) -> Unit = {},
    onArtistAddToPlaylist: (SharedArtistDetailUi, NaviampPlaylistChoiceUi?) -> Unit = { _, _ -> },
    onArtistCreatePlaylistAndAdd: (SharedArtistDetailUi, String) -> Unit = { _, _ -> },
    onArtistPopularPlay: (SharedArtistDetailUi) -> Unit = {},
    onArtistPopularRadio: (SharedArtistDetailUi) -> Unit = {},
    onArtistPopularAddToQueue: (SharedArtistDetailUi) -> Unit = {},
    onArtistPopularTrackSelected: (SharedTrackRowUi) -> Unit = onTrackSelected,
    onAlbumTrackSelected: (SharedTrackRowUi) -> Unit = onTrackSelected,
    onArtistPopularTrackAddToQueue: (SharedTrackRowUi) -> Unit = {},
    onArtistPopularTrackDownload: (SharedTrackRowUi) -> Unit = {},
    onArtistPopularTrackAddToPlaylist: (SharedTrackRowUi, NaviampPlaylistChoiceUi?) -> Unit = { _, _ -> },
    onArtistPopularTrackCreatePlaylistAndAdd: (SharedTrackRowUi, String) -> Unit = { _, _ -> },
    onFindSimilarArtists: (SharedArtistDetailUi) -> Unit = {},
    onSimilarArtistSelected: (SharedSimilarArtistUi) -> Unit = {},
    onSimilarArtistExternalSelected: (String) -> Unit = {},
    onPlaylistSelected: (SharedMediaItemUi) -> Unit,
    onPlaylistSortModeChanged: (SharedPlaylistSortMode) -> Unit = {},
    onPlaylistPlay: (SharedMediaItemUi, Boolean) -> Unit = { _, _ -> },
    onPlaylistAddToQueue: (SharedPlaylistDetailUi) -> Unit = {},
    onPlaylistAddToPlaylist: (SharedPlaylistDetailUi, NaviampPlaylistChoiceUi?) -> Unit = { _, _ -> },
    onPlaylistCreatePlaylistAndAdd: (SharedPlaylistDetailUi, String) -> Unit = { _, _ -> },
    onPlaylistCopy: (SharedPlaylistDetailUi, String, Boolean) -> Unit = { _, _, _ -> },
    onPlaylistRename: (SharedMediaItemUi, String) -> Unit = { _, _ -> },
    onPlaylistDelete: (SharedMediaItemUi) -> Unit = {},
    onStandardPlaylistUpdate: suspend (SharedMediaItemUi, List<SharedTrackRowUi>) -> Unit = { _, _ -> },
    onMediaItemAction: (SharedMediaItemActionRequest) -> Unit = { request ->
        handleSharedMediaItemAction(
            request,
            SharedMediaItemActionHandlers(
                onSelect = { item ->
                    when (request.kind) {
                        SharedMediaItemKind.Album -> onAlbumSelected(item)
                        SharedMediaItemKind.Artist -> onArtistSelected(item)
                        SharedMediaItemKind.Playlist -> onPlaylistSelected(item)
                        SharedMediaItemKind.Unknown,
                        SharedMediaItemKind.RadioStation,
                        SharedMediaItemKind.MixBuilder,
                        -> Unit
                    }
                },
                onPlay = { item, shuffle ->
                    if (request.kind == SharedMediaItemKind.Playlist) {
                        onPlaylistPlay(item, shuffle)
                    }
                },
                onToggleFavorite = { item ->
                    when (request.kind) {
                        SharedMediaItemKind.Album -> onAlbumFavoriteToggled(item)
                        SharedMediaItemKind.Artist -> onArtistFavoriteToggled(item)
                        SharedMediaItemKind.Unknown,
                        SharedMediaItemKind.Playlist,
                        SharedMediaItemKind.RadioStation,
                        SharedMediaItemKind.MixBuilder,
                        -> Unit
                    }
                },
                onRename = onPlaylistRename,
                onEditSmartPlaylist = {},
                onDelete = onPlaylistDelete,
            ),
        )
    },
    onSmartPlaylistSave: suspend (SmartPlaylistDefinition) -> Unit = {},
    onSmartPlaylistUpdate: suspend (SharedMediaItemUi, SmartPlaylistDefinition) -> Unit = { _, _ -> },
    onSmartPlaylistSaveWithPassword: suspend (SmartPlaylistDefinition, String) -> Unit = { definition, _ ->
        onSmartPlaylistSave(definition)
    },
    onSmartPlaylistUpdateWithPassword: suspend (SharedMediaItemUi, SmartPlaylistDefinition, String) -> Unit = { playlist, definition, _ ->
        onSmartPlaylistUpdate(playlist, definition)
    },
    onSmartPlaylistLoad: suspend (SharedMediaItemUi) -> SmartPlaylistDefinition = {
        throw UnsupportedOperationException("Smart playlist loading is not available.")
    },
    onPlaylistBack: () -> Unit = {},
    onPlaylistTrackSelected: (SharedTrackRowUi) -> Unit = {},
    onTrackAddToQueue: (SharedTrackRowUi) -> Unit = {},
    onTrackAction: (SharedTrackRowActionRequest) -> Unit = { request ->
        handleSharedTrackRowAction(
            request,
            SharedTrackRowActionHandlers(
                onSelect = onTrackSelected,
                onAddToQueue = onTrackAddToQueue,
                onDownload = onAlbumTrackDownload,
                onAddToPlaylist = onAlbumTrackAddToPlaylist,
                onCreatePlaylistAndAdd = onAlbumTrackCreatePlaylistAndAdd,
            ),
        )
    },
    onRecentRadioSelected: (SharedMediaItemUi) -> Unit = {},
    onMixBuilderSelected: (SharedMixBuilderUi) -> Unit = {},
    onRadioStationSelected: (InternetRadioStation) -> Unit,
    onRadioStationSave: (InternetRadioStation) -> Unit = {},
    onStationAction: (StationRowActionRequest) -> Unit = {},
    onHomeStationSelected: (SharedHomeStationUi) -> Unit = {},
    onSonicDiscoveryTrackAction: (SharedHomeDiscoveryTrackActionRequest) -> Unit = {},
    onOpenNowPlaying: () -> Unit,
    onCloseNowPlaying: () -> Unit,
    onNowPlayingPlaybackAction: (NowPlayingPlaybackActionRequest) -> Unit = {},
    onNowPlayingDisplayAction: (NowPlayingDisplayActionRequest) -> Unit = {},
    onNowPlayingCurrentTrackAction: (NowPlayingCurrentTrackUiActionRequest) -> Unit = {},
    onNowPlayingQueueAction: (NowPlayingQueueActionRequest) -> Unit = {},
    onNowPlayingSleepTimerAction: (NowPlayingSleepTimerActionRequest) -> Unit = {},
    onNowPlayingSelectionAction: (NowPlayingSelectionActionRequest) -> Unit = {},
    onQueueItemAction: (NowPlayingItemActionRequest) -> Unit = {},
    onClearCache: () -> Unit = {},
    onClearLibrary: () -> Unit = {},
    onResetDatabase: () -> Unit = {},
) {
    val colors = NaviampColors.Dark
    var availableUpdate by remember { mutableStateOf<NaviampAvailableUpdate?>(null) }
    val uriHandler = LocalUriHandler.current
    CompositionLocalProvider(LocalTrackSwipeSettings provides interfaceSettings.trackSwipes) {
    NaviampUpdateCheckEffect(
        enabled = interfaceSettings.checkForUpdates,
        currentVersion = about.version,
        onUpdateAvailable = { availableUpdate = it },
    )
    LaunchedEffect(interfaceSettings.checkForUpdates) {
        if (!interfaceSettings.checkForUpdates) availableUpdate = null
    }
    val showFullNowPlaying = connected && !editingConnection && !restoringConnection && nowPlayingOpen && nowPlaying != null
    val routeUsesOwnScroll = connected &&
        !editingConnection &&
        !restoringConnection &&
        !showFullNowPlaying &&
        (
            albumDetail != null ||
                artistDetail != null ||
                playlistDetail != null ||
                selectedRoute == SharedRoute.Home ||
                selectedRoute == SharedRoute.Playlists ||
                selectedRoute == SharedRoute.Library ||
                selectedRoute == SharedRoute.ArtistMix ||
                selectedRoute == SharedRoute.AlbumMix ||
                selectedRoute == SharedRoute.GenreMix ||
                selectedRoute == SharedRoute.SonicPath ||
                selectedRoute == SharedRoute.SonicMix ||
                selectedRoute == SharedRoute.Radio ||
                selectedRoute == SharedRoute.Downloads
            )
    val nowPlayingPlayerColors = if (nowPlaying != null) {
        rememberPlatformCoverArtPlayerColors(nowPlaying.coverArtUrl, colors)
    } else {
        NaviampPlayerColors.fallback(colors)
    }
    val nowPlayingActions = NaviampNowPlayingActions(
        onPlaybackAction = onNowPlayingPlaybackAction,
        onDisplayAction = onNowPlayingDisplayAction,
        onCurrentTrackAction = onNowPlayingCurrentTrackAction,
        onQueueAction = onNowPlayingQueueAction,
        onSleepTimerAction = onNowPlayingSleepTimerAction,
        onSelectionAction = onNowPlayingSelectionAction,
        onQueueItemAction = onQueueItemAction,
    )
    val backgroundGradientColors = nowPlayingPlayerColors.gradientColors
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = colors.background,
            surface = colors.controlSurface,
            primary = colors.accent,
            onPrimary = colors.onAccent,
            onBackground = colors.primaryText,
            onSurface = colors.primaryText,
        ),
        typography = rememberNaviampTypography(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        backgroundGradientColors,
                    ),
                ),
        ) {
            Column(
                modifier
                    .fillMaxSize(),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (showFullNowPlaying || routeUsesOwnScroll) {
                                Modifier
                            } else {
                                Modifier.verticalScroll(rememberScrollState())
                            },
                        )
                        .padding(
                            horizontal = if (showFullNowPlaying) 0.dp else 18.dp,
                            vertical = if (showFullNowPlaying) 0.dp else 18.dp,
                        ),
                    verticalArrangement = if (showFullNowPlaying) Arrangement.spacedBy(0.dp) else Arrangement.spacedBy(14.dp),
                ) {
                    val showingRouteConnectionForm = editingConnection && selectedRoute != SharedRoute.Settings
                    if (!showFullNowPlaying && (restoringConnection || !connected || showingRouteConnectionForm)) {
                        Text("Naviamp", color = colors.primaryText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text(status, color = colors.secondaryText, fontSize = 13.sp)
                        serverVersion?.let {
                            Text("Connected to Navidrome $it.", color = colors.secondaryText, fontSize = 13.sp)
                        }
                    }

                    if (restoringConnection && !editingConnection) {
                        RestoringConnectionCard(status = status, colors = colors)
                    } else if (showingRouteConnectionForm || (!connected && selectedRoute != SharedRoute.Settings)) {
                        NaviampConnectionForm(
                            form = connectionForm,
                            colors = colors,
                            isReconnect = connected,
                            availableMusicFolders = availableMusicFolders,
                            musicFoldersStatus = musicFoldersStatus,
                            settingsSyncStatus = settingsSyncStatus,
                            onFormChanged = onConnectionFormChanged,
                            onConnect = onConnect,
                            onImportSettingsSyncFile = onImportSettingsSyncFile,
                            onCancel = onCancelEditConnection.takeIf { connected },
                        )
                    } else {
                        ConnectedContent(
                            colors = colors,
                            selectedRoute = selectedRoute,
                            home = home,
                            homeRefreshing = homeRefreshing,
                            query = query,
                            searchResults = searchResults,
            artistMixBuilder = artistMixBuilder,
            albumMixBuilder = albumMixBuilder,
            genreMixBuilder = genreMixBuilder,
            sonicPathBuilder = sonicPathBuilder,
            sonicMixBuilder = sonicMixBuilder,
                            libraryArtists = libraryArtists,
                            libraryQuery = libraryQuery,
                            librarySyncStatus = librarySyncStatus,
                            downloads = downloads,
                            downloadBytes = downloadBytes,
                            maxDownloadBytes = maxDownloadBytes,
                            offlineDashboard = offlineDashboard,
                            downloadStatus = downloadStatus,
                            downloadJobs = downloadJobs,
                            keepFavoritesDownloaded = keepFavoritesDownloaded,
                            downloadLocations = downloadLocations,
                            audioCacheLocations = audioCacheLocations,
                            selectedDownloadLocationId = selectedDownloadLocationId,
                            selectedAudioCacheLocationId = selectedAudioCacheLocationId,
                            playlistItems = playlistItems,
                            recentPlaylistIds = recentPlaylistIds,
                            playlistSortMode = playlistSortMode,
                            playlistChoices = playlistChoices,
                            playlistActionStatus = playlistActionStatus,
                            playlistRefreshing = playlistRefreshing,
                            radioStations = radioStations,
                            radioRefreshing = radioRefreshing,
                            albumDetail = albumDetail,
                            artistDetail = artistDetail,
                            playlistDetail = playlistDetail,
                            nowPlaying = nowPlaying,
                            nowPlayingOpen = nowPlayingOpen,
                            visualizerBandsProvider = visualizerBandsProvider,
                            selectedVisualizer = selectedVisualizer,
                            interfaceSettings = interfaceSettings,
                            playbackSettings = playbackSettings,
                            cacheSettings = cacheSettings,
                            diagnostics = diagnostics,
                            about = about,
                            savedConnections = savedConnections,
                            isConnectionFormOpen = isConnectionFormOpen,
                            isConnecting = isConnecting,
                            connectionStatus = connectionStatus,
                            settingsSyncStatus = settingsSyncStatus,
                            availableMusicFolders = availableMusicFolders,
                            musicFoldersStatus = musicFoldersStatus,
                            connectionForm = connectionForm,
                            hasSavedConnection = hasSavedConnection,
                            supportsReplayGain = supportsReplayGain,
                            supportsGapless = supportsGapless,
                            supportsCrossfade = supportsCrossfade,
                            supportsEqualizer = supportsEqualizer,
                            supportsSonicSimilarity = supportsSonicSimilarity,
                            showMobileNetworkQuality = showMobileNetworkQuality,
                            onEditConnection = onEditConnection,
                            onNewConnection = onNewConnection,
                            onEditSavedConnection = onEditSavedConnection,
                            onConnectSavedConnection = onConnectSavedConnection,
                            onDeleteSavedConnection = onDeleteSavedConnection,
                            onImportSettingsSyncFile = onImportSettingsSyncFile,
                            onChooseSettingsSyncFolder = onChooseSettingsSyncFolder,
                            onImportSettingsSyncFolder = onImportSettingsSyncFolder,
                            onExportSettingsSyncFolder = onExportSettingsSyncFolder,
                            settingsSyncAutoExportEnabled = settingsSyncAutoExportEnabled,
                            onSettingsSyncAutoExportChanged = onSettingsSyncAutoExportChanged,
                            onConnectionFormChanged = onConnectionFormChanged,
                            onConnect = onConnect,
                            onCancelEditConnection = onCancelEditConnection,
                            onInterfaceSettingsChanged = onInterfaceSettingsChanged,
                            onPlaybackSettingsChanged = onPlaybackSettingsChanged,
                            onPlaybackSettingsChangedAndRedownload = onPlaybackSettingsChangedAndRedownload,
                            onCacheSettingsChanged = onCacheSettingsChanged,
                            onDownloadLocationChanged = onDownloadLocationChanged,
                            onAudioCacheLocationChanged = onAudioCacheLocationChanged,
                            onClearCache = onClearCache,
                            onClearLibrary = onClearLibrary,
                            onResetDatabase = onResetDatabase,
                            onQueryChanged = onQueryChanged,
                            onSearch = onSearch,
                            onClearSearch = onClearSearch,
                            onArtistMixQueryChanged = onArtistMixQueryChanged,
                            onArtistMixSearch = onArtistMixSearch,
                            onArtistMixArtistSelected = onArtistMixArtistSelected,
                            onArtistMixArtistRemoved = onArtistMixArtistRemoved,
                            onArtistMixReset = onArtistMixReset,
                            onArtistMixPlay = onArtistMixPlay,
                            onAlbumMixQueryChanged = onAlbumMixQueryChanged,
                            onAlbumMixSearch = onAlbumMixSearch,
                            onAlbumMixAlbumSelected = onAlbumMixAlbumSelected,
                            onAlbumMixAlbumRemoved = onAlbumMixAlbumRemoved,
                            onAlbumMixReset = onAlbumMixReset,
                            onAlbumMixPlay = onAlbumMixPlay,
                            onGenreMixQueryChanged = onGenreMixQueryChanged,
                            onGenreMixSearch = onGenreMixSearch,
                            onGenreMixGenreSelected = onGenreMixGenreSelected,
            onGenreMixGenreRemoved = onGenreMixGenreRemoved,
            onGenreMixReset = onGenreMixReset,
            onGenreMixPlay = onGenreMixPlay,
            onSonicPathStartQueryChanged = onSonicPathStartQueryChanged,
            onSonicPathEndQueryChanged = onSonicPathEndQueryChanged,
            onSonicPathStartSearch = onSonicPathStartSearch,
            onSonicPathEndSearch = onSonicPathEndSearch,
            onSonicPathStartTrackSelected = onSonicPathStartTrackSelected,
            onSonicPathEndTrackSelected = onSonicPathEndTrackSelected,
            onSonicPathStartTrackCleared = onSonicPathStartTrackCleared,
            onSonicPathEndTrackCleared = onSonicPathEndTrackCleared,
            onSonicPathCountChanged = onSonicPathCountChanged,
            onSonicPathBuild = onSonicPathBuild,
            onSonicPathReset = onSonicPathReset,
            onSonicPathPlay = onSonicPathPlay,
            onSonicPathAddToQueue = onSonicPathAddToQueue,
            onSonicPathSaveAsPlaylist = onSonicPathSaveAsPlaylist,
            onSonicMixQueryChanged = onSonicMixQueryChanged,
            onSonicMixSearch = onSonicMixSearch,
            onSonicMixTrackSelected = onSonicMixTrackSelected,
            onSonicMixTrackRemoved = onSonicMixTrackRemoved,
            onSonicMixTargetLengthChanged = onSonicMixTargetLengthChanged,
            onSonicMixBiasChanged = onSonicMixBiasChanged,
            onSonicMixBuild = onSonicMixBuild,
            onSonicMixReset = onSonicMixReset,
            onSonicMixPlay = onSonicMixPlay,
            onSonicMixAddToQueue = onSonicMixAddToQueue,
            onSonicMixSaveAsPlaylist = onSonicMixSaveAsPlaylist,
                            onLibraryQueryChanged = onLibraryQueryChanged,
                            onRefreshHome = onRefreshHome,
                            onRefreshLibrary = onRefreshLibrary,
                            onLoadMoreLibrary = onLoadMoreLibrary,
                            onRefreshPlaylists = onRefreshPlaylists,
                            onRefreshRadioStations = onRefreshRadioStations,
                            onTrackSelected = onTrackSelected,
                            onDownloadedTrackAction = onDownloadedTrackAction,
                            onCancelDownloadJob = onCancelDownloadJob,
                            onRetryDownloadJob = onRetryDownloadJob,
                            onRefreshDownloads = onRefreshDownloads,
                            onToggleKeepFavoritesDownloaded = onToggleKeepFavoritesDownloaded,
                            onDeleteAllDownloads = onDeleteAllDownloads,
                            onAlbumSelected = onAlbumSelected,
                            onAlbumFavoriteToggled = onAlbumFavoriteToggled,
                            onMixAlbumSelected = onMixAlbumSelected,
                            onAlbumPlay = onAlbumPlay,
                            onAlbumRadio = onAlbumRadio,
                            onAlbumDownload = onAlbumDownload,
                            onAlbumAddToQueue = onAlbumAddToQueue,
                            onAlbumAddToPlaylist = onAlbumAddToPlaylist,
                            onAlbumCreatePlaylistAndAdd = onAlbumCreatePlaylistAndAdd,
                            onAlbumTrackSelected = onAlbumTrackSelected,
                            onAlbumTrackDownload = onAlbumTrackDownload,
                            onAlbumTrackAddToPlaylist = onAlbumTrackAddToPlaylist,
                            onAlbumTrackCreatePlaylistAndAdd = onAlbumTrackCreatePlaylistAndAdd,
                            onArtistSelected = onArtistSelected,
                            onArtistFavoriteToggled = onArtistFavoriteToggled,
                            onArtistRadio = onArtistRadio,
                            onArtistShuffle = onArtistShuffle,
                            onArtistAddToQueue = onArtistAddToQueue,
                            onArtistAddToPlaylist = onArtistAddToPlaylist,
                            onArtistCreatePlaylistAndAdd = onArtistCreatePlaylistAndAdd,
                            onArtistPopularPlay = onArtistPopularPlay,
                            onArtistPopularRadio = onArtistPopularRadio,
                            onArtistPopularAddToQueue = onArtistPopularAddToQueue,
                            onArtistPopularTrackSelected = onArtistPopularTrackSelected,
                            onArtistPopularTrackAddToQueue = onArtistPopularTrackAddToQueue,
                            onArtistPopularTrackDownload = onArtistPopularTrackDownload,
                            onArtistPopularTrackAddToPlaylist = onArtistPopularTrackAddToPlaylist,
                            onArtistPopularTrackCreatePlaylistAndAdd = onArtistPopularTrackCreatePlaylistAndAdd,
                            onFindSimilarArtists = onFindSimilarArtists,
                            onSimilarArtistSelected = onSimilarArtistSelected,
                            onSimilarArtistExternalSelected = onSimilarArtistExternalSelected,
                            onPlaylistSelected = onPlaylistSelected,
                            onPlaylistSortModeChanged = onPlaylistSortModeChanged,
                            onPlaylistPlay = onPlaylistPlay,
                            onPlaylistAddToQueue = onPlaylistAddToQueue,
                            onPlaylistAddToPlaylist = onPlaylistAddToPlaylist,
                            onPlaylistCreatePlaylistAndAdd = onPlaylistCreatePlaylistAndAdd,
                            onPlaylistCopy = onPlaylistCopy,
                            onPlaylistRename = onPlaylistRename,
                            onPlaylistDelete = onPlaylistDelete,
                            onStandardPlaylistUpdate = onStandardPlaylistUpdate,
                            onMediaItemAction = onMediaItemAction,
                            onSmartPlaylistSave = onSmartPlaylistSave,
                            onSmartPlaylistUpdate = onSmartPlaylistUpdate,
                            onSmartPlaylistSaveWithPassword = onSmartPlaylistSaveWithPassword,
                            onSmartPlaylistUpdateWithPassword = onSmartPlaylistUpdateWithPassword,
                            onSmartPlaylistLoad = onSmartPlaylistLoad,
                            onPlaylistBack = onPlaylistBack,
                            onPlaylistTrackSelected = onPlaylistTrackSelected,
                            onTrackAddToQueue = onTrackAddToQueue,
                            onTrackAction = onTrackAction,
                            onRecentRadioSelected = onRecentRadioSelected,
                            onMixBuilderSelected = onMixBuilderSelected,
                            onRadioStationSelected = onRadioStationSelected,
                            onRadioStationSave = onRadioStationSave,
                            onStationAction = onStationAction,
                            onHomeStationSelected = onHomeStationSelected,
                            onSonicDiscoveryTrackAction = onSonicDiscoveryTrackAction,
                            onOpenNowPlaying = onOpenNowPlaying,
                            onCloseNowPlaying = onCloseNowPlaying,
                            nowPlayingActions = nowPlayingActions,
                        )
                    }
                }
                if (!showFullNowPlaying) {
                    if (connected && !editingConnection && !restoringConnection && nowPlaying != null) {
                        NaviampMiniNowPlaying(
                            nowPlaying = nowPlaying,
                            colors = colors,
                            onOpen = onOpenNowPlaying,
                            actions = nowPlayingActions,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                    }
                    SharedBottomNavigationBar(
                        colors = colors,
                        selectedRoute = selectedRoute,
                        onRouteSelected = {
                            onCloseNowPlaying()
                            onRouteSelected(it)
                        },
                    )
                }
            }
        }
    }

    availableUpdate?.let { update ->
        AlertDialog(
            onDismissRequest = { availableUpdate = null },
            title = { Text("Naviamp Update Available") },
            text = {
                Text("${update.name} is available. You are currently running ${about.version}.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        availableUpdate = null
                        uriHandler.openUri(update.releaseUrl)
                    },
                ) {
                    Text("View Release")
                }
            },
            dismissButton = {
                TextButton(onClick = { availableUpdate = null }) {
                    Text("Later")
                }
            },
        )
    }
    }
}

@Composable
private fun RestoringConnectionCard(
    status: String,
    colors: NaviampColors,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.controlSurface.copy(alpha = 0.72f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Restoring connection", color = colors.primaryText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(status, color = colors.secondaryText, fontSize = 13.sp)
    }
}

@Composable
fun NaviampConnectionForm(
    form: ConnectionFormState,
    colors: NaviampColors,
    isReconnect: Boolean,
    isConnecting: Boolean = false,
    connectionStatus: String? = null,
    settingsSyncStatus: String? = null,
    availableMusicFolders: List<ConnectionFormMusicFolder> = emptyList(),
    musicFoldersStatus: String? = null,
    modifier: Modifier = Modifier,
    onFormChanged: (ConnectionFormState) -> Unit,
    onConnect: () -> Unit,
    onImportSettingsSyncFile: (() -> Unit)? = null,
    onCancel: (() -> Unit)?,
) {
    var advancedVisible by remember { mutableStateOf(false) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsSectionTitle("Connection Details", colors)
        if (isReconnect) {
            Text(
                "Saved credentials loaded. Leave password blank to reuse them.",
                color = colors.mutedText,
                fontSize = 11.sp,
            )
        }
        onImportSettingsSyncFile?.let { importSettings ->
            ConnectionFormTextAction(
                label = "Import provider settings",
                colors = colors,
                enabled = !isConnecting,
                onClick = importSettings,
            )
            settingsSyncStatus?.let {
                Text(it, color = colors.secondaryText, fontSize = 12.sp)
            }
        }
        NaviampTextField(
            value = form.displayName,
            onValueChange = { onFormChanged(form.copy(displayName = it)) },
            label = "Connection name (optional)",
            colors = colors,
        )
        NaviampTextField(
            value = form.serverUrl,
            onValueChange = { onFormChanged(form.copy(serverUrl = it)) },
            label = "Server URL",
            colors = colors,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            NaviampTextField(
                value = form.username,
                onValueChange = { onFormChanged(form.copy(username = it)) },
                label = "Username",
                colors = colors,
                modifier = Modifier.weight(1f),
            )
            NaviampTextField(
                value = form.password,
                onValueChange = { onFormChanged(form.copy(password = it)) },
                label = "Password",
                colors = colors,
                isPassword = true,
                forceFloatingLabel = isReconnect,
                modifier = Modifier.weight(1f),
            )
        }
        ConnectionFormTextAction(
            label = if (advancedVisible) "Hide Advanced" else "Show Advanced",
            colors = colors,
            onClick = { advancedVisible = !advancedVisible },
        )
        if (advancedVisible) {
            SettingsSectionTitle("Libraries", colors)
            MusicFolderMultiSelect(
                selectedIds = form.selectedMusicFolderIds,
                availableFolders = availableMusicFolders,
                status = musicFoldersStatus,
                colors = colors,
                onSelectedIdsChanged = { ids ->
                    onFormChanged(form.copy(selectedMusicFolderIds = ids))
                },
            )
            SettingsSectionTitle("TLS", colors)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Checkbox(
                    checked = form.skipTlsVerification,
                    onCheckedChange = { onFormChanged(form.copy(skipTlsVerification = it)) },
                )
                Text("Skip TLS certificate verification", color = colors.secondaryText, fontSize = 13.sp)
            }
            NaviampTextField(
                value = form.customCertificatePath,
                onValueChange = { onFormChanged(form.copy(customCertificatePath = it)) },
                label = "Trusted certificate or CA file",
                colors = colors,
                enabled = !form.skipTlsVerification,
            )
            SettingsSectionTitle("mTLS", colors)
            NaviampTextField(
                value = form.clientCertificatePath,
                onValueChange = { onFormChanged(form.copy(clientCertificatePath = it)) },
                label = "Client certificate PKCS12 file",
                colors = colors,
            )
            NaviampTextField(
                value = form.clientCertificatePassword,
                onValueChange = { onFormChanged(form.copy(clientCertificatePassword = it)) },
                label = "Client certificate password",
                colors = colors,
                isPassword = true,
            )
            SettingsSectionTitle("Fallback URLs", colors)
            form.secondaryUrls.forEachIndexed { index, entry ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    NaviampTextField(
                        value = entry.url,
                        onValueChange = { value ->
                            onFormChanged(form.copy(
                                secondaryUrls = form.secondaryUrls.updateAt(index, entry.copy(url = value)),
                            ))
                        },
                        label = "URL",
                        colors = colors,
                        modifier = Modifier.weight(1f),
                    )
                    NaviampTextField(
                        value = entry.label,
                        onValueChange = { value ->
                            onFormChanged(form.copy(
                                secondaryUrls = form.secondaryUrls.updateAt(index, entry.copy(label = value)),
                            ))
                        },
                        label = "Label",
                        colors = colors,
                        modifier = Modifier.weight(0.65f),
                    )
                    TextButton(
                        onClick = {
                            onFormChanged(form.copy(secondaryUrls = form.secondaryUrls.removeAt(index)))
                        },
                    ) {
                        Text("Remove", color = colors.secondaryText)
                    }
                }
            }
            ConnectionFormTextAction(
                label = "Add fallback URL",
                colors = colors,
                onClick = {
                    onFormChanged(form.copy(secondaryUrls = form.secondaryUrls + ConnectionFormSecondaryUrl()))
                },
            )
            SettingsSectionTitle("Headers", colors)
            form.customHeaders.forEachIndexed { index, header ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        NaviampTextField(
                            value = header.name,
                            onValueChange = { value ->
                                onFormChanged(form.copy(
                                    customHeaders = form.customHeaders.updateAt(index, header.copy(name = value)),
                                ))
                            },
                            label = "Header name",
                            colors = colors,
                            modifier = Modifier.weight(1f),
                        )
                        NaviampTextField(
                            value = header.value,
                            onValueChange = { value ->
                                onFormChanged(form.copy(
                                    customHeaders = form.customHeaders.updateAt(index, header.copy(value = value)),
                                ))
                            },
                            label = "Header value",
                            colors = colors,
                            isPassword = header.valueIsSecret,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = {
                                onFormChanged(form.copy(customHeaders = form.customHeaders.removeAt(index)))
                            },
                        ) {
                            Text("Remove", color = colors.secondaryText)
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Checkbox(
                            checked = header.valueIsSecret,
                            onCheckedChange = { checked ->
                                onFormChanged(form.copy(
                                    customHeaders = form.customHeaders.updateAt(index, header.copy(valueIsSecret = checked)),
                                ))
                            },
                        )
                        Text("Treat value as secret; do not sync it", color = colors.secondaryText, fontSize = 12.sp)
                    }
                }
            }
            ConnectionFormTextAction(
                label = "Add header",
                colors = colors,
                onClick = {
                    onFormChanged(form.copy(customHeaders = form.customHeaders + ConnectionFormHeader()))
                },
            )
        }
        connectionStatus?.let {
            Text(it, color = colors.secondaryText, fontSize = 11.sp)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PrimaryButton(
                label = if (isConnecting) "Connecting" else if (isReconnect) "Save and connect" else "Connect",
                colors = colors,
                enabled = !isConnecting,
                onClick = onConnect,
            )
            onCancel?.let {
                TextButton(enabled = !isConnecting, onClick = it) {
                    Text("Cancel", color = colors.secondaryText)
                }
            }
        }
    }
}

@Composable
private fun ConnectionFormTextAction(
    label: String,
    colors: NaviampColors,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    TextButton(
        enabled = enabled,
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(
            contentColor = colors.primaryText,
            containerColor = colors.controlSurface.copy(alpha = 0.42f),
            disabledContentColor = colors.secondaryText.copy(alpha = 0.78f),
            disabledContainerColor = colors.controlSurface.copy(alpha = 0.18f),
        ),
    ) {
        Text(label, fontWeight = FontWeight.SemiBold)
    }
}

private fun <T> List<T>.updateAt(index: Int, value: T): List<T> =
    mapIndexed { itemIndex, item -> if (itemIndex == index) value else item }

private fun <T> List<T>.removeAt(index: Int): List<T> =
    filterIndexed { itemIndex, _ -> itemIndex != index }

@Composable
private fun MusicFolderMultiSelect(
    selectedIds: List<String>,
    availableFolders: List<ConnectionFormMusicFolder>,
    status: String?,
    colors: NaviampColors,
    onSelectedIdsChanged: (List<String>) -> Unit,
) {
    val selectedSet = selectedIds.toSet()
    val knownIds = availableFolders.map { it.id }.toSet()
    val unknownSelected = selectedIds
        .filterNot { it in knownIds }
        .map { id -> ConnectionFormMusicFolder(id = id, name = id) }
    val choices = availableFolders + unknownSelected

    status?.let {
        Text(it, color = colors.mutedText, fontSize = 11.sp)
    }
    if (choices.isEmpty()) {
        Text(
            "Connect or enter credentials to load available libraries.",
            color = colors.secondaryText,
            fontSize = 12.sp,
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        choices.forEach { folder ->
            val checked = folder.id in selectedSet
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .clickable {
                        onSelectedIdsChanged(
                            selectedIds.toggleSelectedMusicFolderId(
                                id = folder.id,
                                requireOne = choices.isNotEmpty(),
                            ),
                        )
                    }
                    .padding(horizontal = 2.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = null,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = folder.name,
                        color = colors.primaryText,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (folder.defaultSelected) "Default library" else "ID: ${folder.id}",
                        color = colors.mutedText,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectedContent(
    colors: NaviampColors,
    selectedRoute: SharedRoute,
    home: SharedHomeUi,
    homeRefreshing: Boolean,
    query: String,
    searchResults: SharedSearchResultsUi,
    artistMixBuilder: SharedArtistMixBuilderUi,
    albumMixBuilder: SharedAlbumMixBuilderUi,
    genreMixBuilder: SharedGenreMixBuilderUi,
    sonicPathBuilder: SharedSonicPathBuilderUi,
    sonicMixBuilder: SharedSonicMixBuilderUi,
    libraryArtists: List<SharedMediaItemUi>,
    libraryQuery: String,
    librarySyncStatus: NaviampLibrarySyncStatusUi,
    downloads: List<NaviampDownloadedTrackUi>,
    downloadBytes: Long,
    maxDownloadBytes: Long,
    offlineDashboard: NaviampOfflineDashboardUi,
    downloadStatus: String?,
    downloadJobs: List<DownloadJob>,
    keepFavoritesDownloaded: Boolean,
    downloadLocations: List<NaviampStorageLocationUi>,
    audioCacheLocations: List<NaviampStorageLocationUi>,
    selectedDownloadLocationId: String?,
    selectedAudioCacheLocationId: String?,
    playlistItems: List<SharedMediaItemUi>,
    recentPlaylistIds: List<String>,
    playlistSortMode: SharedPlaylistSortMode,
    playlistChoices: List<NaviampPlaylistChoiceUi>,
    playlistActionStatus: String?,
    playlistRefreshing: Boolean,
    radioStations: List<InternetRadioStation>,
    radioRefreshing: Boolean,
    albumDetail: SharedAlbumDetailUi?,
    artistDetail: SharedArtistDetailUi?,
    playlistDetail: SharedPlaylistDetailUi?,
    nowPlaying: NowPlayingUi?,
    nowPlayingOpen: Boolean,
    visualizerBandsProvider: () -> List<Float>,
    selectedVisualizer: NaviampVisualizer,
    interfaceSettings: InterfaceSettings,
    playbackSettings: PlaybackSettings,
    cacheSettings: CacheSettings,
    diagnostics: NaviampDiagnosticsUi,
    about: NaviampAboutUi,
    savedConnections: List<NaviampSavedConnectionUi>,
    isConnectionFormOpen: Boolean,
    isConnecting: Boolean,
    connectionStatus: String?,
    settingsSyncStatus: String?,
    availableMusicFolders: List<ConnectionFormMusicFolder>,
    musicFoldersStatus: String?,
    connectionForm: ConnectionFormState,
    hasSavedConnection: Boolean,
    supportsReplayGain: Boolean = false,
    supportsGapless: Boolean = true,
    supportsCrossfade: Boolean = false,
    supportsEqualizer: Boolean = false,
    supportsSonicSimilarity: Boolean = false,
    showMobileNetworkQuality: Boolean = false,
    onEditConnection: () -> Unit,
    onNewConnection: () -> Unit,
    onEditSavedConnection: (NaviampSavedConnectionUi) -> Unit,
    onConnectSavedConnection: (NaviampSavedConnectionUi) -> Unit,
    onDeleteSavedConnection: (NaviampSavedConnectionUi) -> Unit,
    onImportSettingsSyncFile: (() -> Unit)?,
    onChooseSettingsSyncFolder: (() -> Unit)?,
    onImportSettingsSyncFolder: (() -> Unit)?,
    onExportSettingsSyncFolder: (() -> Unit)?,
    settingsSyncAutoExportEnabled: Boolean,
    onSettingsSyncAutoExportChanged: ((Boolean) -> Unit)?,
    onConnectionFormChanged: (ConnectionFormState) -> Unit,
    onConnect: () -> Unit,
    onCancelEditConnection: () -> Unit,
    onInterfaceSettingsChanged: (InterfaceSettings) -> Unit,
    onPlaybackSettingsChanged: (PlaybackSettings) -> Unit,
    onPlaybackSettingsChangedAndRedownload: (PlaybackSettings) -> Unit,
    onCacheSettingsChanged: (CacheSettings) -> Unit,
    onDownloadLocationChanged: (NaviampStorageLocationUi) -> Unit,
    onAudioCacheLocationChanged: (NaviampStorageLocationUi) -> Unit,
    onQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onClearSearch: () -> Unit,
    onArtistMixQueryChanged: (String) -> Unit,
    onArtistMixSearch: () -> Unit,
    onArtistMixArtistSelected: (SharedMediaItemUi) -> Unit,
    onArtistMixArtistRemoved: (SharedMediaItemUi) -> Unit,
    onArtistMixReset: () -> Unit,
    onArtistMixPlay: () -> Unit,
    onAlbumMixQueryChanged: (String) -> Unit,
    onAlbumMixSearch: () -> Unit,
    onAlbumMixAlbumSelected: (SharedMediaItemUi) -> Unit,
    onAlbumMixAlbumRemoved: (SharedMediaItemUi) -> Unit,
    onAlbumMixReset: () -> Unit,
    onAlbumMixPlay: () -> Unit,
    onGenreMixQueryChanged: (String) -> Unit,
    onGenreMixSearch: () -> Unit,
    onGenreMixGenreSelected: (SharedGenreMixItemUi) -> Unit,
    onGenreMixGenreRemoved: (SharedGenreMixItemUi) -> Unit,
    onGenreMixReset: () -> Unit,
    onGenreMixPlay: () -> Unit,
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
    onLibraryQueryChanged: (String) -> Unit,
    onRefreshHome: () -> Unit,
    onRefreshLibrary: () -> Unit,
    onLoadMoreLibrary: () -> Unit,
    onRefreshPlaylists: () -> Unit,
    onRefreshRadioStations: () -> Unit,
    onTrackSelected: (SharedTrackRowUi) -> Unit,
    onDownloadedTrackAction: (DownloadedTrackActionRequest) -> Unit,
    onCancelDownloadJob: (String) -> Unit,
    onRetryDownloadJob: (String) -> Unit,
    onRefreshDownloads: () -> Unit,
    onToggleKeepFavoritesDownloaded: () -> Unit,
    onDeleteAllDownloads: () -> Unit,
    onAlbumSelected: (SharedMediaItemUi) -> Unit,
    onAlbumFavoriteToggled: (SharedMediaItemUi) -> Unit,
    onMixAlbumSelected: (SharedMediaItemUi) -> Unit,
    onAlbumPlay: (SharedAlbumDetailUi, Boolean) -> Unit,
    onAlbumRadio: (SharedAlbumDetailUi) -> Unit,
    onAlbumDownload: (SharedAlbumDetailUi) -> Unit,
    onAlbumAddToQueue: (SharedAlbumDetailUi) -> Unit,
    onAlbumAddToPlaylist: (SharedAlbumDetailUi, NaviampPlaylistChoiceUi?) -> Unit,
    onAlbumCreatePlaylistAndAdd: (SharedAlbumDetailUi, String) -> Unit,
    onAlbumTrackDownload: (SharedTrackRowUi) -> Unit,
    onAlbumTrackAddToPlaylist: (SharedTrackRowUi, NaviampPlaylistChoiceUi?) -> Unit,
    onAlbumTrackCreatePlaylistAndAdd: (SharedTrackRowUi, String) -> Unit,
    onArtistSelected: (SharedMediaItemUi) -> Unit,
    onArtistFavoriteToggled: (SharedMediaItemUi) -> Unit,
    onArtistRadio: (SharedArtistDetailUi) -> Unit,
    onArtistShuffle: (SharedArtistDetailUi) -> Unit,
    onArtistAddToQueue: (SharedArtistDetailUi) -> Unit,
    onArtistAddToPlaylist: (SharedArtistDetailUi, NaviampPlaylistChoiceUi?) -> Unit,
    onArtistCreatePlaylistAndAdd: (SharedArtistDetailUi, String) -> Unit,
    onArtistPopularPlay: (SharedArtistDetailUi) -> Unit,
    onArtistPopularRadio: (SharedArtistDetailUi) -> Unit,
    onArtistPopularAddToQueue: (SharedArtistDetailUi) -> Unit,
    onArtistPopularTrackSelected: (SharedTrackRowUi) -> Unit,
    onAlbumTrackSelected: (SharedTrackRowUi) -> Unit,
    onArtistPopularTrackAddToQueue: (SharedTrackRowUi) -> Unit,
    onArtistPopularTrackDownload: (SharedTrackRowUi) -> Unit,
    onArtistPopularTrackAddToPlaylist: (SharedTrackRowUi, NaviampPlaylistChoiceUi?) -> Unit,
    onArtistPopularTrackCreatePlaylistAndAdd: (SharedTrackRowUi, String) -> Unit,
    onFindSimilarArtists: (SharedArtistDetailUi) -> Unit,
    onSimilarArtistSelected: (SharedSimilarArtistUi) -> Unit,
    onSimilarArtistExternalSelected: (String) -> Unit,
    onPlaylistSelected: (SharedMediaItemUi) -> Unit,
    onPlaylistSortModeChanged: (SharedPlaylistSortMode) -> Unit,
    onPlaylistPlay: (SharedMediaItemUi, Boolean) -> Unit,
    onPlaylistAddToQueue: (SharedPlaylistDetailUi) -> Unit,
    onPlaylistAddToPlaylist: (SharedPlaylistDetailUi, NaviampPlaylistChoiceUi?) -> Unit,
    onPlaylistCreatePlaylistAndAdd: (SharedPlaylistDetailUi, String) -> Unit,
    onPlaylistCopy: (SharedPlaylistDetailUi, String, Boolean) -> Unit,
    onPlaylistRename: (SharedMediaItemUi, String) -> Unit,
    onPlaylistDelete: (SharedMediaItemUi) -> Unit,
    onStandardPlaylistUpdate: suspend (SharedMediaItemUi, List<SharedTrackRowUi>) -> Unit,
    onMediaItemAction: (SharedMediaItemActionRequest) -> Unit,
    onSmartPlaylistSave: suspend (SmartPlaylistDefinition) -> Unit,
    onSmartPlaylistUpdate: suspend (SharedMediaItemUi, SmartPlaylistDefinition) -> Unit,
    onSmartPlaylistSaveWithPassword: suspend (SmartPlaylistDefinition, String) -> Unit,
    onSmartPlaylistUpdateWithPassword: suspend (SharedMediaItemUi, SmartPlaylistDefinition, String) -> Unit,
    onSmartPlaylistLoad: suspend (SharedMediaItemUi) -> SmartPlaylistDefinition,
    onPlaylistBack: () -> Unit,
    onPlaylistTrackSelected: (SharedTrackRowUi) -> Unit,
    onTrackAddToQueue: (SharedTrackRowUi) -> Unit,
    onTrackAction: (SharedTrackRowActionRequest) -> Unit,
    onRecentRadioSelected: (SharedMediaItemUi) -> Unit,
    onMixBuilderSelected: (SharedMixBuilderUi) -> Unit,
    onRadioStationSelected: (InternetRadioStation) -> Unit,
    onRadioStationSave: (InternetRadioStation) -> Unit,
    onStationAction: (StationRowActionRequest) -> Unit,
    onHomeStationSelected: (SharedHomeStationUi) -> Unit,
    onSonicDiscoveryTrackAction: (SharedHomeDiscoveryTrackActionRequest) -> Unit,
    onOpenNowPlaying: () -> Unit,
    onCloseNowPlaying: () -> Unit,
    nowPlayingActions: NaviampNowPlayingActions,
    onClearCache: () -> Unit,
    onClearLibrary: () -> Unit,
    onResetDatabase: () -> Unit,
) {
    var saveSonicPathDialogOpen by remember { mutableStateOf(false) }
    var saveSonicMixDialogOpen by remember { mutableStateOf(false) }
    val nowPlayingPlayerColors = if (nowPlayingOpen && nowPlaying != null) {
        rememberPlatformCoverArtPlayerColors(nowPlaying.coverArtUrl, colors)
    } else {
        NaviampPlayerColors.fallback(colors)
    }

    when {
        nowPlayingOpen && nowPlaying != null -> FullNowPlaying(
            nowPlaying = nowPlaying,
            colors = colors,
            playerColors = nowPlayingPlayerColors,
            visualizerBandsProvider = visualizerBandsProvider,
            selectedVisualizer = selectedVisualizer,
            actions = nowPlayingActions,
            displaySettings = interfaceSettings.nowPlaying,
        )
        selectedRoute == SharedRoute.Settings -> SettingsContent(
            colors = colors,
            interfaceSettings = interfaceSettings,
            playbackSettings = playbackSettings,
            cacheSettings = cacheSettings,
            diagnostics = diagnostics,
            about = about,
            savedConnections = savedConnections,
            isConnectionFormOpen = isConnectionFormOpen,
            isConnecting = isConnecting,
            connectionStatus = connectionStatus,
            settingsSyncStatus = settingsSyncStatus,
            availableMusicFolders = availableMusicFolders,
            musicFoldersStatus = musicFoldersStatus,
            connectionForm = connectionForm,
            hasSavedConnection = hasSavedConnection,
            supportsReplayGain = supportsReplayGain,
            supportsGapless = supportsGapless,
            supportsCrossfade = supportsCrossfade,
            supportsEqualizer = supportsEqualizer,
            supportsSonicSimilarity = supportsSonicSimilarity,
            showMobileNetworkQuality = showMobileNetworkQuality,
            downloadBytes = downloadBytes,
            downloadLocations = downloadLocations,
            audioCacheLocations = audioCacheLocations,
            selectedDownloadLocationId = selectedDownloadLocationId,
            selectedAudioCacheLocationId = selectedAudioCacheLocationId,
            onEditConnection = onEditConnection,
            onNewConnection = onNewConnection,
            onEditSavedConnection = onEditSavedConnection,
            onConnectSavedConnection = onConnectSavedConnection,
            onDeleteSavedConnection = onDeleteSavedConnection,
            onImportSettingsSyncFile = onImportSettingsSyncFile,
            onChooseSettingsSyncFolder = onChooseSettingsSyncFolder,
            onImportSettingsSyncFolder = onImportSettingsSyncFolder,
            onExportSettingsSyncFolder = onExportSettingsSyncFolder,
            settingsSyncAutoExportEnabled = settingsSyncAutoExportEnabled,
            onSettingsSyncAutoExportChanged = onSettingsSyncAutoExportChanged,
            onConnectionFormChanged = onConnectionFormChanged,
            onConnect = onConnect,
            onCancelConnectionForm = onCancelEditConnection,
            onInterfaceSettingsChanged = onInterfaceSettingsChanged,
            onPlaybackSettingsChanged = onPlaybackSettingsChanged,
            onPlaybackSettingsChangedAndRedownload = onPlaybackSettingsChangedAndRedownload,
            onCacheSettingsChanged = onCacheSettingsChanged,
            onDownloadLocationChanged = onDownloadLocationChanged,
            onAudioCacheLocationChanged = onAudioCacheLocationChanged,
            onClearCache = onClearCache,
            onClearLibrary = onClearLibrary,
            onResetDatabase = onResetDatabase,
        )
        albumDetail != null -> AlbumDetailContent(
            colors = colors,
            detail = albumDetail,
            onBack = onCloseNowPlaying,
            onPlayAlbum = { onAlbumPlay(albumDetail, false) },
            onShuffleAlbum = { onAlbumPlay(albumDetail, true) },
            onAlbumRadio = { onAlbumRadio(albumDetail) },
            onAlbumDownload = { onAlbumDownload(albumDetail) },
            onAlbumAddToQueue = { onAlbumAddToQueue(albumDetail) },
            onAlbumAddToPlaylist = { playlist -> onAlbumAddToPlaylist(albumDetail, playlist) },
            onAlbumCreatePlaylistAndAdd = { name -> onAlbumCreatePlaylistAndAdd(albumDetail, name) },
            onAlbumFavoriteToggled = { onAlbumFavoriteToggled(albumDetail.album) },
            onTrackSelected = onAlbumTrackSelected,
            onTrackAddToQueue = { track ->
                onTrackAction(SharedTrackRowActionRequest(track, SharedTrackRowAction.AddToQueue))
            },
            onTrackDownload = { track ->
                onTrackAction(SharedTrackRowActionRequest(track, SharedTrackRowAction.Download))
            },
            onTrackAddToPlaylist = { track, playlist ->
                onTrackAction(
                    SharedTrackRowActionRequest(
                        track = track,
                        action = SharedTrackRowAction.AddToPlaylist,
                        playlistChoice = playlist,
                    ),
                )
            },
            onTrackCreatePlaylistAndAdd = { track, name ->
                onTrackAction(
                    SharedTrackRowActionRequest(
                        track = track,
                        action = SharedTrackRowAction.CreatePlaylistAndAdd,
                        playlistName = name,
                    ),
                )
            },
            playlistChoices = playlistChoices,
            playlistActionStatus = playlistActionStatus,
        )
        artistDetail != null -> ArtistDetailContent(
            colors = colors,
            detail = artistDetail,
            albumCollectionLayout = interfaceSettings.albumCollectionLayout,
            albumSortOrder = interfaceSettings.albumSortOrder,
            groupAlbumsByReleaseType = interfaceSettings.groupAlbumsByReleaseType,
            onBack = onCloseNowPlaying,
            onArtistRadio = { onArtistRadio(artistDetail) },
            onArtistShuffle = { onArtistShuffle(artistDetail) },
            onArtistAddToQueue = { onArtistAddToQueue(artistDetail) },
            onArtistAddToPlaylist = { playlist -> onArtistAddToPlaylist(artistDetail, playlist) },
            onArtistCreatePlaylistAndAdd = { name -> onArtistCreatePlaylistAndAdd(artistDetail, name) },
            onArtistFavoriteToggled = { onArtistFavoriteToggled(artistDetail.artist) },
            onPopularPlay = { onArtistPopularPlay(artistDetail) },
            onPopularRadio = { onArtistPopularRadio(artistDetail) },
            onPopularAddToQueue = { onArtistPopularAddToQueue(artistDetail) },
            onPopularTrackSelected = onArtistPopularTrackSelected,
            onPopularTrackAddToQueue = { track ->
                onTrackAction(SharedTrackRowActionRequest(track, SharedTrackRowAction.AddToQueue))
            },
            onPopularTrackDownload = { track ->
                onTrackAction(SharedTrackRowActionRequest(track, SharedTrackRowAction.Download))
            },
            onPopularTrackAddToPlaylist = { track, playlist ->
                onTrackAction(
                    SharedTrackRowActionRequest(
                        track = track,
                        action = SharedTrackRowAction.AddToPlaylist,
                        playlistChoice = playlist,
                    ),
                )
            },
            onPopularTrackCreatePlaylistAndAdd = { track, name ->
                onTrackAction(
                    SharedTrackRowActionRequest(
                        track = track,
                        action = SharedTrackRowAction.CreatePlaylistAndAdd,
                        playlistName = name,
                    ),
                )
            },
            onFindSimilarArtists = { onFindSimilarArtists(artistDetail) },
            onSimilarArtistSelected = onSimilarArtistSelected,
            onSimilarArtistExternalSelected = onSimilarArtistExternalSelected,
            onAlbumSelected = onAlbumSelected,
            onAlbumAction = onMediaItemAction,
            onAlbumFavoriteToggled = onAlbumFavoriteToggled,
            playlistChoices = playlistChoices,
            playlistActionStatus = playlistActionStatus,
        )
        playlistDetail != null -> PlaylistDetailContent(
            colors = colors,
            detail = playlistDetail,
            onBack = onPlaylistBack,
            onPlayPlaylist = { onPlaylistPlay(playlistDetail.playlist, false) },
            onShufflePlaylist = { onPlaylistPlay(playlistDetail.playlist, true) },
            onAddPlaylistToQueue = { onPlaylistAddToQueue(playlistDetail) },
            onDownloadPlaylist = {
                onMediaItemAction(
                    playlistDetail.playlist.actionRequest(
                        SharedMediaItemAction.Download,
                        kind = SharedMediaItemKind.Playlist,
                    ),
                )
            },
            onAddPlaylistToPlaylist = { playlist -> onPlaylistAddToPlaylist(playlistDetail, playlist) },
            onCreatePlaylistAndAddPlaylist = { name -> onPlaylistCreatePlaylistAndAdd(playlistDetail, name) },
            onCopyPlaylist = { name, deduplicate -> onPlaylistCopy(playlistDetail, name, deduplicate) },
            onRenamePlaylist = onPlaylistRename,
            onDeletePlaylist = onPlaylistDelete,
            onUpdateStandardPlaylist = onStandardPlaylistUpdate,
            onSmartPlaylistUpdate = onSmartPlaylistUpdate,
            onSmartPlaylistUpdateWithPassword = onSmartPlaylistUpdateWithPassword,
            onSmartPlaylistLoad = onSmartPlaylistLoad,
            onTrackSelected = onPlaylistTrackSelected,
            onTrackAddToQueue = { track ->
                onTrackAction(SharedTrackRowActionRequest(track, SharedTrackRowAction.AddToQueue))
            },
            playlistChoices = playlistChoices,
            availableLibraries = availableMusicFolders,
            selectedConnectionLibraryIds = connectionForm.selectedMusicFolderIds,
        )
        else -> when (selectedRoute) {
            SharedRoute.Home -> SharedHomeRoute(
                colors = colors,
                home = home,
                isRefreshing = homeRefreshing,
                onRefresh = onRefreshHome,
                onAlbumSelected = onAlbumSelected,
                onAlbumFavoriteToggled = onAlbumFavoriteToggled,
                onMixAlbumSelected = onMixAlbumSelected,
                onPlaylistSelected = onPlaylistSelected,
                onRecentRadioSelected = onRecentRadioSelected,
                onMixBuilderSelected = onMixBuilderSelected,
                onInternetRadioStationSelected = { item ->
                    radioStations.firstOrNull { it.id == item.id }?.let(onRadioStationSelected)
                },
                onHomeStationSelected = onHomeStationSelected,
                onSonicDiscoveryTrackAction = onSonicDiscoveryTrackAction,
                onRecentlyPlayedTrackAction = { request ->
                    if (request.action == SharedTrackRowAction.Select) {
                        onTrackSelected(request.track)
                    } else {
                        onTrackAction(request)
                    }
                },
            )
            SharedRoute.Playlists -> PullToRefreshRoute(
                isRefreshing = playlistRefreshing,
                onRefresh = onRefreshPlaylists,
                useScrollContainer = true,
            ) {
                PlaylistsContent(
                    colors = colors,
                    playlists = playlistItems,
                    recentPlaylistIds = recentPlaylistIds,
                    sortMode = playlistSortMode,
                    status = playlistActionStatus,
                    onSortModeChanged = onPlaylistSortModeChanged,
                    onPlaylistAction = onMediaItemAction,
                    onSmartPlaylistSave = onSmartPlaylistSave,
                    onSmartPlaylistUpdate = onSmartPlaylistUpdate,
                    onSmartPlaylistSaveWithPassword = onSmartPlaylistSaveWithPassword,
                    onSmartPlaylistUpdateWithPassword = onSmartPlaylistUpdateWithPassword,
                    onSmartPlaylistLoad = onSmartPlaylistLoad,
                    playlistChoices = playlistChoices,
                    availableLibraries = availableMusicFolders,
                    selectedConnectionLibraryIds = connectionForm.selectedMusicFolderIds,
                )
            }
            SharedRoute.Library -> PullToRefreshRoute(
                isRefreshing = librarySyncStatus.isSyncing,
                onRefresh = onRefreshLibrary,
            ) {
                LibraryContent(
                    colors = colors,
                    items = libraryArtists,
                    query = libraryQuery,
                    syncStatus = librarySyncStatus,
                    onQueryChanged = onLibraryQueryChanged,
                    onRefreshLibrary = onRefreshLibrary,
                    onLoadMore = onLoadMoreLibrary,
                    onArtistSelected = onArtistSelected,
                    onArtistFavoriteToggled = onArtistFavoriteToggled,
                )
            }
            SharedRoute.Search -> SearchContent(
                colors = colors,
                query = query,
                results = searchResults,
                onQueryChanged = onQueryChanged,
                onSearch = onSearch,
                onClearSearch = onClearSearch,
                onTrackSelected = onTrackSelected,
                onTrackAddToQueue = { track ->
                    onTrackAction(SharedTrackRowActionRequest(track, SharedTrackRowAction.AddToQueue))
                },
                onAlbumSelected = onAlbumSelected,
                onArtistSelected = onArtistSelected,
                onArtistFavoriteToggled = onArtistFavoriteToggled,
                onAlbumFavoriteToggled = onAlbumFavoriteToggled,
            )
            SharedRoute.ArtistMix -> Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    ArtistMixBuilderContent(
                        colors = colors,
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
                    PrimaryButton("Play Mix", colors, onClick = onArtistMixPlay)
                }
            }
            SharedRoute.AlbumMix -> Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    AlbumMixBuilderContent(
                        colors = colors,
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
                    PrimaryButton("Play Mix", colors, onClick = onAlbumMixPlay)
                }
            }
            SharedRoute.GenreMix -> Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    GenreMixBuilderContent(
                        colors = colors,
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
                    PrimaryButton("Play Mix", colors, onClick = onGenreMixPlay)
                }
            }
            SharedRoute.SonicPath -> Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    SonicPathBuilderContent(
                        colors = colors,
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = onSonicPathPlay, modifier = Modifier.weight(1f)) {
                            Text("Play Path")
                        }
                        Button(onClick = onSonicPathAddToQueue, modifier = Modifier.weight(1f)) {
                            Text("Add to Queue")
                        }
                        Button(onClick = { saveSonicPathDialogOpen = true }, modifier = Modifier.weight(1f)) {
                            Text("Save")
                        }
                    }
                }
            }
            SharedRoute.SonicMix -> Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    SonicMixBuilderContent(
                        colors = colors,
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = onSonicMixPlay, modifier = Modifier.weight(1f)) {
                            Text("Play Mix")
                        }
                        Button(onClick = onSonicMixAddToQueue, modifier = Modifier.weight(1f)) {
                            Text("Add to Queue")
                        }
                        Button(onClick = { saveSonicMixDialogOpen = true }, modifier = Modifier.weight(1f)) {
                            Text("Save")
                        }
                    }
                }
            }
            SharedRoute.Radio -> PullToRefreshRoute(
                isRefreshing = radioRefreshing,
                onRefresh = onRefreshRadioStations,
                useScrollContainer = true,
            ) {
                InternetRadioContent(
                    colors = colors,
                    stations = radioStations,
                    status = null,
                    onStationAction = onStationAction,
                    onSaveStation = onRadioStationSave,
                )
            }
            SharedRoute.Settings -> Unit
            SharedRoute.Downloads -> DownloadsContent(
                colors = colors,
                downloads = downloads,
                status = downloadStatus,
                downloadJobs = downloadJobs,
                keepFavoritesDownloaded = keepFavoritesDownloaded,
                maxDownloadBytes = maxDownloadBytes,
                offlineDashboard = offlineDashboard,
                playlistChoices = playlistChoices,
                playlistActionStatus = playlistActionStatus,
                onDownloadAction = onDownloadedTrackAction,
                onCancelDownloadJob = onCancelDownloadJob,
                onRetryDownloadJob = onRetryDownloadJob,
                onRefreshDownloads = onRefreshDownloads,
                onToggleKeepFavoritesDownloaded = onToggleKeepFavoritesDownloaded,
                onDeleteAllDownloads = onDeleteAllDownloads,
            )
        }
    }
    if (saveSonicPathDialogOpen) {
        SaveQueueAsPlaylistDialog(
            colors = colors,
            status = playlistActionStatus,
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
            colors = colors,
            status = playlistActionStatus,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PullToRefreshRoute(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    useScrollContainer: Boolean = false,
    content: @Composable () -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        if (useScrollContainer) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                content()
            }
        } else {
            content()
        }
    }
}

@Composable
private fun AlbumDetailContent(
    colors: NaviampColors,
    detail: SharedAlbumDetailUi,
    onBack: () -> Unit,
    onPlayAlbum: () -> Unit,
    onShuffleAlbum: () -> Unit,
    onAlbumRadio: () -> Unit,
    onAlbumDownload: () -> Unit,
    onAlbumAddToQueue: () -> Unit,
    onAlbumAddToPlaylist: (NaviampPlaylistChoiceUi?) -> Unit,
    onAlbumCreatePlaylistAndAdd: (String) -> Unit,
    onAlbumFavoriteToggled: () -> Unit,
    onTrackSelected: (SharedTrackRowUi) -> Unit,
    onTrackAddToQueue: (SharedTrackRowUi) -> Unit,
    onTrackDownload: (SharedTrackRowUi) -> Unit,
    onTrackAddToPlaylist: (SharedTrackRowUi, NaviampPlaylistChoiceUi?) -> Unit,
    onTrackCreatePlaylistAndAdd: (SharedTrackRowUi, String) -> Unit,
    playlistChoices: List<NaviampPlaylistChoiceUi>,
    playlistActionStatus: String?,
) {
    var addAlbumToPlaylistOpen by remember(detail.album.id) { mutableStateOf(false) }
    var trackForPlaylist by remember(detail.album.id) { mutableStateOf<SharedTrackRowUi?>(null) }
    var albumImageOpen by remember(detail.album.id) { mutableStateOf(false) }
    val handleTrackAction: (SharedTrackRowActionRequest) -> Unit = { request ->
        handleSharedTrackRowAction(
            request,
            SharedTrackRowActionHandlers(
                onSelect = onTrackSelected,
                onAddToQueue = onTrackAddToQueue,
                onDownload = onTrackDownload,
                onAddToPlaylist = { track, playlist ->
                    if (playlist == null) trackForPlaylist = track else onTrackAddToPlaylist(track, playlist)
                },
                onCreatePlaylistAndAdd = onTrackCreatePlaylistAndAdd,
            ),
        )
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(NaviampIcons.Back, contentDescription = "Back", tint = colors.primaryText)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.clickable(
                    enabled = detail.album.coverArtUrl != null,
                    onClick = { albumImageOpen = true },
                ),
            ) {
                PlatformCoverArt(detail.album.coverArtUrl, colors, 96.dp, 8.dp)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                Text(
                    detail.album.title,
                    color = colors.primaryText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(detail.album.subtitle, color = colors.secondaryText, fontSize = 14.sp)
                if (detail.album.meta.isNotBlank()) {
                    Text(detail.album.meta, color = colors.mutedText, fontSize = 12.sp)
                }
                Text(
                    listOfNotNull(
                        "${detail.tracks.size} tracks",
                        detail.totalDurationLabel.takeIf { it.isNotBlank() },
                    ).joinToString(" - "),
                    color = colors.mutedText,
                    fontSize = 12.sp,
                )
                NaviampResponsiveActionRow(
                    colors = colors,
                    actions = listOf(
                        NaviampDetailAction("Play album", NaviampTransportIcons.Play, onPlayAlbum, detail.tracks.isNotEmpty()),
                        NaviampDetailAction("Shuffle album", NaviampTransportIcons.Shuffle, onShuffleAlbum, detail.tracks.size > 1),
                        NaviampDetailAction("Start album radio", NaviampTransportIcons.Radio, onAlbumRadio, detail.tracks.isNotEmpty()),
                        NaviampDetailAction("Download album", NaviampIcons.Downloads, onAlbumDownload, detail.tracks.isNotEmpty()),
                        NaviampDetailAction("Add album to queue", NaviampIcons.Queue, onAlbumAddToQueue, detail.tracks.isNotEmpty()),
                        NaviampDetailAction("Add album to playlist", NaviampIcons.Playlist, { addAlbumToPlaylistOpen = true }, detail.tracks.isNotEmpty()),
                        NaviampDetailAction(
                            if (detail.album.favoriteActive) "Remove album favorite" else "Favorite album",
                            NaviampTransportIcons.Heart,
                            onAlbumFavoriteToggled,
                            detail.album.canFavorite,
                        ),
                    ),
                )
            }
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            val reservePopularIndicatorSpace = detail.tracks.any { it.popular }
            detail.tracks.forEachIndexed { index, track ->
                TrackRow(
                    track.copy(meta = (index + 1).toString()),
                    colors,
                    onTrackSelected,
                    onAddToQueue = onTrackAddToQueue,
                    onDownload = onTrackDownload,
                    onAddToPlaylist = { selectedTrack -> trackForPlaylist = selectedTrack },
                    onTrackAction = handleTrackAction,
                    reservePopularIndicatorSpace = reservePopularIndicatorSpace,
                )
            }
        }
    }

    if (addAlbumToPlaylistOpen) {
        AddToPlaylistDialog(
            title = detail.album.title,
            colors = colors,
            playlists = playlistChoices,
            status = playlistActionStatus,
            onDismissRequest = { addAlbumToPlaylistOpen = false },
            onAddToExisting = { playlist ->
                addAlbumToPlaylistOpen = false
                onAlbumAddToPlaylist(playlist)
            },
            onCreateAndAdd = { name ->
                addAlbumToPlaylistOpen = false
                onAlbumCreatePlaylistAndAdd(name)
            },
        )
    }

    trackForPlaylist?.let { track ->
        AddToPlaylistDialog(
            title = track.title,
            colors = colors,
            playlists = playlistChoices,
            status = playlistActionStatus,
            onDismissRequest = { trackForPlaylist = null },
            onAddToExisting = { playlist ->
                trackForPlaylist = null
                handleTrackAction(
                    SharedTrackRowActionRequest(
                        track = track,
                        action = SharedTrackRowAction.AddToPlaylist,
                        playlistChoice = playlist,
                    ),
                )
            },
            onCreateAndAdd = { name ->
                trackForPlaylist = null
                handleTrackAction(
                    SharedTrackRowActionRequest(
                        track = track,
                        action = SharedTrackRowAction.CreatePlaylistAndAdd,
                        playlistName = name,
                    ),
                )
            },
        )
    }

    if (albumImageOpen) {
        ExpandedMediaImageDialog(
            imageUrl = detail.album.coverArtUrl,
            colors = colors,
            onDismissRequest = { albumImageOpen = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArtistDetailContent(
    colors: NaviampColors,
    detail: SharedArtistDetailUi,
    albumCollectionLayout: AlbumCollectionLayout,
    albumSortOrder: AlbumSortOrder,
    groupAlbumsByReleaseType: Boolean,
    onBack: () -> Unit,
    onArtistRadio: () -> Unit,
    onArtistShuffle: () -> Unit,
    onArtistAddToQueue: () -> Unit,
    onArtistAddToPlaylist: (NaviampPlaylistChoiceUi?) -> Unit,
    onArtistCreatePlaylistAndAdd: (String) -> Unit,
    onArtistFavoriteToggled: () -> Unit,
    onPopularPlay: () -> Unit,
    onPopularRadio: () -> Unit,
    onPopularAddToQueue: () -> Unit,
    onPopularTrackSelected: (SharedTrackRowUi) -> Unit,
    onPopularTrackAddToQueue: (SharedTrackRowUi) -> Unit,
    onPopularTrackDownload: (SharedTrackRowUi) -> Unit,
    onPopularTrackAddToPlaylist: (SharedTrackRowUi, NaviampPlaylistChoiceUi?) -> Unit,
    onPopularTrackCreatePlaylistAndAdd: (SharedTrackRowUi, String) -> Unit,
    onFindSimilarArtists: () -> Unit,
    onSimilarArtistSelected: (SharedSimilarArtistUi) -> Unit,
    onSimilarArtistExternalSelected: (String) -> Unit,
    onAlbumSelected: (SharedMediaItemUi) -> Unit,
    onAlbumFavoriteToggled: (SharedMediaItemUi) -> Unit,
    onAlbumAction: (SharedMediaItemActionRequest) -> Unit,
    playlistChoices: List<NaviampPlaylistChoiceUi>,
    playlistActionStatus: String?,
) {
    var addArtistToPlaylistOpen by remember(detail.artist.id) { mutableStateOf(false) }
    var popularTrackForPlaylist by remember(detail.artist.id) { mutableStateOf<SharedTrackRowUi?>(null) }
    var albumForPlaylist by remember(detail.artist.id) { mutableStateOf<SharedMediaItemUi?>(null) }
    var biographyExpanded by remember(detail.artist.id) { mutableStateOf(false) }
    var artistImageOpen by remember(detail.artist.id) { mutableStateOf(false) }
    val handleAlbumAction: (SharedMediaItemActionRequest) -> Unit = { request ->
        handleSharedMediaItemAction(
            request,
            SharedMediaItemActionHandlers(
                onSelect = { onAlbumAction(request) },
                onStartRadio = { onAlbumAction(request) },
                onAddToQueue = { onAlbumAction(request) },
                onDownload = { onAlbumAction(request) },
                onAddToPlaylist = { album, playlist ->
                    if (playlist == null) albumForPlaylist = album else onAlbumAction(request)
                },
                onCreatePlaylistAndAdd = { _, _ -> onAlbumAction(request) },
                onToggleFavorite = { onAlbumAction(request) },
            ),
        )
    }
    val handlePopularTrackAction: (SharedTrackRowActionRequest) -> Unit = { request ->
        handleSharedTrackRowAction(
            request,
            SharedTrackRowActionHandlers(
                onSelect = onPopularTrackSelected,
                onAddToQueue = onPopularTrackAddToQueue,
                onDownload = onPopularTrackDownload,
                onAddToPlaylist = { track, playlist ->
                    if (playlist == null) popularTrackForPlaylist = track else onPopularTrackAddToPlaylist(track, playlist)
                },
                onCreatePlaylistAndAdd = onPopularTrackCreatePlaylistAndAdd,
            ),
        )
    }
    val similarArtistsVisible = detail.similarArtists.isNotEmpty() || detail.similarArtistsStatus != null
    val albumMenuItems: (SharedMediaItemUi) -> List<NaviampRowMenuItem> = { album ->
        albumRowActions(
            canStartRadio = true,
            canDownload = true,
            canAddToQueue = true,
            canAddToPlaylist = true,
            canFavorite = false,
            favoriteActive = album.favoriteActive,
        ).mapNotNull { action ->
            val requestAction = when (action.action) {
                NaviampAction.StartAlbumRadio -> SharedMediaItemAction.StartRadio
                NaviampAction.DownloadAlbum -> SharedMediaItemAction.Download
                NaviampAction.AddToQueue -> SharedMediaItemAction.AddToQueue
                NaviampAction.AddToPlaylist -> SharedMediaItemAction.AddToPlaylist
                else -> null
            }
            requestAction?.let {
                NaviampRowMenuItem(
                    action.label,
                    action.icon,
                    { handleAlbumAction(album.actionRequest(it, kind = SharedMediaItemKind.Album)) },
                    action.enabled,
                )
            }
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(NaviampIcons.Back, contentDescription = "Back", tint = colors.primaryText)
            }
            Box(
                modifier = Modifier.clickable(
                    enabled = detail.artist.coverArtUrl != null,
                    onClick = { artistImageOpen = true },
                ),
            ) {
                PlatformCoverArt(detail.artist.coverArtUrl, colors, 64.dp, 32.dp)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(detail.artist.title, color = colors.primaryText, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    detail.localLibraryLabel.ifBlank { "${detail.albums.size} albums" },
                    color = colors.secondaryText,
                    fontSize = 13.sp,
                )
                detail.sourceContextLabel.takeIf { it.isNotBlank() }?.let { label ->
                    Text(
                        label,
                        color = colors.mutedText,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                NaviampResponsiveActionRow(
                    colors = colors,
                    actions = listOf(
                        NaviampDetailAction("Start artist radio", NaviampTransportIcons.Radio, onArtistRadio, detail.albums.isNotEmpty()),
                        NaviampDetailAction(
                            if (detail.artist.favoriteActive) "Remove artist favorite" else "Favorite artist",
                            NaviampTransportIcons.Heart,
                            onArtistFavoriteToggled,
                            detail.artist.canFavorite,
                        ),
                        NaviampDetailAction("Play popular tracks", NaviampTransportIcons.Play, onPopularPlay, detail.popularTracks.isNotEmpty()),
                        NaviampDetailAction(
                            if (similarArtistsVisible) "Hide similar artists" else "Find similar artists",
                            NaviampIcons.Artist,
                            onFindSimilarArtists,
                            selected = similarArtistsVisible,
                        ),
                        NaviampDetailAction("Add artist to queue", NaviampIcons.Queue, onArtistAddToQueue, detail.albums.isNotEmpty()),
                        NaviampDetailAction("Add artist to playlist", NaviampIcons.Playlist, { addArtistToPlaylistOpen = true }, detail.albums.isNotEmpty()),
                    ),
                )
                detail.biography
                    ?.normalizedBiography()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { biography ->
                        val showMoreLink = biography.length > 260
                        Text(
                            biography,
                            color = colors.secondaryText,
                            maxLines = if (biographyExpanded) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(
                                fontSize = 11.sp,
                                lineHeight = 13.sp,
                            ),
                        )
                        if (showMoreLink) {
                            Text(
                                if (biographyExpanded) "Less" else "More...",
                                color = colors.primaryText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable {
                                    biographyExpanded = !biographyExpanded
                                },
                            )
                        }
                    }
            }
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (similarArtistsVisible) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Similar Artists".uppercase(),
                        color = colors.primaryText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                    )
                    MiniPlayerIconButton(colors, true, NaviampIcons.Artist, "Hide similar artists", onFindSimilarArtists)
                }
                detail.similarArtistsStatus?.let {
                    Text(it, color = colors.secondaryText, fontSize = 11.sp)
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    detail.similarArtists.forEach { artist ->
                        SimilarArtistRow(
                            artist = artist,
                            colors = colors,
                            onSimilarArtistSelected = onSimilarArtistSelected,
                            onSimilarArtistExternalSelected = onSimilarArtistExternalSelected,
                        )
                    }
                }
            }
            if (detail.popularTracks.isNotEmpty() || detail.popularTracksStatus != null) {
                Text(
                    "Popular Tracks".uppercase(),
                    color = colors.primaryText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (detail.popularTracks.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            MiniPlayerIconButton(colors, true, NaviampTransportIcons.Play, "Play popular tracks", onPopularPlay)
                            MiniPlayerIconButton(colors, true, NaviampTransportIcons.Radio, "Start popular tracks radio", onPopularRadio)
                            MiniPlayerIconButton(colors, true, NaviampIcons.Queue, "Add popular tracks to queue", onPopularAddToQueue)
                        }
                    }
                    detail.popularTracksStatus?.let { status ->
                        Text(status, color = colors.secondaryText, fontSize = 11.sp)
                    }
                    detail.popularTracks.forEachIndexed { index, track ->
                        TrackRow(
                            track.copy(meta = (index + 1).toString()),
                            colors,
                            onPopularTrackSelected,
                            onAddToQueue = onPopularTrackAddToQueue,
                            onDownload = onPopularTrackDownload,
                            onAddToPlaylist = { selectedTrack -> popularTrackForPlaylist = selectedTrack },
                            onTrackAction = handlePopularTrackAction,
                        )
                    }
                }
            }
            Text("Discography", color = colors.primaryText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            if (detail.albums.isEmpty()) {
                Text("No albums found.", color = colors.secondaryText, fontSize = 13.sp)
            } else {
                val visibleSections = if (groupAlbumsByReleaseType) {
                    detail.albumSections
                } else {
                    listOf(SharedAlbumSectionUi("Albums", detail.albums))
                }.map { section ->
                    section.copy(albums = section.albums.sortedForAlbumDisplay(albumSortOrder))
                }
                visibleSections.forEach { section ->
                    Text(section.title.uppercase(), color = colors.primaryText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    if (albumCollectionLayout == AlbumCollectionLayout.Grid) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            section.albums.forEach { album ->
                                SharedAlbumGridTile(
                                    item = album,
                                    colors = colors,
                                    onClick = { onAlbumSelected(album) },
                                    menuItems = albumMenuItems(album),
                                    onFavoriteToggled = { selected ->
                                        handleAlbumAction(selected.actionRequest(SharedMediaItemAction.ToggleFavorite, kind = SharedMediaItemKind.Album))
                                    },
                                )
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            section.albums.forEach { album ->
                                SharedMediaRow(
                                    item = album,
                                    colors = colors,
                                    itemKind = SharedMediaItemKind.Album,
                                    onClick = { onAlbumSelected(album) },
                                    onItemAction = handleAlbumAction,
                                    menuItems = albumMenuItems(album),
                                    onFavoriteToggled = onAlbumFavoriteToggled,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (addArtistToPlaylistOpen) {
        AddToPlaylistDialog(
            title = detail.artist.title,
            colors = colors,
            playlists = playlistChoices,
            status = playlistActionStatus,
            onDismissRequest = { addArtistToPlaylistOpen = false },
            onAddToExisting = { playlist ->
                addArtistToPlaylistOpen = false
                onArtistAddToPlaylist(playlist)
            },
            onCreateAndAdd = { name ->
                addArtistToPlaylistOpen = false
                onArtistCreatePlaylistAndAdd(name)
            },
        )
    }

    popularTrackForPlaylist?.let { track ->
        AddToPlaylistDialog(
            title = track.title,
            colors = colors,
            playlists = playlistChoices,
            status = playlistActionStatus,
            onDismissRequest = { popularTrackForPlaylist = null },
            onAddToExisting = { playlist ->
                popularTrackForPlaylist = null
                handlePopularTrackAction(
                    SharedTrackRowActionRequest(
                        track = track,
                        action = SharedTrackRowAction.AddToPlaylist,
                        playlistChoice = playlist,
                    ),
                )
            },
            onCreateAndAdd = { name ->
                popularTrackForPlaylist = null
                handlePopularTrackAction(
                    SharedTrackRowActionRequest(
                        track = track,
                        action = SharedTrackRowAction.CreatePlaylistAndAdd,
                        playlistName = name,
                    ),
                )
            },
        )
    }

    albumForPlaylist?.let { album ->
        AddToPlaylistDialog(
            title = album.title,
            colors = colors,
            playlists = playlistChoices,
            status = playlistActionStatus,
            onDismissRequest = { albumForPlaylist = null },
            onAddToExisting = { playlist ->
                albumForPlaylist = null
                handleAlbumAction(
                    album.actionRequest(
                        SharedMediaItemAction.AddToPlaylist,
                        kind = SharedMediaItemKind.Album,
                        playlistChoice = playlist,
                    ),
                )
            },
            onCreateAndAdd = { name ->
                albumForPlaylist = null
                handleAlbumAction(
                    album.actionRequest(
                        SharedMediaItemAction.CreatePlaylistAndAdd,
                        kind = SharedMediaItemKind.Album,
                        playlistName = name,
                    ),
                )
            },
        )
    }

    if (artistImageOpen) {
        ExpandedMediaImageDialog(
            imageUrl = detail.artist.coverArtUrl,
            colors = colors,
            onDismissRequest = { artistImageOpen = false },
        )
    }
}

@Composable
fun ExpandedMediaImageDialog(
    imageUrl: String?,
    colors: NaviampColors,
    onDismissRequest: () -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(colors.controlSurface)
                .clickable(onClick = onDismissRequest)
                .padding(4.dp),
        ) {
            PlatformExpandedMediaImage(
                url = imageUrl,
                colors = colors,
                maxWidth = 320.dp,
                maxHeight = 420.dp,
            )
        }
    }
}

@Composable
private fun SimilarArtistRow(
    artist: SharedSimilarArtistUi,
    colors: NaviampColors,
    onSimilarArtistSelected: (SharedSimilarArtistUi) -> Unit,
    onSimilarArtistExternalSelected: (String) -> Unit,
) {
    val opensLocalArtist = artist.localArtistId != null
    val externalUrl = artist.externalUrl
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(5.dp))
            .background(Color.Black.copy(alpha = 0.12f))
            .clickable(enabled = opensLocalArtist || externalUrl != null) {
                if (opensLocalArtist) {
                    onSimilarArtistSelected(artist)
                } else if (externalUrl != null) {
                    onSimilarArtistExternalSelected(externalUrl)
                }
            }
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        PlatformCoverArt(artist.imageUrl, colors, 42.dp, 21.dp)
        Column(
            verticalArrangement = Arrangement.spacedBy(1.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                artist.title,
                color = colors.primaryText,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                artist.subtitle,
                color = colors.secondaryText,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!opensLocalArtist && externalUrl != null) {
            IconButton(
                onClick = { onSimilarArtistExternalSelected(externalUrl) },
                modifier = Modifier.size(34.dp),
            ) {
                Icon(
                    imageVector = NaviampIcons.ExternalLink,
                    contentDescription = "View in browser",
                    tint = colors.secondaryText,
                    modifier = Modifier.size(18.dp),
                )
            }
        } else {
            Icon(
                imageVector = NaviampIcons.ChevronRight,
                contentDescription = null,
                tint = colors.secondaryText,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun FullNowPlaying(
    nowPlaying: NowPlayingUi,
    colors: NaviampColors,
    playerColors: NaviampPlayerColors,
    visualizerBandsProvider: () -> List<Float>,
    selectedVisualizer: NaviampVisualizer,
    actions: NaviampNowPlayingActions,
    displaySettings: app.naviamp.domain.settings.NowPlayingDisplaySettings,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        NaviampNowPlayingPanel(
            nowPlaying = nowPlaying,
            colors = colors,
            visualizerBandsProvider = visualizerBandsProvider,
            selectedVisualizer = selectedVisualizer,
            visualizerColors = playerColors,
            actions = actions,
            displaySettings = displaySettings,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
    }
}

@Composable
private fun SettingsContent(
    colors: NaviampColors,
    interfaceSettings: InterfaceSettings,
    playbackSettings: PlaybackSettings,
    cacheSettings: CacheSettings,
    diagnostics: NaviampDiagnosticsUi,
    about: NaviampAboutUi,
    savedConnections: List<NaviampSavedConnectionUi>,
    isConnectionFormOpen: Boolean,
    isConnecting: Boolean,
    connectionStatus: String?,
    settingsSyncStatus: String?,
    availableMusicFolders: List<ConnectionFormMusicFolder>,
    musicFoldersStatus: String?,
    connectionForm: ConnectionFormState,
    hasSavedConnection: Boolean,
    supportsReplayGain: Boolean,
    supportsGapless: Boolean,
    supportsCrossfade: Boolean,
    supportsEqualizer: Boolean,
    supportsSonicSimilarity: Boolean,
    showMobileNetworkQuality: Boolean,
    downloadBytes: Long,
    downloadLocations: List<NaviampStorageLocationUi>,
    audioCacheLocations: List<NaviampStorageLocationUi>,
    selectedDownloadLocationId: String?,
    selectedAudioCacheLocationId: String?,
    onEditConnection: () -> Unit,
    onNewConnection: () -> Unit,
    onEditSavedConnection: (NaviampSavedConnectionUi) -> Unit,
    onConnectSavedConnection: (NaviampSavedConnectionUi) -> Unit,
    onDeleteSavedConnection: (NaviampSavedConnectionUi) -> Unit,
    onImportSettingsSyncFile: (() -> Unit)?,
    onChooseSettingsSyncFolder: (() -> Unit)?,
    onImportSettingsSyncFolder: (() -> Unit)?,
    onExportSettingsSyncFolder: (() -> Unit)?,
    settingsSyncAutoExportEnabled: Boolean,
    onSettingsSyncAutoExportChanged: ((Boolean) -> Unit)?,
    onConnectionFormChanged: (ConnectionFormState) -> Unit,
    onConnect: () -> Unit,
    onCancelConnectionForm: () -> Unit,
    onInterfaceSettingsChanged: (InterfaceSettings) -> Unit,
    onPlaybackSettingsChanged: (PlaybackSettings) -> Unit,
    onPlaybackSettingsChangedAndRedownload: (PlaybackSettings) -> Unit,
    onCacheSettingsChanged: (CacheSettings) -> Unit,
    onDownloadLocationChanged: (NaviampStorageLocationUi) -> Unit,
    onAudioCacheLocationChanged: (NaviampStorageLocationUi) -> Unit,
    onClearCache: () -> Unit,
    onClearLibrary: () -> Unit,
    onResetDatabase: () -> Unit,
) {
    NaviampSharedSettingsContent(
        colors = colors,
        interfaceSettings = interfaceSettings,
        playbackSettings = playbackSettings,
        cacheSettings = cacheSettings,
        diagnostics = diagnostics,
        about = about,
        savedConnections = savedConnections,
        isConnectionFormOpen = isConnectionFormOpen,
        isConnecting = isConnecting,
        connectionStatus = connectionStatus,
        settingsSyncStatus = settingsSyncStatus,
        availableMusicFolders = availableMusicFolders,
        musicFoldersStatus = musicFoldersStatus,
        connectionForm = connectionForm,
        hasSavedConnection = hasSavedConnection,
        supportsReplayGain = supportsReplayGain,
        supportsGapless = supportsGapless,
        supportsCrossfade = supportsCrossfade,
        supportsEqualizer = supportsEqualizer,
        supportsSonicSimilarity = supportsSonicSimilarity,
        showMobileNetworkQuality = showMobileNetworkQuality,
        downloadBytes = downloadBytes,
        downloadLocations = downloadLocations,
        audioCacheLocations = audioCacheLocations,
        selectedDownloadLocationId = selectedDownloadLocationId,
        selectedAudioCacheLocationId = selectedAudioCacheLocationId,
        onEditConnection = onEditConnection,
        onNewConnection = onNewConnection,
        onEditSavedConnection = onEditSavedConnection,
        onConnectSavedConnection = onConnectSavedConnection,
        onDeleteSavedConnection = onDeleteSavedConnection,
        onImportSettingsSyncFile = onImportSettingsSyncFile,
        onChooseSettingsSyncFolder = onChooseSettingsSyncFolder,
        onImportSettingsSyncFolder = onImportSettingsSyncFolder,
        onExportSettingsSyncFolder = onExportSettingsSyncFolder,
        settingsSyncAutoExportEnabled = settingsSyncAutoExportEnabled,
        onSettingsSyncAutoExportChanged = onSettingsSyncAutoExportChanged,
        onConnectionFormChanged = onConnectionFormChanged,
        onConnect = onConnect,
        onCancelConnectionForm = onCancelConnectionForm,
        onInterfaceSettingsChanged = onInterfaceSettingsChanged,
        onPlaybackSettingsChanged = onPlaybackSettingsChanged,
        onPlaybackSettingsChangedAndRedownload = onPlaybackSettingsChangedAndRedownload,
        onCacheSettingsChanged = onCacheSettingsChanged,
        onDownloadLocationChanged = onDownloadLocationChanged,
        onAudioCacheLocationChanged = onAudioCacheLocationChanged,
        onClearCache = onClearCache,
        onClearLibrary = onClearLibrary,
        onResetDatabase = onResetDatabase,
    )
}

@Composable
private fun PlaceholderRoute(colors: NaviampColors, route: SharedRoute) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(route.label, color = colors.primaryText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        PlaceholderTile("This screen is wired into the shared shell and ready for the desktop panel extraction.", colors)
    }
}

@Composable
fun NaviampMiniNowPlaying(
    nowPlaying: NowPlayingUi,
    colors: NaviampColors,
    onOpen: () -> Unit,
    actions: NaviampNowPlayingActions,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.22f))
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onOpen),
        ) {
            PlatformCoverArt(nowPlaying.coverArtUrl, colors, 40.dp, 5.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    nowPlaying.subtitle.ifBlank { "Nothing Playing" },
                    color = colors.secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 11.sp,
                )
                Text(
                    nowPlaying.title.ifBlank { "Queue is empty" },
                    color = colors.primaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                )
            }
        }
        MiniPlayerIconButton(
            colors = colors,
            enabled = nowPlaying.hasPrevious,
            icon = NaviampTransportIcons.Previous,
            contentDescription = "Previous",
            onClick = { actions.playback(NowPlayingPlaybackAction.Previous) },
        )
        MiniPlayerIconButton(
            colors = colors,
            enabled = nowPlaying.canPlayPause,
            icon = if (nowPlaying.isPlaying) NaviampTransportIcons.Pause else NaviampTransportIcons.Play,
            contentDescription = if (nowPlaying.isPlaying) "Pause" else "Play",
            onClick = {
                if (nowPlaying.isPlaying) {
                    actions.playback(NowPlayingPlaybackAction.Pause)
                } else if (nowPlaying.isPaused) {
                    actions.playback(NowPlayingPlaybackAction.Resume)
                } else {
                    actions.playback(NowPlayingPlaybackAction.PlayCurrent)
                }
            },
        )
        MiniPlayerIconButton(
            colors = colors,
            enabled = nowPlaying.hasNext,
            icon = NaviampTransportIcons.Next,
            contentDescription = "Next",
            onClick = { actions.playback(NowPlayingPlaybackAction.Next) },
        )
    }
}

private fun String.normalizedBiography(): String =
    trim()
        .replace(Regex("[\\t ]+"), " ")
        .split(Regex("\\R\\s*\\R+"))
        .joinToString("\n\n") { paragraph ->
            paragraph
                .replace(Regex("\\s*\\R\\s*"), " ")
                .trim()
        }

private val ArtistActionsExpandedMinWidth = 232.dp
