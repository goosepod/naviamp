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
    val favoritedAtIso8601: String? = null,
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
    val favoritedAtIso8601: String? = null,
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
    val bpm: Int? = null,
    val moods: List<String> = emptyList(),
    val playCount: Int? = null,
    val lastPlayedAtIso8601: String? = null,
    val musicFolderId: String? = null,
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
    val kind: String? = null,
    val agents: List<LyricAgent> = emptyList(),
    val cueLines: List<LyricCueLine> = emptyList(),
) {
    val hasTimedLines: Boolean
        get() = lines.any { it.startMillis != null }

    val hasKaraokeCues: Boolean
        get() = cueLines.any { it.cues.isNotEmpty() }
}

data class LyricLine(
    val startMillis: Long?,
    val text: String,
)

data class LyricAgent(
    val id: String,
    val name: String? = null,
    val role: String? = null,
)

data class LyricCueLine(
    val lineIndex: Int,
    val startMillis: Long?,
    val endMillis: Long?,
    val text: String,
    val agentId: String? = null,
    val cues: List<LyricCue> = emptyList(),
)

data class LyricCue(
    val startMillis: Long?,
    val endMillis: Long?,
    val text: String,
    val byteStart: Int?,
    val byteEnd: Int?,
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
    val isSmart: Boolean = false,
)

data class Genre(
    val name: String,
    val albumCount: Int? = null,
    val trackCount: Int? = null,
)

data class InternetRadioStation(
    val id: String,
    val name: String,
    val streamUrl: String,
    val homePageUrl: String? = null,
)

data class StreamRequest(
    val trackId: TrackId,
    val quality: StreamQuality,
    val startPositionSeconds: Double? = null,
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
