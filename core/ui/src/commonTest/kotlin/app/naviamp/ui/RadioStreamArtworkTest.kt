package app.naviamp.ui

import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.bass.bassStreamProperties
import app.naviamp.domain.playback.PlaybackStreamMetadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class RadioStreamArtworkTest {
    private val station = InternetRadioStation("rp", "Radio Paradise", "https://stream.radioparadise.com/aac-320",
        coverArtUrl = "https://station.test/logo.png")

    @Test fun nativeIcyBlockUsesTheCurrentSongCoverBeforeStationOrSearchArtwork() {
        val url = "http://img.radioparadise.com/covers/l/8091.jpg"
        val properties = bassStreamProperties(listOf("StreamTitle=StreamTitle='Artist - Song';StreamUrl='$url';"))
        val metadata = PlaybackStreamMetadata.fromProperties(properties)
        assertEquals("Artist - Song", metadata.title)
        assertEquals(url, radioArtworkUrl(station, properties, "https://library.test/cover.png"))
        assertEquals(url, effectiveNowPlayingCoverArtUrl(station.coverArtUrl, null, station, metadata, emptyMap()))
        assertFalse(radioArtworkNeedsTrackLookup(station, metadata.title, properties))
    }

    @Test fun explicitArtworkWinsRegardlessOfPropertyOrder() {
        val properties = linkedMapOf("StreamUrl" to "https://images.test/song.jpg", "artwork" to "https://images.test/explicit")
        assertEquals("https://images.test/explicit", radioArtworkUrl(station, properties))
    }

    @Test fun acceptsImageQueriesFragmentsAndCaseVariants() {
        val url = "HTTPS://images.test/cover.JPEG?size=600#cover"
        assertEquals(url, radioArtworkUrl(station, mapOf("streamurl" to " $url ")))
    }

    @Test fun stationPagesAndUnsafeOrMalformedUrlsKeepTheLogo() {
        listOf("", "https://radioparadise.com/", "https://example.test/page?cover=song.jpg",
            "https://example.test/page#song.jpg", "file:///cover.jpg", "javascript:cover.jpg",
            "https:///cover.jpg", "https://images.test/not a cover.jpg", "https://user@images.test/a.jpg")
            .forEach { url -> assertEquals(station.coverArtUrl, radioArtworkUrl(station, mapOf("StreamUrl" to url)), url) }
    }

    @Test fun changesAndClearsFollowTheLatestMetadataWithoutStaleCovers() {
        fun artwork(block: String) = radioArtworkUrl(station, bassStreamProperties(listOf("StreamTitle=$block")))
        assertEquals("https://images.test/first.jpg", artwork("StreamTitle='First';StreamUrl='https://images.test/first.jpg';"))
        assertEquals("https://images.test/next.png", artwork("StreamTitle='Next';StreamUrl='https://images.test/next.png';"))
        assertEquals(station.coverArtUrl, artwork("StreamTitle='Last';StreamUrl='';"))
        assertEquals(station.coverArtUrl, artwork("StreamTitle='Last';"))
    }
}
