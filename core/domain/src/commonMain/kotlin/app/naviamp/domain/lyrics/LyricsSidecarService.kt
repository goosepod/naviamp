package app.naviamp.domain.lyrics

import app.naviamp.domain.Lyrics
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.Track
import app.naviamp.domain.audio.AudioMetadataSidecarService
import app.naviamp.domain.cache.LyricsSidecarRepository
import app.naviamp.domain.playback.PlaybackAudioAssetRepository
import app.naviamp.domain.playback.PlaybackLocalAudio
import app.naviamp.domain.playback.resolvePlaybackAudioSource
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.settings.LyricsSourcePreference
import app.naviamp.domain.settings.normalizedLyricsSearchOrder
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    private val completedLookupMutex = Mutex()
    private val completedLookups = linkedMapOf<LyricsLookupKey, LyricsSidecarResult>()

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
        preferSyncedLyrics: Boolean = false,
        searchOrder: List<LyricsSourcePreference> = emptyList(),
    ): LyricsSidecarResult {
        val activeSearchOrder = searchOrder.normalizedLyricsSearchOrder()
            .filter { source -> onlineLyricsEnabled || source != LyricsSourcePreference.Download }
        val lookupKey = LyricsLookupKey(
            sourceId = sourceId ?: provider.cacheNamespace,
            trackId = track.id.value,
            quality = quality,
            audioCachingEnabled = audioCachingEnabled,
            onlineLyricsEnabled = onlineLyricsEnabled,
            preferSyncedLyrics = preferSyncedLyrics,
            searchOrder = activeSearchOrder,
        )
        completedLookupMutex.withLock { completedLookups[lookupKey] }?.let { return it }

        val result = loadLyricsUncached(
            sourceId = sourceId,
            provider = provider,
            track = track,
            quality = quality,
            audioCachingEnabled = audioCachingEnabled,
            onlineLyricsEnabled = onlineLyricsEnabled,
            preferSyncedLyrics = preferSyncedLyrics,
            activeSearchOrder = activeSearchOrder,
        )
        completedLookupMutex.withLock {
            completedLookups[lookupKey] = result
            while (completedLookups.size > MaxCompletedLyricsLookups) {
                completedLookups.remove(completedLookups.keys.first())
            }
        }
        return result
    }

    private suspend fun loadLyricsUncached(
        sourceId: String?,
        provider: MediaProvider,
        track: Track,
        quality: StreamQuality,
        audioCachingEnabled: Boolean,
        onlineLyricsEnabled: Boolean,
        preferSyncedLyrics: Boolean,
        activeSearchOrder: List<LyricsSourcePreference>,
    ): LyricsSidecarResult {
        var loadedProviderLyrics: Lyrics? = null
        var loadedEmbeddedLyrics: Lyrics? = null
        var loadedOnlineLyrics: Lyrics? = null
        var localAudio: PlaybackLocalAudio? = null
        var firstLyrics: Lyrics? = null

        suspend fun loadSource(source: LyricsSourcePreference): Lyrics? =
            when (source) {
                LyricsSourcePreference.Provider -> loadedProviderLyrics ?: providerLyrics(sourceId, provider, track).also {
                    loadedProviderLyrics = it
                }
                LyricsSourcePreference.Embedded -> loadedEmbeddedLyrics ?: run {
                    localAudio = localAudio ?: localAudio(sourceId, track, quality, audioCachingEnabled)
                    val tags = audioMetadataSidecarService.audioTags(localAudio)
                    audioMetadataSidecarService.embeddedLyrics(tags).also { lyrics ->
                        if (sourceId != null && lyrics != null) {
                            lyricsRepository.cacheEmbeddedLyrics(sourceId, track.id, lyrics)
                        }
                        loadedEmbeddedLyrics = lyrics
                    }
                }
                LyricsSourcePreference.Download -> {
                    if (!onlineLyricsEnabled || sourceId == null) {
                        null
                    } else {
                        loadedOnlineLyrics ?: runCatching { onlineLyrics(sourceId, track) }
                            .getOrElse { error ->
                                if (firstLyrics != null) null else throw error
                            }
                            .also { loadedOnlineLyrics = it }
                    }
                }
            }

        for (source in activeSearchOrder) {
            val lyrics = loadSource(source) ?: continue
            if (firstLyrics == null) firstLyrics = lyrics
            if (!preferSyncedLyrics || lyrics.synced || lyrics.hasTimedLines || lyrics.hasKaraokeCues) {
                return LyricsSidecarResult(
                    lyrics = lyrics,
                    providerLyrics = loadedProviderLyrics,
                    embeddedLyrics = loadedEmbeddedLyrics,
                    onlineLyrics = loadedOnlineLyrics,
                    localAudio = localAudio,
                )
            }
        }

        return LyricsSidecarResult(
            lyrics = firstLyrics,
            providerLyrics = loadedProviderLyrics,
            embeddedLyrics = loadedEmbeddedLyrics,
            onlineLyrics = loadedOnlineLyrics,
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

private data class LyricsLookupKey(
    val sourceId: String,
    val trackId: String,
    val quality: StreamQuality,
    val audioCachingEnabled: Boolean,
    val onlineLyricsEnabled: Boolean,
    val preferSyncedLyrics: Boolean,
    val searchOrder: List<LyricsSourcePreference>,
)

private const val MaxCompletedLyricsLookups = 512
