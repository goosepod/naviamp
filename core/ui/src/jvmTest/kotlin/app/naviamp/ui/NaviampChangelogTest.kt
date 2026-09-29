package app.naviamp.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NaviampChangelogTest {
    @Test
    fun release280ContainsTheImportantPublicChangesInSignificanceOrder() {
        val changelog = NaviampAboutUi().changelog
        assertEquals(
            listOf("Features", "Improvements", "Bug fixes"),
            changelog.map { it.title },
        )
        assertEquals(4, changelog[0].entries.size)
        assertTrue(changelog[0].entries[0].contains("Google Cast"))
        assertTrue(changelog[0].entries[1].contains("Tailnets"))
        assertTrue(changelog[0].entries[2].contains("OpenSubsonic API key"))
        assertTrue(changelog[0].entries[3].contains("visualizer"))
        assertTrue(changelog[1].entries.single().contains("cached audio"))
        assertTrue(changelog[2].entries.single().contains("Subsonic password fallback"))
    }
}
