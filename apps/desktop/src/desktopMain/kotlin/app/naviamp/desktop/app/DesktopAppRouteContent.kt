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
    libraryListState: LazyListState,
    settingsSync: NaviampSettingsSyncUi,
    settingsSyncActions: NaviampSettingsSyncActions,
) {
    var saveSonicPathDialogOpen by remember { mutableStateOf(false) }
    var saveSonicMixDialogOpen by remember { mutableStateOf(false) }
    val contentScrollState = rememberScrollState()

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
                    home = shellState.home.content,
                    isRefreshing = shellState.home.refreshing,
                    onRefresh = shellActions.homeActions.onRefresh,
                    onAlbumSelected = shellActions.mediaActions.onAlbumSelected,
                    onAlbumFavoriteToggled = shellActions.mediaActions.onAlbumFavoriteToggled,
                    onMixAlbumSelected = shellActions.mediaActions.onMixAlbumSelected,
                    onPlaylistSelected = shellActions.mediaActions.onPlaylistSelected,
                    onRecentRadioSelected = shellActions.homeActions.onRecentRadioSelected,
                    onInternetRadioStationSelected =
                        shellActions.homeActions.onInternetRadioStationSelected,
                    onMixBuilderSelected = shellActions.homeActions.onMixBuilderSelected,
                    onHomeStationSelected = shellActions.homeActions.onStationSelected,
                    onSonicDiscoveryTrackAction = shellActions.homeActions.onSonicDiscoveryTrackAction,
                    onRecentlyPlayedTrackAction = shellActions.homeActions.onRecentlyPlayedTrackAction,
                )
                DesktopAppRoute.AlbumDetail -> DesktopAlbumDetailPanel(
                    appColors = appColors,
                    screen = shellState.albumDetail,
                    actions = shellActions.albumDetailActions,
                )
                DesktopAppRoute.ArtistDetail -> DesktopArtistDetailPanel(
                    appColors = appColors,
                    screen = shellState.artistDetail,
                    albumCollectionLayout = shellState.general.interfaceSettings.albumCollectionLayout,
                    albumSortOrder = shellState.general.interfaceSettings.albumSortOrder,
                    groupAlbumsByReleaseType =
                        shellState.general.interfaceSettings.groupAlbumsByReleaseType,
                    actions = shellActions.artistDetailActions,
                )
                DesktopAppRoute.Playlists -> DesktopPlaylistsPanel(
                    appColors = appColors,
                    screen = shellState.playlists,
                    actions = shellActions.playlistsActions,
                    onPlaylistAction = shellActions.mediaActions.onMediaItemAction ?: {},
                )
                DesktopAppRoute.PlaylistDetail -> DesktopPlaylistDetailPanel(
                    appColors = appColors,
                    screen = shellState.playlistDetail,
                    actions = shellActions.playlistDetailActions,
                    playlistsActions = shellActions.playlistsActions,
                )
                DesktopAppRoute.Library -> {
                    DesktopLibraryPanel(
                        appColors = appColors,
                        library = shellState.library,
                        listState = libraryListState,
                        actions = shellActions.libraryActions,
                        mediaActions = shellActions.mediaActions,
                    )
                }
                DesktopAppRoute.Search -> DesktopSearchPanel(
                    appColors = appColors,
                    search = shellState.search,
                    actions = shellActions.searchActions,
                    mediaActions = shellActions.mediaActions,
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
                            builder = shellState.artistMixBuilder,
                            actions = shellActions.artistMixActions,
                            showPlayMixButton = false,
                        )
                    }
                    if (shellState.artistMixBuilder.selectedArtists.isNotEmpty()) {
                        Button(
                            onClick = shellActions.artistMixActions.onPlay,
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
                            builder = shellState.albumMixBuilder,
                            actions = shellActions.albumMixActions,
                            showPlayMixButton = false,
                        )
                    }
                    if (shellState.albumMixBuilder.selectedAlbums.isNotEmpty()) {
                        Button(
                            onClick = shellActions.albumMixActions.onPlay,
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
                            builder = shellState.genreMixBuilder,
                            actions = shellActions.genreMixActions,
                            showPlayMixButton = false,
                        )
                    }
                    if (shellState.genreMixBuilder.selectedGenres.isNotEmpty()) {
                        Button(
                            onClick = shellActions.genreMixActions.onPlay,
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
                            builder = shellState.sonicPathBuilder,
                            actions = shellActions.sonicPathActions,
                            showPathActions = false,
                        )
                    }
                    if (shellState.sonicPathBuilder.hasPath) {
                        androidx.compose.foundation.layout.Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Button(
                                onClick = shellActions.sonicPathActions.onPlay,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = appColors.accent,
                                    contentColor = appColors.onAccent,
                                ),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Play Path")
                            }
                            Button(
                                onClick = shellActions.sonicPathActions.onAddToQueue,
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
                            builder = shellState.sonicMixBuilder,
                            actions = shellActions.sonicMixActions,
                            showMixActions = false,
                        )
                    }
                    if (shellState.sonicMixBuilder.hasMix) {
                        androidx.compose.foundation.layout.Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Button(
                                onClick = shellActions.sonicMixActions.onPlay,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = appColors.accent,
                                    contentColor = appColors.onAccent,
                                ),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Play Mix")
                            }
                            Button(
                                onClick = shellActions.sonicMixActions.onAddToQueue,
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
                        screen = shellState.radio,
                        actions = shellActions.radioActions,
                    )
                }
                DesktopAppRoute.Downloads -> DesktopDownloadsPanel(
                    appColors = appColors,
                    screen = shellState.downloads,
                    actions = shellActions.downloadsActions,
                )
                DesktopAppRoute.Settings -> DesktopSettingsPanel(
                    appColors = appColors,
                    connectionSettings = shellState.connectionSettings,
                    general = shellState.general,
                    playback = shellState.playback,
                    cache = shellState.cache,
                    settingsSync = settingsSync,
                    connectionActions = shellActions.connectionActions,
                    syncActions = settingsSyncActions,
                    valueActions = shellActions.valueActions,
                    maintenanceActions = shellActions.maintenanceActions,
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
                shellActions.sonicPathActions.onSaveAsPlaylist(name)
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
                shellActions.sonicMixActions.onSaveAsPlaylist(name)
                saveSonicMixDialogOpen = false
            },
        )
    }
}
