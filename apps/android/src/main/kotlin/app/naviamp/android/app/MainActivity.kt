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
import app.naviamp.domain.ArtistId
import app.naviamp.domain.Lyrics
import app.naviamp.domain.Playlist
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.Track
import app.naviamp.domain.isInternetRadioTrack
import app.naviamp.domain.app.NaviampRoute
import app.naviamp.domain.provider.ConnectionValidation
import app.naviamp.domain.provider.PlaylistDetailRefreshIntervalMillis
import app.naviamp.domain.provider.allKnownTracks
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

    val searchController = remember(appState, storage) { AndroidSearchController(appState, storage) }

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
            activeQueue = ::activeQueue,
            currentStreamQuality = ::currentStreamQuality,
            loadRelatedTracks = ::loadRelatedTracks,
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
            toggleCurrentFavorite = ::toggleCurrentFavorite,
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
            appendTracksToQueue = ::appendTracksToQueue,
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
            findKnownTrack = ::findKnownTrack,
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
            activeQueue = ::activeQueue,
            findKnownTrack = ::findKnownTrack,
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
            activeQueue = ::activeQueue,
            findKnownTrack = ::findKnownTrack,
            playTrack = { track, queue -> playbackAppController.playTrack(track, queue) },
            playRadioTrack = { track, queue -> playbackAppController.playTrack(track, queue, keepRadioQueueActive = true) },
            playInternetRadioStation = playbackAppController::playInternetRadioStation,
            startTrackRadio = playbackAppController::startTrackRadio,
            startAlbumRadio = playbackAppController::startAlbumRadio,
            openArtistDetails = ::openArtistDetails,
            rememberRecentRadioStream = playbackAppController::rememberRecentRadioStream,
        )
    }

    val trackActionController = remember(appState) {
        AndroidTrackActionController(
            state = appState,
            activeQueue = ::activeQueue,
            findKnownTrack = ::findKnownTrack,
            playTrack = { track, queue -> playbackAppController.playTrack(track, queue) },
            appendTracksToQueue = ::appendTracksToQueue,
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

    val shellActions = androidAppShellActions(
        state = appState,
        playbackEngine = playbackEngine,
        settingsStore = settingsStore,
        handleConnectionFormChanged = settingsMaintenanceController::handleConnectionFormChanged,
        connectToNavidrome = connectionSessionController::connectToNavidrome,
        handlePlaybackSettingsChanged = settingsMaintenanceController::handlePlaybackSettingsChanged,
        handlePlaybackSettingsChangedAndRedownload = settingsMaintenanceController::handlePlaybackSettingsChangedAndRedownload,
        handleClearCache = settingsMaintenanceController::handleClearCache,
        handleClearLibrary = settingsMaintenanceController::handleClearLibrary,
        handleResetDatabase = settingsMaintenanceController::handleResetDatabase,
        handleSearch = { searchController.launchSearch(scope) },
        handleArtistMixSearch = mixBuilderController::searchArtistSuggestions,
        handleArtistMixArtistSelected = { item -> mixBuilderController.selectArtistByItemId(item.id) },
        handleArtistMixArtistRemoved = { item -> mixBuilderController.removeArtistByItemId(item.id) },
        handleArtistMixReset = mixBuilderController::resetArtistBuilder,
        handleArtistMixPlay = mixBuilderController::playArtistMix,
        handleAlbumMixSearch = mixBuilderController::searchAlbumSuggestions,
        handleAlbumMixAlbumSelected = { item -> mixBuilderController.selectAlbumByItemId(item.id) },
        handleAlbumMixAlbumRemoved = { item -> mixBuilderController.removeAlbumByItemId(item.id) },
        handleAlbumMixReset = mixBuilderController::resetAlbumBuilder,
        handleAlbumMixPlay = mixBuilderController::playAlbumMix,
        handleGenreMixSearch = mixBuilderController::refreshGenreSuggestions,
        handleGenreMixGenreSelected = { item -> mixBuilderController.selectGenreByItemId(item.id) },
        handleGenreMixGenreRemoved = { item -> mixBuilderController.removeGenreByItemId(item.id) },
        handleGenreMixReset = mixBuilderController::resetGenreBuilder,
        handleGenreMixPlay = mixBuilderController::playGenreMix,
        startAndroidLibrarySync = { force -> startAndroidLibrarySync(scope, appState, storage, force) },
        handleShellTrackSelected = shellMediaController::handleShellTrackSelected,
        handleDownloadedTrackSelected = trackActionController::handleDownloadedTrackSelected,
        handleDownloadedTrackAddToPlaylist = trackActionController::handleDownloadedTrackAddToPlaylist,
        handleDownloadedTrackCreatePlaylistAndAdd = trackActionController::handleDownloadedTrackCreatePlaylistAndAdd,
        removeDownload = downloadActionController::removeDownload,
        handleShellAlbumSelected = shellMediaController::handleShellAlbumSelected,
        handleAlbumFavoriteToggled = { item -> toggleAndroidAlbumFavorite(scope, appState, item) },
        handleMixAlbumSelected = shellMediaController::handleMixAlbumSelected,
        handleShellAlbumPlay = shellMediaController::handleShellAlbumPlay,
        handleShellAlbumTrackSelected = shellMediaController::handleShellAlbumTrackSelected,
        handleShellAlbumRadio = shellMediaController::handleShellAlbumRadio,
        appendTracksToQueue = ::appendTracksToQueue,
        downloadTracks = downloadActionController::downloadTracks,
        addTracksToPlaylist = playlistActionController::addTracksToPlaylist,
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
        openPlaylistDetails = playlistActionController::openPlaylistDetails,
        playPlaylist = playlistActionController::playPlaylist,
        downloadPlaylist = downloadActionController::downloadPlaylist,
        addPlaylistToQueue = playlistActionController::addPlaylistToQueue,
        addPlaylistToPlaylist = playlistActionController::addPlaylistToPlaylist,
        renamePlaylist = playlistActionController::renamePlaylist,
        deletePlaylist = playlistActionController::deletePlaylist,
        saveSmartPlaylist = playlistActionController::saveSmartPlaylist,
        updateSmartPlaylist = playlistActionController::updateSmartPlaylist,
        loadSmartPlaylist = playlistActionController::loadSmartPlaylistDefinition,
        closeActivePlaylist = navigationController::closeActivePlaylist,
        handlePlaylistTrackSelected = trackActionController::handlePlaylistTrackSelected,
        handleRecentRadioSelected = shellMediaController::handleShellRecentRadioSelected,
        handleMixBuilderSelected = navigationController::handleMixBuilderSelected,
        handleRadioStationSelected = shellMediaController::handleRadioStationSelected,
        saveInternetRadioStation = shellMediaController::saveInternetRadioStation,
        deleteInternetRadioStation = shellMediaController::deleteInternetRadioStation,
        handleShellHomeStationSelected = shellMediaController::handleShellHomeStationSelected,
        closeActiveDetail = navigationController::closeActiveDetail,
        handleShellResume = shellPlaybackController::resume,
        playAdjacentTrack = playbackAppController::playAdjacentTrack,
        performSeek = playbackAppController::performSeek,
        handleShellToggleShuffle = shellPlaybackController::toggleShuffle,
        loadLyrics = nowPlayingSidecarController::loadLyrics,
        handleLyricsOffsetChanged = nowPlayingSidecarController::handleLyricsOffsetChanged,
        handleShellTrackRadio = shellPlaybackController::startCurrentTrackRadio,
        handleNowPlayingAddToPlaylist = trackActionController::handleNowPlayingAddToPlaylist,
        handleNowPlayingCreatePlaylistAndAdd = trackActionController::handleNowPlayingCreatePlaylistAndAdd,
        handleSaveQueueAsPlaylist = playlistActionController::saveQueueAsPlaylist,
        handleSleepTimerSelected = sleepTimerController::select,
        handleCancelSleepTimer = sleepTimerController::cancel,
        downloadTrack = downloadActionController::downloadTrack,
        handleShellGoToAlbum = shellMediaController::handleShellGoToAlbum,
        handleShellGoToArtist = shellMediaController::handleShellGoToArtist,
        handleShellQueueItemRadio = shellPlaybackController::startQueueItemRadio,
        findKnownTrack = ::findKnownTrack,
        addTrackToPlaylist = playlistActionController::addTrackToPlaylist,
        toggleCurrentFavorite = ::toggleCurrentFavorite,
        handleShellRatingSelected = shellMediaController::handleShellRatingSelected,
    )

    AndroidAppShellContent(shellUiState, shellActions)
    }
}
