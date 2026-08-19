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
        assertTrue(entries.any { "MusicBrainz hierarchy" in it })
        assertTrue(entries.any { "smart-playlist rules" in it })
        assertTrue(entries.any { "original year" in it })
        assertTrue(entries.any { "track-specific artwork" in it })
        assertTrue(entries.any { "unmatched server tags" in it })
        assertTrue(entries.any { "Settings keeps its page" in it })
    }
}
