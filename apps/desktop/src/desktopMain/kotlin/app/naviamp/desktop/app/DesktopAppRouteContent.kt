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
import app.naviamp.domain.Track
import app.naviamp.domain.playback.EqualizerPlaybackEngine
import app.naviamp.domain.settings.ConnectionFormState
import app.naviamp.ui.AlbumMixBuilderContent
import app.naviamp.ui.ArtistMixBuilderContent
import app.naviamp.ui.GenreMixBuilderContent
import app.naviamp.ui.NaviampAppShellActions
import app.naviamp.ui.NaviampAppShellUiState
import app.naviamp.ui.NaviampConnectionSettingsActions
import app.naviamp.ui.NaviampSettingsMaintenanceActions
import app.naviamp.ui.NaviampSettingsSyncActions
import app.naviamp.ui.NaviampSettingsSyncUi
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

@Composable
fun ColumnScope.DesktopAppRouteContent(
    shellState: NaviampAppShellUiState,
    shellActions: NaviampAppShellActions,
    appColors: DesktopAppColors,
    appRoute: DesktopAppRoute,
    connection: NaviampShellConnectionUi,
    libraryListState: LazyListState,
    settingsSync: NaviampSettingsSyncUi,
    settingsSyncActions: NaviampSettingsSyncActions,
) {
    var saveSonicPathDialogOpen by remember { mutableStateOf(false) }
    var saveSonicMixDialogOpen by remember { mutableStateOf(false) }
    val contentScrollState = rememberScrollState()
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
                    onAlbumSelected = sharedShellActions.mediaActions.onAlbumSelected,
                    onAlbumFavoriteToggled = sharedShellActions.mediaActions.onAlbumFavoriteToggled,
                    onMixAlbumSelected = sharedShellActions.mediaActions.onMixAlbumSelected,
                    onPlaylistSelected = sharedShellActions.mediaActions.onPlaylistSelected,
                    onRecentRadioSelected = sharedShellActions.homeActions.onRecentRadioSelected,
                    onInternetRadioStationSelected =
                        sharedShellActions.homeActions.onInternetRadioStationSelected,
                    onMixBuilderSelected = sharedShellActions.homeActions.onMixBuilderSelected,
                    onHomeStationSelected = sharedShellActions.homeActions.onStationSelected,
                    onSonicDiscoveryTrackAction = sharedShellActions.homeActions.onSonicDiscoveryTrackAction,
                    onRecentlyPlayedTrackAction = sharedShellActions.homeActions.onRecentlyPlayedTrackAction,
                )
                DesktopAppRoute.AlbumDetail -> DesktopAlbumDetailPanel(
                    appColors = appColors,
                    screen = sharedShellState.albumDetail,
                    actions = sharedShellActions.albumDetailActions,
                )
                DesktopAppRoute.ArtistDetail -> DesktopArtistDetailPanel(
                    appColors = appColors,
                    screen = sharedShellState.artistDetail,
                    albumCollectionLayout = sharedShellState.general.interfaceSettings.albumCollectionLayout,
                    albumSortOrder = sharedShellState.general.interfaceSettings.albumSortOrder,
                    groupAlbumsByReleaseType =
                        sharedShellState.general.interfaceSettings.groupAlbumsByReleaseType,
                    actions = sharedShellActions.artistDetailActions,
                )
                DesktopAppRoute.Playlists -> DesktopPlaylistsPanel(
                    appColors = appColors,
                    screen = sharedShellState.playlists,
                    actions = sharedShellActions.playlistsActions,
                    onPlaylistAction = sharedShellActions.mediaActions.onMediaItemAction ?: {},
                    availableLibraries = connection.availableMusicFolders,
                    selectedConnectionLibraryIds = connection.form.selectedMusicFolderIds,
                )
                DesktopAppRoute.PlaylistDetail -> DesktopPlaylistDetailPanel(
                    appColors = appColors,
                    screen = sharedShellState.playlistDetail,
                    actions = sharedShellActions.playlistDetailActions,
                    playlistsActions = sharedShellActions.playlistsActions,
                    availableLibraries = connection.availableMusicFolders,
                    selectedConnectionLibraryIds = connection.form.selectedMusicFolderIds,
                )
                DesktopAppRoute.Library -> {
                    DesktopLibraryPanel(
                        appColors = appColors,
                        library = sharedShellState.library,
                        listState = libraryListState,
                        actions = sharedShellActions.libraryActions,
                        onJumpToLetter = sharedShellActions.libraryActions.onJumpToLetter,
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
                        screen = sharedShellState.radio,
                        actions = sharedShellActions.radioActions,
                    )
                }
                DesktopAppRoute.Downloads -> DesktopDownloadsPanel(
                    appColors = appColors,
                    screen = sharedShellState.downloads,
                    onCancelDownloadJob = sharedShellActions.downloadsActions.onCancelJob,
                    onRetryDownloadJob = sharedShellActions.downloadsActions.onRetryJob,
                    onRefreshDownloads = sharedShellActions.downloadsActions.onRefresh,
                    onToggleKeepFavoritesDownloaded =
                        sharedShellActions.downloadsActions.onToggleKeepFavoritesDownloaded,
                    onDeleteAllDownloads = sharedShellActions.downloadsActions.onDeleteAll,
                    onDownloadAction = sharedShellActions.downloadsActions.onTrackAction,
                )
                DesktopAppRoute.Settings -> DesktopSettingsPanel(
                    appColors = appColors,
                    connectionSettings = sharedShellState.connectionSettings,
                    general = sharedShellState.general,
                    playback = sharedShellState.playback,
                    cache = sharedShellState.cache,
                    settingsSync = settingsSync,
                    connectionActions = sharedShellActions.connectionActions,
                    syncActions = settingsSyncActions,
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
