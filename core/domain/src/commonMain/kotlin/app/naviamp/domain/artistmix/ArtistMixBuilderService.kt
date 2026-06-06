package app.naviamp.domain.artistmix

import app.naviamp.domain.Artist
import app.naviamp.domain.Track
import app.naviamp.domain.popular.ArtistPopularTracksService
import app.naviamp.domain.popular.SimilarArtistsService

class ArtistMixBuilderService(
    private val sourceId: () -> String?,
    private val artistSearch: suspend (String, Long) -> List<Artist>,
    private val randomArtists: suspend (Long) -> List<Artist>,
    private val popularTracksService: ArtistPopularTracksService,
    private val similarArtistsService: SimilarArtistsService,
) {
    suspend fun initialSuggestions(
        selectedArtists: List<Artist>,
        limit: Int = ArtistMixSuggestionLimit,
    ): List<Artist> =
        randomArtists((limit * 2).toLong()).artistMixSuggestions(selectedArtists, limit)

    suspend fun searchSuggestions(
        query: String,
        selectedArtists: List<Artist>,
        limit: Int = ArtistMixSuggestionLimit,
    ): List<Artist> =
        if (query.isBlank()) {
            initialSuggestions(selectedArtists, limit)
        } else {
            artistSearch(query, (limit * 2).toLong()).artistMixSuggestions(selectedArtists, limit)
        }

    suspend fun relatedSuggestions(
        selectedArtists: List<Artist>,
        seedArtist: Artist,
        limit: Int = ArtistMixSuggestionLimit,
    ): List<Artist> {
        val similarArtists = similarArtistsService
            .similarArtists(seedArtist.name, ArtistMixSimilarFetchLimit)
            .mapNotNull { it.matchedArtist }
        val randomTail = randomArtists((ArtistMixRandomTailCount * 4).toLong())
        return (similarArtists + randomTail).artistMixSuggestions(selectedArtists, limit)
    }

    suspend fun popularTracks(
        artist: Artist,
        limit: Int = ArtistMixPopularTrackLimit,
    ): List<Track> {
        val activeSourceId = sourceId() ?: return emptyList()
        return popularTracksService
            .popularTracks(activeSourceId, artist, limit)
            .map { it.matchedTrack }
            .distinctBy { it.id }
    }
}

fun List<Artist>.artistMixSuggestions(
    selectedArtists: List<Artist>,
    limit: Int = ArtistMixSuggestionLimit,
): List<Artist> {
    val selectedIds = selectedArtists.map { it.id }.toSet()
    return distinctBy { it.id }
        .filterNot { it.id in selectedIds }
        .take(limit)
}

fun artistMixSelectedArtistsAfterSelect(
    selectedArtists: List<Artist>,
    artist: Artist,
): List<Artist> =
    (selectedArtists + artist).distinctBy { it.id }

fun artistMixSelectedArtistsAfterRemove(
    selectedArtists: List<Artist>,
    artist: Artist,
): List<Artist> =
    selectedArtists.filterNot { it.id == artist.id }

fun artistMixPopularQueue(
    selectedArtists: List<Artist>,
    popularTracksByArtistId: Map<String, List<Track>>,
): List<Track> {
    val rows = selectedArtists.map { artist ->
        popularTracksByArtistId[artist.id.value].orEmpty().shuffled()
    }
    val mixed = mutableListOf<Track>()
    var index = 0
    while (rows.any { index < it.size }) {
        rows.forEach { tracks ->
            tracks.getOrNull(index)?.let(mixed::add)
        }
        index++
    }
    return mixed.distinctBy { it.id }
}

const val ArtistMixSuggestionLimit = 18
const val ArtistMixSimilarFetchLimit = 14
const val ArtistMixRandomTailCount = 6
const val ArtistMixPopularTrackLimit = 10
