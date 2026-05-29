package app.naviamp.desktop

import app.naviamp.desktop.playback.PlaybackSource
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopPlaybackProgressTest {
    @Test
    fun desktopProviderStreamSeekRequiresReplayWhenCoreTranscodedRuleApplies() {
        assertEquals(true, shouldReplayCurrentForSeek(PlaybackSource.ProviderStream))
        assertEquals(true, shouldReplayCurrentForSeek(PlaybackSource.ProviderStreamCacheDisabled))
        assertEquals(false, shouldReplayCurrentForSeek(PlaybackSource.CachedFile))
    }
}
