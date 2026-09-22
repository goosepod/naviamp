package app.naviamp.ui

import app.naviamp.ui.generated.resources.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NaviampChangelogTest {
    @Test
    fun release270LeadsWithFontSizesAndIncludesCrossPlatformAnimationWork() {
        val changelog = NaviampAboutUi().changelog
        assertEquals(listOf(Res.string.changelog_features, Res.string.changelog_improvements,
            Res.string.changelog_bug_fixes, Res.string.changelog_known_issues), changelog.map { it.title })
        assertEquals(listOf(Res.string.changelog_270_font_sizes), changelog.first().entries)
        assertEquals(listOf(Res.string.changelog_270_beta), changelog.last().entries)
        val entries = changelog.flatMap { it.entries }
        assertTrue(Res.string.changelog_270_animations in entries)
        assertTrue(Res.string.changelog_270_library in entries)
        assertTrue(Res.string.changelog_270_playback in entries)
        assertTrue(Res.string.changelog_270_media in entries)
        assertTrue(changelog.all { it.entries.isNotEmpty() })
    }
}
