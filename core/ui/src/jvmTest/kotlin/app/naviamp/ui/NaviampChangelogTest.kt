package app.naviamp.ui

import app.naviamp.ui.generated.resources.*
import kotlin.test.Test
import kotlin.test.assertEquals

class NaviampChangelogTest {
    @Test
    fun release271ContainsOnlyTheApprovedPopupFix() {
        val changelog = NaviampAboutUi().changelog
        assertEquals(listOf(Res.string.changelog_bug_fixes), changelog.map { it.title })
        assertEquals(listOf(Res.string.changelog_271_popups), changelog.single().entries)
    }
}
