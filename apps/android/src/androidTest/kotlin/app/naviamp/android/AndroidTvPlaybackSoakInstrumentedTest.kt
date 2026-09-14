package app.naviamp.android

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.os.Debug
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import app.naviamp.domain.playback.PlaybackProfile
import app.naviamp.domain.playback.PlaybackReplayGainMode
import app.naviamp.domain.playback.PlaybackTransitionMode
import app.naviamp.presentation.*
import app.naviamp.ui.*
import java.io.File
import java.net.URL
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.test.*

/** Opt-in real Activity/BASS/OS resource acceptance; use a fresh disposable installation. */
class AndroidTvPlaybackSoakInstrumentedTest {
    @Test fun backgroundProfilesInterruptionsAndResourceGrowth() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("tvLocalFixture") == "true")
        ActivityScenario.launch(MainActivity::class.java).use { activity -> runBlocking {
            val config = fixture("status").getJSONObject("config")
            val count = config.getInt("tracks")
            val albumsCount = config.getInt("albums")
            val seconds = config.getInt("trackSeconds")
            require(count >= 24 && albumsCount >= 6 && count % albumsCount == 0)
            require(seconds in 30..60 && config.getDouble("burstSeconds") <= 4)
            val perAlbum = count / albumsCount
            val core = prepareTvFixturePlayback(startPlayback = false)
            withContext(Dispatchers.Main) {
                core.execute(NaviampCoreCommand.Settings.ChangePlayback(core.state.value.shell.playback.settings.copy(
                    gaplessEnabled = false, crossfadeDurationSeconds = 0,
                    replayGainMode = app.naviamp.domain.playback.ReplayGainMode.Off,
                ), false))
                core.execute(NaviampCoreCommand.Library.ChangeView(NaviampLibraryView.Albums))
                core.execute(NaviampCoreCommand.Library.Refresh)
            }
            await("album catalog") { core.state.value.shell.library.albums.items.size == albumsCount }
            val albums = core.state.value.shell.library.albums.items.associateBy { it.id }
            for (index in 0 until albumsCount) {
                val album = albums.getValue(if (index == 0) "fixture-album" else "fixture-album-${index + 1}")
                val crossfade = index % 2 == 1
                withContext(Dispatchers.Main) {
                    core.execute(NaviampCoreCommand.Detail.Album(NaviampAlbumDetailActionRequest(album,
                        NaviampAlbumDetailCommand.SavePlaybackProfile(PlaybackProfile(
                            transitionMode = if (crossfade) PlaybackTransitionMode.Crossfade else PlaybackTransitionMode.Gapless,
                            crossfadeDurationSeconds = if (crossfade) 3 else null,
                            replayGainMode = if (crossfade) PlaybackReplayGainMode.Album else PlaybackReplayGainMode.Track,
                        )))))
                    core.execute(NaviampCoreCommand.Detail.Album(NaviampAlbumDetailActionRequest(album,
                        if (index == 0) NaviampAlbumDetailCommand.Play(false) else NaviampAlbumDetailCommand.AddToQueue)))
                }
            }
            await("first track playing") { core.externalPlaybackBridge().snapshot().state == NaviampExternalPlaybackState.Playing }
            assertTrue(InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME))
            await("Activity backgrounded") { activity.state == Lifecycle.State.CREATED }
            record("backgrounded; tracks=$count albums=$albumsCount seconds=$seconds")
            val started = SystemClock.elapsedRealtime()
            val seen = linkedSetOf<String>()
            val interrupted = mutableSetOf<Int>()
            val samples = mutableListOf<ResourceSample>()
            var lastProgressAt = started
            var previousProgress: Pair<String?, Long?>? = null
            var lastSampled = 0
            val outageIndices = setOf(perAlbum + 1, 3 * perAlbum + 1, 5 * perAlbum + 1)
            try {
                withTimeout(count * seconds * 1000L + 180_000) {
                    while (true) {
                        val snapshot = core.externalPlaybackBridge().snapshot()
                        val mediaId = snapshot.current?.mediaId
                        val progress = mediaId to snapshot.positionMillis
                        if (progress != previousProgress) { previousProgress = progress; lastProgressAt = SystemClock.elapsedRealtime() }
                        assertTrue(SystemClock.elapsedRealtime() - lastProgressAt < 45_000, "Playback stuck: $progress")
                        if (snapshot.state == NaviampExternalPlaybackState.Playing && mediaId != null) {
                            if (seen.add(mediaId)) {
                                val index = seen.size
                                val expected = if (index == 1) "fixture-track" else "fixture-track-$index"
                                assertTrue(mediaId.endsWith(":$expected"), "Skipped/repeated queue occurrence: $mediaId at $index")
                                record("track=$index id=$mediaId")
                            }
                            val index = seen.size
                            val position = snapshot.positionMillis ?: 0
                            if (position >= 5000 && lastSampled != index) {
                                val crossfade = ((index - 1) / perAlbum) % 2 == 1
                                val rows = core.statsForNerdsDiagnostics().sections.flatMap { it.rows }.toMap()
                                assertEquals(if (crossfade) "Album" else "Track", rows["ReplayGain mode"])
                                assertEquals(if (crossfade && index % perAlbum != 0) "3s" else "Off", rows["Crossfade duration"])
                                assertTrue(rows["ReplayGain applied"].orEmpty().startsWith(if (crossfade) "-3.0 dB" else "-6.0 dB"))
                                val sample = resources(index)
                                samples += sample
                                lastSampled = index
                                record("profile=${if (crossfade) "crossfade/album" else "gapless/track"} resources=$sample")
                            }
                            if (position >= 6000 && index in outageIndices && interrupted.add(index)) {
                                fixture("off")
                                await("buffer exhausted at track $index") {
                                    core.externalPlaybackBridge().snapshot().state == NaviampExternalPlaybackState.Idle
                                }
                                assertEquals("BASS playback failed.", core.state.value.shell.nowPlaying?.stateLabel)
                                fixture("on")
                                withContext(Dispatchers.Main) { core.externalPlaybackBridge().play() }
                                await("explicit retry $index") { core.externalPlaybackBridge().snapshot().state == NaviampExternalPlaybackState.Playing }
                                await("retry progress $index") { (core.playbackProgress.value.positionSeconds ?: -1.0) >= position / 1000.0 - 1.5 }
                                assertEquals(mediaId, core.externalPlaybackBridge().snapshot().current?.mediaId)
                                record("recovered track=$index beforeMillis=$position afterSeconds=${core.playbackProgress.value.positionSeconds}")
                                lastProgressAt = SystemClock.elapsedRealtime()
                            }
                        } else if (snapshot.state == NaviampExternalPlaybackState.Idle) {
                            assertNotEquals("BASS playback failed.", core.state.value.shell.nowPlaying?.stateLabel)
                            // The shared Finished publication may precede the next Loading frame.
                            // Unexpected termination still fails the progress deadline above.
                            if (seen.size == count) break
                        }
                        delay(200)
                    }
                }
                delay(2000)
                assertEquals(outageIndices, interrupted)
                assertEquals(count, samples.size)
                val status = fixture("status")
                val reports = status.getJSONArray("reports")
                val submitted = (0 until reports.length()).map(reports::getJSONObject)
                    .filter { it.getString("submission") == "true" }.map { it.getString("id") }
                val expected = (1..count).map { if (it == 1) "fixture-track" else "fixture-track-$it" }
                assertEquals(expected.sorted(), submitted.sorted(), "Missing or duplicate listen reports")
                assertEquals(0, status.getInt("activeStreams"))
                assertTrue(status.getInt("maxActiveStreams") <= 8, "Unbounded fixture streams")
                // Compare equal warmed-up windows; broad regression ceilings, not a proof of no leaks.
                val baseline = samples.drop(4).take(4)
                val final = samples.takeLast(4)
                fun growth(value: (ResourceSample) -> Long) = final.map(value).sorted()[2] - baseline.map(value).sorted()[2]
                val pss = growth { it.pssKb }
                val native = growth { it.nativeKb }
                val fds = growth { it.fds }
                val threads = growth { it.threads }
                record("growth pssKb=$pss nativeKb=$native fds=$fds threads=$threads")
                assertTrue(pss <= 64 * 1024, "PSS grew by $pss KiB")
                assertTrue(native <= 16 * 1024, "Native heap grew by $native KiB")
                assertTrue(fds <= 16, "File descriptors grew by $fds")
                assertTrue(threads <= 16, "Threads grew by $threads")
                assertEquals(Lifecycle.State.CREATED, activity.state)
                record("PASS tracks=${seen.size} listens=${submitted.size} outages=${interrupted.size} elapsedSeconds=${(SystemClock.elapsedRealtime() - started) / 1000} maxStreams=${status.getInt("maxActiveStreams")}")
            } finally {
                fixture("on")
                withContext(Dispatchers.Main) { core.externalPlaybackBridge().pause() }
            }
        } }
    }

    private data class ResourceSample(val track: Int, val pssKb: Long, val nativeKb: Long, val fds: Long, val threads: Long)
    private fun resources(track: Int): ResourceSample {
        val memory = Debug.MemoryInfo().also(Debug::getMemoryInfo)
        return ResourceSample(track, memory.totalPss.toLong(), Debug.getNativeHeapAllocatedSize() / 1024,
            requireNotNull(File("/proc/self/fd").list()).size.toLong(),
            requireNotNull(File("/proc/self/task").list()).size.toLong())
    }
    private suspend fun await(label: String, predicate: () -> Boolean) {
        assertTrue(withTimeoutOrNull(45_000) { while (!predicate()) delay(100); true } ?: false, "Timed out: $label")
    }
    private suspend fun fixture(action: String): JSONObject = withContext(Dispatchers.IO) {
        JSONObject(URL("http://127.0.0.1:18080/_test/$action").openConnection().apply {
            connectTimeout = 3000; readTimeout = 3000
        }.getInputStream().bufferedReader().use { it.readText() })
    }
    private fun record(message: String) {
        InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply { putString("stream", "\nTV SOAK $message\n") })
    }
}
