package app.naviamp.app

import app.naviamp.domain.*
import app.naviamp.domain.playback.*
import app.naviamp.domain.provider.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class NaviampListenReportingTest {
    @Test fun reconnectingMidListenDoesNotStartASecondReportingProtocol() = runTest {
        val offline = Provider().apply { this.offline = true }
        val online = Provider(timeline = true)
        val reporter = NaviampListenReporting()
        reporter.observe(offline, track, PlaybackState.Playing, PlaybackProgress(0.0, 60.0), 1234)
        reporter.observe(online, track, PlaybackState.Playing, PlaybackProgress(35.0, 60.0), 36_234)
        assertEquals(listOf(1234L), online.listens)
        assertTrue(online.events.isEmpty())
    }

    private class Provider(timeline: Boolean = false) : MediaProvider by RecordingProvider(false) {
        override val capabilities = ProviderCapabilities(false, false, false, false, false,
            supportsPlayReporting = true, supportsPlaybackTimeline = timeline, supportsListenSubmission = true)
        val events = mutableListOf<String>()
        val listens = mutableListOf<Long>()
        var offline = false
        override suspend fun reportNowPlaying(trackId: TrackId) { if (offline) error("offline"); events += "now" }
        override suspend fun reportPlaybackState(trackId: TrackId, state: PlaybackReportState, positionSeconds: Double?) {
            if (offline) error("offline")
            events += state.providerValue
        }
        override suspend fun submitListen(trackId: TrackId, startedAtEpochMillis: Long) {
            if (offline) error("offline")
            listens += startedAtEpochMillis
        }
    }
    private val track = Track(TrackId("song"), "Song", artistName = "Artist", albumTitle = null,
        durationSeconds = 60, coverArtId = null, audioInfo = null, replayGain = null)

    @Test fun qualifyingListensAreSubmittedOnceAndRepeatedPlaysAreDistinct() = runTest {
        val p = Provider()
        val reporter = NaviampListenReporting()
        for (start in listOf(1_000L, 101_000L)) {
            reporter.observe(p, track, PlaybackState.Playing, PlaybackProgress(0.0, 60.0), start)
            reporter.observe(p, track, PlaybackState.Playing, PlaybackProgress(30.0, 60.0), start + 30_000)
            reporter.observe(p, track, PlaybackState.Playing, PlaybackProgress(45.0, 60.0), start + 45_000)
            reporter.observe(p, track, PlaybackState.Finished, PlaybackProgress(60.0, 60.0), start + 60_000)
        }
        assertEquals(listOf(1_000L, 101_000L), p.listens)
    }

    @Test fun seeksAndPausedTimeDoNotQualifyAsListening() = runTest {
        val p = Provider()
        val reporter = NaviampListenReporting()
        reporter.observe(p, track, PlaybackState.Playing, PlaybackProgress(0.0, 60.0), 0)
        reporter.observe(p, track, PlaybackState.Paused, PlaybackProgress(50.0, 60.0), 1_000)
        reporter.observe(p, track, PlaybackState.Stopped, PlaybackProgress(50.0, 60.0), 200_000)
        assertTrue(p.listens.isEmpty())
    }

    @Test fun timelineSessionsAreOrderedAndNotDoubleScrobbled() = runTest {
        val p = Provider(timeline = true)
        val reporter = NaviampListenReporting()
        reporter.observe(p, track, PlaybackState.Loading, PlaybackProgress(0.0, 60.0), 0)
        reporter.observe(p, track, PlaybackState.Playing, PlaybackProgress(0.0, 60.0), 100)
        reporter.observe(p, track, PlaybackState.Playing, PlaybackProgress(40.0, 60.0), 40_100)
        reporter.observe(p, track, PlaybackState.Stopped, PlaybackProgress(40.0, 60.0), 41_100)
        assertEquals(listOf("starting", "playing", "playing", "stopped"), p.events)
        assertTrue(p.listens.isEmpty())
    }

    @Test fun offlineListensPersistWithOriginalTimestampAndReplayOnReconnect() = runTest {
        val repository = RecordingPendingActions()
        val p = Provider(timeline = true).apply { offline = true }
        val actions = NaviampProviderActionController(repository)
        val offlineProvider = actions.offlineCapable(p, "source")
        val reporter = NaviampListenReporting()
        reporter.observe(offlineProvider, track, PlaybackState.Playing, PlaybackProgress(0.0, 60.0), 1234)
        reporter.observe(offlineProvider, track, PlaybackState.Playing, PlaybackProgress(35.0, 60.0), 36_234)
        val pending = repository.pendingProviderActions("source").single()
        assertEquals(PendingActionSubmitListen, pending.actionType)
        assertEquals(1234L, pending.longValue)
        p.offline = false
        actions.replay("source", p)
        assertEquals(listOf(1234L), p.listens)
        assertTrue(repository.pendingProviderActions("source").isEmpty())
    }
}
