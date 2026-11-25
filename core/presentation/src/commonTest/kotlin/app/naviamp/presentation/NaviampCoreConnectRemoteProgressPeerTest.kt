package app.naviamp.presentation

import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.queue.PlaybackQueue
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Two complete Core compositions: the controller's remote timeline must start from fresh target positions. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NaviampCoreConnectRemoteProgressPeerTest {
    private val track = Track(id = TrackId("track"), title = "Track", artistName = "Artist", albumTitle = "Album",
        durationSeconds = 300, coverArtId = null, audioInfo = null, replayGain = null)

    @Test
    fun midTrackAttachSeekAndNormalProgressReachTheControllerCorrectly() = runTest {
        val network = ConnectTestNetwork()
        val phone = connectPeer("phone", "mac", network, configure = { it.copy(monotonicMillis = { testScheduler.currentTime }) })
        val mac = connectPeer("mac", "phone", network, configure = { it.copy(monotonicMillis = { testScheduler.currentTime }) })
        try {
            runCurrent()
            // The target is two minutes into a track it started long before any controller attached.
            mac.updateLivePlayback {
                it.copy(currentTrack = track, queue = PlaybackQueue(listOf(track), 0),
                    progress = PlaybackProgress(0.0, 300.0), playbackState = PlaybackState.Playing)
            }
            runCurrent()
            for (second in 1..120) mac.updateLivePlayback { it.copy(progress = PlaybackProgress(second.toDouble(), 300.0)) }
            runCurrent()

            phone.actions.shell.connectActions!!.onTrustedDeviceSelected(phone.state.value.shell.connect.trustedDevices.single())
            runCurrent()
            val attached = assertNotNull(phone.state.value.shell.connect.remoteNowPlaying, "controller adopts the playing target")
            assertEquals(120.0, attached.positionSeconds)

            val reported = mutableListOf<Double?>()
            backgroundScope.launch {
                phone.state.map { it.shell.connect.remoteNowPlaying?.positionSeconds }.distinctUntilChanged().collect { reported += it }
            }
            runCurrent()

            // Normal progress is not republished.
            for (second in 121..125) { advanceTimeBy(1_000); mac.updateLivePlayback { it.copy(progress = PlaybackProgress(second.toDouble(), 300.0)) }; runCurrent() }
            advanceTimeBy(1_000); runCurrent()
            assertEquals(listOf<Double?>(120.0), reported)

            // A seek on the TV (no command from this controller) publishes exactly once.
            mac.updateLivePlayback { it.copy(progress = PlaybackProgress(30.0, 300.0)) }
            mac.updateLivePlayback { it.copy(progress = PlaybackProgress(30.25, 300.0)) }
            mac.updateLivePlayback { it.copy(progress = PlaybackProgress(30.5, 300.0)) }
            advanceTimeBy(1_000); runCurrent()
            assertEquals(listOf<Double?>(120.0, 30.5), reported)
            mac.updateLivePlayback { it.copy(progress = PlaybackProgress(31.0, 300.0)) }
            advanceTimeBy(1_000); runCurrent()
            assertEquals(listOf<Double?>(120.0, 30.5), reported)
        } finally { phone.close(); mac.close(); runCurrent() }
    }
}
