package app.naviamp.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import app.naviamp.domain.playback.PlaybackVisualizerFrame
import app.naviamp.domain.waveform.AudioWaveform

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

enum class NaviampVisualizer(val label: String) {
    AlbumArtReactive("Album art"),
    AudioSphere("Audio sphere"),
    AudioTunnel("Audio tunnel"),
    FluidGradient("Fluid gradient"),
    FrequencyTerrain("Frequency terrain"),
    FftMountain("Mountains"),
    ParticleField("Particle field"),
    ParticleGalaxy("Particle galaxy"),
    PixelMountain("Pixel mountains"),
    PixelRidge("Pixel ridge"),
    ReactiveBars("Reactive bars"),
    RibbonTrail("Ribbon trail"),
    SpectralRidge("Spectral ridge"),
    WaveInterference("Wave interference"),
    VinylGroove("Vinyl groove"),
}

typealias ConnectionFormState = app.naviamp.domain.settings.ConnectionFormState
typealias PlaybackSettings = app.naviamp.domain.settings.PlaybackSettings

data class SharedTrackRowUi(
    val id: String,
    val title: String,
    val subtitle: String,
    val coverArtUrl: String? = null,
    val meta: String = "",
    val popular: Boolean = false,
    val detailSections: List<NaviampDetailSectionUi> = emptyList(),
)

data class NaviampDownloadedTrackUi(
    val id: String,
    val track: SharedTrackRowUi,
    val sizeBytes: Long,
)

data class SharedMediaItemUi(
    val id: String,
    val title: String,
    val subtitle: String,
    val meta: String = "",
    val coverArtUrl: String? = null,
    val coverArtUrls: List<String> = emptyList(),
    val isSmartPlaylist: Boolean = false,
    val favoriteActive: Boolean = false,
    val canFavorite: Boolean = false,
)

data class SharedAlbumDetailUi(
    val album: SharedMediaItemUi,
    val tracks: List<SharedTrackRowUi>,
    val totalDurationLabel: String = "",
)

data class SharedArtistDetailUi(
    val artist: SharedMediaItemUi,
    val albums: List<SharedMediaItemUi>,
    val biography: String? = null,
    val popularTracks: List<SharedTrackRowUi> = emptyList(),
    val popularTracksStatus: String? = null,
    val similarArtists: List<SharedSimilarArtistUi> = emptyList(),
    val similarArtistsStatus: String? = null,
)

data class SharedSimilarArtistUi(
    val id: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String? = null,
    val localArtistId: String? = null,
    val externalUrl: String? = null,
)

data class SharedPlaylistDetailUi(
    val playlist: SharedMediaItemUi,
    val tracks: List<SharedTrackRowUi>,
)

data class SharedHomeUi(
    val mixBuilders: List<SharedMixBuilderUi> = emptyList(),
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
        get() = mixBuilders.isEmpty() &&
            recentlyAddedAlbums.isEmpty() &&
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

data class SharedMixBuilderUi(
    val id: String,
    val title: String,
    val subtitle: String,
)

data class SharedHomeStationUi(
    val id: String,
    val title: String,
    val subtitle: String,
)

data class SharedSearchResultsUi(
    val artists: List<SharedMediaItemUi> = emptyList(),
    val albums: List<SharedMediaItemUi> = emptyList(),
    val tracks: List<SharedTrackRowUi> = emptyList(),
) {
    val isEmpty: Boolean
        get() = artists.isEmpty() && albums.isEmpty() && tracks.isEmpty()
}

data class SharedArtistMixBuilderUi(
    val query: String = "",
    val selectedArtists: List<SharedMediaItemUi> = emptyList(),
    val suggestedArtists: List<SharedMediaItemUi> = emptyList(),
    val status: String? = null,
    val loading: Boolean = false,
)

data class SharedAlbumMixBuilderUi(
    val query: String = "",
    val selectedAlbums: List<SharedMediaItemUi> = emptyList(),
    val suggestedAlbums: List<SharedMediaItemUi> = emptyList(),
    val status: String? = null,
    val loading: Boolean = false,
)

data class SharedGenreMixBuilderUi(
    val query: String = "",
    val selectedGenres: List<SharedGenreMixItemUi> = emptyList(),
    val suggestedGenres: List<SharedGenreMixItemUi> = emptyList(),
    val status: String? = null,
    val loading: Boolean = false,
)

data class SharedGenreMixItemUi(
    val id: String,
    val title: String,
    val subtitle: String = "",
)

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
    val canSaveQueueAsPlaylist: Boolean = false,
    val sleepTimer: NaviampSleepTimerUi = NaviampSleepTimerUi(),
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

data class NaviampSleepTimerUi(
    val active: Boolean = false,
    val label: String = "Sleep timer",
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
    ArtistMix("Artist Mix", NaviampTransportIcons.Radio),
    AlbumMix("Album Mix", NaviampTransportIcons.Radio),
    GenreMix("Genre Mix", NaviampTransportIcons.Radio),
    Radio("Radio", NaviampIcons.InternetRadio),
    Downloads("Downloads", NaviampIcons.Downloads),
    Settings("Settings", NaviampIcons.Settings),
}

data class NaviampLibrarySyncStatusUi(
    val message: String? = null,
    val isSyncing: Boolean = false,
) {
    val showRefresh: Boolean
        get() = message?.startsWith("Library changed on server") == true ||
            message?.startsWith("Navidrome is scanning") == true ||
            isSyncing
}

enum class SharedPlaylistSortMode(val label: String) {
    Alphabetical("A-Z"),
    RecentlyPlayed("Recent"),
}
