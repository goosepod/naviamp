package app.naviamp.domain.audio

import app.naviamp.domain.StreamQuality
import app.naviamp.domain.TrackId
import app.naviamp.domain.playback.PlaybackAudioAssetRepository
import app.naviamp.domain.playback.PlaybackLocalAudio
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class AudioMetadataSidecarServiceTest {
    @Test
    fun readsTagsThroughInjectedLocalAudioReader() = runTest {
        val service = AudioMetadataSidecarService(
            playbackAudioAssets = EmptyAudioAssets,
            audioTagReader = { localAudio -> listOf(AudioTag("Path", localAudio.path)) },
        )

        assertEquals(
            listOf(AudioTag("Path", "song.flac")),
            service.audioTags(PlaybackLocalAudio(path = "song.flac", uri = "file://song.flac")),
        )
    }
}

private object EmptyAudioAssets : PlaybackAudioAssetRepository {
    override suspend fun downloadedAudio(sourceId: String, trackId: TrackId): PlaybackLocalAudio? = null

    override suspend fun downloadedAudio(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
    ): PlaybackLocalAudio? = null

    override suspend fun cachedAudio(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
    ): PlaybackLocalAudio? = null
}
