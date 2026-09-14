package app.naviamp.ui

import androidx.compose.ui.graphics.Color
import app.naviamp.domain.settings.AppBackgroundStyle
import app.naviamp.domain.settings.AuroraTone
import app.naviamp.domain.settings.DefaultSingleColorHex
import app.naviamp.domain.settings.InterfaceSettings
import kotlin.test.Test
import kotlin.test.assertEquals

class NaviampAppBackgroundPolicyTest {
    private val colors = NaviampColors.Dark
    private val albumColors = NaviampPlayerColors.fromSingleColor(Color(0xFF186B91), colors)

    @Test
    fun auroraUsesAlbumColorsAndSelectedTone() {
        val background = background(
            InterfaceSettings(
                appBackgroundStyle = AppBackgroundStyle.Aurora,
                auroraTone = AuroraTone.Light,
                auroraColorSteps = 5,
                auroraAngleDegrees = 135,
            ),
        )

        assertEquals(5, background.auroraColorSteps)
        assertEquals(135, background.auroraAngleDegrees)
        assertEquals(AppBackgroundStyle.Aurora, background.style)
        assertEquals(albumColors.withAuroraTone(AuroraTone.Light), background.targetPlayerColors)
    }

    @Test
    fun albumBlurKeepsArtworkAndBlurSettings() {
        val background = background(
            InterfaceSettings(
                appBackgroundStyle = AppBackgroundStyle.AlbumBlur,
                albumBlurRadiusDp = 36,
            ),
        )

        assertEquals(AppBackgroundStyle.AlbumBlur, background.style)
        assertEquals("cover-art", background.coverArtUrl)
        assertEquals(36, background.blurRadiusDp)
        assertEquals(albumColors, background.targetPlayerColors)
    }

    @Test
    fun singleColorUsesSelectedColorAndInvalidValuesFallBack() {
        val selected = background(
            InterfaceSettings(
                appBackgroundStyle = AppBackgroundStyle.SingleColor,
                singleColorHex = "#32253F",
            ),
        )
        val fallback = background(
            InterfaceSettings(
                appBackgroundStyle = AppBackgroundStyle.SingleColor,
                singleColorHex = "invalid",
            ),
        )

        assertEquals(Color(0xFF32253F), selected.singleColor)
        assertEquals(naviampColorFromHex(DefaultSingleColorHex), fallback.singleColor)
        assertEquals(
            NaviampPlayerColors.fromSingleColor(selected.singleColor, colors),
            selected.targetPlayerColors,
        )
    }

    private fun background(settings: InterfaceSettings): NaviampAppBackgroundUi =
        naviampAppBackgroundUi(
            interfaceSettings = settings,
            coverArtUrl = "cover-art",
            albumPlayerColors = albumColors,
            colors = colors,
        )
}
