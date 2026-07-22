package app.naviamp.presentation

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.ArtistId
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.ProviderId
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.Track
import app.naviamp.domain.provider.ConnectionValidation
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.MediaSearchResults
import app.naviamp.domain.provider.ProviderCapabilities
import app.naviamp.domain.radio.recentInternetRadioStationsWith
import app.naviamp.ui.NaviampInternetRadioStationEditUi
import app.naviamp.ui.StationRowAction
import app.naviamp.ui.StationRowActionRequest
import app.naviamp.ui.SharedMediaItemUi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NaviampCoreInternetRadioControllerTest {
    @Test
    fun refreshMapsStationsAndRejectsStaleResults() = runTest {
        val firstGate = CompletableDeferred<Unit>()
        val provider = RadioTestProvider(firstGate)
        val fixture = fixture(provider)

        val first = launch { fixture.controller.execute(NaviampCoreCommand.Radio.Refresh) }
        runCurrent()
        provider.stations = listOf(station("new", "New"))
        fixture.controller.execute(NaviampCoreCommand.Radio.Refresh)
        firstGate.complete(Unit)
        first.join()

        val radio = fixture.store.state.value.shell.radio
        assertFalse(radio.refreshing)
        assertEquals(listOf("new"), radio.stations.map { it.item.id })
        assertEquals(listOf("new"), fixture.controller.stations().map { it.id })
    }

    @Test
    fun stationCatalogPreservesProviderOrderForSharedNowPlaying() = runTest {
        val provider = RadioTestProvider().apply {
            stations = listOf(station("three", "Three"), station("one", "One"), station("two", "Two"))
        }
        val fixture = fixture(provider)

        fixture.controller.execute(NaviampCoreCommand.Radio.Refresh)

        assertEquals(listOf("three", "one", "two"), fixture.controller.stations().map { it.id })
    }

    @Test
    fun selectionPlaysRecordsAndPublishesRecentStationsThroughCore() = runTest {
        val provider = RadioTestProvider()
        val fixture = fixture(provider)
        fixture.controller.execute(NaviampCoreCommand.Radio.Refresh)

        fixture.controller.execute(
            NaviampCoreCommand.Radio.StationAction(
                StationRowActionRequest(SharedMediaItemUi("one", "One", ""), StationRowAction.Select),
            ),
        )

        assertEquals(listOf("one"), fixture.played.map(InternetRadioStation::id))
        assertEquals(listOf("one"), fixture.store.state.value.shell.home.content.recentRadioStreams.map { it.id })
        assertEquals(null, fixture.store.state.value.shell.radio.status)
    }

    @Test
    fun createUpdateAndDeleteRefreshTheAuthoritativeCoreList() = runTest {
        val provider = RadioTestProvider()
        val fixture = fixture(provider)
        fixture.controller.execute(NaviampCoreCommand.Radio.Refresh)

        fixture.controller.execute(
            NaviampCoreCommand.Radio.SaveStation(
                NaviampInternetRadioStationEditUi(name = "Created", streamUrl = "https://created.example"),
            ),
        )
        fixture.controller.execute(
            NaviampCoreCommand.Radio.SaveStation(
                NaviampInternetRadioStationEditUi(
                    id = "one",
                    name = "Updated",
                    streamUrl = "https://updated.example",
                ),
            ),
        )
        fixture.controller.execute(
            NaviampCoreCommand.Radio.StationAction(
                StationRowActionRequest(SharedMediaItemUi("one", "Updated", ""), StationRowAction.Delete),
            ),
        )

        assertEquals(listOf("Created"), provider.created)
        assertEquals(listOf("one:Updated"), provider.updated)
        assertEquals(listOf("one"), provider.deleted)
        assertEquals(listOf("two", "created"), fixture.store.state.value.shell.radio.stations.map { it.item.id })
        assertEquals("Deleted Updated.", fixture.store.state.value.shell.radio.status)
    }

    @Test
    fun disconnectedAndInvalidRequestsPublishSharedFailures() = runTest {
        val fixture = fixture(null)
        fixture.controller.execute(NaviampCoreCommand.Radio.Refresh)
        fixture.controller.execute(
            NaviampCoreCommand.Radio.SaveStation(NaviampInternetRadioStationEditUi(name = "", streamUrl = "")),
        )

        assertTrue(fixture.played.isEmpty())
        assertEquals("Connect to Navidrome to manage internet radio.", fixture.store.state.value.shell.radio.status)
    }

    private fun fixture(provider: RadioTestProvider?): RadioFixture {
        val store = NaviampCoreStateStore()
        val played = mutableListOf<InternetRadioStation>()
        val recent = mutableListOf<InternetRadioStation>()
        val controller = NaviampCoreInternetRadioController(
            stateStore = store,
            providerSource = NaviampCoreMediaProviderSource { provider },
            playback = NaviampCoreInternetRadioPlaybackPort { played += it },
            recents = object : NaviampCoreInternetRadioRecentsPort {
                override fun current() = recent.toList()
                override suspend fun record(station: InternetRadioStation): List<InternetRadioStation> =
                    recentInternetRadioStationsWith(recent, station).also {
                        recent.clear()
                        recent.addAll(it)
                    }
            },
        )
        return RadioFixture(store, controller, played)
    }
}

private data class RadioFixture(
    val store: NaviampCoreStateStore,
    val controller: NaviampCoreInternetRadioController,
    val played: List<InternetRadioStation>,
)

private class RadioTestProvider(
    private val firstRefreshGate: CompletableDeferred<Unit>? = null,
) : MediaProvider {
    override val id = ProviderId("radio")
    override val displayName = "Radio"
    override val capabilities = ProviderCapabilities(false, false, false, false, false)
    var stations = listOf(station("one", "One"), station("two", "Two"))
    private var refreshCount = 0
    val created = mutableListOf<String>()
    val updated = mutableListOf<String>()
    val deleted = mutableListOf<String>()

    override suspend fun validateConnection() = ConnectionValidation(null, null)
    override suspend fun recentlyAddedAlbums(limit: Int) = emptyList<Album>()
    override suspend fun album(albumId: AlbumId): AlbumDetails = error("Not used")
    override suspend fun artist(artistId: ArtistId): ArtistDetails = error("Not used")
    override suspend fun artists(limit: Int) = emptyList<Artist>()
    override suspend fun tracks(limit: Int) = emptyList<Track>()
    override suspend fun search(query: String, limit: Int) = MediaSearchResults()
    override suspend fun internetRadioStations(): List<InternetRadioStation> {
        refreshCount += 1
        val snapshot = stations
        if (refreshCount == 1) firstRefreshGate?.await()
        return snapshot
    }

    override suspend fun createInternetRadioStation(
        name: String,
        streamUrl: String,
        homePageUrl: String?,
    ): InternetRadioStation {
        created += name
        return station("created", name).also { stations = stations + it }
    }

    override suspend fun updateInternetRadioStation(station: InternetRadioStation) {
        updated += "${station.id}:${station.name}"
        stations = stations.map { current -> if (current.id == station.id) station else current }
    }

    override suspend fun deleteInternetRadioStation(stationId: String) {
        deleted += stationId
        stations = stations.filterNot { it.id == stationId }
    }

    override suspend fun streamUrl(request: StreamRequest) = "https://stream.example"
    override fun coverArtUrl(coverArtId: String) = "https://art.example/$coverArtId"
}

private fun station(id: String, name: String) =
    InternetRadioStation(id, name, "https://$id.example")
