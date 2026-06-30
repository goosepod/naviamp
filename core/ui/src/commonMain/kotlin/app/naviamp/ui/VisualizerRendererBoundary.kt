package app.naviamp.ui

internal const val VisualizerFrameBandCount = 32
internal const val VisualizerHistoryColumns = 32
internal const val VisualizerHistoryRows = 32

internal enum class VisualizerRendererMode {
    Canvas,
    SkiaRuntimeShader,
    NativeGpu,
}

internal data class VisualizerEnergyLevels(
    val bass: Float,
    val mids: Float,
    val highs: Float,
    val energy: Float,
    val spectralCentroid: Float,
    val beatDetected: Float,
)

internal data class VisualizerFrameInput(
    val width: Float,
    val height: Float,
    val active: Boolean,
    val timeSeconds: Float,
    val tempoBpm: Float,
    val visibleBands: Float,
    val sourceBands: Float,
    val bands: FloatArray,
    val energy: VisualizerEnergyLevels,
) {
    val resolution: Pair<Float, Float>
        get() = width to height
}

internal val NaviampVisualizer.preferredRendererMode: VisualizerRendererMode
    get() = if (nativeShaderDefinition != null) {
        VisualizerRendererMode.NativeGpu
    } else {
        when (this) {
            NaviampVisualizer.SpectrumBars -> VisualizerRendererMode.Canvas
            NaviampVisualizer.AlbumArtReactive,
            NaviampVisualizer.AnalogSignalFailure,
            NaviampVisualizer.AudioSphere,
            NaviampVisualizer.AudioTunnel,
            NaviampVisualizer.FluidicNebulae,
            NaviampVisualizer.FluidGradient,
            NaviampVisualizer.FrequencyTerrain,
            NaviampVisualizer.FftMountain,
            NaviampVisualizer.OceanHorizon,
            NaviampVisualizer.OceanOfInk,
            NaviampVisualizer.ParticleField,
            NaviampVisualizer.ParticleGalaxy,
            NaviampVisualizer.PixelMountain,
            NaviampVisualizer.PixelRidge,
            NaviampVisualizer.RaymarchedSphereLiquid,
            NaviampVisualizer.ReactiveBars,
            NaviampVisualizer.RibbonTrail,
            NaviampVisualizer.SpectralRidge,
            NaviampVisualizer.WaveInterference,
            NaviampVisualizer.VinylGroove -> VisualizerRendererMode.SkiaRuntimeShader
        }
    }

internal val NaviampVisualizer.usesNativeRenderer: Boolean
    get() = preferredRendererMode == VisualizerRendererMode.NativeGpu

internal val NaviampVisualizer.fallbackRendererMode: VisualizerRendererMode
    get() = when (this) {
        NaviampVisualizer.SpectrumBars -> VisualizerRendererMode.Canvas
        NaviampVisualizer.AlbumArtReactive,
        NaviampVisualizer.AnalogSignalFailure,
        NaviampVisualizer.AudioSphere,
        NaviampVisualizer.AudioTunnel,
        NaviampVisualizer.FluidicNebulae,
        NaviampVisualizer.FluidGradient,
        NaviampVisualizer.FrequencyTerrain,
        NaviampVisualizer.FftMountain,
        NaviampVisualizer.OceanHorizon,
        NaviampVisualizer.OceanOfInk,
        NaviampVisualizer.ParticleField,
        NaviampVisualizer.ParticleGalaxy,
        NaviampVisualizer.PixelMountain,
        NaviampVisualizer.PixelRidge,
        NaviampVisualizer.RaymarchedSphereLiquid,
        NaviampVisualizer.ReactiveBars,
        NaviampVisualizer.RibbonTrail,
        NaviampVisualizer.SpectralRidge,
        NaviampVisualizer.WaveInterference,
        NaviampVisualizer.VinylGroove -> VisualizerRendererMode.SkiaRuntimeShader
    }

internal fun selectedVisualizerRendererMode(
    visualizer: NaviampVisualizer,
    nativeRendererAvailable: Boolean,
): VisualizerRendererMode =
    if (nativeRendererAvailable && visualizer.preferredRendererMode == VisualizerRendererMode.NativeGpu) {
        VisualizerRendererMode.NativeGpu
    } else {
        visualizer.fallbackRendererMode
    }

internal fun smoothVisualizerBands(
    sourceBands: List<Float>,
    smoothBands: FloatArray,
    outputBands: FloatArray,
) {
    repeat(outputBands.size) { index ->
        val sourceIndex = if (outputBands.size == 1 || sourceBands.isEmpty()) {
            0
        } else {
            ((index / (outputBands.size - 1f)) * (sourceBands.size - 1)).toInt()
        }
        val target = sourceBands.getOrNull(sourceIndex)?.coerceIn(0f, 1f) ?: 0f
        val rise = if (target > smoothBands[index]) 0.42f else 0.18f
        smoothBands[index] += (target - smoothBands[index]) * rise
        outputBands[index] = smoothBands[index].coerceIn(0f, 1f)
    }
}

internal fun buildVisualizerFrameInput(
    width: Float,
    height: Float,
    bands: List<Float>,
    visibleBands: Int,
    active: Boolean,
    timeSeconds: Float,
    tempoBpm: Int?,
    uniformBands: FloatArray,
): VisualizerFrameInput {
    val energy = visualizerEnergyLevels(uniformBands)
    return VisualizerFrameInput(
        width = width,
        height = height,
        active = active,
        timeSeconds = timeSeconds,
        tempoBpm = tempoBpm?.toFloat()?.coerceIn(60f, 220f) ?: 120f,
        visibleBands = visibleBands.toFloat(),
        sourceBands = bands.size.toFloat().coerceAtLeast(1f),
        bands = uniformBands.copyOf(),
        energy = energy,
    )
}

private fun visualizerEnergyLevels(bands: FloatArray): VisualizerEnergyLevels {
    val bass = bands.averageRange(0, 8)
    val mids = bands.averageRange(8, 20)
    val highs = bands.averageRange(20, bands.size)
    val energy = bands.average().toFloat().coerceIn(0f, 1f)
    val weightedTotal = bands.indices.sumOf { index ->
        (index.toDouble() / (bands.size - 1).coerceAtLeast(1).toDouble()) * bands[index].toDouble()
    }
    val total = bands.sum().coerceAtLeast(0.0001f)
    val spectralCentroid = (weightedTotal / total).toFloat().coerceIn(0f, 1f)
    val beatDetected = if (bass > 0.68f && bass > mids * 1.18f && energy > 0.34f) 1f else 0f
    return VisualizerEnergyLevels(
        bass = bass,
        mids = mids,
        highs = highs,
        energy = energy,
        spectralCentroid = spectralCentroid,
        beatDetected = beatDetected,
    )
}

private fun FloatArray.averageRange(start: Int, end: Int): Float {
    val safeStart = start.coerceIn(0, size)
    val safeEnd = end.coerceIn(safeStart, size)
    if (safeStart == safeEnd) return 0f
    var total = 0f
    for (index in safeStart until safeEnd) {
        total += this[index]
    }
    return (total / (safeEnd - safeStart)).coerceIn(0f, 1f)
}
