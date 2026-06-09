package app.naviamp.android

import android.Manifest
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.content.Context
import android.content.Intent
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
import app.naviamp.android.playback.AndroidBassLoadReport
import app.naviamp.android.playback.AndroidPlaybackEngine
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.Lyrics
import app.naviamp.domain.Playlist
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.isInternetRadioTrack
import app.naviamp.domain.app.NaviampRoute
import app.naviamp.domain.provider.ConnectionValidation
import app.naviamp.domain.provider.PlaylistDetailRefreshIntervalMillis
import app.naviamp.domain.provider.playlistDetailAutoRefreshTarget
import app.naviamp.domain.provider.runPlaylistDetailAutoRefresh
import app.naviamp.domain.home.HomeDate
import app.naviamp.domain.home.HomeService
import app.naviamp.domain.playback.PlaybackQueueController
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.playback.PlaybackVisualizerFrame
import app.naviamp.domain.playback.ReplayGainMode
import app.naviamp.domain.playback.VisualizerPlaybackEngine
import app.naviamp.domain.playback.label
import app.naviamp.domain.popular.SimilarArtistMatch
import app.naviamp.domain.queue.RepeatMode
import app.naviamp.domain.settings.downloadStreamQuality
import app.naviamp.domain.settings.effectiveForEngine
import app.naviamp.domain.settings.streamQualityForNetwork
import app.naviamp.provider.navidrome.NavidromeApiCall
import app.naviamp.provider.navidrome.NavidromeApiCallHistory
import app.naviamp.provider.navidrome.toNavidromeConnection
import app.naviamp.ui.SharedTrackRowUi
import app.naviamp.ui.NaviampDiagnosticsSectionUi
import app.naviamp.ui.NaviampDiagnosticsUi
import app.naviamp.ui.NaviampLibrarySyncStatusUi
import app.naviamp.ui.NaviampSharedAppShell
import app.naviamp.ui.NaviampVisualizer
import app.naviamp.ui.NowPlayingRadioUiConfig
import app.naviamp.ui.NowPlayingTrackUiConfig
import app.naviamp.ui.NowPlayingUi
import app.naviamp.ui.SharedAlbumDetailUi
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
    val mediaAppController = remember(appState, storage, popularTracksService) {
        AndroidMediaAppController(
            scope = scope,
            state = appState,
            storage = storage,
            playbackEngine = playbackEngine,
            queueController = playbackQueueController,
            popularTracksService = popularTracksService,
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

    val searchController = remember(appState, storage) { AndroidSearchController(appState, storage) }

    LaunchedEffect(query, provider) {
        searchController.load(query, debounce = true)
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

    val androidPlaylistEngine = remember(
        appState,
        dependencies,
        playbackQueueController,
    ) {
        dependencies.playlistEngine(
            state = appState,
            playbackQueueController = playbackQueueController,
            activeQueue = mediaAppController::activeQueue,
            currentStreamQuality = ::currentStreamQuality,
        )
    }

    LaunchedEffect(nowPlaying?.id, activeSourceId, provider) {
        nowPlaying?.takeUnless { it.isInternetRadioTrack() }?.let(nowPlayingSidecarController::loadAudioTags)
    }

    var restorePlaybackSessionAction: (String) -> Boolean = { false }

    val playbackAppController = remember(appState, storage, settingsStore, context) {
        AndroidPlaybackAppController(
            context = context,
            scope = scope,
            state = appState,
            storage = storage,
            settingsStore = settingsStore,
            audioAssets = playbackAudioAssets,
            playbackEngine = playbackEngine,
            queueController = playbackQueueController,
            playlistEngine = androidPlaylistEngine,
            playbackReportController = playbackReportController,
            sidecarController = nowPlayingSidecarController,
            activeQueue = mediaAppController::activeQueue,
            currentStreamQuality = ::currentStreamQuality,
            loadRelatedTracks = mediaAppController::loadRelatedTracks,
        )
    }

    val androidAutoController = remember(appState, storage) {
        AndroidAutoAppController(
            scope = scope,
            state = appState,
            storage = storage,
            playbackEngine = playbackEngine,
            restorePlaybackSession = { sourceId -> restorePlaybackSessionAction(sourceId) },
            playTrack = { track, queue, openNowPlaying, startPositionSeconds ->
                playbackAppController.playTrack(
                    track = track,
                    queue = queue,
                    openNowPlaying = openNowPlaying,
                    startPositionSeconds = startPositionSeconds,
                )
            },
            playInternetRadioStation = playbackAppController::playInternetRadioStation,
            playAdjacentTrack = playbackAppController::playAdjacentTrack,
            performSeek = playbackAppController::performSeek,
            toggleCurrentFavorite = mediaAppController::toggleCurrentFavorite,
            savePlaybackSessionThrottled = playbackAppController::savePlaybackSessionThrottled,
        )
    }
    androidAutoController.installNotificationControls()
    androidAutoController.installMediaIdHandler()

    LaunchedEffect(pendingAutoPlayMediaId, provider, activeSourceId) {
        androidAutoController.consumePendingMediaId(onAutoPlayMediaIdConsumed)
    }

    LaunchedEffect(pendingAutoCommand, provider, activeSourceId, nowPlaying?.id, playbackQueue) {
        androidAutoController.consumePendingCommand(onAutoCommandConsumed)
    }

    LaunchedEffect(nowPlaying?.id, nowPlaying?.favoritedAtIso8601, provider?.capabilities?.supportsTrackFavorites) {
        mediaAppController.updateNotificationFavoriteState()
    }

    val navigationController = remember(appState) {
        AndroidNavigationController(appState, mediaAppController::openArtistDetails)
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
            activeQueue = mediaAppController::activeQueue,
            openArtistDetails = { artistId, fallbackName ->
                mediaAppController.openArtistDetails(artistId, fallbackName)
            },
            playTrack = { track, queue -> playbackAppController.playTrack(track, queue) },
            playRadioTrack = { track, queue -> playbackAppController.playTrack(track, queue, keepRadioQueueActive = true) },
            startTrackRadio = playbackAppController::startTrackRadio,
            startAlbumRadio = playbackAppController::startAlbumRadio,
            rememberRecentRadioStream = playbackAppController::rememberRecentRadioStream,
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

    val albumMixBuilderService = rememberAndroidAlbumMixBuilderService(
        storage = storage,
        sourceId = { activeSourceId },
        provider = { provider },
        homeContent = homeState,
        similarArtistsService = similarArtistsService,
    )

    val genreMixBuilderService = rememberAndroidGenreMixBuilderService(
        provider = { provider },
        homeContent = homeState,
    )

    val mixBuilderController = AndroidMixBuilderController(
        scope = scope,
        state = appState,
        queueController = playbackQueueController,
        storage = storage,
        artistMixBuilderService = { artistMixBuilderService },
        albumMixBuilderService = { albumMixBuilderService },
        genreMixBuilderService = { genreMixBuilderService },
        playTrack = { track, queue -> playbackAppController.playTrack(track, queue, keepRadioQueueActive = true) },
        rememberRecentRadioStream = playbackAppController::rememberRecentRadioStream,
    )

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

    val playlistActionController = remember(appState, storage) {
        AndroidPlaylistActionController(
            scope = scope,
            state = appState,
            storage = storage,
            playTrack = { track, queue -> playbackAppController.playTrack(track, queue) },
            appendTracksToQueue = mediaAppController::appendTracksToQueue,
        )
    }

    val sleepTimerController = remember(appState) {
        AndroidSleepTimerController(appState, playbackEngine)
    }

    val downloadActionController = remember(appState, storage, context) {
        AndroidDownloadActionController(
            context = context,
            scope = scope,
            state = appState,
            storage = storage,
            findKnownTrack = mediaAppController::findKnownTrack,
        )
    }

    val settingsMaintenanceController = remember(appState, storage, settingsStore, context) {
        AndroidSettingsMaintenanceController(
            context = context,
            state = appState,
            storage = storage,
            settingsStore = settingsStore,
            playbackEngine = playbackEngine,
            queueController = playbackQueueController,
            reloadVisibleLyrics = nowPlayingSidecarController::reloadVisibleLyrics,
            redownloadTracks = downloadActionController::redownloadTracks,
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
            preloadPlaylistTracks = playlistActionController::preloadPlaylistTracks,
            loadRelatedTracks = mediaAppController::loadRelatedTracks,
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
        savePlaybackSessionThrottled = playbackAppController::savePlaybackSessionThrottled,
        checkAndroidLibraryFreshness = { checkAndroidLibraryFreshness(scope, appState, storage, storage) },
    )

    val shellUiState = rememberAndroidAppShellUiState(
        state = appState,
        modifier = modifier,
        context = context,
        bassLoadReport = bassLoadReport,
        playbackEngine = playbackEngine,
    )

    val shellPlaybackController = remember(appState) {
        AndroidShellPlaybackController(
            scope = scope,
            state = appState,
            playbackEngine = playbackEngine,
            playbackQueueController = playbackQueueController,
            activeQueue = mediaAppController::activeQueue,
            findKnownTrack = mediaAppController::findKnownTrack,
            playTrack = playbackAppController::playTrack,
            playInternetRadioStation = playbackAppController::playInternetRadioStation,
            rememberRecentRadioStream = playbackAppController::rememberRecentRadioStream,
        )
    }

    val shellMediaController = remember(appState, storage, settingsStore) {
        AndroidShellMediaController(
            scope = scope,
            state = appState,
            storage = storage,
            settingsStore = settingsStore,
            queueController = playbackQueueController,
            playbackEngine = playbackEngine,
            internetRadioStationManager = dependencies.internetRadioStationManager,
            activeQueue = mediaAppController::activeQueue,
            findKnownTrack = mediaAppController::findKnownTrack,
            playTrack = { track, queue -> playbackAppController.playTrack(track, queue) },
            playRadioTrack = { track, queue -> playbackAppController.playTrack(track, queue, keepRadioQueueActive = true) },
            playInternetRadioStation = playbackAppController::playInternetRadioStation,
            startTrackRadio = playbackAppController::startTrackRadio,
            startAlbumRadio = playbackAppController::startAlbumRadio,
            openArtistDetails = { artistId, fallbackName ->
                mediaAppController.openArtistDetails(artistId, fallbackName)
            },
            rememberRecentRadioStream = playbackAppController::rememberRecentRadioStream,
        )
    }

    val trackActionController = remember(appState) {
        AndroidTrackActionController(
            state = appState,
            activeQueue = mediaAppController::activeQueue,
            findKnownTrack = mediaAppController::findKnownTrack,
            playTrack = { track, queue -> playbackAppController.playTrack(track, queue) },
            appendTracksToQueue = mediaAppController::appendTracksToQueue,
            downloadTrack = downloadActionController::downloadTrack,
            addTrackToPlaylist = playlistActionController::addTrackToPlaylist,
        )
    }

    AndroidSleepTimerExpiryEffect(
        sleepTimer = sleepTimer,
        snapshot = sleepTimerController.snapshot(),
        onTick = sleepTimerController::tick,
        onExpired = sleepTimerController::expire,
    )

    val shellActions = androidMainShellActions(
        scope = scope,
        state = appState,
        storage = storage,
        playbackEngine = playbackEngine,
        settingsStore = settingsStore,
        searchController = searchController,
        mediaAppController = mediaAppController,
        playbackAppController = playbackAppController,
        navigationController = navigationController,
        artistActionController = artistActionController,
        mixBuilderController = mixBuilderController,
        playlistActionController = playlistActionController,
        sleepTimerController = sleepTimerController,
        downloadActionController = downloadActionController,
        settingsMaintenanceController = settingsMaintenanceController,
        connectionSessionController = connectionSessionController,
        shellPlaybackController = shellPlaybackController,
        shellMediaController = shellMediaController,
        trackActionController = trackActionController,
        nowPlayingSidecarController = nowPlayingSidecarController,
    )

    AndroidAppShellContent(shellUiState, shellActions)
    }
}
