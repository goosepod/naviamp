package app.naviamp.domain.home

import app.naviamp.domain.Album
import app.naviamp.domain.Artist
import app.naviamp.domain.Genre
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Playlist
import app.naviamp.domain.Track
import app.naviamp.domain.cache.ProviderResponseService
import app.naviamp.domain.provider.AlbumListType
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.settings.RecentRadioStream

data class HomeContent(
    val recentlyAddedAlbums: List<Album> = emptyList(),
    val mixAlbums: List<Album> = emptyList(),
    val recentAlbums: List<Album> = emptyList(),
    val frequentAlbums: List<Album> = emptyList(),
    val randomAlbums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val recentRadioStreams: List<RecentRadioStream> = emptyList(),
    val recentlyPlayedTracks: List<Track> = emptyList(),
    val radioStations: List<InternetRadioStation> = emptyList(),
    val recentInternetRadioStations: List<InternetRadioStation> = emptyList(),
    val genres: List<Genre> = emptyList(),
    val genreSpotlight: Genre? = null,
    val genreSpotlightAlbums: List<Album> = emptyList(),
    val decadeLabel: String = "Decade",
    val decadeFromYear: Int = 0,
    val decadeToYear: Int = 0,
    val decadeAlbums: List<Album> = emptyList(),
) {
    val isEmpty: Boolean
        get() = recentlyAddedAlbums.isEmpty() &&
            mixAlbums.isEmpty() &&
            recentAlbums.isEmpty() &&
            frequentAlbums.isEmpty() &&
            randomAlbums.isEmpty() &&
            artists.isEmpty() &&
            playlists.isEmpty() &&
            recentRadioStreams.isEmpty() &&
            recentlyPlayedTracks.isEmpty() &&
            radioStations.isEmpty() &&
            recentInternetRadioStations.isEmpty() &&
            genres.isEmpty() &&
            genreSpotlightAlbums.isEmpty() &&
            decadeAlbums.isEmpty()
}

fun HomeContent.mixBuilderAlbumCandidates(): List<Album> =
    (randomAlbums + mixAlbums + recentAlbums + frequentAlbums).distinctBy { it.id }

fun HomeContent.mixBuilderArtistCandidates(): List<Artist> =
    artists.distinctBy { it.id }

data class HomeDate(
    val year: Int,
    val dayOfYear: Int,
)

interface HomeLibraryRepository {
    fun albumYears(sourceId: String): List<HomeAlbumYear>

    fun recentlyPlayedTracks(sourceId: String, limit: Long = 12): List<Track> = emptyList()
}

data class HomeAlbumYear(
    val year: Int,
    val albumCount: Long,
)

class HomeService(
    private val provider: MediaProvider,
    private val providerResponseService: ProviderResponseService? = null,
    private val libraryRepository: HomeLibraryRepository? = null,
    private val sourceId: String? = null,
    private val date: HomeDate,
) {
    suspend fun load(
        recentRadioStreams: List<RecentRadioStream> = emptyList(),
        recentInternetRadioStations: List<InternetRadioStation> = emptyList(),
        artistLimit: Int = HomeDefaultArtistLimit,
    ): HomeContent {
        val genres = runCatching { provider.genres(limit = 12) }
            .getOrDefault(emptyList())
            .rotatedBy(date.dayOfYear)
        val genreSpotlight = genres.firstOrNull()
        val decadePick = pickHomeDecade()

        return HomeContent(
            recentlyAddedAlbums = runCatching { albumList(AlbumListType.Newest, limit = 8) }.getOrDefault(emptyList()),
            mixAlbums = runCatching { albumList(AlbumListType.Random, limit = 8) }.getOrDefault(emptyList()),
            recentAlbums = runCatching { albumList(AlbumListType.Recent, limit = 6) }.getOrDefault(emptyList()),
            frequentAlbums = runCatching { albumList(AlbumListType.Frequent, limit = 6) }.getOrDefault(emptyList()),
            randomAlbums = runCatching { albumList(AlbumListType.Random, limit = 6) }.getOrDefault(emptyList()),
            artists = runCatching { artists(limit = artistLimit) }.getOrDefault(emptyList()),
            playlists = runCatching { playlists(limit = 50) }.getOrDefault(emptyList()),
            recentRadioStreams = recentRadioStreams,
            recentlyPlayedTracks = sourceId
                ?.let { id -> runCatching { libraryRepository?.recentlyPlayedTracks(id, 12) }.getOrNull() }
                .orEmpty(),
            radioStations = runCatching { internetRadioStations() }.getOrDefault(emptyList()),
            recentInternetRadioStations = recentInternetRadioStations,
            genres = genres,
            genreSpotlight = genreSpotlight,
            genreSpotlightAlbums = genreSpotlight?.let { genre ->
                runCatching { albumsByGenre(genre.name, limit = 6) }.getOrDefault(emptyList())
            }.orEmpty(),
            decadeLabel = decadePick?.label ?: "Decade",
            decadeFromYear = decadePick?.fromYear ?: 0,
            decadeToYear = decadePick?.toYear ?: 0,
            decadeAlbums = decadePick?.albums.orEmpty(),
        )
    }

    private suspend fun pickHomeDecade(): HomeDecadePick? {
        val candidates = sourceId
            ?.let { libraryRepository?.albumYears(it) }
            .orEmpty()
            .toHomeDecadeCandidates(date)
            .ifEmpty { fallbackHomeDecadeCandidates(date) }

        return candidates.firstNotNullOfOrNull { candidate ->
            val albums = runCatching {
                albumsByYear(candidate.fromYear, candidate.toYear, limit = 6)
            }.getOrDefault(emptyList())
            albums.takeIf { it.isNotEmpty() }?.let {
                HomeDecadePick(
                    label = homeDecadeLabel(candidate.fromYear),
                    fromYear = candidate.fromYear,
                    toYear = candidate.toYear,
                    albums = it,
                )
            }
        }
    }

    private suspend fun albumList(type: AlbumListType, limit: Int): List<Album> =
        providerResponseService?.albumList(provider, type, limit)
            ?: provider.albumList(type, limit)

    private suspend fun albumsByGenre(genre: String, limit: Int): List<Album> =
        providerResponseService?.albumsByGenre(provider, genre, limit)
            ?: provider.albumsByGenre(genre, limit)

    private suspend fun albumsByYear(fromYear: Int, toYear: Int, limit: Int): List<Album> =
        providerResponseService?.albumsByYear(provider, fromYear, toYear, limit)
            ?: provider.albumsByYear(fromYear, toYear, limit)

    private suspend fun artists(limit: Int): List<Artist> =
        providerResponseService?.artists(provider, limit)
            ?: provider.artists(limit)

    private suspend fun playlists(limit: Int): List<Playlist> =
        providerResponseService?.playlists(provider, limit)
            ?: provider.playlists(limit)

    private suspend fun internetRadioStations(): List<InternetRadioStation> =
        providerResponseService?.internetRadioStations(provider)
            ?: provider.internetRadioStations()
}

data class HomeStation(
    val id: String,
    val title: String,
    val subtitle: String,
)

fun homeStations(homeContent: HomeContent): List<HomeStation> =
    buildList {
        add(HomeStation(HomeStationLibrary, "Library Radio", "Random tracks from your full library"))
        add(HomeStation(HomeStationRandomAlbum, "Random Album Radio", "Start from a random album"))
        homeContent.genres.take(3).forEach { genre ->
            add(HomeStation(homeGenreStationId(genre.name), "${genre.name} Radio", "A random ${genre.name} station"))
        }
        if (homeContent.decadeAlbums.isNotEmpty()) {
            add(
                HomeStation(
                    homeDecadeStationId(homeContent.decadeFromYear, homeContent.decadeToYear),
                    "${homeContent.decadeLabel} Radio",
                    "Random songs from ${homeContent.decadeLabel}",
                ),
            )
        }
    }

fun homeGenreStationId(genre: String): String =
    "$HomeStationGenrePrefix$genre"

fun homeDecadeStationId(fromYear: Int, toYear: Int): String =
    "$HomeStationDecadePrefix$fromYear:$toYear"

fun parseHomeGenreStationId(id: String): String? =
    id.takeIf { it.startsWith(HomeStationGenrePrefix) }?.removePrefix(HomeStationGenrePrefix)

fun parseHomeDecadeStationId(id: String): HomeDecadeRange? {
    val value = id.takeIf { it.startsWith(HomeStationDecadePrefix) }
        ?.removePrefix(HomeStationDecadePrefix)
        ?: return null
    val years = value.split(":")
    val fromYear = years.getOrNull(0)?.toIntOrNull() ?: return null
    val toYear = years.getOrNull(1)?.toIntOrNull() ?: return null
    return HomeDecadeRange(fromYear, toYear)
}

data class HomeDecadeRange(
    val fromYear: Int,
    val toYear: Int,
)

private data class HomeDecadePick(
    val label: String,
    val fromYear: Int,
    val toYear: Int,
    val albums: List<Album>,
)

private data class HomeDecadeCandidate(
    val fromYear: Int,
    val toYear: Int,
)

private fun List<HomeAlbumYear>.toHomeDecadeCandidates(date: HomeDate): List<HomeDecadeCandidate> =
    groupBy { it.year.floorToDecade() }
        .map { (decade, years) ->
            decade to years.sumOf { it.albumCount }
        }
        .sortedWith(compareByDescending<Pair<Int, Long>> { it.second }.thenByDescending { it.first })
        .take(HomeDecadeCandidateLimit)
        .rotatedBy(date.dayOfYear)
        .map { (decade, _) -> HomeDecadeCandidate(decade, decade + 9) }

private fun fallbackHomeDecadeCandidates(date: HomeDate): List<HomeDecadeCandidate> {
    val currentDecade = date.year.floorToDecade()
    return generateSequence(currentDecade) { previous ->
        (previous - 10).takeIf { it >= HomeEarliestFallbackDecade }
    }
        .take(HomeDecadeCandidateLimit)
        .toList()
        .rotatedBy(date.dayOfYear)
        .map { decade -> HomeDecadeCandidate(decade, minOf(decade + 9, date.year)) }
}

private fun homeDecadeLabel(fromYear: Int): String =
    "The ${fromYear}s"

fun Int.floorToDecade(): Int =
    (this / 10) * 10

fun <T> List<T>.rotatedBy(offset: Int): List<T> {
    if (isEmpty()) return this
    val normalizedOffset = offset.floorMod(size)
    return drop(normalizedOffset) + take(normalizedOffset)
}

private fun Int.floorMod(divisor: Int): Int =
    ((this % divisor) + divisor) % divisor

const val HomeStationLibrary = "library"
const val HomeStationRandomAlbum = "random-album"
const val HomeDefaultArtistLimit = 50

private const val HomeStationGenrePrefix = "genre:"
private const val HomeStationDecadePrefix = "decade:"
private const val HomeDecadeCandidateLimit = 8
private const val HomeEarliestFallbackDecade = 1950
