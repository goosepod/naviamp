package app.naviamp.domain.playback

import app.naviamp.domain.AudioCodec
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaybackAudioSourceResolverTest {
    @Test
    fun downloadedAudioWinsOverCachedAudio() = runTest {
        val plan = resolvePlaybackAudioSource(
            sourceId = "source",
            track = track("one"),
            quality = StreamQuality.Original,
            audioCachingEnabled = true,
            downloadedAudio = { _, _, _ -> "downloaded" },
            cachedAudio = { _, _, _ -> "cached" },
        )

        assertEquals("downloaded", plan.localAudio)
        assertEquals(PlaybackSource.DownloadedFile, plan.source)
        assertEquals(true, plan.hasLocalAudio)
    }

    @Test
    fun repositoryOverloadUsesDownloadedAudioFirst() = runTest {
        val plan = resolvePlaybackAudioSource(
            sourceId = "source",
            track = track("one"),
            quality = StreamQuality.Original,
            audioCachingEnabled = true,
            audioAssets = fakeAudioAssets(downloaded = "downloaded", cached = "cached"),
        )

        assertEquals("downloaded", plan.localAudio)
        assertEquals(PlaybackSource.DownloadedFile, plan.source)
    }

    @Test
    fun repositoryOverloadUsesTrackLevelDownloadedAudioBeforeQualitySpecificCache() = runTest {
        val plan = resolvePlaybackAudioSource(
            sourceId = "source",
            track = track("one"),
            quality = StreamQuality.Transcoded(AudioCodec.Opus, 128),
            audioCachingEnabled = true,
            audioAssets = fakeAudioAssets(
                downloadedForTrack = "downloaded-original",
                downloaded = null,
                cached = "cached-transcode",
            ),
        )

        assertEquals("downloaded-original", plan.localAudio)
        assertEquals(PlaybackSource.DownloadedFile, plan.source)
    }

    @Test
    fun emptyRepositoryFallsBackToProviderStream() = runTest {
        val plan = resolvePlaybackAudioSource(
            sourceId = "source",
            track = track("one"),
            quality = StreamQuality.Original,
            audioCachingEnabled = true,
            audioAssets = emptyPlaybackAudioAssetRepository<String>(),
        )

        assertNull(plan.localAudio)
        assertEquals(PlaybackSource.ProviderStream, plan.source)
    }

    @Test
    fun cachedAudioIsUsedWhenDownloadIsMissingAndCachingIsEnabled() = runTest {
        val plan = resolvePlaybackAudioSource(
            sourceId = "source",
            track = track("one"),
            quality = StreamQuality.Original,
            audioCachingEnabled = true,
            downloadedAudio = { _, _, _ -> null },
            cachedAudio = { _, _, _ -> "cached" },
        )

        assertEquals("cached", plan.localAudio)
        assertEquals(PlaybackSource.CachedFile, plan.source)
    }

    @Test
    fun cacheLookupIsSkippedWhenCachingIsDisabled() = runTest {
        var cacheLookups = 0
        val plan = resolvePlaybackAudioSource<String>(
            sourceId = "source",
            track = track("one"),
            quality = StreamQuality.Original,
            audioCachingEnabled = false,
            downloadedAudio = { _, _, _ -> null },
            cachedAudio = { _, _, _ ->
                cacheLookups += 1
                "cached"
            },
        )

        assertNull(plan.localAudio)
        assertEquals(PlaybackSource.ProviderStreamCacheDisabled, plan.source)
        assertEquals(0, cacheLookups)
    }

    @Test
    fun localTranscodedAudioKeepsStartPositionForEngine() = runTest {
        val plan = resolvePlaybackAudioSource(
            sourceId = "source",
            track = track("one"),
            quality = StreamQuality.Transcoded(AudioCodec.Opus, 128),
            audioCachingEnabled = true,
            startPositionSeconds = 42.0,
            downloadedAudio = { _, _, _ -> "downloaded" },
            cachedAudio = { _, _, _ -> null },
        )

        assertEquals(42.0, plan.target.engineStartPositionSeconds)
        assertNull(plan.target.providerStreamRequest.startPositionSeconds)
    }

    @Test
    fun remoteTranscodedAudioUsesProviderStartPosition() = runTest {
        val plan = resolvePlaybackAudioSource<String>(
            sourceId = "source",
            track = track("one"),
            quality = StreamQuality.Transcoded(AudioCodec.Opus, 128),
            audioCachingEnabled = true,
            startPositionSeconds = 42.0,
            downloadedAudio = { _, _, _ -> null },
            cachedAudio = { _, _, _ -> null },
        )

        assertNull(plan.target.engineStartPositionSeconds)
        assertEquals(42.0, plan.target.providerStreamRequest.startPositionSeconds)
    }

    private fun track(id: String): Track =
        Track(
            id = TrackId(id),
            title = "Track $id",
            artistName = "Artist",
            albumTitle = "Album",
            durationSeconds = 180,
            coverArtId = null,
            audioInfo = null,
            replayGain = null,
            favoritedAtIso8601 = null,
        )

    private fun fakeAudioAssets(
        downloadedForTrack: String? = null,
        downloaded: String? = null,
        cached: String? = null,
    ): PlaybackAudioAssetRepository<String> =
        object : PlaybackAudioAssetRepository<String> {
            override suspend fun downloadedAudio(
                sourceId: String,
                trackId: TrackId,
            ): String? = downloadedForTrack ?: downloaded

            override suspend fun downloadedAudio(
                sourceId: String,
                trackId: TrackId,
                quality: StreamQuality,
            ): String? = downloaded

            override suspend fun cachedAudio(
                sourceId: String,
                trackId: TrackId,
                quality: StreamQuality,
            ): String? = cached
        }
}
