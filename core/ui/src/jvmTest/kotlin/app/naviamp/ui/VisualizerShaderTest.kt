package app.naviamp.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.math.abs
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

class VisualizerShaderTest {
    @Test
    fun nativeFrequencyTextureFallbackInterpolatesBetweenTexelCentersAndClampsEdges() {
        val source = NativeSkiaShaderTranslator.translateFragmentShader("""
            #version 300 es
            uniform sampler2D u_frequencyTexture;
            out vec4 outColor;
            void main() {
                vec2 pixel = gl_FragCoord.xy;
                float signal = texture(u_frequencyTexture, vec2((pixel.x - 2.5) / 128.0, 0.5)).r;
                outColor = vec4(signal, signal, signal, 1.0);
            }
        """.trimIndent())
        // Alternating extremes expose gaps/steps that a smooth test signal can hide.
        val bands = FloatArray(32) { (it % 2).toFloat() }
        val pixels = renderBands(source, bands, 133)
        pixels.forEachIndexed { x, pixel ->
            val position = (((x - 2f) / 128f) * 32f - .5f).coerceIn(0f, 31f)
            val left = position.toInt()
            val fraction = position - left
            val expected = ((bands[left] * (1f - fraction) + bands[(left + 1).coerceAtMost(31)] * fraction) * 255).toInt()
            assertTrue(abs((pixel and 255) - expected) <= 1, "linear sample at $x")
        }
    }

    @Test
    fun sphereEnvelopeKeepsItsBodyAndRimAndFadesOutsideTheContour() {
        RuntimeEffect.makeForShader(NaviampVisualizer.AudioSphere.shaderSource).use { effect ->
            RuntimeShaderBuilder(effect).use { builder ->
                builder.uniform("iResolution", 96f, 96f)
                builder.uniform("iActive", 1f)
                builder.uniform("iBands", FloatArray(32))
                builder.uniform("iEnergy", 0f, 0f, 0f, 0f)
                for (name in listOf("iAccent", "iColorA", "iColorB", "iColorC", "iReadable")) {
                    builder.uniform(name, 1f, 1f, 1f, 1f)
                }
                builder.makeShader().use { shader ->
                    Bitmap().use { bitmap ->
                        assertTrue(bitmap.allocN32Pixels(96, 96))
                        Paint().use { paint ->
                            paint.shader = shader
                            Canvas(bitmap).use { it.drawRect(Rect.makeWH(96f, 96f), paint) }
                        }
                        assertTrue(bitmap.getColor(48, 48) ushr 24 > 127, "sphere body must remain visible")
                        assertTrue(bitmap.getColor(82, 48) ushr 24 > 127, "sphere rim must remain visible")
                        assertEquals(0, bitmap.getColor(0, 0) ushr 24, "outside the sphere must be transparent")
                    }
                }
            }
        }
    }

    @Test
    fun bandLookupRendersEveryBandAndClampsBothIndicesAndAmplitudes() {
        val source = CommonShaderHeader + """
            half4 main(float2 coord) {
                float band = bandAtIndex(int(floor(coord.x)) - 2);
                return half4(band, band, band, 1.0);
            }
        """
        val bands = FloatArray(32) { ((it * 13) % 32) / 31f }
        bands[0] = -.5f
        bands[31] = 1.5f
        val pixels = renderBands(source, bands, 36)
        repeat(pixels.size) { x ->
            val expected = (bands[(x - 2).coerceIn(0, 31)].coerceIn(0f, 1f) * 255).toInt()
            val actual = pixels[x]
            assertEquals(255, actual ushr 24, "alpha at $x")
            assertTrue(abs((actual and 255) - expected) <= 1, "band at $x")
        }
    }

    private fun renderBands(source: String, bands: FloatArray, width: Int): IntArray {
        return RuntimeEffect.makeForShader(source).use { effect ->
            RuntimeShaderBuilder(effect).use { builder ->
                builder.uniform("iBands", bands)
                builder.makeShader().use { shader ->
                    Bitmap().use { bitmap ->
                        assertTrue(bitmap.allocN32Pixels(width, 1))
                        Paint().use { paint ->
                            paint.shader = shader
                            Canvas(bitmap).use { canvas -> canvas.drawRect(Rect.makeWH(width.toFloat(), 1f), paint) }
                        }
                        IntArray(width) { bitmap.getColor(it, 0) }
                    }
                }
            }
        }
    }

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
