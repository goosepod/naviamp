package app.naviamp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import app.naviamp.domain.settings.MaxAlbumBlurRadiusDp
import app.naviamp.domain.settings.MinAlbumBlurRadiusDp

@Composable
fun NaviampAlbumBlurBackground(
    url: String?,
    colors: NaviampColors,
    playerColors: NaviampPlayerColors,
    blurRadiusDp: Int,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val blurFraction =
            (blurRadiusDp.coerceIn(MinAlbumBlurRadiusDp, MaxAlbumBlurRadiusDp) - MinAlbumBlurRadiusDp).toFloat() /
                (MaxAlbumBlurRadiusDp - MinAlbumBlurRadiusDp).toFloat()
        val coverScale = 1.04f + blurFraction * 0.10f
        val imageSize = maxOf(maxWidth, maxHeight) * (1.08f + blurFraction * 0.10f)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(scaleX = coverScale, scaleY = coverScale)
                .blur(blurRadiusDp.coerceIn(MinAlbumBlurRadiusDp, MaxAlbumBlurRadiusDp).dp),
        ) {
            PlatformCoverArt(
                url = url,
                colors = colors,
                size = imageSize,
                cornerRadius = 0.dp,
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        playerColors.gradientColors.map { it.copy(alpha = 0.34f) },
                    ),
                )
                .background(Color.Black.copy(alpha = 0.38f)),
        )
    }
}
