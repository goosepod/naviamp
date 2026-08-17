package app.naviamp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.center
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.right
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class WaveformScrubberUiTest {
    @Test
    fun renderedScrubberSeeksToTheClickedQuarterHalfAndThreeQuarterPositions() = runComposeUiTest {
        val finishedFractions = mutableListOf<Float>()
        setContent {
            ScrubberTestRow(onFinished = finishedFractions::add)
        }

        listOf(0.25f, 0.5f, 0.75f).forEach { fraction ->
            onNodeWithTag(WaveformScrubberTestTag).performTouchInput {
                down(Offset(right * fraction, center.y))
                up()
            }
        }

        runOnIdle {
            assertEquals(3, finishedFractions.size)
            assertEquals(0.25f, finishedFractions[0], absoluteTolerance = 0.01f)
            assertEquals(0.5f, finishedFractions[1], absoluteTolerance = 0.01f)
            assertEquals(0.75f, finishedFractions[2], absoluteTolerance = 0.01f)
        }
    }
}

@Composable
private fun ScrubberTestRow(onFinished: (Float) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.width(320.dp),
    ) {
        Box(Modifier.width(42.dp))
        WaveformScrubber(
            amplitudes = listOf(0.2f, 0.8f, 0.4f),
            value = 0f,
            enabled = true,
            colors = NaviampColors(),
            onValueChange = {},
            onValueChangeFinished = onFinished,
            modifier = Modifier
                .weight(1f)
                .height(28.dp)
                .testTag(WaveformScrubberTestTag),
        )
        Box(Modifier.width(42.dp))
    }
}

private const val WaveformScrubberTestTag = "waveform-scrubber-test"
