package app.naviamp.domain.audio

import app.naviamp.domain.AudioInfo
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.isDiscoveryPreview
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
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

    /** Reads a bounded original-file header when no local tags or file properties are available.
     * This does not enable caching, download a complete track, or inspect discovery samples.
     */
    suspend fun metadataForTrack(
        sourceId: String,
        provider: MediaProvider,
        track: Track,
        quality: StreamQuality,
        audioCachingEnabled: Boolean,
        localAudio: PlaybackLocalAudio? = null,
    ): AudioFileMetadata {
        if (track.id.isDiscoveryPreview()) return AudioFileMetadata()
        val localTags = try {
            localAudio?.let { audioTags(it) } ?: audioTagsForTrack(
                sourceId, track, quality, audioCachingEnabled,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }
        if (localTags.isNotEmpty() && track.audioInfo?.samplingRateHz != null && track.audioInfo.bitDepth != null) {
            return AudioFileMetadata(localTags)
        }
        if (!provider.supportsPlaybackByteTransport) return AudioFileMetadata(localTags)
        val key = Triple(sourceId, provider.cacheNamespace, track.id)
        val remote = probeMutex.withLock {
            if (cachedProbeKey == key) return@withLock cachedProbe
            val result = withTimeoutOrNull(10_000) { probeAudioFileMetadata(provider, track) }
            if (result != null) {
                cachedProbeKey = key
                cachedProbe = result
            }
            result
        }
        return AudioFileMetadata(localTags.ifEmpty { remote?.tags.orEmpty() }, remote?.audioInfo)
    }

    private val probeMutex = Mutex()
    private var cachedProbeKey: Triple<String, String, app.naviamp.domain.TrackId>? = null
    private var cachedProbe: AudioFileMetadata? = null

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

/** Original file properties, distinct from the decoder's possibly transcoded stream format. */
data class AudioFileMetadata(
    val tags: List<AudioTag> = emptyList(),
    val audioInfo: AudioInfo? = null,
)

private class AudioProbeComplete : CancellationException("Metadata header received")

private suspend fun probeAudioFileMetadata(provider: MediaProvider, track: Track): AudioFileMetadata? {
    val bytes = ByteArray(MaximumAudioTagProbeBytes)
    var size = 0
    var acceptedResponse = false
    return try {
        try {
            val success = provider.streamTrackBytes(
                StreamRequest(track.id, StreamQuality.Original),
                rangeHeader = "bytes=0-${MaximumAudioTagProbeBytes - 1}",
                headOnly = false,
                onResponse = { response ->
                    acceptedResponse = response.statusCode == 200 ||
                        (response.statusCode == 206 && response.contentRange?.startsWith("bytes 0-") == true)
                    if (!acceptedResponse) throw AudioProbeComplete()
                },
                writeChunk = { chunk, count ->
                    if (!acceptedResponse) throw AudioProbeComplete()
                    val copied = minOf(count, bytes.size - size)
                    chunk.copyInto(bytes, size, 0, copied)
                    size += copied
                    // A server may ignore Range. Stop consuming at the memory/network budget.
                    if (size == bytes.size) throw AudioProbeComplete()
                    if (size >= 42 && bytes[0] == 'f'.code.toByte() && bytes[1] == 'L'.code.toByte()) {
                        val prefix = bytes.copyOf(size)
                        if (audioInfoFromAudioBytes(prefix) != null && audioTagsFromAudioBytes(prefix).isNotEmpty()) {
                            throw AudioProbeComplete()
                        }
                    }
                },
            )
            if (!success) return null
        } catch (_: AudioProbeComplete) {
            // The scoped streaming response is closed as this exception unwinds the provider.
        }
        if (!acceptedResponse || size == 0) return null
        val prefix = bytes.copyOf(size)
        AudioFileMetadata(audioTagsFromAudioBytes(prefix), audioInfoFromAudioBytes(prefix))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null // Missing metadata must not interrupt playback; failures remain retryable.
    }
}
