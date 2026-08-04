package app.naviamp.domain.audio

import app.naviamp.domain.StreamQuality
import app.naviamp.domain.Track
import app.naviamp.domain.playback.PlaybackAudioAssetRepository
import app.naviamp.domain.playback.PlaybackLocalAudio
import app.naviamp.domain.playback.PlaybackReplayGain
import app.naviamp.domain.playback.ReplayGainMode
import app.naviamp.domain.playback.ReplayGainSource
import app.naviamp.domain.playback.resolvePlaybackAudioSource
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

fun interface AudioTagReader {
    suspend fun read(localAudio: PlaybackLocalAudio): List<AudioTag>
}

class AudioMetadataSidecarService(
    private val playbackAudioAssets: PlaybackAudioAssetRepository,
    private val audioTagReader: AudioTagReader,
) {
    private val tagCacheMutex = Mutex()
    private var cachedAudioPath: String? = null
    private var cachedTags: List<AudioTag> = emptyList()

    suspend fun audioTags(localAudio: PlaybackLocalAudio?): List<AudioTag> =
        localAudio?.let { audio ->
            tagCacheMutex.withLock {
                if (cachedAudioPath == audio.path) return@withLock cachedTags
                audioTagReader.read(audio).also { tags ->
                    cachedAudioPath = audio.path
                    cachedTags = tags
                }
            }
        }.orEmpty()

    suspend fun audioTagsForTrack(
        sourceId: String?,
        track: Track,
        quality: StreamQuality,
        audioCachingEnabled: Boolean,
    ): List<AudioTag> {
        val activeSourceId = sourceId ?: return emptyList()
        val localAudio = resolvePlaybackAudioSource(
            sourceId = activeSourceId,
            track = track,
            quality = quality,
            audioCachingEnabled = audioCachingEnabled,
            audioAssets = playbackAudioAssets,
        ).localAudio ?: return emptyList()
        return audioTags(localAudio)
    }

    fun embeddedLyrics(tags: List<AudioTag>) =
        lyricsFromAudioTags(tags)

    fun replayGain(tags: List<AudioTag>) =
        replayGainFromAudioTags(tags)

    suspend fun replayGainForTrack(
        sourceId: String?,
        track: Track,
        quality: StreamQuality,
        audioCachingEnabled: Boolean,
        replayGainMode: ReplayGainMode,
    ): PlaybackReplayGain? {
        if (replayGainMode == ReplayGainMode.Off) return null
        track.replayGain?.takeIf { it.hasAnyValue() }?.let { replayGain ->
            return PlaybackReplayGain(replayGain, ReplayGainSource.Provider)
        }

        val activeSourceId = sourceId ?: return null
        val localAudio = resolvePlaybackAudioSource(
            sourceId = activeSourceId,
            track = track,
            quality = quality,
            audioCachingEnabled = audioCachingEnabled,
            audioAssets = playbackAudioAssets,
        ).localAudio ?: return null

        val replayGain = replayGain(audioTags(localAudio)) ?: return null
        return PlaybackReplayGain(replayGain, ReplayGainSource.LocalTags)
    }
}

private fun app.naviamp.domain.ReplayGain.hasAnyValue(): Boolean =
    trackGainDb != null || albumGainDb != null || trackPeak != null || albumPeak != null
