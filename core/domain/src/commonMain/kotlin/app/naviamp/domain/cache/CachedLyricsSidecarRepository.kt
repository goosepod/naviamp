package app.naviamp.domain.cache

import app.naviamp.domain.Lyrics
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.lyrics.LyricsProvider
import app.naviamp.domain.provider.MediaProvider

/**
 * Shared lyrics repository composition used by every host.
 *
 * Hosts provide storage and HTTP engines; Core owns provider lookup, caching, and online fallback.
 */
class CachedLyricsSidecarRepository(
    private val cache: LyricsSidecarCacheService,
    private val onlineProvider: LyricsProvider,
) : LyricsSidecarRepository {
    override suspend fun providerLyrics(
        sourceId: String,
        provider: MediaProvider,
        trackId: TrackId,
    ): Lyrics? = cache.providerLyrics(sourceId, provider, trackId)

    override suspend fun cacheEmbeddedLyrics(
        sourceId: String,
        trackId: TrackId,
        lyrics: Lyrics,
    ): Lyrics = cache.cacheEmbeddedLyrics(sourceId, trackId, lyrics)

    override suspend fun lrclibLyrics(
        sourceId: String,
        track: Track,
    ): Lyrics? = cache.lrclibLyrics(sourceId, track, onlineProvider)
}
