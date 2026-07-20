package app.naviamp.android.playback

import android.content.Context
import android.net.Uri
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import app.naviamp.android.AndroidStorageDependencies
import app.naviamp.domain.Album
import app.naviamp.domain.Artist
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Playlist
import app.naviamp.domain.Track
import app.naviamp.domain.cache.MediaSourceRepository
import app.naviamp.provider.navidrome.NavidromeProvider
import app.naviamp.provider.navidrome.toNavidromeConnection
import app.naviamp.ui.defaultRadioArtworkUrl

/** Owns Android MediaBrowser presentation; catalog loading stays in the browse controller. */
internal class AndroidAutoMediaItemFactory(
    private val context: Context,
    private val storage: () -> AndroidStorageDependencies,
    private val currentMetadata: () -> AndroidPlaybackNotificationMetadata,
) {
    fun track(
        track: Track,
        mediaId: String = AndroidAutoPlaybackControls.MediaIdTrackPrefix + listOf(
            Uri.encode(track.id.value),
            Uri.encode(track.title),
            Uri.encode(track.artistId?.value.orEmpty()),
            Uri.encode(track.artistName),
            Uri.encode(track.albumId?.value.orEmpty()),
            Uri.encode(track.albumTitle.orEmpty()),
            Uri.encode(track.durationSeconds?.toString().orEmpty()),
            Uri.encode(track.coverArtId.orEmpty()),
        ).joinToString(MediaIdPartSeparator),
        includeArt: Boolean = true,
    ): MediaBrowserCompat.MediaItem = playable(
        mediaId = mediaId,
        title = track.title,
        subtitle = listOfNotNull(track.artistName, track.albumTitle).joinToString(" - "),
        iconUri = if (includeArt) storage().autoCoverArtUrl(track) else null,
    )

    fun artist(artist: Artist): MediaBrowserCompat.MediaItem = browsable(
        mediaId = AndroidAutoPlaybackControls.MediaIdArtistPrefix + listOf(
            Uri.encode(artist.id.value),
            Uri.encode(artist.name),
        ).joinToString(MediaIdPartSeparator),
        title = artist.name,
        subtitle = "Artist",
        iconUri = storage().autoCoverArtUrl(artist),
    )

    fun album(album: Album): MediaBrowserCompat.MediaItem = browsable(
        mediaId = AndroidAutoPlaybackControls.MediaIdAlbumPrefix + listOf(
            Uri.encode(album.id.value),
            Uri.encode(album.title),
            Uri.encode(album.artistName),
        ).joinToString(MediaIdPartSeparator),
        title = album.title,
        subtitle = listOfNotNull(album.artistName, album.releaseYear?.toString()).joinToString(" - "),
        iconUri = storage().autoCoverArtUrl(album),
    )

    fun playlist(playlist: Playlist, fallbackIconUri: String? = null): MediaBrowserCompat.MediaItem = browsable(
        mediaId = "${AndroidAutoPlaybackControls.MediaIdPlaylistPrefix}${Uri.encode(playlist.id)}",
        title = playlist.name,
        subtitle = if (playlist.isSmart) "Smart playlist - ${playlist.trackCount} tracks" else "${playlist.trackCount} tracks",
        iconUri = storage().autoCoverArtUrl(playlist) ?: fallbackIconUri,
    )

    fun browsable(
        mediaId: String,
        title: String,
        subtitle: String,
        iconName: String? = null,
        iconUri: String? = null,
    ): MediaBrowserCompat.MediaItem = MediaBrowserCompat.MediaItem(
        MediaDescriptionCompat.Builder()
            .setMediaId(mediaId)
            .setTitle(title)
            .setSubtitle(subtitle)
            .apply {
                when {
                    iconName != null -> setIconUri(Uri.parse(drawableUri(iconName)))
                    iconUri != null -> setIconUri(Uri.parse(iconUri))
                }
            }
            .build(),
        MediaBrowserCompat.MediaItem.FLAG_BROWSABLE,
    )

    fun playable(mediaId: String, title: String, subtitle: String, iconUri: String? = null): MediaBrowserCompat.MediaItem =
        MediaBrowserCompat.MediaItem(
            MediaDescriptionCompat.Builder()
                .setMediaId(mediaId)
                .setTitle(title)
                .setSubtitle(subtitle)
                .apply {
                    val artUri = iconUri ?: currentMetadata().coverArtUrl
                        ?.takeIf { mediaId == AndroidAutoPlaybackControls.MediaIdNowPlaying }
                    artUri?.let { setIconUri(Uri.parse(it)) }
                }
                .build(),
            MediaBrowserCompat.MediaItem.FLAG_PLAYABLE,
        )

    fun noSource(): MutableList<MediaBrowserCompat.MediaItem> = mutableListOf(
        browsable(AndroidAutoPlaybackControls.MediaIdNoSource, "Connect Naviamp first", "Open the phone app and connect to Navidrome."),
    )

    fun drawableUri(name: String): String = "android.resource://${context.packageName}/drawable/$name"

    fun stationArtUri(station: InternetRadioStation): String {
        val artworkUrl = station.defaultRadioArtworkUrl()
        return artworkUrl.takeIf { it.startsWith("http://") || it.startsWith("https://") } ?: drawableUri("ic_auto_radio")
    }
}

internal fun MediaSourceRepository.autoCoverArtUrl(track: Track): String? {
    val coverArtId = track.coverArtId ?: track.albumId?.value ?: return null
    val connection = latestMediaSource()?.toNavidromeConnection() ?: return null
    return NavidromeProvider(connection).coverArtUrl(coverArtId)
}

internal fun MediaSourceRepository.autoCoverArtUrl(album: Album): String? {
    val connection = latestMediaSource()?.toNavidromeConnection() ?: return null
    return NavidromeProvider(connection).coverArtUrl(album.coverArtId ?: album.id.value)
}

internal fun MediaSourceRepository.autoCoverArtUrl(artist: Artist): String? {
    val connection = latestMediaSource()?.toNavidromeConnection() ?: return null
    return NavidromeProvider(connection).coverArtUrl(artist.id.value)
}

internal fun MediaSourceRepository.autoCoverArtUrl(playlist: Playlist): String? {
    val connection = latestMediaSource()?.toNavidromeConnection() ?: return null
    return NavidromeProvider(connection).coverArtUrl(playlist.coverArtId ?: playlist.id)
}
