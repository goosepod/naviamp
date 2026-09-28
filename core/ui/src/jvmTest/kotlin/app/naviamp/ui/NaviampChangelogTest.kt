package app.naviamp.ui

import app.naviamp.ui.generated.resources.*
import kotlin.test.Test
import kotlin.test.assertEquals

class NaviampChangelogTest {
    @Test
    fun release280ContainsTheImportantPublicChangesInSignificanceOrder() {
        val changelog = NaviampAboutUi().changelog
        assertEquals(
            listOf(Res.string.changelog_features, Res.string.changelog_improvements, Res.string.changelog_bug_fixes),
            changelog.map { it.title },
        )
        assertEquals(
            listOf(
                Res.string.changelog_280_cast,
                Res.string.changelog_280_connect,
                Res.string.changelog_280_api_key,
                Res.string.changelog_280_visualizer,
            ),
            changelog[0].entries,
        )
        assertEquals(listOf(Res.string.changelog_280_improvements), changelog[1].entries)
        assertEquals(listOf(Res.string.changelog_280_fixes), changelog[2].entries)
    }
}
