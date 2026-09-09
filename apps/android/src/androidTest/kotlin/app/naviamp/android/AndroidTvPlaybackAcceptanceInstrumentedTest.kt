package app.naviamp.android

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import app.naviamp.presentation.*
import app.naviamp.domain.playback.ReplayGainMode
import kotlinx.coroutines.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.URL
import kotlin.test.*

/** Real AudioManager/BASS/MediaSession checks; only an empty disposable TV fixture installation. */
class AndroidTvPlaybackAcceptanceInstrumentedTest {
    @Test fun drainedTransportRetryPreservesPosition() = scenario { core ->
        val before = core.playbackProgress.value.positionSeconds ?: error("No native progress")
        try {
            fixture("off")
            waitFor("stream interruption") { core.externalPlaybackBridge().snapshot().state == NaviampExternalPlaybackState.Idle }
            record("interrupted: ${core.state.value.shell.nowPlaying?.stateLabel}; position before=$before")
            fixture("on")
            withContext(Dispatchers.Main) { core.externalPlaybackBridge().play() }
            waitFor("retry playing") { core.externalPlaybackBridge().snapshot().state == NaviampExternalPlaybackState.Playing }
            waitFor("retry progress") { core.playbackProgress.value.positionSeconds != null }
            val after = core.playbackProgress.value.positionSeconds!!
            record("retry position=$after")
            assertTrue(after >= before - 1.5, "Retry erased cursor: before=$before after=$after")
        } finally { fixture("on") }
    }

    @Test fun transientAndPermanentAudioFocusLossRespectUserIntent() = scenario { core ->
        val manager = InstrumentationRegistry.getInstrumentation().targetContext.getSystemService(AudioManager::class.java)
        suspend fun request(gain: Int): AudioFocusRequest {
            val request = AudioFocusRequest.Builder(gain)
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setOnAudioFocusChangeListener { }.build()
            withContext(Dispatchers.Main) { assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, manager.requestAudioFocus(request)) }
            return request
        }
        suspend fun expect(state: NaviampExternalPlaybackState) = waitFor("focus state $state") {
            core.externalPlaybackBridge().snapshot().state == state
        }
        var competitor: AudioFocusRequest? = null
        try {
            competitor = request(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            delay(1000)
            assertEquals(NaviampExternalPlaybackState.Playing, core.externalPlaybackBridge().snapshot().state)
            manager.abandonAudioFocusRequest(competitor)
            competitor = null
            record("duckable focus loss kept playback active")
            competitor = request(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            expect(NaviampExternalPlaybackState.Paused)
            manager.abandonAudioFocusRequest(competitor)
            competitor = null
            expect(NaviampExternalPlaybackState.Playing)
            record("transient loss/gain resumed")
            competitor = request(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            expect(NaviampExternalPlaybackState.Paused)
            withContext(Dispatchers.Main) { core.externalPlaybackBridge().pause() }
            delay(500)
            manager.abandonAudioFocusRequest(competitor)
            competitor = null
            delay(1500)
            assertEquals(NaviampExternalPlaybackState.Paused, core.externalPlaybackBridge().snapshot().state)
            record("explicit pause suppressed focus auto-resume")
            withContext(Dispatchers.Main) { core.externalPlaybackBridge().play() }
            expect(NaviampExternalPlaybackState.Playing)
            competitor = request(AudioManager.AUDIOFOCUS_GAIN)
            expect(NaviampExternalPlaybackState.Paused)
            manager.abandonAudioFocusRequest(competitor)
            competitor = null
            delay(1500)
            assertEquals(NaviampExternalPlaybackState.Paused, core.externalPlaybackBridge().snapshot().state)
            record("permanent focus loss stayed paused")
        } finally { competitor?.let(manager::abandonAudioFocusRequest) }
    }

    @Test fun sustainedTransitionsAndProviderReports() = scenario { core ->
        val crossfade = InstrumentationRegistry.getArguments().getString("crossfade") == "true"
        withContext(Dispatchers.Main) {
            core.dispatch(NaviampCoreCommand.Settings.ChangePlayback(core.state.value.shell.playback.settings.copy(
                gaplessEnabled = !crossfade, crossfadeDurationSeconds = if (crossfade) 3 else 0,
                replayGainMode = ReplayGainMode.Track,
            ), false))
        }
        waitFor("ReplayGain applied") {
            core.statsForNerdsDiagnostics().sections.flatMap { it.rows }.toMap()["ReplayGain mode"] == "Track"
        }
        val gain = core.statsForNerdsDiagnostics().sections.flatMap { it.rows }.toMap()
        assertEquals("Provider metadata", gain["ReplayGain source"])
        assertTrue(gain["ReplayGain applied"].orEmpty().startsWith("-6.0 dB"))
        record("ReplayGain applied: ${gain["ReplayGain applied"]}")
        val ids = linkedSetOf<String>()
        withTimeout(160_000) {
            while (ids.size < 3) {
                val snapshot = core.externalPlaybackBridge().snapshot()
                if (snapshot.state == NaviampExternalPlaybackState.Playing) snapshot.current?.mediaId?.let {
                    if (ids.add(it)) record("${if (crossfade) "crossfade" else "gapless"} playing $it")
                }
                delay(100)
            }
            while (core.externalPlaybackBridge().snapshot().state != NaviampExternalPlaybackState.Idle) delay(100)
        }
        delay(1500)
        record("fixture reports: ${fixture("status")}")
        assertEquals(3, ids.size)
        val status = org.json.JSONObject(fixture("status"))
        val reports = status.getJSONArray("reports")
        val submitted = (0 until reports.length()).map(reports::getJSONObject)
            .filter { it.getString("submission") == "true" }.map { it.getString("id") }
        assertEquals(setOf("fixture-track", "fixture-track-2", "fixture-track-3"), submitted.toSet())
        assertEquals(3, submitted.size, "Each queue occurrence must submit one listen")
    }

    private fun scenario(block: suspend (NaviampCore) -> Unit) {
        assumeTrue(InstrumentationRegistry.getArguments().getString("tvLocalFixture") == "true")
        ActivityScenario.launch(MainActivity::class.java).use {
            runBlocking { block(prepareTvFixturePlayback()) }
        }
    }
    private suspend fun waitFor(label: String, predicate: () -> Boolean) {
        val completed = withTimeoutOrNull(45_000) { while (!predicate()) delay(100); true } ?: false
        assertTrue(completed, "Timed out: $label")
    }
    private suspend fun fixture(action: String): String = withContext(Dispatchers.IO) {
        URL("http://127.0.0.1:18080/_test/$action").openConnection().apply {
            connectTimeout = 3000; readTimeout = 3000
        }.getInputStream().bufferedReader().use { it.readText() }
    }
    private fun record(message: String) {
        InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply { putString("stream", "\nTV $message\n") })
    }
}
