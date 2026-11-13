package app.naviamp.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NaviampChangelogTest {
    @Test fun release2112ContainsOnlyTheBackgroundHotfix() {
        val about = NaviampAboutUi()
        assertEquals("v2.11.2", about.version)
        assertEquals("63", about.buildNumber)
        assertEquals(listOf("Bug fixes", "Upgrade notes", "Known issues"), about.changelog.map { it.title })
        val fix = about.changelog.first().entries.single()
        assertTrue(fix.contains("white bands") && fix.contains("Android"))
        assertTrue(fix.contains("Now Playing artwork") && fix.contains("controls safely inset"))
        assertEquals(listOf("No special upgrade steps are required."), about.changelog[1].entries)
        assertTrue(about.changelog[2].entries.single().contains("No new known issues"))
        val entries = about.changelog.flatMap { it.entries }
        assertTrue(entries.none { it.contains("http") || it.contains("@") || it.contains("Android Auto") })
    }
}