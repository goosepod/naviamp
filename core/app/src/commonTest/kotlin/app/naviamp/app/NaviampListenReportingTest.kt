package app.naviamp.app

import app.naviamp.domain.*
import app.naviamp.domain.playback.*
import app.naviamp.domain.provider.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class NaviampListenReportingTest {
    @Test fun interruptionTimeDoesNotQualifyAndReconnectRetriesOnlyOneListen() = runTest {
        val provider = Provider().apply { offline = true }
        val reporting = NaviampListenReporting()
        reporting.observe(provider, track, PlaybackState.Playing, PlaybackProgress(0.0, 60.0), 1_000)
        reporting.observe(provider, track, PlaybackState.Paused, PlaybackProgress(10.0, 60.0), 11_000)
        reporting.observe(provider, track, PlaybackState.Playing, PlaybackProgress(10.0, 60.0), 111_000)
        assertTrue(provider.listens.isEmpty())
        reporting.observe(provider, track, PlaybackState.Playing, PlaybackProgress(35.0, 60.0), 136_000)
        provider.offline = false
        reporting.observe(provider, track, PlaybackState.Playing, PlaybackProgress(40.0, 60.0), 141_000)
        reporting.observe(provider, track, PlaybackState.Playing, PlaybackProgress(50.0, 60.0), 151_000)
        assertEquals(listOf(1_000L), provider.listens)
    }

    @Test fun reconnectingMidListenDoesNotStartASecondReportingProtocol() = runTest {
        val offline = Provider().apply { this.offline = true }
        val online = Provider(timeline = true)
        val reporter = NaviampListenReporting()
        reporter.observe(offline, track, PlaybackState.Playing, PlaybackProgress(0.0, 60.0), 1234)
        reporter.observe(online, track, PlaybackState.Playing, PlaybackProgress(35.0, 60.0), 36_234)
        assertEquals(listOf(1234L), online.listens)
        assertEquals(listOf("now"), online.events)
        assertTrue(online.timelineAttempts.isEmpty())
    }

    private class Provider(
        timeline: Boolean = false,
        namespace: String = "test",
    ) : MediaProvider by RecordingProvider(false) {
        override val cacheNamespace = namespace
        override val capabilities = ProviderCapabilities(false, false, false, false, false,
            supportsPlayReporting = true, supportsPlaybackTimeline = timeline, supportsListenSubmission = true)
        val events = mutableListOf<String>()
        val listens = mutableListOf<Long>()
        val timelineAttempts = mutableListOf<PlaybackReportState>()
        var timelineFailure: PlaybackReportState? = null
        var timelineFailureMessage = "timeline unavailable"
        var presenceFails = false
        var offline = false
        override suspend fun reportNowPlaying(trackId: TrackId) { if (offline || presenceFails) error("unavailable"); events += "now" }
        override suspend fun reportLegacyNowPlaying(trackId: TrackId) = reportNowPlaying(trackId)
        override suspend fun reportPlaybackState(trackId: TrackId, state: PlaybackReportState, positionSeconds: Double?) {
            timelineAttempts += state
            if (state == timelineFailure) error(timelineFailureMessage)
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

    @Test fun failedTimelineStartFallsBackOnlyWhilePlayingAndSubmitsOneListen() = runTest {
        val p = Provider(timeline = true).apply { timelineFailure = PlaybackReportState.Starting }
        val reporter = NaviampListenReporting()
        reporter.observe(p, track, PlaybackState.Loading, PlaybackProgress(0.0, 60.0), 1_000)
        assertTrue(p.events.isEmpty())
        reporter.observe(p, track, PlaybackState.Playing, PlaybackProgress(0.0, 60.0), 2_000)
        assertEquals(listOf("now"), p.events)
        reporter.observe(p, track, PlaybackState.Paused, PlaybackProgress(10.0, 60.0), 12_000)
        reporter.observe(p, track, PlaybackState.Playing, PlaybackProgress(10.0, 60.0), 22_000)
        reporter.observe(p, track, PlaybackState.Playing, PlaybackProgress(35.0, 60.0), 47_000)
        reporter.observe(p, track, PlaybackState.Stopped, PlaybackProgress(35.0, 60.0), 48_000)
        assertEquals(listOf("now", "now", "now"), p.events)
        assertEquals(listOf(PlaybackReportState.Starting), p.timelineAttempts)
        assertEquals(listOf(1_000L), p.listens)
    }

    @Test fun failedPlayingReportFallsBackInTheSameObservation() = runTest {
        val p = Provider(timeline = true).apply { timelineFailure = PlaybackReportState.Playing }
        val reporter = NaviampListenReporting()
        reporter.observe(p, track, PlaybackState.Playing, PlaybackProgress(0.0, 60.0), 0)
        assertEquals(listOf("starting", "now"), p.events)
        reporter.observe(p, track, PlaybackState.Playing, PlaybackProgress(10.0, 60.0), 10_000)
        assertEquals(listOf("starting", "now", "now"), p.events)
        assertEquals(listOf(PlaybackReportState.Starting, PlaybackReportState.Playing), p.timelineAttempts)
        assertTrue(p.listens.isEmpty())
    }

    @Test fun fallbackPresenceRetriesAfterFailureWithoutRestartingTheTimeline() = runTest {
        val p = Provider(timeline = true).apply {
            timelineFailure = PlaybackReportState.Starting
            presenceFails = true
        }
        val reporter = NaviampListenReporting()
        reporter.observe(p, track, PlaybackState.Playing, PlaybackProgress(0.0, 60.0), 0)
        p.presenceFails = false
        reporter.observe(p, track, PlaybackState.Playing, PlaybackProgress(10.0, 60.0), 10_000)
        assertEquals(listOf("now"), p.events)
        assertEquals(listOf(PlaybackReportState.Starting), p.timelineAttempts)
        assertEquals("Legacy fallback", reporter.diagnostics().toMap()["Listen reporting mode"])
        assertTrue(reporter.diagnostics().toMap().getValue("Last listen reporting failure").contains("unavailable"))
    }

    @Test fun reportingDiagnosticsRedactCredentialParameters() = runTest {
        val p = Provider(timeline = true).apply {
            timelineFailure = PlaybackReportState.Starting
            timelineFailureMessage = "request failed?u=demo&t=secret&s=salt&password=hunter2"
        }
        val reporter = NaviampListenReporting()
        reporter.observe(p, track, PlaybackState.Loading, PlaybackProgress(0.0, 60.0), 0)

        val failure = reporter.diagnostics().toMap().getValue("Last listen reporting failure")
        assertFalse("secret" in failure)
        assertFalse("hunter2" in failure)
        assertTrue("<redacted>" in failure)
    }

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

    @Test fun stopObservationCanQualifyWithoutUsingSeekedOrPausedTime() = runTest {
        val p = Provider()
        val reporter = NaviampListenReporting()
        reporter.observe(p, track, PlaybackState.Playing, PlaybackProgress(0.0, 60.0), 1_000)
        reporter.observe(p, track, PlaybackState.Stopped, PlaybackProgress(30.0, 60.0), 31_000)

        assertEquals(listOf(1_000L), p.listens)
    }

    @Test fun sourceChangesDoNotCombineListeningAcrossSessions() = runTest {
        val first = Provider(namespace = "first")
        val second = Provider(namespace = "second")
        val reporter = NaviampListenReporting()
        reporter.observe(first, track, PlaybackState.Playing, PlaybackProgress(0.0, 60.0), 0)
        reporter.observe(first, track, PlaybackState.Playing, PlaybackProgress(20.0, 60.0), 20_000)
        reporter.observe(second, track, PlaybackState.Playing, PlaybackProgress(0.0, 60.0), 21_000)
        reporter.observe(second, track, PlaybackState.Playing, PlaybackProgress(20.0, 60.0), 41_000)

        assertTrue(first.listens.isEmpty())
        assertTrue(second.listens.isEmpty())
    }

    @Test fun gaplessTrackTransitionKeepsQualifiedListensDistinct() = runTest {
        val p = Provider()
        val reporter = NaviampListenReporting()
        val next = track.copy(id = TrackId("next"))
        reporter.observe(p, track, PlaybackState.Playing, PlaybackProgress(0.0, 60.0), 1_000)
        reporter.observe(p, track, PlaybackState.Playing, PlaybackProgress(30.0, 60.0), 31_000)
        reporter.observe(p, next, PlaybackState.Playing, PlaybackProgress(0.0, 60.0), 32_000)
        reporter.observe(p, next, PlaybackState.Playing, PlaybackProgress(30.0, 60.0), 62_000)

        assertEquals(listOf(1_000L, 32_000L), p.listens)
    }

    @Test fun timelinePresenceAndExplicitSubmissionProduceOneQualifiedScrobble() = runTest {
        val p = Provider(timeline = true)
        val reporter = NaviampListenReporting()
        reporter.observe(p, track, PlaybackState.Loading, PlaybackProgress(0.0, 60.0), 0)
        reporter.observe(p, track, PlaybackState.Playing, PlaybackProgress(0.0, 60.0), 100)
        reporter.observe(p, track, PlaybackState.Playing, PlaybackProgress(40.0, 60.0), 40_100)
        reporter.observe(p, track, PlaybackState.Stopped, PlaybackProgress(40.0, 60.0), 41_100)
        assertEquals(listOf("starting", "playing", "playing", "stopped"), p.events)
        assertEquals(listOf(0L), p.listens)
    }

    @Test fun throttledPresenceDoesNotThrottleLocalListenAccounting() = runTest {
        val p = Provider(timeline = true)
        val reporter = NaviampListenReporting()
        reporter.observe(p, track, PlaybackState.Playing, PlaybackProgress(0.0, 60.0), 1_000,
            presenceState = PlaybackReportState.Playing)
        reporter.observe(p, track, PlaybackState.Playing, PlaybackProgress(10.0, 60.0), 11_000,
            presenceState = null)
        reporter.observe(p, track, PlaybackState.Playing, PlaybackProgress(30.0, 60.0), 31_000,
            presenceState = null)

        assertEquals(listOf(1_000L), p.listens)
        assertEquals(listOf(PlaybackReportState.Starting, PlaybackReportState.Playing), p.timelineAttempts)
    }

    @Test fun shortTrackBoundaryIsExplicit() = runTest {
        val reporter = NaviampListenReporting()
        val p = Provider()
        val tooShort = track.copy(id = TrackId("short"), durationSeconds = 29)
        reporter.observe(p, tooShort, PlaybackState.Playing, PlaybackProgress(0.0, 29.0), 0)
        reporter.observe(p, tooShort, PlaybackState.Finished, PlaybackProgress(29.0, 29.0), 29_000)
        val eligible = track.copy(id = TrackId("boundary"), durationSeconds = 30)
        reporter.observe(p, eligible, PlaybackState.Playing, PlaybackProgress(0.0, 30.0), 40_000)
        reporter.observe(p, eligible, PlaybackState.Playing, PlaybackProgress(15.0, 30.0), 55_000)

        assertEquals(listOf(40_000L), p.listens)
    }

    @Test fun bufferingAndRestoredPositionDoNotCountAsListening() = runTest {
        val p = Provider()
        val reporter = NaviampListenReporting()
        reporter.observe(p, track, PlaybackState.Loading, PlaybackProgress(45.0, 60.0), 0)
        reporter.observe(p, track, PlaybackState.Loading, PlaybackProgress(45.0, 60.0), 100_000)
        reporter.observe(p, track, PlaybackState.Playing, PlaybackProgress(45.0, 60.0), 101_000)
        reporter.observe(p, track, PlaybackState.Stopped, PlaybackProgress(50.0, 60.0), 106_000)

        assertTrue(p.listens.isEmpty())
    }

    @Test fun offlineListensPersistWithOriginalTimestampAndReplayOnReconnect() = runTest {
        val repository = RecordingPendingActions()
        val p = Provider(timeline = true).apply { offline = true }
        val actions = NaviampProviderActionController(repository)
        val offlineProvider = actions.offlineCapable(p, "source")
        val reporter = NaviampListenReporting()
        reporter.observe(offlineProvider, track, PlaybackState.Playing, PlaybackProgress(0.0, 60.0), 1234)
        reporter.observe(offlineProvider, track, PlaybackState.Stopped, PlaybackProgress(35.0, 60.0), 36_234)
        val pending = repository.pendingProviderActions("source").single()
        assertEquals(PendingActionSubmitListen, pending.actionType)
        assertEquals(1234L, pending.longValue)
        p.offline = false
        val restartedActions = NaviampProviderActionController(repository)
        restartedActions.replay("source", p)
        assertEquals(listOf(1234L), p.listens)
        assertTrue(repository.pendingProviderActions("source").isEmpty())
    }
}
