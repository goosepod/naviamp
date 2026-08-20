package app.naviamp.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NaviampChangelogTest {
    @Test
    fun inAppChangelogContainsOnlyUserFacingCategories() {
        val changelog = NaviampAboutUi().changelog

        assertEquals(listOf("Features", "Bug Fixes"), changelog.map { it.title })
        assertTrue(changelog.all { section -> section.entries.isNotEmpty() })
        assertTrue(changelog.flatMap { it.entries }.all { entry -> entry.lines().size == 1 })
        val entries = changelog.flatMap { it.entries }
        assertTrue(entries.any { "album or saved playlist" in it })
        assertTrue(entries.any { "queue groups" in it })
        assertTrue(entries.any { "Stats for Nerds" in it })
        assertTrue(entries.any { "refreshes its queue" in it })
        assertTrue(entries.any { "keeps its custom playback settings" in it })
        assertTrue(entries.any { "Waveform seeking" in it })
    }
}
