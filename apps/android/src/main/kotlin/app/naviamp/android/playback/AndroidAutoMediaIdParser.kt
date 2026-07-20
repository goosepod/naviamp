package app.naviamp.android.playback

import android.net.Uri
import app.naviamp.domain.playback.CatalogPlaybackIntent

/** Converts Android Auto's stable MediaBrowser IDs into portable playback selections. */
internal object AndroidAutoMediaIdParser {
    fun parse(mediaId: String, decode: (String) -> String = Uri::decode): CatalogPlaybackIntent? = when {
        mediaId == AndroidAutoPlaybackControls.MediaIdNowPlaying -> CatalogPlaybackIntent.Resume
        mediaId == AndroidAutoPlaybackControls.MediaIdRadioLibrary -> CatalogPlaybackIntent.LibraryRadio
        mediaId.startsWith(AndroidAutoPlaybackControls.MediaIdQueueTrackPrefix) ->
            mediaId.valueAfter(AndroidAutoPlaybackControls.MediaIdQueueTrackPrefix, decode).toIntOrNull()
                ?.let(CatalogPlaybackIntent::QueueItem)
        mediaId.startsWith(AndroidAutoPlaybackControls.MediaIdRadioDjPrefix) ->
            mediaId.valueAfter(AndroidAutoPlaybackControls.MediaIdRadioDjPrefix, decode).nonBlank()
                ?.let(CatalogPlaybackIntent::RadioDj)
        mediaId.startsWith(AndroidAutoPlaybackControls.MediaIdPlaylistPlayPrefix) ->
            mediaId.valueAfter(AndroidAutoPlaybackControls.MediaIdPlaylistPlayPrefix, decode).nonBlank()
                ?.let { CatalogPlaybackIntent.Playlist(it, shuffle = false) }
        mediaId.startsWith(AndroidAutoPlaybackControls.MediaIdPlaylistShufflePrefix) ->
            mediaId.valueAfter(AndroidAutoPlaybackControls.MediaIdPlaylistShufflePrefix, decode).nonBlank()
                ?.let { CatalogPlaybackIntent.Playlist(it, shuffle = true) }
        mediaId.startsWith(AndroidAutoPlaybackControls.MediaIdPlaylistTrackPrefix) ->
            mediaId.partsAfter(AndroidAutoPlaybackControls.MediaIdPlaylistTrackPrefix, decode).let { parts ->
                val playlistId = parts.getOrNull(0).orEmpty()
                val trackId = parts.getOrNull(1).orEmpty()
                if (playlistId.isBlank() || trackId.isBlank()) null else CatalogPlaybackIntent.PlaylistTrack(playlistId, trackId)
            }
        mediaId.startsWith(AndroidAutoPlaybackControls.MediaIdRadioStationPrefix) ->
            mediaId.partsAfter(AndroidAutoPlaybackControls.MediaIdRadioStationPrefix, decode).let { parts ->
                val streamUrl = parts.getOrNull(2).orEmpty()
                if (streamUrl.isBlank()) null else CatalogPlaybackIntent.InternetRadio(
                    id = parts.getOrNull(0).orEmpty(),
                    name = parts.getOrNull(1).orEmpty().ifBlank { "Internet Radio" },
                    streamUrl = streamUrl,
                    homePageUrl = parts.getOrNull(3)?.nonBlank(),
                )
            }
        mediaId.startsWith(AndroidAutoPlaybackControls.MediaIdRecentRadioPrefix) ->
            mediaId.valueAfter(AndroidAutoPlaybackControls.MediaIdRecentRadioPrefix, decode).nonBlank()
                ?.let(CatalogPlaybackIntent::RecentRadio)
        mediaId.startsWith(AndroidAutoPlaybackControls.MediaIdTrackPrefix) ->
            mediaId.partsAfter(AndroidAutoPlaybackControls.MediaIdTrackPrefix, decode).let { parts ->
                parts.getOrNull(0)?.nonBlank()?.let { trackId ->
                    CatalogPlaybackIntent.Track(
                        id = trackId,
                        title = parts.getOrNull(1)?.nonBlank(),
                        artistId = parts.getOrNull(2)?.nonBlank(),
                        artistName = parts.getOrNull(3)?.nonBlank(),
                        albumId = parts.getOrNull(4)?.nonBlank(),
                        albumTitle = parts.getOrNull(5)?.nonBlank(),
                        durationSeconds = parts.getOrNull(6)?.toIntOrNull(),
                        coverArtId = parts.getOrNull(7)?.nonBlank(),
                    )
                }
            }
        mediaId.startsWith(AndroidAutoPlaybackControls.MediaIdArtistTrackPrefix) ->
            mediaId.partsAfter(AndroidAutoPlaybackControls.MediaIdArtistTrackPrefix, decode).let { parts ->
                parts.getOrNull(2)?.nonBlank()?.let { CatalogPlaybackIntent.ArtistTrack(parts.getOrNull(0).orEmpty(), parts.getOrNull(1)?.nonBlank(), it) }
            }
        mediaId.startsWith(AndroidAutoPlaybackControls.MediaIdAlbumTrackPrefix) ->
            mediaId.partsAfter(AndroidAutoPlaybackControls.MediaIdAlbumTrackPrefix, decode).let { parts ->
                val albumId = parts.getOrNull(0).orEmpty()
                val trackId = parts.getOrNull(1).orEmpty()
                if (albumId.isBlank() || trackId.isBlank()) null else CatalogPlaybackIntent.AlbumTrack(albumId, trackId)
            }
        mediaId.startsWith(AndroidAutoPlaybackControls.MediaIdArtistPlayPrefix) -> mediaId.artistIntent(shuffle = false, decode)
        mediaId.startsWith(AndroidAutoPlaybackControls.MediaIdArtistShufflePrefix) -> mediaId.artistIntent(shuffle = true, decode)
        mediaId.startsWith(AndroidAutoPlaybackControls.MediaIdAlbumPlayPrefix) -> mediaId.albumIntent(shuffle = false, decode)
        mediaId.startsWith(AndroidAutoPlaybackControls.MediaIdAlbumShufflePrefix) -> mediaId.albumIntent(shuffle = true, decode)
        mediaId.startsWith(AndroidAutoPlaybackControls.MediaIdDownloadPrefix) ->
            mediaId.valueAfter(AndroidAutoPlaybackControls.MediaIdDownloadPrefix, decode).nonBlank()
                ?.let(CatalogPlaybackIntent::Download)
        else -> null
    }

    private fun String.artistIntent(shuffle: Boolean, decode: (String) -> String): CatalogPlaybackIntent.Artist? {
        val prefix = if (shuffle) AndroidAutoPlaybackControls.MediaIdArtistShufflePrefix else AndroidAutoPlaybackControls.MediaIdArtistPlayPrefix
        val parts = partsAfter(prefix, decode)
        val id = parts.getOrNull(0).orEmpty()
        val name = parts.getOrNull(1)?.nonBlank()
        return if (id.isBlank() && name == null) null else CatalogPlaybackIntent.Artist(id, name, shuffle)
    }

    private fun String.albumIntent(shuffle: Boolean, decode: (String) -> String): CatalogPlaybackIntent.Album? {
        val prefix = if (shuffle) AndroidAutoPlaybackControls.MediaIdAlbumShufflePrefix else AndroidAutoPlaybackControls.MediaIdAlbumPlayPrefix
        val parts = partsAfter(prefix, decode)
        val id = parts.getOrNull(0).orEmpty()
        return id.nonBlank()?.let { CatalogPlaybackIntent.Album(it, parts.getOrNull(1)?.nonBlank(), parts.getOrNull(2)?.nonBlank(), shuffle) }
    }

    private fun String.valueAfter(prefix: String, decode: (String) -> String): String = decode(removePrefix(prefix))
    private fun String.partsAfter(prefix: String, decode: (String) -> String): List<String> = removePrefix(prefix).split(MediaIdPartSeparator).map(decode)
    private fun String.nonBlank(): String? = takeIf(String::isNotBlank)
}
