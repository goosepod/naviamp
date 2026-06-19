package app.naviamp.domain.lyrics

import app.naviamp.domain.Lyrics
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.Track
import app.naviamp.domain.audio.AudioMetadataSidecarService
import app.naviamp.domain.cache.LyricsSidecarRepository
import app.naviamp.domain.playback.PlaybackAudioAssetRepository
import app.naviamp.domain.playback.PlaybackLocalAudio
import app.naviamp.domain.playback.resolvePlaybackAudioSource
import app.naviamp.domain.playback.shouldLoadOnlineLyrics
import app.naviamp.domain.provider.MediaProvider

data class LyricsSidecarResult(
    val lyrics: Lyrics?,
    val providerLyrics: Lyrics?,
    val embeddedLyrics: Lyrics?,
    val onlineLyrics: Lyrics?,
    val localAudio: PlaybackLocalAudio?,
)

class LyricsSidecarService(
    private val lyricsRepository: LyricsSidecarRepository,
    private val playbackAudioAssets: PlaybackAudioAssetRepository,
    private val audioMetadataSidecarService: AudioMetadataSidecarService,
) {
    suspend fun providerLyrics(
        sourceId: String?,
        provider: MediaProvider,
        track: Track,
    ): Lyrics? =
        sourceId?.let { activeSourceId ->
            lyricsRepository.providerLyrics(activeSourceId, provider, track.id)
        } ?: provider.lyrics(track.id)

    suspend fun embeddedLyrics(
        sourceId: String?,
        track: Track,
        quality: StreamQuality,
        audioCachingEnabled: Boolean,
    ): Lyrics? {
        val localAudio = localAudio(sourceId, track, quality, audioCachingEnabled)
        val lyrics = audioMetadataSidecarService.embeddedLyrics(
            audioMetadataSidecarService.audioTags(localAudio),
        )
        if (sourceId != null && lyrics != null) {
            lyricsRepository.cacheEmbeddedLyrics(sourceId, track.id, lyrics)
        }
        return lyrics
    }

    suspend fun onlineLyrics(
        sourceId: String?,
        track: Track,
    ): Lyrics? =
        sourceId?.let { activeSourceId -> lyricsRepository.lrclibLyrics(activeSourceId, track) }

    suspend fun loadLyrics(
        sourceId: String?,
        provider: MediaProvider,
        track: Track,
        quality: StreamQuality,
        audioCachingEnabled: Boolean,
        onlineLyricsEnabled: Boolean,
    ): LyricsSidecarResult {
        val localAudio = localAudio(sourceId, track, quality, audioCachingEnabled)
        val tags = audioMetadataSidecarService.audioTags(localAudio)
        val embeddedLyrics = audioMetadataSidecarService.embeddedLyrics(tags)
        if (sourceId != null && embeddedLyrics != null) {
            lyricsRepository.cacheEmbeddedLyrics(sourceId, track.id, embeddedLyrics)
        }

        val providerLyrics = providerLyrics(sourceId, provider, track)

        val localLyrics = providerLyrics ?: embeddedLyrics
        val onlineLyrics = if (
            sourceId != null &&
            shouldLoadOnlineLyrics(
                onlineLyricsEnabled = onlineLyricsEnabled,
                providerLyrics = providerLyrics,
                embeddedLyrics = embeddedLyrics,
            )
        ) {
            runCatching { onlineLyrics(sourceId, track) }
                .getOrElse { error ->
                    if (localLyrics != null) null else throw error
                }
        } else {
            null
        }

        return LyricsSidecarResult(
            lyrics = selectPreferredLyrics(
                providerLyrics = providerLyrics,
                embeddedLyrics = embeddedLyrics,
                onlineLyrics = onlineLyrics,
            ),
            providerLyrics = providerLyrics,
            embeddedLyrics = embeddedLyrics,
            onlineLyrics = onlineLyrics,
            localAudio = localAudio,
        )
    }

    private suspend fun localAudio(
        sourceId: String?,
        track: Track,
        quality: StreamQuality,
        audioCachingEnabled: Boolean,
    ): PlaybackLocalAudio? =
        sourceId?.let { activeSourceId ->
            resolvePlaybackAudioSource(
                sourceId = activeSourceId,
                track = track,
                quality = quality,
                audioCachingEnabled = audioCachingEnabled,
                audioAssets = playbackAudioAssets,
            ).localAudio
        }
}
