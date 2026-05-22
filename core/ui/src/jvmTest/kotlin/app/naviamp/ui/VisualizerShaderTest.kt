package app.naviamp.ui

import kotlin.test.Test
import org.jetbrains.skia.RuntimeEffect

class VisualizerShaderTest {
    @Test
    fun compilesEveryJvmVisualizerShader() {
        NaviampVisualizer.entries.forEach { visualizer ->
            RuntimeEffect.makeForShader(visualizer.shaderSource).close()
        }
    }
}
