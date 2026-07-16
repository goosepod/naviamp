package app.naviamp.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.naviamp.domain.settings.AppBackgroundStyle
import app.naviamp.domain.settings.DefaultSingleColorHex
import app.naviamp.domain.settings.InterfaceSettings
import app.naviamp.ui.NaviampAlbumBlurBackground
import app.naviamp.ui.NaviampPlayerColors
import app.naviamp.ui.naviampColorFromHex
import app.naviamp.ui.rememberNaviampTypography

@Composable
internal fun DesktopAppSurface(
    colorScheme: ColorScheme,
    appColors: DesktopAppColors,
    statsForNerdsInfo: DesktopStatsForNerdsInfo?,
    backgroundStart: Color,
    backgroundMid: Color,
    backgroundEnd: Color,
    targetBackgroundColors: NaviampPlayerColors,
    coverArtUrl: String?,
    interfaceSettings: InterfaceSettings,
    onCloseStatsForNerds: () -> Unit,
    content: @Composable () -> Unit,
) {
    MaterialTheme(colorScheme = colorScheme, typography = rememberNaviampTypography()) {
        statsForNerdsInfo?.let { info ->
            DesktopStatsForNerdsWindow(
                appColors = appColors,
                info = info,
                onClose = onCloseStatsForNerds,
            )
        }
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize(),
            ) {
                val density = LocalDensity.current
                val widthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
                val heightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
                val narrowLayout = maxWidth < 520.dp
                val gradientEnd = if (narrowLayout) {
                    Offset(widthPx * 0.85f, heightPx * 1.05f)
                } else {
                    Offset(widthPx * 1.08f, heightPx * 0.82f)
                }

                val animatedPlayerColors = NaviampPlayerColors(
                    backgroundStart = backgroundStart,
                    backgroundMid = backgroundMid,
                    backgroundEnd = backgroundEnd,
                    accent = targetBackgroundColors.accent,
                )
                val singleColor = naviampColorFromHex(interfaceSettings.singleColorHex)
                    ?: naviampColorFromHex(DefaultSingleColorHex)!!
                Box(modifier = Modifier.fillMaxSize()) {
                    when (interfaceSettings.appBackgroundStyle) {
                        AppBackgroundStyle.Aurora -> Box(
                            Modifier
                                .fillMaxSize()
                                .background(animatedPlayerColors.backgroundBrush(gradientEnd)),
                        )
                        AppBackgroundStyle.AlbumBlur -> NaviampAlbumBlurBackground(
                            url = coverArtUrl,
                            colors = appColors,
                            playerColors = animatedPlayerColors,
                            blurRadiusDp = interfaceSettings.albumBlurRadiusDp,
                        )
                        AppBackgroundStyle.SingleColor -> Box(
                            Modifier
                                .fillMaxSize()
                                .background(singleColor),
                        )
                    }
                    content()
                }
            }
        }
    }
}
