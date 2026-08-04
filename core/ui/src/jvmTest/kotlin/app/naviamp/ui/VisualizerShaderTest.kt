package app.naviamp.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.jetbrains.skia.RuntimeEffect

class VisualizerShaderTest {
    @Test
    fun compilesEveryJvmVisualizerShader() {
        NaviampVisualizer.entries.forEach { visualizer ->
            RuntimeEffect.makeForShader(visualizer.shaderSource).close()
        }
    }

    @Test
    fun nativeVisualizerFallbacksRemainDistinctCompiledCoreShaders() {
        val translated = NaviampVisualizer.entries.filter { it.usesTranslatedNativeSkiaShader }
        assertEquals(5, translated.size)
        assertEquals(translated.size, translated.map { it.shaderSource }.distinct().size)
        translated.forEach { visualizer ->
            assertFalse(visualizer.shaderSource.contains("u_frequencyTexture"), visualizer.name)
            RuntimeEffect.makeForShader(visualizer.shaderSource).close()
        }
    }
}
