package app.naviamp.ui

internal enum class VisualizerRenderTier(val label: String) {
    Full("full"),
    Balanced("balanced"),
    Constrained("constrained"),
}

internal data class VisualizerRenderPolicy(
    val tier: VisualizerRenderTier,
    val targetFrameIntervalMillis: Long,
    val historyIntervalSeconds: Float,
    val albumArtSidePx: Int,
    val maxCanvasSamples: Int,
) {
    val targetFps: Int
        get() = (1_000L / targetFrameIntervalMillis.coerceAtLeast(1L)).toInt()
}

internal fun visualizerRenderPolicy(
    visualizer: NaviampVisualizer,
    tier: VisualizerRenderTier,
): VisualizerRenderPolicy {
    val heavy = visualizer.isHeavyVisualizer
    return when (tier) {
        VisualizerRenderTier.Full -> VisualizerRenderPolicy(
            tier = tier,
            targetFrameIntervalMillis = 16L,
            historyIntervalSeconds = 0.075f,
            albumArtSidePx = 1024,
            maxCanvasSamples = 96,
        )
        VisualizerRenderTier.Balanced -> VisualizerRenderPolicy(
            tier = tier,
            targetFrameIntervalMillis = if (heavy) 33L else 16L,
            historyIntervalSeconds = if (heavy) 0.120f else 0.075f,
            albumArtSidePx = 512,
            maxCanvasSamples = if (heavy) 56 else 72,
        )
        VisualizerRenderTier.Constrained -> VisualizerRenderPolicy(
            tier = tier,
            targetFrameIntervalMillis = if (heavy) 50L else 33L,
            historyIntervalSeconds = if (heavy) 0.180f else 0.120f,
            albumArtSidePx = 384,
            maxCanvasSamples = if (heavy) 40 else 56,
        )
    }
}

private val NaviampVisualizer.isHeavyVisualizer: Boolean
    get() = this == NaviampVisualizer.AlbumArtReactive ||
        this == NaviampVisualizer.AnalogSignalFailure ||
        this == NaviampVisualizer.FrequencyTerrain ||
        this == NaviampVisualizer.FluidicNebulae ||
        this == NaviampVisualizer.OceanHorizon ||
        this == NaviampVisualizer.OceanOfInk ||
        this == NaviampVisualizer.ParticleGalaxy ||
        this == NaviampVisualizer.ParticleField ||
        this == NaviampVisualizer.RaymarchedSphereLiquid ||
        this == NaviampVisualizer.WaveInterference
