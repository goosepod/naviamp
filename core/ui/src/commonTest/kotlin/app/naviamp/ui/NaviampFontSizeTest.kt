package app.naviamp.ui

import app.naviamp.domain.settings.InterfaceFontSize
import kotlin.test.Test
import kotlin.test.assertEquals

class NaviampFontSizeTest {
    @Test
    fun standardPreservesExistingNaviampScale() {
        assertEquals(1.08f, naviampFontScaleMultiplier(
            InterfaceFontSize.Standard,
            includeStandardScale = true,
        ))
    }

    @Test
    fun nowPlayingScaleIsIndependentFromGeneralScale() {
        val generalScale = naviampFontScaleMultiplier(
            InterfaceFontSize.Large,
            includeStandardScale = true,
        )
        val relativeNowPlayingScale = naviampFontScaleMultiplier(
            InterfaceFontSize.Small,
            relativeTo = InterfaceFontSize.Large,
        )

        assertEquals(1.08f * InterfaceFontSize.Small.scale, generalScale * relativeNowPlayingScale)
    }

    @Test
    fun systemAccessibilityScaleComposesMultiplicatively() {
        val systemScale = 1.35f
        assertEquals(
            systemScale * NaviampStandardFontScale * InterfaceFontSize.Large.scale,
            systemScale * naviampFontScaleMultiplier(
                InterfaceFontSize.Large,
                includeStandardScale = true,
            ),
        )
    }
}
