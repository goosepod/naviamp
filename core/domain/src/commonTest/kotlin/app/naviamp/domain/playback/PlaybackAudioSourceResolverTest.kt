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
            downloadedAudio = { _, _, _ -> localAudio("downloaded") },
            cachedAudio = { _, _, _ -> localAudio("cached") },
        )

        assertEquals(localAudio("downloaded"), plan.localAudio)
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

        assertEquals(localAudio("downloaded"), plan.localAudio)
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

        assertEquals(localAudio("downloaded-original"), plan.localAudio)
        assertEquals(PlaybackSource.DownloadedFile, plan.source)
    }

    @Test
    fun repositoryOverloadFallsBackToAnyCachedAudioForTrack() = runTest {
        val plan = resolvePlaybackAudioSource(
            sourceId = "source",
            track = track("one"),
            quality = StreamQuality.Original,
            audioCachingEnabled = true,
            audioAssets = fakeAudioAssets(
                downloaded = null,
                cached = null,
                cachedForTrack = "cached-transcode",
            ),
        )

        assertEquals(localAudio("cached-transcode"), plan.localAudio)
        assertEquals(PlaybackSource.CachedFile, plan.source)
    }

    @Test
    fun emptyRepositoryFallsBackToProviderStream() = runTest {
        val plan = resolvePlaybackAudioSource(
            sourceId = "source",
            track = track("one"),
            quality = StreamQuality.Original,
            audioCachingEnabled = true,
            audioAssets = emptyPlaybackAudioAssetRepository(),
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
            cachedAudio = { _, _, _ -> localAudio("cached") },
        )

        assertEquals(localAudio("cached"), plan.localAudio)
        assertEquals(PlaybackSource.CachedFile, plan.source)
    }

    @Test
    fun cacheLookupIsSkippedWhenCachingIsDisabled() = runTest {
        var cacheLookups = 0
        val plan = resolvePlaybackAudioSource(
            sourceId = "source",
            track = track("one"),
            quality = StreamQuality.Original,
            audioCachingEnabled = false,
            downloadedAudio = { _, _, _ -> null },
            cachedAudio = { _, _, _ ->
                cacheLookups += 1
                localAudio("cached")
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
            downloadedAudio = { _, _, _ -> localAudio("downloaded") },
            cachedAudio = { _, _, _ -> null },
        )

        assertEquals(42.0, plan.target.engineStartPositionSeconds)
        assertNull(plan.target.providerStreamRequest.startPositionSeconds)
    }

    @Test
    fun remoteTranscodedAudioUsesProviderStartPosition() = runTest {
        val plan = resolvePlaybackAudioSource(
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

    @Test
    fun playbackStreamUrlPrefersLocalAudioUrl() = runTest {
        val plan = resolvePlaybackAudioSource(
            sourceId = "source",
            track = track("one"),
            quality = StreamQuality.Original,
            audioCachingEnabled = true,
            downloadedAudio = { _, _, _ -> PlaybackLocalAudio(path = "/tmp/one.flac", uri = "file:///tmp/one.flac", sizeBytes = 12) },
            cachedAudio = { _, _, _ -> null },
        )

        assertEquals(
            "file:///tmp/one.flac",
            plan.playbackStreamUrl(
                localAudioUrl = { it.uri },
                providerStreamUrl = { "provider://${it.providerStreamRequest.trackId.value}" },
            ),
        )
        assertEquals("/tmp/one.flac", plan.localAudio?.path)
        assertEquals(12, plan.localAudio?.sizeBytes)
    }

    @Test
    fun playbackStreamUrlFallsBackToProviderTarget() = runTest {
        val plan = resolvePlaybackAudioSource(
            sourceId = "source",
            track = track("one"),
            quality = StreamQuality.Original,
            audioCachingEnabled = true,
            downloadedAudio = { _, _, _ -> null },
            cachedAudio = { _, _, _ -> null },
        )

        assertEquals(
            "provider://one",
            plan.playbackStreamUrl(
                localAudioUrl = { it.uri },
                providerStreamUrl = { "provider://${it.providerStreamRequest.trackId.value}" },
            ),
        )
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

    private fun localAudio(id: String): PlaybackLocalAudio =
        PlaybackLocalAudio(
            path = "/tmp/$id",
            uri = "file:///tmp/$id",
        )

    private fun fakeAudioAssets(
        downloadedForTrack: String? = null,
        downloaded: String? = null,
        cached: String? = null,
        cachedForTrack: String? = null,
    ): PlaybackAudioAssetRepository =
        object : PlaybackAudioAssetRepository {
            override suspend fun downloadedAudio(
                sourceId: String,
                trackId: TrackId,
            ): PlaybackLocalAudio? = (downloadedForTrack ?: downloaded)?.let(::localAudio)

            override suspend fun downloadedAudio(
                sourceId: String,
                trackId: TrackId,
                quality: StreamQuality,
            ): PlaybackLocalAudio? = downloaded?.let(::localAudio)

            override suspend fun cachedAudio(
                sourceId: String,
                trackId: TrackId,
                quality: StreamQuality,
            ): PlaybackLocalAudio? = cached?.let(::localAudio)

            override suspend fun cachedAudio(
                sourceId: String,
                trackId: TrackId,
            ): PlaybackLocalAudio? = cachedForTrack?.let(::localAudio)
        }
}
