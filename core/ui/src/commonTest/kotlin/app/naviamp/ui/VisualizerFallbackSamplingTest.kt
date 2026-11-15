package app.naviamp.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class VisualizerFallbackSamplingTest {
    @Test
    fun preservesEndpointsAndClampsFallbackAmplitudes() {
        assertEquals(listOf(0f, 0.5f, 1f), sampleVisualizerFallbackBands(listOf(-1f, 0.5f, 2f), 3))
        assertEquals(listOf(0.1f, 0.9f), sampleVisualizerFallbackBands(listOf(0.1f, 0.3f, 0.6f, 0.9f), 2))
    }

    @Test
    fun handlesEmptyAndSingleBandInputs() {
        assertEquals(emptyList(), sampleVisualizerFallbackBands(emptyList(), 0))
        assertEquals(listOf(0.3f), sampleVisualizerFallbackBands(listOf(0.3f, 0.9f), 1))
        assertEquals(listOf(0.4f, 0.4f, 0.4f), sampleVisualizerFallbackBands(listOf(0.4f), 3))
    }
}
