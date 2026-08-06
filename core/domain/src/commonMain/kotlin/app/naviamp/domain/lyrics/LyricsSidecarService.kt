package app.naviamp.domain.lyrics

import app.naviamp.domain.LyricLine
import app.naviamp.domain.Lyrics
import app.naviamp.domain.LyricsSource
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.Track
import app.naviamp.domain.audio.AudioMetadataSidecarService
import app.naviamp.domain.cache.LyricsSidecarRepository
import app.naviamp.domain.playback.PlaybackAudioAssetRepository
import app.naviamp.domain.playback.PlaybackLocalAudio
import app.naviamp.domain.playback.resolvePlaybackAudioSource
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.settings.LyricsSourcePreference
import app.naviamp.domain.settings.LyricsTimingPreference
import app.naviamp.domain.settings.normalizedLyricsSearchOrder
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class LyricsSidecarResult(
    val lyrics: Lyrics?,
    val availableTiming: LyricsTiming?,
    val providerLyrics: Lyrics?,
    val embeddedLyrics: Lyrics?,
    val onlineLyrics: List<Lyrics>,
    val localAudio: PlaybackLocalAudio?,
)

class LyricsSidecarService(
    private val lyricsRepository: LyricsSidecarRepository,
    private val playbackAudioAssets: PlaybackAudioAssetRepository,
    private val audioMetadataSidecarService: AudioMetadataSidecarService,
) {
    val onlineProviders: List<LyricsProvider>
        get() = lyricsRepository.onlineProviders

    private val completedLookupMutex = Mutex()
    private val completedLookups = linkedMapOf<LyricsLookupKey, LyricsSidecarResult>()

    suspend fun providerLyrics(
        sourceId: String?,
        provider: MediaProvider,
        track: Track,
        acceptedTimings: Set<LyricsTiming> = LyricsTiming.entries.toSet(),
    ): Lyrics? =
        sourceId?.let { activeSourceId ->
            lyricsRepository.providerLyrics(activeSourceId, provider, track.id, acceptedTimings)
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
        provider: LyricsProvider,
        acceptedTimings: Set<LyricsTiming> = LyricsTiming.entries.toSet(),
    ): Lyrics? = sourceId?.let { activeSourceId ->
        lyricsRepository.onlineLyrics(activeSourceId, track, provider, acceptedTimings)
    }

    suspend fun loadLyrics(
        sourceId: String?,
        provider: MediaProvider,
        track: Track,
        quality: StreamQuality,
        audioCachingEnabled: Boolean,
        onlineLyricsEnabled: Boolean,
        timingPreference: LyricsTimingPreference = LyricsTimingPreference.FirstAvailable,
        displayTimingPreference: LyricsTimingPreference = timingPreference,
        searchOrder: List<LyricsSourcePreference> = emptyList(),
    ): LyricsSidecarResult {
        val activeSearchOrder = searchOrder.normalizedLyricsSearchOrder()
            .filter { source -> onlineLyricsEnabled || source != LyricsSourcePreference.Online }
        val lookupKey = LyricsLookupKey(
            sourceId = sourceId ?: provider.cacheNamespace,
            trackId = track.id.value,
            quality = quality,
            audioCachingEnabled = audioCachingEnabled,
            onlineLyricsEnabled = onlineLyricsEnabled,
            timingPreference = timingPreference,
            displayTimingPreference = displayTimingPreference,
            searchOrder = activeSearchOrder,
        )
        completedLookupMutex.withLock { completedLookups[lookupKey] }?.let { return it }

        val result = loadLyricsUncached(
            sourceId = sourceId,
            provider = provider,
            track = track,
            quality = quality,
            audioCachingEnabled = audioCachingEnabled,
            timingPreference = timingPreference,
            displayTimingPreference = displayTimingPreference,
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
        timingPreference: LyricsTimingPreference,
        displayTimingPreference: LyricsTimingPreference,
        activeSearchOrder: List<LyricsSourcePreference>,
    ): LyricsSidecarResult {
        var loadedProviderLyrics: Lyrics? = null
        var loadedEmbeddedLyrics: Lyrics? = null
        val loadedOnlineLyrics = linkedMapOf<String, Lyrics>()
        var localAudio: PlaybackLocalAudio? = null
        val candidates = mutableListOf<RankedLyrics>()
        val acceptedTimings = timingPreference.acceptedTimings()

        fun result(lyrics: Lyrics?): LyricsSidecarResult = LyricsSidecarResult(
            lyrics = lyrics?.forTimingPreference(displayTimingPreference),
            availableTiming = lyrics?.timing,
            providerLyrics = loadedProviderLyrics,
            embeddedLyrics = loadedEmbeddedLyrics,
            onlineLyrics = loadedOnlineLyrics.values.toList(),
            localAudio = localAudio,
        )

        fun recordCandidate(
            lyrics: Lyrics,
            sourceIndex: Int,
            providerIndex: Int = 0,
        ) {
            if (candidates.none { candidate ->
                    candidate.lyrics.source == lyrics.source &&
                        candidate.sourceIndex == sourceIndex &&
                        candidate.providerIndex == providerIndex
                }
            ) {
                candidates += RankedLyrics(lyrics, sourceIndex, providerIndex)
            }
        }

        // Cache preflight is deliberately first: no provider API, audio read, or network lookup
        // occurs when a cached result can satisfy the requested display timing.
        if (sourceId != null) {
            val sharedCached = lyricsRepository.cachedLyrics(sourceId, track.id)
            activeSearchOrder.forEachIndexed { sourceIndex, source ->
                when (source) {
                    LyricsSourcePreference.Provider -> sharedCached
                        ?.takeIf { lyrics -> lyrics.source == LyricsSource.Provider }
                        ?.let { lyrics ->
                            loadedProviderLyrics = lyrics
                            recordCandidate(lyrics, sourceIndex)
                            if (lyrics.satisfies(timingPreference)) return result(lyrics)
                        }
                    LyricsSourcePreference.Embedded -> sharedCached
                        ?.takeIf { lyrics -> lyrics.source == LyricsSource.Embedded }
                        ?.let { lyrics ->
                            loadedEmbeddedLyrics = lyrics
                            recordCandidate(lyrics, sourceIndex)
                            if (lyrics.satisfies(timingPreference)) return result(lyrics)
                        }
                    LyricsSourcePreference.Online -> lyricsRepository.onlineProviders
                        .forEachIndexed { providerIndex, onlineProvider ->
                            lyricsRepository.cachedOnlineLyrics(sourceId, track.id, onlineProvider.id)
                                ?.let { lyrics ->
                                    loadedOnlineLyrics[onlineProvider.id] = lyrics
                                    recordCandidate(lyrics, sourceIndex, providerIndex)
                                    if (lyrics.satisfies(timingPreference)) return result(lyrics)
                                }
                        }
                    LyricsSourcePreference.Download,
                    LyricsSourcePreference.WordSynced,
                    -> Unit
                }
            }
        }

        var firstError: Throwable? = null
        activeSearchOrder.forEachIndexed { sourceIndex, source ->
            when (source) {
                LyricsSourcePreference.Provider -> runCatching {
                    providerLyrics(sourceId, provider, track, acceptedTimings)
                }.onFailure { error -> if (firstError == null) firstError = error }
                    .getOrNull()
                    ?.let { lyrics ->
                        loadedProviderLyrics = lyrics
                        recordCandidate(lyrics, sourceIndex)
                        if (lyrics.satisfies(timingPreference)) return result(lyrics)
                    }

                LyricsSourcePreference.Embedded -> runCatching {
                    localAudio = localAudio ?: localAudio(sourceId, track, quality, audioCachingEnabled)
                    val lyrics = audioMetadataSidecarService.embeddedLyrics(
                        audioMetadataSidecarService.audioTags(localAudio),
                    )
                    if (sourceId != null && lyrics != null) {
                        lyricsRepository.cacheEmbeddedLyrics(sourceId, track.id, lyrics)
                    }
                    lyrics
                }.onFailure { error -> if (firstError == null) firstError = error }
                    .getOrNull()
                    ?.let { lyrics ->
                        loadedEmbeddedLyrics = lyrics
                        recordCandidate(lyrics, sourceIndex)
                        if (lyrics.satisfies(timingPreference)) return result(lyrics)
                    }

                LyricsSourcePreference.Online -> {
                    val providers = lyricsRepository.onlineProviders.prioritizedFor(timingPreference)
                    providers.forEach { onlineProvider ->
                        val providerIndex = lyricsRepository.onlineProviders.indexOf(onlineProvider)
                        val providerAcceptedTimings = if (onlineProvider.canSatisfy(timingPreference)) {
                            acceptedTimings
                        } else {
                            LyricsTiming.entries.toSet()
                        }
                        runCatching {
                            onlineLyrics(sourceId, track, onlineProvider, providerAcceptedTimings)
                        }.onFailure { error -> if (firstError == null) firstError = error }
                            .getOrNull()
                            ?.let { lyrics ->
                                loadedOnlineLyrics[onlineProvider.id] = lyrics
                                recordCandidate(lyrics, sourceIndex, providerIndex)
                                if (lyrics.satisfies(timingPreference)) return result(lyrics)
                            }
                    }
                }

                LyricsSourcePreference.Download,
                LyricsSourcePreference.WordSynced,
                -> Unit
            }
        }

        val fallback = candidates.sortedWith(
            compareByDescending<RankedLyrics> { candidate -> candidate.lyrics.timing.rank }
                .thenBy { candidate -> candidate.sourceIndex }
                .thenBy { candidate -> candidate.providerIndex },
        ).firstOrNull()?.lyrics
        if (fallback == null) firstError?.let { error -> throw error }
        return result(fallback)
    }

    private suspend fun localAudio(
        sourceId: String?,
        track: Track,
        quality: StreamQuality,
        audioCachingEnabled: Boolean,
    ): PlaybackLocalAudio? = sourceId?.let { activeSourceId ->
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
    val timingPreference: LyricsTimingPreference,
    val displayTimingPreference: LyricsTimingPreference,
    val searchOrder: List<LyricsSourcePreference>,
)

private data class RankedLyrics(
    val lyrics: Lyrics,
    val sourceIndex: Int,
    val providerIndex: Int,
)

private val LyricsTiming.rank: Int
    get() = when (this) {
        LyricsTiming.Plain -> 0
        LyricsTiming.LineSynced -> 1
        LyricsTiming.WordSynced -> 2
    }

private fun LyricsTimingPreference.acceptedTimings(): Set<LyricsTiming> = when (this) {
    LyricsTimingPreference.FirstAvailable,
    LyricsTimingPreference.Plain,
    -> LyricsTiming.entries.toSet()
    LyricsTimingPreference.LineSynced -> setOf(LyricsTiming.LineSynced, LyricsTiming.WordSynced)
    LyricsTimingPreference.WordSynced -> setOf(LyricsTiming.WordSynced)
}

private fun Lyrics.satisfies(preference: LyricsTimingPreference): Boolean = when (preference) {
    LyricsTimingPreference.FirstAvailable,
    LyricsTimingPreference.Plain,
    -> true
    LyricsTimingPreference.LineSynced -> timing != LyricsTiming.Plain
    LyricsTimingPreference.WordSynced -> timing == LyricsTiming.WordSynced
}

private fun LyricsProvider.canSatisfy(preference: LyricsTimingPreference): Boolean = when (preference) {
    LyricsTimingPreference.FirstAvailable,
    LyricsTimingPreference.Plain,
    -> capabilities.isNotEmpty()
    LyricsTimingPreference.LineSynced ->
        LyricsTiming.LineSynced in capabilities || LyricsTiming.WordSynced in capabilities
    LyricsTimingPreference.WordSynced -> LyricsTiming.WordSynced in capabilities
}

private fun List<LyricsProvider>.prioritizedFor(preference: LyricsTimingPreference): List<LyricsProvider> =
    withIndex().sortedWith(
        compareByDescending<IndexedValue<LyricsProvider>> { indexed -> indexed.value.canSatisfy(preference) }
            .thenBy { indexed -> indexed.index },
    ).map { indexed -> indexed.value }

private fun Lyrics.forTimingPreference(preference: LyricsTimingPreference): Lyrics = when (preference) {
    LyricsTimingPreference.FirstAvailable,
    LyricsTimingPreference.WordSynced,
    -> this
    LyricsTimingPreference.LineSynced -> if (timing == LyricsTiming.WordSynced) {
        copy(cueLines = emptyList())
    } else {
        this
    }
    LyricsTimingPreference.Plain -> copy(
        synced = false,
        lines = lines.map { line -> LyricLine(startMillis = null, text = line.text) },
        cueLines = emptyList(),
    )
}

private const val MaxCompletedLyricsLookups = 512
