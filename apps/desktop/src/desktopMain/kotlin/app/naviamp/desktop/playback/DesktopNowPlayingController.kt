package app.naviamp.desktop

import app.naviamp.domain.Lyrics
import app.naviamp.domain.Track
import app.naviamp.domain.audio.lyricsFromAudioTags
import app.naviamp.domain.cache.AudioWaveformRepository
import app.naviamp.domain.cache.LocalLibraryIndexRepository
import app.naviamp.domain.cache.LyricsSidecarRepository
import app.naviamp.domain.isInternetRadioTrack
import app.naviamp.domain.lyrics.selectPreferredLyrics
import app.naviamp.domain.playback.PlaybackAudioAssetRepository
import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.domain.playback.coverArtPreloadUrls
import app.naviamp.domain.playback.lyricsLoadingStatus
import app.naviamp.domain.playback.resolvePlaybackAudioSource
import app.naviamp.domain.playback.shouldLoadOnlineLyrics
import app.naviamp.domain.playback.waveformStatus
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.waveform.AudioWaveform
import app.naviamp.desktop.settings.CacheSettings
import app.naviamp.desktop.settings.PlaybackSettings
import app.naviamp.ui.preloadJvmPlatformCoverArt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path

class DesktopNowPlayingController(
    private val audioWaveformRepository: AudioWaveformRepository,
    private val lyricsSidecarRepository: LyricsSidecarRepository,
    private val localLibraryIndexRepository: LocalLibraryIndexRepository,
    private val playbackAudioAssets: PlaybackAudioAssetRepository<Path>,
    private val playbackEngine: PlaybackEngine,
    private val provider: () -> MediaProvider?,
    private val sourceId: () -> String?,
    private val playbackSettings: () -> PlaybackSettings,
    private val cacheSettings: () -> CacheSettings,
    private val appRoute: () -> AppRoute,
    private val lyricsVisible: () -> Boolean,
    private val playbackQueue: () -> PlaybackQueue,
    private val nowPlayingTrack: () -> Track?,
    private val nowPlayingCoverArtUrl: () -> String?,
    private val setNowPlayingWaveform: (AudioWaveform?) -> Unit,
    private val setNowPlayingWaveformStatus: (String) -> Unit,
    private val setNowPlayingAudioTags: (List<AudioTag>?) -> Unit,
    private val setNowPlayingLyrics: (Lyrics?) -> Unit,
    private val setNowPlayingLyricsStatus: (String?) -> Unit,
    private val setRelatedTracks: (List<Track>) -> Unit,
) {
    suspend fun loadNowPlayingAnalysis() {
        val lyricsVisibleForWork = lyricsVisible() && appRoute() == AppRoute.Player
        val track = nowPlayingTrack() ?: run {
            setNowPlayingWaveform(null)
            setNowPlayingWaveformStatus("No track")
            setNowPlayingLyricsStatus(null)
            return
        }
        if (track.isInternetRadioTrack()) {
            setNowPlayingWaveform(null)
            setNowPlayingWaveformStatus("Internet radio")
            setNowPlayingAudioTags(null)
            setNowPlayingLyrics(null)
            setNowPlayingLyricsStatus(null)
            return
        }
        val activeSourceId = sourceId() ?: run {
            setNowPlayingWaveform(null)
            setNowPlayingWaveformStatus("No source")
            setNowPlayingLyricsStatus(null)
            return
        }
        val activeProvider = provider() ?: run {
            setNowPlayingWaveform(null)
            setNowPlayingWaveformStatus("No provider")
            setNowPlayingLyricsStatus(null)
            return
        }
        val activePlaybackSettings = playbackSettings()
        val activeCacheSettings = cacheSettings()
        val quality = activePlaybackSettings.streamQuality(playbackEngine)
        setNowPlayingWaveformStatus("Loading")
        setNowPlayingLyricsStatus(
            if (lyricsVisibleForWork) lyricsLoadingStatus(activePlaybackSettings.lrclibLyricsEnabled) else null,
        )

        val analysis = withContext(Dispatchers.IO) {
            runCatching {
                val cachedWaveformBeforeAudio = audioWaveformRepository.cachedAudioWaveform(
                    sourceId = activeSourceId,
                    trackId = track.id,
                    quality = quality,
                )
                val audioPath = resolvePlaybackAudioSource(
                    sourceId = activeSourceId,
                    track = track,
                    quality = quality,
                    audioCachingEnabled = activeCacheSettings.audioCachingEnabled,
                    audioAssets = playbackAudioAssets,
                ).localAudio
                val cachedWaveform = cachedWaveformBeforeAudio
                    ?: if (audioPath != null) {
                        audioWaveformRepository.cachedAudioWaveform(
                            sourceId = activeSourceId,
                            trackId = track.id,
                            quality = quality,
                        )
                    } else {
                        null
                    }
                val waveform = cachedWaveform ?: if (audioPath != null && activeCacheSettings.audioCachingEnabled) {
                    audioWaveformRepository.ensureAudioWaveform(
                        sourceId = activeSourceId,
                        trackId = track.id,
                        quality = quality,
                    )
                } else {
                    null
                }
                val status = waveformStatus(
                    cachedWaveformAvailable = cachedWaveform != null,
                    generatedWaveformAvailable = waveform != null,
                    audioAvailable = audioPath != null,
                    audioCachingEnabled = activeCacheSettings.audioCachingEnabled,
                )
                val tags = audioPath
                    ?.let { path -> runCatching { AudioTagReader().read(path) }.getOrDefault(emptyList()) }
                    .orEmpty()
                val providerLyrics = if (lyricsVisibleForWork) {
                    lyricsSidecarRepository.providerLyrics(activeSourceId, activeProvider, track.id)
                } else {
                    null
                }
                val embeddedLyrics = if (lyricsVisibleForWork) lyricsFromAudioTags(tags) else null
                val lrclibLyrics = if (
                    lyricsVisibleForWork &&
                    shouldLoadOnlineLyrics(
                        onlineLyricsEnabled = activePlaybackSettings.lrclibLyricsEnabled,
                        providerLyrics = providerLyrics,
                        embeddedLyrics = embeddedLyrics,
                    )
                ) {
                    lyricsSidecarRepository.lrclibLyrics(activeSourceId, track)
                } else {
                    null
                }
                val lyrics = selectPreferredLyrics(
                    providerLyrics = providerLyrics,
                    embeddedLyrics = embeddedLyrics,
                    onlineLyrics = lrclibLyrics,
                )
                NowPlayingAnalysis(waveform, status, tags, lyrics)
            }.getOrNull()
        }
        setNowPlayingWaveform(analysis?.waveform)
        setNowPlayingWaveformStatus(analysis?.waveformStatus ?: "Unavailable")
        setNowPlayingAudioTags(analysis?.audioTags)
        setNowPlayingLyrics(analysis?.lyrics)
        setNowPlayingLyricsStatus(null)
    }

    suspend fun loadRelatedTracks() {
        val track = nowPlayingTrack()
        val activeSourceId = sourceId()
        if (track == null || activeSourceId == null || track.isInternetRadioTrack()) {
            setRelatedTracks(emptyList())
            return
        }
        setRelatedTracks(
            withContext(Dispatchers.IO) {
                localLibraryIndexRepository.relatedLibraryTracks(activeSourceId, track, limit = RelatedTracksLimit)
            },
        )
    }

    suspend fun preloadCoverArt() {
        val activeProvider = provider() ?: return
        val urls = coverArtPreloadUrls(
            queue = playbackQueue(),
            currentCoverArtUrl = nowPlayingCoverArtUrl(),
            historyLimit = CoverArtPreloadHistoryLimit,
            upcomingLimit = CoverArtPreloadUpcomingLimit,
            coverArtUrl = activeProvider::coverArtUrl,
        )
        withContext(Dispatchers.IO) {
            preloadJvmPlatformCoverArt(urls)
        }
    }
}
