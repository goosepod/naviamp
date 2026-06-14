package app.naviamp.android

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.naviamp.android.playback.AndroidPlaybackEngine
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.Lyrics
import app.naviamp.domain.app.NaviampRoute
import app.naviamp.domain.provider.ConnectionValidation
import app.naviamp.domain.home.HomeDate
import app.naviamp.domain.home.HomeService
import app.naviamp.domain.playback.PlaybackQueueController
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.playback.PlaybackVisualizerFrame
import app.naviamp.domain.playback.ReplayGainMode
import app.naviamp.domain.playback.SleepTimerController
import app.naviamp.domain.playback.VisualizerPlaybackEngine
import app.naviamp.domain.playback.label
import app.naviamp.domain.popular.SimilarArtistMatch
import app.naviamp.domain.queue.RepeatMode
import app.naviamp.domain.settings.effectiveForEngine
import app.naviamp.domain.sonicautoplay.SonicAutoplayService
import app.naviamp.provider.navidrome.NavidromeApiCall
import app.naviamp.provider.navidrome.NavidromeApiCallHistory
import app.naviamp.provider.navidrome.toNavidromeConnection
import app.naviamp.ui.SharedTrackRowUi
import app.naviamp.ui.NaviampDiagnosticsSectionUi
import app.naviamp.ui.NaviampDiagnosticsUi
import app.naviamp.ui.NaviampLibrarySyncStatusUi
import app.naviamp.ui.NaviampSharedAppShell
import app.naviamp.ui.NaviampRadioArtworkLookupEffect
import app.naviamp.ui.naviampVisualizerFromName
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
@Composable
fun NaviampAndroidApp(
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
    val savedCacheSettings = remember { settingsStore.loadCacheSettings() }
    val appState = rememberAndroidAppState(
        savedConnection = savedConnection,
        savedPlaybackSettings = savedPlaybackSettings,
        savedCacheSettings = savedCacheSettings,
        canAutoConnect = canAutoConnect,
        savedSourceId = savedProviderSource?.id,
        initialStorageStats = storage.stats(),
        initialOpenNowPlayingRequest = openNowPlayingRequest,
        initialAutoPlayMediaIdRequest = autoPlayMediaIdRequest,
        initialAutoCommandRequest = autoCommandRequest,
        initialSelectedVisualizer = naviampVisualizerFromName(settingsStore.loadVisualizerSettings().selectedVisualizer),
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
    val playbackQualityController = remember(appState, context) {
        AndroidPlaybackQualityController(context, appState)
    }

    AndroidAppRuntimeEffects(
        state = appState,
        bassLoadReport = bassLoadReport,
        playbackEngine = playbackEngine,
        pendingProviderActions = storage,
        openNowPlayingRequest = openNowPlayingRequest,
        autoPlayMediaIdRequest = autoPlayMediaIdRequest,
        autoCommandRequest = autoCommandRequest,
    )

    val nowPlayingSidecarController = remember(appState, dependencies) {
        AndroidNowPlayingSidecarController(
            scope = scope,
            state = appState,
            lyricsSidecarService = lyricsSidecarService,
            audioMetadataSidecarService = audioMetadataSidecarService,
            sidecarStatusRepository = sidecarStatusRepository,
            lyricsOffsetRepository = storage,
            cacheAudioTrack = dependencies::cacheAudioTrack,
            currentStreamQuality = playbackQualityController::currentStreamQuality,
        )
    }
    val playbackReportController = remember(appState, storage) {
        AndroidPlaybackReportController(scope, appState, storage)
    }

    val searchController = remember(appState, storage) { AndroidSearchController(appState, storage) }

    NaviampRadioArtworkLookupEffect(
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
            currentStreamQuality = playbackQualityController::currentStreamQuality,
        )
    }

    var restorePlaybackSessionAction: (String) -> Boolean = { false }
    val sonicAutoplayService = remember(appState) {
        SonicAutoplayService(provider = { appState.provider })
    }

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
            currentStreamQuality = playbackQualityController::currentStreamQuality,
            loadRelatedTracks = mediaAppController::loadRelatedTracks,
            sonicAutoplayService = sonicAutoplayService,
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
            startCurrentTrackRadio = { appState.nowPlaying?.let(playbackAppController::startTrackRadio) },
            savePlaybackSessionThrottled = playbackAppController::savePlaybackSessionThrottled,
        )
    }
    androidAutoController.installNotificationControls()

    val navigationController = remember(appState) {
        AndroidNavigationController(appState, mediaAppController::openArtistDetails)
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

    val mixBuilderController = rememberAndroidMixBuilderController(
        scope = scope,
        state = appState,
        queueController = playbackQueueController,
        storage = storage,
        sourceId = { activeSourceId },
        provider = { provider },
        homeContent = { homeState },
        popularTracksService = popularTracksService,
        similarArtistsService = similarArtistsService,
        playTrack = { track, queue -> playbackAppController.playTrack(track, queue, keepRadioQueueActive = true) },
        rememberRecentRadioStream = playbackAppController::rememberRecentRadioStream,
    )

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
        SleepTimerController(
            nowPlaying = { appState.nowPlaying },
            playbackQueue = { appState.playbackQueue },
            playbackProgress = { appState.playbackProgress },
            playbackState = { appState.playbackState },
            setSleepTimer = { timer -> appState.sleepTimer = timer },
            setSleepTimerNowEpochMillis = { millis -> appState.sleepTimerNowEpochMillis = millis },
            setStatus = { status -> appState.status = status },
            stopPlayback = playbackEngine::stop,
            nowEpochMillis = { System.currentTimeMillis() },
        )
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

    AndroidAppPersistenceEffects(
        state = appState,
        downloadRepository = storage,
        cacheMaintenanceRepository = storage,
        updateAudioCacheLimit = storage::updateAudioCacheLimit,
        savePlaybackSessionThrottled = playbackAppController::savePlaybackSessionThrottled,
        checkAndroidLibraryFreshness = { checkAndroidLibraryFreshness(scope, appState, storage, storage) },
    )

    val sonicPathController = remember(appState) {
        AndroidSonicPathController(
            scope = scope,
            state = appState,
            queueController = playbackQueueController,
            playTrack = { track, queue -> playbackAppController.playTrack(track, queue) },
        )
    }
    val sonicMixController = remember(appState) {
        AndroidSonicMixController(
            scope = scope,
            state = appState,
            queueController = playbackQueueController,
            playTrack = { track, queue -> playbackAppController.playTrack(track, queue) },
        )
    }
    val sonicHomeDiscoveryController = remember(appState, storage) {
        AndroidSonicHomeDiscoveryController(
            scope = scope,
            state = appState,
            storage = storage,
            queueController = playbackQueueController,
            playTrack = { track, queue -> playbackAppController.playTrack(track, queue) },
        )
    }
    val coverArtUrlForUi: (String?) -> String? = { coverArtId -> coverArtId?.let { appState.provider?.coverArtUrl(it) } }

    val shellUiState = rememberAndroidAppShellUiState(
        state = appState,
        modifier = modifier,
        context = context,
        bassLoadReport = bassLoadReport,
        playbackEngine = playbackEngine,
        sonicPathBuilder = sonicPathController.ui(coverArtUrlForUi),
        sonicMixBuilder = sonicMixController.ui(coverArtUrlForUi),
    )

    val shellPlaybackController = remember(appState) {
        AndroidShellPlaybackController(
            scope = scope,
            state = appState,
            playbackEngine = playbackEngine,
            playbackQueueController = playbackQueueController,
            activeQueue = mediaAppController::activeQueue,
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
            scope = scope,
            state = appState,
            activeQueue = mediaAppController::activeQueue,
            findKnownTrack = mediaAppController::findKnownTrack,
            playTrack = { track, queue -> playbackAppController.playTrack(track, queue) },
            playNextTracks = mediaAppController::playNextTracks,
            appendTracksToQueue = mediaAppController::appendTracksToQueue,
            downloadTrack = downloadActionController::downloadTrack,
            addTrackToPlaylist = playlistActionController::addTrackToPlaylist,
            removeDownload = downloadActionController::removeDownload,
        )
    }

    AndroidMainEffects(
        state = appState,
        searchController = searchController,
        nowPlayingSidecarController = nowPlayingSidecarController,
        androidAutoController = androidAutoController,
        navigationController = navigationController,
        mediaAppController = mediaAppController,
        mixBuilderController = mixBuilderController,
        sonicHomeDiscoveryController = sonicHomeDiscoveryController,
        connectionSessionController = connectionSessionController,
        sleepTimerController = sleepTimerController,
        providerResponseCacheRepository = storage,
        onAutoPlayMediaIdConsumed = onAutoPlayMediaIdConsumed,
        onAutoCommandConsumed = onAutoCommandConsumed,
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
        sonicPathController = sonicPathController,
        sonicMixController = sonicMixController,
        sonicHomeDiscoveryController = sonicHomeDiscoveryController,
        nowPlayingSidecarController = nowPlayingSidecarController,
    )

    AndroidAppShellContent(shellUiState, shellActions)
    }
}
