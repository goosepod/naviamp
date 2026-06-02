package app.naviamp.domain.playback

import app.naviamp.domain.StreamQuality
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId

enum class PlaybackSource(val label: String) {
    Unknown("Unknown"),
    DownloadedFile("Downloaded file"),
    CachedFile("Cached file"),
    ProviderStream("Provider stream"),
    ProviderStreamCacheDisabled("Provider stream (cache disabled)"),
}

data class PlaybackAudioSourcePlan<LocalAudio>(
    val localAudio: LocalAudio?,
    val source: PlaybackSource,
    val target: PlaybackTargetPlan,
) {
    val hasLocalAudio: Boolean = localAudio != null
}

interface PlaybackAudioAssetRepository<LocalAudio> {
    suspend fun downloadedAudio(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
    ): LocalAudio?

    suspend fun cachedAudio(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
    ): LocalAudio?
}

fun <LocalAudio> emptyPlaybackAudioAssetRepository(): PlaybackAudioAssetRepository<LocalAudio> =
    object : PlaybackAudioAssetRepository<LocalAudio> {
        override suspend fun downloadedAudio(
            sourceId: String,
            trackId: TrackId,
            quality: StreamQuality,
        ): LocalAudio? = null

        override suspend fun cachedAudio(
            sourceId: String,
            trackId: TrackId,
            quality: StreamQuality,
        ): LocalAudio? = null
    }

suspend fun <LocalAudio> resolvePlaybackAudioSource(
    sourceId: String?,
    track: Track,
    quality: StreamQuality,
    audioCachingEnabled: Boolean,
    audioAssets: PlaybackAudioAssetRepository<LocalAudio>,
    startPositionSeconds: Double? = null,
): PlaybackAudioSourcePlan<LocalAudio> =
    resolvePlaybackAudioSource(
        sourceId = sourceId,
        track = track,
        quality = quality,
        audioCachingEnabled = audioCachingEnabled,
        startPositionSeconds = startPositionSeconds,
        downloadedAudio = audioAssets::downloadedAudio,
        cachedAudio = audioAssets::cachedAudio,
    )

suspend fun <LocalAudio> resolvePlaybackAudioSource(
    sourceId: String?,
    track: Track,
    quality: StreamQuality,
    audioCachingEnabled: Boolean,
    startPositionSeconds: Double? = null,
    downloadedAudio: suspend (sourceId: String, trackId: TrackId, quality: StreamQuality) -> LocalAudio?,
    cachedAudio: suspend (sourceId: String, trackId: TrackId, quality: StreamQuality) -> LocalAudio?,
): PlaybackAudioSourcePlan<LocalAudio> {
    val downloaded = sourceId?.let { id -> downloadedAudio(id, track.id, quality) }
    if (downloaded != null) {
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
        ?.let { id -> cachedAudio(id, track.id, quality) }
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
        source = if (audioCachingEnabled) PlaybackSource.ProviderStream else PlaybackSource.ProviderStreamCacheDisabled,
    )
}

private fun <LocalAudio> playbackAudioSourcePlan(
    track: Track,
    quality: StreamQuality,
    startPositionSeconds: Double?,
    localAudio: LocalAudio?,
    source: PlaybackSource,
): PlaybackAudioSourcePlan<LocalAudio> =
    PlaybackAudioSourcePlan(
        localAudio = localAudio,
        source = source,
        target = playbackTargetPlan(
            track = track,
            quality = quality,
            startPositionSeconds = startPositionSeconds,
            hasLocalAudio = localAudio != null,
        ),
    )
