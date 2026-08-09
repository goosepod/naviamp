package app.naviamp.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackVisualizerFrame
import app.naviamp.domain.playback.AudioOutputDevice
import app.naviamp.domain.cache.StorageCacheStats
import app.naviamp.domain.radio.RadioDjPreset
import app.naviamp.domain.settings.ConnectionFormMusicFolder
import app.naviamp.domain.settings.ConnectionFormState
import app.naviamp.domain.settings.PlaybackSettings
import app.naviamp.domain.settings.CacheSettings
import app.naviamp.domain.settings.InterfaceSettings
import app.naviamp.domain.settings.DesktopShortcutPlatform
import app.naviamp.domain.settings.GlobalShortcutAction
import app.naviamp.domain.settings.HomeSectionLayout
import app.naviamp.domain.settings.HomeSectionPageLayout
import app.naviamp.domain.smartplaylist.SmartPlaylistDefinition
import app.naviamp.domain.waveform.AudioWaveform

data class NaviampColors(
    val background: Color = Color(0xFF101114),
    val backgroundWarm: Color = Color(0xFF52231F),
    val backgroundOlive: Color = Color(0xFF11190B),
    val primaryText: Color = Color.White,
    val secondaryText: Color = Color(0xFFD7CADF),
    val mutedText: Color = Color(0xFF8F96A3),
    val border: Color = Color(0xFF59606D),
    val accent: Color = Color(0xFF315D9E),
    val onAccent: Color = Color.White,
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
            accent = Color(0xFF315D9E),
            onAccent = Color.White,
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
    AlbumArtReactive("Album Art"),
    AnalogSignalFailure("Analog Signal Failure"),
    AudioSphere("Audio Sphere"),
    AudioTunnel("Audio Tunnel"),
    FluidGradient("Fluid Gradient"),
    FrequencyTerrain("Frequency Terrain"),
    FftMountain("Mountains"),
    FluidicNebulae("Fluidic Nebulae"),
    LyricMirrorTunnel("Lyric Mirror Tunnel"),
    OceanHorizon("Ocean Horizon"),
    OceanOfInk("Ocean Of Ink"),
    ParticleField("Particle Field"),
    ParticleGalaxy("Particle Galaxy"),
    PixelMountain("Pixel Mountains"),
    PixelRidge("Pixel Ridge"),
    RaymarchedSphereLiquid("Liquid Sphere"),
    ReactiveBars("Reactive Bars"),
    RibbonTrail("Ribbon Trail"),
    SpectrumBars("Spectrum Bars"),
    SpectralRidge("Spectral Ridge"),
    WaveInterference("Wave Interference"),
    VinylGroove("Vinyl Groove"),
}

fun naviampVisualizerFromName(name: String?): NaviampVisualizer =
    NaviampVisualizer.entries.firstOrNull { visualizer -> visualizer.name == name }
        ?: NaviampVisualizer.AudioSphere

fun isNaviampVisualizerVisible(
    requestedVisible: Boolean,
    playbackState: PlaybackState,
): Boolean =
    requestedVisible &&
        (playbackState == PlaybackState.Playing || playbackState == PlaybackState.Loading)

typealias ConnectionFormState = app.naviamp.domain.settings.ConnectionFormState
typealias PlaybackSettings = app.naviamp.domain.settings.PlaybackSettings
typealias CacheSettings = app.naviamp.domain.settings.CacheSettings

data class SharedTrackRowUi(
    val id: String,
    val title: String,
    val subtitle: String,
    val coverArtUrl: String? = null,
    val meta: String = "",
    val durationLabel: String = "",
    val ratingLabel: String? = null,
    val popular: Boolean = false,
    val favoriteActive: Boolean = false,
    val canToggleFavorite: Boolean = true,
    val hasAlbum: Boolean = false,
    val hasArtist: Boolean = false,
    val artistCredits: List<SharedArtistCreditUi> = emptyList(),
    val albumTitle: String? = null,
    val detailSections: List<NaviampDetailSectionUi> = emptyList(),
)

data class SharedArtistCreditUi(
    val id: String?,
    val name: String,
)

enum class SharedTrackRowAction {
    Select,
    PlayNext,
    StartRadio,
    PlayTrackRadioNext,
    AddTrackRadioToQueue,
    AddToQueue,
    Download,
    AddToPlaylist,
    CreatePlaylistAndAdd,
    ToggleFavorite,
    GoToAlbum,
    GoToArtist,
}

data class SharedTrackRowActionRequest(
    val track: SharedTrackRowUi,
    val action: SharedTrackRowAction,
    val playlistChoice: NaviampPlaylistChoiceUi? = null,
    val playlistName: String? = null,
    val artistId: String? = null,
    val artistName: String? = null,
)

enum class SharedTrackGroupAction {
    Play,
    StartRadio,
    AddToQueue,
}

data class SharedTrackGroupActionRequest(
    val tracks: List<SharedTrackRowUi>,
    val action: SharedTrackGroupAction,
)

data class SharedTrackRowActionHandlers(
    val onSelect: (SharedTrackRowUi) -> Unit,
    val onPlayNext: (SharedTrackRowUi) -> Unit,
    val onStartRadio: (SharedTrackRowUi) -> Unit,
    val onPlayTrackRadioNext: (SharedTrackRowUi) -> Unit,
    val onAddTrackRadioToQueue: (SharedTrackRowUi) -> Unit,
    val onAddToQueue: (SharedTrackRowUi) -> Unit,
    val onDownload: (SharedTrackRowUi) -> Unit,
    val onAddToPlaylist: (SharedTrackRowUi, NaviampPlaylistChoiceUi?) -> Unit,
    val onCreatePlaylistAndAdd: (SharedTrackRowUi, String) -> Unit,
    val onToggleFavorite: (SharedTrackRowUi) -> Unit,
    val onGoToAlbum: (SharedTrackRowUi) -> Unit,
    val onGoToArtist: (SharedTrackRowUi) -> Unit,
)

fun handleSharedTrackRowAction(
    request: SharedTrackRowActionRequest,
    handlers: SharedTrackRowActionHandlers,
) {
    when (request.action) {
        SharedTrackRowAction.Select -> handlers.onSelect(request.track)
        SharedTrackRowAction.PlayNext -> handlers.onPlayNext(request.track)
        SharedTrackRowAction.StartRadio -> handlers.onStartRadio(request.track)
        SharedTrackRowAction.PlayTrackRadioNext -> handlers.onPlayTrackRadioNext(request.track)
        SharedTrackRowAction.AddTrackRadioToQueue -> handlers.onAddTrackRadioToQueue(request.track)
        SharedTrackRowAction.AddToQueue -> handlers.onAddToQueue(request.track)
        SharedTrackRowAction.Download -> handlers.onDownload(request.track)
        SharedTrackRowAction.AddToPlaylist -> handlers.onAddToPlaylist(request.track, request.playlistChoice)
        SharedTrackRowAction.CreatePlaylistAndAdd ->
            request.playlistName?.let { name -> handlers.onCreatePlaylistAndAdd(request.track, name) }
        SharedTrackRowAction.ToggleFavorite -> handlers.onToggleFavorite(request.track)
        SharedTrackRowAction.GoToAlbum -> handlers.onGoToAlbum(request.track)
        SharedTrackRowAction.GoToArtist -> handlers.onGoToArtist(request.track)
    }
}

data class NaviampDownloadedTrackUi(
    val id: String,
    val track: SharedTrackRowUi,
    val sizeBytes: Long,
    val qualityLabel: String = "",
)

data class NaviampOfflineDashboardUi(
    val audioCacheCount: Long = 0L,
    val audioCacheBytes: Long = 0L,
    val maxAudioCacheBytes: Long = 0L,
    val pendingProviderActionCount: Long = 0L,
)

enum class DownloadedTrackAction {
    Select,
    AddToPlaylist,
    CreatePlaylistAndAdd,
    Remove,
}

data class DownloadedTrackActionRequest(
    val download: NaviampDownloadedTrackUi,
    val action: DownloadedTrackAction,
    val playlistChoice: NaviampPlaylistChoiceUi? = null,
    val playlistName: String? = null,
)

data class DownloadedTrackActionHandlers(
    val onSelect: (NaviampDownloadedTrackUi) -> Unit,
    val onAddToPlaylist: (NaviampDownloadedTrackUi, NaviampPlaylistChoiceUi?) -> Unit,
    val onCreatePlaylistAndAdd: (NaviampDownloadedTrackUi, String) -> Unit,
    val onRemove: (NaviampDownloadedTrackUi) -> Unit,
)

fun handleDownloadedTrackAction(
    request: DownloadedTrackActionRequest,
    handlers: DownloadedTrackActionHandlers,
) {
    when (request.action) {
        DownloadedTrackAction.Select -> handlers.onSelect(request.download)
        DownloadedTrackAction.AddToPlaylist -> handlers.onAddToPlaylist(request.download, request.playlistChoice)
        DownloadedTrackAction.CreatePlaylistAndAdd ->
            request.playlistName?.let { name -> handlers.onCreatePlaylistAndAdd(request.download, name) }
        DownloadedTrackAction.Remove -> handlers.onRemove(request.download)
    }
}

data class SharedMediaItemUi(
    val id: String,
    val title: String,
    val subtitle: String,
    val meta: String = "",
    val releaseYear: Int? = null,
    val trackCount: Int? = null,
    val coverArtUrl: String? = null,
    val coverArtUrls: List<String> = emptyList(),
    val isSmartPlaylist: Boolean = false,
    val keepDownloadedActive: Boolean = false,
    val favoriteActive: Boolean = false,
    val canFavorite: Boolean = false,
)

enum class SharedMediaItemKind {
    Unknown,
    Album,
    Artist,
    Playlist,
    RadioStation,
    MixBuilder,
    Track,
}

data class SharedAlbumDetailUi(
    val album: SharedMediaItemUi,
    val tracks: List<SharedTrackRowUi>,
    val totalDurationLabel: String = "",
    val information: String? = null,
    val artist: SharedMediaItemUi? = null,
)

data class NaviampDownloadJobUi(
    val id: String,
    val label: String,
    val statusLabel: String,
    val progress: Float,
    val canCancel: Boolean,
    val canRetry: Boolean,
    val activeItemLabel: String? = null,
    val failedItemLabel: String? = null,
)

data class NaviampDownloadsScreenUi(
    val downloads: List<NaviampDownloadedTrackUi> = emptyList(),
    val status: String? = null,
    val jobs: List<NaviampDownloadJobUi> = emptyList(),
    val downloadBytes: Long = 0L,
    val maxDownloadBytes: Long = 0L,
    val offlineDashboard: NaviampOfflineDashboardUi = NaviampOfflineDashboardUi(),
    val keepFavoritesDownloaded: Boolean = false,
)

data class NaviampDownloadsActions(
    val onTrackAction: (DownloadedTrackActionRequest) -> Unit,
    val onCancelJob: (String) -> Unit,
    val onRetryJob: (String) -> Unit,
    val onRefresh: () -> Unit,
    val onToggleKeepFavoritesDownloaded: () -> Unit,
    val onDeleteAll: () -> Unit,
)

data class NaviampInternetRadioStationUi(
    val item: SharedMediaItemUi,
    val streamUrl: String,
    val homePageUrl: String? = null,
)

data class NaviampInternetRadioStationEditUi(
    val id: String? = null,
    val name: String = "",
    val streamUrl: String = "",
    val homePageUrl: String? = null,
)

data class NaviampInternetRadioScreenUi(
    val stations: List<NaviampInternetRadioStationUi> = emptyList(),
    val status: String? = null,
    val refreshing: Boolean = false,
)

data class NaviampInternetRadioActions(
    val onRefresh: () -> Unit,
    val onStationAction: (StationRowActionRequest) -> Unit,
    val onSaveStation: (NaviampInternetRadioStationEditUi) -> Unit,
)

data class NaviampAlbumDetailScreenUi(
    val selectedAlbum: SharedMediaItemUi? = null,
    val detail: SharedAlbumDetailUi? = null,
    val status: String? = null,
)

sealed interface NaviampAlbumDetailCommand {
    data class Play(val shuffle: Boolean) : NaviampAlbumDetailCommand
    data object StartRadio : NaviampAlbumDetailCommand
    data object Download : NaviampAlbumDetailCommand
    data object AddToQueue : NaviampAlbumDetailCommand
    data class AddToPlaylist(val choice: NaviampPlaylistChoiceUi) : NaviampAlbumDetailCommand
    data class CreatePlaylistAndAdd(val name: String) : NaviampAlbumDetailCommand
    data object ToggleFavorite : NaviampAlbumDetailCommand
}

data class NaviampAlbumDetailActionRequest(
    val album: SharedMediaItemUi,
    val command: NaviampAlbumDetailCommand,
)

data class NaviampAlbumDetailActions(
    val onBack: () -> Unit,
    val onAlbumAction: (NaviampAlbumDetailActionRequest) -> Unit,
    val onTrackAction: (SharedTrackRowActionRequest) -> Unit,
    val onArtistSelected: (SharedMediaItemUi) -> Unit = {},
)

data class SharedArtistDetailUi(
    val artist: SharedMediaItemUi,
    val albums: List<SharedMediaItemUi>,
    val albumSections: List<SharedAlbumSectionUi> = emptyList(),
    val sourceContextLabel: String = "",
    val localLibraryLabel: String = "",
    val biography: String? = null,
    val popularTracks: List<SharedTrackRowUi> = emptyList(),
    val popularTracksStatus: String? = null,
    val similarArtists: List<SharedSimilarArtistUi> = emptyList(),
    val similarArtistsStatus: String? = null,
    val similarArtistsExpanded: Boolean = false,
)

data class NaviampArtistDetailScreenUi(
    val selectedArtist: SharedMediaItemUi? = null,
    val detail: SharedArtistDetailUi? = null,
    val status: String? = null,
)

sealed interface NaviampArtistDetailCommand {
    data class PlayCatalog(
        val albums: List<SharedMediaItemUi>,
        val shuffle: Boolean,
    ) : NaviampArtistDetailCommand
    data object StartRadio : NaviampArtistDetailCommand
    data object AddToQueue : NaviampArtistDetailCommand
    data class AddToPlaylist(val choice: NaviampPlaylistChoiceUi) : NaviampArtistDetailCommand
    data class CreatePlaylistAndAdd(val name: String) : NaviampArtistDetailCommand
    data object ToggleFavorite : NaviampArtistDetailCommand
    data object PlayPopular : NaviampArtistDetailCommand
    data object StartPopularRadio : NaviampArtistDetailCommand
    data object AddPopularToQueue : NaviampArtistDetailCommand
    data object FindSimilar : NaviampArtistDetailCommand
    data class SelectSimilar(val artist: SharedSimilarArtistUi) : NaviampArtistDetailCommand
    data class OpenSimilarExternal(val url: String) : NaviampArtistDetailCommand
}

data class NaviampArtistDetailActionRequest(
    val artist: SharedMediaItemUi,
    val command: NaviampArtistDetailCommand,
)

sealed interface NaviampArtistAlbumCommand {
    data object Select : NaviampArtistAlbumCommand
    data object StartRadio : NaviampArtistAlbumCommand
    data object Download : NaviampArtistAlbumCommand
    data object AddToQueue : NaviampArtistAlbumCommand
    data class AddToPlaylist(val choice: NaviampPlaylistChoiceUi) : NaviampArtistAlbumCommand
    data class CreatePlaylistAndAdd(val name: String) : NaviampArtistAlbumCommand
    data object ToggleFavorite : NaviampArtistAlbumCommand
}

data class NaviampArtistAlbumActionRequest(
    val album: SharedMediaItemUi,
    val command: NaviampArtistAlbumCommand,
)

data class NaviampArtistDetailActions(
    val onBack: () -> Unit,
    val onArtistAction: (NaviampArtistDetailActionRequest) -> Unit,
    val onAlbumAction: (NaviampArtistAlbumActionRequest) -> Unit,
    val onPopularTrackAction: (SharedTrackRowActionRequest) -> Unit,
)

data class SharedAlbumSectionUi(
    val title: String,
    val albums: List<SharedMediaItemUi>,
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

data class NaviampPlaylistsScreenUi(
    val playlists: List<SharedMediaItemUi> = emptyList(),
    val recentPlaylistIds: List<String> = emptyList(),
    val sortMode: SharedPlaylistSortMode = SharedPlaylistSortMode.Alphabetical,
    val status: String? = null,
    val refreshing: Boolean = false,
    val availableLibraries: List<ConnectionFormMusicFolder> = emptyList(),
    val selectedConnectionLibraryIds: List<String> = emptyList(),
)

data class NaviampSmartPlaylistActions(
    val onSave: suspend (SmartPlaylistDefinition) -> Unit,
    val onUpdate: suspend (SharedMediaItemUi, SmartPlaylistDefinition) -> Unit,
    val onSaveWithPassword: suspend (SmartPlaylistDefinition, String) -> Unit,
    val onUpdateWithPassword: suspend (SharedMediaItemUi, SmartPlaylistDefinition, String) -> Unit,
    val onLoad: suspend (SharedMediaItemUi) -> SmartPlaylistDefinition,
    val onLoadWithPassword: suspend (SharedMediaItemUi, String) -> SmartPlaylistDefinition,
)

data class NaviampPlaylistsActions(
    val onRefresh: () -> Unit,
    val onSortModeChanged: (SharedPlaylistSortMode) -> Unit,
    val smartPlaylist: NaviampSmartPlaylistActions,
)

data class NaviampPlaylistDetailScreenUi(
    val selectedPlaylist: SharedMediaItemUi? = null,
    val detail: SharedPlaylistDetailUi? = null,
    val status: String? = null,
    val availableLibraries: List<ConnectionFormMusicFolder> = emptyList(),
    val selectedConnectionLibraryIds: List<String> = emptyList(),
)

sealed interface NaviampPlaylistDetailCommand {
    data class Play(val shuffle: Boolean) : NaviampPlaylistDetailCommand
    data object AddToQueue : NaviampPlaylistDetailCommand
    data class Download(val value: String? = null) : NaviampPlaylistDetailCommand
    data class AddToPlaylist(val choice: NaviampPlaylistChoiceUi) : NaviampPlaylistDetailCommand
    data class CreatePlaylistAndAdd(val name: String) : NaviampPlaylistDetailCommand
    data class Copy(val name: String, val deduplicate: Boolean) : NaviampPlaylistDetailCommand
    data class Rename(val name: String) : NaviampPlaylistDetailCommand
    data object Delete : NaviampPlaylistDetailCommand
}

data class NaviampPlaylistDetailActionRequest(
    val playlist: SharedMediaItemUi,
    val command: NaviampPlaylistDetailCommand,
)

data class NaviampPlaylistDetailActions(
    val onBack: () -> Unit,
    val onPlaylistAction: (NaviampPlaylistDetailActionRequest) -> Unit,
    val onUpdateStandardPlaylist: suspend (SharedMediaItemUi, List<SharedTrackRowUi>) -> Unit,
    val onTrackAction: (SharedTrackRowActionRequest) -> Unit,
)

data class SharedHomeUi(
    val mixBuilders: List<SharedMixBuilderUi> = emptyList(),
    val collectionSections: List<SharedHomeCollectionSectionUi> = emptyList(),
    val navibeatMixes: List<SharedNavibeatMixUi> = emptyList(),
    val sonicDiscoveryRows: List<SharedHomeDiscoveryTrackRowUi> = emptyList(),
    val recentlyAddedAlbums: List<SharedMediaItemUi> = emptyList(),
    val mixAlbums: List<SharedMediaItemUi> = emptyList(),
    val recentAlbums: List<SharedMediaItemUi> = emptyList(),
    val frequentAlbums: List<SharedMediaItemUi> = emptyList(),
    val randomAlbums: List<SharedMediaItemUi> = emptyList(),
    val playlists: List<SharedMediaItemUi> = emptyList(),
    val recentRadioStreams: List<SharedMediaItemUi> = emptyList(),
    val recentlyPlayedTracks: List<SharedTrackRowUi> = emptyList(),
    val radioStations: List<SharedMediaItemUi> = emptyList(),
    val stations: List<SharedHomeStationUi> = emptyList(),
    val genreSpotlightTitle: String? = null,
    val genreSpotlightAlbums: List<SharedMediaItemUi> = emptyList(),
    val decadeLabel: String = "Decade",
    val decadeAlbums: List<SharedMediaItemUi> = emptyList(),
) {
    val isEmpty: Boolean
        get() = mixBuilders.isEmpty() &&
            collectionSections.isEmpty() &&
            navibeatMixes.isEmpty() &&
            sonicDiscoveryRows.isEmpty() &&
            recentlyAddedAlbums.isEmpty() &&
            mixAlbums.isEmpty() &&
            recentAlbums.isEmpty() &&
            frequentAlbums.isEmpty() &&
            randomAlbums.isEmpty() &&
            playlists.isEmpty() &&
            recentRadioStreams.isEmpty() &&
            recentlyPlayedTracks.isEmpty() &&
            radioStations.isEmpty() &&
            stations.isEmpty() &&
            genreSpotlightAlbums.isEmpty() &&
            decadeAlbums.isEmpty()
}

data class SharedNavibeatMixUi(
    val playlist: SharedMediaItemUi,
    val kind: String,
    val description: String,
    val statusLabel: String,
)

enum class SharedHomeCollectionArtwork {
    CoverArt,
    NavibeatGenerated,
}

enum class SharedHomeCollectionItemAction {
    PlayAlbum,
    OpenAlbum,
    OpenPlaylist,
    SelectRecentRadio,
    SelectInternetRadio,
    SelectStation,
    SelectMixBuilder,
    SelectRecentTrack,
    SelectSonicTrack,
}

data class SharedHomeCollectionItemUi(
    val mediaItem: SharedMediaItemUi,
    val mediaKind: SharedMediaItemKind,
    val title: String = mediaItem.title,
    val subtitle: String = mediaItem.subtitle,
    val artwork: SharedHomeCollectionArtwork = SharedHomeCollectionArtwork.CoverArt,
    val artworkKey: String? = null,
    val action: SharedHomeCollectionItemAction,
    val track: SharedTrackRowUi? = null,
    val discoveryRowId: String? = null,
    val mixBuilder: SharedMixBuilderUi? = null,
    val station: SharedHomeStationUi? = null,
)

data class SharedHomeCollectionSectionUi(
    val id: String,
    val title: String,
    val items: List<SharedHomeCollectionItemUi>,
    val supportedHomeLayouts: Set<HomeSectionLayout> = HomeSectionLayout.entries.toSet(),
    val homeLayout: HomeSectionLayout = HomeSectionLayout.Carousel,
    val supportedPageLayouts: Set<HomeSectionPageLayout> = HomeSectionPageLayout.entries.toSet(),
    val defaultPageLayout: HomeSectionPageLayout = HomeSectionPageLayout.Grid,
)

data class SharedHomeCollectionPageUi(
    val section: SharedHomeCollectionSectionUi,
    val layout: HomeSectionPageLayout = section.defaultPageLayout,
)

object SharedHomeCollectionSectionIds {
    const val MixesForYou = app.naviamp.domain.settings.HomeSectionIds.MixesForYou
    const val NavibeatMixes = app.naviamp.domain.settings.HomeSectionIds.NavibeatMixes
}

data class SharedHomeDiscoveryTrackRowUi(
    val id: String,
    val title: String,
    val tracks: List<SharedTrackRowUi>,
)

data class SharedHomeDiscoveryTrackActionRequest(
    val rowId: String,
    val track: SharedTrackRowUi,
    val action: SharedTrackRowAction,
    val artistId: String? = null,
    val artistName: String? = null,
)

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

enum class StationRowAction {
    Select,
    Edit,
    Delete,
}

data class StationRowActionRequest(
    val station: SharedMediaItemUi,
    val action: StationRowAction,
)

data class StationRowActionHandlers(
    val onSelect: (SharedMediaItemUi) -> Unit,
    val onEdit: (SharedMediaItemUi) -> Unit,
    val onDelete: (SharedMediaItemUi) -> Unit,
)

fun handleStationRowAction(
    request: StationRowActionRequest,
    handlers: StationRowActionHandlers,
) {
    when (request.action) {
        StationRowAction.Select -> handlers.onSelect(request.station)
        StationRowAction.Edit -> handlers.onEdit(request.station)
        StationRowAction.Delete -> handlers.onDelete(request.station)
    }
}

data class SharedSearchResultsUi(
    val artists: List<SharedMediaItemUi> = emptyList(),
    val albums: List<SharedMediaItemUi> = emptyList(),
    val tracks: List<SharedTrackRowUi> = emptyList(),
) {
    val isEmpty: Boolean
        get() = artists.isEmpty() && albums.isEmpty() && tracks.isEmpty()
}

data class NaviampSearchScreenUi(
    val query: String = "",
    val results: SharedSearchResultsUi = SharedSearchResultsUi(),
    val status: String? = null,
    val searching: Boolean = false,
)

data class NaviampSearchActions(
    val onQueryChanged: (String) -> Unit,
    val onSearch: () -> Unit,
    val onClear: () -> Unit,
)

data class NaviampLibraryScreenUi(
    val artists: List<SharedMediaItemUi> = emptyList(),
    val query: String = "",
    val syncStatus: NaviampLibrarySyncStatusUi = NaviampLibrarySyncStatusUi(),
)

data class NaviampLibraryActions(
    val onQueryChanged: (String) -> Unit,
    val onRefresh: () -> Unit,
    val onLoadMore: () -> Unit,
    val onJumpToLetter: (Char) -> Unit,
)

data class SharedArtistMixBuilderUi(
    val query: String = "",
    val selectedArtists: List<SharedMediaItemUi> = emptyList(),
    val suggestedArtists: List<SharedMediaItemUi> = emptyList(),
    val status: String? = null,
    val loading: Boolean = false,
)

data class SharedArtistMixBuilderActions(
    val onQueryChanged: (String) -> Unit,
    val onSearch: () -> Unit,
    val onArtistSelected: (SharedMediaItemUi) -> Unit,
    val onArtistRemoved: (SharedMediaItemUi) -> Unit,
    val onReset: () -> Unit,
    val onPlay: () -> Unit,
)

data class SharedAlbumMixBuilderUi(
    val query: String = "",
    val selectedAlbums: List<SharedMediaItemUi> = emptyList(),
    val suggestedAlbums: List<SharedMediaItemUi> = emptyList(),
    val status: String? = null,
    val loading: Boolean = false,
)

data class SharedAlbumMixBuilderActions(
    val onQueryChanged: (String) -> Unit,
    val onSearch: () -> Unit,
    val onAlbumSelected: (SharedMediaItemUi) -> Unit,
    val onAlbumRemoved: (SharedMediaItemUi) -> Unit,
    val onReset: () -> Unit,
    val onPlay: () -> Unit,
)

data class SharedGenreMixBuilderUi(
    val query: String = "",
    val selectedGenres: List<SharedGenreMixItemUi> = emptyList(),
    val suggestedGenres: List<SharedGenreMixItemUi> = emptyList(),
    val status: String? = null,
    val loading: Boolean = false,
)

data class SharedGenreMixBuilderActions(
    val onQueryChanged: (String) -> Unit,
    val onSearch: () -> Unit,
    val onGenreSelected: (SharedGenreMixItemUi) -> Unit,
    val onGenreRemoved: (SharedGenreMixItemUi) -> Unit,
    val onReset: () -> Unit,
    val onPlay: () -> Unit,
)

data class SharedSonicPathBuilderUi(
    val startQuery: String = "",
    val endQuery: String = "",
    val startTrack: SharedTrackRowUi? = null,
    val endTrack: SharedTrackRowUi? = null,
    val startSuggestions: List<SharedTrackRowUi> = emptyList(),
    val endSuggestions: List<SharedTrackRowUi> = emptyList(),
    val pathTracks: List<SharedTrackRowUi> = emptyList(),
    val count: Int = 25,
    val status: String? = null,
    val loading: Boolean = false,
) {
    val canBuild: Boolean
        get() = startTrack != null && endTrack != null && startTrack.id != endTrack.id

    val hasPath: Boolean
        get() = pathTracks.isNotEmpty()
}

data class NaviampHomeScreenUi(
    val content: SharedHomeUi = SharedHomeUi(),
    val refreshing: Boolean = false,
    val collectionPage: SharedHomeCollectionPageUi? = null,
)

data class NaviampHomeActions(
    val onRefresh: () -> Unit,
    val onRecentRadioSelected: (SharedMediaItemUi) -> Unit,
    val onInternetRadioStationSelected: (SharedMediaItemUi) -> Unit,
    val onMixBuilderSelected: (SharedMixBuilderUi) -> Unit,
    val onStationSelected: (SharedHomeStationUi) -> Unit,
    val onSonicDiscoveryTrackAction: (SharedHomeDiscoveryTrackActionRequest) -> Unit,
    val onRecentlyPlayedTrackAction: (SharedTrackRowActionRequest) -> Unit,
    val onCollectionSelected: (String) -> Unit,
    val onCollectionBack: () -> Unit,
    val onCollectionPageLayoutChanged: (String, HomeSectionPageLayout) -> Unit,
)

data class NaviampMediaActions(
    val onTrackAction: (SharedTrackRowActionRequest) -> Unit,
    val onMediaItemAction: (NaviampMediaItemActionRequest) -> Unit,
)

data class NaviampMediaCapabilities(
    val album: NaviampAlbumMediaCapabilities,
    val artist: NaviampArtistMediaCapabilities,
    val playlist: NaviampPlaylistMediaCapabilities,
)

data class NaviampAlbumMediaCapabilities(
    val canStartRadio: Boolean,
    val canDownload: Boolean,
    val canAddToQueue: Boolean,
    val canAddToPlaylist: Boolean,
    val canToggleFavorite: Boolean,
)

data class NaviampArtistMediaCapabilities(
    val canStartRadio: Boolean,
    val canFindSimilar: Boolean,
    val canAddToQueue: Boolean,
    val canAddToPlaylist: Boolean,
    val canToggleFavorite: Boolean,
)

data class NaviampPlaylistMediaCapabilities(
    val canDownload: Boolean,
    val canKeepDownloaded: Boolean,
    val canAddToQueue: Boolean,
    val canAddToPlaylist: Boolean,
    val canRename: Boolean,
    val canEditSmartPlaylist: Boolean,
    val canDelete: Boolean,
)

val NaviampSharedMediaCapabilities = NaviampMediaCapabilities(
    album = NaviampAlbumMediaCapabilities(
        canStartRadio = true,
        canDownload = true,
        canAddToQueue = true,
        canAddToPlaylist = true,
        canToggleFavorite = true,
    ),
    artist = NaviampArtistMediaCapabilities(
        canStartRadio = true,
        canFindSimilar = true,
        canAddToQueue = true,
        canAddToPlaylist = true,
        canToggleFavorite = true,
    ),
    playlist = NaviampPlaylistMediaCapabilities(
        canDownload = true,
        canKeepDownloaded = true,
        canAddToQueue = true,
        canAddToPlaylist = true,
        canRename = true,
        canEditSmartPlaylist = true,
        canDelete = true,
    ),
)

data class NaviampShellNavigationActions(
    val onRouteSelected: (SharedRoute) -> Unit,
    val onOpenNowPlaying: () -> Unit,
    val onCloseNowPlaying: () -> Unit,
)

data class NaviampShellChromeUi(
    val selectedRoute: SharedRoute = SharedRoute.Home,
    val nowPlayingOpen: Boolean = false,
    val supportsDownloads: Boolean = false,
    val supportsApplicationUpdates: Boolean = false,
    val selectedVisualizer: NaviampVisualizer = NaviampVisualizer.AudioSphere,
)

data class NaviampAppShellUiState(
    val capabilities: NaviampShellCapabilitiesUi = NaviampShellCapabilitiesUi(),
    val connectionSettings: NaviampConnectionSettingsUi = NaviampConnectionSettingsUi(),
    val general: NaviampGeneralSettingsUi = NaviampGeneralSettingsUi(),
    val playback: NaviampPlaybackSettingsUi = NaviampPlaybackSettingsUi(),
    val cache: NaviampCacheSettingsUi = NaviampCacheSettingsUi(),
    val shellChrome: NaviampShellChromeUi = NaviampShellChromeUi(),
    val search: NaviampSearchScreenUi = NaviampSearchScreenUi(),
    val home: NaviampHomeScreenUi = NaviampHomeScreenUi(),
    val artistMixBuilder: SharedArtistMixBuilderUi = SharedArtistMixBuilderUi(),
    val albumMixBuilder: SharedAlbumMixBuilderUi = SharedAlbumMixBuilderUi(),
    val genreMixBuilder: SharedGenreMixBuilderUi = SharedGenreMixBuilderUi(),
    val sonicPathBuilder: SharedSonicPathBuilderUi = SharedSonicPathBuilderUi(),
    val sonicMixBuilder: SharedSonicMixBuilderUi = SharedSonicMixBuilderUi(),
    val library: NaviampLibraryScreenUi = NaviampLibraryScreenUi(),
    val downloads: NaviampDownloadsScreenUi = NaviampDownloadsScreenUi(),
    val playlists: NaviampPlaylistsScreenUi = NaviampPlaylistsScreenUi(),
    val playlistChoices: List<NaviampPlaylistChoiceUi> = emptyList(),
    val radio: NaviampInternetRadioScreenUi = NaviampInternetRadioScreenUi(),
    val albumDetail: NaviampAlbumDetailScreenUi = NaviampAlbumDetailScreenUi(),
    val artistDetail: NaviampArtistDetailScreenUi = NaviampArtistDetailScreenUi(),
    val playlistDetail: NaviampPlaylistDetailScreenUi = NaviampPlaylistDetailScreenUi(),
    val nowPlaying: NowPlayingUi? = null,
)

data class NaviampAppShellActions(
    val navigationActions: NaviampShellNavigationActions,
    val connectionActions: NaviampConnectionSettingsActions,
    val valueActions: NaviampSettingsValueActions,
    val maintenanceActions: NaviampSettingsMaintenanceActions,
    val searchActions: NaviampSearchActions,
    val artistMixActions: SharedArtistMixBuilderActions,
    val albumMixActions: SharedAlbumMixBuilderActions,
    val genreMixActions: SharedGenreMixBuilderActions,
    val sonicPathActions: SharedSonicPathBuilderActions,
    val sonicMixActions: SharedSonicMixBuilderActions,
    val downloadsActions: NaviampDownloadsActions,
    val libraryActions: NaviampLibraryActions,
    val playlistsActions: NaviampPlaylistsActions,
    val radioActions: NaviampInternetRadioActions,
    val albumDetailActions: NaviampAlbumDetailActions,
    val artistDetailActions: NaviampArtistDetailActions,
    val playlistDetailActions: NaviampPlaylistDetailActions,
    val homeActions: NaviampHomeActions,
    val mediaActions: NaviampMediaActions,
    val nowPlayingActions: NaviampNowPlayingActions,
)

data class SharedSonicPathBuilderActions(
    val onStartQueryChanged: (String) -> Unit,
    val onEndQueryChanged: (String) -> Unit,
    val onStartSearch: () -> Unit,
    val onEndSearch: () -> Unit,
    val onStartTrackSelected: (SharedTrackRowUi) -> Unit,
    val onEndTrackSelected: (SharedTrackRowUi) -> Unit,
    val onStartTrackCleared: () -> Unit,
    val onEndTrackCleared: () -> Unit,
    val onCountChanged: (Int) -> Unit,
    val onBuild: () -> Unit,
    val onReset: () -> Unit,
    val onPlay: () -> Unit,
    val onAddToQueue: () -> Unit,
    val onSaveAsPlaylist: (String) -> Unit,
)

data class SharedSonicMixBuilderUi(
    val query: String = "",
    val selectedTracks: List<SharedTrackRowUi> = emptyList(),
    val suggestedTracks: List<SharedTrackRowUi> = emptyList(),
    val mixTracks: List<SharedTrackRowUi> = emptyList(),
    val targetLength: Int = 50,
    val bias: SharedSonicMixBiasUi = SharedSonicMixBiasUi.Balanced,
    val includeSeeds: Boolean = false,
    val status: String? = null,
    val loading: Boolean = false,
) {
    val canBuild: Boolean
        get() = selectedTracks.size >= 2

    val hasMix: Boolean
        get() = mixTracks.isNotEmpty()
}

data class SharedSonicMixBuilderActions(
    val onQueryChanged: (String) -> Unit,
    val onSearch: () -> Unit,
    val onTrackSelected: (SharedTrackRowUi) -> Unit,
    val onTrackRemoved: (SharedTrackRowUi) -> Unit,
    val onTargetLengthChanged: (Int) -> Unit,
    val onBiasChanged: (SharedSonicMixBiasUi) -> Unit,
    val onIncludeSeedsChanged: (Boolean) -> Unit,
    val onBuild: () -> Unit,
    val onReset: () -> Unit,
    val onPlay: () -> Unit,
    val onAddToQueue: () -> Unit,
    val onSaveAsPlaylist: (String) -> Unit,
)

enum class SharedSonicMixBiasUi(val label: String) {
    Balanced("Balanced"),
    Favorites("Favorites"),
    Unplayed("Unplayed"),
    Recent("Recent"),
}

data class SharedGenreMixItemUi(
    val id: String,
    val title: String,
    val subtitle: String = "",
)

data class NowPlayingUi(
    val id: String = "",
    val title: String,
    val subtitle: String,
    val artistCredits: List<SharedArtistCreditUi> = emptyList(),
    val stateLabel: String,
    val coverArtUrl: String? = null,
    val isLive: Boolean = false,
    val albumLine: String = "",
    val albumTitle: String = "",
    val albumYear: Int? = null,
    val audioInfo: String = "",
    val waveform: AudioWaveform? = null,
    val visualizerFrame: PlaybackVisualizerFrame? = null,
    val bpm: Int? = null,
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
    val lyricsAvailableTiming: app.naviamp.domain.lyrics.LyricsTiming? = null,
    val lyricsDisplayTiming: app.naviamp.domain.lyrics.LyricsTiming? = null,
    val menuEnabled: Boolean = false,
    val detailSections: List<NaviampDetailSectionUi> = emptyList(),
    val playlistChoices: List<NaviampPlaylistChoiceUi> = emptyList(),
    val useInlinePlaylistPicker: Boolean = true,
    val playlistActionStatus: String? = null,
    val backTo: List<NaviampNowPlayingItemUi> = emptyList(),
    val upNext: List<NaviampNowPlayingItemUi> = emptyList(),
    val related: List<NaviampNowPlayingItemUi> = emptyList(),
    val relatedTabLabel: String = "RELATED",
    val relatedEmptyLabel: String = "Related tracks are not loaded.",
    val radioStations: List<NaviampNowPlayingItemUi> = emptyList(),
    val radioDjs: List<RadioDjPreset> = emptyList(),
    val activeRadioDjId: String? = null,
)

data class NaviampSleepTimerUi(
    val active: Boolean = false,
    val label: String = "Sleep timer",
)

data class NaviampConnectionCapabilitiesUi(
    val insecureServerVerification: Boolean = false,
    val customServerCertificates: Boolean = false,
    val clientCertificates: Boolean = false,
)

data class NaviampShellConnectionUi(
    val status: String? = null,
    val statusIsError: Boolean = false,
    val serverVersion: String? = null,
    val connected: Boolean = false,
    val editingConnection: Boolean = false,
    val editingSavedConnection: Boolean = false,
    val restoringConnection: Boolean = false,
    val isConnecting: Boolean = false,
    val form: ConnectionFormState = ConnectionFormState(),
    val availableMusicFolders: List<ConnectionFormMusicFolder> = emptyList(),
    val musicFoldersStatus: String? = null,
    val savedConnections: List<NaviampSavedConnectionUi> = emptyList(),
    val hasSavedConnection: Boolean = false,
)

data class NaviampConnectionSettingsUi(
    val connection: NaviampShellConnectionUi = NaviampShellConnectionUi(),
    val capabilities: NaviampConnectionCapabilitiesUi = NaviampConnectionCapabilitiesUi(),
    val currentSourceId: String? = null,
)

data class NaviampConnectionSettingsActions(
    val onFormChanged: (ConnectionFormState) -> Unit,
    val onConnect: () -> Unit,
    val onEditCurrentConnection: () -> Unit,
    val onNewConnection: () -> Unit,
    val onEditConnection: (NaviampSavedConnectionUi) -> Unit,
    val onDeleteConnection: (NaviampSavedConnectionUi) -> Unit,
    val onConnectSavedConnection: (NaviampSavedConnectionUi) -> Unit,
    val onCancelConnectionForm: () -> Unit,
) {
    fun updateForm(
        current: ConnectionFormState,
        transform: (ConnectionFormState) -> ConnectionFormState,
    ) {
        onFormChanged(transform(current))
    }
}

data class NaviampSettingsSyncUi(
    val directoryPath: String? = null,
    val autoExportEnabled: Boolean = false,
    val status: String? = null,
    val available: Boolean = false,
)

data class NaviampSettingsSyncActions(
    val onDirectoryChanged: (String?) -> Unit,
    val onDirectorySelectedForImport: (String) -> Unit,
    val onAutoExportChanged: (Boolean) -> Unit,
    val onExport: () -> Unit,
    val onImport: () -> Unit,
    val onImportFile: (() -> Unit)? = null,
    val onChooseFolder: (() -> Unit)? = null,
    val onImportFolder: (() -> Unit)? = null,
    val onExportFolder: (() -> Unit)? = null,
)

data class NaviampSettingsValueActions(
    val onInterfaceSettingsChanged: (InterfaceSettings) -> Unit,
    val onPlaybackSettingsChanged: (PlaybackSettings) -> Unit,
    val onPlaybackSettingsChangedAndRedownload: (PlaybackSettings) -> Unit,
    val onCacheSettingsChanged: (CacheSettings) -> Unit,
    val onDownloadLocationChanged: (NaviampStorageLocationUi) -> Unit,
    val onAudioCacheLocationChanged: (NaviampStorageLocationUi) -> Unit,
)

data class NaviampSettingsMaintenanceActions(
    val onOpenStatsForNerds: () -> Unit,
    val onClearCache: () -> Unit,
    val onClearLibrary: () -> Unit,
    val onRefreshLibrary: () -> Unit,
    val onResetDatabase: () -> Unit,
)

data class NaviampGeneralSettingsUi(
    val interfaceSettings: InterfaceSettings = InterfaceSettings(),
    val about: NaviampAboutUi = NaviampAboutUi(),
    val globalShortcutStatuses: Map<GlobalShortcutAction, GlobalShortcutRegistrationUi> = emptyMap(),
)

enum class GlobalShortcutRegistrationState { Registered, Conflict, Unavailable }

data class GlobalShortcutRegistrationUi(
    val state: GlobalShortcutRegistrationState,
    val detail: String = "",
)

data class NaviampPlaybackSettingsUi(
    val settings: PlaybackSettings = PlaybackSettings(),
    val replayGainAvailable: Boolean = false,
    val gaplessAvailable: Boolean = true,
    val crossfadeAvailable: Boolean = false,
    val equalizerAvailable: Boolean = false,
    val audioOutputDeviceSelectionAvailable: Boolean = false,
    val audioOutputDevices: List<AudioOutputDevice> = emptyList(),
    val sonicSimilarityAvailable: Boolean = false,
    val softwareVolumeControlAvailable: Boolean = true,
    val hoverTooltipsAvailable: Boolean = false,
    val showMobileNetworkQuality: Boolean = false,
    val downloadBytes: Long = 0L,
)

data class NaviampCacheSettingsUi(
    val settings: CacheSettings = CacheSettings(),
    val diagnostics: NaviampDiagnosticsUi = NaviampDiagnosticsUi(),
    val downloadsDiagnostics: NaviampDiagnosticsUi = NaviampDiagnosticsUi(),
    val audioCacheDiagnostics: NaviampDiagnosticsUi = NaviampDiagnosticsUi(),
    val fileSelectionAvailable: Boolean = false,
    val downloadLocations: List<NaviampStorageLocationUi> = emptyList(),
    val audioCacheLocations: List<NaviampStorageLocationUi> = emptyList(),
    val selectedDownloadLocationId: String? = null,
    val selectedAudioCacheLocationId: String? = null,
)

data class NaviampShellCapabilitiesUi(
    val replayGain: Boolean = false,
    val gapless: Boolean = true,
    val crossfade: Boolean = false,
    val equalizer: Boolean = false,
    val sonicSimilarity: Boolean = false,
    val softwareVolumeControl: Boolean = true,
    val hoverTooltips: Boolean = false,
    val downloads: Boolean = false,
    val settingsImportExport: Boolean = false,
    val applicationUpdates: Boolean = false,
    val fileSelection: Boolean = false,
    val showMobileNetworkQuality: Boolean = false,
    val desktopShortcutPlatform: DesktopShortcutPlatform? = null,
    val connection: NaviampConnectionCapabilitiesUi = NaviampConnectionCapabilitiesUi(),
)

fun NaviampShellConnectionUi.toConnectionSettingsUi(
    capabilities: NaviampShellCapabilitiesUi,
    currentSourceId: String? = null,
): NaviampConnectionSettingsUi =
    NaviampConnectionSettingsUi(
        connection = this,
        capabilities = capabilities.connection,
        currentSourceId = currentSourceId,
    )

fun settingsSyncUi(
    directoryPath: String?,
    autoExportEnabled: Boolean,
    status: String?,
    capabilities: NaviampShellCapabilitiesUi,
): NaviampSettingsSyncUi =
    NaviampSettingsSyncUi(
        directoryPath = directoryPath,
        autoExportEnabled = autoExportEnabled,
        status = status,
        available = capabilities.settingsImportExport && capabilities.fileSelection,
    )

fun InterfaceSettings.toGeneralSettingsUi(
    about: NaviampAboutUi,
): NaviampGeneralSettingsUi =
    NaviampGeneralSettingsUi(
        interfaceSettings = this,
        about = about,
    )

fun PlaybackSettings.toPlaybackSettingsUi(
    capabilities: NaviampShellCapabilitiesUi,
    audioOutputDeviceSelectionAvailable: Boolean = false,
    audioOutputDevices: List<AudioOutputDevice> = emptyList(),
    downloadBytes: Long = 0L,
): NaviampPlaybackSettingsUi =
    NaviampPlaybackSettingsUi(
        settings = this,
        replayGainAvailable = capabilities.replayGain,
        gaplessAvailable = capabilities.gapless,
        crossfadeAvailable = capabilities.crossfade,
        equalizerAvailable = capabilities.equalizer,
        audioOutputDeviceSelectionAvailable = audioOutputDeviceSelectionAvailable,
        audioOutputDevices = audioOutputDevices,
        sonicSimilarityAvailable = capabilities.sonicSimilarity,
        softwareVolumeControlAvailable = capabilities.softwareVolumeControl,
        hoverTooltipsAvailable = capabilities.hoverTooltips,
        showMobileNetworkQuality = capabilities.showMobileNetworkQuality,
        downloadBytes = downloadBytes,
    )

fun CacheSettings.toCacheSettingsUi(
    stats: StorageCacheStats,
    capabilities: NaviampShellCapabilitiesUi,
): NaviampCacheSettingsUi =
    NaviampCacheSettingsUi(
        settings = this,
        downloadsDiagnostics = NaviampDiagnosticsUi(
            sections = listOf(
                NaviampDiagnosticsSectionUi(
                    title = "Storage",
                    rows = listOf(
                        "Audio cache" to stats.audioBytes.storageBytesLabel(),
                        "Downloads" to stats.downloadBytes.storageBytesLabel(),
                        "Images" to stats.imageBytes.storageBytesLabel(),
                    ),
                ),
            ),
        ),
        audioCacheDiagnostics = NaviampDiagnosticsUi(
            sections = listOf(
                NaviampDiagnosticsSectionUi(
                    title = "Storage",
                    rows = listOf("Audio cache" to stats.audioBytes.storageBytesLabel()),
                ),
            ),
        ),
        fileSelectionAvailable = capabilities.fileSelection,
    )

data class NaviampLyricLineUi(
    val startMillis: Long?,
    val text: String,
    val endMillis: Long? = null,
    val agentId: String? = null,
    val cues: List<NaviampLyricCueUi> = emptyList(),
)

data class NaviampLyricCueUi(
    val startMillis: Long?,
    val endMillis: Long?,
    val text: String,
    val byteStart: Int? = null,
    val byteEnd: Int? = null,
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
    SonicPath("Sonic Path", NaviampIcons.Brain),
    SonicMix("Sonic Mix", NaviampIcons.Brain),
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
