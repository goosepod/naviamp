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
import app.naviamp.android.playback.AndroidPlaybackNotificationControls
import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.ArtistId
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Lyrics
import app.naviamp.domain.Playlist
import app.naviamp.domain.StreamQuality
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
import app.naviamp.domain.home.HomeStationLibrary
import app.naviamp.domain.home.HomeStationRandomAlbum
import app.naviamp.domain.home.homeDecadeStationId
import app.naviamp.domain.home.homeGenreStationId
import app.naviamp.domain.artistmix.artistMixPopularQueue
import app.naviamp.domain.albummix.albumMixTrackQueue
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackQueueController
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.playback.PlaybackVisualizerFrame
import app.naviamp.domain.playback.ReplayGainMode
import app.naviamp.domain.playback.VisualizerPlaybackEngine
import app.naviamp.domain.playback.label
import app.naviamp.domain.playback.PlaybackAdjacentAction
import app.naviamp.domain.playback.PlaybackPlayPauseCommand
import app.naviamp.domain.playback.planPlaybackAdjacentAction
import app.naviamp.domain.playback.playbackPlayPauseCommand
import app.naviamp.domain.playback.planPlaybackSeek
import app.naviamp.domain.playback.SleepTimerRequest
import app.naviamp.domain.popular.SimilarArtistMatch
import app.naviamp.domain.queue.RepeatMode
import app.naviamp.domain.radio.RecentRadioAction
import app.naviamp.domain.radio.recentRadioAction
import app.naviamp.domain.radio.recentRadioStreamsWith
import app.naviamp.domain.settings.ConnectionFormState
import app.naviamp.domain.settings.PlaybackSettings
import app.naviamp.domain.settings.RecentRadioStream
import app.naviamp.domain.settings.downloadStreamQuality
import app.naviamp.domain.settings.effectiveForEngine
import app.naviamp.domain.settings.playbackSettingsChange
import app.naviamp.domain.settings.streamQualityForNetwork
import app.naviamp.domain.smartplaylist.SmartPlaylistDefinition
import app.naviamp.provider.navidrome.NavidromeApiCall
import app.naviamp.provider.navidrome.NavidromeApiCallHistory
import app.naviamp.provider.navidrome.NavidromeProvider
import app.naviamp.provider.navidrome.toNavidromeConnection
import app.naviamp.ui.SharedTrackRowUi
import app.naviamp.ui.NaviampDiagnosticsSectionUi
import app.naviamp.ui.NaviampDiagnosticsUi
import app.naviamp.ui.NaviampLibrarySyncStatusUi
import app.naviamp.ui.NaviampPlaylistChoiceUi
import app.naviamp.ui.NaviampSharedAppShell
import app.naviamp.ui.NaviampVisualizer
import app.naviamp.ui.NowPlayingRadioUiConfig
import app.naviamp.ui.NowPlayingTrackUiConfig
import app.naviamp.ui.NowPlayingUi
import app.naviamp.ui.SharedAlbumDetailUi
import app.naviamp.ui.SharedHomeStationUi
import app.naviamp.ui.SharedHomeUi
import app.naviamp.ui.SharedMediaItemUi
import app.naviamp.ui.SharedPlaylistDetailUi
import app.naviamp.ui.SharedPlaylistSortMode
import app.naviamp.ui.SharedRoute
import app.naviamp.ui.SharedSearchResultsUi
import app.naviamp.ui.toSharedTrackRowUi
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
    val dependencies = remember(context) { AndroidAppDependencies(context) }
    val playbackRuntime = dependencies.playbackRuntime
    val scope = playbackRuntime.scope
    val bassLoadReport = playbackRuntime.bassLoadReport
    val playbackEngine: AndroidPlaybackEngine = playbackRuntime.playbackEngine
    val waveformAnalyzer = playbackRuntime.waveformAnalyzer
    val storage = dependencies.storage
    val sidecarStatusRepository = dependencies.sidecarStatusRepository
    val playbackAudioAssets = dependencies.playbackAudioAssets
    val audioMetadataSidecarService = dependencies.audioMetadataSidecarService
    val lyricsSidecarService = dependencies.lyricsSidecarService
    DisposableEffect(dependencies) {
        onDispose { dependencies.close() }
    }
    val settingsStore = dependencies.settingsStore
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
    val popularTracksService = remember(dependencies) {
        dependencies.popularTracksService(
            activeSourceIdProvider = { activeSourceId },
            providerProvider = { provider },
        )
    }
    val similarArtistsService = remember(dependencies) {
        dependencies.similarArtistsService(
            activeSourceIdProvider = { activeSourceId },
            providerProvider = { provider },
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

    fun currentStreamQuality(): StreamQuality =
        playbackSettings.streamQualityForNetwork(context.isActiveNetworkMobileData())

    fun currentDownloadQuality(): StreamQuality =
        playbackSettings.downloadStreamQuality()

    val nowPlayingSidecarController = remember(appState, dependencies) {
        AndroidNowPlayingSidecarController(
            scope = scope,
            state = appState,
            lyricsSidecarService = lyricsSidecarService,
            audioMetadataSidecarService = audioMetadataSidecarService,
            sidecarStatusRepository = sidecarStatusRepository,
            lyricsOffsetRepository = storage,
            cacheAudioTrack = dependencies::cacheAudioTrack,
            currentStreamQuality = ::currentStreamQuality,
        )
    }
    val playbackReportController = remember(appState) {
        AndroidPlaybackReportController(scope, appState)
    }

    fun loadLyrics(track: Track) {
        nowPlayingSidecarController.loadLyrics(track)
    }

    fun handleLyricsOffsetChanged(offsetMillis: Int) {
        nowPlayingSidecarController.handleLyricsOffsetChanged(offsetMillis)
    }

    fun handleConnectionFormChanged(form: ConnectionFormState) {
        appState.applyConnectionForm(form)
    }

    fun handlePlaybackSettingsChanged(settings: PlaybackSettings) {
        val change = playbackSettingsChange(settings, playbackEngine, previous = playbackSettings)
        playbackSettings = change.settings
        settingsStore.savePlaybackSettings(change.settings)
        if (change.shouldReloadLyricsSidecars) {
            nowPlayingSidecarController.reloadVisibleLyrics()
        }
    }

    fun handlePlaybackSettingsChangedAndRedownload(settings: PlaybackSettings) {
        val tracksToRedownload = downloadedTracks.map { it.track }
        handlePlaybackSettingsChanged(settings)
        if (tracksToRedownload.isNotEmpty()) {
            redownloadAndroidTracks(
                context = context,
                scope = scope,
                state = appState,
                downloadRepository = storage,
                downloadReplacementRepository = storage,
                cacheMaintenanceRepository = storage,
                tracksToDownload = tracksToRedownload,
                label = "downloads",
            )
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
            cacheMaintenanceRepository = storage,
            settingsStore = settingsStore,
            playbackEngine = playbackEngine,
            queueController = playbackQueueController,
        )
    }

    val searchController = remember(appState, storage) { AndroidSearchController(appState, storage) }

    fun handleSearch() {
        searchController.launchSearch(scope)
    }

    LaunchedEffect(query, provider) {
        searchController.load(query, debounce = true)
    }

    fun loadRelatedTracks(track: Track) {
        loadAndroidRelatedTracks(scope, appState, track)
    }

    AndroidRadioArtworkLookupEffect(
        station = nowPlayingStation,
        streamMetadata = nowPlayingStreamMetadata,
        provider = provider,
        artworkByKey = radioTrackArtworkByKey,
        onArtworkResolved = { key, artworkUrl ->
            radioTrackArtworkByKey = radioTrackArtworkByKey + (key to artworkUrl)
        },
    )

    fun reportNowPlaying(track: Track) {
        playbackReportController.reportNowPlaying(track)
    }

    fun maybeReportPlayed(progress: PlaybackProgress) {
        playbackReportController.maybeReportPlayed(progress)
    }

    val androidPlaylistEngine = remember(
        appState,
        dependencies,
        playbackQueueController,
    ) {
        dependencies.playlistEngine(
            state = appState,
            playbackQueueController = playbackQueueController,
            activeQueue = ::activeQueue,
            currentStreamQuality = ::currentStreamQuality,
        )
    }

    fun loadAudioTags(track: Track) {
        nowPlayingSidecarController.loadAudioTags(track)
    }

    LaunchedEffect(nowPlaying?.id, activeSourceId, provider) {
        nowPlaying?.takeUnless { it.isInternetRadioTrack() }?.let(::loadAudioTags)
    }

    fun handlePlaybackProgressChanged(sessionToken: Long, progress: PlaybackProgress) {
        handleAndroidPlaybackProgressChanged(
            context = context,
            state = appState,
            sessionToken = sessionToken,
            progress = progress,
            maybeReportPlayed = ::maybeReportPlayed,
            prepareNextIfNeeded = androidPlaylistEngine::prepareNextIfNeeded,
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
            audioAssets = playbackAudioAssets,
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
            loadAudioTags = ::loadAudioTags,
            startAudioPrefetch = androidPlaylistEngine::startAudioPrefetch,
            startSidecarPrep = androidPlaylistEngine::startSidecarPrep,
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

    fun rememberRecentRadioStream(stream: RecentRadioStream) {
        val recentStreams = recentRadioStreamsWith(settingsStore.loadRecentRadioStreams(), stream)
        settingsStore.saveRecentRadioStreams(recentStreams)
        homeState = homeState.copy(recentRadioStreams = recentStreams)
    }

    fun startTrackRadio(track: Track) {
        startAndroidTrackRadio(
            scope = scope,
            state = appState,
            queueController = playbackQueueController,
            track = track,
            playTrack = { seedTrack, queue -> playTrack(seedTrack, queue, keepRadioQueueActive = true) },
            providerResponseCacheRepository = storage,
            rememberRecentRadioStream = ::rememberRecentRadioStream,
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
            providerResponseCacheRepository = storage,
            rememberRecentRadioStream = ::rememberRecentRadioStream,
        )
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
        return when (
            playbackPlayPauseCommand(
                playbackState = playbackState,
                hasPlaybackTarget = nowPlaying != null || nowPlayingStation != null || activeSourceId != null,
            )
        ) {
            PlaybackPlayPauseCommand.Pause -> {
                playbackEngine.pause()
                true
            }
            PlaybackPlayPauseCommand.Resume -> {
                playbackEngine.resume()
                true
            }
            PlaybackPlayPauseCommand.StartOrRestore -> {
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
            PlaybackPlayPauseCommand.None -> false
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
            libraryIndexRepository = storage,
            providerResponseCacheRepository = storage,
            popularTracksService = popularTracksService,
            artistId = artistId,
            fallbackName = fallbackName,
            pushCurrentArtist = pushCurrentArtist,
        )
    }

    val navigationController = remember(appState) {
        AndroidNavigationController(appState, ::openArtistDetails)
    }

    BackHandler(enabled = navigationController.handlesAndroidBack()) {
        navigationController.handleAndroidBack()
    }

    val artistActionController = remember(appState, storage, context) {
        AndroidArtistActionController(
            context = context,
            scope = scope,
            state = appState,
            queueController = playbackQueueController,
            libraryIndexRepository = storage,
            providerResponseCacheRepository = storage,
            similarArtistsService = similarArtistsService,
            activeQueue = ::activeQueue,
            openArtistDetails = ::openArtistDetails,
            playTrack = { track, queue -> playTrack(track, queue) },
            playRadioTrack = { track, queue -> playTrack(track, queue, keepRadioQueueActive = true) },
            startTrackRadio = ::startTrackRadio,
            startAlbumRadio = ::startAlbumRadio,
            rememberRecentRadioStream = ::rememberRecentRadioStream,
        )
    }

    val artistMixBuilderService = rememberAndroidArtistMixBuilderService(
        storage = storage,
        sourceId = { activeSourceId },
        provider = { provider },
        homeContent = homeState,
        popularTracksService = popularTracksService,
        similarArtistsService = similarArtistsService,
    )

    fun handleArtistMixPlay() {
        startAndroidArtistMixRadio(
            scope = scope,
            state = appState,
            queueController = playbackQueueController,
            artists = artistMixSelectedArtists,
            popularTracks = artistMixPopularQueue(artistMixSelectedArtists, artistMixPopularTracksByArtistId),
            playTrack = { seedTrack, queue -> playTrack(seedTrack, queue, keepRadioQueueActive = true) },
            providerResponseCacheRepository = storage,
            rememberRecentRadioStream = ::rememberRecentRadioStream,
        )
    }

    val albumMixBuilderService = rememberAndroidAlbumMixBuilderService(
        storage = storage,
        sourceId = { activeSourceId },
        provider = { provider },
        homeContent = homeState,
        similarArtistsService = similarArtistsService,
    )

    fun handleAlbumMixPlay() {
        startAndroidAlbumMixRadio(
            scope = scope,
            state = appState,
            queueController = playbackQueueController,
            albums = albumMixSelectedAlbums,
            selectedTracks = albumMixTrackQueue(albumMixSelectedAlbums, albumMixTracksByAlbumId),
            playTrack = { seedTrack, queue -> playTrack(seedTrack, queue, keepRadioQueueActive = true) },
            libraryIndexRepository = storage,
            providerResponseCacheRepository = storage,
            rememberRecentRadioStream = ::rememberRecentRadioStream,
        )
    }

    val genreMixBuilderService = rememberAndroidGenreMixBuilderService(
        provider = { provider },
        homeContent = homeState,
    )

    val mixBuilderController = AndroidMixBuilderController(
        scope = scope,
        state = appState,
        artistMixBuilderService = { artistMixBuilderService },
        albumMixBuilderService = { albumMixBuilderService },
        genreMixBuilderService = { genreMixBuilderService },
    )

    fun handleGenreMixPlay() {
        startAndroidGenreMixRadio(
            scope = scope,
            state = appState,
            queueController = playbackQueueController,
            genres = genreMixSelectedGenres,
            playTrack = { track, queue -> playTrack(track, queue, keepRadioQueueActive = true) },
            providerResponseCacheRepository = storage,
            rememberRecentRadioStream = ::rememberRecentRadioStream,
        )
    }

    LaunchedEffect(provider, homeState.artists) {
        if (provider != null && artistMixSuggestions.isEmpty()) {
            mixBuilderController.refreshArtistInitialSuggestions()
        }
    }

    LaunchedEffect(provider, homeState.randomAlbums, homeState.mixAlbums) {
        if (provider != null && albumMixSuggestions.isEmpty()) {
            mixBuilderController.refreshAlbumInitialSuggestions()
        }
    }

    LaunchedEffect(provider, homeState.genres) {
        if (provider != null && genreMixSuggestions.isEmpty()) {
            mixBuilderController.refreshGenreSuggestions()
        }
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
                providerResponseCacheRepository = storage,
            )
        }
    }

    fun openPlaylistDetails(playlist: Playlist) {
        openAndroidPlaylistDetails(scope, appState, playlist, storage)
    }

    fun playPlaylist(playlist: Playlist, shuffle: Boolean) {
        playAndroidPlaylist(scope, appState, playlist, shuffle, ::playTrack, storage)
    }

    fun renamePlaylist(playlist: Playlist, name: String) {
        renameAndroidPlaylist(scope, appState, playlist, name, storage)
    }

    fun deletePlaylist(playlist: Playlist) {
        deleteAndroidPlaylist(scope, appState, playlist, storage)
    }

    fun preloadPlaylistTracks(activeProvider: NavidromeProvider, playlists: List<Playlist>) {
        preloadAndroidPlaylistTracks(scope, appState, activeProvider, playlists, storage)
    }

    fun refreshAndroidPlaylists() {
        refreshAndroidPlaylists(scope, appState, storage)
    }

    suspend fun saveSmartPlaylist(definition: SmartPlaylistDefinition) {
        saveAndroidSmartPlaylist(scope, appState, definition, storage)
    }

    suspend fun updateSmartPlaylist(playlist: Playlist, definition: SmartPlaylistDefinition) {
        updateAndroidSmartPlaylist(scope, appState, playlist, definition, storage)
    }

    suspend fun loadSmartPlaylistDefinition(playlist: Playlist): SmartPlaylistDefinition =
        loadAndroidSmartPlaylistDefinition(appState, playlist)

    fun addTrackToPlaylist(track: Track, playlist: NaviampPlaylistChoiceUi?, newPlaylistName: String? = null) {
        addAndroidTrackToPlaylist(scope, appState, track, playlist, newPlaylistName, storage)
    }

    fun addTracksToPlaylist(
        tracksToAdd: List<Track>,
        playlist: NaviampPlaylistChoiceUi?,
        newPlaylistName: String? = null,
        label: String = "tracks",
    ) {
        addAndroidTracksToPlaylist(scope, appState, tracksToAdd, playlist, newPlaylistName, label, storage)
    }

    fun saveQueueAsPlaylist(name: String) {
        saveQueueAsPlaylistFromState(scope, appState, name, storage)
    }

    fun currentSleepTimerSnapshot() =
        androidSleepTimerSnapshot(
            nowPlaying = nowPlaying,
            playbackQueue = playbackQueue,
            playbackProgress = playbackProgress,
            playbackState = playbackState,
        )

    fun handleSleepTimerSelected(request: SleepTimerRequest) {
        val nowMillis = System.currentTimeMillis()
        val selection = androidSleepTimerSelection(
            request = request,
            nowEpochMillis = nowMillis,
            nowPlaying = nowPlaying,
            playbackQueue = playbackQueue,
            playbackProgress = playbackProgress,
            playbackState = playbackState,
        )
        sleepTimer = selection.timer
        sleepTimerNowEpochMillis = selection.nowEpochMillis
        status = selection.status
    }

    fun cancelSleepTimer() {
        sleepTimer = null
        status = "Sleep timer canceled."
    }

    fun addPlaylistToQueue(playlist: Playlist) {
        addAndroidPlaylistToQueue(scope, appState, playlist, storage, ::appendTracksToQueue)
    }

    fun addPlaylistToPlaylist(
        playlist: Playlist,
        targetPlaylist: NaviampPlaylistChoiceUi?,
        newPlaylistName: String?,
    ) {
        addAndroidPlaylistToPlaylist(
            scope = scope,
            state = appState,
            playlist = playlist,
            targetPlaylist = targetPlaylist,
            newPlaylistName = newPlaylistName,
            providerResponseCacheRepository = storage,
            addTracksToPlaylist = ::addTracksToPlaylist,
        )
    }

    val downloadActionController = remember(appState, storage, context) {
        AndroidDownloadActionController(
            context = context,
            scope = scope,
            state = appState,
            storage = storage,
            findKnownTrack = ::findKnownTrack,
        )
    }

    val connectionSessionController = remember(appState, storage, settingsStore, savedProviderConnection) {
        AndroidConnectionSessionController(
            scope = scope,
            state = appState,
            storage = storage,
            settingsStore = settingsStore,
            savedProviderConnection = savedProviderConnection,
            savedConnection = savedConnection,
            playbackEngine = playbackEngine,
            preloadPlaylistTracks = ::preloadPlaylistTracks,
            loadRelatedTracks = ::loadRelatedTracks,
            startAndroidLibrarySync = { force -> startAndroidLibrarySync(scope, appState, storage, force) },
            checkAndroidLibraryFreshness = { checkAndroidLibraryFreshness(scope, appState, storage, storage) },
        )
    }
    restorePlaybackSessionAction = connectionSessionController::restorePlaybackSession

    LaunchedEffect(Unit) {
        connectionSessionController.autoConnect()
    }

    AndroidAppPersistenceEffects(
        state = appState,
        downloadRepository = storage,
        cacheMaintenanceRepository = storage,
        savePlaybackSessionThrottled = ::savePlaybackSessionThrottled,
        checkAndroidLibraryFreshness = { checkAndroidLibraryFreshness(scope, appState, storage, storage) },
    )

    val shellUiState = rememberAndroidAppShellUiState(
        state = appState,
        modifier = modifier,
        context = context,
        bassLoadReport = bassLoadReport,
        playbackEngine = playbackEngine,
    )

    fun handleShellTrackSelected(selectedTrack: SharedTrackRowUi) {
        val playback = selectedAndroidTrackPlayback(appState, selectedTrack.id, activeQueue())
        if (playback == null) {
            status = "Track not found."
            return
        }
        val (track, currentTracks) = playback
        playTrack(track, currentTracks)
    }

    fun handleShellAlbumSelected(selectedAlbum: SharedMediaItemUi) {
        openAndroidAlbumDetails(
            scope = scope,
            state = appState,
            libraryIndexRepository = storage,
            providerResponseCacheRepository = storage,
            selectedAlbum = selectedAlbum,
        )
    }

    fun handleShellHomeStationSelected(station: SharedHomeStationUi) {
        startAndroidHomeStationRadio(
            scope = scope,
            state = appState,
            stationId = station.id,
            stationTitle = station.title,
            playTrack = { track, queue -> playTrack(track, queue) },
            providerResponseCacheRepository = storage,
            rememberRecentRadioStream = ::rememberRecentRadioStream,
        )
    }

    fun handleShellRecentRadioSelected(item: SharedMediaItemUi) {
        val stream = homeState.recentRadioStreams.firstOrNull { it.id == item.id }
            ?: settingsStore.loadRecentRadioStreams().firstOrNull { it.id == item.id }
            ?: return
        when (val action = recentRadioAction(stream) ?: return) {
            RecentRadioAction.PlayLibrary -> handleShellHomeStationSelected(
                SharedHomeStationUi(
                    id = HomeStationLibrary,
                    title = "Library Radio",
                    subtitle = "Random tracks from your full library",
                ),
            )
            RecentRadioAction.PlayRandomAlbum -> handleShellHomeStationSelected(
                SharedHomeStationUi(
                    id = HomeStationRandomAlbum,
                    title = "Random Album Radio",
                    subtitle = "Start from a random album",
                ),
            )
            is RecentRadioAction.PlayGenre -> handleShellHomeStationSelected(
                SharedHomeStationUi(
                    id = homeGenreStationId(action.genre.name),
                    title = "${action.genre.name} Radio",
                    subtitle = "A random ${action.genre.name} station",
                ),
            )
            is RecentRadioAction.PlayDecade -> handleShellHomeStationSelected(
                SharedHomeStationUi(
                    id = homeDecadeStationId(action.fromYear, action.toYear),
                    title = "${action.fromYear}s Radio",
                    subtitle = "Random songs from ${action.fromYear}s",
                ),
            )
            is RecentRadioAction.PlayArtist -> startAndroidArtistRadio(
                scope = scope,
                state = appState,
                queueController = playbackQueueController,
                artistId = action.artist.id,
                artistTitle = action.artist.name,
                artist = action.artist,
                playTrack = { seedTrack, queue -> playTrack(seedTrack, queue, keepRadioQueueActive = true) },
                providerResponseCacheRepository = storage,
                rememberRecentRadioStream = ::rememberRecentRadioStream,
            )
            is RecentRadioAction.PlayAlbum -> startAlbumRadio(action.album)
            is RecentRadioAction.PlayTrack -> startTrackRadio(action.track)
        }
    }

    val shellPlaybackController = remember(appState) {
        AndroidShellPlaybackController(
            scope = scope,
            state = appState,
            playbackEngine = playbackEngine,
            playbackQueueController = playbackQueueController,
            activeQueue = ::activeQueue,
            findKnownTrack = ::findKnownTrack,
            playTrack = ::playTrack,
            playInternetRadioStation = ::playInternetRadioStation,
            rememberRecentRadioStream = ::rememberRecentRadioStream,
        )
    }

    fun handleShellGoToAlbum() {
        openAndroidNowPlayingAlbumDetails(scope, appState, storage, storage)
    }

    fun handleShellRatingSelected(rating: Int?) {
        setAndroidCurrentTrackRating(scope, appState, playbackEngine, rating)
    }

    val trackActionController = remember(appState) {
        AndroidTrackActionController(
            state = appState,
            activeQueue = ::activeQueue,
            findKnownTrack = ::findKnownTrack,
            playTrack = { track, queue -> playTrack(track, queue) },
            appendTracksToQueue = ::appendTracksToQueue,
            downloadTrack = downloadActionController::downloadTrack,
            addTrackToPlaylist = { track, playlist, newPlaylistName ->
                addTrackToPlaylist(track, playlist, newPlaylistName)
            },
        )
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

    fun handleShellAlbumTrackSelected(selectedTrack: SharedTrackRowUi) {
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

    fun handleRadioStationSelected(station: InternetRadioStation) {
        playInternetRadioStation(station)
    }

    fun saveInternetRadioStation(station: InternetRadioStation) {
        saveAndroidInternetRadioStation(scope, appState, dependencies.internetRadioStationManager, station)
    }

    fun deleteInternetRadioStation(station: InternetRadioStation) {
        deleteAndroidInternetRadioStation(scope, appState, dependencies.internetRadioStationManager, station)
    }

    fun handleShellGoToArtist() {
        val currentTrack = nowPlaying ?: return
        currentTrack.artistId?.let { openArtistDetails(it, currentTrack.artistName) }
    }

    AndroidSleepTimerExpiryEffect(
        sleepTimer = sleepTimer,
        snapshot = currentSleepTimerSnapshot(),
        onTick = { nowMillis -> sleepTimerNowEpochMillis = nowMillis },
        onExpired = {
            playbackEngine.stop()
            sleepTimer = null
            status = "Sleep timer stopped playback."
        },
    )

    val shellActions = androidAppShellActions(
        state = appState,
        playbackEngine = playbackEngine,
        settingsStore = settingsStore,
        handleConnectionFormChanged = ::handleConnectionFormChanged,
        connectToNavidrome = connectionSessionController::connectToNavidrome,
        handlePlaybackSettingsChanged = ::handlePlaybackSettingsChanged,
        handlePlaybackSettingsChangedAndRedownload = ::handlePlaybackSettingsChangedAndRedownload,
        handleClearCache = ::handleClearCache,
        handleClearLibrary = ::handleClearLibrary,
        handleResetDatabase = ::handleResetDatabase,
        handleSearch = ::handleSearch,
        handleArtistMixSearch = mixBuilderController::searchArtistSuggestions,
        handleArtistMixArtistSelected = { item -> mixBuilderController.selectArtistByItemId(item.id) },
        handleArtistMixArtistRemoved = { item -> mixBuilderController.removeArtistByItemId(item.id) },
        handleArtistMixReset = mixBuilderController::resetArtistBuilder,
        handleArtistMixPlay = ::handleArtistMixPlay,
        handleAlbumMixSearch = mixBuilderController::searchAlbumSuggestions,
        handleAlbumMixAlbumSelected = { item -> mixBuilderController.selectAlbumByItemId(item.id) },
        handleAlbumMixAlbumRemoved = { item -> mixBuilderController.removeAlbumByItemId(item.id) },
        handleAlbumMixReset = mixBuilderController::resetAlbumBuilder,
        handleAlbumMixPlay = ::handleAlbumMixPlay,
        handleGenreMixSearch = mixBuilderController::refreshGenreSuggestions,
        handleGenreMixGenreSelected = { item -> mixBuilderController.selectGenreByItemId(item.id) },
        handleGenreMixGenreRemoved = { item -> mixBuilderController.removeGenreByItemId(item.id) },
        handleGenreMixReset = mixBuilderController::resetGenreBuilder,
        handleGenreMixPlay = ::handleGenreMixPlay,
        startAndroidLibrarySync = { force -> startAndroidLibrarySync(scope, appState, storage, force) },
        handleShellTrackSelected = ::handleShellTrackSelected,
        handleDownloadedTrackSelected = trackActionController::handleDownloadedTrackSelected,
        handleDownloadedTrackAddToPlaylist = trackActionController::handleDownloadedTrackAddToPlaylist,
        handleDownloadedTrackCreatePlaylistAndAdd = trackActionController::handleDownloadedTrackCreatePlaylistAndAdd,
        removeDownload = downloadActionController::removeDownload,
        handleShellAlbumSelected = ::handleShellAlbumSelected,
        handleAlbumFavoriteToggled = { item -> toggleAndroidAlbumFavorite(scope, appState, item) },
        handleMixAlbumSelected = ::handleMixAlbumSelected,
        handleShellAlbumPlay = ::handleShellAlbumPlay,
        handleShellAlbumTrackSelected = ::handleShellAlbumTrackSelected,
        handleShellAlbumRadio = ::handleShellAlbumRadio,
        appendTracksToQueue = ::appendTracksToQueue,
        downloadTracks = downloadActionController::downloadTracks,
        addTracksToPlaylist = ::addTracksToPlaylist,
        handleAlbumTrackDownload = trackActionController::handleAlbumTrackDownload,
        handleAlbumTrackAddToPlaylist = trackActionController::handleAlbumTrackAddToPlaylist,
        handleAlbumTrackCreatePlaylistAndAdd = trackActionController::handleAlbumTrackCreatePlaylistAndAdd,
        handleShellArtistRadio = artistActionController::handleShellArtistRadio,
        handleShellArtistShuffle = artistActionController::handleShellArtistShuffle,
        loadArtistTracks = artistActionController::loadArtistTracks,
        handleArtistPopularPlay = artistActionController::handleArtistPopularPlay,
        handleShellArtistPopularRadio = artistActionController::handleShellArtistPopularRadio,
        handleArtistPopularTrackSelected = artistActionController::handleArtistPopularTrackSelected,
        handleArtistPopularAddToQueue = artistActionController::handleArtistPopularAddToQueue,
        handleTrackAddToQueue = trackActionController::handleTrackAddToQueue,
        handleTrackDownload = trackActionController::handleTrackDownload,
        handleTrackAddToPlaylist = trackActionController::handleTrackAddToPlaylist,
        handleTrackCreatePlaylistAndAdd = trackActionController::handleTrackCreatePlaylistAndAdd,
        findSimilarArtists = artistActionController::findSimilarArtists,
        handleSimilarArtistSelected = artistActionController::handleSimilarArtistSelected,
        openExternalArtistUrl = artistActionController::openExternalArtistUrl,
        openArtistDetails = ::openArtistDetails,
        handleArtistFavoriteToggled = { item -> toggleAndroidArtistFavorite(scope, appState, item) },
        handleArtistAlbumRadio = artistActionController::handleArtistAlbumRadio,
        loadArtistAlbumTracks = artistActionController::loadArtistAlbumTracks,
        openPlaylistDetails = ::openPlaylistDetails,
        playPlaylist = ::playPlaylist,
        downloadPlaylist = downloadActionController::downloadPlaylist,
        addPlaylistToQueue = ::addPlaylistToQueue,
        addPlaylistToPlaylist = ::addPlaylistToPlaylist,
        renamePlaylist = ::renamePlaylist,
        deletePlaylist = ::deletePlaylist,
        saveSmartPlaylist = ::saveSmartPlaylist,
        updateSmartPlaylist = ::updateSmartPlaylist,
        loadSmartPlaylist = ::loadSmartPlaylistDefinition,
        closeActivePlaylist = navigationController::closeActivePlaylist,
        handlePlaylistTrackSelected = trackActionController::handlePlaylistTrackSelected,
        handleRecentRadioSelected = ::handleShellRecentRadioSelected,
        handleMixBuilderSelected = navigationController::handleMixBuilderSelected,
        handleRadioStationSelected = ::handleRadioStationSelected,
        saveInternetRadioStation = ::saveInternetRadioStation,
        deleteInternetRadioStation = ::deleteInternetRadioStation,
        handleShellHomeStationSelected = ::handleShellHomeStationSelected,
        closeActiveDetail = navigationController::closeActiveDetail,
        handleShellResume = shellPlaybackController::resume,
        playAdjacentTrack = ::playAdjacentTrack,
        performSeek = ::performSeek,
        handleShellToggleShuffle = shellPlaybackController::toggleShuffle,
        loadLyrics = ::loadLyrics,
        handleLyricsOffsetChanged = ::handleLyricsOffsetChanged,
        handleShellTrackRadio = shellPlaybackController::startCurrentTrackRadio,
        handleNowPlayingAddToPlaylist = trackActionController::handleNowPlayingAddToPlaylist,
        handleNowPlayingCreatePlaylistAndAdd = trackActionController::handleNowPlayingCreatePlaylistAndAdd,
        handleSaveQueueAsPlaylist = ::saveQueueAsPlaylist,
        handleSleepTimerSelected = ::handleSleepTimerSelected,
        handleCancelSleepTimer = ::cancelSleepTimer,
        downloadTrack = downloadActionController::downloadTrack,
        handleShellGoToAlbum = ::handleShellGoToAlbum,
        handleShellGoToArtist = ::handleShellGoToArtist,
        handleShellQueueItemRadio = shellPlaybackController::startQueueItemRadio,
        findKnownTrack = ::findKnownTrack,
        addTrackToPlaylist = ::addTrackToPlaylist,
        toggleCurrentFavorite = ::toggleCurrentFavorite,
        handleShellRatingSelected = ::handleShellRatingSelected,
    )

    AndroidAppShellContent(shellUiState, shellActions)
    }
}
