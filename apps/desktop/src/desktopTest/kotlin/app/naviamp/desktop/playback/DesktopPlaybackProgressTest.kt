package app.naviamp.desktop

import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.desktop.playback.PlaybackSource
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode
import app.naviamp.domain.settings.PreviousButtonBehavior
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopPlaybackProgressTest {
    @Test
    fun playbackPositionSaveRequiresCurrentTrackAndThresholdMovement() {
        val queue = PlaybackQueue(tracks = listOf(track("one")), currentIndex = 0)

        assertEquals(false, shouldSavePlaybackPosition(queue, positionSeconds = null, lastSavedPositionSeconds = null))
        assertEquals(false, shouldSavePlaybackPosition(PlaybackQueue(), positionSeconds = 10.0, lastSavedPositionSeconds = null))
        assertEquals(true, shouldSavePlaybackPosition(queue, positionSeconds = 10.0, lastSavedPositionSeconds = null))
        assertEquals(
            false,
            shouldSavePlaybackPosition(
                queue = queue,
                positionSeconds = 12.0,
                lastSavedPositionSeconds = 10.0,
                saveThresholdSeconds = 5.0,
            ),
        )
        assertEquals(
            true,
            shouldSavePlaybackPosition(
                queue = queue,
                positionSeconds = 15.0,
                lastSavedPositionSeconds = 10.0,
                saveThresholdSeconds = 5.0,
            ),
        )
    }

    @Test
    fun previousButtonCanRestartCurrentTrackWhenConfigured() {
        val queue = PlaybackQueue(tracks = listOf(track("one")), currentIndex = 0)

        assertEquals(
            true,
            canUsePreviousButton(
                queue = queue,
                previousButtonBehavior = PreviousButtonBehavior.RestartThenPrevious,
                positionSeconds = 12.0,
                restartThresholdSeconds = 10.0,
            ),
        )
        assertEquals(
            false,
            canUsePreviousButton(
                queue = queue,
                previousButtonBehavior = PreviousButtonBehavior.AlwaysPrevious,
                positionSeconds = 12.0,
                restartThresholdSeconds = 10.0,
            ),
        )
    }

    @Test
    fun nextButtonCanWrapWhenQueueRepeatIsActive() {
        val queue = PlaybackQueue(tracks = listOf(track("one")), currentIndex = 0)

        assertEquals(false, canUseNextButton(queue, RepeatMode.Off))
        assertEquals(true, canUseNextButton(queue, RepeatMode.Queue))
    }

    @Test
    fun desktopProviderStreamSeekRequiresReplayWhenCoreTranscodedRuleApplies() {
        assertEquals(true, shouldReplayCurrentForSeek(PlaybackSource.ProviderStream))
        assertEquals(true, shouldReplayCurrentForSeek(PlaybackSource.ProviderStreamCacheDisabled))
        assertEquals(false, shouldReplayCurrentForSeek(PlaybackSource.CachedFile))
    }

    private fun track(id: String): Track =
        Track(
            id = TrackId(id),
            title = "Track $id",
            artistName = "Artist",
            albumTitle = "Album",
            durationSeconds = 180,
            coverArtId = null,
            audioInfo = null,
            replayGain = null,
        )
}
