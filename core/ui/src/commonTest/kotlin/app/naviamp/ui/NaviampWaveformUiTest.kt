package app.naviamp.ui

import app.naviamp.domain.settings.DefaultWaveformBucketCount
import app.naviamp.domain.settings.MaxWaveformBucketCount
import app.naviamp.domain.settings.MinWaveformBucketCount
import kotlin.test.Test
import kotlin.test.assertEquals

class NaviampWaveformUiTest {
    @Test
    fun rendersEverySampleAtEachSupportedWaveformResolution() {
        val supportedResolutions = listOf(
            MinWaveformBucketCount,
            DefaultWaveformBucketCount,
            250,
            320,
            400,
            MaxWaveformBucketCount,
        )

        supportedResolutions.forEach { resolution ->
            assertEquals(resolution, waveformVisibleBarCount(amplitudeCount = resolution))
        }
    }

    @Test
    fun rejectsNegativeSampleCounts() {
        assertEquals(0, waveformVisibleBarCount(amplitudeCount = -1))
    }
}
