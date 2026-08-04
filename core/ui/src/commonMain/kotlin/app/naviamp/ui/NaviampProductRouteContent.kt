package app.naviamp.ui

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
@Composable
fun ColumnScope.NaviampProductRouteContent(
    shellState: NaviampAppShellUiState,
    shellActions: NaviampAppShellActions,
    colors: NaviampColors,
    appRoute: NaviampRoute,
    libraryListState: LazyListState,
    settingsContent: @Composable () -> Unit,
) {
    var saveSonicPathDialogOpen by remember { mutableStateOf(false) }
    var saveSonicMixDialogOpen by remember { mutableStateOf(false) }
    val contentScrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .then(
                if (naviampProductRouteUsesOuterVerticalScroll(appRoute)) {
                    Modifier.verticalScroll(contentScrollState)
                } else {
                    Modifier
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
                    colors = colors,
                    home = shellState.home,
                    actions = shellActions.homeActions,
                    mediaActions = shellActions.mediaActions,
                )
                NaviampRoute.AlbumDetail -> NaviampAlbumDetailContent(
                    colors = colors,
                    screen = shellState.albumDetail,
                    actions = shellActions.albumDetailActions,
                    playlistChoices = shellState.playlistChoices,
                    playlistActionStatus = shellState.playlists.status,
                )
                NaviampRoute.ArtistDetail -> NaviampArtistDetailContent(
                    colors = colors,
                    screen = shellState.artistDetail,
                    albumCollectionLayout = shellState.general.interfaceSettings.albumCollectionLayout,
                    albumSortOrder = shellState.general.interfaceSettings.albumSortOrder,
                    groupAlbumsByReleaseType = shellState.general.interfaceSettings.groupAlbumsByReleaseType,
                    actions = shellActions.artistDetailActions,
                    playlistChoices = shellState.playlistChoices,
                    playlistActionStatus = shellState.playlists.status,
                )
                NaviampRoute.Playlists -> NaviampPlaylistsContent(
                    colors = colors,
                    screen = shellState.playlists,
                    actions = shellActions.playlistsActions,
                    mediaActions = shellActions.mediaActions,
                    playlistChoices = shellState.playlistChoices,
                )
                NaviampRoute.PlaylistDetail -> NaviampPlaylistDetailContent(
                    colors = colors,
                    screen = shellState.playlistDetail,
                    actions = shellActions.playlistDetailActions,
                    playlistsActions = shellActions.playlistsActions,
                    playlistChoices = shellState.playlistChoices,
                )
                NaviampRoute.Library -> NaviampLibraryContent(
                    colors = colors,
                    screen = shellState.library,
                    actions = shellActions.libraryActions,
                    mediaActions = shellActions.mediaActions,
                    listState = libraryListState,
                )
                NaviampRoute.Search -> NaviampSearchContent(
                    colors = colors,
                    screen = shellState.search,
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
                            colors = colors,
                            builder = shellState.artistMixBuilder,
                            actions = shellActions.artistMixActions,
                            showPlayMixButton = false,
                        )
                    }
                    if (shellState.artistMixBuilder.selectedArtists.isNotEmpty()) {
                        Button(
                            onClick = shellActions.artistMixActions.onPlay,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accent,
                                contentColor = colors.onAccent,
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
                            colors = colors,
                            builder = shellState.albumMixBuilder,
                            actions = shellActions.albumMixActions,
                            showPlayMixButton = false,
                        )
                    }
                    if (shellState.albumMixBuilder.selectedAlbums.isNotEmpty()) {
                        Button(
                            onClick = shellActions.albumMixActions.onPlay,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accent,
                                contentColor = colors.onAccent,
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
                            colors = colors,
                            builder = shellState.genreMixBuilder,
                            actions = shellActions.genreMixActions,
                            showPlayMixButton = false,
                        )
                    }
                    if (shellState.genreMixBuilder.selectedGenres.isNotEmpty()) {
                        Button(
                            onClick = shellActions.genreMixActions.onPlay,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accent,
                                contentColor = colors.onAccent,
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
                            colors = colors,
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
                                    containerColor = colors.accent,
                                    contentColor = colors.onAccent,
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
                            colors = colors,
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
                                    containerColor = colors.accent,
                                    contentColor = colors.onAccent,
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
                NaviampRoute.Radio -> NaviampInternetRadioContent(
                    colors = colors,
                    screen = shellState.radio,
                    actions = shellActions.radioActions,
                )
                NaviampRoute.Downloads -> NaviampDownloadsContent(
                    colors = colors,
                    screen = shellState.downloads,
                    actions = shellActions.downloadsActions,
                    playlistChoices = shellState.playlistChoices,
                    playlistActionStatus = shellState.playlists.status,
                )
                NaviampRoute.Settings -> settingsContent()
            }
        }
    }
    if (saveSonicPathDialogOpen) {
        SaveQueueAsPlaylistDialog(
            colors = colors,
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
            colors = colors,
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

internal fun naviampProductRouteUsesOuterVerticalScroll(route: NaviampRoute): Boolean =
    when (route) {
        NaviampRoute.Search,
        NaviampRoute.Playlists,
        NaviampRoute.Radio,
        NaviampRoute.Settings,
        -> true
        NaviampRoute.Player,
        NaviampRoute.Home,
        NaviampRoute.Library,
        NaviampRoute.ArtistMix,
        NaviampRoute.AlbumMix,
        NaviampRoute.GenreMix,
        NaviampRoute.SonicPath,
        NaviampRoute.SonicMix,
        NaviampRoute.Downloads,
        NaviampRoute.AlbumDetail,
        NaviampRoute.ArtistDetail,
        NaviampRoute.PlaylistDetail,
        -> false
    }
