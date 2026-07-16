package app.naviamp.domain.radio

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.ArtistId
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.ProviderId
import app.naviamp.domain.StreamRequest
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

class InternetRadioStationManagerTest {
    @Test
    fun saveStationCreatesUnsavedStationsAndReloadsAfterInvalidation() = runTest {
        val cache = FakeProviderResponseCacheRepository()
        val provider = FakeRadioProvider(
            stations = listOf(station("before")),
            stationsAfterCreate = listOf(station("created")),
        )
        val manager = InternetRadioStationManager(ProviderResponseService(cache))
        assertEquals(listOf("before"), manager.refreshStations(provider).map { it.id })

        val stations = manager.saveStation(
            provider,
            station(id = "https://stream.example/live", streamUrl = "https://stream.example/live"),
        )

        assertEquals(listOf("create:Station https://stream.example/live:https://stream.example/live:null"), provider.calls)
        assertEquals(listOf("provider-one:internetRadioStations:all"), cache.invalidatedKeys)
        assertEquals(listOf("created"), stations.map { it.id })
        assertEquals(2, provider.internetRadioStationsCalls)
    }

    @Test
    fun saveStationUpdatesExistingStationsAndReloadsAfterInvalidation() = runTest {
        val cache = FakeProviderResponseCacheRepository()
        val provider = FakeRadioProvider(
            stations = listOf(station("before")),
            stationsAfterUpdate = listOf(station("updated")),
        )
        val manager = InternetRadioStationManager(ProviderResponseService(cache))
        assertEquals(listOf("before"), manager.refreshStations(provider).map { it.id })

        val stations = manager.saveStation(provider, station("existing", streamUrl = "https://stream.example/live"))

        assertEquals(listOf("update:existing"), provider.calls)
        assertEquals(listOf("provider-one:internetRadioStations:all"), cache.invalidatedKeys)
        assertEquals(listOf("updated"), stations.map { it.id })
        assertEquals(2, provider.internetRadioStationsCalls)
    }

    @Test
    fun deleteStationDeletesAndReloadsAfterInvalidation() = runTest {
        val cache = FakeProviderResponseCacheRepository()
        val provider = FakeRadioProvider(
            stations = listOf(station("before")),
            stationsAfterDelete = listOf(station("after-delete")),
        )
        val manager = InternetRadioStationManager(ProviderResponseService(cache))
        assertEquals(listOf("before"), manager.refreshStations(provider).map { it.id })

        val stations = manager.deleteStation(provider, station("old"))

        assertEquals(listOf("delete:old"), provider.calls)
        assertEquals(listOf("provider-one:internetRadioStations:all"), cache.invalidatedKeys)
        assertEquals(listOf("after-delete"), stations.map { it.id })
        assertEquals(2, provider.internetRadioStationsCalls)
    }

    @Test
    fun statusHelpersUseStationNames() {
        val station = station("kexp", name = "KEXP")

        assertEquals("Loading internet radio...", internetRadioRefreshLoadingStatus())
        assertEquals("Could not load internet radio stations.", internetRadioRefreshErrorStatus())
        assertEquals("Saving KEXP...", internetRadioSaveLoadingStatus(station))
        assertEquals("Could not save station.", internetRadioSaveErrorStatus())
        assertEquals("Deleting KEXP...", internetRadioDeleteLoadingStatus(station))
        assertEquals("Could not delete station.", internetRadioDeleteErrorStatus())
    }

    private class FakeProviderResponseCacheRepository : ProviderResponseCacheRepository {
        val invalidatedKeys = mutableListOf<String>()
        private val values = mutableMapOf<String, String>()

        override suspend fun <T> cachedProviderResponse(
            provider: MediaProvider,
            resourceType: String,
            resourceId: String,
            decode: (String) -> T,
            encode: (T) -> String,
            fetch: suspend () -> T,
        ): T {
            val key = "${provider.cacheNamespace}:$resourceType:$resourceId"
            val cached = values[key]
            if (cached != null) return decode(cached)
            return fetch().also { value -> values[key] = encode(value) }
        }

        override fun invalidateProviderResponses(
            provider: MediaProvider,
            resourceType: String,
        ) {
            val prefix = "${provider.cacheNamespace}:$resourceType:"
            values.keys.filter { it.startsWith(prefix) }.forEach(values::remove)
        }

        override fun invalidateProviderResponse(
            provider: MediaProvider,
            resourceType: String,
            resourceId: String,
        ) {
            invalidatedKeys += "${provider.cacheNamespace}:$resourceType:$resourceId"
            values.remove("${provider.cacheNamespace}:$resourceType:$resourceId")
        }
    }

    private class FakeRadioProvider(
        private val stations: List<InternetRadioStation>,
        private val stationsAfterCreate: List<InternetRadioStation> = stations,
        private val stationsAfterUpdate: List<InternetRadioStation> = stations,
        private val stationsAfterDelete: List<InternetRadioStation> = stations,
    ) : MediaProvider {
        override val id: ProviderId = ProviderId("provider-one")
        override val displayName: String = "Provider One"
        override val capabilities: ProviderCapabilities = ProviderCapabilities(
            supportsStreamingTranscode = true,
            supportsDownloadTranscode = true,
            supportsArtistRadio = true,
            supportsAlbumRadio = true,
            supportsTrackRadio = true,
        )
        val calls = mutableListOf<String>()
        var internetRadioStationsCalls = 0
        private var nextStations = stations

        override suspend fun validateConnection(): ConnectionValidation =
            error("unused")

        override suspend fun recentlyAddedAlbums(limit: Int): List<Album> =
            error("unused")

        override suspend fun album(albumId: AlbumId): AlbumDetails =
            error("unused")

        override suspend fun artist(artistId: ArtistId): ArtistDetails =
            error("unused")

        override suspend fun artists(limit: Int): List<Artist> =
            error("unused")

        override suspend fun tracks(limit: Int): List<app.naviamp.domain.Track> =
            error("unused")

        override suspend fun search(
            query: String,
            limit: Int,
        ): MediaSearchResults =
            error("unused")

        override suspend fun internetRadioStations(): List<InternetRadioStation> {
            internetRadioStationsCalls += 1
            return nextStations
        }

        override suspend fun createInternetRadioStation(
            name: String,
            streamUrl: String,
            homePageUrl: String?,
        ): InternetRadioStation {
            calls += "create:$name:$streamUrl:$homePageUrl"
            nextStations = stationsAfterCreate
            return stationsAfterCreate.first()
        }

        override suspend fun updateInternetRadioStation(station: InternetRadioStation) {
            calls += "update:${station.id}"
            nextStations = stationsAfterUpdate
        }

        override suspend fun deleteInternetRadioStation(stationId: String) {
            calls += "delete:$stationId"
            nextStations = stationsAfterDelete
        }

        override suspend fun streamUrl(request: StreamRequest): String =
            error("unused")

        override fun coverArtUrl(coverArtId: String): String =
            error("unused")
    }
}

private fun station(
    id: String,
    name: String = "Station $id",
    streamUrl: String = "https://example.test/$id",
    homePageUrl: String? = null,
): InternetRadioStation =
    InternetRadioStation(
        id = id,
        name = name,
        streamUrl = streamUrl,
        homePageUrl = homePageUrl,
    )
