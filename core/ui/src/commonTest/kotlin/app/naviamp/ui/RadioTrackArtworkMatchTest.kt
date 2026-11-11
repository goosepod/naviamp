package app.naviamp.ui

import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RadioTrackArtworkMatchTest {
    @Test
    fun stationsWithoutServerArtworkUseTheirKnownLogoAcrossPresentations() {
        val station = InternetRadioStation(
            id = "groovesalad",
            name = "Groove Salad",
            streamUrl = "https://ice.somafm.com/groovesalad",
        )
        val logo = "https://api.somafm.com/logos/512/groovesalad512.png"

        assertEquals(logo, station.toInternetRadioStationUi().item.coverArtUrl)
        assertEquals(logo, station.defaultRadioArtworkUrl())
        assertEquals(logo, station.toNowPlayingStationUi().coverArtUrl)
        assertEquals(logo, radioArtworkUrl(station))
    }

    @Test
    fun realServerArtworkStillOverridesTheKnownStationLogo() {
        val station = InternetRadioStation(
            id = "groovesalad",
            name = "Groove Salad",
            streamUrl = "https://ice.somafm.com/groovesalad",
            coverArtUrl = "https://music.example.test/station-artwork",
        )

        assertEquals(station.coverArtUrl, station.toInternetRadioStationUi().item.coverArtUrl)
        assertEquals(station.coverArtUrl, station.defaultRadioArtworkUrl())
        assertEquals(station.coverArtUrl, station.toNowPlayingStationUi().coverArtUrl)
        assertEquals(station.coverArtUrl, radioArtworkUrl(station))
    }

    private fun track(artist: String, title: String, cover: String? = "cover-$title") = Track(
        id = TrackId(title),
        title = title,
        artistName = artist,
        albumTitle = null,
        durationSeconds = null,
        coverArtId = cover,
        audioInfo = null,
        replayGain = null,
    )

    @Test
    fun picksTheAnnouncedSongEvenWhenItIsNotTheFirstResult() {
        val results = listOf(
            track("Chappell Roan", "Good Luck, Babe!"),
            track("Chappell Roan", "HOT TO GO!"),
            track("Chappell Roan", "casual", cover = null),
            track("Chappell Roan", "Casual"),
        )

        assertEquals("cover-Casual", radioTrackArtworkMatch(results, "Chappell Roan - Casual")?.coverArtId)
        assertEquals("cover-Casual", radioTrackArtworkMatch(results, "Casual")?.coverArtId)
    }

    @Test
    fun keepsTheStationArtworkWhenNoResultIsTheAnnouncedSong() {
        val results = listOf(track("Chappell Roan", "Good Luck, Babe!"))

        assertNull(radioTrackArtworkMatch(results, "Chappell Roan - Casual"))
        assertNull(radioTrackArtworkMatch(results, null))
    }
}
