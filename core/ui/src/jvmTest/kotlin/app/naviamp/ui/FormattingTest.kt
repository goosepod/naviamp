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

    @Test
    fun commonNumericFormattingKeepsExpectedPrecisionAndSigns() {
        assertEquals("1.0 KB", 1024L.bytesLabel())
        assertEquals("1.5 GB", (1536L * 1024L * 1024L).storageBytesLabel())
        assertEquals("12.3", 12.34.oneDecimalLabel())
        assertEquals("+2.5", 2.5f.signedOneDecimalLabel())
        assertEquals("-3.0", (-3f).signedOneDecimalLabel())
        assertEquals("+4", 4.signedLabel())
    }
}
