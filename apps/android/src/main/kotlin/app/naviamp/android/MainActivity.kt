package app.naviamp.android

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.naviamp.android.playback.AndroidAudioWaveformAnalyzer
import app.naviamp.android.playback.AndroidBassJni
import app.naviamp.android.playback.AndroidBassPlaybackEngine
import app.naviamp.android.playback.AndroidBassNativeLoader
import app.naviamp.android.playback.AndroidPlaybackEngine
import app.naviamp.android.playback.AndroidPlaybackTls
import app.naviamp.android.playback.AndroidPlaybackNotificationControls
import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Lyrics
import app.naviamp.domain.Playlist
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.Track
import app.naviamp.domain.app.NaviampContentState
import app.naviamp.domain.app.NaviampNavigationState
import app.naviamp.domain.app.NaviampRoute
import app.naviamp.domain.provider.AlbumListType
import app.naviamp.domain.provider.ConnectionValidation
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
import app.naviamp.domain.lyrics.selectPreferredLyrics
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode
import app.naviamp.domain.radio.RadioService
import app.naviamp.domain.settings.ConnectionFormState
import app.naviamp.domain.settings.PlaybackSessionSettings
import app.naviamp.domain.settings.PreviousButtonBehavior
import app.naviamp.domain.waveform.AudioWaveform
import app.naviamp.provider.navidrome.NavidromeConnection
import app.naviamp.provider.navidrome.NavidromeProvider
import app.naviamp.provider.navidrome.NavidromeTlsSettings
import app.naviamp.provider.navidrome.toNavidromeConnection
import app.naviamp.ui.AndroidTrackRowUi
import app.naviamp.ui.NaviampNowPlayingItemUi
import app.naviamp.ui.NaviampPlaylistChoiceUi
import app.naviamp.ui.NaviampSharedAppShell
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
import app.naviamp.ui.toSharedSearchResultsUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
        setContent {
            NaviampAndroidApp()
        }
    }
}

@Composable
private fun NaviampAndroidApp() {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val bassLoadReport = remember { AndroidBassNativeLoader.loadBundledLibraries() }
    val bassJni = remember {
        AndroidBassJni.load().fold(
            onSuccess = { it },
            onFailure = { throw IllegalStateException("BASS is required for Android playback.", it) },
        )
    }
    val playbackEngine: AndroidPlaybackEngine = remember { AndroidBassPlaybackEngine(context, bassJni) }
    val waveformAnalyzer = remember { AndroidAudioWaveformAnalyzer(context, bassJni) }
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
    var editingConnection by remember { mutableStateOf(false) }
    var restoringConnection by remember { mutableStateOf(canAutoConnect) }
    var navigationState by remember { mutableStateOf(NaviampNavigationState()) }
    val selectedRoute = navigationState.route.toSharedRoute()
    var provider by remember { mutableStateOf<NavidromeProvider?>(null) }
    var activeSourceId by remember { mutableStateOf<String?>(savedProviderSource?.id) }
    val popularTracksService = remember(storage) {
        ArtistPopularTracksService(
            repository = storage,
            libraryTracksForArtist = { artistId, limit -> storage.libraryTracksForArtist(activeSourceId.orEmpty(), artistId, limit) },
            client = DeezerPopularTracksClient(AndroidPopularTracksHttpClient()),
        )
    }
    var restoredStartPositionSeconds by remember { mutableStateOf<Double?>(null) }
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
    var visualizerVisible by remember { mutableStateOf(false) }
    var pendingSeekPositionSeconds by remember { mutableStateOf<Double?>(null) }
    var pendingSeekIssuedAtMillis by remember { mutableStateOf<Long?>(null) }
    var playbackSessionToken by remember { mutableStateOf(0L) }
    var volumePercent by remember { mutableStateOf(100) }
    var waveformByTrackId by remember { mutableStateOf<Map<String, AudioWaveform>>(emptyMap()) }
    var playbackQueue by remember { mutableStateOf(PlaybackQueue()) }
    var preparedNextTrackId by remember { mutableStateOf<String?>(null) }
    var shuffledUpNextSnapshot by remember { mutableStateOf<List<Track>?>(null) }
    var repeatMode by remember { mutableStateOf(RepeatMode.Off) }
    var relatedTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var artistPopularTracksByArtistId by remember { mutableStateOf<Map<String, List<Track>>>(emptyMap()) }
    var lyricsVisible by remember { mutableStateOf(false) }
    var lyricsByTrackId by remember { mutableStateOf<Map<String, Lyrics?>>(emptyMap()) }
    var lyricsStatusByTrackId by remember { mutableStateOf<Map<String, String?>>(emptyMap()) }
    var playlistActionStatus by remember { mutableStateOf<String?>(null) }
    var sidecarPrepJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(bassLoadReport) {
        if (!bassLoadReport.available) {
            status = "BASS libraries are bundled but did not load on this device."
        }
    }

    LaunchedEffect(nowPlaying?.id, nowPlayingStation?.id) {
        visualizerFrame = null
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

    LaunchedEffect(playbackEngine, playbackSettings.crossfadeDurationSeconds) {
        (playbackEngine as? QueueAwarePlaybackEngine)
            ?.setCrossfadeDuration(playbackSettings.crossfadeDurationSeconds)
    }

    DisposableEffect(playbackEngine) {
        onDispose {
            AndroidPlaybackNotificationControls.clear()
            playbackEngine.release()
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
                activeProvider.streamUrl(StreamRequest(nextTrack.id, StreamQuality.Original))
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
                val localLyrics = activeProvider.lyrics(track.id)
                val onlineLyrics = if (playbackSettings.lrclibLyricsEnabled && (localLyrics == null || !localLyrics.synced)) {
                    lrclibLyricsClient.lyrics(track)
                } else {
                    null
                }
                selectPreferredLyrics(
                    providerLyrics = localLyrics,
                    embeddedLyrics = null,
                    onlineLyrics = onlineLyrics,
                )
            }
                .onSuccess { lyrics ->
                    lyricsByTrackId = lyricsByTrackId + (track.id.value to lyrics)
                    lyricsStatusByTrackId = lyricsStatusByTrackId + (track.id.value to null)
                }
                .onFailure { error ->
                    lyricsByTrackId = lyricsByTrackId + (track.id.value to null)
                    lyricsStatusByTrackId = lyricsStatusByTrackId + (track.id.value to (error.message ?: "Lyrics unavailable"))
                }
        }
    }

    suspend fun ensureWaveform(
        activeProvider: NavidromeProvider,
        sourceId: String?,
        track: Track,
    ): AudioWaveform? {
        val quality = StreamQuality.Original
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
                runCatching {
                    ensureWaveform(activeProvider, sourceId, track)
                }.onSuccess { waveform ->
                    if (waveform != null && sessionToken == playbackSessionToken) {
                        waveformByTrackId = waveformByTrackId + (track.id.value to waveform)
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
        sidecarPrepJob?.cancel()
        sidecarPrepJob = null
        preparedNextTrackId = null
        pendingSeekPositionSeconds = null
        pendingSeekIssuedAtMillis = null
        playbackProgress = PlaybackProgress.Unknown
        return playbackSessionToken
    }

    fun handlePlaybackProgressChanged(sessionToken: Long, progress: PlaybackProgress) {
        if (sessionToken != playbackSessionToken) return
        if (progress.positionSeconds == null && progress.durationSeconds == null) {
            pendingSeekPositionSeconds = null
            pendingSeekIssuedAtMillis = null
            playbackProgress = PlaybackProgress.Unknown
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
        prepareNextIfNeeded(sessionToken, playbackProgress)
    }

    var playAdjacentTrackAction: (Int) -> Unit = {}

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
            runCatching {
                activeProvider.streamUrl(StreamRequest(track.id, StreamQuality.Original))
            }.onSuccess { streamUrl ->
                playbackEngine.applyTlsSettings(activeTlsSettings)
                playbackQueue = PlaybackQueue(
                    tracks = nextQueue,
                    currentIndex = nextQueue.indexOfFirst { it.id == track.id }.takeIf { it >= 0 } ?: 0,
                )
                shuffledUpNextSnapshot = null
                nowPlaying = track
                nowPlayingStation = null
                nowPlayingStreamMetadata = PlaybackStreamMetadata()
                if (openNowPlaying) {
                    nowPlayingOpen = true
                }
                val sessionToken = beginPlaybackSession()
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
                        startPositionSeconds = startPositionSeconds,
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
        nowPlayingStation = station
        nowPlayingStreamMetadata = PlaybackStreamMetadata()
        nowPlayingOpen = true
        status = "Loading ${station.name}..."
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
        pendingSeekPositionSeconds = positionSeconds
        pendingSeekIssuedAtMillis = System.currentTimeMillis()
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

    AndroidPlaybackNotificationControls.onPlayPause = {
        when (playbackState) {
            PlaybackState.Playing -> playbackEngine.pause()
            else -> playbackEngine.resume()
        }
    }
    AndroidPlaybackNotificationControls.onPrevious = { playAdjacentTrack(-1) }
    AndroidPlaybackNotificationControls.onNext = { playAdjacentTrack(1) }
    AndroidPlaybackNotificationControls.onStop = { playbackEngine.stop() }

    fun applyTrackMetadataUpdate(updatedTrack: Track) {
        nowPlaying = nowPlaying?.let { if (it.id == updatedTrack.id) updatedTrack else it }
        tracks = tracks.map { if (it.id == updatedTrack.id) updatedTrack else it }
        contentState = contentState.copy(
            searchResults = searchResults.copy(
                tracks = searchResults.tracks.map { if (it.id == updatedTrack.id) updatedTrack else it },
            ),
            albumDetail = albumDetail?.copy(
                tracks = albumDetail.tracks.map { if (it.id == updatedTrack.id) updatedTrack else it },
            ),
        )
    }

    fun openArtistDetails(artistId: app.naviamp.domain.ArtistId, fallbackName: String? = null) {
        val activeProvider = provider ?: return
        val sourceId = activeSourceId
        scope.launch {
            status = "Loading ${fallbackName ?: "artist"}..."
            runCatching { activeProvider.artist(artistId) }
                .onSuccess { detail ->
                    contentState = contentState.showArtist(detail)
                    nowPlayingOpen = false
                    navigationState = navigationState.copy(route = NaviampRoute.Library)
                    status = "Connected."
                    if (sourceId != null) {
                        scope.launch {
                            runCatching {
                                popularTracksService.popularTracks(
                                    sourceId = sourceId,
                                    artist = detail.artist,
                                    limit = PopularTracksFetchLimit,
                                )
                            }.onSuccess { matches ->
                                artistPopularTracksByArtistId =
                                    artistPopularTracksByArtistId + (
                                        artistId.value to matches
                                            .map { it.matchedTrack }
                                            .take(PopularTracksDisplayLimit)
                                        )
                            }
                        }
                    }
                }
                .onFailure { error -> status = error.message ?: "Artist failed to load." }
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
            runCatching { playlistTracksById[playlist.id] ?: activeProvider.playlistTracks(playlist.id) }
                .onSuccess { playlistTracks ->
                    playlistTracksById = playlistTracksById + (playlist.id to playlistTracks)
                    contentState = contentState.showPlaylist(playlist, playlistTracks)
                    tracks = playlistTracks
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
                runCatching { playlistTracksById[playlist.id] ?: activeProvider.playlistTracks(playlist.id) }
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

    fun downloadTrack(track: Track) {
        val activeProvider = provider ?: return
        scope.launch {
            status = "Preparing ${track.title} download..."
            runCatching {
                activeProvider.streamUrl(StreamRequest(track.id, StreamQuality.Original))
            }.onSuccess { streamUrl ->
                val extension = track.audioInfo?.codec?.lowercase()?.let { codec ->
                    when (codec) {
                        "mpeg", "mp3" -> "mp3"
                        "flac" -> "flac"
                        "opus" -> "opus"
                        "aac" -> "aac"
                        else -> codec
                    }
                } ?: "audio"
                val fileName = "${track.artistName} - ${track.title}"
                    .replace(Regex("""[\\/:*?"<>|]"""), "_")
                    .take(120)
                    .ifBlank { track.id.value } + ".$extension"
                val request = DownloadManager.Request(Uri.parse(streamUrl))
                    .setTitle(track.title)
                    .setDescription(track.artistName)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                manager.enqueue(request)
                status = "Downloading ${track.title}."
            }.onFailure { error ->
                status = error.message ?: "Could not start download."
            }
        }
    }

    fun restorePlaybackSession(sourceId: String): Boolean {
        val session = storage.loadPlaybackSession(sourceId) ?: return false
        session.internetRadioStation?.toStation()?.let { station ->
            nowPlaying = null
            nowPlayingStation = station
            nowPlayingStreamMetadata = PlaybackStreamMetadata()
            playbackQueue = PlaybackQueue()
            playbackProgress = PlaybackProgress.Unknown
            restoredStartPositionSeconds = null
            nowPlayingOpen = true
            status = "Restored ${station.name}. Press play to resume."
            return true
        }

        val sessionTracks = session.toTracks()
        val currentTrack = session.currentTrack() ?: return false
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
        status = "Restored ${currentTrack.title}. Press play to resume."
        return true
    }

    fun savePlaybackSession() {
        val sourceId = activeSourceId ?: return
        val station = nowPlayingStation
        if (station != null) {
            storage.savePlaybackSession(
                sourceId = sourceId,
                session = PlaybackSessionSettings.fromInternetRadioStation(station),
            )
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
                homeState = loadBrowseState(nextProvider)
                preloadPlaylistTracks(nextProvider, homeState.playlists)
                val mediaSource = storage.upsertNavidromeSource(
                    connection = connection,
                    cacheNamespace = nextProvider.cacheNamespace,
                    providerId = nextProvider.id.value,
                )
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
                if (connectionForm.password.isBlank() && savedProviderConnection != null) {
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
        playbackProgress.positionSeconds,
    ) {
        if (activeSourceId == null || (nowPlaying == null && nowPlayingStation == null)) return@LaunchedEffect
        delay(1_000)
        savePlaybackSession()
    }

    NaviampSharedAppShell(
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
        supportsReplayGain = playbackEngine.supportsReplayGain,
        supportsGapless = playbackEngine.supportsGapless,
        supportsCrossfade = playbackEngine.supportsCrossfade,
        query = query,
        home = homeState.toSharedHomeUi(
            coverArtUrl = { coverArtId -> coverArtId?.let { provider?.coverArtUrl(it) } },
            playlistTracksById = playlistTracksById,
        ),
        searchResults = searchResults.toSharedSearchResultsUi { coverArtId -> coverArtId?.let { provider?.coverArtUrl(it) } },
        libraryArtists = homeState.artists.map { artist ->
            artist.toSharedMediaItemUi { coverArtId -> coverArtId?.let { provider?.coverArtUrl(it) } }
        },
        playlistItems = homeState.playlists.map {
            it.toSharedMediaItemUi(
                coverArtUrl = { coverArtId -> coverArtId?.let { provider?.coverArtUrl(it) } },
                tracks = playlistTracksById[it.id].orEmpty(),
            )
        },
        recentPlaylistIds = recentPlaylistIds,
        playlistSortMode = playlistSortMode,
        radioStationItems = homeState.radioStations.map { it.toSharedMediaItemUi() },
        albumDetail = albumDetail?.toSharedAlbumDetailUi { coverArtId -> coverArtId?.let { provider?.coverArtUrl(it) } },
        artistDetail = artistDetail?.toSharedArtistDetailUi(
            coverArtUrl = { coverArtId -> coverArtId?.let { provider?.coverArtUrl(it) } },
            popularTracks = artistPopularTracksByArtistId[artistDetail.artist.id.value].orEmpty(),
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
        onAlbumRadio = { detail ->
            val loadedAlbumTracks = albumDetail?.tracks.orEmpty()
            val album = albumDetail?.album ?: return@NaviampSharedAppShell
            startAlbumRadio(album, loadedAlbumTracks)
        },
        onAlbumAddToQueue = {
            appendTracksToQueue(albumDetail?.tracks.orEmpty(), "album tracks")
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
        onArtistSelected = { selectedArtist ->
            openArtistDetails(app.naviamp.domain.ArtistId(selectedArtist.id), selectedArtist.title)
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
        onPlaylistBack = {
            contentState = contentState.copy(
                selectedPlaylist = null,
                selectedPlaylistTracks = emptyList(),
            )
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
                contentState = contentState.clearDetails()
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
            visualizerVisible = !visualizerVisible
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
            val activeProvider = provider ?: return@NaviampSharedAppShell
            val currentTrack = nowPlaying ?: return@NaviampSharedAppShell
            if (!activeProvider.capabilities.supportsTrackFavorites) return@NaviampSharedAppShell
            scope.launch {
                val favorite = currentTrack.favoritedAtIso8601 == null
                runCatching {
                    activeProvider.setTrackFavorite(currentTrack.id, favorite)
                }.onSuccess {
                    applyTrackMetadataUpdate(
                        currentTrack.copy(favoritedAtIso8601 = if (favorite) "android-local" else null),
                    )
                }.onFailure { error ->
                    status = error.message ?: "Could not update favorite."
                }
            }
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
    return HomeService(
        provider = provider,
        date = HomeDate(year = today.year, dayOfYear = today.dayOfYear),
    ).load()
}

private fun Track.coverArtUrl(provider: NavidromeProvider?): String? =
    (coverArtId ?: albumId?.value)?.let { provider?.coverArtUrl(it) }

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

private fun ReplayGainMode.forEngine(playbackEngine: AndroidPlaybackEngine): ReplayGainMode =
    if (playbackEngine.supportsReplayGain) this else ReplayGainMode.Off

private fun Track.isInternetRadioTrack(): Boolean =
    id.value.startsWith("internet-radio:")

private const val PendingSeekToleranceSeconds = 2.0
private const val PendingSeekStaleProgressWindowMillis = 1_500L
private const val AndroidGaplessPrepareWindowSeconds = 2.0
private const val AndroidSidecarPrepDepth = 5
private const val AndroidPopularRadioSeedLimit = 5
private const val AndroidInitialSimilarRadioCount = 10
private const val PopularTracksFetchLimit = 25
private const val PopularTracksDisplayLimit = 10
private val AndroidSimilarRadioExpansionCounts = listOf(25, 50)
