package app.naviamp.domain.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NamedMediaRequestTest {
    @Test fun explicitKindsRemainDistinct() {
        assertEquals(NamedMediaRequest(NamedMediaKind.ArtistRadio, "CamelPhat", "play CamelPhat radio on Naviamp"),
            namedMediaRequest("play CamelPhat radio on Naviamp"))
        assertEquals(NamedMediaKind.Artist, namedMediaRequest("play the artist CamelPhat on Naviamp")?.kind)
        assertEquals(NamedMediaKind.Album, namedMediaRequest("play the album Discovery on Naviamp")?.kind)
        assertEquals(NamedMediaKind.Playlist, namedMediaRequest("start the playlist Road Trip on Naviamp")?.kind)
        assertEquals("The Album Leaf", namedMediaRequest("play the artist The Album Leaf on Naviamp")?.name)
        assertEquals("Album", namedMediaRequest("play the album Album on Naviamp")?.name)
        assertNull(namedMediaRequest("play KEXP radio station on Naviamp"))
    }

    @Test fun nativeHintAppliesOnlyWhenSpeechHasNoExplicitType() {
        assertEquals(NamedMediaRequest(NamedMediaKind.Album, "Discovery", "Discovery"),
            namedMediaRequest("Discovery", NamedMediaKind.Album))
        assertEquals(NamedMediaKind.ArtistRadio,
            namedMediaRequest("CamelPhat radio", NamedMediaKind.Artist, "CamelPhat")?.kind)
        assertEquals(NamedMediaRequest(NamedMediaKind.Artist, "The Album Leaf", "The Album Leaf"),
            namedMediaRequest("The Album Leaf", NamedMediaKind.Artist, "The Album Leaf"))
        assertEquals("CamelPhat", namedMediaRequest("CamelPhat", NamedMediaKind.ArtistRadio)?.name)
        assertEquals("Road Trip", namedMediaRequest("Road Trip", NamedMediaKind.Playlist)?.name)
    }

    @Test fun tiesNeverSelectAnUnrelatedItem() {
        assertEquals(NamedMediaMatchStatus.Matched,
            namedMediaMatch("Chemical Brothers", listOf("The Chemical Brothers", "Chemical Brothers Live")) { it }.status)
        assertEquals(NamedMediaMatchStatus.Ambiguous,
            namedMediaMatch("Discovery", listOf("Discovery", "Discovery")) { it }.status)
        assertEquals(NamedMediaMatchStatus.Missing,
            namedMediaMatch("Unknown", listOf("Discovery")) { it }.status)
    }
}
