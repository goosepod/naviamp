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
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.MediaSessionManager
import androidx.media.utils.MediaConstants
import app.naviamp.android.AndroidStorageDependencies
import app.naviamp.android.AndroidSettingsStore
import app.naviamp.android.AndroidPlaybackAudioAssets
import app.naviamp.android.R
import app.naviamp.android.MainActivity
import app.naviamp.android.markAndroidSettingsSyncChangedAndAutoExport
import app.naviamp.android.resolveInternetRadioStreamUrl
import app.naviamp.android.withAndroidPendingActions
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
import app.naviamp.domain.playback.nextRepeatMode
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode
import app.naviamp.domain.network.KtorSharedHttpClient
import app.naviamp.domain.radio.RadioService
import app.naviamp.domain.radio.InternetRadioRecentStationApplier
import app.naviamp.domain.radio.applyRememberInternetRadioStation
import app.naviamp.domain.radio.planRememberInternetRadioStation
import app.naviamp.domain.radio.recentRadioStreamsWith
import app.naviamp.domain.radio.withRadioCoverArtIds
import app.naviamp.domain.settings.PlaybackSessionSettings
import app.naviamp.domain.settings.RecentRadioKind
import app.naviamp.domain.settings.RecentRadioStream
import app.naviamp.domain.settings.SavedTrack
import app.naviamp.domain.settings.playbackSessionFromQueue
import app.naviamp.domain.smartplaylist.SmartPlaylistTemplates
import app.naviamp.provider.navidrome.NavidromeProvider
import app.naviamp.provider.navidrome.toNavidromeConnection
import app.naviamp.ui.defaultRadioArtworkUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.concurrent.thread

private const val VoiceArtistScanLimit = 5_000L
private const val AndroidAutoTemplateTrackScanLimit = 5_000L
private const val AndroidAutoSmartTemplateTrackLimit = 100
class AndroidPlaybackForegroundService : MediaBrowserServiceCompat() {
    private var mediaSession: MediaSessionCompat? = null
    private var browserSessionTokenSet = false
    private var serviceStorageInstance: AndroidStorageDependencies? = null
    private var loadingNotificationCoverArtUrl: String? = null
    private var serviceSelectionJobs: AndroidAutoSelectionJobs? = null
    private val notificationArtHttpClient = KtorSharedHttpClient()
    private val serviceStorage: AndroidStorageDependencies
        get() = serviceStorageInstance ?: AndroidStorageDependencies(applicationContext).also { serviceStorageInstance = it }
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
    private val serviceSessionController: AndroidPlaybackServiceSessionController by lazy {
        AndroidPlaybackServiceSessionController(
            storage = { serviceStorage },
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
        serviceCreated = true
        ensureNotificationChannel()
        ensureMediaSession()
        hydrateSavedPlaybackSession()
        registerReceiver(noisyAudioReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
    }

    override fun onDestroy() {
        serviceSelectionJobs?.cancel()
        serviceSelectionJobs = null
        pausePlaybackForRouteDisconnect("service destroyed")
        runCatching { unregisterReceiver(noisyAudioReceiver) }
        mediaSession?.setCallback(null)
        mediaSession?.release()
        mediaSession = null
        browserSessionTokenSet = false
        resetMediaSessionPublishState()
        serviceCreated = false
        super.onDestroy()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (servicePlaybackRuntimeController.ownsPlayback()) {
            Log.i("NaviampAutoCommand", "Android Auto browser unbound while service owns playback; keeping playback alive")
            updateMediaSessionPlaybackState()
            return super.onUnbind(intent)
        }
        pausePlaybackForRouteDisconnect("Android Auto browser unbound")
        return super.onUnbind(intent)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (servicePlaybackRuntimeController.ownsPlayback()) {
            Log.i("NaviampAutoCommand", "Phone task removed while service owns playback; keeping Auto session alive")
            updateMediaSessionPlaybackState()
            super.onTaskRemoved(rootIntent)
            return
        }
        stopPlaybackAndService("task removed")
        super.onTaskRemoved(rootIntent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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
                mediaSession?.isActive = false
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
                if (!startForegroundSafely(metadata)) {
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
        val activityIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.ExtraOpenNowPlaying, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val playPauseAction = if (AndroidPlaybackNotificationControls.isPlaying) {
            notificationAction(ActionPlayPause, android.R.drawable.ic_media_pause, "Pause")
        } else {
            notificationAction(ActionPlayPause, android.R.drawable.ic_media_play, "Play")
        }
        val favoriteAction = if (AndroidPlaybackNotificationControls.isFavorite) {
            notificationAction(ActionFavorite, R.drawable.ic_favorite_filled_24, "Unfavorite")
        } else {
            notificationAction(ActionFavorite, R.drawable.ic_favorite_24, "Favorite")
        }
        val notificationColor = coverArt?.dominantColor() ?: PlayerNotificationColor
        updateMediaSession(metadata, coverArt)
        return Notification.Builder(this, ChannelId)
            .setContentTitle(metadata.title?.takeIf { it.isNotBlank() } ?: "Naviamp is playing")
            .setContentText(metadata.subtitle?.takeIf { it.isNotBlank() } ?: "Audio playback is active")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setLargeIcon(coverArt)
            .setContentIntent(activityIntent)
            .setDeleteIntent(stopPendingIntent())
            .addAction(notificationAction(ActionPrevious, android.R.drawable.ic_media_previous, "Previous"))
            .addAction(playPauseAction)
            .addAction(notificationAction(ActionNext, android.R.drawable.ic_media_next, "Next"))
            .addAction(favoriteAction)
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(
                        ensureMediaSession().sessionToken.token as android.media.session.MediaSession.Token,
                    )
                    .setShowActionsInCompactView(0, 1, 2),
            )
            .setColor(notificationColor)
            .setColorized(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()
    }

    private fun notificationAction(action: String, icon: Int, title: String): Notification.Action =
        Notification.Action.Builder(
            Icon.createWithResource(this, icon),
            title,
            PendingIntent.getService(
                this,
                action.hashCode(),
                Intent(this, AndroidPlaybackForegroundService::class.java)
                    .setAction(action)
                    .putExtra(ExtraTitle, currentMetadata.title)
                    .putExtra(ExtraSubtitle, currentMetadata.subtitle)
                    .putExtra(ExtraCoverArtUrl, currentMetadata.coverArtUrl),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        ).build()

    private fun stopPendingIntent(): PendingIntent =
        PendingIntent.getService(
            this,
            ActionStop.hashCode(),
            Intent(this, AndroidPlaybackForegroundService::class.java)
                .setAction(ActionStop)
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
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NotificationId, buildNotification(metadata, largeIcon = bitmap))
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
        mediaSession?.isActive = false
        stopSelf()
    }

    private fun seekServiceOwnedPlayback(positionMillis: Long) {
        servicePlaybackRuntimeController.seek(positionMillis)
    }

    private fun playSavedSessionAdjacent(delta: Int) {
        servicePlaybackRuntimeController.playSavedSessionAdjacent(delta)
    }

    private fun syncAutoQueue(queue: PlaybackQueue) {
        autoQueueController.replaceQueue(queue)
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
        val storage = serviceStorage
        val source = storage.latestNavidromeSource() ?: return false
        val sourceId = source.id
        return when {
            mediaId == AndroidAutoPlaybackControls.MediaIdNowPlaying -> {
                handleServiceAutoPlayPause()
                true
            }
            mediaId.startsWith(AndroidAutoPlaybackControls.MediaIdQueueTrackPrefix) -> {
                val index = Uri.decode(mediaId.removePrefix(AndroidAutoPlaybackControls.MediaIdQueueTrackPrefix)).toIntOrNull()
                    ?: return false
                playServiceAutoQueueItem(index)
                true
            }
            mediaId == AndroidAutoPlaybackControls.MediaIdRadioLibrary -> {
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
                            rememberRecentRadioStream(recent.withRadioCoverArtIds(tracks))
                            playServiceTrackQueue(storage, sourceId, tracks, currentIndex = 0)
                        }
                        .onFailure { error ->
                            Log.w("NaviampAutoCommand", "Could not start Auto Library Radio", error)
                            AndroidPlaybackNotificationControls.isPlaying = false
                            updateMediaSessionPlaybackState()
                        }
                }
                true
            }
            mediaId.startsWith(AndroidAutoPlaybackControls.MediaIdRadioDjPrefix) -> {
                val djId = Uri.decode(mediaId.removePrefix(AndroidAutoPlaybackControls.MediaIdRadioDjPrefix))
                val dj = storage.radioDjPresets().firstOrNull { it.id == djId } ?: return false
                val provider = NavidromeProvider(source.toNavidromeConnection())
                launchServiceSelection {
                    val radioService = RadioService(
                        provider = provider,
                        tuning = dj.tuning,
                    )
                    val recent = RecentRadioStream(
                        id = "dj:${dj.id}",
                        label = dj.name,
                        kind = RecentRadioKind.Library,
                    )
                    runCatching { withContext(Dispatchers.IO) { radioService.libraryRadio() } }
                        .onSuccess { tracks ->
                            rememberRecentRadioStream(recent.withRadioCoverArtIds(tracks))
                            playServiceTrackQueue(storage, sourceId, tracks, currentIndex = 0)
                        }
                        .onFailure { error ->
                            Log.w("NaviampAutoCommand", "Could not start Auto DJ=${dj.name}", error)
                            AndroidPlaybackNotificationControls.isPlaying = false
                            updateMediaSessionPlaybackState()
                        }
                }
                true
            }
            mediaId.startsWith(AndroidAutoPlaybackControls.MediaIdSmartTemplatePrefix) -> {
                val templateIndex = Uri.decode(mediaId.removePrefix(AndroidAutoPlaybackControls.MediaIdSmartTemplatePrefix))
                    .toIntOrNull()
                    ?: return false
                val template = SmartPlaylistTemplates.recommended.getOrNull(templateIndex) ?: return false
                val tracks = smartTemplateTracks(storage, sourceId, template.title)
                if (tracks.isEmpty()) {
                    Log.w("NaviampAutoCommand", "Auto smart template had no tracks title=${template.title}")
                    return false
                }
                Log.i("NaviampAutoCommand", "Auto playing smart template=${template.title} count=${tracks.size}")
                playServiceTrackQueue(storage, sourceId, tracks, currentIndex = 0)
                true
            }
            mediaId.startsWith(AndroidAutoPlaybackControls.MediaIdPlaylistPlayPrefix) -> {
                val playlistId = Uri.decode(mediaId.removePrefix(AndroidAutoPlaybackControls.MediaIdPlaylistPlayPrefix))
                val provider = NavidromeProvider(source.toNavidromeConnection())
                launchServiceSelection {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            loadServicePlaylistTracks(storage, provider, playlistId)
                        }
                    }
                        .onSuccess { tracks ->
                            playServiceTrackQueue(storage, sourceId, tracks, currentIndex = 0)
                        }
                        .onFailure { error ->
                            Log.w("NaviampAutoCommand", "Could not start Auto playlist=$playlistId", error)
                            AndroidPlaybackNotificationControls.isPlaying = false
                            updateMediaSessionPlaybackState()
                        }
                }
                true
            }
            mediaId.startsWith(AndroidAutoPlaybackControls.MediaIdPlaylistShufflePrefix) -> {
                val playlistId = Uri.decode(mediaId.removePrefix(AndroidAutoPlaybackControls.MediaIdPlaylistShufflePrefix))
                val provider = NavidromeProvider(source.toNavidromeConnection())
                launchServiceSelection {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            loadServicePlaylistTracks(storage, provider, playlistId)
                        }
                    }
                        .onSuccess { tracks ->
                            playServiceTrackQueue(storage, sourceId, tracks.shuffled(), currentIndex = 0)
                        }
                        .onFailure { error ->
                            Log.w("NaviampAutoCommand", "Could not shuffle Auto playlist=$playlistId", error)
                            AndroidPlaybackNotificationControls.isPlaying = false
                            updateMediaSessionPlaybackState()
                        }
                }
                true
            }
            mediaId.startsWith(AndroidAutoPlaybackControls.MediaIdPlaylistTrackPrefix) -> {
                val parts = mediaId.removePrefix(AndroidAutoPlaybackControls.MediaIdPlaylistTrackPrefix).mediaIdParts()
                val playlistId = parts.getOrNull(0).orEmpty()
                val trackId = parts.getOrNull(1).orEmpty()
                if (playlistId.isBlank() || trackId.isBlank()) return false
                val provider = NavidromeProvider(source.toNavidromeConnection())
                launchServiceSelection {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            loadServicePlaylistTracks(storage, provider, playlistId)
                        }
                    }
                        .onSuccess { tracks ->
                            val index = tracks.indexOfFirst { it.id.value == trackId }
                            if (index >= 0) {
                                playServiceTrackQueue(storage, sourceId, tracks, index)
                            }
                        }
                        .onFailure { error ->
                            Log.w("NaviampAutoCommand", "Could not start Auto playlist track=$trackId", error)
                            AndroidPlaybackNotificationControls.isPlaying = false
                            updateMediaSessionPlaybackState()
                        }
                }
                true
            }
            mediaId.startsWith(AndroidAutoPlaybackControls.MediaIdRadioStationPrefix) -> {
                val parts = mediaId.removePrefix(AndroidAutoPlaybackControls.MediaIdRadioStationPrefix).mediaIdParts()
                val station = InternetRadioStation(
                    id = parts.getOrNull(0).orEmpty(),
                    name = parts.getOrNull(1).orEmpty().ifBlank { "Internet Radio" },
                    streamUrl = parts.getOrNull(2).orEmpty(),
                    homePageUrl = parts.getOrNull(3)?.takeIf { it.isNotBlank() },
                )
                if (station.streamUrl.isBlank()) return false
                playServiceInternetRadioStation(storage, storage, sourceId, station)
                true
            }
            mediaId.startsWith(AndroidAutoPlaybackControls.MediaIdRecentRadioPrefix) -> {
                val recentId = Uri.decode(mediaId.removePrefix(AndroidAutoPlaybackControls.MediaIdRecentRadioPrefix))
                val settingsStore = AndroidSettingsStore(applicationContext)
                val recentStream = settingsStore.loadRecentRadioStreams().firstOrNull { it.id == recentId }
                if (recentStream != null) {
                    playServiceRecentRadioStream(storage, storage, sourceId, recentStream)
                    return true
                }
                val station = settingsStore.loadRecentInternetRadioStations()
                    .firstOrNull { it.id == recentId }
                    ?.toStation()
                if (station != null) {
                playServiceInternetRadioStation(storage, storage, sourceId, station)
                    return true
                }
                false
            }
            mediaId.startsWith(AndroidAutoPlaybackControls.MediaIdTrackPrefix) -> {
                val trackId = Uri.decode(mediaId.removePrefix(AndroidAutoPlaybackControls.MediaIdTrackPrefix))
                val track = storage.libraryTrack(sourceId, TrackId(trackId)) ?: return false
                val queue = serviceQueueForLibraryTrack(storage, sourceId, track)
                val index = queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
                playServiceTrackQueue(storage, sourceId, queue, index)
                true
            }
            mediaId.startsWith(AndroidAutoPlaybackControls.MediaIdArtistTrackPrefix) -> {
                val parts = mediaId.removePrefix(AndroidAutoPlaybackControls.MediaIdArtistTrackPrefix).mediaIdParts()
                val artistId = parts.getOrNull(0).orEmpty()
                val artistName = parts.getOrNull(1)
                val trackId = parts.getOrNull(2).orEmpty()
                if (trackId.isBlank()) return false
                val provider = NavidromeProvider(source.toNavidromeConnection())
                launchServiceSelection {
                    runCatching {
                        val tracks = loadServiceArtistTracks(storage, storage, sourceId, provider, artistId, artistName)
                        val track = tracks.firstOrNull { it.id.value == trackId }
                            ?: storage.libraryTrack(sourceId, TrackId(trackId))
                        track?.let { selectedTrack ->
                            val queue = tracks.takeIf { items -> items.any { it.id == selectedTrack.id } }
                                ?: serviceQueueForLibraryTrack(storage, sourceId, selectedTrack)
                            selectedTrack to queue
                        }
                    }.onSuccess { selection ->
                        if (selection != null) {
                            val (track, queue) = selection
                            playServiceTrackQueue(storage, sourceId, queue, queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0))
                        } else {
                            AndroidPlaybackNotificationControls.isPlaying = false
                            updateMediaSessionPlaybackState()
                        }
                    }.onFailure { error ->
                        Log.w("NaviampAutoCommand", "Could not start Auto artist track=$trackId", error)
                        AndroidPlaybackNotificationControls.isPlaying = false
                        updateMediaSessionPlaybackState()
                    }
                }
                true
            }
            mediaId.startsWith(AndroidAutoPlaybackControls.MediaIdAlbumTrackPrefix) -> {
                val parts = mediaId.removePrefix(AndroidAutoPlaybackControls.MediaIdAlbumTrackPrefix).mediaIdParts()
                val albumId = parts.getOrNull(0).orEmpty()
                val trackId = parts.getOrNull(1).orEmpty()
                if (albumId.isBlank() || trackId.isBlank()) return false
                val provider = NavidromeProvider(source.toNavidromeConnection())
                launchServiceSelection {
                    runCatching {
                        val tracks = loadServiceAlbumTracks(
                            libraryIndexRepository = storage,
                            providerResponseCacheRepository = storage,
                            sourceId = sourceId,
                            provider = provider,
                            albumId = albumId,
                            albumTitle = null,
                            albumArtist = null,
                        )
                        val track = tracks.firstOrNull { it.id.value == trackId }
                            ?: storage.libraryTrack(sourceId, TrackId(trackId))
                        track?.let { selectedTrack ->
                            val queue = tracks.takeIf { items -> items.any { it.id == selectedTrack.id } }
                                ?: serviceQueueForLibraryTrack(storage, sourceId, selectedTrack)
                            selectedTrack to queue
                        }
                    }
                        .onSuccess { selection ->
                            if (selection != null) {
                                val (track, queue) = selection
                                playServiceTrackQueue(storage, sourceId, queue, queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0))
                            } else {
                                AndroidPlaybackNotificationControls.isPlaying = false
                                updateMediaSessionPlaybackState()
                            }
                        }
                        .onFailure { error ->
                            Log.w("NaviampAutoCommand", "Could not start Auto album track=$trackId", error)
                            AndroidPlaybackNotificationControls.isPlaying = false
                            updateMediaSessionPlaybackState()
                        }
                }
                true
            }
            mediaId.startsWith(AndroidAutoPlaybackControls.MediaIdArtistPlayPrefix) -> {
                val parts = mediaId.removePrefix(AndroidAutoPlaybackControls.MediaIdArtistPlayPrefix).mediaIdParts()
                val artistId = parts.getOrNull(0).orEmpty()
                val artistName = parts.getOrNull(1)
                if (artistId.isBlank() && artistName.isNullOrBlank()) return false
                val provider = NavidromeProvider(source.toNavidromeConnection())
                launchServiceSelection {
                    runCatching {
                        loadServiceArtistTracks(storage, storage, sourceId, provider, artistId, artistName)
                    }.onSuccess { tracks ->
                        playServiceTrackQueue(storage, sourceId, tracks, currentIndex = 0)
                    }.onFailure { error ->
                        Log.w("NaviampAutoCommand", "Could not play Auto artist=$artistId", error)
                        AndroidPlaybackNotificationControls.isPlaying = false
                        updateMediaSessionPlaybackState()
                    }
                }
                true
            }
            mediaId.startsWith(AndroidAutoPlaybackControls.MediaIdArtistShufflePrefix) -> {
                val parts = mediaId.removePrefix(AndroidAutoPlaybackControls.MediaIdArtistShufflePrefix).mediaIdParts()
                val artistId = parts.getOrNull(0).orEmpty()
                val artistName = parts.getOrNull(1)
                if (artistId.isBlank() && artistName.isNullOrBlank()) return false
                val provider = NavidromeProvider(source.toNavidromeConnection())
                launchServiceSelection {
                    runCatching {
                        loadServiceArtistTracks(storage, storage, sourceId, provider, artistId, artistName)
                    }.onSuccess { tracks ->
                        playServiceTrackQueue(storage, sourceId, tracks.shuffled(), currentIndex = 0)
                    }.onFailure { error ->
                        Log.w("NaviampAutoCommand", "Could not shuffle Auto artist=$artistId", error)
                        AndroidPlaybackNotificationControls.isPlaying = false
                        updateMediaSessionPlaybackState()
                    }
                }
                true
            }
            mediaId.startsWith(AndroidAutoPlaybackControls.MediaIdAlbumPlayPrefix) -> {
                val parts = mediaId.removePrefix(AndroidAutoPlaybackControls.MediaIdAlbumPlayPrefix).mediaIdParts()
                val albumId = parts.getOrNull(0).orEmpty()
                val albumTitle = parts.getOrNull(1)
                val albumArtist = parts.getOrNull(2)
                if (albumId.isBlank()) return false
                val provider = NavidromeProvider(source.toNavidromeConnection())
                launchServiceSelection {
                    runCatching {
                        loadServiceAlbumTracks(storage, storage, sourceId, provider, albumId, albumTitle, albumArtist)
                    }.onSuccess { tracks ->
                        playServiceTrackQueue(storage, sourceId, tracks, currentIndex = 0)
                    }.onFailure { error ->
                        Log.w("NaviampAutoCommand", "Could not play Auto album=$albumId", error)
                        AndroidPlaybackNotificationControls.isPlaying = false
                        updateMediaSessionPlaybackState()
                    }
                }
                true
            }
            mediaId.startsWith(AndroidAutoPlaybackControls.MediaIdAlbumShufflePrefix) -> {
                val parts = mediaId.removePrefix(AndroidAutoPlaybackControls.MediaIdAlbumShufflePrefix).mediaIdParts()
                val albumId = parts.getOrNull(0).orEmpty()
                val albumTitle = parts.getOrNull(1)
                val albumArtist = parts.getOrNull(2)
                if (albumId.isBlank()) return false
                val provider = NavidromeProvider(source.toNavidromeConnection())
                launchServiceSelection {
                    runCatching {
                        loadServiceAlbumTracks(storage, storage, sourceId, provider, albumId, albumTitle, albumArtist)
                    }.onSuccess { tracks ->
                        val shuffled = tracks.shuffled()
                        playServiceTrackQueue(storage, sourceId, shuffled, currentIndex = 0)
                    }.onFailure { error ->
                        Log.w("NaviampAutoCommand", "Could not shuffle Auto album=$albumId", error)
                        AndroidPlaybackNotificationControls.isPlaying = false
                        updateMediaSessionPlaybackState()
                    }
                }
                true
            }
            mediaId.startsWith(AndroidAutoPlaybackControls.MediaIdDownloadPrefix) -> {
                val trackId = Uri.decode(mediaId.removePrefix(AndroidAutoPlaybackControls.MediaIdDownloadPrefix))
                val downloads = storage.downloadedTracks(sourceId)
                    .filter { it.file.exists() }
                    .map { it.track }
                val index = downloads.indexOfFirst { it.id.value == trackId }
                if (index < 0) return false
                playServiceTrackQueue(storage, sourceId, downloads, index)
                true
            }
            else -> false
        }
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
        val voiceQuery = trimmedQuery.autoVoiceQuery()
        if (voiceQuery.isDownloadedMusicQuery()) {
            return playServiceDownloadedMusicSearch(storage, source.id, trimmedQuery)
        }
        if (voiceQuery.isLibraryRadioQuery()) {
            return playServiceLibraryRadioSearch(storage, storage, source.id, trimmedQuery)
        }
        if (voiceQuery.isPlaylistQuery()) {
            return playServicePlaylistVoiceSearch(storage, source.id, voiceQuery.playlistSearchQuery(), trimmedQuery)
        }
        if (voiceQuery.isInternetRadioStationQuery()) {
            return playServiceInternetRadioVoiceSearch(storage, source.id, voiceQuery.stationSearchQuery(), trimmedQuery)
        }
        val radioQuery = trimmedQuery.radioSearchQuery()
        if (radioQuery != null) {
            if (playServiceArtistRadioSearch(storage, storage, storage, source.id, radioQuery)) return true
            if (playServiceGenreRadioSearch(storage, storage, source.id, radioQuery)) return true
            Log.w("NaviampAutoCommand", "No Auto radio match for query=$trimmedQuery normalized=$radioQuery")
            return false
        }
        val snapshot = storage.searchLibrary(source.id, trimmedQuery, AndroidAutoBrowseLimit.toLong(), 0)
        snapshot.tracks.firstOrNull()?.let { track ->
            Log.i("NaviampAutoCommand", "Auto voice search matched track=${track.title}")
            val queue = serviceQueueForLibraryTrack(storage, source.id, track)
            playServiceTrackQueue(storage, source.id, queue, queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0))
            return true
        }
        snapshot.albums.firstOrNull()?.let { album ->
            val tracks = storage.libraryTracksForAlbum(source.id, album.id, 200)
                .ifEmpty { storage.libraryTracksForAlbumTitle(source.id, album.title, album.artistName, 200) }
            if (tracks.isNotEmpty()) {
                Log.i("NaviampAutoCommand", "Auto voice search matched album=${album.title}")
                playServiceTrackQueue(storage, source.id, tracks, 0)
                return true
            }
            Log.w("NaviampAutoCommand", "Auto voice album match had no tracks album=${album.title}")
        }
        snapshot.artists.forEach { artist ->
            val tracks = storage.libraryTracksForArtist(source.id, artist.id, 200)
                .ifEmpty { storage.libraryTracksForArtistName(source.id, artist.name, 200) }
            if (tracks.isNotEmpty()) {
                Log.i("NaviampAutoCommand", "Auto voice search matched artist=${artist.name}")
                playServiceTrackQueue(storage, source.id, tracks, 0)
                return true
            }
            Log.w("NaviampAutoCommand", "Auto voice artist match had no tracks artist=${artist.name}")
        }
        Log.w("NaviampAutoCommand", "No Auto voice search match query=$trimmedQuery")
        return false
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
        val searchArtists = libraryIndexRepository.searchLibrary(sourceId, query, AndroidAutoBrowseLimit.toLong(), 0).artists
        val artist = searchArtists.firstOrNull { it.name.equals(query, ignoreCase = true) }
            ?: searchArtists.firstOrNull()
            ?: findVoiceArtistMatch(libraryIndexRepository, sourceId, query)
            ?: return false
        val source = mediaSourceRepository.latestMediaSource() ?: return false
        val provider = NavidromeProvider(source.toNavidromeConnection())
        val recent = RecentRadioStream(
            id = "artist:${artist.id.value}",
            label = "${artist.name} Radio",
            kind = RecentRadioKind.Artist,
            artist = app.naviamp.domain.settings.SavedArtist.fromArtist(artist),
        )
        launchServiceSelection {
            val radioService = RadioService(
                provider = provider,
                tuning = AndroidSettingsStore(applicationContext).loadPlaybackSettings().radioTuning,
            )
            runCatching { withContext(Dispatchers.IO) { radioService.artistRadio(artist.id) } }
                .onSuccess { tracks ->
                    rememberRecentRadioStream(recent.withRadioCoverArtIds(tracks))
                    playServiceTrackQueue(playbackSessionRepository, sourceId, tracks, currentIndex = 0)
                }
                .onFailure { error ->
                    Log.w("NaviampAutoCommand", "Could not start artist radio search=${artist.name}", error)
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
        publishAutoQueue(ensureMediaSession())
        updateMediaSessionPlaybackState()
    }

    private fun smartTemplateTracks(
        storage: AndroidStorageDependencies,
        sourceId: String,
        title: String,
    ): List<Track> {
        val snapshot = storage.librarySnapshot(sourceId, AndroidAutoTemplateTrackScanLimit, 0)
        val tracks = snapshot.tracks
        return when (title) {
            "Recently Played" -> tracks
                .filter { it.lastPlayedAtIso8601 != null }
                .sortedByDescending { it.lastPlayedAtIso8601 }
            "Never Played" -> tracks
                .filter { (it.playCount ?: 0) == 0 }
                .shuffled()
            "High Rated" -> tracks
                .filter { (it.userRating ?: 0) >= 4 }
                .sortedWith(compareByDescending<Track> { it.userRating ?: 0 }.thenBy { it.title.lowercase() })
            "Favorite Albums" -> {
                val favoriteAlbumIds = snapshot.albums
                    .filter { it.favoritedAtIso8601 != null }
                    .map { it.id }
                    .toSet()
                tracks
                    .filter { track -> track.albumId?.let { it in favoriteAlbumIds } == true }
                    .sortedWith(compareBy<Track> { it.albumTitle.orEmpty().lowercase() }.thenBy { it.title.lowercase() })
            }
            "Recently Added but Unplayed" -> tracks
                .filter { (it.playCount ?: 0) == 0 }
                .sortedByDescending { albumRecentlyAddedKey(snapshot.albums, it) }
            "Long-Unheard Favorites" -> tracks
                .filter { it.favoritedAtIso8601 != null }
                .sortedBy { it.lastPlayedAtIso8601 ?: "" }
            else -> emptyList()
        }
            .distinctBy { it.id }
            .take(AndroidAutoSmartTemplateTrackLimit)
    }

    private fun albumRecentlyAddedKey(albums: List<Album>, track: Track): String =
        track.albumId
            ?.let { albumId -> albums.firstOrNull { it.id == albumId } }
            ?.recentlyAddedAtIso8601
            .orEmpty()

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
        libraryIndexRepository.libraryTracksForAlbum(sourceId, AlbumId(albumId), AndroidAutoBrowseLimit.toLong())
            .ifEmpty {
                albumTitle?.let { title ->
                    libraryIndexRepository.libraryTracksForAlbumTitle(sourceId, title, albumArtist, AndroidAutoBrowseLimit.toLong())
                }.orEmpty()
            }
            .ifEmpty {
                runCatching {
                    withContext(Dispatchers.IO) {
                        providerResponseService(providerResponseCacheRepository).album(provider, AlbumId(albumId)).tracks
                    }
                }.getOrDefault(emptyList())
            }

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
        artistId.takeIf { it.isNotBlank() }
            ?.let { id -> libraryIndexRepository.libraryTracksForArtist(sourceId, ArtistId(id), AndroidAutoBrowseLimit.toLong()) }
            .orEmpty()
            .ifEmpty {
                artistName?.let { name ->
                    libraryIndexRepository.libraryTracksForArtistName(sourceId, name, AndroidAutoBrowseLimit.toLong())
                }.orEmpty()
            }
            .ifEmpty {
                artistId.takeIf { it.isNotBlank() }?.let { id ->
                    runCatching {
                        withContext(Dispatchers.IO) {
                            provider.artist(ArtistId(id)).albums.flatMap { album ->
                                provider.album(album.id).tracks
                            }
                        }
                    }.onSuccess { tracks ->
                        Log.i("NaviampAutoCommand", "Auto artist provider fallback artist=$id count=${tracks.size}")
                    }.onFailure { error ->
                        Log.w("NaviampAutoCommand", "Auto artist provider fallback failed artist=$id", error)
                    }.getOrDefault(emptyList())
                }.orEmpty()
            }

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
                                playbackSessionRepository,
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
        val settingsStore = AndroidSettingsStore(applicationContext)
        settingsStore.saveRecentRadioStreams(
            recentRadioStreamsWith(settingsStore.loadRecentRadioStreams(), stream),
        )
        markAndroidSettingsSyncChangedAndAutoExport(
            context = applicationContext,
            settingsStore = settingsStore,
            storage = serviceStorage,
        )
    }

    private fun rememberRecentInternetRadioStation(station: InternetRadioStation) {
        val settingsStore = AndroidSettingsStore(applicationContext)
        applyRememberInternetRadioStation(
            plan = planRememberInternetRadioStation(
                station = station,
                recentStations = emptyList(),
                recentSavedStations = settingsStore.loadRecentInternetRadioStations(),
            ),
            applier = InternetRadioRecentStationApplier(
                saveRecentStations = settingsStore::saveRecentInternetRadioStations,
            ),
        )
        markAndroidSettingsSyncChangedAndAutoExport(
            context = applicationContext,
            settingsStore = settingsStore,
            storage = serviceStorage,
        )
    }

    private fun handleServicePlaybackProgress(
        playbackSessionRepository: PlaybackSessionRepository,
        sourceId: String,
        session: PlaybackSessionSettings,
        progress: PlaybackProgress,
    ) {
        servicePlaybackRuntimeController.handlePlaybackProgress(
            playbackSessionRepository = playbackSessionRepository,
            sourceId = sourceId,
            session = session,
            progress = progress,
        )
    }

    private fun ensureMediaSession(): MediaSessionCompat =
        mediaSession ?: MediaSessionCompat(this, "NaviampPlayback").apply {
            resetMediaSessionPublishState()
            setCallback(
                object : MediaSessionCompat.Callback() {
                    override fun onPlay() {
                        if (!AndroidPlaybackNotificationControls.isPlaying) {
                            handleAutoPlayPause()
                            refreshNotification(null)
                        }
                    }

                    override fun onPause() {
                        if (AndroidPlaybackNotificationControls.isPlaying) {
                            handleAutoPlayPause()
                            refreshNotification(null)
                        }
                    }

                    override fun onSkipToPrevious() {
                        handleAutoPrevious()
                        refreshNotification(null)
                    }

                    override fun onSkipToNext() {
                        handleAutoNext()
                        refreshNotification(null)
                    }

                    override fun onSkipToQueueItem(id: Long) {
                        playServiceAutoQueueItem(id.toInt())
                        refreshNotification(null)
                    }

                    override fun onStop() {
                        handleAutoStop("media session stop")
                    }

                    override fun onSeekTo(pos: Long) {
                        handleAutoSeek(pos)
                    }

                    override fun onRewind() {
                        handleAutoSeek(
                            ((AndroidPlaybackNotificationControls.positionMillis ?: 0L) - MediaSessionSeekStepMillis)
                                .coerceAtLeast(0L),
                        )
                    }

                    override fun onFastForward() {
                        val currentPosition = AndroidPlaybackNotificationControls.positionMillis ?: 0L
                        val duration = AndroidPlaybackNotificationControls.durationMillis
                        val nextPosition = currentPosition + MediaSessionSeekStepMillis
                        handleAutoSeek(
                            if (duration != null && duration > 0L) {
                                nextPosition.coerceAtMost(duration)
                            } else {
                                nextPosition
                            },
                        )
                    }

                    override fun onPlayFromMediaId(mediaId: String, extras: Bundle?) {
                        autoCommandController.playFromMediaId(mediaId, extras)
                    }

                    override fun onPlayFromSearch(query: String, extras: Bundle?) {
                        autoCommandController.playFromSearch(query, extras)
                    }

                    override fun onCustomAction(action: String, extras: android.os.Bundle?) {
                        autoCommandController.customAction(action, extras)
                    }
                },
            )
            publishPlaybackState(this)
            if (!browserSessionTokenSet) {
                setSessionToken(sessionToken)
                browserSessionTokenSet = true
            }
            this@AndroidPlaybackForegroundService.mediaSession = this
        }

    private fun resetMediaSessionPublishState() {
        lastPublishedAutoQueueSignature = null
        lastPublishedMediaMetadataSignature = null
        lastPublishedPlaybackStateSignature = null
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?,
    ): BrowserRoot? {
        val remoteUser = MediaSessionManager.RemoteUserInfo(
            clientPackageName,
            -1,
            clientUid,
        )
        val trusted = clientUid == applicationInfo.uid ||
            MediaSessionManager.getSessionManager(applicationContext).isTrustedForMediaControl(remoteUser)
        if (!trusted) {
            Log.w("NaviampAutoCommand", "Rejecting untrusted media browser client=$clientPackageName uid=$clientUid")
            return null
        }
        hydrateSavedPlaybackSession()
        return BrowserRoot(
            AndroidAutoPlaybackControls.MediaIdRoot,
            Bundle().apply {
                putBoolean(MediaConstants.BROWSER_SERVICE_EXTRAS_KEY_SEARCH_SUPPORTED, true)
            },
        )
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>,
    ) {
        hydrateSavedPlaybackSession()
        autoBrowseController.loadChildren(parentId, result)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>,
        options: Bundle,
    ) {
        hydrateSavedPlaybackSession()
        autoBrowseController.loadChildren(parentId, result, options)
    }

    override fun onSearch(
        query: String,
        extras: Bundle?,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>,
    ) {
        hydrateSavedPlaybackSession()
        autoBrowseController.search(query, extras, result)
    }

    private fun updateMediaSession(metadata: AndroidPlaybackNotificationMetadata, largeIcon: Bitmap?) {
        val session = ensureMediaSession()
        currentMediaSessionDurationMillis = currentPlaybackDurationMillis()
        publishAutoQueue(session)
        val metadataSignature = mediaMetadataSignature(metadata, currentMediaSessionDurationMillis, largeIcon)
        if (metadataSignature != lastPublishedMediaMetadataSignature) {
            lastPublishedMediaMetadataSignature = metadataSignature
            session.setMetadata(
                MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, currentMediaSessionMediaId())
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, metadata.title.orEmpty())
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, metadata.subtitle.orEmpty())
                    .apply {
                        currentAutoQueue.getOrNull(currentAutoQueueIndex)?.albumTitle?.takeIf { it.isNotBlank() }?.let { album ->
                            putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album)
                        }
                    }
                    .apply {
                        currentMediaSessionDurationMillis?.takeIf { it > 0L }?.let { duration ->
                            putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
                        }
                    }
                    .apply {
                        largeIcon?.let { art ->
                            putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, art)
                            putBitmap(MediaMetadataCompat.METADATA_KEY_ART, art)
                        }
                    }
                    .build(),
            )
        }
        publishPlaybackState(session)
        session.isActive = true
    }

    private fun mediaMetadataSignature(
        metadata: AndroidPlaybackNotificationMetadata,
        durationMillis: Long?,
        largeIcon: Bitmap?,
    ): String =
        listOf(
            currentMediaSessionMediaId(),
            metadata.title.orEmpty(),
            metadata.subtitle.orEmpty(),
            durationMillis?.toString().orEmpty(),
            largeIcon?.generationId?.toString().orEmpty(),
        ).joinToString("|")

    private fun currentMediaSessionMediaId(): String =
        currentAutoQueue.getOrNull(currentAutoQueueIndex)?.id?.value
            ?: currentMetadata.coverArtUrl?.takeIf { it.isNotBlank() }
            ?: "naviamp-now-playing"

    private fun currentPlaybackDurationMillis(): Long? =
        AndroidPlaybackNotificationControls.durationMillis
            ?.takeIf { it > 0L }
            ?: currentAutoQueue.getOrNull(currentAutoQueueIndex)
                ?.durationSeconds
                ?.takeIf { it > 0 }
                ?.let { it * 1_000L }

    private fun toggleServiceShuffle() {
        serviceShuffleEnabled = !serviceShuffleEnabled
        if (serviceShuffleEnabled) {
            autoQueueController.replaceQueue(PlaybackQueue(currentAutoQueue, currentAutoQueueIndex))
            val shuffled = autoQueueController.toggleUpcomingShuffle(shuffledSnapshot = null)
            if (shuffled != null) {
                currentAutoQueue = shuffled.queue.tracks
                currentAutoQueueIndex = shuffled.queue.currentIndex
                lastPublishedAutoQueueSignature = null
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
                        .withAndroidPendingActions(source.id, storage)
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

    private fun publishAutoQueue(session: MediaSessionCompat) {
        val queue = currentAutoQueue
        val queueSignature = "$currentAutoQueueIndex:${queue.joinToString("|") { it.id.value }}"
        if (queueSignature == lastPublishedAutoQueueSignature) return
        lastPublishedAutoQueueSignature = queueSignature
        if (queue.isEmpty()) {
            Log.i("NaviampAutoCommand", "Publishing empty native Auto queue")
            session.setQueue(emptyList())
            session.setQueueTitle(null)
            return
        }
        Log.i(
            "NaviampAutoCommand",
            "Publishing native Auto queue count=${queue.size} index=$currentAutoQueueIndex",
        )
        session.setQueueTitle("Queue")
        session.setQueue(
            queue.mapIndexed { index, track ->
                MediaSessionCompat.QueueItem(
                    MediaDescriptionCompat.Builder()
                        .setMediaId("${AndroidAutoPlaybackControls.MediaIdQueueTrackPrefix}${Uri.encode(index.toString())}")
                        .setTitle(track.title)
                        .setSubtitle(track.artistName)
                        .setDescription(track.albumTitle)
                        .apply {
                            val artUrl = serviceStorage.savedCoverArtUrl(track)
                            artUrl?.let { setIconUri(Uri.parse(it)) }
                        }
                        .build(),
                    index.toLong(),
                )
            },
        )
    }

    private fun updateMediaSessionPlaybackState() {
        val session = ensureMediaSession()
        if (currentMediaSessionDurationMillis != currentPlaybackDurationMillis()) {
            updateMediaSession(currentMetadata, currentLargeIcon)
            return
        }
        publishPlaybackState(session)
        session.isActive = true
    }

    private fun publishPlaybackState(session: MediaSessionCompat) {
        val stateSignature = playbackStateSignature()
        if (stateSignature == lastPublishedPlaybackStateSignature) return
        lastPublishedPlaybackStateSignature = stateSignature
        val favoriteIcon = if (AndroidPlaybackNotificationControls.isFavorite) {
            R.drawable.ic_favorite_filled_24
        } else {
            R.drawable.ic_favorite_24
        }
        val favoriteTitle = if (AndroidPlaybackNotificationControls.isFavorite) "Unfavorite" else "Favorite"
        session.setPlaybackState(buildPlaybackState(favoriteTitle, favoriteIcon))
    }

    private fun playbackStateSignature(): String =
        listOf(
            AndroidPlaybackNotificationControls.isPlaying.toString(),
            AndroidPlaybackNotificationControls.positionMillis?.toString().orEmpty(),
            AndroidPlaybackNotificationControls.durationMillis?.toString().orEmpty(),
            AndroidPlaybackNotificationControls.canFavorite.toString(),
            AndroidPlaybackNotificationControls.isFavorite.toString(),
            serviceShuffleEnabled.toString(),
            serviceRepeatMode.name,
            currentAutoQueueIndex.toString(),
            currentAutoQueue.getOrNull(currentAutoQueueIndex)?.id?.value.orEmpty(),
        ).joinToString("|")

    private fun buildPlaybackState(
        favoriteTitle: String,
        favoriteIcon: Int,
    ): PlaybackStateCompat =
        PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_REWIND or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_FAST_FORWARD or
                    PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM or
                    PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH or
                    PlaybackStateCompat.ACTION_SEEK_TO or
                    PlaybackStateCompat.ACTION_STOP,
            )
            .addCustomAction(ActionFavorite, favoriteTitle, favoriteIcon)
            .addCustomAction(
                ActionShuffle,
                if (serviceShuffleEnabled) "Shuffle on" else "Shuffle off",
                R.drawable.ic_shuffle_24,
            )
            .addCustomAction(
                ActionRepeat,
                when (serviceRepeatMode) {
                    ServiceRepeatMode.Off -> "Repeat off"
                    ServiceRepeatMode.All -> "Repeat all"
                    ServiceRepeatMode.One -> "Repeat one"
                },
                R.drawable.ic_repeat_24,
            )
            .addCustomAction(ActionTrackRadio, "Start song radio", R.drawable.ic_auto_radio)
            .setState(
                if (AndroidPlaybackNotificationControls.isPlaying) {
                    PlaybackStateCompat.STATE_PLAYING
                } else {
                    PlaybackStateCompat.STATE_PAUSED
                },
                AndroidPlaybackNotificationControls.positionMillis
                    ?: PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                if (AndroidPlaybackNotificationControls.isPlaying) 1f else 0f,
            )
            .setActiveQueueItemId(
                currentAutoQueueIndex
                    .takeIf { it in currentAutoQueue.indices }
                    ?.toLong()
                    ?: -1L,
            )
            .build()

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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
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
        private const val MediaSessionSeekStepMillis = 10_000L
        private const val ExtraTitle = "title"
        private const val ExtraSubtitle = "subtitle"
        private const val ExtraCoverArtUrl = "coverArtUrl"
        private const val ExtraFromEngine = "fromEngine"
        private const val ExtraPositionMillis = "positionMillis"
        private const val ExtraDurationMillis = "durationMillis"
        private val PlayerNotificationColor = Color.rgb(82, 35, 31)
        private var currentMetadata = AndroidPlaybackNotificationMetadata()
        private var currentLargeIcon: Bitmap? = null
        private var currentMediaSessionDurationMillis: Long? = null
        private var currentAutoQueue: List<Track> = emptyList()
        private var currentAutoQueueIndex: Int = -1
        private var lastPublishedAutoQueueSignature: String? = null
        private var lastPublishedMediaMetadataSignature: String? = null
        private var lastPublishedPlaybackStateSignature: String? = null
        private var serviceShuffleEnabled = false
        private var serviceRepeatMode = ServiceRepeatMode.Off
        @Volatile
        private var serviceCreated = false

        fun start(context: Context, metadata: AndroidPlaybackNotificationMetadata) {
            val intent = Intent(context, AndroidPlaybackForegroundService::class.java)
                .setAction(ActionStart)
                .putExtra(ExtraTitle, metadata.title)
                .putExtra(ExtraSubtitle, metadata.subtitle)
                .putExtra(ExtraCoverArtUrl, metadata.coverArtUrl)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }.onFailure { error ->
                Log.w("NaviampAutoCommand", "Could not start playback foreground service", error)
            }
        }

        fun update(context: Context, metadata: AndroidPlaybackNotificationMetadata) {
            if (!serviceCreated) return
            val intent = Intent(context, AndroidPlaybackForegroundService::class.java)
                .setAction(ActionUpdate)
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
                        .putExtra(ExtraPositionMillis, positionMillis ?: -1L)
                        .putExtra(ExtraDurationMillis, durationMillis ?: -1L),
                )
            }.onFailure { error ->
                Log.w("NaviampAutoCommand", "Could not update playback foreground service progress", error)
            }
        }
    }
}

private enum class ServiceRepeatMode {
    Off,
    All,
    One,
}

private fun Bitmap.dominantColor(): Int {
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

private fun String.decodedMediaId(): String =
    Uri.decode(this)

private fun String.mediaIdParts(): List<String> =
    split("|").map { Uri.decode(it) }

private data class AutoVoiceQuery(
    val original: String,
    val normalized: String,
) {
    fun isDownloadedMusicQuery(): Boolean =
        normalized.contains("downloaded") ||
            normalized.contains("downloads") ||
            normalized.contains("offline")

    fun isLibraryRadioQuery(): Boolean =
        normalized.contains("library radio") ||
            normalized.contains("my library radio")

    fun isPlaylistQuery(): Boolean =
        normalized.contains("playlist")

    fun isInternetRadioStationQuery(): Boolean =
        normalized.contains("internet radio") ||
            normalized.contains("station")

    fun playlistSearchQuery(): String =
        original.voiceIntentTarget()
            .replace(Regex("\\bplaylist\\b", RegexOption.IGNORE_CASE), " ")
            .normalizedVoiceTarget()

    fun stationSearchQuery(): String =
        original.voiceIntentTarget()
            .replace(Regex("\\binternet radio\\b", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("\\bradio station\\b", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("\\bstation\\b", RegexOption.IGNORE_CASE), " ")
            .normalizedVoiceTarget()
}

private fun String.autoVoiceQuery(): AutoVoiceQuery =
    AutoVoiceQuery(
        original = trim(),
        normalized = lowercase().replace(Regex("\\s+"), " ").trim(),
    )

private fun String.voiceIntentTarget(): String =
    replace(Regex("\\b(play|start|listen to|listen|some|an|a|the|my)\\b", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("\\bon naviamp\\b", RegexOption.IGNORE_CASE), " ")
        .normalizedVoiceTarget()

private fun String.normalizedVoiceTarget(): String =
    replace(Regex("\\s+"), " ")
        .trim()

private fun <T> List<T>.bestVoiceNameMatch(
    query: String,
    name: (T) -> String,
): T? {
    val queryKey = query.voiceSearchKey()
    if (queryKey.isBlank()) return null
    return mapNotNull { item ->
        val score = voiceArtistMatchScore(queryKey, name(item).voiceSearchKey())
        if (score == null) null else item to score
    }
        .sortedWith(compareBy<Pair<T, Int>> { it.second }.thenBy { name(it.first).length })
        .firstOrNull()
        ?.first
}

private fun String.radioSearchQuery(): String? {
    val normalized = lowercase()
    if (!normalized.contains("radio")) return null
    return replace(Regex("\\b(play|start|listen to|listen|some|an|a|the)\\b", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("\\bradio\\b", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("\\bon naviamp\\b", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .takeIf { it.isNotBlank() }
}

private fun findVoiceArtistMatch(
    libraryIndexRepository: LocalLibraryIndexRepository,
    sourceId: String,
    query: String,
): Artist? {
    val queryKey = query.voiceSearchKey()
    if (queryKey.isBlank()) return null
    return libraryIndexRepository.librarySnapshot(sourceId, VoiceArtistScanLimit, 0)
        .artists
        .mapNotNull { artist ->
            val score = voiceArtistMatchScore(queryKey, artist.name.voiceSearchKey())
            if (score == null) null else artist to score
        }
        .sortedWith(compareBy<Pair<Artist, Int>> { it.second }.thenBy { it.first.name.length })
        .firstOrNull()
        ?.first
}

private fun voiceArtistMatchScore(queryKey: String, artistKey: String): Int? =
    when {
        artistKey == queryKey -> 0
        artistKey.startsWith(queryKey) || queryKey.startsWith(artistKey) -> 1
        artistKey.contains(queryKey) || queryKey.contains(artistKey) -> 2
        else -> null
    }

private fun String.voiceSearchKey(): String =
    lowercase()
        .replace("&", "and")
        .replace("ph", "f")
        .replace(Regex("\\b(the|a|an)\\b"), " ")
        .filter { it.isLetterOrDigit() }

private fun MediaSourceRepository.savedCoverArtUrl(track: Track): String? {
    val coverArtId = track.coverArtId ?: track.albumId?.value ?: return null
    val connection = latestMediaSource()?.toNavidromeConnection() ?: return null
    return NavidromeProvider(connection).coverArtUrl(coverArtId)
}

private fun MediaSourceRepository.savedCoverArtUrl(album: Album): String? {
    val coverArtId = album.coverArtId ?: album.id.value
    val connection = latestMediaSource()?.toNavidromeConnection() ?: return null
    return NavidromeProvider(connection).coverArtUrl(coverArtId)
}
