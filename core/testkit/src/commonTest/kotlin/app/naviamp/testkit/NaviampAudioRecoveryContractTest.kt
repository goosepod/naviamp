package app.naviamp.testkit

import app.naviamp.domain.playback.*
import app.naviamp.presentation.*
import app.naviamp.ui.NaviampExternalPlaybackLabels
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.Test
import kotlin.test.assertTrue

/** Fast Core/fixture preflight; the native run separately proves OS translation and the real cursor. */
@OptIn(ExperimentalCoroutinesApi::class)
class NaviampAudioRecoveryContractTest {
    @Test fun sharedContractSelectsAndPersistsItsLeaseAndCompletesEveryRecoveryPhase() = runTest {
        for (modern in listOf(false, true)) verifyWithClock(modern, requireDeactivation = true) { verifyNaviampAudioRecovery(it) }
    }

    @Test fun remoteCommandsTraverseTheSessionAndRecoverRealCoreState() = runTest {
        for (modern in listOf(false, true)) verifyWithClock(modern) { verifyNaviampRemoteTransport(it) }
    }

    private suspend fun TestScope.verifyWithClock(modern: Boolean, requireDeactivation: Boolean = false, verify: suspend (NaviampAudioRecoveryHost<Unit>) -> Unit) {
        val clock = ClockEngine { testScheduler.currentTime / 1_000.0 }
        var deactivated = false
        val output = object : NaviampExternalPlaybackSurface<Unit> {
            var closed = false
            lateinit var audio: (NaviampExternalPlaybackAudioEvent) -> Unit
            override fun attach(command: (NaviampExternalPlaybackSurfaceCommand) -> Boolean,
                                audioEvent: (NaviampExternalPlaybackAudioEvent) -> Unit) { audio = audioEvent }
            override fun decodeArtwork(bytes: ByteArray) = null
            override fun publish(snapshot: NaviampExternalPlaybackSnapshot, actions: Set<NaviampExternalPlaybackSurfaceAction>,
                                 artwork: Unit?, labels: NaviampExternalPlaybackLabels) {
                check(labels.actions.getValue(app.naviamp.ui.NaviampExternalPlaybackAction.Favorite).isNotBlank())
            }
            override fun close() { closed = true }
        }
        verify(object : NaviampAudioRecoveryHost<Unit> {
            override val engine = clock
            override val surface = output
            override val usesActivationLifecycle = modern
            override suspend fun writeWave(bytes: ByteArray) = "file:///audio-fixture.wav"
            override fun nativePositionSeconds() = clock.position()
            override suspend fun deactivateAudioSession() {
                deactivated = true
                if (modern) output.audio(NaviampExternalPlaybackAudioEvent.SessionBecameInactive(NaviampAudioSessionDeactivationSource.Application))
            }
            override suspend fun notify(stimulus: NaviampAudioRecoveryStimulus) {
                if (output.closed) return
                val event = when (stimulus) {
                    NaviampAudioRecoveryStimulus.Begin -> if (modern) NaviampExternalPlaybackAudioEvent.SessionBecameInactive(NaviampAudioSessionDeactivationSource.System) else NaviampExternalPlaybackAudioEvent.InterruptionBegan
                    NaviampAudioRecoveryStimulus.EndMayResume -> if (modern) NaviampExternalPlaybackAudioEvent.ResumptionRecommended(NaviampAudioSessionResumptionRecommendation.Resume) else NaviampExternalPlaybackAudioEvent.InterruptionEnded(true)
                    NaviampAudioRecoveryStimulus.EndDenied -> if (modern) NaviampExternalPlaybackAudioEvent.ResumptionRecommended(NaviampAudioSessionResumptionRecommendation.StayPaused) else NaviampExternalPlaybackAudioEvent.InterruptionEnded(false)
                    NaviampAudioRecoveryStimulus.EndMissingOptions -> if (modern) null else NaviampExternalPlaybackAudioEvent.InterruptionEnded(false)
                    NaviampAudioRecoveryStimulus.Disconnect -> NaviampExternalPlaybackAudioEvent.OutputDisconnected
                    NaviampAudioRecoveryStimulus.ApplicationDeactivation -> NaviampExternalPlaybackAudioEvent.SessionBecameInactive(NaviampAudioSessionDeactivationSource.Application)
                    else -> null
                }
                event?.let(output.audio)
            }
        })
        if (requireDeactivation) assertTrue(deactivated)
        assertTrue(output.closed)
    }
}

private class ClockEngine(private val now: () -> Double) : ReleasablePlaybackEngine {
    override val name = "Core contract clock"
    override val supportsPause = true
    override val supportsSeek = true
    override val supportsGapless = false
    override val supportsCrossfade = false
    override val supportsReplayGain = false
    override val supportsSoftwareVolume = true
    override val prefersOriginalStream = true
    private var paused = true
    private var base = 0.0
    private var started = 0.0
    private var job: Job? = null
    private var state: (PlaybackState) -> Unit = {}
    fun position() = base + if (paused) 0.0 else now() - started
    override fun play(scope: CoroutineScope, request: PlaybackRequest, onStateChanged: (PlaybackState) -> Unit,
                      onProgressChanged: (PlaybackProgress) -> Unit, onMetadataChanged: (PlaybackStreamMetadata) -> Unit) {
        job?.cancel(); state = onStateChanged; base = 0.0; started = now(); paused = false
        state(PlaybackState.Playing)
        job = scope.launch { while (isActive) { onProgressChanged(PlaybackProgress(position(), 90.0)); delay(50) } }
    }
    override fun pause() { base = position(); paused = true; state(PlaybackState.Paused) }
    override fun resume() { started = now(); paused = false; state(PlaybackState.Playing) }
    override fun seek(positionSeconds: Double) { base = positionSeconds; started = now() }
    override fun setVolume(percent: Int) = Unit
    override fun stop() { job?.cancel(); job = null; paused = true; state(PlaybackState.Stopped) }
    override fun release() { stop() }
}
