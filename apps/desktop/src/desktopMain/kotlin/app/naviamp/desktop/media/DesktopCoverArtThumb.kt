package app.naviamp.desktop

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.naviamp.ui.PlatformCoverArt

@Composable
fun DesktopCoverArtThumb(
    appColors: DesktopAppColors,
    coverArtUrl: String?,
    size: Dp,
    cornerRadius: Dp = 5.dp,
) {
    PlatformCoverArt(coverArtUrl, appColors, size, cornerRadius)
}
