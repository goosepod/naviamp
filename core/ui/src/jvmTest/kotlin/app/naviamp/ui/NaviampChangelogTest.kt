package app.naviamp.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NaviampChangelogTest {
    @Test
    fun release2100ContainsTheImportantPublicChangesInSignificanceOrder() {
        val changelog = NaviampAboutUi().changelog
        assertEquals(
            listOf("Improvements", "Bug fixes", "Upgrade notes", "Known issues"),
            changelog.map { it.title },
        )
        assertEquals("2.10.0", NaviampAboutUi().version.removePrefix("v"))
        assertEquals(5, changelog[0].entries.size)
        assertTrue(changelog[0].entries[0].contains("named playlist references"))
        assertTrue(changelog[0].entries[0].contains("owner-aware"))
        assertTrue(changelog[0].entries[1].contains("synced library"))
        assertTrue(changelog[0].entries[2].contains("Radio and Sonic"))
        assertTrue(changelog[0].entries[2].contains("batch sizes"))
        assertTrue(changelog[1].entries[0].contains("Android playback recovery"))
        assertTrue(changelog[1].entries[1].contains("incomplete smart-playlist rules"))
        assertTrue(changelog[1].entries[1].contains("unsupported definitions"))
        assertTrue(changelog[1].entries[2].contains("popups"))
        assertTrue(changelog[2].entries.single().contains("No special upgrade steps"))
        assertTrue(changelog[2].entries.single().contains("silently switching"))
        assertTrue(changelog[3].entries.any { it.contains("macOS Cast") })
        assertTrue(changelog[3].entries.any { it.contains("Linux") })
        assertTrue(changelog[0].entries.none { it.contains("Cast online music from desktop") })
    }
}
