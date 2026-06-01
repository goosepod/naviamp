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
import app.naviamp.domain.ArtistId
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Lyrics
import app.naviamp.domain.Playlist
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.isInternetRadioTrack
import app.naviamp.domain.app.NaviampRoute
import app.naviamp.domain.provider.ConnectionValidation
import app.naviamp.domain.provider.PlaylistDetailRefreshIntervalMillis
import app.naviamp.domain.provider.allKnownTracks
import app.naviamp.domain.provider.playlistDetailAutoRefreshTarget
import app.naviamp.domain.provider.runPlaylistDetailAutoRefresh
import app.naviamp.domain.home.HomeDate
import app.naviamp.domain.home.HomeService
import app.naviamp.domain.media.albumDetailLoadErrorStatus
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackQueueController
import app.naviamp.domain.playback.PlaybackRequest
import app.naviamp.domain.playback.PlaybackReplayGain
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackVisualizerFrame
import app.naviamp.domain.playback.QueueAwarePlaybackEngine
import app.naviamp.domain.playback.ReplayGainMode
import app.naviamp.domain.playback.ReplayGainSource
import app.naviamp.domain.playback.VisualizerPlaybackEngine
import app.naviamp.domain.playback.label
import app.naviamp.domain.playback.PlaybackAdjacentAction
import app.naviamp.domain.playback.planPlaybackAdjacentAction
import app.naviamp.domain.playback.planPlaybackSeek
import app.naviamp.domain.playback.canReportPlaybackTrack
import app.naviamp.domain.playback.shouldSubmitPlayReport
import app.naviamp.domain.playback.SidecarTypeLyrics
import app.naviamp.domain.playback.SidecarTypeWaveform
import app.naviamp.domain.playback.lyricsLoadingStatus
import app.naviamp.domain.playback.lyricsUnavailableStatus
import app.naviamp.domain.playback.shouldLoadOnlineLyrics
import app.naviamp.domain.playback.sidecarPrepPlan
import app.naviamp.domain.playback.waveformUnavailableStatus
import app.naviamp.domain.playback.resolvePlaybackAudioSource
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
import app.naviamp.domain.settings.downloadStreamQuality
import app.naviamp.domain.settings.effectiveForEngine
import app.naviamp.domain.settings.playbackSettingsChange
import app.naviamp.domain.settings.streamQualityForNetwork
import app.naviamp.domain.smartplaylist.SmartPlaylistDefinition
import app.naviamp.domain.waveform.AudioWaveform
import app.naviamp.provider.navidrome.NavidromeConnection
import app.naviamp.provider.navidrome.NavidromeApiCall
import app.naviamp.provider.navidrome.NavidromeApiCallHistory
import app.naviamp.provider.navidrome.NavidromeProvider
import app.naviamp.provider.navidrome.toNavidromeConnection
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
import app.naviamp.ui.SharedHomeUi
import app.naviamp.ui.SharedMediaItemUi
import app.naviamp.ui.SharedPlaylistDetailUi
import app.naviamp.ui.SharedPlaylistSortMode
import app.naviamp.ui.SharedRoute
import app.naviamp.ui.SharedSearchResultsUi
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
import app.naviamp.ui.resetAndroidPlatformCoverArtByteLoader
import app.naviamp.ui.setAndroidPlatformCoverArtByteLoader
import app.naviamp.ui.toSharedSearchResultsUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
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
    val audioCacheKeysInFlight = remember { mutableSetOf<String>() }
    DisposableEffect(storage) {
        onDispose { storage.close() }
    }
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
    val savedPlaybackSettings = remember { settingsStore.loadPlaybackSettings().effectiveForEngine(playbackEngine) }
    val appState = rememberAndroidAppState(
        savedConnection = savedConnection,
        savedPlaybackSettings = savedPlaybackSettings,
        canAutoConnect = canAutoConnect,
        savedSourceId = savedProviderSource?.id,
        initialStorageStats = storage.stats(),
        initialOpenNowPlayingRequest = openNowPlayingRequest,
        initialAutoPlayMediaIdRequest = autoPlayMediaIdRequest,
        initialAutoCommandRequest = autoCommandRequest,
        initialSelectedVisualizer = NaviampVisualizer.entries.firstOrNull {
            it.name == settingsStore.loadVisualizerSettings().selectedVisualizer
        }
            ?: NaviampVisualizer.AudioSphere,
    )
    val playbackQueueController = remember { PlaybackQueueController(appState.playbackQueue) }
    with(appState) {
    DisposableEffect(provider) {
        setAndroidPlatformCoverArtByteLoader { url ->
            provider
                ?.takeIf { it.ownsUrl(url) }
                ?.bytes(url)
        }
        onDispose { resetAndroidPlatformCoverArtByteLoader() }
    }
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
                    provider
                        ?.tracksForArtist(artist.id, limit.coerceAtMost(AndroidPopularTrackFallbackLimit))
                        .orEmpty()
                        .also { fetchedTracks ->
                            if (sourceId != null && fetchedTracks.isNotEmpty()) {
                                storage.upsertLibraryTracks(sourceId, fetchedTracks)
                            }
                        }
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

    AndroidAppRuntimeEffects(
        state = appState,
        bassLoadReport = bassLoadReport,
        playbackEngine = playbackEngine,
        openNowPlayingRequest = openNowPlayingRequest,
        autoPlayMediaIdRequest = autoPlayMediaIdRequest,
        autoCommandRequest = autoCommandRequest,
    )

    fun activeQueue(): List<Track> =
        playbackQueue.tracks.ifEmpty { allKnownTracks(searchResults, albumDetail) }

    fun findKnownTrack(trackId: String): Track? =
        findAndroidKnownTrack(appState, trackId, activeQueue())

    fun appendTracksToQueue(tracksToAdd: List<Track>, label: String = "tracks") {
        appendAndroidTracksToQueue(appState, playbackQueueController, tracksToAdd, label)
    }

    fun nextQueueIndex(): Int? {
        val currentTrack = nowPlaying ?: return null
        val queue = activeQueue()
        val currentIndex = queue.indexOfFirst { it.id == currentTrack.id }
        if (currentIndex < 0) return null
        playbackQueueController.replaceQueue(
            queue = PlaybackQueue(tracks = queue, currentIndex = currentIndex),
            clearPreparedNext = false,
        )
        playbackQueueController.setRepeatMode(repeatMode)
        return playbackQueueController.nextGaplessQueueIndex()
    }

    fun currentStreamQuality(): StreamQuality =
        playbackSettings.streamQualityForNetwork(context.isActiveNetworkMobileData())

    fun currentDownloadQuality(): StreamQuality =
        playbackSettings.downloadStreamQuality()

    suspend fun cacheAudioTrackForPlayback(
        sourceId: String,
        activeProvider: NavidromeProvider,
        track: Track,
        quality: StreamQuality,
    ): File? {
        if (track.isInternetRadioTrack()) return null
        resolvePlaybackAudioSource(
            sourceId = sourceId,
            track = track,
            quality = quality,
            audioCachingEnabled = true,
            downloadedAudio = { activeSourceId, trackId, requestedQuality ->
                storage.downloadedAudioFile(activeSourceId, trackId, requestedQuality)?.file
            },
            cachedAudio = { activeSourceId, trackId, requestedQuality ->
                storage.cachedAudioFile(activeSourceId, trackId, requestedQuality)?.file
            },
        ).localAudio?.let { return it }

        val cacheKey = "${sourceId}:${track.id.value}:$quality"
        if (!audioCacheKeysInFlight.add(cacheKey)) {
            return storage.cachedAudioFile(sourceId, track.id, quality)?.file
        }

        return try {
            storage.cacheAudioTrack(sourceId, activeProvider, track, quality).file
        } finally {
            audioCacheKeysInFlight.remove(cacheKey)
        }
    }

    fun startAudioPrefetch(
        sessionToken: Long,
        activeProvider: NavidromeProvider,
        queue: PlaybackQueue,
    ) {
        audioPrefetchJob?.cancel()
        val sourceId = activeSourceId ?: return
        val quality = currentStreamQuality()
        val tracksToPrefetch = queue.tracks
            .drop(queue.currentIndex.coerceAtLeast(0))
            .take(AndroidAudioPrefetchDepth)
            .filterNot { it.isInternetRadioTrack() }
        if (tracksToPrefetch.isEmpty()) return

        audioPrefetchJob = scope.launch {
            tracksToPrefetch.forEach { track ->
                if (sessionToken != playbackSessionToken) return@launch
                runCatching {
                    cacheAudioTrackForPlayback(sourceId, activeProvider, track, quality)
                }.onSuccess { file ->
                    if (file != null) {
                        android.util.Log.i("NaviampCache", "Prefetched audio title=${track.title} path=${file.name}")
                    }
                }.onFailure { error ->
                    android.util.Log.w("NaviampCache", "Audio prefetch failed title=${track.title}", error)
                }
            }
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
        if (!playbackQueueController.shouldPrepareNext(nextIndex)) return
        val activeProvider = provider ?: return
        val sourceId = activeSourceId
        playbackQueueController.markPreparedNext(nextIndex)
        scope.launch {
            runCatching {
                val quality = currentStreamQuality()
                val audioSourcePlan = resolvePlaybackAudioSource(
                    sourceId = sourceId,
                    track = nextTrack,
                    quality = quality,
                    audioCachingEnabled = true,
                    downloadedAudio = { activeSourceId, trackId, requestedQuality ->
                        storage.downloadedAudioFile(activeSourceId, trackId, requestedQuality)?.file
                    },
                    cachedAudio = { activeSourceId, trackId, requestedQuality ->
                        storage.cachedAudioFile(activeSourceId, trackId, requestedQuality)?.file
                    },
                )
                audioSourcePlan.localAudio?.toURI()?.toString()
                    ?: activeProvider.streamUrl(audioSourcePlan.target.providerStreamRequest)
            }.onSuccess { streamUrl ->
                if (sessionToken != playbackSessionToken) return@onSuccess
                queueAwareEngine.prepareNext(
                    PlaybackRequest(
                        url = streamUrl,
                        mediaId = nextTrack.id.value,
                        replayGainMode = playbackSettings.replayGainMode,
                        replayGain = nextTrack.replayGain?.let { PlaybackReplayGain(it, ReplayGainSource.Provider) },
                    ),
                )
            }.onFailure {
                playbackQueueController.clearPreparedNext()
            }
        }
    }

    fun loadLyrics(track: Track) {
        val activeProvider = provider ?: return
        if (lyricsByTrackId.containsKey(track.id.value) || lyricsStatusByTrackId[track.id.value] != null) return
        lyricsStatusByTrackId = lyricsStatusByTrackId + (track.id.value to lyricsLoadingStatus(playbackSettings.lrclibLyricsEnabled))
        scope.launch {
            runCatching {
                val sourceId = activeSourceId
                val quality = currentStreamQuality()
                val audioFile = resolvePlaybackAudioSource(
                    sourceId = sourceId,
                    track = track,
                    quality = quality,
                    audioCachingEnabled = true,
                    downloadedAudio = { activeSourceId, trackId, requestedQuality ->
                        storage.downloadedAudioFile(activeSourceId, trackId, requestedQuality)?.file
                    },
                    cachedAudio = { activeSourceId, trackId, requestedQuality ->
                        storage.cachedAudioFile(activeSourceId, trackId, requestedQuality)?.file
                    },
                ).localAudio
                val embeddedLyrics = audioFile?.let(::embeddedLyricsFromAudioFile)
                val localLyrics = activeProvider.lyrics(track.id)
                val onlineLyrics = if (
                    shouldLoadOnlineLyrics(
                        onlineLyricsEnabled = playbackSettings.lrclibLyricsEnabled,
                        providerLyrics = localLyrics,
                        embeddedLyrics = embeddedLyrics,
                    )
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
                            sidecarType = SidecarTypeLyrics,
                            success = true,
                        )
                    }
                }
                .onFailure { error ->
                    lyricsByTrackId = lyricsByTrackId + (track.id.value to null)
                    val message = lyricsUnavailableStatus(error)
                    lyricsStatusByTrackId = lyricsStatusByTrackId + (track.id.value to message)
                    activeSourceId?.let { sourceId ->
                        storage.recordSidecarStatus(
                            sourceId = sourceId,
                            trackId = track.id,
                            quality = StreamQuality.Original,
                            sidecarType = SidecarTypeLyrics,
                            success = false,
                            errorMessage = message,
                        )
                    }
                }
        }
    }

    fun handleConnectionFormChanged(form: ConnectionFormState) {
        appState.applyConnectionForm(form)
    }

    fun handlePlaybackSettingsChanged(settings: PlaybackSettings) {
        val change = playbackSettingsChange(settings, playbackEngine, previous = playbackSettings)
        playbackSettings = change.settings
        settingsStore.savePlaybackSettings(change.settings)
        if (change.shouldReloadLyricsSidecars) {
            lyricsByTrackId = emptyMap()
            lyricsStatusByTrackId = emptyMap()
            if (lyricsVisible && nowPlayingOpen) nowPlaying?.let(::loadLyrics)
        }
    }

    fun handleClearCache() {
        handleAndroidClearCache(context, appState, storage)
    }

    fun handleClearLibrary() {
        handleAndroidClearLibrary(appState, storage)
    }

    fun handleResetDatabase() {
        handleAndroidResetDatabase(
            context = context,
            state = appState,
            storage = storage,
            settingsStore = settingsStore,
            playbackEngine = playbackEngine,
            queueController = playbackQueueController,
        )
    }

    val searchController = remember(appState) { AndroidSearchController(appState) }

    fun handleSearch() {
        searchController.launchSearch(scope)
    }

    LaunchedEffect(query, provider) {
        searchController.load(query, debounce = true)
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
            cacheAudioTrackForPlayback(sourceId, activeProvider, track, quality)
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
        val plan = sidecarPrepPlan(
            queue = queue,
            depth = AndroidSidecarPrepDepth,
            onlineLyricsEnabled = playbackSettings.lrclibLyricsEnabled,
            lyricsVisible = lyricsVisible,
        )
        if (plan.tracks.isEmpty()) return
        val sourceId = activeSourceId
        sidecarPrepJob = scope.launch {
            plan.tracks.forEach { track ->
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
                            sidecarType = SidecarTypeWaveform,
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
                            sidecarType = SidecarTypeWaveform,
                            success = false,
                            errorMessage = waveformUnavailableStatus(error),
                        )
                    }
                }
                if (sessionToken != playbackSessionToken) return@launch
                if (plan.loadLyrics) {
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

    fun reportNowPlaying(track: Track) {
        val activeProvider = provider ?: return
        if (
            !canReportPlaybackTrack(
                supportsPlayReporting = activeProvider.capabilities.supportsPlayReporting,
                isInternetRadioTrack = track.isInternetRadioTrack(),
            )
        ) {
            return
        }
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
        val track = nowPlaying ?: return
        val durationSeconds = progress.durationSeconds ?: track.durationSeconds?.toDouble()
        if (
            !shouldSubmitPlayReport(
                supportsPlayReporting = activeProvider.capabilities.supportsPlayReporting,
                isInternetRadioTrack = track.isInternetRadioTrack(),
                activeSessionId = playbackSessionToken,
                submittedSessionId = submittedPlayReportSessionToken,
                positionSeconds = progress.positionSeconds,
                durationSeconds = durationSeconds,
            )
        ) {
            return
        }

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
        handleAndroidPlaybackProgressChanged(
            context = context,
            state = appState,
            sessionToken = sessionToken,
            progress = progress,
            maybeReportPlayed = ::maybeReportPlayed,
            prepareNextIfNeeded = ::prepareNextIfNeeded,
        )
    }

    var playAdjacentTrackAction: (Int) -> Unit = {}
    var restorePlaybackSessionAction: (String) -> Boolean = { false }

    fun savePlaybackSession() {
        saveAndroidPlaybackSession(appState, storage)
    }

    fun savePlaybackSessionThrottled(force: Boolean = false) {
        saveAndroidPlaybackSessionThrottled(appState, storage, force)
    }

    fun playTrack(
        track: Track,
        queue: List<Track>? = null,
        openNowPlaying: Boolean = true,
        startPositionSeconds: Double? = null,
        keepRadioQueueActive: Boolean = false,
    ) {
        playAndroidTrack(
            scope = scope,
            state = appState,
            storage = storage,
            playbackEngine = playbackEngine,
            playbackQueueController = playbackQueueController,
            track = track,
            queue = queue,
            openNowPlaying = openNowPlaying,
            startPositionSeconds = startPositionSeconds,
            keepRadioQueueActive = keepRadioQueueActive,
            activeQueue = ::activeQueue,
            currentStreamQuality = ::currentStreamQuality,
            savePlaybackSessionThrottled = ::savePlaybackSessionThrottled,
            reportNowPlaying = ::reportNowPlaying,
            loadRelatedTracks = ::loadRelatedTracks,
            loadLyrics = ::loadLyrics,
            startAudioPrefetch = ::startAudioPrefetch,
            startSidecarPrep = ::startSidecarPrep,
            handlePlaybackProgressChanged = ::handlePlaybackProgressChanged,
            playAdjacentTrack = playAdjacentTrackAction,
        )
    }

    fun playInternetRadioStation(station: InternetRadioStation) {
        playAndroidInternetRadioStation(
            scope = scope,
            state = appState,
            settingsStore = settingsStore,
            playbackEngine = playbackEngine,
            playbackQueueController = playbackQueueController,
            station = station,
            savePlaybackSessionThrottled = ::savePlaybackSessionThrottled,
            handlePlaybackProgressChanged = ::handlePlaybackProgressChanged,
        )
    }

    fun performSeek(positionSeconds: Double) {
        val currentTrack = nowPlaying
        val seekPlan = planPlaybackSeek(
            isInternetRadioTrack = currentTrack?.isInternetRadioTrack() == true,
            positionSeconds = positionSeconds,
            currentProgress = playbackProgress,
            trackDurationSeconds = currentTrack?.durationSeconds,
            streamQuality = currentStreamQuality(),
            shouldReplayTranscodedStream = true,
        ) ?: return
        if (seekPlan.shouldClearRestoredStartPosition) {
            restoredStartPositionSeconds = null
            pendingRestoreStartPositionSeconds = null
        }
        playbackProgress = seekPlan.progress
        val positionMillis = playbackProgress.positionSeconds?.secondsToMillis()
        val durationMillis = playbackProgress.durationSeconds?.secondsToMillis()
        AndroidPlaybackNotificationControls.positionMillis = positionMillis
        AndroidPlaybackNotificationControls.durationMillis = durationMillis
        AndroidPlaybackForegroundService.updateProgress(context, positionMillis, durationMillis)
        pendingSeekPositionSeconds = seekPlan.pendingSeekPositionSeconds
        pendingSeekIssuedAtMillis = System.currentTimeMillis()
        if (currentTrack != null && seekPlan.shouldReplayCurrent) {
            playTrack(
                track = currentTrack,
                queue = playbackQueue.tracks.takeIf { it.isNotEmpty() },
                openNowPlaying = false,
                startPositionSeconds = seekPlan.pendingSeekPositionSeconds,
            )
            return
        }
        playbackEngine.seek(seekPlan.pendingSeekPositionSeconds)
    }

    fun playAdjacentTrack(offset: Int) {
        when (
            val action = planPlaybackAdjacentAction(
                currentTrack = nowPlaying,
                activeQueue = activeQueue(),
                offset = offset,
                repeatMode = repeatMode,
                previousButtonBehavior = playbackSettings.previousButtonBehavior,
                positionSeconds = playbackProgress.positionSeconds,
                restartThresholdSeconds = 3.0,
            )
        ) {
            PlaybackAdjacentAction.None -> Unit
            PlaybackAdjacentAction.RestartCurrent -> performSeek(0.0)
            is PlaybackAdjacentAction.PlayTrack -> playTrack(
                action.track,
                action.queue,
                openNowPlaying = false,
            )
        }
    }
    playAdjacentTrackAction = ::playAdjacentTrack

    fun startTrackRadio(track: Track) {
        startAndroidTrackRadio(
            scope = scope,
            state = appState,
            queueController = playbackQueueController,
            track = track,
            playTrack = { seedTrack, queue -> playTrack(seedTrack, queue, keepRadioQueueActive = true) },
        )
    }

    fun startAlbumRadio(album: Album, loadedAlbumTracks: List<Track> = emptyList()) {
        startAndroidAlbumRadio(
            scope = scope,
            state = appState,
            queueController = playbackQueueController,
            album = album,
            loadedAlbumTracks = loadedAlbumTracks,
            playTrack = { seedTrack, queue -> playTrack(seedTrack, queue, keepRadioQueueActive = true) },
        )
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
        updateAndroidNotificationFavoriteState(appState, track)
    }

    fun applyTrackMetadataUpdate(updatedTrack: Track) {
        applyAndroidTrackMetadataUpdate(appState, playbackEngine, updatedTrack)
    }

    fun toggleCurrentFavorite() {
        toggleAndroidCurrentFavorite(scope, appState, playbackEngine)
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
    AndroidPlaybackNotificationControls.onStop = {
        savePlaybackSessionThrottled(force = true)
        playbackEngine.stop()
    }
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
                startAndroidRadioTracks(
                    scope = scope,
                    state = appState,
                    statusLabel = "Library Radio",
                    playTrack = { track, queue -> playTrack(track, queue) },
                ) { radioService ->
                    radioService.libraryRadio()
                }
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

    fun openArtistDetails(
        artistId: app.naviamp.domain.ArtistId,
        fallbackName: String? = null,
        pushCurrentArtist: Boolean = true,
    ) {
        openAndroidArtistDetails(
            scope = scope,
            state = appState,
            storage = storage,
            popularTracksService = popularTracksService,
            artistId = artistId,
            fallbackName = fallbackName,
            pushCurrentArtist = pushCurrentArtist,
        )
    }
    openArtistDetailsFromBack = ::openArtistDetails

    fun findSimilarArtists(artistId: app.naviamp.domain.ArtistId, artistName: String) {
        findAndroidSimilarArtists(scope, appState, similarArtistsService, artistId, artistName)
    }

    fun openExternalArtistUrl(url: String) {
        openAndroidExternalArtistUrl(context, appState, url)
    }

    LaunchedEffect(provider, selectedPlaylist?.id) {
        val target = playlistDetailAutoRefreshTarget(
            provider = provider,
            playlist = selectedPlaylist,
        ) ?: return@LaunchedEffect
        runPlaylistDetailAutoRefresh(
            target = target,
            waitForNextRefresh = {
                delay(PlaylistDetailRefreshIntervalMillis)
            },
        ) { activeProvider, playlist ->
            refreshAndroidPlaylistDetailsFromServer(
                state = appState,
                activeProvider = activeProvider,
                playlist = playlist,
                showLoadingStatus = false,
            )
        }
    }

    fun openPlaylistDetails(playlist: Playlist) {
        openAndroidPlaylistDetails(scope, appState, playlist)
    }

    fun playPlaylist(playlist: Playlist, shuffle: Boolean) {
        playAndroidPlaylist(scope, appState, playlist, shuffle, ::playTrack)
    }

    fun renamePlaylist(playlist: Playlist, name: String) {
        renameAndroidPlaylist(scope, appState, playlist, name)
    }

    fun deletePlaylist(playlist: Playlist) {
        deleteAndroidPlaylist(scope, appState, playlist)
    }

    fun preloadPlaylistTracks(activeProvider: NavidromeProvider, playlists: List<Playlist>) {
        preloadAndroidPlaylistTracks(scope, appState, activeProvider, playlists)
    }

    fun refreshAndroidPlaylists() {
        refreshAndroidPlaylists(scope, appState)
    }

    suspend fun saveSmartPlaylist(definition: SmartPlaylistDefinition) {
        saveAndroidSmartPlaylist(scope, appState, definition)
    }

    fun addTrackToPlaylist(track: Track, playlist: NaviampPlaylistChoiceUi?, newPlaylistName: String? = null) {
        addAndroidTrackToPlaylist(scope, appState, track, playlist, newPlaylistName)
    }

    fun addTracksToPlaylist(
        tracksToAdd: List<Track>,
        playlist: NaviampPlaylistChoiceUi?,
        newPlaylistName: String? = null,
        label: String = "tracks",
    ) {
        addAndroidTracksToPlaylist(scope, appState, tracksToAdd, playlist, newPlaylistName, label)
    }

    fun downloadTrack(track: Track) {
        downloadAndroidTrack(context, scope, appState, storage, track)
    }

    fun downloadTracks(tracksToDownload: List<Track>, label: String = "tracks") {
        downloadAndroidTracks(context, scope, appState, storage, tracksToDownload, label)
    }

    fun removeDownload(download: NaviampDownloadedTrackUi) {
        removeAndroidDownload(scope, appState, storage, download, ::findKnownTrack)
    }

    fun restorePlaybackSession(sourceId: String): Boolean {
        return restoreAndroidPlaybackSession(appState, storage, sourceId, ::loadRelatedTracks)
    }
    restorePlaybackSessionAction = ::restorePlaybackSession

    fun connectWithNavidromeConnection(connection: NavidromeConnection) {
        startNavidromeConnection(
            scope = scope,
            state = appState,
            connection = connection,
            storage = storage,
            playbackEngine = playbackEngine,
            preloadPlaylistTracks = ::preloadPlaylistTracks,
            restorePlaybackSession = ::restorePlaybackSession,
            startAndroidLibrarySync = { force -> startAndroidLibrarySync(scope, appState, storage, force) },
            checkAndroidLibraryFreshness = { checkAndroidLibraryFreshness(scope, appState, storage) },
        )
    }

    fun connectToNavidrome() {
        startNavidromeConnectionFromForm(
            scope = scope,
            state = appState,
            settingsStore = settingsStore,
            savedProviderConnection = savedProviderConnection,
            connectWithNavidromeConnection = ::connectWithNavidromeConnection,
        )
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

    AndroidAppPersistenceEffects(
        state = appState,
        storage = storage,
        savePlaybackSessionThrottled = ::savePlaybackSessionThrottled,
        checkAndroidLibraryFreshness = { checkAndroidLibraryFreshness(scope, appState, storage) },
    )

    val shellUiState = rememberAndroidAppShellUiState(
        state = appState,
        modifier = modifier,
        context = context,
        bassLoadReport = bassLoadReport,
        playbackEngine = playbackEngine,
    )

    fun handleShellTrackSelected(selectedTrack: AndroidTrackRowUi) {
        val playback = selectedAndroidTrackPlayback(appState, selectedTrack.id, activeQueue())
        if (playback == null) {
            status = "Track not found."
            return
        }
        val (track, currentTracks) = playback
        playTrack(track, currentTracks)
    }

    fun handleShellAlbumSelected(selectedAlbum: SharedMediaItemUi) {
        openAndroidAlbumDetails(scope, appState, storage, selectedAlbum)
    }

    fun handleShellArtistRadio(detail: SharedArtistDetailUi) {
        val artistId = app.naviamp.domain.ArtistId(detail.artist.id)
        startAndroidArtistRadio(
            scope = scope,
            state = appState,
            queueController = playbackQueueController,
            artistId = artistId,
            artistTitle = detail.artist.title,
            artist = artistDetail?.artist ?: app.naviamp.domain.Artist(artistId, detail.artist.title),
            playTrack = { seedTrack, queue -> playTrack(seedTrack, queue, keepRadioQueueActive = true) },
        )
    }

    fun loadArtistTracks(action: (List<Track>) -> Unit) {
        loadAndroidArtistTracks(scope, appState, action)
    }

    fun handleShellArtistShuffle() {
        loadArtistTracks { artistTracks ->
            val queue = artistTracks.distinctBy { it.id }.shuffled()
            queue.firstOrNull()?.let { playTrack(it, queue) }
                ?: run { status = "No artist tracks found." }
        }
    }

    fun handleShellArtistPopularRadio(detail: SharedArtistDetailUi) {
        startAndroidPopularTracksRadio(
            scope = scope,
            state = appState,
            queueController = playbackQueueController,
            artistTitle = detail.artist.title,
            popularTracks = artistPopularTracksByArtistId[detail.artist.id].orEmpty(),
            playTrack = { seedTrack, queue -> playTrack(seedTrack, queue, keepRadioQueueActive = true) },
        )
    }

    fun handleShellHomeStationSelected(station: SharedHomeStationUi) {
        startAndroidHomeStationRadio(
            scope = scope,
            state = appState,
            stationId = station.id,
            stationTitle = station.title,
            playTrack = { track, queue -> playTrack(track, queue) },
        )
    }

    fun handleShellResume() {
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
    }

    fun handleShellToggleShuffle() {
        val currentTrack = nowPlaying ?: return
        val queue = activeQueue()
        val currentIndex = queue.indexOfFirst { it.id == currentTrack.id }
        if (currentIndex < 0) return
        playbackQueueController.replaceQueue(PlaybackQueue(tracks = queue, currentIndex = currentIndex))
        val toggled = playbackQueueController.toggleUpcomingShuffle(shuffledUpNextSnapshot) ?: return
        playbackQueue = toggled.queue
        shuffledUpNextSnapshot = toggled.shuffledSnapshot
    }

    fun startTrackRadioQueue(track: Track, playSeed: Boolean) {
        startAndroidTrackRadioQueue(
            scope = scope,
            state = appState,
            queueController = playbackQueueController,
            track = track,
            playSeed = playSeed,
            playTrack = { seedTrack, queue -> playTrack(seedTrack, queue, keepRadioQueueActive = true) },
        )
    }

    fun handleShellTrackRadio() {
        val currentTrack = nowPlaying ?: return
        startTrackRadioQueue(currentTrack, playSeed = false)
    }

    fun handleShellQueueItemRadio(item: NaviampNowPlayingItemUi) {
        findKnownTrack(item.id)?.let { track -> startTrackRadioQueue(track, playSeed = true) }
    }

    fun handleShellGoToAlbum() {
        val activeProvider = provider ?: return
        val albumId = nowPlaying?.albumId ?: return
        scope.launch {
            runCatching { activeProvider.album(albumId) }
                .recoverCatching { error ->
                    activeSourceId
                        ?.let { sourceId ->
                            albumDetailsFromAndroidTrackFallback(
                                storage = storage,
                                sourceId = sourceId,
                                albumId = albumId,
                                fallbackTitle = nowPlaying?.albumTitle,
                                fallbackArtistName = nowPlaying?.artistName,
                            )
                        }
                        ?: throw error
                }
                .onSuccess { detail ->
                    contentState = contentState.showAlbum(detail)
                    nowPlayingOpen = false
                    navigationState = navigationState.copy(route = NaviampRoute.Library)
                }
                .onFailure { error -> status = albumDetailLoadErrorStatus(error) }
        }
    }

    fun handleShellRatingSelected(rating: Int?) {
        setAndroidCurrentTrackRating(scope, appState, playbackEngine, rating)
    }

    fun handleDownloadedTrackSelected(download: NaviampDownloadedTrackUi) {
        playAndroidDownloadedTrack(appState, download) { track, queue -> playTrack(track, queue) }
    }

    fun handleDownloadedTrackAddToPlaylist(download: NaviampDownloadedTrackUi, playlist: NaviampPlaylistChoiceUi?) {
        withAndroidDownloadedTrack(appState, download) { track -> addTrackToPlaylist(track, playlist) }
    }

    fun handleDownloadedTrackCreatePlaylistAndAdd(download: NaviampDownloadedTrackUi, name: String) {
        withAndroidDownloadedTrack(appState, download) { track ->
            addTrackToPlaylist(track, playlist = null, newPlaylistName = name)
        }
    }

    fun handleMixAlbumSelected(selectedAlbum: SharedMediaItemUi) {
        homeState.mixAlbums.firstOrNull { it.id.value == selectedAlbum.id }
            ?.let { startAlbumRadio(it) }
            ?: run { status = "Album not found." }
    }

    fun handleShellAlbumPlay(shuffle: Boolean) {
        val albumTracks = albumDetail?.tracks.orEmpty()
        val queue = if (shuffle) albumTracks.shuffled() else albumTracks
        queue.firstOrNull()?.let { playTrack(it, queue) }
            ?: run { status = "Album is empty." }
    }

    fun handleShellAlbumTrackSelected(selectedTrack: AndroidTrackRowUi) {
        val track = albumDetail?.tracks?.firstOrNull { it.id.value == selectedTrack.id }
            ?: findKnownTrack(selectedTrack.id)
        if (track == null) {
            status = "Track not found."
        } else {
            startTrackRadio(track)
        }
    }

    fun handleShellAlbumRadio() {
        val loadedAlbumTracks = albumDetail?.tracks.orEmpty()
        val album = albumDetail?.album ?: return
        startAlbumRadio(album, loadedAlbumTracks)
    }

    fun handleAlbumTrackDownload(selectedTrack: AndroidTrackRowUi) {
        withAndroidKnownTrack(appState, selectedTrack, activeQueue(), ::downloadTrack)
    }

    fun handleAlbumTrackAddToPlaylist(selectedTrack: AndroidTrackRowUi, playlist: NaviampPlaylistChoiceUi?) {
        withAndroidKnownTrack(appState, selectedTrack, activeQueue()) { track -> addTrackToPlaylist(track, playlist) }
    }

    fun handleAlbumTrackCreatePlaylistAndAdd(selectedTrack: AndroidTrackRowUi, name: String) {
        withAndroidKnownTrack(appState, selectedTrack, activeQueue()) { track ->
            addTrackToPlaylist(track, playlist = null, newPlaylistName = name)
        }
    }

    fun handleArtistPopularPlay(detail: SharedArtistDetailUi) {
        playAndroidArtistPopularTracks(appState, detail.artist.id) { track, queue -> playTrack(track, queue) }
    }

    fun handleArtistPopularTrackSelected(selectedTrack: AndroidTrackRowUi) {
        startAndroidArtistPopularTrackRadio(appState, selectedTrack.id, activeQueue(), ::startTrackRadio)
    }

    fun handleArtistPopularAddToQueue(detail: SharedArtistDetailUi) {
        appendAndroidArtistPopularTracksToQueue(appState, playbackQueueController, detail.artist.id)
    }

    fun handleTrackAddToQueue(selectedTrack: AndroidTrackRowUi) {
        withAndroidKnownTrack(appState, selectedTrack, activeQueue()) { track ->
            appendTracksToQueue(listOf(track), "track")
        }
    }

    fun handleTrackDownload(selectedTrack: AndroidTrackRowUi) {
        withAndroidKnownTrack(appState, selectedTrack, activeQueue(), ::downloadTrack)
    }

    fun handleTrackAddToPlaylist(selectedTrack: AndroidTrackRowUi, playlist: NaviampPlaylistChoiceUi?) {
        withAndroidKnownTrack(appState, selectedTrack, activeQueue()) { track -> addTrackToPlaylist(track, playlist) }
    }

    fun handleTrackCreatePlaylistAndAdd(selectedTrack: AndroidTrackRowUi, name: String) {
        withAndroidKnownTrack(appState, selectedTrack, activeQueue()) { track ->
            addTrackToPlaylist(track, playlist = null, newPlaylistName = name)
        }
    }

    fun handleSimilarArtistSelected(similarArtist: SharedSimilarArtistUi) {
        val artistId = similarArtist.localArtistId
        if (artistId == null) {
            status = "Artist is not in your library."
        } else {
            openArtistDetails(app.naviamp.domain.ArtistId(artistId), similarArtist.title)
        }
    }

    fun loadArtistAlbumTracks(selectedAlbum: SharedMediaItemUi, action: (List<Track>) -> Unit) {
        loadAndroidArtistAlbumTracks(scope, appState, storage, selectedAlbum, action)
    }

    fun handleArtistAlbumRadio(selectedAlbum: SharedMediaItemUi) {
        startAndroidArtistAlbumRadio(
            scope = scope,
            state = appState,
            selectedAlbum = selectedAlbum,
            startAlbumRadio = ::startAlbumRadio,
        )
    }

    fun handlePlaylistTrackSelected(selectedTrack: AndroidTrackRowUi) {
        val track = selectedPlaylistTracks.firstOrNull { it.id.value == selectedTrack.id } ?: findKnownTrack(selectedTrack.id)
        if (track == null) {
            status = "Track not found."
            return
        }
        playTrack(track, selectedPlaylistTracks.ifEmpty { listOf(track) })
    }

    fun handleRadioStationSelected(selectedStation: SharedMediaItemUi) {
        val station = homeState.radioStations.firstOrNull { it.id == selectedStation.id }
        if (station == null) {
            status = "Station not found."
            return
        }
        playInternetRadioStation(station)
    }

    fun handleNowPlayingAddToPlaylist(playlist: NaviampPlaylistChoiceUi?) {
        val currentTrack = nowPlaying ?: return
        addTrackToPlaylist(currentTrack, playlist)
    }

    fun handleNowPlayingCreatePlaylistAndAdd(name: String) {
        val currentTrack = nowPlaying ?: return
        addTrackToPlaylist(currentTrack, playlist = null, newPlaylistName = name)
    }

    fun handleShellGoToArtist() {
        val currentTrack = nowPlaying ?: return
        currentTrack.artistId?.let { openArtistDetails(it, currentTrack.artistName) }
    }

    val shellActions = androidAppShellActions(
        state = appState,
        playbackEngine = playbackEngine,
        settingsStore = settingsStore,
        handleConnectionFormChanged = ::handleConnectionFormChanged,
        connectToNavidrome = ::connectToNavidrome,
        handlePlaybackSettingsChanged = ::handlePlaybackSettingsChanged,
        handleClearCache = ::handleClearCache,
        handleClearLibrary = ::handleClearLibrary,
        handleResetDatabase = ::handleResetDatabase,
        handleSearch = ::handleSearch,
        startAndroidLibrarySync = { force -> startAndroidLibrarySync(scope, appState, storage, force) },
        handleShellTrackSelected = ::handleShellTrackSelected,
        handleDownloadedTrackSelected = ::handleDownloadedTrackSelected,
        handleDownloadedTrackAddToPlaylist = ::handleDownloadedTrackAddToPlaylist,
        handleDownloadedTrackCreatePlaylistAndAdd = ::handleDownloadedTrackCreatePlaylistAndAdd,
        removeDownload = ::removeDownload,
        handleShellAlbumSelected = ::handleShellAlbumSelected,
        handleMixAlbumSelected = ::handleMixAlbumSelected,
        handleShellAlbumPlay = ::handleShellAlbumPlay,
        handleShellAlbumTrackSelected = ::handleShellAlbumTrackSelected,
        handleShellAlbumRadio = ::handleShellAlbumRadio,
        appendTracksToQueue = ::appendTracksToQueue,
        downloadTracks = ::downloadTracks,
        addTracksToPlaylist = ::addTracksToPlaylist,
        handleAlbumTrackDownload = ::handleAlbumTrackDownload,
        handleAlbumTrackAddToPlaylist = ::handleAlbumTrackAddToPlaylist,
        handleAlbumTrackCreatePlaylistAndAdd = ::handleAlbumTrackCreatePlaylistAndAdd,
        handleShellArtistRadio = ::handleShellArtistRadio,
        handleShellArtistShuffle = ::handleShellArtistShuffle,
        loadArtistTracks = ::loadArtistTracks,
        handleArtistPopularPlay = ::handleArtistPopularPlay,
        handleShellArtistPopularRadio = ::handleShellArtistPopularRadio,
        handleArtistPopularTrackSelected = ::handleArtistPopularTrackSelected,
        handleArtistPopularAddToQueue = ::handleArtistPopularAddToQueue,
        handleTrackAddToQueue = ::handleTrackAddToQueue,
        handleTrackDownload = ::handleTrackDownload,
        handleTrackAddToPlaylist = ::handleTrackAddToPlaylist,
        handleTrackCreatePlaylistAndAdd = ::handleTrackCreatePlaylistAndAdd,
        findSimilarArtists = ::findSimilarArtists,
        handleSimilarArtistSelected = ::handleSimilarArtistSelected,
        openExternalArtistUrl = ::openExternalArtistUrl,
        openArtistDetails = ::openArtistDetails,
        handleArtistAlbumRadio = ::handleArtistAlbumRadio,
        loadArtistAlbumTracks = ::loadArtistAlbumTracks,
        openPlaylistDetails = ::openPlaylistDetails,
        playPlaylist = ::playPlaylist,
        renamePlaylist = ::renamePlaylist,
        deletePlaylist = ::deletePlaylist,
        saveSmartPlaylist = ::saveSmartPlaylist,
        closeActivePlaylist = ::closeActivePlaylist,
        handlePlaylistTrackSelected = ::handlePlaylistTrackSelected,
        handleRadioStationSelected = ::handleRadioStationSelected,
        handleShellHomeStationSelected = ::handleShellHomeStationSelected,
        closeActiveDetail = ::closeActiveDetail,
        handleShellResume = ::handleShellResume,
        playAdjacentTrack = ::playAdjacentTrack,
        performSeek = ::performSeek,
        handleShellToggleShuffle = ::handleShellToggleShuffle,
        loadLyrics = ::loadLyrics,
        handleShellTrackRadio = ::handleShellTrackRadio,
        handleNowPlayingAddToPlaylist = ::handleNowPlayingAddToPlaylist,
        handleNowPlayingCreatePlaylistAndAdd = ::handleNowPlayingCreatePlaylistAndAdd,
        downloadTrack = ::downloadTrack,
        handleShellGoToAlbum = ::handleShellGoToAlbum,
        handleShellGoToArtist = ::handleShellGoToArtist,
        handleShellQueueItemRadio = ::handleShellQueueItemRadio,
        findKnownTrack = ::findKnownTrack,
        addTrackToPlaylist = ::addTrackToPlaylist,
        toggleCurrentFavorite = ::toggleCurrentFavorite,
        handleShellRatingSelected = ::handleShellRatingSelected,
    )

    AndroidAppShellContent(shellUiState, shellActions)
    }
}
