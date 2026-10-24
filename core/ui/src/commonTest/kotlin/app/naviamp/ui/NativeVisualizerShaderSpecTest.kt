package app.naviamp.ui

import kotlin.test.*

class NativeVisualizerShaderSpecTest {
    @Test fun allBackendsReceiveCanonicalShaderAndQualityValues() {
        for (effect in listOf(NaviampVisualizer.AnalogSignalFailure, NaviampVisualizer.AudioTunnel,
            NaviampVisualizer.FluidicNebulae, NaviampVisualizer.LyricMirrorTunnel, NaviampVisualizer.OceanHorizon,
            NaviampVisualizer.OceanOfInk, NaviampVisualizer.RaymarchedSphereLiquid)) {
            for (tier in VisualizerRenderTier.entries) {
                val policy = visualizerRenderPolicy(effect, tier)
                val spec = effect.nativeShaderSpec(policy)
                assertEquals(effect.nativeShaderDefinition!!.fragmentSource, spec.fragmentSource)
                assertEquals(effect.nativeVisualizerRenderScale(policy), spec.renderScale)
                assertEquals(effect.nativeVisualizerMaxRaymarchSteps(policy), spec.maxRaymarchSteps)
            }
        }
    }

    @Test fun qualityTiersKeepDeclaredBudgetsAndLyricMasksAtFullResolution() {
        fun NaviampVisualizer.spec(tier: VisualizerRenderTier) = nativeShaderSpec(visualizerRenderPolicy(this, tier))
        assertEquals(.82f, NaviampVisualizer.OceanHorizon.spec(VisualizerRenderTier.Full).renderScale)
        assertEquals(.82f, NaviampVisualizer.AnalogSignalFailure.spec(VisualizerRenderTier.Balanced).renderScale)
        assertEquals(.65f, NaviampVisualizer.OceanOfInk.spec(VisualizerRenderTier.Constrained).renderScale)
        assertEquals(38, NaviampVisualizer.AudioTunnel.spec(VisualizerRenderTier.Constrained).maxRaymarchSteps)
        assertEquals(80, NaviampVisualizer.RaymarchedSphereLiquid.spec(VisualizerRenderTier.Full).maxRaymarchSteps)
        for (tier in VisualizerRenderTier.entries) assertEquals(1f, NaviampVisualizer.LyricMirrorTunnel.spec(tier).renderScale)
    }
}
