package app.naviamp.domain.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MediaVoiceQueryTest {
    @Test
    fun extractsPortableVoiceTargets() {
        assertEquals("Road Trip", MediaVoiceQuery.parse("Play my Road Trip playlist on Naviamp").playlistTarget)
        assertEquals("KEXP", MediaVoiceQuery.parse("Listen to KEXP radio station").stationTarget)
        assertEquals("Bicep", MediaVoiceQuery.parse("Start Bicep radio on Naviamp").radioTarget)
    }

    @Test
    fun classifiesSpecialPlaybackRequests() {
        assertTrue(MediaVoiceQuery.parse("play my downloaded music").isDownloadedMusic)
        assertTrue(MediaVoiceQuery.parse("start library radio").isLibraryRadio)
        assertTrue(MediaVoiceQuery.parse("play Focus playlist").isPlaylist)
        assertTrue(MediaVoiceQuery.parse("play KEXP station").isInternetRadioStation)
        assertFalse(MediaVoiceQuery.parse("play Bicep").isPlaylist)
    }

    @Test
    fun choosesBestNormalizedNameMatch() {
        val names = listOf("The Chemical Brothers", "Chemical Brothers Live", "Chemicals")
        assertEquals("The Chemical Brothers", names.bestVoiceNameMatch("Chemical Brothers") { it })
        assertEquals("The Knife", listOf("Knife Party", "The Knife").bestVoiceNameMatch("the knife") { it })
    }
}
