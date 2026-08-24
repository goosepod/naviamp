package app.naviamp.domain.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StereoDownmixTest {
    @Test
    fun monoAndStereoDoNotNeedCustomMatrices() {
        val mono = planStereoDownmix(1)
        val stereo = planStereoDownmix(2)

        assertFalse(mono.isDownmixed)
        assertFalse(stereo.isDownmixed)
        assertEquals(StereoOutputChannels, mono.outputChannels)
        assertEquals("Mono source expanded by stereo mixer", mono.diagnosticLabel)
        assertEquals("Stereo passthrough", stereo.diagnosticLabel)
    }

    @Test
    fun commonMultichannelLayoutsProducePeakSafeStereoMatrices() {
        (3..8).forEach { channels ->
            val plan = planStereoDownmix(channels)
            val matrix = requireNotNull(plan.matrix)

            assertEquals(channels, matrix.inputChannels)
            assertEquals(2, matrix.outputChannels)
            assertTrue(matrix.absoluteRowSum(0) <= 1.000001f)
            assertTrue(matrix.absoluteRowSum(1) <= 1.000001f)
            assertTrue(plan.diagnosticLabel.startsWith("Stereo downmix"))
        }
    }

    @Test
    fun fivePointOneRoutesFrontCenterLfeAndSurroundsIntentionally() {
        val matrix = requireNotNull(planStereoDownmix(6).matrix)

        assertTrue(matrix[0, 0] > 0f)
        assertEquals(0f, matrix[0, 1])
        assertEquals(0f, matrix[1, 0])
        assertTrue(matrix[1, 1] > 0f)
        assertEquals(matrix[0, 2], matrix[1, 2])
        assertEquals(matrix[0, 3], matrix[1, 3])
        assertTrue(matrix[0, 2] > matrix[0, 3])
        assertTrue(matrix[0, 4] > 0f)
        assertEquals(0f, matrix[0, 5])
        assertEquals(0f, matrix[1, 4])
        assertTrue(matrix[1, 5] > 0f)
    }

    @Test
    fun sevenPointOneKeepsRearAndSidePairsOnTheirRespectiveSides() {
        val matrix = requireNotNull(planStereoDownmix(8).matrix)

        assertTrue(matrix[0, 4] > 0f)
        assertEquals(0f, matrix[1, 4])
        assertTrue(matrix[1, 5] > 0f)
        assertEquals(0f, matrix[0, 5])
        assertTrue(matrix[0, 6] > 0f)
        assertEquals(0f, matrix[1, 6])
        assertTrue(matrix[1, 7] > 0f)
        assertEquals(0f, matrix[0, 7])
    }

    @Test
    fun unknownLayoutsUseAVisibleSafeFallbackWithoutDroppingChannels() {
        val plan = planStereoDownmix(10)
        val matrix = requireNotNull(plan.matrix)

        assertEquals("10-channel (unknown layout)", plan.sourceLayoutLabel)
        (0 until 10).forEach { input ->
            assertTrue(matrix[0, input] > 0f || matrix[1, input] > 0f)
        }
        assertTrue(matrix.absoluteRowSum(0) <= 1.000001f)
        assertTrue(matrix.absoluteRowSum(1) <= 1.000001f)
    }

    @Test
    fun mixingMatrixRejectsIncorrectDimensions() {
        val result = runCatching {
            AudioMixingMatrix(outputChannels = 2, inputChannels = 3, coefficients = listOf(1f))
        }

        assertTrue(result.isFailure)
    }
}
