package app.naviamp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.naviamp.ui.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** Static, cached artwork cropped to the pane; progress belongs only to the foreground content. */
@Composable
internal fun NaviampFilledNowPlayingArtwork(
    url: String?,
    colors: NaviampColors,
    mediaKind: SharedMediaItemKind,
    modifier: Modifier = Modifier,
    onArtworkClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit = {},
) {
    BoxWithConstraints(modifier.testTag("now-playing-filled-artwork")) {
        val decodeSize = maxOf(maxWidth, maxHeight)
        val radius = resolvedMediaArtworkCornerRadius(mediaKind, decodeSize, 8.dp)
        val fade = remember { Brush.verticalGradient(
            0f to Color.Black.copy(alpha = 0.12f),
            0.35f to Color.Black.copy(alpha = 0.28f),
            0.65f to Color.Black.copy(alpha = 0.90f),
            1f to Color.Black.copy(alpha = 0.98f),
        ) }
        val artworkAction = if (onArtworkClick != null) {
            val label = stringResource(Res.string.now_playing_toggle_visualizer)
            Modifier.clickable(role = Role.Button, onClickLabel = label, onClick = onArtworkClick)
                .semantics { contentDescription = label }
        } else Modifier
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(radius))) {
            // A rectangular constraint plus Crop preserves the bitmap's aspect ratio. There is
            // no second image, timer, blur or read of playback progress in this background.
            Box(Modifier.matchParentSize().graphicsLayer().then(artworkAction)) {
                NaviampCoverArt(url, colors, decodeSize, 0.dp, Modifier.matchParentSize(), mediaKind = mediaKind)
                Box(Modifier.matchParentSize().background(fade))
            }
            content()
        }
    }
}

internal fun useFilledNowPlayingArtwork(
    enabled: Boolean,
    visualizerVisible: Boolean,
    lyricsVisible: Boolean,
    wideLayout: Boolean,
    panelLayout: NaviampPlayerPanelLayout = NaviampPlayerPanelLayout.Adaptive,
): Boolean = enabled && !visualizerVisible && !lyricsVisible && !wideLayout &&
    panelLayout == NaviampPlayerPanelLayout.Adaptive
