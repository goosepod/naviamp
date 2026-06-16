package app.naviamp.domain.radio

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.ArtistId
import app.naviamp.domain.ProviderId
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.cache.ProviderResponseCacheRepository
import app.naviamp.domain.cache.ProviderResponseService
import app.naviamp.domain.provider.ConnectionValidation
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.MediaSearchResults
import app.naviamp.domain.provider.ProviderCapabilities
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RadioServiceTest {
    @Test
    fun albumSeedUsesProviderResponseServiceForAlbumTracks() = runTest {
        val cache = RecordingProviderResponseCacheRepository()
        val provider = FakeRadioProvider()
        val service = RadioService(provider, providerResponseService = ProviderResponseService(cache))

        val seed = service.albumSeed(album("album-one"))

        assertEquals(track("album-track"), seed)
        assertEquals(listOf("provider-one:album:album-one"), cache.keys)
        assertEquals(1, provider.albumCalls)
    }

    @Test
    fun artistSeedUsesProviderResponseServiceForArtistAndAlbumDetails() = runTest {
        val cache = RecordingProviderResponseCacheRepository()
        val provider = FakeRadioProvider()
        val service = RadioService(provider, providerResponseService = ProviderResponseService(cache))

        val seed = service.artistSeed(artist("artist-one"))

        assertEquals(track("album-track"), seed)
        assertEquals(
            listOf(
                "provider-one:artist:artist-one",
                "provider-one:album:album-one",
            ),
            cache.keys,
        )
        assertEquals(1, provider.artistCalls)
        assertEquals(1, provider.albumCalls)
    }

    @Test
    fun trackRadioPrefersSonicTracksWhenEnabledAndSupported() = runTest {
        val provider = FakeRadioProvider(
            supportsSonicSimilarity = true,
            sonicTracks = listOf(track("seed"), track("sonic-one"), track("sonic-two")),
            radioTracks = listOf(track("provider-radio")),
        )
        val service = RadioService(provider)

        val tracks = service.trackRadio(track("seed"), preferSonicSimilarity = true)

        assertEquals(listOf("sonic-one", "sonic-two"), tracks.map { it.id.value })
    }

    @Test
    fun trackRadioFallsBackToProviderRadioWhenSonicIsDisabledOrEmpty() = runTest {
        val provider = FakeRadioProvider(
            supportsSonicSimilarity = true,
            sonicTracks = emptyList(),
            radioTracks = listOf(track("provider-radio")),
        )
        val service = RadioService(provider)

        val tracks = service.trackRadio(track("seed"), preferSonicSimilarity = true)

        assertEquals(listOf("provider-radio"), tracks.map { it.id.value })
    }

    @Test
    fun tunedRadioTracksCanPreferFavorites() {
        val tracks = listOf(
            track("plain", playCount = 20),
            track("favorite", favoritedAtIso8601 = "2026-06-16T12:00:00Z"),
            track("rated", userRating = 5),
        )

        val tuned = tunedRadioTracks(
            seedTrack = null,
            tracks = tracks,
            tuning = RadioTuningSettings(familiarity = RadioFamiliarity.Favorites),
        )

        assertEquals(listOf("favorite", "rated", "plain"), tuned.map { it.id.value })
    }

    @Test
    fun tunedRadioTracksCanPreferDeepCuts() {
        val tracks = listOf(
            track("favorite", favoritedAtIso8601 = "2026-06-16T12:00:00Z", playCount = 50),
            track("unplayed", playCount = 0),
            track("played", playCount = 12),
        )

        val tuned = tunedRadioTracks(
            seedTrack = null,
            tracks = tracks,
            tuning = RadioTuningSettings(familiarity = RadioFamiliarity.DeepCuts),
        )

        assertEquals(listOf("unplayed", "played", "favorite"), tuned.map { it.id.value })
    }

    @Test
    fun tunedRadioTracksCanStayInSeedDecadeWhenEnoughMetadataExists() {
        val seed = track("seed", albumReleaseYear = 1994)
        val tracks = listOf(
            track("new", albumReleaseYear = 2001),
            track("same-one", albumReleaseYear = 1990),
            track("same-two", albumReleaseYear = 1999),
        )

        val tuned = tunedRadioTracks(
            seedTrack = seed,
            tracks = tracks,
            tuning = RadioTuningSettings(sameDecadeOnly = true),
        )

        assertEquals(listOf("same-one", "same-two"), tuned.map { it.id.value })
    }

    @Test
    fun tunedRadioTracksCanBroadenArtistSpread() {
        val tracks = listOf(
            track("artist-one-a", artistId = ArtistId("artist-one")),
            track("artist-one-b", artistId = ArtistId("artist-one")),
            track("artist-two-a", artistId = ArtistId("artist-two")),
            track("artist-two-b", artistId = ArtistId("artist-two")),
        )

        val tuned = tunedRadioTracks(
            seedTrack = null,
            tracks = tracks,
            tuning = RadioTuningSettings(artistSpread = RadioArtistSpread.Broad),
        )

        assertEquals(listOf("artist-one-a", "artist-two-a", "artist-one-b", "artist-two-b"), tuned.map { it.id.value })
    }

    @Test
    fun tunedRadioTracksCanStayWithSeedArtist() {
        val seed = track("seed", artistId = ArtistId("seed-artist"))
        val tracks = listOf(
            track("seed-artist-a", artistId = ArtistId("seed-artist")),
            track("other-artist", artistId = ArtistId("other-artist")),
            track("seed-artist-b", artistId = ArtistId("seed-artist")),
        )

        val tuned = tunedRadioTracks(
            seedTrack = seed,
            tracks = tracks,
            tuning = RadioTuningSettings(artistRunMode = RadioArtistRunMode.SingleArtist),
        )

        assertEquals(listOf("seed-artist-a", "seed-artist-b"), tuned.map { it.id.value })
    }

    @Test
    fun tunedRadioTracksCanUseArtistBlocks() {
        val seed = track("seed", artistId = ArtistId("seed-artist"))
        val tracks = listOf(
            track("seed-artist-a", artistId = ArtistId("seed-artist")),
            track("seed-artist-b", artistId = ArtistId("seed-artist")),
            track("seed-artist-c", artistId = ArtistId("seed-artist")),
            track("other-artist-a", artistId = ArtistId("other-artist-a")),
            track("other-artist-b", artistId = ArtistId("other-artist-b")),
            track("other-artist-c", artistId = ArtistId("other-artist-c")),
        )

        val tuned = tunedRadioTracks(
            seedTrack = seed,
            tracks = tracks,
            tuning = RadioTuningSettings(
                artistRunMode = RadioArtistRunMode.ArtistBlocks,
                sameArtistRunLength = 2,
                otherArtistRunLength = 1,
            ),
        )

        assertEquals(
            listOf(
                "seed-artist-a",
                "seed-artist-b",
                "other-artist-a",
                "seed-artist-c",
                "other-artist-b",
                "other-artist-c",
            ),
            tuned.map { it.id.value },
        )
    }

    @Test
    fun trackRadioSingleArtistBackfillsFromSeedArtistAlbums() = runTest {
        val seed = track("seed", artistId = ArtistId("seed-artist"))
        val provider = FakeRadioProvider(
            radioTracks = listOf(track("only-radio-match", artistId = ArtistId("seed-artist"))),
            artistAlbums = listOf(album("seed-album")),
            albumTracksById = mapOf(
                AlbumId("seed-album") to listOf(
                    seed,
                    track("seed-artist-a", artistId = ArtistId("seed-artist")),
                    track("seed-artist-b", artistId = ArtistId("seed-artist")),
                    track("other-artist", artistId = ArtistId("other-artist")),
                ),
            ),
        )
        val service = RadioService(
            provider = provider,
            count = 10,
            tuning = RadioTuningSettings(artistRunMode = RadioArtistRunMode.SingleArtist),
        )

        val tracks = service.trackRadio(seed, preferSonicSimilarity = false)

        assertEquals(
            listOf("seed-artist-a", "seed-artist-b", "only-radio-match"),
            tracks.map { it.id.value },
        )
    }

    @Test
    fun trackRadioArtistBlocksBackfillSameArtistRun() = runTest {
        val seed = track("seed", artistId = ArtistId("seed-artist"))
        val provider = FakeRadioProvider(
            radioTracks = listOf(
                track("other-a", artistId = ArtistId("other-a")),
                track("other-b", artistId = ArtistId("other-b")),
                track("other-c", artistId = ArtistId("other-c")),
            ),
            artistAlbums = listOf(album("seed-album")),
            albumTracksById = mapOf(
                AlbumId("seed-album") to listOf(
                    seed,
                    track("seed-artist-a", artistId = ArtistId("seed-artist")),
                    track("seed-artist-b", artistId = ArtistId("seed-artist")),
                ),
            ),
        )
        val service = RadioService(
            provider = provider,
            count = 5,
            tuning = RadioTuningSettings(
                artistRunMode = RadioArtistRunMode.ArtistBlocks,
                sameArtistRunLength = 2,
                otherArtistRunLength = 3,
            ),
        )

        val tracks = service.trackRadio(seed, preferSonicSimilarity = false)

        assertEquals(
            listOf("seed-artist-a", "seed-artist-b", "other-a", "other-b", "other-c"),
            tracks.map { it.id.value },
        )
    }

    private class RecordingProviderResponseCacheRepository : ProviderResponseCacheRepository {
        private val values = mutableMapOf<String, String>()
        val keys = mutableListOf<String>()

        override suspend fun <T> cachedProviderResponse(
            provider: MediaProvider,
            resourceType: String,
            resourceId: String,
            decode: (String) -> T,
            encode: (T) -> String,
            fetch: suspend () -> T,
        ): T {
            val key = "${provider.cacheNamespace}:$resourceType:$resourceId"
            keys += key
            values[key]?.let { return decode(it) }
            val value = fetch()
            values[key] = encode(value)
            return value
        }

        override fun invalidateProviderResponses(
            provider: MediaProvider,
            resourceType: String,
        ) {
            values.keys
                .filter { it.startsWith("${provider.cacheNamespace}:$resourceType:") }
                .forEach(values::remove)
        }

        override fun invalidateProviderResponse(
            provider: MediaProvider,
            resourceType: String,
            resourceId: String,
        ) {
            values.remove("${provider.cacheNamespace}:$resourceType:$resourceId")
        }
    }

    private class FakeRadioProvider(
        private val supportsSonicSimilarity: Boolean = false,
        private val sonicTracks: List<Track> = emptyList(),
        private val radioTracks: List<Track> = emptyList(),
        private val artistAlbums: List<Album> = listOf(album("album-one")),
        private val albumTracksById: Map<AlbumId, List<Track>> = mapOf(
            AlbumId("album-one") to listOf(track("album-track")),
        ),
    ) : MediaProvider {
        override val id: ProviderId = ProviderId("provider-one")
        override val displayName: String = "Provider One"
        override val capabilities: ProviderCapabilities = ProviderCapabilities(
            supportsStreamingTranscode = true,
            supportsDownloadTranscode = true,
            supportsArtistRadio = true,
            supportsAlbumRadio = true,
            supportsTrackRadio = true,
            supportsSonicSimilarity = supportsSonicSimilarity,
        )
        var albumCalls: Int = 0
        var artistCalls: Int = 0

        override suspend fun validateConnection(): ConnectionValidation =
            error("unused")

        override suspend fun recentlyAddedAlbums(limit: Int): List<Album> =
            error("unused")

        override suspend fun album(albumId: AlbumId): AlbumDetails {
            albumCalls += 1
            return AlbumDetails(album(albumId.value), albumTracksById[albumId].orEmpty())
        }

        override suspend fun artist(artistId: ArtistId): ArtistDetails {
            artistCalls += 1
            return ArtistDetails(artist(artistId.value), artistAlbums)
        }

        override suspend fun artists(limit: Int): List<Artist> =
            error("unused")

        override suspend fun tracks(limit: Int): List<Track> =
            error("unused")

        override suspend fun trackRadio(trackId: TrackId, count: Int): List<Track> =
            radioTracks

        override suspend fun sonicSimilarTracks(trackId: TrackId, count: Int): List<Track> =
            sonicTracks

        override suspend fun search(query: String, limit: Int): MediaSearchResults =
            error("unused")

        override suspend fun streamUrl(request: StreamRequest): String =
            error("unused")

        override fun coverArtUrl(coverArtId: String): String =
            error("unused")
    }
}

private fun album(id: String): Album =
    Album(
        id = AlbumId(id),
        title = id,
        artistName = "Artist",
        coverArtId = null,
        recentlyAddedAtIso8601 = null,
    )

private fun artist(id: String): Artist =
    Artist(
        id = ArtistId(id),
        name = "Artist",
    )

private fun track(
    id: String,
    artistId: ArtistId? = ArtistId("artist-one"),
    albumReleaseYear: Int? = null,
    favoritedAtIso8601: String? = null,
    userRating: Int? = null,
    playCount: Int? = null,
): Track =
    Track(
        id = TrackId(id),
        title = id,
        artistId = artistId,
        artistName = "Artist",
        albumTitle = "Album",
        albumReleaseYear = albumReleaseYear,
        durationSeconds = 180,
        coverArtId = null,
        audioInfo = null,
        replayGain = null,
        favoritedAtIso8601 = favoritedAtIso8601,
        userRating = userRating,
        playCount = playCount,
    )
