package app.naviamp.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NaviampChangelogTest {
    @Test fun release2120LeadsWithTheCompanionAndCarPlayAndNotesTheAurralRequirement() {
        val about = NaviampAboutUi()
        assertEquals("v2.12.0", about.version)
        assertEquals("65", about.buildNumber)
        assertEquals(listOf("Features", "Improvements", "Bug fixes", "Upgrade notes", "Known issues"),
            about.changelog.map { it.title })
        val sections = about.changelog.associate { it.title to it.entries }
        val features = sections.getValue("Features")
        assertTrue(features[0].contains("Aurral account as a companion") && features[0].contains("without switching servers"))
        assertTrue(features[1].startsWith("Apple CarPlay (beta)") && features[1].contains("Up Next"))
        assertTrue(features[2].startsWith("Apple TV (beta)") && features[2].contains("No Apple TV download is published"))
        val improvements = sections.getValue("Improvements")
        assertTrue(improvements.first().startsWith("Android Auto") && improvements.first().contains("Start Radio"))
        assertEquals("Android TV and Google TV are no longer beta.", improvements[1])
        val fixes = sections.getValue("Bug fixes")
        assertTrue(fixes.first().startsWith("Linux media controls") && fixes.first().contains("private local file"))
        assertTrue(fixes.any { it.startsWith("Mixes for you start a radio station") && it.contains("instead of playing the seed album") })
        assertTrue(fixes.any { it.contains("queues the album or playlist from that track") })
        assertTrue(fixes.any { it.startsWith("Elapsed time keeps advancing") && it.contains("0:00") })
        assertTrue(fixes.any { it.startsWith("On Linux, the first launch") && it.contains("Retry") })
        assertTrue(fixes.any { it.contains("importing settings") && it.contains("duplicate-connection") })
        assertTrue(fixes.any { it.contains("Importing settings keeps this device's volume") })
        assertEquals("Aurral connections now require Aurral 3.x. Aurral 2.x servers are no longer supported.",
            sections.getValue("Upgrade notes").first())
        assertTrue(sections.getValue("Known issues").first().contains("beta") &&
            sections.getValue("Known issues").first().contains("physical CarPlay or Apple TV hardware"))
        val entries = about.changelog.flatMap { it.entries }
        assertTrue(entries.none { it.contains("http") || it.contains("@") || it.contains("#") || it.contains("white bands") })
    }
}
