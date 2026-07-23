package app.naviamp.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.MediaSessionManager
import androidx.media.utils.MediaConstants
import app.naviamp.presentation.NaviampCoreExternalPlaybackBridge
import app.naviamp.presentation.NaviampExternalMediaItem
import app.naviamp.presentation.NaviampExternalMediaRootId
import app.naviamp.presentation.NaviampExternalPlaybackSnapshot
import app.naviamp.presentation.NaviampExternalPlaybackPublicationPlanner
import app.naviamp.presentation.NaviampExternalPlaybackState
import app.naviamp.presentation.NaviampExternalQueueId
import app.naviamp.presentation.NaviampExternalPlaylistsId
import app.naviamp.presentation.NaviampExternalRadioId
import app.naviamp.presentation.NaviampExternalRecentAlbumsId
import app.naviamp.presentation.NaviampExternalRecentTracksId
import app.naviamp.ui.NaviampRepeatMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Android MediaSession, foreground notification, and Android Auto API boundary. */
class AndroidNaviampPlaybackService : MediaBrowserServiceCompat() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var session: MediaSessionCompat
    private var bridge: NaviampCoreExternalPlaybackBridge? = null
    private var snapshot = NaviampExternalPlaybackSnapshot()
    private var snapshotJob: Job? = null
    private var foreground = false
    private val publicationPlanner = NaviampExternalPlaybackPublicationPlanner()

    override fun onCreate() {
        super.onCreate()
        AndroidNaviampApplicationRuntime.get(this)
        createNotificationChannel()
        session = MediaSessionCompat(this, MediaSessionTag).apply {
            setCallback(mediaSessionCallback())
        }
        sessionToken = session.sessionToken
        attachBridge()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        attachBridge()
        when (intent?.action) {
            ActionPlayPause -> if (snapshot.state == NaviampExternalPlaybackState.Playing) bridge?.pause() else bridge?.play()
            ActionPrevious -> bridge?.previous()
            ActionNext -> bridge?.next()
            ActionFavorite -> bridge?.toggleFavorite()
            ActionStop -> bridge?.stop()
            ActionReleaseForeground -> releaseForeground()
            ActionRefresh, null -> publish(snapshot)
        }
        return START_STICKY
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?,
    ): BrowserRoot? {
        val remote = MediaSessionManager.RemoteUserInfo(clientPackageName, -1, clientUid)
        val trusted = clientUid == applicationInfo.uid ||
            MediaSessionManager.getSessionManager(this).isTrustedForMediaControl(remote)
        if (!trusted) return null
        attachBridge()
        return BrowserRoot(
            NaviampExternalMediaRootId,
            Bundle().apply {
                putBoolean(MediaConstants.BROWSER_SERVICE_EXTRAS_KEY_SEARCH_SUPPORTED, true)
            },
        )
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>,
    ) {
        result.sendResult(children(parentId))
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>,
        options: Bundle,
    ) {
        result.sendResult(children(parentId).paginated(options))
    }

    override fun onSearch(
        query: String,
        extras: Bundle?,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>,
    ) {
        val matches = bridge?.search(query).orEmpty().map(::playableItem).toMutableList()
        result.sendResult(matches)
    }

    override fun onDestroy() {
        snapshotJob?.cancel()
        session.setCallback(null)
        session.release()
        scope.cancel()
        super.onDestroy()
    }

    private fun attachBridge() {
        val available = AndroidNaviampPlaybackRuntime.bridge() ?: return
        if (bridge === available) return
        bridge = available
        snapshotJob?.cancel()
        publicationPlanner.reset()
        snapshot = available.snapshot()
        snapshotJob = scope.launch {
            available.snapshots.collectLatest(::publish)
        }
        publish(snapshot)
    }

    private fun publish(next: NaviampExternalPlaybackSnapshot) {
        snapshot = next
        val publication = publicationPlanner.plan(next)
        if (publication.sessionContent) publishSessionContent(next)
        if (publication.playbackState) publishPlaybackState(next)
        if (publication.browseCatalog) {
            notifyChildrenChanged(NaviampExternalMediaRootId)
            notifyChildrenChanged(NaviampExternalQueueId)
            notifyChildrenChanged(NaviampExternalRecentTracksId)
            notifyChildrenChanged(NaviampExternalRecentAlbumsId)
            notifyChildrenChanged(NaviampExternalPlaylistsId)
            notifyChildrenChanged(NaviampExternalRadioId)
        }
        if (next.shouldRetainPlaybackService) {
            if (publication.notification || !foreground) {
                val notification = notification(next)
                if (!foreground) {
                    startForeground(NotificationId, notification)
                    foreground = true
                } else {
                    getSystemService(NotificationManager::class.java).notify(NotificationId, notification)
                }
            }
        } else {
            releaseForeground()
            stopSelf()
        }
    }

    private fun publishSessionContent(value: NaviampExternalPlaybackSnapshot) {
        val current = value.current
        session.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, current?.mediaId)
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, current?.title.orEmpty())
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, current?.subtitle.orEmpty())
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, current?.description.orEmpty())
                .apply {
                    value.durationMillis?.let { putLong(MediaMetadataCompat.METADATA_KEY_DURATION, it) }
                    current?.artworkUrl?.let { putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, it) }
                }
                .build(),
        )
        session.setQueue(value.queue.mapIndexed { index, item ->
            MediaSessionCompat.QueueItem(item.description(), index.toLong())
        })
        session.setQueueTitle("Queue")
    }

    private fun publishPlaybackState(value: NaviampExternalPlaybackSnapshot) {
        session.setPlaybackState(playbackState(value))
        session.setShuffleMode(
            if (value.shuffleActive) PlaybackStateCompat.SHUFFLE_MODE_ALL else PlaybackStateCompat.SHUFFLE_MODE_NONE,
        )
        session.setRepeatMode(
            when (value.repeatMode) {
                NaviampRepeatMode.Off -> PlaybackStateCompat.REPEAT_MODE_NONE
                NaviampRepeatMode.Queue -> PlaybackStateCompat.REPEAT_MODE_ALL
                NaviampRepeatMode.Track -> PlaybackStateCompat.REPEAT_MODE_ONE
            },
        )
        session.isActive = value.shouldRetainPlaybackService
    }

    private fun playbackState(value: NaviampExternalPlaybackSnapshot): PlaybackStateCompat =
        PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_STOP or
                    PlaybackStateCompat.ACTION_REWIND or PlaybackStateCompat.ACTION_FAST_FORWARD or
                    PlaybackStateCompat.ACTION_SEEK_TO or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM or
                    PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID or PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH,
            )
            .addCustomAction(
                CustomActionFavorite,
                if (value.favorite) "Unfavorite" else "Favorite",
                if (value.favorite) android.R.drawable.btn_star_big_on else android.R.drawable.btn_star_big_off,
            )
            .addCustomAction(
                CustomActionShuffle,
                if (value.shuffleActive) "Shuffle on" else "Shuffle off",
                android.R.drawable.ic_menu_sort_by_size,
            )
            .addCustomAction(
                CustomActionRepeat,
                when (value.repeatMode) {
                    NaviampRepeatMode.Off -> "Repeat off"
                    NaviampRepeatMode.Queue -> "Repeat queue"
                    NaviampRepeatMode.Track -> "Repeat track"
                },
                android.R.drawable.ic_menu_revert,
            )
            .setState(
                when (value.state) {
                    NaviampExternalPlaybackState.Idle -> PlaybackStateCompat.STATE_STOPPED
                    NaviampExternalPlaybackState.Loading -> PlaybackStateCompat.STATE_BUFFERING
                    NaviampExternalPlaybackState.Playing -> PlaybackStateCompat.STATE_PLAYING
                    NaviampExternalPlaybackState.Paused -> PlaybackStateCompat.STATE_PAUSED
                },
                value.positionMillis ?: PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                if (value.state == NaviampExternalPlaybackState.Playing) 1f else 0f,
            )
            .setActiveQueueItemId(value.currentQueueIndex.toLong())
            .build()

    private fun mediaSessionCallback() = object : MediaSessionCompat.Callback() {
        override fun onPlay() = bridge?.play() ?: Unit
        override fun onPause() = bridge?.pause() ?: Unit
        override fun onStop() = bridge?.stop() ?: Unit
        override fun onSkipToPrevious() = bridge?.previous() ?: Unit
        override fun onSkipToNext() = bridge?.next() ?: Unit
        override fun onSkipToQueueItem(id: Long) = bridge?.selectQueueItem(id.toInt()) ?: Unit
        override fun onSeekTo(pos: Long) = bridge?.seekTo(pos) ?: Unit
        override fun onRewind() = bridge?.seekTo((snapshot.positionMillis ?: 0L) - SeekStepMillis) ?: Unit
        override fun onFastForward() = bridge?.seekTo(
            ((snapshot.positionMillis ?: 0L) + SeekStepMillis)
                .let { requested -> snapshot.durationMillis?.let(requested::coerceAtMost) ?: requested },
        ) ?: Unit
        override fun onSetShuffleMode(shuffleMode: Int) {
            val requested = shuffleMode != PlaybackStateCompat.SHUFFLE_MODE_NONE
            if (requested != snapshot.shuffleActive) bridge?.toggleShuffle()
        }
        override fun onSetRepeatMode(repeatMode: Int) {
            val requested = when (repeatMode) {
                PlaybackStateCompat.REPEAT_MODE_ONE -> NaviampRepeatMode.Track
                PlaybackStateCompat.REPEAT_MODE_ALL,
                PlaybackStateCompat.REPEAT_MODE_GROUP,
                -> NaviampRepeatMode.Queue
                else -> NaviampRepeatMode.Off
            }
            val modes = listOf(NaviampRepeatMode.Off, NaviampRepeatMode.Queue, NaviampRepeatMode.Track)
            val steps = (modes.indexOf(requested) - modes.indexOf(snapshot.repeatMode) + modes.size) % modes.size
            repeat(steps) { bridge?.cycleRepeatMode() }
        }
        override fun onCustomAction(action: String, extras: Bundle?) {
            when (action) {
                CustomActionFavorite -> bridge?.toggleFavorite()
                CustomActionShuffle -> bridge?.toggleShuffle()
                CustomActionRepeat -> bridge?.cycleRepeatMode()
            }
        }
        override fun onPlayFromMediaId(mediaId: String, extras: Bundle?) {
            bridge?.playMediaId(mediaId)
        }
        override fun onPlayFromSearch(query: String, extras: Bundle?) {
            bridge?.playSearch(query)
        }
    }

    private fun children(parentId: String): MutableList<MediaBrowserCompat.MediaItem> =
        bridge?.browseChildren(parentId).orEmpty().map { item ->
            if (item.playable) playableItem(item) else browsableItem(item.mediaId, item.title, item.subtitle)
        }.toMutableList()

    private fun notification(value: NaviampExternalPlaybackSnapshot): Notification {
        val current = value.current
        val playPause = if (value.state == NaviampExternalPlaybackState.Playing) {
            notificationAction(ActionPlayPause, android.R.drawable.ic_media_pause, "Pause")
        } else {
            notificationAction(ActionPlayPause, android.R.drawable.ic_media_play, "Play")
        }
        return Notification.Builder(this, NotificationChannelId)
            .setContentTitle(current?.title ?: "Naviamp")
            .setContentText(current?.subtitle ?: "Playback")
            .setSubText(current?.description?.takeIf(String::isNotBlank))
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(contentIntent())
            .setDeleteIntent(servicePendingIntent(ActionStop, 5))
            .addAction(notificationAction(ActionPrevious, android.R.drawable.ic_media_previous, "Previous"))
            .addAction(playPause)
            .addAction(notificationAction(ActionNext, android.R.drawable.ic_media_next, "Next"))
            .addAction(
                notificationAction(
                    ActionFavorite,
                    if (value.favorite) android.R.drawable.btn_star_big_on else android.R.drawable.btn_star_big_off,
                    if (value.favorite) "Unfavorite" else "Favorite",
                ),
            )
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(session.sessionToken.token as android.media.session.MediaSession.Token)
                    .setShowActionsInCompactView(0, 1, 2),
            )
            .setOngoing(value.state == NaviampExternalPlaybackState.Playing)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()
    }

    private fun notificationAction(action: String, icon: Int, title: String): Notification.Action =
        Notification.Action.Builder(
            Icon.createWithResource(this, icon),
            title,
            servicePendingIntent(action, action.hashCode()),
        ).build()

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            naviampPlaybackServiceIntent(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun contentIntent(): PendingIntent {
        val launch = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(Intent.ACTION_MAIN).setPackage(packageName)
        launch.putExtra(IntentExtraOpenNowPlaying, true)
        return PendingIntent.getActivity(
            this,
            0,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun releaseForeground() {
        if (foreground) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            foreground = false
        }
    }

    private fun createNotificationChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                NotificationChannelId,
                "Playback",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Playback controls"
                setShowBadge(false)
            },
        )
    }

    private fun NaviampExternalMediaItem.description(): MediaDescriptionCompat =
        MediaDescriptionCompat.Builder()
            .setMediaId(mediaId)
            .setTitle(title)
            .setSubtitle(subtitle)
            .setDescription(this.description)
            .apply { artworkUrl?.let { setIconUri(Uri.parse(it)) } }
            .build()

    private fun playableItem(item: NaviampExternalMediaItem) = MediaBrowserCompat.MediaItem(
        item.description(),
        MediaBrowserCompat.MediaItem.FLAG_PLAYABLE,
    )

    private fun browsableItem(id: String, title: String, subtitle: String) = MediaBrowserCompat.MediaItem(
        MediaDescriptionCompat.Builder().setMediaId(id).setTitle(title).setSubtitle(subtitle).build(),
        MediaBrowserCompat.MediaItem.FLAG_BROWSABLE,
    )

    private fun MutableList<MediaBrowserCompat.MediaItem>.paginated(options: Bundle): MutableList<MediaBrowserCompat.MediaItem> {
        val page = options.getInt(MediaBrowserCompat.EXTRA_PAGE, -1)
        val size = options.getInt(MediaBrowserCompat.EXTRA_PAGE_SIZE, -1)
        if (page < 0 || size <= 0) return this
        val start = page * size
        if (start !in indices) return mutableListOf()
        return subList(start, (start + size).coerceAtMost(this.size)).toMutableList()
    }

    companion object {
        internal const val ActionRefresh = "app.naviamp.android.v2.action.REFRESH_PLAYBACK"
        internal const val ActionReleaseForeground = "app.naviamp.android.v2.action.RELEASE_FOREGROUND"
        private const val ActionPlayPause = "app.naviamp.android.v2.action.PLAY_PAUSE"
        private const val ActionPrevious = "app.naviamp.android.v2.action.PREVIOUS"
        private const val ActionNext = "app.naviamp.android.v2.action.NEXT"
        private const val ActionFavorite = "app.naviamp.android.v2.action.FAVORITE"
        private const val ActionStop = "app.naviamp.android.v2.action.STOP"
        private const val NotificationChannelId = "naviamp-playback-v2"
        private const val NotificationId = 2001
        private const val MediaSessionTag = "NaviampCorePlayback"
        private const val SeekStepMillis = 10_000L
        private const val CustomActionFavorite = "app.naviamp.action.FAVORITE"
        private const val CustomActionShuffle = "app.naviamp.action.SHUFFLE"
        private const val CustomActionRepeat = "app.naviamp.action.REPEAT"

        fun refreshIntent(context: Context): Intent = context.naviampPlaybackServiceIntent(ActionRefresh)
        fun releaseForegroundIntent(context: Context): Intent =
            context.naviampPlaybackServiceIntent(ActionReleaseForeground)
    }
}
