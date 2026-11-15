package app.naviamp.ui

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VisualizerHistoryBufferTest {
    private val policy = visualizerRenderPolicy(NaviampVisualizer.SpectralRidge, VisualizerRenderTier.Constrained)

    @Test
    fun onlyPixelHistoryPresetsRequestNearestSampling() {
        assertEquals(setOf(NaviampVisualizer.PixelRidge, NaviampVisualizer.PixelMountain),
            NaviampVisualizer.entries.filter { it.historyUsesNearestSampling }.toSet())
    }

    @Test
    fun onlyHistoryVisualizersRecordActiveFrames() {
        val bands = FloatArray(VisualizerFrameBandCount) { 1f }
        val history = VisualizerHistoryBuffer(NaviampVisualizer.SpectralRidge, policy)
        assertFalse(history.update(1f, false, bands))
        assertTrue(history.update(1f, true, bands))
        assertFalse(VisualizerHistoryBuffer(NaviampVisualizer.SpectrumBars, policy).update(1f, true, bands))
    }

    @Test
    fun retainsFirstFrameThenRequiresCadenceAndMeaningfulChange() {
        val history = VisualizerHistoryBuffer(NaviampVisualizer.SpectralRidge, policy)
        assertTrue(history.update(0f, true, FloatArray(VisualizerFrameBandCount)))
        assertFalse(history.update(policy.historyIntervalSeconds / 2, true, FloatArray(VisualizerFrameBandCount) { 1f }))
        assertFalse(history.update(1f, true, FloatArray(VisualizerFrameBandCount) { 0.005f }))
        assertTrue(history.update(2f, true, FloatArray(VisualizerFrameBandCount) { 0.007f }))
        assertFalse(history.update(3f, true, FloatArray(VisualizerFrameBandCount) { 0.007f }))
    }

    @Test
    fun newRowsAgeAndPreserveEarlierSamples() {
        val history = VisualizerHistoryBuffer(NaviampVisualizer.SpectralRidge, policy)
        listOf(0.25f, 0.5f, 1f).forEachIndexed { index, value ->
            assertTrue(history.update(index.toFloat(), true, FloatArray(VisualizerFrameBandCount) { value }))
        }
        val argb = IntArray(VisualizerHistoryColumns * VisualizerHistoryRows)
        history.copyArgbTo(argb)
        assertEquals(0xffffffff.toInt(), argb[0])
        assertEquals(0xff7f7f7f.toInt(), argb[VisualizerHistoryColumns])
        assertEquals(0xff3f3f3f.toInt(), argb[VisualizerHistoryColumns * 2])
        assertEquals(0, argb[VisualizerHistoryColumns * 3])
    }

    @Test
    fun nativeArgbAndRgbaEncodingsDescribeIdenticalClampedPixels() {
        val history = VisualizerHistoryBuffer(NaviampVisualizer.PixelMountain, policy)
        val bands = FloatArray(VisualizerFrameBandCount) { index -> when (index) { 0 -> -1f; 1 -> 2f; else -> 0.5f } }
        assertTrue(history.update(0f, true, bands))
        val argb = IntArray(VisualizerHistoryColumns * VisualizerHistoryRows)
        val rgba = ByteArray(argb.size * 4)
        history.copyArgbTo(argb)
        history.copyRgbaTo(rgba)
        assertEquals(0xff000000.toInt(), argb[0])
        assertEquals(0xffffffff.toInt(), argb[1])
        assertContentEquals(byteArrayOf(127, 127, 127, 255.toByte()), rgba.copyOfRange(8, 12))
        argb.forEachIndexed { index, pixel ->
            val offset = index * 4
            val decoded = ((rgba[offset + 3].toInt() and 255) shl 24) or
                ((rgba[offset].toInt() and 255) shl 16) or ((rgba[offset + 1].toInt() and 255) shl 8) or
                (rgba[offset + 2].toInt() and 255)
            assertEquals(pixel, decoded)
        }
    }

    @Test
    fun cachedNativeRgbaEncodingTracksLaterRowsAndKeepsPriorHistory() {
        val history = VisualizerHistoryBuffer(NaviampVisualizer.SpectralRidge, policy)
        val rgba = ByteArray(VisualizerHistoryColumns * VisualizerHistoryRows * 4)
        history.update(0f, true, FloatArray(VisualizerFrameBandCount) { 0.25f })
        history.copyRgbaTo(rgba)
        assertContentEquals(byteArrayOf(63, 63, 63, 255.toByte()), rgba.copyOfRange(0, 4))
        history.update(1f, true, FloatArray(VisualizerFrameBandCount) { 1f })
        history.copyRgbaTo(rgba)
        assertContentEquals(byteArrayOf(-1, -1, -1, -1), rgba.copyOfRange(0, 4))
        val secondRow = VisualizerHistoryColumns * 4
        assertContentEquals(byteArrayOf(63, 63, 63, 255.toByte()), rgba.copyOfRange(secondRow, secondRow + 4))
    }
}
