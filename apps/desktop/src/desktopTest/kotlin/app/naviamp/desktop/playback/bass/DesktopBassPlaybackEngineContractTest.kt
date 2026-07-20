package app.naviamp.desktop.playback.bass

import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackRequest
import app.naviamp.domain.playback.PlaybackState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopBassPlaybackEngineContractTest {
    @Test
    fun stopPublishesStoppedStateAndUnknownProgress() = runTest {
        val engine = DesktopBassPlaybackEngine(
            backendResult = Result.failure(IllegalStateException("BASS unavailable")),
        )
        val states = mutableListOf<PlaybackState>()
        val progress = mutableListOf<PlaybackProgress>()
        engine.play(
            scope = this,
            request = PlaybackRequest(url = "file:///track.flac", mediaId = "track-1"),
            onStateChanged = states::add,
            onProgressChanged = progress::add,
        )
        states.clear()
        progress.clear()

        engine.stop()

        assertEquals(listOf<PlaybackState>(PlaybackState.Stopped), states)
        assertEquals(listOf(PlaybackProgress.Unknown), progress)
    }
}
