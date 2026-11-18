package app.naviamp.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NaviampChangelogTest {
    @Test fun release2113ContainsTheJava4ShellHotfix() {
        val about = NaviampAboutUi()
        assertEquals("v2.11.3", about.version)
        assertEquals("64", about.buildNumber)
        assertEquals(listOf("Bug fixes", "Upgrade notes", "Known issues"), about.changelog.map { it.title })
        assertEquals(
            listOf(
                "Update the bundled Java runtime on Windows and Linux to fix Java4Shell (CVE-2026-114938).",
                "Remove the blue background behind the taskbar icon in the Microsoft Store build.",
            ),
            about.changelog.first().entries,
        )
        assertEquals(listOf("Install the updated Naviamp package to replace its bundled Java runtime."), about.changelog[1].entries)
        assertTrue(about.changelog[2].entries.single().contains("No new known issues"))
        val entries = about.changelog.flatMap { it.entries }
        assertTrue(entries.none { it.contains("http") || it.contains("@") || it.contains("Android Auto") })
    }
}