package app.naviamp.domain.playback

import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.queue.PlaybackQueue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CancellationException
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
    fun planAudioPrefetchWorkBuildsSharedWork() {
        val work = planAudioPrefetchWork(
            sourceId = "server",
            provider = "provider",
            quality = StreamQuality.Original,
            queue = PlaybackQueue(
                tracks = listOf(prefetchTrack("current"), prefetchTrack("next-1"), prefetchTrack("next-2")),
                currentIndex = 0,
            ),
            enabled = true,
            configuredDepth = 1,
        )

        requireNotNull(work)
        assertEquals("server", work.sourceId)
        assertEquals("provider", work.provider)
        assertEquals(StreamQuality.Original, work.quality)
        assertEquals(listOf("next-1"), work.tracks.map { it.id.value })
        assertTrue(work.items.single().cacheAudio)
        assertTrue(work.items.single().prepareSidecars)
        assertEquals(1, work.stats.configuredDepth)
    }

    @Test
    fun incrementalPlanOnlyCachesNewHorizonTracksAndLimitsSidecarsToNext() {
        val completedAudio = AudioPrefetchCompletionLedger()
        val completedSidecars = AudioPrefetchCompletionLedger()
        val nextOneKey = AudioPrefetchKey("server", TrackId("next-1"), StreamQuality.Original)
        val nextTwoKey = AudioPrefetchKey("server", TrackId("next-2"), StreamQuality.Original)
        completedAudio.record(nextOneKey)
        completedAudio.record(nextTwoKey)
        completedSidecars.record(nextOneKey)

        val work = planAudioPrefetchWork(
            sourceId = "server",
            provider = "provider",
            quality = StreamQuality.Original,
            queue = PlaybackQueue(
                tracks = listOf(
                    prefetchTrack("current"),
                    prefetchTrack("next-1"),
                    prefetchTrack("next-2"),
                    prefetchTrack("next-3"),
                ),
                currentIndex = 1,
            ),
            enabled = true,
            configuredDepth = 2,
            completedAudio = completedAudio,
            completedSidecars = completedSidecars,
        )

        requireNotNull(work)
        assertEquals(listOf("next-2", "next-3"), work.tracks.map { it.id.value })
        assertEquals(false, work.items[0].cacheAudio)
        assertEquals(true, work.items[0].prepareSidecars)
        assertEquals(true, work.items[1].cacheAudio)
        assertEquals(false, work.items[1].prepareSidecars)
    }

    @Test
    fun completionLedgerIsBounded() {
        val ledger = AudioPrefetchCompletionLedger(capacity = 2)
        val first = AudioPrefetchKey("server", TrackId("first"), StreamQuality.Original)
        val second = AudioPrefetchKey("server", TrackId("second"), StreamQuality.Original)
        val third = AudioPrefetchKey("server", TrackId("third"), StreamQuality.Original)

        ledger.record(first)
        ledger.record(second)
        ledger.record(third)

        assertFalse(ledger.contains(first))
        assertTrue(ledger.contains(second))
        assertTrue(ledger.contains(third))
    }

    @Test
    fun planAudioPrefetchWorkSkipsMissingRequirements() {
        val queue = PlaybackQueue(tracks = listOf(prefetchTrack("current"), prefetchTrack("next")), currentIndex = 0)

        assertNull(
            planAudioPrefetchWork(
                sourceId = null,
                provider = "provider",
                quality = StreamQuality.Original,
                queue = queue,
                enabled = true,
                configuredDepth = 1,
            ),
        )
        assertNull(
            planAudioPrefetchWork(
                sourceId = "server",
                provider = "provider",
                quality = StreamQuality.Original,
                queue = queue,
                enabled = false,
                configuredDepth = 1,
            ),
        )
        assertNull(
            planAudioPrefetchWork(
                sourceId = "server",
                provider = "provider",
                quality = StreamQuality.Original,
                queue = queue,
                enabled = true,
                configuredDepth = 0,
            ),
        )
    }

    @Test
    fun runAudioPrefetchUpdatesStatsForSuccessAndFailure() = runTest {
        val changes = mutableListOf<AudioPrefetchStats>()
        val result = runAudioPrefetch(
            stats = initialAudioPrefetchStats(enabled = true, configuredDepth = 2),
            items = listOf(prefetchItem("ok"), prefetchItem("bad")),
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

    @Test
    fun coverArtWarmFailuresDoNotFailAudioPrefetch() = runTest {
        val warmed = mutableListOf<String>()

        val result = runAudioPrefetch(
            stats = initialAudioPrefetchStats(enabled = true, configuredDepth = 1),
            items = listOf(prefetchItem("ok")),
            isActive = { true },
            cacheAudio = { track -> track },
            warmCoverArt = { track ->
                warmed += track.id.value
                error("art failed")
            },
        )

        assertEquals(listOf("ok"), warmed)
        assertEquals(1, result.completed)
        assertEquals(0, result.failed)
        assertEquals(null, result.lastError)
    }

    @Test
    fun runOnlyPerformsAndRecordsTheWorkRequestedByEachItem() = runTest {
        val audio = mutableListOf<String>()
        val sidecars = mutableListOf<String>()
        val completedAudio = mutableListOf<String>()
        val completedSidecars = mutableListOf<String>()
        val audioOnly = prefetchItem("audio-only").copy(prepareSidecars = false)
        val sidecarOnly = prefetchItem("sidecar-only").copy(cacheAudio = false)

        runAudioPrefetch(
            stats = initialAudioPrefetchStats(enabled = true, configuredDepth = 2),
            items = listOf(audioOnly, sidecarOnly),
            isActive = { true },
            cacheAudio = { track -> track.also { audio += track.id.value } },
            prepareSidecars = { track, _ ->
                sidecars += track.id.value
                PlaybackSidecarPrepResult()
            },
            onAudioCached = { completedAudio += it.trackId.value },
            onSidecarsPrepared = { completedSidecars += it.trackId.value },
        )

        assertEquals(listOf("audio-only"), audio)
        assertEquals(listOf("sidecar-only"), sidecars)
        assertEquals(listOf("audio-only"), completedAudio)
        assertEquals(listOf("sidecar-only"), completedSidecars)
    }

    @Test
    fun failedSidecarsAreNotRecordedAsComplete() = runTest {
        val completedSidecars = mutableListOf<String>()

        runAudioPrefetch(
            stats = initialAudioPrefetchStats(enabled = true, configuredDepth = 1),
            items = listOf(prefetchItem("retry")),
            isActive = { true },
            cacheAudio = { it },
            prepareSidecars = { _, _ -> PlaybackSidecarPrepResult(failed = 1) },
            onSidecarsPrepared = { completedSidecars += it.trackId.value },
        )

        assertTrue(completedSidecars.isEmpty())
    }

    @Test
    fun cancellationStopsPrefetchWithoutBeingReportedAsAnAudioFailure() = runTest {
        var failureReported = false

        assertFailsWith<CancellationException> {
            runAudioPrefetch(
                stats = initialAudioPrefetchStats(enabled = true, configuredDepth = 1),
                items = listOf(prefetchItem("cancelled")),
                isActive = { true },
                cacheAudio = { throw CancellationException("queue replaced") },
                onTrackFailed = { _, _ -> failureReported = true },
            )
        }

        assertFalse(failureReported)
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

    private fun prefetchItem(id: String): AudioPrefetchItem {
        val track = prefetchTrack(id)
        return AudioPrefetchItem(
            track = track,
            key = AudioPrefetchKey("server", track.id, StreamQuality.Original),
            cacheAudio = true,
            prepareSidecars = true,
        )
    }
}
