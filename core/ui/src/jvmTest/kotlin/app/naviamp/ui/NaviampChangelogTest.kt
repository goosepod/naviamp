package app.naviamp.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NaviampChangelogTest {
    @Test
    fun release2100ContainsTheImportantPublicChangesInSignificanceOrder() {
        val changelog = NaviampAboutUi().changelog
        assertEquals(
            listOf("Features", "Improvements", "Bug fixes", "Upgrade notes", "Known issues"),
            changelog.map { it.title },
        )
        assertEquals("v2.10.0", NaviampAboutUi().version)
        assertEquals(3, changelog[0].entries.size)
        assertTrue(changelog[0].entries[0].contains("Microsoft Store"))
        val feature = changelog[0].entries[1]
        assertTrue(feature.contains("every platform"))
        assertTrue(feature.contains("PKCS12"))
        assertTrue(feature.contains("private server authorities"))
        assertTrue(feature.contains("uncached playback"))
        assertTrue(changelog[0].entries[2].contains("playlists you created"))
        assertTrue(changelog[0].entries[2].contains("Experience settings"))
        val improvements = changelog[1].entries
        assertEquals(7, improvements.size)
        assertTrue(improvements[0].contains("sort priorities"))
        assertTrue(improvements[0].contains("keyboard focus"))
        assertTrue(improvements[6].contains("Top Tracks"))
        assertTrue(improvements[6].contains("5–50 tracks"))
        assertTrue(improvements[6].contains("default remains 10"))
        assertTrue(improvements[6].contains("List/Grid layouts for playlists and Appears on albums"))
        assertTrue(improvements[6].contains("appearance tracks stay in a list"))
        assertTrue(improvements[1].contains("named playlist references"))
        assertTrue(improvements[1].contains("without JSON syntax"))
        assertTrue(improvements[1].contains("owner-aware"))
        assertTrue(improvements[2].contains("synced library"))
        assertTrue(improvements[3].contains("Radio and Sonic"))
        assertTrue(improvements[3].contains("batch sizes"))
        assertTrue(changelog[2].entries[6].contains("Android playback recovery"))
        assertTrue(changelog[2].entries[7].contains("incomplete smart-playlist rules"))
        assertTrue(changelog[2].entries[7].contains("unsupported definitions"))
        assertTrue(changelog[2].entries[8].contains("Library search controls stationary"))
        assertTrue(changelog[2].entries[8].contains("without refreshing the library"))
        assertTrue(changelog[2].entries[8].contains("accented names, aliases and metadata"))
        assertTrue(changelog[2].entries[8].contains("Artists, Albums and Songs"))
        assertTrue(changelog[2].entries[9].contains("popups"))
        val fixes = changelog[2].entries
        assertTrue(fixes[0].contains("native login"))
        assertTrue(fixes[1].contains("pending Autoplay"))
        assertTrue(fixes[2].contains("paused when seeking"))
        assertTrue(fixes[3].contains("black tooltip padding"))
        assertTrue(fixes[4].contains("secure credentials"))
        assertTrue(fixes[5].contains("TV Radio sliders"))
        assertTrue(changelog[4].entries.any { it.contains("not confirmed resolved") })
        assertTrue(changelog[3].entries.any { it.contains("No special upgrade steps") })
        assertTrue(changelog[3].entries.any { it.contains("Radio method selection is explicit") })
        assertTrue(changelog[3].entries.any { it.contains("not transferred by settings sync") })
        assertTrue(changelog[4].entries.any { it.contains("buffered-seeking") })
        assertTrue(changelog[4].entries.any { it.contains("macOS Cast") })
        assertTrue(changelog[4].entries.any { it.contains("Linux") })
        assertTrue(changelog.flatMap { it.entries }.none { it.contains("Cast online music from desktop") })
    }
}
