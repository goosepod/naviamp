package app.naviamp.android

import app.naviamp.domain.cache.StorageCacheStats

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.Genre
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Lyrics
import app.naviamp.domain.Playlist
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.app.NaviampContentState
import app.naviamp.domain.app.NaviampNavigationState
import app.naviamp.domain.audio.AudioTag
import app.naviamp.domain.media.RelatedTracksSource
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.playback.PlaybackVisualizerFrame
import app.naviamp.domain.playback.SleepTimerState
import app.naviamp.domain.provider.ConnectionValidation
import app.naviamp.domain.provider.MediaSearchResults
import app.naviamp.domain.provider.PendingPlaybackAction
import app.naviamp.domain.popular.SimilarArtistMatch
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode
import app.naviamp.domain.settings.ConnectionFormState
import app.naviamp.domain.settings.PlaybackSettings
import app.naviamp.domain.waveform.AudioWaveform
import app.naviamp.provider.navidrome.NavidromeProvider
import app.naviamp.provider.navidrome.NavidromeTlsSettings
import app.naviamp.ui.NaviampVisualizer
import app.naviamp.ui.SharedPlaylistSortMode
import app.naviamp.ui.SharedRoute
import app.naviamp.ui.isNaviampVisualizerVisible
import app.naviamp.ui.toSharedRoute
import kotlinx.coroutines.Job

class AndroidAppState(
    savedConnection: ConnectionFormState,
    savedPlaybackSettings: PlaybackSettings,
    canAutoConnect: Boolean,
    savedSourceId: String?,
    initialStorageStats: StorageCacheStats,
    initialOpenNowPlayingRequest: Int,
    initialAutoPlayMediaIdRequest: String?,
    initialAutoCommandRequest: String?,
    initialSelectedVisualizer: NaviampVisualizer,
) {
    var connectionName by mutableStateOf(savedConnection.displayName)
    var serverUrl by mutableStateOf(savedConnection.serverUrl)
    var username by mutableStateOf(savedConnection.username)
    var password by mutableStateOf(savedConnection.password)
    var skipTlsVerification by mutableStateOf(savedConnection.skipTlsVerification)
    var customCertificatePath by mutableStateOf(savedConnection.customCertificatePath)
    var clientCertificatePath by mutableStateOf(savedConnection.clientCertificatePath)
    var clientCertificatePassword by mutableStateOf(savedConnection.clientCertificatePassword)
    var playbackSettings by mutableStateOf(savedPlaybackSettings)
    var homeState by mutableStateOf(app.naviamp.domain.home.HomeContent())
    var contentState by mutableStateOf(NaviampContentState())
    val query: String get() = contentState.searchQuery
    val searchResults: MediaSearchResults get() = contentState.searchResults
    val albumDetail: AlbumDetails? get() = contentState.albumDetail
    val artistDetail: ArtistDetails? get() = contentState.artistDetail
    val selectedPlaylist: Playlist? get() = contentState.selectedPlaylist
    val selectedPlaylistTracks: List<Track> get() = contentState.selectedPlaylistTracks
    var playlistSortMode by mutableStateOf(SharedPlaylistSortMode.Alphabetical)
    var recentPlaylistIds by mutableStateOf<List<String>>(emptyList())
    var playlistTracksById by mutableStateOf<Map<String, List<Track>>>(emptyMap())
    var libraryQuery by mutableStateOf("")
    var storageStats by mutableStateOf(initialStorageStats)
    var downloadedTracks by mutableStateOf<List<AndroidDownloadedTrack>>(emptyList())
    var downloadStatus by mutableStateOf<String?>(null)
    var downloadRefreshToken by mutableStateOf(0)
    var libraryStatus by mutableStateOf<String?>(null)
    var isLibrarySyncing by mutableStateOf(false)
    var editingConnection by mutableStateOf(false)
    var restoringConnection by mutableStateOf(canAutoConnect)
    var navigationState by mutableStateOf(NaviampNavigationState())
    val selectedRoute: SharedRoute get() = navigationState.route.toSharedRoute()
    var provider by mutableStateOf<NavidromeProvider?>(null)
    var activeSourceId by mutableStateOf<String?>(savedSourceId)
    var restoredStartPositionSeconds by mutableStateOf<Double?>(null)
    var pendingOpenNowPlayingFromIntent by mutableStateOf(initialOpenNowPlayingRequest > 0)
    var pendingAutoPlayMediaId by mutableStateOf(initialAutoPlayMediaIdRequest)
    var pendingAutoCommand by mutableStateOf(initialAutoCommandRequest)
    var activeTlsSettings by mutableStateOf(NavidromeTlsSettings())
    var validation by mutableStateOf<ConnectionValidation?>(null)
    var status by mutableStateOf("Connect to Navidrome to start Android playback.")
    var tracks by mutableStateOf<List<Track>>(emptyList())
    var nowPlaying by mutableStateOf<Track?>(null)
    var nowPlayingStation by mutableStateOf<InternetRadioStation?>(null)
    var nowPlayingStreamMetadata by mutableStateOf(PlaybackStreamMetadata())
    var nowPlayingOpen by mutableStateOf(false)
    var playbackState by mutableStateOf<PlaybackState>(PlaybackState.Idle)
    var playbackProgress by mutableStateOf(PlaybackProgress.Unknown)
    var visualizerFrame by mutableStateOf<PlaybackVisualizerFrame?>(null)
    var visualizerRequestedVisible by mutableStateOf(false)
    var selectedVisualizer by mutableStateOf(initialSelectedVisualizer)
    val visualizerVisible: Boolean
        get() = isNaviampVisualizerVisible(visualizerRequestedVisible, playbackState)
    var pendingSeekPositionSeconds by mutableStateOf<Double?>(null)
    var pendingSeekIssuedAtMillis by mutableStateOf<Long?>(null)
    var pendingRestoreStartPositionSeconds by mutableStateOf<Double?>(null)
    var playbackSessionToken by mutableStateOf(0L)
    var submittedPlayReportSessionToken by mutableStateOf<Long?>(null)
    var volumePercent by mutableStateOf(100)
    var waveformByTrackId by mutableStateOf<Map<String, AudioWaveform>>(emptyMap())
    var audioTagsByTrackId by mutableStateOf<Map<String, List<AudioTag>>>(emptyMap())
    var playbackQueue by mutableStateOf(PlaybackQueue())
    var sleepTimer by mutableStateOf<SleepTimerState?>(null)
    var sleepTimerNowEpochMillis by mutableStateOf(System.currentTimeMillis())
    var shuffledUpNextSnapshot by mutableStateOf<List<Track>?>(null)
    var repeatMode by mutableStateOf(RepeatMode.Off)
    var radioQueueActive by mutableStateOf(false)
    var radioRefilling by mutableStateOf(false)
    var lastRadioRefillSeedId by mutableStateOf<TrackId?>(null)
    var radioTrackArtworkByKey by mutableStateOf<Map<String, String?>>(emptyMap())
    var relatedTracks by mutableStateOf<List<Track>>(emptyList())
    var relatedTracksSource by mutableStateOf(RelatedTracksSource.None)
    var relatedSimilarityByTrackId by mutableStateOf(emptyMap<TrackId, Double>())
    var artistPopularTracksByArtistId by mutableStateOf<Map<String, List<Track>>>(emptyMap())
    var artistPopularTracksStatusByArtistId by mutableStateOf<Map<String, String?>>(emptyMap())
    var artistSimilarArtistsByArtistId by mutableStateOf<Map<String, List<SimilarArtistMatch>>>(emptyMap())
    var artistSimilarArtistsStatusByArtistId by mutableStateOf<Map<String, String?>>(emptyMap())
    var artistDetailBackStack by mutableStateOf<List<Artist>>(emptyList())
    var artistMixQuery by mutableStateOf("")
    var artistMixSelectedArtists by mutableStateOf<List<Artist>>(emptyList())
    var artistMixSuggestions by mutableStateOf<List<Artist>>(emptyList())
    var artistMixPopularTracksByArtistId by mutableStateOf<Map<String, List<Track>>>(emptyMap())
    var artistMixStatus by mutableStateOf<String?>(null)
    var artistMixLoading by mutableStateOf(false)
    var albumMixQuery by mutableStateOf("")
    var albumMixSelectedAlbums by mutableStateOf<List<Album>>(emptyList())
    var albumMixSuggestions by mutableStateOf<List<Album>>(emptyList())
    var albumMixTracksByAlbumId by mutableStateOf<Map<String, List<Track>>>(emptyMap())
    var albumMixStatus by mutableStateOf<String?>(null)
    var albumMixLoading by mutableStateOf(false)
    var genreMixQuery by mutableStateOf("")
    var genreMixSelectedGenres by mutableStateOf<List<Genre>>(emptyList())
    var genreMixSuggestions by mutableStateOf<List<Genre>>(emptyList())
    var genreMixStatus by mutableStateOf<String?>(null)
    var genreMixLoading by mutableStateOf(false)
    var lyricsVisible by mutableStateOf(false)
    var lyricsByTrackId by mutableStateOf<Map<String, Lyrics?>>(emptyMap())
    var lyricsStatusByTrackId by mutableStateOf<Map<String, String?>>(emptyMap())
    var playlistActionStatus by mutableStateOf<String?>(null)
    var pendingPlaybackAction by mutableStateOf<PendingPlaybackAction?>(null)
    var audioPrefetchJob by mutableStateOf<Job?>(null)
    var sidecarPrepJob by mutableStateOf<Job?>(null)
    var lastPlaybackSessionSaveAtMillis by mutableStateOf(0L)
    var lastAndroidAutoProgressPublishAtMillis by mutableStateOf(0L)
}

@Composable
fun rememberAndroidAppState(
    savedConnection: ConnectionFormState,
    savedPlaybackSettings: PlaybackSettings,
    canAutoConnect: Boolean,
    savedSourceId: String?,
    initialStorageStats: StorageCacheStats,
    initialOpenNowPlayingRequest: Int,
    initialAutoPlayMediaIdRequest: String?,
    initialAutoCommandRequest: String?,
    initialSelectedVisualizer: NaviampVisualizer,
): AndroidAppState =
    remember {
        AndroidAppState(
            savedConnection = savedConnection,
            savedPlaybackSettings = savedPlaybackSettings,
            canAutoConnect = canAutoConnect,
            savedSourceId = savedSourceId,
            initialStorageStats = initialStorageStats,
            initialOpenNowPlayingRequest = initialOpenNowPlayingRequest,
            initialAutoPlayMediaIdRequest = initialAutoPlayMediaIdRequest,
            initialAutoCommandRequest = initialAutoCommandRequest,
            initialSelectedVisualizer = initialSelectedVisualizer,
        )
    }
