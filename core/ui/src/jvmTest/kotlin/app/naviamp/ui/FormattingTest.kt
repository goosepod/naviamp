package app.naviamp.ui

import app.naviamp.domain.playback.PlaybackProgress
import kotlin.test.Test
import kotlin.test.assertEquals

class FormattingTest {
    @Test
    fun playbackProgressLabelUsesSharedTimeFallbacks() {
        assertEquals(
            "1:05 / 3:20",
            PlaybackProgress(positionSeconds = 65.9, durationSeconds = null)
                .label(effectiveDurationSeconds = 200.1),
        )
        assertEquals(
            "--:-- / --:--",
            PlaybackProgress.Unknown.label(effectiveDurationSeconds = null),
        )
    }

    @Test
    fun playbackProgressPositionLabelUsesUnknownFallback() {
        assertEquals("--:--", PlaybackProgress.Unknown.positionLabel())
        assertEquals("2:03", PlaybackProgress(positionSeconds = 123.4, durationSeconds = null).positionLabel())
    }

    @Test
    fun playbackProgressFractionUsesSharedWaveformRules() {
        assertEquals(
            0.25,
            PlaybackProgress(positionSeconds = 25.0, durationSeconds = null).fraction(effectiveDurationSeconds = 100.0),
        )
        assertEquals(
            1.0,
            PlaybackProgress(positionSeconds = 250.0, durationSeconds = null).fraction(effectiveDurationSeconds = 100.0),
        )
    }
}

