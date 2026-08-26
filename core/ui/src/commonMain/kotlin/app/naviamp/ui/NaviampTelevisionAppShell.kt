package app.naviamp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.naviamp.domain.playback.PlaybackProgress
import kotlinx.coroutines.flow.StateFlow

/** Shared ten-foot shell. Android reports a TV display; it does not assemble this product UI. */
@Composable
fun NaviampTelevisionAppShell(
    modifier: Modifier = Modifier,
    uiState: NaviampAppShellUiState,
    settingsSync: NaviampSettingsSyncUi = NaviampSettingsSyncUi(),
    playbackProgress: StateFlow<PlaybackProgress>? = null,
    visualizerBandsProvider: () -> List<Float> = { uiState.nowPlaying?.visualizerFrame?.bands.orEmpty() },
    actions: NaviampAppShellActions,
    syncActions: NaviampSettingsSyncActions,
) {
    val colors = NaviampColors.Dark
    val connection = uiState.connectionSettings.connection
    val nowPlaying = uiState.nowPlaying?.withDisplaySettings(uiState.general.interfaceSettings.nowPlaying)
    val selectedDestination = naviampSelectedTelevisionDestination(
        selectedRoute = uiState.shellChrome.selectedRoute,
        nowPlayingOpen = uiState.shellChrome.nowPlayingOpen,
    )

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
            modifier = modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(
                            colors.background,
                            colors.controlSurface.copy(alpha = 0.82f),
                            colors.background,
                        ),
                    ),
                ),
        ) {
            when {
                connection.restoringConnection && !connection.editingConnection -> TelevisionStatusScreen(
                    title = "Restoring Naviamp TV",
                    message = connection.status.orEmpty(),
                    colors = colors,
                )
                !connection.connected || connection.editingConnection -> TelevisionConnectionScreen(
                    uiState = uiState,
                    settingsSync = settingsSync,
                    actions = actions,
                    syncActions = syncActions,
                    colors = colors,
                )
                else -> {
                    val transientContentOpen = uiState.shellChrome.nowPlayingOpen ||
                        uiState.home.collectionPage != null ||
                        uiState.albumDetail.selectedAlbum != null ||
                        uiState.artistDetail.selectedArtist != null ||
                        uiState.playlistDetail.selectedPlaylist != null
                    val backRoute = naviampTelevisionBackRoute(
                        selectedRoute = uiState.shellChrome.selectedRoute,
                        transientContentOpen = transientContentOpen,
                    )
                    NaviampSystemBackHandler(enabled = backRoute != null) {
                        backRoute?.let(actions.navigationActions.onRouteSelected)
                    }
                    if (uiState.shellChrome.nowPlayingOpen && nowPlaying != null) {
                        TelevisionNowPlaying(
                            nowPlaying = nowPlaying,
                            playbackProgress = playbackProgress,
                            colors = colors,
                            actions = actions.nowPlayingActions,
                            onClose = actions.navigationActions.onCloseNowPlaying,
                            onSearch = {
                                actions.navigationActions.onCloseNowPlaying()
                                actions.navigationActions.onRouteSelected(SharedRoute.Search)
                            },
                        )
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            TelevisionNavigationBar(
                                destinations = naviampTelevisionDestinations(),
                                selected = selectedDestination,
                                settingsSelected = uiState.shellChrome.selectedRoute == SharedRoute.Settings,
                                colors = colors,
                                onSelected = { destination ->
                                    actions.navigationActions.onCloseNowPlaying()
                                    destination.route?.let(actions.navigationActions.onRouteSelected)
                                },
                                onSettingsSelected = {
                                    actions.navigationActions.onCloseNowPlaying()
                                    actions.navigationActions.onRouteSelected(SharedRoute.Settings)
                                },
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(horizontal = 36.dp, vertical = 18.dp),
                            ) {
                                TelevisionConnectedContent(
                                    colors = colors,
                                    uiState = uiState,
                                    playbackProgress = playbackProgress,
                                    visualizerBandsProvider = visualizerBandsProvider,
                                    settingsSync = settingsSync,
                                    actions = actions,
                                    syncActions = syncActions,
                                )
                            }
                            if (nowPlaying != null) {
                                TelevisionMiniPlayer(
                                    nowPlaying = nowPlaying,
                                    colors = colors,
                                    onOpen = actions.navigationActions.onOpenNowPlaying,
                                    actions = actions.nowPlayingActions,
                                    modifier = Modifier.padding(horizontal = 36.dp, vertical = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TelevisionNavigationBar(
    destinations: List<NaviampTelevisionDestination>,
    selected: NaviampTelevisionDestination?,
    settingsSelected: Boolean,
    colors: NaviampColors,
    onSelected: (NaviampTelevisionDestination) -> Unit,
    onSettingsSelected: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.34f))
            .padding(horizontal = 28.dp, vertical = 14.dp),
    ) {
        Text(
            text = "Naviamp",
            color = colors.primaryText,
            fontSize = 25.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            modifier = Modifier.padding(end = 14.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            destinations.forEach { destination ->
                TelevisionNavigationButton(
                    destination = destination,
                    selected = destination == selected,
                    colors = colors,
                    modifier = Modifier.widthIn(min = 128.dp),
                    onClick = { onSelected(destination) },
                )
            }
        }
        Spacer(Modifier.weight(1f))
        TelevisionIconButton(
            enabled = true,
            icon = NaviampIcons.Settings,
            description = "Settings",
            colors = colors,
            selected = settingsSelected,
            onClick = onSettingsSelected,
        )
    }
}

@Composable
private fun TelevisionConnectedContent(
    colors: NaviampColors,
    uiState: NaviampAppShellUiState,
    playbackProgress: StateFlow<PlaybackProgress>?,
    visualizerBandsProvider: () -> List<Float>,
    settingsSync: NaviampSettingsSyncUi,
    actions: NaviampAppShellActions,
    syncActions: NaviampSettingsSyncActions,
) {
    val hasStandardDetail = uiState.home.collectionPage != null ||
        uiState.playlistDetail.selectedPlaylist != null
    when {
        uiState.albumDetail.selectedAlbum != null -> TelevisionAlbumDetail(
            screen = uiState.albumDetail,
            colors = colors,
            actions = actions.albumDetailActions,
        )
        uiState.artistDetail.selectedArtist != null -> TelevisionArtistDetail(
            screen = uiState.artistDetail,
            colors = colors,
            actions = actions.artistDetailActions,
        )
        hasStandardDetail -> ConnectedContent(
            colors = colors,
            uiState = uiState,
            playbackProgress = playbackProgress,
            visualizerBandsProvider = visualizerBandsProvider,
            settingsSync = settingsSync,
            actions = actions,
            syncActions = syncActions,
        )
        uiState.shellChrome.selectedRoute == SharedRoute.Home -> TelevisionHome(
            home = uiState.home,
            colors = colors,
            actions = actions.homeActions,
            mediaActions = actions.mediaActions,
        )
        uiState.shellChrome.selectedRoute == SharedRoute.Library -> TelevisionLibrary(
            screen = uiState.library,
            colors = colors,
            actions = actions.libraryActions,
            mediaActions = actions.mediaActions,
            onOpenPlaylists = { actions.navigationActions.onRouteSelected(SharedRoute.Playlists) },
        )
        uiState.shellChrome.selectedRoute == SharedRoute.Search -> TelevisionSearch(
            screen = uiState.search,
            colors = colors,
            actions = actions.searchActions,
            mediaActions = actions.mediaActions,
        )
        else -> ConnectedContent(
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

@Composable
private fun TelevisionNavigationButton(
    destination: NaviampTelevisionDestination,
    selected: Boolean,
    colors: NaviampColors,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) colors.primaryText else Color.Transparent,
            contentColor = if (selected) colors.background else colors.secondaryText,
        ),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
        shape = shape,
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .then(
                if (focused) {
                    Modifier.border(3.dp, colors.accent, shape)
                } else {
                    Modifier
                },
            ),
    ) {
        Text(
            text = destination.label,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TelevisionConnectionScreen(
    uiState: NaviampAppShellUiState,
    settingsSync: NaviampSettingsSyncUi,
    actions: NaviampAppShellActions,
    syncActions: NaviampSettingsSyncActions,
    colors: NaviampColors,
) {
    val connectionSettings = uiState.connectionSettings
    val connection = connectionSettings.connection
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 72.dp, vertical = 40.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .widthIn(max = 820.dp)
                .align(Alignment.CenterHorizontally),
        ) {
            Text("Set up Naviamp TV", color = colors.primaryText, fontSize = 36.sp, fontWeight = FontWeight.Black)
            Text(
                "Connect directly from this TV. Pairing a phone or computer will remain optional.",
                color = colors.secondaryText,
                fontSize = 18.sp,
            )
            Text(
                "While typing, choose Next on the TV keyboard to advance. Use Back or Esc to close the keyboard.",
                color = colors.mutedText,
                fontSize = 14.sp,
            )
            NaviampConnectionForm(
                form = connection.form,
                colors = colors,
                isReconnect = connection.editingSavedConnection,
                isConnecting = connection.isConnecting,
                connectionStatus = connection.status,
                connectionStatusIsError = connection.statusIsError,
                availableMusicFolders = connection.availableMusicFolders,
                musicFoldersStatus = connection.musicFoldersStatus,
                capabilities = connectionSettings.capabilities,
                allowLocalFileInputs = false,
                allowFallbackUrls = false,
                settingsSyncStatus = settingsSync.status,
                onFormChanged = actions.connectionActions.onFormChanged,
                onConnect = actions.connectionActions.onConnect,
                onImportSettingsSyncFile = null,
                onCancel = actions.connectionActions.onCancelConnectionForm.takeIf { connection.connected },
            )
        }
    }
}

@Composable
private fun TelevisionStatusScreen(
    title: String,
    message: String,
    colors: NaviampColors,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(48.dp),
    ) {
        Text(title, color = colors.primaryText, fontSize = 36.sp, fontWeight = FontWeight.Black)
        if (message.isNotBlank()) {
            Text(message, color = colors.secondaryText, fontSize = 18.sp, modifier = Modifier.padding(top = 12.dp))
        }
    }
}
