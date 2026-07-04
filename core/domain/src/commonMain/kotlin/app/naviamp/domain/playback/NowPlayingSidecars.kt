package app.naviamp.domain.playback

import app.naviamp.domain.Lyrics
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.audio.AudioTag
import app.naviamp.domain.cache.SidecarStatusRepository
import app.naviamp.domain.isInternetRadioTrack
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.waveform.AudioWaveform

const val SidecarTypeWaveform = "waveform"
const val SidecarTypeProviderLyrics = "provider_lyrics"
const val SidecarTypeEmbeddedLyrics = "embedded_lyrics"
const val SidecarTypeLrclibLyrics = "lrclib_lyrics"
const val SidecarTypeLyrics = "lyrics"

fun lyricsLoadingStatus(onlineLyricsEnabled: Boolean): String =
    if (onlineLyricsEnabled) "Grabbing lyrics..." else "Loading lyrics..."

fun lyricsUnavailableStatus(error: Throwable): String =
    error.message ?: "Lyrics unavailable"

fun waveformUnavailableStatus(error: Throwable): String =
    error.message ?: "Waveform unavailable"

fun sidecarFailureStatus(error: Throwable): String =
    error.message ?: error::class.simpleName ?: "Sidecar prep failed."

fun SidecarStatusRepository.recordSidecarSuccess(
    sourceId: String,
    trackId: TrackId,
    quality: StreamQuality,
    sidecarType: String,
) {
    recordSidecarStatus(
        sourceId = sourceId,
        trackId = trackId,
        quality = quality,
        sidecarType = sidecarType,
        success = true,
    )
}

fun SidecarStatusRepository.recordSidecarFailure(
    sourceId: String,
    trackId: TrackId,
    quality: StreamQuality,
    sidecarType: String,
    errorMessage: String,
) {
    recordSidecarStatus(
        sourceId = sourceId,
        trackId = trackId,
        quality = quality,
        sidecarType = sidecarType,
        success = false,
        errorMessage = errorMessage,
    )
}

fun shouldLoadOnlineLyrics(
    onlineLyricsEnabled: Boolean,
    providerLyrics: Lyrics?,
    embeddedLyrics: Lyrics?,
): Boolean =
    onlineLyricsEnabled && listOf(providerLyrics, embeddedLyrics).none { it?.synced == true }

fun waveformStatus(
    cachedWaveformAvailable: Boolean,
    generatedWaveformAvailable: Boolean,
    audioAvailable: Boolean,
    audioCachingEnabled: Boolean,
): String =
    when {
        cachedWaveformAvailable -> "Cached"
        generatedWaveformAvailable -> "Generated"
        !audioAvailable && !audioCachingEnabled -> "Cache disabled"
        !audioAvailable -> "Preparing"
        else -> "Unavailable"
    }

fun sidecarPrepTracks(
    queue: PlaybackQueue,
    depth: Int,
): List<Track> =
    queue.tracks
        .drop(queue.currentIndex.coerceAtLeast(0))
        .take(depth.coerceAtLeast(0))
        .filterNot { it.isInternetRadioTrack() }

fun audioPrefetchTracks(
    queue: PlaybackQueue,
    depth: Int,
    includeCurrentTrack: Boolean,
): List<Track> {
    val startIndex = if (includeCurrentTrack) {
        queue.currentIndex.coerceAtLeast(0)
    } else {
        (queue.currentIndex + 1).coerceAtLeast(0)
    }
    return queue.tracks
        .drop(startIndex)
        .take(depth.coerceAtLeast(0))
        .filterNot { it.isInternetRadioTrack() }
}

data class SidecarPrepPlan(
    val tracks: List<Track>,
    val loadLyrics: Boolean,
)

data class CurrentTrackSidecarWork<Provider>(
    val sourceId: String?,
    val provider: Provider,
    val track: Track,
    val quality: StreamQuality,
    val audioCachingEnabled: Boolean,
    val onlineLyricsEnabled: Boolean,
    val loadLyrics: Boolean,
)

fun sidecarPrepPlan(
    queue: PlaybackQueue,
    depth: Int,
    onlineLyricsEnabled: Boolean,
    lyricsVisible: Boolean,
): SidecarPrepPlan =
    SidecarPrepPlan(
        tracks = sidecarPrepTracks(queue, depth),
        loadLyrics = onlineLyricsEnabled || lyricsVisible,
    )

fun <Provider> currentTrackSidecarWork(
    sourceId: String?,
    provider: Provider?,
    track: Track?,
    quality: StreamQuality?,
    audioCachingEnabled: Boolean,
    onlineLyricsEnabled: Boolean,
    lyricsVisible: Boolean,
): CurrentTrackSidecarWork<Provider>? {
    val activeProvider = provider ?: return null
    val activeTrack = track?.takeUnless { it.isInternetRadioTrack() } ?: return null
    val activeQuality = quality ?: return null
    return CurrentTrackSidecarWork(
        sourceId = sourceId,
        provider = activeProvider,
        track = activeTrack,
        quality = activeQuality,
        audioCachingEnabled = audioCachingEnabled,
        onlineLyricsEnabled = onlineLyricsEnabled,
        loadLyrics = onlineLyricsEnabled || lyricsVisible,
    )
}

fun <Provider> currentTrackSidecarWork(
    sourceId: String?,
    provider: Provider?,
    queue: PlaybackQueue,
    quality: StreamQuality?,
    audioCachingEnabled: Boolean,
    onlineLyricsEnabled: Boolean,
    lyricsVisible: Boolean,
): CurrentTrackSidecarWork<Provider>? =
    currentTrackSidecarWork(
        sourceId = sourceId,
        provider = provider,
        track = queue.current,
        quality = quality,
        audioCachingEnabled = audioCachingEnabled,
        onlineLyricsEnabled = onlineLyricsEnabled,
        lyricsVisible = lyricsVisible,
    )

suspend fun <Provider> runCurrentTrackSidecars(
    work: CurrentTrackSidecarWork<Provider>,
    isActive: () -> Boolean,
    prepareAudio: suspend (CurrentTrackSidecarWork<Provider>) -> Unit = {},
    prepareWaveform: suspend (CurrentTrackSidecarWork<Provider>) -> AudioWaveform?,
    prepareAudioTags: (suspend (CurrentTrackSidecarWork<Provider>) -> List<AudioTag>)? = null,
    prepareLyrics: (suspend (CurrentTrackSidecarWork<Provider>) -> Lyrics?)? = null,
    onWaveformReady: suspend (AudioWaveform?) -> Unit = {},
    onWaveformFailed: suspend (Throwable) -> Unit = {},
    onAudioTagsReady: suspend (List<AudioTag>) -> Unit = {},
    onAudioTagsFailed: suspend (Throwable) -> Unit = {},
    onLyricsReady: suspend (Lyrics?) -> Unit = {},
    onLyricsFailed: suspend (Throwable, String) -> Unit = { _, _ -> },
) {
    if (!isActive()) return
    runCatching {
        prepareWaveform(work)
    }.onSuccess { waveform ->
        if (isActive()) onWaveformReady(waveform)
    }.onFailure { error ->
        if (isActive()) onWaveformFailed(error)
    }

    if (!isActive()) return
    runCatching {
        prepareAudio(work)
    }.onFailure {
        return
    }

    if (!isActive()) return
    if (prepareAudioTags != null) {
        runCatching {
            prepareAudioTags(work)
        }.onSuccess { tags ->
            if (isActive()) onAudioTagsReady(tags)
        }.onFailure { error ->
            if (isActive()) onAudioTagsFailed(error)
        }
    }

    if (!isActive() || !work.loadLyrics || prepareLyrics == null) return
    runCatching {
        prepareLyrics(work)
    }.onSuccess { lyrics ->
        if (isActive()) onLyricsReady(lyrics)
    }.onFailure { error ->
        if (isActive()) onLyricsFailed(error, lyricsUnavailableStatus(error))
    }
}

fun coverArtPreloadUrls(
    queue: PlaybackQueue,
    currentCoverArtUrl: String?,
    historyLimit: Int,
    upcomingLimit: Int,
    coverArtUrl: (String) -> String,
): List<String> =
    buildList {
        currentCoverArtUrl?.let(::add)
        queue.backTo()
            .take(historyLimit.coerceAtLeast(0))
            .mapNotNull { track -> track.coverArtId?.let(coverArtUrl) }
            .forEach(::add)
        queue.upNext()
            .take(upcomingLimit.coerceAtLeast(0))
            .mapNotNull { track -> track.coverArtId?.let(coverArtUrl) }
            .forEach(::add)
    }
