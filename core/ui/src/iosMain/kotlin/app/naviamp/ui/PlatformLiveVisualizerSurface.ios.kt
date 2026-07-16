package app.naviamp.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Uses the shared Canvas renderer until an iOS GPU surface is implemented and verified.
 */
@Composable
internal actual fun PlatformLiveVisualizerSurface(
    coverArtUrl: String?,
    bandsProvider: () -> List<Float>,
    visualizer: NaviampVisualizer,
    visualizerColors: NaviampPlayerColors,
    active: Boolean,
    tempoBpm: Int?,
    colors: NaviampColors,
    lyricStage: LyricMirrorTunnelStage,
    modifier: Modifier,
) {
    val renderPolicy = visualizerRenderPolicy(
        visualizer = visualizer,
        tier = VisualizerRenderTier.Constrained,
    )
    if (visualizer == NaviampVisualizer.LyricMirrorTunnel) {
        LyricMirrorTunnelVisualizerSurface(
            bandsProvider = bandsProvider,
            visualizerColors = visualizerColors,
            active = active,
            colors = colors,
            lyricStage = lyricStage,
            renderPolicy = renderPolicy,
            modifier = modifier,
        )
    } else {
        SpectrumBarsVisualizerSurface(
            bandsProvider = bandsProvider,
            visualizerColors = visualizerColors,
            active = active,
            colors = colors,
            renderPolicy = renderPolicy,
            modifier = modifier,
        )
    }
}
