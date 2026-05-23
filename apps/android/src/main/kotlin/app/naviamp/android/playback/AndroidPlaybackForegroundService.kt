package app.naviamp.android.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState as AndroidPlaybackState
import android.os.Build
import android.os.IBinder
import app.naviamp.android.R
import app.naviamp.android.MainActivity
import java.net.URL
import kotlin.concurrent.thread

class AndroidPlaybackForegroundService : Service() {
    private var mediaSession: MediaSession? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ActionPlayPause -> {
                AndroidPlaybackNotificationControls.onPlayPause?.invoke()
                refreshNotification(intent)
                return START_STICKY
            }
            ActionPrevious -> {
                AndroidPlaybackNotificationControls.onPrevious?.invoke()
                refreshNotification(intent)
                return START_STICKY
            }
            ActionNext -> {
                AndroidPlaybackNotificationControls.onNext?.invoke()
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
                }
                stopForeground(STOP_FOREGROUND_REMOVE)
                mediaSession?.isActive = false
                stopSelf()
                return START_NOT_STICKY
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

    private fun ensureMediaSession(): MediaSession =
        mediaSession ?: MediaSession(this, "NaviampPlayback").apply {
            setCallback(
                object : MediaSession.Callback() {
                    override fun onPlay() {
                        if (!AndroidPlaybackNotificationControls.isPlaying) {
                            AndroidPlaybackNotificationControls.onPlayPause?.invoke()
                            refreshNotification(null)
                        }
                    }

                    override fun onPause() {
                        if (AndroidPlaybackNotificationControls.isPlaying) {
                            AndroidPlaybackNotificationControls.onPlayPause?.invoke()
                            refreshNotification(null)
                        }
                    }

                    override fun onSkipToPrevious() {
                        AndroidPlaybackNotificationControls.onPrevious?.invoke()
                        refreshNotification(null)
                    }

                    override fun onSkipToNext() {
                        AndroidPlaybackNotificationControls.onNext?.invoke()
                        refreshNotification(null)
                    }

                    override fun onStop() {
                        AndroidPlaybackNotificationControls.onStop?.invoke()
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
            mediaSession = this
        }

    private fun updateMediaSession(metadata: AndroidPlaybackNotificationMetadata, largeIcon: Bitmap?) {
        val session = ensureMediaSession()
        session.isActive = true
        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, metadata.title.orEmpty())
                .putString(MediaMetadata.METADATA_KEY_ARTIST, metadata.subtitle.orEmpty())
                .apply {
                    largeIcon?.let { art ->
                        putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art)
                        putBitmap(MediaMetadata.METADATA_KEY_ART, art)
                    }
                }
                .build(),
        )
        val state = if (AndroidPlaybackNotificationControls.isPlaying) {
            AndroidPlaybackState.STATE_PLAYING
        } else {
            AndroidPlaybackState.STATE_PAUSED
        }
        val favoriteIcon = if (AndroidPlaybackNotificationControls.isFavorite) {
            R.drawable.ic_favorite_filled_24
        } else {
            R.drawable.ic_favorite_24
        }
        val favoriteTitle = if (AndroidPlaybackNotificationControls.isFavorite) "Unfavorite" else "Favorite"
        session.setPlaybackState(
            AndroidPlaybackState.Builder()
                .setActions(
                    AndroidPlaybackState.ACTION_PLAY or
                        AndroidPlaybackState.ACTION_PAUSE or
                        AndroidPlaybackState.ACTION_PLAY_PAUSE or
                        AndroidPlaybackState.ACTION_SKIP_TO_PREVIOUS or
                        AndroidPlaybackState.ACTION_SKIP_TO_NEXT or
                        AndroidPlaybackState.ACTION_STOP,
                )
                .addCustomAction(ActionFavorite, favoriteTitle, favoriteIcon)
                .setState(state, AndroidPlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f)
                .build(),
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
        private const val ExtraTitle = "title"
        private const val ExtraSubtitle = "subtitle"
        private const val ExtraCoverArtUrl = "coverArtUrl"
        private const val ExtraFromEngine = "fromEngine"
        private val PlayerNotificationColor = Color.rgb(82, 35, 31)
        private var currentMetadata = AndroidPlaybackNotificationMetadata()
        private var currentLargeIcon: Bitmap? = null

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
