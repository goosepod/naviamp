package app.naviamp.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NaviampTelevisionScreenProtectionTest {
    @Test
    fun shortListeningSessionsKeepNormalBrightnessAndPosition() {
        for (time in listOf(-1L, 0L, TelevisionScreenProtectionDelayMillis - 1)) {
            assertEquals(TelevisionScreenProtection(), televisionScreenProtection(time))
        }
    }

    @Test
    fun prolongedListeningDimsInTwoStages() {
        assertEquals(0.55f, televisionScreenProtection(TelevisionScreenProtectionDelayMillis).brightness)
        assertEquals(0.55f, televisionScreenProtection(TelevisionScreenProtectionDeepDimMillis - 1).brightness)
        assertEquals(0.35f, televisionScreenProtection(TelevisionScreenProtectionDeepDimMillis).brightness)
    }

    @Test
    fun shiftCycleMovesBothAxesAndStaysInsideExistingContentMargins() {
        val positions = (0..15).map { minute ->
            televisionScreenProtection(TelevisionScreenProtectionDelayMillis + minute * TelevisionScreenProtectionStepMillis)
                .let { it.offsetXDp to it.offsetYDp }
        }
        assertEquals(8, positions.take(8).distinct().size)
        assertEquals(positions.take(8), positions.drop(8))
        assertTrue(positions.all { (x, y) -> x in -8..8 && y in -8..8 })
        assertTrue(positions.all { it != (0 to 0) })
        assertEquals(0, positions.take(8).sumOf { it.first })
        assertEquals(0, positions.take(8).sumOf { it.second })
    }

    @Test
    fun veryLongSessionsRemainBounded() {
        val state = televisionScreenProtection(Long.MAX_VALUE)
        assertEquals(0.35f, state.brightness)
        assertTrue(state.offsetXDp in -8..8 && state.offsetYDp in -8..8)
    }
}
