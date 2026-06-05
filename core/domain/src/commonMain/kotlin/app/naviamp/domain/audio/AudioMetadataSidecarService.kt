package app.naviamp.domain.audio

import app.naviamp.domain.StreamQuality
import app.naviamp.domain.Track
import app.naviamp.domain.playback.PlaybackAudioAssetRepository
import app.naviamp.domain.playback.PlaybackLocalAudio
import app.naviamp.domain.playback.PlaybackReplayGain
import app.naviamp.domain.playback.ReplayGainMode
import app.naviamp.domain.playback.ReplayGainSource
import app.naviamp.domain.playback.resolvePlaybackAudioSource

fun interface AudioTagReader {
    suspend fun read(localAudio: PlaybackLocalAudio): List<AudioTag>
}

class AudioMetadataSidecarService(
    private val playbackAudioAssets: PlaybackAudioAssetRepository,
    private val audioTagReader: AudioTagReader,
) {
    suspend fun audioTags(localAudio: PlaybackLocalAudio?): List<AudioTag> =
        localAudio?.let { audioTagReader.read(it) }.orEmpty()

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
