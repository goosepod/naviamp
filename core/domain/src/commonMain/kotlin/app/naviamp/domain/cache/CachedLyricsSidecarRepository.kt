package app.naviamp.domain.cache

import app.naviamp.domain.Lyrics
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.lyrics.LyricsProvider
import app.naviamp.domain.lyrics.LyricsTiming
import app.naviamp.domain.provider.MediaProvider

/**
 * Shared lyrics repository composition used by every host.
 *
 * Hosts provide storage and HTTP engines; Core owns provider lookup, caching, and online fallback.
 */
class CachedLyricsSidecarRepository(
    private val cache: LyricsSidecarCacheService,
    override val onlineProviders: List<LyricsProvider>,
) : LyricsSidecarRepository {
    override suspend fun providerLyrics(
        sourceId: String,
        provider: MediaProvider,
        trackId: TrackId,
        acceptedTimings: Set<LyricsTiming>,
    ): Lyrics? = cache.providerLyrics(sourceId, provider, trackId, acceptedTimings)

    override suspend fun cacheEmbeddedLyrics(
        sourceId: String,
        trackId: TrackId,
        lyrics: Lyrics,
    ): Lyrics = cache.cacheEmbeddedLyrics(sourceId, trackId, lyrics)

    override suspend fun cachedLyrics(sourceId: String, trackId: TrackId): Lyrics? =
        cache.cachedLyrics(sourceId, trackId)

    override suspend fun cachedOnlineLyrics(
        sourceId: String,
        trackId: TrackId,
        providerId: String,
    ): Lyrics? = cache.cachedOnlineLyrics(sourceId, trackId, providerId)

    override suspend fun onlineLyrics(
        sourceId: String,
        track: Track,
        provider: LyricsProvider,
        acceptedTimings: Set<LyricsTiming>,
    ): Lyrics? = cache.onlineLyrics(sourceId, track, provider, acceptedTimings)
}
