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
import app.naviamp.domain.isInternetRadioTrack
import app.naviamp.domain.app.NaviampContentState
import app.naviamp.domain.app.NaviampNavigationState
import app.naviamp.domain.app.NaviampRoute
import app.naviamp.domain.provider.AlbumListType
import app.naviamp.domain.provider.ConnectionValidation
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.MediaSearchResults
import app.naviamp.domain.provider.SearchDebounceMillis
import app.naviamp.domain.provider.SearchResultLimit
import app.naviamp.domain.provider.allKnownTracks
import app.naviamp.domain.provider.addToPlaylistMutationUpdate
import app.naviamp.domain.provider.createPlaylistOrAddMissingTracks
import app.naviamp.domain.provider.normalizedSearchQuery
import app.naviamp.domain.provider.totalCount
import app.naviamp.domain.home.HomeContent
import app.naviamp.domain.home.HomeDate
import app.naviamp.domain.home.HomeService
import app.naviamp.domain.home.HomeStationLibrary
import app.naviamp.domain.home.HomeStationRandomAlbum
import app.naviamp.domain.home.parseHomeDecadeStationId
import app.naviamp.domain.home.parseHomeGenreStationId
import app.naviamp.domain.library.LibraryFreshness
import app.naviamp.domain.library.evaluateLibraryFreshness
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
import app.naviamp.domain.playback.planPlaybackSeek
import app.naviamp.domain.playback.shouldClearPendingSeek
import app.naviamp.domain.playback.shouldIgnoreProgressForPendingSeek
import app.naviamp.domain.playback.shouldRestartInsteadOfPrevious
import app.naviamp.domain.playback.shouldSubmitPlayReport
import app.naviamp.domain.popular.ArtistPopularTracksService
import app.naviamp.domain.popular.DeezerPopularTracksClient
import app.naviamp.domain.popular.SimilarArtistMatch
import app.naviamp.domain.popular.SimilarArtistsService
import app.naviamp.domain.lyrics.selectPreferredLyrics
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode
import app.naviamp.domain.radio.RadioService
import app.naviamp.domain.radio.generatedRadioQueue
import app.naviamp.domain.settings.ConnectionFormState
import app.naviamp.domain.settings.PlaybackSettings
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
    val savedPlaybackSettings = remember { settingsStore.loadPlaybackSettings() }
    val appState = rememberAndroidAppState(
        savedConnection = savedConnection,
        savedPlaybackSettings = savedPlaybackSettings,
        canAutoConnect = canAutoConnect,
        savedSourceId = savedProviderSource?.id,
        initialStorageStats = storage.stats(),
        initialOpenNowPlayingRequest = openNowPlayingRequest,
        initialAutoPlayMediaIdRequest = autoPlayMediaIdRequest,
        initialAutoCommandRequest = autoCommandRequest,
        initialSelectedVisualizer = NaviampVisualizer.entries.firstOrNull { it.name == settingsStore.loadSelectedVisualizer() }
            ?: NaviampVisualizer.AudioSphere,
    )
    with(appState) {
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
        audioPrefetchJob?.cancel()
        audioPrefetchJob = null
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

    suspend fun cacheAudioTrackForPlayback(
        sourceId: String,
        activeProvider: NavidromeProvider,
        track: Track,
        quality: StreamQuality,
    ): File? {
        if (track.isInternetRadioTrack()) return null
        storage.downloadedAudioFile(sourceId, track.id, quality)?.file?.let { return it }
        storage.cachedAudioFile(sourceId, track.id, quality)?.file?.let { return it }

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

    fun handleConnectionFormChanged(form: ConnectionFormState) {
        appState.applyConnectionForm(form)
    }

    fun handlePlaybackSettingsChanged(settings: PlaybackSettings) {
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
    }

    fun handleClearCache() {
        storage.clearCacheData()
        clearAndroidFileCaches()
        clearDerivedMediaState()
        status = "Cache cleared."
    }

    fun handleClearLibrary() {
        storage.clearLibraryData(activeSourceId)
        homeState = HomeContent()
        contentState = NaviampContentState()
        tracks = emptyList()
        recentPlaylistIds = emptyList()
        clearDerivedMediaState()
        status = "Library index cleared."
    }

    fun handleResetDatabase() {
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
    }

    suspend fun performSearch(activeProvider: MediaProvider, searchQuery: String) {
        status = "Searching..."
        runCatching {
            activeProvider.search(searchQuery, limit = SearchResultLimit)
        }.onSuccess { results ->
            contentState = contentState.clearDetails().copy(searchResults = results)
            tracks = results.tracks
            status = if (results.isEmpty) "No matches found." else "Found ${results.totalCount()} matches."
        }.onFailure { error ->
            status = error.message ?: "Search failed."
        }
    }

    fun handleSearch() {
        val activeProvider = provider ?: return
        val searchQuery = normalizedSearchQuery(query) ?: return
        scope.launch {
            performSearch(activeProvider, searchQuery)
        }
    }

    LaunchedEffect(query, provider) {
        val activeProvider = provider
        val searchQuery = normalizedSearchQuery(query)
        if (searchQuery == null) {
            contentState = contentState.copy(searchResults = MediaSearchResults())
            return@LaunchedEffect
        }
        if (activeProvider == null) return@LaunchedEffect

        delay(SearchDebounceMillis)
        performSearch(activeProvider, searchQuery)
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

    fun beginPlaybackSession(resetProgress: Boolean = true): Long {
        playbackSessionToken += 1
        submittedPlayReportSessionToken = null
        audioPrefetchJob?.cancel()
        audioPrefetchJob = null
        sidecarPrepJob?.cancel()
        sidecarPrepJob = null
        preparedNextTrackId = null
        pendingSeekPositionSeconds = null
        pendingSeekIssuedAtMillis = null
        if (resetProgress) {
            playbackProgress = PlaybackProgress.Unknown
        }
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
        if (sessionToken != playbackSessionToken) return
        if (progress.positionSeconds == null && progress.durationSeconds == null) {
            if (pendingRestoreStartPositionSeconds != null) return
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
        val nowMillis = System.currentTimeMillis()
        if (
            shouldIgnoreProgressForPendingSeek(
                pendingSeekPositionSeconds = pendingSeek,
                pendingSeekIssuedAtMillis = pendingSeekIssuedAt,
                incomingPositionSeconds = progressPosition,
                nowMillis = nowMillis,
                toleranceSeconds = PendingSeekToleranceSeconds,
                staleWindowMillis = PendingSeekStaleProgressWindowMillis,
            )
        ) {
            return
        }
        var pendingRestoreStart = pendingRestoreStartPositionSeconds
        if (
            pendingRestoreStart != null &&
            (pendingSeekIssuedAt == null || nowMillis - pendingSeekIssuedAt >= PendingSeekStaleProgressWindowMillis)
        ) {
            pendingRestoreStartPositionSeconds = null
            pendingRestoreStart = null
        }
        if (
            pendingRestoreStart != null &&
            progressPosition != null &&
            progressPosition < pendingRestoreStart - PendingSeekToleranceSeconds
        ) {
            return
        }
        if (
            shouldClearPendingSeek(
                pendingSeekPositionSeconds = pendingSeek,
                pendingSeekIssuedAtMillis = pendingSeekIssuedAt,
                incomingPositionSeconds = progressPosition,
                nowMillis = nowMillis,
                toleranceSeconds = PendingSeekToleranceSeconds,
                staleWindowMillis = PendingSeekStaleProgressWindowMillis,
            )
        ) {
            pendingSeekPositionSeconds = null
            pendingSeekIssuedAtMillis = null
        }
        if (
            pendingRestoreStart != null &&
            progressPosition != null &&
            progressPosition >= pendingRestoreStart - PendingSeekToleranceSeconds
        ) {
            pendingRestoreStartPositionSeconds = null
        }
        playbackProgress = progress.mergeForAndroidPlayback(playbackProgress)
        maybeReportPlayed(playbackProgress)
        val positionMillis = playbackProgress.positionSeconds?.secondsToMillis()
        val durationMillis = playbackProgress.durationSeconds
            ?.secondsToMillis()
            ?: nowPlaying?.durationSeconds?.toDouble()?.secondsToMillis()
        AndroidPlaybackNotificationControls.positionMillis = positionMillis
        AndroidPlaybackNotificationControls.durationMillis = durationMillis
        if (nowMillis - lastAndroidAutoProgressPublishAtMillis >= AndroidAutoProgressPublishIntervalMillis) {
            lastAndroidAutoProgressPublishAtMillis = nowMillis
            AndroidPlaybackForegroundService.updateProgress(context, positionMillis, durationMillis)
        }
        prepareNextIfNeeded(sessionToken, playbackProgress)
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
            val localAudioFile = activeSourceId?.let { sourceId ->
                storage.downloadedAudioFile(sourceId, track.id, streamQuality)?.file
                    ?: storage.cachedAudioFile(sourceId, track.id, streamQuality)?.file
            }
            val engineStartPositionSeconds = startPositionSeconds
                ?.takeIf { localAudioFile != null || streamQuality !is StreamQuality.Transcoded }
            val providerStartPositionSeconds = startPositionSeconds
                ?.takeIf { localAudioFile == null && streamQuality is StreamQuality.Transcoded }
            runCatching {
                localAudioFile?.toURI()?.toString()
                    ?: activeProvider.streamUrl(
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
                val restoredStartPosition = engineStartPositionSeconds?.takeIf { it > 0.0 }
                val sessionToken = beginPlaybackSession(resetProgress = restoredStartPosition == null)
                restoredStartPosition?.let { restoredPosition ->
                    playbackProgress = PlaybackProgress(
                        positionSeconds = restoredPosition,
                        durationSeconds = track.durationSeconds?.toDouble(),
                    )
                    pendingSeekPositionSeconds = restoredPosition
                    pendingSeekIssuedAtMillis = System.currentTimeMillis()
                    pendingRestoreStartPositionSeconds = restoredPosition
                    AndroidPlaybackNotificationControls.positionMillis = restoredPosition.secondsToMillis()
                    AndroidPlaybackNotificationControls.durationMillis = track.durationSeconds?.toDouble()?.secondsToMillis()
                } ?: run {
                    AndroidPlaybackNotificationControls.positionMillis = null
                    AndroidPlaybackNotificationControls.durationMillis = track.durationSeconds?.toDouble()?.secondsToMillis()
                }
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
                reportNowPlaying(track)
                loadRelatedTracks(track)
                if (lyricsVisible && nowPlayingOpen) loadLyrics(track)
                startAudioPrefetch(sessionToken, activeProvider, playbackQueue)
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
        pendingRestoreStartPositionSeconds = null
        val currentTrack = nowPlaying
        val seekPlan = planPlaybackSeek(
            isInternetRadioTrack = currentTrack?.isInternetRadioTrack() == true,
            positionSeconds = positionSeconds,
            currentProgress = playbackProgress,
            trackDurationSeconds = currentTrack?.durationSeconds,
            streamQuality = currentStreamQuality(),
            shouldReplayTranscodedStream = true,
        ) ?: return
        playbackProgress = seekPlan.progress
        val positionMillis = playbackProgress.positionSeconds?.secondsToMillis()
        val durationMillis = playbackProgress.durationSeconds?.secondsToMillis()
        AndroidPlaybackNotificationControls.positionMillis = positionMillis
        AndroidPlaybackNotificationControls.durationMillis = durationMillis
        AndroidPlaybackForegroundService.updateProgress(context, positionMillis, durationMillis)
        pendingSeekPositionSeconds = positionSeconds
        pendingSeekIssuedAtMillis = System.currentTimeMillis()
        if (currentTrack != null && seekPlan.shouldReplayCurrent) {
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
            shouldRestartInsteadOfPrevious(
                previousButtonBehavior = playbackSettings.previousButtonBehavior,
                positionSeconds = playbackProgress.positionSeconds,
                restartThresholdSeconds = 3.0,
            )
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

    fun appendGeneratedRadioTracks(seedTrack: Track, fetchedTracks: List<Track>) {
        appendAndroidGeneratedRadioTracks(appState, seedTrack, fetchedTracks)
    }

    fun startSeededRadio(
        statusLabel: String,
        seedTrack: Track,
        loadRest: suspend (RadioService) -> List<Track>,
    ) {
        startAndroidSeededRadio(
            scope = scope,
            state = appState,
            statusLabel = statusLabel,
            seedTrack = seedTrack,
            playTrack = { track, queue -> playTrack(track, queue) },
            loadRest = loadRest,
        )
    }

    fun startTrackRadio(track: Track) {
        startAndroidTrackRadio(
            scope = scope,
            state = appState,
            track = track,
            playTrack = { seedTrack, queue -> playTrack(seedTrack, queue) },
        )
    }

    fun startAlbumRadio(album: Album, loadedAlbumTracks: List<Track> = emptyList()) {
        startAndroidAlbumRadio(
            scope = scope,
            state = appState,
            album = album,
            loadedAlbumTracks = loadedAlbumTracks,
            playTrack = { seedTrack, queue -> playTrack(seedTrack, queue) },
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
        AndroidPlaybackNotificationControls.canFavorite =
            track != null && provider?.capabilities?.supportsTrackFavorites == true
        AndroidPlaybackNotificationControls.isFavorite = track?.favoritedAtIso8601 != null
    }

    fun applyTrackMetadataUpdate(updatedTrack: Track) {
        val updatedNowPlaying = nowPlaying?.let { if (it.id == updatedTrack.id) updatedTrack else it }
        val currentAlbumDetail = albumDetail
        nowPlaying = updatedNowPlaying
        tracks = tracks.map { if (it.id == updatedTrack.id) updatedTrack else it }
        contentState = contentState.copy(
            searchResults = searchResults.copy(
                tracks = searchResults.tracks.map { if (it.id == updatedTrack.id) updatedTrack else it },
            ),
            albumDetail = currentAlbumDetail?.copy(
                tracks = currentAlbumDetail.tracks.map { if (it.id == updatedTrack.id) updatedTrack else it },
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
        return localAndroidArtistDetail(storage, sourceId, artistId, fallbackName)
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
                val result = activeProvider.createPlaylistOrAddMissingTracks(
                    playlistId = playlist?.id,
                    newPlaylistName = newPlaylistName,
                    trackIds = listOf(track.id),
                )
                val update = addToPlaylistMutationUpdate(result, playlist?.name)
                val playlists = if (update.refreshPlaylists) activeProvider.playlists(limit = 500) else null
                update to playlists
            }.onSuccess { (update, playlists) ->
                if (playlists != null) {
                    homeState = homeState.copy(playlists = playlists)
                }
                playlistActionStatus = update.addToPlaylistStatus
                update.connectionStatus?.let { status = it }
                    ?: update.addToPlaylistStatus?.let { status = it }
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
                val result = activeProvider.createPlaylistOrAddMissingTracks(
                    playlistId = playlist?.id,
                    newPlaylistName = newPlaylistName,
                    trackIds = uniqueTracks.map { it.id },
                )
                val update = addToPlaylistMutationUpdate(result, playlist?.name)
                val playlists = if (update.refreshPlaylists) activeProvider.playlists(limit = 500) else null
                update to playlists
            }.onSuccess { (update, playlists) ->
                if (playlists != null) {
                    homeState = homeState.copy(playlists = playlists)
                }
                playlistActionStatus = update.addToPlaylistStatus
                update.connectionStatus?.let { status = it }
                    ?: update.addToPlaylistStatus?.let { status = it }
            }.onFailure { error ->
                playlistActionStatus = error.message ?: "Could not add $label to playlist."
                status = playlistActionStatus.orEmpty()
            }
        }
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
                LibraryFreshness(
                    signature = scanStatus?.signature,
                    previousSignature = storage.mediaSource(sourceId)?.lastLibraryScanSignature,
                    scanning = scanStatus?.scanning == true,
                )
            }
            val update = freshness.evaluateLibraryFreshness(libraryStatus)
            update.signatureToMarkChecked?.let { signature ->
                withContext(Dispatchers.IO) {
                    storage.markLibraryScanChecked(sourceId, signature)
                }
            }
            update.status?.let { status ->
                libraryStatus = status
            }
            if (update.clearStatus) {
                libraryStatus = null
            }
        }
    }

    fun connectWithNavidromeConnection(connection: NavidromeConnection) {
        startNavidromeConnection(
            scope = scope,
            state = appState,
            connection = connection,
            storage = storage,
            playbackEngine = playbackEngine,
            preloadPlaylistTracks = ::preloadPlaylistTracks,
            restorePlaybackSession = ::restorePlaybackSession,
            startAndroidLibrarySync = ::startAndroidLibrarySync,
            checkAndroidLibraryFreshness = ::checkAndroidLibraryFreshness,
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
        checkAndroidLibraryFreshness = ::checkAndroidLibraryFreshness,
    )

    val shellUiState = rememberAndroidAppShellUiState(
        state = appState,
        modifier = modifier,
        context = context,
        bassLoadReport = bassLoadReport,
        playbackEngine = playbackEngine,
    )

    fun handleShellTrackSelected(selectedTrack: AndroidTrackRowUi) {
        val currentTracks = activeQueue().takeIf { queue -> queue.any { it.id.value == selectedTrack.id } }
            ?: relatedTracks.takeIf { queue -> queue.any { it.id.value == selectedTrack.id } }
            ?: artistPopularTracksByArtistId.values.flatten().takeIf { queue -> queue.any { it.id.value == selectedTrack.id } }
            ?: allKnownTracks(searchResults, albumDetail)
        val track = currentTracks.firstOrNull { it.id.value == selectedTrack.id } ?: findKnownTrack(selectedTrack.id)
        if (track == null) {
            status = "Track not found."
            return
        }
        playTrack(track, currentTracks)
    }

    fun handleShellAlbumSelected(selectedAlbum: SharedMediaItemUi) {
        openAndroidAlbumDetails(scope, appState, selectedAlbum)
    }

    fun handleShellArtistRadio(detail: SharedArtistDetailUi) {
        val service = radioService() ?: return
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
        val service = radioService() ?: return
        val popularTracks = artistPopularTracksByArtistId[detail.artist.id].orEmpty()
        val seedTrack = popularTracks.shuffled().firstOrNull()
        if (seedTrack == null) {
            status = "No popular tracks matched your library."
            return
        }
        startSeededRadio("${detail.artist.title} popular tracks radio", seedTrack) { radioService ->
            coroutineScope {
                popularTracks.take(AndroidPopularRadioSeedLimit)
                    .map { track -> async(Dispatchers.IO) { radioService.trackRadio(track.id) } }
                    .awaitAll()
                    .flatten()
            }
        }
    }

    fun handleShellHomeStationSelected(station: SharedHomeStationUi) {
        when {
            station.id == HomeStationLibrary -> {
                playRadioTracks("Library Radio") { radioService -> radioService.libraryRadio() }
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
                playRadioTracks("${genre} Radio") { radioService -> radioService.genreRadio(genre) }
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
        val snapshot = shuffledUpNextSnapshot
        val queueState = PlaybackQueue(tracks = queue, currentIndex = currentIndex)
        if (snapshot == null) {
            val shuffled = queueState.shuffleUpcoming() ?: return
            playbackQueue = shuffled.first
            shuffledUpNextSnapshot = shuffled.second
        } else {
            playbackQueue = queueState.restoreUpcoming(snapshot)
            shuffledUpNextSnapshot = null
        }
    }

    fun startTrackRadioQueue(track: Track, playSeed: Boolean) {
        val activeProvider = provider ?: return
        if (provider?.capabilities?.supportsTrackRadio != true) return
        scope.launch {
            status = "Starting ${track.title} radio..."
            runCatching { RadioService(activeProvider, count = AndroidInitialSimilarRadioCount).trackRadio(track.id) }
                .onSuccess { radioTracks ->
                    val queue = generatedRadioQueue(track, radioTracks)
                    if (playSeed) {
                        playTrack(track, queue)
                    } else {
                        playbackQueue = PlaybackQueue(tracks = queue, currentIndex = 0)
                        relatedTracks = queue.drop(1)
                        shuffledUpNextSnapshot = null
                    }
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
                .onSuccess { detail ->
                    contentState = contentState.showAlbum(detail)
                    nowPlayingOpen = false
                    navigationState = navigationState.copy(route = NaviampRoute.Library)
                }
                .onFailure { error -> status = error.message ?: "Album failed to load." }
        }
    }

    fun handleShellRatingSelected(rating: Int?) {
        val activeProvider = provider ?: return
        val currentTrack = nowPlaying ?: return
        if (!activeProvider.capabilities.supportsTrackRatings) return
        scope.launch {
            runCatching {
                activeProvider.setTrackRating(currentTrack.id, rating)
            }.onSuccess {
                applyTrackMetadataUpdate(currentTrack.copy(userRating = rating))
            }.onFailure { error ->
                status = error.message ?: "Could not update rating."
            }
        }
    }

    fun handleDownloadedTrackSelected(download: NaviampDownloadedTrackUi) {
        val currentTracks = downloadedTracks.map { it.track }
        val track = currentTracks.firstOrNull { it.id.value == download.track.id } ?: return
        playTrack(track, currentTracks)
    }

    fun handleDownloadedTrackAddToPlaylist(download: NaviampDownloadedTrackUi, playlist: NaviampPlaylistChoiceUi?) {
        downloadedTracks.firstOrNull { it.file.absolutePath == download.id }
            ?.track
            ?.let { addTrackToPlaylist(it, playlist) }
            ?: run { status = "Track not found." }
    }

    fun handleDownloadedTrackCreatePlaylistAndAdd(download: NaviampDownloadedTrackUi, name: String) {
        downloadedTracks.firstOrNull { it.file.absolutePath == download.id }
            ?.track
            ?.let { addTrackToPlaylist(it, playlist = null, newPlaylistName = name) }
            ?: run { status = "Track not found." }
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
        findKnownTrack(selectedTrack.id)?.let(::downloadTrack) ?: run { status = "Track not found." }
    }

    fun handleAlbumTrackAddToPlaylist(selectedTrack: AndroidTrackRowUi, playlist: NaviampPlaylistChoiceUi?) {
        findKnownTrack(selectedTrack.id)?.let { addTrackToPlaylist(it, playlist) } ?: run { status = "Track not found." }
    }

    fun handleAlbumTrackCreatePlaylistAndAdd(selectedTrack: AndroidTrackRowUi, name: String) {
        findKnownTrack(selectedTrack.id)?.let { addTrackToPlaylist(it, playlist = null, newPlaylistName = name) }
            ?: run { status = "Track not found." }
    }

    fun handleArtistPopularPlay(detail: SharedArtistDetailUi) {
        val popularTracks = artistPopularTracksByArtistId[detail.artist.id].orEmpty()
        popularTracks.firstOrNull()?.let { playTrack(it, popularTracks) }
            ?: run { status = "No popular tracks matched your library." }
    }

    fun handleArtistPopularTrackSelected(selectedTrack: AndroidTrackRowUi) {
        val track = artistPopularTracksByArtistId.values.flatten().firstOrNull { it.id.value == selectedTrack.id }
            ?: findKnownTrack(selectedTrack.id)
        if (track == null) {
            status = "Track not found."
        } else {
            startTrackRadio(track)
        }
    }

    fun handleArtistPopularAddToQueue(detail: SharedArtistDetailUi) {
        val popularTracks = artistPopularTracksByArtistId[detail.artist.id].orEmpty()
        if (popularTracks.isEmpty()) {
            status = "No popular tracks matched your library."
        } else {
            appendTracksToQueue(popularTracks.filterNot { track -> playbackQueue.tracks.any { it.id == track.id } }, "popular tracks")
        }
    }

    fun handleTrackAddToQueue(selectedTrack: AndroidTrackRowUi) {
        val track = findKnownTrack(selectedTrack.id)
        if (track == null) {
            status = "Track not found."
        } else {
            appendTracksToQueue(listOf(track), "track")
        }
    }

    fun handleTrackDownload(selectedTrack: AndroidTrackRowUi) {
        findKnownTrack(selectedTrack.id)?.let(::downloadTrack) ?: run { status = "Track not found." }
    }

    fun handleTrackAddToPlaylist(selectedTrack: AndroidTrackRowUi, playlist: NaviampPlaylistChoiceUi?) {
        findKnownTrack(selectedTrack.id)?.let { addTrackToPlaylist(it, playlist) } ?: run { status = "Track not found." }
    }

    fun handleTrackCreatePlaylistAndAdd(selectedTrack: AndroidTrackRowUi, name: String) {
        findKnownTrack(selectedTrack.id)?.let { addTrackToPlaylist(it, playlist = null, newPlaylistName = name) }
            ?: run { status = "Track not found." }
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
        loadAndroidArtistAlbumTracks(scope, appState, selectedAlbum, action)
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
        startAndroidLibrarySync = ::startAndroidLibrarySync,
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
