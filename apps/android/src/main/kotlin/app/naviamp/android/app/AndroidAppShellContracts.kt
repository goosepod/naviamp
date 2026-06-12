package app.naviamp.android

import androidx.compose.ui.Modifier
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Playlist
import app.naviamp.domain.Track
import app.naviamp.domain.settings.ConnectionFormState
import app.naviamp.domain.settings.PlaybackSettings
import app.naviamp.domain.smartplaylist.SmartPlaylistDefinition
import app.naviamp.ui.NaviampDiagnosticsUi
import app.naviamp.ui.NaviampDownloadedTrackUi
import app.naviamp.ui.NaviampLibrarySyncStatusUi
import app.naviamp.ui.NaviampPlaylistChoiceUi
import app.naviamp.ui.NaviampVisualizer
import app.naviamp.ui.DownloadedTrackActionRequest
import app.naviamp.ui.NowPlayingCurrentTrackUiActionRequest
import app.naviamp.ui.NowPlayingDisplayActionRequest
import app.naviamp.ui.NowPlayingItemActionRequest
import app.naviamp.ui.NowPlayingPlaybackActionRequest
import app.naviamp.ui.NowPlayingQueueActionRequest
import app.naviamp.ui.NowPlayingSelectionActionRequest
import app.naviamp.ui.NowPlayingSleepTimerActionRequest
import app.naviamp.ui.NowPlayingUi
import app.naviamp.ui.SharedAlbumDetailUi
import app.naviamp.ui.SharedAlbumMixBuilderUi
import app.naviamp.ui.SharedArtistDetailUi
import app.naviamp.ui.SharedArtistMixBuilderUi
import app.naviamp.ui.SharedGenreMixBuilderUi
import app.naviamp.ui.SharedGenreMixItemUi
import app.naviamp.ui.SharedHomeStationUi
import app.naviamp.ui.SharedHomeUi
import app.naviamp.ui.SharedMediaItemActionRequest
import app.naviamp.ui.SharedMediaItemUi
import app.naviamp.ui.SharedMixBuilderUi
import app.naviamp.ui.SharedPlaylistDetailUi
import app.naviamp.ui.SharedPlaylistSortMode
import app.naviamp.ui.SharedRoute
import app.naviamp.ui.SharedSearchResultsUi
import app.naviamp.ui.SharedSimilarArtistUi
import app.naviamp.ui.StationRowActionRequest
import app.naviamp.ui.SharedTrackRowActionRequest
import app.naviamp.ui.SharedTrackRowUi

data class AndroidAppShellUiState(
    val modifier: Modifier,
    val status: String,
    val serverVersion: String?,
    val connected: Boolean,
    val editingConnection: Boolean,
    val restoringConnection: Boolean,
    val connectionForm: ConnectionFormState,
    val playbackSettings: PlaybackSettings,
    val diagnostics: NaviampDiagnosticsUi,
    val supportsReplayGain: Boolean,
    val supportsGapless: Boolean,
    val supportsCrossfade: Boolean,
    val supportsEqualizer: Boolean,
    val showMobileNetworkQuality: Boolean,
    val selectedVisualizer: NaviampVisualizer,
    val visualizerBandsProvider: () -> List<Float>,
    val query: String,
    val home: SharedHomeUi,
    val searchResults: SharedSearchResultsUi,
    val artistMixBuilder: SharedArtistMixBuilderUi,
    val albumMixBuilder: SharedAlbumMixBuilderUi,
    val genreMixBuilder: SharedGenreMixBuilderUi,
    val libraryArtists: List<SharedMediaItemUi>,
    val libraryQuery: String,
    val librarySyncStatus: NaviampLibrarySyncStatusUi,
    val downloads: List<NaviampDownloadedTrackUi>,
    val downloadBytes: Long,
    val maxDownloadBytes: Long,
    val downloadStatus: String?,
    val playlistItems: List<SharedMediaItemUi>,
    val recentPlaylistIds: List<String>,
    val playlistSortMode: SharedPlaylistSortMode,
    val playlistChoices: List<NaviampPlaylistChoiceUi>,
    val playlistActionStatus: String?,
    val radioStations: List<InternetRadioStation>,
    val albumDetail: SharedAlbumDetailUi?,
    val artistDetail: SharedArtistDetailUi?,
    val playlistDetail: SharedPlaylistDetailUi?,
    val nowPlaying: NowPlayingUi?,
    val nowPlayingOpen: Boolean,
    val selectedRoute: SharedRoute,
)

data class AndroidAppShellActions(
    val onRouteSelected: (SharedRoute) -> Unit,
    val onConnectionFormChanged: (ConnectionFormState) -> Unit,
    val onConnect: () -> Unit,
    val onEditConnection: () -> Unit,
    val onCancelEditConnection: () -> Unit,
    val onPlaybackSettingsChanged: (PlaybackSettings) -> Unit,
    val onPlaybackSettingsChangedAndRedownload: (PlaybackSettings) -> Unit,
    val onClearCache: () -> Unit,
    val onClearLibrary: () -> Unit,
    val onResetDatabase: () -> Unit,
    val onQueryChanged: (String) -> Unit,
    val onSearch: () -> Unit,
    val onClearSearch: () -> Unit,
    val onArtistMixQueryChanged: (String) -> Unit,
    val onArtistMixSearch: () -> Unit,
    val onArtistMixArtistSelected: (SharedMediaItemUi) -> Unit,
    val onArtistMixArtistRemoved: (SharedMediaItemUi) -> Unit,
    val onArtistMixReset: () -> Unit,
    val onArtistMixPlay: () -> Unit,
    val onAlbumMixQueryChanged: (String) -> Unit,
    val onAlbumMixSearch: () -> Unit,
    val onAlbumMixAlbumSelected: (SharedMediaItemUi) -> Unit,
    val onAlbumMixAlbumRemoved: (SharedMediaItemUi) -> Unit,
    val onAlbumMixReset: () -> Unit,
    val onAlbumMixPlay: () -> Unit,
    val onGenreMixQueryChanged: (String) -> Unit,
    val onGenreMixSearch: () -> Unit,
    val onGenreMixGenreSelected: (SharedGenreMixItemUi) -> Unit,
    val onGenreMixGenreRemoved: (SharedGenreMixItemUi) -> Unit,
    val onGenreMixReset: () -> Unit,
    val onGenreMixPlay: () -> Unit,
    val onLibraryQueryChanged: (String) -> Unit,
    val onRefreshLibrary: () -> Unit,
    val onTrackSelected: (SharedTrackRowUi) -> Unit,
    val onDownloadedTrackAction: (DownloadedTrackActionRequest) -> Unit,
    val onAlbumSelected: (SharedMediaItemUi) -> Unit,
    val onAlbumFavoriteToggled: (SharedMediaItemUi) -> Unit,
    val onMixAlbumSelected: (SharedMediaItemUi) -> Unit,
    val onAlbumPlay: (SharedAlbumDetailUi, Boolean) -> Unit,
    val onAlbumTrackSelected: (SharedTrackRowUi) -> Unit,
    val onAlbumRadio: (SharedAlbumDetailUi) -> Unit,
    val onAlbumAddToQueue: (SharedAlbumDetailUi) -> Unit,
    val onAlbumDownload: (SharedAlbumDetailUi) -> Unit,
    val onAlbumAddToPlaylist: (SharedAlbumDetailUi, NaviampPlaylistChoiceUi?) -> Unit,
    val onAlbumCreatePlaylistAndAdd: (SharedAlbumDetailUi, String) -> Unit,
    val onTrackAction: (SharedTrackRowActionRequest) -> Unit,
    val onArtistRadio: (SharedArtistDetailUi) -> Unit,
    val onArtistShuffle: (SharedArtistDetailUi) -> Unit,
    val onArtistAddToQueue: (SharedArtistDetailUi) -> Unit,
    val onArtistAddToPlaylist: (SharedArtistDetailUi, NaviampPlaylistChoiceUi?) -> Unit,
    val onArtistCreatePlaylistAndAdd: (SharedArtistDetailUi, String) -> Unit,
    val onArtistPopularPlay: (SharedArtistDetailUi) -> Unit,
    val onArtistPopularRadio: (SharedArtistDetailUi) -> Unit,
    val onArtistPopularTrackSelected: (SharedTrackRowUi) -> Unit,
    val onArtistPopularAddToQueue: (SharedArtistDetailUi) -> Unit,
    val onFindSimilarArtists: (SharedArtistDetailUi) -> Unit,
    val onSimilarArtistSelected: (SharedSimilarArtistUi) -> Unit,
    val onSimilarArtistExternalSelected: (String) -> Unit,
    val onArtistSelected: (SharedMediaItemUi) -> Unit,
    val onArtistFavoriteToggled: (SharedMediaItemUi) -> Unit,
    val onPlaylistSelected: (SharedMediaItemUi) -> Unit,
    val onPlaylistSortModeChanged: (SharedPlaylistSortMode) -> Unit,
    val onPlaylistPlay: (SharedMediaItemUi, Boolean) -> Unit,
    val onPlaylistAddToQueue: (SharedPlaylistDetailUi) -> Unit,
    val onPlaylistAddToPlaylist: (SharedPlaylistDetailUi, NaviampPlaylistChoiceUi?) -> Unit,
    val onPlaylistCreatePlaylistAndAdd: (SharedPlaylistDetailUi, String) -> Unit,
    val onPlaylistRename: (SharedMediaItemUi, String) -> Unit,
    val onPlaylistDelete: (SharedMediaItemUi) -> Unit,
    val onMediaItemAction: (SharedMediaItemActionRequest) -> Unit,
    val onSmartPlaylistSave: suspend (SmartPlaylistDefinition) -> Unit,
    val onSmartPlaylistUpdate: suspend (SharedMediaItemUi, SmartPlaylistDefinition) -> Unit,
    val onSmartPlaylistLoad: suspend (SharedMediaItemUi) -> SmartPlaylistDefinition,
    val onPlaylistBack: () -> Unit,
    val onPlaylistTrackSelected: (SharedTrackRowUi) -> Unit,
    val onRecentRadioSelected: (SharedMediaItemUi) -> Unit,
    val onMixBuilderSelected: (SharedMixBuilderUi) -> Unit,
    val onRadioStationSelected: (InternetRadioStation) -> Unit,
    val onRadioStationSave: (InternetRadioStation) -> Unit,
    val onStationAction: (StationRowActionRequest) -> Unit,
    val onHomeStationSelected: (SharedHomeStationUi) -> Unit,
    val onOpenNowPlaying: () -> Unit,
    val onCloseNowPlaying: () -> Unit,
    val onNowPlayingPlaybackAction: (NowPlayingPlaybackActionRequest) -> Unit,
    val onNowPlayingDisplayAction: (NowPlayingDisplayActionRequest) -> Unit,
    val onNowPlayingCurrentTrackAction: (NowPlayingCurrentTrackUiActionRequest) -> Unit,
    val onNowPlayingQueueAction: (NowPlayingQueueActionRequest) -> Unit,
    val onNowPlayingSleepTimerAction: (NowPlayingSleepTimerActionRequest) -> Unit,
    val onNowPlayingSelectionAction: (NowPlayingSelectionActionRequest) -> Unit,
    val onQueueItemAction: (NowPlayingItemActionRequest) -> Unit,
)
