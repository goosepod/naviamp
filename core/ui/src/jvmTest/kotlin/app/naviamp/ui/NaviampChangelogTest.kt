package app.naviamp.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NaviampChangelogTest {
    @Test fun release2121RestoresJellyfin12TranscodedPlaybackAndThanksTheReporter() {
        val about = NaviampAboutUi()
        assertEquals("v2.12.1", about.version)
        assertEquals("66", about.buildNumber)
        assertEquals(listOf("Bug fixes", "Upgrade notes", "Known issues", "Thanks"),
            about.changelog.map { it.title })
        val sections = about.changelog.associate { it.title to it.entries }
        val fix = sections.getValue("Bug fixes").single()
        assertTrue(fix.contains("Jellyfin 12") && fix.contains("transcoded") && fix.contains("Invalid token"))
        assertTrue(sections.getValue("Upgrade notes").single().contains("do not need to sign in again"))
        assertTrue(sections.getValue("Known issues").first().contains("physical CarPlay or Apple TV hardware"))
        val thanks = sections.getValue("Thanks").single()
        assertTrue(thanks.startsWith("tobycell12-beep") && thanks.contains("first-time contributor"))
        val entries = about.changelog.flatMap { it.entries }
        assertTrue(entries.none { it.contains("http") || it.contains("@") || it.contains("#") || it.contains("Aurral 3.x") })
    }
}
