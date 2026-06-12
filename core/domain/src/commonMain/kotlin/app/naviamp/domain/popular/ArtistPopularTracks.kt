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

data class ArtistPopularTracksResult(
    val source: String,
    val candidates: List<ArtistPopularTrackCandidate> = emptyList(),
    val matchedTracksBySourceTrackId: Map<String, Track> = emptyMap(),
)

data class SimilarArtistCandidate(
    val source: String,
    val sourceArtistId: String,
    val name: String,
    val imageUrl: String? = null,
    val externalUrl: String? = null,
)

data class SimilarArtistMatch(
    val candidate: SimilarArtistCandidate,
    val matchedArtist: Artist? = null,
)

interface ArtistPopularTracksClient {
    val source: String
    suspend fun popularTracks(artist: Artist, limit: Int = 10): ArtistPopularTracksResult
}

interface SimilarArtistsClient {
    suspend fun similarArtists(artist: Artist, limit: Int = 10): List<SimilarArtistCandidate>
}

interface ArtistPopularTracksRepository {
    fun artistPopularTracks(
        sourceId: String,
        artistId: ArtistId,
        source: String = NavidromeAgentMetadataSource,
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
    private val libraryTracksForArtist: suspend (Artist, Long) -> List<Track>,
    private val client: ArtistPopularTracksClient,
    private val nowMillis: () -> Long = { currentTimeMillis() },
) {
    suspend fun popularTracks(
        sourceId: String,
        artist: Artist,
        limit: Int = 10,
        forceRefresh: Boolean = false,
    ): List<ArtistPopularTrackMatch> {
        val source = client.source
        val cached = repository.artistPopularTracks(sourceId, artist.id, source)
        if (!forceRefresh && cached.isNotEmpty()) return cached.take(limit)

        val result = client.popularTracks(artist, limit)
        val candidates = result.candidates
        if (candidates.isEmpty()) return cached.take(limit)

        val matchedTracks = result.matchedTracksBySourceTrackId.ifEmpty {
            val libraryTracks = libraryTracksForArtist(artist, 5_000)
            matchPopularTracks(candidates, libraryTracks)
        }
        repository.replaceArtistPopularTracks(
            sourceId = sourceId,
            artistId = artist.id,
            source = result.source,
            candidates = candidates,
            matchedTracksBySourceTrackId = matchedTracks,
            fetchedAtEpochMillis = nowMillis(),
        )
        return repository.artistPopularTracks(sourceId, artist.id, result.source).take(limit)
    }
}

class SimilarArtistsService(
    private val libraryArtistsSearch: suspend (String, Long) -> List<Artist>,
    private val client: SimilarArtistsClient,
) {
    suspend fun similarArtists(
        artist: Artist,
        limit: Int = 10,
    ): List<SimilarArtistMatch> {
        val candidates = client.similarArtists(artist, limit)
        if (candidates.isEmpty()) return emptyList()

        val localArtistsByName = candidates
            .flatMap { candidate -> libraryArtistsSearch(candidate.name, 8) }
            .distinctBy { it.id }
            .associateBy { it.name.artistSearchText() }

        return candidates.map { candidate ->
            SimilarArtistMatch(
                candidate = candidate,
                matchedArtist = localArtistsByName[candidate.name.artistSearchText()],
            )
        }
    }

    suspend fun similarArtists(
        artistName: String,
        limit: Int = 10,
    ): List<SimilarArtistMatch> {
        val resolvedArtist = libraryArtistsSearch(artistName, 1)
            .firstOrNull { it.name.equals(artistName, ignoreCase = true) }
            ?: return emptyList()
        return similarArtists(resolvedArtist, limit)
    }
}

fun matchPopularTracks(
    candidates: List<ArtistPopularTrackCandidate>,
    libraryTracks: List<Track>,
): Map<String, Track> {
    val tracksByTitle = libraryTracks.flatMap { track ->
        track.title.popularTrackSearchKeys().map { key -> key to track }
    }.groupBy(
        keySelector = { it.first },
        valueTransform = { it.second },
    )

    return candidates.mapNotNull { candidate ->
        val candidateKeys = candidate.title.popularTrackSearchKeys()
        val matches = candidateKeys
            .flatMap { key -> tracksByTitle[key].orEmpty() }
            .distinctBy { it.id }
            .ifEmpty {
                libraryTracks
                    .filter { track -> candidateKeys.any { key -> track.title.popularTrackSearchKeys().any { it.fuzzyTitleMatches(key) } } }
                    .distinctBy { it.id }
            }
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
        .replace(Regex("\\b(feat|featuring|ft)\\.?\\b.*$"), " ")
        .replace(Regex("\\s+[-–—]\\s+.*\\b(remaster|remastered|remix|edit|version|mono|stereo|live|explicit|clean|deluxe|bonus)\\b.*$"), " ")
        .replace(Regex("\\b(\\d{4}\\s*)?(remaster(ed)?|radio edit|single version|album version|mono|stereo|explicit|clean)\\b"), " ")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

private fun String.popularTrackSearchKeys(): Set<String> {
    val primary = popularTrackSearchText()
    if (primary.isBlank()) return emptySet()
    val withoutTrailingVersion = primary
        .replace(Regex("\\b(remaster(ed)?|remix|edit|version|mono|stereo|live|explicit|clean|deluxe|bonus)\\b.*$"), " ")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
    return setOf(primary, withoutTrailingVersion)
        .filter { it.length >= 2 }
        .toSet()
}

private fun String.fuzzyTitleMatches(other: String): Boolean {
    if (this == other) return true
    if (length < 4 || other.length < 4) return false
    return startsWith(other) || other.startsWith(this)
}

private fun String.artistSearchText(): String =
    lowercase()
        .replace(Regex("&"), " and ")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

const val NavidromeAgentMetadataSource = "navidrome"

internal expect fun currentTimeMillis(): Long
