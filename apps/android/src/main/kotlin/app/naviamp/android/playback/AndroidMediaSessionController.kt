package app.naviamp.android.playback

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import app.naviamp.android.AndroidStorageDependencies
import app.naviamp.android.R
import app.naviamp.domain.Track
import app.naviamp.provider.navidrome.NavidromeProvider
import app.naviamp.provider.navidrome.toNavidromeConnection

internal data class AndroidMediaSessionActions(
    val play: () -> Unit,
    val pause: () -> Unit,
    val previous: () -> Unit,
    val next: () -> Unit,
    val queueItem: (Int) -> Unit,
    val stop: () -> Unit,
    val seek: (Long) -> Unit,
    val playMediaId: (String, Bundle?) -> Unit,
    val playSearch: (String, Bundle?) -> Unit,
    val customAction: (String, Bundle?) -> Unit,
)

/** Owns Android MediaSession lifecycle and publication; playback policy remains in shared controllers. */
internal class AndroidMediaSessionController(
    private val context: Context,
    private val storage: () -> AndroidStorageDependencies,
    private val queue: () -> List<Track>,
    private val queueIndex: () -> Int,
    private val metadata: () -> AndroidPlaybackNotificationMetadata,
    private val artwork: () -> Bitmap?,
    private val shuffleEnabled: () -> Boolean,
    private val repeatMode: () -> ServiceRepeatMode,
    private val actions: AndroidMediaSessionActions,
    private val publishBrowserToken: (MediaSessionCompat.Token) -> Unit,
    private val favoriteAction: String,
    private val shuffleAction: String,
    private val repeatAction: String,
    private val trackRadioAction: String,
    private val queueTrackPrefix: String,
) {
    private var session: MediaSessionCompat? = null
    private var browserTokenPublished = false
    private var publishedDurationMillis: Long? = null
    private var lastQueueSignature: String? = null
    private var lastMetadataSignature: String? = null
    private var lastPlaybackStateSignature: String? = null

    fun ensure(): MediaSessionCompat = session ?: MediaSessionCompat(context, "NaviampPlayback").apply {
        resetPublicationState()
        setCallback(callback())
        publishPlaybackState(this)
        if (!browserTokenPublished) {
            publishBrowserToken(sessionToken)
            browserTokenPublished = true
        }
        session = this
    }

    fun release() {
        session?.setCallback(null)
        session?.release()
        session = null
        browserTokenPublished = false
        resetPublicationState()
    }

    fun setActive(active: Boolean) {
        session?.isActive = active
    }

    fun invalidateQueue() {
        lastQueueSignature = null
    }

    fun publishQueue() {
        publishQueue(ensure())
    }

    fun update(metadata: AndroidPlaybackNotificationMetadata, artwork: Bitmap?) {
        val session = ensure()
        publishedDurationMillis = playbackDurationMillis()
        publishQueue(session)
        val signature = metadataSignature(metadata, publishedDurationMillis, artwork)
        if (signature != lastMetadataSignature) {
            lastMetadataSignature = signature
            session.setMetadata(
                MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, currentMediaId())
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, metadata.title.orEmpty())
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, metadata.subtitle.orEmpty())
                    .apply {
                        queue().getOrNull(queueIndex())?.albumTitle?.takeIf(String::isNotBlank)?.let {
                            putString(MediaMetadataCompat.METADATA_KEY_ALBUM, it)
                        }
                        publishedDurationMillis?.takeIf { it > 0L }?.let {
                            putLong(MediaMetadataCompat.METADATA_KEY_DURATION, it)
                        }
                        artwork?.let {
                            putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it)
                            putBitmap(MediaMetadataCompat.METADATA_KEY_ART, it)
                        }
                    }
                    .build(),
            )
        }
        publishPlaybackState(session)
        session.isActive = true
    }

    fun updatePlaybackState() {
        val session = ensure()
        if (publishedDurationMillis != playbackDurationMillis()) {
            update(metadata(), artwork())
            return
        }
        publishPlaybackState(session)
        session.isActive = true
    }

    private fun callback(): MediaSessionCompat.Callback = object : MediaSessionCompat.Callback() {
        override fun onPlay() = actions.play()
        override fun onPause() = actions.pause()
        override fun onSkipToPrevious() = actions.previous()
        override fun onSkipToNext() = actions.next()
        override fun onSkipToQueueItem(id: Long) = actions.queueItem(id.toInt())
        override fun onStop() = actions.stop()
        override fun onSeekTo(pos: Long) = actions.seek(pos)
        override fun onRewind() = actions.seek(
            ((AndroidPlaybackNotificationControls.positionMillis ?: 0L) - MediaSessionSeekStepMillis).coerceAtLeast(0L),
        )
        override fun onFastForward() {
            val position = AndroidPlaybackNotificationControls.positionMillis ?: 0L
            val duration = AndroidPlaybackNotificationControls.durationMillis
            actions.seek((position + MediaSessionSeekStepMillis).let { if (duration != null && duration > 0L) it.coerceAtMost(duration) else it })
        }
        override fun onPlayFromMediaId(mediaId: String, extras: Bundle?) = actions.playMediaId(mediaId, extras)
        override fun onPlayFromSearch(query: String, extras: Bundle?) = actions.playSearch(query, extras)
        override fun onCustomAction(action: String, extras: Bundle?) = actions.customAction(action, extras)
    }

    private fun publishQueue(session: MediaSessionCompat) {
        val queue = queue()
        val index = queueIndex()
        val signature = "$index:${queue.joinToString("|") { it.id.value }}"
        if (signature == lastQueueSignature) return
        lastQueueSignature = signature
        if (queue.isEmpty()) {
            session.setQueue(emptyList())
            session.setQueueTitle(null)
            return
        }
        val provider = storage().latestNavidromeSource()?.toNavidromeConnection()?.let(::NavidromeProvider)
        session.setQueueTitle("Queue")
        session.setQueue(queue.mapIndexed { itemIndex, track ->
            MediaSessionCompat.QueueItem(
                MediaDescriptionCompat.Builder()
                    .setMediaId("$queueTrackPrefix${Uri.encode(itemIndex.toString())}")
                    .setTitle(track.title)
                    .setSubtitle(track.artistName)
                    .setDescription(track.albumTitle)
                    .apply {
                        (track.coverArtId ?: track.albumId?.value)?.let { provider?.coverArtUrl(it) }?.let { setIconUri(Uri.parse(it)) }
                    }
                    .build(),
                itemIndex.toLong(),
            )
        })
    }

    private fun publishPlaybackState(session: MediaSessionCompat) {
        val signature = playbackStateSignature()
        if (signature == lastPlaybackStateSignature) return
        lastPlaybackStateSignature = signature
        val favorite = AndroidPlaybackNotificationControls.isFavorite
        session.setPlaybackState(buildPlaybackState(if (favorite) "Unfavorite" else "Favorite", if (favorite) R.drawable.ic_favorite_filled_24 else R.drawable.ic_favorite_24))
    }

    private fun buildPlaybackState(favoriteTitle: String, favoriteIcon: Int): PlaybackStateCompat =
        PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_REWIND or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_FAST_FORWARD or PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM or
                    PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH or PlaybackStateCompat.ACTION_SEEK_TO or PlaybackStateCompat.ACTION_STOP,
            )
            .addCustomAction(favoriteAction, favoriteTitle, favoriteIcon)
            .addCustomAction(shuffleAction, if (shuffleEnabled()) "Shuffle on" else "Shuffle off", R.drawable.ic_shuffle_24)
            .addCustomAction(repeatAction, when (repeatMode()) {
                ServiceRepeatMode.Off -> "Repeat off"
                ServiceRepeatMode.All -> "Repeat all"
                ServiceRepeatMode.One -> "Repeat one"
            }, R.drawable.ic_repeat_24)
            .addCustomAction(trackRadioAction, "Start song radio", R.drawable.ic_auto_radio)
            .setState(
                if (AndroidPlaybackNotificationControls.isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                AndroidPlaybackNotificationControls.positionMillis ?: PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                if (AndroidPlaybackNotificationControls.isPlaying) 1f else 0f,
            )
            .setActiveQueueItemId(queueIndex().takeIf { it in queue().indices }?.toLong() ?: -1L)
            .build()

    private fun playbackStateSignature(): String = listOf(
        AndroidPlaybackNotificationControls.isPlaying,
        AndroidPlaybackNotificationControls.positionMillis,
        AndroidPlaybackNotificationControls.durationMillis,
        AndroidPlaybackNotificationControls.canFavorite,
        AndroidPlaybackNotificationControls.isFavorite,
        shuffleEnabled(),
        repeatMode().name,
        queueIndex(),
        queue().getOrNull(queueIndex())?.id?.value.orEmpty(),
    ).joinToString("|")

    private fun metadataSignature(metadata: AndroidPlaybackNotificationMetadata, duration: Long?, artwork: Bitmap?): String =
        listOf(currentMediaId(), metadata.title.orEmpty(), metadata.subtitle.orEmpty(), duration, artwork?.generationId).joinToString("|")

    private fun currentMediaId(): String = queue().getOrNull(queueIndex())?.id?.value
        ?: metadata().coverArtUrl?.takeIf(String::isNotBlank)
        ?: "naviamp-now-playing"

    private fun playbackDurationMillis(): Long? = AndroidPlaybackNotificationControls.durationMillis?.takeIf { it > 0L }
        ?: queue().getOrNull(queueIndex())?.durationSeconds?.takeIf { it > 0 }?.let { it * 1_000L }

    private fun resetPublicationState() {
        publishedDurationMillis = null
        lastQueueSignature = null
        lastMetadataSignature = null
        lastPlaybackStateSignature = null
    }
}

private const val MediaSessionSeekStepMillis = 10_000L

internal enum class ServiceRepeatMode { Off, All, One }
