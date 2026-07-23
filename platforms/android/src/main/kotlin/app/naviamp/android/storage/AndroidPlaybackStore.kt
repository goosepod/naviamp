package app.naviamp.android

import app.naviamp.domain.AlbumId
import app.naviamp.domain.ArtistId
import app.naviamp.domain.AudioInfo
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.settings.PlaybackSessionSettings
import app.naviamp.storage.NaviampStorageQueries
import app.naviamp.storage.Playback_history
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AndroidPlaybackStore(
    private val queries: NaviampStorageQueries,
    private val json: Json,
    private val nowMillis: () -> Long,
) {
    fun loadPlaybackSession(sourceId: String?): PlaybackSessionSettings? =
        queries.selectPlaybackSession(requirePlaybackSessionSourceId(sourceId))
            .executeAsOneOrNull()
            ?.let { payload ->
                runCatching { json.decodeFromString<PlaybackSessionSettings>(payload) }.getOrNull()
            }

    fun savePlaybackSession(session: PlaybackSessionSettings?, sourceId: String?) {
        val requiredSourceId = requirePlaybackSessionSourceId(sourceId)
        if (session == null) {
            queries.deletePlaybackSession(requiredSourceId)
            return
        }
        queries.upsertPlaybackSession(
            source_id = requiredSourceId,
            payload = json.encodeToString(session),
            updated_at_epoch_millis = nowMillis(),
        )
    }

    fun playbackHistory(sourceId: String, limit: Int): List<AndroidPlaybackHistoryItem> =
        queries.selectPlaybackHistory(sourceId, limit.toLong())
            .executeAsList()
            .map { row ->
                AndroidPlaybackHistoryItem(row.toTrack(), row.played_at_epoch_millis)
            }

    fun recordPlaybackHistory(sourceId: String, track: Track, playedAtEpochMillis: Long) {
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

    fun clearPlaybackHistory() {
        queries.clearPlaybackHistory()
    }

    private fun requirePlaybackSessionSourceId(sourceId: String?): String =
        requireNotNull(sourceId?.takeIf { it.isNotBlank() }) {
            "Android playback sessions require a sourceId."
        }
}

data class AndroidPlaybackHistoryItem(
    val track: Track,
    val playedAtEpochMillis: Long,
)

private fun Playback_history.toTrack(): Track =
    Track(
        id = TrackId(remote_track_id),
        title = title,
        artistId = artist_id?.let { ArtistId(it) },
        artistName = artist_name,
        albumId = album_id?.let { AlbumId(it) },
        albumTitle = album_title,
        albumReleaseYear = album_release_year?.toInt(),
        durationSeconds = duration_seconds?.toInt(),
        coverArtId = cover_art_id,
        audioInfo = audioInfo(audio_codec, audio_bitrate_kbps, audio_content_type, audio_bit_depth, audio_sampling_rate_hz),
        replayGain = null,
        favoritedAtIso8601 = favorited_at_iso8601,
        userRating = user_rating?.toInt(),
    )

private fun audioInfo(
    codec: String?,
    bitrateKbps: Long?,
    contentType: String?,
    bitDepth: Long?,
    samplingRateHz: Long?,
): AudioInfo? =
    AudioInfo(
        codec = codec,
        bitrateKbps = bitrateKbps?.toInt(),
        contentType = contentType,
        bitDepth = bitDepth?.toInt(),
        samplingRateHz = samplingRateHz?.toInt(),
    ).takeIf {
        it.codec != null ||
            it.bitrateKbps != null ||
            it.contentType != null ||
            it.bitDepth != null ||
            it.samplingRateHz != null
    }
