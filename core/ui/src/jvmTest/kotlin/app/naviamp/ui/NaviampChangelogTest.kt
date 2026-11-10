package app.naviamp.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NaviampChangelogTest {
    @Test
    fun release2110ContainsThePublicChangesInSignificanceOrder() {
        val about = NaviampAboutUi()
        val changelog = about.changelog
        assertEquals("v2.11.0", about.version)
        assertEquals(listOf("Features", "Improvements", "Bug fixes", "Upgrade notes", "Known issues", "Thanks"),
            changelog.map { it.title })
        val features = changelog.first().entries
        assertTrue(features.first().contains("Aurral hub"))
        assertTrue(features.first().contains("Track requests"))
        assertTrue(features.first().contains("waiting-for-release"))
        assertTrue(features.first().contains("after reconnecting"))
        assertTrue(features[1].contains("signed x86_64 Flatpak"))
        assertTrue(features.any { it.contains("square album artwork") && it.contains("sync") })
        assertTrue(features.any { it.contains("TV Now Playing controls visible") && it.contains("focus") })
        val fixes = changelog.single { it.title == "Bug fixes" }.entries
        assertTrue(fixes.any { it.contains("old stream immediately") })
        assertTrue(fixes.any { it.contains("Retry lyrics") })
        assertTrue(fixes.any { it.contains("Cast sender module is unavailable") })
        assertTrue(fixes.any { it.contains("album’s saved playback profile") })
        assertTrue(fixes.any { it.contains("final paused playback position") })
        assertTrue(fixes.any { it.contains("audible clock stalls") })
        assertTrue(fixes.any { it.contains("initial announced internet-radio song title") })
        assertTrue(fixes.any { it.contains("tooltips correctly before their first visible frame") })
        val upgrades = changelog.single { it.title == "Upgrade notes" }.entries
        assertTrue(upgrades.any { it.contains("database upgrades automatically") })
        assertTrue(upgrades.any { it.contains("v2.11.0") && it.contains("synchronization") })
        val known = changelog.single { it.title == "Known issues" }.entries
        assertTrue(known.any { it.contains("Android 15") && it.contains("Windows") && it.contains("under investigation") })
        assertTrue(known.any { it.contains("physical GNOME/KDE") && it.contains("outstanding") })
    }

    @Test
    fun aboutUsesPlainChangeListsAndDeduplicatedContributorThanks() {
        val changelog = NaviampAboutUi().changelog
        val changes = changelog.filter { it.title != "Thanks" }.flatMap { it.entries }
        assertTrue(changes.none { it.contains("http") || Regex("(?:Issue|PR|commit) #?\\d+").containsMatchIn(it) })
        assertTrue(changes.none { it.contains("@crsthn-slv") || it.contains("@sewnie") })
        val thanks = changelog.single { it.title == "Thanks" }.entries
        for (handle in listOf("@crsthn-slv", "@sewnie")) {
            assertEquals(1, thanks.count { it.contains(handle) })
            assertTrue(thanks.single { it.contains(handle) }.contains("first-time contributor"))
        }
        assertTrue(thanks.single { it.contains("@crsthn-slv") }.contains("Brazilian Portuguese translation"))
        assertTrue(thanks.single { it.contains("@sewnie") }.contains("reporting"))
    }
}
