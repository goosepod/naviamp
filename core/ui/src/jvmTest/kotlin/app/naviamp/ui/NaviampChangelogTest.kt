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
        assertTrue(entries.any { "Play Next" in it })
        assertTrue(entries.any { "interrupt a group" in it })
        assertTrue(entries.any { "downmix multichannel audio" in it })
        assertTrue(entries.any { "buffering the complete audio file" in it })
        assertTrue(entries.any { "Restoring a long track" in it })
    }
}
