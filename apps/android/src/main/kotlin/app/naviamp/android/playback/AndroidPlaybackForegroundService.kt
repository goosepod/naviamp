package app.naviamp.android.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.ForegroundServiceStartNotAllowedException
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaSessionCompat
import androidx.media.MediaBrowserServiceCompat
import app.naviamp.android.AndroidStorageDependencies
import app.naviamp.android.AndroidSettingsStore
import app.naviamp.android.AndroidPlaybackAudioAssets
import app.naviamp.android.MainActivity
import app.naviamp.android.markAndroidSettingsSyncChangedAndAutoExport
import app.naviamp.android.resolveInternetRadioStreamUrl
import app.naviamp.android.withAndroidPendingActions
import app.naviamp.app.NaviampProviderActionController
import app.naviamp.app.NaviampRecentRadioStreamController
import app.naviamp.domain.Album
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistId
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.cache.LocalLibraryIndexRepository
import app.naviamp.domain.cache.MediaSourceRepository
import app.naviamp.domain.cache.PlaybackHistoryRepository
import app.naviamp.domain.cache.PlaybackSessionRepository
import app.naviamp.domain.cache.ProviderResponseCacheRepository
import app.naviamp.domain.cache.ProviderResponseService
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackQueueController
import app.naviamp.domain.playback.PlaybackRequest
import app.naviamp.domain.playback.MediaVoiceQuery
import app.naviamp.domain.playback.bestVoiceNameMatch
import app.naviamp.domain.playback.nextRepeatMode
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode
import app.naviamp.domain.network.KtorSharedHttpClient
import app.naviamp.domain.radio.RadioService
import app.naviamp.domain.radio.InternetRadioRecentStationApplier
import app.naviamp.domain.radio.applyRememberInternetRadioStation
import app.naviamp.domain.radio.planRememberInternetRadioStation
import app.naviamp.domain.radio.withRadioCoverArtIds
import app.naviamp.domain.settings.PlaybackSessionSettings
import app.naviamp.domain.settings.RecentRadioKind
import app.naviamp.domain.settings.RecentRadioStream
import app.naviamp.domain.settings.SavedTrack
import app.naviamp.domain.settings.playbackSessionFromQueue
import app.naviamp.domain.source.SavedMediaSource
import app.naviamp.provider.navidrome.NavidromeProvider
import app.naviamp.provider.navidrome.toNavidromeConnection
import app.naviamp.ui.defaultRadioArtworkUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.UUID
import kotlin.concurrent.thread

private const val AndroidAutoArtistAlbumFallbackLimit = 4
class AndroidPlaybackForegroundService : MediaBrowserServiceCompat() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var serviceActive = false
    private var serviceStorageInstance: AndroidStorageDependencies? = null
    private var loadingNotificationCoverArtUrl: String? = null
    private var serviceSelectionJobs: AndroidAutoSelectionJobs? = null
    private val notificationArtHttpClient = KtorSharedHttpClient()
    private val notificationFactory by lazy {
        AndroidPlaybackNotificationFactory(
            context = this,
            channelId = ChannelId,
            contentIntent = ::notificationContentIntent,
            deleteIntent = ::stopPendingIntent,
            actionIntent = ::notificationActionPendingIntent,
            mediaSessionToken = {
                ensureMediaSession().sessionToken.token as android.media.session.MediaSession.Token
            },
            publishMediaSession = ::updateMediaSession,
            playerColor = PlayerNotificationColor,
            previousAction = ActionPrevious,
            playPauseAction = ActionPlayPause,
            nextAction = ActionNext,
            favoriteAction = ActionFavorite,
        )
    }
    private val serviceStorage: AndroidStorageDependencies
        get() = serviceStorageInstance ?: AndroidStorageDependencies(applicationContext).also { serviceStorageInstance = it }
    private val serviceProviderActions: NaviampProviderActionController by lazy {
        NaviampProviderActionController(serviceStorage)
    }
    private val serviceSettingsStore: AndroidSettingsStore by lazy {
        AndroidSettingsStore(applicationContext)
    }
    private val recentRadioStreamController: NaviampRecentRadioStreamController by lazy {
        NaviampRecentRadioStreamController(
            load = serviceSettingsStore::loadRecentRadioStreams,
            save = serviceSettingsStore::saveRecentRadioStreams,
            onChanged = {
                markAndroidSettingsSyncChangedAndAutoExport(
                    context = applicationContext,
                    settingsStore = serviceSettingsStore,
                    storage = serviceStorage,
                )
            },
        )
    }
    private val autoQueueController = PlaybackQueueController()
    private val autoBrowseController: AndroidAutoBrowseController by lazy {
        AndroidAutoBrowseController(
            context = applicationContext,
            storage = { serviceStorage },
            currentQueue = { currentAutoQueue },
            currentQueueIndex = { currentAutoQueueIndex },
            currentMetadata = { currentMetadata },
            restoredNowPlayingMetadata = { restoredNowPlayingMetadata() },
            providerResponseService = { cacheRepository -> providerResponseService(cacheRepository) },
            loadArtistTracks = ::loadServiceArtistTracks,
            loadAlbumTracks = ::loadServiceAlbumTracks,
        )
    }
    private val mediaBrowserController: AndroidMediaBrowserController by lazy {
        AndroidMediaBrowserController(
            context = applicationContext,
            applicationUid = applicationInfo.uid,
            hydrateSession = ::hydrateSavedPlaybackSession,
            browse = autoBrowseController,
        )
    }
    private val autoSelectionController: AndroidAutoCatalogSelectionController by lazy {
        AndroidAutoCatalogSelectionController(
            object : AndroidAutoCatalogSelectionHost {
                override val storage: AndroidStorageDependencies get() = serviceStorage
                override val settings: AndroidSettingsStore get() = serviceSettingsStore
                override fun resume() = handleServiceAutoPlayPause()
                override fun playQueueItem(index: Int) = playServiceAutoQueueItem(index)
                override fun launch(block: suspend () -> Unit) = launchServiceSelection(block)
                override fun playQueue(tracks: List<Track>, index: Int) {
                    storage.latestNavidromeSource()?.id?.let { playServiceTrackQueue(storage, it, tracks, index) }
                }
                override fun fallbackQueue(track: Track): List<Track> {
                    val sourceId = storage.latestNavidromeSource()?.id ?: return listOf(track)
                    return serviceQueueForLibraryTrack(storage, sourceId, track)
                }
                override suspend fun loadPlaylist(provider: NavidromeProvider, id: String): List<Track> =
                    loadServicePlaylistTracks(storage, provider, id)
                override suspend fun loadArtist(provider: NavidromeProvider, id: String, name: String?): List<Track> {
                    val sourceId = storage.latestNavidromeSource()?.id ?: return emptyList()
                    return loadServiceArtistTracks(storage, storage, sourceId, provider, id, name)
                }
                override suspend fun loadAlbum(provider: NavidromeProvider, id: String, title: String?, artist: String?): List<Track> {
                    val sourceId = storage.latestNavidromeSource()?.id ?: return emptyList()
                    return loadServiceAlbumTracks(storage, storage, sourceId, provider, id, title, artist)
                }
                override fun playStation(station: InternetRadioStation) {
                    storage.latestNavidromeSource()?.id?.let { playServiceInternetRadioStation(storage, storage, it, station) }
                }
                override fun playRecent(stream: RecentRadioStream) {
                    storage.latestNavidromeSource()?.id?.let { playServiceRecentRadioStream(storage, storage, it, stream) }
                }
                override fun rememberRecent(stream: RecentRadioStream) = rememberRecentRadioStream(stream)
                override fun failed(message: String, error: Throwable?) {
                    if (error == null) Log.w("NaviampAutoCommand", message) else Log.w("NaviampAutoCommand", message, error)
                    AndroidPlaybackNotificationControls.isPlaying = false
                    updateMediaSessionPlaybackState()
                }
            },
        )
    }
    private val autoCommandController: AndroidAutoCommandController by lazy {
        AndroidAutoCommandController(
            handleServiceAutoPlayPause = { handleServiceAutoPlayPause() },
            handleServicePlayMediaId = ::handleServicePlayMediaId,
            handleServicePlaySearch = ::handleServicePlaySearch,
            launchMainActivityForAutoMediaId = ::launchMainActivityForAutoMediaId,
            toggleFavorite = { toggleServiceFavorite() },
            toggleShuffle = { toggleServiceShuffle() },
            cycleRepeat = { cycleServiceRepeatMode() },
            openQueue = { openAutoQueue() },
            startTrackRadio = { startServiceCurrentTrackRadio() },
            refreshNotification = { refreshNotification(null) },
            isPlaying = { AndroidPlaybackNotificationControls.isPlaying },
            favoriteAction = ActionFavorite,
            shuffleAction = ActionShuffle,
            repeatAction = ActionRepeat,
            queueAction = ActionQueue,
            trackRadioAction = ActionTrackRadio,
        )
    }
    private val mediaSessionController: AndroidMediaSessionController by lazy {
        AndroidMediaSessionController(
            context = this,
            storage = { serviceStorage },
            queue = { currentAutoQueue },
            queueIndex = { currentAutoQueueIndex },
            metadata = { currentMetadata },
            artwork = { currentLargeIcon },
            shuffleEnabled = { serviceShuffleEnabled },
            repeatMode = { serviceRepeatMode },
            actions = AndroidMediaSessionActions(
                play = {
                    if (!AndroidPlaybackNotificationControls.isPlaying) {
                        handleAutoPlayPause()
                        refreshNotification(null)
                    }
                },
                pause = {
                    if (AndroidPlaybackNotificationControls.isPlaying) {
                        handleAutoPlayPause()
                        refreshNotification(null)
                    }
                },
                previous = { handleAutoPrevious(); refreshNotification(null) },
                next = { handleAutoNext(); refreshNotification(null) },
                queueItem = { playServiceAutoQueueItem(it); refreshNotification(null) },
                stop = { handleAutoStop("media session stop") },
                seek = ::handleAutoSeek,
                playMediaId = autoCommandController::playFromMediaId,
                playSearch = autoCommandController::playFromSearch,
                customAction = autoCommandController::customAction,
            ),
            publishBrowserToken = ::setSessionToken,
            favoriteAction = ActionFavorite,
            shuffleAction = ActionShuffle,
            repeatAction = ActionRepeat,
            trackRadioAction = ActionTrackRadio,
            queueTrackPrefix = AndroidAutoPlaybackControls.MediaIdQueueTrackPrefix,
        )
    }
    private val serviceSessionController: AndroidPlaybackServiceSessionController by lazy {
        AndroidPlaybackServiceSessionController(
            sessions = AndroidPlaybackServiceStorageSessionStore { serviceStorage },
            currentMetadata = { currentMetadata },
            setCurrentMetadata = ::setCurrentMetadata,
            syncQueue = ::syncAutoQueue,
            updateMediaSession = { metadata -> updateMediaSession(metadata, currentLargeIcon) },
            loadCoverArt = { url, metadata -> loadCoverArtAsync(url, metadata) },
        )
    }
    private val servicePlaybackRuntimeController: AndroidServicePlaybackRuntimeController by lazy {
        AndroidServicePlaybackRuntimeController(
            context = applicationContext,
            storage = { serviceStorage },
            providerActions = serviceProviderActions,
            queueController = autoQueueController,
            currentQueue = { currentAutoQueue },
            currentQueueIndex = { currentAutoQueueIndex },
            syncQueue = ::syncAutoQueue,
            repeatMode = { serviceRepeatModeForQueue() },
            currentMetadata = { currentMetadata },
            setCurrentMetadata = ::setCurrentMetadata,
            updateMediaSession = { metadata -> updateMediaSession(metadata, currentLargeIcon) },
            updateMediaSessionPlaybackState = { updateMediaSessionPlaybackState() },
            loadCoverArt = { url, metadata -> loadCoverArtAsync(url, metadata) },
            playTrackQueue = ::playServiceTrackQueue,
            playInternetRadioStation = ::playServiceInternetRadioStation,
        )
    }

    private fun providerResponseService(cacheRepository: ProviderResponseCacheRepository = serviceStorage): ProviderResponseService =
        ProviderResponseService(cacheRepository)

    private val noisyAudioReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != AudioManager.ACTION_AUDIO_BECOMING_NOISY) return
            pausePlaybackForRouteDisconnect("audio becoming noisy")
        }
    }

    override fun onCreate() {
        super.onCreate()
        serviceActive = true
        serviceCreated = true
        ensureNotificationChannel()
        ensureMediaSession()
        hydrateSavedPlaybackSession()
        registerReceiver(noisyAudioReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
    }

    override fun onDestroy() {
        serviceActive = false
        mainHandler.removeCallbacksAndMessages(null)
        serviceSelectionJobs?.cancel()
        serviceSelectionJobs = null
        pausePlaybackForRouteDisconnect("service destroyed")
        runCatching { unregisterReceiver(noisyAudioReceiver) }
        mediaSessionController.release()
        serviceCreated = false
        super.onDestroy()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (
            androidPlaybackServiceRetention(servicePlaybackRuntimeController.ownsPlayback()) ==
            AndroidPlaybackServiceRetention.KeepAlive
        ) {
            Log.i("NaviampAutoCommand", "Android Auto browser unbound while service owns playback; keeping playback alive")
            updateMediaSessionPlaybackState()
            return super.onUnbind(intent)
        }
        pausePlaybackForRouteDisconnect("Android Auto browser unbound")
        return super.onUnbind(intent)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (
            androidPlaybackServiceRetention(servicePlaybackRuntimeController.ownsPlayback()) ==
            AndroidPlaybackServiceRetention.KeepAlive
        ) {
            Log.i("NaviampAutoCommand", "Phone task removed while service owns playback; keeping Auto session alive")
            updateMediaSessionPlaybackState()
            super.onTaskRemoved(rootIntent)
            return
        }
        stopPlaybackAndService("task removed")
        super.onTaskRemoved(rootIntent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val startPlan = planAndroidPlaybackServiceStart(intentPresent = intent != null)
        if (
            isProtectedPlaybackServiceAction(intent?.action) &&
            !isAuthorizedPlaybackServiceCommand(
                suppliedCapability = intent?.getStringExtra(ExtraCommandCapability),
                expectedCapability = CommandCapability,
            )
        ) {
            Log.w("NaviampAutoCommand", "Rejecting unauthorized playback service action=${intent?.action}")
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ActionPlayPause -> {
                handleAutoPlayPause()
                refreshNotification(intent)
                return START_STICKY
            }
            ActionPrevious -> {
                handleAutoPrevious()
                refreshNotification(intent)
                return START_STICKY
            }
            ActionNext -> {
                handleAutoNext()
                refreshNotification(intent)
                return START_STICKY
            }
            ActionFavorite -> {
                toggleServiceFavorite()
                return START_STICKY
            }
            ActionTrackRadio -> {
                startServiceCurrentTrackRadio()
                return START_STICKY
            }
            ActionStop -> {
                if (intent.getBooleanExtra(ExtraFromEngine, false).not()) {
                    stopPlaybackForUserRequest("stop action")
                }
                stopForeground(STOP_FOREGROUND_REMOVE)
                mediaSessionController.setActive(false)
                stopSelf()
                return START_NOT_STICKY
            }
            ActionProgress -> {
                val previousPositionMillis = AndroidPlaybackNotificationControls.positionMillis
                val previousDurationMillis = AndroidPlaybackNotificationControls.durationMillis
                intent.getLongExtra(ExtraPositionMillis, -1L)
                    .takeIf { it >= 0L }
                    ?.let { positionMillis ->
                        AndroidPlaybackNotificationControls.positionMillis = positionMillis
                    }
                val incomingDurationMillis = intent.getLongExtra(ExtraDurationMillis, -1L)
                if (incomingDurationMillis > 0L) {
                    AndroidPlaybackNotificationControls.durationMillis = incomingDurationMillis
                }
                val durationChanged = previousDurationMillis != AndroidPlaybackNotificationControls.durationMillis
                val positionChanged = previousPositionMillis != AndroidPlaybackNotificationControls.positionMillis
                if (durationChanged) {
                    updateMediaSession(currentMetadata, currentLargeIcon)
                } else if (!AndroidPlaybackNotificationControls.isPlaying && positionChanged) {
                    updateMediaSessionPlaybackState()
                }
                return START_STICKY
            }
            ActionUpdate -> {
                refreshNotification(intent)
                return START_STICKY
            }
            else -> {
                ensureNotificationChannel()
                val metadata = intent.toMetadata()
                if (startPlan.republishMediaSession) {
                    updateMediaSession(currentMetadata, currentLargeIcon)
                    updateMediaSessionPlaybackState()
                }
                if (startPlan.publishNotification && !startForegroundSafely(metadata)) {
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
                loadCoverArtIfNeeded(metadata)
                return START_STICKY
            }
        }
    }

    private fun startForegroundSafely(metadata: AndroidPlaybackNotificationMetadata): Boolean =
        runCatching {
            startForeground(NotificationId, buildNotification(metadata, largeIcon = null))
        }.onFailure { error ->
            val notAllowed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                error is ForegroundServiceStartNotAllowedException
            if (notAllowed) {
                Log.w("NaviampAutoCommand", "Android rejected playback foreground start from background", error)
            } else {
                Log.w("NaviampAutoCommand", "Could not promote playback service to foreground", error)
            }
        }.isSuccess

    private fun refreshNotification(intent: Intent?) {
        val manager = getSystemService(NotificationManager::class.java)
        val metadata = intent.toMetadata()
        manager.notify(NotificationId, buildNotification(metadata, largeIcon = null))
        loadCoverArtIfNeeded(metadata)
    }

    private fun buildNotification(
        metadata: AndroidPlaybackNotificationMetadata,
        largeIcon: Bitmap?,
    ): Notification {
        if (largeIcon != null) {
            currentLargeIcon = largeIcon
        }
        val coverArt = largeIcon ?: currentLargeIcon
        return notificationFactory.build(metadata, coverArt)
    }

    private fun notificationContentIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.ExtraOpenNowPlaying, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun notificationActionPendingIntent(action: String): PendingIntent =
        PendingIntent.getService(
            this,
            action.hashCode(),
            Intent(this, AndroidPlaybackForegroundService::class.java)
                .setAction(action)
                .putExtra(ExtraCommandCapability, CommandCapability)
                .putExtra(ExtraTitle, currentMetadata.title)
                .putExtra(ExtraSubtitle, currentMetadata.subtitle)
                .putExtra(ExtraCoverArtUrl, currentMetadata.coverArtUrl),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun stopPendingIntent(): PendingIntent =
        PendingIntent.getService(
            this,
            ActionStop.hashCode(),
            Intent(this, AndroidPlaybackForegroundService::class.java)
                .setAction(ActionStop)
                .putExtra(ExtraCommandCapability, CommandCapability)
                .putExtra(ExtraTitle, currentMetadata.title)
                .putExtra(ExtraSubtitle, currentMetadata.subtitle)
                .putExtra(ExtraCoverArtUrl, currentMetadata.coverArtUrl),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun loadCoverArtAsync(coverArtUrl: String, metadata: AndroidPlaybackNotificationMetadata) {
        loadingNotificationCoverArtUrl = coverArtUrl
        thread(name = "naviamp-notification-art") {
            val bitmap = runCatching {
                runBlocking {
                    notificationCoverArtBytes(coverArtUrl)
                        ?.let { decodeSampledBitmap(it, NotificationCoverArtSidePx) }
                }
            }.getOrNull() ?: run {
                if (loadingNotificationCoverArtUrl == coverArtUrl) {
                    loadingNotificationCoverArtUrl = null
                }
                return@thread
            }
            if (currentMetadata.coverArtUrl != coverArtUrl) {
                if (loadingNotificationCoverArtUrl == coverArtUrl) {
                    loadingNotificationCoverArtUrl = null
                }
                return@thread
            }
            loadingNotificationCoverArtUrl = null
            mainHandler.post {
                if (!serviceActive || currentMetadata.coverArtUrl != coverArtUrl) return@post
                val manager = getSystemService(NotificationManager::class.java)
                manager.notify(NotificationId, buildNotification(metadata, largeIcon = bitmap))
            }
        }
    }

    private fun loadCoverArtIfNeeded(metadata: AndroidPlaybackNotificationMetadata) {
        val coverArtUrl = metadata.coverArtUrl?.takeIf { it.isNotBlank() } ?: return
        if (currentLargeIcon != null && currentMetadata.coverArtUrl == coverArtUrl) return
        if (loadingNotificationCoverArtUrl == coverArtUrl) return
        loadCoverArtAsync(coverArtUrl, metadata)
    }

    private suspend fun notificationCoverArtBytes(url: String): ByteArray? {
        serviceStorage.cachedImageBytes(url)?.let { bytes ->
            Log.i("NaviampAutoCommand", "Loaded notification cover art from cache bytes=${bytes.size}")
            return bytes
        }
        val provider = serviceStorage.latestNavidromeSource()
            ?.toNavidromeConnection()
            ?.let(::NavidromeProvider)
        return serviceStorage.imageBytes(url) {
            provider
                ?.takeIf { it.ownsUrl(url) }
                ?.bytes(url)
                ?: notificationArtHttpClient.getBytes(url)
                ?: throw IllegalStateException("Could not download notification cover art.")
        }
    }

    private fun launchMainActivityForAutoMediaId(mediaId: String) {
        Log.i("NaviampAutoCommand", "Launching phone app for Auto mediaId=$mediaId")
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .putExtra(MainActivity.ExtraOpenNowPlaying, true)
                    .putExtra(MainActivity.ExtraAutoPlayMediaId, mediaId)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            )
        }.onFailure { error ->
            Log.w("NaviampAutoCommand", "Could not launch phone app for mediaId=$mediaId", error)
        }
    }

    private fun handleServiceAutoPlayPause() {
        cancelPendingServiceSelection()
        servicePlaybackRuntimeController.handleAutoPlayPause()
    }

    private fun handleAutoPlayPause() {
        cancelPendingServiceSelection()
        if (servicePlaybackRuntimeController.ownsPlayback()) {
            handleServiceAutoPlayPause()
            return
        }
        AndroidPlaybackNotificationControls.onPlayPause?.invoke()
            ?: handleServiceAutoPlayPause()
    }

    private fun handleAutoPrevious() {
        cancelPendingServiceSelection()
        if (playServiceOwnedAdjacent(-1)) return
        AndroidPlaybackNotificationControls.onPrevious?.invoke()
            ?: playSavedSessionAdjacent(-1)
    }

    private fun handleAutoNext() {
        cancelPendingServiceSelection()
        if (playServiceOwnedAdjacent(1)) return
        AndroidPlaybackNotificationControls.onNext?.invoke()
            ?: playSavedSessionAdjacent(1)
    }

    private fun handleAutoStop(reason: String) {
        cancelPendingServiceSelection()
        if (servicePlaybackRuntimeController.ownsPlayback()) {
            stopServiceOwnedPlayback(reason)
            return
        }
        AndroidPlaybackNotificationControls.onStop?.invoke()
            ?: stopServiceOwnedPlayback(reason)
    }

    private fun handleAutoSeek(positionMillis: Long) {
        cancelPendingServiceSelection()
        if (servicePlaybackRuntimeController.ownsPlayback()) {
            seekServiceOwnedPlayback(positionMillis)
            return
        }
        val seekCallback = AndroidPlaybackNotificationControls.onSeekTo
        if (seekCallback != null) {
            seekCallback(positionMillis)
            AndroidPlaybackNotificationControls.positionMillis = positionMillis.coerceAtLeast(0L)
            updateMediaSessionPlaybackState()
        } else {
            seekServiceOwnedPlayback(positionMillis)
        }
    }

    private fun pauseServiceOwnedPlayback(reason: String) {
        servicePlaybackRuntimeController.pause(reason)
    }

    private fun pausePlaybackForRouteDisconnect(reason: String) {
        cancelPendingServiceSelection()
        if (!AndroidPlaybackNotificationControls.isPlaying) return
        Log.i("NaviampAutoCommand", "Pausing playback after route disconnect: $reason")
        if (servicePlaybackRuntimeController.ownsPlayback()) {
            pauseServiceOwnedPlayback(reason)
        } else {
            AndroidPlaybackNotificationControls.onPlayPause?.invoke()
                ?: pauseServiceOwnedPlayback(reason)
        }
        AndroidPlaybackNotificationControls.isPlaying = false
        updateMediaSessionPlaybackState()
        refreshNotification(null)
    }

    private fun stopServiceOwnedPlayback(reason: String) {
        servicePlaybackRuntimeController.stop(reason)
    }

    private fun stopPlaybackForUserRequest(reason: String) {
        cancelPendingServiceSelection()
        if (servicePlaybackRuntimeController.ownsPlayback()) {
            stopServiceOwnedPlayback(reason)
        } else {
            servicePlaybackRuntimeController.stopForUserRequest(reason)
        }
    }

    private fun stopPlaybackAndService(reason: String) {
        stopPlaybackForUserRequest(reason)
        stopForeground(STOP_FOREGROUND_REMOVE)
        mediaSessionController.setActive(false)
        stopSelf()
    }

    private fun seekServiceOwnedPlayback(positionMillis: Long) {
        servicePlaybackRuntimeController.seek(positionMillis)
    }

    private fun playSavedSessionAdjacent(delta: Int) {
        servicePlaybackRuntimeController.playSavedSessionAdjacent(delta)
    }

    private fun syncAutoQueue(queue: PlaybackQueue) {
        val preservesPreparedNext =
            autoQueueController.preparedNextIndex == queue.currentIndex &&
                autoQueueController.queue.tracks.map { it.id } == queue.tracks.map { it.id }
        autoQueueController.replaceQueue(
            queue = queue,
            clearPreparedNext = !preservesPreparedNext,
        )
        currentAutoQueue = autoQueueController.queue.tracks
        currentAutoQueueIndex = autoQueueController.queue.currentIndex
    }

    private fun serviceRepeatModeForQueue(): RepeatMode =
        when (serviceRepeatMode) {
            ServiceRepeatMode.Off -> RepeatMode.Off
            ServiceRepeatMode.All -> RepeatMode.Queue
            ServiceRepeatMode.One -> RepeatMode.Track
        }

    private fun serviceRepeatModeFromQueue(mode: RepeatMode): ServiceRepeatMode =
        when (mode) {
            RepeatMode.Off -> ServiceRepeatMode.Off
            RepeatMode.Queue -> ServiceRepeatMode.All
            RepeatMode.Track -> ServiceRepeatMode.One
        }

    private fun playServiceOwnedAdjacent(delta: Int): Boolean =
        servicePlaybackRuntimeController.playServiceOwnedAdjacent(delta)

    private fun playSavedSession(existingSession: PlaybackSessionSettings? = null) {
        servicePlaybackRuntimeController.playSavedSession(existingSession)
    }

    private fun cancelPendingServiceSelection() {
        serviceSelectionJobs?.cancel()
    }

    private fun launchServiceSelection(block: suspend () -> Unit) {
        val jobs = serviceSelectionJobs ?: AndroidAutoSelectionJobs(
            AndroidPlaybackRuntime.get(applicationContext).scope,
        ).also { serviceSelectionJobs = it }
        jobs.launch(block)
    }

    private fun handleServicePlayMediaId(mediaId: String): Boolean {
        cancelPendingServiceSelection()
        return autoSelectionController.playMediaId(mediaId)
    }

    private fun handleServicePlaySearch(query: String): Boolean {
        cancelPendingServiceSelection()
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank()) {
            Log.w("NaviampAutoCommand", "Ignoring blank Auto voice search")
            return false
        }
        val storage = serviceStorage
        val source = storage.latestNavidromeSource()
        if (source == null) {
            Log.w("NaviampAutoCommand", "Auto voice search has no saved provider query=$trimmedQuery")
            return false
        }
        val voiceQuery = MediaVoiceQuery.parse(trimmedQuery)
        if (voiceQuery.isDownloadedMusic) {
            return playServiceDownloadedMusicSearch(storage, source.id, trimmedQuery)
        }
        if (voiceQuery.isLibraryRadio) {
            return playServiceLibraryRadioSearch(storage, storage, source.id, trimmedQuery)
        }
        if (voiceQuery.isPlaylist) {
            return playServicePlaylistVoiceSearch(storage, source.id, voiceQuery.playlistTarget, trimmedQuery)
        }
        if (voiceQuery.isInternetRadioStation) {
            return playServiceInternetRadioVoiceSearch(storage, source.id, voiceQuery.stationTarget, trimmedQuery)
        }
        val radioQuery = voiceQuery.radioTarget
        if (radioQuery != null) {
            if (playServiceArtistRadioSearch(storage, storage, storage, source.id, radioQuery)) return true
            if (playServiceGenreRadioSearch(storage, storage, source.id, radioQuery)) return true
            Log.w("NaviampAutoCommand", "No Auto radio match for query=$trimmedQuery normalized=$radioQuery")
            return false
        }
        val provider = NavidromeProvider(source.toNavidromeConnection())
        launchServiceSelection {
            runCatching {
                withContext(Dispatchers.IO) {
                    val results = provider.search(trimmedQuery, AndroidAutoBrowseLimit)
                    val track = results.tracks.firstOrNull()
                    val artist = results.artists.firstOrNull { it.name.equals(trimmedQuery, ignoreCase = true) }
                        ?: results.artists.firstOrNull()
                    val album = results.albums.firstOrNull { it.title.equals(trimmedQuery, ignoreCase = true) }
                        ?: results.albums.firstOrNull()
                    when {
                        track != null -> {
                            Log.i("NaviampAutoCommand", "Auto voice search matched track=${track.title}")
                            listOf(track)
                        }
                        album != null -> provider.album(album.id).tracks
                        artist != null -> loadServiceArtistTracks(
                            libraryIndexRepository = storage,
                            providerResponseCacheRepository = storage,
                            sourceId = source.id,
                            provider = provider,
                            artistId = artist.id.value,
                            artistName = artist.name,
                        )
                        else -> emptyList()
                    }
                }
            }
                .onSuccess { tracks ->
                    if (tracks.isEmpty()) {
                        Log.w("NaviampAutoCommand", "No Auto voice search match query=$trimmedQuery")
                    } else {
                        playServiceTrackQueue(storage, source.id, tracks, 0)
                    }
                }
                .onFailure { error -> Log.w("NaviampAutoCommand", "Auto voice search failed query=$trimmedQuery", error) }
        }
        return true
    }

    private fun playServiceVoiceAlbumMatch(
        storage: AndroidStorageDependencies,
        source: SavedMediaSource,
        album: Album,
    ) {
        val provider = NavidromeProvider(source.toNavidromeConnection())
        launchServiceSelection {
            runCatching {
                loadServiceAlbumTracks(
                    libraryIndexRepository = storage,
                    providerResponseCacheRepository = storage,
                    sourceId = source.id,
                    provider = provider,
                    albumId = album.id.value,
                    albumTitle = album.title,
                    albumArtist = album.artistName,
                )
            }.onSuccess { tracks ->
                if (tracks.isNotEmpty()) {
                    Log.i("NaviampAutoCommand", "Auto voice search matched album=${album.title}")
                    playServiceTrackQueue(storage, source.id, tracks, 0)
                } else {
                    Log.w("NaviampAutoCommand", "Auto voice album match had no tracks album=${album.title}")
                    AndroidPlaybackNotificationControls.isPlaying = false
                    updateMediaSessionPlaybackState()
                }
            }.onFailure { error ->
                Log.w("NaviampAutoCommand", "Could not start Auto voice album=${album.title}", error)
                AndroidPlaybackNotificationControls.isPlaying = false
                updateMediaSessionPlaybackState()
            }
        }
    }

    private fun playServiceVoiceArtistMatch(
        storage: AndroidStorageDependencies,
        source: SavedMediaSource,
        artist: Artist,
    ) {
        val provider = NavidromeProvider(source.toNavidromeConnection())
        launchServiceSelection {
            runCatching {
                loadServiceArtistTracks(
                    libraryIndexRepository = storage,
                    providerResponseCacheRepository = storage,
                    sourceId = source.id,
                    provider = provider,
                    artistId = artist.id.value,
                    artistName = artist.name,
                )
            }.onSuccess { tracks ->
                if (tracks.isNotEmpty()) {
                    Log.i("NaviampAutoCommand", "Auto voice search matched artist=${artist.name}")
                    playServiceTrackQueue(storage, source.id, tracks, 0)
                } else {
                    Log.w("NaviampAutoCommand", "Auto voice artist match had no tracks artist=${artist.name}")
                    AndroidPlaybackNotificationControls.isPlaying = false
                    updateMediaSessionPlaybackState()
                }
            }.onFailure { error ->
                Log.w("NaviampAutoCommand", "Could not start Auto voice artist=${artist.name}", error)
                AndroidPlaybackNotificationControls.isPlaying = false
                updateMediaSessionPlaybackState()
            }
        }
    }

    private fun playServiceDownloadedMusicSearch(
        storage: AndroidStorageDependencies,
        sourceId: String,
        originalQuery: String,
    ): Boolean {
        val downloads = storage.downloadedTracks(sourceId)
            .filter { it.file.exists() }
            .take(AndroidAutoBrowseLimit)
            .map { it.track }
        if (downloads.isEmpty()) {
            Log.w("NaviampAutoCommand", "Auto voice downloaded music had no local downloads query=$originalQuery")
            return false
        }
        Log.i("NaviampAutoCommand", "Auto voice playing downloaded music count=${downloads.size}")
        playServiceTrackQueue(storage, sourceId, downloads, currentIndex = 0)
        return true
    }

    private fun playServiceLibraryRadioSearch(
        mediaSourceRepository: MediaSourceRepository,
        playbackSessionRepository: PlaybackSessionRepository,
        sourceId: String,
        originalQuery: String,
    ): Boolean {
        val source = mediaSourceRepository.latestMediaSource()
        if (source == null) {
            Log.w("NaviampAutoCommand", "Auto voice Library Radio has no provider query=$originalQuery")
            return false
        }
        val provider = NavidromeProvider(source.toNavidromeConnection())
        val recent = RecentRadioStream(
            id = AndroidAutoPlaybackControls.MediaIdRadioLibrary,
            label = "Library Radio",
            kind = RecentRadioKind.Library,
        )
        launchServiceSelection {
            val radioService = RadioService(
                provider = provider,
                tuning = AndroidSettingsStore(applicationContext).loadPlaybackSettings().radioTuning,
            )
            runCatching { withContext(Dispatchers.IO) { radioService.libraryRadio() } }
                .onSuccess { tracks ->
                    if (tracks.isEmpty()) {
                        Log.w("NaviampAutoCommand", "Auto voice Library Radio returned no tracks query=$originalQuery")
                        return@onSuccess
                    }
                    Log.i("NaviampAutoCommand", "Auto voice playing Library Radio count=${tracks.size}")
                    rememberRecentRadioStream(recent.withRadioCoverArtIds(tracks))
                    playServiceTrackQueue(playbackSessionRepository, sourceId, tracks, currentIndex = 0)
                }
                .onFailure { error ->
                    Log.w("NaviampAutoCommand", "Could not start Auto voice Library Radio query=$originalQuery", error)
                    AndroidPlaybackNotificationControls.isPlaying = false
                    updateMediaSessionPlaybackState()
                }
        }
        return true
    }

    private fun playServicePlaylistVoiceSearch(
        storage: AndroidStorageDependencies,
        sourceId: String,
        playlistQuery: String,
        originalQuery: String,
    ): Boolean {
        if (playlistQuery.isBlank()) {
            Log.w("NaviampAutoCommand", "Auto voice playlist search had no playlist name query=$originalQuery")
            return false
        }
        val source = storage.latestNavidromeSource()
        if (source == null) {
            Log.w("NaviampAutoCommand", "Auto voice playlist search has no provider query=$originalQuery")
            return false
        }
        val provider = NavidromeProvider(source.toNavidromeConnection())
        launchServiceSelection {
            runCatching {
                withContext(Dispatchers.IO) {
                    val responseService = providerResponseService(storage)
                    val playlist = responseService.playlists(provider, AndroidAutoBrowseLimit)
                        .bestVoiceNameMatch(playlistQuery) { it.name }
                    playlist to playlist?.let { responseService.playlistTracks(provider, it.id) }.orEmpty()
                }
            }.onSuccess { (playlist, tracks) ->
                if (playlist == null) {
                    Log.w("NaviampAutoCommand", "No Auto voice playlist match query=$originalQuery normalized=$playlistQuery")
                    return@onSuccess
                }
                if (tracks.isEmpty()) {
                    Log.w("NaviampAutoCommand", "Auto voice playlist matched empty playlist=${playlist.name}")
                    return@onSuccess
                }
                Log.i("NaviampAutoCommand", "Auto voice playing playlist=${playlist.name} count=${tracks.size}")
                playServiceTrackQueue(storage, sourceId, tracks, currentIndex = 0)
            }.onFailure { error ->
                Log.w("NaviampAutoCommand", "Could not start Auto voice playlist query=$originalQuery", error)
                AndroidPlaybackNotificationControls.isPlaying = false
                updateMediaSessionPlaybackState()
            }
        }
        return true
    }

    private fun playServiceInternetRadioVoiceSearch(
        storage: AndroidStorageDependencies,
        sourceId: String,
        stationQuery: String,
        originalQuery: String,
    ): Boolean {
        if (stationQuery.isBlank()) {
            Log.w("NaviampAutoCommand", "Auto voice station search had no station name query=$originalQuery")
            return false
        }
        val source = storage.latestNavidromeSource()
        if (source == null) {
            Log.w("NaviampAutoCommand", "Auto voice station search has no provider query=$originalQuery")
            return false
        }
        val provider = NavidromeProvider(source.toNavidromeConnection())
        launchServiceSelection {
            runCatching {
                withContext(Dispatchers.IO) {
                    providerResponseService(storage)
                        .internetRadioStations(provider)
                        .bestVoiceNameMatch(stationQuery) { it.name }
                }
            }.onSuccess { station ->
                if (station == null) {
                    Log.w("NaviampAutoCommand", "No Auto voice station match query=$originalQuery normalized=$stationQuery")
                    return@onSuccess
                }
                Log.i("NaviampAutoCommand", "Auto voice playing internet radio station=${station.name}")
                playServiceInternetRadioStation(storage, storage, sourceId, station)
            }.onFailure { error ->
                Log.w("NaviampAutoCommand", "Could not start Auto voice station query=$originalQuery", error)
                AndroidPlaybackNotificationControls.isPlaying = false
                updateMediaSessionPlaybackState()
            }
        }
        return true
    }

    private fun playServiceArtistRadioSearch(
        libraryIndexRepository: LocalLibraryIndexRepository,
        mediaSourceRepository: MediaSourceRepository,
        playbackSessionRepository: PlaybackSessionRepository,
        sourceId: String,
        query: String,
    ): Boolean {
        val source = mediaSourceRepository.latestMediaSource() ?: return false
        val provider = NavidromeProvider(source.toNavidromeConnection())
        launchServiceSelection {
            runCatching {
                withContext(Dispatchers.IO) {
                    val results = provider.search(query, AndroidAutoBrowseLimit)
                    val artist = results.artists.firstOrNull { it.name.equals(query, ignoreCase = true) }
                        ?: results.artists.firstOrNull()
                    val tracks = if (artist != null) {
                        RadioService(
                            provider = provider,
                            tuning = AndroidSettingsStore(applicationContext).loadPlaybackSettings().radioTuning,
                        ).artistRadio(artist.id)
                    } else {
                        provider.randomSongs(genre = query)
                    }
                    artist to tracks
                }
            }
                .onSuccess { (artist, tracks) ->
                    val recent = if (artist != null) {
                        RecentRadioStream(
                            id = "artist:${artist.id.value}",
                            label = "${artist.name} Radio",
                            kind = RecentRadioKind.Artist,
                            artist = app.naviamp.domain.settings.SavedArtist.fromArtist(artist),
                        )
                    } else {
                        RecentRadioStream(
                            id = "genre:${query.lowercase()}",
                            label = "${query.replaceFirstChar { it.titlecase() }} Radio",
                            kind = RecentRadioKind.Genre,
                            genre = query,
                        )
                    }
                    rememberRecentRadioStream(recent.withRadioCoverArtIds(tracks))
                    playServiceTrackQueue(playbackSessionRepository, sourceId, tracks, currentIndex = 0)
                }
                .onFailure { error ->
                    Log.w("NaviampAutoCommand", "Could not start radio search=$query", error)
                    AndroidPlaybackNotificationControls.isPlaying = false
                    updateMediaSessionPlaybackState()
                }
        }
        return true
    }

    private fun playServiceGenreRadioSearch(
        mediaSourceRepository: MediaSourceRepository,
        playbackSessionRepository: PlaybackSessionRepository,
        sourceId: String,
        query: String,
    ): Boolean {
        val source = mediaSourceRepository.latestMediaSource() ?: return false
        val provider = NavidromeProvider(source.toNavidromeConnection())
        val recent = RecentRadioStream(
            id = "genre:${query.lowercase()}",
            label = "${query.replaceFirstChar { it.titlecase() }} Radio",
            kind = RecentRadioKind.Genre,
            genre = query,
        )
        launchServiceSelection {
            runCatching { withContext(Dispatchers.IO) { provider.randomSongs(genre = query) } }
                .onSuccess { tracks ->
                    if (tracks.isEmpty()) {
                        Log.i("NaviampAutoCommand", "No genre radio tracks for query=$query")
                        return@onSuccess
                    }
                    rememberRecentRadioStream(recent.withRadioCoverArtIds(tracks))
                    playServiceTrackQueue(playbackSessionRepository, sourceId, tracks, currentIndex = 0)
                }
                .onFailure { error ->
                    Log.w("NaviampAutoCommand", "Could not start genre radio search=$query", error)
                    AndroidPlaybackNotificationControls.isPlaying = false
                    updateMediaSessionPlaybackState()
                }
        }
        return true
    }

    private fun serviceQueueForLibraryTrack(
        libraryIndexRepository: LocalLibraryIndexRepository,
        sourceId: String,
        track: Track,
    ): List<Track> =
        track.albumId?.let { libraryIndexRepository.libraryTracksForAlbum(sourceId, it, 200) }
            ?.takeIf { tracks -> tracks.any { it.id == track.id } }
            ?: track.albumTitle?.let { libraryIndexRepository.libraryTracksForAlbumTitle(sourceId, it, track.artistName, 200) }
                ?.takeIf { tracks -> tracks.any { it.id == track.id } }
            ?: track.artistId?.let { libraryIndexRepository.libraryTracksForArtist(sourceId, it, 200) }
                ?.takeIf { tracks -> tracks.any { it.id == track.id } }
            ?: libraryIndexRepository.libraryTracksForArtistName(sourceId, track.artistName, 200)
                .takeIf { tracks -> tracks.any { it.id == track.id } }
            ?: listOf(track)

    private fun playServiceTrackQueue(
        playbackSessionRepository: PlaybackSessionRepository,
        sourceId: String,
        tracks: List<Track>,
        currentIndex: Int,
    ) {
        if (tracks.isEmpty()) return
        syncAutoQueue(PlaybackQueue(tracks = tracks, currentIndex = currentIndex.coerceIn(tracks.indices)))
        val session = playbackSessionFromQueue(autoQueueController.queue) ?: return
        playbackSessionRepository.savePlaybackSession(sourceId = sourceId, session = session)
        playSavedSession(session)
    }

    private fun replaceServiceTrackQueueWithoutRestarting(
        playbackSessionRepository: PlaybackSessionRepository,
        sourceId: String,
        tracks: List<Track>,
        currentIndex: Int,
    ) {
        if (tracks.isEmpty()) return
        syncAutoQueue(PlaybackQueue(tracks = tracks, currentIndex = currentIndex.coerceIn(tracks.indices)))
        val session = playbackSessionFromQueue(autoQueueController.queue) ?: return
        playbackSessionRepository.savePlaybackSession(sourceId = sourceId, session = session)
        mediaSessionController.publishQueue()
        updateMediaSessionPlaybackState()
    }

    private fun playServiceAutoQueueItem(index: Int) {
        cancelPendingServiceSelection()
        val queue = currentAutoQueue
        if (index !in queue.indices) return
        val storage = serviceStorage
        val sourceId = storage.latestNavidromeSource()?.id ?: return
        playServiceTrackQueue(storage, sourceId, queue, index)
    }

    private suspend fun loadServiceAlbumTracks(
        libraryIndexRepository: LocalLibraryIndexRepository,
        providerResponseCacheRepository: ProviderResponseCacheRepository,
        sourceId: String,
        provider: NavidromeProvider,
        albumId: String,
        albumTitle: String?,
        albumArtist: String?,
    ): List<Track> =
        runCatching {
            withContext(Dispatchers.IO) { provider.album(AlbumId(albumId)).tracks }
        }.getOrDefault(emptyList())

    private suspend fun loadServicePlaylistTracks(
        providerResponseCacheRepository: ProviderResponseCacheRepository,
        provider: NavidromeProvider,
        playlistId: String,
    ): List<Track> {
        providerResponseService(providerResponseCacheRepository).invalidatePlaylistTracks(provider, playlistId)
        return provider.playlistTracks(playlistId).also { tracks ->
            Log.i("NaviampAutoCommand", "Auto playlist service tracks playlist=$playlistId count=${tracks.size}")
        }
    }

    private suspend fun loadServiceArtistTracks(
        libraryIndexRepository: LocalLibraryIndexRepository,
        providerResponseCacheRepository: ProviderResponseCacheRepository,
        sourceId: String,
        provider: NavidromeProvider,
        artistId: String,
        artistName: String?,
    ): List<Track> =
        artistId.takeIf { it.isNotBlank() }?.let { id ->
            runCatching {
                withContext(Dispatchers.IO) {
                    val artist = Artist(ArtistId(id), artistName.orEmpty().ifBlank { "Artist" })
                    val popular = provider.popularTracks(artist, AndroidAutoBrowseLimit)
                    popular.candidates.mapNotNull { candidate ->
                        popular.matchedTracksBySourceTrackId[candidate.sourceTrackId]
                    }.ifEmpty {
                        provider.artist(artist.id).albums
                            .take(AndroidAutoArtistAlbumFallbackLimit)
                            .flatMap { album -> provider.album(album.id).tracks }
                            .take(AndroidAutoBrowseLimit)
                    }
                }
            }.onSuccess { tracks ->
                Log.i("NaviampAutoCommand", "Auto artist provider tracks artist=$id count=${tracks.size}")
            }.onFailure { error ->
                Log.w("NaviampAutoCommand", "Auto artist provider failed artist=$id", error)
            }.getOrDefault(emptyList())
        }.orEmpty()

    private fun playServiceTrackRadio(
        playbackSessionRepository: PlaybackSessionRepository,
        sourceId: String,
        provider: NavidromeProvider,
        seedTrack: Track,
        restartPlayback: Boolean = true,
    ) {
        val recent = RecentRadioStream(
            id = "track:${seedTrack.id.value}",
            label = "${seedTrack.title} Radio",
            kind = RecentRadioKind.Track,
            track = SavedTrack.fromTrack(seedTrack),
        )
        rememberRecentRadioStream(recent.withRadioCoverArtIds(listOf(seedTrack)))
        launchServiceSelection {
            val playbackSettings = AndroidSettingsStore(applicationContext).loadPlaybackSettings()
            val preferSonicSimilarity = playbackSettings.sonicSimilarityEnabled
            runCatching {
                withContext(Dispatchers.IO) {
                    RadioService(
                        provider = provider,
                        tuning = playbackSettings.radioTuning,
                    ).trackRadio(seedTrack, preferSonicSimilarity)
                }
            }
                .onSuccess { tracks ->
                    val queueTracks = (listOf(seedTrack) + tracks).distinctBy { it.id }
                    rememberRecentRadioStream(recent.withRadioCoverArtIds(queueTracks))
                    if (restartPlayback) {
                        playServiceTrackQueue(
                            playbackSessionRepository = playbackSessionRepository,
                            sourceId = sourceId,
                            tracks = queueTracks,
                            currentIndex = 0,
                        )
                    } else {
                        replaceServiceTrackQueueWithoutRestarting(
                            playbackSessionRepository = playbackSessionRepository,
                            sourceId = sourceId,
                            tracks = queueTracks,
                            currentIndex = 0,
                        )
                    }
                }
                .onFailure { error ->
                    Log.w("NaviampAutoCommand", "Could not start track radio=${seedTrack.id.value}", error)
                    if (restartPlayback) {
                        playServiceTrackQueue(playbackSessionRepository, sourceId, listOf(seedTrack), currentIndex = 0)
                    } else {
                        replaceServiceTrackQueueWithoutRestarting(playbackSessionRepository, sourceId, listOf(seedTrack), currentIndex = 0)
                    }
                }
        }
    }

    private fun playServiceRecentRadioStream(
        mediaSourceRepository: MediaSourceRepository,
        playbackSessionRepository: PlaybackSessionRepository,
        sourceId: String,
        stream: RecentRadioStream,
    ) {
        val source = mediaSourceRepository.latestMediaSource() ?: return
        val provider = NavidromeProvider(source.toNavidromeConnection())
        launchServiceSelection {
            val radioService = RadioService(
                provider = provider,
                tuning = AndroidSettingsStore(applicationContext).loadPlaybackSettings().radioTuning,
            )
            runCatching {
                withContext(Dispatchers.IO) {
                    when (stream.kind) {
                        RecentRadioKind.Library -> radioService.libraryRadio()
                        RecentRadioKind.Artist -> stream.artist?.let { radioService.artistRadio(ArtistId(it.id)) }.orEmpty()
                        RecentRadioKind.Album -> stream.album?.let { radioService.albumRadio(AlbumId(it.id)) }.orEmpty()
                        RecentRadioKind.Track -> stream.track?.toTrack()
                            ?.let { radioService.trackRadio(it, AndroidSettingsStore(applicationContext).loadPlaybackSettings().sonicSimilarityEnabled) }
                            .orEmpty()
                        RecentRadioKind.Genre -> stream.genre?.let { radioService.genreRadio(it) }.orEmpty()
                        RecentRadioKind.Decade -> radioService.decadeRadio(
                            fromYear = stream.fromYear ?: 0,
                            toYear = stream.toYear ?: 9999,
                        )
                        RecentRadioKind.RandomAlbum -> radioService.libraryRadio()
                    }
                }
            }.onSuccess { tracks ->
                rememberRecentRadioStream(stream.withRadioCoverArtIds(tracks))
                playServiceTrackQueue(playbackSessionRepository, sourceId, tracks, currentIndex = 0)
            }.onFailure { error ->
                Log.w("NaviampAutoCommand", "Could not start recent radio=${stream.label}", error)
                AndroidPlaybackNotificationControls.isPlaying = false
                updateMediaSessionPlaybackState()
            }
        }
    }

    private fun playServiceInternetRadioStation(
        mediaSourceRepository: MediaSourceRepository,
        playbackSessionRepository: PlaybackSessionRepository,
        sourceId: String,
        station: InternetRadioStation,
    ) {
        val stationArtUrl = station.defaultRadioArtworkUrl()
            .takeIf { it.startsWith("http://") || it.startsWith("https://") }
        rememberRecentInternetRadioStation(station)
        playbackSessionRepository.savePlaybackSession(
            sourceId = sourceId,
            session = PlaybackSessionSettings.fromInternetRadioStation(station),
        )
        val runtime = AndroidPlaybackRuntime.get(applicationContext)
        runtime.playbackEngine.applyTlsSettings(mediaSourceRepository.latestMediaSource()?.toNavidromeConnection()?.tlsSettings ?: return)
        AndroidPlaybackNotificationControls.canFavorite = false
        AndroidPlaybackNotificationControls.isFavorite = false
        AndroidPlaybackNotificationControls.isPlaying = true
        AndroidPlaybackNotificationControls.positionMillis = 0L
        AndroidPlaybackNotificationControls.durationMillis = null
        servicePlaybackRuntimeController.markStarted()
        syncAutoQueue(PlaybackQueue())
        setCurrentMetadata(
            AndroidPlaybackNotificationMetadata(
                title = station.name,
                subtitle = "Internet radio",
                coverArtUrl = stationArtUrl,
            ),
        )
        stationArtUrl?.let { loadCoverArtAsync(it, currentMetadata) }
        updateMediaSession(currentMetadata, currentLargeIcon)
        launchServiceSelection {
            runCatching { resolveInternetRadioStreamUrl(station.streamUrl.trim()) }
                .onSuccess { streamUrl ->
                    runtime.playbackEngine.updateNotificationMetadata(
                        title = station.name,
                        subtitle = "Internet radio",
                        coverArtUrl = stationArtUrl,
                    )
                    runtime.playbackEngine.play(
                        scope = runtime.scope,
                        request = PlaybackRequest(streamUrl),
                        onStateChanged = { state ->
                            servicePlaybackRuntimeController.handlePlaybackStateChanged(state)
                        },
                        onProgressChanged = { progress ->
                            handleServicePlaybackProgress(
                                sourceId,
                                PlaybackSessionSettings.fromInternetRadioStation(station),
                                progress,
                            )
                        },
                        onMetadataChanged = { metadata ->
                            metadata.title?.takeIf { it.isNotBlank() }?.let { streamTitle ->
                                setCurrentMetadata(currentMetadata.copy(title = streamTitle, subtitle = station.name))
                                runtime.playbackEngine.updateNotificationMetadata(
                                    title = streamTitle,
                                    subtitle = station.name,
                                    coverArtUrl = stationArtUrl,
                                )
                                updateMediaSession(currentMetadata, currentLargeIcon)
                            }
                        },
                    )
                }
                .onFailure { error ->
                    Log.w("NaviampAutoCommand", "Could not start Auto internet radio=${station.name}", error)
                    AndroidPlaybackNotificationControls.isPlaying = false
                    updateMediaSessionPlaybackState()
                }
        }
    }

    private fun rememberRecentRadioStream(stream: RecentRadioStream) {
        recentRadioStreamController.remember(stream)
    }

    private fun rememberRecentInternetRadioStation(station: InternetRadioStation) {
        applyRememberInternetRadioStation(
            plan = planRememberInternetRadioStation(
                station = station,
                recentStations = emptyList(),
                recentSavedStations = serviceSettingsStore.loadRecentInternetRadioStations(),
            ),
            applier = InternetRadioRecentStationApplier(
                saveRecentStations = serviceSettingsStore::saveRecentInternetRadioStations,
            ),
        )
        markAndroidSettingsSyncChangedAndAutoExport(
            context = applicationContext,
            settingsStore = serviceSettingsStore,
            storage = serviceStorage,
        )
    }

    private fun handleServicePlaybackProgress(
        sourceId: String,
        session: PlaybackSessionSettings,
        progress: PlaybackProgress,
    ) {
        servicePlaybackRuntimeController.handlePlaybackProgress(
            sourceId = sourceId,
            session = session,
            progress = progress,
        )
    }

    private fun ensureMediaSession(): MediaSessionCompat = mediaSessionController.ensure()

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?,
    ): BrowserRoot? = mediaBrowserController.root(clientPackageName, clientUid)

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>,
    ) = mediaBrowserController.loadChildren(parentId, result)

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>,
        options: Bundle,
    ) = mediaBrowserController.loadChildren(parentId, result, options)

    override fun onSearch(
        query: String,
        extras: Bundle?,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>,
    ) = mediaBrowserController.search(query, extras, result)

    private fun updateMediaSession(metadata: AndroidPlaybackNotificationMetadata, largeIcon: Bitmap?) {
        mediaSessionController.update(metadata, largeIcon)
    }

    private fun toggleServiceShuffle() {
        serviceShuffleEnabled = !serviceShuffleEnabled
        if (serviceShuffleEnabled) {
            autoQueueController.replaceQueue(PlaybackQueue(currentAutoQueue, currentAutoQueueIndex))
            val shuffled = autoQueueController.toggleUpcomingShuffle(shuffledSnapshot = null)
            if (shuffled != null) {
                currentAutoQueue = shuffled.queue.tracks
                currentAutoQueueIndex = shuffled.queue.currentIndex
                mediaSessionController.invalidateQueue()
                val storage = serviceStorage
                val sourceId = storage.latestNavidromeSource()?.id
                val session = playbackSessionFromQueue(
                    queue = autoQueueController.queue,
                    positionSeconds = AndroidPlaybackNotificationControls.positionMillis?.let { it / 1_000.0 },
                )
                if (sourceId != null && session != null) {
                    storage.savePlaybackSession(sourceId = sourceId, session = session)
                }
            }
        }
        updateMediaSession(currentMetadata, currentLargeIcon)
    }

    private fun toggleServiceFavorite() {
        if (!AndroidPlaybackNotificationControls.canFavorite) return
        val nextFavorite = !AndroidPlaybackNotificationControls.isFavorite
        AndroidPlaybackNotificationControls.isFavorite =
            nextFavorite
        val phoneCallback = AndroidPlaybackNotificationControls.onToggleFavorite
        if (phoneCallback != null) {
            phoneCallback()
            refreshNotification(null)
            return
        }
        val storage = serviceStorage
        val source = storage.latestNavidromeSource()
        val track = currentAutoQueue.getOrNull(currentAutoQueueIndex)
        if (source != null && track != null) {
            val provider = NavidromeProvider(source.toNavidromeConnection())
            val updatedTrack = track.copy(favoritedAtIso8601 = if (nextFavorite) "local" else null)
            val updatedQueue = currentAutoQueue.toMutableList().also { queue ->
                queue[currentAutoQueueIndex] = updatedTrack
            }
            currentAutoQueue = updatedQueue
            autoQueueController.replaceQueue(PlaybackQueue(currentAutoQueue, currentAutoQueueIndex))
            playbackSessionFromQueue(
                queue = autoQueueController.queue,
                positionSeconds = AndroidPlaybackNotificationControls.positionMillis?.let { it / 1_000.0 },
            )?.let { session ->
                storage.savePlaybackSession(sourceId = source.id, session = session)
            }
            AndroidPlaybackRuntime.get(applicationContext).scope.launch {
                withContext(Dispatchers.IO) {
                    provider
                        .withAndroidPendingActions(source.id, serviceProviderActions)
                        .setTrackFavorite(track.id, nextFavorite)
                }
            }
        }
        refreshNotification(null)
    }

    private fun cycleServiceRepeatMode() {
        serviceRepeatMode = serviceRepeatModeFromQueue(nextRepeatMode(serviceRepeatModeForQueue()))
        updateMediaSessionPlaybackState()
    }

    private fun openAutoQueue() {
        Log.i("NaviampAutoCommand", "Opening Auto queue action size=${currentAutoQueue.size}")
        launchMainActivityForAutoMediaId(AndroidAutoPlaybackControls.MediaIdQueue)
    }

    private fun startServiceCurrentTrackRadio() {
        val storage = serviceStorage
        val source = storage.latestNavidromeSource()
        val track = currentAutoQueue.getOrNull(currentAutoQueueIndex)
        if (source == null || track == null) {
            Log.w("NaviampAutoCommand", "Cannot start track radio; source=${source?.id} track=${track?.id?.value}")
            return
        }
        Log.i("NaviampAutoCommand", "Starting Auto track radio for current track=${track.id.value}")
        playServiceTrackRadio(
            playbackSessionRepository = storage,
            sourceId = source.id,
            provider = NavidromeProvider(source.toNavidromeConnection()),
            seedTrack = track,
            restartPlayback = false,
        )
    }

    private fun updateMediaSessionPlaybackState() {
        mediaSessionController.updatePlaybackState()
    }

    private fun hydrateSavedPlaybackSession() {
        serviceSessionController.hydrateSavedPlaybackSession()
    }

    private fun restoredNowPlayingMetadata(): AndroidPlaybackNotificationMetadata? =
        serviceSessionController.restoredNowPlayingMetadata()

    private fun Intent?.toMetadata(): AndroidPlaybackNotificationMetadata {
        val nextCoverArtUrl = this?.getStringExtra(ExtraCoverArtUrl) ?: currentMetadata.coverArtUrl
        setCurrentMetadata(
            AndroidPlaybackNotificationMetadata(
                title = this?.getStringExtra(ExtraTitle) ?: currentMetadata.title,
                subtitle = this?.getStringExtra(ExtraSubtitle) ?: currentMetadata.subtitle,
                coverArtUrl = nextCoverArtUrl,
            ),
        )
        return currentMetadata
    }

    private fun setCurrentMetadata(metadata: AndroidPlaybackNotificationMetadata) {
        if (metadata.coverArtUrl != currentMetadata.coverArtUrl) {
            currentLargeIcon = null
        }
        currentMetadata = metadata
    }

    private fun ensureNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            ChannelId,
            "Playback",
            NotificationManager.IMPORTANCE_MIN,
        ).apply {
            description = "Keeps Naviamp playback alive in the background."
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val ChannelId = "naviamp_playback_media"
        private const val NotificationId = 1001
        private const val ActionStart = "app.naviamp.android.playback.START"
        private const val ActionStop = "app.naviamp.android.playback.STOP"
        private const val ActionPlayPause = "app.naviamp.android.playback.PLAY_PAUSE"
        private const val ActionPrevious = "app.naviamp.android.playback.PREVIOUS"
        private const val ActionNext = "app.naviamp.android.playback.NEXT"
        private const val ActionFavorite = "app.naviamp.android.playback.FAVORITE"
        private const val ActionQueue = "app.naviamp.android.playback.QUEUE"
        private const val ActionShuffle = "app.naviamp.android.playback.SHUFFLE"
        private const val ActionRepeat = "app.naviamp.android.playback.REPEAT"
        private const val ActionTrackRadio = "app.naviamp.android.playback.TRACK_RADIO"
        private const val ActionUpdate = "app.naviamp.android.playback.UPDATE"
        private const val ActionProgress = "app.naviamp.android.playback.PROGRESS"
        private const val AndroidAutoBrowseLimit = 50
        private const val ExtraTitle = "title"
        private const val ExtraSubtitle = "subtitle"
        private const val ExtraCoverArtUrl = "coverArtUrl"
        private const val ExtraFromEngine = "fromEngine"
        private const val ExtraPositionMillis = "positionMillis"
        private const val ExtraDurationMillis = "durationMillis"
        private const val ExtraCommandCapability = "commandCapability"
        private val CommandCapability = UUID.randomUUID().toString()
        private val PlayerNotificationColor = Color.rgb(82, 35, 31)
        private var currentMetadata = AndroidPlaybackNotificationMetadata()
        private var currentLargeIcon: Bitmap? = null
        private var currentAutoQueue: List<Track> = emptyList()
        private var currentAutoQueueIndex: Int = -1
        private var serviceShuffleEnabled = false
        private var serviceRepeatMode = ServiceRepeatMode.Off
        @Volatile
        private var serviceCreated = false

        fun start(context: Context, metadata: AndroidPlaybackNotificationMetadata) {
            val intent = Intent(context, AndroidPlaybackForegroundService::class.java)
                .setAction(ActionStart)
                .putExtra(ExtraCommandCapability, CommandCapability)
                .putExtra(ExtraTitle, metadata.title)
                .putExtra(ExtraSubtitle, metadata.subtitle)
                .putExtra(ExtraCoverArtUrl, metadata.coverArtUrl)
            runCatching {
                context.startForegroundService(intent)
            }.onFailure { error ->
                Log.w("NaviampAutoCommand", "Could not start playback foreground service", error)
            }
        }

        fun update(context: Context, metadata: AndroidPlaybackNotificationMetadata) {
            if (!serviceCreated) return
            val intent = Intent(context, AndroidPlaybackForegroundService::class.java)
                .setAction(ActionUpdate)
                .putExtra(ExtraCommandCapability, CommandCapability)
                .putExtra(ExtraTitle, metadata.title)
                .putExtra(ExtraSubtitle, metadata.subtitle)
                .putExtra(ExtraCoverArtUrl, metadata.coverArtUrl)
            runCatching {
                context.startService(intent)
            }.onFailure { error ->
                Log.w("NaviampAutoCommand", "Could not update playback foreground service", error)
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.startService(
                    Intent(context, AndroidPlaybackForegroundService::class.java)
                        .setAction(ActionStop)
                        .putExtra(ExtraCommandCapability, CommandCapability)
                        .putExtra(ExtraFromEngine, true),
                )
            }.onFailure { error ->
                Log.w("NaviampAutoCommand", "Could not stop playback foreground service", error)
            }
        }

        fun updateProgress(context: Context, positionMillis: Long?, durationMillis: Long?) {
            if (!serviceCreated) return
            runCatching {
                context.startService(
                    Intent(context, AndroidPlaybackForegroundService::class.java)
                        .setAction(ActionProgress)
                        .putExtra(ExtraCommandCapability, CommandCapability)
                        .putExtra(ExtraPositionMillis, positionMillis ?: -1L)
                        .putExtra(ExtraDurationMillis, durationMillis ?: -1L),
                )
            }.onFailure { error ->
                Log.w("NaviampAutoCommand", "Could not update playback foreground service progress", error)
            }
        }
    }
}

internal fun isAuthorizedPlaybackServiceCommand(
    suppliedCapability: String?,
    expectedCapability: String,
): Boolean {
    val supplied = suppliedCapability?.encodeToByteArray() ?: return false
    return MessageDigest.isEqual(supplied, expectedCapability.encodeToByteArray())
}

private fun isProtectedPlaybackServiceAction(action: String?): Boolean =
    action?.startsWith("app.naviamp.android.playback.") == true

internal fun Bitmap.dominantNotificationColor(): Int {
    var red = 0L
    var green = 0L
    var blue = 0L
    var count = 0L
    val stepX = (width / 24).coerceAtLeast(1)
    val stepY = (height / 24).coerceAtLeast(1)
    var y = 0
    while (y < height) {
        var x = 0
        while (x < width) {
            val pixel = getPixel(x, y)
            red += Color.red(pixel)
            green += Color.green(pixel)
            blue += Color.blue(pixel)
            count++
            x += stepX
        }
        y += stepY
    }
    if (count == 0L) return Color.rgb(82, 35, 31)
    return Color.rgb((red / count).toInt(), (green / count).toInt(), (blue / count).toInt())
}

private fun decodeSampledBitmap(bytes: ByteArray, maxSidePx: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxSidePx)
    }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}

private fun sampleSizeFor(width: Int, height: Int, maxSidePx: Int): Int {
    var sampleSize = 1
    val target = maxSidePx.coerceAtLeast(1)
    while ((width / sampleSize) > target || (height / sampleSize) > target) {
        sampleSize *= 2
    }
    return sampleSize
}

private const val NotificationCoverArtSidePx = 512
