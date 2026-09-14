package app.naviamp.ui

import app.naviamp.ui.generated.resources.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NaviampChangelogTest {
    @Test
    fun release260LeadsWithTvAndConnectBetaAndIncludesTheBetaNotice() {
        val changelog = NaviampAboutUi().changelog
        assertEquals(listOf(Res.string.changelog_features, Res.string.changelog_improvements,
            Res.string.changelog_bug_fixes, Res.string.changelog_known_issues), changelog.map { it.title })
        assertEquals(listOf(Res.string.changelog_260_tv, Res.string.changelog_260_connect), changelog.first().entries)
        assertEquals(listOf(Res.string.changelog_260_beta), changelog.last().entries)
        val entries = changelog.flatMap { it.entries }
        assertTrue(Res.string.changelog_260_screen_awake in entries)
        assertTrue(Res.string.changelog_260_playback_recovery in entries)
        assertTrue(changelog.all { it.entries.isNotEmpty() })
    }
}
