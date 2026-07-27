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
    val nowPlaying = uiState.nowPlaying
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
        checker = applicationUpdateChecker,
    )
    val showFullNowPlaying = connected && !editingConnection && !restoringConnection && nowPlayingOpen && nowPlaying != null
    val routeUsesOwnScroll = connected &&
        !editingConnection &&
        !restoringConnection &&
        !showFullNowPlaying &&
        (
            albumDetail.selectedAlbum != null ||
                artistDetail.selectedArtist != null ||
                playlistDetail.selectedPlaylist != null ||
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
                                Modifier.verticalScroll(rememberScrollState())
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
    capabilities: NaviampConnectionCapabilitiesUi = NaviampConnectionCapabilitiesUi(),
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
            if (capabilities.insecureServerVerification || capabilities.customServerCertificates) {
                SettingsSectionTitle("TLS", colors)
            }
            if (capabilities.insecureServerVerification) {
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
            }
            if (capabilities.customServerCertificates) {
                NaviampTextField(
                    value = form.customCertificatePath,
                    onValueChange = { onFormChanged(form.copy(customCertificatePath = it)) },
                    label = "Trusted certificate or CA file",
                    colors = colors,
                    enabled = !form.skipTlsVerification,
                )
            }
            if (capabilities.clientCertificates) {
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
            }
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
    uiState: NaviampAppShellUiState,
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
    val nowPlaying = uiState.nowPlaying
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
    val albumPlayerColors = rememberNaviampCoverArtPlayerColors(nowPlaying?.coverArtUrl, colors)
    val singleBackgroundColor = naviampColorFromHex(interfaceSettings.singleColorHex)
        ?: naviampColorFromHex(DefaultSingleColorHex)!!
    val targetNowPlayingPlayerColors = when (interfaceSettings.appBackgroundStyle) {
        AppBackgroundStyle.SingleColor -> NaviampPlayerColors.fromSingleColor(singleBackgroundColor, colors)
        AppBackgroundStyle.Aurora -> albumPlayerColors.withAuroraTone(interfaceSettings.auroraTone)
        AppBackgroundStyle.AlbumBlur -> albumPlayerColors
    }
    val nowPlayingPlayerColors = animatedNaviampPlayerColors(targetNowPlayingPlayerColors)

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
        )
        playlistDetail.selectedPlaylist != null -> NaviampPlaylistDetailContent(
            colors = colors,
            screen = playlistDetail,
            actions = playlistDetailActions,
            playlistsActions = playlistsActions,
            playlistChoices = playlistChoices,
        )
        selectedRoute == SharedRoute.Settings -> NaviampSettingsContent(
            colors = colors,
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
        else -> when (selectedRoute) {
            SharedRoute.Home -> SharedHomeRoute(
                colors = colors,
                home = home,
                actions = homeActions,
                mediaActions = mediaActions,
            )
            SharedRoute.Playlists -> PullToRefreshRoute(
                isRefreshing = playlists.refreshing,
                onRefresh = playlistsActions.onRefresh,
                useScrollContainer = true,
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
                    listState = rememberLazyListState(),
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
                useScrollContainer = true,
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
fun NaviampAlbumDetailContent(
    colors: NaviampColors,
    screen: NaviampAlbumDetailScreenUi,
    actions: NaviampAlbumDetailActions,
    playlistChoices: List<NaviampPlaylistChoiceUi> = emptyList(),
    playlistActionStatus: String? = null,
) {
    val detail = screen.detail
    if (detail == null) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            IconButton(onClick = actions.onBack, modifier = Modifier.size(36.dp)) {
                Icon(NaviampIcons.Back, contentDescription = "Back", tint = colors.primaryText)
            }
            Text(
                screen.selectedAlbum?.title ?: "Album",
                color = colors.primaryText,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            screen.status?.let { Text(it, color = colors.secondaryText) }
        }
        return
    }
    AlbumDetailContent(
        colors = colors,
        detail = detail,
        onBack = actions.onBack,
        onPlayAlbum = {
            actions.onAlbumAction(
                NaviampAlbumDetailActionRequest(detail.album, NaviampAlbumDetailCommand.Play(false)),
            )
        },
        onShuffleAlbum = {
            actions.onAlbumAction(
                NaviampAlbumDetailActionRequest(detail.album, NaviampAlbumDetailCommand.Play(true)),
            )
        },
        onAlbumRadio = {
            actions.onAlbumAction(
                NaviampAlbumDetailActionRequest(detail.album, NaviampAlbumDetailCommand.StartRadio),
            )
        },
        onAlbumDownload = {
            actions.onAlbumAction(
                NaviampAlbumDetailActionRequest(detail.album, NaviampAlbumDetailCommand.Download),
            )
        },
        onAlbumAddToQueue = {
            actions.onAlbumAction(
                NaviampAlbumDetailActionRequest(detail.album, NaviampAlbumDetailCommand.AddToQueue),
            )
        },
        onAlbumAddToPlaylist = { playlist ->
            playlist?.let {
                actions.onAlbumAction(
                    NaviampAlbumDetailActionRequest(detail.album, NaviampAlbumDetailCommand.AddToPlaylist(it)),
                )
            }
        },
        onAlbumCreatePlaylistAndAdd = { name ->
            actions.onAlbumAction(
                NaviampAlbumDetailActionRequest(
                    detail.album,
                    NaviampAlbumDetailCommand.CreatePlaylistAndAdd(name),
                ),
            )
        },
        onAlbumFavoriteToggled = {
            actions.onAlbumAction(
                NaviampAlbumDetailActionRequest(detail.album, NaviampAlbumDetailCommand.ToggleFavorite),
            )
        },
        onTrackAction = actions.onTrackAction,
        playlistChoices = playlistChoices,
        playlistActionStatus = playlistActionStatus,
    )
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
    onTrackAction: (SharedTrackRowActionRequest) -> Unit,
    playlistChoices: List<NaviampPlaylistChoiceUi>,
    playlistActionStatus: String?,
) {
    var addAlbumToPlaylistOpen by remember(detail.album.id) { mutableStateOf(false) }
    var trackForPlaylist by remember(detail.album.id) { mutableStateOf<SharedTrackRowUi?>(null) }
    var albumImageOpen by remember(detail.album.id) { mutableStateOf(false) }
    val handleTrackAction: (SharedTrackRowActionRequest) -> Unit = { request ->
        if (request.action == SharedTrackRowAction.AddToPlaylist && request.playlistChoice == null) {
            trackForPlaylist = request.track
        } else {
            onTrackAction(request)
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            NaviampTooltip("Back", colors) {
                IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                    Icon(
                        NaviampIcons.Back,
                        contentDescription = "Back",
                        tint = colors.primaryText,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Text(
                detail.album.title,
                color = colors.primaryText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier.clickable(
                    enabled = detail.album.coverArtUrl != null,
                    onClick = { albumImageOpen = true },
                ),
            ) {
                NaviampCoverArt(detail.album.coverArtUrl, colors, 96.dp, 4.dp)
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                Text(
                    detail.album.subtitle,
                    color = colors.primaryText,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                detail.album.releaseYear?.let { year ->
                    Text(year.toString(), color = colors.secondaryText, fontSize = 12.sp)
                }
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
        Text(
            listOfNotNull(
                "${detail.tracks.size} tracks",
                detail.album.releaseYear?.toString(),
                detail.totalDurationLabel.takeIf { it.isNotBlank() }?.let { "Total $it" },
            ).joinToString(" - "),
            color = colors.secondaryText,
            modifier = Modifier.fillMaxWidth(),
        )
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
                    onTrackAction = handleTrackAction,
                    canSelect = true,
                    canStartRadio = false,
                    canAddToQueue = true,
                    canDownload = true,
                    canAddToPlaylist = true,
                    background = false,
                    verticalPadding = 0.dp,
                    showCoverArt = false,
                    showMenu = true,
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
fun NaviampArtistDetailContent(
    colors: NaviampColors,
    screen: NaviampArtistDetailScreenUi,
    albumCollectionLayout: AlbumCollectionLayout,
    albumSortOrder: AlbumSortOrder,
    groupAlbumsByReleaseType: Boolean,
    actions: NaviampArtistDetailActions,
    playlistChoices: List<NaviampPlaylistChoiceUi> = emptyList(),
    playlistActionStatus: String? = null,
) {
    val detail = screen.detail
    if (detail == null) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            IconButton(onClick = actions.onBack, modifier = Modifier.size(36.dp)) {
                Icon(NaviampIcons.Back, contentDescription = "Back", tint = colors.primaryText)
            }
            Text(
                screen.selectedArtist?.title ?: "Artist",
                color = colors.primaryText,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            screen.status?.let { Text(it, color = colors.secondaryText) }
        }
        return
    }
    ArtistDetailContent(
        colors = colors,
        detail = detail,
        albumCollectionLayout = albumCollectionLayout,
        albumSortOrder = albumSortOrder,
        groupAlbumsByReleaseType = groupAlbumsByReleaseType,
        onBack = actions.onBack,
        onArtistRadio = {
            actions.onArtistAction(
                NaviampArtistDetailActionRequest(detail.artist, NaviampArtistDetailCommand.StartRadio),
            )
        },
        onArtistPlay = { albums ->
            actions.onArtistAction(
                NaviampArtistDetailActionRequest(
                    detail.artist,
                    NaviampArtistDetailCommand.PlayCatalog(albums, shuffle = false),
                ),
            )
        },
        onArtistShuffle = { albums ->
            actions.onArtistAction(
                NaviampArtistDetailActionRequest(
                    detail.artist,
                    NaviampArtistDetailCommand.PlayCatalog(albums, shuffle = true),
                ),
            )
        },
        onArtistAddToQueue = {
            actions.onArtistAction(
                NaviampArtistDetailActionRequest(detail.artist, NaviampArtistDetailCommand.AddToQueue),
            )
        },
        onArtistAddToPlaylist = { playlist ->
            playlist?.let {
                actions.onArtistAction(
                    NaviampArtistDetailActionRequest(
                        detail.artist,
                        NaviampArtistDetailCommand.AddToPlaylist(it),
                    ),
                )
            }
        },
        onArtistCreatePlaylistAndAdd = { name ->
            actions.onArtistAction(
                NaviampArtistDetailActionRequest(
                    detail.artist,
                    NaviampArtistDetailCommand.CreatePlaylistAndAdd(name),
                ),
            )
        },
        onArtistFavoriteToggled = {
            actions.onArtistAction(
                NaviampArtistDetailActionRequest(detail.artist, NaviampArtistDetailCommand.ToggleFavorite),
            )
        },
        onPopularPlay = {
            actions.onArtistAction(
                NaviampArtistDetailActionRequest(detail.artist, NaviampArtistDetailCommand.PlayPopular),
            )
        },
        onPopularRadio = {
            actions.onArtistAction(
                NaviampArtistDetailActionRequest(detail.artist, NaviampArtistDetailCommand.StartPopularRadio),
            )
        },
        onPopularAddToQueue = {
            actions.onArtistAction(
                NaviampArtistDetailActionRequest(detail.artist, NaviampArtistDetailCommand.AddPopularToQueue),
            )
        },
        onPopularTrackAction = actions.onPopularTrackAction,
        onFindSimilarArtists = {
            actions.onArtistAction(
                NaviampArtistDetailActionRequest(detail.artist, NaviampArtistDetailCommand.FindSimilar),
            )
        },
        onSimilarArtistSelected = { similarArtist ->
            actions.onArtistAction(
                NaviampArtistDetailActionRequest(
                    detail.artist,
                    NaviampArtistDetailCommand.SelectSimilar(similarArtist),
                ),
            )
        },
        onSimilarArtistExternalSelected = { url ->
            actions.onArtistAction(
                NaviampArtistDetailActionRequest(
                    detail.artist,
                    NaviampArtistDetailCommand.OpenSimilarExternal(url),
                ),
            )
        },
        onAlbumSelected = { album ->
            actions.onAlbumAction(
                NaviampArtistAlbumActionRequest(album, NaviampArtistAlbumCommand.Select),
            )
        },
        onAlbumFavoriteToggled = { album ->
            actions.onAlbumAction(
                NaviampArtistAlbumActionRequest(album, NaviampArtistAlbumCommand.ToggleFavorite),
            )
        },
        onAlbumAction = actions.onAlbumAction,
        playlistChoices = playlistChoices,
        playlistActionStatus = playlistActionStatus,
    )
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
    onArtistPlay: (List<SharedMediaItemUi>) -> Unit,
    onArtistShuffle: (List<SharedMediaItemUi>) -> Unit,
    onArtistAddToQueue: () -> Unit,
    onArtistAddToPlaylist: (NaviampPlaylistChoiceUi?) -> Unit,
    onArtistCreatePlaylistAndAdd: (String) -> Unit,
    onArtistFavoriteToggled: () -> Unit,
    onPopularPlay: () -> Unit,
    onPopularRadio: () -> Unit,
    onPopularAddToQueue: () -> Unit,
    onPopularTrackAction: (SharedTrackRowActionRequest) -> Unit,
    onFindSimilarArtists: () -> Unit,
    onSimilarArtistSelected: (SharedSimilarArtistUi) -> Unit,
    onSimilarArtistExternalSelected: (String) -> Unit,
    onAlbumSelected: (SharedMediaItemUi) -> Unit,
    onAlbumFavoriteToggled: (SharedMediaItemUi) -> Unit,
    onAlbumAction: (NaviampArtistAlbumActionRequest) -> Unit,
    playlistChoices: List<NaviampPlaylistChoiceUi>,
    playlistActionStatus: String?,
) {
    var addArtistToPlaylistOpen by remember(detail.artist.id) { mutableStateOf(false) }
    var popularTrackForPlaylist by remember(detail.artist.id) { mutableStateOf<SharedTrackRowUi?>(null) }
    var albumForPlaylist by remember(detail.artist.id) { mutableStateOf<SharedMediaItemUi?>(null) }
    var biographyExpanded by remember(detail.artist.id) { mutableStateOf(false) }
    var artistImageOpen by remember(detail.artist.id) { mutableStateOf(false) }
    val handlePopularTrackAction: (SharedTrackRowActionRequest) -> Unit = { request ->
        if (request.action == SharedTrackRowAction.AddToPlaylist && request.playlistChoice == null) {
            popularTrackForPlaylist = request.track
        } else {
            onPopularTrackAction(request)
        }
    }
    val similarArtistsVisible = detail.similarArtistsExpanded
    val visibleAlbumSections = if (groupAlbumsByReleaseType) {
        detail.albumSections
    } else {
        listOf(SharedAlbumSectionUi("Albums", detail.albums))
    }.map { section ->
        section.copy(albums = section.albums.sortedForAlbumDisplay(albumSortOrder))
    }
    val displayedAlbums = visibleAlbumSections.flatMap { section -> section.albums }
    val albumMenuItems: (SharedMediaItemUi) -> List<NaviampRowMenuItem> = { album ->
        albumRowActions(
            canStartRadio = true,
            canDownload = true,
            canAddToQueue = true,
            canAddToPlaylist = true,
            canFavorite = false,
            favoriteActive = album.favoriteActive,
        ).mapNotNull { action ->
            val command = action.action.albumMediaCommandOrNull()
            when {
                action.action == NaviampAction.AddToPlaylist -> NaviampRowMenuItem(
                    action.label,
                    action.icon,
                    { albumForPlaylist = album },
                    action.enabled,
                )
                command != null -> {
                NaviampRowMenuItem(
                    action.label,
                    action.icon,
                    { onAlbumAction(NaviampArtistAlbumActionRequest(album, command)) },
                    action.enabled,
                )
                }
                else -> null
            }
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            NaviampTooltip("Back", colors) {
                IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                    Icon(
                        NaviampIcons.Back,
                        contentDescription = "Back",
                        tint = colors.primaryText,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Text(
                detail.artist.title,
                color = colors.primaryText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier.clickable(
                    enabled = detail.artist.coverArtUrl != null,
                    onClick = { artistImageOpen = true },
                ),
            ) {
                NaviampCoverArt(detail.artist.coverArtUrl, colors, 96.dp, 48.dp)
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    detail.artist.title,
                    color = colors.primaryText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    detail.localLibraryLabel.ifBlank { "${detail.albums.size} albums" },
                    color = colors.secondaryText,
                    fontSize = 13.sp,
                )
                NaviampResponsiveActionRow(
                    colors = colors,
                    actions = listOf(
                        NaviampDetailAction("Play artist catalog", NaviampTransportIcons.Play, { onArtistPlay(displayedAlbums) }, displayedAlbums.isNotEmpty()),
                        NaviampDetailAction("Start artist radio", NaviampTransportIcons.Radio, onArtistRadio, detail.albums.isNotEmpty()),
                        NaviampDetailAction(
                            if (detail.artist.favoriteActive) "Remove artist favorite" else "Favorite artist",
                            NaviampTransportIcons.Heart,
                            onArtistFavoriteToggled,
                            detail.artist.canFavorite,
                        ),
                        NaviampDetailAction(
                            if (similarArtistsVisible) "Hide similar artists" else "Find similar artists",
                            NaviampIcons.Artist,
                            onFindSimilarArtists,
                            selected = similarArtistsVisible,
                        ),
                        NaviampDetailAction("Add artist to queue", NaviampIcons.Queue, onArtistAddToQueue, detail.albums.isNotEmpty()),
                        NaviampDetailAction("Add artist to playlist", NaviampIcons.Playlist, { addArtistToPlaylistOpen = true }, detail.albums.isNotEmpty()),
                        NaviampDetailAction("Shuffle artist catalog", NaviampTransportIcons.Shuffle, { onArtistShuffle(displayedAlbums) }, displayedAlbums.isNotEmpty()),
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
            verticalArrangement = Arrangement.spacedBy(6.dp),
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
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
                    detail.popularTracks.forEach { track ->
                        TrackRow(
                            track,
                            colors,
                            onTrackAction = handlePopularTrackAction,
                            canSelect = true,
                            canStartRadio = false,
                            canAddToQueue = true,
                            canDownload = true,
                            canAddToPlaylist = true,
                        )
                    }
                }
            }
            Text("DISCOGRAPHY", color = colors.primaryText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            if (detail.albums.isEmpty()) {
                Text("No albums found.", color = colors.secondaryText, fontSize = 13.sp)
            } else {
                visibleAlbumSections.forEach { section ->
                    Text(section.title.uppercase(), color = colors.primaryText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    if (albumCollectionLayout == AlbumCollectionLayout.Grid) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            section.albums.forEach { album ->
                                SharedAlbumGridTile(
                                    item = album,
                                    colors = colors,
                                    onClick = { onAlbumSelected(album) },
                                    menuItems = albumMenuItems(album),
                                    onFavoriteToggled = { selected ->
                                        onAlbumAction(
                                            NaviampArtistAlbumActionRequest(
                                                selected,
                                                NaviampArtistAlbumCommand.ToggleFavorite,
                                            ),
                                        )
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
                                    onClick = { onAlbumSelected(album) },
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
                onAlbumAction(
                    NaviampArtistAlbumActionRequest(
                        album,
                        NaviampArtistAlbumCommand.AddToPlaylist(playlist),
                    ),
                )
            },
            onCreateAndAdd = { name ->
                albumForPlaylist = null
                onAlbumAction(
                    NaviampArtistAlbumActionRequest(
                        album,
                        NaviampArtistAlbumCommand.CreatePlaylistAndAdd(name),
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
            NaviampExpandedMediaImage(
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
        NaviampCoverArt(artist.imageUrl, colors, 42.dp, 21.dp)
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
            NaviampTooltip("View in browser", colors) {
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
fun NaviampSettingsContent(
    colors: NaviampColors,
    modifier: Modifier = Modifier,
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
        isConnecting = connection.isConnecting,
        connectionStatus = connection.status,
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
        onImportSettingsSyncFolder = syncActions.onImportFolder.takeIf { settingsSync.available },
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
