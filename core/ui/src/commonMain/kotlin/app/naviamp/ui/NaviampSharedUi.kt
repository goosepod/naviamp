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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import app.naviamp.domain.settings.ConnectionFormHeader
import app.naviamp.domain.settings.ConnectionFormMusicFolder
import app.naviamp.domain.settings.ConnectionFormSecondaryUrl
import app.naviamp.domain.settings.InterfaceSettings
import app.naviamp.domain.settings.AlbumCollectionLayout
import app.naviamp.domain.settings.AlbumSortOrder
import app.naviamp.domain.settings.AppBackgroundStyle
import app.naviamp.domain.settings.DefaultSingleColorHex
import app.naviamp.domain.settings.toggleSelectedMusicFolderId
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.ui.generated.resources.Res
import app.naviamp.ui.generated.resources.*
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.resources.stringResource

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
    uiState: NaviampAppShellUiState,
    settingsSync: NaviampSettingsSyncUi = NaviampSettingsSyncUi(),
    playbackProgress: StateFlow<PlaybackProgress>? = null,
    visualizerBandsProvider: () -> List<Float> = { uiState.nowPlaying?.visualizerFrame?.bands.orEmpty() },
    actions: NaviampAppShellActions,
    syncActions: NaviampSettingsSyncActions,
    applicationUpdateChecker: NaviampApplicationUpdateChecker? = null,
) {
    val navigationActions = actions.navigationActions
    val connectionActions = actions.connectionActions
    val valueActions = actions.valueActions
    val maintenanceActions = actions.maintenanceActions
    val searchActions = actions.searchActions
    val artistMixActions = actions.artistMixActions
    val albumMixActions = actions.albumMixActions
    val genreMixActions = actions.genreMixActions
    val sonicPathActions = actions.sonicPathActions
    val sonicMixActions = actions.sonicMixActions
    val downloadsActions = actions.downloadsActions
    val libraryActions = actions.libraryActions
    val playlistsActions = actions.playlistsActions
    val radioActions = actions.radioActions
    val albumDetailActions = actions.albumDetailActions
    val artistDetailActions = actions.artistDetailActions
    val playlistDetailActions = actions.playlistDetailActions
    val homeActions = actions.homeActions
    val mediaActions = actions.mediaActions
    val nowPlayingActions = actions.nowPlayingActions
    val connectionSettings = uiState.connectionSettings
    val general = uiState.general
    val playback = uiState.playback
    val cache = uiState.cache
    val shellChrome = uiState.shellChrome
    val search = uiState.search
    val home = uiState.home
    val artistMixBuilder = uiState.artistMixBuilder
    val albumMixBuilder = uiState.albumMixBuilder
    val genreMixBuilder = uiState.genreMixBuilder
    val sonicPathBuilder = uiState.sonicPathBuilder
    val sonicMixBuilder = uiState.sonicMixBuilder
    val library = uiState.library
    val downloads = uiState.downloads
    val playlists = uiState.playlists
    val playlistChoices = uiState.playlistChoices
    val radio = uiState.radio
    val albumDetail = uiState.albumDetail
    val artistDetail = uiState.artistDetail
    val playlistDetail = uiState.playlistDetail
    val nowPlaying = uiState.nowPlaying?.withDisplaySettings(general.interfaceSettings.nowPlaying)
    val supportsDownloads = shellChrome.supportsDownloads
    val supportsApplicationUpdates = shellChrome.supportsApplicationUpdates
    val selectedRoute = shellChrome.selectedRoute
    val nowPlayingOpen = shellChrome.nowPlayingOpen
    val connection = connectionSettings.connection
    val status = connection.status.orEmpty()
    val serverVersion = connection.serverVersion
    val connected = connection.connected
    val editingConnection = connection.editingConnection
    val restoringConnection = connection.restoringConnection
    val connectionForm = connection.form
    val interfaceSettings = general.interfaceSettings
    val playbackSettings = playback.settings
    val cacheSettings = cache.settings
    val diagnostics = cache.diagnostics
    val about = general.about
    val savedConnections = connection.savedConnections
    val isConnectionFormOpen = connection.editingConnection
    val isConnecting = connection.isConnecting
    val connectionStatus = connection.status
    val settingsSyncStatus = settingsSync.status
    val settingsSyncAutoExportEnabled = settingsSync.autoExportEnabled
    val availableMusicFolders = connection.availableMusicFolders
    val musicFoldersStatus = connection.musicFoldersStatus
    val hasSavedConnection = connection.hasSavedConnection
    val supportsReplayGain = playback.replayGainAvailable
    val supportsGapless = playback.gaplessAvailable
    val supportsCrossfade = playback.crossfadeAvailable
    val supportsEqualizer = playback.equalizerAvailable
    val supportsSonicSimilarity = playback.sonicSimilarityAvailable
    val connectionCapabilities = connectionSettings.capabilities
    val showMobileNetworkQuality = playback.showMobileNetworkQuality
    val downloadLocations = cache.downloadLocations
    val audioCacheLocations = cache.audioCacheLocations
    val selectedDownloadLocationId = cache.selectedDownloadLocationId
    val selectedAudioCacheLocationId = cache.selectedAudioCacheLocationId
    val colors = NaviampColors.Dark
    CompositionLocalProvider(
        LocalTrackSwipeSettings provides interfaceSettings.trackSwipes,
        LocalNaviampTooltipsEnabled provides interfaceSettings.showDesktopTooltips,
    ) {
    NaviampApplicationUpdateEffect(
        enabled = supportsApplicationUpdates && interfaceSettings.checkForUpdates,
        currentVersion = about.version,
        channel = interfaceSettings.applicationUpdateChannel ?: defaultApplicationUpdateChannel(about.version),
        checker = applicationUpdateChecker,
    )
    val showFullNowPlaying = connected && !editingConnection && !restoringConnection && nowPlayingOpen && nowPlaying != null
    val outerContentScrollState = rememberScrollState()
    LaunchedEffect(editingConnection) {
        if (editingConnection) {
            outerContentScrollState.scrollTo(0)
        }
    }
    LaunchedEffect(connection.statusIsError, connection.status) {
        if (connection.statusIsError && !connection.status.isNullOrBlank()) {
            outerContentScrollState.animateScrollTo(0)
        }
    }
    val routeUsesOwnScroll = connected &&
        !editingConnection &&
        !restoringConnection &&
        !showFullNowPlaying &&
        (
            albumDetail.selectedAlbum != null ||
                artistDetail.selectedArtist != null ||
                playlistDetail.selectedPlaylist != null ||
                selectedRoute == SharedRoute.Home ||
                selectedRoute == SharedRoute.Search ||
                selectedRoute == SharedRoute.Playlists ||
                selectedRoute == SharedRoute.Library ||
                selectedRoute == SharedRoute.ArtistMix ||
                selectedRoute == SharedRoute.AlbumMix ||
                selectedRoute == SharedRoute.GenreMix ||
                selectedRoute == SharedRoute.SonicPath ||
                selectedRoute == SharedRoute.SonicMix ||
                selectedRoute == SharedRoute.Radio ||
                selectedRoute == SharedRoute.Downloads ||
                selectedRoute == SharedRoute.Settings
            )
    val albumPlayerColors = rememberNaviampCoverArtPlayerColors(nowPlaying?.coverArtUrl, colors)
    val singleBackgroundColor = naviampColorFromHex(interfaceSettings.singleColorHex)
        ?: naviampColorFromHex(DefaultSingleColorHex)!!
    val targetNowPlayingPlayerColors = when (interfaceSettings.appBackgroundStyle) {
        AppBackgroundStyle.SingleColor -> NaviampPlayerColors.fromSingleColor(singleBackgroundColor, colors)
        AppBackgroundStyle.Aurora -> albumPlayerColors.withAuroraTone(interfaceSettings.auroraTone)
        AppBackgroundStyle.AlbumBlur -> albumPlayerColors
    }
    val nowPlayingPlayerColors = animatedNaviampPlayerColors(targetNowPlayingPlayerColors)
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
            modifier = Modifier.fillMaxSize(),
        ) {
            when (interfaceSettings.appBackgroundStyle) {
                AppBackgroundStyle.Aurora -> Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.linearGradient(nowPlayingPlayerColors.gradientColors)),
                )
                AppBackgroundStyle.AlbumBlur -> NaviampAlbumBlurBackground(
                    url = nowPlaying?.coverArtUrl,
                    colors = colors,
                    playerColors = nowPlayingPlayerColors,
                    blurRadiusDp = interfaceSettings.albumBlurRadiusDp,
                )
                AppBackgroundStyle.SingleColor -> Box(
                    Modifier
                        .fillMaxSize()
                        .background(singleBackgroundColor),
                )
            }
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
                                Modifier.verticalScroll(outerContentScrollState)
                            },
                        )
                        .padding(
                            horizontal = if (showFullNowPlaying) 0.dp else 12.dp,
                            vertical = if (showFullNowPlaying) 0.dp else 12.dp,
                        ),
                    verticalArrangement = if (showFullNowPlaying) Arrangement.spacedBy(0.dp) else Arrangement.spacedBy(8.dp),
                ) {
                    val showingRouteConnectionForm = editingConnection && selectedRoute != SharedRoute.Settings
                    if (!showFullNowPlaying && (restoringConnection || !connected || showingRouteConnectionForm)) {
                        Text("Naviamp", color = colors.primaryText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        if (!connection.statusIsError) {
                            Text(status, color = colors.secondaryText, fontSize = 13.sp)
                        }
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
                            isReconnect = connection.editingSavedConnection,
                            isConnecting = isConnecting,
                            connectionStatus = connectionStatus,
                            connectionStatusIsError = connection.statusIsError,
                            availableMusicFolders = availableMusicFolders,
                            musicFoldersStatus = musicFoldersStatus,
                            capabilities = connectionCapabilities,
                            settingsSyncStatus = settingsSyncStatus,
                            onFormChanged = connectionActions.onFormChanged,
                            onConnect = connectionActions.onConnect,
                            onImportSettingsSyncFile = syncActions.onImportFile,
                            onCancel = connectionActions.onCancelConnectionForm.takeIf { connected },
                        )
                    } else {
                        ConnectedContent(
                            colors = colors,
                            uiState = uiState,
                            playbackProgress = playbackProgress,
                            visualizerBandsProvider = visualizerBandsProvider,
                            settingsSync = settingsSync,
                            actions = actions,
                            syncActions = syncActions,
                        )
                    }
                }
                if (!showFullNowPlaying) {
                    if (connected && !editingConnection && !restoringConnection && nowPlaying != null) {
                        NaviampMiniNowPlaying(
                            nowPlaying = nowPlaying,
                            colors = colors,
                            onOpen = navigationActions.onOpenNowPlaying,
                            actions = nowPlayingActions,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                    SharedBottomNavigationBar(
                        colors = colors,
                        selectedRoute = selectedRoute,
                        supportsDownloads = supportsDownloads,
                        onRouteSelected = {
                            navigationActions.onCloseNowPlaying()
                            navigationActions.onRouteSelected(it)
                        },
                    )
                }
            }
        }
    }

    }
}
@Composable
private fun ConnectedContent(
    colors: NaviampColors,
    uiState: NaviampAppShellUiState,
    playbackProgress: StateFlow<PlaybackProgress>?,
    visualizerBandsProvider: () -> List<Float>,
    settingsSync: NaviampSettingsSyncUi,
    actions: NaviampAppShellActions,
    syncActions: NaviampSettingsSyncActions,
) {
    val connectionActions = actions.connectionActions
    val valueActions = actions.valueActions
    val maintenanceActions = actions.maintenanceActions
    val searchActions = actions.searchActions
    val artistMixActions = actions.artistMixActions
    val albumMixActions = actions.albumMixActions
    val genreMixActions = actions.genreMixActions
    val sonicPathActions = actions.sonicPathActions
    val sonicMixActions = actions.sonicMixActions
    val downloadsActions = actions.downloadsActions
    val libraryActions = actions.libraryActions
    val playlistsActions = actions.playlistsActions
    val radioActions = actions.radioActions
    val albumDetailActions = actions.albumDetailActions
    val artistDetailActions = actions.artistDetailActions
    val playlistDetailActions = actions.playlistDetailActions
    val homeActions = actions.homeActions
    val mediaActions = actions.mediaActions
    val nowPlayingActions = actions.nowPlayingActions
    val connectionSettings = uiState.connectionSettings
    val general = uiState.general
    val playback = uiState.playback
    val cache = uiState.cache
    val shellChrome = uiState.shellChrome
    val search = uiState.search
    val home = uiState.home
    val artistMixBuilder = uiState.artistMixBuilder
    val albumMixBuilder = uiState.albumMixBuilder
    val genreMixBuilder = uiState.genreMixBuilder
    val sonicPathBuilder = uiState.sonicPathBuilder
    val sonicMixBuilder = uiState.sonicMixBuilder
    val library = uiState.library
    val downloads = uiState.downloads
    val playlists = uiState.playlists
    val playlistChoices = uiState.playlistChoices
    val radio = uiState.radio
    val albumDetail = uiState.albumDetail
    val artistDetail = uiState.artistDetail
    val playlistDetail = uiState.playlistDetail
    val nowPlaying = uiState.nowPlaying?.withDisplaySettings(general.interfaceSettings.nowPlaying)
    val selectedRoute = shellChrome.selectedRoute
    val nowPlayingOpen = shellChrome.nowPlayingOpen
    val selectedVisualizer = shellChrome.selectedVisualizer
    val onTrackAction = mediaActions.onTrackAction
    val onMediaItemAction = mediaActions.onMediaItemAction
    val onTrackSelected: (SharedTrackRowUi) -> Unit = {
        onTrackAction(SharedTrackRowActionRequest(it, SharedTrackRowAction.Select))
    }
    val onAlbumSelected: (SharedMediaItemUi) -> Unit = {
        onMediaItemAction(NaviampMediaItemActionRequest(it, NaviampMediaItemCommand.Album(NaviampArtistAlbumCommand.Select)))
    }
    val onAlbumFavoriteToggled: (SharedMediaItemUi) -> Unit = {
        onMediaItemAction(NaviampMediaItemActionRequest(it, NaviampMediaItemCommand.Album(NaviampArtistAlbumCommand.ToggleFavorite)))
    }
    val onMixAlbumSelected: (SharedMediaItemUi) -> Unit = {
        onMediaItemAction(NaviampMediaItemActionRequest(it, NaviampMediaItemCommand.PlayAlbum))
    }
    val onArtistSelected: (SharedMediaItemUi) -> Unit = {
        onMediaItemAction(NaviampMediaItemActionRequest(it, NaviampMediaItemCommand.Artist(NaviampArtistMediaCommand.Select)))
    }
    val onArtistFavoriteToggled: (SharedMediaItemUi) -> Unit = {
        onMediaItemAction(NaviampMediaItemActionRequest(it, NaviampMediaItemCommand.Artist(NaviampArtistMediaCommand.ToggleFavorite)))
    }
    val onPlaylistSelected: (SharedMediaItemUi) -> Unit = {
        onMediaItemAction(NaviampMediaItemActionRequest(it, NaviampMediaItemCommand.Playlist(NaviampPlaylistMediaCommand.Select)))
    }
    val connection = connectionSettings.connection
    val availableMusicFolders = connection.availableMusicFolders
    val connectionForm = connection.form
    val interfaceSettings = general.interfaceSettings
    val playbackSettings = playback.settings
    val cacheSettings = cache.settings
    var saveSonicPathDialogOpen by remember { mutableStateOf(false) }
    var saveSonicMixDialogOpen by remember { mutableStateOf(false) }
    val routeStateHolder = rememberSaveableStateHolder()
    val albumPlayerColors = rememberNaviampCoverArtPlayerColors(nowPlaying?.coverArtUrl, colors)
    val singleBackgroundColor = naviampColorFromHex(interfaceSettings.singleColorHex)
        ?: naviampColorFromHex(DefaultSingleColorHex)!!
    val targetNowPlayingPlayerColors = when (interfaceSettings.appBackgroundStyle) {
        AppBackgroundStyle.SingleColor -> NaviampPlayerColors.fromSingleColor(singleBackgroundColor, colors)
        AppBackgroundStyle.Aurora -> albumPlayerColors.withAuroraTone(interfaceSettings.auroraTone)
        AppBackgroundStyle.AlbumBlur -> albumPlayerColors
    }
    val nowPlayingPlayerColors = animatedNaviampPlayerColors(targetNowPlayingPlayerColors)
    val homeScrollState = rememberScrollState()
    val libraryListState = rememberLazyListState()
    val artistDetailScrollState = rememberScrollState()
    val playlistDetailScrollState = rememberScrollState()
    LaunchedEffect(artistDetail.selectedArtist?.id) {
        artistDetailScrollState.scrollTo(0)
    }
    LaunchedEffect(playlistDetail.selectedPlaylist?.id) {
        playlistDetailScrollState.scrollTo(0)
    }

    when {
        nowPlayingOpen && nowPlaying != null -> FullNowPlaying(
            nowPlaying = nowPlaying,
            playbackProgress = playbackProgress,
            colors = colors,
            playerColors = nowPlayingPlayerColors,
            visualizerBandsProvider = visualizerBandsProvider,
            selectedVisualizer = selectedVisualizer,
            actions = nowPlayingActions,
            displaySettings = interfaceSettings.nowPlaying,
        )
        albumDetail.selectedAlbum != null -> NaviampAlbumDetailContent(
            colors = colors,
            screen = albumDetail,
            actions = albumDetailActions,
            playlistChoices = playlistChoices,
            playlistActionStatus = playlists.status,
        )
        artistDetail.selectedArtist != null -> NaviampArtistDetailContent(
            colors = colors,
            screen = artistDetail,
            albumCollectionLayout = interfaceSettings.albumCollectionLayout,
            albumSortOrder = interfaceSettings.albumSortOrder,
            groupAlbumsByReleaseType = interfaceSettings.groupAlbumsByReleaseType,
            actions = artistDetailActions,
            playlistChoices = playlistChoices,
            playlistActionStatus = playlists.status,
            scrollState = artistDetailScrollState,
        )
        playlistDetail.selectedPlaylist != null -> NaviampPlaylistDetailContent(
            colors = colors,
            screen = playlistDetail,
            actions = playlistDetailActions,
            playlistsActions = playlistsActions,
            playlistChoices = playlistChoices,
            scrollState = playlistDetailScrollState,
        )
        selectedRoute == SharedRoute.Settings -> routeStateHolder.SaveableStateProvider(SharedRoute.Settings.name) {
            NaviampSettingsContent(
                colors = colors,
                desktopShortcutPlatform = uiState.capabilities.desktopShortcutPlatform,
                connectionSettings = connectionSettings,
                general = general,
                playback = playback,
                cache = cache,
                settingsSync = settingsSync,
                connectionActions = connectionActions,
                syncActions = syncActions,
                valueActions = valueActions,
                maintenanceActions = maintenanceActions,
            )
        }
        else -> when (selectedRoute) {
            SharedRoute.Home -> SharedHomeRoute(
                colors = colors,
                home = home,
                actions = homeActions,
                mediaActions = mediaActions,
                scrollState = homeScrollState,
            )
            SharedRoute.Playlists -> PullToRefreshRoute(
                isRefreshing = playlists.refreshing,
                onRefresh = playlistsActions.onRefresh,
            ) {
                NaviampPlaylistsContent(
                    colors = colors,
                    screen = playlists,
                    actions = playlistsActions,
                    mediaActions = mediaActions,
                    playlistChoices = playlistChoices,
                )
            }
            SharedRoute.Library -> PullToRefreshRoute(
                isRefreshing = library.syncStatus.isSyncing,
                onRefresh = libraryActions.onRefresh,
            ) {
                NaviampLibraryContent(
                    colors = colors,
                    screen = library,
                    actions = libraryActions,
                    mediaActions = mediaActions,
                    listState = libraryListState,
                )
            }
            SharedRoute.Search -> NaviampSearchContent(
                colors = colors,
                screen = search,
                actions = searchActions,
                mediaActions = mediaActions,
            )
            SharedRoute.ArtistMix -> Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                NaviampPageTitle(stringResource(Res.string.mix_artist_builder_title), colors)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    ArtistMixBuilderContent(
                        colors = colors,
                        builder = artistMixBuilder,
                        actions = artistMixActions,
                        showPlayMixButton = false,
                        showTitle = false,
                    )
                }
                if (artistMixBuilder.selectedArtists.isNotEmpty()) {
                    PrimaryButton("Play Mix", colors, onClick = artistMixActions.onPlay)
                }
            }
            SharedRoute.AlbumMix -> Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                NaviampPageTitle(stringResource(Res.string.mix_album_builder_title), colors)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    AlbumMixBuilderContent(
                        colors = colors,
                        builder = albumMixBuilder,
                        actions = albumMixActions,
                        showPlayMixButton = false,
                        showTitle = false,
                    )
                }
                if (albumMixBuilder.selectedAlbums.isNotEmpty()) {
                    PrimaryButton("Play Mix", colors, onClick = albumMixActions.onPlay)
                }
            }
            SharedRoute.GenreMix -> Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                NaviampPageTitle(stringResource(Res.string.mix_genre_builder_title), colors)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    GenreMixBuilderContent(
                        colors = colors,
                        builder = genreMixBuilder,
                        actions = genreMixActions,
                        showPlayMixButton = false,
                        showTitle = false,
                    )
                }
                if (genreMixBuilder.selectedGenres.isNotEmpty()) {
                    PrimaryButton("Play Mix", colors, onClick = genreMixActions.onPlay)
                }
            }
            SharedRoute.SonicPath -> Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                NaviampPageTitle(stringResource(Res.string.sonic_path_title), colors)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    SonicPathBuilderContent(
                        colors = colors,
                        builder = sonicPathBuilder,
                        actions = sonicPathActions,
                        showPathActions = false,
                        showTitle = false,
                    )
                }
                if (sonicPathBuilder.hasPath) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = sonicPathActions.onPlay, modifier = Modifier.weight(1f)) {
                            Text("Play Path")
                        }
                        Button(onClick = sonicPathActions.onAddToQueue, modifier = Modifier.weight(1f)) {
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
                NaviampPageTitle(stringResource(Res.string.nav_sonic_mix), colors)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    SonicMixBuilderContent(
                        colors = colors,
                        builder = sonicMixBuilder,
                        actions = sonicMixActions,
                        showMixActions = false,
                        showTitle = false,
                    )
                }
                if (sonicMixBuilder.hasMix) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = sonicMixActions.onPlay, modifier = Modifier.weight(1f)) {
                            Text("Play Mix")
                        }
                        Button(onClick = sonicMixActions.onAddToQueue, modifier = Modifier.weight(1f)) {
                            Text("Add to Queue")
                        }
                        Button(onClick = { saveSonicMixDialogOpen = true }, modifier = Modifier.weight(1f)) {
                            Text("Save")
                        }
                    }
                }
            }
            SharedRoute.Radio -> PullToRefreshRoute(
                isRefreshing = radio.refreshing,
                onRefresh = radioActions.onRefresh,
            ) {
                NaviampInternetRadioContent(
                    colors = colors,
                    screen = radio,
                    actions = radioActions,
                )
            }
            SharedRoute.Settings -> Unit
            SharedRoute.Downloads -> NaviampDownloadsContent(
                colors = colors,
                screen = downloads,
                actions = downloadsActions,
                playlistChoices = playlistChoices,
                playlistActionStatus = playlists.status,
            )
        }
    }
    if (saveSonicPathDialogOpen) {
        SaveQueueAsPlaylistDialog(
            colors = colors,
            status = playlists.status,
            title = "Save path as playlist",
            description = "Save this Sonic Path in order as a server playlist.",
            onDismissRequest = { saveSonicPathDialogOpen = false },
            onSave = { name ->
                sonicPathActions.onSaveAsPlaylist(name)
                saveSonicPathDialogOpen = false
            },
        )
    }
    if (saveSonicMixDialogOpen) {
        SaveQueueAsPlaylistDialog(
            colors = colors,
            status = playlists.status,
            title = "Save mix as playlist",
            description = "Save this Sonic Mix in order as a server playlist.",
            onDismissRequest = { saveSonicMixDialogOpen = false },
            onSave = { name ->
                sonicMixActions.onSaveAsPlaylist(name)
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
    scrollState: androidx.compose.foundation.ScrollState = rememberScrollState(),
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
                    .verticalScroll(scrollState),
            ) {
                content()
            }
        } else {
            content()
        }
    }
}

@Composable
private fun FullNowPlaying(
    nowPlaying: NowPlayingUi,
    playbackProgress: StateFlow<PlaybackProgress>?,
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
            playbackProgress = playbackProgress,
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
fun NaviampSettingsContent(
    colors: NaviampColors,
    modifier: Modifier = Modifier,
    desktopShortcutPlatform: app.naviamp.domain.settings.DesktopShortcutPlatform? = null,
    connectionSettings: NaviampConnectionSettingsUi,
    general: NaviampGeneralSettingsUi,
    playback: NaviampPlaybackSettingsUi,
    cache: NaviampCacheSettingsUi,
    settingsSync: NaviampSettingsSyncUi,
    connectionActions: NaviampConnectionSettingsActions,
    syncActions: NaviampSettingsSyncActions,
    valueActions: NaviampSettingsValueActions,
    maintenanceActions: NaviampSettingsMaintenanceActions,
) {
    val connection = connectionSettings.connection
    NaviampSharedSettingsContent(
        colors = colors,
        modifier = modifier,
        interfaceSettings = general.interfaceSettings,
        playbackSettings = playback.settings,
        cacheSettings = cache.settings,
        diagnostics = cache.diagnostics,
        downloadsDiagnostics = cache.downloadsDiagnostics,
        audioCacheDiagnostics = cache.audioCacheDiagnostics,
        about = general.about,
        savedConnections = connection.savedConnections,
        isConnectionFormOpen = connection.editingConnection,
        editingSavedConnection = connection.editingSavedConnection,
        isConnecting = connection.isConnecting,
        connectionStatus = connection.status,
        connectionStatusIsError = connection.statusIsError,
        settingsSyncStatus = settingsSync.status,
        availableMusicFolders = connection.availableMusicFolders,
        musicFoldersStatus = connection.musicFoldersStatus,
        connectionForm = connection.form,
        hasSavedConnection = connection.hasSavedConnection,
        supportsReplayGain = playback.replayGainAvailable,
        supportsGapless = playback.gaplessAvailable,
        supportsCrossfade = playback.crossfadeAvailable,
        supportsEqualizer = playback.equalizerAvailable,
        supportsAudioOutputDeviceSelection = playback.audioOutputDeviceSelectionAvailable,
        audioOutputDevices = playback.audioOutputDevices,
        supportsSonicSimilarity = playback.sonicSimilarityAvailable,
        connectionCapabilities = connectionSettings.capabilities,
        showMobileNetworkQuality = playback.showMobileNetworkQuality,
        downloadBytes = playback.downloadBytes,
        downloadLocations = cache.downloadLocations,
        audioCacheLocations = cache.audioCacheLocations,
        selectedDownloadLocationId = cache.selectedDownloadLocationId,
        selectedAudioCacheLocationId = cache.selectedAudioCacheLocationId,
        onEditConnection = connectionActions.onEditCurrentConnection,
        onNewConnection = connectionActions.onNewConnection,
        onEditSavedConnection = connectionActions.onEditConnection,
        onConnectSavedConnection = connectionActions.onConnectSavedConnection,
        onDeleteSavedConnection = connectionActions.onDeleteConnection,
        onImportSettingsSyncFile = syncActions.onImportFile.takeIf { settingsSync.available },
        onChooseSettingsSyncFolder = syncActions.onChooseFolder.takeIf { settingsSync.available },
        onSyncSettingsNow = syncActions.onExport.takeIf { settingsSync.available },
        onExportSettingsSyncFolder = syncActions.onExportFolder.takeIf { settingsSync.available },
        settingsSyncAutoExportEnabled = settingsSync.autoExportEnabled,
        onSettingsSyncAutoExportChanged = syncActions.onAutoExportChanged.takeIf { settingsSync.available },
        onConnectionFormChanged = connectionActions.onFormChanged,
        onConnect = connectionActions.onConnect,
        onCancelConnectionForm = connectionActions.onCancelConnectionForm,
        onInterfaceSettingsChanged = valueActions.onInterfaceSettingsChanged,
        onPlaybackSettingsChanged = valueActions.onPlaybackSettingsChanged,
        onPlaybackSettingsChangedAndRedownload = valueActions.onPlaybackSettingsChangedAndRedownload,
        onCacheSettingsChanged = valueActions.onCacheSettingsChanged,
        onDownloadLocationChanged = valueActions.onDownloadLocationChanged,
        onAudioCacheLocationChanged = valueActions.onAudioCacheLocationChanged,
        onClearCache = maintenanceActions.onClearCache,
        onClearLibrary = maintenanceActions.onClearLibrary,
        onRefreshLibrary = maintenanceActions.onRefreshLibrary,
        onResetDatabase = maintenanceActions.onResetDatabase,
        onOpenStatsForNerds = maintenanceActions.onOpenStatsForNerds,
        showSoftwareVolumePreference = playback.softwareVolumeControlAvailable,
        showTooltipPreference = playback.hoverTooltipsAvailable,
        desktopShortcutPlatform = desktopShortcutPlatform,
        globalShortcutStatuses = general.globalShortcutStatuses,
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
            NaviampCoverArt(nowPlaying.coverArtUrl, colors, 40.dp, 5.dp)
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
