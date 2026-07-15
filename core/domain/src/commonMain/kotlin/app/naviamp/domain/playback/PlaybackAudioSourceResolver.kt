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
) {
    val hasLocalAudio: Boolean = localAudio != null || fallbackLocalAudio != null
}

data class PlaybackLocalAudio(
    val path: String,
    val uri: String,
    val sizeBytes: Long? = null,
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
    startPositionSeconds: Double? = null,
): PlaybackAudioSourcePlan =
    resolvePlaybackAudioSource(
        sourceId = sourceId,
        track = track,
        quality = quality,
        audioCachingEnabled = audioCachingEnabled,
        startPositionSeconds = startPositionSeconds,
        downloadedTrackPlayback = downloadedTrackPlayback,
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
        ?.let { id -> cachedAudio(id, track.id, quality) ?: cachedAudioForTrack(id, track.id) }
    if (cached != null) {
        return playbackAudioSourcePlan(
            track = track,
            quality = quality,
            startPositionSeconds = startPositionSeconds,
            localAudio = cached,
            source = PlaybackSource.CachedFile,
        )
    }

    return playbackAudioSourcePlan(
        track = track,
        quality = quality,
        startPositionSeconds = startPositionSeconds,
        localAudio = null,
        fallbackLocalAudio = downloaded.takeIf {
            downloadedTrackPlayback == DownloadedTrackPlayback.PreferServer
        },
        source = if (audioCachingEnabled) PlaybackSource.ProviderStream else PlaybackSource.ProviderStreamCacheDisabled,
    )
}

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
