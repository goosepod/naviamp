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
import app.naviamp.domain.Track
import app.naviamp.domain.cache.DownloadJob
import app.naviamp.domain.cache.KeepDownloadedCollectionPolicy
import app.naviamp.domain.cache.StorageCacheStats
import app.naviamp.domain.home.HomeContent
import app.naviamp.domain.playback.EqualizerPlaybackEngine
import app.naviamp.domain.settings.ConnectionFormState
import app.naviamp.domain.settings.InterfaceSettings
import app.naviamp.ui.AlbumMixBuilderContent
import app.naviamp.ui.ArtistMixBuilderContent
import app.naviamp.ui.GenreMixBuilderContent
import app.naviamp.ui.NaviampAppShellActions
import app.naviamp.ui.NaviampAppShellUiState
import app.naviamp.ui.NaviampConnectionSettingsActions
import app.naviamp.ui.NaviampSettingsMaintenanceActions
import app.naviamp.ui.NaviampSettingsSyncActions
import app.naviamp.ui.NaviampSettingsValueActions
import app.naviamp.ui.NaviampAlbumDetailScreenUi
import app.naviamp.ui.NaviampArtistDetailScreenUi
import app.naviamp.ui.NaviampSavedConnectionUi
import app.naviamp.ui.NaviampLibraryActions
import app.naviamp.ui.NaviampLibraryScreenUi
import app.naviamp.ui.NaviampHomeActions
import app.naviamp.ui.NaviampInternetRadioScreenUi
import app.naviamp.ui.NaviampPlaylistDetailScreenUi
import app.naviamp.ui.NaviampPlaylistsScreenUi
import app.naviamp.ui.NaviampSearchScreenUi
import app.naviamp.ui.NaviampSearchActions
import app.naviamp.ui.NaviampShellCapabilitiesUi
import app.naviamp.ui.NaviampShellConnectionUi
import app.naviamp.ui.NaviampShellNavigationActions
import app.naviamp.ui.SharedGenreMixItemUi
import app.naviamp.ui.SharedHomeRoute
import app.naviamp.ui.SharedHomeDiscoveryTrackActionRequest
import app.naviamp.ui.SharedMediaItemUi
import app.naviamp.ui.SharedMixBuilderUi
import app.naviamp.ui.SharedTrackGroupAction
import app.naviamp.ui.SharedTrackGroupActionRequest
import app.naviamp.ui.SharedTrackRowAction
import app.naviamp.ui.SharedTrackRowUi
import app.naviamp.ui.SaveQueueAsPlaylistDialog
import app.naviamp.ui.SonicMixBuilderContent
import app.naviamp.ui.SonicPathBuilderContent
import app.naviamp.ui.settingsSyncUi

@Composable
fun ColumnScope.DesktopAppRouteContent(
    shellState: NaviampAppShellUiState,
    shellActions: NaviampAppShellActions,
    appColors: DesktopAppColors,
    appRoute: DesktopAppRoute,
    connection: NaviampShellConnectionUi,
    capabilities: NaviampShellCapabilitiesUi,
    homeContent: HomeContent,
    coverArtUrl: (String?) -> String?,
    appActions: DesktopAppActions,
    playlistsController: DesktopPlaylistsController,
    onLibraryJumpToLetter: (Char) -> Unit,
    libraryListState: LazyListState,
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
    onSettingsSyncDirectoryChanged: (String?) -> Unit,
    onSettingsSyncDirectorySelectedForImport: (String) -> Unit,
    onSettingsSyncAutoExportChanged: (Boolean) -> Unit,
    onSettingsSyncExport: () -> Unit,
    onSettingsSyncImport: () -> Unit,
) {
    var saveSonicPathDialogOpen by remember { mutableStateOf(false) }
    var saveSonicMixDialogOpen by remember { mutableStateOf(false) }
    val contentScrollState = rememberScrollState()
    val sharedSettingsSync = settingsSyncUi(
        directoryPath = settingsSyncDirectoryPath,
        autoExportEnabled = settingsSyncAutoExportEnabled,
        status = settingsSyncStatus,
        capabilities = capabilities,
    )
    val sharedSettingsSyncActions = NaviampSettingsSyncActions(
        onDirectoryChanged = onSettingsSyncDirectoryChanged,
        onDirectorySelectedForImport = onSettingsSyncDirectorySelectedForImport,
        onAutoExportChanged = onSettingsSyncAutoExportChanged,
        onExport = onSettingsSyncExport,
        onImport = onSettingsSyncImport,
    )
    val sharedShellState = shellState
    val sharedShellActions = shellActions

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
                    home = sharedShellState.home.content,
                    isRefreshing = sharedShellState.home.refreshing,
                    onRefresh = sharedShellActions.homeActions.onRefresh,
                    onAlbumSelected = { item -> appActions.openHomeAlbum(item.id) },
                    onAlbumFavoriteToggled = { item -> appActions.toggleHomeAlbumFavorite(item.id) },
                    onMixAlbumSelected = { item -> appActions.playHomeMixAlbum(item.id) },
                    onPlaylistSelected = { item -> appActions.openHomePlaylist(item.id) },
                    onRecentRadioSelected = sharedShellActions.homeActions.onRecentRadioSelected,
                    onInternetRadioStationSelected = { item -> appActions.playHomeInternetRadio(item.id) },
                    onMixBuilderSelected = sharedShellActions.homeActions.onMixBuilderSelected,
                    onHomeStationSelected = sharedShellActions.homeActions.onStationSelected,
                    onSonicDiscoveryTrackAction = sharedShellActions.homeActions.onSonicDiscoveryTrackAction,
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
                    screen = sharedShellState.albumDetail,
                    actions = sharedShellActions.albumDetailActions,
                )
                DesktopAppRoute.ArtistDetail -> DesktopArtistDetailPanel(
                    appColors = appColors,
                    screen = sharedShellState.artistDetail,
                    albumCollectionLayout = interfaceSettings.albumCollectionLayout,
                    albumSortOrder = interfaceSettings.albumSortOrder,
                    groupAlbumsByReleaseType = interfaceSettings.groupAlbumsByReleaseType,
                    actions = sharedShellActions.artistDetailActions,
                )
                DesktopAppRoute.Playlists -> DesktopPlaylistsPanel(
                    appColors = appColors,
                    screen = sharedShellState.playlists.copy(
                        status = sharedShellState.playlists.status ?: connection.status.pageStatusOrNull(),
                    ),
                    actions = sharedShellActions.playlistsActions,
                    onPlaylistAction = sharedShellActions.mediaActions.onMediaItemAction ?: {},
                    availableLibraries = connection.availableMusicFolders,
                    selectedConnectionLibraryIds = connection.form.selectedMusicFolderIds,
                )
                DesktopAppRoute.PlaylistDetail -> DesktopPlaylistDetailPanel(
                    appColors = appColors,
                    screen = sharedShellState.playlistDetail.copy(
                        status = sharedShellState.playlistDetail.status ?: sharedShellState.playlists.status,
                    ),
                    actions = sharedShellActions.playlistDetailActions,
                    playlistsActions = sharedShellActions.playlistsActions,
                    availableLibraries = connection.availableMusicFolders,
                    selectedConnectionLibraryIds = connection.form.selectedMusicFolderIds,
                )
                DesktopAppRoute.Library -> {
                    DesktopLibraryPanel(
                        appColors = appColors,
                        library = sharedShellState.library.copy(
                            syncStatus = sharedShellState.library.syncStatus.copy(
                                message = sharedShellState.library.syncStatus.message
                                    ?: connection.status.pageStatusOrNull(),
                            ),
                        ),
                        listState = libraryListState,
                        actions = sharedShellActions.libraryActions,
                        onJumpToLetter = onLibraryJumpToLetter,
                        onMediaItemAction = sharedShellActions.mediaActions.onMediaItemAction ?: {},
                    )
                }
                DesktopAppRoute.Search -> DesktopSearchPanel(
                    appColors = appColors,
                    search = sharedShellState.search,
                    actions = sharedShellActions.searchActions,
                    onMediaItemAction = sharedShellActions.mediaActions.onMediaItemAction ?: {},
                    onTrackAction = sharedShellActions.mediaActions.onTrackAction,
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
                            builder = sharedShellState.artistMixBuilder,
                            actions = sharedShellActions.artistMixActions,
                            showPlayMixButton = false,
                        )
                    }
                    if (sharedShellState.artistMixBuilder.selectedArtists.isNotEmpty()) {
                        Button(
                            onClick = sharedShellActions.artistMixActions.onPlay,
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
                            builder = sharedShellState.albumMixBuilder,
                            actions = sharedShellActions.albumMixActions,
                            showPlayMixButton = false,
                        )
                    }
                    if (sharedShellState.albumMixBuilder.selectedAlbums.isNotEmpty()) {
                        Button(
                            onClick = sharedShellActions.albumMixActions.onPlay,
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
                            builder = sharedShellState.genreMixBuilder,
                            actions = sharedShellActions.genreMixActions,
                            showPlayMixButton = false,
                        )
                    }
                    if (sharedShellState.genreMixBuilder.selectedGenres.isNotEmpty()) {
                        Button(
                            onClick = sharedShellActions.genreMixActions.onPlay,
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
                            builder = sharedShellState.sonicPathBuilder,
                            actions = sharedShellActions.sonicPathActions,
                            showPathActions = false,
                        )
                    }
                    if (sharedShellState.sonicPathBuilder.hasPath) {
                        androidx.compose.foundation.layout.Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Button(
                                onClick = sharedShellActions.sonicPathActions.onPlay,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = appColors.accent,
                                    contentColor = appColors.onAccent,
                                ),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Play Path")
                            }
                            Button(
                                onClick = sharedShellActions.sonicPathActions.onAddToQueue,
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
                            builder = sharedShellState.sonicMixBuilder,
                            actions = sharedShellActions.sonicMixActions,
                            showMixActions = false,
                        )
                    }
                    if (sharedShellState.sonicMixBuilder.hasMix) {
                        androidx.compose.foundation.layout.Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Button(
                                onClick = sharedShellActions.sonicMixActions.onPlay,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = appColors.accent,
                                    contentColor = appColors.onAccent,
                                ),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Play Mix")
                            }
                            Button(
                                onClick = sharedShellActions.sonicMixActions.onAddToQueue,
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
                        screen = sharedShellState.radio.copy(
                            status = sharedShellState.radio.status ?: connection.status.pageStatusOrNull(),
                        ),
                        actions = sharedShellActions.radioActions,
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
                    connectionSettings = sharedShellState.connectionSettings,
                    general = sharedShellState.general,
                    playback = sharedShellState.playback,
                    cache = sharedShellState.cache,
                    settingsSync = sharedSettingsSync,
                    connectionActions = sharedShellActions.connectionActions,
                    syncActions = sharedSettingsSyncActions,
                    valueActions = sharedShellActions.valueActions,
                    maintenanceActions = sharedShellActions.maintenanceActions,
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
                sharedShellActions.sonicPathActions.onSaveAsPlaylist(name)
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
                sharedShellActions.sonicMixActions.onSaveAsPlaylist(name)
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
