package app.naviamp.domain.playback

import kotlin.test.*

class BassStreamEndTest {
    @Test fun liveEndsAreRetryableEvenWithoutNativeByteCounters() {
        assertIs<PlaybackState.Error>(bassStreamEndState(null, null, null, null, isLive = true))
        assertIs<PlaybackState.Error>(bassStreamEndState(0, 1000, 1000, 0, isLive = true))
        assertIs<PlaybackState.Error>(bassStreamEndState(1000, 900, 1000, 0, isLive = true))
    }
    @Test fun truncatedFiniteDownloadIsRetryableFailure() {
        assertIs<PlaybackState.Error>(bassStreamEndState(1000, 900, 400, 0))
        assertIs<PlaybackState.Error>(bassStreamEndState(1000, 900, 0, 0))
    }
    @Test fun completeAudioDoesNotRequireTrailingTags() {
        assertEquals(PlaybackState.Finished, bassStreamEndState(1000, 900, 900, 0))
        assertEquals(PlaybackState.Finished, bassStreamEndState(1000, 900, 1000, 0))
    }
    @Test fun unavailableLiveAndStillConnectedSourcesAreNotFalseFailures() {
        assertEquals(PlaybackState.Finished, bassStreamEndState(null, null, null, null))
        assertEquals(PlaybackState.Finished, bassStreamEndState(0, 1000, 100, 0))
        assertEquals(PlaybackState.Finished, bassStreamEndState(1000, 900, 400, 1))
        assertEquals(PlaybackState.Finished, bassStreamEndState(1000, 900, 400, 2))
        assertEquals(PlaybackState.Finished, bassStreamEndState(1000, 900, null, 0))
    }
}
