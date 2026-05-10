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

data class Album(
    val id: AlbumId,
    val title: String,
    val artistName: String,
    val coverArtId: String?,
    val recentlyAddedAtIso8601: String?,
)

data class AlbumDetails(
    val album: Album,
    val tracks: List<Track>,
)

data class Track(
    val id: TrackId,
    val title: String,
    val artistName: String,
    val albumTitle: String?,
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
)

data class ReplayGain(
    val trackGainDb: Double?,
    val albumGainDb: Double?,
    val trackPeak: Double?,
    val albumPeak: Double?,
)

data class Playlist(
    val id: String,
    val name: String,
    val trackCount: Int,
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
