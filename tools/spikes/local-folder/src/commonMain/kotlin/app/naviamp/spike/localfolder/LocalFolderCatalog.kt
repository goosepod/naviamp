package app.naviamp.spike.localfolder

import app.naviamp.domain.AlbumId
import app.naviamp.domain.ArtistId
import app.naviamp.domain.AudioInfo
import app.naviamp.domain.ReplayGain
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.playback.PlaybackRandomAccessSource
import app.naviamp.spike.localfolder.tags.AudioTags

private const val UnknownArtist = "Unknown Artist"

/**
 * Maps one scanned file to the existing provider-neutral [Track], so library_track, downloaded-style
 * availability and the #620 offline playlist snapshots can store it without a parallel model.
 * IDs are derived from the source and the file's relative path; a detected move keeps the old ID.
 */
fun localFolderTrack(sourceId: String, file: ScannedFile, tags: AudioTags, existingTrackId: String? = null): Track {
    val folder = file.relativePath.substringBeforeLast('/', "")
    val title = tags.title ?: file.entry.name.substringBeforeLast('.')
    val artist = tags.artists.firstOrNull() ?: tags.albumArtist ?: UnknownArtist
    val albumArtist = tags.albumArtist ?: artist
    // Untagged files group by their folder, which is how most players present loose rips.
    val album = tags.album ?: folder.substringAfterLast('/').ifEmpty { null }
    return Track(
        id = TrackId(existingTrackId ?: localId("t", sourceId, file.relativePath)),
        title = title,
        artistId = ArtistId(localId("ar", sourceId, artist.lowercase())),
        artistName = tags.artists.joinToString(" / ").ifEmpty { artist },
        albumId = album?.let { AlbumId(localId("al", sourceId, "${albumArtist.lowercase()}\u0000${it.lowercase()}")) },
        albumTitle = album,
        albumReleaseYear = tags.year,
        durationSeconds = tags.durationMillis?.let { ((it + 500) / 1000).toInt() },
        coverArtId = if (tags.artwork != null) "lf-embedded:${localId("t", sourceId, file.relativePath)}" else null,
        audioInfo = AudioInfo(
            codec = tags.codec ?: file.entry.name.substringAfterLast('.').uppercase(),
            bitrateKbps = null,
            contentType = file.entry.mimeType,
            bitDepth = tags.bitsPerSample,
            samplingRateHz = tags.sampleRateHz,
        ),
        replayGain = tags.replayGain.takeUnless { it.isEmpty }?.let {
            ReplayGain(it.trackGainDb, it.albumGainDb, it.trackPeak, it.albumPeak)
        },
        genres = tags.genres,
        musicFolderId = folder.ifEmpty { null },
        discNumber = tags.discNumber,
        trackNumber = tags.trackNumber,
    )
}

/** FNV-1a like stableMediaSourceId; prefixed so local IDs never collide with server IDs. */
fun localId(kind: String, sourceId: String, key: String): String {
    var hash = 14695981039346656037UL
    "$sourceId\u0000$key".encodeToByteArray().forEach { byte ->
        hash = hash xor byte.toUByte().toULong()
        hash *= 1099511628211UL
    }
    return "lf-$kind-${hash.toString(16).padStart(16, '0')}"
}

/**
 * Bridges a host byte source to the existing BASS FILEPROCS path, so SAF and bookmark-backed files
 * play through BASS_StreamCreateFileUser without copying and without a filesystem path.
 * PlaybackRandomAccessSource has no close today; production needs BassFileCallbacks.close to reach it.
 */
class LocalFolderRandomAccessSource(private val source: LocalByteSource) : PlaybackRandomAccessSource, AutoCloseable {
    override val lengthBytes: Long = source.lengthBytes

    override suspend fun read(offset: Long, count: Int): ByteArray {
        val buffer = ByteArray(minOf(count.toLong(), lengthBytes - offset).coerceAtLeast(0).toInt())
        val read = source.readFully(offset, buffer, 0, buffer.size)
        return if (read == buffer.size) buffer else buffer.copyOf(read)
    }

    override fun close() = source.close()
}
