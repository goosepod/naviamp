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

    private class FakeRadioProvider : MediaProvider {
        override val id: ProviderId = ProviderId("provider-one")
        override val displayName: String = "Provider One"
        override val capabilities: ProviderCapabilities = ProviderCapabilities(
            supportsStreamingTranscode = true,
            supportsDownloadTranscode = true,
            supportsArtistRadio = true,
            supportsAlbumRadio = true,
            supportsTrackRadio = true,
        )
        var albumCalls: Int = 0
        var artistCalls: Int = 0

        override suspend fun validateConnection(): ConnectionValidation =
            error("unused")

        override suspend fun recentlyAddedAlbums(limit: Int): List<Album> =
            error("unused")

        override suspend fun album(albumId: AlbumId): AlbumDetails {
            albumCalls += 1
            return AlbumDetails(album(albumId.value), listOf(track("album-track")))
        }

        override suspend fun artist(artistId: ArtistId): ArtistDetails {
            artistCalls += 1
            return ArtistDetails(artist(artistId.value), listOf(album("album-one")))
        }

        override suspend fun artists(limit: Int): List<Artist> =
            error("unused")

        override suspend fun tracks(limit: Int): List<Track> =
            error("unused")

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

private fun track(id: String): Track =
    Track(
        id = TrackId(id),
        title = id,
        artistId = ArtistId("artist-one"),
        artistName = "Artist",
        albumTitle = "Album",
        durationSeconds = 180,
        coverArtId = null,
        audioInfo = null,
        replayGain = null,
    )
