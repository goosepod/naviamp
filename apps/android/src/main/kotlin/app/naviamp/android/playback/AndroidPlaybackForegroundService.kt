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
import android.media.MediaDescription
import android.media.MediaMetadata
import android.media.browse.MediaBrowser
import android.media.session.MediaSession
import android.net.Uri
import android.media.session.PlaybackState as AndroidPlaybackState
import android.os.Build
import android.os.Bundle
import android.service.media.MediaBrowserService
import android.util.Log
import app.naviamp.android.AndroidStorage
import app.naviamp.android.AndroidSettingsStore
import app.naviamp.android.R
import app.naviamp.android.MainActivity
import app.naviamp.domain.AlbumId
import app.naviamp.domain.ArtistId
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.Track
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackRequest
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.settings.PlaybackSessionSettings
import app.naviamp.provider.navidrome.NavidromeProvider
import app.naviamp.provider.navidrome.toNavidromeConnection
import kotlinx.coroutines.launch
import java.net.URL
import kotlin.concurrent.thread

class AndroidPlaybackForegroundService : MediaBrowserService() {
    private var mediaSession: MediaSession? = null
    private var browserSessionTokenSet = false
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
        super.onDestroy()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        pauseServiceOwnedPlayback("Android Auto browser unbound")
        return super.onUnbind(intent)
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
                AndroidPlaybackNotificationControls.onPrevious?.invoke()
                    ?: playSavedSessionAdjacent(-1)
                refreshNotification(intent)
                return START_STICKY
            }
            ActionNext -> {
                AndroidPlaybackNotificationControls.onNext?.invoke()
                    ?: playSavedSessionAdjacent(1)
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
                    AndroidPlaybackNotificationControls.onStop?.invoke()
                        ?: stopServiceOwnedPlayback("stop action")
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
                    .setMediaSession(ensureMediaSession().sessionToken)
                    .setShowActionsInCompactView(0, 1, 2),
            )
            .setColor(notificationColor)
            .setColorized(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setPriority(Notification.PRIORITY_MIN)
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
                URL(coverArtUrl).openStream().use { input ->
                    BitmapFactory.decodeStream(input)
                }
            }.getOrNull() ?: return@thread
            if (currentMetadata.coverArtUrl != coverArtUrl) return@thread
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NotificationId, buildNotification(metadata, largeIcon = bitmap))
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
        if (!serviceOwnedPlayback && !AndroidPlaybackNotificationControls.isPlaying) return
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
        val storage = AndroidStorage(applicationContext)
        val sourceId = storage.latestNavidromeSource()?.id ?: return
        val session = storage.loadPlaybackSession(sourceId) ?: return
        storage.savePlaybackSession(
            sourceId,
            session.copy(positionSeconds = positionSeconds.takeIf { it > 0.0 }),
        )
    }

    private fun playSavedSessionAdjacent(delta: Int) {
        val storage = AndroidStorage(applicationContext)
        val sourceId = storage.latestNavidromeSource()?.id ?: return
        val session = storage.loadPlaybackSession(sourceId) ?: return
        val tracks = session.toTracks()
        if (tracks.isEmpty()) return
        val nextIndex = (session.currentIndex + delta).coerceIn(tracks.indices)
        val nextSession = session.copy(
            currentIndex = nextIndex,
            positionSeconds = null,
        )
        storage.savePlaybackSession(sourceId, nextSession)
        playSavedSession(nextSession)
    }

    private fun playSavedSession(existingSession: PlaybackSessionSettings? = null) {
        val storage = AndroidStorage(applicationContext)
        val source = storage.latestNavidromeSource() ?: return
        val session = existingSession ?: storage.loadPlaybackSession(source.id) ?: return
        val track = session.currentTrack() ?: return
        val connection = source.toNavidromeConnection()
        val provider = NavidromeProvider(connection)
        val runtime = AndroidPlaybackRuntime.get(applicationContext)
        val playbackSettings = AndroidSettingsStore(applicationContext).loadPlaybackSettings()
        val quality = StreamQuality.Original
        val startPositionSeconds = session.positionSeconds?.takeIf { it > 0.0 }

        runtime.playbackEngine.applyTlsSettings(connection.tlsSettings)
        AndroidPlaybackNotificationControls.canFavorite = provider.capabilities.supportsTrackFavorites
        AndroidPlaybackNotificationControls.isFavorite = track.favoritedAtIso8601 != null
        AndroidPlaybackNotificationControls.isPlaying = true
        serviceOwnedPlayback = true
        lastServiceProgressPublishAtMillis = 0L
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
            subtitle = track.artistName ?: track.albumTitle,
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
                provider.streamUrl(
                    StreamRequest(
                        trackId = track.id,
                        quality = quality,
                        startPositionSeconds = null,
                    ),
                )
            }.onSuccess { streamUrl ->
                runtime.playbackEngine.updateNotificationMetadata(
                    title = track.title,
                    subtitle = track.artistName,
                    coverArtUrl = storage.savedCoverArtUrl(track),
                )
                runtime.playbackEngine.play(
                    scope = runtime.scope,
                    request = PlaybackRequest(
                        url = streamUrl,
                        mediaId = track.id.value,
                        replayGainMode = playbackSettings.replayGainMode,
                        startPositionSeconds = startPositionSeconds,
                    ),
                    onStateChanged = { state ->
                        if (state != lastServicePlaybackState) {
                            lastServicePlaybackState = state
                            AndroidPlaybackNotificationControls.isPlaying = state == PlaybackState.Playing
                            updateMediaSessionPlaybackState()
                        }
                        if (state == PlaybackState.Finished) {
                            playSavedSessionAdjacent(1)
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
            pendingSeekPosition != null &&
            now - pendingServiceSeekAtMillis < ServiceSeekStaleProgressWindowMillis &&
            progressPositionSeconds != null &&
            kotlin.math.abs(progressPositionSeconds - pendingSeekPosition) > ServiceSeekToleranceSeconds
        ) {
            return
        }
        if (
            pendingSeekPosition != null &&
            progressPositionSeconds != null &&
            kotlin.math.abs(progressPositionSeconds - pendingSeekPosition) <= ServiceSeekToleranceSeconds
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
                session.copy(positionSeconds = progressPositionSeconds?.takeIf { it > 0.0 }),
            )
        }
        if (now - lastServiceProgressPublishAtMillis >= ServicePlaybackProgressPublishIntervalMillis) {
            lastServiceProgressPublishAtMillis = now
            updateMediaSessionPlaybackState()
        }
    }

    private fun ensureMediaSession(): MediaSession =
        mediaSession ?: MediaSession(this, "NaviampPlayback").apply {
            setCallback(
                object : MediaSession.Callback() {
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
                        AndroidPlaybackNotificationControls.onPrevious?.invoke()
                            ?: playSavedSessionAdjacent(-1)
                        refreshNotification(null)
                    }

                    override fun onSkipToNext() {
                        AndroidPlaybackNotificationControls.onNext?.invoke()
                            ?: playSavedSessionAdjacent(1)
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
                        if (mediaId == AndroidAutoPlaybackControls.MediaIdNowPlaying && !AndroidPlaybackNotificationControls.isPlaying) {
                            handleServiceAutoPlayPause()
                            refreshNotification(null)
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

                    override fun onCustomAction(action: String, extras: android.os.Bundle?) {
                        if (action == ActionFavorite && AndroidPlaybackNotificationControls.canFavorite) {
                            AndroidPlaybackNotificationControls.isFavorite =
                                !AndroidPlaybackNotificationControls.isFavorite
                            AndroidPlaybackNotificationControls.onToggleFavorite?.invoke()
                            refreshNotification(null)
                        }
                    }
                },
            )
            isActive = true
            if (!browserSessionTokenSet) {
                setSessionToken(sessionToken)
                browserSessionTokenSet = true
            }
            mediaSession = this
        }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?,
    ): BrowserRoot {
        hydrateSavedPlaybackSession()
        return BrowserRoot(AndroidAutoPlaybackControls.MediaIdRoot, null)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowser.MediaItem>>,
    ) {
        hydrateSavedPlaybackSession()
        val storage = AndroidStorage(applicationContext)
        val sourceId = storage.latestNavidromeSource()?.id
        val children = when (parentId) {
            AndroidAutoPlaybackControls.MediaIdRoot -> mutableListOf(
                currentNowPlayingItem(),
                browsableItem(AndroidAutoPlaybackControls.MediaIdLibrary, "Library", "Artists, albums, and tracks"),
                browsableItem(AndroidAutoPlaybackControls.MediaIdRadio, "Radio", "Library radio"),
                browsableItem(AndroidAutoPlaybackControls.MediaIdDownloads, "Downloads", "Offline music"),
            )
            AndroidAutoPlaybackControls.MediaIdLibrary -> mutableListOf(
                browsableItem(AndroidAutoPlaybackControls.MediaIdLibraryArtists, "Artists", "Browse indexed artists"),
                browsableItem(AndroidAutoPlaybackControls.MediaIdLibraryAlbums, "Albums", "Browse indexed albums"),
                browsableItem(AndroidAutoPlaybackControls.MediaIdLibraryTracks, "Tracks", "Browse indexed tracks"),
            )
            AndroidAutoPlaybackControls.MediaIdLibraryArtists -> {
                sourceId?.let { id ->
                    storage.librarySnapshot(id, AndroidAutoBrowseLimit.toLong(), 0).artists.map { artist ->
                        browsableItem(
                            mediaId = "${AndroidAutoPlaybackControls.MediaIdArtistPrefix}${Uri.encode(artist.id.value)}",
                            title = artist.name,
                            subtitle = "Artist",
                        )
                    }.toMutableList()
                } ?: noSourceItems()
            }
            AndroidAutoPlaybackControls.MediaIdLibraryAlbums -> {
                sourceId?.let { id ->
                    storage.librarySnapshot(id, AndroidAutoBrowseLimit.toLong(), 0).albums.map { album ->
                        browsableItem(
                            mediaId = "${AndroidAutoPlaybackControls.MediaIdAlbumPrefix}${Uri.encode(album.id.value)}",
                            title = album.title,
                            subtitle = listOfNotNull(album.artistName, album.releaseYear?.toString()).joinToString(" - "),
                        )
                    }.toMutableList()
                } ?: noSourceItems()
            }
            AndroidAutoPlaybackControls.MediaIdLibraryTracks -> {
                sourceId?.let { id ->
                    storage.librarySnapshot(id, AndroidAutoBrowseLimit.toLong(), 0).tracks.map(::trackItem).toMutableList()
                } ?: noSourceItems()
            }
            AndroidAutoPlaybackControls.MediaIdRadio -> mutableListOf(
                playableItem(AndroidAutoPlaybackControls.MediaIdRadioLibrary, "Library Radio", "Random tracks from your library"),
            )
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
            else -> when {
                parentId.startsWith(AndroidAutoPlaybackControls.MediaIdArtistPrefix) -> {
                    val artistId = parentId.removePrefix(AndroidAutoPlaybackControls.MediaIdArtistPrefix).decodedMediaId()
                    sourceId?.let { id ->
                        storage.libraryTracksForArtist(id, ArtistId(artistId), AndroidAutoBrowseLimit.toLong())
                            .map(::trackItem)
                            .toMutableList()
                    } ?: noSourceItems()
                }
                parentId.startsWith(AndroidAutoPlaybackControls.MediaIdAlbumPrefix) -> {
                    val albumId = parentId.removePrefix(AndroidAutoPlaybackControls.MediaIdAlbumPrefix).decodedMediaId()
                    sourceId?.let { id ->
                        storage.libraryTracksForAlbum(id, AlbumId(albumId), AndroidAutoBrowseLimit.toLong())
                            .map(::trackItem)
                            .toMutableList()
                    } ?: noSourceItems()
                }
                else -> mutableListOf()
            }
        }
        if (children.isEmpty()) {
            result.sendResult(
                mutableListOf(
                    browsableItem("$parentId.empty", "Nothing here yet", "Open Naviamp on your phone to refresh the library."),
                ),
            )
        } else {
            result.sendResult(children)
        }
    }

    private fun updateMediaSession(metadata: AndroidPlaybackNotificationMetadata, largeIcon: Bitmap?) {
        val session = ensureMediaSession()
        session.isActive = true
        currentMediaSessionDurationMillis = AndroidPlaybackNotificationControls.durationMillis
        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, metadata.title.orEmpty())
                .putString(MediaMetadata.METADATA_KEY_ARTIST, metadata.subtitle.orEmpty())
                .apply {
                    AndroidPlaybackNotificationControls.durationMillis?.takeIf { it > 0L }?.let { duration ->
                        putLong(MediaMetadata.METADATA_KEY_DURATION, duration)
                    }
                }
                .apply {
                    largeIcon?.let { art ->
                        putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art)
                        putBitmap(MediaMetadata.METADATA_KEY_ART, art)
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
    ): AndroidPlaybackState =
        AndroidPlaybackState.Builder()
            .setActions(
                AndroidPlaybackState.ACTION_PLAY or
                    AndroidPlaybackState.ACTION_PAUSE or
                    AndroidPlaybackState.ACTION_PLAY_PAUSE or
                    AndroidPlaybackState.ACTION_SKIP_TO_PREVIOUS or
                    AndroidPlaybackState.ACTION_SKIP_TO_NEXT or
                    AndroidPlaybackState.ACTION_SEEK_TO or
                    AndroidPlaybackState.ACTION_STOP,
            )
            .addCustomAction(ActionFavorite, favoriteTitle, favoriteIcon)
            .setState(
                if (AndroidPlaybackNotificationControls.isPlaying) {
                    AndroidPlaybackState.STATE_PLAYING
                } else {
                    AndroidPlaybackState.STATE_PAUSED
                },
                AndroidPlaybackNotificationControls.positionMillis
                    ?: AndroidPlaybackState.PLAYBACK_POSITION_UNKNOWN,
                if (AndroidPlaybackNotificationControls.isPlaying) 1f else 0f,
            )
            .build()

    private fun currentNowPlayingItem(): MediaBrowser.MediaItem {
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
        val storage = AndroidStorage(applicationContext)
        val sourceId = storage.latestNavidromeSource()?.id ?: return
        val session = storage.loadPlaybackSession(sourceId) ?: return
        val track = session.currentTrack() ?: return
        val coverArtUrl = storage.savedCoverArtUrl(track)
        currentMetadata = AndroidPlaybackNotificationMetadata(
            title = track.title,
            subtitle = track.artistName ?: track.albumTitle,
            coverArtUrl = coverArtUrl,
        )
        AndroidPlaybackNotificationControls.isPlaying = false
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
        val storage = AndroidStorage(applicationContext)
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
    ): MediaBrowser.MediaItem =
        playableItem(
            mediaId = mediaId,
            title = track.title,
            subtitle = listOfNotNull(track.artistName, track.albumTitle).joinToString(" - "),
        )

    private fun browsableItem(
        mediaId: String,
        title: String,
        subtitle: String,
    ): MediaBrowser.MediaItem =
        MediaBrowser.MediaItem(
            MediaDescription.Builder()
                .setMediaId(mediaId)
                .setTitle(title)
                .setSubtitle(subtitle)
                .build(),
            MediaBrowser.MediaItem.FLAG_BROWSABLE,
        )

    private fun playableItem(
        mediaId: String,
        title: String,
        subtitle: String,
    ): MediaBrowser.MediaItem =
        MediaBrowser.MediaItem(
            MediaDescription.Builder()
                .setMediaId(mediaId)
                .setTitle(title)
                .setSubtitle(subtitle)
                .apply {
                    currentMetadata.coverArtUrl?.takeIf { mediaId == AndroidAutoPlaybackControls.MediaIdNowPlaying }?.let {
                        setIconUri(Uri.parse(it))
                    }
                }
                .build(),
            MediaBrowser.MediaItem.FLAG_PLAYABLE,
        )

    private fun noSourceItems(): MutableList<MediaBrowser.MediaItem> =
        mutableListOf(
            browsableItem("naviamp.no_source", "Connect Naviamp first", "Open the phone app and connect to Navidrome."),
        )

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
        private const val ActionProgress = "app.naviamp.android.playback.PROGRESS"
        private const val AndroidAutoBrowseLimit = 50
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
        private var lastServiceProgressPublishAtMillis = 0L
        private var lastServiceSessionSaveAtMillis = 0L
        private var lastServicePlaybackState: PlaybackState? = null
        private var pendingServiceSeekPositionSeconds: Double? = null
        private var pendingServiceSeekAtMillis = 0L
        private const val ServicePlaybackProgressPublishIntervalMillis = 1_000L
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

private fun String.decodedMediaId(): String =
    Uri.decode(this)

private fun AndroidStorage.savedCoverArtUrl(track: Track): String? {
    val coverArtId = track.coverArtId ?: track.albumId?.value ?: return null
    val connection = latestNavidromeSource()?.toNavidromeConnection() ?: return null
    return NavidromeProvider(connection).coverArtUrl(coverArtId)
}
