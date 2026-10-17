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
        assertEquals(2, changelog[0].entries.size)
        val feature = changelog[0].entries.first()
        assertTrue(feature.contains("every platform"))
        assertTrue(feature.contains("PKCS12"))
        assertTrue(feature.contains("private server authorities"))
        assertTrue(feature.contains("uncached playback"))
        assertTrue(changelog[0].entries[1].contains("playlists you created"))
        assertTrue(changelog[0].entries[1].contains("Experience settings"))
        val improvements = changelog[1].entries
        assertEquals(5, improvements.size)
        assertTrue(improvements[0].contains("named playlist references"))
        assertTrue(improvements[0].contains("without JSON syntax"))
        assertTrue(improvements[0].contains("owner-aware"))
        assertTrue(improvements[1].contains("synced library"))
        assertTrue(improvements[2].contains("Radio and Sonic"))
        assertTrue(improvements[2].contains("batch sizes"))
        assertTrue(changelog[2].entries[0].contains("Android playback recovery"))
        assertTrue(changelog[2].entries[1].contains("incomplete smart-playlist rules"))
        assertTrue(changelog[2].entries[1].contains("unsupported definitions"))
        assertTrue(changelog[2].entries[2].contains("Library search controls stationary"))
        assertTrue(changelog[2].entries[2].contains("without refreshing the library"))
        assertTrue(changelog[2].entries[3].contains("popups"))
        assertTrue(changelog[3].entries.any { it.contains("No special upgrade steps") })
        assertTrue(changelog[3].entries.any { it.contains("Radio method selection is explicit") })
        assertTrue(changelog[3].entries.any { it.contains("not transferred by settings sync") })
        assertTrue(changelog[4].entries.any { it.contains("buffered-seeking") })
        assertTrue(changelog[4].entries.any { it.contains("macOS Cast") })
        assertTrue(changelog[4].entries.any { it.contains("Linux") })
        assertTrue(changelog.flatMap { it.entries }.none { it.contains("Cast online music from desktop") })
    }
}
