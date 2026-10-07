package app.naviamp.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NaviampGpuVisualizerTest {
    @Test fun unavailableAndFailedSurfacesFallBackButBusyDoesNotQueueOrFail() {
        val session = NaviampGpuPresentationSession()
        assertTrue(session.receive(NaviampGpuSubmission.NotReady, 0))
        assertTrue(session.receive(NaviampGpuSubmission.NotReady, 1_999_999_999))
        assertFalse(session.receive(NaviampGpuSubmission.NotReady, 2_000_000_000))
        val ready = NaviampGpuPresentationSession()
        assertTrue(ready.receive(NaviampGpuSubmission.Accepted, 0))
        assertTrue(ready.receive(NaviampGpuSubmission.Busy, 3_000_000_000))
        assertFalse(ready.receive(NaviampGpuSubmission.Failed, 3_000_000_001))
    }
    @Test fun malformedInputCannotProduceNonfiniteGpuUniforms() {
        val assembler = NaviampGpuFrameAssembler()
        val palette = NaviampPlayerColors.fallback(NaviampColors.Dark)
        val frame = assembler.prepare(1, 1, listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f),
            true, 0f, 120, palette, NaviampColors.Dark)
        assertTrue(frame.bands.all { it.isFinite() && it in 0f..1f })
        assertTrue((3..9).all { Float.fromBits(frame.uniforms[it]).isFinite() })
        assertFailsWith<IllegalArgumentException> {
            assembler.prepare(0, 1, emptyList(), false, 0f, null, palette, NaviampColors.Dark)
        }
    }
    @Test fun deadlinesPreserveSixtyAndFortyFiveWithoutAccumulatingMissedFrames() {
        for (fps in listOf(60, 45)) {
            val pacer = NaviampVisualizerPacer(fps)
            var frames = 0
            for (millis in 0L until 10_000L) {
                val now = millis * 1_000_000
                if (pacer.due(now)) { frames++; pacer.submitted(now) }
            }
            assertEquals(fps * 10, frames)
            pacer.submitted(20_000_000_000)
            assertFalse(pacer.due(20_000_000_001))
            assertTrue(pacer.delayMillis(20_000_000_001) in 16L..23L)
        }
    }
    @Test fun elapsedTimeExcludesHiddenTimeAndAvoidsUptimePrecisionLoss() {
        val clock = NaviampVisualizerElapsedTime()
        val uptime = 9_000_000_000_000_000L
        clock.resume(uptime)
        assertEquals(.016f, clock.seconds(uptime + 16_000_000))
        clock.pause(uptime + 1_000_000_000)
        assertEquals(1f, clock.seconds(uptime + 100_000_000_000))
        clock.resume(uptime + 100_000_000_000)
        assertEquals(1.016f, clock.seconds(uptime + 100_016_000_000))
    }
    @Test fun frameCopiesInputAndHasTheSameMetalAbiAcrossHosts() {
        val source = mutableListOf(.5f, .8f)
        val assembler = NaviampGpuFrameAssembler()
        val frame = assembler.prepare(640, 640, source, true, 1.25f, 120,
            NaviampPlayerColors.fallback(NaviampColors.Dark), NaviampColors.Dark)
        source[0] = 0f
        assembler.prepare(358, 358, emptyList(), false, 2f, 60,
            NaviampPlayerColors.fallback(NaviampColors.Dark), NaviampColors.Dark)
        assertEquals(32, frame.bands.size)
        assertEquals(39, frame.uniforms.size)
        assertEquals(640f, Float.fromBits(frame.uniforms[1]))
        assertEquals(1.25f, Float.fromBits(frame.uniforms[0]))
        assertTrue(frame.bands[0] > 0f)
    }
    @Test fun directEffectsKeepCanonicalSourcesAndDoNotSilentlySelectOtherEffects() {
        for (visualizer in listOf(NaviampVisualizer.AnalogSignalFailure, NaviampVisualizer.OceanOfInk)) {
            val shader = assertNotNull(naviampGpuVisualizerShader(visualizer))
            assertEquals(visualizer.nativeShaderDefinition!!.fragmentSource, shader.glsl)
            assertTrue(shader.metal.contains("packed_float4 idle;"))
        }
        val sphere = assertNotNull(naviampGpuVisualizerShader(NaviampVisualizer.AudioSphere))
        assertTrue(sphere.metal.contains("atan2(uv.y, uv.x)"))
        assertTrue(sphere.metal.contains("spherePalette(u, "))
        assertNull(naviampGpuVisualizerShader(NaviampVisualizer.AudioTunnel))
    }
}
