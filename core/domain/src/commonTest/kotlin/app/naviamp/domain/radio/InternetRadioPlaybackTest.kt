package app.naviamp.domain.radio

import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.internetRadioTrackId
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.network.SharedHttpClient
import app.naviamp.domain.network.SharedHttpResponse
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.settings.SavedInternetRadioStation
import kotlin.test.Test
import kotlin.test.assertEquals

class InternetRadioPlaybackTest {
    @Test
    fun internetRadioTrackUsesStationFields() {
        val station = station("kexp", name = "KEXP", streamUrl = "https://stream.example", homePageUrl = "https://kexp.org")

        val track = internetRadioTrack(station)

        assertEquals(internetRadioTrackId("kexp"), track.id)
        assertEquals("KEXP", track.title)
        assertEquals("Internet Radio", track.artistName)
        assertEquals("https://kexp.org", track.albumTitle)
        assertEquals(null, track.durationSeconds)
    }

    @Test
    fun internetRadioTrackWithMetadataUsesStreamTitleWhenPresent() {
        val station = station("kexp", name = "KEXP")
        val fallback = internetRadioTrack(station)

        val track = internetRadioTrackWithMetadata(
            fallback,
            station,
            PlaybackStreamMetadata(title = "Artist - Song"),
        )

        assertEquals("Artist - Song", track.title)
        assertEquals("KEXP", track.artistName)
        assertEquals("Internet Radio", track.albumTitle)
    }

    @Test
    fun recentInternetRadioStationsMoveSelectedStationToFront() {
        val selected = station("two")
        val recent = recentInternetRadioStationsWith(
            listOf(station("one"), selected.copy(name = "Old Name"), station("three")),
            selected,
        )

        assertEquals(listOf("two", "one", "three"), recent.map { it.id })
        assertEquals("Station two", recent.first().name)
    }

    @Test
    fun recentSavedInternetRadioStationsAreLimited() {
        val stations = (1..14).map { SavedInternetRadioStation.fromStation(station("$it")) }
        val recent = recentSavedInternetRadioStationsWith(stations, station("15"))

        assertEquals(MaxRecentInternetRadioStations, recent.size)
        assertEquals("15", recent.first().id)
        assertEquals("11", recent.last().id)
    }

    @Test
    fun internetRadioStartPlanBuildsRecentStateAndPlaybackEffects() {
        val selected = station("two")
        val plan = planInternetRadioStart(
            station = selected,
            recentStations = listOf(station("one"), selected.copy(name = "Old Name")),
            recentSavedStations = listOf(SavedInternetRadioStation.fromStation(station("one"))),
        )

        assertEquals(listOf("two", "one"), plan.recentStations.map { it.id })
        assertEquals(listOf("two", "one"), plan.recentSavedStations.map { it.id })
        assertEquals(null, plan.nowPlayingTrack)
        assertEquals(selected, plan.station)
        assertEquals(PlaybackStreamMetadata(), plan.streamMetadata)
        assertEquals(PlaybackProgress.Unknown, plan.playbackProgress)
        assertEquals(PlaybackQueue(), plan.playbackQueue)
        assertEquals(true, plan.openNowPlaying)
        assertEquals(false, plan.canFavorite)
        assertEquals(false, plan.isFavorite)
        assertEquals(true, plan.clearShuffleSnapshot)
        assertEquals(true, plan.clearRadioContinuation)
        assertEquals(true, plan.savePlaybackSession)
        assertEquals("Loading Station two...", plan.status)
        assertEquals("Station two", plan.notificationTitle)
        assertEquals("Internet radio", plan.notificationSubtitle)
        assertEquals(null, plan.notificationCoverArtUrl)
        assertEquals("two", plan.engineMediaId)
        assertEquals(true, plan.replayGainOff)
    }

    @Test
    fun radioPlaylistParserFindsPlsFileEntry() {
        val body = """
            [playlist]
            NumberOfEntries=1
            File1=https://stream.example/live.mp3
        """.trimIndent()

        assertEquals("https://stream.example/live.mp3", parseRadioPlaylist(body))
    }

    @Test
    fun radioStreamResolverReturnsDirectAudioResponseUrl() = kotlinx.coroutines.test.runTest {
        val resolver = InternetRadioStreamResolver(
            FakeHttpClient(
                SharedHttpResponse(
                    url = "https://radio.example/listen",
                    finalUrl = "https://cdn.radio.example/live.mp3",
                    statusCode = 200,
                    contentType = "audio/mpeg",
                    body = ByteArray(0),
                ),
            ),
        )

        assertEquals("https://cdn.radio.example/live.mp3", resolver.resolve("https://radio.example/listen"))
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

    private class FakeHttpClient(private val response: SharedHttpResponse) : SharedHttpClient {
        override suspend fun get(url: String, headers: Map<String, String>): String? =
            response.bodyText()

        override suspend fun getBytes(url: String, headers: Map<String, String>): ByteArray? =
            response.body

        override suspend fun getResponse(url: String, headers: Map<String, String>): SharedHttpResponse =
            response

        override suspend fun download(
            url: String,
            headers: Map<String, String>,
            writeChunk: suspend (bytes: ByteArray, count: Int) -> Unit,
        ): Boolean {
            writeChunk(response.body, response.body.size)
            return true
        }
    }
}
