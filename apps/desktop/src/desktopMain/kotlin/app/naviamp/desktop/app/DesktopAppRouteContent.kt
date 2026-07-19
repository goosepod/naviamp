package app.naviamp.desktop

import app.naviamp.domain.app.NaviampRoute

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
import app.naviamp.ui.AlbumMixBuilderContent
import app.naviamp.ui.ArtistMixBuilderContent
import app.naviamp.ui.GenreMixBuilderContent
import app.naviamp.ui.NaviampAppShellActions
import app.naviamp.ui.NaviampAppShellUiState
import app.naviamp.ui.NaviampSettingsSyncActions
import app.naviamp.ui.NaviampSettingsSyncUi
import app.naviamp.ui.SharedHomeRoute
import app.naviamp.ui.SaveQueueAsPlaylistDialog
import app.naviamp.ui.SonicMixBuilderContent
import app.naviamp.ui.SonicPathBuilderContent

@Composable
fun ColumnScope.DesktopRouteContent(
    shellState: NaviampAppShellUiState,
    shellActions: NaviampAppShellActions,
    appColors: DesktopAppColors,
    appRoute: NaviampRoute,
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
                    appRoute == NaviampRoute.Home ||
                        appRoute == NaviampRoute.Library ||
                        appRoute == NaviampRoute.ArtistMix ||
                        appRoute == NaviampRoute.AlbumMix ||
                        appRoute == NaviampRoute.GenreMix ||
                        appRoute == NaviampRoute.SonicPath ||
                        appRoute == NaviampRoute.SonicMix ||
                        appRoute == NaviampRoute.Settings ||
                        appRoute == NaviampRoute.AlbumDetail ||
                        appRoute == NaviampRoute.ArtistDetail
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
                NaviampRoute.Player -> Unit
                NaviampRoute.Home -> SharedHomeRoute(
                    colors = appColors,
                    home = shellState.home,
                    actions = shellActions.homeActions,
                    mediaActions = shellActions.mediaActions,
                )
                NaviampRoute.AlbumDetail -> DesktopAlbumDetailPanel(
                    appColors = appColors,
                    screen = shellState.albumDetail,
                    actions = shellActions.albumDetailActions,
                )
                NaviampRoute.ArtistDetail -> DesktopArtistDetailPanel(
                    appColors = appColors,
                    screen = shellState.artistDetail,
                    interfaceSettings = shellState.general.interfaceSettings,
                    actions = shellActions.artistDetailActions,
                )
                NaviampRoute.Playlists -> DesktopPlaylistsPanel(
                    appColors = appColors,
                    screen = shellState.playlists,
                    actions = shellActions.playlistsActions,
                    mediaActions = shellActions.mediaActions,
                )
                NaviampRoute.PlaylistDetail -> DesktopPlaylistDetailPanel(
                    appColors = appColors,
                    screen = shellState.playlistDetail,
                    actions = shellActions.playlistDetailActions,
                    playlistsActions = shellActions.playlistsActions,
                )
                NaviampRoute.Library -> {
                    DesktopLibraryPanel(
                        appColors = appColors,
                        library = shellState.library,
                        listState = libraryListState,
                        actions = shellActions.libraryActions,
                        mediaActions = shellActions.mediaActions,
                    )
                }
                NaviampRoute.Search -> DesktopSearchPanel(
                    appColors = appColors,
                    search = shellState.search,
                    actions = shellActions.searchActions,
                    mediaActions = shellActions.mediaActions,
                )
                NaviampRoute.ArtistMix -> Column(
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
                NaviampRoute.AlbumMix -> Column(
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
                NaviampRoute.GenreMix -> Column(
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
                NaviampRoute.SonicPath -> Column(
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
                NaviampRoute.SonicMix -> Column(
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
                NaviampRoute.Radio -> Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    DesktopInternetRadioPanel(
                        appColors = appColors,
                        screen = shellState.radio,
                        actions = shellActions.radioActions,
                    )
                }
                NaviampRoute.Downloads -> DesktopDownloadsPanel(
                    appColors = appColors,
                    screen = shellState.downloads,
                    actions = shellActions.downloadsActions,
                )
                NaviampRoute.Settings -> DesktopSettingsPanel(
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
