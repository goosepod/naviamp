package app.naviamp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
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
import app.naviamp.domain.smartplaylist.SmartPlaylistDefinition

@Composable
expect fun PlatformCoverArt(
    url: String?,
    colors: NaviampColors,
    size: Dp,
    cornerRadius: Dp,
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
fun NaviampSharedAppShell(
    modifier: Modifier = Modifier,
    status: String,
    serverVersion: String?,
    connected: Boolean,
    editingConnection: Boolean,
    restoringConnection: Boolean = false,
    connectionForm: ConnectionFormState,
    playbackSettings: PlaybackSettings = PlaybackSettings(),
    diagnostics: NaviampDiagnosticsUi = NaviampDiagnosticsUi(),
    supportsReplayGain: Boolean = false,
    supportsGapless: Boolean = true,
    supportsCrossfade: Boolean = false,
    showMobileNetworkQuality: Boolean = false,
    query: String,
    home: SharedHomeUi,
    searchResults: SharedSearchResultsUi,
    libraryArtists: List<SharedMediaItemUi>,
    libraryQuery: String = "",
    librarySyncStatus: NaviampLibrarySyncStatusUi = NaviampLibrarySyncStatusUi(),
    downloads: List<NaviampDownloadedTrackUi> = emptyList(),
    downloadBytes: Long = 0L,
    maxDownloadBytes: Long = 0L,
    downloadStatus: String? = null,
    playlistItems: List<SharedMediaItemUi>,
    recentPlaylistIds: List<String> = emptyList(),
    playlistSortMode: SharedPlaylistSortMode = SharedPlaylistSortMode.Alphabetical,
    playlistChoices: List<NaviampPlaylistChoiceUi> = emptyList(),
    playlistActionStatus: String? = null,
    radioStationItems: List<SharedMediaItemUi>,
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
    onCancelEditConnection: () -> Unit,
    onPlaybackSettingsChanged: (PlaybackSettings) -> Unit = {},
    onPlaybackSettingsChangedAndRedownload: (PlaybackSettings) -> Unit = onPlaybackSettingsChanged,
    onQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onClearSearch: () -> Unit = {},
    onLibraryQueryChanged: (String) -> Unit = {},
    onRefreshLibrary: () -> Unit = {},
    onTrackSelected: (AndroidTrackRowUi) -> Unit,
    onDownloadedTrackSelected: (NaviampDownloadedTrackUi) -> Unit = {},
    onDownloadedTrackAddToPlaylist: (NaviampDownloadedTrackUi, NaviampPlaylistChoiceUi?) -> Unit = { _, _ -> },
    onDownloadedTrackCreatePlaylistAndAdd: (NaviampDownloadedTrackUi, String) -> Unit = { _, _ -> },
    onRemoveDownload: (NaviampDownloadedTrackUi) -> Unit = {},
    onAlbumSelected: (SharedMediaItemUi) -> Unit,
    onMixAlbumSelected: (SharedMediaItemUi) -> Unit = onAlbumSelected,
    onAlbumPlay: (SharedAlbumDetailUi, Boolean) -> Unit = { _, _ -> },
    onAlbumRadio: (SharedAlbumDetailUi) -> Unit = {},
    onAlbumDownload: (SharedAlbumDetailUi) -> Unit = {},
    onAlbumAddToQueue: (SharedAlbumDetailUi) -> Unit = {},
    onAlbumAddToPlaylist: (SharedAlbumDetailUi, NaviampPlaylistChoiceUi?) -> Unit = { _, _ -> },
    onAlbumCreatePlaylistAndAdd: (SharedAlbumDetailUi, String) -> Unit = { _, _ -> },
    onAlbumTrackDownload: (AndroidTrackRowUi) -> Unit = {},
    onAlbumTrackAddToPlaylist: (AndroidTrackRowUi, NaviampPlaylistChoiceUi?) -> Unit = { _, _ -> },
    onAlbumTrackCreatePlaylistAndAdd: (AndroidTrackRowUi, String) -> Unit = { _, _ -> },
    onArtistSelected: (SharedMediaItemUi) -> Unit,
    onArtistRadio: (SharedArtistDetailUi) -> Unit = {},
    onArtistShuffle: (SharedArtistDetailUi) -> Unit = {},
    onArtistAddToQueue: (SharedArtistDetailUi) -> Unit = {},
    onArtistAddToPlaylist: (SharedArtistDetailUi, NaviampPlaylistChoiceUi?) -> Unit = { _, _ -> },
    onArtistCreatePlaylistAndAdd: (SharedArtistDetailUi, String) -> Unit = { _, _ -> },
    onArtistPopularPlay: (SharedArtistDetailUi) -> Unit = {},
    onArtistPopularRadio: (SharedArtistDetailUi) -> Unit = {},
    onArtistPopularAddToQueue: (SharedArtistDetailUi) -> Unit = {},
    onArtistPopularTrackSelected: (AndroidTrackRowUi) -> Unit = onTrackSelected,
    onAlbumTrackSelected: (AndroidTrackRowUi) -> Unit = onTrackSelected,
    onArtistPopularTrackAddToQueue: (AndroidTrackRowUi) -> Unit = {},
    onArtistPopularTrackDownload: (AndroidTrackRowUi) -> Unit = {},
    onArtistPopularTrackAddToPlaylist: (AndroidTrackRowUi, NaviampPlaylistChoiceUi?) -> Unit = { _, _ -> },
    onArtistPopularTrackCreatePlaylistAndAdd: (AndroidTrackRowUi, String) -> Unit = { _, _ -> },
    onFindSimilarArtists: (SharedArtistDetailUi) -> Unit = {},
    onSimilarArtistSelected: (SharedSimilarArtistUi) -> Unit = {},
    onSimilarArtistExternalSelected: (String) -> Unit = {},
    onArtistAlbumRadio: (SharedMediaItemUi) -> Unit = {},
    onArtistAlbumDownload: (SharedMediaItemUi) -> Unit = {},
    onArtistAlbumAddToQueue: (SharedMediaItemUi) -> Unit = {},
    onArtistAlbumAddToPlaylist: (SharedMediaItemUi, NaviampPlaylistChoiceUi?) -> Unit = { _, _ -> },
    onArtistAlbumCreatePlaylistAndAdd: (SharedMediaItemUi, String) -> Unit = { _, _ -> },
    onPlaylistSelected: (SharedMediaItemUi) -> Unit,
    onPlaylistSortModeChanged: (SharedPlaylistSortMode) -> Unit = {},
    onPlaylistPlay: (SharedMediaItemUi, Boolean) -> Unit = { _, _ -> },
    onPlaylistAddToQueue: (SharedPlaylistDetailUi) -> Unit = {},
    onPlaylistDownload: (SharedMediaItemUi) -> Unit = {},
    onPlaylistRename: (SharedMediaItemUi, String) -> Unit = { _, _ -> },
    onPlaylistDelete: (SharedMediaItemUi) -> Unit = {},
    onSmartPlaylistSave: suspend (SmartPlaylistDefinition) -> Unit = {},
    onPlaylistBack: () -> Unit = {},
    onPlaylistTrackSelected: (AndroidTrackRowUi) -> Unit = {},
    onTrackAddToQueue: (AndroidTrackRowUi) -> Unit = {},
    onRadioStationSelected: (SharedMediaItemUi) -> Unit,
    onHomeStationSelected: (SharedHomeStationUi) -> Unit = {},
    onOpenNowPlaying: () -> Unit,
    onCloseNowPlaying: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onPrevious: () -> Unit = {},
    onNext: () -> Unit = {},
    onSeek: (Double) -> Unit = {},
    onVolumeChanged: (Int) -> Unit = {},
    onToggleShuffle: () -> Unit = {},
    onCycleRepeatMode: () -> Unit = {},
    onToggleLyrics: () -> Unit = {},
    onToggleVisualizer: () -> Unit = {},
    onVisualizerSelected: (NaviampVisualizer) -> Unit = {},
    onTrackRadio: () -> Unit = {},
    onAddToPlaylist: (NaviampPlaylistChoiceUi?) -> Unit = {},
    onCreatePlaylistAndAdd: (String) -> Unit = {},
    onDownloadTrack: () -> Unit = {},
    onGoToAlbum: () -> Unit = {},
    onGoToArtist: () -> Unit = {},
    onQueueItemRadio: (NaviampNowPlayingItemUi) -> Unit = {},
    onQueueItemAddToPlaylist: (NaviampNowPlayingItemUi, NaviampPlaylistChoiceUi?) -> Unit = { _, _ -> },
    onQueueItemCreatePlaylistAndAdd: (NaviampNowPlayingItemUi, String) -> Unit = { _, _ -> },
    onQueueItemDownload: (NaviampNowPlayingItemUi) -> Unit = {},
    onToggleFavorite: () -> Unit = {},
    onRatingSelected: (Int?) -> Unit = {},
    onClearCache: () -> Unit = {},
    onClearLibrary: () -> Unit = {},
    onResetDatabase: () -> Unit = {},
) {
    val colors = NaviampColors.Dark
    val showFullNowPlaying = connected && !editingConnection && !restoringConnection && nowPlayingOpen && nowPlaying != null
    val routeUsesOwnScroll = connected &&
        !editingConnection &&
        !restoringConnection &&
        !showFullNowPlaying &&
        (
            albumDetail != null ||
                artistDetail != null ||
                playlistDetail != null ||
                selectedRoute == SharedRoute.Library ||
                selectedRoute == SharedRoute.Radio ||
                selectedRoute == SharedRoute.Downloads
            )
    val nowPlayingPlayerColors = if (nowPlaying != null) {
        rememberPlatformCoverArtPlayerColors(nowPlaying.coverArtUrl, colors)
    } else {
        NaviampPlayerColors.fallback(colors)
    }
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
                    if (!showFullNowPlaying && (restoringConnection || !connected || editingConnection)) {
                        Text("Naviamp", color = colors.primaryText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text(status, color = colors.secondaryText, fontSize = 13.sp)
                        serverVersion?.let {
                            Text("Connected to Navidrome $it.", color = colors.secondaryText, fontSize = 13.sp)
                        }
                    }

                    if (restoringConnection && !editingConnection) {
                        RestoringConnectionCard(status = status, colors = colors)
                    } else if (editingConnection || (!connected && selectedRoute != SharedRoute.Settings)) {
                        ConnectionCard(
                            form = connectionForm,
                            colors = colors,
                            isReconnect = connected,
                            onFormChanged = onConnectionFormChanged,
                            onConnect = onConnect,
                            onCancel = onCancelEditConnection.takeIf { connected },
                        )
                    } else {
                        ConnectedContent(
                            colors = colors,
                            selectedRoute = selectedRoute,
                            home = home,
                            query = query,
                            searchResults = searchResults,
                            libraryArtists = libraryArtists,
                            libraryQuery = libraryQuery,
                            librarySyncStatus = librarySyncStatus,
                            downloads = downloads,
                            downloadBytes = downloadBytes,
                            maxDownloadBytes = maxDownloadBytes,
                            downloadStatus = downloadStatus,
                            playlistItems = playlistItems,
                            recentPlaylistIds = recentPlaylistIds,
                            playlistSortMode = playlistSortMode,
                            playlistChoices = playlistChoices,
                            playlistActionStatus = playlistActionStatus,
                            radioStationItems = radioStationItems,
                            albumDetail = albumDetail,
                            artistDetail = artistDetail,
                            playlistDetail = playlistDetail,
                            nowPlaying = nowPlaying,
                            nowPlayingOpen = nowPlayingOpen,
                            visualizerBandsProvider = visualizerBandsProvider,
                            selectedVisualizer = selectedVisualizer,
                            playbackSettings = playbackSettings,
                            diagnostics = diagnostics,
                            supportsReplayGain = supportsReplayGain,
                            supportsGapless = supportsGapless,
                            supportsCrossfade = supportsCrossfade,
                            showMobileNetworkQuality = showMobileNetworkQuality,
                            onEditConnection = onEditConnection,
                            onPlaybackSettingsChanged = onPlaybackSettingsChanged,
                            onPlaybackSettingsChangedAndRedownload = onPlaybackSettingsChangedAndRedownload,
                            onClearCache = onClearCache,
                            onClearLibrary = onClearLibrary,
                            onResetDatabase = onResetDatabase,
                            onQueryChanged = onQueryChanged,
                            onSearch = onSearch,
                            onClearSearch = onClearSearch,
                            onLibraryQueryChanged = onLibraryQueryChanged,
                            onRefreshLibrary = onRefreshLibrary,
                            onTrackSelected = onTrackSelected,
                            onDownloadedTrackSelected = onDownloadedTrackSelected,
                            onDownloadedTrackAddToPlaylist = onDownloadedTrackAddToPlaylist,
                            onDownloadedTrackCreatePlaylistAndAdd = onDownloadedTrackCreatePlaylistAndAdd,
                            onRemoveDownload = onRemoveDownload,
                            onAlbumSelected = onAlbumSelected,
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
                            onArtistAlbumRadio = onArtistAlbumRadio,
                            onArtistAlbumDownload = onArtistAlbumDownload,
                            onArtistAlbumAddToQueue = onArtistAlbumAddToQueue,
                            onArtistAlbumAddToPlaylist = onArtistAlbumAddToPlaylist,
                            onArtistAlbumCreatePlaylistAndAdd = onArtistAlbumCreatePlaylistAndAdd,
                            onPlaylistSelected = onPlaylistSelected,
                            onPlaylistSortModeChanged = onPlaylistSortModeChanged,
                            onPlaylistPlay = onPlaylistPlay,
                            onPlaylistAddToQueue = onPlaylistAddToQueue,
                            onPlaylistDownload = onPlaylistDownload,
                            onPlaylistRename = onPlaylistRename,
                            onPlaylistDelete = onPlaylistDelete,
                            onSmartPlaylistSave = onSmartPlaylistSave,
                            onPlaylistBack = onPlaylistBack,
                            onPlaylistTrackSelected = onPlaylistTrackSelected,
                            onTrackAddToQueue = onTrackAddToQueue,
                            onRadioStationSelected = onRadioStationSelected,
                            onHomeStationSelected = onHomeStationSelected,
                            onOpenNowPlaying = onOpenNowPlaying,
                            onCloseNowPlaying = onCloseNowPlaying,
                            onPause = onPause,
                            onResume = onResume,
                            onStop = onStop,
                            onPrevious = onPrevious,
                            onNext = onNext,
                            onSeek = onSeek,
                            onVolumeChanged = onVolumeChanged,
                            onToggleShuffle = onToggleShuffle,
                            onCycleRepeatMode = onCycleRepeatMode,
                            onToggleLyrics = onToggleLyrics,
                            onToggleVisualizer = onToggleVisualizer,
                            onVisualizerSelected = onVisualizerSelected,
                            onTrackRadio = onTrackRadio,
                            onAddToPlaylist = onAddToPlaylist,
                            onCreatePlaylistAndAdd = onCreatePlaylistAndAdd,
                            onDownloadTrack = onDownloadTrack,
                            onGoToAlbum = onGoToAlbum,
                            onGoToArtist = onGoToArtist,
                            onQueueItemRadio = onQueueItemRadio,
                            onQueueItemAddToPlaylist = onQueueItemAddToPlaylist,
                            onQueueItemCreatePlaylistAndAdd = onQueueItemCreatePlaylistAndAdd,
                            onQueueItemDownload = onQueueItemDownload,
                            onToggleFavorite = onToggleFavorite,
                            onRatingSelected = onRatingSelected,
                        )
                    }
                }
                if (!showFullNowPlaying) {
                    if (connected && !editingConnection && !restoringConnection && nowPlaying != null) {
                        NaviampMiniNowPlaying(
                            nowPlaying = nowPlaying,
                            colors = colors,
                            onOpen = onOpenNowPlaying,
                            onPause = onPause,
                            onResume = onResume,
                            onPlayCurrent = onResume,
                            onPrevious = onPrevious,
                            onNext = onNext,
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
private fun ConnectionCard(
    form: ConnectionFormState,
    colors: NaviampColors,
    isReconnect: Boolean,
    onFormChanged: (ConnectionFormState) -> Unit,
    onConnect: () -> Unit,
    onCancel: (() -> Unit)?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsSectionTitle("Connection Details", colors)
        if (isReconnect) {
            Text(
                "Saved credentials loaded. Leave password blank to reuse them.",
                color = colors.mutedText,
                fontSize = 11.sp,
            )
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
                label = if (isReconnect) "Password (optional)" else "Password",
                colors = colors,
                isPassword = true,
                modifier = Modifier.weight(1f),
            )
        }
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
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PrimaryButton(
                label = if (isReconnect) "Save and connect" else "Connect",
                colors = colors,
                onClick = onConnect,
            )
            onCancel?.let {
                TextButton(onClick = it) {
                    Text("Cancel", color = colors.secondaryText)
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
    query: String,
    searchResults: SharedSearchResultsUi,
    libraryArtists: List<SharedMediaItemUi>,
    libraryQuery: String,
    librarySyncStatus: NaviampLibrarySyncStatusUi,
    downloads: List<NaviampDownloadedTrackUi>,
    downloadBytes: Long,
    maxDownloadBytes: Long,
    downloadStatus: String?,
    playlistItems: List<SharedMediaItemUi>,
    recentPlaylistIds: List<String>,
    playlistSortMode: SharedPlaylistSortMode,
    playlistChoices: List<NaviampPlaylistChoiceUi>,
    playlistActionStatus: String?,
    radioStationItems: List<SharedMediaItemUi>,
    albumDetail: SharedAlbumDetailUi?,
    artistDetail: SharedArtistDetailUi?,
    playlistDetail: SharedPlaylistDetailUi?,
    nowPlaying: NowPlayingUi?,
    nowPlayingOpen: Boolean,
    visualizerBandsProvider: () -> List<Float>,
    selectedVisualizer: NaviampVisualizer,
    playbackSettings: PlaybackSettings,
    diagnostics: NaviampDiagnosticsUi,
    supportsReplayGain: Boolean = false,
    supportsGapless: Boolean = true,
    supportsCrossfade: Boolean = false,
    showMobileNetworkQuality: Boolean = false,
    onEditConnection: () -> Unit,
    onPlaybackSettingsChanged: (PlaybackSettings) -> Unit,
    onPlaybackSettingsChangedAndRedownload: (PlaybackSettings) -> Unit,
    onQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onClearSearch: () -> Unit,
    onLibraryQueryChanged: (String) -> Unit,
    onRefreshLibrary: () -> Unit,
    onTrackSelected: (AndroidTrackRowUi) -> Unit,
    onDownloadedTrackSelected: (NaviampDownloadedTrackUi) -> Unit,
    onDownloadedTrackAddToPlaylist: (NaviampDownloadedTrackUi, NaviampPlaylistChoiceUi?) -> Unit,
    onDownloadedTrackCreatePlaylistAndAdd: (NaviampDownloadedTrackUi, String) -> Unit,
    onRemoveDownload: (NaviampDownloadedTrackUi) -> Unit,
    onAlbumSelected: (SharedMediaItemUi) -> Unit,
    onMixAlbumSelected: (SharedMediaItemUi) -> Unit,
    onAlbumPlay: (SharedAlbumDetailUi, Boolean) -> Unit,
    onAlbumRadio: (SharedAlbumDetailUi) -> Unit,
    onAlbumDownload: (SharedAlbumDetailUi) -> Unit,
    onAlbumAddToQueue: (SharedAlbumDetailUi) -> Unit,
    onAlbumAddToPlaylist: (SharedAlbumDetailUi, NaviampPlaylistChoiceUi?) -> Unit,
    onAlbumCreatePlaylistAndAdd: (SharedAlbumDetailUi, String) -> Unit,
    onAlbumTrackDownload: (AndroidTrackRowUi) -> Unit,
    onAlbumTrackAddToPlaylist: (AndroidTrackRowUi, NaviampPlaylistChoiceUi?) -> Unit,
    onAlbumTrackCreatePlaylistAndAdd: (AndroidTrackRowUi, String) -> Unit,
    onArtistSelected: (SharedMediaItemUi) -> Unit,
    onArtistRadio: (SharedArtistDetailUi) -> Unit,
    onArtistShuffle: (SharedArtistDetailUi) -> Unit,
    onArtistAddToQueue: (SharedArtistDetailUi) -> Unit,
    onArtistAddToPlaylist: (SharedArtistDetailUi, NaviampPlaylistChoiceUi?) -> Unit,
    onArtistCreatePlaylistAndAdd: (SharedArtistDetailUi, String) -> Unit,
    onArtistPopularPlay: (SharedArtistDetailUi) -> Unit,
    onArtistPopularRadio: (SharedArtistDetailUi) -> Unit,
    onArtistPopularAddToQueue: (SharedArtistDetailUi) -> Unit,
    onArtistPopularTrackSelected: (AndroidTrackRowUi) -> Unit,
    onAlbumTrackSelected: (AndroidTrackRowUi) -> Unit,
    onArtistPopularTrackAddToQueue: (AndroidTrackRowUi) -> Unit,
    onArtistPopularTrackDownload: (AndroidTrackRowUi) -> Unit,
    onArtistPopularTrackAddToPlaylist: (AndroidTrackRowUi, NaviampPlaylistChoiceUi?) -> Unit,
    onArtistPopularTrackCreatePlaylistAndAdd: (AndroidTrackRowUi, String) -> Unit,
    onFindSimilarArtists: (SharedArtistDetailUi) -> Unit,
    onSimilarArtistSelected: (SharedSimilarArtistUi) -> Unit,
    onSimilarArtistExternalSelected: (String) -> Unit,
    onArtistAlbumRadio: (SharedMediaItemUi) -> Unit,
    onArtistAlbumDownload: (SharedMediaItemUi) -> Unit,
    onArtistAlbumAddToQueue: (SharedMediaItemUi) -> Unit,
    onArtistAlbumAddToPlaylist: (SharedMediaItemUi, NaviampPlaylistChoiceUi?) -> Unit,
    onArtistAlbumCreatePlaylistAndAdd: (SharedMediaItemUi, String) -> Unit,
    onPlaylistSelected: (SharedMediaItemUi) -> Unit,
    onPlaylistSortModeChanged: (SharedPlaylistSortMode) -> Unit,
    onPlaylistPlay: (SharedMediaItemUi, Boolean) -> Unit,
    onPlaylistAddToQueue: (SharedPlaylistDetailUi) -> Unit,
    onPlaylistDownload: (SharedMediaItemUi) -> Unit,
    onPlaylistRename: (SharedMediaItemUi, String) -> Unit,
    onPlaylistDelete: (SharedMediaItemUi) -> Unit,
    onSmartPlaylistSave: suspend (SmartPlaylistDefinition) -> Unit,
    onPlaylistBack: () -> Unit,
    onPlaylistTrackSelected: (AndroidTrackRowUi) -> Unit,
    onTrackAddToQueue: (AndroidTrackRowUi) -> Unit,
    onRadioStationSelected: (SharedMediaItemUi) -> Unit,
    onHomeStationSelected: (SharedHomeStationUi) -> Unit,
    onOpenNowPlaying: () -> Unit,
    onCloseNowPlaying: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Double) -> Unit,
    onVolumeChanged: (Int) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onToggleLyrics: () -> Unit,
    onToggleVisualizer: () -> Unit,
    onVisualizerSelected: (NaviampVisualizer) -> Unit,
    onTrackRadio: () -> Unit,
    onAddToPlaylist: (NaviampPlaylistChoiceUi?) -> Unit,
    onCreatePlaylistAndAdd: (String) -> Unit,
    onDownloadTrack: () -> Unit,
    onGoToAlbum: () -> Unit,
    onGoToArtist: () -> Unit,
    onQueueItemRadio: (NaviampNowPlayingItemUi) -> Unit,
    onQueueItemAddToPlaylist: (NaviampNowPlayingItemUi, NaviampPlaylistChoiceUi?) -> Unit,
    onQueueItemCreatePlaylistAndAdd: (NaviampNowPlayingItemUi, String) -> Unit,
    onQueueItemDownload: (NaviampNowPlayingItemUi) -> Unit,
    onToggleFavorite: () -> Unit,
    onRatingSelected: (Int?) -> Unit,
    onClearCache: () -> Unit,
    onClearLibrary: () -> Unit,
    onResetDatabase: () -> Unit,
) {
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
            onBack = onCloseNowPlaying,
            onPause = onPause,
            onResume = onResume,
            onStop = onStop,
            onPrevious = onPrevious,
            onNext = onNext,
            onSeek = onSeek,
            onVolumeChanged = onVolumeChanged,
            onToggleShuffle = onToggleShuffle,
            onCycleRepeatMode = onCycleRepeatMode,
            onToggleLyrics = onToggleLyrics,
            onToggleVisualizer = onToggleVisualizer,
            onVisualizerSelected = onVisualizerSelected,
            onTrackRadio = onTrackRadio,
            onAddToPlaylist = onAddToPlaylist,
            onCreatePlaylistAndAdd = onCreatePlaylistAndAdd,
            onDownloadTrack = onDownloadTrack,
            onGoToAlbum = onGoToAlbum,
            onGoToArtist = onGoToArtist,
            onQueueItemRadio = onQueueItemRadio,
            onQueueItemAddToPlaylist = onQueueItemAddToPlaylist,
            onQueueItemCreatePlaylistAndAdd = onQueueItemCreatePlaylistAndAdd,
            onQueueItemDownload = onQueueItemDownload,
            onToggleFavorite = onToggleFavorite,
            onRatingSelected = onRatingSelected,
            onTrackSelected = onTrackSelected,
            onRadioStationSelected = onRadioStationSelected,
        )
        selectedRoute == SharedRoute.Settings -> SettingsContent(
            colors = colors,
            playbackSettings = playbackSettings,
            diagnostics = diagnostics,
            supportsReplayGain = supportsReplayGain,
            supportsGapless = supportsGapless,
            supportsCrossfade = supportsCrossfade,
            showMobileNetworkQuality = showMobileNetworkQuality,
            downloadBytes = downloadBytes,
            onEditConnection = onEditConnection,
            onPlaybackSettingsChanged = onPlaybackSettingsChanged,
            onPlaybackSettingsChangedAndRedownload = onPlaybackSettingsChangedAndRedownload,
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
            onTrackSelected = onAlbumTrackSelected,
            onTrackAddToQueue = onTrackAddToQueue,
            onTrackDownload = onAlbumTrackDownload,
            onTrackAddToPlaylist = onAlbumTrackAddToPlaylist,
            onTrackCreatePlaylistAndAdd = onAlbumTrackCreatePlaylistAndAdd,
            playlistChoices = playlistChoices,
            playlistActionStatus = playlistActionStatus,
        )
        artistDetail != null -> ArtistDetailContent(
            colors = colors,
            detail = artistDetail,
            onBack = onCloseNowPlaying,
            onArtistRadio = { onArtistRadio(artistDetail) },
            onArtistShuffle = { onArtistShuffle(artistDetail) },
            onArtistAddToQueue = { onArtistAddToQueue(artistDetail) },
            onArtistAddToPlaylist = { playlist -> onArtistAddToPlaylist(artistDetail, playlist) },
            onArtistCreatePlaylistAndAdd = { name -> onArtistCreatePlaylistAndAdd(artistDetail, name) },
            onPopularPlay = { onArtistPopularPlay(artistDetail) },
            onPopularRadio = { onArtistPopularRadio(artistDetail) },
            onPopularAddToQueue = { onArtistPopularAddToQueue(artistDetail) },
            onPopularTrackSelected = onArtistPopularTrackSelected,
            onPopularTrackAddToQueue = onArtistPopularTrackAddToQueue,
            onPopularTrackDownload = onArtistPopularTrackDownload,
            onPopularTrackAddToPlaylist = onArtistPopularTrackAddToPlaylist,
            onPopularTrackCreatePlaylistAndAdd = onArtistPopularTrackCreatePlaylistAndAdd,
            onFindSimilarArtists = { onFindSimilarArtists(artistDetail) },
            onSimilarArtistSelected = onSimilarArtistSelected,
            onSimilarArtistExternalSelected = onSimilarArtistExternalSelected,
            onAlbumSelected = onAlbumSelected,
            onAlbumRadio = onArtistAlbumRadio,
            onAlbumDownload = onArtistAlbumDownload,
            onAlbumAddToQueue = onArtistAlbumAddToQueue,
            onAlbumAddToPlaylist = onArtistAlbumAddToPlaylist,
            onAlbumCreatePlaylistAndAdd = onArtistAlbumCreatePlaylistAndAdd,
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
            onDownloadPlaylist = { onPlaylistDownload(playlistDetail.playlist) },
            onRenamePlaylist = onPlaylistRename,
            onDeletePlaylist = onPlaylistDelete,
            onTrackSelected = onPlaylistTrackSelected,
            onTrackAddToQueue = onTrackAddToQueue,
        )
        else -> when (selectedRoute) {
            SharedRoute.Home -> SharedHome(colors, home, onAlbumSelected, onMixAlbumSelected, onPlaylistSelected, onRadioStationSelected, onHomeStationSelected)
            SharedRoute.Playlists -> PlaylistsContent(
                colors = colors,
                playlists = playlistItems,
                recentPlaylistIds = recentPlaylistIds,
                sortMode = playlistSortMode,
                status = playlistActionStatus,
                onSortModeChanged = onPlaylistSortModeChanged,
                onPlaylistSelected = onPlaylistSelected,
                onPlaylistPlay = onPlaylistPlay,
                onPlaylistDownload = onPlaylistDownload,
                onPlaylistRename = onPlaylistRename,
                onPlaylistDelete = onPlaylistDelete,
                onSmartPlaylistSave = onSmartPlaylistSave,
            )
            SharedRoute.Library -> LibraryContent(
                colors = colors,
                items = libraryArtists,
                query = libraryQuery,
                syncStatus = librarySyncStatus,
                onQueryChanged = onLibraryQueryChanged,
                onRefreshLibrary = onRefreshLibrary,
                onArtistSelected = onArtistSelected,
            )
            SharedRoute.Search -> SearchContent(
                colors = colors,
                query = query,
                results = searchResults,
                onQueryChanged = onQueryChanged,
                onSearch = onSearch,
                onClearSearch = onClearSearch,
                onTrackSelected = onTrackSelected,
                onTrackAddToQueue = onTrackAddToQueue,
                onAlbumSelected = onAlbumSelected,
                onArtistSelected = onArtistSelected,
            )
            SharedRoute.Radio -> MediaListContent(colors, "Internet Radio", radioStationItems, "No stations found.", onRadioStationSelected)
            SharedRoute.Settings -> Unit
            SharedRoute.Downloads -> DownloadsContent(
                colors = colors,
                downloads = downloads,
                status = downloadStatus,
                downloadBytes = downloadBytes,
                maxDownloadBytes = maxDownloadBytes,
                onTrackSelected = onDownloadedTrackSelected,
                playlistChoices = playlistChoices,
                playlistActionStatus = playlistActionStatus,
                onAddToPlaylist = onDownloadedTrackAddToPlaylist,
                onCreatePlaylistAndAdd = onDownloadedTrackCreatePlaylistAndAdd,
                onRemoveDownload = onRemoveDownload,
            )
        }
    }
}

@Composable
private fun SharedHome(
    colors: NaviampColors,
    home: SharedHomeUi,
    onAlbumSelected: (SharedMediaItemUi) -> Unit,
    onMixAlbumSelected: (SharedMediaItemUi) -> Unit,
    onPlaylistSelected: (SharedMediaItemUi) -> Unit,
    onRadioStationSelected: (SharedMediaItemUi) -> Unit,
    onHomeStationSelected: (SharedHomeStationUi) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Music", color = colors.primaryText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        if (home.isEmpty) {
            PlaceholderTile("Home sections will appear after connection.", colors)
        }
        if (home.mixAlbums.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                SectionHeader("MIXES FOR YOU", colors)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    home.mixAlbums.take(6).forEach { album ->
                        MixCard(album, colors, onClick = { onMixAlbumSelected(album) })
                    }
                }
            }
        }
        HomeSection("Recently Added In Music", home.recentlyAddedAlbums, colors, onAlbumSelected)
        HomeSection("Recent Playlists", home.playlists, colors, onPlaylistSelected)
        HomeSection("Recently Played Radio", home.recentRadioStreams, colors, onRadioStationSelected, stationStyle = true)
        HomeSection("Recent Internet Radio", home.radioStations, colors, onRadioStationSelected)
        HomeStationSection(home.stations, colors, onHomeStationSelected)
        HomeSection("Recent Albums", home.recentAlbums, colors, onAlbumSelected)
        HomeSection("Frequently Played Albums", home.frequentAlbums, colors, onAlbumSelected)
        HomeSection("Random Albums", home.randomAlbums, colors, onAlbumSelected)
        home.genreSpotlightTitle?.let { title ->
            HomeSection("More In $title", home.genreSpotlightAlbums, colors, onAlbumSelected)
        }
        HomeSection("From ${home.decadeLabel}", home.decadeAlbums, colors, onAlbumSelected)
    }
}

@Composable
private fun SearchContent(
    colors: NaviampColors,
    query: String,
    results: SharedSearchResultsUi,
    onQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onClearSearch: () -> Unit,
    onTrackSelected: (AndroidTrackRowUi) -> Unit,
    onTrackAddToQueue: (AndroidTrackRowUi) -> Unit,
    onAlbumSelected: (SharedMediaItemUi) -> Unit,
    onArtistSelected: (SharedMediaItemUi) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        Text("Search", color = colors.primaryText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            NaviampTextField(
                value = query,
                onValueChange = onQueryChanged,
                label = "Search tracks",
                colors = colors,
                modifier = Modifier.weight(1f),
            )
            if (query.isNotBlank() || !results.isEmpty) {
                IconButton(onClick = onClearSearch) {
                    Icon(NaviampIcons.Close, contentDescription = "Clear search", tint = colors.secondaryText)
                }
            }
        }
        if (query.isNotBlank() && results.isEmpty) {
            Text("No matches found.", color = colors.secondaryText, fontSize = 12.sp)
        }
        MediaSection("Artists", results.artists, colors, onArtistSelected)
        MediaSection("Albums", results.albums, colors, onAlbumSelected)
        if (results.tracks.isNotEmpty()) {
            SectionHeader("TRACKS", colors)
            results.tracks.forEach { track ->
                TrackRow(track, colors, onTrackSelected, onAddToQueue = onTrackAddToQueue)
            }
        }
    }
}

@Composable
private fun MediaListContent(
    colors: NaviampColors,
    title: String,
    items: List<SharedMediaItemUi>,
    emptyText: String,
    onItemSelected: ((SharedMediaItemUi) -> Unit)? = null,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(title, color = colors.primaryText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        if (items.isEmpty()) {
            item {
                Text(emptyText, color = colors.secondaryText, fontSize = 13.sp)
            }
        }
        items(
            items = items,
            key = { item -> item.id },
        ) { item ->
            SharedMediaRow(
                item = item,
                colors = colors,
                onClick = onItemSelected?.let { { it(item) } },
            )
        }
    }
}

@Composable
private fun LibraryContent(
    colors: NaviampColors,
    items: List<SharedMediaItemUi>,
    query: String,
    syncStatus: NaviampLibrarySyncStatusUi,
    onQueryChanged: (String) -> Unit,
    onRefreshLibrary: () -> Unit,
    onArtistSelected: (SharedMediaItemUi) -> Unit,
) {
    val filteredItems = remember(items, query) {
        val normalizedQuery = query.trim().lowercase()
        if (normalizedQuery.isBlank()) {
            items
        } else {
            items.filter { item ->
                item.title.lowercase().contains(normalizedQuery) ||
                    item.subtitle.lowercase().contains(normalizedQuery) ||
                    item.meta.lowercase().contains(normalizedQuery)
            }
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("Library", color = colors.primaryText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                NaviampTextField(
                    value = query,
                    onValueChange = onQueryChanged,
                    label = "Search library artists",
                    colors = colors,
                    modifier = Modifier.weight(1f),
                )
                if (query.isNotBlank()) {
                    IconButton(onClick = { onQueryChanged("") }) {
                        Icon(NaviampIcons.Close, contentDescription = "Clear library search", tint = colors.secondaryText)
                    }
                }
            }
        }
        syncStatus.message?.let { message ->
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        message,
                        color = colors.secondaryText,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                    )
                    if (syncStatus.showRefresh) {
                        TextButton(
                            enabled = !syncStatus.isSyncing,
                            onClick = onRefreshLibrary,
                        ) {
                            Text(if (syncStatus.isSyncing) "Refreshing..." else "Refresh", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        if (filteredItems.isEmpty()) {
            item {
                Text(
                    if (query.isBlank()) "No library artists found." else "No library artists match.",
                    color = colors.secondaryText,
                    fontSize = 13.sp,
                )
            }
        }
        items(
            items = filteredItems,
            key = { item -> item.id },
        ) { item ->
            SharedMediaRow(
                item = item,
                colors = colors,
                onClick = { onArtistSelected(item) },
            )
        }
    }
}

@Composable
private fun DownloadsContent(
    colors: NaviampColors,
    downloads: List<NaviampDownloadedTrackUi>,
    status: String?,
    downloadBytes: Long,
    maxDownloadBytes: Long,
    onTrackSelected: (NaviampDownloadedTrackUi) -> Unit,
    playlistChoices: List<NaviampPlaylistChoiceUi>,
    playlistActionStatus: String?,
    onAddToPlaylist: (NaviampDownloadedTrackUi, NaviampPlaylistChoiceUi?) -> Unit,
    onCreatePlaylistAndAdd: (NaviampDownloadedTrackUi, String) -> Unit,
    onRemoveDownload: (NaviampDownloadedTrackUi) -> Unit,
) {
    var downloadForPlaylist by remember { mutableStateOf<NaviampDownloadedTrackUi?>(null) }
    val remainingBytes = (maxDownloadBytes - downloadBytes).coerceAtLeast(0L)
    val usedPercent = if (maxDownloadBytes > 0L) {
        ((downloadBytes.toDouble() / maxDownloadBytes.toDouble()) * 100.0).coerceIn(0.0, 100.0)
    } else {
        0.0
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Downloads", color = colors.primaryText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    "${downloads.size} files - ${downloadBytes.storageBytesLabel()} of ${maxDownloadBytes.storageBytesLabel()}",
                    color = colors.secondaryText,
                    fontSize = 12.sp,
                )
                Text(
                    "${remainingBytes.storageBytesLabel()} remaining - ${usedPercent.oneDecimalLabel()}% used",
                    color = colors.mutedText,
                    fontSize = 11.sp,
                )
            }
        }
        status?.takeIf { it.isNotBlank() }?.let { message ->
            item {
                Text(message, color = colors.secondaryText, fontSize = 12.sp)
            }
        }
        if (downloads.isEmpty()) {
            item {
                Text("Downloaded tracks will appear here.", color = colors.secondaryText, fontSize = 13.sp)
            }
        }
        items(
            items = downloads,
            key = { item -> item.id },
        ) { download ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onTrackSelected(download) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PlatformCoverArt(download.track.coverArtUrl, colors, 38.dp, 4.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(download.track.title, color = colors.primaryText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(download.track.subtitle, color = colors.secondaryText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(download.sizeBytes.storageBytesLabel(), color = colors.mutedText, fontSize = 11.sp)
                NaviampRowOverflowMenu(
                    colors = colors,
                    items = downloadRowActions(canRemove = true, canAddToPlaylist = true).mapNotNull { action ->
                        when (action.action) {
                            NaviampAction.AddToPlaylist -> NaviampRowMenuItem(
                                label = action.label,
                                icon = action.icon,
                                onClick = { downloadForPlaylist = download },
                                enabled = action.enabled,
                            )
                            NaviampAction.RemoveDownload -> NaviampRowMenuItem(
                                label = action.label,
                                icon = action.icon,
                                onClick = { onRemoveDownload(download) },
                                enabled = action.enabled,
                            )
                            else -> null
                        }
                    },
                )
            }
        }
    }

    downloadForPlaylist?.let { download ->
        AddToPlaylistDialog(
            title = download.track.title,
            colors = colors,
            playlists = playlistChoices,
            status = playlistActionStatus,
            onDismissRequest = { downloadForPlaylist = null },
            onAddToExisting = { playlist ->
                downloadForPlaylist = null
                onAddToPlaylist(download, playlist)
            },
            onCreateAndAdd = { name ->
                downloadForPlaylist = null
                onCreatePlaylistAndAdd(download, name)
            },
        )
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
    onTrackSelected: (AndroidTrackRowUi) -> Unit,
    onTrackAddToQueue: (AndroidTrackRowUi) -> Unit,
    onTrackDownload: (AndroidTrackRowUi) -> Unit,
    onTrackAddToPlaylist: (AndroidTrackRowUi, NaviampPlaylistChoiceUi?) -> Unit,
    onTrackCreatePlaylistAndAdd: (AndroidTrackRowUi, String) -> Unit,
    playlistChoices: List<NaviampPlaylistChoiceUi>,
    playlistActionStatus: String?,
) {
    var addAlbumToPlaylistOpen by remember(detail.album.id) { mutableStateOf(false) }
    var trackForPlaylist by remember(detail.album.id) { mutableStateOf<AndroidTrackRowUi?>(null) }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(NaviampIcons.Back, contentDescription = "Back", tint = colors.primaryText)
            }
            Text(detail.album.title, color = colors.primaryText, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            PlatformCoverArt(detail.album.coverArtUrl, colors, 96.dp, 8.dp)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
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
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    MiniPlayerIconButton(colors, detail.tracks.isNotEmpty(), NaviampTransportIcons.Play, "Play album", onPlayAlbum)
                    MiniPlayerIconButton(colors, detail.tracks.size > 1, NaviampTransportIcons.Shuffle, "Shuffle album", onShuffleAlbum)
                    MiniPlayerIconButton(colors, detail.tracks.isNotEmpty(), NaviampTransportIcons.Radio, "Start album radio", onAlbumRadio)
                    MiniPlayerIconButton(colors, detail.tracks.isNotEmpty(), NaviampIcons.Downloads, "Download album", onAlbumDownload)
                    MiniPlayerIconButton(colors, detail.tracks.isNotEmpty(), NaviampIcons.Queue, "Add album to queue", onAlbumAddToQueue)
                    MiniPlayerIconButton(colors, detail.tracks.isNotEmpty(), NaviampIcons.Playlist, "Add album to playlist") {
                        addAlbumToPlaylistOpen = true
                    }
                }
            }
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
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
                onTrackAddToPlaylist(track, playlist)
            },
            onCreateAndAdd = { name ->
                trackForPlaylist = null
                onTrackCreatePlaylistAndAdd(track, name)
            },
        )
    }
}

@Composable
private fun ArtistDetailContent(
    colors: NaviampColors,
    detail: SharedArtistDetailUi,
    onBack: () -> Unit,
    onArtistRadio: () -> Unit,
    onArtistShuffle: () -> Unit,
    onArtistAddToQueue: () -> Unit,
    onArtistAddToPlaylist: (NaviampPlaylistChoiceUi?) -> Unit,
    onArtistCreatePlaylistAndAdd: (String) -> Unit,
    onPopularPlay: () -> Unit,
    onPopularRadio: () -> Unit,
    onPopularAddToQueue: () -> Unit,
    onPopularTrackSelected: (AndroidTrackRowUi) -> Unit,
    onPopularTrackAddToQueue: (AndroidTrackRowUi) -> Unit,
    onPopularTrackDownload: (AndroidTrackRowUi) -> Unit,
    onPopularTrackAddToPlaylist: (AndroidTrackRowUi, NaviampPlaylistChoiceUi?) -> Unit,
    onPopularTrackCreatePlaylistAndAdd: (AndroidTrackRowUi, String) -> Unit,
    onFindSimilarArtists: () -> Unit,
    onSimilarArtistSelected: (SharedSimilarArtistUi) -> Unit,
    onSimilarArtistExternalSelected: (String) -> Unit,
    onAlbumSelected: (SharedMediaItemUi) -> Unit,
    onAlbumRadio: (SharedMediaItemUi) -> Unit,
    onAlbumDownload: (SharedMediaItemUi) -> Unit,
    onAlbumAddToQueue: (SharedMediaItemUi) -> Unit,
    onAlbumAddToPlaylist: (SharedMediaItemUi, NaviampPlaylistChoiceUi?) -> Unit,
    onAlbumCreatePlaylistAndAdd: (SharedMediaItemUi, String) -> Unit,
    playlistChoices: List<NaviampPlaylistChoiceUi>,
    playlistActionStatus: String?,
) {
    var addArtistToPlaylistOpen by remember(detail.artist.id) { mutableStateOf(false) }
    var popularTrackForPlaylist by remember(detail.artist.id) { mutableStateOf<AndroidTrackRowUi?>(null) }
    var albumForPlaylist by remember(detail.artist.id) { mutableStateOf<SharedMediaItemUi?>(null) }
    var biographyExpanded by remember(detail.artist.id) { mutableStateOf(false) }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(NaviampIcons.Back, contentDescription = "Back", tint = colors.primaryText)
            }
            PlatformCoverArt(detail.artist.coverArtUrl, colors, 64.dp, 32.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(detail.artist.title, color = colors.primaryText, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${detail.albums.size} albums", color = colors.secondaryText, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    MiniPlayerIconButton(colors, detail.albums.isNotEmpty(), NaviampTransportIcons.Radio, "Start artist radio", onArtistRadio)
                    MiniPlayerIconButton(colors, detail.albums.isNotEmpty(), NaviampIcons.Queue, "Add artist to queue", onArtistAddToQueue)
                    MiniPlayerIconButton(colors, detail.albums.isNotEmpty(), NaviampIcons.Playlist, "Add artist to playlist") {
                        addArtistToPlaylistOpen = true
                    }
                    MiniPlayerIconButton(colors, detail.popularTracks.isNotEmpty(), NaviampTransportIcons.Play, "Play popular tracks", onPopularPlay)
                    MiniPlayerIconButton(colors, true, NaviampIcons.Artist, "Find similar artists", onFindSimilarArtists)
                }
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
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            if (detail.similarArtists.isNotEmpty() || detail.similarArtistsStatus != null) {
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
                    MiniPlayerIconButton(colors, true, NaviampIcons.Artist, "Refresh similar artists", onFindSimilarArtists)
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
                        )
                    }
                }
            }
            Text("Albums", color = colors.primaryText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            if (detail.albums.isEmpty()) {
                Text("No albums found.", color = colors.secondaryText, fontSize = 13.sp)
            } else {
                detail.albums.forEach { album ->
                    SharedMediaRow(
                        item = album,
                        colors = colors,
                        onClick = { onAlbumSelected(album) },
                        menuItems = albumRowActions(
                            canStartRadio = true,
                            canDownload = true,
                            canAddToQueue = true,
                            canAddToPlaylist = true,
                        ).mapNotNull { action ->
                            when (action.action) {
                                NaviampAction.StartAlbumRadio -> NaviampRowMenuItem(action.label, action.icon, { onAlbumRadio(album) }, action.enabled)
                                NaviampAction.DownloadAlbum -> NaviampRowMenuItem(action.label, action.icon, { onAlbumDownload(album) }, action.enabled)
                                NaviampAction.AddToQueue -> NaviampRowMenuItem(action.label, action.icon, { onAlbumAddToQueue(album) }, action.enabled)
                                NaviampAction.AddToPlaylist -> NaviampRowMenuItem(action.label, action.icon, { albumForPlaylist = album }, action.enabled)
                                else -> null
                            }
                        },
                    )
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
                onPopularTrackAddToPlaylist(track, playlist)
            },
            onCreateAndAdd = { name ->
                popularTrackForPlaylist = null
                onPopularTrackCreatePlaylistAndAdd(track, name)
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
                onAlbumAddToPlaylist(album, playlist)
            },
            onCreateAndAdd = { name ->
                albumForPlaylist = null
                onAlbumCreatePlaylistAndAdd(album, name)
            },
        )
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
                    contentDescription = "Open external artist page",
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
    onBack: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Double) -> Unit,
    onVolumeChanged: (Int) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onToggleLyrics: () -> Unit,
    onToggleVisualizer: () -> Unit,
    onVisualizerSelected: (NaviampVisualizer) -> Unit,
    onTrackRadio: () -> Unit,
    onAddToPlaylist: (NaviampPlaylistChoiceUi?) -> Unit,
    onCreatePlaylistAndAdd: (String) -> Unit,
    onDownloadTrack: () -> Unit,
    onGoToAlbum: () -> Unit,
    onGoToArtist: () -> Unit,
    onQueueItemRadio: (NaviampNowPlayingItemUi) -> Unit,
    onQueueItemAddToPlaylist: (NaviampNowPlayingItemUi, NaviampPlaylistChoiceUi?) -> Unit,
    onQueueItemCreatePlaylistAndAdd: (NaviampNowPlayingItemUi, String) -> Unit,
    onQueueItemDownload: (NaviampNowPlayingItemUi) -> Unit,
    onToggleFavorite: () -> Unit,
    onRatingSelected: (Int?) -> Unit,
    onTrackSelected: (AndroidTrackRowUi) -> Unit,
    onRadioStationSelected: (SharedMediaItemUi) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        NaviampNowPlayingPanel(
            nowPlaying = nowPlaying,
            colors = colors,
            visualizerBandsProvider = visualizerBandsProvider,
            selectedVisualizer = selectedVisualizer,
            visualizerColors = playerColors,
            actions = NaviampNowPlayingActions(
                onPause = onPause,
                onResume = onResume,
                onPlayCurrent = onResume,
                onSeek = onSeek,
                onPrevious = onPrevious,
                onNext = onNext,
                onVolumeChanged = onVolumeChanged,
                onToggleShuffle = onToggleShuffle,
                onCycleRepeatMode = onCycleRepeatMode,
                onToggleLyrics = onToggleLyrics,
                onToggleVisualizer = onToggleVisualizer,
                onVisualizerSelected = onVisualizerSelected,
                onTrackRadio = onTrackRadio,
                onAddToPlaylist = onAddToPlaylist,
                onCreatePlaylistAndAdd = onCreatePlaylistAndAdd,
                onDownloadTrack = onDownloadTrack,
                onGoToAlbum = onGoToAlbum,
                onGoToArtist = onGoToArtist,
                onQueueItemRadio = onQueueItemRadio,
                onQueueItemAddToPlaylist = onQueueItemAddToPlaylist,
                onQueueItemCreatePlaylistAndAdd = onQueueItemCreatePlaylistAndAdd,
                onQueueItemDownload = onQueueItemDownload,
                onToggleFavorite = onToggleFavorite,
                onRatingSelected = onRatingSelected,
                onCollapse = onBack,
                onQueueItemSelected = { item ->
                    onTrackSelected(
                        AndroidTrackRowUi(
                            id = item.id,
                            title = item.title,
                            subtitle = item.subtitle,
                            coverArtUrl = item.coverArtUrl,
                            meta = item.meta,
                        ),
                    )
                },
                onRelatedItemSelected = { item ->
                    onTrackSelected(
                        AndroidTrackRowUi(
                            id = item.id,
                            title = item.title,
                            subtitle = item.subtitle,
                            coverArtUrl = item.coverArtUrl,
                            meta = item.meta,
                        ),
                    )
                },
                onRadioStationSelected = { item ->
                    onRadioStationSelected(
                        SharedMediaItemUi(
                            id = item.id,
                            title = item.title,
                            subtitle = item.subtitle,
                            meta = item.meta,
                            coverArtUrl = item.coverArtUrl,
                        ),
                    )
                },
            ),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
    }
}

@Composable
private fun SettingsContent(
    colors: NaviampColors,
    playbackSettings: PlaybackSettings,
    diagnostics: NaviampDiagnosticsUi,
    supportsReplayGain: Boolean,
    supportsGapless: Boolean,
    supportsCrossfade: Boolean,
    showMobileNetworkQuality: Boolean,
    downloadBytes: Long,
    onEditConnection: () -> Unit,
    onPlaybackSettingsChanged: (PlaybackSettings) -> Unit,
    onPlaybackSettingsChangedAndRedownload: (PlaybackSettings) -> Unit,
    onClearCache: () -> Unit,
    onClearLibrary: () -> Unit,
    onResetDatabase: () -> Unit,
) {
    NaviampSharedSettingsContent(
        colors = colors,
        playbackSettings = playbackSettings,
        diagnostics = diagnostics,
        supportsReplayGain = supportsReplayGain,
        supportsGapless = supportsGapless,
        supportsCrossfade = supportsCrossfade,
        showMobileNetworkQuality = showMobileNetworkQuality,
        downloadBytes = downloadBytes,
        onEditConnection = onEditConnection,
        onPlaybackSettingsChanged = onPlaybackSettingsChanged,
        onPlaybackSettingsChangedAndRedownload = onPlaybackSettingsChangedAndRedownload,
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
    onPause: () -> Unit,
    onResume: () -> Unit,
    onPlayCurrent: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
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
            onClick = onPrevious,
        )
        MiniPlayerIconButton(
            colors = colors,
            enabled = nowPlaying.canPlayPause,
            icon = if (nowPlaying.isPlaying) NaviampTransportIcons.Pause else NaviampTransportIcons.Play,
            contentDescription = if (nowPlaying.isPlaying) "Pause" else "Play",
            onClick = {
                if (nowPlaying.isPlaying) {
                    onPause()
                } else if (nowPlaying.isPaused) {
                    onResume()
                } else {
                    onPlayCurrent()
                }
            },
        )
        MiniPlayerIconButton(
            colors = colors,
            enabled = nowPlaying.hasNext,
            icon = NaviampTransportIcons.Next,
            contentDescription = "Next",
            onClick = onNext,
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
