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
        val aurral = changelog[0].entries[0]
        assertTrue(aurral.contains("Aurral"))
        assertTrue(aurral.contains("recommended and trending"))
        assertTrue(aurral.contains("play or pause samples"))
        assertTrue(aurral.contains("request tracks or albums"))
        assertTrue(aurral.contains("monitoring"))
        assertTrue(aurral.contains("preserve your main queue and position"))
        assertTrue(changelog[0].entries.none { it.contains("Microsoft Store") || it.contains("App Store") })
        val feature = changelog[0].entries[1]
        assertTrue(feature.contains("every platform"))
        assertTrue(feature.contains("PKCS12"))
        assertTrue(feature.contains("private server authorities"))
        assertTrue(feature.contains("uncached playback"))
        assertTrue(changelog[0].entries[2].contains("playlists you created"))
        assertTrue(changelog[0].entries[2].contains("Experience settings"))
        val improvements = changelog[1].entries
        assertEquals(8, improvements.size)
        assertTrue(improvements[0].contains("carousels"))
        assertTrue(improvements[0].contains("saved layout choices"))
        assertTrue(improvements[1].contains("sort priorities"))
        assertTrue(improvements[1].contains("keyboard focus"))
        assertTrue(improvements[7].contains("Top Tracks"))
        assertTrue(improvements[7].contains("5–50 tracks"))
        assertTrue(improvements[7].contains("default remains 10"))
        assertTrue(improvements[7].contains("List/Grid layouts for playlists and Appears on albums"))
        assertTrue(improvements[7].contains("appearance tracks stay in a list"))
        assertTrue(improvements[2].contains("named playlist references"))
        assertTrue(improvements[2].contains("without JSON syntax"))
        assertTrue(improvements[2].contains("owner-aware"))
        assertTrue(improvements[3].contains("synced library"))
        assertTrue(improvements[4].contains("Radio and Sonic"))
        assertTrue(improvements[4].contains("batch sizes"))
        assertTrue(changelog[2].entries[7].contains("Android playback recovery"))
        assertTrue(changelog[2].entries[8].contains("incomplete smart-playlist rules"))
        assertTrue(changelog[2].entries[8].contains("unsupported definitions"))
        assertTrue(changelog[2].entries[9].contains("Library search controls stationary"))
        assertTrue(changelog[2].entries[9].contains("without refreshing the library"))
        assertTrue(changelog[2].entries[9].contains("accented names, aliases and metadata"))
        assertTrue(changelog[2].entries[9].contains("Artists, Albums and Songs"))
        assertTrue(changelog[2].entries[10].contains("popups"))
        val fixes = changelog[2].entries
        val sliders = fixes.single { it.contains("volume slider labels") }
        assertTrue(sliders.contains("playback-position"))
        assertTrue(sliders.contains("keyboard users focus and adjust"))
        assertTrue(fixes.last().contains("Audio Cache and Downloads storage totals"))
        assertTrue(fixes.last().contains("while Settings is open"))
        assertTrue(fixes[0].contains("imported saved connections"))
        assertTrue(fixes[1].contains("native login"))
        assertTrue(fixes[2].contains("pending Autoplay"))
        assertTrue(fixes[3].contains("paused when seeking"))
        assertTrue(fixes[3].contains("beyond the downloaded buffer"))
        assertTrue(fixes[3].contains("finite byte ranges"))
        assertTrue(fixes[4].contains("black tooltip padding"))
        assertTrue(fixes[5].contains("secure credentials"))
        assertTrue(fixes[6].contains("TV Radio sliders"))
        assertTrue(changelog[3].entries.any { it.contains("changed to Aurral") && it.contains("account permissions") })
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
