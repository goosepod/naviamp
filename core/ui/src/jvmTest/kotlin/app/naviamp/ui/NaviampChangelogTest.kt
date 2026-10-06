package app.naviamp.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NaviampChangelogTest {
    @Test
    fun release290ContainsTheImportantPublicChangesInSignificanceOrder() {
        val changelog = NaviampAboutUi().changelog
        assertEquals(
            listOf("Features", "Improvements", "Bug fixes", "Upgrade notes", "Known issues"),
            changelog.map { it.title },
        )
        assertEquals("2.9.0", NaviampAboutUi().version.removePrefix("v"))
        assertEquals(6, changelog[0].entries.size)
        assertTrue(changelog[0].entries[0].contains("Google Cast"))
        assertTrue(changelog[0].entries[0].contains("desktop"))
        assertTrue(changelog[0].entries[1].contains("downloaded"))
        assertTrue(changelog[0].entries[2].contains("fullscreen"))
        assertTrue(changelog[0].entries[2].contains("compact players"))
        assertTrue(changelog[0].entries[3].contains("MPRIS"))
        assertTrue(changelog[0].entries[4].contains("Android TV"))
        assertTrue(changelog[0].entries[5].contains("artwork"))
        assertTrue(changelog[1].entries.any { it.contains("multi-disc") })
        assertTrue(changelog[1].entries.any { it.contains("DJs") })
        assertTrue(changelog[1].entries.any { it.contains("device choices") })
        assertTrue(changelog[2].entries.any { it.contains("Smart Playlists") })
        assertTrue(changelog[2].entries.any { it.contains("Linux X11") })
        assertTrue(changelog[2].entries.any { it.contains("Stats for Nerds") && it.contains("macOS") })
        assertTrue(changelog[3].entries.single().contains("pre-upgrade database backup"))
        assertTrue(changelog[4].entries.any { it.contains("macOS Cast") })
        assertTrue(changelog[4].entries.none { it.contains("macOS and Linux popup") })
    }
}
