package app.naviamp.domain.playback

import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class PlaybackPrefetchTest {
    @Test
    fun initialStatsClampConfiguredDepth() {
        assertEquals(
            MaxAudioPrefetchDepth,
            initialAudioPrefetchStats(enabled = true, configuredDepth = 50).configuredDepth,
        )
    }

    @Test
    fun startedResetsRunCounters() {
        val stats = AudioPrefetchStats(
            completed = 2,
            failed = 1,
            sidecarFailed = 1,
            lastError = "old",
            lastSidecarError = "sidecar",
        ).started(3)

        assertTrue(stats.running)
        assertEquals(3, stats.queued)
        assertEquals(0, stats.completed)
        assertEquals(0, stats.failed)
        assertEquals(null, stats.lastError)
        assertEquals(null, stats.lastSidecarError)
    }

    @Test
    fun audioSuccessCountsSidecarResult() {
        val stats = AudioPrefetchStats()
            .audioSuccess(PlaybackSidecarPrepResult())
            .audioSuccess(PlaybackSidecarPrepResult(failed = 1, lastError = "lyrics failed"))

        assertEquals(2, stats.completed)
        assertEquals(1, stats.sidecarCompleted)
        assertEquals(1, stats.sidecarFailed)
        assertEquals("lyrics failed", stats.lastSidecarError)
    }

    @Test
    fun finishedClearsRunningFlag() {
        assertFalse(AudioPrefetchStats(running = true).finished().running)
    }

    @Test
    fun runAudioPrefetchUpdatesStatsForSuccessAndFailure() = runTest {
        val changes = mutableListOf<AudioPrefetchStats>()
        val result = runAudioPrefetch(
            stats = initialAudioPrefetchStats(enabled = true, configuredDepth = 2),
            tracks = listOf(prefetchTrack("ok"), prefetchTrack("bad")),
            isActive = { true },
            cacheAudio = { track ->
                if (track.id.value == "bad") error("cache failed") else track
            },
            onStatsChanged = { changes += it },
        )

        assertEquals(1, result.completed)
        assertEquals(1, result.failed)
        assertEquals("cache failed", result.lastError)
        assertFalse(result.running)
        assertTrue(changes.first().running)
        assertFalse(changes.last().running)
    }

    private fun prefetchTrack(id: String): Track =
        Track(
            id = TrackId(id),
            title = id,
            artistName = "Artist",
            albumTitle = "Album",
            durationSeconds = 180,
            coverArtId = null,
            audioInfo = null,
            replayGain = null,
        )
}
