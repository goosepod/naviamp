package app.naviamp.presentation

import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.playback.PlaybackProfile
import app.naviamp.domain.playback.PlaybackProfileTarget
import app.naviamp.domain.playback.PlaybackProfileTargetType
import app.naviamp.domain.playback.PlaybackReplayGainMode
import app.naviamp.domain.playback.PlaybackTransitionMode
import app.naviamp.domain.playback.ReplayGainMode
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.PlaybackQueueGroup
import app.naviamp.domain.settings.PlaybackSettings
import kotlin.test.Test
import kotlin.test.assertEquals

class NaviampCorePlaybackProfileDiagnosticsTest {
    private val global = PlaybackSettings(
        gaplessEnabled = false,
        crossfadeDurationSeconds = 8,
        replayGainMode = ReplayGainMode.Track,
    )

    @Test
    fun reportsCustomProfileAndResolvedValuesWithinAlbumGroup() {
        val rows = diagnosticMap(
            PlaybackQueue(
                tracks = listOf(track("one"), track("two"), track("three")),
                currentIndex = 0,
                groups = listOf(albumGroup(end = 2)),
            ),
        )

        assertEquals("true", rows["Custom profile active"])
        assertEquals("Charlotte de Witte", rows["Queue group"])
        assertEquals("Album: album-id", rows["Profile target"])
        assertEquals("Gapless", rows["Transition override"])
        assertEquals("Off", rows["ReplayGain override"])
        assertEquals(ReplayGainMode.Off.displayName, rows["Resolved ReplayGain"])
        assertEquals("Custom group profile", rows["Next transition source"])
        assertEquals("Gapless", rows["Next transition"])
    }

    @Test
    fun reportsGlobalTransitionWhenLeavingCustomAlbumGroup() {
        val rows = diagnosticMap(
            PlaybackQueue(
                tracks = listOf(track("one"), track("two"), track("three")),
                currentIndex = 1,
                groups = listOf(albumGroup(end = 2)),
            ),
        )

        assertEquals("true", rows["Custom profile active"])
        assertEquals("Global at group boundary", rows["Next transition source"])
        assertEquals("Crossfade 8s", rows["Next transition"])
    }

    @Test
    fun reportsGlobalSettingsForIndependentlyQueuedTrack() {
        val rows = diagnosticMap(
            PlaybackQueue(
                tracks = listOf(track("one"), track("two")),
                currentIndex = 0,
            ),
        )

        assertEquals("false", rows["Custom profile active"])
        assertEquals("None", rows["Queue group"])
        assertEquals("None", rows["Profile target"])
        assertEquals("Global player settings", rows["Next transition source"])
        assertEquals("Crossfade 8s", rows["Next transition"])
    }

    private fun diagnosticMap(queue: PlaybackQueue): Map<String, String> =
        playbackProfileDiagnosticRows(queue, global).toMap()
}

private fun albumGroup(end: Int) = PlaybackQueueGroup(
    id = "album-group",
    target = PlaybackProfileTarget(PlaybackProfileTargetType.Album, "album-id"),
    label = "Charlotte de Witte",
    startIndex = 0,
    endIndexExclusive = end,
    profile = PlaybackProfile(
        transitionMode = PlaybackTransitionMode.Gapless,
        replayGainMode = PlaybackReplayGainMode.Off,
    ),
)

private fun track(id: String) = Track(
    id = TrackId(id),
    title = id,
    artistName = "Charlotte de Witte",
    albumTitle = "Charlotte de Witte",
    durationSeconds = null,
    coverArtId = null,
    audioInfo = null,
    replayGain = null,
)
