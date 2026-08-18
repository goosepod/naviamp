package app.naviamp.presentation

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistId
import app.naviamp.domain.Genre
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.albummix.AlbumMixBuilderService
import app.naviamp.domain.artistmix.ArtistMixBuilderService
import app.naviamp.domain.genremix.GenreMixBuilderService
import app.naviamp.domain.library.LibraryGenreOntologyNode
import app.naviamp.domain.library.LibraryGenreOntologyProjection
import app.naviamp.domain.popular.ArtistPopularTrackCandidate
import app.naviamp.domain.popular.ArtistPopularTracksClient
import app.naviamp.domain.popular.ArtistPopularTracksResult
import app.naviamp.domain.popular.ArtistPopularTracksService
import app.naviamp.domain.popular.SessionArtistPopularTracksRepository
import app.naviamp.domain.popular.SimilarArtistCandidate
import app.naviamp.domain.popular.SimilarArtistsClient
import app.naviamp.domain.popular.SimilarArtistsService
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NaviampCoreStandardMixControllerTest {
    @Test
    fun artistBuilderOwnsSuggestionsSelectionTracksAndPlaybackIntent() = runTest {
        val fixture = fixture()
        fixture.controller.initializeArtist()

        fixture.controller.execute(
            NaviampCoreCommand.MixBuilder.Artist(
                NaviampCoreCommand.ArtistAction.Select(fixture.store.state.value.shell.artistMixBuilder.suggestedArtists.first()),
            ),
        )
        fixture.controller.execute(
            NaviampCoreCommand.MixBuilder.Artist(NaviampCoreCommand.ArtistAction.Play),
        )

        val ui = fixture.store.state.value.shell.artistMixBuilder
        assertEquals(listOf("artist-a"), ui.selectedArtists.map { it.id })
        assertEquals(listOf("artist-b"), ui.suggestedArtists.map { it.id })
        assertEquals(listOf("artist-a:popular-artist-a"), fixture.playback.artistPlays)
        assertNull(ui.status)
    }

    @Test
    fun albumBuilderOwnsSearchSelectionSeedTracksAndPlaybackIntent() = runTest {
        val fixture = fixture()
        fixture.controller.dispatch(
            NaviampCoreCommand.MixBuilder.Album(NaviampCoreCommand.AlbumAction.ChangeQuery("Album A")),
        )
        fixture.controller.execute(
            NaviampCoreCommand.MixBuilder.Album(NaviampCoreCommand.AlbumAction.Search),
        )
        fixture.controller.execute(
            NaviampCoreCommand.MixBuilder.Album(
                NaviampCoreCommand.AlbumAction.Select(fixture.store.state.value.shell.albumMixBuilder.suggestedAlbums.first()),
            ),
        )
        fixture.controller.execute(
            NaviampCoreCommand.MixBuilder.Album(NaviampCoreCommand.AlbumAction.Play),
        )

        val ui = fixture.store.state.value.shell.albumMixBuilder
        assertEquals("Album A", ui.query)
        assertEquals(listOf("album-a"), ui.selectedAlbums.map { it.id })
        assertEquals(listOf("album-b"), ui.suggestedAlbums.map { it.id })
        assertEquals(listOf("album-a:track-album-a"), fixture.playback.albumPlays)
    }

    @Test
    fun genreBuilderOwnsFilteringSelectionResetAndPlaybackIntent() = runTest {
        val fixture = fixture()
        fixture.controller.dispatch(
            NaviampCoreCommand.MixBuilder.Genre(NaviampCoreCommand.GenreAction.ChangeQuery("amb")),
        )
        fixture.controller.execute(NaviampCoreCommand.MixBuilder.Genre(NaviampCoreCommand.GenreAction.Search))
        fixture.controller.execute(
            NaviampCoreCommand.MixBuilder.Genre(
                NaviampCoreCommand.GenreAction.Select(fixture.store.state.value.shell.genreMixBuilder.suggestedGenres.single()),
            ),
        )
        fixture.controller.execute(NaviampCoreCommand.MixBuilder.Genre(NaviampCoreCommand.GenreAction.Play))

        assertEquals(listOf("Ambient"), fixture.playback.genrePlays)
        assertEquals(listOf("Ambient"), fixture.store.state.value.shell.genreMixBuilder.selectedGenres.map { it.title })
    }

    @Test
    fun genreBuilderBrowsesAncestorsAndPlaysTheOriginalServerGenreName() = runTest {
        val projection = LibraryGenreOntologyProjection(
            nodes = listOf(
                LibraryGenreOntologyNode("music", "Music", emptyList(), emptyList(), listOf("rock")),
                LibraryGenreOntologyNode("rock", "Rock", emptyList(), listOf("music"), listOf("dream-pop")),
                LibraryGenreOntologyNode(
                    "dream-pop",
                    "Dream Pop",
                    listOf("Dream-Pop"),
                    listOf("rock"),
                    emptyList(),
                    albumCount = 12,
                    trackCount = 345,
                ),
            ),
            rootIds = listOf("music"),
            unmatchedGenreNames = listOf("Server Only"),
        )
        val genreService = GenreMixBuilderService(
            genres = { listOf(Genre("Dream-Pop"), Genre("Server Only")) },
            ontologyProjection = { projection },
        )
        val fixture = fixture(genreService)

        fixture.controller.initializeGenre()
        var ui = fixture.store.state.value.shell.genreMixBuilder
        assertEquals(listOf("Music"), ui.treeRows.map { it.title })
        assertEquals(listOf("Server Only"), ui.unmatchedGenres.map { it.title })

        fixture.controller.execute(
            NaviampCoreCommand.MixBuilder.Genre(NaviampCoreCommand.GenreAction.ToggleBranch("music")),
        )
        fixture.controller.execute(
            NaviampCoreCommand.MixBuilder.Genre(NaviampCoreCommand.GenreAction.ToggleBranch("rock")),
        )
        ui = fixture.store.state.value.shell.genreMixBuilder
        assertEquals(listOf("Music", "Rock", "Dream Pop"), ui.treeRows.map { it.title })
        assertEquals(listOf(0, 1, 2), ui.treeRows.map { it.depth })
        assertEquals(listOf("", "", "345 tracks · 12 albums"), ui.treeRows.map { it.subtitle })

        fixture.controller.execute(
            NaviampCoreCommand.MixBuilder.Genre(
                NaviampCoreCommand.GenreAction.Select(requireNotNull(ui.treeRows.last().genre)),
            ),
        )
        fixture.controller.execute(NaviampCoreCommand.MixBuilder.Genre(NaviampCoreCommand.GenreAction.Play))

        assertEquals(listOf("Dream-Pop"), fixture.playback.genrePlays)
        assertEquals(true, fixture.store.state.value.shell.genreMixBuilder.treeRows.last().selected)
    }

    @Test
    fun emptyBuilderPlaybackProducesCommonValidationStatus() = runTest {
        val fixture = fixture()

        fixture.controller.execute(NaviampCoreCommand.MixBuilder.Artist(NaviampCoreCommand.ArtistAction.Play))
        fixture.controller.execute(NaviampCoreCommand.MixBuilder.Album(NaviampCoreCommand.AlbumAction.Play))
        fixture.controller.execute(NaviampCoreCommand.MixBuilder.Genre(NaviampCoreCommand.GenreAction.Play))

        assertEquals("Select artists with matched songs first.", fixture.store.state.value.shell.artistMixBuilder.status)
        assertEquals("Select albums with matched songs first.", fixture.store.state.value.shell.albumMixBuilder.status)
        assertEquals("Select at least one genre first.", fixture.store.state.value.shell.genreMixBuilder.status)
    }

    private fun fixture(
        genreService: GenreMixBuilderService = GenreMixBuilderService {
            listOf(Genre("Ambient"), Genre("Rock"))
        },
    ): StandardMixFixture {
        val artistA = Artist(ArtistId("artist-a"), "Artist A")
        val artistB = Artist(ArtistId("artist-b"), "Artist B")
        val albumA = Album(AlbumId("album-a"), "Album A", "Artist A", null, null)
        val albumB = Album(AlbumId("album-b"), "Album B", "Artist B", null, null)
        val similar = SimilarArtistsService(
            libraryArtistsSearch = { query, _ -> listOf(artistA, artistB).filter { it.name.contains(query) } },
            client = object : SimilarArtistsClient {
                override suspend fun similarArtists(artist: Artist, limit: Int) = listOf(
                    SimilarArtistCandidate("test", "artist-b", "Artist B"),
                )
            },
        )
        val popular = ArtistPopularTracksService(
            repository = SessionArtistPopularTracksRepository(),
            libraryTracksForArtist = { artist, _ -> listOf(track("popular-${artist.id.value}")) },
            client = object : ArtistPopularTracksClient {
                override val source = "test"
                override suspend fun popularTracks(artist: Artist, limit: Int): ArtistPopularTracksResult {
                    val id = "popular-${artist.id.value}"
                    val candidate = ArtistPopularTrackCandidate("test", id, 1, id)
                    return ArtistPopularTracksResult("test", listOf(candidate), mapOf(id to track(id)))
                }
            },
            nowMillis = { 0 },
        )
        val artistService = ArtistMixBuilderService(
            sourceId = { "source" },
            artistSearch = { query, _ -> listOf(artistA, artistB).filter { it.name.contains(query, true) } },
            randomArtists = { listOf(artistA, artistB) },
            popularTracksService = popular,
            similarArtistsService = similar,
        )
        val albumService = AlbumMixBuilderService(
            albumSearch = { query, _ -> listOf(albumA, albumB).filter { it.title.contains(query, true) } },
            randomAlbums = { listOf(albumA, albumB) },
            albumsForArtist = { artist, _ -> listOf(albumB).filter { it.artistName == artist.name } },
            albumTracks = { album, _ -> listOf(track("track-${album.id.value}")) },
            similarArtistsService = similar,
        )
        val store = NaviampCoreStateStore()
        val playback = StandardMixTestPlayback()
        return StandardMixFixture(
            store,
            NaviampCoreStandardMixController(
                stateStore = store,
                providerSource = NaviampCoreMediaProviderSource { null },
                artistService = { artistService },
                albumService = { albumService },
                genreService = { genreService },
                playback = playback,
            ),
            playback,
        )
    }
}

private data class StandardMixFixture(
    val store: NaviampCoreStateStore,
    val controller: NaviampCoreStandardMixController,
    val playback: StandardMixTestPlayback,
)

private class StandardMixTestPlayback : NaviampCoreStandardMixPlaybackPort {
    val artistPlays = mutableListOf<String>()
    val albumPlays = mutableListOf<String>()
    var genrePlays = emptyList<String>()

    override suspend fun playArtistMix(artists: List<Artist>, seedTracks: List<Track>) {
        artistPlays += "${artists.joinToString { it.id.value }}:${seedTracks.joinToString { it.id.value }}"
    }

    override suspend fun playAlbumMix(albums: List<Album>, seedTracks: List<Track>) {
        albumPlays += "${albums.joinToString { it.id.value }}:${seedTracks.joinToString { it.id.value }}"
    }

    override suspend fun playGenreMix(genres: List<Genre>) {
        genrePlays = genres.map(Genre::name)
    }
}

private fun track(id: String) = Track(
    id = TrackId(id),
    title = id,
    artistName = "Artist",
    albumTitle = "Album",
    durationSeconds = 180,
    coverArtId = null,
    audioInfo = null,
    replayGain = null,
)
