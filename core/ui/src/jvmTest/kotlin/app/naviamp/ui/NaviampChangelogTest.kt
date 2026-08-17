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
        assertTrue(entries.any { "Recently Played Radio" in it })
        assertTrue(entries.any { "5, 10, 20, or 50" in it })
        assertTrue(entries.any { "Starting radio from an album" in it })
        assertTrue(entries.any { "scrub bar" in it })
        assertTrue(entries.any { "Playlist names" in it })
    }
}
