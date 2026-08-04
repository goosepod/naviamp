package app.naviamp.storage

import app.naviamp.domain.AlbumId
import app.naviamp.domain.ArtistId
import app.naviamp.domain.AudioInfo
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.cache.PlaybackHistoryRepository

data class StoragePlaybackHistoryItem(
    val track: Track,
    val playedAtEpochMillis: Long,
)

/** Portable playback-history SQL mapping shared by every host. */
class StoragePlaybackHistoryStore(
    private val queries: NaviampStorageQueries,
) : PlaybackHistoryRepository<StoragePlaybackHistoryItem> {
    override fun playbackHistory(sourceId: String, limit: Int): List<StoragePlaybackHistoryItem> =
        queries.selectPlaybackHistory(sourceId, limit.toLong()).executeAsList().map { row ->
            StoragePlaybackHistoryItem(row.toTrack(), row.played_at_epoch_millis)
        }

    override fun recordPlaybackHistory(sourceId: String, track: Track, playedAtEpochMillis: Long) {
        queries.upsertPlaybackHistory(
            source_id = sourceId,
            remote_track_id = track.id.value,
            title = track.title,
            artist_id = track.artistId?.value,
            artist_name = track.artistName,
            album_id = track.albumId?.value,
            album_title = track.albumTitle,
            album_release_year = track.albumReleaseYear?.toLong(),
            duration_seconds = track.durationSeconds?.toLong(),
            cover_art_id = track.coverArtId,
            audio_codec = track.audioInfo?.codec,
            audio_bitrate_kbps = track.audioInfo?.bitrateKbps?.toLong(),
            audio_content_type = track.audioInfo?.contentType,
            audio_bit_depth = track.audioInfo?.bitDepth?.toLong(),
            audio_sampling_rate_hz = track.audioInfo?.samplingRateHz?.toLong(),
            favorited_at_iso8601 = track.favoritedAtIso8601,
            user_rating = track.userRating?.toLong(),
            played_at_epoch_millis = playedAtEpochMillis,
        )
    }

    fun clear() {
        queries.clearPlaybackHistory()
    }
}

private fun Playback_history.toTrack(): Track = Track(
    id = TrackId(remote_track_id),
    title = title,
    artistId = artist_id?.let(::ArtistId),
    artistName = artist_name,
    albumId = album_id?.let(::AlbumId),
    albumTitle = album_title,
    albumReleaseYear = album_release_year?.toInt(),
    durationSeconds = duration_seconds?.toInt(),
    coverArtId = cover_art_id,
    audioInfo = AudioInfo(
        codec = audio_codec,
        bitrateKbps = audio_bitrate_kbps?.toInt(),
        contentType = audio_content_type,
        bitDepth = audio_bit_depth?.toInt(),
        samplingRateHz = audio_sampling_rate_hz?.toInt(),
    ).takeIf {
        it.codec != null || it.bitrateKbps != null || it.contentType != null ||
            it.bitDepth != null || it.samplingRateHz != null
    },
    replayGain = null,
    favoritedAtIso8601 = favorited_at_iso8601,
    userRating = user_rating?.toInt(),
)
