package app.naviamp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import app.naviamp.domain.Track
import app.naviamp.domain.home.HomeContent
import app.naviamp.domain.home.homeStations
import app.naviamp.domain.waveform.AudioWaveform
import app.naviamp.domain.playback.PlaybackVisualizerFrame

data class NaviampColors(
    val background: Color = Color(0xFF101114),
    val backgroundWarm: Color = Color(0xFF52231F),
    val backgroundOlive: Color = Color(0xFF11190B),
    val primaryText: Color = Color.White,
    val secondaryText: Color = Color(0xFFD7CADF),
    val mutedText: Color = Color(0xFF8F96A3),
    val border: Color = Color(0xFF59606D),
    val accent: Color = Color(0xFFD8B9FF),
    val onAccent: Color = Color(0xFF28103C),
    val controlSurface: Color = Color(0xFF201921),
    val albumArtPlaceholder: Color = Color(0xFF43536B),
) {
    companion object {
        val Dark = NaviampColors(
            background = Color(0xFF101114),
            primaryText = Color.White,
            secondaryText = Color(0xFFB9BDC7),
            mutedText = Color(0xFF8F96A3),
            border = Color(0xFF59606D),
            accent = Color(0xFF8EA7D8),
            albumArtPlaceholder = Color(0xFF43536B),
        )

        val Light = NaviampColors(
            background = Color(0xFFF8F9FB),
            backgroundWarm = Color(0xFFEAE1DC),
            backgroundOlive = Color(0xFFE9EEE4),
            primaryText = Color(0xFF171A21),
            secondaryText = Color(0xFF4F5663),
            mutedText = Color(0xFF727A86),
            border = Color(0xFFBAC1CC),
            accent = Color(0xFF315D9E),
            onAccent = Color.White,
            controlSurface = Color(0xFFFFFFFF),
            albumArtPlaceholder = Color(0xFFD3DBE8),
        )
    }
}

typealias ConnectionFormState = app.naviamp.domain.settings.ConnectionFormState
typealias PlaybackSettings = app.naviamp.domain.settings.PlaybackSettings

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

data class AndroidTrackRowUi(
    val id: String,
    val title: String,
    val subtitle: String,
    val coverArtUrl: String? = null,
    val meta: String = "",
)

data class SharedMediaItemUi(
    val id: String,
    val title: String,
    val subtitle: String,
    val meta: String = "",
    val coverArtUrl: String? = null,
    val coverArtUrls: List<String> = emptyList(),
)

data class SharedAlbumDetailUi(
    val album: SharedMediaItemUi,
    val tracks: List<AndroidTrackRowUi>,
    val totalDurationLabel: String = "",
)

data class SharedArtistDetailUi(
    val artist: SharedMediaItemUi,
    val albums: List<SharedMediaItemUi>,
    val popularTracks: List<AndroidTrackRowUi> = emptyList(),
)

data class SharedPlaylistDetailUi(
    val playlist: SharedMediaItemUi,
    val tracks: List<AndroidTrackRowUi>,
)

data class SharedHomeUi(
    val recentlyAddedAlbums: List<SharedMediaItemUi> = emptyList(),
    val mixAlbums: List<SharedMediaItemUi> = emptyList(),
    val recentAlbums: List<SharedMediaItemUi> = emptyList(),
    val frequentAlbums: List<SharedMediaItemUi> = emptyList(),
    val randomAlbums: List<SharedMediaItemUi> = emptyList(),
    val playlists: List<SharedMediaItemUi> = emptyList(),
    val recentRadioStreams: List<SharedMediaItemUi> = emptyList(),
    val radioStations: List<SharedMediaItemUi> = emptyList(),
    val stations: List<SharedHomeStationUi> = emptyList(),
    val genreSpotlightTitle: String? = null,
    val genreSpotlightAlbums: List<SharedMediaItemUi> = emptyList(),
    val decadeLabel: String = "Decade",
    val decadeAlbums: List<SharedMediaItemUi> = emptyList(),
) {
    val isEmpty: Boolean
        get() = recentlyAddedAlbums.isEmpty() &&
            mixAlbums.isEmpty() &&
            recentAlbums.isEmpty() &&
            frequentAlbums.isEmpty() &&
            randomAlbums.isEmpty() &&
            playlists.isEmpty() &&
            recentRadioStreams.isEmpty() &&
            radioStations.isEmpty() &&
            stations.isEmpty() &&
            genreSpotlightAlbums.isEmpty() &&
            decadeAlbums.isEmpty()
}

data class SharedHomeStationUi(
    val id: String,
    val title: String,
    val subtitle: String,
)

fun HomeContent.toSharedHomeUi(
    coverArtUrl: (String?) -> String?,
    playlistTracksById: Map<String, List<Track>> = emptyMap(),
): SharedHomeUi =
    SharedHomeUi(
        recentlyAddedAlbums = recentlyAddedAlbums.map { it.toSharedMediaItemUi(coverArtUrl) },
        mixAlbums = mixAlbums.map { it.toSharedMediaItemUi(coverArtUrl) },
        recentAlbums = recentAlbums.map { it.toSharedMediaItemUi(coverArtUrl) },
        frequentAlbums = frequentAlbums.map { it.toSharedMediaItemUi(coverArtUrl) },
        randomAlbums = randomAlbums.map { it.toSharedMediaItemUi(coverArtUrl) },
        playlists = playlists.map { playlist ->
            playlist.toSharedMediaItemUi(
                coverArtUrl = coverArtUrl,
                tracks = playlistTracksById[playlist.id].orEmpty(),
            )
        },
        recentRadioStreams = recentRadioStreams.map {
            SharedMediaItemUi(id = it.id, title = it.label, subtitle = "Radio")
        },
        radioStations = radioStations.map { it.toSharedMediaItemUi() },
        stations = homeStations(this).map {
            SharedHomeStationUi(id = it.id, title = it.title, subtitle = it.subtitle)
        },
        genreSpotlightTitle = genreSpotlight?.name,
        genreSpotlightAlbums = genreSpotlightAlbums.map { it.toSharedMediaItemUi(coverArtUrl) },
        decadeLabel = decadeLabel,
        decadeAlbums = decadeAlbums.map { it.toSharedMediaItemUi(coverArtUrl) },
    )

data class SharedSearchResultsUi(
    val artists: List<SharedMediaItemUi> = emptyList(),
    val albums: List<SharedMediaItemUi> = emptyList(),
    val tracks: List<AndroidTrackRowUi> = emptyList(),
) {
    val isEmpty: Boolean
        get() = artists.isEmpty() && albums.isEmpty() && tracks.isEmpty()
}

data class NowPlayingUi(
    val id: String = "",
    val title: String,
    val subtitle: String,
    val stateLabel: String,
    val coverArtUrl: String? = null,
    val isLive: Boolean = false,
    val albumLine: String = "",
    val audioInfo: String = "",
    val waveform: AudioWaveform? = null,
    val visualizerFrame: PlaybackVisualizerFrame? = null,
    val visualizerAvailable: Boolean = false,
    val visualizerVisible: Boolean = false,
    val positionSeconds: Double? = null,
    val durationSeconds: Double? = null,
    val volumePercent: Int = 100,
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val canPlayPause: Boolean = true,
    val canSeek: Boolean = true,
    val canChangeVolume: Boolean = true,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
    val shuffleEnabled: Boolean = false,
    val shuffleActive: Boolean = false,
    val repeatMode: NaviampRepeatMode = NaviampRepeatMode.Off,
    val canRepeat: Boolean = false,
    val canStartRadio: Boolean = false,
    val canAddToPlaylist: Boolean = false,
    val favoriteActive: Boolean = false,
    val canFavorite: Boolean = false,
    val userRating: Int? = null,
    val canRate: Boolean = false,
    val lyricsAvailable: Boolean = false,
    val lyricsVisible: Boolean = false,
    val lyricsStatus: String? = null,
    val lyricsOffsetMillis: Int = 0,
    val lyricsLines: List<NaviampLyricLineUi> = emptyList(),
    val menuEnabled: Boolean = false,
    val detailSections: List<NaviampDetailSectionUi> = emptyList(),
    val playlistChoices: List<NaviampPlaylistChoiceUi> = emptyList(),
    val useInlinePlaylistPicker: Boolean = true,
    val playlistActionStatus: String? = null,
    val backTo: List<NaviampNowPlayingItemUi> = emptyList(),
    val upNext: List<NaviampNowPlayingItemUi> = emptyList(),
    val related: List<NaviampNowPlayingItemUi> = emptyList(),
    val radioStations: List<NaviampNowPlayingItemUi> = emptyList(),
)

data class NaviampLyricLineUi(
    val startMillis: Long?,
    val text: String,
)

data class NaviampDetailSectionUi(
    val title: String,
    val rows: List<Pair<String, String>>,
)

data class NaviampPlaylistChoiceUi(
    val id: String,
    val name: String,
    val subtitle: String = "",
)

enum class SharedRoute(val label: String, val icon: ImageVector) {
    Home("Home", NaviampIcons.Home),
    Playlists("Playlists", NaviampIcons.Playlist),
    Library("Library", NaviampIcons.Library),
    Search("Search", NaviampIcons.Search),
    Radio("Radio", NaviampIcons.InternetRadio),
    Downloads("Downloads", NaviampIcons.Downloads),
    Settings("Settings", NaviampIcons.Settings),
}

enum class SharedPlaylistSortMode(val label: String) {
    Alphabetical("A-Z"),
    RecentlyPlayed("Recent"),
}

@Composable
fun NaviampSharedAppShell(
    status: String,
    serverVersion: String?,
    connected: Boolean,
    editingConnection: Boolean,
    restoringConnection: Boolean = false,
    connectionForm: ConnectionFormState,
    playbackSettings: PlaybackSettings = PlaybackSettings(),
    supportsReplayGain: Boolean = false,
    supportsGapless: Boolean = true,
    supportsCrossfade: Boolean = false,
    query: String,
    home: SharedHomeUi,
    searchResults: SharedSearchResultsUi,
    libraryArtists: List<SharedMediaItemUi>,
    playlistItems: List<SharedMediaItemUi>,
    recentPlaylistIds: List<String> = emptyList(),
    playlistSortMode: SharedPlaylistSortMode = SharedPlaylistSortMode.Alphabetical,
    radioStationItems: List<SharedMediaItemUi>,
    albumDetail: SharedAlbumDetailUi?,
    artistDetail: SharedArtistDetailUi?,
    playlistDetail: SharedPlaylistDetailUi? = null,
    nowPlaying: NowPlayingUi?,
    nowPlayingOpen: Boolean,
    selectedRoute: SharedRoute,
    onRouteSelected: (SharedRoute) -> Unit,
    onConnectionFormChanged: (ConnectionFormState) -> Unit,
    onConnect: () -> Unit,
    onEditConnection: () -> Unit,
    onCancelEditConnection: () -> Unit,
    onPlaybackSettingsChanged: (PlaybackSettings) -> Unit = {},
    onQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onTrackSelected: (AndroidTrackRowUi) -> Unit,
    onAlbumSelected: (SharedMediaItemUi) -> Unit,
    onMixAlbumSelected: (SharedMediaItemUi) -> Unit = onAlbumSelected,
    onAlbumPlay: (SharedAlbumDetailUi, Boolean) -> Unit = { _, _ -> },
    onAlbumRadio: (SharedAlbumDetailUi) -> Unit = {},
    onAlbumAddToQueue: (SharedAlbumDetailUi) -> Unit = {},
    onArtistSelected: (SharedMediaItemUi) -> Unit,
    onArtistRadio: (SharedArtistDetailUi) -> Unit = {},
    onArtistPopularPlay: (SharedArtistDetailUi) -> Unit = {},
    onArtistPopularRadio: (SharedArtistDetailUi) -> Unit = {},
    onArtistPopularAddToQueue: (SharedArtistDetailUi) -> Unit = {},
    onArtistPopularTrackAddToQueue: (AndroidTrackRowUi) -> Unit = {},
    onPlaylistSelected: (SharedMediaItemUi) -> Unit,
    onPlaylistSortModeChanged: (SharedPlaylistSortMode) -> Unit = {},
    onPlaylistPlay: (SharedMediaItemUi, Boolean) -> Unit = { _, _ -> },
    onPlaylistAddToQueue: (SharedPlaylistDetailUi) -> Unit = {},
    onPlaylistRename: (SharedMediaItemUi, String) -> Unit = { _, _ -> },
    onPlaylistDelete: (SharedMediaItemUi) -> Unit = {},
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
) {
    val colors = NaviampColors.Dark
    val showFullNowPlaying = connected && !editingConnection && !restoringConnection && nowPlayingOpen && nowPlaying != null
    val backgroundGradientColors = if (showFullNowPlaying) {
        rememberPlatformCoverArtGradientColors(nowPlaying.coverArtUrl, colors)
    } else {
        listOf(colors.backgroundWarm, colors.background, colors.backgroundOlive)
    }
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
            Column(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (showFullNowPlaying) {
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
                            playlistItems = playlistItems,
                            recentPlaylistIds = recentPlaylistIds,
                            playlistSortMode = playlistSortMode,
                            radioStationItems = radioStationItems,
                            albumDetail = albumDetail,
                            artistDetail = artistDetail,
                            playlistDetail = playlistDetail,
                            nowPlaying = nowPlaying,
                            nowPlayingOpen = nowPlayingOpen,
                            playbackSettings = playbackSettings,
                            supportsReplayGain = supportsReplayGain,
                            supportsGapless = supportsGapless,
                            supportsCrossfade = supportsCrossfade,
                            onEditConnection = onEditConnection,
                            onPlaybackSettingsChanged = onPlaybackSettingsChanged,
                            onQueryChanged = onQueryChanged,
                            onSearch = onSearch,
                            onTrackSelected = onTrackSelected,
                            onAlbumSelected = onAlbumSelected,
                            onMixAlbumSelected = onMixAlbumSelected,
                            onAlbumPlay = onAlbumPlay,
                            onAlbumRadio = onAlbumRadio,
                            onAlbumAddToQueue = onAlbumAddToQueue,
                            onArtistSelected = onArtistSelected,
                            onArtistRadio = onArtistRadio,
                            onArtistPopularPlay = onArtistPopularPlay,
                            onArtistPopularRadio = onArtistPopularRadio,
                            onArtistPopularAddToQueue = onArtistPopularAddToQueue,
                            onArtistPopularTrackAddToQueue = onArtistPopularTrackAddToQueue,
                            onPlaylistSelected = onPlaylistSelected,
                            onPlaylistSortModeChanged = onPlaylistSortModeChanged,
                            onPlaylistPlay = onPlaylistPlay,
                            onPlaylistAddToQueue = onPlaylistAddToQueue,
                            onPlaylistRename = onPlaylistRename,
                            onPlaylistDelete = onPlaylistDelete,
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
    playlistItems: List<SharedMediaItemUi>,
    recentPlaylistIds: List<String>,
    playlistSortMode: SharedPlaylistSortMode,
    radioStationItems: List<SharedMediaItemUi>,
    albumDetail: SharedAlbumDetailUi?,
    artistDetail: SharedArtistDetailUi?,
    playlistDetail: SharedPlaylistDetailUi?,
    nowPlaying: NowPlayingUi?,
    nowPlayingOpen: Boolean,
    playbackSettings: PlaybackSettings,
    supportsReplayGain: Boolean = false,
    supportsGapless: Boolean = true,
    supportsCrossfade: Boolean = false,
    onEditConnection: () -> Unit,
    onPlaybackSettingsChanged: (PlaybackSettings) -> Unit,
    onQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onTrackSelected: (AndroidTrackRowUi) -> Unit,
    onAlbumSelected: (SharedMediaItemUi) -> Unit,
    onMixAlbumSelected: (SharedMediaItemUi) -> Unit,
    onAlbumPlay: (SharedAlbumDetailUi, Boolean) -> Unit,
    onAlbumRadio: (SharedAlbumDetailUi) -> Unit,
    onAlbumAddToQueue: (SharedAlbumDetailUi) -> Unit,
    onArtistSelected: (SharedMediaItemUi) -> Unit,
    onArtistRadio: (SharedArtistDetailUi) -> Unit,
    onArtistPopularPlay: (SharedArtistDetailUi) -> Unit,
    onArtistPopularRadio: (SharedArtistDetailUi) -> Unit,
    onArtistPopularAddToQueue: (SharedArtistDetailUi) -> Unit,
    onArtistPopularTrackAddToQueue: (AndroidTrackRowUi) -> Unit,
    onPlaylistSelected: (SharedMediaItemUi) -> Unit,
    onPlaylistSortModeChanged: (SharedPlaylistSortMode) -> Unit,
    onPlaylistPlay: (SharedMediaItemUi, Boolean) -> Unit,
    onPlaylistAddToQueue: (SharedPlaylistDetailUi) -> Unit,
    onPlaylistRename: (SharedMediaItemUi, String) -> Unit,
    onPlaylistDelete: (SharedMediaItemUi) -> Unit,
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
) {
    when {
        selectedRoute == SharedRoute.Settings -> SettingsContent(
                            colors = colors,
                            playbackSettings = playbackSettings,
                            supportsReplayGain = supportsReplayGain,
                            supportsGapless = supportsGapless,
                            supportsCrossfade = supportsCrossfade,
            onEditConnection = onEditConnection,
            onPlaybackSettingsChanged = onPlaybackSettingsChanged,
        )
        nowPlayingOpen && nowPlaying != null -> FullNowPlaying(
            nowPlaying = nowPlaying,
            colors = colors,
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
        albumDetail != null -> AlbumDetailContent(
            colors = colors,
            detail = albumDetail,
            onBack = onCloseNowPlaying,
            onPlayAlbum = { onAlbumPlay(albumDetail, false) },
            onShuffleAlbum = { onAlbumPlay(albumDetail, true) },
            onAlbumRadio = { onAlbumRadio(albumDetail) },
            onAlbumAddToQueue = { onAlbumAddToQueue(albumDetail) },
            onTrackSelected = onTrackSelected,
            onTrackAddToQueue = onTrackAddToQueue,
        )
        artistDetail != null -> ArtistDetailContent(
            colors = colors,
            detail = artistDetail,
            onBack = onCloseNowPlaying,
            onArtistRadio = { onArtistRadio(artistDetail) },
            onPopularPlay = { onArtistPopularPlay(artistDetail) },
            onPopularRadio = { onArtistPopularRadio(artistDetail) },
            onPopularAddToQueue = { onArtistPopularAddToQueue(artistDetail) },
            onPopularTrackSelected = onTrackSelected,
            onPopularTrackAddToQueue = onArtistPopularTrackAddToQueue,
            onAlbumSelected = onAlbumSelected,
        )
        playlistDetail != null -> PlaylistDetailContent(
            colors = colors,
            detail = playlistDetail,
            onBack = onPlaylistBack,
            onPlayPlaylist = { onPlaylistPlay(playlistDetail.playlist, false) },
            onShufflePlaylist = { onPlaylistPlay(playlistDetail.playlist, true) },
            onAddPlaylistToQueue = { onPlaylistAddToQueue(playlistDetail) },
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
                onSortModeChanged = onPlaylistSortModeChanged,
                onPlaylistSelected = onPlaylistSelected,
                onPlaylistPlay = onPlaylistPlay,
                onPlaylistRename = onPlaylistRename,
                onPlaylistDelete = onPlaylistDelete,
            )
            SharedRoute.Library -> MediaListContent(colors, "Library", libraryArtists, "No library artists found.", onArtistSelected)
            SharedRoute.Search -> SearchContent(colors, query, searchResults, onQueryChanged, onSearch, onTrackSelected, onTrackAddToQueue, onAlbumSelected, onArtistSelected)
            SharedRoute.Radio -> MediaListContent(colors, "Internet Radio", radioStationItems, "No stations found.", onRadioStationSelected)
            SharedRoute.Settings -> Unit
            SharedRoute.Downloads -> PlaceholderRoute(colors, selectedRoute)
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
    onTrackSelected: (AndroidTrackRowUi) -> Unit,
    onTrackAddToQueue: (AndroidTrackRowUi) -> Unit,
    onAlbumSelected: (SharedMediaItemUi) -> Unit,
    onArtistSelected: (SharedMediaItemUi) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Search", color = colors.primaryText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        NaviampTextField(query, onQueryChanged, "Search tracks", colors)
        PrimaryButton("Search", colors, onSearch, enabled = query.isNotBlank())
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
private fun PlaylistsContent(
    colors: NaviampColors,
    playlists: List<SharedMediaItemUi>,
    recentPlaylistIds: List<String>,
    sortMode: SharedPlaylistSortMode,
    onSortModeChanged: (SharedPlaylistSortMode) -> Unit,
    onPlaylistSelected: (SharedMediaItemUi) -> Unit,
    onPlaylistPlay: (SharedMediaItemUi, Boolean) -> Unit,
    onPlaylistRename: (SharedMediaItemUi, String) -> Unit,
    onPlaylistDelete: (SharedMediaItemUi) -> Unit,
) {
    var playlistToRename by remember { mutableStateOf<SharedMediaItemUi?>(null) }
    var playlistToDelete by remember { mutableStateOf<SharedMediaItemUi?>(null) }
    val sortedPlaylists = when (sortMode) {
        SharedPlaylistSortMode.Alphabetical -> playlists.sortedBy { it.title.lowercase() }
        SharedPlaylistSortMode.RecentlyPlayed -> playlists.sortedWith(
            compareBy<SharedMediaItemUi> {
                val index = recentPlaylistIds.indexOf(it.id)
                if (index == -1) Int.MAX_VALUE else index
            }.thenBy { it.title.lowercase() },
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Playlists", color = colors.primaryText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SharedPlaylistSortMode.entries.forEach { mode ->
                    PlaylistSortIconButton(
                        mode = mode,
                        selected = sortMode == mode,
                        colors = colors,
                        onClick = { onSortModeChanged(mode) },
                    )
                }
            }
        }
        if (sortedPlaylists.isEmpty()) {
            Text("No playlists yet.", color = colors.secondaryText, fontSize = 12.sp)
        }
        sortedPlaylists.forEach { playlist ->
            PlaylistListRow(
                playlist = playlist,
                colors = colors,
                onClick = { onPlaylistSelected(playlist) },
                onPlay = { onPlaylistPlay(playlist, false) },
                onShuffle = { onPlaylistPlay(playlist, true) },
                onRename = { playlistToRename = playlist },
                onDelete = { playlistToDelete = playlist },
            )
        }
    }

    playlistToRename?.let { playlist ->
        RenamePlaylistDialog(
            playlist = playlist,
            colors = colors,
            onDismiss = { playlistToRename = null },
            onConfirm = { name ->
                playlistToRename = null
                onPlaylistRename(playlist, name)
            },
        )
    }
    playlistToDelete?.let { playlist ->
        DeletePlaylistDialog(
            playlist = playlist,
            colors = colors,
            onDismiss = { playlistToDelete = null },
            onConfirm = {
                playlistToDelete = null
                onPlaylistDelete(playlist)
            },
        )
    }
}

@Composable
private fun PlaylistSortIconButton(
    mode: SharedPlaylistSortMode,
    selected: Boolean,
    colors: NaviampColors,
    onClick: () -> Unit,
) {
    val icon = when (mode) {
        SharedPlaylistSortMode.Alphabetical -> NaviampIcons.Alphabetical
        SharedPlaylistSortMode.RecentlyPlayed -> NaviampIcons.Clock
    }
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(19.dp))
            .background(if (selected) colors.accent else colors.controlSurface.copy(alpha = 0.72f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = mode.label,
            tint = if (selected) colors.onAccent else colors.secondaryText,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun PlaylistListRow(
    playlist: SharedMediaItemUi,
    colors: NaviampColors,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(5.dp))
            .background(Color.Black.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PlaylistCoverFromUrls(
            colors = colors,
            covers = playlist.coverArtUrls.ifEmpty { listOfNotNull(playlist.coverArtUrl) },
            size = 38.dp,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(playlist.title, color = colors.primaryText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(playlist.subtitle, color = colors.secondaryText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        MiniPlayerIconButton(colors, true, NaviampTransportIcons.Play, "Play playlist", onPlay)
        MiniPlayerIconButton(colors, playlist.meta != "1 track", NaviampTransportIcons.Shuffle, "Shuffle playlist", onShuffle)
        NaviampRowOverflowMenu(
            colors = colors,
            items = playlistRowActions(canRename = true, canDelete = true).mapNotNull { action ->
                when (action.action) {
                    NaviampAction.RenamePlaylist -> NaviampRowMenuItem(action.label, action.icon, onRename, action.enabled)
                    NaviampAction.DeletePlaylist -> NaviampRowMenuItem(action.label, action.icon, onDelete, action.enabled)
                    else -> null
                }
            },
        )
    }
}

@Composable
private fun PlaylistDetailContent(
    colors: NaviampColors,
    detail: SharedPlaylistDetailUi,
    onBack: () -> Unit,
    onPlayPlaylist: () -> Unit,
    onShufflePlaylist: () -> Unit,
    onAddPlaylistToQueue: () -> Unit,
    onRenamePlaylist: (SharedMediaItemUi, String) -> Unit,
    onDeletePlaylist: (SharedMediaItemUi) -> Unit,
    onTrackSelected: (AndroidTrackRowUi) -> Unit,
    onTrackAddToQueue: (AndroidTrackRowUi) -> Unit,
) {
    var renameOpen by remember { mutableStateOf(false) }
    var deleteOpen by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                Icon(NaviampIcons.Back, contentDescription = "Back", tint = colors.primaryText, modifier = Modifier.size(18.dp))
            }
            Text(detail.playlist.title, color = colors.primaryText, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PlaylistCover(colors, detail.tracks, 96.dp)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                Text(detail.playlist.subtitle, color = colors.secondaryText, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    MiniPlayerIconButton(colors, detail.tracks.isNotEmpty(), NaviampTransportIcons.Play, "Play playlist", onPlayPlaylist)
                    MiniPlayerIconButton(colors, detail.tracks.size > 1, NaviampTransportIcons.Shuffle, "Shuffle playlist", onShufflePlaylist)
                    MiniPlayerIconButton(colors, detail.tracks.isNotEmpty(), NaviampIcons.Queue, "Add playlist to queue", onAddPlaylistToQueue)
                    MiniPlayerIconButton(colors, true, NaviampIcons.Edit, "Rename playlist", { renameOpen = true })
                    MiniPlayerIconButton(colors, true, NaviampIcons.Trash, "Delete playlist", { deleteOpen = true })
                }
            }
        }
        detail.tracks.forEach { track ->
            TrackRow(track, colors, onTrackSelected, onAddToQueue = onTrackAddToQueue)
        }
    }

    if (renameOpen) {
        RenamePlaylistDialog(
            playlist = detail.playlist,
            colors = colors,
            onDismiss = { renameOpen = false },
            onConfirm = { name ->
                renameOpen = false
                onRenamePlaylist(detail.playlist, name)
            },
        )
    }
    if (deleteOpen) {
        DeletePlaylistDialog(
            playlist = detail.playlist,
            colors = colors,
            onDismiss = { deleteOpen = false },
            onConfirm = {
                deleteOpen = false
                onDeletePlaylist(detail.playlist)
            },
        )
    }
}

@Composable
private fun PlaylistCover(colors: NaviampColors, tracks: List<AndroidTrackRowUi>, size: Dp) {
    PlaylistCoverFromUrls(colors, tracks.mapNotNull { it.coverArtUrl }.distinct().take(4), size)
}

@Composable
private fun PlaylistCoverFromUrls(colors: NaviampColors, covers: List<String>, size: Dp) {
    val visibleCovers = covers.distinct().take(4)
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(4.dp)),
    ) {
        when (visibleCovers.size) {
            0 -> PlatformCoverArt(null, colors, size, 4.dp)
            1 -> PlatformCoverArt(visibleCovers[0], colors, size, 4.dp)
            else -> {
                val cell = size / 2
                Column {
                    Row {
                        PlatformCoverArt(visibleCovers[0], colors, cell, 0.dp)
                        PlatformCoverArt(visibleCovers[1], colors, cell, 0.dp)
                    }
                    Row {
                        PlatformCoverArt(visibleCovers.getOrElse(2) { visibleCovers[0] }, colors, cell, 0.dp)
                        PlatformCoverArt(
                            visibleCovers.getOrElse(3) { visibleCovers.getOrElse(2) { visibleCovers[1] } },
                            colors,
                            cell,
                            0.dp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RenamePlaylistDialog(
    playlist: SharedMediaItemUi,
    colors: NaviampColors,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(playlist.id) { mutableStateOf(playlist.title) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename playlist") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Playlist name") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        containerColor = colors.controlSurface,
        titleContentColor = colors.primaryText,
        textContentColor = colors.secondaryText,
    )
}

@Composable
private fun DeletePlaylistDialog(
    playlist: SharedMediaItemUi,
    colors: NaviampColors,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete playlist") },
        text = { Text("Delete ${playlist.title}? This removes the server playlist.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        containerColor = colors.controlSurface,
        titleContentColor = colors.primaryText,
        textContentColor = colors.secondaryText,
    )
}

@Composable
private fun MediaListContent(
    colors: NaviampColors,
    title: String,
    items: List<SharedMediaItemUi>,
    emptyText: String,
    onItemSelected: ((SharedMediaItemUi) -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, color = colors.primaryText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        if (items.isEmpty()) {
            Text(emptyText, color = colors.secondaryText, fontSize = 13.sp)
        }
        items.forEach { item ->
            SharedMediaRow(item = item, colors = colors, onClick = onItemSelected?.let { { it(item) } })
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
    onAlbumAddToQueue: () -> Unit,
    onTrackSelected: (AndroidTrackRowUi) -> Unit,
    onTrackAddToQueue: (AndroidTrackRowUi) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    MiniPlayerIconButton(colors, detail.tracks.isNotEmpty(), NaviampIcons.Queue, "Add album to queue", onAlbumAddToQueue)
                }
            }
        }
        detail.tracks.forEachIndexed { index, track ->
            TrackRow(track.copy(meta = (index + 1).toString()), colors, onTrackSelected, onAddToQueue = onTrackAddToQueue)
        }
    }
}

@Composable
private fun ArtistDetailContent(
    colors: NaviampColors,
    detail: SharedArtistDetailUi,
    onBack: () -> Unit,
    onArtistRadio: () -> Unit,
    onPopularPlay: () -> Unit,
    onPopularRadio: () -> Unit,
    onPopularAddToQueue: () -> Unit,
    onPopularTrackSelected: (AndroidTrackRowUi) -> Unit,
    onPopularTrackAddToQueue: (AndroidTrackRowUi) -> Unit,
    onAlbumSelected: (SharedMediaItemUi) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(NaviampIcons.Back, contentDescription = "Back", tint = colors.primaryText)
            }
            Column {
                Text(detail.artist.title, color = colors.primaryText, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${detail.albums.size} albums", color = colors.secondaryText, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    MiniPlayerIconButton(colors, detail.albums.isNotEmpty(), NaviampTransportIcons.Radio, "Start artist radio", onArtistRadio)
                    MiniPlayerIconButton(colors, detail.popularTracks.isNotEmpty(), NaviampTransportIcons.Play, "Play popular tracks", onPopularPlay)
                    MiniPlayerIconButton(colors, detail.popularTracks.isNotEmpty(), NaviampIcons.Queue, "Add popular tracks to queue", onPopularAddToQueue)
                }
            }
        }
        if (detail.popularTracks.isNotEmpty()) {
            Text(
                "Popular Tracks".uppercase(),
                color = colors.primaryText,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    MiniPlayerIconButton(colors, true, NaviampTransportIcons.Play, "Play popular tracks", onPopularPlay)
                    MiniPlayerIconButton(colors, true, NaviampTransportIcons.Radio, "Start popular tracks radio", onPopularRadio)
                    MiniPlayerIconButton(colors, true, NaviampIcons.Queue, "Add popular tracks to queue", onPopularAddToQueue)
                }
                detail.popularTracks.forEachIndexed { index, track ->
                    TrackRow(
                        track.copy(meta = (index + 1).toString()),
                        colors,
                        onPopularTrackSelected,
                        onAddToQueue = onPopularTrackAddToQueue,
                    )
                }
            }
        }
        MediaListContent(colors, "Albums", detail.albums, "No albums found.", onAlbumSelected)
    }
}

@Composable
private fun FullNowPlaying(
    nowPlaying: NowPlayingUi,
    colors: NaviampColors,
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
    supportsReplayGain: Boolean,
    supportsGapless: Boolean,
    supportsCrossfade: Boolean,
    onEditConnection: () -> Unit,
    onPlaybackSettingsChanged: (PlaybackSettings) -> Unit,
) {
    NaviampSharedSettingsContent(
        colors = colors,
        playbackSettings = playbackSettings,
        supportsReplayGain = supportsReplayGain,
        supportsGapless = supportsGapless,
        supportsCrossfade = supportsCrossfade,
        onEditConnection = onEditConnection,
        onPlaybackSettingsChanged = onPlaybackSettingsChanged,
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

@Composable
private fun MiniPlayerIconButton(
    colors: NaviampColors,
    enabled: Boolean,
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    IconButton(
        enabled = enabled,
        onClick = onClick,
        modifier = Modifier.size(34.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) colors.primaryText else colors.mutedText,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun TrackRow(
    track: AndroidTrackRowUi,
    colors: NaviampColors,
    onTrackSelected: (AndroidTrackRowUi) -> Unit,
    onAddToQueue: ((AndroidTrackRowUi) -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTrackSelected(track) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (track.meta.isNotBlank()) {
            Text(track.meta, color = colors.mutedText, fontSize = 11.sp, modifier = Modifier.width(20.dp))
        }
        PlatformCoverArt(track.coverArtUrl, colors, 34.dp, 4.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(track.title, color = colors.primaryText, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.subtitle, color = colors.secondaryText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        onAddToQueue?.let { addToQueue ->
            NaviampRowOverflowMenu(
                colors = colors,
                items = listOf(
                    NaviampRowMenuItem(
                        label = NaviampAction.AddToQueue.label,
                        icon = NaviampAction.AddToQueue.icon,
                        onClick = { addToQueue(track) },
                    ),
                ),
            )
        }
    }
}

@Composable
private fun MixCard(
    album: SharedMediaItemUi,
    colors: NaviampColors,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(154.dp)
            .height(120.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
    ) {
        PlatformCoverArt(album.coverArtUrl, colors, 154.dp, 6.dp)
        Column(
            verticalArrangement = Arrangement.Bottom,
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.68f),
                    ),
                )
                .padding(8.dp),
        ) {
            Text(
                "${album.subtitle} Mix",
                color = colors.primaryText,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                album.title,
                color = colors.secondaryText,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun HomeStationSection(
    stations: List<SharedHomeStationUi>,
    colors: NaviampColors,
    onStationSelected: (SharedHomeStationUi) -> Unit,
) {
    if (stations.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionHeader("STATIONS", colors)
        stations.forEach { station ->
            StationRow(
                title = station.title,
                subtitle = station.subtitle,
                colors = colors,
                onClick = { onStationSelected(station) },
            )
        }
    }
}

@Composable
private fun StationRow(
    title: String,
    subtitle: String,
    colors: NaviampColors,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(5.dp))
            .background(Color.Black.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(colors.controlSurface.copy(alpha = 0.5f)),
        ) {
            Icon(NaviampIcons.InternetRadio, contentDescription = null, tint = colors.primaryText, modifier = Modifier.size(19.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = colors.primaryText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = colors.secondaryText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(">", color = colors.mutedText, fontSize = 16.sp)
    }
}

@Composable
private fun HomeSection(
    title: String,
    items: List<SharedMediaItemUi>,
    colors: NaviampColors,
    onItemSelected: ((SharedMediaItemUi) -> Unit)? = null,
    stationStyle: Boolean = false,
) {
    if (items.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionHeader(title.uppercase(), colors)
        items.take(6).forEach { item ->
            if (stationStyle) {
                StationRow(
                    title = item.title,
                    subtitle = item.subtitle,
                    colors = colors,
                    onClick = { onItemSelected?.invoke(item) },
                )
            } else {
                SharedMediaRow(
                    item = item,
                    colors = colors,
                    onClick = onItemSelected?.let { { it(item) } },
                )
            }
        }
    }
}

@Composable
private fun MediaSection(
    title: String,
    items: List<SharedMediaItemUi>,
    colors: NaviampColors,
    onItemSelected: ((SharedMediaItemUi) -> Unit)? = null,
) {
    if (items.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        SectionHeader(title.uppercase(), colors)
        items.forEach { item ->
            SharedMediaRow(item, colors, onClick = onItemSelected?.let { { it(item) } })
        }
    }
}

@Composable
private fun SharedMediaRow(
    item: SharedMediaItemUi,
    colors: NaviampColors,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(5.dp))
            .background(Color.Black.copy(alpha = 0.12f))
            .let { modifier ->
                if (onClick != null) modifier.clickable(onClick = onClick) else modifier
            }
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PlatformCoverArt(item.coverArtUrl, colors, 34.dp, 4.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.title, color = colors.primaryText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.subtitle, color = colors.secondaryText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (item.meta.isNotBlank()) {
            Text(item.meta, color = colors.mutedText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun NaviampTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    colors: NaviampColors,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    isPassword: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = colors.secondaryText) },
        singleLine = true,
        enabled = enabled,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = modifier,
    )
}

@Composable
private fun PrimaryButton(label: String, colors: NaviampColors, onClick: () -> Unit, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.accent,
            contentColor = colors.onAccent,
            disabledContainerColor = colors.controlSurface,
            disabledContentColor = colors.mutedText,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label)
    }
}

@Composable
private fun SmallPill(label: String, colors: NaviampColors, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
        modifier = Modifier.height(40.dp),
    ) {
        Text(label)
    }
}

@Composable
fun SharedTransportControls(
    colors: NaviampColors,
    isPlaying: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    shuffleEnabled: Boolean = false,
    repeatEnabled: Boolean = false,
    onShuffle: (() -> Unit)? = null,
    onPrevious: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
    onRepeat: (() -> Unit)? = null,
) {
    val transportSize = if (compact) 38.dp else 46.dp
    val sideSize = if (compact) 32.dp else 36.dp
    Row(
        horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        TransportIconButton(
            icon = NaviampTransportIcons.Shuffle,
            contentDescription = "Shuffle",
            colors = colors,
            onClick = onShuffle ?: {},
            enabled = onShuffle != null,
            selected = shuffleEnabled,
            size = sideSize,
        )
        TransportIconButton(
            icon = NaviampTransportIcons.Previous,
            contentDescription = "Previous",
            colors = colors,
            onClick = onPrevious ?: {},
            enabled = onPrevious != null,
            size = sideSize,
        )
        TransportIconButton(
            icon = if (isPlaying) NaviampTransportIcons.Pause else NaviampTransportIcons.Play,
            contentDescription = if (isPlaying) "Pause" else "Play",
            colors = colors,
            onClick = if (isPlaying) onPause else onResume,
            size = transportSize,
        )
        TransportIconButton(
            icon = NaviampTransportIcons.Stop,
            contentDescription = "Stop",
            colors = colors,
            onClick = onStop,
            size = sideSize,
        )
        TransportIconButton(
            icon = NaviampTransportIcons.Next,
            contentDescription = "Next",
            colors = colors,
            onClick = onNext ?: {},
            enabled = onNext != null,
            size = sideSize,
        )
        TransportIconButton(
            icon = NaviampTransportIcons.Repeat,
            contentDescription = "Repeat",
            colors = colors,
            onClick = onRepeat ?: {},
            enabled = onRepeat != null,
            selected = repeatEnabled,
            size = sideSize,
        )
    }
}

@Composable
private fun TransportIconButton(
    icon: ImageVector,
    contentDescription: String,
    colors: NaviampColors,
    onClick: () -> Unit,
    enabled: Boolean = true,
    selected: Boolean = false,
    size: Dp = 46.dp,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size / 2))
            .background(
                when {
                    !enabled -> colors.controlSurface.copy(alpha = 0.55f)
                    selected -> colors.primaryText
                    else -> colors.accent
                },
            ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = when {
                !enabled -> colors.mutedText
                selected -> colors.background
                else -> colors.onAccent
            },
            modifier = Modifier.size(size * 0.48f),
        )
    }
}

@Composable
private fun SectionHeader(text: String, colors: NaviampColors) {
    Text(text, color = colors.primaryText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
}

@Composable
private fun PlaceholderTile(text: String, colors: NaviampColors) {
    Text(
        text,
        color = colors.secondaryText,
        fontSize = 13.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.2f))
            .padding(12.dp),
    )
}

@Composable
fun SharedBottomNavigationBar(
    colors: NaviampColors,
    selectedRoute: SharedRoute,
    onRouteSelected: (SharedRoute) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.28f))
            .padding(vertical = 2.dp),
    ) {
        SharedRoute.entries.forEach { route ->
            IconButton(onClick = { onRouteSelected(route) }, modifier = Modifier.size(42.dp)) {
                Icon(
                    route.icon,
                    contentDescription = route.label,
                    tint = if (route == selectedRoute) colors.primaryText else colors.mutedText,
                    modifier = Modifier.size(21.dp),
                )
            }
        }
    }
}

@Composable
fun NaviampDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset.Zero,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        offset = offset,
        shape = RoundedCornerShape(4.dp),
        containerColor = MenuBackground,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
        modifier = modifier.widthIn(min = 220.dp),
        content = content,
    )
}

@Composable
fun NaviampDropdownMenuItem(
    label: String,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Text(
                label,
                fontSize = 13.sp,
            )
        },
        leadingIcon = icon?.let {
            {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = if (enabled) MenuText else MenuText.copy(alpha = 0.42f),
                    modifier = Modifier.size(17.dp),
                )
            }
        },
        enabled = enabled,
        colors = MenuDefaults.itemColors(
            textColor = MenuText,
            disabledTextColor = MenuText.copy(alpha = 0.42f),
        ),
        onClick = onClick,
    )
}

data class NaviampRowMenuItem(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
)

@Composable
fun NaviampRowOverflowMenu(
    colors: NaviampColors,
    items: List<NaviampRowMenuItem>,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return

    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(28.dp),
        ) {
            Text("⋮", color = colors.mutedText, fontSize = 15.sp)
        }
        NaviampDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            items.forEach { item ->
                NaviampDropdownMenuItem(
                    label = item.label,
                    icon = item.icon,
                    enabled = item.enabled,
                    onClick = {
                        expanded = false
                        item.onClick()
                    },
                )
            }
        }
    }
}

private val MenuBackground = Color(0xFF292C36)
private val MenuText = Color(0xFFE7E8EF)

object NaviampIcons {
    private val IconSize = 24.dp
    private const val Viewport = 24f

    val Home = icon("Home") {
        moveTo(4f, 11f)
        lineTo(12f, 4f)
        lineTo(20f, 11f)
        moveTo(6.5f, 10f)
        lineTo(6.5f, 20f)
        lineTo(10f, 20f)
        lineTo(10f, 15f)
        lineTo(14f, 15f)
        lineTo(14f, 20f)
        lineTo(17.5f, 20f)
        lineTo(17.5f, 10f)
    }
    val Player = filledIcon("Player") {
        moveTo(9f, 7f)
        lineTo(18f, 12f)
        lineTo(9f, 17f)
        close()
    }
    val Pause = filledIcon("Pause") {
        moveTo(7f, 5f)
        lineTo(10f, 5f)
        lineTo(10f, 19f)
        lineTo(7f, 19f)
        close()
        moveTo(14f, 5f)
        lineTo(17f, 5f)
        lineTo(17f, 19f)
        lineTo(14f, 19f)
        close()
    }
    val Stop = filledIcon("Stop") {
        moveTo(7f, 7f)
        lineTo(17f, 7f)
        lineTo(17f, 17f)
        lineTo(7f, 17f)
        close()
    }
    val Playlist = icon("Playlist") {
        moveTo(5f, 6.5f)
        lineTo(14.5f, 6.5f)
        moveTo(5f, 11.5f)
        lineTo(14.5f, 11.5f)
        moveTo(5f, 16.5f)
        lineTo(11.5f, 16.5f)
        moveTo(17.5f, 13.5f)
        lineTo(17.5f, 20f)
        moveTo(14.3f, 16.7f)
        lineTo(20.7f, 16.7f)
    }
    val Queue = icon("Queue") {
        moveTo(5f, 6.5f)
        lineTo(15.5f, 6.5f)
        moveTo(5f, 11.5f)
        lineTo(13f, 11.5f)
        moveTo(5f, 16.5f)
        lineTo(11.5f, 16.5f)
        moveTo(15.5f, 13.5f)
        lineTo(19.5f, 16.5f)
        lineTo(15.5f, 19.5f)
        moveTo(14.5f, 16.5f)
        lineTo(19.2f, 16.5f)
    }
    val Library = icon("Library") {
        moveTo(8f, 5f)
        lineTo(8f, 16.2f)
        curveTo(7.2f, 15.8f, 5.9f, 15.8f, 5f, 16.5f)
        curveTo(4f, 17.3f, 4.2f, 18.8f, 5.4f, 19.3f)
        curveTo(6.8f, 19.9f, 8f, 19f, 8f, 17.4f)
        moveTo(8f, 7.2f)
        lineTo(18f, 5.4f)
        lineTo(18f, 14.8f)
        curveTo(17.2f, 14.4f, 15.9f, 14.4f, 15f, 15.1f)
        curveTo(14f, 15.9f, 14.2f, 17.4f, 15.4f, 17.9f)
        curveTo(16.8f, 18.5f, 18f, 17.6f, 18f, 16f)
    }
    val Search = icon("Search") {
        moveTo(10.8f, 5f)
        curveTo(7.6f, 5f, 5f, 7.6f, 5f, 10.8f)
        curveTo(5f, 14f, 7.6f, 16.6f, 10.8f, 16.6f)
        curveTo(14f, 16.6f, 16.6f, 14f, 16.6f, 10.8f)
        curveTo(16.6f, 7.6f, 14f, 5f, 10.8f, 5f)
        close()
        moveTo(15.2f, 15.2f)
        lineTo(20f, 20f)
    }
    val InternetRadio = icon("InternetRadio") {
        moveTo(6f, 10f)
        lineTo(18.5f, 7.5f)
        moveTo(6f, 10f)
        lineTo(6f, 18.5f)
        lineTo(19f, 18.5f)
        lineTo(19f, 10f)
        close()
        moveTo(9f, 13.2f)
        lineTo(13f, 13.2f)
        moveTo(9f, 15.8f)
        lineTo(11.5f, 15.8f)
        moveTo(16f, 13.8f)
        curveTo(15.1f, 13.8f, 14.4f, 14.5f, 14.4f, 15.4f)
        curveTo(14.4f, 16.3f, 15.1f, 17f, 16f, 17f)
        curveTo(16.9f, 17f, 17.6f, 16.3f, 17.6f, 15.4f)
        curveTo(17.6f, 14.5f, 16.9f, 13.8f, 16f, 13.8f)
        close()
    }
    val Downloads = icon("Downloads") {
        moveTo(12f, 4f)
        lineTo(12f, 15f)
        moveTo(7.5f, 10.8f)
        lineTo(12f, 15.3f)
        lineTo(16.5f, 10.8f)
        moveTo(5f, 20f)
        lineTo(19f, 20f)
    }
    val Settings = icon("Settings") {
        moveTo(12f, 8.3f)
        curveTo(10f, 8.3f, 8.3f, 10f, 8.3f, 12f)
        curveTo(8.3f, 14f, 10f, 15.7f, 12f, 15.7f)
        curveTo(14f, 15.7f, 15.7f, 14f, 15.7f, 12f)
        curveTo(15.7f, 10f, 14f, 8.3f, 12f, 8.3f)
        close()
        moveTo(10.5f, 3.2f)
        lineTo(13.5f, 3.2f)
        lineTo(14f, 5.5f)
        curveTo(14.6f, 5.7f, 15.1f, 5.9f, 15.6f, 6.3f)
        lineTo(17.8f, 5.4f)
        lineTo(18.9f, 6.5f)
        lineTo(18f, 8.7f)
        curveTo(18.3f, 9.2f, 18.6f, 9.7f, 18.8f, 10.3f)
        lineTo(21f, 11.2f)
        lineTo(21f, 12.8f)
        lineTo(18.8f, 13.7f)
        curveTo(18.6f, 14.3f, 18.3f, 14.8f, 18f, 15.3f)
        lineTo(18.9f, 17.5f)
        lineTo(17.8f, 18.6f)
        lineTo(15.6f, 17.7f)
        curveTo(15.1f, 18.1f, 14.6f, 18.3f, 14f, 18.5f)
        lineTo(13.5f, 20.8f)
        lineTo(10.5f, 20.8f)
        lineTo(10f, 18.5f)
        curveTo(9.4f, 18.3f, 8.9f, 18.1f, 8.4f, 17.7f)
        lineTo(6.2f, 18.6f)
        lineTo(5.1f, 17.5f)
        lineTo(6f, 15.3f)
        curveTo(5.7f, 14.8f, 5.4f, 14.3f, 5.2f, 13.7f)
        lineTo(3f, 12.8f)
        lineTo(3f, 11.2f)
        lineTo(5.2f, 10.3f)
        curveTo(5.4f, 9.7f, 5.7f, 9.2f, 6f, 8.7f)
        lineTo(5.1f, 6.5f)
        lineTo(6.2f, 5.4f)
        lineTo(8.4f, 6.3f)
        curveTo(8.9f, 5.9f, 9.4f, 5.7f, 10f, 5.5f)
        lineTo(10.5f, 3.2f)
        close()
    }
    val ChevronRight = icon("ChevronRight") {
        moveTo(9f, 6f)
        lineTo(15f, 12f)
        lineTo(9f, 18f)
    }
    val ChevronDown = icon("ChevronDown") {
        moveTo(6f, 9f)
        lineTo(12f, 15f)
        lineTo(18f, 9f)
    }
    val Back = icon("Back") {
        moveTo(15f, 5f)
        lineTo(8f, 12f)
        lineTo(15f, 19f)
    }
    val Trash = icon("Trash") {
        moveTo(5f, 7f)
        lineTo(19f, 7f)
        moveTo(10f, 11f)
        lineTo(10f, 17f)
        moveTo(14f, 11f)
        lineTo(14f, 17f)
        moveTo(8f, 7f)
        lineTo(8.8f, 20f)
        lineTo(15.2f, 20f)
        lineTo(16f, 7f)
        moveTo(9.5f, 7f)
        lineTo(10.2f, 4.5f)
        lineTo(13.8f, 4.5f)
        lineTo(14.5f, 7f)
    }
    val Edit = icon("Edit") {
        moveTo(5f, 19f)
        lineTo(9f, 18.2f)
        lineTo(18.2f, 9f)
        curveTo(19f, 8.2f, 19f, 6.9f, 18.2f, 6.1f)
        lineTo(17.9f, 5.8f)
        curveTo(17.1f, 5f, 15.8f, 5f, 15f, 5.8f)
        lineTo(5.8f, 15f)
        lineTo(5f, 19f)
        close()
        moveTo(13.8f, 7f)
        lineTo(17f, 10.2f)
    }
    val Alphabetical = icon("Alphabetical") {
        moveTo(5f, 18f)
        lineTo(8f, 6f)
        lineTo(11f, 18f)
        moveTo(6.1f, 14f)
        lineTo(9.9f, 14f)
        moveTo(14f, 7f)
        lineTo(19f, 7f)
        lineTo(14f, 17f)
        lineTo(19f, 17f)
    }
    val Clock = icon("Clock") {
        moveTo(12f, 4f)
        curveTo(7.6f, 4f, 4f, 7.6f, 4f, 12f)
        curveTo(4f, 16.4f, 7.6f, 20f, 12f, 20f)
        curveTo(16.4f, 20f, 20f, 16.4f, 20f, 12f)
        curveTo(20f, 7.6f, 16.4f, 4f, 12f, 4f)
        close()
        moveTo(12f, 8f)
        lineTo(12f, 12.4f)
        lineTo(15f, 14.2f)
    }
    val Info = icon("Info") {
        moveTo(12f, 10.5f)
        lineTo(12f, 17f)
        moveTo(12f, 7f)
        lineTo(12.02f, 7f)
        moveTo(12f, 3.5f)
        curveTo(7.3f, 3.5f, 3.5f, 7.3f, 3.5f, 12f)
        curveTo(3.5f, 16.7f, 7.3f, 20.5f, 12f, 20.5f)
        curveTo(16.7f, 20.5f, 20.5f, 16.7f, 20.5f, 12f)
        curveTo(20.5f, 7.3f, 16.7f, 3.5f, 12f, 3.5f)
        close()
    }
    val Album = icon("Album") {
        moveTo(12f, 4f)
        curveTo(7.6f, 4f, 4f, 7.6f, 4f, 12f)
        curveTo(4f, 16.4f, 7.6f, 20f, 12f, 20f)
        curveTo(16.4f, 20f, 20f, 16.4f, 20f, 12f)
        curveTo(20f, 7.6f, 16.4f, 4f, 12f, 4f)
        close()
        moveTo(12f, 9.2f)
        curveTo(10.5f, 9.2f, 9.2f, 10.5f, 9.2f, 12f)
        curveTo(9.2f, 13.5f, 10.5f, 14.8f, 12f, 14.8f)
        curveTo(13.5f, 14.8f, 14.8f, 13.5f, 14.8f, 12f)
        curveTo(14.8f, 10.5f, 13.5f, 9.2f, 12f, 9.2f)
        close()
    }
    val Artist = icon("Artist") {
        moveTo(12f, 5f)
        curveTo(10f, 5f, 8.4f, 6.6f, 8.4f, 8.6f)
        curveTo(8.4f, 10.6f, 10f, 12.2f, 12f, 12.2f)
        curveTo(14f, 12.2f, 15.6f, 10.6f, 15.6f, 8.6f)
        curveTo(15.6f, 6.6f, 14f, 5f, 12f, 5f)
        close()
        moveTo(5.5f, 20f)
        curveTo(6.4f, 16.8f, 8.8f, 15f, 12f, 15f)
        curveTo(15.2f, 15f, 17.6f, 16.8f, 18.5f, 20f)
    }

    private fun icon(name: String, block: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = IconSize,
            defaultHeight = IconSize,
            viewportWidth = Viewport,
            viewportHeight = Viewport,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                block()
            }
        }.build()

    private fun filledIcon(name: String, block: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = IconSize,
            defaultHeight = IconSize,
            viewportWidth = Viewport,
            viewportHeight = Viewport,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                block()
            }
        }.build()
}
