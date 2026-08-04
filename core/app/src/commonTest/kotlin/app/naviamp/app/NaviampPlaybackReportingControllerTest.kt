package app.naviamp.app

import app.naviamp.domain.TrackId
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.provider.PlaybackReportState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFailsWith

class NaviampPlaybackReportingControllerTest {
    @Test
    fun initialNowPlayingEligibilityIsShared() {
        val controller = NaviampPlaybackReportingController()
        val trackId = TrackId("track")

        assertEquals(
            trackId,
            controller.nowPlayingReport(
                NaviampNowPlayingReportRequest(
                    trackId = trackId,
                    isInternetRadioTrack = false,
                    supportsPlayReporting = true,
                ),
            )?.trackId,
        )
        assertNull(
            controller.nowPlayingReport(
                NaviampNowPlayingReportRequest(
                    trackId = trackId,
                    isInternetRadioTrack = false,
                    supportsPlayReporting = false,
                ),
            ),
        )
        assertNull(
            controller.nowPlayingReport(
                NaviampNowPlayingReportRequest(
                    trackId = trackId,
                    isInternetRadioTrack = true,
                    supportsPlayReporting = true,
                ),
            ),
        )
    }

    @Test
    fun reportsStateChangesAndThrottlesRepeatedPlayingUpdates() {
        val controller = NaviampPlaybackReportingController()

        assertEquals(PlaybackReportState.Playing, controller.stateReport(request(now = 1_000L))?.state)
        assertNull(controller.stateReport(request(now = 2_000L)))
        assertEquals(PlaybackReportState.Playing, controller.stateReport(request(now = 16_000L))?.state)
        assertEquals(
            PlaybackReportState.Paused,
            controller.stateReport(request(now = 16_001L, state = PlaybackState.Paused))?.state,
        )
    }

    @Test
    fun reportsImmediatelyForNewSessionAndRejectsUnsupportedTargets() {
        val controller = NaviampPlaybackReportingController()

        assertEquals(PlaybackReportState.Playing, controller.stateReport(request(now = 1_000L))?.state)
        assertEquals(PlaybackReportState.Playing, controller.stateReport(request(now = 2_000L, sessionId = 2L))?.state)
        assertNull(controller.stateReport(request(now = 20_000L, supported = false)))
        assertNull(controller.stateReport(request(now = 20_000L, internetRadio = true)))
    }

    @Test
    fun mapsEveryReportablePlaybackStateAndCarriesTheCurrentPosition() {
        val controller = NaviampPlaybackReportingController()
        val states = listOf(
            PlaybackState.Loading to PlaybackReportState.Starting,
            PlaybackState.Playing to PlaybackReportState.Playing,
            PlaybackState.Paused to PlaybackReportState.Paused,
            PlaybackState.Stopped to PlaybackReportState.Stopped,
            PlaybackState.Finished to PlaybackReportState.Stopped,
            PlaybackState.Error("failed") to PlaybackReportState.Stopped,
        )

        states.forEachIndexed { index, (playbackState, expected) ->
            val report = controller.stateReport(
                request(
                    now = index.toLong() + 1,
                    sessionId = index.toLong() + 1,
                    state = playbackState,
                ),
            )
            assertEquals(expected, report?.state)
            assertEquals(12.0, report?.positionSeconds)
        }
        assertNull(controller.stateReport(request(now = 100L, sessionId = 100L, state = PlaybackState.Idle)))
    }

    @Test
    fun heartbeatRunsSharedEligibilityAndPreservesCancellation() = runTest {
        var reports = 0
        assertFailsWith<CancellationException> {
            runNaviampNowPlayingHeartbeat(
                request = NaviampNowPlayingHeartbeatRequest(
                    trackId = TrackId("track"),
                    isInternetRadioTrack = false,
                    supportsPlayReporting = true,
                    playbackState = PlaybackState.Playing,
                ),
                report = { reports += 1 },
                waitForNext = { throw CancellationException("stop") },
            )
        }
        assertEquals(1, reports)

        runNaviampNowPlayingHeartbeat(
            request = NaviampNowPlayingHeartbeatRequest(
                trackId = TrackId("track"),
                isInternetRadioTrack = false,
                supportsPlayReporting = false,
                playbackState = PlaybackState.Playing,
            ),
            report = { reports += 1 },
        )
        assertEquals(1, reports)
    }

    private fun request(
        now: Long,
        sessionId: Long = 1L,
        state: PlaybackState = PlaybackState.Playing,
        supported: Boolean = true,
        internetRadio: Boolean = false,
    ) = NaviampPlaybackStateReportRequest(
        sessionId = sessionId,
        trackId = TrackId("track"),
        isInternetRadioTrack = internetRadio,
        supportsPlayReporting = supported,
        playbackState = state,
        progress = PlaybackProgress(positionSeconds = 12.0, durationSeconds = 180.0),
        nowEpochMillis = now,
    )
}
