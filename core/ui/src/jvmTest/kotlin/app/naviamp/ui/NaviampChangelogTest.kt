package app.naviamp.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NaviampChangelogTest {
    @Test fun release2121KeepsJellyfinDevicesSignedInRestoresTranscodedPlaybackAndThanksTheReporter() {
        val about = NaviampAboutUi()
        assertEquals("v2.12.1", about.version)
        assertEquals("66", about.buildNumber)
        assertEquals(listOf("Bug fixes", "Upgrade notes", "Known issues", "Thanks"),
            about.changelog.map { it.title })
        val sections = about.changelog.associate { it.title to it.entries }
        val fixes = sections.getValue("Bug fixes")
        assertEquals(2, fixes.size)
        assertTrue(fixes[0].contains("second device") && fixes[0].contains("no longer signs out the first"))
        assertTrue(fixes[1].contains("Jellyfin 12") && fixes[1].contains("transcoded") && fixes[1].contains("Invalid token"))
        val upgrade = sections.getValue("Upgrade notes")
        assertTrue(upgrade[0].contains("still signed in keep working"))
        assertTrue(upgrade[1].contains("enter your password") && upgrade[1].contains("do not need to add the connection again"))
        assertTrue(sections.getValue("Known issues").first().contains("physical CarPlay or Apple TV hardware"))
        val thanks = sections.getValue("Thanks").single()
        assertTrue(thanks.startsWith("tobycell12-beep") && thanks.contains("first-time contributor"))
        val entries = about.changelog.flatMap { it.entries }
        assertTrue(entries.none { it.contains("http") || it.contains("@") || it.contains("#") || it.contains("Aurral 3.x") })
    }
}
