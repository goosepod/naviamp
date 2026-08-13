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
        assertTrue(entries.any { "shared application" in it })
        assertTrue(entries.any { "Jellyfin" in it && "Bandcamp" in it })
        assertTrue(entries.any { "secure credential storage" in it })
        assertTrue(entries.any { "installer upgrade ordering" in it })
    }
}
