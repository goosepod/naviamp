package app.naviamp.domain.radio

import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.internetRadioTrackId
import app.naviamp.domain.playback.PlaybackStreamMetadata
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
}
