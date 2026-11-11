package app.naviamp.ui

import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackStreamMetadata
import kotlin.test.*

class NaviampStreamDetailsTest {
    private val station = InternetRadioStation("rp", "Radio Paradise", "https://stream.radioparadise.com/aac-320")
    private fun List<NaviampStreamDetailSection>.values() = flatMap { it.rows }.map { it.value }

    @Test fun sparseStationStillEnablesDetailsWithoutInventingSongOrQuality() {
        val ui = station.toRadioNowPlayingUi(PlaybackStreamMetadata(), PlaybackState.Paused, 50, emptyList(), emptyMap())
        assertTrue(ui.menuEnabled)
        assertEquals(listOf(NaviampStreamDetailLabel.Station), ui.streamDetailSections.map { it.label })
        assertEquals(listOf(station.name, station.streamUrl), ui.streamDetailSections.values())
        val action = nowPlayingTrackMenuActions(false, true, ui.streamDetailSections.isNotEmpty(), false, false,
            false, "Sleep timer", detailsLabel = "Stream Details").single { it.action == NaviampAction.TrackDetails }
        assertTrue(action.enabled)
        assertEquals("Stream Details", action.label)
    }

    @Test fun richMetadataExposesGroupedFieldsAndCustomTags() {
        val metadata = PlaybackStreamMetadata("Artist - Song", mapOf(
            "icy-name" to "RP Main", "icy-description" to "Independent music", "icy-genre" to "Eclectic",
            "icy-url" to "https://radioparadise.com", "ARTIST" to "Artist", "ALBUM" to "Album", "genre" to "Rock",
            "Content-Type" to "audio/aac", "icy-br" to "320",
            "ice-audio-info" to "channels=2;samplerate=44100;bitrate=320",
            "StreamUrl" to "https://images.test/song.jpg", "DJ" to "Presenter", "DATE" to "2026", "ISRC" to "TEST123",
        ))
        val sections = station.streamDetails(metadata)
        assertEquals(NaviampStreamDetailLabel.entries.take(4), sections.map { it.label })
        val rows = sections.flatMap { it.rows }
        for (label in listOf(NaviampStreamDetailLabel.Title, NaviampStreamDetailLabel.Artist,
            NaviampStreamDetailLabel.Album, NaviampStreamDetailLabel.Codec, NaviampStreamDetailLabel.Bitrate,
            NaviampStreamDetailLabel.SampleRate, NaviampStreamDetailLabel.Channels, NaviampStreamDetailLabel.ArtworkUrl)) {
            assertTrue(rows.any { it.label == label }, label.name)
        }
        assertTrue(rows.any { it.metadataKey == "DJ" && it.value == "Presenter" })
        assertTrue(rows.any { it.metadataKey == "ISRC" && it.value == "TEST123" })
        assertTrue(rows.any { it.metadataKey == "DATE" && it.value == "2026" })
        assertTrue(sections.first { it.label == NaviampStreamDetailLabel.Station }.rows.any {
            it.label == NaviampStreamDetailLabel.Genre && it.value == "Eclectic" })
        assertTrue(sections.first { it.label == NaviampStreamDetailLabel.Broadcast }.rows.any {
            it.label == NaviampStreamDetailLabel.Genre && it.value == "Rock" })
    }

    @Test fun absentFieldsAndStationChangesCannotLeakPreviousMetadata() {
        val previous = station.streamDetails(PlaybackStreamMetadata("Previous song", mapOf("ALBUM" to "Old album")))
        assertTrue("Old album" in previous.values())
        val current = station.copy(id = "another", name = "Another station").streamDetails(PlaybackStreamMetadata())
        assertFalse("Old album" in current.values())
        assertFalse("Previous song" in current.values())
        assertTrue("Another station" in current.values())
    }

    @Test fun credentialsAndTransportNoiseAreNotDisplayed() {
        val sections = station.copy(streamUrl = "https://user:password@radio.test/live?token=private", homePageUrl = "https://site.test/?session=private")
            .streamDetails(PlaybackStreamMetadata(properties = mapOf(
                "Set-Cookie" to "session=private", "Authorization" to "Bearer private", "X-Api-Key" to "private",
                "Server" to "diagnostic-only", "Connection" to "diagnostic-only", "icy-metaint" to "16000",
                "StreamUrl" to "https://images.test/a.jpg?signature=private", "DJ" to "Public presenter")))
        val text = sections.values().joinToString()
        assertFalse("private" in text)
        assertFalse("password" in text)
        assertFalse("diagnostic-only" in text)
        assertTrue("Public presenter" in text)
    }

    @Test fun embeddedPicturesAreRepresentedWithoutRenderingTheirBinaryPayloadAsText() {
        val rows = station.streamDetails(PlaybackStreamMetadata(properties = mapOf("METADATA_BLOCK_PICTURE" to "large-base64-payload")))
            .flatMap { it.rows }
        assertTrue(rows.single { it.metadataKey == "METADATA_BLOCK_PICTURE" }.embeddedPicture)
        assertFalse(rows.any { "large-base64-payload" in it.value })
    }
}
