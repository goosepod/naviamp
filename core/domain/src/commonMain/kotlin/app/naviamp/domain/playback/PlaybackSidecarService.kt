package app.naviamp.domain.playback

import app.naviamp.domain.Lyrics
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.Track
import app.naviamp.domain.cache.SidecarStatusRepository
import app.naviamp.domain.lyrics.LyricsSidecarService
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.waveform.AudioWaveform
import app.naviamp.domain.waveform.AudioWaveformService

data class PlaybackSidecarPrepResult(
    val waveform: AudioWaveform? = null,
    val lyrics: Lyrics? = null,
    val failed: Int = 0,
    val lastError: String? = null,
) {
    val successful: Boolean
        get() = failed == 0
}

class PlaybackSidecarService(
    private val waveformService: AudioWaveformService,
    private val lyricsSidecarService: LyricsSidecarService,
    private val sidecarStatusRepository: SidecarStatusRepository,
) {
    suspend fun prepareWaveform(
        sourceId: String?,
        provider: MediaProvider,
        track: Track,
        quality: StreamQuality,
        audioCachingEnabled: Boolean,
    ): AudioWaveform? {
        val waveform = waveformService.loadOrCreateWaveform(
            sourceId = sourceId,
            provider = provider,
            track = track,
            quality = quality,
            audioCachingEnabled = audioCachingEnabled,
        ).waveform
        if (sourceId != null) {
            sidecarStatusRepository.recordSidecarSuccess(
                sourceId = sourceId,
                trackId = track.id,
                quality = quality,
                sidecarType = SidecarTypeWaveform,
            )
        }
        return waveform
    }

    suspend fun prepareLyrics(
        sourceId: String?,
        provider: MediaProvider,
        track: Track,
        quality: StreamQuality,
        audioCachingEnabled: Boolean,
        onlineLyricsEnabled: Boolean,
    ): Lyrics? {
        val lyrics = lyricsSidecarService.loadLyrics(
            sourceId = sourceId,
            provider = provider,
            track = track,
            quality = quality,
            audioCachingEnabled = audioCachingEnabled,
            onlineLyricsEnabled = onlineLyricsEnabled,
        ).lyrics
        if (sourceId != null) {
            sidecarStatusRepository.recordSidecarSuccess(
                sourceId = sourceId,
                trackId = track.id,
                quality = quality,
                sidecarType = SidecarTypeLyrics,
            )
        }
        return lyrics
    }

    suspend fun prepareAll(
        sourceId: String?,
        provider: MediaProvider,
        track: Track,
        quality: StreamQuality,
        audioCachingEnabled: Boolean,
        onlineLyricsEnabled: Boolean,
        includeLyrics: Boolean,
    ): PlaybackSidecarPrepResult {
        var waveform: AudioWaveform? = null
        var lyrics: Lyrics? = null
        var failed = 0
        var lastError: String? = null

        suspend fun runSidecar(
            sidecarType: String,
            failureLabel: (Throwable) -> String,
            block: suspend () -> Unit,
        ) {
            runCatching { block() }
                .onFailure { error ->
                    val message = failureLabel(error)
                    recordFailure(
                        sourceId = sourceId,
                        track = track,
                        quality = quality,
                        sidecarType = sidecarType,
                        message = message,
                    )
                    failed += 1
                    lastError = message
                }
        }

        runSidecar(SidecarTypeWaveform, ::waveformUnavailableStatus) {
            waveform = prepareWaveform(sourceId, provider, track, quality, audioCachingEnabled)
        }
        if (includeLyrics) {
            runSidecar(SidecarTypeLyrics, ::lyricsUnavailableStatus) {
                lyrics = prepareLyrics(
                    sourceId = sourceId,
                    provider = provider,
                    track = track,
                    quality = quality,
                    audioCachingEnabled = audioCachingEnabled,
                    onlineLyricsEnabled = onlineLyricsEnabled,
                )
            }
        }

        return PlaybackSidecarPrepResult(
            waveform = waveform,
            lyrics = lyrics,
            failed = failed,
            lastError = lastError,
        )
    }

    suspend fun prepareDetailedLyrics(
        sourceId: String,
        provider: MediaProvider,
        track: Track,
        quality: StreamQuality,
        audioCachingEnabled: Boolean,
    ): PlaybackSidecarPrepResult {
        var failed = 0
        var lastError: String? = null

        suspend fun runSidecar(sidecarType: String, block: suspend () -> Unit) {
            runCatching { block() }
                .onSuccess {
                    sidecarStatusRepository.recordSidecarSuccess(
                        sourceId = sourceId,
                        trackId = track.id,
                        quality = quality,
                        sidecarType = sidecarType,
                    )
                }
                .onFailure { error ->
                    val message = sidecarFailureStatus(error)
                    sidecarStatusRepository.recordSidecarFailure(
                        sourceId = sourceId,
                        trackId = track.id,
                        quality = quality,
                        sidecarType = sidecarType,
                        errorMessage = message,
                    )
                    failed += 1
                    lastError = message
                }
        }

        runSidecar(SidecarTypeProviderLyrics) {
            lyricsSidecarService.providerLyrics(sourceId, provider, track)
        }
        runSidecar(SidecarTypeEmbeddedLyrics) {
            lyricsSidecarService.embeddedLyrics(
                sourceId = sourceId,
                track = track,
                quality = quality,
                audioCachingEnabled = audioCachingEnabled,
            )
        }
        runSidecar(SidecarTypeLrclibLyrics) {
            lyricsSidecarService.onlineLyrics(sourceId, track)
        }

        return PlaybackSidecarPrepResult(failed = failed, lastError = lastError)
    }

    private fun recordFailure(
        sourceId: String?,
        track: Track,
        quality: StreamQuality,
        sidecarType: String,
        message: String,
    ) {
        sourceId ?: return
        sidecarStatusRepository.recordSidecarFailure(
            sourceId = sourceId,
            trackId = track.id,
            quality = quality,
            sidecarType = sidecarType,
            errorMessage = message,
        )
    }
}
