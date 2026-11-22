package app.naviamp.presentation

import app.naviamp.ui.NaviampRepeatMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NaviampLocalArtworkExternalPlaybackTest {
    private val credentialedUrl = "https://music.example.test/rest/getCoverArt?id=al-1&u=user&t=token&s=salt"

    private fun snapshot(mediaId: String = "track-1", url: String? = credentialedUrl) = NaviampExternalPlaybackSnapshot(
        state = NaviampExternalPlaybackState.Playing,
        current = NaviampExternalMediaItem(mediaId, "Title", "Artist", artworkUrl = url),
        resumeIdentity = NaviampExternalPlaybackResumeIdentity("source-a", 1, 1, 1, 1, mediaId, 0, false),
    )

    private class Control(val state: MutableStateFlow<NaviampExternalPlaybackSnapshot>) : NaviampExternalPlaybackControl {
        val calls = mutableListOf<String>()
        override fun snapshot() = state.value
        override fun play() { calls += "play" }
        override fun pause() { calls += "pause" }
        override fun playPause() { calls += "playPause" }
        override fun stop() { calls += "stop" }
        override fun previous() { calls += "previous" }
        override fun next() { calls += "next" }
        override fun seekTo(positionMillis: Long) { calls += "seekTo" }
        override fun seekBy(deltaMillis: Long, advancePastEnd: Boolean) { calls += "seekBy" }
        override fun setShuffleActive(active: Boolean) { calls += "shuffle" }
        override fun setRepeatMode(mode: NaviampRepeatMode) { calls += "repeat" }
        override fun setVolumePercent(percent: Int) { calls += "volume" }
    }

    private class Files : NaviampExternalArtworkFileWriter {
        val writes = mutableListOf<Pair<String, ByteArray>>()
        var result: (String) -> String? = { name -> "file:///private/naviamp/$name" }
        override suspend fun write(name: String, bytes: ByteArray): String? { writes += name to bytes; return result(name) }
    }

    @Test
    fun providerUrlsNeverLeaveTheProcessAndOnlyLocalFilesArePublished() {
        val item = NaviampExternalMediaItem("track", "Title", "Artist", artworkUrl = credentialedUrl)
        assertNull(item.externalArtworkUrl)
        assertNull(item.copy(artworkUrl = "http://music.example.test/art?api_key=secret").externalArtworkUrl)
        assertEquals("file:///music/cover.jpg", item.copy(artworkUrl = "file:///music/cover.jpg").externalArtworkUrl)
        assertEquals("file:///private/now-playing-0", item.copy(localArtworkUri = "file:///private/now-playing-0").externalArtworkUrl)
    }

    @Test
    fun currentArtworkIsFetchedOnceAndPublishedAsALocalFile() = runTest {
        val state = MutableStateFlow(snapshot())
        val files = Files(); val loads = mutableListOf<String>()
        val playback = NaviampLocalArtworkExternalPlayback(backgroundScope, Control(state), state, { loads += it; byteArrayOf(7) }, files)
        runCurrent()
        assertEquals(listOf(credentialedUrl), loads)
        assertEquals("file:///private/naviamp/now-playing-0", playback.snapshot().current?.externalArtworkUrl)
        assertEquals("file:///private/naviamp/now-playing-0", playback.snapshots.first().current?.externalArtworkUrl)
        repeat(20) { state.value = state.value.copy(positionMillis = it * 1_000L) }
        runCurrent()
        assertEquals(1, loads.size)
        assertEquals(1, files.writes.size)
        playback.close()
    }

    @Test
    fun trackChangesAlternateFileNamesAndNeverReuseThePreviousTracksFile() = runTest {
        val state = MutableStateFlow(snapshot())
        val files = Files()
        val gate = CompletableDeferred<Unit>()
        var loads = 0
        val playback = NaviampLocalArtworkExternalPlayback(backgroundScope, Control(state), state, {
            loads++; if (loads == 2) gate.await(); byteArrayOf(loads.toByte())
        }, files)
        runCurrent()
        val first = playback.snapshot().current?.externalArtworkUrl
        state.value = snapshot("track-2", "https://music.example.test/rest/getCoverArt?id=al-2&t=token")
        runCurrent()
        assertNull(playback.snapshot().current?.externalArtworkUrl, "the previous track's file must not be shown while loading")
        gate.complete(Unit); runCurrent()
        val second = playback.snapshot().current?.externalArtworkUrl
        assertEquals(listOf("now-playing-0", "now-playing-1"), files.writes.map { it.first })
        assertNotEquals(first, second)
        playback.close()
    }

    @Test
    fun lateArtworkForAReplacedTrackOrSourceIsDiscarded() = runTest {
        val state = MutableStateFlow(snapshot())
        val files = Files(); val gate = CompletableDeferred<Unit>()
        val playback = NaviampLocalArtworkExternalPlayback(backgroundScope, Control(state), state, {
            gate.await(); byteArrayOf(1)
        }, files)
        runCurrent()
        state.value = state.value.copy(resumeIdentity = state.value.resumeIdentity!!.copy(sourceId = "source-b", sourceGeneration = 2))
        runCurrent()
        gate.complete(Unit); runCurrent()
        // Only the replacement source's load may publish; the first load was cancelled.
        assertEquals(1, files.writes.size)
        assertEquals("file:///private/naviamp/now-playing-0", playback.snapshot().current?.externalArtworkUrl)
        state.value = state.value.copy(current = null); runCurrent()
        assertNull(playback.snapshot().current)
        playback.close()
    }

    @Test
    fun syntheticLocalFailedAndNonLocalWriterResultsPublishNoRemoteUrl() = runTest {
        for (case in listOf("synthetic", "local", "load-fails", "empty", "write-fails", "writer-returns-remote")) {
            val url = when (case) {
                "synthetic" -> "naviamp-radio-tile://tile?label=Radio"
                "local" -> "file:///music/cover.jpg"
                else -> credentialedUrl
            }
            val state = MutableStateFlow(snapshot(url = url))
            val files = Files().apply {
                if (case == "write-fails") result = { error("disk full") }
                if (case == "writer-returns-remote") result = { credentialedUrl }
            }
            var loads = 0
            val playback = NaviampLocalArtworkExternalPlayback(backgroundScope, Control(state), state, {
                loads++
                when (case) { "load-fails" -> error("offline"); "empty" -> byteArrayOf(); else -> byteArrayOf(1) }
            }, files)
            runCurrent()
            val published = playback.snapshot().current?.externalArtworkUrl
            if (case == "local") assertEquals("file:///music/cover.jpg", published, case) else assertNull(published, case)
            if (case == "synthetic" || case == "local") assertEquals(0, loads, case)
            playback.close()
        }
    }

    @Test
    fun transportCommandsReachTheUnderlyingControl() = runTest {
        val state = MutableStateFlow(snapshot())
        val control = Control(state)
        val playback = NaviampLocalArtworkExternalPlayback(backgroundScope, control, state, { null }, Files())
        playback.playPause(); playback.next(); playback.seekTo(1_000); playback.setVolumePercent(10)
        assertEquals(listOf("playPause", "next", "seekTo", "volume"), control.calls)
        assertTrue(playback.snapshot().current?.externalArtworkUrl == null)
        playback.close()
    }
}
