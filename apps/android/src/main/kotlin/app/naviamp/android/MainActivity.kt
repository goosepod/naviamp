package app.naviamp.android

import android.Manifest
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.naviamp.android.playback.AndroidAutoPlaybackControls
import app.naviamp.android.playback.AndroidBassLoadReport
import app.naviamp.android.playback.AndroidPlaybackEngine
import app.naviamp.android.playback.AndroidPlaybackForegroundService
import app.naviamp.android.playback.AndroidPlaybackTls
import app.naviamp.android.playback.AndroidPlaybackNotificationControls
import app.naviamp.android.playback.AndroidPlaybackRuntime
import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.ArtistId
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Lyrics
import app.naviamp.domain.Playlist
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.app.NaviampContentState
import app.naviamp.domain.app.NaviampNavigationState
import app.naviamp.domain.app.NaviampRoute
import app.naviamp.domain.provider.AlbumListType
import app.naviamp.domain.provider.ConnectionValidation
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.MediaSearchResults
import app.naviamp.domain.home.HomeContent
import app.naviamp.domain.home.HomeDate
import app.naviamp.domain.home.HomeService
import app.naviamp.domain.home.HomeStationLibrary
import app.naviamp.domain.home.HomeStationRandomAlbum
import app.naviamp.domain.home.parseHomeDecadeStationId
import app.naviamp.domain.home.parseHomeGenreStationId
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackRequest
import app.naviamp.domain.playback.PlaybackReplayGain
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.playback.PlaybackVisualizerFrame
import app.naviamp.domain.playback.QueueAwarePlaybackEngine
import app.naviamp.domain.playback.ReplayGainMode
import app.naviamp.domain.playback.ReplayGainSource
import app.naviamp.domain.playback.VisualizerPlaybackEngine
import app.naviamp.domain.playback.label
import app.naviamp.domain.popular.ArtistPopularTracksService
import app.naviamp.domain.popular.DeezerPopularTracksClient
import app.naviamp.domain.popular.SimilarArtistMatch
import app.naviamp.domain.popular.SimilarArtistsService
import app.naviamp.domain.lyrics.selectPreferredLyrics
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode
import app.naviamp.domain.radio.RadioService
import app.naviamp.domain.settings.ConnectionFormState
import app.naviamp.domain.settings.PlaybackSettings
import app.naviamp.domain.settings.PlaybackSessionSettings
import app.naviamp.domain.settings.PreviousButtonBehavior
import app.naviamp.domain.settings.downloadStreamQuality
import app.naviamp.domain.settings.streamQualityForNetwork
import app.naviamp.domain.smartplaylist.SmartPlaylistDefinition
import app.naviamp.domain.waveform.AudioWaveform
import app.naviamp.provider.navidrome.NavidromeConnection
import app.naviamp.provider.navidrome.NavidromeApiCall
import app.naviamp.provider.navidrome.NavidromeApiCallHistory
import app.naviamp.provider.navidrome.NavidromeProvider
import app.naviamp.provider.navidrome.NavidromeTlsSettings
import app.naviamp.provider.navidrome.toNavidromeConnection
import app.naviamp.provider.navidrome.withNativeTokenFromPassword
import app.naviamp.ui.AndroidTrackRowUi
import app.naviamp.ui.NaviampNowPlayingItemUi
import app.naviamp.ui.NaviampDiagnosticsSectionUi
import app.naviamp.ui.NaviampDiagnosticsUi
import app.naviamp.ui.NaviampDownloadedTrackUi
import app.naviamp.ui.NaviampLibrarySyncStatusUi
import app.naviamp.ui.NaviampPlaylistChoiceUi
import app.naviamp.ui.NaviampSharedAppShell
import app.naviamp.ui.NaviampVisualizer
import app.naviamp.ui.NowPlayingRadioUiConfig
import app.naviamp.ui.NowPlayingTrackUiConfig
import app.naviamp.ui.NowPlayingUi
import app.naviamp.ui.SharedAlbumDetailUi
import app.naviamp.ui.SharedArtistDetailUi
import app.naviamp.ui.SharedHomeStationUi
import app.naviamp.ui.SharedMediaItemUi
import app.naviamp.ui.SharedPlaylistDetailUi
import app.naviamp.ui.SharedPlaylistSortMode
import app.naviamp.ui.SharedRoute
import app.naviamp.ui.SharedSimilarArtistUi
import app.naviamp.ui.toAndroidTrackRowUi
import app.naviamp.ui.toNowPlayingItemUi
import app.naviamp.ui.toNowPlayingStationUi
import app.naviamp.ui.toNowPlayingUi
import app.naviamp.ui.toPlaylistChoiceUi
import app.naviamp.ui.toSharedAlbumDetailUi
import app.naviamp.ui.toSharedArtistDetailUi
import app.naviamp.ui.toSharedHomeUi
import app.naviamp.ui.toSharedMediaItemUi
import app.naviamp.ui.toSharedPlaylistDetailUi
import app.naviamp.ui.bytesLabel
import app.naviamp.ui.label as streamQualityLabel
import app.naviamp.ui.toSharedSearchResultsUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    private var openNowPlayingRequest by mutableStateOf(0)
    private var autoPlayMediaIdRequest by mutableStateOf<String?>(null)
    private var autoCommandRequest by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleLaunchIntent(intent)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
        setContent {
            NaviampAndroidApp(
                openNowPlayingRequest = openNowPlayingRequest,
                autoPlayMediaIdRequest = autoPlayMediaIdRequest,
                onAutoPlayMediaIdConsumed = { autoPlayMediaIdRequest = null },
                autoCommandRequest = autoCommandRequest,
                onAutoCommandConsumed = { autoCommandRequest = null },
                modifier = Modifier
                    .systemBarsPadding()
                    .imePadding(),
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunchIntent(intent)
    }

    private fun handleLaunchIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(ExtraOpenNowPlaying, false) == true) {
            openNowPlayingRequest += 1
            android.util.Log.i("NaviampAutoCommand", "Received open-now-playing request")
        }
        intent?.getStringExtra(ExtraAutoPlayMediaId)?.takeIf { it.isNotBlank() }?.let { mediaId ->
            autoPlayMediaIdRequest = mediaId
            android.util.Log.i("NaviampAutoCommand", "Received Auto mediaId=$mediaId")
        }
        intent?.getStringExtra(ExtraAutoCommand)?.takeIf { it.isNotBlank() }?.let { command ->
            autoCommandRequest = command
            android.util.Log.i("NaviampAutoCommand", "Received Auto command=$command")
        }
    }

    companion object {
        const val ExtraOpenNowPlaying = "app.naviamp.android.OPEN_NOW_PLAYING"
        const val ExtraAutoPlayMediaId = "app.naviamp.android.AUTO_PLAY_MEDIA_ID"
        const val ExtraAutoCommand = "app.naviamp.android.AUTO_COMMAND"
    }
}

@Composable
private fun NaviampAndroidApp(
    openNowPlayingRequest: Int = 0,
    autoPlayMediaIdRequest: String? = null,
    onAutoPlayMediaIdConsumed: () -> Unit = {},
    autoCommandRequest: String? = null,
    onAutoCommandConsumed: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val playbackRuntime = remember { AndroidPlaybackRuntime.get(context) }
    val scope = playbackRuntime.scope
    val bassLoadReport = playbackRuntime.bassLoadReport
    val playbackEngine: AndroidPlaybackEngine = playbackRuntime.playbackEngine
    val waveformAnalyzer = playbackRuntime.waveformAnalyzer
    val lrclibLyricsClient = remember { AndroidLrclibLyricsClient() }
    val storage = remember { AndroidStorage(context) }
    val settingsStore = remember { AndroidSettingsStore(context) }
    val savedProviderSource = remember { storage.latestNavidromeSource() }
    val savedProviderConnection = savedProviderSource?.toNavidromeConnection()
    val savedConnection = remember { settingsStore.loadConnection(savedProviderConnection) }
    val canAutoConnect = savedProviderConnection != null ||
        (
            savedConnection.serverUrl.isNotBlank() &&
                savedConnection.username.isNotBlank() &&
                savedConnection.password.isNotBlank()
            )
    val savedPlaybackSettings = remember { settingsStore.loadPlaybackSettings() }
    var connectionName by remember { mutableStateOf(savedConnection.displayName) }
    var serverUrl by remember { mutableStateOf(savedConnection.serverUrl) }
    var username by remember { mutableStateOf(savedConnection.username) }
    var password by remember { mutableStateOf(savedConnection.password) }
    var skipTlsVerification by remember { mutableStateOf(savedConnection.skipTlsVerification) }
    var customCertificatePath by remember { mutableStateOf(savedConnection.customCertificatePath) }
    var clientCertificatePath by remember { mutableStateOf(savedConnection.clientCertificatePath) }
    var clientCertificatePassword by remember { mutableStateOf(savedConnection.clientCertificatePassword) }
    var playbackSettings by remember { mutableStateOf(savedPlaybackSettings) }
    var homeState by remember { mutableStateOf(HomeContent()) }
    var contentState by remember { mutableStateOf(NaviampContentState()) }
    val query = contentState.searchQuery
    val searchResults = contentState.searchResults
    val albumDetail = contentState.albumDetail
    val artistDetail = contentState.artistDetail
    val selectedPlaylist = contentState.selectedPlaylist
    val selectedPlaylistTracks = contentState.selectedPlaylistTracks
    var playlistSortMode by remember { mutableStateOf(SharedPlaylistSortMode.Alphabetical) }
    var recentPlaylistIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var playlistTracksById by remember { mutableStateOf<Map<String, List<Track>>>(emptyMap()) }
    var libraryQuery by remember { mutableStateOf("") }
    var storageStats by remember { mutableStateOf(storage.stats()) }
    var downloadedTracks by remember { mutableStateOf<List<AndroidDownloadedTrack>>(emptyList()) }
    var downloadStatus by remember { mutableStateOf<String?>(null) }
    var downloadRefreshToken by remember { mutableStateOf(0) }
    var libraryStatus by remember { mutableStateOf<String?>(null) }
    var isLibrarySyncing by remember { mutableStateOf(false) }
    var editingConnection by remember { mutableStateOf(false) }
    var restoringConnection by remember { mutableStateOf(canAutoConnect) }
    var navigationState by remember { mutableStateOf(NaviampNavigationState()) }
    val selectedRoute = navigationState.route.toSharedRoute()
    var provider by remember { mutableStateOf<NavidromeProvider?>(null) }
    var activeSourceId by remember { mutableStateOf<String?>(savedProviderSource?.id) }
    val deezerDiscoveryClient = remember { DeezerPopularTracksClient(AndroidPopularTracksHttpClient()) }
    val popularTracksService = remember(storage, deezerDiscoveryClient) {
        ArtistPopularTracksService(
            repository = storage,
            libraryTracksForArtist = { artist, limit ->
                val sourceId = activeSourceId
                val indexedTracks = sourceId
                    ?.let { storage.libraryTracksForArtist(it, artist.id, limit) }
                    .orEmpty()
                    .ifEmpty {
                        sourceId
                            ?.let { storage.libraryTracksForArtistName(it, artist.name, limit) }
                            .orEmpty()
                    }
                indexedTracks.ifEmpty {
                    provider?.tracksForArtist(artist.id, limit.coerceAtMost(AndroidPopularTrackFallbackLimit)).orEmpty()
                }
            },
            client = deezerDiscoveryClient,
        )
    }
    val similarArtistsService = remember(storage, deezerDiscoveryClient) {
        SimilarArtistsService(
            libraryArtistsSearch = { artistName, limit ->
                val sourceId = activeSourceId
                val indexedArtists = sourceId
                    ?.let { storage.searchLibrary(it, artistName, limit, 0).artists }
                    .orEmpty()
                indexedArtists.ifEmpty {
                    provider?.search(artistName, limit.toInt())?.artists.orEmpty()
                }
            },
            client = deezerDiscoveryClient,
        )
    }
    var restoredStartPositionSeconds by remember { mutableStateOf<Double?>(null) }
    var pendingOpenNowPlayingFromIntent by remember { mutableStateOf(openNowPlayingRequest > 0) }
    var pendingAutoPlayMediaId by remember { mutableStateOf(autoPlayMediaIdRequest) }
    var pendingAutoCommand by remember { mutableStateOf(autoCommandRequest) }
    var activeTlsSettings by remember { mutableStateOf(NavidromeTlsSettings()) }
    var validation by remember { mutableStateOf<ConnectionValidation?>(null) }
    var status by remember { mutableStateOf("Connect to Navidrome to start Android playback.") }
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var nowPlaying by remember { mutableStateOf<Track?>(null) }
    var nowPlayingStation by remember { mutableStateOf<InternetRadioStation?>(null) }
    var nowPlayingStreamMetadata by remember { mutableStateOf(PlaybackStreamMetadata()) }
    var nowPlayingOpen by remember { mutableStateOf(false) }
    var playbackState by remember { mutableStateOf<PlaybackState>(PlaybackState.Idle) }
    var playbackProgress by remember { mutableStateOf(PlaybackProgress.Unknown) }
    var visualizerFrame by remember { mutableStateOf<PlaybackVisualizerFrame?>(null) }
    var visualizerRequestedVisible by remember { mutableStateOf(false) }
    var selectedVisualizer by remember {
        mutableStateOf(
            NaviampVisualizer.entries.firstOrNull { it.name == settingsStore.loadSelectedVisualizer() }
                ?: NaviampVisualizer.AudioSphere,
        )
    }
    val visualizerVisible = visualizerRequestedVisible &&
        (playbackState == PlaybackState.Playing || playbackState == PlaybackState.Loading)
    var pendingSeekPositionSeconds by remember { mutableStateOf<Double?>(null) }
    var pendingSeekIssuedAtMillis by remember { mutableStateOf<Long?>(null) }
    var playbackSessionToken by remember { mutableStateOf(0L) }
    var submittedPlayReportSessionToken by remember { mutableStateOf<Long?>(null) }
    var volumePercent by remember { mutableStateOf(100) }
    var waveformByTrackId by remember { mutableStateOf<Map<String, AudioWaveform>>(emptyMap()) }
    var playbackQueue by remember { mutableStateOf(PlaybackQueue()) }
    var preparedNextTrackId by remember { mutableStateOf<String?>(null) }
    var shuffledUpNextSnapshot by remember { mutableStateOf<List<Track>?>(null) }
    var repeatMode by remember { mutableStateOf(RepeatMode.Off) }
    var relatedTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var artistPopularTracksByArtistId by remember { mutableStateOf<Map<String, List<Track>>>(emptyMap()) }
    var artistPopularTracksStatusByArtistId by remember { mutableStateOf<Map<String, String?>>(emptyMap()) }
    var artistSimilarArtistsByArtistId by remember { mutableStateOf<Map<String, List<SimilarArtistMatch>>>(emptyMap()) }
    var artistSimilarArtistsStatusByArtistId by remember { mutableStateOf<Map<String, String?>>(emptyMap()) }
    var artistDetailBackStack by remember { mutableStateOf<List<Artist>>(emptyList()) }
    var lyricsVisible by remember { mutableStateOf(false) }
    var lyricsByTrackId by remember { mutableStateOf<Map<String, Lyrics?>>(emptyMap()) }
    var lyricsStatusByTrackId by remember { mutableStateOf<Map<String, String?>>(emptyMap()) }
    var playlistActionStatus by remember { mutableStateOf<String?>(null) }
    var sidecarPrepJob by remember { mutableStateOf<Job?>(null) }
    var lastPlaybackSessionSaveAtMillis by remember { mutableStateOf(0L) }
    var lastAndroidAutoProgressPublishAtMillis by remember { mutableStateOf(0L) }

    LaunchedEffect(bassLoadReport) {
        if (!bassLoadReport.available) {
            status = "BASS libraries are bundled but did not load on this device."
        }
    }

    LaunchedEffect(nowPlaying?.id, nowPlayingStation?.id) {
        visualizerFrame = null
    }

    LaunchedEffect(provider, nowPlaying?.id, playbackState) {
        val activeProvider = provider ?: return@LaunchedEffect
        val track = nowPlaying ?: return@LaunchedEffect
        if (!activeProvider.capabilities.supportsPlayReporting || track.isInternetRadioTrack()) return@LaunchedEffect
        if (playbackState != PlaybackState.Playing) return@LaunchedEffect

        while (true) {
            runCatching {
                withContext(Dispatchers.IO) {
                    activeProvider.reportNowPlaying(track.id)
                }
            }
            delay(AndroidNowPlayingHeartbeatIntervalMillis)
        }
    }

    LaunchedEffect(playbackEngine, playbackState, visualizerVisible, nowPlayingOpen) {
        val visualizerEngine = playbackEngine as? VisualizerPlaybackEngine
        if (visualizerEngine?.supportsVisualizer != true) {
            visualizerFrame = null
            return@LaunchedEffect
        }
        if (!visualizerVisible || !nowPlayingOpen) {
            visualizerFrame = null
            return@LaunchedEffect
        }
        while (visualizerVisible && nowPlayingOpen && (playbackState == PlaybackState.Playing || playbackState == PlaybackState.Loading)) {
            visualizerFrame = visualizerEngine.visualizerFrame()
            delay(66)
        }
        visualizerFrame = null
    }

    LaunchedEffect(openNowPlayingRequest) {
        if (openNowPlayingRequest > 0) {
            pendingOpenNowPlayingFromIntent = true
        }
    }

    LaunchedEffect(pendingOpenNowPlayingFromIntent, nowPlaying, nowPlayingStation) {
        if (pendingOpenNowPlayingFromIntent && (nowPlaying != null || nowPlayingStation != null)) {
            nowPlayingOpen = true
            editingConnection = false
            pendingOpenNowPlayingFromIntent = false
        }
    }

    LaunchedEffect(autoPlayMediaIdRequest) {
        if (!autoPlayMediaIdRequest.isNullOrBlank()) {
            pendingAutoPlayMediaId = autoPlayMediaIdRequest
        }
    }

    LaunchedEffect(autoCommandRequest) {
        if (!autoCommandRequest.isNullOrBlank()) {
            pendingAutoCommand = autoCommandRequest
        }
    }

    LaunchedEffect(playbackEngine, playbackSettings.crossfadeDurationSeconds) {
        (playbackEngine as? QueueAwarePlaybackEngine)
            ?.setCrossfadeDuration(playbackSettings.crossfadeDurationSeconds)
    }

    DisposableEffect(playbackEngine) {
        onDispose {
            // Playback intentionally outlives the activity so Android Auto and the notification
            // remain useful after the phone UI is swiped away.
        }
    }

    fun activeQueue(): List<Track> =
        playbackQueue.tracks.ifEmpty { allKnownTracks(searchResults, albumDetail) }

    fun findKnownTrack(trackId: String): Track? =
        (activeQueue() + selectedPlaylistTracks + relatedTracks + allKnownTracks(searchResults, albumDetail))
            .firstOrNull { it.id.value == trackId }

    fun appendTracksToQueue(tracksToAdd: List<Track>, label: String = "tracks") {
        if (tracksToAdd.isEmpty()) {
            status = "No tracks found."
            return
        }
        val currentQueue = playbackQueue.tracks
        playbackQueue = PlaybackQueue(
            tracks = currentQueue + tracksToAdd,
            currentIndex = playbackQueue.currentIndex.coerceAtLeast(0),
        )
        status = "Added ${tracksToAdd.size} $label to queue."
    }

    fun clearDerivedMediaState() {
        waveformByTrackId = emptyMap()
        lyricsByTrackId = emptyMap()
        lyricsStatusByTrackId = emptyMap()
        relatedTracks = emptyList()
        artistPopularTracksByArtistId = emptyMap()
        artistPopularTracksStatusByArtistId = emptyMap()
        artistSimilarArtistsByArtistId = emptyMap()
        artistSimilarArtistsStatusByArtistId = emptyMap()
        playlistTracksById = emptyMap()
    }

    fun clearAndroidFileCaches() {
        deleteDirectoryContents(File(context.cacheDir, "cover-art"))
        deleteDirectoryContents(File(context.cacheDir, "waveforms"))
    }

    fun resetPlaybackState() {
        playbackEngine.stop()
        sidecarPrepJob?.cancel()
        sidecarPrepJob = null
        playbackSessionToken += 1
        playbackState = PlaybackState.Idle
        playbackProgress = PlaybackProgress.Unknown
        nowPlaying = null
        nowPlayingStation = null
        nowPlayingStreamMetadata = PlaybackStreamMetadata()
        nowPlayingOpen = false
        visualizerFrame = null
        visualizerRequestedVisible = false
        playbackQueue = PlaybackQueue()
        preparedNextTrackId = null
        shuffledUpNextSnapshot = null
        restoredStartPositionSeconds = null
    }

    fun nextQueueIndex(): Int? {
        val currentTrack = nowPlaying ?: return null
        val queue = activeQueue()
        val currentIndex = queue.indexOfFirst { it.id == currentTrack.id }
        if (currentIndex < 0) return null
        return when {
            repeatMode == RepeatMode.Track -> currentIndex
            currentIndex < queue.lastIndex -> currentIndex + 1
            repeatMode == RepeatMode.Queue && queue.isNotEmpty() -> 0
            else -> null
        }
    }

    fun currentStreamQuality(): StreamQuality =
        playbackSettings.streamQualityForNetwork(context.isActiveNetworkMobileData())

    fun currentDownloadQuality(): StreamQuality =
        playbackSettings.downloadStreamQuality()

    fun prepareNextIfNeeded(sessionToken: Long, progress: PlaybackProgress) {
        val queueAwareEngine = playbackEngine as? QueueAwarePlaybackEngine ?: return
        val canPrepareForCrossfade = playbackSettings.crossfadeDurationSeconds > 0 && playbackEngine.supportsCrossfade
        val canPrepareForGapless = playbackSettings.gaplessEnabled && playbackEngine.supportsGapless
        if (!canPrepareForCrossfade && !canPrepareForGapless) return
        val position = progress.positionSeconds ?: return
        val duration = progress.durationSeconds ?: return
        val prepareWindowSeconds = if (canPrepareForCrossfade) {
            playbackSettings.crossfadeDurationSeconds.toDouble()
        } else {
            AndroidGaplessPrepareWindowSeconds
        }
        if (duration - position > prepareWindowSeconds) return
        val nextIndex = nextQueueIndex() ?: return
        val nextTrack = activeQueue().getOrNull(nextIndex) ?: return
        if (preparedNextTrackId == nextTrack.id.value) return
        val activeProvider = provider ?: return
        preparedNextTrackId = nextTrack.id.value
        scope.launch {
            runCatching {
                activeProvider.streamUrl(StreamRequest(nextTrack.id, currentStreamQuality()))
            }.onSuccess { streamUrl ->
                if (sessionToken != playbackSessionToken) return@onSuccess
                queueAwareEngine.prepareNext(
                    PlaybackRequest(
                        url = streamUrl,
                        mediaId = nextTrack.id.value,
                        replayGainMode = playbackSettings.replayGainMode.forEngine(playbackEngine),
                        replayGain = nextTrack.replayGain?.let { PlaybackReplayGain(it, ReplayGainSource.Provider) },
                    ),
                )
            }.onFailure {
                if (preparedNextTrackId == nextTrack.id.value) {
                    preparedNextTrackId = null
                }
            }
        }
    }

    fun loadLyrics(track: Track) {
        val activeProvider = provider ?: return
        if (lyricsByTrackId.containsKey(track.id.value) || lyricsStatusByTrackId[track.id.value] != null) return
        lyricsStatusByTrackId = lyricsStatusByTrackId + (track.id.value to "Grabbing lyrics")
        scope.launch {
            runCatching {
                val sourceId = activeSourceId
                val quality = currentStreamQuality()
                val audioFile = if (sourceId != null) {
                    storage.downloadedAudioFile(sourceId, track.id, quality)?.file
                        ?: storage.cachedAudioFile(sourceId, track.id, quality)?.file
                } else {
                    null
                }
                val embeddedLyrics = audioFile?.let(::embeddedLyricsFromAudioFile)
                val localLyrics = activeProvider.lyrics(track.id)
                val onlineLyrics = if (
                    playbackSettings.lrclibLyricsEnabled &&
                    listOf(localLyrics, embeddedLyrics).none { it?.synced == true }
                ) {
                    lrclibLyricsClient.lyrics(track)
                } else {
                    null
                }
                selectPreferredLyrics(
                    providerLyrics = localLyrics,
                    embeddedLyrics = embeddedLyrics,
                    onlineLyrics = onlineLyrics,
                )
            }
                .onSuccess { lyrics ->
                    lyricsByTrackId = lyricsByTrackId + (track.id.value to lyrics)
                    lyricsStatusByTrackId = lyricsStatusByTrackId + (track.id.value to null)
                    activeSourceId?.let { sourceId ->
                        storage.recordSidecarStatus(
                            sourceId = sourceId,
                            trackId = track.id,
                            quality = StreamQuality.Original,
                            sidecarType = "lyrics",
                            success = true,
                        )
                    }
                }
                .onFailure { error ->
                    lyricsByTrackId = lyricsByTrackId + (track.id.value to null)
                    val message = error.message ?: "Lyrics unavailable"
                    lyricsStatusByTrackId = lyricsStatusByTrackId + (track.id.value to message)
                    activeSourceId?.let { sourceId ->
                        storage.recordSidecarStatus(
                            sourceId = sourceId,
                            trackId = track.id,
                            quality = StreamQuality.Original,
                            sidecarType = "lyrics",
                            success = false,
                            errorMessage = message,
                        )
                    }
                }
        }
    }

    suspend fun ensureWaveform(
        activeProvider: NavidromeProvider,
        sourceId: String?,
        track: Track,
    ): AudioWaveform? {
        val quality = currentStreamQuality()
        if (sourceId != null) {
            storage.cachedAudioWaveform(sourceId, track.id, quality)?.let { return it }
        }
        waveformAnalyzer.applyTlsSettings(activeTlsSettings)
        AndroidPlaybackTls.applyDefaults(activeTlsSettings)
        val localFile = if (sourceId != null) {
            storage.downloadedAudioFile(sourceId, track.id, quality)?.file
                ?: storage.cacheAudioTrack(sourceId, activeProvider, track, quality).file
        } else {
            null
        }
        val waveform = waveformAnalyzer.analyze(
            trackId = track.id.value,
            streamUrl = localFile?.toURI()?.toString()
                ?: activeProvider.streamUrl(StreamRequest(track.id, quality)),
        )
        if (waveform != null && sourceId != null && localFile != null) {
            storage.upsertAudioWaveform(
                sourceId = sourceId,
                trackId = track.id,
                quality = quality,
                audioFile = localFile,
                waveform = waveform,
            )
        }
        return waveform
    }

    fun startSidecarPrep(
        sessionToken: Long,
        activeProvider: NavidromeProvider,
        queue: PlaybackQueue,
    ) {
        sidecarPrepJob?.cancel()
        val tracksToPrep = queue.tracks
            .drop(queue.currentIndex.coerceAtLeast(0))
            .take(AndroidSidecarPrepDepth)
            .filterNot { it.isInternetRadioTrack() }
        if (tracksToPrep.isEmpty()) return
        val sourceId = activeSourceId
        sidecarPrepJob = scope.launch {
            tracksToPrep.forEach { track ->
                if (sessionToken != playbackSessionToken) return@launch
                val sidecarQuality = currentStreamQuality()
                runCatching {
                    ensureWaveform(activeProvider, sourceId, track)
                }.onSuccess { waveform ->
                    if (sourceId != null) {
                        storage.recordSidecarStatus(
                            sourceId = sourceId,
                            trackId = track.id,
                            quality = sidecarQuality,
                            sidecarType = "waveform",
                            success = true,
                        )
                    }
                    if (waveform != null && sessionToken == playbackSessionToken) {
                        waveformByTrackId = waveformByTrackId + (track.id.value to waveform)
                    }
                }.onFailure { error ->
                    if (sourceId != null) {
                        storage.recordSidecarStatus(
                            sourceId = sourceId,
                            trackId = track.id,
                            quality = sidecarQuality,
                            sidecarType = "waveform",
                            success = false,
                            errorMessage = error.message ?: "Waveform unavailable",
                        )
                    }
                }
                if (sessionToken != playbackSessionToken) return@launch
                if (playbackSettings.lrclibLyricsEnabled || lyricsVisible) {
                    loadLyrics(track)
                }
            }
        }
    }

    fun loadRelatedTracks(track: Track) {
        val activeProvider = provider ?: return
        if (!activeProvider.capabilities.supportsTrackRadio) {
            relatedTracks = emptyList()
            return
        }
        scope.launch {
            runCatching { RadioService(activeProvider, count = 20).trackRadio(track.id) }
                .onSuccess { relatedTracks = it }
                .onFailure { relatedTracks = emptyList() }
        }
    }

    fun beginPlaybackSession(): Long {
        playbackSessionToken += 1
        submittedPlayReportSessionToken = null
        sidecarPrepJob?.cancel()
        sidecarPrepJob = null
        preparedNextTrackId = null
        pendingSeekPositionSeconds = null
        pendingSeekIssuedAtMillis = null
        playbackProgress = PlaybackProgress.Unknown
        return playbackSessionToken
    }

    fun reportNowPlaying(track: Track) {
        val activeProvider = provider ?: return
        if (!activeProvider.capabilities.supportsPlayReporting || track.isInternetRadioTrack()) return
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    activeProvider.reportNowPlaying(track.id)
                }
            }
        }
    }

    fun maybeReportPlayed(progress: PlaybackProgress) {
        val activeProvider = provider ?: return
        if (!activeProvider.capabilities.supportsPlayReporting) return
        if (submittedPlayReportSessionToken == playbackSessionToken) return
        val track = nowPlaying ?: return
        if (track.isInternetRadioTrack()) return
        val positionSeconds = progress.positionSeconds ?: return
        val durationSeconds = progress.durationSeconds ?: track.durationSeconds?.toDouble()
        if (positionSeconds < playReportThresholdSeconds(durationSeconds)) return

        val activeSessionToken = playbackSessionToken
        submittedPlayReportSessionToken = activeSessionToken
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    activeProvider.reportPlayed(track.id, System.currentTimeMillis())
                }
            }.onFailure {
                if (submittedPlayReportSessionToken == activeSessionToken) {
                    submittedPlayReportSessionToken = null
                }
            }
        }
    }

    fun handlePlaybackProgressChanged(sessionToken: Long, progress: PlaybackProgress) {
        if (sessionToken != playbackSessionToken) return
        if (progress.positionSeconds == null && progress.durationSeconds == null) {
            pendingSeekPositionSeconds = null
            pendingSeekIssuedAtMillis = null
            playbackProgress = PlaybackProgress.Unknown
            AndroidPlaybackNotificationControls.positionMillis = null
            AndroidPlaybackNotificationControls.durationMillis = null
            AndroidPlaybackForegroundService.updateProgress(context, null, null)
            return
        }
        val pendingSeek = pendingSeekPositionSeconds
        val pendingSeekIssuedAt = pendingSeekIssuedAtMillis
        val progressPosition = progress.positionSeconds
        if (
            pendingSeek != null &&
            pendingSeekIssuedAt != null &&
            progressPosition != null &&
            abs(progressPosition - pendingSeek) > PendingSeekToleranceSeconds &&
            System.currentTimeMillis() - pendingSeekIssuedAt < PendingSeekStaleProgressWindowMillis
        ) {
            return
        }
        if (
            pendingSeek != null &&
            (progressPosition == null ||
                pendingSeekIssuedAt == null ||
                abs(progressPosition - pendingSeek) <= PendingSeekToleranceSeconds ||
                System.currentTimeMillis() - pendingSeekIssuedAt >= PendingSeekStaleProgressWindowMillis)
        ) {
            pendingSeekPositionSeconds = null
            pendingSeekIssuedAtMillis = null
        }
        playbackProgress = progress.mergeForAndroidPlayback(playbackProgress)
        maybeReportPlayed(playbackProgress)
        val positionMillis = playbackProgress.positionSeconds?.secondsToMillis()
        val durationMillis = playbackProgress.durationSeconds
            ?.secondsToMillis()
            ?: nowPlaying?.durationSeconds?.toDouble()?.secondsToMillis()
        AndroidPlaybackNotificationControls.positionMillis = positionMillis
        AndroidPlaybackNotificationControls.durationMillis = durationMillis
        val nowMillis = System.currentTimeMillis()
        if (nowMillis - lastAndroidAutoProgressPublishAtMillis >= AndroidAutoProgressPublishIntervalMillis) {
            lastAndroidAutoProgressPublishAtMillis = nowMillis
            AndroidPlaybackForegroundService.updateProgress(context, positionMillis, durationMillis)
        }
        prepareNextIfNeeded(sessionToken, playbackProgress)
    }

    var playAdjacentTrackAction: (Int) -> Unit = {}
    var restorePlaybackSessionAction: (String) -> Boolean = { false }

    fun savePlaybackSession() {
        val sourceId = activeSourceId ?: return
        val station = nowPlayingStation
        if (station != null) {
            storage.savePlaybackSession(
                sourceId = sourceId,
                session = PlaybackSessionSettings.fromInternetRadioStation(station),
            )
            android.util.Log.i("NaviampSession", "Saved station source=$sourceId name=${station.name}")
            return
        }

        val currentTrack = nowPlaying ?: return
        val queue = playbackQueue.takeIf { it.current?.id == currentTrack.id }
            ?: PlaybackQueue(tracks = listOf(currentTrack), currentIndex = 0)
        storage.savePlaybackSession(
            sourceId = sourceId,
            session = PlaybackSessionSettings.fromTracks(
                tracks = queue.tracks,
                currentIndex = queue.currentIndex,
                positionSeconds = playbackProgress.positionSeconds,
            ),
        )
        android.util.Log.i("NaviampSession", "Saved track source=$sourceId title=${currentTrack.title} queue=${queue.tracks.size} index=${queue.currentIndex} position=${playbackProgress.positionSeconds}")
    }

    fun savePlaybackSessionThrottled(force: Boolean = false) {
        if (activeSourceId == null || (nowPlaying == null && nowPlayingStation == null)) return
        val now = System.currentTimeMillis()
        if (!force && now - lastPlaybackSessionSaveAtMillis < AndroidPlaybackSessionSaveIntervalMillis) return
        lastPlaybackSessionSaveAtMillis = now
        savePlaybackSession()
    }

    fun playTrack(
        track: Track,
        queue: List<Track>? = null,
        openNowPlaying: Boolean = true,
        startPositionSeconds: Double? = null,
    ) {
        android.util.Log.i("NaviampBass", "playTrack requested id=${track.id.value} title=${track.title}")
        val activeProvider = provider
        if (activeProvider == null) {
            status = "Connect before playing a track."
            return
        }
        val nextQueue = queue
            ?.takeIf { tracks -> tracks.any { it.id == track.id } }
            ?: activeQueue().takeIf { tracks -> tracks.any { it.id == track.id } }
            ?: listOf(track)
        scope.launch {
            status = "Loading ${track.title}..."
            val streamQuality = currentStreamQuality()
            val providerStartPositionSeconds = startPositionSeconds
                ?.takeIf { streamQuality is StreamQuality.Transcoded }
            val engineStartPositionSeconds = startPositionSeconds
                ?.takeIf { streamQuality !is StreamQuality.Transcoded }
            runCatching {
                activeProvider.streamUrl(
                    StreamRequest(
                        trackId = track.id,
                        quality = streamQuality,
                        startPositionSeconds = providerStartPositionSeconds,
                    ),
                )
            }.onSuccess { streamUrl ->
                playbackEngine.applyTlsSettings(activeTlsSettings)
                playbackQueue = PlaybackQueue(
                    tracks = nextQueue,
                    currentIndex = nextQueue.indexOfFirst { it.id == track.id }.takeIf { it >= 0 } ?: 0,
                )
                shuffledUpNextSnapshot = null
                nowPlaying = track
                AndroidPlaybackNotificationControls.canFavorite =
                    activeProvider.capabilities.supportsTrackFavorites
                AndroidPlaybackNotificationControls.isFavorite = track.favoritedAtIso8601 != null
                nowPlayingStation = null
                nowPlayingStreamMetadata = PlaybackStreamMetadata()
                savePlaybackSessionThrottled(force = true)
                if (openNowPlaying) {
                    nowPlayingOpen = true
                }
                val sessionToken = beginPlaybackSession()
                reportNowPlaying(track)
                loadRelatedTracks(track)
                if (lyricsVisible && nowPlayingOpen) loadLyrics(track)
                startSidecarPrep(sessionToken, activeProvider, playbackQueue)
                playbackEngine.updateNotificationMetadata(
                    title = track.title,
                    subtitle = track.artistName,
                    coverArtUrl = track.coverArtUrl(activeProvider),
                )
                playbackEngine.play(
                    scope = scope,
                    request = PlaybackRequest(
                        url = streamUrl,
                        mediaId = track.id.value,
                        replayGainMode = playbackSettings.replayGainMode.forEngine(playbackEngine),
                        replayGain = track.replayGain?.let { PlaybackReplayGain(it, ReplayGainSource.Provider) },
                        startPositionSeconds = engineStartPositionSeconds,
                    ),
                    onStateChanged = { state ->
                        playbackState = state
                        when (state) {
                            PlaybackState.Finished -> playAdjacentTrackAction(1)
                            is PlaybackState.Error -> status = state.message
                            else -> Unit
                        }
                    },
                    onProgressChanged = { progress -> handlePlaybackProgressChanged(sessionToken, progress) },
                )
                status = "Loading ${track.title}..."
            }.onFailure { error ->
                status = error.message ?: "Playback failed."
            }
        }
    }

    fun playInternetRadioStation(station: InternetRadioStation) {
        val sessionToken = beginPlaybackSession()
        nowPlaying = null
        AndroidPlaybackNotificationControls.canFavorite = false
        AndroidPlaybackNotificationControls.isFavorite = false
        nowPlayingStation = station
        nowPlayingStreamMetadata = PlaybackStreamMetadata()
        nowPlayingOpen = true
        status = "Loading ${station.name}..."
        savePlaybackSessionThrottled(force = true)
        playbackEngine.applyTlsSettings(activeTlsSettings)
        playbackEngine.updateNotificationMetadata(
            title = station.name,
            subtitle = "Internet radio",
            coverArtUrl = null,
        )
        scope.launch {
            runCatching {
                resolveInternetRadioStreamUrl(station.streamUrl.trim())
            }.onSuccess { streamUrl ->
                playbackEngine.play(
                    scope = scope,
                    request = PlaybackRequest(streamUrl),
                    onStateChanged = { state ->
                        playbackState = state
                        if (state is PlaybackState.Error) {
                            status = state.message
                        }
                    },
                    onProgressChanged = { progress -> handlePlaybackProgressChanged(sessionToken, progress) },
                    onMetadataChanged = { metadata ->
                        nowPlayingStreamMetadata = metadata
                        metadata.title?.takeIf { it.isNotBlank() }?.let { streamTitle ->
                            playbackEngine.updateNotificationMetadata(
                                title = streamTitle,
                                subtitle = station.name,
                                coverArtUrl = null,
                            )
                        }
                    },
                )
            }.onFailure { error ->
                status = error.message ?: "Radio stream failed."
            }
        }
    }

    fun performSeek(positionSeconds: Double) {
        restoredStartPositionSeconds = null
        val durationSeconds = playbackProgress.durationSeconds ?: nowPlaying?.durationSeconds?.toDouble()
        playbackProgress = PlaybackProgress(
            positionSeconds = positionSeconds.coerceAtLeast(0.0),
            durationSeconds = durationSeconds,
        )
        val positionMillis = playbackProgress.positionSeconds?.secondsToMillis()
        val durationMillis = playbackProgress.durationSeconds?.secondsToMillis()
        AndroidPlaybackNotificationControls.positionMillis = positionMillis
        AndroidPlaybackNotificationControls.durationMillis = durationMillis
        AndroidPlaybackForegroundService.updateProgress(context, positionMillis, durationMillis)
        pendingSeekPositionSeconds = positionSeconds
        pendingSeekIssuedAtMillis = System.currentTimeMillis()
        val currentTrack = nowPlaying
        if (currentTrack != null && currentStreamQuality() is StreamQuality.Transcoded) {
            playTrack(
                track = currentTrack,
                queue = playbackQueue.tracks.takeIf { it.isNotEmpty() },
                openNowPlaying = false,
                startPositionSeconds = positionSeconds,
            )
            return
        }
        playbackEngine.seek(positionSeconds)
    }

    fun playAdjacentTrack(offset: Int) {
        val currentTrack = nowPlaying ?: return
        if (
            offset < 0 &&
            playbackSettings.previousButtonBehavior == PreviousButtonBehavior.RestartThenPrevious &&
            (playbackProgress.positionSeconds ?: 0.0) > 3.0
        ) {
            performSeek(0.0)
            return
        }
        val knownTracks = activeQueue()
        val currentIndex = knownTracks.indexOfFirst { it.id == currentTrack.id }
        if (currentIndex < 0) return
        val nextIndex = when {
            repeatMode == RepeatMode.Track -> currentIndex
            offset > 0 && currentIndex == knownTracks.lastIndex && repeatMode == RepeatMode.Queue -> 0
            offset < 0 && currentIndex == 0 && repeatMode == RepeatMode.Queue -> knownTracks.lastIndex
            else -> currentIndex + offset
        }
        val nextTrack = knownTracks.getOrNull(nextIndex) ?: return
        playTrack(nextTrack, knownTracks, openNowPlaying = false)
    }
    playAdjacentTrackAction = ::playAdjacentTrack

    fun radioService(): RadioService? =
        provider?.let { RadioService(it, count = AndroidInitialSimilarRadioCount) }

    fun playRadioTracks(statusLabel: String, loadTracks: suspend (RadioService) -> List<Track>) {
        val activeProvider = provider ?: return
        val service = RadioService(activeProvider)
        scope.launch {
            status = "Starting $statusLabel..."
            runCatching { loadTracks(service) }
                .onSuccess { radioTracks ->
                    val queue = radioTracks.distinctBy { it.id }
                    val firstTrack = queue.firstOrNull()
                    if (firstTrack == null) {
                        status = "No tracks found for $statusLabel."
                    } else {
                        playTrack(firstTrack, queue)
                    }
                }
                .onFailure { error -> status = error.message ?: "Could not start $statusLabel." }
        }
    }

    fun generatedRadioQueue(seedTrack: Track, fetchedTracks: List<Track>): List<Track> =
        (listOf(seedTrack) + fetchedTracks).distinctBy { it.id }

    fun appendGeneratedRadioTracks(seedTrack: Track, fetchedTracks: List<Track>) {
        if (nowPlaying?.id != seedTrack.id) return
        val existingTrackIds = playbackQueue.tracks.map { it.id }.toSet()
        val newTracks = generatedRadioQueue(seedTrack, fetchedTracks).filterNot { track ->
            track.id in existingTrackIds
        }
        if (newTracks.isNotEmpty()) {
            playbackQueue = playbackQueue.copy(tracks = playbackQueue.tracks + newTracks)
        }
    }

    fun startSeededRadio(
        statusLabel: String,
        seedTrack: Track,
        loadRest: suspend (RadioService) -> List<Track>,
    ) {
        val activeProvider = provider ?: return
        val seedQueue = listOf(seedTrack)
        playTrack(seedTrack, seedQueue)
        scope.launch {
            runCatching { loadRest(RadioService(activeProvider, count = AndroidInitialSimilarRadioCount)) }
                .onSuccess { fetchedTracks ->
                    val queue = generatedRadioQueue(seedTrack, fetchedTracks)
                        .ifEmpty { seedQueue }
                    if (nowPlaying?.id == seedTrack.id) {
                        playbackQueue = PlaybackQueue(tracks = queue, currentIndex = 0)
                    }
                    status = "Building $statusLabel queue..."
                }
                .onFailure { error ->
                    if (nowPlaying?.id == seedTrack.id) {
                        status = error.message ?: "Could not build $statusLabel."
                    }
                }

            AndroidSimilarRadioExpansionCounts.forEach { count ->
                if (nowPlaying?.id != seedTrack.id) return@launch
                val fetchedTracks = runCatching {
                    loadRest(RadioService(activeProvider, count = count))
                }.getOrElse {
                    return@forEach
                }
                appendGeneratedRadioTracks(seedTrack, fetchedTracks)
                status = "Building $statusLabel queue (${playbackQueue.tracks.size} tracks)..."
            }
            if (nowPlaying?.id == seedTrack.id) {
                status = "Playing $statusLabel."
            }
        }
    }

    fun startTrackRadio(track: Track) {
        startSeededRadio("${track.title} radio", track) { radioService ->
            radioService.trackRadio(track.id)
        }
    }

    fun startAlbumRadio(album: Album, loadedAlbumTracks: List<Track> = emptyList()) {
        val service = radioService() ?: return
        scope.launch {
            status = "Starting ${album.title} radio..."
            val seedTrack = service.albumSeed(album, loadedAlbumTracks)
            if (seedTrack == null) {
                status = "${album.title} did not return any tracks."
            } else {
                startSeededRadio("${album.title} radio", seedTrack) { radioService ->
                    radioService.albumRadio(album.id, loadedAlbumTracks)
                }
            }
        }
    }

    lateinit var openArtistDetailsFromBack: (
        app.naviamp.domain.ArtistId,
        String?,
        Boolean,
    ) -> Unit

    fun closeActiveDetail() {
        val previousArtist = contentState.artistDetail
            ?.let { artistDetailBackStack.lastOrNull() }
        if (previousArtist != null) {
            artistDetailBackStack = artistDetailBackStack.dropLast(1)
            openArtistDetailsFromBack(previousArtist.id, previousArtist.name, false)
        } else {
            contentState = contentState.clearDetails()
            artistDetailBackStack = emptyList()
        }
    }

    fun closeActivePlaylist() {
        contentState = contentState.copy(
            selectedPlaylist = null,
            selectedPlaylistTracks = emptyList(),
        )
    }

    fun handleAndroidBack() {
        when {
            nowPlayingOpen -> nowPlayingOpen = false
            albumDetail != null || artistDetail != null -> closeActiveDetail()
            selectedPlaylist != null -> closeActivePlaylist()
            editingConnection && provider != null -> editingConnection = false
            navigationState.route != NaviampRoute.Home -> {
                navigationState = navigationState.copy(route = NaviampRoute.Home)
                contentState = contentState.clearDetails()
            }
        }
    }

    val handlesAndroidBack = nowPlayingOpen ||
        albumDetail != null ||
        artistDetail != null ||
        selectedPlaylist != null ||
        (editingConnection && provider != null) ||
        navigationState.route != NaviampRoute.Home

    BackHandler(enabled = handlesAndroidBack) {
        handleAndroidBack()
    }

    fun updateNotificationFavoriteState(track: Track? = nowPlaying) {
        AndroidPlaybackNotificationControls.canFavorite =
            track != null && provider?.capabilities?.supportsTrackFavorites == true
        AndroidPlaybackNotificationControls.isFavorite = track?.favoritedAtIso8601 != null
    }

    fun applyTrackMetadataUpdate(updatedTrack: Track) {
        val updatedNowPlaying = nowPlaying?.let { if (it.id == updatedTrack.id) updatedTrack else it }
        nowPlaying = updatedNowPlaying
        tracks = tracks.map { if (it.id == updatedTrack.id) updatedTrack else it }
        contentState = contentState.copy(
            searchResults = searchResults.copy(
                tracks = searchResults.tracks.map { if (it.id == updatedTrack.id) updatedTrack else it },
            ),
            albumDetail = albumDetail?.copy(
                tracks = albumDetail.tracks.map { if (it.id == updatedTrack.id) updatedTrack else it },
            ),
        )
        if (updatedNowPlaying?.id == updatedTrack.id) {
            updateNotificationFavoriteState(updatedNowPlaying)
            playbackEngine.updateNotificationMetadata(
                title = updatedNowPlaying.title,
                subtitle = updatedNowPlaying.artistName,
                coverArtUrl = provider?.let { updatedNowPlaying.coverArtUrl(it) },
            )
        }
    }

    fun toggleCurrentFavorite() {
        val activeProvider = provider ?: return
        val currentTrack = nowPlaying ?: return
        if (!activeProvider.capabilities.supportsTrackFavorites) return
        scope.launch {
            val favorite = currentTrack.favoritedAtIso8601 == null
            AndroidPlaybackNotificationControls.isFavorite = favorite
            runCatching {
                activeProvider.setTrackFavorite(currentTrack.id, favorite)
            }.onSuccess {
                applyTrackMetadataUpdate(
                    currentTrack.copy(favoritedAtIso8601 = if (favorite) "android-local" else null),
                )
            }.onFailure { error ->
                AndroidPlaybackNotificationControls.isFavorite = !favorite
                status = error.message ?: "Could not update favorite."
            }
        }
    }

    fun handleAutoPlayPauseCommand(): Boolean {
        return when (playbackState) {
            PlaybackState.Playing -> {
                playbackEngine.pause()
                true
            }
            PlaybackState.Paused -> {
                playbackEngine.resume()
                true
            }
            else -> {
                if (nowPlaying == null && nowPlayingStation == null) {
                    activeSourceId?.let { sourceId -> restorePlaybackSessionAction(sourceId) }
                }
                nowPlayingStation?.let { station ->
                    playInternetRadioStation(station)
                    return true
                }
                val currentTrack = nowPlaying ?: return false
                playTrack(
                    track = currentTrack,
                    queue = playbackQueue.tracks.takeIf { it.isNotEmpty() },
                    openNowPlaying = false,
                    startPositionSeconds = restoredStartPositionSeconds,
                )
                restoredStartPositionSeconds = null
                true
            }
        }
    }

    fun handleAndroidAutoCommand(command: String): Boolean =
        when (command) {
            AndroidAutoPlaybackControls.CommandPlayPause -> handleAutoPlayPauseCommand()
            AndroidAutoPlaybackControls.CommandPrevious -> {
                playAdjacentTrack(-1)
                nowPlaying != null
            }
            AndroidAutoPlaybackControls.CommandNext -> {
                playAdjacentTrack(1)
                nowPlaying != null
            }
            else -> false
        }.also { handled ->
            android.util.Log.i("NaviampAutoCommand", "Handled Auto command=$command handled=$handled state=$playbackState nowPlaying=${nowPlaying?.title}")
        }

    AndroidPlaybackNotificationControls.onPlayPause = {
        handleAutoPlayPauseCommand()
    }
    AndroidPlaybackNotificationControls.onPrevious = { playAdjacentTrack(-1) }
    AndroidPlaybackNotificationControls.onNext = { playAdjacentTrack(1) }
    AndroidPlaybackNotificationControls.onToggleFavorite = { toggleCurrentFavorite() }
    AndroidPlaybackNotificationControls.onStop = { playbackEngine.stop() }
    AndroidPlaybackNotificationControls.onSeekTo = seekHandler@{ positionMillis ->
        val normalizedPositionMillis = normalizeAndroidAutoSeekPositionMillis(
            rawPositionMillis = positionMillis,
            durationSeconds = playbackProgress.durationSeconds ?: nowPlaying?.durationSeconds?.toDouble(),
        )
        android.util.Log.i(
            "NaviampAutoSeek",
            "Auto seek raw=$positionMillis normalized=$normalizedPositionMillis duration=${playbackProgress.durationSeconds ?: nowPlaying?.durationSeconds?.toDouble()}",
        )
        if (
            normalizedPositionMillis == 0L &&
            (playbackProgress.positionSeconds ?: 0.0) > AndroidAutoIgnoreZeroSeekAfterSeconds
        ) {
            android.util.Log.i(
                "NaviampAutoSeek",
                "Ignoring zero seek while currentPosition=${playbackProgress.positionSeconds}",
            )
            return@seekHandler
        }
        performSeek(normalizedPositionMillis / 1_000.0)
    }

    fun playAndroidAutoMediaId(mediaId: String): Boolean {
        val sourceId = activeSourceId
        val handled = when {
            mediaId == AndroidAutoPlaybackControls.MediaIdNowPlaying -> handleAutoPlayPauseCommand()
            mediaId == AndroidAutoPlaybackControls.MediaIdRadioLibrary -> {
                playRadioTracks("Library Radio") { radioService -> radioService.libraryRadio() }
                true
            }
            mediaId.startsWith(AndroidAutoPlaybackControls.MediaIdTrackPrefix) && sourceId != null -> {
                val trackId = Uri.decode(mediaId.removePrefix(AndroidAutoPlaybackControls.MediaIdTrackPrefix))
                storage.libraryTrack(sourceId, TrackId(trackId))?.let { track ->
                    val queue = track.albumId?.let { storage.libraryTracksForAlbum(sourceId, it, 200) }
                        ?.takeIf { tracks -> tracks.any { it.id == track.id } }
                        ?: track.artistId?.let { storage.libraryTracksForArtist(sourceId, it, 200) }
                            ?.takeIf { tracks -> tracks.any { it.id == track.id } }
                    playTrack(track, queue, openNowPlaying = false)
                    true
                } ?: run {
                    status = "Track is not available in the local library index."
                    false
                }
            }
            mediaId.startsWith(AndroidAutoPlaybackControls.MediaIdDownloadPrefix) && sourceId != null -> {
                val trackId = Uri.decode(mediaId.removePrefix(AndroidAutoPlaybackControls.MediaIdDownloadPrefix))
                val download = storage.downloadedTracks(sourceId).firstOrNull { it.track.id.value == trackId }
                if (download != null) {
                    val queue = storage.downloadedTracks(sourceId).map { it.track }
                    playTrack(download.track, queue, openNowPlaying = false)
                    true
                } else {
                    status = "Downloaded track is not available."
                    false
                }
            }
            else -> {
                status = "Open Naviamp on your phone before starting Android Auto playback."
                false
            }
        }
        android.util.Log.i("NaviampAutoCommand", "Handled Auto mediaId=$mediaId handled=$handled state=$playbackState nowPlaying=${nowPlaying?.title}")
        return handled
    }
    AndroidAutoPlaybackControls.onPlayMediaId = { mediaId ->
        if (!playAndroidAutoMediaId(mediaId)) {
            pendingAutoPlayMediaId = mediaId
        }
    }

    LaunchedEffect(pendingAutoPlayMediaId, provider, activeSourceId) {
        val mediaId = pendingAutoPlayMediaId ?: return@LaunchedEffect
        if (provider == null || activeSourceId == null) return@LaunchedEffect
        if (playAndroidAutoMediaId(mediaId)) {
            pendingAutoPlayMediaId = null
            onAutoPlayMediaIdConsumed()
        }
    }

    LaunchedEffect(pendingAutoCommand, provider, activeSourceId, nowPlaying?.id, playbackQueue) {
        val command = pendingAutoCommand ?: return@LaunchedEffect
        if (provider == null || activeSourceId == null) return@LaunchedEffect
        if (handleAndroidAutoCommand(command)) {
            pendingAutoCommand = null
            onAutoCommandConsumed()
        }
    }

    LaunchedEffect(nowPlaying?.id, nowPlaying?.favoritedAtIso8601, provider?.capabilities?.supportsTrackFavorites) {
        updateNotificationFavoriteState()
    }

    fun localArtistDetail(
        sourceId: String,
        artistId: app.naviamp.domain.ArtistId,
        fallbackName: String?,
    ): ArtistDetails? {
        val tracks = storage.libraryTracksForArtist(sourceId, artistId, limit = 1_000)
        if (tracks.isEmpty()) return fallbackName?.let { name ->
            ArtistDetails(artist = Artist(artistId, name), albums = emptyList(), info = null)
        }
        val artistName = fallbackName ?: tracks.firstOrNull()?.artistName ?: "Unknown Artist"
        val albums = tracks
            .groupBy { track -> track.albumId?.value ?: track.albumTitle.orEmpty() }
            .mapNotNull { (_, albumTracks) ->
                val first = albumTracks.firstOrNull() ?: return@mapNotNull null
                val albumId = first.albumId ?: return@mapNotNull null
                Album(
                    id = albumId,
                    title = first.albumTitle ?: "Unknown Album",
                    artistName = first.artistName,
                    coverArtId = first.coverArtId,
                    recentlyAddedAtIso8601 = null,
                    releaseYear = first.albumReleaseYear,
                )
            }
            .sortedWith(compareBy<Album> { it.releaseYear ?: Int.MAX_VALUE }.thenBy { it.title.lowercase() })
        return ArtistDetails(
            artist = Artist(artistId, artistName),
            albums = albums,
            info = null,
        )
    }

    fun openArtistDetails(
        artistId: app.naviamp.domain.ArtistId,
        fallbackName: String? = null,
        pushCurrentArtist: Boolean = true,
    ) {
        val activeProvider = provider ?: return
        val sourceId = activeSourceId
        if (pushCurrentArtist) {
            contentState.artistDetail
                ?.artist
                ?.takeIf { currentArtist -> currentArtist.id != artistId }
                ?.let { currentArtist -> artistDetailBackStack = artistDetailBackStack + currentArtist }
        }
        scope.launch {
            status = "Loading ${fallbackName ?: "artist"}..."
            runCatching { activeProvider.artist(artistId) }
                .recoverCatching { error ->
                    val fallbackDetail = sourceId?.let { localArtistDetail(it, artistId, fallbackName) }
                    fallbackDetail ?: throw error
                }
                .onSuccess { detail ->
                    contentState = contentState.showArtist(detail)
                    nowPlayingOpen = false
                    navigationState = navigationState.copy(route = NaviampRoute.Library)
                    status = if (detail.albums.isEmpty()) {
                        "No albums found for ${detail.artist.name}."
                    } else {
                        "Connected."
                    }
                    if (sourceId != null) {
                        artistPopularTracksStatusByArtistId =
                            artistPopularTracksStatusByArtistId + (artistId.value to "Loading popular tracks...")
                        scope.launch(Dispatchers.IO) {
                            runCatching {
                                popularTracksService.popularTracks(
                                    sourceId = sourceId,
                                    artist = detail.artist,
                                    limit = PopularTracksFetchLimit,
                                )
                            }.onSuccess { matches ->
                                val matchedTracks = matches
                                    .map { it.matchedTrack }
                                    .take(PopularTracksDisplayLimit)
                                withContext(Dispatchers.Main) {
                                    artistPopularTracksByArtistId =
                                        artistPopularTracksByArtistId + (artistId.value to matchedTracks)
                                    artistPopularTracksStatusByArtistId =
                                        artistPopularTracksStatusByArtistId + (
                                            artistId.value to matchedTracks
                                                .takeIf { it.isEmpty() }
                                                ?.let { "No popular tracks matched songs in your library." }
                                            )
                                }
                            }.onFailure { error ->
                                withContext(Dispatchers.Main) {
                                    artistPopularTracksStatusByArtistId =
                                        artistPopularTracksStatusByArtistId + (
                                            artistId.value to "Popular tracks unavailable: ${error.message ?: "unknown error"}"
                                            )
                                }
                            }
                        }
                    } else {
                        artistPopularTracksStatusByArtistId =
                            artistPopularTracksStatusByArtistId + (
                                artistId.value to "Popular tracks unavailable: no connected media source."
                                )
                    }
                }
                .onFailure { error -> status = error.message ?: "Artist failed to load." }
        }
    }
    openArtistDetailsFromBack = ::openArtistDetails

    fun findSimilarArtists(artistId: app.naviamp.domain.ArtistId, artistName: String) {
        artistSimilarArtistsStatusByArtistId = artistSimilarArtistsStatusByArtistId + (artistId.value to "Finding similar artists...")
        artistSimilarArtistsByArtistId = artistSimilarArtistsByArtistId - artistId.value
        scope.launch {
            runCatching {
                similarArtistsService.similarArtists(
                    artistName = artistName,
                    limit = SimilarArtistsFetchLimit,
                )
            }.onSuccess { artists ->
                artistSimilarArtistsByArtistId = artistSimilarArtistsByArtistId + (
                    artistId.value to artists.take(SimilarArtistsDisplayLimit)
                    )
                artistSimilarArtistsStatusByArtistId = artistSimilarArtistsStatusByArtistId + (
                    artistId.value to if (artists.isEmpty()) "No similar artists found." else null
                    )
            }.onFailure { error ->
                artistSimilarArtistsStatusByArtistId = artistSimilarArtistsStatusByArtistId + (
                    artistId.value to "Similar artists unavailable: ${error.message ?: "unknown error"}"
                    )
            }
        }
    }

    fun openExternalArtistUrl(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { error ->
            status = "Could not open external artist page: ${error.message ?: "unknown error"}"
        }
    }

    suspend fun refreshPlaylistDetailsFromServer(
        activeProvider: NavidromeProvider,
        playlist: Playlist,
        showLoadingStatus: Boolean,
    ) {
        if (showLoadingStatus) status = "Loading ${playlist.name}..."
        val playlists = withContext(Dispatchers.IO) { activeProvider.playlists(limit = 500) }
        val refreshedPlaylist = playlists.firstOrNull { it.id == playlist.id } ?: playlist
        val playlistTracks = withContext(Dispatchers.IO) { activeProvider.playlistTracks(refreshedPlaylist.id) }
        val displayPlaylist = refreshedPlaylist.copy(trackCount = playlistTracks.size)
        homeState = homeState.copy(
            playlists = playlists.map {
                if (it.id == displayPlaylist.id) displayPlaylist else it
            },
        )
        playlistTracksById = playlistTracksById + (displayPlaylist.id to playlistTracks)
        if (selectedPlaylist?.id == playlist.id) {
            contentState = contentState.showPlaylist(displayPlaylist, playlistTracks)
            tracks = playlistTracks
            if (showLoadingStatus) status = "Connected."
        }
    }

    LaunchedEffect(provider, selectedPlaylist?.id) {
        val activeProvider = provider ?: return@LaunchedEffect
        val playlist = selectedPlaylist ?: return@LaunchedEffect

        while (true) {
            delay(AndroidPlaylistDetailRefreshIntervalMillis)
            runCatching {
                refreshPlaylistDetailsFromServer(
                    activeProvider = activeProvider,
                    playlist = playlist,
                    showLoadingStatus = false,
                )
            }
        }
    }

    fun openPlaylistDetails(playlist: Playlist) {
        val activeProvider = provider ?: return
        contentState = contentState.showPlaylist(playlist)
        navigationState = navigationState.copy(route = NaviampRoute.Playlists)
        nowPlayingOpen = false
        recentPlaylistIds = (listOf(playlist.id) + recentPlaylistIds.filterNot { it == playlist.id }).take(20)
        scope.launch {
            status = "Loading ${playlist.name}..."
            runCatching {
                refreshPlaylistDetailsFromServer(
                    activeProvider = activeProvider,
                    playlist = playlist,
                    showLoadingStatus = false,
                )
            }.onSuccess {
                    status = "Connected."
                }
                .onFailure { error ->
                    contentState = contentState.showPlaylist(playlist)
                    status = error.message ?: "Playlist failed to load."
                }
        }
    }

    fun playPlaylist(playlist: Playlist, shuffle: Boolean) {
        val activeProvider = provider ?: return
        scope.launch {
            status = "Loading ${playlist.name}..."
            val playlistTracks = if (selectedPlaylist?.id == playlist.id && selectedPlaylistTracks.isNotEmpty()) {
                selectedPlaylistTracks
            } else {
                runCatching { activeProvider.playlistTracks(playlist.id) }
                    .onSuccess {
                        playlistTracksById = playlistTracksById + (playlist.id to it)
                        contentState = contentState.showPlaylist(playlist, it)
                        tracks = it
                    }
                    .getOrDefault(emptyList())
            }
            val queue = if (shuffle) playlistTracks.shuffled() else playlistTracks
            queue.firstOrNull()?.let { firstTrack ->
                recentPlaylistIds = (listOf(playlist.id) + recentPlaylistIds.filterNot { it == playlist.id }).take(20)
                playTrack(firstTrack, queue)
            } ?: run {
                status = "Playlist is empty."
            }
        }
    }

    fun renamePlaylist(playlist: Playlist, name: String) {
        val activeProvider = provider ?: return
        scope.launch {
            status = "Renaming ${playlist.name}..."
            runCatching {
                activeProvider.renamePlaylist(playlist.id, name.trim())
                activeProvider.playlists(limit = 500)
            }.onSuccess { playlists ->
                homeState = homeState.copy(playlists = playlists)
                selectedPlaylist?.let { current ->
                    if (current.id == playlist.id) {
                        val renamed = playlists.firstOrNull { it.id == playlist.id } ?: current.copy(name = name.trim())
                        contentState = contentState.copy(selectedPlaylist = renamed)
                    }
                }
                status = "Renamed playlist."
            }.onFailure { error ->
                status = error.message ?: "Could not rename playlist."
            }
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        val activeProvider = provider ?: return
        scope.launch {
            status = "Deleting ${playlist.name}..."
            runCatching {
                activeProvider.deletePlaylist(playlist.id)
                activeProvider.playlists(limit = 500)
            }.onSuccess { playlists ->
                homeState = homeState.copy(playlists = playlists)
                if (selectedPlaylist?.id == playlist.id) {
                    contentState = contentState.copy(
                        selectedPlaylist = null,
                        selectedPlaylistTracks = emptyList(),
                    )
                }
                playlistTracksById = playlistTracksById - playlist.id
                recentPlaylistIds = recentPlaylistIds.filterNot { it == playlist.id }
                status = "Deleted playlist."
            }.onFailure { error ->
                status = error.message ?: "Could not delete playlist."
            }
        }
    }

    fun preloadPlaylistTracks(activeProvider: NavidromeProvider, playlists: List<Playlist>) {
        scope.launch {
            playlists.take(100).forEach { playlist ->
                if (playlistTracksById[playlist.id].isNullOrEmpty()) {
                    runCatching { activeProvider.playlistTracks(playlist.id) }
                        .onSuccess { tracks ->
                            playlistTracksById = playlistTracksById + (playlist.id to tracks)
                        }
                }
            }
        }
    }

    fun refreshAndroidPlaylists() {
        val activeProvider = provider ?: return
        scope.launch {
            runCatching { activeProvider.playlists(limit = 500) }
                .onSuccess { playlists ->
                    homeState = homeState.copy(playlists = playlists)
                    preloadPlaylistTracks(activeProvider, playlists)
                }
        }
    }

    suspend fun saveSmartPlaylist(definition: SmartPlaylistDefinition) {
        val activeProvider = provider
            ?: throw IllegalStateException("Connect to Navidrome before saving smart playlists.")
        status = "Saving ${definition.name}..."
        try {
            val createdPlaylist = activeProvider.createSmartPlaylist(definition)
            val playlists = activeProvider.playlists(limit = 500)
            val refreshedPlaylist = playlists.firstOrNull { it.id == createdPlaylist.id }
                ?: playlists.firstOrNull { it.name == createdPlaylist.name }
                ?: createdPlaylist
            val refreshedTracks = activeProvider.playlistTracks(refreshedPlaylist.id)
            playlistTracksById = playlistTracksById + (refreshedPlaylist.id to refreshedTracks)
            val displayPlaylists = playlists.map { playlist ->
                if (playlist.id == refreshedPlaylist.id) playlist.copy(trackCount = refreshedTracks.size) else playlist
            }
            homeState = homeState.copy(playlists = displayPlaylists)
            preloadPlaylistTracks(activeProvider, playlists)
            status = "Saved smart playlist ${definition.name} with ${refreshedTracks.size} tracks."
        } catch (error: Exception) {
            status = error.message ?: "Could not save smart playlist."
            throw error
        }
    }

    fun addTrackToPlaylist(track: Track, playlist: NaviampPlaylistChoiceUi?, newPlaylistName: String? = null) {
        val activeProvider = provider ?: return
        playlistActionStatus = "Adding to playlist..."
        scope.launch {
            runCatching {
                if (playlist == null) {
                    activeProvider.createPlaylist(newPlaylistName.orEmpty(), listOf(track.id))
                } else {
                    val existingIds = activeProvider.playlistTracks(playlist.id).map { it.id }.toSet()
                    if (track.id !in existingIds) {
                        activeProvider.addTracksToPlaylist(playlist.id, listOf(track.id))
                    }
                }
                activeProvider.playlists(limit = 500)
            }.onSuccess { playlists ->
                homeState = homeState.copy(playlists = playlists)
                playlistActionStatus = null
                status = "Added ${track.title} to playlist."
            }.onFailure { error ->
                playlistActionStatus = error.message ?: "Could not add track to playlist."
                status = playlistActionStatus.orEmpty()
            }
        }
    }

    fun addTracksToPlaylist(
        tracksToAdd: List<Track>,
        playlist: NaviampPlaylistChoiceUi?,
        newPlaylistName: String? = null,
        label: String = "tracks",
    ) {
        val activeProvider = provider ?: return
        val uniqueTracks = tracksToAdd.distinctBy { it.id }
        if (uniqueTracks.isEmpty()) {
            status = "No tracks found."
            return
        }
        playlistActionStatus = "Adding $label to playlist..."
        scope.launch {
            runCatching {
                val ids = uniqueTracks.map { it.id }
                if (playlist == null) {
                    activeProvider.createPlaylist(newPlaylistName.orEmpty(), ids)
                } else {
                    val existingIds = activeProvider.playlistTracks(playlist.id).map { it.id }.toSet()
                    val missingIds = ids.filterNot { it in existingIds }
                    if (missingIds.isNotEmpty()) {
                        activeProvider.addTracksToPlaylist(playlist.id, missingIds)
                    }
                }
                activeProvider.playlists(limit = 500)
            }.onSuccess { playlists ->
                homeState = homeState.copy(playlists = playlists)
                playlistActionStatus = null
                status = "Added $label to playlist."
            }.onFailure { error ->
                playlistActionStatus = error.message ?: "Could not add $label to playlist."
                status = playlistActionStatus.orEmpty()
            }
        }
    }

    fun downloadTrack(track: Track) {
        val activeProvider = provider ?: return
        val sourceId = activeSourceId ?: return
        scope.launch {
            if (context.isActiveNetworkMobileData() && !playbackSettings.allowMobileDownloads) {
                downloadStatus = "Downloads over mobile data are disabled."
                status = downloadStatus.orEmpty()
                return@launch
            }
            val quality = currentDownloadQuality()
            downloadStatus = "Downloading ${track.title}..."
            status = downloadStatus.orEmpty()
            runCatching {
                withContext(Dispatchers.IO) {
                    storage.downloadAudioTrack(
                        sourceId = sourceId,
                        provider = activeProvider,
                        track = track,
                        quality = quality,
                        maxDownloadBytes = AndroidMaxDownloadBytes,
                    )
                }
            }.onSuccess {
                downloadRefreshToken += 1
                storageStats = withContext(Dispatchers.IO) { storage.stats() }
                downloadStatus = "Downloaded ${track.title}."
                status = downloadStatus.orEmpty()
            }.onFailure { error ->
                downloadStatus = error.message ?: "Could not download track."
                status = downloadStatus.orEmpty()
            }
        }
    }

    fun downloadTracks(tracksToDownload: List<Track>, label: String = "tracks") {
        val activeProvider = provider ?: return
        val sourceId = activeSourceId ?: return
        val uniqueTracks = tracksToDownload.distinctBy { it.id }
        if (uniqueTracks.isEmpty()) {
            status = "No tracks found."
            return
        }
        scope.launch {
            if (context.isActiveNetworkMobileData() && !playbackSettings.allowMobileDownloads) {
                downloadStatus = "Downloads over mobile data are disabled."
                status = downloadStatus.orEmpty()
                return@launch
            }
            val quality = currentDownloadQuality()
            downloadStatus = "Downloading $label..."
            status = downloadStatus.orEmpty()
            runCatching {
                withContext(Dispatchers.IO) {
                    uniqueTracks.forEach { track ->
                        storage.downloadAudioTrack(
                            sourceId = sourceId,
                            provider = activeProvider,
                            track = track,
                            quality = quality,
                            maxDownloadBytes = AndroidMaxDownloadBytes,
                        )
                    }
                }
            }.onSuccess {
                downloadRefreshToken += 1
                storageStats = withContext(Dispatchers.IO) { storage.stats() }
                downloadStatus = "Downloaded $label."
                status = downloadStatus.orEmpty()
            }.onFailure { error ->
                downloadStatus = error.message ?: "Could not download $label."
                status = downloadStatus.orEmpty()
            }
        }
    }

    fun removeDownload(download: NaviampDownloadedTrackUi) {
        val sourceId = activeSourceId ?: return
        scope.launch {
            val track = downloadedTracks.firstOrNull { it.track.id.value == download.track.id }?.track
                ?: findKnownTrack(download.track.id)
                ?: return@launch
            withContext(Dispatchers.IO) {
                storage.removeDownloadedAudioForTrack(sourceId, track.id)
            }
            downloadRefreshToken += 1
            storageStats = withContext(Dispatchers.IO) { storage.stats() }
            downloadStatus = "Removed ${track.title}."
            status = downloadStatus.orEmpty()
        }
    }

    fun restorePlaybackSession(sourceId: String): Boolean {
        val session = storage.loadPlaybackSession(sourceId)
        if (session == null) {
            android.util.Log.i("NaviampSession", "No playback session for source=$sourceId")
            return false
        }
        session.internetRadioStation?.toStation()?.let { station ->
            nowPlaying = null
            nowPlayingStation = station
            nowPlayingStreamMetadata = PlaybackStreamMetadata()
            playbackQueue = PlaybackQueue()
            playbackProgress = PlaybackProgress.Unknown
            restoredStartPositionSeconds = null
            nowPlayingOpen = true
            android.util.Log.i("NaviampSession", "Restored station source=$sourceId name=${station.name}")
            status = "Restored ${station.name}. Press play to resume."
            return true
        }

        val sessionTracks = session.toTracks()
        val currentTrack = session.currentTrack() ?: run {
            android.util.Log.i("NaviampSession", "Playback session had no current track source=$sourceId tracks=${session.tracks.size} index=${session.currentIndex}")
            return false
        }
        playbackQueue = PlaybackQueue(
            tracks = sessionTracks,
            currentIndex = session.currentIndex.coerceIn(sessionTracks.indices),
        )
        tracks = sessionTracks
        nowPlaying = currentTrack
        nowPlayingStation = null
        nowPlayingStreamMetadata = PlaybackStreamMetadata()
        nowPlayingOpen = true
        playbackProgress = PlaybackProgress(
            positionSeconds = session.positionSeconds,
            durationSeconds = currentTrack.durationSeconds?.toDouble(),
        )
        restoredStartPositionSeconds = session.positionSeconds
        loadRelatedTracks(currentTrack)
        android.util.Log.i("NaviampSession", "Restored track source=$sourceId title=${currentTrack.title} queue=${sessionTracks.size} position=${session.positionSeconds}")
        status = "Restored ${currentTrack.title}. Press play to resume."
        return true
    }
    restorePlaybackSessionAction = ::restorePlaybackSession

    fun startAndroidLibrarySync(force: Boolean = false) {
        val activeProvider = provider ?: return
        val sourceId = activeSourceId ?: return
        if (isLibrarySyncing) return
        if (!force && storage.libraryIndexStats(sourceId).hasUsableIndex) {
            libraryStatus = null
            return
        }
        isLibrarySyncing = true
        libraryStatus = "Starting library import..."
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    syncAndroidLibrary(sourceId, activeProvider, storage) { progress ->
                        withContext(Dispatchers.Main) {
                            if (progress.artists != null) {
                                homeState = homeState.copy(artists = progress.artists)
                            }
                            libraryStatus = progress.label
                            if (nowPlaying == null && nowPlayingStation == null) {
                                status = progress.label
                            }
                        }
                    }
                    activeProvider.libraryScanStatus()?.signature?.let { signature ->
                        storage.markLibraryScanChecked(sourceId, signature)
                    }
                }
            }.onSuccess {
                libraryStatus = null
                if (nowPlaying == null && nowPlayingStation == null) {
                    status = "Library refreshed."
                }
            }.onFailure { error ->
                libraryStatus = error.message ?: "Could not import library."
                status = libraryStatus.orEmpty()
            }
            isLibrarySyncing = false
        }
    }

    fun checkAndroidLibraryFreshness() {
        val activeProvider = provider ?: return
        val sourceId = activeSourceId ?: return
        if (isLibrarySyncing) return
        scope.launch {
            val freshness = withContext(Dispatchers.IO) {
                val scanStatus = activeProvider.libraryScanStatus()
                AndroidLibraryFreshness(
                    signature = scanStatus?.signature,
                    previousSignature = storage.mediaSource(sourceId)?.lastLibraryScanSignature,
                    scanning = scanStatus?.scanning == true,
                )
            }
            val signature = freshness.signature ?: return@launch
            when {
                freshness.previousSignature == null -> {
                    withContext(Dispatchers.IO) {
                        storage.markLibraryScanChecked(sourceId, signature)
                    }
                }
                freshness.previousSignature != signature -> {
                    libraryStatus = if (freshness.scanning) {
                        "Navidrome is scanning. Refresh library after the scan finishes."
                    } else {
                        "Library changed on server. Refresh library to import updates."
                    }
                }
                libraryStatus?.startsWith("Library changed on server") == true ||
                    libraryStatus?.startsWith("Navidrome is scanning") == true -> {
                    libraryStatus = null
                }
            }
        }
    }

    fun currentConnectionForm(): ConnectionFormState =
        ConnectionFormState(
            displayName = connectionName,
            serverUrl = serverUrl,
            username = username,
            password = password,
            skipTlsVerification = skipTlsVerification,
            customCertificatePath = customCertificatePath,
            clientCertificatePath = clientCertificatePath,
            clientCertificatePassword = clientCertificatePassword,
        )

    fun connectWithNavidromeConnection(connection: NavidromeConnection) {
        scope.launch {
            status = "Connecting..."
            runCatching {
                val tlsSettings = connection.tlsSettings
                val nextProvider = NavidromeProvider(connection)
                playbackEngine.applyTlsSettings(tlsSettings)
                AndroidPlaybackTls.applyDefaults(tlsSettings)
                validation = nextProvider.validateConnection()
                val mediaSource = storage.upsertNavidromeSource(
                    connection = connection,
                    cacheNamespace = nextProvider.cacheNamespace,
                    providerId = nextProvider.id.value,
                )
                homeState = loadBrowseState(nextProvider)
                preloadPlaylistTracks(nextProvider, homeState.playlists)
                provider = nextProvider
                activeSourceId = mediaSource.id
                activeTlsSettings = tlsSettings
                restorePlaybackSession(mediaSource.id)
            }.onSuccess {
                if (nowPlaying == null && nowPlayingStation == null) {
                    status = "Connected."
                }
                restoringConnection = false
                editingConnection = false
                navigationState = navigationState.copy(route = NaviampRoute.Home)
                startAndroidLibrarySync(force = false)
                checkAndroidLibraryFreshness()
            }.onFailure { error ->
                status = error.message ?: "Connection failed."
                restoringConnection = false
                provider = null
                validation = null
            }
        }
    }

    fun connectToNavidrome() {
        val connectionForm = currentConnectionForm()
        val tlsSettings = NavidromeTlsSettings(
            insecureSkipTlsVerification = connectionForm.skipTlsVerification,
            customCertificatePath = connectionForm.customCertificatePath.trim().takeIf { it.isNotEmpty() },
            clientCertificateKeyStorePath = connectionForm.clientCertificatePath.trim().takeIf { it.isNotEmpty() },
            clientCertificateKeyStorePassword = connectionForm.clientCertificatePassword
                .takeIf { connectionForm.clientCertificatePath.trim().isNotEmpty() },
        )
        scope.launch {
            runCatching {
                val displayName = connectionForm.displayName.trim().takeIf { it.isNotEmpty() }
                val connection = if (connectionForm.password.isBlank() && savedProviderConnection != null) {
                    savedProviderConnection.copy(
                        baseUrl = connectionForm.serverUrl,
                        username = connectionForm.username,
                        displayName = displayName,
                        tlsSettings = tlsSettings,
                    )
                } else {
                    NavidromeConnection.fromPassword(
                        baseUrl = connectionForm.serverUrl,
                        username = connectionForm.username,
                        password = connectionForm.password,
                        displayName = displayName,
                        tlsSettings = tlsSettings,
                    )
                }
                if (connectionForm.password.isNotBlank()) {
                    connection.withNativeTokenFromPassword(connectionForm.password)
                } else {
                    connection
                }
            }.onSuccess { connection ->
                settingsStore.saveConnection(connectionForm)
                connectWithNavidromeConnection(connection)
            }.onFailure { error ->
                status = error.message ?: "Connection failed."
                restoringConnection = false
                provider = null
                validation = null
            }
        }
    }

    LaunchedEffect(Unit) {
        when {
            savedProviderConnection != null -> connectWithNavidromeConnection(savedProviderConnection)
            savedConnection.serverUrl.isNotBlank() &&
                savedConnection.username.isNotBlank() &&
                savedConnection.password.isNotBlank() -> {
                connectToNavidrome()
            }
        }
    }

    LaunchedEffect(
        activeSourceId,
        playbackQueue,
        nowPlaying?.id,
        nowPlayingStation?.id,
    ) {
        savePlaybackSessionThrottled(force = true)
    }

    LaunchedEffect(
        activeSourceId,
        nowPlaying?.id,
        nowPlayingStation?.id,
        playbackProgress.positionSeconds,
    ) {
        savePlaybackSessionThrottled()
    }

    DisposableEffect(activeSourceId, nowPlaying?.id, nowPlayingStation?.id, playbackQueue) {
        onDispose {
            savePlaybackSessionThrottled(force = true)
        }
    }

    LaunchedEffect(selectedRoute, activeSourceId, nowPlaying?.id, nowPlayingStation?.id) {
        if (selectedRoute != SharedRoute.Settings) return@LaunchedEffect
        while (true) {
            storageStats = withContext(Dispatchers.IO) { storage.stats() }
            delay(5_000)
        }
    }

    LaunchedEffect(provider, activeSourceId) {
        if (provider == null || activeSourceId == null) return@LaunchedEffect
        checkAndroidLibraryFreshness()
        while (true) {
            delay(AndroidLibraryFreshnessCheckIntervalMillis)
            checkAndroidLibraryFreshness()
        }
    }

    LaunchedEffect(activeSourceId, selectedRoute, downloadRefreshToken) {
        val sourceId = activeSourceId ?: return@LaunchedEffect
        if (selectedRoute != SharedRoute.Downloads) return@LaunchedEffect
        downloadedTracks = withContext(Dispatchers.IO) {
            storage.downloadedTracks(sourceId)
        }
    }

    val diagnostics = androidDiagnostics(
        storageStats = storageStats,
        provider = provider,
        validation = validation,
        activeSourceId = activeSourceId,
        bassLoadReport = bassLoadReport,
        playbackEngine = playbackEngine,
        playbackState = playbackState,
        playbackProgress = playbackProgress,
        playbackQueue = playbackQueue,
        playbackSettings = playbackSettings,
        streamQuality = currentStreamQuality(),
        nowPlaying = nowPlaying,
        nowPlayingStation = nowPlayingStation,
        nowPlayingStreamMetadata = nowPlayingStreamMetadata,
        nowPlayingOpen = nowPlayingOpen,
        visualizerVisible = visualizerVisible,
        activeTlsSettings = activeTlsSettings,
        selectedRoute = selectedRoute,
    )

    NaviampSharedAppShell(
        modifier = modifier,
        status = status,
        serverVersion = validation?.serverVersion,
        connected = provider != null,
        editingConnection = editingConnection,
        restoringConnection = restoringConnection,
        connectionForm = ConnectionFormState(
            displayName = connectionName,
            serverUrl = serverUrl,
            username = username,
            password = password,
            skipTlsVerification = skipTlsVerification,
            customCertificatePath = customCertificatePath,
            clientCertificatePath = clientCertificatePath,
            clientCertificatePassword = clientCertificatePassword,
        ),
        playbackSettings = playbackSettings,
        diagnostics = diagnostics,
        supportsReplayGain = playbackEngine.supportsReplayGain,
        supportsGapless = playbackEngine.supportsGapless,
        supportsCrossfade = playbackEngine.supportsCrossfade,
        showMobileNetworkQuality = true,
        selectedVisualizer = selectedVisualizer,
        query = query,
        home = homeState.toSharedHomeUi(
            coverArtUrl = { coverArtId -> coverArtId?.let { provider?.coverArtUrl(it) } },
            playlistTracksById = playlistTracksById,
        ),
        searchResults = searchResults.toSharedSearchResultsUi { coverArtId -> coverArtId?.let { provider?.coverArtUrl(it) } },
        libraryArtists = homeState.artists.map { artist ->
            artist.toSharedMediaItemUi { coverArtId -> coverArtId?.let { provider?.coverArtUrl(it) } }
        },
        libraryQuery = libraryQuery,
        librarySyncStatus = NaviampLibrarySyncStatusUi(
            message = libraryStatus,
            isSyncing = isLibrarySyncing,
        ),
        downloads = downloadedTracks.map { download ->
            NaviampDownloadedTrackUi(
                id = download.file.absolutePath,
                track = download.track.toAndroidTrackRowUi(
                    coverArtUrl = { coverArtId -> coverArtId?.let { provider?.coverArtUrl(it) } },
                ),
                sizeBytes = download.sizeBytes,
            )
        },
        downloadBytes = storageStats.downloadBytes,
        maxDownloadBytes = AndroidMaxDownloadBytes,
        downloadStatus = downloadStatus,
        playlistItems = homeState.playlists.map {
            it.toSharedMediaItemUi(
                coverArtUrl = { coverArtId -> coverArtId?.let { provider?.coverArtUrl(it) } },
                tracks = playlistTracksById[it.id].orEmpty(),
            )
        },
        recentPlaylistIds = recentPlaylistIds,
        playlistSortMode = playlistSortMode,
        playlistChoices = homeState.playlists.map { it.toPlaylistChoiceUi() },
        playlistActionStatus = playlistActionStatus,
        radioStationItems = homeState.radioStations.map { it.toSharedMediaItemUi() },
        albumDetail = albumDetail?.let { detail ->
            detail.toSharedAlbumDetailUi(
                coverArtUrl = { coverArtId -> coverArtId?.let { provider?.coverArtUrl(it) } },
                popularTrackIds = detail.tracks
                    .mapNotNull { it.artistId?.value }
                    .distinct()
                    .flatMap { artistId -> artistPopularTracksByArtistId[artistId].orEmpty() }
                    .map { it.id.value }
                    .toSet(),
            )
        },
        artistDetail = artistDetail?.toSharedArtistDetailUi(
            coverArtUrl = { coverArtId -> coverArtId?.let { provider?.coverArtUrl(it) } },
            popularTracks = artistPopularTracksByArtistId[artistDetail.artist.id.value].orEmpty(),
            popularTracksStatus = artistPopularTracksStatusByArtistId[artistDetail.artist.id.value],
            similarArtists = artistSimilarArtistsByArtistId[artistDetail.artist.id.value].orEmpty(),
            similarArtistsStatus = artistSimilarArtistsStatusByArtistId[artistDetail.artist.id.value],
        ),
        playlistDetail = selectedPlaylist?.toSharedPlaylistDetailUi(
            tracks = selectedPlaylistTracks,
            coverArtUrl = { coverArtId -> coverArtId?.let { provider?.coverArtUrl(it) } },
        ),
        nowPlaying = nowPlaying?.let { track ->
            val knownTracks = activeQueue()
            val currentIndex = knownTracks.indexOfFirst { it.id == track.id }
            val lyrics = lyricsByTrackId[track.id.value]
            val lyricsStatus = lyricsStatusByTrackId[track.id.value]
            track.toNowPlayingUi(
                NowPlayingTrackUiConfig(
                    stateLabel = "${playbackState.label()} ${playbackProgress.positionSeconds?.toInt() ?: 0}s",
                    coverArtUrl = track.coverArtUrl(provider),
                    waveform = waveformByTrackId[track.id.value],
                    visualizerFrame = visualizerFrame,
                    visualizerAvailable = (playbackEngine as? VisualizerPlaybackEngine)?.supportsVisualizer == true,
                    visualizerVisible = visualizerVisible,
                    positionSeconds = playbackProgress.positionSeconds,
                    durationSeconds = playbackProgress.durationSeconds,
                    volumePercent = volumePercent,
                    isPlaying = playbackState == PlaybackState.Playing,
                    isPaused = playbackState == PlaybackState.Paused,
                    canSeek = true,
                    canChangeVolume = false,
                    hasPrevious = currentIndex > 0 || (repeatMode == RepeatMode.Queue && knownTracks.size > 1),
                    hasNext = (currentIndex >= 0 && currentIndex < knownTracks.lastIndex) ||
                        (repeatMode == RepeatMode.Queue && knownTracks.size > 1),
                    shuffleEnabled = knownTracks.drop(currentIndex + 1).size > 1,
                    shuffleActive = shuffledUpNextSnapshot != null,
                    repeatMode = repeatMode,
                    canRepeat = knownTracks.isNotEmpty(),
                    canStartRadio = provider?.capabilities?.supportsTrackRadio == true,
                    canAddToPlaylist = true,
                    canFavorite = provider?.capabilities?.supportsTrackFavorites == true,
                    canRate = provider?.capabilities?.supportsTrackRatings == true,
                    lyricsAvailable = true,
                    lyricsVisible = lyricsVisible && nowPlayingOpen,
                    lyricsStatus = lyricsStatus,
                    lyrics = lyrics,
                    menuEnabled = true,
                    streamQuality = currentStreamQuality(),
                    playlistChoices = homeState.playlists.map { it.toPlaylistChoiceUi() },
                    playlistActionStatus = playlistActionStatus,
                    backTo = knownTracks
                        .take(currentIndex.coerceAtLeast(0))
                        .asReversed()
                        .map { it.toNowPlayingItemUi { coverArtId -> coverArtId?.let { provider?.coverArtUrl(it) } } },
                    upNext = if (currentIndex >= 0) {
                        knownTracks.drop(currentIndex + 1).map {
                            it.toNowPlayingItemUi { coverArtId -> coverArtId?.let { provider?.coverArtUrl(it) } }
                        }
                    } else {
                        emptyList()
                    },
                    related = relatedTracks.map {
                        it.toNowPlayingItemUi { coverArtId -> coverArtId?.let { provider?.coverArtUrl(it) } }
                    },
                ),
            )
        } ?: nowPlayingStation?.let { station ->
            station.toNowPlayingUi(
                NowPlayingRadioUiConfig(
                    streamTitle = nowPlayingStreamMetadata.title,
                    stateLabel = playbackState.label(),
                    volumePercent = volumePercent,
                    isPlaying = playbackState == PlaybackState.Playing,
                    isPaused = playbackState == PlaybackState.Paused,
                    canChangeVolume = false,
                    radioStations = homeState.radioStations.map { it.toNowPlayingStationUi() },
                ),
            )
        },
        nowPlayingOpen = nowPlayingOpen,
        selectedRoute = selectedRoute,
        onRouteSelected = { route ->
            navigationState = navigationState.copy(route = route.toNaviampRoute())
            contentState = contentState.clearDetails()
            nowPlayingOpen = false
        },
        onConnectionFormChanged = { form ->
            connectionName = form.displayName
            serverUrl = form.serverUrl
            username = form.username
            password = form.password
            skipTlsVerification = form.skipTlsVerification
            customCertificatePath = form.customCertificatePath
            clientCertificatePath = form.clientCertificatePath
            clientCertificatePassword = form.clientCertificatePassword
        },
        onConnect = { connectToNavidrome() },
        onEditConnection = { editingConnection = true },
        onCancelEditConnection = { editingConnection = false },
        onPlaybackSettingsChanged = { settings ->
            val normalizedSettings = settings.copy(
                replayGainMode = settings.replayGainMode.forEngine(playbackEngine),
                gaplessEnabled = playbackEngine.supportsGapless && settings.gaplessEnabled,
                crossfadeDurationSeconds = if (playbackEngine.supportsCrossfade) {
                    settings.crossfadeDurationSeconds
                } else {
                    0
                },
            )
            playbackSettings = normalizedSettings
            settingsStore.savePlaybackSettings(normalizedSettings)
            lyricsByTrackId = emptyMap()
            lyricsStatusByTrackId = emptyMap()
            if (lyricsVisible && nowPlayingOpen) nowPlaying?.let(::loadLyrics)
        },
        onClearCache = {
            storage.clearCacheData()
            clearAndroidFileCaches()
            clearDerivedMediaState()
            status = "Cache cleared."
        },
        onClearLibrary = {
            storage.clearLibraryData(activeSourceId)
            homeState = HomeContent()
            contentState = NaviampContentState()
            tracks = emptyList()
            recentPlaylistIds = emptyList()
            clearDerivedMediaState()
            status = "Library index cleared."
        },
        onResetDatabase = {
            resetPlaybackState()
            storage.clearAll()
            settingsStore.clear()
            clearAndroidFileCaches()
            provider = null
            activeSourceId = null
            validation = null
            activeTlsSettings = NavidromeTlsSettings()
            homeState = HomeContent()
            contentState = NaviampContentState()
            tracks = emptyList()
            recentPlaylistIds = emptyList()
            connectionName = ""
            serverUrl = ""
            username = ""
            password = ""
            skipTlsVerification = false
            customCertificatePath = ""
            clientCertificatePath = ""
            clientCertificatePassword = ""
            editingConnection = true
            restoringConnection = false
            navigationState = NaviampNavigationState(route = NaviampRoute.Settings)
            clearDerivedMediaState()
            status = "Database reset."
        },
        onQueryChanged = { contentState = contentState.copy(searchQuery = it) },
        onSearch = {
            val activeProvider = provider ?: return@NaviampSharedAppShell
            scope.launch {
                status = "Searching..."
                runCatching {
                    activeProvider.search(query, limit = 20)
                }.onSuccess { results ->
                    contentState = contentState.clearDetails().copy(searchResults = results)
                    tracks = results.tracks
                    status = if (results.isEmpty) "No matches found." else "Found ${results.totalCount()} matches."
                }.onFailure { error ->
                    status = error.message ?: "Search failed."
                }
            }
        },
        onLibraryQueryChanged = { libraryQuery = it },
        onRefreshLibrary = {
            startAndroidLibrarySync(force = true)
        },
        onTrackSelected = { selectedTrack ->
            val currentTracks = activeQueue().takeIf { queue -> queue.any { it.id.value == selectedTrack.id } }
                ?: relatedTracks.takeIf { queue -> queue.any { it.id.value == selectedTrack.id } }
                ?: artistPopularTracksByArtistId.values.flatten().takeIf { queue -> queue.any { it.id.value == selectedTrack.id } }
                ?: allKnownTracks(searchResults, albumDetail)
            val track = currentTracks.firstOrNull { it.id.value == selectedTrack.id } ?: findKnownTrack(selectedTrack.id)
            if (track == null) {
                status = "Track not found."
                return@NaviampSharedAppShell
            }
            playTrack(track, currentTracks)
        },
        onDownloadedTrackSelected = { download ->
            val currentTracks = downloadedTracks.map { it.track }
            val track = currentTracks.firstOrNull { it.id.value == download.track.id } ?: return@NaviampSharedAppShell
            playTrack(track, currentTracks)
        },
        onDownloadedTrackAddToPlaylist = { download, playlist ->
            downloadedTracks.firstOrNull { it.file.absolutePath == download.id }
                ?.track
                ?.let { addTrackToPlaylist(it, playlist) }
                ?: run { status = "Track not found." }
        },
        onDownloadedTrackCreatePlaylistAndAdd = { download, name ->
            downloadedTracks.firstOrNull { it.file.absolutePath == download.id }
                ?.track
                ?.let { addTrackToPlaylist(it, playlist = null, newPlaylistName = name) }
                ?: run { status = "Track not found." }
        },
        onRemoveDownload = { download ->
            removeDownload(download)
        },
        onAlbumSelected = { selectedAlbum ->
            val activeProvider = provider ?: return@NaviampSharedAppShell
            scope.launch {
                status = "Loading ${selectedAlbum.title}..."
                runCatching {
                    activeProvider.album(app.naviamp.domain.AlbumId(selectedAlbum.id))
                }.onSuccess { detail ->
                    contentState = contentState.showAlbum(detail)
                    tracks = detail.tracks
                    nowPlayingOpen = false
                    status = "Connected."
                }.onFailure { error ->
                    status = error.message ?: "Album failed to load."
                }
            }
        },
        onMixAlbumSelected = { selectedAlbum ->
            homeState.mixAlbums.firstOrNull { it.id.value == selectedAlbum.id }
                ?.let { startAlbumRadio(it) }
                ?: run { status = "Album not found." }
        },
        onAlbumPlay = { _, shuffle ->
            val albumTracks = albumDetail?.tracks.orEmpty()
            val queue = if (shuffle) albumTracks.shuffled() else albumTracks
            queue.firstOrNull()?.let { playTrack(it, queue) }
                ?: run { status = "Album is empty." }
        },
        onAlbumTrackSelected = { selectedTrack ->
            val track = albumDetail?.tracks?.firstOrNull { it.id.value == selectedTrack.id }
                ?: findKnownTrack(selectedTrack.id)
            if (track == null) {
                status = "Track not found."
            } else {
                startTrackRadio(track)
            }
        },
        onAlbumRadio = { detail ->
            val loadedAlbumTracks = albumDetail?.tracks.orEmpty()
            val album = albumDetail?.album ?: return@NaviampSharedAppShell
            startAlbumRadio(album, loadedAlbumTracks)
        },
        onAlbumAddToQueue = {
            appendTracksToQueue(albumDetail?.tracks.orEmpty(), "album tracks")
        },
        onAlbumDownload = {
            downloadTracks(albumDetail?.tracks.orEmpty(), "album")
        },
        onAlbumAddToPlaylist = { _, playlist ->
            addTracksToPlaylist(albumDetail?.tracks.orEmpty(), playlist, label = "album")
        },
        onAlbumCreatePlaylistAndAdd = { _, name ->
            addTracksToPlaylist(albumDetail?.tracks.orEmpty(), playlist = null, newPlaylistName = name, label = "album")
        },
        onAlbumTrackDownload = { selectedTrack ->
            findKnownTrack(selectedTrack.id)?.let(::downloadTrack) ?: run { status = "Track not found." }
        },
        onAlbumTrackAddToPlaylist = { selectedTrack, playlist ->
            findKnownTrack(selectedTrack.id)?.let { addTrackToPlaylist(it, playlist) } ?: run { status = "Track not found." }
        },
        onAlbumTrackCreatePlaylistAndAdd = { selectedTrack, name ->
            findKnownTrack(selectedTrack.id)?.let { addTrackToPlaylist(it, playlist = null, newPlaylistName = name) }
                ?: run { status = "Track not found." }
        },
        onArtistRadio = { detail ->
            val service = radioService() ?: return@NaviampSharedAppShell
            val artistId = app.naviamp.domain.ArtistId(detail.artist.id)
            val artist = artistDetail?.artist ?: Artist(artistId, detail.artist.title)
            scope.launch {
                status = "Starting ${detail.artist.title} radio..."
                runCatching { service.artistSeed(artist, artistDetail?.albums.orEmpty()) }
                    .onSuccess { seedTrack ->
                    if (seedTrack == null) {
                        status = "${detail.artist.title} radio did not find a seed track."
                    } else {
                        startSeededRadio("${detail.artist.title} radio", seedTrack) { radioService ->
                            radioService.artistRadio(artistId)
                        }
                    }
                }.onFailure { error ->
                    status = error.message ?: "Could not start artist radio."
                }
            }
        },
        onArtistShuffle = {
            val albums = artistDetail?.albums.orEmpty()
            val activeProvider = provider ?: return@NaviampSharedAppShell
            scope.launch {
                status = "Loading artist tracks..."
                val artistTracks = albums.flatMap { album ->
                    runCatching { activeProvider.album(album.id).tracks }.getOrDefault(emptyList())
                }.distinctBy { it.id }
                val queue = artistTracks.shuffled()
                queue.firstOrNull()?.let { playTrack(it, queue) }
                    ?: run { status = "No artist tracks found." }
            }
        },
        onArtistAddToQueue = {
            val albums = artistDetail?.albums.orEmpty()
            val activeProvider = provider ?: return@NaviampSharedAppShell
            scope.launch {
                status = "Loading artist tracks..."
                val artistTracks = albums.flatMap { album ->
                    runCatching { activeProvider.album(album.id).tracks }.getOrDefault(emptyList())
                }
                appendTracksToQueue(artistTracks, "artist tracks")
            }
        },
        onArtistAddToPlaylist = { _, playlist ->
            val albums = artistDetail?.albums.orEmpty()
            val activeProvider = provider ?: return@NaviampSharedAppShell
            scope.launch {
                status = "Loading artist tracks..."
                val artistTracks = albums.flatMap { album ->
                    runCatching { activeProvider.album(album.id).tracks }.getOrDefault(emptyList())
                }
                addTracksToPlaylist(artistTracks, playlist, label = "artist")
            }
        },
        onArtistCreatePlaylistAndAdd = { _, name ->
            val albums = artistDetail?.albums.orEmpty()
            val activeProvider = provider ?: return@NaviampSharedAppShell
            scope.launch {
                status = "Loading artist tracks..."
                val artistTracks = albums.flatMap { album ->
                    runCatching { activeProvider.album(album.id).tracks }.getOrDefault(emptyList())
                }
                addTracksToPlaylist(artistTracks, playlist = null, newPlaylistName = name, label = "artist")
            }
        },
        onArtistPopularPlay = { detail ->
            val popularTracks = artistPopularTracksByArtistId[detail.artist.id].orEmpty()
            popularTracks.firstOrNull()?.let { playTrack(it, popularTracks) }
                ?: run { status = "No popular tracks matched your library." }
        },
        onArtistPopularRadio = { detail ->
            val service = radioService() ?: return@NaviampSharedAppShell
            val popularTracks = artistPopularTracksByArtistId[detail.artist.id].orEmpty()
            val seedTrack = popularTracks.shuffled().firstOrNull()
            if (seedTrack == null) {
                status = "No popular tracks matched your library."
                return@NaviampSharedAppShell
            }
            startSeededRadio("${detail.artist.title} popular tracks radio", seedTrack) { radioService ->
                coroutineScope {
                    popularTracks.take(AndroidPopularRadioSeedLimit)
                        .map { track -> async(Dispatchers.IO) { radioService.trackRadio(track.id) } }
                        .awaitAll()
                        .flatten()
                }
            }
        },
        onArtistPopularTrackSelected = { selectedTrack ->
            val track = artistPopularTracksByArtistId.values.flatten().firstOrNull { it.id.value == selectedTrack.id }
                ?: findKnownTrack(selectedTrack.id)
            if (track == null) {
                status = "Track not found."
            } else {
                startTrackRadio(track)
            }
        },
        onArtistPopularAddToQueue = { detail ->
            val popularTracks = artistPopularTracksByArtistId[detail.artist.id].orEmpty()
            if (popularTracks.isEmpty()) {
                status = "No popular tracks matched your library."
            } else {
                appendTracksToQueue(popularTracks.filterNot { track -> playbackQueue.tracks.any { it.id == track.id } }, "popular tracks")
            }
        },
        onArtistPopularTrackAddToQueue = { selectedTrack ->
            val track = findKnownTrack(selectedTrack.id)
            if (track == null) {
                status = "Track not found."
            } else {
                appendTracksToQueue(listOf(track), "track")
            }
        },
        onArtistPopularTrackDownload = { selectedTrack ->
            findKnownTrack(selectedTrack.id)?.let(::downloadTrack) ?: run { status = "Track not found." }
        },
        onArtistPopularTrackAddToPlaylist = { selectedTrack, playlist ->
            findKnownTrack(selectedTrack.id)?.let { addTrackToPlaylist(it, playlist) } ?: run { status = "Track not found." }
        },
        onArtistPopularTrackCreatePlaylistAndAdd = { selectedTrack, name ->
            findKnownTrack(selectedTrack.id)?.let { addTrackToPlaylist(it, playlist = null, newPlaylistName = name) }
                ?: run { status = "Track not found." }
        },
        onFindSimilarArtists = { detail ->
            findSimilarArtists(app.naviamp.domain.ArtistId(detail.artist.id), detail.artist.title)
        },
        onSimilarArtistSelected = { similarArtist ->
            val artistId = similarArtist.localArtistId
            if (artistId == null) {
                status = "Artist is not in your library."
            } else {
                openArtistDetails(app.naviamp.domain.ArtistId(artistId), similarArtist.title)
            }
        },
        onSimilarArtistExternalSelected = { url ->
            openExternalArtistUrl(url)
        },
        onArtistSelected = { selectedArtist ->
            openArtistDetails(app.naviamp.domain.ArtistId(selectedArtist.id), selectedArtist.title)
        },
        onArtistAlbumRadio = { selectedAlbum ->
            val activeProvider = provider ?: return@NaviampSharedAppShell
            scope.launch {
                status = "Starting ${selectedAlbum.title} radio..."
                runCatching { activeProvider.album(app.naviamp.domain.AlbumId(selectedAlbum.id)) }
                    .onSuccess { detail -> startAlbumRadio(detail.album, detail.tracks) }
                    .onFailure { error -> status = error.message ?: "Could not start album radio." }
            }
        },
        onArtistAlbumDownload = { selectedAlbum ->
            val activeProvider = provider ?: return@NaviampSharedAppShell
            scope.launch {
                status = "Loading ${selectedAlbum.title}..."
                runCatching { activeProvider.album(app.naviamp.domain.AlbumId(selectedAlbum.id)).tracks }
                    .onSuccess { albumTracks -> downloadTracks(albumTracks, selectedAlbum.title) }
                    .onFailure { error -> status = error.message ?: "Could not load album." }
            }
        },
        onArtistAlbumAddToQueue = { selectedAlbum ->
            val activeProvider = provider ?: return@NaviampSharedAppShell
            scope.launch {
                status = "Loading ${selectedAlbum.title}..."
                runCatching { activeProvider.album(app.naviamp.domain.AlbumId(selectedAlbum.id)).tracks }
                    .onSuccess { albumTracks -> appendTracksToQueue(albumTracks, "album tracks") }
                    .onFailure { error -> status = error.message ?: "Could not load album." }
            }
        },
        onArtistAlbumAddToPlaylist = { selectedAlbum, playlist ->
            val activeProvider = provider ?: return@NaviampSharedAppShell
            scope.launch {
                status = "Loading ${selectedAlbum.title}..."
                runCatching { activeProvider.album(app.naviamp.domain.AlbumId(selectedAlbum.id)).tracks }
                    .onSuccess { albumTracks -> addTracksToPlaylist(albumTracks, playlist, label = selectedAlbum.title) }
                    .onFailure { error -> status = error.message ?: "Could not load album." }
            }
        },
        onArtistAlbumCreatePlaylistAndAdd = { selectedAlbum, name ->
            val activeProvider = provider ?: return@NaviampSharedAppShell
            scope.launch {
                status = "Loading ${selectedAlbum.title}..."
                runCatching { activeProvider.album(app.naviamp.domain.AlbumId(selectedAlbum.id)).tracks }
                    .onSuccess { albumTracks ->
                        addTracksToPlaylist(albumTracks, playlist = null, newPlaylistName = name, label = selectedAlbum.title)
                    }
                    .onFailure { error -> status = error.message ?: "Could not load album." }
            }
        },
        onPlaylistSelected = { selectedPlaylist ->
            homeState.playlists.firstOrNull { it.id == selectedPlaylist.id }?.let { openPlaylistDetails(it) }
                ?: run { status = "Playlist not found." }
        },
        onPlaylistSortModeChanged = { playlistSortMode = it },
        onPlaylistPlay = { selectedPlaylist, shuffle ->
            homeState.playlists.firstOrNull { it.id == selectedPlaylist.id }?.let { playPlaylist(it, shuffle) }
                ?: run { status = "Playlist not found." }
        },
        onPlaylistAddToQueue = {
            appendTracksToQueue(selectedPlaylistTracks, "playlist tracks")
        },
        onPlaylistRename = { selectedPlaylist, name ->
            homeState.playlists.firstOrNull { it.id == selectedPlaylist.id }?.let { renamePlaylist(it, name) }
                ?: run { status = "Playlist not found." }
        },
        onPlaylistDelete = { selectedPlaylist ->
            homeState.playlists.firstOrNull { it.id == selectedPlaylist.id }?.let { deletePlaylist(it) }
                ?: run { status = "Playlist not found." }
        },
        onSmartPlaylistSave = { definition -> saveSmartPlaylist(definition) },
        onPlaylistBack = {
            closeActivePlaylist()
        },
        onPlaylistTrackSelected = { selectedTrack ->
            val track = selectedPlaylistTracks.firstOrNull { it.id.value == selectedTrack.id } ?: findKnownTrack(selectedTrack.id)
            if (track == null) {
                status = "Track not found."
                return@NaviampSharedAppShell
            }
            playTrack(track, selectedPlaylistTracks.ifEmpty { listOf(track) })
        },
        onTrackAddToQueue = { selectedTrack ->
            val track = findKnownTrack(selectedTrack.id)
            if (track == null) {
                status = "Track not found."
            } else {
                appendTracksToQueue(listOf(track), "track")
            }
        },
        onRadioStationSelected = { selectedStation ->
            val station = homeState.radioStations.firstOrNull { it.id == selectedStation.id }
            if (station == null) {
                status = "Station not found."
                return@NaviampSharedAppShell
            }
            playInternetRadioStation(station)
        },
        onHomeStationSelected = { station ->
            when {
                station.id == HomeStationLibrary -> {
                    playRadioTracks("Library Radio") { radioService ->
                        radioService.libraryRadio()
                    }
                }
                station.id == HomeStationRandomAlbum -> {
                    playRadioTracks("Random Album Radio") { radioService ->
                        val album = homeState.randomAlbums.firstOrNull()
                            ?: provider?.albumList(AlbumListType.Random, limit = 1)?.firstOrNull()
                        album?.let { radioService.albumRadio(it.id) }.orEmpty()
                    }
                }
                parseHomeGenreStationId(station.id) != null -> {
                    val genre = parseHomeGenreStationId(station.id).orEmpty()
                    playRadioTracks("${genre} Radio") { radioService ->
                        radioService.genreRadio(genre)
                    }
                }
                parseHomeDecadeStationId(station.id) != null -> {
                    val decade = parseHomeDecadeStationId(station.id)
                    if (decade != null) {
                        playRadioTracks(station.title) { radioService ->
                            radioService.decadeRadio(decade.fromYear, decade.toYear)
                        }
                    }
                }
            }
        },
        onOpenNowPlaying = { nowPlayingOpen = true },
        onCloseNowPlaying = {
            if (nowPlayingOpen) {
                nowPlayingOpen = false
            } else {
                closeActiveDetail()
            }
        },
        onPause = playbackEngine::pause,
        onResume = {
            when (playbackState) {
                PlaybackState.Idle,
                PlaybackState.Stopped,
                PlaybackState.Finished,
                is PlaybackState.Error,
                -> {
                    nowPlaying?.let { track ->
                        playTrack(
                            track = track,
                            queue = playbackQueue.tracks.ifEmpty { listOf(track) },
                            startPositionSeconds = restoredStartPositionSeconds,
                        )
                        restoredStartPositionSeconds = null
                    } ?: nowPlayingStation?.let(::playInternetRadioStation)
                }
                else -> playbackEngine.resume()
            }
        },
        onStop = playbackEngine::stop,
        onPrevious = { playAdjacentTrack(-1) },
        onNext = { playAdjacentTrack(1) },
        onSeek = ::performSeek,
        onVolumeChanged = { percent ->
            volumePercent = percent
            playbackEngine.setVolume(percent)
        },
        onToggleShuffle = {
            val currentTrack = nowPlaying ?: return@NaviampSharedAppShell
            val queue = activeQueue()
            val currentIndex = queue.indexOfFirst { it.id == currentTrack.id }
            if (currentIndex < 0) return@NaviampSharedAppShell
            val snapshot = shuffledUpNextSnapshot
            val queueState = PlaybackQueue(tracks = queue, currentIndex = currentIndex)
            if (snapshot == null) {
                val shuffled = queueState.shuffleUpcoming() ?: return@NaviampSharedAppShell
                playbackQueue = shuffled.first
                shuffledUpNextSnapshot = shuffled.second
            } else {
                playbackQueue = queueState.restoreUpcoming(snapshot)
                shuffledUpNextSnapshot = null
            }
        },
        onCycleRepeatMode = {
            repeatMode = when (repeatMode) {
                RepeatMode.Off -> RepeatMode.Queue
                RepeatMode.Queue -> RepeatMode.Track
                RepeatMode.Track -> RepeatMode.Off
            }
        },
        onToggleLyrics = {
            lyricsVisible = !lyricsVisible
            if (lyricsVisible) {
                nowPlaying?.let(::loadLyrics)
            }
        },
        onToggleVisualizer = {
            visualizerRequestedVisible = !visualizerRequestedVisible
        },
        onVisualizerSelected = { visualizer ->
            selectedVisualizer = visualizer
            settingsStore.saveSelectedVisualizer(visualizer.name)
        },
        onTrackRadio = {
            val activeProvider = provider ?: return@NaviampSharedAppShell
            val currentTrack = nowPlaying ?: return@NaviampSharedAppShell
            if (provider?.capabilities?.supportsTrackRadio != true) return@NaviampSharedAppShell
            scope.launch {
                status = "Starting ${currentTrack.title} radio..."
                runCatching { RadioService(activeProvider, count = AndroidInitialSimilarRadioCount).trackRadio(currentTrack.id) }
                    .onSuccess { radioTracks ->
                        val queue = generatedRadioQueue(currentTrack, radioTracks)
                        val deduped = queue.drop(1)
                        playbackQueue = PlaybackQueue(tracks = queue, currentIndex = 0)
                        relatedTracks = deduped
                        shuffledUpNextSnapshot = null
                        status = "Building ${currentTrack.title} radio queue..."

                        AndroidSimilarRadioExpansionCounts.forEach { count ->
                            if (nowPlaying?.id != currentTrack.id) return@launch
                            val fetchedTracks = runCatching {
                                RadioService(activeProvider, count = count).trackRadio(currentTrack.id)
                            }.getOrElse {
                                return@forEach
                            }
                            appendGeneratedRadioTracks(currentTrack, fetchedTracks)
                            status = "Building ${currentTrack.title} radio queue (${playbackQueue.tracks.size} tracks)..."
                        }
                        if (nowPlaying?.id == currentTrack.id) {
                            status = "Track radio loaded."
                        }
                    }
                    .onFailure { error ->
                        status = error.message ?: "Could not start track radio."
                    }
            }
        },
        onAddToPlaylist = { playlist ->
            val currentTrack = nowPlaying ?: return@NaviampSharedAppShell
            addTrackToPlaylist(currentTrack, playlist)
        },
        onCreatePlaylistAndAdd = { name ->
            val currentTrack = nowPlaying ?: return@NaviampSharedAppShell
            addTrackToPlaylist(currentTrack, playlist = null, newPlaylistName = name)
        },
        onDownloadTrack = {
            nowPlaying?.let(::downloadTrack)
        },
        onGoToAlbum = {
            val activeProvider = provider ?: return@NaviampSharedAppShell
            val albumId = nowPlaying?.albumId ?: return@NaviampSharedAppShell
            scope.launch {
                runCatching { activeProvider.album(albumId) }
                    .onSuccess { detail ->
                        contentState = contentState.showAlbum(detail)
                        nowPlayingOpen = false
                        navigationState = navigationState.copy(route = NaviampRoute.Library)
                    }
                    .onFailure { error -> status = error.message ?: "Album failed to load." }
            }
        },
        onGoToArtist = {
            val currentTrack = nowPlaying ?: return@NaviampSharedAppShell
            currentTrack.artistId?.let { openArtistDetails(it, currentTrack.artistName) }
        },
        onQueueItemRadio = { item ->
            findKnownTrack(item.id)?.let { track ->
                val activeProvider = provider ?: return@let
                if (provider?.capabilities?.supportsTrackRadio != true) return@let
                scope.launch {
                    runCatching { RadioService(activeProvider, count = AndroidInitialSimilarRadioCount).trackRadio(track.id) }
                        .onSuccess { radioTracks ->
                            val queue = generatedRadioQueue(track, radioTracks)
                            playTrack(track, queue)
                            status = "Building ${track.title} radio queue..."
                            AndroidSimilarRadioExpansionCounts.forEach { count ->
                                if (nowPlaying?.id != track.id) return@launch
                                val fetchedTracks = runCatching {
                                    RadioService(activeProvider, count = count).trackRadio(track.id)
                                }.getOrElse {
                                    return@forEach
                                }
                                appendGeneratedRadioTracks(track, fetchedTracks)
                                status = "Building ${track.title} radio queue (${playbackQueue.tracks.size} tracks)..."
                            }
                            if (nowPlaying?.id == track.id) {
                                status = "Track radio loaded."
                            }
                        }
                        .onFailure { error -> status = error.message ?: "Could not start track radio." }
                }
            }
        },
        onQueueItemAddToPlaylist = { item, playlist ->
            findKnownTrack(item.id)?.let { addTrackToPlaylist(it, playlist) }
        },
        onQueueItemCreatePlaylistAndAdd = { item, name ->
            findKnownTrack(item.id)?.let { addTrackToPlaylist(it, playlist = null, newPlaylistName = name) }
        },
        onQueueItemDownload = { item ->
            findKnownTrack(item.id)?.let(::downloadTrack)
        },
        onToggleFavorite = {
            toggleCurrentFavorite()
        },
        onRatingSelected = { rating ->
            val activeProvider = provider ?: return@NaviampSharedAppShell
            val currentTrack = nowPlaying ?: return@NaviampSharedAppShell
            if (!activeProvider.capabilities.supportsTrackRatings) return@NaviampSharedAppShell
            scope.launch {
                runCatching {
                    activeProvider.setTrackRating(currentTrack.id, rating)
                }.onSuccess {
                    applyTrackMetadataUpdate(currentTrack.copy(userRating = rating))
                }.onFailure { error ->
                    status = error.message ?: "Could not update rating."
                }
            }
        },
    )
}

private suspend fun loadBrowseState(provider: NavidromeProvider): HomeContent {
    val today = LocalDate.now()
    val home = HomeService(
        provider = provider,
        date = HomeDate(year = today.year, dayOfYear = today.dayOfYear),
    ).load()
    val artists = runCatching { provider.artists(limit = AndroidLibraryArtistLimit) }
        .getOrDefault(home.artists)
    return home.copy(artists = artists)
}

private suspend fun syncAndroidLibrary(
    sourceId: String,
    provider: MediaProvider,
    storage: AndroidStorage,
    onProgress: suspend (AndroidLibrarySyncProgress) -> Unit = {},
) {
    storage.markLibrarySyncStarted(sourceId)
    onProgress(AndroidLibrarySyncProgress(label = "Loading library artists..."))
    val artists = provider.artists(limit = AndroidLibraryArtistLimit)
    storage.upsertLibraryArtists(sourceId, artists)
    onProgress(AndroidLibrarySyncProgress(label = "Indexed ${artists.size} artists.", artists = artists))

    val albums = mutableListOf<Album>()
    var offset = 0
    while (true) {
        onProgress(AndroidLibrarySyncProgress(label = "Loading library albums (${albums.size})..."))
        val page = provider.albums(limit = AndroidLibraryAlbumPageSize, offset = offset)
        if (page.isEmpty()) break
        albums += page
        storage.upsertLibraryAlbums(sourceId, page)
        onProgress(AndroidLibrarySyncProgress(label = "Indexed ${albums.size} albums."))
        if (page.size < AndroidLibraryAlbumPageSize) break
        offset += AndroidLibraryAlbumPageSize
    }

    storage.markLibrarySyncCompleted(sourceId)
    onProgress(AndroidLibrarySyncProgress(label = "Library indexed: ${artists.size} artists, ${albums.size} albums."))
}

private data class AndroidLibrarySyncProgress(
    val label: String,
    val artists: List<Artist>? = null,
)

private data class AndroidLibraryFreshness(
    val signature: String?,
    val previousSignature: String?,
    val scanning: Boolean,
)

private fun Track.coverArtUrl(provider: NavidromeProvider?): String? =
    (coverArtId ?: albumId?.value)?.let { provider?.coverArtUrl(it) }

private fun Context.isActiveNetworkMobileData(): Boolean {
    val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
        !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
        !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
}

private fun allKnownTracks(
    searchResults: MediaSearchResults,
    albumDetail: AlbumDetails?,
): List<Track> =
    albumDetail?.tracks ?: searchResults.tracks

private fun MediaSearchResults.totalCount(): Int =
    artists.size + albums.size + tracks.size

private suspend fun resolveInternetRadioStreamUrl(stationUrl: String): String =
    withContext(Dispatchers.IO) {
        val connection = (URL(stationUrl).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("User-Agent", "Naviamp Android")
            setRequestProperty("Icy-MetaData", "1")
        }
        val contentType = connection.contentType.orEmpty().lowercase()
        val resolvedUrl = connection.url.toString()
        if (!looksLikePlaylistUrl(resolvedUrl) &&
            !contentType.isPlaylistContentType() &&
            (contentType.startsWith("audio/") || contentType.contains("ogg"))
        ) {
            connection.disconnect()
            return@withContext resolvedUrl
        }

        val body = connection.inputStream.bufferedReader().use { it.readText().take(128_000) }
        connection.disconnect()

        parseRadioPlaylist(body)
            ?: if (looksLikeDirectAudioUrl(resolvedUrl)) resolvedUrl else stationUrl
    }

private fun parseRadioPlaylist(body: String): String? {
    val lines = body.lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toList()

    lines.firstNotNullOfOrNull { line ->
        val equalsIndex = line.indexOf('=')
        if (equalsIndex > 0 && line.substring(0, equalsIndex).trim().lowercase().startsWith("file")) {
            line.substring(equalsIndex + 1).trim().takeIf { it.startsWith("http", ignoreCase = true) }
        } else {
            null
        }
    }?.let { return it }

    lines.firstOrNull { line ->
        line.startsWith("http", ignoreCase = true) && !line.startsWith("#")
    }?.let { return it }

    Regex("<location>(.*?)</location>", RegexOption.IGNORE_CASE)
        .find(body)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?.takeIf { it.startsWith("http", ignoreCase = true) }
        ?.let { return it }

    Regex("https?://[^\\s\"'<>]+", RegexOption.IGNORE_CASE)
        .find(body)
        ?.value
        ?.let { return it }

    return null
}

private fun looksLikeDirectAudioUrl(url: String): Boolean {
    val normalized = url.substringBefore('?').lowercase()
    return normalized.endsWith(".mp3") ||
        normalized.endsWith(".ogg") ||
        normalized.endsWith(".opus") ||
        normalized.endsWith(".aac") ||
        normalized.endsWith(".m4a") ||
        normalized.endsWith(".flac")
}

private fun looksLikePlaylistUrl(url: String): Boolean {
    val normalized = url.substringBefore('?').lowercase()
    return normalized.endsWith(".pls") ||
        normalized.endsWith(".m3u") ||
        normalized.endsWith(".m3u8") ||
        normalized.endsWith(".xspf") ||
        normalized.endsWith(".asx")
}

private fun String.isPlaylistContentType(): Boolean =
    contains("mpegurl") ||
        contains("scpls") ||
        contains("pls") ||
        contains("xspf") ||
        contains("asx") ||
        contains("text/plain") ||
        contains("text/html") ||
        contains("application/octet-stream")

private fun NaviampRoute.toSharedRoute(): SharedRoute =
    when (this) {
        NaviampRoute.Home -> SharedRoute.Home
        NaviampRoute.Playlists,
        NaviampRoute.PlaylistDetail,
        -> SharedRoute.Playlists
        NaviampRoute.Library -> SharedRoute.Library
        NaviampRoute.Search,
        NaviampRoute.AlbumDetail,
        NaviampRoute.ArtistDetail,
        NaviampRoute.Player,
        -> SharedRoute.Search
        NaviampRoute.Radio -> SharedRoute.Radio
        NaviampRoute.Downloads -> SharedRoute.Downloads
        NaviampRoute.Settings -> SharedRoute.Settings
    }

private fun SharedRoute.toNaviampRoute(): NaviampRoute =
    when (this) {
        SharedRoute.Home -> NaviampRoute.Home
        SharedRoute.Playlists -> NaviampRoute.Playlists
        SharedRoute.Library -> NaviampRoute.Library
        SharedRoute.Search -> NaviampRoute.Search
        SharedRoute.Radio -> NaviampRoute.Radio
        SharedRoute.Downloads -> NaviampRoute.Downloads
        SharedRoute.Settings -> NaviampRoute.Settings
    }

private fun PlaybackProgress.mergeForAndroidPlayback(previous: PlaybackProgress): PlaybackProgress =
    PlaybackProgress(
        positionSeconds = positionSeconds ?: previous.positionSeconds,
        durationSeconds = durationSeconds ?: previous.durationSeconds,
    )

private fun playReportThresholdSeconds(durationSeconds: Double?): Double =
    durationSeconds
        ?.let { minOf(it * PlayReportDurationFraction, PlayReportMaxThresholdSeconds) }
        ?: PlayReportMaxThresholdSeconds

private fun ReplayGainMode.forEngine(playbackEngine: AndroidPlaybackEngine): ReplayGainMode =
    if (playbackEngine.supportsReplayGain) this else ReplayGainMode.Off

private fun Track.isInternetRadioTrack(): Boolean =
    id.value.startsWith("internet-radio:")

private suspend fun MediaProvider.tracksForArtist(artistId: ArtistId, limit: Long): List<Track> {
    val tracks = mutableListOf<Track>()
    artist(artistId)
        .albums
        .take(ProviderArtistPopularTrackAlbumFallbackLimit)
        .forEach { albumSummary ->
            if (tracks.size >= limit) return@forEach
            val albumTracks = runCatching { album(albumSummary.id).tracks }.getOrDefault(emptyList())
            tracks += albumTracks.take((limit - tracks.size).toInt())
        }
    return tracks
}

private fun androidDiagnostics(
    storageStats: AndroidStorageStats,
    provider: MediaProvider?,
    validation: ConnectionValidation?,
    activeSourceId: String?,
    bassLoadReport: AndroidBassLoadReport,
    playbackEngine: AndroidPlaybackEngine,
    playbackState: PlaybackState,
    playbackProgress: PlaybackProgress,
    playbackQueue: PlaybackQueue,
    playbackSettings: PlaybackSettings,
    streamQuality: StreamQuality,
    nowPlaying: Track?,
    nowPlayingStation: InternetRadioStation?,
    nowPlayingStreamMetadata: PlaybackStreamMetadata,
    nowPlayingOpen: Boolean,
    visualizerVisible: Boolean,
    activeTlsSettings: NavidromeTlsSettings,
    selectedRoute: SharedRoute,
): NaviampDiagnosticsUi =
    NaviampDiagnosticsUi(
        sections = listOf(
            NaviampDiagnosticsSectionUi(
                title = "Connection",
                rows = listOf(
                    "Provider" to (provider?.displayName ?: "Not connected"),
                    "Source ID" to (activeSourceId ?: "None"),
                    "Server" to (validation?.serverVersion ?: "Unknown"),
                    "API" to (validation?.apiVersion ?: "Unknown"),
                    "Route" to selectedRoute.label,
                    "Skip TLS verification" to activeTlsSettings.insecureSkipTlsVerification.toString(),
                ),
            ),
            NaviampDiagnosticsSectionUi(
                title = "API calls",
                rows = androidApiCallRows(),
            ),
            NaviampDiagnosticsSectionUi(
                title = "BASS",
                rows = listOf(
                    "Available" to bassLoadReport.available.toString(),
                    "Loaded libraries" to "${bassLoadReport.loadedLibraries.size}: ${bassLoadReport.loadedLibraries.joinToString(", ")}",
                    "Failed libraries" to bassLoadReport.failedLibraries.ifEmpty { null }
                        ?.joinToString(", ") { "${it.name}: ${it.message}" }
                        .orEmpty()
                        .ifBlank { "None" },
                ),
            ),
            NaviampDiagnosticsSectionUi(
                title = "Playback",
                rows = listOf(
                    "Engine" to playbackEngine.name,
                    "State" to playbackState.label(),
                    "Now playing" to (nowPlaying?.title ?: nowPlayingStation?.name ?: "None"),
                    "Stream title" to (nowPlayingStreamMetadata.title ?: "None"),
                    "Now Playing screen" to nowPlayingOpen.toString(),
                    "Queue" to "${playbackQueue.tracks.size} tracks, index ${playbackQueue.currentIndex}",
                    "Position" to playbackProgress.positionSeconds?.let { "%.1fs".format(it) }.orEmpty().ifBlank { "Unknown" },
                    "Duration" to playbackProgress.durationSeconds?.let { "%.1fs".format(it) }.orEmpty().ifBlank { "Unknown" },
                    "Transcoded" to if (streamQuality is StreamQuality.Transcoded) "Yes" else "No",
                    "Stream quality" to streamQuality.streamQualityLabel(),
                    "ReplayGain" to playbackSettings.replayGainMode.name,
                    "Gapless" to playbackSettings.gaplessEnabled.toString(),
                    "Crossfade" to "${playbackSettings.crossfadeDurationSeconds}s",
                    "Visualizer" to visualizerVisible.toString(),
                ),
            ),
            NaviampDiagnosticsSectionUi(
                title = "Storage",
                rows = listOf(
                    "Database" to storageStats.databaseName,
                    "Saved sources" to storageStats.mediaSourceCount.toString(),
                    "Saved sessions" to storageStats.playbackSessionCount.toString(),
                    "Library index" to "${storageStats.libraryArtistCount} artists, ${storageStats.libraryAlbumCount} albums, ${storageStats.libraryTrackCount} tracks",
                    "Images" to "${storageStats.imageCount} (${storageStats.imageBytes.bytesLabel()})",
                    "Provider responses" to storageStats.responseCount.toString(),
                    "Audio cache" to "${storageStats.audioCount} (${storageStats.audioBytes.bytesLabel()})",
                    "Downloads" to "${storageStats.downloadCount} (${storageStats.downloadBytes.bytesLabel()})",
                    "Waveforms" to "${storageStats.audioWaveformCount} (${storageStats.audioWaveformBytes.bytesLabel()})",
                    "Lyrics" to storageStats.lyricsBytes.bytesLabel(),
                ),
            ),
            NaviampDiagnosticsSectionUi(
                title = "Provider features",
                rows = provider?.capabilities?.let { capabilities ->
                    listOf(
                        "Streaming transcode" to capabilities.supportsStreamingTranscode.toString(),
                        "Download transcode" to capabilities.supportsDownloadTranscode.toString(),
                        "Artist radio" to capabilities.supportsArtistRadio.toString(),
                        "Album radio" to capabilities.supportsAlbumRadio.toString(),
                        "Track radio" to capabilities.supportsTrackRadio.toString(),
                        "Track favorites" to capabilities.supportsTrackFavorites.toString(),
                        "Track ratings" to capabilities.supportsTrackRatings.toString(),
                        "Play reporting" to capabilities.supportsPlayReporting.toString(),
                    )
                }.orEmpty(),
            ),
        ),
    )

private fun androidApiCallRows(): List<Pair<String, String>> =
    buildList {
        NavidromeApiCallHistory.recent(8).forEach { call ->
            add("Navidrome ${call.method} ${call.endpoint}" to call.summary())
        }
        AndroidPopularTracksApiCallHistory.recent(8).forEach { call ->
            add("Deezer ${call.endpoint}" to call.summary())
        }
        AndroidLrclibApiCallHistory.recent(8).forEach { call ->
            add("LRCLIB ${call.endpoint}" to call.summary())
        }
    }.ifEmpty {
        listOf("Recent calls" to "None yet")
    }

private fun NavidromeApiCall.summary(): String =
    buildApiCallSummary(success = success, durationMillis = durationMillis, errorMessage = errorMessage, url = sanitizedUrl)

private fun AndroidPopularTracksApiCall.summary(): String =
    buildApiCallSummary(success = success, durationMillis = durationMillis, errorMessage = errorMessage, url = sanitizedUrl)

private fun AndroidLrclibApiCall.summary(): String =
    buildApiCallSummary(success = success, durationMillis = durationMillis, errorMessage = errorMessage, url = sanitizedUrl)

private fun buildApiCallSummary(
    success: Boolean,
    durationMillis: Long,
    errorMessage: String?,
    url: String,
): String =
    "${if (success) "OK" else "ERR"} ${durationMillis} ms" +
        errorMessage?.let { " - $it" }.orEmpty() +
        "\n$url"

private fun deleteDirectoryContents(directory: File) {
    if (!directory.exists()) return
    directory.walkBottomUp()
        .filter { it != directory }
        .forEach { it.delete() }
}

private fun Double.secondsToMillis(): Long =
    (coerceAtLeast(0.0) * 1_000.0).toLong()

private fun normalizeAndroidAutoSeekPositionMillis(
    rawPositionMillis: Long,
    durationSeconds: Double?,
): Long {
    val raw = rawPositionMillis.coerceAtLeast(0L)
    val duration = durationSeconds ?: return raw
    if (raw == 0L) return 0L
    val durationMillis = duration.secondsToMillis()
    return if (raw <= duration.toLong() + 1L && durationMillis > 1_000L) {
        raw.toDouble().secondsToMillis()
    } else {
        raw
    }.coerceIn(0L, durationMillis)
}

private const val PendingSeekToleranceSeconds = 2.0
private const val PendingSeekStaleProgressWindowMillis = 1_500L
private const val AndroidAutoProgressPublishIntervalMillis = 1_000L
private const val AndroidNowPlayingHeartbeatIntervalMillis = 30_000L
private const val AndroidPlaylistDetailRefreshIntervalMillis = 60_000L
private const val AndroidAutoIgnoreZeroSeekAfterSeconds = 3.0
private const val AndroidGaplessPrepareWindowSeconds = 2.0
private const val AndroidSidecarPrepDepth = 5
private const val AndroidPlaybackSessionSaveIntervalMillis = 5_000L
private const val AndroidLibraryFreshnessCheckIntervalMillis = 60_000L
private const val AndroidMaxDownloadBytes = 2L * 1024L * 1024L * 1024L
private const val AndroidLibraryAlbumPageSize = 500
private const val AndroidLibraryArtistLimit = 100_000
private const val AndroidPopularRadioSeedLimit = 5
private const val AndroidInitialSimilarRadioCount = 10
private const val AndroidPopularTrackFallbackLimit = 120L
private const val ProviderArtistPopularTrackAlbumFallbackLimit = 30
private const val PopularTracksFetchLimit = 25
private const val PopularTracksDisplayLimit = 10
private const val SimilarArtistsFetchLimit = 20
private const val SimilarArtistsDisplayLimit = 10
private const val PlayReportDurationFraction = 0.5
private const val PlayReportMaxThresholdSeconds = 240.0
private val AndroidSimilarRadioExpansionCounts = listOf(25, 50)
