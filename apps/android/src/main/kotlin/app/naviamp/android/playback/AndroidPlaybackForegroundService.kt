package app.naviamp.android.playback

import android.app.Notification
import android.app.NotificationChannel
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
import androidx.media.utils.MediaConstants
import app.naviamp.android.AndroidPlaybackHistoryItem
import app.naviamp.android.AndroidStorage
import app.naviamp.android.AndroidSettingsStore
import app.naviamp.android.AndroidPlaybackAudioAssets
import app.naviamp.android.R
import app.naviamp.android.MainActivity
import app.naviamp.android.resolveInternetRadioStreamUrl
import app.naviamp.domain.Album
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistId
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.StreamQuality
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
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.hasPendingSeekReachedTarget
import app.naviamp.domain.playback.playbackStreamUrl
import app.naviamp.domain.playback.resolvePlaybackAudioSource
import app.naviamp.domain.playback.shouldIgnoreProgressForPendingSeek
import app.naviamp.domain.provider.AlbumListType
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode
import app.naviamp.domain.network.KtorSharedHttpClient
import app.naviamp.domain.radio.RadioService
import app.naviamp.domain.radio.recentRadioStreamsWith
import app.naviamp.domain.radio.recentSavedInternetRadioStationsWith
import app.naviamp.domain.settings.PlaybackSessionSettings
import app.naviamp.domain.settings.RecentRadioKind
import app.naviamp.domain.settings.RecentRadioStream
import app.naviamp.domain.settings.SavedTrack
import app.naviamp.domain.settings.adjacentTrackSession
import app.naviamp.domain.settings.playbackSessionFromQueue
import app.naviamp.domain.settings.restoredTrackSession
import app.naviamp.domain.settings.withPlaybackPosition
import app.naviamp.provider.navidrome.NavidromeProvider
import app.naviamp.provider.navidrome.toNavidromeConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.concurrent.thread

private const val VoiceArtistScanLimit = 5_000L
class AndroidPlaybackForegroundService : MediaBrowserServiceCompat() {
    private var mediaSession: MediaSessionCompat? = null
    private var browserSessionTokenSet = false
    private var serviceStorageInstance: AndroidStorage? = null
    private val notificationArtHttpClient = KtorSharedHttpClient()
    private val serviceStorage: AndroidStorage
        get() = serviceStorageInstance ?: AndroidStorage(applicationContext).also { serviceStorageInstance = it }
    private val autoQueueController = PlaybackQueueController()

    private fun providerResponseService(cacheRepository: ProviderResponseCacheRepository = serviceStorage): ProviderResponseService =
        ProviderResponseService(cacheRepository)

    private fun recentPlaybackHistoryItems(
        playbackHistoryRepository: PlaybackHistoryRepository<AndroidPlaybackHistoryItem>,
        sourceId: String,
    ): List<MediaBrowserCompat.MediaItem> =
        playbackHistoryRepository.playbackHistory(sourceId, AndroidAutoBrowseLimit)
            .map { history -> trackItem(history.track) }

    private val noisyAudioReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != AudioManager.ACTION_AUDIO_BECOMING_NOISY) return
            if (!AndroidPlaybackNotificationControls.isPlaying) return
            AndroidPlaybackNotificationControls.onPlayPause?.invoke()
                ?: launchMainActivityForAutoCommand(AndroidAutoPlaybackControls.CommandPlayPause)
            refreshNotification(null)
        }
    }

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        ensureMediaSession()
        hydrateSavedPlaybackSession()
        registerReceiver(noisyAudioReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
    }

    override fun onDestroy() {
        pauseServiceOwnedPlayback("service destroyed")
        runCatching { unregisterReceiver(noisyAudioReceiver) }
        mediaSession?.release()
        mediaSession = null
        browserSessionTokenSet = false
        serviceStorageInstance?.close()
        serviceStorageInstance = null
        super.onDestroy()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        pauseServiceOwnedPlayback("Android Auto browser unbound")
        return super.onUnbind(intent)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopPlaybackAndService("task removed")
        super.onTaskRemoved(rootIntent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ActionPlayPause -> {
                AndroidPlaybackNotificationControls.onPlayPause?.invoke()
                    ?: handleServiceAutoPlayPause()
                refreshNotification(intent)
                return START_STICKY
            }
            ActionPrevious -> {
                if (!playServiceOwnedAdjacent(-1)) {
                    AndroidPlaybackNotificationControls.onPrevious?.invoke()
                        ?: playSavedSessionAdjacent(-1)
                }
                refreshNotification(intent)
                return START_STICKY
            }
            ActionNext -> {
                if (!playServiceOwnedAdjacent(1)) {
                    AndroidPlaybackNotificationControls.onNext?.invoke()
                        ?: playSavedSessionAdjacent(1)
                }
                refreshNotification(intent)
                return START_STICKY
            }
            ActionFavorite -> {
                if (AndroidPlaybackNotificationControls.canFavorite) {
                    AndroidPlaybackNotificationControls.isFavorite = !AndroidPlaybackNotificationControls.isFavorite
                    AndroidPlaybackNotificationControls.onToggleFavorite?.invoke()
                    refreshNotification(intent)
                }
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
                AndroidPlaybackNotificationControls.positionMillis =
                    intent.getLongExtra(ExtraPositionMillis, -1L).takeIf { it >= 0L }
                AndroidPlaybackNotificationControls.durationMillis =
                    intent.getLongExtra(ExtraDurationMillis, -1L).takeIf { it > 0L }
                if (currentMediaSessionDurationMillis != AndroidPlaybackNotificationControls.durationMillis) {
                    updateMediaSession(currentMetadata, currentLargeIcon)
                } else {
                    updateMediaSessionPlaybackState()
                }
                return START_STICKY
            }
            else -> {
                ensureNotificationChannel()
                val metadata = intent.toMetadata()
                startForeground(NotificationId, buildNotification(metadata, largeIcon = null))
                metadata.coverArtUrl?.let { coverArtUrl ->
                    loadCoverArtAsync(coverArtUrl, metadata)
                }
                return START_STICKY
            }
        }
    }

    private fun refreshNotification(intent: Intent?) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NotificationId, buildNotification(intent.toMetadata(), largeIcon = null))
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
        thread(name = "naviamp-notification-art") {
            val bitmap = runCatching {
                runBlocking {
                    notificationCoverArtBytes(coverArtUrl)
                        ?.let { decodeSampledBitmap(it, NotificationCoverArtSidePx) }
                }
            }.getOrNull() ?: return@thread
            if (currentMetadata.coverArtUrl != coverArtUrl) return@thread
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NotificationId, buildNotification(metadata, largeIcon = bitmap))
        }
    }

    private suspend fun notificationCoverArtBytes(url: String): ByteArray? {
        val provider = serviceStorage.latestNavidromeSource()
            ?.toNavidromeConnection()
            ?.let(::NavidromeProvider)
        return provider
            ?.takeIf { it.ownsUrl(url) }
            ?.bytes(url)
            ?: notificationArtHttpClient.getBytes(url)
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

    private fun launchMainActivityForAutoCommand(command: String) {
        Log.i("NaviampAutoCommand", "Launching phone app for Auto command=$command")
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .putExtra(MainActivity.ExtraOpenNowPlaying, true)
                    .putExtra(MainActivity.ExtraAutoCommand, command)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            )
        }.onFailure { error ->
            Log.w("NaviampAutoCommand", "Could not launch phone app for command=$command", error)
        }
    }

    private fun handleServiceAutoPlayPause() {
        if (AndroidPlaybackNotificationControls.isPlaying) {
            pauseServiceOwnedPlayback("Auto pause")
            return
        }
        playSavedSession()
    }

    private fun pauseServiceOwnedPlayback(reason: String) {
        if (!serviceOwnedPlayback) return
        Log.i("NaviampAutoCommand", "Pausing service-owned playback: $reason")
        runCatching { AndroidPlaybackRuntime.get(applicationContext).playbackEngine.pause() }
        AndroidPlaybackNotificationControls.isPlaying = false
        updateMediaSessionPlaybackState()
    }

    private fun stopServiceOwnedPlayback(reason: String) {
        Log.i("NaviampAutoCommand", "Stopping service-owned playback: $reason")
        runCatching { AndroidPlaybackRuntime.get(applicationContext).playbackEngine.stop() }
        AndroidPlaybackNotificationControls.isPlaying = false
        serviceOwnedPlayback = false
        updateMediaSessionPlaybackState()
    }

    private fun stopPlaybackForUserRequest(reason: String) {
        AndroidPlaybackNotificationControls.onStop?.invoke()
            ?: stopServiceOwnedPlayback(reason)
    }

    private fun stopPlaybackAndService(reason: String) {
        stopPlaybackForUserRequest(reason)
        stopForeground(STOP_FOREGROUND_REMOVE)
        mediaSession?.isActive = false
        stopSelf()
    }

    private fun seekServiceOwnedPlayback(positionMillis: Long) {
        val currentPositionSeconds = AndroidPlaybackNotificationControls.positionMillis
            ?.let { it / 1_000.0 }
            ?: 0.0
        if (positionMillis == 0L && currentPositionSeconds > ServiceIgnoreZeroSeekAfterSeconds) {
            Log.i(
                "NaviampAutoCommand",
                "Ignoring service zero seek while currentPosition=$currentPositionSeconds",
            )
            updateMediaSessionPlaybackState()
            return
        }
        val positionSeconds = (positionMillis.coerceAtLeast(0L) / 1_000.0)
        Log.i("NaviampAutoCommand", "Service seek requested seconds=$positionSeconds")
        pendingServiceSeekPositionSeconds = positionSeconds
        pendingServiceSeekAtMillis = System.currentTimeMillis()
        AndroidPlaybackNotificationControls.positionMillis = positionMillis.coerceAtLeast(0L)
        AndroidPlaybackRuntime.get(applicationContext).playbackEngine.seek(positionSeconds)
        saveServicePlaybackPosition(positionSeconds)
        updateMediaSessionPlaybackState()
    }

    private fun saveServicePlaybackPosition(positionSeconds: Double) {
        val storage = serviceStorage
        val sourceId = storage.latestNavidromeSource()?.id ?: return
        val session = storage.loadPlaybackSession(sourceId) ?: return
        storage.savePlaybackSession(
            sourceId,
            session.withPlaybackPosition(positionSeconds),
        )
    }

    private fun playSavedSessionAdjacent(delta: Int) {
        val storage = serviceStorage
        val sourceId = storage.latestNavidromeSource()?.id ?: return
        val session = storage.loadPlaybackSession(sourceId) ?: return
        val nextSession = session.adjacentTrackSession(delta) ?: return
        storage.savePlaybackSession(sourceId, nextSession)
        playSavedSession(nextSession)
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

    private fun playServiceOwnedAdjacent(delta: Int): Boolean {
        if (!serviceOwnedPlayback) return false
        autoQueueController.replaceQueue(PlaybackQueue(currentAutoQueue, currentAutoQueueIndex))
        autoQueueController.setRepeatMode(serviceRepeatModeForQueue())
        val selection = autoQueueController.adjacent(offset = delta)
        if (selection == null) {
            Log.i(
                "NaviampAutoCommand",
                "Service-owned queue has no adjacent track delta=$delta index=$currentAutoQueueIndex size=${currentAutoQueue.size}",
            )
            AndroidPlaybackNotificationControls.isPlaying = false
            updateMediaSessionPlaybackState()
            return true
        }
        val storage = serviceStorage
        val sourceId = storage.latestNavidromeSource()?.id ?: return false
        Log.i(
            "NaviampAutoCommand",
            "Service-owned queue advancing delta=$delta from=$currentAutoQueueIndex to=${selection.queue.currentIndex} size=${selection.queue.tracks.size}",
        )
        playServiceTrackQueue(storage, sourceId, selection.queue.tracks, selection.queue.currentIndex)
        return true
    }

    private fun handleServiceTrackFinished() {
        if (serviceRepeatMode == ServiceRepeatMode.One) {
            autoQueueController.replaceQueue(PlaybackQueue(currentAutoQueue, currentAutoQueueIndex))
            val selection = autoQueueController.playCurrent()
            if (selection != null) {
                val storage = serviceStorage
                val sourceId = storage.latestNavidromeSource()?.id ?: return
                playServiceTrackQueue(storage, sourceId, selection.queue.tracks, selection.queue.currentIndex)
                return
            }
        }
        playServiceOwnedAdjacent(1)
    }

    private fun playSavedSession(existingSession: PlaybackSessionSettings? = null) {
        val storage = serviceStorage
        val source = storage.latestNavidromeSource() ?: return
        val session = existingSession ?: storage.loadPlaybackSession(source.id) ?: return
        session.internetRadioStation?.let { station ->
            playServiceInternetRadioStation(storage, source.id, station.toStation())
            return
        }
        val restoredSession = session.restoredTrackSession() ?: return
        val track = restoredSession.currentTrack
        syncAutoQueue(PlaybackQueue(restoredSession.tracks, restoredSession.currentIndex))
        val connection = source.toNavidromeConnection()
        val provider = NavidromeProvider(connection)
        val runtime = AndroidPlaybackRuntime.get(applicationContext)
        val playbackSettings = AndroidSettingsStore(applicationContext).loadPlaybackSettings()
        val audioAssets = AndroidPlaybackAudioAssets(storage, storage)
        val quality = StreamQuality.Original
        val startPositionSeconds = session.positionSeconds?.takeIf { it > 0.0 }

        runtime.playbackEngine.applyTlsSettings(connection.tlsSettings)
        AndroidPlaybackNotificationControls.canFavorite = provider.capabilities.supportsTrackFavorites
        AndroidPlaybackNotificationControls.isFavorite = track.favoritedAtIso8601 != null
        AndroidPlaybackNotificationControls.isPlaying = true
        serviceOwnedPlayback = true
        lastServiceSessionSaveAtMillis = 0L
        lastServicePlaybackState = null
        AndroidPlaybackNotificationControls.positionMillis = startPositionSeconds
            ?.let { (it * 1_000.0).toLong() }
            ?: 0L
        AndroidPlaybackNotificationControls.durationMillis = track.durationSeconds
            ?.takeIf { it > 0 }
            ?.let { it * 1_000L }
        currentMetadata = AndroidPlaybackNotificationMetadata(
            title = track.title,
            subtitle = track.artistName,
            coverArtUrl = storage.savedCoverArtUrl(track),
        )
        currentMetadata.coverArtUrl?.let { loadCoverArtAsync(it, currentMetadata) }
        updateMediaSession(currentMetadata, currentLargeIcon)
        Log.i(
            "NaviampAutoCommand",
            "Service playing saved session source=${source.id} title=${track.title} position=$startPositionSeconds",
        )

        runtime.scope.launch {
            runCatching {
                val audioSourcePlan = resolvePlaybackAudioSource(
                    sourceId = source.id,
                    track = track,
                    quality = quality,
                    audioCachingEnabled = true,
                    startPositionSeconds = startPositionSeconds,
                    audioAssets = audioAssets,
                )
                val streamUrl = audioSourcePlan.playbackStreamUrl(
                    providerStreamUrl = { target -> provider.streamUrl(target.providerStreamRequest) },
                )
                streamUrl to audioSourcePlan.target.engineStartPositionSeconds
            }.onSuccess { playbackTarget ->
                runtime.playbackEngine.updateNotificationMetadata(
                    title = track.title,
                    subtitle = track.artistName,
                    coverArtUrl = storage.savedCoverArtUrl(track),
                )
                runtime.playbackEngine.play(
                    scope = runtime.scope,
                    request = PlaybackRequest(
                        url = playbackTarget.first,
                        mediaId = track.id.value,
                        replayGainMode = playbackSettings.replayGainMode,
                        startPositionSeconds = playbackTarget.second,
                    ),
                    onStateChanged = { state ->
                        if (state != lastServicePlaybackState) {
                            lastServicePlaybackState = state
                            AndroidPlaybackNotificationControls.isPlaying = state == PlaybackState.Playing
                            updateMediaSessionPlaybackState()
                        }
                        if (state == PlaybackState.Finished) {
                            handleServiceTrackFinished()
                        }
                    },
                    onProgressChanged = { progress ->
                        handleServicePlaybackProgress(storage, source.id, session, progress)
                    },
                )
            }.onFailure { error ->
                Log.w("NaviampAutoCommand", "Service saved-session playback failed", error)
                AndroidPlaybackNotificationControls.isPlaying = false
                updateMediaSessionPlaybackState()
            }
        }
    }

    private fun handleServicePlayMediaId(mediaId: String): Boolean {
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
                rememberRecentRadioStream(
                    RecentRadioStream(
                        id = AndroidAutoPlaybackControls.MediaIdRadioLibrary,
                        label = "Library Radio",
                        kind = RecentRadioKind.Library,
                    ),
                )
                AndroidPlaybackRuntime.get(applicationContext).scope.launch {
                    runCatching { withContext(Dispatchers.IO) { RadioService(provider).libraryRadio() } }
                        .onSuccess { tracks ->
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
            mediaId.startsWith(AndroidAutoPlaybackControls.MediaIdPlaylistPrefix) -> {
                val playlistId = Uri.decode(mediaId.removePrefix(AndroidAutoPlaybackControls.MediaIdPlaylistPrefix))
                val provider = NavidromeProvider(source.toNavidromeConnection())
                AndroidPlaybackRuntime.get(applicationContext).scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            providerResponseService(storage).playlistTracks(provider, playlistId)
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
            mediaId.startsWith(AndroidAutoPlaybackControls.MediaIdPlaylistTrackPrefix) -> {
                val parts = mediaId.removePrefix(AndroidAutoPlaybackControls.MediaIdPlaylistTrackPrefix).mediaIdParts()
                val playlistId = parts.getOrNull(0).orEmpty()
                val trackId = parts.getOrNull(1).orEmpty()
                if (playlistId.isBlank() || trackId.isBlank()) return false
                val provider = NavidromeProvider(source.toNavidromeConnection())
                AndroidPlaybackRuntime.get(applicationContext).scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            providerResponseService(storage).playlistTracks(provider, playlistId)
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
                playServiceInternetRadioStation(storage, sourceId, station)
                true
            }
            mediaId.startsWith(AndroidAutoPlaybackControls.MediaIdRecentRadioPrefix) -> {
                val recentId = Uri.decode(mediaId.removePrefix(AndroidAutoPlaybackControls.MediaIdRecentRadioPrefix))
                val settingsStore = AndroidSettingsStore(applicationContext)
                val recentStream = settingsStore.loadRecentRadioStreams().firstOrNull { it.id == recentId }
                if (recentStream != null) {
                    playServiceRecentRadioStream(storage, sourceId, recentStream)
                    return true
                }
                val station = settingsStore.loadRecentInternetRadioStations()
                    .firstOrNull { it.id == recentId }
                    ?.toStation()
                if (station != null) {
                    playServiceInternetRadioStation(storage, sourceId, station)
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
                AndroidPlaybackRuntime.get(applicationContext).scope.launch {
                    runCatching {
                        loadServiceArtistTracks(storage, storage, sourceId, provider, artistId, artistName)
                            .firstOrNull { it.id.value == trackId }
                            ?: storage.libraryTrack(sourceId, TrackId(trackId))
                    }.onSuccess { track ->
                        if (track != null) {
                            playServiceTrackRadio(storage, sourceId, provider, track)
                        } else {
                            AndroidPlaybackNotificationControls.isPlaying = false
                            updateMediaSessionPlaybackState()
                        }
                    }.onFailure { error ->
                        Log.w("NaviampAutoCommand", "Could not start Auto artist track radio=$trackId", error)
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
                AndroidPlaybackRuntime.get(applicationContext).scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            providerResponseService(storage)
                                .album(provider, AlbumId(albumId))
                                .tracks
                                .firstOrNull { it.id.value == trackId }
                        }
                    }
                        .onSuccess { track ->
                            if (track != null) {
                                playServiceTrackRadio(storage, sourceId, provider, track)
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
            mediaId.startsWith(AndroidAutoPlaybackControls.MediaIdArtistShufflePrefix) -> {
                val parts = mediaId.removePrefix(AndroidAutoPlaybackControls.MediaIdArtistShufflePrefix).mediaIdParts()
                val artistId = parts.getOrNull(0).orEmpty()
                val artistName = parts.getOrNull(1)
                if (artistId.isBlank() && artistName.isNullOrBlank()) return false
                val provider = NavidromeProvider(source.toNavidromeConnection())
                AndroidPlaybackRuntime.get(applicationContext).scope.launch {
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
            mediaId.startsWith(AndroidAutoPlaybackControls.MediaIdAlbumShufflePrefix) -> {
                val parts = mediaId.removePrefix(AndroidAutoPlaybackControls.MediaIdAlbumShufflePrefix).mediaIdParts()
                val albumId = parts.getOrNull(0).orEmpty()
                val albumTitle = parts.getOrNull(1)
                val albumArtist = parts.getOrNull(2)
                if (albumId.isBlank()) return false
                val provider = NavidromeProvider(source.toNavidromeConnection())
                AndroidPlaybackRuntime.get(applicationContext).scope.launch {
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
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank()) return false
        val storage = serviceStorage
        val source = storage.latestNavidromeSource() ?: return false
        val radioQuery = trimmedQuery.radioSearchQuery()
        if (radioQuery != null) {
            if (playServiceArtistRadioSearch(storage, source.id, radioQuery)) return true
            if (playServiceGenreRadioSearch(storage, source.id, radioQuery)) return true
        }
        val snapshot = storage.searchLibrary(source.id, trimmedQuery, AndroidAutoBrowseLimit.toLong(), 0)
        snapshot.tracks.firstOrNull()?.let { track ->
            val queue = serviceQueueForLibraryTrack(storage, source.id, track)
            playServiceTrackQueue(storage, source.id, queue, queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0))
            return true
        }
        snapshot.albums.firstOrNull()?.let { album ->
            val tracks = storage.libraryTracksForAlbum(source.id, album.id, 200)
                .ifEmpty { storage.libraryTracksForAlbumTitle(source.id, album.title, album.artistName, 200) }
            if (tracks.isNotEmpty()) {
                playServiceTrackQueue(storage, source.id, tracks, 0)
                return true
            }
        }
        snapshot.artists.firstOrNull()?.let { artist ->
            val tracks = storage.libraryTracksForArtist(source.id, artist.id, 200)
                .ifEmpty { storage.libraryTracksForArtistName(source.id, artist.name, 200) }
            if (tracks.isNotEmpty()) {
                playServiceTrackQueue(storage, source.id, tracks, 0)
                return true
            }
        }
        return false
    }

    private fun playServiceArtistRadioSearch(
        storage: AndroidStorage,
        sourceId: String,
        query: String,
    ): Boolean {
        val searchArtists = storage.searchLibrary(sourceId, query, AndroidAutoBrowseLimit.toLong(), 0).artists
        val artist = searchArtists.firstOrNull { it.name.equals(query, ignoreCase = true) }
            ?: searchArtists.firstOrNull()
            ?: findVoiceArtistMatch(storage, sourceId, query)
            ?: return false
        val source = storage.latestNavidromeSource() ?: return false
        val provider = NavidromeProvider(source.toNavidromeConnection())
        val recent = RecentRadioStream(
            id = "artist:${artist.id.value}",
            label = "${artist.name} Radio",
            kind = RecentRadioKind.Artist,
            artist = app.naviamp.domain.settings.SavedArtist.fromArtist(artist),
        )
        rememberRecentRadioStream(recent)
        AndroidPlaybackRuntime.get(applicationContext).scope.launch {
            runCatching { withContext(Dispatchers.IO) { RadioService(provider).artistRadio(artist.id) } }
                .onSuccess { tracks ->
                    playServiceTrackQueue(storage, sourceId, tracks, currentIndex = 0)
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
        storage: AndroidStorage,
        sourceId: String,
        query: String,
    ): Boolean {
        val source = storage.latestNavidromeSource() ?: return false
        val provider = NavidromeProvider(source.toNavidromeConnection())
        val recent = RecentRadioStream(
            id = "genre:${query.lowercase()}",
            label = "${query.replaceFirstChar { it.titlecase() }} Radio",
            kind = RecentRadioKind.Genre,
            genre = query,
        )
        rememberRecentRadioStream(recent)
        AndroidPlaybackRuntime.get(applicationContext).scope.launch {
            runCatching { withContext(Dispatchers.IO) { provider.randomSongs(genre = query) } }
                .onSuccess { tracks ->
                    if (tracks.isEmpty()) {
                        Log.i("NaviampAutoCommand", "No genre radio tracks for query=$query")
                        return@onSuccess
                    }
                    playServiceTrackQueue(storage, sourceId, tracks, currentIndex = 0)
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

    private fun playServiceAutoQueueItem(index: Int) {
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
                            val responseService = providerResponseService(providerResponseCacheRepository)
                            responseService.artist(provider, ArtistId(id)).albums.flatMap { album ->
                                responseService.album(provider, album.id).tracks
                            }
                        }
                    }.getOrDefault(emptyList())
                }.orEmpty()
            }

    private fun playServiceTrackRadio(
        storage: AndroidStorage,
        sourceId: String,
        provider: NavidromeProvider,
        seedTrack: Track,
    ) {
        rememberRecentRadioStream(
            RecentRadioStream(
                id = "track:${seedTrack.id.value}",
                label = "${seedTrack.title} Radio",
                kind = RecentRadioKind.Track,
                track = SavedTrack.fromTrack(seedTrack),
            ),
        )
        AndroidPlaybackRuntime.get(applicationContext).scope.launch {
            runCatching { withContext(Dispatchers.IO) { RadioService(provider).trackRadio(seedTrack.id) } }
                .onSuccess { tracks ->
                    playServiceTrackQueue(
                        playbackSessionRepository = storage,
                        sourceId = sourceId,
                        tracks = (listOf(seedTrack) + tracks).distinctBy { it.id },
                        currentIndex = 0,
                    )
                }
                .onFailure { error ->
                    Log.w("NaviampAutoCommand", "Could not start track radio=${seedTrack.id.value}", error)
                    playServiceTrackQueue(storage, sourceId, listOf(seedTrack), currentIndex = 0)
                }
        }
    }

    private fun playServiceRecentRadioStream(
        storage: AndroidStorage,
        sourceId: String,
        stream: RecentRadioStream,
    ) {
        rememberRecentRadioStream(stream)
        val source = storage.latestNavidromeSource() ?: return
        val provider = NavidromeProvider(source.toNavidromeConnection())
        AndroidPlaybackRuntime.get(applicationContext).scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    when (stream.kind) {
                        RecentRadioKind.Library -> RadioService(provider).libraryRadio()
                        RecentRadioKind.Artist -> stream.artist?.let { provider.artistRadio(ArtistId(it.id)) }.orEmpty()
                        RecentRadioKind.Album -> stream.album?.let { provider.albumRadio(AlbumId(it.id)) }.orEmpty()
                        RecentRadioKind.Track -> stream.track?.let { provider.trackRadio(TrackId(it.id)) }.orEmpty()
                        RecentRadioKind.Genre -> stream.genre?.let { provider.randomSongs(genre = it) }.orEmpty()
                        RecentRadioKind.Decade -> provider.randomSongs(
                            fromYear = stream.fromYear,
                            toYear = stream.toYear,
                        )
                        RecentRadioKind.RandomAlbum -> provider.randomSongs()
                    }
                }
            }.onSuccess { tracks ->
                playServiceTrackQueue(storage, sourceId, tracks, currentIndex = 0)
            }.onFailure { error ->
                Log.w("NaviampAutoCommand", "Could not start recent radio=${stream.label}", error)
                AndroidPlaybackNotificationControls.isPlaying = false
                updateMediaSessionPlaybackState()
            }
        }
    }

    private fun playServiceInternetRadioStation(
        storage: AndroidStorage,
        sourceId: String,
        station: InternetRadioStation,
    ) {
        rememberRecentInternetRadioStation(station)
        storage.savePlaybackSession(sourceId, PlaybackSessionSettings.fromInternetRadioStation(station))
        val runtime = AndroidPlaybackRuntime.get(applicationContext)
        runtime.playbackEngine.applyTlsSettings(storage.latestNavidromeSource()?.toNavidromeConnection()?.tlsSettings ?: return)
        AndroidPlaybackNotificationControls.canFavorite = false
        AndroidPlaybackNotificationControls.isFavorite = false
        AndroidPlaybackNotificationControls.isPlaying = true
        AndroidPlaybackNotificationControls.positionMillis = 0L
        AndroidPlaybackNotificationControls.durationMillis = null
        serviceOwnedPlayback = true
        syncAutoQueue(PlaybackQueue())
        currentMetadata = AndroidPlaybackNotificationMetadata(
            title = station.name,
            subtitle = "Internet radio",
            coverArtUrl = null,
        )
        updateMediaSession(currentMetadata, currentLargeIcon)
        runtime.scope.launch {
            runCatching { resolveInternetRadioStreamUrl(station.streamUrl.trim()) }
                .onSuccess { streamUrl ->
                    runtime.playbackEngine.updateNotificationMetadata(
                        title = station.name,
                        subtitle = "Internet radio",
                        coverArtUrl = null,
                    )
                    runtime.playbackEngine.play(
                        scope = runtime.scope,
                        request = PlaybackRequest(streamUrl),
                        onStateChanged = { state ->
                            if (state != lastServicePlaybackState) {
                                lastServicePlaybackState = state
                                AndroidPlaybackNotificationControls.isPlaying = state == PlaybackState.Playing
                                updateMediaSessionPlaybackState()
                            }
                        },
                        onProgressChanged = { progress ->
                            handleServicePlaybackProgress(storage, sourceId, PlaybackSessionSettings.fromInternetRadioStation(station), progress)
                        },
                        onMetadataChanged = { metadata ->
                            metadata.title?.takeIf { it.isNotBlank() }?.let { streamTitle ->
                                currentMetadata = currentMetadata.copy(title = streamTitle, subtitle = station.name)
                                runtime.playbackEngine.updateNotificationMetadata(
                                    title = streamTitle,
                                    subtitle = station.name,
                                    coverArtUrl = null,
                                )
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
    }

    private fun rememberRecentInternetRadioStation(station: InternetRadioStation) {
        val settingsStore = AndroidSettingsStore(applicationContext)
        settingsStore.saveRecentInternetRadioStations(
            recentSavedInternetRadioStationsWith(
                settingsStore.loadRecentInternetRadioStations(),
                station,
            ),
        )
    }

    private fun handleServicePlaybackProgress(
        storage: AndroidStorage,
        sourceId: String,
        session: PlaybackSessionSettings,
        progress: PlaybackProgress,
    ) {
        val now = System.currentTimeMillis()
        val progressPositionSeconds = progress.positionSeconds
        if (progressPositionSeconds == null && progress.durationSeconds == null) return
        val pendingSeekPosition = pendingServiceSeekPositionSeconds
        if (
            shouldIgnoreProgressForPendingSeek(
                pendingSeekPositionSeconds = pendingSeekPosition,
                pendingSeekIssuedAtMillis = pendingServiceSeekAtMillis,
                incomingPositionSeconds = progressPositionSeconds,
                nowMillis = now,
                toleranceSeconds = ServiceSeekToleranceSeconds,
                staleWindowMillis = ServiceSeekStaleProgressWindowMillis,
            )
        ) {
            return
        }
        if (
            hasPendingSeekReachedTarget(
                pendingSeekPositionSeconds = pendingSeekPosition,
                incomingPositionSeconds = progressPositionSeconds,
                toleranceSeconds = ServiceSeekToleranceSeconds,
            )
        ) {
            pendingServiceSeekPositionSeconds = null
        }
        val positionMillis = progressPositionSeconds
            ?.takeIf { it >= 0.0 }
            ?.let { (it * 1_000.0).toLong() }
        val durationMillis = progress.durationSeconds
            ?.takeIf { it > 0.0 }
            ?.let { (it * 1_000.0).toLong() }
        AndroidPlaybackNotificationControls.positionMillis = positionMillis
        AndroidPlaybackNotificationControls.durationMillis = durationMillis
        if (now - lastServiceSessionSaveAtMillis >= ServicePlaybackSessionSaveIntervalMillis) {
            lastServiceSessionSaveAtMillis = now
            storage.savePlaybackSession(
                sourceId,
                session.withPlaybackPosition(progressPositionSeconds),
            )
        }
    }

    private fun ensureMediaSession(): MediaSessionCompat =
        mediaSession ?: MediaSessionCompat(this, "NaviampPlayback").apply {
            setCallback(
                object : MediaSessionCompat.Callback() {
                    override fun onPlay() {
                        if (!AndroidPlaybackNotificationControls.isPlaying) {
                            AndroidPlaybackNotificationControls.onPlayPause?.invoke()
                                ?: handleServiceAutoPlayPause()
                            refreshNotification(null)
                        }
                    }

                    override fun onPause() {
                        if (AndroidPlaybackNotificationControls.isPlaying) {
                            AndroidPlaybackNotificationControls.onPlayPause?.invoke()
                                ?: handleServiceAutoPlayPause()
                            refreshNotification(null)
                        }
                    }

                    override fun onSkipToPrevious() {
                        if (!playServiceOwnedAdjacent(-1)) {
                            AndroidPlaybackNotificationControls.onPrevious?.invoke()
                                ?: playSavedSessionAdjacent(-1)
                        }
                        refreshNotification(null)
                    }

                    override fun onSkipToNext() {
                        if (!playServiceOwnedAdjacent(1)) {
                            AndroidPlaybackNotificationControls.onNext?.invoke()
                                ?: playSavedSessionAdjacent(1)
                        }
                        refreshNotification(null)
                    }

                    override fun onSkipToQueueItem(id: Long) {
                        playServiceAutoQueueItem(id.toInt())
                        refreshNotification(null)
                    }

                    override fun onStop() {
                        AndroidPlaybackNotificationControls.onStop?.invoke()
                            ?: stopServiceOwnedPlayback("media session stop")
                    }

                    override fun onSeekTo(pos: Long) {
                        val seekCallback = AndroidPlaybackNotificationControls.onSeekTo
                        if (seekCallback != null) {
                            seekCallback(pos)
                            AndroidPlaybackNotificationControls.positionMillis = pos.coerceAtLeast(0L)
                            updateMediaSessionPlaybackState()
                        } else {
                            seekServiceOwnedPlayback(pos)
                        }
                    }

                    override fun onPlayFromMediaId(mediaId: String, extras: Bundle?) {
                        Log.i("NaviampAutoCommand", "Auto requested mediaId=$mediaId")
                        if (mediaId == AndroidAutoPlaybackControls.MediaIdNowPlaying && !AndroidPlaybackNotificationControls.isPlaying) {
                            handleServiceAutoPlayPause()
                            refreshNotification(null)
                            return
                        }
                        if (handleServicePlayMediaId(mediaId)) {
                            return
                        }
                        val handledInProcess = AndroidAutoPlaybackControls.onPlayMediaId?.let { handler ->
                            handler(mediaId)
                            true
                        } ?: false
                        if (!handledInProcess) {
                            launchMainActivityForAutoMediaId(mediaId)
                        }
                    }

                    override fun onPlayFromSearch(query: String, extras: Bundle?) {
                        Log.i("NaviampAutoCommand", "Auto requested search=$query")
                        if (!handleServicePlaySearch(query)) {
                            launchMainActivityForAutoCommand(AndroidAutoPlaybackControls.CommandPlayPause)
                        }
                    }

                    override fun onCustomAction(action: String, extras: android.os.Bundle?) {
                        when (action) {
                            ActionFavorite -> if (AndroidPlaybackNotificationControls.canFavorite) {
                                AndroidPlaybackNotificationControls.isFavorite =
                                    !AndroidPlaybackNotificationControls.isFavorite
                                AndroidPlaybackNotificationControls.onToggleFavorite?.invoke()
                                refreshNotification(null)
                            }
                            ActionShuffle -> {
                                toggleServiceShuffle()
                                refreshNotification(null)
                            }
                            ActionRepeat -> {
                                serviceRepeatMode = serviceRepeatMode.next()
                                updateMediaSessionPlaybackState()
                                refreshNotification(null)
                            }
                        }
                    }
                },
            )
            isActive = true
            if (!browserSessionTokenSet) {
                setSessionToken(sessionToken)
                browserSessionTokenSet = true
            }
            this@AndroidPlaybackForegroundService.mediaSession = this
        }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?,
    ): BrowserRoot {
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
        Log.i("NaviampAutoCommand", "Loading Auto children parent=$parentId")
        val storage = serviceStorage
        val sourceId = storage.latestNavidromeSource()?.id
        if (parentId == AndroidAutoPlaybackControls.MediaIdRadioStations) {
            loadAsyncChildren(result) {
                val source = storage.latestNavidromeSource() ?: return@loadAsyncChildren noSourceItems()
                val provider = NavidromeProvider(source.toNavidromeConnection())
                withContext(Dispatchers.IO) {
                    providerResponseService(storage).internetRadioStations(provider)
                }
                    .take(AndroidAutoBrowseLimit)
                    .map { station ->
                        playableItem(
                            mediaId = AndroidAutoPlaybackControls.MediaIdRadioStationPrefix + listOf(
                                Uri.encode(station.id),
                                Uri.encode(station.name),
                                Uri.encode(station.streamUrl),
                                Uri.encode(station.homePageUrl.orEmpty()),
                            ).joinToString(MediaIdPartSeparator),
                            title = station.name,
                            subtitle = station.homePageUrl ?: "Internet radio",
                        )
                    }
                    .toMutableList()
            }
            return
        }
        if (parentId == AndroidAutoPlaybackControls.MediaIdRadioRecent) {
            val settingsStore = AndroidSettingsStore(applicationContext)
            val recentStreams = settingsStore.loadRecentRadioStreams().map { stream ->
                playableItem(
                    mediaId = "${AndroidAutoPlaybackControls.MediaIdRecentRadioPrefix}${Uri.encode(stream.id)}",
                    title = stream.label,
                    subtitle = "Radio",
                )
            }
            val recentStations = settingsStore.loadRecentInternetRadioStations().map { station ->
                playableItem(
                    mediaId = "${AndroidAutoPlaybackControls.MediaIdRecentRadioPrefix}${Uri.encode(station.id)}",
                    title = station.name,
                    subtitle = station.homePageUrl ?: "Internet radio",
                )
            }
            sendChildren(parentId, (recentStreams + recentStations).toMutableList(), result)
            return
        }
        if (parentId == AndroidAutoPlaybackControls.MediaIdQueue) {
            val queue = currentAutoQueue
            val children = queue.mapIndexed { index, track ->
                trackItem(
                    track = track,
                    mediaId = "${AndroidAutoPlaybackControls.MediaIdQueueTrackPrefix}${Uri.encode(index.toString())}",
                )
            }.toMutableList()
            sendChildren(parentId, children, result)
            return
        }
        if (parentId == AndroidAutoPlaybackControls.MediaIdPlaylists) {
            loadAsyncChildren(result) {
                val source = storage.latestNavidromeSource() ?: return@loadAsyncChildren noSourceItems()
                val provider = NavidromeProvider(source.toNavidromeConnection())
                withContext(Dispatchers.IO) {
                    providerResponseService(storage).playlists(provider, AndroidAutoBrowseLimit)
                }
                    .map { playlist ->
                        browsableItem(
                            mediaId = "${AndroidAutoPlaybackControls.MediaIdPlaylistPrefix}${Uri.encode(playlist.id)}",
                            title = playlist.name,
                            subtitle = "${playlist.trackCount} tracks",
                        )
                    }
                    .toMutableList()
            }
            return
        }
        if (parentId == AndroidAutoPlaybackControls.MediaIdHomeRecentlyAdded) {
            loadAsyncChildren(result) {
                val source = storage.latestNavidromeSource() ?: return@loadAsyncChildren noSourceItems()
                val provider = NavidromeProvider(source.toNavidromeConnection())
                withContext(Dispatchers.IO) {
                    providerResponseService(storage).albumList(provider, AlbumListType.Newest, AndroidAutoBrowseLimit)
                }
                    .map(::albumItem)
                    .toMutableList()
            }
            return
        }
        if (parentId == AndroidAutoPlaybackControls.MediaIdChartsAlbums) {
            loadAsyncChildren(result) {
                val source = storage.latestNavidromeSource() ?: return@loadAsyncChildren noSourceItems()
                val provider = NavidromeProvider(source.toNavidromeConnection())
                withContext(Dispatchers.IO) {
                    providerResponseService(storage).albumList(provider, AlbumListType.Frequent, AndroidAutoBrowseLimit)
                }
                    .map(::albumItem)
                    .toMutableList()
            }
            return
        }
        val children = when (parentId) {
            AndroidAutoPlaybackControls.MediaIdRoot -> mutableListOf(
                browsableItem(AndroidAutoPlaybackControls.MediaIdHome, "Home", "Mixes and recent music", iconName = "ic_auto_home"),
                browsableItem(AndroidAutoPlaybackControls.MediaIdLibrary, "Library", "Browse your collection", iconName = "ic_auto_library"),
                browsableItem(AndroidAutoPlaybackControls.MediaIdCharts, "Charts", "Top artists, albums, and tracks", iconName = "ic_auto_charts"),
                browsableItem(AndroidAutoPlaybackControls.MediaIdRadioStations, "Radio", "Saved internet radio", iconName = "ic_auto_radio"),
            )
            AndroidAutoPlaybackControls.MediaIdHome -> mutableListOf(
                browsableItem(AndroidAutoPlaybackControls.MediaIdHomeMixes, "Mixes For You", "Radio based on your library"),
                browsableItem(AndroidAutoPlaybackControls.MediaIdHomeRecentPlays, "Recent Plays", "Recently played tracks and radio"),
                browsableItem(AndroidAutoPlaybackControls.MediaIdHomeRecentlyAdded, "Recently Added in Music", "Newest albums"),
            )
            AndroidAutoPlaybackControls.MediaIdLibrary -> mutableListOf(
                browsableItem(AndroidAutoPlaybackControls.MediaIdLibraryArtists, "Artists A-Z", "Browse indexed artists"),
                browsableItem(AndroidAutoPlaybackControls.MediaIdLibraryAlbums, "Albums", "Browse indexed albums"),
                browsableItem(AndroidAutoPlaybackControls.MediaIdLibraryTracks, "Tracks", "Browse indexed tracks"),
            )
            AndroidAutoPlaybackControls.MediaIdLibraryArtists -> {
                sourceId?.let { id ->
                    storage.librarySnapshot(id, VoiceArtistScanLimit, 0)
                        .artists
                        .groupBy { it.name.autoArtistGroupKey() }
                        .toSortedMap(compareBy<String> { if (it == "#") "0" else it })
                        .map { (group, artists) ->
                            browsableItem(
                                mediaId = "${AndroidAutoPlaybackControls.MediaIdArtistGroupPrefix}${Uri.encode(group)}",
                                title = group,
                                subtitle = "${artists.size} artists",
                            )
                        }
                        .toMutableList()
                } ?: noSourceItems()
            }
            AndroidAutoPlaybackControls.MediaIdLibraryAlbums -> {
                sourceId?.let { id ->
                    storage.librarySnapshot(id, AndroidAutoBrowseLimit.toLong(), 0).albums.map(::albumItem).toMutableList()
                } ?: noSourceItems()
            }
            AndroidAutoPlaybackControls.MediaIdLibraryTracks -> {
                sourceId?.let { id ->
                    storage.librarySnapshot(id, AndroidAutoBrowseLimit.toLong(), 0).tracks.map(::trackItem).toMutableList()
                } ?: noSourceItems()
            }
            AndroidAutoPlaybackControls.MediaIdRadio -> mutableListOf(
                playableItem(AndroidAutoPlaybackControls.MediaIdRadioLibrary, "Library Radio", "Random tracks from your library"),
                browsableItem(AndroidAutoPlaybackControls.MediaIdRadioRecent, "Recently Played Radio", "Radio you started from Naviamp"),
                browsableItem(AndroidAutoPlaybackControls.MediaIdRadioStations, "Internet Radio", "Saved Navidrome stations"),
            )
            AndroidAutoPlaybackControls.MediaIdHomeMixes -> mutableListOf(
                playableItem(AndroidAutoPlaybackControls.MediaIdRadioLibrary, "Library Radio", "Random tracks from your library"),
                browsableItem(AndroidAutoPlaybackControls.MediaIdRadioRecent, "Recently Played Radio", "Radio you started from Naviamp"),
            )
            AndroidAutoPlaybackControls.MediaIdHomeRecentPlays -> {
                sourceId?.let { id ->
                    val settingsStore = AndroidSettingsStore(applicationContext)
                    val recentStreams = settingsStore.loadRecentRadioStreams().map { stream ->
                        playableItem(
                            mediaId = "${AndroidAutoPlaybackControls.MediaIdRecentRadioPrefix}${Uri.encode(stream.id)}",
                            title = stream.label,
                            subtitle = "Radio",
                        )
                    }
                    val recentTracks = recentPlaybackHistoryItems(storage, id)
                    (recentStreams + recentTracks).take(AndroidAutoBrowseLimit).toMutableList()
                } ?: noSourceItems()
            }
            AndroidAutoPlaybackControls.MediaIdCharts -> mutableListOf(
                browsableItem(AndroidAutoPlaybackControls.MediaIdChartsArtists, "Top Artists", "Library favorites"),
                browsableItem(AndroidAutoPlaybackControls.MediaIdChartsAlbums, "Top Albums", "Frequently played albums"),
                browsableItem(AndroidAutoPlaybackControls.MediaIdChartsTracks, "Top Tracks", "Recently played tracks"),
            )
            AndroidAutoPlaybackControls.MediaIdChartsArtists -> {
                sourceId?.let { id ->
                    storage.librarySnapshot(id, AndroidAutoBrowseLimit.toLong(), 0).artists.map(::artistItem).toMutableList()
                } ?: noSourceItems()
            }
            AndroidAutoPlaybackControls.MediaIdChartsTracks -> {
                sourceId?.let { id ->
                    recentPlaybackHistoryItems(storage, id).toMutableList()
                } ?: noSourceItems()
            }
            AndroidAutoPlaybackControls.MediaIdDownloads -> {
                sourceId?.let { id ->
                    storage.downloadedTracks(id)
                        .filter { it.file.exists() }
                        .take(AndroidAutoBrowseLimit)
                        .map { download ->
                            trackItem(
                                track = download.track,
                                mediaId = "${AndroidAutoPlaybackControls.MediaIdDownloadPrefix}${Uri.encode(download.track.id.value)}",
                            )
                        }
                        .toMutableList()
                } ?: noSourceItems()
            }
            AndroidAutoPlaybackControls.MediaIdMore -> mutableListOf(
                currentNowPlayingItem(),
                browsableItem(AndroidAutoPlaybackControls.MediaIdQueue, "Current queue", "${currentAutoQueue.size} tracks"),
                browsableItem(AndroidAutoPlaybackControls.MediaIdPlaylists, "Playlists", "Saved Navidrome playlists"),
                browsableItem(AndroidAutoPlaybackControls.MediaIdRadioStations, "Internet Radio", "Saved Navidrome stations"),
                browsableItem(AndroidAutoPlaybackControls.MediaIdLibraryTracks, "All Tracks", "Browse indexed tracks"),
            )
            else -> when {
                parentId.startsWith(AndroidAutoPlaybackControls.MediaIdArtistGroupPrefix) -> {
                    val group = Uri.decode(parentId.removePrefix(AndroidAutoPlaybackControls.MediaIdArtistGroupPrefix))
                    sourceId?.let { id ->
                        storage.librarySnapshot(id, VoiceArtistScanLimit, 0)
                            .artists
                            .filter { it.name.autoArtistGroupKey() == group }
                            .take(AndroidAutoBrowseLimit)
                            .map(::artistItem)
                            .toMutableList()
                    } ?: noSourceItems()
                }
                parentId.startsWith(AndroidAutoPlaybackControls.MediaIdPlaylistPrefix) -> {
                    val playlistId = Uri.decode(parentId.removePrefix(AndroidAutoPlaybackControls.MediaIdPlaylistPrefix))
                    loadAsyncChildren(result) {
                        val source = storage.latestNavidromeSource() ?: return@loadAsyncChildren noSourceItems()
                        val provider = NavidromeProvider(source.toNavidromeConnection())
                        withContext(Dispatchers.IO) {
                            providerResponseService(storage).playlistTracks(provider, playlistId)
                        }
                            .take(AndroidAutoBrowseLimit)
                            .map { track ->
                                trackItem(
                                    track = track,
                                    mediaId = AndroidAutoPlaybackControls.MediaIdPlaylistTrackPrefix + listOf(
                                        Uri.encode(playlistId),
                                        Uri.encode(track.id.value),
                                    ).joinToString(MediaIdPartSeparator),
                                )
                            }
                            .toMutableList()
                    }
                    return
                }
                parentId.startsWith(AndroidAutoPlaybackControls.MediaIdArtistPrefix) -> {
                    val parts = parentId.removePrefix(AndroidAutoPlaybackControls.MediaIdArtistPrefix).mediaIdParts()
                    val artistId = parts.getOrNull(0).orEmpty()
                    val artistName = parts.getOrNull(1)
                    loadAsyncChildren(result) {
                        val source = storage.latestNavidromeSource() ?: return@loadAsyncChildren noSourceItems()
                        val provider = NavidromeProvider(source.toNavidromeConnection())
                        val tracks = loadServiceArtistTracks(storage, storage, source.id, provider, artistId, artistName)
                        (
                            listOf(
                                playableItem(
                                    mediaId = AndroidAutoPlaybackControls.MediaIdArtistShufflePrefix + listOf(
                                        Uri.encode(artistId),
                                        Uri.encode(artistName.orEmpty()),
                                    ).joinToString(MediaIdPartSeparator),
                                    title = "Shuffle",
                                    subtitle = "Shuffle ${artistName ?: "artist"}",
                                    iconUri = autoDrawableUri("ic_shuffle_24"),
                                ),
                            ) + tracks.take(AndroidAutoBrowseLimit).map { track ->
                                trackItem(
                                    track = track,
                                    mediaId = AndroidAutoPlaybackControls.MediaIdArtistTrackPrefix + listOf(
                                        Uri.encode(artistId),
                                        Uri.encode(artistName.orEmpty()),
                                        Uri.encode(track.id.value),
                                    ).joinToString(MediaIdPartSeparator),
                                )
                            }
                        ).toMutableList()
                    }
                    return
                }
                parentId.startsWith(AndroidAutoPlaybackControls.MediaIdAlbumPrefix) -> {
                    val parts = parentId.removePrefix(AndroidAutoPlaybackControls.MediaIdAlbumPrefix).mediaIdParts()
                    val albumId = parts.getOrNull(0).orEmpty()
                    val albumTitle = parts.getOrNull(1)
                    val albumArtist = parts.getOrNull(2)
                    loadAsyncChildren(result) {
                        val source = storage.latestNavidromeSource() ?: return@loadAsyncChildren noSourceItems()
                        val provider = NavidromeProvider(source.toNavidromeConnection())
                        val tracks = loadServiceAlbumTracks(storage, storage, source.id, provider, albumId, albumTitle, albumArtist)
                        (
                            listOf(
                                playableItem(
                                    mediaId = AndroidAutoPlaybackControls.MediaIdAlbumShufflePrefix + listOf(
                                        Uri.encode(albumId),
                                        Uri.encode(albumTitle.orEmpty()),
                                        Uri.encode(albumArtist.orEmpty()),
                                    ).joinToString(MediaIdPartSeparator),
                                    title = "Shuffle",
                                    subtitle = "Shuffle ${albumTitle ?: "album"}",
                                    iconUri = autoDrawableUri("ic_shuffle_24"),
                                ),
                            ) + tracks.take(AndroidAutoBrowseLimit).map { track ->
                            trackItem(
                                track = track,
                                mediaId = AndroidAutoPlaybackControls.MediaIdAlbumTrackPrefix + listOf(
                                    Uri.encode(albumId),
                                    Uri.encode(track.id.value),
                                ).joinToString(MediaIdPartSeparator),
                            )
                        }).toMutableList()
                    }
                    return
                }
                else -> mutableListOf()
            }
        }
        Log.i("NaviampAutoCommand", "Loaded Auto children parent=$parentId count=${children.size}")
        sendChildren(parentId, children, result)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>,
        options: Bundle,
    ) {
        hydrateSavedPlaybackSession()
        Log.i("NaviampAutoCommand", "Loading Auto children parent=$parentId options=${options.debugDescription()}")
        options.autoSearchQuery()?.let { query ->
            loadAsyncChildren(result) { autoSearchResults(query) }
            return
        }
        onLoadChildren(parentId, result)
    }

    override fun onSearch(
        query: String,
        extras: Bundle?,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>,
    ) {
        hydrateSavedPlaybackSession()
        val searchQuery = query.ifBlank { extras?.autoSearchQuery().orEmpty() }
        Log.i("NaviampAutoCommand", "Loading Auto search query=$searchQuery extras=${extras?.debugDescription().orEmpty()}")
        loadAsyncChildren(result) { autoSearchResults(searchQuery) }
    }

    private fun updateMediaSession(metadata: AndroidPlaybackNotificationMetadata, largeIcon: Bitmap?) {
        val session = ensureMediaSession()
        session.isActive = true
        currentMediaSessionDurationMillis = AndroidPlaybackNotificationControls.durationMillis
        publishAutoQueue(session)
        session.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, metadata.title.orEmpty())
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, metadata.subtitle.orEmpty())
                .apply {
                    AndroidPlaybackNotificationControls.durationMillis?.takeIf { it > 0L }?.let { duration ->
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
        val favoriteIcon = if (AndroidPlaybackNotificationControls.isFavorite) {
            R.drawable.ic_favorite_filled_24
        } else {
            R.drawable.ic_favorite_24
        }
        val favoriteTitle = if (AndroidPlaybackNotificationControls.isFavorite) "Unfavorite" else "Favorite"
        session.setPlaybackState(buildPlaybackState(favoriteTitle, favoriteIcon))
    }

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
                    storage.savePlaybackSession(sourceId, session)
                }
            }
        }
        updateMediaSession(currentMetadata, currentLargeIcon)
    }

    private fun publishAutoQueue(session: MediaSessionCompat) {
        val queue = currentAutoQueue
        val queueSignature = queue.joinToString("|") { it.id.value }
        if (queueSignature == lastPublishedAutoQueueSignature) return
        lastPublishedAutoQueueSignature = queueSignature
        if (queue.isEmpty()) {
            session.setQueue(emptyList())
            session.setQueueTitle(null)
            return
        }
        session.setQueueTitle("Queue")
        session.setQueue(
            queue.mapIndexed { index, track ->
                MediaSessionCompat.QueueItem(
                    MediaDescriptionCompat.Builder()
                    .setMediaId("${AndroidAutoPlaybackControls.MediaIdTrackPrefix}${Uri.encode(track.id.value)}")
                        .setTitle(track.title)
                        .setSubtitle(track.artistName)
                        .setDescription(track.albumTitle)
                        .apply {
                            serviceStorage.savedCoverArtUrl(track)?.let { setIconUri(Uri.parse(it)) }
                        }
                        .build(),
                    index.toLong(),
                )
            },
        )
    }

    private fun updateMediaSessionPlaybackState() {
        val session = ensureMediaSession()
        session.isActive = true
        val favoriteIcon = if (AndroidPlaybackNotificationControls.isFavorite) {
            R.drawable.ic_favorite_filled_24
        } else {
            R.drawable.ic_favorite_24
        }
        val favoriteTitle = if (AndroidPlaybackNotificationControls.isFavorite) "Unfavorite" else "Favorite"
        session.setPlaybackState(buildPlaybackState(favoriteTitle, favoriteIcon))
    }

    private fun buildPlaybackState(
        favoriteTitle: String,
        favoriteIcon: Int,
    ): PlaybackStateCompat =
        PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
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
            .setActiveQueueItemId(currentAutoQueueIndex.takeIf { it >= 0 }?.toLong() ?: -1L)
            .build()

    private fun currentNowPlayingItem(): MediaBrowserCompat.MediaItem {
        val restored = restoredNowPlayingMetadata()
        val title = currentMetadata.title?.takeIf { it.isNotBlank() }
            ?: restored?.title
            ?: "Resume playback"
        val subtitle = currentMetadata.subtitle?.takeIf { it.isNotBlank() }
            ?: restored?.subtitle
            ?: "Continue your last Naviamp session"
        return playableItem(AndroidAutoPlaybackControls.MediaIdNowPlaying, title, subtitle)
    }

    private fun hydrateSavedPlaybackSession() {
        if (!currentMetadata.title.isNullOrBlank()) return
        val storage = serviceStorage
        val sourceId = storage.latestNavidromeSource()?.id ?: return
        val session = storage.loadPlaybackSession(sourceId) ?: return
        val restoredSession = session.restoredTrackSession() ?: return
        val track = restoredSession.currentTrack
        syncAutoQueue(PlaybackQueue(restoredSession.tracks, restoredSession.currentIndex))
        val coverArtUrl = storage.savedCoverArtUrl(track)
        currentMetadata = AndroidPlaybackNotificationMetadata(
            title = track.title,
            subtitle = track.artistName,
            coverArtUrl = coverArtUrl,
        )
        AndroidPlaybackNotificationControls.positionMillis = session.positionSeconds
            ?.takeIf { it > 0.0 }
            ?.let { (it * 1_000.0).toLong() }
        AndroidPlaybackNotificationControls.durationMillis = track.durationSeconds
            ?.takeIf { it > 0 }
            ?.let { it * 1_000L }
        updateMediaSession(currentMetadata, currentLargeIcon)
        coverArtUrl?.let { loadCoverArtAsync(it, currentMetadata) }
        Log.i(
            "NaviampSession",
            "Hydrated Android Auto session source=$sourceId title=${track.title} position=${session.positionSeconds}",
        )
    }

    private fun restoredNowPlayingMetadata(): AndroidPlaybackNotificationMetadata? {
        val storage = serviceStorage
        val sourceId = storage.latestNavidromeSource()?.id ?: return null
        val session = storage.loadPlaybackSession(sourceId) ?: return null
        session.internetRadioStation?.let { station ->
            return AndroidPlaybackNotificationMetadata(
                title = station.name,
                subtitle = "Internet radio",
            )
        }
        val track = session.currentTrack() ?: return null
        return AndroidPlaybackNotificationMetadata(
            title = track.title,
            subtitle = track.artistName,
            coverArtUrl = storage.savedCoverArtUrl(track),
        )
    }

    private fun trackItem(
        track: Track,
        mediaId: String = "${AndroidAutoPlaybackControls.MediaIdTrackPrefix}${Uri.encode(track.id.value)}",
    ): MediaBrowserCompat.MediaItem =
        playableItem(
            mediaId = mediaId,
            title = track.title,
            subtitle = listOfNotNull(track.artistName, track.albumTitle).joinToString(" - "),
            iconUri = serviceStorage.savedCoverArtUrl(track),
        )

    private fun artistItem(artist: Artist): MediaBrowserCompat.MediaItem =
        browsableItem(
            mediaId = AndroidAutoPlaybackControls.MediaIdArtistPrefix + listOf(
                Uri.encode(artist.id.value),
                Uri.encode(artist.name),
            ).joinToString(MediaIdPartSeparator),
            title = artist.name,
            subtitle = "Artist",
        )

    private fun albumItem(album: Album): MediaBrowserCompat.MediaItem =
        browsableItem(
            mediaId = AndroidAutoPlaybackControls.MediaIdAlbumPrefix + listOf(
                Uri.encode(album.id.value),
                Uri.encode(album.title),
                Uri.encode(album.artistName),
            ).joinToString(MediaIdPartSeparator),
            title = album.title,
            subtitle = listOfNotNull(album.artistName, album.releaseYear?.toString()).joinToString(" - "),
            iconUri = serviceStorage.savedCoverArtUrl(album),
        )

    private suspend fun autoSearchResults(query: String): MutableList<MediaBrowserCompat.MediaItem> =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.isBlank()) return@withContext mutableListOf()
            val storage = serviceStorage
            val source = storage.latestNavidromeSource() ?: return@withContext noSourceItems()
            val local = storage.searchLibrary(source.id, trimmed, AndroidAutoBrowseLimit.toLong(), 0)
            val provider = NavidromeProvider(source.toNavidromeConnection())
            val remote = if (local.isEmpty) {
                runCatching {
                    providerResponseService(storage).search(provider, trimmed, AndroidAutoBrowseLimit)
                }.getOrNull()
            } else {
                null
            }
            buildList {
                addAll(local.artists.ifEmpty { remote?.artists.orEmpty() }.take(8).map(::artistItem))
                addAll(local.albums.ifEmpty { remote?.albums.orEmpty() }.take(12).map(::albumItem))
                addAll(local.tracks.ifEmpty { remote?.tracks.orEmpty() }.take(AndroidAutoBrowseLimit).map(::trackItem))
            }.toMutableList()
        }

    private fun browsableItem(
        mediaId: String,
        title: String,
        subtitle: String,
        iconName: String? = null,
        iconUri: String? = null,
    ): MediaBrowserCompat.MediaItem =
        MediaBrowserCompat.MediaItem(
            MediaDescriptionCompat.Builder()
                .setMediaId(mediaId)
                .setTitle(title)
                .setSubtitle(subtitle)
                .apply {
                    iconName?.let { setIconUri(Uri.parse(autoDrawableUri(it))) }
                    iconUri?.let { setIconUri(Uri.parse(it)) }
                }
                .build(),
            MediaBrowserCompat.MediaItem.FLAG_BROWSABLE,
        )

    private fun playableItem(
        mediaId: String,
        title: String,
        subtitle: String,
        iconUri: String? = null,
    ): MediaBrowserCompat.MediaItem =
        MediaBrowserCompat.MediaItem(
            MediaDescriptionCompat.Builder()
                .setMediaId(mediaId)
                .setTitle(title)
                .setSubtitle(subtitle)
                .apply {
                    val artUri = iconUri ?: currentMetadata.coverArtUrl?.takeIf { mediaId == AndroidAutoPlaybackControls.MediaIdNowPlaying }
                    artUri?.let {
                        setIconUri(Uri.parse(it))
                    }
                }
                .build(),
            MediaBrowserCompat.MediaItem.FLAG_PLAYABLE,
        )

    private fun noSourceItems(): MutableList<MediaBrowserCompat.MediaItem> =
        mutableListOf(
            browsableItem("naviamp.no_source", "Connect Naviamp first", "Open the phone app and connect to Navidrome."),
        )

    private fun autoDrawableUri(name: String): String =
        "android.resource://$packageName/drawable/$name"

    private fun loadAsyncChildren(
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>,
        load: suspend () -> MutableList<MediaBrowserCompat.MediaItem>,
    ) {
        result.detach()
        AndroidPlaybackRuntime.get(applicationContext).scope.launch {
            val children = runCatching { load() }
                .onFailure { error -> Log.w("NaviampAutoCommand", "Could not load Auto children", error) }
                .getOrDefault(mutableListOf())
            sendChildren("async", children, result)
        }
    }

    private fun sendChildren(
        parentId: String,
        children: MutableList<MediaBrowserCompat.MediaItem>,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>,
    ) {
        result.sendResult(
            children.ifEmpty {
                mutableListOf(
                    browsableItem(
                        "$parentId.empty",
                        "Nothing here yet",
                        "Open Naviamp on your phone to refresh the library.",
                    ),
                )
            },
        )
    }

    private fun Intent?.toMetadata(): AndroidPlaybackNotificationMetadata {
        val nextCoverArtUrl = this?.getStringExtra(ExtraCoverArtUrl) ?: currentMetadata.coverArtUrl
        if (nextCoverArtUrl != currentMetadata.coverArtUrl) {
            currentLargeIcon = null
        }
        currentMetadata = AndroidPlaybackNotificationMetadata(
            title = this?.getStringExtra(ExtraTitle) ?: currentMetadata.title,
            subtitle = this?.getStringExtra(ExtraSubtitle) ?: currentMetadata.subtitle,
            coverArtUrl = nextCoverArtUrl,
        )
        return currentMetadata
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
        private const val ActionShuffle = "app.naviamp.android.playback.SHUFFLE"
        private const val ActionRepeat = "app.naviamp.android.playback.REPEAT"
        private const val ActionProgress = "app.naviamp.android.playback.PROGRESS"
        private const val AndroidAutoBrowseLimit = 50
        private const val MediaIdPartSeparator = "|"
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
        private var serviceOwnedPlayback = false
        private var currentAutoQueue: List<Track> = emptyList()
        private var currentAutoQueueIndex: Int = -1
        private var lastPublishedAutoQueueSignature: String? = null
        private var serviceShuffleEnabled = false
        private var serviceRepeatMode = ServiceRepeatMode.Off
        private var lastServiceSessionSaveAtMillis = 0L
        private var lastServicePlaybackState: PlaybackState? = null
        private var pendingServiceSeekPositionSeconds: Double? = null
        private var pendingServiceSeekAtMillis = 0L
        private const val ServicePlaybackSessionSaveIntervalMillis = 5_000L
        private const val ServiceSeekToleranceSeconds = 2.0
        private const val ServiceSeekStaleProgressWindowMillis = 1_500L
        private const val ServiceIgnoreZeroSeekAfterSeconds = 3.0

        fun start(context: Context, metadata: AndroidPlaybackNotificationMetadata) {
            val intent = Intent(context, AndroidPlaybackForegroundService::class.java)
                .setAction(ActionStart)
                .putExtra(ExtraTitle, metadata.title)
                .putExtra(ExtraSubtitle, metadata.subtitle)
                .putExtra(ExtraCoverArtUrl, metadata.coverArtUrl)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun update(context: Context, metadata: AndroidPlaybackNotificationMetadata) {
            start(context, metadata)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, AndroidPlaybackForegroundService::class.java)
                    .setAction(ActionStop)
                    .putExtra(ExtraFromEngine, true),
            )
        }

        fun updateProgress(context: Context, positionMillis: Long?, durationMillis: Long?) {
            context.startService(
                Intent(context, AndroidPlaybackForegroundService::class.java)
                    .setAction(ActionProgress)
                    .putExtra(ExtraPositionMillis, positionMillis ?: -1L)
                    .putExtra(ExtraDurationMillis, durationMillis ?: -1L),
            )
        }
    }
}

private enum class ServiceRepeatMode {
    Off,
    All,
    One;

    fun next(): ServiceRepeatMode =
        when (this) {
            Off -> All
            All -> One
            One -> Off
        }
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

private fun String.autoArtistGroupKey(): String {
    val first = trim().firstOrNull { it.isLetterOrDigit() } ?: return "#"
    return if (first.isLetter()) first.uppercaseChar().toString() else "#"
}

@Suppress("DEPRECATION")
private fun Bundle.debugDescription(): String =
    keySet().joinToString(prefix = "{", postfix = "}") { key -> "$key=${get(key)}" }

@Suppress("DEPRECATION")
private fun Bundle.autoSearchQuery(): String? {
    val keys = keySet()
    keys.firstOrNull { it.contains("search", ignoreCase = true) || it.contains("query", ignoreCase = true) }
        ?.let { key -> (get(key) as? String)?.trim()?.takeIf { it.isNotBlank() } }
        ?.let { return it }
    return keys.asSequence()
        .mapNotNull { key -> get(key) as? String }
        .map { it.trim() }
        .firstOrNull { it.isNotBlank() }
}

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
