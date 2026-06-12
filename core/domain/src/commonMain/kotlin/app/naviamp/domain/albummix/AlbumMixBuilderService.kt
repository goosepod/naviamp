package app.naviamp.domain.albummix

import app.naviamp.domain.Album
import app.naviamp.domain.Artist
import app.naviamp.domain.Track
import app.naviamp.domain.home.HomeContent
import app.naviamp.domain.home.mixBuilderAlbumCandidates
import app.naviamp.domain.popular.SimilarArtistsService
import app.naviamp.domain.provider.AlbumListType
import app.naviamp.domain.provider.MediaProvider

class AlbumMixBuilderService(
    private val albumSearch: suspend (String, Long) -> List<Album>,
    private val randomAlbums: suspend (Long) -> List<Album>,
    private val albumsForArtist: suspend (Artist, Long) -> List<Album>,
    private val albumTracks: suspend (Album, Long) -> List<Track>,
    private val similarArtistsService: SimilarArtistsService,
) {
    suspend fun initialSuggestions(
        selectedAlbums: List<Album>,
        limit: Int = AlbumMixSuggestionLimit,
    ): List<Album> =
        randomAlbums((limit * 2).toLong()).albumMixSuggestions(selectedAlbums, limit)

    suspend fun searchSuggestions(
        query: String,
        selectedAlbums: List<Album>,
        limit: Int = AlbumMixSuggestionLimit,
    ): List<Album> =
        if (query.isBlank()) {
            initialSuggestions(selectedAlbums, limit)
        } else {
            albumSearch(query, (limit * 2).toLong()).albumMixSuggestions(selectedAlbums, limit)
        }

    suspend fun relatedSuggestions(
        selectedAlbums: List<Album>,
        seedAlbum: Album,
        limit: Int = AlbumMixSuggestionLimit,
    ): List<Album> {
        val selectedIds = selectedAlbums.map { it.id }.toSet()
        val similarAlbums = similarArtistsService
            .similarArtists(seedAlbum.artistName, AlbumMixSimilarFetchLimit)
            .mapNotNull { it.matchedArtist }
            .flatMap { artist -> albumsForArtist(artist, AlbumMixAlbumsPerSimilarArtistLimit.toLong()) }
            .distinctBy { it.id }
            .filterNot { it.id in selectedIds }
            .take(limit)
        val randomTailLimit = minOf(AlbumMixRandomTailCount, limit - similarAlbums.size)
        if (randomTailLimit <= 0) return similarAlbums
        val randomTail = randomAlbums((AlbumMixRandomTailCount * 4).toLong())
            .distinctBy { it.id }
            .filterNot { it.id in selectedIds || similarAlbums.any { similar -> similar.id == it.id } }
            .take(randomTailLimit)
        return similarAlbums + randomTail
    }

    suspend fun selectedTracks(
        album: Album,
        limit: Int = AlbumMixTracksPerAlbum,
    ): List<Track> =
        albumTracks(album, 200)
            .shuffled()
            .distinctBy { it.id }
            .take(limit)
}

fun List<Album>.albumMixSuggestions(
    selectedAlbums: List<Album>,
    limit: Int = AlbumMixSuggestionLimit,
): List<Album> {
    val selectedIds = selectedAlbums.map { it.id }.toSet()
    return distinctBy { it.id }
        .filterNot { it.id in selectedIds }
        .take(limit)
}

fun albumMixBuilderService(
    sourceId: () -> String?,
    provider: () -> MediaProvider?,
    homeContent: () -> HomeContent,
    localAlbumSearch: suspend (sourceId: String, query: String, limit: Long) -> List<Album>,
    localAlbumTracks: suspend (sourceId: String, album: Album, limit: Long) -> List<Track>,
    providerAlbumTracks: suspend (provider: MediaProvider, album: Album) -> List<Track>,
    similarArtistsService: SimilarArtistsService,
): AlbumMixBuilderService =
    AlbumMixBuilderService(
        albumSearch = { query, limit ->
            sourceId()
                ?.let { activeSourceId -> localAlbumSearch(activeSourceId, query, limit) }
                .orEmpty()
                .ifEmpty { provider()?.search(query, limit.toInt())?.albums.orEmpty() }
        },
        randomAlbums = { limit ->
            homeContent().mixBuilderAlbumCandidates()
                .shuffled()
                .take(limit.toInt())
                .ifEmpty {
                    provider()?.albumList(AlbumListType.Random, limit.toInt())?.shuffled().orEmpty()
                }
        },
        albumsForArtist = { artist, limit ->
            sourceId()
                ?.let { activeSourceId -> localAlbumSearch(activeSourceId, artist.name, limit) }
                .orEmpty()
                .filter { album -> album.artistName.equals(artist.name, ignoreCase = true) }
        },
        albumTracks = { album, limit ->
            val localTracks = sourceId()
                ?.let { activeSourceId -> localAlbumTracks(activeSourceId, album, limit) }
                .orEmpty()
            val providerTracks = provider()
                ?.let { activeProvider ->
                    runCatching { providerAlbumTracks(activeProvider, album) }.getOrDefault(emptyList())
                }
                .orEmpty()
            providerTracks.ifEmpty { localTracks }.take(limit.toInt())
        },
        similarArtistsService = similarArtistsService,
    )

fun albumMixSelectedAlbumsAfterSelect(
    selectedAlbums: List<Album>,
    album: Album,
): List<Album> =
    (selectedAlbums + album).distinctBy { it.id }

fun albumMixSelectedAlbumsAfterRemove(
    selectedAlbums: List<Album>,
    album: Album,
): List<Album> =
    selectedAlbums.filterNot { it.id == album.id }

fun albumMixTrackQueue(
    selectedAlbums: List<Album>,
    tracksByAlbumId: Map<String, List<Track>>,
): List<Track> {
    val rows = selectedAlbums.map { album ->
        tracksByAlbumId[album.id.value].orEmpty().shuffled()
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

const val AlbumMixSuggestionLimit = 18
const val AlbumMixSimilarFetchLimit = 14
const val AlbumMixAlbumsPerSimilarArtistLimit = 3
const val AlbumMixRandomTailCount = 6
const val AlbumMixTracksPerAlbum = 3
