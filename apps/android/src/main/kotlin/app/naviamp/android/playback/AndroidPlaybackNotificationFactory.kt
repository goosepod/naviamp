package app.naviamp.android.playback

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.media.session.MediaSession
import app.naviamp.android.R

/** Builds the Android media notification while the service retains lifecycle and command ownership. */
internal class AndroidPlaybackNotificationFactory(
    private val context: Context,
    private val channelId: String,
    private val contentIntent: () -> PendingIntent,
    private val deleteIntent: () -> PendingIntent,
    private val actionIntent: (action: String) -> PendingIntent,
    private val mediaSessionToken: () -> MediaSession.Token,
    private val publishMediaSession: (AndroidPlaybackNotificationMetadata, Bitmap?) -> Unit,
    private val playerColor: Int,
    private val previousAction: String,
    private val playPauseAction: String,
    private val nextAction: String,
    private val favoriteAction: String,
) {
    fun build(metadata: AndroidPlaybackNotificationMetadata, coverArt: Bitmap?): Notification {
        val playPause = if (AndroidPlaybackNotificationControls.isPlaying) {
            action(playPauseAction, android.R.drawable.ic_media_pause, "Pause")
        } else {
            action(playPauseAction, android.R.drawable.ic_media_play, "Play")
        }
        val favorite = if (AndroidPlaybackNotificationControls.isFavorite) {
            action(favoriteAction, R.drawable.ic_favorite_filled_24, "Unfavorite")
        } else {
            action(favoriteAction, R.drawable.ic_favorite_24, "Favorite")
        }
        publishMediaSession(metadata, coverArt)
        return Notification.Builder(context, channelId)
            .setContentTitle(metadata.title?.takeIf { it.isNotBlank() } ?: "Naviamp is playing")
            .setContentText(metadata.subtitle?.takeIf { it.isNotBlank() } ?: "Audio playback is active")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setLargeIcon(coverArt)
            .setContentIntent(contentIntent())
            .setDeleteIntent(deleteIntent())
            .addAction(action(previousAction, android.R.drawable.ic_media_previous, "Previous"))
            .addAction(playPause)
            .addAction(action(nextAction, android.R.drawable.ic_media_next, "Next"))
            .addAction(favorite)
            .setStyle(Notification.MediaStyle().setMediaSession(mediaSessionToken()).setShowActionsInCompactView(0, 1, 2))
            .setColor(coverArt?.dominantNotificationColor() ?: playerColor)
            .setColorized(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()
    }

    private fun action(action: String, icon: Int, title: String): Notification.Action = Notification.Action.Builder(
        Icon.createWithResource(context, icon),
        title,
        actionIntent(action),
    ).build()
}
