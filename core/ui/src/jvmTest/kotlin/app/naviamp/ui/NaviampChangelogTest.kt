package app.naviamp.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NaviampChangelogTest {
    @Test
    fun release2111ContainsThePublicChangesInSignificanceOrder() {
        val about = NaviampAboutUi()
        val changelog = about.changelog
        assertEquals("v2.11.1", about.version)
        assertEquals("62", about.buildNumber)
        assertEquals(listOf("Features", "Improvements", "Bug fixes", "Upgrade notes", "Known issues"),
            changelog.map { it.title })
        val feature = changelog.first().entries.single()
        assertTrue(feature.contains("Stream Details") && feature.contains("update") && feature.contains("copying"))
        val improvements = changelog.single { it.title == "Improvements" }.entries
        assertTrue(improvements.any { it.contains("per-song artwork") && it.contains("SomaFM") })
        assertTrue(improvements.any { it.contains("codec and bitrate") && it.contains("setting") })
        val fixes = changelog.single { it.title == "Bug fixes" }.entries
        assertEquals(4, fixes.size)
        assertTrue(fixes.any { it.contains("blank artwork identifier") })
        assertTrue(fixes.any { it.contains("homepage edits") && it.contains("clearing") })
        assertTrue(fixes.any { it.contains("shuffle") && it.contains("restoring") })
        assertTrue(fixes.any { it.contains("generated Radio") && it.contains("duplicate") && it.contains("distinct") })
        val upgrades = changelog.single { it.title == "Upgrade notes" }.entries
        assertTrue(upgrades.any { it.contains("No special upgrade steps") })
        assertTrue(upgrades.any { it.contains("Historical tuning") })
        assertTrue(upgrades.any { it.contains("v2.10.0") && it.contains("await signing") })
        assertTrue(changelog.single { it.title == "Known issues" }.entries.any { it.contains("No new known issues") })
    }

    @Test
    fun aboutUsesPlainCurrentReleaseChangesWithoutRepeatingEarlierContributions() {
        val changes = NaviampAboutUi().changelog.flatMap { it.entries }
        assertTrue(changes.none { it.contains("http") || Regex("(?:Issue|PR|commit) #?\\d+").containsMatchIn(it) })
        assertTrue(changes.none { it.contains("@") || it.contains("Aurral hub") || it.contains("first-time contributor") })
    }
}
