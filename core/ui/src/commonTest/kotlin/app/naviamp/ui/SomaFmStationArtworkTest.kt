package app.naviamp.ui

import app.naviamp.domain.InternetRadioStation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SomaFmStationArtworkTest {
    @Test fun reportedChannelsUseOfficialJpegLogosInEverySharedPresentation() {
        listOf("synphaera" to "Synphaera Radio", "thetrip" to "The Trip", "gsclassic" to "Groove Salad Classic")
            .forEach { (id, title) ->
                val station = InternetRadioStation(id, title, "https://ice.somafm.com/$id")
                val url = "https://api.somafm.com/logos/512/${id}512.jpg"
                assertEquals(url, station.toInternetRadioStationUi().item.coverArtUrl)
                assertEquals(url, station.defaultRadioArtworkUrl())
                assertEquals(url, station.toNowPlayingStationUi().coverArtUrl)
                assertEquals(url, radioArtworkUrl(station))
            }
    }

    @Test fun streamIdentityWorksWithRenamedStationsPlaylistsAndBitrates() {
        listOf("https://ice2.somafm.com/synphaera-128-mp3", "https://api.somafm.com/synphaera130.pls",
            "https://somafm.com/synphaera/").forEach { url ->
            assertEquals("https://api.somafm.com/logos/512/synphaera512.jpg",
                somaFmStationArtworkUrl(InternetRadioStation("custom", "My ambient station", url)))
        }
        assertEquals("https://api.somafm.com/logos/512/synphaera512.jpg", somaFmStationArtworkUrl(
            InternetRadioStation("custom", "SomaFM: Synphaera", "https://proxy.test/radio")))
    }

    @Test fun channelDigitsAndNonstandardFilenamesAreNotGuessed() {
        listOf("groovesalad2" to "groovesalad2512.png", "sf1033" to "sf1033512.png", "bossa" to "bossa-512.jpg")
            .forEach { (id, file) -> assertEquals("https://api.somafm.com/logos/512/$file", somaFmStationArtworkUrl(
                InternetRadioStation(id, "Renamed", "https://ice.somafm.com/${id}-128-mp3"))) }
    }

    @Test fun unknownChannelsDoNotInventADeadImageUrlAndUnrelatedStationsStayUnchanged() {
        assertNull(somaFmStationArtworkUrl(InternetRadioStation("x", "SomaFM New Channel", "https://ice.somafm.com/newchannel")))
        assertNull(somaFmStationArtworkUrl(InternetRadioStation("x", "The Trip", "https://unrelated.test/thetrip")))
    }

    @Test fun serverArtworkAndSongArtworkRetainPrecedence() {
        val station = InternetRadioStation("synphaera", "Synphaera", "https://ice.somafm.com/synphaera",
            coverArtUrl = "https://server.test/art")
        assertEquals(station.coverArtUrl, station.defaultRadioArtworkUrl())
        assertEquals("https://stream.test/song.jpg", radioArtworkUrl(station, mapOf("artwork" to "https://stream.test/song.jpg")))
    }
}
