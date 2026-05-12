package app.naviamp.domain

@JvmInline
value class ProviderId(val value: String)

@JvmInline
value class ArtistId(val value: String)

@JvmInline
value class AlbumId(val value: String)

@JvmInline
value class TrackId(val value: String)

data class Artist(
    val id: ArtistId,
    val name: String,
)

data class ArtistDetails(
    val artist: Artist,
    val albums: List<Album>,
    val info: ArtistInfo? = null,
)

data class ArtistInfo(
    val biography: String?,
    val smallImageUrl: String?,
    val mediumImageUrl: String?,
    val largeImageUrl: String?,
)

data class Album(
    val id: AlbumId,
    val title: String,
    val artistName: String,
    val coverArtId: String?,
    val recentlyAddedAtIso8601: String?,
    val releaseYear: Int? = null,
)

data class AlbumDetails(
    val album: Album,
    val tracks: List<Track>,
)

data class Track(
    val id: TrackId,
    val title: String,
    val artistId: ArtistId? = null,
    val artistName: String,
    val albumId: AlbumId? = null,
    val albumTitle: String?,
    val albumReleaseYear: Int? = null,
    val durationSeconds: Int?,
    val coverArtId: String?,
    val audioInfo: AudioInfo?,
    val replayGain: ReplayGain?,
    val favoritedAtIso8601: String? = null,
    val userRating: Int? = null,
)

data class AudioInfo(
    val codec: String?,
    val bitrateKbps: Int?,
    val contentType: String?,
    val bitDepth: Int? = null,
    val samplingRateHz: Int? = null,
)

data class ReplayGain(
    val trackGainDb: Double?,
    val albumGainDb: Double?,
    val trackPeak: Double?,
    val albumPeak: Double?,
)

data class Lyrics(
    val source: LyricsSource,
    val synced: Boolean,
    val lines: List<LyricLine>,
    val displayArtist: String? = null,
    val displayTitle: String? = null,
    val language: String? = null,
    val offsetMillis: Int = 0,
) {
    val hasTimedLines: Boolean
        get() = lines.any { it.startMillis != null }
}

data class LyricLine(
    val startMillis: Long?,
    val text: String,
)

enum class LyricsSource {
    Provider,
    Embedded,
    Lrclib,
}

data class Playlist(
    val id: String,
    val name: String,
    val trackCount: Int,
    val durationSeconds: Int? = null,
    val coverArtId: String? = null,
)

data class Genre(
    val name: String,
    val albumCount: Int? = null,
    val trackCount: Int? = null,
)

data class StreamRequest(
    val trackId: TrackId,
    val quality: StreamQuality,
)

sealed interface StreamQuality {
    data object Original : StreamQuality

    data class Transcoded(
        val codec: AudioCodec,
        val bitrateKbps: Int,
    ) : StreamQuality
}

enum class AudioCodec {
    Opus,
    Mp3,
    Aac,
}
