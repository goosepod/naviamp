package app.naviamp.android

import android.content.Intent
import android.os.Bundle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import app.naviamp.presentation.*
import app.naviamp.ui.*
import kotlinx.coroutines.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.test.*

/** Real BASS completion and Android HOME lifecycle; an empty disposable fixture profile only. */
class AndroidTvRadioContinuationInstrumentedTest {
    @Test fun sonicQueueContinuesAtNaturalEndInForegroundAndBackground() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("tvLocalFixture") == "true")
        ActivityScenario.launch(MainActivity::class.java).use { runBlocking {
            val core = prepareTvFixturePlayback(startPlayback = false)
            val bridge = core.externalPlaybackBridge()
            try {
                assertTrue(core.state.value.shell.library.songs.tracks.size >= 40,
                    "Requires --sonic --tracks 60 --track-seconds 30 --silent")
                withContext(Dispatchers.Main) {
                    core.execute(NaviampCoreCommand.Settings.ChangePlayback(core.state.value.shell.playback.settings.copy(
                        sonicSimilarityEnabled = true, sonicAutoplayEnabled = true,
                        radioMinimumTracks = 10, sonicAutoplayMinimumTracks = 10,
                        gaplessEnabled = false, crossfadeDurationSeconds = 0), false))
                    core.execute(NaviampCoreCommand.Library.TrackAction(SharedTrackRowActionRequest(
                        core.state.value.shell.library.songs.tracks.first(), SharedTrackRowAction.StartRadio)))
                }
                await("eleven-track Radio queue playing") {
                    bridge.snapshot().let { it.state == NaviampExternalPlaybackState.Playing && it.queue.size == 11 }
                }
                for (background in listOf(false, true)) {
                    val before = assertNotNull(core.state.value.shell.nowPlaying?.queueSnapshot)
                    val prefix = before.tracks.map { it.id }
                    val lastIndex = prefix.lastIndex
                    while (bridge.snapshot().currentQueueIndex < lastIndex) {
                        val index = bridge.snapshot().currentQueueIndex
                        withContext(Dispatchers.Main) { bridge.next() }
                        await("next index ${index + 1}") {
                            bridge.snapshot().let { it.currentQueueIndex == index + 1 &&
                                it.state == NaviampExternalPlaybackState.Playing }
                        }
                    }
                    assertEquals(prefix.size, bridge.snapshot().queue.size, "No proactive top-up is expected")
                    if (background) {
                        val context = InstrumentationRegistry.getInstrumentation().targetContext
                        context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        await("launcher foreground") {
                            InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow
                                ?.packageName?.toString()?.let { it != context.packageName } == true
                        }
                    }
                    withContext(Dispatchers.Main) { bridge.seekTo(23_000) }
                    await("native near-end seek") { (bridge.snapshot().positionMillis ?: 0) >= 23_000 }
                    record("${if (background) "background" else "foreground"}: waiting for native EOF, queue=${prefix.size}")
                    await("ten unique additions playing after native EOF") {
                        bridge.snapshot().let { it.queue.size == prefix.size + 10 &&
                            it.currentQueueIndex == prefix.size && it.state == NaviampExternalPlaybackState.Playing }
                    }
                    val after = assertNotNull(core.state.value.shell.nowPlaying?.queueSnapshot)
                    assertEquals(prefix, after.tracks.take(prefix.size).map { it.id })
                    assertEquals(after.tracks.size, after.tracks.map { it.id }.distinct().size)
                    val initialPosition = bridge.snapshot().positionMillis ?: error("No native position")
                    await("new native track advances") {
                        (bridge.snapshot().positionMillis ?: 0) >= initialPosition + 1_000
                    }
                    record("${if (background) "background" else "foreground"}: ${prefix.size} -> ${after.tracks.size}, " +
                        "index=${bridge.snapshot().currentQueueIndex}, original prefix retained; all IDs unique; native clock advances")
                }
            } finally {
                withContext(Dispatchers.Main) { bridge.pause() }
            }
        } }
    }

    private suspend fun await(label: String, predicate: () -> Boolean) {
        val completed = withTimeoutOrNull(45_000) {
            while (!predicate()) delay(100)
            true
        } ?: false
        assertTrue(completed, "Timed out: $label")
    }

    private fun record(message: String) {
        InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
            putString("stream", "\nTV Radio: $message\n")
        })
    }
}
