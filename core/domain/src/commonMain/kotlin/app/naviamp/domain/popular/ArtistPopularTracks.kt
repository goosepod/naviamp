package app.naviamp.domain.popular

import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistId
import app.naviamp.domain.Track

data class ArtistPopularTrackCandidate(
    val source: String,
    val sourceTrackId: String,
    val rank: Int,
    val title: String,
    val albumTitle: String? = null,
    val durationSeconds: Int? = null,
)

data class ArtistPopularTrackMatch(
    val candidate: ArtistPopularTrackCandidate,
    val matchedTrack: Track,
    val fetchedAtEpochMillis: Long,
)

interface ArtistPopularTracksClient {
    suspend fun popularTracks(artistName: String, limit: Int = 10): List<ArtistPopularTrackCandidate>
}

interface ArtistPopularTracksRepository {
    fun artistPopularTracks(
        sourceId: String,
        artistId: ArtistId,
        source: String = DeezerPopularTrackSource,
    ): List<ArtistPopularTrackMatch>

    fun replaceArtistPopularTracks(
        sourceId: String,
        artistId: ArtistId,
        source: String,
        candidates: List<ArtistPopularTrackCandidate>,
        matchedTracksBySourceTrackId: Map<String, Track>,
        fetchedAtEpochMillis: Long,
    )
}

class ArtistPopularTracksService(
    private val repository: ArtistPopularTracksRepository,
    private val libraryTracksForArtist: (ArtistId, Long) -> List<Track>,
    private val client: ArtistPopularTracksClient,
    private val nowMillis: () -> Long = { currentTimeMillis() },
) {
    suspend fun popularTracks(
        sourceId: String,
        artist: Artist,
        limit: Int = 10,
        forceRefresh: Boolean = false,
    ): List<ArtistPopularTrackMatch> {
        val cached = repository.artistPopularTracks(sourceId, artist.id, DeezerPopularTrackSource)
        if (!forceRefresh && cached.isNotEmpty()) return cached.take(limit)

        val candidates = client.popularTracks(artist.name, limit)
        if (candidates.isEmpty()) return cached.take(limit)

        val libraryTracks = libraryTracksForArtist(artist.id, 5_000)
        val matchedTracks = matchPopularTracks(candidates, libraryTracks)
        repository.replaceArtistPopularTracks(
            sourceId = sourceId,
            artistId = artist.id,
            source = DeezerPopularTrackSource,
            candidates = candidates,
            matchedTracksBySourceTrackId = matchedTracks,
            fetchedAtEpochMillis = nowMillis(),
        )
        return repository.artistPopularTracks(sourceId, artist.id, DeezerPopularTrackSource).take(limit)
    }
}

fun matchPopularTracks(
    candidates: List<ArtistPopularTrackCandidate>,
    libraryTracks: List<Track>,
): Map<String, Track> {
    val tracksByTitle = libraryTracks
        .groupBy { it.title.popularTrackSearchText() }

    return candidates.mapNotNull { candidate ->
        val titleKey = candidate.title.popularTrackSearchText()
        val matches = tracksByTitle[titleKey].orEmpty()
        val match = matches.bestMatchFor(candidate) ?: return@mapNotNull null
        candidate.sourceTrackId to match
    }.toMap()
}

private fun List<Track>.bestMatchFor(candidate: ArtistPopularTrackCandidate): Track? {
    if (isEmpty()) return null
    val candidateAlbum = candidate.albumTitle?.popularTrackSearchText()
    return minByOrNull { track ->
        var score = 0
        val trackAlbum = track.albumTitle?.popularTrackSearchText()
        if (candidateAlbum != null && trackAlbum != null && candidateAlbum != trackAlbum) score += 2
        val candidateDuration = candidate.durationSeconds
        val trackDuration = track.durationSeconds
        if (candidateDuration != null && trackDuration != null) {
            score += kotlin.math.abs(candidateDuration - trackDuration).coerceAtMost(60)
        }
        score
    }
}

private fun String.popularTrackSearchText(): String =
    lowercase()
        .replace(Regex("&"), " and ")
        .replace(Regex("\\([^)]*\\)|\\[[^]]*]"), " ")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

const val DeezerPopularTrackSource = "deezer"

internal expect fun currentTimeMillis(): Long
