package app.naviamp.domain.playback

import app.naviamp.domain.StreamQuality
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.settings.DownloadedTrackPlayback

enum class PlaybackSource(val label: String) {
    Unknown("Unknown"),
    DownloadedFile("Downloaded file"),
    CachedFile("Cached file"),
    ProviderStream("Provider stream"),
    ProviderStreamCacheDisabled("Provider stream (cache disabled)"),
}

data class PlaybackAudioSourcePlan(
    val localAudio: PlaybackLocalAudio?,
    val fallbackLocalAudio: PlaybackLocalAudio? = null,
    val source: PlaybackSource,
    val target: PlaybackTargetPlan,
    val fallbackSource: PlaybackSource = PlaybackSource.DownloadedFile,
) {
    val hasLocalAudio: Boolean = localAudio != null || fallbackLocalAudio != null
    val effectiveQuality: StreamQuality
        get() = localAudio?.quality ?: target.providerStreamRequest.quality
}

/** Fall back to engine seeking when the server cannot start an audio stream at an offset. */
fun PlaybackAudioSourcePlan.withAudioStreamOffsetSupport(supported: Boolean): PlaybackAudioSourcePlan {
    val offset = target.providerStreamRequest.startPositionSeconds ?: return this
    if (supported) return this
    return copy(target = target.copy(
        engineStartPositionSeconds = offset,
        providerStreamRequest = target.providerStreamRequest.copy(startPositionSeconds = null),
    ))
}

data class PlaybackLocalAudio(
    val path: String,
    val uri: String,
    val sizeBytes: Long? = null,
    val quality: StreamQuality? = null,
)

interface PlaybackAudioAssetRepository {
    suspend fun downloadedAudio(
        sourceId: String,
        trackId: TrackId,
    ): PlaybackLocalAudio?

    suspend fun downloadedAudio(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
    ): PlaybackLocalAudio?

    suspend fun cachedAudio(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
    ): PlaybackLocalAudio?

    suspend fun cachedAudio(
        sourceId: String,
        trackId: TrackId,
    ): PlaybackLocalAudio? = null
}

suspend fun PlaybackAudioSourcePlan.playbackStreamUrl(
    localAudioUrl: (PlaybackLocalAudio) -> String = { it.uri },
    providerStreamUrl: suspend (PlaybackTargetPlan) -> String,
): String =
    localAudio?.let(localAudioUrl) ?: runCatching { providerStreamUrl(target) }
        .getOrElse { error -> fallbackLocalAudio?.let(localAudioUrl) ?: throw error }

fun PlaybackAudioSourcePlan.fallbackPlaybackUrl(
    localAudioUrl: (PlaybackLocalAudio) -> String = { it.uri },
): String? = fallbackLocalAudio?.let(localAudioUrl)

fun PlaybackAudioSourcePlan.resolvedForPlaybackUrl(url: String): PlaybackAudioSourcePlan {
    val fallback = fallbackLocalAudio?.takeIf { it.uri == url && localAudio == null } ?: return this
    return copy(
        localAudio = fallback,
        fallbackLocalAudio = null,
        source = fallbackSource,
        target = target.copy(engineStartPositionSeconds =
            target.providerStreamRequest.startPositionSeconds ?: target.engineStartPositionSeconds),
    )
}

fun emptyPlaybackAudioAssetRepository(): PlaybackAudioAssetRepository =
    object : PlaybackAudioAssetRepository {
        override suspend fun downloadedAudio(
            sourceId: String,
            trackId: TrackId,
        ): PlaybackLocalAudio? = null

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

suspend fun resolvePlaybackAudioSource(
    sourceId: String?,
    track: Track,
    quality: StreamQuality,
    audioCachingEnabled: Boolean,
    audioAssets: PlaybackAudioAssetRepository,
    downloadedTrackPlayback: DownloadedTrackPlayback = DownloadedTrackPlayback.PreferDownloaded,
    allowMismatchedCachedAudio: Boolean = true,
    startPositionSeconds: Double? = null,
): PlaybackAudioSourcePlan =
    resolvePlaybackAudioSource(
        sourceId = sourceId,
        track = track,
        quality = quality,
        audioCachingEnabled = audioCachingEnabled,
        startPositionSeconds = startPositionSeconds,
        downloadedTrackPlayback = downloadedTrackPlayback,
        allowMismatchedCachedAudio = allowMismatchedCachedAudio,
        downloadedAudio = { id, trackId, _ -> audioAssets.downloadedAudio(id, trackId) },
        cachedAudio = audioAssets::cachedAudio,
        cachedAudioForTrack = audioAssets::cachedAudio,
    )

suspend fun resolvePlaybackAudioSource(
    sourceId: String?,
    track: Track,
    quality: StreamQuality,
    audioCachingEnabled: Boolean,
    downloadedTrackPlayback: DownloadedTrackPlayback = DownloadedTrackPlayback.PreferDownloaded,
    allowMismatchedCachedAudio: Boolean = true,
    startPositionSeconds: Double? = null,
    downloadedAudio: suspend (sourceId: String, trackId: TrackId, quality: StreamQuality) -> PlaybackLocalAudio?,
    cachedAudio: suspend (sourceId: String, trackId: TrackId, quality: StreamQuality) -> PlaybackLocalAudio?,
    cachedAudioForTrack: suspend (sourceId: String, trackId: TrackId) -> PlaybackLocalAudio? = { _, _ -> null },
): PlaybackAudioSourcePlan {
    val downloaded = sourceId?.let { id -> downloadedAudio(id, track.id, quality) }
    if (downloaded != null && downloadedTrackPlayback == DownloadedTrackPlayback.PreferDownloaded) {
        return playbackAudioSourcePlan(
            track = track,
            quality = quality,
            startPositionSeconds = startPositionSeconds,
            localAudio = downloaded,
            source = PlaybackSource.DownloadedFile,
        )
    }

    val cached = sourceId
        ?.takeIf { audioCachingEnabled }
        ?.let { id ->
            cachedAudio(id, track.id, quality)
                ?: cachedAudioForTrack(id, track.id)
        }
    if (cached != null && (allowMismatchedCachedAudio || !quality.upgradesCachedAudio(cached.quality))) {
        return playbackAudioSourcePlan(
            track = track,
            quality = quality,
            startPositionSeconds = startPositionSeconds,
            localAudio = cached,
            source = PlaybackSource.CachedFile,
        )
    }

    val fallback = downloaded.takeIf {
        downloadedTrackPlayback == DownloadedTrackPlayback.PreferServer
    } ?: cached
    return playbackAudioSourcePlan(
        track = track,
        quality = quality,
        startPositionSeconds = startPositionSeconds,
        localAudio = null,
        fallbackLocalAudio = fallback,
        source = if (audioCachingEnabled) PlaybackSource.ProviderStream else PlaybackSource.ProviderStreamCacheDisabled,
    ).copy(fallbackSource = if (fallback === cached) PlaybackSource.CachedFile else PlaybackSource.DownloadedFile)
}

/** Only known upgrades replace cached audio; codec bitrates are not directly comparable. */
fun StreamQuality.upgradesCachedAudio(cached: StreamQuality?): Boolean = when {
    cached == null || cached == this || cached == StreamQuality.Original -> false
    this == StreamQuality.Original -> true
    this is StreamQuality.Transcoded && cached is StreamQuality.Transcoded ->
        codec == cached.codec && bitrateKbps > cached.bitrateKbps
    else -> false
}

fun cachedPlaybackAudioSourcePlan(
    track: Track,
    quality: StreamQuality,
    startPositionSeconds: Double?,
    localAudio: PlaybackLocalAudio,
): PlaybackAudioSourcePlan =
    playbackAudioSourcePlan(
        track = track,
        quality = quality,
        startPositionSeconds = startPositionSeconds,
        localAudio = localAudio,
        source = PlaybackSource.CachedFile,
    )

fun providerPlaybackAudioSourcePlan(
    track: Track,
    quality: StreamQuality,
    startPositionSeconds: Double?,
): PlaybackAudioSourcePlan =
    playbackAudioSourcePlan(
        track = track,
        quality = quality,
        startPositionSeconds = startPositionSeconds,
        localAudio = null,
        source = PlaybackSource.ProviderStream,
    )

private fun playbackAudioSourcePlan(
    track: Track,
    quality: StreamQuality,
    startPositionSeconds: Double?,
    localAudio: PlaybackLocalAudio?,
    fallbackLocalAudio: PlaybackLocalAudio? = null,
    source: PlaybackSource,
): PlaybackAudioSourcePlan =
    PlaybackAudioSourcePlan(
        localAudio = localAudio,
        fallbackLocalAudio = fallbackLocalAudio,
        source = source,
        target = playbackTargetPlan(
            track = track,
            quality = quality,
            startPositionSeconds = startPositionSeconds,
            hasLocalAudio = localAudio != null,
        ),
    )
