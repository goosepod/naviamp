package app.naviamp.android

import android.os.Bundle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import app.naviamp.presentation.NaviampCoreCommand
import app.naviamp.presentation.NaviampExternalPlaybackState
import app.naviamp.presentation.externalPlaybackBridge
import app.naviamp.ui.SharedTrackRowAction
import app.naviamp.ui.SharedTrackRowActionRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.URL
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Opt-in real BASS/network/service recovery checks against an empty disposable phone installation. */
class AndroidPlaybackRecoveryInstrumentedTest {
    @Test
    fun interruptedPlaybackAndRapidFailedSelectionsRecoverWithoutAServiceCrash(): Unit {
        assumeTrue(InstrumentationRegistry.getArguments().getString("playbackRecoveryFixture") == "true")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            runBlocking {
                val core = prepareTvFixturePlayback()
                val bridge = core.externalPlaybackBridge()
                val tracks = core.state.value.shell.library.songs.tracks
                check(tracks.size >= 3) { "Start fixture with --tracks 3" }
                try {
                    withContext(Dispatchers.Main) { bridge.pause() }
                    await { bridge.snapshot().state == NaviampExternalPlaybackState.Paused }
                    withContext(Dispatchers.Main) { bridge.play() }
                    await { bridge.snapshot().state == NaviampExternalPlaybackState.Playing }
                    record("pause/resume completed")

                    fixture("off")
                    await { bridge.snapshot().state == NaviampExternalPlaybackState.Idle }
                    delay(1_500) // Include stable-idle release before restarting against an unavailable provider.
                    repeat(3) { attempt ->
                        withContext(Dispatchers.Main) {
                            core.dispatch(NaviampCoreCommand.Library.TrackAction(
                                SharedTrackRowActionRequest(tracks[attempt], SharedTrackRowAction.Select),
                            ))
                        }
                        delay(100)
                    }
                    await { bridge.snapshot().state == NaviampExternalPlaybackState.Idle }
                    delay(25_000) // Observe beyond the physical device's foreground-start timeout.
                    record("unreachable provider and rapid selections survived the startup deadline")

                    fixture("on")
                    withContext(Dispatchers.Main) {
                        core.dispatch(NaviampCoreCommand.Library.TrackAction(
                            SharedTrackRowActionRequest(tracks[0], SharedTrackRowAction.Select),
                        ))
                    }
                    await { bridge.snapshot().state == NaviampExternalPlaybackState.Playing }
                    val recoveredId = bridge.snapshot().current?.mediaId
                    val before = bridge.snapshot().positionMillis ?: 0L
                    delay(2_000)
                    assertTrue((bridge.snapshot().positionMillis ?: 0L) > before, "Recovered playback is frozen")
                    scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
                    delay(2_000)
                    assertEquals(NaviampExternalPlaybackState.Playing, bridge.snapshot().state)
                    scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
                    assertEquals(recoveredId, bridge.snapshot().current?.mediaId)
                    record("restored transport played with moving progress and survived background/restore")
                    withContext(Dispatchers.Main) { bridge.stop() }
                    await { bridge.snapshot().state == NaviampExternalPlaybackState.Idle }
                } finally {
                    fixture("on")
                    withContext(Dispatchers.Main) { bridge.stop() }
                }
            }
        }
    }

    private suspend fun await(predicate: () -> Boolean) = withTimeout(45_000) {
        while (!predicate()) delay(100)
    }

    private suspend fun fixture(action: String) = withContext(Dispatchers.IO) {
        URL("http://127.0.0.1:18080/_test/$action").openConnection().apply {
            connectTimeout = 3_000
            readTimeout = 3_000
        }.getInputStream().bufferedReader().use { it.readText() }
    }

    private fun record(message: String) {
        InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
            putString("stream", "\nPlayback recovery: $message\n")
        })
    }
}
