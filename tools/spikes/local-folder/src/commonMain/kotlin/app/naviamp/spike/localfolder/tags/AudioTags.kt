package app.naviamp.spike.localfolder.tags

enum class AudioContainer { Mp3, Flac, OggVorbis, OggOpus, Mp4, MonkeysAudio, WavPack, Unknown }

/** Where embedded artwork lives, so it can be read lazily instead of during the scan. */
data class ArtworkLocation(
    val offset: Long,
    val length: Long,
    val mimeType: String?,
    /** Vorbis-in-Ogg pictures are base64 text inside the comment packet; ID3 unsync needs decoding. */
    val encoding: ArtworkEncoding = ArtworkEncoding.Raw,
)

enum class ArtworkEncoding { Raw, Base64FlacPicture, Id3Unsynchronized }

data class TagReplayGain(
    val trackGainDb: Double? = null,
    val trackPeak: Double? = null,
    val albumGainDb: Double? = null,
    val albumPeak: Double? = null,
) {
    val isEmpty: Boolean get() = trackGainDb == null && trackPeak == null && albumGainDb == null && albumPeak == null
}

data class AudioTags(
    val container: AudioContainer,
    val codec: String? = null,
    val title: String? = null,
    val artists: List<String> = emptyList(),
    val albumArtist: String? = null,
    val album: String? = null,
    val trackNumber: Int? = null,
    val trackTotal: Int? = null,
    val discNumber: Int? = null,
    val discTotal: Int? = null,
    val year: Int? = null,
    val genres: List<String> = emptyList(),
    val replayGain: TagReplayGain = TagReplayGain(),
    val durationMillis: Long? = null,
    val sampleRateHz: Int? = null,
    val channels: Int? = null,
    val bitsPerSample: Int? = null,
    val artwork: ArtworkLocation? = null,
)

/** Field accumulation shared by Vorbis comments, APEv2 items and ID3 TXXX/MP4 freeform keys. */
internal class TagFields {
    var title: String? = null
    val artists = mutableListOf<String>()
    var albumArtist: String? = null
    var album: String? = null
    var track: Pair<Int?, Int?> = null to null
    var disc: Pair<Int?, Int?> = null to null
    var trackTotal: Int? = null
    var discTotal: Int? = null
    var year: Int? = null
    val genres = mutableListOf<String>()
    var replayGain = TagReplayGain()

    fun put(rawKey: String, rawValue: String) {
        val value = rawValue.trim().trimEnd('\u0000')
        if (value.isEmpty()) return
        when (rawKey.trim().uppercase()) {
            "TITLE" -> title = title ?: value
            "ARTIST" -> artists += value
            "ALBUMARTIST", "ALBUM ARTIST", "ALBUM_ARTIST" -> albumArtist = albumArtist ?: value
            "ALBUM" -> album = album ?: value
            "TRACKNUMBER", "TRACK" -> track = numberPair(value)
            "TRACKTOTAL", "TOTALTRACKS" -> trackTotal = value.toIntOrNull()
            "DISCNUMBER", "DISC" -> disc = numberPair(value)
            "DISCTOTAL", "TOTALDISCS" -> discTotal = value.toIntOrNull()
            "DATE", "YEAR", "ORIGINALDATE", "ORIGINALYEAR" -> year = year ?: parseYear(value)
            "GENRE" -> genres += value
            "REPLAYGAIN_TRACK_GAIN" -> replayGain = replayGain.copy(trackGainDb = parseGain(value))
            "REPLAYGAIN_TRACK_PEAK" -> replayGain = replayGain.copy(trackPeak = value.toDoubleOrNull())
            "REPLAYGAIN_ALBUM_GAIN" -> replayGain = replayGain.copy(albumGainDb = parseGain(value))
            "REPLAYGAIN_ALBUM_PEAK" -> replayGain = replayGain.copy(albumPeak = value.toDoubleOrNull())
        }
    }

    fun toTags(container: AudioContainer) = AudioTags(
        container = container,
        title = title,
        artists = artists.distinct(),
        albumArtist = albumArtist,
        album = album,
        trackNumber = track.first,
        trackTotal = track.second ?: trackTotal,
        discNumber = disc.first,
        discTotal = disc.second ?: discTotal,
        year = year,
        genres = genres.distinct(),
        replayGain = replayGain,
    )
}

internal fun numberPair(value: String): Pair<Int?, Int?> {
    val parts = value.split('/')
    return parts[0].trim().toIntOrNull() to parts.getOrNull(1)?.trim()?.toIntOrNull()
}

internal fun parseYear(value: String): Int? =
    value.trim().take(4).toIntOrNull()?.takeIf { it in 1..9999 }

internal fun parseGain(value: String): Double? =
    value.trim().removeSuffix("dB").removeSuffix("db").trim().toDoubleOrNull()

/** Merges a secondary tag (ID3v1, APEv2 next to ID3v2) without overriding the primary one. */
internal fun AudioTags.withFallback(other: AudioTags?): AudioTags = if (other == null) this else copy(
    title = title ?: other.title,
    artists = artists.ifEmpty { other.artists },
    albumArtist = albumArtist ?: other.albumArtist,
    album = album ?: other.album,
    trackNumber = trackNumber ?: other.trackNumber,
    trackTotal = trackTotal ?: other.trackTotal,
    discNumber = discNumber ?: other.discNumber,
    discTotal = discTotal ?: other.discTotal,
    year = year ?: other.year,
    genres = genres.ifEmpty { other.genres },
    replayGain = TagReplayGain(
        replayGain.trackGainDb ?: other.replayGain.trackGainDb,
        replayGain.trackPeak ?: other.replayGain.trackPeak,
        replayGain.albumGainDb ?: other.replayGain.albumGainDb,
        replayGain.albumPeak ?: other.replayGain.albumPeak,
    ),
    artwork = artwork ?: other.artwork,
)
