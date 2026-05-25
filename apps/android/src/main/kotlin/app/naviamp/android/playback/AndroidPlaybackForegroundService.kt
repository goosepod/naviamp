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
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistId
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackRequest
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.radio.RadioService
import app.naviamp.domain.settings.PlaybackSessionSettings
import app.naviamp.domain.settings.RecentRadioKind
import app.naviamp.domain.settings.RecentRadioStream
import app.naviamp.domain.settings.SavedInternetRadioStation
import app.naviamp.provider.navidrome.NavidromeProvider
import app.naviamp.provider.navidrome.toNavidromeConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

private const val VoiceArtistScanLimit = 5_000L

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
        session.internetRadioStation?.let { station ->
            playServiceInternetRadioStation(storage, source.id, station.toStation())
            return
        }
        val track = session.currentTrack() ?: return
        currentAutoQueue = session.toTracks()
        currentAutoQueueIndex = session.currentIndex
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

    private fun handleServicePlayMediaId(mediaId: String): Boolean {
        val storage = AndroidStorage(applicationContext)
        val source = storage.latestNavidromeSource() ?: return false
        val sourceId = source.id
        return when {
            mediaId == AndroidAutoPlaybackControls.MediaIdNowPlaying -> {
                handleServiceAutoPlayPause()
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
                    runCatching { withContext(Dispatchers.IO) { provider.playlistTracks(playlistId) } }
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
                    runCatching { withContext(Dispatchers.IO) { provider.playlistTracks(playlistId) } }
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
        val storage = AndroidStorage(applicationContext)
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
        storage: AndroidStorage,
        sourceId: String,
        track: Track,
    ): List<Track> =
        track.albumId?.let { storage.libraryTracksForAlbum(sourceId, it, 200) }
            ?.takeIf { tracks -> tracks.any { it.id == track.id } }
            ?: track.albumTitle?.let { storage.libraryTracksForAlbumTitle(sourceId, it, track.artistName, 200) }
                ?.takeIf { tracks -> tracks.any { it.id == track.id } }
            ?: track.artistId?.let { storage.libraryTracksForArtist(sourceId, it, 200) }
                ?.takeIf { tracks -> tracks.any { it.id == track.id } }
            ?: storage.libraryTracksForArtistName(sourceId, track.artistName, 200)
                .takeIf { tracks -> tracks.any { it.id == track.id } }
            ?: listOf(track)

    private fun playServiceTrackQueue(
        storage: AndroidStorage,
        sourceId: String,
        tracks: List<Track>,
        currentIndex: Int,
    ) {
        if (tracks.isEmpty()) return
        val session = PlaybackSessionSettings.fromTracks(
            tracks = tracks,
            currentIndex = currentIndex.coerceIn(tracks.indices),
        ) ?: return
        storage.savePlaybackSession(sourceId, session)
        playSavedSession(session)
    }

    private fun playServiceAutoQueueItem(index: Int) {
        val queue = currentAutoQueue
        if (index !in queue.indices) return
        val storage = AndroidStorage(applicationContext)
        val sourceId = storage.latestNavidromeSource()?.id ?: return
        playServiceTrackQueue(storage, sourceId, queue, index)
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
        currentAutoQueue = emptyList()
        currentAutoQueueIndex = -1
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
        val recent = (listOf(stream) + settingsStore.loadRecentRadioStreams().filterNot { it.id == stream.id }).take(12)
        settingsStore.saveRecentRadioStreams(recent)
    }

    private fun rememberRecentInternetRadioStation(station: InternetRadioStation) {
        val settingsStore = AndroidSettingsStore(applicationContext)
        val saved = SavedInternetRadioStation.fromStation(station)
        val recent = (listOf(saved) + settingsStore.loadRecentInternetRadioStations().filterNot { it.id == saved.id }).take(12)
        settingsStore.saveRecentInternetRadioStations(recent)
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
        if (parentId == AndroidAutoPlaybackControls.MediaIdRadioStations) {
            loadAsyncChildren(result) {
                val source = storage.latestNavidromeSource() ?: return@loadAsyncChildren noSourceItems()
                val provider = NavidromeProvider(source.toNavidromeConnection())
                withContext(Dispatchers.IO) { provider.internetRadioStations() }
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
        if (parentId == AndroidAutoPlaybackControls.MediaIdPlaylists) {
            loadAsyncChildren(result) {
                val source = storage.latestNavidromeSource() ?: return@loadAsyncChildren noSourceItems()
                val provider = NavidromeProvider(source.toNavidromeConnection())
                withContext(Dispatchers.IO) { provider.playlists(limit = AndroidAutoBrowseLimit) }
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
        val children = when (parentId) {
            AndroidAutoPlaybackControls.MediaIdRoot -> mutableListOf(
                currentNowPlayingItem(),
                browsableItem(AndroidAutoPlaybackControls.MediaIdLibrary, "Library", "Artists, albums, and tracks"),
                browsableItem(AndroidAutoPlaybackControls.MediaIdRadio, "Radio", "Library radio"),
                browsableItem(AndroidAutoPlaybackControls.MediaIdDownloads, "Downloads", "Offline music"),
                browsableItem(AndroidAutoPlaybackControls.MediaIdMore, "More", "Playlists and shortcuts"),
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
                            mediaId = AndroidAutoPlaybackControls.MediaIdArtistPrefix + listOf(
                                Uri.encode(artist.id.value),
                                Uri.encode(artist.name),
                            ).joinToString(MediaIdPartSeparator),
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
                            mediaId = AndroidAutoPlaybackControls.MediaIdAlbumPrefix + listOf(
                                Uri.encode(album.id.value),
                                Uri.encode(album.title),
                                Uri.encode(album.artistName),
                            ).joinToString(MediaIdPartSeparator),
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
                browsableItem(AndroidAutoPlaybackControls.MediaIdRadioRecent, "Recently Played Radio", "Radio you started from Naviamp"),
                browsableItem(AndroidAutoPlaybackControls.MediaIdRadioStations, "Internet Radio", "Saved Navidrome stations"),
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
            AndroidAutoPlaybackControls.MediaIdMore -> mutableListOf(
                currentNowPlayingItem(),
                browsableItem(AndroidAutoPlaybackControls.MediaIdPlaylists, "Playlists", "Saved Navidrome playlists"),
                browsableItem(AndroidAutoPlaybackControls.MediaIdRadioStations, "Internet Radio", "Saved Navidrome stations"),
                browsableItem(AndroidAutoPlaybackControls.MediaIdLibraryTracks, "All Tracks", "Browse indexed tracks"),
            )
            else -> when {
                parentId.startsWith(AndroidAutoPlaybackControls.MediaIdPlaylistPrefix) -> {
                    val playlistId = Uri.decode(parentId.removePrefix(AndroidAutoPlaybackControls.MediaIdPlaylistPrefix))
                    loadAsyncChildren(result) {
                        val source = storage.latestNavidromeSource() ?: return@loadAsyncChildren noSourceItems()
                        val provider = NavidromeProvider(source.toNavidromeConnection())
                        withContext(Dispatchers.IO) { provider.playlistTracks(playlistId) }
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
                    sourceId?.let { id ->
                        storage.libraryTracksForArtist(id, ArtistId(artistId), AndroidAutoBrowseLimit.toLong())
                            .ifEmpty {
                                artistName?.let { name ->
                                    storage.libraryTracksForArtistName(id, name, AndroidAutoBrowseLimit.toLong())
                                }.orEmpty()
                            }
                            .map(::trackItem)
                            .toMutableList()
                    } ?: noSourceItems()
                }
                parentId.startsWith(AndroidAutoPlaybackControls.MediaIdAlbumPrefix) -> {
                    val parts = parentId.removePrefix(AndroidAutoPlaybackControls.MediaIdAlbumPrefix).mediaIdParts()
                    val albumId = parts.getOrNull(0).orEmpty()
                    val albumTitle = parts.getOrNull(1)
                    val albumArtist = parts.getOrNull(2)
                    sourceId?.let { id ->
                        storage.libraryTracksForAlbum(id, AlbumId(albumId), AndroidAutoBrowseLimit.toLong())
                            .ifEmpty {
                                albumTitle?.let { title ->
                                    storage.libraryTracksForAlbumTitle(id, title, albumArtist, AndroidAutoBrowseLimit.toLong())
                                }.orEmpty()
                            }
                            .map(::trackItem)
                            .toMutableList()
                    } ?: noSourceItems()
                }
                else -> mutableListOf()
            }
        }
        Log.i("NaviampAutoCommand", "Loaded Auto children parent=$parentId count=${children.size}")
        sendChildren(parentId, children, result)
    }

    private fun updateMediaSession(metadata: AndroidPlaybackNotificationMetadata, largeIcon: Bitmap?) {
        val session = ensureMediaSession()
        session.isActive = true
        currentMediaSessionDurationMillis = AndroidPlaybackNotificationControls.durationMillis
        publishAutoQueue(session)
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

    private fun publishAutoQueue(session: MediaSession) {
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
                MediaSession.QueueItem(
                    MediaDescription.Builder()
                        .setMediaId("${AndroidAutoPlaybackControls.MediaIdTrackPrefix}${Uri.encode(track.id.value)}")
                        .setTitle(track.title)
                        .setSubtitle(track.artistName)
                        .setDescription(track.albumTitle)
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
    ): AndroidPlaybackState =
        AndroidPlaybackState.Builder()
            .setActions(
                AndroidPlaybackState.ACTION_PLAY or
                    AndroidPlaybackState.ACTION_PAUSE or
                    AndroidPlaybackState.ACTION_PLAY_PAUSE or
                    AndroidPlaybackState.ACTION_SKIP_TO_PREVIOUS or
                    AndroidPlaybackState.ACTION_SKIP_TO_NEXT or
                    AndroidPlaybackState.ACTION_SKIP_TO_QUEUE_ITEM or
                    AndroidPlaybackState.ACTION_PLAY_FROM_SEARCH or
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
            .setActiveQueueItemId(currentAutoQueueIndex.takeIf { it >= 0 }?.toLong() ?: -1L)
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
        currentAutoQueue = session.toTracks()
        currentAutoQueueIndex = session.currentIndex
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

    private fun loadAsyncChildren(
        result: Result<MutableList<MediaBrowser.MediaItem>>,
        load: suspend () -> MutableList<MediaBrowser.MediaItem>,
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
        children: MutableList<MediaBrowser.MediaItem>,
        result: Result<MutableList<MediaBrowser.MediaItem>>,
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
    storage: AndroidStorage,
    sourceId: String,
    query: String,
): Artist? {
    val queryKey = query.voiceSearchKey()
    if (queryKey.isBlank()) return null
    return storage.librarySnapshot(sourceId, VoiceArtistScanLimit, 0)
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

private fun AndroidStorage.savedCoverArtUrl(track: Track): String? {
    val coverArtId = track.coverArtId ?: track.albumId?.value ?: return null
    val connection = latestNavidromeSource()?.toNavidromeConnection() ?: return null
    return NavidromeProvider(connection).coverArtUrl(coverArtId)
}

private suspend fun resolveInternetRadioStreamUrl(stationUrl: String): String =
    withContext(Dispatchers.IO) {
        val connection = (URL(stationUrl).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("User-Agent", "Naviamp Android")
            setRequestProperty("Icy-MetaData", "1")
        }
        val contentType = connection.contentType.orEmpty().lowercase()
        val resolvedUrl = connection.url.toString()
        if (!looksLikePlaylistUrl(resolvedUrl) &&
            !contentType.isPlaylistContentType() &&
            (contentType.startsWith("audio/") || contentType.contains("ogg"))
        ) {
            connection.disconnect()
            return@withContext resolvedUrl
        }

        val body = connection.inputStream.bufferedReader().use { it.readText().take(128_000) }
        connection.disconnect()

        parseRadioPlaylist(body)
            ?: if (looksLikeDirectAudioUrl(resolvedUrl)) resolvedUrl else stationUrl
    }

private fun parseRadioPlaylist(body: String): String? {
    val lines = body.lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toList()

    lines.firstNotNullOfOrNull { line ->
        val equalsIndex = line.indexOf('=')
        if (equalsIndex > 0 && line.substring(0, equalsIndex).trim().lowercase().startsWith("file")) {
            line.substring(equalsIndex + 1).trim().takeIf { it.startsWith("http", ignoreCase = true) }
        } else {
            null
        }
    }?.let { return it }

    lines.firstOrNull { line ->
        line.startsWith("http", ignoreCase = true) && !line.startsWith("#")
    }?.let { return it }

    Regex("<location>(.*?)</location>", RegexOption.IGNORE_CASE)
        .find(body)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?.takeIf { it.startsWith("http", ignoreCase = true) }
        ?.let { return it }

    Regex("https?://[^\\s\"'<>]+", RegexOption.IGNORE_CASE)
        .find(body)
        ?.value
        ?.let { return it }

    return null
}

private fun looksLikeDirectAudioUrl(url: String): Boolean {
    val normalized = url.substringBefore('?').lowercase()
    return normalized.endsWith(".mp3") ||
        normalized.endsWith(".ogg") ||
        normalized.endsWith(".opus") ||
        normalized.endsWith(".aac") ||
        normalized.endsWith(".m4a") ||
        normalized.endsWith(".flac")
}

private fun looksLikePlaylistUrl(url: String): Boolean {
    val normalized = url.substringBefore('?').lowercase()
    return normalized.endsWith(".pls") ||
        normalized.endsWith(".m3u") ||
        normalized.endsWith(".m3u8") ||
        normalized.endsWith(".xspf") ||
        normalized.endsWith(".asx")
}

private fun String.isPlaylistContentType(): Boolean =
    contains("mpegurl") ||
        contains("scpls") ||
        contains("pls") ||
        contains("xspf") ||
        contains("asx") ||
        contains("text/plain") ||
        contains("xml")
