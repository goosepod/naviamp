package app.naviamp.ui

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo

class NativeMetalShaderTranslatorTest {
    @Test
    fun translatesEveryNativeVisualizerShaderToMetalContract() {
        val nativeVisualizers = NaviampVisualizer.entries.filter { it.nativeShaderDefinition != null }
        assertTrue(nativeVisualizers.isNotEmpty())

        nativeVisualizers.forEach { visualizer ->
            val shaderDefinition = assertNotNull(visualizer.nativeShaderDefinition)
            val source = shaderDefinition.fragmentSourceForDialect(NativeShaderDialect.MetalShadingLanguage)

            assertTrue(source.contains("#include <metal_stdlib>"), visualizer.name)
            assertTrue(source.contains("fragment float4 visualizerFragment"), visualizer.name)
            assertTrue(source.contains("constant NaviampVisualizerUniforms& u [[buffer(0)]]"), visualizer.name)
            assertTrue(source.contains("texture2d<float> u_frequencyTexture [[texture(0)]]"), visualizer.name)
            assertTrue(source.contains("texture2d<float> u_albumArtTexture [[texture(1)]]"), visualizer.name)
            if (shaderDefinition.fragmentSource.contains("texture(u_frequencyTexture")) {
                assertTrue(source.contains("u_frequencyTexture.sample(textureSampler,"), visualizer.name)
            }
            assertFalse(source.contains("#version 300 es"), visualizer.name)
            assertFalse(source.contains("uniform "), visualizer.name)
            assertFalse(source.contains("gl_FragCoord"), visualizer.name)
            assertFalse(source.contains("outColor"), visualizer.name)
            assertFalse(Regex("""[=\s(]texture\s*\(""").containsMatchIn(source), visualizer.name)
        }
    }

    @Test
    fun translatesEveryNativeVisualizerShaderToDesktopGlslContract() {
        val nativeVisualizers = NaviampVisualizer.entries.filter { it.nativeShaderDefinition != null }
        assertTrue(nativeVisualizers.isNotEmpty())

        nativeVisualizers.forEach { visualizer ->
            val shaderDefinition = assertNotNull(visualizer.nativeShaderDefinition)
            val source = shaderDefinition.fragmentSourceForDialect(NativeShaderDialect.DesktopGlsl330)

            assertTrue(source.startsWith("#version 330 core"), visualizer.name)
            assertTrue(source.contains("out vec4 outColor"), visualizer.name)
            assertFalse(source.contains("#version 300 es"), visualizer.name)
            assertFalse(source.contains("precision highp float"), visualizer.name)
        }
    }

    @Test
    fun keepsDesktopNativeMetalBackendOptInAndMacOnly() {
        assertFalse(
            jvmNativeMetalVisualizerAvailable(
                visualizer = NaviampVisualizer.FluidicNebulae,
                osName = "Mac OS X",
                enabledProperty = null,
                libraryAvailable = true,
            ),
        )
        assertFalse(
            jvmNativeMetalVisualizerAvailable(
                visualizer = NaviampVisualizer.FluidicNebulae,
                osName = "Linux",
                enabledProperty = "true",
                libraryAvailable = true,
            ),
        )
        assertFalse(
            jvmNativeMetalVisualizerAvailable(
                visualizer = NaviampVisualizer.FluidicNebulae,
                osName = "Mac OS X",
                enabledProperty = "true",
                libraryAvailable = false,
            ),
        )
        assertTrue(
            jvmNativeMetalVisualizerAvailable(
                visualizer = NaviampVisualizer.FluidicNebulae,
                osName = "Mac OS X",
                enabledProperty = "true",
                libraryAvailable = true,
            ),
        )
    }

    @Test
    fun nativeMetalAvailabilityFeedsRendererSelection() {
        assertEquals(
            VisualizerRendererMode.NativeGpu,
            selectedVisualizerRendererMode(
                visualizer = NaviampVisualizer.FluidicNebulae,
                nativeRendererAvailable = jvmNativeMetalVisualizerAvailable(
                    visualizer = NaviampVisualizer.FluidicNebulae,
                    osName = "Mac OS X",
                    enabledProperty = "true",
                    libraryAvailable = true,
                ),
            ),
        )
        assertEquals(
            VisualizerRendererMode.SkiaRuntimeShader,
            selectedVisualizerRendererMode(
                visualizer = NaviampVisualizer.AudioSphere,
                nativeRendererAvailable = jvmNativeMetalVisualizerAvailable(
                    visualizer = NaviampVisualizer.AudioSphere,
                    osName = "Mac OS X",
                    enabledProperty = "true",
                    libraryAvailable = true,
                ),
            ),
        )
    }

    @Test
    fun nativeOpenGlAvailabilityFeedsRendererSelectionOnWindows() {
        assertFalse(
            jvmNativeOpenGlVisualizerAvailable(
                visualizer = NaviampVisualizer.FluidicNebulae,
                osName = "Windows 11",
                enabledProperty = null,
                libraryAvailable = true,
            ),
        )
        assertFalse(
            jvmNativeOpenGlVisualizerAvailable(
                visualizer = NaviampVisualizer.FluidicNebulae,
                osName = "Mac OS X",
                enabledProperty = "true",
                libraryAvailable = true,
            ),
        )
        assertTrue(
            jvmNativeOpenGlVisualizerAvailable(
                visualizer = NaviampVisualizer.FluidicNebulae,
                osName = "Windows 11",
                enabledProperty = "true",
                libraryAvailable = true,
            ),
        )
        assertEquals(
            VisualizerRendererMode.NativeGpu,
            selectedVisualizerRendererMode(
                visualizer = NaviampVisualizer.FluidicNebulae,
                nativeRendererAvailable = jvmNativeVisualizerAvailable(
                    visualizer = NaviampVisualizer.FluidicNebulae,
                    osName = "Windows 11",
                    windowsOpenGlEnabledProperty = "true",
                    openGlLibraryAvailable = true,
                ),
            ),
        )
    }

    @Test
    fun translatedMetalShadersCompileWhenToolchainTestIsEnabled() {
        if (!System.getProperty("naviamp.visualizer.metalCompilerTest").equals("true", ignoreCase = true)) {
            return
        }

        val outputDir = Files.createTempDirectory("naviamp-metal-shaders")
        NaviampVisualizer.entries
            .mapNotNull { visualizer -> visualizer.nativeShaderDefinition }
            .forEach { shaderDefinition ->
                val shaderName = shaderDefinition.canonicalName
                    .replace(Regex("""[^A-Za-z0-9._-]"""), "_")
                    .replace(".glsl", ".metal")
                val sourceFile = outputDir.resolve(shaderName)
                val outputFile = outputDir.resolve(shaderName.replace(".metal", ".air"))
                Files.writeString(
                    sourceFile,
                    shaderDefinition.fragmentSourceForDialect(NativeShaderDialect.MetalShadingLanguage),
                )

                val process = ProcessBuilder(
                    "xcrun",
                    "-sdk",
                    "macosx",
                    "metal",
                    "-c",
                    sourceFile.toString(),
                    "-o",
                    outputFile.toString(),
                )
                    .redirectErrorStream(true)
                    .start()
                val output = process.inputStream.bufferedReader().readText()
                val exitCode = process.waitFor()

                assertEquals(
                    0,
                    exitCode,
                    "${shaderDefinition.visualizer.name} failed Metal compilation:\n$output",
                )
                assertTrue(Files.isRegularFile(outputFile), "${shaderDefinition.visualizer.name} did not produce AIR output")
            }
    }

    @Test
    fun nativeMetalHostRendersPixelsWhenNativeTestIsEnabled() {
        val nativeLibraryDir = System.getProperty("naviamp.visualizer.metal.dir")
            ?.takeIf { it.isNotBlank() }
        if (!System.getProperty("naviamp.visualizer.nativeMetalHostTest").equals("true", ignoreCase = true) ||
            nativeLibraryDir == null
        ) {
            return
        }

        NaviampVisualizer.entries
            .filter { it.nativeShaderDefinition != null }
            .forEach { visualizer ->
                val host = NativeMetalVisualizerHost(
                    visualizer = visualizer,
                    renderPolicy = visualizerRenderPolicy(visualizer, VisualizerRenderTier.Constrained),
                )
                try {
                    val image = assertNotNull(
                        host.renderImage(
                            width = 96,
                            height = 64,
                            bands = List(32) { index -> (index % 8) / 8f },
                            active = true,
                            visualizerColors = NaviampPlayerColors.fallback(NaviampColors.Dark),
                            colors = NaviampColors.Dark,
                            timeSeconds = 1.25f,
                            tempoBpm = 120,
                        ),
                        visualizer.name,
                    )
                    assertTrue(image.hasVisibleVariation(), "${visualizer.name} native Metal renderer returned a flat image")
                    image.close()
                } finally {
                    host.close()
                }
            }
    }

    private fun Image.hasVisibleVariation(): Boolean {
        val imageInfo = ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.OPAQUE)
        val bitmap = Bitmap()
        return try {
            assertTrue(bitmap.allocPixels(imageInfo))
            assertTrue(readPixels(bitmap))
            val pixels = bitmap.readPixels(imageInfo, width * 4, 0, 0) ?: return false
            val firstBlue = pixels[0]
            val firstGreen = pixels[1]
            val firstRed = pixels[2]
            var changedPixels = 0
            var visiblePixels = 0
            var index = 0
            while (index <= pixels.size - 4) {
                val blue = pixels[index]
                val green = pixels[index + 1]
                val red = pixels[index + 2]
                if ((blue.toInt() and 0xff) > 4 || (green.toInt() and 0xff) > 4 || (red.toInt() and 0xff) > 4) {
                    visiblePixels += 1
                }
                if (blue != firstBlue || green != firstGreen || red != firstRed) {
                    changedPixels += 1
                }
                index += 4
            }
            visiblePixels > width * height / 20 && changedPixels > width * height / 20
        } finally {
            bitmap.close()
        }
    }
}
